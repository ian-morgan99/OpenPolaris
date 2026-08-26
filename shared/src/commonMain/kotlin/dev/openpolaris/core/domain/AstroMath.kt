package dev.openpolaris.core.domain

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Astronomy math: coordinate transforms between equatorial (RA/Dec) and
 * horizontal (Alt/Az) frames. Pure common-main functions, degrees in/out.
 *
 * References: Meeus, "Astronomical Algorithms" ch. 13 & 22 (low-precision
 * forms are adequate for goto pointing at ~1 arcmin level).
 */
object AstroMath {

    /** Result of an RA/Dec → Alt/Az transform. */
    data class Horizontal(val azimuthDeg: Double, val altitudeDeg: Double)

    /**
     * Julian Date from a civil UTC timestamp.
     * @param year..millisecond UTC components.
     */
    fun julianDate(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0, second: Int = 0, millis: Int = 0): Double {
        var y = year
        var m = month
        if (m <= 2) { y -= 1; m += 12 }
        val a = y / 100
        val b = 2 - a + a / 4 // Gregorian correction
        val dayFrac = day + (hour * 3600.0 + minute * 60.0 + second + millis / 1000.0) / 86400.0
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + dayFrac + b - 1524.5
    }

    /**
     * Local Sidereal Time in degrees for the given Julian Date and east-positive
     * longitude. Low-precision form (Meeus 12.4), good to ~0.1 arcsec/century.
     */
    fun localSiderealTimeDeg(jd: Double, longitudeEastDeg: Double): Double {
        val t = (jd - 2451545.0) / 36525.0
        val gmst = 280.46061837 + 360.98564736629 * (jd - 2451545.0) +
            0.000387933 * t * t - t * t * t / 38710000.0
        return normalizeDeg(gmst + longitudeEastDeg)
    }

    /**
     * Convert equatorial to horizontal coordinates.
     * @param raDeg right ascension, degrees
     * @param decDeg declination, degrees
     * @param latDeg observer latitude, north positive
     * @param lstDeg local sidereal time, degrees
     */
    fun toHorizontal(raDeg: Double, decDeg: Double, latDeg: Double, lstDeg: Double): Horizontal {
        val ha = normalizeDeg(lstDeg - raDeg) // hour angle
        val haR = Math.toRadians(ha)
        val decR = Math.toRadians(decDeg)
        val latR = Math.toRadians(latDeg)

        val sinAlt = sin(decR) * sin(latR) + cos(decR) * cos(latR) * cos(haR)
        val alt = Math.toDegrees(asin(sinAlt.coerceIn(-1.0, 1.0)))

        // Azimuth from North, increasing Eastward (Meeus 13.5 with N-based convention).
        val azS = Math.toDegrees(
            atan2(sin(haR), cos(haR) * sin(latR) - tan(decR) * cos(latR))
        )
        return Horizontal(normalizeDeg(azS + 180.0), alt)
    }

    /** Convenience: RA/Dec → Alt/Az given observer location and UTC time parts. */
    fun toHorizontalAt(
        raDeg: Double, decDeg: Double,
        latDeg: Double, lngEastDeg: Double,
        jd: Double,
    ): Horizontal = toHorizontal(raDeg, decDeg, latDeg, localSiderealTimeDeg(jd, lngEastDeg))

    /** Normalize angle into [0, 360). */
    fun normalizeDeg(deg: Double): Double {
        var d = deg % 360.0
        if (d < 0) d += 360.0
        return d
    }

    /** Parse "HH MM SS.s" or "H.h" into decimal degrees of RA. */
    fun parseRa(text: String): Double? {
        val t = text.trim()
        val parts = t.split(Regex("[\\s:hms°]+")).filter { it.isNotBlank() }
        return when {
            parts.size >= 3 -> {
                val h = parts[0].toDoubleOrNull() ?: return null
                val m = parts[1].toDoubleOrNull() ?: return null
                val s = parts[2].toDoubleOrNull() ?: return null
                (h + m / 60.0 + s / 3600.0) * 15.0
            }
            parts.size == 1 -> parts[0].toDoubleOrNull()?.let { it * 15.0 }
            else -> null
        }
    }

    /** Parse "+DD MM SS.s", "-DD.d", or plain decimal degrees into Dec degrees. */
    fun parseDec(text: String): Double? {
        val t = text.trim()
        val neg = t.startsWith("-")
        val body = t.removePrefix("+").removePrefix("-")
        val parts = body.split(Regex("[\\s:dms°]+")).filter { it.isNotBlank() }
        val value = when {
            parts.size >= 3 -> {
                val d = parts[0].toDoubleOrNull() ?: return null
                val m = parts[1].toDoubleOrNull() ?: return null
                val s = parts[2].toDoubleOrNull() ?: return null
                d + m / 60.0 + s / 3600.0
            }
            parts.size == 2 -> {
                val d = parts[0].toDoubleOrNull() ?: return null
                val m = parts[1].toDoubleOrNull() ?: return null
                d + m / 60.0
            }
            parts.size == 1 -> parts[0].toDoubleOrNull() ?: return null
            else -> return null
        }
        return if (neg) -value else value
    }

    /** Format RA degrees as "HH MM SS". */
    fun formatRaHours(raDeg: Double): String {
        val hours = normalizeDeg(raDeg) / 15.0
        val h = floor(hours).toInt()
        val mFull = (hours - h) * 60.0
        val m = floor(mFull).toInt()
        val s = ((mFull - m) * 60.0).toInt()
        return "$h ${format2(m)} ${format2(s)}"
    }

    private fun format2(v: Int): String = v.toString().padStart(2, '0')
}
