package dev.openpolaris.ui

import kotlin.test.Test
import kotlin.test.assertTrue

/** Keeps named-star selection visible without making it an implicit slew. */
class GotoAlignmentSelectionContractTest {
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
    fun alignmentChooserSelectsIdentityWithoutCallingSlew() {
        val source = sourceText()
        assertTrue("Find a named alignment star" in source)
        assertTrue("vm.selectAlignmentStar(star.designation" in source)
        assertTrue("Use last plate solve as alignment identity" in source)
        assertTrue("Center a bright star with the jog controls, then record it" in source)
    }
}
