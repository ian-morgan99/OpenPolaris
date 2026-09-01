plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.jetbrainsCompose)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions { jvmTarget = "17" }
        }
    }
    jvm()

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "OpenPolarisUI"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.components.resources)
            implementation(libs.material3.window.size)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        val jvmMain by sourceSets.getting {
            dependencies {
                // Skiko + AWT runtime for the Compose Desktop window.
                // Resolved per-current-OS (Linux x64 on the dev box);
                // `compose.desktop.currentOs` is the canonical BOM-style
                // selector that pulls in `skiko-awt-runtime-linux-x64`,
                // `runtime-desktop`, `foundation-desktop`, etc.
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

android {
    namespace = "dev.openpolaris.ui"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

// ---- Desktop run task ----
// We don't use the `compose.desktop { application { ... } }` DSL block
// because in Compose 1.7.3 the `desktop-application` plugin marker was
// dropped (the multiplatform `org.jetbrains.compose` plugin absorbed
// the responsibility) and the DSL accessor `compose.desktop` is
// therefore not generated for this project. Instead we hand-roll a
// `run` task that forks the JVM against the built jvmJar.
//
// We build a "slim" jvmJar that contains ONLY our compiled Kotlin
// classes (no transitive jars packed in) and add the full runtime
// classpath (skiko + compose.runtime + ...) to the JavaExec classpath
// separately, so the JVM sees the real `.so` files inside
// `skiko-awt-runtime-linux-x64.jar` instead of a jar-of-jars.
//
// Note: the standard `packageDistribution` task is NOT registered here.
// Users who need a native installer (deb/msi/dmg) should run a one-off
// Gradle init script that applies `org.jetbrains.compose:compose-gradle-plugin:1.7.3`
// in standalone mode, or downgrade to Compose 1.7.1 which still ships
// the marker artifact.
val jvmJar = tasks.named<org.gradle.jvm.tasks.Jar>("jvmJar") {
    manifest {
        attributes["Main-Class"] = "dev.openpolaris.ui.MainKt"
    }
}

tasks.register<JavaExec>("run") {
    group = "application"
    description = "Runs the OpenPolaris Desktop UI on the local JVM."
    dependsOn(jvmJar)
    classpath = files(jvmJar) + files(configurations.named("jvmRuntimeClasspath"))
    mainClass.set("dev.openpolaris.ui.MainKt")
    jvmArgs("-Djava.awt.headless=false")
}
