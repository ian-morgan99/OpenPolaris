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


## Latest committed verification - 90cd25f

Commit under test: 90cd25f (fix: own queued fast jog release).

Command:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL (103 actionable tasks). Shared and Compose JVM tests
passed, including the queued fast-jog release/disconnect regression, session
lifecycle and cancellation regressions, capture lifecycle, bridge/Bluetooth
lifecycle, SLOW transport/controller and wire replay, Astro WebUI and
support-bundle tests. Android debug Kotlin compilation and APK assembly
passed, and the Linux x64 desktop distributable was created.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: 8678fb1be150aea4747f38a9f32e209a30c83b6966b181582b27e134e4da47d0
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-90cd25f.tar.gz
  SHA-256: e417d2756737324178b66a89342362d23725d127fb4546d72a3d42c7a70c196c

Existing warnings remained: Kotlin expect/actual, Java/Android deprecations,
the known libandroidx.graphics.path.so strip warning and Gradle 9
deprecation notices. No live mount, camera, Astro WebUI or physical SLOW
qualification was performed. This is host-side build evidence, not hardware
qualification.


## Latest committed verification - 76af9a6

Commit under test: 76af9a6 (fix: cancel queued goto on disconnect).

Command:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL (103 actionable tasks). Shared and Compose JVM tests
passed, including the queued GoTo/cancel/disconnect regression, fast-jog
release ownership, session lifecycle and cancellation regressions, capture
lifecycle, bridge/Bluetooth lifecycle, SLOW transport/controller and wire
replay, Astro WebUI and support-bundle tests. Android debug Kotlin
compilation and APK assembly passed, and the Linux x64 desktop distributable
was created.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: ce23b65b3bf21614012bfd8d093309472b910cb3363b3bef4a6ef39122db769b
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-76af9a6.tar.gz
  SHA-256: 3a5e8c0d9a11045074919df96a11ec46f6bf5c789caa746dddd53459c2aa4edf

Existing warnings remained: Kotlin expect/actual, Java/Android deprecations,
the known libandroidx.graphics.path.so strip warning and Gradle 9
deprecation notices. No live mount, camera, Astro WebUI or physical SLOW
qualification was performed. This is host-side build evidence, not hardware
qualification.


## Latest committed verification - 17ba0b6

Commit under test: 17ba0b6 (fix: own astro webui actions).

Command:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL (103 actionable tasks). Shared and Compose JVM tests
passed, including the Astro WebUI programme/capture late-result regressions,
M/B shutter evidence, session lifecycle and cancellation regressions, capture
lifecycle, bridge/Bluetooth lifecycle, SLOW transport/controller and wire
replay, Astro WebUI and support-bundle tests. Android debug Kotlin compilation
and APK assembly passed, and the Linux x64 desktop distributable was created.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: 8387c8f916f4b74ae0e93183481919568ebdb4c8986cf4002b9476452abc36d3
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-17ba0b6.tar.gz
  SHA-256: ff7a62638a110c60ed71fc36eb419403ac23096442067552de5f1687b57471eb

Existing warnings remained: Kotlin expect/actual, Java/Android deprecations,
the known libandroidx.graphics.path.so strip warning and Gradle 9
deprecation notices. No live mount, camera, Astro WebUI or physical SLOW
qualification was performed. This is host-side build evidence, not hardware
qualification.


## Latest committed verification - 24d6b23

Commit under test: 24d6b23 (fix: isolate astro webui reads).

Focused read-side command:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiStatusTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiSolveTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiRunStateTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiSiteTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiProgrammeTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiMediaTest --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL; the focused Astro WebUI ViewModel suites passed,
including the late-result status regression. A prior run exposed and the
implementation preserved the existing no-session media failure state.

Full build command:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL (103 actionable tasks). Shared and Compose JVM tests
passed, Android debug Kotlin compilation and APK assembly passed, and the
Linux x64 desktop distributable was created.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: 3106592bb59af274ae6a996a10b47345b2fbfbe91056cbfb6e9530bad3c79b2b
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-24d6b23.tar.gz
  SHA-256: 16effad1a848a25abf6141a7477f8a47c214e08c8f41560536392a8fd71f07e3

