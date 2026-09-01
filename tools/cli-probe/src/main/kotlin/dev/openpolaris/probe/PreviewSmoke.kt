package dev.openpolaris.probe

import dev.openpolaris.core.domain.JvmPreviewTransport
import java.net.ServerSocket

fun previewSmoke(): Int {
    val payload = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
    val boundary = "polaris-smoke"
    val server = ServerSocket(0, 0, java.net.InetAddress.getByName("127.0.0.1"))
    val port = server.localPort

    val feeder = Thread {
        try {
            server.use { srv ->
                val client = srv.accept()
                client.use { c ->
                    val out = c.getOutputStream()
                    val header = "HTTP/1.1 200 OK\r\nContent-Type: multipart/x-mixed-replace; boundary=$boundary\r\nConnection: close\r\n\r\n"
                    out.write(header.toByteArray(Charsets.US_ASCII))
                    out.write(("--$boundary\r\n").toByteArray(Charsets.US_ASCII))
                    out.write("Content-Type: image/jpeg\r\n".toByteArray(Charsets.US_ASCII))
                    out.write(("Content-Length: ${payload.size}\r\n").toByteArray(Charsets.US_ASCII))
                    out.write("\r\n".toByteArray(Charsets.US_ASCII))
                    out.write(payload)
                    out.write("\r\n".toByteArray(Charsets.US_ASCII))
                    out.flush()
                    Thread.sleep(200)
                }
            }
        } catch (_: Throwable) {}
    }
    feeder.isDaemon = true
    feeder.start()

    var received: ByteArray? = null
    var error: Throwable? = null
    val transport = JvmPreviewTransport(
        onFrame = { received = it; true },
        onError = { error = it },
    )
    val worker = Thread { transport.start("127.0.0.1", port, "/preview") }
    worker.start()
    worker.join(3_000)
    transport.stop()

    return when {
        error != null -> { System.err.println("preview-smoke: transport error: ${error!!.message}"); 1 }
        received == null -> { System.err.println("preview-smoke: no frame received within 3s"); 1 }
        !received!!.contentEquals(payload) -> { System.err.println("preview-smoke: payload mismatch"); 1 }
        else -> { println("preview-smoke: OK (${received!!.size} bytes from 127.0.0.1:$port)"); 0 }
    }
}
