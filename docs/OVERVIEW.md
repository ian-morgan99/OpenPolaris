# Open Polaris — What It Is, How It Works, What's Coming

Open Polaris is an open-source replacement for the Benro Connect phone app, used to control the
Benro Polaris camera mount over Wi-Fi. It runs on Android (with a desktop build for development),
speaks the same wire protocol the official app uses, and adds astronomy features Benro never
shipped — real GoTo, plate-solved alignment, and a night-friendly interface.

This document explains what the system does today, how it works, and where it's headed.

---

## 1. The big picture

```
┌─────────────────┐   Wi-Fi AP (Polaris_XXXX)    ┌──────────────────┐
│  Your phone      │ ───────────────────────────▶ │  Benro Polaris   │
│  Open Polaris    │   TCP port 9090              │  gimbal + camera │
│  app             │ ◀─────────────────────────── │                  │
└─────────────────┘   JSON-ish text frames       └──────────────────┘
        │                                              ▲
        │ (future) platesolving                        │ HDMI / USB cable release
        ▼                                              │
┌─────────────────┐                                    │
│  Star catalogs   │                            ┌──────┴──────┐
│  + astrometry    │                            │ Your camera │
│  index files     │                            └─────────────┘
└─────────────────┘
```

Everything runs on the phone. The mount is a TCP server; the app is the brain.

---

## 2. How it talks to the mount

### Transport
- The Polaris broadcasts its own Wi-Fi access point (`Polaris_XXXX`). Join it, then open a TCP
  connection to `192.168.0.1:9090` (the gimbal's default control address; observed in live
  captures of the WiFi control channel).
- Messages are ASCII text frames of the form:

  ```
  1&<code>&2&<payload>#
  ```

  e.g. `1&519&2&az:180.0000;alt:45.0000;#` means "slew to azimuth 180°, altitude 45°".
- Every command gets a response frame back with the same code number. The mount also pushes
  unsolicited status frames (battery, mode, position) when state changes.

### Command families in use

| Codes | Purpose |
|---|---|
| 284 / 285 | Read / set shooting mode; 284 response carries battery, mode, state |
| 513–517 | Manual jog and position feedback (517 = current yaw/pitch/roll) |
| 519 | GoTo az/alt slew |
| 520 / 518 | AHRS quaternion pointing data |
| 530–533 | Star alignment flow, sidereal tracking start/stop/rates |
| 535 / 536 | Jog angle config, tracking half-speed & inversion |
| 537–549 | Astro helpers: tilt, dither, settling, hardware limits, auto-level |
| 258–311 | Camera parameters (ISO, shutter, aperture, etc.) |

The full table with payloads lives in [PROTOCOL.md](PROTOCOL.md), derived from live gimbal
captures and string-corpus analysis of the vendor's control channel.

### Session lifecycle
1. Connect TCP.
2. Send the push-mode handshake so the mount starts streaming status.
3. Exchange commands/responses; each request matches its response by code number.
4. On disconnect, the app auto-reconnects and re-handshakes.

---

## 3. What the app does today

### Connection & status
- Host/port fields with one-tap connect; auto-reconnect on drop.
- Live status pane: battery %, shooting mode, mount state — updated from pushed frames.
- **Demo mode**: a built-in simulated mount so you can explore the UI without hardware.
- **Wi-Fi bridge for laptops/desktops** (BT-as-control-plane, segregated data plane): the laptop
  keeps `wlp8s0` powered off until you ask to connect. The "Find Wi-Fi" button runs the bridge
  tool (`./gradlew :tools:bridge:run --args=...`) which does three things in order:
  1. **Wake the gimbal over Bluetooth.** The official Benro app does the same — it identifies the
     device via GATT, then asks the firmware to bring up its AP.
  2. **Bring up a saved NetworkManager profile** (`polaris_d13e86`) on `wlp8s0`. No Wi-Fi scans,
     no auth storms — the SSID is already known to NM and just gets reactivated.
  3. **Segregate the gimbal traffic from the LAN** via policy routing: a dedicated routing table
     routes `192.168.0.0/24` out `wlp8s0` only, leaving `enp11s0` as the default route for the
     internet. The gimbal cannot reach your home network, and your home network cannot reach the
     gimbal.
  Once the bridge is up, the app talks to `192.168.0.1:9090` exactly as it does on a phone joined
  directly to `Polaris_XXXX`. Tearing down (`--down`) reverses all three steps.

### Manual control
- Four-direction jog pad (yaw/pitch nudge) using the firmware's rate-adjust codes.
- Live position readout from 517 frames.

### Astronomy core
- **Location & time aware**: you enter latitude/longitude once; the app computes local sidereal
  time internally for all transforms.
- **GoTo two ways**:
  - *Alt/Az direct* — type azimuth/altitude, tap Go.
  - *RA/Dec* — enter equatorial coordinates; the app converts to alt/az for your location and
    time using Meeus-style astronomy math, then slews.
- **Arrival detection**: after issuing a slew the app polls position until within tolerance,
  then reports arrival.
