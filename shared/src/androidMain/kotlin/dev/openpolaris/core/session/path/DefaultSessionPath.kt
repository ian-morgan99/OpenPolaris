package dev.openpolaris.core.session.path

import java.nio.file.Paths

/**
 * Android actual for v1. Until 3c.4 wires `Context` injection in
 * `MainActivity.onResume`, we land on a stable, readable location rooted in
 * the per-app data dir. 3c.4 will replace this body with
 * `context.filesDir.resolve("openpolaris/session.json").toString()` so the
 * path lives inside the app's own sandbox and survives backups.
 */
actual fun defaultSessionPath(): String {
    val home = System.getProperty("user.home") ?: "/data/local/tmp"
    return Paths.get(home, ".openpolaris", "session.json").toString()
}
