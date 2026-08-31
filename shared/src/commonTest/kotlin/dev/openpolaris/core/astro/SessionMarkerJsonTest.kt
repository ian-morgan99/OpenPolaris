package dev.openpolaris.core.astro

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Round-trip and failure-mode tests for [SessionMarker] JSON
 * serialization (issue #24 / 3c.1).
 *
 * The acceptance criteria are:
 *  - 1000 markers round-trip through `toJson()` / `fromJson()` with no
 *    data loss.
 *  - Invalid JSON returns `Result.failure` with a typed parse error,
 *    not a thrown exception.
 *
 * Bonus tests cover the data-class `init {}` range guards and the
 * `autoName` helper because they are part of the same type's
 * contract — a marker that round-trips but has out-of-range RA/Dec
 * is still a broken marker.
 */
class SessionMarkerJsonTest {

    private fun sample(
        id: String = "11111111-1111-4111-8111-111111111111",
        name: String = "M31",
        raHours: Double = 0.7123,
        decDeg: Double = 41.2691,
        capturedAtMs: Long = 1_757_635_200_000L, // 2025-09-12 00:00 UTC
    ) = SessionMarker(id, name, raHours, decDeg, capturedAtMs)

    @Test
    fun roundTripsSingleMarker() {
        val original = sample()
        val parsed = SessionMarker.fromJson(original.toJson()).getOrThrow()
        assertEquals(original, parsed)
    }

    @Test
    fun roundTripsOneThousandMarkers() {
        // Deterministic generator: f(i) is reproducible across runs.
        // The variety of RA/Dec/capturedAt values stresses all three
        // numeric fields, not just one.
        val markers = (0 until 1_000).map { i ->
            val ra = (i * 0.02397) % 24.0 // pseudorandom in [0, 24)
            val dec = -89.5 + (i * 0.179) % 179.0 // pseudorandom in [-89.5, 89.5]
            val captured = 1_700_000_000_000L + i.toLong() * 60_000L
            SessionMarker(
                id = "00000000-0000-4000-8000-%012d".format(i),
                name = "marker-%04d".format(i),
                raHours = ra,
                decDeg = dec,
                capturedAtMs = captured,
            )
        }
        val roundTripped = markers.map {
            SessionMarker.fromJson(it.toJson()).getOrThrow()
        }
        // No data loss across all 1_000.
        assertEquals(markers, roundTripped)
    }

    @Test
    fun invalidJsonReturnsResultFailure() {
        val result = SessionMarker.fromJson("{ this is not json")
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertNotNull(ex)
        // Spec says "typed parse error" — the catch site narrows to
        // SerializationException for malformed JSON. assertIs here
        // documents that contract.
        assertIs<kotlinx.serialization.SerializationException>(ex)
    }

    @Test
    fun wrongFieldTypeReturnsFailure() {
        // raHours must be a number; "abc" fails at the type level.
        val bad = """{"id":"a","name":"b","raHours":"abc","decDeg":0.0,"capturedAtMs":0}"""
        val result = SessionMarker.fromJson(bad)
        assertTrue(result.isFailure)
    }

    @Test
    fun outOfRangeRaRejectedAtConstruction() {
        // Boundary check: data class init {} enforces [0, 24].
        assertFailsWith<IllegalArgumentException> {
            sample(raHours = -0.0001)
        }
        assertFailsWith<IllegalArgumentException> {
            sample(raHours = 24.0001)
        }
    }

    @Test
    fun outOfRangeDecRejectedAtConstruction() {
        assertFailsWith<IllegalArgumentException> {
            sample(decDeg = -90.0001)
        }
        assertFailsWith<IllegalArgumentException> {
            sample(decDeg = 90.0001)
        }
    }

    @Test
    fun outOfRangeInJsonFailsNotThrows() {
        // The JSON path must report the same IAE via Result.failure,
        // not propagate it. The user's stored file with a corrupt
        // marker should not crash the app.
        val bad = """{"id":"a","name":"b","raHours":99.0,"decDeg":0.0,"capturedAtMs":0}"""
        val result = SessionMarker.fromJson(bad)
        assertTrue(result.isFailure)
        // IAE is wrapped — verify it is *not* a SerializationException
        // (a different failure mode the caller may want to distinguish).
        assertIs<IllegalArgumentException>(result.exceptionOrNull())
    }

    @Test
    fun autoNameMatchesIssueSpecExample() {
        // 2026-08-30 22:14 UTC. The issue spec quotes the format
        // "M31 2026-08-30 22:14" so this is the canonical example.
        val ts = 1_788_128_040_000L
        val name = SessionMarker.autoName("M31", ts)
        assertEquals("M31 2026-08-30 22:14", name)
    }

    @Test
    fun autoNameHandlesEpochZero() {
        // 1970-01-01 00:00 UTC — boundary check that the
        // proleptic-Gregorian helper does not underflow.
        assertEquals("X 1970-01-01 00:00", SessionMarker.autoName("X", 0L))
    }

    @Test
    fun autoNameHandlesLeapDay() {
        // 2024-02-29 12:00 UTC — exercises the leap-year branch.
        val ts = 1_709_208_000_000L
        assertEquals("X 2024-02-29 12:00", SessionMarker.autoName("X", ts))
    }
}
