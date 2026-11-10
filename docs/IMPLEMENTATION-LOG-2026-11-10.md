# OpenPolaris implementation log

This log records the first execution slices after the product plan and WBS were
committed. It does not promote source or simulator evidence to hardware support.

## Completed slices

### WBS-04.3 — Astro WebUI shutter contract

Commits `4da4618`, `c77c492`, and `5cd87b1` add a transport-neutral, source-
aligned Astro WebUI capture seam:

- M/manual sends the Benro Connect-compatible `264` subtype-2 frame:
  `state:1;bulb:0;c:-1;`.
- B/bulb sends `state:1;bulb:<positive-seconds>;c:-1;`.
- B stop is an explicit `state:0;bulb:0;c:-1;` edge.
- The HTTP route is `/api/link/send` with the source form body and `confirm=1`.
- A successful HTTP response is represented as `Queued`, never as a completed
  photograph. Completion remains the shared post-request `264` lifecycle plus
  non-empty `773` publication evidence.
- Unsupported/unknown release modes, invalid durations, unsafe hosts, HTTP
  failures and malformed acknowledgements fail closed without automatic retry.

The adapter is deliberately not connected to a live socket or enabled as a
release capability. The existing OpenPolaris native direct-capture subtype-4
path remains separately qualified; no ambiguous subtype fallback was added.

## Verification

Passed:

```text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.AstroCaptureContractTest --rerun-tasks --console=plain --no-daemon
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.AstroWebUiCaptureTest --rerun-tasks --console=plain --no-daemon
./gradlew :shared:jvmTest :composeApp:jvmTest :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid --rerun-tasks --console=plain --no-daemon
```

The full shared/Compose/Android verification passed after the first contract
slice. The focused WebUI adapter suite passed after the M/B extension.

## Next controlled slices

1. Add the platform HTTP POST seam and an explicit feature flag, still default
   off, then connect it to the existing capture evidence state machine.
2. Add camera-pane UX for Astro M/B only when camera attachment, release-mode
   readback and the feature gate are all positive; show queue/evidence states
   separately.
3. Add platform save/share sinks for the Astro WebUI full-file result, keeping
   path validation and user approval at the platform boundary.
4. Run fake-mount replay and then named-firmware/no-camera qualification before
   any live shutter or motor capability is enabled.


### WBS-04.3 continuation — guarded live adapter seam

The next slice is now present in the worktree and is ready to commit:

- JVM and Android platform HTTP POST factories are bounded, cancellation-aware
  and return queue acknowledgements without treating them as shutter completion.
- `AppViewModel` routes optional Astro M/B dispatch through the existing camera
  attachment, release-mode, programme-ownership, evidence and watchdog gates.
- The Camera pane exposes M/B and explicit B stop actions only behind the
  default-off `astroWebUiCapture` flag and labels the queue/evidence distinction.
- Disconnect and correlated completion clear the Astro WebUI active state.

Verification passed for the focused capture suite, Compose JVM tests, Compose JVM
compile and Android debug compile. No live mount, camera or Astro WebUI endpoint
was exercised.

### WBS-07.2 — Astro WebUI full-file save/share

The gallery now carries the source-aligned `/api/media/full` result through a
platform-neutral export controller. It accepts only a non-empty full-file JPEG
with a policy-approved Astro path and a safe basename; thumbnails, traversal
paths, empty data and byte mismatches are rejected before any sink is called.

- Desktop uses a native Save dialog for an explicit copy destination.
- Android uses `ACTION_CREATE_DOCUMENT` for Save copy and a cache-only
  `FileProvider` URI for `ACTION_SEND`; chooser cancellation and host recreation
  are reported as failures rather than success.
- Share/save state is visible in the Gallery and is cleared when the gallery
  selection, session or feature gate changes.
- No native 772/773/774/781/782 transfer or deletion path was enabled.

Verification passed:

```text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.AstroMediaExportTest :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiMediaTest --rerun-tasks --console=plain --no-daemon
./gradlew :androidApp:compileDebugKotlinAndroid :composeApp:compileKotlinJvm :shared:jvmTest --tests dev.openpolaris.core.domain.AstroMediaExportTest --rerun-tasks --console=plain --no-daemon
```

The Android provider and activity-result bridge compile, and the controller/ViewModel
tests prove exact-byte export and fail-closed rejection. Physical Android chooser
behaviour and desktop native dialog behaviour remain host-validation tasks.


