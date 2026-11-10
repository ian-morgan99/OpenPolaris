package dev.openpolaris.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SlowMotionDemoControllerTest {
    @Test
    fun startUsesTheSharedPlannerAndEmitsOnlyLogicalEvents() {
        val controller = SlowMotionDemoController()

        val snapshot = controller.start(RateAxis.M1_PAN, direction = -1, level = 2, nowMs = 0L)

        assertTrue(snapshot.running)
        assertEquals(RateAxis.M1_PAN, snapshot.axis)
        assertEquals(-1, snapshot.direction)
        assertEquals(2, snapshot.level)
        assertTrue(snapshot.lastEvents.any { it.axis == RateAxis.M1_PAN && it.kind == SlowScheduleKind.SLOW })
        assertTrue(snapshot.lastEvents.all { it.point == null || it.point.level == 2 })
    }

    @Test
    fun stopEmitsOneLogicalStopAndClearsRunningState() {
        val controller = SlowMotionDemoController()
        controller.start(RateAxis.M2_TILT, direction = 1, level = 1, nowMs = 0L)

        val stopped = controller.stop(nowMs = 1_000L)

        assertTrue(!stopped.running)
        assertTrue(stopped.lastEvents.any { it.axis == RateAxis.M2_TILT && it.kind == SlowScheduleKind.STOP })
        assertEquals("Stopped", stopped.label)
    }

    @Test
    fun invalidDemoRequestsFailBeforeAnyStateChange() {
        val controller = SlowMotionDemoController()

        assertFailsWith<IllegalArgumentException> { controller.start(RateAxis.M3_ROTATION, 0, 1, 0L) }
        assertFailsWith<IllegalArgumentException> { controller.start(RateAxis.M3_ROTATION, 1, 6, 0L) }
        assertEquals(SlowMotionDemoSnapshot(), controller.state.value)
        assertIs<SlowMotionDemoSnapshot>(controller.stop(0L))
    }
}
