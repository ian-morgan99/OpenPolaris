package dev.openpolaris.probe

import dev.openpolaris.core.protocol.Codes
import dev.openpolaris.core.protocol.ResponseParser
import dev.openpolaris.core.protocol.command
import java.net.InetSocketAddress
import java.net.Socket

private fun log(s: String) { println(s); System.out.flush() }

/** Send a comma-separated list of codes to host:port and print each response. */
fun main(args: Array<String>) {
    val host = args.getOrElse(0) { "192.168.0.1" }
    val port = args.getOrElse(1) { "9090" }.toIntOrNull() ?: 9090
    val codes = args.getOrElse(2) { "524,544,802,824,775,778,779" }
        .split(",").map { it.trim().toInt() }
    log("burst → $host:$port codes=$codes")

    val socket = Socket()
    socket.connect(InetSocketAddress(host, port), 5000)
    val out = socket.getOutputStream()
    val `in` = socket.getInputStream()
    val parser = ResponseParser()

    for (c in codes) {
        out.write(command(c)); out.flush()
        log("  sent code=$c")

        // short per-code window — setters (e.g. 544) ack-free, push codes are async
        socket.soTimeout = 1500
        val pending = java.io.ByteArrayOutputStream()
        val buf = ByteArray(4096)
        try {
            while (true) {
                val n = `in`.read(buf)
                if (n < 0) break
                if (n == 0) break
                pending.write(buf, 0, n)
            }
        } catch (_: java.net.SocketTimeoutException) {
            // expected for setter / push codes
        }
        val bytes = pending.toByteArray()
        log("  drained ${bytes.size}B raw=${String(bytes, Charsets.US_ASCII).trim()}")
        if (bytes.isEmpty()) {
            log("  code=$c <no response> (setter or push-mode)"); continue
        }
        val (frames, _) = parser.parse(bytes)
        if (frames.isEmpty()) {
            log("  code=$c unparsed")
        } else for (f in frames) {
            val fields = f.fields.entries.joinToString(" ") { "${it.key}=${it.value}" }
            log("  code=${f.code} $fields")
        }
    }
    socket.close()
}