Existing warnings remained: Kotlin expect/actual, Java/Android deprecations,
the known libandroidx.graphics.path.so strip warning and Gradle 9
deprecation notices. No live mount, camera, Astro WebUI or physical SLOW
qualification was performed. This is host-side build evidence, not hardware
qualification.


## Latest committed verification - fdb631a

Commit under test: fdb631a (fix: own astro webui session controls).

Focused command:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiSessionControlTest --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL; the Astro WebUI session-control suite passed,
including the cancellation-insensitive late enter-result regression.

Full build command:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL (103 actionable tasks). Shared and Compose JVM tests
passed, Android debug Kotlin compilation and APK assembly passed, and the
Linux x64 desktop distributable was created.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: 6fdfb5179161fbbb17035b1ead4a56b21294ef6dab0d2b900914cf968ba31c66
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-fdb631a.tar.gz
  SHA-256: 672e2f7b8a52483133519a90474107dcd3d25a013c44dca1cb80fdf85de9c9cc

Existing warnings remained: Kotlin expect/actual, Java/Android deprecations,
the known libandroidx.graphics.path.so strip warning and Gradle 9
deprecation notices. No live mount, camera, Astro WebUI or physical SLOW
qualification was performed. This is host-side build evidence, not hardware
qualification.


## Latest committed verification - 1883539

Commit under test: 1883539 (feat: persist validated observer location).

Focused command:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL; all shared and Compose JVM tests passed, including
the preference round-trip, observer-location validation, and stable shell
ViewModel coverage.

Full build command:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL (103 actionable tasks). Shared and Compose JVM tests
passed, Android debug Kotlin compilation and APK assembly passed, and the
Linux x64 desktop distributable was created.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: 1997880f2c0f5729d6c625ec564882dbd899a60c4ca6bd3d70528aed5a813426
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-1883539.tar.gz
  SHA-256: 5091bb763ec1195d28f19168676901c427fef9b012a47778eeb9802aad1ba116

Existing warnings remained: Kotlin expect/actual, Java/Android deprecations,
the known libandroidx.graphics.path.so strip warning and Gradle 9
deprecation notices. No live mount, camera, Astro WebUI or physical SLOW
qualification was performed. This is host-side build evidence, not hardware
qualification.


## Latest committed verification - 7e064f2

Commit under test: 7e064f2 (feat: add gated astro webui site save).

Focused contract and ViewModel verification:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.AstroWebUiSiteControlTest --tests dev.openpolaris.core.config.FeatureFlagsTest :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiSiteTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiSiteWriteTest --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL; site-write contract, default-off policy, read-site
and site-write lifecycle tests passed.

Full build command:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL (103 actionable tasks). Shared and Compose JVM tests
passed, Android debug Kotlin compilation and APK assembly passed, and the
Linux x64 desktop distributable was created.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: a3a0538abe4456d46377adc099e31d5674bebdf5511ad4b9f43146d6cbe683af
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-7e064f2.tar.gz
  SHA-256: bc120ef0135cee5aaae15474ad6064d134e3ce97f1fe0b12a3ce4e12be104147

Existing warnings remained: Kotlin expect/actual, Java/Android
deprecations, the known libandroidx.graphics.path.so strip warning and Gradle
9 deprecation notices. No live mount, camera, Astro WebUI or physical SLOW
qualification was performed. This is host-side build evidence, not hardware
qualification.


## Latest committed verification - 35b2ccf

Commit under test: 35b2ccf (feat: add gated astro solar programme).

Focused solar verification:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.AstroWebUiSunProgrammeTest :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiSunTest --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL; source contract, form validation, preview/start
separation and session lifecycle tests passed.

Full build command:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL (103 actionable tasks). Shared and Compose JVM tests
passed, Android debug Kotlin compilation and APK assembly passed, and the
Linux x64 desktop distributable was created.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: 236a30d9cb61967740d0b36bbb0750179feaf3df2ce420c055cc64a9957d8635
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-35b2ccf.tar.gz
  SHA-256: 0bb12ba21bd5d8e6876cb41bbf058b962f99bbb7f0e9bc08fd8cbb93b2a0dd77

