package dev.openpolaris.core.session

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * JVM actual. `java.nio.file.Path` is the right tool here — `Files.exists`,
 * `Files.isReadable`, `Files.isWritable`, `Files.createDirectories`, and the
 * atomic-ish `Files.write` all do exactly what we need and have well-defined
 * failure modes.
 */
actual class PlatformFile actual constructor(private val path: String) {
    private val p: Path = Paths.get(path)

    actual fun exists(): Boolean = Files.exists(p)
    actual fun isReadable(): Boolean = Files.isReadable(p)
    actual fun isWritable(): Boolean = Files.isWritable(p)

    actual fun createDirectories(): Boolean {
        Files.createDirectories(p)
        return true
    }

    actual fun readBytes(): ByteArray = Files.readAllBytes(p)

    actual fun writeBytes(bytes: ByteArray) {
        Files.write(p, bytes)
    }

    actual fun deleteIfExists(): Boolean = Files.deleteIfExists(p)

    actual fun parentPath(): String? = p.parent?.toString()
}
