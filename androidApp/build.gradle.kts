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
        versionCode = 16
        versionName = "0.1.16"
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
