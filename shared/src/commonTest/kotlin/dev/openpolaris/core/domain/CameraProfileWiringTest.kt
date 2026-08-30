package dev.openpolaris.core.domain

import dev.openpolaris.core.solver.SolveResult
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the end-to-end wiring for issue #15: a [CameraProfile] is
 * the only thing a caller needs to hand to [SolveTargetProjector]
 * to project a target, and the FoV it carries must directly scale
 * the projected screen position. If this test breaks, the
 * `MainActivity` → `VRActivity` FoV hand-off has regressed.
 */
class CameraProfileWiringTest {

    @Test
    fun polarisEyepieceDrivesProjectorAt60By45() {
        val target = SolveResult(raDeg = 1.0, decDeg = 0.0, confidence = 0.8, matchedStars = 10)
        val field = SolveResult(raDeg = 0.0, decDeg = 0.0, confidence = 0.8, matchedStars = 10)
        val profile = CameraProfile.PolarisEyepiece
        val p = SolveTargetProjector.project(
            target = target,
            field = field,
            fovXDeg = profile.fovXDeg.toDouble(),
            fovYDeg = profile.fovYDeg.toDouble(),
        )
        // x = -(1 * 1) / 60 = -0.0166667
        assertEquals(-1.0 / 60.0, p.x, absoluteTolerance = 1e-9)
        assertEquals(0.0, p.y, absoluteTolerance = 1e-9)
    }

    @Test
    fun widerFovPullsTargetTowardCentre() {
        // Acceptance example for issue #15: fovXDeg=90, target 1° east
        // of field at dec=0 → x = -1/90 = -0.01111.
        val target = SolveResult(raDeg = 1.0, decDeg = 0.0, confidence = 0.8, matchedStars = 10)
        val field = SolveResult(raDeg = 0.0, decDeg = 0.0, confidence = 0.8, matchedStars = 10)
        val profile = CameraProfile(90f, 60f, CameraProfileSource.SENSOR)
        val p = SolveTargetProjector.project(
            target = target,
            field = field,
            fovXDeg = profile.fovXDeg.toDouble(),
            fovYDeg = profile.fovYDeg.toDouble(),
        )
        assertEquals(-1.0 / 90.0, p.x, absoluteTolerance = 1e-9)
        assertEquals(0.0, p.y, absoluteTolerance = 1e-9)
    }

    @Test
    fun narrowerFovPushesTargetFurtherFromCentre() {
        // Same 1° offset, narrower FoV → bigger screen-x magnitude.
        val target = SolveResult(raDeg = 1.0, decDeg = 0.0, confidence = 0.8, matchedStars = 10)
        val field = SolveResult(raDeg = 0.0, decDeg = 0.0, confidence = 0.8, matchedStars = 10)
        val narrow = CameraProfile(30f, 22.5f, CameraProfileSource.OVERRIDE)
        val p = SolveTargetProjector.project(
            target = target,
            field = field,
            fovXDeg = narrow.fovXDeg.toDouble(),
            fovYDeg = narrow.fovYDeg.toDouble(),
        )
        assertEquals(-1.0 / 30.0, p.x, absoluteTolerance = 1e-9)
    }

    @Test
    fun sourceLabelIsIndependentOfProjectionMath() {
        // Two profiles with the same FoV but different sources must
        // produce the same projected position — the source label is
        // metadata, not a coefficient.
        val sensor = CameraProfile(60f, 45f, CameraProfileSource.SENSOR)
        val default = CameraProfile.PolarisEyepiece
        assertEquals(default.fovXDeg, sensor.fovXDeg)
        assertEquals(default.fovYDeg, sensor.fovYDeg)
        // The actual projection call is covered by the other tests;
        // here we only pin that the type preserves both pieces of
        // information without one shadowing the other.
        assertTrue(sensor.source != default.source)
    }
}
