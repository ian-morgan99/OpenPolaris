package dev.openpolaris.ui

import dev.openpolaris.core.domain.readResourceBytes
import dev.openpolaris.core.domain.readResourceText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Pins the in-app user manual: the file is bundled in the APK / JAR
 * and the headline content is present.
 *
 * Background: the user manual lives at `docs/USER-MANUAL.md` on
 * GitHub and is also bundled into the app under
 * `composeApp/src/commonMain/resources/USER-MANUAL.md` so that
 * [UserManualPane] can open it offline. This test runs on the JVM
 * (CI) and Android (runtime); on both, the same `readResourceText`
 * expect/actual resolves the bundled file. Failing this test means
 * the APK / JAR no longer contains the manual and users on a mount
 * with no network can no longer read the documentation from the
 * Guide callout.
 */
class UserManualPaneBundledAssetTest {

    @Test
    fun userManualIsBundledAndStartsWithExpectedHeading() {
        val text = readResourceText("USER-MANUAL.md")
        assertNotNull(text, "USER-MANUAL.md must be bundled in the application resources")
        assertTrue(text.isNotBlank(), "USER-MANUAL.md must not be empty")
        // The first non-blank line must be the H1 title. This is
        // what [UserManualPane] suppresses from rendering (it
        // draws its own "User Manual" header) so a regression
        // here would also break that suppression logic.
        val firstNonBlank = text.lineSequence().firstOrNull { it.isNotBlank() }
        assertEquals(
            "# Open Polaris — User Manual",
            firstNonBlank,
            "USER-MANUAL.md must start with the canonical H1 title so UserManualPane can " +
                "suppress it and render its own header instead.",
        )
        // The 12 sections the manual promises (Install, Connection, Main screen, Callouts,
        // Feature flags, Reconnect, 3D/VR, Troubleshooting, Safety, Smoke test, ...).
        // A spot check on a couple of known sections is enough to catch a truncated bundle.
        assertTrue(
            "## 1. Install" in text,
            "USER-MANUAL.md must contain the Install section heading.",
        )
        assertTrue(
            "## 7. Reconnect prompt" in text,
            "USER-MANUAL.md must contain the Reconnect prompt section heading.",
        )
    }

    @Test
    fun desktopScreenshotAssetIsBundled() {
        val bytes = readResourceBytes("screenshots/openpolaris-desktop-v1.0.0.png")
        assertNotNull(
            bytes,
            "screenshots/openpolaris-desktop-v1.0.0.png must be bundled so the in-app manual " +
                "can render the embedded screenshot. The PNG is referenced from " +
                "USER-MANUAL.md and rendered by [UserManualPane.ManualImage].",
        )
        // PNG magic header: 89 50 4E 47 0D 0A 1A 0A
        assertTrue(bytes.size >= 8, "screenshot asset must be non-trivial in size")
        assertEquals(
            0x89.toByte(), bytes[0],
            "screenshot asset must start with the PNG magic byte 0x89",
        )
        assertEquals(
            0x50.toByte(), bytes[1],
            "screenshot asset must have the 'P' PNG magic byte at offset 1",
        )
        assertEquals(
            0x4E.toByte(), bytes[2],
            "screenshot asset must have the 'N' PNG magic byte at offset 2",
        )
        assertEquals(
            0x47.toByte(), bytes[3],
            "screenshot asset must have the 'G' PNG magic byte at offset 3",
        )
    }
}
