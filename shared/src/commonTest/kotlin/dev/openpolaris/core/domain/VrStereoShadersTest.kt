package dev.openpolaris.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The GLSL source is a `const val` String, so the JVM can read it. We can't
 * actually compile or execute the shader on the JVM (no GLES, no driver),
 * but we *can* assert the literals the shader depends on match
 * [CardboardWarp]'s constants. This is the proxy for "the shader and the
 * reference math agree".
 *
 * If a future refactor changes a clamp in the shader without updating
 * [CardboardWarp] (or vice versa), these tests fail and the drift is
 * caught in CI without needing a device.
 */
class VrStereoShadersTest {

    @Test fun vertex_shader_pan_amplitude_matches_cardboard_warp() {
        // The shader's "0.18" pan amplitude must equal CardboardWarp.PAN_AMPLITUDE.
        assertTrue(
            VrStereoShaders.VERTEX_SHADER_SRC.contains("* 0.18"),
            "vertex shader must scale the head pan by 0.18 (CardboardWarp.PAN_AMPLITUDE)",
        )
        assertEquals(0.18f, CardboardWarp.PAN_AMPLITUDE)
    }

    @Test fun vertex_shader_yaw_clamp_matches_cardboard_warp() {
        // The shader clamps yaw to [-0.6, 0.6], which must equal CardboardWarp.MAX_YAW.
        assertTrue(
            VrStereoShaders.VERTEX_SHADER_SRC.contains("clamp(uYawPan, -0.6, 0.6)"),
            "vertex shader must clamp uYawPan to [-0.6, 0.6]",
        )
        assertEquals(0.6f, CardboardWarp.MAX_YAW)
    }

    @Test fun vertex_shader_pitch_clamp_matches_cardboard_warp() {
        // The shader clamps pitch to [-0.5, 0.5], which must equal CardboardWarp.MAX_PITCH.
        assertTrue(
            VrStereoShaders.VERTEX_SHADER_SRC.contains("clamp(uPitchPan, -0.5, 0.5)"),
            "vertex shader must clamp uPitchPan to [-0.5, 0.5]",
        )
        assertEquals(0.5f, CardboardWarp.MAX_PITCH)
    }

    @Test fun vertex_shader_drops_barrel_on_position() {
        // Plan-4.1 fix: the original shader applied K1/K2 warp to gl_Position
        // but sampled with raw aUV, giving flat output. The corrected shader
        // must NOT reference the K1/K2 magic numbers — the pan lives in vUV
        // and the quad is flat.
        val src = VrStereoShaders.VERTEX_SHADER_SRC
        assertTrue(!src.contains("0.34"), "vertex shader must not contain the K1 magic number (0.34)")
        assertTrue(!src.contains("0.55"), "vertex shader must not contain the K2 magic number (0.55)")
    }

    @Test fun vertex_shader_computes_vuv_in_pan_shifted_ndc() {
        // The corrected mapping: convert aUV ([0,1]) to NDC, add the pan
        // (tan(yaw), tan(pitch)) * 0.18, then map back to [0, 1]. If this
        // exact expression disappears, the look-around feel breaks.
        val src = VrStereoShaders.VERTEX_SHADER_SRC
        assertTrue(src.contains("aUV * 2.0 - 1.0"), "vUV must convert aUV to NDC first")
        assertTrue(src.contains("vUV = (ndc + pan) * 0.5 + 0.5"), "vUV must be the NDC-pan mapping back to [0,1]")
    }

    @Test fun fragment_shader_samples_vuv_unchanged() {
        // The fragment shader is a plain pass-through sampler. No changes
        // needed beyond the vertex shader's corrected vUV.
        val src = VrStereoShaders.FRAGMENT_SHADER_SRC
        assertTrue(src.contains("gl_FragColor = texture2D(uTex, vUV)"))
    }

    @Test fun vertex_shader_uses_eye_offset_for_ipd() {
        // IPD shift: the quad is offset in NDC for the right eye. If the
        // eye offset is dropped, both eyes see the same half-screen image
        // and the stereo effect is gone.
        val src = VrStereoShaders.VERTEX_SHADER_SRC
        assertTrue(src.contains("aPos + uEyeOffset"), "vertex shader must offset aPos by uEyeOffset for IPD")
    }
}
