# OpenPolaris UX parity work breakdown structure

**Baseline:** `main` at the commit containing the companion product plan  
**Status key:** `PLANNED`, `ACTIVE`, `DONE`, `BLOCKED`, `QUALIFICATION`  
**Rule:** no item is marked done by source inspection alone when its outcome depends on a real Polaris, camera, firmware or physical stop.

## Work-package map

| ID | Work package | Primary outcome | Depends on | Status |
|---|---|---|---|---|
| WBS-00 | Product contract and evidence ledger | Plan, capability vocabulary, UX invariants, provenance rules | — | DONE |
| WBS-01 | Shared app shell and responsive mission UX | Mobile/tablet/PC information architecture and status chrome | WBS-00 | ACTIVE |
| WBS-02 | Connection, session and health | Direct AP, BT wake, PC bridge, LAN, reconnect and truthful health | WBS-00 | ACTIVE |
| WBS-03 | Motion ownership and slow-motion control | Fast jog, SLOW manual motion, fine-rate seam, safety arbitration | WBS-00 | ACTIVE |
| WBS-04 | Camera and capture workspace | Dynamic camera controls, preview, M/B capture and file lifecycle | WBS-02 | ACTIVE |
| WBS-05 | Astro mission workflow | Prepare, align, track, target, solve, guide and recover | WBS-01,WBS-02,WBS-03,WBS-04 | ACTIVE |
| WBS-06 | Observation programmes | Lapse, panorama, HDR, focus stack, PLC/path-lapse, solar workflows | WBS-04,WBS-05 | ACTIVE |
| WBS-07 | Gallery and export | Safe media browse, thumbnails, full files and transfer | WBS-04 | ACTIVE |
| WBS-08 | System, diagnostics and interoperability | Settings, logs, firmware provenance, Alpaca/INDI/LX200 adapters | WBS-02,WBS-05 | ACTIVE |
| WBS-09 | Accessibility, night UX and polish | Keyboard/screen-reader/touch/contrast/performance quality | WBS-01..08 | ACTIVE |
| WBS-10 | Platform packaging and release | Android/desktop artifacts, install/upgrade/rollback and support docs | WBS-01..09 | ACTIVE |
| WBS-11 | Physical qualification and release gate | Named-device evidence and honest capability matrix | WBS-02..10 | QUALIFICATION |

## WBS-00 — Product contract and evidence ledger — DONE

### WBS-00.1 — Freeze product principles

- Deliverable: `docs/PRODUCT-UX-PARITY-PLAN-2026-11-10.md`.
- Exit: truth-before-reassurance, one-owner, night-safe, capability honesty and evidence boundaries are explicit.

### WBS-00.2 — Freeze parity inventory

- Deliverable: mapping of Astro WebUI areas to OpenPolaris surfaces, controllers, platforms and gates.
- Exit: each visible WebUI capability has an OpenPolaris destination or a documented reason to remain external/blocked.

### WBS-00.3 — Freeze evidence vocabulary

- Deliverable: consistent `source`, `simulator`, `hardware`, `supported`, `available`, `unqualified`, `blocked`, `unknown` terms in docs and UI.
- Exit: no release or UI acceptance test uses `PASS` for a capability without its required evidence class.

## WBS-01 — Shared app shell and responsive mission UX

### WBS-01.1 — Navigation model — P0

- Add typed destinations for Mission, Control, Camera, Astro, Gallery and Settings.
- Keep current callout mode working while introducing an adaptive shell behind tests.
- Exit: the same destinations are reachable by touch, mouse, keyboard and accessibility semantics.

### WBS-01.2 — Mission dashboard — P0

- Add a compact readiness summary: connection, mount, camera, preview, storage, alignment, tracking and active owner.
- Each warning links to the next safe action.
- Exit: a fresh user can explain why an action is disabled without reading logs.

### WBS-01.3 — Responsive layout — P1

- Mobile bottom navigation/sheets; tablet master-detail; PC rail/workspace/inspector.
- Preserve dark adaptation and large touch targets.
- Exit: screenshot/semantics tests at phone, tablet and desktop widths; no clipped primary actions.

## WBS-02 — Connection, session and health

### WBS-02.1 — Typed endpoint profiles — P0

- Model Direct AP, home-LAN, PC bridge and demo endpoints with control/preview ports.
- Show endpoint, route, age, link phase and last failure.
- Exit: no pane invents host/port state; endpoint health is simulator-tested.

- **2026-11-10 - WBS-02.1 progress:** added a shared read-only connection-health
  section to the inline Mission workspace and Connection pane. It shows
  endpoint kind/identity, the distinct control and preview addresses, route
  phase age and last failure, with a safe action back to the Connection owner.
  Phase-age calculation is clock-safe and covered by the shared domain test.

### WBS-02.2 — Single session owner — P0

- Audit all controllers for connection ownership, reconnect and teardown ordering.
- Make motion/camera/preview/programme ownership visible to the UI.
- Exit: connect/drop/reconnect tests prove no orphan reader, duplicate poller or competing socket.

### WBS-02.3 — Bluetooth and Wi-Fi bridge UX — P1

- Present wake requested, GATT opened, Wi-Fi route ready, control reachable, preview reachable and teardown separately.
- Keep the home LAN default route isolated from the Polaris route.
- Exit: fake bridge tests plus read-only host diagnostics; physical network changes remain operator-gated.

## WBS-03 — Motion ownership and slow-motion control

### WBS-03.1 — Typed motion arbitration — P0

- Add one `MotionSupervisor` that arbitrates fast jog, SLOW, GoTo, alignment, tracking corrections and programme motion.
- Define owner, preemption, stop priority and `OutcomeUnknown` states.
- Exit: simulator proves only one owner drives at a time; emergency stop wins over queued work.

### WBS-03.1  Typed motion arbitration  P0  ACTIVE (SLOW owner wired)

- **2026-11-10  WBS-03.1 progress:** SlowMotionController now accepts the
  shared MotionSupervisor. Demo SLOW acquires MotionOwner.SLOW_MOVE, is
  blocked by an active GoTo/fast-jog/programme owner, routes emergency/release/
  disconnect stops through the shared stop boundary, and uses lease-scoped
  recovery so it cannot clear another owner. Live owners still need to migrate
  before this package is complete.

- **2026-11-10 - WBS-03.1 progress (GoTo owner wired):** the arrival-aware 519/517 GoTo path now acquires the shared `MotionOwner.GOTO` lease, reports contention or a blocked recovery state, routes cancel and disconnect through the shared stop boundary, and releases the lease only after the stop result is observed.

### WBS-03.2 — Fast jog dead-man contract — P0

- Preserve 513/514/521 semantics separately from slow/fine rate.
- Implement pointer capture, lease renewal, release/cancel/background stop and explicit active feedback.
- Exit: UI and controller tests cover touch cancellation, keyboard release, disconnect and stop ordering.

- **2026-11-10 - WBS-03.1/03.2 progress:** FastJogController now accepts
  the same shared MotionSupervisor. Cross-owner acquisition, owner-specific
  stop delegation and lease-scoped recovery are covered by deterministic tests.
  The live 513-516 rocker now uses a hold-to-move/release-to-stop interaction,
  sends source-aligned time:Nms beats and state:0 stop frames through sendOnly,
  and the simulator proves shared-owner acquisition and release. A physical
  stop observation and named-firmware evidence are still required before this
  package is marked complete.

### WBS-03.3 — Slow-motion abstract model — P0 — DONE (contract only)

- Add capability identity, physical axis names, signed targets, bounds, request versions and state flow.
- Start with no live adapter and a deterministic simulator.
- Exit: malformed/NaN/out-of-range/stale requests fail closed; stop/disconnect clears state.

### WBS-03.4 — SLOW planner and scheduler — P1

- Port/reimplement the pure planning concepts: shared level, adjacent rung duty, minimum dwell, reversal and explicit stop.
- Keep values as calibration data; do not expose degrees/sec until measured.
- Exit: property tests cover all three axes, zero hold, duty extremes, dwell and stop priority.

### WBS-03.5 — SLOW wire seam — P1, blocked by firmware evidence

- Add an adapter for exact 532/533/534 payloads only after target firmware route, signs, acknowledgement, stop and watchdog are proven.
- Keep alternative `AC 4A` route isolated until its receiver/units are proven.
- Exit: exact app/gimbal hashes, fake-head fault suite, no-camera physical stop/reconnect/rate matrix.

- **2026-11-10 — WBS-03.5 ACTIVE (transport-neutral owner):** added a SLOW controller seam with exclusive ownership, separate dead-man renewal, scheduler ticks, explicit release/emergency/disconnect stops, and terminal unknown/recovery states. It sends no wire frame; exact firmware mapping remains blocked.
- **2026-11-10 — WBS-03.5/03.6 ACTIVE (owner-driven Demo SLOW):** moved the demo scheduler loop out of the Compose pane and into the shared SlowMotionController owner. Demo pointer/key holds now acquire a lease, renew it separately from scheduler ticks, record logical commands through a transport-neutral adapter, and release through the explicit stop path; disconnect cancels the owner and uses the disconnect stop boundary. Live 532/533/534 and AC 4A transport remains hard-gated.
- **2026-11-10 — WBS-03.5 ACTIVE (source-aligned host-only wire seam):** added a pure formatter for the Benro Connect 532/533/534 SLOW family. It maps M1/M2/M3 to the exact `key;state;level` payload, uses the observed type-3 request envelope, retains the active point for explicit state-0 STOP, and rejects a STOP without a retained point. It produces bytes for replay/fake-head tests only; no MountSession or live transport calls it, and AC 4A remains isolated.
- **2026-11-10 — WBS-03.5 ACTIVE (host-only replay/fault suite):** added a test-local fake head that replays planner/scheduler output through the formatter for all three axes, expires stale active points at the host lease boundary, and rejects malformed frames and repeated STOP. This validates the host seam only; it is not a firmware watchdog or physical-stop result.


