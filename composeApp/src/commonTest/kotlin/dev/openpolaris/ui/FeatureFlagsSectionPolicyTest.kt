package dev.openpolaris.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the "Settings pane is split into Day-to-day / Advanced / Admin"
 * policy behind a pure-function test so a future edit of [sectionFor]
 * (or of the [FlagSpec] `safe` / `destructive` flags) cannot silently
 * move a destructive flag out of the Admin section.
 *
 * Background: v0.1.5 reorganised the previously-flat 25-row Settings
 * pane into three collapsible groups. The visual hierarchy
 * (caret + blurb + destructive-only confirm dialog) is the safety net
 * for a casual user who would otherwise be one tap away from a
 * firmware-upload or SD-format dialog. Keeping destructive flags in
 * the Admin group is part of that safety net.
 */
class FeatureFlagsSectionPolicyTest {

    @Test
    fun destructiveFlagIsInAdminSection() {
        val spec = FlagSpec(
            "firmwareUpload", "Firmware upload", "…",
            safe = false, destructive = true,
        )
        assertEquals(FlagSection.Admin, sectionFor(spec))
    }

    @Test
    fun safeFlagIsInDayToDaySection() {
        val spec = FlagSpec("basicControls", "Slew / track", "…", safe = true)
        assertEquals(FlagSection.DayToDay, sectionFor(spec))
    }

    @Test
    fun unsafeNonDestructiveFlagIsInAdvancedSection() {
        val spec = FlagSpec("rawFrameLog", "Frame raw dump", "…", safe = false)
        assertEquals(FlagSection.Advanced, sectionFor(spec))
    }

    @Test
    fun safeDestructiveFlagStillGoesToAdmin() {
        // safe=true + destructive=true is a defensive shape — the toggle
        // is allowed AND requires a confirmation. Such a flag must be
        // surfaced under Admin, not DayToDay, because the action is
        // still irreversible.
        val spec = FlagSpec("hypothetical", "label", "desc", safe = true, destructive = true)
        assertEquals(FlagSection.Admin, sectionFor(spec))
    }
}
