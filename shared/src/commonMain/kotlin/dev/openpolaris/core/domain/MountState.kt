package dev.openpolaris.core.domain

import dev.openpolaris.core.protocol.ResponseParser

/** Mount operating mode reported by 284. */
enum class MountMode { PANORAMA, TIMELAPSE, ASTRO, UNKNOWN }

/** High-level mount state derived from 284 pushes and command results. */
data class MountState(
    val connected: Boolean = false,
    val mode: MountMode = MountMode.UNKNOWN,
    val batteryPercent: Int? = null,
    val charging: Boolean = false,
    val tracking: Boolean? = null,
    val halfSpeed: Boolean = false,
    val ahrsEnabled: Boolean = false,
    /**
     * Human-readable message of the last protocol-level error observed
     * by [MountSession], or null. Stored as a string on the flow type
     * so the top-level [MountState] has no compile-time dependency on
     * [MountSession.CmdResult]. Cleared on a successful
     * [MountSession.connect]. Use [MountSession.lastError] for the
     * typed `CmdResult?` view (PLAN-CRITICAL-REVIEW §H).
     */
    val lastErrorMessage: String? = null,
) {
    companion object {
        /** Derive state from a 284 push frame. Tolerant of missing fields. */
        fun fromFrame284(f: ResponseParser.Frame): MountState {
            // Mode is an int; observed values map to app modes. Unknown stays UNKNOWN.
            val mode = when (f.int("mode")) {
                0 -> MountMode.PANORAMA
                1 -> MountMode.TIMELAPSE
                2 -> MountMode.ASTRO
                else -> MountMode.UNKNOWN
            }
            val batt = f.int("battery")
            return MountState(
                connected = true,
                mode = mode,
                batteryPercent = batt?.takeIf { it in 0..100 },
                charging = (f.int("charge") ?: 0) != 0,
                tracking = f.int("track")?.let { it != 0 },
                halfSpeed = (f.int("halfSpeed") ?: 0) != 0,
                ahrsEnabled = (f.int("ahrs") ?: 0) != 0,
            )
        }
    }
}

/** Gimbal position in degrees, as read from 517. */
data class GimbalPosition(val yaw: Float, val pitch: Float, val roll: Float) {
    companion object {
        fun fromFrame517(f: ResponseParser.Frame): GimbalPosition? {
            val y = f.float("yaw") ?: return null
            val p = f.float("pitch") ?: return null
            val r = f.float("roll") ?: return null
            return GimbalPosition(y, p, r)
        }
    }
}
