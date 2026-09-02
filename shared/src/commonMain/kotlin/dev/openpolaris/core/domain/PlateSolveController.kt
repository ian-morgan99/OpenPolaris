package dev.openpolaris.core.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

// =============================================================================
// Plate-solve controller — uploads a captured JPEG to nova.astrometry.net and
// waits for the resulting sky coordinates (RA / Dec, pixel scale, orientation,
// parity). This is the "blind plate solve" path Benro's app uses to recover
// pointing after a meridian flip or cold start, and is the same backend
// nova.astrometry.net exposes publicly.
//
// ## Why a thin dedicated controller (not part of MountSession)
//
// The mount speaks the SP/SP_CMD protocol on port 9090. Plate solving is an
// off-board service: the image leaves the app, hits a public internet
// endpoint, and comes back as RA/Dec floats. Keeping the controller outside
// the wire-protocol layer means we can stub [PlateSolveTransport] in tests
// without dragging in a fake MountSession.
//
// ## Pipeline
//
//   Idle → LoggingIn → Uploading → PollingSubmission(subid)
//                              → PollingJob(jobId) → Solved(result)
//                                                   ↘ Failed(reason)
//
// Cancellation: any caller that cancels the coroutine running [solve] aborts
// the pipeline between steps. The nova.astrometry.net server will time out
// the orphaned submission on its own; we do not call the "cancel" endpoint
// because (a) it requires auth-as-the-submitter and (b) the server is good
// at reaping.
//
// ## Privacy
//
// The full-resolution image is uploaded to a third party. The feature is
// gated behind [dev.openpolaris.core.config.FeatureFlags.plateSolve], which
// is OFF by default. The UI surfaces this in the FeatureFlags pane.
//
// ## Public-internet egress
//
// nova.astrometry.net is the upstream. The host string is overridable via
// [host] (default "nova.astrometry.net") for tests and for users who self-
// host a nova instance behind a VPN. We do NOT retry on connection errors;
// the caller can re-run. We DO retry status-poll up to [pollAttempts] times
// because nova's job queue is throttled and a "pending" submission can sit
// in the queue for tens of seconds.
// =============================================================================

/** A sky-position result, in J2000-ish degrees-as-floats. Matches what
 *  nova.astrometry.net's `/api/jobs/{id}/calibration/` endpoint returns. */
data class PlateSolveResult(
    /** Right Ascension in degrees, [0, 360). */
    val raDeg: Double,
    /** Declination in degrees, [-90, 90]. */
    val decDeg: Double,
    /** Arcseconds per pixel. */
    val pixscaleArcsec: Double,
    /** Field rotation in degrees, [-180, 180]. Positive = east-of-north. */
    val orientationDeg: Double,
    /** Image parity as reported by nova: 1 if the solver had to flip the
     *  image to match the catalog, 0 otherwise. */
    val parity: Int,
    /** Job id assigned by nova. Useful if the user wants to re-fetch the
     *  result later via the nova UI. */
    val jobId: Long,
)

/** Pipeline status. Exposed so the UI can show a label without reading
 *  internal flags. */
sealed class PlateSolveStatus {
    object Idle : PlateSolveStatus()
    object LoggingIn : PlateSolveStatus()
    data class Uploading(val bytesSent: Long, val bytesTotal: Long) : PlateSolveStatus()
    data class PollingSubmission(val subId: Long, val attempt: Int) : PlateSolveStatus()
    data class PollingJob(val jobId: Long, val attempt: Int) : PlateSolveStatus()
    data class Solved(val result: PlateSolveResult) : PlateSolveStatus()
    data class Failed(val reason: String) : PlateSolveStatus()
}

/** The HTTP transport seam. [dev.openpolaris.core.domain.PlateSolveController]
 *  delegates to this; tests provide a fake. The methods are blocking — the
 *  controller runs them on the IO dispatcher. */
interface PlateSolveTransport {
    /** POST /api/login. Returns the `session` string on success, throws on
     *  network/parse error. */
    fun login(host: String, apiKey: String?): String

    /** POST /api/upload. `requestJson` is the JSON body the nova API expects
     *  (see https://nova.astrometry.net/api_help). The bytes are the image.
     *  Returns the `subid`. */
    fun upload(host: String, session: String, requestJson: String, imageBytes: ByteArray): Long

