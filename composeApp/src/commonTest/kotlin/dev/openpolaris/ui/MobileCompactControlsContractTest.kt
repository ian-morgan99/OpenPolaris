package dev.openpolaris.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Prevents the phone shell from regressing to large text controls. */
class MobileCompactControlsContractTest {
    private fun source(relativePath: String): String {
        var directory: java.io.File? = java.io.File(".").canonicalFile
        repeat(8) {
            val candidate = directory?.let { java.io.File(it, relativePath) }
            if (candidate?.isFile == true) return candidate.readText(Charsets.UTF_8)
            directory = directory?.parentFile
        }
        error("Cannot read $relativePath")
    }

    @Test
    fun primaryPhoneControlsUseSmallGraphicsWithAccessibleHitTargets() {
        val graphics = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/CompactGraphics.kt")
        val shell = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/OpenPolarisApp.kt")
        val panes = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/Panes.kt")
        val theme = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/Theme.kt")

        assertTrue("6.dp else 20.dp" in graphics, "phone graphics must be visibly smaller than desktop graphics")
        assertTrue("compactActionTargetSize" in graphics)
        assertTrue("18.dp else 48.dp" in graphics, "phone action targets must use the micro footprint")
        assertTrue("16.dp else 32.dp" in graphics, "phone selector chips must use the micro footprint")
        assertTrue("LocalMinimumInteractiveComponentSize" in theme)
        assertTrue("if (compact) 18.dp else 48.dp" in theme)
        assertTrue("CompactFilterChip" in graphics, "text selectors must use the compact chip boundary")
        assertTrue("CompactRadioButton" in graphics, "radio indicators must use the compact boundary")
        assertTrue("Role.RadioButton" in graphics, "compact radio indicators must retain radio semantics")
        assertTrue("selectable(" in graphics, "compact radio indicators must remain keyboard/selectable controls")
        assertTrue("CompactTextButton" in graphics, "long text fallbacks must use the compact text boundary")
        assertTrue("CompactTextButton(" in panes, "alignment results must use the compact text fallback")
        assertTrue("graphic: OpenPolarisGraphic? = null" in graphics, "compact chips must support graphic-only phone rendering")
        assertTrue("contentDescription: String? = null" in graphics, "graphic-only chips must retain accessible names")
        assertTrue("compactLabel: String? = null" in graphics, "text selectors need a small phone label when no symbol exists")
        assertTrue("if (LocalOpenPolarisCompact.current)" in graphics, "all phone selectors must use the compact branch")
        assertTrue("Use Home Wi-Fi LAN connection" in panes, "compact aliases must keep full accessible names")
        assertTrue("CompactRadioButton(" in panes, "firmware delivery choices must use compact radio indicators")
        assertTrue("Use verified SSH pipe firmware delivery" in panes)
        assertTrue("OpenPolarisGraphic.Tracking" in shell)
        assertTrue("OpenPolarisGraphic.HalfSpeed" in shell)
        assertTrue("OpenPolarisGraphic.Ahrs" in shell)
        assertTrue("OpenPolarisGraphic.Fine" in shell)
        assertTrue("CompactGraphicButton" in shell)
        assertTrue("OpenPolarisGraphicIcon" in shell)
        assertTrue("callout.graphic" in shell, "overflow actions must retain their graphics")
        assertTrue("graphic: OpenPolarisGraphic? = null" in panes)
        assertTrue("contentDescription = \"\$label. Hold to move; release to stop.\"" in panes)
        assertTrue("OpenPolarisGraphic.Up" in panes)
        assertTrue("DirectionA" in graphics && "DirectionB" in graphics)
        assertTrue("OpenPolarisGraphic.Stop" in graphics)
        assertTrue("graphic = OpenPolarisGraphic.DirectionA" in panes)
        assertTrue("graphic = OpenPolarisGraphic.DirectionB" in panes)
        assertTrue("contentDescription = \"STOP ALL motion\"" in panes)
        assertFalse("Text(\"More\"" in shell)
    }