- **Sidereal tracking**: start/stop, half-speed toggle, AHRS enable.
- **Star alignment wizard**: pick alignment stars, center them, submit — builds a client-side
  pointing model that corrects subsequent GoTos for cone error and leveling error.
- **Astro helpers**: tilt check, dither, settling time, **hardware limit clamps**
  (codes 541/542 — the app reads limits and refuses commands that would overwind a motor),
  and **auto-level** trigger/status.

### Camera control
- ISO, shutter speed, aperture steppers wired to the 258–311 parameter codes.
- Mode switching via 285.

### Night-mode UI
- Black background, red primary elements — preserves dark adaptation.
- Large touch targets sized for use with gloves at odd angles.
- All panes (Connection, Status, Jog, GoTo, Camera) on one scrollable screen; no hidden menus
  to fumble for in the dark.

### Desktop build
- Same codebase runs as a desktop window (Compose Desktop) for development and demo purposes;
- useful for protocol work without a phone attached.

---

## 4. Under the hood (briefly)

- **Kotlin Multiplatform + Compose** — one codebase, Android today, iOS possible later.
- **Single connection owner** (`MountSession`) — all traffic serialized through one socket with
  request/response matching by code; UI never touches sockets directly.
- **Command layer generated from one table** — every command's code/payload format defined once;
  builders and parsers derive from it, so adding a command is a one-line change.
- **Quirk handling centralized** — known firmware oddities (e.g., synthetic responses, payload
  quirks) are handled in one place, not scattered through UI code.
- **Astronomy math isolated** (`AstroMath`) — pure functions, heavily unit-tested: Julian dates,
  sidereal time, RA/Dec↔Alt/Az, refraction-corrected rise/set.
- **Pointing model** — simple TPOINT-style az/alt offsets fit from alignment stars; absorbs the
  dominant error terms without needing firmware support.
- **Tested** — full JVM test suite covers protocol framing, session logic, astro math, goto
  pipeline, and pointing model.

---

## 5. What's coming

### Near term — close the loop with hardware
- **First-light field test**: connect to a real Polaris, verify status/jog/tracking parity with
  the stock app. *(APK v0.1.0-debug is published for this.)*
- Fix whatever the field test surfaces — first contact with real hardware always does.

### Plate-solving alignment (the big upgrade)
Three-tier approach, so there's a path for every skill level:
1. **Beginner** — brightest-star alignment: grab one snapshot, solve against a bright-star list,
   done. No user input beyond "solve".
2. **Intermediate** — 3-star wizard automated: app picks stars from the catalog, slews, snaps,
   solves, refines — hands-free alignment.
3. **Expert** — tethered camera via HDMI/cable-release captures CR2 raws; solves offline-grade
   precision for pinpoint pointing.

**How the phone gets the star data:** the app downloads astrometry.net index files (~400 MB for
wide-to-narrow coverage — normal for modern apps) plus bright-star/Messier/comet catalogs on
first run or on demand. Downloads are triggered from the app with progress UI; after that,
everything works offline at the tripod.

**HDMI loop**: K01 board can push camera frames over RTSP → app receives image → solves locally →
sends corrective GoTo back to the mount. No firmware change needed.

### Firmware-side enhancements (research track)
Analysis of the Polaris firmware vs. the Alpaca driver ecosystem identified capabilities worth
adding *in the mount itself*, staged so nothing breaks existing behavior:
- In-device pointing correction (Alpaca-style `Sync`/`SlewToCoordinates` semantics).
- Native plate-solve result ingestion (mount accepts solved RA/Dec directly).
- Refined limit handling exposed over the protocol.
These land only after the app-only paths prove out; the app is designed so firmware upgrades
slot in without UI changes.

### Polish
- Timelapse UI (firmware step payloads already documented; scope decision pending).
- Comet/ephemeris target lists with altitude timelines.
- Observing-list support ("tonight's best targets" from your location/time).

---

## 6. Trying it now

1. Grab [`v0.1.0-debug`](https://github.com/ian-morgan99/benro-polaris-firmware-patcher/releases/tag/v0.1.0-debug)
   APK onto your Android phone.
2. Sideload it (allow unknown sources).
3. Join the Polaris Wi-Fi AP.
4. Open the app → Connect (host pre-filled).
5. Or tap **Demo** to explore without hardware.

Debug-signed; expect rough edges. Field-test findings go straight into the issue tracker.

---

## 7. Document map

| Doc | Contents |
|---|---|
| [SPEC.md](SPEC.md) | Feature-by-feature functional spec (v1 = faithful replica) |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Module layout, design decisions, testing strategy |
| [PROTOCOL.md](PROTOCOL.md) | Complete command reference derived from live gimbal captures |
| [PLAN.md](PLAN.md) | Phased delivery plan with hardware-gated milestones |
| [EVALUATION.md](EVALUATION.md) | Phase 0/1 close-out evaluation |
| [SMOKE-TEST.md](SMOKE-TEST.md) | Hardware smoke-test checklist |

The "Wi-Fi bridge for laptops" section above links the laptop BT→WiFi flow to the architecture
document, where the segregated routing table and NetworkManager integration live in detail.
