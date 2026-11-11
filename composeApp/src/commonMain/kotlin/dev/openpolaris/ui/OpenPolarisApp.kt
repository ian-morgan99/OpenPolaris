package dev.openpolaris.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.openpolaris.core.domain.BatteryAwareness
import dev.openpolaris.core.domain.BatterySeverity
import dev.openpolaris.core.domain.Connection
import dev.openpolaris.core.domain.EndpointService
import dev.openpolaris.core.domain.MissionReadiness
import dev.openpolaris.core.domain.MissionReadinessInput
import dev.openpolaris.core.domain.MotionState
import dev.openpolaris.core.domain.TrackingRate
import dev.openpolaris.core.domain.format2
import dev.openpolaris.core.config.FeatureFlags
import dev.openpolaris.core.session.SessionStore
import dev.openpolaris.core.session.UserPreferences
import dev.openpolaris.core.session.UserPreferencesStore
import dev.openpolaris.core.session.path.defaultSessionPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Root app surface, modelled on the original Benro Connect layout: a fixed
 * "operating" screen — status strip, position readout and jog pad always
 * visible — with small call-out buttons that open dialogs for less-used
 * functions (Connection, Slew/align, Camera, Guide).
 *
 * Phone portrait: everything fits without scrolling.
 * Wide/landscape: same fixed view with a vertical call-out rail on the right.
 *
 * @param viewModel optional pre-built [AppViewModel]. Production Android
 *   hosts (3c.4) build the VM in `MainActivity.onCreate` so they can hold a
 *   reference for `onResume` (to fire [AppViewModel.tryReconnectIfMarkerExists])
 *   and pass the same instance into the composable. Desktop / tests omit
 *   this argument; a fresh VM is constructed from [sessionStore] +
 *   [connectionFactory] (or the default
 *   `SessionStore(defaultSessionPath())` if [sessionStore] is also null).
 */
