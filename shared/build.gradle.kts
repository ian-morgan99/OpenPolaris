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
                freeCompilerArgs += listOf("-Xexpect-actual-classes")
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
        androidMain.dependencies {
            // FilePicker uses ActivityResultContracts.OpenDocument for
            // Storage Access Framework picking. We hold the launcher on a
            // static registry that the host Activity populates.
            // `activity-compose` brings in `androidx.activity:activity`
            // transitively, which is what we need for ComponentActivity
            // and the result-launcher types.
            implementation(libs.androidx.activity.compose)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            // kotlinx-coroutines-debug artifact carries the JVM
            // debug agent. The leak test (SessionShutdownLeakTest,
            // issue #20 / 3a.1) doesn't actually need DebugProbes'
            // dumpCoroutines() API on JVM 1.9.0 — that variant is
            // print-to-stream, not list-returning — but the artifact
            // is also what installs the background coroutine probe
            // that makes `Dispatchers.Default` parking threads
            // observable. Belt-and-braces for future expansion of
            // the leak test to per-coroutine counts.
            implementation(libs.kotlinx.coroutines.debug)
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
    // KMP's `commonMain` resources (catalog.json, comets.json, …) are
    // wired into jvmTest automatically but not into AGP's unit-test
    // variants. CatalogTest, InMemoryCatalogTest, and friends load
    // these via `classLoader.getResource(...)`, so we have to make the
    // same files visible to `testDebugUnitTest` / `testReleaseUnitTest`
    // — otherwise those variants throw "catalog.json not on test classpath"
    // even though jvmTest is green.
    listOf("test", "testDebug", "testRelease").forEach { name ->
        sourceSets[name].resources.srcDirs("src/commonMain/resources")
    }
}

// Always emit per-class XML results (CI evidence + screenshot-friendly HTML reports).
tasks.withType<Test>().configureEach {
    reports.html.required = true
    reports.junitXml.required = true
    testLogging {
        events("passed", "failed", "skipped")
    }
}
