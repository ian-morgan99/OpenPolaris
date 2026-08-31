package dev.openpolaris.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.openpolaris.core.config.FeatureFlags
import dev.openpolaris.core.domain.format2

// =============================================================================
// Helpers pane: dither, settling time, limits, auto-level
// =============================================================================
//
// Phase-2 astro helpers. Each row shows: a label, the current cached value
// from the mount, +/- steppers or a switch, and a Refresh button. Calls are
// routed through the VM (AppViewModel), which in turn respects FeatureFlags
// and produces a statusMessage on success/failure.

@Composable
fun HelpersPane(vm: AppViewModel, modifier: Modifier = Modifier) {
    Card(modifier = modifier.padding(8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Astro helpers", style = MaterialTheme.typography.titleMedium)
            if (!FeatureFlags.isEnabled("advancedAstro") && !FeatureFlags.isEnabled("autoLevel")) {
                Text(
                    "Helpers and auto-level are off in the current config. Enable " +
                        "advancedAstro and autoLevel in FeatureFlags to use this pane.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            DitherRow(vm)
            SettlingRow(vm)
            LimitsRow(vm)
            HorizontalDivider()
            AutoLevelRow(vm)
        }
    }
}

@Composable
private fun DitherRow(vm: AppViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Dither", modifier = Modifier.width(110.dp))
        Text(vm.ditherEnabled?.let { if (it) "ON" else "OFF" } ?: "—", modifier = Modifier.width(48.dp))
        Switch(
            enabled = FeatureFlags.isEnabled("advancedAstro"),
            checked = vm.ditherEnabled == true,
            onCheckedChange = vm::setDither,
        )
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = vm::refreshHelpers) { Text("Refresh") }
    }
}

@Composable
private fun SettlingRow(vm: AppViewModel) {
    var draft by remember(vm.settlingTime) { mutableStateOf(vm.settlingTime?.toString() ?: "") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Settling (s)", modifier = Modifier.width(110.dp))
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it.filter { c -> c.isDigit() }.take(5) },
            singleLine = true,
            label = { Text("seconds") },
            modifier = Modifier.width(110.dp),
        )
        Button(
            enabled = FeatureFlags.isEnabled("advancedAstro"),
            onClick = {
                val v = draft.toIntOrNull()
                if (v != null && v >= 0) vm.setSettlingTimeMs(v * 1000) else vm.refreshSettling()
            },
        ) { Text("Apply") }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = vm::refreshSettling) { Text("Refresh") }
    }
}

@Composable
private fun LimitsRow(vm: AppViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Limits", modifier = Modifier.width(110.dp))
        Text(vm.limitsEnabled?.let { if (it) "ON" else "OFF" } ?: "—", modifier = Modifier.width(48.dp))
        Switch(
            enabled = FeatureFlags.isEnabled("advancedAstro"),
            checked = vm.limitsEnabled == true,
            onCheckedChange = vm::setLimits,
        )
        Spacer(Modifier.width(8.dp))
        Text("UNVERIFIED", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = vm::refreshHelpers) { Text("Refresh") }
    }
}

@Composable
private fun AutoLevelRow(vm: AppViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Auto-level", modifier = Modifier.width(110.dp))
        Text(vm.autoLevelEnabled?.let { if (it) "EN" else "DIS" } ?: "—", modifier = Modifier.width(48.dp))
        Switch(
            enabled = FeatureFlags.isEnabled("autoLevel"),
            checked = vm.autoLevelEnabled == true,
            onCheckedChange = vm::setAutoLevelEnabled,
        )
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = vm::runAutoLevel) { Text("Run now") }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = vm::refreshAutoLevel) { Text("Refresh") }
    }
}

// =============================================================================
// System pane: time, timezone, language, buzzer, LED, WiFi, reboot, shutdown
// =============================================================================

@Composable
fun SystemPane(vm: AppViewModel, modifier: Modifier = Modifier) {
    Card(modifier = modifier.padding(8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("System", style = MaterialTheme.typography.titleMedium)
            TimeRow(vm)
            TzRow(vm)
            LangRow(vm)
            BuzzerRow(vm)
            LedRow(vm)
            HorizontalDivider()
            WifiRow(vm)
            HorizontalDivider()
            RebootRow(vm)
            ShutdownRow(vm)
        }
    }
}

