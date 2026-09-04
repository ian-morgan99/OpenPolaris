# Changelog

All notable changes to OpenPolaris are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.16] - 2026-09-04

### Fixed
- **Firmware picker dropped result on rotation** (issue #49). The picker
  callback reference lived on `FilePickerRegistry`, which was cleared
  in `MainActivity.onDestroy()`. After a configuration change, AndroidX
  re-delivered the activity result to the new activity's launcher but
  the callback reference was null, so the URI was dropped silently and
  the picker never produced a `Selected:` line. Extracted a
  process-scoped `PickerBridge` into `commonMain` (testable from
  `commonTest`), so the result survives both rotation and `onDestroy`.
  `MainActivity.onCreate` now drains the bridge in a
  `LaunchedEffect(viewModel)` and forwards the result via the new
  `AppViewModel.applyPickResult(...)` method. 13 new unit tests pin the
  state machine; full rotation flow is covered.
- **Firmware picker was silently cancellable.** The `pickFirmwareFile`
  handler did `if (path == null) return@pickFile` — no UI feedback.
  Now sets `statusMessage = "Picker cancelled"` so the user sees the
  cancel on the firmware pane. The `Back` button and the system
  picker's own cancel UI both surface this message.

### Changed
- **Release signing identity is now stable** (closes the
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE` / "package is not valid"
  blocker for sideloaded test builds). Prior releases were signed with
  a per-machine debug keystore whose fingerprint was different on every
  developer's laptop, so any user who installed a build from one
  developer and then tried to install a build from another got a
  signature-mismatch error. v0.1.16+ is signed with a single
  deterministic keystore committed at
  `androidApp/keystore/release.keystore` (alias `openpolaris`,
  SHA-1 `86:FE:A8:CD:93:D9:31:49:BA:95:84:D2:F3:CE:1A:60:6A:E2:74:8F`,
  SHA-256
  `5c72c56e09c9c157fa689df037933eafcbc88e73357ccde6eb8b5a684033a9fd`).
  A new `release.yml` workflow builds the signed APK on every `v*.*.*`
  tag push and attaches it to the GitHub Release.

  **Install note — please read before updating:** the new signing
  identity is different from any prior build, so Android will refuse
  to update in place. **Uninstall any previous OpenPolaris build
  before installing v0.1.16.** After this one-time uninstall, future
  versions signed with the same committed key will install cleanly as
  updates.

### Added
- **`androidApp/key.properties.example`** — a committed template for
  `androidApp/key.properties` (gitignored), so anyone rebuilding locally
  knows what to fill in.

## [0.1.15] - 2026-09-04

### Fixed
- **VR mode (3D view) tap-to-exit did not work.** VRActivity declared
  the tap-to-exit `OnClickListener` on a `FrameLayout` that was a
  sibling of a full-screen `GLSurfaceView`. The `GLSurfaceView` child
  consumed the touch dispatch, so the `FrameLayout`'s listener never
  fired and the activity could not be dismissed by tapping. The
  activity comment even promised "tap anywhere on the VR view to exit"
  but the implementation did not deliver that. Move the click listener
  directly to the `GLSurfaceView` with `isClickable=true`; set the
  "tap to exit" overlay `TextView` to `isClickable=false` /
  `isFocusable=false` so it cannot intercept the tap. Verified
  end-to-end on emulator 1080x2340 in landscape: a single tap at
  screen centre returns the user to the launcher.

### Known limitations
- **Head-tracking on emulator.** `adb emu sensor set` exposes only
  `acceleration/gyroscope/magnetic-field/orientation` on this build.
  `TYPE_ROTATION_VECTOR` is fused in the framework but the fusion
  is not driven by the orientation console command, so the close-up
  view as the user moves their head cannot be exercised on
  emulator. The math (CardboardWarp, VrStereoShaders, RecenterMath)
  has unit-test coverage; the on-device experience must be
  confirmed on a real Cardboard-class viewer.

## [0.1.14] - 2026-09-04

### Fixed
- **Firmware file picker broken on Android** (the top user complaint
  after v0.1.13). The `OpenDocument` contract fires
  `Intent.ACTION_OPEN_DOCUMENT`, which the system `DocumentsUI`
  picker renders with single-tap = preview. Tapping a `.zip` file
  fired `ACTION_VIEW` (no app handles `application/zip` VIEW), got
  result code `-91` (`ActivityManager.START_CLASS_NOT_FOUND`), and
  the picker silently stayed open. The only way to actually pick
  a file was long-pressing it to reveal the "Select" action — a
  completely undiscoverable gesture. Switched to
  `ActivityResultContracts.GetContent()` (`ACTION_GET_CONTENT`),
  which uses single-tap = select on every supported Android
  version. The picker now closes immediately on tap, and the
  firmware pane shows `Selected: picked_<ts>_<name>.zip (<size> B)`
  as expected. The `Back` button cleanly cancels (preserves the
  previous selection, if any). No persistent URI permission is
  needed because `FilePickerRegistry.handleResult` already copies
  the picked bytes into `cacheDir` before returning an absolute
  filesystem path. Touched files: `MainActivity.kt` (launcher
  swap) and `FilePicker.kt` (contract signature change
  `Array<String>` → `String`).

## [0.1.13] - 2026-09-04

### Added
- **In-app User Manual** (closes the long-running "add a manual"
  ask). The `Guide` callout now opens `UserManualPane`, which loads
  the bundled `docs/USER-MANUAL.md` (`composeApp/src/commonMain/resources/USER-MANUAL.md`,
  21 KB) and renders it inside `CalloutDialog`'s single bounded
  `verticalScroll` (so the manual scrolls with the rest of the
  dialog body and the v0.1.11 no-same-axis-nested-scrollables
  contract still holds). Sections, paragraphs, bullet lists,
  fenced code blocks, and the one bundled screenshot
  (`screenshots/openpolaris-desktop-v1.0.0.png`) all render. The
  source of truth remains `docs/USER-MANUAL.md` on GitHub — the
  bundle is a copy so users can read the manual offline / on
  mount, without needing network access.
- **Bundled resources for the manual**: `USER-MANUAL.md` and
  `screenshots/openpolaris-desktop-v1.0.0.png` are now part of
  the APK under `assets/`, loaded via the existing
  `readResourceText` / `readResourceBytes` expect/actual. The
  Android actual uses `ctx.assets.open`; the JVM desktop actual
  uses `classLoader.getResourceAsStream` and decodes PNGs with
  `org.jetbrains.skia` (already on the JVM classpath via
  Compose for Desktop).
- **README link**: `README.md` now surfaces `docs/USER-MANUAL.md`
  in its Documentation table as the primary end-user manual,
  marked as bundled inside the app under the **Guide** callout.

### Tests
- `CalloutDialogNoScrollWrapperTest` extended with
  `userManualPaneIsNotAVerticalScroll` — pins that the new
  `UserManualPane.kt` does not introduce its own
  `Modifier.verticalScroll` and continues to defer to
  `CalloutDialog`'s scroll.
- `UserManualPaneBundledAssetTest` (new) reads
  `USER-MANUAL.md` via `readResourceText` and asserts the
  bundle is non-empty, the first heading is
  `# Open Polaris — User Manual`, and the referenced screenshot
  asset resolves via `readResourceBytes`.

## [0.1.12] - 2026-09-04

### Fixed
- **Several dialog forms still unreachable on landscape phones**
  (closes #47). v0.1.11 fixed the v0.1.10 Settings-dialog collapse
  but did not address three other panes where the dialog body was
  too narrow for the controls.
  - **Camera pane `StepperRow`**: the label was squeezed to ~0
    width when the two `OutlinedButton`s took their natural
    width first, so the integer value was invisible. Switched to
    a vertical `Column` with the label on top and the buttons
    side-by-side with `weight(1f)` below. Compact `contentPadding`
    (8/4 dp) keeps the row height modest.
  - **Slew / Goto `Lat`/`Lng` fields**: laid out side-by-side
    inside a 250dp dialog the `OutlinedTextField` label got
    rotated to vertical text. Stacked the two fields vertically
    with `fillMaxWidth()`.
  - **Helpers (Astro) pane**: all four rows (`Dither`, `Settling`,
    `Limits`, `Auto-level`) overflowed the 250dp-wide dialog by
    ~28dp, clipping controls and rotating the read-only caption
    to vertical text. Restructured each row into a two-line
    layout — read-only label and current value on the first
    line, interactive controls on the second.
- **`FeatureFlagsPane` weight regression** (closes #47
  firmwareUpload reachability). The `Modifier.weight(1f, fill = true)`
  v0.1.10 added to pin the build-identity footer created a
  circular measurement inside the AlertDialog's `text` slot and
  collapsed the dialog to 284px regardless of the outer
  `CalloutDialog` scroll, clipping all 25 flag rows. The
  version label is no longer pinned, but it now scrolls with the
  list so every flag (including `firmwareUpload`) is reachable.
  `CalloutDialogNoScrollWrapperTest.featureFlagsPaneOwnsZeroVerticalScrolls`
  is updated to drop the removed-weight assertion.
- **`PlatformFile.deleteIfExists` (Android) returned `true` for
  missing files**. The Android actual was
  `f.delete() || !f.exists()`. When the file does not exist
  `f.delete()` returns `false`, but `!f.exists()` returns `true`,
  so the whole expression returned `true`. The JVM actual
  (`Files.deleteIfExists(p)`) was already correct, so the bug
  was Android-only. User-visible effect: tapping
  **Forget saved mount** in the Reconnect dialog with no
  marker file present reported
  *Forgot saved mount* (success) instead of
  *No saved mount to forget*. Caught by
  `AppViewModelSessionMarkerTest.forgetMarkerWithoutFileIsANoOp`
  — note that `:composeApp:testDebugUnitTest` resolves
  `PlatformFile` `expect/actual` to the Android variant even
  though the test runs on the JVM host.

## [0.1.11] - 2026-09-04

### Fixed
- **Callout dialog body clipped on landscape phones** (closes #45
  follow-up, completes the v0.1.8 fix). The Slew callout's Plate
  solve section, Slew/Cancel buttons, and rotated Lat/Lng labels
  were clipped at 472 px below the visible 880x948 dialog area
  because v0.1.6's "no same-axis nested scrollables" policy
  (reverting v0.1.5's outer `verticalScroll`) only added a per-pane
  scroll to Settings. Every other callout (GotoPane, CameraPane,
  FirmwarePane, etc.) was re-clipped at the bottom of the
  AlertDialog's bounded `text` slot.
  - v0.1.10 added `Modifier.weight(1f, fill = true)` to the inner
    FeatureFlagsPane Column so its build-identity footer was
    pinned. That weight is exactly the bounded-height precondition
    the v0.1.5 outer scroller was missing.
  - v0.1.11 therefore relocates the single `verticalScroll` from
    `FeatureFlagsPane.kt` up into the outer `CalloutDialog`
    Column in `OpenPolarisApp.kt`, keeping the inner
    FeatureFlagsPane weight. The total `verticalScroll` count
    across the commonMain UI tree is still exactly 1
    (`CalloutDialogNoScrollWrapperTest.noSameAxisNestedVerticalScrollsAcrossUiTree`),
    and every callout now scrolls, including Slew which reveals
    its previously clipped Plate solve section, Slew/Cancel
    buttons, and rotated Lat/Lng labels.

### Tests
- `CalloutDialogNoScrollWrapperTest` inverted to enforce the new
  v0.1.11 policy:
  - `calloutDialogBodyOwnsExactlyOneVerticalScroll` asserts the
    `CalloutDialog` body in `OpenPolarisApp.kt` is the sole
    `Modifier.verticalScroll` owner and the `rememberScrollState`/
    `verticalScroll` imports are present.
  - `featureFlagsPaneOwnsZeroVerticalScrolls` asserts
    `FeatureFlagsPane.kt` has no `Modifier.verticalScroll` but
    still uses `Modifier.weight(1f, fill = true)` on its inner
    Column (footer pin).
  - `noSameAxisNestedVerticalScrollsAcrossUiTree` unchanged
    (asserts total across `OpenPolarisApp.kt` / `FeatureFlagsPane.kt` /
    `FullControlPanes.kt` / `Panes.kt` is still 1).
- `MobileResponsiveLayoutContractTest.calloutDialogBodyOwnsTheSoleVerticalScroll`
  inverted from its v0.1.9 name
  `calloutDialogBodyIsStillNotAVerticalScroll` to reflect the
  v0.1.11 ownership inversion.

## [0.1.9] - 2026-09-03

### Added
- **Compose UI test baseline** (resolves #47).
  `MobileResponsiveLayoutContractTest` adds 9 source-level policy-pin
  tests under `composeApp/src/commonTest/kotlin/dev/openpolaris/ui/`
  that lock down the v0.1.8 mobile responsive-layout contract. The
  tests run on every CI invocation via `./gradlew :composeApp:jvmTest`
  and require zero new dependencies.
  - `phoneDetectionUsesBothSizeClasses` pins that the phone
    detection combines width and height size classes (so landscape
    phones with Compact height get the phone layout, not the tablet
    layout).
  - `operateItemsPlusMoreItemsPartitionIsExclusiveAndTotal` pins that
    OperateItems (Connection, Slew, Camera, Preview) plus MoreItems
    (Helpers, Firmware, VR, Readme, Settings) are disjoint and total
    9 callouts.
  - `dangerousCalloutsAreBehindMoreMenu` pins that destructive or
    admin panes live in the overflow More menu, not the main rail.
  - `moreMenuButtonIsPresentInPhoneRail` pins the MoreMenuButton
    handle is wired into the phone rail.
  - `firmwarePaneIsPaginatedIntoThreeSteps` pins
    `FirmwareStep1`/`2`/`3` exist as `private fun`s and the
    `FirmwarePane` owns the step state.
  - `cameraPaneIsLaidOutInTwoColumns` pins CameraPane builds a
    stepper list and partitions it into two columns.
  - `connectionPaneUsesFlowRowNotRow` pins the connection pane opts
    into `ExperimentalLayoutApi` for FlowRow reflow.
  - `calloutDialogBodyIsStillNotAVerticalScroll` re-pins the v0.1.6
    #40 / #42 contract that the callout dialog body is a plain
    Column with no verticalScroll (regression guard).
  - `settingsPaneStillExposesBuildIdentity` re-pins the v0.1.5 #43
    fix that the settings pane exposes the `versionLabel` parameter
    for build identity.

## [0.1.8] - 2026-09-03

### Fixed
- **Callout dialog clipped on landscape phones** (resolves #45).
  Three tall panes (Firmware, Camera, Connection) used a single
  Column that overflowed the dialog on landscape phones whose
  height is only ~360 dp. Each pane has been redesigned:
  - `FirmwarePane` is now paginated into three sub-composables
    (Step 1: file picker + MD5, Step 2: delivery + SSH, Step 3:
    upload progress) with Back/Next buttons. Step 1's Next is gated
    on both a picked file and the feature flag.
  - `CameraPane` lays its 10 steppers out as a 2-column grid
    (`Row` with two `weight(1f)` Columns) so a 320 dp phone fits
    without clipping.
  - `ConnectionPane` swaps its primary button `Row` for a
    `FlowRow` (`@OptIn(ExperimentalLayoutApi::class)`) so the
    buttons reflow when the dialog is narrow.
- **Callout rail density** (resolves #46). The single 9-item
  horizontal rail overflowed on 320 dp landscape phones. The
  callouts are now partitioned: 4 always-visible **Operate**
  items (Connection, Slew, Camera, Preview) plus a **More** menu
  button (handle icon) that opens a dropdown of the remaining 5
  admin items (Helpers, Firmware, VR, Readme, Settings). On
  tablets / Expanded width the rail stays vertical with all 9
  callouts visible.

### Changed
- Phone detection now uses **both** width and height size classes:
  `wide = !widthCompact && !heightCompact`. Previously a phone in
  landscape (width Expanded, height Compact) was being routed to
  the tablet layout, which only made the callout-clipping problem
  worse.
- Status strip and Az/Alt readout now use `MaterialTheme.typography`
  styles (`titleSmall`, `bodySmall`) instead of unstyled `Text`.
  This is a prerequisite for a future dark-mode contrast pass.
- A `NightTypography` override on `Theme.kt` lets the readouts
  pick up a system font consistently across light and dark.

## [0.1.7] - 2026-09-03

### Fixed
- **Critical: first-launch crash on Android 14** (resolves #43).
  The runtime permission launcher was registered inside
  `setContent { remember { ... } }`, which fires during the first
  composition frame — *after* the activity reaches RESUMED. Android's
  `ActivityResultRegistry.register` enforces "must be called before
  STARTED" and throws `IllegalStateException` on Android 14 (API 34).
  Result: the published v0.1.6 APK crashed instantly on launch with
  no UI. Fix hoists `permissionLauncher` to a member field (matching
  the working `openDocumentLauncher` pattern) and uses a
  `pendingPermissionResult` trampoline to hand the grant back into
  the composable. Verified on an Android 14 x86_64 emulator:
  install → tap launcher icon → MainActivity is topResumedActivity,
  no `FATAL EXCEPTION` in logcat, app renders the "Find & wake
  Polaris…" UI. Closes the regression first reported in #43.

## [0.1.6] - 2026-09-03

## [0.1.6] - 2026-09-03

### Fixed
- **Reverted `verticalScroll` wrapper on `CalloutDialog`** (regression
  in v0.1.5). The wrapper was added as a "best of breed" UI/UX polish
  fix, but it directly undid the `ff0672a` fix for #40 ("android app
  crashes on open") and made the app fail to start on Android. The
  dialog body is now a plain `Column` again — matching the Benro
  Connect aesthetic, restoring the #40 fix, and avoiding a visible
  scroll bar in a 320×568 dp landscape phone. Tall panes (Firmware,
  Camera) are redesigned to fit on screen rather than relying on a
  scroll bar; Settings uses collapsible sections (Day-to-day /
  Advanced / Admin).

## [0.1.5] - 2026-09-03

### Added
- **Collapsible Settings sections** (`FeatureFlagsPane`).
  The 25 runtime feature flags are now grouped into three collapsible
  sections in the Settings dialog:
  - **Day-to-day** (expanded by default) — safe flags the user can flip
    with a plain toggle.
  - **Advanced** (expanded by default) — read-only knobs whose wire
    path is not yet verified, surfaced for visibility.
  - **Admin** (collapsed by default, header rendered in the error
    colour) — destructive actions (reboot, shutdown, firmware upload,
    SD format). Every change still requires an explicit confirm
    dialog, but the section starts collapsed so a casual user is not
  one tap away from the dangerous toggles.
  - The section policy is a pure function (`sectionFor`) and is
    pinned by `FeatureFlagsSectionPolicyTest` so a future edit of
    the `safe` / `destructive` flags cannot silently move a
    destructive flag out of the Admin section.
- **UI/UX review document** (`docs/UI-UX-REVIEW-2026-09.md`).
  Comprehensive audit of the OpenPolaris surface (6,830 LoC across 14
  files) covering layout, discoverability, admin vs. day-to-day
  separation, consistency, dead code, per-pane notes, and the fixes
  landed in this release.

### Changed
- **Callout rail labels** (`OpenPolarisApp.kt`). Replaced cryptic
  glyphs (`Cam`, `FW`, `VR`, `?`, `Cfg`) with full words
  (`Camera`, `Firmware`, `3D view`, `Guide`, `Settings`) so a
  first-time user can read the rail without hovering for tooltips.
  Longest label is 8 characters; fits the 320 dp-wide compact rail
  without overflow.
- **Callout dialogs deliberately do NOT scroll** (`CalloutDialog`).
  Content is laid out top-to-bottom in a plain `Column`; if a pane
  is taller than the dialog, Material's default `AlertDialog`
  clips it. This mirrors the Benro Connect app (no scroll bars on
  detail panes) and was specifically required by the locked 320×568
  dp landscape orientation for the Android app. Tall panes are
  redesigned to fit on screen (Settings now uses collapsible
  sections) rather than wrapping in a scroll bar. An earlier
  `verticalScroll` wrapper was added in this release and then
  reverted before tagging because it directly undid the
  `ff0672a` fix for #40 ("android app crashes on open").

### Removed
- **Dead code: `StatusPane`** (`Panes.kt`). The function was defined
  but had zero callers (verified by `grep`). The same status info
  (mode / battery / tracking / half-speed / AHRS) is already always
  visible via the `StatusStrip` and `PositionReadout` chrome in
  `OpenPolarisApp`.

## [0.1.4] - 2026-09-03

### Added
- **Windows desktop installer pipeline** (`:desktopApp:createDistributable`,
  `:desktopApp:packageMsi`, `:desktopApp:createExeInstaller`).
  - App-image folder (bundled JRE + `OpenPolaris.exe`) via jpackage.
  - MSI installer via jpackage `--type msi` + WiX 3.0+
    (`choco install wixtoolset` on the build host).
  - Single-file EXE installer via Inno Setup 6+ (`:desktopApp:createExeInstaller`,
    skipped on non-Windows hosts).
  - Skiko AWT native runtime resolved per host OS via
    `compose.desktop.currentOs`, so a Windows build pulls windows-x64
    natives automatically.
- **Windows CI job** (`.github/workflows/ci.yml::windows-desktop`):
  builds app-image, MSI, and Inno EXE on `windows-latest`. Explicit
  WiX install + `candle -?` preflight + non-empty-MSI verification
  so the job fails fast with a diagnostic instead of uploading a
  0-byte artifact (#41). App-image build is independent of the MSI
  step so a broken WiX install does not mask a working app-image.
- **v1 parity audit** (`docs/OPENPOLARIS-PARITY-2026-09-03.md`,
  467 lines). Compares OpenPolaris against the Benro Connect v1
  Polaris surface across all 9 protocol areas. Verdict: feature
  equivalent on every Benro Connect v1 Polaris capability; exceeds
  Benro in 12 areas (plate solving, BT-wake+AP-scan bridge, fail-closed
  MD5 gate, on-board install watcher, unsafe-override confirmation,
  feature flags pane, protocol trace viewer, embedded catalog, etc.).
  Scoped to Polaris ONLY — Theta is explicitly out of scope (Ricoh's
  360° camera product uses a different protocol and is not a Polaris
  feature). Verdict block lists T1–T6 excluded capabilities.

### Fixed
- **Firmware upload verify-before-upload is now fail-closed (#39).**
  In v0.1.3 the MD5 cross-check was opt-in: a blank or null
  `expectedMd5` was silently treated as "no cross-check" and the
  upload proceeded. v0.1.4 makes the MD5 **required** in the normal
  path so that an unverified bytes blob can never reach the SD card
  or the SCP/SSH transport.
  - `FirmwareUpdateController.start()` now takes a new
    `unsafeAllowNoChecksum: Boolean = false` escape hatch. The
    `expectedMd5` parameter remains `String? = null` for source
    compatibility, but is now treated as **required** in the normal
    path: a blank or null value causes `start()` to return
    `Status.Failed("missing expected MD5; ...")`. Production callers
    in `AppViewModel.uploadFirmware()` always supply a real value and
    never set `unsafeAllowNoChecksum = true`. The MD5 gate runs
    **before** the SD free-space pre-flight and **before** any
    delivery dispatch, in this exact order:
    1. empty bytes
    2. 128 MB size cap
    3. MD5 presence (blank/null → `Failed`, unless
       `unsafeAllowNoChecksum = true`)
    4. MD5 format (must be exactly 32 hex characters)
    5. MD5 compare (case-insensitive)
    6. SD free-space pre-flight (§6 #4)
    7. delivery dispatch
  - `AppViewModel.uploadFirmware()` pre-validates the user-pasted
    expected MD5 (trim, length, hex charset) and short-circuits to
    `Status.Failed(...)` with a clear message on bad input. The
    production path never sets `unsafeAllowNoChecksum = true`.
  - 4 new controller tests cover the fail-closed behaviour
    (`blankExpectedMd5FailsBeforeAnyWireTraffic`,
    `nullExpectedMd5FailsBeforeAnyWireTraffic`,
    `malformedExpectedMd5FailsBeforeAnyWireTraffic` — 5 bad cases —
    and `unsafeAllowNoChecksumTrueSkipsMd5Check` which replaces the
    prior `md5NullBehavesAsBefore` opt-in test). 16 pre-existing
    happy-path / error-path tests were updated to pass a real
    `expectedMd5 = md5Of(payload)` (or `unsafeAllowNoChecksum = true`
    for the single test that exercises the NoOp-delivery sentinel
    and is unrelated to MD5). All
    `:shared:jvmTest` and `:composeApp:jvmTest` pass.
  - `docs/FIRMWARE-UPLOAD-AUDIT-2026-09-01.md` §6 #2 progress note
    updated to reflect the v0.1.4 order and the v0.1.x "free-space
    runs first" wording is now marked stale.
- **Android ANR on open + landscape lock (#40).** The
  `MountWifiScan` BroadcastReceiver was being parked on the main
  thread by a `runBlocking` call, producing a ~5 s ANR. Now uses
  `suspendCancellableCoroutine` + `withTimeoutOrNull` on
  `Dispatchers.IO` with a single `@Volatile` continuation slot that
  bridges the main-thread broadcast to the IO-parked coroutine.
  `MainActivity` is now `screenOrientation="sensorLandscape"`,
  `configChanges="orientation|screenSize|screenLayout|smallestScreenSize"`,
  and `resizeableActivity="true"`. Also: 'Open Polaris' header
  removed from `StatusStrip`; `CalloutDialog` no longer wraps content
  in `verticalScroll`.
- **Desktop Wake + Bridge buttons now wired (#41-related).**
  Previously clicking Wake or Bridge in the Connection pane did
  nothing (the buttons existed but had no viewmodel handlers).
  Wired through `AppViewModel` so the desktop mirrors the Android
  flow: BT-wake on the gimbal MAC if reachable, then AP-scan for
  `polaris_*` networks, then connect.
- `ResponseParser`: the live-captured wire body shape
  `1&<code>&<type>&<payload>#` is now accepted. Previously the
  parser required the body to start with `&<code>...` which silently
  rejected every legitimate gimbal frame. All 5 protocol tests that
  parse such frames are now green.
- `BurstKt` (`:liveBurst` task) — fixed the gradle wiring so
  `tools:cli-probe:liveBurst` actually invokes the 9-code burst
  instead of running the default smoke probe.
- `FileList::fromFrame` method-reference was inapplicable to the
  `FileListRequest` request-type parameter of the FILE_LIST
  descriptor; replaced with an explicit lambda in `CommandTable`.
  The VM supplies the response-side parser separately so behaviour
  is unchanged.
- `CommandTableTest.burstPreCameraParsersCoverAllSteps` now exercises
  the actual parsers for all 9 burst codes (808, 809, 802, 778, 779,
  775, 824, 524, 543) instead of silently skipping 5 of 9 via a
  `?: continue` fallback. A new burst code in `CommandTable` will
  fail the test rather than pass with reduced coverage.
- Polkit: `/etc/polkit-1/rules.d/50-openpolaris-wifi-scan.rules`
  installed and active. Silences the GNOME WiFi-panel auth flood
  by short-circuiting the 8 relevant NetworkManager actions to
  `polkit.Result.YES` for user `ian`. Deployed via
  `scripts/install-wifi-polkit-rule.sh` (idempotent, uses `pkexec`
  with a graphical agent).

### Notes
- No protocol changes. The only signature change on
  `FirmwareUpdateController.start()` is the new
  `unsafeAllowNoChecksum: Boolean = false` parameter (defaulted, so
  it is source- and binary-compatible at the call site). Callers
  wishing to preserve the v0.1.x "MD5 is optional" behaviour must
  explicitly pass `unsafeAllowNoChecksum = true`.
- Android `versionName = "0.1.4"`, `versionCode = 5`.
- Windows MSI build requires WiX 3.0+ on the build host. The
  Compose plugin does NOT auto-download WiX; install with
  `choco install wixtoolset` before running `:desktopApp:packageMsi`.

## [0.1.3] - 2026-09-02

### Fixed
- **Firmware upload verify-before-upload (Phase 1a §6 #2 of the
  firmware audit):** every byte of the chosen local zip is now hashed
  with a pure-Kotlin RFC 1321 MD5 and compared (case-insensitive,
  trim-tolerant) to a user-pasted expected MD5 before any wire/SCP
  traffic. The 121 MB SD free-space pre-flight still runs first. A
  mismatch short-circuits with `Status.Failed("MD5 mismatch: ...")`
  and no firmware-related opcodes are written to the socket — mirrors
  the `crcInfo` MD5 step the production `polestar_app` performs
  during the on-device install. Wired end-to-end:
  - `shared/.../util/Md5.kt` (new) — 14 unit tests, all green.
  - `shared/.../domain/FirmwareUpdateController.kt` — new
    `expectedMd5: String?` parameter + cross-check + 3 new
    controller tests covering match, mismatch-no-wire, and null
    backwards-compatible behaviour.
  - `composeApp/.../ui/AppViewModel.kt` — `firmwareExpectedMd5` user
    input state, `pickedFirmwareMd5` computed surface, and
    `Md5.digest(bytes)` computation at upload time (avoids reading
    the 50 MB zip twice).
  - `composeApp/.../ui/Panes.kt` `FirmwarePane` — `OutlinedTextField`
    for the expected hash and a "Local MD5: …" display line.
  - `docs/FIRMWARE-UPLOAD-AUDIT-2026-09-01.md` — append-only
    progress log for §6 #2.
  - All 476 `:shared:jvmTest` tests pass.

### Notes
- 128 MB size cap and SD free-space pre-flight (Phase 1a §6 #3, #4)
  were already in place in v0.1.2 and remain unchanged.
- v0.1.3 is a bug-fix release. No protocol changes, no API-breaking
  changes for downstream Kotlin/Compose consumers of `shared`.

## [Initial] - 2026-08-27

### Added
- **Relocated** the OpenPolaris Kotlin/Compose client from
  `agents/benro-polaris-firmware-analysis` of
  [benro-polaris-firmware-patcher](https://github.com/ian-morgan99/benro-polaris-firmware-patcher)
  to its own home at
  [OpenPolaris](https://github.com/ian-morgan99/OpenPolaris).
- **Re-rooted** the source tree: `polaris-client/*` is now the
  repository root.
- **History rewritten** with `git filter-branch --subdirectory-filter
  polaris-client` to keep only the client commits.  All firmware-patcher
  infrastructure commits removed.
- `LICENSE` (MIT).
- `NOTICE` recording the origin.
- `docs/FIRMWARE-ANALYSIS-ALPACA.md` (495 lines of firmware ground-truth
  context that informs the protocol implementation).

### Notes
- The `polaris-client/docs/` subdirectory remains the canonical project
  documentation (SPEC, PROTOCOL, ARCHITECTURE, PLAN, OVERVIEW,
  SMOKE-TEST, EVALUATION).
- For the firmware-patcher side of the Polaris ecosystem (HDMI geometry,
  Pentax libgphoto2 integration, FwPkt rebuild), see
  [benro-polaris-firmware-patcher](https://github.com/ian-morgan99/benro-polaris-firmware-patcher).

## 0.1.10

Pinned the build-identity footer in the Settings dialog so it is always
visible (not clipped by the AlertDialog's bounded `text` slot). The
inner scrollable Column now uses `Modifier.weight(1f)` so the version
label is always pinned to the bottom of the dialog instead of being
rendered off-screen on portrait and landscape phones.

This was the reason release APKs were being misread as "0.1.6" — the
in-app label was being clipped below the dialog viewport, so users
couldn't visually confirm which build they were running. With this
fix, `v0.1.10 (dev.openpolaris.app)` is now always visible in the
Settings dialog footer.

v0.1.10 test baseline: 70 tests pass (`./gradlew :composeApp:jvmTest`).
The `settingsPaneStillExposesBuildIdentity` test was strengthened to
pin the `Modifier.weight(1f)` requirement so a future edit cannot
silently regress to the clipped-footer layout.
