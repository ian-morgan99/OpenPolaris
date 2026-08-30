package dev.openpolaris.core.domain

import dev.openpolaris.core.protocol.Codes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the session-level background reader introduced by issue #6.
 *
 * These tests exercise the wire-level behaviour of [MountSession] that
 * the controller-level tests cannot see:
 *
 *  - the handshake is fire-and-forget, so `connect()` does not block on
 *    a 284 reply and an interleaved 284 frame from the mount is not
 *    mistaken for a request response;
 *  - 538 push frames are demultiplexed to the [MountSession.tilt] flow
 *    and do not delay a concurrent [MountSession.request];
 *  - the reader is owned by the session and cancelled on disconnect.
 *
 * Every test passes its own [CoroutineScope] (the [TestScope] of
 * `runTest`) as `readerScope` so the reader runs in virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MountSessionReaderTest {

    @Test
    fun connectWritesHandshakeAndDoesNotBlock() = runTest {
        val conn = FakeConnection()
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
        val session = MountSession({ conn }, readerScope = backgroundScope)
        assertTrue(session.connect())

        // Subscribe to the tilt flow *before* pushing the 538 frame.
        // The shared flow has replay=0 and SUSPEND overflow, so a
        // frame emitted with no collector is dropped — the test
        // would otherwise race the reader and lose the sample.
        val sampleDeferred = async { session.tilt.first() }
        runCurrent() // let `tilt.first()` register as a collector
        conn.push("1&538&2&pitch:1.25;roll:-0.5;#".toByteArray(Charsets.US_ASCII))
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
        val session = MountSession({ conn }, readerScope = backgroundScope)
        assertTrue(session.connect())

        // Queue a 538 push *before* the 517 reply. The reader must
        // route the 538 to tilt (see [tiltFlowEmits538PushFrames])
        // and still hand the 517 to the waiter — i.e. the demux is
        // correct even with push frames interleaved into the
        // request stream, and a 538 in front of a 517 does not
        // satisfy the 517 waiter.
        conn.push("1&538&2&pitch:0.0;roll:0.0;#".toByteArray(Charsets.US_ASCII))
        conn.push("1&517&2&yaw:42.0;pitch:17.5;roll:0.0;#".toByteArray(Charsets.US_ASCII))

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

    private data class GimbalPose(val yaw: Double, val pitch: Double, val roll: Double)
}