@Composable
private fun TimeRow(vm: AppViewModel) {
    var draft by remember { mutableStateOf((System.currentTimeMillis() / 1000L).toString()) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Epoch s", modifier = Modifier.width(110.dp))
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it.filter { c -> c.isDigit() }.take(12) },
            singleLine = true,
            label = { Text("seconds since 1970") },
            modifier = Modifier.width(160.dp),
        )
        Button(
            enabled = FeatureFlags.isEnabled("systemSettings"),
            onClick = { draft.toLongOrNull()?.let(vm::setSystemTime) },
        ) { Text("Set") }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = { draft = (System.currentTimeMillis() / 1000L).toString() }) { Text("Now") }
    }
}

@Composable
private fun TzRow(vm: AppViewModel) {
    var draft by remember { mutableStateOf("0") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Timezone", modifier = Modifier.width(110.dp))
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it.filter { c -> c == '-' || c.isDigit() }.take(4) },
            singleLine = true,
            label = { Text("offset hours") },
            modifier = Modifier.width(110.dp),
        )
        Button(
            enabled = FeatureFlags.isEnabled("systemSettings"),
            onClick = { draft.toIntOrNull()?.let(vm::setTimezone) },
        ) { Text("Set") }
    }
}

@Composable
private fun LangRow(vm: AppViewModel) {
    var draft by remember { mutableStateOf("0") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Language", modifier = Modifier.width(110.dp))
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it.filter { c -> c.isDigit() }.take(3) },
            singleLine = true,
            label = { Text("index") },
            modifier = Modifier.width(110.dp),
        )
        Button(
            enabled = FeatureFlags.isEnabled("systemSettings"),
            onClick = { draft.toIntOrNull()?.let(vm::setLanguage) },
        ) { Text("Set") }
    }
}

@Composable
private fun BuzzerRow(vm: AppViewModel) {
    var on by remember { mutableStateOf(true) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Buzzer", modifier = Modifier.width(110.dp))
        Switch(checked = on, onCheckedChange = { on = it; vm.setBuzzer(it) })
        Text(if (on) "ON" else "OFF")
    }
}

@Composable
private fun LedRow(vm: AppViewModel) {
    var on by remember { mutableStateOf(true) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Status LED", modifier = Modifier.width(110.dp))
        Switch(checked = on, onCheckedChange = { on = it; vm.setLed(it) })
        Text(if (on) "ON" else "OFF")
    }
}

