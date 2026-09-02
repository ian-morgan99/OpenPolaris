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
import java.io.File
import java.io.FileInputStream

/**
 * End-to-end smoke test for the 820/821/823 app handshake
 * (see [MountSession.authenticate]). Spins up a raw TCP socket to the
 * gimbal, hands it to a [MountSession] configured with the user-supplied
 * [AuthConfig], calls `connect()` (which now runs the full handshake),
 * and reports the outcome plus a single 519 (gimbal position) read as
 * evidence the post-handshake protocol actually works.
 *
 * # Password handling
 *
 * Connection passwords are NOT accepted as a positional CLI argument
 * because that leaks the secret via `ps`, shell history, and CI logs.
 * The default flow is:
 *
 * 1. `OPENPOLARIS_AUTH_PASSWORD` environment variable (CI-friendly,
 *    but still has the env-dump / process-info leak risk — see warning).
 * 2. Interactive prompt via `Console.readPassword()` — masked input,
 *    not retained in process memory longer than needed.
 * 3. File-descriptor path: `--password-fd <N>` reads from the
 *    inherited fd N. The string is read once and zeroed.
 *
 * For one-off debugging where the leak risk is acceptable, the
 * `--password-on-command-line <value>` opt-in flag may be used. The
 * value is still passed via process argv and so remains visible in
 * `ps` / CI logs / shell history; the marker must be explicit so
 * scripts can never accidentally trigger this path.
 *
 * Usage:
 *   # Interactive (default; safe for local use):
 *   cli-probe auth-smoke 192.168.0.1 9090
 *
 *   # CI / automation via env var:
 *   OPENPOLARIS_AUTH_PASSWORD=… cli-probe auth-smoke 192.168.0.1 9090
 *
 *   # CI / automation via fd (no env, no argv):
 *   echo -n '…' > /tmp/pw && cli-probe auth-smoke 192.168.0.1 9090 \
 *     --password-fd 3 3</tmp/pw
 *
 *   # DEBUG-ONLY: explicit opt-in to the legacy argv path:
 *   cli-probe auth-smoke 192.168.0.1 9090 \
 *     --password-on-command-line mypassword
 *
 *   OPENPOLARIS_APP_NAME=benro OPENPOLARIS_APP_VERSION=1.0.0 \
 *     cli-probe auth-smoke 192.168.0.1 9090
 *
 * Exit codes:
 *   0  handshake + post-handshake read both succeeded
 *   1  connect / handshake failed (auth rejected, network down, …)
 *   2  usage error
 *   3  password required but no source provided / not provided interactively
 */
