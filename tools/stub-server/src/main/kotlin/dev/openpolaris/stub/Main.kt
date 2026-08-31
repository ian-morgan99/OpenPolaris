package dev.openpolaris.stub

import dev.openpolaris.core.protocol.ResponseParser
import dev.openpolaris.core.sim.SimulatedProtocol
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * Standalone TCP stub that emulates a Polaris gimbal at the wire level.
 *
 * Purpose: the mobile app normally talks to the gimbal over its own WiFi access
 * point. While developing on the PC we don't always have the gimbal present, and
 * captive-portal churn on the gimbal's AP was overloading this machine in the
 * past. Running this stub on the PC and pointing the app at the PC's LAN IP
 * exercises the full TCP/parsing/response path without the captive portal.
 *
 * Usage: ./gradlew :tools:stub-server:run --args="--port 9090 --bind 0.0.0.0"
 *
 * Each accepted connection runs on its own thread with its own [SimulatedProtocol]
 * so sessions do not share mutable state. The reader accumulates bytes and uses
 * [ResponseParser.parse] to extract complete `…#` frames; any partial trailing
 * frame is kept across reads. This mirrors the real mount's framing — clients
 * do NOT terminate requests with newlines, only with `#`.
 */
/**
 * Parse args and return either a [Startup] describing what to bind to, or a
 * [Usage] error (exit code 2) describing what went wrong. Testable seam.
 */
internal sealed interface ParseResult {
    data class Startup(val bind: String, val port: Int) : ParseResult
    data class Usage(val message: String) : ParseResult
}

internal fun parseArgs(args: Array<String>): ParseResult {
    val portRaw = argValue(args, "--port")
    val port = portRaw?.toIntOrNull()
    if (portRaw != null && port == null) {
        return ParseResult.Usage("--port must be an integer, got: $portRaw")
    }
    val bind = argValue(args, "--bind") ?: "0.0.0.0"
    return ParseResult.Startup(bind, port ?: 9090)
}

fun main(args: Array<String>) {
    when (val r = parseArgs(args)) {
        is ParseResult.Usage -> {
            System.err.println(r.message)
            kotlin.system.exitProcess(2)
        }
        is ParseResult.Startup -> runServer(r.bind, r.port)
    }
}

/**
 * Open the listening socket, accept clients forever, and return when the
 * server is bound. Returns the actual bound port (useful when the caller
 * asked for port 0 / ephemeral).
 *
 * The function does NOT return under normal operation — the [ServerSocket]
 * accept loop runs until the JVM is shut down. The return is reached only
 * if [bind] fails (e.g. port already in use), in which case we propagate
 * the [java.io.IOException] to the caller.
 */
fun runServer(bind: String, port: Int): Int {
    val address = InetAddress.getByName(bind)
    val server = ServerSocket(port, 50, address)
    val boundPort = server.localPort
    System.out.println("polaris-stub listening on $bind:$boundPort")
    // Self-test the parser at startup. A hang here (no log line) is itself the failure
    // signal: findResponseStart() used to infinite-loop on a buffer with no '@'.
    val parser = ResponseParser()
    val (frames, _) = parser.parse("1&524&2&#".toByteArray(Charsets.US_ASCII))
    System.out.println("parser self-test: ${frames.size} frame(s) parsed")
    val exec = Executors.newCachedThreadPool { r ->
        Thread(r, "polaris-stub-client-${CLIENT_ID.incrementAndGet()}").apply { isDaemon = true }
    }
    Runtime.getRuntime().addShutdownHook(Thread {
        System.out.println("polaris-stub shutting down")
        runCatching { server.close() }
        exec.shutdownNow()
    })
    while (true) {
        val client = server.accept()
        System.out.println("client connected: ${client.remoteSocketAddress}")
        exec.submit { handle(client) }
    }
    return boundPort
}

private fun handle(client: Socket) {
    val sim = SimulatedProtocol()
    val parser = ResponseParser()
    val `in` = client.getInputStream()
    val out = client.getOutputStream().buffered()
    val pending = ByteArrayOutputStream()
    val chunk = ByteArray(4096)
    try {
        while (true) {
            val n = `in`.read(chunk)
            if (n < 0) break
            pending.write(chunk, 0, n)
            val (frames, consumed) = parser.parse(pending.toByteArray())
            if (consumed > 0) {
                // Drop consumed bytes, keep any partial tail.
                val remaining = pending.size() - consumed
                val tail = pending.toByteArray().copyOfRange(consumed, pending.size())
                pending.reset()
                pending.write(tail, 0, remaining)
            }
            for (f in frames) {
                val responses = sim.handle(f.code, f.fields)
                for (r in responses) {
                    out.write(r)
                    out.flush()
                }
            }
        }
    } catch (e: Exception) {
        System.out.println("client ${client.remoteSocketAddress} closed: ${e.message}")
    } finally {
        runCatching { client.close() }
    }
}

internal fun argValue(args: Array<String>, name: String): String? {
    val i = args.indexOf(name)
    if (i < 0 || i + 1 >= args.size) return null
    return args[i + 1]
}

private val CLIENT_ID = AtomicLong(0)
