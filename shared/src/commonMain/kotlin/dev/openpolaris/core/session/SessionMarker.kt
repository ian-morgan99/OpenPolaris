package dev.openpolaris.core.session

import dev.openpolaris.core.domain.MountMode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Persisted "last known session" record. Written when the app disconnects cleanly so
 * the next launch can offer an auto-reconnect prompt (or auto-reconnect outright).
 *
 * 3c.1 from issue #7. The fields are deliberately broader than just host/port:
 *
 *  - [host] / [port] identify the mount on the LAN.
 *  - [lastConnectedAtEpochMs] lets the UI / `SessionStore` age the marker
 *    ("session is older than N days, don't auto-prompt"). Set at write time
 *    by the caller (typically `AppViewModel`); `SessionStore` does not read
 *    a clock of its own.
 *  - [lastMountMode] and [lastTrackingStarted] let the UI show
 *    "Resume in astro mode, tracking on?" instead of restarting blind.
 *  - [lastRollDeg] / [lastPitchDeg] capture the freshest tilt from
 *    `AutoLevelController.tilt` so the user can compare against a fresh
 *    tilt read on reconnect. Both are nullable: `null` means "no 517
 *    frame had landed when the marker was written" (e.g. the very first
 *    `connect()` after power-up, where 517 is the second poll). Pre-3d
 *    we wrote 0.0 in that case, which the UI then displayed as "you were
 *    at roll 0.0°" — a real first-class bug (data was invented out of
 *    thin air). Nullable types now propagate the truth end-to-end.
 *  - [schemaVersion] is the forward-compat seam: a v1 reader must accept
 *    a missing v2 field (`Json { ignoreUnknownKeys = true }`) and a v1
 *    reader must reject a v2 marker by **schemaVersion** so it never
 *    pretends to understand fields it does not.
 *  - [checksum] is FNV-1a 64-bit over the canonical string form of the
 *    other fields (see [SessionMarkerCodec.checksumOf]). Non-cryptographic;
 *    sufficient to detect truncation / bit rot, NOT a security boundary.
 *
 * Wire-format note: making the tilt fields nullable does NOT change the
 * JSON form for non-null values (a legacy v1 marker with `"lastRollDeg":0.0`
 * still deserializes to `lastRollDeg = 0.0`, just into a `Double?` slot),
 * so no schema bump is required and existing on-disk markers continue to
 * verify their checksums.
 */
@Serializable
data class SessionMarker(
    val host: String,
    val port: Int,
    val lastConnectedAtEpochMs: Long,
    val lastMountMode: MountMode = MountMode.UNKNOWN,
    val lastTrackingStarted: Boolean = false,
    val lastRollDeg: Double? = null,
    val lastPitchDeg: Double? = null,
    @SerialName("schemaVersion")
    val schemaVersion: Int = SCHEMA_VERSION,
    val checksum: Long = 0L,
) {
    companion object {
        /** Bump when the field set changes. A reader with a lower version rejects the marker. */
        const val SCHEMA_VERSION: Int = 1
    }
}
