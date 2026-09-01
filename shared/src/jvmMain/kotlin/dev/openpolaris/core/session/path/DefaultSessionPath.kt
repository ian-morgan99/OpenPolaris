package dev.openpolaris.core.session.path

import java.nio.file.Paths

/**
 * JVM/desktop actual. The `.openpolaris` dotfile lives in the user's home
 * directory — matches the standard `~/.config/<app>/` convention and keeps
 * the working directory clean.
 */
actual fun defaultSessionPath(): String {
    val home = System.getProperty("user.home")
        ?: error("user.home not set; cannot derive default session path")
    return Paths.get(home, ".openpolaris", "session.json").toString()
}
