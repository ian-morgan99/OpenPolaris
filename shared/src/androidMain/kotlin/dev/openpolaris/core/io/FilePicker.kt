package dev.openpolaris.core.io

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import java.io.File
import java.io.FileOutputStream

/**
 * Android actual for [FilePicker]. Wraps
 * `Intent(ACTION_OPEN_DOCUMENT) + ActivityResultContracts.OpenDocument`,
 * which is the modern Storage Access Framework path (works on every
 * supported API level, no runtime permission needed, and survives process
 * death — the launcher is registered on the [ComponentActivity] so the
 * result callback is delivered even if the OS kills the app between the
 * launch and the user's pick).
 *
 * The `shared/` module can't construct the launcher itself because it has
 * no reference to the `ComponentActivity` — those are owned by the Android
 * app shell. We bridge through [FilePickerRegistry], which [MainActivity]
 * populates in `onCreate` and clears in `onDestroy`. The
 * `pickFirmwareFile` path then hands the picked `Uri` to
 * [copyPickedToCache] so callers always get a real filesystem path they
 * can read with `PlatformFile.readBytes()` — `Uri` itself is not a path
 * and cannot be opened directly.
 */
actual object FilePicker {
    actual fun pickFile(
        title: String,
        @Suppress("UNUSED_PARAMETER") mimeType: String?,
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
        // The launcher stored in the registry is whatever concrete type
        // the host registered (typically
        // `ActivityResultLauncher<Array<String>>` for `OpenDocument`,
        // not `ActivityResultLauncher<Intent>`). We launch via reflection
        // so the shared module isn't coupled to a specific contract type.
        // The OpenDocument contract takes a `String[]` of MIME types;
        // pass a single-element array with the requested type, or `*/*`
        // for "all document types" when the caller asked for any.
        val mimes = arrayOf(mimeType ?: "*/*")
        try {
            val launchMethod = launcher::class.java.getMethod("launch", Any::class.java)
            launchMethod.invoke(launcher, mimes)
        } catch (t: Throwable) {
            onPicked(null)
        }
    }
}

/**
 * Internal registry shared between [FilePicker] and `MainActivity`. Holds
 * the Activity result launcher and a single-slot pending callback for
 * the next pick. Cleared by `MainActivity` in `onDestroy` so we don't
 * leak the activity reference.
 */
object FilePickerRegistry {
    @Volatile var appContext: Context? = null
    /**
     * The launcher must accept the contract the host Activity registers
     * (typically `ActivityResultContracts.OpenDocument`, which is
     * `ActivityResultLauncher<Array<String>>` — not `Intent`). We use a
     * raw `Any?` here because the shared module shouldn't be coupled to a
     * specific contract; the host in `MainActivity` always assigns a
     * compatible launcher, and `FilePicker` doesn't touch this field
     * directly — it invokes via the cast helper below.
     */
    @Volatile var launcher: Any? = null
    @Volatile var pendingCallback: ((String?) -> Unit)? = null

    /**
     * Drop all references the registry holds. Called by `MainActivity` in
     * `onDestroy` so we don't leak the Activity across configuration
     * changes. The picker is single-slot; any in-flight callback is
     * dropped on rotation, which is acceptable for v1.
     */
    fun clear() {
        appContext = null
        launcher = null
        pendingCallback = null
    }

    /**
     * Read the picked SAF `Uri` into a file under the app's cacheDir and
     * return the absolute path. Called from the result callback in
     * `MainActivity`. Returns `null` if the user cancelled (no Uri) or
     * the copy failed (e.g. revoked permission).
     */
    fun handleResult(uri: Uri?): String? {
        val ctx = appContext ?: return null
        if (uri == null) return null
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
