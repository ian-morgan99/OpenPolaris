package dev.openpolaris.core.domain

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ObservationProgrammeExecutorTest {
    private class FakeOwner(var available: Boolean = true) : ObservationProgrammeExecutor.OwnerLease {
        var acquires = 0
        var releases = 0

        override fun acquire(): Boolean {
            if (!available) return false
            available = false
            acquires += 1
            return true
        }

        override fun release() {
            available = true
            releases += 1
        }
    }

    private fun plan(vararg delays: Long) = ObservationProgrammePlanner.Plan(
        ObservationProgrammePlanner.Kind.LAPSE,
        delays.mapIndexed { index, delay ->
            ObservationProgrammePlanner.Step(index + 1, "step ${index + 1}", delay)
        },
    )

    @Test
    fun confirmedStepsRunInOrderAndReleaseTheOwner() = runTest {
        val owner = FakeOwner()
        val seen = mutableListOf<Int>()
        val executor = ObservationProgrammeExecutor(this, owner) { step ->
            seen += step.ordinal
            ObservationProgrammeExecutor.StepResult.Confirmed
        }

        val p = plan(0L, 10L, 10L)
        assertTrue(executor.start(p))
        advanceUntilIdle()

        assertEquals(listOf(1, 2, 3), seen)
        assertEquals(ObservationProgrammeExecutor.State.Completed(p, 3), executor.state)
        assertEquals(1, owner.acquires)
        assertEquals(1, owner.releases)
    }

    @Test
    fun rejectedStepStopsWithoutExecutingLaterSteps() = runTest {
        val owner = FakeOwner()
        val seen = mutableListOf<Int>()
        val executor = ObservationProgrammeExecutor(this, owner) { step ->
            seen += step.ordinal
            if (step.ordinal == 2) {
                ObservationProgrammeExecutor.StepResult.Rejected("camera not ready")
            } else {
                ObservationProgrammeExecutor.StepResult.Confirmed
            }
        }

        val p = plan(0L, 0L, 0L)
        executor.start(p)
        advanceUntilIdle()

        assertEquals(listOf(1, 2), seen)
        assertEquals(ObservationProgrammeExecutor.State.Failed("camera not ready"), executor.state)
        assertEquals(1, owner.releases)
    }

    @Test
    fun ambiguousStepIsTerminalAndCannotBeRetriedAutomatically() = runTest {
        val owner = FakeOwner()
        var attempts = 0
        val executor = ObservationProgrammeExecutor(this, owner) {
            attempts += 1
            ObservationProgrammeExecutor.StepResult.OutcomeUnknown("capture result not correlated")
        }

        val p = plan(0L, 0L)
        assertTrue(executor.start(p))
        advanceUntilIdle()

        assertEquals(1, attempts)
        assertEquals(ObservationProgrammeExecutor.State.OutcomeUnknown("capture result not correlated", p, 0), executor.state)
        assertFalse(executor.start(p))
    }

    @Test
    fun pauseCancelsOnlyTheInterStepWaitAndResumeContinuesAtNextStep() = runTest {
        val owner = FakeOwner()
        val seen = mutableListOf<Int>()
        val executor = ObservationProgrammeExecutor(this, owner) { step ->
            seen += step.ordinal
            ObservationProgrammeExecutor.StepResult.Confirmed
        }
        val p = plan(0L, 10_000L, 0L)

        executor.start(p)
        runCurrent()
        executor.pause()
        runCurrent()

        assertEquals(listOf(1), seen)
        assertEquals(ObservationProgrammeExecutor.State.Paused(p, 1), executor.state)
        assertEquals(1, owner.releases)

        assertTrue(executor.resume())
        advanceUntilIdle()
        assertEquals(listOf(1, 2, 3), seen)
        assertEquals(ObservationProgrammeExecutor.State.Completed(p, 3), executor.state)
        assertEquals(2, owner.acquires)
        assertEquals(2, owner.releases)
    }

    @Test
    fun stopDuringAnInFlightStepWaitsForItsOutcomeAndDoesNotRepeatIt() = runTest {
        val owner = FakeOwner()
        val releaseStep = CompletableDeferred<Unit>()
        var attempts = 0
        val p = plan(0L, 0L)
        val executor = ObservationProgrammeExecutor(this, owner) {
            attempts += 1
            releaseStep.await()
            ObservationProgrammeExecutor.StepResult.Confirmed
        }

        executor.start(p)
        runCurrent()
        executor.stop()
        assertEquals(ObservationProgrammeExecutor.State.Running(p, 0), executor.state)
        releaseStep.complete(Unit)
        runCurrent()

        assertEquals(1, attempts)
        assertEquals(ObservationProgrammeExecutor.State.Stopped(p, 1), executor.state)
        assertEquals(1, owner.releases)
    }
}
