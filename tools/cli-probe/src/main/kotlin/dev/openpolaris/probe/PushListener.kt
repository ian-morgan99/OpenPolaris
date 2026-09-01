package dev.openpolaris.probe

import dev.openpolaris.core.protocol.CommandBuilder
import dev.openpolaris.core.protocol.ResponseParser
import java.io.File
import java.io.PrintStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.system.exitProcess

/**
 * cli-probe/listen-push: a long-lived passive listener for the Open Polaris
 * control channel. Unlike `listen <code> <seconds>` which only matches one
 * code and writes nothing, PushListener logs EVERY incoming frame to a file
 * and prints a one-line summary to stdout.
 *
 * Usage:
 *   listen-push <seconds> [host] [port] [--send <code>[:k:v;k:v]]
 *                              [--send-step <n>] [--out <file>]
 *                              [--send-delay <ms>]
 *
 * Why a separate tool?
 *   Several codes are push-mode (525 Tempa, 524/517 AHRS, 779, 808) and never
 *   answer a direct request. The previous `listen` mode filtered to a single
 *   code and dropped everything else. This tool records the full conversation
 *   so we can correlate a 526 send with whatever unsolicited push traffic
 *   the gimbal emits in response.
 */
fun main(args: Array<String>) {
    if (args.isEmpty() || args[0] == "-h" || args[0] == "--help" || args[0] == "help") {
        System.err.println(
            "usage: listen-push <seconds> [host] [port] [--send <code>[:k:v;k:v]]" +
                " [--send-step <n>] [--out <file>] [--send-delay <ms>]",
        )
        exitProcess(2)
    }
    val secs = args[0].toIntOrNull() ?: run {
        System.err.println("invalid seconds: ${args[0]}")
        exitProcess(2)
    }
    var host: String? = null
    var port: Int? = null
    var sendFrame: ByteArray? = null
    var outFile = "push.log"
    var sendDelayMs: Long = -1L // -1 = default (halfway)

    var i = 1
    while (i < args.size) {
        when (val a = args[i]) {
            "--send" -> {
                val spec = args.getOrNull(i + 1) ?: run {
                    System.err.println("--send needs an argument")
                    exitProcess(2)
                }
                sendFrame = buildSendFrame(spec)
                i += 2
            }
            "--send-step" -> {
                val n = args.getOrNull(i + 1)?.toIntOrNull() ?: run {
                    System.err.println("--send-step needs an integer")
                    exitProcess(2)
                }
                sendFrame = buildSendFrame("526:step:$n")
                i += 2
            }
            "--out" -> {
                outFile = args.getOrNull(i + 1) ?: run {
                    System.err.println("--out needs a file path")
                    exitProcess(2)
                }
                i += 2
            }
            "--send-delay" -> {
                sendDelayMs = args.getOrNull(i + 1)?.toLongOrNull() ?: run {
                    System.err.println("--send-delay needs an integer (milliseconds)")
                    exitProcess(2)
                }
                if (sendDelayMs < 0) {
                    System.err.println("--send-delay must be non-negative")
                    exitProcess(2)
                }
                i += 2
            }
            else -> {
                when {
                    host == null -> host = a
                    port == null -> port = a.toIntOrNull() ?: run {
                        System.err.println("invalid port: $a")
                        exitProcess(2)
                    }
                    else -> {
                        System.err.println("unexpected positional arg: $a (host/port already set)")
                        exitProcess(2)
                    }
                }
                i++
            }
        }
    }
    val h = host ?: "192.168.0.1"
    val p = port ?: 9090

    val out = File(outFile)
    out.parentFile?.mkdirs()
    val outStream = PrintStream(out.outputStream(), true, Charsets.UTF_8)
    val effectiveDelay = if (sendDelayMs >= 0) "${sendDelayMs}ms" else "${secs * 1000L / 2}ms (default half)"
    outStream.println(
        "# push-log  host=$h  port=$p  duration=${secs}s  sendFrame=${sendFrame?.let { String(it) }}" +
            "  sendDelayMs=$effectiveDelay",
    )

    println(
        "PushListener: $h:$p for ${secs}s, sendFrame=${sendFrame?.let { String(it) }}" +
            ", sendDelay=$effectiveDelay, log=$out",
    )
    val socket = Socket()
    socket.connect(InetSocketAddress(h, p), 5000)
    socket.soTimeout = 200
    socket.tcpNoDelay = true

    try {
        val deadline = System.currentTimeMillis() + secs * 1000L
        var totalFrames = 0
        val seen = HashMap<Int, Int>()
        val pending = StringBuilder()
        val startMs = System.currentTimeMillis()

        while (System.currentTimeMillis() < deadline) {
            val available = try {
                socket.getInputStream().available()
            } catch (_: Exception) {
                0
            }
            if (available > 0) {
                val buf = ByteArray(available.coerceAtLeast(1024))
                val n = try {
                    socket.getInputStream().read(buf)
                } catch (_: java.net.SocketTimeoutException) {
                    0
                }
                if (n > 0) {
                    pending.append(String(buf, 0, n, Charsets.UTF_8))
                }
            } else {
                Thread.sleep(20)
            }

            var hashIdx = pending.indexOf('#')
            while (hashIdx >= 0) {
                val frameStr = pending.substring(0, hashIdx + 1)
                pending.delete(0, hashIdx + 1)
                val ts = System.currentTimeMillis() - startMs
                val line = "t=${ts}ms  raw=${frameStr.replace("\n", "\\n")}"
                outStream.println(line)
                val (parsed, _) = ResponseParser().parse(frameStr.toByteArray(Charsets.UTF_8))
                for (fr in parsed) {
                    totalFrames++
                    seen[fr.code] = (seen[fr.code] ?: 0) + 1
                    val keyOut = if (fr.fields.isNotEmpty()) {
                        fr.fields.entries.joinToString(";") { "${it.key}=${it.value}" }
                    } else {
                        "(no k:v fields)"
                    }
                    println("  t=${ts}ms  code=${fr.code}  $keyOut")
                }
                hashIdx = pending.indexOf('#')
            }

            if (sendFrame != null) {
                val elapsed = System.currentTimeMillis() - startMs
                val trigger = if (sendDelayMs >= 0) sendDelayMs else secs * 1000L / 2
                if (elapsed >= trigger) {
                    val ts = elapsed
                    outStream.println("t=${ts}ms  >>> SEND  ${String(sendFrame!!)}")
                    println("  t=${ts}ms  >>> SEND  ${String(sendFrame!!)}")
                    socket.getOutputStream().write(sendFrame)
                    socket.getOutputStream().flush()
                    sendFrame = null
                }
            }
        }

        println("done.  frames=$totalFrames  byCode=${seen.toSortedMap()}")
        outStream.println("# done.  frames=$totalFrames  byCode=${seen.toSortedMap()}")
    } finally {
        outStream.close()
        socket.close()
    }
}

private fun buildSendFrame(spec: String): ByteArray {
    val colon = spec.indexOf(':')
    if (colon <= 0) {
        val code = spec.toIntOrNull() ?: error("invalid --send '$spec' (need <code>[:k=v;k=v;...])")
        return dev.openpolaris.core.protocol.command(code)
    }
    val code = spec.substring(0, colon).toIntOrNull() ?: error("invalid --send code in '$spec'")
    val payload = spec.substring(colon + 1)
    if (payload.isBlank()) return dev.openpolaris.core.protocol.command(code)
    val builder = CommandBuilder(code)
    for (p in payload.split(';').filter { it.isNotBlank() }) {
        val i = p.indexOf(':')
        if (i <= 0) error("invalid payload segment '$p' (need k:v)")
        builder.put(p.substring(0, i).trim(), p.substring(i + 1).trim())
    }
    return builder.build()
}