### WBS-05.1/08.2 — Astro WebUI read-only status

The Gallery now has an explicit, read-only status check for the Astro WebUI's `/api/astro` response. The client is bounded to a 64 KiB response, validates the endpoint host, ignores forward-compatible JSON fields, and reports only the source's `active` value.

- The AppViewModel uses the dedicated preview/WebUI port (8080), not the control port.
- The request is feature-gated, requires an installed session, and resets on disconnect.
- The UI distinguishes idle, loading, active/inactive and failed states.
- The path does not call `/api/astro/enter`, `/api/astro/leave`, `/api/astro/track` or any native mount command; it cannot imply tracking or mission completion.

Verification passed:

```text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.AstroWebUiStatusTest --rerun-tasks --console=plain --no-daemon
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiStatusTest --rerun-tasks --console=plain --no-daemon
```

No live Astro WebUI endpoint or hardware command was exercised.


### WBS-05.1 — Astro WebUI read-only observing site

The Mission readiness sheet now offers a source-aligned read of the Astro WebUI's `/api/site` response. It reports whether a saved latitude/longitude exists and validates both coordinates before exposing them to the UI.

- The client is bounded to a 64 KiB JSON response and uses the dedicated preview/WebUI port.
- A missing saved site is a valid unknown/pending state; it is not treated as a default location.
- Out-of-range, missing and malformed coordinates fail closed.
- The path never calls the POST site-save route and does not claim alignment, tracking or GoTo readiness.

Verification passed:

```text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.AstroWebUiSiteTest --rerun-tasks --console=plain --no-daemon
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiSiteTest --rerun-tasks --console=plain --no-daemon
```


### WBS-06.1/08.2 — Astro WebUI read-only programme status

The Mission readiness sheet now exposes the resident Astro WebUI's `/api/prog` state. The parser follows the source C response contract for idle, timelapse, panorama, HDR, focus, program, pathlapse and solar runs, while retaining unknown future kind strings for display.

- Progress values and sentinel ranges are validated; malformed or contradictory numeric state fails closed.
- Pause, preview and stale are shown as observed flags.
- The request is bounded, feature-gated, session-gated and routed to the dedicated WebUI port.
- No programme start, pause, cancel or native mount command is issued.

Verification passed:

```text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.AstroWebUiProgrammeTest --rerun-tasks --console=plain --no-daemon
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiProgrammeTest --rerun-tasks --console=plain --no-daemon
```

### WBS-06.1/08.2 — Astro WebUI programme polling parity

The Mission readiness sheet now owns the read-only /api/prog poll lifecycle while it is visible. It follows Astro WebUI's source cadence: one-second reads during a resident programme and four-second reads while idle.

- Manual Refresh and the visible-sheet poller are serialized by a shared mutex.
- Leaving the Mission sheet or disconnecting cancels the poller; no background read survives the owner lifecycle.
- Polling never starts, pauses, cancels or otherwise mutates a programme.

Verification command:

./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiProgrammeTest --rerun-tasks --console=plain --no-daemon

### WBS-06.1/08.2 — Astro WebUI resident programme control seam

Added source-aligned /api/prog/cancel and /api/prog/pano/pause adapters and exposed their safety-gated Mission actions. The default remains disabled because the endpoints can alter a live resident run; an accepted HTTP acknowledgement is followed by a /api/prog read and is never treated as proof of physical completion.

Verification is covered by AstroWebUiProgrammeControlTest. No live write route or hardware command was exercised.

### WBS-06.3/08.2 — Astro WebUI programme preview/start seam

Added a source-aligned, bounded programme client for the resident Astro WebUI:

- Timelapse start uses /api/prog/lapse with the source interval, shots, bulb,
  fps, confirm=1 form contract and rejects overlapping Bulb intervals before
  transport.
- Panorama preview/start use the /api/prog two-step contract. Preview exposes
  the exact returned frame and preserves the source inferred marker; start adds
  confirm=1.
- HDR preview exposes the resolved frame/shutter summary; start adds confirm=1.
- Focus validation, preview and start preserve the source distinction between
  validation, preview=1 and confirm=1.
- All four kinds validate the source bounds, use the dedicated WebUI/preview
  port, enforce the safe-host policy and distinguish non-committing Preview
  from Accepted start. Accepted is not completion; /api/prog remains the
  status authority.
