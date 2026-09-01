package dev.openpolaris.core.io

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * JVM actual. Uses `java.awt.FileDialog` — the lightest cross-platform
 * file picker in the JDK. It opens a native modal on macOS / Windows / KDE
 * and an XdgDesktopPortal on most Linux desktops, so users get the same
 * UX they see for every other desktop app.
 *
 * `FileDialog` is synchronous, so we run it on the current thread and call
 * [onPicked] inline before returning. Callers normally invoke this from a
 * background coroutine (the firmware pane uses `Dispatchers.IO`) so the
 * brief UI freeze of the modal is fine.
 */
actual object FilePicker {
    actual fun pickFile(
        title: String,
        @Suppress("UNUSED_PARAMETER") mimeType: String?,
        onPicked: (absolutePath: String?) -> Unit,
    ) {
        // java.awt.FileDialog is modal and blocks until the user picks
        // a file or cancels. We instantiate a hidden parent Frame so the
        // dialog has something to attach to without flashing a window of
        // its own.
        val parent = Frame()
        parent.isUndecorated = true
        parent.isVisible = false

        val dialog = FileDialog(parent, title, FileDialog.LOAD).apply {
            // Start in the user's home so the dialog doesn't always land
            // in the JVM install dir, which is the platform default.
            val home = System.getProperty("user.home")
            if (home != null) directory = home
            // `filename` doubles as the default-suggested filename in the
            // entry box. Leave it empty so we don't suggest a name that
            // the user might not expect.
        }
        try {
            dialog.isVisible = true
            val dir = dialog.directory
            val name = dialog.file
            val picked: String? = if (dir != null && name != null) {
                File(dir, name).absolutePath
            } else {
                null
            }
            onPicked(picked)
        } finally {
            dialog.dispose()
            parent.dispose()
        }
    }
}
