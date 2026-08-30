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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.lang.System.currentTimeMillis
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
    // Use a Channel so the reader can BLOCK (suspend) when no responses
    // are available, instead of returning -1 immediately. Without this
    // the reader's pre-queued 517 frames are consumed by the install
    // session handshake and any subsequent 517 waiter added later (e.g.
    // by GoToController.solveAndRefine) never receives a frame,
    // causing MountSession.request(GET_GIMBAL_POS) to time out.
    private val channel = kotlinx.coroutines.channels.Channel<ByteArray>(kotlinx.coroutines.channels.Channel.BUFFERED)
    var failConnect = false
    var failMessage: String? = null

    /** Append a response for the reader to consume. */
    fun enqueueResponse(data: ByteArray) {
        channel.trySend(data)
    }

    /**
     * Optional hook invoked from inside [write] with the raw request
     * bytes. Tests can install a script that enqueues the appropriate
     * response frames based on what was written, decoupling the script
     * from the reader's consumption order.
     */
    var onWrite: ((ByteArray) -> Unit)? = null

    override suspend fun connect(host: String, port: Int, timeoutMs: Int) {
        if (failConnect) throw java.io.IOException("refused")
    }

    override suspend fun write(data: ByteArray) {
        try {
            written += data
            onWrite?.invoke(data)
        } catch (t: Throwable) {
            failMessage = t.message
        }
    }

    override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int {
        // Suspend until a response is enqueued. Using `tryReceive` after a
        // `delay(timeoutMs)` is fundamentally broken under `runTest`'s
        // virtual scheduler: the delay consumes the entire 200ms window
        // before the writer can enqueue a response, and `tryReceive` is
        // non-blocking — so we return -1 and re-loop, eating another
        // 200ms of virtual time. The writer's `withTimeout(60000)` is
        // also on the test scheduler, so eventually the timeout fires
        // before the response is ever consumed.
        //
        // `channel.receive()` suspends until a value is available, so
        // the test scheduler correctly advances to the enqueue point
        // without burning virtual time.
        val r = channel.receive()
        r.copyInto(buffer)
        return r.size
    }

    override fun close() {
        channel.close()
    }
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
            installSession(vm, conn, this)
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

        // Mount reports it is currently at the cluster's horizon coords.
        val h = AstroMath.toHorizontalAt(truthRa, truthDec, lat, lng, jdUtc)
        // Wire up onWrite to enqueue responses AS the writer issues
        // requests. This decouples the reader's consumption order from
        // the writer's request order, which is required because
        // MountSession.tryConnect launches the reader coroutine BEFORE
        // it registers the 284 waiter (see L202 startReader then
        // L205-208 request(284)). Any pre-queued 284 ack would be
        // consumed by the reader before the waiter exists, so we must
        // enqueue it on demand from inside the write call. The same
        // pattern covers the 517 gimbal polls in the happy path
        // (GoToController.solveAndRefine L134, refine L102). The
        // corrective slew is fire-and-forget (TrackingController.
        // gotoAzAlt), so no 519 ack is needed.
        conn.onWrite = { req ->
            // Request frames use the form "1&<code>&<type>&<payload>#".
            // Match on the "1&<code>&" prefix to enqueue the matching
            // response.
            val s = String(req, Charsets.US_ASCII)
            when {
                s.startsWith("1&284&") -> {
                    conn.enqueueResponse("1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII))
                }
                s.startsWith("1&517&") -> {
                    conn.enqueueResponse(gimbalFrame(h.azimuthDeg, h.altitudeDeg))
                }
            }
        }

        try {
            installSession(vm, conn, this)
            advanceUntilIdle()

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
            // Confidence and matchedStars now flow from the real
            // PyramidMatcher (issue #13); the old placeholders (0.6, 3)
            // are gone. Assert they are non-zero and that the solve
            // meets the matcher's gate.
            assertTrue(
                result.confidence >= 0.5,
                "expected confidence ≥ 0.5 from real solver, got ${result.confidence}",
            )
            assertTrue(
                result.matchedStars >= 3,
                "expected matchedStars ≥ 3 from real solver, got ${result.matchedStars}",
            )
            assertTrue(
                vm.statusMessage.startsWith("Solved RA"),
                "expected 'Solved RA …' status, got '${vm.statusMessage}'",
            )
            // Status message should now report real match quality
            // (issue #13), not just RA/Dec.
            assertTrue(
                vm.statusMessage.contains("conf") && vm.statusMessage.contains("stars"),
                "expected status to report conf & stars, got '${vm.statusMessage}'",
            )
        } finally {
            // Tear down the session so MountSession.disconnect cancels
            // the reader coroutine (which would otherwise keep the test
            // scheduler alive and trigger UncompletedCoroutinesError).
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun disconnectClearsSolveState() = runTest(UnconfinedTestDispatcher()) {
        val conn = FakeConnection()
        val vm = newViewModel(this) { conn }
        try {
            installSession(vm, conn, this)
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
    private suspend fun installSession(
        vm: AppViewModel,
        conn: Connection,
        readerScope: CoroutineScope,
    ) {
        // Inject the test's runTest scope as MountSession.readerScope.
        // Without this, the reader runs on MountSession's default
        // Dispatchers.Default scope (real time), and the test's virtual
        // scheduler advances through the 2000ms withTimeout in `request`
        // before the real-time reader can poll the response channel. The
        // symptom is a deterministic "Plate-solve failed (no confident
        // match)" because solveAndRefine's 517 gimbal-position request
        // times out before the 517 frame is delivered.
        //
        // By passing the test scope, the reader's delay(READ_RETRY_MS)
        // loop is driven by the test scheduler, and advanceUntilIdle()
        // is now sufficient to flush every pending read + write +
        // response-handler pair.
        val s = MountSession(
            connectionFactory = { conn },
            host = "127.0.0.1",
            port = 9090,
            readerScope = readerScope,
        )
        s.connect()
        vm.testInstallSession(s)
    }
}