- AppViewModel exposes lifecycle-safe, duplicate-request-blocked actions behind
  a new default-off astroWebUiProgrammeStarts flag. Disconnect cancels the job
  and clears stale start state.

Verification passed:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.AstroWebUiProgrammeStartTest :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiProgrammeStartTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon
~~~

No programme write route, resident Astro WebUI, camera, mount or physical
hardware was exercised. The Compose composer/two-tap controls and named-head
qualification remain the next UX/field slices.

### WBS-01.1/01.3 - Typed responsive destinations

Added the first adaptive-shell navigation layer shared by phone and PC:

- Mission, Control, Camera, Astro and Gallery are typed primary destinations.
- Compact layouts use a five-destination bar plus a More overflow for
  connection, preview, helpers, firmware, guide, VR and settings.
- Wide layouts use a primary destination rail and retain a separate advanced
  callout rail.
- Mission and Astro open the existing readiness surface with truthful titles;
  Control and Camera preserve their existing gated panes; Gallery preserves
  its export lifecycle.

The old callout routes remain available during this transitional shell slice;
the next shell work is to replace modal primary surfaces with responsive
inline workspaces and add rendered semantics/screenshot coverage.

Verification passed:

~~~text
./gradlew :composeApp:compileKotlinJvm :composeApp:jvmTest --rerun-tasks --console=plain --no-daemon
~~~

### WBS-03.1/03.2 - Fast-jog ownership seam

FastJogController now accepts the shared MotionSupervisor. A verified duration
hold can be blocked by SLOW, GoTo or another owner, and its release/expiry/
disconnect stop is routed through the owner-specific shared boundary. Existing
live JogPane dispatch remains unchanged because the physical stop and positive
acknowledgement contract for that UI path still needs qualification.

Verification is included in the combined FastJogController, SlowMotionController
and MotionSupervisor test run.

### WBS-03.1/03.5 - Shared motion ownership for SLOW

The demo SLOW controller now participates in the shared MotionSupervisor
rather than owning an isolated lease. It cannot acquire while another motion
owner is active; emergency, release and disconnect stops pass through the
supervisor's owner-specific stop transport; and recovery is lease-scoped so a
stale SLOW controller cannot clear another owner's motion.

The new deterministic tests cover cross-owner blocking, stop delegation,
supervisor idle recovery and stale lease recovery. The demo stop transport still
records logical events only. Fast jog, GoTo, programme and live SLOW adapters
remain separate migration/qualification work.

Verification passed:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.SlowMotionControllerTest --tests dev.openpolaris.core.domain.MotionSupervisorTest --rerun-tasks --console=plain --no-daemon
~~~

### WBS-06.3/08.2 - Astro WebUI programme composer

Added the cross-platform Mission composer for the new source-aligned client:

- Responsive mode selection covers lapse, panorama, HDR and focus.
- Numeric fields use the same bounded domain contract; invalid text reaches
  validation as an invalid value and never creates an unvalidated wire body.
- Lapse uses an explicit arm then second-tap start warning.
- Panorama and HDR require a returned preview before the start action; focus
  requires source validation before start.
- Preview/validation and Accepted/status states are visible, including the
  returned panorama/HDR frame and inferred marker.
- The entire composer remains behind the default-off
  astroWebUiProgrammeStarts flag.

Verification passed:

~~~text
./gradlew :composeApp:compileKotlinJvm :composeApp:jvmTest --rerun-tasks --console=plain --no-daemon
~~~

No live WebUI write route, camera or mount was exercised.

### WBS-01.1/01.3 - Inline primary workspaces

The typed destinations now own the main responsive workspace rather than only
opening modal copies of the panes:

- Mission and Astro render the readiness/programme workflow inline.
- Control renders position, jog and the slow-motion dock together.
- Camera renders the existing evidence-gated camera workspace.
- Gallery renders the existing Astro WebUI media and export workflow.
- Mission action callbacks route to typed destinations or advanced callouts
  without silently starting a connection, shutter, motor or programme action.
- One LazyColumn owns destination scrolling, avoiding same-axis nested scroll
  inside the existing CalloutDialog wrapper.

Verification passed:

~~~text
./gradlew :composeApp:compileKotlinJvm :composeApp:jvmTest --rerun-tasks --console=plain --no-daemon
~~~

### WBS-02.1 - Inline endpoint health

