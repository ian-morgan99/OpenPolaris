package dev.openpolaris.core.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * JVM-side [SshCommandRunner] that shells out to the platform
 * `ssh` binary. Mirrors the authentication options used by
 * `ScpFirmwareDelivery` so the user only configures one set of
 * connection options in the UI.
 *
 * ### Why shell out instead of using JSch
 *
 * The Benro Polaris gimbal's sshd is **dropbear** with
 * empty-password root (per the live-captured
 * `sshd_config` in
 * `docs/evidence/gimbal-ssh-2026-08-31/`). The
 * `ssh` binary that ships with the host OS (OpenSSH) is
 * already a known-good client for this; pulling in JSch
 * would add a dependency, an attack surface, and a
 * duplicate configuration path. The same rationale the
 * `ScpFirmwareDelivery` docstring cites applies here.
 *
 * ### Cancellation
 *
 * The synchronous `run` calls [execute] via `runBlocking`. For
 * long-lived callers that need proper coroutine cancellation,
 * the [execute] suspend variant is exposed and is the
 * preferred entry point inside coroutines.
 */
class SystemSshCommandRunner(
    /** The host (or IP) the gimbal's AP or WiFi network exposes. */
    val host: String,
    /** sshd port — default 22 even on dropbear. */
    val port: Int = 22,
    /** Username — the gimbal's sshd allows `root` with empty password. */
    val user: String = "root",
    /** Path to the `ssh` binary. Override for non-standard PATHs. */
    val binary: String = "ssh",
    /** Optional identity file (e.g. `~/.ssh/id_rsa`) for hardened gimbal. */
    val identityFile: String? = null,
) : SshCommandRunner {

    /** Synchronous `fun interface` entry point. Wraps [execute] in
     *  `runBlocking` so the function-interface contract is met. */
    override fun run(command: String): SshCommandResult = runBlocking {
        execute(command)
    }

    /** Suspendable variant. Run from inside a coroutine so
     *  cancellation propagates: cancelling the caller interrupts
     *  the read threads and `destroyForcibly()`'s the subprocess. */
    suspend fun execute(command: String): SshCommandResult =
        withContext(Dispatchers.IO) {
            val cmd = buildList {
                add(binary)
                if (identityFile != null) {
                    add("-i"); add(identityFile)
                }
                add("-p"); add(port.toString())
                add("-o"); add("StrictHostKeyChecking=no")
                add("-o"); add("UserKnownHostsFile=/dev/null")
                add("-o"); add("BatchMode=yes")
                add("$user@$host")
                add(command)
            }
            val proc = try {
                ProcessBuilder(cmd).redirectErrorStream(false).start()
            } catch (t: Throwable) {
                throw IOException(
                    "Failed to start $binary: ${t.message ?: t::class.simpleName}",
                    t,
                )
            }
            val out = StringBuilder()
            val err = StringBuilder()
            val readOut = Thread {
                runCatching {
                    proc.inputStream.bufferedReader(Charsets.UTF_8).forEachLine {
                        out.appendLine(it)
                    }
                }
            }.also { it.isDaemon = true; it.start() }
            val readErr = Thread {
                runCatching {
                    proc.errorStream.bufferedReader(Charsets.UTF_8).forEachLine {
                        err.appendLine(it)
                    }
                }
            }.also { it.isDaemon = true; it.start() }
            try {
                val exit = proc.waitFor()
                readOut.join(500)
                readErr.join(500)
                SshCommandResult(
                    exitCode = exit,
                    stdout = out.toString(),
                    stderr = err.toString(),
                )
            } catch (e: InterruptedException) {
                proc.destroyForcibly()
                readOut.interrupt()
                readErr.interrupt()
                throw CancellationException("ssh command interrupted: $command").initCause(e)
            } catch (e: CancellationException) {
                proc.destroyForcibly()
                readOut.interrupt()
                readErr.interrupt()
                throw e
            } finally {
                if (proc.isAlive) proc.destroyForcibly()
            }
        }
}
