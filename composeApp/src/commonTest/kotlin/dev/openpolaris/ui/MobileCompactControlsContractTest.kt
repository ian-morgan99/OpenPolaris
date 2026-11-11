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
}
