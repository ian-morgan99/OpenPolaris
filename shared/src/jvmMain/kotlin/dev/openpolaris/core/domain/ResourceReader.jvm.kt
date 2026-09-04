package dev.openpolaris.core.domain

private val classLoader: ClassLoader = ResourceReaderJVM::class.java.classLoader

/** Internal alias used to anchor the classloader lookup. */
private class ResourceReaderJVM

actual fun readResourceText(path: String): String? {
    val stream = classLoader.getResourceAsStream(path) ?: return null
    return stream.use { it.readBytes().toString(Charsets.UTF_8) }
}

actual fun readResourceBytes(path: String): ByteArray? {
    val stream = classLoader.getResourceAsStream(path) ?: return null
    return stream.use { it.readBytes() }
}
