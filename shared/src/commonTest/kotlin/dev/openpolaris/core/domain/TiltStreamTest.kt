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

    /** Queue the canned replies [MountSession.connect] expects after the
     *  284 lifecycle handshake: the 820 auth probe (`needed:0` — most
     *  production firmware doesn't require a connection password) plus
     *  the 823 hello ack. Mirrors the helper in [MountSessionReaderTest]. */
    private fun FakeConnection.queueDefaultAuthOk() {
        responses += "1&820&2&needed:0;#".toByteArray(Charsets.US_ASCII)
        responses += "1&823&2&app:openpolaris;ver:0.1.0;#".toByteArray(Charsets.US_ASCII)
    }

    @Test
    fun readerRoutes538ToTiltFlowInOrder() = runTest {
        val conn = FakeConnection()
        val s = MountSession({ conn }, readerScope = this)

        // Handshake so the reader transitions out of pre-handshake.
        conn.responses += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        // ...then the 820+823 auth handshake (see [queueDefaultAuthOk]).
        conn.queueDefaultAuthOk()
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
        // ...then the 820+823 auth handshake (see [queueDefaultAuthOk]).
        conn.queueDefaultAuthOk()
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
        // ...then the 820+823 auth handshake (see [queueDefaultAuthOk]).
        conn.queueDefaultAuthOk()
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
        // ...then the 820+823 auth handshake (see [queueDefaultAuthOk]).
        conn.queueDefaultAuthOk()
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

    // -------------------------------------------------------------------------
    // Issue #19 — MountSession._tilt SUSPEND+tryEmit contradiction
    // -------------------------------------------------------------------------
    //
    // The previous design configured _tilt with `BufferOverflow.SUSPEND` but
    // the production reader called `tryEmit` (non-suspending, returns false on
    // overflow). The contradiction made the SUSPEND policy unreachable: the
    // reader ignored the false return value, so the system was de facto lossy
    // AND the contract was incoherent. The fix switched to DROP_OLDEST so the
    // reader can stay non-suspending, and exposes `tiltDropsNoSubscriber` so
    // the loss is visible. These tests pin the new contract end-to-end through
    // `publishTiltForTest` (which uses the same `_tilt.tryEmit` path as the
    // production reader, so it exercises the real SharedFlow configuration).
    //
    // Reference: PLAN-CRITICAL-REVIEW §K, PLAN.md "Production gap", #19.

    @Test
    fun zeroSubscriberPushesIncrementTiltDropsNoSubscriber() = runTest {
        // The reader MUST NOT block on a full buffer — it owns the socket.
        // With DROP_OLDEST the SharedFlow's buffer is sized at 64, but
        // when no collector is attached every emit is delivered to the
        // floor (a no-op for downstream consumers) and is therefore a
        // drop. Push 100 frames through the reader loop (not via
        // publishTiltForTest — that seam bypasses the drop-counter
        // increment because the counter is owned by the reader, not by
        // the flow) and assert `tiltDropsNoSubscriber` reflects the count
        // exactly.
        val conn = FakeConnection()
        val s = MountSession({ conn }, readerScope = this)
        conn.responses += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        // ...then the 820+823 auth handshake (see [queueDefaultAuthOk]).
        conn.queueDefaultAuthOk()
        s.connect()
        // Reset baseline: connect() clears the counter to 0, so the
        // current value is the start-of-test reference.
        assertEquals(
            0L,
            s.tiltDropsNoSubscriber.value,
            "tiltDropsNoSubscriber must start at 0 after connect",
        )

        // Push 100 frames. Each is a different pitch value so the
        // frame body is well-formed and TiltCodec.parse returns non-null.
        repeat(100) { i ->
            conn.responses += "1&538&2&pitch:${i.toDouble() * 0.01};roll:0.0;#"
                .toByteArray(Charsets.US_ASCII)
        }
        // Drain virtual time so the reader loop processes all queued
        // bytes and runs through the 538 demultiplexer for each frame.
        advanceTimeBy(200)

        assertEquals(
            100L,
            s.tiltDropsNoSubscriber.value,
            "100 frames with zero subscribers must all count as drops",
        )
        s.disconnect()
    }

    @Test
    fun liveCollectorDoesNotIncrementTiltDropsNoSubscriber() = runTest {
        // Counterpart to the previous test: when a collector is attached
        // and the buffer is not yet full, every emit is delivered to the
        // collector and `tiltDropsNoSubscriber` must remain zero. The
        // reader's subscription-count guard means a live collector
        // prevents drops from being counted.
        //
        // NB this test uses a collector that drains 10 samples — well
        // below the 64-slot buffer — so the DROP_OLDEST eviction path is
        // NOT exercised. The buffer-eviction contract is pinned by the
        // third test in this group, `liveSlowCollectorExceedingBufferReceivesExactly64Newest`.
        val conn = FakeConnection()
        val s = MountSession({ conn }, readerScope = this)
        conn.responses += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        // ...then the 820+823 auth handshake (see [queueDefaultAuthOk]).
        conn.queueDefaultAuthOk()
        s.connect()

        val collectorJob = launch {
            s.tilt.take(10).toList()
        }
        // Let the subscription register before publishing.
        runCurrent()

        repeat(10) { i ->
            s.publishTiltForTest(
                TiltSample(
                    pitchDeg = i.toDouble(),
                    rollDeg = 0.0,
                    timestampMs = i.toLong(),
                )
            )
        }
        // Drain virtual time so the SharedFlow delivers all 10 samples
        // to the collector.
        advanceTimeBy(50)
        collectorJob.join()

        assertEquals(
            0L,
            s.tiltDropsNoSubscriber.value,
            "10 emits with a live collector must not count as drops",
        )
        s.disconnect()
    }

    @Test
    fun liveSlowCollectorExceedingBufferReceivesExactly64Newest() = runTest {
        // This is the regression that #19's acceptance criterion demands:
        // a deliberately slow *live* collector that forces the SharedFlow
        // to evict samples via its DROP_OLDEST overflow path. We publish
        // 538 frames (538 is the convention used in the issue; it's
        // also one of those "enough to overflow many times over" values
        // that makes the eviction obvious) through the reader loop while
        // a live collector is attached but never advances its take(N).
        //
        // Expected contract:
        //  - The buffer is 64 wide. Once 64 samples are queued, every
        //    further tryEmit evicts the OLDEST queued sample.
        //  - The collector therefore receives the NEWEST 64 samples, not
        //    the first 64.
        //  - `tiltDropsNoSubscriber` stays at 0 because a collector WAS
        //    attached at every emit — the no-subscriber path never fires.
        //
        // We `take(64)` and assert the pitch range is [538-64, 538),
        // confirming eviction (not silent miss) and that the surviving
        // samples are the tail end of the stream. We advance virtual time
        // generously so the reader loop's READ_RETRY_MS drains fully.
        val conn = FakeConnection()
        val s = MountSession({ conn }, readerScope = this)
        conn.responses += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        // ...then the 820+823 auth handshake (see [queueDefaultAuthOk]).
        conn.queueDefaultAuthOk()
        s.connect()

        // Subscribe first so the SharedFlow's subscriptionCount is > 0
        // when the reader starts emitting. The collector is launched in
        // a child coroutine and we runCurrent() so the subscription
        // registers before any frames are queued.
        val totalFrames = 538
        val expectedKept = 64 // SharedFlow's extraBufferCapacity
        var received: List<TiltSample> = emptyList()
        val collectorJob = launch {
            received = s.tilt.take(expectedKept).toList()
        }
        runCurrent()

        // Queue 538 frames. Each pitch is a unique index so the
        // post-eviction assertions can verify which samples survived.
        repeat(totalFrames) { i ->
            conn.responses += "1&538&2&pitch:${i.toDouble()};roll:0.0;#"
                .toByteArray(Charsets.US_ASCII)
        }
        // Drain virtual time generously. Each reader iteration costs
        // READ_RETRY_MS = 10ms, so 538 frames take at least 5.4s of
        // virtual time; we advance 10s for safety margin.
        advanceTimeBy(10_000)
        collectorJob.join()
        assertEquals(
            expectedKept,
            received.size,
            "live collector must receive exactly the 64 newest of $totalFrames pushes; " +
                "DROP_OLDEST evicts the rest silently",
        )
        // Newest 64 = indices [totalFrames-64, totalFrames). The
        // SharedFlow's eviction policy keeps the head of the queue
        // fresh by dropping the tail, so the collector sees the LAST
        // 64 frames the reader published. (Confirmed by re-reading
        // MutableSharedFlow's DROP_OLDEST semantics: on overflow the
        // oldest queued value is dropped to make room — i.e. the
        // head of the buffer — so the tail (newest) survives.)
        val firstIdx = totalFrames - expectedKept
        assertEquals(
            firstIdx.toDouble(),
            received.first().pitchDeg,
            1e-6,
            "first received sample must be frame #$firstIdx (newest-64)",
        )
        assertEquals(
            (totalFrames - 1).toDouble(),
            received.last().pitchDeg,
            1e-6,
            "last received sample must be frame #${totalFrames - 1} (newest)",
        )
        // No-subscriber counter must remain zero: a collector was
        // attached at every emit (subscriptionCount > 0 the whole time).
        assertEquals(
            0L,
            s.tiltDropsNoSubscriber.value,
            "DROP_OLDEST evictions are not counted in tiltDropsNoSubscriber",
        )
        s.disconnect()
    }
}
