package dev.openpolaris.ui

import kotlin.test.Test
import kotlin.test.assertTrue

/** Keeps programme discovery read-only until the shared executor is wired to capture evidence. */
class ObservationProgrammePreviewContractTest {
    @Test
    fun cameraSurfaceExposesAReadOnlyPlanPreview() {
        var directory: java.io.File? = java.io.File(".").canonicalFile
        val source = (0 until 8).firstNotNullOfOrNull {
            val candidate = directory?.let { java.io.File(it, "composeApp/src/commonMain/kotlin/dev/openpolaris/ui/Panes.kt") }
            if (candidate?.isFile == true) candidate.readText() else {
                directory = directory?.parentFile
                null
            }
        } ?: error("Cannot read Panes.kt")
        assertTrue("ObservationProgrammePreviewSection" in source)
        assertTrue("Preview plan" in source)
        assertTrue("Run qualified lapse" in source)
        assertTrue("startObservationProgramme" in source)
        assertTrue("Read-only" in source)
        assertTrue("ObservationProgrammePlanner" in source)
        assertTrue("Previewing never moves the mount or fires the camera." in source)
    }
}
