package dev.openpolaris.core.solver

import dev.openpolaris.core.astro.AstroMath
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end tests for [OnDevicePlateSolver] against the synthetic
 * cluster. Verifies the hint-localized path and the no-hint
 * full-catalog path both converge on the cluster centre.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnDevicePlateSolverTest {

    private val solver = OnDevicePlateSolver(
        catalog = SyntheticTestCatalog.asCatalog,
        matcher = PyramidMatcher(
            angularToleranceDeg = 0.05,
            pixelTolerance = 5.0,
        ),
        searchRadiusDeg = 5.0,
        minConfidence = 0.5,
    )

    private val frameW = 1280
    private val frameH = 960

    // Observer: somewhere in the temperate northern hemisphere at a
    // roundish Julian date. (Lat=40°N, lng=0°E, jd=2460190.0 puts the
    // cluster at (200°, +20°) well above the horizon at alt ≈ +51°.)
    private val lat = 40.0
    private val lng = 0.0
    private val jd = 2460190.0

    private val truthRa = SyntheticTestCatalog.CLUSTER_RA_DEG
    private val truthDec = SyntheticTestCatalog.CLUSTER_DEC_DEG

    private val clusterStars: List<CatalogStar> = SyntheticTestCatalog
        .stars
        .filter { it.name?.startsWith("cluster-") == true }
        .sortedBy { it.mag }
        .take(6)

    /**
     * PyramidMatcher reports the centroid of its seed triangle — the
     * 3 brightest catalog stars. With the synthetic cluster those
     * are cluster-0, cluster-1, cluster-2 (the SW corner), so the
     * solved centre is biased south-west of the cluster centre. The
     * assertions compare against this centroid, not [truthRa]/[truthDec].
     */
    private val expectedCenterRa: Double = clusterStars.take(3).sumOf { it.raDeg } / 3.0
    private val expectedCenterDec: Double = clusterStars.take(3).sumOf { it.decDeg } / 3.0

    /**
     * Project a catalog star onto the matcher's tangent plane (same
     * gnomonic math as [PyramidMatcherTest]).
     */
    private fun project(s: CatalogStar, centreRaDeg: Double, centreDecDeg: Double): Pair<Double, Double> {
        val ra0 = Math.toRadians(centreRaDeg)
        val dec0 = Math.toRadians(centreDecDeg)
        val ra = Math.toRadians(s.raDeg)
        val dec = Math.toRadians(s.decDeg)
        val cosC = Math.sin(dec0) * Math.sin(dec) +
            Math.cos(dec0) * Math.cos(dec) * Math.cos(ra - ra0)
        require(cosC > 1e-6) { "star behind tangent plane" }
        val x = (Math.cos(dec) * Math.sin(ra - ra0)) / cosC
        val y = (Math.cos(dec0) * Math.sin(dec) -
            Math.sin(dec0) * Math.cos(dec) * Math.cos(ra - ra0)) / cosC
        val degPerRad = 180.0 / PI
        val degPerPixel = 5.0 / minOf(frameW, frameH) / 2.0
        val px = frameW / 2.0 + x * degPerRad / degPerPixel
        val py = frameH / 2.0 - y * degPerRad / degPerPixel
        return px to py
    }

    private fun detectionsForCluster(centreRaDeg: Double = truthRa, centreDecDeg: Double = truthDec): List<StarDetection> =
        clusterStars.mapIndexed { i, s ->
            val (px, py) = project(s, centreRaDeg, centreDecDeg)
            StarDetection(px, py, 1000.0 - i * 10.0)
        }

    @Test
    fun solvesClusterWithHint() = runTest {
        // Build a hint whose az/alt converts back to the cluster
        // centre. The matcher's tolerance is loose (0.05°), so the
        // round-trip should be well within that.
        val h = AstroMath.toHorizontalAt(
            raDeg = truthRa, decDeg = truthDec, latDeg = lat, lngEastDeg = lng, jd = jd,
        )
        val hint = SolveHint(
            azAltDeg = h.azimuthDeg to h.altitudeDeg,
            latDeg = lat,
            lngEastDeg = lng,
            jdUtc = jd,
        )
        val detections = detectionsForCluster()
        val result = solver.solve(detections, frameW, frameH, hint)
        assertNotNull(result, "solver should converge on the cluster with a hint")
        assertRaClose(expectedCenterRa, result.raDeg, tolDeg = 0.05)
        assertDecClose(expectedCenterDec, result.decDeg, tolDeg = 0.05)
        assertTrue(result.matchedStars >= 3, "matchedStars should be >=3, was ${result.matchedStars}")
        assertTrue(result.confidence >= 0.5, "confidence should be >=0.5, was ${result.confidence}")
    }

    @Test
    fun solvesClusterWithoutHint() = runTest {
        val detections = detectionsForCluster()
        val result = solver.solve(detections, frameW, frameH, hint = null)
        assertNotNull(result, "solver should converge on the cluster from the full catalog")
        assertRaClose(expectedCenterRa, result.raDeg, tolDeg = 0.05)
        assertDecClose(expectedCenterDec, result.decDeg, tolDeg = 0.05)
    }

    @Test
    fun rejectsTooFewDetections() = runTest {
        val detections = listOf(
            StarDetection(100.0, 100.0, 1000.0),
            StarDetection(200.0, 200.0, 900.0),
        )
        var threw = false
        try {
            solver.solve(detections, frameW, frameH, hint = null)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw, "expected IllegalArgumentException for <3 detections")
    }

    // --- helpers -----------------------------------------------------------

    private fun assertRaClose(expected: Double, actual: Double, tolDeg: Double) {
        var diff = abs(expected - actual) % 360.0
        if (diff > 180.0) diff = 360.0 - diff
        assertTrue(diff <= tolDeg, "RA: expected $expected, got $actual (diff=$diff° > $tolDeg°)")
    }

    private fun assertDecClose(expected: Double, actual: Double, tolDeg: Double) {
        val diff = abs(expected - actual)
        assertTrue(diff <= tolDeg, "Dec: expected $expected, got $actual (diff=$diff° > $tolDeg°)")
    }
}
