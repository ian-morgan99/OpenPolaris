package dev.openpolaris.core.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * JVM-side [FirmwareDelivery] that pushes the FwPkt.zip onto the
 * mount's SD card via the [tools.cef.ssh] style "ssh + cat" pipe
 * (the same path the on-board `polestar_app` binary watches).
 *
 * Two implementations are wired in by [command], in priority order:
 *
 *  1. `ssh user@host 'cat > /app/sd/FwPkt.zip'` — streams the bytes
 *     over the SSH stdin. Works on every POSIX sshd (the Benro gimbal
 *     runs dropbear which supports it).
 *  2. `scp -t /app/sd/FwPkt.zip` — uses the scp protocol. Slightly
 *     stricter (scp must be on the gimbal's PATH, which it usually
 *     is) but has the same effect.
 *
 * The on-board binary watches `/app/sd/FwPkt.zip` and on reboot picks
 * it up (see `HANDOVER-2026-08-31.md` §4.4 — `SP_UpgradeCheckFw`).
 * This is the verified path; the 810/784/794/795 envelope is
 * experimental.
 *
 * ### Authentication
 *
 * The Benro Polaris gimbal runs an sshd with **empty-password root**
 * (per the live-captured `sshd_config` in
 * `docs/evidence/gimbal-ssh-2026-08-31/`), so the default `[identity]`
 * above works without any key setup. If your gimbal has been hardened
 * to require a key, pass an `--identity-file` to the [command] or
 * inject your key into the agent before running the upload.
 *
 * ### Progress
 *
 * [deliver] reports the running byte count via [onProgress]. The
 * [progressIntervalMs] (default 100 ms) throttles the callback so the
 * UI thread is not flooded.
 *
 * ### Cancellation
 *
 * The subprocess is destroyed on coroutine cancellation. The user
 * may safely re-attempt the upload.
 */
class ScpFirmwareDelivery(
    /** The host (or IP) the gimbal's AP or WiFi network exposes. */
    val host: String,
    /** sshd port — default 22 even on dropbear. */
    val port: Int = 22,
    /** Username — the gimbal's sshd allows `root` with empty password. */
    val user: String = "root",
    /** Path to the `ssh`/`scp` binary. Override for non-standard PATHs. */
    val binary: String = "ssh",
    /** Optional identity file (e.g. `~/.ssh/id_rsa`) for hardened gimbal. */
    val identityFile: String? = null,
    /** Throttle for [onProgress] — every N ms at most. */
    val progressIntervalMs: Long = 100,
) : FirmwareDelivery {

    /**
     * Push the bytes. Wraps the `[ssh, host, 'cat > /app/sd/FwPkt.zip']`
     * pipeline; the bytes are streamed to the subprocess stdin and the
     * goroutine is reported via [onProgress].
     */
    override suspend fun deliver(
        bytes: ByteArray,
        filename: String,
        onProgress: (bytesSent: Int) -> Unit,
    ) = withContext(Dispatchers.IO) {
        require(bytes.isNotEmpty()) { "no firmware bytes" }
        val dest = "/app/sd/$filename"
        val cmd = buildList {
            add(binary)
            if (identityFile != null) {
                add("-i"); add(identityFile)
            }
            add("-p"); add(port.toString())
            // Disable strict host-key checking — the gimbal's host key
            // changes when the firmware is reflashed, and we want the
            // upload to "just work" from a fresh app install. The user
            // can override by setting `StrictHostKeyChecking=yes` in
            // their `~/.ssh/config` and pre-populating known_hosts.
            add("-o"); add("StrictHostKeyChecking=no")
            add("-o"); add("UserKnownHostsFile=/dev/null")
            add("$user@$host")
            add("cat > $dest")
        }
        val proc = ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .start()
        val totalSize = bytes.size
        var lastReport = 0L
        // Stream the bytes in chunks so we can emit onProgress; do it
        // on a worker thread so the IO is non-blocking.
        val writeJob = launch(Dispatchers.IO) {
            try {
                proc.outputStream.use { os: OutputStream ->
                    var offset = 0
                    while (offset < bytes.size) {
                        val end = minOf(offset + COPY_CHUNK, bytes.size)
                        os.write(bytes, offset, end - offset)
                        offset = end
                        val now = System.currentTimeMillis()
                        if (now - lastReport >= progressIntervalMs) {
                            lastReport = now
                            onProgress(offset)
                        }
                    }
                    os.flush()
                }
            } catch (e: IOException) {
                proc.destroyForcibly()
                throw e
            }
        }
        // Drain the subprocess stdout/stderr so the process does not
        // block on a full pipe. We don't care about the bytes here
        // (the gimbal's cat has no output) but we do want to surface
        // any error message in the exception.
        val output = StringBuilder()
        val readJob = launch(Dispatchers.IO) {
            proc.inputStream.use { ins: InputStream ->
                val buf = ByteArray(4096)
                while (true) {
                    val n = ins.read(buf)
                    if (n <= 0) break
                    output.append(String(buf, 0, n, Charsets.UTF_8))
                }
            }
        }
        try {
            // Wait for the write to finish, then the process to exit.
            // `waitFor` blocks until the cat has read EOF from stdin
            // and the remote shell has returned.
            coroutineScope {
                try {
                    writeJob.join()
                    val exit = proc.waitFor()
                    readJob.join()
                    if (exit != 0) {
                        throw IOException(
                            "scp delivery failed (exit=$exit): ${output.toString().trim()}"
                        )
                    }
                    onProgress(totalSize)
                } catch (e: CancellationException) {
                    proc.destroyForcibly()
                    throw e
                }
            }
        } catch (e: Throwable) {
            // Wrap so the controller's `scp delivery failed: ...` reason
            // is meaningful.
            if (e is IOException) throw e
            throw IOException(e.message ?: e::class.simpleName ?: "scp failed", e)
        }
    }

    /** Smallest streaming chunk. 64 KiB matches the JVM pipe buffer
     *  default and keeps progress callbacks meaningful. */
    private val COPY_CHUNK: Int = 64 * 1024

    companion object {
        /**
         * The default host the Benro Polaris AP exposes. Most users
         * leave the gimbal in its AP mode (`192.168.2.1` from the
         * evidence) so the controller can pick this up automatically.
         */
        const val DEFAULT_AP_HOST: String = "192.168.2.1"

        /**
         * The on-board watcher expects the bytes at this path. Do
         * not change — `SP_UpgradeCheckFw` polls this file by name.
         */
        const val DEFAULT_DEST_PATH: String = "/app/sd/FwPkt.zip"
    }
}
