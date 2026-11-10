package dev.openpolaris.core.domain

/**
 * User-facing classification of the shutter option read from the camera.
 *
 * The wire value is model-dependent and may be an index elsewhere in the
 * protocol; this classifier only interprets the option label when one has
 * already been positively read back. Unknown is deliberately distinct from a
 * normal timed/manual release so the UI cannot offer a Bulb workflow on a
 * missing or stale option list.
 */
enum class CameraReleaseMode(val label: String) {
    MANUAL("M / manual"),
    BULB("B / bulb"),
    OTHER("Timed / other"),
    UNKNOWN("Unknown");

    companion object {
        fun fromOption(option: String?): CameraReleaseMode = when (option?.trim()?.lowercase()) {
            "m", "manual" -> MANUAL
            "b", "bulb" -> BULB
            null, "" -> UNKNOWN
            else -> OTHER
        }
    }
}
