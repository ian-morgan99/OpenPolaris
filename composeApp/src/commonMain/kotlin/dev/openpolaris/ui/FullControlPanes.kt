package dev.openpolaris.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.openpolaris.core.config.FeatureFlags
import dev.openpolaris.core.domain.AstroMediaExportDestination
import dev.openpolaris.core.domain.AstroMediaExportState
import dev.openpolaris.core.domain.AstroMediaPolicy
import dev.openpolaris.core.domain.AstroMediaState
import dev.openpolaris.core.domain.AstroMediaTransferKind
import dev.openpolaris.core.domain.AstroWebUiStatusState
import dev.openpolaris.core.domain.MediaPolicy
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
    Card(modifier = modifier.padding(openPolarisCardOuterPadding())) {
        Column(Modifier.padding(openPolarisCardInnerPadding()), verticalArrangement = Arrangement.spacedBy(if (LocalOpenPolarisCompact.current) 8.dp else 10.dp)) {
            Text("Astro helpers", style = MaterialTheme.typography.titleMedium)
            if (!FeatureFlags.isEnabled("advancedAstro") && !FeatureFlags.isEnabled("autoLevel")) {
                Text(
                    "Helpers and auto-level are off in the current config. Enable " +
                        "advancedAstro and autoLevel in FeatureFlags to use this pane " +
                        "(both default ON; only flagged OFF on kiosk/test rigs).",
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
    // v0.1.12: split into two rows so the controls fit inside the
    // 250dp-wide Helpers dialog. The previous single-row layout
    // (label 110dp + value 48dp + Switch + spacer + Button) overflowed
    // by ~28dp, clipping the controls and rotating the "read-only"
    // caption to vertical text (#47). See
    // .copilot/agent-state/.../v0111-ui-audit/22_helpers_pane.png.
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Dither", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            vm.ditherEnabled?.let { if (it) "ON" else "OFF" } ?: "—",
            fontWeight = FontWeight.SemiBold,
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CompactSwitch(
            enabled = FeatureFlags.isEnabled("advancedAstro"),
            checked = vm.ditherEnabled == true,
            onCheckedChange = vm::setDither,
        )
        Spacer(Modifier.weight(1f))
        ResponsiveActionButton(
            graphic = OpenPolarisGraphic.Refresh,
            label = "Refresh",
            contentDescription = "Refresh helper state",
            onClick = vm::refreshHelpers,
            outlined = true,
        )
    }
}

@Composable
private fun SettlingRow(vm: AppViewModel) {
    var draft by remember(vm.settlingTime) { mutableStateOf(vm.settlingTime?.toString() ?: "") }
    Text("Settling (s)", style = MaterialTheme.typography.bodyMedium)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CompactOutlinedTextField(
            value = draft,
            onValueChange = { draft = it.filter { c -> c.isDigit() }.take(5) },
            singleLine = true,
            label = { Text("seconds") },
            modifier = Modifier.weight(1f),
        )
        ResponsiveActionButton(
            graphic = OpenPolarisGraphic.Apply,
            label = "Apply",
            contentDescription = "Apply settling time",
            enabled = FeatureFlags.isEnabled("advancedAstro"),
            onClick = {
                val v = draft.toIntOrNull()
                if (v != null && v >= 0) vm.setSettlingTimeMs(v * 1000) else vm.refreshSettling()
            },
        )
        ResponsiveActionButton(
            graphic = OpenPolarisGraphic.Refresh,
            label = "Refresh",
            contentDescription = "Refresh settling time",
            onClick = vm::refreshSettling,
            outlined = true,
        )
    }
}

@Composable
private fun LimitsRow(vm: AppViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Limits", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            vm.limitsEnabled?.let { if (it) "ON" else "OFF" } ?: "—",
            fontWeight = FontWeight.SemiBold,
        )
    }
    if (FeatureFlags.isEnabled("limitsWrite")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactSwitch(
                checked = vm.limitsEnabled == true,
                onCheckedChange = vm::setLimits,
            )
            Text("UNVERIFIED", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            ResponsiveActionButton(
                graphic = OpenPolarisGraphic.Refresh,
                label = "Refresh",
                contentDescription = "Refresh helper state",
                onClick = vm::refreshHelpers,
                outlined = true,
            )
        }
    } else {
        // Read-only: the write path is gated off, so we surface the value
        // the mount reports but offer no toggle. The user can still see
        // the current state and hit Refresh to re-read.
        Text(
            "read-only (set limitsWrite=true in config to toggle)",
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            ResponsiveActionButton(
                graphic = OpenPolarisGraphic.Refresh,
                label = "Refresh",
                contentDescription = "Refresh helper state",
                onClick = vm::refreshHelpers,
                outlined = true,
            )
        }
    }
}

