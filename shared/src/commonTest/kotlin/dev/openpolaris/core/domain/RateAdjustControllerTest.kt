package dev.openpolaris.core.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RateAdjustControllerTest {
    private class CancellingTransport : RateAdjustTransport {
        override suspend fun apply(request: RateAdjustRequest): RateCommandResult {
            throw CancellationException("cancelled apply")
        }

        override suspend fun stop(): RateCommandResult {
            throw CancellationException("cancelled stop")
        }
    }

    private class FakeTransport : RateAdjustTransport {
        val applied = mutableListOf<RateAdjustRequest>()
        var stops = 0
        var nextApply: RateCommandResult = RateCommandResult.Accepted()
        var nextStop: RateCommandResult = RateCommandResult.Accepted()

        override suspend fun apply(request: RateAdjustRequest): RateCommandResult {
            applied += request
            return nextApply
        }

        override suspend fun stop(): RateCommandResult {
            stops += 1
            return nextStop
        }
    }

    private val identity = RateCapabilityIdentity("fw-test", "gimbal-test", "slow-v1")
    private val capability = RateAdjustCapability(
        identity = identity,
        qualified = true,
        units = RateUnits.DEGREES_PER_SECOND,
        bounds = RateAxis.entries.associateWith { RateAxisBounds(-1.0, 1.0) },
    )

    private fun request(version: Long = 1L) = RateAdjustRequest(
        baseProfile = TrackingRate.SIDEREAL,
        m1Pan = 0.1,
        m2Tilt = -0.2,
        m3Rotation = 0.0,
        version = version,
        capability = identity,
    )

    private fun ready(controller: SafeRateAdjustController) {
        controller.setCapability(capability)
        controller.updateContext(
            RateAdjustContext(
                connected = true,
                trackingConfirmed = true,
                halfSpeedEnabled = false,
                exclusiveMotionAvailable = true,
                solutionAgeMs = 0L,
                solutionConfidence = 0.9,
                solutionRotationDegrees = 0.0,
            ),
        )
    }

    @Test
    fun `controller starts unavailable and only becomes ready with qualified context`() {
        val transport = FakeTransport()
        val controller = SafeRateAdjustController(transport)

        assertIs<RateAdjustState.Unavailable>(controller.state.value)
        controller.setCapability(capability.copy(qualified = false))
        assertIs<RateAdjustState.Unavailable>(controller.state.value)
        controller.setCapability(capability)
        assertIs<RateAdjustState.Unavailable>(controller.state.value)
        controller.updateContext(
            RateAdjustContext(
                connected = true,
                trackingConfirmed = true,
                exclusiveMotionAvailable = true,
                solutionAgeMs = 0L,
                solutionConfidence = 0.9,
                solutionRotationDegrees = 0.0,
            ),
        )
        assertIs<RateAdjustState.Ready>(controller.state.value)
    }

    @Test
    fun `accepted request becomes active and preserves physical targets`() = runTest {
        val transport = FakeTransport()
        val controller = SafeRateAdjustController(transport)
        ready(controller)

        assertEquals(RateCommandResult.Accepted(), controller.apply(request()))
        assertEquals(listOf(request()), transport.applied)
        assertEquals(RateAdjustState.Active(request()), controller.state.value)
    }

    @Test
    fun `newer accepted request atomically supersedes the active target`() = runTest {
        val transport = FakeTransport()
        val controller = SafeRateAdjustController(transport)
        ready(controller)
        controller.apply(request(version = 1))

        assertEquals(RateCommandResult.Accepted(), controller.apply(request(version = 2)))
        assertEquals(listOf(request(version = 1), request(version = 2)), transport.applied)
        assertEquals(RateAdjustState.Active(request(version = 2)), controller.state.value)
        assertEquals(0, transport.stops)
    }

    @Test
    fun `delayed stale request cannot fault or replace the active target`() = runTest {
        val transport = FakeTransport()
        val controller = SafeRateAdjustController(transport)
        ready(controller)
        controller.apply(request(version = 2))

        val result = controller.apply(request(version = 1))

        assertEquals(RateCommandResult.Rejected("request version is stale"), result)
        assertEquals(listOf(request(version = 2)), transport.applied)
        assertEquals(RateAdjustState.Active(request(version = 2)), controller.state.value)
    }

    @Test
    fun `delayed stale invalid request cannot fault or replace the active target`() = runTest {
        val transport = FakeTransport()
        val controller = SafeRateAdjustController(transport)
        ready(controller)
        controller.apply(request(version = 2))

        val result = controller.apply(request(version = 1).copy(m1Pan = 2.0))

        assertEquals(RateCommandResult.Rejected("request version is stale"), result)
        assertEquals(listOf(request(version = 2)), transport.applied)
        assertEquals(RateAdjustState.Active(request(version = 2)), controller.state.value)
    }

    @Test
    fun `unknown result requires explicit stop before a newer request`() = runTest {
        val transport = FakeTransport().also {
            it.nextApply = RateCommandResult.Unknown("acknowledgement lost")
        }
        val controller = SafeRateAdjustController(transport)
        ready(controller)
        controller.apply(request(version = 1))

        assertIs<RateCommandResult.Rejected>(controller.apply(request(version = 2)))
        assertEquals(1, transport.applied.size)
        assertEquals(RateCommandResult.Accepted(), controller.stopCorrection())

        transport.nextApply = RateCommandResult.Accepted()
        assertEquals(RateCommandResult.Accepted(), controller.apply(request(version = 2)))
        assertEquals(2, transport.applied.size)
        assertEquals(RateAdjustState.Active(request(version = 2)), controller.state.value)
    }

    @Test
    fun `rejected replacement requires stop because the prior target may remain active`() = runTest {
        val transport = FakeTransport()
        val controller = SafeRateAdjustController(transport)
        ready(controller)
        controller.apply(request(version = 1))
        transport.nextApply = RateCommandResult.Rejected("replacement refused")

        assertIs<RateCommandResult.Rejected>(controller.apply(request(version = 2)))
        assertIs<RateAdjustState.Faulted>(controller.state.value)
        assertIs<RateCommandResult.Rejected>(controller.apply(request(version = 3)))
        assertEquals(2, transport.applied.size)

        assertEquals(RateCommandResult.Accepted(), controller.stopCorrection())
        transport.nextApply = RateCommandResult.Accepted()
        assertEquals(RateCommandResult.Accepted(), controller.apply(request(version = 3)))
        assertEquals(3, transport.applied.size)
    }

    @Test
    fun `invalid target is rejected before transport`() = runTest {
        val transport = FakeTransport()
        val controller = SafeRateAdjustController(transport)
        ready(controller)

        val invalid = request().copy(m1Pan = 2.0)
        val result = controller.apply(invalid)

        assertIs<RateCommandResult.Rejected>(result)
        assertTrue(transport.applied.isEmpty())
        assertIs<RateAdjustState.Faulted>(controller.state.value)
    }

    @Test
    fun `invalid newer replacement preserves the last possible physical target`() = runTest {
        val transport = FakeTransport()
        val controller = SafeRateAdjustController(transport)
        ready(controller)
        controller.apply(request(version = 1))

        val invalid = request(version = 2).copy(m1Pan = 2.0)
        val result = controller.apply(invalid)

        assertEquals(RateCommandResult.Rejected("M1_PAN target 2.0 is outside the qualified range"), result)
        assertEquals(listOf(request(version = 1)), transport.applied)
        assertEquals(
            RateAdjustState.Faulted(
                reason = "M1_PAN target 2.0 is outside the qualified range",
                request = request(version = 1),
            ),
            controller.state.value,
        )
        assertIs<RateCommandResult.Rejected>(controller.apply(request(version = 3)))
        assertEquals(listOf(request(version = 1)), transport.applied)

        assertEquals(RateCommandResult.Accepted(), controller.stopCorrection())
        assertEquals(1, transport.stops)
    }

    @Test
    fun `half speed blocks correction and does not send`() = runTest {
        val transport = FakeTransport()
        val controller = SafeRateAdjustController(transport)
        controller.setCapability(capability)
        controller.updateContext(
            RateAdjustContext(connected = true, trackingConfirmed = true, halfSpeedEnabled = true, exclusiveMotionAvailable = true),
        )

        val result = controller.apply(request())

        assertIs<RateCommandResult.Rejected>(result)
        assertTrue(transport.applied.isEmpty())
        assertIs<RateAdjustState.Unavailable>(controller.state.value)
    }

    @Test
    fun `stale solve blocks correction before transport`() = runTest {
        val transport = FakeTransport()
        val controller = SafeRateAdjustController(transport)
        controller.setCapability(capability)
        controller.updateContext(
            RateAdjustContext(
                connected = true,
                trackingConfirmed = true,
                exclusiveMotionAvailable = true,
                solutionAgeMs = 5_001L,
                solutionConfidence = 0.9,
                solutionRotationDegrees = 0.0,
            ),
        )

        val result = controller.apply(request())

        assertIs<RateCommandResult.Rejected>(result)
        assertTrue(result.reason.contains("stale"))
        assertTrue(transport.applied.isEmpty())
    }

    @Test
    fun `low confidence solve blocks correction before transport`() = runTest {
        val transport = FakeTransport()
        val controller = SafeRateAdjustController(transport)
        controller.setCapability(capability)
        controller.updateContext(
            RateAdjustContext(
                connected = true,
                trackingConfirmed = true,
                exclusiveMotionAvailable = true,
                solutionAgeMs = 0L,
                solutionConfidence = 0.59,
                solutionRotationDegrees = 0.0,
            ),
        )

        val result = controller.apply(request())

        assertIs<RateCommandResult.Rejected>(result)
        assertTrue(result.reason.contains("confidence"))
        assertTrue(transport.applied.isEmpty())
    }

    @Test
    fun `rotated solve blocks correction before transport`() = runTest {
        val transport = FakeTransport()
        val controller = SafeRateAdjustController(transport)
        controller.setCapability(capability)
        controller.updateContext(
            RateAdjustContext(
                connected = true,
                trackingConfirmed = true,
                exclusiveMotionAvailable = true,
                solutionAgeMs = 0L,
                solutionConfidence = 0.9,
                solutionRotationDegrees = 5.1,
            ),
        )

        val result = controller.apply(request())

        assertIs<RateCommandResult.Rejected>(result)
        assertTrue(result.reason.contains("rotation"))
        assertTrue(transport.applied.isEmpty())
    }

    @Test
    fun `stale versions are rejected without a second apply`() = runTest {
        val transport = FakeTransport()
        val controller = SafeRateAdjustController(transport)
        ready(controller)
        controller.apply(request(version = 2))

        val result = controller.apply(request(version = 2))

        assertIs<RateCommandResult.Rejected>(result)
        assertEquals(1, transport.applied.size)
        assertIs<RateAdjustState.Active>(controller.state.value)
    }

    @Test
    fun `stop has priority and returns to ready`() = runTest {
        val transport = FakeTransport()
        val controller = SafeRateAdjustController(transport)
        ready(controller)
        controller.apply(request())

        assertEquals(RateCommandResult.Accepted(), controller.stopCorrection())
        assertEquals(1, transport.stops)
        assertIs<RateAdjustState.Ready>(controller.state.value)
    }

    @Test
    fun `disconnect stops active correction and disables the capability`() = runTest {
        val transport = FakeTransport()
        val controller = SafeRateAdjustController(transport)
        ready(controller)
        controller.apply(request())

        controller.onDisconnected()

        assertEquals(1, transport.stops)
        val state = assertIs<RateAdjustState.Unavailable>(controller.state.value)
        assertTrue(state.reason.contains("Connect"))
    }

    @Test
    fun `unknown transport result is stale and is never retried`() = runTest {
        val transport = FakeTransport().also {
            it.nextApply = RateCommandResult.Unknown("acknowledgement lost")
        }
        val controller = SafeRateAdjustController(transport)
        ready(controller)

        val result = controller.apply(request())

        assertEquals(RateCommandResult.Unknown("acknowledgement lost"), result)
        assertEquals(1, transport.applied.size)
        assertIs<RateAdjustState.Stale>(controller.state.value)
    }
    @Test
    fun unknownTransportResultIsStaleAndNeverRetried() = runTest {
        val transport = FakeTransport().also {
            it.nextApply = RateCommandResult.Unknown("acknowledgement lost")
        }
        val controller = SafeRateAdjustController(transport)
        ready(controller)

        val result = controller.apply(request())

        assertEquals(RateCommandResult.Unknown("acknowledgement lost"), result)
        assertEquals(1, transport.applied.size)
        assertIs<RateAdjustState.Stale>(controller.state.value)
        assertIs<RateCommandResult.Rejected>(controller.apply(request(version = 2)))
        assertEquals(1, transport.applied.size)
    }

    @Test
    fun cancellationFromApplyIsNotConvertedToUnknown() = runTest {
        val controller = SafeRateAdjustController(CancellingTransport())
        ready(controller)

        assertFailsWith<CancellationException> {
            controller.apply(request())
        }
    }

    @Test
    fun cancellationFromStopIsNotConvertedToUnknown() = runTest {
        val transport = object : RateAdjustTransport {
            override suspend fun apply(request: RateAdjustRequest): RateCommandResult =
                RateCommandResult.Accepted()

            override suspend fun stop(): RateCommandResult {
                throw CancellationException("cancelled stop")
            }
        }
        val controller = SafeRateAdjustController(transport)
        ready(controller)
        controller.apply(request())

        assertFailsWith<CancellationException> {
            controller.stopCorrection()
        }
    }

    @Test
    fun contextLossLeavesActiveCorrectionStaleUntilExplicitStop() = runTest {
        val transport = FakeTransport()
        val controller = SafeRateAdjustController(transport)
        ready(controller)
        controller.apply(request())

        controller.updateContext(RateAdjustContext())
        assertIs<RateAdjustState.Stale>(controller.state.value)
        assertEquals(0, transport.stops)

        assertIs<RateCommandResult.Accepted>(controller.stopCorrection())
        assertEquals(1, transport.stops)
        assertIs<RateAdjustState.Unavailable>(controller.state.value)
    }

}