Existing warnings remained: Kotlin expect/actual, Java/Android
deprecations, the known libandroidx.graphics.path.so strip warning and Gradle
9 deprecation notices. No live Astro WebUI, mount, camera or physical SLOW
qualification was performed. This is host-side build evidence, not hardware
qualification.


## Latest committed verification - eec8cbc

Commit under test: eec8cbc (feat: add gated astro motion programmes).

Focused motion-programme verification:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.AstroWebUiMotionProgrammesTest :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiMotionProgrammesTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL; source-contract tests, ViewModel default-gate/
preview-port/session-ownership tests and Compose JVM compilation passed.

Full build command:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL (103 actionable tasks). Shared and Compose JVM tests
passed, Android debug Kotlin compilation and APK assembly passed, and the
Linux x64 desktop distributable was created.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: 12b9e9afa2f9c9c8ba2717856fbed76da9235fe306b2ee8ff734639600c33a44
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-eec8cbc.tar.gz
  SHA-256: 7f020000def5058cb81ec2a83c55697cbcdbee3dc99d9cec868b635f49cd90f2

Existing warnings remained: Kotlin expect/actual, Java/Android
deprecations, the known libandroidx.graphics.path.so strip warning and Gradle 9
deprecation notices. No live mount, camera, Astro WebUI or physical SLOW
qualification was performed. This is host-side build evidence, not hardware
qualification.


## Latest committed verification - fd16336

Commit under test: fd16336 (feat: add gated astro autofocus).

Focused autofocus verification:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.AstroWebUiAutofocusTest --tests dev.openpolaris.core.config.FeatureFlagsTest :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiAutofocusTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL; autofocus source-contract, feature-policy,
ViewModel lifecycle and Compose compilation tests passed.

Full build command:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL (103 actionable tasks). Shared and Compose JVM tests
passed, Android debug Kotlin compilation and APK assembly passed, and the
Linux x64 desktop distributable was created.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: 27aff09ddbedc760ffd6409fa022c8daaa1f16b5ee6a1554a6ad07dd6f2dca86
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-fd16336.tar.gz
  SHA-256: 5f9de7375c7018ca9c4464c96ea4cc3848e8b4f6e6c733416a40985fbbdec38c

Existing warnings remained: Kotlin expect/actual, Java/Android
deprecations, the known libandroidx.graphics.path.so strip warning and Gradle
9 deprecation notices. No live Astro WebUI, mount, camera or physical SLOW
qualification was performed. This is host-side build evidence, not hardware
qualification.


## Latest committed verification - 3440bea

Commit under test: 3440bea (feat: add astro webui focal controls).

Focused focal verification:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.AstroWebUiFocalTest --tests dev.openpolaris.core.config.FeatureFlagsTest :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiFocalTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL; focal parser/form validation, safe feature defaults,
ViewModel preview-port/session lifecycle and Compose compilation tests passed.

Full build command:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL (103 actionable tasks). Shared and Compose JVM tests
passed, Android debug Kotlin compilation and APK assembly passed, and the
Linux x64 desktop distributable was created.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: 0ff1161b4d04187202b9972c8dedb78e206f2300f9596b5cc3da86ca29948305
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-3440bea.tar.gz
  SHA-256: aa0023697a17bb49bfde95aa242fd9415fcfd880e24a709c6aa459ab83ff2d31

Existing warnings remained: Kotlin expect/actual, Java/Android
deprecations, the known libandroidx.graphics.path.so strip warning and Gradle
9 deprecation notices. No live Astro WebUI, solve, camera, mount or physical
SLOW qualification was performed. This is host-side build evidence, not
hardware qualification.


## Latest committed verification - 26cbae0

Commit under test: 26cbae0 (feat: add astro travel limit readback).

Focused travel-limit verification:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.AstroWebUiLimitsTest --tests dev.openpolaris.core.config.FeatureFlagsTest :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiLimitsTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL; travel-limit parser, safe feature policy,
ViewModel preview-port/session lifecycle and Compose compilation tests passed.

