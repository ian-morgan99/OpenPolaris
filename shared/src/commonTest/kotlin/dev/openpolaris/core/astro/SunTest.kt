package dev.openpolaris.core.astro

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Low-precision solar ephemeris sanity. Real ephemerides require JPL DE
 * for arc-second accuracy; this is good enough for "where is the sun
 * in the sky right now" UI affordances.
 */
class SunTest {

    @Test
    fun sunAtJ2000Epoch() {
        // J2000.0 (2000-01-01T12:00:00 UT) — sun geocentric RA is ~18h 42m
        // (≈ 280.5°), Dec ~ -23.0°. Allow 2° tolerance (low-precision).
        val jd = AstroMath.julianDate(2000, 1, 1, 12, 0, 0)
        val pos = Sun.position(jd)
        assertTrue(pos.raDeg in 278.0..283.0, "sun RA out of range: ${pos.raDeg}")
        assertTrue(pos.decDeg in -25.0..-21.0, "sun Dec out of range: ${pos.decDeg}")
    }

    @Test
    fun sunIsNorthOfCelestialEquatorInJune() {
        // 2020-06-21 (June solstice) — sun declination is +23.44° ± 1° at
        // the moment of solstice, but on a noon UT the day is roughly
        // within 0.5° of maximum.
        val jd = AstroMath.julianDate(2020, 6, 21, 12, 0, 0)
        val dec = Sun.decDeg(jd)
        assertTrue(dec > 22.0, "expected sun Dec > +22° at June solstice, got $dec")
        assertTrue(dec < 24.5, "expected sun Dec < +24.5° at June solstice, got $dec")
    }

    @Test
    fun sunIsSouthOfCelestialEquatorInDecember() {
        val jd = AstroMath.julianDate(2020, 12, 21, 12, 0, 0)
        val dec = Sun.decDeg(jd)
        assertTrue(dec < -22.0, "expected sun Dec < -22° at Dec solstice, got $dec")
        assertTrue(dec > -24.5, "expected sun Dec > -24.5° at Dec solstice, got $dec")
    }

    @Test
    fun sunRaAdvancesMonotonicallyOverADay() {
        val jd0 = AstroMath.julianDate(2024, 3, 15, 0, 0, 0)
        val jd1 = jd0 + 1.0
        val ra0 = Sun.raDeg(jd0)
        val ra1 = Sun.raDeg(jd1)
        // Sun moves ~1°/day eastward, but RA wraps at 360. Handle wrap.
        val delta = ((ra1 - ra0) + 360.0) % 360.0
        assertTrue(delta in 0.8..1.2, "expected sun ΔRA ≈ 1°/day, got $delta")
    }
}
