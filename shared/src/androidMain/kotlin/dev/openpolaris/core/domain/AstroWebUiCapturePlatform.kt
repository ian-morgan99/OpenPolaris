package dev.openpolaris.core.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

actual fun createAstroWebUiCaptureTransport(): AstroWebUiCaptureTransport =
    AstroWebUiCaptureTransport { request ->
        astroWebUiHttpPost(
            request.host,
            request.port,
            request.path,
            request.contentType,
            request.body.encodeToByteArray(),
            ASTRO_CAPTURE_ACK_MAX_BYTES,
        )
    }

actual fun createAstroWebUiCaptureClient(): AstroWebUiCaptureClient =
    AstroWebUiCaptureClient(createAstroWebUiCaptureTransport())

private suspend fun astroWebUiHttpPost(
    host: String,
    port: Int,
    path: String,
    contentType: String,
    body: ByteArray,
    maxBytes: Long,
): AstroWebUiHttpResponse = withContext(Dispatchers.IO) {
    var connection: HttpURLConnection? = null
    try {
        connection = (URL("http://$host:$port$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5_000
            readTimeout = 10_000
            doInput = true
            doOutput = true
            useCaches = false
            instanceFollowRedirects = false
            setFixedLengthStreamingMode(body.size)
            setRequestProperty("Connection", "close")
            setRequestProperty("User-Agent", "OpenPolaris/0.1")
            setRequestProperty("Content-Type", contentType)
        }
        connection.outputStream.use { it.write(body) }
        val status = connection.responseCode
        val type = connection.contentType ?: ""
        val input = if (status in 200..299) connection.inputStream else connection.errorStream
        val bytes = input?.use { readBounded(it, maxBytes) } ?: ByteArray(0)
        AstroWebUiHttpResponse(status, type, bytes)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        AstroWebUiHttpResponse(-1, "", ByteArray(0), "Astro WebUI POST failed: ${error.message ?: error::class.simpleName}")
    } finally {
        connection?.disconnect()
    }
}

private fun readBounded(input: java.io.InputStream, maxBytes: Long): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    var total = 0L
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        total += read
        if (total > maxBytes) error("response exceeds $maxBytes bytes")
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

actual fun createAstroWebUiProgrammeControlClient(): AstroWebUiProgrammeControlClient =
    DefaultAstroWebUiProgrammeControlClient(createAstroWebUiCaptureTransport())