@Composable
private fun AutoLevelRow(vm: AppViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Auto-level", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            vm.autoLevelEnabled?.let { if (it) "ENABLED" else "DISABLED" } ?: "—",
            fontWeight = FontWeight.SemiBold,
        )
    }
    if (FeatureFlags.isEnabled("autoLevel")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactSwitch(
                checked = vm.autoLevelEnabled == true,
                onCheckedChange = vm::setAutoLevelEnabled,
            )
            Spacer(Modifier.weight(1f))
            ResponsiveActionButton(
                graphic = OpenPolarisGraphic.Helpers,
                label = "Run now",
                contentDescription = "Run auto-level",
                onClick = vm::runAutoLevel,
                outlined = true,
            )
            ResponsiveActionButton(
                graphic = OpenPolarisGraphic.Refresh,
                label = "Refresh",
                contentDescription = "Refresh auto-level state",
                onClick = vm::refreshAutoLevel,
                outlined = true,
            )
        }
    } else {
        // Read-only: kiosk/test rigs may set autoLevel=false to hide
        // the toggle and Run-now button. 547/548/549 are live-confirmed
        // (Functions Report §2.3) so this is a UI-level hide, not a
        // safety gate.
        Text(
            "read-only (set autoLevel=true in config to enable)",
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            ResponsiveActionButton(
                graphic = OpenPolarisGraphic.Refresh,
                label = "Refresh",
                contentDescription = "Refresh auto-level state",
                onClick = vm::refreshAutoLevel,
                outlined = true,
            )
        }
    }
}

// =============================================================================
// System pane: time, timezone, language, buzzer, LED, WiFi, reboot, shutdown
// =============================================================================

