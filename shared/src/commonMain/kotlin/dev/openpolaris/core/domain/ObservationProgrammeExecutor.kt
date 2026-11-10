package dev.openpolaris.core.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Executes an already validated [ObservationProgrammePlanner.Plan] without
 * owning any camera or mount wire protocol.
 *
 * The executor owns programme ordering and recovery semantics only. The
 * injected step function must acquire the appropriate camera/motion owners,
 * perform the step, and return [StepResult.Confirmed] only after the existing
 * positive lifecycle/file evidence is available. An ambiguous result is
 * terminal and is never retried automatically.
 */
class ObservationProgrammeExecutor(
    private val scope: CoroutineScope,
    private val owner: OwnerLease,
    private val executeStep: suspend (ObservationProgrammePlanner.Step) -> StepResult,
) {
    interface OwnerLease {
        fun acquire(): Boolean
        fun release()
    }

    sealed interface StepResult {
        data object Confirmed : StepResult
        data class Rejected(val reason: String) : StepResult
        data class OutcomeUnknown(val reason: String) : StepResult
    }

    sealed interface State {
        data object Idle : State
        data class Running(val plan: ObservationProgrammePlanner.Plan, val completedSteps: Int) : State
        data class Paused(val plan: ObservationProgrammePlanner.Plan, val completedSteps: Int) : State
        data class Completed(val plan: ObservationProgrammePlanner.Plan, val completedSteps: Int) : State
        data class Stopped(val plan: ObservationProgrammePlanner.Plan, val completedSteps: Int) : State
        data class Failed(val reason: String) : State
        data class OutcomeUnknown(
            val reason: String,
            val plan: ObservationProgrammePlanner.Plan,
            val completedSteps: Int,
        ) : State
    }

    var state: State = State.Idle
        private set

    private var job: Job? = null
    private var stepInFlight = false
    private var pauseRequested = false
    private var stopRequested = false
    private var ownerHeld = false

    fun start(plan: ObservationProgrammePlanner.Plan): Boolean {
        if (state is State.Running || state is State.Paused || state is State.OutcomeUnknown) return false
        if (!owner.acquire()) {
            state = State.Failed("programme owner is already held")
            return false
        }
        ownerHeld = true
        pauseRequested = false
        stopRequested = false
        state = State.Running(plan, 0)
        job = scope.launch { run(plan, fromIndex = 0, completed = 0) }
        return true
    }

    fun pause() {
        if (state !is State.Running) return
        pauseRequested = true
        if (!stepInFlight) {
            job?.cancel()
        }
    }

    fun resume(): Boolean {
        val paused = state as? State.Paused ?: return false
        if (!owner.acquire()) {
            state = State.Failed("programme owner could not be reacquired")
            return false
        }
        ownerHeld = true
        pauseRequested = false
        stopRequested = false
        state = State.Running(paused.plan, paused.completedSteps)
        job = scope.launch {
            run(paused.plan, fromIndex = paused.completedSteps, completed = paused.completedSteps)
        }
        return true
    }

    fun stop() {
        val current = state
        if (current !is State.Running && current !is State.Paused) return
        stopRequested = true
        if (!stepInFlight) {
            job?.cancel()
        }
        if (current is State.Paused) {
            state = State.Stopped(current.plan, current.completedSteps)
            releaseOwner()
        }
    }

    private suspend fun run(
        plan: ObservationProgrammePlanner.Plan,
        fromIndex: Int,
        completed: Int,
    ) {
        var completedSteps = completed
        try {
            for (index in fromIndex until plan.steps.size) {
                if (stopRequested) {
                    state = State.Stopped(plan, completedSteps)
                    releaseOwner()
                    return
                }
                if (pauseRequested) {
                    state = State.Paused(plan, completedSteps)
                    releaseOwner()
                    return
                }

                val step = plan.steps[index]
                if (step.delayBeforeMs > 0L) delay(step.delayBeforeMs)
                if (stopRequested) {
                    state = State.Stopped(plan, completedSteps)
                    releaseOwner()
                    return
                }
                if (pauseRequested) {
                    state = State.Paused(plan, completedSteps)
                    releaseOwner()
                    return
                }

                stepInFlight = true
                val result = try {
                    executeStep(step)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    StepResult.OutcomeUnknown(error.message ?: "programme step failed")
                } finally {
                    stepInFlight = false
                }

                when (result) {
                    StepResult.Confirmed -> {
                        completedSteps = index + 1
                        state = State.Running(plan, completedSteps)
                        if (stopRequested) {
                            state = State.Stopped(plan, completedSteps)
                            releaseOwner()
                            return
                        }
                        if (pauseRequested) {
                            state = State.Paused(plan, completedSteps)
                            releaseOwner()
                            return
                        }
                    }
                    is StepResult.Rejected -> {
                        state = State.Failed(result.reason)
                        releaseOwner()
                        return
                    }
                    is StepResult.OutcomeUnknown -> {
                        state = State.OutcomeUnknown(result.reason, plan, completedSteps)
                        releaseOwner()
                        return
                    }
                }
            }
            state = State.Completed(plan, completedSteps)
            releaseOwner()
        } catch (cancelled: CancellationException) {
            when {
                stopRequested -> state = State.Stopped(plan, completedSteps)
                pauseRequested -> state = State.Paused(plan, completedSteps)
                else -> {
                    releaseOwner()
                    throw cancelled
                }
            }
            releaseOwner()
        }
    }

    private fun releaseOwner() {
        if (ownerHeld) {
            ownerHeld = false
            owner.release()
        }
    }
}
