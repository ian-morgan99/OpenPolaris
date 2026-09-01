package dev.openpolaris.core.session

/**
 * Minimal file-system wrapper used by [SessionStore]. 3c.2.
 *
 *  - JVM/desktop: actual wraps `java.nio.file.Path`.
 *  - Android: actual wraps `java.io.File` and the per-app `filesDir` injected
 *    by `MainActivity` in 3c.4. (For v1 we just use `java.io.File` directly
 *    because the path coming in from `defaultSessionPath()` is already
 *    absolute; the Context swap happens in 3c.4.)
 *
 * We do not use `kotlin.io.path` (JVM-only) or `okio` (out of scope for v1)
 * because both pull in extra surface area for what is, today, four file ops.
 */
expect class PlatformFile(path: String) {
    fun exists(): Boolean
    fun isReadable(): Boolean
    fun isWritable(): Boolean
    fun createDirectories(): Boolean
    fun readBytes(): ByteArray
    fun writeBytes(bytes: ByteArray)
    fun deleteIfExists(): Boolean
    fun parentPath(): String?
}
