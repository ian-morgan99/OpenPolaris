package dev.openpolaris.core.domain

import dev.openpolaris.core.solver.CatalogStar
import dev.openpolaris.core.solver.OnDevicePlateSolver
import dev.openpolaris.core.solver.PyramidMatcher
import dev.openpolaris.core.solver.StarDetection
import dev.openpolaris.core.solver.SyntheticTestCatalog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests [GoToController.solveAndRefine] using the synthetic cluster.
 *
 * Strategy: tell the mount it is currently pointing at the **horizon
 * position of the cluster** (computed via [AstroMath.toHorizontalAt]).
 * Construct detections of the same cluster stars at the expected
 * pixel positions. Call [solveAndRefine] with the cluster as target.
 * The solver should succeed and `refine` should issue a 519 nudge
 * whose commanded az/alt now points at the cluster.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GoToControllerSolveAndRefineTest {

    private val frameW = 1280
    private val frameH = 960

    private val lat = 40.0
    private val lng = 0.0
    private val jd = 2460000.0

    private val truthRa = SyntheticTestCatalog.CLUSTER_RA_DEG
    private val truthDec = SyntheticTestCatalog.CLUSTER_DEC_DEG

    private val clusterStars: List<CatalogStar> = SyntheticTestCatalog
        .stars
        .filter { it.name?.startsWith("cluster-") == true }
        .sortedBy { it.mag }
        .take(6)

    private fun project(s: CatalogStar, centreRaDeg: Double, centreDecDeg: Double): Pair<Double, Double> {
        val ra0 = Math.toRadians(centreRaDeg)
        val dec0 = Math.toRadians(centreDecDeg)
        val ra = Math.toRadians(s.raDeg)
        val dec = Math.toRadians(s.decDeg)
        val cosC = Math.sin(dec0) * Math.sin(dec) +
            Math.cos(dec0) * Math.cos(dec) * Math.cos(ra - ra0)
        val x = (Math.cos(dec) * Math.sin(ra - ra0)) / cosC
        val y = (Math.cos(dec0) * Math.sin(dec) -
            Math.sin(dec0) * Math.cos(dec) * Math.cos(ra - ra0)) / cosC
        val degPerRad = 180.0 / PI
        val degPerPixel = 5.0 / minOf(frameW, frameH) / 2.0
        val px = frameW / 2.0 + x * degPerRad / degPerPixel
        val py = frameH / 2.0 - y * degPerRad / degPerPixel
        return px to py
    }

    private fun detections(): List<StarDetection> = clusterStars.mapIndexed { i, s ->
        val (px, py) = project(s, truthRa, truthDec)
        StarDetection(px, py, 1000.0 - i * 10.0)
    }

    private fun gimbalFrame(az: Double, alt: Double): ByteArray {
        val s = "1&517&2&yaw:%.4f;pitch:%.4f;roll:0.0;#".format(az, alt)
        return s.toByteArray(Charsets.US_ASCII)
    }

    @Test
    fun solveAndRefineSendsGotoFrame() = runTest {
        val conn = FakeConnection()
        val session = MountSession({ conn })
        val tracking = TrackingController(session)
        val controller = GoToController(session, tracking)
        session.connect()

        // Mount reports it is currently at the cluster's horizon coords.
        val h = AstroMath.toHorizontalAt(truthRa, truthDec, lat, lng, jd)
        // Queue two 517 replies: one for solveAndRefine's initial pose
        // poll, one for refine's mid-flight pose poll. The corrective
        // 519 frame is written immediately after the second poll.
        conn.responses += gimbalFrame(h.azimuthDeg, h.altitudeDeg)
        conn.responses += gimbalFrame(h.azimuthDeg, h.altitudeDeg)

        val solver = OnDevicePlateSolver(
            catalog = SyntheticTestCatalog.asCatalog,
            matcher = PyramidMatcher(angularToleranceDeg = 0.05, pixelTolerance = 5.0),
            searchRadiusDeg = 5.0,
            minConfidence = 0.5,
        )

        val result = controller.solveAndRefine(
            solver = solver,
            detections = detections(),
            frameWidth = frameW,
            frameHeight = frameH,
            targetRaDeg = truthRa,
            targetDecDeg = truthDec,
            latDeg = lat,
            lngEastDeg = lng,
            jdUtc = jd,
        )
        assertNotNull(result, "solver should converge and refine should fire")

        // A 519 frame must have been written. Since the mount is
        // already at the cluster, the corrective nudge should be near
        // zero — but the frame still must be emitted.
        val gotoFrames = conn.written.map { it.decodeToString() }.filter { it.contains("&519&") }
        assertTrue(gotoFrames.isNotEmpty(), "expected a 519 goto frame, got ${conn.written.size} writes")
    }
}
