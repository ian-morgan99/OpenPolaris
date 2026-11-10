package dev.openpolaris.core.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Deterministic, transport-free SLOW interaction model for Demo mode.
 *
 * It uses the same planner and scheduler as the future qualified adapter, but
 * deliberately records logical transitions instead of serializing or sending
 * 532/533/534/AC 4A. It exists so the mobile/desktop UX can be exercised
 * without making a simulator look like physical qualification.
 */
data class SlowMotionDemoSnapshot(
    val running: Boolean = false,
    val axis: RateAxis? = null,
    val direction: Int = 0,
    val level: Int = 0,
    val lastEvents: List<SlowScheduleCommand> = emptyList(),
) {
    val label: String
        get() = if (!running) "Stopped" else "${axis?.name ?: "axis"} ${if (direction < 0) "B" else "A"} · level $level"
}

class SlowMotionDemoController(
    calibration: SlowMotionCalibration = SlowMotionCalibration.REFERENCE,
) {
    private val planner = SlowMotionPlanner(calibration)
    private val calibration = calibration
    private val scheduler = SlowMotionScheduler()
    private val _state = MutableStateFlow(SlowMotionDemoSnapshot())
    val state: StateFlow<SlowMotionDemoSnapshot> = _state.asStateFlow()

    fun start(axis: RateAxis, direction: Int, level: Int, nowMs: Long): SlowMotionDemoSnapshot {
        require(direction == -1 || direction == 1) { "demo direction must be -1 or 1" }
        require(level in 1..5) { "demo level must be 1..5" }
        require(nowMs >= 0L) { "demo time must be non-negative" }
        val rate = plannerRate(level) * direction
        val targets = RateAxis.entries.associateWith { candidate ->
            if (candidate == axis) SlowMotionTarget(rate, hold = true)
            else SlowMotionTarget(0.0, hold = false)
        }
        scheduler.setPlan(planner.plan(targets), nowMs)
        return publish(
            SlowMotionDemoSnapshot(
                running = true,
                axis = axis,
                direction = direction,
                level = level,
                lastEvents = scheduler.tick(nowMs),
            ),
        )
    }

    fun tick(nowMs: Long): SlowMotionDemoSnapshot {
        require(nowMs >= 0L) { "demo time must be non-negative" }
        val current = _state.value
        return if (!current.running) current else publish(current.copy(lastEvents = scheduler.tick(nowMs)))
    }

    fun stop(nowMs: Long): SlowMotionDemoSnapshot {
        require(nowMs >= 0L) { "demo time must be non-negative" }
        val current = _state.value
        if (!current.running) return current.copy(lastEvents = emptyList()).also { _state.value = it }
        scheduler.setPlan(
            planner.plan(RateAxis.entries.associateWith { SlowMotionTarget(0.0, hold = false) }),
            nowMs,
        )
        return publish(current.copy(running = false, direction = 0, level = 0, lastEvents = scheduler.tick(nowMs)))
    }

    fun reset(nowMs: Long) {
        stop(nowMs)
        _state.value = SlowMotionDemoSnapshot()
    }

    private fun plannerRate(level: Int): Double {
        val rung = calibration.levels.getValue(level)
        return (rung.stateOneRate + rung.stateTwoRate) / 2.0
    }

    private fun publish(snapshot: SlowMotionDemoSnapshot): SlowMotionDemoSnapshot {
        _state.value = snapshot
        return snapshot
    }
}
