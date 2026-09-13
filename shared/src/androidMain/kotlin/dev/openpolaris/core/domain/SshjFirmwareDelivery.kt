package dev.openpolaris.core.domain

import dev.openpolaris.core.net.SshjClientFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import net.schmizz.sshj.xfer.LocalFileFilter
import net.schmizz.sshj.xfer.LocalSourceFile
import java.io.IOException
import java.io.InputStream

/**
 * Android-side [FirmwareDelivery] that pushes the FwPkt.zip onto the mount's
 * SD card over SSHJ (a pure-JVM SSH client), so the APK needs no `ssh`/`scp`
 * binary on PATH — unlike the JVM-side [ScpFirmwareDelivery], which shells out.
 *
 * ### Where the bytes land
 *
 * The zip is written to `/app/sd/FwPkt.zip` (the path the on-board
 * `SP_UpgradeCheckFw` watcher polls by name). After delivery, the
 * [FirmwareUpdateController] sends the 783 extraction trigger and reboots so
 * the boot-time SD scan flashes the extracted `/app/sd/FwPkt/` tree. This is
 * the same "verified path" the JVM-side delivery uses; only the transport
 * differs (SSHJ SFTP instead of a shelled-out `ssh cat`).
 *
 * ### Authentication
 *
 * The gimbal's dropbear sshd allows **root with an empty password** by default
 * (per the live-captured `sshd_config` in
 * `docs/evidence/gimbal-ssh-2026-08-31/`). [password] defaults to empty to
 * match that; a hardened gimbal can supply its root password.
 *
 * ### Progress
 *
 * SSHJ 0.40.x exposes no SFTP progress monitor, so we wrap the bytes in a
 * counting [LocalSourceFile] whose input stream reports the running byte count
 * to [onProgress], throttled to at most one call per [progressIntervalMs] so
 * the UI thread is not flooded. The controller's progress watchdog consumes
 * these on a conflated channel, so occasional drops are harmless.
 *
 * ### Cancellation
 *
 * The blocking SFTP put runs on [Dispatchers.IO]. Cancellation takes effect at
 * the transfer boundary (the controller's watchdog cancels the transfer job);
 * a mid-transfer interrupt is not supported by SSHJ's single-call `put`, which
 * is acceptable because the watchdog already bounds stall time.
 */
class SshjFirmwareDelivery(
    /** The host (or IP) the gimbal's AP or WiFi network exposes. */
    val host: String,
    /** sshd port — default 22 even on dropbear. */
    val port: Int = 22,
    /** Username — the gimbal's sshd allows `root` with empty password. */
    val user: String = "root",
    /** Root password. Empty by default (the gimbal's stock dropbear config). */
    val password: String = "",
    /** Destination path on the mount. The on-board watcher polls this name. */
    val destPath: String = DEFAULT_DEST_PATH,
    /** Throttle for [onProgress] — at most one callback per N ms. */
    val progressIntervalMs: Long = 100,
) : FirmwareDelivery {

    override suspend fun deliver(
        bytes: ByteArray,
        filename: String,
        onProgress: (bytesSent: Int) -> Unit,
    ) {
        require(bytes.isNotEmpty()) { "no firmware bytes" }
        if (!currentCoroutineContext().isActive) throw CancellationException("firmware upload cancelled")

        withContext(Dispatchers.IO) {
            val client = SshjClientFactory.newConnectedClient(host, port, user, password)
            try {
                val sftp = client.newSFTPClient()
                try {
                    // SSHJ 0.40.x has no SFTP progress monitor, so we wrap the
                    // bytes in a counting LocalSourceFile whose stream reports
                    // the running byte count to [onProgress], throttled to at
                    // most one call per [progressIntervalMs].
                    val source = CountingLocalSourceFile(
                        name = filename,
                        bytes = bytes,
                        progressIntervalMs = progressIntervalMs,
                        onProgress = onProgress,
                    )
                    sftp.put(source, destPath)
                    onProgress(bytes.size)
                } finally {
                    runCatching { sftp.close() }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Wrap so the controller's "scp delivery failed: ..." reason is
                // meaningful regardless of which SSHJ exception surfaced.
                if (e is IOException) throw e
                throw IOException("sshj delivery failed: ${e.message ?: e::class.simpleName}", e)
            } finally {
                runCatching { client.close() }
            }
        }
    }

    /**
     * A [LocalSourceFile] backed by an in-memory byte array whose input stream
     * reports the running read offset to [onProgress], throttled to at most one
     * call per [progressIntervalMs]. SSHJ 0.40.x has no SFTP progress monitor,
     * so this is how we surface upload progress to the UI.
     */
    private class CountingLocalSourceFile(
        private val name: String,
        private val bytes: ByteArray,
        private val progressIntervalMs: Long,
        private val onProgress: (bytesSent: Int) -> Unit,
    ) : LocalSourceFile {

        private var lastReportMs = 0L

        override fun getName(): String = name
        override fun getLength(): Long = bytes.size.toLong()
        override fun getInputStream(): InputStream = CountingStream(bytes, this::report)
        override fun getPermissions(): Int = 0b100_100_100 // rw-r--r--
        override fun isFile(): Boolean = true
        override fun isDirectory(): Boolean = false
        override fun getChildren(filter: LocalFileFilter): Iterable<LocalSourceFile> = emptyList()
        override fun providesAtimeMtime(): Boolean = false
        override fun getLastAccessTime(): Long = 0L
        override fun getLastModifiedTime(): Long = 0L

        /** Throttled progress report — at most one [onProgress] per interval. */
        private fun report(sent: Int) {
            val now = System.currentTimeMillis()
            if (now - lastReportMs >= progressIntervalMs) {
                lastReportMs = now
                onProgress(sent)
            }
        }

        /** Reports the running read offset to [report] as bytes are consumed. */
        private class CountingStream(
            private val bytes: ByteArray,
            private val report: (sent: Int) -> Unit,
        ) : InputStream() {
            private var offset = 0

            override fun read(): Int {
                if (offset >= bytes.size) return -1
                val b = bytes[offset++].toInt() and 0xFF
                report(offset)
                return b
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (offset >= bytes.size) return -1
                val n = minOf(len, bytes.size - offset)
                System.arraycopy(bytes, offset, b, off, n)
                offset += n
                report(offset)
                return n
            }
        }
    }

    companion object {
        /**
         * The on-board watcher expects the bytes at this path. Do not change —
         * `SP_UpgradeCheckFw` polls this file by name.
         */
        const val DEFAULT_DEST_PATH: String = "/app/sd/FwPkt.zip"
    }
}
