# Astro workflow and Benro Connect parity handover — 2026-09-17

## Handover status

**READY FOR IMPLEMENTATION. NO AGENT CURRENTLY OWNS THIS WORK.**

OpenPolaris does **not** currently have full Benro Connect feature parity and
does **not** provide a complete guided astro session. The older statement in
`OPENPOLARIS-PARITY-2026-09-03.md` that parity is complete is superseded by
this audit.

The requested user journey is:

```text
connect
  -> calibrate / level
  -> choose an alignment star
  -> slew to it
  -> preview and focus/autofocus
  -> centre it and record alignment
  -> repeat for 2-3 stars
  -> choose an imaging target
  -> goto and verify arrival
  -> start sidereal tracking
  -> configure intervalometer and camera
  -> start shooting
  -> monitor / pause / resume / stop
```

The repository contains many individual building blocks, but they are exposed
as unrelated controls rather than one guarded workflow. Do not claim parity
from the presence of opcode constants or isolated controller methods.

## Repository state and ownership warning

At audit time:

- branch `main` was at `7e5b2e7`, one commit behind `origin/main` (`57dd1a0`);
- the worktree already contained user/concurrent edits in
  `.github/workflows/release.yml`, `AppViewModel.kt`, `Panes.kt`,
  `LIVE-PROGRESS.md`, and the untracked 2026-09-17 K-3 III evidence directory;
- the dirty `AppViewModel.kt` / `Panes.kt` changes include camera-attachment
  polling, jog status messaging and preview-port/status fixes. Preserve them;
  do not replace these files wholesale;
- hardware ownership is currently unclaimed. Claim it in `LIVE-PROGRESS.md`
  before sending any mount, focus, preview or shutter command.

Start by comparing proposed work against both the dirty working tree and
`origin/main`; do not pull or merge over existing edits without first isolating
ownership.

## Current capability matrix

Status meanings: **WORKING PRIMITIVE** means reusable code exists but is not
proof of the integrated workflow; **PARTIAL** lacks required state/validation;
**BROKEN** cannot meet its stated contract; **MISSING** has no production UI or
workflow; **UNQUALIFIED** lacks adequate physical evidence.

| Workflow stage | Status | Current implementation | Required correction |
|---|---|---|---|
| Connect and identify mount/camera | WORKING PRIMITIVE | Connection and camera-info polling exist | Make connection/camera readiness a wizard prerequisite |
| Enter astro mode | MISSING | AHRS is a global chip; no astro-session lifecycle | Add explicit enter/exit lifecycle; enable AHRS on entry and disable it on every exit/disconnect |
| Calibrate / level | PARTIAL | Auto-level controls exist in `GotoPane` | Define whether this means auto-level, yaw reference, or both; provide progress/error/retry |
| Choose alignment star | MISSING | `TonightPane` can search/slew, but retains no alignment-star selection | Add selected star identity, coordinates, visibility checks and recommended-star list |
| Slew to alignment star | PARTIAL | Catalog and manual RA/Dec goto send code 519 | Tie slew to selected star; await arrival, timeout/cancel and final state |
| Preview during alignment | WORKING PRIMITIVE | MJPEG preview and health checks exist | Embed preview and prevent progression without a valid recent JPEG |
| Focus / autofocus | MISSING from workflow | Manual 262/311 methods exist; distinct autofocus is not exposed | Add bounded manual controls and a separately evidenced autofocus action; never alias AF to MF |
| Centre star | PARTIAL | Generic jog pad exists | Embed jog controls, show live position and require operator confirmation |
| Record one alignment point | BROKEN | `submitAlignmentStar()` sends code 530 | Retain selected star and send the verified target/observed-position contract |
| Multi-star alignment | BROKEN | UI counter increments | Persist one controller/session; preserve star index across submissions; require 2-3 separated stars |
| Reset alignment | BROKEN / misleading | Only the Compose counter resets | Reset controller/firmware state or clearly restart the entire alignment lifecycle |
| Choose imaging target | WORKING PRIMITIVE | Tonight catalog/search exists | Retain target in the session model instead of fire-and-forget slew |
| Goto imaging target | PARTIAL | UI sends 519 and immediately reports slewing | Use arrival polling, timeout/cancel and optional plate-solve refinement |
| Start tracking | PARTIAL | Track chip sends 531 `state:1;` | Send explicit sidereal rate, confirm state and gate on successful goto/alignment |
| Dither/settling | PARTIAL | Individual controls exist | Integrate with the capture plan and settling waits |
| Camera parameters | PARTIAL | ISO/WB/EV/shutter/aperture qualification surface | Promote physically verified settings; add format, focus and capture-mode contracts |
| Intervalometer | MISSING | 306/307 domain methods only; no production caller | Add interval, exposure/bulb, count/duration, delay, storage and validation |
| Start shooting | MISSING | Only single-shot Capture is exposed | Add the exact Benro multi-step sequence state machine |
| Monitor/pause/resume/stop | MISSING | No sequence lifecycle | Parse progress/file events; implement idempotent stop and recovery |