@Composable
private fun WifiRow(vm: AppViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("WiFi band", modifier = Modifier.width(110.dp))
        OutlinedButton(
            enabled = FeatureFlags.isEnabled("wifiScan"),
            onClick = vm::refreshWifiScan,
        ) { Text("Scan") }
        Spacer(Modifier.width(8.dp))
        Text(vm.wifiScanResult?.take(40) ?: "—", style = MaterialTheme.typography.bodySmall)
    }
    if (FeatureFlags.isEnabled("wifiConnect")) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.connectWifiSsid("") }) { Text("Connect SSID…") }
            OutlinedButton(onClick = vm::disconnectWifi) { Text("Disconnect") }
        }
    } else {
        Text(
            "WiFi connect is off in config (only scan/list is exposed).",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun RebootRow(vm: AppViewModel) {
    var dialog by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Reboot", modifier = Modifier.width(110.dp), fontWeight = FontWeight.Bold)
        Button(
            enabled = FeatureFlags.isEnabled("allowReboot"),
            onClick = { dialog = true },
        ) { Text("Reboot mount") }
    }
    if (dialog) {
        AlertDialog(
            onDismissRequest = { dialog = false },
            title = { Text("Reboot the mount?") },
            text = {
                Text("The mount will power-cycle. The connection will drop. Make sure the mount is on a stable surface and no slew is in progress.")
            },
            confirmButton = {
                TextButton(onClick = { dialog = false; vm.reboot() }) { Text("Reboot") }
            },
            dismissButton = {
                TextButton(onClick = { dialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ShutdownRow(vm: AppViewModel) {
    var dialog by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Shutdown", modifier = Modifier.width(110.dp), fontWeight = FontWeight.Bold)
        Button(
            enabled = FeatureFlags.isEnabled("allowShutdown"),
            onClick = { dialog = true },
        ) { Text("Shutdown mount") }
    }
    if (dialog) {
        AlertDialog(
            onDismissRequest = { dialog = false },
            title = { Text("Shutdown the mount?") },
            text = {
                Text("The mount will power off. You'll need physical access to power it back on. The connection will drop immediately.")
            },
            confirmButton = {
                TextButton(onClick = { dialog = false; vm.shutdown() }) { Text("Shutdown") }
            },
            dismissButton = {
                TextButton(onClick = { dialog = false }) { Text("Cancel") }
            },
        )
    }
}

// =============================================================================
// Files pane: list / delete / protect / SD format
// =============================================================================

@Composable
fun FilesPane(vm: AppViewModel, modifier: Modifier = Modifier) {
    Card(modifier = modifier.padding(8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("SD card files", style = MaterialTheme.typography.titleMedium)
            SdHeaderRow(vm)
            OutlinedButton(
                enabled = FeatureFlags.isEnabled("fileManager"),
                onClick = vm::refreshFileList,
            ) { Text("Refresh list") }
            HorizontalDivider()
            val files = vm.fileList?.files.orEmpty()
            if (files.isEmpty()) {
                Text("(no files loaded — hit Refresh list)", style = MaterialTheme.typography.bodySmall)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    files.forEach { f -> FileRow(vm, f) }
                }
            }
            HorizontalDivider()
            FormatRow(vm)
        }
    }
}

@Composable
private fun SdHeaderRow(vm: AppViewModel) {
    val sd = vm.sdStatus
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("SD", modifier = Modifier.width(60.dp), fontWeight = FontWeight.Bold)
        Text(sd?.let { if (it.present) "present" else "absent" } ?: "—")
        if (sd?.totalMb != null && sd.freeMb != null) {
            Text("${sd.freeMb} MB free / ${sd.totalMb} MB")
        }
    }
}

@Composable
private fun FileRow(vm: AppViewModel, file: dev.openpolaris.core.domain.FileEntry) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("#${file.id}", modifier = Modifier.width(40.dp))
        Text(file.name, modifier = Modifier.weight(1f), maxLines = 1)
        if (file.prot != 0) Text("🔒", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(
            enabled = FeatureFlags.isEnabled("fileManagerMutate"),
            onClick = { vm.protectFile(file.id, if (file.prot == 0) 1 else 0) },
        ) { Text(if (file.prot == 0) "Protect" else "Unprotect") }
        Button(
            enabled = FeatureFlags.isEnabled("fileManagerMutate"),
            onClick = { vm.deleteFile(file.id) },
        ) { Text("Delete") }
    }
}

@Composable
private fun FormatRow(vm: AppViewModel) {
    var dialog by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("SD format", modifier = Modifier.width(110.dp), fontWeight = FontWeight.Bold)
        Button(
            enabled = FeatureFlags.isEnabled("fileManagerFormat"),
            onClick = { dialog = true },
        ) { Text("Format SD card") }
        if (!FeatureFlags.isEnabled("fileManagerFormat")) {
            Text("(enable fileManagerFormat in config)", style = MaterialTheme.typography.labelSmall)
        }
    }
    if (dialog) {
        AlertDialog(
            onDismissRequest = { dialog = false },
            title = { Text("Erase everything on the SD card?") },
            text = {
                Text("All photos and timelapse files on the SD card will be deleted. There is no undo. Make sure you've copied everything off the card first.")
            },
            confirmButton = {
                TextButton(onClick = { dialog = false; vm.formatSd() }) { Text("Format") }
            },
            dismissButton = {
                TextButton(onClick = { dialog = false }) { Text("Cancel") }
            },
        )
    }
}
