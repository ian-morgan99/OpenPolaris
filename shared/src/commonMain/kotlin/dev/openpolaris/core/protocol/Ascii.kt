package dev.openpolaris.core.protocol

/**
 * ASCII encode/decode without java.nio Charsets (not available in common code).
 * The Polaris wire protocol is pure ASCII, so this is lossless for valid frames.
 */
fun encodeAscii(s: String): ByteArray = ByteArray(s.length) { s[it].code.toByte() }

fun decodeAscii(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): String {
    val sb = StringBuilder(length)
    for (i in 0 until length) sb.append((bytes[offset + i].toInt() and 0x7F).toChar())
    return sb.toString()
}
