package dev.openpolaris.core.session

import java.io.File

/**
 * Android actual. Uses `java.io.File` directly because the path coming in
 * from `defaultSessionPath()` (or the future `Context`-injected one) is
 * already absolute. 3c.4 will swap the path source from `userHome` to
 * `context.filesDir`; the file operations here are unchanged.
 *
 * `deleteIfExists()` relies on `File.delete()` alone. `File.delete()`
 * returns false both when the file does not exist and when deletion failed
 * for another reason (read-only file, dir not empty, etc.), which is
 * exactly the "false means nothing was removed" semantic we want.
 *
 * Do NOT OR with `!f.exists()`. The earlier `f.delete() || !f.exists()`
 * inverted the not-found case to true, falsely claiming the file was
 * removed. SessionStore.forget() then reported "Forgot saved mount" when
 * there was nothing to forget, which surfaced as a UI bug and tripped
 * `AppViewModelSessionMarkerTest.forgetMarkerWithoutFileIsANoOp`.
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

    actual fun deleteIfExists(): Boolean = f.delete()

    actual fun parentPath(): String? = f.parent
}
