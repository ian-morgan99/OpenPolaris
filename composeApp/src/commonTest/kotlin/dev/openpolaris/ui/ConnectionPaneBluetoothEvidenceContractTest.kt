package dev.openpolaris.ui

import kotlin.test.Test
import kotlin.test.assertTrue

/** Keeps Bluetooth wake evidence separate from Wi-Fi and control readiness. */
class ConnectionPaneBluetoothEvidenceContractTest {
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
    fun connectionPaneShowsTypedWakeEvidence() {
        val source = sourceText()
        assertTrue("BluetoothWakeEvidenceSection(vm)" in source)
        assertTrue("bluetooth-wake-evidence" in source)
        assertTrue("Pulse phase:" in source)
        assertTrue("Bluetooth is a wake pulse only" in source)
        assertTrue("Wi-Fi, the control socket, preview or the camera" in source)
    }
}