@Composable
fun OpenPolarisApp(
    windowSizeClass: WindowSizeClass,
    connectionFactory: () -> Connection,
    onFindWifi: (() -> Unit)? = null,
    onLaunchVr: (() -> Unit)? = null,
    viewModel: AppViewModel? = null,
    sessionStore: SessionStore? = null,
    /**
     * Optional durable preferences store. Desktop injects a file-backed store;
     * mobile hosts can inject an app-sandbox path. Null keeps the UI session-only.
     */
    preferenceStore: UserPreferencesStore? = null,
    /**
     * Desktop-only: bridge the segregated Wi-Fi interface to the gimbal.
     * Receives a `progress: (String) -> Unit` callback the bridge calls
     * from its IO dispatcher and returns true only after route and Polaris
     * service identity are verified. The lambda itself is invoked from the
     * click-launched coroutine. A false-returning default keeps Android from
     * claiming a desktop bridge is ready. Mirrors [AppViewModel].
     */
    connectWifi: (suspend (suspend (String) -> Unit) -> Boolean)? = null,
    /**
     * Optional BT-only wake pulse. When supplied, the Connection pane
     * shows a "Wake" button that calls [AppViewModel.wake]. Independent
     * of [connectWifi] (the full bring-up): a user can wake the gimbal
     * without bringing up the bridge, and vice versa. Mirrors the
     * constructor parameter on [AppViewModel].
     */
    wakeProbe: (suspend (suspend (String) -> Unit) -> Unit)? = null,
    /**
     * Optional bridge teardown. When supplied, the Connection pane shows a
     * "Tear down bridge" button that reverses [connectWifi]: removes the
     * policy route, brings the saved Wi-Fi profile down, and releases the
     * retained BLE GATT wake link (the keep-alive change). Desktop-only;
     * Android has no bridge to tear down so it leaves this null. Mirrors
     * the constructor parameter on [AppViewModel].
     */
    teardownBridge: (suspend (suspend (String) -> Unit) -> Unit)? = null,
    /**
     * Optional platform "find & wake Polaris" flow that mirrors the
     * Benro app's first tap on a cold start: send a BT wake pulse to
     * the gimbal, then scan for any `polaris*` access point and offer
     * the strongest match. Independent of [onFindWifi] (the system
     * Wi-Fi picker) — `onFindWifi` is a coarse fallback, this is the
     * intended path on mobile. When supplied, the Connection pane
     * shows a "Find & wake Polaris…" button that drives the flow.
     *
     * The lambda accepts a `progress: suspend (String) -> Unit` the
     * implementation calls from its IO dispatcher so the user sees
     * live status lines ("Pulsing Bluetooth…", "Scanning Wi-Fi…",
     * "Found polaris_d13e86 (-42 dBm)").
     */
    onMountWifiScan: (suspend (suspend (String) -> Unit) -> Unit)? = null,
    /**
     * Build identity to display in the Settings dialog footer. Pass the
     * running app's package id + version (e.g. "dev.openpolaris.app
     * v0.1.6 (7)") from the host so the user can confirm they are on the
     * real build and not a third-party fork installed under a similar id
     * (see issue #43). Defaults to null on platforms where the host
     * doesn't inject one (e.g. desktop).
     */
    versionLabel: String? = null,
    /** Optional mobile compass input; desktop leaves this null and uses manual heading. */
    compassHeadingSource: CompassHeadingSource? = null,
) {
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<Callout?>(null) }
    var unavailableFeature by remember { mutableStateOf<String?>(null) }
    var activeDestination by remember { mutableStateOf(AppDestination.MISSION) }
    var preferences by remember { mutableStateOf(UserPreferences()) }
    var dimMode by remember { mutableStateOf(false) }

    fun persistPreferences(updated: UserPreferences) {
        preferences = updated
        preferenceStore?.let { store ->
            scope.launch(Dispatchers.IO) {
                store.write(updated)
            }
        }
    }

    val vm: AppViewModel = viewModel
        ?: remember {
            AppViewModel(
                scope = scope,
                connectionFactory = connectionFactory,
                connectWifi = connectWifi ?: { false },
                wakeProbe = wakeProbe ?: {},
                teardownBridge = teardownBridge ?: {},
                sessionStore = sessionStore ?: SessionStore(defaultSessionPath()),
            )
        }

    LaunchedEffect(preferenceStore, vm) {
        vm.setConnectionModePersistence { mode ->
            persistPreferences(preferences.copy(connectionMode = mode))
        }
        vm.setObserverLocationPersistence { latitude, longitude ->
            persistPreferences(
                preferences.copy(
                    observerLatitudeDeg = latitude,
                    observerLongitudeDeg = longitude,
                ),
            )
        }
        if (preferenceStore != null) {
            val loaded = withContext(Dispatchers.IO) { preferenceStore.read() }
            if (loaded != null) {
                preferences = loaded
                dimMode = loaded.dimMode
                vm.restoreConnectionMode(loaded.connectionMode)
                vm.restoreObserverLocation(
                    loaded.observerLatitudeDeg,
                    loaded.observerLongitudeDeg,
                )
            }
        }
    }

    fun setDimMode(value: Boolean) {
        dimMode = value
        persistPreferences(preferences.copy(dimMode = value))
    }

    fun openDestination(destination: AppDestination) {
        activeDestination = destination
        dialog = null
    }

    fun openAdvanced(callout: Callout) {
        if (callout == Callout.VR) {
            if (onLaunchVr == null) {
                unavailableFeature = "3D view"
            } else {
                onLaunchVr.invoke()
            }
        } else {
            dialog = callout
        }
    }

    fun openMissionAction(actionId: String) {
        when (actionId) {
            "connection", "mount" -> openAdvanced(Callout.Connection)
            "camera", "capture", "prepare", "focus" -> openDestination(AppDestination.CAMERA)
            "preview" -> openAdvanced(Callout.Preview)
            "alignment", "tracking", "motion", "target" -> openDestination(AppDestination.CONTROL)
            "guide" -> openAdvanced(Callout.Helpers)
            "storage" -> openDestination(AppDestination.GALLERY)
        }
    }

    // v0.1.8: also factor in heightSizeClass. A phone in landscape has a
    // Compact *height* (typically 360 dp) which can't host a full 9-item
    // rail without the items being shrunk to glyph-sized labels — exactly
    // the "icons oversized" / "text not readable" complaint from #45/#46.
    // Treat any landscape-short viewport as a phone-portrait layout so the
    // rail collapses to Operate + a "More…" menu.
    val widthCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
    val heightCompact = windowSizeClass.heightSizeClass == WindowHeightSizeClass.Compact
    val wide = !widthCompact && !heightCompact

    OpenPolarisTheme(dim = dimMode, compact = !wide) {
        Surface(Modifier.fillMaxSize()) {
            if (wide) {
                Row(
                    Modifier.fillMaxSize().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PrimaryDestinationRail(
                        selected = activeDestination,
                        onSelect = ::openDestination,
                        modifier = Modifier.fillMaxHeight(),
                    )
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusStrip(
                            vm,
                            Modifier.fillMaxWidth(),
                            compact = false,
                            onOpenDetails = { openDestination(AppDestination.MISSION) },
                            onOpenGallery = { openDestination(AppDestination.GALLERY) },
                            onOpenMissionAction = ::openMissionAction,
                            dimMode = dimMode,
                            onToggleDim = { setDimMode(!dimMode) },
                        )
                        InlineDestinationWorkspace(
                            vm = vm,
                            destination = activeDestination,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            onMissionAction = ::openMissionAction,
                            onOpenGallery = { openDestination(AppDestination.GALLERY) },
                            compassHeadingSource = compassHeadingSource,
                        )
                    }
                    CalloutRail(vertical = true, Modifier.fillMaxHeight(), onLaunchVr) { openAdvanced(it) }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(1.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    StatusStrip(
                        vm,
                        Modifier.fillMaxWidth(),
                        compact = true,
                        onOpenDetails = { openDestination(AppDestination.MISSION) },
                        onOpenGallery = { openDestination(AppDestination.GALLERY) },
                        onOpenMissionAction = ::openMissionAction,
                        dimMode = dimMode,
                        onToggleDim = { setDimMode(!dimMode) },
                    )
                    InlineDestinationWorkspace(
                        vm = vm,
                        destination = activeDestination,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        onMissionAction = ::openMissionAction,
                        onOpenGallery = { openDestination(AppDestination.GALLERY) },
                        compassHeadingSource = compassHeadingSource,
                    )
                    CompactDestinationBar(
                        selected = activeDestination,
                        onSelect = ::openDestination,
                        onAdvancedSelect = ::openAdvanced,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            unavailableFeature?.let { feature ->
                AlertDialog(
                    onDismissRequest = { unavailableFeature = null },
                    title = { Text("$feature unavailable") },
                    text = {
                        Text(
                            "This build has no host for $feature. Choose a supported Android VR host or continue with the standard mobile/PC workspace.",
                        )
                    },
                    confirmButton = {
                        ResponsiveTextActionButton(
                            graphic = OpenPolarisGraphic.Back,
                            label = "Close",
                            contentDescription = "Close unavailable feature dialog",
                            onClick = { unavailableFeature = null },
                        )
                    },
                )
            }

            when (dialog) {
                Callout.Connection -> CalloutDialog("Connection", { dialog = null }) {
                    ConnectionPane(
                        vm,
                        Modifier.fillMaxWidth(),
                        onFindWifi = onFindWifi,
                        onBridgeWifi = if (connectWifi != null) ({ vm.connectWifi() }) else null,
                        onWake = if (wakeProbe != null) ({ vm.wake() }) else null,
                        onMountWifiScan = onMountWifiScan,
                        onBridgeDown = if (teardownBridge != null) ({ vm.teardownBridge() }) else null,
                    )
                }
                Callout.Slew -> CalloutDialog("Slew & Motion", { dialog = null }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GotoPane(vm, Modifier.fillMaxWidth())
                        SlowMotionPane(vm, Modifier.fillMaxWidth())
                    }
                }
                Callout.Camera -> CalloutDialog("Camera", { dialog = null }) { CameraPane(vm, Modifier.fillMaxWidth()) }
                Callout.Preview -> CalloutDialog("Preview", { dialog = null }) { PreviewPane(vm, Modifier.fillMaxWidth()) }
                Callout.Helpers -> CalloutDialog("Astro helpers", { dialog = null }) { HelpersPane(vm, Modifier.fillMaxWidth()) }
                Callout.Tonight -> if (FeatureFlags.isEnabled("catalog")) {
                    CalloutDialog("Tonight", { dialog = null }) { TonightPane(vm, Modifier.fillMaxWidth()) }
                } else {
                    dialog = null
                }
                Callout.Firmware -> CalloutDialog("Firmware update", { dialog = null }) { FirmwarePane(vm, Modifier.fillMaxWidth()) }
                // VR is launched in two stages: the rail click handler
                // (CalloutRail.handle, below) calls `onLaunchVr?.invoke()`
                // which starts the Android VR activity directly. Here
                // we just close whatever callout might be open — setting
                // `dialog = Callout.VR` is a sentinel that survives
                // recomposition long enough for the activity to take
                // focus, but in practice the rail handler runs first
                // and the dialog is never opened for VR. The explicit
                // no-op here documents the intent and keeps the `when`
                // exhaustive.
                Callout.VR -> { dialog = null }
                Callout.Readme -> CalloutDialog("Guide", { dialog = null }) { UserManualPane(Modifier.fillMaxWidth()) }
                Callout.Settings -> CalloutDialog("Settings", { dialog = null }) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        EvidenceCentrePane(
                            vm,
                            Modifier.fillMaxWidth(),
                            versionLabel = versionLabel,
                        )
                        HorizontalDivider()
                        FeatureFlagsPaneContent(Modifier.fillMaxWidth(), versionLabel = versionLabel)
                    }
                }
                null -> {}
            }

            // 3c.4: surface the "Reconnect to last mount?" prompt whenever the
            // ViewModel populates `reconnectPrompt`. Hosted at the root surface
            // so the dialog floats above the call-out stack and survives
            // navigation between Connection/Slew/Helpers dialogs.
            ReconnectDialog(vm)
            // Password dialog: surfaces whenever the 820 probe reports
            // `needed:1` with no password configured. Float above the
            // callout stack (same approach as ReconnectDialog) so the
            // user can fix it from any pane they happen to be on.
            if (vm.needsPassword) {
                PasswordDialog(
                    vm,
                    onDismiss = { vm.setConnectionPassword(null) },
                )
            }
        }
    }
}

