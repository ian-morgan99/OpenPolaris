package dev.openpolaris.probe

import dev.openpolaris.core.domain.JvmConnection
import dev.openpolaris.core.domain.MountSession
import dev.openpolaris.core.protocol.Codes
import dev.openpolaris.core.protocol.ResponseParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import kotlin.system.measureTimeMillis

/** Hold one authenticated control connection open and prove periodic 284 replies. */
fun main(args: Array<String>) = runBlocking {
    val host = args.getOrNull(0) ?: "192.168.0.1"
    val port = args.getOrNull(1)?.toIntOrNull() ?: 9090
    val seconds = args.getOrNull(2)?.toIntOrNull() ?: 120
    val session = MountSession(
        connectionFactory = {
            JvmConnection(bindTo = InetAddress.getByName("192.168.0.4"))
        },
        host = host,
        port = port,
    )
    check(session.connect()) { "handshake failed: ${session.lastError}" }
    println("keepalive connected host=$host port=$port duration=${seconds}s")
    repeat(seconds) { sample ->
        // 822 is classified as the app liveness ping. Real firmware commonly
        // treats it as one-way, so do not stall waiting for an acknowledgement.
        session.sendOnly(Codes.APP_PING)
        var result: MountSession.CmdResult<ResponseParser.Frame>? = null
        val elapsed = measureTimeMillis {
            result = session.request(Codes.PUSH_MODE_STATE, timeoutMs = 2_000) { it }
        }
        when (val current = result) {
            is MountSession.CmdResult.Ok -> println("sample=${sample + 1} rtt_ms=$elapsed fields=${current.value.fields}")
            else -> error("keepalive failed at sample ${sample + 1}: $current")
        }
        delay(1_000)
    }
    session.shutdown()
}
