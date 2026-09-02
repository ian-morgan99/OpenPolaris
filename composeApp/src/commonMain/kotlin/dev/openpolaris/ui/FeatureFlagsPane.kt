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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.openpolaris.core.config.FeatureFlags

/**
 * Catalog of every FeatureFlag with metadata for the runtime settings pane.
 *
 * - [safe]: true → user can toggle the switch in the Settings dialog directly.
 *           false → the row is read-only and the user must edit the source
 *           config (or a future "kiosk override" file) to flip it.
 * - [destructive]: true → the flag controls a destructive write path. Even
 *           when [safe] is true, flipping it requires an explicit confirm
 *           dialog because the action it enables is irreversible.
 *
 * The default is whatever the const val in [FeatureFlags] currently says;
 * [FeatureFlags.isEnabled] merges the override map on top.
 */
private data class FlagSpec(
    val name: String,
    val label: String,
    val description: String,
    val safe: Boolean,
    val destructive: Boolean = false,
)

/** All 25 flags, organised by category for the UI. The UI iterates these in
 *  declaration order; the section header is rendered whenever the category
 *  changes between adjacent rows. */
private val flagSpecs: List<FlagSpec> = listOf(
    // ---- core (always on, but listed for visibility) ---------------------
    FlagSpec("basicControls", "Slew / track / jog (513-516, 524-536)",
        "Slew, track, half-speed, AHRS, position-reset, jog pad. Always ON — turns off basic app use.", safe = true),
    FlagSpec("postConnectBurst", "Post-connect burst (808/809/802/…)",
        "Reads firmware, serial, WiFi band, SD, OMS, ex-axis, camera info, device info, temperature after connect.", safe = true),
    FlagSpec("experimentalCamera", "Camera 10-param steppers (258-311)",
        "Experimental GET/SET steppers. Behind a flag so the unsafe-codes banner can be hidden in test rigs.", safe = true),
    FlagSpec("catalog", "Tonight catalog (read-only)",
        "Star/planet/comet catalog queries. No wire side effect.", safe = true),
    FlagSpec("alignment", "Star alignment (530)",
        "Single-shot star alignment record (alignment record from the mount's plate-solving).", safe = true),

    // ---- advanced astro (verified, but configurable) --------------------
    FlagSpec("advancedAstro", "Dither + settling (539/540, 543/544)",
        "Dither on/off and settling-time read/write. Verified round-trip on the live burst.", safe = true),
    FlagSpec("deviceInfo", "Device-info pane",
        "Every read the post-connect burst pulls in. Read-only, no wire side effect. Hides the pane only; the burst is gated by postConnectBurst.", safe = true),
    FlagSpec("autoLevel", "Auto-level (547/548/549)",
        "Auto-level en/trigger and live tilt push. Verified on real hardware 2026-08-31 — see POLARIS-FUNCTIONS-REPORT.md §2.3.", safe = true),

    // ---- unverified write paths (safe=false) ----------------------------
    FlagSpec("limitsWrite", "Limits write (541/542)",
        "Wire format is a best-effort guess, NOT verified on real hardware. Keeps the read-only display visible.", safe = false),
    FlagSpec("timelapse", "Timelapse (258-262, 535)",
        "Timelapse + dynamic-lapse parameters. Not verified on real hardware yet.", safe = false),
    FlagSpec("ditherAdvanced", "Dither pattern parameters",
        "Advanced dither (separately from the dither on/off). Not verified on real hardware yet.", safe = false),
    FlagSpec("wifiConnect", "WiFi connect (772-773, 802)",
        "Unverified write path. Lets the app issue a connect/disconnect/set-band call to the mount.", safe = false),
    FlagSpec("omsScheduler", "OMS scheduler",
        "Add / edit / delete OMS scheduled tasks. Unverified write path.", safe = false),

    // ---- file manager (read vs mutate vs format) -------------------------
    FlagSpec("fileManager", "SD file list (702)",
        "Browse SD files. Verified read-only.", safe = true),
    FlagSpec("fileManagerReadOnly", "SD file browse (browse-only mode)",
        "Force the file manager into a read-only display. Always safe; can be combined with fileManager=true to browse without exposing buttons.", safe = true),
    FlagSpec("fileManagerMutate", "SD delete / rename / protect",
        "Mutate-file actions. Unverified write path.", safe = false),
    FlagSpec("fileManagerFormat", "SD format (707)",
        "Format the SD card. Destructive — requires explicit confirm.", safe = false, destructive = true),

    // ---- system / WiFi / firmware ---------------------------------------
    FlagSpec("systemSettings", "System settings: buzzer (817), LED (818)",
        "Verified writes: buzzer on/off, status LED on/off. Safe to flip — actions are reversible. See PROTOCOL-CODE-AUDIT-2026-08-31.md §4.6.", safe = true),
    FlagSpec("systemSettingsUnverified", "System settings: time (814), tz (815), language (816)",
        "Unverified writes. Wire payload contradicts the SYS_TIME / SYS_TIMEZONE / SYS_LANGUAGE name on every firmware we've captured. OFF by default — enable only when you intend to debug the protocol.", safe = false),
    FlagSpec("wifiScan", "WiFi scan / list (770-771)",
        "Read-only network scan. Verified.", safe = true),

    // ---- destructive (destructive=true) ---------------------------------
    FlagSpec("allowReboot", "Reboot (812 — UNVERIFIED)",
        "Sends a reboot command to the mount. Opcode 812 is decompile-classified as SP_GET_CELLULAR_IMEI (a query), so the wire mapping is uncertain. The on-board install auto-reboots on success. Destructive — requires explicit confirm.", safe = false, destructive = true),
    FlagSpec("allowShutdown", "Shutdown (813 — UNVERIFIED)",
        "Sends a shutdown command. Opcode 813 is decompile-classified as SP_SET_CELLULAR_COMUSB (a cellular USB-mode setter), so the wire mapping is uncertain. Destructive — requires explicit confirm.", safe = false, destructive = true),
    FlagSpec("firmwareUpload", "Firmware upload (810 / 784 / 794 / 795 / 811 / 812 — UNVERIFIED)",
        "Experimental 9090-side upload using opcodes 810→784→794→795→811→812. None of these opcodes are verified on production firmware: the live install path is SSH/scp to /app/sd/FwPkt.zip, not port 9090. See docs/FIRMWARE-UPLOAD-AUDIT-2026-09-01.md §2. An interrupted upload CAN BRICK the gimbal.", safe = false, destructive = true),

    // ---- diagnostic ------------------------------------------------------
    FlagSpec("rawFrameLog", "Frame raw dump",
        "Log every raw frame to console. Live debugging only.", safe = true),
    FlagSpec("verboseLogging", "Verbose status messages",
        "Log every code sent and every reply. Live debugging only.", safe = true),
    FlagSpec("demoMode", "Demo mode (in-process simulator)",
        "Show the in-process simulator button. Useful for UI development without a mount.", safe = true),
    FlagSpec("wifiBridge", "WiFi bridge bring-up button",
        "Show the BT-wake → NM-up bridge button.", safe = true),
)

