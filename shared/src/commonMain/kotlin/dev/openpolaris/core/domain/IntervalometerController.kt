package dev.openpolaris.core.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Typed intervalometer / capture-sequence engine (handover §6, "Required
 * software tests: interval-plan validation and exact step order").
 *
 * The Benro firmware's 306/307 interval-type commands only select a
 * timelapse *mode*; the actual multi-shot sequence (count, interval,
 * pre-delay) is driven client-side. This controller owns that sequence:
 * it validates a [SequencePlan], then issues one shutter per shot at the
 * planned interval, with pause/resume/stop and progress reporting.
 *
 * Design notes:
 *  - Timing uses [delay], so tests run under `runTest` and advance virtual
 *    time deterministically instead of waiting real intervals.
 *  - Pause is implemented as cancel-and-restart from the next shot index,
 *    which makes pause/resume/stop trivially idempotent and safe against a
 *    mid-shot disconnect: resuming never re-triggers a shot that already
 *    fired (the loop resumes at `completedShots + 1`).
 *  - Shutter de-duplication: [hasInFlightShot] stays true from the moment a
 *    shutter is triggered until the progress observer confirms completion via
 *    [markShotDone] (or [stop] clears it). A resume therefore never re-sends
 *    an unconfirmed shutter — no duplicate exposure after timeout/reconnect.
 */
class IntervalometerController(
    private val camera: CameraController,
    private val scope: CoroutineScope,
    /**
     * Optional observer invoked on every [state] transition (start, each shot
     * boundary, pause/resume/stop, completion, failure). The production
     * [AppViewModel] uses this to mirror progress into its Compose state so
     * the UI updates as shots complete. Defaults to a no-op, which keeps the
     * engine's existing unit tests (which read [state] directly) unchanged.
     */
    private val onStateChange: ((State) -> Unit)? = null,
) {

    /** A validated capture plan. */
    data class SequencePlan(
        val shotCount: Int,
        val intervalMs: Long,
        val preDelayMs: Long = 0L,
        val exposureIndex: Int? = null,
    ) {
        init {
            require(shotCount > 0) { "shotCount must be > 0, was $shotCount" }
            require(intervalMs >= 0) { "intervalMs must be >= 0, was $intervalMs" }
            require(preDelayMs >= 0) { "preDelayMs must be >= 0, was $preDelayMs" }
        }

        /** Minimum scheduled duration, excluding camera processing time. */
        val totalDurationMs: Long get() =
            preDelayMs + intervalMs * (shotCount - 1).toLong()
    }

    sealed interface State {
        object Idle : State
        data class Running(val plan: SequencePlan, val completedShots: Int) : State
        data class Paused(val plan: SequencePlan, val completedShots: Int) : State
        data class Completed(val plan: SequencePlan, val completedShots: Int) : State
        data class Stopped(val plan: SequencePlan, val completedShots: Int) : State
        data class Failed(val reason: String) : State
    }

    var state: State = State.Idle
        private set

    /** Single write path for [state]: assigns, then notifies the observer. */
    private fun setState(next: State) {
        state = next
        onStateChange?.invoke(next)
    }

    /** True while a shutter trigger is in flight (not yet confirmed done). */
    private var shotInFlight = false
    private val shotCompleted = Channel<Unit>(capacity = Channel.CONFLATED)

    private var job: Job? = null

    /**
     * Validate and start the sequence. No-op if already running/paused.
     * Returns true when a new sequence was started.
     */
    fun start(plan: SequencePlan): Boolean {
        if (state is State.Running || state is State.Paused) return false
        shotInFlight = false
        setState(State.Running(plan, 0))
        job = scope.launch { runShots(plan, fromShot = 1, preDelay = plan.preDelayMs) }
        return true
    }

    private suspend fun runShots(plan: SequencePlan, fromShot: Int, preDelay: Long) {
        if (preDelay > 0L) delay(preDelay)
        for (i in fromShot..plan.shotCount) {
            // A resumed job can arrive here with the preceding shutter still
            // unconfirmed. Wait for its completion; never send it twice.
            if (!shotInFlight) {
                while (shotCompleted.tryReceive().isSuccess) Unit
                shotInFlight = true
                try {
                    plan.exposureIndex?.let { camera.setExposureTime(it) }
                    camera.capture()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    setState(State.Failed(e.message ?: "capture command failed"))
                    return
                }
            }

            // Command acceptance and elapsed time are not completion proof.
            // The owner correlates the lifecycle and file events, then calls
            // markShotDone(). This prevents overlapping exposures (#90).
            shotCompleted.receive()
            shotInFlight = false
            setState(State.Running(plan, i))
            if (i < plan.shotCount && plan.intervalMs > 0L) delay(plan.intervalMs)
        }
        setState(State.Completed(plan, plan.shotCount))
    }

    /**
     * Pause the sequence after the current shot boundary. Idempotent: a
     * no-op when not running. The in-flight flag is preserved so an
     * unconfirmed shutter is not re-triggered on [resume].
     */
    fun pause() {
        val s = state
        if (s !is State.Running) return
        job?.cancel()
        job = null
        setState(State.Paused(s.plan, s.completedShots))
    }

    /**
     * Resume a paused sequence from the next shot. Idempotent: a no-op when
     * not paused. No pre-delay on resume (it only applies to [start]).
     */
    fun resume() {
        val s = state
        if (s !is State.Paused) return
        val next = s.completedShots + 1
        if (next > s.plan.shotCount) {
            // Nothing left to shoot.
            shotInFlight = false
            setState(State.Completed(s.plan, s.plan.shotCount))
            return
        }
        setState(State.Running(s.plan, s.completedShots))
        job = scope.launch { runShots(s.plan, fromShot = next, preDelay = 0L) }
    }

    /**
     * Stop the sequence early. Idempotent: a no-op when already terminal.
     */
    fun stop() {
        val s = state
        if (s is State.Idle || s is State.Completed || s is State.Stopped || s is State.Failed) return
        job?.cancel()
        job = null
        shotInFlight = false
        setState(when (s) {
            is State.Running -> State.Stopped(s.plan, s.completedShots)
            is State.Paused -> State.Stopped(s.plan, s.completedShots)
            else -> s
        })
    }

    /**
     * Progress observer hook: called by the capture-event watcher when a
     * shot's file/lifecycle event confirms completion. Clears the in-flight
     * flag so a subsequent resume does not double-trigger the shutter.
     */
    fun markShotDone() {
        if (shotInFlight) shotCompleted.trySend(Unit)
    }

    /** Whether a shutter trigger is currently unconfirmed (in flight). */
    val hasInFlightShot: Boolean get() = shotInFlight
}