The Mission workspace and Connection pane now share one read-only endpoint-health
section. It reports endpoint kind/identity, control and preview addresses
independently, route phase age and last failure, with a stable connection action
from the Mission workspace. The phase-age calculation clamps clock rollback to
zero; no connection, Bluetooth, Wi-Fi or preview transport is started by the
presentation layer.

Verification passed:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.EndpointHealthTest :composeApp:jvmTest --tests dev.openpolaris.ui.UiAccessibilityContractTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon
~~~

### WBS-03.6 - Motion owner state UX

The Motion workspace now observes the shared MotionSupervisor state and labels
idle, owned-owner and fault-blocked conditions separately from the Demo SLOW
logical scheduler trace. This keeps ownership and stop-fault feedback visible on
mobile and desktop while the firmware-bound transport gate remains closed.

Verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.UiAccessibilityContractTest :composeApp:compileKotlinJvm :shared:jvmTest --tests dev.openpolaris.core.domain.MotionSupervisorTest --tests dev.openpolaris.core.domain.SlowMotionControllerTest --rerun-tasks --console=plain --no-daemon
~~~

### WBS-05.1/08.2 - Astro WebUI session status parity

Extended the source-aligned read-only /api/astro status path to retain the
resident WebUI session/request fields:

- optional site latitude/longitude;
- track requested state;
- source rate index and half-speed flag;
- optional alignment heading.

The parser accepts the source's inactive/active field shape, ignores unknown
future fields, rejects partial or out-of-range coordinates, invalid headings and
negative rate indexes, and preserves the dedicated preview-port/session/feature
gates. The Gallery displays the observed values below the refresh action and
labels them as session/request state; it does not infer physical tracking or
alignment completion from them.

Verification passed:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.AstroWebUiStatusTest :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiStatusTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon
~~~

No live Astro WebUI endpoint, mount, camera or hardware was exercised.

### WBS-05.1/08.2 - Astro WebUI status polling

The Mission/Astro workspace now follows the Astro WebUI's visible-tab status
cadence:

- /api/astro is read every two seconds while the workspace is visible;
- manual refresh and polling serialize through a shared mutex;
- leaving the workspace and disconnecting cancel the poller;
- the Gallery remains an explicit read-only status surface as well.

The UI shows the active mode, source session/request fields and an explicit
mount-telemetry authority note. Polling performs only bounded GET reads and
does not enter/leave Astro mode, change tracking, move the mount or claim
alignment completion.

Verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiStatusTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon
~~~

No live Astro WebUI endpoint, mount, camera or hardware was exercised.

### WBS-05.3/08.2 - Astro WebUI session-control seam

Added the source-aligned session writes from the Astro WebUI:

- POST /api/astro/enter and /api/astro/leave;
- POST /api/astro/track with on, rate (sidereal 0 or lunar 2) and half fields;
- bounded acknowledgement parsing and safe-host validation;
- status readback on the dedicated preview/WebUI port after acceptance;
- lifecycle cancellation and disconnect reset in AppViewModel.

The responsive Astro mission surface exposes Enter Astro, Leave Astro, rate
selection, half-rate selection and Start/Stop Astro tracking, but the separate
astroWebUiSessionControls flag defaults OFF and the UI explains that ownership,
route and physical recovery evidence are still required. The seam does not
claim that an HTTP acknowledgement proves the mount entered Astro mode or is
tracking; /api/astro readback and mount telemetry remain separate evidence.

Verification passed:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.AstroWebUiSessionControlTest --tests dev.openpolaris.core.config.FeatureFlagsTest :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiSessionControlTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon
~~~

No live Astro WebUI write route, mount, camera or hardware was exercised.

### WBS-05.2/08.2 - Astro WebUI solve evidence

Added the source-aligned read-only /api/astro/solve seam:

- The bounded parser consumes the target and last-frame objects emitted by the
  Astro WebUI files, including target/solved coordinates, rotation, drift,
  match count, RMS, log odds, failure reason and action metadata.
- Unknown fields remain forward-compatible, but malformed object shapes,
  partial coordinates, non-finite/out-of-range values and oversized metadata
  fail closed.
- Mission owns a two-second visible-surface poll and a manual refresh routed to
  the dedicated WebUI/preview port. Disconnect clears the state.
- The UI labels this as file-backed solve evidence only; it cannot imply mount
  alignment, tracking or successful physical correction.

Verification passed:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.AstroWebUiSolveTest :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiSolveTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon
~~~

