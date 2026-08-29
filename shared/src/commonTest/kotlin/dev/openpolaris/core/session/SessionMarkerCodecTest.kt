package dev.openpolaris.core.session

import dev.openpolaris.core.domain.MountMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 3c.1: pure-function tests for the FNV-1a codec.
 *
 *  - Pin the known FNV-1a vectors so a future "optimization" can't silently
 *    corrupt the digest.
 *  - Pin the property that the checksum covers each of the 8 non-checksum
 *    fields, so adding a 9th field forces a re-think (not a silent bit flip).
 *  - Pin the property that [MountMode] is hashed by its **name**, not its
 *    ordinal, so reordering the enum later doesn't invalidate on-disk markers.
 */
class SessionMarkerCodecTest {

    private fun mk(
        host: String = "10.0.0.7",
        port: Int = 9090,
        lastConnectedAtEpochMs: Long = 1_700_000_000_000L,
        lastMountMode: MountMode = MountMode.ASTRO,
        lastTrackingStarted: Boolean = true,
        lastRollDeg: Double = 0.5,
        lastPitchDeg: Double = -0.25,
        schemaVersion: Int = SessionMarker.SCHEMA_VERSION,
        checksum: Long = 0L,
    ) = SessionMarker(
        host = host,
        port = port,
        lastConnectedAtEpochMs = lastConnectedAtEpochMs,
        lastMountMode = lastMountMode,
        lastTrackingStarted = lastTrackingStarted,
        lastRollDeg = lastRollDeg,
        lastPitchDeg = lastPitchDeg,
        schemaVersion = schemaVersion,
        checksum = checksum,
    )

    // -------- FNV-1a known vectors (http://www.isthe.com/chongo/tech/comp/fnv/) --------

    @Test
    fun fnv1a_emptyString() {
        assertEquals(-3750763034362895579L, SessionMarkerCodec.fnv1a64(ByteArray(0)))
    }

    @Test
    fun fnv1a_singleCharA() {
        // FNV-1a 64-bit of "a" = 0xaf63dc4c8601ec8c = -5808556873153909620 as signed Long.
        assertEquals(-5808556873153909620L, SessionMarkerCodec.fnv1a64("a".toByteArray()))
    }

    @Test
    fun fnv1a_foobar() {
        // FNV-1a 64-bit of "foobar" = 0x85944171f73967e8 = -8821353812377114648 as signed Long.
        assertEquals(-8821353812377114648L, SessionMarkerCodec.fnv1a64("foobar".toByteArray()))
    }

    // -------- checksum property: covers every non-checksum field --------

    @Test
    fun differentHostChangesDigest() {
        assertNotEquals(
            SessionMarkerCodec.checksumOf(mk(host = "10.0.0.7")),
            SessionMarkerCodec.checksumOf(mk(host = "10.0.0.8")),
        )
    }

    @Test
    fun differentPortChangesDigest() {
        assertNotEquals(
            SessionMarkerCodec.checksumOf(mk(port = 9090)),
            SessionMarkerCodec.checksumOf(mk(port = 9091)),
        )
    }

    @Test
    fun differentMountModeChangesDigest() {
        assertNotEquals(
            SessionMarkerCodec.checksumOf(mk(lastMountMode = MountMode.ASTRO)),
            SessionMarkerCodec.checksumOf(mk(lastMountMode = MountMode.PANORAMA)),
        )
    }

    @Test
    fun differentRollChangesDigest() {
        assertNotEquals(
            SessionMarkerCodec.checksumOf(mk(lastRollDeg = 0.5)),
            SessionMarkerCodec.checksumOf(mk(lastRollDeg = 0.6)),
        )
    }

    @Test
    fun differentPitchChangesDigest() {
        assertNotEquals(
            SessionMarkerCodec.checksumOf(mk(lastPitchDeg = -0.25)),
            SessionMarkerCodec.checksumOf(mk(lastPitchDeg = -0.26)),
        )
    }

