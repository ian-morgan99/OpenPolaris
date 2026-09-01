package dev.openpolaris.core.io

/**
 * Cross-platform file picker for arbitrary user files.
 *
 * v1 scope: only the firmware upload flow needs a picker (FwPkt.zip), so this
 * is a tiny, focused API rather than a general-purpose file chooser. The JVM
 * actual wraps `java.awt.FileDialog`; the Android actual wraps
 * `Intent(ACTION_OPEN_DOCUMENT)`.
 *
 *  - JVM: [pickFile] blocks the calling thread (JVM dialogs are synchronous)
 *    and returns the absolute path the user chose, or `null` if they
 *    cancelled.
 *  - Android: [pickFile] is fire-and-forget. The result is delivered through
 *    [onPicked] on the main thread. This mirrors how `registerForActivityResult`
 *    is normally used — there is no synchronous "wait" because Android
 *    lifecycle forbids blocking the UI thread on a content resolver query.
 *
 * Filtering by MIME type or extension is a hint, not a hard requirement: the
 * user can still pick any file the OS shows them. The firmware flow asks
 * for `application/zip` first and falls back to whatever the user selected.
 */
expect object FilePicker {
    /**
     * Show a file picker to the user. Returns the absolute path of the
     * selected file on success and `null` if the user cancelled.
     *
     * On Android this is asynchronous: pass [onPicked] to receive the
     * result. On JVM it is synchronous and [onPicked] is invoked inline
     * before this function returns.
     *
     * @param title dialog title (shown in the system chooser on Android,
     *              the window title on JVM).
     * @param mimeType preferred MIME type, e.g. `"application/zip"`. Pass
     *                 `null` to accept anything.
     * @param onPicked callback for the Android actual. Ignored on JVM.
     */
    fun pickFile(
        title: String,
        mimeType: String?,
        onPicked: (absolutePath: String?) -> Unit,
    )
}
