# OpenPolaris build evidence — 2026-11-10

This record covers the owner-driven Demo SLOW milestone and the platform
artifacts built from source commit
14690e411dc3ececdd40568a9d39b515551e8dfb.

## Verified source and tests

- Worktree was clean at the time of the desktop provenance check.
- Desktop app-image provenance check passed:
  :desktopApp:verifyDesktopAppImage
- JVM shared/domain tests passed:
  :shared:jvmTest
- Compose UI JVM tests passed:
  :composeApp:jvmTest
- Astro WebUI media parser and AppViewModel gallery tests passed:
  AstroWebUiMediaTest, AppViewModelAstroWebUiMediaTest, category-filter coverage
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
| Android debug APK | androidApp/build/outputs/apk/debug/androidApp-debug.apk | adea4838e0855cb7f3acaacc674bcab8d6c26dc8e34042c7c27fc126cadd15d1 |
| Linux x64 app-image archive | /tmp/OpenPolaris-linux-x64-14690e411dc3ececdd40568a9d39b515551e8dfb.tar.gz | 50c961695c92fe28454a2c0368fe060974ffeb65b01c477375431e6dce3ae0c |

The Linux archive was made from
desktopApp/build/compose/binaries/main/app/OpenPolaris and the embedded build
metadata matched the source commit and clean-checkout requirement. The archive
in /tmp is a local handoff artifact, not a tracked release asset.

## Follow-up SLOW wire-seam verification

- Source-aligned host-only formatter added after the prior build record:
  `shared/src/commonMain/kotlin/dev/openpolaris/core/protocol/SlowMotionWire.kt`.
- The formatter pins M1/M2/M3 to 532/533/534, formats
  `key:<0|1>;state:<0|1|2>;level:<1..5>;`, emits subtype 3 through the
  subtype-aware builder, and turns a retained-point STOP into state 0.
- `SlowMotionWireTest` and the complete `:shared:jvmTest` suite passed.
- This is replay/fake-head preparation only. It does not connect the formatter
  to `MountSession`, enable live SLOW, or qualify target-firmware stop behavior.

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
