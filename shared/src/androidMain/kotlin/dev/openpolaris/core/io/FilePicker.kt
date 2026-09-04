package dev.openpolaris.core.io

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Android actual for [FilePicker]. Wraps
 * `Intent(ACTION_GET_CONTENT) + ActivityResultContracts.GetContent`,
 * which gives a single-tap = select UX on every supported Android
 * version (the SAF / `OpenDocument` picker uses single-tap = preview
 * for grid view, which is not discoverable for picking files — see
 * MainActivity for the full rationale).
 *
 * v0.1.14 switched from `OpenDocument` to `GetContent`. We don't need
 * persistent URI permission because [FilePickerRegistry.handleResult]
 * copies the picked bytes into `cacheDir` immediately and returns an
 * absolute filesystem path, which is what every caller wants.
 *
 * The `shared/` module can't construct the launcher itself because it has
 * no reference to the `ComponentActivity` — those are owned by the Android
 * app shell. We bridge through [FilePickerRegistry], which [MainActivity]
 * populates in `onCreate` and clears in `onDestroy`. The
 * `pickFirmwareFile` path then hands the picked `Uri` to
 * [FilePickerRegistry.handleResult] so callers always get a real
 * filesystem path they can read with `PlatformFile.readBytes()` —
 * `Uri` itself is not a path and cannot be opened directly.
 *
 * **Rotation / Activity recreate (issue #49)**: when the device
 * rotates mid-pick, the old `MainActivity` is destroyed and a new
 * instance is created. The new activity re-registers the
 * `ActivityResultLauncher`, and AndroidX re-delivers the pending
 * `Uri` to the new launcher. But the per-call callback
 * ([FilePickerRegistry.pendingCallback]) was set on the old
 * instance's VM and is now stale. The fix is the process-scoped
 * [PickerBridge] buffer the launcher callback always writes to; the
 * new activity's `onCreate` reads and drains it, then re-applies the
 * result to the freshly-built `AppViewModel`. [FilePickerRegistry.clear]
 * deliberately preserves this buffer.
 */
actual object FilePicker {
    actual fun pickFile(
        title: String,
        mimeType: String?,
        onPicked: (absolutePath: String?) -> Unit,
    ) {
        val ctx = FilePickerRegistry.appContext
        val launcher = FilePickerRegistry.launcher
        if (ctx == null || launcher == null) {
            // Not wired up yet — usually means `pickFile` was called from
            // outside `MainActivity` (e.g. a test). Fail soft: cancel the
            // call rather than crashing.
            onPicked(null)
            return
        }
        // Set the per-call result callback. The registered launcher only
        // owns one slot, so we serialize picks through a single-element
        // holder. (The firmware flow is one-pick-at-a-time anyway, so a
        // concurrent pick is a user error — we just overwrite.)
        FilePickerRegistry.pendingCallback = onPicked
        // Mark a new pick as in-flight on the bridge so the launcher's
        // later callback knows to overwrite the buffer (vs. leaving an
        // old pre-rotation result in place that the new VM would then
        // mis-apply as fresh).
        PickerBridge.beginPick()
        // The launcher stored in the registry is whatever concrete type
        // the host registered — currently
        // `ActivityResultLauncher<String>` for `GetContent`. We launch
        // via reflection so the shared module isn't coupled to a
        // specific contract type. Pass the requested MIME type, or
        // `*/*` for "all document types" when the caller asked for
        // any. (The `title` parameter is ignored on Android — the
        // system chooser doesn't show a title bar in modern Android
        // versions. JVM still uses `title` for the FileDialog window
        // title.)
        val mime = mimeType ?: "*/*"
        try {
            val launchMethod = launcher::class.java.getMethod("launch", Any::class.java)
            launchMethod.invoke(launcher, mime)
        } catch (t: Throwable) {
            // The reflection call failed (e.g. contract was changed to a
            // non-`launch(Any)` shape). Surface a cancel-style null to
            // the caller so they don't hang waiting for a result that
            // will never arrive, and clear the in-flight flag so the
            // stale callback is not mistaken for a fresh result later.
            FilePickerRegistry.pendingCallback = null
            PickerBridge.publishResult(null, PickerBridge.PickResult.Reason.Error)
            onPicked(null)
        }
    }
}

/**
 * Internal registry shared between [FilePicker] and `MainActivity`. Holds
 * the Activity result launcher and a single-slot pending callback for
 * the next pick. Cleared by `MainActivity` in `onDestroy` so we don't
 * leak the activity reference.
 *
 * **Last-pick-result buffer (issue #49)**: the cross-process buffer
 * lives in [PickerBridge] (commonMain) so it's unit-testable. The
 * `MainActivity` launcher callback always writes there, and
 * `MainActivity.onCreate` drains it after the new VM has been
 * built. This is what lets a result delivered after rotation reach
 * the new VM. The buffer survives [clear] because
 * rotation's `onDestroy`/`onCreate` pair is the exact case we need
 * to bridge.
 */
object FilePickerRegistry {
    @Volatile var appContext: Context? = null
    /**
     * The launcher must accept the contract the host Activity registers
     * (currently `ActivityResultContracts.GetContent`, which is
     * `ActivityResultLauncher<String>` — a single MIME string). We use
     * a raw `Any?` here because the shared module shouldn't be coupled
     * to a specific contract; the host in `MainActivity` always assigns
     * a compatible launcher, and `FilePicker` doesn't touch this field
     * directly — it invokes via the reflection helper above.
     */
    @Volatile var launcher: Any? = null
    @Volatile var pendingCallback: ((String?) -> Unit)? = null

    /**
     * Read the picked SAF `Uri` into a file under the app's cacheDir
     * and return the absolute path. Called from the result callback
     * in `MainActivity`. Returns `null` if the user cancelled (no
     * Uri), the bytes could not be read, or no app context is
     * available yet.
     */
    fun handleResult(uri: Uri?): String? {
        val ctx = appContext
        if (uri == null) return null
        if (ctx == null) return null
        return try {
            val name = queryDisplayName(ctx, uri) ?: "picked.bin"
            val cacheFile = File(ctx.cacheDir, "picked_${System.currentTimeMillis()}_$name")
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            cacheFile.absolutePath
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Drop the activity-scoped references the registry holds. Called by
     * `MainActivity` in `onDestroy` so we don't leak the Activity
     * across configuration changes. We deliberately do NOT clear
     * [PickerBridge.pickInFlight] or [PickerBridge.lastPickResult]
     * here — that buffer is the rotation bridge and must survive
     * `onDestroy`/`onCreate`.
     */
    fun clear() {
        appContext = null
        launcher = null
        pendingCallback = null
        // PickerBridge.pickInFlight and PickerBridge.lastPickResult
        // intentionally preserved.
    }

    private fun queryDisplayName(ctx: Context, uri: Uri): String? {
        val proj = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        ctx.contentResolver.query(uri, proj, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        return null
    }
}
