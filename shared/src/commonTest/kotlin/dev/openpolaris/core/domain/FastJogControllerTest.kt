package dev.openpolaris.core.domain

import dev.openpolaris.core.protocol.Codes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FastJogControllerTest {
    private class FakeTransport : FastJogTransport {
        val sends = mutableListOf<Pair<Int, Int>>()
        val stops = mutableListOf<Pair<Int, MotionStopReason>>()
        var nextSend: FastJogSendResult = FastJogSendResult.Sent
        var nextStop: MotionStopResult = MotionStopResult.Stopped

        override suspend fun send(code: Int, durationMs: Int): FastJogSendResult {
            sends += code to durationMs
            return nextSend
        }

        override suspend fun stop(code: Int, reason: MotionStopReason): MotionStopResult {
            stops += code to reason
            return nextStop
        }
    }

    @Test
    fun onlyOneLeaseCanBeArmedAndStartUsesExistingDurationContract() = kotlinx.coroutines.test.runTest {
        val transport = FakeTransport()
        val controller = FastJogController(transport)
        val first = assertIs<FastJogAcquireResult.Granted>(controller.acquire(Codes.GIMBAL_HADJ_SPEED)).lease
        val second = controller.acquire(Codes.GIMBAL_VADJ_SPEED)

        assertIs<FastJogAcquireResult.Busy>(second)
        assertEquals(FastJogActionResult.Accepted, controller.start(first, nowMs = 100L))
        assertEquals(listOf(Codes.GIMBAL_HADJ_SPEED to 500), transport.sends)
    }

    @Test
    fun tickDoesNotSpamBeforeRepeatIntervalAndExpiresAtLeaseWindow() = kotlinx.coroutines.test.runTest {
        val transport = FakeTransport()
        val controller = FastJogController(transport, leaseWindowMs = 400L, repeatIntervalMs = 50L)
        val lease = assertIs<FastJogAcquireResult.Granted>(controller.acquire(Codes.GIMBAL_VADJ_SPEED)).lease
        controller.start(lease, 0L)

        assertEquals(FastJogActionResult.NotDue, controller.tick(lease, 49L))
        assertEquals(FastJogActionResult.Accepted, controller.tick(lease, 50L))
        assertEquals(FastJogActionResult.Stopped, controller.tick(lease, 450L))
        assertEquals(listOf(MotionStopReason.LEASE_EXPIRED), transport.stops.map { it.second })
        assertEquals(FastJogState.Idle, controller.state.value)
    }

    @Test
    fun releaseStopsBeforeReturningIdle() = kotlinx.coroutines.test.runTest {
        val transport = FakeTransport()
        val controller = FastJogController(transport)
        val lease = assertIs<FastJogAcquireResult.Granted>(controller.acquire(Codes.GIMBAL_HADJ_ANGLE)).lease
        controller.start(lease, 10L)

        assertEquals(FastJogActionResult.Stopped, controller.release(lease))
        assertEquals(listOf(Codes.GIMBAL_HADJ_ANGLE to MotionStopReason.RELEASE), transport.stops)
        assertEquals(FastJogState.Idle, controller.state.value)
    }

    @Test
    fun stopFailureBlocksUntilExplicitRecovery() = kotlinx.coroutines.test.runTest {
        val transport = FakeTransport().also { it.nextStop = MotionStopResult.Unknown("no stop acknowledgement") }
        val controller = FastJogController(transport)
        val lease = assertIs<FastJogAcquireResult.Granted>(controller.acquire(Codes.GIMBAL_VADJ_ANGLE)).lease
        controller.start(lease, 1L)

        assertIs<FastJogActionResult.Unknown>(controller.release(lease))
        assertIs<FastJogState.Faulted>(controller.state.value)
        assertIs<FastJogAcquireResult.Blocked>(controller.acquire(Codes.GIMBAL_HADJ_SPEED))
        controller.recoverAfterReconnect()
        assertIs<FastJogAcquireResult.Granted>(controller.acquire(Codes.GIMBAL_HADJ_SPEED))
    }

    @Test
    fun ambiguousBeatNeverRetriesAutomatically() = kotlinx.coroutines.test.runTest {
        val transport = FakeTransport().also { it.nextSend = FastJogSendResult.Unknown("link dropped") }
        val controller = FastJogController(transport)
        val lease = assertIs<FastJogAcquireResult.Granted>(controller.acquire(Codes.GIMBAL_HADJ_SPEED)).lease

        assertIs<FastJogActionResult.Unknown>(controller.start(lease, 0L))
        assertEquals(1, transport.sends.size)
        assertIs<FastJogAcquireResult.Blocked>(controller.acquire(Codes.GIMBAL_HADJ_SPEED))
        assertTrue(transport.stops.isEmpty())
    }

    @Test
    fun invalidCodeAndTimeFailBeforeTransport() = kotlinx.coroutines.test.runTest {
        val transport = FakeTransport()
        val controller = FastJogController(transport)

        assertFailsWith<IllegalArgumentException> { controller.acquire(512) }
        val lease = assertIs<FastJogAcquireResult.Granted>(controller.acquire(Codes.GIMBAL_HADJ_SPEED)).lease
        assertFailsWith<IllegalArgumentException> { controller.start(lease, -1L) }
        assertTrue(transport.sends.isEmpty())
    }
}
