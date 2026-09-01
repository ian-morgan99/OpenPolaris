package dev.openpolaris.core.session

import dev.openpolaris.core.domain.MountMode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 3c.1: Serialization round-trip + schemaVersion behaviour.
 *
 *  - Round-trip preserves the marker.
 *  - `ignoreUnknownKeys = true` lets a v1 reader accept a v2 marker's extra
 *    field without crashing (forward compatibility).
 *  - Unknown enum values fail to parse (we don't have `coerceInputValues`).
 */
class SessionMarkerJsonTest {

    private val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        isLenient = false
        encodeDefaults = true
    }

    private fun sample() = SessionMarker(
        host = "10.0.0.7",
        port = 9090,
        lastConnectedAtEpochMs = 1_700_000_000_000L,
        lastMountMode = MountMode.ASTRO,
        lastTrackingStarted = true,
        lastRollDeg = 0.5,
        lastPitchDeg = -0.25,
        schemaVersion = SessionMarker.SCHEMA_VERSION,
        checksum = 0L, // will be overwritten by withChecksum
    )

    @Test
    fun roundTripEquals() {
        val original = SessionMarkerCodec.withChecksum(sample())
        val text = json.encodeToString(original)
        val parsed = json.decodeFromString<SessionMarker>(text)
        assertEquals(original, parsed)
    }

    @Test
    fun forwardCompatibleWithExtraField() {
        val signed = SessionMarkerCodec.withChecksum(sample())
        val text = json.encodeToString(signed)
        // A v2 writer added a field that v1 doesn't know about.
        val v2Text = text.replace(
            "\"checksum\":",
            "\"futureField\":\"hello\",\"checksum\":",
        )
        val parsed = json.decodeFromString<SessionMarker>(v2Text)
        assertEquals(signed, parsed, "ignoreUnknownKeys must let v1 read v2 markers")
    }

    @Test
    fun rejectsMalformedJson() {
        assertNull(runCatching { json.decodeFromString<SessionMarker>("{not json") }.getOrNull())
    }

    @Test
    fun rejectsJsonWithWrongFieldType() {
        // port is Int; passing a String here must fail at the parser.
        val bad = """
            {"host":"x","port":"not-a-number","lastConnectedAtEpochMs":0,
             "lastMountMode":"ASTRO","lastTrackingStarted":false,
             "lastRollDeg":0.0,"lastPitchDeg":0.0,"schemaVersion":1,"checksum":0}
        """.trimIndent()
        assertNull(runCatching { json.decodeFromString<SessionMarker>(bad) }.getOrNull())
    }

    @Test
    fun schemaVersionConstantIsPinned() {
        // Bumping SCHEMA_VERSION is a breaking change for on-disk markers.
        // If you bump it, the test that pins the value to 1 must change too.
        assertEquals(1, SessionMarker.SCHEMA_VERSION)
    }

    @Test
    fun encodeDefaultsIncludesAllFields() {
        // `encodeDefaults = true` so a deserialized v2 marker that lacks a v2
        // field doesn't fail to round-trip (v2 readers can still reconstruct
        // a v1 marker from a v2 file).
        val text = json.encodeToString(sample())
        for (field in listOf("host", "port", "lastConnectedAtEpochMs",
                "lastMountMode", "lastTrackingStarted", "lastRollDeg",
                "lastPitchDeg", "schemaVersion", "checksum")) {
            assertTrue(field in text, "expected field '$field' in encoded marker: $text")
        }
    }

    @Test
    fun unknownMountModeFailsParsing() {
        // A v1 reader sees an unknown enum value — we don't have
        // `coerceInputValues`, so the parser must reject, not silently
        // map to UNKNOWN. (If we ever want UNKNOWN-as-fallback semantics
        // we'll need to revisit, but the safe default is to fail loud.)
        val bad = """
            {"host":"x","port":9090,"lastConnectedAtEpochMs":0,
             "lastMountMode":"FUTURE_MODE","lastTrackingStarted":false,
             "lastRollDeg":0.0,"lastPitchDeg":0.0,"schemaVersion":1,"checksum":0}
        """.trimIndent()
        val parsed = runCatching { json.decodeFromString<SessionMarker>(bad) }.getOrNull()
        assertNull(parsed, "unknown enum must fail to parse")
    }

    @Test
    fun serializingThenReReadingStillVerifies() {
        // The real consumer flow: store writes with checksum → reader loads → verify passes.
        val signed = SessionMarkerCodec.withChecksum(sample())
        val text = json.encodeToString(signed)
        val parsed = json.decodeFromString<SessionMarker>(text)
        assertNotNull(parsed)
        assertTrue(SessionMarkerCodec.verify(parsed))
    }

    // -------- 3d: nullable tilt (no schema bump) --------

    @Test
    fun nullTiltRoundTrips() {
        val m = sample().copy(lastRollDeg = null, lastPitchDeg = null)
        val signed = SessionMarkerCodec.withChecksum(m)
        val text = json.encodeToString(signed)
        val parsed = json.decodeFromString<SessionMarker>(text)
        assertEquals(signed, parsed)
        assertNull(parsed.lastRollDeg, "null must round-trip as null, not default to 0.0")
        assertNull(parsed.lastPitchDeg)
        assertTrue(SessionMarkerCodec.verify(parsed))
    }

    @Test
    fun legacyV1MarkerWithZeroTiltStillDeserializesCleanly() {
        // 3d: a v1 marker written before nullability (lastRollDeg = 0.0)
        // must still parse into the new nullable type without losing its
        // checksum — no schema bump. The legacy "0.0" value maps to a
        // non-null Double? slot, exactly as it always did; the type
        // loosening is purely a Kotlin-level improvement.
        val legacy = sample().copy(lastRollDeg = 0.0, lastPitchDeg = 0.0)
        val signed = SessionMarkerCodec.withChecksum(legacy)
        val text = json.encodeToString(signed)
        val parsed = json.decodeFromString<SessionMarker>(text)
        assertEquals(0.0, parsed.lastRollDeg)
        assertEquals(0.0, parsed.lastPitchDeg)
        assertTrue(SessionMarkerCodec.verify(parsed))
    }

    @Test
    fun encodeDefaultsIncludesAllFieldsForNullTilt() {
        // `encodeDefaults = true` must still emit the tilt fields when they're
        // null, otherwise a v1 reader (with `ignoreUnknownKeys = true`) would
        // see a marker missing the field and (in this codebase) the codec
        // would still produce a stable digest because the field order is
        // pinned, but the explicit "field is present, value is null" form is
        // what makes on-disk markers round-trippable.
        val m = sample().copy(lastRollDeg = null, lastPitchDeg = null)
        val text = json.encodeToString(m)
        assertTrue("\"lastRollDeg\":null" in text, "expected 'lastRollDeg':null in: $text")
        assertTrue("\"lastPitchDeg\":null" in text, "expected 'lastPitchDeg':null in: $text")
    }
}
