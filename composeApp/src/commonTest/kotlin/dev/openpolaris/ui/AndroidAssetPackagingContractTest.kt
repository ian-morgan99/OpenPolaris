package dev.openpolaris.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Source-level guard for issue #87's Android resource packaging contract.
 * The Gradle verifyCommonRuntimeAssets task performs the actual merged-assets
 * check; this test keeps the cross-module copy and explicit verification hook
 * visible to the common JVM contract suite.
 */
class AndroidAssetPackagingContractTest {

    private fun sourceText(relativePath: String): String {
        var directory: File? = File(".").canonicalFile
        repeat(8) {
            val candidate = directory?.let { File(it, relativePath) }
            if (candidate?.isFile == true) return candidate.readText(Charsets.UTF_8)
            directory = directory?.parentFile
        }
        error("Cannot read $relativePath - searched current directory and its parents")
    }

    @Test
    fun androidBuildCopiesAndVerifiesAllRuntimeResources() {
        val build = sourceText("androidApp/build.gradle.kts")
        assertTrue("syncCommonRuntimeAssets" in build)
        assertTrue("""project(":shared").file("src/commonMain/resources")""" in build)
        assertTrue("""project(":composeApp").file("src/commonMain/resources")""" in build)
        assertTrue("""sourceSets["main"].assets.srcDir(commonRuntimeAssetsDir)""" in build)
        assertTrue("verifyCommonRuntimeAssets" in build)
        for (asset in listOf(
            "USER-MANUAL.md",
            "screenshots/openpolaris-desktop-v1.0.0.png",
            "catalog.json",
            "stars.json",
            "ngc.json",
            "comets.json",
        )) {
            assertTrue(
                "\"$asset\"" in build,
                "Android merged-assets verification must name $asset explicitly",
            )
        }
    }
}
