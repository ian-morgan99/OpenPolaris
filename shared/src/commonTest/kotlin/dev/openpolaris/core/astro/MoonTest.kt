package dev.openpolaris.core.astro

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Lunar ephemeris sanity (low-precision Meeus ch. 47, ~0.2°).
 * The truncated series can drift a few degrees from JPL DE over a
 * decade, so assertions here are generous.
 */
class MoonTest {

    @Test
    fun moonIsRoughlyOppositeSunAtFullMoon() {
        // 2000-07-01 was a full moon. Sun and moon should be ~180° apart
        // in RA on that day, give or take several degrees (Meeus low-precision).
        val jd = AstroMath.julianDate(2000, 7, 1, 12, 0, 0)
        val sunRa = Sun.raDeg(jd)
        val moonRa = Moon.raDeg(jd)
        val diff = ((moonRa - sunRa) + 360.0) % 360.0
        val distance = minOf(diff, 360.0 - diff)
        assertTrue(
            distance < 25.0,
            "expected sun-moon RA near 180° at full moon, got $distance",
        )
    }

    @Test
    fun moonRaAdvancesMonotonicallyOverADay() {
        val jd0 = AstroMath.julianDate(2024, 1, 1, 0, 0, 0)
        val jd1 = jd0 + 1.0
        val ra0 = Moon.raDeg(jd0)
        val ra1 = Moon.raDeg(jd1)
        val delta = ((ra1 - ra0) + 360.0) % 360.0
        // Moon moves ~13°/day eastward.
        assertTrue(delta in 11.0..16.0, "expected moon ΔRA ≈ 13°/day, got $delta")
    }

    @Test
    fun lunarRateRatioIsSlightlySlowerThanSidereal() {
        // Synodic month is longer than sidereal day, so the apparent
        // angular rate of the moon across the sky is a touch under
        // sidereal. The constant is the (sidereal / synodic) ratio.
        assertTrue(Moon.LUNAR_RATE_RATIO in 0.96..0.97, "ratio out of range")
    }

    @Test
    fun moonDecStaysWithinEclipticBand() {
        // The moon's declination is bounded by the 5.1° lunar orbital
        // inclination, so over many days it should stay within ~30° of
        // the celestial equator (extreme standstills reach 28.6°; the
        // low-precision truncation here can overshoot by a degree).
        for (offset in 0..30) {
            val jd = AstroMath.julianDate(2024, 6, 1, 0, 0, 0) + offset
            val dec = Moon.decDeg(jd)
            assertTrue(
                dec in -30.0..30.0,
                "moon Dec out of band on day +$offset: $dec",
            )
        }
    }
}
