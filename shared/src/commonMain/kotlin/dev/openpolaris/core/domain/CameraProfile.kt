package dev.openpolaris.core.domain

/**
 * Where the FoV numbers in a [CameraProfile] came from. The render-side
 * code uses this to show the user *why* a particular FoV is in use, and
 * to decide which values to update when a new measurement arrives.
 *
 * - [SENSOR] — the live FoV was read from the connected mount's sensor
 *   (or, in a follow-up, streamed from a separate camera-control path).
 *   This is the "honest" path: the marker is being projected against the
 *   actual sensor the mount is imaging.
 * - [PER_MOUNT_DEFAULT] — the mount does not yet expose a sensor FoV, so
 *   we are using a per-mount default the device profile is expected to
 *   provide once a real camera profile lands. Until then the eyepiece
 *   default ([CameraProfile.PolarisEyepiece]) is the most honest answer.
 * - [OVERRIDE] — a user/operator explicitly overrode the FoV (e.g. via a
 *   "use a wider lens" toggle in the settings panel). Render-side code
 *   should respect the override and not silently revert to the default.
 */
enum class CameraProfileSource { SENSOR, PER_MOUNT_DEFAULT, OVERRIDE }

/**
 * The horizontal and vertical full-FoV of the camera currently feeding
 * the marker projection, plus a [source] label so downstream code can
 * tell *why* those values are in use.
 *
 * ## Why a value class isn't enough
 *
 * A pair of `Float`s would carry the math but would lose the
 * provenance. Knowing whether the FoV came from a sensor reading or a
 * hard-coded default matters when a future test asserts "the marker is
 * using a SENSOR value" or "the override is honoured when set". The
 * [source] is therefore a first-class part of the type, not an
 * out-of-band tag.
 *
 * ## Why the per-mount default lives here
 *
 * The mount's actual sensor FoV is not yet exposed by the protocol
 * (issue #15 lists this as a follow-up). The values used by today's
 * [CameraProfile.PolarisEyepiece] are the Polaris eyepiece profile —
 * the same 60x45 that the test fixtures and the VRActivity's
 * pre-#15 hard-coded defaults used. Centralising them here means a
 * future per-mount profile (e.g. `CameraProfile.PolarisWideField`) can
 * be added by extending this companion object without touching every
 * call site.
 *
 * ## Validation
 *
 * Both FoV values must be strictly positive. A zero or negative FoV is
 * meaningless to [SolveTargetProjector] (it `require`s `>0.0`), so the
 * type refuses to carry one rather than letting it surface as a runtime
 * crash inside the projection.
 */
data class CameraProfile(
    val fovXDeg: Float,
    val fovYDeg: Float,
    val source: CameraProfileSource,
) {
    init {
        require(fovXDeg > 0f) { "fovXDeg must be >0, was $fovXDeg" }
        require(fovYDeg > 0f) { "fovYDeg must be >0, was $fovYDeg" }
    }

    companion object {
        /**
         * Polaris eyepiece profile — the values used historically by
         * VRActivity's pre-#15 `DEFAULT_FOV_X_DEG` / `DEFAULT_FOV_Y_DEG`
         * constants and by the [SolveTargetProjectorTest] math fixtures.
         *
         * Tagged [CameraProfileSource.PER_MOUNT_DEFAULT] because the
         * mount does not yet report its own sensor FoV. When a real
         * sensor stream lands, callers will publish a new
         * [CameraProfile] with [CameraProfileSource.SENSOR] and the
         * default stops being the active value.
         */
        val PolarisEyepiece: CameraProfile =
            CameraProfile(fovXDeg = 60f, fovYDeg = 45f, source = CameraProfileSource.PER_MOUNT_DEFAULT)
    }
}
