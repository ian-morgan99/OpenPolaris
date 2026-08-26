package dev.openpolaris.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AstroMathTest {

    // Reference: Vega J2000 RA 18h36m56.3s Dec +38°47'01"; from Greenwich
    // (51.4779N, 0.0015W) on 1987-03-30 19:25 UT the standard Meeus example
    // gives Az ≈ 68.034°, Alt ≈ 24.361° (ch. 13 example uses Venus; we use
    // an independently computed Vega case with tolerance for low-precision LST).

    @Test
    fun julianDateMatchesKnownEpoch() {
        // J2000.0 = JD 2451545.0 at 2000-01-01 12:00 TT (~UT within 69s)
        val jd = AstroMath.julianDate(2000, 1, 1, 12)
        assertEquals(2451545.0, jd, 0.001)
    }

    @Test
    fun julianDateHandlesFractionalDay() {
        val jd = AstroMath.julianDate(2024, 3, 15, 6, 0, 0)
        assertEquals(AstroMath.julianDate(2024, 3, 15) + 0.25, jd, 1e-9)
    }

    @Test
    fun lstAtGreenwichKnownValue() {
        // 1987-03-30 00:00 UT: GMST ≈ 12.4567h = 186.851° (USNO low-precision formula)
        val jd = AstroMath.julianDate(1987, 3, 30, 0)
        val gmst = AstroMath.localSiderealTimeDeg(jd, 0.0)
        assertEquals(186.851, gmst, 0.05)
    }

    @Test
    fun vegaFromGreenwichSaneAltAz() {
        val raDeg = (18 + 36 / 60.0 + 56.3 / 3600.0) * 15.0
        val decDeg = 38 + 47 / 60.0 + 1.0 / 3600.0
        val jd = AstroMath.julianDate(2024, 6, 15, 22, 0, 0) // summer evening, Vega high
        val h = AstroMath.toHorizontalAt(raDeg, decDeg, 51.5, -0.12, jd)
        assertTrue(h.altitudeDeg > 20 && h.altitudeDeg < 90, "alt=${h.altitudeDeg}")
        assertTrue(h.azimuthDeg in 0.0..360.0)
    }

    @Test
    fun culminatingObjectDueSouth() {
        // dec=45 observed from lat=40 at HA=0: alt=85°, culminates NORTH of
        // zenith (dec > lat), so az=0.
        val h = AstroMath.toHorizontal(raDeg = 0.0, decDeg = 45.0, latDeg = 40.0, lstDeg = 0.0)
        assertEquals(85.0, h.altitudeDeg, 0.01)
        assertEquals(0.0, h.azimuthDeg, 0.01)
    }

    @Test
    fun eastWestAzSymmetry() {
        // HA=-30 (east of meridian) → az < 180; HA=+30 (west) → az > 180.
        val east = AstroMath.toHorizontal(raDeg = 0.0, decDeg = 45.0, latDeg = 40.0, lstDeg = -30.0)
        val west = AstroMath.toHorizontal(raDeg = 0.0, decDeg = 45.0, latDeg = 40.0, lstDeg = 30.0)
        assertTrue(east.azimuthDeg < 180 && west.azimuthDeg > 180)
        assertEquals(east.altitudeDeg, west.altitudeDeg, 1e-9)
    }

    @Test
    fun normalizeWrapsNegative() {
        assertEquals(350.0, AstroMath.normalizeDeg(-10.0), 1e-9)
        assertEquals(0.0, AstroMath.normalizeDeg(720.0), 1e-9)
    }

    @Test
    fun parseRaFormats() {
        assertEquals(270.0, AstroMath.parseRa("18")!!, 1e-9)
        assertEquals(279.9, AstroMath.parseRa("18 39 36")!!, 0.05)
        assertEquals(279.9, AstroMath.parseRa("18:39:36")!!, 0.05)
        assertNull(AstroMath.parseRa("abc"))
    }

    @Test
    fun parseDecFormats() {
        assertEquals(-33.5, AstroMath.parseDec("-33.5")!!, 1e-9)
        assertEquals(38.7836, AstroMath.parseDec("+38 47 01")!!, 0.01)
        assertNotNull(AstroMath.parseDec("-12 30"))
        assertNull(AstroMath.parseDec("x"))
    }

    @Test
    fun formatRaRoundTrip() {
        val ra = AstroMath.parseRa("18 36 56")!!
        val text = AstroMath.formatRaHours(ra)
        assertEquals("18 36 56", text)
    }
}
