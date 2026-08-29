package dev.openpolaris.core.domain

import kotlin.math.tan

/**
 * Cardboard v1 barrel-distortion coefficients + the pan-clamp constants
 * used by `VRActivity.StereoRenderer`. The actual GLSL shader lives in the
 * Android activity; the JVM-side math here is a reference implementation
 * kept in lockstep with the shader so the constants can be unit-tested.
 *
 * Important: the GLSL shader applies the warp to the *vertex position* but
 * the texture UV (`vUV = aUV`) is the raw, unwarped quad. That means the
 * shipped code currently shows the source image twice without lens
 * correction; the in-plane "head pan" only moves the quad and reveals the
 * texture's clamp-to-edge pixels. To get real barrel correction the shader
 * must instead set `vUV = correctedUv(aPos + uEyeOffset, yaw, pitch)`.
 * See the issue tracker entry "VR: switch to UV-space barrel" (vr-uvwarp).
 */
object CardboardWarp {
    const val K1: Float = 0.34f
    const val K2: Float = 0.55f
    const val PAN_AMPLITUDE: Float = 0.18f
    const val MAX_YAW: Float = 0.6f
    const val MAX_PITCH: Float = 0.5f

    /** Forward-warp factor for an NDC position with squared radius `r2`. */
    fun warpFactor(r2: Float): Float = 1f + K1 * r2 + K2 * r2 * r2

    /**
     * What the GLSL vertex shader computes for `gl_Position.xy` at a given
     * post-eye-offset NDC point, given a clamped head yaw/pitch.
     *
     * `gl_Position.xy = ((pos * warp) + pan) / warp`, where `warp` is the
     * forward barrel factor and `pan = (tan(yaw), tan(pitch)) * 0.18`.
     */
    fun vertexPosition(posNdc: Pair<Float, Float>, yawRad: Float, pitchRad: Float): Pair<Float, Float> {
        val (x, y) = posNdc
        val yaw = clampYaw(yawRad)
        val pitch = clampPitch(pitchRad)
        val r2 = x * x + y * y
        val warp = warpFactor(r2)
        val warpedX = x * warp
        val warpedY = y * warp
        val panX = tan(yaw) * PAN_AMPLITUDE
        val panY = tan(pitch) * PAN_AMPLITUDE
        val outX = (warpedX + panX) / warp
        val outY = (warpedY + panY) / warp
        return outX to outY
    }

    /**
     * What `vUV` *should* be set to in a corrected shader. Currently the
     * shipped shader sets `vUV = aPos` (no warp, no pan); switching to this
     * function gives real Cardboard-style barrel-corrected sampling.
     */
    fun correctedUv(posNdc: Pair<Float, Float>, yawRad: Float, pitchRad: Float): Pair<Float, Float> {
        val (x, y) = posNdc
        val yaw = clampYaw(yawRad)
        val pitch = clampPitch(pitchRad)
        val panX = tan(yaw) * PAN_AMPLITUDE
        val panY = tan(pitch) * PAN_AMPLITUDE
        return (x + panX) to (y + panY)
    }

    /** Clamp a raw sensor yaw to the VR activity's allowed range. */
    fun clampYaw(raw: Float): Float = raw.coerceIn(-MAX_YAW, MAX_YAW)

    /** Clamp a raw sensor pitch to the VR activity's allowed range. */
    fun clampPitch(raw: Float): Float = raw.coerceIn(-MAX_PITCH, MAX_PITCH)
}
