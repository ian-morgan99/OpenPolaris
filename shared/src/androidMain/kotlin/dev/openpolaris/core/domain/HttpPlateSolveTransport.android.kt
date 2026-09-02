package dev.openpolaris.core.domain

actual fun defaultPlateSolveTransport(): PlateSolveTransport = HttpPlateSolveTransport()

/**
 * Android-side transport backed by [java.net.HttpURLConnection] (available in
 * the Android stdlib). Implementation mirrors the JVM side; we keep a
 * separate file because the jvmMain and androidMain source sets do not
 * see each other.
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

internal fun plateSolveGet(host: String, path: String, query: String): String {
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
    val boundary = "----OpenPolarisBoundary${java.lang.System.nanoTime()}"
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
