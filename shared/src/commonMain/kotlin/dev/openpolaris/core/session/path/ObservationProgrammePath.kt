package dev.openpolaris.core.session.path

/**
 * Platform-consistent sibling path for an in-progress observation journal.
 * Android production callers should inject the Context.filesDir variant.
 */
fun defaultObservationProgrammePath(): String =
    defaultSessionPath().replace("session.json", "observation-programme.json")
