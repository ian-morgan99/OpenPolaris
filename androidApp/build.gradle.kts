import org.gradle.api.tasks.Sync

import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.jetbrainsCompose)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions { jvmTarget = "17" }
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(project(":composeApp"))
            implementation(project(":shared"))
            implementation(compose.material3)
            implementation(libs.androidx.activity.compose)
            implementation(libs.material3.window.size)
        }
    }
}

// Release signing (issue #44).
//
// Before v0.1.16 the release variant used the per-machine debug keystore,
// which produced an APK whose signature fingerprint differed from any
// locally-installed debug build. The result was `INSTALL_FAILED_UPDATE_INCOMPATIBLE`
// on the user's device, surfaced as "app not installed because the package
// is not valid". We now sign release with a single, deterministic keystore
// committed at `androidApp/keystore/release.keystore` (see
// `androidApp/key.properties.example` for the file format and a template).
//
// The keystore binary is the trust anchor — committing it is a deliberate
// trade-off for a single-maintainer open-source project: reproducibility
// of signed releases matters more than hiding the trust anchor. The
// password lives in `androidApp/key.properties` (gitignored). On CI, the
// same file can be regenerated from `KEYSTORE_BASE64` + `KEYSTORE_PASSWORD`
// + `KEY_ALIAS` + `KEY_PASSWORD` secrets before running the build.
// The Android host owns the shared resource reader, so copy both KMP resource
// trees into the application assets directory. AGP does not automatically merge
// commonMain/resources from sibling KMP modules into androidApp/assets.
val commonRuntimeAssetsDir = layout.buildDirectory.dir("generated/commonRuntimeAssets")
val syncCommonRuntimeAssets = tasks.register<Sync>("syncCommonRuntimeAssets") {
    from(project(":shared").file("src/commonMain/resources"))
    from(project(":composeApp").file("src/commonMain/resources"))
    into(commonRuntimeAssetsDir)
}

val androidAppDir = rootProject.projectDir.resolve("androidApp")
val keystorePropsFile = androidAppDir.resolve("key.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        load(keystorePropsFile.inputStream())
    }
}

android {
    namespace = "dev.openpolaris.android"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.openpolaris.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 19
        versionName = "0.1.19"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        // Enables BuildConfig.java generation. The Android host reads
        // APPLICATION_ID / VERSION_NAME / VERSION_CODE from it and passes
        // them into the UI so the Settings dialog can show the running
        // build identity — important when users have a third-party fork
        // installed under a similar id (see issue #43).
        buildConfig = true
    }
    sourceSets["main"].assets.srcDir(commonRuntimeAssetsDir)

    packaging {
        // SSHJ (the Android firmware-delivery seam) pulls in BouncyCastle,
        // whose bcpkix/bcutil/bcprov jars each ship an OSGi manifest at the
        // same path. Android ignores OSGi metadata, so keep only one copy
        // instead of failing the resource merge on the duplicates.
        resources {
            excludes += setOf(
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/OSGI-INF/MANIFEST.MF",
            )
        }
    }
    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                // Paths in `key.properties` are relative to the `androidApp/`
                // module directory (where the file itself lives), so resolve
                // from `androidAppDir` rather than `rootProject.projectDir`.
                storeFile = androidAppDir.resolve(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        debug {
            // Allow the regression-test build to coexist with the stable
            // release installation. This avoids uninstalling the user's app
            // and preserves its settings/data during physical A/B testing.
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
        release {
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            // If `key.properties` is missing, this build falls back to the
            // unsigned default. CI must supply `key.properties` (or its
            // decoded equivalent) before running `:androidApp:assembleRelease`.
        }
    }
}


// A real merged-assets check protects the Android runtime reader, not just the
// JVM classpath tests. Keep the required set explicit so a new resource cannot
// silently disappear from the phone build.
val verifyCommonRuntimeAssets = tasks.register("verifyCommonRuntimeAssets") {
    dependsOn("mergeDebugAssets", "mergeReleaseAssets")
    doLast {
        val required = listOf(
            "USER-MANUAL.md",
            "screenshots/openpolaris-desktop-v1.0.0.png",
            "catalog.json",
            "stars.json",
            "ngc.json",
            "comets.json",
        )
        val variants = mapOf(
            "debug" to layout.buildDirectory.dir("intermediates/assets/debug/mergeDebugAssets"),
            "release" to layout.buildDirectory.dir("intermediates/assets/release/mergeReleaseAssets"),
        )
        variants.forEach { (variant, directory) ->
            val root = directory.get().asFile
            required.forEach { path ->
                check(root.resolve(path).isFile) {
                    "Missing Android $variant runtime asset: $path in ${root.absolutePath}"
                }
            }
        }
    }
}

tasks.configureEach {
    // AGP's merge and lint-model tasks consume the generated directory through
    // the Android source-set model, so declare the producing task explicitly
    // for every asset/lint consumer. This also keeps Gradle 8 validation from
    // accepting a locally ordered build that fails on a clean CI runner.
    if (name != "syncCommonRuntimeAssets" && (name.contains("Assets") || name.contains("Lint"))) {
        dependsOn(syncCommonRuntimeAssets)
    }
    if (name == "assembleDebug" || name == "assembleRelease") {
        finalizedBy(verifyCommonRuntimeAssets)
    }
}
