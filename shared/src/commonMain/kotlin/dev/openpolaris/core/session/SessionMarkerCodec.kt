package dev.openpolaris.core.session

import dev.openpolaris.core.domain.MountMode
import dev.openpolaris.core.io.Utf8

/**
 * FNV-1a 64-bit checksum and the canonical "fields-for-hashing" string form.
 *
 * Lives next to [SessionMarker] so the checksum algorithm is documented in one
 * place. The fields hashed exclude [SessionMarker.checksum] itself (otherwise
 * the digest would always be zero). The field order matches the constructor
 * declaration so a future maintainer reading "what was hashed" has a single
 * answer.
 *
 * FNV-1a is intentionally not cryptographic — the threat model is "file got
 * truncated by a power loss", not "attacker wrote a malicious marker". A
 * malicious marker can set the host to anything it likes; the [SessionStore]
 * read path caps the file at 4 KiB and ignores unknown fields, and the
 * `AppViewModel` re-prompts the user before connecting on a restored marker
 * so a tampered host requires user consent.
 */
internal object SessionMarkerCodec {

    // FNV-1a 64-bit: offset basis = 0xcbf29ce484222325, prime = 0x100000001b3
    // (both as unsigned; represented here as Long to fit Kotlin's signed type)
    private const val FNV_OFFSET_BASIS: Long = -3750763034362895579L // 0xcbf29ce484222325 as signed Long
    private const val FNV_PRIME_64: Long = 1099511628211L           // 0x100000001b3

    /**
     * The fields of [SessionMarker] that contribute to the checksum, in a stable
     * order. MountMode is hashed as its `name` (string) so that reordering
     * enum constants does not silently shift the digest.
     */
    fun fieldsForHashing(m: SessionMarker): List<Pair<String, String>> = listOf(
        "host" to m.host,
        "port" to m.port.toString(),
        "lastConnectedAtEpochMs" to m.lastConnectedAtEpochMs.toString(),
        "lastMountMode" to m.lastMountMode.name,
        "lastTrackingStarted" to m.lastTrackingStarted.toString(),
        "lastRollDeg" to m.lastRollDeg.toString(),
        "lastPitchDeg" to m.lastPitchDeg.toString(),
        "schemaVersion" to m.schemaVersion.toString(),
    )

    /**
     * FNV-1a 64-bit over the canonical form. Returns a Long whose high bit may
     * be set — callers that need a printable form should format it unsigned.
     */
    fun checksumOf(m: SessionMarker): Long {
        val pairs = fieldsForHashing(m)
        val canonical = buildString(capacity = pairs.size * 24) {
            for ((k, v) in pairs) {
                append(k).append('=').append(v).append(';')
            }
        }
        return fnv1a64(Utf8.encode(canonical))
    }

    /** FNV-1a 64-bit, byte-at-a-time. Public so tests can sanity-check the algorithm. */
    fun fnv1a64(bytes: ByteArray): Long {
        var hash = FNV_OFFSET_BASIS
        for (b in bytes) {
            hash = hash xor (b.toLong() and 0xFF)
            hash *= FNV_PRIME_64
        }
        return hash
    }

    /** Verify a marker in one call. The marker's [SessionMarker.checksum] is trusted only for the compare. */
    fun verify(m: SessionMarker): Boolean = checksumOf(m) == m.checksum

    /**
     * Helper used by [SessionStore.write] to fill in [SessionMarker.checksum].
     * Preserves the rest of the marker byte-for-byte (Kotlin data class
     * `copy` is reference-stable for the unchanged fields).
     */
    fun withChecksum(m: SessionMarker): SessionMarker = m.copy(checksum = checksumOf(m))

    /** Marker with checksum zero, for use in unit tests that want to feed a hand-rolled body. */
    fun zeroChecksumOf(host: String, port: Int, lastConnectedAtEpochMs: Long): SessionMarker =
        SessionMarker(
            host = host,
            port = port,
            lastConnectedAtEpochMs = lastConnectedAtEpochMs,
            lastMountMode = MountMode.UNKNOWN,
            lastTrackingStarted = false,
            lastRollDeg = null,
            lastPitchDeg = null,
            schemaVersion = SessionMarker.SCHEMA_VERSION,
            checksum = 0L,
        )
}
