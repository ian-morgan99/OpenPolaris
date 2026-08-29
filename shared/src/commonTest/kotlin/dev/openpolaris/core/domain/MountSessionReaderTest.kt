package dev.openpolaris.core.domain

import dev.openpolaris.core.protocol.Codes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * Demux reader-loop tests (issue #6, PLAN-CRITICAL-REVIEW §F).
 *
 * The [MountSession] demux design has a single background reader coroutine
 * that:
 *  1. Owns the [Connection.read] loop.
 *  2. Publishes every parsed frame to [MountSession.frames] (a StateFlow
 *     retaining the most recent frame).
 *  3. Completes the [MountSession.request] waiter registered for the
 *     frame's code — and only that waiter.
 *
 * These tests pin the contract with a real [runReaderLoop] driven by a
 * [FakeConnection] whose [FakeConnection.responses] queue is pre-loaded
 * with the wire bytes the reader is expected to consume.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MountSessionReaderTest {

    /** Minimal [Connection] double. Returns -1 when the response queue
     *  is empty so the reader falls into its retry-loop `delay`. */
    private class FakeConnection : Connection {
        val written = mutableListOf<ByteArray>()
        val responses = mutableListOf<ByteArray>()

        override suspend fun connect(host: String, port: Int, timeoutMs: Int) {}

        override suspend fun write(data: ByteArray) {
            written += data
        }

        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int {
            if (responses.isEmpty()) return -1
            val r = responses.removeAt(0)
            r.copyInto(buffer)
            return r.size
        }

        override fun close() {}
    }

    @Test
    fun readerEmitsIncomingFramesOnFramesFlow() = runTest {
        val conn = FakeConnection()
        val s = MountSession({ conn }, readerScope = this)

        // Handshake response — the reader will publish 284 to `frames`
        // before we observe the StateFlow.
        conn.responses += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        s.connect()

        // After connect, the 284 frame is the most recent.
        assertEquals(Codes.PUSH_MODE_STATE, s.frames.value?.code)

        // Queue a non-538 push (285) to exercise the generic path on
        // `frames`. 538 is intentionally demuxed to the tilt flow (issue
        // #6) so it is NOT published here — see [tiltStreamRoutes538].
        conn.responses += "1&285&2&state:running;#".toByteArray(Charsets.US_ASCII)
        // Reader waits READ_RETRY_MS (10ms) between read attempts.
        advanceTimeBy(50)

        assertEquals(285, s.frames.value?.code)
        assertEquals("running", s.frames.value?.fields?.get("state"))
        s.disconnect()
    }

    @Test
    fun readerCompletesMatchingWaiterByCode() = runTest {
        val conn = FakeConnection()
        val s = MountSession({ conn }, readerScope = this)
        conn.responses += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        s.connect()

        // Pre-queue a 517 response, then issue a 517 request. The reader
        // must dispatch the response to the waiter's deferred.
        conn.responses += "1&517&2&yaw:1.5;pitch:-2.5;roll:3.5;#".toByteArray(Charsets.US_ASCII)
        val r = s.request(Codes.GET_GIMBAL_POS) { f ->
            Triple(f.fields["yaw"], f.fields["pitch"], f.fields["roll"])
        }
        val ok = assertIs<MountSession.CmdResult.Ok<Triple<String?, String?, String?>>>(r)
        assertEquals("1.5", ok.value.first)
        assertEquals("-2.5", ok.value.second)
        assertEquals("3.5", ok.value.third)
        s.disconnect()
    }

    @Test
    fun readerCancelsCleanlyOnDisconnect() = runTest {
        val conn = FakeConnection()
        val s = MountSession({ conn }, readerScope = this)
        conn.responses += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        s.connect()
        // Reader is now spinning on its retry delay. Disconnect should
        // cancel the reader without throwing and clear `connected`.
        s.disconnect()
        assertFalse(s.state.value.connected, "state.connected must be false after disconnect")
    }

    @Test
    fun readerDoesNotCompleteUnrelatedWaiters() = runTest {
        // A 538 push arriving while a 517 request is in flight must not
        // complete the 517 waiter. The matching 517 response is what
        // completes it.
        val conn = FakeConnection()
        val s = MountSession({ conn }, readerScope = this)
        conn.responses += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        s.connect()

        // Queue 538 (mismatched) then 517 (matching). The reader should
        // publish the 538 and route the 517 to the waiter.
        conn.responses += "1&538&2&pitch:9.9;roll:9.9;#".toByteArray(Charsets.US_ASCII)
        conn.responses += "1&517&2&yaw:7.0;pitch:8.0;roll:9.0;#".toByteArray(Charsets.US_ASCII)
        val r = s.request(Codes.GET_GIMBAL_POS) { f -> f.fields["yaw"] }
        val ok = assertIs<MountSession.CmdResult.Ok<String?>>(r)
        assertEquals(
            "7.0",
            ok.value,
            "waiter must receive the matching 517 frame, not the mismatched 538",
        )
        // The 538 was published to _frames, then overwritten by 517. The
        // waiter still got the 517 (the only frame whose code matched the
        // pending request). If the demux were broken, the waiter would
        // have completed with the 538 and the request would have returned
        // ProtocolError("parser returned null for code 517") because 538
        // has no "yaw" field.
        assertNotNull(s.frames.value)
        assertEquals(Codes.GET_GIMBAL_POS, s.frames.value?.code)
        s.disconnect()
    }
}
