package dev.openpolaris.core.domain

/**
 * iOS/Kotlin-Native actual for the KMP `synchronized` seam.
 *
 * Kotlin/Native has a frozen object model: object references cannot
 * mutate after publication, and most state is single-threaded by
 * default. The `kotlin.synchronized(lock) { ... }` JVM primitive has
 * no native equivalent, and there is no monitor to acquire.
 *
 * For our use sites (a single `pending` map inside `MountSession`,
 * held in an `AtomicReference` wrapper and only mutated from the
 * single dispatcher coroutine) the lock is logically unnecessary on
 * iOS. We simply call the block, which is the correct semantics for
 * a single-threaded owner.
 *
 * If we ever need real cross-thread synchronisation on iOS we should
 * switch to `kotlinx.atomicfu.locks.SynchronizedObject` or
 * `kotlinx.coroutines.sync.Mutex` instead — both have multiplatform
 * actuals.
 */
actual inline fun <T> synchronized(lock: Any, block: () -> T): T = block()
