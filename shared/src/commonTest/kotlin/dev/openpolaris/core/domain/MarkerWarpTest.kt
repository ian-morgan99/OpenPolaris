package dev.openpolaris.core.domain

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Lockstep tests for the solve-marker warp. See [MarkerWarp] for why the
 * marker has to go through the same [CardboardWarp] math as the eye
 * shader, and the issue #16 acceptance invariant ("Removing barrel
 * distortion from the eye textures does NOT change where the marker
 * lands").
 */
class MarkerWarpTest {

    private fun assertClose(expected: Float, actual: Float, tol: Float = 1e-5f) {
        assertTrue(
            abs(expected - actual) < tol,
            "expected $expected but got $actual (tol=$tol)",
        )
    }

    @Test fun marker_at_center_with_zero_pose_is_identity() {
        val (x, y) = MarkerWarp.warpNdc(0f to 0f, 0f, 0f)
        assertEquals(0f, x)
        assertEquals(0f, y)
    }

    @Test fun marker_at_edge_with_zero_pose_is_identity() {
        // Off-center but no head pan -> the lens is a pure magnification
        // for the same radial line, so the warped point equals the input.
        val (x, y) = MarkerWarp.warpNdc(0.5f to 0.25f, 0f, 0f)
        assertEquals(0.5f, x)
        assertEquals(0.25f, y)
    }

    @Test fun marker_locks_to_eye_shader_vertex_position() {
        // The marker's perceived position must equal the eye shader's
        // vertex-stage position for the same input. This is the lockstep
        // the issue requires: any change to CardboardWarp moves the eye
        // and the marker together.
        val inputs = listOf(
            Triple(0f to 0f, 0f, 0f),
            Triple(0.5f to 0f, 0.3f, 0f),
            Triple(0f to 0.4f, 0f, -0.2f),
            Triple(-0.3f to 0.3f, 0.6f, 0.5f),
            Triple(0.7f to -0.6f, -0.6f, -0.5f),
        )
        for ((pos, yaw, pitch) in inputs) {
            val (mx, my) = MarkerWarp.warpNdc(pos, yaw, pitch)
            val (ex, ey) = CardboardWarp.vertexPosition(pos, yaw, pitch)
            assertEquals(ex, mx, "marker x must equal eye vertex x for $pos, yaw=$yaw, pitch=$pitch")
            assertEquals(ey, my, "marker y must equal eye vertex y for $pos, yaw=$yaw, pitch=$pitch")
        }
    }

    @Test fun marker_at_center_tracks_head_pan_exactly() {
        // At the optical axis, warp=1, so the warped position is the
        // input + head pan. The eye shader's vertex math agrees.
        val yaw = 0.4f
        val pitch = -0.3f
        val (mx, my) = MarkerWarp.warpNdc(0f to 0f, yaw, pitch)
        val expectedX = kotlin.math.tan(yaw) * CardboardWarp.PAN_AMPLITUDE
        val expectedY = kotlin.math.tan(pitch) * CardboardWarp.PAN_AMPLITUDE
        assertClose(expectedX, mx)
        assertClose(expectedY, my)
    }

    @Test fun marker_yaw_pan_is_clamped() {
        val (xMid, _) = MarkerWarp.warpNdc(0f to 0f, CardboardWarp.MAX_YAW - 0.05f, 0f)
        val (xWild, _) = MarkerWarp.warpNdc(0f to 0f, 5f, 0f)
        val expectedClamped = kotlin.math.tan(CardboardWarp.MAX_YAW) * CardboardWarp.PAN_AMPLITUDE
        assertClose(expectedClamped, xWild, tol = 1e-5f)
        assertTrue(xWild > xMid, "clamped pan must not exceed sub-max pan")
    }

    @Test fun marker_pitch_pan_is_clamped() {
        val (_, yWildPos) = MarkerWarp.warpNdc(0f to 0f, 0f, 3f)
        val (_, yWildNeg) = MarkerWarp.warpNdc(0f to 0f, 0f, -3f)
        val expectedUp = kotlin.math.tan(CardboardWarp.MAX_PITCH) * CardboardWarp.PAN_AMPLITUDE
        val expectedDown = -expectedUp
        assertClose(expectedUp, yWildPos, tol = 1e-5f)
        assertClose(expectedDown, yWildNeg, tol = 1e-5f)
    }
}
