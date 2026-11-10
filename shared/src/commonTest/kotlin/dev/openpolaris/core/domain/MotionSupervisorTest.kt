package dev.openpolaris.core.domain

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MotionSupervisorTest {
    private class FakeStopTransport : MotionStopTransport {
        val stops = mutableListOf<Pair<MotionOwner, MotionStopReason>>()
        var next: MotionStopResult = MotionStopResult.Stopped

        override suspend fun stop(owner: MotionOwner, reason: MotionStopReason): MotionStopResult {
            stops += owner to reason
            return next
        }
    }

    @Test
    fun only_one_motion_owner_can_hold_the_mount() = runTest {
        val transport = FakeStopTransport()
        val supervisor = MotionSupervisor(transport)

        val granted = assertIs<MotionAcquireResult.Granted>(supervisor.acquire(MotionOwner.FAST_JOG))
        val busy = assertIs<MotionAcquireResult.Busy>(supervisor.acquire(MotionOwner.GOTO))

        assertEquals(granted.lease, busy.active)
        assertEquals(MotionState.Owned(granted.lease), supervisor.state.value)
    }

    @Test
    fun release_stops_the_active_owner_before_returning_idle() = runTest {
        val transport = FakeStopTransport()
        val supervisor = MotionSupervisor(transport)
        val lease = assertIs<MotionAcquireResult.Granted>(supervisor.acquire(MotionOwner.SLOW_MOVE)).lease

        assertEquals(MotionStopResult.Stopped, supervisor.stop(lease))
        assertEquals(listOf(MotionOwner.SLOW_MOVE to MotionStopReason.RELEASE), transport.stops)
        assertEquals(MotionState.Idle, supervisor.state.value)
    }

    @Test
    fun wrong_lease_cannot_stop_or_release_motion() = runTest {
        val transport = FakeStopTransport()
        val supervisor = MotionSupervisor(transport)
        val lease = assertIs<MotionAcquireResult.Granted>(supervisor.acquire(MotionOwner.GOTO)).lease

        assertEquals(MotionStopResult.Rejected, supervisor.stop(lease.copy(token = lease.token + 1)))
        assertEquals(emptyList(), transport.stops)
        assertEquals(MotionState.Owned(lease), supervisor.state.value)
    }

    @Test
    fun emergency_stop_has_priority_and_records_reason() = runTest {
        val transport = FakeStopTransport()
        val supervisor = MotionSupervisor(transport)
        supervisor.acquire(MotionOwner.PROGRAMME)

        assertEquals(MotionStopResult.Stopped, supervisor.emergencyStop())
        assertEquals(listOf(MotionOwner.PROGRAMME to MotionStopReason.EMERGENCY), transport.stops)
        assertEquals(MotionState.Idle, supervisor.state.value)
    }

    @Test
    fun lease_scoped_recovery_cannot_clear_a_different_owner() = runTest {
        val transport = FakeStopTransport()
        val supervisor = MotionSupervisor(transport)
        val first = assertIs<MotionAcquireResult.Granted>(
            supervisor.acquire(MotionOwner.SLOW_MOVE),
        ).lease

        assertEquals(MotionStopResult.Stopped, supervisor.stop(first))
        val second = assertIs<MotionAcquireResult.Granted>(
            supervisor.acquire(MotionOwner.GOTO),
        ).lease

        supervisor.recoverAfterReconnect(first)
        assertEquals(MotionState.Owned(second), supervisor.state.value)
    }

    @Test
    fun failed_stop_blocks_new_owners_until_explicit_recovery() = runTest {
        val transport = FakeStopTransport().also {
            it.next = MotionStopResult.Unknown("link disappeared")
        }
        val supervisor = MotionSupervisor(transport)
        val lease = assertIs<MotionAcquireResult.Granted>(supervisor.acquire(MotionOwner.FAST_JOG)).lease

        assertIs<MotionStopResult.Unknown>(supervisor.stop(lease, MotionStopReason.DISCONNECT))
        assertIs<MotionAcquireResult.Blocked>(supervisor.acquire(MotionOwner.GOTO))
        assertIs<MotionState.Faulted>(supervisor.state.value)

        supervisor.recoverAfterReconnect()
        assertEquals(MotionState.Idle, supervisor.state.value)
        assertIs<MotionAcquireResult.Granted>(supervisor.acquire(MotionOwner.GOTO))
    }
}
