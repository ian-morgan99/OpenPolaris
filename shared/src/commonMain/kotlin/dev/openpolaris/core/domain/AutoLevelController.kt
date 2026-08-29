package dev.openpolaris.core.domain

import dev.openpolaris.core.protocol.CommandTable
import dev.openpolaris.core.protocol.Codes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
 * Lifecycle: the consumer calls [start] when the session is connected, [stop]
 * when disconnecting. While started, the controller subscribes to `session.frames`
 * and updates [tilt] whenever a 538 frame arrives.
 */
class AutoLevelController(private val session: MountSession) {

    data class Tilt(val pitchDeg: Double, val rollDeg: Double) {
        val withinTolerance: Boolean get() = kotlin.math.abs(pitchDeg) <= TOLERANCE_DEG && kotlin.math.abs(rollDeg) <= TOLERANCE_DEG
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
                    dev.openpolaris.core.protocol.TiltCodec.parse(f)?.let { parsed ->
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

    /** Trigger one auto-level cycle (549). Marks [isRunning] true until a tilt frame arrives. */
    suspend fun run() {
        _isRunning.value = true
        session.send(CommandTable.AUTO_LEVEL_TRIGGER.code)
    }

    /** Read the current tilt envelope on demand (537). */
    suspend fun readTilt(): Tilt? {
        val result = session.request(Codes.GET_TILT_STATE) { dev.openpolaris.core.protocol.TiltCodec.parse(it) }
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
    }
}
