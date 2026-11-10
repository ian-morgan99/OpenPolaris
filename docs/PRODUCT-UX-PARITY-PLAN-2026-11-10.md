# OpenPolaris product and UX parity plan

**Date:** 2026-11-10  
**Status:** committed product plan; implementation proceeds through the companion WBS  
**Product goal:** make OpenPolaris a calm, truthful, night-safe mobile and PC control surface that covers the supported extent of the Astro WebUI without copying its unsafe assumptions or creating a second uncontrolled protocol owner.

## 1. Product promise

OpenPolaris should let a user complete the whole observing job from one coherent app:

1. wake and connect to a Polaris over Bluetooth/Wi-Fi or a known home-LAN endpoint;
2. understand immediately whether the mount, camera, preview, storage and Astro session are ready;
3. level, align, go to a target and start the correct tracking profile;
4. make precise manual adjustments, including deliberately slow motion;
5. frame, focus, capture, solve, guide, dither and review the resulting files;
6. recover from ordinary link, camera and application failures without guessing or double-firing a shutter;
7. use the same concepts on a phone, tablet and PC, with controls resized and rearranged for the device rather than merely stretched.

The product is not complete when a button sends bytes. A capability is complete when the user can see its preconditions, action, positive state transition, result, failure mode and recovery path. Static protocol evidence, simulator evidence, desktop rendering and physical qualification remain separate claims.

## 2. UX principles

### Truth before reassurance

Every command has a visible lifecycle: unavailable, ready, queued, active, confirmed, complete, rejected, failed or unknown. A socket write, HTTP `ok`, return code or stale cached value is never labelled as a physical result. Capture success requires the correlated 264 lifecycle, a new non-empty 773 file publication where applicable, and terminal idle.

### One owner for every resource

The shared domain owns the mount socket, camera workload, preview transport, motion lease, capture lifecycle and Astro programme. Compose only renders state and emits typed intents. No pane, coroutine, desktop bridge or Android callback may open a competing control connection or replay an ambiguous physical command.

### Night-safe by construction

The default palette remains dark and red-accented, but contrast, focus rings, text scaling, screen-reader labels, keyboard access and colour-independent state indicators are mandatory. Destructive or physically risky actions use a deliberate arm/hold/confirm step. Emergency stop is always reachable and never hidden behind a tab.

### Capability honesty

The app distinguishes `supported`, `available`, `enabled`, `in progress`, `unqualified`, `blocked` and `unknown`. An unsupported Astro feature is still discoverable as an explanation, not rendered as a dead-looking button. Firmware/app/model identities are part of the capability record; a familiar version string alone never enables a motor or capture path.

### Progressive disclosure, not feature disappearance

The first screen is a mission dashboard. Common actions stay one tap away. Advanced programmes, wire diagnostics and interoperability bridges live behind clearly labelled sheets or an advanced drawer. The information architecture must expose the full Astro WebUI surface without forcing a novice through it on every session.

### Reversible, resumable work

Every long operation has pause/stop/cancel semantics, a progress state, a durable session event and a defined recovery result. A reconnect never blindly repeats a shutter, a slew, a rate correction or a programme step whose outcome is unknown.

## 3. Supported product surface

The implementation follows the Astro WebUI parity inventory while keeping native OpenPolaris protocol ownership:

| Area | End-user experience | OpenPolaris product surface |
|---|---|---|
| Connection | Direct AP, Bluetooth wake, PC Wi-Fi bridge, home LAN, reconnect and diagnostics | Connection wizard, endpoint profiles, typed health, one session owner |
| Mission dashboard | Ready-to-observe summary, camera/storage/preview/mount state, stop | Home/mission screen and persistent status strip |
| Mount control | Position, jog, slow motion, go-to, cancel, limits and tracking | Motion dock, GoTo sheet, tracking state machine |
| Camera | Dynamic exposure controls, preview, focus, M/B release and video where qualified | Camera workspace with field-level verification |
| Astro | Enter/leave Astro, tilt compensation, alignment, tracking, solve, target, guide, dither | Guided Astro mission flow plus advanced diagnostics |
| Programmes | Lapse, panorama, HDR, focus stack, PLC/path-lapse, Sun/Holy Grail where qualified | Observation-plan engine with progress and recovery |
| Media | Categories, thumbnails, full-size view, download/export and safe file actions | Gallery and transfer controller |
| System | Site, head settings, Wi-Fi diagnostics, firmware provenance, logs, help | Settings, device health and release/evidence panels |
| Bridges | Alpaca, INDI, LX200 and external camera workflows where applicable | Desktop-first adapters with explicit ownership and capability state |

The WebUI's resident HTTP routes are evidence for user-visible behaviour, not a reason to copy its server into the client. Stable protocol controllers are preferred. A narrow versioned adapter is permitted where the WebUI has a capability with no safe direct equivalent.

## 4. Information architecture

### Mobile and tablet

