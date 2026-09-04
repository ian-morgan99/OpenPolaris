package dev.openpolaris.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.openpolaris.core.config.FeatureFlags
import dev.openpolaris.core.domain.AlignmentController
import dev.openpolaris.core.domain.AuthConfig
import dev.openpolaris.core.domain.AutoLevelController
import dev.openpolaris.core.domain.MountSessionTiltSampleSource
import dev.openpolaris.core.astro.AstroMath
import dev.openpolaris.core.astro.Catalog
import dev.openpolaris.core.astro.CometOrbitalElements
import dev.openpolaris.core.astro.CometShardLoader
import dev.openpolaris.core.astro.EmbeddedCatalog
import dev.openpolaris.core.astro.ObjectType
import dev.openpolaris.core.domain.BatteryDetail
import dev.openpolaris.core.domain.CameraInfo
import dev.openpolaris.core.domain.CameraProfile
import dev.openpolaris.core.domain.CameraProfileSource
import dev.openpolaris.core.domain.Connection
import dev.openpolaris.core.domain.DeviceInfo
import dev.openpolaris.core.domain.ExAxisState
import dev.openpolaris.core.domain.FileEntry
import dev.openpolaris.core.domain.FileList
import dev.openpolaris.core.domain.FirmwareUpdateController
import dev.openpolaris.core.domain.DeliveryMode
import dev.openpolaris.core.domain.FirmwareDelivery
import dev.openpolaris.core.domain.NoOpFirmwareDelivery
import dev.openpolaris.core.domain.platformFirmwareDelivery
import dev.openpolaris.core.domain.GimbalPosition
import dev.openpolaris.core.io.FilePicker
import dev.openpolaris.core.io.PickerBridge
import dev.openpolaris.core.session.PlatformFile
import dev.openpolaris.core.domain.GoToController
import dev.openpolaris.core.domain.HelpersController
import dev.openpolaris.core.domain.CameraController
import dev.openpolaris.core.domain.MarkerStateBus
import dev.openpolaris.core.domain.MountMode
import dev.openpolaris.core.domain.MountSession
import dev.openpolaris.core.domain.MountState
import dev.openpolaris.core.domain.OmsState
import dev.openpolaris.core.domain.SdStatus
import dev.openpolaris.core.domain.TaskList
import dev.openpolaris.core.domain.Temperature
import dev.openpolaris.core.domain.PreviewController
import dev.openpolaris.core.domain.TrackingController
import dev.openpolaris.core.domain.readResourceText
import dev.openpolaris.core.protocol.CommandTable
import dev.openpolaris.core.protocol.Codes
import dev.openpolaris.core.protocol.ResponseParser
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
import dev.openpolaris.core.util.Md5
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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
    /**
     * Bridge the segregated Wi-Fi interface to the gimbal. The lambda receives
     * a `progress: (String) -> Unit` callback; it is called from a background
     * dispatcher and must be safe to invoke off the main thread. The default
     * is a no-op so callers that don't have a bridge implementation (e.g.
     * the Android build) can construct the VM without it.
     */
    private val connectWifi: suspend (suspend (String) -> Unit) -> Unit = {},
    /**
     * BT-only wake pulse for the gimbal. Distinct from [connectWifi] (which
     * does wake → NM up → link → policy route in one shot). The lambda
     * receives a `progress: (String) -> Unit` callback it calls from a
     * background dispatcher; the default is a no-op so callers without a
     * BT stack (e.g. the Android build) can construct the VM without it.
     * The [ConnectionPane] "Wake" button is shown when this is wired.
     */
    private val wakeProbe: suspend (suspend (String) -> Unit) -> Unit = {},
    private val solver: PlateSolver = OnDevicePlateSolver(SyntheticTestCatalog.asCatalog),
    private val starDetector: StarDetector = NullStarDetector,
    private val sessionStore: SessionStore = SessionStore(defaultSessionPath()),
    // The dispatcher used for session-marker I/O. Tests inject the
    // unconfined test dispatcher so marker reads complete synchronously
    // inside [tryReconnectIfMarkerExists]; production callers use the
    // default Dispatchers.IO to keep the main thread off the filesystem.
    internal val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    // The scope MountSession uses to run its reader loop. Tests inject
    // the runTest scope so virtual time advances the reader's blocking
    // `read` call the same way it advances the waiter; production
    // passes null to keep MountSession's default Dispatchers.Default
    // scope. Without a test-supplied scope, the reader runs on a
    // real-time dispatcher and `request()`'s virtual-time
    // `withTimeout(2000)` fires before the reader's real-time channel
    // receive delivers the response.
    private val sessionReaderScope: CoroutineScope? = null,
) {
    // 3d: default host is the Polaris AP (192.168.0.1) when the user's
    // phone is joined to the mount's WiFi network. The pre-3d default
    // (192.168.43.1) is the Android USB-tethered gateway — useful in
    // dev rigs that tether the PC to a phone's hotspot, but wrong as a
    // first-class default for a real Polaris. The host is only a
    // starting value anyway — once a successful connect writes a
    // [SessionMarker], the next launch offers a reconnect prompt
    // pre-filled with the actual host, so the default rarely matters
    // beyond the very first connect.

    var host by mutableStateOf("192.168.0.1")
        private set

    // 3b.5-BUG: live (committed) port, read by MountSession() and the
    // persisted marker. Defaults to 9090 to match prior hard-coded
    // behaviour; settable through [updatePort] from the UI.
    var port by mutableStateOf(9090)
        private set

    fun updatePort(p: Int) { port = p }

    // App-handshake password. Defaults to null (most production gimbal firmware
    // doesn't require a password, so the 820→821 sequence is skipped). When
    // the gimbal responds to 820 with `needed:1` but the user hasn't supplied
    // a password, connect() fails and the IOException message is surfaced via
    // [statusMessage]; the UI should observe [needsPassword] and prompt the
    // user, then call [setPassword] and retry.
    //
    // The password is held in-memory only — it is never persisted to
    // SessionMarker or any other on-disk file. The Android app is expected to
    // keep it in EncryptedSharedPreferences (or the JVM/Desktop build keeps
    // it for the lifetime of the process).
    var password by mutableStateOf<String?>(null)
        private set

    // True after a connect attempt where the gimbal reported `needed:1` but
    // no password was configured. Reset on the next connect() / setPassword().
    var needsPassword by mutableStateOf(false)
        private set

    fun setConnectionPassword(value: String?) {
        password = value
        needsPassword = false
    }

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

    /** 780 — device info (hardware/software/serverVersion). Read-only,
     *  populated by the post-connect burst. */
    var deviceInfo by mutableStateOf<DeviceInfo?>(null)
        private set

    /** 525 — raw temperature/IMU read. Wire format is
     *  `525@Tempa509ca361e0000275a ;#` (a hex blob, decoding is TODO). We
     *  surface the raw hex so the user can see the value the mount reports. */
    var temperature by mutableStateOf<Temperature?>(null)
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

    /**
     * Live status of the firmware-update flow (FirmwareUpdateController). Null
     * when no upload has been attempted yet in this session. Surfaced by
     * FirmwarePane as a progress bar / status line.
     */
    var firmwareStatus by mutableStateOf<FirmwareUpdateController.Status?>(null)
        private set

    /**
     * The firmware file the user just picked, surfaced in the FirmwarePane
     * before they hit "Upload". Null when nothing has been picked yet, or
     * after a successful upload (so the pane resets to its clean state).
     * We hold the absolute path rather than the bytes: the bytes can be
     * many MB and we don't want them sitting in Compose state.
     */
    var pickedFirmwarePath by mutableStateOf<String?>(null)
        private set

    /** Display name of the picked firmware file, taken from the path's basename. */
    var pickedFirmwareName by mutableStateOf<String?>(null)
        private set

    /** Bytes of the picked firmware file, lazily read by the upload button. */
    var pickedFirmwareSize by mutableStateOf<Long?>(null)
        private set

    /**
     * Lower-case hex MD5 of the picked firmware file, computed at upload
     * time (NOT at pick time — multi-MB files would be wasteful to read
     * twice, and the upload is the first time we actually load the bytes).
     * Surfaced in the FirmwarePane so the user can sanity-check the file
     * against an external source. Phase 1a #2 of
     * docs/FIRMWARE-UPLOAD-AUDIT-2026-09-01.md.
     */
    var pickedFirmwareMd5 by mutableStateOf<String?>(null)
        private set

    /**
     * Expected MD5 of the firmware bundle, supplied by the user (typically
     * pasted from the Benro web console). When non-blank, the controller
     * cross-checks this against the locally-computed MD5 and refuses to
     * touch the wire if they disagree. Mirrors the Benro Connect flow
     * where the user is expected to verify the bundle's hash before upload.
     */
    var firmwareExpectedMd5 by mutableStateOf("")

    /** Whether the firmware upload is currently in flight. Drives UI gating. */
    var firmwareBusy by mutableStateOf(false)
        private set

    /** Whether the user wants the mount to reboot after a successful install. */
    var firmwareRebootAfter by mutableStateOf(false)

    /**
     * Which delivery transport the firmware upload uses. The default
     * [DeliveryMode.SSH_PIPE] is the verified path — bytes are
     * scp'd to `/app/sd/FwPkt.zip` and the on-board
     * `SP_UpgradeCheckFw` watcher takes over after the user reboots
     * the gimbal. The alternate [DeliveryMode.WIRE] drives the
     * 810/784/794/795/811/812 envelope through the binary control
     * plane; that sequence is reconstructed from the Benro Connect
     * Android decompile but **has not been observed in a live
     * Benro Connect traffic capture as of 2026-08-31**, so the
     * chunk payload slot (`len:N;`) is a placeholder. See
     * [FirmwareUpdateController] class KDoc for the full
     * verification status of each path.
     *
     * The default lives in the VM (not in the KDoc) so a power user
     * can flip the radio button in the FirmwarePane without editing
     * source. The runtime change does not persist across restarts.
     */
    var firmwareDeliveryMode by mutableStateOf(DeliveryMode.SSH_PIPE)

    /**
     * The host the gimbal's AP exposes. Used by [DeliveryMode.SSH_PIPE]
     * to construct an `ssh root@<host>` command. Defaults to
     * `192.168.2.1` (the Benro Polaris AP default) and is editable
     * in the FirmwarePane so the user can override for a custom
     * network.
     */
    var firmwareSshHost by mutableStateOf("192.168.2.1")

    // ---- session persistence + reconnect prompt (issue #27) ----------------

    // [pendingReconnectMarker] / [lastSlewMarkerId] / [confirmReconnect] /
    // [maybeOfferReconnect] / [saveCurrentTarget] were part of the OURS'
    // "Return to last celestial target" feature merged alongside THEIRS'
    // "Reconnect to last host" feature. The OURS' feature referenced a
    // TargetSessionMarker store that was never wired in (only the
    // SessionStore<connection> exists), so the merged code referenced
    // methods that don't exist. Removed in favour of THEIRS' feature for
    // now; the target-marker feature can be re-added against a real
    // TargetStore later.

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
    private var capturePollJob: Job? = null
    // The simulated mount (only set in demo mode). Held so disconnect()
    // can cancel its private reader scope.
    private var demoSim: SimulatedMount? = null

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

    // 7.5: separate in-flight flag for the BT-only Wake button so the
    // user can wake the gimbal *before* pressing Connect (the gimbal
    // sleeps to save battery; on first run nothing answers on the Wi-Fi
    // until the BT GATT-connect pulse has fired). Independent of
    // `_reconnecting` so a wake and a connect can run back-to-back
    // without either waiting on the other's flag.
    private val _waking = MutableStateFlow(false)
    val waking: StateFlow<Boolean> = _waking.asStateFlow()

    // Mount-Wi-Fi scan: in-flight flag for the "Find & wake Polaris…"
    // button on the Android Connection pane. Independent of `_waking`
    // (BT-only pulse) because the scan flow also does a Wi-Fi scan +
    // permission check, which can outlive the BT pulse itself.
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    // 8.0: monotonic counter bumped every time a connect attempt is
    // *launched*. The wake coroutine snapshots it at entry and compares
    // at terminal-write time — a mismatch means a connect happened
    // during the wake, so the wake's "Wake complete — try Connect"
    // message is suppressed and the connect's "Connected" line is the
    // authoritative terminal. This is what the in-flight
    // `!_reconnecting.value` check alone cannot catch, because the
    // connect's `finally` resets `_reconnecting` to false *before* the
    // wake resumes from its BT scan delays.
    private var reconnectGeneration: Int = 0

    /**
     * Stream 15.1 (issue #15): the active [CameraProfile] used by
     * [MainActivity.onLaunchVr] when handing FoV to VRActivity. Defaults
     * to [CameraProfile.PolarisEyepiece] — the per-mount default the
     * VRActivity used as a constant before #15. When a real sensor
     * stream lands the source will flip to [CameraProfileSource.SENSOR];
     * the render side uses the label to show the user which is in play.
     */
    private val _cameraProfile = MutableStateFlow(CameraProfile.PolarisEyepiece)
    val cameraProfile: StateFlow<CameraProfile> = _cameraProfile.asStateFlow()

    /**
     * Publish a new [CameraProfile] (e.g. when a sensor reading arrives,
     * or when the user toggles an override). The StateFlow conflates
     * identical samples, so it is safe to call from a hot sensor stream.
     */
    fun setCameraProfile(profile: CameraProfile) {
        _cameraProfile.value = profile
    }

    // 3c.5: host-edit buffer for the ReconnectDialog. Populated by
    // [tryReconnectIfMarkerExists] from the saved marker, and read by
    // [acceptReconnect] so the user can override the saved host without
    // first dismissing the dialog. A non-blank [draftHost] also
    // short-circuits [tryReconnectIfMarkerExists] so a re-prompt
    // (MainActivity.onResume) cannot clobber the user's edit.
    private val _draftHost = MutableStateFlow("")
    val draftHost: StateFlow<String> = _draftHost.asStateFlow()

    fun updateDraftHost(h: String) { _draftHost.value = h }

    // 3b.5-BUG: parallel of [draftHost] for the persisted [ReconnectPrompt.port].
    // Pre-fix, port had no draft/edit field at all and was a private mutableStateOf
    // hard-coded to 9090 in both connect() and saveMarker(). The user could
    // never change the port; even if they could, it was silently dropped on
    // round-trip. ReconnectDialog now exposes this as a numeric field and
    // acceptReconnect() reads it instead of the persisted prompt value.
    private val _draftPort = MutableStateFlow("")
    val draftPort: StateFlow<String> = _draftPort.asStateFlow()

    fun updateDraftPort(p: String) { _draftPort.value = p }

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
            // 3b.5-BUG: same seeding for port. Without this the port
            // text field starts blank on first prompt and any
            // acceptReconnect() derivation has nothing to fall back on
            // except the prompt's port. We prefer the persisted value so
            // the dialog round-trips whatever the user last accepted.
            _draftPort.value = marker.port.toString()
        }
    }

    /**
     * User accepted the prompt. Sets [host] to the persisted host (or the
     * user's edited [draftHost] if non-blank) and calls [connect] using the
     * persisted port (or the user's edited [draftPort] if a valid integer).
     * Clears the prompt synchronously so the dialog closes before the
     * connection attempt begins. Sets [reconnecting] true so the
     * (now-closed) dialog swap-in spinner and the dedicated "Cancel" path
     * become active. A successful connect will overwrite the marker with
     * fresh state; a cancelled connect leaves the marker untouched so the
     * next resume still offers the same prompt.
     */
    fun acceptReconnect() {
        val prompt = _reconnectPrompt.value ?: return
        val targetHost = _draftHost.value.trim().ifBlank { prompt.host }
        // 3b.5-BUG: derive the target port the same way. We *do not* trust
        // the persisted prompt's port if the user has typed something on
        // this surface, even when the typed value is unchanged (it gets
        // reseeded to the persisted value by tryReconnectIfMarkerExists),
        // because the user may have *just* edited it. An unparseable edit
        // (e.g. "" or "abc") falls back to the persisted port, mirroring
        // the host behavior.
        val targetPort = _draftPort.value.trim().toIntOrNull() ?: prompt.port
        _reconnectPrompt.value = null
        host = targetHost
        // 3b.5-BUG: commit the chosen port to the live field BEFORE
        // connect() reads it. MountSession is constructed with `port`,
        // not the prompt value, and saveMarker() also reads `port`. If
        // the user typed a different port, the existing marker is now
        // stale — persist a fresh one BEFORE launching connect so a
        // successful connect-time saveMarker() does not race with a
        // user-visible "still pointing at the old port" prompt on the
        // next launch. Same precedence as host: prompt.stale ↔ live != prompt.
        port = targetPort
        if (targetHost != prompt.host || targetPort != prompt.port) {
            val pos = position
            val writeResult = sessionStore.write(
                SessionMarker(
                    host = targetHost,
                    port = targetPort,
                    lastConnectedAtEpochMs = nowMs(),
                    lastMountMode = prompt.mountMode,
                    lastTrackingStarted = prompt.trackingStarted,
                    lastRollDeg = pos?.roll?.toDouble(),
                    lastPitchDeg = pos?.pitch?.toDouble(),
                ),
            )
            // 3d: if the write fails, the on-disk marker still points at
            // the OLD host, so the next launch would re-prompt the user
            // with the wrong host. Surface the failure so the user
            // understands the discrepancy, and still proceed with the
            // live connect (the session itself is not affected).
            if (writeResult.isFailure) {
                statusMessage = "Could not save updated host: ${writeResult.exceptionOrNull()?.message ?: "unknown"}"
            }
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
        // 3e: explicit reset is still needed for the "connect already
        // completed" race — the connectJob's finally block will also
        // reset the flag, but it may run on a different dispatcher and
        // we want the flag to drop synchronously with the cancel call
        // so the dialog action row swaps back immediately.
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
            // 3b.5-BUG: same clearing for the port-edit buffer.
            _draftPort.value = ""
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
     * connect, since 517 is the second poll) we record `null` for
     * roll/pitch — meaning "no 517 frame had landed yet". The next
     * `connect` will overwrite this once the first 517 lands. Pre-3d we
     * wrote 0.0 here, which the UI then displayed as "you were at roll
     * 0.0°" — a real first-class bug (data was invented out of thin air).
     */
    private fun saveMarker() {
        val pos = position
        val marker = SessionMarker(
            host = host,
            // 3b.5-BUG: was hard-coded 9090. Reads the live [port] field
            // which acceptReconnect() / connect() committed before this
            // call. For a non-reconnect connect(), port is whatever the
            // user last set it to (defaults to 9090).
            port = port,
            lastConnectedAtEpochMs = nowMs(),
            lastMountMode = mount.mode,
            lastTrackingStarted = mount.tracking == true,
            lastRollDeg = pos?.roll?.toDouble(),
            lastPitchDeg = pos?.pitch?.toDouble(),
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
        // 3d D2 + 3e E1: disconnect() now preserves any pending
        // "Could not save updated host: …" message (see the comment
        // in disconnect()), so the save-failure context survives the
        // disconnect-and-reconnect cycle intact. The terminal-status
        // branch in [connectJob] combines the save-failure message
        // with the connect-outcome message into a single status line
        // so the user sees both: the marker write failed AND the
        // live connect failed.
        // 8.0: bump the reconnect generation BEFORE disconnect() so
        // any wake coroutine that is currently suspended on a BT scan
        // delay sees the new generation on its next terminal-write
        // check and suppresses its own "Wake complete" / "Wake
        // failed" line. (Doing this AFTER disconnect() would also
        // work, but a wake whose gate fires between the suspend and
        // the generation bump would still clobber — bump first,
        // always.)
        reconnectGeneration++
        disconnect()
        demoMode = false
        // 3b.5-BUG: was hard-coded 9090. Reads the live [port] field which
        // acceptReconnect() committed before calling connect() (in the
        // reconnect path) or which the user set via the UI before pressing
        // Connect on a fresh connect. The control socket endpoint and the
        // persisted marker must agree, so both read from the same field.
        val s = MountSession(
            connectionFactory = connectionFactory,
            host = host,
            port = port,
            // App-handshake: see [AuthConfig]. `password` is the live in-memory
            // value the user provided via [setPassword]; the 820→821 sequence
            // is skipped when it is null and the gimbal doesn't require one.
            auth = AuthConfig(password = password),
            // Test-only: see [sessionReaderScope] doc. When the VM is
            // constructed with a test reader scope, MountSession uses
            // it for the reader loop so virtual time drives the
            // response delivery. Production callers leave it null
            // and we fall through to MountSession's default
            // Dispatchers.Default scope.
            readerScope = sessionReaderScope
                ?: CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
        session = s
        controller = TrackingController(s)
        cameraController = CameraController(s)
        wireHelpers(s)
        startAutoLevel(s)
        // 3c.5: capture the launched coroutine so [cancelReconnect] can
        // interrupt a hung `s.connect()` (mount powered off, link down).
        // 3e: wrap the whole body in try/finally so the in-flight flag
        // is reset on every exit path (success, failure, cancellation,
        // unhandled throw). Previously the flag was only reset in the
        // success/failure branches, so cancelling via [scope.cancel()]
        // (e.g. test teardown, Activity destroyed) would leave
        // `_reconnecting` stuck at true forever, wedging the spinner UX.
        // 3e: also drop the intermediate "Connecting to $host…" status
        // line. It was a real first-class race against the 3d D2
        // "Could not save updated host: …" message — the latter was set
        // synchronously inside [acceptReconnect] immediately before
        // [connect] was invoked, so the "Connecting to" line clobbered
        // the error before the user could see it. The in-flight spinner
        // already communicates "in progress" via the dialog; the
        // status line is more useful showing only the terminal state
        // ("Connected" or "Could not reach…"), or the preserved
        // write-failure message until the next terminal status lands.
        connectJob = scope.launch {
            try {
                // 3d D2: a preceding "Could not save updated host: …" message
                // (set synchronously in acceptReconnect when the marker
                // write failed) is the *cause* of the failure the user is
                // about to see, not a separate fact — the connect itself
                // is about to fail because the marker on disk is stale.
                // Preserve that message across the terminal-status update
                // so the user sees both: the save failure AND the
                // connection failure. Without this, the "Could not
                // reach …" line would silently clobber the save-failure
                // context and the user would have no idea why their
                // edit was lost.
                //
                // [disconnect()] preserves the save-failure message
                // (see its comment), so reading statusMessage here
                // inside the launched coroutine still returns the
                // "Could not save updated host: …" line that
                // acceptReconnect set synchronously. Match the EXACT
                // prefix from acceptReconnect, NOT any past combined
                // " — and could not reach …" message — a retry that
                // again fails to save would re-trigger the snapshot,
                // but a retry after a prior combined failure would
                // NOT (no "Could not save" prefix). This keeps each
                // connect attempt's status chain self-contained.
                val pendingSaveFailure = statusMessage
                    .takeIf { it.startsWith("Could not save updated host:") }
                if (s.connect()) {
                    statusMessage = "Connected"
                    // 3e E2: catch any throw from the post-connect bootstrap
                    // (saveMarker / startPolling / startCapturePolling /
                    // startPreview) so a single failing bootstrap step surfaces
                    // as a status message instead of killing the launched
                    // coroutine and leaving the UI in a half-connected state.
                    // Pre-fix the success branch was bare; an NPE in the
                    // preview controller (e.g. host resolution failure when the
                    // user typed an unresolvable hostname) would leave
                    // statusMessage stuck at "Connected" while the polling
                    // loops silently never started.
                    try {
                        saveMarker()
                        startPolling(s)
                        startCapturePolling(s)
                        startPreview()
                    } catch (e: Throwable) {
                        // Make sure the half-built session is torn down so a
                        // retry starts from a clean slate. The next connect()
                        // call already calls disconnect() at the top, but a
                        // successful-then-failed bootstrap means we'd otherwise
                        // keep polling and streaming from a session whose
                        // bootstrap never finished.
                        disconnect()
                        statusMessage = "Connected, but post-connect setup failed: " +
                            "${e.message ?: e::class.simpleName}"
                    }
                } else {
                    // 2026-09-01: surface the tagged failure reason from
                    // MountSession._state.lastErrorMessage so the user sees
                    // *which* connect step failed (284 handshake, 820 probe,
                    // 821 token, 823 hello, or "factory:" wiring) instead of
                    // the bare "Could not reach $host". Falls back to the
                    // original message if MountSession never set one.
                    val reason = s.state.value.lastErrorMessage
                    val prefix = pendingSaveFailure?.let { saveMsg ->
                        "$saveMsg — and could not reach $host."
                    } ?: "Could not reach $host"
                    statusMessage = if (reason != null) "$prefix — $reason" else "$prefix. Try Demo mode."
                }
            } catch (e: Throwable) {
                // 3e E2: outer catch for anything the inner try did not cover
                // (e.g. an uncaught throw inside the launching dispatcher, or
                // a CancellationException we want to swallow so the UI stays
                // responsive). Surface as a status message; the finally block
                // still resets the in-flight flag.
                //
                // If the gimbal reported `needed:1` from the 820 probe and
                // we don't have a password configured, MountSession throws
                // a recognisable IOException — flip the needsPassword flag
                // so the UI can surface a password-entry dialog instead of
                // presenting the raw message as a generic failure.
                if (e is java.io.IOException &&
                    (e.message?.contains("requires connection password") == true)
                ) {
                    needsPassword = true
                }
                statusMessage = "Connect failed: ${e.message ?: e::class.simpleName}"
            } finally {
                _reconnecting.value = false

            }
        }
    }

    /**
     * Bring up the segregated Wi-Fi bridge (BT wake → NM up → link up →
     * policy route). Each phase posts to [statusMessage] as it runs.
     */
    fun connectWifi() {
        // 3d D3: try/catch the whole launch so a synchronously-throwing
        // lambda (e.g. an NPE inside the orchestrator that escaped its
        // own runCatching) does not kill the coroutine and leave
        // statusMessage stuck on the initial "Connecting…" line. Also
        // guard against double-tap so two overlapping launches do not
        // race to write the same status line.
        if (_reconnecting.value) return
        _reconnecting.value = true
        scope.launch {
            try {
                statusMessage = "Connecting to mount Wi-Fi…"
                connectWifi { msg -> statusMessage = msg }
            } catch (e: Throwable) {
                statusMessage = "Wi-Fi bridge failed: ${e.message ?: e::class.simpleName}"
            } finally {
                _reconnecting.value = false
            }
        }
    }

    /**
     * BT-only wake pulse: a single GATT-connect to the gimbal so it brings
     * its Wi-Fi AP up. Distinct from [connectWifi] which continues on to
     * bring the segregated network up and install a policy route. The
     * Benro app uses this BT-wake as the first tap on a cold start — the
     * gimbal sleeps to save battery and nothing answers on Wi-Fi until
     * the pulse has fired (see `BluetoothProbe.wake` and
     * `polaris-re-results.md` §8.5).
     *
     * Has its own in-flight flag ([_waking]) so a user can tap Wake and
     * then Connect without either step waiting on the other.
     */
    fun wake() {
        if (_waking.value) return
        _waking.value = true
        scope.launch {
            // 8.0 wake-then-connect race (v2): a connect that *started* during
            // the wake must own the terminal status line, even if it finishes
            // and resets [_reconnecting] before the wake's terminal write
            // lands. The earlier gate (`!_reconnecting.value` at the terminal
            // write site) was racy: the wake coroutine suspends on BT scan
            // delays (~7s total) while the connect coroutine runs to
            // completion, resets `_reconnecting = false` in its `finally`,
            // and writes "Connected". The wake then resumes, sees
            // `_reconnecting = false`, and overwrites "Connected" with
            // "Wake complete — try Connect" — exactly the silent-failure UX
            // the user reported. Snapshot [reconnectGeneration] at the start
            // of the wake and compare it at the terminal site; if the
            // counter has advanced, a connect ran during the wake and owns
            // the status line.
            val wakeStartGeneration = reconnectGeneration
            try {
                // The in-flight gate ([!_reconnecting.value]) still does the
                // right thing for the live progress writes: a connect that is
                // currently in flight owns the status, so the wake's
                // "Scanning…" / "Connecting GATT…" / "Woke gimbal: …"
                // lines are suppressed until the connect's outcome lands.
                //
                // The generation gate ([reconnectGeneration ==
                // wakeStartGeneration]) catches the other half of the race:
                // the connect may *finish* between two wake progress
                // publishes and reset `_reconnecting = false` in its
                // `finally`. Without the generation check, the wake's next
                // progress line ("Woke gimbal: …") would land AFTER the
                // connect's "Connected" line and overwrite it — exactly
                // the silent-failure UX the user reported.
                val wakeProgress: suspend (String) -> Unit = { msg ->
                    if (!_reconnecting.value &&
                        reconnectGeneration == wakeStartGeneration
                    ) {
                        statusMessage = msg
                    }
                }
                if (!_reconnecting.value &&
                    reconnectGeneration == wakeStartGeneration
                ) {
                    statusMessage = "Waking gimbal over Bluetooth…"
                }
                wakeProbe(wakeProgress)
                // 8.0 v2: in addition to the in-flight check, suppress the
                // terminal if a connect *happened* during the wake. Without
                // this, the wake's terminal lands AFTER the connect's
                // terminal and clobbers "Connected" with "Wake complete —
                // try Connect". The generation counter is bumped by
                // [connect] at its start, so a wake that observed the
                // current generation at entry and a different one at exit
                // knows the connect took the status line.
                val connectRanDuringWake = reconnectGeneration != wakeStartGeneration
                if (!_reconnecting.value && !connectRanDuringWake &&
                    !statusMessage.startsWith("Woke ") &&
                    !statusMessage.startsWith("BT wake failed:")
                ) {
                    statusMessage = "Wake complete — try Connect"
                }
            } catch (e: Throwable) {
                if (!_reconnecting.value && reconnectGeneration == wakeStartGeneration) {
                    statusMessage = "Wake failed: ${e.message ?: e::class.simpleName}"
                }
            } finally {
                _waking.value = false
            }
        }
    }

    /**
     * Public write-access to [statusMessage] for host-driven flows that
     * already own their own coroutine and progress callback (e.g. the
     * Android `MountWifiScan` wake+scan flow). Mirrors how
     * [AppViewModel.wake] writes through its own progress lambda, but
     * skips the wake-in-flight and reconnect-generation gates because
     * the host is already on its own scope and is the authoritative
     * owner of the status line.
     */
    fun notifyStatus(msg: String) {
        statusMessage = msg
    }

    /**
     * Toggle [_scanning] for hosts that own the in-flight tracking on
     * the host side (e.g. `MountWifiScan` on Android, which has its own
     * coroutine scope). Most hosts can just observe [scanning] and
     * gate UI on it; this is a no-op when [_scanning] is already in
     * the desired state.
     */
    fun setScanning(value: Boolean) {
        _scanning.value = value
    }

    /** Simulator mode: no hardware needed; drives a fake session locally. */
    /**
     * Spin up a fully simulated mount session.
     *
     * @param startPolling when true (default for the UI button) the launch
     *   will start the mount-state poll loop; when false, the call returns
     *   after writing the marker. Tests that only want to assert the
     *   marker-save path pass `false` so that `advanceUntilIdle()` does
     *   not run the poll loop forever.
     */
    fun connectDemo(startPolling: Boolean = true) {
        disconnect()
        demoMode = true
        val sim = SimulatedMount()
        // Hold a reference so disconnect() can cancel the private reader
        // scope (otherwise the long-lived reader coroutine keeps the
        // SupervisorJob alive past the lifetime of the AppViewModel).
        demoSim = sim
        session = sim.session
        controller = TrackingController(sim.session)
        cameraController = CameraController(sim.session)
        wireHelpers(sim.session)

        startAutoLevel(sim.session)
        scope.launch {
            try {
                sim.session.connect()
                // SimulatedMount's connect() always returns true; treat as
                // success for the marker-save path. (A future slice that
                // simulates intermittent failure should gate this on the
                // return value the same way `connect()` does.)
                saveMarker()
                statusMessage = "Demo mode (simulated mount)"
                if (startPolling) {
                    startPolling(sim.session)
                }
                // No preview in demo mode: there is no MJPEG endpoint in the
                // simulator. PreviewController stays Idle, which the pane
                // renders as "Stream unavailable".
            } catch (e: Throwable) {
                // 3e E2: demo mode should never really throw (the
                // simulator is in-process) but if a future slice adds a
                // simulated intermittent failure or a saveMarker() I/O
                // regression sneaks in, surface the cause instead of
                // silently leaving the user with no status feedback.
                statusMessage = "Demo mode failed: ${e.message ?: e::class.simpleName}"
            }

        }
    }

    fun disconnect() {
        pollJob?.cancel()
        capturePollJob?.cancel()
        // 3c.5: if a reconnect was in flight, tear it down too so the
        // spinner does not stay up after the user navigates away.
        connectJob?.cancel()
        // Tear down the simulated mount's private reader scope so the
        // long-lived reader coroutine does not keep the dispatcher alive
        // past the lifetime of this view model.
        runCatching { demoSim?.shutdown() }
        demoSim = null
        // 3b.5-BUG (3e follow-up): _reconnecting is a "connect lifecycle"
        // flag, not a "disconnect lifecycle" flag. The launched coroutine
        // in connect() resets it in its `finally` block (line 411) and
        // cancelReconnect() resets it synchronously (line 290). Resetting
        // it here was the root cause of `connectClearsReconnectingOnSuccess`
        // failing: acceptReconnect() set it true and then synchronously
        // called connect(), which called disconnect() (here), which
        // cleared it before the test could observe the in-flight state.
        // The cancel path is unaffected because cancelReconnect() does
        // not call disconnect() and has its own explicit reset.
        stopAutoLevelAsync()
        cancelHelpersJobs()
        runCatching { preview.stop() }
        previewFrame = null

        session?.let { runBlocking { it.disconnect() } }
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
        firmwareVersion = null
        serialNumber = null
        wifiBand = null
        batteryDetail = null
        sdStatus = null
        omsState = null
        settlingTime = null
        exAxisState = null
        cameraInfo = null
        deviceInfo = null
        temperature = null
        captureState = null
        // Tear down the auto-level controller in its own coroutine so we can
        // call the suspending stopAutoLevel() from a non-suspending context.
        // Safe to fire-and-forget: stopAutoLevel only cancels jobs that
        // belong to this VM, and the controller's stop() is idempotent.
        stopAutoLevelAsync()
        _lastSolveResult.value = null
        // Clear the VR marker bus too so a stale solve doesn't linger
        // on the headset after the user disconnects.
        MarkerStateBus.reset()
        solveInProgress = false
        if (!demoMode) {
            // 3e E1: acceptReconnect() may have just set
            // statusMessage to "Could not save updated host: …"
            // (the marker write failed, so the next launch will
            // re-prompt with the OLD host). The user needs to see
            // that error, not the generic "Disconnected" line. This
            // branch is the only writer to statusMessage in
            // disconnect(), and it is reached on every connect()
            // (which calls disconnect() first) and on every
            // explicit user disconnect — in both cases, a
            // preceding save-failure error is the more useful
            // thing to keep on screen until the next connect
            // attempt overwrites it. Match the EXACT prefix used
            // in acceptReconnect.
            //
            // 8.0 wake-then-connect race: also skip the "Disconnected"
            // write if the wake coroutine is currently publishing
            // progress (its status is the more useful thing to keep on
            // screen while the BT pulse is in flight). The wake's
            // coroutine will either land its own terminal message or
            // surrender to the connect's terminal message on the
            // _reconnecting gate added in [wake].
            if (!statusMessage.startsWith("Could not save updated host:") &&
                !_waking.value
            ) {
                statusMessage = "Disconnected"
            }
        }
    }

    /**
     * Open the MJPEG preview stream on the current host. Each frame
     * is decoded off the main thread and published to [previewFrame].
     */
    private fun startPreview() {
        // 3h-BUG: read the live port field, not a hard-coded 8080. The port
        // is seeded from the persisted SessionMarker (in
        // tryReconnectIfMarkerExists) and may be overridden by the user via
        // the reconnect dialog's port field (acceptReconnect() writes
        // `port = targetPort`). Previously this call always hit 8080 even
        // when the user picked a non-default port, so the preview never
        // opened. Called before the collector launches so the first frame
        // isn't missed by a start/collect race.
        try {
            preview.start(host, port)
        } catch (e: Throwable) {
            // 3e E2: PreviewController.start may throw if the host is
            // unresolvable or the port is closed. Surface as a status
            // message and skip the collector — the rest of the post-
            // connect setup (polling, capture polling) will still
            // continue. Without this, the connect() success branch
            // catches it at the outer layer, but a cleaner message
            // here helps the user debug a misconfigured port.
            statusMessage = "Preview unavailable: ${e.message ?: e::class.simpleName}"
            return
        }
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
     * Centralised here so the `refresh*()` methods and the burst share one
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
            780 -> deviceInfo = value as DeviceInfo
            525 -> temperature = value as Temperature
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
    fun refreshDeviceInfo() = refreshBurstStep(780)
    fun refreshTemperature() = refreshBurstStep(525)

    /**
     * Fire every read-back in the pre-camera burst in a single coroutine.
     * Used by the Device info pane's "Refresh all" button so a single tap
     * re-reads every observable without each control having its own button.
     */
    fun refreshAllDeviceInfo() = scope.launch {
        for (step in CommandTable.BURST_PRE_CAMERA) {
            val s = session ?: return@launch
            runCatching {
                @Suppress("UNCHECKED_CAST")
                val r = s.request(step.code, parse = step.parse as (ResponseParser.Frame) -> Any?)
                if (r is MountSession.CmdResult.Ok) applyBurstValue(step.code, r.value)
            }
        }
        // Re-fire the camera burst too — cameraInfo is part of "what the
        // mount knows about itself" and the user expects the device-info
        // refresh to update it.
        val s = session ?: return@launch
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

    // ---- session persistence + reconnect prompt (issue #27) ----------------

    // [saveCurrentTarget] / [maybeOfferReconnect] / [confirmReconnect] were
    // part of the OURS' "Return to last celestial target" feature. They
    // referenced a TargetStore that was never wired in. Removed; the
    // THEIRS' host-reconnect prompt ([reconnectPrompt], [acceptReconnect])
    // is the only reconnect UX in this build.

    // ---- alignment ---------------------------------------------------------

    var alignmentStars by mutableStateOf(0)
        private set

    // ---- plate-solve -------------------------------------------------------

    /**
     * Most recent successful plate-solve. Null until the user has
     * pressed "Solve now" and the solver returned a confident
     * match. The pane surfaces RA/Dec / confidence / matched-star
     * count for the operator to decide whether to "Sync to target".
     *
     * Stream 7.4 (issue #14): a [StateFlow] rather than Compose
     * state, so the VR activity (which runs on the Android side
     * and cannot observe Compose state) can `collect` it in its
     * `lifecycleScope` and push each emission to the renderer's
     * `setSolveTarget` — re-solves while VR is open update the
     * marker within one frame instead of waiting for the next
     * launch.
     */
    private val _lastSolveResult = MutableStateFlow<SolveResult?>(null)
    val lastSolveResult: StateFlow<SolveResult?> = _lastSolveResult.asStateFlow()

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
        val launchJob = scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
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
                    // solveAndRefine now returns the full SolveResult
                    // (RA/Dec, confidence, matched-star count, and the
                    // timestamp the match converged at — stamped inside
                    // the solver). Threading the real values through here
                    // means the VR marker can show honest confidence and
                    // honest age, and the status message can mention the
                    // match quality. See issue #13.
                    _lastSolveResult.value = result
                    // Push the same value to the process-wide bus so the
                    // VR marker (a separate Android Activity with no
                    // reference to this AppViewModel) updates on a
                    // re-solve while it's open. See issue #14.
                    MarkerStateBus.publish(result)
                    statusMessage = "Solved RA %.4f° Dec %.4f° — %.0f%% conf, %d stars — mount refined to target"
                        .format(result.raDeg, result.decDeg, result.confidence * 100.0, result.matchedStars)
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
     * Test seam: install an [AutoLevelController] on this viewmodel
     * without going through [connect] / [startAutoLevel]. Lets tests
     * drive [runAutoLevel] with a controller wired to a known
     * (hanging, scripted, or real) sample source. Caller is responsible
     * for the controller's [AutoLevelController.start] lifecycle —
     * mirroring what [startAutoLevel] does in production.
     */
    internal fun testInstallAutoLevel(c: AutoLevelController) {
        this.autoLevelController = c
    }

    /**
     * Test seam: simulate a prior successful [solveNow] by writing
     * a [SolveResult] into [lastSolveResult]. Used by tests that
     * need to assert [disconnect] clears the cached solve.
     */
    internal fun testSetLastSolve(raDeg: Double, decDeg: Double) {
        val r = SolveResult(raDeg, decDeg, 0.6, 3)
        _lastSolveResult.value = r
        MarkerStateBus.publish(r)
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

    /**
     * Live tilt readback from [AutoLevelController]. Pushed from the 538 frame
     * stream while auto-level is running. Displayed as a read-only row in the
     * Full control pane so the user can see the current pitch/roll even when
     * they don't have a safe write path for it.
     */
    var autoLevelTilt by mutableStateOf<AutoLevelController.Tilt?>(null)
        private set

    /**
     * True while a `runAndAwait()` settling loop is in flight. Drives a
     * "Running…" badge next to the AutoLevel row.
     */
    var autoLevelRunning by mutableStateOf(false)
        private set

    /**
     * Bring up the auto-level controller and wire its three state flows
     * (enabled / tilt / running) into Compose-observable fields. Called from
     * [connect] (and [connectDemo]) so the readback is always live.
     *
     * Each collector is launched on the VM scope so a disconnect cleanly
     * cancels them. We hold the [Job]s in [autoLevelJobs] so [stopAutoLevel]
     * can join them before tearing down the controller.
     */
    private fun startAutoLevel(s: MountSession) {
        // Wire the AutoLevelController's settling loop to the session's
        // non-conflating tilt push stream (issue #6). The default
        // sampleSource reads from `session.frames` filtered to 538 — that
        // is a conflated StateFlow that drops intermediate samples, fatal
        // for AHRS settling. Use the buffered [MountSession.tilt] flow
        // via [MountSessionTiltSampleSource] so every 538 push arrives in
        // order.
        val pushSource = MountSessionTiltSampleSource(s)
        val sampleSource: suspend () -> AutoLevelController.Tilt? = {
            pushSource.next()?.let {
                AutoLevelController.Tilt(pitchDeg = it.pitchDeg, rollDeg = it.rollDeg)
            }
        }
        val c = AutoLevelController(s, sampleSource)
        autoLevelController = c
        c.start(scope)
        autoLevelJobs += scope.launch {
            c.isEnabled.collect { autoLevelEnabled = it }
        }
        autoLevelJobs += scope.launch {
            c.tilt.collect { autoLevelTilt = it }
        }
        autoLevelJobs += scope.launch {
            c.isRunning.collect { autoLevelRunning = it }
        }
        // refreshEnabled is a suspend function; fire it on the VM scope so
        // the connect path stays non-suspending and the initial 547 GET
        // races with the controller's collector.
        scope.launch { c.refreshEnabled() }
    }

    /**
     * Tear down the auto-level controller: cancel the three collectors,
     * stop the controller's job, null the state fields, and clear the
     * 538 readback so a stale tilt doesn't linger on the next connect.
     */
    private suspend fun stopAutoLevel() {
        // Snapshot before iterating: cancelAndJoin suspends, and another
        // coroutine on the UI dispatcher can mutate autoLevelJobs via +=
        // (e.g. refreshAutoLevel() re-wiring collectors), which would throw
        // ConcurrentModificationException on a live iterator.
        val pending = autoLevelJobs.toList()
        autoLevelJobs.clear()
        for (j in pending) j.cancelAndJoin()
        autoLevelController?.stop()
        autoLevelController = null
        autoLevelTilt = null
        autoLevelRunning = false
    }

    /**
     * Non-suspending wrapper for use in [disconnect]. Cancel and join all
     * collectors, then null the controller and state. The actual suspension
     * happens in [scope] so callers can stay on the main thread.
     */
    private fun stopAutoLevelAsync() {
        scope.launch { stopAutoLevel() }
    }

    fun refreshAutoLevel() {
        val s = session ?: run { statusMessage = "Not connected"; return }
        if (autoLevelController == null) {
            // First-time setup: bring up the controller and wire its three
            // state flows. Mirrors what startAutoLevel() does on connect;
            // exposed here so a manual Refresh from the UI works mid-session.
            val c = AutoLevelController(s)
            autoLevelController = c
            c.start(scope)
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
        // refreshEnabled is a suspend function; fire it on the VM scope so
        // the connect path stays non-suspending and the initial 547 GET
        // races with the controller's collector.
        scope.launch { autoLevelController?.refreshEnabled() }
    }

    fun setAutoLevelEnabled(on: Boolean) = scope.launch {
        val c = autoLevelController ?: run { statusMessage = "Not connected"; return@launch }
        c.setEnabled(on)
        statusMessage = "Auto-level ${if (on) "enabled" else "disabled"}"
    }

    private fun cancelAutoLevelJobs() {
        // Snapshot to avoid ConcurrentModificationException if a UI callback
        // mutates autoLevelJobs (e.g. refreshAutoLevel) while we iterate.
        val pending = autoLevelJobs.toList()
        autoLevelJobs.clear()
        for (j in pending) j.cancel()
    }

    private fun cancelHelpersJobs() {
        val pending = helpersJobs.toList()
        helpersJobs.clear()
        for (j in pending) j.cancel()
    }

    /**
     * Trigger one auto-level cycle (code 549) and wait for the gimbal to settle.
     *
     * The settling loop lives in [AutoLevelController]: it fires 549, then
     * consumes 538 push samples from the [MountSession.tilt] push stream
     * until 10 consecutive samples land within [AutoLevelController.SETTLE_EPSILON_DEG]
     * of their mean on both pitch and roll, or 60s elapses.
     *
     * Cancellation: if the calling scope is cancelled (e.g. the user hits
     * disconnect), `CancellationException` is re-thrown after we set a
     * friendly status. We deliberately do not catch `TimeoutCancellationException`
     * because the controller already maps that to [AutoLevelResult.TimedOut].
     */
    fun runAutoLevel() = scope.launch {
        val c = autoLevelController
        if (c == null) {
            statusMessage = "Not connected"
            return@launch
        }
        statusMessage = "Auto-level started"
        val result = try {
            // Hard cap at 75s in case the controller's 60s internal timeout
            // is bypassed (e.g. by a wedged sampleSource). 75s gives the
            // controller a comfortable buffer.
            withTimeoutOrNull(75_000) { c.runAndAwait() }
                ?: AutoLevelController.AutoLevelResult.TimedOut
        } catch (e: CancellationException) {
            statusMessage = "Auto-level cancelled"
            throw e
        }
        statusMessage = when (result) {
            is AutoLevelController.AutoLevelResult.Completed ->
                "Auto-level settled at roll=${"%.3f".format(result.rollDeg)}°, pitch=${"%.3f".format(result.pitchDeg)}°"
            is AutoLevelController.AutoLevelResult.Failed ->
                "Auto-level failed: ${result.reason}"
            AutoLevelController.AutoLevelResult.TimedOut ->
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

    // ---- catalog & comets (Tonight pane) ----------------------------------
    //
    // The bundled shards (catalog.json, stars.json, ngc.json, comets.json)
    // ship in `commonMain/resources/` and are visible via the
    // `readResourceText` expect/actual. They are loaded once at
    // construction time and never mutate; UI code reads them via
    // [tonightCatalog] / [tonightComets] to drive the Tonight call-out.

    /** Merged fixed-position catalog (Messier + named stars + NGC). */
    val tonightCatalog: Catalog by lazy {
        EmbeddedCatalog.loadFrom(EmbeddedCatalog.DEFAULT_SHARDS) { path ->
            readResourceText(path)
        }
    }

    /** Periodic comets + any appended discoveries (orbital elements). */
    val tonightComets: List<CometOrbitalElements> by lazy {
        val text = readResourceText("comets.json") ?: return@lazy emptyList()
        runCatching { CometShardLoader.parse(text).objects }
            .getOrDefault(emptyList())
    }

    /**
     * Tap-to-slew helper used by the Tonight pane. Prefills the goto
     * fields with the object's J2000 RA/Dec (formatted HH MM SS / ±DD MM SS)
     * and kicks off the existing [goto] path.
     */
    fun slewToObject(obj: dev.openpolaris.core.astro.AstroObject) {
        val raText = AstroMath.formatRaHours(obj.raDeg)
        val decText = AstroMath.formatDecDMS(obj.decDeg)
        updateRa(raText)
        updateDec(decText)
        setRaDecMode(true)
        statusMessage = "Slewing to ${obj.name ?: obj.designation} (${obj.type.name})…"
        goto()
    }

    // ====================================================================
    // Full control-panel surface.
    //
    // Each block below is gated on a FeatureFlags key so a freshly-flashed
    // build only exposes what we have verified on real hardware. Adding a
    // new code is a three-step recipe: (1) add the Descriptor to
    // CommandTable, (2) gate it on a flag here, (3) wire a UI affordance
    // that calls the VM method. The VM methods are the *only* place that
    // reaches into `session.send` for non-CommandTable-burst traffic, so
    // search there when looking for all wire side-effects.
    // ====================================================================

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


    // ---- OMS task list (825) ---------------------------------------------

    /** Last known OMS scheduled task list. */
    var omsTaskList by mutableStateOf<TaskList?>(null)
        private set

    fun refreshOmsTaskList() = scope.launch {
        if (!FeatureFlags.isEnabled("omsRead")) { statusMessage = "OMS read disabled by config"; return@launch }
        val s = session ?: return@launch
        runCatching {
            val r = s.request<TaskList>(Codes.OMS_TASK_LIST) { CommandTable.OMS_TASK_LIST.parse!!(it) }
            if (r is MountSession.CmdResult.Ok) {
                omsTaskList = r.value
                statusMessage = "OMS task list: ${r.value?.tasks?.size ?: 0} task(s)"
            }
        }
    }

    // ---- file manager (770 list, 703 delete, 705 protect) ----------------

    /** Last loaded file list. Null while not loaded. */
    var fileList by mutableStateOf<FileList?>(null)
        private set
    var fileListType by mutableStateOf(0)
        private set
    var fileListPage by mutableStateOf(0)
        private set

    fun setFileType(t: Int) { fileListType = t }
    fun setFilePage(p: Int) { fileListPage = p.coerceAtLeast(0) }

    fun refreshFileList() = scope.launch {
        if (!FeatureFlags.isEnabled("fileManager")) { statusMessage = "File manager disabled by config"; return@launch }
        val s = session ?: return@launch
        runCatching {
            val r = s.request<FileList>(Codes.FILE_LIST) { f -> FileList.fromFrame(f) }
            if (r is MountSession.CmdResult.Ok) {
                fileList = r.value
                statusMessage = "File list: ${r.value?.files?.size ?: 0} file(s)"
            }
        }
    }

    fun deleteFile(id: Int) = scope.launch {
        if (!FeatureFlags.isEnabled("fileManagerMutate")) {
            statusMessage = "File mutate disabled — enable fileManagerMutate in config"; return@launch
        }
        val s = session ?: return@launch
        s.send(Codes.FILE_DELETE, "id:$id;")
        statusMessage = "Delete $id sent"
        refreshFileList()
    }

    fun protectFile(id: Int, prot: Int) = scope.launch {
        if (!FeatureFlags.isEnabled("fileManagerMutate")) {
            statusMessage = "File mutate disabled"; return@launch
        }
        val s = session ?: return@launch
        s.send(Codes.FILE_PROTECT, "id:$id;prot:$prot;")
        statusMessage = "Protect $id = $prot sent"
        refreshFileList()
    }

    fun formatSd() = scope.launch {
        if (!FeatureFlags.isEnabled("fileManagerFormat")) {
            statusMessage = "Format disabled — enable fileManagerFormat in config"; return@launch
        }
        val s = session ?: return@launch
        s.send(Codes.FILE_SD_FORMAT)
        statusMessage = "SD format sent — will reload SD status"
        refreshSdStatus()
    }

    // ---- WiFi (770 scan, 771 list, 772 connect, 773 disconnect) ----------

    /** Raw payload of the last WiFi scan — parsers can be tightened later. */
    var wifiScanResult by mutableStateOf<String?>(null)
        private set

    fun refreshWifiScan() = scope.launch {
        if (!FeatureFlags.isEnabled("wifiScan")) { statusMessage = "WiFi scan disabled by config"; return@launch }
        val s = session ?: return@launch
        runCatching {
            val r = s.request<ResponseParser.Frame>(Codes.WIFI_SCAN) { it }
            if (r is MountSession.CmdResult.Ok) wifiScanResult = r.value.raw
        }
    }

    fun connectWifiSsid(ssid: String) = scope.launch {
        if (!FeatureFlags.isEnabled("wifiConnect")) {
            statusMessage = "WiFi connect disabled — enable wifiConnect in config"; return@launch
        }
        val s = session ?: return@launch
        s.send(Codes.WIFI_CONNECT, "ssid:$ssid;")
        statusMessage = "WiFi connect: $ssid"
    }

    fun disconnectWifi() = scope.launch {
        if (!FeatureFlags.isEnabled("wifiConnect")) { statusMessage = "WiFi write disabled — enable wifiConnect in config"; return@launch }
        val s = session ?: return@launch
        s.send(Codes.WIFI_DISCONNECT)
        statusMessage = "WiFi disconnect sent"
    }

    fun setWifiBand(band: Int) = scope.launch {
        if (!FeatureFlags.isEnabled("wifiConnect")) { statusMessage = "WiFi write disabled — enable wifiConnect in config"; return@launch }
        val s = session ?: return@launch
        s.send(Codes.SET_WIFI_BAND, "band:$band;")
        statusMessage = "WiFi band set to $band"
    }

    // ---- system (time, timezone, language, buzzer, LED, reboot, shutdown) -

    fun setSystemTime(epochSeconds: Long) = scope.launch {
        if (!FeatureFlags.isEnabled("systemSettings")) { statusMessage = "System settings disabled"; return@launch }
        val s = session ?: return@launch
        s.send(Codes.SYS_TIME, "time:$epochSeconds;")
        statusMessage = "System time set"
    }

    fun setTimezone(tz: Int) = scope.launch {
        if (!FeatureFlags.isEnabled("systemSettings")) { statusMessage = "System settings disabled"; return@launch }
        val s = session ?: return@launch
        s.send(Codes.SYS_TIMEZONE, "tz:$tz;")
        statusMessage = "Timezone $tz sent"
    }

    fun setLanguage(lang: Int) = scope.launch {
        if (!FeatureFlags.isEnabled("systemSettings")) { statusMessage = "System settings disabled"; return@launch }
        val s = session ?: return@launch
        s.send(Codes.SYS_LANGUAGE, "lang:$lang;")
        statusMessage = "Language $lang sent"
    }

    fun setBuzzer(on: Boolean) = scope.launch {
        if (!FeatureFlags.isEnabled("systemSettings")) { statusMessage = "System settings disabled"; return@launch }
        val s = session ?: return@launch
        s.send(Codes.SYS_BUZZER, "en:${if (on) 1 else 0};")
        statusMessage = "Buzzer ${if (on) "on" else "off"}"
    }

    fun setLed(on: Boolean) = scope.launch {
        if (!FeatureFlags.isEnabled("systemSettings")) { statusMessage = "System settings disabled"; return@launch }
        val s = session ?: return@launch
        s.send(Codes.SYS_LED, "en:${if (on) 1 else 0};")
        statusMessage = "LED ${if (on) "on" else "off"}"
    }

    @Suppress("DEPRECATION")
    fun reboot() = scope.launch {
        if (!FeatureFlags.isEnabled("allowReboot")) { statusMessage = "Reboot disabled — enable allowReboot in config"; return@launch }
        val s = session ?: return@launch
        // SYS_REBOOT (812) is unverified — see docs/FIRMWARE-UPLOAD-AUDIT-2026-09-01.md §2.
        // The decompile names 812 SP_GET_CELLULAR_IMEI. Production firmware auto-reboots
        // on install success; this is an explicit request from the UI, gated by
        // FeatureFlags.allowReboot, destructive, and requires the user to have opted in.
        s.send(Codes.SYS_REBOOT)
        statusMessage = "Reboot sent — connection will drop"
    }

    @Suppress("DEPRECATION")
    fun shutdown() = scope.launch {
        if (!FeatureFlags.isEnabled("allowShutdown")) { statusMessage = "Shutdown disabled — enable allowShutdown in config"; return@launch }
        val s = session ?: return@launch
        // SYS_SHUTDOWN (813) is unverified — see docs/FIRMWARE-UPLOAD-AUDIT-2026-09-01.md §2.
        // The decompile names 813 SP_SET_CELLULAR_COMUSB. Shutdown is not part of the
        // verified Benro Connect install flow; gated by FeatureFlags.allowShutdown.
        s.send(Codes.SYS_SHUTDOWN)
        statusMessage = "Shutdown sent — connection will drop"
    }

    /**
     * Drive the full FwPkt.zip firmware upload via [FirmwareUpdateController].
     * Surfaces every [FirmwareUpdateController.Status] update to both
     * [firmwareStatus] (so the UI can show a progress bar) and
     * [statusMessage] (so the bottom strip reports what is happening).
     *
     * Gated behind `firmwareUpload` — must be enabled in the user's
     * config file before the call has any effect. The flag defaults to
     * false because firmware install is destructive: a bad image bricks
     * the mount until you re-flash over USB.
     *
     * The transport ([firmwareDeliveryMode] / [firmwareSshHost]) is
     * supplied by the user via the FirmwarePane. [DeliveryMode.SSH_PIPE]
     * is the verified path (drops the bytes at `/app/sd/FwPkt.zip`
     * and the on-board watcher takes over after reboot);
     * [DeliveryMode.WIRE] is the experimental envelope through the
     * binary control plane. See the [FirmwareUpdateController] class
     * KDoc for the trust profile of each.
     */
    fun uploadFirmware(bytes: ByteArray, filename: String, rebootAfter: Boolean) = scope.launch {
        try {
            if (!FeatureFlags.isEnabled("firmwareUpload")) {
                statusMessage = "Firmware upload disabled — enable firmwareUpload in config"
                return@launch
            }
            val s = session ?: run {
                statusMessage = "Connect to the mount first"
                return@launch
            }
            if (bytes.isEmpty()) {
                statusMessage = "Pick a FwPkt.zip file first"
                return@launch
            }
            // Phase 1a #2: compute the local MD5 over the just-read bytes and
            // surface it in the pane. The controller's verify-before-upload
            // step will compare this to firmwareExpectedMd5 (when set) and
            // short-circuit if they disagree. Storing the value here also
            // lets the user copy it out for an external cross-check.
            val localMd5 = try {
                Md5.digest(bytes)
            } catch (t: Throwable) {
                statusMessage = "Failed to compute local MD5: ${t.message ?: t::class.simpleName}"
                firmwareBusy = false
                return@launch
            }
            pickedFirmwareMd5 = localMd5

            statusMessage = "Uploading firmware (${bytes.size} bytes) via ${firmwareDeliveryMode}…"
            val controller = FirmwareUpdateController(
                session = s,
                delivery = firmwareDeliveryMode,
                sshDelivery = buildFirmwareDelivery(),
                chunkSize = 1024,
                progressPollMs = 500,
                progressDoneRepeats = 2,
                installTimeoutMs = 5 * 60_000L, // 5 minutes
            )
            // A checksum is optional. When supplied, the controller validates
            // it and checks the selected file before any upload traffic.
            val expectedMd5 = firmwareExpectedMd5.trim().takeIf { it.isNotBlank() }
            if (expectedMd5 != null && (expectedMd5.length != 32 ||
                !expectedMd5.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
            )) {
                val s = FirmwareUpdateController.Status.Failed(
                        "expected MD5 must be 32 hexadecimal characters " +
                        "(got ${expectedMd5.length} chars)"
                )
                firmwareStatus = s
                statusMessage = "Firmware upload failed: ${s.reason}"
                firmwareBusy = false
                return@launch
            }
            val final = controller.start(
                bytes = bytes,
                filename = filename,
                expectedMd5 = expectedMd5,
                rebootAfter = rebootAfter,
            ) { status ->
                firmwareStatus = status
                statusMessage = when (status) {
                    is FirmwareUpdateController.Status.Idle -> "Idle"
                    is FirmwareUpdateController.Status.Uploading -> "Uploading: ${status.bytesSent}/${status.bytesTotal} bytes"
                    is FirmwareUpdateController.Status.Installing -> "Installing on mount: ${status.percent}%"
                    is FirmwareUpdateController.Status.Done -> if (rebootAfter) "Done — rebooting" else "Done"
                    is FirmwareUpdateController.Status.Failed -> "Firmware upload failed: ${status.reason}"
                }
            }
            firmwareStatus = final
            if (final is FirmwareUpdateController.Status.Done) {
                statusMessage = if (rebootAfter) "Firmware updated — rebooting" else "Firmware update complete"
            }
            // Reset the picker on success so the user can immediately pick the
            // next firmware (or close the pane without a "you have something
            // queued" surprise). On failure we keep the path so they can retry
            // without re-picking.
            if (final is FirmwareUpdateController.Status.Done) {
                pickedFirmwarePath = null
                pickedFirmwareName = null
                pickedFirmwareSize = null
                pickedFirmwareMd5 = null
                firmwareExpectedMd5 = ""
            }
        } catch (e: Throwable) {
            // 3e E2: a synchronous throw from controller.start (e.g. socket
            // IOException during chunk upload, NPE in a future
            // FirmwareUpdateController slice) must not leave firmwareBusy
            // stuck at true forever — that would prevent the user from
            // even re-picking the file. Surface the cause and clear the
            // busy flag so they can try again.
            statusMessage = "Firmware upload crashed: ${e.message ?: e::class.simpleName}"
            firmwareStatus = FirmwareUpdateController.Status.Failed("crash: ${e.message ?: e::class.simpleName}")
        } finally {
            firmwareBusy = false
        }
    }

    /**
     * Build the [FirmwareDelivery] for the current [firmwareDeliveryMode].
     * For [DeliveryMode.SSH_PIPE] this delegates to the platform seam
     * (`platformFirmwareDelivery`) which on JVM wires up
     * [dev.openpolaris.core.domain.ScpFirmwareDelivery] and on
     * Android currently returns [NoOpFirmwareDelivery] (Android does
     * not ship ssh on PATH; the OpenPolaris build does not yet include
     * a JSch/SSHJ port).
     *
     * For [DeliveryMode.WIRE] the controller does not need a delivery
     * seam, but we still pass the result of this method (the controller
     * ignores it). Returning a no-op sentinel keeps the wiring uniform.
     */
    private fun buildFirmwareDelivery(): FirmwareDelivery =
        if (firmwareDeliveryMode == DeliveryMode.SSH_PIPE) {
            platformFirmwareDelivery(host = firmwareSshHost)
        } else {
            NoOpFirmwareDelivery
        }

    /**
     * Open the native file picker so the user can choose a FwPkt.zip. On
     * JVM this is a blocking `FileDialog`; on Android it's an
     * `ACTION_OPEN_DOCUMENT` Intent. The result lands in [pickedFirmwarePath]
     * (and the companion name/size fields) so the pane can show what was
     * chosen before the user hits Upload.
     *
     * The actual file read happens in [uploadPickedFirmware] — we don't
     * load the bytes into memory at pick time, which would be wasteful for
     * the multi-MB images Benro's update tool ships.
     */
    fun pickFirmwareFile() {
        // Reset the previous attempt's status so the pane goes back to a
        // clean "ready to pick" state when the user reaches for a new file.
        if (firmwareBusy) return // ignore picks while uploading
        // Clear stale MD5 fields from a prior pick — we'll recompute on
        // upload once we have the bytes in hand. We deliberately do NOT
        // hash at pick time: a 50MB FwPkt.zip read twice (stat, then
        // hash, then upload) would be wasteful.
        pickedFirmwareMd5 = null
        FilePicker.pickFile(
            title = "Pick FwPkt.zip",
            mimeType = "application/zip",
        ) { path ->
            if (path == null) {
                // Native chooser closed without selecting. We can't tell
                // cancel from error here, so surface a neutral "cancelled"
                // message — better than the previous silent no-op that
                // confused users (issue #49 "no cancel option"). The
                // tagged [PickerBridge] path is the one that can
                // distinguish Cancelled vs Error; see [applyPickResult].
                statusMessage = "Picker cancelled"
                return@pickFile
            }
            val f = PlatformFile(path)
            if (!f.exists() || !f.isReadable()) {
                statusMessage = "Picked file is not readable: $path"
                return@pickFile
            }
            pickedFirmwarePath = path
            pickedFirmwareName = basename(path)
            // Stat the file to surface its size before the user hits
            // Upload. On JVM the read is cheap; on Android the SAF copy
            // happened at pick time so the file is already in cacheDir.
            pickedFirmwareSize = try {
                java.io.File(path).length()
            } catch (t: Throwable) {
                null
            }
            statusMessage = "Firmware ready: ${pickedFirmwareName} (${pickedFirmwareSize ?: "?"} bytes)"
        }
    }

    /**
     * Apply a [PickerBridge.PickResult] to this VM. Called from
     * `MainActivity.onCreate` after the new VM is built, to drain
     * any result that was published by the previous activity's
     * launcher callback (the rotation / recreate case — issue #49).
     *
     * This is the tagged-path equivalent of the inline callback in
     * [pickFirmwareFile]: the inline callback can only distinguish
     * "got a path" vs "got null" (the AndroidX callback is just
     * `Uri?`), so it has to say "cancelled" for both Cancel and
     * Error. The bridge path tags the result with a [PickerBridge.PickResult.Reason]
     * so we can give a more honest "Picker failed" for the error case.
     */
    fun applyPickResult(result: PickerBridge.PickResult) {
        when (result.reason) {
            PickerBridge.PickResult.Reason.Picked -> {
                val path = result.absolutePath ?: run {
                    // A Picked result with a null path is a malformed
                    // publish (handleResult returned null but the
                    // launcher said Picked). Treat as error.
                    statusMessage = "Picker failed: empty path"
                    return
                }
                val f = PlatformFile(path)
                if (!f.exists() || !f.isReadable()) {
                    statusMessage = "Picked file is not readable: $path"
                    return
                }
                pickedFirmwarePath = path
                pickedFirmwareName = basename(path)
                pickedFirmwareSize = try {
                    java.io.File(path).length()
                } catch (t: Throwable) {
                    null
                }
                statusMessage = "Firmware ready: ${pickedFirmwareName} (${pickedFirmwareSize ?: "?"} bytes)"
            }
            PickerBridge.PickResult.Reason.Cancelled -> {
                statusMessage = "Picker cancelled"
            }
            PickerBridge.PickResult.Reason.Error -> {
                statusMessage = "Picker failed"
            }
        }
    }

    /**
     * Convenience: read the bytes of the picked firmware file and start the
     * upload. This is the entry point the "Upload" button calls. We do the
     * read inside the VM (rather than the composable) so the pane can stay
     * thin and the read can be cancelled on disconnect.
     */
    fun uploadPickedFirmware() = scope.launch {
        try {
            val path = pickedFirmwarePath
            val name = pickedFirmwareName
            if (path == null || name == null) {
                statusMessage = "Pick a FwPkt.zip first"
                return@launch
            }
            firmwareBusy = true
            val bytes = withContext(ioDispatcher) {
                try {
                    PlatformFile(path).readBytes()
                } catch (t: Throwable) {
                    null
                }
            }
            if (bytes == null) {
                firmwareBusy = false
                statusMessage = "Could not read $name"
                return@launch
            }
            uploadFirmware(bytes, name, firmwareRebootAfter)
        } catch (e: Throwable) {
            // 3e E2: outer guard — uploadFirmware has its own try/catch
            // but the read-bytes path or the busy-flag flip could
            // throw in a future slice. Always reset the flag and
            // surface the cause.
            statusMessage = "Firmware upload prep failed: ${e.message ?: e::class.simpleName}"
            firmwareBusy = false
        }
    }

    /** Drop the picked file without uploading — the "Clear" button. */
    fun clearPickedFirmware() {
        if (firmwareBusy) return
        pickedFirmwarePath = null
        pickedFirmwareName = null
        pickedFirmwareSize = null
        pickedFirmwareMd5 = null
        firmwareStatus = null
    }

    private fun basename(path: String): String {
        val ix = path.lastIndexOfAny(charArrayOf('/', '\\'))
        return if (ix >= 0) path.substring(ix + 1) else path
    }

    companion object {
        /**
         * Maximum age of a cached [SessionMarker] that will still trigger
         * the reconnect prompt (issue #27, 3c.4). 24 h covers a typical
         * evening-to-next-evening observing session; anything older is
         * almost certainly the wrong target.
         */
        private const val RECONNECT_MAX_AGE_MS: Long = 24L * 60L * 60L * 1000L

    }
}
