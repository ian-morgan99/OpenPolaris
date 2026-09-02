package dev.openpolaris.core.util

/**
 * Pure-Kotlin MD5 implementation (RFC 1321). No platform-specific
 * dependencies; usable from `commonMain` and from every KMP target.
 *
 * The on-board Polaris firmware uses MD5 (not SHA-256) for its
 * `crcInfo` / `omsInfo` comparison (see
 * `docs/evidence/gimbal-ssh-2026-08-31/fw-install-flow.txt`). The
 * desktop app's "verify before upload" toggle has to speak the same
 * hash so the user can paste the `crcInfo` value from the Benro
 * Connect app and have the desktop refuse to push a zip that won't
 * pass on-device.
 *
 * This is intentionally minimal: 64-byte blocks, little-endian word
 * encoding, the four standard auxiliary functions, and the 64-constant
 * round table. No streaming, no resets — pass the full payload to
 * [digest] in one call. Firmware zips are at most 128 MB (the
 * Phase-1a size cap) which fits in memory and the single-shot form
 * is easier to reason about than the rolling variant.
 *
 * Reference: RFC 1321 (https://datatracker.ietf.org/doc/html/rfc1321).
 */
object Md5 {
    /** Compute the MD5 of [bytes] and return it as 32 lowercase hex chars. */
    fun digest(bytes: ByteArray): String {
        val state = IntArray(4).also {
            it[0] = 0x67452301
            it[1] = -0x10325477 // 0xefcdab89 as signed Int
            it[2] = -0x67452302 // 0x98badcfe as signed Int
            it[3] = 0x10325476
        }

        // Pre-processing: append padding bits + length.
        // Original length in bits, mod 2^64.
        val bitLen = bytes.size.toLong() * 8
        // Number of bytes after padding: bytes.size + 1 (the 0x80 marker) +
        // zero-padding to make (len mod 64) == 56 + 8 (the 64-bit length).
        val paddedLen = ((bytes.size + 9 + 63) / 64) * 64
        val padded = ByteArray(paddedLen)
        bytes.copyInto(padded)
        padded[bytes.size] = 0x80.toByte()
        // Append 64-bit length in little-endian.
        for (j in 0 until 8) {
            padded[paddedLen - 8 + j] = (bitLen ushr (8 * j)).toByte()
        }

        // Process each 512-bit (= 64-byte) chunk.
        val x = IntArray(16)
        var off = 0
        while (off < paddedLen) {
            // Load 16 little-endian 32-bit words.
            for (j in 0 until 16) {
                val k = off + j * 4
                x[j] = (padded[k].toInt() and 0xff) or
                    ((padded[k + 1].toInt() and 0xff) shl 8) or
                    ((padded[k + 2].toInt() and 0xff) shl 16) or
                    ((padded[k + 3].toInt() and 0xff) shl 24)
            }
            var a = state[0]
            var b = state[1]
            var c = state[2]
            var d = state[3]

            // Main round loop. 64 operations grouped into 4 rounds of 16.
            // Each step writes the rotated A into the B register and shifts
            // (a, b, c, d) -> (old_d, new_b, old_b, old_c). This is the
            // standard "rotating" formulation of the RFC 1321 [ABCD]/[DABC]
            // round macros.
            for (op in 0 until 64) {
                val f = roundFunc(op, a, b, c, d)
                val g = wordIndex(op)
                val temp = d
                d = c
                c = b
                b = (b + leftRotate((a + f + K[op] + x[g]), S[op])) and 0xffffffff.toInt()
                a = temp
            }
            state[0] = (state[0] + a) and 0xffffffff.toInt()
            state[1] = (state[1] + b) and 0xffffffff.toInt()
            state[2] = (state[2] + c) and 0xffffffff.toInt()
            state[3] = (state[3] + d) and 0xffffffff.toInt()

            off += 64
        }

        // Output is the 4 state words concatenated, each in little-endian.
        val out = ByteArray(16)
        for (w in 0 until 4) {
            val s = state[w]
            out[w * 4] = (s and 0xff).toByte()
            out[w * 4 + 1] = ((s ushr 8) and 0xff).toByte()
            out[w * 4 + 2] = ((s ushr 16) and 0xff).toByte()
            out[w * 4 + 3] = ((s ushr 24) and 0xff).toByte()
        }
        return out.toHex()
    }

