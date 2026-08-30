package dev.openpolaris.core.astro

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The bundled catalogue must contain the alignment stars the goto
 * workflow depends on and the canonical Messier anchors. RA/Dec are
 * J2000 within a few arcmin — well below the go-to tolerance.
 */
class CatalogTest {

    @Test
    fun embeddedCatalogLoads() {
        val json = """
            {
              "version": 1,
              "objects": [
                {
                  "designation": "Polaris", "name": "Polaris", "type": "STAR",
                  "raDeg": 37.95, "decDeg": 89.26, "magnitude": 1.98,
                  "constellation": "Ursa Minor"
                }
              ]
            }
        """.trimIndent()
        val cat = Catalog.parse(json)
        assertEquals(1, cat.objects.size)
        assertEquals("Polaris", cat.objects[0].designation)
    }

    @Test
    fun bundledCatalogContainsAllAlignmentStars() {
        val cat = loadBundled()
        val expected = listOf(
            "Polaris", "Vega", "Sirius", "Arcturus", "Capella", "Rigel",
            "Procyon", "Betelgeuse", "Altair", "Aldebaran", "Spica",
            "Antares", "Pollux", "Fomalhaut", "Deneb", "Regulus", "Castor",
        )
        for (name in expected) {
            val obj = cat.findByName(name)
            assertNotNull(obj, "missing alignment star: $name")
        }
    }

    @Test
    fun bundledCatalogContainsAllMessierObjects() {
        val cat = loadBundled()
        for (i in 1..110) {
            val des = "M$i"
            val obj = cat.findByDesignation(des)
            assertNotNull(obj, "missing Messier: $des")
        }
    }

    @Test
    fun polarisHasCorrectJ2000Coords() {
        val cat = loadBundled()
        val polaris = cat.findByName("Polaris")!!
        // Polaris J2000: RA ≈ 02h 31m 49s = 37.95°, Dec ≈ +89° 15' 51" = 89.26°.
        // Allow 1° tolerance for catalog rounding.
        assertTrue(polaris.raDeg in 36.0..40.0, "Polaris RA: ${polaris.raDeg}")
        assertTrue(polaris.decDeg in 88.0..90.0, "Polaris Dec: ${polaris.decDeg}")
    }

    @Test
    fun m31AndromedaHasCorrectJ2000Coords() {
        val cat = loadBundled()
        val m31 = cat.findByDesignation("M31")!!
        // M31 J2000: RA ≈ 00h 42m 44s = 10.69°, Dec ≈ +41° 16' = 41.27°.
        assertTrue(m31.raDeg in 9.0..12.0, "M31 RA: ${m31.raDeg}")
        assertTrue(m31.decDeg in 40.0..43.0, "M31 Dec: ${m31.decDeg}")
    }

    @Test
    fun m42OrionNebulaClassifiedAsNebula() {
        val cat = loadBundled()
        val m42 = cat.findByDesignation("M42")!!
        assertEquals(ObjectType.NEBULA, m42.type)
    }

    @Test
    fun searchByPrefixFindsMessierHits() {
        val cat = loadBundled()
        val hits = cat.searchByPrefix("M4")
        assertTrue(hits.size >= 10, "expected many M4x hits, got ${hits.size}")
        // First result should be M4 itself.
        assertEquals("M4", hits[0].designation)
    }

    @Test
    fun searchByPrefixFindsNamedObjects() {
        val cat = loadBundled()
        val hits = cat.searchByPrefix("Androm")
        assertTrue(hits.isNotEmpty(), "expected Andromeda hit")
        assertEquals("M31", hits.first().designation)
    }

    @Test
    fun bundledCatalogHasReasonableObjectCount() {
        val cat = loadBundled()
        // 30 alignment stars + 110 Messier = 140 minimum. Allow headroom
        // for future additions.
        assertTrue(
            cat.objects.size >= 140,
            "catalog too small: ${cat.objects.size}",
        )
    }

    @Test
    fun mergeDeduplicatesByDesignation() {
        val a = Catalog.parse(
            """{"version":1,"objects":[
              |{"designation":"X","name":"X","type":"STAR","raDeg":1.0,"decDeg":2.0,"magnitude":1.0,"constellation":""}
              |]}""".trimMargin()
        )
        val b = Catalog.parse(
            """{"version":1,"objects":[
              |{"designation":"X","name":"X v2","type":"STAR","raDeg":3.0,"decDeg":4.0,"magnitude":2.0,"constellation":""},
              |{"designation":"Y","name":"Y","type":"STAR","raDeg":5.0,"decDeg":6.0,"magnitude":3.0,"constellation":""}
              |]}""".trimMargin()
        )
        val merged = Catalog.merge(a, b)
        // X deduped (last-write-wins, so X.name == "X v2"); Y added.
        assertEquals(2, merged.objects.size)
        val x = merged.objects.first { it.designation == "X" }
        assertEquals("X v2", x.name)
    }

    @Test
    fun loadFromMultipleShardsAggregatesObjects() {
        val cat = EmbeddedCatalog.loadFrom(
            paths = EmbeddedCatalog.DEFAULT_SHARDS,
            reader = { path ->
                this::class.java.classLoader
                    ?.getResource(path)
                    ?.readText()
            }
        )
        // 140 catalog + 205 stars + 274 NGC = 619 minimum, no duplicates
        // across shards (verified separately).
        assertTrue(
            cat.objects.size >= 619,
            "expected ≥619 objects across shards, got ${cat.objects.size}",
        )
        val designations = cat.objects.map { it.designation }
        val distinctCount = designations.toSet().size
        if (designations.size != distinctCount) {
            fail("duplicates across shards: ${designations.size} total vs $distinctCount distinct")
        }
    }

    @Test
    fun loadFromCanIncludeCometsShard() {
        // The comets shard is not in DEFAULT_SHARDS (it's only loaded when
        // the user is actively searching for comets). It is *not* parsed
        // through the generic catalog loader (comets have orbital elements,
        // not a fixed RA/Dec). It is loaded through Comets.fromShard, which
        // uses the dedicated CometsShard type.
        val text = this::class.java.classLoader
            ?.getResource("comets.json")
            ?.readText()
            ?: error("comets.json not on classpath")
        val comets = Comets.fromShard(text)
        assertTrue(
            comets.size >= 5,
            "expected at least 5 comets, got ${comets.size}",
        )
        val halley = comets.firstOrNull { it.designation == "1P/Halley" }
        assertNotNull(halley, "Halley not in comet shard")
    }

    @Test
    fun loadFromSkipsMissingShards() {
        // All shards "missing" — should return an empty catalog, not throw.
        val cat = EmbeddedCatalog.loadFrom(
            paths = listOf("does-not-exist-1.json", "does-not-exist-2.json"),
            reader = { null }
        )
        assertEquals(0, cat.objects.size)
    }

    private fun loadBundled(): Catalog {
        val text = this::class.java.classLoader
            ?.getResource("catalog.json")
            ?.readText()
            ?: error("catalog.json not on test classpath")
        return Catalog.parse(text)
    }
}