    @Test
    fun slowMotionSurfaceExposesCommandAgeAndPositiveAcknowledgementEvidence() {
        val vm = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/AppViewModel.kt")
        val panes = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/Panes.kt")
        val controller = source("shared/src/commonMain/kotlin/dev/openpolaris/core/domain/SlowMotionController.kt")

        assertTrue("slowMotionTelemetry" in vm)
        assertTrue("lastPositiveAcknowledgementAtMs" in controller)
        assertTrue("SlowMotionTelemetryEventKind" in controller)
        assertTrue("slow-motion-acknowledgement" in panes)
        assertTrue("slow-motion-event-toggle" in panes)
        assertTrue("slow-motion-event-timeline" in panes)
        assertTrue("Do not retry; recover explicitly." in panes)
    }

    @Test
    fun compactCameraAndFineRateActionsRetainAccessibleLabels() {
        val graphics = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/CompactGraphics.kt")
        val panes = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/Panes.kt")

        assertTrue("OpenPolarisGraphic.Refresh" in graphics)
        assertTrue("OpenPolarisGraphic.Plus" in graphics)
        assertTrue("OpenPolarisGraphic.Minus" in graphics)
        assertTrue("private fun CameraActionButton" in panes)
        assertTrue("if (LocalOpenPolarisCompact.current)" in panes)
        assertTrue("contentDescription = \"Capture photo\"" in panes)
        assertTrue("contentDescription = \"Refresh camera state\"" in panes)
        assertTrue("contentDescription = \"Start Bulb capture" in panes)
        assertTrue("contentDescription = \"Stop Bulb capture\"" in panes)
        assertTrue("contentDescription = \"Move focus nearer\"" in panes)
        assertTrue("contentDescription = \"Move focus farther\"" in panes)
        assertTrue("contentDescription = \"Start video recording\"" in panes)
        assertTrue("contentDescription = \"Stop video recording\"" in panes)
        assertTrue("Fine step: A +0.1°/s" in panes)
        assertTrue("graphic = OpenPolarisGraphic.DirectionA" in panes)
        assertTrue("graphic = OpenPolarisGraphic.DirectionB" in panes)
        assertTrue("graphic = OpenPolarisGraphic.Stop" in panes)
        assertTrue("camera-refresh-attachment" in panes)
        assertTrue("camera-auxiliary-readback" in panes)
        assertTrue("camera-arm-settings" in panes)
        assertTrue("contentDescription = \"Capture Astro WebUI manual exposure\"" in panes)
        assertTrue("contentDescription = \"Start Astro WebUI Bulb exposure\"" in panes)
        assertTrue("contentDescription = \"Stop Astro WebUI Bulb exposure\"" in panes)
        assertTrue("graphic = OpenPolarisGraphic.Preview" in panes)
        assertTrue("Review the last capture in Gallery" in panes)
    }

    @Test
    fun statusAndSlowMotionActionsUseCompactGraphicsWithoutLosingDesktopLabels() {
        val graphics = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/CompactGraphics.kt")
        val shell = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/OpenPolarisApp.kt")
        val panes = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/Panes.kt")

        assertTrue("internal fun ResponsiveActionButton" in graphics)
        assertTrue("wideModifier: Modifier = Modifier" in graphics)
        assertTrue("modifier.then(wideModifier)" in graphics)
        assertTrue("OpenPolarisGraphic.Apply" in graphics)
        assertTrue("OpenPolarisGraphic.Cancel" in graphics)
        assertTrue("graphic = OpenPolarisGraphic.Guide" in shell)
        assertTrue("graphic = OpenPolarisGraphic.Preview" in shell)
        assertTrue("graphic = OpenPolarisGraphic.Tonight" in shell)
        assertTrue("graphic = OpenPolarisGraphic.Stop" in shell)
        assertTrue("contentDescription = \"STOP ALL motion\"" in shell)
        assertTrue("graphic = OpenPolarisGraphic.Refresh" in panes)
        assertTrue("\"Show keyboard shortcuts\"" in panes)
        assertTrue("fine-correction-unavailable" in panes)
        assertTrue("contentDescription = \"Prepare synthetic plate solve evidence for the fine correction demo\"" in panes)
        assertTrue("graphic = OpenPolarisGraphic.Apply" in panes)
        assertTrue("graphic = OpenPolarisGraphic.Cancel" in panes)
        assertTrue("wideModifier = Modifier.fillMaxWidth()" in panes)
        assertTrue("wideModifier = Modifier.weight(1f)" in panes)
        assertFalse("modifier = Modifier.fillMaxWidth().testTag(\"slow-motion-recover\")" in panes)
        assertFalse("modifier = Modifier.weight(1f).testTag(\"fine-rate-demo-apply\")" in panes)
        assertTrue("Text(label)" in graphics)
    }