    @Test
    fun differentSchemaVersionChangesDigest() {
        assertNotEquals(
            SessionMarkerCodec.checksumOf(mk(schemaVersion = 1)),
            SessionMarkerCodec.checksumOf(mk(schemaVersion = 2)),
        )
    }

    @Test
    fun differentLastConnectedAtChangesDigest() {
        assertNotEquals(
            SessionMarkerCodec.checksumOf(mk(lastConnectedAtEpochMs = 1_700_000_000_000L)),
            SessionMarkerCodec.checksumOf(mk(lastConnectedAtEpochMs = 1_700_000_000_001L)),
        )
    }

    @Test
    fun differentTrackingStartedChangesDigest() {
        assertNotEquals(
            SessionMarkerCodec.checksumOf(mk(lastTrackingStarted = true)),
            SessionMarkerCodec.checksumOf(mk(lastTrackingStarted = false)),
        )
    }

    // -------- checksum ignores its own field (re-running is stable) --------

    @Test
    fun checksumIsIndependentOfOwnField() {
        val a = SessionMarkerCodec.checksumOf(mk(checksum = 0L))
        val b = SessionMarkerCodec.checksumOf(mk(checksum = 0xDEADBEEFL))
        assertEquals(a, b, "checksum field must not contribute to its own digest")
    }

    @Test
    fun checksumIsStableAcrossRuns() {
        val m = mk()
        assertEquals(SessionMarkerCodec.checksumOf(m), SessionMarkerCodec.checksumOf(m))
    }

    // -------- verify() positive / negative --------

    @Test
    fun verifyAcceptsCorrectChecksum() {
        val signed = SessionMarkerCodec.withChecksum(mk())
        assertTrue(SessionMarkerCodec.verify(signed))
    }

    @Test
    fun verifyRejectsWrongChecksum() {
        val tampered = mk(checksum = 0x0123456789ABCDEFL)
        assertFalse(SessionMarkerCodec.verify(tampered))
    }

    @Test
    fun verifyRejectsAfterFieldMutationEvenIfChecksumWasCorrect() {
        val signed = SessionMarkerCodec.withChecksum(mk())
        val mutated = signed.copy(host = "10.0.0.99")
        assertFalse(SessionMarkerCodec.verify(mutated))
    }

    // -------- withChecksum overwrites any caller-supplied checksum --------

    @Test
    fun withChecksumOverwritesSuppliedChecksum() {
        val tampered = mk(checksum = 0x0123456789ABCDEFL)
        val fixed = SessionMarkerCodec.withChecksum(tampered)
        // The fixed marker should have a *real* digest, not the supplied sentinel.
        assertNotEquals(0x0123456789ABCDEFL, fixed.checksum)
        assertTrue(SessionMarkerCodec.verify(fixed))
    }

    // -------- MountMode is hashed by name, not ordinal --------
    //  Pins the "reorder the enum" stability promise. If you intentionally
    //  break this, the test must be updated AND the on-disk migration story
    //  must be revisited.

    @Test
    fun mountModeHashedByNameNotOrdinal() {
        val astro = mk(lastMountMode = MountMode.ASTRO)
        val fields = SessionMarkerCodec.fieldsForHashing(astro)
        val (_, v) = fields.first { (k, _) -> k == "lastMountMode" }
        assertEquals("ASTRO", v, "MountMode.ASTRO must serialize as 'ASTRO', not '2' (the ordinal)")
        assertFalse(v.toIntOrNull() != null, "name must be a String, not an int-formatted ordinal")
    }

    // -------- fieldsForHashing is order-stable (the contract the codec promises) --------

    @Test
    fun fieldsForHashingIsOrderStable() {
        val f = SessionMarkerCodec.fieldsForHashing(mk())
        val keys = f.map { it.first }
        assertEquals(
            listOf(
                "host", "port", "lastConnectedAtEpochMs", "lastMountMode",
                "lastTrackingStarted", "lastRollDeg", "lastPitchDeg", "schemaVersion",
            ),
            keys,
            "Adding or reordering fields here changes every marker's digest on disk",
        )
    }
}
