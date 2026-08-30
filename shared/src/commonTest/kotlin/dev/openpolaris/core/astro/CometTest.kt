package dev.openpolaris.core.astro

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Sanity tests for the parabolic-orbit (Barker-equation) comet
 * position calculator. We use Halley's 1986 perihelion as a known
 * reference point and a few synthetic edge cases to exercise the
 * Newton-Raphson solver.
 */
class CometTest {

    // Halley orbital elements at 1986 perihelion (epoch JD 2446470.5).
    // q = 0.586 AU, i = 162.3°, Ω = 58.4°, ω = 111.8°.
    private val halleyQ = 0.586
    private val halleyI = 162.3
    private val halleyOmega = 58.4
    private val halleyW = 111.8
    private val halleyT = 2446470.5

    @Test
    fun halleyAtPerihelionMatchesQ() {
        val pos = Comets.position(halleyQ, 1.0, halleyI, halleyOmega, halleyW, halleyT, halleyT)
        // At perihelion the heliocentric distance must equal q (within
        // Newton-Raphson tolerance, which is exact for s=0).
        assertTrue(
            kotlin.math.abs(pos.rAU - halleyQ) < 0.01,
            "Halley rAU at perihelion: ${pos.rAU}, expected ${halleyQ}",
        )
    }

    @Test
    fun halleyMonthsAfterPerihelionRecedes() {
        val pos = Comets.position(halleyQ, 1.0, halleyI, halleyOmega, halleyW, halleyT, halleyT + 100)
        // 100 days past perihelion: r should have grown past q.
        assertTrue(pos.rAU > halleyQ, "expected receding, got ${pos.rAU}")
        // And should still be inside the orbit of Saturn.
        assertTrue(pos.rAU < 10.0, "way too far: ${pos.rAU}")
    }

    @Test
    fun halleyAtPerihelionDeltaIsEarthPlusQ() {
        // At perihelion Halley is on the opposite side of the Sun from
        // Earth (around Feb 9, 1986); geocentric distance ≈ 1 AU + q AU.
        // We allow a 0.3 AU tolerance for geometry.
        val pos = Comets.position(halleyQ, 1.0, halleyI, halleyOmega, halleyW, halleyT, halleyT)
        val expected = 1.0 + halleyQ
        assertTrue(
            kotlin.math.abs(pos.deltaAU - expected) < 0.3,
            "Halley geocentric distance: ${pos.deltaAU}, expected ≈ $expected",
        )
    }

    @Test
    fun barkerSolverIsExactAtPerihelion() {
        // M=0 (i.e. jd == T) should yield s=0 → r = q.
        val pos = Comets.position(1.234, 1.0, 0.0, 0.0, 0.0, 2451545.0, 2451545.0)
        assertTrue(kotlin.math.abs(pos.rAU - 1.234) < 1e-9, "rAU: ${pos.rAU}")
    }
}
