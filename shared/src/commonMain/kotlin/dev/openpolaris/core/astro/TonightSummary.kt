package dev.openpolaris.core.astro

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * One-shot summary of "what's up in the sky right now" for an observer
 * at a given location and Julian Date. Pure function on a small
 * observable surface; computed by [TonightSummary.of] from the bundled
 * astro core. Used by the Tonight call-out in the UI and unit-tested
 * directly.
 *
 * All time inputs/outputs are Julian Date (UT). The UI converts JD back
 * to local civil time using [AstroMath] if it wants a wall-clock label.
 *
 * The summary intentionally keeps RA/Dec and Alt/Az both around so the
 * caller can show whichever is most useful ("RA 05 35  /  Alt 32°"),
 * and so the slew button can pre-populate RA/Dec without a second
 * transform.
 */
data class TonightSummary(
    val jd: Double,
    val observerLatDeg: Double,
    val observerLngEastDeg: Double,
    val sun: Body,
    val moon: Body,
    /** Five naked-eye planets + Uranus (so the user can see "all 6"). */
    val planets: List<PlanetEntry>,
    val comets: List<CometEntry>,
    /** Brightest catalogue objects currently above the horizon. */
    val upTargets: List<UpTarget>,
    val twilight: Twilight,
) {
    /**
     * Sun/Moon/planet/comet/catalog target rendered for the Tonight pane.
     * `riseJd` and `setJd` are the next rise/set *after* [jd] (or null if
     * the body stays above/below the horizon for 24 h, e.g. polar regions
     * in midsummer).
     */
    data class Body(
        val raDeg: Double,
        val decDeg: Double,
        val altitudeDeg: Double,
        val azimuthDeg: Double,
        val riseJd: Double?,
        val setJd: Double?,
    )

    data class PlanetEntry(
        val planet: Planet,
        val raDeg: Double,
        val decDeg: Double,
        val altitudeDeg: Double,
        val azimuthDeg: Double,
        val rAU: Double,
        val deltaAU: Double,
        val magnitudeApprox: Double?,
    )

    data class CometEntry(
        val designation: String,
        val name: String,
        val raDeg: Double,
        val decDeg: Double,
        val altitudeDeg: Double,
        val azimuthDeg: Double,
        val magnitudeApprox: Double?,
    )

    /**
     * Catalog object currently above the horizon (or above
     * [altitudeCutoffDeg]). Sorted by descending altitude, capped at
     * [maxTargets].
     */
    data class UpTarget(
        val designation: String,
        val name: String?,
        val type: ObjectType,
        val raDeg: Double,
        val decDeg: Double,
        val altitudeDeg: Double,
        val azimuthDeg: Double,
        val magnitude: Double?,
        val constellation: String?,
    )

    /**
     * Next rise/set events for the Sun. Each is the Julian Date of the
     * event *after* [jd] (so the UI can show "sunset in 2 h 14 m").
     * `isCircumpolar` and `isAllDayBelow` indicate 24-h polar cases.
     */
    data class Twilight(
        val sunriseJd: Double?,
        val sunsetJd: Double?,
        val civilDawnJd: Double?,
        val civilDuskJd: Double?,
        val nauticalDawnJd: Double?,
        val nauticalDuskJd: Double?,
        val astroDawnJd: Double?,
        val astroDuskJd: Double?,
        val isSunUp: Boolean,
    )

    companion object {

        /**
         * Build a summary for an observer at [latDeg] / [lngEastDeg] at
         * Julian Date [jd]. [comets] is optional; pass `null` (or empty)
         * to skip the comet list (faster path for tests that don't care).
         * [altitudeCutoffDeg] filters [upTargets]; pass a very negative
         * value to include objects below the horizon.
         */
        fun of(
            jd: Double,
            latDeg: Double,
            lngEastDeg: Double,
            catalog: Catalog,
            comets: List<CometOrbitalElements> = emptyList(),
            altitudeCutoffDeg: Double = 20.0,
            maxTargets: Int = 10,
        ): TonightSummary {
            val sun = sunBody(jd, latDeg, lngEastDeg)
            val moon = moonBody(jd, latDeg, lngEastDeg)
            val planetList = planetEntries(jd, latDeg, lngEastDeg)
            val cometList = cometEntries(jd, latDeg, lngEastDeg, comets)
            val up = upTargets(jd, latDeg, lngEastDeg, catalog, altitudeCutoffDeg, maxTargets)
            val tw = twilight(jd, latDeg, lngEastDeg)
            return TonightSummary(
                jd = jd,
                observerLatDeg = latDeg,
                observerLngEastDeg = lngEastDeg,
                sun = sun,
                moon = moon,
                planets = planetList,
                comets = cometList,
                upTargets = up,
                twilight = tw,
            )
        }

        // ---- bodies --------------------------------------------------------

        private fun sunBody(jd: Double, latDeg: Double, lngEastDeg: Double): Body {
            val pos = Sun.position(jd)
            val h = AstroMath.toHorizontalAt(pos.raDeg, pos.decDeg, latDeg, lngEastDeg, jd)
            val rs = nextRiseSet(jd, latDeg, lngEastDeg, ::sunAlt) { sin(0.833 * kotlin.math.PI / 180.0) }
            return Body(
                raDeg = pos.raDeg,
                decDeg = pos.decDeg,
                altitudeDeg = h.altitudeDeg,
                azimuthDeg = h.azimuthDeg,
                riseJd = rs?.first,
                setJd = rs?.second,
            )
        }

        private fun moonBody(jd: Double, latDeg: Double, lngEastDeg: Double): Body {
            val pos = Moon.position(jd)
            val h = AstroMath.toHorizontalAt(pos.raDeg, pos.decDeg, latDeg, lngEastDeg, jd)
            val rs = nextRiseSet(jd, latDeg, lngEastDeg, ::moonAlt) { 0.0 }
            return Body(
                raDeg = pos.raDeg,
                decDeg = pos.decDeg,
                altitudeDeg = h.altitudeDeg,
                azimuthDeg = h.azimuthDeg,
                riseJd = rs?.first,
                setJd = rs?.second,
            )
        }

        private fun sunAlt(jd: Double, latDeg: Double, lngEastDeg: Double): Double {
            val pos = Sun.position(jd)
            val h = AstroMath.toHorizontalAt(pos.raDeg, pos.decDeg, latDeg, lngEastDeg, jd)
            return h.altitudeDeg
        }

        private fun moonAlt(jd: Double, latDeg: Double, lngEastDeg: Double): Double {
            val pos = Moon.position(jd)
            val h = AstroMath.toHorizontalAt(pos.raDeg, pos.decDeg, latDeg, lngEastDeg, jd)
            return h.altitudeDeg
        }

        // ---- planets -------------------------------------------------------

        /** Naked-eye planets (Mercury..Saturn + Uranus for completeness). */
        private val NAKED_EYE: List<Planet> = listOf(
            Planet.MERCURY, Planet.VENUS, Planet.MARS,
            Planet.JUPITER, Planet.SATURN, Planet.URANUS,
        )

        /**
         * Approximate visual magnitudes at opposition. Negative = brighter.
         * Used to order the planet list. Uranus is +5.7, technically the
         * edge of naked-eye visibility from dark sites.
         */
        private val PLANET_MAG: Map<Planet, Double> = mapOf(
            Planet.MERCURY to -0.4,
            Planet.VENUS to -4.6,
            Planet.MARS to -2.0,
            Planet.JUPITER to -2.7,
            Planet.SATURN to 0.5,
            Planet.URANUS to 5.7,
        )

        private fun planetEntries(
            jd: Double,
            latDeg: Double,
            lngEastDeg: Double,
        ): List<PlanetEntry> = NAKED_EYE.map { p ->
            val pos = Planets.position(p, jd)
            val h = AstroMath.toHorizontalAt(pos.raDeg, pos.decDeg, latDeg, lngEastDeg, jd)
            PlanetEntry(
                planet = p,
                raDeg = pos.raDeg,
                decDeg = pos.decDeg,
                altitudeDeg = h.altitudeDeg,
                azimuthDeg = h.azimuthDeg,
                rAU = pos.rAU,
                deltaAU = pos.deltaAU,
                magnitudeApprox = PLANET_MAG[p],
            )
        }.sortedBy { it.magnitudeApprox ?: 99.0 }

        // ---- comets --------------------------------------------------------

        private fun cometEntries(
            jd: Double,
            latDeg: Double,
            lngEastDeg: Double,
            comets: List<CometOrbitalElements>,
        ): List<CometEntry> = comets.map { c ->
            val pos = Comets.position(c.qAU, c.e, c.iDeg, c.omegaDeg, c.wDeg, c.tJD, jd)
            val h = AstroMath.toHorizontalAt(pos.raDeg, pos.decDeg, latDeg, lngEastDeg, jd)
            CometEntry(
                designation = c.designation,
                name = c.name,
                raDeg = pos.raDeg,
                decDeg = pos.decDeg,
                altitudeDeg = h.altitudeDeg,
                azimuthDeg = h.azimuthDeg,
                magnitudeApprox = c.magnitude,
            )
        }.sortedBy { it.altitudeDeg }.reversed() // highest first

        // ---- "up now" targets ---------------------------------------------

        private fun upTargets(
            jd: Double,
            latDeg: Double,
            lngEastDeg: Double,
            catalog: Catalog,
            altitudeCutoffDeg: Double,
            maxTargets: Int,
        ): List<UpTarget> {
            if (catalog.objects.isEmpty()) return emptyList()
            return catalog.objects.asSequence()
                .map { o ->
                    val h = AstroMath.toHorizontalAt(o.raDeg, o.decDeg, latDeg, lngEastDeg, jd)
                    UpTarget(
                        designation = o.designation,
                        name = o.name,
                        type = o.type,
                        raDeg = o.raDeg,
                        decDeg = o.decDeg,
                        altitudeDeg = h.altitudeDeg,
                        azimuthDeg = h.azimuthDeg,
                        magnitude = o.magnitude,
                        constellation = o.constellation,
                    )
                }
                .filter { it.altitudeDeg >= altitudeCutoffDeg }
                // Sort: brightest first (lowest magnitude value), then highest altitude as a tiebreaker.
                .sortedWith(
                    compareBy<UpTarget> { it.magnitude ?: 99.0 }
                        .thenByDescending { it.altitudeDeg }
                )
                .take(maxTargets)
                .toList()
        }

        // ---- twilight ------------------------------------------------------

        private fun twilight(jd: Double, latDeg: Double, lngEastDeg: Double): Twilight {
            val (sRise, sSet) = nextRiseSet(jd, latDeg, lngEastDeg, ::sunAlt) { sin(0.833 * kotlin.math.PI / 180.0) } ?: (null to null)
            val (cDawn, cDusk) = nextRiseSet(jd, latDeg, lngEastDeg, ::sunAlt) { sin(6.0 * kotlin.math.PI / 180.0) } ?: (null to null)
            val (nDawn, nDusk) = nextRiseSet(jd, latDeg, lngEastDeg, ::sunAlt) { sin(12.0 * kotlin.math.PI / 180.0) } ?: (null to null)
            val (aDawn, aDusk) = nextRiseSet(jd, latDeg, lngEastDeg, ::sunAlt) { sin(18.0 * kotlin.math.PI / 180.0) } ?: (null to null)
            val isUp = sunAlt(jd, latDeg, lngEastDeg) > -0.833
            return Twilight(
                sunriseJd = sRise,
                sunsetJd = sSet,
                civilDawnJd = cDawn,
                civilDuskJd = cDusk,
                nauticalDawnJd = nDawn,
                nauticalDuskJd = nDusk,
                astroDawnJd = aDawn,
                astroDuskJd = aDusk,
                isSunUp = isUp,
            )
        }

        // ---- rise / set binary search -------------------------------------
        //
        // Walk forward in 10-min steps from `jd` until the altitude crosses
        // the threshold, then bisect the window to ~1 second. Returns the
        // (rise, set) pair as the next two events after `jd`, or null if no
        // event is found within 48 h (caller can treat as circumpolar or
        // all-day-below).

        private fun nextRiseSet(
            jd: Double,
            latDeg: Double,
            lngEastDeg: Double,
            altFn: (Double, Double, Double) -> Double,
            threshold: () -> Double,
        ): Pair<Double, Double>? {
            val stepDays = 10.0 / 1440.0 // 10 minutes
            val horizon = threshold()
            val startAlt = altFn(jd, latDeg, lngEastDeg)
            val startUp = startAlt > horizon
            // 48 h forward
            val maxDays = 2.0
            val nSteps = (maxDays / stepDays).toInt()
            var prev = jd
            var prevAlt = startAlt
            var prevUp = startUp
            // Find first rising edge (alt crosses horizon upward) in [jd, jd+24h]
            var firstRise: Double? = null
            var firstSet: Double? = null
            for (i in 1..nSteps) {
                val t = jd + i * stepDays
                val a = altFn(t, latDeg, lngEastDeg)
                val up = a > horizon
                if (firstRise == null && !prevUp && up) {
                    firstRise = bisect(jd = prev, jd2 = t, targetAlt = horizon, latDeg, lngEastDeg, altFn, goingUp = true)
                }
                if (firstSet == null && prevUp && !up) {
                    firstSet = bisect(jd = prev, jd2 = t, targetAlt = horizon, latDeg, lngEastDeg, altFn, goingUp = false)
                }
                if (firstRise != null && firstSet != null) break
                prev = t
                prevAlt = a
                prevUp = up
            }
            val r: Double? = firstRise
            val s: Double? = firstSet
            return if (r != null && s != null) r to s else null
        }

        private fun bisect(
            jd: Double,
            jd2: Double,
            targetAlt: Double,
            latDeg: Double,
            lngEastDeg: Double,
            altFn: (Double, Double, Double) -> Double,
            goingUp: Boolean,
        ): Double {
            var lo = jd
            var hi = jd2
            for (i in 0 until 60) { // 60 iterations → ~1e-15 day precision
                val mid = (lo + hi) / 2.0
                val a = altFn(mid, latDeg, lngEastDeg)
                val up = a > targetAlt
                if (up == goingUp) hi = mid else lo = mid
                if (hi - lo < 1.0 / 86400.0) break // 1 second
            }
            return (lo + hi) / 2.0
        }
    }
}
