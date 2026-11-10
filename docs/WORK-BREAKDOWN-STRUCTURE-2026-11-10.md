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

- **2026-11-10 — WBS-03.5 ACTIVE (transport-neutral owner):** added a SLOW controller seam with exclusive ownership, separate dead-man renewal, scheduler ticks, explicit release/emergency/disconnect stops, and terminal unknown/recovery states. It sends no wire frame; exact firmware mapping remains blocked.
- **2026-11-10 — WBS-03.5/03.6 ACTIVE (owner-driven Demo SLOW):** moved the demo scheduler loop out of the Compose pane and into the shared SlowMotionController owner. Demo pointer/key holds now acquire a lease, renew it separately from scheduler ticks, record logical commands through a transport-neutral adapter, and release through the explicit stop path; disconnect cancels the owner and uses the disconnect stop boundary. Live 532/533/534 and AC 4A transport remains hard-gated.


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
- **Next:** extend execution only after separately qualifying exposure and motion owners; Astro WebUI save/share and native media-transfer qualification remain separate, and 772/773/774/781/782 transfer payloads remain blocked until their corpus-only formats are replay-tested and hardware-qualified.
