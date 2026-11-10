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
        return String(
            Files.readAllBytes(root.resolve("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/$file")),
            Charsets.UTF_8,
        )
    }

    @Test
    fun `all callout destinations expose deterministic automation tags`() {
        val text = source("OpenPolarisApp.kt")
        assertTrue("testTag(\"callout-${'$'}{c.name.lowercase()}\")" in text)
    }

    @Test
    fun `persistent status actions expose deterministic automation tags`() {
        val text = source("OpenPolarisApp.kt")
        assertTrue("testTag(\"mission-details\")" in text)
        assertTrue("testTag(\"open-gallery\")" in text)
    }

    @Test
    fun `mission readiness exposes safe next-action callbacks`() {
        val text = source("Panes.kt")
        assertTrue("onAction: ((String) -> Unit)? = null" in text)
        assertTrue("onAction(card.id)" in text)
        val shell = source("OpenPolarisApp.kt")
        assertTrue("MissionDashboardPane(" in shell)
        assertTrue("dialog = when (actionId)" in shell)
        assertTrue("\"alignment\", \"tracking\", \"motion\" -> Callout.Slew" in shell)
        assertTrue("if (actionId == \"storage\") galleryOpen = true" in shell)
    }

    @Test
    fun `ambiguous camera glyph controls have spoken labels`() {
        val text = source("Panes.kt")
        assertTrue("contentDescription = \"Decrease ${'$'}label\"" in text)
        assertTrue("contentDescription = \"Increase ${'$'}label\"" in text)
    }
}
