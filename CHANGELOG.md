# Changelog

All notable changes to OpenPolaris are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
