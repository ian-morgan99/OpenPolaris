package dev.openpolaris.ui

import kotlin.test.Test
import kotlin.test.assertTrue

/** Pins the read-only evidence centre and its support-safe evidence boundaries. */
class EvidenceCentreContractTest {
    private fun source(path: String): String {
        var directory: java.io.File? = java.io.File(".").canonicalFile
        repeat(8) {
            val candidate = directory?.let { java.io.File(it, path) }
            if (candidate?.isFile == true) return candidate.readText(Charsets.UTF_8)
            directory = directory?.parentFile
        }
        error("Cannot read $path")
    }

    @Test
    fun evidenceCentreIsReadOnlyAndLabelsUnknownStates() {
        val panes = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/Panes.kt")
        val app = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/OpenPolarisApp.kt")
        assertTrue("fun EvidenceCentrePane" in panes)
        assertTrue("Read-only support view" in panes)
        assertTrue("Outcome unknown" in panes)
        assertTrue("captureTimeline" in panes)
        assertTrue("Save support bundle" in panes)
        assertTrue("Hostnames, credentials, raw frames and camera bytes are omitted." in panes)
        assertTrue("live transport remains gated" in panes)
        assertTrue("EvidenceCentrePane(" in app)
    }
}
