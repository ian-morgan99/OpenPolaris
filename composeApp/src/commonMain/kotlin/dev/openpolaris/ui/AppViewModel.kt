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
import dev.openpolaris.core.session.SessionMarker
import dev.openpolaris.core.session.SessionStore
import dev.openpolaris.core.session.path.defaultSessionPath
import dev.openpolaris.core.solver.NullStarDetector
import dev.openpolaris.core.solver.OnDevicePlateSolver
import dev.openpolaris.core.solver.PlateSolver
import dev.openpolaris.core.solver.SolveHint
import dev.openpolaris.core.solver.SolveResult
import dev.openpolaris.core.solver.StarDetector
import dev.openpolaris.core.solver.SyntheticTestCatalog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * UI-facing view model. Owns the MountSession lifecycle and exposes observable
 * state for Compose. `connectionFactory` is injected so tests and the desktop
 * simulator can substitute a fake connection.
 *
 * [sessionStore] persists the "last connected mount" [SessionMarker] so the
 * next launch can offer an auto-reconnect prompt via [reconnectPrompt].
 * Callers (e.g. `OpenPolarisApp` and tests) supply the store explicitly;
 * production wires `SessionStore(defaultSessionPath())` (JVM:
 * `~/.openpolaris/session.json`, Android: `${filesDir}/openpolaris/session.json`
 * once issue #6's `Context` wiring lands in 3c.4). Tests pass a temp-dir-backed
 * `SessionStore` so the real home directory is never touched.
 */
class AppViewModel(
    private val scope: CoroutineScope,
    private val connectionFactory: () -> Connection,
    private val solver: PlateSolver = OnDevicePlateSolver(SyntheticTestCatalog.asCatalog),
    private val starDetector: StarDetector = NullStarDetector,
    private val sessionStore: SessionStore = SessionStore(defaultSessionPath()),
    // The dispatcher used for session-marker I/O. Tests inject the
    // unconfined test dispatcher so marker reads complete synchronously
    // inside [tryReconnectIfMarkerExists]; production callers use the
    // default Dispatchers.IO to keep the main thread off the filesystem.
    internal val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
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
    private val autoLevelJobs: MutableList<Job> = mutableListOf()
    // The 3 helper-state collectors (dither/settling/limits) launched by
    // [wireHelpers] subscribe to never-completing StateFlows. Track them
    // in a list (mirroring [autoLevelJobs]) so [disconnect] can cancel
    // every one. Without this the test `connectDoesNotWriteMarkerOnFailure`
    // (issue #7 3c.3) sees 3 active child coroutines after the test scope
    // tears down.
    private val helpersJobs: MutableList<Job> = mutableListOf()
    private var pollJob: Job? = null

    // Live preview of the camera MJPEG stream. Independent of the control
    // socket so a slow preview frame can never block the mount poll loop.
    // Decoded JPEGs land in [previewFrame] on Dispatchers.Default.
    val preview = PreviewController(parent = scope.coroutineContext[Job])
    val previewState: StateFlow<PreviewController.State> get() = preview.state
    var previewFrame by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
        private set

    // 3c.3: session-pause UX surface. Non-null while the UI is expected to
    // render the "Reconnect to <host>?" AlertDialog. Populated by
    // [tryReconnectIfMarkerExists] when a valid marker is found; cleared by
    // [acceptReconnect], [dismissReconnect], or [forgetMarker].
    private val _reconnectPrompt = MutableStateFlow<ReconnectPrompt?>(null)
    val reconnectPrompt: StateFlow<ReconnectPrompt?> = _reconnectPrompt.asStateFlow()

    // 3c.5: in-flight indicator. True between [acceptReconnect] and the
    // connect coroutine's final outcome. UI swaps the dialog's action row
    // to a single "Cancel" button while this is true so a hung
    // `s.connect()` (mount powered off) cannot strand the user staring at
    // a non-interactive modal.
    private val _reconnecting = MutableStateFlow(false)
    val reconnecting: StateFlow<Boolean> = _reconnecting.asStateFlow()

    // 3c.5: host-edit buffer for the ReconnectDialog. Populated by
    // [tryReconnectIfMarkerExists] from the saved marker, and read by
    // [acceptReconnect] so the user can override the saved host without
    // first dismissing the dialog. A non-blank [draftHost] also
    // short-circuits [tryReconnectIfMarkerExists] so a re-prompt
    // (MainActivity.onResume) cannot clobber the user's edit.
    private val _draftHost = MutableStateFlow("")
    val draftHost: StateFlow<String> = _draftHost.asStateFlow()

    fun updateDraftHost(h: String) { _draftHost.value = h }

    // 3c.5: handle to the in-flight connect coroutine so [cancelReconnect]
    // can interrupt a hung `s.connect()`. Null between connects. Set by
    // [connect] immediately before launching.
    private var connectJob: Job? = null

    /**
     * Wall-clock provider for the marker-age calculation. Overridable from
     * tests so `ReconnectPrompt.ageMs` is deterministic regardless of the
     * real system clock. Production callers should not touch this.
     */
    internal var nowMs: () -> Long = { dev.openpolaris.core.domain.currentEpochMillis() }

    fun updateHost(h: String) { host = h }

    /**
     * Read the persisted [SessionMarker] (if any) and, if valid, set
     * [reconnectPrompt] so the UI can show the "Reconnect?" dialog. Called
     * once at startup from `MainActivity.onResume` (3c.4). No-op when the
     * store has no usable marker, or when [reconnectPrompt] is already set
     * (e.g. the user dismissed it and we do not want to re-prompt this
     * launch). All I/O is on [Dispatchers.IO] so the calling coroutine
     * (typically the main dispatcher) is not blocked.
     */
    fun tryReconnectIfMarkerExists() {
        scope.launch {
            val marker = withContext(ioDispatcher) { sessionStore.read() }
            if (marker == null) return@launch
            // Re-check inside the launched coroutine: the user may have
            // dismissed the prompt between the call site and here.
            if (_reconnectPrompt.value != null) return@launch
            // 3c.5: a non-blank draftHost means the user has started
            // editing the saved host on this surface. Do not clobber
            // their edit with a re-prompt driven by onResume. The next
            // time the app is *freshly* launched (cold start) the
            // draftHost is empty and the prompt returns as normal.
            if (_draftHost.value.isNotBlank()) return@launch
            _reconnectPrompt.value = ReconnectPrompt(
                host = marker.host,
                port = marker.port,
                mountMode = marker.lastMountMode,
                trackingStarted = marker.lastTrackingStarted,
                ageMs = (nowMs() - marker.lastConnectedAtEpochMs).coerceAtLeast(0L),
                lastRollDeg = marker.lastRollDeg,
                lastPitchDeg = marker.lastPitchDeg,
            )
            // Seed the host-edit field with the saved host so the
            // OutlinedTextField in the dialog has something to show.
            _draftHost.value = marker.host
        }
    }

    /**
     * User accepted the prompt. Sets [host] to the persisted host (or the
     * user's edited [draftHost] if non-blank) and calls [connect] using the
     * persisted port. Clears the prompt synchronously so the dialog closes
     * before the connection attempt begins. Sets [reconnecting] true so the
     * (now-closed) dialog swap-in spinner and the dedicated "Cancel" path
     * become active. A successful connect will overwrite the marker with
     * fresh state; a cancelled connect leaves the marker untouched so the
     * next resume still offers the same prompt.
     */
    fun acceptReconnect() {
        val prompt = _reconnectPrompt.value ?: return
        val targetHost = _draftHost.value.trim().ifBlank { prompt.host }
        _reconnectPrompt.value = null
        host = targetHost
        // If the user typed a different host, the existing marker is now
        // stale. Persist a fresh one BEFORE launching connect so a
        // successful connect-time saveMarker() does not race with a
        // user-visible "still pointing at the old host" prompt on the
        // next launch.
        if (targetHost != prompt.host) {
            val pos = position
            sessionStore.write(
                SessionMarker(
                    host = targetHost,
                    port = prompt.port,
                    lastConnectedAtEpochMs = nowMs(),
                    lastMountMode = prompt.mountMode,
                    lastTrackingStarted = prompt.trackingStarted,
                    lastRollDeg = pos?.roll?.toDouble() ?: 0.0,
                    lastPitchDeg = pos?.pitch?.toDouble() ?: 0.0,
                ),
            )
        }
        _reconnecting.value = true
        connect()
    }

    /**
     * User cancelled an in-flight reconnect (the dialog's single "Cancel"
     * action while [reconnecting] is true). Cancels the connect coroutine
     * and clears the in-flight flag. Does NOT clear the prompt or the
     * marker — the prompt is already gone (we cleared it on accept); the
     * marker stays so the next launch can offer the same prompt.
     *
     * Idempotent: safe to call when no connect is in flight (no-op).
     */
    fun cancelReconnect() {
        if (!_reconnecting.value) return
        connectJob?.cancel()
        _reconnecting.value = false
        statusMessage = "Reconnect cancelled"
    }

    /**
     * User dismissed the prompt ("Different mount"). Clears the prompt for
     * this launch but does NOT delete the marker file — the prompt will
     * return on the next launch until the user accepts, or explicitly
     * chooses "Forget this mount" via [forgetMarker]. If the user edited
     * the host field before dismissing, the edit is preserved in
     * [draftHost] so the next resume's [tryReconnectIfMarkerExists] does
     * not clobber it.
     */
    fun dismissReconnect() {
        _reconnectPrompt.value = null
    }

    /**
     * Permanently forget the persisted mount. Called from a settings
     * "Forget this mount" action. After this, [tryReconnectIfMarkerExists]
     * will be a no-op until a new [connect] succeeds and writes a new
     * marker. Surfaces the result in [statusMessage].
     */
    fun forgetMarker() {
        scope.launch {
            val removed = withContext(ioDispatcher) { sessionStore.forget() }
            _reconnectPrompt.value = null
            // 3c.5: clear the host-edit buffer so the next launch (or
            // the next successful connect that writes a new marker) can
            // re-seed it from the fresh marker's host.
            _draftHost.value = ""
            statusMessage = if (removed) "Forgot saved mount" else "No saved mount to forget"
        }
    }

    /**
     * Persist a fresh [SessionMarker] for the just-connected mount. Called
     * from inside [connect] / [connectDemo] on a successful `s.connect()`,
     * capturing the mode + tracking + tilt at that moment. A future slice
     * could re-write the marker on every state change (so a long-running
     * session's marker reflects the final mode), but v1 captures the
     * connect-time state which is the minimum the reconnect prompt needs.
     *
     * If [position] is still null at connect time (a real race for the first
     * connect, since 517 is the second poll) we record 0.0/0.0 — the
     * "freshest known" value. The next `connect` will overwrite this once
     * the first 517 lands.
     */
    private fun saveMarker() {
        val pos = position
        val marker = SessionMarker(
            host = host,
            port = 9090,
            lastConnectedAtEpochMs = nowMs(),
            lastMountMode = mount.mode,
            lastTrackingStarted = mount.tracking == true,
            lastRollDeg = pos?.roll?.toDouble() ?: 0.0,
            lastPitchDeg = pos?.pitch?.toDouble() ?: 0.0,
        )
        scope.launch {
            val result = withContext(ioDispatcher) { sessionStore.write(marker) }
            if (result.isFailure) {
                // Best-effort: a failed write does not break the live
                // session, but the user should know the reconnect prompt
                // will not appear next launch.
                statusMessage = "Connected (could not save session: ${result.exceptionOrNull()?.message ?: "unknown"})"
            }
        }
    }

    fun connect() {
        disconnect()
        demoMode = false
        val s = MountSession(connectionFactory, host, 9090)
        session = s
        controller = TrackingController(s)
        cameraController = dev.openpolaris.core.domain.CameraController(s)
        wireHelpers(s)
        startAutoLevel(s)
        // 3c.5: capture the launched coroutine so [cancelReconnect] can
        // interrupt a hung `s.connect()` (mount powered off, link down).
        // Also reset the in-flight flag in the completion path so the
        // spinner collapses to a status message either way.
        connectJob = scope.launch {
            statusMessage = "Connecting to $host…"
            if (s.connect()) {
                statusMessage = "Connected"
                _reconnecting.value = false
                saveMarker()
                startPolling(s)
                startPreview()
            } else {
                statusMessage = "Could not reach $host — try Demo mode"
                _reconnecting.value = false
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
            // SimulatedMount's connect() always returns true; treat as
            // success for the marker-save path. (A future slice that
            // simulates intermittent failure should gate this on the
            // return value the same way `connect()` does.)
            saveMarker()
            statusMessage = "Demo mode (simulated mount)"
            startPolling(sim.session)
            // No preview in demo mode: there is no MJPEG endpoint in the
            // simulator. PreviewController stays Idle, which the pane
            // renders as "Stream unavailable".
        }
    }

    fun disconnect() {
        pollJob?.cancel()
        // 3c.5: if a reconnect was in flight, tear it down too so the
        // spinner does not stay up after the user navigates away.
        connectJob?.cancel()
        _reconnecting.value = false
        stopAutoLevel()
        cancelHelpersJobs()
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
                    // If the mount dropped mid-solve, surface that rather
                    // than the generic "no confident match" — users were
                    // being told the *solver* failed when the real cause
                    // was a broken link. See PLAN-CRITICAL-REVIEW §H.
                    val mountErr = s.lastError
                    statusMessage = if (mountErr is MountSession.CmdResult.ProtocolError) {
                        "Plate-solve failed (mount error: ${mountErr.message})"
                    } else {
                        "Plate-solve failed (no confident match)"
                    }
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
        // Wire the AutoLevel controller's settling loop to the session's
        // non-conflating tilt push stream (issue #6). The default sample
        // source reads from a StateFlow that conflates identical samples
        // and drops intermediate ones — fatal for AHRS settling, which
        // needs every 538 push in arrival order.
        val pushSource = dev.openpolaris.core.domain.MountSessionTiltSampleSource(s)
        val sampleSource: suspend () -> dev.openpolaris.core.domain.AutoLevelController.Tilt? = {
            pushSource.next()?.let {
                dev.openpolaris.core.domain.AutoLevelController.Tilt(
                    pitchDeg = it.pitchDeg,
                    rollDeg = it.rollDeg,
                )
            }
        }
        val c = AutoLevelController(s, sampleSource)
        autoLevelController = c
        c.start(scope)
        // Track all collector jobs in a list (NOT a single field) — three
        // collectors are launched and every one must be cancellable from
        // [stopAutoLevel]. A single `Job?` reference would be overwritten
        // three times and orphan the first two (see PR for issue #7 3c.3).
        cancelAutoLevelJobs()
        autoLevelJobs += scope.launch {
            c.isEnabled.collect { autoLevelEnabled = it }
        }
        autoLevelJobs += scope.launch {
            c.tilt.collect { autoLevelTilt = it }
        }
        autoLevelJobs += scope.launch {
            c.isRunning.collect { autoLevelRunning = it }
        }
    }

    private fun cancelAutoLevelJobs() {
        for (j in autoLevelJobs) j.cancel()
        autoLevelJobs.clear()
    }

    private fun cancelHelpersJobs() {
        for (j in helpersJobs) j.cancel()
        helpersJobs.clear()
    }

    private fun stopAutoLevel() {
        cancelAutoLevelJobs()
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

    /** Trigger one auto-level cycle (code 549) and surface the settling result. */
    fun runAutoLevel() = scope.launch {
        val c = autoLevelController ?: run { statusMessage = "Not connected"; return@launch }
        statusMessage = "Auto-level started…"
        val result = c.runAndAwait()
        statusMessage = when (result) {
            is dev.openpolaris.core.domain.AutoLevelController.AutoLevelResult.Completed ->
                "Auto-level settled at roll=${"%.3f".format(result.rollDeg)}°, pitch=${"%.3f".format(result.pitchDeg)}°"
            is dev.openpolaris.core.domain.AutoLevelController.AutoLevelResult.Failed ->
                "Auto-level failed: ${result.reason}"
            dev.openpolaris.core.domain.AutoLevelController.AutoLevelResult.TimedOut ->
                "Auto-level timed out before settling"
        }
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
        helpersJobs += scope.launch {
            h.ditherEnabled.collect { ditherEnabled = it }
        }
        helpersJobs += scope.launch {
            h.settlingSeconds.collect {
                settlingSeconds = it
                // Refresh the input draft only when empty or out-of-sync so the
                // user can type freely without us stomping their value.
                if (it != null && (settlingInput.isBlank() || settlingInput.toIntOrNull() != it)) {
                    settlingInput = it.toString()
                }
            }
        }
        helpersJobs += scope.launch {
            h.limitsEnabled.collect { limitsEnabled = it }
        }
        // refreshAll() completes on its own (one-shot request) so it does
        // not need to be tracked.
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
