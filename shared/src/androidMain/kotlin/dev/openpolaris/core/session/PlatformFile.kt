package dev.openpolaris.core.session

import java.io.File

/**
 * Android actual. Uses `java.io.File` directly because the path coming in
 * from `defaultSessionPath()` (or the future `Context`-injected one) is
 * already absolute. 3c.4 will swap the path source from `userHome` to
 * `context.filesDir`; the file operations here are unchanged.
 */
actual class PlatformFile actual constructor(private val path: String) {
    private val f: File = File(path)

    actual fun exists(): Boolean = f.exists()
    actual fun isReadable(): Boolean = f.canRead()
    actual fun isWritable(): Boolean = f.canWrite()

    actual fun createDirectories(): Boolean {
        // `mkdirs` creates this directory AND all missing ancestors (mirrors
        // `Files.createDirectories`). The earlier `f.parentFile.mkdirs()`
        // was wrong — it created f's parent, not f.
        return f.mkdirs() || f.isDirectory
    }

    actual fun readBytes(): ByteArray = f.readBytes()

    actual fun writeBytes(bytes: ByteArray) {
        f.writeBytes(bytes)
    }

    actual fun deleteIfExists(): Boolean = f.delete() || !f.exists()

    actual fun parentPath(): String? = f.parent
}
