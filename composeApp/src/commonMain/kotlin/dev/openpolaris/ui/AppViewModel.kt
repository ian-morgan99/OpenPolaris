package dev.openpolaris.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.openpolaris.core.domain.AlignmentController
import dev.openpolaris.core.domain.AstroMath
import dev.openpolaris.core.domain.AutoLevelController
import dev.openpolaris.core.domain.Connection
import dev.openpolaris.core.domain.GimbalPosition
import dev.openpolaris.core.domain.GoToController
import dev.openpolaris.core.domain.HelpersController
import dev.openpolaris.core.domain.MountMode
import dev.openpolaris.core.domain.MountSession
import dev.openpolaris.core.domain.MountState
import dev.openpolaris.core.domain.PreviewController
import dev.openpolaris.core.domain.TrackingController
import dev.openpolaris.core.protocol.CommandTable
import dev.openpolaris.core.solver.NullStarDetector
import dev.openpolaris.core.solver.OnDevicePlateSolver
import dev.openpolaris.core.solver.PlateSolver
import dev.openpolaris.core.solver.SolveHint
import dev.openpolaris.core.solver.SolveResult
import dev.openpolaris.core.solver.StarDetector
import dev.openpolaris.core.solver.SyntheticTestCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * UI-facing view model. Owns the MountSession lifecycle and exposes observable
 * state for Compose. `connectionFactory` is injected so tests and the desktop
 * simulator can substitute a fake connection.
 */