Full build command:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL (103 actionable tasks). Shared and Compose JVM tests
passed, Android debug Kotlin compilation and APK assembly passed, and the
Linux x64 desktop distributable was created.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: 77f90e32f376c85cc3a78685cf78964beb04d756c7dc597f64e5fdae99b3cc9b
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-26cbae0.tar.gz
  SHA-256: 6dd0e84ce9e3861bdaccd9d1398e21f18964375625a1edc7d6093eaaa248f3c3

Existing warnings remained: Kotlin expect/actual, Java/Android
deprecations, the known libandroidx.graphics.path.so strip warning and Gradle
9 deprecation notices. No live Astro WebUI, travel-limit write, mount,
camera or physical SLOW qualification was performed. This is host-side build
evidence, not hardware qualification.


## Latest committed verification - 2b13d40

Commit under test: 2b13d40 (feat: add astro keep wifi controls).

Focused keep-Wi-Fi verification:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.AstroWebUiKeepWifiTest --tests dev.openpolaris.core.config.FeatureFlagsTest :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiKeepWifiTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL; keep-Wi-Fi parser/form validation, safe feature
policy, ViewModel preview-port/session lifecycle and Compose compilation tests
passed.

Full build command:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL (103 actionable tasks). Shared and Compose JVM tests
passed, Android debug Kotlin compilation and APK assembly passed, and the
Linux x64 desktop distributable was created.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: 7d920dfb0191f40b5636c6aba93ab245905acb8c39e6620a50bc69e48e84d99b
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-2b13d40.tar.gz
  SHA-256: e23629ec3f4cf61537f98bd24f3cc1dede099080fdf1d1239d200a1a659c0b41

Existing warnings remained: Kotlin expect/actual, Java/Android
deprecations, the known libandroidx.graphics.path.so strip warning and Gradle
9 deprecation notices. No live Astro WebUI, bridge, Bluetooth, mount, camera
or physical SLOW qualification was performed. This is host-side build
evidence, not hardware qualification.


## Latest committed verification - 69ca50b

Commit under test: 69ca50b (feat: add astro wifi status).

Focused Astro Wi-Fi verification:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.AstroWebUiWifiTest --tests dev.openpolaris.core.config.FeatureFlagsTest :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiWifiTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL; Wi-Fi parser and bounds, safe feature policy,
ViewModel preview-port/session lifecycle and Compose compilation tests passed.

Full build command:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL (103 actionable tasks). Shared and Compose JVM tests
passed, Android debug Kotlin compilation and APK assembly passed, and the
Linux x64 desktop distributable was created.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: 53f82599dedfe9c690fe53dd711e5deb87ae08ddfa3e1f7e81cf428275b9f8b4
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-69ca50b.tar.gz
  SHA-256: e411b4d14a2ac8fcdda7e1ea40ff1d678e032f5e984b7ef728a981d11e63fa4f

Existing warnings remained: Kotlin expect/actual, Java/Android
deprecations, the known libandroidx.graphics.path.so strip warning and Gradle
9 deprecation notices. No live Astro WebUI, Wi-Fi, bridge, Bluetooth, mount,
camera or physical SLOW qualification was performed. This is host-side build
evidence, not hardware qualification.


## Latest committed verification - b4b3757

Commit under test: b4b3757 (feat: add astro target controls).

Focused target-control verification:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.AstroWebUiTargetControlTest --tests dev.openpolaris.core.config.FeatureFlagsTest :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiTargetTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon --quiet
~~~

Result: BUILD SUCCESSFUL; target adapter, range validation, feature policy, ViewModel preview/confirm lifecycle and Compose JVM compilation tests passed.

Full build command:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL (103 actionable tasks). Shared and Compose JVM tests passed, Android debug Kotlin compilation and APK assembly passed, and the Linux x64 desktop distributable was created.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: f898fb17e5f68d4c8aacb55f4c99fbf6ec1348dadb18ad25cd02b2db04f0c910
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-b4b3757.tar.gz
  SHA-256: 3cf50b19bed0bded516164802fbdd93306b95e30e81e5fb6cb8015d37018d3af

