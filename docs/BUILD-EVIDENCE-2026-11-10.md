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


## Latest committed verification - d9c8a4f

Commit under test: d9c8a4f (fix: lock capture after unknown outcome).

Focused verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.CameraCaptureSafetyContractTest --tests dev.openpolaris.ui.AppViewModelCapturePhaseTest --rerun-tasks --console=plain --no-daemon --quiet
~~~

Full cross-platform verification passed:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: 0887b24f80d8d7e571a9da66eceea32d1cdccfdf153cac1ed6757900de123856
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-d9c8a4f.tar.gz
  SHA-256: b8d432cd006972b4d266503bba290740a08be492464825975ca4793fbd0aa1a9

Existing warnings remain: Kotlin expect/actual, Java/Android deprecations,
the known libandroidx.graphics.path.so strip warning and Gradle 9 deprecation
notices. This is host/package evidence only; no live shutter, Astro WebUI,
Bluetooth, bridge, camera, mount or physical SLOW qualification was exercised.


## Latest committed verification - ff427fd

Commit under test: ff427fd (feat: surface Bluetooth wake evidence).

Focused verification passed:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.BluetoothWakeStateTest :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelWakeConnectRaceTest --tests dev.openpolaris.ui.AppViewModelSessionLifecycleTest --tests dev.openpolaris.ui.ConnectionPaneBluetoothEvidenceContractTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon --quiet
~~~

Full cross-platform verification passed:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: c87c8b3a4e0368e1039a6888f6d05d31f38805a5f5e2966042f33f8e0f117d79
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-ff427fd.tar.gz
  SHA-256: b3ed4814dcb8b1a1529fb1895b50745198db8cf1d2c1e69b070d6ef0ee626ff1

Existing warnings remain: Kotlin expect/actual, Java/Android deprecations,
the known libandroidx.graphics.path.so strip warning and Gradle 9 deprecation
notices. This is host/package evidence only; no live Bluetooth, bridge,
camera, mount or physical SLOW qualification was exercised.


## Latest committed verification - ea3436c

Commit under test: `ea3436c` (`feat: rehearse fine-rate control in demo`).

Focused verification passed:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.RateAdjustControllerTest :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelFineRateDemoTest --tests dev.openpolaris.ui.SlowMotionPaneSafetyContractTest --tests dev.openpolaris.ui.AppViewModelFastJogTest --rerun-tasks --console=plain --no-daemon --quiet
~~~

Full cross-platform verification passed:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

Artifacts:

- Android debug APK: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`
  SHA-256: `1794c2eabbeeb109d821719de7ed14269731a612e9b4f4639b00c71ed4f528ea`
- Linux x64 desktop archive: `/tmp/OpenPolaris-linux-x64-ea3436c.tar.gz`
  SHA-256: `939bb0dc24bff383400060552d390268692bb871e5e97b95f6e4f06920c0e357`

Existing warnings remain: Kotlin expect/actual, Java/Android deprecations, the known libandroidx.graphics.path.so strip warning and Gradle 9 deprecation notices. These are host/package checks only; no live fine-rate, solve, Bluetooth, bridge, camera, mount or physical SLOW qualification was exercised.


## Latest committed verification - 570ac1a

Commit under test: `570ac1a` (`feat: wire gated slow transport seam`).

Focused verification passed:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.QualifiedSlowMotionTransportTest :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelFineRateDemoTest --tests dev.openpolaris.ui.AppViewModelFastJogTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon --quiet
~~~

Full cross-platform verification passed:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

Artifacts:

- Android debug APK: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`
  SHA-256: `19105a27efc49ce8bb4361c7bbe42a443133c554d001b50921e2b4f356eee10b`
- Linux x64 desktop archive: `/tmp/OpenPolaris-linux-x64-570ac1a.tar.gz`
  SHA-256: `f5649eb036afa8b528139e39e387a86101667e28dddb5f05a33e5416325d2835`

No live SLOW command, Bluetooth, bridge, camera, mount or physical stop qualification was exercised.


## Latest committed verification - 96f8eac

Commit under test: `96f8eac` (`feat: expose qualified slow motion path`).

