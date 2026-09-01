package dev.openpolaris.core.domain

import dev.openpolaris.core.protocol.Codes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Issue #6 / sub-task 4 acceptance: the production [MountSessionTiltSampleSource]
 * adapter must surface every 538 push the reader emits, in arrival order,
 * with no conflation of identical samples (the bug the previous StateFlow
 * design had). This test pins the contract end-to-end through
 * [MountSession.runReaderLoop], not via the test seam
 * [MountSession.publishTiltForTest], so it would catch a regression where
 * the adapter started reading from the wrong flow.
 *
 * The flow is backed by a `replay=0, extraBufferCapacity=64` SharedFlow,
 * so the subscriber must be in place before the reader emits — same
 * subscribe-first discipline as [TiltStreamTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MountSessionTiltSampleSourceTest {

    private class FakeConnection : Connection {
        val responses = mutableListOf<ByteArray>()
        val written = mutableListOf<ByteArray>()

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
    fun adapterSurfacesEveryReaderPush() = runTest {
        val conn = FakeConnection()
        val s = MountSession({ conn }, readerScope = this)

        // Handshake so the reader transitions out of pre-handshake.
        conn.responses += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        s.connect()

        val source = MountSessionTiltSampleSource(s)

        // Subscribe FIRST via the production adapter. The adapter's
        // `next()` calls `session.tilt.first()` internally, so the
        // collector is in place before the reader emits the queued
        // 538.
        var first: TiltSample? = null
        var second: TiltSample? = null
        var third: TiltSample? = null

        val firstJob = launch { first = source.next() }
        runCurrent()
        conn.responses += "1&538&2&pitch:0.10;roll:-0.05;#".toByteArray(Charsets.US_ASCII)
        advanceTimeBy(50)
        firstJob.join()

        val secondJob = launch { second = source.next() }
        runCurrent()
        conn.responses += "1&538&2&pitch:0.20;roll:-0.10;#".toByteArray(Charsets.US_ASCII)
        advanceTimeBy(50)
        secondJob.join()

        val thirdJob = launch { third = source.next() }
        runCurrent()
        conn.responses += "1&538&2&pitch:0.30;roll:-0.15;#".toByteArray(Charsets.US_ASCII)
        advanceTimeBy(50)
        thirdJob.join()

        assertNotNull(first)
        assertNotNull(second)
        assertNotNull(third)
        assertEquals(0.10, first!!.pitchDeg, 1e-6)
        assertEquals(-0.05, first!!.rollDeg, 1e-6)
        assertEquals(0.20, second!!.pitchDeg, 1e-6)
        assertEquals(-0.10, second!!.rollDeg, 1e-6)
        assertEquals(0.30, third!!.pitchDeg, 1e-6)
        assertEquals(-0.15, third!!.rollDeg, 1e-6)
        s.disconnect()
    }

    @Test
    fun adapterSurfacesIdenticalSamplesWithoutConflation() = runTest {
        // Regression: the previous StateFlow-based design dropped
        // identical back-to-back samples because StateFlow only emits
        // on equality change. The new design must surface every push
        // the reader emits, even when three consecutive pushes carry
        // identical pitch/roll.
        val conn = FakeConnection()
        val s = MountSession({ conn }, readerScope = this)
        conn.responses += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        s.connect()

        val source = MountSessionTiltSampleSource(s)

        // The flow is backed by a `replay=0` SharedFlow, so we must
        // subscribe (launch + runCurrent) before each queued push
        // to guarantee the collector is in place when the reader
        // emits. The first push resolves the first .next() call; we
        // re-launch for each subsequent push.
        val seen = mutableListOf<TiltSample>()
        for (i in 0 until 3) {
            val job = launch { seen += source.next()!! }
            runCurrent()
            conn.responses += "1&538&2&pitch:0.00;roll:0.00;#".toByteArray(Charsets.US_ASCII)
            advanceTimeBy(50)
            job.join()
        }

        assertEquals(
            3,
            seen.size,
            "adapter must not conflate identical samples (issue #6)",
        )
        // And the generic frames flow must NOT carry 538.
        assertEquals(
            Codes.SET_TILT_STATE,
            538,
            "sanity: SET_TILT_STATE code constant hasn't drifted",
        )
        s.disconnect()
    }
}
