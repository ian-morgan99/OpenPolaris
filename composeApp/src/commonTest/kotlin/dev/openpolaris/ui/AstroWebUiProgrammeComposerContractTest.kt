package dev.openpolaris.ui

import kotlin.test.Test
import kotlin.test.assertTrue

/** Protects the cross-platform Astro programme composer safety affordances. */
class AstroWebUiProgrammeComposerContractTest {
    private fun sourceText(): String {
        var directory: java.io.File? = java.io.File(".").canonicalFile
        repeat(8) {
            val candidate = directory?.let {
                java.io.File(it, "composeApp/src/commonMain/kotlin/dev/openpolaris/ui/Panes.kt")
            }
            if (candidate?.isFile == true) return candidate.readText(Charsets.UTF_8)
            directory = directory?.parentFile
        }
        error("Cannot read Panes.kt")
    }

    @Test
    fun composerKeepsPreviewAndStartAsSeparateActions() {
        val source = sourceText()
        assertTrue("AstroWebUiProgrammeComposer" in source)
        assertTrue("astroWebUiProgrammeStarts" in source)
        assertTrue("Preview and validation never start a run" in source)
        assertTrue("Tap again to start lapse" in source)
        assertTrue("previewAstroWebUiPanorama" in source)
        assertTrue("startAstroWebUiPanorama" in source)
        assertTrue("previewAstroWebUiHdr" in source)
        assertTrue("startAstroWebUiHdr" in source)
        assertTrue("validateAstroWebUiFocus" in source)
        assertTrue("startAstroWebUiFocus" in source)
        assertTrue("Returned frame:" in source)
        assertTrue("AstroCompactFieldPair" in source)
        assertTrue("Box(Modifier.weight(1f))" in source)
        assertTrue("H step deg" in source)
        assertTrue("contentDescription = fullLabel" in source)
    }

    @Test
    fun inHeadCaptureRemainsExplicitlyGatedAndStatusAuthoritative() {
        val source = sourceText()
        assertTrue("AstroWebUiAstroCaptureComposer" in source)
        assertTrue("astroWebUiAstroCapture" in source)
        assertTrue("/api/astro/capture" in source)
        assertTrue("resident 272 run" in source)
        assertTrue("externally triggered Astro capture can be unreliable" in source)
        assertTrue("Tap again to start in-head Astro capture" in source)
        assertTrue("delay(4_000L)" in source)
        assertTrue("resident programme status remains authoritative" in source)
    }
}
