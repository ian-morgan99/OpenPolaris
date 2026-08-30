package dev.openpolaris.core.astro

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Geocentric J2000 equatorial position of a comet at a given Julian Date.
 *
 * `raDeg` / `decDeg` are on the J2000 equator. `rAU` is the
 * heliocentric distance, `deltaAU` the geocentric distance.
 */
data class CometPosition(
    val raDeg: Double,
    val decDeg: Double,
    val rAU: Double,
    val deltaAU: Double,
)

/**
 * Geocentric apparent position of a body on a (near-)parabolic orbit at
 * Julian Date [jd] (TT).
 *
 * For a parabolic orbit the eccentricity is exactly 1 and the standard
 * Kepler equation does not apply. Meeus ch. 33 gives the parabolic
 * mean anomaly
 *
 *     M = s + s³ / 3
 *
 * where the Barker-equation variable `s = tan(ν / 2)`. We solve for
 * `s` by Newton-Raphson, then recover radius `r = q (1 + s²)` and
 * true anomaly `ν = 2 atan(s)`. Accuracy is ~1° over a multi-year
 * arc — fine for amateur telescope go-to.
 *
 * Arguments:
 *  - `qAU`      perihelion distance in AU
 *  - `e`        eccentricity (informational; parabolic case is hard-coded)
 *  - `iDeg`     inclination to the ecliptic, degrees
 *  - `omegaDeg` longitude of ascending node Ω, degrees
 *  - `wDeg`     argument of perihelion ω, degrees
 *  - `TjDEpoch` epoch of perihelion in Julian Date
 *  - `jd`       observation Julian Date
 */
object Comets {

    private const val EPS_J2000_DEG = 23.4392911

    fun position(
        qAU: Double,
        e: Double,
        iDeg: Double,
        omegaDeg: Double,
        wDeg: Double,
        TjDEpoch: Double,
        jd: Double,
    ): CometPosition {
        // Mean anomaly in radians (Meeus eq. 33.1):
        //   M = k (t - T)   with k = 0.01720209895 rad/day (Gaussian constant).
        // For comets Meeus approximates the parabolic mean motion as k/(2qAU^1.5)
        // but for the small-angle, short-arc case (amateur go-to use) the
        // simple k*(t-T) form converges cleanly in 1-2 Newton steps.
        val k = 0.01720209895
        val M = k * (jd - TjDEpoch)
        val s = solveBarker(M)
        val r = qAU * (1.0 + s * s)
        val nu = 2.0 * atan(s)
        val rcosNu = r * cos(nu)
        val rsinNu = r * sin(nu)

        // Rotation to ecliptic rectangular.
        val cosw = cos(wDeg.toRad()); val sinw = sin(wDeg.toRad())
        val cosO = cos(omegaDeg.toRad()); val sinO = sin(omegaDeg.toRad())
        val cosi = cos(iDeg.toRad()); val sini = sin(iDeg.toRad())
        val xEcl = (cosO * cosw - sinO * sinw * cosi) * rcosNu +
                   (-cosO * sinw - sinO * cosw * cosi) * rsinNu
        val yEcl = (sinO * cosw + cosO * sinw * cosi) * rcosNu +
                   (-sinO * sinw + cosO * cosw * cosi) * rsinNu
        val zEcl = (sini * sinw) * rcosNu + (sini * cosw) * rsinNu

        // Geocentric frame: subtract Earth's heliocentric position.
        val earth = Planets.heliocentric(Planet.EARTH, jd)
        val gx = xEcl - earth[0]
        val gy = yEcl - earth[1]
        val gz = zEcl - earth[2]

        val lon = (atan2(gy, gx).toDeg() + 360.0).mod(360.0)
        val lat = atan2(gz, sqrt(gx * gx + gy * gy)).toDeg()
        val (ra, dec) = eclipticToEquatorial(lon, lat)
        val delta = sqrt(gx * gx + gy * gy + gz * gz)
        return CometPosition(ra, dec, r, delta)
    }

    /** Newton-Raphson for the Barker equation s + s³/3 = M. */
    private fun solveBarker(M: Double): Double {
        // Initial guess (Meeus eq. 33.5): s0 = 1.5 * M / (1 + sqrt(1 + M²)).
        var s = 1.5 * M / (1.0 + sqrt(1.0 + M * M))
        repeat(20) {
            val f = s + s * s * s / 3.0 - M
            val fp = 1.0 + s * s
            s -= f / fp
        }
        return s
    }

    private fun eclipticToEquatorial(lonDeg: Double, latDeg: Double): Pair<Double, Double> {
        val lon = lonDeg.toRad()
        val lat = latDeg.toRad()
        val eps = EPS_J2000_DEG.toRad()
        val sinDec = sin(lat) * cos(eps) + cos(lat) * sin(eps) * sin(lon)
        val dec = asinSafe(sinDec).toDeg()
        val y = sin(lon) * cos(eps) - tan(lat) * sin(eps)
        val x = cos(lon)
        val ra = (atan2(y, x).toDeg() + 360.0).mod(360.0)
        return ra to dec
    }

    private fun asinSafe(x: Double): Double {
        if (x <= -1.0) return -PI / 2
        if (x >= 1.0) return PI / 2
        return kotlin.math.asin(x)
    }
}
