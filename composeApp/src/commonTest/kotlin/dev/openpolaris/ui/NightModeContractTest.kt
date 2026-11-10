package dev.openpolaris.ui

import kotlin.test.Test
import kotlin.test.assertTrue

/** Keeps the field-night control discoverable on both responsive shells. */
class NightModeContractTest {
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
    fun dimModeHasAnAccessibleToggleAndSeparatePalette() {
        val theme = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/Theme.kt")
        val shell = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/OpenPolarisApp.kt")
        assertTrue("DimNightColors" in theme)
        assertTrue("OpenPolarisTheme(dim = dimMode, compact = !wide)" in shell)
        assertTrue("night-dim-toggle" in shell)
        assertTrue("Bright" in shell)
        assertTrue("preferenceStore" in shell)
        assertTrue("UserPreferences" in shell)
        assertTrue("setDimMode(!dimMode)" in shell)
    }
}
