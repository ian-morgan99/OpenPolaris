import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidLibrary)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
    jvm()

    // iOS targets — enabled now so protocol/domain stay honest about platform-free code.
    // Full iOS app shell lands in v2 (see ARCHITECTURE.md §0).
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { target ->
        target.binaries.framework {
            baseName = "OpenPolarisKit"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            // `api` so downstream modules (composeApp) can name Json in
            // their call sites without re-declaring the dependency. The
            // exposure is intentional: SessionStore's public default
            // parameter is `Json = DEFAULT_JSON`, so callers that want to
            // pass a custom Json must be able to reference the type.
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "dev.openpolaris.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Always emit per-class XML results (CI evidence + screenshot-friendly HTML reports).
tasks.withType<Test>().configureEach {
    reports.html.required = true
    reports.junitXml.required = true
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
    }
}