/** A single row in the flags pane. */
@Composable
private fun FlagRow(spec: FlagSpec, isOn: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(spec.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (spec.destructive) {
                    Text("DESTRUCTIVE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                } else if (!spec.safe) {
                    Text("UNVERIFIED", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                }
            }
            Text(
                spec.description,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "code: ${spec.name}   effective: ${if (isOn) "ON" else "OFF"}",
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Switch(
            checked = isOn,
            onCheckedChange = onToggle,
            enabled = spec.safe,
        )
    }
}

/** Header bar with a single "Reset to compile defaults" button. */
@Composable
private fun FlagsHeader() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("Feature flags (runtime overrides)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { FeatureFlags.reset() }) { Text("Reset all") }
    }
}

/** Confirmation dialog for destructive flags. */
@Composable
private fun DestructiveConfirmDialog(
    spec: FlagSpec,
    target: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (target) "Enable ${spec.label}?" else "Disable ${spec.label}?") },
        text = {
            Text(
                if (target)
                    "This will let the app ${spec.description.lowercase()}. " +
                        "The action is irreversible. Continue?"
                else
                    "Disabling this flag will hide the controls in the app. " +
                        "You can re-enable it any time."
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(if (target) "Enable" else "Disable") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

/**
 * Top-level entry point for the settings pane.
 *
 * Two overloads:
 *  - [FeatureFlagsPane] wraps the content in a [Card]. Use this when rendering
 *    the pane as a free-standing surface (a full-screen page, an embed in a
 *    non-dialog container, etc.).
 *  - [FeatureFlagsPaneContent] is the bare column. Use this when embedding
 *    inside another scroller / dialog body (e.g. [CalloutDialog] already
 *    provides its own Card-style surface).
 *
 * Both share the same flag state via [FeatureFlags] so flipping a switch in
 * one is immediately visible in the other.
 */
@Composable
fun FeatureFlagsPane(modifier: Modifier = Modifier) {
    Card(modifier = modifier.padding(8.dp)) {
        FeatureFlagsPaneContent(Modifier.padding(16.dp))
    }
}

/**
 * Bare column of flag rows, suitable for embedding inside a [CalloutDialog].
 * Owns the destructive-confirm dialog state.
 */
@Composable
fun FeatureFlagsPaneContent(modifier: Modifier = Modifier) {
    // Bump this counter whenever a flag flips so the rows re-read isEnabled
    // (isEnabled itself isn't backed by a snapshot, so we bridge through
    // a `mutableStateOf` to force recomposition).
    var revision by remember { mutableStateOf(0) }
    var pending by remember { mutableStateOf<Pair<FlagSpec, Boolean>?>(null) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlagsHeader()
        // Read revision to subscribe the header to flag changes too.
        revision
        Spacer(Modifier.height(4.dp))
        HorizontalDivider()
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            flagSpecs.forEach { spec ->
                val on = FeatureFlags.isEnabled(spec.name).also { revision.let { _ -> } }
                FlagRow(
                    spec = spec,
                    isOn = on,
                    onToggle = { desired ->
                        if (spec.destructive) {
                            pending = spec to desired
                        } else if (spec.safe) {
                            if (desired) FeatureFlags.enable(spec.name) else FeatureFlags.disable(spec.name)
                            revision++
                        }
                        // unsafe (safe=false, destructive=false) → no-op
                    },
                )
            }
        }
    }

    pending?.let { (spec, desired) ->
        DestructiveConfirmDialog(
            spec = spec,
            target = desired,
            onConfirm = {
                if (desired) FeatureFlags.enable(spec.name) else FeatureFlags.disable(spec.name)
                revision++
                pending = null
            },
            onCancel = { pending = null },
        )
    }
}