No solve, alignment, mount or camera write route was exercised.

### WBS-03.5/03.6 - Named SLOW capability allow-list

Paired the hard live-transport gate in the Motion workspace with an
immutable SlowMotionCapabilityRecord. A live SLOW adapter cannot become
eligible until the record carries named firmware, gimbal and source-app
provenance plus route/unit, signs, measured envelope, acknowledgement,
watchdog, physical stop, disconnect recovery and limit evidence.

The default record is deliberately unqualified and the UI lists its missing
gates. This improves the mobile/PC explanation without opening 532/533/534 or
AC 4A transport.

Verification passed:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.SlowMotionCapabilityTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon
~~~

### WBS-03.1 - GoTo owner integration

Moved the live arrival-aware GoTo operation behind the shared MotionSupervisor:

- 519/517 GoTo acquires `MotionOwner.GOTO` before it starts and reports the active owner when another motion workload holds the lease.
- The existing 519 `state:0` cancel path is the GoTo stop adapter; cancel, normal completion, disconnect and session teardown all pass through the same stop boundary.
- A failed or ambiguous stop remains a supervisor fault and is not silently converted into an idle/ready state.

Verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.SlowMotionPaneSafetyContractTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon
~~~

This validates the UI safety contract and Compose compilation. No live GoTo, mount or hardware was exercised in this environment.

### WBS-05.1/08.2 - Astro WebUI run-state evidence

Added the source-aligned read-only /api/state seam:

- The bounded parser preserves status, elapsed time, mount-read versus mount-blocked state, altitude, azimuth, alignment, tracking, optional solution coordinates and the source log.
- Automatic Mission polling uses only /api/state at the existing two-second visible-surface cadence.
- /api/state?mount=1 is never polled automatically; it is available only through an explicit Read mount position action and the UI states that the resident WebUI may block it unless alignment permits.
- A feature flag keeps the read-only surface discoverable by default, while disconnect resets its state and lifecycle jobs.

Verification passed:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.AstroWebUiRunStateTest --tests dev.openpolaris.core.config.FeatureFlagsTest :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiRunStateTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon
~~~

The focused ViewModel report contains three passing tests for preview-port routing, two-second polling without mount reads and explicit mount-position reads. No live Astro WebUI, mount or hardware was exercised.

### WBS-03.1/03.2 - Fast-jog live owner integration

Moved the existing 513-516 rocker behind the shared motion owner:

- The ViewModel now sends the source-aligned duration payload time:Nms;
  through sendOnly, repeats beats at the controller's 50 ms cadence and
  sends state:0; on release, lease expiry, disconnect or stop.
- The MotionSupervisor receives a lease-aware stop hook so the active axis is
  retained through release and a failed write becomes an unknown/faulted stop
  rather than an idle claim.
- Jog buttons on the common mobile/PC surface now use pointer/keyboard
  hold-to-move semantics with release-to-stop; the compatibility jog API
  remains a bounded one-shot.
- The default basic-controls path remains available because 513-516 are
  existing verified commands, but this slice does not claim new physical
  stop evidence for every named firmware build.

Verification passed:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.FastJogControllerTest --tests dev.openpolaris.core.domain.MotionSupervisorTest --tests dev.openpolaris.core.domain.SlowMotionControllerTest :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelFastJogTest --tests dev.openpolaris.ui.SlowMotionPaneSafetyContractTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon
~~~

The simulator and deterministic controller tests passed; no live mount or hardware was exercised.

### WBS-03.1/05.2 - Alignment owner boundary

The discrete 530 alignment-record action now participates in the shared
motion supervisor as an ALIGNMENT owner. It reports contention with fast jog,
SLOW or GoTo, releases through a cancellation-safe finally block, and is
explicitly stopped/cleared during disconnect. This owner protects the
operation boundary; it does not claim that recording a star physically moves
the mount or proves a pointing model.

Verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAlignmentTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon
~~~

Four existing alignment ViewModel tests passed; no live alignment or hardware was exercised.

### WBS-03.1/03.6 - Common emergency stop

Replaced the demo-only STOP ALL behavior with a shared emergency-stop path for
the Motion dock. The active owner is inspected before dispatch:

- FAST_JOG and SLOW_MOVE use their lease-scoped controller emergency boundary,
  so a stale UI action cannot stop or clear another owner's lease.