Focused verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.SlowMotionPaneSafetyContractTest --tests dev.openpolaris.ui.AppViewModelFineRateDemoTest :shared:jvmTest --tests dev.openpolaris.core.domain.QualifiedSlowMotionTransportTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon --quiet
~~~

Full cross-platform verification passed:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

Artifacts:

- Android debug APK: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`
  SHA-256: `f16ca5ecb9ae7ae59a95eb87e2494d32f0169c31b1020ce42b889d599be12079`
- Linux x64 desktop archive: `/tmp/OpenPolaris-linux-x64-96f8eac.tar.gz`
  SHA-256: `8b810bc8c02819d2d2f09c447aba9b882688dc3399ea1c4706797f5f3e416782`

No live SLOW command, Bluetooth, bridge, camera, mount or physical stop qualification was exercised.


## Latest committed verification - d5c9ff4

Commit under test: `d5c9ff4` (`feat: inject slow capability evidence`).

Focused verification passed:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.QualifiedSlowMotionTransportTest :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelFineRateDemoTest --tests dev.openpolaris.ui.SlowMotionPaneSafetyContractTest --tests dev.openpolaris.ui.AppViewModelFastJogTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon --quiet
~~~

Full cross-platform verification passed:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

Artifacts:

- Android debug APK: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`
  SHA-256: `281ed68b235813fc1eee16434eea8e6e965bc2eff762ffda7939740ecb4fe71f`
- Linux x64 desktop archive: `/tmp/OpenPolaris-linux-x64-d5c9ff4.tar.gz`
  SHA-256: `5250fae163914774e90a82739d6602b2fc45b2e8aadae9bb8b0ca85487af0648`

No live SLOW command, Bluetooth, bridge, camera, mount or physical stop qualification was exercised.


## Latest committed verification - 1892b81

Commit under test: 1892b81 (feat: make mission readiness actions direct).

Focused verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.MotionSafetyChromeContractTest --tests dev.openpolaris.ui.MobileResponsiveLayoutContractTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon --quiet
~~~

Full cross-platform verification passed:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: 42781361b6befe8b7fbc382ff53e23a76df57b5f38687852055645d3702ebdc8
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-1892b81.tar.gz
  SHA-256: 9f8a53b5036bf1f42abb44dbf6577f3955e0d025d058e9e36246225fb1ffa48c
- Debian package: desktopApp/build/compose/binaries/main/deb/openpolaris_1.0.0_amd64.deb
  SHA-256: a6a9aa81f711d487f75f0c57c3a36c2e1c210f75489c6f319a8da459a99fa61a

The build proves package/host behavior only; no live Astro WebUI, Bluetooth, bridge, camera, mount or physical SLOW qualification was exercised.


## Latest committed verification - cfe7290

Commit under test: cfe7290 (fix: keep camera readback inside session).

Focused verification passed:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelCameraRefreshOwnershipTest --tests dev.openpolaris.ui.AppViewModelCapturePhaseTest --tests dev.openpolaris.ui.AppViewModelSessionLifecycleTest --tests dev.openpolaris.ui.MotionSafetyChromeContractTest --tests dev.openpolaris.ui.AppViewModelQualifiedSlowMotionTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon --quiet
~~~

Full cross-platform verification passed:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: e9b7867f089d280cc3fd53f7313c2354293717566311a76e502e82c3e10a7626
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-cfe7290.tar.gz
  SHA-256: 13c04c1063c7c056ebc1d0154cf467436ed54e6588e6844a8a2382a65ab08745
- Debian package: desktopApp/build/compose/binaries/main/deb/openpolaris_1.0.0_amd64.deb
  SHA-256: a6a9aa81f711d487f75f0c57c3a36c2e1c210f75489c6f319a8da459a99fa61a

No live Astro WebUI, Bluetooth, bridge, camera, mount or physical SLOW qualification was exercised.


## Latest committed verification - 2b3e342

Commit under test: 2b3e342 (feat: surface motion faults in readiness).

Focused verification passed:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.MissionReadinessTest :composeApp:jvmTest --tests dev.openpolaris.ui.MotionSafetyChromeContractTest --tests dev.openpolaris.ui.MobileResponsiveLayoutContractTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon --quiet
~~~

