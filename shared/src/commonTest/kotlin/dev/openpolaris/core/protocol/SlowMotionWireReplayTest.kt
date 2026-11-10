package dev.openpolaris.core.protocol

import dev.openpolaris.core.domain.RateAxis
import dev.openpolaris.core.domain.SlowMotionCalibration
import dev.openpolaris.core.domain.SlowMotionPlanner
import dev.openpolaris.core.domain.SlowMotionTarget
import dev.openpolaris.core.domain.SlowScheduleKind
import dev.openpolaris.core.domain.SlowMotionPoint
import dev.openpolaris.core.domain.SlowScheduleCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Host-only fake head for the source-aligned SLOW frames. It is deliberately
 * test-local: it proves formatter/scheduler behavior, not target MCU behavior.
 */
class SlowMotionWireReplayTest {
    private class FakeHead(private val leaseWindowMs: Long = 400L) {
        private val active = mutableMapOf<RateAxis, SlowMotionWireFrame>()
        private val lastSeenMs = mutableMapOf<RateAxis, Long>()

        fun apply(frame: SlowMotionWireFrame, nowMs: Long) {
            require(nowMs >= 0L)
            require(frame.subtype == 3) { "wrong SLOW subtype" }
            require(frame.bytes.decodeToString() == "1&${frame.code}&3&${frame.payload}#") {
                "frame bytes do not match the declared SLOW frame"
            }
            val axis = when (frame.code) {
                Codes.YAW_KEY -> RateAxis.M1_PAN
                Codes.PITCH_KEY -> RateAxis.M2_TILT
                Codes.ROLL_KEY -> RateAxis.M3_ROTATION
                else -> error("unknown SLOW opcode")
            }
            val match = Regex("key:([01]);state:([012]);level:([1-5]);").matchEntire(frame.payload)
                ?: error("malformed SLOW payload")
            val state = match.groupValues[2].toInt()
            if (state == 0) {
                if (active.remove(axis) == null) error("repeated STOP")
                lastSeenMs.remove(axis)
            } else {
                active[axis] = frame
                lastSeenMs[axis] = nowMs
            }
        }

        fun tick(nowMs: Long): List<RateAxis> {
            require(nowMs >= 0L)
            val expired = lastSeenMs.filterValues { nowMs - it >= leaseWindowMs }.keys.toList()
            expired.forEach {
                active.remove(it)
                lastSeenMs.remove(it)
            }
            return expired
        }

        fun activeAxes(): Set<RateAxis> = active.keys.toSet()
    }

    @Test
    fun `scheduler output replays through all three source-aligned axes and stops`() {
        val planner = SlowMotionPlanner(SlowMotionCalibration.REFERENCE)
        val targets = RateAxis.entries.associateWith { axis ->
            SlowMotionTarget(
                rate = when (axis) {
                    RateAxis.M1_PAN -> 0.01
                    RateAxis.M2_TILT -> -0.01
                    RateAxis.M3_ROTATION -> 0.0
                },
                hold = true,
            )
        }
        val scheduler = dev.openpolaris.core.domain.SlowMotionScheduler()
        scheduler.setPlan(planner.plan(targets), 0L)
        val head = FakeHead()

        scheduler.tick(0L).forEach { head.apply(SlowMotionWire.encode(it), 0L) }
        assertEquals(RateAxis.entries.toSet(), head.activeAxes())

        scheduler.setPlan(planner.plan(emptyTargets()), 1_000L)
        scheduler.tick(1_000L).forEach { command ->
            assertEquals(SlowScheduleKind.STOP, command.kind)
            head.apply(SlowMotionWire.encode(command), 1_000L)
        }
        assertTrue(head.activeAxes().isEmpty())
    }

    @Test
    fun `fake head rejects repeated stop malformed bytes and stale lease`() {
        val head = FakeHead()
        val run = SlowMotionWire.encode(
            runCommand(RateAxis.M1_PAN, key = 0, state = 1, level = 1),
        )
        head.apply(run, 0L)
        assertEquals(listOf(RateAxis.M1_PAN), head.tick(400L))
        assertFailsWith<IllegalStateException> { head.apply(SlowMotionWire.encode(stopCommand(RateAxis.M1_PAN)), 401L) }

        val malformed = run.copy(bytes = "garbage".encodeToByteArray())
        assertFailsWith<IllegalArgumentException> { head.apply(malformed, 1L) }

        val fresh = SlowMotionWire.encode(
            runCommand(RateAxis.M1_PAN, key = 0, state = 1, level = 1),
        )
        head.apply(fresh, 2L)
        val stop = SlowMotionWire.encode(stopCommand(RateAxis.M1_PAN))
        head.apply(stop, 3L)
        assertFailsWith<IllegalStateException> { head.apply(stop, 4L) }
    }

    private fun runCommand(axis: RateAxis, key: Int, state: Int, level: Int) =
        SlowScheduleCommand(
            axis = axis,
            kind = SlowScheduleKind.SLOW,
            point = SlowMotionPoint(rate = 0.01, key = key, state = state, level = level),
        )

    private fun stopCommand(axis: RateAxis) =
        SlowScheduleCommand(axis = axis, kind = SlowScheduleKind.STOP, point = SlowMotionPoint(0.01, 0, 1, 1))

    private fun emptyTargets() = RateAxis.entries.associateWith {
        SlowMotionTarget(rate = 0.0, hold = false)
    }
}
