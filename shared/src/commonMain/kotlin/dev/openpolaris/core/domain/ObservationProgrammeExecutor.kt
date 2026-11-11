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
    private val onStateChange: ((State) -> Unit)? = null,
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

    private fun setState(next: State) {
        state = next
        onStateChange?.invoke(next)
    }

    private var job: Job? = null
    private var stepInFlight = false
    private var pauseRequested = false
    private var stopRequested = false
    private var ownerHeld = false

    fun start(plan: ObservationProgrammePlanner.Plan): Boolean {
        if (state is State.Running || state is State.Paused || state is State.OutcomeUnknown) return false
        if (!owner.acquire()) {
            setState(State.Failed("programme owner is already held"))
            return false
        }
        ownerHeld = true
        pauseRequested = false
        stopRequested = false
        setState(State.Running(plan, 0))
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
            setState(State.Failed("programme owner could not be reacquired"))
            return false
        }
        ownerHeld = true
        pauseRequested = false
        stopRequested = false
        setState(State.Running(paused.plan, paused.completedSteps))
        job = scope.launch {
            run(paused.plan, fromIndex = paused.completedSteps, completed = paused.completedSteps)
        }
        return true
    }

    /**
     * Restore a durable paused checkpoint without acquiring the camera owner.
     *
     * The operator must explicitly call [resume] afterwards, which reacquires
     * the owner and starts at the next unconfirmed step. Running or ambiguous
     * checkpoints are intentionally not accepted here.
     */
    fun restorePaused(plan: ObservationProgrammePlanner.Plan, completedSteps: Int): Boolean {
        if (state is State.Running || state is State.Paused || state is State.OutcomeUnknown) return false
        if (completedSteps !in 0..plan.captureCount) return false
        setState(State.Paused(plan, completedSteps))
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
            setState(State.Stopped(current.plan, current.completedSteps))
            releaseOwner()
        }
    }

    /** Abandon the programme on session loss; an in-flight step becomes unknown. */
    fun abandon(reason: String) {
        val current = state
        when (current) {
            is State.Running -> setState(State.OutcomeUnknown(reason, current.plan, current.completedSteps))
            is State.Paused -> setState(State.Stopped(current.plan, current.completedSteps))
            else -> return
        }
        stopRequested = false
        pauseRequested = false
        job?.cancel()
        job = null
        stepInFlight = false
        releaseOwner()
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
                    setState(State.Stopped(plan, completedSteps))
                    releaseOwner()
                    return
                }
                if (pauseRequested) {
                    setState(State.Paused(plan, completedSteps))
                    releaseOwner()
                    return
                }

                val step = plan.steps[index]
                if (step.delayBeforeMs > 0L) delay(step.delayBeforeMs)
                if (stopRequested) {
                    setState(State.Stopped(plan, completedSteps))
                    releaseOwner()
                    return
                }
                if (pauseRequested) {
                    setState(State.Paused(plan, completedSteps))
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
                        setState(State.Running(plan, completedSteps))
                        if (stopRequested) {
                            setState(State.Stopped(plan, completedSteps))
                            releaseOwner()
                            return
                        }
                        if (pauseRequested) {
                            setState(State.Paused(plan, completedSteps))
                            releaseOwner()
                            return
                        }
                    }
                    is StepResult.Rejected -> {
                        setState(State.Failed(result.reason))
                        releaseOwner()
                        return
                    }
                    is StepResult.OutcomeUnknown -> {
                        setState(State.OutcomeUnknown(result.reason, plan, completedSteps))
                        releaseOwner()
                        return
                    }
                }
            }
            setState(State.Completed(plan, completedSteps))
            releaseOwner()
        } catch (cancelled: CancellationException) {
            when {
                stopRequested -> setState(State.Stopped(plan, completedSteps))
                pauseRequested -> setState(State.Paused(plan, completedSteps))
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
