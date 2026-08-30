package dev.openpolaris.core.domain

/**
 * Routes the solve marker through the same warp the VR eye textures go
 * through, so the marker lands where the user actually perceives it in the
 * headset rather than at a flat NDC point.
 *
 * The marker's *input* position is the flat NDC coordinate that
 * `SolveTargetProjector` (or a fallback) computes in the [-1, +1] eye
 * viewport. That is the same coordinate space the eye-quad vertex shader
 * feeds into [CardboardWarp.vertexPosition] when rendering the per-eye
 * texture. Applying [vertexPosition] here keeps the marker in lockstep with
 * the eye shader: turn barrel distortion on/off in the eye shader and the
 * marker tracks identically, because both share the same warp math.
 *
 * The class is a thin, JVM-testable seam in front of [CardboardWarp] so
 * `VRActivity` doesn't have to know about the lens coefficients directly
 * and so the lockstep can be asserted in unit tests.
 */
object MarkerWarp {
    /**
     * The NDC position to draw the solve marker at, given a flat NDC input
     * and the current head pose. The math is identical to
     * [CardboardWarp.vertexPosition]; the dedicated function exists so the
     * call site in `VRActivity` reads as "warp the marker" rather than
     * "warp an arbitrary vertex".
     */
    fun warpNdc(
        markerNdc: Pair<Float, Float>,
        yawRad: Float,
        pitchRad: Float,
    ): Pair<Float, Float> = CardboardWarp.vertexPosition(markerNdc, yawRad, pitchRad)
}
