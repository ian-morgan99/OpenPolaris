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

}
