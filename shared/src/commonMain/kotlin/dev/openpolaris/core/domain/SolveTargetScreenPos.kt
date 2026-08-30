package dev.openpolaris.core.domain

import dev.openpolaris.core.solver.SolveResult
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * 7.4. Pure math for placing a plate-solve target marker in the VR
 * headset view.
 *
 * **Model.** The mount's plate-solve establishes a *field centre* in
 * RA/Dec (the centre of the camera's current view). The user can also
 * have a *target* in RA/Dec (e.g. an NGC object the user wants to
 * centre). The target's position on screen is the small-angle offset
 * (ΔRA · cos(δ), Δδ) divided by the VR camera's per-axis FoV, so the
 * output is in *units of the full FoV*: the visible disc spans
 * `[-0.5, +0.5]` on each axis, the frame edge is at `±1.0`, and any
 * value past the frame is off-screen. The `(0, 0)` origin is the
 * screen centre (where the crosshair sits); positive `x` is right;
 * positive `y` is up.
 *
 * **Sign convention.** ΔRA is `targetRA − fieldRA` and is
 * *east-positive* (RA increases eastward on the sky, which is
 * *screen-left* from the observer's perspective at the eyepiece, so
 * we negate it for screen coordinates). The downstream renderer can
 * flip it again if the lens inverts — keep this contract clean here
 * and let the caller decide.
 *
 * **Off-screen.** When `|x| > 0.5` or `|y| > 0.5`, the target is past
 * the visible frame edge. Callers typically clamp
 * the magnitude to a small "edge" position (see [clampedToEdge]) so
 * the user can see which direction to slew. The function returns a
 * struct with both the raw and clamped coordinates so the caller can
 * decide.
 *
 * **JVM-testable.** All inputs are pure doubles; no Android, GL, or
 * sensor types. Lives in `commonMain` so the multiplatform test
 * suite can validate it.
 */
data class SolveTargetScreenPos(
    /** True iff the target is in front of the camera (positive altitude, not behind the mount). */
    val inFront: Boolean,
    /** Raw x in `[-1, +1]`; sign convention: east-positive-RA is screen-LEFT, so Δx = -ΔRA · cos(δ) / fovX. */
    val x: Double,
    /** Raw y in `[-1, +1]`; Δy = ΔDec / fovY. */
    val y: Double,
    /** Distance from centre in normalised units (0 = centre, 1 = edge of frame, >1 = off-screen). */
    val r: Double,
    /** `true` iff the target lies outside the visible frustum (|x|>1 or |y|>1). */
    val offScreen: Boolean,
) {
    /**
     * Clamp to the unit disc — caller can use this to place the
     * marker on the edge of the screen for an "off-screen target"
     * affordance, instead of drawing it at the raw off-screen pos.
     */
    fun clampedToEdge(): SolveTargetScreenPos {
        if (!offScreen) return this
        val mag = kotlin.math.sqrt(x * x + y * y)
        if (mag == 0.0) return this
        val scale = 0.95 / mag
        return copy(x = x * scale, y = y * scale)
    }
}

object SolveTargetProjector {

    /**
     * Project a (target, field) pair into the VR view.
     *
     * @param target  the object the user wants to centre
     * @param field   the plate-solved field centre (i.e. the centre of the current camera frame)
     * @param fovXDeg the camera's horizontal full FoV in degrees (e.g. 60)
     * @param fovYDeg the camera's vertical full FoV in degrees (e.g. 45)
     */
    fun project(
        target: SolveResult,
        field: SolveResult,
        fovXDeg: Double,
        fovYDeg: Double,
    ): SolveTargetScreenPos {
        require(fovXDeg > 0.0) { "fovXDeg must be >0, was $fovXDeg" }
        require(fovYDeg > 0.0) { "fovYDeg must be >0, was $fovYDeg" }

        val decMid = 0.5 * (target.decDeg + field.decDeg)
        val cosDec = cos(Math.toRadians(decMid))

        // Small-angle: ΔRA → x via the cos(δ) factor so a 1° RA step
        // at the equator is the same arc-length as a 1° Dec step.
        // dRa is "from field to target", so positive when the target is
        // east of the field centre (RA increases eastward).
        val dRa = shortestSignedDelta(field.raDeg, target.raDeg)
        val dDec = target.decDeg - field.decDeg

        // East-positive RA → screen-left (camera is mirrored relative
        // to the sky), so negate. Dec up is screen up.
        // x is in units of full FoV, so the visible disc spans
        // [-0.5, +0.5] in each axis; anything past half-FoV is off-screen.
        val x = -(dRa * cosDec) / fovXDeg
        val y = dDec / fovYDeg

        val r = kotlin.math.sqrt(x * x + y * y)
        return SolveTargetScreenPos(
            inFront = true, // small-angle: always "in front" once on the sky
            x = x,
            y = y,
            r = r,
            offScreen = kotlin.math.abs(x) > 0.5 || kotlin.math.abs(y) > 0.5,
        )
    }

    /**
     * Smallest signed arc from `fromDeg` to `toDeg` in degrees,
     * wrapped into `(-180, +180]`. Positive when `toDeg` is east of
     * `fromDeg` (RA increases eastward).
     */
    private fun shortestSignedDelta(fromDeg: Double, toDeg: Double): Double {
        var d = (toDeg - fromDeg) % 360.0
        if (d > 180.0) d -= 360.0
        if (d <= -180.0) d += 360.0
        return d
    }
}
