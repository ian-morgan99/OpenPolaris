package dev.openpolaris.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.openpolaris.core.domain.Connection
import dev.openpolaris.core.domain.format2
import dev.openpolaris.core.session.SessionStore
import dev.openpolaris.core.session.path.defaultSessionPath

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
) {
    val scope = rememberCoroutineScope()
    val vm: AppViewModel = viewModel
        ?: AppViewModel(
            scope = scope,
            connectionFactory = connectionFactory,
            sessionStore = sessionStore ?: SessionStore(defaultSessionPath()),
        )
    var dialog by remember { mutableStateOf<Callout?>(null) }
    val wide = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

    OpenPolarisTheme {
        Surface(Modifier.fillMaxSize()) {
            if (wide) {
                Row(
                    Modifier.fillMaxSize().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusStrip(vm, Modifier.fillMaxWidth())
                        PositionReadout(vm, Modifier.fillMaxWidth())
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            JogPane(vm, Modifier.width(260.dp))
                        }
                    }
                    CalloutRail(vertical = true, Modifier.fillMaxHeight(), onLaunchVr) { dialog = it }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusStrip(vm, Modifier.fillMaxWidth())
                    PositionReadout(vm, Modifier.fillMaxWidth())
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        JogPane(vm, Modifier.width(220.dp))
                    }
                    CalloutRail(vertical = false, Modifier.fillMaxWidth(), onLaunchVr) { dialog = it }
                }
            }

            when (dialog) {
                Callout.Connection -> CalloutDialog("Connection", { dialog = null }) { ConnectionPane(vm, Modifier.fillMaxWidth(), onFindWifi) }
                Callout.Slew -> CalloutDialog("Slew & Align", { dialog = null }) { GotoPane(vm, Modifier.fillMaxWidth()) }
                Callout.Camera -> CalloutDialog("Camera", { dialog = null }) { CameraPane(vm, Modifier.fillMaxWidth()) }
                Callout.Preview -> CalloutDialog("Preview", { dialog = null }) { PreviewPane(vm, Modifier.fillMaxWidth()) }
                Callout.Helpers -> CalloutDialog("Astro helpers", { dialog = null }) { HelpersPane(vm, Modifier.fillMaxWidth()) }
                Callout.VR -> { dialog = null }
                Callout.Readme -> CalloutDialog("Guide", { dialog = null }) { ReadmePane(Modifier.fillMaxWidth()) }
                null -> {}
            }

            // 3c.4: surface the "Reconnect to last mount?" prompt whenever the
            // ViewModel populates `reconnectPrompt`. Hosted at the root surface
            // so the dialog floats above the call-out stack and survives
            // navigation between Connection/Slew/Helpers dialogs.
            ReconnectDialog(vm)
        }
    }
}

private enum class Callout(val glyph: String) {
    Connection("Wi-Fi"),
    Slew("Slew"),
    Camera("Cam"),
    Preview("Preview"),
    Helpers("Helpers"),
    VR("VR"),
    Readme("?"),
}

/** Row (portrait) or column (landscape rail) of small call-out buttons. */
@Composable
private fun CalloutRail(
    vertical: Boolean,
    modifier: Modifier = Modifier,
    onLaunchVr: (() -> Unit)?,
    onSelect: (Callout) -> Unit,
) {
    val items = listOf(Callout.Connection, Callout.Slew, Callout.Camera, Callout.Preview, Callout.Helpers, Callout.VR, Callout.Readme)
    val handle: (Callout) -> Unit = { c ->
        if (c == Callout.VR) onLaunchVr?.invoke() else onSelect(c)
    }
    if (vertical) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            items.forEach { c -> CalloutButton(c, handle) }
        }
    } else {
        Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly) {
            items.forEach { c -> CalloutButton(c, handle) }
        }
    }
}

@Composable
private fun CalloutButton(c: Callout, onSelect: (Callout) -> Unit) {
    TextButton(onClick = { onSelect(c) }) {
        Text(c.glyph, style = MaterialTheme.typography.labelMedium)
    }
}

/** Compact always-visible status line. */
@Composable
private fun StatusStrip(vm: AppViewModel, modifier: Modifier = Modifier) {
    val s = vm.mount
    Card(modifier) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Open Polaris", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(vm.statusMessage, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text(
                buildString {
                    append(s.batteryPercent?.toString() ?: "—")
                    append("%")
                    if (s.charging) append("+")
                    if (s.tracking == true) append("  TRK")
                    if (s.halfSpeed) append("  ½×")
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Az/Alt readout + tracking / half-speed / AHRS chips, compact. */
@Composable
private fun PositionReadout(vm: AppViewModel, modifier: Modifier = Modifier) {
    val p = vm.position
    Card(modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Az ${p?.yaw?.toDouble()?.format2() ?: "—"}°   Alt ${p?.pitch?.toDouble()?.format2() ?: "—"}°",
                style = MaterialTheme.typography.headlineSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = vm.mount.tracking == true, onClick = {
                    if (vm.mount.tracking == true) vm.stopTracking() else vm.startTracking()
                }, label = { Text("Track") })
                FilterChip(selected = vm.mount.halfSpeed, onClick = { vm.toggleHalfSpeed(!vm.mount.halfSpeed) }, label = { Text("½ speed") })
                FilterChip(selected = vm.mount.ahrsEnabled, onClick = { vm.enableAhrs(!vm.mount.ahrsEnabled) }, label = { Text("AHRS") })
            }
        }
    }
}

/** Call-out dialog wrapper with scrollable body. */
@Composable
private fun CalloutDialog(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                content()
            }
        },
    )
}
