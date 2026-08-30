package dev.openpolaris.core.astro

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Solar-system planets. */
enum class Planet {
    MERCURY, VENUS, EARTH, MARS, JUPITER, SATURN, URANUS, NEPTUNE;
}

/**
 * Geocentric J2000 equatorial position of a planet.
 *
 * `raDeg`/`decDeg` are in degrees on the J2000 equator. `rAU` is the
 * heliocentric distance, `deltaAU` is the geocentric distance.
 */
data class PlanetPosition(
    val raDeg: Double,
    val decDeg: Double,
    val rAU: Double,
    val deltaAU: Double,
)

/**
 * Geocentric apparent position of a planet at Julian Date [jd] (TT).
 * Source: Meeus, _Astronomical Algorithms_, 2nd ed., chapter 32.
 *
 * Accuracy: ~0.1° for inner planets, ~0.5° for outer planets over a
 * multi-decade span — well within the go-to pointing tolerance of a
 * small telescope mount.
 */
object Planets {

    /** Obliquity of the ecliptic at J2000.0, in degrees. */
    private const val EPS_J2000_DEG = 23.4392911

    /**
     * J2000 mean osculating elements per planet. Each row is:
     *  a0, aRate, e0, eRate, i0, iRate, L0, LRate, wbar0, wbarRate, Omega0, OmegaRate
     *  — angles in degrees, semi-major axis in AU, rates per Julian
     *  century. Source: Meeus ch. 32 (Standish, JPL).
     */
    private val elements: Map<Planet, DoubleArray> = mapOf(
        Planet.MERCURY to doubleArrayOf(
            0.38709927, 0.00000037, 0.20563593, 0.00001906,
            7.00497902, -0.00594749, 252.25032350, 149472.67411175,
            77.45779628, 0.16047689, 48.33076593, -0.12534081,
        ),
        Planet.VENUS to doubleArrayOf(
            0.72333566, 0.00000390, 0.00677672, -0.00004107,
            3.39467605, -0.00078890, 181.97909950, 58517.81538729,
            131.60246718, 0.00268329, 76.67984255, -0.27769418,
        ),
        Planet.EARTH to doubleArrayOf(
            1.00000261, 0.00000562, 0.01671123, -0.00004392,
            -0.00001531, -0.01294668, 100.46457166, 35999.37244981,
            102.93768193, 0.32327364, 0.0, 0.0,
        ),
        Planet.MARS to doubleArrayOf(
            1.52371034, 0.00001847, 0.09339410, 0.00007882,
            1.84969142, -0.00813131, -4.55343205, 19140.30268499,
            -23.94362959, 0.44441088, 49.55953891, -0.29257343,
        ),
        Planet.JUPITER to doubleArrayOf(
            5.20288700, -0.00011607, 0.04838624, -0.00013253,
            1.30439695, -0.00183714, 34.39644051, 3034.74612775,
            14.72847983, 0.21252668, 100.47390909, 0.20469106,
        ),
        Planet.SATURN to doubleArrayOf(
            9.53667594, -0.00125060, 0.05386179, -0.00050991,
            2.48599187, 0.00193609, 49.95424423, 1222.49362201,
            92.59887831, -0.41897216, 113.66242448, -0.28867794,
        ),
        Planet.URANUS to doubleArrayOf(
            19.18916464, -0.00196176, 0.04725744, -0.00004397,
            0.77263783, -0.00242939, 313.23810451, 428.48202785,
            170.95427630, 0.40805281, 74.01692503, 0.04240589,
        ),
        Planet.NEPTUNE to doubleArrayOf(
            30.06992276, 0.00026291, 0.00859048, 0.00005105,
            1.77004347, 0.00035372, -55.12002969, 218.45945325,
            44.96476227, -0.32241464, 131.78422574, -0.00508664,
        ),
    )

