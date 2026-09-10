package dev.openpolaris.ui

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertTrue

class UiAccessibilityContractTest {
    private fun source(file: String): String {
        val start = Paths.get("").toAbsolutePath()
        val root = generateSequence(start) { it.parent }
            .first { Files.exists(it.resolve("settings.gradle.kts")) }
        return Files.readString(root.resolve("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/$file"))
    }

    @Test
    fun `all callout destinations expose deterministic automation tags`() {
        val text = source("OpenPolarisApp.kt")
        assertTrue("testTag(\"callout-${'$'}{c.name.lowercase()}\")" in text)
    }

    @Test
    fun `ambiguous camera glyph controls have spoken labels`() {
        val text = source("Panes.kt")
        assertTrue("contentDescription = \"Decrease ${'$'}label\"" in text)
        assertTrue("contentDescription = \"Increase ${'$'}label\"" in text)
    }
}