### WBS-03.6 — Motion UX — P1 — ACTIVE (disabled surface)

- Add mobile Motion dock, PC control panel, speed ladder, axis legend, owner state and emergency stop.
- Add disabled Fine correction sheet before live qualification.
- Exit: UI can be reviewed end-to-end without sending a live motor command; live controls appear only on an allow-list.

- **2026-11-10 - WBS-03.6 progress:** exposed the shared MotionSupervisor state
  to the responsive Motion workspace. Demo SLOW now shows idle, owned-owner or
  fault-blocked state separately from its logical scheduler trace; the state is
  read-only and does not open a live motor path.

- **2026-11-10 - WBS-03.5/03.6 ACTIVE (named SLOW capability allow-list):** paired the UI's hard live-transport gate with an immutable capability record requiring exact firmware/gimbal/source-app identity, route and unit, signs, measured envelope, acknowledgement, watchdog, physical stop, disconnect recovery and limit evidence. The default record is unqualified; the Motion surface now lists the missing gates and remains live-disabled.
- **2026-11-10 - WBS-03.1/03.6 ACTIVE (common emergency stop):** replaced the demo-only STOP ALL action with a shared Motion dock stop path. It delegates to the active controller's lease-scoped emergency boundary for FAST_JOG and SLOW_MOVE, uses the supervisor boundary for GoTo/alignment, cancels local motion jobs after the stop result, and keeps unknown or faulted outcomes visible instead of clearing them to idle. Deterministic FastJog and ViewModel tests passed; physical stop observation remains required.
- **2026-11-10 - WBS-03.6/09.1 ACTIVE (focus-loss dead-man hardening):** the shared mobile/desktop hold control now releases the motion owner when keyboard focus is lost, and guards pointer/key cleanup against duplicate release callbacks. The source contract test covers the focus-loss seam; hardware stop qualification remains separate.
- **2026-11-10 - WBS-03.6/09.1 ACTIVE (global motion safety chrome):** the responsive status strip now surfaces the active motion owner and exposes STOP ALL without requiring navigation to the Control or Slew workspace. Faulted stop state is rendered as an error instead of disappearing. The shell contract and Compose JVM tests passed.

### WBS-03.7 — Fine-rate Astro integration — P2, blocked by WBS-03.5

- Apply bounded signed corrections around verified base tracking.
- Add confidence/freshness/rotation gates from plate solve and a watchdog.
- Exit: drift loop cannot command a rate on stale/low-confidence/rotated solutions and normal capture remains unchanged.

## WBS-04 — Camera and capture workspace

### WBS-04.1 — Camera capability registry — P0

- Render dynamic options, readback age, write verification and model/firmware qualification.
- Unsupported fields explain why rather than displaying broken controls.

### WBS-04.2 — Preview workspace — P1

- Separate control port health from 8080 MJPEG health.
- Add frame age, empty-stream terminal state, overlays, histogram/focus-peaking hooks and fullscreen.

### WBS-04.3 — Source-aligned M/B capture — P0

- Keep ordinary and Bulb paths distinct; mode-read before dispatch; no blind subtype retry.
- Show queued/exposing/writing/published/complete/failed/unknown states.
- Exit: deterministic replay tests and named-camera lifecycle evidence.

### WBS-04.4 — Focus and video — P2

- Add manual focus hold/jog, video toggle and explicit hardware gates.
- Exit: no focus direction claim without physical observation; no video UI without 263 evidence.

## WBS-05 — Astro mission workflow

### WBS-05.1 — Session prepare — P1

- Guided checklist for physical kit, camera, storage, level, tilt compensation, limits and focus mode.

- **2026-11-10 - WBS-05.1/08.2 ACTIVE (Astro WebUI session status parity):** extended the read-only /api/astro model and Gallery readout to preserve source session/request fields for latitude, longitude, track request, rate index, half-speed mode and optional alignment heading. Coordinate, heading and rate validation remains fail-closed; mount telemetry is still identified as authoritative, and no Astro WebUI write route is opened.

- **2026-11-10 - WBS-05.1/08.2 ACTIVE (Astro WebUI status polling):** the visible Mission/Astro workspace now owns a read-only /api/astro poller at the source WebUI cadence of two seconds. Manual refresh and polling share a mutex, the poller stops with the Compose surface and disconnect, and status fields remain labelled as session/request evidence rather than physical tracking or alignment proof.

- **2026-11-10 - WBS-05.3/08.2 ACTIVE (Astro WebUI session-control seam):** added source-aligned enter, leave and sidereal/lunar track requests with the source half-rate form field, dedicated preview-port routing, positive acknowledgement parsing, status readback and a default-off feature gate. The Mission surface exposes the controls with explicit qualification text; no write is enabled by the default build.

- **2026-11-10 - WBS-05.2/08.2 ACTIVE (Astro WebUI solve evidence):** added a bounded read-only /api/astro/solve client and Mission readout for the source target and last-frame files. The parser preserves solved/not-solved metadata, coordinates, rotation, drift and quality fields while rejecting unsafe shapes/ranges; two-second visible-surface polling is lifecycle-scoped. This is solve evidence only and cannot mark mount alignment or tracking complete.

- **2026-11-10 - WBS-05.1/08.2 ACTIVE (Astro WebUI run-state):** added a bounded read-only /api/state client and Mission readout for source status, elapsed time, alignment/tracking indicators, solution summary, log and altitude/azimuth fields. Automatic two-second polling uses /api/state only; /api/state?mount=1 is explicit-action-only and visibly labels blocked reads. The parser rejects malformed shapes, unsafe ranges, contradictory mount flags and oversized logs.

### WBS-05.2 — Alignment — P1

- Plate solve, phone compass and manual bearing alternatives with confidence/provenance.
- Preserve star-alignment fallback.

- **2026-11-10 - WBS-03.1/05.2 progress:** discrete 530 alignment-record submissions now acquire the shared ALIGNMENT owner, report contention/blocking, cancel safely on disconnect and release through a cancellation-safe boundary. The record action does not move the mount; live firmware and pointing-model evidence remain separate.

### WBS-05.3 — Target and tracking — P1

- Catalogue search, altitude/sun/filter safety, GoTo, arrival and solve-refine.
- Keep Sidereal/Lunar and ½ speed semantics explicit.

### WBS-05.4 — Guiding and dither — P2

- Frame freshness/confidence gates, drift/rotation chart, bounded correction, dither only between captures.

### WBS-05.5 — Autofocus — P2

- Relative sweep, settle/backlash settings, HFR curve, cancel and restore.

## WBS-06 — Observation programmes

### WBS-06.1 — Common programme engine — P1

- Plan validation, progress, pause/resume/stop, one-shot de-duplication, durable events and recovery.

### WBS-06.2 — Lapse and stacking — P1

- Client-driven lapse first; resident 272 adapter only after file/lifecycle evidence.

### WBS-06.3 — Panorama/HDR/focus stack — P2

- Implement each as a capability-gated plan with explicit camera settings, output set and cancellation.

### WBS-06.4 — PLC/path-lapse/Sun/Holy Grail — P3

- Define safety and time-dependent contracts before exposing controls; no unattended solar or long-exposure operation by default.

## WBS-07 — Gallery and export

### WBS-07.1 — Safe media index — P1

- Typed categories, pagination, stable IDs and thumbnail/full-file states.
- Reject traversal and non-media paths.
- Keep native 770/772/773/774 transfer provenance separate from the optional Astro WebUI HTTP surface.

### WBS-07.2 — Viewer and export — P1

- Full-size view, metadata, download/share/save, transfer progress and retry only when idempotent.
- Astro WebUI parity uses the source-aligned /api/media/list, /api/media/thumb and /api/media/full endpoints; native payload derivation remains a separate qualification task.

## WBS-08 — System, diagnostics and interoperability

### WBS-08.1 — Settings and site — P1

- Persist validated location and user preferences locally with clear scope; distinguish head-persisted values.

- **2026-11-10 — WBS-08.1 ACTIVE:** added a bounded, versioned UserPreferencesStore separate from mount session identity. Desktop injects it into the shared shell so the red/dim choice survives restart; mobile hosts can inject an app-sandbox path. Passwords and mount state remain excluded.

### WBS-08.2 — Health/evidence centre — P1

- Event timeline, raw frame/log export, build identity, firmware identity, capability matrix and support bundle.

- **2026-11-10 — WBS-08.2 ACTIVE:** added a read-only evidence and health centre to Settings. It reports build/route identity, separate control and preview health, observed device/camera/storage state, capture lifecycle/publication evidence, and the gated live-SLOW boundary. Raw-log export and support-bundle packaging remain future work.

### WBS-08.3 — Firmware/network controls — P2

