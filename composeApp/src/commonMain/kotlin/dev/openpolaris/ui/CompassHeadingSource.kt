package dev.openpolaris.ui

/** Optional platform compass input for Astro alignment rehearsal. It never sends a mount command. */
interface CompassHeadingSource {
    val supported: Boolean
    fun start(onHeading: (Double) -> Unit, onUnavailable: (String) -> Unit)
    fun stop()
}
