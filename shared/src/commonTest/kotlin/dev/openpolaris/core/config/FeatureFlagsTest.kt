package dev.openpolaris.core.config

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression test for issue #28. FeatureFlags defaults must match the
 * documented "safe-by-default" policy.
 *
 * After a fresh FeatureFlags.reset, anything that:
 *   - is destructive (reboot, shutdown, format, firmware upload),
 *   - is a WiFi write (connect / disconnect / set-band),
 *   - is an unverified mount move (limits, auto-level, OMS schedule),
 *   - or whose wire format is still unverified on real hardware,
 * must default to false.
 *
 * The system-settings (810-829) writes were verified round-trip on the
 * live burst (see docs/PROTOCOL-CODE-AUDIT-2026-08-31.md) and now
 * default to true — every safe switch should be reachable without a
 * config flag flip. Kiosk builds can opt out via
 * `FeatureFlags.disable("systemSettings")`.
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
        // systemSettings (810-829) was verified round-trip on the live burst;
        // defaults to true. See FeatureFlags KDoc.
        assertTrue(FeatureFlags.isEnabled("systemSettings"), "System settings (810-829 writes) verified - ON by default")
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
        // systemSettings defaults to true; disable→enable→disable round trip
        assertTrue(FeatureFlags.isEnabled("systemSettings"))
        FeatureFlags.disable("systemSettings")
        assertFalse(FeatureFlags.isEnabled("systemSettings"))
        FeatureFlags.enable("systemSettings")
        assertTrue(FeatureFlags.isEnabled("systemSettings"))
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
        // Flip several flags away from their defaults
        FeatureFlags.disable("systemSettings")  // default true → now false
        FeatureFlags.enable("allowReboot")      // default false → now true
        FeatureFlags.enable("limitsWrite")      // default false → now true
        assertFalse(FeatureFlags.isEnabled("systemSettings"))
        assertTrue(FeatureFlags.isEnabled("allowReboot"))
        assertTrue(FeatureFlags.isEnabled("limitsWrite"))

        FeatureFlags.reset()

        // After reset, compile defaults apply
        assertTrue(FeatureFlags.isEnabled("systemSettings"))
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