Full cross-platform verification passed:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: ec97ccb130749bcfcad0e243d9a4d84dbeba211c9a49c1106980fb595beaabc5
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-2b3e342.tar.gz
  SHA-256: 62084448217d734f3a968664515b6e5dc7e04c2e07d0b44395feffdfae3e82db
- Debian package: desktopApp/build/compose/binaries/main/deb/openpolaris_1.0.0_amd64.deb
  SHA-256: a6a9aa81f711d487f75f0c57c3a36c2e1c210f75489c6f319a8da459a99fa61a

No live Astro WebUI, Bluetooth, bridge, camera, mount or physical SLOW qualification was exercised.

## Latest committed verification - de66375

Commits under test: e5e3836 (fix: stop slow motion before tracking mode changes) and de66375 (test: update slow motion safety contract).

Focused verification passed:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.SlowMotionControllerTest :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelFineRateDemoTest --tests dev.openpolaris.ui.MotionSafetyChromeContractTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon --quiet
~~~

Full cross-platform verification passed:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: a2d8602976b69b3c5c4187ccdb3e507d63ae8b6957831596394413a69fb87860
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-e5e3836.tar.gz
  SHA-256: 3688624a11eb4423ca06eb480d192daaae7bebb1a7ef9353175740549cd60f1d
- Debian package: desktopApp/build/compose/binaries/main/deb/openpolaris_1.0.0_amd64.deb
  SHA-256: a6a9aa81f711d487f75f0c57c3a36c2e1c210f75489c6f319a8da459a99fa61a

No live Astro WebUI, Bluetooth, bridge, camera, mount or physical SLOW qualification was exercised.

## Latest committed verification - ec3a86e

Commit under test: ec3a86e (fix: stop slow motion before Astro session changes).

Focused verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiSessionControlTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon --quiet
~~~

Full cross-platform verification passed:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: 6c76b2f41a30715daa588eea81863d24487590ebf09028206bbeb8a8fe866c46
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-ec3a86e.tar.gz
  SHA-256: 9eff532f2262437aa164b7df2fc71e5902c6d1af5b30cce90fbe7504d9fff8a3
- Debian package: desktopApp/build/compose/binaries/main/deb/openpolaris_1.0.0_amd64.deb
  SHA-256: a6a9aa81f711d487f75f0c57c3a36c2e1c210f75489c6f319a8da459a99fa61a

No live Astro WebUI, Bluetooth, bridge, camera, mount or physical SLOW qualification was exercised.

## Latest committed verification - 5160187

Commit under test: 5160187 (fix: isolate slow motion from capture workflows).

Focused verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelFineRateDemoTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon --quiet
~~~

Full cross-platform verification passed:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: 3bd64ff7d7a63590a5cd5b987de03c41992104bd98f21fa9bed635d6590c767a
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-5160187.tar.gz
  SHA-256: cca35b68421cbe6eda4da6561287da64d15d35a3d287295b8adb35bab3551b83
- Debian package: desktopApp/build/compose/binaries/main/deb/openpolaris_1.0.0_amd64.deb
  SHA-256: a6a9aa81f711d487f75f0c57c3a36c2e1c210f75489c6f319a8da459a99fa61a

No live Astro WebUI, Bluetooth, bridge, camera, mount or physical SLOW qualification was exercised.

## Latest committed verification - 34dd364

Commit under test: 34dd364 (feat: use semantic mobile navigation bar).

Focused verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.MobileResponsiveLayoutContractTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon --quiet
~~~

Full cross-platform verification passed:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: e3c4f3c810c095f08fc121c9cda689f93b8ae192c8174d7872b6870ba8febcda
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-34dd364.tar.gz
  SHA-256: 4da3cddbf934c9da2ae659a36648005521c03e364f4a4b3ec355ba99fa0db777
- Debian package: desktopApp/build/compose/binaries/main/deb/openpolaris_1.0.0_amd64.deb
  SHA-256: a6a9aa81f711d487f75f0c57c3a36c2e1c210f75489c6f319a8da459a99fa61a

No live Astro WebUI, Bluetooth, bridge, camera, mount or physical SLOW qualification was exercised.


