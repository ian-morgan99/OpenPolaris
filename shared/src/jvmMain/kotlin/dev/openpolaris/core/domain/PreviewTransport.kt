package dev.openpolaris.core.domain

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Plain JVM/Android implementation of the MJPEG-over-HTTP transport.
 * See the `interface` declaration in commonMain for the contract.
 */
class JvmPreviewTransport(
    private val onFrame: (ByteArray) -> Boolean,
    private val onError: (Throwable) -> Unit = {},
) : PreviewTransport {
    @Volatile private var cancelled: Boolean = false
    private var connection: HttpURLConnection? = null

    override fun start(host: String, port: Int, path: String) {
        val url = URL("http://$host:$port$path")
        var conn: HttpURLConnection? = null
        try {
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5_000
                readTimeout = 0 // never time out the read on an open stream
                doInput = true
                useCaches = false
                instanceFollowRedirects = false
                setRequestProperty("Connection", "close")
                setRequestProperty("User-Agent", "OpenPolaris/0.1")
            }
            this.connection = conn
            val code = conn.responseCode
            if (code !in 200..299) {
                onError(IllegalStateException("Preview HTTP $code"))
                return
            }
            val contentType = conn.contentType ?: ""
            val boundary = parseBoundary(contentType)
                ?: run {
                    onError(IllegalStateException("Preview not multipart: $contentType"))
                    return
                }
            val stream = BufferedInputStream(conn.inputStream, 64 * 1024)
            val dis = DataInputStream(stream)
            readParts(dis, boundary)
        } catch (t: Throwable) {
            if (!cancelled) onError(t)
        } finally {
            runCatching { conn?.disconnect() }
            connection = null
        }
    }

    override fun stop() {
        cancelled = true
        runCatching { connection?.disconnect() }
    }

    private fun readParts(stream: InputStream, boundary: String) {
        val dashBoundary = "--$boundary"
        val dis = if (stream is DataInputStream) stream else DataInputStream(stream)
        // Loop forever: the server keeps producing parts until we disconnect.
        while (!cancelled) {
            // 1. Read the boundary delimiter line.
            val line = dis.readLine() ?: return
            if (line.isEmpty()) continue
            if (line != dashBoundary) {
                // Tolerate stray whitespace, but otherwise we are out of sync.
                if (line.startsWith("--") && line.length >= dashBoundary.length + 2) return
                continue
            }
            // 2. Read part headers until blank line.
            var contentLength: Int? = null
            while (true) {
                val h = dis.readLine() ?: return
                if (h.isEmpty()) break
                val colon = h.indexOf(':')
                if (colon <= 0) continue
                val name = h.substring(0, colon).trim().lowercase()
                val value = h.substring(colon + 1).trim()
                if (name == "content-length") contentLength = value.toIntOrNull()
            }
            // 3. Read the JPEG bytes from the same stream — dis.readLine() above is
            //    byte-level and does not prefetch, so the body starts at exactly the
            //    next byte we read here.
            val bytes: ByteArray = if (contentLength != null && contentLength >= 0) {
                readExactly(dis, contentLength)
            } else {
                readUntilBoundary(dis, dashBoundary)
            }
            if (cancelled) return
            // 4. Hand off; onFrame returning false means "drop".
            onFrame(bytes)
            // 5. Consume the trailing \r\n (or \n) after the part body.
            readCrlf(dis)
        }
    }

    private fun readExactly(stream: InputStream, n: Int): ByteArray {
        val out = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = stream.read(out, off, n - off)
            if (r < 0) error("EOF in $n-byte part at offset $off")
            off += r
        }
        return out
    }

    /** Read until we hit the next `--<boundary>` line; the boundary CRLF is consumed. */
    private fun readUntilBoundary(stream: InputStream, dashBoundary: String): ByteArray {
        val out = ArrayList<ByteArray>(8)
        val scan = ByteArray(4096)
        val needle = (dashBoundary + "\r\n").toByteArray(Charsets.US_ASCII)
        var tail = ByteArray(0)
        while (true) {
            val r = stream.read(scan)
            if (r < 0) return concat(out, tail)
            tail = concat(tail, scan.copyOf(r))
            val hit = indexOf(tail, needle)
            if (hit >= 0) {
                if (hit > 0) out.add(tail.copyOfRange(0, hit))
                // Leave the boundary in the stream for the outer loop to consume.
                return concat(out, ByteArray(0))
            }
            // Keep the last (needle.size - 1) bytes as the tail in case the
            // boundary straddles two reads.
            if (tail.size > needle.size) {
                out.add(tail.copyOfRange(0, tail.size - (needle.size - 1)))
                tail = tail.copyOfRange(tail.size - (needle.size - 1), tail.size)
            }
        }
    }

    private fun readCrlf(stream: InputStream) {
        val a = stream.read()
        if (a == '\r'.code) {
            val b = stream.read()
            if (b != '\n'.code && b != -1) {
                // Tolerate servers that use bare \n.
            }
        }
    }

    private fun concat(parts: List<ByteArray>, tail: ByteArray): ByteArray {
        var total = tail.size
        for (p in parts) total += p.size
        val out = ByteArray(total)
        var off = 0
        for (p in parts) {
            System.arraycopy(p, 0, out, off, p.size); off += p.size
        }
        System.arraycopy(tail, 0, out, off, tail.size)
        return out
    }

    private fun concat(a: ByteArray, b: ByteArray): ByteArray {
        if (a.isEmpty()) return b
        if (b.isEmpty()) return a
        val out = ByteArray(a.size + b.size)
        System.arraycopy(a, 0, out, 0, a.size)
        System.arraycopy(b, 0, out, a.size, b.size)
        return out
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty()) return 0
        outer@ for (i in 0..(haystack.size - needle.size)) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private fun parseBoundary(contentType: String): String? {
        // e.g. "multipart/x-mixed-replace; boundary=myboundary"
        for (part in contentType.split(';')) {
            val p = part.trim()
            if (p.startsWith("boundary=", ignoreCase = true)) {
                var v = p.substring("boundary=".length)
                if (v.startsWith('"') && v.endsWith('"')) v = v.substring(1, v.length - 1)
                return v
            }
        }
        return null
    }
}

/** JVM-specific default. HttpsURLConnection import keeps the file JVM-only if it ever grows. */
@Suppress("unused")
private val _jvmOnlyAnchor: Class<HttpsURLConnection> = HttpsURLConnection::class.java

actual fun createPreviewTransport(
    onFrame: (ByteArray) -> Boolean,
    onError: (Throwable) -> Unit,
): PreviewTransport = JvmPreviewTransport(onFrame, onError)