class AppViewModel(
    private val scope: CoroutineScope,
    private val connectionFactory: () -> Connection,
    private val solver: PlateSolver = OnDevicePlateSolver(SyntheticTestCatalog.asCatalog),
    private val starDetector: StarDetector = NullStarDetector,
) {
    var host by mutableStateOf("192.168.43.1")
        private set

    var mount by mutableStateOf(MountState())
        private set

    var position by mutableStateOf<GimbalPosition?>(null)
        private set

    var statusMessage by mutableStateOf("Disconnected")
        private set

    var demoMode by mutableStateOf(false)
        private set

    private var session: MountSession? = null
    private var controller: TrackingController? = null
    private var autoLevelController: AutoLevelController? = null
    private var autoLevelJob: Job? = null
    private var pollJob: Job? = null

    // Live preview of the camera MJPEG stream. Independent of the control
    // socket so a slow preview frame can never block the mount poll loop.
    // Decoded JPEGs land in [previewFrame] on Dispatchers.Default.
    val preview = PreviewController(parent = scope.coroutineContext[Job])
    val previewState: StateFlow<PreviewController.State> get() = preview.state
    var previewFrame by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
        private set

    fun updateHost(h: String) { host = h }

    fun connect() {
        disconnect()
        demoMode = false
        val s = MountSession(connectionFactory, host, 9090)
        session = s
        controller = TrackingController(s)
        cameraController = dev.openpolaris.core.domain.CameraController(s)
        wireHelpers(s)
        startAutoLevel(s)
        scope.launch {
            statusMessage = "Connecting to $host…"
            if (s.connect()) {
                statusMessage = "Connected"
                startPolling(s)
                startPreview()
            } else {
                statusMessage = "Could not reach $host — try Demo mode"
            }
        }
    }

    /** Simulator mode: no hardware needed; drives a fake session locally. */
    fun connectDemo() {
        disconnect()
        demoMode = true
        val sim = SimulatedMount(scope)
        session = sim.session
        controller = TrackingController(sim.session)
        cameraController = dev.openpolaris.core.domain.CameraController(sim.session)
        wireHelpers(sim.session)
        startAutoLevel(sim.session)
        scope.launch {
            sim.session.connect()
            statusMessage = "Demo mode (simulated mount)"
            startPolling(sim.session)
            // No preview in demo mode: there is no MJPEG endpoint in the
            // simulator. PreviewController stays Idle, which the pane
            // renders as "Stream unavailable".
        }
    }

    fun disconnect() {
        pollJob?.cancel()
        stopAutoLevel()
        preview.stop()
        previewFrame = null
        session?.disconnect()
        session = null
        controller = null
        cameraController = null
        helpersController = null
        ditherEnabled = null
        settlingSeconds = null
        limitsEnabled = null
        settlingInput = ""
        mount = MountState()
        position = null
        lastSolveResult = null
        solveInProgress = false
        if (!demoMode) statusMessage = "Disconnected"
    }

    /**
     * Open the MJPEG preview stream on the current host. Each frame
     * is decoded off the main thread and published to [previewFrame].
     */
    private fun startPreview() {
        scope.launch {
            preview.bytes.collect { jpeg ->
                if (jpeg == null) {
                    previewFrame = null
                    return@collect
                }
                // Drop-on-late: if the user closes the pane, the next
                // decode will simply replace a stale bitmap.
                val decoded = withContext(Dispatchers.Default) {
                    runCatching { decodeJpegToImageBitmap(jpeg) }
                }
                decoded.getOrNull()?.let { previewFrame = it }
            }
        }
        preview.start(host, 8080)
    }

    private fun startPolling(s: MountSession) {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                when (val r = s.request(284) { MountState.fromFrame284(it) }) {
                    is MountSession.CmdResult.Ok -> mount = r.value
                    is MountSession.CmdResult.Timeout -> {}
                    is MountSession.CmdResult.ProtocolError -> statusMessage = "Error: ${r.message}"
                }
                when (val r = s.request(517) { GimbalPosition.fromFrame517(it) }) {
                    is MountSession.CmdResult.Ok -> position = r.value
                    else -> {}
                }
                delay(1000)
            }
        }
    }

    // ---- user actions -------------------------------------------------

    fun startTracking() = scope.launch { controller?.start() }
    fun stopTracking() = scope.launch { controller?.stop() }
    fun toggleHalfSpeed(on: Boolean) = scope.launch { controller?.setHalfSpeed(on) }
    fun enableAhrs(on: Boolean) = scope.launch { controller?.enableAhrs(on) }
    fun jog(code: Int) = scope.launch { controller?.jog(code) }

    var gotoAz by mutableStateOf("0.0")
    var gotoAlt by mutableStateOf("0.0")

    // ---- observer location & RA/Dec goto ---------------------------------

    var latDeg by mutableStateOf("51.5")
    var lngEastDeg by mutableStateOf("-0.12")

    /** When true, Slew converts RA/Dec (J2000) to az/alt using location + clock. */
    var raDecMode: Boolean by mutableStateOf(false)
        private set
    var gotoRa by mutableStateOf("05 34 31")   // M42
    var gotoDec by mutableStateOf("-05 27")

    /**
     * Advanced/experimental Alpaca-derived features (dither, settle time,
     * lunar-rate tracking). Off by default until hardware-validated.
     */
    var advancedMode by mutableStateOf(false)

    fun updateLat(v: String) { latDeg = v }
    fun updateLng(v: String) { lngEastDeg = v }
    @JvmName("toggleRaDecMode")
    fun setRaDecMode(on: Boolean) { raDecMode = on }
    fun updateRa(v: String) { gotoRa = v }
    fun updateDec(v: String) { gotoDec = v }

    private fun parsedLocation(): Pair<Double, Double>? {
        val lat = latDeg.toDoubleOrNull() ?: return null
        val lng = lngEastDeg.toDoubleOrNull() ?: return null
        if (lat < -90 || lat > 90) return null
        return lat to lng
    }

    /** Slew to entered coordinates (code 519). Reports result in statusMessage. */
    fun goto() {
        if (raDecMode) {
            val loc = parsedLocation() ?: run { statusMessage = "Invalid latitude/longitude"; return }
            val ra = AstroMath.parseRa(gotoRa) ?: run { statusMessage = "Invalid RA (use HH MM SS or H.h)"; return }
            val dec = AstroMath.parseDec(gotoDec) ?: run { statusMessage = "Invalid Dec (use ±DD MM SS or ±D.d)"; return }
            val altAz = AstroMath.toHorizontalAt(ra, dec, loc.first, loc.second, AstroMath.julianDateNow())
            scope.launch {
                when (controller?.gotoAzAlt(altAz.azimuthDeg, altAz.altitudeDeg)) {
                    null -> statusMessage = "Not connected"
                    else -> statusMessage = "Slewing to RA $gotoRa Dec $gotoDec (az %.1f°, alt %.1f°)"
                        .format(altAz.azimuthDeg, altAz.altitudeDeg)
                }
            }
        } else {
            val az = gotoAz.toDoubleOrNull()
            val alt = gotoAlt.toDoubleOrNull()
            if (az == null || alt == null) {
                statusMessage = "Invalid coordinates"
                return
            }
            scope.launch {
                when (controller?.gotoAzAlt(az, alt)) {
                    null -> statusMessage = "Not connected"
                    else -> statusMessage = "Slewing to az $az°, alt $alt°"
                }
            }
        }
    }

    /** Cancel an in-progress slew (519 state:0). */
    fun cancelSlew() = scope.launch {
        session?.send(dev.openpolaris.core.protocol.Codes.SET_GOTO_AU_STATE, "state:0;")
        statusMessage = "Slew cancelled"
    }

    // ---- alignment ---------------------------------------------------------

    var alignmentStars by mutableStateOf(0)
        private set

    // ---- plate-solve -------------------------------------------------------

    /**
     * Most recent successful plate-solve. Null until the user has
     * pressed "Solve now" and the solver returned a confident
     * match. The pane surfaces RA/Dec / confidence / matched-star
     * count for the operator to decide whether to "Sync to target".
     */
    var lastSolveResult by mutableStateOf<SolveResult?>(null)
        private set

    /** True while [solveNow] is running. Gates the "Solve now" button. */
    var solveInProgress by mutableStateOf(false)
        private set

    /**
     * Plate-solve the current camera frame and, when localized,
     * nudge the mount to centre the currently-entered RA/Dec
     * target. Mirrors the [submitAlignmentStar] / [goto] flow:
     * the work runs on [scope] so the UI stays responsive.
     *
     * The flow is:
     *  1. detect stars in the latest preview JPEG (Android-only;
     *     JVM/Desktop get an empty list from [NullStarDetector]),
     *  2. build a localized [SolveHint] from the current mount
     *     position + observer site,
     *  3. call [solver]; on a confident result, refine the slew
     *     and remember the solved RA/Dec in [lastSolveResult].
     *
     * No-op with a status message when the prerequisites (session,
     * preview frame, location) are missing.
     */
    fun solveNow(
        frameWidth: Int = 1280,
        frameHeight: Int = 960,
        /**
         * Optional Julian Date (UTC) override. When `null`, the call uses
         * [AstroMath.julianDateNow] — i.e. "now". Tests inject a fixed
         * JD so the horizon→equatorial conversion in the solver is
         * reproducible; the production UI does not pass a value.
         */
        jdUtc: Double? = null,
    ) {
        if (solveInProgress) return
        val s = session ?: run { statusMessage = "Not connected"; return }
        val loc = parsedLocation() ?: run { statusMessage = "Set a valid observer location first"; return }
        if (!raDecMode) {
            statusMessage = "Switch to RA/Dec mode to plate-solve"
            return
        }
        val ra = AstroMath.parseRa(gotoRa) ?: run { statusMessage = "Invalid RA"; return }
        val dec = AstroMath.parseDec(gotoDec) ?: run { statusMessage = "Invalid Dec"; return }
        val jpeg = preview.bytes.value
        if (jpeg == null) {
            statusMessage = "No preview frame — wait for the live view"
            return
        }
        solveInProgress = true
        statusMessage = "Solving…"
        scope.launch {
            try {
                val detections = starDetector.detect(jpeg, frameWidth, frameHeight)
                if (detections.isEmpty()) {
                    statusMessage = "No stars detected in preview frame"
                    return@launch
                }
                val result = GoToController(s, controller ?: TrackingController(s)).solveAndRefine(
                    solver = solver,
                    detections = detections,
                    frameWidth = frameWidth,
                    frameHeight = frameHeight,
                    targetRaDeg = ra,
                    targetDecDeg = dec,
                    latDeg = loc.first,
                    lngEastDeg = loc.second,
                    jdUtc = jdUtc ?: AstroMath.julianDateNow(),
                )
                if (result == null) {
                    statusMessage = "Plate-solve failed (no confident match)"
                } else {
                    lastSolveResult = SolveResult(
                        raDeg = result.first,
                        decDeg = result.second,
                        // Confidence and matched-star count aren't returned by
                        // solveAndRefine — they live in the raw solver
                        // result, which solveAndRefine currently drops. The
                        // pane shows "solved" without those fields for now;
                        // a follow-up PR will thread them through. The
                        // placeholder values below are the v1 "good enough"
                        // floor (0.6 confidence, 3-star minimum) so the
                        // SolveResult init contract is satisfied.
                        confidence = 0.6,
                        matchedStars = 3,
                    )
                    statusMessage = "Solved RA %.4f° Dec %.4f° — mount refined to target"
                        .format(result.first, result.second)
                }
            } finally {
                solveInProgress = false
            }
        }
    }

    /**
     * Test seam: install a [MountSession] on this viewmodel without
     * going through [connect] (which would also launch a poll loop and
     * a preview fetch that we don't want interfering with
     * determinism in unit tests).
     */
    internal fun testInstallSession(s: MountSession) {
        this.session = s
    }

    /**
     * Test seam: simulate a prior successful [solveNow] by writing
     * a [SolveResult] into [lastSolveResult]. Used by tests that
     * need to assert [disconnect] clears the cached solve.
     */
    internal fun testSetLastSolve(raDeg: Double, decDeg: Double) {
        lastSolveResult = SolveResult(raDeg, decDeg, 0.6, 3)
    }

    /**
     * Test seam: publish a synthetic JPEG preview frame to the
     * [PreviewController] without having to go through a real
     * camera/feed. Mirrors `PreviewController.publishForTest` from
     * the test module boundary.
     */
    internal fun testSetPreview(jpeg: ByteArray) {
        preview.publishForTest(jpeg)
    }

    /** Record current pointing as alignment star [alignmentStars] (code 530). */
    fun submitAlignmentStar() {
        val s = session ?: run { statusMessage = "Not connected"; return }
        val pos = position ?: run { statusMessage = "No mount position yet"; return }
        val loc = parsedLocation() ?: run { statusMessage = "Set a valid observer location first"; return }
        scope.launch {
            AlignmentController(s).submitStar(pos.yaw.toDouble(), pos.pitch.toDouble(), loc.first, loc.second)
            alignmentStars++
            statusMessage = "Alignment star ${alignmentStars} recorded"
        }
    }

    fun resetAlignment() { alignmentStars = 0; statusMessage = "Alignment reset" }

    // ---- auto-level ----------------------------------------------------------

    var autoLevelEnabled by mutableStateOf<Boolean?>(null)
        private set

    var autoLevelTilt by mutableStateOf<AutoLevelController.Tilt?>(null)
        private set

    var autoLevelRunning by mutableStateOf(false)
        private set

    private fun startAutoLevel(s: MountSession) {
        autoLevelController?.stop()
        val c = AutoLevelController(s)
        autoLevelController = c
        c.start(scope)
        autoLevelJob?.cancel()
        autoLevelJob = scope.launch {
            c.isEnabled.collect { autoLevelEnabled = it }
        }
        autoLevelJob = scope.launch {
            c.tilt.collect { autoLevelTilt = it }
        }
        autoLevelJob = scope.launch {
            c.isRunning.collect { autoLevelRunning = it }
        }
    }

    private fun stopAutoLevel() {
        autoLevelJob?.cancel()
        autoLevelJob = null
        autoLevelController?.stop()
        autoLevelController = null
        autoLevelEnabled = null
        autoLevelTilt = null
        autoLevelRunning = false
    }

    fun refreshAutoLevel() {
        val c = autoLevelController ?: run { statusMessage = "Not connected"; return }
        scope.launch { c.refreshEnabled() }
    }

    fun setAutoLevelEnabled(on: Boolean) = scope.launch {
        val c = autoLevelController ?: run { statusMessage = "Not connected"; return@launch }
        c.setEnabled(on)
        statusMessage = "Auto-level ${if (on) "enabled" else "disabled"}"
    }

    /** Trigger one auto-level cycle (code 549). */
    fun runAutoLevel() = scope.launch {
        val c = autoLevelController ?: run { statusMessage = "Not connected"; return@launch }
        c.run()
        statusMessage = "Auto-level started"
    }

    /** Reset gimbal position reference (code 523). */
    fun resetPosition() = scope.launch {
        session?.send(dev.openpolaris.core.protocol.Codes.POS_RESET)
        statusMessage = "Position reset sent"
    }

    // ---- camera ----------------------------------------------------------
    // Codes are INFERRED (see Codes.kt) — controls stay disabled until validated
    // on hardware. Demo mode exercises the full path.

    var camera by mutableStateOf(dev.openpolaris.core.domain.CameraController.Params())
        private set

    private var cameraController: dev.openpolaris.core.domain.CameraController? = null

    fun refreshCamera() {
        val cc = cameraController ?: run { statusMessage = "Not connected"; return }
        scope.launch {
            val p = dev.openpolaris.core.domain.CameraController.Params(
                isoIndex = cc.queryIso(),
                wbIndex = cc.queryWb(),
                fNumIndex = cc.queryFNum(),
                evIndex = cc.queryEv(),
            )
            camera = p
            statusMessage = if (p.isoIndex != null || p.wbIndex != null ||
                p.fNumIndex != null || p.evIndex != null)
                "Camera parameters refreshed" else "Camera did not respond"
        }
    }

    fun setIso(index: Int) { camera = camera.copy(isoIndex = index); scope.launch { cameraController?.setIso(index) } }
    fun setWb(index: Int) { camera = camera.copy(wbIndex = index); scope.launch { cameraController?.setWb(index) } }
    fun setFNum(index: Int) { camera = camera.copy(fNumIndex = index); scope.launch { cameraController?.setFNum(index) } }
    fun setEv(index: Int) { camera = camera.copy(evIndex = index); scope.launch { cameraController?.setEv(index) } }
    fun capture() = scope.launch {
        if (cameraController == null) { statusMessage = "Not connected"; return@launch }
        cameraController?.capture()
        statusMessage = "Capture sent"
    }

    // ---- astro helpers ----------------------------------------------------
    // Codes 539/540 (dither), 543/544 (settling), 541/542 (limits) are
    // best-effort ports from the Alpaca driver and have not been
    // hardware-validated on every Benro firmware. Only shown when
    // [advancedMode] is on; the panel refreshes after connect.

    private var helpersController: HelpersController? = null

    var ditherEnabled by mutableStateOf<Boolean?>(null)
        private set
    var settlingSeconds by mutableStateOf<Int?>(null)
        private set
    var limitsEnabled by mutableStateOf<Boolean?>(null)
        private set

    /** Local draft for the settling-time text field; committed via [applySettling]. */
    var settlingInput by mutableStateOf("")
        private set

    private fun wireHelpers(s: MountSession) {
        val h = HelpersController(s)
        helpersController = h
        h.start(scope)
        scope.launch {
            h.ditherEnabled.collect { ditherEnabled = it }
        }
        scope.launch {
            h.settlingSeconds.collect {
                settlingSeconds = it
                // Refresh the input draft only when empty or out-of-sync so the
                // user can type freely without us stomping their value.
                if (it != null && (settlingInput.isBlank() || settlingInput.toIntOrNull() != it)) {
                    settlingInput = it.toString()
                }
            }
        }
        scope.launch {
            h.limitsEnabled.collect { limitsEnabled = it }
        }
        scope.launch { h.refreshAll() }
    }

    fun refreshHelpers() {
        val h = helpersController ?: run { statusMessage = "Not connected"; return }
        scope.launch {
            h.refreshAll()
            statusMessage = "Helpers refreshed"
        }
    }

    fun setDither(on: Boolean) {
        // Optimistic local update so the switch feels snappy on real hardware.
        ditherEnabled = on
        scope.launch { helpersController?.setDither(on) }
    }

    fun setLimits(on: Boolean) {
        limitsEnabled = on
        scope.launch { helpersController?.setLimits(on) }
    }

    fun updateSettlingInput(v: String) { settlingInput = v.filter { it.isDigit() }.take(3) }

    fun applySettling() {
        val secs = settlingInput.toIntOrNull() ?: run { statusMessage = "Invalid settling seconds"; return }
        val clamped = secs.coerceIn(0, 99)
        settlingSeconds = clamped
        scope.launch { helpersController?.setSettling(clamped) }
    }
}