## Latest committed verification - 421774b

Commit under test: 421774b (feat: complete demo capture lifecycle).

Focused verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelCapturePhaseTest --rerun-tasks --console=plain --no-daemon
~~~

Full cross-platform verification passed:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

Authoritative JVM test reports contained no failures.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: f9b47f62e0af68a25474679793554dbc558660cad06629e772ac622f8c01e632
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-demo-capture.tar.gz
  SHA-256: eea3fee7732167f80afc7aab1ea91b0e0b65a4840e145efc162b4e1a70c7eb19
- Debian package: desktopApp/build/compose/binaries/main/deb/openpolaris_1.0.0_amd64.deb
  SHA-256: a6a9aa81f711d487f75f0c57c3a36c2e1c210f75489c6f319a8da459a99fa61a

The demo capture path is simulator/package evidence only. No live Astro WebUI,
Bluetooth, bridge, camera, mount or physical SLOW qualification was exercised.


## Latest committed verification - 27c37ff

Commit under test: 27c37ff (test: cover demo manual and bulb capture).

Focused verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelCapturePhaseTest --rerun-tasks --console=plain --no-daemon
~~~

Full cross-platform verification passed:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

Authoritative JVM test reports contained no failures.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: 36a55c34dce8a959363c1af2e1728d7feccb65a4fcced9602ce63d99dccd3a48
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-demo-mb.tar.gz
  SHA-256: eafda764402a30a201fea47dc7a35adebec4393f1fdfb0491888e6fd9aad9d23
- Debian package: desktopApp/build/compose/binaries/main/deb/openpolaris_1.0.0_amd64.deb
  SHA-256: a6a9aa81f711d487f75f0c57c3a36c2e1c210f75489c6f319a8da459a99fa61a

The M/B demo path is simulator/package evidence only. No live Astro WebUI,
Bluetooth, bridge, camera, mount or physical SLOW qualification was exercised.


## Latest committed verification - 8cf7018

Commit under test: 8cf7018 (test: connect demo captures to gallery).

Focused verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelCapturePhaseTest --rerun-tasks --console=plain --no-daemon
~~~

Full cross-platform verification passed:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

Authoritative JVM test reports contained no failures.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: 4cc4ac78efb3ef23c8f7097d8f7130d973fcd7170092345ca3d221dd748f70cf
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-demo-gallery.tar.gz
  SHA-256: 92bcc38a0a5dbaf2e7a8f072ca97726c4a16acb8d6c84d76f4b69bbebed1b6ea
- Debian package: desktopApp/build/compose/binaries/main/deb/openpolaris_1.0.0_amd64.deb
  SHA-256: a6a9aa81f711d487f75f0c57c3a36c2e1c210f75489c6f319a8da459a99fa61a

The capture-to-gallery path is simulator/package evidence only. No live Astro
WebUI, Bluetooth, bridge, camera, SD card, mount or physical SLOW qualification
was exercised.


## Latest committed verification - 15dd8c2

Commit under test: 15dd8c2 (feat: link capture review to gallery).

Focused verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.CameraCaptureSafetyContractTest --tests dev.openpolaris.ui.MobileResponsiveLayoutContractTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon
~~~

Full cross-platform verification passed:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

Authoritative JVM test reports contained no failures.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: 3761dd1256e229b7d1424324eb5963446b9f12d2f6b43be58ce4a519d602a18a
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-capture-review.tar.gz
  SHA-256: 418af98ea08860880ebfd66219028bac8b0fe6eef052dff9e8bcc38c20479b44
- Debian package: desktopApp/build/compose/binaries/main/deb/openpolaris_1.0.0_amd64.deb
  SHA-256: a6a9aa81f711d487f75f0c57c3a36c2e1c210f75489c6f319a8da459a99fa61a

The review affordance is simulator/package evidence only. No live Astro WebUI,
Bluetooth, bridge, camera, SD card, mount or physical SLOW qualification was
exercised.


## Latest committed verification - de116be

Commit under test: de116be (test: rehearse demo camera M/B readback).

Focused verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelCapturePhaseTest --rerun-tasks --console=plain --no-daemon
~~~