/**
 * Inline primary workspace used by the responsive shell. It is intentionally
 * one LazyColumn with one item: the individual panes do not own a competing
 * vertical scroll, and the same destination state is used on phone and PC.
 */
@Composable
private fun InlineDestinationWorkspace(
    vm: AppViewModel,
    destination: AppDestination,
    modifier: Modifier = Modifier,
    onMissionAction: (String) -> Unit,
    onOpenGallery: () -> Unit,
    compassHeadingSource: CompassHeadingSource?,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (LocalOpenPolarisCompact.current) 3.dp else 8.dp),
    ) {
        item {
            when (destination) {
                AppDestination.MISSION,
                AppDestination.ASTRO,
                -> MissionDashboardPane(
                    vm = vm,
                    modifier = Modifier.fillMaxWidth(),
                    title = if (destination == AppDestination.ASTRO) "Astro mission" else "Mission readiness",
                    onAction = onMissionAction,
                    compassHeadingSource = compassHeadingSource,
                )
                AppDestination.CONTROL -> ControlWorkspace(vm, Modifier.fillMaxWidth())
                AppDestination.CAMERA -> CameraPane(
                    vm = vm,
                    modifier = Modifier.fillMaxWidth(),
                    onOpenGallery = onOpenGallery,
                )
                AppDestination.GALLERY -> GalleryPane(vm, Modifier.fillMaxWidth())
            }
        }
    }
}