- GoTo and alignment use the MotionSupervisor emergency boundary, then cancel
  their local jobs only after a non-unknown result.
- A rejected, unknown or already-faulted result remains visible; the UI never
  converts an uncertain stop into idle.
- The action is available only while the shared supervisor reports an owned
  motion lease.

Verification passed:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.FastJogControllerTest :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelFastJogTest --tests dev.openpolaris.ui.SlowMotionPaneSafetyContractTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon
~~~

The focused run passed, including the lease-scoped emergency-stop controller
test and the ViewModel test that clears a demo fast-jog owner. No live mount or
hardware was exercised; physical emergency-stop observation remains open.

### WBS-03.6/09.1 - Focus-loss dead-man hardening

The shared mobile/desktop hold-to-move control now treats keyboard focus loss as
an input cancellation. It releases the active hold exactly once whether the
loss occurs during pointer capture or a keyboard hold, while preserving the
existing pointer-up and key-up release paths. The control remains labelled
"Hold to move; release to stop" and still never creates a UI repeat timer.

Verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.SlowMotionPaneSafetyContractTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon
~~~

The safety contract and Compose JVM compilation passed. No live mount or
hardware was exercised.

### WBS-03.6/09.1 - Global motion safety chrome

The responsive shell's always-visible status strip now observes the shared
MotionSupervisor. While an owner is active it shows the owner name and a
reachable STOP ALL action; if the supervisor is faulted it shows the stop fault
in the status chrome rather than presenting the mount as idle. This keeps the
highest-priority safety action available on both the phone layout and the wide
desktop layout, including when the user is viewing Mission, Camera or Gallery.

Verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.MotionSafetyChromeContractTest --tests dev.openpolaris.ui.SlowMotionPaneSafetyContractTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon
~~~

The shell safety contract and Compose JVM compilation passed. No live mount or
hardware was exercised.

### WBS-04.3 - Astro WebUI shutter reliability hardening

Tightened the source-aligned resident WebUI shutter path for both M and B
modes:

- Admission marks the request as dispatching before the coroutine yields, so
  rapid taps cannot queue two 264 writes.
- HTTP bridge exceptions become an explicit failed capture outcome rather than
  an unobserved coroutine failure.
- Bulb stop is lease-like at the UI boundary: exactly one state-0 stop may be
  posted while the first stop awaits lifecycle/file evidence. The UI shows
  "stop queued" and does not expose a second stop action.
- Disconnect, confirmed evidence, timeout and failed dispatch clear the new
  transient state.

Verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiCaptureTest :shared:jvmTest --tests dev.openpolaris.core.domain.AstroWebUiCaptureTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon
~~~

The source-aligned M/B payload tests and ViewModel capture lifecycle tests
passed. No live Astro WebUI, camera or hardware was exercised.

### WBS-03.1 - SLOW emergency edge

Closed a motion-safety gap in the shared SLOW controller. Emergency and
disconnect now accept the controller's armed, holding or faulted lease instead
of only accepting a holding state. This matters when the scheduler has not
emitted its first command yet, or when a command acknowledgement becomes
unknown: STOP ALL can still reach the shared stop boundary and only settles to
idle after that boundary returns Stopped.

Verification passed:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.SlowMotionControllerTest :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelFastJogTest --tests dev.openpolaris.ui.MotionSafetyChromeContractTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiCaptureTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon
~~~

The SLOW controller, motion safety chrome, fast-jog owner and Astro WebUI
capture lifecycle tests passed. No live mount or hardware was exercised.
### WBS-03.5 - Qualification-gated SLOW runtime adapter

Added QualifiedSlowMotionTransport as the first runtime-shaped seam between the owner-driven SLOW scheduler and the source-aligned 532/533/534 wire formatter. The adapter requires an evidence-backed SlowMotionCapabilityRecord.liveReady result before the sender callback can be reached, never falls back to AC 4A, treats the scheduler's first idle-axis STOP as a logical no-op, retains acknowledged active points for explicit STOP frames, and keeps the retained point after an UNKNOWN stop result so recovery cannot be reported as successful by accident. The default record remains unqualified and no live adapter is installed in AppViewModel.

Focused verification passed:

./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.QualifiedSlowMotionTransportTest --tests dev.openpolaris.core.domain.SlowMotionControllerTest --tests dev.openpolaris.core.protocol.SlowMotionWireTest --tests dev.openpolaris.core.protocol.SlowMotionWireReplayTest :shared:compileKotlinJvm --rerun-tasks --console=plain --no-daemon

