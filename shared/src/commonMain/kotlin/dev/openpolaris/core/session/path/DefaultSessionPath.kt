package dev.openpolaris.core.session.path

/**
 * Returns the platform-default location for the persisted
 * [dev.openpolaris.core.session.SessionMarker]. Production callers
 * should *always* prefer an explicit path:
 *  - JVM/desktop: pass `${userHome}/.openpolaris/session.json` (or use this
 *    function, which returns exactly that).
 *  - Android: build the path from `Context.filesDir` via
 *    [sessionStorePathForFilesDir] inside `MainActivity.onCreate`. Do not
 *    rely on this function on Android — it has no `Context`, so it falls
 *    back to `${userHome}/.openpolaris/session.json`, which is the wrong
 *    location on a real device. The Android actual exists only so the
 *    multiplatform symbol compiles; production Android callers MUST wire
 *    a real `SessionStore` into the `AppViewModel`.
 *  - iOS: NSDocumentDirectory (v2; the iOS target is wired in `shared` so the
 *    symbol must exist, but the file-backed behaviour is desktop-only at v1)
 *
 * Tests should never call this at all — pass a
 * `tempDir.resolve("session.json")` directly so the real home directory
 * is never touched.
 */
expect fun defaultSessionPath(): String
