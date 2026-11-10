package dev.openpolaris.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PosePollPolicyTest {
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
    fun idleMountUsesLowerTrafficCadence() {
        assertEquals(2_000L, PosePollPolicy.delayMs(tracking = false, motionOwned = false, slewInProgress = false))
    }

    @Test
    fun trackingKeepsOneHertzCadence() {
        assertEquals(1_000L, PosePollPolicy.delayMs(tracking = true, motionOwned = false, slewInProgress = false))
    }

    @Test
    fun ownedMotionKeepsOneHertzCadence() {
        assertEquals(1_000L, PosePollPolicy.delayMs(tracking = false, motionOwned = true, slewInProgress = false))
    }

    @Test
    fun gotoKeepsOneHertzCadence() {
        assertEquals(1_000L, PosePollPolicy.delayMs(tracking = false, motionOwned = false, slewInProgress = true))
    }

    @Test
    fun viewModelUsesThePolicyAfterEachPosePair() {
        val vm = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/AppViewModel.kt")
        assertTrue("PosePollPolicy.delayMs(" in vm)
        assertTrue("tracking = mount.tracking == true" in vm)
        assertTrue("motionOwned = motionSupervisor.state.value is MotionState.Owned" in vm)
        assertTrue("slewInProgress = slewInProgress" in vm)
    }
}
