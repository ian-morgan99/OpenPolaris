package dev.openpolaris.core.domain

/**
 * JVM actual for the KMP `synchronized` seam. Delegates to the stdlib's
 * `kotlin.synchronized` which inlines down to a monitor enter/exit on
 * [lock]. The `expect` is itself `inline`, so this is a straight
 * re-export — no extra lambda allocation at the call site.
 */
actual inline fun <T> synchronized(lock: Any, block: () -> T): T =
    kotlin.synchronized(lock, block)