    @Test
    fun gotoAlignmentAndRecoveryActionsUseCompactGraphics() {
        val panes = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/Panes.kt")

        assertTrue("contentDescription = \"Slew to the entered coordinates\"" in panes)
        assertTrue("contentDescription = \"Cancel the active slew\"" in panes)
        assertTrue("contentDescription = \"Reset the mount position\"" in panes)
        assertTrue("contentDescription = \"Solve the current preview frame\"" in panes)
        assertTrue("contentDescription = \"Record the centered alignment star\"" in panes)
        assertTrue("contentDescription = \"Run auto-level\"" in panes)
        assertTrue("contentDescription = \"Refresh auto-level status\"" in panes)
        assertTrue("contentDescription = \"Recover fast jog after confirming the mount is stopped\"" in panes)
        assertTrue("goto-slew" in panes)
        assertTrue("alignment-record-star" in panes)
        assertFalse("Button(onClick = vm::goto)" in panes)
    }

    @Test
    fun astroGalleryActionsUseCompactGraphicsWithExplicitMediaLabels() {
        val graphics = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/CompactGraphics.kt")
        val panes = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/FullControlPanes.kt")

        assertTrue("OpenPolarisGraphic.Save" in graphics)
        assertTrue("OpenPolarisGraphic.Share" in graphics)
        assertTrue("contentDescription = \"Refresh Astro WebUI gallery\"" in panes)
        assertTrue("contentDescription = \"Refresh Astro WebUI status\"" in panes)
        assertTrue("contentDescription = \"Load thumbnail for \${item.path}\"" in panes)
        assertTrue("contentDescription = \"Load full file for \${item.path}\"" in panes)
        assertTrue("contentDescription = \"Save a copy of the Astro WebUI preview\"" in panes)
        assertTrue("contentDescription = \"Share the Astro WebUI preview\"" in panes)
        assertTrue("graphic = OpenPolarisGraphic.Preview" in panes)
        assertTrue("graphic = OpenPolarisGraphic.Camera" in panes)
        assertTrue("graphic = OpenPolarisGraphic.Save" in panes)
        assertTrue("graphic = OpenPolarisGraphic.Share" in panes)
        assertTrue("Text(\"All (\${allItems.size})\")" in panes)
    }

    @Test
    fun mediaAndCoordinateSelectorsUseSmallGraphicsWhenLabelsDoNotFit() {
        val panes = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/Panes.kt")
        val full = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/FullControlPanes.kt")

        assertTrue("contentDescription = \"Use Azimuth and Altitude coordinates\"" in panes)
        assertTrue("contentDescription = \"Use Right Ascension and Declination coordinates\"" in panes)
        assertTrue("graphic = OpenPolarisGraphic.DirectionA" in panes)
        assertTrue("graphic = OpenPolarisGraphic.DirectionB" in panes)
        assertTrue("contentDescription = \"Show all Astro WebUI media\"" in full)
        assertTrue("categoryLabel} Astro WebUI media" in full)
        assertTrue("label} files" in full)
        assertTrue("graphic = if (type == 0) OpenPolarisGraphic.Preview else OpenPolarisGraphic.Camera" in full)
        assertTrue("CompactFilterChip(" in full)
    }

