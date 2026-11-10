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
## Latest committed verification - 924901f

Commit under test: 924901f (feat: add qualified slow motion transport seam).

Command:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL (103 actionable tasks). All shared and Compose JVM
tests passed, including the five qualification-gated SLOW transport tests,
SLOW controller/wire replay tests, Astro WebUI capture tests and Bluetooth
tests. Android debug Kotlin compilation and APK assembly passed, and the
desktop distributable was created.

Focused SLOW verification also passed with 22 tests:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.QualifiedSlowMotionTransportTest --tests dev.openpolaris.core.domain.SlowMotionControllerTest --tests dev.openpolaris.core.protocol.SlowMotionWireTest --tests dev.openpolaris.core.protocol.SlowMotionWireReplayTest :shared:compileKotlinJvm --rerun-tasks --console=plain --no-daemon
~~~

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: 54243a5cf89d16fd10741fd078cd9e8e6afb2d99707cf96498d09185dd70f744
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-924901f.tar.gz
  SHA-256: d1ad5865e873adf38755c6b3c6b77124d21be59f1b641a42d688280e5cf9e80f

Existing warnings remained: Kotlin expect/actual, Java/Android deprecations
and the known libandroidx.graphics.path.so strip warning. The default SLOW
capability record remains unqualified; no live mount, camera, Astro WebUI or
physical SLOW qualification was performed. These artifacts are build evidence,
not hardware qualification.
## Latest committed verification - da6a17f

Commit under test: da6a17f (feat: add cross-platform support bundle export).

Command:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL (103 actionable tasks). All shared and Compose JVM
tests passed, including the support-bundle document/export tests, the evidence
centre contract, Astro WebUI capture tests, Bluetooth tests and SLOW transport
tests. Android debug Kotlin compilation and APK assembly passed, and the
desktop distributable was created.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: 065abe66c7a61eb8aecffbdc764dfecdaa4273124d68374a652408d2575310b2
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-da6a17f.tar.gz
  SHA-256: 2a525a5e420b2b16bca3d2a0bb018afc27e38f2c08b8d882242127783b37fdc8

Existing warnings remained: Kotlin expect/actual, Java/Android deprecations
and the known libandroidx.graphics.path.so strip warning. Support export is
host-side build evidence only; no live mount, camera, network or physical SLOW
qualification was performed.


## Latest committed verification - 4bda211

Commit under test: 4bda211 (fix: cancel support exports on disconnect).

Command:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL (103 actionable tasks). All shared and Compose JVM
tests passed, including SLOW transport/controller and wire replay tests,
Astro WebUI M/B shutter lifecycle tests, Bluetooth and route tests, capture
publication evidence, support-bundle document tests and the support-export
disconnect lifecycle regression. Android debug Kotlin compilation and APK
assembly passed, and the Linux x64 desktop distributable was created.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: d07c37b4acbd67f9073b6b81e3aea5cda6814b00acf934111e64290f5f9d119d
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-4bda211.tar.gz
  SHA-256: 46ddd09213b5b5442ec7920baa6a643bab95761b55c467b83bf78e48a3a52029

Existing warnings remained: Kotlin expect/actual, Java/Android deprecations,
the known libandroidx.graphics.path.so strip warning and Gradle 9
deprecation notices. No live mount, camera, Astro WebUI or physical SLOW
qualification was performed. The lifecycle regression and build artifacts
are host-side evidence, not hardware qualification.


## Latest committed verification - f2b52fe

Commit under test: f2b52fe (fix: own bridge and wake lifecycle jobs).

Command:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL (103 actionable tasks). All shared and Compose JVM
tests passed, including the bridge/Bluetooth session lifecycle cancellation
regressions, wake/connect race coverage, SLOW transport/controller and wire
replay tests, Astro WebUI M/B shutter lifecycle tests, route tests, capture
publication evidence and support-bundle export tests. Android debug Kotlin
compilation and APK assembly passed, and the Linux x64 desktop distributable
was created.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: 0b802d8496942949b1646fdb36f84637a119e61795ea138d77b0684328cd43bb
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-f2b52fe.tar.gz
  SHA-256: 78aaa173dcf8bda5f457b4646802b3f2a2f127d386cd7a7bb0fad9fd6d38be2f

