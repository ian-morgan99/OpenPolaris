package dev.openpolaris.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the v0.1.6 fix for the v0.1.5 regression that wrapped
 * CalloutDialog in `Modifier.verticalScroll(rememberScrollState())`
 * (commit 8f6c971), which together with the inner `verticalScroll`
 * inside FeatureFlagsPane created same-axis nested scrollables with
 * no bounded height. Opening the Settings callout would then throw
 * "Vertically scrollable component was measured with an infinity
 * maximum height constraints" at layout.
 *
 * The policy this test pins:
 *
 * 1. CalloutDialog MUST NOT wrap its body in verticalScroll.
 *    A plain Column is preferred so Material.AlertDialog clipping
 *    handles overflow on the 320x568 dp landscape surface, mirroring
 *    the Benro Connect aesthetic and the project's explicit
 *    no-scroll-bars requirement.
 * 2. The inner FeatureFlagsPane MAY keep a single verticalScroll
 *    for its 25 flag rows (the exception explicitly allowed by the
 *    fix that closed #40, ff0672a).
 * 3. There MUST be exactly one verticalScroll modifier in
 *    commonMain UI code (i.e. no same-axis nesting).
 *
 * Background: this regression was the cause of issue #42 (filed at
 * v0.1.5) and indirectly re-broke issue #40. The runtime symptom was
 * "android app won't even open" on top of "android says the app is
 * not valid" (the v0.1.5 APK was also unsigned - fixed separately).
 */
class CalloutDialogNoScrollWrapperTest {

    private fun commonMainUiSources(): List<String> = listOf(
        "composeApp/src/commonMain/kotlin/dev/openpolaris/ui/OpenPolarisApp.kt",
        "composeApp/src/commonMain/kotlin/dev/openpolaris/ui/FeatureFlagsPane.kt",
        "composeApp/src/commonMain/kotlin/dev/openpolaris/ui/FullControlPanes.kt",
        "composeApp/src/commonMain/kotlin/dev/openpolaris/ui/Panes.kt",
    )

    private fun sourceText(relativePath: String): String {
        val candidates = listOf(
            java.io.File(relativePath),
            java.io.File("/home/ian/Documents/VSCodeProjects/OpenPolaris/$relativePath"),
        )
        return candidates.firstOrNull { it.exists() }
            ?.readText(Charsets.UTF_8)
            ?: error("Cannot read $relativePath - test must run from the repo root or a known absolute path")
    }

    /**
     * Strip lines that are inside a `/** ... */` or `//` block
     * comment so the regex below does not match the
     * "DO NOT RE-ADD" warning we explicitly wrote into the
     * CalloutDialog docstring.
     */
    private fun stripComments(src: String): String {
        val noBlockComments = Regex("""/\*[\s\S]*?\*/""").replace(src, "")
        return noBlockComments.lines()
            .map { line -> line.substringBefore("//") }
            .joinToString("\n")
    }

    private fun countVerticalScrollModifiers(src: String): Int {
        val noComments = stripComments(src)
        return Regex("""Modifier\.verticalScroll\s*\(""").findAll(noComments).count()
    }

    @Test
    fun calloutDialogBodyIsNotAVerticalScroll() {
        val src = sourceText("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/OpenPolarisApp.kt")
        assertEquals(
            0,
            countVerticalScrollModifiers(src),
            "OpenPolarisApp.kt must not contain any Modifier.verticalScroll call. " +
                "The outer CalloutDialog wrapper was the v0.1.5 regression. " +
                "See CalloutDialog docstring and issues #40/#42."
        )
        // The recallScrollState import was the smoking gun for v0.1.5.
        assertFalse(
            "import androidx.compose.foundation.rememberScrollState" in src,
            "OpenPolarisApp.kt must not import rememberScrollState - its presence indicates " +
                "a verticalScroll wrapper has been re-added. See issues #40/#42."
        )
        assertFalse(
            "import androidx.compose.foundation.verticalScroll" in src,
            "OpenPolarisApp.kt must not import verticalScroll - its presence indicates a " +
                "verticalScroll wrapper has been re-added. See issues #40/#42."
        )
    }

    @Test
    fun settingsPaneOwnsExactlyOneVerticalScroll() {
        val src = sourceText("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/FeatureFlagsPane.kt")
        assertEquals(
            1,
            countVerticalScrollModifiers(src),
            "FeatureFlagsPane.kt must own exactly one Modifier.verticalScroll (for the 25 " +
                "flag rows). The Settings dialog's outer wrapper no longer scrolls (v0.1.6), " +
                "so this inner scroller is the sole owner of vertical scrolling in the UI."
        )
        val noComments = stripComments(src)
        val scrollableColumnCount = Regex(
            """Column\s*\(\s*[^)]*modifier\s*=\s*Modifier\.verticalScroll"""
        ).findAll(noComments).count()
        assertTrue(
            scrollableColumnCount >= 1,
            "FeatureFlagsPane must wrap its flag list Column in a verticalScroll. None found; " +
                "the 25 flag rows will not fit in the dialog's clipped body without it."
        )
    }

    @Test
    fun noSameAxisNestedVerticalScrollsAcrossUiTree() {
        val total = commonMainUiSources().sumOf { countVerticalScrollModifiers(sourceText(it)) }
        assertEquals(
            1,
            total,
            "commonMain UI tree must contain exactly one Modifier.verticalScroll across " +
                "all .kt files (currently only FeatureFlagsPane.kt). Found $total. " +
                "Two same-axis scrollables with no bounded height between them will crash " +
                "at layout when the dialog is opened. See issues #40 and #42."
        )
    }
}
