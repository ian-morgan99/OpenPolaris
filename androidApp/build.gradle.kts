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
        versionCode = 8
        versionName = "0.1.7"
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
}
