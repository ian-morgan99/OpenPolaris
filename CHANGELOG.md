# Changelog

All notable changes to OpenPolaris are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- `tools:cli-probe` `--full` burst mode: pass `--full` as the 3rd arg
  to send the canonical 9-code pre-camera burst from
  `CommandTable.BURST_PRE_CAMERA` (808, 809, 802, 778, 779, 775, 824,
  524, 543) instead of the smoke-test default. Wired up as the new
  `:tools:cli-probe:liveBurst` gradle task. Use this against a real
  gimbal once the `Polaris_XXXX` AP is in range; the simulator sees
  the same traffic.

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
