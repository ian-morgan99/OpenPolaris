package dev.openpolaris.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import dev.openpolaris.core.protocol.Codes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import dev.openpolaris.core.config.FeatureFlags
import dev.openpolaris.core.astro.AstroMath
import dev.openpolaris.core.domain.CameraReleaseMode
import dev.openpolaris.core.domain.EndpointService
import dev.openpolaris.core.domain.AstroMissionFlow
import dev.openpolaris.core.domain.AstroMissionInput
import dev.openpolaris.core.domain.AstroPrepareCheckState
import dev.openpolaris.core.domain.AstroPrepareChecklist
import dev.openpolaris.core.domain.AstroPrepareInput
import dev.openpolaris.core.domain.AstroWebUiSiteState
import dev.openpolaris.core.domain.AstroWebUiAstroCaptureParameters
import dev.openpolaris.core.domain.AstroWebUiAstroCaptureState
import dev.openpolaris.core.domain.AstroWebUiSiteWriteState
import dev.openpolaris.core.domain.AstroWebUiSunParameters
import dev.openpolaris.core.domain.AstroWebUiSunState
import dev.openpolaris.core.domain.AstroWebUiMotionProgrammeState
import dev.openpolaris.core.domain.AstroWebUiPlcKeyRequest
import dev.openpolaris.core.domain.AstroWebUiPlcParameters
import dev.openpolaris.core.domain.AstroWebUiPathLapseParameters
import dev.openpolaris.core.domain.AstroWebUiPathWaypointRequest
import dev.openpolaris.core.domain.AstroWebUiGrailNode
import dev.openpolaris.core.domain.AstroWebUiGrailParameters
import dev.openpolaris.core.domain.AstroWebUiStatusState
import dev.openpolaris.core.domain.AstroWebUiGuideUiState
import dev.openpolaris.core.domain.AstroWebUiAutofocusParameters
import dev.openpolaris.core.domain.AstroWebUiAutofocusUiState
import dev.openpolaris.core.domain.AstroWebUiFocalUiState
import dev.openpolaris.core.domain.AstroWebUiLimitsUiState
import dev.openpolaris.core.domain.AstroWebUiKeepWifiUiState
import dev.openpolaris.core.domain.AstroWebUiWifiUiState
import dev.openpolaris.core.domain.AstroWebUiLinkUiState
import dev.openpolaris.core.domain.AstroWebUiAlignRequest
import dev.openpolaris.core.domain.AstroWebUiGotoRequest
import dev.openpolaris.core.domain.AstroWebUiTargetUiState
import dev.openpolaris.core.domain.AstroWebUiRunStateUiState
import dev.openpolaris.core.domain.AstroWebUiSolveUiState
import dev.openpolaris.core.domain.AstroWebUiSolveControlUiState
import dev.openpolaris.core.domain.AstroWebUiSolveMode
import dev.openpolaris.core.domain.AstroWebUiSessionControlState
import dev.openpolaris.core.domain.AstroWebUiTrackRate
import dev.openpolaris.core.domain.AstroWebUiTrackRequest
import dev.openpolaris.core.domain.AstroWebUiProgrammeStartResult
import dev.openpolaris.core.domain.AstroWebUiProgrammeStartState
import dev.openpolaris.core.domain.AstroWebUiProgrammeState
import dev.openpolaris.core.domain.AstroWebUiLapseParameters
import dev.openpolaris.core.domain.AstroWebUiPanoramaParameters
import dev.openpolaris.core.domain.AstroWebUiHdrParameters
import dev.openpolaris.core.domain.AstroWebUiFocusParameters
import dev.openpolaris.core.domain.SlowMotionCapabilities
import dev.openpolaris.core.domain.SlowMotionCalibration
import dev.openpolaris.core.domain.SupportBundleExportState
import dev.openpolaris.core.domain.MissionReadiness
import dev.openpolaris.core.domain.MissionReadinessInput
import dev.openpolaris.core.domain.SystemMillis
import dev.openpolaris.core.domain.ObservationProgrammePlanner
import dev.openpolaris.core.domain.format2
import dev.openpolaris.core.domain.DeliveryMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Read-only health and evidence surface for support and field diagnosis.
 *
 * It intentionally reports what OpenPolaris has observed, not what a command
 * might have meant. A simulator trace is labelled as such and a capture file
 * is only shown as confirmed when the lifecycle and publication evidence were
 * correlated by the view model.
 */
