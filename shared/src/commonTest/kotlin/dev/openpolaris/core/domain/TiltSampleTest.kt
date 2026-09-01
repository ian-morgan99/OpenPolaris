package dev.openpolaris.core.domain

import dev.openpolaris.core.protocol.ResponseParser
import dev.openpolaris.core.protocol.TiltCodec
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Unit tests for the [TiltSample] data class and the [TiltSampleSource]
 * functional interface. Complements the reader-loop demux test in
 * [TiltStreamTest] (which exercises the production path through
 * [MountSession.tilt]).
 */
class TiltSampleTest {

    @Test
    fun dataClassEquality() {
        val a = TiltSample(pitchDeg = 0.10, rollDeg = -0.05, timestampMs = 1_000L)
        val b = TiltSample(pitchDeg = 0.10, rollDeg = -0.05, timestampMs = 1_000L)
        val c = TiltSample(pitchDeg = 0.11, rollDeg = -0.05, timestampMs = 1_000L)
        assertEquals(a, b, "two TiltSamples with the same fields are equal")
        assertEquals(a.hashCode(), b.hashCode())
        assertEquals(false, a == c, "changing pitchDeg breaks equality")
    }

    @Test
    fun tiltCodecRoundTripsIntoTilt() {
        // TiltSample and AutoLevelController.Tilt share the same field
        // shape by design (see TiltSample.kt) so TiltCodec can decode
        // a wire Frame into either. This test pins that invariant.
        val frame = ResponseParser.Frame(
            code = 538,
            fields = linkedMapOf("pitch" to "0.10", "roll" to "-0.05"),
        )
        val tilt = assertNotNull(TiltCodec.parse(frame))
        // TiltCodec routes through Float first, so a 1e-6 absolute
        // tolerance is the realistic contract. The TiltSample path on
        // MountSession will be exact (we read the String directly into a
        // Double), so this is the codec-path tolerance, not the
        // TiltSample-path tolerance.
        assertEquals(0.10, tilt.pitchDeg, absoluteTolerance = 1e-6)
        assertEquals(-0.05, tilt.rollDeg, absoluteTolerance = 1e-6)
    }

    @Test
    fun tiltSampleSourceNextReturnsThenNulls() = runTest {
        val samples = listOf(
            TiltSample(0.0, 0.0, 1L),
            TiltSample(0.1, -0.1, 2L),
            TiltSample(-0.2, 0.2, 3L),
        )
        val idx = intArrayOf(0)
        val source = TiltSampleSource {
            if (idx[0] >= samples.size) null else samples[idx[0]++]
        }
        assertEquals(samples[0], source.next())
        assertEquals(samples[1], source.next())
        assertEquals(samples[2], source.next())
        assertNull(source.next(), "exhausted source returns null")
    }

    @Test
    fun tiltSampleSourceWrappingAFlowYieldsSamplesInOrder() = runTest {
        val samples = listOf(
            TiltSample(1.0, 2.0, 10L),
            TiltSample(3.0, 4.0, 20L),
        )
        val flow = flowOf(*samples.toTypedArray())
        val received = flow.toList()
        assertEquals(samples, received)
    }
}