    /**
     * The non-linear mixing function for the [op]-th operation. RFC 1321 §3.4.
     *  - op in  0..15: F(B,C,D) = (B AND C) OR ((NOT B) AND D)
     *  - op in 16..31: G(B,C,D) = (D AND B) OR ((NOT D) AND C)
     *  - op in 32..47: H(B,C,D) = B XOR C XOR D
     *  - op in 48..63: I(B,C,D) = C XOR (B OR (NOT D))
     */
    private fun roundFunc(op: Int, a: Int, b: Int, c: Int, d: Int): Int = when (op ushr 4) {
        0 -> (b and c) or ((b.inv()) and d)
        1 -> (d and b) or ((d.inv()) and c)
        2 -> b xor c xor d
        else -> c xor (b or d.inv())
    }

    /**
     * The message word index for the [op]-th operation. RFC 1321 §3.4.
     *  - Round 1 ( 0..15): g = op
     *  - Round 2 (16..31): g = (5*op + 1) mod 16
     *  - Round 3 (32..47): g = (3*op + 5) mod 16
     *  - Round 4 (48..63): g = (7*op)     mod 16
     */
    private fun wordIndex(op: Int): Int = when (op ushr 4) {
        0 -> op
        1 -> (5 * op + 1) % 16
        2 -> (3 * op + 5) % 16
        else -> (7 * op) % 16
    }

    private fun leftRotate(x: Int, n: Int): Int =
        ((x shl n) or (x ushr (32 - n))) and 0xffffffff.toInt()

    // Per-round shift amounts. RFC 1321 §3.4.
    private val S = intArrayOf(
        7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
        5,  9, 14, 20, 5,  9, 14, 20, 5,  9, 14, 20, 5,  9, 14, 20,
        4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
        6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
    )

    // Pre-computed constants: floor(2^32 * abs(sin(i+1))). RFC 1321 §3.4.
    // Every value is 32 bits; we use `0xXXXXXXXX.toInt()` for the
    // ones whose high bit is set so Kotlin doesn't widen them to Long
    // (which would then fail the intArrayOf inference).
    private val K = intArrayOf(
        0xd76aa478.toInt(), 0xe8c7b756.toInt(), 0x242070db.toInt(), 0xc1bdceee.toInt(),
        0xf57c0faf.toInt(), 0x4787c62a.toInt(), 0xa8304613.toInt(), 0xfd469501.toInt(),
        0x698098d8.toInt(), 0x8b44f7af.toInt(), 0xffff5bb1.toInt(), 0x895cd7be.toInt(),
        0x6b901122.toInt(), 0xfd987193.toInt(), 0xa679438e.toInt(), 0x49b40821.toInt(),
        0xf61e2562.toInt(), 0xc040b340.toInt(), 0x265e5a51.toInt(), 0xe9b6c7aa.toInt(),
        0xd62f105d.toInt(), 0x02441453.toInt(), 0xd8a1e681.toInt(), 0xe7d3fbc8.toInt(),
        0x21e1cde6.toInt(), 0xc33707d6.toInt(), 0xf4d50d87.toInt(), 0x455a14ed.toInt(),
        0xa9e3e905.toInt(), 0xfcefa3f8.toInt(), 0x676f02d9.toInt(), 0x8d2a4c8a.toInt(),
        0xfffa3942.toInt(), 0x8771f681.toInt(), 0x6d9d6122.toInt(), 0xfde5380c.toInt(),
        0xa4beea44.toInt(), 0x4bdecfa9.toInt(), 0xf6bb4b60.toInt(), 0xbebfbc70.toInt(),
        0x289b7ec6.toInt(), 0xeaa127fa.toInt(), 0xd4ef3085.toInt(), 0x04881d05.toInt(),
        0xd9d4d039.toInt(), 0xe6db99e5.toInt(), 0x1fa27cf8.toInt(), 0xc4ac5665.toInt(),
        0xf4292244.toInt(), 0x432aff97.toInt(), 0xab9423a7.toInt(), 0xfc93a039.toInt(),
        0x655b59c3.toInt(), 0x8f0ccc92.toInt(), 0xffeff47d.toInt(), 0x85845dd1.toInt(),
        0x6fa87e4f.toInt(), 0xfe2ce6e0.toInt(), 0xa3014314.toInt(), 0x4e0811a1.toInt(),
        0xf7537e82.toInt(), 0xbd3af235.toInt(), 0x2ad7d2bb.toInt(), 0xeb86d391.toInt(),
    )
}

/** Convert a 16-byte raw MD5 output to 32 lowercase hex chars. */
private fun ByteArray.toHex(): String {
    val sb = StringBuilder(size * 2)
    for (b in this) {
        val v = b.toInt() and 0xff
        sb.append("0123456789abcdef"[v ushr 4])
        sb.append("0123456789abcdef"[v and 0x0f])
    }
    return sb.toString()
}
