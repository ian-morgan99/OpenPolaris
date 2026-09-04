package dev.openpolaris.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextAlign
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
internal data class FlagSpec(
    val name: String,
    val label: String,
    val description: String,
    val safe: Boolean,
    val destructive: Boolean = false,
)

/**
 * Section in the Settings pane. Drives the visual grouping that separates
 * "things a user can flip without thinking" (DayToDay) from "advanced knobs
 * that may not be wired" (Advanced) from "things that can break the
 * gimbal if you're not careful" (Admin / destructive).
 */
internal enum class FlagSection(val title: String, val blurb: String) {
    DayToDay(
        "Day-to-day",
        "Settings a regular user can safely flip. Each row is a plain toggle.",
    ),
    Advanced(
        "Advanced",
        "Read-only knobs whose wire path is not yet verified. " +
            "Surfaced for visibility; flip them by editing the source config.",
    ),
    Admin(
        "Admin",
        "Destructive actions — reboot, shutdown, firmware upload, SD format. " +
            "Every change here asks for an explicit confirmation.",
    ),
}

/**
 * Returns true when the [FlagSpec] is interactive from the Settings pane.
 *
 * A spec is interactive if it is **safe** (a plain toggle, no further
 * confirmation) or **destructive** (a confirmation dialog gates the
 * change — see [DestructiveConfirmDialog]). A spec that is neither safe
 * nor destructive is read-only — surfaced for visibility but not
 * user-toggleable.
 *
 * Exposed `internal` (not `private`) so [FeatureFlagsEnabledPolicyTest]
 * can exercise the policy without a Compose test harness. The
 * `FlagSpec` data class itself stays private because it is a UI
 * catalog concern.
 */
internal fun isFlagSpecInteractive(spec: FlagSpec): Boolean =
    spec.safe || spec.destructive

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

    // ---- plate solving (network round-trip, unverified) ------------------
    FlagSpec("plateSolve", "Plate solve (nova.astrometry.net)",
        "Upload a captured JPEG to nova.astrometry.net for blind plate solving. Requires internet egress. Sends the picked image to a third-party service — review the privacy posture before enabling. OFF by default.", safe = false),

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

/**
 * Bucket a [FlagSpec] into the Settings section it belongs in.
 *
 *  - `destructive` flags go to [FlagSection.Admin] (reboot / shutdown / firmware / format).
 *  - `safe` flags (free toggle, no confirm) go to [FlagSection.DayToDay].
 *  - Everything else (`safe = false`, `destructive = false`) is "advanced
 *    experimental" — surfaced read-only under [FlagSection.Advanced].
 *
 * The policy is `internal` so [isFlagSpecInteractive] tests can also assert
 * the bucket assignment.
 */
internal fun sectionFor(spec: FlagSpec): FlagSection = when {
    spec.destructive -> FlagSection.Admin
    spec.safe -> FlagSection.DayToDay
    else -> FlagSection.Advanced
}

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
        // The switch is interactive when the spec is `safe` (read/write
        // toggle, no further confirmation) or `destructive` (a
        // confirmation dialog gates the change — see `DestructiveConfirmDialog`).
        // Unsafe non-destructive specs (e.g. raw frame logging) are still
        // surfaced for visibility but stay read-only until someone promotes
        // them to `safe` or `destructive` in [flagSpecs].
        // The policy itself lives in [isFlagSpecInteractive] so it can
        // be unit-tested without a Compose test harness.
        Switch(
            checked = isOn,
            onCheckedChange = onToggle,
            enabled = isFlagSpecInteractive(spec),
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

/**
 * Clickable section header for the Settings pane. Renders a small caret, the
 * section title, and a one-line blurb. The Admin section uses the error
 * colour so a user who scans down the page sees that the bottom group is
 * the dangerous one.
 */
@Composable
private fun SectionHeader(
    section: FlagSection,
    expanded: Boolean,
    onToggle: () -> Unit,
    rowCount: Int,
) {
    val titleColor = when (section) {
        FlagSection.Admin -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (expanded) "▾" else "▸",
                style = MaterialTheme.typography.titleSmall,
                color = titleColor,
            )
            Text(
                "${section.title} ($rowCount)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
            )
        }
        Text(
            section.blurb,
            style = MaterialTheme.typography.bodySmall,
        )
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
fun FeatureFlagsPaneContent(
    modifier: Modifier = Modifier,
    /**
     * Build identity to render at the bottom of the dialog (e.g.
     * "dev.openpolaris.app v0.1.6 (7)"). Set by the Android host from
     * BuildConfig; pass null on platforms that don't inject one. Helps
     * users confirm which build is running when triaging a crash report.
     */
    versionLabel: String? = null,
) {
    // Bump this counter whenever a flag flips so the rows re-read isEnabled
    // (isEnabled itself isn't backed by a snapshot, so we bridge through
    // a `mutableStateOf` to force recomposition).
    var revision by remember { mutableStateOf(0) }
    var pending by remember { mutableStateOf<Pair<FlagSpec, Boolean>?>(null) }
    // Admin section starts collapsed so a casual user never sees the
    // reboot / shutdown / firmware / format toggles unless they
    // explicitly expand the bottom group.
    var adminExpanded by remember { mutableStateOf(false) }
    var advancedExpanded by remember { mutableStateOf(true) }
    var dayToDayExpanded by remember { mutableStateOf(true) }

    val bySection = remember { flagSpecs.groupBy { sectionFor(it) } }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlagsHeader()
        // Read revision to subscribe the header to flag changes too.
        revision
        Spacer(Modifier.height(4.dp))
        HorizontalDivider()
        // No weight(1f, fill = true) here — it created a circular
        // measurement constraint that made the AlertDialog's `text`
        // slot measure at 284px regardless of the outer scroll
        // (v0.1.11 regression: all 25 flag rows were clipped to
        // invisible). The outer CalloutDialog owns the single
        // Modifier.verticalScroll — adding any weight inside it makes
        // the AlertDialog collapse the text slot to a tiny strip. Now
        // the flags + version label all live in a plain Column that
        // grows with its content; the outer scroll takes care of
        // overflow. The version label is no longer pinned, but it
        // scrolls with the list which is fine — the user can see it
        // when they reach the end, and it no longer blocks access to
        // any flag (notably firmwareUpload, which was unreachable
        // before this fix).
        listOf(
                FlagSection.DayToDay to dayToDayExpanded,
                FlagSection.Advanced to advancedExpanded,
                FlagSection.Admin to adminExpanded,
            ).forEach { (section, expanded) ->
                val rows = bySection[section].orEmpty()
                if (rows.isNotEmpty()) {
                    SectionHeader(
                        section = section,
                        expanded = expanded,
                        onToggle = {
                            when (section) {
                                FlagSection.DayToDay -> dayToDayExpanded = !dayToDayExpanded
                                FlagSection.Advanced -> advancedExpanded = !advancedExpanded
                                FlagSection.Admin -> adminExpanded = !adminExpanded
                            }
                        },
                        rowCount = rows.size,
                    )
                    if (expanded) {
                        rows.forEach { spec ->
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
                    HorizontalDivider()
                }
            }
        if (!versionLabel.isNullOrBlank()) {
            HorizontalDivider()
            Text(
                text = versionLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                textAlign = TextAlign.End,
            )
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
