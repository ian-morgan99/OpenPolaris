package dev.openpolaris.core.domain

/**
 * Android actual for the KMP `synchronized` seam. Same implementation
 * as the JVM actual — the Android stdlib also provides
 * `kotlin.synchronized(lock, block)` which compiles to a monitor
 * enter/exit on [lock].
 */
actual inline fun <T> synchronized(lock: Any, block: () -> T): T =
    kotlin.synchronized(lock, block)
