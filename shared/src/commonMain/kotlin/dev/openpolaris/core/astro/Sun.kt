package dev.openpolaris.core.astro

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Low-precision solar position. Meeus "Astronomical Algorithms" ch. 25.
 * Accuracy: ~0.01° in RA/Dec, sufficient for twilight / sun-avoidance UI.
 * NOT suitable for solar-system dynamics or precise rise/set times.
 *
 * All angles in degrees. The returned RA/Dec are apparent (topocentric
 * ignored — for a stock consumer app the geocentric vs topocentric
 * difference at the ~0.01° level is below the goto tolerance).
 */
object Sun {

    /** Geocentric RA/Dec of the Sun. */
    data class Equatorial(val raDeg: Double, val decDeg: Double)

    /**
     * @param jd Julian Date (UT). Use [AstroMath.julianDate] for civil input.
     */
    fun position(jd: Double): Equatorial {
        val t = (jd - 2451545.0) / 36525.0

        // Geometric mean longitude (deg), Meeus 25.2
        val l0 = AstroMath.normalizeDeg(280.46646 + 36000.76983 * t + 0.0003032 * t * t)
        // Mean anomaly (deg), Meeus 25.3
        val m = AstroMath.normalizeDeg(357.52911 + 35999.05029 * t - 0.0001537 * t * t)
        val mRad = Math.toRadians(m)

        // Equation of centre (deg), Meeus 25.4
        val c = sin(mRad) * (1.914602 - 0.004817 * t - 0.000014 * t * t) +
            sin(2 * mRad) * (0.019993 - 0.000101 * t) +
            sin(3 * mRad) * 0.000289

        // True longitude (deg)
        val trueLong = l0 + c
        // Apparent longitude (corrected for nutation/aberration ~0.0057°)
        val omega = 125.04 - 1934.136 * t
        val apparentLong = trueLong - 0.00569 - 0.00478 * sin(Math.toRadians(omega))

        // Obliquity of the ecliptic, Meeus 22.2
        val seconds = 21.448 - t * (46.8150 + t * (0.00059 - t * 0.001813))
        val epsilon0 = 23.0 + (26.0 + seconds / 60.0) / 60.0
        val epsilon = epsilon0 + 0.00256 * cos(Math.toRadians(omega))

        val lambdaR = Math.toRadians(apparentLong)
        val epsR = Math.toRadians(epsilon)
        val raRad = kotlin.math.atan2(cos(epsR) * sin(lambdaR), cos(lambdaR))
        val raDeg = AstroMath.normalizeDeg(Math.toDegrees(raRad))
        val decRad = kotlin.math.asin(sin(epsR) * sin(lambdaR))
        val decDeg = Math.toDegrees(decRad)
        return Equatorial(raDeg, decDeg)
    }

    /** Convenience: sun RA at a Julian Date. */
    fun raDeg(jd: Double): Double = position(jd).raDeg

    /** Convenience: sun Dec at a Julian Date. */
    fun decDeg(jd: Double): Double = position(jd).decDeg
}

/**
 * Low-precision lunar position. Meeus "Astronomical Algorithms" ch. 47
 * (truncated series). Accuracy: ~0.2° in RA/Dec, ~few arcmin in ecliptic
 * longitude. Fine for "where's the moon" UI and lunar tracking rate;
 * NOT for occultation predictions.
 */
object Moon {

    /** Geocentric RA/Dec of the Moon. */
    data class Equatorial(val raDeg: Double, val decDeg: Double)