Full cross-platform verification passed:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

Authoritative JVM XML reports contained no failures.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: ef5299119735abdbe30d3f0d4beb3945c6eca0f9da1342e049f17a2967fd0cac
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-demo-mb-modes.tar.gz
  SHA-256: 9a7571e0c91f7360cc8e36bc65877764170f17484f9af8a917730abfeadaecc2
- Debian package: desktopApp/build/compose/binaries/main/deb/openpolaris_1.0.0_amd64.deb
  SHA-256: a6a9aa81f711d487f75f0c57c3a36c2e1c210f75489c6f319a8da459a99fa61a

The M/B mode rehearsal is simulator/package evidence only. No live Astro
WebUI, Bluetooth, bridge, camera, mount or physical SLOW qualification was
performed.


## Latest committed verification - 07d31b9

Commit under test: 07d31b9 (feat: rehearse demo camera preview).

Focused verification passed:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.PreviewControllerTest :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelPreviewPortTest --rerun-tasks --console=plain --no-daemon
~~~

Full cross-platform verification passed:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

Authoritative JVM XML reports contained no failures.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: 6a0e2f070337c767ba1c0a37ec214667666415e1b4bfd107d19c07651ec4c023
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-demo-preview.tar.gz
  SHA-256: df9c60c1d8ad25ff970728cd39de4f369f2fbc022065d0787f6303e90252bf3d
- Debian package: desktopApp/build/compose/binaries/main/deb/openpolaris_1.0.0_amd64.deb
  SHA-256: a6a9aa81f711d487f75f0c57c3a36c2e1c210f75489c6f319a8da459a99fa61a

The synthetic preview is simulator/package UX evidence only. No live Astro
WebUI, Bluetooth, bridge, camera, mount or physical SLOW qualification was
performed.


## Latest committed verification - 67d3d76

Commit under test: 67d3d76 (test: rehearse demo focus and video controls).

Focused verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelCapturePhaseTest --rerun-tasks --console=plain --no-daemon
~~~

Full cross-platform verification passed:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

Authoritative JVM XML reports contained no failures.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: e686f2f5fc2de38d87a09295422b5b840ae7bbf0881d439d905dc87e8d09c05f
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-demo-camera-controls.tar.gz
  SHA-256: a7f047cc7b2c62c45986ffb16fdfbf188f614b107e2d467baa25468be3de8387
- Debian package: desktopApp/build/compose/binaries/main/deb/openpolaris_1.0.0_amd64.deb
  SHA-256: a6a9aa81f711d487f75f0c57c3a36c2e1c210f75489c6f319a8da459a99fa61a

The focus/video rehearsal is simulator/package evidence only. No live Astro
WebUI, Bluetooth, bridge, camera, mount or physical SLOW qualification was
performed.


## Latest committed verification - ef52ca2

Commit under test: ef52ca2 (feat: rehearse demo observation programmes).

Focused verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelCapturePhaseTest --rerun-tasks --console=plain --no-daemon
~~~

Full cross-platform verification passed:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

Authoritative JVM XML reports contained no failures.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: 97860b843bcb1282a707baa19786099117e2ba3ce7313ecd092ac2cb87a192bf
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-demo-programmes.tar.gz
  SHA-256: 4e04062bf473057a37967f26b39440628f5783898f431c7890adeb73043550c8
- Debian package: desktopApp/build/compose/binaries/main/deb/openpolaris_1.0.0_amd64.deb
  SHA-256: a6a9aa81f711d487f75f0c57c3a36c2e1c210f75489c6f319a8da459a99fa61a

The HDR observation-programme rehearsal is simulator/package evidence only.
Live non-lapse programme execution, Astro WebUI, Bluetooth, bridge, camera, SD
card, mount and physical SLOW qualification remain unverified.


## Latest committed verification - af41a23

Commit under test: af41a23 (feat: add demo programme pause controls).

Focused verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelCapturePhaseTest.demoHdrProgrammeSupportsPauseAndResumeAtCaptureBoundary --rerun-tasks --console=plain --no-daemon
~~~

Full cross-platform verification passed:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

