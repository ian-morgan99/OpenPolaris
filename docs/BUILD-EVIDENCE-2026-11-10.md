# OpenPolaris build evidence — 2026-11-10

This record covers the owner-driven Demo SLOW milestone and the platform
artifacts built from source commit
9535fd903615ca288589ecd2c0f2a11fbaceafe1.

## Verified source and tests

- Worktree was clean at the time of the desktop provenance check.
- Desktop app-image provenance check passed:
  :desktopApp:verifyDesktopAppImage
- JVM shared/domain tests passed:
  :shared:jvmTest
- Compose UI JVM tests passed:
  :composeApp:jvmTest
- Astro WebUI media parser and AppViewModel gallery tests passed:
  AstroWebUiMediaTest, AppViewModelAstroWebUiMediaTest
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
| Android debug APK | androidApp/build/outputs/apk/debug/androidApp-debug.apk | 736dda7d9d634c4ba78b4454c436421abef1985fd683e9f672fa07131cb2f382 |
| Linux x64 app-image archive | /tmp/OpenPolaris-linux-x64-9535fd903615ca288589ecd2c0f2a11fbaceafe1.tar.gz | 6d01eb580811a1729531f05e04408eb3f20d5d025dc31d35b14e6f0fe85de678 |

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
- Native thumbnail/full-file transfer formats remain blocked pending corpus replay and
  hardware evidence. The optional source-aligned Astro WebUI HTTP list/thumb/full
  path is covered by parser, policy and view-model tests but has not been
  physically exercised against a mount in this environment.

## Reproduction

From a clean checkout at the source commit, run:

    ./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid
    ./gradlew :desktopApp:createDistributable
    ./gradlew :desktopApp:verifyDesktopAppImage
    ./gradlew :androidApp:assembleDebug

Then compute SHA-256 with sha256sum and keep the source commit alongside the
artifact hashes. The desktop provenance verifier must run after the distributable
is rebuilt from the same clean source commit.
