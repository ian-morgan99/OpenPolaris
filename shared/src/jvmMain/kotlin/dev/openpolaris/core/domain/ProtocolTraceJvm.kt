package dev.openpolaris.core.domain

internal actual fun getSystemProperty(key: String): String? =
    System.getProperty(key)

internal actual fun getEnvironmentVariable(key: String): String? =
    System.getenv(key)
