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
            runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-linux-x64:0.8.18")
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
