package dev.openpolaris.ui

import androidx.compose.foundation.layout.Arrangement
import dev.openpolaris.core.protocol.Codes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import dev.openpolaris.core.domain.format2

/**
 * Connection pane: host entry, connect/demo buttons, status line.
 * [onFindWifi], when provided, opens a platform Wi-Fi picker so the user can
 * join the mount's access point without leaving the app.
 */
@Composable
fun ConnectionPane(vm: AppViewModel, modifier: Modifier = Modifier, onFindWifi: (() -> Unit)? = null) {
    Card(modifier = modifier.padding(8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Open Polaris", style = MaterialTheme.typography.headlineSmall)
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::connect) { Text("Connect") }
                OutlinedButton(onClick = vm::connectDemo) { Text("Demo mode") }
                OutlinedButton(onClick = vm::disconnect) { Text("Disconnect") }
            }
            if (onFindWifi != null) {
                OutlinedButton(onClick = onFindWifi) {
                    Text("Connect mount Wi-Fi…")
                }
            }
            Text(vm.statusMessage, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Status pane: mode, battery, tracking, half-speed, AHRS. */
@Composable
fun StatusPane(vm: AppViewModel, modifier: Modifier = Modifier) {
    val s = vm.mount
    Card(modifier = modifier.padding(8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Status", style = MaterialTheme.typography.titleMedium)
            Text("Mode: ${s.mode.name.lowercase().replaceFirstChar { it.uppercase() }}")
            Text("Battery: ${s.batteryPercent?.toString() ?: "—"}%${if (s.charging) " (charging)" else ""}")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(checked = s.tracking == true, onCheckedChange = { if (it) vm.startTracking() else vm.stopTracking() })
                Text("Tracking")
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(checked = s.halfSpeed, onCheckedChange = vm::toggleHalfSpeed)
                Text("Half speed (sidereal ÷2)")
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(checked = s.ahrsEnabled, onCheckedChange = vm::enableAhrs)
                Text("AHRS")
            }
        }
    }
}

/** Position + jog pane. */
@Composable
fun JogPane(vm: AppViewModel, modifier: Modifier = Modifier) {
    val p = vm.position
    Card(modifier = modifier.padding(8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Position", style = MaterialTheme.typography.titleMedium)
            Text("Az/Yaw: ${p?.yaw?.toDouble()?.format2() ?: "—"}°   Alt/Pitch: ${p?.pitch?.toDouble()?.format2() ?: "—"}°")
            Spacer(Modifier.height(4.dp))
            JogPad(vm, Codes.GIMBAL_HADJ_SPEED, Codes.GIMBAL_HADJ_ANGLE, Codes.GIMBAL_VADJ_SPEED, Codes.GIMBAL_VADJ_ANGLE)
        }
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
    OutlinedButton(onClick = { vm.jog(code) }) { Text(label) }
}

/** Goto pane: az/alt or RA/Dec entry, observer location, slew/cancel, alignment, auto-level. */
@Composable
fun GotoPane(vm: AppViewModel, modifier: Modifier = Modifier) {
    Card(modifier = modifier.padding(8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Slew", style = MaterialTheme.typography.titleMedium)

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Observer location:", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = vm.latDeg,
                    onValueChange = vm::updateLat,
                    label = { Text("Lat ° (N+)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = vm.lngEastDeg,
                    onValueChange = vm::updateLng,
                    label = { Text("Lng ° (E+)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

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
    Card(modifier = modifier.padding(8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Camera", style = MaterialTheme.typography.titleMedium)
            Text(
                "Experimental — command codes unverified; enable only in Demo mode or after hardware validation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            StepperRow("ISO", c.isoIndex, vm::setIso)
            StepperRow("WB", c.wbIndex, vm::setWb)
            StepperRow("Aperture", c.fNumIndex, vm::setFNum)
            StepperRow("EV", c.evIndex, vm::setEv)
            StepperRow("Focus", c.focusIndex, vm::setFocus)
            StepperRow("Image size", c.imgSizeIndex, vm::setImgSize)
            StepperRow("Image format", c.imgFmtIndex, vm::setImgFmt)
            StepperRow("Color", c.colorIndex, vm::setColor)
            StepperRow("Shutter", c.shutterIndex, vm::setShutter)
            StepperRow("Capture mode", c.captureModeIndex, vm::setCaptureMode)
            val busy = vm.captureState?.state == 1
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = vm::capture, enabled = !busy) { Text("Capture") }
                if (busy) {
                    Text("Busy", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                }
                OutlinedButton(onClick = vm::refreshCamera) { Text("Refresh") }
            }
        }
    }
}

@Composable
private fun StepperRow(label: String, value: Int?, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$label: ${value?.toString() ?: "—"}", modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { if (value != null && value > 0) onChange(value - 1) else onChange(0) }) { Text("−") }
        OutlinedButton(onClick = { onChange((value ?: -1) + 1) }) { Text("+") }
    }
}

/**
 * Live MJPEG preview of the camera. Best-effort: a slow or absent
 * stream shows a "Stream unavailable" placeholder and never blocks
 * the control pane. The frame is decoded off the main thread by
 * AppViewModel; this composable just renders whatever's latest.
 */
@Composable
fun PreviewPane(vm: AppViewModel, modifier: Modifier = Modifier) {
    val frame = vm.previewFrame
    val state = vm.previewState
    Card(modifier = modifier.padding(8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Camera preview", style = MaterialTheme.typography.titleMedium)
            when {
                frame != null -> androidx.compose.foundation.Image(
                    bitmap = frame,
                    contentDescription = "Live camera preview",
                    modifier = Modifier.fillMaxWidth(),
                )
                // 3h-BUG: `state` is a StateFlow, not a State. The previous
                // check `state is PreviewController.State.Connecting` was
                // comparing a StateFlow to a State object — always false.
                // The branch never fired, so the user only ever saw the
                // generic "Stream unavailable" message while the transport
                // was still connecting. Use `.value` to read the current
                // state out of the flow.
                state.value is dev.openpolaris.core.domain.PreviewController.State.Connecting ->
                    Text("Connecting…", style = MaterialTheme.typography.bodyMedium)
                state.value is dev.openpolaris.core.domain.PreviewController.State.Error ->
                    Text(
                        "Stream unavailable: ${(state.value as dev.openpolaris.core.domain.PreviewController.State.Error).message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                else ->
                    Text("Stream unavailable — connect to the mount or check Wi-Fi.", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                // 3h-BUG: surface the live port (vm.port) instead of a
                // hard-coded 8080 so the user knows which port the preview
                // is actually using — matters when they entered a non-
                // default port in the reconnect dialog.
                "Streamed from http://${vm.host}:${vm.port}/?action=stream. 16:9, best-effort, frames are dropped when stale.",
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
 *    Android: ACTION_OPEN_DOCUMENT via FilePicker). The picked path is held
 *    by the VM so the file survives a recomposition.
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

    Card(modifier = modifier.padding(8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Firmware update", style = MaterialTheme.typography.headlineSmall)

            if (!featureEnabled) {
                Text(
                    "Firmware upload is disabled. Enable the 'firmwareUpload' flag in " +
                        "your config to use this pane. Firmware install is destructive — a " +
                        "bad image bricks the mount until you re-flash over USB.",
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

            // ---- Action ----------------------------------------------------
            Button(
                onClick = vm::uploadPickedFirmware,
                enabled = featureEnabled && !vm.firmwareBusy && vm.pickedFirmwarePath != null,
            ) { Text(if (vm.firmwareBusy) "Uploading…" else "Upload") }

            // ---- Progress + status ----------------------------------------
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
                        "Failed: ${s.reason}"
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
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
