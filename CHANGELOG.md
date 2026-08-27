# Changelog

All notable changes to OpenPolaris are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased] - Moved from benro-polaris-firmware-patcher

### Changed
- **Relocated** the OpenPolaris Kotlin/Compose client from
  `agents/benro-polaris-firmware-analysis` of
  [benro-polaris-firmware-patcher](https://github.com/ian-morgan99/benro-polaris-firmware-patcher)
  to its own home at
  [OpenPolaris](https://github.com/ian-morgan99/OpenPolaris).
- **Relicensed** from MIT to GPL-3.0.
- **Re-rooted** the source tree: `polaris-client/*` is now the
  repository root.
- **History rewritten** with `git filter-branch --subdirectory-filter
  polaris-client` to keep only the client commits.  All firmware-patcher
  infrastructure commits removed.

### Added
- `LICENSE` (GPL-3.0).
- `NOTICE` recording the origin and relicense.
- `docs/FIRMWARE-ANALYSIS-ALPACA.md` (495 lines of firmware ground-truth
  context that informs the protocol implementation).
- This CHANGELOG entry.

### Notes
- The `polaris-client/docs/` subdirectory remains the canonical project
  documentation (SPEC, PROTOCOL, ARCHITECTURE, PLAN, OVERVIEW,
  SMOKE-TEST, EVALUATION).
- For the firmware-patcher side of the Polaris ecosystem (HDMI geometry,
  Pentax libgphoto2 integration, FwPkt rebuild), see
  [benro-polaris-firmware-patcher](https://github.com/ian-morgan99/benro-polaris-firmware-patcher).