    @Test
    fun singleLinePhoneFieldsUseTheCompactSizingBoundary() {
        val graphics = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/CompactGraphics.kt")
        assertTrue("internal fun CompactOutlinedTextField(" in graphics)
        assertTrue("heightIn(max = 40.dp)" in graphics)
        assertTrue("supportingText == null" in graphics)
        for (path in listOf(
            "composeApp/src/commonMain/kotlin/dev/openpolaris/ui/Panes.kt",
            "composeApp/src/commonMain/kotlin/dev/openpolaris/ui/FullControlPanes.kt",
            "composeApp/src/commonMain/kotlin/dev/openpolaris/ui/TonightPane.kt",
            "composeApp/src/commonMain/kotlin/dev/openpolaris/ui/ReconnectDialog.kt",
        )) {
            assertFalse(Regex("""(?m)^\s+OutlinedTextField\(""").containsMatchIn(source(path)), "$path must use CompactOutlinedTextField")
        }
    }

    @Test
    fun fileListPagingAndMutationActionsUseCompactGraphics() {
        val graphics = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/CompactGraphics.kt")
        val panes = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/FullControlPanes.kt")

        assertTrue("OpenPolarisGraphic.Protect" in graphics)
        assertTrue("OpenPolarisGraphic.Unprotect" in graphics)
        assertTrue("OpenPolarisGraphic.Delete" in graphics)
        assertTrue("contentDescription = \"Refresh SD card file list\"" in panes)
        assertTrue("contentDescription = \"Previous file page\"" in panes)
        assertTrue("contentDescription = \"Next file page\"" in panes)
        assertTrue("contentDescription = if (file.prot == 0) \"Protect file \${file.name}\"" in panes)
        assertTrue("contentDescription = \"Delete file \${file.name}\"" in panes)
        assertTrue("graphic = OpenPolarisGraphic.Left" in panes)
        assertTrue("graphic = OpenPolarisGraphic.Right" in panes)
        assertTrue("Format SD card" in panes)
    }

    @Test
    fun destructiveSystemTriggersUseCompactGraphicsWithExplicitSafetyDescriptions() {
        val panes = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/FullControlPanes.kt")

        assertTrue("contentDescription = \"Open the reboot mount confirmation\"" in panes)
        assertTrue("contentDescription = \"Open the shutdown mount confirmation\"" in panes)
        assertTrue("contentDescription = \"Open the format SD card confirmation\"" in panes)
        assertTrue("graphic = OpenPolarisGraphic.Refresh" in panes)
        assertTrue("graphic = OpenPolarisGraphic.Stop" in panes)
        assertTrue("graphic = OpenPolarisGraphic.Delete" in panes)
        assertFalse("Button(onClick = { dialog = true })" in panes)
        assertTrue("Text(\"Reboot\")" in panes)
        assertTrue("Text(\"Shutdown\")" in panes)
        assertTrue("Text(\"Format\")" in panes)
    }

    @Test
    fun helperPaneActionsUseCompactGraphicsWithAccessibleDescriptions() {
        val panes = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/FullControlPanes.kt")

        assertTrue("contentDescription = \"Refresh helper state\"" in panes)
        assertTrue("contentDescription = \"Refresh settling time\"" in panes)
        assertTrue("contentDescription = \"Apply settling time\"" in panes)
        assertTrue("contentDescription = \"Run auto-level\"" in panes)
        assertTrue("contentDescription = \"Refresh auto-level state\"" in panes)
        assertTrue("graphic = OpenPolarisGraphic.Apply" in panes)
        assertTrue("graphic = OpenPolarisGraphic.Helpers" in panes)
        assertFalse("OutlinedButton(onClick = vm::refreshHelpers)" in panes)
        assertFalse("OutlinedButton(onClick = vm::refreshSettling)" in panes)
        assertFalse("OutlinedButton(onClick = vm::refreshAutoLevel)" in panes)
    }

