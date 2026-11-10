package dev.openpolaris.core.domain

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RateAdjustControllerTest {
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
        controller.updateContext(RateAdjustContext(connected = true, trackingConfirmed = true, exclusiveMotionAvailable = true))
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
    fun `stale versions are rejected without a second apply`() = runTest {
        val transport = FakeTransport()
        val controller = SafeRateAdjustController(transport)
        ready(controller)
        controller.apply(request(version = 2))

        val result = controller.apply(request(version = 2))

        assertIs<RateCommandResult.Rejected>(result)
        assertEquals(1, transport.applied.size)
        assertIs<RateAdjustState.Faulted>(controller.state.value)
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
