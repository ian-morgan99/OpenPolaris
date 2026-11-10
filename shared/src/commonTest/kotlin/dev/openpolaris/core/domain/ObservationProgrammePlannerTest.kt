package dev.openpolaris.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ObservationProgrammePlannerTest {
    @Test
    fun lapseUsesPreDelayThenIntervalWithoutDuplicatingAFrame() {
        val plan = ObservationProgrammePlanner.plan(
            ObservationProgrammePlanner.Request.Lapse(
                shotCount = 3,
                intervalMs = 2_000L,
                preDelayMs = 500L,
                exposureIndex = 7,
            ),
        )

        assertEquals(ObservationProgrammePlanner.Kind.LAPSE, plan.kind)
        assertEquals(3, plan.captureCount)
        assertEquals(listOf(500L, 2_000L, 2_000L), plan.steps.map { it.delayBeforeMs })
        assertEquals(listOf(7, 7, 7), plan.steps.map { it.exposureIndex })
    }

    @Test
    fun hdrSortsBracketsAndKeepsBaseExposureInThePlan() {
        val plan = ObservationProgrammePlanner.plan(
            ObservationProgrammePlanner.Request.Hdr(
                baseExposureIndex = 20,
                bracketOffsets = listOf(2, 0, -2),
                intervalMs = 100L,
            ),
        )

        assertEquals(listOf(18, 20, 22), plan.steps.map { it.exposureIndex })
        assertEquals(listOf("HDR 1/3", "HDR 2/3", "HDR 3/3"), plan.steps.map { it.label })
    }

    @Test
    fun focusStackInterpolatesBothEndpoints() {
        val plan = ObservationProgrammePlanner.plan(
            ObservationProgrammePlanner.Request.FocusStack(
                startPosition = 10.0,
                endPosition = 20.0,
                stepCount = 3,
                intervalMs = 50L,
            ),
        )

        assertEquals(listOf(10.0, 15.0, 20.0), plan.steps.map { it.focusPosition })
        assertEquals(100L, plan.scheduledDelayMs)
    }

    @Test
    fun panoramaUsesSerpentineRowsAndKeepsOffsetsSeparateFromWireCommands() {
        val plan = ObservationProgrammePlanner.plan(
            ObservationProgrammePlanner.Request.Panorama(
                rows = 2,
                columns = 3,
                yawStepDeg = 10.0,
                pitchStepDeg = 5.0,
                intervalMs = 25L,
                preDelayMs = 100L,
            ),
        )

        assertEquals(6, plan.captureCount)
        assertEquals(
            listOf(0.0, 10.0, 20.0, 20.0, 10.0, 0.0),
            plan.steps.map { it.yawOffsetDeg },
        )
        assertEquals(listOf(0.0, 0.0, 0.0, 5.0, 5.0, 5.0), plan.steps.map { it.pitchOffsetDeg })
        assertEquals(100L, plan.steps.first().delayBeforeMs)
        assertTrue(plan.steps.drop(1).all { it.delayBeforeMs == 25L })
    }

    @Test
    fun invalidPlansFailBeforeAnyTransportCouldBeStarted() {
        assertFailsWith<IllegalArgumentException> {
            ObservationProgrammePlanner.plan(
                ObservationProgrammePlanner.Request.Hdr(20, listOf(-1, 1), 0L),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ObservationProgrammePlanner.plan(
                ObservationProgrammePlanner.Request.FocusStack(0.0, 101.0, 2, 0L),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ObservationProgrammePlanner.plan(
                ObservationProgrammePlanner.Request.Panorama(0, 2, 10.0, 5.0, 0L),
            )
        }
    }
}
