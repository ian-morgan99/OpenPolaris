package dev.openpolaris.core.domain

/**
 * GLSL source for the VR stereo renderer. Lives in `commonMain` (not the
 * Android activity) so the constants and the corrected mapping logic can
 * be cross-checked from a JVM unit test.
 *
 * The vertex shader samples the MJPEG texture at a head-pan-shifted NDC
 * position, not at the raw quad UV. This is the corrected form of the
 * original "barrel on position, raw UV" shader: a flat quad per eye, with
 * the look-around pan baked into `vUV` so the fragment shader samples the
 * source image where the viewer is actually looking.
 *
 * Constants (yaw/pitch clamps and pan amplitude) mirror [CardboardWarp].
 * The JVM test VrStereoShadersTest asserts the two stay in lockstep.
 */
object VrStereoShaders {

    /**
     * GLES 2.0 vertex shader for the stereo renderer.
     *
     * Inputs:
     * - `aPos` is the quad position in NDC ([-1, 1] x [-1, 1]).
     * - `aUV` is the raw texture coordinate ([0, 1] x [0, 1]).
     * - `uEyeOffset` shifts the quad in NDC for IPD (left eye is negative).
     * - `uYawPan` / `uPitchPan` are clamped sensor-driven radian pans.
     *
     * Outputs:
     * - `gl_Position` is a flat quad per eye (no barrel math on the vertex).
     * - `vUV` is the head-pan-shifted texture coordinate, computed in NDC
     *   then mapped back to [0, 1]. This gives a "look around" feel inside
     *   the texture with the GPU doing the per-pixel work.
     */
    const val VERTEX_SHADER_SRC: String = """
        attribute vec2 aPos;
        attribute vec2 aUV;
        uniform mat4 uMVP;
        uniform vec2 uEyeOffset;
        uniform float uYawPan;
        uniform float uPitchPan;
        varying vec2 vUV;
        void main() {
            float yaw = clamp(uYawPan, -0.6, 0.6);
            float pitch = clamp(uPitchPan, -0.5, 0.5);
            vec2 ndc = aUV * 2.0 - 1.0;
            vec2 pan = vec2(tan(yaw), tan(pitch)) * 0.18;
            vUV = (ndc + pan) * 0.5 + 0.5;
            gl_Position = uMVP * vec4(aPos + uEyeOffset, 0.0, 1.0);
        }
    """

    /** GLES 2.0 fragment shader: just samples the texture at `vUV`. */
    const val FRAGMENT_SHADER_SRC: String = """
        precision mediump float;
        varying vec2 vUV;
        uniform sampler2D uTex;
        void main() {
            gl_FragColor = texture2D(uTex, vUV);
        }
    """
}
