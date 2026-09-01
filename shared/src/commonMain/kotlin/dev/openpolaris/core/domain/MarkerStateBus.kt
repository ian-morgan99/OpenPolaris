package dev.openpolaris.core.domain

import dev.openpolaris.core.solver.SolveResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide [StateFlow] of the most recent [SolveResult], used to push
 * live solve updates from the [dev.openpolaris.core.solver] pipeline into
 * UI surfaces that are not part of the Compose tree (notably the
 * [dev.openpolaris.android.VRActivity] `StereoRenderer`, which runs on a
 * separate `ComponentActivity` and has no direct access to the
 * `AppViewModel` instance owned by `MainActivity`).
 *
 * The bus is **a single per-process value**, not a queue. Late subscribers
 * receive the latest emission immediately on `collect`, which is what the
 * VR marker wants: it doesn't care about intermediate states from before
 * the activity launched.
 *
 * ## Wiring
 *
 * [dev.openpolaris.ui.AppViewModel] publishes via [publish] whenever a
 * plate-solve completes. `VRActivity` collects [solve] in
 * `lifecycleScope` and forwards each emission to the renderer.
 *
 * ## Testing
 *
 * [reset] is the seam that lets JVM tests start each scenario from a clean
 * state without spinning up a fresh JVM. Test ordering doesn't matter as
 * long as every test calls `reset()` in its `beforeTest { }` block.
 *
 * ## Why a singleton and not DI?
 *
 * The codebase has no Hilt/Koin; introducing a DI container for one
 * cross-Activity flow is more weight than the value it adds. A process
 * singleton with an explicit `reset()` is the smallest honest seam.
 */
object MarkerStateBus {
    private val _solve = MutableStateFlow<SolveResult?>(null)

    /** Latest solve result, or `null` if no solve has been published. */
    val solve: StateFlow<SolveResult?> = _solve.asStateFlow()

    /**
     * Publish a new solve. No-op if [result] is `null` — callers wanting
     * to clear the marker should pass `SolveResult` with the values that
     * make it invisible, not `null` (the VR renderer's `setSolve(null)`
     * is the explicit hide path on the consumer side).
     */
    fun publish(result: SolveResult) {
        _solve.value = result
    }

    /** Clear the bus. JVM tests call this in `beforeTest { }`. */
    fun reset() {
        _solve.value = null
    }
}
