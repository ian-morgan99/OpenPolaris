package dev.openpolaris.core.domain

import kotlin.math.PI
import kotlin.math.abs

/**
 * Stream 7.5 - in-VR recenter affordance.
 *
 * A small, JVM-testable seam for the math behind "the user's current head
 * pose becomes the new origin." When the user nudges the viewer slightly
 * off-centre and then presses the recenter key, the view must not jump -
 * the rendered scene should look identical before and after the press,
 * but the underlying sensor pose that maps to "looking straight ahead"
 * must update.
 *
 * That guarantee is: effective(raw) == effective(recenterAfter(raw, raw)).
 *
 * Mechanism:
 *   - The renderer stores the offset (the raw sensor pose at recenter
 *     time) and the current raw pose (from the sensor listener).
 *   - The "effective" pose - the one passed to the eye shader's pan and to
 *     MarkerWarp.warpNdc - is raw - offset per axis.
 *   - On recenter, the renderer snapshots the current raw pose and stores
 *     it as the new offset. After that, effective(raw) == 0.
 *
 * Why a class (not an object): the offset is mutable state. Wrapping
 * it in a tiny class with explicit recenter(rawYaw, rawPitch) and
 * effective(rawYaw, rawPitch) methods lets the StereoRenderer own one
 * instance, lets a JVM test own one instance, and keeps the
 * publication/threading story identical to the @Volatile yaw, pitch
 * pattern the renderer already uses.
 *
 * Clamping policy. Sensor noise on a phone IMU routinely produces
 * raw yaw/pitch values that are 5-10 degrees off the user's intended
 * centre. Without clamping, a recenter pressed while looking slightly
 * off-axis would freeze that off-axis pose as the new origin and the
 * next frame would pan hard to compensate. We clamp the offset to
 * +/- 10 degrees (well inside CardboardWarp.MAX_YAW / MAX_PITCH of 0.6
 * rad ~ 34 deg) so a recenter cannot push the effective pose to the
 * very edge of the FoV. The clamp is a safety bound, not a soft
 * recenter: if the user is genuinely looking 30 deg off-axis and
 * presses recenter, the offset is capped at 10 deg and the next frame
 * still pans 20 deg to centre. That preserves the contract that the
 * view does not jump.
 */
class RecenterMath(
    /** Maximum absolute value of the recenter offset, in radians. */
    private val maxOffsetRad: Float = MAX_OFFSET_RAD,
) {
    /** @Volatile equivalent: read on the GL thread, written on the UI thread. */
    @kotlin.concurrent.Volatile var yawOffset: Float = 0f
        private set
    @kotlin.concurrent.Volatile var pitchOffset: Float = 0f
        private set

    /**
     * Record rawYawRad / rawPitchRad as the new origin. After this
     * returns, effective of those same raw values is (0, 0). The
     * offset is clamped to +/- maxOffsetRad on each axis.
     */
    fun recenter(rawYawRad: Float, rawPitchRad: Float) {
        yawOffset = rawYawRad.coerceIn(-maxOffsetRad, maxOffsetRad)
        pitchOffset = rawPitchRad.coerceIn(-maxOffsetRad, maxOffsetRad)
    }

    /**
     * The pose the eye shader and marker should use, given the latest
     * raw pose from the sensor. Equals raw - offset per axis. The
     * caller is still responsible for the eye-shader's own
     * coerceIn(-MAX_YAW, +MAX_YAW) clamp downstream; this method does
     * not re-clamp.
     */
    fun effective(rawYawRad: Float, rawPitchRad: Float): Pair<Float, Float> =
        (rawYawRad - yawOffset) to (rawPitchRad - pitchOffset)

    /**
     * True iff a recenter would have a user-visible effect right now
     * - i.e. the current raw pose is not already inside the
     * "essentially centred" radius on either axis. Used to debounce
     * haptic-style cues (a transient toast) so the user does not see
     * "Recentered" flash every time the button is pressed.
     */
    fun recenterWouldChange(rawYawRad: Float, rawPitchRad: Float): Boolean {
        val (ey, ep) = effective(rawYawRad, rawPitchRad)
        val radius = RECENTER_EPSILON_RAD
        return abs(ey) > radius || abs(ep) > radius
    }

    companion object {
        /** ~10 deg. Comfortably inside the Cardboard shader's 34 deg / 28.6 deg clamps. */
        const val MAX_OFFSET_RAD: Float = 10f * (PI.toFloat() / 180f)
        /** "Already centred" radius for debouncing the recenter toast. */
        const val RECENTER_EPSILON_RAD: Float = 0.5f * (PI.toFloat() / 180f)
    }
}
