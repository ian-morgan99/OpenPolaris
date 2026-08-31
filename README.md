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
| [SPEC.md](docs/SPEC.md) | Functional specification: feature inventory mirroring the stock app, screen map, behavioral rules, v1 acceptance criteria |
| [PROTOCOL.md](docs/PROTOCOL.md) | Complete protocol reference: transport, framing, all command codes/payloads, session lifecycle, known quirks (inverted halfSpeed, AHRS gating) |
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

## License

TBD — recommend GPL-3.0 or Apache-2.0 before first public release (decide in Phase 0 review).