## Critical defects to fix first

### 1. Multi-star alignment always submits star index zero

`AppViewModel.submitAlignmentStar()` constructs a new `AlignmentController`
inside every button press:

```kotlin
AlignmentController(s).submitStar(...)
```

`AlignmentController.starCount` starts at zero and increments only inside that
new instance. The instance is then discarded. Every code-530 submission
therefore uses `num:0`, while the separate UI variable `alignmentStars`
increments and falsely displays 1, 2, 3.

Do not patch only the visible counter. The workflow must own a persistent
alignment session/controller and test the exact outgoing sequence
`num:0`, `num:1`, `num:2`.

### 2. No selected-star identity participates in alignment

The record action uses current mount yaw/pitch and observer location. It does
not retain the chosen catalog star, its RA/Dec, or its computed current alt/az.
Before implementation, re-check the Benro APK call sites for code 530 and pin
the semantics of `yaw`, `pitch`, `step` and `num` in tests. The current protocol
document says Benro uses `step:2` with target alt/az computed client-side.

### 3. Reset alignment is local-only

`resetAlignment()` only sets `alignmentStars = 0`. It does not reset a
controller, firmware calibration state or chosen-star history. Recover the
official reset/restart lifecycle from the APK. If no reset command exists,
document and enforce the safe lifecycle for abandoning an incomplete run.

### 4. UI goto does not await arrival

`AppViewModel.goto()` calls `TrackingController.gotoAzAlt()`, which only sends
code 519, then immediately reports "Slewing". A richer
`GoToController.goToRaDec()` polls code 517 for arrival, but the normal UI path
does not use it. Tracking and shooting must not unlock merely because a socket
write completed.

### 5. Tracking rate is implicit

`startTracking()` calls `TrackingController.start()` without a rate, producing
only `state:1;`. The astro workflow needs an explicit sidereal rate and a
separate lunar option. Confirm rate indices physically and verify resulting
state rather than treating write success as tracking success.

### 6. Intervalometer methods are orphaned

`CameraController.queryIntervalType()` / `setIntervalType()` and photo/video
status methods have protocol tests but no production caller. The timelapse flag
defaults off and there is no parameter form or sequence engine. Do not expose
306/307 alone and call that an intervalometer: Benro shooting workflows use
multi-step payloads and asynchronous progress states.

## Benro features inventoried but absent from production

These `Codes.BenroCamera` entries have no production reference outside their
constant declarations:

- 270 focus stack;
- 271 panoramic capture;
- 272 delayed shot;
- 277 sun shot;
- 280 HDR;
- 283 PLC;
- 289 remove-people shot;
- 305 Holy Grail.

They are not implemented features. Recover request construction, response
parsing, call-site ranges, cancellation and terminal states from the Benro APK
before writing each controller. Follow
`docs/CAMERA-PARITY-JUNIOR-AGENT-GUIDE.md` one feature at a time.

## Proposed architecture

Add a common-main `AstroSessionController` with a sealed state model:

```text
Disconnected
Ready
Calibrating
SelectingAlignmentStar(completedStars)
SlewingToAlignmentStar(star)
FocusingAlignmentStar(star)
CenteringAlignmentStar(star)
RecordingAlignmentStar(star, index)
AlignmentReady(stars)
SelectingTarget
SlewingToTarget(target)
RefiningTarget(target)
Tracking(target, rate)
ConfiguringSequence(target, plan)
Shooting(target, plan, progress)
Paused(...)
Completed(summary)
Failed(stage, reason, recovery)
```

Transitions must be validated centrally. Composables should render state and
send intents; they must not independently sequence protocol commands.

The session controller should own:

- one persistent alignment controller/state;
- observer location and time source;
- selected alignment stars and imaging target;
- AHRS lifecycle;
- goto arrival/cancellation;
- focus actions and preview freshness;
- explicit tracking rate and confirmation;
- intervalometer plan and capture progress;
- cancellation, disconnect and reconnect recovery;
- an evidence/event log for hardware qualification.

Create a dedicated **Astro** entry instead of expanding the generic Slew
dialog. Its minimum steps are:

