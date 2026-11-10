package dev.openpolaris.ui

/**
 * Stable user-facing destinations shared by phone, tablet and PC navigation.
 *
 * Advanced device, diagnostic and firmware actions remain in the overflow
 * surface; these five destinations are the primary observing workflow.
 */
enum class AppDestination(
    val label: String,
) {
    MISSION("Mission"),
    CONTROL("Control"),
    CAMERA("Camera"),
    ASTRO("Astro"),
    GALLERY("Gallery"),
}

val primaryAppDestinations: List<AppDestination> = AppDestination.entries.toList()