- Keep destructive operations off by default, require manifests/rollback and show exact installed provenance.

### WBS-08.4 — Alpaca/INDI/LX200 — P3

- Desktop-first adapters with ownership arbitration; no hidden second capture/motion path.

## WBS-09 — Accessibility, night UX and polish

### WBS-09.1 — Interaction quality — P1

- Touch target audit, focus order, screen reader text, keyboard shortcuts, reduced motion and text scaling.

- **2026-11-10 — WBS-09.1 ACTIVE:** dead-man Demo SLOW controls now expose an explicit hold/release semantic description while retaining pointer capture, keyboard release and stop ordering.

### WBS-09.2 — Night and field conditions — P1

- Red/dim mode, glove-friendly controls, state not dependent on colour, offline help and battery/network awareness.

### WBS-09.3 — Performance — P2

- Bound recomposition, preview decode, event/log volume, gallery memory and long-session battery use.

## WBS-10 — Platform packaging and release

### WBS-10.1 — Android — P1

- Build/install/permissions, background/foreground session policy, orientation and share/export.

### WBS-10.2 — Linux/Windows/macOS desktop — P1/P2

- Package and launch safely, remote desktop policy, window scaling, keyboard and native file picker.

### WBS-10.3 — Release evidence — P0

- Source SHA, build flags, artifact hashes, tests, known gaps, device matrix and rollback notes.
- Current record: docs/BUILD-EVIDENCE-2026-11-10.md.

## WBS-11 — Physical qualification and release gate

### WBS-11.1 — Device matrix

- Named Polaris firmware and gimbal payloads, K-3 III/K-1 II and any other supported cameras, direct AP and PC bridge.

### WBS-11.2 — Motion gate

- No-camera axis/sign/rate/stop/disconnect/reconnect tests before any camera or plate-solve loop.

### WBS-11.3 — Capture gate

- M, timed B, untimed B, cancellation, RAW/JPEG publication, repeatability and recovery.

### WBS-11.4 — Astro mission gate

- Prepare → align → track → target → focus → guide → capture → gallery, with evidence at each boundary.

### WBS-11.5 — Release decision

- `PASS`, `FAIL`, `UNKNOWN`, `BLOCKED` per capability; no aggregate green result hides an unknown hardware gate.

## Execution order now

The implementation sequence is deliberately incremental:

1. commit the product plan and WBS;
2. establish the abstract rate-control, SLOW planner/scheduler, motion ownership and disabled Motion UX seams;
3. establish typed endpoint health, mission readiness and truthful Bluetooth/bridge route states;
4. enforce camera attachment evidence and single-owner preview lifecycle before expanding camera workflows;
5. continue with dynamic camera parity, Astro mission steps, programmes, gallery and interoperability;
6. qualify live motor/camera behaviour only against named firmware, hardware and physical evidence.

The current implementation slice must not send a new motor frame, alter firmware, alter network configuration or claim hardware support.


## Execution log

- **2026-11-10 — WBS-00 DONE:** product UX parity plan and this WBS committed.
- **2026-11-10 — WBS-03.3 DONE (contract only):** added the shared rate capability identity, physical-axis request model, qualified bounds, context gates, stale/no-retry state and transport seam in the shared domain.
- **2026-11-10 — WBS-03.6 ACTIVE:** added the disabled Fine correction position control and an explicit explanation that the firmware rate/stop contract is not qualified.
- **2026-11-10 — verification:** focused rate-control tests passed; the shared and Compose JVM suites passed before the final stale-context hardening, and the focused suite passed again afterward.
- **Next:** build the pure SLOW planner/scheduler and MotionSupervisor tests. No live motor adapter is enabled.

- **2026-11-10 — WBS-03.4 DONE (pure seam):** added the calibration-driven SLOW planner and clock-driven scheduler with shared levels, torque-preserving zero holds, minimum dwell and one-shot STOP events; no transport adapter is connected.
- **2026-11-10 — WBS-03.1 DONE (contract only):** added the shared MotionSupervisor ownership/lease seam with emergency-stop priority, wrong-lease rejection and fault blocking; no controller is wired to a new live transport.
- **2026-11-10 — WBS-03.6 ACTIVE (disabled UX):** added a Motion destination with physical-axis naming, calibration-only speed ladder, stop/lease semantics and an unavailable emergency-stop state; all controls remain non-operational behind `slowMotionControl=false`.
- **2026-11-10 — verification:** `FeatureFlagsTest`, rate-control, SLOW planner/scheduler and MotionSupervisor tests remain green after the Motion surface changes; full shared/Compose JVM verification is next.
- **2026-11-10 — WBS-02.1 DONE (model only):** added typed endpoint profiles and independent control/preview health phases for Direct AP, home LAN, PC bridge and demo routes; no networking side effects.
- **2026-11-10 — verification:** full `:shared:jvmTest :composeApp:jvmTest` passed, including endpoint validation and existing mobile-layout contracts.
- **2026-11-10 — WBS-01.2 ACTIVE (first slice):** added `MissionReadiness` ordering and actionable ready/attention/blocked/unknown cards, then surfaced the highest-priority next action in the always-visible status chrome.
- **2026-11-10 — verification:** focused mission-readiness tests and Compose JVM tests passed; the summary is read-only and does not alter connection, preview, capture or motion transports.
- **2026-11-10 — WBS-02.2 ACTIVE (first slice):** connected typed endpoint health to AppViewModel connect, demo, preview and disconnect lifecycle points; control reachability, preview reachability and failure evidence remain separate.
- **2026-11-10 — verification:** preview-port and endpoint lifecycle regressions passed, including reset-to-disconnected and no-frame preview remaining unknown.
- **2026-11-10 — WBS-03.2 ACTIVE (contract only):** added a fast-jog dead-man lease seam for duration-style 513–516 commands with 400 ms expiry, 50 ms repeat pacing, release/disconnect stop, ambiguous-result blocking and explicit recovery; no live adapter is connected.
- **2026-11-10 — verification:** focused FastJogController tests passed for single ownership, repeat pacing, expiry, release ordering, invalid input and no-retry after ambiguity.
- **2026-11-10 — WBS-01.2 ACTIVE (parity complete for current signals):** extended the readiness model and summary to cover connection, mount, camera, preview, storage, alignment, tracking, capture and motion ownership with separate unknown/blocked/attention states.
- **2026-11-10 — verification:** focused MissionReadiness and mobile-layout tests passed after adding storage/alignment boundaries.
- **2026-11-10 — WBS-01.1/01.3 ACTIVE (transitional shell):** added a touch/mouse/keyboard-accessible Mission details sheet from the persistent status strip, with endpoint identity, phase and all readiness rows; existing responsive callout navigation remains intact while the adaptive shell is expanded.
- **2026-11-10 — verification:** Compose JVM suite passed after the Mission details integration.
- **2026-11-10 - WBS-01.1/01.3 ACTIVE (typed destinations):** added a shared
  primary destination model and responsive Mission/Control/Camera/Astro/Gallery
  navigation. Phone layouts use a compact destination bar with an advanced
  overflow; wide layouts use a primary rail plus the existing advanced rail.
  The original callout routes remain available and the navigation is still a
  transitional shell rather than the final inline workspace.
