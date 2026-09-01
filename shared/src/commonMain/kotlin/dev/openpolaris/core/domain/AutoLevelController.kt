package dev.openpolaris.core.domain

import dev.openpolaris.core.protocol.CommandTable
import dev.openpolaris.core.protocol.Codes
import dev.openpolaris.core.protocol.TiltCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * High-level auto-level control. Encapsulates the three auto-level primitives:
 *
 *  * [isEnabled] (547/548) — toggle the firmware's auto-level feature.
 *  * [run] (549) — trigger one auto-level cycle on demand.
 *  * [tilt] (537/538) — observe the current tilt envelope as the firmware pushes it.
 *
 * The 537/538 codes are NOT a true push (no `PUSH_TILT` constant in Codes.kt — the
 * `PUSH_*` family is for unsolicited state the mount sends). For 537 we treat it as
 * a request/reply: the controller polls on demand and exposes the result via
 * [tilt]. When the firmware does push a 538 frame, it surfaces on `session.frames`
 * and the controller captures the most recent value, so observers always see the
 * freshest known tilt.
 *
 * [run] with a [timeout] subscribes to the tilt push (538) and waits for the
 * AHRS settling condition defined by [SETTLE_EPSILON_DEG] over
 * [SETTLE_WINDOW] consecutive samples. See [AutoLevelResult] for the three
 * possible outcomes.
 *
 * Lifecycle: the consumer calls [start] when the session is connected, [stop]
 * when disconnecting. While started, the controller subscribes to `session.frames`
 * and updates [tilt] whenever a 538 frame arrives.
 */