// Each callout's rail label. Full words instead of cryptic acronyms so
// a first-time user can read the rail without hovering for tooltips.
// The 4 most-used entries form the "Operate" group on phones; the
// remaining 6 live in the "More" overflow menu so the visible rail
// always fits a 320 dp wide phone in portrait AND landscape.
private enum class Callout(
    val glyph: String,
    val graphic: OpenPolarisGraphic,
    val accessibilityLabel: String,
) {
    Connection("Wi-Fi", OpenPolarisGraphic.Connection, "Connection"),
    Slew("Slew", OpenPolarisGraphic.Slew, "Slew and motion"),
    Camera("Camera", OpenPolarisGraphic.Camera, "Camera"),
    Preview("Preview", OpenPolarisGraphic.Preview, "Preview"),
    Helpers("Helpers", OpenPolarisGraphic.Helpers, "Astro helpers"),
    Tonight("Tonight", OpenPolarisGraphic.Tonight, "Tonight"),
    Firmware("Firmware", OpenPolarisGraphic.Firmware, "Firmware"),
    VR("3D view", OpenPolarisGraphic.View3d, "3D view"),
    Readme("Guide", OpenPolarisGraphic.Guide, "Guide"),
    Settings("Settings", OpenPolarisGraphic.Settings, "Settings"),
}

// Operate group: 4 items shown in the always-visible rail. The
// remaining 6 items (Helpers, Tonight, Firmware, VR, Readme, Settings) live in
// the "More" overflow menu.
private val OperateItems: List<Callout> = listOf(
    Callout.Connection,
    Callout.Slew,
    Callout.Camera,
    Callout.Preview,
)

private val MoreItems: List<Callout> = listOf(
    Callout.Helpers,
    Callout.Tonight,
    Callout.Firmware,
    Callout.VR,
    Callout.Readme,
    Callout.Settings,
)

/**
 * Compact phone navigation: graphic-only, one 18 dp visual row.
 *
 * Material3 NavigationBar reserves a large icon slot above every label. That
 * is useful on a tablet, but wastes most of a phone's vertical workspace when
 * the labels already identify the destinations. The row uses the same small
 * line graphics as the action controls; full names remain in semantics and
 * the More menu so icon-only controls do not become ambiguous. The overflow
 * menu also uses compact 26 dp rows on phones instead of stock Material menu
 * chrome.
 */
