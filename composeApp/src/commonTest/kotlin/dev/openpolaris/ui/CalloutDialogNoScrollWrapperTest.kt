package dev.openpolaris.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the v0.1.11 inversion: the single bounded vertical scroller
 * for every callout has been moved from
 * [dev.openpolaris.ui.FeatureFlagsPane] up into
 * [dev.openpolaris.ui.OpenPolarisApp]'s `CalloutDialog` wrapper.
 *
 * History of the same-axis scrollable crash this test guards against:
 *
 *  - v0.1.5 (commit 8f6c971) added `Modifier.verticalScroll` to the
 *    outer CalloutDialog Column while FeatureFlagsPane already
 *    wrapped its 25 flag rows in its own `verticalScroll`. The two
 *    same-axis scrollables had no bounded height between them, so
 *    opening Settings threw "Vertically scrollable component was
 *    measured with an infinity maximum height constraints" at layout
 *    (issues #40 / #42).
 *
 *  - v0.1.6 (commit ff0672a) "fixed" it by removing the outer
 *    CalloutDialog scroll, leaving FeatureFlagsPane as the sole
 *    `verticalScroll` owner. That solved the crash but also re-clipped
 *    every other callout (GotoPane, CameraPane, FirmwarePane, etc.)
 *    whose content was taller than the AlertDialog's bounded `text`
 *    slot - the visual audit at v0.1.10 found the Slew callout had
 *    lost the Plate solve section, Slew/Cancel buttons, and rotated
 *    Lat/Lng labels below its 472 px Card.
 *
 *  - v0.1.10 added `Modifier.weight(1f, fill = true)` next to the
 *    FeatureFlagsPane scroll so its own build-identity footer was
 *    pinned to the bottom of the dialog. This made the inner column
 *    consume the AlertDialog's bounded slot, which is exactly the
 *    precondition that the v0.1.5 outer scroller was missing.
 *
 *  - v0.1.11 therefore relocates the single `verticalScroll` to the
 *    outer CalloutDialog Column (where the bounded height exists),
 *    keeping the same `weight(1f, fill = true)` on the inner
 *    FeatureFlagsPane Column so its footer is still pinned to the
 *    bottom. Every callout now scrolls, and there is still exactly
 *    one `verticalScroll` in the commonMain UI tree, just owned by a
 *    different file.
 *
 * The policy this test pins (v0.1.11):
 *
 *  1. CalloutDialog MUST wrap its body in exactly one `verticalScroll`
 *     (this is the single bounded scrollable for all callouts).
 *  2. FeatureFlagsPane MUST NOT add its own `verticalScroll` - the
 *     outer wrapper already scrolls the whole callout body. It
 *     continues to use `weight(1f, fill = true)` on its inner column
 *     to pin the build-identity footer.
 *  3. There MUST be exactly one `verticalScroll` modifier in
 *     commonMain UI code (i.e. no same-axis nesting).
 */
class CalloutDialogNoScrollWrapperTest {

    private fun commonMainUiSources(): List<String> = listOf(
        "composeApp/src/commonMain/kotlin/dev/openpolaris/ui/OpenPolarisApp.kt",
        "composeApp/src/commonMain/kotlin/dev/openpolaris/ui/FeatureFlagsPane.kt",
        "composeApp/src/commonMain/kotlin/dev/openpolaris/ui/FullControlPanes.kt",
        "composeApp/src/commonMain/kotlin/dev/openpolaris/ui/Panes.kt",
        "composeApp/src/commonMain/kotlin/dev/openpolaris/ui/UserManualPane.kt",
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
     * explanatory references to `verticalScroll` in our own
     * docstrings.
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
    fun calloutDialogBodyOwnsExactlyOneVerticalScroll() {
        val src = sourceText("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/OpenPolarisApp.kt")
        assertEquals(
            1,
            countVerticalScrollModifiers(src),
            "OpenPolarisApp.kt's CalloutDialog must own exactly one Modifier.verticalScroll " +
                "for its callout body. v0.1.11 inverted the v0.1.6 policy: the outer wrapper is " +
                "now the sole scroller, so all callouts (GotoPane, CameraPane, FirmwarePane, " +
                "FeatureFlagsPane, ...) scroll without each needing its own. Without this, " +
                "taller callouts are clipped at the bottom by the AlertDialog's bounded `text` " +
                "slot (Slew at v0.1.10 lost its Plate solve section and Slew/Cancel buttons)."
        )
        // The rememberScrollState import is required to back the
        // single verticalScroll in CalloutDialog.
        assertTrue(
            "import androidx.compose.foundation.rememberScrollState" in src,
            "OpenPolarisApp.kt must import rememberScrollState to back the v0.1.11 " +
                "CalloutDialog verticalScroll wrapper."
        )
        assertTrue(
            "import androidx.compose.foundation.verticalScroll" in src,
            "OpenPolarisApp.kt must import verticalScroll to wrap the v0.1.11 " +
                "CalloutDialog body."
        )
    }

    @Test
    fun featureFlagsPaneOwnsZeroVerticalScrolls() {
        val src = sourceText("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/FeatureFlagsPane.kt")
        assertEquals(
            0,
            countVerticalScrollModifiers(src),
            "FeatureFlagsPane.kt must not own any Modifier.verticalScroll (v0.1.11). " +
                "The sole bounded scroller now lives in CalloutDialog. The Settings dialog's " +
                "outer wrapper already scrolls, so any inner verticalScroll here would create " +
                "the same-axis nested-scrollables crash from v0.1.5 (issues #40 / #42). " +
                "In v0.1.11 we also removed Modifier.weight(1f, fill = true) on the inner " +
                "Column: that weight created a circular measurement constraint that made the " +
                "AlertDialog's `text` slot measure at 284px and clip all 25 flag rows. The " +
                "version-label footer now scrolls with the list instead of being pinned."
        )
    }

    @Test
    fun noSameAxisNestedVerticalScrollsAcrossUiTree() {
        val total = commonMainUiSources().sumOf { countVerticalScrollModifiers(sourceText(it)) }
        assertEquals(
            1,
            total,
            "commonMain UI tree must contain exactly one Modifier.verticalScroll across " +
                "all .kt files. Two same-axis scrollables with no bounded height between them " +
                "will crash at layout when the dialog is opened (v0.1.5 regression, issues " +
                "#40 / #42). At v0.1.11 the sole scroll lives in OpenPolarisApp.kt (inside " +
                "CalloutDialog, which is the only level that sees a bounded height from the " +
                "AlertDialog's `text` slot). Found $total."
        )
    }
}
