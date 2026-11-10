package dev.openpolaris.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Keeps the SLOW surface truthful while the firmware transport is unqualified. */
class SlowMotionPaneSafetyContractTest {
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
    fun featureFlagCannotImplyLiveTransportQualification() {
        val source = sourceText()
        assertTrue("val liveTransportQualified = false" in source)
        assertTrue("val liveControlsEnabled = gateOpen && liveTransportQualified" in source)
        assertTrue("enabled = liveControlsEnabled" in source)
        assertFalse("enabled = gateOpen" in source)
        assertTrue("no 532/533/534" in source)
    }
}
