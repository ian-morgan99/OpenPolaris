package dev.openpolaris.core.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

actual suspend fun astroWebUiHttpGet(host: String, port: Int, path: String, maxBytes: Long): AstroWebUiHttpResponse =
    withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL("http://$host:$port$path").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5_000
                readTimeout = 30_000
                doInput = true
                useCaches = false
                instanceFollowRedirects = false
                setRequestProperty("Connection", "close")
                setRequestProperty("User-Agent", "OpenPolaris/0.1")
            }
            val statusCode = connection.responseCode
            val contentType = connection.contentType ?: ""
            if (statusCode !in 200..299) return@withContext AstroWebUiHttpResponse(statusCode, contentType, ByteArray(0))
            val contentLength = connection.contentLengthLong
            if (contentLength > maxBytes) throw IllegalStateException("response exceeds $maxBytes bytes")
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) throw IllegalStateException("response exceeds $maxBytes bytes")
                    output.write(buffer, 0, read)
                }
            }
            AstroWebUiHttpResponse(statusCode, contentType, output.toByteArray())
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Throwable) {
            AstroWebUiHttpResponse(-1, "", ByteArray(0), "Astro WebUI request failed: ${error.message ?: error::class.simpleName}")
        } finally {
            connection?.disconnect()
        }
    }

actual fun createAstroWebUiMediaClient(): AstroWebUiMediaClient = DefaultAstroWebUiMediaClient()
