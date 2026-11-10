package dev.openpolaris.ui

import kotlin.test.Test
import kotlin.test.assertTrue

/** Keeps the highest-priority stop action reachable from every responsive shell. */
class MotionSafetyChromeContractTest {
    @Test
    fun statusStripSurfacesOwnedAndFaultedMotion() {
        var directory: java.io.File? = java.io.File(".").canonicalFile
        repeat(8) {
            val candidate = directory?.let {
                java.io.File(it, "composeApp/src/commonMain/kotlin/dev/openpolaris/ui/OpenPolarisApp.kt")
            }
            if (candidate?.isFile == true) {
                val source = candidate.readText(Charsets.UTF_8)
                assertTrue("val motionState by vm.motionState.collectAsState()" in source)
                assertTrue("MotionState.Owned" in source)
                assertTrue("MotionState.Faulted" in source)
                assertTrue("testTag(\"emergency-stop\")" in source)
                assertTrue("onClick = vm::emergencyStopMotion" in source)
                return
            }
            directory = directory?.parentFile
        }
        error("Cannot read OpenPolarisApp.kt")
    }
}
