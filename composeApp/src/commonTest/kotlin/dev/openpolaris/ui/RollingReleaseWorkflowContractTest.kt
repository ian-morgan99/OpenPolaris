package dev.openpolaris.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RollingReleaseWorkflowContractTest {
    private fun source(path: String): String {
        var directory: java.io.File? = java.io.File(".").canonicalFile
        repeat(10) {
            val candidate = directory?.let { java.io.File(it, path) }
            if (candidate?.isFile == true) return candidate.readText(Charsets.UTF_8)
            directory = directory?.parentFile
        }
        error("Cannot read $path")
    }

    @Test
    fun rollingApkPublishesArtifactAndSignerDigestsSeparately() {
        val workflow = source(".github/workflows/publish-rolling.yml")

        assertTrue("sha256sum \"\${APK}\" > apk-sha256.txt" in workflow)
        assertTrue("apk-signer-sha256.txt" in workflow)
        assertTrue("test -s apk-sha256.txt" in workflow)
        assertTrue("test -s apk-signer-sha256.txt" in workflow)
        assertTrue("printf '%s  androidApp-release.apk\\n' \"\$digest\" > out/android/apk-sha256.txt" in workflow)
        assertFalse("awk -F': *' '/SHA-256 digest/ {print $2}' | head -1 > apk-sha256.txt" in workflow)
    }

    @Test
    fun rollingReleasePublishesOnlyFromTheFinalSameShaJob() {
        val workflow = source(".github/workflows/publish-rolling.yml")

        assertTrue("uses: actions/upload-artifact@v4" in workflow)
        assertTrue("name: rolling-android-\${{ github.sha }}" in workflow)
        assertTrue("name: rolling-windows-\${{ github.sha }}" in workflow)
        assertTrue("  publish:" in workflow)
        assertTrue("needs: [android, windows]" in workflow)
        assertTrue("Move rolling tag only after all required builds pass" in workflow)
        assertTrue("Remove stale rolling assets" in workflow)
        assertTrue("OpenPolaris-release-manifest.txt" in workflow)
        assertTrue("sha256sum -c apk-sha256.txt" in workflow)
        assertTrue("git/ref/tags/latest" in workflow)
        assertFalse("Move rolling tag to this commit" in workflow)
        assertFalse("Upload APK to rolling prerelease" in workflow)
        assertFalse("Upload Windows artifacts to rolling prerelease" in workflow)
        assertTrue(
            workflow.split("softprops/action-gh-release@v2").size - 1 == 1,
            "the final publish job must be the only rolling-release asset writer",
        )
    }

    @Test
    fun rollingReleaseIsPushOrManualOnly() {
        val workflow = source(".github/workflows/publish-rolling.yml")

        assertTrue("push:" in workflow)
        assertTrue("branches: [main]" in workflow)
        assertTrue("workflow_dispatch:" in workflow)
        assertFalse("schedule:" in workflow)
        assertFalse("cron:" in workflow)
    }

}
