# Open Polaris

An open-source, community replacement for the Benro Connect Android app, controlling the Benro
Polaris gimbal over its documented TCP protocol. **v1 goal: a faithful functional replica** of
Benro Connect — same features, same flows — with a cleaner UI and enhancement hooks designed-in
but disabled for v2.

> **Naming:** "Open Polaris" is an independent project, not affiliated with, endorsed by, or
> produced by Benro. "Benro" and "Polaris" are used only to identify hardware compatibility.

## Documentation

| Doc | Contents |
|---|---|
| **[USER-MANUAL.md](docs/USER-MANUAL.md)** | **End-user manual — bundled inside the app (Guide callout) and readable on the web. Start here if you just want to use the app.** |
| [SPEC.md](docs/SPEC.md) | Functional specification: feature inventory mirroring the stock app, screen map, behavioral rules, v1 acceptance criteria |
| [PROTOCOL.md](docs/PROTOCOL.md) | Complete protocol reference: transport, framing, all command codes/payloads, session lifecycle, known quirks (inverted halfSpeed, AHRS gating) |
| [CAMERA-PARITY-JUNIOR-AGENT-GUIDE.md](docs/CAMERA-PARITY-JUNIOR-AGENT-GUIDE.md) | One-feature-at-a-time handoff for deriving camera contracts from the local Benro APK, implementing them safely, testing them, and qualifying both camera bodies |
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | Tech stack (Kotlin Multiplatform + Compose), module layout, key design decisions, testing strategy |
| [PLAN.md](docs/PLAN.md) | Phased project plan with hardware-validated gates G0–G3, effort estimates, risk register |

## Why replica-first?

The stock app is both our specification and our test oracle: every screen mirrors a
known-good behavior we can verify side-by-side on hardware. Enhancement (custom tracking rates,
drift meter, sync points) ships in v2 behind feature flags once the clone is proven.

## Provenance

All protocol facts were derived from live gimbal captures and string-corpus analysis of the
vendor's WiFi/BT control channel — see `../docs/FIRMWARE-ANALYSIS-ALPACA.md`. No proprietary
code is copied; this is a clean-room implementation against documented behavior.

## Desktop release and local testing

The Linux desktop shortcut installed by `scripts/install-desktop-launcher.sh` launches through
`scripts/launch-desktop-image.sh`. That entry point checks the packaged app's embedded full Git
commit against the current clean checkout; if stale, it runs the JVM regression tests, rebuilds,
and verifies the image before opening it. It refuses to replace a currently running app image.
For a deliberate manual refresh, close OpenPolaris and run `scripts/update-desktop-launcher.sh`.

Pull requests and pushes to `main` run the shared/UI tests and build a Linux app-image candidate
with its source commit and SHA-256 recorded. Version-tag releases publish the verified Linux
desktop package alongside the other platform releases. A CI package or a clean build proves
which source was packaged; it does not prove live Polaris hardware behavior.

## License

TBD — recommend GPL-3.0 or Apache-2.0 before first public release (decide in Phase 0 review).
