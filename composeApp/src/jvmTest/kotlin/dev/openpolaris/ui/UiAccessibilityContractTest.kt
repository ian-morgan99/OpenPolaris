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
        assertTrue("InlineDestinationWorkspace(" in shell)
        assertTrue("onMissionAction = ::openMissionAction" in shell)
        assertTrue("fun openMissionAction(actionId: String)" in shell)
        assertTrue("\"alignment\", \"tracking\", \"motion\", \"target\" -> openDestination(AppDestination.CONTROL)" in shell)
        assertTrue("\"storage\" -> openDestination(AppDestination.GALLERY)" in shell)
        assertTrue("\"connection\", \"mount\" -> openAdvanced(Callout.Connection)" in shell)
        assertTrue("testTag(\"endpoint-health\")" in text)
        assertTrue("testTag(\"endpoint-control-service\")" in text)
        assertTrue("testTag(\"endpoint-preview-service\")" in text)
        assertTrue("onOpenConnection = onAction?.let" in text)
        assertTrue("vm.motionState.collectAsState()" in text)
        assertTrue("testTag(\"motion-owner-state\")" in text)
    }
    @Test
    fun `ambiguous camera glyph controls have spoken labels`() {
        val text = source("Panes.kt")
        assertTrue("contentDescription = \"Decrease ${'$'}label\"" in text)
        assertTrue("contentDescription = \"Increase ${'$'}label\"" in text)
    }
}
