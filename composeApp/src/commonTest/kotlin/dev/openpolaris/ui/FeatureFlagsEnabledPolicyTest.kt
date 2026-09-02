package dev.openpolaris.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the "destructive flags are interactive from Settings" policy
 * behind a pure-function test so a future refactor of
 * [isFlagSpecInteractive] (or the `FlagRow` Switch in
 * [FeatureFlagsPane]) cannot silently disable the confirmation flow
 * for destructive flags again.
 *
 * Background: Issue #37 reported that `FlagRow` used
 * `Switch(enabled = spec.safe)`, so all `safe = false, destructive = true`
 * flags (fileManagerFormat, allowReboot, allowShutdown, firmwareUpload)
 * were non-interactive even though the surrounding UI claimed they could
 * be enabled.
 *
 * The fix is to make `enabled = spec.safe || spec.destructive` and gate
 * the actual change behind [DestructiveConfirmDialog]. The policy is
 * centralised in [isFlagSpecInteractive] so this test does not need a
 * Compose test harness — only the data-class semantics.
 */
class FeatureFlagsEnabledPolicyTest {

    @Test
    fun safeFlagIsInteractive() {
        val spec = FlagSpec("basicControls", "Slew / track", "Slew, track, jog.", safe = true)
        assertTrue(isFlagSpecInteractive(spec), "safe flags must be directly toggleable")
    }

    @Test
    fun destructiveFlagIsInteractive() {
        // Mirrors the real destructive specs in FeatureFlagsPane
        // (fileManagerFormat, allowReboot, allowShutdown, firmwareUpload).
        val spec = FlagSpec(
            "firmwareUpload", "Firmware upload (9090 opcodes)",
            "Experimental 9090-side upload. Destructive — requires explicit confirm.",
            safe = false,
            destructive = true,
        )
        assertTrue(
            isFlagSpecInteractive(spec),
            "destructive flags must be interactive so the confirm dialog can fire",
        )
    }

    @Test
    fun unsafeNonDestructiveFlagIsReadOnly() {
        // Surfaced for visibility but stays read-only — promotion to
        // `safe` or `destructive` is an intentional source change.
        val spec = FlagSpec("rawFrameLog", "Frame raw dump", "Log every raw frame.", safe = false)
        assertFalse(
            isFlagSpecInteractive(spec),
            "unsafe + non-destructive specs should remain read-only",
        )
    }

    @Test
    fun safeDestructiveFlagIsInteractive() {
        // The (safe=true, destructive=true) combination is a defensive
        // shape: a flag that is BOTH directly togglable and requires
        // confirmation. Both paths must remain open.
        val spec = FlagSpec("hypothetical", "label", "desc", safe = true, destructive = true)
        assertTrue(isFlagSpecInteractive(spec))
    }
}