@Composable
fun EvidenceCentrePane(
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    versionLabel: String? = null,
) {
    val endpoint = vm.endpointHealth
    val captureSummary = when (val phase = vm.capturePhase) {
        AppViewModel.CapturePhase.Idle -> "Idle"
        AppViewModel.CapturePhase.Requested -> "Requested"
        AppViewModel.CapturePhase.Busy -> "Camera active"
        AppViewModel.CapturePhase.Completed -> "Confirmed: lifecycle + file publication"
        is AppViewModel.CapturePhase.OutcomeUnknown -> "Outcome unknown: ${phase.reason}"
        is AppViewModel.CapturePhase.Failed -> "Failed: ${phase.reason}"
    }
    val cameraSummary = vm.cameraAttachment?.let { attachment ->
        if (attachment.isAttached) {
            listOfNotNull(attachment.manufacturer, attachment.model).joinToString(" ").ifBlank { "Attached camera" }
        } else {
            "No camera attached (state ${attachment.state ?: "unknown"})"
        }
    } ?: "Not checked"
    val routeSummary = endpoint.profile?.let { profile ->
        "${profile.label} ${profile.address.host}:${profile.address.controlPort} / preview ${profile.address.previewPort}"
    } ?: "No endpoint selected"

    Card(modifier.padding(8.dp)) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Evidence and health", style = MaterialTheme.typography.titleMedium)
            Text(
                "Read-only support view. Values are observed in this session; unknown is not success.",
                style = MaterialTheme.typography.bodySmall,
            )
            HorizontalDivider()
            Text("Build and route", style = MaterialTheme.typography.titleSmall)
            EvidenceRow("App build", versionLabel ?: "Host build identity not supplied")
            EvidenceRow("Endpoint", routeSummary)
            EvidenceRow("Route phase", endpoint.phase.name)
            EvidenceRow("Control service", endpoint.control.name)
            EvidenceRow("Preview service", endpoint.preview.name)
            endpoint.lastFailure?.let { EvidenceRow("Last failure", it) }

            HorizontalDivider()
            Text("Device and mission state", style = MaterialTheme.typography.titleSmall)
            EvidenceRow("Mount", if (vm.mount.connected) "Connected" else "Disconnected")
            EvidenceRow("Firmware", vm.firmwareVersion ?: vm.deviceInfo?.software ?: "Not observed")
            EvidenceRow("Hardware", vm.deviceInfo?.hardware ?: "Not observed")
            EvidenceRow("Serial", vm.serialNumber ?: "Not observed")
            EvidenceRow("Camera", cameraSummary)
            EvidenceRow("SD storage", vm.sdStatus?.let { status ->
                if (status.present) {
                    "${status.freeMb ?: "?"} MB free / ${status.totalMb ?: "?"} MB"
                } else {
                    "Not present"
                }
            } ?: "Not checked")
            EvidenceRow("Capture", captureSummary)

            HorizontalDivider()
            Text("Capability gates", style = MaterialTheme.typography.titleSmall)
            EvidenceRow(
                "Slow motion",
                if (vm.demoMode) {
                    "Simulator-only trace available; live transport remains gated"
                } else {
                    "Blocked pending exact firmware route and physical stop evidence"
                },
            )
            EvidenceRow(
                "Preview",
                if (endpoint.previewReady) "Hardware stream reachable" else "Unknown or unavailable",
            )
            EvidenceRow(
                "Alignment",
                if (vm.alignmentStars > 0 || vm.lastSolveResult.value != null) {
                    "Identity recorded in this session"
                } else {
                    "Not recorded"
                },
            )

            HorizontalDivider()
            Text("Recent capture evidence", style = MaterialTheme.typography.titleSmall)
            if (vm.captureTimeline.isEmpty()) {
                Text(
                    "No correlated capture lifecycle has been recorded.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                vm.captureTimeline.takeLast(6).forEach { entry ->
                    EvidenceRow(entry.stage.label, entry.detail)
                }
            }
            vm.lastCapturePublication?.let { publication ->
                EvidenceRow(
                    "Published file",
                    "${publication.path} (${publication.sizeBytes ?: "size unknown"} bytes)",
                )
            }

            HorizontalDivider()
            Text("Support bundle", style = MaterialTheme.typography.titleSmall)
            Text(
                "Save a bounded snapshot of observed health and capture evidence. Hostnames, credentials, raw frames and camera bytes are omitted.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = { vm.exportSupportBundle(versionLabel) },
                enabled = vm.supportBundleExportState !is SupportBundleExportState.Exporting,
            ) {
                Text("Save support bundle")
            }
            when (val export = vm.supportBundleExportState) {
                SupportBundleExportState.Idle -> Unit
                is SupportBundleExportState.Ready -> Text(
                    "Preparing save chooser...",
                    style = MaterialTheme.typography.bodySmall,
                )
                is SupportBundleExportState.Exporting -> Text(
                    "Waiting for a save location...",
                    style = MaterialTheme.typography.bodySmall,
                )
                is SupportBundleExportState.Complete -> Text(
                    "Saved: " + (export.location ?: "user-selected destination"),
                    style = MaterialTheme.typography.bodySmall,
                )
                is SupportBundleExportState.Failed -> Text(
                    "Not saved: " + export.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun EvidenceRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(112.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Connection pane: host entry, connect/demo buttons, status line.
 * [onFindWifi], when provided, opens a platform Wi-Fi picker so the user can
 * join the mount's access point without leaving the app. This is the
 * coarse fallback — it drops the user into the system settings screen.
 * [onMountWifiScan], when provided, surfaces a "Find & wake Polaris…"
 * button that runs the platform's wake+AP-scan flow (BT pulse + Wi-Fi
 * scan filtered to `polaris*` SSIDs) and offers the strongest match.
 * This is the mobile analogue of the Benro app's first-tap behaviour
 * and supersedes the [onFindWifi] picker on Android. When both are
 * supplied, both buttons render so the user can fall back to the system
 * picker if the scan finds nothing.
 * [onBridgeWifi], when provided, surfaces the desktop bridge button — it
 * drives the BT-wake / NetworkManager-up / policy-route sequence via
 * [AppViewModel.connectWifi] so the user has a single tap from the app to
 * the segregated gimbal network. When only [onBridgeWifi] is supplied the
 * "Connect mount Wi-Fi…" picker button is suppressed (the bridge is the
 * canonical path on desktop); when both are supplied both buttons render so
 * the user can fall back to the picker if the bridge fails.
 * [onWake], when provided, surfaces a single "Wake" button that calls
 * [AppViewModel.wake] — a BT-only GATT-connect pulse that brings the
 * gimbal's Wi-Fi AP up without doing the full bridge. The Benro app
 * exposes this as the first tap on a cold start (the gimbal sleeps to
 * save battery; nothing answers on Wi-Fi until the pulse has fired).
 * [onBridgeDown], when provided, surfaces a "Tear down bridge" button that
 * reverses [onBridgeWifi]: removes the policy route, brings the saved Wi-Fi
 * profile down, and releases the retained BLE GATT wake link. Desktop-only;
 * Android has no bridge to tear down so it leaves this null.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ConnectionPane(
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    onFindWifi: (() -> Unit)? = null,
    onBridgeWifi: (() -> Unit)? = null,
    onWake: (() -> Unit)? = null,
    onMountWifiScan: (suspend (suspend (String) -> Unit) -> Unit)? = null,
    onBridgeDown: (() -> Unit)? = null,
) {
    Card(modifier = modifier.padding(8.dp)) {
        val scope = rememberCoroutineScope()
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // No header — the Benro Connect aesthetic is a flat, full-width
            // form; the app name is already in the launcher icon, not the
            // content area. The status line beneath the form acts as the
            // surface label.
            OutlinedTextField(
                value = vm.host,
                onValueChange = vm::updateHost,
                label = { Text("Mount host") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            // 3b.5-BUG: port-edit field next to host. Pre-fix, the
            // user had no way to change the port from the connection
            // pane at all — `port` was a private mutableStateOf with
            // no setter, hard-coded to 9090 in connect() and
            // saveMarker(). Exposed here as a numeric field; if the
            // user types a non-numeric value we silently fall back to
            // the default 9090 (the previous valid value), which
            // avoids ever setting [port] to 0 or -1.
            OutlinedTextField(
                value = vm.port.toString(),
                onValueChange = { v -> vm.updatePort(v.toIntOrNull() ?: 9090) },
                label = { Text("Port") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            // Keep primary actions immediately visible in landscape. The
            // password field is optional for most firmwares and can sit below.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = vm::connect,
                    modifier = Modifier.semantics {
                        contentDescription = "Connect to mount"
                    },
                ) { Text("Connect") }
                if (onWake != null) {
                    OutlinedButton(
                        onClick = onWake,
                        enabled = !vm.waking.value,
                    ) {
                        Text(if (vm.waking.value) "Waking…" else "Wake")
                    }
                }
                OutlinedButton(onClick = vm::connectDemo) { Text("Demo mode") }
                OutlinedButton(onClick = vm::disconnect) { Text("Disconnect") }
            }
            // Connection-password field. Most production gimbal firmware
            // does not require a password (820→821 skipped, `needed:0`),
            // in which case leaving this blank is the correct state and
            // the field is just a no-op. Some firmwares report
            // `needed:1` from the 820 probe — those need a password to
            // complete the handshake, and connect() will surface an
            // IOException and flip [AppViewModel.needsPassword]. The
            // OpenPolarisApp root renders [PasswordDialog] in that case
            // so the user can set + retry. Having the field here too
            // means a user who already knows the gimbal needs a password
            // can pre-set it before the first connect.
            OutlinedTextField(
                value = vm.password.orEmpty(),
                onValueChange = { v -> vm.setConnectionPassword(v.ifEmpty { null }) },
                label = { Text("Connection password (optional)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                supportingText = {
                    Text("Required only if the gimbal reports needed:1 on probe.")
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (onBridgeWifi != null) {
                OutlinedButton(onClick = onBridgeWifi) {
                    Text("Bridge to mount Wi-Fi…")
                }
            }
            if (onBridgeDown != null) {
                OutlinedButton(onClick = onBridgeDown) {
                    Text("Tear down bridge")
                }
            }
            if (onMountWifiScan != null) {
                OutlinedButton(
                    onClick = {
                        // Host handles its own long-running work and posts
                        // progress through the VM's statusMessage so the
                        // line below tracks the wake/scan phases. The host
                        // callback is suspend, so we hop into the pane's
                        // own rememberCoroutineScope to invoke it without
                        // coupling the host (Android) to the pane's
                        // internal coroutine machinery.
                        scope.launch {
                            onMountWifiScan { msg -> vm.notifyStatus(msg) }
                        }
                    },
                    enabled = !vm.scanning.value,
                ) {
                    Text(if (vm.scanning.value) "Scanning…" else "Find & wake Polaris…")
                }
            }
            if (onFindWifi != null) {
                OutlinedButton(onClick = onFindWifi) {
                    Text("Open system Wi-Fi…")
                }
            }
            HorizontalDivider()
            EndpointHealthSection(vm)
            Text("Astro control link", style = MaterialTheme.typography.titleMedium)
            Text(
                "Read-only /api/link evidence from the resident Astro WebUI. It describes the server control link and cached opcode frames; it does not replace Bluetooth wake, bridge or native motion ownership.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    enabled = FeatureFlags.isEnabled("astroWebUiLinkStatus"),
                    onClick = vm::refreshAstroWebUiLink,
                ) { Text("Refresh link") }
                when (val link = vm.astroWebUiLink) {
                    AstroWebUiLinkUiState.Idle -> Text("State not checked.", style = MaterialTheme.typography.labelSmall)
                    AstroWebUiLinkUiState.Loading -> Text("Reading control-link telemetry…", style = MaterialTheme.typography.labelSmall)
                    is AstroWebUiLinkUiState.Failed -> Text(
                        link.reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    is AstroWebUiLinkUiState.Ready -> {
                        val state = link.state
                        Text(
                            state.phase.name.lowercase() + " · wanted " + if (state.wanted) "yes" else "no",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(
                            "Connects " + state.connects + " · drops " + state.drops +
                                " · RX " + state.receivedFrames + " · TX " + state.transmittedFrames,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(
                            "Parse errors " + state.parseErrors + " · evictions " + state.evictions +
                                " · last frame " + if (state.lastRxAgeMs >= 0) state.lastRxAgeMs.toString() + " ms ago" else "unknown",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        if (state.error.isNotBlank()) {
                            Text("Last error: " + state.error, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                        Text("Cached opcode frames: " + state.opcodes.size, style = MaterialTheme.typography.labelSmall)
                        state.opcodes.take(4).forEach { opcode ->
                            Text(
                                opcode.command.toString() + " · " + opcode.ageMs + " ms · " + opcode.args,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
            if (!FeatureFlags.isEnabled("astroWebUiLinkStatus")) {
                Text("Astro control-link telemetry is disabled by config.", style = MaterialTheme.typography.labelSmall)
            }
            HorizontalDivider()
            Text("Astro keep-Wi-Fi", style = MaterialTheme.typography.titleMedium)
            Text(
                "The resident helper can keep the mount link awake after alignment. Readback distinguishes enabled, running, aligned and forced; it does not claim the Bluetooth or Wi-Fi bridge is healthy.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    enabled = FeatureFlags.isEnabled("astroWebUiKeepWifiStatus"),
                    onClick = vm::refreshAstroWebUiKeepWifi,
                ) { Text("Refresh keep-Wi-Fi") }
                when (val keepWifi = vm.astroWebUiKeepWifi) {
                    AstroWebUiKeepWifiUiState.Idle -> Text(
                        "State not checked.",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    AstroWebUiKeepWifiUiState.Loading -> Text(
                        "Reading or changing keep-Wi-Fi…",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    is AstroWebUiKeepWifiUiState.Accepted -> Text(
                        "Change accepted; confirming by readback.",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    is AstroWebUiKeepWifiUiState.Failed -> Text(
                        keepWifi.reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    is AstroWebUiKeepWifiUiState.Ready -> {
                        Text(
                            when {
                                keepWifi.state.running && keepWifi.state.forced -> "Running · forced"
                                keepWifi.state.running -> "Running · alignment-aware"
                                keepWifi.state.enabled -> "Enabled · not running"
                                else -> "Off"
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Switch(
                            checked = keepWifi.state.enabled,
                            enabled = FeatureFlags.isEnabled("astroWebUiKeepWifiWrite"),
                            onCheckedChange = { enabled ->
                                vm.setAstroWebUiKeepWifi(enabled, keepWifi.state.forced)
                            },
                        )
                    }
                }
            }
            if (vm.astroWebUiKeepWifi is AstroWebUiKeepWifiUiState.Ready) {
                val state = (vm.astroWebUiKeepWifi as AstroWebUiKeepWifiUiState.Ready).state
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Aligned: " + if (state.aligned) "yes" else "no" +
                            " · Forced: " + if (state.forced) "yes" else "no",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    if (state.enabled) {
                        Switch(
                            checked = state.forced,
                            enabled = FeatureFlags.isEnabled("astroWebUiKeepWifiWrite"),
                            onCheckedChange = { forced ->
                                vm.setAstroWebUiKeepWifi(true, forced)
                            },
                        )
                    }
                }
            }
            if (!FeatureFlags.isEnabled("astroWebUiKeepWifiStatus")) {
                Text(
                    "Keep-Wi-Fi readback is disabled by config.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!FeatureFlags.isEnabled("astroWebUiKeepWifiWrite")) {
                Text(
                    "Keep-Wi-Fi writes are disabled until bridge, alignment and recovery behaviour are qualified.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            Text("Astro Wi-Fi", style = MaterialTheme.typography.titleMedium)
            Text(
                "Read-only interface and saved-network evidence from the resident Astro WebUI. This is separate from the app's Bluetooth wake and PC bridge phases.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    enabled = FeatureFlags.isEnabled("astroWebUiWifiStatus"),
                    onClick = vm::refreshAstroWebUiWifi,
                ) { Text("Refresh Wi-Fi") }
                when (val wifi = vm.astroWebUiWifi) {
                    AstroWebUiWifiUiState.Idle -> Text(
                        "State not checked.",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    AstroWebUiWifiUiState.Loading -> Text(
                        "Reading Wi-Fi state…",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    is AstroWebUiWifiUiState.Failed -> Text(
                        wifi.reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    is AstroWebUiWifiUiState.Ready -> {
                        Text(
                            "AP " + (wifi.state.apIp.ifBlank { "unknown" }) +
                                " · station " + (wifi.state.stationIp.ifBlank { "none" }),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(
                            "SSID " + (wifi.state.ssid.ifBlank { "none" }) +
                                " · configured " + if (wifi.state.configured) "yes" else "no" +
                                " · autojoin " + if (wifi.state.autojoin) "yes" else "no",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            if (!FeatureFlags.isEnabled("astroWebUiWifiStatus")) {
                Text(
                    "Astro Wi-Fi readback is disabled by config.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(vm.statusMessage, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * Modal password-entry dialog. Renders whenever [AppViewModel.needsPassword]
 * is true — the connect() flow flips that flag when the gimbal reports
 * `needed:1` from its 820 probe and we have no password configured. The user
 * types one in, presses "Connect" (or hits Enter / Done on the keyboard),
 * and the VM is asked to set the password and retry the connection.
 *
 * "Cancel" clears the password (so a subsequent plain Connect from the
 * Connection pane goes back to a clean, password-less state) and dismisses.
 *
 * The dialog is intentionally local in its own composable so the root
 * [OpenPolarisApp] only has to gate visibility on [vm].needsPassword.
 */
@Composable
fun PasswordDialog(
    vm: AppViewModel,
    onDismiss: () -> Unit,
) {
    // Local UI state for the text the user is typing. The "real" password
    // is only written to the VM on Connect, so dismissing the dialog
    // without committing doesn't mutate anything observable elsewhere.
    var input by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connection password required") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "The gimbal reported needed:1 on the handshake probe. " +
                        "Enter the connection password Benro Polaris uses for " +
                        "this firmware, then tap Connect to retry."
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (input.isNotEmpty()) {
                                vm.setConnectionPassword(input)
                                vm.connect()
                                onDismiss()
                            }
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (input.isNotEmpty()) {
                        vm.setConnectionPassword(input)
                        vm.connect()
                        onDismiss()
                    }
                },
                enabled = input.isNotEmpty(),
            ) { Text("Connect") }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    // Clear the in-memory password so a later Connect
                    // doesn't accidentally retry with whatever the user
                    // typed in the Connection pane.
                    vm.setConnectionPassword(null)
                    onDismiss()
                },
            ) { Text("Cancel") }
        },
    )
}

/** Status pane: mode, battery, tracking, half-speed, AHRS. */
// StatusPane was removed in v0.1.5: it was unreachable from the surface tree
// (grep confirmed zero callers), and the same status info is now always
// visible via the StatusStrip / PositionReadout chrome in OpenPolarisApp.

/**
 * Shared route-health section used by the Mission dashboard and Connection
 * workspace. It keeps control and preview evidence visibly separate and never
 * derives one service's state from the other.
 */
@Composable
private fun EndpointHealthSection(
    vm: AppViewModel,
    onOpenConnection: (() -> Unit)? = null,
) {
    val endpoint = vm.endpointHealth
    var clockMs by remember { mutableStateOf(vm.nowMs().coerceAtLeast(0L)) }
    LaunchedEffect(endpoint.phase, endpoint.phaseSinceMs) {
        if (endpoint.phaseSinceMs == null) {
            clockMs = vm.nowMs().coerceAtLeast(0L)
            return@LaunchedEffect
        }
        while (true) {
            clockMs = vm.nowMs().coerceAtLeast(0L)
            delay(1_000L)
        }
    }
    val ageText = endpoint.phaseAgeMs(clockMs)?.let { age ->
        when {
            age < 1_000L -> "<1 s"
            age < 60_000L -> "${age / 1_000L} s"
            else -> "${age / 60_000L} min"
        }
    } ?: "unknown"

    Column(
        modifier = Modifier.testTag("endpoint-health"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Connection health", style = MaterialTheme.typography.titleMedium)
        endpoint.profile?.let { profile ->
            Text(
                "${profile.label} - ${profile.kind.name.lowercase().replace('_', ' ')}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("endpoint-route"),
            )
            Text(
                "Control ${profile.address.host}:${profile.address.controlPort} - Preview ${profile.address.host}:${profile.address.previewPort}",
                style = MaterialTheme.typography.bodySmall,
            )
        } ?: Text(
            "No endpoint selected",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("endpoint-route"),
        )
        Text(
            "Route ${endpoint.phase.name.lowercase()} - phase age $ageText",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Control service: ${endpoint.control.name.lowercase()}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("endpoint-control-service"),
        )
        Text(
            "Preview service: ${endpoint.preview.name.lowercase()}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("endpoint-preview-service"),
        )
        endpoint.lastFailure?.let { failure ->
            Text(
                "Last failure: $failure",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("endpoint-last-failure"),
            )
        }
        onOpenConnection?.let { open ->
            TextButton(
                onClick = open,
                modifier = Modifier.testTag("open-connection"),
            ) {
                Text("Open connection")
            }
        }
    }
}

/**
 * Full Mission readiness sheet opened from the always-visible status strip.
 * It is deliberately read-only: each row explains the evidence currently
 * known and the next safe action, but does not silently start a connection,
 * capture or motion owner.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MissionDashboardPane(
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    title: String = "Mission readiness",
    onAction: ((String) -> Unit)? = null,
    compassHeadingSource: CompassHeadingSource? = null,
) {
    DisposableEffect(vm) {
        vm.startAstroWebUiStatusPolling()
        vm.startAstroWebUiGuidePolling()
        vm.startAstroWebUiAutofocusPolling()
        vm.refreshAstroWebUiFocal()
        vm.refreshAstroWebUiLimits()
        vm.startAstroWebUiSolvePolling()
        vm.startAstroWebUiRunStatePolling()
        vm.startAstroWebUiProgrammePolling()
        onDispose {
            vm.stopAstroWebUiStatusPolling()
            vm.stopAstroWebUiGuidePolling()
            vm.stopAstroWebUiAutofocusPolling()
            vm.stopAstroWebUiSolvePolling()
            vm.stopAstroWebUiRunStatePolling()
            vm.stopAstroWebUiProgrammePolling()
        }
    }
    val endpoint = vm.endpointHealth
    val captureInProgress = vm.capturePhase is AppViewModel.CapturePhase.Requested ||
        vm.capturePhase is AppViewModel.CapturePhase.Busy
    val cards = MissionReadiness.evaluate(
        MissionReadinessInput(
            control = endpoint.control,
            mountConnected = vm.mount.connected,
            cameraAttached = vm.cameraAttachment?.isAttached,
            preview = endpoint.preview,
            storageReady = vm.sdStatus?.present,
            alignmentReady = if (vm.alignmentStars > 0 || vm.lastSolveResult.value != null) true else false,
            tracking = vm.mount.tracking,
            captureInProgress = captureInProgress,
        ),
    )
    var selectedAstroRate by remember { mutableStateOf(AstroWebUiTrackRate.SIDEREAL) }
    var selectedAstroHalfSpeed by remember { mutableStateOf(false) }
    var autofocusSteps by remember { mutableStateOf("12") }
    var autofocusAdjustment by remember { mutableStateOf("1") }
    var autofocusSettle by remember { mutableStateOf("0.8") }
    var autofocusBacklash by remember { mutableStateOf("3") }
    var panoramaInterval by remember { mutableStateOf("0") }
    var focalOverride by remember { mutableStateOf("") }
    var gotoRa by remember { mutableStateOf("") }
    var gotoDec by remember { mutableStateOf("") }
    var gotoName by remember { mutableStateOf("") }
    var compassHeading by remember { mutableStateOf("") }
    var phoneCompassHeading by remember { mutableStateOf<Double?>(null) }
    var compassError by remember { mutableStateOf<String?>(null) }
    val focalOverrideMm = focalOverride.toDoubleOrNull()?.takeIf { it.isFinite() && it in 1.0..100_000.0 }
    val gotoRequest = runCatching {
        AstroWebUiGotoRequest(
            rightAscensionDeg = gotoRa.toDoubleOrNull() ?: Double.NaN,
            declinationDeg = gotoDec.toDoubleOrNull() ?: Double.NaN,
            name = gotoName,
        )
    }.getOrNull()
    val compassRequest = compassHeading.toDoubleOrNull()?.let { runCatching { AstroWebUiAlignRequest(it) }.getOrNull() }
    DisposableEffect(compassHeadingSource) {
        onDispose { compassHeadingSource?.stop() }
    }
    val autofocusParameters = runCatching {
        AstroWebUiAutofocusParameters(
            steps = autofocusSteps.toIntOrNull() ?: -1,
            adjustment = autofocusAdjustment.toIntOrNull() ?: -1,
            settleSeconds = autofocusSettle.toDoubleOrNull() ?: Double.NaN,
            backlash = autofocusBacklash.toIntOrNull() ?: -1,
        )
    }.getOrNull()
    Card(modifier = modifier.padding(8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            EndpointHealthSection(
                vm = vm,
                onOpenConnection = onAction?.let { action -> { action("connection") } },
            )
            HorizontalDivider()
            Text("Resident Astro WebUI", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = FeatureFlags.isEnabled("astroWebUiStatus"),
                    onClick = vm::refreshAstroWebUiStatus,
                ) { Text("Refresh status") }
            }
            when (val state = vm.astroWebUiStatus) {
                AstroWebUiStatusState.Idle -> Text("Status not checked.", style = MaterialTheme.typography.labelSmall)
                AstroWebUiStatusState.Loading -> Text("Reading Astro mode...", style = MaterialTheme.typography.labelSmall)
                is AstroWebUiStatusState.Failed -> Text(
                    state.reason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                is AstroWebUiStatusState.Ready -> {
                    val status = state.status
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            if (status.active) "Astro mode active" else "Astro mode inactive",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        if (status.latitudeDeg != null && status.longitudeDeg != null) {
                            Text(
                                "Site " + status.latitudeDeg + ", " + status.longitudeDeg,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        status.trackRequested?.let { requested ->
                            Text(
                                "Tracking requested: " + if (requested) "yes" else "no",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        status.rateIndex?.let { rate ->
                            Text("Rate index: " + rate, style = MaterialTheme.typography.labelSmall)
                        }
                        status.halfSpeed?.let { half ->
                            Text(
                                "Half speed: " + if (half) "yes" else "no",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        status.alignmentHeadingDeg?.let { heading ->
                            Text(
                                "Alignment heading: " + heading + " deg",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        Text(
                            "Session/request state only; mount telemetry remains authoritative.",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            HorizontalDivider()
            Text("Resident solve state", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = FeatureFlags.isEnabled("astroWebUiSolveStatus"),
                    onClick = vm::refreshAstroWebUiSolve,
                ) { Text("Refresh solve") }
            }
            when (val state = vm.astroWebUiSolve) {
                AstroWebUiSolveUiState.Idle -> Text(
                    "Solve state not checked.",
                    style = MaterialTheme.typography.labelSmall,
                )
                AstroWebUiSolveUiState.Loading -> Text(
                    "Reading solve state...",
                    style = MaterialTheme.typography.labelSmall,
                )
                is AstroWebUiSolveUiState.Failed -> Text(
                    state.reason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                is AstroWebUiSolveUiState.Ready -> {
                    val target = state.state.target
                    val targetRa = target?.targetRightAscensionDeg
                    val targetDec = target?.targetDeclinationDeg
                    val frame = state.state.lastFrame
                    val frameRa = frame?.rightAscensionDeg
                    val frameDec = frame?.declinationDeg
                    Text(
                        if (targetRa != null && targetDec != null) {
                            "Target RA " + targetRa + " deg, Dec " + targetDec + " deg"
                        } else {
                            "Target: none"
                        },
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        when {
                            frame == null -> "Last frame: none"
                            frame.solved == true && frameRa != null && frameDec != null ->
                                "Last frame: solved, RA " + frameRa + " deg, Dec " + frameDec + " deg"
                            frame.solved == false -> "Last frame: not solved" +
                                (frame.failureReason?.let { " (" + it + ")" } ?: "")
                            else -> "Last frame: observed"
                        },
                        style = MaterialTheme.typography.labelSmall,
                    )
                    frame?.matchedCount?.let {
                        Text("Matches: " + it, style = MaterialTheme.typography.labelSmall)
                    }
                    frame?.rmsPx?.let {
                        Text("RMS: " + it + " px", style = MaterialTheme.typography.labelSmall)
                    }
                    Text(
                        "Read-only file-backed solve evidence; it does not prove mount alignment or tracking.",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Text("Solve controls", style = MaterialTheme.typography.titleSmall)
            Text(
                "Solve start, cancel and apply are resident-server actions. Apply can change the mount alignment; all three remain disabled until solver, camera and recovery qualification is complete.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    enabled = FeatureFlags.isEnabled("astroWebUiSolveControls"),
                    onClick = { vm.startAstroWebUiSolve(AstroWebUiSolveMode.LATEST) },
                ) { Text("Solve latest") }
                Button(
                    enabled = FeatureFlags.isEnabled("astroWebUiSolveControls"),
                    onClick = { vm.startAstroWebUiSolve(AstroWebUiSolveMode.CAPTURE) },
                ) { Text("Solve fresh frame") }
                OutlinedButton(
                    enabled = FeatureFlags.isEnabled("astroWebUiSolveControls"),
                    onClick = vm::cancelAstroWebUiSolve,
                ) { Text("Cancel solve") }
                OutlinedButton(
                    enabled = FeatureFlags.isEnabled("astroWebUiSolveControls"),
                    onClick = vm::applyAstroWebUiSolve,
                ) { Text("Apply last solution") }
            }
            when (val control = vm.astroWebUiSolveControl) {
                AstroWebUiSolveControlUiState.Idle -> Unit
                AstroWebUiSolveControlUiState.Loading -> Text("Sending solve action…", style = MaterialTheme.typography.labelSmall)
                is AstroWebUiSolveControlUiState.Accepted -> Text(control.action, style = MaterialTheme.typography.labelSmall)
                is AstroWebUiSolveControlUiState.Failed -> Text(
                    control.reason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            HorizontalDivider()
            Text("Astro target and alignment", style = MaterialTheme.typography.titleMedium)
            Text(
                "GoTo preview validates the target without moving the head. A second explicit action starts the resident slew; compass alignment and GoTo writes remain separately gated and require physical recovery evidence.",
                style = MaterialTheme.typography.bodySmall,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedTextField(
                    value = gotoRa,
                    onValueChange = { gotoRa = it },
                    label = { Text("RA degrees 0–360") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = gotoDec,
                    onValueChange = { gotoDec = it },
                    label = { Text("Dec degrees −90–90") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = gotoName,
                    onValueChange = { gotoName = it.take(64) },
                    label = { Text("Target name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = FeatureFlags.isEnabled("astroWebUiTargetPreview") && gotoRequest != null,
                    onClick = { gotoRequest?.let(vm::previewAstroWebUiGoto) },
                ) { Text("Preview GoTo") }
                val previewMatches = (vm.astroWebUiTarget as? AstroWebUiTargetUiState.Preview)?.request == gotoRequest
                Button(
                    enabled = FeatureFlags.isEnabled("astroWebUiTargetWrites") && previewMatches,
                    onClick = { gotoRequest?.let(vm::startAstroWebUiGoto) },
                ) { Text("Confirm GoTo") }
            }
            Text(
                compassHeadingSource?.let {
                    if (it.supported) "Phone compass is available for heading rehearsal; reading it does not send alignment."
                    else "This device does not expose a usable phone compass; enter a heading manually."
                } ?: "Phone compass is unavailable on this platform; enter a heading manually.",
                style = MaterialTheme.typography.labelSmall,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    enabled = compassHeadingSource?.supported == true,
                    onClick = {
                        compassError = null
                        compassHeadingSource?.start(
                            onHeading = { phoneCompassHeading = it },
                            onUnavailable = { compassError = it },
                        )
                    },
                ) { Text("Read phone compass") }
                OutlinedButton(
                    enabled = phoneCompassHeading != null,
                    onClick = { compassHeading = phoneCompassHeading?.let { "%.2f".format(it) } ?: compassHeading },
                ) { Text("Use reading") }
                phoneCompassHeading?.let { Text("%.1f°".format(it), style = MaterialTheme.typography.labelSmall) }
            }
            compassError?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = compassHeading,
                    onValueChange = { compassHeading = it },
                    label = { Text("Compass degrees 0–360") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    enabled = FeatureFlags.isEnabled("astroWebUiTargetWrites") && compassRequest != null,
                    onClick = { compassRequest?.let(vm::alignAstroWebUiCompass) },
                ) { Text("Align compass") }
            }
            when (val target = vm.astroWebUiTarget) {
                AstroWebUiTargetUiState.Idle -> Text(
                    "No Astro target action checked.",
                    style = MaterialTheme.typography.labelSmall,
                )
                AstroWebUiTargetUiState.Loading -> Text(
                    "Previewing or sending Astro target action…",
                    style = MaterialTheme.typography.labelSmall,
                )
                is AstroWebUiTargetUiState.Preview -> Text(
                    "Preview ready for " + (target.request.name.ifBlank { "unnamed target" }) +
                        " at RA " + target.request.rightAscensionDeg + "°, Dec " + target.request.declinationDeg + "°.",
                    style = MaterialTheme.typography.labelSmall,
                )
                is AstroWebUiTargetUiState.Accepted -> Text(
                    target.action + " accepted; resident Astro status remains authoritative.",
                    style = MaterialTheme.typography.labelSmall,
                )
                is AstroWebUiTargetUiState.Failed -> Text(
                    target.reason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (!FeatureFlags.isEnabled("astroWebUiTargetPreview")) {
                Text(
                    "Astro GoTo preview is disabled by config.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!FeatureFlags.isEnabled("astroWebUiTargetWrites")) {
                Text(
                    "Compass alignment and confirmed GoTo are disabled until physical motion, collision and recovery qualification.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            Text("Solve focal length", style = MaterialTheme.typography.titleMedium)
            Text(
                "The resident solver prefers a manual override, then the last frame's EXIF focal length, then its configured default. Setting a focal override changes future solves; it does not re-solve or apply alignment.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    enabled = FeatureFlags.isEnabled("astroWebUiFocalStatus"),
                    onClick = vm::refreshAstroWebUiFocal,
                ) { Text("Refresh focal") }
                OutlinedButton(
                    enabled = FeatureFlags.isEnabled("astroWebUiFocalWrite"),
                    onClick = { vm.setAstroWebUiFocalOverride(null) },
                ) { Text("Use auto") }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedTextField(
                    value = focalOverride,
                    onValueChange = { focalOverride = it },
                    label = { Text("Manual focal mm") },
                    supportingText = { Text("1–100000 mm") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    enabled = FeatureFlags.isEnabled("astroWebUiFocalWrite") && focalOverrideMm != null,
                    onClick = { focalOverrideMm?.let(vm::setAstroWebUiFocalOverride) },
                ) { Text("Set override") }
            }
            when (val focal = vm.astroWebUiFocal) {
                AstroWebUiFocalUiState.Idle -> Text(
                    "Focal state not checked.",
                    style = MaterialTheme.typography.labelSmall,
                )
                AstroWebUiFocalUiState.Loading -> Text(
                    "Reading or changing focal state…",
                    style = MaterialTheme.typography.labelSmall,
                )
                is AstroWebUiFocalUiState.Accepted -> Text(
                    "Focal change accepted; confirming by readback.",
                    style = MaterialTheme.typography.labelSmall,
                )
                is AstroWebUiFocalUiState.Failed -> Text(
                    focal.reason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                is AstroWebUiFocalUiState.Ready -> {
                    Text(
                        "Manual override: " + (focal.state.overrideMm?.let { it.toString() + " mm" } ?: "auto"),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        "Last EXIF: " + (focal.state.exifCacheMm?.let { it.toString() + " mm" } ?: "unknown") +
                            " · config default: " + focal.state.configMm + " mm",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            if (!FeatureFlags.isEnabled("astroWebUiFocalStatus")) {
                Text(
                    "Focal readback is disabled by config.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!FeatureFlags.isEnabled("astroWebUiFocalWrite")) {
                Text(
                    "Focal writes are disabled until solve mutation and recovery are qualified.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            Text("Travel limits", style = MaterialTheme.typography.titleMedium)
            Text(
                "This readback mirrors the resident safety state. Enforced is the safe default; released permits the head to enter restricted angles and remains a deliberate physical-clearance operation.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    enabled = FeatureFlags.isEnabled("astroWebUiLimitsStatus"),
                    onClick = vm::refreshAstroWebUiLimits,
                ) { Text("Refresh limits") }
                when (val limits = vm.astroWebUiLimits) {
                    AstroWebUiLimitsUiState.Idle -> Text(
                        "Travel-limit state not checked.",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    AstroWebUiLimitsUiState.Loading -> Text(
                        "Reading travel-limit state…",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    is AstroWebUiLimitsUiState.Failed -> Text(
                        limits.reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    is AstroWebUiLimitsUiState.Ready -> Text(
                        when (limits.state.released) {
                            true -> "RELEASED — physical clearance must be watched"
                            false -> "Enforced — safe restricted-angle state"
                            null -> "Unknown — do not assume clearance or release"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (limits.state.released == true) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            if (!FeatureFlags.isEnabled("astroWebUiLimitsStatus")) {
                Text(
                    "Travel-limit readback is disabled by config.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "OpenPolaris does not expose limit release here: the Astro endpoint requires physical attestation and collision-clearance qualification.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            Text("Resident guiding", style = MaterialTheme.typography.titleMedium)
            Text(
                "Readback is source-aligned and shows the guider's own drift history. It is not proof that a correction reached the mount.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    enabled = FeatureFlags.isEnabled("astroWebUiGuideStatus"),
                    onClick = vm::refreshAstroWebUiGuide,
                ) { Text("Refresh guiding") }
                when (val guide = vm.astroWebUiGuide) {
                    AstroWebUiGuideUiState.Idle -> Text("Guide state not checked.", style = MaterialTheme.typography.labelSmall)
                    AstroWebUiGuideUiState.Loading -> Text("Reading guide state…", style = MaterialTheme.typography.labelSmall)
                    is AstroWebUiGuideUiState.Accepted -> Text(
                        "Guide change accepted; waiting for readback.",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    is AstroWebUiGuideUiState.Failed -> Text(
                        guide.reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    is AstroWebUiGuideUiState.Ready -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (guide.state.running) "Guiding active" else "Guiding off",
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Switch(
                                checked = guide.state.running,
                                enabled = FeatureFlags.isEnabled("astroWebUiGuiding"),
                                onCheckedChange = vm::setAstroWebUiGuiding,
                            )
                        }
                        Text(
                            "Threshold " + guide.state.thresholdArcsec + "″ · interval " +
                                guide.state.intervalSeconds + " s",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(
                            "Last drift " + (guide.state.lastDriftArcsec?.let { it.toString() + "″" } ?: "unknown") +
                                " · " + guide.state.points.size + " points",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        if (guide.state.log.isNotBlank()) {
                            Text("Guide log: " + guide.state.log, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            if (!FeatureFlags.isEnabled("astroWebUiGuideStatus")) {
                Text(
                    "Guide readback is disabled by config.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!FeatureFlags.isEnabled("astroWebUiGuiding")) {
                Text(
                    "Guide writes are disabled until solve anchoring, motion ownership and recovery are qualified.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            Text("Astro autofocus", style = MaterialTheme.typography.titleMedium)
            Text(
                "The resident WebUI runs a focus sweep, scores preview frames and returns to the best step. Progress is read-only; start/stop writes remain separately qualified.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = FeatureFlags.isEnabled("astroWebUiAutofocusStatus"),
                    onClick = vm::refreshAstroWebUiAutofocus,
                ) { Text("Refresh autofocus") }
                val autofocusRunning = (vm.astroWebUiAutofocus as? AstroWebUiAutofocusUiState.Ready)
                    ?.state?.status == "running"
                Button(
                    enabled = FeatureFlags.isEnabled("astroWebUiAutofocus") &&
                        !autofocusRunning &&
                        autofocusParameters != null,
                    onClick = { autofocusParameters?.let(vm::startAstroWebUiAutofocus) },
                ) { Text("Start sweep") }
                OutlinedButton(
                    enabled = FeatureFlags.isEnabled("astroWebUiAutofocus") && autofocusRunning,
                    onClick = vm::stopAstroWebUiAutofocus,
                ) { Text("Stop sweep") }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedTextField(
                    value = autofocusSteps,
                    onValueChange = { autofocusSteps = it },
                    label = { Text("Steps 3–40") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = autofocusAdjustment,
                    onValueChange = { autofocusAdjustment = it },
                    label = { Text("Adjustment 1–10") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = autofocusSettle,
                    onValueChange = { autofocusSettle = it },
                    label = { Text("Settle seconds") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = autofocusBacklash,
                    onValueChange = { autofocusBacklash = it },
                    label = { Text("Backlash 0–20") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            when (val autofocus = vm.astroWebUiAutofocus) {
                AstroWebUiAutofocusUiState.Idle -> Text("Autofocus state not checked.", style = MaterialTheme.typography.labelSmall)
                AstroWebUiAutofocusUiState.Loading -> Text("Reading or changing autofocus…", style = MaterialTheme.typography.labelSmall)
                is AstroWebUiAutofocusUiState.Accepted -> Text(
                    "Autofocus " + autofocus.action + " accepted; resident progress remains the authority.",
                    style = MaterialTheme.typography.labelSmall,
                )
                is AstroWebUiAutofocusUiState.Failed -> Text(
                    autofocus.reason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                is AstroWebUiAutofocusUiState.Ready -> {
                    val progress = autofocus.state.progress
                    Text(
                        "Status " + autofocus.state.status + " · step " + progress.step + " / " + progress.steps +
                            " · samples " + progress.samples.size,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    progress.best?.let { best ->
                        Text(
                            "Best step " + best.step + " · score " + best.score +
                                (best.hfr?.let { " · HFR " + it + " px" } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    progress.error?.let { error ->
                        Text("Autofocus: " + error, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (!FeatureFlags.isEnabled("astroWebUiAutofocusStatus")) {
                Text(
                    "Autofocus progress readback is disabled by config.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!FeatureFlags.isEnabled("astroWebUiAutofocus")) {
                Text(
                    "Autofocus start/stop is disabled until camera attachment, preview, focus direction, capture ownership and recovery are qualified.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            Text("Astro run state", style = MaterialTheme.typography.titleMedium)
            Text(
                "Automatic reads use file and log state only. Mount position is read only by the explicit button when the resident WebUI permits it.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    enabled = FeatureFlags.isEnabled("astroWebUiRunState"),
                    onClick = vm::refreshAstroWebUiRunState,
                ) { Text("Refresh run state") }
                OutlinedButton(
                    enabled = FeatureFlags.isEnabled("astroWebUiRunState"),
                    onClick = vm::readAstroWebUiMountPosition,
                ) { Text("Read mount position") }
            }
            when (val state = vm.astroWebUiRunState) {
                AstroWebUiRunStateUiState.Idle -> Text(
                    "Run state not checked.",
                    style = MaterialTheme.typography.labelSmall,
                )
                AstroWebUiRunStateUiState.Loading -> Text(
                    "Reading Astro WebUI run state...",
                    style = MaterialTheme.typography.labelSmall,
                )
                is AstroWebUiRunStateUiState.Failed -> Text(
                    state.reason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                is AstroWebUiRunStateUiState.Ready -> {
                    val run = state.state
                    Text(
                        "Status " + run.status + ", elapsed " + run.elapsedSeconds + " s",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        "Alignment " + if (run.aligned) "yes" else "no" +
                            ", tracking " + if (run.tracking) "yes" else "no",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    if (run.mountRead) {
                        Text(
                            "Mount position: alt " + run.altitudeDeg + " deg, az " + run.azimuthDeg + " deg",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    } else if (run.mountBlocked) {
                        Text(
                            "Mount position read was blocked by the resident WebUI safety check.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Text(
                            "Mount position not read; use the explicit button only when required.",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    run.solution?.let { solution ->
                        Text(
                            "Solution " + if (solution.solved == true) "solved" else "not confirmed",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    if (run.log.isNotBlank()) {
                        Text("Log: " + run.log, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            HorizontalDivider()
            Text("Astro session controls", style = MaterialTheme.typography.titleMedium)
            val sessionControlsEnabled = FeatureFlags.isEnabled("astroWebUiSessionControls")
            val observedAstroStatus =
                (vm.astroWebUiStatus as? AstroWebUiStatusState.Ready)?.status
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = sessionControlsEnabled && observedAstroStatus?.active != true,
                    onClick = vm::enterAstroWebUi,
                ) { Text("Enter Astro") }
                Button(
                    enabled = sessionControlsEnabled && observedAstroStatus?.active == true,
                    onClick = vm::leaveAstroWebUi,
                ) { Text("Leave Astro") }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedAstroRate == AstroWebUiTrackRate.SIDEREAL,
                    onClick = { selectedAstroRate = AstroWebUiTrackRate.SIDEREAL },
                    enabled = sessionControlsEnabled,
                    label = { Text("Sidereal") },
                )
                FilterChip(
                    selected = selectedAstroRate == AstroWebUiTrackRate.LUNAR,
                    onClick = { selectedAstroRate = AstroWebUiTrackRate.LUNAR },
                    enabled = sessionControlsEnabled,
                    label = { Text("Lunar") },
                )
                Switch(
                    checked = selectedAstroHalfSpeed,
                    onCheckedChange = { selectedAstroHalfSpeed = it },
                    enabled = sessionControlsEnabled,
                )
                Text("Half rate", style = MaterialTheme.typography.labelSmall)
            }
            Button(
                enabled = sessionControlsEnabled && observedAstroStatus?.active == true,
                onClick = {
                    val currentlyRequested = observedAstroStatus?.trackRequested == true
                    vm.setAstroWebUiTracking(
                        AstroWebUiTrackRequest(
                            on = !currentlyRequested,
                            rate = selectedAstroRate.index,
                            halfSpeed = selectedAstroHalfSpeed,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (observedAstroStatus?.trackRequested == true) {
                        "Stop Astro tracking"
                    } else {
                        "Start Astro tracking"
                    },
                )
            }
            when (val state = vm.astroWebUiSessionControl) {
                AstroWebUiSessionControlState.Idle -> Unit
                is AstroWebUiSessionControlState.Working -> Text(
                    "Requesting " + state.action + "...",
                    style = MaterialTheme.typography.labelSmall,
                )
                is AstroWebUiSessionControlState.Accepted -> Text(
                    "Accepted " + state.action + "; confirming by /api/astro readback.",
                    style = MaterialTheme.typography.labelSmall,
                )
                is AstroWebUiSessionControlState.Failed -> Text(
                    state.reason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (!sessionControlsEnabled) {
                Text(
                    "Enter/leave and tracking writes are source-aligned but disabled until route ownership and physical recovery are qualified.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            Text("Astro mission", style = MaterialTheme.typography.titleMedium)
            AstroMissionFlow.evaluate(
                AstroMissionInput(
                    controlReady = endpoint.control == EndpointService.REACHABLE,
                    mountConnected = vm.mount.connected,
                    cameraAttached = vm.cameraAttachment?.isAttached,
                    storageReady = vm.sdStatus?.present,
                    alignmentReady = if (vm.alignmentStars > 0 || vm.lastSolveResult.value != null) true else false,
                    tracking = vm.mount.tracking,
                    targetSelected = vm.targetSelected,
                    focusReady = null,
                    guideActive = false,
                    captureReady = vm.mount.connected && vm.cameraAttachment?.isAttached == true,
                    captureComplete = vm.lastCapturePublication != null,
                    reviewReady = vm.lastCapturePublication != null,
                ),
            ).forEach { stage ->
                val color = when (stage.state) {
                    dev.openpolaris.core.domain.AstroMissionStageState.COMPLETE -> MaterialTheme.colorScheme.primary
                    dev.openpolaris.core.domain.AstroMissionStageState.BLOCKED -> MaterialTheme.colorScheme.error
                    dev.openpolaris.core.domain.AstroMissionStageState.CURRENT -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stage.stage.label + " · " + stage.state.name.lowercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = color,
                    )
                    Text(stage.summary, style = MaterialTheme.typography.bodySmall)
                    stage.actionLabel?.let { actionLabel ->
                        if (onAction != null) {
                            TextButton(onClick = { onAction(stage.stage.actionId) }) {
                                Text("Next: " + actionLabel)
                            }
                        } else {
                            Text("Next: " + actionLabel, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            HorizontalDivider()
            Text("Observing site", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = FeatureFlags.isEnabled("astroWebUiSite"),
                    onClick = vm::refreshAstroWebUiSite,
                ) { Text("Refresh site") }
                when (val state = vm.astroWebUiSite) {
                    AstroWebUiSiteState.Idle -> Text("Site not checked.", style = MaterialTheme.typography.labelSmall)
                    AstroWebUiSiteState.Loading -> Text("Reading saved site…", style = MaterialTheme.typography.labelSmall)
                    is AstroWebUiSiteState.Ready -> Text(
                        if (state.site.havePosition) {
                            "lat=" + state.site.latitudeDeg + ", lon=" + state.site.longitudeDeg
                        } else {
                            "No saved observing site"
                        },
                        style = MaterialTheme.typography.labelSmall,
                    )
                    is AstroWebUiSiteState.Failed -> Text(state.reason, style = MaterialTheme.typography.labelSmall)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = FeatureFlags.isEnabled("astroWebUiSiteWrite") &&
                        vm.mount.connected &&
                        vm.observerLocationReady,
                    onClick = vm::saveAstroWebUiSite,
                ) { Text("Save site to Astro WebUI") }
                when (val state = vm.astroWebUiSiteWrite) {
                    AstroWebUiSiteWriteState.Idle -> Text(
                        "Resident-site saving is disabled until mutation and recovery are qualified.",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    is AstroWebUiSiteWriteState.Working -> Text(
                        "Saving lat=" + state.latitudeDeg + ", lon=" + state.longitudeDeg + "…",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    is AstroWebUiSiteWriteState.Accepted -> Text(
                        "Save accepted; confirming by readback.",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    is AstroWebUiSiteWriteState.Failed -> Text(
                        state.reason,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            HorizontalDivider()
            Text("Resident programmes", style = MaterialTheme.typography.titleMedium)
            Text(
                "Read-only status refreshes every 4 s while idle and every 1 s while a resident run is active.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = FeatureFlags.isEnabled("astroWebUiProgrammeStatus"),
                    onClick = vm::refreshAstroWebUiProgramme,
                ) { Text("Refresh programme") }
                when (val state = vm.astroWebUiProgramme) {
                    AstroWebUiProgrammeState.Idle -> Text("Programme not checked.", style = MaterialTheme.typography.labelSmall)
                    AstroWebUiProgrammeState.Loading -> Text("Reading programme…", style = MaterialTheme.typography.labelSmall)
                    is AstroWebUiProgrammeState.Failed -> Text(state.reason, style = MaterialTheme.typography.labelSmall)
                    is AstroWebUiProgrammeState.Ready -> {
                        val programme = state.programme
                        val total = programme.total
                        val remaining = programme.remaining
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                if (programme.kind == "none") "No resident programme running" else programme.kind,
                                style = MaterialTheme.typography.labelSmall,
                            )
                            if (total != null && remaining != null && total >= 0 && remaining >= 0) {
                                Text(
                                    "Taken " + (total - remaining) + " / " + total,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            if (programme.paused == true || programme.preview == true || programme.stale == true) {
                                Text(
                                    listOfNotNull(
                                        if (programme.paused == true) "paused" else null,
                                        if (programme.preview == true) "preview" else null,
                                        if (programme.stale == true) "stale" else null,
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            if (programme.kind != "none") {
                                if (FeatureFlags.isEnabled("astroWebUiProgrammeControls")) {
                                    if (programme.kind == "panorama") {
                                        val intervalSeconds = panoramaInterval.toIntOrNull()
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            OutlinedTextField(
                                                value = panoramaInterval,
                                                onValueChange = { panoramaInterval = it },
                                                label = { Text("Live interval (seconds)") },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f),
                                            )
                                            OutlinedButton(
                                                enabled = intervalSeconds != null && intervalSeconds in 0..3_600,
                                                onClick = {
                                                    intervalSeconds?.let(vm::setAstroWebUiPanoramaInterval)
                                                },
                                            ) { Text("Set interval") }
                                        }
                                        Text(
                                            "Source /api/prog/pano/interval update; status polling remains authoritative.",
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        val paused = programme.paused
                                        if (programme.kind == "panorama" && paused != null) {
                                            OutlinedButton(
                                                onClick = vm::toggleAstroWebUiPanoramaPause,
                                            ) {
                                                Text(if (paused) "Resume panorama" else "Pause panorama")
                                            }
                                        }
                                        OutlinedButton(onClick = vm::cancelAstroWebUiProgramme) {
                                            Text("Stop programme")
                                        }
                                    }
                                    Text(
                                        "Controls acknowledge a request; status polling remains the completion authority.",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                } else {
                                    Text(
                                        "Pause/resume and stop are source-aligned but disabled until programme write-route qualification.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
            AstroWebUiProgrammeComposer(vm)
            HorizontalDivider()
            AstroWebUiAstroCaptureComposer(vm)
            HorizontalDivider()
            AstroWebUiSunComposer(vm)
            HorizontalDivider()
            AstroWebUiExtendedProgrammeComposer(vm)
            HorizontalDivider()
            Text("Prepare checklist", style = MaterialTheme.typography.titleMedium)
            Text(
                "Read-only checks from the current session; nothing here starts a motor, changes limits or fires the camera.",
                style = MaterialTheme.typography.bodySmall,
            )
            AstroPrepareChecklist.evaluate(
                AstroPrepareInput(
                    controlReady = endpoint.control == EndpointService.REACHABLE,
                    mountConnected = vm.mount.connected,
                    cameraAttached = vm.cameraAttachment?.isAttached,
                    storageReady = vm.sdStatus?.present,
                    levelWithinTolerance = vm.autoLevelTilt?.withinTolerance,
                    tiltCompensationEnabled = vm.autoLevelEnabled,
                    limitsEnabled = vm.limitsEnabled,
                ),
            ).forEach { check ->
                val color = when (check.state) {
                    AstroPrepareCheckState.READY -> MaterialTheme.colorScheme.primary
                    AstroPrepareCheckState.BLOCKED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        check.id.label + " · " + check.state.name.lowercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = color,
                    )
                    Text(check.summary, style = MaterialTheme.typography.bodySmall)
                }
            }
            HorizontalDivider()
            cards.forEach { card ->
                val color = when (card.level) {
                    dev.openpolaris.core.domain.ReadinessLevel.READY -> MaterialTheme.colorScheme.primary
                    dev.openpolaris.core.domain.ReadinessLevel.BLOCKED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "${card.label} · ${card.level.name.lowercase()}",
                        style = MaterialTheme.typography.labelLarge,
                        color = color,
                    )
                    Text(card.summary, style = MaterialTheme.typography.bodySmall)
                    card.actionLabel?.let { actionLabel ->
                        if (onAction != null) {
                            TextButton(onClick = { onAction(card.id) }) {
                                Text("Next: $actionLabel")
                            }
                        } else {
                            Text("Next: $actionLabel", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AstroWebUiProgrammeComposer(vm: AppViewModel) {
    var kind by remember { mutableStateOf(AstroWebUiProgrammeStartResult.Kind.LAPSE) }
    var armed by remember { mutableStateOf(false) }
    var lapseInterval by remember { mutableStateOf("5") }
    var lapseShots by remember { mutableStateOf("10") }
    var lapseBulb by remember { mutableStateOf("0") }
    var lapseFps by remember { mutableStateOf("24") }
    var panoColumns by remember { mutableStateOf("2") }
    var panoRows by remember { mutableStateOf("2") }
    var panoHStep by remember { mutableStateOf("30") }
    var panoVStep by remember { mutableStateOf("10") }
    var panoStartDirection by remember { mutableStateOf("1") }
    var panoPerSpot by remember { mutableStateOf("1") }
    var panoInterval by remember { mutableStateOf("0") }
    var hdrSpread by remember { mutableStateOf("3") }
    var focusShots by remember { mutableStateOf("14") }
    var imageStabilization by remember { mutableStateOf(false) }

    val enabled = FeatureFlags.isEnabled("astroWebUiProgrammeStarts")
    val startState = vm.astroWebUiProgrammeStartState
    val matchingPreview = (startState as? AstroWebUiProgrammeStartState.Preview)?.result?.kind == kind
    val setField: (String, (String) -> Unit) -> Unit = { value, setter ->
        setter(value)
        armed = false
    }

    Text("Start resident programme", style = MaterialTheme.typography.titleMedium)
    Text(
        "Lapse, panorama, HDR and focus use the resident Astro WebUI. Preview and validation never start a run; a confirmed start may move the mount or fire the shutter.",
        style = MaterialTheme.typography.bodySmall,
    )
    if (!enabled) {
        Text(
            "Programme starts are disabled until named-head write, recovery and physical capture evidence are qualified.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        return
    }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AstroWebUiProgrammeStartResult.Kind.entries.forEach { option ->
            FilterChip(
                selected = kind == option,
                onClick = { kind = option; armed = false },
                label = { Text(option.name.lowercase().replace('_', ' ')) },
            )
        }
    }

    when (kind) {
        AstroWebUiProgrammeStartResult.Kind.LAPSE -> {
            OutlinedTextField(value = lapseInterval, onValueChange = { setField(it) { lapseInterval = it } }, label = { Text("Interval (seconds)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = lapseShots, onValueChange = { setField(it) { lapseShots = it } }, label = { Text("Shots (-1 for unlimited)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = lapseBulb, onValueChange = { setField(it) { lapseBulb = it } }, label = { Text("Bulb seconds") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = lapseFps, onValueChange = { setField(it) { lapseFps = it } }, label = { Text("Playback FPS") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        AstroWebUiProgrammeStartResult.Kind.PANORAMA -> {
            OutlinedTextField(value = panoColumns, onValueChange = { setField(it) { panoColumns = it } }, label = { Text("Columns") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = panoRows, onValueChange = { setField(it) { panoRows = it } }, label = { Text("Rows") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = panoHStep, onValueChange = { setField(it) { panoHStep = it } }, label = { Text("Horizontal step (degrees)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = panoVStep, onValueChange = { setField(it) { panoVStep = it } }, label = { Text("Vertical step (degrees)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = panoStartDirection, onValueChange = { setField(it) { panoStartDirection = it } }, label = { Text("Start direction (0-4)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = panoPerSpot, onValueChange = { setField(it) { panoPerSpot = it } }, label = { Text("Frames per position") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = panoInterval, onValueChange = { setField(it) { panoInterval = it } }, label = { Text("Position interval (seconds)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        AstroWebUiProgrammeStartResult.Kind.HDR -> {
            OutlinedTextField(value = hdrSpread, onValueChange = { setField(it) { hdrSpread = it } }, label = { Text("Shutter spread (steps)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        AstroWebUiProgrammeStartResult.Kind.FOCUS -> {
            OutlinedTextField(value = focusShots, onValueChange = { setField(it) { focusShots = it } }, label = { Text("Focus shots") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(
            checked = imageStabilization,
            onCheckedChange = { imageStabilization = it; armed = false },
        )
        Text("Image stabilisation", style = MaterialTheme.typography.bodySmall)
    }

    val actionLabel = when {
        kind == AstroWebUiProgrammeStartResult.Kind.LAPSE && armed -> "Tap again to start lapse - this fires the shutter"
        matchingPreview && armed -> "Start " + kind.name.lowercase().replace('_', ' ') + " - this may move/fire"
        kind == AstroWebUiProgrammeStartResult.Kind.LAPSE -> "Arm lapse start"
        kind == AstroWebUiProgrammeStartResult.Kind.FOCUS -> "Validate focus stack"
        else -> "Preview " + kind.name.lowercase().replace('_', ' ')
    }
    Button(
        onClick = {
            when (kind) {
                AstroWebUiProgrammeStartResult.Kind.LAPSE -> {
                    if (armed) {
                        vm.startAstroWebUiLapse(
                            AstroWebUiLapseParameters(
                                intervalSeconds = lapseInterval.toDoubleOrNull() ?: Double.NaN,
                                shots = lapseShots.toIntOrNull() ?: Int.MIN_VALUE,
                                bulbSeconds = lapseBulb.toDoubleOrNull() ?: Double.NaN,
                                fps = lapseFps.toIntOrNull() ?: Int.MIN_VALUE,
                            ),
                        )
                        armed = false
                    } else {
                        armed = true
                    }
                }
                AstroWebUiProgrammeStartResult.Kind.PANORAMA -> {
                    val parameters = AstroWebUiPanoramaParameters(
                        columns = panoColumns.toIntOrNull() ?: Int.MIN_VALUE,
                        rows = panoRows.toIntOrNull() ?: Int.MIN_VALUE,
                        horizontalStepDegrees = panoHStep.toDoubleOrNull() ?: Double.NaN,
                        verticalStepDegrees = panoVStep.toDoubleOrNull() ?: Double.NaN,
                        startDirection = panoStartDirection.toIntOrNull() ?: Int.MIN_VALUE,
                        framesPerPosition = panoPerSpot.toIntOrNull() ?: Int.MIN_VALUE,
                        intervalSeconds = panoInterval.toIntOrNull() ?: Int.MIN_VALUE,
                        imageStabilization = imageStabilization,
                    )
                    if (matchingPreview && armed) {
                        vm.startAstroWebUiPanorama(parameters)
                        armed = false
                    } else {
                        vm.previewAstroWebUiPanorama(parameters)
                        armed = true
                    }
                }
                AstroWebUiProgrammeStartResult.Kind.HDR -> {
                    val parameters = AstroWebUiHdrParameters(
                        spread = hdrSpread.toIntOrNull() ?: Int.MIN_VALUE,
                        imageStabilization = imageStabilization,
                    )
                    if (matchingPreview && armed) {
                        vm.startAstroWebUiHdr(parameters)
                        armed = false
                    } else {
                        vm.previewAstroWebUiHdr(parameters)
                        armed = true
                    }
                }
                AstroWebUiProgrammeStartResult.Kind.FOCUS -> {
                    val parameters = AstroWebUiFocusParameters(
                        shots = focusShots.toIntOrNull() ?: Int.MIN_VALUE,
                        imageStabilization = imageStabilization,
                    )
                    if (matchingPreview && armed) {
                        vm.startAstroWebUiFocus(parameters)
                        armed = false
                    } else {
                        vm.validateAstroWebUiFocus(parameters)
                        armed = true
                    }
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(actionLabel)
    }

    when (val state = startState) {
        AstroWebUiProgrammeStartState.Idle -> Unit
        AstroWebUiProgrammeStartState.Loading -> Text("Sending programme request...", style = MaterialTheme.typography.labelSmall)
        is AstroWebUiProgrammeStartState.Preview -> {
            val result = state.result
            Text(
                "Preview or validation ready" +
                    (result.shots?.let { " - " + it + " frames" } ?: "") +
                    if (result.inferred) " - panorama frame inferred" else "",
                style = MaterialTheme.typography.labelSmall,
            )
            result.frame?.let { frame ->
                Text("Returned frame: " + frame, style = MaterialTheme.typography.labelSmall)
            }
        }
        is AstroWebUiProgrammeStartState.Accepted -> Text(
            "Start accepted; follow Resident programmes for status and completion.",
            style = MaterialTheme.typography.labelSmall,
        )
        is AstroWebUiProgrammeStartState.Failed -> Text(
            state.reason,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun AstroWebUiAstroCaptureComposer(vm: AppViewModel) {
    var interval by remember { mutableStateOf("8") }
    var shots by remember { mutableStateOf("") }
    var bulb by remember { mutableStateOf("0") }
    var armed by remember { mutableStateOf(false) }

    LaunchedEffect(armed) {
        if (armed) {
            delay(4_000L)
            armed = false
        }
    }

    val enabled = FeatureFlags.isEnabled("astroWebUiAstroCapture")
    val parameters = runCatching {
        AstroWebUiAstroCaptureParameters(
            intervalSeconds = interval.toIntOrNull() ?: 0,
            shots = if (shots.isBlank()) -1 else shots.toIntOrNull() ?: 0,
            bulbSeconds = bulb.toDoubleOrNull() ?: Double.NaN,
        )
    }.getOrNull()

    Text("In-head Astro capture", style = MaterialTheme.typography.titleMedium)
    Text(
        "Source Astro WebUI /api/astro/capture starts a resident 272 run. The server's /api/prog status remains authoritative after the HTTP acknowledgement; the source also warns that externally triggered Astro capture can be unreliable.",
        style = MaterialTheme.typography.bodySmall,
    )
    if (!enabled) {
        Text(
            "Disabled until route, mode, recovery and physical shutter/mount qualification are complete. This action can move the mount and fire the shutter.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("astro-webui-astro-capture"),
        )
        return
    }

    OutlinedTextField(
        value = interval,
        onValueChange = { interval = it; armed = false },
        label = { Text("Interval (seconds)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = shots,
        onValueChange = { shots = it; armed = false },
        label = { Text("Shots (blank = unlimited)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = bulb,
        onValueChange = { bulb = it; armed = false },
        label = { Text("Bulb seconds (0 = camera default)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    if (parameters == null) {
        Text(
            "Enter an interval of 1–3600 seconds, up to 5000 shots (blank is unlimited), and a bulb exposure shorter than the interval.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    val state = vm.astroWebUiAstroCapture
    Button(
        enabled = parameters != null,
        onClick = {
            if (armed) {
                parameters?.let(vm::startAstroWebUiAstroCapture)
                armed = false
            } else {
                armed = true
            }
        },
        modifier = Modifier.fillMaxWidth().testTag("astro-webui-astro-capture-start"),
    ) {
        Text(
            when {
                armed -> "Tap again to start in-head Astro capture"
                else -> "Arm in-head Astro capture"
            },
        )
    }
    if (armed) {
        Text(
            "Armed: the next tap sends the source capture request and may move/fire. Check camera mode and framing first.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    when (state) {
        AstroWebUiAstroCaptureState.Idle -> Unit
        AstroWebUiAstroCaptureState.Loading -> Text(
            "Sending in-head capture request…",
            style = MaterialTheme.typography.labelSmall,
        )
        is AstroWebUiAstroCaptureState.Accepted -> Text(
            "Accepted at HTTP level; resident programme status remains authoritative.",
            style = MaterialTheme.typography.labelSmall,
        )
        is AstroWebUiAstroCaptureState.Failed -> Text(
            state.reason,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun AstroWebUiSunComposer(vm: AppViewModel) {
    var sunset by remember { mutableStateOf(false) }
    var interval by remember { mutableStateOf("10") }
    var start by remember { mutableStateOf((SystemMillis.now() / 1000L).toString()) }
    var end by remember { mutableStateOf(((SystemMillis.now() / 1000L) + 1800L).toString()) }
    var armed by remember { mutableStateOf(false) }

    val enabled = FeatureFlags.isEnabled("astroWebUiProgrammeStarts")
    val state = vm.astroWebUiSunState
    val previewReady = state is AstroWebUiSunState.Preview

    Text("Scheduled solar lapse", style = MaterialTheme.typography.titleMedium)
    Text(
        "Preview a sunrise or sunset window using the client clock, then explicitly start it. The resident programme status remains the completion authority.",
        style = MaterialTheme.typography.bodySmall,
    )
    if (!enabled) {
        Text(
            "Solar programme starts are disabled until named-head write, recovery and physical capture evidence are qualified.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        return
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FilterChip(
            selected = !sunset,
            onClick = { sunset = false; armed = false },
            label = { Text("Sunrise") },
        )
        FilterChip(
            selected = sunset,
            onClick = { sunset = true; armed = false },
            label = { Text("Sunset") },
        )
        OutlinedButton(
            onClick = {
                val now = SystemMillis.now() / 1000L
                start = now.toString()
                end = (now + 1800L).toString()
                armed = false
            },
        ) { Text("Now + 30 min") }
    }
    OutlinedTextField(
        value = interval,
        onValueChange = { interval = it; armed = false },
        label = { Text("Interval (seconds)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = start,
        onValueChange = { start = it; armed = false },
        label = { Text("Start Unix time") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = end,
        onValueChange = { end = it; armed = false },
        label = { Text("End Unix time") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    val parameters = runCatching {
        AstroWebUiSunParameters(
            sunset = sunset,
            intervalSeconds = interval.toDoubleOrNull() ?: Double.NaN,
            startEpochSeconds = start.toLongOrNull() ?: Long.MIN_VALUE,
            endEpochSeconds = end.toLongOrNull() ?: Long.MIN_VALUE,
            nowEpochSeconds = SystemMillis.now() / 1000L,
        )
    }.getOrNull()
    if (parameters == null) {
        Text(
            "Enter a positive interval and an end time after the start time.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Button(
        enabled = parameters != null,
        onClick = {
            if (parameters != null) {
                if (previewReady && armed) {
                    vm.startAstroWebUiSun(parameters)
                    armed = false
                } else {
                    vm.previewAstroWebUiSun(parameters)
                    armed = true
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (previewReady && armed) "Start solar programme - this may move/fire" else "Preview solar programme")
    }

    when (state) {
        AstroWebUiSunState.Idle -> Unit
        AstroWebUiSunState.Loading -> Text("Sending solar programme request…", style = MaterialTheme.typography.labelSmall)
        is AstroWebUiSunState.Preview -> Text(
            "Preview ready: " + state.frame,
            style = MaterialTheme.typography.labelSmall,
        )
        AstroWebUiSunState.Accepted -> Text(
            "Start accepted; follow Resident programmes for progress and completion.",
            style = MaterialTheme.typography.labelSmall,
        )
        is AstroWebUiSunState.Failed -> Text(
            state.reason,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AstroWebUiExtendedProgrammeComposer(vm: AppViewModel) {
    var kind by remember { mutableStateOf("PLC") }
    var armed by remember { mutableStateOf(false) }
    var plcKeyTime by remember { mutableStateOf("0") }
    var plcInterval by remember { mutableStateOf("5") }
    var plcShots by remember { mutableStateOf("-1") }
    var plcHold by remember { mutableStateOf(false) }
    var pathFrames by remember { mutableStateOf("10") }
    var pathInterval by remember { mutableStateOf("4") }
    var grailNodeMinutes by remember { mutableStateOf("60") }
    var grailNodeEv by remember { mutableStateOf("1") }
    var grailEnabled by remember { mutableStateOf(true) }

    val enabled = FeatureFlags.isEnabled("astroWebUiProgrammeStarts")
    val state = vm.astroWebUiMotionProgrammeState
    val preview = state as? AstroWebUiMotionProgrammeState.Preview
    val plc = runCatching {
        AstroWebUiPlcParameters(
            photoIntervalSeconds = plcInterval.toDoubleOrNull() ?: Double.NaN,
            photoCount = plcShots.toIntOrNull() ?: Int.MIN_VALUE,
            hold = plcHold,
        )
    }.getOrNull()
    val path = runCatching {
        AstroWebUiPathLapseParameters(pathInterval.toDoubleOrNull() ?: Double.NaN)
    }.getOrNull()
    val grail = runCatching {
        AstroWebUiGrailParameters(
            enabled = grailEnabled,
            curve = listOf(
                AstroWebUiGrailNode(0, 0.0),
                AstroWebUiGrailNode(
                    grailNodeMinutes.toIntOrNull() ?: -1,
                    grailNodeEv.toDoubleOrNull() ?: Double.NaN,
                ),
            ),
            priority = listOf(0, 1, 2),
        )
    }.getOrNull()
    val previewMatches = preview != null && (
        (kind == "PLC" && preview.action == "PLC preview") ||
            (kind == "Path-lapse" && preview.action == "path-lapse preview") ||
            (kind == "Holy Grail" && preview.action == "Holy Grail preview")
        )

    Text("Extended Astro programmes", style = MaterialTheme.typography.titleMedium)
    Text(
        "PLC and path-lapse capture live head poses as server-side waypoints. Holy Grail uploads a day-to-night exposure curve. Preview is non-committing; starts and uploads remain qualification-gated.",
        style = MaterialTheme.typography.bodySmall,
    )
    if (!enabled) {
        Text(
            "PLC, path-lapse and Holy Grail writes are disabled until named-head route, recovery and physical evidence are qualified.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        return
    }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("PLC", "Path-lapse", "Holy Grail").forEach { option ->
            FilterChip(
                selected = kind == option,
                onClick = { kind = option; armed = false },
                label = { Text(option) },
            )
        }
    }

    when (kind) {
        "PLC" -> {
            OutlinedTextField(
                value = plcKeyTime,
                onValueChange = { plcKeyTime = it },
                label = { Text("Next keyframe time (seconds)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = plcKeyTime.toDoubleOrNull()?.let { it >= 0.0 } == true,
                    onClick = {
                        plcKeyTime.toDoubleOrNull()?.let { vm.addAstroWebUiPlcKey(AstroWebUiPlcKeyRequest(it)) }
                    },
                ) { Text("Capture live pose") }
                OutlinedButton(onClick = { vm.clearAstroWebUiPlcKeys() }) { Text("Clear keys") }
            }
            OutlinedTextField(value = plcInterval, onValueChange = { plcInterval = it; armed = false }, label = { Text("Photo interval (seconds)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = plcShots, onValueChange = { plcShots = it; armed = false }, label = { Text("Photo count (-1 unlimited)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = plcHold, onCheckedChange = { plcHold = it; armed = false })
                Text("Hold final pose", style = MaterialTheme.typography.bodySmall)
            }
            Button(
                enabled = plc != null,
                onClick = {
                    plc?.let {
                        if (previewMatches && armed) {
                            vm.startAstroWebUiPlc(it)
                            armed = false
                        } else {
                            vm.previewAstroWebUiPlc(it)
                            armed = true
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (previewMatches && armed) "Run PLC - this may move/fire" else "Preview PLC") }
        }
        "Path-lapse" -> {
            OutlinedTextField(value = pathFrames, onValueChange = { pathFrames = it }, label = { Text("Frames on next leg (-1 unlimited)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = pathFrames.toIntOrNull()?.let { it == -1 || it > 0 } == true,
                    onClick = { pathFrames.toIntOrNull()?.let { vm.addAstroWebUiPathWaypoint(AstroWebUiPathWaypointRequest(it)) } },
                ) { Text("Capture waypoint") }
                OutlinedButton(onClick = { vm.clearAstroWebUiPathWaypoints() }) { Text("Clear waypoints") }
            }
            OutlinedTextField(value = pathInterval, onValueChange = { pathInterval = it; armed = false }, label = { Text("Interval (seconds)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(
                enabled = path != null,
                onClick = {
                    path?.let {
                        if (previewMatches && armed) {
                            vm.startAstroWebUiPathLapse(it)
                            armed = false
                        } else {
                            vm.previewAstroWebUiPathLapse(it)
                            armed = true
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (previewMatches && armed) "Run path-lapse - this may move/fire" else "Preview path-lapse") }
        }
        else -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = grailEnabled, onCheckedChange = { grailEnabled = it; armed = false })
                Text("Enable Holy Grail ramp", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(value = grailNodeMinutes, onValueChange = { grailNodeMinutes = it; armed = false }, label = { Text("Second curve node (minutes)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = grailNodeEv, onValueChange = { grailNodeEv = it; armed = false }, label = { Text("Second curve node (EV)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.refreshAstroWebUiGrailStatus() }) { Text("Refresh ramp status") }
                OutlinedButton(onClick = { vm.disableAstroWebUiGrail() }) { Text("Disable ramp") }
            }
            Button(
                enabled = grail != null,
                onClick = {
                    grail?.let {
                        if (previewMatches && armed) {
                            vm.applyAstroWebUiGrail(it)
                            armed = false
                        } else {
                            vm.previewAstroWebUiGrail(it)
                            armed = true
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (previewMatches && armed) "Upload Holy Grail ramp" else "Preview Holy Grail ramp") }
        }
    }

    when (state) {
        AstroWebUiMotionProgrammeState.Idle -> Unit
        is AstroWebUiMotionProgrammeState.Loading -> Text(state.action + "…", style = MaterialTheme.typography.labelSmall)
        is AstroWebUiMotionProgrammeState.Count -> Text(state.action + " accepted; count=" + state.count, style = MaterialTheme.typography.labelSmall)
        is AstroWebUiMotionProgrammeState.Preview -> Text(
            state.action + " ready" + (state.count?.let { " · count=" + it } ?: "") +
                (if (state.inferred) " · inferred frame" else "") + " · " + state.frame,
            style = MaterialTheme.typography.labelSmall,
        )
        is AstroWebUiMotionProgrammeState.Accepted -> Text(
            state.action + " accepted; resident status remains the completion authority.",
            style = MaterialTheme.typography.labelSmall,
        )
        is AstroWebUiMotionProgrammeState.Disabled -> Text("Holy Grail enabled=" + state.enabled, style = MaterialTheme.typography.labelSmall)
        is AstroWebUiMotionProgrammeState.Status -> Text(
            "Holy Grail enabled=" + state.enabled + " · brightness=" + (state.brightness ?: "unavailable"),
            style = MaterialTheme.typography.labelSmall,
        )
        is AstroWebUiMotionProgrammeState.Failed -> Text(state.reason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
    }
}

/** Position + jog pane. */
@Composable
fun JogPane(vm: AppViewModel, modifier: Modifier = Modifier) {
    val p = vm.position
    val fastJogState by vm.fastJogState.collectAsState()
    Card(modifier = modifier.padding(8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Position", style = MaterialTheme.typography.titleMedium)
            Text("Az/Yaw: ${p?.yaw?.toDouble()?.format2() ?: "—"}°   Alt/Pitch: ${p?.pitch?.toDouble()?.format2() ?: "—"}°")
            Text(
                "Fast jog: hold a direction and release to stop.",
                style = MaterialTheme.typography.bodySmall,
            )
            when (fastJogState) {
                dev.openpolaris.core.domain.FastJogState.Idle -> Unit
                is dev.openpolaris.core.domain.FastJogState.Armed,
                is dev.openpolaris.core.domain.FastJogState.Holding -> Text(
                    "Fast jog active; release the direction to stop.",
                    style = MaterialTheme.typography.labelSmall,
                )
                is dev.openpolaris.core.domain.FastJogState.Faulted -> {
                    val fault = fastJogState as dev.openpolaris.core.domain.FastJogState.Faulted
                    Text(
                        "Fast jog fault: " + fault.reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(
                        onClick = vm::recoverFastJogAfterReconnect,
                        enabled = vm.mount.connected,
                    ) {
                        Text("Recover after confirming stopped")
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            JogPad(vm, Codes.GIMBAL_HADJ_SPEED, Codes.GIMBAL_HADJ_ANGLE, Codes.GIMBAL_VADJ_SPEED, Codes.GIMBAL_VADJ_ANGLE)
        }
    }
}

/**
 * Slow-motion workspace.
 *
 * This is deliberately a truthful, reviewable surface while the target
 * firmware wire contract is still unqualified. It explains the physical
 * axes, speed ladder and hold/stop semantics without sending 532/533/534 or
 * any alternative rate frame. The live controls must not appear until the
 * exact firmware identity, signs, acknowledgement, watchdog and physical
 * stop evidence are recorded in the capability registry.
 */
@Composable
fun SlowMotionPane(vm: AppViewModel, modifier: Modifier = Modifier) {
    val gateOpen = dev.openpolaris.core.config.FeatureFlags.isEnabled("slowMotionControl")
    // This hard gate is intentionally not configuration-controlled: a flag
    // cannot imply that the target firmware transport is qualified.
    // Keep the source-level hard gate until a named physical qualification record is installed.
    val liveTransportQualified = false
    val capabilityRecord = SlowMotionCapabilities.current
    val liveControlsEnabled = gateOpen && liveTransportQualified && capabilityRecord.liveReady && vm.mount.connected
    val demoControlsEnabled = vm.demoMode
    val controlsEnabled = liveControlsEnabled || demoControlsEnabled
    val connectionText = if (vm.mount.connected) "Mount connected" else "Connect to a mount first"
    val motionState by vm.motionState.collectAsState()
    val motionOwnerText = when (val state = motionState) {
        dev.openpolaris.core.domain.MotionState.Idle -> "Motion owner: idle"
        is dev.openpolaris.core.domain.MotionState.Owned ->
            "Motion owner: " + state.lease.owner.name.lowercase().replace('_', ' ')
        is dev.openpolaris.core.domain.MotionState.Faulted ->
            "Motion owner blocked: " + state.reason
    }
    var selectedAxis by remember { mutableStateOf("M1 · Pan / yaw") }
    var selectedLevel by remember { mutableStateOf(1) }
    var showShortcuts by remember { mutableStateOf(false) }
    Card(modifier = modifier.padding(8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Slow motion", style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    demoControlsEnabled -> "Demo simulation is active: planner and scheduler transitions are shown without sending a hardware command."
                    !gateOpen -> "Slow-motion controls are unavailable until the firmware and physical stop contract are qualified."
                    !liveTransportQualified -> "Host planner is implemented, but no firmware-bound slow-motion transport is enabled in this build."
                    !vm.mount.connected -> "Connect to a mount before using slow motion."
                    else -> "Qualified slow-motion controls are ready for this build."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (demoControlsEnabled) "Motion state: Demo ${vm.slowMotionDemoState.label}" else "Motion state: Unavailable",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(if (demoControlsEnabled) "No physical transport · ${vm.slowMotionDemoState.lastEvents.size} logical transitions" else connectionText, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                motionOwnerText,
                style = MaterialTheme.typography.bodySmall,
                color = if (motionState is dev.openpolaris.core.domain.MotionState.Faulted) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.testTag("motion-owner-state"),
            )
            if (!liveTransportQualified) {
                Text(
                    "Live qualification blockers: " +
                        capabilityRecord.blockers.joinToString(", ") + ".",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (demoControlsEnabled && vm.slowMotionDemoState.lastEvents.isNotEmpty()) {
                Text("Logical scheduler trace", style = MaterialTheme.typography.labelSmall)
                vm.slowMotionDemoState.lastEvents.takeLast(4).forEach { event ->
                    val point = event.point?.let { "key ${it.key} · state ${it.state} · level ${it.level}" } ?: "explicit stop"
                    Text("${event.axis.name}: ${event.kind.name} · $point", style = MaterialTheme.typography.labelSmall)
                }
            }

            Text("Motion dock", style = MaterialTheme.typography.labelLarge)
            Text(
                "Select an axis, then press and hold a direction in Demo mode; release stops the logical session. A qualified live version will use the same dead-man contract. Axis signs remain Direction A/B until a firmware-bound calibration proves left/right, up/down or celestial meaning.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("M1 · Pan / yaw", "M2 · Tilt / pitch", "M3 · Rotation / roll").forEach { axis ->
                    FilterChip(
                        selected = selectedAxis == axis,
                        onClick = { selectedAxis = axis },
                        enabled = controlsEnabled,
                        label = { Text(axis.substringBefore(" ·")) },
                    )
                }
            }
            Text("Selected axis: " + selectedAxis, style = MaterialTheme.typography.bodySmall)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SlowDemoHoldButton(
                    label = "Direction A",
                    enabled = controlsEnabled && demoControlsEnabled,
                    modifier = Modifier.weight(1f),
                    onPress = { vm.demoSlowMotionStart(slowAxis(selectedAxis), 1, selectedLevel) },
                    onRelease = vm::demoSlowMotionStop,
                )
                Button(
                    onClick = vm::emergencyStopMotion,
                    enabled = motionState is dev.openpolaris.core.domain.MotionState.Owned,
                    modifier = Modifier.weight(1f),
                ) { Text("STOP ALL") }
                SlowDemoHoldButton(
                    label = "Direction B",
                    enabled = controlsEnabled && demoControlsEnabled,
                    modifier = Modifier.weight(1f),
                    onPress = { vm.demoSlowMotionStart(slowAxis(selectedAxis), -1, selectedLevel) },
                    onRelease = vm::demoSlowMotionStop,
                )
            }
            Text(
                "Pointer capture, dead-man renewal and stop ordering belong to the shared motion owner; the UI asserts the hold and never creates a repeat timer.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { showShortcuts = !showShortcuts },
                modifier = Modifier.testTag("slow-motion-shortcuts"),
            ) {
                Text(if (showShortcuts) "Hide shortcuts" else "Keyboard shortcuts")
            }
            if (showShortcuts) {
                Text(
                    "Keyboard: Tab to a direction, hold Enter or Space to move, and release the key to stop. Focus leaving a direction also stops it. Tab to STOP ALL for the emergency boundary. These shortcuts exercise the same owner as touch; they never create a second repeat loop.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("slow-motion-shortcuts-help"),
                )
            }

            Text("Speed rail", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..5).forEach { level ->
                    val levelLabel = when (level) {
                        1 -> level.toString() + " Precision"
                        5 -> level.toString() + " Fast"
                        else -> level.toString()
                    }
                    FilterChip(
                        selected = selectedLevel == level,
                        onClick = { selectedLevel = level },
                        enabled = controlsEnabled,
                        label = { Text(levelLabel) },
                    )
                }
            }
            Text(
                "Level " + selectedLevel + " is a calibration step, not a degrees-per-second promise. The planner selects adjacent measured points and time-shares them; state 0 is STOP, never a running point.",
                style = MaterialTheme.typography.bodySmall,
            )
            val selectedCalibration = SlowMotionCalibration.REFERENCE.levels.getValue(selectedLevel)
            Card(
                modifier = Modifier.fillMaxWidth().testTag("slow-motion-calibration"),
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Host reference calibration", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "Level $selectedLevel: state 1 ${selectedCalibration.stateOneRate}°/s · state 2 ${selectedCalibration.stateTwoRate}°/s",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Reference values are for planner rehearsal only. They are not a live target rate and must be replaced by exact-firmware measurements before hardware use.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text("Rotation rocker · M3", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SlowDemoHoldButton(
                    label = "A · rotate",
                    enabled = controlsEnabled && demoControlsEnabled,
                    modifier = Modifier.weight(1f),
                    onPress = { vm.demoSlowMotionStart(dev.openpolaris.core.domain.RateAxis.M3_ROTATION, 1, selectedLevel) },
                    onRelease = vm::demoSlowMotionStop,
                )
                SlowDemoHoldButton(
                    label = "B · rotate",
                    enabled = controlsEnabled && demoControlsEnabled,
                    modifier = Modifier.weight(1f),
                    onPress = { vm.demoSlowMotionStart(dev.openpolaris.core.domain.RateAxis.M3_ROTATION, -1, selectedLevel) },
                    onRelease = vm::demoSlowMotionStop,
                )
            }

            OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Text("Fine correction — unavailable until tracking and rate transport are qualified")
            }
            Text(
                "Release, cancel, backgrounding, screen lock, link loss, mode change and emergency stop must relinquish the motion lease. A failed or ambiguous stop blocks motion until explicit recovery.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                if (demoControlsEnabled) {
                    "Demo mode records logical planner/scheduler transitions only; no 532/533/534 or alternative AC 4A frame is emitted."
                } else {
                    "Current qualification: planner and scheduler tests only; no 532/533/534 or alternative AC 4A transport is connected."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun slowAxis(label: String): dev.openpolaris.core.domain.RateAxis = when (label.substringBefore(" ·")) {
    "M2" -> dev.openpolaris.core.domain.RateAxis.M2_TILT
    "M3" -> dev.openpolaris.core.domain.RateAxis.M3_ROTATION
    else -> dev.openpolaris.core.domain.RateAxis.M1_PAN
}

/** Demo-only dead-man control: press starts logical motion and release stops it. */
@Composable
private fun SlowDemoHoldButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onPress: () -> Unit,
    onRelease: () -> Unit,
) {
    var suppressNextClick by remember { mutableStateOf(false) }
    var keyHeld by remember { mutableStateOf(false) }
    var holdActive by remember { mutableStateOf(false) }
    val inputModifier = modifier
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                suppressNextClick = true
                holdActive = true
                onPress()
                try {
                    while (awaitPointerEvent().changes.any { it.pressed }) Unit
                } finally {
                    if (holdActive) {
                        holdActive = false
                        onRelease()
                    }
                }
            }
        }
        .onKeyEvent { event ->
            if (!enabled || (event.key != Key.Enter && event.key != Key.Spacebar)) return@onKeyEvent false
            when (event.type) {
                KeyEventType.KeyDown -> {
                    if (!keyHeld) {
                        keyHeld = true
                        holdActive = true
                        onPress()
                    }
                }
                KeyEventType.KeyUp -> {
                    if (keyHeld) {
                        keyHeld = false
                        if (holdActive) {
                            holdActive = false
                            onRelease()
                        }
                    }
                }
            }
            true
        }
        .onFocusChanged { focusState ->
            if (!focusState.isFocused) {
                keyHeld = false
                if (holdActive) {
                    holdActive = false
                    onRelease()
                }
            }
        }
        .semantics {
            contentDescription = "$label. Hold to move; release to stop."
            stateDescription = if (holdActive) "Held" else "Released"
            role = Role.Button
        }
        .focusable(enabled)
    Button(
        onClick = {
            if (suppressNextClick) suppressNextClick = false
            else {
                onPress()
                onRelease()
            }
        },
        enabled = enabled,
        modifier = inputModifier,
    ) {
        Text(label)
    }
}

@Composable
private fun JogPad(vm: AppViewModel, yawUp: Int, yawDown: Int, pitchUp: Int, pitchDown: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        JogButton("▲", pitchUp, vm)
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            JogButton("◀", yawDown, vm)
            JogButton("▶", yawUp, vm)
        }
        JogButton("▼", pitchDown, vm)
    }
}

@Composable
private fun JogButton(label: String, code: Int, vm: AppViewModel) {
    SlowDemoHoldButton(
        label = label,
        enabled = vm.mount.connected && FeatureFlags.isEnabled("basicControls"),
        onPress = { vm.startFastJog(code) },
        onRelease = vm::stopFastJog,
    )
}

/** Goto pane: az/alt or RA/Dec entry, observer location, slew/cancel, alignment, auto-level. */
@Composable
fun GotoPane(vm: AppViewModel, modifier: Modifier = Modifier) {
    var alignmentQuery by remember { mutableStateOf("") }
    Card(modifier = modifier.padding(8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Slew", style = MaterialTheme.typography.titleMedium)

            // v0.1.12: stack the Lat/Lng fields vertically so the
            // OutlinedTextField label doesn't get rotated to vertical
            // text when the column is too narrow for the label string
            // (#47). See .copilot/agent-state/.../v0111-ui-audit/21_slew_pane.png.
            Text("Observer location", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = vm.latDeg,
                onValueChange = vm::updateLat,
                label = { Text("Lat ° (N+)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.lngEastDeg,
                onValueChange = vm::updateLng,
                label = { Text("Lng ° (E+)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !vm.raDecMode, onClick = { vm.setRaDecMode(false) }, label = { Text("Az/Alt") })
                FilterChip(selected = vm.raDecMode, onClick = { vm.setRaDecMode(true) }, label = { Text("RA/Dec") })
            }

            if (vm.raDecMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = vm.gotoRa,
                        onValueChange = vm::updateRa,
                        label = { Text("RA (HH MM SS)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = vm.gotoDec,
                        onValueChange = vm::updateDec,
                        label = { Text("Dec (±DD MM SS)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = vm.gotoAz,
                        onValueChange = { vm.gotoAz = it },
                        label = { Text("Azimuth °") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = vm.gotoAlt,
                        onValueChange = { vm.gotoAlt = it },
                        label = { Text("Altitude °") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::goto) { Text("Slew") }
                OutlinedButton(onClick = vm::cancelSlew) { Text("Cancel slew") }
                OutlinedButton(onClick = vm::resetPosition) { Text("Reset position") }
            }

            HorizontalDivider()

            Text("Plate solve", style = MaterialTheme.typography.titleSmall)
            Text(
                "Detect stars in the current preview frame and nudge the mount to centre the entered RA/Dec target. Requires a connected camera and a valid observer location.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = vm::solveNow,
                    enabled = !vm.solveInProgress,
                ) {
                    Text(if (vm.solveInProgress) "Solving…" else "Solve now")
                }
            }
            val solve by vm.lastSolveResult.collectAsState()
            val solved = solve
            if (solved != null) {
                Text(
                    "Last solve: RA %.4f°  Dec %.4f°  (matched=%d, conf=%.2f)".format(
                        solved.raDeg, solved.decDeg, solved.matchedStars, solved.confidence,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            HorizontalDivider()

            Text("Star alignment (${vm.alignmentStars} stars)", style = MaterialTheme.typography.titleSmall)
            Text(
                "Center a bright star with the jog controls, then record it. 2–3 stars spread across the sky give the best pointing model.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = alignmentQuery,
                onValueChange = { alignmentQuery = it },
                label = { Text("Find a named alignment star") },
                placeholder = { Text("Polaris, Vega, Deneb…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            val alignmentCandidates = if (alignmentQuery.length < 2) emptyList() else
                vm.tonightCatalog.objects.filter { star ->
                    star.type == dev.openpolaris.core.astro.ObjectType.STAR &&
                        (star.designation.contains(alignmentQuery, ignoreCase = true) ||
                            star.name.contains(alignmentQuery, ignoreCase = true))
                }.take(8)
            vm.selectedAlignmentStar?.let { selected ->
                Text("Selected: ${selected.designation} — centre it, then Record star", style = MaterialTheme.typography.labelMedium)
            }
            alignmentCandidates.forEach { star ->
                TextButton(
                    onClick = { vm.selectAlignmentStar(star.designation, star.raDeg, star.decDeg) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("${star.designation}  ${star.name} · RA ${AstroMath.formatRaHours(star.raDeg)} · Dec ${AstroMath.formatDecDMS(star.decDeg)}")
                }
            }
            solved?.let { result ->
                OutlinedButton(
                    onClick = { vm.selectAlignmentStar("plate-solve", result.raDeg, result.decDeg) },
                ) { Text("Use last plate solve as alignment identity") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::submitAlignmentStar) { Text("Record star") }
                OutlinedButton(onClick = vm::resetAlignment) { Text("Reset alignment") }
            }

            HorizontalDivider()

            Text("Auto-level", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = vm::runAutoLevel, enabled = !vm.autoLevelRunning) {
                    Text(if (vm.autoLevelRunning) "Leveling…" else "Level now")
                }
                Switch(checked = vm.autoLevelEnabled == true, onCheckedChange = vm::setAutoLevelEnabled)
                Text(if (vm.autoLevelEnabled == true) "Enabled" else "Disabled / unknown")
                OutlinedButton(onClick = vm::refreshAutoLevel) { Text("Refresh") }
            }
            AutoLevelTiltStatus(tilt = vm.autoLevelTilt)
        }
    }
}

/**
 * Camera pane: all 10 camera GETs as index steppers plus capture.
 * Codes are inferred — a warning banner says so until hardware-validated.
 * The 6 new steppers (Focus / Image size / Image format / Color / Shutter /
 * Capture mode) were added after the 5dab031 commit wired the underlying
 * GET/SET pairs into `CommandTable` and the post-connect burst.
 */
@Composable
fun CameraPane(vm: AppViewModel, modifier: Modifier = Modifier) {
    val c = vm.camera
    val attachment = vm.cameraAttachment
    val qualificationEnabled = dev.openpolaris.core.config.FeatureFlags.isEnabled("experimentalCamera")
    var qualificationArmed by remember { mutableStateOf(false) }
    var bulbSecondsText by remember { mutableStateOf("5") }
    val shutterLabel = c.shutterIndex?.let { c.shutterOptions.getOrNull(it)?.trim() }
    val releaseMode = CameraReleaseMode.fromOption(shutterLabel)
    Card(modifier = modifier.padding(8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Camera", style = MaterialTheme.typography.titleMedium)
            // 286 camera-attachment status (one connect-time snapshot). Without this the
            // pane shows a wall of "unavailable" steppers with no explanation —
            // the firmware's `state:-5` sentinel is the reason. Live-verified
            // 2026-09-16: no camera → `manufacturer:none;model:none;state:-5`.
            when {
                attachment == null -> Text(
                    "Camera detection: unknown (read on connect)",
                    style = MaterialTheme.typography.bodySmall,
                )
                !attachment.isAttached -> Text(
                    "No camera detected by the mount (state ${attachment.state}). " +
                        "Re-seat the USB cable / power the camera, then wait a few seconds — " +
                        "the mount re-detects on its own schedule.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                else -> Text(
                    "Camera: ${attachment.manufacturer ?: "?"} ${attachment.model ?: ""}".trim() +
                        " (state ${attachment.state})",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = vm::refreshCameraAttachment) {
                Text("Refresh camera status")
            }
            Text(
                "Parameter readback: ${vm.cameraReadbackAgeText}. Dynamic options load only after an explicit qualified refresh.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Release mode: ${releaseMode.label}${shutterLabel?.let { " ($it)" } ?: ""}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                if (qualificationEnabled)
                    "Qualification mode uses Benro Connect's evidenced INFO/SET map. Changes are sent to the attached camera; verify and restore each original value."
                else
                    "Camera settings are locked. Enable Camera qualification mode in Settings to test the evidenced Benro mappings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            if (attachment == null) {
                Text(
                    "Camera controls and capture are disabled until camera status is verified. Refresh the camera status before trying again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (!attachment.isAttached) {
                Text(
                    "Camera controls and capture are disabled until the mount reports an attached camera.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (qualificationEnabled && !qualificationArmed) {
                Button(onClick = { qualificationArmed = true }) {
                    Text("I understand — arm setting tests")
                }
            }
            // v0.1.8: 2-column grid of steppers so the pane fits a 320 dp
            // phone in landscape. Was a single 10-row Column that clipped
            // half its controls below the callout dialog fold (#45).
            val cameraWriteEnabled = qualificationArmed && attachment?.isAttached == true
            val steppers: List<@Composable () -> Unit> = listOf(
                { StepperRow("ISO", c.isoIndex, c.isoOptions, cameraWriteEnabled, vm::setIso) },
                { StepperRow("WB", c.wbIndex, c.wbOptions, cameraWriteEnabled, vm::setWb) },
                { StepperRow("Aperture", c.fNumIndex, c.fNumOptions, cameraWriteEnabled, vm::setFNum) },
                { StepperRow("EV", c.evIndex, c.evOptions, cameraWriteEnabled, vm::setEv) },
                { StepperRow("Shutter", c.shutterIndex, c.shutterOptions, cameraWriteEnabled, vm::setShutter) },
            )
            val midpoint = (steppers.size + 1) / 2
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    steppers.take(midpoint).forEach { it() }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    steppers.drop(midpoint).forEach { it() }
                }
            }
            Text(
                "Not mapped for setting yet: focus, image size, image format, colour, capture mode. Workflow commands are inventoried but remain locked until their payload contracts are evidenced.",
                style = MaterialTheme.typography.bodySmall,
            )
            // #60: drive the button off the capture *phase* machine, not just
            // the raw 266 poll value. The K-3 III reports transient negative
            // states (e.g. -1005) mid-capture; the phase machine treats those
            // as busy and only completes when the camera returns to idle, so
            // the user sees one pending/busy period followed by an accurate
            // result instead of a false "failed" flash.
            val phase = vm.capturePhase
            val inFlight = phase is dev.openpolaris.ui.AppViewModel.CapturePhase.Requested ||
                phase is dev.openpolaris.ui.AppViewModel.CapturePhase.Busy
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = vm::capture, enabled = !inFlight && attachment?.isAttached == true) { Text("Capture") }
                when (phase) {
                    is dev.openpolaris.ui.AppViewModel.CapturePhase.Requested ->
                        Text("Sent…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    is dev.openpolaris.ui.AppViewModel.CapturePhase.Busy ->
                        Text("Busy", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    is dev.openpolaris.ui.AppViewModel.CapturePhase.Completed ->
                        Text("Done", style = MaterialTheme.typography.labelMedium)
                    is dev.openpolaris.ui.AppViewModel.CapturePhase.Failed ->
                        Text("Failed: ${phase.reason}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    else -> {}
                }
                OutlinedButton(onClick = vm::refreshCamera) { Text("Refresh") }
            }
            vm.lastCapturePublication?.let { publication ->
                Text(
                    "Published: ${publication.path}" + (publication.sizeBytes?.let { " (${it} bytes)" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            CaptureTimelinePane(vm, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val bulbInFlight = phase is dev.openpolaris.ui.AppViewModel.CapturePhase.Requested ||
                    phase is dev.openpolaris.ui.AppViewModel.CapturePhase.Busy
                OutlinedTextField(
                    value = bulbSecondsText,
                    onValueChange = { if (it.all(Char::isDigit)) bulbSecondsText = it },
                    label = { Text("Bulb seconds") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(150.dp),
                )
                Button(
                    onClick = { vm.captureBulb(bulbSecondsText.toIntOrNull() ?: 0) },
                    enabled = !bulbInFlight && attachment?.isAttached == true && releaseMode == CameraReleaseMode.BULB,
                ) {
                    Text("Bulb capture")
                }
                if (attachment?.isAttached == true && releaseMode != CameraReleaseMode.BULB) {
                    Text(
                        "Bulb capture requires a verified B / Bulb shutter readback.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (bulbInFlight) {
                    OutlinedButton(onClick = vm::stopBulb) { Text("Stop Bulb") }
                }
            }
            HorizontalDivider()
            Text("Astro WebUI shutter", style = MaterialTheme.typography.titleSmall)
            Text(
                "Optional source-aligned resident WebUI route. HTTP acceptance is only queue evidence; the same lifecycle + file evidence gate decides completion.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (!FeatureFlags.isEnabled("astroWebUiCapture")) {
                Text(
                    "Disabled until Astro WebUI M/B lifecycle and named-firmware qualification is complete.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val astroActive = vm.astroWebUiCaptureActive || vm.astroWebUiCaptureDispatching
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { vm.captureAstroWebUi(CameraReleaseMode.MANUAL) },
                        enabled = !astroActive && !inFlight && attachment?.isAttached == true && releaseMode == CameraReleaseMode.MANUAL,
                    ) { Text("Astro M") }
                    Button(
                        onClick = { vm.captureAstroWebUi(CameraReleaseMode.BULB, bulbSecondsText.toIntOrNull() ?: 0) },
                        enabled = !astroActive && !inFlight && attachment?.isAttached == true && releaseMode == CameraReleaseMode.BULB,
                    ) { Text("Astro B") }
                    if (vm.astroWebUiBulbActive && !vm.astroWebUiBulbStopQueued) {
                        OutlinedButton(onClick = vm::stopAstroWebUiBulb) { Text("Stop Astro B") }
                    }
                    if (vm.astroWebUiBulbStopQueued) {
                        Text("Astro B stop queued; waiting for capture evidence", style = MaterialTheme.typography.labelSmall)
                    }
                }
                vm.astroWebUiCaptureStatus?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall)
                }
            }

            // §6: client-driven capture sequence (intervalometer). The engine
            // fires one shutter per shot at the planned interval; each shot is
            // confirmed by the capture-event observer correlating the 264
            // lifecycle + 773 file events. Progress mirrors vm.sequenceState.
            IntervalometerSection(vm)
            ObservationProgrammePreviewSection(vm)

            HorizontalDivider()
            Text("Focus and video", style = MaterialTheme.typography.titleSmall)
            Text(
                "These controls are visible so the workflow is discoverable. Their source-derived wire paths remain disabled until direction, lifecycle and recovery are qualified for the attached camera.",
                style = MaterialTheme.typography.bodySmall,
            )
            val focusEnabled = qualificationArmed &&
                FeatureFlags.isEnabled("cameraFocusControl") &&
                attachment?.isAttached == true
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.adjustFocusNear() }, enabled = focusEnabled) { Text("Focus near") }
                OutlinedButton(onClick = { vm.adjustFocusFar() }, enabled = focusEnabled) { Text("Focus far") }
                vm.focusStatus?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
            }
            val videoEnabled = qualificationArmed &&
                FeatureFlags.isEnabled("cameraVideo") &&
                attachment?.isAttached == true
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { vm.setVideoRecording(true) },
                    enabled = videoEnabled && vm.videoRecording != true,
                ) { Text("Start video") }
                OutlinedButton(
                    onClick = { vm.setVideoRecording(false) },
                    enabled = videoEnabled && vm.videoRecording == true,
                ) { Text("Stop video") }
                Text(
                    when (vm.videoRecording) {
                        true -> "Recording"
                        false -> "Stopped"
                        null -> "Video state unknown"
                    },
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (!FeatureFlags.isEnabled("cameraFocusControl") || !FeatureFlags.isEnabled("cameraVideo")) {
                Text(
                    "Enablement is intentionally unavailable in this build until the named-camera qualification record is complete.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Cross-platform, read-only preview of the Astro WebUI-style programme plans.
 * The preview deliberately does not acquire the camera or motion owner; the
 * executor and capture evidence bridge are the only future execution path.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ObservationProgrammePreviewSection(vm: AppViewModel) {
    var selectedKind by remember { mutableStateOf(ObservationProgrammePlanner.Kind.LAPSE) }
    var plan by remember { mutableStateOf<ObservationProgrammePlanner.Plan?>(null) }

    HorizontalDivider()
    Text("Observation programmes", style = MaterialTheme.typography.titleSmall)
    Text(
        "Build a safe plan preview for lapse, HDR, focus-stack or panorama work. Previewing never moves the mount or fires the camera.",
        style = MaterialTheme.typography.bodySmall,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ObservationProgrammePlanner.Kind.entries.forEach { kind ->
            FilterChip(
                selected = selectedKind == kind,
                onClick = {
                    selectedKind = kind
                    plan = null
                },
                label = { Text(kind.label()) },
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = {
                plan = runCatching {
                    ObservationProgrammePlanner.plan(
                        when (selectedKind) {
                            ObservationProgrammePlanner.Kind.LAPSE ->
                                ObservationProgrammePlanner.Request.Lapse(10, 5_000L)
                            ObservationProgrammePlanner.Kind.HDR ->
                                ObservationProgrammePlanner.Request.Hdr(20, listOf(-2, 0, 2), 1_000L)
                            ObservationProgrammePlanner.Kind.FOCUS_STACK ->
                                ObservationProgrammePlanner.Request.FocusStack(10.0, 20.0, 5, 1_000L)
                            ObservationProgrammePlanner.Kind.PANORAMA ->
                                ObservationProgrammePlanner.Request.Panorama(2, 3, 30.0, 10.0, 1_000L)
                        },
                    )
                }.getOrNull()
            },
            modifier = Modifier.semantics { contentDescription = "Preview observation programme plan" },
        ) { Text("Preview plan") }
        Text("Read-only", style = MaterialTheme.typography.labelSmall)
    }
    plan?.let { preview ->
        Text(
            "${preview.kind.label()} · ${preview.captureCount} captures · ${preview.scheduledDelayMs} ms scheduled delay",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (preview.kind == ObservationProgrammePlanner.Kind.LAPSE) {
            val programmeState = vm.observationProgrammeState
            val programmeActive = programmeState is dev.openpolaris.core.domain.ObservationProgrammeExecutor.State.Running ||
                programmeState is dev.openpolaris.core.domain.ObservationProgrammeExecutor.State.Paused
            val programmeUnknown = programmeState is dev.openpolaris.core.domain.ObservationProgrammeExecutor.State.OutcomeUnknown
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { vm.startObservationProgramme(preview) },
                    enabled = !programmeActive && !programmeUnknown,
                    modifier = Modifier.semantics { contentDescription = "Run qualified lapse programme" },
                ) { Text("Run qualified lapse") }
                OutlinedButton(
                    onClick = vm::pauseObservationProgramme,
                    enabled = programmeState is dev.openpolaris.core.domain.ObservationProgrammeExecutor.State.Running,
                ) { Text("Pause") }
                OutlinedButton(
                    onClick = { vm.resumeObservationProgramme() },
                    enabled = programmeState is dev.openpolaris.core.domain.ObservationProgrammeExecutor.State.Paused,
                ) { Text("Resume") }
                OutlinedButton(onClick = vm::stopObservationProgramme, enabled = programmeActive) { Text("Stop") }
            }
            Text(
                when (programmeState) {
                    dev.openpolaris.core.domain.ObservationProgrammeExecutor.State.Idle -> "Qualified path: fixed-exposure lapse with correlated file evidence"
                    is dev.openpolaris.core.domain.ObservationProgrammeExecutor.State.Running -> "Running: ${programmeState.completedSteps}/${programmeState.plan.captureCount} confirmed"
                    is dev.openpolaris.core.domain.ObservationProgrammeExecutor.State.Paused -> "Paused: ${programmeState.completedSteps}/${programmeState.plan.captureCount} confirmed"
                    is dev.openpolaris.core.domain.ObservationProgrammeExecutor.State.Completed -> "Completed: ${programmeState.completedSteps} captures"
                    is dev.openpolaris.core.domain.ObservationProgrammeExecutor.State.Stopped -> "Stopped: ${programmeState.completedSteps} captures"
                    is dev.openpolaris.core.domain.ObservationProgrammeExecutor.State.Failed -> "Failed: ${programmeState.reason}"
                    is dev.openpolaris.core.domain.ObservationProgrammeExecutor.State.OutcomeUnknown -> "Outcome unknown: reconnect before another shutter"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (programmeUnknown) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "Planner preview only: this programme needs separately qualified exposure or motion controls.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        preview.steps.take(5).forEach { step ->
            Text(
                "${step.ordinal}. ${step.label} · wait ${step.delayBeforeMs} ms" +
                    (step.exposureIndex?.let { " · exposure $it" } ?: "") +
                    (step.focusPosition?.let { " · focus $it" } ?: "") +
                    (step.yawOffsetDeg?.let { " · yaw $it°" } ?: "") +
                    (step.pitchOffsetDeg?.let { " · pitch $it°" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (preview.steps.size > 5) {
            Text("… ${preview.steps.size - 5} more planned steps", style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun ObservationProgrammePlanner.Kind.label(): String = when (this) {
    ObservationProgrammePlanner.Kind.LAPSE -> "Lapse"
    ObservationProgrammePlanner.Kind.HDR -> "HDR"
    ObservationProgrammePlanner.Kind.FOCUS_STACK -> "Focus stack"
    ObservationProgrammePlanner.Kind.PANORAMA -> "Panorama"
}

/**
 * §6: plan inputs (shot count / interval / pre-delay) plus start, pause,
 * resume and stop controls for the client-driven capture sequence, with a
 * live progress line driven by [AppViewModel.sequenceState].
 */
@Composable
private fun IntervalometerSection(vm: AppViewModel) {
    var shotCountText by remember { mutableStateOf("10") }
    var intervalSecText by remember { mutableStateOf("5") }
    var preDelaySecText by remember { mutableStateOf("0") }

    val state = vm.sequenceState
    val running = state is dev.openpolaris.core.domain.IntervalometerController.State.Running
    val paused = state is dev.openpolaris.core.domain.IntervalometerController.State.Paused
    val active = running || paused
    val outcomeUnknown = state is dev.openpolaris.core.domain.IntervalometerController.State.OutcomeUnknown
    val canStart = !active && !outcomeUnknown

    HorizontalDivider()
    Text("Capture sequence", style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = shotCountText,
            onValueChange = { shotCountText = it },
            label = { Text("Shots") },
            singleLine = true,
            enabled = canStart,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = intervalSecText,
            onValueChange = { intervalSecText = it },
            label = { Text("Interval (s)") },
            singleLine = true,
            enabled = canStart,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = preDelaySecText,
            onValueChange = { preDelaySecText = it },
            label = { Text("Pre-delay (s)") },
            singleLine = true,
            enabled = canStart,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = {
                val count = shotCountText.trim().toIntOrNull() ?: 0
                val intervalMs = (intervalSecText.trim().toDoubleOrNull()?.times(1000.0))?.toLong() ?: 0L
                val preDelayMs = (preDelaySecText.trim().toDoubleOrNull()?.times(1000.0))?.toLong() ?: 0L
                vm.startSequence(count, intervalMs, preDelayMs)
            },
            enabled = canStart,
        ) { Text("Start") }
        OutlinedButton(onClick = vm::pauseSequence, enabled = running) { Text("Pause") }
        OutlinedButton(onClick = vm::resumeSequence, enabled = paused) { Text("Resume") }
        OutlinedButton(onClick = vm::stopSequence, enabled = active) { Text("Stop") }
    }
    when (state) {
        is dev.openpolaris.core.domain.IntervalometerController.State.Idle ->
            Text("Idle", style = MaterialTheme.typography.labelMedium)
        is dev.openpolaris.core.domain.IntervalometerController.State.Running ->
            Text(
                "Running: shot ${state.completedShots} of ${state.plan.shotCount}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        is dev.openpolaris.core.domain.IntervalometerController.State.Paused ->
            Text(
                "Paused: shot ${state.completedShots} of ${state.plan.shotCount}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        is dev.openpolaris.core.domain.IntervalometerController.State.Completed ->
            Text("Completed: ${state.completedShots} shots", style = MaterialTheme.typography.labelMedium)
        is dev.openpolaris.core.domain.IntervalometerController.State.Stopped ->
            Text(
                "Stopped: ${state.completedShots} of ${state.plan.shotCount}",
                style = MaterialTheme.typography.labelMedium,
            )
        is dev.openpolaris.core.domain.IntervalometerController.State.Failed ->
            Text("Failed: ${state.reason}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
        is dev.openpolaris.core.domain.IntervalometerController.State.OutcomeUnknown ->
            Text("Outcome unknown: ${state.reason}. Reconnect before another shutter.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: Int?,
    options: List<String>,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    // v0.1.12: vertical layout to keep the label readable inside the
    // narrow 2-column Camera pane. The previous Row-with-weight-Text
    // got squeezed to ~0 width when the two OutlinedButtons took
    // their natural width first (#47). See
    // .copilot/agent-state/.../v0111-ui-audit/20_camera_pane.png.
    var candidate by remember(value) { mutableStateOf(value ?: 0) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "$label: ${value?.let { options.getOrNull(it) } ?: "unavailable"}" +
                if (value != candidate) " → ${options.getOrNull(candidate) ?: candidate}" else "",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(
                onClick = {
                    candidate = (candidate - 1).coerceAtLeast(0)
                    onChange(candidate)
                },
                enabled = enabled && value != null && options.isNotEmpty() && candidate > 0,
                modifier = Modifier.weight(1f).semantics { contentDescription = "Decrease $label" },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) { Text("−", style = MaterialTheme.typography.labelLarge) }
            OutlinedButton(
                onClick = {
                    candidate += 1
                    onChange(candidate)
                },
                enabled = enabled && value != null && options.isNotEmpty() && candidate < options.lastIndex,
                modifier = Modifier.weight(1f).semantics { contentDescription = "Increase $label" },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) { Text("+", style = MaterialTheme.typography.labelLarge) }
        }
    }
}

/**
 * Live MJPEG preview of the camera. Best-effort: a slow or absent
 * stream shows a "Stream unavailable" placeholder and never blocks
 * the control pane. The frame is decoded off the main thread by
 * AppViewModel; this composable just renders whatever's latest.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun PreviewPane(vm: AppViewModel, modifier: Modifier = Modifier) {
    androidx.compose.runtime.DisposableEffect(vm) {
        vm.enterPreview()
        onDispose { vm.leavePreview() }
    }
    var framingGrid by remember { mutableStateOf(false) }
    var centreMark by remember { mutableStateOf(false) }
    var showHistogram by remember { mutableStateOf(false) }
    var focusPeaking by remember { mutableStateOf(false) }
    val frame = vm.previewFrame
    val analysis = vm.previewPixelAnalysis
    val state = vm.previewState
    var nowMs by remember { mutableStateOf(SystemMillis.now()) }
    LaunchedEffect(frame != null) {
        while (frame != null) {
            nowMs = SystemMillis.now()
            delay(1_000L)
        }
    }
    val frameAgeSeconds = vm.previewFrameReceivedAtMs?.let { received ->
        ((nowMs - received).coerceAtLeast(0L) / 1_000L)
    }
    Card(modifier = modifier.padding(8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Camera preview", style = MaterialTheme.typography.titleMedium)
            if (frame != null) {
                Box(Modifier.fillMaxWidth()) {
                    androidx.compose.foundation.Image(
                        bitmap = frame,
                        contentDescription = "Live camera preview",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (framingGrid || centreMark || (focusPeaking && analysis != null)) {
                        Canvas(Modifier.fillMaxSize()) {
                            val stroke = 1.dp.toPx()
                            val overlay = Color.White.copy(alpha = 0.75f)
                            if (framingGrid) {
                                drawLine(overlay, Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), strokeWidth = stroke)
                                drawLine(overlay, Offset(size.width * 2f / 3f, 0f), Offset(size.width * 2f / 3f, size.height), strokeWidth = stroke)
                                drawLine(overlay, Offset(0f, size.height / 3f), Offset(size.width, size.height / 3f), strokeWidth = stroke)
                                drawLine(overlay, Offset(0f, size.height * 2f / 3f), Offset(size.width, size.height * 2f / 3f), strokeWidth = stroke)
                            }
                            if (centreMark) {
                                drawLine(overlay, Offset(size.width / 2f - 12.dp.toPx(), size.height / 2f), Offset(size.width / 2f + 12.dp.toPx(), size.height / 2f), strokeWidth = stroke)
                                drawLine(overlay, Offset(size.width / 2f, size.height / 2f - 12.dp.toPx()), Offset(size.width / 2f, size.height / 2f + 12.dp.toPx()), strokeWidth = stroke)
                            }
                            if (focusPeaking) {
                                analysis?.let { result ->
                                    val radius = minOf(
                                        size.width / (result.peakMapWidth * 3f),
                                        size.height / (result.peakMapHeight * 3f),
                                    ).coerceAtLeast(1f)
                                    for (y in 0 until result.peakMapHeight) {
                                        for (x in 0 until result.peakMapWidth) {
                                            if (result.peakMap[y * result.peakMapWidth + x]) {
                                                drawCircle(
                                                    color = Color.Red.copy(alpha = 0.85f),
                                                    radius = radius,
                                                    center = Offset(
                                                        size.width * (x + 0.5f) / result.peakMapWidth,
                                                        size.height * (y + 0.5f) / result.peakMapHeight,
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            frameAgeSeconds?.let { age ->
                Text(
                    if (age == 0L) "Frame age: under 1 s" else "Frame age: ${age} s",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (age > 5L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilterChip(
                    selected = framingGrid,
                    onClick = { framingGrid = !framingGrid },
                    label = { Text("Grid") },
                )
                FilterChip(
                    selected = centreMark,
                    onClick = { centreMark = !centreMark },
                    label = { Text("Centre") },
                )
                FilterChip(
                    selected = showHistogram,
                    onClick = { showHistogram = !showHistogram },
                    label = { Text("Histogram") },
                    modifier = Modifier.semantics { contentDescription = "Toggle preview histogram" },
                )
                FilterChip(
                    selected = focusPeaking,
                    onClick = { focusPeaking = !focusPeaking },
                    label = { Text("Focus peaks") },
                    modifier = Modifier.semantics { contentDescription = "Toggle preview focus peaking" },
                )
            }
            Text(
                "Framing overlays and pixel analysis are local presentation aids; they do not alter camera or mount state.",
                style = MaterialTheme.typography.labelSmall,
            )
            if (showHistogram) {
                if (analysis == null) {
                    Text(
                        "Histogram waiting for a decoded preview frame…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val histogramColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        Canvas(Modifier.fillMaxWidth().height(72.dp)) {
                            val maximum = analysis.histogramMax.coerceAtLeast(1)
                            val barWidth = size.width / analysis.histogram.size
                            analysis.histogram.forEachIndexed { index, count ->
                                val barHeight = size.height * count / maximum
                                drawRect(
                                    color = histogramColor,
                                    topLeft = Offset(index * barWidth, size.height - barHeight),
                                    size = androidx.compose.ui.geometry.Size((barWidth - 1f).coerceAtLeast(1f), barHeight),
                                )
                            }
                        }
                        Text(
                            "Mean luminance ${analysis.meanLuma.toInt()}/255 · focus edges ${(analysis.peakFraction * 100f).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            if (focusPeaking && analysis == null) {
                Text(
                    "Focus peaking waiting for a decoded preview frame…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                frame == null -> when {
                // 3h-BUG: `state` is a StateFlow, not a State. The previous
                // check `state is PreviewController.State.Connecting` was
                // comparing a StateFlow to a State object — always false.
                // The branch never fired, so the user only ever saw the
                // generic "Stream unavailable" message while the transport
                // was still connecting. Use `.value` to read the current
                // state out of the flow.
                state.value is dev.openpolaris.core.domain.PreviewController.State.Connecting ->
                    Text("Connecting…", style = MaterialTheme.typography.bodyMedium)
                state.value is dev.openpolaris.core.domain.PreviewController.State.Empty ->
                    Text("Preview endpoint is open but no frames arrived — check camera attachment or preview mode.", style = MaterialTheme.typography.bodySmall)
                state.value is dev.openpolaris.core.domain.PreviewController.State.Error ->
                    Text(
                        "Stream unavailable: ${(state.value as dev.openpolaris.core.domain.PreviewController.State.Error).message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                else ->
                    Text("Stream unavailable — connect to the mount or check Wi-Fi.", style = MaterialTheme.typography.bodySmall)
                }
            }
            // Live-verified 2026-09-16: with no camera attached the firmware's
            // MJPG-Streamer answers HTTP 200 but never sends a JPEG frame, so
            // the pane sits on "Connecting…" for the full 30 s read timeout.
            // The 286 poll (vm.cameraAttachment) tells us that up front.
            if (frame == null && vm.cameraAttachment != null && !vm.cameraAttachment!!.isAttached) {
                Text(
                    "No camera detected by the mount — the stream will stay empty until one is attached.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                // 3h-BUG: surface the live preview port (vm.previewPort) instead
                // of a hard-coded 8080 so the user knows which port the preview
                // is actually using. (Was vm.port — the *control* socket port,
                // which is wrong: the MJPEG stream lives on the fixed 8080.)
                "Streamed from http://${vm.host}:${vm.previewPort}/?action=stream. 16:9, best-effort, frames are dropped when stale.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * Tilt envelope read-out. Shows the latest pitch/roll whenever the controller
 * has a value, and a color-coded level badge against [AutoLevelController.TOLERANCE_DEG].
 * Compact enough to live inside the Auto-level card.
 */
@Composable
fun AutoLevelTiltStatus(tilt: dev.openpolaris.core.domain.AutoLevelController.Tilt?) {
    if (tilt == null) {
        Text(
            "Tilt: unknown — tap Refresh or run Level now to poll.",
            style = MaterialTheme.typography.bodySmall,
        )
        return
    }
    val pitch = tilt.pitchDeg.format2()
    val roll = tilt.rollDeg.format2()
    val badge = if (tilt.withinTolerance) "Level" else "Tilt detected"
    val color = if (tilt.withinTolerance) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Tilt: pitch $pitch°  roll $roll°", style = MaterialTheme.typography.bodySmall)
        Text(badge, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/**
 * Firmware pane: pick a FwPkt.zip from disk, optionally reboot after install,
 * and stream the upload/install progress to the user.
 *
 *  - "Pick firmware" opens the native file chooser (JVM: java.awt.FileDialog,
 *    Android: ACTION_GET_CONTENT via FilePicker — single-tap = select UX).
 *    The picked path is held by the VM so the file survives a recomposition.
 *  - "Upload" reads the bytes on [Dispatchers.IO] and runs the full
 *    [FirmwareUpdateController] flow (arm → start → chunks → end → install
 *    → poll → optional reboot). Disabled until a file is picked and the
 *    feature flag is enabled.
 *  - The progress bar binds to [AppViewModel.firmwareStatus] and renders
 *    a percentage plus a short status label so the user can tell whether
 *    the upload is in progress, the install is in progress, or the call
 *    finished (with or without an error).
 *
 * Note: the firmware-upload feature is gated behind
 * [dev.openpolaris.core.config.FeatureFlags] "firmwareUpload" — this pane
 * does not enforce that itself; the VM rejects the call and surfaces a
 * status message. We do, however, show a banner in this pane so the user
 * knows the feature is disabled if they have not yet enabled it.
 */
@Composable
fun FirmwarePane(vm: AppViewModel, modifier: Modifier = Modifier) {
    val status = vm.firmwareStatus
    val featureEnabled = remember { dev.openpolaris.core.config.FeatureFlags.isEnabled("firmwareUpload") }
    val bytesTotal = (status as? dev.openpolaris.core.domain.FirmwareUpdateController.Status.Uploading)?.bytesTotal
    val bytesSent = (status as? dev.openpolaris.core.domain.FirmwareUpdateController.Status.Uploading)?.bytesSent
    val installPercent = (status as? dev.openpolaris.core.domain.FirmwareUpdateController.Status.Installing)?.percent
    val progress: Float? = when {
        bytesTotal != null && bytesTotal > 0 && bytesSent != null -> {
            (bytesSent.toFloat() / bytesTotal.toFloat()).coerceIn(0f, 1f)
        }
        installPercent != null -> installPercent / 100f
        status is dev.openpolaris.core.domain.FirmwareUpdateController.Status.Done -> 1f
        else -> null
    }

    // v0.1.8: paginate into 3 steps so the callout dialog (which is
    // intentionally NOT scrollable — see issues #40/#42 and the
    // CalloutDialog constraint in OpenPolarisApp.kt) can show the
    // full pane on a 320 dp phone in landscape. 1 = pick, 2 = options,
    // 3 = run. Steps 2 and 3 are only enabled once a file is picked.
    var step by remember { mutableStateOf(1) }
    val picked = vm.pickedFirmwarePath != null

    Card(modifier = modifier.padding(8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Firmware update", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Step $step of 3",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when (step) {
                1 -> FirmwareStep1(
                    vm = vm,
                    featureEnabled = featureEnabled,
                )
                2 -> FirmwareStep2(vm = vm)
                3 -> FirmwareStep3(
                    vm = vm,
                    featureEnabled = featureEnabled,
                    progress = progress,
                    status = status,
                )
            }

            // ---- Pager buttons (no verticalScroll) ------------------------
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = { step = (step - 1).coerceAtLeast(1) },
                    enabled = step > 1,
                ) { Text("Back") }

                TextButton(
                    onClick = { step = 1 },
                ) { Text("Close") }

                Button(
                    onClick = { step = (step + 1).coerceAtMost(3) },
                    enabled = when (step) {
                        1 -> picked && featureEnabled
                        2 -> vm.firmwareDeliveryMode == DeliveryMode.SSH_PIPE || vm.firmwareSshHost.isNotBlank()
                        else -> false
                    },
                ) { Text(if (step < 3) "Next" else "Done") }
            }
        }
    }
}

// Step 1 — pick the FwPkt.zip, surface the experimental banner and the
// local MD5 placeholder. Same content as the v0.1.7 top of the pane.
@Composable
private fun FirmwareStep1(
    vm: AppViewModel,
    featureEnabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Phase 1a banner (docs/FIRMWARE-UPLOAD-AUDIT-2026-09-01.md §6 #1).
        // The WIRE envelope below is reconstructed from the Benro Connect
        // decompile and has not been observed in a live traffic capture.
        // A bad FwPkt.zip bricks the gimbal until you re-flash over USB.
        // Always verify the bundle with `crcInfo` in the Benro Connect
        // app before pressing Upload.
        Text(
            "EXPERIMENTAL — a bad image bricks the gimbal until you re-flash over USB. " +
                "Verify the bundle's CRC in the Benro Connect app before uploading. " +
                "Use the SSH_PIPE delivery (default) unless you have a reason not to.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )

        if (!featureEnabled) {
            Text(
                "Firmware upload is disabled. Open the 'Settings' callout and turn " +
                    "on the 'firmwareUpload' flag to use this pane. Firmware install is " +
                    "destructive — a bad image bricks the mount until you re-flash over USB.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        // ---- Picked file summary ---------------------------------------
        val name = vm.pickedFirmwareName
        val size = vm.pickedFirmwareSize
        if (name != null) {
            Text(
                text = if (size != null) "Selected: $name (${humanBytes(size)})" else "Selected: $name",
                style = MaterialTheme.typography.bodyMedium,
            )
            // Phase 1a #2: surface the locally-computed MD5 so the user
            // can sanity-check the picked file against an external
            // source (Benro web console, the original download's
            // checksum, a side-loaded copy, etc.). Populated at
            // upload time, not pick time, so it stays "—" until the
            // first time Upload runs.
            val localMd5 = vm.pickedFirmwareMd5
            Text(
                text = if (localMd5 != null) "Local MD5:  $localMd5" else "Local MD5:  — (computed at upload time)",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Text(
                "No firmware selected. Tap 'Pick firmware…' to choose a FwPkt.zip.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = vm::pickFirmwareFile,
                enabled = !vm.firmwareBusy,
            ) { Text("Pick firmware…") }
            if (vm.pickedFirmwarePath != null) {
                OutlinedButton(
                    onClick = vm::clearPickedFirmware,
                    enabled = !vm.firmwareBusy,
                ) { Text("Clear") }
            }
        }
    }
}

// Step 2 — delivery mode, expected MD5, SSH host, reboot-after toggle.
@Composable
private fun FirmwareStep2(vm: AppViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // ---- Options ---------------------------------------------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = vm.firmwareRebootAfter,
                onCheckedChange = { vm.firmwareRebootAfter = it },
                enabled = !vm.firmwareBusy,
            )
            Text(
                "  Reboot mount after install",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // Phase 1a #2: expected-MD5 cross-check. The user pastes the
        // bundle's MD5 (typically from the Benro web console) and the
        // controller refuses to touch the wire / SD if the local hash
        // disagrees. Mirrors the Benro Connect flow where the user is
        // expected to verify the hash before pressing Upload. Leaving
        // the field blank disables the check (the controller's
        // expectedMd5=null branch is exercised in the null test).
        OutlinedTextField(
            value = vm.firmwareExpectedMd5,
            onValueChange = { vm.firmwareExpectedMd5 = it.trim() },
            enabled = !vm.firmwareBusy,
            singleLine = true,
            label = { Text("Expected MD5 (from Benro console — optional)") },
            placeholder = { Text("32-char hex, e.g. d41d8cd98f00b204e9800998ecf8427e") },
            modifier = Modifier.fillMaxWidth(),
        )

        // ---- Delivery mode (verified vs experimental) -----------------
        // The default SSH_PIPE path is the verified one — the bytes
        // are scp'd to /app/sd/FwPkt.zip and the on-board
        // 783 extraction trigger and boot-time SD scan take over.
        // The WIRE path drives 810/784/794/795/811/812
        // through the binary control plane; that envelope is a
        // best-effort reconstruction from the Benro Connect
        // Android decompile and has not been observed in a live
        // Benro Connect capture. See FirmwareUpdateController KDoc
        // for the trust profile of each.
        Column {
            Text("Delivery mode", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = vm.firmwareDeliveryMode == DeliveryMode.SSH_PIPE,
                    onClick = { vm.firmwareDeliveryMode = DeliveryMode.SSH_PIPE },
                    enabled = !vm.firmwareBusy,
                )
                Text("SSH pipe (verified) — scp FwPkt.zip, then reboot", style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = vm.firmwareDeliveryMode == DeliveryMode.WIRE,
                    onClick = { vm.firmwareDeliveryMode = DeliveryMode.WIRE },
                    enabled = false,
                )
                Text("Wire envelope unavailable — chunk framing is unproven", style = MaterialTheme.typography.bodyMedium)
            }
            if (vm.firmwareDeliveryMode == DeliveryMode.WIRE) {
                Text(
                    "WIRE is reconstructed from the Benro Connect decompile and " +
                        "has not been observed in a live Benro Connect traffic " +
                        "capture. The chunk payload slot is currently a `len:N;` " +
                        "placeholder. Use SSH_PIPE for a verified upload.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        // ---- SSH host (only meaningful for SSH_PIPE) -----------------
        if (vm.firmwareDeliveryMode == DeliveryMode.SSH_PIPE) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("SSH host:", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = vm.firmwareSshHost,
                    onValueChange = { vm.firmwareSshHost = it },
                    enabled = !vm.firmwareBusy,
                    singleLine = true,
                    modifier = Modifier.width(180.dp),
                )
            }
        }
    }
}

// Step 3 — run: Upload button, progress, status line. The "Done" button
// in the pager is a no-op (we stay on the step so the user can read the
// result); tapping "Close" resets back to step 1.
@Composable
private fun FirmwareStep3(
    vm: AppViewModel,
    featureEnabled: Boolean,
    progress: Float?,
    status: dev.openpolaris.core.domain.FirmwareUpdateController.Status?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = vm::uploadPickedFirmware,
            enabled = featureEnabled && !vm.firmwareBusy && vm.pickedFirmwarePath != null,
        ) { Text(if (vm.firmwareBusy) "Uploading…" else "Upload") }

        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            text = when (val s = status) {
                null -> "Idle."
                dev.openpolaris.core.domain.FirmwareUpdateController.Status.Idle ->
                    "Idle."
                is dev.openpolaris.core.domain.FirmwareUpdateController.Status.Uploading ->
                    "Uploading: ${s.bytesSent} / ${s.bytesTotal} bytes"
                is dev.openpolaris.core.domain.FirmwareUpdateController.Status.Installing ->
                    "Installing on mount: ${s.percent}%"
                dev.openpolaris.core.domain.FirmwareUpdateController.Status.Done ->
                    "Done."
                is dev.openpolaris.core.domain.FirmwareUpdateController.Status.Failed ->
                    "Failed: ${s.reason} The selected file is retained so you can retry."
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Human-readable byte count (1.2 MB, 542 KB, …). Used by the firmware pane. */
private fun humanBytes(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024.0
    return when {
        bytes >= mb.toLong() -> "%.1f MB".format(bytes / mb)
        bytes >= kb.toLong() -> "%.0f KB".format(bytes / kb)
        else -> "$bytes B"
    }
}
