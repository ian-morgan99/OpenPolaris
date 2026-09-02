package dev.openpolaris.core.astro

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * One entry in a user's "session log" — the celestial target the user
 * was on at a given moment, with enough context to return to it.
 *
 * Defined by issue #24 (3c.1, sub-claim of #7 "session persistence").
 * The fields are deliberately the smallest set that lets #27 (3c.4,
 * "Return to M31?" reconnect prompt) actually work:
 *
 *  - [id]            — UUIDv4. Stable across app restarts; never reused
 *                       even if the user deletes one and adds another.
 *  - [name]          — user-supplied ("M31") or auto-generated
 *                       ("M31 2026-08-30 22:14"). Shown in the
 *                       reconnect prompt; not used as a key.
 *  - [raHours]       — right ascension in **decimal hours** [0, 24).
 *                       Matches the wire units used elsewhere in the
 *                       app (see [AstroObject.raDeg] for the parallel
 *                       degree representation; conversion is
 *                       trivial: `raHours = raDeg / 15`).
 *  - [decDeg]        — declination in decimal degrees [-90, 90].
 *  - [capturedAtMs]  — wall-clock epoch millis when the user picked
 *                       the target. Drives the "session older than
 *                       24 h, do not auto-prompt" filter in #27.
 *
 * **Where this lives.** The package is `dev.openpolaris.core.astro`
 * because a session marker is a celestial target plus a timestamp,
 * not a connection record. The mount-connection state (host/port/
 * lastMountMode/lastTilt) is tracked separately by the `session`
 * package and is a different concept; do not conflate them. Issue
 * #24's reopen comment makes this distinction explicit: "the
 * current connection marker should remain focused", so this type
 * is the target-identity marker the issue spec asked for.
 *
 * **Wire format.** JSON via `kotlinx.serialization` (no Android
 * dependencies; works in `commonTest`, `jvmTest`, and on-device).
 * Round-trip is tested with 1000 markers in [dev.openpolaris.core.astro.SessionMarkerJsonTest].
 */
@Serializable
data class SessionMarker(
    val id: String,
    val name: String,
    val raHours: Double,
    val decDeg: Double,
    val capturedAtMs: Long,
) {
    init {
        // Range guards. The "Return to M31?" prompt trusts these
        // values to compute a goto; out-of-range RA/Dec would
        // produce a useless or harmful slew, so reject them at the
        // data-class boundary rather than at first use.
        require(raHours in 0.0..24.0) { "raHours out of range [0, 24): $raHours" }
        require(decDeg in -90.0..90.0) { "decDeg out of range [-90, 90]: $decDeg" }
    }

    /**
     * Serialize this marker to its canonical JSON form. Stable for
     * on-disk storage; used by [fromJson] as the inverse and by the
     * round-trip test. Declared at the type level (not in the
     * companion) so callers can write `marker.toJson()` directly.
     */
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        /**
         * JSON codec shared by [toJson] and [fromJson]. `ignoreUnknownKeys`
         * is **off** on purpose: a forward-compatible reader must be a
         * separate, opt-in path. The default round-trip must reject
         * unknown fields, otherwise a future schema with a mandatory
         * field that this class does not yet model would silently load
         * a "valid" marker that drops data on read.
         */
        private val json = Json { encodeDefaults = true }

        /**
         * Parse a [SessionMarker] from JSON. Returns `Result.failure`
         * with a [SerializationException] (or [IllegalArgumentException]
         * for range/format violations) rather than throwing, so the
         * caller can decide what to do with a bad file — typical UI
         * behavior is to surface "session log corrupted, start fresh".
         */
        fun fromJson(text: String): Result<SessionMarker> = try {
            Result.success(json.decodeFromString(serializer(), text))
        } catch (e: SerializationException) {
            Result.failure(e)
        } catch (e: IllegalArgumentException) {
            // init {} range guards throw IAE on bad data. Wrap so the
            // caller sees a single failure type, not a mixed bag.
            Result.failure(e)
        }

        /**
         * Auto-name a marker when the user did not supply one. Format
         * matches the issue spec example: "<designation> yyyy-MM-dd HH:mm"
         * — local time, ISO-style, minute precision. Used by the UI
         * when the user hits "Save" on a target with no name field.
         */
        fun autoName(
            designation: String,
            nowEpochMs: Long,
        ): String {
            val (y, mo, d, hh, mm) = civilFromEpochMs(nowEpochMs)
            // Hand-rolled zero-padded formatter: JVM-only String.format
            // ("%04d") is unavailable in commonMain. Negative years are
            // not produced by civilFromDays() (Hinnant's algorithm always
            // returns a non-negative year for the proleptic Gregorian
            // calendar), so we can pad to a fixed width without a sign.
            return buildString {
                append(designation)
                append(' ')
                append(y.toString().padStart(4, '0'))
                append('-')
                append(mo.toString().padStart(2, '0'))
                append('-')
                append(d.toString().padStart(2, '0'))
                append(' ')
                append(hh.toString().padStart(2, '0'))
                append(':')
                append(mm.toString().padStart(2, '0'))
            }
        }

        /** Five-tuple (year, month, day, hour, minute) in UTC. */
        private fun civilFromEpochMs(epochMs: Long): IntArray {
            val totalMin = epochMs / 60_000L
            val days = totalMin / 1_440L
            val rem = totalMin - days * 1_440L
            val hh = (rem / 60L).toInt()
            val mm = (rem % 60L).toInt()
            val (y, mo, d) = civilFromDays(days)
            return intArrayOf(y, mo, d, hh, mm)
        }

        // Proleptic Gregorian — Hinnant's date.h, ported to Kotlin.
        // Returns (year, month, day) for the given days-since-Unix-epoch
        // (1970-01-01 = 0). Stable across platforms without java.time.
        private fun civilFromDays(days: Long): Triple<Int, Int, Int> {
            // Shift Unix epoch (1970-01-01) to Hinnant's epoch (0000-03-01):
            // 1970-01-01 is day 0 from Unix, day 719468 from 0000-03-01.
            val z = days + 719_468L
            val z2 = if (z >= 0) z else z - 146_096L
            val era = z2 / 146_097L
            val doe = (z2 - era * 146_097L).toInt() // [0, 146_096]
            val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146_096) / 365 // [0, 399]
            val y = yoe + (era * 400L).toInt()
            val doy = doe - (365 * yoe + yoe / 4 - yoe / 100) // [0, 365]
            val mp = (5 * doy + 2) / 153 // [0, 11]
            val d = doy - (153 * mp + 2) / 5 + 1 // [1, 31]
            val m = if (mp < 10) mp + 3 else mp - 9 // [1, 12]
            val yr = if (m <= 2) y + 1 else y
            return Triple(yr, m, d)
        }
    }
}
