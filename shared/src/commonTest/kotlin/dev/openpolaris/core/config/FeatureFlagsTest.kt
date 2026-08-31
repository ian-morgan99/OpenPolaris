package dev.openpolaris.core.config

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression test for issue #28. FeatureFlags defaults must match the
 * documented "safe-by-default" policy.
 *
 * Anything that:
 *   - moves the mount (limits, auto-level, OMS schedule, file mutate),
 *   - is a system-setting write (time / timezone / language / buzzer / LED),
 *   - is a WiFi write (connect / disconnect / set-band),
 *   - is destructive (reboot, shutdown, format, firmware upload),
 *   - or whose wire format is still unverified on real hardware,
 * must default to false after a fresh FeatureFlags.reset.
 *
 * Verified reads (basicControls, postConnectBurst, omsRead, wifiScan,
 * fileManager browse) remain true.
 */
class FeatureFlagsTest {

    @AfterTest
    fun clearOverrides() = FeatureFlags.reset()

    // --- 1. Compile defaults match the KDoc contract -----------------------

    @Test
    fun destructiveFlagsDefaultToFalse() {
        FeatureFlags.reset()
        assertFalse(FeatureFlags.isEnabled("allowReboot"), "Reboot must be OFF by default (issue #28)")
        assertFalse(FeatureFlags.isEnabled("allowShutdown"), "Shutdown must be OFF by default")
        assertFalse(FeatureFlags.isEnabled("firmwareUpload"), "Firmware upload must be OFF by default")
        assertFalse(FeatureFlags.isEnabled("fileManagerFormat"), "SD format must be OFF by default")
        assertFalse(FeatureFlags.isEnabled("fileManagerMutate"), "File mutate must be OFF by default")
    }

    @Test
    fun unverifiedWritePathsDefaultToFalse() {
        FeatureFlags.reset()
        assertFalse(FeatureFlags.isEnabled("systemSettings"), "System settings (810-829 writes) must be OFF until verified")
        assertFalse(FeatureFlags.isEnabled("wifiConnect"), "WiFi write must be OFF until verified")
        assertFalse(FeatureFlags.isEnabled("autoLevel"), "Auto-level (537/538 writes) must be OFF until verified")
        assertFalse(FeatureFlags.isEnabled("limitsWrite"), "Limits (541/542) wire format unverified - OFF by default")
    }

    @Test
    fun verifiedReadPathsDefaultToTrue() {
        FeatureFlags.reset()
        assertTrue(FeatureFlags.isEnabled("basicControls"), "Slew/track/AHRS verified")
        assertTrue(FeatureFlags.isEnabled("postConnectBurst"), "Post-connect burst verified")
        assertTrue(FeatureFlags.isEnabled("omsRead"), "OMS read (824/825) verified")
        assertTrue(FeatureFlags.isEnabled("wifiScan"), "WiFi scan (770) verified")
        assertTrue(FeatureFlags.isEnabled("fileManager"), "File list (702) verified")
        assertTrue(FeatureFlags.isEnabled("fileManagerReadOnly"), "File browse is safe to leave on")
    }

    @Test
    fun ditherAndSettlingRemainEnabledSeparatelyFromLimits() {
        FeatureFlags.reset()
        assertTrue(FeatureFlags.isEnabled("advancedAstro"), "Dither/settling (539/540/543/544) verified")
        assertFalse(FeatureFlags.isEnabled("limitsWrite"), "Limits (541/542) must NOT piggyback on advancedAstro")
    }

    // --- 2. Runtime override behaviour -------------------------------------

    @Test
    fun enableAndDisableRoundTrip() {
        FeatureFlags.reset()
        assertFalse(FeatureFlags.isEnabled("systemSettings"))
        FeatureFlags.enable("systemSettings")
        assertTrue(FeatureFlags.isEnabled("systemSettings"))
        FeatureFlags.disable("systemSettings")
        assertFalse(FeatureFlags.isEnabled("systemSettings"))
    }

    @Test
    fun toggleFlipsEffectiveValue() {
        FeatureFlags.reset()
        assertFalse(FeatureFlags.isEnabled("autoLevel"))
        FeatureFlags.toggle("autoLevel")
        assertTrue(FeatureFlags.isEnabled("autoLevel"))
        FeatureFlags.toggle("autoLevel")
        assertFalse(FeatureFlags.isEnabled("autoLevel"))
    }

    @Test
    fun resetClearsAllOverridesBackToCompileDefaults() {
        FeatureFlags.enable("systemSettings")
        FeatureFlags.enable("allowReboot")
        FeatureFlags.enable("limitsWrite")
        assertTrue(FeatureFlags.isEnabled("systemSettings"))
        assertTrue(FeatureFlags.isEnabled("allowReboot"))
        assertTrue(FeatureFlags.isEnabled("limitsWrite"))

        FeatureFlags.reset()

        assertFalse(FeatureFlags.isEnabled("systemSettings"))
        assertFalse(FeatureFlags.isEnabled("allowReboot"))
        assertFalse(FeatureFlags.isEnabled("limitsWrite"))
    }

    @Test
    fun unknownFlagDefaultsToFalse() {
        FeatureFlags.reset()
        assertFalse(FeatureFlags.isEnabled("thisFlagDoesNotExist"))
    }

    // --- 3. The limits split is real (not just a rename) -------------------

    @Test
    fun limitsWriteAndAdvancedAstroAreIndependentFlags() {
        FeatureFlags.reset()
        // advancedAstro on does not imply limitsWrite on
        assertTrue(FeatureFlags.isEnabled("advancedAstro"))
        assertFalse(FeatureFlags.isEnabled("limitsWrite"))

        // Enabling limitsWrite does not change advancedAstro
        FeatureFlags.enable("limitsWrite")
        assertTrue(FeatureFlags.isEnabled("limitsWrite"))
        assertTrue(FeatureFlags.isEnabled("advancedAstro"))

        // Disabling advancedAstro does not change limitsWrite
        FeatureFlags.disable("advancedAstro")
        assertFalse(FeatureFlags.isEnabled("advancedAstro"))
        assertTrue(FeatureFlags.isEnabled("limitsWrite"))
    }
}