@Composable
private fun CompactDestinationBar(
    selected: AppDestination,
    onSelect: (AppDestination) -> Unit,
    onAdvancedSelect: (Callout) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier
            .height(18.dp)
            .testTag("primary-navigation"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            primaryAppDestinations.forEach { destination ->
                CompactDestinationItem(
                    destination = destination,
                    selected = selected == destination,
                    onClick = { onSelect(destination) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("destination-" + destination.name.lowercase()),
                )
            }
            CompactGraphicTabButton(
                graphic = OpenPolarisGraphic.More,
                contentDescription = "More destinations",
                selected = expanded,
                onClick = { expanded = true },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("destination-more"),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            AdvancedItems.forEachIndexed { i, callout ->
                if (i > 0) HorizontalDivider()
                CompactOverflowMenuItem(
                    callout = callout,
                    modifier = Modifier.testTag("destination-more-" + callout.name.lowercase()),
                    onClick = {
                        expanded = false
                        onAdvancedSelect(callout)
                    },
                )
            }
        }
    }
}

@Composable
private fun CompactDestinationItem(
    destination: AppDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CompactGraphicTabButton(
        graphic = destination.graphic(),
        contentDescription = destination.label,
        selected = selected,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun PrimaryDestinationRail(
    selected: AppDestination,
    onSelect: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.width(92.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        primaryAppDestinations.forEach { destination ->
            DestinationButton(
                destination = destination,
                selected = selected == destination,
                onClick = { onSelect(destination) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DestinationButton(
    destination: AppDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier
            .testTag("destination-" + destination.name.lowercase())
            .semantics(mergeDescendants = true) {
                contentDescription = destination.label
                stateDescription = if (selected) "Selected" else "Not selected"
                role = Role.Tab
            },
    ) {
        OpenPolarisGraphicIcon(
            graphic = destination.graphic(),
            tint = if (selected) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        )
    }
}

private val AdvancedItems: List<Callout> = listOf(
    Callout.Connection,
    Callout.Preview,
) + MoreItems

private fun AppDestination.graphic(): OpenPolarisGraphic = when (this) {
    AppDestination.MISSION -> OpenPolarisGraphic.Mission
    AppDestination.CONTROL -> OpenPolarisGraphic.Slew
    AppDestination.CAMERA -> OpenPolarisGraphic.Camera
    AppDestination.ASTRO -> OpenPolarisGraphic.Tonight
    AppDestination.GALLERY -> OpenPolarisGraphic.Preview
}


@Composable
private fun CalloutRail(
    vertical: Boolean,
    modifier: Modifier = Modifier,
    onLaunchVr: (() -> Unit)?,
    onSelect: (Callout) -> Unit,
) {
    val handle: (Callout) -> Unit = { c ->
        if (c == Callout.VR) onLaunchVr?.invoke() else onSelect(c)
    }
    if (vertical) {
        // Tablet layout: full 10-item vertical rail.
        Column(
            modifier,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AdvancedItems.forEach { c -> CalloutButton(c, handle) }
        }
    } else {
        // Phone layout: 4-item Operate row + "More…" overflow button.
        Row(
            modifier,
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OperateItems.forEach { c -> CalloutButton(c, handle) }
            MoreMenuButton(handle)
        }
    }
}

@Composable
private fun MoreMenuButton(onSelect: (Callout) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        CompactGraphicButton(
            graphic = OpenPolarisGraphic.More,
            contentDescription = "More controls",
            onClick = { expanded = true },
            modifier = Modifier.testTag("callout-more"),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            AdvancedItems.forEachIndexed { i, c ->
                if (i > 0) HorizontalDivider()
                CompactOverflowMenuItem(
                    callout = c,
                    modifier = Modifier.testTag("callout-${c.name.lowercase()}"),
                    onClick = {
                        expanded = false
                        onSelect(c)
                    },
                )
            }
        }
    }
}

@Composable
private fun CompactOverflowMenuItem(
    callout: Callout,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    if (LocalOpenPolarisCompact.current) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(26.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {
                    contentDescription = callout.accessibilityLabel
                },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OpenPolarisGraphicIcon(callout.graphic)
            Text(
                callout.glyph,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        DropdownMenuItem(
            leadingIcon = { OpenPolarisGraphicIcon(callout.graphic) },
            text = { Text(callout.glyph) },
            modifier = modifier,
            onClick = onClick,
        )
    }
}

@Composable
private fun CalloutButton(c: Callout, onSelect: (Callout) -> Unit) {
    CompactGraphicButton(
        graphic = c.graphic,
        contentDescription = c.accessibilityLabel,
        onClick = { onSelect(c) },
        modifier = Modifier.testTag("callout-${c.name.lowercase()}"),
    )
}

/** Compact always-visible status line. */
@Composable
private fun StatusStrip(
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    onOpenDetails: (() -> Unit)? = null,
    onOpenGallery: (() -> Unit)? = null,
    onOpenMissionAction: ((String) -> Unit)? = null,
    dimMode: Boolean = false,
    onToggleDim: (() -> Unit)? = null,
    compact: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val s = vm.mount
    val endpoint = vm.endpointHealth
    val motionState by vm.motionState.collectAsState()
    val activeMotionOwner = (motionState as? MotionState.Owned)?.lease?.owner
    val captureInProgress = vm.capturePhase is AppViewModel.CapturePhase.Requested ||
        vm.capturePhase is AppViewModel.CapturePhase.Busy
    val readiness = MissionReadiness.evaluate(
        MissionReadinessInput(
            control = endpoint.control,
            mountConnected = s.connected,
            cameraAttached = vm.cameraAttachment?.isAttached,
            preview = endpoint.preview,
            storageReady = vm.sdStatus?.present,
            alignmentReady = if (vm.alignmentStars > 0 || vm.lastSolveResult.value != null) true else false,
            tracking = s.tracking,
            captureInProgress = captureInProgress,
            activeMotionOwner = activeMotionOwner,
            motionFault = (motionState as? MotionState.Faulted)?.reason,
        ),
    )
    val next = readiness.firstOrNull { it.level != dev.openpolaris.core.domain.ReadinessLevel.READY }
    Card(modifier) {
        Column(
            Modifier.padding(
                horizontal = if (compact) 4.dp else 12.dp,
                vertical = if (compact) 2.dp else 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp),
        ) {
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            vm.statusMessage,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        val battery = BatteryAwareness.from(s.batteryPercent, s.charging)
                        Text(
                            battery.compactLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = when (battery.severity) {
                                BatterySeverity.CRITICAL -> MaterialTheme.colorScheme.error
                                BatterySeverity.LOW -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier
                                .testTag("battery-status")
                                .semantics {
                                    contentDescription = battery.contentDescription
                                    stateDescription = battery.severity.label
                                },
                        )
                        Text(
                            buildString {
                                if (s.tracking == true) append("TRK")
                                if (s.halfSpeed) append(if (s.tracking == true) "  ½×" else "½×")
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .testTag("status-actions"),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        onOpenDetails?.let { open ->
                            ResponsiveActionButton(
                                graphic = OpenPolarisGraphic.Guide,
                                label = "Details",
                                contentDescription = "Open mission details",
                                onClick = open,
                                modifier = Modifier.testTag("mission-details"),
                                outlined = true,
                            )
                        }
                        onOpenGallery?.let { open ->
                            ResponsiveActionButton(
                                graphic = OpenPolarisGraphic.Preview,
                                label = "Gallery",
                                contentDescription = "Open Gallery",
                                onClick = open,
                                modifier = Modifier.testTag("open-gallery"),
                                outlined = true,
                            )
                        }
                        onToggleDim?.let { toggle ->
                            ResponsiveActionButton(
                                graphic = OpenPolarisGraphic.Tonight,
                                label = if (dimMode) "Bright" else "Dim",
                                contentDescription = if (dimMode) "Set screen to bright mode" else "Set screen to dim mode",
                                onClick = toggle,
                                modifier = Modifier.testTag("night-dim-toggle"),
                                outlined = true,
                            )
                        }
                        ResponsiveActionButton(
                            graphic = OpenPolarisGraphic.More,
                            label = if (expanded) "Less" else "More",
                            contentDescription = if (expanded) "Show less mission details" else "Show more mission details",
                            onClick = { expanded = !expanded },
                            modifier = Modifier.testTag("status-details-toggle"),
                            outlined = true,
                        )
                    }
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(vm.statusMessage, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    onOpenDetails?.let { open ->
                        TextButton(
                            onClick = open,
                            modifier = Modifier.testTag("mission-details"),
                        ) { Text("Details") }
                    }
                    onOpenGallery?.let { open ->
                        TextButton(
                            onClick = open,
                            modifier = Modifier.testTag("open-gallery"),
                        ) { Text("Gallery") }
                    }
                    onToggleDim?.let { toggle ->
                        TextButton(
                            onClick = toggle,
                            modifier = Modifier.testTag("night-dim-toggle"),
                        ) { Text(if (dimMode) "Bright" else "Dim") }
                    }
                    val battery = BatteryAwareness.from(s.batteryPercent, s.charging)
                    Text(
                        battery.compactLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = when (battery.severity) {
                            BatterySeverity.CRITICAL -> MaterialTheme.colorScheme.error
                            BatterySeverity.LOW -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier
                            .testTag("battery-status")
                            .semantics {
                                contentDescription = battery.contentDescription
                                stateDescription = battery.severity.label
                            },
                    )
                    Text(
                        buildString {
                            if (s.tracking == true) append("TRK")
                            if (s.halfSpeed) append(if (s.tracking == true) "  ½×" else "½×")
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (!compact || expanded) {
                when (val motion = motionState) {
                    MotionState.Idle -> Unit
                    is MotionState.Owned -> Row(
                        modifier = Modifier.fillMaxWidth().testTag("motion-safety-chrome"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Motion active: " + motion.lease.owner.name.lowercase().replace('_', ' '),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f),
                        )
                        ResponsiveActionButton(
                            graphic = OpenPolarisGraphic.Stop,
                            label = "STOP ALL",
                            contentDescription = "STOP ALL motion",
                            onClick = vm::emergencyStopMotion,
                            modifier = Modifier.testTag("emergency-stop"),
                            outlined = true,
                        )
                    }
                    is MotionState.Faulted -> Text(
                        "Motion stop fault: " + motion.reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth().testTag("motion-safety-chrome"),
                    )
                }
                HorizontalDivider()
                if (next == null) {
                    Text(
                        "Mission ready — no blocked or unknown prerequisites",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("mission-readiness-summary"),
                    )
                } else {
                    Text(
                        "Next: ${next.label} — ${next.summary}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (next.level == dev.openpolaris.core.domain.ReadinessLevel.BLOCKED) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.testTag("mission-readiness-summary"),
                    )
                    next.actionLabel?.let { action ->
                        onOpenMissionAction?.let { open ->
                            ResponsiveTextActionButton(
                                graphic = OpenPolarisGraphic.Forward,
                                label = "Next: $action",
                                contentDescription = "Next: $action",
                                onClick = { open(next.id) },
                                modifier = Modifier.testTag("mission-next-action"),
                            )
                        } ?: Text(
                            "Action: $action",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

        }
    }
}

private enum class CompactControlSurface {
    JOG,
    SLOW,
}

/**
 * The phone control destination is a command deck, not a long report. Keep
 * the live position and safety-critical tracking chips visible, then expose
 * either fast jog or SLOW as the active surface. The desktop still shows both
 * surfaces together so no capability is removed; the phone simply avoids
 * making the operator scroll past one control family to reach the other.
 */
@Composable
private fun ControlWorkspace(
    vm: AppViewModel,
    modifier: Modifier = Modifier,
) {
    if (!LocalOpenPolarisCompact.current) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
            PositionReadout(vm, Modifier.fillMaxWidth())
            JogPane(vm, Modifier.fillMaxWidth())
            SlowMotionPane(vm, Modifier.fillMaxWidth())
        }
        return
    }

    var surface by remember { mutableStateOf(CompactControlSurface.JOG) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        PositionReadout(vm, Modifier.fillMaxWidth())
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactFilterChip(
                selected = surface == CompactControlSurface.JOG,
                onClick = { surface = CompactControlSurface.JOG },
                label = { Text("Jog") },
                graphic = OpenPolarisGraphic.Slew,
                contentDescription = "Show fast jog controls",
                modifier = Modifier.testTag("control-surface-jog"),
            )
            CompactFilterChip(
                selected = surface == CompactControlSurface.SLOW,
                onClick = { surface = CompactControlSurface.SLOW },
                label = { Text("SLOW") },
                graphic = OpenPolarisGraphic.Fine,
                contentDescription = "Show slow motion controls",
                compactLabel = "S",
                modifier = Modifier.testTag("control-surface-slow"),
            )
        }
        when (surface) {
            CompactControlSurface.JOG -> JogPane(vm, Modifier.fillMaxWidth())
            CompactControlSurface.SLOW -> SlowMotionPane(vm, Modifier.fillMaxWidth())
        }
    }
}

/** Az/Alt readout + tracking / half-speed / AHRS chips, compact. */
@Composable
private fun PositionReadout(vm: AppViewModel, modifier: Modifier = Modifier) {
    val p = vm.position
    Card(modifier) {
        Column(
            Modifier.padding(openPolarisCardInnerPadding()),
            verticalArrangement = Arrangement.spacedBy(openPolarisSectionSpacing()),
        ) {
            Text(
                "Az ${p?.yaw?.toDouble()?.format2() ?: "—"}°   Alt ${p?.pitch?.toDouble()?.format2() ?: "—"}°",
                style = if (LocalOpenPolarisCompact.current) {
                    MaterialTheme.typography.labelLarge
                } else {
                    MaterialTheme.typography.headlineSmall
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(if (LocalOpenPolarisCompact.current) 3.dp else 8.dp)) {
                CompactFilterChip(
                    selected = vm.mount.tracking == true,
                    onClick = { if (vm.mount.tracking == true) vm.stopTracking() else vm.startTracking() },
                    label = { Text("Track") },
                    graphic = OpenPolarisGraphic.Tracking,
                    contentDescription = "Toggle tracking",
                )
                CompactFilterChip(
                    selected = vm.mount.halfSpeed,
                    onClick = { vm.toggleHalfSpeed(!vm.mount.halfSpeed) },
                    label = { Text("½ speed") },
                    graphic = OpenPolarisGraphic.HalfSpeed,
                    contentDescription = "Toggle half speed",
                )
                CompactFilterChip(
                    selected = vm.mount.ahrsEnabled,
                    onClick = { vm.enableAhrs(!vm.mount.ahrsEnabled) },
                    label = { Text("AHRS") },
                    graphic = OpenPolarisGraphic.Ahrs,
                    contentDescription = "Toggle AHRS",
                )
                CompactFilterChip(
                    selected = false,
                    onClick = {},
                    enabled = FeatureFlags.isEnabled("fineRateControl"),
                    label = { Text("Fine correction") },
                    graphic = OpenPolarisGraphic.Fine,
                    contentDescription = "Fine correction",
                )
            }
            if (!FeatureFlags.isEnabled("fineRateControl") && !LocalOpenPolarisCompact.current) {
                Text(
                    "Fine correction unavailable — the firmware rate and physical stop contract is not qualified.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // Explicit tracking rate (code 531 `speed:` field). The Benro app
            // always sends an explicit index; sidereal is correct for star
            // imaging, lunar (~0.966× sidereal) for the Moon. Selecting a rate
            // takes effect on the next Track start (docs/ASTRO-WORKFLOW-HANDOVER §5).
            Row(horizontalArrangement = Arrangement.spacedBy(if (LocalOpenPolarisCompact.current) 3.dp else 8.dp)) {
                Text("Rate", style = MaterialTheme.typography.labelSmall)
                TrackingRate.entries.forEach { rate ->
                    CompactFilterChip(
                        selected = vm.trackingRate == rate,
                        onClick = { vm.trackingRate = rate },
                        label = { Text(rate.label) },
                        compactLabel = if (rate == TrackingRate.SIDEREAL) "S" else "L",
                        contentDescription = "${rate.label} tracking rate",
                    )
                }
            }
        }
    }
}

/**
 * Call-out dialog wrapper.
 *
 * Wraps the callout body in a single bounded `verticalScroll` (the
 * sole `Modifier.verticalScroll` in the commonMain UI tree, enforced
 * by `CalloutDialogNoScrollWrapperTest`). This is what makes every
 * callout — GotoPane, CameraPane, FirmwarePane, FeatureFlagsPane —
 * scrollable instead of being clipped at the bottom of the
 * AlertDialog's bounded `text` slot.
 *
 * History of the same-axis scrollable crash this guards against:
 *  - v0.1.5 added `Modifier.verticalScroll` here while
 *    `FeatureFlagsPane` also wrapped its content in `verticalScroll`.
 *    The two same-axis scrollables had no bounded height between
 *    them, so opening Settings threw "Vertically scrollable
 *    component was measured with an infinity maximum height
 *    constraints" (issues #40 / #42).
 *  - v0.1.6 "fixed" it by removing the outer scroll, leaving
 *    `FeatureFlagsPane` as the sole scroller. That solved the
 *    crash but re-clipped every other callout.
 *  - v0.1.10 added `Modifier.weight(1f, fill = true)` to
 *    `FeatureFlagsPane`'s inner Column so its build-identity
 *    footer was pinned. That weight is exactly the bounded-height
 *    precondition the v0.1.5 outer scroller was missing.
 *  - v0.1.11 therefore relocates the single `verticalScroll` back
 *    to this outer wrapper, where the bounded height exists.
 *    `FeatureFlagsPane` keeps the weight to pin its footer but no
 *    longer adds its own scroll. The total `verticalScroll` count
 *    across the commonMain UI tree is still exactly 1.
 */
@Composable
private fun CalloutDialog(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            ResponsiveTextActionButton(
                graphic = OpenPolarisGraphic.Back,
                label = "Close",
                contentDescription = "Close $title",
                onClick = onDismiss,
            )
        },
        title = { Text(title) },
        // The single, bounded vertical scroller for ALL callouts. The
        // `fillMaxHeight()` is what stops the v0.1.5
        // "Vertically scrollable component was measured with an
        // infinity maximum height constraints" crash: the AlertDialog
        // constrains the `text` slot to the viewport, and fillMaxHeight()
        // makes the inner Column consume exactly that bounded height,
        // giving `verticalScroll` a finite extent to scroll within.
        // Owned here (not in any individual pane) so every pane —
        // GotoPane, CameraPane, FirmwarePane, FeatureFlagsPane, etc. —
        // can be arbitrarily tall without its own scroll modifier.
        // This is the sole Modifier.verticalScroll in the commonMain
        // UI tree, enforced by CalloutDialogNoScrollWrapperTest.
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxHeight()) {
                content()
            }
        },
    )
}
