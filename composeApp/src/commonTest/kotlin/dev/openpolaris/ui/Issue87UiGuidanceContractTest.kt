package dev.openpolaris.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Regression coverage for the remaining source-level guidance in issue #87. */
class Issue87UiGuidanceContractTest {

    private fun sourceText(relativePath: String): String {
        var directory: File? = File(".").canonicalFile
        repeat(8) {
            val candidate = directory?.let { File(it, relativePath) }
            if (candidate?.isFile == true) return candidate.readText(Charsets.UTF_8)
            directory = directory?.parentFile
        }
        error("Cannot read $relativePath - searched current directory and its parents")
    }

    @Test
    fun previewCopyUsesTheActualDedicatedPreviewPort() {
        val source = sourceText("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/Panes.kt")
        assertTrue("Streamed from http://\${vm.host}:\${vm.previewPort}/?action=stream" in source)
        assertFalse("Streamed from http://\${vm.host}:9090/?action=stream" in source)
    }

    @Test
    fun firmwarePickerHasStableAccessibleTarget() {
        val source = sourceText("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/Panes.kt")
        assertTrue("testTag(\"firmware-pick\")" in source)
        assertTrue("contentDescription = \"Pick firmware ZIP\"" in source)
        assertTrue("label = \"Pick firmware…\"" in source)
    }
}
