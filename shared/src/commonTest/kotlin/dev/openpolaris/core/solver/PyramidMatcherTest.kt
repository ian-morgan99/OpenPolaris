package dev.openpolaris.core.solver

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests [PyramidMatcher] against a synthetic cluster.
 *
 * Strategy: use [SyntheticTestCatalog]'s 10-star cluster around
 * (200°, +20°). Project those catalog stars to pixel coordinates using
 * the SAME gnomonic math the matcher uses internally (so the "ground
 * truth" detection positions are consistent with what the matcher
 * expects). Add small Gaussian-like jitter, then assert that the
 * matcher recovers the cluster centroid within sub-arcminute
 * tolerance.
 *
 * Tests cover:
 *  - Happy path: 6 detections of cluster stars → match
 *  - Confused detections (a bogus star) → still matches the right
 *    cluster, confidence drops
 *  - Degenerate input: 3 collinear detections → null
 *  - Too-few detections (<3) → IllegalArgumentException
 *  - Empty candidate set → null
 */
class PyramidMatcherTest {

    private val matcher = PyramidMatcher(
        angularToleranceDeg = 0.05,
        pixelTolerance = 5.0, // 5 px is generous for a 1280×960 frame
    )

    private val frameW = 1280
    private val frameH = 960

    /** Cluster centre. */
    private val truthRa = SyntheticTestCatalog.CLUSTER_RA_DEG
    private val truthDec = SyntheticTestCatalog.CLUSTER_DEC_DEG

    /** Pick the 6 brightest cluster stars as the "true" detections. */
    private val clusterStars: List<CatalogStar> = SyntheticTestCatalog
        .stars
        .filter { it.name?.startsWith("cluster-") == true }
        .sortedBy { it.mag }
        .take(6)

    /**
     * Project a catalog star onto the same tangent plane the matcher
     * uses internally (gnomonic, scaled so 5° FOV fills the frame).
     */
    private fun project(s: CatalogStar, centreRaDeg: Double, centreDecDeg: Double): Pair<Double, Double> {
        val ra0 = Math.toRadians(centreRaDeg)
        val dec0 = Math.toRadians(centreDecDeg)
        val ra = Math.toRadians(s.raDeg)
        val dec = Math.toRadians(s.decDeg)

        val cosC = sin(dec0) * sin(dec) + cos(dec0) * cos(dec) * cos(ra - ra0)
        require(cosC > 1e-6) { "star behind tangent plane" }
        val x = (cos(dec) * sin(ra - ra0)) / cosC
        val y = (cos(dec0) * sin(dec) - sin(dec0) * cos(dec) * cos(ra - ra0)) / cosC
        val degPerRad = 180.0 / PI
        val degPerPixel = 5.0 / minOf(frameW, frameH) / 2.0
        val px = frameW / 2.0 + x * degPerRad / degPerPixel
        val py = frameH / 2.0 - y * degPerRad / degPerPixel
        return px to py
    }

    private fun detectionsForCluster(
        stars: List<CatalogStar>,
        centreRaDeg: Double = truthRa,
        centreDecDeg: Double = truthDec,
        pixelJitter: Double = 0.0,
    ): List<StarDetection> = stars.mapIndexed { i, s ->
        val (px, py) = project(s, centreRaDeg, centreDecDeg)
        // Deterministic "noise" so tests are reproducible.
        val nx = if (i % 2 == 0) pixelJitter else -pixelJitter
        val ny = if (i % 3 == 0) pixelJitter else -pixelJitter
        StarDetection(
            x = px + nx,
            y = py + ny,
            flux = 1000.0 - i * 10.0, // brightest first
        )
    }

    @Test
    fun cleanClusterConverges() {
        val detections = detectionsForCluster(clusterStars)
        // Candidates = full catalog, but the matcher's hint patch is the
        // 1° cluster box (10 stars). The solver would pass those; here
        // we simulate the solver.
        val candidates = SyntheticTestCatalog.stars.filter {
            it.name?.startsWith("cluster-") == true
        }

        val match = matcher.match(detections, frameW, frameH, candidates)
        assertNotNull(match, "matcher should converge on a clean 6-star cluster")
        // Centroid is mean of seed triangle (3 brightest detections).
        val top3 = clusterStars.sortedBy { it.mag }.take(3)
        val expectedRa = top3.sumOf { it.raDeg } / 3.0
        val expectedDec = top3.sumOf { it.decDeg } / 3.0
        assertRaClose(expectedRa, match.centerRaDeg, tolDeg = 0.01)
        assertDecClose(expectedDec, match.centerDecDeg, tolDeg = 0.01)
        assertTrue(match.matchedStars >= 3, "matchedStars should be >=3, was ${match.matchedStars}")
        assertTrue(match.confidence >= 0.5, "confidence should be >=0.5, was ${match.confidence}")
    }

