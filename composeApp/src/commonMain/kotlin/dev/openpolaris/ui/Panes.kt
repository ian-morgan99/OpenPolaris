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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
                Button(onClick = vm::runAutoLevel) { Text("Level now") }
                Switch(checked = vm.autoLevelEnabled == true, onCheckedChange = vm::setAutoLevelEnabled)
                Text(if (vm.autoLevelEnabled == true) "Enabled" else "Disabled / unknown")
                OutlinedButton(onClick = vm::refreshAutoLevel) { Text("Refresh") }
            }
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
