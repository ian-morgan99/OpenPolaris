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
        assertTrue("val controlsEnabled = liveControlsEnabled || demoControlsEnabled" in source)
        assertTrue("enabled = controlsEnabled" in source)
        assertTrue("onPress = { vm.demoSlowMotionStart" in source)
        assertTrue("awaitEachGesture" in source)
        assertTrue("onRelease = vm::demoSlowMotionStop" in source)
        assertTrue("Hold to move; release to stop" in source)
        assertTrue(".focusable(enabled)" in source)
        assertTrue("onFocusChanged" in source)
        assertTrue("if (holdActive)" in source)
        assertFalse("enabled = gateOpen" in source)
        assertTrue("no 532/533/534" in source)
        assertTrue("STOP ALL" in source)
        assertTrue("Logical scheduler trace" in source)
        assertTrue("Pointer capture, dead-man renewal" in source)
        assertTrue("Keyboard shortcuts" in source)
        assertTrue("slow-motion-shortcuts-help" in source)
        assertTrue("stateDescription = if (holdActive)" in source)
        assertTrue("role = Role.Button" in source)
        assertTrue("never creates a repeat timer" in source)
        assertFalse("LaunchedEffect(vm.demoMode, vm.slowMotionDemoState.running)" in source)
        assertFalse("vm.demoSlowMotionTick()" in source)
        assertTrue("Fine correction — unavailable" in source)
        assertTrue("fine-rate-readiness" in source)
        assertTrue("Fine correction readiness" in source)
        assertTrue("Fresh plate solve (under 5 seconds)" in source)
        assertTrue("Solve confidence at least 0.60" in source)
        assertTrue("Field rotation within 5 degrees" in source)
        assertTrue("live rate transport is disabled in this build" in source)
        assertTrue("safety-policy defaults" in source)
        assertTrue("fine-rate-demo" in source)
        assertTrue("Prepare synthetic solve for demo" in source)
        assertTrue("shared TRACKING_CORRECTION owner" in source)
        assertTrue("no hardware command" in source)
        assertTrue("fine-rate-demo-stop" in source)
    }
}
