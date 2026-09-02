package dev.openpolaris.core.domain

/**
 * Platform-specific bridge to `System.currentTimeMillis()`.
 *
 * Lives in commonMain so pure-logic classes like
 * [OnBoardInstallWatcher] can default to a wall-clock source
 * without having to plumb a `Clock` everywhere. Tests always
 * pass their own `nowMs` lambda; this is only used by
 * production callers.
 */
expect object SystemMillis {
    fun now(): Long
}
