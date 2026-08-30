package dev.openpolaris.probe

import dev.openpolaris.core.protocol.CommandTable
import dev.openpolaris.core.protocol.ResponseParser
import dev.openpolaris.core.protocol.command
import java.net.InetSocketAddress
import java.net.Socket

private fun log(s: String) { println(s); System.out.flush() }

/**
 * Result of parsing command-line arguments. Host/port default to the real gimbal
 * (192.168.0.1:9090); codes default to a hand-picked smoke-test set. Pass `--full`
 * as a third argument to use the canonical [CommandTable.BURST_PRE_CAMERA] list
 * (all 9 pre-camera GETs) instead.
 */
data class BurstArgs(
    val host: String,
    val port: Int,
    val codes: List<Int>,
    val full: Boolean = false,
)

/** Pure, testable argument parser. */
internal fun parseBurstArgs(args: Array<String>): BurstArgs {
    val host = args.getOrNull(0) ?: "192.168.0.1"
    val port = args.getOrNull(1)?.toIntOrNull() ?: 9090
    val full = args.getOrNull(2) == "--full"
    val codes = if (full) {
        CommandTable.BURST_PRE_CAMERA.map { it.code }
    } else {
        (args.getOrNull(2) ?: "524,544,802,824,775,778,779")
            .split(",").map { it.trim().toInt() }
    }
    return BurstArgs(host, port, codes, full)
}

/** Run the burst against host:port. Returns the list of per-code result lines that
 *  were written to [sink] so the test suite can assert on them. Errors are converted
 *  to "<error>" lines rather than thrown, so the burst always runs to completion. */
fun runBurst(args: BurstArgs, sink: (String) -> Unit = ::log): List<String> {
    val results = mutableListOf<String>()
    val header = "burst → ${args.host}:${args.port} codes=${args.codes}" +
        if (args.full) " (full pre-camera burst)" else ""
    sink(header); results += header
    try {
        val socket = Socket()
        socket.connect(InetSocketAddress(args.host, args.port), 5000)
        try {
            val out = socket.getOutputStream()
            val `in` = socket.getInputStream()
            val parser = ResponseParser()
            for (c in args.codes) {
                out.write(command(c)); out.flush()
                val line = "  sent code=$c"
                sink(line); results += line

                // short per-code window — setters (e.g. 544) ack-free, push codes are async
                socket.soTimeout = 1500
                val pending = java.io.ByteArrayOutputStream()
                val buf = ByteArray(4096)
                try {
                    while (true) {
                        val n = `in`.read(buf)
                        if (n <= 0) break
                        pending.write(buf, 0, n)
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    // expected for setter / push codes
                }
                val bytes = pending.toByteArray()
                val drained = "  drained ${bytes.size}B raw=${String(bytes, Charsets.US_ASCII).trim()}"
                sink(drained); results += drained
                if (bytes.isEmpty()) {
                    val noResp = "  code=$c <no response> (setter or push-mode)"
                    sink(noResp); results += noResp
                    continue
                }
                val (frames, _) = parser.parse(bytes)
                if (frames.isEmpty()) {
                    val unparsed = "  code=$c unparsed"
                    sink(unparsed); results += unparsed
                } else for (f in frames) {
                    val fields = f.fields.entries.joinToString(" ") { "${it.key}=${it.value}" }
                    val parsed = "  code=${f.code} $fields"
                    sink(parsed); results += parsed
                }
            }
        } finally {
            runCatching { socket.close() }
        }
    } catch (e: Exception) {
        val err = "  <error: ${e.javaClass.simpleName}: ${e.message}>"
        sink(err); results += err
    }
    return results
}

fun main(args: Array<String>) {
    runBurst(parseBurstArgs(args))
}
