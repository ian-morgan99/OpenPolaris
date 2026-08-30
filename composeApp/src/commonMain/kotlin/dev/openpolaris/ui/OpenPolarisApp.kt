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

/**
 * Root app surface, modelled on the original Benro Connect layout: a fixed
 * "operating" screen — status strip, position readout and jog pad always
 * visible — with small call-out buttons that open dialogs for less-used
 * functions (Connection, Slew/align, Camera, Guide).
 *
 * Phone portrait: everything fits without scrolling.
 * Wide/landscape: same fixed view with a vertical call-out rail on the right.
 */
@Composable
fun OpenPolarisApp(
    windowSizeClass: WindowSizeClass,
    connectionFactory: () -> Connection,
    connectWifi: (suspend (String) -> Unit) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val vm = AppViewModel(scope, connectionFactory, connectWifi)
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
                    CalloutRail(vertical = true, Modifier.fillMaxHeight()) { dialog = it }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusStrip(vm, Modifier.fillMaxWidth())
                    PositionReadout(vm, Modifier.fillMaxWidth())
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        JogPane(vm, Modifier.width(220.dp))
                    }
                    CalloutRail(vertical = false, Modifier.fillMaxWidth()) { dialog = it }
                }
            }

            when (dialog) {
                Callout.Connection -> CalloutDialog("Connection", { dialog = null }) { ConnectionPane(vm, Modifier.fillMaxWidth(), onFindWifi = vm::connectWifi) }
                Callout.Slew -> CalloutDialog("Slew & Align", { dialog = null }) { GotoPane(vm, Modifier.fillMaxWidth()) }
                Callout.Camera -> CalloutDialog("Camera", { dialog = null }) { CameraPane(vm, Modifier.fillMaxWidth()) }
                Callout.Readme -> CalloutDialog("Guide", { dialog = null }) { ReadmePane(Modifier.fillMaxWidth()) }
                null -> {}
            }
        }
    }
}

private enum class Callout(val glyph: String) {
    Connection("Wi-Fi"),
    Slew("Slew"),
    Camera("Cam"),
    Readme("?"),
}

/** Row (portrait) or column (landscape rail) of small call-out buttons. */
@Composable
private fun CalloutRail(vertical: Boolean, modifier: Modifier = Modifier, onSelect: (Callout) -> Unit) {
    val items = listOf(Callout.Connection, Callout.Slew, Callout.Camera, Callout.Readme)
    if (vertical) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            items.forEach { c -> CalloutButton(c, onSelect) }
        }
    } else {
        Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly) {
            items.forEach { c -> CalloutButton(c, onSelect) }
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