    /** GET /api/submissions/{subid}. Returns true if the submission has been
     *  processed enough to have at least one job id, false if still in
     *  queue. */
    fun submissionReady(host: String, session: String, subId: Long): Boolean

    /** GET /api/submissions/{subid}. Returns the (possibly empty) list of
     *  job ids the submission spawned. */
    fun submissionJobs(host: String, session: String, subId: Long): List<Long>

    /** GET /api/jobs/{jobId}. Returns one of: "success", "failure",
     *  "pending". Anything else is treated as failure (e.g. nova returns
     *  "error" for jobs the solver bailed on). */
    fun jobStatus(host: String, session: String, jobId: Long): String

    /** GET /api/jobs/{jobId}/calibration/. Returns the final sky position
     *  on success. Throws if the job has not finished solving yet. */
    fun jobCalibration(host: String, session: String, jobId: Long): PlateSolveResult
}

/** Default transport: a thin [java.net.HttpURLConnection] wrapper. Lives
 *  here (not in a separate file) because it has zero state and the
 *  controller holds no reference to it directly — pass an instance to
 *  the constructor. */
class HttpPlateSolveTransport : PlateSolveTransport {
    override fun login(host: String, apiKey: String?): String =
        plateSolvePostForm(host, "/api/login", formOf(
            "request-json" to """{"apikey":${quoteIfNotNull(apiKey)}}"""
        )).let { parseNovaStringField(it, "session")
            ?: throw IllegalStateException("login response missing 'session': $it") }

    override fun upload(host: String, session: String, requestJson: String, imageBytes: ByteArray): Long {
        val body = plateSolvePostMultipart(
            host = host,
            path = "/api/upload",
            fields = listOf(
                "session" to session,
                "request-json" to requestJson,
            ),
            fileField = "file",
            fileName = "image.jpg",
            fileBytes = imageBytes,
        )
        return parseNovaNumberField(body, "subid")?.toLong()
            ?: throw IllegalStateException("upload response missing 'subid': $body")
    }

    override fun submissionReady(host: String, session: String, subId: Long): Boolean =
        submissionJobs(host, session, subId).isNotEmpty()

    override fun submissionJobs(host: String, session: String, subId: Long): List<Long> {
        val body = plateSolveGet(
            host = host,
            path = "/api/submissions/$subId",
            query = "session=$session",
        )
        // nova returns a JSON object with a "jobs" array of integers.
        // We don't need full JSON parsing — extract [N, N, N] and walk it.
        val jobsMatch = Regex(""""jobs"\s*:\s*\[([^\]]*)]""").find(body)
            ?: return emptyList()
        val inner = jobsMatch.groupValues[1]
        if (inner.isBlank()) return emptyList()
        return inner.split(",").mapNotNull { it.trim().toLongOrNull() }
    }

    override fun jobStatus(host: String, session: String, jobId: Long): String {
        val body = plateSolveGet(
            host = host,
            path = "/api/jobs/$jobId",
            query = "session=$session",
        )
        return parseNovaStringField(body, "status")
            ?: throw IllegalStateException("job status response missing 'status': $body")
    }

    override fun jobCalibration(host: String, session: String, jobId: Long): PlateSolveResult {
        val body = plateSolveGet(
            host = host,
            path = "/api/jobs/$jobId/calibration/",
            query = "session=$session",
        )
        // Calibration endpoint returns either `null` (no result yet) or
        // a JSON object. We scrape the four fields we use; missing means
        // "not ready" and we let the caller decide.
        val ra = parseNovaNumberField(body, "ra")
        val dec = parseNovaNumberField(body, "dec")
        val pixscale = parseNovaNumberField(body, "pixscale")
        val orient = parseNovaNumberField(body, "orientation")
        val parity = parseNovaNumberField(body, "parity")?.toInt() ?: 0
        if (ra == null || dec == null || pixscale == null || orient == null) {
            throw IllegalStateException("calibration incomplete: $body")
        }
        return PlateSolveResult(
            raDeg = ra,
            decDeg = dec,
            pixscaleArcsec = pixscale,
            orientationDeg = orient,
            parity = parity,
            jobId = jobId,
        )
    }
}

// ----- HTTP helpers ---------------------------------------------------------
//
// Tiny wrappers around HttpURLConnection. We avoid pulling in Ktor/OkHttp
// because (a) the surface we need is two verbs and three endpoints, and
// (b) every KMP HTTP lib on the classpath today is "almost-but-not-quite"
// multiplatform. HttpURLConnection is in the JDK and the Android stdlib.

