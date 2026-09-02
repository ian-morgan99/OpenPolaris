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

android {
    namespace = "dev.openpolaris.android"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.openpolaris.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.1.2"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}