- **2026-11-10 — WBS-02.3 ACTIVE (truthful route UX):** Connection now shows endpoint identity, control/preview service state, phase and last failure; Bluetooth wake and PC bridge phases are represented separately, and the bridge callback now returns a verified Boolean so a false route result cannot claim readiness.
- **2026-11-10 — verification:** focused preview/bridge tests passed, including false bridge result → FAILED endpoint evidence.
- **2026-11-10 — WBS-04.1 ACTIVE (camera safety slice):** camera setting writes, single M/B capture and intervalometer admission now stop unless the mount's 286 snapshot positively reports an attached camera; explicit absence and unknown status are both non-optimistic, and the existing correlated 264/773 completion path is unchanged.
- **2026-11-10 — verification:** Compose JVM tests passed before and after the camera-admission regression coverage; tests prove no 264 shutter command is emitted for unknown status, explicit absence, M/B, or sequence admission.
- **2026-11-10 — WBS-02.3 ACTIVE (preview ownership):** Preview now has one explicit workflow owner and one frame collector; repeated entry is idempotent, leaving releases the stream, and capture suspension only restores a preview that the user actually owned.
- **2026-11-10 — verification:** preview-port tests passed with repeated-entry and leave/re-entry assertions; no new endpoint or hardware side effect was introduced.
- **2026-11-10 — WBS-04.1 ACTIVE (camera readback UX):** Camera workspace now exposes the age of the last explicit dynamic parameter readback, keeps unknown/unread state distinct, and continues to leave unqualified fields locked.
- **2026-11-10 — WBS-04.3 ACTIVE (mode-aware release UX):** Shared shutter-label classification now distinguishes M/manual, B/bulb, timed/other and unknown; the UI only offers Bulb capture after positive B readback while normal release remains available for attached cameras.
- **2026-11-10 — WBS-04.4 ACTIVE (focus/video discovery surface):** Added visible Near/Far focus and Start/Stop video surfaces, with explicit per-capability gates, camera-attachment admission and still/video conflict protection; source-derived controls remain disabled until named-camera qualification.
- **2026-11-10 — verification:** shared and Compose JVM suites passed after the readback state, mode-aware release and focus/video gate changes; CameraReleaseMode has deterministic M/B/unknown coverage.
- **2026-11-10 — verification:** AppViewModel regression coverage proves unqualified focus/video controls emit no 311/263 wire commands and leave the user with a read-only explanation.
- **2026-11-10 — WBS-07 ACTIVE (first slice):** added a reachable Gallery surface from the persistent status chrome, showing the latest file only after correlated 264 lifecycle + non-empty 773 publication evidence, plus the existing read-only SD-card file browser; camera capture also exposes the confirmed publication path and size.
- **2026-11-10 — verification:** full `:shared:jvmTest :composeApp:jvmTest` passed after the Gallery/publication increment; uncorrelated file events remain non-published and no new destructive file operation was enabled.
- **2026-11-10 — WBS-04.3/WBS-07 ACTIVE (bounded capture evidence):** added a capped ordered timeline for requested, camera-active, file-published, confirmed and unknown/failed outcomes; Camera and Gallery render the same evidence trail, while disconnect and explicit test reset clear stale session evidence.
- **2026-11-10 — verification:** focused capture lifecycle tests and the full shared/Compose JVM suites passed; the timeline only marks publication after the existing correlated 264 + non-empty 773 gate.
- **2026-11-10 — WBS-03.6 ACTIVE (SLOW safety hardening):** the Motion surface now has a second hard-coded transport-qualification gate, so a runtime feature-flag override cannot imply that 532/533/534 or AC 4A controls are live; the UI explicitly distinguishes host planner/scheduler readiness from firmware-bound qualification.
- **2026-11-10 — WBS-03.6 ACTIVE (motion dock parity):** expanded the disabled Motion dock to match the planned mobile/PC interaction model—physical-axis selection, A/B direction controls, visible STOP ALL, named speed rail, rotation rocker and fine-correction gate—while retaining the hard-coded no-transport gate and shared-owner semantics.
- **2026-11-10 — verification:** SLOW safety and responsive-layout source contracts passed; no live motor command path was added.
- **2026-11-10 — WBS-09.1 ACTIVE (interaction hooks):** persistent Mission details and Gallery actions now have stable automation tags for keyboard, accessibility and desktop/mobile UI harnesses; existing callout and camera control labels remain covered.
- **2026-11-10 — WBS-01.2 ACTIVE (actionable readiness):** Mission readiness warnings now expose safe next-action buttons; connection, camera, preview, Slew/motion and Gallery/storage routes are mapped in the shell without issuing hardware commands from the dashboard.
- **2026-11-10 — WBS-01.3 ACTIVE (guided Astro mission):** added a shared ordered Connect → Prepare → Align → Track → Target → Focus → Guide → Capture → Review model; the dashboard now shows complete/current/blocked/pending/optional states and routes only to existing owners, while target selection is tracked explicitly after a validated GoTo or confident solve.
- **2026-11-10 — WBS-04.2 ACTIVE (presentation parity):** Preview offers local framing-grid and centre-mark overlays plus bounded histogram and focus-peaking analysis over the same decoded frame; all analysis is local, cancellable and has no camera or mount side effects.
- **2026-11-10 — WBS-07.1 ACTIVE (media safety contract):** added shared media classification and a mount-local path policy rejecting traversal, URL-like paths, separators and query fragments before future thumbnail/full-file transfer adapters can consume them; Gallery labels known image/video files without enabling transfer.
- **2026-11-10 — WBS-07.2 ACTIVE (pure transfer seam):** added a byte-counter-only media transfer state machine for future thumbnail/full-file adapters, with request tokens, stale-event rejection, size caps, explicit retry only for failed idempotent requests, and `OutcomeUnknown` with no automatic retry; it performs no I/O.
- **2026-11-10 — verification:** media transfer controller tests passed for completion, stale handles, size overflow, unknown outcomes and explicit retry policy.
- **2026-11-10 — verification:** shared `MediaPolicyTest` passed for image/video/unknown classification and safe/rejected path cases.
- **2026-11-10 — WBS-07.2 ACTIVE (explicit export boundary):** added a platform-neutral save/share controller that accepts only a completed, byte-count-matching transfer and an explicit destination; unsafe mount paths, unsafe filenames, incomplete/ambiguous transfers and sink failures remain fail-closed with no automatic retry.
- **2026-11-10 — verification:** Preview overlay contract and full Compose JVM compilation passed; the preview transport ownership/port contract remains unchanged.
- **2026-11-10 — verification:** accessibility and responsive-layout contracts passed after adding the readiness callback path; action routing remains navigation-only.
- **2026-11-10 — WBS-10 ACTIVE (build verification):** Android debug Kotlin and desktop JVM targets compile from the same shared UI/domain code; no platform-specific capability divergence was introduced.
- **2026-11-10 — WBS-07.1 ACTIVE (read-only media navigation):** Gallery now drives the evidenced 770 `type/page` request, exposes the confirmed `All` and `Normal` categories, renders page/count state and only enables next/previous navigation when the response count supports it; disconnect clears stale file-list state.
- **2026-11-10 — verification:** the file-list request payload and parser are covered by deterministic AppViewModel tests; delete/protect/format remain disabled by their existing feature gates.
- **2026-11-10 — WBS-06.1 ACTIVE (pure programme planning):** added transport-neutral planners for lapse, HDR brackets, focus stacks and serpentine panoramas; each plan validates bounds and exposes ordered delays/metadata without issuing camera or motion commands.
- **2026-11-10 — verification:** observation-programme planner tests passed for deterministic ordering, interval/pre-delay semantics, endpoint interpolation, HDR base exposure retention and invalid-plan rejection.
- **2026-11-10 — WBS-06.2 ACTIVE (owner-aware execution seam):** added a transport-neutral programme executor with one owner lease, pause/resume at step boundaries, stop-after-in-flight semantics, rejection handling and terminal no-retry outcome-unknown handling.
- **2026-11-10 — verification:** executor tests passed for ordered confirmed steps, owner release/reacquisition, rejected steps, ambiguity blocking, pause/resume and stop during an in-flight step.
- **2026-11-10 — WBS-06.3 ACTIVE (read-only plan preview):** exposed the shared lapse/HDR/focus-stack/panorama planners in the common Camera surface with accessible plan selection and bounded step detail; previewing performs no camera or motion action.
- **2026-11-10 — verification:** Compose preview contract and JVM compilation passed; the preview remains separate from the live intervalometer and from the future evidence-backed executor bridge.
- **2026-11-10 — WBS-06.4 ACTIVE (shared capture evidence):** added a transport-neutral lifecycle/publication gate and routed AppViewModel single-shot, Bulb and intervalometer correlation through it; stale, idle-only, empty-file, duplicate-file and terminal unknown states remain fail-closed.
- **2026-11-10 — verification:** CaptureEvidenceGate and AppViewModel capture-phase regression suites passed; the existing 264/773 evidence requirement and no-retry behaviour remain intact.
- **2026-11-10 — WBS-06.5 ACTIVE (programme capture bridge):** added the executor-facing adapter that triggers one step and advances only when the shared gate returns the matching confirmed publication; rejected, ambiguous, missing and mismatched evidence remain terminal.
- **2026-11-10 — verification:** bridge tests passed for positive correlation, missing evidence, trigger rejection and mismatched publication; no new wire command or automatic retry was introduced.
- **2026-11-10 — WBS-06.6 ACTIVE (qualified lapse execution):** wired the common mobile/PC Camera surface to execute only fixed-exposure lapse plans through the single programme owner, existing camera controller and shared 264/773 evidence gate; HDR, focus-stack and panorama remain preview-only.
- **2026-11-10 — verification:** deterministic AppViewModel tests prove two lapse steps wait for correlated files, never duplicate a shutter, and reject HDR before any 264 command; UI contract coverage includes Run/Pause/Resume/Stop semantics.
- **2026-11-10 — WBS-04.2 ACTIVE (pixel-analysis presentation):** added a bounded luminance histogram and sampled local-edge focus-peaking map, with cancellation and a four-million-pixel source cap; analysis never enables autofocus or claims physical focus confirmation.
- **2026-11-10 — WBS-04.2 ACTIVE (preview health):** added an explicit empty-stream terminal state after a bounded wait, preserving the distinction between an open preview endpoint with no frames and a transport error.
- **2026-11-10 — WBS-04.2 ACTIVE (frame freshness):** exposed the receipt age of the displayed frame with a bounded UI clock and session-safe reset; stale preview is visible without being mistaken for camera or mount state.
- **2026-11-10 — verification:** preview pixel-analysis tests cover uniform frames, real contrast edges, histogram bounds and large-frame sampling; Compose preview contracts pass with the controls wrapped for narrow mobile layouts.
- **2026-11-10 — verification:** PreviewController coverage proves an open no-frame endpoint becomes `Empty`, while a later frame recovers it to `Streaming`; the UI exposes the terminal reason without issuing a camera command.
- **2026-11-10 — verification:** preview contract coverage includes the frame-age label; Android and desktop compile from the same preview state path.
- **2026-11-10 — WBS-05.1 ACTIVE (prepare evidence):** added a shared read-only Astro preparation checklist for connection, camera, storage, level, tilt compensation and mount limits; unknown values remain unknown, disabled limits are optional with a manual-clearance warning, and no check issues a physical command.
- **2026-11-10 — verification:** checklist tests cover positive evidence, disconnected pending states, unknown readbacks and optional limits; the Mission dashboard renders the same checklist on mobile and desktop.
- **2026-11-10 — WBS-03.6 ACTIVE (demo interaction):** added a simulator-only SLOW interaction path using the shared planner/scheduler; Demo mode can exercise axis, direction, speed and STOP semantics while live 532/533/534/AC 4A transport remains unavailable.
- **2026-11-10 — verification:** SlowMotionControllerTest covers exclusive ownership, separate renewal, explicit stop and no-retry outcomes; SlowMotionDemoControllerTest retains pure logical planner validation, and the Motion UI labels Demo mode explicitly.
- **2026-11-10 — WBS-03.6 ACTIVE (dead-man UX):** Demo direction and rotation controls now start on pointer/key press and stop on release/key-up, retain an explicit STOP ALL action, and keep the physical transport gate closed.
- **2026-11-10 — verification:** Demo scheduler ticks use the shared 75 ms/250 ms logical timing model and expose transition counts; controller tests cover a duty-cycle transition without changing axis ownership.
- **2026-11-10 — WBS-03.6 ACTIVE (trace UX):** Demo mode now renders the latest bounded logical scheduler transitions with axis, STOP/SLOW kind, key, state and calibration level, making the planner behavior inspectable without exposing a wire command.
- **2026-11-10 — WBS-05.2 ACTIVE (alignment UX):** added a bounded named-star chooser and a last-plate-solve identity action to the shared Slew/Alignment surface; selecting an identity never slews, and Record star remains an explicit separate action.
- **2026-11-10 — verification:** `GotoAlignmentSelectionContractTest` covers the visible chooser, plate-solve identity path and explicit centre-then-record wording.
- **2026-11-10 — WBS-09.2 ACTIVE (night UX):** added a reversible dim-red palette and tagged Bright/Dim toggle to the shared phone and desktop status strips; it changes presentation only and persists no hardware or network state.

