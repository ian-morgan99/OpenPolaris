package dev.openpolaris.ui

import dev.openpolaris.core.domain.AstroMath
import dev.openpolaris.core.domain.Connection
import dev.openpolaris.core.domain.MountSession
import dev.openpolaris.core.solver.CatalogStar
import dev.openpolaris.core.solver.OnDevicePlateSolver
import dev.openpolaris.core.solver.PyramidMatcher
import dev.openpolaris.core.solver.StarDetection
import dev.openpolaris.core.solver.SyntheticTestCatalog
import dev.openpolaris.core.solver.StarDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests [AppViewModel.solveNow].
 *
 * Strategy: stand up an [AppViewModel] with a fake [Connection] that
 * responds to 517 gimbal-position polls with the cluster's horizon
 * coordinates (computed via [AstroMath.toHorizontalAt]). Push a synthetic
 * JPEG into [dev.openpolaris.core.domain.PreviewController] via the
 * [dev.openpolaris.core.domain.PreviewController.publishForTest] test
 * seam, swap in a [StarDetector] that returns the cluster's detections,
 * and assert [AppViewModel.lastSolveResult] converges on the cluster RA/Dec.
 */

/** In-memory [Connection] that records writes and can emit scripted responses. */
private class FakeConnection : Connection {
    val written = mutableListOf<ByteArray>()
    val responses = mutableListOf<ByteArray>()
    var failConnect = false
    var failMessage: String? = null

    override suspend fun connect(host: String, port: Int, timeoutMs: Int) {
        if (failConnect) throw java.io.IOException("refused")
    }

    override suspend fun write(data: ByteArray) {
        try {
            written += data
        } catch (t: Throwable) {
            failMessage = t.message
        }
    }

    override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int {
        if (responses.isEmpty()) return -1
        val r = responses.removeAt(0)
        r.copyInto(buffer)
        return r.size
    }