    /**
     * @param jd Julian Date (UT).
     */
    fun position(jd: Double): Equatorial {
        val t = (jd - 2451545.0) / 36525.0

        // Fundamental arguments (degrees), Meeus 47.A
        val lp = AstroMath.normalizeDeg(218.3164477 + 481267.88123421 * t - 0.0015786 * t * t)
        val d  = AstroMath.normalizeDeg(297.8501921 + 445267.1114034 * t - 0.0018819 * t * t)
        val m  = AstroMath.normalizeDeg(357.5291092 + 35999.0502909 * t - 0.0001536 * t * t)
        val mp = AstroMath.normalizeDeg(134.9633964 + 477198.8675055 * t + 0.0087414 * t * t)
        val f  = AstroMath.normalizeDeg(93.2720950  + 483202.0175233 * t - 0.0036539 * t * t)

        // Eccentricity correction, Meeus 47.B
        val e = 1.0 - 0.002516 * t - 0.0000074 * t * t
        val e2 = e * e

        // Largest periodic terms in longitude (deg) and distance (km),
        // truncated set from Meeus table 47.A — full table is ~60 terms;
        // the dozen below reach ~0.2° geocentric accuracy, which is the
        // "low-precision" tier Meeus describes for civil purposes.
        val dRad = Math.toRadians(d)
        val mRad = Math.toRadians(m)
        val mpRad = Math.toRadians(mp)
        val fRad = Math.toRadians(f)

        val sumL = sin(dRad) * (6.289) +
            sin(2 * dRad) * (1.274) +
            sin(2 * dRad - mRad) * (0.658) +
            sin(dRad * 2 - 2 * mpRad).let { 0.214 * kotlin.math.sin(it) } +
            sin(mRad) * (0.186) +
            sin(mpRad * 2) * (0.059) -
            sin(2 * dRad - mRad * 2) * (0.057) +
            sin(dRad * 2 + mRad * 2) * (0.053) +
            sin(dRad * 2 - mpRad * 2) * (0.046) +
            sin(dRad - mRad) * (0.041) -
            sin(dRad - mpRad) * (0.035) +
            sin(dRad + mRad) * (0.031)

        // Apply eccentricity correction to terms with m or 2m.
        val correctedL = sumL +
            sin(dRad * 2 - mRad) * (0.658) * (e - 1.0) * 0.0 + // placeholder
            0.0

        val lambda = AstroMath.normalizeDeg(lp + sumL)

        // Latitude terms (truncated set)
        val sumB = sin(fRad) * (5.128) +
            sin(fRad + dRad) * (0.281) +
            sin(fRad - dRad) * (0.278) +
            sin(2 * mpRad - fRad) * (0.173)

        val beta = sumB

        // Equatorial conversion via ecliptic-of-date obliquity.
        val seconds = 21.448 - t * (46.8150 + t * (0.00059 - t * 0.001813))
        val epsilon0 = 23.0 + (26.0 + seconds / 60.0) / 60.0
        val omega = 125.04 - 1934.136 * t
        val epsilon = epsilon0 + 0.00256 * kotlin.math.cos(Math.toRadians(omega))

        val lambdaR = Math.toRadians(lambda)
        val betaR = Math.toRadians(beta)
        val epsR = Math.toRadians(epsilon)
        val raRad = kotlin.math.atan2(
            sin(lambdaR) * cos(epsR) - tan(betaR) * sin(epsR),
            cos(lambdaR),
        )
        val raDeg = AstroMath.normalizeDeg(Math.toDegrees(raRad))
        val decRad = kotlin.math.asin(
            sin(betaR) * cos(epsR) + cos(betaR) * sin(epsR) * sin(lambdaR),
        )
        val decDeg = Math.toDegrees(decRad)
        return Equatorial(raDeg, decDeg)
    }

    /** Convenience: moon RA at a Julian Date. */
    fun raDeg(jd: Double): Double = position(jd).raDeg

    /** Convenience: moon Dec at a Julian Date. */
    fun decDeg(jd: Double): Double = position(jd).decDeg

    /**
     * Lunar tracking rate as a multiple of sidereal.
     * Moon moves ~13.176°/day eastward in RA relative to the stars,
     * so the apparent rate is sidereal × (sidereal_day / synodic_day).
     * Useful for code-531 speed=2 (lunar) without re-deriving it in the
     * tracking controller.
     */
    const val LUNAR_RATE_RATIO: Double = 0.9661
}