    @Test
    fun systemPaneActionsUseCompactGraphicsWithSafetyLabels() {
        val graphics = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/CompactGraphics.kt")
        val panes = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/FullControlPanes.kt")

        assertTrue("OpenPolarisGraphic.Clock" in graphics)
        assertTrue("contentDescription = \"Set mount system time\"" in panes)
        assertTrue("contentDescription = \"Use the current system time\"" in panes)
        assertTrue("contentDescription = \"Set mount timezone offset\"" in panes)
        assertTrue("contentDescription = \"Set mount language\"" in panes)
        assertTrue("contentDescription = \"Scan mount WiFi bands\"" in panes)
        assertTrue("graphic = OpenPolarisGraphic.Clock" in panes)
        assertTrue("graphic = OpenPolarisGraphic.Apply" in panes)
        assertTrue("label = \"Reboot mount\"" in panes)
        assertTrue("label = \"Shutdown mount\"" in panes)
    }

    @Test
    fun connectionTonightAndAstroHelpersUseCompactGraphics() {
        val panes = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/Panes.kt")
        val tonight = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/TonightPane.kt")
        val flags = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/FeatureFlagsPane.kt")

        assertTrue("contentDescription = \"Connect to mount\"" in panes)
        assertTrue("contentDescription = \"Wake mount over Bluetooth\"" in panes)
        assertTrue("contentDescription = \"Find and wake a Polaris mount\"" in panes)
        assertTrue("contentDescription = \"Save support bundle\"" in panes)
        assertTrue("contentDescription = \"Refresh resident Astro WebUI status\"" in panes)
        assertTrue("contentDescription = \"Refresh resident Astro solve state\"" in panes)
        assertTrue("contentDescription = \"Save the observing site to Astro WebUI\"" in panes)
        assertTrue("contentDescription = \"Set the resident panorama interval\"" in panes)
        assertTrue("graphic = OpenPolarisGraphic.Connection" in panes)
        assertTrue("graphic = OpenPolarisGraphic.Refresh" in panes)
        assertTrue("graphic = OpenPolarisGraphic.Save" in panes)

        assertTrue("ResponsiveActionButton" in tonight)
        assertTrue("contentDescription = \"Slew to comet \${c.designation}\"" in tonight)
        assertTrue("contentDescription = \"Slew to \${t.designation}\"" in tonight)
        assertTrue("contentDescription = \"Slew to \${o.designation}\"" in tonight)
        assertTrue("contentDescription = \"Slew to \${planet.name}\"" in tonight)

        assertTrue("contentDescription = \"Reset all feature flags\"" in flags)
        assertTrue("graphic = OpenPolarisGraphic.Refresh" in flags)
        assertFalse("OutlinedButton(onClick = { FeatureFlags.reset() })" in flags)
    }


    @Test
    fun astroComposerHelpersUseCompactGraphicsWithSafetyTextPreserved() {
        val panes = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/Panes.kt")

        assertTrue("contentDescription = \"Set the solar window to now plus 30 minutes\"" in panes)
        assertTrue("contentDescription = \"Capture the current live pose as a PLC key\"" in panes)
        assertTrue("contentDescription = \"Clear all PLC keyframes\"" in panes)
        assertTrue("contentDescription = \"Capture the current pose as a path-lapse waypoint\"" in panes)
        assertTrue("contentDescription = \"Clear all path-lapse waypoints\"" in panes)
        assertTrue("graphic = OpenPolarisGraphic.Clock" in panes)
        assertTrue("graphic = OpenPolarisGraphic.Camera" in panes)
        assertTrue("graphic = OpenPolarisGraphic.Cancel" in panes)
        assertTrue("this may move/fire" in panes)
        assertFalse("OutlinedButton(onClick = { vm.clearAstroWebUiPlcKeys() })" in panes)
        assertFalse("OutlinedButton(onClick = { vm.clearAstroWebUiPathWaypoints() })" in panes)
    }