class AutoLevelController(
    private val session: MountSession,
    /**
     * Source of tilt samples for the settling loop. Returns the next sample, or
     * null if the source is exhausted. The default is the [_tilt] StateFlow
     * populated by the [start] collector: production callers do not need to
     * override this. Tests override with a queue-based source because they
     * need deterministic, time-controlled sample delivery without spinning
     * up a real session.frames reader (see PLAN-CRITICAL-REVIEW §F).
     */
    sampleSource: suspend () -> Tilt? = DefaultSampleSource,
) {

    /**
     * Backing field for the constructor's [sampleSource] parameter. If the
     * caller used the default placeholder, it is rebound in [init] to a
     * real reader that closes over [_tilt] (which is only visible once
     * the class body has run, not at default-argument evaluation time).
     */
    private var sampleSource: suspend () -> Tilt? = sampleSource

    init {
        if (sampleSource === DefaultSampleSource) {
            this.sampleSource = { _tilt.first { it != null } }
        }
    }

    data class Tilt(val pitchDeg: Double, val rollDeg: Double) {
        val withinTolerance: Boolean get() = kotlin.math.abs(pitchDeg) <= TOLERANCE_DEG && kotlin.math.abs(rollDeg) <= TOLERANCE_DEG
    }

    /**
     * Outcome of [run] with a timeout. [Completed] reports the mean tilt over
     * the settling window; [Failed] is returned when a sample cannot be
     * obtained; [TimedOut] is returned when the budget elapses before the
     * settling window fills.
     */
    sealed interface AutoLevelResult {
        data class Completed(val rollDeg: Double, val pitchDeg: Double) : AutoLevelResult
        data class Failed(val reason: String) : AutoLevelResult
        data object TimedOut : AutoLevelResult
    }

    private val _isEnabled = MutableStateFlow<Boolean?>(null)
    /** Last-known auto-level enabled flag; null while unknown. */
    val isEnabled: StateFlow<Boolean?> = _isEnabled.asStateFlow()

    private val _tilt = MutableStateFlow<Tilt?>(null)
    /** Last-known tilt envelope (pitch, roll in degrees); null while unknown. */
    val tilt: StateFlow<Tilt?> = _tilt.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    /** True between [run] and the next matching 538 frame (best-effort completion signal). */
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var observeJob: Job? = null

    /** Subscribe to incoming frames to capture tilt pushes. */
    fun start(scope: CoroutineScope) {
        if (observeJob != null) return
        observeJob = scope.launch {
            session.frames.collect { f ->
                if (f == null) return@collect
                if (f.code == Codes.SET_TILT_STATE) {
                    TiltCodec.parse(f)?.let { parsed ->
                        _tilt.value = parsed
                        _isRunning.value = false
                    }
                }
            }
        }
    }

    /** Unsubscribe from frame updates. Idempotent. */
    fun stop() {
        observeJob?.cancel()
        observeJob = null
        _isRunning.value = false
    }

    /** Refresh the enabled flag from the mount (547). */
    suspend fun refreshEnabled(): Boolean? {
        val result = session.request(CommandTable.AUTO_LEVEL_GET_EN.code) { f -> f.int("en") }
        return when (result) {
            is MountSession.CmdResult.Ok -> {
                val v = result.value == 1
                _isEnabled.value = v
                v
            }
            else -> null
        }
    }

    /** Toggle the firmware's auto-level feature (548). */
    suspend fun setEnabled(on: Boolean) {
        session.send(CommandTable.AUTO_LEVEL_SET_EN.code, CommandTable.AUTO_LEVEL_SET_EN.payload(on))
        _isEnabled.value = on
    }

    /**
     * Trigger one auto-level cycle (549) and return immediately. The cycle's
     * progress is observed via the [start] collector updating [tilt] on each
     * 538 frame; [isRunning] flips back to false on the first 538 frame as
     * a best-effort completion signal. To await AHRS settling use
     * [runAndAwait].
     */
    suspend fun run() {
        _isRunning.value = true
        session.send(CommandTable.AUTO_LEVEL_TRIGGER.code)
    }

    /**
     * Trigger one auto-level cycle (549) and await AHRS settling within
     * [timeout]. Returns [AutoLevelResult.Completed] when [SETTLE_WINDOW]
     * consecutive samples each satisfy
     * `|roll - roll_mean| < SETTLE_EPSILON_DEG` and
     * `|pitch - pitch_mean| < SETTLE_EPSILON_DEG`; [AutoLevelResult.TimedOut]
     * when the budget elapses first; [AutoLevelResult.Failed] when a sample
     * cannot be obtained.
     *
     * Spec note: PLAN-CRITICAL-REVIEW §F and GitHub issue #5 both reference
     * "the existing 517 position push" for the settling samples, but 517 is
     * `GET_GIMBAL_POS` (RA/Dec) per [Codes.GET_GIMBAL_POS]. The actual tilt
     * push is 538 ([Codes.SET_TILT_STATE]). The default [sampleSource] reads
     * from `session.frames` filtered to 538, which is the only tilt source
     * the firmware provides.
     */
    /**
     * Run auto-level until the [SETTLE_WINDOW] predicate holds, the caller's
     * scope is cancelled, or [timeout] elapses.
     *
     * Cancellation contract (revised in 3b.5 per issue #7 reviewer
     * feedback `5464953376`):
     * - `CancellationException` from the *calling* coroutine propagates to
     *   the caller. Coroutine cancellation is control flow, not an
     *   application error: a `launch {}` cancelled by its parent is
     *   expected to propagate cancellation, and converting it to
     *   `Failed("cancelled")` would swallow structured cancellation that
     *   the rest of the codebase relies on. Callers that need a visible
     *   "cancelled" status should set that in their own cancellation
     *   handler (see `AppViewModel.runAutoLevel`).
     * - `TimeoutCancellationException` is *not* propagated — it is mapped
     *   to [AutoLevelResult.TimedOut] because the timeout budget is a
     *   domain concept, not coroutine cancellation.
     * - [_isRunning] is *always* cleared in `finally`. `MutableStateFlow`
     *   assignment is non-suspending, so cancellation is not a reason to
     *   suppress this state update; the previous "skip on cancel" guard
     *   left observers seeing `isRunning == true` after a cancelled run.
     */
    suspend fun runAndAwait(timeout: Duration = 60.seconds): AutoLevelResult {
        run()
        return try {
            withTimeout(timeout) { awaitSettling() }
        } catch (_: TimeoutCancellationException) {
            AutoLevelResult.TimedOut
        } finally {
            _isRunning.value = false
        }
    }

    private suspend fun awaitSettling(): AutoLevelResult {
        val window = ArrayDeque<Tilt>(SETTLE_WINDOW)
        while (true) {
            val sample = nextSampleWithFallback() ?: return AutoLevelResult.Failed("no sample")
            window.addLast(sample)
            while (window.size > SETTLE_WINDOW) window.removeFirst()
            // Always update the public tilt so observers see the same stream
            // the settling predicate sees.
            _tilt.value = sample
            if (window.size < SETTLE_WINDOW) continue
            val mean = window.mean()
            val settled = window.all { t ->
                kotlin.math.abs(t.rollDeg - mean.roll) < SETTLE_EPSILON_DEG &&
                    kotlin.math.abs(t.pitchDeg - mean.pitch) < SETTLE_EPSILON_DEG
            }
            if (settled) return AutoLevelResult.Completed(mean.roll, mean.pitch)
        }
    }

    /**
     * Next tilt sample, with an active-poll fallback.
     *
     * Issue ("autolevel did nothing" — 2026-08-31 user report): on real
     * firmware the controller subscribes to `session.frames` for 538
     * tilt pushes, but the push stream is best-effort — if the firmware
     * never pushes, the settling loop blocks on `sampleSource()`
     * indefinitely. The 537 GET is a deterministic on-demand read that
     * works regardless of the push cadence, so this routine falls back
     * to it after a short idle window. Without the fallback the
     * controller reports "TimedOut" even when the firmware is healthy
     * and just not pushing.
     *
     * The active-poll path also covers the in-app demo mode, where
     * `SimulatedMount` answers 537 immediately even when no 538 push
     * is in flight.
     */
    private suspend fun nextSampleWithFallback(): Tilt? {
        // Race the passive push source against an active 537 poll so a
        // long push gap still produces a fresh sample within ~500ms.
        val passive = withTimeoutOrNull(SAMPLE_IDLE_MS) { sampleSource() }
        if (passive != null) return passive
        return readTilt()
    }

    /** Read the current tilt envelope on demand (537). */
    suspend fun readTilt(): Tilt? {
        val result = session.request(Codes.GET_TILT_STATE) { TiltCodec.parse(it) }
        return when (result) {
            is MountSession.CmdResult.Ok -> {
                result.value?.also { _tilt.value = it }
            }
            else -> null
        }
    }

    companion object {
        /** Polaris tilt envelope considered "level" (matches Benro app's green badge threshold). */
        const val TOLERANCE_DEG: Double = 0.5

        /** Per-axis tolerance for the AHRS settling window, in degrees. */
        const val SETTLE_EPSILON_DEG: Double = 0.01

        /** Number of consecutive samples that must all lie within [SETTLE_EPSILON_DEG] of the mean. */
        const val SETTLE_WINDOW: Int = 10

        /**
         * Active-poll fallback window. If the passive 538 push source is
         * idle for longer than this, the settling loop fires a 537 GET
         * on demand. Tuned for the in-app demo mode's simulated 538
         * cadence (~25ms/step) — well below the window — while still
         * catching the "no push at all" firmware case within ~500ms.
         */
        const val SAMPLE_IDLE_MS: Long = 500

        /**
         * Sentinel value used as the default for the primary-constructor
         * [sampleSource] parameter. Compared with `===` in the [init] block
         * to detect that the caller did not pass an explicit source, so we
         * can rebind to a real reader that closes over [_tilt].
         */
        private val DefaultSampleSource: suspend () -> AutoLevelController.Tilt? = { null }
    }
}

private data class TiltMean(val roll: Double, val pitch: Double)

/** Arithmetic mean of the [AutoLevelController.Tilt]s in this deque. Requires at least one element. */
private fun ArrayDeque<AutoLevelController.Tilt>.mean(): TiltMean {
    var r = 0.0
    var p = 0.0
    for (t in this) { r += t.rollDeg; p += t.pitchDeg }
    val n = size
    return TiltMean(r / n, p / n)
}
