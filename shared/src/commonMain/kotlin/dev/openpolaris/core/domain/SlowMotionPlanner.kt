package dev.openpolaris.core.domain

import kotlin.math.abs

/** One measured SLOW level. Values are calibration data, not a live default. */
data class SlowMotionLevel(
    val stateOneRate: Double,
    val stateTwoRate: Double,
) {
    init {
        require(stateOneRate.isFinite() && stateTwoRate.isFinite()) { "SLOW rates must be finite" }
        require(stateOneRate > 0.0 && stateTwoRate >= stateOneRate) {
            "SLOW state rates must be positive and ordered"
        }
    }
}

/**
 * An explicit calibration table. The reference table is only for simulator
 * and planner tests; a target firmware must supply its own evidence-bound
 * record before this can drive a transport.
 */
data class SlowMotionCalibration(
    val levels: Map<Int, SlowMotionLevel>,
) {
    init {
        require(levels.keys == (1..5).toSet()) { "SLOW calibration must contain levels 1..5" }
    }

    val maximumRate: Double get() = levels.getValue(5).stateTwoRate

    companion object {
        /** Independent reference measurements used by the host-only planner seam. */
        val REFERENCE = SlowMotionCalibration(
            mapOf(
                1 to SlowMotionLevel(0.0059, 0.0119),
                2 to SlowMotionLevel(0.0179, 0.0295),
                3 to SlowMotionLevel(0.0474, 0.0593),
                4 to SlowMotionLevel(0.0896, 0.1193),
                5 to SlowMotionLevel(0.2088, 0.2402),
            ),
        )
    }
}

/** A requested average rate; hold keeps torque engaged at zero. */
data class SlowMotionTarget(
    val rate: Double,
    val hold: Boolean,
) {
    init {
        require(rate.isFinite()) { "requested SLOW rate must be finite" }
    }
}

enum class SlowMotionMode {
    STOP,
    SLOW,
}

/** One physical SLOW point. State 0 is reserved for a scheduler STOP. */
data class SlowMotionPoint(
    val rate: Double,
    val key: Int,
    val state: Int,
    val level: Int,
) {
    init {
        require(rate.isFinite()) { "SLOW point rate must be finite" }
        require(key == 0 || key == 1) { "SLOW point key must be 0 or 1" }
        require(state == 1 || state == 2) { "SLOW run point state must be 1 or 2" }
        require(level in 1..5) { "SLOW point level must be 1..5" }
    }
}

data class SlowAxisPlan(
    val mode: SlowMotionMode,
    val low: SlowMotionPoint? = null,
    val high: SlowMotionPoint? = null,
    val dutyHigh: Double = 0.0,
) {
    init {
        when (mode) {
            SlowMotionMode.STOP -> require(low == null && high == null) { "STOP must not contain SLOW points" }
            SlowMotionMode.SLOW -> {
                require(low != null && high != null) { "SLOW must contain adjacent points" }
                require(dutyHigh in 0.0..1.0) { "dutyHigh must be between 0 and 1" }
                require(low.level == high.level) { "SLOW points must use one common level" }
            }
        }
    }

    fun averageRate(): Double = when (mode) {
        SlowMotionMode.STOP -> 0.0
        SlowMotionMode.SLOW -> low!!.rate + dutyHigh * (high!!.rate - low.rate)
    }
}

data class SlowMotionPlan(
    val commonLevel: Int,
    val axes: List<SlowAxisPlan>,
) {
    init {
        require(axes.size == RateAxis.entries.size) { "SLOW plan must contain all physical axes" }
        require(commonLevel in 0..5) { "common SLOW level must be 0..5" }
        if (commonLevel == 0) require(axes.all { it.mode == SlowMotionMode.STOP })
    }

    fun axis(axis: RateAxis): SlowAxisPlan = axes[axis.ordinal]
}

/**
 * Pure planner for the measured/reference SLOW ladder.
 *
 * It produces adjacent points and a duty cycle; it never opens a socket and
 * cannot command a motor. Zero with hold=true is a balanced opposite state-1
 * pair, while zero with hold=false is an explicit STOP.
 */