    @Test
    fun astroReadbackActionsUseCompactGraphicsWithoutHidingSafetyState() {
        val panes = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/Panes.kt")

        assertTrue("contentDescription = \"Refresh Astro travel-limit state\"" in panes)
        assertTrue("contentDescription = \"Refresh resident Astro guiding state\"" in panes)
        assertTrue("contentDescription = \"Refresh resident Astro autofocus state\"" in panes)
        assertTrue("contentDescription = \"Refresh Astro run state\"" in panes)
        assertTrue("contentDescription = \"Read the resident Astro mount position\"" in panes)
        assertTrue("graphic = OpenPolarisGraphic.Slew" in panes)
        assertTrue("RELEASED — physical clearance must be watched" in panes)
        assertTrue("Mount position read was blocked by the resident WebUI safety check." in panes)
        assertFalse("Button(\n                    enabled = FeatureFlags.isEnabled(\"astroWebUiLimitsStatus\"),\n                    onClick = vm::refreshAstroWebUiLimits" in panes)
        assertFalse("Button(\n                    enabled = FeatureFlags.isEnabled(\"astroWebUiRunState\"),\n                    onClick = vm::refreshAstroWebUiRunState" in panes)
    }


    @Test
    fun astroMissionAndProgrammeActionsUseCompactGraphics() {
        val panes = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/Panes.kt")

        assertTrue("contentDescription = \"Solve the latest Astro frame\"" in panes)
        assertTrue("contentDescription = \"Confirm and start the Astro GoTo\"" in panes)
        assertTrue("contentDescription = \"Start the resident Astro autofocus sweep\"" in panes)
        assertTrue("contentDescription = \"Enter Astro mode\"" in panes)
        assertTrue("contentDescription = if (observedAstroStatus?.trackRequested == true)" in panes)
        assertTrue("contentDescription = \"Stop the resident Astro programme\"" in panes)
        assertTrue("contentDescription = if (armed) \"Confirm and start in-head Astro capture\"" in panes)
        assertTrue("contentDescription = solarActionLabel" in panes)
        assertTrue("contentDescription = plcActionLabel" in panes)
        assertTrue("contentDescription = pathActionLabel" in panes)
        assertTrue("contentDescription = grailActionLabel" in panes)
        assertTrue("contentDescription = \"Start the intervalometer sequence\"" in panes)
        assertTrue("graphic = OpenPolarisGraphic.Camera" in panes)
        assertTrue("graphic = OpenPolarisGraphic.Preview" in panes)
        assertTrue("graphic = OpenPolarisGraphic.Stop" in panes)
        assertTrue("graphic = OpenPolarisGraphic.Apply" in panes)
        assertFalse("Text(\"Solve latest\")" in panes)
        assertFalse("Text(\"Start sweep\")" in panes)
        assertFalse("Text(\"Enter Astro\")" in panes)
    }


    @Test
    fun secondaryTextActionsCollapseToGraphicsWithoutLosingNames() {
        val graphics = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/CompactGraphics.kt")
        val panes = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/Panes.kt")

        assertTrue("internal fun ResponsiveTextActionButton" in graphics)
        assertTrue("TextButton(" in graphics)
        assertTrue("OpenPolarisGraphic.Back" in graphics)
        assertTrue("OpenPolarisGraphic.Forward" in graphics)
        assertTrue("firmware-pick" in panes)
        assertTrue("firmware-clear" in panes)
        assertTrue("firmware-upload" in panes)
        assertTrue("firmware-back" in panes)
        assertTrue("firmware-next" in panes)
        assertTrue("Pick firmware ZIP" in panes)
        assertTrue("Upload selected firmware" in panes)
        assertTrue("Open connection settings" in panes)
    }