internal fun plateSolveGet(host: String, path: String, query: String): String {
    // The deprecated `URL(String, String, int, String)` constructor has
    // different parameter conventions across JDKs (and is removed on JDK
    // 21+), so we build the URL via URI+toURL() which is portable.
    val full = java.net.URI("https://$host$path?$query").toURL()
    val conn = full.openConnection() as java.net.HttpURLConnection
    conn.requestMethod = "GET"
    conn.connectTimeout = 15_000
    conn.readTimeout = 30_000
    return readHttpBody(conn)
}

internal fun plateSolvePostForm(host: String, path: String, form: String): String {
    val full = java.net.URI("https://$host$path").toURL()
    val conn = full.openConnection() as java.net.HttpURLConnection
    conn.requestMethod = "POST"
    conn.doOutput = true
    conn.connectTimeout = 15_000
    conn.readTimeout = 30_000
    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
    conn.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
    return readHttpBody(conn)
}

internal fun plateSolvePostMultipart(
    host: String,
    path: String,
    fields: List<Pair<String, String>>,
    fileField: String,
    fileName: String,
    fileBytes: ByteArray,
): String {
    val boundary = "----OpenPolarisBoundary${System.nanoTime()}"
    val full = java.net.URI("https://$host$path").toURL()
    val conn = full.openConnection() as java.net.HttpURLConnection
    conn.requestMethod = "POST"
    conn.doOutput = true
    conn.useCaches = false
    conn.connectTimeout = 15_000
    conn.readTimeout = 60_000
    conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
    conn.outputStream.use { out ->
        for ((name, value) in fields) {
            out.write("--$boundary\r\n".toByteArray(Charsets.UTF_8))
            out.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray(Charsets.UTF_8))
            out.write(value.toByteArray(Charsets.UTF_8))
            out.write("\r\n".toByteArray(Charsets.UTF_8))
        }
        out.write("--$boundary\r\n".toByteArray(Charsets.UTF_8))
        out.write("Content-Disposition: form-data; name=\"$fileField\"; filename=\"$fileName\"\r\n".toByteArray(Charsets.UTF_8))
        out.write("Content-Type: image/jpeg\r\n\r\n".toByteArray(Charsets.UTF_8))
        out.write(fileBytes)
        out.write("\r\n".toByteArray(Charsets.UTF_8))
        out.write("--$boundary--\r\n".toByteArray(Charsets.UTF_8))
    }
    return readHttpBody(conn)
}

private fun readHttpBody(conn: java.net.HttpURLConnection): String {
    val code = conn.responseCode
    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
    return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
}

private fun formOf(vararg pairs: Pair<String, String>): String =
    pairs.joinToString("&") { (k, v) ->
        java.net.URLEncoder.encode(k, "UTF-8") + "=" + java.net.URLEncoder.encode(v, "UTF-8")
    }

private fun quoteIfNotNull(s: String?): String = if (s == null) "null" else "\"$s\""

// ----- Nova JSON field scrapers --------------------------------------------
//
// nova's responses are JSON, but the structures are loose (top-level
// strings for some endpoints, objects-with-fields for others). The fields
// we need are always primitive — we extract them with regexes rather than
// pull in a JSON parser dependency.

