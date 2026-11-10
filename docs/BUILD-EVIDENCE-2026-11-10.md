# OpenPolaris build evidence — 2026-11-10

This record covers the owner-driven Demo SLOW milestone and the platform
artifacts built from source commit
cecca70d8b7aa9626912a24ae33663a1c01073d9.

## Verified source and tests

- Worktree was clean at the time of the desktop provenance check.
- Desktop app-image provenance check passed:
  :desktopApp:verifyDesktopAppImage
- JVM shared/domain tests passed:
  :shared:jvmTest
- Compose UI JVM tests passed:
  :composeApp:jvmTest
- Desktop JVM compilation passed:
  :composeApp:compileKotlinJvm
- Android debug Kotlin compilation passed:
  :androidApp:compileDebugKotlinAndroid
- Fresh Android debug APK assembly passed:
  :androidApp:assembleDebug

The test/build warnings are existing Kotlin/Java and Android deprecation warnings;
no warning was promoted to a build failure.

## Generated artifacts

These are generated under ignored build directories and must be rebuilt for a
different source commit.

| Artifact | Build path | SHA-256 |
|---|---|---|
| Android debug APK | androidApp/build/outputs/apk/debug/androidApp-debug.apk | ba175a27d8425972d7402734f71003326610b05ac4d2f8ce43a79ac6f4fafb95 |
| Linux x64 app-image archive | /tmp/OpenPolaris-linux-x64-cecca70d8b7aa9626912a24ae33663a1c01073d9.tar.gz | 82564f173bee6fb8cc9cbe15623c18a06a1852367dffdb9f08aed14b19d645ce |

The Linux archive was made from
desktopApp/build/compose/binaries/main/app/OpenPolaris and the embedded build
metadata matched the source commit and clean-checkout requirement. The archive
in /tmp is a local handoff artifact, not a tracked release asset.

## Release and qualification boundaries

- A local signed Android release rebuild was attempted but stopped at
  :androidApp:validateSigningRelease because the ignored
  androidApp/keystore/release.keystore is not present. No signing material was
  created, changed or recovered.
- The repository release workflow still requires its protected signing secrets
  to materialise the release keystore in CI.
- Windows MSI/EXE and macOS packaging were not claimed from this Linux host.
- The Android debug APK and Linux app-image are build evidence, not a physical
  hardware qualification result.
- Live SLOW firmware mapping, acknowledgement/watchdog behavior, signs and
  physical stop/reconnect evidence remain blocked. The UI continues to hard-gate
  532/533/534 and AC 4A live transport.
- Actual thumbnail/full-file transfer formats remain blocked pending corpus
  replay and hardware evidence.

## Reproduction

From a clean checkout at the source commit, run:

    ./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid
    ./gradlew :desktopApp:createDistributable :desktopApp:verifyDesktopAppImage :androidApp:assembleDebug

Then compute SHA-256 with sha256sum and keep the source commit alongside the
artifact hashes.