- **2026-11-10 — WBS-07.1 ACTIVE (gallery category UX):** added horizontally scrollable, touch/mouse-accessible category filters for Photos, Astro, HDR, Panorama, Focus stack, Solar and Timelapse, with counts and a bounded 40-item render window. Filter state is presentation-only and clears on disconnect.
- **2026-11-10 — verification:** AstroMediaPolicy label coverage and AppViewModel category-selection tests passed; the filter never changes the read-only WebUI endpoint or native protocol path.
- **2026-11-10 — WBS-07.1/07.2 ACTIVE (Astro WebUI media slice):** added a bounded, source-aligned read-only client for the resident Astro WebUI list, thumbnail and full-JPEG endpoints. The parser enforces the observed category/path policy, host/path validation and response caps; Gallery exposes explicit Refresh, Thumb and Full actions without enabling native file-transfer writes or deletion. Native 772/773/774/781/782 payloads remain blocked pending corpus replay and hardware evidence.
- **2026-11-10 — verification:** AstroWebUiMediaTest, AppViewModelAstroWebUiMediaTest, shared JVM tests, Compose JVM tests, desktop JVM compilation and Android debug compilation passed; no WebUI request is made without an installed session.
- **2026-11-10 — WBS-10.1/10.2 verification:** rebuilt the Linux desktop app image from a clean checkout and verified embedded source provenance; Android debug Kotlin, release-path compilation and desktop JVM compilation remain green. A local signed Android release could not be rebuilt because the ignored release keystore is absent; the repository CI release path still requires its protected signing secrets. Windows/macOS installers remain host-specific qualification tasks.
- **2026-11-10 — verification:** :shared:jvmTest, :composeApp:jvmTest, :composeApp:compileKotlinJvm and :androidApp:compileDebugKotlinAndroid passed after the owner-driven Demo SLOW change.

- **2026-11-10 — WBS-07.2 ACTIVE (Astro WebUI export):** added a platform-neutral full-file save/share controller and integrated it into the Gallery. Desktop uses a native Save dialog; Android uses SAF CreateDocument and a cache-only FileProvider share URI. Unsafe paths, thumbnails, empty bytes, byte mismatches, chooser cancellation and host recreation remain fail-closed. Native 772/773/774/781/782 transfer and deletion remain separate qualification work.
- **2026-11-10 — verification:** AstroMediaExportTest, AppViewModelAstroWebUiMediaTest, desktop JVM compilation and Android debug compilation passed; physical chooser behavior remains host-validation work.
- **2026-11-10 — WBS-05.1/08.2 ACTIVE (Astro WebUI read-only status):** added a bounded source-aligned GET client for `/api/astro`, with forward-compatible JSON parsing, dedicated preview-port routing, explicit session/feature gating, disconnect reset and a Gallery status readout. The status path only reports the WebUI `active` value; it does not enter/leave Astro mode, start tracking or infer completion.
- **2026-11-10 — verification:** AstroWebUiStatusTest and AppViewModelAstroWebUiStatusTest passed; shared and Compose JVM compilation/tests remain green. No live endpoint or hardware command was exercised.
- **2026-11-10 — WBS-05.1 ACTIVE (Astro WebUI site read):** added a bounded source-aligned GET client for `/api/site`, validating `have_pos` and latitude/longitude ranges, plus a Mission readiness readout. It is read-only, preview-port routed, session/feature gated and reset on disconnect; it does not write the saved site or infer alignment.
- **2026-11-10 — verification:** AstroWebUiSiteTest and AppViewModelAstroWebUiSiteTest passed; no live Astro endpoint or hardware command was exercised.
- **2026-11-10 — WBS-06.1/08.2 ACTIVE (Astro WebUI programme status):** added a bounded source-aligned GET client for `/api/prog`, parsing resident timelapse, panorama, HDR, focus and other programme progress including pause, preview and stale states. The Mission readiness sheet exposes Refresh programme and progress without starting, pausing or cancelling a run.
- **2026-11-10 — verification:** AstroWebUiProgrammeTest and AppViewModelAstroWebUiProgrammeTest passed; no programme write route or hardware command was exercised.
- **2026-11-10 — WBS-06.1/08.2 ACTIVE (programme polling parity):** Mission now owns a lifecycle-scoped read-only /api/prog poller matching Astro WebUI cadence: 1 s during a resident run and 4 s while idle. Mutex-serialized reads prevent manual Refresh and polling from racing; leaving the sheet and disconnecting cancel the poller.
- **2026-11-10 — verification:** cadence tests cover running and idle poll intervals; no programme write route or hardware command was exercised.
- **2026-11-10 — WBS-06.1/08.2 ACTIVE (resident programme controls seam):** added source-aligned cancel and panorama pause/resume adapters plus visible Mission actions. The capability is disabled by default; HTTP acknowledgement is followed by readback and never treated as physical completion.
- **2026-11-10 — verification:** AstroWebUiProgrammeControlTest covers exact endpoints, form values, negative acknowledgements and host rejection; no live write route or hardware command was exercised.
- **Next:** qualify the Android/Desktop chooser flows on their hosts, then continue WBS-02/05/08 integration; keep native 772/773/774/781/782 transfer, Astro start routes and live SLOW transport blocked until corpus, firmware and hardware evidence exists.
- **2026-11-10 — WBS-06.3/08.2 ACTIVE (Astro WebUI programme write seam):** added source-aligned timelapse, panorama, HDR and focus validation/preview/start contracts with bounded parameters, exact form bodies, explicit non-committing Preview versus Accepted results, and AppViewModel lifecycle/duplicate-request gates. The new astroWebUiProgrammeStarts flag defaults OFF; /api/prog remains the completion authority.
- **2026-11-10 — verification:** focused AstroWebUiProgrammeStartTest and AppViewModelAstroWebUiProgrammeStartTest passed; no WebUI write route or hardware command was exercised.
- **2026-11-10 - WBS-06.3/08.2 ACTIVE (Astro WebUI composer):** added the responsive Mission composer with lapse arm/confirm, panorama/HDR preview-then-start, focus validation-then-start, visible returned-frame state and default-off programme-start gating.
- **2026-11-10 - verification:** Compose JVM compilation/tests passed, including the composer contract; no live WebUI write route or hardware command was exercised.

- **2026-11-10 - WBS-01.1/01.3 ACTIVE (inline workspaces):** primary
  destinations now render shared inline workspaces on phone and PC. Mission and
  Astro use the readiness/programme surface, Control contains position/jog/SLOW
  surfaces, Camera uses the camera pane and Gallery uses the export-aware
  gallery. A single LazyColumn owns destination scrolling; advanced routes
  remain modal and gated.
- **2026-11-10 - verification:** Compose JVM compilation and UI contract tests
  passed after converting the primary shell from modal-only routing to inline
  destination content.