Existing warnings remained: Kotlin expect/actual, Java/Android deprecations,
the known libandroidx.graphics.path.so strip warning and Gradle 9
deprecation notices. No live mount, camera, Astro WebUI or physical SLOW
qualification was performed. The lifecycle tests and artifacts are host-side
evidence, not hardware qualification.


## Latest committed verification - 5e44b80

Commit under test: 5e44b80 (fix: own capture dispatch lifecycle).

Command:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL (103 actionable tasks). All shared and Compose JVM
tests passed, including the queued capture-dispatch cancellation regression,
M/B lifecycle/publication and timeout tests, bridge/Bluetooth lifecycle
regressions, wake/connect race coverage, SLOW transport/controller and wire
replay tests, Astro WebUI capture tests, route tests and support-bundle tests.
Android debug Kotlin compilation and APK assembly passed, and the Linux x64
desktop distributable was created.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: 0ec648c80730fa94d9417da845fe12d14e4632bbe65d349646f5338cb6d04ba8
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-5e44b80.tar.gz
  SHA-256: e23882d3b109a0c425b6dbacda07dc4c95e985bc81e7097045ca8de932cee352

Existing warnings remained: Kotlin expect/actual, Java/Android deprecations,
the known libandroidx.graphics.path.so strip warning and Gradle 9
deprecation notices. No live mount, camera, Astro WebUI or physical SLOW
qualification was performed. The lifecycle tests and artifacts are host-side
evidence, not hardware qualification.


## Latest committed verification - b1b109d

Commit under test: b1b109d (fix: own simulator session startup).

Command:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL (103 actionable tasks). All shared and Compose JVM
tests passed, including the queued Demo-session cancellation regression,
session-marker coverage, capture lifecycle tests, bridge/Bluetooth lifecycle
tests, wake/connect race coverage, SLOW transport/controller and wire replay
tests, Astro WebUI capture tests, route tests and support-bundle tests. Android
debug Kotlin compilation and APK assembly passed, and the Linux x64 desktop
distributable was created.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: 9ceba00898c8224e361c9e563c5c84f926db1fb9d30fce756f1cbaf20f51a476
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-b1b109d.tar.gz
  SHA-256: 8a930ce925724ea23d85f7c6555e005745637d50b7fc4cca4e6f0d37b6ace877

Existing warnings remained: Kotlin expect/actual, Java/Android deprecations,
the known libandroidx.graphics.path.so strip warning and Gradle 9
deprecation notices. No live mount, camera, Astro WebUI or physical SLOW
qualification was performed. The lifecycle tests and artifacts are host-side
evidence, not hardware qualification.


## Latest committed verification - 35c75ed

Commit under test: 35c75ed (fix: hand off bridge ownership from wake).

Command:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL (103 actionable tasks). All shared and Compose JVM
tests passed, including the bridge/wake ownership handoff and disconnect
cancellation regressions, queued Demo-session cancellation, capture lifecycle
tests, SLOW transport/controller and wire replay tests, Astro WebUI M/B
capture tests, Bluetooth and route tests, and support-bundle tests. Android
debug Kotlin compilation and APK assembly passed, and the Linux x64 desktop
distributable was created.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: f204fafef473d5c9dec92d2babe494858e6a4e3c1eae98fa9163eddff8b4b761
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-35c75ed.tar.gz
  SHA-256: c2b0bdbf48dc8a1c3a78e1cf9afa6267dfabd22dd77f3a9822be822f6e2c6327

Existing warnings remained: Kotlin expect/actual, Java/Android deprecations,
the known libandroidx.graphics.path.so strip warning and Gradle 9
deprecation notices. No live mount, camera, Astro WebUI or physical SLOW
qualification was performed. The lifecycle tests and artifacts are host-side
evidence, not hardware qualification.


## Latest committed verification - 0975564

Commit under test: 0975564 (fix: preserve cancellation truth during teardown).

