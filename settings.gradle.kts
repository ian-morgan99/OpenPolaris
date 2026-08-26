rootProject.name = "open-polaris"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

include(":shared")
include(":composeApp")
include(":desktopApp")
include(":androidApp")
include(":tools:cli-probe")
