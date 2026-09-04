package dev.openpolaris.core.domain

import dev.openpolaris.core.net.SshCommandResult
import dev.openpolaris.core.net.SshCommandRunner
import dev.openpolaris.core.protocol.Codes
import dev.openpolaris.core.util.Md5
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
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

    /** Pre-compute expected MD5 values for checksum-verification tests. */
    private fun md5Of(bytes: ByteArray): String = Md5.digest(bytes)


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

        /** Queue the canned replies [MountSession.connect] expects after the
         *  284 lifecycle handshake: the 820 auth probe (`needed:0` — most
         *  production firmware doesn't require a connection password) plus
         *  the 823 hello ack. Mirrors the helper in [MountSessionReaderTest]
         *  but uses this file's [pendingReplies] field. */
        fun queueDefaultAuthOk() {
            pendingReplies += "1&820&2&needed:0;#".toByteArray(Charsets.US_ASCII)
            pendingReplies += "1&823&2&app:openpolaris;ver:0.1.0;#".toByteArray(Charsets.US_ASCII)
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
        // ...then the 820+823 auth handshake (see [queueDefaultAuthOk]).
        conn.queueDefaultAuthOk()
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
            delivery = DeliveryMode.WIRE,
            chunkSize = 4,
            progressPollMs = 50,
            progressDoneRepeats = 2,
            installTimeoutMs = 2_000,
        )
        val statuses = mutableListOf<FirmwareUpdateController.Status>()
        val final = controller.start(bytes = ByteArray(8) { it.toByte() }, filename = "FwPkt.zip", expectedMd5 = md5Of(ByteArray(8) { it.toByte() }), rebootAfter = false) {
            statuses += it
        }
        pump.cancel()

        val codes = conn.written.mapNotNull { parseCode(it) }
        assertEquals(listOf(284, 820, 823, 810, 784, 794, 794, 795, 811, 811), codes,
            "expected 284 handshake, 820+823 auth, then firmware sequence, got $codes")

        assertIs<FirmwareUpdateController.Status.Done>(final)
        assertTrue(statuses.any { it is FirmwareUpdateController.Status.Uploading },
            "expected at least one Uploading status, got $statuses")
        assertTrue(statuses.any { it is FirmwareUpdateController.Status.Installing },
            "expected at least one Installing status, got $statuses")

        session.disconnect()
        runCurrent()
    }

    /**
     * In-memory [FirmwareDelivery] for tests. Records every call to
     * [deliver] and replays a [onProgress] ramp so the test can assert
     * the controller surfaces [Status.Uploading] updates.
     */
    private class RecordingDelivery : FirmwareDelivery {
        var lastBytes: ByteArray? = null
        var lastFilename: String? = null
        var callCount: Int = 0
            private set
        override suspend fun deliver(
            bytes: ByteArray,
            filename: String,
            onProgress: (bytesSent: Int) -> Unit,
        ) {
            callCount++
            lastBytes = bytes
            lastFilename = filename
            // Report progress in two steps so the test sees a Uploading
            // with a non-final byteSent.
            onProgress(bytes.size / 2)
            onProgress(bytes.size)
        }

    }

    @Test
    fun stalledSshTransferFailsWithRetryableStatus() = runTest {
        val delivery = object : FirmwareDelivery {
            override suspend fun deliver(
                bytes: ByteArray,
                filename: String,
                onProgress: (bytesSent: Int) -> Unit,
            ) {
                awaitCancellation()
            }
        }
        val controller = FirmwareUpdateController(
            session = MountSession({ FakeConnection() }, readerScope = backgroundScope),
            delivery = DeliveryMode.SSH_PIPE,
            sshDelivery = delivery,
            uploadProgressTimeoutMs = 1_000,
        )

        val final = controller.start(bytes = ByteArray(16), filename = "FwPkt.zip")

        val failure = assertIs<FirmwareUpdateController.Status.Failed>(final)
        assertTrue(
            failure.reason.startsWith("firmware transfer stalled:"),
            "expected a stalled-transfer error, got: ${failure.reason}",
        )
    }

    @Test
    fun md5MatchProceedsAndReachesDelivery() = runTest {
        // When the caller passes a correct expected MD5, the upload
        // proceeds normally. The MD5 check must run BEFORE any wire
        // traffic (Phase 1a #2: verify-before-upload), but a match
        // means the rest of the upload continues as before.
        val conn = FakeConnection()
        conn.pendingReplies += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        conn.queueDefaultAuthOk()
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
            delivery = DeliveryMode.WIRE,
            chunkSize = 4,
            progressPollMs = 50,
            progressDoneRepeats = 2,
            installTimeoutMs = 2_000,
        )
        val payload = ByteArray(8) { it.toByte() }
        // Local MD5: compute it from the same payload we hand to start().
        val localMd5 = dev.openpolaris.core.util.Md5.digest(payload)
        val final = controller.start(
            bytes = payload,
            filename = "FwPkt.zip",
            expectedMd5 = localMd5,
            rebootAfter = false,
        )
        pump.cancel()

        assertIs<FirmwareUpdateController.Status.Done>(final)
        // The full firmware sequence ran, i.e. the MD5 match did not
        // short-circuit any subsequent step.
        val codes = conn.written.mapNotNull { parseCode(it) }
        assertTrue(codes.contains(810), "expected 810 on wire, got $codes")
        assertTrue(codes.contains(784), "expected 784 on wire, got $codes")
        assertTrue(codes.contains(795), "expected 795 on wire, got $codes")

        session.disconnect()
        runCurrent()
    }

    @Test
    fun md5MismatchShortCircuitsBeforeAnyWireTraffic() = runTest {
        // When the caller passes a wrong expected MD5, the upload must
        // fail immediately with Status.Failed and must NOT issue ANY of
        // 810/784/794/795 on the wire. The user can fix the paste and
        // retry without corrupting the SD card.
        val conn = FakeConnection()
        conn.pendingReplies += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        conn.queueDefaultAuthOk()
        val session = MountSession({ conn }, readerScope = backgroundScope)
        assertTrue(session.connect())

        val controller = FirmwareUpdateController(
            session = session,
            delivery = DeliveryMode.WIRE,
            chunkSize = 4,
            progressPollMs = 50,
            progressDoneRepeats = 2,
            installTimeoutMs = 2_000,
        )
        val payload = ByteArray(8) { it.toByte() }
        val final = controller.start(
            bytes = payload,
            filename = "FwPkt.zip",
            // 32 hex chars, all '0' — guaranteed not to match the
            // computed MD5 of any non-trivial payload.
            expectedMd5 = "0".repeat(32),
            rebootAfter = false,
        )
        val s = assertIs<FirmwareUpdateController.Status.Failed>(final)
        assertTrue(
            s.reason.contains("MD5 mismatch"),
            "expected failure reason to start with 'MD5 mismatch', got: ${s.reason}",
        )
        assertTrue(
            s.reason.contains("expected=00000000000000000000000000000000"),
            "expected the user-supplied MD5 to appear in the reason, got: ${s.reason}",
        )
        // No firmware-plane codes must have been written. The 820+823
        // auth pair is allowed (those are the session's own handshake).
        val firmwareCodes = conn.written.mapNotNull { parseCode(it) }
            .filter { it in setOf(810, 784, 794, 795, 811, 812) }
        assertEquals(
            emptyList(), firmwareCodes,
            "MD5 mismatch must short-circuit before any wire traffic; got $firmwareCodes",
        )

        session.disconnect()
        runCurrent()
    }

    @Test
    fun blankExpectedMd5ProceedsWithoutVerification() = runTest {
        // The UI advertises MD5 verification as optional. A blank value must
        // proceed to delivery, while a supplied malformed or mismatched value
        // remains rejected by the tests below.
        val conn = FakeConnection()
        conn.pendingReplies += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        conn.queueDefaultAuthOk()
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
            delivery = DeliveryMode.WIRE,
            chunkSize = 4,
            progressPollMs = 50,
            progressDoneRepeats = 2,
            installTimeoutMs = 2_000,
        )
        val final = controller.start(
            bytes = ByteArray(8) { it.toByte() },
            filename = "FwPkt.zip",
            expectedMd5 = "",
            rebootAfter = false,
        )
        pump.cancel()

        assertIs<FirmwareUpdateController.Status.Done>(final)
        val codes = conn.written.mapNotNull { parseCode(it) }
        assertTrue(codes.contains(810), "expected 810 on wire, got $codes")
        assertTrue(codes.contains(795), "expected 795 on wire, got $codes")

        session.disconnect()
        runCurrent()
    }

    @Test
    fun malformedExpectedMd5FailsBeforeAnyWireTraffic() = runTest {
        // Issue #39: a paste error (wrong length, non-hex characters)
        // must be caught by the format check and surfaced with a clear
        // "must be 32 hexadecimal characters" reason, *before* the
        // local-bytes MD5 is even computed. This keeps the failure
        // message actionable: the user knows their paste is wrong, not
        // that the firmware itself is corrupted.
        val cases = listOf(
            "abcdef",                       // 6 chars
            "0".repeat(31),                 // 31 chars
            "0".repeat(33),                 // 33 chars
            "Z".repeat(32),                 // 32 chars but non-hex
            "1234567890abcdef1234567890abcde!", // 32 chars, 31 hex + 1 !
        )
        for (bad in cases) {
            val conn = FakeConnection()
            conn.pendingReplies += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
            conn.queueDefaultAuthOk()
            val session = MountSession({ conn }, readerScope = backgroundScope)
            assertTrue(session.connect())

            val controller = FirmwareUpdateController(
                session = session,
                delivery = DeliveryMode.WIRE,
                chunkSize = 4,
                progressPollMs = 50,
                progressDoneRepeats = 2,
                installTimeoutMs = 2_000,
            )
            val final = controller.start(
                bytes = ByteArray(8) { it.toByte() },
                filename = "FwPkt.zip",
                expectedMd5 = bad,
                unsafeAllowNoChecksum = false,
                rebootAfter = false,
            )

            assertIs<FirmwareUpdateController.Status.Failed>(final)
            val reason = (final as FirmwareUpdateController.Status.Failed).reason
            assertTrue(
                reason.contains("32 hexadecimal characters"),
                "expected format failure for \"$bad\", got: $reason",
            )
            val firmwareCodes = conn.written.mapNotNull { parseCode(it) }
                .filter { it in setOf(810, 784, 794, 795, 811, 812) }
            assertEquals(
                emptyList(), firmwareCodes,
                "malformed MD5 (\"$bad\") must short-circuit before any wire traffic; got $firmwareCodes",
            )

            session.disconnect()
            runCurrent()
        }
    }

    @Test
    fun sshPipeDeliveryDoesNotTouchTheWire() = runTest {
        // SshPipe delivery must NOT issue 810/784/794/795/811. The
        // controller has no business talking to the gimbal — the user's
        // [FirmwareDelivery] handles it, and the user then reboots the
        // gimbal manually. The wire must stay quiet.
        val conn = FakeConnection()
        conn.pendingReplies += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        conn.queueDefaultAuthOk()
        val session = MountSession({ conn }, readerScope = backgroundScope)
        assertTrue(session.connect())

        val delivery = RecordingDelivery()
        val controller = FirmwareUpdateController(
            session = session,
            delivery = DeliveryMode.SSH_PIPE,
            sshDelivery = delivery,
        )
        val statuses = mutableListOf<FirmwareUpdateController.Status>()
        val payload = ByteArray(64) { it.toByte() }
        val final = controller.start(bytes = payload, filename = "FwPkt.zip", expectedMd5 = md5Of(payload), rebootAfter = false) {
            statuses += it
        }

        assertIs<FirmwareUpdateController.Status.Done>(final)
        assertEquals(payload.size, delivery.lastBytes?.size)
        assertEquals("FwPkt.zip", delivery.lastFilename)
        // No firmware-plane codes (810, 784, 794, 795, 811, 812) on the
        // wire — the 820+823 auth pair is allowed (those are the
        // session's own handshake, not firmware).
        val firmwareCodes = conn.written.mapNotNull { parseCode(it) }
            .filter { it in setOf(810, 784, 794, 795, 811, 812) }
        assertEquals(emptyList(), firmwareCodes,
            "SSH_PIPE delivery must not write firmware codes; got $firmwareCodes")
        // And the controller reported an Uploading + Installing sequence.
        assertTrue(statuses.any { it is FirmwareUpdateController.Status.Uploading })
        assertTrue(statuses.any { it is FirmwareUpdateController.Status.Installing })

        session.disconnect()
        runCurrent()
    }

    @Test
    fun sshPipeWithRebootFiresReboot() = runTest {
        // SshPipe + rebootAfter=true should send 812 (reboot) after
        // the bytes are dropped. This is the rare case where the wire
        // IS involved in SSH_PIPE — but only for the post-install
        // reboot, not the upload itself.
        val conn = FakeConnection()
        conn.pendingReplies += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        conn.queueDefaultAuthOk()
        val session = MountSession({ conn }, readerScope = backgroundScope)
        assertTrue(session.connect())

        val pump = backgroundScope.launch {
            conn.awaitWriteOf(812); conn.replyForCode(812, "ret:0;")
        }
        val delivery = RecordingDelivery()
        val controller = FirmwareUpdateController(
            session = session,
            delivery = DeliveryMode.SSH_PIPE,
            sshDelivery = delivery,
        )
        val final = controller.start(
            bytes = ByteArray(16) { it.toByte() },
            filename = "FwPkt.zip",
            expectedMd5 = md5Of(ByteArray(16) { it.toByte() }),
            rebootAfter = true,
        )
        pump.cancel()
        assertIs<FirmwareUpdateController.Status.Done>(final)
        assertTrue(conn.writtenCode(812), "expected reboot (812) on the wire")

        session.disconnect()
        runCurrent()
    }

    @Test
    fun sshPipeWithoutDeliveryThrowsClearError() = runTest {
        // The default NoOpFirmwareDelivery is a sentinel — it throws so
        // the user cannot accidentally upload nothing. The controller
        // wraps the throw into a Status.Failed with a useful reason.
        val conn = FakeConnection()
        conn.pendingReplies += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        conn.queueDefaultAuthOk()
        val session = MountSession({ conn }, readerScope = backgroundScope)
        assertTrue(session.connect())

        val controller = FirmwareUpdateController(
            session = session,
            delivery = DeliveryMode.SSH_PIPE,
            // sshDelivery defaults to NoOpFirmwareDelivery
        )
        val final = controller.start(
            bytes = ByteArray(8) { it.toByte() },
            filename = "FwPkt.zip",
            // Leave MD5 blank so this test exercises the NoOp-delivery sentinel.
        )
        val s = assertIs<FirmwareUpdateController.Status.Failed>(final)
        assertTrue(s.reason.contains("scp delivery failed"),
            "expected the NoOp-sentinel throw to be wrapped into a 'scp delivery failed' failure, got: ${s.reason}")

        session.disconnect()
        runCurrent()
    }

    @Test
    fun happyPathFiresRebootWhenRequested() = runTest {
        val conn = FakeConnection()
        conn.pendingReplies += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        // ...then the 820+823 auth handshake (see [queueDefaultAuthOk]).
        conn.queueDefaultAuthOk()
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
            delivery = DeliveryMode.WIRE,
            chunkSize = 4,
            progressPollMs = 50,
            progressDoneRepeats = 2,
            installTimeoutMs = 2_000,
        )
        val payload = ByteArray(4) { 0x10 }
        val final = controller.start(bytes = payload, filename = "FwPkt.zip", expectedMd5 = md5Of(payload), rebootAfter = true)
        pump.cancel()

        val codes = conn.written.mapNotNull { parseCode(it) }
        assertEquals(listOf(284, 820, 823, 810, 784, 794, 795, 811, 811, 812), codes,
            "expected 284 handshake, 820+823 auth, then reboot sequence, got $codes")
        assertIs<FirmwareUpdateController.Status.Done>(final)

        session.disconnect()
        runCurrent()
    }

    @Test
    fun emptyFirmwareFailsImmediately() = runTest {
        val conn = FakeConnection()
        conn.pendingReplies += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        // ...then the 820+823 auth handshake (see [queueDefaultAuthOk]).
        conn.queueDefaultAuthOk()
        val session = MountSession({ conn }, readerScope = backgroundScope)
        assertTrue(session.connect())

        val controller = FirmwareUpdateController(session = session)
        val final = controller.start(bytes = ByteArray(0), filename = "FwPkt.zip")

        assertIs<FirmwareUpdateController.Status.Failed>(final)
        assertEquals("no firmware bytes", (final as FirmwareUpdateController.Status.Failed).reason)
        // 284 push-mode handshake + 820 password probe + 823 app hello.
        // The firmware controller returns Failed before any 810 frame
        // is written, so we expect exactly the connect-time auth
        // handshake frames.
        assertEquals(3, conn.written.size, "only the connect-time auth handshake should have been written")
        session.disconnect()
        runCurrent()
    }

    @Test
    fun oversizeFirmwareFailsImmediately() = runTest {
        // Phase 1a size cap (FIRMWARE-UPLOAD-AUDIT-2026-09-01.md §6).
        // Anything over 128 MB cannot land on the on-board SD card
        // (121 MB free, 128 MB partition). The controller must refuse
        // up-front rather than mid-flight, and must not even attempt
        // the wire sequence.
        val conn = FakeConnection()
        conn.pendingReplies += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        conn.queueDefaultAuthOk()
        val session = MountSession({ conn }, readerScope = backgroundScope)
        assertTrue(session.connect())

        val controller = FirmwareUpdateController(session = session)
        // 128 MB + 1 byte — over the cap.
        val oversize = ByteArray(128 * 1024 * 1024 + 1)
        val final = controller.start(bytes = oversize, filename = "FwPkt.zip")

        val failed = assertIs<FirmwareUpdateController.Status.Failed>(final)
        assertTrue(
            failed.reason.contains("128 MB") || failed.reason.contains("134217729"),
            "expected the 128 MB cap message in the failure reason, got: ${failed.reason}"
        )
        // No firmware-plane codes (810, 784, 794, 795, 811, 812) on the
        // wire — the cap short-circuits before startWire is called.
        val firmwareCodes = conn.written.mapNotNull { parseCode(it) }
            .filter { it in setOf(810, 784, 794, 795, 811, 812) }
        assertEquals(emptyList(), firmwareCodes,
            "oversize bundle must not write firmware codes; got $firmwareCodes")

        session.disconnect()
        runCurrent()
    }

    @Test
    fun at128MbFirmwareIsAccepted() = runTest {
        // 128 MB exactly is on the boundary. It should pass the cap and
        // proceed to the delivery path (SSH_PIPE here, so the wire stays
        // quiet and the FakeDelivery absorbs the bytes).
        val conn = FakeConnection()
        conn.pendingReplies += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        conn.queueDefaultAuthOk()
        val session = MountSession({ conn }, readerScope = backgroundScope)
        assertTrue(session.connect())

        val delivery = RecordingDelivery()
        val controller = FirmwareUpdateController(
            session = session,
            delivery = DeliveryMode.SSH_PIPE,
            sshDelivery = delivery,
        )
        val exact = ByteArray(128 * 1024 * 1024)
        val final = controller.start(bytes = exact, filename = "FwPkt.zip", expectedMd5 = md5Of(exact), rebootAfter = false)

        assertIs<FirmwareUpdateController.Status.Done>(final)
        assertEquals(128 * 1024 * 1024, delivery.lastBytes?.size)
        session.disconnect()
        runCurrent()
    }

    @Test
    fun armTimeoutReportsFailure() = runTest {
        val conn = FakeConnection()
        conn.pendingReplies += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        // ...then the 820+823 auth handshake (see [queueDefaultAuthOk]).
        conn.queueDefaultAuthOk()
        val session = MountSession({ conn }, readerScope = backgroundScope)
        assertTrue(session.connect())
        // No 810 reply queued → armFirmwareUpgrade() should time out.

        val controller = FirmwareUpdateController(
            session = session,
            delivery = DeliveryMode.WIRE,
            armTimeoutMs = 100,
        )
        val armPayload = ByteArray(4) { 0xAA.toByte() }
        val final = controller.start(
            bytes = armPayload,
            filename = "FwPkt.zip",
            expectedMd5 = md5Of(armPayload),
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
        // ...then the 820+823 auth handshake (see [queueDefaultAuthOk]).
        conn.queueDefaultAuthOk()
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
            delivery = DeliveryMode.WIRE,
            chunkSize = 2,
            progressPollMs = 50,
            progressDoneRepeats = 2,
            installTimeoutMs = 300,
        )
        val installPayload = ByteArray(2) { 0xCC.toByte() }
        val final = controller.start(
            bytes = installPayload,
            filename = "FwPkt.zip",
            expectedMd5 = md5Of(installPayload),
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
        // ...then the 820+823 auth handshake (see [queueDefaultAuthOk]).
        conn.queueDefaultAuthOk()
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
            delivery = DeliveryMode.WIRE,
            chunkSize = 4,
            progressPollMs = 50,
            progressDoneRepeats = 2,
            installTimeoutMs = 2_000,
        )
        val bytes = ByteArray(12) { it.toByte() }
        val statuses = mutableListOf<FirmwareUpdateController.Status>()
        controller.start(bytes = bytes, filename = "FwPkt.zip", expectedMd5 = md5Of(bytes), rebootAfter = false) {
            statuses += it
        }
        pump.cancel()

        val lastUploading = statuses.filterIsInstance<FirmwareUpdateController.Status.Uploading>().lastOrNull()
        assertEquals(12, lastUploading?.bytesSent, "expected last Uploading.bytesSent == 12, got $lastUploading")

        session.disconnect()
        runCurrent()
    }

    // ---- SSH_PIPE install-with-watcher (Phase 2) -------------------------

    /**
     * [FirmwareUpdateController] with an [SshCommandRunner] provided
     * must, after the bytes land on the SD card:
     *   1. restart `polestar_app` via `pkill polestar_app; nohup ...`,
     *   2. poll `/app/Mlog.txt` until a terminal sentinel appears,
     *   3. surface `Status.Done` when the sentinel is a Pass.
     *
     * The SSH seam is faked with [ScriptedSshRunner] which hands back
     * a different stdout per call. The test scripts the first call
     * (the restart) to succeed and the second (the first tail) to
     * include `SP_EVENT_UPGRADE_SUCCESS`.
     */
    @Test
    fun sshPipeWithWatcher_returnsDone_whenMlogReportsSuccess() = runTest {
        val conn = FakeConnection()
        conn.pendingReplies += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        conn.queueDefaultAuthOk()
        val session = MountSession({ conn }, readerScope = backgroundScope)
        assertTrue(session.connect())

        val ssh = ScriptedSshRunner()
        ssh.scriptNext(0, "Filesystem 1B-blocks Used Available Use% Mounted on\n/dev/root 200000000 50000000 150000000 25% /app/sd\n")  // pre-flight df: 150 MB free
        ssh.scriptNext(0, "")                  // pkill+nohup restart
        ssh.scriptNext(0, "[OMS] SP_EVENT_UPGRADE_SUCCESS\n")  // first Mlog tail

        val delivery = RecordingDelivery()
        val controller = FirmwareUpdateController(
            session = session,
            delivery = DeliveryMode.SSH_PIPE,
            sshDelivery = delivery,
            sshCommandRunner = ssh,
            installPollIntervalMs = 0,
            onBoardInstallTimeoutMs = 2_000,
        )
        val statuses = mutableListOf<FirmwareUpdateController.Status>()
        val watcherPayload = ByteArray(64) { it.toByte() }
        val final = controller.start(
            bytes = watcherPayload,
            filename = "FwPkt.zip",
            expectedMd5 = md5Of(watcherPayload),
            rebootAfter = false,
        ) { statuses += it }

        assertIs<FirmwareUpdateController.Status.Done>(final)
        // 3 scripted SSH calls: 1 pre-flight df + 1 restart + 1 tail
        // (the tail returned Pass, so we don't keep polling).
        assertEquals(3, ssh.commands.size, "expected exactly 3 SSH calls, got: ${ssh.commands}")
        assertTrue(ssh.commands[0].contains("df -B1 /app/sd"),
            "first SSH call should be the pre-flight df, got: ${ssh.commands[0]}")
        assertTrue(ssh.commands[1].contains("pkill polestar_app"),
            "second SSH call should restart polestar_app, got: ${ssh.commands[1]}")
        assertTrue(ssh.commands[2].contains("/app/Mlog.txt"),
            "third SSH call should tail Mlog.txt, got: ${ssh.commands[2]}")
        // The controller must surface an Installing(100) before Done so the UI can show 100%.
        val installing100 = statuses.filterIsInstance<FirmwareUpdateController.Status.Installing>()
            .any { it.percent == 100 }
        assertTrue(installing100, "expected Installing(100) before Done, got: $statuses")

        session.disconnect()
        runCurrent()
    }

    @Test
    fun sshPipeWithWatcher_returnsFailed_whenMlogReportsFail() = runTest {
        val conn = FakeConnection()
        conn.pendingReplies += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        conn.queueDefaultAuthOk()
        val session = MountSession({ conn }, readerScope = backgroundScope)
        assertTrue(session.connect())

        val ssh = ScriptedSshRunner()
        ssh.scriptNext(0, "Filesystem 1B-blocks Used Available Use% Mounted on\n/dev/root 200000000 50000000 150000000 25% /app/sd\n")  // pre-flight df
        ssh.scriptNext(0, "")                                // restart
        ssh.scriptNext(0, "[OMS] SP_EVENT_UPGRADE_FAIL crc_mismatch\n")  // tail → Fail

        val delivery = RecordingDelivery()
        val controller = FirmwareUpdateController(
            session = session,
            delivery = DeliveryMode.SSH_PIPE,
            sshDelivery = delivery,
            sshCommandRunner = ssh,
            installPollIntervalMs = 0,
            onBoardInstallTimeoutMs = 2_000,
        )
        val failPayload = ByteArray(32) { it.toByte() }
        val final = controller.start(
            bytes = failPayload,
            filename = "FwPkt.zip",
            expectedMd5 = md5Of(failPayload),
            rebootAfter = false,
        )

        val failed = assertIs<FirmwareUpdateController.Status.Failed>(final)
        assertTrue(failed.reason.contains("on-board install failed"),
            "expected on-board install failure reason, got: ${failed.reason}")
        // Exactly 3 SSH calls: 1 pre-flight df + 1 restart + 1 tail
        // (the tail returned Fail, so we stop).
        assertEquals(3, ssh.commands.size, "expected exactly 3 SSH calls, got: ${ssh.commands}")

        session.disconnect()
        runCurrent()
    }

    @Test
    fun sshPipeWithWatcher_returnsTimeout_whenMlogNeverSentinels() = runTest {
        val conn = FakeConnection()
        conn.pendingReplies += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        conn.queueDefaultAuthOk()
        val session = MountSession({ conn }, readerScope = backgroundScope)
        assertTrue(session.connect())

        // All Mlog tails are empty — no sentinel ever appears.
        val ssh = ScriptedSshRunner()
        ssh.scriptNext(0, "Filesystem 1B-blocks Used Available Use% Mounted on\n/dev/root 200000000 50000000 150000000 25% /app/sd\n")  // pre-flight df
        ssh.scriptNext(0, "")  // restart
        // The watcher keeps calling tail forever; we pre-script a finite
        // number of empty responses. The watcher will eventually time out
        // based on its own clock (real wall clock here; we set timeout to 50ms
        // so this test still finishes quickly).

        val delivery = RecordingDelivery()
        val controller = FirmwareUpdateController(
            session = session,
            delivery = DeliveryMode.SSH_PIPE,
            sshDelivery = delivery,
            sshCommandRunner = ssh,
            installPollIntervalMs = 0,
            onBoardInstallTimeoutMs = 50,
        )
        val timeoutPayload = ByteArray(16) { it.toByte() }
        val final = controller.start(
            bytes = timeoutPayload,
            filename = "FwPkt.zip",
            expectedMd5 = md5Of(timeoutPayload),
            rebootAfter = false,
        )

        val failed = assertIs<FirmwareUpdateController.Status.Failed>(final)
        assertTrue(failed.reason.contains("on-board install timed out"),
            "expected on-board install timeout reason, got: ${failed.reason}")
        assertTrue(failed.reason.contains("50ms"),
            "expected the 50ms timeout to be quoted in the reason, got: ${failed.reason}")
        // At least 1 pre-flight + 1 restart + at least 1 tail before timing out.
        assertTrue(ssh.commands.size >= 3,
            "expected at least pre-flight + restart + 1 tail call, got: ${ssh.commands.size}")
        assertTrue(ssh.commands[0].contains("df -B1 /app/sd"),
            "first SSH call should be the pre-flight df, got: ${ssh.commands[0]}")
        assertTrue(ssh.commands[1].contains("pkill polestar_app"),
            "second SSH call should restart polestar_app, got: ${ssh.commands[1]}")

        session.disconnect()
        runCurrent()
    }

    @Test
    fun sshPipeWithWatcher_returnsFailed_whenRestartExitsNonZero() = runTest {
        val conn = FakeConnection()
        conn.pendingReplies += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        conn.queueDefaultAuthOk()
        val session = MountSession({ conn }, readerScope = backgroundScope)
        assertTrue(session.connect())

        val ssh = ScriptedSshRunner()
        ssh.scriptNext(0, "Filesystem 1B-blocks Used Available Use% Mounted on\n/dev/root 200000000 50000000 150000000 25% /app/sd\n")  // pre-flight df
        ssh.scriptNext(127, "", "pkill: no such process")  // restart fails

        val delivery = RecordingDelivery()
        val controller = FirmwareUpdateController(
            session = session,
            delivery = DeliveryMode.SSH_PIPE,
            sshDelivery = delivery,
            sshCommandRunner = ssh,
            installPollIntervalMs = 0,
            onBoardInstallTimeoutMs = 2_000,
        )
        val restartPayload = ByteArray(16) { it.toByte() }
        val final = controller.start(
            bytes = restartPayload,
            filename = "FwPkt.zip",
            expectedMd5 = md5Of(restartPayload),
            rebootAfter = false,
        )

        val failed = assertIs<FirmwareUpdateController.Status.Failed>(final)
        assertTrue(failed.reason.contains("restart") || failed.reason.contains("exit=127"),
            "expected restart-failure reason, got: ${failed.reason}")
        // Pre-flight ran + restart was attempted — no tail calls because we bailed out.
        assertEquals(2, ssh.commands.size,
            "expected pre-flight + restart, got: ${ssh.commands.size}")

        session.disconnect()
        runCurrent()
    }

    /**
     * Pre-flight `df` reports less free space than the bundle
     * needs. The controller must refuse *before* any bytes are
     * pushed, so `sshDelivery.deliver()` should never be called.
     * This guards the FAT-corruption footgun the audit calls out
     * in `docs/FIRMWARE-UPLOAD-AUDIT-2026-09-01.md` §6.
     */
    @Test
    fun sshPipePreflight_refusesUploadWhenSdIsTooSmall() = runTest {
        val conn = FakeConnection()
        conn.pendingReplies += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        conn.queueDefaultAuthOk()
        val session = MountSession({ conn }, readerScope = backgroundScope)
        assertTrue(session.connect())

        val ssh = ScriptedSshRunner()
        // df reports only 1024 bytes free — bundle needs bundle-size + 1 MB.
        ssh.scriptNext(0, "Filesystem 1B-blocks Used Available Use% Mounted on\n/dev/root 200000000 199999976 1024 100% /app/sd\n")

        val delivery = RecordingDelivery()
        val controller = FirmwareUpdateController(
            session = session,
            delivery = DeliveryMode.SSH_PIPE,
            sshDelivery = delivery,
            sshCommandRunner = ssh,
            installPollIntervalMs = 0,
            onBoardInstallTimeoutMs = 2_000,
        )
        val preflightPayload = ByteArray(64) { it.toByte() }
        val final = controller.start(
            bytes = preflightPayload,
            filename = "FwPkt.zip",
            expectedMd5 = md5Of(preflightPayload),
            rebootAfter = false,
        )

        val failed = assertIs<FirmwareUpdateController.Status.Failed>(final)
        assertTrue(failed.reason.contains("/app/sd has"),
            "expected pre-flight refusal reason, got: ${failed.reason}")
        assertEquals(0, delivery.callCount,
            "scp delivery must not run when pre-flight refused, got ${delivery.callCount} calls")
        assertEquals(1, ssh.commands.size,
            "only the pre-flight df should have run, got: ${ssh.commands}")

        session.disconnect()
        runCurrent()
    }

    /**
     * Pre-flight `df` is missing (busybox stripped) — controller
     * should fall through (fail-open) and continue with the upload.
     * The in-band `cat > ...` will still surface a real
     * "no space" error if the card is actually full.
     */
    @Test
    fun sshPipePreflight_isFailOpenWhenDfMissing() = runTest {
        val conn = FakeConnection()
        conn.pendingReplies += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        conn.queueDefaultAuthOk()
        val session = MountSession({ conn }, readerScope = backgroundScope)
        assertTrue(session.connect())

        val ssh = ScriptedSshRunner()
        // `df` exits 127 (not found) — pre-flight should treat as
        // "could not determine" and continue optimistically.
        ssh.scriptNext(127, "", "sh: df: not found")
        ssh.scriptNext(0, "")                                     // restart
        ssh.scriptNext(0, "[OMS] SP_EVENT_UPGRADE_SUCCESS\n")    // tail → Pass

        val delivery = RecordingDelivery()
        val controller = FirmwareUpdateController(
            session = session,
            delivery = DeliveryMode.SSH_PIPE,
            sshDelivery = delivery,
            sshCommandRunner = ssh,
            installPollIntervalMs = 0,
            onBoardInstallTimeoutMs = 2_000,
        )
        val preflightPayload = ByteArray(64) { it.toByte() }
        val final = controller.start(
            bytes = preflightPayload,
            filename = "FwPkt.zip",
            expectedMd5 = md5Of(preflightPayload),
            rebootAfter = false,
        )

        assertIs<FirmwareUpdateController.Status.Done>(final)
        assertEquals(1, delivery.callCount,
            "scp delivery should still run when df is missing, got ${delivery.callCount} calls, ssh.commands=${ssh.commands}")

        session.disconnect()
        runCurrent()
    }

    /**
     * Pre-flight `df` returns only the header (no data row), e.g.
     * the path doesn't exist on the target. The controller must
     * fail open and continue with the upload.
     */
    @Test
    fun sshPipePreflight_isFailOpenWhenDfHasNoDataRow() = runTest {
        val conn = FakeConnection()
        conn.pendingReplies += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        conn.queueDefaultAuthOk()
        val session = MountSession({ conn }, readerScope = backgroundScope)
        assertTrue(session.connect())

        val ssh = ScriptedSshRunner()
        ssh.scriptNext(0, "Filesystem 1B-blocks Used Available Use% Mounted on\n")  // header only
        ssh.scriptNext(0, "")                                     // restart
        ssh.scriptNext(0, "[OMS] SP_EVENT_UPGRADE_SUCCESS\n")    // tail → Pass

        val delivery = RecordingDelivery()
        val controller = FirmwareUpdateController(
            session = session,
            delivery = DeliveryMode.SSH_PIPE,
            sshDelivery = delivery,
            sshCommandRunner = ssh,
            installPollIntervalMs = 0,
            onBoardInstallTimeoutMs = 2_000,
        )
        val preflightPayload = ByteArray(64) { it.toByte() }
        val final = controller.start(
            bytes = preflightPayload,
            filename = "FwPkt.zip",
            expectedMd5 = md5Of(preflightPayload),
            rebootAfter = false,
        )

        assertIs<FirmwareUpdateController.Status.Done>(final)
        assertEquals(1, delivery.callCount,
            "scp delivery should run when df returns header only, got ${delivery.callCount} calls")

        session.disconnect()
        runCurrent()
    }

    /**
     * Pre-flight `df` succeeds with plenty of space — sanity check
     * that a healthy response does not falsely refuse.
     */
    @Test
    fun sshPipePreflight_proceedsWhenSdHasPlentyOfSpace() = runTest {
        val conn = FakeConnection()
        conn.pendingReplies += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        conn.queueDefaultAuthOk()
        val session = MountSession({ conn }, readerScope = backgroundScope)
        assertTrue(session.connect())

        val ssh = ScriptedSshRunner()
        // 200 MB free — way more than the 1 MB + 64 byte bundle.
        ssh.scriptNext(0, "Filesystem 1B-blocks Used Available Use% Mounted on\n/dev/root 250000000 50000000 200000000 20% /app/sd\n")
        ssh.scriptNext(0, "")                                     // restart
        ssh.scriptNext(0, "[OMS] SP_EVENT_UPGRADE_SUCCESS\n")    // tail → Pass

        val delivery = RecordingDelivery()
        val controller = FirmwareUpdateController(
            session = session,
            delivery = DeliveryMode.SSH_PIPE,
            sshDelivery = delivery,
            sshCommandRunner = ssh,
            installPollIntervalMs = 0,
            onBoardInstallTimeoutMs = 2_000,
        )
        val preflightPayload = ByteArray(64) { it.toByte() }
        val final = controller.start(
            bytes = preflightPayload,
            filename = "FwPkt.zip",
            expectedMd5 = md5Of(preflightPayload),
            rebootAfter = false,
        )

        assertIs<FirmwareUpdateController.Status.Done>(final)
        assertEquals(1, delivery.callCount)

        session.disconnect()
        runCurrent()
    }

    /**
     * Pre-flight `df` returns non-numeric columns in the data row
     * (e.g. `df` was aliased to a busybox applet that omits
     * `-B1` support). Controller must fail open rather than
     * refuse based on a parse failure.
     */
    @Test
    fun sshPipePreflight_isFailOpenWhenDfColumnsAreNonNumeric() = runTest {
        val conn = FakeConnection()
        conn.pendingReplies += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        conn.queueDefaultAuthOk()
        val session = MountSession({ conn }, readerScope = backgroundScope)
        assertTrue(session.connect())

        val ssh = ScriptedSshRunner()
        // busybox-style output with `1K-blocks` units — parts[1..3]
        // are not all digits, so the parser should bail and return Ok.
        ssh.scriptNext(0, "Filesystem 1K-blocks Used Available Use% Mounted on\n/dev/root 123456 78901 44444 64% /app/sd\n")
        ssh.scriptNext(0, "")                                     // restart
        ssh.scriptNext(0, "[OMS] SP_EVENT_UPGRADE_SUCCESS\n")    // tail → Pass

        val delivery = RecordingDelivery()
        val controller = FirmwareUpdateController(
            session = session,
            delivery = DeliveryMode.SSH_PIPE,
            sshDelivery = delivery,
            sshCommandRunner = ssh,
            installPollIntervalMs = 0,
            onBoardInstallTimeoutMs = 2_000,
        )
        val preflightPayload = ByteArray(64) { it.toByte() }
        val final = controller.start(
            bytes = preflightPayload,
            filename = "FwPkt.zip",
            expectedMd5 = md5Of(preflightPayload),
            rebootAfter = false,
        )

        assertIs<FirmwareUpdateController.Status.Done>(final)
        assertEquals(1, delivery.callCount,
            "scp delivery should run when df columns are non-numeric, got ${delivery.callCount} calls")

        session.disconnect()
        runCurrent()
    }

    /**
     * Scripted [SshCommandRunner] that hands out a different
     * stdout/stderr/exitCode per call. Tests push scripted
     * outcomes with [scriptNext] in the order the calls happen.
     */
    private class ScriptedSshRunner : SshCommandRunner {
        val commands = mutableListOf<String>()
        private val queue = ArrayDeque<SshCommandResult>()

        fun scriptNext(exitCode: Int, stdout: String, stderr: String = "") {
            queue.addLast(SshCommandResult(exitCode = exitCode, stdout = stdout, stderr = stderr))
        }

        override suspend fun run(command: String): SshCommandResult {
            commands += command
            return if (queue.isEmpty()) {
                // Beyond the scripted end: keep returning empty stdout so the
                // watcher keeps polling and eventually times out.
                SshCommandResult(exitCode = 0, stdout = "", stderr = "")
            } else {
                queue.removeFirst()
            }
        }
    }
}
