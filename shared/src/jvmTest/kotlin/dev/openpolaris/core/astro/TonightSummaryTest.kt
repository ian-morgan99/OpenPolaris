package dev.openpolaris.core.astro

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TonightSummaryTest {

    /** A tiny but representative catalog: Orion Nebula, Polaris, and Vega. */
    private val tinyCatalog: Catalog = CatalogParser.of(
        listOf(
            AstroObject(
                designation = "M42",
                name = "Orion Nebula",
                type = ObjectType.NEBULA,
                raDeg = 83.8221,   // 05 35 17
                decDeg = -5.3911,  // -05 23 28
                magnitude = 4.0,
                constellation = "Orion",
            ),
            AstroObject(
                designation = "Polaris",
                name = "Polaris",
                type = ObjectType.STAR,
                raDeg = 37.9529,   // 02 31 49
                decDeg = 89.2641,  // +89 15 51
                magnitude = 1.98,
                constellation = "Ursa Minor",
            ),
            AstroObject(
                designation = "Vega",
                name = "Vega",
                type = ObjectType.STAR,
                raDeg = 279.2347,  // 18 36 56
                decDeg = 38.7837,  // +38 47 01
                magnitude = 0.03,
                constellation = "Lyra",
            ),
        ),
        version = 1,
    )

    @Test
    fun `observer latitude matches output`() {
        val jd = 2_460_700.5 // 2024-12-21 00:00 UT, winter solstice
        val s = TonightSummary.of(jd, latDeg = 51.5, lngEastDeg = -0.12, catalog = tinyCatalog)
        assertEquals(51.5, s.observerLatDeg)
        assertEquals(-0.12, s.observerLngEastDeg)
        assertEquals(jd, s.jd)
    }

    @Test
    fun `sun position computed in alt az`() {
        val s = TonightSummary.of(
            jd = 2_460_700.5, latDeg = 51.5, lngEastDeg = -0.12, catalog = tinyCatalog,
        )
        // In December at 51.5°N, the sun should be well below the horizon at 0h UT
        // (around midnight local in London, so altitude is around -60°).
        assertTrue(s.sun.altitudeDeg < 0.0, "sun should be below horizon at midnight, got ${s.sun.altitudeDeg}")
        assertTrue(s.sun.azimuthDeg in 0.0..360.0, "azimuth should be normalized")
    }

    @Test
    fun `twilight includes sunset and sunrise within 24h`() {
        val s = TonightSummary.of(
            jd = 2_460_700.5, latDeg = 51.5, lngEastDeg = -0.12, catalog = tinyCatalog,
        )
        val tw = s.twilight
        assertNotNull(tw.sunsetJd, "sunset should be found within 48h")
        assertNotNull(tw.sunriseJd, "sunrise should be found within 48h")
        assertTrue(tw.sunsetJd!! > s.jd)
        assertTrue(tw.sunriseJd!! > s.jd)
    }

    @Test
    fun `polaris is circumpolar in London`() {
        val s = TonightSummary.of(
            jd = 2_460_700.5, latDeg = 51.5, lngEastDeg = -0.12, catalog = tinyCatalog,
        )
        val polaris = s.upTargets.firstOrNull { it.designation == "Polaris" }
        assertNotNull(polaris, "Polaris should be in the up targets at 51.5°N")
        // Polaris is within ~0.74° of the NCP, so its altitude is ≈ observer latitude.
        // Allow ±1° slack for the small dec offset (89.26° vs 90°).
        assertTrue(
            polaris!!.altitudeDeg in 50.0..53.0,
            "Polaris altitude at 51.5°N should be ~51.5°, got ${polaris.altitudeDeg}",
        )
    }

    @Test
    fun `up targets sorted brightest first`() {
        val s = TonightSummary.of(
            jd = 2_460_700.5, latDeg = 51.5, lngEastDeg = -0.12, catalog = tinyCatalog,
            altitudeCutoffDeg = -90.0, // include all (some may be below horizon)
        )
        // All three should be represented, sorted by magnitude (brightest first).
        assertEquals(3, s.upTargets.size)
        val mags = s.upTargets.map { it.magnitude }
        assertEquals(listOf(0.03, 1.98, 4.0), mags)
    }

    @Test
    fun `up targets filter by altitude cutoff`() {
        val s = TonightSummary.of(
            jd = 2_460_700.5, latDeg = 51.5, lngEastDeg = -0.12, catalog = tinyCatalog,
            altitudeCutoffDeg = 30.0,
        )
        // Only Polaris is above 30° at 51.5°N in winter at 0h UT.
        assertEquals(1, s.upTargets.size)
        assertEquals("Polaris", s.upTargets.first().designation)
    }

    @Test
    fun `planets list is sorted brightest first`() {
        val s = TonightSummary.of(
            jd = 2_460_700.5, latDeg = 51.5, lngEastDeg = -0.12, catalog = tinyCatalog,
        )
        // Should have 6 planets (Mercury..Saturn + Uranus).
        assertEquals(6, s.planets.size)
        // Venus is brightest naked-eye planet.
        assertEquals(Planet.VENUS, s.planets.first().planet)
    }

    @Test
    fun `empty comet list is allowed`() {
        val s = TonightSummary.of(
            jd = 2_460_700.5, latDeg = 51.5, lngEastDeg = -0.12, catalog = tinyCatalog,
            comets = emptyList(),
        )
        assertEquals(0, s.comets.size)
    }

    @Test
    fun `max targets caps result`() {
        val catalog = CatalogParser.of(
            (1..20).map { i ->
                AstroObject(
                    designation = "X$i",
                    name = "",
                    type = ObjectType.STAR,
                    raDeg = (i * 18.0) % 360.0,
                    decDeg = 0.0, // On equator → above horizon half the time everywhere
                    magnitude = 6.0 - i / 20.0, // bright to dim
                    constellation = "",
                )
            },
            version = 1,
        )
        val s = TonightSummary.of(
            jd = 2_460_700.5, latDeg = 0.0, lngEastDeg = 0.0, catalog = catalog,
            altitudeCutoffDeg = -90.0, maxTargets = 5,
        )
        assertEquals(5, s.upTargets.size)
    }
}
