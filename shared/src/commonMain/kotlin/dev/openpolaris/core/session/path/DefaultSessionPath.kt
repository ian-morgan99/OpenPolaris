package dev.openpolaris.core.session.path

/**
 * Returns the platform-default location for the persisted [dev.openpolaris.core.session.SessionMarker].
 *
 *  - JVM/desktop: `${userHome}/.openpolaris/session.json`
 *  - Android: `${filesDir}/openpolaris/session.json` (added with #6's Android
 *    `Context` injection in a follow-up; for now Android's actual returns
 *    `${userHome}/.openpolaris/session.json` so the build is green)
 *  - iOS: NSDocumentDirectory (v2; the iOS target is wired in `shared` so the
 *    symbol must exist, but the file-backed behaviour is desktop-only at v1)
 *
 * Production callers should *always* wrap this in `SessionStore(...)` rather
 * than re-derive a path inside the store. Tests should never call this at
 * all — pass a `tempDir.resolve("session.json")` directly.
 */
expect fun defaultSessionPath(): String
