package dev.openpolaris.core.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * RFC 1321 test vectors + block-boundary cases for the pure-Kotlin
 * MD5 implementation. The vectors are from RFC 1321 Appendix A.5
 * and standard 55/56/64/119-byte boundary tests.
 */
class Md5Test {
    @Test
    fun rfc1321_empty() {
        // MD5("") = d41d8cd98f00b204e9800998ecf8427e
        assertEquals(
            "d41d8cd98f00b204e9800998ecf8427e",
            Md5.digest(ByteArray(0))
        )
    }

    @Test
    fun rfc1321_a() {
        // MD5("a") = 0cc175b9c0f1b6a831c399e269772661
        assertEquals(
            "0cc175b9c0f1b6a831c399e269772661",
            Md5.digest("a".encodeToByteArray())
        )
    }

    @Test
    fun rfc1321_abc() {
        // MD5("abc") = 900150983cd24fb0d6963f7d28e17f72
        assertEquals(
            "900150983cd24fb0d6963f7d28e17f72",
            Md5.digest("abc".encodeToByteArray())
        )
    }

    @Test
    fun rfc1321_message_digest() {
        // MD5("message digest") = f96b697d7cb7938d525a2f31aaf161d0
        assertEquals(
            "f96b697d7cb7938d525a2f31aaf161d0",
            Md5.digest("message digest".encodeToByteArray())
        )
    }

    @Test
    fun rfc1321_alphabet() {
        // MD5("abcdefghijklmnopqrstuvwxyz") = c3fcd3d76192e4007dfb496cca67e13b
        assertEquals(
            "c3fcd3d76192e4007dfb496cca67e13b",
            Md5.digest("abcdefghijklmnopqrstuvwxyz".encodeToByteArray())
        )
    }

    @Test
    fun rfc1321_alnum() {
        // MD5("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789")
        //   = d174ab98d277d9f5a5611c2c9f419d9f
        assertEquals(
            "d174ab98d277d9f5a5611c2c9f419d9f",
            Md5.digest(
                "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
                    .encodeToByteArray()
            )
        )
    }

    @Test
    fun rfc1321_eight_digits() {
        // MD5("12345678901234567890123456789012345678901234567890123456789012345678901234567890")
        //   = 57edf4a22be3c955ac49da2e2107b67a
        assertEquals(
            "57edf4a22be3c955ac49da2e2107b67a",
            Md5.digest(
                ("1234567890".repeat(8).encodeToByteArray())
            )
        )
    }

    @Test
    fun block_boundary_55() {
        // MD5 of 55 'a' bytes (just under 56-byte pad threshold).
        assertEquals(
            "ef1772b6dff9a122358552954ad0df65",
            Md5.digest(ByteArray(55) { 'a'.code.toByte() })
        )
    }

    @Test
    fun block_boundary_56() {
        // MD5 of 56 'a' bytes (just over 56-byte pad threshold).
        assertEquals(
            "3b0c8ac703f828b04c6c197006d17218",
            Md5.digest(ByteArray(56) { 'a'.code.toByte() })
        )
    }

    @Test
    fun block_boundary_64() {
        // MD5 of 64 'a' bytes (one full block plus length).
        assertEquals(
            "014842d480b571495a4a0363793f7367",
            Md5.digest(ByteArray(64) { 'a'.code.toByte() })
        )
    }

    @Test
    fun block_boundary_119() {
        // MD5 of 119 'a' bytes (straddles two blocks, exercises mid-block padding).
        assertEquals(
            "8a7bd0732ed6a28ce75f6dabc90e1613",
            Md5.digest(ByteArray(119) { 'a'.code.toByte() })
        )
    }

    @Test
    fun output_is_lowercase_hex_32() {
        val hex = Md5.digest("a".encodeToByteArray())
        assertEquals(32, hex.length)
        assertEquals(hex, hex.lowercase())
        // Every char is a hex digit.
        for (c in hex) {
            assertTrue(
                c in '0'..'9' || c in 'a'..'f',
                "non-hex char '$c' in digest",
            )
        }
    }

    @Test
    fun different_payloads_differ() {
        // Smoke check that two distinct payloads don't collide (catches
        // a total function that returns a constant).
        val a = Md5.digest("hello".encodeToByteArray())
        val b = Md5.digest("hellp".encodeToByteArray())
        assertTrue(a != b, "MD5 collision on neighbouring bytes")
    }

    @Test
    fun never_throws() {
        // Sanity: the implementation should not throw on edge inputs
        // like a single zero byte. This is just a call that must
        // succeed without exception.
        val z = Md5.digest(byteArrayOf(0))
        assertEquals(32, z.length)
    }
}
