package dev.openpolaris.core.io

/**
 * Pure-Kotlin UTF-8 codec for the multiplatform `shared` module.
 *
 * `Charsets.UTF_8` is JVM-only; in commonMain we need a tiny hand-rolled
 * encoder/decoder that doesn't drag in `java.nio.charset.Charset`. The
 * algorithm is the standard one (RFC 3629) and is correct for any
 * codepoint in the BMP and supplementary planes.
 *
 * The decoder is strict by default — malformed input throws
 * [IllegalArgumentException] with the byte offset — which matches what
 * the JVM `UTF_8` charset does for un-mappable sequences and what our
 * protocol parsers want.
 */
object Utf8 {
    // ---- encode (String -> ByteArray) -----------------------------------

    fun encode(s: String): ByteArray {
        val out = ArrayList<Byte>(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i].code
            when {
                c < 0x80 -> {
                    out.add(c.toByte())
                }
                c < 0x800 -> {
                    out.add((0xC0 or (c shr 6)).toByte())
                    out.add((0x80 or (c and 0x3F)).toByte())
                }
                c in 0xD800..0xDBFF && i + 1 < s.length &&
                    s[i + 1].code in 0xDC00..0xDFFF -> {
                    // Surrogate pair
                    val cp = 0x10000 + (((c and 0x3FF) shl 10) or (s[i + 1].code and 0x3FF))
                    out.add((0xF0 or (cp shr 18)).toByte())
                    out.add((0x80 or ((cp shr 12) and 0x3F)).toByte())
                    out.add((0x80 or ((cp shr 6) and 0x3F)).toByte())
                    out.add((0x80 or (cp and 0x3F)).toByte())
                    i++
                }
                c < 0x10000 -> {
                    out.add((0xE0 or (c shr 12)).toByte())
                    out.add((0x80 or ((c shr 6) and 0x3F)).toByte())
                    out.add((0x80 or (c and 0x3F)).toByte())
                }
                else -> {
                    // Codepoints > U+10FFFF aren't valid; encode as replacement U+FFFD
                    out.add(0xEF.toByte())
                    out.add(0xBF.toByte())
                    out.add(0xBD.toByte())
                }
            }
            i++
        }
        val arr = ByteArray(out.size)
        for (j in out.indices) arr[j] = out[j]
        return arr
    }

    /**
     * Encode as US-ASCII (7-bit). Any non-ASCII character throws
     * [IllegalArgumentException] with the offset. Mirrors the JVM
     * `US_ASCII` charset's strict behaviour.
     */
    fun encodeAscii(s: String): ByteArray {
        val out = ByteArray(s.length)
        for (i in s.indices) {
            val c = s[i].code
            if (c >= 0x80) {
                throw IllegalArgumentException(
                    "Non-ASCII character U+${c.toString(16)} at index $i"
                )
            }
            out[i] = c.toByte()
        }
        return out
    }

    // ---- decode (ByteArray -> String) -----------------------------------

    fun decode(bytes: ByteArray, start: Int = 0, length: Int = bytes.size - start): String {
        require(start >= 0) { "start < 0" }
        require(length >= 0) { "length < 0" }
        require(start + length <= bytes.size) { "start+length > bytes.size" }

        val sb = StringBuilder(length)
        var i = start
        val end = start + length
        while (i < end) {
            val b1 = bytes[i].toInt() and 0xFF
            when {
                b1 < 0x80 -> {
                    sb.append(b1.toChar())
                    i++
                }
                b1 < 0xC2 -> throw IllegalArgumentException(
                    "Invalid UTF-8 lead byte 0x${b1.toString(16)} at offset $i"
                )
                b1 < 0xE0 -> {
                    if (i + 1 >= end) throw IllegalArgumentException("Truncated 2-byte sequence at $i")
                    val b2 = bytes[i + 1].toInt() and 0xFF
                    if (b2 and 0xC0 != 0x80) throw IllegalArgumentException(
                        "Invalid UTF-8 continuation at ${i + 1}"
                    )
                    val cp = ((b1 and 0x1F) shl 6) or (b2 and 0x3F)
                    sb.append(cp.toChar())
                    i += 2
                }
                b1 < 0xF0 -> {
                    if (i + 2 >= end) throw IllegalArgumentException("Truncated 3-byte sequence at $i")
                    val b2 = bytes[i + 1].toInt() and 0xFF
                    val b3 = bytes[i + 2].toInt() and 0xFF
                    if (b2 and 0xC0 != 0x80 || b3 and 0xC0 != 0x80) {
                        throw IllegalArgumentException("Invalid UTF-8 continuation at ${i + 1}")
                    }
                    val cp = ((b1 and 0x0F) shl 12) or ((b2 and 0x3F) shl 6) or (b3 and 0x3F)
                    if (cp in 0xD800..0xDFFF) {
                        throw IllegalArgumentException("Surrogate codepoint U+${cp.toString(16)} at $i")
                    }
                    sb.append(cp.toChar())
                    i += 3
                }
                b1 < 0xF5 -> {
                    if (i + 3 >= end) throw IllegalArgumentException("Truncated 4-byte sequence at $i")
                    val b2 = bytes[i + 1].toInt() and 0xFF
                    val b3 = bytes[i + 2].toInt() and 0xFF
                    val b4 = bytes[i + 3].toInt() and 0xFF
                    if (b2 and 0xC0 != 0x80 || b3 and 0xC0 != 0x80 || b4 and 0xC0 != 0x80) {
                        throw IllegalArgumentException("Invalid UTF-8 continuation at ${i + 1}")
                    }
                    val cp = ((b1 and 0x07) shl 18) or
                        ((b2 and 0x3F) shl 12) or
                        ((b3 and 0x3F) shl 6) or
                        (b4 and 0x3F)
                    if (cp > 0x10FFFF) {
                        throw IllegalArgumentException("Codepoint U+${cp.toString(16)} out of range at $i")
                    }
                    val offset = cp - 0x10000
                    sb.append((0xD800 + (offset shr 10)).toChar())
                    sb.append((0xDC00 + (offset and 0x3FF)).toChar())
                    i += 4
                }
                else -> throw IllegalArgumentException(
                    "Invalid UTF-8 lead byte 0x${b1.toString(16)} at offset $i"
                )
            }
        }
        return sb.toString()
    }
}