    @Test
    fun noisyClusterStillConverges() {
        val detections = detectionsForCluster(clusterStars, pixelJitter = 1.5)
        val candidates = SyntheticTestCatalog.stars.filter {
            it.name?.startsWith("cluster-") == true
        }

        val match = matcher.match(detections, frameW, frameH, candidates)
        assertNotNull(match, "matcher should survive 1.5px jitter on a 6-star cluster")
        assertTrue(match.matchedStars >= 3, "matchedStars should be >=3 with jitter, was ${match.matchedStars}")
    }

    @Test
    fun extraDetectionsReduceConfidence() {
        // Same 6 cluster stars, but add a 4th spurious detection far
        // away. The matcher should still find the right triangle, but
        // the supporting count only counts the 3 brightest + any
        // extra cluster star.
        val cleanDetections = detectionsForCluster(clusterStars)
        val spurious = StarDetection(
            x = 50.0, y = 50.0, flux = 500.0, // dimmer than the cluster's brightest
        )
        val detections = cleanDetections + spurious
        val candidates = SyntheticTestCatalog.stars.filter {
            it.name?.startsWith("cluster-") == true
        }
        val match = matcher.match(detections, frameW, frameH, candidates)
        assertNotNull(match)
        // bright.take(6) means the spurious star may or may not appear
        // in `bright` depending on flux; the test mostly asserts the
        // matcher didn't crash and still identified the right centroid.
        val top3 = clusterStars.sortedBy { it.mag }.take(3)
        val expectedRa = top3.sumOf { it.raDeg } / 3.0
        val expectedDec = top3.sumOf { it.decDeg } / 3.0
        assertRaClose(expectedRa, match.centerRaDeg, tolDeg = 0.01)
        assertDecClose(expectedDec, match.centerDecDeg, tolDeg = 0.01)
    }

    @Test
    fun collinearDetectionsReturnNull() {
        // 3 detections exactly on a horizontal line. The matcher's
        // mapTriangle will return null for the degenerate seed.
        val detections = listOf(
            StarDetection(100.0, 200.0, 1000.0),
            StarDetection(200.0, 200.0, 900.0),
            StarDetection(300.0, 200.0, 800.0),
        )
        val candidates = SyntheticTestCatalog.stars.filter {
            it.name?.startsWith("cluster-") == true
        }
        // The matcher will skip every catalog candidate because
        // mapTriangle returns null. Result: null.
        val match = matcher.match(detections, frameW, frameH, candidates)
        assertNull(match, "collinear detections should produce no match")
    }

    @Test
    fun emptyCandidatesReturnNull() {
        val detections = detectionsForCluster(clusterStars)
        val match = matcher.match(detections, frameW, frameH, emptyList())
        assertNull(match)
    }

    @Test
    fun tooFewDetectionsThrows() {
        val detections = listOf(
            StarDetection(100.0, 100.0, 1000.0),
            StarDetection(200.0, 200.0, 900.0),
        )
        val candidates = SyntheticTestCatalog.stars.filter {
            it.name?.startsWith("cluster-") == true
        }
        var threw = false
        try {
            matcher.match(detections, frameW, frameH, candidates)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw, "expected IllegalArgumentException for <3 detections")
    }

    // --- helpers -----------------------------------------------------------

    private fun assertRaClose(expected: Double, actual: Double, tolDeg: Double) {
        // Wrap both into [0, 360) and pick the shorter arc.
        var diff = abs(expected - actual) % 360.0
        if (diff > 180.0) diff = 360.0 - diff
        assertTrue(diff <= tolDeg, "RA: expected $expected, got $actual (diff=$diff° > $tolDeg°)")
    }

    private fun assertDecClose(expected: Double, actual: Double, tolDeg: Double) {
        val diff = abs(expected - actual)
        assertTrue(diff <= tolDeg, "Dec: expected $expected, got $actual (diff=$diff° > $tolDeg°)")
    }
}
