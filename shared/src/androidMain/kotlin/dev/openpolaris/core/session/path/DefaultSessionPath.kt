package dev.openpolaris.core.session.path

import java.io.File
import java.nio.file.Paths

/**
 * Android actual for v1. Production callers should NOT rely on this
 * default — it has no `Context`, so it cannot know the per-app data dir.
 * `MainActivity` in 3c.4 builds a real `SessionStore` rooted at
 * `Context.filesDir` via [sessionStorePathForFilesDir] and injects it into
 * the [AppViewModel]; this default only fires for paths that come through
 * the multiplatform `defaultSessionPath()` call (e.g. an automated test
 * that does not set up an Activity). The fallback is
 * `${userHome}/.openpolaris/session.json` so we never crash on a missing
 * path.
 */
actual fun defaultSessionPath(): String {
    val home = System.getProperty("user.home") ?: "/data/local/tmp"
    return Paths.get(home, ".openpolaris", "session.json").toString()
}

/**
 * Build the per-app [SessionStore] file path from a `Context.filesDir`
 * value. Pure function — no `Context` reference leaks out of the Android
 * module into `shared`. Lives in the app's own sandbox, so the file is
 * wiped on uninstall, included in auto-backups, and never collides with
 * another package. Used by `MainActivity` in 3c.4 to construct a real
 * Android-rooted `SessionStore` for the [AppViewModel].
 */
fun sessionStorePathForFilesDir(filesDir: File): String =
    File(filesDir, "openpolaris").resolve("session.json").absolutePath
