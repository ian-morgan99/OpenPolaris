package dev.openpolaris.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the compact phone visual-density boundary. This is source-level
 * coverage because the common JVM target has no installed-phone screenshot
 * harness; the release acceptance still needs a real phone render check.
 */
class MobileCompactDensityContractTest {
    private fun source(relativePath: String): String {
        var directory: java.io.File? = java.io.File(".").canonicalFile
        repeat(8) {
            val candidate = directory?.let { java.io.File(it, relativePath) }
            if (candidate?.isFile == true) return candidate.readText(Charsets.UTF_8)
            directory = directory?.parentFile
        }
        error("Cannot read $relativePath")
    }

    @Test
    fun phoneShellSelectsCompactTypographyAndSpacing() {
        val theme = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/Theme.kt")
        val shell = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/OpenPolarisApp.kt")
        val panes = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/Panes.kt")

        assertTrue("CompactNightTypography" in theme)
        assertTrue("LocalOpenPolarisCompact" in theme)
        assertTrue("labelSmall = TextStyle(fontSize = 11.sp" in theme)
        assertTrue("OpenPolarisTheme(dim = dimMode, compact = !wide)" in shell)
        assertTrue("openPolarisCardOuterPadding()" in panes)
        assertTrue("openPolarisCardInnerPadding()" in panes)
        assertTrue("openPolarisSectionSpacing()" in panes)
        assertTrue("Arrangement.spacedBy(if (LocalOpenPolarisCompact.current) 3.dp else 8.dp)" in shell)
    }

    @Test
    fun missionSurfaceIsSummaryFirstOnCompactPhones() {
        val panes = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/Panes.kt")

        assertTrue("val nextReadiness = cards.firstOrNull" in panes)
        assertTrue("testTag(\"mission-compact-summary\")" in panes)
        assertTrue("maxLines = 1" in panes)
        assertTrue(panes.contains("if (!compactLayout || showAdvancedAstro)"))
        assertTrue(panes.contains("AstroWebUiProgrammeComposer(vm)"))
        assertTrue(panes.contains("contentDescription = if (showAdvancedAstro)"))
    }

    @Test
    fun desktopThemeDoesNotInheritPhoneTypography() {
        val theme = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/Theme.kt")
        assertTrue("typography = if (compact) CompactNightTypography else NightTypography" in theme)
        assertFalse("LocalOpenPolarisCompact provides true" in theme)
    }

    @Test
    fun compactPhoneCalloutsUseAFullViewportSingleScrollShell() {
        val shell = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/OpenPolarisApp.kt")

        assertTrue("if (LocalOpenPolarisCompact.current)" in shell)
        assertTrue("DialogProperties(usePlatformDefaultWidth = false)" in shell)
        assertTrue("testTag(\"compact-callout\")" in shell)
        assertTrue("height(28.dp)" in shell)
        assertTrue("contentDescription = \"Close \$title\"" in shell)
        assertTrue("text = scrollableBody" in shell)
    }

    @Test
    fun compactSlowMotionUsesAShortCommandDeckBeforeDetails() {
        val panes = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/Panes.kt")

        assertTrue("if (compactLayout && !showMotionDetails)" in panes)
        assertTrue("CompactSlowMotionDeck(" in panes)
        assertTrue("testTag(\"slow-motion-compact-deck\")" in panes)
        assertTrue("Hold A/B · release stops" in panes)
        assertTrue("onShowDetails = { showMotionDetails = true }" in panes)
    }

    @Test
    fun compactCameraUsesACaptureFirstDeckBeforeDetails() {
        val panes = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/Panes.kt")

        assertTrue("if (compactLayout && !showCameraDetails)" in panes)
        assertTrue("CompactCameraDeck(" in panes)
        assertTrue("testTag(\"camera-compact-deck\")" in panes)
        assertTrue("onShowDetails = { showCameraDetails = true }" in panes)
        assertTrue("contentDescription = \"Stop Bulb capture\"" in panes)
        assertTrue("contentDescription = \"Start Astro WebUI Bulb exposure\"" in panes)
    }

}
