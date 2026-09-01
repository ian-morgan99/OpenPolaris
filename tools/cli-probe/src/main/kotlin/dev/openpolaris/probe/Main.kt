package dev.openpolaris.probe

import dev.openpolaris.core.protocol.Codes
import dev.openpolaris.core.protocol.CommandBuilder
import dev.openpolaris.core.protocol.ResponseParser
import dev.openpolaris.core.protocol.command
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.system.exitProcess

/**
 * cli-probe: a tiny interactive driver for the Open Polaris protocol.
 *
 * Modes:
 *   probe                              connect, send 284 (status), parse and print. Default v0 behaviour.
 *   probe send <code> [payload] [host] [port]
 *                                      build a frame with the given code and `k:v;...` payload,
 *                                      send it, read one round-trip, print every parsed frame.
 *   probe burst <code> <count> [host] [port]
 *                                      send the given code N times back-to-back, print a one-line
 *                                      summary per response. Used for the idempotency test.
 *   probe listen <code> <seconds> [host] [port]
 *                                      connect, read frames for N seconds without sending anything.
 *                                      Used to confirm an AHRS stream (518) is actually arriving.
 *   probe preview-smoke
 *                                      local-loopback smoke test for JvmPreviewTransport: spins up a
 *                                      ServerSocket, feeds a single multipart/x-mixed-replace part
 *                                      containing a minimal 4-byte JPEG, asserts the bytes arrive
 *                                      intact through the transport. Exit 0 on pass, 1 on fail.
 *
 * Defaults: host=192.168.0.1, port=9090 (control channel).
 */
fun main(args: Array<String>) {
    when (val mode = args.getOrNull(0)) {
        null, "status" -> pollStatus(args.drop(1).toTypedArray())
        "send" -> sendOnce(args.drop(1).toTypedArray())
        "burst" -> sendBurst(args.drop(1).toTypedArray())
        "listen" -> listenOnly(args.drop(1).toTypedArray())
        "preview-smoke" -> exitProcess(previewSmoke())
        "help", "-h", "--help" -> {
            println("usage: see KDoc in Main.kt — modes: status|send|burst|listen|preview-smoke")
        }
        else -> {
            System.err.println("unknown mode: $mode (try: status|send|burst|listen|preview-smoke)")
            exitProcess(2)
        }
    }
}

private fun defaultHost(args: Array<String>, idx: Int): String = args.getOrNull(idx) ?: "192.168.0.1"
private fun defaultPort(args: Array<String>, idx: Int): Int =
    args.getOrNull(idx)?.toIntOrNull() ?: 9090

private fun openSocket(host: String, port: Int, readTimeoutMs: Int = 3000): Socket {
    val s = Socket()
    s.connect(InetSocketAddress(host, port), 5000)
    s.soTimeout = readTimeoutMs
    return s
}

private fun parsePayloadToBuilder(code: Int, payload: String?): ByteArray {
    if (payload.isNullOrEmpty()) return command(code)
    val parts = payload.split(';').filter { it.isNotBlank() }
    val builder = CommandBuilder(code)
    for (p in parts) {
        val i = p.indexOf(':')
        if (i <= 0) {
            // Pass through raw segment (no colon) — should not happen for known codes
            builder.putRaw("$p;")
            continue
        }
        val k = p.substring(0, i).trim()
        val v = p.substring(i + 1).trim()
        builder.put(k, v)
    }
    return builder.build()
}

private fun printFrames(raw: ByteArray, n: Int) {
    if (n <= 0) {
        println("(no bytes read)")
        return
    }
    val parser = ResponseParser()
    val (frames, _) = parser.parse(raw.copyOf(n))
    if (frames.isEmpty()) {
        println("unparsed: ${String(raw, 0, n, Charsets.US_ASCII).trim()}")
    } else {
        frames.forEach { f ->
            println("code=${f.code}")
            f.fields.forEach { (k, v) -> println("  $k = $v") }
        }
    }
}

private fun pollStatus(args: Array<String>) {
    val host = defaultHost(args, 0)
    val port = defaultPort(args, 1)
    println("Open Polaris probe → $host:$port (status poll, code 284)")
    Socket().use { socket ->
        try {
            socket.connect(InetSocketAddress(host, port), 5000)
            socket.soTimeout = 3000
        } catch (e: Exception) {
            System.err.println("connect failed: ${e.message}")
            exitProcess(1)
        }
        socket.getOutputStream().apply {
            write(command(Codes.PUSH_MODE_STATE))
            flush()
        }
        val buf = ByteArray(4096)
        val n = socket.getInputStream().read(buf)
        printFrames(buf, n)
    }
}

