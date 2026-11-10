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
        assertFalse("Text(\"More\"" in shell)
    }
}
