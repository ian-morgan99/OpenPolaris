package dev.openpolaris.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Prevents Android wake regressions back to discovery-only or write-based behavior. */
class AndroidBluetoothWakeContractTest {
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
    fun androidWakeMatchesBenroConnectBareGattPulse() {
        val wake = source(
            "androidApp/src/androidMain/kotlin/dev/openpolaris/android/MountBluetoothWake.kt",
        )
        assertTrue("connectGatt" in wake)
        assertTrue("STATE_CONNECTED" in wake)
        assertTrue("state=2" in wake)
        assertTrue("No characteristic write is sent" in wake)
        assertFalse("discoverServices" in wake)
        assertFalse("writeCharacteristic" in wake)

        val wifi = source(
            "androidApp/src/androidMain/kotlin/dev/openpolaris/android/MountWifiScan.kt",
        )
        assertTrue("MountBluetoothWake(context).wake" in wifi)
        assertFalse("startDiscovery" in wifi)

        val activity = source(
            "androidApp/src/androidMain/kotlin/dev/openpolaris/android/MainActivity.kt",
        )
        assertTrue("Manifest.permission.BLUETOOTH_SCAN" in activity)
    }
}
