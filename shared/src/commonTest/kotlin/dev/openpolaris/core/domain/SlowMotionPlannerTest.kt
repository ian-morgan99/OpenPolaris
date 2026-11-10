package dev.openpolaris.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SlowMotionPlannerTest {
    private val planner = SlowMotionPlanner(SlowMotionCalibration.REFERENCE)

    private fun targets(
        m1: SlowMotionTarget = SlowMotionTarget(0.0, hold = false),
        m2: SlowMotionTarget = SlowMotionTarget(0.0, hold = false),
        m3: SlowMotionTarget = SlowMotionTarget(0.0, hold = false),
    ) = mapOf(RateAxis.M1_PAN to m1, RateAxis.M2_TILT to m2, RateAxis.M3_ROTATION to m3)

    @Test
    fun low_rate_three_axis_plan_preserves_each_target() {
        val sidereal = 360.0 / 86164.1
        val plan = planner.plan(
            targets(
                m1 = SlowMotionTarget(sidereal, hold = true),
                m2 = SlowMotionTarget(0.0, hold = true),
                m3 = SlowMotionTarget(-0.00402, hold = true),
            ),
        )

        assertEquals(1, plan.commonLevel)
        RateAxis.entries.forEach { axis ->
            val axisPlan = plan.axis(axis)
            assertEquals(SlowMotionMode.SLOW, axisPlan.mode)
            assertEquals(targets(
                m1 = SlowMotionTarget(sidereal, hold = true),
                m2 = SlowMotionTarget(0.0, hold = true),
                m3 = SlowMotionTarget(-0.00402, hold = true),
            ).getValue(axis).rate, axisPlan.averageRate(), 1e-12)
            assertTrue(axisPlan.low!!.state != 0)
            assertTrue(axisPlan.high!!.state != 0)
        }
        val zero = plan.axis(RateAxis.M2_TILT)
        assertEquals(1, zero.low!!.state)
        assertEquals(1, zero.high!!.state)
        assertEquals(1, zero.low!!.key)
        assertEquals(0, zero.high!!.key)
    }

    @Test
    fun higher_rate_selects_shared_level_and_idle_axes_stop() {
        val plan = planner.plan(targets(m1 = SlowMotionTarget(0.2, hold = true)))

        assertEquals(5, plan.commonLevel)
        assertEquals(SlowMotionMode.SLOW, plan.axis(RateAxis.M1_PAN).mode)
        assertEquals(0.2, plan.axis(RateAxis.M1_PAN).averageRate(), 1e-12)
        assertEquals(SlowMotionMode.STOP, plan.axis(RateAxis.M2_TILT).mode)
        assertEquals(SlowMotionMode.STOP, plan.axis(RateAxis.M3_ROTATION).mode)
    }

    @Test
    fun all_zero_holds_use_the_least_intrusive_torque_preserving_level() {
        val plan = planner.plan(
            targets(
                m1 = SlowMotionTarget(0.0, hold = true),
                m2 = SlowMotionTarget(0.0, hold = true),
                m3 = SlowMotionTarget(0.0, hold = true),
            ),
        )

        assertEquals(1, plan.commonLevel)
        assertTrue(plan.axes.all { it.mode == SlowMotionMode.SLOW })
    }

    @Test
    fun planner_rejects_unknown_axes_non_finite_and_over_envelope() {
        assertFailsWith<IllegalArgumentException> {
            planner.plan(mapOf(RateAxis.M1_PAN to SlowMotionTarget(0.0, false)))
        }
        assertFailsWith<IllegalArgumentException> {
            planner.plan(targets(m1 = SlowMotionTarget(Double.NaN, hold = true)))
        }
        assertFailsWith<IllegalArgumentException> {
            planner.plan(targets(m1 = SlowMotionTarget(0.3, hold = true)))
        }
    }

    @Test
    fun scheduler_respects_dwell_and_switches_zero_hold_direction() {
        val plan = planner.plan(
            targets(
                m1 = SlowMotionTarget(360.0 / 86164.1, hold = true),
                m2 = SlowMotionTarget(0.0, hold = true),
                m3 = SlowMotionTarget(-0.00402, hold = true),
            ),
        )
        val scheduler = SlowMotionScheduler()
        scheduler.setPlan(plan, nowMs = 0L)

        assertEquals(3, scheduler.tick(0L).size)
        assertEquals(0, scheduler.tick(50L).size)
        val transition = scheduler.tick(250L).single { it.axis == RateAxis.M2_TILT }
        assertEquals(SlowScheduleKind.SLOW, transition.kind)
        assertEquals(1, transition.point!!.state)
        assertEquals(1, transition.point.key)
    }

    @Test
    fun scheduler_emits_each_stop_once_and_retains_last_point() {
        val scheduler = SlowMotionScheduler()
        val moving = planner.plan(
            targets(
                m1 = SlowMotionTarget(0.01, hold = true),
                m2 = SlowMotionTarget(0.0, hold = true),
                m3 = SlowMotionTarget(-0.01, hold = true),
            ),
        )
        scheduler.setPlan(moving, 0L)
        scheduler.tick(0L)

        val stopped = planner.plan(targets())
        scheduler.setPlan(stopped, 1_000L)
        val commands = scheduler.tick(1_000L)

        assertEquals(3, commands.size)
        commands.forEach { command ->
            assertEquals(SlowScheduleKind.STOP, command.kind)
            assertIs<SlowMotionPoint>(command.point)
        }
        assertTrue(scheduler.tick(1_100L).isEmpty())
    }
}