private fun sendOnce(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println("usage: send <code> [payload] [host] [port]")
        exitProcess(2)
    }
    val code = args[0].toIntOrNull() ?: run {
        System.err.println("invalid code: ${args[0]}"); exitProcess(2)
    }
    val payload = args.getOrNull(1)
    val host = defaultHost(args, 2)
    val port = defaultPort(args, 3)
    val frame = parsePayloadToBuilder(code, payload)
    println("→ $host:$port  ${String(frame, Charsets.US_ASCII).trim()}")
    openSocket(host, port).use { socket ->
        // The real mount returns frames terminated by '#'. We don't know if it
        // also writes a newline; read enough and split on '#' ourselves.
        socket.getOutputStream().apply { write(frame); flush() }
        val raw = readFrames(socket.getInputStream(), 3000)
        if (raw.isEmpty()) {
            println("  (no response within timeout)")
        } else {
            raw.forEach { println("← ${it.trim()}") }
        }
    }
}

/**
 * Drain whatever's available up to [totalTimeoutMs] and split on '#'.
 *
 * Server framing is `1&<code>&<type>&<payload>#` — terminated by '#', often
 * followed by a newline. We read until we see at least one '#' (or the timeout
 * elapses), then split on '#' and re-emit each non-empty piece with the
 * trailing '#' attached so callers can echo/store the original frame.
 */
private fun readFrames(`in`: java.io.InputStream, totalTimeoutMs: Int): List<String> {
    val buf = ByteArray(16384)
    val pending = StringBuilder()
    val deadline = System.currentTimeMillis() + totalTimeoutMs
    while (System.currentTimeMillis() < deadline) {
        val n = try {
            `in`.read(buf)
        } catch (_: java.net.SocketTimeoutException) {
            break
        }
        if (n <= 0) break
        pending.append(String(buf, 0, n, Charsets.US_ASCII))
        if (pending.contains('#')) break
    }
    return pending.split('#')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { "$it#" }
}

private fun sendBurst(args: Array<String>) {
    if (args.size < 2) {
        System.err.println("usage: burst <code> <count> [host] [port]")
        exitProcess(2)
    }
    val code = args[0].toIntOrNull() ?: run {
        System.err.println("invalid code: ${args[0]}"); exitProcess(2)
    }
    val count = args[1].toIntOrNull() ?: run {
        System.err.println("invalid count: ${args[1]}"); exitProcess(2)
    }
    val host = defaultHost(args, 2)
    val port = defaultPort(args, 3)
    val frame = command(code)
    println("burst → $host:$port  x$count  code=$code")
    var ok = 0
    var fail = 0
    var firstCode: Int? = null
    openSocket(host, port, readTimeoutMs = 2000).use { socket ->
        for (i in 1..count) {
            try {
                socket.getOutputStream().apply { write(frame); flush() }
                val buf = ByteArray(4096)
                val n = socket.getInputStream().read(buf)
                if (n > 0) {
                    val (frames, _) = ResponseParser().parse(buf.copyOf(n))
                    if (frames.isNotEmpty()) {
                        if (firstCode == null) firstCode = frames.first().code
                        ok++
                    } else {
                        fail++
                    }
                } else {
                    fail++
                }
            } catch (e: Exception) {
                fail++
                System.err.println("  [$i] error: ${e.message}")
                break
            }
        }
    }
    println("ok=$ok  fail=$fail  firstCode=$firstCode")
}

private fun listenOnly(args: Array<String>) {
    if (args.size < 2) {
        System.err.println("usage: listen <code> <seconds> [host] [port]")
        exitProcess(2)
    }
    val code = args[0].toIntOrNull() ?: run { System.err.println("invalid code"); exitProcess(2) }
    val secs = args[1].toIntOrNull() ?: run { System.err.println("invalid seconds"); exitProcess(2) }
    val host = defaultHost(args, 2)
    val port = defaultPort(args, 3)
    println("listening $host:$port for $secs s, looking for code=$code")
    openSocket(host, port, readTimeoutMs = 1000).use { socket ->
        val deadline = System.currentTimeMillis() + secs * 1000L
        var frames = 0
        while (System.currentTimeMillis() < deadline) {
            val available = try { socket.getInputStream().available() } catch (_: Exception) { 0 }
            if (available > 0) {
                val buf = ByteArray(available.coerceAtLeast(1024))
                val n = socket.getInputStream().read(buf)
                if (n > 0) {
                    val (parsed, _) = ResponseParser().parse(buf.copyOf(n))
                    parsed.filter { it.code == code }.forEach {
                        frames++
                        if (frames <= 3) {
                            println("  code=${it.code} ${it.fields}")
                        }
                    }
                }
            } else {
                Thread.sleep(50)
            }
        }
        println("frames matching code=$code: $frames")
    }
}
