package dev.openpolaris.core.domain

actual object SystemMillis {
    actual fun now(): Long = java.lang.System.currentTimeMillis()
}