Use five primary destinations with a persistent emergency stop and status affordance:

1. **Mission** — connection health, target, alignment/tracking state, next safe action and recent result.
2. **Control** — position readout, manual/slow motion dock, tracking, stop and GoTo.
3. **Camera** — preview, exposure, focus, capture, M/B release and capture timeline.
4. **Astro** — guided session steps, solve, target, guiding and observation plan.
5. **Gallery** — media categories, thumbnails and export.

Settings, device information, firmware, network diagnostics and feature evidence are opened from the overflow/drawer. A phone uses a bottom navigation bar and bottom sheets; a tablet uses a two-column master/detail layout when width allows.

### PC

Use a resizable three-region layout:

- left rail: Mission, Control, Camera, Astro, Gallery, Settings;
- centre workspace: the active mission surface or preview;
- right inspector: selected target, capture state, mount/camera telemetry and event log.

Keyboard shortcuts are discoverable and never replace pointer/touch safety. The PC can show more diagnostics and an event timeline, but it must use the same state and command contracts as mobile.

### Shared visual language

Every action surface has the same structure: title and purpose, preconditions, primary action, progress/status, recent evidence and recovery. Unknown values render as `Unknown`, not zero. A stale value shows age. A disabled button explains the gate in one sentence.

## 5. Slow-motion control: product definition

“Slow motion” is not one thing. OpenPolaris must keep these controls separate:

| User concept | Meaning | Existing/target path |
|---|---|---|
| Fast jog | Hold-to-move manual adjustment with a dead-man lease | 513/514/521 and their known `AC 41` path; never used as tracking-rate control |
| Slow manual move | Low-speed manual move, intended for framing and alignment | Candidate SLOW ladder 532/533/534; disabled until exact target contract and stop behaviour are qualified |
| Base tracking | Sidereal/lunar/other verified tracking profile | 531 `state` + explicit `speed` |
| Half speed | Landscape-astro compromise or firmware global mode | 536, with the known inverted payload |
| Fine rate | Signed, bounded per-axis correction around a tracking profile | Separate `RateAdjustController`; not repeated jogs and not another 531 index |

The UX must never call all of these “speed”. Labels should say `Fast jog`, `Slow move`, `Track`, `Half rate` and `Fine correction`.

### 5.1 Physical axes and language

The domain names are physical and stable: M1/pan, M2/tilt and M3/rotation. When M3 has rotated, M1 and M2 must not be described as fixed azimuth/elevation. The UI may show a friendly `Pan`, `Tilt`, `Rotation` label with an expandable physical-axis explanation.

Direction signs are capability data. Until a target firmware/load matrix proves the sign, the UI uses `Direction A/B` in a diagnostic view and does not claim `left/right`, `up/down` or celestial east/west. Once qualified, the selected axis card shows a reversible direction legend and the evidence identity.

### 5.2 Mobile slow-motion interaction

The Control destination contains a **Motion dock**:

- a large two-axis pad for the selected M1/M2 plane;
- a rotation rocker for M3;
- a speed rail with named levels `1 Precision` through `5 Fast` for the qualified SLOW ladder;
- a `Fine correction` entry that opens a sheet rather than sending slider ticks;
- a visible `STOP ALL` button above the pad;
- a small live state line: `Ready`, `Holding M1 +`, `Active`, `Lease lost — stopped`, `Unconfirmed`, or `Unavailable on this firmware`.

Interaction rules:

1. A touch-down on a direction starts a hold only after pointer capture and precondition checks.
2. The client renews a short lease; the domain/server owns the repeat cadence and automatic stop.
3. A touch-up, pointer cancellation, app backgrounding, screen lock, connection loss, mode change, second conflicting direction or emergency stop sends the highest-priority stop path.
4. A tap without a hold never starts continuous movement.
5. Haptic feedback is optional and suppressed when the device reports no confirmed active motion.
6. The pad exposes an axis lock and a diagonal policy. The default is axis-locked for safety; simultaneous axes require an explicit `multi-axis` mode and a simulator/hardware-qualified capability.
7. The user sees the command age and last positive acknowledgement; no spinner is allowed to imply movement.

### 5.3 PC slow-motion interaction

The same Motion dock becomes a larger control panel:

- click-and-hold buttons with pointer capture;
- keyboard mapping shown in a `Shortcuts` popover, disabled while focus is in a text field;
- configurable hold repeat only at the domain boundary, never from UI timers;
- an optional rate/level inspector with exact units only when the capability record supplies them;
- a timeline showing command, acknowledgement, stop, lease expiry and link events.

The PC must not create a second motion owner when a phone is connected. The active owner and a `Take control`/`Read-only` state are visible.

### 5.4 Fine-rate sheet

The sheet is available as a disabled/explanatory surface before qualification and becomes interactive only on an allow-listed capability record. It contains:

