# Open Polaris — Functional Specification (v1: Faithful Replica)

**Project:** open-source replacement for Benro Connect (`com.snoppa.libra` v3.0.33)
**Design philosophy:** v1 is a *functional clone* — same features, same flows, same protocol behavior as the
stock app, with a cleaner UI. Enhancement hooks are designed-in but disabled. Re-imagining comes in v2 once
the clone is proven against real hardware.

The stock app is our specification **and** our test oracle: for every screen we ask "what does Benro Connect do
here?" and mirror it.

## 1. Users & scenarios

| Persona | Scenario |
|---|---|
| Astro photographer | Polar-align, star-align, goto target, track at sidereal/lunar, dither, shoot timelapse |
| Day shooter | Sun/moon tracking, timelapse, panorama (panorama capture only — stitching stays in official app) |
| Existing owner | Uses official app for one-time calibrations and firmware updates; uses this client nightly |

## 2. Feature inventory (mirrors Benro Connect)

### 2.1 Connection & status
- Wi-Fi discovery of mount (TCP `192.168.0.1:9090`); connect/disconnect; auto-reconnect.
- Status bar: battery, mode, free shots/time (from 284 push), connection state.
- Live camera preview via MJPEG stream (`:8080/?action=stream`).

### 2.2 Shooting modes (mode codes from 284/285)
1. **Normal photo/video** — shutter trigger, timer.
2. **Timelapse** — interval, shot count, duration; start/pause/resume/end.
3. **Dynamic lapse** — keyframe positions (uses 517 position reads + 535 sets).
4. **Panorama** — grid capture (capture only; stitching not replicated).
5. **Sun tracking** — rise/set times computed locally; goto sun; track.
6. **Star tracking** — the astro core (below).
7. **Precompile/appointment** — scheduled session start.

### 2.3 Star-tracking mode (astro core)
- **Alignment**: pick alignment stars from an embedded catalog → app computes target alt/az
  (planetarium math) → send 530 per star (multi-star via `num`). Progress UI mirrors stock flow.
- **Goto**: search catalog/targets → send 519 with alt/az/location; cancel supported.
- **Tracking**: start/stop via 531; sidereal/lunar rate selection (auto-lunar when Moon selected);
  half-speed toggle via 536 (**inverted payload** — see PROTOCOL.md §5).
- **AHRS gating**: enable on entering mode (520 state:1), disable on exit; live pointing readout
  from 518 quaternions (compass/alt display like stock).
- **Astro helper settings dialog**: tilt switch (538), dither on/off (540), angle limits (542),
  settling time (544), auto-level enable/state (548/549).

### 2.4 Camera control (in scope — full parity)
- Capture mode, ISO, aperture, shutter speed, white balance, bulb time, RAW/JPEG.
- Get current values on connect; set with range validation per model; controls locked while a
  sequence/capture is running (codes 258–311; see PROTOCOL.md §3.4).

### 2.5 Manual control
- Jog rocker (yaw/pitch/roll): speed and angle modes (513–516, 521–522).
- Position readout (517); position reset (523); direct position set (535).

### 2.6 Explicitly NOT replicated (kept for official app)
Firmware update flashing; cellular/cloud remote; panorama stitching; Benro account/login;
Theta secondary-device support.

## 3. Disabled enhancement hooks (designed now, shipped v2)

Each is a stub behind a feature flag so v1 architecture doesn't need rework:

| Hook | Where it plugs in | Flag |
|---|---|---|
| Custom tracking-rate trims | jog-speed commands (513–522) applied during tracking | `enh.rateTrims` |
| Drift-error readout | periodic 517 sampling vs expected sidereal motion | `enh.driftMeter` |
| Sync-point submission | local model only in v1 (no firmware command exists) | `enh.syncPoints` |
| Extended status fields | tolerant parser already ignores unknown keys | `enh.extStatus` |

## 4. Screen map

1. **Connect** — device list/connect state, quick status.
2. **Home/Shoot** — mode selector strip (the 7 modes), preview pane, shutter.
3. **Camera params panel** — slide-up sheet over Home.
4. **Star sky** (astro) — catalog search, alignment flow wizard, goto, tracking controls,
   helper-settings dialog, live pointing display.
5. **Sun** — rise/set card, goto/track.
6. **Timelapse / Dynamic lapse / Panorama** — parameter forms + progress.
7. **Jog pad** — overlay accessible from any mode.
8. **Settings** — units, catalog prefs, feature flags (enhancement hooks hidden by default).

## 5. Behavioral rules inherited from ground truth

- Keepalive 284 sent on mode transitions only (match stock behavior; no aggressive polling).
- Command timeout 2 s; slew/goto timeout 10 s; all failures surface as user-visible toasts +
  logged detail.
- AHRS must be explicitly disabled when leaving astro mode or backgrounding (stock behavior).
- All payloads built exactly per PROTOCOL.md formats; a shared builder module prevents drift.

## 6. Non-goals for v1

No re-imagined UX, no new features beyond stock, no iOS (Android first; architecture keeps UI
portable), no cloud anything, no account system.

## 7. Acceptance criteria for v1

A user can perform a complete night's session using only this client:
connect → align (2–3 stars) → goto target → track sidereal → toggle half-speed → enable dither →
run timelapse with camera params set in-app → stop → disconnect — with behavior indistinguishable
from Benro Connect as verified side-by-side on hardware.