Command:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL (103 actionable tasks). All shared and Compose JVM
tests passed, including pending control-connect cancellation, bridge/wake
handoff and disconnect cancellation tests, queued Demo-session cancellation,
capture lifecycle tests, SLOW transport/controller and wire replay tests,
Astro WebUI M/B capture tests, Bluetooth and route tests, and support-bundle
tests. Android debug Kotlin compilation and APK assembly passed, and the Linux
x64 desktop distributable was created.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: 00f9be7f8e416692353e78e4a1a5f6c632f75e71fd4473de2e0efec84dfc38b7
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-0975564.tar.gz
  SHA-256: 7c505ce1e59e93033b42e12bbc5550872df5e08867e85f6fc46c04c3607f01bb

Existing warnings remained: Kotlin expect/actual, Java/Android deprecations,
the known libandroidx.graphics.path.so strip warning and Gradle 9
deprecation notices. No live mount, camera, Astro WebUI or physical SLOW
qualification was performed. The lifecycle tests and artifacts are host-side
evidence, not hardware qualification.


## Latest committed verification - 027f141

Commit under test: 027f141 (fix: own session marker persistence).

Command:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL (103 actionable tasks). Shared and Compose JVM tests
passed, including the gated session-marker persistence cancellation regression,
capture lifecycle, bridge/Bluetooth lifecycle, SLOW transport/controller and
wire replay, Astro WebUI and support-bundle tests. Android debug Kotlin
compilation and APK assembly passed, and the Linux x64 desktop distributable
was created.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: ae24c2d0486ad1bda78c4028e2b29853b1a706ac949652f187e9e59f0549ef72
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-027f141.tar.gz
  SHA-256: 9c6d8856bfc167eafe5e5af05ba6873003cf630df5d9375e36b09ee4bcb89fba

Existing warnings remained: Kotlin expect/actual, Java/Android deprecations,
the known libandroidx.graphics.path.so strip warning and Gradle 9
deprecation notices. No live mount, camera, Astro WebUI or physical SLOW
qualification was performed. This is host-side build evidence, not hardware
qualification.


## Latest committed verification - 31778ca

Commit under test: 31778ca (fix: own demo slow stop lifecycle).

Command:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL (103 actionable tasks). Shared and Compose JVM tests
passed, including the Demo SLOW disconnect-stop regression, SLOW transport and
wire replay tests, capture lifecycle, bridge/Bluetooth lifecycle, Astro WebUI
and support-bundle tests. Android debug Kotlin compilation and APK assembly
passed, and the Linux x64 desktop distributable was created.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: ab7060cd29f5b36ac2457ef464b0f17ff86933109a0d0cd1892d5b9d671ffec1
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-31778ca.tar.gz
  SHA-256: a3c30f179574eb436271b155b70244652926331318e93085d8439fd759193602

Existing warnings remained: Kotlin expect/actual, Java/Android deprecations,
the known libandroidx.graphics.path.so strip warning and Gradle 9
deprecation notices. No live mount, camera, Astro WebUI or physical SLOW
qualification was performed. This is host-side build evidence, not hardware
qualification.


## Latest committed verification - c3d8197

Commit under test: c3d8197 (fix: guard queued emergency stop).

Command:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL (103 actionable tasks). Shared and Compose JVM tests
passed, including the queued Demo-session emergency-stop regression, session
marker persistence and cancellation regressions, capture lifecycle, bridge/
Bluetooth lifecycle, SLOW transport/controller and wire replay, Astro WebUI
and support-bundle tests. Android debug Kotlin compilation and APK assembly
passed, and the Linux x64 desktop distributable was created.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: 98f2c8eee4aea22c541357da3c710c1426c1e94a42158fdf31669b0aefbbb274
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-c3d8197.tar.gz
  SHA-256: fda4bf86dba6da8d4afae07f73fee8ece72d8ba3aae433de4cdc08b9649d47ec

Existing warnings remained: Kotlin expect/actual, Java/Android deprecations,
the known libandroidx.graphics.path.so strip warning and Gradle 9
deprecation notices. No live mount, camera, Astro WebUI or physical SLOW
qualification was performed. This is host-side build evidence, not hardware
qualification.