Existing warnings remained: Kotlin expect/actual, Java/Android deprecations, the known libandroidx.graphics.path.so strip warning and Gradle 9 deprecation notices. No live Astro WebUI, compass alignment, GoTo, mount, camera or physical SLOW qualification was performed. This is host-side build evidence, not hardware qualification.


## Latest committed verification - 7e9c3ba

Commit under test: 7e9c3ba (test: annotate astro solve control coroutine tests), including the preceding f5feb97 solve-control implementation.

Focused solve-control verification:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.AstroWebUiSolveControlTest --tests dev.openpolaris.core.config.FeatureFlagsTest :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiSolveControlTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon --quiet
~~~

Result: BUILD SUCCESSFUL; solve-control adapter, feature-policy, ViewModel lifecycle and Compose compilation tests passed.

Full build command:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

Result: BUILD SUCCESSFUL (103 actionable tasks). Shared and Compose JVM tests passed, Android debug Kotlin compilation and APK assembly passed, and the Linux x64 desktop distributable was created.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: 296dd4e0c5332a30373e10db56e8aab03bd1ec04ee2c0e644f2b60c5a1c752cd
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-7e9c3ba.tar.gz
  SHA-256: adfa49c1f270b8ff045144c32e3991863a41ce6fece2194d89e327ec0e2a11b4

Existing warnings remained: Kotlin expect/actual, Java/Android deprecations, the known libandroidx.graphics.path.so strip warning and Gradle 9 deprecation notices. No live Astro WebUI, solver, camera, alignment apply, mount or physical SLOW qualification was performed. This is host-side build evidence, not hardware qualification.


## Latest committed verification - 6f064dc

Commit under test: 6f064dc (feat: add astro link telemetry).

Focused link-telemetry verification:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.AstroWebUiLinkTest --tests dev.openpolaris.core.config.FeatureFlagsTest :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiLinkTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon --quiet
~~~

Result: BUILD SUCCESSFUL; link parser, feature-policy, ViewModel lifecycle and Compose compilation tests passed.

Full build command:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

Result: BUILD SUCCESSFUL (103 actionable tasks). Shared and Compose JVM tests passed, Android debug Kotlin compilation and APK assembly passed, and the Linux x64 desktop distributable was created.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: 3a7845544f87b542f24f4a16bd09420e87b36e478666f66a44593e7072f6549f
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-6f064dc.tar.gz
  SHA-256: 4a2214c94184c56a4b28ff7e54c5e6fa8fdd3d503661c2b56495d0fa156d00d7

Existing warnings remained: Kotlin expect/actual, Java/Android deprecations, the known libandroidx.graphics.path.so strip warning and Gradle 9 deprecation notices. No live Astro WebUI, Bluetooth, bridge, mount or physical SLOW qualification was performed. This is host-side build evidence, not hardware qualification.


## Latest committed verification - 54a5fe9

Commit under test: 54a5fe9 (feat: add mobile compass rehearsal).

Focused mobile-compass verification:

~~~text
./gradlew :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :composeApp:jvmTest --rerun-tasks --console=plain --no-daemon --quiet
~~~

Result: BUILD SUCCESSFUL; shared UI tests, Compose JVM compilation and Android debug Kotlin compilation passed.

Full build command:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

Result: BUILD SUCCESSFUL (103 actionable tasks). Shared and Compose JVM tests passed, Android debug Kotlin compilation and APK assembly passed, and the Linux x64 desktop distributable was created.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: d328b040c850bb507e9bbcbb2ce0a0a940e0cdfab11f04a226a80f2066771e6c
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-54a5fe9.tar.gz
  SHA-256: a3cf9792be46d2ede74a42df303e8b8c5cf16c4f5e1efbfa676e5b2ba789f452

Existing warnings remained: Kotlin expect/actual, Java/Android deprecations, the known libandroidx.graphics.path.so strip warning and Gradle 9 deprecation notices. No physical compass accuracy, live Astro WebUI, alignment, Bluetooth, bridge, mount or physical SLOW qualification was performed. This is host-side build evidence, not hardware qualification.


## Latest committed verification - 6295c46

Commit under test: 6295c46 (feat: clarify slow motion rehearsal UX).

Focused verification:

~~~text
./gradlew :composeApp:compileKotlinJvm :composeApp:jvmTest --tests dev.openpolaris.ui.SlowMotionPaneSafetyContractTest --rerun-tasks --console=plain --no-daemon --quiet
~~~

Result: BUILD SUCCESSFUL; Compose JVM compilation and the SLOW safety contract test passed.

Full build:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

The first full run had one timing-sensitive `SessionShutdownLeakTest` failure at cycle 3. The test passed in an isolated rerun, and the complete command then passed: 817 shared tests, Compose JVM tests, Android debug compilation/APK assembly and Linux x64 desktop distributable creation.

Artifacts:

- Android debug APK: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`
  SHA-256: `8e83d85d2e21f7bae0d3e20386a4dcea0d66a6d07d48db49857a8e7cdf6fc2a9`
- Linux x64 desktop archive: `/tmp/OpenPolaris-linux-x64-6295c46.tar.gz`
  SHA-256: `440b55418b5bd49a8564992ef551e331abf1880cdfdf034215e6e80aefe7aeae`

Existing warnings remained: Kotlin expect/actual, Java/Android deprecations, the known `libandroidx.graphics.path.so` strip warning and Gradle 9 deprecation notices. The build proves host/package integrity only; no live Astro WebUI, Bluetooth, bridge, camera, mount or physical SLOW qualification was exercised.


## Latest committed verification - ff29943

Commit under test: ff29943 (feat: add slow motion keyboard guidance).

Focused verification:

~~~text
./gradlew :composeApp:compileKotlinJvm :composeApp:jvmTest --tests dev.openpolaris.ui.SlowMotionPaneSafetyContractTest --rerun-tasks --console=plain --no-daemon --quiet
~~~

Result: BUILD SUCCESSFUL; the SLOW safety/accessibility contract test and Compose JVM compilation passed.

Full build:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

Result: BUILD SUCCESSFUL. Shared and Compose JVM tests passed, Android debug compilation/APK assembly passed, and the Linux x64 desktop distributable was created.

Artifacts:

- Android debug APK: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`
  SHA-256: `7f1da9252488bfc258e529fbe1857646aead41df47032b7ac95a7d641d31111e`
- Linux x64 desktop archive: `/tmp/OpenPolaris-linux-x64-ff29943.tar.gz`
  SHA-256: `cc5b235ee74de3f800080e8c944157fedc0286537a9a20fd40d069637aae6b1a`

Existing warnings remained: Kotlin expect/actual, Java/Android deprecations, the known `libandroidx.graphics.path.so` strip warning and Gradle 9 deprecation notices. This is host/package evidence only; no live Astro WebUI, Bluetooth, bridge, camera, mount or physical SLOW qualification was exercised.



## Latest committed verification - 5f05872

Commit under test: 5f05872 (feat: add gated astro in-head capture).

Focused client/ViewModel/UI verification passed:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.AstroWebUiAstroCaptureTest --tests dev.openpolaris.core.config.FeatureFlagsTest :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiAstroCaptureTest --tests dev.openpolaris.ui.AstroWebUiProgrammeComposerContractTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon --quiet
~~~

Full cross-platform verification passed:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: d871304ae7b7c44b30e01ef36e6b6e218aa064a4364a99519c54b0bb0424739f
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-5f05872.tar.gz
  SHA-256: 26cea892e72e2c2123e966fb0c0a69c163872bb3b8c4dca184b909545ddcb4ef

Existing warnings remain: Kotlin expect/actual, Java/Android deprecations,
the known libandroidx.graphics.path.so strip warning and Gradle 9 deprecation
notices. This is host/package evidence only; no live Astro WebUI, Bluetooth,
bridge, camera, mount or physical SLOW qualification was exercised.



## Latest committed verification - bc0b782

Commit under test: bc0b782 (feat: add astro panorama interval control).

Focused panorama-control verification passed:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.AstroWebUiProgrammeControlTest :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiProgrammeControlTest --tests dev.openpolaris.ui.AstroWebUiProgrammeComposerContractTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon --quiet
~~~

