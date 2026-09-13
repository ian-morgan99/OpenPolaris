package dev.openpolaris.core.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream

/**
 * Android-side [SshCommandRunner] that executes a single remote shell command on
 * the gimbal over SSHJ (pure-JVM), so the APK needs no `ssh` binary on PATH —
 * unlike the JVM-side [SystemSshCommandRunner], which shells out to OpenSSH.
 *
 * The [FirmwareUpdateController] uses this seam for the post-delivery probes:
 * the `/app/sd` free-space pre-flight (`df -B1 /app/sd`), the extraction
 * readiness check, and the mandatory `sync; /sbin/reboot`. Each call opens a
 * fresh connection (the probes are short-lived and infrequent), runs one
 * command, and returns the captured stdout/stderr + exit code.
 *
 * ### Authentication
 *
 * dropbear root with an empty password by default (per the live-captured
 * `sshd_config` in `docs/evidence/gimbal-ssh-2026-08-31/`). [password] defaults
 * to empty to match; a hardened gimbal can supply its root password.
 *
 * ### Exit-code contract
 *
 * Mirrors [SystemSshCommandRunner]: a non-zero exit from the remote command is a
 * *normal* outcome (e.g. `test -f` returning 1) and is reported in
 * [SshCommandResult.exitCode]; only a transport failure (auth rejected, host
 * unreachable, connection dropped) throws. The reboot probe is special: dropbear
 * vanishing mid-`reboot` surfaces as a transport error, which the controller's
 * `isExpectedRebootDisconnect` already treats as success.
 */
class SshjCommandRunner(
    /** The host (or IP) the gimbal's AP or WiFi network exposes. */
    val host: String,
    /** sshd port — default 22 even on dropbear. */
    val port: Int = 22,
    /** Username — the gimbal's sshd allows `root` with empty password. */
    val user: String = "root",
    /** Root password. Empty by default (the gimbal's stock dropbear config). */
    val password: String = "",
) : SshCommandRunner {

    override suspend fun run(command: String): SshCommandResult {
        if (!currentCoroutineContext().isActive) throw CancellationException("ssh command cancelled")
        return withContext(Dispatchers.IO) {
            val client = SshjClientFactory.newConnectedClient(host, port, user, password)
            try {
                val session = client.startSession()
                // `sh -c` so the probe can use shell syntax (test, &&, etc.),
                // matching the OpenSSH `-c` behaviour the JVM runner relies on.
                val cmd = session.exec("sh -c " + shellQuote(command))
                // SSHJ's exec() returns immediately; drain both streams to EOF
                // before asking for the exit status (which blocks until close).
                val stdout = readFully(cmd.getInputStream())
                val stderr = readFully(cmd.getErrorStream())
                val exitCode = cmd.getExitStatus() ?: 0
                SshCommandResult(
                    exitCode = exitCode,
                    stdout = stdout,
                    stderr = stderr,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Transport failure (auth rejected, host unreachable, connection
                // dropped mid-reboot). The controller's reboot path already
                // treats a drop as the expected success signal.
                if (e is IOException) throw e
                throw IOException("sshj command failed: ${e.message ?: e::class.simpleName}", e)
            } finally {
                runCatching { client.close() }
            }
        }
    }

    /** Read an SSH stream to EOF as UTF-8. */
    private fun readFully(stream: InputStream): String =
        String(stream.readBytes(), Charsets.UTF_8)

    /** Single-quote a shell argument so the probe runs verbatim. */
    private fun shellQuote(arg: String): String = "'" + arg.replace("'", "'\\''") + "'"
}