- **2026-11-10 - WBS-04.3 ACTIVE (Astro WebUI shutter hardening):** M/B dispatch now has an immediate in-flight gate, transport exceptions become explicit failed outcomes, and a Bulb stop can be posted only once until lifecycle/file evidence settles. The UI distinguishes dispatching and queued-stop states; source-aligned M/B payloads and evidence-gated completion remain intact.
- **2026-11-10 - WBS-03.1 ACTIVE (SLOW emergency edge):** fixed the shared SLOW owner so emergency and disconnect stops also settle an armed lease or a controller-faulted lease. Previously STOP ALL could be rejected after a failed SLOW command while MotionSupervisor still owned the mount. Deterministic armed/faulted recovery tests now cover the boundary.
- 2026-11-10 - WBS-03.5 ACTIVE (qualification-gated runtime adapter): added QualifiedSlowMotionTransport. It emits only the source-aligned 532/533/534 frames after an evidence-backed SlowMotionCapabilityRecord.liveReady gate, treats the scheduler's initial idle-axis STOP as an acknowledge-free logical no-op, retains acknowledged active points for explicit STOP and emergency-stop sequencing, and preserves UNKNOWN outcomes without falsely claiming that motion stopped. The default capability record remains unqualified; no physical SLOW transport is enabled.
- 2026-11-10 - WBS-08.2 ACTIVE (support bundle): added a bounded, redacted support snapshot with route/service health, observed device/camera/storage state, capture timeline/publication evidence and the named SLOW capability blockers. Settings now offers an explicit Save support bundle action on Android and desktop. Hostnames, credentials, mount-local paths, raw frames and camera bytes are omitted; raw-log collection remains a separate future qualification task.
- 2026-11-10 - WBS-02.2/08.2 ACTIVE (export lifecycle ownership): disconnect now cancels an in-flight support-bundle platform chooser, resets its controller/state, and exposes the pending Exporting state while the chooser is open. This prevents a save callback from a torn-down session from updating stale UI state.

- **2026-11-10 - WBS-02.2/02.3 ACTIVE (host-operation ownership):** AppViewModel now
  retains explicit jobs for bridge bring-up and Bluetooth wake. Disconnect and
  reconnect cancel those session-owned operations before clearing endpoint
  evidence; cancellation is propagated instead of being rendered as a false
  bridge or wake failure. This prevents stale callbacks from a prior session
  publishing into a new one.
- **2026-11-10 - verification:** AppViewModelSessionLifecycleTest proves an
  in-flight bridge and Bluetooth wake are both cancelled by disconnect and
  their visible progress flags clear. No live network or Bluetooth operation
  was exercised.

- **2026-11-10 - WBS-02.2/04.3 ACTIVE (capture command ownership):** queued
  still, Bulb and Bulb-stop dispatch now have explicit session-owned jobs.
  Disconnect cancels them before clearing capture evidence, preventing a
  delayed UI action from repopulating Requested or sending a shutter after
  the session has ended.
- **2026-11-10 - verification:** AppViewModelCapturePhaseTest proves a queued
  capture is cancelled before any 264 shutter write; the existing correlated
  M/B lifecycle, timeout, sequence and publication tests remain green. No
  live camera was exercised.

- **2026-11-10 - WBS-02.2 ACTIVE (simulator session ownership):** Demo session
  startup now has an explicit lifecycle job and propagates cancellation.
  Disconnect cancels it before shutting down the simulator, preventing a
  queued Demo connect from resurrecting endpoint state or writing a stale
  reconnect marker.
- **2026-11-10 - verification:** AppViewModelSessionMarkerTest proves a queued
  Demo connection is cancelled before marker persistence. No live mount or
  network operation was exercised.

- **2026-11-10 - WBS-02.3 ACTIVE (bridge/wake handoff):** the full bridge
  bring-up now cancels and takes ownership from a prior standalone Bluetooth
  Wake action, so the two host operations cannot publish competing progress or
  terminal state.
- **2026-11-10 - verification:** AppViewModelSessionLifecycleTest covers the
  handoff as well as disconnect cancellation for both operations. No live
  Bluetooth or network operation was exercised.

- **2026-11-10 - WBS-02.2 ACTIVE (cancellation truth):** control connect and
  bridge teardown now propagate coroutine cancellation rather than converting
  session teardown into a user-visible connection failure. The connect path
  cannot report post-connect bootstrap failure for a deliberate disconnect.
- **2026-11-10 - verification:** AppViewModelSessionLifecycleTest covers a
  pending control connect, while the existing reconnect cancellation and
  bridge/wake tests remain green. No live mount or network operation was
  exercised.


- **2026-11-10 - WBS-02.2 ACTIVE (session-marker persistence ownership):**
  successful connect marker writes are now retained as an explicit session job.
  Disconnect cancels the write before teardown, and a failed write can update
  status only while the original session still owns the view model. This
  prevents a delayed old-session write or error from publishing into a
  replacement session.
- **2026-11-10 - verification:** AppViewModelSessionMarkerTest now gates a
  queued marker write, disconnects, then releases the dispatcher; the
  persisted marker remains absent. The full session-marker suite passed. No
  live mount or network operation was exercised.


- **2026-11-10 - WBS-03.6/02.2 ACTIVE (Demo SLOW stop ownership):** Demo
  SLOW release is now a retained job with explicit ordering against the next
  direction. Disconnect cancels queued UI stop work, synchronously settles the
  owner lease through the disconnect boundary, resets the logical state and
  prevents a stale post-disconnect stop status.
- **2026-11-10 - verification:** AppViewModelSessionMarkerTest proves a
  queued Demo SLOW stop cannot publish its completion status after disconnect.
  Live SLOW remains disabled pending firmware, acknowledgement and physical
  qualification evidence.


- **2026-11-10 - WBS-03.1/02.2 ACTIVE (queued emergency-stop status):**
  STOP ALL now captures the owning session before dispatch. A queued stop from
  a disconnected session is discarded rather than inspecting the reset
  supervisor and reporting a false idle/emergency result; disconnect remains
  the synchronous lease-stop boundary.
- **2026-11-10 - verification:** AppViewModelSessionMarkerTest covers a queued
  Demo SLOW STOP ALL followed by disconnect and proves no post-disconnect
  emergency/idle status is published. No live motion command was exercised.


- **2026-11-10 - WBS-03.1/02.2 ACTIVE (queued fast-jog release):** fast-jog
  press-release now retains its lease until the asynchronous stop settles.
  Disconnect can therefore take ownership of a queued release and synchronously
  close the physical-motion boundary; an old-session release cannot use a
  replacement session or publish stale status.
- **2026-11-10 - verification:** AppViewModelFastJogTest proves a queued Demo
  fast-jog release followed by disconnect leaves both the shared supervisor and
  fast-jog controller idle. No live motion command was exercised.


- **2026-11-10 - WBS-03.1/02.2 ACTIVE (queued GoTo cancellation):** disconnect
  now cancels a GoTo job even when it has not acquired its shared lease yet,
  and clears the in-progress UI state unconditionally. The Cancel Slew action
  captures its session at dispatch and suppresses stale terminal status after
  teardown.
- **2026-11-10 - verification:** AppViewModelSessionMarkerTest proves a
  queued GoTo plus queued cancel cannot outlive Demo disconnect or publish
  "Slew cancelled" into the ended session. No live motion command was
  exercised.


- **2026-11-10 - WBS-06.3/08.2 ACTIVE (Astro WebUI write-session ownership):**
  programme start/control actions now capture their owning mount session and
  discard late HTTP results after disconnect, so a replacement session cannot
  inherit stale Accepted/Failed state. Source-aligned write routes remain
  default-off pending named-head qualification.
- **2026-11-10 - WBS-04.3/02.2 ACTIVE (Astro WebUI capture-session ownership):**
  M/B HTTP dispatch and Bulb stop now have explicit lifecycle jobs and session
  guards. Disconnect cancels them and a late queue acknowledgement cannot
  resurrect Requested/Busy/active capture state or an old watchdog.
- **2026-11-10 - verification:** AppViewModelAstroWebUiProgrammeStartTest and
  AppViewModelAstroWebUiCaptureTest cover cancellation-insensitive late results.
  No live Astro WebUI or camera command was exercised.


- **2026-11-10 - WBS-05.1/07.1/08.2 ACTIVE (Astro WebUI read-session
  ownership):** status, solve, run-state, programme, site and media reads now
  capture their owning mount session and discard late HTTP results after
  disconnect or reconnect. Direct no-session requests retain their explicit
  Failed state, while stale work from an ended session cannot repopulate the
  current UI.
- **2026-11-10 - verification:** the focused Astro WebUI status, solve,
  run-state, site, programme and media ViewModel suites passed, including a
  cancellation-insensitive late status regression. No live Astro WebUI or
  camera command was exercised.


- **2026-11-10 - WBS-06.3/08.2 ACTIVE (Astro WebUI session-control
  ownership):** enter, leave and tracking actions now capture their owning
  mount session and discard late HTTP results after disconnect or reconnect.
  Confirmation status reads are therefore only launched for the session that
  received the accepted write.
- **2026-11-10 - verification:** AppViewModelAstroWebUiSessionControlTest
  passed, including a cancellation-insensitive late enter-result regression.
  Write routes remain default-off pending named-head qualification; no live
  Astro WebUI command was exercised.


- **2026-11-10 - WBS-08.1/05.1 ACTIVE (validated observer-site persistence):**
  observer latitude/longitude now live in the versioned user-preferences store,
  restore only as a complete in-range pair, and persist only after both fields
  are valid. The location remains an OpenPolaris user setting; no Astro WebUI
  site-write or mount-side mutation was added.
- **2026-11-10 - verification:** UserPreferencesStore round-trip and
  AppViewModel observer-location tests passed, including partial, non-finite
  and out-of-range rejection. No network, mount or camera operation was
  exercised.


