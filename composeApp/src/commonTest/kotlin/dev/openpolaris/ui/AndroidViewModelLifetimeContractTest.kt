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
    fun androidHostRetainsViewModelAcrossRecompositionAndRotation() {
        val activity = source(
            "androidApp/src/androidMain/kotlin/dev/openpolaris/android/MainActivity.kt",
        )
        val host = source(
            "androidApp/src/androidMain/kotlin/dev/openpolaris/android/OpenPolarisHostViewModel.kt",
        )

        assertTrue("ViewModelProvider(" in activity)
        assertTrue("viewModel = retainedHostViewModel.appViewModel" in activity)
        assertFalse("val retainedViewModel = remember {" in activity)
        assertTrue("class OpenPolarisHostViewModel(" in host)
        assertTrue(": ViewModel()" in host)
        assertTrue("override fun onCleared()" in host)
        assertTrue("appViewModel.disconnect()" in host)
    }
}
