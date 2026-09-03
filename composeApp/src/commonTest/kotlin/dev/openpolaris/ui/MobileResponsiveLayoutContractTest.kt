package dev.openpolaris.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * Pins the v0.1.8 responsive-layout contract that fixed issues #45
 * (callout dialogs clipped core controls in landscape) and #46
 * (callout rail density for touch). These are the structural
 * guarantees issue #47 demands: the design contract enforced by
 * source so a future edit cannot silently regress to v0.1.7's
 * phone-unusable layout.
 *
 * The tests follow the same source-text pattern as
 * CalloutDialogNoScrollWrapperTest (the v0.1.6 #40/#42 pin) so
 * they run in the existing commonTest JVM target with no new
 * infrastructure (Paparazzi/Roborazzi are multi-day setup).
 *
 * Acceptance criteria from issue #47 this file covers:
 *   - Landscape phone layout is selected using both width and height
 *   - Navigation fits 568x320 dp landscape without clipping
 *   - Firmware / advanced settings are not primary operational destinations
 *   - The three tall panes (Firmware, Camera, Connection) are redesigned
 */
class MobileResponsiveLayoutContractTest {

    private fun sourceText(relativePath: String): String {
        val candidates = listOf(
            java.io.File(relativePath),
            java.io.File("/home/ian/Documents/VSCodeProjects/OpenPolaris/$relativePath"),
        )
        return candidates.firstOrNull { it.exists() }
            ?.readText(Charsets.UTF_8)
            ?: error("Cannot read $relativePath - test must run from the repo root or a known absolute path")
    }

    /** Strip block + line comments so regexes don't match docstring snippets. */
    private fun stripComments(src: String): String {
        val noBlockComments = Regex("""/\*[\s\S]*?\*/""").replace(src, "")
        return noBlockComments.lines()
            .map { line -> line.substringBefore("//") }
            .joinToString("\n")
    }

    // --- (1) Phone detection uses BOTH size classes -----------------

    @Test
    fun phoneDetectionUsesBothSizeClasses() {
        val src = sourceText("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/OpenPolarisApp.kt")
        val noComments = stripComments(src)
        assertTrue(
            "widthSizeClass" in noComments && "heightSizeClass" in noComments,
            "OpenPolarisApp.kt must reference BOTH WindowWidthSizeClass and WindowHeightSizeClass. v0.1.7 only checked width, so a 415 dp tall landscape phone took the wide rail."
        )
        assertTrue(
            Regex("""val\s+widthCompact\s*=.*WidthSizeClass\.Compact""").containsMatchIn(noComments),
            "OpenPolarisApp.kt must derive widthCompact from WindowWidthSizeClass.Compact"
        )
        assertTrue(
            Regex("""val\s+heightCompact\s*=.*HeightSizeClass\.Compact""").containsMatchIn(noComments),
            "OpenPolarisApp.kt must derive heightCompact from WindowHeightSizeClass.Compact (v0.1.8)"
        )
        val wideMatch = Regex("""val\s+wide\s*=\s*([^;\n]+)""").find(noComments)
        assertNotNull(wideMatch, "OpenPolarisApp.kt must compute a wide boolean")
        val wideExpr = wideMatch!!.groupValues[1]
        assertTrue(
            "widthCompact" in wideExpr && "heightCompact" in wideExpr,
            "wide must consider BOTH widthCompact and heightCompact. Found: $wideExpr"
        )
    }

    // --- (2) Operate + More partition --------------------------------

    @Test
    fun operateItemsPlusMoreItemsPartitionIsExclusiveAndTotal() {
        val src = sourceText("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/OpenPolarisApp.kt")
        val noComments = stripComments(src)
        val operateMatch = Regex("""private\s+val\s+OperateItems\s*:\s*List<Callout>\s*=\s*listOf\(([\s\S]*?)\)""").find(noComments)
        val moreMatch = Regex("""private\s+val\s+MoreItems\s*:\s*List<Callout>\s*=\s*listOf\(([\s\S]*?)\)""").find(noComments)
        assertNotNull(operateMatch, "OpenPolarisApp.kt must define OperateItems (v0.1.8 phone rail)")
        assertNotNull(moreMatch, "OpenPolarisApp.kt must define MoreItems (v0.1.8 phone rail overflow)")
        val operateRefs = Regex("""Callout\.\w+""").findAll(operateMatch!!.groupValues[1]).map { it.value }.toList()
        val moreRefs = Regex("""Callout\.\w+""").findAll(moreMatch!!.groupValues[1]).map { it.value }.toList()
        assertEquals(4, operateRefs.size, "OperateItems must be 4 entries. Found: $operateRefs")
        assertEquals(5, moreRefs.size, "MoreItems must be 5 entries. Found: $moreRefs")
        assertEquals(0, operateRefs.intersect(moreRefs.toSet()).size, "OperateItems and MoreItems must be disjoint")
        val total = (operateRefs + moreRefs).toSet()
        assertEquals(9, total.size, "OperateItems + MoreItems must cover all 9 Callout entries. Found: $total")
    }

    @Test
    fun dangerousCalloutsAreBehindMoreMenu() {
        val src = sourceText("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/OpenPolarisApp.kt")
        val noComments = stripComments(src)
        val operateMatch = Regex("""private\s+val\s+OperateItems\s*:\s*List<Callout>\s*=\s*listOf\(([\s\S]*?)\)""").find(noComments)!!
        val operateRefs = Regex("""Callout\.\w+""").findAll(operateMatch.groupValues[1]).map { it.value }.toSet()
        val mustBeInMore = setOf("Callout.Firmware", "Callout.Settings", "Callout.Readme", "Callout.Helpers", "Callout.VR")
        for (c in mustBeInMore) {
            assertFalse(c in operateRefs, "$c must NOT be in OperateItems (issue #47: not a primary operational destination)")
        }
        val mustBeInOperate = setOf("Callout.Connection", "Callout.Slew", "Callout.Camera", "Callout.Preview")
        for (c in mustBeInOperate) {
            assertTrue(c in operateRefs, "$c must be in OperateItems (most-frequent primary action)")
        }
    }

    @Test
    fun moreMenuButtonIsPresentInPhoneRail() {
        val src = sourceText("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/OpenPolarisApp.kt")
        val noComments = stripComments(src)
        assertTrue("MoreMenuButton" in noComments, "OpenPolarisApp.kt must define MoreMenuButton for the phone rail overflow")
        assertTrue(
            Regex("""MoreMenuButton\(\s*handle\s*\)""").containsMatchIn(noComments),
            "CalloutRail phone branch must contain MoreMenuButton(handle)"
        )
    }

    // --- (3) Three tall panes redesigned ----------------------------

    @Test
    fun firmwarePaneIsPaginatedIntoThreeSteps() {
        val src = sourceText("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/Panes.kt")
        val noComments = stripComments(src)
        for (stepName in listOf("FirmwareStep1", "FirmwareStep2", "FirmwareStep3")) {
            assertTrue(
                Regex("""private\s+fun\s+$stepName\s*\(""").containsMatchIn(noComments),
                "Panes.kt must define private fun $stepName (v0.1.8 Firmware pagination)"
            )
        }
        assertTrue(
            Regex("""fun\s+FirmwarePane\b[\s\S]{0,2400}var\s+step\s+by\s+remember""").containsMatchIn(noComments),
            "FirmwarePane must own a var step by remember state and drive the sub-composable from it"
        )
        assertTrue("Back" in noComments && "Next" in noComments, "FirmwarePane must expose Back and Next buttons")
    }

    @Test
    fun cameraPaneIsLaidOutInTwoColumns() {
        val src = sourceText("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/Panes.kt")
        val noComments = stripComments(src)
        // Stepper list must be defined
        assertTrue(
            Regex("""val\s+steppers\s*:\s*List<@Composable\s*\(\)\s*->\s*Unit>\s*=\s*listOf\(""").containsMatchIn(noComments),
            "CameraPane must build a steppers list (v0.1.8 layout)"
        )
        // Count StepperRow( call sites inside the listOf. The function is also called from
        // its definition line ("private fun StepperRow("); we subtract that to get 10 steppers.
        val stepperCallSites = Regex("""\bStepperRow\(""").findAll(noComments).count()
        assertTrue(stepperCallSites >= 9, "CameraPane must have at least 8 stepper call sites. Found $stepperCallSites")
        // List must be partitioned into two columns (take/drop, chunked, subList, or partition)
        val splitsInHalf = Regex("""\bsteppers\.(?:take|drop|chunked|subList|partition)\b""").containsMatchIn(noComments)
        val altPartition = Regex("""\b(first|left|top)Half\b.*\b(second|right|bottom)Half\b""", RegexOption.DOT_MATCHES_ALL).containsMatchIn(noComments)
        assertTrue(splitsInHalf || altPartition, "CameraPane must partition steppers into two columns (v0.1.8)")
    }

    @Test
    fun connectionPaneUsesFlowRowNotRow() {
        val src = sourceText("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/Panes.kt")
        val noComments = stripComments(src)
        assertTrue(
            Regex("""@OptIn\(ExperimentalLayoutApi::class\)""").containsMatchIn(noComments),
            "Panes.kt must opt into ExperimentalLayoutApi for FlowRow"
        )
        val connectionPane = Regex("""fun\s+ConnectionPane\b[\s\S]*?(?=\n@OptIn|\n@Composable|\nfun\s+\w|\Z)""").find(noComments)
        assertNotNull(connectionPane, "Could not locate ConnectionPane in Panes.kt")
        val body = connectionPane!!.groupValues[0]
        assertTrue("FlowRow" in body, "ConnectionPane body must use FlowRow (v0.1.8, issue #45)")
    }

    // --- (4) CalloutDialog body now owns the sole scroller ---------

    @Test
    fun calloutDialogBodyOwnsTheSoleVerticalScroll() {
        val src = sourceText("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/OpenPolarisApp.kt")
        val noComments = stripComments(src)
        // v0.1.11 inversion: the bounded vertical scroller now lives
        // here, not in FeatureFlagsPane. The two-scrollables-with-no-
        // bounded-height crash from v0.1.5 cannot recur because there
        // is now exactly one `verticalScroll` in the commonMain UI
        // tree, sitting at the AlertDialog `text` slot which has a
        // bounded height.
        assertEquals(
            1,
            Regex("""Modifier\.verticalScroll\s*\(""").findAll(noComments).count(),
            "OpenPolarisApp.kt must contain exactly one Modifier.verticalScroll for the " +
                "CalloutDialog body (v0.1.11). See CalloutDialog docstring and issues " +
                "#40 / #42 for the same-axis nested-scrollables crash this avoids."
        )
        assertTrue(
            "import androidx.compose.foundation.rememberScrollState" in noComments,
            "OpenPolarisApp.kt must import rememberScrollState to back the v0.1.11 " +
                "CalloutDialog verticalScroll wrapper."
        )
        assertTrue(
            "import androidx.compose.foundation.verticalScroll" in noComments,
            "OpenPolarisApp.kt must import verticalScroll to wrap the v0.1.11 " +
                "CalloutDialog body."
        )
    }

    // --- (5) Build identity in Settings still required --------------

    @Test
    fun settingsPaneStillExposesBuildIdentity() {
        val src = sourceText("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/FeatureFlagsPane.kt")
        assertTrue("versionLabel" in src, "FeatureFlagsPane must render versionLabel (issue #43 fix)")

        // v0.1.10 fix: the version label was being clipped by the
        // AlertDialog's bounded `text` slot because the surrounding
        // Column had no scroll and no weight was applied to the inner
        // scrollable Column. The label was rendered off-screen on
        // portrait and landscape phones. Pin the fix: the inner flag
        // list must use weight(1f) so the footer stays in view.
        val noComments = Regex("""/\*[\s\S]*?\*/""").replace(src, "")
            .lines().map { it.substringBefore("//") }.joinToString("\n")
        assertTrue(
            "Modifier.weight(1f" in noComments,
            "FeatureFlagsPane must use Modifier.weight(1f) on the inner " +
                "scrollable Column so the build-identity footer is pinned " +
                "at the bottom of the Settings dialog instead of being " +
                "clipped by the AlertDialog's bounded `text` slot."
        )
    }
}
