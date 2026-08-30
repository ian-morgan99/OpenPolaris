package dev.openpolaris.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.openpolaris.core.domain.AlignmentController
import dev.openpolaris.core.domain.AstroMath
import dev.openpolaris.core.domain.BatteryDetail
import dev.openpolaris.core.domain.CameraInfo
import dev.openpolaris.core.domain.Connection
import dev.openpolaris.core.domain.ExAxisState
import dev.openpolaris.core.domain.GimbalPosition
import dev.openpolaris.core.domain.MountMode
import dev.openpolaris.core.domain.MountSession
import dev.openpolaris.core.domain.MountState
import dev.openpolaris.core.domain.OmsState
import dev.openpolaris.core.domain.SdStatus
import dev.openpolaris.core.domain.TrackingController
import dev.openpolaris.core.protocol.CommandTable
import dev.openpolaris.core.protocol.ResponseParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * UI-facing view model. Owns the MountSession lifecycle and exposes observable
 * state for Compose. `connectionFactory` is injected so tests and the desktop
 * simulator can substitute a fake connection.
 */
class AppViewModel(
    private val scope: CoroutineScope,
    private val connectionFactory: () -> Connection,
    /**
     * Bridge the segregated Wi-Fi interface to the gimbal. The lambda receives
     * a `progress: (String) -> Unit` callback; it is called from a background
     * dispatcher and must be safe to invoke off the main thread. The default
     * is a no-op so callers that don't have a bridge implementation (e.g.
     * the Android build) can construct the VM without it.
     */
    private val connectWifi: (suspend (String) -> Unit) -> Unit = {},
) {
    var host by mutableStateOf("192.168.0.1")
        private set

    var mount by mutableStateOf(MountState())
        private set

    var position by mutableStateOf<GimbalPosition?>(null)
        private set

    var statusMessage by mutableStateOf("Disconnected")
        private set

    var demoMode by mutableStateOf(false)
        private set

    /**
     * One-shot post-connect burst (524, 544, 802, 824, 775, 778, 779 + get 543
     * + 808/809).  See `docs/PLANNING-2026-08.md` Step 5.  Each field stays
     * null until the matching code's response (or timeout) is observed.
     */
    var firmwareVersion by mutableStateOf<String?>(null)
        private set
    var serialNumber by mutableStateOf<String?>(null)
        private set
    var wifiBand by mutableStateOf<Int?>(null)
        private set
    var batteryDetail by mutableStateOf<BatteryDetail?>(null)
        private set
    var sdStatus by mutableStateOf<SdStatus?>(null)
        private set
    var omsState by mutableStateOf<OmsState?>(null)
        private set
    var settlingTime by mutableStateOf<Int?>(null)
        private set
    var exAxisState by mutableStateOf<Int?>(null)
        private set
    var cameraInfo by mutableStateOf<CameraInfo?>(null)
        private set

    /**
     * Live capture pipeline state (code 266). Polled on a separate 2s cadence
     * from the main 1Hz pose poll so that a slow/no-response 266 doesn't stall
     * the 284/517 update loop. `state==1` means a shot is in progress (bulb
     * exposure, processing, etc.) and the UI should grey out the Capture
     * button to prevent stacking exposures. `state==0` is idle. `c` is a
     * firmware-defined counter (typically the remaining shots in a burst).
     * See docs/PLANNING-2026-08.md Step 7.
     */
    var captureState by mutableStateOf<CommandTable.CaptureState?>(null)
        private set

    private var session: MountSession? = null
    private var controller: TrackingController? = null
    private var pollJob: Job? = null
    private var capturePollJob: Job? = null

    fun updateHost(h: String) { host = h }

    fun connect() {
        disconnect()
        demoMode = false
        val s = MountSession(connectionFactory, host, 9090)
        session = s
        controller = TrackingController(s)
        cameraController = dev.openpolaris.core.domain.CameraController(s)
        scope.launch {
            statusMessage = "Connecting to $host…"
            if (s.connect()) {
                statusMessage = "Connected"
                postConnectBurst(s)
                startPolling(s)
                startCapturePolling(s)
            } else {
                statusMessage = "Could not reach $host — try Demo mode"
            }
        }
    }

    /**
     * Bring up the segregated Wi-Fi bridge (BT wake → NM up → link up →
     * policy route). Each phase posts to [statusMessage] as it runs.
     */
    fun connectWifi() {
        scope.launch {
            statusMessage = "Connecting to mount Wi-Fi…"
            connectWifi { msg -> statusMessage = msg }
            statusMessage = "Mount Wi-Fi phase complete — try Connect"
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
        scope.launch {
            sim.session.connect()
            statusMessage = "Demo mode (simulated mount)"
            startPolling(sim.session)
            startCapturePolling(sim.session)
        }
    }

    fun disconnect() {
        pollJob?.cancel()
        capturePollJob?.cancel()
        session?.disconnect()
        session = null
        controller = null
        cameraController = null
        mount = MountState()
        position = null
        firmwareVersion = null
        serialNumber = null
        wifiBand = null
        batteryDetail = null
        sdStatus = null
        omsState = null
        settlingTime = null
        exAxisState = null
        cameraInfo = null
        captureState = null
        if (!demoMode) statusMessage = "Disconnected"
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

    /**
     * Periodic 2s poll for code 266 (CAM_GET_STATE). Kept off the main 1Hz
     * pose poll so a slow/missing 266 reply (older firmware or hardware
     * timeout) cannot stall 284/517. MountSession.request serialises through
     * a single Mutex, so captureState updates will interleave with the pose
     * poll but never overlap. The parser returns null if `state` is absent;
     * we preserve the last good value in that case so a single missed
     * response doesn't visually reset the Capture button.
     */
    private fun startCapturePolling(s: MountSession) {
        capturePollJob?.cancel()
        capturePollJob = scope.launch {
            while (isActive) {
                when (val r = s.request(dev.openpolaris.core.protocol.Codes.CAM_GET_STATE) { CommandTable.CAM_GET_STATE.parse!!(it) }) {
                    is MountSession.CmdResult.Ok -> r.value?.let { captureState = it }
                    else -> {} // Timeout / ProtocolError: keep last good captureState
                }
                delay(2000)
            }
        }
    }

    // ---- post-connect burst --------------------------------------------
    //
    // After a successful TCP connect the gimbal is silent until we ask for
    // things.  We fire off the codes that surface on the Info / Battery / About
    // screens: firmware, serial, wifi band, battery (status+detail), SD card,
    // OMS run state, ex-axis state, settling time.  Each is independent — a
    // timeout on one (older firmware may not implement it) must not block the
    // others.
    //
    // Order matches `tools/cli-probe/.../Burst.kt` so the simulator and the
    // live device see the same traffic.  543 is a GET for settling time and is
    // appended after 544 (the SETTER used by the demo).  In a real connection
    // only 543 fires.
    //
    // See docs/PLANNING-2026-08.md Step 5.

    private suspend fun postConnectBurst(s: MountSession) {
        for (step in CommandTable.BURST_PRE_CAMERA) {
            runCatching {
                @Suppress("UNCHECKED_CAST")
                val r = s.request(step.code, parse = step.parse as (ResponseParser.Frame) -> Any?)
                if (r is MountSession.CmdResult.Ok) applyBurstValue(step.code, r.value)
            }
        }

        // Camera parameter burst (10 GETs). Each merges one field into the
        // running CameraInfo snapshot. Codes 266 (STATE) and 267 (CAPTURE) are
        // NOT part of this — they feed the CaptureState pipeline / capture button.
        runCatching {
            var snapshot: CameraInfo = cameraInfo ?: CameraInfo()
            for (c in CommandTable.BURST_CAMERA_CODES) {
                val r = s.request<ResponseParser.Frame>(c) { it }
                if (r is MountSession.CmdResult.Ok) {
                    snapshot = CameraInfo.fromFrame(c, r.value, snapshot)
                }
            }
            cameraInfo = snapshot
        }
    }

    /**
     * Dispatch a single parsed pre-camera burst value to the right observable.
     * Centralised here so the 8 `refresh*()` methods and the burst share one
     * code-to-field mapping.
     */
    private fun applyBurstValue(code: Int, value: Any) {
        when (code) {
            808 -> firmwareVersion = value as String
            809 -> serialNumber = value as String
            802 -> wifiBand = value as Int
            778, 779 -> batteryDetail = value as BatteryDetail
            775 -> sdStatus = value as SdStatus
            824 -> omsState = value as OmsState
            524 -> exAxisState = (value as ExAxisState).state
            543 -> settlingTime = value as Int
        }
    }

    /** Re-fire a single code from the post-connect burst on demand. */
    fun refreshFirmware()   = refreshBurstStep(808)
    fun refreshSerial()     = refreshBurstStep(809)
    fun refreshWifiBand()   = refreshBurstStep(802)
    fun refreshBattery()    { refreshBurstStep(778); refreshBurstStep(779) }
    fun refreshSdStatus()   = refreshBurstStep(775)
    fun refreshOmsState()   = refreshBurstStep(824)
    fun refreshExAxis()     = refreshBurstStep(524)
    fun refreshSettling()   = refreshBurstStep(543)

    private fun refreshBurstStep(code: Int) = scope.launch {
        val s = session ?: return@launch
        val step = CommandTable.BURST_PRE_CAMERA.firstOrNull { it.code == code } ?: return@launch
        runCatching {
            @Suppress("UNCHECKED_CAST")
            val r = s.request(step.code, parse = step.parse as (ResponseParser.Frame) -> Any?)
            if (r is MountSession.CmdResult.Ok) applyBurstValue(code, r.value)
        }
    }

    fun setSettlingTimeMs(ms: Int) = scope.launch {
        val s = session ?: return@launch
        // 544 is a SETTER with no echo on the real device.  Use Int parser so
        // `matched!!` succeeds; result is discarded.
        s.request<Int>(544, "time:$ms;") { 0 }
        // Re-read to confirm.
        (s.request<Int>(543) { it.int("time") } as? MountSession.CmdResult.Ok)?.let { settlingTime = it.value }
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

    fun refreshAutoLevel() {
        val s = session ?: run { statusMessage = "Not connected"; return }
        scope.launch {
            autoLevelEnabled = when (val r = s.request(CommandTable.AUTO_LEVEL_GET_EN.code) { f ->
                f.int("en")
            }) {
                is MountSession.CmdResult.Ok -> r.value == 1
                else -> null
            }
        }
    }

    fun setAutoLevelEnabled(on: Boolean) = scope.launch {
        session?.send(CommandTable.AUTO_LEVEL_SET_EN.code, CommandTable.AUTO_LEVEL_SET_EN.payload(on))
        autoLevelEnabled = on
        statusMessage = "Auto-level ${if (on) "enabled" else "disabled"}"
    }

    /** Trigger one auto-level cycle (code 549). */
    fun runAutoLevel() = scope.launch {
        session?.send(CommandTable.AUTO_LEVEL_TRIGGER.code)
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
                focusIndex = cc.queryFocus(),
                imgSizeIndex = cc.queryImgSize(),
                imgFmtIndex = cc.queryImgFmt(),
                colorIndex = cc.queryColor(),
                shutterIndex = cc.queryShutter(),
                captureModeIndex = cc.queryCaptureMode(),
            )
            camera = p
            statusMessage = if (p.isoIndex != null || p.wbIndex != null ||
                p.fNumIndex != null || p.evIndex != null || p.focusIndex != null ||
                p.imgSizeIndex != null || p.imgFmtIndex != null || p.colorIndex != null ||
                p.shutterIndex != null || p.captureModeIndex != null)
                "Camera parameters refreshed" else "Camera did not respond"
        }
    }

    fun setFocus(index: Int) { camera = camera.copy(focusIndex = index); scope.launch { cameraController?.setFocus(index) } }
    fun setImgSize(index: Int) { camera = camera.copy(imgSizeIndex = index); scope.launch { cameraController?.setImgSize(index) } }
    fun setImgFmt(index: Int) { camera = camera.copy(imgFmtIndex = index); scope.launch { cameraController?.setImgFmt(index) } }
    fun setColor(index: Int) { camera = camera.copy(colorIndex = index); scope.launch { cameraController?.setColor(index) } }
    fun setShutter(index: Int) { camera = camera.copy(shutterIndex = index); scope.launch { cameraController?.setShutter(index) } }
    fun setCaptureMode(index: Int) { camera = camera.copy(captureModeIndex = index); scope.launch { cameraController?.setCaptureMode(index) } }

    fun setIso(index: Int) { camera = camera.copy(isoIndex = index); scope.launch { cameraController?.setIso(index) } }
    fun setWb(index: Int) { camera = camera.copy(wbIndex = index); scope.launch { cameraController?.setWb(index) } }
    fun setFNum(index: Int) { camera = camera.copy(fNumIndex = index); scope.launch { cameraController?.setFNum(index) } }
    fun setEv(index: Int) { camera = camera.copy(evIndex = index); scope.launch { cameraController?.setEv(index) } }
    fun capture() = scope.launch {
        if (cameraController == null) { statusMessage = "Not connected"; return@launch }
        cameraController?.capture()
        statusMessage = "Capture sent"
    }
}
