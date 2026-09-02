package dev.openpolaris.core.domain

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Platform-default IO dispatcher. `Dispatchers.IO` exists on JVM and
 * Android, but NOT on iOS — the kotlinx-coroutines iOS artifact only
 * ships `Default` and `Main` (the latter is `MainCoroutineDispatcher`,
 * which is also a special case). Tests inject their own dispatcher into
 * [PlateSolveController] anyway, so this factory is only consulted in
 * production wiring.
 */
expect val IoDispatcher: CoroutineDispatcher