    fun position(planet: Planet, jd: Double): PlanetPosition {
        val T = (jd - 2451545.0) / 36525.0
        val earth = helioRectangular(Planet.EARTH, T)
        if (planet == Planet.EARTH) {
            // Geocentric Earth position is undefined; return the Sun's
            // apparent J2000 RA/Dec (it is what observers want).
            val lon = (atan2(-earth[1], -earth[0]).toDeg() + 360.0).mod(360.0)
            val r2 = sqrt(earth[0] * earth[0] + earth[1] * earth[1])
            val lat = -atan2(-earth[2], r2).toDeg()
            val (ra, dec) = eclipticToEquatorial(lon, lat)
            val r = sqrt(earth[0] * earth[0] + earth[1] * earth[1] + earth[2] * earth[2])
            return PlanetPosition(ra, dec, r, r)
        }
        val hp = helioRectangular(planet, T)
        val gx = hp[0] - earth[0]
        val gy = hp[1] - earth[1]
        val gz = hp[2] - earth[2]
        val lon = (atan2(gy, gx).toDeg() + 360.0).mod(360.0)
        val lat = atan2(gz, sqrt(gx * gx + gy * gy)).toDeg()
        val (ra, dec) = eclipticToEquatorial(lon, lat)
        val delta = sqrt(gx * gx + gy * gy + gz * gz)
        val r = sqrt(hp[0] * hp[0] + hp[1] * hp[1] + hp[2] * hp[2])
        return PlanetPosition(ra, dec, r, delta)
    }

    /**
     * Heliocentric ecliptic-rectangular coordinates (x, y, z) of a planet
     * at Julian Date `jd`. Used by [Comets] to subtract Earth's position
     * and obtain a geocentric frame.
     */
    fun heliocentric(planet: Planet, jd: Double): DoubleArray {
        val T = (jd - 2451545.0) / 36525.0
        return helioRectangular(planet, T)
    }

    private fun helioRectangular(planet: Planet, T: Double): DoubleArray {
        val e = elements[planet]!!
        val a = e[0] + e[1] * T
        val ecc = e[2] + e[3] * T
        val inc = e[4] + e[5] * T
        val L = (e[6] + e[7] * T).normalizeDeg()
        val wbar = (e[8] + e[9] * T).normalizeDeg()
        val Omega = (e[10] + e[11] * T).normalizeDeg()
        val w = (wbar - Omega).normalizeDeg()
        val M = (L - wbar).normalizeDeg()
        val nu = solveKepler(M, ecc)
        return orbitToEcliptic(a, ecc, inc, Omega, w, nu)
    }

    /** Heliocentric position rotated to ecliptic rectangular (x, y, z). */
    private fun orbitToEcliptic(
        a: Double, e: Double, i: Double, Omega: Double, w: Double, nu: Double,
    ): DoubleArray {
        val r = a * (1 - e * e) / (1 + e * cos(nu.toRad()))
        val xOrb = r * cos(nu.toRad())
        val yOrb = r * sin(nu.toRad())
        val cosw = cos(w.toRad()); val sinw = sin(w.toRad())
        val cosO = cos(Omega.toRad()); val sinO = sin(Omega.toRad())
        val cosi = cos(i.toRad()); val sini = sin(i.toRad())
        val xEcl = (cosO * cosw - sinO * sinw * cosi) * xOrb +
                   (-cosO * sinw - sinO * cosw * cosi) * yOrb
        val yEcl = (sinO * cosw + cosO * sinw * cosi) * xOrb +
                   (-sinO * sinw + cosO * cosw * cosi) * yOrb
        val zEcl = (sini * sinw) * xOrb + (sini * cosw) * yOrb
        return doubleArrayOf(xEcl, yEcl, zEcl)
    }

    private fun solveKepler(M: Double, e: Double): Double {
        var E = M.toRad() + e * sin(M.toRad())
        repeat(10) {
            val dE = (E - e * sin(E) - M.toRad()) / (1 - e * cos(E))
            E -= dE
        }
        val sinE = sin(E); val cosE = cos(E)
        val nu = atan2(sqrt(1 - e * e) * sinE, cosE - e)
        return nu.toDeg().normalizeDeg()
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
        return asin(x)
    }
}
