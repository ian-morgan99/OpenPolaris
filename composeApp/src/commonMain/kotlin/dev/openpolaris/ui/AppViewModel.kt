package dev.openpolaris.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.openpolaris.core.config.FeatureFlags
import dev.openpolaris.core.domain.AlignmentController
import dev.openpolaris.core.domain.AstroMediaExportController
import dev.openpolaris.core.domain.AstroMediaExportDestination
import dev.openpolaris.core.domain.AstroMediaExportSink
import dev.openpolaris.core.domain.AstroMediaExportState
import dev.openpolaris.core.domain.SupportBundleExportController
import dev.openpolaris.core.domain.SupportBundleExportSink
import dev.openpolaris.core.domain.SupportBundleExportState
import dev.openpolaris.core.domain.SupportBundlePublication
import dev.openpolaris.core.domain.SupportBundleSnapshot
import dev.openpolaris.core.domain.SupportBundleTimelineEntry
import dev.openpolaris.core.domain.AstroMediaFetchResult
import dev.openpolaris.core.domain.AstroMediaItem
import dev.openpolaris.core.domain.AstroMediaListResult
import dev.openpolaris.core.domain.AstroMediaPreview
import dev.openpolaris.core.domain.AstroMediaState
import dev.openpolaris.core.domain.AstroMediaTransferKind
import dev.openpolaris.core.domain.AstroWebUiCaptureClient
import dev.openpolaris.core.domain.AstroWebUiCaptureResult
import dev.openpolaris.core.domain.AstroWebUiAstroCaptureClient
import dev.openpolaris.core.domain.AstroWebUiAstroCaptureParameters
import dev.openpolaris.core.domain.AstroWebUiAstroCaptureResult
import dev.openpolaris.core.domain.AstroWebUiAstroCaptureState
import dev.openpolaris.core.domain.AstroWebUiMediaClient
import dev.openpolaris.core.domain.AstroWebUiStatusClient
import dev.openpolaris.core.domain.AstroWebUiStatusState
import dev.openpolaris.core.domain.AstroWebUiStatusResult
import dev.openpolaris.core.domain.AstroWebUiGuideClient
import dev.openpolaris.core.domain.AstroWebUiGuideResult
import dev.openpolaris.core.domain.AstroWebUiGuideUiState
import dev.openpolaris.core.domain.AstroWebUiAutofocusClient
import dev.openpolaris.core.domain.AstroWebUiAutofocusParameters
import dev.openpolaris.core.domain.AstroWebUiAutofocusResult
import dev.openpolaris.core.domain.AstroWebUiAutofocusUiState
import dev.openpolaris.core.domain.AstroWebUiFocalClient
import dev.openpolaris.core.domain.AstroWebUiFocalResult
import dev.openpolaris.core.domain.AstroWebUiFocalUiState
import dev.openpolaris.core.domain.AstroWebUiLimitsClient
import dev.openpolaris.core.domain.AstroWebUiLimitsResult
import dev.openpolaris.core.domain.AstroWebUiLimitsUiState
import dev.openpolaris.core.domain.AstroWebUiKeepWifiClient
import dev.openpolaris.core.domain.AstroWebUiKeepWifiResult
import dev.openpolaris.core.domain.AstroWebUiKeepWifiUiState
import dev.openpolaris.core.domain.AstroWebUiWifiClient
import dev.openpolaris.core.domain.AstroWebUiWifiResult
import dev.openpolaris.core.domain.AstroWebUiWifiUiState
import dev.openpolaris.core.domain.AstroWebUiLinkClient
import dev.openpolaris.core.domain.AstroWebUiLinkResult
import dev.openpolaris.core.domain.AstroWebUiLinkUiState
import dev.openpolaris.core.domain.AstroWebUiAlignRequest
import dev.openpolaris.core.domain.AstroWebUiGotoRequest
import dev.openpolaris.core.domain.AstroWebUiTargetClient
import dev.openpolaris.core.domain.AstroWebUiTargetResult
import dev.openpolaris.core.domain.AstroWebUiTargetUiState
import dev.openpolaris.core.domain.AstroWebUiRunStateClient
import dev.openpolaris.core.domain.AstroWebUiRunStateResult
import dev.openpolaris.core.domain.AstroWebUiRunStateUiState
import dev.openpolaris.core.domain.AstroWebUiSolveClient
import dev.openpolaris.core.domain.AstroWebUiSolveResult
import dev.openpolaris.core.domain.AstroWebUiSolveUiState
import dev.openpolaris.core.domain.AstroWebUiSolveControlClient
import dev.openpolaris.core.domain.AstroWebUiSolveControlResult
import dev.openpolaris.core.domain.AstroWebUiSolveControlUiState
import dev.openpolaris.core.domain.AstroWebUiSolveMode
import dev.openpolaris.core.domain.AstroWebUiSolveRequest
import dev.openpolaris.core.domain.AstroWebUiSessionControlClient
import dev.openpolaris.core.domain.AstroWebUiSessionControlResult
import dev.openpolaris.core.domain.AstroWebUiSessionControlState
import dev.openpolaris.core.domain.AstroWebUiTrackRequest
import dev.openpolaris.core.domain.AstroWebUiSiteClient
import dev.openpolaris.core.domain.AstroWebUiSiteResult
import dev.openpolaris.core.domain.AstroWebUiSiteState
import dev.openpolaris.core.domain.AstroWebUiSiteControlClient
import dev.openpolaris.core.domain.AstroWebUiSiteWriteRequest
import dev.openpolaris.core.domain.AstroWebUiSiteWriteResult
import dev.openpolaris.core.domain.AstroWebUiSiteWriteState
import dev.openpolaris.core.domain.AstroWebUiSunProgrammeClient
import dev.openpolaris.core.domain.AstroWebUiSunParameters
import dev.openpolaris.core.domain.AstroWebUiSunResult
import dev.openpolaris.core.domain.AstroWebUiSunState
import dev.openpolaris.core.domain.AstroWebUiMotionProgrammeClient
import dev.openpolaris.core.domain.AstroWebUiMotionProgrammeResult
import dev.openpolaris.core.domain.AstroWebUiMotionProgrammeState
import dev.openpolaris.core.domain.AstroWebUiPlcKeyRequest
import dev.openpolaris.core.domain.AstroWebUiPlcParameters
import dev.openpolaris.core.domain.AstroWebUiPathLapseParameters
import dev.openpolaris.core.domain.AstroWebUiPathWaypointRequest
import dev.openpolaris.core.domain.AstroWebUiGrailParameters
import dev.openpolaris.core.domain.AstroWebUiProgrammeClient
import dev.openpolaris.core.domain.AstroWebUiProgrammeControlClient
import dev.openpolaris.core.domain.AstroWebUiProgrammeControlResult
import dev.openpolaris.core.domain.AstroWebUiProgrammeResult
import dev.openpolaris.core.domain.AstroWebUiProgrammeStartClient
import dev.openpolaris.core.domain.AstroWebUiProgrammeStartResult
import dev.openpolaris.core.domain.AstroWebUiProgrammeStartState
import dev.openpolaris.core.domain.AstroWebUiLapseParameters
import dev.openpolaris.core.domain.AstroWebUiPanoramaParameters
import dev.openpolaris.core.domain.AstroWebUiHdrParameters
import dev.openpolaris.core.domain.AstroWebUiFocusParameters
import dev.openpolaris.core.domain.AstroWebUiProgrammeState
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
import dev.openpolaris.core.domain.CameraAttachment
import dev.openpolaris.core.domain.CameraInfo
import dev.openpolaris.core.domain.CameraProfile
import dev.openpolaris.core.domain.CameraProfileSource
import dev.openpolaris.core.domain.Connection
import dev.openpolaris.core.domain.ConnectionMode
import dev.openpolaris.core.domain.BluetoothWakePhase
import dev.openpolaris.core.domain.BluetoothWakeState
import dev.openpolaris.core.domain.DeviceInfo
import dev.openpolaris.core.domain.EndpointAddress
import dev.openpolaris.core.domain.EndpointHealth
import dev.openpolaris.core.domain.EndpointKind
import dev.openpolaris.core.domain.EndpointPhase
import dev.openpolaris.core.domain.EndpointProfile
import dev.openpolaris.core.domain.EndpointService
import dev.openpolaris.core.domain.ExAxisState
import dev.openpolaris.core.domain.FileEntry
import dev.openpolaris.core.domain.FileList
import dev.openpolaris.core.domain.FastJogActionResult
import dev.openpolaris.core.domain.FastJogAcquireResult
import dev.openpolaris.core.domain.FastJogController
import dev.openpolaris.core.domain.FastJogSendResult
import dev.openpolaris.core.domain.FastJogState
import dev.openpolaris.core.domain.FastJogTransport
import dev.openpolaris.core.domain.FastJogLease
import dev.openpolaris.core.domain.FirmwareUpdateController
import dev.openpolaris.core.domain.DeliveryMode
import dev.openpolaris.core.domain.FirmwareDelivery
import dev.openpolaris.core.domain.NoOpFirmwareDelivery
import dev.openpolaris.core.domain.platformFirmwareDelivery
import dev.openpolaris.core.domain.platformFirmwareCommandRunner
import dev.openpolaris.core.domain.GimbalPosition
import dev.openpolaris.core.io.FilePicker
import dev.openpolaris.core.io.PickerBridge
import dev.openpolaris.core.session.PlatformFile
import dev.openpolaris.core.domain.GoToController
import dev.openpolaris.core.domain.HelpersController
import dev.openpolaris.core.domain.CameraController
import dev.openpolaris.core.domain.CaptureEvidenceGate
import dev.openpolaris.core.domain.MarkerStateBus
import dev.openpolaris.core.domain.MountMode
import dev.openpolaris.core.domain.MountSession
import dev.openpolaris.core.domain.MountState
import dev.openpolaris.core.domain.OmsState
import dev.openpolaris.core.domain.SdStatus
import dev.openpolaris.core.domain.TaskList
import dev.openpolaris.core.domain.Temperature
import dev.openpolaris.core.domain.PreviewController
import dev.openpolaris.core.domain.PreviewTransport
import dev.openpolaris.core.domain.QualifiedSlowMotionTransport
import dev.openpolaris.core.domain.SystemMillis
import dev.openpolaris.core.domain.MotionOwner
import dev.openpolaris.core.domain.MotionAcquireResult
import dev.openpolaris.core.domain.MotionLease
import dev.openpolaris.core.domain.MotionState
import dev.openpolaris.core.domain.MotionStopReason
import dev.openpolaris.core.domain.MotionStopResult
import dev.openpolaris.core.domain.MotionStopTransport
import dev.openpolaris.core.domain.MotionSupervisor
import dev.openpolaris.core.domain.RateAxis
import dev.openpolaris.core.domain.RateAdjustCapability
import dev.openpolaris.core.domain.RateAdjustContext
import dev.openpolaris.core.domain.RateAdjustRequest
import dev.openpolaris.core.domain.RateAdjustState
import dev.openpolaris.core.domain.RateAdjustTransport
import dev.openpolaris.core.domain.RateAxisBounds
import dev.openpolaris.core.domain.RateCapabilityIdentity
import dev.openpolaris.core.domain.RateCommandResult
import dev.openpolaris.core.domain.RateUnits
import dev.openpolaris.core.domain.SafeRateAdjustController
import dev.openpolaris.core.domain.SlowMotionActionResult
import dev.openpolaris.core.domain.SlowMotionCapabilities
import dev.openpolaris.core.domain.SlowMotionCapabilityRecord
import dev.openpolaris.core.domain.SlowMotionAcquireResult
import dev.openpolaris.core.domain.SlowMotionCalibration
import dev.openpolaris.core.domain.SlowMotionController
import dev.openpolaris.core.domain.SlowMotionDemoSnapshot
import dev.openpolaris.core.domain.SlowMotionLease
import dev.openpolaris.core.domain.SlowMotionPlanner
import dev.openpolaris.core.domain.SlowMotionTarget
import dev.openpolaris.core.domain.SlowMotionTransport
import dev.openpolaris.core.domain.SlowMotionTransportResult
import dev.openpolaris.core.domain.SlowScheduleCommand
import dev.openpolaris.core.domain.TrackingController
import dev.openpolaris.core.domain.TrackingRate
import dev.openpolaris.core.domain.createAstroMediaExportSink
import dev.openpolaris.core.domain.createSupportBundleExportSink
import dev.openpolaris.core.domain.createAstroWebUiMediaClient
import dev.openpolaris.core.domain.createAstroWebUiCaptureClient
import dev.openpolaris.core.domain.createAstroWebUiAstroCaptureClient
import dev.openpolaris.core.domain.createAstroWebUiStatusClient
import dev.openpolaris.core.domain.createAstroWebUiGuideClient
import dev.openpolaris.core.domain.createAstroWebUiAutofocusClient
import dev.openpolaris.core.domain.createAstroWebUiFocalClient
import dev.openpolaris.core.domain.createAstroWebUiLimitsClient
import dev.openpolaris.core.domain.createAstroWebUiKeepWifiClient
import dev.openpolaris.core.domain.createAstroWebUiWifiClient
import dev.openpolaris.core.domain.createAstroWebUiLinkClient
import dev.openpolaris.core.domain.createAstroWebUiTargetClient
import dev.openpolaris.core.domain.createAstroWebUiRunStateClient
import dev.openpolaris.core.domain.createAstroWebUiSolveClient
import dev.openpolaris.core.domain.createAstroWebUiSolveControlClient
import dev.openpolaris.core.domain.createAstroWebUiSessionControlClient
import dev.openpolaris.core.domain.createAstroWebUiSiteClient
import dev.openpolaris.core.domain.createAstroWebUiSiteControlClient
import dev.openpolaris.core.domain.createAstroWebUiSunProgrammeClient
import dev.openpolaris.core.domain.createAstroWebUiMotionProgrammeClient
import dev.openpolaris.core.domain.createAstroWebUiProgrammeClient
import dev.openpolaris.core.domain.createAstroWebUiProgrammeControlClient
import dev.openpolaris.core.domain.createAstroWebUiProgrammeStartClient
import dev.openpolaris.core.domain.createPreviewTransport
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
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Transport used only by the Demo SLOW owner. It records the exact logical
 * scheduler output but never serializes or sends a firmware frame.
 */
private class DemoSlowMotionTransport : SlowMotionTransport {
    private val events = mutableListOf<SlowScheduleCommand>()

    override suspend fun apply(command: SlowScheduleCommand): SlowMotionTransportResult {
        events += command
        return SlowMotionTransportResult.Accepted
    }

    override suspend fun stop(reason: MotionStopReason): MotionStopResult =
        MotionStopResult.Stopped

    fun drainEvents(): List<SlowScheduleCommand> {
        if (events.isEmpty()) return emptyList()
        return events.toList().also { events.clear() }
    }
}

/** Accepts logical fine-rate requests without any wire or host transport. */
private class DemoFineRateTransport : RateAdjustTransport {
    override suspend fun apply(request: RateAdjustRequest): RateCommandResult =
        RateCommandResult.Accepted(request.version)

    override suspend fun stop(): RateCommandResult = RateCommandResult.Accepted()
}

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
private val DEMO_PREVIEW_PNG = byteArrayOf(
    -119, 80, 78, 71, 13, 10, 26, 10,
    0, 0, 0, 13, 73, 72, 68, 82,
    0, 0, 0, 1, 0, 0, 0, 1,
    8, 6, 0, 0, 0, 31, 21, -60,
    -119, 0, 0, 0, 13, 73, 68, 65,
    84, 120, -100, 99, 96, 96, 96, 0,
    0, 0, 4, 0, 1, 11, -25, 2,
    -99, 0, 0, 0, 0, 73, 69, 78,
    68, -82, 66, 96, -126,
)

