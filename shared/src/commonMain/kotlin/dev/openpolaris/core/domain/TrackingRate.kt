package dev.openpolaris.core.domain

/**
 * Tracking rate selectable on the mount (code 531 `speed:` field).
 *
 * The firmware format string is `state:%d;speed:%d;` (recovered from
 * polestar_app). Per the ogecko/alpaca driver and the Benro app, the
 * `speed` index maps to:
 *
 * - [SIDEREAL] = 0 — sidereal rate, for star imaging. This is the default
 *   the firmware uses when no `speed:` field is present.
 * - [LUNAR] = 2 — lunar rate (~0.966× sidereal), for tracking the Moon.
 *
 * The Benro app exposes star/sun/moon rates; only sidereal (0) and lunar (2)
 * are hardware-verified on Polaris, so this enum is deliberately limited to
 * those two. See docs/PROTOCOL.md §531 and docs/SMOKE-TEST.md §1.7–1.8.
 */
enum class TrackingRate(val speedIndex: Int, val label: String) {
    /** Sidereal rate — correct for star imaging (the default). */
    SIDEREAL(0, "Sidereal"),

    /** Lunar rate (~0.966× sidereal) — for tracking the Moon. */
    LUNAR(2, "Lunar");

    companion object {
        /** The rate the firmware defaults to when no `speed:` field is sent. */
        val DEFAULT: TrackingRate = SIDEREAL
    }
}
