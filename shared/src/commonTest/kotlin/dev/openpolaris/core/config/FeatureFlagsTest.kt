package dev.openpolaris.core.config

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression test for issue #28. FeatureFlags defaults must match the
 * documented "safe-by-default" policy.
 *
 * After the evidence-driven merge (docs/PROTOCOL-CODE-AUDIT-2026-08-31.md),
 * the policy is:
 *   - Verified reads and verified writes default to ON — every safe
 *     switch the user can reach on the hardware should be reachable
 *     without a config flag flip.
 *   - Unverified writes, mount-moving writes (limits, auto-level),
 *     unverified WiFi writes, and destructive actions (reboot, shutdown,
 *     format, firmware upload, file mutate) default to OFF.
 *   - The verified system settings (810-829: time, timezone, language,
 *     buzzer, LED) default to ON — they are round-trip verified and
 *     the user wants them reachable.
 *   - The verified advanced astro (dither 539/540, settling 543/544)
 *     default to ON. The unverified limits (541/542) and auto-level
 *     (547-549) writes stay OFF.
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
        // Verified: systemSettings (817 buzzer, 818 LED) is round-trip verified → ON
        assertTrue(FeatureFlags.isEnabled("systemSettings"), "System settings (817/818) verified round-trip → ON")
        // Unverified split: 814/815/816 wire payload contradicts name → OFF
        assertFalse(FeatureFlags.isEnabled("systemSettingsUnverified"), "814/815/816 wire contradicts name → OFF")
        // Verified: autoLevel (547/548/549) live-confirmed 2026-08-31 → ON
        // (see docs/POLARIS-FUNCTIONS-REPORT.md §2.3)
        assertTrue(FeatureFlags.isEnabled("autoLevel"), "Auto-level (547/548/549) live-confirmed → ON")
        // Unverified: still OFF
        assertFalse(FeatureFlags.isEnabled("wifiConnect"), "WiFi write must be OFF until verified")
        assertFalse(FeatureFlags.isEnabled("limitsWrite"), "Limits (541/542) wire format unverified - OFF by default")
        assertFalse(FeatureFlags.isEnabled("cameraFocusControl"), "Focus direction and repeat/stop behaviour must be qualified first")
        assertFalse(FeatureFlags.isEnabled("cameraVideo"), "Video lifecycle must be qualified first")
        assertFalse(FeatureFlags.isEnabled("astroWebUiProgrammeControls"), "Astro programme writes must remain off until route qualification")
        assertFalse(FeatureFlags.isEnabled("astroWebUiSessionControls"), "Astro session writes must remain off until route and recovery qualification")
        assertFalse(FeatureFlags.isEnabled("astroWebUiProgrammeStarts"), "Astro programme starts must remain off until named-head qualification")
        assertFalse(FeatureFlags.isEnabled("astroWebUiSiteWrite"), "Astro WebUI site writes must remain off until mutation and recovery qualification")
        assertFalse(FeatureFlags.isEnabled("astroWebUiGuiding"), "Astro WebUI guiding must remain off until solve, ownership and recovery qualification")
        assertFalse(FeatureFlags.isEnabled("astroWebUiAutofocus"), "Astro WebUI autofocus writes must remain off until camera and recovery qualification")
        assertFalse(FeatureFlags.isEnabled("astroWebUiFocalWrite"), "Astro WebUI focal writes must remain off until solve mutation and recovery qualification")
        assertFalse(FeatureFlags.isEnabled("astroWebUiKeepWifiWrite"), "Astro WebUI keep-Wi-Fi writes must remain off until recovery qualification")
        assertFalse(FeatureFlags.isEnabled("astroWebUiTargetWrites"), "Astro WebUI compass alignment and GoTo writes must remain off until motion recovery qualification")
    }

    @Test
    fun verifiedReadPathsDefaultToTrue() {
        FeatureFlags.reset()
        assertTrue(FeatureFlags.isEnabled("basicControls"), "Slew/track/AHRS verified")
        assertFalse(FeatureFlags.isEnabled("fineRateControl"), "Fine correction must remain off until physically qualified")
        assertFalse(FeatureFlags.isEnabled("slowMotionControl"), "Slow motion must remain off until wire and stop semantics are qualified")
        assertTrue(FeatureFlags.isEnabled("postConnectBurst"), "Post-connect burst verified")
        assertTrue(FeatureFlags.isEnabled("astroWebUiGuideStatus"), "Astro WebUI guide readback is safe and should remain visible")
        assertTrue(FeatureFlags.isEnabled("astroWebUiAutofocusStatus"), "Astro WebUI autofocus progress readback is safe and should remain visible")
        assertTrue(FeatureFlags.isEnabled("astroWebUiFocalStatus"), "Astro WebUI focal readback is safe and should remain visible")
        assertTrue(FeatureFlags.isEnabled("astroWebUiLimitsStatus"), "Astro WebUI travel-limit readback is safe and should remain visible")
        assertTrue(FeatureFlags.isEnabled("astroWebUiKeepWifiStatus"), "Astro WebUI keep-Wi-Fi readback is safe and should remain visible")
        assertTrue(FeatureFlags.isEnabled("astroWebUiWifiStatus"), "Astro WebUI Wi-Fi readback is safe and should remain visible")
        assertTrue(FeatureFlags.isEnabled("astroWebUiTargetPreview"), "Astro WebUI GoTo preview is non-moving and should remain visible")
        assertTrue(FeatureFlags.isEnabled("omsRead"), "OMS read (824/825) verified")
        assertTrue(FeatureFlags.isEnabled("wifiScan"), "WiFi scan (770) verified")
        assertTrue(FeatureFlags.isEnabled("fileManager"), "File list (702) verified")
        assertTrue(FeatureFlags.isEnabled("fileManagerReadOnly"), "File browse is safe to leave on")
        assertTrue(FeatureFlags.isEnabled("astroWebUiMedia"), "Astro WebUI media reads are read-only")
        assertTrue(FeatureFlags.isEnabled("astroWebUiSolveStatus"), "Astro WebUI solve status is read-only")
        assertTrue(FeatureFlags.isEnabled("astroWebUiRunState"), "Astro WebUI run state is read-only")
        assertFalse(FeatureFlags.isEnabled("astroWebUiCapture"), "Astro WebUI shutter must remain off until lifecycle qualification")
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
        // systemSettings is ON by default, but toggle still works.
        assertTrue(FeatureFlags.isEnabled("systemSettings"))
        FeatureFlags.disable("systemSettings")
        assertFalse(FeatureFlags.isEnabled("systemSettings"))
        FeatureFlags.enable("systemSettings")
        assertTrue(FeatureFlags.isEnabled("systemSettings"))
    }

    @Test
    fun toggleFlipsEffectiveValue() {
        FeatureFlags.reset()
        // autoLevel now defaults to ON (live-confirmed). Toggle still works
        // bidirectionally — kiosk builds can flip it off without code edits.
        assertTrue(FeatureFlags.isEnabled("autoLevel"))
        FeatureFlags.toggle("autoLevel")
        assertFalse(FeatureFlags.isEnabled("autoLevel"))
        FeatureFlags.toggle("autoLevel")
        assertTrue(FeatureFlags.isEnabled("autoLevel"))
    }

    @Test
    fun resetClearsAllOverridesBackToCompileDefaults() {
        // Flip both on and off
        FeatureFlags.disable("systemSettings")
        FeatureFlags.enable("allowReboot")
        FeatureFlags.enable("limitsWrite")
        assertFalse(FeatureFlags.isEnabled("systemSettings"))
        assertTrue(FeatureFlags.isEnabled("allowReboot"))
        assertTrue(FeatureFlags.isEnabled("limitsWrite"))

        FeatureFlags.reset()

        // After reset, defaults are restored
        assertTrue(FeatureFlags.isEnabled("systemSettings"), "systemSettings verified → ON")
        assertFalse(FeatureFlags.isEnabled("allowReboot"), "allowReboot → OFF")
        assertFalse(FeatureFlags.isEnabled("limitsWrite"), "limitsWrite → OFF")
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