class AppViewModel(
    private val scope: CoroutineScope,
    private val connectionFactory: () -> Connection,
    /**
     * Bridge the segregated Wi-Fi interface to the gimbal. The lambda receives
     * a `progress: (String) -> Unit` callback; it is called from a background
     * dispatcher and must be safe to invoke off the main thread. It returns
     * true only after the route and Polaris service identity are verified. The
     * default is a false-returning no-op so callers without a bridge (e.g. the
     * Android build) cannot claim Wi-Fi readiness.
     */
    private val connectWifi: suspend (suspend (String) -> Unit) -> Boolean = { false },
    /**
     * BT-only wake pulse for the gimbal. Distinct from [connectWifi] (which
     * does wake → NM up → link → policy route in one shot). The lambda
     * receives a `progress: (String) -> Unit` callback it calls from a
     * background dispatcher; the default is a no-op so callers without a
     * BT stack (e.g. the Android build) can construct the VM without it.
     * The [ConnectionPane] "Wake" button is shown when this is wired.
     */
    private val wakeProbe: suspend (suspend (String) -> Unit) -> Unit = {},
    /**
     * Bridge teardown. The reverse of [connectWifi]: removes the policy route,
     * brings the saved Wi-Fi profile down, and releases the retained BLE GATT
     * wake link (the keep-alive change). The lambda receives a
     * `progress: (String) -> Unit` callback it calls from a background
     * dispatcher; the default is a no-op so callers without a bridge
     * implementation (e.g. the Android build) can construct the VM without it.
     * The [ConnectionPane] "Tear down bridge" button is shown when this is wired.
     */
    private val teardownBridge: suspend (suspend (String) -> Unit) -> Unit = {},
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
    // #74 test seam: lets tests inject a recording [PreviewTransport] factory
    // so they can assert the stream URL's host/port (the regression was that
    // the control port 9090 leaked into the MJPEG request). Production keeps
    // the platform default via PreviewController's own default.
    private val previewTransportFactory: ((ByteArray) -> Boolean, (Throwable) -> Unit) -> PreviewTransport = ::createPreviewTransport,
    private val previewIoDispatcher: CoroutineDispatcher? = null,
    private val astroWebUiMediaClient: AstroWebUiMediaClient = createAstroWebUiMediaClient(),
    private val astroWebUiCaptureClient: AstroWebUiCaptureClient = createAstroWebUiCaptureClient(),
    private val astroWebUiAstroCaptureClient: AstroWebUiAstroCaptureClient = createAstroWebUiAstroCaptureClient(),
    private val astroWebUiStatusClient: AstroWebUiStatusClient = createAstroWebUiStatusClient(),
    private val astroWebUiGuideClient: AstroWebUiGuideClient = createAstroWebUiGuideClient(),
    private val astroWebUiAutofocusClient: AstroWebUiAutofocusClient = createAstroWebUiAutofocusClient(),
    private val astroWebUiFocalClient: AstroWebUiFocalClient = createAstroWebUiFocalClient(),
    private val astroWebUiLimitsClient: AstroWebUiLimitsClient = createAstroWebUiLimitsClient(),
    private val astroWebUiKeepWifiClient: AstroWebUiKeepWifiClient = createAstroWebUiKeepWifiClient(),
    private val astroWebUiWifiClient: AstroWebUiWifiClient = createAstroWebUiWifiClient(),
    private val astroWebUiLinkClient: AstroWebUiLinkClient = createAstroWebUiLinkClient(),
    private val astroWebUiTargetClient: AstroWebUiTargetClient = createAstroWebUiTargetClient(),
    private val astroWebUiSolveClient: AstroWebUiSolveClient = createAstroWebUiSolveClient(),
    private val astroWebUiSolveControlClient: AstroWebUiSolveControlClient = createAstroWebUiSolveControlClient(),
    private val astroWebUiRunStateClient: AstroWebUiRunStateClient = createAstroWebUiRunStateClient(),
    private val astroWebUiSessionControlClient: AstroWebUiSessionControlClient = createAstroWebUiSessionControlClient(),
    private val astroWebUiSiteClient: AstroWebUiSiteClient = createAstroWebUiSiteClient(),
    private val astroWebUiSiteControlClient: AstroWebUiSiteControlClient = createAstroWebUiSiteControlClient(),
    private val astroWebUiProgrammeClient: AstroWebUiProgrammeClient = createAstroWebUiProgrammeClient(),
    private val astroWebUiProgrammeControlClient: AstroWebUiProgrammeControlClient = createAstroWebUiProgrammeControlClient(),
    private val astroWebUiProgrammeStartClient: AstroWebUiProgrammeStartClient = createAstroWebUiProgrammeStartClient(),
    private val astroWebUiSunProgrammeClient: AstroWebUiSunProgrammeClient = createAstroWebUiSunProgrammeClient(),
    private val astroWebUiMotionProgrammeClient: AstroWebUiMotionProgrammeClient = createAstroWebUiMotionProgrammeClient(),
    private val astroMediaExportSink: AstroMediaExportSink = createAstroMediaExportSink(),
    private val supportBundleExportSink: SupportBundleExportSink = createSupportBundleExportSink(),
    /** Evidence-backed SLOW allow-list; production defaults to the unqualified record. */
    private val slowMotionCapabilityRecord: SlowMotionCapabilityRecord = SlowMotionCapabilities.current,
    /** Clock seam for deterministic SLOW hold/release tests; production uses wall time. */
    private val slowMotionNow: () -> Long = { SystemMillis.now() },
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

    /** Network intent shown in the Connection workspace and endpoint health. */
    var connectionMode by mutableStateOf(ConnectionMode.DIRECT_AP)
        private set
    private var connectionModePersistence: ((ConnectionMode) -> Unit)? = null

    fun setConnectionModePersistence(persistence: (ConnectionMode) -> Unit) {
        connectionModePersistence = persistence
    }

    /** Restore presentation/routing intent without writing preferences again. */
    fun restoreConnectionMode(mode: ConnectionMode) {
        connectionMode = mode
    }

    fun selectConnectionMode(mode: ConnectionMode) {
        if (connectionMode == mode) return
        connectionMode = mode
        connectionModePersistence?.invoke(mode)
        if (session != null) {
            statusMessage = "Connection mode changed to ${mode.name.lowercase().replace('_', ' ')}; reconnect to apply"
        }
    }

    // 3b.5-BUG: live (committed) port, read by MountSession() and the
    // persisted marker. Defaults to 9090 to match prior hard-coded
    // behaviour; settable through [updatePort] from the UI.
    var port by mutableStateOf(9090)
        private set

    fun updatePort(p: Int) { port = p }

    // #74: the MJPEG preview endpoint is a *separate* service from the
    // configurable control socket. The Polaris serves it on a fixed 8080;
    // sending the stream request to the control port (9090) makes the
    // firmware log `unkown msg:GET /?action=stream` and consume an extra
    // control client. Kept as its own field so callers (e.g. VRActivity's
    // intent extras) can read the preview endpoint without confusing it
    // with [port].
    var previewPort by mutableStateOf(8080)
        private set

    fun updatePreviewPort(p: Int) { previewPort = p }

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

    /** Typed evidence for the Bluetooth wake pulse; it is not Wi-Fi/control readiness. */
    var bluetoothWakeState by mutableStateOf(BluetoothWakeState())
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

    /** 286 — camera-attachment info (manufacturer/model/state). Read once on
     * connect and refreshed only on explicit user request to avoid a periodic
     * second-client camera query while Benro Connect is active.
     *  so the Camera pane can show "no camera detected" (state -5) instead of a
     *  wall of "unavailable" steppers, and the Preview pane can explain a dead
     *  MJPEG stream. Live-verified 2026-09-16: with no camera the firmware
     *  reports `manufacturer:none;model:none;state:-5` and the 8080 stream
     *  returns HTTP 200 but zero JPEG frames (issue #61). */
    var cameraAttachment by mutableStateOf<CameraAttachment?>(null)
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
     * Last capture lifecycle event reported by code 264. Code 266 is deliberately
     * not queried: live firmware identifies it as a white-balance/configuration
     * read, not capture state (issue #90).
     */
    var captureState by mutableStateOf<CommandTable.CaptureState?>(null)
        private set

    /**
     * #60: the capture lifecycle as a state machine, distinct from the raw
     * [captureState] poll value. The K-3 III capture path is asynchronous —
     * the shutter request is acknowledged in ~12 ms, the camera reports a
     * transient negative state (e.g. `-1005`) around 2 s, goes idle at ~3 s,
     * and the final image lands 3–4 s after the shutter. Treating the first
     * response as completion (the pre-fix behaviour) made the UI flash a
     * false "failed" while the shot was actually succeeding.
     *
     * Completion requires two correlated, post-request signals: a 264 lifecycle
     * event and a positive 773 file event (non-empty `path`). Some firmware
     * emits SP_ADD_FILE with no `size`, so size is useful telemetry but is not
     * part of the portable completion contract. Missing
     * evidence becomes [OutcomeUnknown], never optimistic success.
     *
     * The shutter is debounced while any non-[Idle] phase is active: a second
     * press is ignored (never auto-retried) so a possibly-successful release
     * can't be doubled.
     */
    data class CapturePublication(
        val path: String,
        val type: Int?,
        val sizeBytes: Long?,
    )

    enum class CaptureTimelineStage(val label: String) {
        REQUESTED("Requested"),
        ACTIVE("Camera active"),
        PUBLISHED("File published"),
        CONFIRMED("Confirmed"),
        UNKNOWN("Outcome unknown"),
        FAILED("Failed"),
    }

    data class CaptureTimelineEntry(
        val stage: CaptureTimelineStage,
        val detail: String,
    )

    enum class CaptureKind {
        NORMAL,
        BULB,
    }

    /** The owner of the current release, so native Bulb stop cannot cross into Astro WebUI. */
    enum class CaptureSource {
        NATIVE,
        ASTRO_WEB_UI,
    }

    sealed interface CapturePhase {
        data object Idle : CapturePhase
        data object Requested : CapturePhase
        data object Busy : CapturePhase
        data object Completed : CapturePhase
        data class OutcomeUnknown(val reason: String) : CapturePhase
        data class Failed(val reason: String) : CapturePhase
    }

    /** The requested shutter kind is independent from lifecycle/busy state. */
    var captureKind by mutableStateOf<CaptureKind?>(null)
        private set

    var captureSource by mutableStateOf<CaptureSource?>(null)
        private set

    /** Set only after the native 264/state:1 reply echoes acceptance. */
    private var nativeBulbCommandAccepted by mutableStateOf(false)

    /** Native Bulb only: Astro WebUI has a separate stop route and must not use 264/state:0 here. */
    val nativeBulbCaptureActive: Boolean
        get() = captureKind == CaptureKind.BULB &&
            captureSource == CaptureSource.NATIVE &&
            nativeBulbCommandAccepted

    var capturePhase by mutableStateOf<CapturePhase>(CapturePhase.Idle)
        private set

    /** Last file publication confirmed by the correlated capture lifecycle. */
    var lastCapturePublication by mutableStateOf<CapturePublication?>(null)
        private set

    /** Bounded, ordered evidence trail for the current/last capture workflow. */
    var captureTimeline by mutableStateOf<List<CaptureTimelineEntry>>(emptyList())
        private set

    private val supportBundleExportController = SupportBundleExportController(supportBundleExportSink)
    var supportBundleExportState by mutableStateOf<SupportBundleExportState>(SupportBundleExportState.Idle)
        private set
    private var supportBundleExportJob: Job? = null

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
    // Demo-only SLOW owner. It uses the same exclusive lease, scheduler and
    // dead-man renewal contract as the future live adapter, but its transport
    // records logical commands only and never emits a firmware frame.
    private val demoSlowMotionTransport = DemoSlowMotionTransport()
    /**
     * The firmware-bound adapter is present as a qualification-gated seam, but
     * the current immutable capability record is unqualified. The selector
     * below keeps Demo and a future qualified session on the same controller
     * and owner lifecycle without allowing a feature flag to open the route.
     */
    private val qualifiedSlowMotionTransport = QualifiedSlowMotionTransport(
        capability = slowMotionCapabilityRecord,
    ) { frame ->
        val activeSession = session
        if (activeSession == null) {
            SlowMotionTransportResult.Unknown("mount session unavailable for SLOW transport")
        } else {
            try {
                activeSession.sendOnly(frame.code, frame.payload)
                SlowMotionTransportResult.Accepted
            } catch (error: Throwable) {
                SlowMotionTransportResult.Unknown(
                    "SLOW frame dispatch failed: " +
                        (error.message ?: error::class.simpleName ?: "unknown error"),
                )
            }
        }
    }
    private val slowMotionTransport = object : SlowMotionTransport {
        override suspend fun apply(command: SlowScheduleCommand): SlowMotionTransportResult =
            if (demoMode) demoSlowMotionTransport.apply(command)
            else qualifiedSlowMotionTransport.apply(command)

        override suspend fun stop(reason: MotionStopReason): MotionStopResult =
            if (demoMode) demoSlowMotionTransport.stop(reason)
            else qualifiedSlowMotionTransport.stop(reason)
    }
    /**
     * Demo-only fine-rate transport. It accepts logical requests and never
     * has access to a MountSession, so the rehearsal cannot emit a firmware
     * frame accidentally.
     */
    private val demoFineRateTransport = DemoFineRateTransport()
    private val demoFineRateIdentity = RateCapabilityIdentity(
        firmwareId = "openpolaris-demo",
        gimbalPayloadSha256 = "demo-only-no-payload",
        profileId = "fine-rate-rehearsal-v1",
    )
    private val demoFineRateCapability = RateAdjustCapability(
        identity = demoFineRateIdentity,
        qualified = true,
        units = RateUnits.DEGREES_PER_SECOND,
        bounds = RateAxis.entries.associateWith { RateAxisBounds(-1.0, 1.0) },
    )
    /**
     * Source-aligned duration jog transport. 513-516 use time:Nms for a
     * bounded beat and state:0 on the same code for an explicit stop.
     * sendOnly is used so a failed write becomes an unknown stop state rather
     * than a silent successful command.
     */
    private val fastJogTransport = object : FastJogTransport {
        override suspend fun send(code: Int, durationMs: Int): FastJogSendResult {
            val activeSession = session ?: return FastJogSendResult.Rejected
            return try {
                activeSession.sendOnly(code, "time:" + durationMs + "ms;")
                FastJogSendResult.Sent
            } catch (error: Throwable) {
                FastJogSendResult.Unknown(
                    "fast jog dispatch failed: " + (error.message ?: error::class.simpleName ?: "unknown error"),
                )
            }
        }

        override suspend fun stop(code: Int, reason: MotionStopReason): MotionStopResult {
            val activeSession = session ?: return MotionStopResult.Unknown(
                "mount session unavailable for fast jog stop",
            )
            return try {
                activeSession.sendOnly(code, "state:0;")
                MotionStopResult.Stopped
            } catch (error: Throwable) {
                MotionStopResult.Unknown(
                    "fast jog stop failed: " + (error.message ?: error::class.simpleName ?: "unknown error"),
                )
            }
        }
    }
    // Keep ownership separate from the SLOW scheduler. The supervisor is the
    // shared boundary that future fast-jog, GoTo and programme adapters must
    // use; a SLOW hold cannot acquire while another owner is active.
    /**
     * Shared physical-motion ownership state for the responsive Motion pane.
     * The state is observable even while the live transport gate is closed;
     * it prevents Demo SLOW feedback from looking like an unowned motor path.
     */
    val motionState: StateFlow<MotionState>
        get() = motionSupervisor.state

    /** True only when the SLOW controller itself owns the faulted recovery state. */
    val slowMotionFaulted: Boolean
        get() = slowMotionStateIsFaulted()

    private fun slowMotionStateIsFaulted(): Boolean =
        demoSlowMotion.state.value is dev.openpolaris.core.domain.SlowMotionState.Faulted

    /** The exact SLOW evidence record used by both the UI and runtime adapter. */
    val slowMotionCapability: SlowMotionCapabilityRecord
        get() = slowMotionCapabilityRecord

    val fastJogState: StateFlow<FastJogState>
        get() = fastJogController.state

    private val motionSupervisor = MotionSupervisor(
        stopTransport = object : MotionStopTransport {
            override suspend fun stop(owner: MotionOwner, reason: MotionStopReason): MotionStopResult =
                when (owner) {
                    MotionOwner.SLOW_MOVE -> slowMotionTransport.stop(reason)
                    MotionOwner.TRACKING_CORRECTION -> when (val result = demoFineRateTransport.stop()) {
                        is RateCommandResult.Accepted -> MotionStopResult.Stopped
                        is RateCommandResult.Rejected -> MotionStopResult.Rejected
                        is RateCommandResult.Unknown -> MotionStopResult.Unknown(result.reason)
                    }
                    MotionOwner.FAST_JOG -> {
                        val code = fastJogController.activeCode
                        if (code == null) {
                            MotionStopResult.Unknown("fast jog axis is not available for stop")
                        } else {
                            fastJogTransport.stop(code, reason)
                        }
                    }
                    MotionOwner.ALIGNMENT -> MotionStopResult.Stopped
                    MotionOwner.GOTO -> {
                        val activeSession = session
                        if (activeSession == null) {
                            MotionStopResult.Unknown("mount session unavailable for GoTo stop")
                        } else {
                            try {
                                GoToController(
                                    activeSession,
                                    controller ?: TrackingController(activeSession),
                                ).cancelGoto()
                                MotionStopResult.Stopped
                            } catch (error: Throwable) {
                                MotionStopResult.Unknown(
                                    "GoTo stop failed: ${error.message ?: error::class.simpleName ?: "unknown error"}",
                                )
                            }
                        }
                    }
                    else -> MotionStopResult.Unknown(
                        "no live stop adapter for " + owner.name,
                    )
                }

            override suspend fun stop(lease: MotionLease, reason: MotionStopReason): MotionStopResult =
                if (lease.owner == MotionOwner.FAST_JOG) {
                    val code = fastJogController.activeCode
                    if (code == null) {
                        MotionStopResult.Unknown("fast jog axis is not available for stop")
                    } else {
                        fastJogTransport.stop(code, reason)
                    }
                } else {
                    stop(lease.owner, reason)
                }
        },
    )
    private val fastJogController: FastJogController = FastJogController(
        transport = fastJogTransport,
        motionSupervisor = motionSupervisor,
    )
    private var fastJogLease: FastJogLease? = null
    private var fastJogJob: Job? = null
    private val demoSlowMotion = SlowMotionController(
        transport = slowMotionTransport,
        planner = SlowMotionPlanner(SlowMotionCalibration.REFERENCE),
        motionSupervisor = motionSupervisor,
    )
    private var demoSlowMotionLease: SlowMotionLease? = null
    private var demoSlowMotionJob: Job? = null
    private var demoSlowMotionStopJob: Job? = null
    private val demoFineRate = SafeRateAdjustController(demoFineRateTransport)
    private var demoFineRateLease: MotionLease? = null
    private var demoFineRateJob: Job? = null
    private var demoFineRateRehearsalEvidence by mutableStateOf(false)
    private var demoFineRateVersion = 0L
    val fineRateDemoState: StateFlow<RateAdjustState>
        get() = demoFineRate.state
    val fineRateDemoEvidenceArmed: Boolean
        get() = demoFineRateRehearsalEvidence
    var slowMotionDemoState by mutableStateOf(SlowMotionDemoSnapshot())
        private set
    /**
     * Persistent multi-star alignment session (code 530). Owned by the VM so
     * the firmware receives a monotonically increasing `num` index across
     * submissions — constructing a fresh [AlignmentController] per button
     * press was the repeated-`num:0` defect (ASTRO-WORKFLOW-HANDOVER §1).
     * Created in [connect] / [connectDemo], cleared in [disconnect].
     */
    private var alignmentController: AlignmentController? = null
    private var alignmentMotionLease: MotionLease? = null
    private var alignmentJob: Job? = null
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
    private var captureEventJob: Job? = null
    // 286 camera-attachment poll (see [startCameraAttachmentPolling]).
    private var cameraAttachmentPollJob: Job? = null
    // Dynamic INFO reads are session-owned so a late camera read cannot
    // repopulate a replacement mount session after disconnect/reconnect.
    private var cameraRefreshJob: Job? = null
    // Explicit source-aligned task-3 camera reads are separate from the
    // dynamic index registry so an unavailable auxiliary route cannot erase
    // the primary exposure options.
    private var cameraAuxiliaryRefreshJob: Job? = null
    // #60: bounded watchdog for an in-flight capture. Started by [capture],
    // cancelled when the capture-event observer correlates a lifecycle + file
    // event (shot done) or when the phase reaches a terminal state. If it
    // fires, the capture is reported as Failed("timeout") instead of hanging
    // on Busy.
    private var captureWatchdogJob: Job? = null
    // User-triggered capture dispatch is session-owned. Without an explicit
    // handle, a queued Compose action could repopulate Requested after
    // disconnect has already cleared the capture evidence.
    private var captureDispatchJob: Job? = null
    private var bulbStopJob: Job? = null
    private var astroWebUiCaptureJob: Job? = null
    private var astroWebUiBulbStopJob: Job? = null
    /** Shared lifecycle + publication correlation for single shots and programmes. */
    private val captureEvidence = CaptureEvidenceGate()
    private var pendingCapturePublication: CapturePublication? = null
    private var programmeConfirmation: CompletableDeferred<CaptureEvidenceGate.Publication?>? = null
    private var observationProgrammeActive = false
    private var sessionTearingDown = false
    // §6: true while an intervalometer sequence is driving captures. The
    // capture-event observer uses this to route a correlated completion into
    // [intervalometer].markShotDone() so the engine advances to the next shot
    // instead of stalling on its in-flight shutter.
    private var sequenceActive = false
    private var captureWorkloadsSuspended = false
    private var cameraPollingSuspended = false
    private var restorePreviewAfterCapture = false
    // The simulated mount (only set in demo mode). Held so disconnect()
    // can cancel its private reader scope.
    private var demoSim: SimulatedMount? = null

    // Live preview of the camera MJPEG stream. Independent of the control
    // socket so a slow preview frame can never block the mount poll loop.
    // Decoded JPEGs land in [previewFrame] on Dispatchers.Default.
    val preview = PreviewController(
        transportFactory = previewTransportFactory,
        parent = scope.coroutineContext[Job],
        ioDispatcher = previewIoDispatcher ?: kotlinx.coroutines.Dispatchers.Default,
    )
    val previewState: StateFlow<PreviewController.State> get() = preview.state
    var previewFrame by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
        private set
    // Derived locally from the same decoded frame that is rendered. This is
    // presentation state only; it never implies camera focus or mount health.
    var previewPixelAnalysis by mutableStateOf<PreviewPixelAnalysis?>(null)
        private set
    /** Wall-clock receipt of the currently displayed decoded preview frame. */
    var previewFrameReceivedAtMs by mutableStateOf<Long?>(null)
        private set
    // Pixel reads can block on a GPU-backed bitmap, so analysis has its own
    // cancellable job and never delays the control-socket owner.
    private var previewAnalysisJob: Job? = null
    // Preview has one owner (the active Preview workflow). Keep a single
    // collector for that owner so reopening the pane cannot accumulate
    // duplicate frame decoders or competing transport lifecycles.
    private var previewFrameJob: Job? = null
    private var previewRequested = false

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
    // Bridge and BT wake operations are session-owned too. Keeping explicit
    // handles prevents teardown/reconnect from leaving host work publishing
    // into a new session after the old UI surface has gone away.
    private var connectWifiJob: Job? = null
    private var wakeJob: Job? = null
    private var demoConnectJob: Job? = null
    private var saveMarkerJob: Job? = null

    /**
     * Wall-clock provider for the marker-age calculation. Overridable from
     * tests so `ReconnectPrompt.ageMs` is deterministic regardless of the
     * real system clock. Production callers should not touch this.
     */
    internal var nowMs: () -> Long = { dev.openpolaris.core.domain.currentEpochMillis() }

    /**
     * Typed endpoint evidence consumed by the Mission summary and future
     * connection workspace. Control and preview are updated independently;
     * a successful control handshake never claims that the MJPEG stream is
     * healthy.
     */
    var endpointHealth by mutableStateOf(EndpointHealth.disconnected())
        private set

    private fun endpointProfile(): EndpointProfile? = runCatching {
        EndpointProfile(
            id = if (demoMode) "demo" else "current-mount",
            label = if (demoMode) "Demo mount" else "Current mount",
            kind = when {
                demoMode -> EndpointKind.DEMO
                connectionMode == ConnectionMode.HOME_LAN -> EndpointKind.HOME_LAN
                else -> EndpointKind.DIRECT_AP
            },
            address = EndpointAddress(host, controlPort = port, previewPort = previewPort),
        )
    }.getOrNull()

    private fun setEndpointHealth(
        phase: EndpointPhase,
        control: EndpointService,
        preview: EndpointService,
        failure: String? = null,
    ) {
        endpointHealth = EndpointHealth(
            profile = endpointProfile(),
            phase = phase,
            control = control,
            preview = preview,
            phaseSinceMs = nowMs().coerceAtLeast(0L),
            lastFailure = failure,
        )
    }

    private fun setPreviewHealth(service: EndpointService, failure: String? = null) {
        val current = endpointHealth
        if (current.profile == null) return
        endpointHealth = current.copy(
            phase = when {
                failure != null -> EndpointPhase.DEGRADED
                current.phase == EndpointPhase.FAILED -> EndpointPhase.FAILED
                else -> current.phase
            },
            preview = service,
            phaseSinceMs = nowMs().coerceAtLeast(0L),
            lastFailure = failure ?: current.lastFailure,
        )
    }

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
        val markerSession = session
        saveMarkerJob?.cancel()
        saveMarkerJob = scope.launch {
            try {
                val result = withContext(ioDispatcher) { sessionStore.write(marker) }
                if (saveMarkerJob === currentCoroutineContext()[Job] &&
                    session === markerSession &&
                    !sessionTearingDown &&
                    result.isFailure
                ) {
                    // Best-effort: a failed write does not break the live
                    // session, but the user should know the reconnect prompt
                    // will not appear next launch.
                    statusMessage = "Connected (could not save session: " +
                        (result.exceptionOrNull()?.message ?: "unknown")
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                if (saveMarkerJob === currentCoroutineContext()[Job]) {
                    saveMarkerJob = null
                }
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
        stopDemoSlowMotionForDisconnect()
        demoMode = false
        slowMotionDemoState = SlowMotionDemoSnapshot()
        setEndpointHealth(
            phase = EndpointPhase.CONTROL_CHECKING,
            control = EndpointService.UNKNOWN,
            preview = EndpointService.UNKNOWN,
        )
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
        intervalometer = createIntervalometer()
        observationExecutor = createObservationProgrammeExecutor()
        alignmentController = AlignmentController(s)
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
                    setEndpointHealth(
                        phase = EndpointPhase.READY,
                        control = EndpointService.REACHABLE,
                        preview = EndpointService.UNKNOWN,
                    )
                    // 3e E2: catch any throw from the post-connect bootstrap
                    // (saveMarker / startPolling / startCaptureEventObserver /
                    // camera attachment poll) so a failing setup step surfaces
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
                        startCaptureEventObserver(s)
                        startCameraAttachmentPolling(s)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        // Make sure the half-built session is torn down so a
                        // retry starts from a clean slate. The next connect()
                        // call already calls disconnect() at the top, but a
                        // successful-then-failed bootstrap means we'd otherwise
                        // keep polling and streaming from a session whose
                        // bootstrap never finished.
                        disconnect()
                        setEndpointHealth(
                            phase = EndpointPhase.FAILED,
                            control = EndpointService.REACHABLE,
                            preview = EndpointService.UNKNOWN,
                            failure = e.message ?: e::class.simpleName ?: "post-connect setup failed",
                        )
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
                    setEndpointHealth(
                        phase = EndpointPhase.FAILED,
                        control = EndpointService.UNREACHABLE,
                        preview = EndpointService.UNKNOWN,
                        failure = reason ?: "control connection failed",
                    )
                    val prefix = pendingSaveFailure?.let { saveMsg ->
                        "$saveMsg — and could not reach $host."
                    } ?: "Could not reach $host"
                    statusMessage = if (reason != null) "$prefix — $reason" else "$prefix. Try Demo mode."
                }
            } catch (e: CancellationException) {
                throw e
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
                val failure = e.message ?: e::class.simpleName ?: "unknown connection failure"
                setEndpointHealth(
                    phase = EndpointPhase.FAILED,
                    control = EndpointService.UNREACHABLE,
                    preview = EndpointService.UNKNOWN,
                    failure = failure,
                )
                statusMessage = "Connect failed: $failure"
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
        if (_reconnecting.value || connectWifiJob?.isActive == true) return
        // The full bridge bring-up includes its own wake pulse. If the user
        // started the standalone Wake action first, hand status ownership to
        // this operation instead of letting both host jobs publish progress.
        val activeWake = wakeJob
        if (activeWake?.isActive == true) {
            activeWake.cancel()
            wakeJob = null
            _waking.value = false
            bluetoothWakeState = BluetoothWakeState(
                BluetoothWakePhase.CANCELLED,
                "Standalone Bluetooth wake cancelled; the PC bridge now owns the attempt",
            )
        }
        _reconnecting.value = true
        bluetoothWakeState = BluetoothWakeState(
            BluetoothWakePhase.REQUESTED,
            "PC bridge may use the Bluetooth wake pulse before establishing Wi-Fi",
        )
        connectWifiJob = scope.launch {
            try {
                setEndpointHealth(
                    phase = EndpointPhase.WAKING,
                    control = endpointHealth.control,
                    preview = endpointHealth.preview,
                )
                statusMessage = "Connecting to mount Wi-Fi…"
                val routeReady = connectWifi { msg -> publishConnectionProgress(msg) }
                if (routeReady) {
                    setEndpointHealth(
                        phase = EndpointPhase.WIFI_READY,
                        control = EndpointService.UNKNOWN,
                        preview = EndpointService.UNKNOWN,
                    )
                } else {
                    val failure = "bridge did not verify the mount route"
                    setEndpointHealth(
                        phase = EndpointPhase.FAILED,
                        control = EndpointService.UNREACHABLE,
                        preview = EndpointService.UNKNOWN,
                        failure = failure,
                    )
                    statusMessage = "Wi-Fi bridge failed: $failure"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val failure = e.message ?: e::class.simpleName ?: "Wi-Fi bridge failed"
                setEndpointHealth(
                    phase = EndpointPhase.FAILED,
                    control = EndpointService.UNREACHABLE,
                    preview = EndpointService.UNKNOWN,
                    failure = failure,
                )
                statusMessage = "Wi-Fi bridge failed: $failure"
            } finally {
                if (connectWifiJob === currentCoroutineContext()[Job]) {
                    connectWifiJob = null
                    _reconnecting.value = false
                }
            }
        }
    }

    /**
     * Tear down the segregated Wi-Fi bridge (policy route → NM down → release
     * the retained BLE GATT wake link). The reverse of [connectWifi]. Each
     * phase posts to [statusMessage] as it runs. No-op when the host has no
     * bridge implementation (the default no-op lambda), so Android callers can
     * invoke it harmlessly.
     */
    fun teardownBridge() {
        // Capture the injected lambda property up front. Inside the launch block
        // `this` is the coroutine-scope receiver, so a bare `teardownBridge`
        // would resolve to this method (infinite recursion) rather than the
        // injected lambda property.
        val teardown = teardownBridge
        scope.launch {
            try {
                statusMessage = "Tearing down mount Wi-Fi…"
                teardown { msg -> statusMessage = msg }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                statusMessage = "Wi-Fi bridge teardown failed: ${e.message ?: e::class.simpleName}"
            }
        }
    }

    private fun publishConnectionProgress(message: String) {
        statusMessage = message
        val lower = message.lowercase()
        val current = bluetoothWakeState.phase
        val phase = when {
            lower.startsWith("woke ") ||
                lower.contains("wake complete") ||
                lower.contains("pulse opened") ||
                lower.contains("pulse succeeded") -> BluetoothWakePhase.SUCCEEDED
            lower.contains("bt wake failed") ||
                lower.contains("wake failed") ||
                (lower.contains("bluetooth") && lower.contains("fail")) -> BluetoothWakePhase.FAILED
            lower.contains("gatt") ||
                (lower.contains("bluetooth") && lower.contains("connect")) -> BluetoothWakePhase.CONNECTING
            lower.contains("scan") && (
                current == BluetoothWakePhase.REQUESTED ||
                    current == BluetoothWakePhase.DISCOVERING
                ) -> BluetoothWakePhase.DISCOVERING
            lower.contains("bluetooth") && current == BluetoothWakePhase.IDLE -> BluetoothWakePhase.REQUESTED
            else -> return
        }
        bluetoothWakeState = BluetoothWakeState(phase, message)
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
        bluetoothWakeState = BluetoothWakeState(
            BluetoothWakePhase.REQUESTED,
            "Bluetooth wake pulse requested",
        )
        setEndpointHealth(
            phase = EndpointPhase.WAKING,
            control = endpointHealth.control,
            preview = endpointHealth.preview,
        )
        wakeJob = scope.launch {
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
                        publishConnectionProgress(msg)
                    }
                }
                if (!_reconnecting.value &&
                    reconnectGeneration == wakeStartGeneration
                ) {
                    publishConnectionProgress("Waking gimbal over Bluetooth…")
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
                    publishConnectionProgress("Wake complete — try Connect")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (!_reconnecting.value && reconnectGeneration == wakeStartGeneration) {
                    val failure = e.message ?: e::class.simpleName ?: "Bluetooth wake failed"
                    setEndpointHealth(
                        phase = EndpointPhase.FAILED,
                        control = EndpointService.UNREACHABLE,
                        preview = EndpointService.UNKNOWN,
                        failure = failure,
                    )
                    publishConnectionProgress("BT wake failed: $failure")
                }
            } finally {
                if (wakeJob === currentCoroutineContext()[Job]) {
                    wakeJob = null
                    _waking.value = false
                }
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
        publishConnectionProgress(msg)
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
        setEndpointHealth(
            phase = EndpointPhase.CONTROL_CHECKING,
            control = EndpointService.UNKNOWN,
            preview = EndpointService.UNKNOWN,
        )
        val sim = SimulatedMount()
        // Hold a reference so disconnect() can cancel the private reader
        // scope (otherwise the long-lived reader coroutine keeps the
        // SupervisorJob alive past the lifetime of the AppViewModel).
        demoSim = sim
        session = sim.session
        controller = TrackingController(sim.session)
        cameraController = CameraController(sim.session)
        intervalometer = createIntervalometer()
        observationExecutor = createObservationProgrammeExecutor()
        alignmentController = AlignmentController(sim.session)
        wireHelpers(sim.session)

        startAutoLevel(sim.session)
        demoConnectJob = scope.launch {
            try {
                if (!sim.session.connect()) {
                    throw IllegalStateException("simulated mount handshake failed")
                }
                demoFineRateRehearsalEvidence = false
                demoFineRate.setCapability(demoFineRateCapability)
                updateDemoFineRateContext()
                // SimulatedMount's connect() always returns true; treat as
                // success for the marker-save path. (A future slice that
                // simulates intermittent failure should gate this on the
                // return value the same way `connect()` does.)
                saveMarker()
                startCaptureEventObserver(sim.session)
                startCameraAttachmentPolling(sim.session)
                refreshCamera()
                setEndpointHealth(
                    phase = EndpointPhase.READY,
                    control = EndpointService.REACHABLE,
                    preview = EndpointService.UNREACHABLE,
                )
                statusMessage = "Demo mode (simulated mount)"
                if (startPolling) {
                    startPolling(sim.session)
                }
                // No preview in demo mode: there is no MJPEG endpoint in the
                // simulator. PreviewController stays Idle, which the pane
                // renders as "Stream unavailable".
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // 3e E2: demo mode should never really throw (the
                // simulator is in-process) but if a future slice adds a
                // simulated intermittent failure or a saveMarker() I/O
                // regression sneaks in, surface the cause instead of
                // silently leaving the user with no status feedback.
                val failure = e.message ?: e::class.simpleName ?: "unknown demo failure"
                setEndpointHealth(
                    phase = EndpointPhase.FAILED,
                    control = EndpointService.UNREACHABLE,
                    preview = EndpointService.UNKNOWN,
                    failure = failure,
                )
                statusMessage = "Demo mode failed: $failure"
            } finally {
                if (demoConnectJob === currentCoroutineContext()[Job]) {
                    demoConnectJob = null
                }
            }
        }
    }

    fun disconnect() {
        // Host-side wake/bridge work belongs to the same session boundary as
        // the control socket. Cancel it before clearing endpoint evidence so
        // a suspended callback cannot publish stale progress into the next
        // connection. The host orchestrator owns any lower-level cleanup.
        val activeConnectWifiJob = connectWifiJob
        activeConnectWifiJob?.cancel()
        connectWifiJob = null
        if (activeConnectWifiJob != null) {
            _reconnecting.value = false
        }
        val wakeWasActive = wakeJob?.isActive == true || _waking.value
        wakeJob?.cancel()
        wakeJob = null
        _waking.value = false
        if (wakeWasActive) {
            bluetoothWakeState = BluetoothWakeState(
                BluetoothWakePhase.CANCELLED,
                "Bluetooth wake cancelled by disconnect",
            )
        }
        stopDemoSlowMotionForDisconnect()
        stopDemoFineRateForDisconnect()
        val activeFastJogLease = fastJogLease
        fastJogLease = null
        fastJogJob?.cancel()
        fastJogJob = null
        if (activeFastJogLease != null) {
            runBlocking { fastJogController.release(activeFastJogLease) }
        }
        sessionTearingDown = true
        saveMarkerJob?.cancel()
        saveMarkerJob = null
        programmeConfirmation?.complete(null)
        programmeConfirmation = null
        observationExecutor?.abandon("session disconnected")
        observationExecutor = null
        observationProgrammeActive = false
        observationProgrammeState = dev.openpolaris.core.domain.ObservationProgrammeExecutor.State.Idle
        pollJob?.cancel()
        captureEventJob?.cancel()
        cameraAttachmentPollJob?.cancel()
        cameraRefreshJob?.cancel()
        cameraRefreshJob = null
        cameraAuxiliaryRefreshJob?.cancel()
        cameraAuxiliaryRefreshJob = null
        // 3c.5: if a reconnect was in flight, tear it down too so the
        // spinner does not stay up after the user navigates away.
        connectJob?.cancel()
        // Tear down the simulated mount's private reader scope so the
        // long-lived reader coroutine does not keep the dispatcher alive
        // past the lifetime of this view model.
        demoConnectJob?.cancel()
        demoConnectJob = null
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
        previewRequested = false
        previewFrameJob?.cancel()
        previewFrameJob = null
        previewAnalysisJob?.cancel()
        previewAnalysisJob = null
        runCatching { preview.stop() }
        previewFrame = null
        previewPixelAnalysis = null
        previewFrameReceivedAtMs = null
        endpointHealth = EndpointHealth.disconnected()

        alignmentJob?.cancel()
        alignmentJob = null
        alignmentMotionLease?.let { lease ->
            runBlocking { motionSupervisor.stop(lease, MotionStopReason.DISCONNECT) }
        }
        alignmentMotionLease = null
        val activeGotoLease = gotoMotionLease
        if (activeGotoLease != null) {
            runBlocking { motionSupervisor.stop(activeGotoLease, MotionStopReason.DISCONNECT) }
        }
        // A GoTo coroutine can still be queued before it acquires its shared
        // lease. Cancel it unconditionally; otherwise disconnect would leave
        // a stale 519 write and an in-progress UI state behind.
        slewJob?.cancel()
        slewJob = null
        gotoMotionLease = null
        slewInProgress = false
        session?.let { runBlocking { it.disconnect() } }
        session = null
        controller = null
        cameraController = null
        // §6: a sequence in flight at disconnect time is abandoned — stop the
        // engine (cancels its shot loop), drop the reference, and reset the
        // mirrored state so a stale "Running" doesn't linger into the next
        // session. The engine's job lives on [scope], so stop() before nulling.
        intervalometer?.stop()
        intervalometer = null
        sequenceActive = false
        sequenceState = dev.openpolaris.core.domain.IntervalometerController.State.Idle
        alignmentController = null
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
        cameraAttachment = null
        cameraReadbackAtMs = null
        cameraReadbackStatuses = emptyMap()
        cameraAuxiliaryReadback = CameraAuxiliaryReadback()
        cameraAuxiliaryReadbackAtMs = null
        focusStatus = null
        videoRecording = null
        deviceInfo = null
        temperature = null
        captureState = null
        // #60: a capture in flight at disconnect time is abandoned — drop the
        // watchdog and reset the phase so a stale "Busy" / "Failed" doesn't
        // linger into the next session.
        captureDispatchJob?.cancel()
        captureDispatchJob = null
        bulbStopJob?.cancel()
        bulbStopJob = null
        astroWebUiCaptureJob?.cancel()
        astroWebUiCaptureJob = null
        astroWebUiBulbStopJob?.cancel()
        astroWebUiBulbStopJob = null
        captureWatchdogJob?.cancel()
        captureWatchdogJob = null
        capturePhase = CapturePhase.Idle
        captureKind = null
        captureSource = null
        nativeBulbCommandAccepted = false
        captureEvidence.clear()
        pendingCapturePublication = null
        lastCapturePublication = null
        captureTimeline = emptyList()
        astroWebUiCaptureActive = false
        astroWebUiCaptureDispatching = false
        astroWebUiBulbActive = false
        astroWebUiBulbStopQueued = false
        astroWebUiCaptureStatus = null
        fileList = null
        astroMediaJob?.cancel()
        astroMediaJob = null
        astroMediaItems = null
        astroMediaPreview = null
        astroMediaCategory = null
        astroMediaState = AstroMediaState.Idle
        astroMediaExportJob?.cancel()
        astroMediaExportController.reset()
        astroMediaExportState = AstroMediaExportState.Idle
        supportBundleExportJob?.cancel()
        supportBundleExportJob = null
        supportBundleExportController.reset()
        supportBundleExportState = SupportBundleExportState.Idle
        astroWebUiStatusJob?.cancel()
        astroWebUiStatusJob = null
        astroWebUiStatusPollJob?.cancel()
        astroWebUiStatusPollJob = null
        astroWebUiStatus = AstroWebUiStatusState.Idle
        astroWebUiGuideJob?.cancel()
        astroWebUiGuideJob = null
        astroWebUiGuidePollJob?.cancel()
        astroWebUiGuidePollJob = null
        astroWebUiGuide = AstroWebUiGuideUiState.Idle
        astroWebUiAutofocusJob?.cancel()
        astroWebUiAutofocusJob = null
        astroWebUiAutofocusPollJob?.cancel()
        astroWebUiAutofocusPollJob = null
        astroWebUiAutofocus = AstroWebUiAutofocusUiState.Idle
        astroWebUiFocalJob?.cancel()
        astroWebUiFocalJob = null
        astroWebUiFocal = AstroWebUiFocalUiState.Idle
        astroWebUiLimitsJob?.cancel()
        astroWebUiLimitsJob = null
        astroWebUiLimits = AstroWebUiLimitsUiState.Idle
        astroWebUiKeepWifiJob?.cancel()
        astroWebUiKeepWifiJob = null
        astroWebUiKeepWifi = AstroWebUiKeepWifiUiState.Idle
        astroWebUiWifiJob?.cancel()
        astroWebUiWifiJob = null
        astroWebUiWifi = AstroWebUiWifiUiState.Idle
        astroWebUiLinkJob?.cancel()
        astroWebUiLinkJob = null
        astroWebUiLink = AstroWebUiLinkUiState.Idle
        astroWebUiTargetJob?.cancel()
        astroWebUiTargetJob = null
        astroWebUiTarget = AstroWebUiTargetUiState.Idle
        astroWebUiSolveJob?.cancel()
        astroWebUiSolveJob = null
        astroWebUiSolveControlJob?.cancel()
        astroWebUiSolveControlJob = null
        astroWebUiSolveControl = AstroWebUiSolveControlUiState.Idle
        astroWebUiSolvePollJob?.cancel()
        astroWebUiSolvePollJob = null
        astroWebUiSolve = AstroWebUiSolveUiState.Idle
        astroWebUiRunStateJob?.cancel()
        astroWebUiRunStateJob = null
        astroWebUiRunStatePollJob?.cancel()
        astroWebUiRunStatePollJob = null
        astroWebUiRunState = AstroWebUiRunStateUiState.Idle
        astroWebUiSessionControlJob?.cancel()
        astroWebUiSessionControlJob = null
        astroWebUiSessionControl = AstroWebUiSessionControlState.Idle
        astroWebUiSiteJob?.cancel()
        astroWebUiSiteJob = null
        astroWebUiSite = AstroWebUiSiteState.Idle
        astroWebUiSiteWriteJob?.cancel()
        astroWebUiSiteWriteJob = null
        astroWebUiSiteWrite = AstroWebUiSiteWriteState.Idle
        astroWebUiProgrammeJob?.cancel()
        astroWebUiProgrammeJob = null
        astroWebUiProgrammePollJob?.cancel()
        astroWebUiProgrammePollJob = null
        astroWebUiProgrammeControlJob?.cancel()
        astroWebUiProgrammeControlJob = null
        astroWebUiProgrammeStartJob?.cancel()
        astroWebUiProgrammeStartJob = null
        astroWebUiProgramme = AstroWebUiProgrammeState.Idle
        astroWebUiProgrammeStartState = AstroWebUiProgrammeStartState.Idle
        astroWebUiAstroCaptureJob?.cancel()
        astroWebUiAstroCaptureJob = null
        astroWebUiAstroCapture = AstroWebUiAstroCaptureState.Idle
        astroWebUiSunState = AstroWebUiSunState.Idle
        astroWebUiMotionProgrammeState = AstroWebUiMotionProgrammeState.Idle
        captureWorkloadsSuspended = false
        cameraPollingSuspended = false
        restorePreviewAfterCapture = false
        // Tear down the auto-level controller in its own coroutine so we can
        // call the suspending stopAutoLevel() from a non-suspending context.
        // Safe to fire-and-forget: stopAutoLevel only cancels jobs that
        // belong to this VM, and the controller's stop() is idempotent.
        stopAutoLevelAsync()
        _lastSolveResult.value = null
        targetSelected = false
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
                !_waking.value &&
                !wakeWasActive
            ) {
                statusMessage = "Disconnected"
            }
        }
        sessionTearingDown = false
    }

    /**
     * Open the MJPEG preview stream on the current host. Each frame
     * is decoded off the main thread and published to [previewFrame].
     */
    private fun startPreview() {
        // Preview is a separate service from the configurable 9090 control
        // socket. Deliberately use [previewPort] (fixed 8080 on the Polaris),
        // NOT [port]: passing the control port here sends the MJPEG HTTP
        // request to polestar_app, where it is rejected as an unknown control
        // frame (issue #74).
        // Replacing a stream must also replace its frame collector. The
        // transport is independent from the control socket, but the decoder
        // belongs to this Preview workflow and must not outlive it.
        previewFrameJob?.cancel()
        previewFrameJob = null
        previewAnalysisJob?.cancel()
        previewAnalysisJob = null
        previewPixelAnalysis = null
        previewFrameReceivedAtMs = null
        if (demoMode) {
            // Demo preview is deliberately local and synthetic. It exercises
            // the same frame collector and presentation analysis without
            // opening a second network transport or implying camera evidence.
            preview.publishSyntheticForDemo(DEMO_PREVIEW_PNG)
            setPreviewHealth(EndpointService.REACHABLE)
        } else {
            try {
                preview.start(host, previewPort)
            } catch (e: Throwable) {
                // 3e E2: PreviewController.start may throw if the host is
                // unresolvable or the port is closed. Surface as a status
                // message and skip the collector  - the rest of the post-
                // connect setup (polling, capture polling) will still
                // continue. Without this, the connect() success branch
                // catches it at the outer layer, but a cleaner message
                // here helps the user debug a misconfigured port.
                val failure = e.message ?: e::class.simpleName ?: "preview start failed"
                setPreviewHealth(EndpointService.UNREACHABLE, failure)
                statusMessage = "Preview unavailable: $failure"
                return
            }
            setPreviewHealth(EndpointService.UNKNOWN)
        }
        previewFrameJob = scope.launch {
            preview.bytes.collect { jpeg ->
                if (jpeg == null) {
                    previewFrame = null
                    previewAnalysisJob?.cancel()
                    previewAnalysisJob = null
                    previewPixelAnalysis = null
                    previewFrameReceivedAtMs = null
                    return@collect
                }
                setPreviewHealth(EndpointService.REACHABLE)
                previewFrameReceivedAtMs = SystemMillis.now()
                // Drop-on-late: if the user closes the pane, the next
                // decode will simply replace a stale bitmap.
                val decoded = withContext(Dispatchers.Default) {
                    runCatching { decodeJpegToImageBitmap(jpeg) }
                }
                decoded.getOrNull()?.let { image ->
                    previewFrame = image
                    // Do not queue one pixel read per MJPEG frame. If an
                    // analysis is still running, the next frame will be
                    // offered after it completes and the displayed bitmap
                    // remains independent of this optional presentation aid.
                    if (previewAnalysisJob?.isActive != true) {
                        previewAnalysisJob = scope.launch(Dispatchers.Default) {
                            val result = runCatching { PreviewPixelAnalyzer.analyze(image) }.getOrNull()
                            if (isActive) previewPixelAnalysis = result
                        }
                    }
                }
            }
        }

    }

    /**
     * #89: the 284 (mount state) and 517 (gimbal position) polls are issued
     * **concurrently** with a shortened [POLL_REQUEST_TIMEOUT_MS] timeout,
     * instead of sequentially with the 2 s default. With a camera attached the
     * Polaris is constantly busy with PTP/camera I/O and responds slowly to
     * control queries; the pre-fix sequential loop could stall for up to
     * ~5 s per cycle (2 s + 2 s + 1 s delay) of stale UI, and a slow 284
     * response delayed the independent 517 update. Running them in parallel
     * makes the worst case max(t284, t517) instead of the sum, and the 800 ms
     * timeout means a response older than that is already stale at a 1 Hz
     * cadence — the next cycle catches up. `MountSession.request` still
     * serialises the actual socket writes through its internal Mutex, so the
     * two requests never overlap on the wire; only their wait windows overlap.
     */
    private fun startPolling(s: MountSession) {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                if (cameraPollingSuspended) {
                    delay(100)
                    continue
                }
                coroutineScope {
                    launch {
                        when (val r = s.request(284, timeoutMs = POLL_REQUEST_TIMEOUT_MS) { MountState.fromFrame284(it) }) {
                            is MountSession.CmdResult.Ok -> {
                                mount = r.value
                                updateDemoFineRateContext()
                            }
                            is MountSession.CmdResult.Timeout -> {}
                            is MountSession.CmdResult.ProtocolError -> statusMessage = "Error: ${r.message}"
                        }
                    }
                    launch {
                        when (val r = s.request(517, timeoutMs = POLL_REQUEST_TIMEOUT_MS) { GimbalPosition.fromFrame517(it) }) {
                            is MountSession.CmdResult.Ok -> position = r.value
                            else -> {}
                        }
                    }
                }
                delay(
                    PosePollPolicy.delayMs(
                        tracking = mount.tracking == true,
                        motionOwned = motionSupervisor.state.value is MotionState.Owned,
                        slewInProgress = slewInProgress,
                    ),
                )
            }
        }
    }

    /** Observe unsolicited camera/file events without issuing any camera query. */
    private fun startCaptureEventObserver(s: MountSession) {
        captureEventJob?.cancel()
        captureEventJob = scope.launch {
            s.frames.collect { frame -> frame?.let(::onCaptureFrame) }
        }
    }

    private fun appendCaptureTimeline(stage: CaptureTimelineStage, detail: String) {
        captureTimeline = (captureTimeline + CaptureTimelineEntry(stage, detail)).takeLast(20)
    }

    private fun beginCaptureTimeline(detail: String) {
        captureTimeline = listOf(CaptureTimelineEntry(CaptureTimelineStage.REQUESTED, detail))
    }

    private fun onCaptureFrame(frame: ResponseParser.Frame) {
        val active = capturePhase is CapturePhase.Requested || capturePhase is CapturePhase.Busy
        if (!active) return
        when (frame.code) {
            Codes.CAM_CAPTURE -> {
                val state = frame.int("state") ?: return
                captureState = CommandTable.CaptureState(
                    state = state,
                    bulb = frame.int("bulb") ?: 0,
                    c = frame.int("c") ?: -1,
                )
                // Pentax firmware uses -1005 as a transient busy/error edge
                // during a successful capture. It is not terminal evidence.
                // -110, however, is a terminal shutter failure: do not feed it
                // into the positive evidence gate, and never turn a later idle
                // edge into a Bulb stop or a successful capture.
                if (state == -110) {
                    failCaptureFromCameraState(state)
                    return
                }
                // Idle alone is not evidence that this shutter began. Require
                // a post-request non-idle lifecycle state before accepting a
                // subsequent file notification as correlated.
                if (captureEvidence.observeLifecycle(state)) {
                    appendCaptureTimeline(CaptureTimelineStage.ACTIVE, "state $state")
                }
                capturePhase = CapturePhase.Busy
                completeCaptureIfCorrelated()
            }
            Codes.FILE_DOWNLOAD_DATA -> {
                val path = frame["path"]?.trim().orEmpty()
                if (captureEvidence.observePublication(path, frame.int("type"), frame["size"]?.trim()?.toLongOrNull())) {
                    captureEvidence.publication?.let { publication ->
                        pendingCapturePublication = CapturePublication(
                            path = publication.path,
                            type = publication.type,
                            sizeBytes = publication.sizeBytes,
                        )
                        appendCaptureTimeline(CaptureTimelineStage.PUBLISHED, publication.path)
                    }
                    completeCaptureIfCorrelated()
                }
            }
        }
    }

    private fun failCaptureFromCameraState(state: Int) {
        val reason = "camera capture failed: state " + state
        captureWatchdogJob?.cancel()
        captureWatchdogJob = null
        captureEvidence.fail(reason)
        // A sequence/programme must not advance after a terminal camera
        // failure. Stopping the owner also clears its in-flight shutter guard.
        intervalometer?.stop()
        programmeConfirmation?.complete(null)
        observationExecutor?.stop()
        astroWebUiCaptureActive = false
        astroWebUiCaptureDispatching = false
        astroWebUiBulbActive = false
        astroWebUiBulbStopQueued = false
        captureKind = null
        captureSource = null
        nativeBulbCommandAccepted = false
        capturePhase = CapturePhase.Failed(reason)
        appendCaptureTimeline(CaptureTimelineStage.FAILED, reason)
        statusMessage = "Capture failed: state " + state
        restoreCaptureWorkloads()
    }

    private fun completeCaptureIfCorrelated() {
        if (!captureEvidence.isConfirmed) return
        captureWatchdogJob?.cancel()
        val confirmedProgrammePublication = captureEvidence.publication
        pendingCapturePublication?.let { lastCapturePublication = it }
        pendingCapturePublication = null
        appendCaptureTimeline(CaptureTimelineStage.CONFIRMED, "lifecycle + file evidence")
        astroWebUiCaptureActive = false
        astroWebUiCaptureDispatching = false
        astroWebUiBulbActive = false
        astroWebUiBulbStopQueued = false
        captureWatchdogJob = null
        // §6: while a sequence or observation programme is driving captures,
        // this correlated completion releases the current step without
        // restoring preview ownership between steps.
        // confirms the current shot. Release the engine's in-flight shutter so
        // it advances to the next shot (or completes). A single-shot capture
        // has no engine, so markShotDone() is a harmless no-op there.
        if (sequenceActive || observationProgrammeActive) {
            if (sequenceActive) intervalometer?.markShotDone()
            programmeConfirmation?.complete(confirmedProgrammePublication)
            // The programme bridge may not have reached awaitConfirmation yet
            // when both evidence frames arrive. Keep the completed deferred
            // discoverable until awaitObservationConfirmation consumes it;
            // clearing it here creates a false OutcomeUnknown race.
            if (!observationProgrammeActive) programmeConfirmation = null
            // The executor starts the next programme step by calling begin();
            // keep the confirmed evidence intact until its bridge consumes it.
            if (sequenceActive) {
                captureEvidence.resetForNext()
                capturePhase = CapturePhase.Requested
            } else {
                capturePhase = CapturePhase.Completed
            }
        } else {
            capturePhase = CapturePhase.Completed
        }
        statusMessage = "Capture completed"
        captureKind = null
        captureSource = null
        nativeBulbCommandAccepted = false
        if (!sequenceActive) restoreCaptureWorkloads()
    }

    /** Open preview only while the user is in the Preview workflow. */
    fun enterPreview() {
        if (session == null || captureWorkloadsSuspended) return
        // The Preview pane may recompose or be opened repeatedly. Treat the
        // first entry as ownership acquisition and keep it idempotent while
        // the collector is alive; an explicit Error is the one case where a
        // repeated entry is allowed to retry the stream.
        previewRequested = true
        if (previewFrameJob?.isActive == true && preview.state.value !is PreviewController.State.Error) return
        startPreview()
    }

    /** Release this app's preview connection when leaving the Preview workflow. */
    fun leavePreview() {
        previewRequested = false
        previewFrameJob?.cancel()
        previewFrameJob = null
        previewAnalysisJob?.cancel()
        previewAnalysisJob = null
        preview.stop()
        previewFrame = null
        previewPixelAnalysis = null
        previewFrameReceivedAtMs = null
    }

    private suspend fun suspendCaptureWorkloads() {
        if (captureWorkloadsSuspended) return
        captureWorkloadsSuspended = true
        cameraPollingSuspended = true
        // Let a request already inside the 800 ms poll window finish before
        // issuing camera-preview or shutter commands on the shared socket.
        delay(POLL_REQUEST_TIMEOUT_MS + 50)
        restorePreviewAfterCapture = previewRequested && (
            preview.state.value is PreviewController.State.Streaming ||
                preview.state.value is PreviewController.State.Connecting
        )
        previewFrameJob?.cancel()
        previewFrameJob = null
        previewAnalysisJob?.cancel()
        previewAnalysisJob = null
        preview.stop()
        previewFrame = null
        previewPixelAnalysis = null
        previewFrameReceivedAtMs = null
        if (restorePreviewAfterCapture) {
            // Best effort: the transport is already closed, so even a firmware
            // timeout cannot leave a second 8080 owner in this process.
            runCatching { cameraController?.setCameraPreview(false) }
        }
    }

    private fun restoreCaptureWorkloads() {
        if (!captureWorkloadsSuspended) return
        captureWorkloadsSuspended = false
        cameraPollingSuspended = false
        if (restorePreviewAfterCapture) {
            restorePreviewAfterCapture = false
            scope.launch {
                runCatching { cameraController?.setCameraPreview(true) }
                startPreview()
            }
        }
    }

    /**
     * One code-286 snapshot on connect. Repeating it every five seconds made
     * OpenPolaris a continuous second camera client (and differs from the
     * otherwise stable Benro-Connect-only workload). The mount re-detects USB
     * on its own schedule; let the user request a fresh snapshot explicitly.
     */
    private fun startCameraAttachmentPolling(s: MountSession) {
        cameraAttachmentPollJob?.cancel()
        cameraAttachmentPollJob = scope.launch {
            if (!cameraPollingSuspended) {
                when (val r = s.request(Codes.CAM_INFO, timeoutMs = POLL_REQUEST_TIMEOUT_MS) { CommandTable.CAM_INFO.parse!!(it) }) {
                    is MountSession.CmdResult.Ok -> r.value?.let { cameraAttachment = it }
                    else -> {} // Timeout / ProtocolError: keep last good value
                }
            }
        }
    }

    /** Explicitly refresh the camera identity after the user changes its power/USB state. */
    fun refreshCameraAttachment() {
        val s = session ?: return
        if (cameraPollingSuspended) return
        startCameraAttachmentPolling(s)
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

        // Camera parameter opcodes are not sequential GET/SET pairs (#62).
        // Do not send the legacy inferred burst until every descriptor is evidenced.
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

    /**
     * The tracking rate to send with code 531. Defaults to [TrackingRate.SIDEREAL]
     * (the correct rate for star imaging). The Benro app always sends an explicit
     * `speed:` index; the pre-fix UI sent a bare `state:1;` and let the firmware
     * pick its default — the "tracking rate is implicit" gap
     * (docs/ASTRO-WORKFLOW-HANDOVER §5).
     */
    var trackingRate by mutableStateOf(TrackingRate.DEFAULT)

    fun startTracking() = scope.launch {
        if (!stopSlowMotionForModeChange()) return@launch
        controller?.start(trackingRate)
        updateDemoFineRateContext()
    }

    fun stopTracking() = scope.launch {
        val slowStopped = stopSlowMotionForModeChange()
        if (demoMode) stopDemoFineRateNow(MotionStopReason.MODE_CHANGE)
        controller?.stop()
        if (!slowStopped) {
            statusMessage = "Tracking stopped; slow-motion recovery is still required"
        }
        updateDemoFineRateContext()
    }

    fun toggleHalfSpeed(on: Boolean) = scope.launch {
        if (!stopSlowMotionForModeChange()) return@launch
        if (demoMode && on) stopDemoFineRateNow(MotionStopReason.MODE_CHANGE)
        controller?.setHalfSpeed(on)
        updateDemoFineRateContext()
    }
    fun enableAhrs(on: Boolean) = scope.launch { controller?.enableAhrs(on) }
    /** Refresh the simulator's fine-rate safety contract without manufacturing a live solve. */
    private fun updateDemoFineRateContext() {
        val sim = demoSim
        val solveEvidence = demoMode && demoFineRateRehearsalEvidence
        val owner = motionSupervisor.state.value
        val ownerAvailable = owner == MotionState.Idle ||
            (owner is MotionState.Owned && owner.lease == demoFineRateLease)
        demoFineRate.updateContext(
            RateAdjustContext(
                connected = demoMode && sim != null && session != null,
                trackingConfirmed = demoMode && sim?.tracking == true,
                halfSpeedEnabled = demoMode && sim?.halfSpeed == true,
                exclusiveMotionAvailable = ownerAvailable,
                solutionAgeMs = if (solveEvidence) 0L else null,
                solutionConfidence = if (solveEvidence) 0.90 else null,
                solutionRotationDegrees = if (solveEvidence) 0.0 else null,
            ),
        )
    }

    /** Arm only synthetic solve evidence for the simulator rehearsal. */
    fun prepareDemoFineRate() {
        if (!demoMode) {
            statusMessage = "Fine correction rehearsal is available only in Demo mode"
            return
        }
        demoFineRate.setCapability(demoFineRateCapability)
        demoFineRateRehearsalEvidence = true
        updateDemoFineRateContext()
        statusMessage = if (demoSim?.tracking == true) {
            "Demo fine correction ready (synthetic solve evidence; no hardware command)"
        } else {
            "Demo solve rehearsal prepared; start tracking before applying fine correction"
        }
    }

    /** Apply one bounded logical fine-rate request and retain the shared owner lease. */
    fun demoFineRateApply(axis: RateAxis, direction: Int) {
        if (!demoMode) {
            statusMessage = "Fine-rate transport is not qualified for live hardware"
            return
        }
        if (direction !in -1..1 || direction == 0) {
            statusMessage = "Invalid Demo fine-rate direction"
            return
        }
        val amount = 0.1 * direction
        demoFineRateApplyTargets(
            m1Pan = if (axis == RateAxis.M1_PAN) amount else 0.0,
            m2Tilt = if (axis == RateAxis.M2_TILT) amount else 0.0,
            m3Rotation = if (axis == RateAxis.M3_ROTATION) amount else 0.0,
            statusLabel = "${axis.name} ${if (direction > 0) "A" else "B"} · 0.1°/s",
        )
    }

    /**
     * Apply a complete multi-axis logical fine-rate request.
     *
     * Draft values remain in the UI until this method is called. Validation is
     * repeated here before taking the shared motion lease, so malformed or
     * out-of-range mobile input cannot reserve the mount owner.
     */
    fun demoFineRateApplyTargets(
        m1Pan: Double,
        m2Tilt: Double,
        m3Rotation: Double,
        statusLabel: String = "M1 ${formatDemoRate(m1Pan)} · M2 ${formatDemoRate(m2Tilt)} · M3 ${formatDemoRate(m3Rotation)}",
    ) {
        if (!demoMode) {
            statusMessage = "Fine-rate transport is not qualified for live hardware"
            return
        }
        if (demoFineRateJob?.isActive == true) return
        demoFineRateJob = scope.launch {
            try {
                updateDemoFineRateContext()
                val ready = demoFineRate.state.value as? RateAdjustState.Ready
                if (ready == null) {
                    statusMessage = "Fine correction unavailable: ${demoFineRateStateReason()}"
                    return@launch
                }
                val request = RateAdjustRequest(
                    baseProfile = TrackingRate.SIDEREAL,
                    m1Pan = m1Pan,
                    m2Tilt = m2Tilt,
                    m3Rotation = m3Rotation,
                    version = demoFineRateVersion + 1,
                    capability = ready.capability.identity,
                )
                val validation = ready.capability.validate(request)
                if (validation != null) {
                    statusMessage = "Fine correction rejected: ${validation}"
                    return@launch
                }
                val lease = when (val acquired = motionSupervisor.acquire(MotionOwner.TRACKING_CORRECTION)) {
                    is MotionAcquireResult.Granted -> acquired.lease
                    is MotionAcquireResult.Busy -> {
                        statusMessage = "Fine correction blocked by ${acquired.active.owner.name.lowercase()}"
                        return@launch
                    }
                    is MotionAcquireResult.Blocked -> {
                        statusMessage = "Fine correction blocked: ${acquired.reason}"
                        return@launch
                    }
                }
                demoFineRateLease = lease
                demoFineRateVersion = request.version
                when (val result = demoFineRate.apply(request)) {
                    is RateCommandResult.Accepted -> {
                        statusMessage = "Demo fine correction active: ${statusLabel} (no hardware command)"
                    }
                    is RateCommandResult.Rejected -> {
                        demoFineRateLease = null
                        motionSupervisor.stop(lease, MotionStopReason.RELEASE)
                        statusMessage = "Fine correction rejected: ${result.reason}"
                    }
                    is RateCommandResult.Unknown -> {
                        statusMessage = "Fine correction stop/recovery required: ${result.reason}"
                    }
                }
            } finally {
                if (demoFineRateJob === currentCoroutineContext()[Job]) {
                    demoFineRateJob = null
                }
            }
        }
    }

    private fun formatDemoRate(value: Double): String =
        if (value == 0.0) "0.0°/s" else "${value}°/s"

    /** Stop logical fine correction before releasing its shared motion owner. */
    fun demoFineRateStop() {
        if (!demoMode) {
            statusMessage = "Fine-rate transport is not qualified for live hardware"
            return
        }
        scope.launch { stopDemoFineRateNow(MotionStopReason.RELEASE) }
    }

    private fun demoFineRateStateReason(): String = when (val state = demoFineRate.state.value) {
        is RateAdjustState.Unavailable -> state.reason
        is RateAdjustState.Faulted -> state.reason
        is RateAdjustState.Stale -> state.reason
        is RateAdjustState.Applying -> "request is still being acknowledged"
        is RateAdjustState.Active -> "an explicit stop is required before another request"
        is RateAdjustState.Ready -> "ready"
    }

    private suspend fun stopDemoFineRateNow(reason: MotionStopReason): RateCommandResult {
        val applyJob = demoFineRateJob
        demoFineRateJob = null
        applyJob?.cancelAndJoin()
        val result = demoFineRate.stopCorrection()
        val lease = demoFineRateLease
        if (lease != null && result is RateCommandResult.Accepted) {
            when (val ownerResult = motionSupervisor.stop(lease, reason)) {
                MotionStopResult.Stopped -> demoFineRateLease = null
                MotionStopResult.Rejected -> statusMessage = "Fine correction owner release was rejected"
                is MotionStopResult.Unknown -> statusMessage = "Fine correction owner stop unknown: ${ownerResult.reason}"
            }
        }
        updateDemoFineRateContext()
        if (result is RateCommandResult.Unknown) {
            statusMessage = "Fine correction stop outcome unknown: ${result.reason}"
        }
        return result
    }

    /** Disconnect boundary for the logical rate owner; no automatic retry. */
    private fun stopDemoFineRateForDisconnect() {
        runBlocking {
            demoFineRateJob?.cancelAndJoin()
            demoFineRateJob = null
            demoFineRate.onDisconnected()
            demoFineRateLease?.let { lease ->
                if (motionSupervisor.stop(lease, MotionStopReason.DISCONNECT) is MotionStopResult.Stopped) {
                    demoFineRateLease = null
                }
            }
        }
        demoFineRateRehearsalEvidence = false
        demoFineRate.setCapability(null)
    }

    /**
     * A tracking/half-rate mode change is a physical-motion boundary. SLOW
     * must settle first; a direct 531/536 write cannot be allowed to race a
     * live 532/533/534 hold. An ambiguous stop leaves the shared supervisor
     * faulted and blocks the mode change until explicit recovery.
     */
    private suspend fun stopSlowMotionForModeChange(): Boolean {
        demoSlowMotionStopJob?.let { pending ->
            if (pending !== currentCoroutineContext()[Job]) pending.join()
        }

        val owner = motionSupervisor.state.value
        val lease = demoSlowMotionLease
        val slowOwnerActive = lease != null ||
            (owner as? MotionState.Owned)?.lease?.owner == MotionOwner.SLOW_MOVE
        if (!slowOwnerActive) return true

        if (lease == null) {
            statusMessage = "Slow-motion owner has no recoverable lease; reconnect and recover before changing mode"
            return false
        }

        val previous = demoSlowMotionJob
        demoSlowMotionJob = null
        previous?.cancelAndJoin()

        return when (val result = demoSlowMotion.release(lease)) {
            SlowMotionActionResult.Stopped -> {
                demoSlowMotionLease = null
                slowMotionDemoState = slowMotionDemoState.copy(
                    running = false,
                    direction = 0,
                    level = 0,
                )
                true
            }
            is SlowMotionActionResult.Unknown -> {
                statusMessage = "Slow-motion stop outcome unknown: " + result.reason
                false
            }
            else -> {
                statusMessage = "Slow-motion stop was not confirmed"
                false
            }
        }
    }

    /** Capture/programme state that must prevent a new SLOW hold. */
    private fun slowMotionAdmissionBlocker(): String? = when {
        capturePhase is CapturePhase.Requested ||
            capturePhase is CapturePhase.Busy ||
            capturePhase is CapturePhase.OutcomeUnknown ||
            astroWebUiCaptureDispatching ||
            astroWebUiCaptureActive ->
            "Capture is active or unresolved; settle it before moving the mount"
        sequenceActive || observationProgrammeActive ->
            "Capture programme is active; stop it before moving the mount"
        videoRecording == true ->
            "Video is recording; stop it before moving the mount"
        else -> null
    }

    /** Camera/programme starts must not race a currently-held SLOW owner. */
    private fun blockCaptureDuringSlowMotion(): Boolean {
        val owner = (motionSupervisor.state.value as? MotionState.Owned)?.lease?.owner
        if (owner != MotionOwner.SLOW_MOVE) return false
        statusMessage = "Stop slow motion before starting a capture or programme"
        return true
    }

    /** Start one logical SLOW direction in Demo or qualified live mode. */
    private fun liveSlowMotionReady(): Boolean =
        !demoMode && session != null && slowMotionCapabilityRecord.liveReady

    fun demoSlowMotionStart(axis: RateAxis, direction: Int, level: Int) {
        slowMotionAdmissionBlocker()?.let {
            statusMessage = it
            return
        }
        if (!demoMode && !liveSlowMotionReady()) {
            statusMessage = "Slow-motion transport is not qualified for live hardware"
            return
        }
        if (direction !in -1..1 || direction == 0 || level !in 1..5) {
            statusMessage = "Invalid Demo slow-motion request"
            return
        }

        val previous = demoSlowMotionJob
        val pendingStop = demoSlowMotionStopJob
        // Start the owner acquisition at the input boundary. This prevents a
        // queued coroutine from losing a very short touch/key hold before it
        // has established the shared lease.
        demoSlowMotionJob = scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            // A release requested by the previous press/release cycle owns
            // the same SLOW controller. Finish that boundary before a new
            // direction can acquire it again.
            pendingStop?.join()
            previous?.cancelAndJoin()
            demoSlowMotionLease?.let { lease ->
                when (demoSlowMotion.state.value) {
                    is dev.openpolaris.core.domain.SlowMotionState.Armed ->
                        demoSlowMotion.release(lease)
                    is dev.openpolaris.core.domain.SlowMotionState.Holding ->
                        demoSlowMotion.release(lease)
                    is dev.openpolaris.core.domain.SlowMotionState.Faulted -> {
                        statusMessage = "Slow-motion blocked: explicit recovery is required"
                        return@launch
                    }
                    else -> Unit
                }
            }
            demoSlowMotionLease = null

            val lease = when (val acquired = demoSlowMotion.acquire()) {
                is SlowMotionAcquireResult.Granted -> acquired.lease
                is SlowMotionAcquireResult.Busy ->
                    return@launch.also { statusMessage = "Slow-motion owner is busy" }
                is SlowMotionAcquireResult.Blocked ->
                    return@launch.also { statusMessage = "Slow-motion blocked: ${acquired.reason}" }
            }
            val calibration = SlowMotionCalibration.REFERENCE.levels.getValue(level)
            val rate = ((calibration.stateOneRate + calibration.stateTwoRate) / 2.0) * direction
            val targets = RateAxis.entries.associateWith { candidate ->
                if (candidate == axis) SlowMotionTarget(rate, hold = true)
                else SlowMotionTarget(0.0, hold = false)
            }
            val now = slowMotionNow().coerceAtLeast(0L)
            if (demoSlowMotion.start(lease, targets, now) != SlowMotionActionResult.Started) {
                demoSlowMotion.release(lease)
                statusMessage = "Slow-motion plan was rejected"
                return@launch
            }
            demoSlowMotionLease = lease
            demoSlowMotion.tick(lease, now)
            val initialEvents = demoSlowMotionTransport.drainEvents()
            slowMotionDemoState = SlowMotionDemoSnapshot(
                running = true,
                axis = axis,
                direction = direction,
                level = level,
                lastEvents = initialEvents.takeLast(8),
            )
            statusMessage = if (demoMode) {
                "Demo slow motion: ${slowMotionDemoState.label} (logical owner active; no hardware command)"
            } else {
                "Qualified slow motion active: ${slowMotionDemoState.label}"
            }

            while (isActive && (demoMode || liveSlowMotionReady()) && demoSlowMotionLease == lease) {
                delay(50L)
                val tickNow = slowMotionNow().coerceAtLeast(now)
                val renewed = demoSlowMotion.renew(lease, tickNow)
                if (renewed != SlowMotionActionResult.Renewed) {
                    slowMotionDemoState = slowMotionDemoState.copy(
                        running = false,
                        direction = 0,
                        level = 0,
                    )
                    demoSlowMotionLease = null
                    break
                }
                val result = demoSlowMotion.tick(lease, tickNow)
                val events = demoSlowMotionTransport.drainEvents()
                if (events.isNotEmpty()) {
                    slowMotionDemoState = slowMotionDemoState.copy(
                        lastEvents = (slowMotionDemoState.lastEvents + events).takeLast(8),
                    )
                }
                if (result is SlowMotionActionResult.Unknown ||
                    result == SlowMotionActionResult.Rejected
                ) {
                    slowMotionDemoState = slowMotionDemoState.copy(
                        running = false,
                        direction = 0,
                        level = 0,
                    )
                    demoSlowMotionLease = null
                    break
                }
            }
        }
    }

    /**
     * Stop the logical Demo or qualified live SLOW session through the shared
     * owner. The UI only asserts/relinquishes the hold; it does not own a
     * repeat timer.
     */
    fun demoSlowMotionStop() {
        if (!demoMode && !liveSlowMotionReady()) {
            statusMessage = "Slow-motion transport is not qualified for live hardware"
            return
        }
        if (demoSlowMotionStopJob?.isActive == true) return
        val stopJob = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            try {
                val result = stopDemoSlowMotionNow(MotionStopReason.RELEASE)
                if (result is SlowMotionActionResult.Unknown) {
                    statusMessage = "Demo slow-motion stop outcome unknown: " + result.reason
                }
                val stops = RateAxis.entries.map { axis ->
                    SlowScheduleCommand(axis, dev.openpolaris.core.domain.SlowScheduleKind.STOP)
                }
                slowMotionDemoState = slowMotionDemoState.copy(
                    running = false,
                    direction = 0,
                    level = 0,
                    lastEvents = stops,
                )
                if (result == SlowMotionActionResult.Stopped) {
                    statusMessage = if (demoMode) {
                        "Demo slow motion stopped (" + stops.size + " logical stop transitions)"
                    } else {
                        "Qualified slow motion stopped"
                    }
                }
            } finally {
                if (demoSlowMotionStopJob === currentCoroutineContext()[Job]) {
                    demoSlowMotionStopJob = null
                }
            }
        }
        demoSlowMotionStopJob = stopJob
        stopJob.start()
    }

    /** Stop SLOW and retain a fault lease when the physical outcome is unknown. */
    private suspend fun stopDemoSlowMotionNow(reason: MotionStopReason): SlowMotionActionResult {
        val previous = demoSlowMotionJob
        demoSlowMotionJob = null
        previous?.cancelAndJoin()

        val lease = demoSlowMotionLease
        if (lease == null) {
            return SlowMotionActionResult.Stopped
        }

        val result = demoSlowMotion.release(lease, reason)
        slowMotionDemoState = slowMotionDemoState.copy(
            running = false,
            direction = 0,
            level = 0,
        )
        if (result == SlowMotionActionResult.Stopped) {
            demoSlowMotionLease = null
        }
        return result
    }

    /** Explicit recovery after reconnect and operator confirmation of a stopped head. */
    fun recoverSlowMotionAfterReconnect() {
        val lease = demoSlowMotionLease
        if (!slowMotionStateIsFaulted() || lease == null) {
            statusMessage = "No recoverable slow-motion fault is owned by this session"
            return
        }
        if (session == null || !mount.connected) {
            statusMessage = "Reconnect and confirm the mount is stopped before recovery"
            return
        }
        scope.launch {
            if (demoSlowMotion.recoverAfterReconnect(lease)) {
                demoSlowMotionLease = null
                demoSlowMotionJob = null
                slowMotionDemoState = SlowMotionDemoSnapshot()
                statusMessage = "Slow-motion fault cleared after explicit recovery"
            } else {
                statusMessage = "Slow-motion recovery lease no longer matches this session"
            }
        }
    }

    /**
     * Disconnect/background teardown for the Demo owner. A live adapter must
     * use the same boundary with a physical stop proof before reconnecting.
     */
    private fun stopDemoSlowMotionForDisconnect() {
        val previous = demoSlowMotionJob
        demoSlowMotionJob = null
        previous?.cancel()
        demoSlowMotionStopJob?.cancel()
        demoSlowMotionStopJob = null
        val lease = demoSlowMotionLease
        demoSlowMotionLease = null
        if (lease != null) {
            // Disconnect is a hard session boundary. The Demo transport is
            // local today, but this synchronous owner release preserves the
            // same stop-before-reconnect contract required by a future
            // qualified physical adapter and prevents a queued stop job from
            // publishing stale status after disconnect.
            runBlocking {
                when (demoSlowMotion.state.value) {
                    is dev.openpolaris.core.domain.SlowMotionState.Holding ->
                        demoSlowMotion.onDisconnected(lease)
                    is dev.openpolaris.core.domain.SlowMotionState.Armed ->
                        demoSlowMotion.release(lease)
                    else -> Unit
                }
            }
        }
        slowMotionDemoState = SlowMotionDemoSnapshot()
    }

    private suspend fun startFastJogInternal(code: Int): FastJogLease? {
        if (controller == null || session == null) {
            statusMessage = "Not connected"
            return null
        }
        // Live-verified 2026-09-16 (Polaris, FW 6.0.0.54): the 513-516
        // duration jog is honoured while tracking is running. Preserve the
        // source behaviour but surface the condition instead of hiding it.
        if (mount.tracking == false) {
            statusMessage = "Jog sent - tracking is off, so the mount may not move"
        }
        return when (val acquired = fastJogController.acquire(code)) {
            is FastJogAcquireResult.Granted -> {
                fastJogLease = acquired.lease
                when (val started = fastJogController.start(acquired.lease, nowMs())) {
                    FastJogActionResult.Accepted -> acquired.lease
                    FastJogActionResult.Rejected -> {
                        when (val stop = fastJogController.release(acquired.lease)) {
                            FastJogActionResult.Stopped -> statusMessage = "Fast jog start rejected and stopped"
                            is FastJogActionResult.Unknown -> statusMessage = stop.reason
                            else -> statusMessage = "Fast jog start rejected; stop outcome unknown"
                        }
                        fastJogLease = null
                        null
                    }
                    is FastJogActionResult.Unknown -> {
                        fastJogLease = acquired.lease
                        statusMessage = started.reason
                        null
                    }
                    else -> {
                        fastJogLease = null
                        statusMessage = "Fast jog did not start"
                        null
                    }
                }
            }
            is FastJogAcquireResult.Busy -> {
                statusMessage = "Motion busy: " + (acquired.owner?.name ?: "fast jog")
                null
            }
            is FastJogAcquireResult.Blocked -> {
                statusMessage = "Motion blocked: " + acquired.reason
                null
            }
        }
    }

    fun startFastJog(code: Int) {
        if (fastJogJob?.isActive == true) return
        fastJogJob = scope.launch {
            val lease = startFastJogInternal(code) ?: return@launch
            while (isActive && fastJogLease == lease) {
                delay(50L)
                when (val result = fastJogController.tick(lease, nowMs())) {
                    FastJogActionResult.Accepted,
                    FastJogActionResult.NotDue -> Unit
                    FastJogActionResult.Stopped -> {
                        fastJogLease = null
                        statusMessage = "Fast jog lease expired and stopped"
                        break
                    }
                    FastJogActionResult.Rejected -> {
                        fastJogLease = null
                        statusMessage = "Fast jog beat rejected"
                        break
                    }
                    is FastJogActionResult.Unknown -> {
                        fastJogLease = null
                        statusMessage = result.reason
                        break
                    }
                }
            }
        }
    }

    fun stopFastJog() {
        val lease = fastJogLease ?: return
        val motionSession = session
        // Keep the lease visible until the asynchronous release has either
        // settled or been taken over by disconnect(). Clearing it before the
        // coroutine runs lets disconnect miss the physical stop boundary.
        fastJogJob?.cancel()
        fastJogJob = null
        scope.launch {
            // A queued release from an old session must not use the replacement
            // session or publish a terminal status after disconnect.
            if (motionSession == null ||
                session !== motionSession ||
                sessionTearingDown
            ) {
                return@launch
            }
            val result = fastJogController.release(lease)
            if (session !== motionSession || sessionTearingDown) {
                return@launch
            }
            if (fastJogLease == lease) {
                fastJogLease = null
            }
            when (result) {
                FastJogActionResult.Stopped -> statusMessage = "Fast jog stopped"
                FastJogActionResult.Rejected -> statusMessage = "Fast jog stop rejected"
                is FastJogActionResult.Unknown -> statusMessage = result.reason
                else -> statusMessage = "Fast jog stop not confirmed"
            }
        }
    }

    /** Explicit operator recovery after the mount is reconnected and confirmed stopped. */
    fun recoverFastJogAfterReconnect() {
        if (session == null || !mount.connected) {
            statusMessage = "Reconnect and confirm the mount is stopped before recovery"
            return
        }
        scope.launch {
            fastJogController.recoverAfterReconnect()
            fastJogLease = null
            fastJogJob = null
            statusMessage = "Fast jog motion fault cleared after explicit recovery"
        }
    }

    /**
     * Highest-priority common stop for the Motion dock. It settles the active
     * controller before clearing its local lease; an unknown result remains a
     * visible fault and is never converted into idle.
     */
    fun emergencyStopMotion() {
        fastJogJob?.cancel()
        fastJogJob = null
        val motionSession = session
        scope.launch {
            // A queued STOP ALL from a torn-down session must not inspect the
            // replacement/idle supervisor and publish a misleading terminal
            // status. Disconnect itself owns the synchronous lease boundary.
            if (motionSession != null &&
                (session !== motionSession || sessionTearingDown)
            ) {
                return@launch
            }
            when (val state = motionSupervisor.state.value) {
                MotionState.Idle -> statusMessage = "No active motion owner"
                is MotionState.Faulted -> {
                    statusMessage = "Motion stop already faulted: " + state.reason
                }
                is MotionState.Owned -> {
                    when (state.lease.owner) {
                        MotionOwner.FAST_JOG -> {
                            val lease = fastJogLease
                            if (lease == null) {
                                reportEmergencyStop(motionSupervisor.emergencyStop())
                            } else {
                                when (val result = fastJogController.emergencyStop(lease)) {
                                    FastJogActionResult.Stopped -> {
                                        fastJogLease = null
                                        statusMessage = "Emergency stop sent"
                                    }
                                    is FastJogActionResult.Unknown -> statusMessage = result.reason
                                    else -> statusMessage = "Fast jog emergency stop not confirmed"
                                }
                            }
                        }
                        MotionOwner.SLOW_MOVE -> {
                            val lease = demoSlowMotionLease
                            if (lease == null) {
                                reportEmergencyStop(motionSupervisor.emergencyStop())
                            } else {
                                when (val result = demoSlowMotion.emergencyStop(lease)) {
                                    SlowMotionActionResult.Stopped -> {
                                        demoSlowMotionLease = null
                                        slowMotionDemoState = slowMotionDemoState.copy(
                                            running = false,
                                            direction = 0,
                                            level = 0,
                                        )
                                        statusMessage = "Emergency stop sent"
                                    }
                                    is SlowMotionActionResult.Unknown -> statusMessage = result.reason
                                    else -> statusMessage = "Slow-motion emergency stop not confirmed"
                                }
                            }
                        }
                        MotionOwner.TRACKING_CORRECTION -> {
                            val result = stopDemoFineRateNow(MotionStopReason.EMERGENCY)
                            if (result is RateCommandResult.Accepted) {
                                statusMessage = "Fine correction emergency stop sent"
                            }
                        }
                        MotionOwner.GOTO,
                        MotionOwner.ALIGNMENT -> {
                            val result = motionSupervisor.emergencyStop()
                            if (result is MotionStopResult.Unknown) {
                                statusMessage = "Emergency stop outcome unknown: " + result.reason
                            } else {
                                gotoMotionLease = null
                                alignmentMotionLease = null
                                slewJob?.cancelAndJoin()
                                slewJob = null
                                alignmentJob?.cancelAndJoin()
                                alignmentJob = null
                                slewInProgress = false
                                statusMessage = "Emergency stop sent"
                            }
                        }
                        else -> reportEmergencyStop(motionSupervisor.emergencyStop())
                    }
                }
            }
        }
    }

    private fun reportEmergencyStop(result: MotionStopResult) {
        statusMessage = when (result) {
            MotionStopResult.Stopped -> "Emergency stop sent"
            MotionStopResult.Rejected -> "Emergency stop rejected"
            is MotionStopResult.Unknown -> "Emergency stop outcome unknown: " + result.reason
        }
    }

    /** Compatibility one-shot: one bounded source beat followed by stop. */
    fun jog(code: Int) {
        startFastJog(code)
        scope.launch {
            delay(500L)
            stopFastJog()
        }
    }

    var gotoAz by mutableStateOf("0.0")
    var gotoAlt by mutableStateOf("0.0")
    /** True after the user has submitted or solved a target in this session. */
    var targetSelected by mutableStateOf(false)
        private set

    // ---- observer location & RA/Dec goto ---------------------------------

    var latDeg by mutableStateOf("51.5")
    var lngEastDeg by mutableStateOf("-0.12")
    private var observerLocationPersistence: ((Double, Double) -> Unit)? = null

    /**
     * Install the host-owned preference sink. The callback receives only a
     * complete, range-validated observer location; transient invalid text
     * entered into either field is never persisted.
     */
    internal fun setObserverLocationPersistence(callback: ((Double, Double) -> Unit)?) {
        observerLocationPersistence = callback
    }

    /** Restore a previously validated location without writing it back. */
    internal fun restoreObserverLocation(latitudeDeg: Double?, longitudeDeg: Double?) {
        if (latitudeDeg == null || longitudeDeg == null) return
        if (!latitudeDeg.isFinite() || latitudeDeg !in -90.0..90.0) return
        if (!longitudeDeg.isFinite() || longitudeDeg !in -180.0..180.0) return
        latDeg = latitudeDeg.toString()
        lngEastDeg = longitudeDeg.toString()
    }

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

    fun updateLat(v: String) {
        latDeg = v
        persistObserverLocationIfValid()
    }

    fun updateLng(v: String) {
        lngEastDeg = v
        persistObserverLocationIfValid()
    }

    private fun persistObserverLocationIfValid() {
        parsedLocation()?.let { (latitude, longitude) ->
            observerLocationPersistence?.invoke(latitude, longitude)
        }
    }
    @JvmName("toggleRaDecMode")
    fun setRaDecMode(on: Boolean) { raDecMode = on }
    fun updateRa(v: String) { gotoRa = v }
    fun updateDec(v: String) { gotoDec = v }

    val observerLocationReady: Boolean
        get() = parsedLocation() != null

    private fun parsedLocation(): Pair<Double, Double>? {
        val lat = latDeg.toDoubleOrNull() ?: return null
        val lng = lngEastDeg.toDoubleOrNull() ?: return null
        if (!lat.isFinite() || lat !in -90.0..90.0) return null
        if (!lng.isFinite() || lng !in -180.0..180.0) return null
        return lat to lng
    }

    /**
     * True while an arrival-aware slew is in flight (519 issued, 517 poll
     * running). Gates the Slew button and tells the operator that tracking /
     * shooting must wait for arrival — the pre-fix path reported "Slewing"
     * the moment the socket write completed (ASTRO-WORKFLOW-HANDOVER §4).
     */
    var slewInProgress by mutableStateOf(false)
        private set

    /** Handle to the in-flight arrival-aware slew coroutine, for [cancelSlew]. */
    private var slewJob: Job? = null
    /** Shared owner lease for a live GoTo operation. */
    private var gotoMotionLease: dev.openpolaris.core.domain.MotionLease? = null

    /**
     * Slew to entered coordinates and await arrival (code 519 + 517 poll).
     *
     * The UI path now routes through [GoToController] so the mount's actual
     * position is observed before the slew is reported complete: tracking
     * and shooting must not unlock merely because a socket write finished.
     * On timeout the status says so explicitly instead of pretending success.
     */
    fun goto() {
        if (slewInProgress) return
        val s = session ?: run { statusMessage = "Not connected"; return }
        val c = GoToController(s, controller ?: TrackingController(s))
        val target: Pair<Double, Double>
        val targetLabel: String
        if (raDecMode) {
            val loc = parsedLocation() ?: run { statusMessage = "Invalid latitude/longitude"; return }
            val ra = AstroMath.parseRa(gotoRa) ?: run { statusMessage = "Invalid RA (use HH MM SS or H.h)"; return }
            val dec = AstroMath.parseDec(gotoDec) ?: run { statusMessage = "Invalid Dec (use DD MM SS or D.d)"; return }
            val altAz = AstroMath.toHorizontalAt(ra, dec, loc.first, loc.second, AstroMath.julianDateNow())
            target = altAz.azimuthDeg to altAz.altitudeDeg
            targetLabel = "RA $gotoRa Dec $gotoDec (az %.1f deg, alt %.1f deg)".format(
                altAz.azimuthDeg,
                altAz.altitudeDeg,
            )
        } else {
            val az = gotoAz.toDoubleOrNull()
            val alt = gotoAlt.toDoubleOrNull()
            if (az == null || alt == null) {
                statusMessage = "Invalid coordinates"
                return
            }
            target = az to alt
            targetLabel = "az $az deg, alt $alt deg"
        }
        targetSelected = true
        slewInProgress = true
        slewJob = scope.launch {
            val acquired = motionSupervisor.acquire(MotionOwner.GOTO)
            val lease = when (acquired) {
                is MotionAcquireResult.Granted -> acquired.lease
                is MotionAcquireResult.Busy -> {
                    statusMessage = "Slew blocked by ${acquired.active.owner.name.lowercase().replace('_', ' ')}"
                    slewInProgress = false
                    slewJob = null
                    return@launch
                }
                is MotionAcquireResult.Blocked -> {
                    statusMessage = "Slew blocked: ${acquired.reason}"
                    slewInProgress = false
                    slewJob = null
                    return@launch
                }
            }
            gotoMotionLease = lease
            var terminalMessage: String? = null
            try {
                val arrived = c.goToAzAlt(target.first, target.second)
                terminalMessage = if (arrived) {
                    "Arrived at $targetLabel"
                } else {
                    "Slew to $targetLabel timed out - mount not yet at target"
                }
            } finally {
                val stopResult = motionSupervisor.stop(lease, MotionStopReason.RELEASE)
                if (gotoMotionLease == lease) gotoMotionLease = null
                if (stopResult is MotionStopResult.Unknown) {
                    statusMessage = "Slew stop outcome unknown: ${stopResult.reason}"
                } else if (isActive && terminalMessage != null) {
                    statusMessage = terminalMessage
                }
                slewInProgress = false
                slewJob = null
            }
        }
    }

    /**
     * Cancel an in-progress slew: send 519 `state:0` to the firmware and stop
     * the arrival poll so its terminal status write cannot clobber the
     * "Slew cancelled" line. Idempotent when no slew is in flight.
     */
    fun cancelSlew(): Job {
        val motionSession = session
        return scope.launch {
            // Cancellation is a session-owned UI action. A queued cancel from
            // a disconnected session must not inspect replacement motion state
            // or publish a terminal status after teardown.
            if (motionSession != null &&
                (session !== motionSession || sessionTearingDown)
            ) {
                return@launch
            }
            val lease = gotoMotionLease
            if (lease != null) {
                val stopResult = motionSupervisor.stop(lease, MotionStopReason.EMERGENCY)
                if (motionSession != null &&
                    (session !== motionSession || sessionTearingDown)
                ) {
                    return@launch
                }
                if (stopResult is MotionStopResult.Unknown) {
                    statusMessage = "Slew stop outcome unknown: ${stopResult.reason}"
                } else {
                    statusMessage = "Slew cancelled"
                }
            } else {
                statusMessage = "Slew cancelled"
            }
            slewJob?.cancelAndJoin()
            gotoMotionLease = null
            slewJob = null
            slewInProgress = false
        }
    }

    // ---- session persistence + reconnect prompt (issue #27) ----------------

    // [saveCurrentTarget] / [maybeOfferReconnect] / [confirmReconnect] were
    // part of the OURS' "Return to last celestial target" feature. They
    // referenced a TargetStore that was never wired in. Removed; the
    // THEIRS' host-reconnect prompt ([reconnectPrompt], [acceptReconnect])
    // is the only reconnect UX in this build.

    // ---- alignment ---------------------------------------------------------

    /**
     * Identity of the catalog star currently being aligned on. The record
     * action (code 530) sends the mount's current pointing (yaw/pitch) plus
     * observer location — the firmware builds its pointing model from the
     * `num`-indexed submissions — but the *chosen* star's identity is what
     * the operator needs to see: which star was centred, at what RA/Dec.
     * Retained here so a multi-star run keeps an explicit, visible history
     * (ASTRO-WORKFLOW-HANDOVER §2) instead of an anonymous counter.
     */
    data class AlignmentStarRecord(
        val designation: String,
        val raDeg: Double,
        val decDeg: Double,
        /** Az/alt the mount was pointing at when the star was recorded. */
        val azDeg: Double,
        val altDeg: Double,
    )

    var selectedAlignmentStar by mutableStateOf<AlignmentStarRecord?>(null)
        private set

    /** Recorded alignment stars, in submission order (index = firmware `num`). */
    private val _recordedAlignmentStars = mutableListOf<AlignmentStarRecord>()
    val recordedAlignmentStars: List<AlignmentStarRecord> get() = _recordedAlignmentStars

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
                    targetSelected = true
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
        // Mirror connect(): the alignment session must exist for
        // submitAlignmentStar() to have a persistent controller.
        this.alignmentController = AlignmentController(s)
        // Also wire the tracking controller so startTracking()/stopTracking()
        // (and the explicit-rate 531 payload) work in tests without going
        // through connect(). Mirrors the controller wiring in connect().
        this.controller = TrackingController(s)
    }

    /**
     * §6 test seam: install a [CameraController] and a bound
     * [dev.openpolaris.core.domain.IntervalometerController] on this viewmodel
     * without going through [connect]. Mirrors the controller wiring in
     * connect()/connectDemo() so tests can drive [startSequence] and correlate
     * shots via [testOnCaptureFrame] on a known (fake) session. The engine runs
     * on [scope], so under `runTest` its shot loop advances with virtual time.
     */
    internal fun testInstallCameraAndIntervalometer(s: MountSession) {
        this.cameraController = CameraController(s)
        this.intervalometer = createIntervalometer()
        this.observationExecutor = createObservationProgrammeExecutor()
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

    /**
     * Test seam: set the last observed gimbal position without waiting for a
     * 517 poll. Used by alignment tests that need [submitAlignmentStar] to
     * have a position to record.
     */
    internal fun testSetPosition(pos: GimbalPosition?) {
        position = pos
    }

    /**
     * Set the identity of the catalog star currently centred in the view.
     * Called from the UI (star chooser / plate-solve result) before
     * [submitAlignmentStar] so the recorded history carries a name and
     * RA/Dec, not just an anonymous index.
     */
    fun selectAlignmentStar(designation: String, raDeg: Double, decDeg: Double) {
        selectedAlignmentStar = AlignmentStarRecord(
            designation = designation,
            raDeg = raDeg,
            decDeg = decDeg,
            azDeg = 0.0,
            altDeg = 0.0,
        )
    }

    /**
     * Record the current pointing as the next alignment star (code 530).
     *
     * Uses the persistent [alignmentController] so the firmware receives a
     * monotonically increasing `num` index (0, 1, 2, …) across submissions.
     * The pre-fix code constructed a fresh controller per call, so every
     * frame carried `num:0` while the UI counter showed 1, 2, 3 — the
     * repeated-`num:0` defect (ASTRO-WORKFLOW-HANDOVER §1).
     */
    fun submitAlignmentStar() {
        val s = session ?: run { statusMessage = "Not connected"; return }
        val c = alignmentController ?: run { statusMessage = "Not connected"; return }
        val pos = position ?: run { statusMessage = "No mount position yet"; return }
        val loc = parsedLocation() ?: run { statusMessage = "Set a valid observer location first"; return }
        alignmentJob?.cancel()
        alignmentJob = scope.launch {
            val acquired = motionSupervisor.acquire(MotionOwner.ALIGNMENT)
            val lease = when (acquired) {
                is MotionAcquireResult.Granted -> acquired.lease
                is MotionAcquireResult.Busy -> {
                    statusMessage = "Motion busy: " + acquired.active.owner.name.lowercase()
                    return@launch
                }
                is MotionAcquireResult.Blocked -> {
                    statusMessage = "Motion blocked: " + acquired.reason
                    return@launch
                }
            }
            alignmentMotionLease = lease
            try {
                c.submitStar(pos.yaw.toDouble(), pos.pitch.toDouble(), loc.first, loc.second)
                // Record the selected star's identity (if any) with the
                // az/alt it was centred on, so the history is explicit.
                val rec = selectedAlignmentStar?.copy(
                    azDeg = pos.yaw.toDouble(),
                    altDeg = pos.pitch.toDouble(),
                ) ?: AlignmentStarRecord(
                    designation = "star-" + (c.starCount - 1),
                    raDeg = 0.0,
                    decDeg = 0.0,
                    azDeg = pos.yaw.toDouble(),
                    altDeg = pos.pitch.toDouble(),
                )
                _recordedAlignmentStars.add(rec)
                alignmentStars = c.starCount
                statusMessage = "Alignment star " + c.starCount + " recorded (" + rec.designation + ")"
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                statusMessage = "Alignment failed: " + (error.message ?: "unknown error")
            } finally {
                withContext(kotlinx.coroutines.NonCancellable) {
                    val stopped = motionSupervisor.stop(lease, MotionStopReason.RELEASE)
                    if (stopped is MotionStopResult.Unknown) {
                        statusMessage = "Alignment stop outcome unknown: " + stopped.reason
                    }
                    alignmentMotionLease = null
                }
            }
        }
    }

    /**
     * Reset the alignment run: clears the persistent controller's index so
     * the next submission starts at `num:0` again, and drops the recorded
     * star history. The firmware has no dedicated 530-reset opcode (the
     * stock app simply starts a fresh calibration), so abandoning an
     * incomplete run is safe: the next `num:0` begins a new model.
     */
    fun resetAlignment() {
        alignmentController?.reset()
        _recordedAlignmentStars.clear()
        selectedAlignmentStar = null
        alignmentStars = 0
        statusMessage = "Alignment reset"
    }

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

    /** Time of the last explicit dynamic camera-parameter readback. */
    var cameraReadbackAtMs by mutableStateOf<Long?>(null)
        private set

    /**
     * Per-field result from the last refresh. A failed refresh never erases a
     * last-known-good value, but the field status remains visible to the UI.
     */
    var cameraReadbackStatuses by mutableStateOf<Map<String, CameraController.InfoStatus>>(emptyMap())
        private set

    data class CameraAuxiliaryReadback(
        val imageFormat: CameraController.CameraParamResult? = null,
        val controlMode: CameraController.CameraParamResult? = null,
        val exposureTime: CameraController.CameraParamResult? = null,
        val intervalType: CameraController.CameraParamResult? = null,
    )

    var cameraAuxiliaryReadback by mutableStateOf(CameraAuxiliaryReadback())
        private set

    var cameraAuxiliaryReadbackAtMs by mutableStateOf<Long?>(null)
        private set

    val cameraAuxiliaryReadbackAgeText: String
        get() = cameraAuxiliaryReadbackAtMs?.let {
            val ageSeconds = (nowMs() - it).coerceAtLeast(0L) / 1_000L
            if (ageSeconds == 0L) "just now" else "${ageSeconds}s ago"
        } ?: "not read"

    var focusStatus by mutableStateOf<String?>(null)
        private set
    var videoRecording by mutableStateOf<Boolean?>(null)
        private set

    val cameraReadbackAgeText: String
        get() = cameraReadbackAtMs?.let {
            val ageSeconds = (nowMs() - it).coerceAtLeast(0L) / 1_000L
            if (ageSeconds == 0L) "just now" else "${ageSeconds}s ago"
        } ?: "not read"

    private var cameraController: dev.openpolaris.core.domain.CameraController? = null

    // §6 (handover step 7): the typed intervalometer / capture-sequence engine.
    // Owned by the VM so its shot loop runs on the same scope as the rest of
    // the app (and, in tests, on the virtual-time test scope). Created per
    // session in connect()/connectDemo(), torn down in disconnect(). The UI
    // drives it via [startSequence]/[pauseSequence]/[resumeSequence]/
    // [stopSequence]; shot completion is correlated by the capture-event
    // observer (see [completeCaptureIfCorrelated]) which calls
    // [dev.openpolaris.core.domain.IntervalometerController.markShotDone].
    private var intervalometer: dev.openpolaris.core.domain.IntervalometerController? = null
    private var observationExecutor: dev.openpolaris.core.domain.ObservationProgrammeExecutor? = null

    /** Live intervalometer state for the UI (mirrors [intervalometer].state). */
    var sequenceState: dev.openpolaris.core.domain.IntervalometerController.State by mutableStateOf(
        dev.openpolaris.core.domain.IntervalometerController.State.Idle
    )
        private set

    /** Programme state is separate from the legacy intervalometer state. */
    var observationProgrammeState: dev.openpolaris.core.domain.ObservationProgrammeExecutor.State by mutableStateOf(
        dev.openpolaris.core.domain.ObservationProgrammeExecutor.State.Idle,
    )
        private set

    /**
     * Build a fresh [dev.openpolaris.core.domain.IntervalometerController] bound
     * to the current session's camera controller. Its shot loop runs on [scope]
     * (the app scope in production, the virtual-time test scope in tests), and
     * every state transition is mirrored into [sequenceState] so the Camera
     * pane can render live progress without polling.
     */
    private fun createIntervalometer(): dev.openpolaris.core.domain.IntervalometerController? {
        val cam = cameraController ?: return null
        return dev.openpolaris.core.domain.IntervalometerController(
            camera = cam,
            scope = scope,
            beforeCapture = {
                val ownerSession = session
                if (ownerSession == null || !ownsSession(ownerSession) || cameraController !== cam) {
                    throw IllegalStateException("capture session changed before sequence shutter")
                }
                verifyCaptureReadiness(ownerSession, cam)?.let { reason ->
                    throw IllegalStateException("camera readiness failed: $reason")
                }
                suspendCaptureWorkloads()
            },
            onStateChange = { s ->
                sequenceState = s
                // A shutter may still be in flight while Running (between shots)
                // or Paused (a resume waits for the unconfirmed shutter). Only
                // terminal states clear it so a late file event is not routed
                // into markShotDone() after the sequence has ended.
                sequenceActive = when (s) {
                    is dev.openpolaris.core.domain.IntervalometerController.State.Running,
                    is dev.openpolaris.core.domain.IntervalometerController.State.Paused,
                    is dev.openpolaris.core.domain.IntervalometerController.State.OutcomeUnknown -> true
                    else -> false
                }
                when (s) {
                    is dev.openpolaris.core.domain.IntervalometerController.State.Completed -> {
                        capturePhase = CapturePhase.Completed
                        restoreCaptureWorkloads()
                    }
                    is dev.openpolaris.core.domain.IntervalometerController.State.OutcomeUnknown -> {
                        capturePhase = CapturePhase.OutcomeUnknown(s.reason)
                        statusMessage = "Sequence outcome unknown — check the card and reconnect before another shutter"
                    }
                    is dev.openpolaris.core.domain.IntervalometerController.State.Failed -> {
                        statusMessage = "Sequence failed: ${s.reason}"
                        if (capturePhase !is CapturePhase.Requested && capturePhase !is CapturePhase.Busy) restoreCaptureWorkloads()
                    }
                    is dev.openpolaris.core.domain.IntervalometerController.State.Stopped -> {
                        if (capturePhase !is CapturePhase.Requested && capturePhase !is CapturePhase.Busy) restoreCaptureWorkloads()
                    }
                    else -> Unit
                }
            },
        )
    }

    private val observationOwner = object : dev.openpolaris.core.domain.ObservationProgrammeExecutor.OwnerLease {
        override fun acquire(): Boolean {
            if (observationProgrammeActive || sequenceActive) return false
            if (session == null || cameraController == null) return false
            if (capturePhase is CapturePhase.Requested || capturePhase is CapturePhase.Busy || capturePhase is CapturePhase.OutcomeUnknown) return false
            if (videoRecording == true) return false
            if (!cameraAttachmentAllowsCommand()) return false
            observationProgrammeActive = true
            return true
        }

        override fun release() {
            observationProgrammeActive = false
            programmeConfirmation?.cancel()
            programmeConfirmation = null
            if (!sessionTearingDown) restoreCaptureWorkloads()
        }
    }

    private val observationCaptureBridge = dev.openpolaris.core.domain.ObservationProgrammeCaptureBridge(
        evidence = captureEvidence,
        trigger = ::triggerObservationProgrammeStep,
        awaitConfirmation = ::awaitObservationConfirmation,
    )

    private fun createObservationProgrammeExecutor(): dev.openpolaris.core.domain.ObservationProgrammeExecutor =
        dev.openpolaris.core.domain.ObservationProgrammeExecutor(
            scope = scope,
            owner = observationOwner,
            onStateChange = { state ->
                observationProgrammeState = state
                when (state) {
                    is dev.openpolaris.core.domain.ObservationProgrammeExecutor.State.Running ->
                        statusMessage = "Programme running: ${state.completedSteps}/${state.plan.captureCount}"
                    is dev.openpolaris.core.domain.ObservationProgrammeExecutor.State.Paused ->
                        statusMessage = "Programme paused: ${state.completedSteps}/${state.plan.captureCount}"
                    is dev.openpolaris.core.domain.ObservationProgrammeExecutor.State.Completed ->
                        statusMessage = "Programme completed: ${state.completedSteps} captures"
                    is dev.openpolaris.core.domain.ObservationProgrammeExecutor.State.Stopped ->
                        statusMessage = "Programme stopped after ${state.completedSteps} captures"
                    is dev.openpolaris.core.domain.ObservationProgrammeExecutor.State.Failed ->
                        statusMessage = "Programme failed: ${state.reason}"
                    is dev.openpolaris.core.domain.ObservationProgrammeExecutor.State.OutcomeUnknown ->
                        statusMessage = "Programme outcome unknown — reconnect before another shutter"
                    dev.openpolaris.core.domain.ObservationProgrammeExecutor.State.Idle -> Unit
                }
            },
            executeStep = observationCaptureBridge::execute,
        )

    /**
     * A qualified first execution path for a planner-produced lapse. HDR,
     * focus-stack and panorama plans remain preview-only until their exposure
     * and motion contracts are separately qualified.
     */
    fun startObservationProgramme(plan: dev.openpolaris.core.domain.ObservationProgrammePlanner.Plan): Boolean {
        if (blockCaptureDuringSlowMotion()) return false
        val requiresExtraCapability =
            plan.kind != dev.openpolaris.core.domain.ObservationProgrammePlanner.Kind.LAPSE ||
                plan.steps.any {
                    it.exposureIndex != null ||
                        it.focusPosition != null ||
                        it.yawOffsetDeg != null ||
                        it.pitchOffsetDeg != null
                }
        if (requiresExtraCapability && !demoMode) {
            statusMessage = "Only a fixed-exposure lapse is qualified for execution"
            return false
        }
        if (observationExecutor == null || cameraController == null || session == null) {
            statusMessage = "Not connected"
            return false
        }
        if (sequenceActive) {
            statusMessage = "Capture sequence in progress — stop it first"
            return false
        }
        if (videoRecording == true) {
            statusMessage = "Stop video before starting a programme"
            return false
        }
        if (capturePhase is CapturePhase.Requested || capturePhase is CapturePhase.Busy || capturePhase is CapturePhase.OutcomeUnknown) {
            statusMessage = "Capture outcome is not clear — wait or reconnect"
            return false
        }
        beginCaptureTimeline("Programme requested: ${plan.captureCount} captures")
        return observationExecutor!!.start(plan).also { started ->
            if (!started) statusMessage = "Programme owner is busy or unavailable"
        }
    }

    fun pauseObservationProgramme() {
        observationExecutor?.pause()
    }

    fun resumeObservationProgramme(): Boolean = observationExecutor?.resume() == true

    fun stopObservationProgramme() {
        observationExecutor?.stop()
    }

    private suspend fun triggerObservationProgrammeStep(
        step: dev.openpolaris.core.domain.ObservationProgrammePlanner.Step,
    ): dev.openpolaris.core.domain.ObservationProgrammeCaptureBridge.TriggerResult {
        val camera = cameraController
            ?: return dev.openpolaris.core.domain.ObservationProgrammeCaptureBridge.TriggerResult.Rejected("camera is not connected")
        if (!cameraAttachmentAllowsCommand()) {
            return dev.openpolaris.core.domain.ObservationProgrammeCaptureBridge.TriggerResult.Rejected("camera attachment is not confirmed")
        }
        if (videoRecording == true) {
            return dev.openpolaris.core.domain.ObservationProgrammeCaptureBridge.TriggerResult.Rejected("video is recording")
        }
        if (sequenceActive) {
            return dev.openpolaris.core.domain.ObservationProgrammeCaptureBridge.TriggerResult.Rejected("another capture sequence owns the shutter")
        }
        val ownerSession = session
            ?: return dev.openpolaris.core.domain.ObservationProgrammeCaptureBridge.TriggerResult.Rejected("capture session is not connected")
        val readinessFailure = verifyCaptureReadiness(ownerSession, camera)
        if (readinessFailure != null) {
            return dev.openpolaris.core.domain.ObservationProgrammeCaptureBridge.TriggerResult.Rejected(
                "camera readiness failed: $readinessFailure",
            )
        }
        capturePhase = CapturePhase.Requested
        pendingCapturePublication = null
        programmeConfirmation = CompletableDeferred()
        beginCaptureTimeline("Programme step ${step.ordinal}: ${step.label}")
        suspendCaptureWorkloads()
        return try {
            camera.capture()
            statusMessage = if (demoMode) {
                "Demo programme step sent: " + step.label + " (metadata rehearsal)"
            } else {
                "Programme step sent: " + step.label
            }
            dev.openpolaris.core.domain.ObservationProgrammeCaptureBridge.TriggerResult.Accepted
        } catch (error: Throwable) {
            val reason = error.message ?: "programme shutter dispatch failed"
            captureEvidence.unknown(reason)
            capturePhase = CapturePhase.OutcomeUnknown(reason)
            appendCaptureTimeline(CaptureTimelineStage.UNKNOWN, reason)
            dev.openpolaris.core.domain.ObservationProgrammeCaptureBridge.TriggerResult.OutcomeUnknown(reason)
        }
    }

    private suspend fun awaitObservationConfirmation(): CaptureEvidenceGate.Publication? {
        val confirmation = programmeConfirmation ?: return null
        val publication = withTimeoutOrNull(CAPTURE_TIMEOUT_MS) { confirmation.await() }
        if (publication == null && !confirmation.isCompleted) {
            val reason = "timeout: missing correlated 264 lifecycle and/or 773 file event after ${CAPTURE_TIMEOUT_MS / 1000}s"
            captureEvidence.unknown(reason)
            capturePhase = CapturePhase.OutcomeUnknown(reason)
            appendCaptureTimeline(CaptureTimelineStage.UNKNOWN, reason)
            statusMessage = "Programme outcome unknown — check the card before another shutter"
        }
        programmeConfirmation = null
        return publication
    }

    /**
     * §6: start a capture sequence (count + interval + optional pre-delay).
     * Each shot is fired by the engine and confirmed by the capture-event
     * observer correlating the 264 lifecycle + 773 file events, which calls
     * [dev.openpolaris.core.domain.IntervalometerController.markShotDone].
     * Returns false (and reports why) when no session / camera is attached.
     */
    fun startSequence(shotCount: Int, intervalMs: Long, preDelayMs: Long = 0L): Boolean {
        if (blockCaptureDuringSlowMotion()) return false
        val engine = intervalometer
        if (engine == null || cameraController == null) {
            statusMessage = "Not connected"
            return false
        }
        if (observationProgrammeActive) {
            statusMessage = "Observation programme in progress — stop it first"
            return false
        }
        if (!cameraAttachmentAllowsCommand()) return false
        if (videoRecording == true) {
            statusMessage = "Stop video before starting a still capture sequence"
            return false
        }
        if (capturePhase !is CapturePhase.Idle && capturePhase !is CapturePhase.Completed && capturePhase !is CapturePhase.Failed) {
            statusMessage = "Capture outcome is not clear — reconnect before another shutter"
            return false
        }
        val plan = dev.openpolaris.core.domain.IntervalometerController.SequencePlan(
            shotCount = shotCount,
            intervalMs = intervalMs,
            preDelayMs = preDelayMs,
        )
        val previousPhase = capturePhase
        captureEvidence.begin()
        pendingCapturePublication = null
        beginCaptureTimeline("Sequence requested: $shotCount shots")
        capturePhase = CapturePhase.Requested
        if (!engine.start(plan)) {
            capturePhase = previousPhase
            appendCaptureTimeline(CaptureTimelineStage.FAILED, "sequence already running")
            statusMessage = "Sequence already running"
            return false
        }
        // sequenceActive is set by the engine's onStateChange callback (Running).
        // Put the capture-event observer in its active state so the first shot's
        // 264/773 frames are processed; [completeCaptureIfCorrelated] keeps it
        // active between subsequent shots.
        statusMessage = "Sequence started: $shotCount shots @ ${intervalMs} ms"
        return true
    }

    /** §6: pause the running sequence at the next shot boundary. */
    fun pauseSequence() {
        intervalometer?.pause()
    }

    /** §6: resume a paused sequence from the next unshot index. */
    fun resumeSequence() {
        // Re-arm the capture-event observer for the next shot (a pause may have
        // left the phase in a terminal state after the last completed shot).
        captureEvidence.begin()
        pendingCapturePublication = null
        capturePhase = CapturePhase.Requested
        intervalometer?.resume()
    }

    /** §6: stop the sequence early (terminal [Stopped] state). */
    fun stopSequence() {
        val engine = intervalometer ?: return
        engine.stop()
        if (engine.state is dev.openpolaris.core.domain.IntervalometerController.State.Stopped) {
            statusMessage = "Sequence stopped"
        }
    }

    /** §6 test seam: reset the sequence-active flag between scripted scenarios. */
    internal fun testResetSequenceActive() {
        sequenceActive = false
    }

    fun refreshCamera() {
        val ownerSession = session
        val ownerController = cameraController
        if (ownerController == null || ownerSession == null) {
            statusMessage = "Not connected"
            return
        }
        if (!dev.openpolaris.core.config.FeatureFlags.isEnabled("experimentalCamera") && !demoMode) {
            statusMessage = "Enable Camera qualification mode in Settings first"
            return
        }
        if (cameraRefreshJob?.isActive == true) {
            statusMessage = "Camera parameter refresh already in progress"
            return
        }
        cameraRefreshJob = scope.launch {
            val iso = ownerController.queryBenroInfo(Codes.BenroCamera.GET_ISO_INFO, "iso")
            val wb = ownerController.queryBenroInfo(Codes.BenroCamera.GET_WB_INFO, "wb")
            val ev = ownerController.queryBenroInfo(Codes.BenroCamera.GET_EV_INFO, "ev")
            val shutter = ownerController.queryBenroInfo(Codes.BenroCamera.GET_SHUTTER_INFO, "shutter")
            val aperture = ownerController.queryBenroInfo(Codes.BenroCamera.GET_FNUM_INFO, "fNum")
            if (!ownsSession(ownerSession)) return@launch

            fun usable(info: CameraController.Info): Boolean =
                info.status == CameraController.InfoStatus.VALID

            val current = camera
            camera = current.copy(
                isoIndex = if (usable(iso)) iso.index else current.isoIndex,
                wbIndex = if (usable(wb)) wb.index else current.wbIndex,
                evIndex = if (usable(ev)) ev.index else current.evIndex,
                shutterIndex = if (usable(shutter)) shutter.index else current.shutterIndex,
                fNumIndex = if (usable(aperture)) aperture.index else current.fNumIndex,
                isoOptions = if (usable(iso) && iso.options.isNotEmpty()) iso.options else current.isoOptions,
                wbOptions = if (usable(wb) && wb.options.isNotEmpty()) wb.options else current.wbOptions,
                evOptions = if (usable(ev) && ev.options.isNotEmpty()) ev.options else current.evOptions,
                shutterOptions = if (usable(shutter) && shutter.options.isNotEmpty()) shutter.options else current.shutterOptions,
                fNumOptions = if (usable(aperture) && aperture.options.isNotEmpty()) aperture.options else current.fNumOptions,
            )
            cameraReadbackStatuses = mapOf(
                "ISO" to iso.status,
                "WB" to wb.status,
                "EV" to ev.status,
                "Shutter" to shutter.status,
                "Aperture" to aperture.status,
            )
            if (listOf(iso, wb, ev, shutter, aperture).any(::usable)) {
                cameraReadbackAtMs = nowMs().coerceAtLeast(0L)
            }
            val readings = listOf(
                "ISO[265]=${iso.raw.ifBlank { "<empty>" }} (${iso.status.name.lowercase()})",
                "WB[266]=${wb.raw.ifBlank { "<empty>" }} (${wb.status.name.lowercase()})",
                "EV[267]=${ev.raw.ifBlank { "<empty>" }} (${ev.status.name.lowercase()})",
                "Shutter[268]=${shutter.raw.ifBlank { "<empty>" }} (${shutter.status.name.lowercase()})",
                "Aperture[275]=${aperture.raw.ifBlank { "<empty>" }} (${aperture.status.name.lowercase()})",
            )
            statusMessage = if (demoMode) {
                "Demo camera readback ready: " + shutter.raw.ifBlank { "unknown" }
            } else {
                readings.joinToString(" | ")
            }
        }
    }

    /**
     * Read the source-aligned task-3 camera values without enabling any write.
     * These routes are intentionally explicit because some firmware exposes
     * only the primary INFO registry; a missing auxiliary response is shown as
     * unavailable and never replaces the primary camera readback.
     */
    fun refreshCameraAuxiliary() {
        val ownerSession = session
        val ownerController = cameraController
        if (ownerController == null || ownerSession == null) {
            statusMessage = "Not connected"
            return
        }
        if (!FeatureFlags.isEnabled("experimentalCamera") && !demoMode) {
            statusMessage = "Enable Camera qualification mode in Settings first"
            return
        }
        if (cameraAuxiliaryRefreshJob?.isActive == true) {
            statusMessage = "Auxiliary camera readback already in progress"
            return
        }
        cameraAuxiliaryRefreshJob = scope.launch {
            val imageFormat = ownerController.queryImageFormat()
            val controlMode = ownerController.queryControlMode()
            val exposureTime = ownerController.queryExposureTime()
            val intervalType = ownerController.queryIntervalType()
            if (!ownsSession(ownerSession) || cameraController !== ownerController) return@launch
            cameraAuxiliaryReadback = CameraAuxiliaryReadback(
                imageFormat = imageFormat,
                controlMode = controlMode,
                exposureTime = exposureTime,
                intervalType = intervalType,
            )
            cameraAuxiliaryReadbackAtMs = nowMs().coerceAtLeast(0L)
            statusMessage = if (demoMode) {
                "Demo auxiliary camera readback ready"
            } else {
                "Auxiliary camera readback complete"
            }
        }
    }

    private fun cameraAttachmentAllowsCommand(): Boolean {
        val attachment = cameraAttachment
        if (attachment == null) {
            statusMessage = "Camera status not verified — camera command blocked"
            return false
        }
        if (!attachment.isAttached) {
            statusMessage = "No camera detected — camera command blocked"
            return false
        }
        return true
    }

    /**
     * Fresh capture admission check. Camera INFO values are not cached for a
     * release decision: a late same-code frame can otherwise make a later
     * shutter look ready when the camera has actually disappeared or changed
     * mode. MountSession quarantines timed-out response codes until reconnect.
     */
    private suspend fun verifyCaptureReadiness(
        ownerSession: MountSession,
        ownerController: CameraController,
        requiredMode: dev.openpolaris.core.domain.CameraReleaseMode? = null,
    ): String? {
        val attachmentResult = ownerSession.request(
            code = Codes.CAM_INFO,
            timeoutMs = CAMERA_READINESS_TIMEOUT_MS,
            quarantineOnTimeout = true,
        ) { CommandTable.CAM_INFO.parse!!(it) }
        when (attachmentResult) {
            is MountSession.CmdResult.Ok -> {
                val attachment = attachmentResult.value
                cameraAttachment = attachment
                if (!attachment.isAttached) {
                    return "no camera detected by the mount"
                }
            }
            is MountSession.CmdResult.Timeout ->
                return "camera readiness check timed out"
            is MountSession.CmdResult.ProtocolError ->
                return "camera readiness unavailable: ${attachmentResult.message}"
        }

        val shutter = ownerController.queryBenroInfo(
            Codes.BenroCamera.GET_SHUTTER_INFO,
            "shutter",
        )
        cameraReadbackStatuses = cameraReadbackStatuses + ("Shutter" to shutter.status)
        val shutterIndex = shutter.index
        if (shutter.status != CameraController.InfoStatus.VALID ||
            shutterIndex == null ||
            shutter.options.isEmpty() ||
            shutterIndex !in shutter.options.indices
        ) {
            val raw = shutter.raw.trim().ifBlank { shutter.detail ?: "no raw response" }
                .take(256)
            return "camera shutter readback unavailable (${shutter.status.name.lowercase()}; raw=$raw); reconnect required"
        }

        camera = camera.copy(
            shutterIndex = shutterIndex,
            shutterOptions = shutter.options,
        )
        cameraReadbackAtMs = nowMs().coerceAtLeast(0L)
        val observedMode = dev.openpolaris.core.domain.CameraReleaseMode.fromOption(
            shutter.options[shutterIndex],
        )
        if (requiredMode != null && observedMode != requiredMode) {
            return "camera is not in ${requiredMode.label} mode (fresh readback: ${observedMode.label})"
        }
        return null
    }

    private fun failCaptureAdmission(reason: String) {
        captureKind = null
        captureSource = null
        nativeBulbCommandAccepted = false
        captureEvidence.fail(reason)
        capturePhase = CapturePhase.Failed(reason)
        appendCaptureTimeline(CaptureTimelineStage.FAILED, "Capture blocked: $reason")
        statusMessage = "Capture blocked: $reason"
        restoreCaptureWorkloads()
    }

    private fun rejectUnverifiedCameraWrite() {
        statusMessage = "Camera parameter write blocked: protocol mapping is unverified"
    }
    fun setFocus(index: Int) = rejectUnverifiedCameraWrite()
    fun setImgSize(index: Int) = rejectUnverifiedCameraWrite()
    fun setImgFmt(index: Int) = rejectUnverifiedCameraWrite()
    fun setColor(index: Int) = rejectUnverifiedCameraWrite()
    fun setShutter(index: Int) {
        val option = camera.shutterOptions.getOrNull(index)?.trim()?.lowercase()
        qualifyCameraSetting(
            "Shutter",
            Codes.BenroCamera.GET_SHUTTER_INFO,
            Codes.BenroCamera.SET_SHUTTER,
            "shutter",
            index,
            rebindForBulb = option == "bulb" || option == "b",
        )
    }
    fun setCaptureMode(index: Int) = rejectUnverifiedCameraWrite()
    fun setIso(index: Int) = qualifyCameraSetting("ISO", Codes.BenroCamera.GET_ISO_INFO, Codes.BenroCamera.SET_ISO, "iso", index)
    fun setWb(index: Int) = qualifyCameraSetting("WB", Codes.BenroCamera.GET_WB_INFO, Codes.BenroCamera.SET_WB, "wb", index)
    fun setFNum(index: Int) = qualifyCameraSetting("Aperture", Codes.BenroCamera.GET_FNUM_INFO, Codes.BenroCamera.SET_FNUM, "fNum", index)
    fun setEv(index: Int) = qualifyCameraSetting("EV", Codes.BenroCamera.GET_EV_INFO, Codes.BenroCamera.SET_EV, "ev", index)

    /** One discrete, source-aligned manual-focus adjustment. The live gate is
     * deliberately separate from [experimentalCamera] because the field sign
     * and repeat/stop behaviour need named-camera qualification. */
    fun adjustFocusNear(fast: Boolean = false) = dispatchFocusAdjustment(near = true, fast = fast)
    fun adjustFocusFar(fast: Boolean = false) = dispatchFocusAdjustment(near = false, fast = fast)

    private fun dispatchFocusAdjustment(near: Boolean, fast: Boolean) {
        val controller = cameraController
        if (controller == null) { statusMessage = "Not connected"; return }
        if (!cameraAttachmentAllowsCommand()) return
        if ((!FeatureFlags.isEnabled("experimentalCamera") ||
            !FeatureFlags.isEnabled("cameraFocusControl")) && !demoMode
        ) {
            statusMessage = "Manual focus is read-only until camera direction is qualified"
            return
        }
        scope.launch {
            val result = if (near) controller.adjustManualFocusNear(fast)
            else controller.adjustManualFocusFar(fast)
            focusStatus = if (result.accepted) {
                "${if (near) "Near" else "Far"} focus acknowledged${if (fast) " (fast)" else ""}"
            } else {
                "Focus not confirmed: ${result.error ?: result.raw ?: "no acknowledgement"}"
            }
            statusMessage = focusStatus.orEmpty()
        }
    }

    /** Start/stop video using the source-aligned 263 state echo. */
    fun setVideoRecording(recording: Boolean) {
        val controller = cameraController
        if (controller == null) { statusMessage = "Not connected"; return }
        if (!cameraAttachmentAllowsCommand()) return
        if ((!FeatureFlags.isEnabled("experimentalCamera") ||
            !FeatureFlags.isEnabled("cameraVideo")) && !demoMode
        ) {
            statusMessage = "Video control is read-only until its camera lifecycle is qualified"
            return
        }
        if (sequenceActive || capturePhase is CapturePhase.Requested || capturePhase is CapturePhase.Busy) {
            statusMessage = "Stop the active capture workflow before changing video"
            return
        }
        scope.launch {
            val result = controller.setVideoRecordStatus(recording)
            if (result.accepted) {
                videoRecording = recording
                statusMessage = if (recording) "Video recording started" else "Video recording stopped"
            } else {
                statusMessage = "Video ${if (recording) "start" else "stop"} not confirmed: ${result.error ?: result.raw ?: "no state echo"}"
            }
        }
    }

    private fun qualifyCameraSetting(
        label: String,
        infoCode: Int,
        setCode: Int,
        key: String,
        index: Int,
        rebindForBulb: Boolean = false,
    ) {
        val controller = cameraController
        if (controller == null) { statusMessage = "Not connected"; return }
        if (!cameraAttachmentAllowsCommand()) return
        if (!dev.openpolaris.core.config.FeatureFlags.isEnabled("experimentalCamera") && !demoMode) {
            statusMessage = "Enable Camera qualification mode in Settings first"
            return
        }
        scope.launch {
            if (rebindForBulb && !demoMode) {
                statusMessage = "Reconnecting camera for Bulb mode…"
                if (!controller.rebindForModeTransition()) {
                    statusMessage = "Bulb mode rebind failed; shutter write blocked"
                    return@launch
                }
            }
            val result = controller.qualifyBenroSetting(label, infoCode, setCode, key, index)
            if (result.verified) {
                camera = when (label) {
                    "ISO" -> camera.copy(isoIndex = result.after.index)
                    "WB" -> camera.copy(wbIndex = result.after.index)
                    "EV" -> camera.copy(evIndex = result.after.index)
                    "Shutter" -> camera.copy(shutterIndex = result.after.index)
                    "Aperture" -> camera.copy(fNumIndex = result.after.index)
                    else -> camera
                }
            }
            statusMessage = if (result.verified) {
                "$label verified: SET[$setCode] reply=${result.setRaw}; INFO[$infoCode] before=${result.before.raw}; after=${result.after.raw}"
            } else {
                "$label NOT VERIFIED: ${result.error ?: "SET[$setCode] reply=${result.setRaw}; INFO[$infoCode] before=${result.before.raw}; after=${result.after.raw}"}"
            }
        }
    }
    /**
     * #60: trigger a single exposure and track its asynchronous lifecycle via
     * [capturePhase]. The shutter is debounced while any non-[Idle] phase is
     * active (a second press is ignored, never auto-retried — the camera may
     * already be exposing). A bounded watchdog ([CAPTURE_TIMEOUT_MS]) reports
     * [OutcomeUnknown] if correlated lifecycle/file evidence never arrives.
     */
    fun capture() {
        if (blockCaptureDuringSlowMotion()) return
        val ownerSession = session
        val ownerController = cameraController
        if (ownerSession == null || ownerController == null) { statusMessage = "Not connected"; return }
        if (!cameraAttachmentAllowsCommand()) return
        if (videoRecording == true) {
            statusMessage = "Stop video before a still capture"
            return
        }
        // §6: a running/paused sequence owns the shutter — a manual single-shot
        // press would race the engine's next trigger. Stop the sequence first.
        if (sequenceActive || observationProgrammeActive) {
            statusMessage = "Capture programme in progress — stop it before a single shot"
            return
        }
        // Debounce: ignore a second shutter press while a capture is pending,
        // busy, or still settling from the previous one. Only Idle and
        // terminal states (Completed/Failed) accept a new request.
        val phase = capturePhase
        if (phase !is CapturePhase.Idle &&
            phase !is CapturePhase.Completed &&
            phase !is CapturePhase.Failed
        ) {
            statusMessage = "Capture in progress — wait for it to finish"
            return
        }
        captureDispatchJob = scope.launch {
            if (!ownsSession(ownerSession) || cameraController !== ownerController) return@launch
            val admissionFailure = verifyCaptureReadiness(ownerSession, ownerController)
            if (admissionFailure != null) {
                failCaptureAdmission(admissionFailure)
                return@launch
            }
            captureKind = CaptureKind.NORMAL
            captureSource = CaptureSource.NATIVE
            nativeBulbCommandAccepted = false
            capturePhase = CapturePhase.Requested
            captureEvidence.begin()
            pendingCapturePublication = null
            beginCaptureTimeline("Still release sent")
            suspendCaptureWorkloads()
            if (!ownsSession(ownerSession) || cameraController !== ownerController) return@launch
            ownerController.capture()
            statusMessage = "Capture sent"
            captureWatchdogJob?.cancel()
            captureWatchdogJob = scope.launch {
                delay(CAPTURE_TIMEOUT_MS)
                if (capturePhase is CapturePhase.Requested || capturePhase is CapturePhase.Busy) {
                    val reason = "timeout: missing correlated 264 lifecycle and/or 773 file event after ${CAPTURE_TIMEOUT_MS / 1000}s"
                    captureEvidence.unknown(reason)
                    capturePhase = CapturePhase.OutcomeUnknown(reason)
                    appendCaptureTimeline(CaptureTimelineStage.UNKNOWN, reason)
                    statusMessage = "Capture outcome unknown — check the card before another shutter"
                    // Keep camera-backed work suspended until explicit session
                    // recovery; the camera may still be processing the shot.
                }
            }
        }
    }

    /** Trigger a Benro-compatible Bulb exposure from the Manual camera pane. */
    fun captureBulb(seconds: Int) {
        if (blockCaptureDuringSlowMotion()) return
        if (seconds <= 0) { statusMessage = "Bulb duration must be positive"; return }
        val ownerSession = session
        val ownerController = cameraController
        if (ownerSession == null || ownerController == null) { statusMessage = "Not connected"; return }
        if (!cameraAttachmentAllowsCommand()) return
        if (videoRecording == true) {
            statusMessage = "Stop video before a Bulb capture"
            return
        }
        if (sequenceActive || observationProgrammeActive) { statusMessage = "Capture programme in progress — stop it first"; return }
        val phase = capturePhase
        if (phase !is CapturePhase.Idle && phase !is CapturePhase.Completed && phase !is CapturePhase.Failed) {
            statusMessage = "Capture in progress — wait for it to finish"; return
        }
        captureDispatchJob = scope.launch {
            if (!ownsSession(ownerSession) || cameraController !== ownerController) return@launch
            val admissionFailure = verifyCaptureReadiness(
                ownerSession,
                ownerController,
                requiredMode = dev.openpolaris.core.domain.CameraReleaseMode.BULB,
            )
            if (admissionFailure != null) {
                failCaptureAdmission(admissionFailure)
                return@launch
            }
            captureKind = CaptureKind.BULB
            captureSource = CaptureSource.NATIVE
            nativeBulbCommandAccepted = false
            capturePhase = CapturePhase.Requested
            captureEvidence.begin()
            pendingCapturePublication = null
            beginCaptureTimeline("Bulb command requested; awaiting acknowledgement")
            suspendCaptureWorkloads()
            if (!ownsSession(ownerSession) || cameraController !== ownerController) return@launch
            val result = ownerController.captureBulb(seconds)
            if (!result.accepted) {
                val reason = result.error ?: result.raw ?: "no explicit state:1 acknowledgement"
                captureEvidence.fail(reason)
                captureKind = null
                captureSource = null
                nativeBulbCommandAccepted = false
                capturePhase = CapturePhase.Failed("Bulb command rejected: $reason")
                appendCaptureTimeline(CaptureTimelineStage.FAILED, "Bulb command rejected: $reason")
                statusMessage = "Bulb command rejected: $reason"
                restoreCaptureWorkloads()
                return@launch
            }
            nativeBulbCommandAccepted = true
            statusMessage = "Bulb command accepted (${seconds}s); waiting for lifecycle/file evidence"
            captureWatchdogJob?.cancel()
            captureWatchdogJob = scope.launch {
                delay(maxOf(CAPTURE_TIMEOUT_MS, seconds * 1000L + 15_000L))
                if (capturePhase is CapturePhase.Requested || capturePhase is CapturePhase.Busy) {
                    val reason = "timeout: missing correlated Bulb lifecycle/file event"
                    captureEvidence.unknown(reason)
                    capturePhase = CapturePhase.OutcomeUnknown(reason)
                    appendCaptureTimeline(CaptureTimelineStage.UNKNOWN, reason)
                    statusMessage = "Bulb outcome unknown — check the card before another shutter"
                }
            }
        }
    }

    /** Request the matching 264 state-0 edge for an active native Bulb capture. */
    fun stopBulb() {
        val phase = capturePhase
        if (!nativeBulbCaptureActive ||
            (phase !is CapturePhase.Requested && phase !is CapturePhase.Busy)
        ) {
            statusMessage = "No native Bulb capture is active"
            return
        }
        val controller = cameraController
        if (controller == null) {
            captureEvidence.unknown("camera disconnected while stopping Bulb")
            capturePhase = CapturePhase.OutcomeUnknown("camera disconnected while stopping Bulb")
            statusMessage = "Bulb outcome unknown — camera disconnected"
            return
        }
        bulbStopJob = scope.launch {
            val result = controller.stopBulb()
            if (result.accepted) {
                statusMessage = "Bulb stop sent; waiting for the image"
            } else {
                val reason = "Bulb stop was not acknowledged: ${result.error ?: result.raw ?: "unknown"}"
                captureEvidence.unknown(reason)
                capturePhase = CapturePhase.OutcomeUnknown(reason)
                appendCaptureTimeline(CaptureTimelineStage.UNKNOWN, reason)
                statusMessage = "Bulb stop not confirmed — check the card before another shutter"
            }
        }
    }

    /** Test seam for capture/SLOW admission state-machine coverage. */
    internal fun testSetCapturePhaseForSafety(phase: CapturePhase) {
        capturePhase = phase
    }

    /** #90 test seam: drive a parsed unsolicited frame without a live device. */
    internal fun testOnCaptureFrame(frame: ResponseParser.Frame) = onCaptureFrame(frame)

    /** #60 test seam: reset the phase to Idle (e.g. between scripted scenarios). */
    internal fun testResetCapturePhase() {
        captureWatchdogJob?.cancel()
        captureWatchdogJob = null
        capturePhase = CapturePhase.Idle
        captureKind = null
        captureSource = null
        nativeBulbCommandAccepted = false
        captureEvidence.clear()
        pendingCapturePublication = null
        captureTimeline = emptyList()
        restoreCaptureWorkloads()
    }

    /** Test seam for a positively observed camera release-mode readback. */
    internal fun testSetCameraParams(params: dev.openpolaris.core.domain.CameraController.Params) {
        camera = params
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

    /** Optional source-aligned Astro WebUI media surface; native 770 remains separate. */
    var astroMediaItems by mutableStateOf<List<AstroMediaItem>?>(null)
        private set
    var astroMediaState by mutableStateOf<AstroMediaState>(AstroMediaState.Idle)
        private set
    var astroMediaPreview by mutableStateOf<AstroMediaPreview?>(null)
        private set
    var astroMediaCategory by mutableStateOf<String?>(null)
        private set
    var astroMediaExportState by mutableStateOf<AstroMediaExportState>(AstroMediaExportState.Idle)
        private set
    var astroWebUiStatus by mutableStateOf<AstroWebUiStatusState>(AstroWebUiStatusState.Idle)
        private set
    var astroWebUiGuide by mutableStateOf<AstroWebUiGuideUiState>(AstroWebUiGuideUiState.Idle)
        private set
    var astroWebUiAutofocus by mutableStateOf<AstroWebUiAutofocusUiState>(AstroWebUiAutofocusUiState.Idle)
        private set
    var astroWebUiFocal by mutableStateOf<AstroWebUiFocalUiState>(AstroWebUiFocalUiState.Idle)
        private set
    var astroWebUiLimits by mutableStateOf<AstroWebUiLimitsUiState>(AstroWebUiLimitsUiState.Idle)
        private set
    var astroWebUiKeepWifi by mutableStateOf<AstroWebUiKeepWifiUiState>(AstroWebUiKeepWifiUiState.Idle)
        private set
    var astroWebUiWifi by mutableStateOf<AstroWebUiWifiUiState>(AstroWebUiWifiUiState.Idle)
        private set
    var astroWebUiLink by mutableStateOf<AstroWebUiLinkUiState>(AstroWebUiLinkUiState.Idle)
        private set
    var astroWebUiTarget by mutableStateOf<AstroWebUiTargetUiState>(AstroWebUiTargetUiState.Idle)
        private set
    var astroWebUiSolve by mutableStateOf<AstroWebUiSolveUiState>(AstroWebUiSolveUiState.Idle)
        private set
    var astroWebUiSolveControl by mutableStateOf<AstroWebUiSolveControlUiState>(AstroWebUiSolveControlUiState.Idle)
        private set
    var astroWebUiRunState by mutableStateOf<AstroWebUiRunStateUiState>(AstroWebUiRunStateUiState.Idle)
        private set
    var astroWebUiSessionControl by mutableStateOf<AstroWebUiSessionControlState>(
        AstroWebUiSessionControlState.Idle,
    )
        private set
    var astroWebUiSite by mutableStateOf<AstroWebUiSiteState>(AstroWebUiSiteState.Idle)
        private set
    var astroWebUiSiteWrite by mutableStateOf<AstroWebUiSiteWriteState>(AstroWebUiSiteWriteState.Idle)
        private set
    var astroWebUiProgramme by mutableStateOf<AstroWebUiProgrammeState>(AstroWebUiProgrammeState.Idle)
        private set
    var astroWebUiProgrammeStartState by mutableStateOf<AstroWebUiProgrammeStartState>(
        AstroWebUiProgrammeStartState.Idle,
    )
        private set
    var astroWebUiAstroCapture by mutableStateOf<AstroWebUiAstroCaptureState>(
        AstroWebUiAstroCaptureState.Idle,
    )
        private set
    var astroWebUiSunState by mutableStateOf<AstroWebUiSunState>(AstroWebUiSunState.Idle)
        private set
    var astroWebUiMotionProgrammeState by mutableStateOf<AstroWebUiMotionProgrammeState>(
        AstroWebUiMotionProgrammeState.Idle,
    )
        private set
    private val astroMediaExportController = AstroMediaExportController(astroMediaExportSink)
    private var astroMediaJob: Job? = null
    private var astroWebUiStatusJob: Job? = null
    private var astroWebUiStatusPollJob: Job? = null
    private var astroWebUiGuideJob: Job? = null
    private var astroWebUiGuidePollJob: Job? = null
    private var astroWebUiAutofocusJob: Job? = null
    private var astroWebUiAutofocusPollJob: Job? = null
    private var astroWebUiFocalJob: Job? = null
    private var astroWebUiLimitsJob: Job? = null
    private var astroWebUiKeepWifiJob: Job? = null
    private var astroWebUiWifiJob: Job? = null
    private var astroWebUiLinkJob: Job? = null
    private var astroWebUiTargetJob: Job? = null
    private var astroWebUiSolveJob: Job? = null
    private var astroWebUiSolveControlJob: Job? = null
    private var astroWebUiSolvePollJob: Job? = null
    private var astroWebUiRunStateJob: Job? = null
    private var astroWebUiRunStatePollJob: Job? = null
    private var astroWebUiSessionControlJob: Job? = null
    private var astroWebUiSiteJob: Job? = null
    private var astroWebUiSiteWriteJob: Job? = null
    private var astroWebUiProgrammeJob: Job? = null
    private var astroWebUiProgrammePollJob: Job? = null
    private var astroWebUiProgrammeControlJob: Job? = null
    private var astroWebUiProgrammeStartJob: Job? = null
    private var astroWebUiAstroCaptureJob: Job? = null
    private var astroWebUiSunJob: Job? = null
    private var astroWebUiMotionProgrammeJob: Job? = null
    private val astroWebUiStatusReadMutex = Mutex()
    private val astroWebUiGuideReadMutex = Mutex()
    private val astroWebUiAutofocusReadMutex = Mutex()
    private val astroWebUiFocalReadMutex = Mutex()
    private val astroWebUiLimitsReadMutex = Mutex()
    private val astroWebUiKeepWifiReadMutex = Mutex()
    private val astroWebUiWifiReadMutex = Mutex()
    private val astroWebUiLinkReadMutex = Mutex()
    private val astroWebUiSolveReadMutex = Mutex()
    private val astroWebUiRunStateReadMutex = Mutex()
    private val astroWebUiProgrammeReadMutex = Mutex()
    private var astroMediaExportJob: Job? = null

    /** True only while an asynchronous action still belongs to this mount session. */
    private fun ownsSession(owner: MountSession?): Boolean =
        owner != null && session === owner && !sessionTearingDown

    fun selectAstroMediaCategory(category: String?) {
        astroMediaCategory = category
    }

    fun setFileType(t: Int) { fileListType = t }
    fun setFilePage(p: Int) { fileListPage = p.coerceAtLeast(0) }

    fun refreshFileList() = scope.launch {
        if (!FeatureFlags.isEnabled("fileManager")) { statusMessage = "File manager disabled by config"; return@launch }
        val s = session ?: return@launch
        runCatching {
            val r = s.request<FileList>(
                Codes.FILE_LIST,
                CommandTable.FILE_LIST.payload(CommandTable.FileListRequest(fileListType, fileListPage)),
            ) { f ->
                FileList.fromFrame(f)
            }
            if (r is MountSession.CmdResult.Ok) {
                fileList = r.value
                statusMessage = "File list: ${r.value?.files?.size ?: 0} file(s)"
            }
        }
    }

    private suspend fun readAstroWebUiStatus(announce: Boolean) {
        astroWebUiStatusReadMutex.withLock {
            if (!FeatureFlags.isEnabled("astroWebUiStatus")) {
                astroWebUiStatus = AstroWebUiStatusState.Failed("Astro WebUI status disabled by config")
                return@withLock
            }
            val ownerSession = session
            if (ownerSession == null) {
                astroWebUiStatus = AstroWebUiStatusState.Failed(
                    "Connect to a mount before reading its Astro WebUI status",
                )
                return@withLock
            }
            astroWebUiStatus = AstroWebUiStatusState.Loading
            val result = astroWebUiStatusClient.read(host, previewPort)
            if (!ownsSession(ownerSession)) return@withLock
            when (result) {
                is AstroWebUiStatusResult.Success -> {
                    astroWebUiStatus = AstroWebUiStatusState.Ready(result.status)
                    if (announce) {
                        statusMessage = if (result.status.active) {
                            "Astro WebUI reports Astro mode active"
                        } else {
                            "Astro WebUI reports Astro mode inactive"
                        }
                    }
                }
                is AstroWebUiStatusResult.Failure -> {
                    astroWebUiStatus = AstroWebUiStatusState.Failed(result.reason)
                    if (announce) statusMessage = result.reason
                }
            }
        }
    }

    fun refreshAstroWebUiStatus() {
        astroWebUiStatusJob?.cancel()
        astroWebUiStatusJob = scope.launch {
            readAstroWebUiStatus(announce = true)
        }
    }

    /**
     * Mirror Astro WebUI's visible Astro-tab polling contract: the resident
     * status is refreshed every two seconds while the mission surface is
     * visible. The poller performs only GET /api/astro reads and is stopped
     * with the owning Compose surface.
     */
    fun startAstroWebUiStatusPolling() {
        astroWebUiStatusPollJob?.cancel()
        if (!FeatureFlags.isEnabled("astroWebUiStatus")) {
            astroWebUiStatus = AstroWebUiStatusState.Failed("Astro WebUI status disabled by config")
            return
        }
        astroWebUiStatusPollJob = scope.launch {
            while (isActive) {
                readAstroWebUiStatus(announce = false)
                delay(2_000L)
            }
        }
    }

    fun stopAstroWebUiStatusPolling() {
        astroWebUiStatusPollJob?.cancel()
        astroWebUiStatusPollJob = null
    }

    private suspend fun readAstroWebUiGuide(announce: Boolean) {
        astroWebUiGuideReadMutex.withLock {
            if (!FeatureFlags.isEnabled("astroWebUiGuideStatus")) {
                astroWebUiGuide = AstroWebUiGuideUiState.Failed("Astro WebUI guide status disabled by config")
                return@withLock
            }
            val ownerSession = session
            if (ownerSession == null) {
                astroWebUiGuide = AstroWebUiGuideUiState.Failed(
                    "Connect to a mount before reading its Astro WebUI guide state",
                )
                return@withLock
            }
            astroWebUiGuide = AstroWebUiGuideUiState.Loading
            val result = astroWebUiGuideClient.read(host, previewPort)
            if (!ownsSession(ownerSession)) return@withLock
            when (result) {
                is AstroWebUiGuideResult.Success -> {
                    astroWebUiGuide = AstroWebUiGuideUiState.Ready(result.state)
                    if (announce) statusMessage = "Astro WebUI guide state refreshed"
                }
                is AstroWebUiGuideResult.Failure -> {
                    astroWebUiGuide = AstroWebUiGuideUiState.Failed(result.reason)
                    if (announce) statusMessage = result.reason
                }
                is AstroWebUiGuideResult.Accepted -> Unit
            }
        }
    }

    fun refreshAstroWebUiGuide() {
        astroWebUiGuideJob?.cancel()
        astroWebUiGuideJob = scope.launch {
            readAstroWebUiGuide(announce = true)
        }
    }

    fun startAstroWebUiGuidePolling() {
        astroWebUiGuidePollJob?.cancel()
        if (!FeatureFlags.isEnabled("astroWebUiGuideStatus")) {
            astroWebUiGuide = AstroWebUiGuideUiState.Failed("Astro WebUI guide status disabled by config")
            return
        }
        astroWebUiGuidePollJob = scope.launch {
            while (isActive) {
                readAstroWebUiGuide(announce = false)
                delay(2_000L)
            }
        }
    }

    fun stopAstroWebUiGuidePolling() {
        astroWebUiGuidePollJob?.cancel()
        astroWebUiGuidePollJob = null
    }

    fun setAstroWebUiGuiding(running: Boolean) {
        astroWebUiGuideJob?.cancel()
        if (!FeatureFlags.isEnabled("astroWebUiGuiding")) {
            astroWebUiGuide = AstroWebUiGuideUiState.Failed(
                "Astro WebUI guiding writes are disabled until solve, ownership and recovery are qualified",
            )
            statusMessage = "Astro WebUI guiding writes are disabled by config"
            return
        }
        val ownerSession = session
        if (ownerSession == null) {
            astroWebUiGuide = AstroWebUiGuideUiState.Failed(
                "Connect to a mount before changing Astro WebUI guiding",
            )
            statusMessage = "Connect to a mount before changing Astro WebUI guiding"
            return
        }
        astroWebUiGuide = AstroWebUiGuideUiState.Loading
        astroWebUiGuideJob = scope.launch {
            if (!ownsSession(ownerSession)) return@launch
            val result = try {
                astroWebUiGuideClient.setRunning(host, previewPort, running)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                AstroWebUiGuideResult.Failure(error.message ?: "Astro WebUI guide change failed")
            }
            if (!ownsSession(ownerSession)) return@launch
            when (result) {
                is AstroWebUiGuideResult.Accepted -> {
                    astroWebUiGuide = AstroWebUiGuideUiState.Accepted(result.running)
                    statusMessage = "Astro WebUI guide change accepted; confirming by readback"
                    readAstroWebUiGuide(announce = false)
                }
                is AstroWebUiGuideResult.Success -> astroWebUiGuide = AstroWebUiGuideUiState.Ready(result.state)
                is AstroWebUiGuideResult.Failure -> {
                    astroWebUiGuide = AstroWebUiGuideUiState.Failed(result.reason)
                    statusMessage = result.reason
                }
            }
        }
    }

    fun previewAstroWebUiGoto(request: AstroWebUiGotoRequest) {
        astroWebUiTargetJob?.cancel()
        if (!FeatureFlags.isEnabled("astroWebUiTargetPreview")) {
            astroWebUiTarget = AstroWebUiTargetUiState.Failed(
                "Astro WebUI GoTo preview is disabled by config",
            )
            statusMessage = "Astro WebUI GoTo preview is disabled by config"
            return
        }
        val ownerSession = session
        if (ownerSession == null) {
            astroWebUiTarget = AstroWebUiTargetUiState.Failed(
                "Connect to a mount before previewing an Astro WebUI GoTo",
            )
            statusMessage = "Connect to a mount before previewing an Astro WebUI GoTo"
            return
        }
        astroWebUiTarget = AstroWebUiTargetUiState.Loading
        astroWebUiTargetJob = scope.launch {
            if (!ownsSession(ownerSession)) return@launch
            val result = try {
                astroWebUiTargetClient.previewGoto(host, previewPort, request)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                AstroWebUiTargetResult.Failure(error.message ?: "Astro WebUI GoTo preview failed")
            }
            if (!ownsSession(ownerSession)) return@launch
            when (result) {
                is AstroWebUiTargetResult.GotoPreview -> {
                    astroWebUiTarget = AstroWebUiTargetUiState.Preview(result.request)
                    statusMessage = "GoTo preview ready; confirmation is still required"
                }
                is AstroWebUiTargetResult.Failure -> {
                    astroWebUiTarget = AstroWebUiTargetUiState.Failed(result.reason)
                    statusMessage = result.reason
                }
                else -> Unit
            }
        }
    }

    fun startAstroWebUiGoto(request: AstroWebUiGotoRequest) {
        astroWebUiTargetJob?.cancel()
        if (!FeatureFlags.isEnabled("astroWebUiTargetWrites")) {
            astroWebUiTarget = AstroWebUiTargetUiState.Failed(
                "Astro WebUI GoTo writes are disabled until motion and recovery qualification",
            )
            statusMessage = "Astro WebUI GoTo writes are disabled by config"
            return
        }
        if ((astroWebUiTarget as? AstroWebUiTargetUiState.Preview)?.request != request) {
            astroWebUiTarget = AstroWebUiTargetUiState.Failed(
                "Preview this exact target before starting the GoTo",
            )
            statusMessage = "Preview this exact target before starting the GoTo"
            return
        }
        val ownerSession = session
        if (ownerSession == null) {
            astroWebUiTarget = AstroWebUiTargetUiState.Failed(
                "Connect to a mount before starting an Astro WebUI GoTo",
            )
            statusMessage = "Connect to a mount before starting an Astro WebUI GoTo"
            return
        }
        astroWebUiTarget = AstroWebUiTargetUiState.Loading
        astroWebUiTargetJob = scope.launch {
            if (!ownsSession(ownerSession)) return@launch
            val result = try {
                astroWebUiTargetClient.startGoto(host, previewPort, request)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                AstroWebUiTargetResult.Failure(error.message ?: "Astro WebUI GoTo failed")
            }
            if (!ownsSession(ownerSession)) return@launch
            when (result) {
                is AstroWebUiTargetResult.GotoAccepted -> {
                    astroWebUiTarget = AstroWebUiTargetUiState.Accepted("GoTo")
                    statusMessage = "Astro WebUI GoTo accepted; resident status remains authoritative"
                    refreshAstroWebUiStatus()
                }
                is AstroWebUiTargetResult.Failure -> {
                    astroWebUiTarget = AstroWebUiTargetUiState.Failed(result.reason)
                    statusMessage = result.reason
                }
                else -> Unit
            }
        }
    }

    fun alignAstroWebUiCompass(request: AstroWebUiAlignRequest) {
        astroWebUiTargetJob?.cancel()
        if (!FeatureFlags.isEnabled("astroWebUiTargetWrites")) {
            astroWebUiTarget = AstroWebUiTargetUiState.Failed(
                "Astro WebUI compass alignment is disabled until alignment and recovery qualification",
            )
            statusMessage = "Astro WebUI compass alignment is disabled by config"
            return
        }
        val ownerSession = session
        if (ownerSession == null) {
            astroWebUiTarget = AstroWebUiTargetUiState.Failed(
                "Connect to a mount before compass alignment",
            )
            statusMessage = "Connect to a mount before compass alignment"
            return
        }
        astroWebUiTarget = AstroWebUiTargetUiState.Loading
        astroWebUiTargetJob = scope.launch {
            if (!ownsSession(ownerSession)) return@launch
            val result = try {
                astroWebUiTargetClient.align(host, previewPort, request)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                AstroWebUiTargetResult.Failure(error.message ?: "Astro WebUI compass alignment failed")
            }
            if (!ownsSession(ownerSession)) return@launch
            when (result) {
                is AstroWebUiTargetResult.AlignmentAccepted -> {
                    astroWebUiTarget = AstroWebUiTargetUiState.Accepted("compass alignment")
                    statusMessage = "Compass alignment accepted; resident status remains authoritative"
                    refreshAstroWebUiStatus()
                }
                is AstroWebUiTargetResult.Failure -> {
                    astroWebUiTarget = AstroWebUiTargetUiState.Failed(result.reason)
                    statusMessage = result.reason
                }
                else -> Unit
            }
        }
    }

    private suspend fun readAstroWebUiWifi(announce: Boolean) {
        astroWebUiWifiReadMutex.withLock {
            if (!FeatureFlags.isEnabled("astroWebUiWifiStatus")) {
                astroWebUiWifi = AstroWebUiWifiUiState.Failed(
                    "Astro WebUI Wi-Fi status disabled by config",
                )
                return@withLock
            }
            val ownerSession = session
            if (ownerSession == null) {
                astroWebUiWifi = AstroWebUiWifiUiState.Failed(
                    "Connect to a mount before reading Astro WebUI Wi-Fi state",
                )
                return@withLock
            }
            astroWebUiWifi = AstroWebUiWifiUiState.Loading
            val result = astroWebUiWifiClient.read(host, previewPort)
            if (!ownsSession(ownerSession)) return@withLock
            when (result) {
                is AstroWebUiWifiResult.Success -> {
                    astroWebUiWifi = AstroWebUiWifiUiState.Ready(result.state)
                    if (announce) statusMessage = "Astro WebUI Wi-Fi state refreshed"
                }
                is AstroWebUiWifiResult.Failure -> {
                    astroWebUiWifi = AstroWebUiWifiUiState.Failed(result.reason)
                    if (announce) statusMessage = result.reason
                }
            }
        }
    }

    fun refreshAstroWebUiWifi() {
        astroWebUiWifiJob?.cancel()
        astroWebUiWifiJob = scope.launch {
            readAstroWebUiWifi(announce = true)
        }
    }

    private suspend fun readAstroWebUiLink(announce: Boolean) {
        astroWebUiLinkReadMutex.withLock {
            if (!FeatureFlags.isEnabled("astroWebUiLinkStatus")) {
                astroWebUiLink = AstroWebUiLinkUiState.Failed("Astro WebUI link telemetry disabled by config")
                return@withLock
            }
            val ownerSession = session
            if (ownerSession == null) {
                astroWebUiLink = AstroWebUiLinkUiState.Failed(
                    "Connect to a mount before reading Astro WebUI link telemetry",
                )
                return@withLock
            }
            astroWebUiLink = AstroWebUiLinkUiState.Loading
            val result = astroWebUiLinkClient.read(host, previewPort)
            if (!ownsSession(ownerSession)) return@withLock
            when (result) {
                is AstroWebUiLinkResult.Success -> {
                    astroWebUiLink = AstroWebUiLinkUiState.Ready(result.state)
                    if (announce) statusMessage = "Astro WebUI control-link telemetry refreshed"
                }
                is AstroWebUiLinkResult.Failure -> {
                    astroWebUiLink = AstroWebUiLinkUiState.Failed(result.reason)
                    if (announce) statusMessage = result.reason
                }
            }
        }
    }

    fun refreshAstroWebUiLink() {
        astroWebUiLinkJob?.cancel()
        astroWebUiLinkJob = scope.launch {
            readAstroWebUiLink(announce = true)
        }
    }

    private suspend fun readAstroWebUiKeepWifi(announce: Boolean) {
        astroWebUiKeepWifiReadMutex.withLock {
            if (!FeatureFlags.isEnabled("astroWebUiKeepWifiStatus")) {
                astroWebUiKeepWifi = AstroWebUiKeepWifiUiState.Failed(
                    "Astro WebUI keep-Wi-Fi status disabled by config",
                )
                return@withLock
            }
            val ownerSession = session
            if (ownerSession == null) {
                astroWebUiKeepWifi = AstroWebUiKeepWifiUiState.Failed(
                    "Connect to a mount before reading Astro WebUI keep-Wi-Fi state",
                )
                return@withLock
            }
            astroWebUiKeepWifi = AstroWebUiKeepWifiUiState.Loading
            val result = astroWebUiKeepWifiClient.read(host, previewPort)
            if (!ownsSession(ownerSession)) return@withLock
            when (result) {
                is AstroWebUiKeepWifiResult.Success -> {
                    astroWebUiKeepWifi = AstroWebUiKeepWifiUiState.Ready(result.state)
                    if (announce) statusMessage = "Astro WebUI keep-Wi-Fi state refreshed"
                }
                is AstroWebUiKeepWifiResult.Accepted -> Unit
                is AstroWebUiKeepWifiResult.Failure -> {
                    astroWebUiKeepWifi = AstroWebUiKeepWifiUiState.Failed(result.reason)
                    if (announce) statusMessage = result.reason
                }
            }
        }
    }

    fun refreshAstroWebUiKeepWifi() {
        astroWebUiKeepWifiJob?.cancel()
        astroWebUiKeepWifiJob = scope.launch {
            readAstroWebUiKeepWifi(announce = true)
        }
    }

    fun setAstroWebUiKeepWifi(enabled: Boolean, forced: Boolean) {
        astroWebUiKeepWifiJob?.cancel()
        if (!FeatureFlags.isEnabled("astroWebUiKeepWifiWrite")) {
            astroWebUiKeepWifi = AstroWebUiKeepWifiUiState.Failed(
                "Astro WebUI keep-Wi-Fi writes are disabled until recovery qualification",
            )
            statusMessage = "Astro WebUI keep-Wi-Fi writes are disabled by config"
            return
        }
        val ownerSession = session
        if (ownerSession == null) {
            astroWebUiKeepWifi = AstroWebUiKeepWifiUiState.Failed(
                "Connect to a mount before changing Astro WebUI keep-Wi-Fi",
            )
            statusMessage = "Connect to a mount before changing Astro WebUI keep-Wi-Fi"
            return
        }
        astroWebUiKeepWifi = AstroWebUiKeepWifiUiState.Loading
        astroWebUiKeepWifiJob = scope.launch {
            if (!ownsSession(ownerSession)) return@launch
            val result = try {
                astroWebUiKeepWifiClient.set(host, previewPort, enabled, forced)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                AstroWebUiKeepWifiResult.Failure(
                    error.message ?: "Astro WebUI keep-Wi-Fi change failed",
                )
            }
            if (!ownsSession(ownerSession)) return@launch
            when (result) {
                is AstroWebUiKeepWifiResult.Accepted -> {
                    astroWebUiKeepWifi = AstroWebUiKeepWifiUiState.Accepted(result.enabled, result.forced)
                    statusMessage = "Astro WebUI keep-Wi-Fi change accepted; confirming by readback"
                    readAstroWebUiKeepWifi(announce = false)
                }
                is AstroWebUiKeepWifiResult.Success -> astroWebUiKeepWifi = AstroWebUiKeepWifiUiState.Ready(result.state)
                is AstroWebUiKeepWifiResult.Failure -> {
                    astroWebUiKeepWifi = AstroWebUiKeepWifiUiState.Failed(result.reason)
                    statusMessage = result.reason
                }
            }
        }
    }

    private suspend fun readAstroWebUiLimits(announce: Boolean) {
        astroWebUiLimitsReadMutex.withLock {
            if (!FeatureFlags.isEnabled("astroWebUiLimitsStatus")) {
                astroWebUiLimits = AstroWebUiLimitsUiState.Failed(
                    "Astro WebUI travel-limit status disabled by config",
                )
                return@withLock
            }
            val ownerSession = session
            if (ownerSession == null) {
                astroWebUiLimits = AstroWebUiLimitsUiState.Failed(
                    "Connect to a mount before reading Astro WebUI travel-limit state",
                )
                return@withLock
            }
            astroWebUiLimits = AstroWebUiLimitsUiState.Loading
            val result = astroWebUiLimitsClient.read(host, previewPort)
            if (!ownsSession(ownerSession)) return@withLock
            when (result) {
                is AstroWebUiLimitsResult.Success -> {
                    astroWebUiLimits = AstroWebUiLimitsUiState.Ready(result.state)
                    if (announce) statusMessage = "Astro WebUI travel-limit state refreshed"
                }
                is AstroWebUiLimitsResult.Failure -> {
                    astroWebUiLimits = AstroWebUiLimitsUiState.Failed(result.reason)
                    if (announce) statusMessage = result.reason
                }
            }
        }
    }

    fun refreshAstroWebUiLimits() {
        astroWebUiLimitsJob?.cancel()
        astroWebUiLimitsJob = scope.launch {
            readAstroWebUiLimits(announce = true)
        }
    }

    private suspend fun readAstroWebUiFocal(announce: Boolean) {
        astroWebUiFocalReadMutex.withLock {
            if (!FeatureFlags.isEnabled("astroWebUiFocalStatus")) {
                astroWebUiFocal = AstroWebUiFocalUiState.Failed(
                    "Astro WebUI focal status disabled by config",
                )
                return@withLock
            }
            val ownerSession = session
            if (ownerSession == null) {
                astroWebUiFocal = AstroWebUiFocalUiState.Failed(
                    "Connect to a mount before reading Astro WebUI focal state",
                )
                return@withLock
            }
            astroWebUiFocal = AstroWebUiFocalUiState.Loading
            val result = astroWebUiFocalClient.read(host, previewPort)
            if (!ownsSession(ownerSession)) return@withLock
            when (result) {
                is AstroWebUiFocalResult.Success -> {
                    astroWebUiFocal = AstroWebUiFocalUiState.Ready(result.state)
                    if (announce) statusMessage = "Astro WebUI focal state refreshed"
                }
                is AstroWebUiFocalResult.Failure -> {
                    astroWebUiFocal = AstroWebUiFocalUiState.Failed(result.reason)
                    if (announce) statusMessage = result.reason
                }
                is AstroWebUiFocalResult.Accepted -> Unit
            }
        }
    }

    fun refreshAstroWebUiFocal() {
        astroWebUiFocalJob?.cancel()
        astroWebUiFocalJob = scope.launch {
            readAstroWebUiFocal(announce = true)
        }
    }

    fun setAstroWebUiFocalOverride(focalMm: Double?) {
        astroWebUiFocalJob?.cancel()
        if (!FeatureFlags.isEnabled("astroWebUiFocalWrite")) {
            astroWebUiFocal = AstroWebUiFocalUiState.Failed(
                "Astro WebUI focal writes are disabled until solve and recovery qualification",
            )
            statusMessage = "Astro WebUI focal writes are disabled by config"
            return
        }
        if (focalMm != null && (focalMm.isNaN() || focalMm.isInfinite() || focalMm !in 1.0..100_000.0)) {
            astroWebUiFocal = AstroWebUiFocalUiState.Failed(
                "Focal override must be 1-100000 mm, or auto",
            )
            statusMessage = "Focal override is out of range"
            return
        }
        val ownerSession = session
        if (ownerSession == null) {
            astroWebUiFocal = AstroWebUiFocalUiState.Failed(
                "Connect to a mount before changing Astro WebUI focal override",
            )
            statusMessage = "Connect to a mount before changing Astro WebUI focal override"
            return
        }
        astroWebUiFocal = AstroWebUiFocalUiState.Loading
        astroWebUiFocalJob = scope.launch {
            if (!ownsSession(ownerSession)) return@launch
            val result = try {
                astroWebUiFocalClient.setOverride(host, previewPort, focalMm)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                AstroWebUiFocalResult.Failure(
                    error.message ?: "Astro WebUI focal change failed",
                )
            }
            if (!ownsSession(ownerSession)) return@launch
            when (result) {
                is AstroWebUiFocalResult.Accepted -> {
                    astroWebUiFocal = AstroWebUiFocalUiState.Accepted(result.focalMm)
                    statusMessage = "Astro WebUI focal change accepted; confirming by readback"
                    readAstroWebUiFocal(announce = false)
                }
                is AstroWebUiFocalResult.Success -> astroWebUiFocal = AstroWebUiFocalUiState.Ready(result.state)
                is AstroWebUiFocalResult.Failure -> {
                    astroWebUiFocal = AstroWebUiFocalUiState.Failed(result.reason)
                    statusMessage = result.reason
                }
            }
        }
    }

    private suspend fun readAstroWebUiAutofocus(announce: Boolean) {
        astroWebUiAutofocusReadMutex.withLock {
            if (!FeatureFlags.isEnabled("astroWebUiAutofocusStatus")) {
                astroWebUiAutofocus = AstroWebUiAutofocusUiState.Failed(
                    "Astro WebUI autofocus status disabled by config",
                )
                return@withLock
            }
            val ownerSession = session
            if (ownerSession == null) {
                astroWebUiAutofocus = AstroWebUiAutofocusUiState.Failed(
                    "Connect to a mount before reading Astro WebUI autofocus",
                )
                return@withLock
            }
            astroWebUiAutofocus = AstroWebUiAutofocusUiState.Loading
            val result = astroWebUiAutofocusClient.read(host, previewPort)
            if (!ownsSession(ownerSession)) return@withLock
            when (result) {
                is AstroWebUiAutofocusResult.Success -> {
                    astroWebUiAutofocus = AstroWebUiAutofocusUiState.Ready(result.state)
                    if (announce) statusMessage = "Astro WebUI autofocus state refreshed"
                }
                is AstroWebUiAutofocusResult.Failure -> {
                    astroWebUiAutofocus = AstroWebUiAutofocusUiState.Failed(result.reason)
                    if (announce) statusMessage = result.reason
                }
                is AstroWebUiAutofocusResult.Accepted -> Unit
            }
        }
    }

    fun refreshAstroWebUiAutofocus() {
        astroWebUiAutofocusJob?.cancel()
        astroWebUiAutofocusJob = scope.launch {
            readAstroWebUiAutofocus(announce = true)
        }
    }

    fun startAstroWebUiAutofocusPolling() {
        astroWebUiAutofocusPollJob?.cancel()
        if (!FeatureFlags.isEnabled("astroWebUiAutofocusStatus")) {
            astroWebUiAutofocus = AstroWebUiAutofocusUiState.Failed(
                "Astro WebUI autofocus status disabled by config",
            )
            return
        }
        astroWebUiAutofocusPollJob = scope.launch {
            while (isActive) {
                readAstroWebUiAutofocus(announce = false)
                delay(2_000L)
            }
        }
    }

    fun stopAstroWebUiAutofocusPolling() {
        astroWebUiAutofocusPollJob?.cancel()
        astroWebUiAutofocusPollJob = null
    }

    fun startAstroWebUiAutofocus(parameters: AstroWebUiAutofocusParameters) {
        runAstroWebUiAutofocusWrite("start", parameters)
    }

    fun stopAstroWebUiAutofocus() {
        astroWebUiAutofocusJob?.cancel()
        if (!FeatureFlags.isEnabled("astroWebUiAutofocus")) {
            astroWebUiAutofocus = AstroWebUiAutofocusUiState.Failed(
                "Astro WebUI autofocus is disabled until camera, focus and recovery qualification",
            )
            statusMessage = "Astro WebUI autofocus is disabled by config"
            return
        }
        val ownerSession = session
        if (ownerSession == null) {
            astroWebUiAutofocus = AstroWebUiAutofocusUiState.Failed(
                "Connect to a mount before stopping Astro WebUI autofocus",
            )
            statusMessage = "Connect to a mount before stopping Astro WebUI autofocus"
            return
        }
        astroWebUiAutofocus = AstroWebUiAutofocusUiState.Loading
        astroWebUiAutofocusJob = scope.launch {
            if (!ownsSession(ownerSession)) return@launch
            val result = try {
                astroWebUiAutofocusClient.stop(host, previewPort)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                AstroWebUiAutofocusResult.Failure(
                    error.message ?: "Astro WebUI autofocus stop failed",
                )
            }
            if (!ownsSession(ownerSession)) return@launch
            when (result) {
                is AstroWebUiAutofocusResult.Accepted -> {
                    astroWebUiAutofocus = AstroWebUiAutofocusUiState.Accepted(result.action)
                    statusMessage = "Astro WebUI autofocus stop accepted; confirming by readback"
                    readAstroWebUiAutofocus(announce = false)
                }
                is AstroWebUiAutofocusResult.Success -> astroWebUiAutofocus = AstroWebUiAutofocusUiState.Ready(result.state)
                is AstroWebUiAutofocusResult.Failure -> {
                    astroWebUiAutofocus = AstroWebUiAutofocusUiState.Failed(result.reason)
                    statusMessage = result.reason
                }
            }
        }
    }

    private fun runAstroWebUiAutofocusWrite(
        action: String,
        parameters: AstroWebUiAutofocusParameters,
    ) {
        astroWebUiAutofocusJob?.cancel()
        if (!FeatureFlags.isEnabled("astroWebUiAutofocus")) {
            astroWebUiAutofocus = AstroWebUiAutofocusUiState.Failed(
                "Astro WebUI autofocus is disabled until camera, focus and recovery qualification",
            )
            statusMessage = "Astro WebUI autofocus is disabled by config"
            return
        }
        val ownerSession = session
        if (ownerSession == null) {
            astroWebUiAutofocus = AstroWebUiAutofocusUiState.Failed(
                "Connect to a mount before starting Astro WebUI autofocus",
            )
            statusMessage = "Connect to a mount before starting Astro WebUI autofocus"
            return
        }
        astroWebUiAutofocus = AstroWebUiAutofocusUiState.Loading
        astroWebUiAutofocusJob = scope.launch {
            if (!ownsSession(ownerSession)) return@launch
            val result = try {
                astroWebUiAutofocusClient.start(host, previewPort, parameters)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                AstroWebUiAutofocusResult.Failure(
                    error.message ?: "Astro WebUI autofocus $action failed",
                )
            }
            if (!ownsSession(ownerSession)) return@launch
            when (result) {
                is AstroWebUiAutofocusResult.Accepted -> {
                    astroWebUiAutofocus = AstroWebUiAutofocusUiState.Accepted(result.action)
                    statusMessage = "Astro WebUI autofocus start accepted; progress is read back from the resident job"
                    readAstroWebUiAutofocus(announce = false)
                }
                is AstroWebUiAutofocusResult.Success -> astroWebUiAutofocus = AstroWebUiAutofocusUiState.Ready(result.state)
                is AstroWebUiAutofocusResult.Failure -> {
                    astroWebUiAutofocus = AstroWebUiAutofocusUiState.Failed(result.reason)
                    statusMessage = result.reason
                }
            }
        }
    }

    private suspend fun readAstroWebUiSolve(announce: Boolean) {
        astroWebUiSolveReadMutex.withLock {
            if (!FeatureFlags.isEnabled("astroWebUiSolveStatus")) {
                astroWebUiSolve = AstroWebUiSolveUiState.Failed("Astro WebUI solve status disabled by config")
                return@withLock
            }
            val ownerSession = session
            if (ownerSession == null) {
                astroWebUiSolve = AstroWebUiSolveUiState.Failed(
                    "Connect to a mount before reading its Astro WebUI solve state",
                )
                return@withLock
            }
            astroWebUiSolve = AstroWebUiSolveUiState.Loading
            val result = astroWebUiSolveClient.read(host, previewPort)
            if (!ownsSession(ownerSession)) return@withLock
            when (result) {
                is AstroWebUiSolveResult.Success -> {
                    astroWebUiSolve = AstroWebUiSolveUiState.Ready(result.state)
                    if (announce) statusMessage = "Astro WebUI solve state refreshed"
                }
                is AstroWebUiSolveResult.Failure -> {
                    astroWebUiSolve = AstroWebUiSolveUiState.Failed(result.reason)
                    if (announce) statusMessage = result.reason
                }
            }
        }
    }

    fun refreshAstroWebUiSolve() {
        astroWebUiSolveJob?.cancel()
        astroWebUiSolveJob = scope.launch {
            readAstroWebUiSolve(announce = true)
        }
    }

    /** Poll the source-aligned solve files only while the Mission surface owns the read. */
    fun startAstroWebUiSolvePolling() {
        astroWebUiSolvePollJob?.cancel()
        if (!FeatureFlags.isEnabled("astroWebUiSolveStatus")) {
            astroWebUiSolve = AstroWebUiSolveUiState.Failed("Astro WebUI solve status disabled by config")
            return
        }
        astroWebUiSolvePollJob = scope.launch {
            while (isActive) {
                readAstroWebUiSolve(announce = false)
                delay(2_000L)
            }
        }
    }

    fun stopAstroWebUiSolvePolling() {
        astroWebUiSolvePollJob?.cancel()
        astroWebUiSolvePollJob = null
    }

    private fun runAstroWebUiSolveControl(action: String, block: suspend (String, Int) -> AstroWebUiSolveControlResult) {
        astroWebUiSolveControlJob?.cancel()
        if (!FeatureFlags.isEnabled("astroWebUiSolveControls")) {
            astroWebUiSolveControl = AstroWebUiSolveControlUiState.Failed(
                "Astro WebUI solve controls are disabled until solver and alignment recovery are qualified",
            )
            statusMessage = "Astro WebUI solve controls remain disabled by config"
            return
        }
        val ownerSession = session
        if (ownerSession == null) {
            astroWebUiSolveControl = AstroWebUiSolveControlUiState.Failed(
                "Connect to a mount before using Astro WebUI solve controls",
            )
            statusMessage = "Connect to a mount before using Astro WebUI solve controls"
            return
        }
        astroWebUiSolveControl = AstroWebUiSolveControlUiState.Loading
        astroWebUiSolveControlJob = scope.launch {
            val result = try {
                block(host, previewPort)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                AstroWebUiSolveControlResult.Failure(
                    error.message ?: "Astro WebUI solve $action failed",
                )
            }
            if (!ownsSession(ownerSession)) return@launch
            when (result) {
                is AstroWebUiSolveControlResult.Started -> {
                    astroWebUiSolveControl = AstroWebUiSolveControlUiState.Accepted("Solve ${result.request.mode.wireValue} started")
                    statusMessage = "Astro WebUI solve started; resident run state remains authoritative"
                    refreshAstroWebUiSolve()
                }
                is AstroWebUiSolveControlResult.AlreadyRunning -> {
                    astroWebUiSolveControl = AstroWebUiSolveControlUiState.Failed(
                        result.reason + (result.elapsedSeconds?.let { " (${it}s elapsed)" } ?: ""),
                    )
                    statusMessage = result.reason
                }
                is AstroWebUiSolveControlResult.Applied -> {
                    astroWebUiSolveControl = AstroWebUiSolveControlUiState.Accepted(
                        "Last solution applied (${result.ageSeconds}s old)",
                    )
                    statusMessage = "Astro WebUI last solution applied; verify mount telemetry"
                    refreshAstroWebUiSolve()
                }
                AstroWebUiSolveControlResult.Cancelled -> {
                    astroWebUiSolveControl = AstroWebUiSolveControlUiState.Accepted("Solve cancelled")
                    statusMessage = "Astro WebUI solve cancelled"
                    refreshAstroWebUiSolve()
                }
                is AstroWebUiSolveControlResult.NothingToCancel -> {
                    astroWebUiSolveControl = AstroWebUiSolveControlUiState.Failed(result.reason)
                    statusMessage = result.reason
                }
                is AstroWebUiSolveControlResult.Failure -> {
                    astroWebUiSolveControl = AstroWebUiSolveControlUiState.Failed(result.reason)
                    statusMessage = result.reason
                }
            }
        }
    }

    fun startAstroWebUiSolve(mode: AstroWebUiSolveMode, apply: Boolean = false) =
        runAstroWebUiSolveControl("start") { host, port ->
            astroWebUiSolveControlClient.start(host, port, AstroWebUiSolveRequest(mode, apply))
        }

    fun applyAstroWebUiSolve() =
        runAstroWebUiSolveControl("apply") { host, port ->
            astroWebUiSolveControlClient.apply(host, port)
        }

    fun cancelAstroWebUiSolve() =
        runAstroWebUiSolveControl("cancel") { host, port ->
            astroWebUiSolveControlClient.cancel(host, port)
        }

    private suspend fun readAstroWebUiRunState(readMount: Boolean, announce: Boolean) {
        astroWebUiRunStateReadMutex.withLock {
            if (!FeatureFlags.isEnabled("astroWebUiRunState")) {
                astroWebUiRunState = AstroWebUiRunStateUiState.Failed(
                    "Astro WebUI run state disabled by config",
                )
                return@withLock
            }
            val ownerSession = session
            if (ownerSession == null) {
                astroWebUiRunState = AstroWebUiRunStateUiState.Failed(
                    "Connect to a mount before reading Astro WebUI run state",
                )
                return@withLock
            }
            astroWebUiRunState = AstroWebUiRunStateUiState.Loading
            val result = astroWebUiRunStateClient.read(host, previewPort, readMount)
            if (!ownsSession(ownerSession)) return@withLock
            when (result) {
                is AstroWebUiRunStateResult.Success -> {
                    astroWebUiRunState = AstroWebUiRunStateUiState.Ready(result.state)
                    if (announce) {
                        statusMessage = if (readMount) {
                            "Astro WebUI mount position read"
                        } else {
                            "Astro WebUI run state refreshed"
                        }
                    }
                }
                is AstroWebUiRunStateResult.Failure -> {
                    astroWebUiRunState = AstroWebUiRunStateUiState.Failed(result.reason)
                    if (announce) statusMessage = result.reason
                }
            }
        }
    }

    fun refreshAstroWebUiRunState() {
        astroWebUiRunStateJob?.cancel()
        astroWebUiRunStateJob = scope.launch {
            readAstroWebUiRunState(readMount = false, announce = true)
        }
    }

    fun readAstroWebUiMountPosition() {
        astroWebUiRunStateJob?.cancel()
        astroWebUiRunStateJob = scope.launch {
            readAstroWebUiRunState(readMount = true, announce = true)
        }
    }

    /** Poll file/log-backed state only; mount=1 remains explicit-action-only. */
    fun startAstroWebUiRunStatePolling() {
        astroWebUiRunStatePollJob?.cancel()
        if (!FeatureFlags.isEnabled("astroWebUiRunState")) {
            astroWebUiRunState = AstroWebUiRunStateUiState.Failed(
                "Astro WebUI run state disabled by config",
            )
            return
        }
        astroWebUiRunStatePollJob = scope.launch {
            while (isActive) {
                readAstroWebUiRunState(readMount = false, announce = false)
                delay(2_000L)
            }
        }
    }

    fun stopAstroWebUiRunStatePolling() {
        astroWebUiRunStatePollJob?.cancel()
        astroWebUiRunStatePollJob = null
    }

    private fun runAstroWebUiSessionControl(
        action: String,
        requiresMotionBoundary: Boolean = false,
        request: suspend (AstroWebUiSessionControlClient) -> AstroWebUiSessionControlResult,
    ) {
        if (astroWebUiSessionControl is AstroWebUiSessionControlState.Working) return
        val ownerSession = session
        astroWebUiSessionControlJob?.cancel()
        astroWebUiSessionControlJob = scope.launch {
            if (!FeatureFlags.isEnabled("astroWebUiSessionControls")) {
                astroWebUiSessionControl = AstroWebUiSessionControlState.Failed(
                    "Astro WebUI session controls are disabled by config",
                )
                return@launch
            }
            if (ownerSession == null) {
                astroWebUiSessionControl = AstroWebUiSessionControlState.Failed(
                    "Connect to a mount before changing its Astro WebUI session",
                )
                return@launch
            }
            if (!ownsSession(ownerSession)) return@launch
            if (requiresMotionBoundary && !stopSlowMotionForModeChange()) return@launch
            astroWebUiSessionControl = AstroWebUiSessionControlState.Working(action)
            val result = request(astroWebUiSessionControlClient)
            if (!ownsSession(ownerSession)) return@launch
            when (result) {
                is AstroWebUiSessionControlResult.Accepted -> {
                    astroWebUiSessionControl = AstroWebUiSessionControlState.Accepted(result.action)
                    statusMessage = "Astro WebUI " + result.action + " request accepted; confirming by status read"
                    readAstroWebUiStatus(announce = false)
                }
                is AstroWebUiSessionControlResult.Failure -> {
                    astroWebUiSessionControl = AstroWebUiSessionControlState.Failed(result.reason)
                    statusMessage = result.reason
                }
            }
        }
    }

    fun enterAstroWebUi() {
        runAstroWebUiSessionControl("enter") { client ->
            client.enter(host, previewPort)
        }
    }

    fun leaveAstroWebUi() {
        runAstroWebUiSessionControl("leave", requiresMotionBoundary = true) { client ->
            client.leave(host, previewPort)
        }
    }

    fun setAstroWebUiTracking(request: AstroWebUiTrackRequest) {
        runAstroWebUiSessionControl(
            if (request.on) "start tracking" else "stop tracking",
            requiresMotionBoundary = true,
        ) { client ->
            client.setTracking(host, previewPort, request)
        }
    }

    fun refreshAstroWebUiSite() {
        astroWebUiSiteJob?.cancel()
        astroWebUiSiteJob = scope.launch {
            if (!FeatureFlags.isEnabled("astroWebUiSite")) {
                astroWebUiSite = AstroWebUiSiteState.Failed("Astro WebUI site disabled by config")
                return@launch
            }
            val ownerSession = session
            if (ownerSession == null) {
                astroWebUiSite = AstroWebUiSiteState.Failed("Connect to a mount before reading its Astro WebUI site")
                return@launch
            }
            astroWebUiSite = AstroWebUiSiteState.Loading
            val result = astroWebUiSiteClient.read(host, previewPort)
            if (!ownsSession(ownerSession)) return@launch
            when (result) {
                is AstroWebUiSiteResult.Success -> {
                    astroWebUiSite = AstroWebUiSiteState.Ready(result.site)
                    statusMessage = if (result.site.havePosition) {
                        "Astro WebUI site loaded"
                    } else {
                        "Astro WebUI has no saved observing site"
                    }
                }
                is AstroWebUiSiteResult.Failure -> {
                    astroWebUiSite = AstroWebUiSiteState.Failed(result.reason)
                    statusMessage = result.reason
                }
            }
        }
    }

    fun saveAstroWebUiSite() {
        if (!FeatureFlags.isEnabled("astroWebUiSiteWrite")) {
            astroWebUiSiteWrite = AstroWebUiSiteWriteState.Failed(
                "Astro WebUI site writes are disabled until mutation and recovery are qualified",
            )
            statusMessage = "Astro WebUI site save is disabled by config"
            return
        }
        val ownerSession = session
        if (ownerSession == null) {
            astroWebUiSiteWrite = AstroWebUiSiteWriteState.Failed(
                "Connect to a mount before saving its Astro WebUI site",
            )
            statusMessage = "Connect to a mount before saving the Astro WebUI site"
            return
        }
        val (latitude, longitude) = parsedLocation() ?: run {
            astroWebUiSiteWrite = AstroWebUiSiteWriteState.Failed(
                "Enter a valid observer latitude and longitude first",
            )
            statusMessage = "Enter a valid observer latitude and longitude first"
            return
        }
        if (astroWebUiSiteWriteJob?.isActive == true) return

        astroWebUiSiteWriteJob = scope.launch {
            astroWebUiSiteWrite = AstroWebUiSiteWriteState.Working(latitude, longitude)
            val result = try {
                astroWebUiSiteControlClient.save(
                    host,
                    previewPort,
                    AstroWebUiSiteWriteRequest(latitude, longitude),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                AstroWebUiSiteWriteResult.Failure(
                    error.message ?: "Astro WebUI site save failed",
                )
            }
            if (!ownsSession(ownerSession)) return@launch
            when (result) {
                is AstroWebUiSiteWriteResult.Accepted -> {
                    astroWebUiSiteWrite = AstroWebUiSiteWriteState.Accepted(result.site)
                    statusMessage = "Astro WebUI site save accepted; confirming by readback"
                    refreshAstroWebUiSite()
                }
                is AstroWebUiSiteWriteResult.Failure -> {
                    astroWebUiSiteWrite = AstroWebUiSiteWriteState.Failed(result.reason)
                    statusMessage = result.reason
                }
            }
        }
    }

    private suspend fun readAstroWebUiProgramme(announce: Boolean): Boolean =
        astroWebUiProgrammeReadMutex.withLock {
            if (!FeatureFlags.isEnabled("astroWebUiProgrammeStatus")) {
                astroWebUiProgramme = AstroWebUiProgrammeState.Failed("Astro WebUI programme status disabled by config")
                return@withLock false
            }
            val ownerSession = session
            if (ownerSession == null) {
                astroWebUiProgramme = AstroWebUiProgrammeState.Failed("Connect to a mount before reading its Astro WebUI programme status")
                return@withLock false
            }
            astroWebUiProgramme = AstroWebUiProgrammeState.Loading
            val result = astroWebUiProgrammeClient.read(host, previewPort)
            if (!ownsSession(ownerSession)) return@withLock false
            when (result) {
                is AstroWebUiProgrammeResult.Success -> {
                    astroWebUiProgramme = AstroWebUiProgrammeState.Ready(result.programme)
                    if (announce) {
                        statusMessage = if (result.programme.kind == "none") {
                            "Astro WebUI has no programme running"
                        } else {
                            "Astro WebUI programme: " + result.programme.kind
                        }
                    }
                    result.programme.kind != "none"
                }
                is AstroWebUiProgrammeResult.Failure -> {
                    astroWebUiProgramme = AstroWebUiProgrammeState.Failed(result.reason)
                    if (announce) statusMessage = result.reason
                    false
                }
            }
        }

    fun refreshAstroWebUiProgramme() {
        astroWebUiProgrammeJob?.cancel()
        astroWebUiProgrammeJob = scope.launch {
            readAstroWebUiProgramme(announce = true)
        }
    }

    /**
     * Mirror Astro WebUI's visible-programme polling contract: idle reads are
     * quiet, while a resident run is refreshed once per second. The poller is
     * owned by the Mission sheet and performs only GET /api/prog reads.
     */
    fun startAstroWebUiProgrammePolling() {
        astroWebUiProgrammePollJob?.cancel()
        if (!FeatureFlags.isEnabled("astroWebUiProgrammeStatus")) {
            astroWebUiProgramme = AstroWebUiProgrammeState.Failed("Astro WebUI programme status disabled by config")
            return
        }
        astroWebUiProgrammePollJob = scope.launch {
            while (isActive) {
                val running = readAstroWebUiProgramme(announce = false)
                delay(if (running) 1_000L else 4_000L)
            }
        }
    }

    fun stopAstroWebUiProgrammePolling() {
        astroWebUiProgrammePollJob?.cancel()
        astroWebUiProgrammePollJob = null
    }

    fun cancelAstroWebUiProgramme() {
        runAstroWebUiProgrammeControl("cancel") { client ->
            client.cancel(host, previewPort)
        }
    }

    fun setAstroWebUiPanoramaInterval(seconds: Int) {
        runAstroWebUiProgrammeControl("panorama interval update") { client ->
            client.setPanoramaInterval(host, previewPort, seconds)
        }
    }

    fun toggleAstroWebUiPanoramaPause() {
        val programme = (astroWebUiProgramme as? AstroWebUiProgrammeState.Ready)?.programme
        val paused = programme?.paused
        if (programme?.kind != "panorama" || paused == null) {
            statusMessage = "Panorama pause state is not positively observed"
            return
        }
        val desired = !paused
        runAstroWebUiProgrammeControl(if (desired) "pause" else "resume") { client ->
            client.setPanoramaPaused(host, previewPort, desired)
        }
    }

    fun startAstroWebUiLapse(parameters: AstroWebUiLapseParameters) {
        runAstroWebUiProgrammeStart("timelapse start") { client ->
            client.startLapse(host, previewPort, parameters)
        }
    }

    /**
     * Start the source-specific in-head Astro capture route. The HTTP
     * acknowledgement only proves that the resident programme was accepted;
     * /api/prog remains the authority for the run after this returns.
     */
    fun startAstroWebUiAstroCapture(parameters: AstroWebUiAstroCaptureParameters) {
        if (blockCaptureDuringSlowMotion()) return
        if (!FeatureFlags.isEnabled("astroWebUiAstroCapture")) {
            astroWebUiAstroCapture = AstroWebUiAstroCaptureState.Failed(
                "Astro WebUI in-head capture is disabled until route, recovery and physical shutter/mount qualification",
            )
            statusMessage = "Astro WebUI in-head capture is disabled by config"
            return
        }
        val ownerSession = session
        if (ownerSession == null) {
            astroWebUiAstroCapture = AstroWebUiAstroCaptureState.Failed(
                "Connect to a mount before starting Astro WebUI in-head capture",
            )
            statusMessage = "Connect to a mount before starting Astro WebUI in-head capture"
            return
        }
        if (astroWebUiAstroCaptureJob?.isActive == true) {
            statusMessage = "An Astro WebUI in-head capture request is already in progress"
            return
        }
        astroWebUiAstroCapture = AstroWebUiAstroCaptureState.Loading
        astroWebUiAstroCaptureJob = scope.launch {
            if (!ownsSession(ownerSession)) return@launch
            val result = try {
                astroWebUiAstroCaptureClient.start(host, previewPort, parameters)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                AstroWebUiAstroCaptureResult.Failure(
                    error.message ?: "Astro WebUI in-head capture failed",
                )
            }
            if (!ownsSession(ownerSession)) return@launch
            when (result) {
                is AstroWebUiAstroCaptureResult.Accepted -> {
                    astroWebUiAstroCapture = AstroWebUiAstroCaptureState.Accepted(result.programme)
                    statusMessage = "Astro WebUI in-head capture accepted; /api/prog remains authoritative"
                    readAstroWebUiProgramme(announce = false)
                }
                is AstroWebUiAstroCaptureResult.Failure -> {
                    astroWebUiAstroCapture = AstroWebUiAstroCaptureState.Failed(result.reason)
                    statusMessage = result.reason
                }
            }
        }
    }

    fun previewAstroWebUiPanorama(parameters: AstroWebUiPanoramaParameters) {
        runAstroWebUiProgrammeStart("panorama preview") { client ->
            client.previewPanorama(host, previewPort, parameters)
        }
    }

    fun startAstroWebUiPanorama(parameters: AstroWebUiPanoramaParameters) {
        runAstroWebUiProgrammeStart("panorama start") { client ->
            client.startPanorama(host, previewPort, parameters)
        }
    }

    fun previewAstroWebUiHdr(parameters: AstroWebUiHdrParameters) {
        runAstroWebUiProgrammeStart("HDR preview") { client ->
            client.previewHdr(host, previewPort, parameters)
        }
    }

    fun startAstroWebUiHdr(parameters: AstroWebUiHdrParameters) {
        runAstroWebUiProgrammeStart("HDR start") { client ->
            client.startHdr(host, previewPort, parameters)
        }
    }

    fun validateAstroWebUiFocus(parameters: AstroWebUiFocusParameters) {
        runAstroWebUiProgrammeStart("focus validation") { client ->
            client.validateFocus(host, previewPort, parameters)
        }
    }

    fun previewAstroWebUiFocus(parameters: AstroWebUiFocusParameters) {
        runAstroWebUiProgrammeStart("focus preview") { client ->
            client.previewFocus(host, previewPort, parameters)
        }
    }

    fun startAstroWebUiFocus(parameters: AstroWebUiFocusParameters) {
        runAstroWebUiProgrammeStart("focus start") { client ->
            client.startFocus(host, previewPort, parameters)
        }
    }

    fun addAstroWebUiPlcKey(request: AstroWebUiPlcKeyRequest) =
        runAstroWebUiMotionProgramme("add PLC keyframe") { client ->
            client.addPlcKey(host, previewPort, request)
        }

    fun clearAstroWebUiPlcKeys() =
        runAstroWebUiMotionProgramme("clear PLC keyframes") { client ->
            client.clearPlcKeys(host, previewPort)
        }

    fun previewAstroWebUiPlc(parameters: AstroWebUiPlcParameters) =
        runAstroWebUiMotionProgramme("PLC preview") { client ->
            client.previewPlc(host, previewPort, parameters)
        }

    fun startAstroWebUiPlc(parameters: AstroWebUiPlcParameters) =
        runAstroWebUiMotionProgramme("PLC start") { client ->
            client.startPlc(host, previewPort, parameters)
        }

    fun addAstroWebUiPathWaypoint(request: AstroWebUiPathWaypointRequest) =
        runAstroWebUiMotionProgramme("add path-lapse waypoint") { client ->
            client.addPathWaypoint(host, previewPort, request)
        }

    fun clearAstroWebUiPathWaypoints() =
        runAstroWebUiMotionProgramme("clear path-lapse waypoints") { client ->
            client.clearPathWaypoints(host, previewPort)
        }

    fun previewAstroWebUiPathLapse(parameters: AstroWebUiPathLapseParameters) =
        runAstroWebUiMotionProgramme("path-lapse preview") { client ->
            client.previewPathLapse(host, previewPort, parameters)
        }

    fun startAstroWebUiPathLapse(parameters: AstroWebUiPathLapseParameters) =
        runAstroWebUiMotionProgramme("path-lapse start") { client ->
            client.startPathLapse(host, previewPort, parameters)
        }

    fun previewAstroWebUiGrail(parameters: AstroWebUiGrailParameters) =
        runAstroWebUiMotionProgramme("Holy Grail preview") { client ->
            client.previewGrail(host, previewPort, parameters)
        }

    fun applyAstroWebUiGrail(parameters: AstroWebUiGrailParameters) =
        runAstroWebUiMotionProgramme("Holy Grail apply") { client ->
            client.applyGrail(host, previewPort, parameters)
        }

    fun disableAstroWebUiGrail() =
        runAstroWebUiMotionProgramme("Holy Grail disable") { client ->
            client.disableGrail(host, previewPort)
        }

    fun refreshAstroWebUiGrailStatus() =
        runAstroWebUiMotionProgramme("Holy Grail status") { client ->
            client.grailStatus(host, previewPort)
        }

    private fun runAstroWebUiMotionProgramme(
        action: String,
        request: suspend (AstroWebUiMotionProgrammeClient) -> AstroWebUiMotionProgrammeResult,
    ) {
        if (!FeatureFlags.isEnabled("astroWebUiProgrammeStarts")) {
            astroWebUiMotionProgrammeState = AstroWebUiMotionProgrammeState.Failed(
                "Extended Astro WebUI programmes are disabled until named-head qualification",
            )
            statusMessage = "Extended Astro WebUI programmes are disabled by config"
            return
        }
        val ownerSession = session
        if (ownerSession == null) {
            astroWebUiMotionProgrammeState = AstroWebUiMotionProgrammeState.Failed(
                "Connect to a mount before using an extended Astro WebUI programme",
            )
            statusMessage = "Connect to a mount before using an extended Astro WebUI programme"
            return
        }
        if (astroWebUiMotionProgrammeJob?.isActive == true) {
            statusMessage = "An extended Astro WebUI programme request is already in progress"
            return
        }
        astroWebUiMotionProgrammeState = AstroWebUiMotionProgrammeState.Loading(action)
        astroWebUiMotionProgrammeJob = scope.launch {
            if (!ownsSession(ownerSession)) return@launch
            val result = try {
                request(astroWebUiMotionProgrammeClient)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                AstroWebUiMotionProgrammeResult.Failure(
                    error.message ?: "Astro WebUI $action failed",
                )
            }
            if (!ownsSession(ownerSession)) return@launch
            when (result) {
                is AstroWebUiMotionProgrammeResult.Count ->
                    astroWebUiMotionProgrammeState = AstroWebUiMotionProgrammeState.Count(action, result.count)
                is AstroWebUiMotionProgrammeResult.Preview ->
                    astroWebUiMotionProgrammeState = AstroWebUiMotionProgrammeState.Preview(action, result.frame, result.count, result.inferred)
                AstroWebUiMotionProgrammeResult.Accepted -> {
                    astroWebUiMotionProgrammeState = AstroWebUiMotionProgrammeState.Accepted(action)
                    readAstroWebUiProgramme(announce = false)
                }
                is AstroWebUiMotionProgrammeResult.Disabled ->
                    astroWebUiMotionProgrammeState = AstroWebUiMotionProgrammeState.Disabled(result.enabled)
                is AstroWebUiMotionProgrammeResult.Status ->
                    astroWebUiMotionProgrammeState = AstroWebUiMotionProgrammeState.Status(result.enabled, result.brightness)
                is AstroWebUiMotionProgrammeResult.Failure ->
                    astroWebUiMotionProgrammeState = AstroWebUiMotionProgrammeState.Failed(result.reason)
            }
            if (result !is AstroWebUiMotionProgrammeResult.Failure) {
                statusMessage = "Astro WebUI $action completed at the HTTP/protocol layer; physical completion remains status-gated"
            }
        }
    }

    fun previewAstroWebUiSun(parameters: AstroWebUiSunParameters) {
        runAstroWebUiSunProgramme("sun programme preview", confirm = false) { client ->
            client.preview(host, previewPort, parameters)
        }
    }

    fun startAstroWebUiSun(parameters: AstroWebUiSunParameters) {
        runAstroWebUiSunProgramme("sun programme start", confirm = true) { client ->
            client.start(host, previewPort, parameters)
        }
    }

    private fun runAstroWebUiSunProgramme(
        action: String,
        confirm: Boolean,
        request: suspend (AstroWebUiSunProgrammeClient) -> AstroWebUiSunResult,
    ) {
        if (confirm && blockCaptureDuringSlowMotion()) return
        if (!FeatureFlags.isEnabled("astroWebUiProgrammeStarts")) {
            astroWebUiSunState = AstroWebUiSunState.Failed(
                "Astro WebUI programme starts are disabled until named-head qualification",
            )
            statusMessage = "Astro WebUI programme starts are disabled until named-head qualification"
            return
        }
        val ownerSession = session
        if (ownerSession == null) {
            astroWebUiSunState = AstroWebUiSunState.Failed(
                "Connect to a mount before starting an Astro WebUI solar programme",
            )
            statusMessage = "Connect to a mount before starting an Astro WebUI solar programme"
            return
        }
        if (astroWebUiSunJob?.isActive == true) {
            statusMessage = "An Astro WebUI solar programme request is already in progress"
            return
        }
        astroWebUiSunState = AstroWebUiSunState.Loading
        astroWebUiSunJob = scope.launch {
            if (!ownsSession(ownerSession)) return@launch
            val result = try {
                request(astroWebUiSunProgrammeClient)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                AstroWebUiSunResult.Failure(
                    error.message ?: "Astro WebUI solar programme failed",
                )
            }
            if (!ownsSession(ownerSession)) return@launch
            when (result) {
                is AstroWebUiSunResult.Preview -> {
                    astroWebUiSunState = AstroWebUiSunState.Preview(result.frame)
                    statusMessage = "Astro WebUI solar programme preview ready; no run was started"
                }
                AstroWebUiSunResult.Accepted -> {
                    astroWebUiSunState = AstroWebUiSunState.Accepted
                    statusMessage = "Astro WebUI solar programme accepted; confirming through programme status"
                    readAstroWebUiProgramme(announce = false)
                }
                is AstroWebUiSunResult.Failure -> {
                    astroWebUiSunState = AstroWebUiSunState.Failed(result.reason)
                    statusMessage = result.reason
                }
            }
        }
    }

    private fun runAstroWebUiProgrammeStart(
        action: String,
        request: suspend (AstroWebUiProgrammeStartClient) -> AstroWebUiProgrammeStartResult,
    ) {
        if (blockCaptureDuringSlowMotion()) return
        if (!FeatureFlags.isEnabled("astroWebUiProgrammeStarts")) {
            astroWebUiProgrammeStartState = AstroWebUiProgrammeStartState.Failed(
                "Astro WebUI programme starts are disabled until named-head qualification",
            )
            statusMessage = "Astro WebUI programme starts are disabled until named-head qualification"
            return
        }
        val ownerSession = session
        if (ownerSession == null) {
            astroWebUiProgrammeStartState = AstroWebUiProgrammeStartState.Failed(
                "Connect to a mount before starting an Astro WebUI programme",
            )
            statusMessage = "Connect to a mount before starting an Astro WebUI programme"
            return
        }
        if (astroWebUiProgrammeStartJob?.isActive == true) {
            statusMessage = "An Astro WebUI programme request is already in progress"
            return
        }
        astroWebUiProgrammeStartState = AstroWebUiProgrammeStartState.Loading
        astroWebUiProgrammeStartJob = scope.launch {
            if (!ownsSession(ownerSession)) return@launch
            val result = request(astroWebUiProgrammeStartClient)
            if (!ownsSession(ownerSession)) return@launch
            when (result) {
                is AstroWebUiProgrammeStartResult.Preview -> {
                    astroWebUiProgrammeStartState = AstroWebUiProgrammeStartState.Preview(result)
                    statusMessage = "Astro WebUI $action is ready; no programme was started"
                }
                is AstroWebUiProgrammeStartResult.Accepted -> {
                    astroWebUiProgrammeStartState = AstroWebUiProgrammeStartState.Accepted(result.kind)
                    statusMessage = "Astro WebUI $action accepted; confirming through programme status"
                    readAstroWebUiProgramme(announce = false)
                }
                is AstroWebUiProgrammeStartResult.Failure -> {
                    astroWebUiProgrammeStartState = AstroWebUiProgrammeStartState.Failed(result.reason)
                    statusMessage = result.reason
                }
            }
        }
    }

    private fun runAstroWebUiProgrammeControl(
        action: String,
        request: suspend (AstroWebUiProgrammeControlClient) -> AstroWebUiProgrammeControlResult,
    ) {
        if (!FeatureFlags.isEnabled("astroWebUiProgrammeControls")) {
            statusMessage = "Astro WebUI programme controls are disabled until write-route qualification"
            return
        }
        val ownerSession = session
        if (ownerSession == null) {
            statusMessage = "Connect to a mount before controlling an Astro WebUI programme"
            return
        }
        if (astroWebUiProgrammeControlJob?.isActive == true) {
            statusMessage = "An Astro WebUI programme control request is already in progress"
            return
        }
        astroWebUiProgrammeControlJob = scope.launch {
            if (!ownsSession(ownerSession)) return@launch
            val result = request(astroWebUiProgrammeControlClient)
            if (!ownsSession(ownerSession)) return@launch
            when (result) {
                AstroWebUiProgrammeControlResult.Accepted -> {
                    statusMessage = "Astro WebUI " + action + " request accepted; confirming by status poll"
                    readAstroWebUiProgramme(announce = false)
                }
                is AstroWebUiProgrammeControlResult.Failure -> {
                    statusMessage = result.reason
                }
            }
        }
    }

    fun refreshAstroMedia() {
        astroMediaExportJob?.cancel()
        astroMediaExportController.reset()
        astroMediaExportState = AstroMediaExportState.Idle
        val ownerSession = session
        astroMediaJob?.cancel()
        astroMediaJob = scope.launch {
            if (ownerSession == null) {
                astroMediaState = AstroMediaState.Failed("Connect to a mount before reading its Astro WebUI")
                return@launch
            }
            if (!ownsSession(ownerSession)) return@launch
            if (!FeatureFlags.isEnabled("astroWebUiMedia")) {
                astroMediaState = AstroMediaState.Failed("Astro WebUI gallery disabled by config")
                return@launch
            }
            astroMediaState = AstroMediaState.LoadingList
            astroMediaPreview = null
            val result = astroWebUiMediaClient.list(host, previewPort)
            if (!ownsSession(ownerSession)) return@launch
            when (result) {
                is AstroMediaListResult.Success -> {
                    astroMediaItems = result.items
                    astroMediaState = AstroMediaState.Ready(result.reportedCount)
                    statusMessage = "Astro WebUI gallery: ${result.items.size} item(s)"
                }
                is AstroMediaListResult.Failure -> {
                    astroMediaItems = null
                    astroMediaState = AstroMediaState.Failed(result.reason)
                    statusMessage = result.reason
                }
            }
        }
    }

    fun loadAstroMedia(path: String, kind: AstroMediaTransferKind) {
        astroMediaExportJob?.cancel()
        astroMediaExportController.reset()
        astroMediaExportState = AstroMediaExportState.Idle
        val ownerSession = session
        astroMediaJob?.cancel()
        astroMediaJob = scope.launch {
            if (ownerSession == null) {
                astroMediaState = AstroMediaState.Failed("Connect to a mount before reading its Astro WebUI")
                return@launch
            }
            if (!ownsSession(ownerSession)) return@launch
            if (!FeatureFlags.isEnabled("astroWebUiMedia")) {
                astroMediaState = AstroMediaState.Failed("Astro WebUI gallery disabled by config")
                return@launch
            }
            astroMediaState = AstroMediaState.LoadingItem(path, kind)
            val result = astroWebUiMediaClient.fetch(host, previewPort, path, kind)
            if (!ownsSession(ownerSession)) return@launch
            when (result) {
                is AstroMediaFetchResult.Success -> {
                    astroMediaPreview = AstroMediaPreview(path, kind, result.bytes, result.contentType)
                    astroMediaState = AstroMediaState.Ready(astroMediaItems?.size ?: 0)
                    statusMessage = "Astro WebUI ${kind.name.lowercase()}: ${result.bytes.size} bytes"
                }
                is AstroMediaFetchResult.Failure -> {
                    astroMediaState = AstroMediaState.Failed(result.reason)
                    statusMessage = result.reason
                }
            }
        }
    }

    fun exportAstroMedia(destination: AstroMediaExportDestination) {
        if (!FeatureFlags.isEnabled("astroWebUiMedia")) {
            astroMediaExportState = AstroMediaExportState.Failed(null, "Astro WebUI gallery disabled by config")
            statusMessage = "Astro WebUI gallery disabled by config"
            return
        }
        val preview = astroMediaPreview
        if (preview == null) {
            astroMediaExportState = AstroMediaExportState.Failed(null, "Load a full Astro WebUI image before exporting")
            statusMessage = "Load a full Astro WebUI image before exporting"
            return
        }
        astroMediaExportJob?.cancel()
        if (!astroMediaExportController.prepare(preview, destination)) {
            astroMediaExportState = astroMediaExportController.state.value
            statusMessage = (astroMediaExportState as? AstroMediaExportState.Failed)?.reason.orEmpty()
            return
        }
        astroMediaExportState = astroMediaExportController.state.value
        val bytes = preview.bytes.copyOf()
        astroMediaExportJob = scope.launch {
            val result = astroMediaExportController.export(bytes)
            astroMediaExportState = result
            statusMessage = when (result) {
                is AstroMediaExportState.Complete -> "Astro media export complete (" + result.bytesStored + " bytes)"
                is AstroMediaExportState.Failed -> result.reason
                else -> "Astro media export in progress"
            }
        }
    }

    /**
     * Prepare and save a redacted support snapshot. The platform chooser is
     * explicit and the export contains only observed state; it never performs
     * a fresh mount request or includes credentials/raw camera bytes.
     */
    fun exportSupportBundle(versionLabel: String? = null) {
        supportBundleExportJob?.cancel()
        val attachment = cameraAttachment
        val cameraSummary = attachment?.let {
            if (it.isAttached) {
                listOfNotNull(it.manufacturer, it.model).joinToString(" ").ifBlank { "Attached camera" }
            } else {
                "No camera attached (state " + (it.state ?: "unknown") + ")"
            }
        }
        val storageSummary = sdStatus?.let { status ->
            if (status.present) {
                (status.freeMb ?: "?").toString() + " MB free / " +
                    (status.totalMb ?: "?") + " MB"
            } else {
                "Not present"
            }
        }
        val captureSummary = when (val phase = capturePhase) {
            CapturePhase.Idle -> "Idle"
            CapturePhase.Requested -> "Requested"
            CapturePhase.Busy -> "Camera active"
            CapturePhase.Completed -> "Confirmed: lifecycle + file publication"
            is CapturePhase.OutcomeUnknown -> "Outcome unknown: " + phase.reason
            is CapturePhase.Failed -> "Failed: " + phase.reason
        }
        val snapshot = SupportBundleSnapshot(
            appBuild = versionLabel,
            generatedAtEpochMs = SystemMillis.now().coerceAtLeast(0L),
            endpointKind = endpointHealth.profile?.kind?.name,
            controlPort = endpointHealth.profile?.address?.controlPort,
            previewPort = endpointHealth.profile?.address?.previewPort,
            routePhase = endpointHealth.phase.name,
            controlService = endpointHealth.control.name,
            previewService = endpointHealth.preview.name,
            lastFailure = endpointHealth.lastFailure,
            mountConnected = mount.connected,
            firmware = firmwareVersion ?: deviceInfo?.software,
            hardware = deviceInfo?.hardware,
            camera = cameraSummary,
            storage = storageSummary,
            capture = captureSummary,
            slowMotionLiveReady = slowMotionCapabilityRecord.liveReady,
            slowMotionBlockers = slowMotionCapabilityRecord.blockers,
            rawFrameLoggingEnabled = FeatureFlags.isEnabled("rawFrameLog"),
            captureTimeline = captureTimeline.takeLast(20).map {
                SupportBundleTimelineEntry(it.stage.label, it.detail)
            },
            publication = lastCapturePublication?.let {
                SupportBundlePublication(
                    basename = it.path.substringAfterLast('/').substringAfterLast('\\'),
                    type = it.type,
                    sizeBytes = it.sizeBytes,
                )
            },
        )
        if (!supportBundleExportController.prepare(snapshot)) {
            supportBundleExportState = supportBundleExportController.state
            statusMessage = (supportBundleExportState as? SupportBundleExportState.Failed)?.reason
                ?: "Support bundle could not be prepared"
            return
        }
        supportBundleExportState = supportBundleExportController.state
        supportBundleExportJob = scope.launch {
            val ready = supportBundleExportController.state as? SupportBundleExportState.Ready
            if (ready != null) {
                supportBundleExportState = SupportBundleExportState.Exporting(ready.request)
            }
            val result = supportBundleExportController.export()
            supportBundleExportState = result
            statusMessage = when (result) {
                is SupportBundleExportState.Complete -> "Support bundle saved"
                is SupportBundleExportState.Failed -> result.reason
                else -> "Support bundle save in progress"
            }
        }
    }

    /** True while a feature-gated Astro WebUI shutter is awaiting evidence. */
    var astroWebUiCaptureActive by mutableStateOf(false)
        private set
    /** True while the M/B HTTP bridge request is being dispatched. */
    var astroWebUiCaptureDispatching by mutableStateOf(false)
        private set
    var astroWebUiBulbActive by mutableStateOf(false)
        private set
    /** Prevents a second B stop write while the first stop awaits evidence. */
    var astroWebUiBulbStopQueued by mutableStateOf(false)
        private set
    var astroWebUiCaptureStatus by mutableStateOf<String?>(null)
        private set

    /**
     * Dispatch one Astro WebUI M/B release through the resident HTTP bridge.
     * The feature is deliberately separate from native capture: a positive
     * HTTP result only queues 264, so the shared capture evidence observer and
     * watchdog remain the sole completion authority.
     */
    fun captureAstroWebUi(mode: dev.openpolaris.core.domain.CameraReleaseMode, bulbSeconds: Int = 0) {
        if (blockCaptureDuringSlowMotion()) return
        if (!FeatureFlags.isEnabled("astroWebUiCapture")) {
            astroWebUiCaptureStatus = "Astro WebUI shutter is disabled until lifecycle qualification"
            statusMessage = astroWebUiCaptureStatus.orEmpty()
            return
        }
        val ownerSession = session
        val ownerController = cameraController
        if (ownerSession == null || ownerController == null) {
            statusMessage = "Connect a mount and camera before using Astro WebUI capture"
            return
        }
        if (!cameraAttachmentAllowsCommand()) return
        val observed = camera.shutterIndex?.let { camera.shutterOptions.getOrNull(it)?.trim() }
        val observedMode = dev.openpolaris.core.domain.CameraReleaseMode.fromOption(observed)
        if (observedMode != mode) {
            statusMessage = "Astro WebUI capture requires verified ${mode.label} readback (observed ${observedMode.label})"
            return
        }
        if (mode == dev.openpolaris.core.domain.CameraReleaseMode.BULB && bulbSeconds <= 0) {
            statusMessage = "Bulb duration must be positive"
            return
        }
        if (videoRecording == true) {
            statusMessage = "Stop video before an Astro WebUI still"
            return
        }
        if (sequenceActive || observationProgrammeActive) {
            statusMessage = "Capture programme in progress — stop it before a single shot"
            return
        }
        val phase = capturePhase
        if (phase !is CapturePhase.Idle && phase !is CapturePhase.Completed && phase !is CapturePhase.Failed) {
            statusMessage = "Capture in progress — wait for it to finish"
            return
        }
        astroWebUiCaptureDispatching = true
        capturePhase = CapturePhase.Requested
        captureEvidence.begin()
        pendingCapturePublication = null
        astroWebUiCaptureActive = false
        astroWebUiBulbActive = false
        astroWebUiBulbStopQueued = false
        astroWebUiCaptureJob?.cancel()
        astroWebUiCaptureJob = scope.launch {
            if (!ownsSession(ownerSession) || cameraController !== ownerController) return@launch
            val admissionFailure = verifyCaptureReadiness(
                ownerSession,
                ownerController,
                requiredMode = mode,
            )
            if (admissionFailure != null) {
                captureEvidence.fail(admissionFailure)
                captureKind = null
                captureSource = null
                capturePhase = CapturePhase.Failed(admissionFailure)
                astroWebUiCaptureStatus = "Astro WebUI capture blocked: $admissionFailure"
                appendCaptureTimeline(CaptureTimelineStage.FAILED, admissionFailure)
                statusMessage = astroWebUiCaptureStatus.orEmpty()
                astroWebUiCaptureDispatching = false
                restoreCaptureWorkloads()
                return@launch
            }
            beginCaptureTimeline("Astro WebUI ${mode.label} release queued")
            suspendCaptureWorkloads()
            if (!ownsSession(ownerSession) || cameraController !== ownerController) return@launch
            val result = runCatching {
                astroWebUiCaptureClient.dispatch(host, previewPort, mode, bulbSeconds)
            }.getOrElse { error ->
                AstroWebUiCaptureResult.Failed(
                    "Astro WebUI dispatch failed: " +
                        (error.message ?: error::class.simpleName ?: "unknown error"),
                )
            }
            if (!ownsSession(ownerSession)) return@launch
            when (result) {
                is AstroWebUiCaptureResult.Queued -> {
                    captureKind = if (mode == dev.openpolaris.core.domain.CameraReleaseMode.BULB) {
                        CaptureKind.BULB
                    } else {
                        CaptureKind.NORMAL
                    }
                    captureSource = CaptureSource.ASTRO_WEB_UI
                    astroWebUiCaptureDispatching = false
                    astroWebUiCaptureActive = true
                    astroWebUiBulbActive = mode == dev.openpolaris.core.domain.CameraReleaseMode.BULB
                    astroWebUiCaptureStatus = "HTTP bridge accepted 264; waiting for camera lifecycle and file evidence"
                    statusMessage = astroWebUiCaptureStatus.orEmpty()
                    captureWatchdogJob?.cancel()
                    captureWatchdogJob = scope.launch {
                        delay(if (mode == dev.openpolaris.core.domain.CameraReleaseMode.BULB) {
                            maxOf(CAPTURE_TIMEOUT_MS, bulbSeconds * 1000L + 15_000L)
                        } else CAPTURE_TIMEOUT_MS)
                        if (!ownsSession(ownerSession)) return@launch
                        if (capturePhase is CapturePhase.Requested || capturePhase is CapturePhase.Busy) {
                            val reason = "timeout: Astro WebUI queue accepted but correlated 264 lifecycle and/or 773 file evidence is missing"
                            captureEvidence.unknown(reason)
                            capturePhase = CapturePhase.OutcomeUnknown(reason)
                            captureKind = null
                            captureSource = null
                            astroWebUiCaptureActive = false
                            astroWebUiCaptureDispatching = false
                            astroWebUiBulbActive = false
                            astroWebUiBulbStopQueued = false
                            appendCaptureTimeline(CaptureTimelineStage.UNKNOWN, reason)
                            statusMessage = "Astro WebUI outcome unknown — check the card before another shutter"
                        }
                    }
                }
                is AstroWebUiCaptureResult.Failed -> {
                    captureEvidence.fail(result.reason)
                    captureKind = null
                    captureSource = null
                    capturePhase = CapturePhase.Failed(result.reason)
                    astroWebUiCaptureActive = false
                    astroWebUiCaptureDispatching = false
                    astroWebUiBulbActive = false
                    astroWebUiBulbStopQueued = false
                    astroWebUiCaptureStatus = "Astro WebUI dispatch failed: " + result.reason
                    appendCaptureTimeline(CaptureTimelineStage.FAILED, result.reason)
                    statusMessage = astroWebUiCaptureStatus.orEmpty()
                    restoreCaptureWorkloads()
                }
                is AstroWebUiCaptureResult.Stopped -> Unit
            }
        }
    }

    fun stopAstroWebUiBulb() {
        if (!astroWebUiBulbActive) {
            statusMessage = "No Astro WebUI Bulb capture is active"
            return
        }
        if (astroWebUiBulbStopQueued) {
            statusMessage = "Astro WebUI Bulb stop already queued; waiting for evidence"
            return
        }
        astroWebUiBulbStopQueued = true
        val ownerSession = session
        astroWebUiBulbStopJob?.cancel()
        astroWebUiBulbStopJob = scope.launch {
            if (!ownsSession(ownerSession)) return@launch
            val result = runCatching {
                astroWebUiCaptureClient.stop(host, previewPort)
            }.getOrElse { error ->
                AstroWebUiCaptureResult.Failed(
                    error.message ?: error::class.simpleName ?: "unknown stop error",
                )
            }
            if (!ownsSession(ownerSession)) return@launch
            when (result) {
                is AstroWebUiCaptureResult.Stopped -> {
                    astroWebUiCaptureStatus = "Astro WebUI Bulb stop queued; waiting for file evidence"
                    statusMessage = astroWebUiCaptureStatus.orEmpty()
                }
                is AstroWebUiCaptureResult.Failed -> {
                    val reason = "Astro WebUI Bulb stop failed: " + result.reason
                    captureEvidence.unknown(reason)
                    capturePhase = CapturePhase.OutcomeUnknown(reason)
                    astroWebUiCaptureActive = false
                    astroWebUiCaptureDispatching = false
                    astroWebUiBulbActive = false
                    appendCaptureTimeline(CaptureTimelineStage.UNKNOWN, reason)
                    statusMessage = "Astro WebUI stop outcome unknown - check the card"
                }
                is AstroWebUiCaptureResult.Queued -> Unit
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
                sshCommandRunner = if (firmwareDeliveryMode == DeliveryMode.SSH_PIPE) {
                    platformFirmwareCommandRunner(host = firmwareSshHost)
                } else null,
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

        /**
         * #60: bounded wait for a capture to complete after the shutter
         * request. The K-3 III lands its final image ~3–4 s after the
         * shutter; 15 s leaves generous headroom for slower cameras /
         * long bulb exposures while still catching a genuinely wedged
         * pipeline instead of leaving the UI stuck on "Busy" forever.
         */
        private const val CAPTURE_TIMEOUT_MS: Long = 15_000L

        /** Fresh camera identity/release-mode admission budget before any 264 write. */
        private const val CAMERA_READINESS_TIMEOUT_MS: Long = 4_000L

        /**
         * #89: per-request timeout for the 1 Hz 284/517 pose poll. The
         * [MountSession.request] default is 2000 ms; at a 1 s cadence a
         * response older than 800 ms is already stale, so waiting the full
         * 2 s only delays the next cycle (worst case ~5 s of stale UI in
         * the pre-fix sequential loop). 800 ms keeps the worst-case cycle
         * at ~1.8 s (parallel requests + 1 s delay) while still giving a
         * busy gimbal plenty of time to answer.
         */
        private const val POLL_REQUEST_TIMEOUT_MS: Long = 800L

    }
}