Full cross-platform verification passed:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: 233f77376d43e5366d5a2850fba958ef4982203417d5d689aff83060e460449e
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-bc0b782.tar.gz
  SHA-256: 16a1b327add0dc5dfd97f37e3a8620c93b4ce0b2c6b650b4318466d9c1a75237

Existing warnings remain: Kotlin expect/actual, Java/Android deprecations,
the known libandroidx.graphics.path.so strip warning and Gradle 9 deprecation
notices. Host/package evidence only; no live Astro WebUI, Bluetooth, bridge,
camera, mount or physical SLOW qualification was exercised.



## Latest committed verification - b016a1e

Commit under test: b016a1e (fix: align astro capture arm safety).

Focused Astro capture verification passed:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.AstroWebUiAstroCaptureTest :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiAstroCaptureTest --tests dev.openpolaris.ui.AstroWebUiProgrammeComposerContractTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon --quiet
~~~

Full cross-platform verification passed:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: 4ffae8babfb5f433f66d3b28c67078b6d4e0540a0043a86649f30a445531d248
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-b016a1e.tar.gz
  SHA-256: 2e052aa86a8a83d10ca54bc4b2d98feff2d62c9cb34ceabc5db809e018092605

Existing warnings remain: Kotlin expect/actual, Java/Android deprecations,
the known libandroidx.graphics.path.so strip warning and Gradle 9 deprecation
notices. Host/package evidence only; no live Astro WebUI, Bluetooth, bridge,
camera, mount or physical SLOW qualification was exercised.



## Latest committed verification - e1ab877

Commit under test: e1ab877 (fix: preserve fine-rate cancellation).

Focused SLOW/fine-rate verification passed:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.RateAdjustControllerTest --tests dev.openpolaris.core.domain.QualifiedSlowMotionTransportTest --rerun-tasks --console=plain --no-daemon --quiet
~~~

Full cross-platform verification passed:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: e87bb99739f731c76ea84837cf3e5cc22f27db3a67320b0d49630cf51589f02f
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-e1ab877.tar.gz
  SHA-256: ec984134c1d0031fa988397f46eec06d5e720ba449afafdaf327704c0c73f9f4

Existing warnings remain: Kotlin expect/actual, Java/Android deprecations,
the known libandroidx.graphics.path.so strip warning and Gradle 9 deprecation
notices. Host/package evidence only; no live Astro WebUI, Bluetooth, bridge,
camera, mount or physical SLOW qualification was exercised.


## Latest committed verification - 64d0460

Commit under test: 64d0460 (feat: gate fine rate on solve evidence).

Focused verification passed:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.RateAdjustControllerTest --tests dev.openpolaris.core.domain.QualifiedSlowMotionTransportTest --rerun-tasks --console=plain --no-daemon --quiet
~~~

Full cross-platform verification passed:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: 05e2dbf1d6d6e9885dd6491ee31e0c2b81244b4f67dffe91ff69dfcd935afb1d
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-64d0460.tar.gz
  SHA-256: f7c9c4ca260b75e44419eaecea6d0c52983fbdd58a5180cb77fbadd1bd083377

Existing warnings remain: Kotlin expect/actual, Java/Android deprecations,
the known libandroidx.graphics.path.so strip warning and Gradle 9 deprecation
notices. This is host/package evidence only; no live solve, Astro WebUI,
Bluetooth, bridge, camera, mount or physical SLOW qualification was exercised.


## Latest committed verification - a101008

Commit under test: a101008 (feat: show fine-rate readiness contract).

Focused verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.SlowMotionPaneSafetyContractTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon --quiet
~~~

Full cross-platform verification passed:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: 260c554926b25e2b5ef9fffacf173ba27cfb6b41c9853de480c595643eeca767
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-a101008.tar.gz
  SHA-256: e18ee04d37282499a6127b3b67c81d2de4866b020b2ca5822cefdb69a6b0e1bf

Existing warnings remain: Kotlin expect/actual, Java/Android deprecations,
the known libandroidx.graphics.path.so strip warning and Gradle 9 deprecation
notices. This is host/package evidence only; no live fine-rate, Bluetooth,
bridge, camera, mount or physical SLOW qualification was exercised.