    @Test
    fun reconnectDialogCompactsProgressAndReconnectWithoutHidingForget() {
        val dialog = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/ReconnectDialog.kt")

        assertTrue("ResponsiveTextActionButton" in dialog)
        assertTrue("graphic = OpenPolarisGraphic.Cancel" in dialog)
        assertTrue("contentDescription = \"Cancel reconnect\"" in dialog)
        assertTrue("graphic = OpenPolarisGraphic.Connection" in dialog)
        assertTrue("contentDescription = \"Reconnect to last mount\"" in dialog)
        assertTrue("Text(\"Forget\")" in dialog, "irreversible marker removal remains explicitly textual")
    }

    @Test
    fun compactAstroMissionKeepsCoreStatusAndDisclosesAdvancedControls() {
        val panes = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/Panes.kt")

        assertTrue("val compactLayout = LocalOpenPolarisCompact.current" in panes)
        assertTrue("var showAdvancedAstro by remember(compactLayout)" in panes)
        assertTrue("testTag(\"astro-advanced-controls-toggle\")" in panes)
        assertTrue("label = if (showAdvancedAstro) \"Hide advanced\" else \"More controls\"" in panes)
        assertTrue("Show advanced Astro controls" in panes)
        assertTrue("if (!compactLayout || showAdvancedAstro)" in panes)
        assertTrue("Text(\"Resident Astro WebUI\"" in panes)
        assertTrue("Text(\"Prepare checklist\"" in panes)
    }


    @Test
    fun remainingSecondaryActionsCollapseToGraphicsOnPhones() {
        val panes = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/Panes.kt")
        val fullPanes = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/FullControlPanes.kt")

        assertTrue("graphic = OpenPolarisGraphic.Forward" in panes)
        assertTrue("contentDescription = \"Next: \$actionLabel\"" in panes)
        assertFalse("TextButton(onClick = { onAction(stage.stage.actionId) })" in panes)
        assertFalse("TextButton(onClick = { onAction(card.id) })" in panes)
        assertTrue("contentDescription = \"Connect to a mount Wi-Fi network\"" in fullPanes)
        assertTrue("contentDescription = \"Disconnect mount Wi-Fi\"" in fullPanes)
        assertFalse("OutlinedButton(onClick = { vm.connectWifiSsid(\"\") })" in fullPanes)
        assertFalse("OutlinedButton(onClick = vm::disconnectWifi)" in fullPanes)
    }

    @Test
    fun compactMissionReadinessActionUsesGraphicFirstBoundary() {
        val shell = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/OpenPolarisApp.kt")

        assertTrue("graphic = OpenPolarisGraphic.Forward" in shell)
        assertTrue("contentDescription = \"Next: \$action\"" in shell)
        assertFalse("TextButton(\n                                onClick = { open(next.id) }" in shell)
    }

    @Test
    fun compactNavigationAndCalloutActionsAvoidMaterialTextButtonChrome() {
        val shell = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/OpenPolarisApp.kt")
        val noComments = Regex("""/\*[\s\S]*?\*/""").replace(shell, "")
            .lines().map { it.substringBefore("//") }.joinToString("\n")
        val navigation = Regex("""private fun CompactDestinationBar[\s\S]*?private fun PrimaryDestinationRail""")
            .find(noComments)?.value ?: error("Cannot locate compact destination bar")
        assertTrue("CompactGraphicTabButton(" in navigation)
        assertFalse("TextButton(" in navigation, "phone destination tabs must not inherit Material TextButton chrome")
        assertTrue("CompactOverflowMenuItem(" in navigation, "phone overflow rows must use the compact menu boundary")
        assertTrue(".height(26.dp)" in noComments, "phone overflow rows must avoid the stock 48 dp menu row")
        assertTrue("contentDescription = callout.accessibilityLabel" in noComments)
        assertTrue("ResponsiveTextActionButton(" in noComments)
        assertTrue("graphic = OpenPolarisGraphic.Back" in noComments)
    }
}