- **2026-11-10 - WBS-01.1/08.1 ACTIVE (desktop ViewModel lifetime):** the
  internally constructed AppViewModel is now remembered by the shared shell,
  preventing desktop recomposition from replacing session, motion, capture or
  preference-callback state. Host-supplied Android ViewModels remain accepted.
- **2026-11-10 - verification:** the focused Compose JVM suite and full
  cross-platform build remain the required checks; no transport or hardware
  behavior changed.


- **2026-11-10 - WBS-08.1/05.1 ACTIVE (Astro WebUI site-write seam):**
  added the source-aligned `POST /api/site` form contract for latitude and
  longitude, strict acknowledgement validation, preview-port routing, and a
  readback confirmation through the existing site reader. The user-facing
  Save site action is explicitly default-off and reports that resident-site
  mutation, persistence and recovery still require qualification. No mount-side
  site was changed.
- **2026-11-10 - verification:** AstroWebUiSiteControlTest,
  AppViewModelAstroWebUiSiteWriteTest and FeatureFlagsTest cover form shape,
  coordinate/host/ack validation, default blocking, enabled readback and
  cancellation-insensitive disconnect ownership. No live Astro WebUI or
  hardware operation was exercised.


- **2026-11-10 - WBS-06.4/08.2 ACTIVE (Astro WebUI solar programme):**
  added the source-aligned `/api/prog/sun` adapter and shared Mission
  composer. Sunrise/sunset, interval and client-clock window are validated
  before transport; preview requires positive `valid=true` and
  `preview=true` evidence, while start adds the explicit `confirm=1`
  guard. The UI remains behind the existing default-off named-head programme
  gate and accepted starts confirm through resident programme status.
- **2026-11-10 - verification:** AstroWebUiSunProgrammeTest and
  AppViewModelAstroWebUiSunTest cover exact form fields, preview/start
  separation, invalid-window/host rejection, default blocking, preview-port
  routing and late-result disconnect ownership. No live Astro WebUI, mount or
  camera operation was exercised.


- **2026-11-10 - WBS-06.4/08.2 ACTIVE (Astro WebUI extended motion programmes):**
  added source-aligned PLC, path-lapse and Holy Grail contracts for keyframe/
  waypoint capture, preview, explicit confirm starts, inferred Holy Grail
  frames, enable/disable and status. The shared Mission composer exposes these
  on Android and desktop with preview-before-write, visible motion/camera
  warnings, and default-off qualification gating. HTTP acceptance is not
  treated as physical completion; resident programme status remains the
  authority after accepted starts.
- **2026-11-10 - verification:** AstroWebUiMotionProgrammesTest and
  AppViewModelAstroWebUiMotionProgrammesTest passed, including exact source
  form bodies, Holy Grail status responses without an `ok` field, default
  blocking, preview-port routing and late-result disconnect ownership. No live
  Astro WebUI, mount, camera or physical SLOW command was exercised.


- **2026-11-10 - WBS-05.4/08.2 ACTIVE (Astro WebUI guide telemetry):**
  added bounded source-aligned `/api/guide` parsing for running state, threshold,
  interval, last drift, up to 64 drift points and a capped log. The Mission
  surface polls this readback with the same session lifecycle as the other
  Astro status surfaces and keeps it distinct from proof that a correction
  reached the mount. Guide writes use a separate default-off gate.
- **2026-11-10 - verification:** AstroWebUiGuideTest,
  AppViewModelAstroWebUiGuideTest and FeatureFlagsTest passed for exact `on=1`
  form acknowledgement, malformed/sentinel data, safe default-on readback,
  preview-port routing, default-off writes and late-result disconnect
  ownership. No live guiding or mount command was exercised.


- **2026-11-10 - WBS-05.5/08.2 ACTIVE (Astro WebUI autofocus progress):**
  added bounded `/api/astro/autofocus` progress parsing for resident status,
  V-curve samples, best step and failure detail. The shared Mission surface
  exposes progress and validated sweep parameters, while start/stop remain
  separate default-off writes because they drive the focuser and consume live
  preview frames.
- **2026-11-10 - verification:** AstroWebUiAutofocusTest,
  AppViewModelAstroWebUiAutofocusTest and FeatureFlagsTest passed for source
  progress shapes, explicit confirm/stop forms, range rejection, safe
  default-on readback, default-off writes, preview-port routing and
  disconnect ownership. No live focus, camera or mount command was exercised.


- **2026-11-10 - WBS-05.3/08.2 ACTIVE (Astro WebUI focal controls):** added
  bounded source-aligned `/api/focal` parsing for manual override, EXIF cache
  and configured fallback values. The shared Mission surface performs a
  session-scoped read on entry and offers Refresh, Use auto and Set override
  controls; focal writes remain separately default-off, validate 1-100000 mm
  and confirm through readback. No solve, alignment or mount command is
  inferred from focal state.


- **2026-11-10 - WBS-05.1/08.2 ACTIVE (Astro WebUI travel-limit
  readback):** added bounded `GET /api/astro/limits` parsing for enforced,
  released and unknown states. Mission now refreshes and displays the resident
  safety state with collision-risk language; limit release is deliberately
  not exposed because the source requires physical attestation and a
  collision-clearance qualification. No 541/542 write was sent.


- **2026-11-10 - WBS-02.3/08.2 ACTIVE (Astro WebUI keep-Wi-Fi):** added
  source-aligned status for enabled/running/aligned/forced helper state and
  normal, force and off forms. The Connection workspace exposes Refresh and
  clearly separates helper state from bridge/Bluetooth health; writes remain
  default-off until alignment, bridge recovery and process teardown are
  qualified.


- **2026-11-10 - WBS-02.1/08.2 ACTIVE (Astro WebUI Wi-Fi status):** added
  bounded read-only `GET /api/wifi` parsing for AP/station addresses, SSID,
  saved configuration and autojoin. The Connection workspace keeps this
  evidence separate from Bluetooth wake and PC bridge health; Wi-Fi mutation
  remains out of scope.


- **2026-11-10 - WBS-05.6/08.2 ACTIVE (Astro WebUI target controls):** added source-aligned compass alignment plus preview-then-confirm GoTo contracts. RA/Dec/name input is bounded before transport; preview requires positive `valid=true` and `preview=true` evidence; confirmation requires an exact matching preview and adds the explicit `confirm=1` guard. Motion writes and alignment remain separately default-off, while the safe preview path is available for UI rehearsal.
- **2026-11-10 - verification:** AstroWebUiTargetControlTest, AppViewModelAstroWebUiTargetTest, FeatureFlagsTest and Compose JVM compilation passed. No live Astro WebUI, alignment, GoTo, mount or physical SLOW operation was exercised.


- **2026-11-10 - WBS-05.3/08.2 ACTIVE (Astro WebUI solve controls):** added source-aligned solve latest/capture/wait start routes plus explicit cancel and apply-last-solution adapters. Busy responses, stale/unknown acknowledgements and resident status are preserved as non-success outcomes; all solve mutations remain behind a separate default-off gate because capture and apply can affect camera/mount state. Mission exposes the actions with the qualification boundary visible.
- **2026-11-10 - verification:** AstroWebUiSolveControlTest, AppViewModelAstroWebUiSolveControlTest, FeatureFlagsTest and Compose JVM compilation passed. No live solver, camera capture, alignment apply or mount operation was exercised.


- **2026-11-10 - WBS-02.1/02.3/08.2 ACTIVE (Astro WebUI control-link telemetry):** added bounded read-only `/api/link` parsing for link phase, wanted state, connect/drop/RX/TX/parse/eviction counters, last-frame age, error and capped opcode snapshots. Connection presents this as resident Astro server evidence and keeps it separate from Bluetooth wake, PC bridge and native motion ownership.
- **2026-11-10 - verification:** AstroWebUiLinkTest, AppViewModelAstroWebUiLinkTest, FeatureFlagsTest and Compose JVM compilation passed. No live Astro server, Bluetooth, bridge or mount operation was exercised.


- **2026-11-10 - WBS-05.2/09.1 ACTIVE (mobile compass rehearsal):** added an optional Android rotation-vector compass source and Mission controls to read a local heading, display it and copy it into the manual Astro alignment field. The source is lifecycle-stopped, desktop remains manual-entry only, and no sensor reading can send a mount command; compass alignment remains behind its existing default-off qualification gate.
- **2026-11-10 - verification:** Compose JVM tests, Compose JVM compilation and Android debug Kotlin compilation passed. No physical sensor accuracy, magnetic interference, alignment or mount operation was qualified.


- **2026-11-10 - WBS-03.6/09.1 ACTIVE (SLOW rehearsal calibration UX):** added a visible, test-tagged host-reference calibration card for the selected SLOW level and explicit held/released button semantics with button roles. The card labels state-1/state-2 rates as planner rehearsal data, never as live promises; the hard-coded live transport gate and no-wire boundary remain unchanged.


- **2026-11-10 - WBS-03.6/09.1 ACTIVE (SLOW PC keyboard guidance):** added an explicit shortcuts disclosure for desktop and keyboard users. It documents Tab focus, Enter/Space hold/release, focus-loss stop and STOP ALL while stating that UI input shares the motion owner and does not create a second repeat loop.



