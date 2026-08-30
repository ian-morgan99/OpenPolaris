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
fun main(args: Array<String>) {
    val port = argValue(args, "--port")?.toIntOrNull() ?: 9090
    val bind = argValue(args, "--bind") ?: "0.0.0.0"

    val address = InetAddress.getByName(bind)
    val server = ServerSocket(port, 50, address)
    System.out.println("polaris-stub listening on $bind:$port")
    val exec = Executors.newCachedThreadPool { r ->
        Thread(r, "polaris-stub-client-${CLIENT_ID.incrementAndGet()}").apply { isDaemon = true }
    }
    Runtime.getRuntime().addShutdownHook(Thread {
        System.out.println("polaris-stub shutting down")
        server.close()
        exec.shutdownNow()
    })
    while (true) {
        val client = server.accept()
        System.out.println("client connected: ${client.remoteSocketAddress}")
        exec.submit { handle(client) }
    }
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

private fun argValue(args: Array<String>, name: String): String? {
    val i = args.indexOf(name)
    if (i < 0 || i + 1 >= args.size) return null
    return args[i + 1]
}

private val CLIENT_ID = AtomicLong(0)
