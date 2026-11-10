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
- `SlowMotionWireTest` and `SlowMotionWireReplayTest` passed, including all-axis
  replay, stale host lease expiry, malformed-frame rejection and repeated STOP
  rejection; the complete `:shared:jvmTest` suite remains green.
- This is replay/fake-head preparation only. It does not connect the formatter
  to `MountSession`, enable live SLOW, or qualify target-firmware stop behavior.
- Follow-up source commits: formatter `5cfdc7614b78ce2bb8a3f27c9ccfb3f2608923b9`,
  replay suite `756508060a6dcb74d7421acd2375e8e9ee6960c3`, and protocol record
  `915d82dfd2dcb8c750d748f6afcd16dd605d7936`.

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

## Latest committed verification - c8584e9

Source commit: `c8584e9` (`fix: preserve explicit SLOW live hard gate`).

Full verification passed from this source state:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon
~~~

- All shared and Compose JVM tests passed, including `AstroWebUiSolveTest`, `AppViewModelAstroWebUiSolveTest`, `SlowMotionCapabilityTest` and `SlowMotionPaneSafetyContractTest`.
- Android debug Kotlin compilation and APK assembly passed.
- Desktop distributable creation passed.
- Existing warnings only: Kotlin expect/actual, Java/Android deprecations and the known `libandroidx.graphics.path.so` strip warning.

| Artifact | Path | SHA-256 |
|---|---|---|
| Android debug APK | `androidApp/build/outputs/apk/debug/androidApp-debug.apk` | `125f2b4ba58ca1ef716e7992d345af71d5578d16fa65d41c24a706455bb7c870` |
| Linux x64 app-image archive | `/tmp/OpenPolaris-linux-x64-c0ab834.tar.gz` | `39db616423c6b51b50bcab8a982e11a263d5729bdd9257cbe06b84a4eb27f00b` |

The SLOW live hard gate remains false. These are source, test and build artifacts only; they are not physical qualification of a mount, firmware route, acknowledgement, watchdog or stop behavior.

## Latest committed verification - c548e28

Commit under test: c548e28b72c85f13933837d67444d45a219f070d.

Command:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL (103 actionable tasks). All shared and Compose JVM
tests passed, Android debug Kotlin compilation and APK assembly passed, and the
desktop distributable was created.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: 206eaae3dc908ef3862cd12f5b79b8d13f8b4307b5259fa95b69fc51c6a306b3
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-c548e28.tar.gz
  SHA-256: 907cdc3eda4a723623e5ca349d5ada0828190cc427ffbb92f1f224efa85b915f

The build emits existing deprecation warnings, including Android Bluetooth scan
API warnings and JVM URL/readLine warnings. No live mount, camera, Astro
WebUI or physical SLOW qualification was performed; artifact success is not
hardware qualification.

## Latest committed verification - f5f3f90

Commit under test: f5f3f90 (feat: add common motion emergency stop).

Command:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL (103 actionable tasks). All shared and Compose JVM
tests passed, including the FastJog emergency-stop and ViewModel owner-clear
tests. Android debug Kotlin compilation and APK assembly passed, and the Linux
desktop distributable was created.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: af2b80b11a6f2aaf2477e55ce37a44ade0ef6d40e541e061f59b825c8e2fa984
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-f5f3f90.tar.gz
  SHA-256: 58e5ac3fef3f82187d186f6d226c754a2cc150c032c33919f0d3939917e07774

Existing warnings remained: Kotlin expect/actual, Java/Android deprecations
and the known libandroidx.graphics.path.so strip warning. No live mount,
camera, Astro WebUI or physical SLOW qualification was performed; artifact
success is not hardware qualification.

## Latest committed verification - 17d317b

Commit under test: 17d317b (fix: settle SLOW faulted emergency stops).

Command:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL (103 actionable tasks). All shared and Compose JVM
tests passed, including the SLOW armed/faulted emergency-stop tests, the
global motion safety chrome test and Astro WebUI capture lifecycle tests.
Android debug Kotlin compilation and APK assembly passed, and the Linux
desktop distributable was created.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: 4081b831125a8563f4721c23eed8262378846c8b0a928c55f19e3aaf5200f6d2
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-17d317b.tar.gz
  SHA-256: ebdb0854072c83d953d6944d4c0b25554cd911f535d7b5662318913ac94ee1d4

Existing warnings remained: Kotlin expect/actual, Java/Android deprecations
and the known libandroidx.graphics.path.so strip warning. No live mount,
camera, Astro WebUI or physical SLOW qualification was performed; artifact
success is not hardware qualification.