Authoritative JVM XML reports contained no failures.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: a98159bdc92920300bfa2dc5f322d00be8ac2910b183b8fc07b6439d70285299
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-demo-programme-controls.tar.gz
  SHA-256: 32195ee777d518ddf622b14dda0add44049870927da0e5e985dcfed354b61d78
- Debian package: desktopApp/build/compose/binaries/main/deb/openpolaris_1.0.0_amd64.deb
  SHA-256: a6a9aa81f711d487f75f0c57c3a36c2e1c210f75489c6f319a8da459a99fa61a

The programme pause/resume controls are simulator/package evidence only.
Live Astro WebUI, Bluetooth, bridge, camera, SD card, mount and physical SLOW
qualification remain unverified.


## Latest committed verification - b26d4cb

Commit under test: b26d4cb (test: rehearse demo focus and panorama programmes).

Focused verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelCapturePhaseTest --rerun-tasks --console=plain --no-daemon
~~~

Full cross-platform verification passed:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

Authoritative JVM XML reports contained no failures.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: a98159bdc92920300bfa2dc5f322d00be8ac2910b183b8fc07b6439d70285299
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-demo-focus-panorama.tar.gz
  SHA-256: b1494890acaf208f8c178bc82af7debfd1426a9cb8d42ad4fe00bd70a0c847db
- Debian package: desktopApp/build/compose/binaries/main/deb/openpolaris_1.0.0_amd64.deb
  SHA-256: a6a9aa81f711d487f75f0c57c3a36c2e1c210f75489c6f319a8da459a99fa61a

The focus-stack and panorama rehearsals are simulator/package evidence only.
Live Astro WebUI, Bluetooth, bridge, camera, SD card, mount and physical SLOW
qualification remain unverified.


## Latest committed verification - 513d4dd

Commit under test: 513d4dd (feat: add programme progress feedback).

Focused verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.ObservationProgrammePreviewContractTest --rerun-tasks --console=plain --no-daemon
~~~

Full cross-platform verification passed:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

Authoritative JVM XML reports contained no failures.

Artifacts:

- Android debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
  SHA-256: f1d995160e45eb94ae9b7a08609095dba47baf3a57825fec4da9ada0aad538ab
- Linux x64 desktop archive: /tmp/OpenPolaris-linux-x64-programme-progress.tar.gz
  SHA-256: 594ba2f3a217b841806f20cc927d0c1ff97ebc76b81c8161c025d6a21f1bcdea
- Debian package: desktopApp/build/compose/binaries/main/deb/openpolaris_1.0.0_amd64.deb
  SHA-256: a6a9aa81f711d487f75f0c57c3a36c2e1c210f75489c6f319a8da459a99fa61a

The programme progress UX is simulator/package evidence only. Live Astro
WebUI, Bluetooth, bridge, camera, SD card, mount and physical SLOW
qualification remain unverified.


## Latest committed verification - 84da9ff

Commit under test: 84da9ff (fix: harden camera readback and capture admission).

Focused verification passed:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.CameraInfoContractTest --tests dev.openpolaris.core.domain.MountSessionReaderTest --no-daemon --console=plain
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelCapturePhaseTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiCaptureTest --tests dev.openpolaris.ui.AppViewModelCameraRefreshOwnershipTest --no-daemon --console=plain
~~~

Full cross-platform verification passed:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

The authoritative shared and Compose JVM XML reports contained no failures.

Android debug APK:

- androidApp/build/outputs/apk/debug/androidApp-debug.apk
- SHA-256: 3f12cd5dbe6aa4f9b8ea5048f5c5b9c1fca75a0bff557cc95337680fc2114e88

The change is package/simulator evidence only. Live camera, Astro WebUI,
Bluetooth, mount, SD-card and physical slow-motion qualification remain open.


## Latest committed verification - a3d4d9b

Commit under test: a3d4d9b (fix: recover cancelled slow-motion pointer holds).

Focused verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.SlowMotionPaneSafetyContractTest --no-daemon --console=plain
./gradlew :composeApp:compileKotlinJvm --no-daemon --console=plain
~~~

Full cross-platform verification passed:

~~~text
./gradlew --stop
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

Android debug APK:

- androidApp/build/outputs/apk/debug/androidApp-debug.apk
- SHA-256: 31a27c507c210a2a03e1e2b7a41704c3b0e5c280b6eb3870827e888b3d6abb1f

Debian package:

- desktopApp/build/compose/binaries/main/deb/openpolaris_1.0.0_amd64.deb
- SHA-256: a6a9aa81f711d487f75f0c57c3a36c2e1c210f75489c6f319a8da459a99fa61a

This is simulator/package evidence only. Live mount, Bluetooth, camera,
Astro WebUI and physical slow-motion qualification remain unverified.


## Latest committed verification - 0b4cdb6

Commit under test: 0b4cdb6 (feat: arbitrate slow-motion dock holds).

Focused verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.SlowMotionPaneSafetyContractTest :composeApp:compileKotlinJvm --no-daemon --console=plain
~~~

Full cross-platform verification passed:

~~~text
./gradlew --stop
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

Android debug APK:

- androidApp/build/outputs/apk/debug/androidApp-debug.apk
- SHA-256: 5ead6c38e67ce6028270aa5cb513983c7b70bbf34fade7edd4825844b879a5ac

This is simulator/package evidence only. Live mount, Bluetooth, camera,
Astro WebUI and physical slow-motion qualification remain unverified.


## Latest committed verification - 435d0be

Commit under test: 435d0be (fix: use source-aligned Android GATT wake).

Focused verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.AndroidBluetoothWakeContractTest :androidApp:compileDebugKotlinAndroid --no-daemon --console=plain
~~~

Full cross-platform verification passed:

~~~text
./gradlew --stop
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

Android debug APK:

- androidApp/build/outputs/apk/debug/androidApp-debug.apk
- SHA-256: de6e61ec3d9c913c57398d0d5252db54514b8681081ff5fff042c14cedfbe458

This is source-contract/package evidence only. Live phone Bluetooth GATT,
mount AP wake, camera, Astro WebUI and physical slow-motion qualification
remain unverified.

## Latest committed verification - 2282c60

Commit under test: 2282c60 (feat: add explicit home LAN connection mode).

Focused verification passed:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.EndpointHealthTest :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelConnectionModeTest --tests dev.openpolaris.ui.ConnectionModeContractTest --tests dev.openpolaris.ui.AndroidBluetoothWakeContractTest :composeApp:compileKotlinJvm --no-daemon --console=plain
~~~

Full cross-platform verification passed:

~~~text
./gradlew --stop
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

Android debug APK:

- androidApp/build/outputs/apk/debug/androidApp-debug.apk
- SHA-256: f6e19607f6994810d4ee93262956685bec8da4e28a3ec53b33dbd192c45c4e8f

This is simulator/package and source-contract evidence only. Live station-LAN
listener reachability, phone Bluetooth GATT wake, mount, camera, Astro WebUI
and physical SLOW qualification remain unverified.

## Latest committed verification - e03753b

Commit under test: e03753b (feat: add field battery awareness).

Focused verification passed:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.BatteryAwarenessTest :composeApp:jvmTest --tests dev.openpolaris.ui.BatteryStatusContractTest :composeApp:compileKotlinJvm --no-daemon --console=plain
~~~

Full cross-platform verification passed with exit code 0:

~~~text
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

Android debug APK:

- androidApp/build/outputs/apk/debug/androidApp-debug.apk
- SHA-256: 23f313a81c003bd7bdd58d78cea539d4375ce13119f3f5da640e3aa50a2049f6

This is simulator/package evidence only. Live battery telemetry, station-LAN
listener reachability, phone Bluetooth GATT wake, mount, camera, Astro WebUI
and physical SLOW qualification remain unverified.

## Latest committed verification - 8672e9d

Commit under test: 8672e9d (fix: retain Android session view model).

Focused verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.AndroidViewModelLifetimeContractTest :androidApp:compileDebugKotlinAndroid --no-daemon --console=plain
~~~

Full cross-platform verification passed with exit code 0:

~~~text
./gradlew --stop
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

Android debug APK:

- androidApp/build/outputs/apk/debug/androidApp-debug.apk
- SHA-256: a39b4bf0ceab9379ed3bfff8db9dfc896c32402dde20b698b8e2015b11d87e03