- current base profile: Sidereal or Lunar;
- tracking active/confirmed state;
- M1, M2 and M3 signed target fields or sliders;
- unit selector only when the firmware declares calibrated units, preferably arcseconds/second for the user;
- target range, ramp/settle policy and capability provenance;
- `Apply`, `Cancel`, `Stop correction` and `Restore base tracking`;
- a warning when a value is queued but not positively confirmed.

Apply is atomic from the user's perspective: the previous request is superseded, values are bounded, the controller ramps them, and the resulting state is `Active and confirmed` or an explicit fault. Slider movement edits a draft; it does not stream commands.

### 5.5 Slow-motion technical contract

The initial shared contract must be abstract and simulator-backed:

```kotlin
interface RateAdjustController {
    val state: StateFlow<RateAdjustState>
    suspend fun apply(request: RateAdjustRequest): RateCommandResult
    suspend fun stopCorrection(): RateCommandResult
    suspend fun onTrackingStopped()
    suspend fun onDisconnected()
}
```

The request includes a base profile, three signed finite axis targets, a monotonic request version and the exact capability identity. State distinguishes unavailable, available/idle, applying, active/confirmed, stale/lease-expired and faulted.

The host-side planner may use the researched shared SLOW ladder as a dry-run reference:

- M1/pan → 532, M2/tilt → 533, M3/rotation → 534;
- payload shape `key:<0|1>;state:<1|2>;level:<1..5>;` for a running SLOW point;
- state 0 is an explicit stop, never a low-rate hold;
- a requested average between adjacent rungs is time-shared;
- minimum dwell, direction reversal and stop priority are enforced by the scheduler;
- low rates below the first rung require a proven hold/time-share strategy, not a guessed zero.

This remains a planning seam, not permission to send live commands. The alternative `AC 4A <axis> <float>` path is also withheld until dispatcher route, MCU receiver, units, signs, acknowledgement, zero-hold and watchdog behaviour are proven for the exact firmware.

### 5.6 Slow-motion safety gates

Fine control and SLOW must remain unavailable when:

- mount identity, firmware identity or gimbal capability is unknown;
- tracking is not confirmed when fine correction is requested;
- half-speed/custom correction composition is not qualified;
- a GoTo, alignment, programme, capture or another motion owner is active;
- the link/lease/acknowledgement is stale;
- hardware limits are unknown or the requested target exceeds the qualified envelope.

On stop tracking, Astro exit, disconnect, timeout, process shutdown or rejected acknowledgement, the controller issues the qualified stop, clears active state and prevents re-entry until the session is re-established. A no-camera physical bench test precedes camera/plate-solve integration.

## 6. Full Astro workflow

The Astro destination is a guided mission, not a list of unrelated switches:

1. **Connect and inspect** — mount, camera, storage, preview and capability health.
2. **Prepare** — physical kit check, auto-level if required, tilt compensation choice, camera focus mode and safe limits.
3. **Align** — plate solve first; phone compass or manual bearing as explicit alternatives; show accuracy and provenance.
4. **Track** — choose Sidereal/Lunar, half rate only where compatible, confirm tracking state.
5. **Target** — search/catalogue, altitude/sun/filter safety, GoTo, arrival and optional solve-refine.
6. **Focus** — live-view autofocus sweep with step/settle/backlash settings and cancel/recovery.
7. **Guide** — solution freshness/confidence, drift/rotation chart, bounded correction, dither between frames.
8. **Capture** — select ordinary/M/B/stack/programme mode, show exposure/file lifecycle, no false success.
9. **Review** — gallery, full-size frame, metadata, export and session evidence.

The flow always allows an expert to skip ahead, but skipped prerequisites remain visible and explain what is unavailable.

## 7. Delivery and qualification policy

Each WBS item ships in three layers where applicable:

1. pure contract/model and deterministic tests;
2. simulator/replay and UI state coverage;
3. platform packaging and supervised physical qualification.

“Implemented” means the first two. “Hardware-qualified” means the third with exact camera, mount, firmware, app build, evidence files and rollback/recovery result. Release notes and UI badges use those terms precisely.

## 8. Definition of a fully functioning app

OpenPolaris reaches the product goal when:

- mobile and PC builds share the same typed controllers and state contracts;
- connection, preview, camera, mount, Astro, programmes, gallery, settings and bridges are discoverable and coherent;
- every advertised action has deterministic tests, error UX and recovery;
- all motion controls are lease/dead-man protected and slow/fine rate remains disabled where unqualified;
- ordinary and Bulb capture complete through lifecycle plus file evidence;
- Astro alignment, tracking, target, focus, guiding and capture are qualified on the named device matrix;
- the app can be used in darkness with touch, keyboard, accessibility and no-network/poor-network explanations;
- installers, release provenance, diagnostics and rollback are reproducible;
- unresolved hardware or firmware capability is visible as `Unknown`/`Blocked`, never hidden behind a green button.

The companion [work breakdown structure](WORK-BREAKDOWN-STRUCTURE-2026-11-10.md) turns this plan into ordered, checkable work.