1. readiness: connection, camera identity, battery, storage, preview, location;
2. calibration: level/yaw-reference operation and result;
3. alignment-star chooser with separation guidance and completed-star history;
4. alignment operation: slew, preview, focus/AF, jog, centre, record;
5. target chooser: Tonight/catalog/search;
6. goto/refine: progress, cancel, arrival and optional plate solve;
7. tracking: sidereal/lunar rate, active-state confirmation, dither/settling;
8. capture plan: settings, interval, exposure, count/duration, delay, format;
9. run screen: progress, files, pause/resume/stop and errors.

## Implementation order

Keep changes small and independently reviewable.

1. Add a failing integrated alignment test expecting `num:0`, `num:1`,
   `num:2`; prove the current repeated-`num:0` defect.
2. Add persistent alignment/selected-star state, exact code-530 tests and safe
   reset/new-session semantics.
3. Route UI goto through one arrival-aware controller; cover success, timeout,
   cancel, disconnect and angle wrap.
4. Add the Astro enter/exit AHRS lifecycle and explicit tracking-rate state.
5. Integrate bounded manual focus; implement autofocus separately only after
   its contract is evidenced.
6. Audit Benro intervalometer step payloads and terminal states; build a typed
   sequence controller with simulator tests.
7. Bind the controller to the dedicated Astro UI.
8. Qualify the complete workflow on hardware under one client, then labelled
   multi-client stress.
9. Implement remaining Benro shooting modes one at a time.

## Required software tests

At minimum add:

- three distinct alignment indices and selected-star identities;
- no progression without valid star, location, position and preview;
- alignment reset/new-session behaviour;
- goto arrival, timeout, cancel and disconnect;
- AHRS enabled once on entry and disabled on every exit path;
- explicit sidereal/lunar payload and state confirmation;
- focus near/far/stop acknowledgement, timeout and cancellation;
- interval-plan validation and exact step order;
- out-of-order, duplicate, malformed and missing progress frames;
- pause/resume/stop idempotence;
- shutter de-duplication after timeout/reconnect;
- file-event correlation to the active exposure;
- Compose tests proving blocked steps cannot be bypassed;
- process-death/reconnect behaviour without silent sequence resumption.

Run the canonical suite before handoff:

```bash
./gradlew test --no-daemon
```

Green protocol tests do not prove product or hardware completion.

## Hardware qualification gates

Before live commands:

1. claim hardware ownership in `LIVE-PROGRESS.md`;
2. prove `ip route get 192.168.0.1` uses `wlp8s0`;
3. record active 8080/9090 clients and eliminate or label extras;
4. prove USB `25fb:0189`, code-286 K-3 III identity, deployed provenance,
   pgphoto PID and storage baseline;
5. record bcmdhd error counters before and after each stage.

Observe physical results, not only acknowledgements:

- level/calibration motion and final tilt;
- selected star centred in preview;
- manual-focus direction and visible focus change;
- autofocus acquisition/failure without still capture;
- distinct three-star indexing and improved goto accuracy;
- goto arrival and tracking rate;
- exact exposure count and interval timing;
- final file integrity/association;
- stop/disconnect/reconnect recovery;
- no duplicate shutter;
- radio health under one bounded preview client.

The 2026-09-17 evidence under
`docs/evidence/k3iii-live-test-2026-09-17/` proves setting round trips, preview,
manual-focus acknowledgements and one completed DNG capture. It does **not**
prove the integrated astro workflow. That run also had a second client at
`192.168.0.2` and rising bcmdhd bookkeeping errors, so repeat under a clean
single-client baseline.

## Definition of done

Do not mark parity or the astro workflow complete until:

- the full journey at the top works from one Astro screen;
- every transition is acknowledgement/state/data-plane verified;
- 2-3 distinct alignment stars are correctly indexed and retained;
- selected alignment stars and imaging target remain explicit and visible;
- goto completion is observed before tracking unlocks;
- tracking uses and confirms the requested rate;
- intervalometer start/progress/pause/resume/stop exist;
- physical K-3 III qualification passes with exact image counts;
- failures/reconnects never duplicate a shutter command;
- every missing Benro feature is implemented, intentionally out of scope, or
  tracked with an owning issue;
- the parity report is regenerated from production references and physical
  evidence rather than opcode inventory.

## Immediate next action

Begin with one narrow change: add an integrated regression test for three
alignment submissions expecting `num:0`, `num:1`, `num:2`. It should fail
against the current `AppViewModel.submitAlignmentStar()` path. Then introduce a
persistent alignment-session owner and make the test pass without touching the
intervalometer or UI layout in the same change.