This is package/source-contract evidence only. Live Android recomposition,
station-LAN listener reachability, phone Bluetooth GATT wake, mount, camera,
Astro WebUI and physical SLOW qualification remain unverified.

## Latest committed verification - 9d24d06

Commit under test: 9d24d06 (fix: publish correct rolling APK checksum).

Focused verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.RollingReleaseWorkflowContractTest :composeApp:compileKotlinJvm --no-daemon --console=plain
~~~

The workflow now computes `sha256sum "${APK}" > apk-sha256.txt` and separately
records the signing certificate digest in `apk-signer-sha256.txt`. This is a
source-contract check only; GitHub execution, signing secrets and phone install
remain external verification.

## Latest committed verification - 646fbfd

Commit under test: 646fbfd (fix: retain Android sessions across rotation).

Focused verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.AndroidViewModelLifetimeContractTest :androidApp:compileDebugKotlinAndroid --no-daemon --console=plain
~~~

Full cross-platform verification passed with exit code 0:

~~~text
./gradlew --stop
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon --quiet
~~~

Android debug APK:

- androidApp/build/outputs/apk/debug/androidApp-debug.apk
- SHA-256: 6f244f64a9f98d58823c337d7364b4daa55200955eb304a3de813b315e2857d9

This is source/package evidence only. Physical rotation with a live session,
station-LAN reachability, phone Bluetooth GATT wake, mount, camera, Astro
WebUI and physical SLOW qualification remain unverified.
## Latest committed verification - 2ec31f1

Commit under test: 2ec31f1 (fix: harden Astro shutter acknowledgements).

Focused verification passed:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.AstroWebUiCaptureTest :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiCaptureTest :composeApp:compileKotlinJvm --rerun-tasks --no-daemon --console=plain
~~~

Full cross-platform verification passed with exit code 0:

~~~text
./gradlew --stop
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon
~~~

Android debug APK:

- androidApp/build/outputs/apk/debug/androidApp-debug.apk
- SHA-256: 5f2dec45bebe49fab54a5662484b8e138b9316cad5092e29c529d5fca18c5278

This is source/package evidence only. The WebUI bridge acknowledgement is now
strictly parsed, but physical M/B shutter lifecycle, file publication, named
camera/firmware, station LAN, phone Bluetooth and physical SLOW qualification
remain unverified.
## Latest committed verification - 99c1fb5

Commit under test: 99c1fb5 (feat: expose auxiliary camera readback).

Focused verification passed:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.sim.SimulatedProtocolTest :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelCameraRefreshOwnershipTest :composeApp:compileKotlinJvm --rerun-tasks --no-daemon --console=plain
~~~

Full cross-platform verification passed with exit code 0:

~~~text
./gradlew --stop
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon
~~~

Android debug APK:

- androidApp/build/outputs/apk/debug/androidApp-debug.apk
- SHA-256: 378bae6fc962fb0dd91cd93f38fee36105764c3b8de554979bddc7f9af05d13c

This is source/package evidence only. Auxiliary camera values are displayed as
read-only source evidence; camera writes, named-camera M/B lifecycle/file
publication, station LAN, phone Bluetooth and physical SLOW qualification
remain unverified.


## Latest committed verification - f61de35

Commit under test: f61de35 (fix: compact mobile navigation and release cadence).

Focused verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.MobileResponsiveLayoutContractTest --tests dev.openpolaris.ui.RollingReleaseWorkflowContractTest :composeApp:compileKotlinJvm --rerun-tasks --no-daemon --console=plain
~~~

Full cross-platform verification passed with exit code 0:

~~~text
./gradlew --stop
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid :desktopApp:createDistributable :androidApp:assembleDebug --rerun-tasks --console=plain --no-daemon
~~~

Android debug APK:

- androidApp/build/outputs/apk/debug/androidApp-debug.apk
- SHA-256: 0e326d22cba2f340992a0119ed27011ced5ac9ffce9dde05c74fdc188cb9454d

The source/package result is ready for a phone UI check. Physical mount,
camera, Bluetooth, Astro WebUI lifecycle, and live SLOW qualification remain
separate evidence gates.
