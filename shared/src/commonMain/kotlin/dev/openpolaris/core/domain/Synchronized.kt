package dev.openpolaris.core.domain

/**
 * KMP-friendly replacement for the JVM-only `kotlin.synchronized` intrinsic
 * used by [MountSession] to guard its [MountSession.pending] map.
 *
 * The JVM and Android actuals both delegate to the stdlib's
 * `kotlin.synchronized(lock, block)`, which compiles down to a monitor
 * enter/exit pair on the [lock] instance. Kotlin/Native (iOS) does not
 * have a comparable monitor primitive, so the native actual is a plain
 * pass-through: the body runs unsynchronised. That is acceptable here
 * because:
 *  - The lock is only ever held for O(1) map operations (insert / remove /
 *    snapshot), and contention on the `pending` map is bounded by one
 *    writer and one reader coroutine.
 *  - Kotlin/Native's frozen-object model means the [MountSession] itself
 *    is not safely shared between threads anyway, so the monitor would
 *    be redundant.
 *  - The MountSession is currently wired only to the JVM/Android targets
 *    in production; iOS is a metadata-only validation target.
 *
 * The signature is intentionally `inline` so the JVM actual compiles
 * down to a monitor enter/exit around the call site, exactly as the
 * original `synchronized` intrinsic did. This avoids the per-call
 * lambda allocation the old stdlib had before 1.7.x and matches what
 * MountSession was doing before KMP 2.1.0.
 */
expect inline fun <T> synchronized(lock: Any, block: () -> T): T
