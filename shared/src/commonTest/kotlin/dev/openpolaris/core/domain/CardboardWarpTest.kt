package dev.openpolaris.core.domain

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The shader lives in `VRActivity` and can't be executed on JVM, but every
 * magic number and the algebra they drive live in [CardboardWarp] so the
 * VR activity's behavior is testable here.
 */
class CardboardWarpTest {

    private fun assertClose(expected: Float, actual: Float, tol: Float = 1e-5f) {
        assertTrue(
            abs(expected - actual) < tol,
            "expected $expected but got $actual (tol=$tol)",
        )
    }

    @Test fun constants_match_cardboard_v1_spec() {
        // Cardboard 1.x viewer profile: K1 = 0.34, K2 = 0.55. Locking these
        // here so a stray edit to the GLSL shader doesn't drift away from
        // the public spec.
        assertEquals(0.34f, CardboardWarp.K1)
        assertEquals(0.55f, CardboardWarp.K2)
    }

    @Test fun warp_factor_at_center_is_unity() {
        // r2 = 0 -> warp = 1 + 0 + 0 = 1, regardless of K1/K2.
        assertEquals(1f, CardboardWarp.warpFactor(0f))
    }

    @Test fun warp_factor_grows_with_radius() {
        // Lens magnifies pixels near the edge; warp > 1 everywhere except
        // the exact center.
        val inner = CardboardWarp.warpFactor(0.1f)
        val outer = CardboardWarp.warpFactor(0.5f)
        assertTrue(inner > 1f)
        assertTrue(outer > inner, "warp must be monotonic in r^2")
    }

    @Test fun vertex_position_at_center_with_zero_pan_returns_position() {
        // At the screen center, warp = 1, so pos * warp = pos, and
        // (pos + 0) / 1 = pos. No head pan -> no offset.
        val (x, y) = CardboardWarp.vertexPosition(0f to 0f, 0f, 0f)
        assertEquals(0f, x)
        assertEquals(0f, y)
    }

    @Test fun vertex_position_is_invariant_to_lens_at_center() {
        // Two symmetric points at the same distance from center but
        // different angles must end up at the same distance from center
        // after warping (lens is radially symmetric). Pick a non-zero
        // radius where the warp factor > 1 to actually exercise the math.
        val (xa, ya) = CardboardWarp.vertexPosition(0.3f to 0f, 0f, 0f)
        val (xb, yb) = CardboardWarp.vertexPosition(0f to 0.3f, 0f, 0f)
        val ra = kotlin.math.sqrt(xa * xa + ya * ya)
        val rb = kotlin.math.sqrt(xb * xb + yb * yb)
        assertClose(ra, rb, tol = 1e-4f)
    }

    @Test fun head_pan_translates_vertex_position() {
        // At the center, lens is identity. With yaw = 0.5 rad, pan.x = tan(0.5)*0.18.
        val (_, noPanY) = CardboardWarp.vertexPosition(0f to 0f, 0f, 0f)
        assertEquals(0f, noPanY, "yaw should not affect the y axis at center")

        val (xWithYaw, _) = CardboardWarp.vertexPosition(0f to 0f, 0.5f, 0f)
        val expectedX = kotlin.math.tan(0.5f) * CardboardWarp.PAN_AMPLITUDE
        assertClose(expectedX, xWithYaw, tol = 1e-5f)
    }

    @Test fun yaw_is_clamped_to_max() {
        // Raw sensor value of 2.0 rad must clamp to MAX_YAW (0.6 rad).
        // A sub-max input (0.5 rad) should produce a *smaller* pan than a
        // value past the clamp.
        val (xBelow, _) = CardboardWarp.vertexPosition(0f to 0f, 0.5f, 0f)
        val (xClamped, _) = CardboardWarp.vertexPosition(0f to 0f, 2.0f, 0f)
        val expectedClamped = kotlin.math.tan(CardboardWarp.MAX_YAW) * CardboardWarp.PAN_AMPLITUDE
        assertClose(expectedClamped, xClamped, tol = 1e-5f)
        assertTrue(xClamped > xBelow, "clamped pan should exceed sub-max pan")
        assertEquals(CardboardWarp.MAX_YAW, CardboardWarp.clampYaw(2.0f))
    }

    @Test fun pitch_is_clamped_to_max() {
        assertEquals(CardboardWarp.MAX_PITCH, CardboardWarp.clampPitch(1.5f))
        assertEquals(-CardboardWarp.MAX_PITCH, CardboardWarp.clampPitch(-1.5f))
    }

    @Test fun corrected_uv_pans_image_in_texture_space() {
        // The corrected sampler should add the same pan as the vertex
        // shader at the center, where the lens is the identity.
        val (uNoPan, vNoPan) = CardboardWarp.correctedUv(0f to 0f, 0f, 0f)
        assertEquals(0f, uNoPan)
        assertEquals(0f, vNoPan)

        val (uWithPan, vWithPan) = CardboardWarp.correctedUv(0.2f to 0.1f, 0.3f, -0.2f)
        val expectedU = 0.2f + kotlin.math.tan(0.3f) * CardboardWarp.PAN_AMPLITUDE
        val expectedV = 0.1f - kotlin.math.tan(0.2f) * CardboardWarp.PAN_AMPLITUDE
        assertClose(expectedU, uWithPan)
        assertClose(expectedV, vWithPan)
    }
}
