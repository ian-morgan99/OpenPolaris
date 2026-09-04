package dev.openpolaris.core.io

/**
 * Process-scoped buffer that carries the most recent file-pick result
 * from the Android `ActivityResultLauncher` callback to the
 * `AppViewModel`, even across an activity recreate (rotation,
 * dark-mode toggle, locale change, low-memory kill+restore).
 *
 * **Why this lives in commonMain**: the state machine is pure data —
 * no `Context`, no `Uri`, no Compose imports. That makes it unit
 * testable from `commonTest` (which has no Android source set on the
 * `shared` module — only `commonTest` and `jvmTest`; see issue #47
 * and `compose-kmp-pitfalls` §1). The Android-specific glue
 * (`Context`, `Uri`, `ActivityResultLauncher`) stays in
 * [FilePickerRegistry] (androidMain).
 *
 * **Lifecycle**:
 *
 * 1. `FilePicker.pickFile` is called from `AppViewModel` →
 *    [beginPick] marks the bridge as `pickInFlight = true` and
 *    launches the system chooser.
 *
 * 2. The launcher callback (in `MainActivity`) always publishes the
 *    result via [publishResult], with a [PickResult.Reason] tag so the
 *    VM can distinguish Picked / Cancelled / Error. This is the
 *    "buffer write" half of the rotation bridge.
 *
 * 3. `MainActivity.onCreate` constructs a fresh `AppViewModel` (the
 *    old one is dead after recreate). It then calls [consume] to read
 *    and clear the buffer, and re-applies the result via
 *    `viewModel.applyPickResult(...)`. This is the "buffer read" half.
 *
 * 4. The per-call `pendingCallback` in `FilePickerRegistry` is
 *    cleared in `onDestroy` (and so is lost across rotation). The
 *    buffer is **not** cleared by that path — it's the only way a
 *    delivered-after-rotation result reaches the new VM.
 *
 * 5. On process death, the buffer is also gone — but so is the
 *    original `Uri`, so a stale buffer would be meaningless.
 */
object PickerBridge {
    /**
     * The most recent pick result, written by the launcher callback
     * and consumed (and cleared) by `MainActivity.onCreate` after the
     * new VM has been built. `null` means "no result yet" or "already
     * consumed".
     */
    @Volatile
    var lastPickResult: PickResult? = null

    /**
     * `true` between [FilePicker.pickFile] launching the chooser and
     * the launcher callback (or the registry's own null-fallback) firing
     * with a result. Used by `MainActivity` to know whether a delivered
     * [lastPickResult] is fresh (and should be applied to the new VM)
     * or stale (and should be left alone). Cleared by [publishResult].
     */
    @Volatile
    var pickInFlight: Boolean = false

    /**
     * Snapshot of a pick result. Tags the path with a [Reason] so the
     * VM can surface a useful status message ("Picker cancelled", etc.)
     * instead of silently swallowing the event.
     */
    data class PickResult(
        val absolutePath: String?,
        val reason: Reason,
    ) {
        enum class Reason { Picked, Cancelled, Error }
    }

    /**
     * Mark a new pick as in-flight. Callers should invoke this
     * immediately before launching the system chooser, so the
     * launcher's later callback can tell a fresh result apart from
     * a stale one left over from a previous session.
     */
    fun beginPick() {
        pickInFlight = true
    }

    /**
     * Set [lastPickResult] from a `MainActivity` launcher callback,
     * with the right [PickResult.Reason] tag. Callers should always
     * go through this rather than writing [lastPickResult] directly
     * so the [pickInFlight] flag is cleared in the same step.
     */
    fun publishResult(path: String?, reason: PickResult.Reason) {
        pickInFlight = false
        lastPickResult = PickResult(path, reason)
    }

    /**
     * Read and clear the buffer. Returns the [PickResult] that was
     * last published (or `null` if there is none). Called from
     * `MainActivity.onCreate` after the new `AppViewModel` is built.
     */
    fun consume(): PickResult? {
        val r = lastPickResult
        lastPickResult = null
        return r
    }

    /**
     * Drop the buffer and the in-flight flag. Used by tests and by
     * any caller that needs a clean slate. Note that
     * `FilePickerRegistry.clear()` (the activity-destroy path) does
     * **not** call this — preserving the buffer across
     * `onDestroy`/`onCreate` is the entire point of the bridge.
     */
    fun reset() {
        pickInFlight = false
        lastPickResult = null
    }
}
