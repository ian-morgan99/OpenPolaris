package dev.openpolaris.core.domain

/**
 * JVM-side actual of [defaultPlateSolveTransport] and the
 * [HttpPlateSolveTransport] implementation that backs it.
 *
 * Everything `java.net.*` and `Charsets.UTF_8` lives here because
 * commonMain cannot see those types. Tests inject a fake transport
 * directly into [PlateSolveController], so this factory is only
 * consulted in production wiring.
 */

actual fun defaultPlateSolveTransport(): PlateSolveTransport = HttpPlateSolveTransport()

/**
 * Default transport: a thin [java.net.HttpURLConnection] wrapper.
 *
 * We avoid pulling in Ktor/OkHttp because (a) the surface we need is
 * two verbs and three endpoints, and (b) every KMP HTTP lib on the
 * classpath today is "almost-but-not-quite" multiplatform.
 * `HttpURLConnection` is in the JDK and the Android stdlib.
 */
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
