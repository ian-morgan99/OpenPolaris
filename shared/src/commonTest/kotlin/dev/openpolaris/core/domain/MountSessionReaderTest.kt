package dev.openpolaris.core.domain

import dev.openpolaris.core.protocol.Codes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
 * These tests pin the contract with a real MountSession driven by a
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
        var failConnect = false

        override suspend fun connect(host: String, port: Int, timeoutMs: Int) {
            if (failConnect) throw java.io.IOException("refused")
        }

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

    /**
     * Queue the canned replies [MountSession.connect] expects after the
     * 284 lifecycle handshake: the 820 auth probe (`needed:0` — most
     * production firmware doesn't require a connection password) plus
     * the 823 hello ack. Tests that pre-load only the 284 reply and
     * call [MountSession.connect] will time out on the 820 wait and
     * return false; the helper keeps the upgrade mechanical (one line
     * per test) so the focus stays on what's being tested.
     */
    private fun FakeConnection.queueDefaultAuthOk() {
        responses += "1&820&2&needed:0;#".toByteArray(Charsets.US_ASCII)
        responses += "1&823&2&app:openpolaris;ver:0.1.0;#".toByteArray(Charsets.US_ASCII)
    }

    private data class GimbalPose(val yaw: Double, val pitch: Double, val roll: Double)

    @Test
    fun connectWritesHandshakeAndDoesNotBlock() = runTest {
        val conn = FakeConnection()
        // connect() does a synchronous PUSH_MODE_STATE handshake; queue the
        // 284 reply so the handshake completes.
        conn.responses += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        // ...then the 820+823 auth handshake (see [queueDefaultAuthOk]).
        conn.queueDefaultAuthOk()
        val session = MountSession({ conn }, readerScope = backgroundScope)
        assertTrue(session.connect())
        // First write is the PUSH_MODE_STATE handshake poll.
        val first = conn.written.first().decodeToString()
        assertEquals("1&${Codes.PUSH_MODE_STATE}&2&-100#", first)
        session.disconnect()
    }

    @Test
    fun tiltFlowEmits538PushFrames() = runTest {
        val conn = FakeConnection()
        // connect() does a synchronous PUSH_MODE_STATE handshake; queue the
        // 284 reply so the handshake completes.
        conn.responses += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        // ...then the 820+823 auth handshake (see [queueDefaultAuthOk]).
        conn.queueDefaultAuthOk()
        val session = MountSession({ conn }, readerScope = backgroundScope)
        assertTrue(session.connect())

        // Subscribe to the tilt flow *before* pushing the 538 frame.
        // The shared flow has replay=0 and SUSPEND overflow, so a
        // frame emitted with no collector is dropped — the test
        // would otherwise race the reader and lose the sample.
        val sampleDeferred = async { session.tilt.first() }
        runCurrent() // let `tilt.first()` register as a collector
        conn.responses += "1&538&2&pitch:1.25;roll:-0.5;#".toByteArray(Charsets.US_ASCII)
        // Pump the scheduler so the background reader can dispatch
        // the frame into the now-active tilt flow.
        runCurrent()
        val sample = sampleDeferred.await()
        assertEquals(1.25, sample.pitchDeg, 1e-9)
        assertEquals(-0.5, sample.rollDeg, 1e-9)
        session.disconnect()
        // disconnect() cancels the reader job, but the cancellation
        // is delivered the next time the test scheduler runs the
        // reader coroutine. Without this pump the reader is still
        // parked on channel.receive() when the test body returns and
        // runTest reports UncompletedCoroutinesError. The channel is
        // also closed by disconnect(), which is what wakes the reader.
        runCurrent()
    }

    @Test
    fun interleaved538DoesNotDelayRequest() = runTest {
        val conn = FakeConnection()
        // connect() does a synchronous PUSH_MODE_STATE handshake; queue the
        // 284 reply so the handshake completes.
        conn.responses += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        // ...then the 820+823 auth handshake (see [queueDefaultAuthOk]).
        conn.queueDefaultAuthOk()
        val session = MountSession({ conn }, readerScope = backgroundScope)
        val ok = session.connect()
        assertTrue(ok)

        // Queue a 538 push *before* the 517 reply. The reader must
        // route the 538 to tilt (see [tiltFlowEmits538PushFrames])
        // and still hand the 517 to the waiter — i.e. the demux is
        // correct even with push frames interleaved into the
        // request stream, and a 538 in front of a 517 does not
        // satisfy the 517 waiter.
        conn.responses += "1&538&2&pitch:0.0;roll:0.0;#".toByteArray(Charsets.US_ASCII)
        conn.responses += "1&517&2&yaw:42.0;pitch:17.5;roll:0.0;#".toByteArray(Charsets.US_ASCII)

        val result = session.request(Codes.GET_GIMBAL_POS) { f ->
            GimbalPose(yaw = f.float("yaw")?.toDouble() ?: 0.0,
                       pitch = f.float("pitch")?.toDouble() ?: 0.0,
                       roll = f.float("roll")?.toDouble() ?: 0.0)
        }

        assertTrue(result is MountSession.CmdResult.Ok<*>, "expected Ok, got $result")
        val pose = (result as MountSession.CmdResult.Ok).value as GimbalPose
        assertEquals(42.0, pose.yaw, 1e-9)
        assertEquals(17.5, pose.pitch, 1e-9)
        assertEquals(0.0, pose.roll, 1e-9)
        session.disconnect()
    }

    @Test
    fun requestTimesOutWhenNoReplyArrives() = runTest {
        val conn = FakeConnection()
        // connect() does a synchronous PUSH_MODE_STATE handshake; queue the
        // 284 reply so the handshake completes. The next request below
        // (GET_GIMBAL_POS) intentionally has no reply, so it times out.
        conn.responses += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        // ...then the 820+823 auth handshake (see [queueDefaultAuthOk]).
        conn.queueDefaultAuthOk()
        val session = MountSession({ conn }, readerScope = backgroundScope)
        assertTrue(session.connect())

        // 200 ms is short enough to keep the test fast but long enough
        // that the reader has had a chance to run.
        val result = session.request(Codes.GET_GIMBAL_POS, timeoutMs = 200) { GimbalPose(0.0, 0.0, 0.0) }
        assertEquals(MountSession.CmdResult.Timeout, result)
        session.disconnect()
    }

    @Test
    fun disconnectCancelsReaderAndFailsPending() = runTest {
        val conn = FakeConnection()
        // connect() does a synchronous PUSH_MODE_STATE handshake; queue the
        // 284 reply so the handshake completes.
        conn.responses += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        // ...then the 820+823 auth handshake (see [queueDefaultAuthOk]).
        conn.queueDefaultAuthOk()
        val session = MountSession({ conn }, readerScope = backgroundScope)
        assertTrue(session.connect())

        // A request in flight: we don't enqueue a reply, so without
        // disconnect it would time out. Instead, disconnect should
        // fail the waiter immediately.
        val deferred = async {
            session.request(Codes.GET_GIMBAL_POS, timeoutMs = 5_000) { GimbalPose(0.0, 0.0, 0.0) }
        }
        // Give the request a chance to register the waiter before
        // we call disconnect. We use runCurrent rather than
        // advanceUntilIdle: advanceUntilIdle would also fire the
        // 5 s request timeout, returning Timeout instead of the
        // ProtocolError we want to assert on. runCurrent runs the
        // currently-scheduled tasks (which is enough to register
        // the waiter) without skipping past the timeout deadline.
        runCurrent()
        session.disconnect()
        val result = deferred.await()
        // The reader was cancelled, the waiter was failed with a
        // plain IllegalStateException by disconnect() — request
        // converts that to a ProtocolError so the caller does not
        // have to know whether the failure came from the wire or
        // from a local shutdown.
        assertTrue(result is MountSession.CmdResult.ProtocolError,
            "expected ProtocolError after disconnect, got $result")
    }

    @Test
    fun connectFailureDoesNotMarkSessionConnected() = runTest {
        val conn = FakeConnection().apply { failConnect = true }
        val session = MountSession({ conn }, readerScope = backgroundScope)
        assertFalse(session.connect())
        assertFalse(session.state.value.connected)
        // A subsequent request must not hang waiting for a reader that
        // never started; it should immediately return a ProtocolError
        // because connection is null.
        val result = session.request(Codes.GET_GIMBAL_POS) { GimbalPose(0.0, 0.0, 0.0) }
        assertTrue(result is MountSession.CmdResult.ProtocolError,
            "expected ProtocolError when not connected, got $result")
    }

    @Test
    fun readerEmitsIncomingFramesOnFramesFlow() = runTest {
        val conn = FakeConnection()
        val s = MountSession({ conn }, readerScope = this)

        // Handshake response — the reader will publish 284 to `frames`
        // before we observe the StateFlow.
        conn.responses += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        // ...then the 820+823 auth handshake (see [queueDefaultAuthOk]).
        // NB: 823 is fire-and-forget on real firmware, so the test
        // still enqueues a reply to verify the reader publishes it to
        // `frames` when it does happen to come back.
        conn.queueDefaultAuthOk()
        s.connect()

        // 823 is fire-and-forget now — connect() returns as soon as
        // the 823 frame is *written*, before the reader has had a
        // chance to consume the queued 823 reply. Advance virtual
        // time so the reader picks it up.
        advanceTimeBy(50)

        // After connect + the 823 reply is read, the 823 hello ack is
        // the most recent frame on `_frames` (it's published after
        // 284 and 820). The 284 frame was also published — it just
        // got overwritten by the later frames. See
        // [MountSession.connect] for the full handshake order.
        assertEquals(Codes.APP_HELLO, s.frames.value?.code)

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
        // ...then the 820+823 auth handshake (see [queueDefaultAuthOk]).
        conn.queueDefaultAuthOk()
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
        // ...then the 820+823 auth handshake (see [queueDefaultAuthOk]).
        conn.queueDefaultAuthOk()
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
        // ...then the 820+823 auth handshake (see [queueDefaultAuthOk]).
        conn.queueDefaultAuthOk()
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
