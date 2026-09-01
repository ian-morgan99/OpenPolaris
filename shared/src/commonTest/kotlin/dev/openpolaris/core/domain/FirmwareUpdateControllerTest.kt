package dev.openpolaris.core.domain

import dev.openpolaris.core.protocol.Codes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for [FirmwareUpdateController] using a [FakeConnection] that
 * hands out pre-loaded wire bytes on demand.
 *
 * Contract under test:
 *  - 810 (SYS_FW_UPGRADE) fires first as a precondition
 *  - 784 (FILE_UPLOAD_FW) is the start frame, expecting `ret:0;`
 *  - 794 (FILE_UPLOAD_CHUNK) is fired once per [chunkSize] slice (no reply)
 *  - 795 (FILE_UPLOAD_END) is the finalizer
 *  - 811 (SYS_FW_PROGRESS) is polled until p:100
 *  - 812 (SYS_REBOOT) is sent if `rebootAfter=true`
 *
 * The demux reader in [MountSession] drops frames that have no
 * matching waiter at delivery time, so we must enqueue each reply
 * lazily — only after the matching write has been observed. A
 * background coroutine pumps the right reply for each code as it
 * is written.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FirmwareUpdateControllerTest {

    private class FakeConnection : Connection {
        val written = mutableListOf<ByteArray>()
        val pendingReplies = mutableListOf<ByteArray>()

        override suspend fun connect(host: String, port: Int, timeoutMs: Int) {}
        override suspend fun write(data: ByteArray) { written += data }
        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int {
            if (pendingReplies.isEmpty()) return -1
            val r = pendingReplies.removeAt(0)
            r.copyInto(buffer)
            return r.size
        }
        override fun close() {}

        fun replyForCode(code: Int, payload: String = "") {
            pendingReplies += "1&$code&2&$payload#".toByteArray(Charsets.US_ASCII)
        }

        fun writtenCode(code: Int): Boolean = written.any {
            val parts = it.decodeToString().split("&")
            parts.size >= 2 && parts[1].toIntOrNull() == code
        }

        fun countWrittenCode(code: Int): Int = written.count {
            val parts = it.decodeToString().split("&")
            parts.size >= 2 && parts[1].toIntOrNull() == code
        }

        suspend fun awaitWriteOf(code: Int) {
            while (!writtenCode(code)) delay(1)
        }

        suspend fun awaitWriteCountOf(code: Int, target: Int) {
            while (countWrittenCode(code) < target) delay(1)
        }
    }

    private fun parseCode(frame: ByteArray): Int? {
        val parts = frame.decodeToString().split("&")
        return if (parts.size >= 2) parts[1].toIntOrNull() else null
    }

    @Test
    fun happyPathFiresAllCodesAndReturnsDone() = runTest {
        val conn = FakeConnection()
        conn.pendingReplies += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        val session = MountSession({ conn }, readerScope = backgroundScope)
        assertTrue(session.connect())

        val pump = backgroundScope.launch {
            conn.awaitWriteOf(810); conn.replyForCode(810, "state:1;")
            conn.awaitWriteOf(784); conn.replyForCode(784, "ret:0;")
            conn.awaitWriteOf(795); conn.replyForCode(795, "ret:0;")
            conn.awaitWriteOf(811); conn.replyForCode(811, "p:100;")
            // Wait for the *next* 811 write (i.e. the second poll) before
            // sending the second p:100 reply.
            conn.awaitWriteCountOf(811, target = 2)
            conn.replyForCode(811, "p:100;")
        }

        val controller = FirmwareUpdateController(
            session = session,
            chunkSize = 4,
            progressPollMs = 50,
            progressDoneRepeats = 2,
            installTimeoutMs = 2_000,
        )
        val statuses = mutableListOf<FirmwareUpdateController.Status>()
        val final = controller.start(bytes = ByteArray(8) { it.toByte() }, filename = "FwPkt.zip", rebootAfter = false) {
            statuses += it
        }
        pump.cancel()

        val codes = conn.written.mapNotNull { parseCode(it) }
        assertEquals(listOf(284, 810, 784, 794, 794, 795, 811, 811), codes,
            "expected 284 handshake then firmware sequence, got $codes")

        assertIs<FirmwareUpdateController.Status.Done>(final)
        assertTrue(statuses.any { it is FirmwareUpdateController.Status.Uploading },
            "expected at least one Uploading status, got $statuses")
        assertTrue(statuses.any { it is FirmwareUpdateController.Status.Installing },
            "expected at least one Installing status, got $statuses")

        session.disconnect()
        runCurrent()
    }

    @Test
    fun happyPathFiresRebootWhenRequested() = runTest {
        val conn = FakeConnection()
        conn.pendingReplies += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        val session = MountSession({ conn }, readerScope = backgroundScope)
        assertTrue(session.connect())

        val pump = backgroundScope.launch {
            conn.awaitWriteOf(810); conn.replyForCode(810, "state:1;")
            conn.awaitWriteOf(784); conn.replyForCode(784, "ret:0;")
            conn.awaitWriteOf(795); conn.replyForCode(795, "ret:0;")
            conn.awaitWriteOf(811); conn.replyForCode(811, "p:100;")
            conn.awaitWriteCountOf(811, target = 2)
            conn.replyForCode(811, "p:100;")
            conn.awaitWriteOf(812); conn.replyForCode(812, "ret:0;")
        }

        val controller = FirmwareUpdateController(
            session = session,
            chunkSize = 4,
            progressPollMs = 50,
            progressDoneRepeats = 2,
            installTimeoutMs = 2_000,
        )
        val final = controller.start(bytes = ByteArray(4) { 0x10 }, filename = "FwPkt.zip", rebootAfter = true)
        pump.cancel()

        val codes = conn.written.mapNotNull { parseCode(it) }
        assertEquals(listOf(284, 810, 784, 794, 795, 811, 811, 812), codes,
            "expected reboot sequence, got $codes")
        assertIs<FirmwareUpdateController.Status.Done>(final)

        session.disconnect()
        runCurrent()
    }

    @Test
    fun emptyFirmwareFailsImmediately() = runTest {
        val conn = FakeConnection()
        conn.pendingReplies += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        val session = MountSession({ conn }, readerScope = backgroundScope)
        assertTrue(session.connect())

        val controller = FirmwareUpdateController(session = session)
        val final = controller.start(bytes = ByteArray(0), filename = "FwPkt.zip")

        assertIs<FirmwareUpdateController.Status.Failed>(final)
        assertEquals("no firmware bytes", (final as FirmwareUpdateController.Status.Failed).reason)
        assertEquals(1, conn.written.size, "only the 284 handshake should have been written")
        session.disconnect()
        runCurrent()
    }

    @Test
    fun armTimeoutReportsFailure() = runTest {
        val conn = FakeConnection()
        conn.pendingReplies += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        val session = MountSession({ conn }, readerScope = backgroundScope)
        assertTrue(session.connect())
        // No 810 reply queued → armFirmwareUpgrade() should time out.

        val controller = FirmwareUpdateController(
            session = session,
            armTimeoutMs = 100,
        )
        val final = controller.start(
            bytes = ByteArray(4) { 0xAA.toByte() },
            filename = "FwPkt.zip",
        )

        assertIs<FirmwareUpdateController.Status.Failed>(final)
        val reason = (final as FirmwareUpdateController.Status.Failed).reason
        assertTrue(reason.contains("810") || reason.contains("arm"),
            "expected arm-related reason, got: $reason")
        session.disconnect()
        runCurrent()
    }

    @Test
    fun installTimeoutReportsProgress() = runTest {
        val conn = FakeConnection()
        conn.pendingReplies += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        val session = MountSession({ conn }, readerScope = backgroundScope)
        assertTrue(session.connect())

        val pump = backgroundScope.launch {
            conn.awaitWriteOf(810); conn.replyForCode(810, "state:1;")
            conn.awaitWriteOf(784); conn.replyForCode(784, "ret:0;")
            conn.awaitWriteOf(795); conn.replyForCode(795, "ret:0;")
            conn.awaitWriteOf(811)
            // Feed p:50 on every 811 the controller issues.
            var lastCount = 0
            while (true) {
                val now = conn.written.count { parseCode(it) == 811 }
                while (now > lastCount) {
                    conn.replyForCode(811, "p:50;")
                    lastCount++
                }
                delay(1)
            }
        }

        val controller = FirmwareUpdateController(
            session = session,
            chunkSize = 2,
            progressPollMs = 50,
            progressDoneRepeats = 2,
            installTimeoutMs = 300,
        )
        val final = controller.start(
            bytes = ByteArray(2) { 0xCC.toByte() },
            filename = "FwPkt.zip",
        )
        pump.cancel()

        assertIs<FirmwareUpdateController.Status.Failed>(final)
        val reason = (final as FirmwareUpdateController.Status.Failed).reason
        assertTrue(reason.contains("timed out") && reason.contains("50"),
            "expected install-timeout reason containing '50', got: $reason")
        session.disconnect()
        runCurrent()
    }

    @Test
    fun uploadProgressReportsBytesSent() = runTest {
        val conn = FakeConnection()
        conn.pendingReplies += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        val session = MountSession({ conn }, readerScope = backgroundScope)
        assertTrue(session.connect())

        val pump = backgroundScope.launch {
            conn.awaitWriteOf(810); conn.replyForCode(810, "state:1;")
            conn.awaitWriteOf(784); conn.replyForCode(784, "ret:0;")
            conn.awaitWriteOf(795); conn.replyForCode(795, "ret:0;")
            conn.awaitWriteOf(811); conn.replyForCode(811, "p:100;")
            conn.awaitWriteCountOf(811, target = 2)
            conn.replyForCode(811, "p:100;")
        }

        val controller = FirmwareUpdateController(
            session = session,
            chunkSize = 4,
            progressPollMs = 50,
            progressDoneRepeats = 2,
            installTimeoutMs = 2_000,
        )
        val bytes = ByteArray(12) { it.toByte() }
        val statuses = mutableListOf<FirmwareUpdateController.Status>()
        controller.start(bytes = bytes, filename = "FwPkt.zip", rebootAfter = false) {
            statuses += it
        }
        pump.cancel()

        val lastUploading = statuses.filterIsInstance<FirmwareUpdateController.Status.Uploading>().lastOrNull()
        assertEquals(12, lastUploading?.bytesSent, "expected last Uploading.bytesSent == 12, got $lastUploading")

        session.disconnect()
        runCurrent()
    }
}
