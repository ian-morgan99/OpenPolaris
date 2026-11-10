package dev.openpolaris.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidViewModelLifetimeContractTest {
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
    fun androidHostRetainsViewModelAcrossRecomposition() {
        val activity = source(
            "androidApp/src/androidMain/kotlin/dev/openpolaris/android/MainActivity.kt",
        )

        assertTrue("val retainedViewModel = remember {" in activity)
        assertTrue("viewModel = retainedViewModel" in activity)
        assertFalse("viewModel = AppViewModel(" in activity)
    }
}
