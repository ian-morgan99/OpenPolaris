package dev.openpolaris.core.domain

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SlowMotionControllerTest {
    private class FakeTransport : SlowMotionTransport {
        val applied = mutableListOf<SlowScheduleCommand>()
        val stops = mutableListOf<MotionStopReason>()
        var nextApply: SlowMotionTransportResult = SlowMotionTransportResult.Accepted
        var nextStop: MotionStopResult = MotionStopResult.Stopped

        override suspend fun apply(command: SlowScheduleCommand): SlowMotionTransportResult {
            applied += command
            return nextApply
        }

        override suspend fun stop(reason: MotionStopReason): MotionStopResult {
            stops += reason
            return nextStop
        }
    }

    private fun targets(rate: Double = 0.01): Map<RateAxis, SlowMotionTarget> =
        RateAxis.entries.associateWith { axis ->
            if (axis == RateAxis.M1_PAN) SlowMotionTarget(rate, hold = true)
            else SlowMotionTarget(0.0, hold = false)
        }

    private suspend fun ready(
        transport: FakeTransport = FakeTransport(),
        leaseWindowMs: Long = 400L,
    ): Triple<SlowMotionController, SlowMotionLease, FakeTransport> {
        val controller = SlowMotionController(transport, leaseWindowMs = leaseWindowMs)
        val lease = assertIs<SlowMotionAcquireResult.Granted>(controller.acquire()).lease
        assertEquals(SlowMotionActionResult.Started, controller.start(lease, targets(), 0L))
        return Triple(controller, lease, transport)
    }

    @Test
    fun onlyOneLeaseOwnsTheSLOWController() = runTest {
        val transport = FakeTransport()
        val controller = SlowMotionController(transport)
        val first = assertIs<SlowMotionAcquireResult.Granted>(controller.acquire()).lease
        val second = assertIs<SlowMotionAcquireResult.Busy>(controller.acquire())
        assertEquals(first, second.lease)
        assertEquals(SlowMotionActionResult.Started, controller.start(first, targets(), 0L))
        assertIs<SlowMotionState.Holding>(controller.state.value)
    }

    @Test
    fun telemetryRecordsTheLastCommandAndItsPositiveAcknowledgement() = runTest {
        val (controller, lease, _) = ready()

        assertEquals(SlowMotionActionResult.Applied(3), controller.tick(lease, 75L))
        val telemetry = controller.telemetry.value
        assertTrue(telemetry.lastCommand != null)
        assertEquals(75L, telemetry.lastCommandAtMs)
        assertEquals(75L, telemetry.lastPositiveAcknowledgementAtMs)
        assertEquals(SlowMotionTransportResult.Accepted, telemetry.lastOutcome)
    }

    @Test
    fun telemetryKeepsAnUnknownOutcomeDistinctFromTheLastPositiveAcknowledgement() = runTest {
        val transport = FakeTransport().also {
            it.nextApply = SlowMotionTransportResult.Unknown("ack lost")
        }
        val controller = SlowMotionController(transport)
        val lease = assertIs<SlowMotionAcquireResult.Granted>(controller.acquire()).lease
        assertEquals(SlowMotionActionResult.Started, controller.start(lease, targets(), 0L))

        assertIs<SlowMotionActionResult.Unknown>(controller.tick(lease, 75L))
        val telemetry = controller.telemetry.value
        assertEquals(75L, telemetry.lastCommandAtMs)
        assertNull(telemetry.lastPositiveAcknowledgementAtMs)
        assertEquals(SlowMotionTransportResult.Unknown("ack lost"), telemetry.lastOutcome)
    }

    @Test
    fun sharedSupervisorBlocksSlowMotionBehindAnotherMotionOwner() = runTest {
        val transport = FakeTransport()
        val stopTransport = object : MotionStopTransport {
            override suspend fun stop(owner: MotionOwner, reason: MotionStopReason): MotionStopResult =
                MotionStopResult.Stopped
        }
        val supervisor = MotionSupervisor(stopTransport)
        val controller = SlowMotionController(
            transport = transport,
            motionSupervisor = supervisor,
        )
        val gotoLease = assertIs<MotionAcquireResult.Granted>(
            supervisor.acquire(MotionOwner.GOTO),
        ).lease

        val blocked = assertIs<SlowMotionAcquireResult.Busy>(controller.acquire())
        assertEquals(MotionOwner.GOTO, blocked.owner)
        assertEquals(null, blocked.lease)

        assertEquals(MotionStopResult.Stopped, supervisor.stop(gotoLease))
        assertIs<SlowMotionAcquireResult.Granted>(controller.acquire())
    }

    @Test
    fun sharedSupervisorReceivesSlowStopAndReturnsToIdle() = runTest {
        val transport = FakeTransport()
        val stopTransport = object : MotionStopTransport {
            val stops = mutableListOf<Pair<MotionOwner, MotionStopReason>>()
            override suspend fun stop(owner: MotionOwner, reason: MotionStopReason): MotionStopResult {
                stops += owner to reason
                return MotionStopResult.Stopped
            }
        }
        val supervisor = MotionSupervisor(stopTransport)
        val controller = SlowMotionController(
            transport = transport,
            motionSupervisor = supervisor,
        )
        val lease = assertIs<SlowMotionAcquireResult.Granted>(controller.acquire()).lease
        assertEquals(SlowMotionActionResult.Started, controller.start(lease, targets(), 0L))

        assertEquals(SlowMotionActionResult.Stopped, controller.emergencyStop(lease))
        assertEquals(MotionState.Idle, supervisor.state.value)
        assertEquals(listOf(MotionOwner.SLOW_MOVE to MotionStopReason.EMERGENCY), stopTransport.stops)
        assertEquals(emptyList(), transport.stops)
    }

    @Test
    fun schedulerTicksDoNotRenewTheDeadManLease() = runTest {
        val (controller, lease, transport) = ready()
        assertEquals(SlowMotionActionResult.Applied(3), controller.tick(lease, 75L))
        assertEquals(SlowMotionActionResult.NotDue, controller.tick(lease, 100L))
        assertEquals(SlowMotionActionResult.Stopped, controller.tick(lease, 400L))
        assertEquals(listOf(MotionStopReason.LEASE_EXPIRED), transport.stops)
        assertEquals(SlowMotionState.Idle, controller.state.value)
    }

    @Test
    fun explicitRenewalKeepsTheLeaseAliveUntilTheNextWindow() = runTest {
        val (controller, lease, transport) = ready()
        assertEquals(SlowMotionActionResult.Renewed, controller.renew(lease, 300L))
        assertTrue(controller.tick(lease, 699L) is SlowMotionActionResult.Applied)
        assertEquals(SlowMotionActionResult.Stopped, controller.tick(lease, 700L))
        assertEquals(listOf(MotionStopReason.LEASE_EXPIRED), transport.stops)
    }

    @Test
    fun releaseUsesTheExplicitStopPath() = runTest {
        val (controller, lease, transport) = ready()
        assertEquals(SlowMotionActionResult.Stopped, controller.release(lease))
        assertEquals(listOf(MotionStopReason.RELEASE), transport.stops)
        assertEquals(SlowMotionState.Idle, controller.state.value)
    }

    @Test
    fun modeChangeReleasePreservesTheExplicitStopReason() = runTest {
        val (controller, lease, transport) = ready()
        assertEquals(
            SlowMotionActionResult.Stopped,
            controller.release(lease, MotionStopReason.MODE_CHANGE),
        )
        assertEquals(listOf(MotionStopReason.MODE_CHANGE), transport.stops)
    }

    @Test
    fun unknownCommandIsFaultedAndNeverRetried() = runTest {
        val transport = FakeTransport().also {
            it.nextApply = SlowMotionTransportResult.Unknown("ack lost")
        }
        val controller = SlowMotionController(transport)
        val lease = assertIs<SlowMotionAcquireResult.Granted>(controller.acquire()).lease
        assertEquals(SlowMotionActionResult.Started, controller.start(lease, targets(), 0L))

        val first = assertIs<SlowMotionActionResult.Unknown>(controller.tick(lease, 75L))
        assertEquals("ack lost", first.reason)
        assertEquals(1, transport.applied.size)
        assertIs<SlowMotionState.Faulted>(controller.state.value)
        assertEquals(SlowMotionActionResult.Rejected, controller.tick(lease, 100L))
        assertEquals(1, transport.applied.size)
    }

    @Test
    fun stopFailureBlocksUntilExplicitRecovery() = runTest {
        val transport = FakeTransport().also {
            it.nextStop = MotionStopResult.Unknown("stop ack lost")
        }
        val (controller, lease, _) = ready(transport)
        assertIs<SlowMotionActionResult.Unknown>(controller.release(lease))
        assertIs<SlowMotionState.Faulted>(controller.state.value)
        assertIs<SlowMotionAcquireResult.Blocked>(controller.acquire())
        controller.recoverAfterReconnect()
        assertIs<SlowMotionAcquireResult.Granted>(controller.acquire())
    }

    @Test
    fun invalidPlanFailsBeforeTransport() = runTest {
        val transport = FakeTransport()
        val controller = SlowMotionController(transport)
        val lease = assertIs<SlowMotionAcquireResult.Granted>(controller.acquire()).lease
        val invalid = targets(rate = 100.0)
        assertEquals(SlowMotionActionResult.Rejected, controller.start(lease, invalid, 0L))
        assertIs<SlowMotionState.Armed>(controller.state.value)
        assertTrue(transport.applied.isEmpty())
        assertTrue(transport.stops.isEmpty())
    }

    @Test
    fun emergencyStopHasPriority() = runTest {
        val (controller, lease, transport) = ready()
        assertEquals(SlowMotionActionResult.Stopped, controller.emergencyStop(lease))
        assertEquals(listOf(MotionStopReason.EMERGENCY), transport.stops)
    }

    @Test
    fun emergencyStopSettlesAnArmedLease() = runTest {
        val transport = FakeTransport()
        val controller = SlowMotionController(transport)
        val lease = assertIs<SlowMotionAcquireResult.Granted>(controller.acquire()).lease

        assertEquals(SlowMotionActionResult.Stopped, controller.emergencyStop(lease))
        assertEquals(listOf(MotionStopReason.EMERGENCY), transport.stops)
        assertEquals(SlowMotionState.Idle, controller.state.value)
    }

    @Test
    fun emergencyStopCanSettleACommandFault() = runTest {
        val transport = FakeTransport().also {
            it.nextApply = SlowMotionTransportResult.Unknown("ack lost")
        }
        val controller = SlowMotionController(transport)
        val lease = assertIs<SlowMotionAcquireResult.Granted>(controller.acquire()).lease
        assertEquals(SlowMotionActionResult.Started, controller.start(lease, targets(), 0L))
        assertIs<SlowMotionActionResult.Unknown>(controller.tick(lease, 75L))

        assertEquals(SlowMotionActionResult.Stopped, controller.emergencyStop(lease))
        assertEquals(listOf(MotionStopReason.EMERGENCY), transport.stops)
        assertEquals(SlowMotionState.Idle, controller.state.value)
    }
}
