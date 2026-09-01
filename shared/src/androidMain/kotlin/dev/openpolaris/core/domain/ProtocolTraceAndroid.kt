package dev.openpolaris.core.domain

internal actual fun getSystemProperty(key: String): String? =
    // `System.getProperty` works on Android for keys explicitly passed
    // via `-D` JVM args (rare on Android, but possible via Robolectric
    // or instrumentation tests). For production Android the env var
    // route is the only one that works.
    try {
        System.getProperty(key)
    } catch (_: SecurityException) {
        null
    }

internal actual fun getEnvironmentVariable(key: String): String? =
    try {
        // Android's [System.getenv] throws SecurityException for some
        // restricted keys; this swallows that so the trace gate stays
        // a no-op rather than crashing the reader loop.
        System.getenv(key)
    } catch (_: SecurityException) {
        null
    }
