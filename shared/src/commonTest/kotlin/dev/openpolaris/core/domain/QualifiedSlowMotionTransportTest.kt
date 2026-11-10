package dev.openpolaris.core.domain

import dev.openpolaris.core.protocol.SlowMotionWireFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class QualifiedSlowMotionTransportTest {
    private fun qualified() = SlowMotionCapabilityRecord(
        firmwareIdentity = "fw:test",
        gimbalIdentity = "gimbal:test",
        sourceAppIdentity = "connect:test",
        routeQualified = true,
        axisSignsQualified = true,
        rateEnvelopeQualified = true,
        acknowledgementQualified = true,
        watchdogQualified = true,
        stopQualified = true,
        disconnectRecoveryQualified = true,
        limitsQualified = true,
    )

    @Test
    fun unqualifiedRecordRefusesBeforeTheSenderIsCalled() = runTest {
        var sends = 0
        val transport = QualifiedSlowMotionTransport(SlowMotionCapabilityRecord.Unqualified) {
            sends += 1
            SlowMotionTransportResult.Accepted
        }

        val result = transport.apply(
            SlowScheduleCommand(
                RateAxis.M1_PAN,
                SlowScheduleKind.SLOW,
                SlowMotionPoint(0.0059, key = 0, state = 1, level = 1),
            ),
        )

        assertIs<SlowMotionTransportResult.Rejected>(result)
        assertTrue(result.reason.contains("physical STOP"))
        assertEquals(0, sends)
    }

    @Test
    fun qualifiedTransportSendsSourceFrameAndRetainsItForStop() = runTest {
        val frames = mutableListOf<SlowMotionWireFrame>()
        val transport = QualifiedSlowMotionTransport(qualified()) {
            frames += it
            SlowMotionTransportResult.Accepted
        }
        val point = SlowMotionPoint(0.0059, key = 0, state = 1, level = 1)

        assertEquals(
            SlowMotionTransportResult.Accepted,
            transport.apply(SlowScheduleCommand(RateAxis.M1_PAN, SlowScheduleKind.SLOW, point)),
        )
        assertEquals("key:0;state:1;level:1;", frames.single().payload)

        assertEquals(MotionStopResult.Stopped, transport.stop(MotionStopReason.EMERGENCY))
        assertEquals(2, frames.size)
        assertEquals(532, frames[1].code)
        assertEquals("key:0;state:0;level:1;", frames[1].payload)
    }

    @Test
    fun idleAxisStopIsAnAcknowledgeFreeNoOp() = runTest {
        val frames = mutableListOf<SlowMotionWireFrame>()
        val transport = QualifiedSlowMotionTransport(qualified()) {
            frames += it
            SlowMotionTransportResult.Accepted
        }

        assertEquals(
            SlowMotionTransportResult.Accepted,
            transport.apply(SlowScheduleCommand(RateAxis.M2_TILT, SlowScheduleKind.STOP)),
        )
        assertEquals(0, frames.size)
    }

    @Test
    fun cancellationFromSenderIsNotConvertedIntoAnUnknownResult() = runTest {
        val transport = QualifiedSlowMotionTransport(qualified()) {
            throw CancellationException("cancelled")
        }

        assertFailsWith<CancellationException> {
            transport.apply(
                SlowScheduleCommand(
                    RateAxis.M1_PAN,
                    SlowScheduleKind.SLOW,
                    SlowMotionPoint(0.0059, key = 0, state = 1, level = 1),
                ),
            )
        }
    }

    @Test
    fun unknownStopDoesNotClearRetainedPointOrClaimStopped() = runTest {
        var calls = 0
        val transport = QualifiedSlowMotionTransport(qualified()) {
            calls += 1
            if (calls == 1) SlowMotionTransportResult.Accepted
            else SlowMotionTransportResult.Unknown("ack lost")
        }
        val point = SlowMotionPoint(0.0059, key = 0, state = 1, level = 1)
        transport.apply(SlowScheduleCommand(RateAxis.M3_ROTATION, SlowScheduleKind.SLOW, point))

        val first = assertIs<MotionStopResult.Unknown>(
            transport.stop(MotionStopReason.RELEASE),
        )
        assertEquals("ack lost", first.reason)
        assertEquals(2, calls)
        val second = assertIs<MotionStopResult.Unknown>(
            transport.stop(MotionStopReason.EMERGENCY),
        )
        assertEquals("ack lost", second.reason)
        assertEquals(3, calls)
    }
}
