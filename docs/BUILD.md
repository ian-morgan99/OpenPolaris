# Building OpenPolaris

This document captures the toolchain setup required to build OpenPolaris
desktop and Android targets.

## Toolchain

| Tool         | Version         | Location                                |
|--------------|-----------------|-----------------------------------------|
| JDK          | Temurin 21      | `/home/ian/jdk/jdk-21.0.5+11`           |
| Android SDK  | API 34/35/36    | `/home/ian/android-sdk`                 |
| Kotlin       | 2.0.21          | resolved by Gradle from Maven Central   |
| AGP          | 8.7.2           | resolved by Gradle from Maven Central   |
| Gradle       | 8.10.2          | via `./gradlew` wrapper                 |

The host **does not** have a JDK 17 toolchain. The Android build is
configured to compile to Java 17 bytecode using a JDK 21 toolchain.
See `gradle.properties` (`org.gradle.java.home` +
`org.gradle.java.installations.paths`) and `shared/build.gradle.kts`
(removed `kotlin.jvmToolchain(17)`, kept `compileOptions VERSION_17` +
`jvmTarget = "17"`).

## Environment

Set `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) before running Gradle:

```bash
export ANDROID_HOME=/home/ian/android-sdk
export ANDROID_SDK_ROOT=/home/ian/android-sdk
```

## Targets

```bash
# Desktop JVM (runs the local PolarisSim stub server / app)
./gradlew :desktopApp:run

# All JVM unit tests (shared + composeApp)
./gradlew :shared:jvmTest :composeApp:jvmTest

# Android debug APK
./gradlew :androidApp:assembleDebug
# Output: androidApp/build/outputs/apk/debug/androidApp-debug.apk (~9.6 MB)

# Full build (all targets)
./gradlew build
```

## Windows desktop executable

The `:desktopApp` module applies the Compose Desktop application plugin, so it
can produce a self-contained Windows distribution (bundled JRE +
`OpenPolaris.exe`) via jpackage. Run these **on** a Windows machine with JDK 17
or 21 installed — no VS Code required:

```bat
:: Self-contained folder at desktopApp\build\compose\distributions\OpenPolaris\
gradlew.bat :desktopApp:createDistributable

:: MSI installer at desktopApp\build\compose\packages\ (WiX is auto-downloaded)
gradlew.bat :desktopApp:packageMsi
```

The Skiko AWT runtime resolves per host OS (`compose.desktop.currentOs` in
[`desktopApp/build.gradle.kts`](../desktopApp/build.gradle.kts)), so the Windows
build pulls the windows-x64 native libraries automatically. Alternatively,
trigger the **CI** workflow from the Actions tab: the `windows-desktop` job
runs on `windows-latest`, builds both artifacts, and uploads them for download.

## Why Temurin 21 instead of 17

AGP 8.7.2 will happily consume JDK 21 to compile to Java 17 bytecode.
The previous `kotlin.jvmToolchain(17)` block forced Gradle to look
specifically for a JDK 17 install. With no JDK 17 on the host, the
build aborted with `Cannot find JDK 17 toolchain`.

Removing the explicit toolchain pin and using `compileOptions` +
`jvmTarget` for the source/target level is the supported idiom for
"compile to Java N with whatever JDK the daemon is using".
