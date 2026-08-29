package dev.openpolaris.core.domain

import dev.openpolaris.core.protocol.Codes
import dev.openpolaris.core.protocol.ResponseParser
import dev.openpolaris.core.protocol.TiltCodec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Issue #6 acceptance: 538 (SET_TILT_STATE) push frames surface on
 * [MountSession.tilt] as a [Flow] of [TiltSample]s, in arrival order, with
 * the demultiplexer sending every other code to the generic frames /
 * request-waiter path. This file pins that contract end-to-end through the
 * real [MountSession.runReaderLoop], not via [MountSession.publishTiltForTest]
 * (which exists as a unit-level seam).
 *
 * The 538 stream must not lose samples on the way to a [TiltSampleSource]
 * consumer — the previous design used a [kotlinx.coroutines.flow.StateFlow]
 * for the frames surface, which conflated intermediate samples when the
 * consumer (e.g. [AutoLevelController.runAndAwait]) couldn't keep up with
 * the ~10 Hz push rate. The fix: demux by frame-id in the reader loop and
 * publish 538 to a buffered [kotlinx.coroutines.flow.MutableSharedFlow]
 * that back-pressures rather than drops.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TiltStreamTest {

    /** Same minimal [Connection] double as [MountSessionReaderTest]. Kept
     *  private per file to avoid a shared TestSupport dependency while
     *  the suite is still being seeded (PLAN-CRITICAL-REVIEW §K). */
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
    fun readerRoutes538ToTiltFlowInOrder() = runTest {
        val conn = FakeConnection()
        val s = MountSession({ conn }, readerScope = this)

        // Handshake so the reader transitions out of pre-handshake.
        conn.responses += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        s.connect()

        // Five 538 pushes in order. Reader publishes each on `tilt` and
        // must NOT publish them on `frames`.
        conn.responses += "1&538&2&pitch:0.10;roll:-0.05;#".toByteArray(Charsets.US_ASCII)
        conn.responses += "1&538&2&pitch:0.20;roll:-0.10;#".toByteArray(Charsets.US_ASCII)
        conn.responses += "1&538&2&pitch:0.30;roll:-0.15;#".toByteArray(Charsets.US_ASCII)
        conn.responses += "1&538&2&pitch:0.40;roll:-0.20;#".toByteArray(Charsets.US_ASCII)
        conn.responses += "1&538&2&pitch:0.50;roll:-0.25;#".toByteArray(Charsets.US_ASCII)

        val samples = s.tilt.take(5).toList()
        // Reader waits READ_RETRY_MS between read attempts under virtual
        // time — flush so the reader coroutine processes the full burst
        // and the SharedFlow backpressure releases five times.
        advanceTimeBy(50)
        assertEquals(5, samples.size, "all five 538 pushes must be emitted on tilt")
        assertEquals(0.10, samples[0].pitchDeg, 1e-6)
        assertEquals(-0.05, samples[0].rollDeg, 1e-6)
        assertEquals(0.50, samples[4].pitchDeg, 1e-6)
        assertEquals(-0.25, samples[4].rollDeg, 1e-6)

        // And the generic frames StateFlow must NOT carry 538 — that's
        // the whole point of the demux.
        assertTrue(
            s.frames.value?.code != Codes.SET_TILT_STATE,
            "538 must not leak into the generic frames flow",
        )
        s.disconnect()
    }

    @Test
    fun readerDoesNotLeak538IntoRequestWaiter() = runTest {
        // 537 request in flight while a 538 push is in the response
        // queue behind it. The 537 must complete the waiter; the 538
        // must surface ONLY on `tilt` and never leak into the waiter.
        //
        // We subscribe to `tilt` BEFORE queueing the 538 so the
        // collector is in place when the reader emits. The flow is
        // backed by a replay=0 SharedFlow, so a late subscriber
        // would miss the emit and the test would hang.
        val conn = FakeConnection()
        val s = MountSession({ conn }, readerScope = this)
        conn.responses += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        s.connect()

        // Subscribe FIRST.
        val tiltJob = launch { tiltCollector = s.tilt.first() }
        runCurrent()

        // Now queue the 537 (request) and the 538 (push), then issue
        // the request. The reader's first read completes the 537
        // waiter; its second read emits the 538 to the already-
        // subscribed collector.
        conn.responses += "1&537&2&pitch:0.42;roll:-0.13;#".toByteArray(Charsets.US_ASCII)
        conn.responses += "1&538&2&pitch:9.9;roll:9.9;#".toByteArray(Charsets.US_ASCII)
        val r = s.request(Codes.GET_TILT_STATE) { f ->
            f.float("pitch") to f.float("roll")
        }
        val ok = assertIs<MountSession.CmdResult.Ok<Pair<Float?, Float?>>>(r)
        assertEquals(0.42f, ok.value.first)
        assertEquals(-0.13f, ok.value.second)

        // Drain virtual time so the reader's delay between reads
        // elapses and the 538 reaches the collector.
        advanceTimeBy(50)
        tiltJob.join()
        assertNotNull(tiltCollector)
        assertEquals(9.9, tiltCollector!!.pitchDeg, 1e-6)
        assertEquals(9.9, tiltCollector!!.rollDeg, 1e-6)
        s.disconnect()
    }

    private var tiltCollector: TiltSample? = null

    @Test
    fun readerDropsMalformed538Silently() = runTest {
        // 538 with a missing pitch field — TiltCodec.parse returns null.
        // The reader must not crash and the next 538 must still be
        // delivered.
        val conn = FakeConnection()
        val s = MountSession({ conn }, readerScope = this)
        conn.responses += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        s.connect()

        // Malformed (no pitch) and then well-formed.
        conn.responses += "1&538&2&roll:-0.05;#".toByteArray(Charsets.US_ASCII)
        conn.responses += "1&538&2&pitch:0.77;roll:0.88;#".toByteArray(Charsets.US_ASCII)

        // Read one well-formed sample. The malformed one is silently
        // dropped by the reader, so tilt emits only the second one.
        val sample = s.tilt.first()
        // Flush virtual time so the reader reads the second 538.
        advanceTimeBy(50)
        assertEquals(0.77, sample.pitchDeg, 1e-6)
        assertEquals(0.88, sample.rollDeg, 1e-6)
        s.disconnect()
    }

    @Test
    fun tiltSampleSourceExposesReaderPushes() = runTest {
        // The TiltSampleSource adapter (sub-task 4) must be able to
        // subscribe to session.tilt and surface a TiltSample per push.
        // For this test we hand-build the adapter inline so the test
        // doesn't depend on a future helper class location.
        //
        // The flow is backed by a replay=0 SharedFlow, so the
        // collector must be subscribed before the reader emits. We
        // start a child coroutine for each .next() and runCurrent()
        // to subscribe before draining virtual time.
        val conn = FakeConnection()
        val s = MountSession({ conn }, readerScope = this)
        conn.responses += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        s.connect()

        val source = TiltSampleSource { s.tilt.first() }
        var first: TiltSample? = null
        var second: TiltSample? = null
        val firstJob = launch { first = source.next() }
        runCurrent()
        // Queue the 538 pushes AFTER the collector is subscribed, so
        // the reader doesn't emit into a vacant subscriber.
        conn.responses += "1&538&2&pitch:1.25;roll:-2.50;#".toByteArray(Charsets.US_ASCII)
        advanceTimeBy(50)
        firstJob.join()
        val secondJob = launch { second = source.next() }
        runCurrent()
        conn.responses += "1&538&2&pitch:1.50;roll:-2.75;#".toByteArray(Charsets.US_ASCII)
        advanceTimeBy(50)
        secondJob.join()
        assertNotNull(first)
        assertNotNull(second)
        assertEquals(1.25, first!!.pitchDeg, 1e-6)
        assertEquals(1.50, second!!.pitchDeg, 1e-6)
        s.disconnect()
    }
}
