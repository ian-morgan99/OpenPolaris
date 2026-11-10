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
| WBS-05 | Astro mission workflow | Prepare, align, track, target, solve, guide and recover | WBS-01,WBS-02,WBS-03,WBS-04 | PLANNED |
| WBS-06 | Observation programmes | Lapse, panorama, HDR, focus stack, PLC/path-lapse, solar workflows | WBS-04,WBS-05 | PLANNED |
| WBS-07 | Gallery and export | Safe media browse, thumbnails, full files and transfer | WBS-04 | ACTIVE |
| WBS-08 | System, diagnostics and interoperability | Settings, logs, firmware provenance, Alpaca/INDI/LX200 adapters | WBS-02,WBS-05 | PLANNED |
| WBS-09 | Accessibility, night UX and polish | Keyboard/screen-reader/touch/contrast/performance quality | WBS-01..08 | PLANNED |
| WBS-10 | Platform packaging and release | Android/desktop artifacts, install/upgrade/rollback and support docs | WBS-01..09 | PLANNED |
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

### WBS-03.2 — Fast jog dead-man contract — P0

- Preserve 513/514/521 semantics separately from slow/fine rate.
- Implement pointer capture, lease renewal, release/cancel/background stop and explicit active feedback.
- Exit: UI and controller tests cover touch cancellation, keyboard release, disconnect and stop ordering.

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

### WBS-03.6 — Motion UX — P1 — ACTIVE (disabled surface)

- Add mobile Motion dock, PC control panel, speed ladder, axis legend, owner state and emergency stop.
- Add disabled Fine correction sheet before live qualification.
- Exit: UI can be reviewed end-to-end without sending a live motor command; live controls appear only on an allow-list.

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

### WBS-05.2 — Alignment — P1

- Plate solve, phone compass and manual bearing alternatives with confidence/provenance.
- Preserve star-alignment fallback.

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

### WBS-07.2 — Viewer and export — P1

- Full-size view, metadata, download/share/save, transfer progress and retry only when idempotent.

## WBS-08 — System, diagnostics and interoperability

### WBS-08.1 — Settings and site — P1

- Persist validated location and user preferences locally with clear scope; distinguish head-persisted values.

### WBS-08.2 — Health/evidence centre — P1

- Event timeline, raw frame/log export, build identity, firmware identity, capability matrix and support bundle.

### WBS-08.3 — Firmware/network controls — P2

- Keep destructive operations off by default, require manifests/rollback and show exact installed provenance.

### WBS-08.4 — Alpaca/INDI/LX200 — P3

- Desktop-first adapters with ownership arbitration; no hidden second capture/motion path.

## WBS-09 — Accessibility, night UX and polish

### WBS-09.1 — Interaction quality — P1

- Touch target audit, focus order, screen reader text, keyboard shortcuts, reduced motion and text scaling.

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
- **2026-11-10 — verification:** SLOW safety and responsive-layout source contracts passed; no live motor command path was added.
- **2026-11-10 — WBS-09.1 ACTIVE (interaction hooks):** persistent Mission details and Gallery actions now have stable automation tags for keyboard, accessibility and desktop/mobile UI harnesses; existing callout and camera control labels remain covered.
- **2026-11-10 — WBS-01.2 ACTIVE (actionable readiness):** Mission readiness warnings now expose safe next-action buttons; connection, camera, preview, Slew/motion and Gallery/storage routes are mapped in the shell without issuing hardware commands from the dashboard.
- **2026-11-10 — verification:** accessibility and responsive-layout contracts passed after adding the readiness callback path; action routing remains navigation-only.
- **2026-11-10 — WBS-10 ACTIVE (build verification):** Android debug Kotlin and desktop JVM targets compile from the same shared UI/domain code; no platform-specific capability divergence was introduced.
- **2026-11-10 — WBS-07.1 ACTIVE (read-only media navigation):** Gallery now drives the evidenced 770 `type/page` request, exposes the confirmed `All` and `Normal` categories, renders page/count state and only enables next/previous navigation when the response count supports it; disconnect clears stale file-list state.
- **2026-11-10 — verification:** the file-list request payload and parser are covered by deterministic AppViewModel tests; delete/protect/format remain disabled by their existing feature gates.
- **Next:** continue WBS-04/WBS-07 with thumbnail/preview derivation and safe export contracts; 772/773/774/781/782 transfer payloads remain blocked until their corpus-only formats are replay-tested and hardware-qualified.
