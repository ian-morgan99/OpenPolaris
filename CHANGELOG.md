# Changelog

All notable changes to OpenPolaris are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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

## [Unreleased]

### Added
- **Phase 1a §6 #5 (firmware audit):** the seven unverified opcodes
  used by the experimental wire-delivery channel — `SYS_FW_UPGRADE`
  (810), `FILE_UPLOAD_FW` (784), `FILE_UPLOAD_CHUNK` (794),
  `FILE_UPLOAD_END` (795), `SYS_FW_PROGRESS` (811), `SYS_REBOOT` (812),
  and `SYS_SHUTDOWN` (813) — now carry explicit
  `@Deprecated(level = DeprecationLevel.WARNING)` annotations in
  `shared/src/commonMain/kotlin/dev/openpolaris/core/protocol/Codes.kt`.
  Each annotation cites `docs/FIRMWARE-UPLOAD-AUDIT-2026-09-01.md` §2
  and explains that the verified install path is the on-board SD-card
  SCP delivery (`scp /app/sd/FwPkt.zip`), not 9090 push. The constants
  remain usable so the experimental `WIRE` delivery mode (gated behind
  `FeatureFlags.firmwareUpload` and behind a red brick-warning banner
  in `FirmwarePane`) keeps working, but the deprecation surfaces the
  decompile gap to any new caller and forces an explicit
  `@Suppress("DEPRECATION")` at the call site. Call sites annotated:
  - `SimulatedProtocol.kt` — `@file:Suppress("DEPRECATION")` (the
    simulator must keep responding to all six opcodes for testing).
  - `CommandTable.kt` — `@file:Suppress("DEPRECATION")` (Descriptor
    entries for all seven).
  - `FirmwareUpdateController.kt` — `@file:Suppress("DEPRECATION")`
    (drives the experimental wire delivery path).
  - `AppViewModel.kt` — narrow `@Suppress("DEPRECATION")` on
    `reboot()` and `shutdown()` (the only two uses; both are gated by
    `FeatureFlags.allowReboot` / `FeatureFlags.allowShutdown` and now
    carry inline comments reiterating the audit warning).
  All 476 `:shared:jvmTest` and 53 `:composeApp:jvmTest` still pass.
  Debug APK rebuilds clean (v0.1.3, code 4). Closes Phase 1a item §6
  #5. See `docs/FIRMWARE-UPLOAD-AUDIT-2026-09-01.md` "Phase 1a status"
  table for the full Phase 1a close-out.
- `tools:cli-probe` `--full` burst mode: pass `--full` as the 3rd arg
  to send the canonical 9-code pre-camera burst from
  `CommandTable.BURST_PRE_CAMERA` (808, 809, 802, 778, 779, 775, 824,
  524, 543) instead of the smoke-test default. Wired up as the new
  `:tools:cli-probe:liveBurst` gradle task. Use this against a real
  gimbal once the `Polaris_XXXX` AP is in range; the simulator sees
  the same traffic.
- `scripts/live-smoke.sh` (Step 3 of `PLANNING-2026-08.md`): automates
  the network-state check, the gimbal TCP reachability check, and the
  post-connect pre-camera burst probe. Refuses to fire the burst
  unless the host is on a `polaris_*` AP (because from the tplink
  subnet, `192.168.0.1` is the TP-Link admin page, not the gimbal).
  Use `nmcli connection up polaris_d13e86` first, then
  `scripts/live-smoke.sh` (or `scripts/live-smoke.sh --check` to
  probe reachability without firing the burst). Output is appended
  to `/tmp/openpolaris-live-smoke.log` and rotated past 1 MB so it
  can be captured across many runs and pasted into
  `polaris-re-results.md` §10 to tune the `fromFrame` parsers.

### Changed
- **License:** GPL-3.0 → **MIT**.  The OpenPolaris codebase has not
  yet accepted third-party contributions, so the project owner can
  relicense the original work unilaterally.  NOTICE records the
  origin from `benro-polaris-firmware-patcher`.
- **MountSession background reader (issue #6 core):** the session
  now owns a single `runReaderLoop` coroutine that parses every
  frame off the socket and dispatches it — 538 push frames go to
  the new `tilt` `SharedFlow`, everything else goes to the waiter
  registered by the matching `request(code)`. Previously each
  request spun up its own read loop, so a 538 push arriving
  *between* a request and its reply would be silently dropped
  (the inner reader was torn down when the deferred completed).
  Push frames now reach `MountSession.tilt` even while a
  request/response is in flight, and the demux is correct under
  interleaving (covered by `MountSessionReaderTest`).

### Fixed
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
- `ResponseParser`: the live-captured wire body shape
  `1&<code>&<type>&<payload>#` is now accepted. Previously the
  parser required the body to start with `&<code>...` which silently
  rejected every legitimate gimbal frame. All 5 protocol tests that
  parse such frames are now green.
- `BurstKt` (`:liveBurst` task) — fixed the gradle wiring so
  `tools:cli-probe:liveBurst` actually invokes the 9-code burst
  instead of running the default smoke probe. Also reworded the
  license-sensitive comments throughout the protocol layer to
  "live-captured" / "corpus-derived" vocabulary.
- `FileList::fromFrame` method-reference was inapplicable to the
  `FileListRequest` request-type parameter of the FILE_LIST
  descriptor; replaced with an explicit lambda in `CommandTable`.
  The VM supplies the response-side parser separately so behaviour
  is unchanged.

### Added (full control panel)
- **`FeatureFlags`**: compile-time default values + runtime
  `FeatureOverrides` for safe modes. `basicControls`,
  `postConnectBurst`, `experimentalCamera`, `catalog`, `alignment`
  are ON. `advancedAstro`, `autoLevel`, `systemSettings`, `wifiScan`,
  `allowReboot`, `allowShutdown`, `demoMode`, `wifiBridge`,
  `fileManager`, `omsRead` are ON. `timelapse`, `ditherAdvanced`,
  `fileManagerMutate`, `fileManagerFormat`, `wifiConnect`,
  `firmwareUpload`, `omsScheduler`, `rawFrameLog`, `verboseLogging`
  are OFF. The full set is centralised in
  `shared/.../config/FeatureFlags.kt` and surfaced via
  `AppViewModel` to the UI.
- **Helpers callout pane**: dither, settling time, limits
  (UNVERIFIED), auto-level enable/trigger, go-home, ex-axis state.
- **System callout pane**: time / timezone / language, buzzer / LED,
  WiFi scan + connect + disconnect + band select, BLE scan,
  reboot, shutdown.
- **Files callout pane**: SD header, file list, file delete,
  rename, protect, info, SD format (gated by `fileManager` /
  `fileManagerMutate` / `fileManagerFormat`).
- New `CommandTable` descriptors for FILE_LIST / FILE_DELETE /
  FILE_RENAME / FILE_PROTECT / FILE_INFO / FILE_SD_FORMAT /
  OMS_TASK_LIST / WIFI_LIST / WIFI_SCAN / WIFI_CONNECT /
  WIFI_DISCONNECT (see commit 6f3649a for the full list).
- New `MountState` data classes: `FileList`, `FileEntry`,
  `ExAxisState`.

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
