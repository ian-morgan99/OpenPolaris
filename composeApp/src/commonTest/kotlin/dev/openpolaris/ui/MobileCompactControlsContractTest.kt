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

        assertTrue("Canvas(modifier.size(20.dp))" in graphics)
        assertTrue(".size(48.dp)" in graphics)
        assertTrue("CompactGraphicButton" in shell)
        assertTrue("OpenPolarisGraphicIcon" in shell)
        assertTrue("leadingIcon = { OpenPolarisGraphicIcon(c.graphic) }" in shell)
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
        assertTrue("Text(\"Reboot mount\")" in panes)
        assertTrue("Text(\"Shutdown mount\")" in panes)
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

}
