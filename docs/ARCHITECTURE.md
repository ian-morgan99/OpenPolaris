# Open Polaris — Architecture

## 0. Target platforms

| Platform | Priority | Notes |
|---|---|---|
| Android phone | v1 | Primary target; replica of stock app form factor |
| iPad / Android tablet | v1.1 (layout work only) | Same codebase, `WindowSizeClass`-adaptive layouts: two-pane master-detail (sky view + controls side by side), larger jog rocker, split alignment wizard |
| iOS phone | v2 | Shared core + shared Compose UI; platform shim for sockets/MJPEG |
| Desktop (JVM) | free | Falls out of KMP; useful for development/testing indoors |

**One codebase, four surfaces.** All business logic and all screens are shared; per-platform code is
limited to a thin shell (~5%): socket implementation, MJPEG decode surface, permissions.

## 1. Technology choice: Kotlin Multiplatform (KMP) + Compose

| Option | Verdict |
|---|---|
| **Kotlin Multiplatform + Compose** | **Chosen.** Required once iOS + tablet joined the scope: one codebase ships Android, iOS, iPad, and desktop. Compose Multiplatform covers all UIs with adaptive layouts (`WindowSizeClass` for phone vs tablet two-pane). Native performance for MJPEG decode and planetarium math. |
| Flutter | Viable alternative; slightly more mature iOS story today, but KMP keeps protocol layer in a language with first-class coroutines/sockets, matches the decompiled Java mental model, and preserves the JVM `cli-probe` tool for free. |
| React Native / web | Rejected: MJPEG rendering and long-lived socket reliability are friction points; astro users expect native feel. |
| Two native apps (Swift + Kotlin) | Rejected outright by the iOS requirement: doubles every feature forever. |

Non-negotiables behind the choice:
- The **protocol layer must be pure and platform-free** (no Android imports) so it can be unit-tested on JVM
  and reused by CLI tools or the Alpaca driver later.
- UI framework must support a custom sky view (canvas) — Compose Canvas suffices.

## 2. Module layout

```
polaris-client/
├── docs/                    # this documentation set
├── shared/                  # KMP module (targets: android, iosArm64, iosSimulatorArm64, jvm)
│   └── src/commonMain/
│       ├── protocol/        # PURE Kotlin. Framing, command builders, response parsers.
│       │                    #   CommandBuilder (one function per command; single source of payload formats)
│       │                    #   ResponseParser (key-based, tolerant of unknown keys)
│       ├── domain/          # PURE Kotlin. State machines & models.
│       │                    #   MountState (mode, battery, tracking state)
│       │                    #   AlignmentSession (multi-star flow state machine)
│       │                    #   TrackingController (start/stop/rate/half-speed incl. inversion)
│       │                    #   CameraParams (ranges per model, validation)
│       │                    #   MountSession (socket owner; Connection currently duplicated per platform — see EVALUATION.md)
│       └── astro/           # PURE Kotlin. Planetarium math.
│                            #   RA/Dec ↔ Alt/Az, sidereal time, sun/moon ephemeris (low-precision Meeus is enough),
│                            #   star catalog loader (bright-star set ~300 stars, embedded JSON)
├── composeApp/              # Compose Multiplatform UI — ALL screens shared across phone/tablet/desktop/iOS
│                            #   WindowSizeClass-adaptive: single-pane phone, two-pane tablet
├── androidApp/              # Android shell (manifest, permissions, service)
├── iosApp/                  # iOS shell (Xcode project wrapping the KMP framework)
└── tools/
    └── cli-probe/           # JVM CLI that speaks the protocol — hardware testing WITHOUT any app
                             #   (fast iteration, scriptable regression checks)
```

**Dependency rule:** `composeApp → shared/domain → shared/protocol`; nothing in `shared/*` imports
platform code except through the `Connection` expect/actual interface. Tablet/iPad layouts are a
UI-only concern inside `composeApp` (WindowSizeClass), never a fork of screens.

## 3. Key design decisions

### 3.1 Single connection owner
One `MountSession` object owns the TCP socket, a send mutex (requests serialized like the stock app), and a
reader coroutine dispatching responses/pushes to flows. All screens observe `MountSession.state: StateFlow<MountState>`.
Reconnect is automatic with exponential backoff (1→2→4…30 s cap); on reconnect, session re-runs the
lifecycle handshake (284 poll; re-enable AHRS if astro mode was active — PROTOCOL.md §4).

`MountSession.lastError: CmdResult<Nothing>?` mirrors the most recent `ProtocolError` from `request` or
`send`; cleared on a successful `connect`. Lets the plate-solver (and any other caller) distinguish
"the mount dropped" from "the solver found no match" without re-querying the state flow
(PLAN-CRITICAL-REVIEW §H).

### 3.2 Command layer = generated from one table
A single `CommandTable` maps code → builder/parser pair. PROTOCOL.md is the human mirror of this table;
a unit test asserts every documented command has an entry (docs can't rot silently).

### 3.3 Quirk handling is centralized
The halfSpeed inversion, `-100` sentinel, and AHRS gating live in exactly one place each
(`TrackingController`, `ResponseParser`, `MountSession` respectively) with tests pinning the behavior.

### 3.4 MJPEG preview
Standard multipart JPEG stream parse; rendered via `ImageBitmap`. Decoding happens off the main thread;
preview is best-effort (dropping frames is fine) and never blocks the control path.

### 3.5 Planetarium math
Implement Meeus low-precision solar/lunar + sidereal time conversions in `core/astro` (~200 lines, fully
unit-testable against known values). Star catalog: embedded JSON of ~300 brightest stars (name, RA, Dec,
magnitude) — sufficient to replicate stock alignment UX.

### 3.6 Feature flags
`FeatureFlags` data class loaded from settings; all v2 enhancement hooks default false and their UI is
hidden when disabled (SPEC.md §3).

## 4. Testing strategy

| Layer | Approach |
|---|---|
| protocol | Unit tests: golden request strings vs known-correct frames captured from decompiled logic; parser fuzzing (truncated/garbled payloads must not throw) |
| domain | State-machine tests: alignment flow, tracking transitions, reconnect handshake |
| astro | Known-value tests (e.g., Polaris alt/az at reference location/time; sun rise/set for fixed date) |
| integration | `tools/cli-probe` scripts run against real mount: connect→poll→jog→track→stop in <60 s |
| UI | Manual side-by-side with stock app on hardware (the oracle test) |

## 5. Error handling philosophy

- Every command returns a typed result (`Ok(value)` / `Timeout` / `ProtocolError(msg)`).
- User-facing errors are plain-language ("Mount didn't respond — check Wi-Fi"); raw detail goes to an
  in-app log screen (exportable) since hardware debugging happens in the field at night.

## 6. Security/privacy

No accounts, no analytics, no network access except to the mount's AP subnet. Catalog and settings stored
locally. (The mount's protocol has no authentication — a known firmware limitation, documented but not
solvable client-side.)