internal fun parseNovaStringField(body: String, field: String): String? {
    val m = Regex(""""$field"\s*:\s*"((?:\\.|[^"\\])*)"""").find(body) ?: return null
    return m.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\")
}

internal fun parseNovaNumberField(body: String, field: String): Double? {
    val m = Regex(""""$field"\s*:\s*(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)""").find(body) ?: return null
    return m.groupValues[1].toDoubleOrNull()
}

// =============================================================================
// Controller
// =============================================================================

/** Drives a blind plate-solve against nova.astrometry.net. Construct with
 *  the transport seam ([HttpPlateSolveTransport] in production). */
class PlateSolveController(
    private val transport: PlateSolveTransport = HttpPlateSolveTransport(),
    private val host: String = "nova.astrometry.net",
    private val apiKey: String? = null,
    /** Total attempts to poll the submission/job status before giving up. */
    private val pollAttempts: Int = 30,
    /** Delay between polls. Nova's job queue is throttled so we wait
     *  generously; 2 s × 30 = up to 60 s end-to-end. */
    private val pollDelayMs: Long = 2_000L,
    /** IO dispatcher for blocking HTTP calls. Tests can swap to
     *  Dispatchers.Unconfined for fast-running scenarios. */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /** Solve [imageBytes] (a JPEG from the camera) and return the sky
     *  position. The [onStatus] callback fires on every state transition
     *  so the UI can render progress labels. The pipeline runs in the
     *  caller's coroutine — cancel by cancelling the surrounding
     *  coroutine. */
    suspend fun solve(
        imageBytes: ByteArray,
        onStatus: (PlateSolveStatus) -> Unit = {},
    ): PlateSolveResult = coroutineScope {
        try {
            onStatus(PlateSolveStatus.LoggingIn)
            val session = withContext(ioDispatcher) { transport.login(host, apiKey) }
            ensureActive()

            onStatus(PlateSolveStatus.Uploading(0L, imageBytes.size.toLong()))
            val requestJson = buildRequestJson()
            val subId = withContext(ioDispatcher) {
                transport.upload(host, session, requestJson, imageBytes)
            }
            onStatus(PlateSolveStatus.Uploading(imageBytes.size.toLong(), imageBytes.size.toLong()))
            ensureActive()

            // nova takes a few seconds to spin up a job. We poll the
            // submission's "jobs" array until it's non-empty.
            val jobIds = pollForJobs(session, subId, onStatus)
            if (jobIds.isEmpty()) {
                throw IllegalStateException(
                    "submission $subId produced no jobs after $pollAttempts attempts"
                )
            }
            ensureActive()

            // nova runs jobs in parallel; we wait on the first. (For
            // most users there's only ever one job per submission.)
            for (jobId in jobIds) {
                val status = pollForJobCompletion(session, jobId, onStatus)
                if (status == "success") {
                    val result = withContext(ioDispatcher) {
                        transport.jobCalibration(host, session, jobId)
                    }
                    onStatus(PlateSolveStatus.Solved(result))
                    return@coroutineScope result
                }
            }
            throw IllegalStateException("none of the submission's jobs succeeded: $jobIds")
        } catch (ce: CancellationException) {
            // Don't wrap cancellation in a Failed state — let it propagate
            // so the caller's coroutine machinery sees it.
            throw ce
        } catch (t: Throwable) {
            val msg = t.message ?: t::class.simpleName ?: "unknown error"
            onStatus(PlateSolveStatus.Failed(msg))
            throw t
        }
    }

    private suspend fun pollForJobs(
        session: String,
        subId: Long,
        onStatus: (PlateSolveStatus) -> Unit,
    ): List<Long> {
        repeat(pollAttempts) { attempt ->
            ensureActive()
            onStatus(PlateSolveStatus.PollingSubmission(subId, attempt + 1))
            val jobs = withContext(ioDispatcher) {
                try {
                    transport.submissionJobs(host, session, subId)
                } catch (t: Throwable) {
                    // Transient HTTP failure — log via status and keep
                    // polling until we exhaust attempts.
                    null
                }
            }
            if (jobs != null && jobs.isNotEmpty()) return jobs
            delay(pollDelayMs)
        }
        return emptyList()
    }

    private suspend fun pollForJobCompletion(
        session: String,
        jobId: Long,
        onStatus: (PlateSolveStatus) -> Unit,
    ): String {
        repeat(pollAttempts) { attempt ->
            ensureActive()
            onStatus(PlateSolveStatus.PollingJob(jobId, attempt + 1))
            val status = withContext(ioDispatcher) {
                try {
                    transport.jobStatus(host, session, jobId)
                } catch (t: Throwable) {
                    null
                }
            } ?: run {
                delay(pollDelayMs)
                return@repeat
            }
            if (status == "success" || status == "failure") return status
            delay(pollDelayMs)
        }
        throw IllegalStateException("job $jobId did not finish after $pollAttempts attempts")
    }

    private suspend fun ensureActive() {
        if (!coroutineContext[kotlinx.coroutines.Job]!!.isActive) {
            throw CancellationException("caller cancelled the plate-solve coroutine")
        }
    }

    private fun buildRequestJson(): String = buildString {
        append("{")
        // The minimum nova needs to start a job. "allow_sextractor" and
        // "allow_modular" are left as defaults (true) so the server
        // picks the best pipeline for our JPEG.
        if (apiKey != null) {
            // Sent at login time already, but include for safety.
            append("\"apikey\":\"$apiKey\",")
        }
        append("\"public\":true")
        append("}")
    }
}
