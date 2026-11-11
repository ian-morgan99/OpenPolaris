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
        assertTrue("val liveTransportQualified = capabilityRecord.liveReady" in source)
        assertTrue("val liveTransportInstalled = vm.slowMotionTransportInstalled" in source)
        assertTrue("val liveControlsEnabled = gateOpen && liveTransportQualified && liveTransportInstalled" in source)
        assertTrue("val controlsEnabled = (liveControlsEnabled || demoControlsEnabled)" in source)
        assertTrue("enabled = controlsEnabled" in source)
        assertTrue("enabled = controlsEnabled && demoControlsEnabled" !in source)
        assertTrue("startHeldMotion" in source)
        assertTrue("awaitEachGesture" in source)
        assertTrue("stopHeldMotion" in source)
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
        assertTrue("changedToUpIgnoreConsumed" in source)
        assertTrue("if (!released) suppressNextClick = false" in source)
        assertTrue("HOLDING" in source)
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
        assertTrue("demoFineRateApplyTargets" in source)
        assertTrue("fine-rate-demo-target-row" in source)
        assertTrue("fine-rate-demo-m1" in source)
        assertTrue("fine-rate-demo-m2" in source)
        assertTrue("fine-rate-demo-m3" in source)
        assertTrue("fine-rate-demo-apply" in source)
        assertTrue("fine-rate-demo-cancel" in source)
        assertTrue("Draft targets" in source)
        assertTrue("onValueChange = { m1PanDraft = it }" in source)
        assertTrue("var heldControl by remember" in source)
        assertTrue("val motionSelectionEnabled = controlsEnabled && heldControl == null" in source)
        assertTrue("slow-motion-held-state" in source)
        assertTrue("slow-motion-direction-a" in source)
        assertTrue("slow-motion-direction-b" in source)
        assertTrue("slow-motion-stop-all" in source)
        assertTrue("heldControl = null" in source)
        assertTrue("startHeldMotion" in source)
        assertTrue("stopHeldMotion" in source)
    }

    @Test
    fun compactMotionKeepsCoreControlsAndDisclosesVerboseDetails() {
        val source = sourceText()
        assertTrue("var showMotionDetails by remember(compactLayout)" in source)
        assertTrue("testTag(\"slow-motion-details-toggle\")" in source)
        assertTrue("label = if (showMotionDetails) \"Hide details\" else \"Details\"" in source)
        assertTrue("Show slow-motion qualification details" in source)
        assertTrue("if (!compactLayout || showMotionDetails)" in source)
        assertTrue("testTag(\"slow-motion-direction-a\")" in source)
        assertTrue("testTag(\"slow-motion-stop-all\")" in source)
        assertTrue("testTag(\"slow-motion-speed-row\")" in source)
        assertTrue("val compactSlowStatus" in source)
        assertTrue("val compactAcknowledgementText" in source)
        assertTrue("Live unavailable · \${liveQualificationBlockers.size} checks" in source)
        assertTrue("Fine correction demo · open Details" in source)
        assertTrue("maxLines = if (compactLayout) 1 else Int.MAX_VALUE" in source)
    }

    @Test
    fun mobileMotionSelectorsRemainScrollable() {
        val source = sourceText()
        assertTrue("horizontalScroll(rememberScrollState())" in source)
        assertTrue("testTag(\"slow-motion-axis-row\")" in source)
        assertTrue("testTag(\"slow-motion-speed-row\")" in source)
    }

}