@Composable
fun SystemPane(vm: AppViewModel, modifier: Modifier = Modifier) {
    Card(modifier = modifier.padding(openPolarisCardOuterPadding())) {
        Column(Modifier.padding(openPolarisCardInnerPadding()), verticalArrangement = Arrangement.spacedBy(if (LocalOpenPolarisCompact.current) 8.dp else 10.dp)) {
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
        CompactOutlinedTextField(
            value = draft,
            onValueChange = { draft = it.filter { c -> c.isDigit() }.take(12) },
            singleLine = true,
            label = { Text("seconds since 1970") },
            modifier = Modifier.width(160.dp),
        )
        ResponsiveActionButton(
            graphic = OpenPolarisGraphic.Apply,
            label = "Set",
            contentDescription = "Set mount system time",
            enabled = FeatureFlags.isEnabled("systemSettingsUnverified"),
            onClick = { draft.toLongOrNull()?.let(vm::setSystemTime) },
        )
        ResponsiveActionButton(
            graphic = OpenPolarisGraphic.Clock,
            label = "Now",
            contentDescription = "Use the current system time",
            onClick = { draft = (System.currentTimeMillis() / 1000L).toString() },
            outlined = true,
        )
    }
}

@Composable
private fun TzRow(vm: AppViewModel) {
    var draft by remember { mutableStateOf("0") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Timezone", modifier = Modifier.width(110.dp))
        CompactOutlinedTextField(
            value = draft,
            onValueChange = { draft = it.filter { c -> c == '-' || c.isDigit() }.take(4) },
            singleLine = true,
            label = { Text("offset hours") },
            modifier = Modifier.width(110.dp),
        )
        ResponsiveActionButton(
            graphic = OpenPolarisGraphic.Apply,
            label = "Set",
            contentDescription = "Set mount timezone offset",
            enabled = FeatureFlags.isEnabled("systemSettingsUnverified"),
            onClick = { draft.toIntOrNull()?.let(vm::setTimezone) },
        )
    }
}

@Composable
private fun LangRow(vm: AppViewModel) {
    var draft by remember { mutableStateOf("0") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Language", modifier = Modifier.width(110.dp))
        CompactOutlinedTextField(
            value = draft,
            onValueChange = { draft = it.filter { c -> c.isDigit() }.take(3) },
            singleLine = true,
            label = { Text("index") },
            modifier = Modifier.width(110.dp),
        )
        ResponsiveActionButton(
            graphic = OpenPolarisGraphic.Apply,
            label = "Set",
            contentDescription = "Set mount language",
            enabled = FeatureFlags.isEnabled("systemSettingsUnverified"),
            onClick = { draft.toIntOrNull()?.let(vm::setLanguage) },
        )
    }
}

@Composable
private fun BuzzerRow(vm: AppViewModel) {
    var on by remember { mutableStateOf(true) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Buzzer", modifier = Modifier.width(110.dp))
        CompactSwitch(
            checked = on,
            onCheckedChange = { on = it; vm.setBuzzer(it) },
            enabled = FeatureFlags.isEnabled("systemSettings"),
        )
        Text(if (on) "ON" else "OFF")
    }
}

@Composable
private fun LedRow(vm: AppViewModel) {
    var on by remember { mutableStateOf(true) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Status LED", modifier = Modifier.width(110.dp))
        CompactSwitch(
            checked = on,
            onCheckedChange = { on = it; vm.setLed(it) },
            enabled = FeatureFlags.isEnabled("systemSettings"),
        )
        Text(if (on) "ON" else "OFF")
    }
}

@Composable
private fun WifiRow(vm: AppViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("WiFi band", modifier = Modifier.width(110.dp))
        Text(
            // 802 returns 0=2.4 GHz, 1=5 GHz on every firmware we've seen.
            when (vm.wifiBand) {
                0 -> "2.4 GHz"
                1 -> "5 GHz"
                null -> "—"
                else -> "code=${vm.wifiBand}"
            },
            modifier = Modifier.width(80.dp),
            fontWeight = FontWeight.SemiBold,
        )
        ResponsiveActionButton(
            graphic = OpenPolarisGraphic.Refresh,
            label = "Scan",
            contentDescription = "Scan mount WiFi bands",
            enabled = FeatureFlags.isEnabled("wifiScan"),
            onClick = vm::refreshWifiScan,
            outlined = true,
        )
        Spacer(Modifier.width(8.dp))
        Text(vm.wifiScanResult?.take(40) ?: "—", style = MaterialTheme.typography.bodySmall)
    }
    if (FeatureFlags.isEnabled("wifiConnect")) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ResponsiveActionButton(
                graphic = OpenPolarisGraphic.Connection,
                label = "Connect SSID…",
                contentDescription = "Connect to a mount Wi-Fi network",
                onClick = { vm.connectWifiSsid("") },
                outlined = true,
            )
            ResponsiveActionButton(
                graphic = OpenPolarisGraphic.Cancel,
                label = "Disconnect",
                contentDescription = "Disconnect mount Wi-Fi",
                onClick = vm::disconnectWifi,
                outlined = true,
            )
        }
    } else {
        // Read-only: scanning is safe, but the connect/disconnect path is
        // gated off because we have not verified the round-trip on real
        // hardware (and a mis-typed SSID can lock you out of the head).
        Text(
            "Connect/disable hidden — set wifiConnect=true in config to surface.",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun RebootRow(vm: AppViewModel) {
    var dialog by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Reboot", modifier = Modifier.width(110.dp), fontWeight = FontWeight.Bold)
        if (FeatureFlags.isEnabled("allowReboot")) {
            ResponsiveActionButton(
                graphic = OpenPolarisGraphic.Refresh,
                label = "Reboot mount",
                contentDescription = "Open the reboot mount confirmation",
                onClick = { dialog = true },
            )
        } else {
            // Read-only: destructive action gated off. The user can still see
            // what the action is — they just can't fire it from the app.
            Text(
                "disabled — set allowReboot=true in config to enable",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
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
        if (FeatureFlags.isEnabled("allowShutdown")) {
            ResponsiveActionButton(
                graphic = OpenPolarisGraphic.Stop,
                label = "Shutdown mount",
                contentDescription = "Open the shutdown mount confirmation",
                onClick = { dialog = true },
            )
        } else {
            // Read-only: the head does not power itself back on, so this is
            // destructive enough that it stays behind a flag.
            Text(
                "disabled — set allowShutdown=true in config to enable",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
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
// Gallery pane: confirmed capture publication + SD-card browse
// =============================================================================

@Composable
fun CaptureTimelinePane(vm: AppViewModel, modifier: Modifier = Modifier) {
    val entries = vm.captureTimeline.takeLast(8)
    if (entries.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Capture timeline", style = MaterialTheme.typography.titleSmall)
        entries.forEach { entry ->
            Text(
                "${entry.stage.label}: ${entry.detail}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
fun GalleryPane(vm: AppViewModel, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (LocalOpenPolarisCompact.current) 4.dp else 8.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(openPolarisCardInnerPadding()), verticalArrangement = Arrangement.spacedBy(if (LocalOpenPolarisCompact.current) 5.dp else 6.dp)) {
                Text("Latest capture", style = MaterialTheme.typography.titleMedium)
                val publication = vm.lastCapturePublication
                if (publication == null) {
                    Text(
                        "No capture has been confirmed in this session.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(publication.path, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        buildString {
                            publication.type?.let { append("type $it") }
                            publication.sizeBytes?.let {
                                if (isNotEmpty()) append(" · ")
                                append("${it} bytes")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Published only after the capture lifecycle and file event were correlated.",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        CaptureTimelinePane(vm, Modifier.fillMaxWidth())
        AstroWebUiGalleryPane(vm, Modifier.fillMaxWidth())
        FilesPane(vm, Modifier.fillMaxWidth())
    }
}

// =============================================================================
// Astro WebUI gallery: source-aligned, read-only HTTP media surface
// =============================================================================

@Composable
fun AstroWebUiGalleryPane(vm: AppViewModel, modifier: Modifier = Modifier) {
    Card(modifier = modifier.padding(openPolarisCardOuterPadding())) {
        Column(Modifier.padding(openPolarisCardInnerPadding()), verticalArrangement = Arrangement.spacedBy(openPolarisSectionSpacing())) {
            Text("Astro WebUI gallery", style = MaterialTheme.typography.titleMedium)
            Text(
                "Optional resident WebUI index with thumbnail and full-JPEG reads. " +
                    "This is separate from native 770/772/773 media transfer and cannot delete files.",
                style = MaterialTheme.typography.bodySmall,
            )
            val enabled = FeatureFlags.isEnabled("astroWebUiMedia")
            val statusEnabled = FeatureFlags.isEnabled("astroWebUiStatus")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ResponsiveActionButton(
                    graphic = OpenPolarisGraphic.Refresh,
                    label = "Refresh gallery",
                    contentDescription = "Refresh Astro WebUI gallery",
                    enabled = enabled,
                    onClick = vm::refreshAstroMedia,
                )
                if (!enabled) {
                    Text("Disabled by config", style = MaterialTheme.typography.labelSmall)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ResponsiveActionButton(
                    graphic = OpenPolarisGraphic.Refresh,
                    label = "Refresh Astro status",
                    contentDescription = "Refresh Astro WebUI status",
                    enabled = statusEnabled,
                    onClick = vm::refreshAstroWebUiStatus,
                )
            }
            when (val state = vm.astroWebUiStatus) {
                AstroWebUiStatusState.Idle -> Text("Status not checked.", style = MaterialTheme.typography.labelSmall)
                AstroWebUiStatusState.Loading -> Text("Reading Astro mode…", style = MaterialTheme.typography.labelSmall)
                is AstroWebUiStatusState.Ready -> {
                    val status = state.status
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            if (status.active) "Astro mode active" else "Astro mode inactive",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        if (status.latitudeDeg != null && status.longitudeDeg != null) {
                            Text(
                                "Site ${status.latitudeDeg}, ${status.longitudeDeg}",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        status.trackRequested?.let { requested ->
                            Text(
                                "Tracking requested: ${if (requested) "yes" else "no"}",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        status.rateIndex?.let { rate ->
                            Text("Rate index: ${rate}", style = MaterialTheme.typography.labelSmall)
                        }
                        status.halfSpeed?.let { half ->
                            Text(
                                "Half speed: ${if (half) "yes" else "no"}",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        status.alignmentHeadingDeg?.let { heading ->
                            Text(
                                "Alignment heading: ${heading} deg",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        Text(
                            "Session/request state only; mount telemetry remains authoritative.",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                is AstroWebUiStatusState.Failed -> Text(state.reason, style = MaterialTheme.typography.labelSmall)
            }

            when (val state = vm.astroMediaState) {
                AstroMediaState.Idle -> Text("No WebUI media list loaded.", style = MaterialTheme.typography.bodySmall)
                AstroMediaState.LoadingList -> Text("Loading WebUI media list…", style = MaterialTheme.typography.bodySmall)
                is AstroMediaState.LoadingItem -> Text(
                    "Loading ${state.kind.name.lowercase()} for ${state.path}…",
                    style = MaterialTheme.typography.bodySmall,
                )
                is AstroMediaState.Failed -> Text(state.reason, style = MaterialTheme.typography.bodySmall)
                is AstroMediaState.Ready -> Text(
                    "WebUI reports ${state.reportedCount} item(s); showing ${vm.astroMediaItems?.size ?: 0}.",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            when (val exportState = vm.astroMediaExportState) {
                AstroMediaExportState.Idle -> Unit
                is AstroMediaExportState.Ready -> Text(
                    "Ready to export ${exportState.request.suggestedName} (${exportState.bytesExpected} bytes).",
                    style = MaterialTheme.typography.labelSmall,
                )
                is AstroMediaExportState.Exporting -> Text(
                    "Exporting ${exportState.request.suggestedName}…",
                    style = MaterialTheme.typography.labelSmall,
                )
                is AstroMediaExportState.Complete -> Text(
                    "Exported ${exportState.request.suggestedName} (${exportState.bytesStored} bytes).",
                    style = MaterialTheme.typography.labelSmall,
                )
                is AstroMediaExportState.Failed -> Text(
                    "Export unavailable: ${exportState.reason}",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            val allItems = vm.astroMediaItems.orEmpty()
            if (allItems.isNotEmpty()) {
                val categories = allItems.map { it.category }.distinct().sorted()
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    CompactFilterChip(
                        selected = vm.astroMediaCategory == null,
                        onClick = { vm.selectAstroMediaCategory(null) },
                        enabled = vm.astroMediaCategory != null,
                        label = { Text("All (${allItems.size})") },
                        graphic = OpenPolarisGraphic.Preview,
                        contentDescription = "Show all Astro WebUI media",
                    )
                    categories.forEach { category ->
                        val categoryLabel = AstroMediaPolicy.labelFor(category)
                        CompactFilterChip(
                            selected = vm.astroMediaCategory == category,
                            onClick = { vm.selectAstroMediaCategory(category) },
                            enabled = vm.astroMediaCategory != category,
                            label = { Text("${categoryLabel} (${allItems.count { it.category == category }})") },
                            graphic = OpenPolarisGraphic.Camera,
                            contentDescription = "Show ${categoryLabel} Astro WebUI media",
                        )
                    }
                }
                val items = vm.astroMediaCategory?.let { category ->
                    allItems.filter { it.category == category }
                } ?: allItems
                Text(
                    "Showing ${vm.astroMediaCategory?.let(AstroMediaPolicy::labelFor) ?: "all media"} · ${items.size} item(s)",
                    style = MaterialTheme.typography.labelSmall,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items.take(40).forEach { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(item.path, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "${item.category} · ${item.sizeBytes} bytes",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            ResponsiveActionButton(
                                graphic = OpenPolarisGraphic.Preview,
                                label = "Thumb",
                                contentDescription = "Load thumbnail for ${item.path}",
                                enabled = enabled,
                                onClick = { vm.loadAstroMedia(item.path, AstroMediaTransferKind.THUMBNAIL) },
                            )
                            ResponsiveActionButton(
                                graphic = OpenPolarisGraphic.Camera,
                                label = "Full",
                                contentDescription = "Load full file for ${item.path}",
                                enabled = enabled,
                                onClick = { vm.loadAstroMedia(item.path, AstroMediaTransferKind.FULL_FILE) },
                                outlined = true,
                            )
                        }
                        val preview = vm.astroMediaPreview
                        if (preview?.path == item.path) {
                            val bitmap = bytesToImageBitmap(preview.bytes)
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = item.path,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxWidth().height(180.dp),
                                )
                            }
                            Text(
                                "${preview.kind.name.lowercase()} · ${preview.bytes.size} bytes · ${preview.contentType}",
                                style = MaterialTheme.typography.labelSmall,
                            )
                            if (preview.kind == AstroMediaTransferKind.FULL_FILE) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ResponsiveActionButton(
                                        graphic = OpenPolarisGraphic.Save,
                                        label = "Save copy",
                                        contentDescription = "Save a copy of the Astro WebUI preview",
                                        enabled = enabled,
                                        onClick = { vm.exportAstroMedia(AstroMediaExportDestination.SAVE_COPY) },
                                    )
                                    ResponsiveActionButton(
                                        graphic = OpenPolarisGraphic.Share,
                                        label = "Share",
                                        contentDescription = "Share the Astro WebUI preview",
                                        enabled = enabled,
                                        onClick = { vm.exportAstroMedia(AstroMediaExportDestination.SHARE) },
                                        outlined = true,
                                    )
                                }
                            }
                        }
                    }
                }
                if (items.size > 40) {
                    Text("Showing the first 40 items; native file list remains available below.", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

// =============================================================================
// Files pane: list / delete / protect / SD format
// =============================================================================

@Composable
fun FilesPane(vm: AppViewModel, modifier: Modifier = Modifier) {
    Card(modifier = modifier.padding(openPolarisCardOuterPadding())) {
        Column(Modifier.padding(openPolarisCardInnerPadding()), verticalArrangement = Arrangement.spacedBy(if (LocalOpenPolarisCompact.current) 8.dp else 10.dp)) {
            Text("SD card files", style = MaterialTheme.typography.titleMedium)
            SdHeaderRow(vm)
            val fileManagerEnabled = FeatureFlags.isEnabled("fileManager")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Category", style = MaterialTheme.typography.labelMedium)
                listOf(0 to "All", 1 to "Normal").forEach { (type, label) ->
                    CompactFilterChip(
                        selected = vm.fileListType == type,
                        enabled = fileManagerEnabled && vm.fileListType != type,
                        onClick = {
                            vm.setFileType(type)
                            vm.setFilePage(0)
                            vm.refreshFileList()
                        },
                        label = { Text(label) },
                        graphic = if (type == 0) OpenPolarisGraphic.Preview else OpenPolarisGraphic.Camera,
                        contentDescription = "Show ${label} files",
                    )
                }
                ResponsiveActionButton(
                    graphic = OpenPolarisGraphic.Refresh,
                    label = "Refresh",
                    contentDescription = "Refresh SD card file list",
                    enabled = fileManagerEnabled,
                    onClick = vm::refreshFileList,
                    outlined = true,
                )
            }
            val fileList = vm.fileList
            val files = fileList?.files.orEmpty()
            if (fileList != null) {
                Text(
                    "Type ${vm.fileListType} · page ${vm.fileListPage}" +
                        (fileList.count?.let { " · ${files.size} of $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            HorizontalDivider()
            if (files.isEmpty()) {
                Text("(no files loaded — hit Refresh list)", style = MaterialTheme.typography.bodySmall)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    files.forEach { f -> FileRow(vm, f) }
                }
            }
            val canNextPage = fileList?.count?.let { total ->
                files.isNotEmpty() && (vm.fileListPage + 1) * files.size < total
            } ?: false
            if (fileList != null && (vm.fileListPage > 0 || canNextPage)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ResponsiveActionButton(
                        graphic = OpenPolarisGraphic.Left,
                        label = "Previous",
                        contentDescription = "Previous file page",
                        enabled = fileManagerEnabled && vm.fileListPage > 0,
                        onClick = {
                            vm.setFilePage(vm.fileListPage - 1)
                            vm.refreshFileList()
                        },
                        outlined = true,
                    )
                    ResponsiveActionButton(
                        graphic = OpenPolarisGraphic.Right,
                        label = "Next",
                        contentDescription = "Next file page",
                        enabled = fileManagerEnabled && canNextPage,
                        onClick = {
                            vm.setFilePage(vm.fileListPage + 1)
                            vm.refreshFileList()
                        },
                        outlined = true,
                    )
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
        Text(
            "${file.name} · ${MediaPolicy.kind(file.name).label}",
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        if (file.prot != 0) Text("🔒", style = MaterialTheme.typography.bodySmall)
        if (FeatureFlags.isEnabled("fileManagerMutate")) {
            ResponsiveActionButton(
                graphic = if (file.prot == 0) OpenPolarisGraphic.Protect else OpenPolarisGraphic.Unprotect,
                label = if (file.prot == 0) "Protect" else "Unprotect",
                contentDescription = if (file.prot == 0) "Protect file ${file.name}" else "Unprotect file ${file.name}",
                onClick = { vm.protectFile(file.id, if (file.prot == 0) 1 else 0) },
                outlined = true,
            )
            ResponsiveActionButton(
                graphic = OpenPolarisGraphic.Delete,
                label = "Delete",
                contentDescription = "Delete file ${file.name}",
                onClick = { vm.deleteFile(file.id) },
            )
        } else {
            // Read-only: file delete/protect is destructive enough that we
            // never expose it without an explicit config flip.
            Text(
                "read-only",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun FormatRow(vm: AppViewModel) {
    var dialog by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("SD format", modifier = Modifier.width(110.dp), fontWeight = FontWeight.Bold)
        if (FeatureFlags.isEnabled("fileManagerFormat")) {
            ResponsiveActionButton(
                graphic = OpenPolarisGraphic.Delete,
                label = "Format SD card",
                contentDescription = "Open the format SD card confirmation",
                onClick = { dialog = true },
            )
        } else {
            // Read-only: SD format is destructive — every photo on the card
            // is gone forever. Stays behind a config flag.
            Text(
                "disabled — set fileManagerFormat=true in config to enable",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
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
