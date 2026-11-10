package dev.openpolaris.ui

import kotlin.test.Test
import kotlin.test.assertTrue

/** Presentation-only Astro WebUI parity controls must remain local to Preview. */
class PreviewOverlayContractTest {
    @Test
    fun previewHasLocalFramingOverlaysAndHonestAnalysisGate() {
        var directory: java.io.File? = java.io.File(".").canonicalFile
        val source = (0 until 8).firstNotNullOfOrNull {
            val candidate = directory?.let { java.io.File(it, "composeApp/src/commonMain/kotlin/dev/openpolaris/ui/Panes.kt") }
            if (candidate?.isFile == true) candidate.readText() else {
                directory = directory?.parentFile
                null
            }
        } ?: error("Cannot read Panes.kt")
        assertTrue("var framingGrid by remember" in source)
        assertTrue("var centreMark by remember" in source)
        assertTrue("Canvas(Modifier.fillMaxSize())" in source)
        assertTrue("Framing overlays and pixel analysis are local presentation aids" in source)
        assertTrue("Toggle preview histogram" in source)
        assertTrue("Toggle preview focus peaking" in source)
        assertTrue("do not alter camera or mount state" in source)
        assertTrue("Preview endpoint is open but no frames arrived" in source)
        assertTrue("Frame age: under 1 s" in source)
    }
}
