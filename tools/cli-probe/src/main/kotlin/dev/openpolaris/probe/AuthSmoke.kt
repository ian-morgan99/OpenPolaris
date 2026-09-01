package dev.openpolaris.probe

import dev.openpolaris.core.domain.AuthConfig
import dev.openpolaris.core.domain.Connection
import dev.openpolaris.core.domain.MountSession
import dev.openpolaris.core.protocol.Codes
import dev.openpolaris.core.protocol.ResponseParser
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess
import java.net.InetSocketAddress
import java.net.Socket

/**
 * End-to-end smoke test for the 820/821/823 app handshake
 * (see [MountSession.authenticate]). Spins up a raw TCP socket to the
 * gimbal, hands it to a [MountSession] configured with the user-supplied
 * [AuthConfig], calls `connect()` (which now runs the full handshake),
 * and reports the outcome plus a single 519 (gimbal position) read as
 * evidence the post-handshake protocol actually works.
 *
 * Usage:
 *   cli-probe auth-smoke 192.168.0.1 9090
 *   cli-probe auth-smoke 192.168.0.1 9090 mypassword
 *   OPENPOLARIS_APP_NAME=benro OPENPOLARIS_APP_VERSION=1.0.0 \
 *     cli-probe auth-smoke 192.168.0.1 9090
 *
 * Exit codes:
 *   0  handshake + post-handshake read both succeeded
 *   1  connect / handshake failed (auth rejected, network down, …)
 *   2  usage error
 */
fun main(args: Array<String>): Unit {
    if (args.isEmpty()) {
        System.err.println(
            "usage: auth-smoke <host> [port] [password] " +
                "[--app <name>] [--ver <version>]",
        )
        System.exit(2)
    }
    val host = args[0]
    val port = args.getOrNull(1)?.toIntOrNull() ?: 9090

    // Parse the optional named flags. We do this by hand rather than
    // pulling in a CLI library to keep the cli-probe module
    // dependency-free beyond kotlinx-coroutines.
    var password: String? = null
    var appName = System.getenv("OPENPOLARIS_APP_NAME") ?: "openpolaris"
    var appVersion = System.getenv("OPENPOLARIS_APP_VERSION")
        ?: AuthConfig.DEFAULT_APP_VERSION
    val positional = mutableListOf<String>()
    var i = 2
    while (i < args.size) {
        when (val a = args[i]) {
            "--app" -> {
                appName = args.getOrNull(i + 1) ?: missingArg("--app")
                i += 2
            }
            "--ver" -> {
                appVersion = args.getOrNull(i + 1) ?: missingArg("--ver")
                i += 2
            }
            else -> {
                positional += a
                i += 1
            }
        }
    }
    password = positional.firstOrNull()

    println(
        "auth-smoke → $host:$port " +
            "(app=$appName ver=$appVersion " +
            "password=${if (password == null) "<none>" else "***"})",
    )

    val auth = AuthConfig(
        appName = appName,
        appVersion = appVersion,
        password = password,
    )

    val ok = runCatching {
        runBlocking {
            // Use a single-connection factory that returns a fresh
            // Socket each call. MountSession will call it from its
            // tryConnect block.
            val connFactory: () -> Connection = { SocketConnection(host, port) }
            val session = MountSession(
                connectionFactory = connFactory,
                host = host,
                port = port,
                auth = auth,
            )
            println("→ connecting (full 284 + 820 + 823 handshake) …")
            val connected = session.connect()
            if (!connected) {
                System.err.println(
                    "FAIL  connect() returned false " +
                        "(lastError=${session.lastError})",
                )
                return@runBlocking false
            }
            println("OK  connect() returned true — handshake completed")

            // Post-handshake read: a single 519 gimbal position read.
            // If the gimbal processed the request, we get a non-empty
            // `yaw`/`pitch`/`roll` payload. Pre-handshake the same
            // call would have returned `ret:-1`.
            println("→ request(519) post-handshake read …")
            val r = session.request<ResponseParser.Frame>(
                code = Codes.EX_AXIS_STA,
                timeoutMs = 3000,
            ) { it } // identity parse — return the frame as-is
            val readOk: Boolean = when (r) {
                is MountSession.CmdResult.Ok -> {
                    println("OK  519 read: ${r.value.fields}")
                    true
                }
                is MountSession.CmdResult.Timeout -> {
                    System.err.println("FAIL  519 timed out (no frame within 3s)")
                    false
                }
                is MountSession.CmdResult.ProtocolError -> {
                    System.err.println("FAIL  519 returned ProtocolError: ${r.message}")
                    false
                }
            }
            readOk
        }
    }.getOrElse { e ->
        System.err.println("FAIL  exception: ${e.javaClass.simpleName}: ${e.message}")
        e.printStackTrace(System.err)
        false
    }

    System.exit(if (ok) 0 else 1)
}

private fun missingArg(flag: String): Nothing {
    System.err.println("missing value for $flag")
    exitProcess(2)
}

/**
 * Thin [Connection] adapter over a single TCP [Socket]. The socket is
 * created at construction time (mirrors what the real `SocketConnection`
 * would do for an Android build) and reused for the lifetime of the
 * [MountSession].
 */
private class SocketConnection(
    private val host: String,
    private val port: Int,
) : Connection {
    private val socket: Socket = Socket().apply {
        connect(InetSocketAddress(host, port), 5000)
        soTimeout = 3000
        tcpNoDelay = true
    }

    override suspend fun connect(host: String, port: Int, timeoutMs: Int) {
        // Already connected in the initializer; nothing to do.
    }

    override suspend fun write(data: ByteArray) {
        socket.getOutputStream().apply {
            write(data)
            flush()
        }
    }

    override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int =
        socket.getInputStream().read(buffer)

    override fun close() {
        runCatching { socket.close() }
    }
}
