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
        assertFalse("awk -F': *' '/SHA-256 digest/ {print $2}' | head -1 > apk-sha256.txt" in workflow)
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