class SlowMotionPlanner(
    private val calibration: SlowMotionCalibration,
) {
    fun plan(targets: Map<RateAxis, SlowMotionTarget>): SlowMotionPlan {
        require(targets.keys == RateAxis.entries.toSet()) { "targets must contain every physical axis" }

        var commonLevel = 0
        for (axis in RateAxis.entries) {
            val target = targets.getValue(axis)
            if (target.rate != 0.0 || target.hold) {
                val magnitude = abs(target.rate)
                val candidate = (1..5).firstOrNull { magnitude <= calibration.levels.getValue(it).stateTwoRate }
                    ?: throw IllegalArgumentException("rate exceeds the reference SLOW envelope")
                commonLevel = maxOf(commonLevel, candidate)
            }
        }
        // An engaged zero-rate hold still needs a calibrated torque-preserving
        // point. Level 1 is the least intrusive reference choice.
        if (commonLevel == 0 && targets.values.any { it.hold }) commonLevel = 1

        val axes = RateAxis.entries.map { axis ->
            val target = targets.getValue(axis)
            if (target.rate == 0.0 && !target.hold) {
                SlowAxisPlan(SlowMotionMode.STOP)
            } else {
                bracket(target.rate, commonLevel)
            }
        }
        return SlowMotionPlan(commonLevel, axes)
    }

    private fun bracket(target: Double, level: Int): SlowAxisPlan {
        val rung = calibration.levels.getValue(level)
        val ladder = listOf(
            SlowMotionPoint(-rung.stateTwoRate, key = 1, state = 2, level = level),
            SlowMotionPoint(-rung.stateOneRate, key = 1, state = 1, level = level),
            SlowMotionPoint(rung.stateOneRate, key = 0, state = 1, level = level),
            SlowMotionPoint(rung.stateTwoRate, key = 0, state = 2, level = level),
        )
        if (target <= ladder.first().rate) {
            return SlowAxisPlan(SlowMotionMode.SLOW, ladder.first(), ladder.first(), 1.0)
        }
        for (index in 1 until ladder.size) {
            if (target <= ladder[index].rate) {
                val low = ladder[index - 1]
                val high = ladder[index]
                val duty = ((target - low.rate) / (high.rate - low.rate)).coerceIn(0.0, 1.0)
                return SlowAxisPlan(SlowMotionMode.SLOW, low, high, duty)
            }
        }
        return SlowAxisPlan(SlowMotionMode.SLOW, ladder.last(), ladder.last(), 1.0)
    }
}

enum class SlowScheduleKind {
    STOP,
    SLOW,
}

data class SlowScheduleCommand(
    val axis: RateAxis,
    val kind: SlowScheduleKind,
    /** Last active point is retained for a STOP adapter; null means none. */
    val point: SlowMotionPoint? = null,
)

/**
 * Clock-driven pure scheduler. It emits logical SLOW transitions and STOP
 * events only. The transport adapter must still prove wire and physical stop
 * semantics before consuming these events.
 */
class SlowMotionScheduler(
    private val minimumDwellMs: Long = 75L,
    private val normalCycleMs: Long = 250L,
    private val reversingCycleMs: Long = 500L,
) {
    init {
        require(minimumDwellMs > 0L)
        require(normalCycleMs >= minimumDwellMs)
        require(reversingCycleMs >= normalCycleMs)
    }

    private var plan: SlowMotionPlan? = null
    private val active = arrayOfNulls<SlowMotionPoint>(RateAxis.entries.size)
    private val lastSwitchMs = LongArray(RateAxis.entries.size)
    private val cycleStartMs = LongArray(RateAxis.entries.size)
    private val stopSent = BooleanArray(RateAxis.entries.size)

    /**
     * Clear scheduler history at a new motion-session boundary. Without this,
     * a previously acknowledged point can suppress the first point of a new
     * lease when the new plan happens to begin on the same rung.
     */
    fun reset() {
        plan = null
        active.fill(null)
        lastSwitchMs.fill(0L)
        cycleStartMs.fill(0L)
        stopSent.fill(false)
    }

    fun setPlan(value: SlowMotionPlan, nowMs: Long) {
        require(nowMs >= 0L) { "scheduler time must be monotonic and non-negative" }
        plan = value
        RateAxis.entries.forEach { axis ->
            cycleStartMs[axis.ordinal] = nowMs
            stopSent[axis.ordinal] = false
        }
    }

    fun tick(nowMs: Long): List<SlowScheduleCommand> {
        require(nowMs >= 0L) { "scheduler time must be monotonic and non-negative" }
        val currentPlan = plan ?: return emptyList()
        return RateAxis.entries.mapNotNull { axis -> tickAxis(currentPlan.axis(axis), axis, nowMs) }
    }

    private fun tickAxis(axisPlan: SlowAxisPlan, axis: RateAxis, nowMs: Long): SlowScheduleCommand? {
        val index = axis.ordinal
        if (axisPlan.mode == SlowMotionMode.STOP) {
            if (stopSent[index]) return null
            stopSent[index] = true
            val previous = active[index]
            active[index] = null
            return SlowScheduleCommand(axis, SlowScheduleKind.STOP, previous)
        }

        stopSent[index] = false
        val desired = pointForPhase(axisPlan, nowMs, cycleStartMs[index])
        val previous = active[index]
        if (previous != null && samePoint(previous, desired)) return null
        val elapsed = if (previous == null) minimumDwellMs else nowMs - lastSwitchMs[index]
        if (elapsed < minimumDwellMs) return null

        active[index] = desired
        lastSwitchMs[index] = nowMs
        return SlowScheduleCommand(axis, SlowScheduleKind.SLOW, desired)
    }

    private fun pointForPhase(axisPlan: SlowAxisPlan, nowMs: Long, cycleStart: Long): SlowMotionPoint {
        val low = axisPlan.low!!
        val high = axisPlan.high!!
        val cycle = if (low.rate < 0.0 && high.rate > 0.0) reversingCycleMs else normalCycleMs
        val phase = ((nowMs - cycleStart) % cycle + cycle) % cycle
        return if (phase < axisPlan.dutyHigh * cycle) high else low
    }

    private fun samePoint(left: SlowMotionPoint, right: SlowMotionPoint): Boolean =
        left.key == right.key && left.state == right.state && left.level == right.level
}
