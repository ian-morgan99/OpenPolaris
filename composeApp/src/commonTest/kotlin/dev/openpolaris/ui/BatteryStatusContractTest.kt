package dev.openpolaris.ui

import kotlin.test.Test
import kotlin.test.assertTrue

class BatteryStatusContractTest {
    private fun source(path: String): String {
        var directory: java.io.File? = java.io.File(".").canonicalFile
        repeat(10) {
            val candidate = directory?.let { java.io.File(it, path) }
            if (candidate?.isFile == true) return candidate.readText(Charsets.UTF_8)
            directory = directory?.parentFile
        }
        error("Cannot read $path")
    }

    @Test
    fun statusStripUsesExplicitBatterySemantics() {
        val source = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/OpenPolarisApp.kt")

        assertTrue("BatteryAwareness.from" in source)
        assertTrue("battery-status" in source)
        assertTrue("battery.contentDescription" in source)
        assertTrue("BatterySeverity.CRITICAL" in source)
        assertTrue("BatterySeverity.LOW" in source)
    }
}
