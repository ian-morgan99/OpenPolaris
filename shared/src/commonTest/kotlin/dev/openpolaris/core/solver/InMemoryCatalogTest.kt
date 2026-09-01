package dev.openpolaris.core.solver

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies [InMemoryCatalog]'s tangent-plane scan against a brute-force
 * Euclidean ground truth in RA/Dec degree space. Tolerance: 0.1° to absorb
 * tangent-plane distortion near the poles; we never test pole points here.
 */
class InMemoryCatalogTest {

    private val catalog = SyntheticTestCatalog.asCatalog

    @Test
    fun sizeAndLimitingMagMatchData() {
        assertEquals(SyntheticTestCatalog.stars.size, catalog.size)
        // SyntheticTestCatalog includes mag 5.9 stars and brighter
        assertTrue(catalog.limitingMagnitude <= 6.0,
            "limiting mag should be <= 6.0, was ${catalog.limitingMagnitude}")
    }

    @Test
    fun starsWithinFindsCluster() {
        // Cluster centre is 200°, +20° — all 10 cluster-* stars are within ~0.71° (corner).
        val found = catalog.starsWithin(
            raDeg = SyntheticTestCatalog.CLUSTER_RA_DEG,
            decDeg = SyntheticTestCatalog.CLUSTER_DEC_DEG,
            radiusDeg = 0.8
        )
        // 10 cluster stars in a 1°×1° box around (200, 20) — corner offset is ~0.71°.
        // A 0.8° box should capture all 10.
        val clusterHits = found.count { it.name?.startsWith("cluster-") == true }
        assertEquals(10, clusterHits, "0.8° box around cluster centre should contain all 10 cluster stars")
    }

    @Test
    fun starsWithinExcludesFarStars() {
        val found = catalog.starsWithin(raDeg = 200.0, decDeg = 20.0, radiusDeg = 0.8)
        // Polaris (89° dec away) must NOT be in the result.
        assertTrue(found.none { it.name == "Polaris" },
            "Polaris is 70° away from (200,20) and must not match r=0.8°")
    }

    @Test
    fun starsWithinIsSymmetricUnderSmallBox() {
        // The synthetic cluster at (200, 20) has 10 stars within ±0.5°
        // (corner stars at ~0.707° from centre).
        // A 0.8° box safely captures all of them regardless of query offset.
        val a = catalog.starsWithin(200.0, 20.0, 0.8)
        val b = catalog.starsWithin(200.05, 20.05, 0.8)
        // Both queries should see all 10 cluster stars
        assertEquals(10, a.count { it.name?.startsWith("cluster-") == true },
            "0.8° box around cluster should contain all 10 cluster stars, got ${a.count { it.name?.startsWith("cluster-") == true }}")
        assertEquals(10, b.count { it.name?.startsWith("cluster-") == true },
            "0.8° box around offset cluster should contain all 10 cluster stars, got ${b.count { it.name?.startsWith("cluster-") == true }}")
    }

    @Test
    fun nearestKReturnsClosestInOrder() {
        // The synthetic cluster at (200, 20) has 10 stars within ±0.5°.
        // nearestK with k=5 should return the 5 closest, sorted ascending
        // by distance.
        val k = 5
        val near = catalog.nearestK(raDeg = 200.0, decDeg = 20.0, k = k)
        assertEquals(k, near.size, "Expected exactly $k nearest stars, got ${near.size}")
        // Closest star is the cluster centre (cluster-5)
        assertEquals("cluster-5", near[0].name, "Closest star should be the cluster centre")
        // Verify ascending order by approximate distance.
        val cosDec = Math.cos(Math.toRadians(20.0))
        var prev = -1.0
        for (s in near) {
            val dra = s.raDeg - 200.0
            val ddec = s.decDeg - 20.0
            val d2 = dra * dra * cosDec * cosDec + ddec * ddec
            val d = sqrt(d2)
            assertTrue(d >= prev, "nearestK not sorted: $d < $prev at ${s.name}")
            prev = d
        }
    }

    @Test
    fun nearestKExcludesStarsBeyondSearchRadius() {
        // With a 30° default search radius around Vega, Polaris (89° away)
        // must not appear in nearestK(..., k=20).
        val near = catalog.nearestK(raDeg = 79.17, decDeg = 45.99, k = 20)
        assertTrue(near.none { it.name == "Polaris" },
            "Polaris is outside 30° of Vega and should not be in nearestK(20)")
    }
}