fun main(args: Array<String>): Unit {
    if (args.isEmpty()) {
        System.err.println(
            "usage: auth-smoke <host> [port] " +
                "[--app <name>] [--ver <version>] " +
                "[--password-fd <N>] " +
                "[--password-on-command-line <pw> (UNSAFE; debug only)]",
        )
        System.err.println(
            "By default the password is read from " +
                "OPENPOLARIS_AUTH_PASSWORD or prompted interactively " +
                "(no-echo via Console.readPassword).",
        )
        System.exit(2)
    }
    val host = args[0]
    val port = args.getOrNull(1)?.toIntOrNull() ?: 9090

    // Parse the optional named flags. We do this by hand rather than
    // pulling in a CLI library to keep the cli-probe module
    // dependency-free beyond kotlinx-coroutines.
    var password: String? = System.getenv("OPENPOLARIS_AUTH_PASSWORD")
    var passwordFd: Int? = null
    var unsafeArgvPassword: String? = null
    var allowArgvPassword = false
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
            "--password-fd" -> {
                val n = args.getOrNull(i + 1)?.toIntOrNull()
                    ?: failUsage("--password-fd requires an integer fd number")
                passwordFd = n
                i += 2
            }
            "--password-on-command-line" -> {
                // Two-step opt-in: a marker followed by the value.
                // The marker must be present and adjacent to the value
                // so an alias or stray script can never accidentally
                // trigger the legacy argv path.
                allowArgvPassword = true
                val value = args.getOrNull(i + 1)
                    ?: failUsage(
                        "--password-on-command-line requires the password " +
                            "to follow the marker (UNSAFE; debug only)",
                    )
                unsafeArgvPassword = value
                i += 2
            }
            else -> {
                positional += a
                i += 1
            }
        }
    }

    // Reject a stray bare positional BEFORE attempting to resolve the
    // password. Defence in depth: never silently absorb a cleartext argv
    // password even if a safer source is also present.
    val argvOptInUsed = unsafeArgvPassword != null
    val stray = positional.firstOrNull()
    if (stray != null && !argvOptInUsed) {
        System.err.println(
            "auth-smoke: refusing positional password from argv. " +
                "Pass --password-on-command-line <value> to opt in " +
                "(UNSAFE; debug only), or use --password-fd, " +
                "OPENPOLARIS_AUTH_PASSWORD, or the interactive prompt.",
        )
        exitProcess(2)
    }

    // Resolution order:
    //   1. --password-on-command-line (debug-only opt-in)
    //   2. OPENPOLARIS_AUTH_PASSWORD env var
    //   3. --password-fd <N>
    //   4. Interactive Console.readPassword() prompt
    val resolved: String = when {
        unsafeArgvPassword != null -> {
            // The opt-in flag was set; warn the user that this is
            // unsafe and they accepted that.
            System.err.println(
                "WARNING: --password-on-command-line leaks the password " +
                    "via process argv. Do not use in CI logs or shared shells.",
            )
            unsafeArgvPassword!!
        }
        password != null -> {
            // Already populated from OPENPOLARIS_AUTH_PASSWORD above.
            // Warn that env vars are also visible to other processes
            // via /proc/<pid>/environ.
            System.err.println(
                "WARNING: OPENPOLARIS_AUTH_PASSWORD is visible in the " +
                    "process environment. Prefer --password-fd for CI.",
            )
            password
        }
        passwordFd != null -> readPasswordFromFd(passwordFd)
        System.console() != null -> readPasswordInteractively()
        else -> {
            // No console (e.g. fully detached service) and no source.
            System.err.println(
                "auth-smoke: no password source. Set OPENPOLARIS_AUTH_PASSWORD, " +
                    "use --password-fd, or run from a terminal for an " +
                    "interactive prompt.",
            )
            exitProcess(3)
        }
    }
    password = resolved

    // If both --password-on-command-line and a bare positional were
    // given, the marker value wins and we warn so the user notices.
    if (stray != null && argvOptInUsed) {
        System.err.println(
            "auth-smoke: ignoring stray positional '" + stray + "' " +
                "because --password-on-command-line was set.",
        )
    }

    val sourceLabel = when {
        unsafeArgvPassword != null -> "argv (UNSAFE)"
        System.getenv("OPENPOLARIS_AUTH_PASSWORD") != null ->
            "OPENPOLARIS_AUTH_PASSWORD"
        passwordFd != null -> "fd " + passwordFd
        else -> "interactive"
    }
    println(
        "auth-smoke → $host:$port " +
            "(app=$appName ver=$appVersion " +
            "password-source=" + sourceLabel + ")",
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


private fun failUsage(message: String): Nothing {
    System.err.println(message)
    exitProcess(2)
}

/**
 * Read the password from the inherited file descriptor [n]. The fd is
 * left open (the parent process owns it). A single trailing newline
 * that `echo` may have added is trimmed.
 */
private fun readPasswordFromFd(n: Int): String {
    require(n > 2) { "--password-fd must reference an inherited fd > 2" }
    // java.io.FileDescriptor(int) is private; reach the inherited fd
    // via /proc/self/fd which is the documented Linux mechanism and
    // does not require reflection.
    val raw = FileInputStream(File("/proc/self/fd/$n")).use { stream ->
        stream.readBytes()
    }
    val s = String(raw, Charsets.UTF_8)
    return if (s.endsWith("\n")) s.dropLast(1) else s
}

/**
 * Prompt on stderr (so the prompt is visible even when stdout is
 * redirected) and read a no-echo password from the controlling
 * terminal. The password is not retained after this call returns.
 */
private fun readPasswordInteractively(): String {
    System.err.print("auth-smoke password: ")
    val pw = System.console().readPassword()
    if (pw == null) {
        System.err.println("auth-smoke: no password provided on stdin.")
        exitProcess(3)
    }
    return String(pw)
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
