import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.jetbrainsCompose)
}

kotlin {
    jvm()
    sourceSets {
        jvmMain.dependencies {
            implementation(project(":composeApp"))
            implementation(project(":shared"))
            implementation(compose.material3)
            implementation(compose.foundation)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.material3.window.size)
            // Skiko AWT native runtime, resolved per host OS (linux-x64 on the
            // dev box, windows-x64 when building on Windows). Replaces a
            // hardcoded linux-x64 pin that would leak into Windows builds.
            implementation(compose.desktop.currentOs)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
        }
    }
}

compose.desktop {
    application {
        mainClass = "dev.openpolaris.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "OpenPolaris"
            packageVersion = "1.0.0"
        }
    }
}
