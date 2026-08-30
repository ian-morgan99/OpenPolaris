package dev.openpolaris.core.domain

import dev.openpolaris.core.solver.SolveResult
import kotlin.math.abs
import kotlin.math.cos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val eps = 1e-9

class SolveTargetProjectorTest {

    private fun solve(ra: Double, dec: Double, confidence: Double = 0.9) =
        SolveResult(raDeg = ra, decDeg = dec, confidence = confidence, matchedStars = 12)

    @Test
    fun targetAtFieldCentreRendersAtOrigin() {
        val p = SolveTargetProjector.project(
            target = solve(ra = 180.0, dec = 45.0),
            field = solve(ra = 180.0, dec = 45.0),
            fovXDeg = 60.0,
            fovYDeg = 45.0,
        )
        assertEquals(0.0, p.x, eps)
        assertEquals(0.0, p.y, eps)
        assertEquals(0.0, p.r, eps)
        assertFalse(p.offScreen)
    }

    @Test
    fun targetEastOfFieldAppearsOnLeftHalf() {
        // target is 10° east of field (RA 190 vs 180) at dec 45
        // At dec=45, cos(45) ≈ 0.707. So x = -10 * 0.707 / 60 = -0.118
        val p = SolveTargetProjector.project(
            target = solve(ra = 190.0, dec = 45.0),
            field = solve(ra = 180.0, dec = 45.0),
            fovXDeg = 60.0,
            fovYDeg = 45.0,
        )
        assertTrue(p.x < 0.0, "east-RA target should be screen-left (x<0), was ${p.x}")
        assertEquals(-10.0 * cos(Math.toRadians(45.0)) / 60.0, p.x, 1e-9)
    }

    @Test
    fun targetNorthOfFieldAppearsAbove() {
        // target is 5° north (dec 50 vs 45)
        val p = SolveTargetProjector.project(
            target = solve(ra = 180.0, dec = 50.0),
            field = solve(ra = 180.0, dec = 45.0),
            fovXDeg = 60.0,
            fovYDeg = 45.0,
        )
        assertEquals(5.0 / 45.0, p.y, 1e-9)
        assertEquals(0.0, p.x, 1e-9)
    }

    @Test
    fun targetSouthOfFieldAppearsBelow() {
        val p = SolveTargetProjector.project(
            target = solve(ra = 180.0, dec = 40.0),
            field = solve(ra = 180.0, dec = 45.0),
            fovXDeg = 60.0,
            fovYDeg = 45.0,
        )
        assertEquals(-5.0 / 45.0, p.y, 1e-9)
    }

    @Test
    fun raWraparoundGoesTheShortWay() {
        // Field at RA 355, target at RA 5. The sky-distance is 10°
        // eastward, NOT 350° westward.
        val p = SolveTargetProjector.project(
            target = solve(ra = 5.0, dec = 0.0),
            field = solve(ra = 355.0, dec = 0.0),
            fovXDeg = 60.0,
            fovYDeg = 45.0,
        )
        // At dec=0, cos(0)=1, so dRa = 10, x = -10/60 = -0.1667
        assertTrue(p.x < 0.0, "RA 5 is east of RA 355 → screen-left, x=${p.x}")
        assertEquals(-10.0 / 60.0, p.x, 1e-9)
    }

    @Test
    fun farTargetIsMarkedOffScreen() {
        val p = SolveTargetProjector.project(
            target = solve(ra = 240.0, dec = 45.0),
            field = solve(ra = 180.0, dec = 45.0),
            fovXDeg = 60.0,
            fovYDeg = 45.0,
        )
        assertTrue(p.offScreen)
        // dRa = 60, cos(45)=0.707, x = -60*0.707/60 = -0.707
        assertEquals(-cos(Math.toRadians(45.0)), p.x, 1e-9)
    }

    @Test
    fun clampedToEdgeSnapsFarTargetToVisibleDisc() {
        val p = SolveTargetProjector.project(
            target = solve(ra = 300.0, dec = 0.0),
            field = solve(ra = 180.0, dec = 0.0),
            fovXDeg = 60.0,
            fovYDeg = 45.0,
        )
        assertTrue(p.offScreen)
        val e = p.clampedToEdge()
        val r = kotlin.math.sqrt(e.x * e.x + e.y * e.y)
        assertTrue(r <= 0.96, "clamped r should be on the disc, was $r")
    }

    @Test
    fun decCompensatesRaAtHighDeclination() {
        // At dec=80, cos(80)≈0.174. So a 10° RA step projects to a
        // much smaller screen offset than at the equator.
        val pEq = SolveTargetProjector.project(
            target = solve(ra = 190.0, dec = 0.0),
            field = solve(ra = 180.0, dec = 0.0),
            fovXDeg = 60.0,
            fovYDeg = 45.0,
        )
        val pPole = SolveTargetProjector.project(
            target = solve(ra = 190.0, dec = 80.0),
            field = solve(ra = 180.0, dec = 80.0),
            fovXDeg = 60.0,
            fovYDeg = 45.0,
        )
        assertTrue(abs(pEq.x) > abs(pPole.x),
            "near equator |x| should be larger than near pole, got ${pEq.x} vs ${pPole.x}")
    }
}