- **2026-11-10 - WBS-04.3/06.1/08.2 ACTIVE (Astro WebUI in-head capture):**
  added a source-aligned, separately gated /api/astro/capture client for the
  resident 272 capture route. It validates interval, optional shot count and
  bulb duration, sends the source confirm=1 form (blank shots means
  unlimited), parses the returned programme acknowledgement, and immediately
  hands authority to resident /api/prog status. The Mission surface keeps
  this distinct from ordinary M/B capture, shows the source warning that
  externally triggered Astro capture can be unreliable, and requires a
  two-step arm/start action. The feature is default-off because it can move
  the mount and fire the shutter; no live route, recovery or physical shutter
  evidence has been claimed.
- **2026-11-10 - verification:** AstroWebUiAstroCaptureTest,
  AppViewModelAstroWebUiAstroCaptureTest,
  AstroWebUiProgrammeComposerContractTest, FeatureFlagsTest, shared JVM tests,
  Compose JVM tests and Compose JVM compilation passed. No live Astro WebUI,
  camera, mount, Bluetooth or physical SLOW operation was exercised.



- **2026-11-10 - WBS-06.1/08.2 ACTIVE (Astro panorama interval control):**
  added the source /api/prog/pano/interval resident-programme control. The
  seconds value is bounded before transport, uses the dedicated preview/WebUI
  port, remains behind the existing programme-write gate, and is only shown
  for an observed panorama run. Mission labels the change as a request and
  keeps resident programme polling authoritative; default configuration still
  sends no write.
- **2026-11-10 - verification:** AstroWebUiProgrammeControlTest and
  AppViewModelAstroWebUiProgrammeControlTest passed for exact route/body,
  unsafe-range rejection, default blocking and preview-port routing. Full
  shared/Compose/Android/desktop build passed. No live Astro WebUI or mount
  operation was exercised.



- **2026-11-10 - WBS-04.3/06.1/09.1 ACTIVE (Astro capture arm safety):**
  aligned the in-head Astro capture input bound with the source WebUI maximum
  of 5000 frames and added the source four-second arm timeout to the shared
  Mission composer. Editing any parameter still disarms immediately; HTTP
  acceptance remains distinct from resident programme completion.
- **2026-11-10 - verification:** AstroWebUiAstroCaptureTest,
  AppViewModelAstroWebUiAstroCaptureTest,
  AstroWebUiProgrammeComposerContractTest and the full cross-platform build
  passed. No live Astro WebUI, camera, mount, Bluetooth or physical SLOW
  operation was exercised.



- **2026-11-10 - WBS-03.7/03.5 ACTIVE (fine-rate cancellation boundary):**
  fixed the existing SafeRateAdjustController so coroutine cancellation from
  apply or stop propagates through the transport seam instead of being
  converted into a synthetic UNKNOWN result. This keeps disconnect and
  teardown distinguishable from an ambiguous device acknowledgement; the
  controller remains capability-gated and no live rate transport was enabled.
- **2026-11-10 - verification:** RateAdjustControllerTest now covers
  cancellation propagation for both apply and stop, alongside the existing
  stale, lease, half-speed and unknown-result tests. The full cross-platform
  build passed.


- **2026-11-10 - WBS-03.7/03.5 ACTIVE (fine-rate solve-evidence gate):** extended the SafeRateAdjustController policy boundary so fine correction requires a fresh solve, minimum confidence and bounded field rotation in addition to capability qualification, tracking confirmation, half-speed exclusion and exclusive motion ownership. Default policy values are 5000 ms, 0.6 confidence and 5 degrees; these are safety-policy defaults, not hardware qualification claims. Missing, invalid, stale or unsafe evidence is rejected before transport, and an active correction becomes stale if the context later loses the gate.
- **2026-11-10 - verification:** added stale-solve, low-confidence and rotated-solve transport-isolation tests; focused fine-rate tests and full cross-platform build passed. No live solve, Bluetooth, mount or physical SLOW operation was exercised.


- **2026-11-10 - WBS-03.6/03.7/09.1 ACTIVE (fine-rate readiness UX):** added a responsive, read-only Fine correction readiness card to the shared Motion pane. It reports observed connection, tracking, half-speed, motion-owner and local solve evidence, plus the qualified firmware/route/physical-stop gate. Solve age refreshes once per second while a solve is present; missing field rotation remains visible as a blocker. The 0.60 confidence, 5-second freshness and 5-degree rotation values are labelled safety-policy defaults, not hardware qualification.
- **2026-11-10 - verification:** SlowMotionPaneSafetyContractTest, Compose JVM compilation, shared/Compose JVM tests, Android Kotlin compilation, desktop packaging and Android APK assembly passed. No live fine-rate, Bluetooth, mount or physical SLOW operation was exercised.


- **2026-11-10 - WBS-04.3/06.1/09.1 ACTIVE (unknown capture outcome UX):** hardened the Camera pane so an ambiguous shutter result disables manual M, Bulb, Astro M/B and new intervalometer starts. The pane now says not to retry, directs the operator to inspect the camera/card and reconnect, and keeps the existing lifecycle/file evidence gate authoritative. This aligns the UI with the Benro Connect asynchronous release behavior and the no-automatic-retry safety rule.
- **2026-11-10 - verification:** added CameraCaptureSafetyContractTest; the capture lifecycle regression suite, Compose JVM compilation, shared/Compose JVM tests, Android Kotlin compilation, desktop packaging and Android APK assembly passed. No live shutter, Astro WebUI, Bluetooth or camera operation was exercised.


- **2026-11-10 - WBS-02.3/08.2 ACTIVE (typed Bluetooth wake evidence):** added a shared Bluetooth wake phase model and wired standalone wake, PC bridge progress, mobile host status callbacks, success, failure and disconnect cancellation into AppViewModel. Connection now shows a mobile/PC evidence card that explicitly keeps the GATT wake pulse separate from Wi-Fi route, control socket, preview and camera readiness. A pulse success is never presented as a connected mount.
- **2026-11-10 - verification:** BluetoothWakeStateTest, wake/connect race and lifecycle tests, ConnectionPaneBluetoothEvidenceContractTest, Compose compilation, shared/Compose JVM tests, Android Kotlin compilation, desktop packaging and Android APK assembly passed. No live Bluetooth adapter, network bridge or mount operation was exercised.


- **2026-11-10 - WBS-03.6/03.7/09.1 ACTIVE (simulator fine-rate rehearsal):** added a Demo-only Fine correction rehearsal to the shared Motion pane. The flow is explicit: prepare synthetic solve evidence, confirm tracking, acquire the shared `TRACKING_CORRECTION` owner, submit one bounded ±0.1°/s logical request, and require an explicit STOP before release. It uses `SafeRateAdjustController` with a no-wire transport, exposes Ready/Applying/Active/Stale/Faulted states, blocks concurrent SLOW/fast motion through the shared arbiter, and cleans up on tracking stop, half-speed enable, emergency stop and disconnect. The UI labels synthetic evidence and no hardware command at the point of use. Live 532/533/534/AC 4A transport remains hard-gated and unqualified.
- **2026-11-10 - verification:** `AppViewModelFineRateDemoTest`, `SlowMotionPaneSafetyContractTest`, `RateAdjustControllerTest`, existing fast-jog regressions and the full cross-platform build passed. This is simulator/package evidence only; no live solve, rate transport, Bluetooth, mount or physical SLOW qualification was performed.


- **2026-11-10 - WBS-03.5/03.6 ACTIVE (gated runtime transport wiring):** installed the existing `QualifiedSlowMotionTransport` behind the shared SLOW controller and motion-owner stop boundary. Demo continues to use the no-wire logical transport; a future qualified session will use the source-aligned 532/533/534 formatter through the same lease, stop and recovery lifecycle. The immutable capability record remains unqualified, so no live frame can be emitted by the current build.
- **2026-11-10 - verification:** qualified transport tests, demo fine-rate/motion-owner regressions, full shared/Compose JVM tests, Compose compilation, Android Kotlin compilation, desktop packaging and Android APK assembly passed. No live SLOW route or physical stop was exercised.


- **2026-11-10 - WBS-03.5/03.6 ACTIVE (qualified SLOW path exposed):** changed the Motion pane and AppViewModel to derive live SLOW availability from the immutable `SlowMotionCapabilityRecord.liveReady` allow-list rather than a permanently false UI branch. The default record is still unqualified and the feature flag remains independent, so current builds stay safe; when a named evidence record is installed, the existing shared controller, dead-man loop, source-aligned transport and stop boundary are now reachable on mobile and PC. Demo remains no-wire and unchanged.
- **2026-11-10 - verification:** focused SLOW UI, controller and transport tests plus the full cross-platform build passed. No capability record was promoted and no live SLOW frame or physical stop was exercised.


- **2026-11-10 - WBS-03.5/03.6 ACTIVE (capability provenance injection):** made the exact `SlowMotionCapabilityRecord` an AppViewModel dependency with the production default still set to `SlowMotionCapabilities.current` (unqualified). The Motion pane, runtime adapter, live gate and support snapshot now consume the same record, preventing UI/runtime provenance drift and allowing a fake-head qualification test harness to install a named record without changing the release default.
- **2026-11-10 - verification:** SLOW transport/domain tests, Demo motion regressions, UI safety contract and the full cross-platform build passed. The attempted VM live fake-head test was discarded because the existing test session seam did not establish a deterministic owner coroutine; no unsupported live claim was retained.
