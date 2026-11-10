package dev.openpolaris.ui

import kotlin.test.Test
import kotlin.test.assertTrue

/** Keeps ambiguous shutter outcomes from presenting a retry affordance. */
class CameraCaptureSafetyContractTest {
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
    fun unknownCaptureOutcomeLocksAllReleaseSurfaces() {
        val source = sourceText()
        assertTrue("val captureOutcomeUnknown = phase is" in source)
        assertTrue("val captureLocked = inFlight || captureOutcomeUnknown" in source)
        assertTrue("enabled = !captureLocked && attachment?.isAttached == true" in source)
        assertTrue("Do not retry the shutter" in source)
        assertTrue("capture-outcome-unknown" in source)
        assertTrue("enabled = !captureLocked && attachment?.isAttached == true && releaseMode == CameraReleaseMode.BULB" in source)
        assertTrue("val captureOutcomeUnknown = vm.capturePhase is AppViewModel.CapturePhase.OutcomeUnknown" in source)
        assertTrue("val canStart = !active && !outcomeUnknown && !captureOutcomeUnknown" in source)
        assertTrue("A single shutter outcome is unknown; reconnect before starting a sequence." in source)
    }
}