    override fun close() {}
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelSolveNowTest {

    private val frameW = 1280
    private val frameH = 960

    private val lat = 40.0
    private val lng = 0.0

    private val truthRa = SyntheticTestCatalog.CLUSTER_RA_DEG
    private val truthDec = SyntheticTestCatalog.CLUSTER_DEC_DEG

    private val jdUtc = 2460000.0

    private val clusterStars: List<CatalogStar> = SyntheticTestCatalog
        .stars
        .filter { it.name?.startsWith("cluster-") == true }
        .sortedBy { it.mag }
        .take(6)

    private fun project(
        s: CatalogStar,
        centreRaDeg: Double,
        centreDecDeg: Double,
    ): Pair<Double, Double> {
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

    /**
     * StarDetector that returns the cluster's detections regardless of
     * the input bytes. The JVM/desktop targets use [NullStarDetector],
     * so a real one is needed for solveNow to do meaningful work.
     */
    private class ClusterStarDetector : StarDetector {
        override suspend fun detect(
            frame: ByteArray,
            frameWidth: Int,
            frameHeight: Int,
        ): List<StarDetection> {
            val ra = SyntheticTestCatalog.CLUSTER_RA_DEG
            val dec = SyntheticTestCatalog.CLUSTER_DEC_DEG
            val stars = SyntheticTestCatalog.stars
                .filter { it.name?.startsWith("cluster-") == true }
                .sortedBy { it.mag }
                .take(6)
            val ra0 = Math.toRadians(ra)
            val dec0 = Math.toRadians(dec)
            val minSide = minOf(frameWidth, frameHeight)
            val degPerRad = 180.0 / PI
            val degPerPixel = 5.0 / minSide / 2.0
            return stars.mapIndexed { i, s ->
                val ras = Math.toRadians(s.raDeg)
                val decs = Math.toRadians(s.decDeg)
                val cosC = Math.sin(dec0) * Math.sin(decs) +
                    Math.cos(dec0) * Math.cos(decs) * Math.cos(ras - ra0)
                val x = (Math.cos(decs) * Math.sin(ras - ra0)) / cosC
                val y = (Math.cos(dec0) * Math.sin(decs) -
                    Math.sin(dec0) * Math.cos(decs) * Math.cos(ras - ra0)) / cosC
                val px = frameWidth / 2.0 + x * degPerRad / degPerPixel
                val py = frameHeight / 2.0 - y * degPerRad / degPerPixel
                StarDetection(px, py, 1000.0 - i * 10.0)
            }
        }
    }

    private fun newViewModel(
        scope: CoroutineScope,
        connectionFactory: () -> Connection,
    ): AppViewModel = AppViewModel(
        scope = scope,
        connectionFactory = connectionFactory,
        solver = OnDevicePlateSolver(
            catalog = SyntheticTestCatalog.asCatalog,
            matcher = PyramidMatcher(angularToleranceDeg = 0.05, pixelTolerance = 5.0),
            searchRadiusDeg = 5.0,
            minConfidence = 0.5,
        ),
        starDetector = ClusterStarDetector(),
    )

    @Test
    fun solveNowWithoutSessionIsRejected() = runTest {
        val vm = newViewModel(this) { error("not used") }
        try {
            vm.solveNow(frameW, frameH)
            assertEquals("Not connected", vm.statusMessage)
            assertNull(vm.lastSolveResult)
        } finally {
            vm.preview.shutdown()
        }
    }

    @Test
    fun solveNowWithoutPreviewIsRejected() = runTest(UnconfinedTestDispatcher()) {
        val vm = newViewModel(this) { error("not used") }
        try {
            // Bring up a session but no preview frame.
            val conn = FakeConnection()
            // Bypass connect() (which would start preview too) by installing
            // the session/controller directly: this is the same code path
            // connect() executes inside its launch{} block.
            installSession(vm, conn)
            advanceUntilIdle()

            vm.setRaDecMode(true)
            vm.updateLat("40.0")
            vm.updateLng("0.0")
            vm.updateRa("13 20 00")
            vm.updateDec("20 00 00")
            vm.solveNow(frameW, frameH)
            advanceUntilIdle()
            assertTrue(
                vm.statusMessage.contains("No preview frame"),
                "expected 'No preview frame' in status, got '${vm.statusMessage}'",
            )
        } finally {
            vm.preview.shutdown()
        }
    }

    @Test
    fun solveNowHappyPathSetsResult() = runTest(UnconfinedTestDispatcher()) {
        val conn = FakeConnection()
        val vm = newViewModel(this) { conn }

        try {
            installSession(vm, conn)
            advanceUntilIdle()

            // Mount reports it is currently at the cluster's horizon coords.
            val h = AstroMath.toHorizontalAt(truthRa, truthDec, lat, lng, jdUtc)
            // Queue two 517 replies: one for solveAndRefine's initial pose
            // poll, one for refine's mid-flight pose poll. The corrective
            // 519 frame is written immediately after the second poll.
            conn.responses += gimbalFrame(h.azimuthDeg, h.altitudeDeg)
            conn.responses += gimbalFrame(h.azimuthDeg, h.altitudeDeg)

            vm.setRaDecMode(true)
            vm.updateLat("40.0")
            vm.updateLng("0.0")
            vm.updateRa("13 20 00")
            vm.updateDec("20 00 00")

            // Push a synthetic frame into the preview controller via the
            // test seam so the gate "jpeg == null" passes.
            vm.testSetPreview(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()))

            vm.solveNow(frameW, frameH, jdUtc = jdUtc)
            advanceUntilIdle()

            assertNull(conn.failMessage, "FakeConnection error: ${conn.failMessage}")
            val result = vm.lastSolveResult
            assertNotNull(result, "expected lastSolveResult, status='${vm.statusMessage}'")
            assertTrue(
                kotlin.math.abs(result.raDeg - truthRa) < 1.0,
                "expected raDeg ≈ $truthRa, got ${result.raDeg}",
            )
            assertTrue(
                kotlin.math.abs(result.decDeg - truthDec) < 1.0,
                "expected decDeg ≈ $truthDec, got ${result.decDeg}",
            )
            assertEquals(0.6, result.confidence, 0.001)
            assertEquals(3, result.matchedStars)
            assertTrue(
                vm.statusMessage.startsWith("Solved RA"),
                "expected 'Solved RA …' status, got '${vm.statusMessage}'",
            )
        } finally {
            vm.preview.shutdown()
        }
    }

    @Test
    fun disconnectClearsSolveState() = runTest(UnconfinedTestDispatcher()) {
        val conn = FakeConnection()
        val vm = newViewModel(this) { conn }
        try {
            installSession(vm, conn)
            advanceUntilIdle()

            // Force a solve result by directly setting it (no need to run
            // the full solve path for the disconnect test).
            vm.testSetLastSolve(199.0, 19.0)

            vm.disconnect()
            advanceUntilIdle()
            assertNull(vm.lastSolveResult)
            assertEquals(false, vm.solveInProgress)
        } finally {
            vm.preview.shutdown()
        }
    }

    /**
     * Install a session + controllers the same way [AppViewModel.connect]
     * does inside its launch{} block, but without going through
     * [AppViewModel.connect] (which would also start a poll loop and
     * preview fetch). Lets the test drive `solveNow` with a working
     * session and no background poll noise.
     *
     * We still call [MountSession.connect] so the session's internal
     * `connection` field is populated — otherwise `solveAndRefine`'s
     * `request()` returns ProtocolError("not connected") and the test
     * sees "Plate-solve failed" with no diagnostic.
     */
    private suspend fun installSession(vm: AppViewModel, conn: Connection) {
        val s = MountSession({ conn }, "127.0.0.1", 9090)
        s.connect()
        vm.testInstallSession(s)
    }
}