Result: BUILD SUCCESSFUL; 22 tests passed. This is host-side contract evidence only. Exact firmware/gimbal/source-app identity, acknowledgement, watchdog, physical stop, disconnect recovery and limit evidence remain required before enabling live SLOW.
### WBS-08.2 - Bounded support bundle

Added a cross-platform Settings export for a bounded, redacted support snapshot. AppViewModel assembles only observed route/service health, device identity fields, camera/storage state, correlated capture timeline/publication evidence and the immutable SLOW capability blockers; the snapshot does not issue fresh mount requests. The document redacts hostnames and password/token/secret-like values, emits a basename-only publication field, states that raw logs are not included, and refuses oversized or unsafe requests. Android uses a lifecycle-owned CreateDocument text chooser; desktop uses a native Save dialog. Failed or cancelled saves remain terminal until the user explicitly requests another export.

Verification passed:

./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.SupportBundleTest :shared:compileKotlinJvm :composeApp:compileKotlinJvm :androidApp:compileDebugKotlinAndroid --rerun-tasks --console=plain --no-daemon

Result: BUILD SUCCESSFUL; four support-bundle tests passed, shared/Compose JVM compilation passed, and Android Kotlin compilation passed. No live mount, camera or network state was read for the export.
### WBS-02.2/08.2 - Support export lifecycle ownership

Closed a session-lifecycle gap introduced by the support-bundle export: the
Settings pane now shows Exporting while the Android/Desktop save surface is
pending, and AppViewModel.disconnect cancels the export job, resets its
controller and clears the export state. A regression test proves that an
in-flight platform save is cancelled and cannot leave stale export state after
disconnect.

Verification passed:

./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelSupportBundleTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon

Result: BUILD SUCCESSFUL; the lifecycle regression passed.

### WBS-02.2/02.3 - Bridge and Bluetooth session ownership

Closed another session-lifecycle gap: connectWifi() and wake() now retain
their coroutine jobs, reject duplicate bridge starts, propagate cancellation
without converting it to a failure, and are cancelled by disconnect() before
endpoint state is reset. The wake operation also clears its in-flight state
synchronously at teardown. This keeps bridge/Bluetooth callbacks from a
previous session from writing stale route health into a replacement session.

Verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelSessionLifecycleTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL; both lifecycle cancellation tests passed. No live
network, Bluetooth, mount or camera operation was exercised.

### WBS-02.2/04.3 - Capture command lifecycle ownership

Made still, Bulb and Bulb-stop dispatch session-owned. A capture request that
is queued on the Compose scope is now cancelled by disconnect before it can
recreate capture evidence or reach the shutter; the watchdog and stop command
are cancelled at the same boundary.

Verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelCapturePhaseTest --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL; the full capture phase suite passed, including the
new queued-dispatch regression and the existing M/B lifecycle/publication,
timeout, sequence and read-only focus/video checks. No live camera operation
was exercised.

### WBS-02.2 - Simulator session ownership

Made Demo startup obey the same session boundary as a live connection. Its
queued connect job is retained, cancellation is not converted into a failed
Demo session, and disconnect cancels it before simulator shutdown and marker
persistence.

Verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelSessionMarkerTest --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL; the session-marker suite passed, including the new
queued-Demo cancellation regression. No live mount or network operation was
exercised.

### WBS-02.3 - Bridge and standalone wake handoff

Made the full bridge operation the owner of host bring-up when a user starts
it during a standalone Bluetooth Wake. The prior wake is cancelled before
bridge progress begins, eliminating competing status writers while preserving
the explicit disconnect cancellation boundary.

Verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelSessionLifecycleTest --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL; the bridge/wake handoff and disconnect-cancellation
tests passed. No live Bluetooth, network, mount or camera operation was
exercised.

### WBS-02.2 - Cancellation truth for connect teardown

Closed the remaining cancellation-reporting gap in the session owner:
control connect, post-connect bootstrap and bridge teardown now rethrow
CancellationException. Deliberate disconnect therefore cannot overwrite
endpoint evidence with a synthetic failure message.

Verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelSessionLifecycleTest --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL; the pending-connect cancellation, bridge/wake
handoff and disconnect-cancellation tests passed. No live mount or network
operation was exercised.
