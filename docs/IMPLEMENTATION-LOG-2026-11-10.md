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


### WBS-02.2 - Session-marker persistence ownership

Made reconnect-marker persistence session-owned. A successful connect now
retains its marker-write job; disconnect cancels it before clearing the session,
and the failure status is allowed through only when the original session still
owns the view model. This prevents delayed marker I/O from publishing stale
reconnect state after teardown.

Verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelSessionMarkerTest --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL; 40 session-marker tests passed, including the gated
queued-marker cancellation regression. No live mount or network operation was
exercised.


### WBS-03.6/02.2 - Demo SLOW stop ownership

Closed a lifecycle race in the Demo SLOW interaction. Press-release stop is
now retained as a job, repeated direction input waits for the prior release,
and disconnect cancels queued UI stop work before synchronously settling the
Demo owner lease. The disconnect path resets the logical SLOW snapshot without
allowing a stale stop coroutine to overwrite the disconnected status.

Verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelSessionMarkerTest --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL; the session-marker suite passed, including the Demo
SLOW disconnect regression. Live SLOW remains gated and no hardware operation
was exercised.


### WBS-03.1/02.2 - Queued emergency-stop status ownership

Made the common STOP ALL action session-aware at dispatch. It captures the
session that owned the motion request and drops a queued action after
disconnect, preventing the reset MotionSupervisor from producing a misleading
"No active motion owner" or emergency completion message. The disconnect path
continues to own synchronous lease cleanup.

Verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelSessionMarkerTest --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL; the session-marker suite passed, including the
queued STOP ALL regression. No live motion command was exercised.


### WBS-03.1/02.2 - Queued fast-jog release ownership

Closed the fast-jog press-release teardown race. The UI no longer clears the
fast-jog lease before its asynchronous release runs. The lease remains visible
so disconnect can synchronously settle it if the user leaves the session before
the queued release executes. The release also checks session ownership before
using the transport and before publishing status, preventing an old-session
release from acting on a replacement connection.

Verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelFastJogTest --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL; the fast-jog suite passed, including the queued
release/disconnect regression. No live motion command was exercised.


### WBS-03.1/02.2 - Queued GoTo cancellation ownership

Closed the pre-lease GoTo teardown gap. Disconnect now always cancels the
arrival-aware GoTo coroutine and clears its UI state, including the case where
the coroutine was still queued before it acquired MotionSupervisor ownership.
Cancel Slew captures the owning session before dispatch, refuses an old-session
action after teardown, and rechecks ownership before publishing its terminal
status.

Verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelSessionMarkerTest --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL; 43 session-marker tests passed, including the queued
GoTo/cancel/disconnect regression. No live motion command was exercised.


### WBS-06.3/08.2 - Astro WebUI programme write-session ownership

Made Astro WebUI programme start and control actions session-owned. Each action
captures the MountSession that admitted it, checks ownership before dispatch and
again before applying the HTTP result. A late result after disconnect is dropped
instead of updating the replacement/idle ViewModel or triggering a stale
programme readback.

### WBS-04.3/02.2 - Astro WebUI capture-session ownership

Made source-aligned M/B dispatch and Bulb stop lifecycle-owned. They now have
dedicated jobs, are cancelled by disconnect, and recheck session ownership after
the HTTP bridge returns. The capture watchdog also refuses to mutate state
unless its original session still owns the ViewModel.

Verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiProgrammeStartTest --rerun-tasks --console=plain --no-daemon
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiCaptureTest --rerun-tasks --console=plain --no-daemon
~~~

Result: both focused suites passed, including cancellation-insensitive late
programme and capture result regressions. No live Astro WebUI or camera command
was exercised.


### WBS-05.1/07.1/08.2 - Astro WebUI read-session ownership

Made the Astro WebUI read surface lifecycle-owned. Status, solve, run-state,
programme, site and media list/item requests capture the MountSession that
admitted the read and check ownership after the HTTP client returns. Disconnect
or reconnect therefore cannot allow an old response to overwrite current UI
state. No-session media calls still report the existing actionable failure.

Verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiStatusTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiSolveTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiRunStateTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiSiteTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiProgrammeTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiMediaTest --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL; 17 focused tests passed, followed by the added
late-status regression in the status suite. No live Astro WebUI or camera
command was exercised.


### WBS-06.3/08.2 - Astro WebUI session-control ownership

Made Astro WebUI enter, leave and tracking writes lifecycle-owned. The action
captures the MountSession before dispatch, checks ownership before the request
and again before applying its result, so disconnect cannot revive Accepted or
Failed state or launch a stale status confirmation read.

Verification passed:

~~~text
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiSessionControlTest --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL; the session-control suite passed, including the
cancellation-insensitive late enter-result regression. Write routes remain
default-off pending named-head qualification. No live Astro WebUI command was
exercised.


### WBS-08.1/05.1 - Validated observer-site persistence

Completed the safe local half of Astro WebUI site parity. Observer latitude and
east-positive longitude are optional fields in the versioned user-preferences
record. The shared ViewModel restores only a complete, finite pair within
-90..90 and -180..180, and sends persistence callbacks only after edits produce
a complete valid pair. Desktop and Android continue to own the preference file;
the values are never written to the mount or the resident WebUI site endpoint.

Verification passed:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.session.UserPreferencesStoreTest :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelObserverLocationTest --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL; preference round-trip and observer-location tests
passed. No live Astro WebUI or hardware command was exercised.


### WBS-01.1/08.1 - Stable shared-shell ViewModel lifetime

The shared shell now remembers its internally constructed AppViewModel. This
keeps the desktop session owner, lifecycle jobs and observer-location
persistence callback stable across Compose recomposition while preserving the
Android host-supplied ViewModel path.

Verification is included in the focused preference/observer-location suite and
the subsequent full JVM, Android and desktop build. No transport or hardware
behavior changed.


### WBS-08.1/05.1 - Gated Astro WebUI site-save seam

Added the source-aligned resident Astro WebUI site mutation seam. The adapter
posts `lat` and `lon` to `/api/site` as
`application/x-www-form-urlencoded`, validates the JSON acknowledgement and
returned coordinate ranges, and uses the Astro WebUI preview port. The
ViewModel owns the write by MountSession, drops late results after disconnect,
and confirms an accepted write by re-reading the resident site. The feature
flag and UI action remain default-off because this mutates the resident Astro
site and still needs named-head persistence and recovery qualification.

Verification passed:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.AstroWebUiSiteControlTest --tests dev.openpolaris.core.config.FeatureFlagsTest :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiSiteTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiSiteWriteTest --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL; contract, feature-flag, read-site and site-write
ViewModel tests passed. No live Astro WebUI, mount or camera operation was
exercised.


### WBS-06.4/08.2 - Gated Astro WebUI solar programme

Added the first remaining Astro WebUI programme family beyond lapse, panorama,
HDR and focus. The solar adapter posts the source `sunset`, `interval`,
`start`, `end` and client-clock `now` fields to `/api/prog/sun`.
Preview and start are separate operations; start is never sent without
`confirm=1`. The shared mobile/desktop Mission surface provides sunrise or
sunset selection, Unix-time window editing, a “Now + 30 min” helper, explicit
preview-before-start and readback through resident programme status. The
existing default-off programme-start gate remains closed.

Verification passed:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.AstroWebUiSunProgrammeTest :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiSunTest --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL; source contract, form validation, preview/start
separation and session lifecycle tests passed. No live Astro WebUI, mount or
camera operation was exercised.


### WBS-06.4/08.2 - Gated Astro WebUI extended motion programmes

Added the remaining source-backed Astro WebUI motion-programme seam for PLC,
path-lapse and Holy Grail:

- PLC supports live-pose keyframe capture and clearing, photo interval/count,
  hold-final-pose, preview, and explicit `confirm=1` start.
- Path-lapse supports waypoint capture/clearing, per-leg frame count,
  interval, preview, and explicit `confirm=1` start.
- Holy Grail supports the source curve form, optional priority/ISO/aperture/
  shutter constraints, preview with `valid=true`, `preview=true` and
  inferred-frame evidence, explicit apply, disable and status. The status
  endpoint is accepted without assuming an `ok` field because that is how
  Astro WebUI serves it.
- The shared Mission UI uses one mobile/desktop surface, keeps write actions
  behind `astroWebUiProgrammeStarts`, requires preview before the second
  action, labels movement/camera risk, and preserves accepted-vs-physical
  completion semantics.

Verification passed:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.AstroWebUiMotionProgrammesTest :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiMotionProgrammesTest --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL; all four source-contract tests and all three
ViewModel lifecycle/default-gate tests passed. No live Astro WebUI, mount,
camera or physical SLOW command was exercised.


### WBS-05.4/08.2 - Bounded Astro WebUI guide telemetry

Added the source-backed guide surface:

- `GET /api/guide` is parsed into typed running/threshold/interval/drift
  state with strict numeric and boolean validation, a 64-point cap and a
  4096-character log cap.
- The Mission surface polls guide telemetry at the same two-second cadence as
  Astro status and labels it as guider evidence rather than physical correction
  confirmation.
- `POST /api/guide` uses the source `on=1|0` form and positive JSON
  acknowledgement parsing, but the switch remains behind a separate
  default-off `astroWebUiGuiding` gate. Safe readback is independently
  default-on through `astroWebUiGuideStatus`.
- Reads, writes, polling and disconnect are MountSession-owned; late guide
  results cannot update a replacement session.

Verification passed:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.AstroWebUiGuideTest --tests dev.openpolaris.core.config.FeatureFlagsTest :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiGuideTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL; parser, feature-policy, ViewModel lifecycle and
Compose compilation tests passed. No live guiding or mount command was
exercised.


### WBS-05.5/08.2 - Gated Astro WebUI autofocus progress

Added the source-backed autofocus seam:

- The adapter reads `/api/astro/autofocus` and bounds status, progress state,
  step counts, best-focus data, V-curve samples and error text.
- Start uses the source `confirm=1`, `steps`, `adj`, `settle` and
  `backlash` form; stop uses `stop=1`. HTTP acceptance is followed by
  resident progress readback rather than treated as focus completion.
- The Mission surface shows progress, sample count, best step/HFR and
  qualification messaging. Readback is default-on; start/stop are separately
  default-off pending camera attachment, preview, focus direction,
  capture/motion ownership and recovery evidence.
- ViewModel jobs are session-owned and polling is stopped with the Mission
  surface; late results cannot update a replacement session.

Verification passed:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.AstroWebUiAutofocusTest --tests dev.openpolaris.core.config.FeatureFlagsTest :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiAutofocusTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon
~~~

Result: BUILD SUCCESSFUL; parser, feature-policy, ViewModel lifecycle and
Compose compilation tests passed. No live focus, camera or mount command was
exercised.


- **2026-11-10 - WBS-05.5/08.2 verification:** full host build for the gated
  Astro autofocus slice passed: 103 actionable tasks, shared and Compose JVM
  tests, Android debug compilation/APK assembly, and desktop distributable
  creation. No live focus, camera, mount or physical SLOW qualification was
  performed. APK SHA-256 is
  `27aff09ddbedc760ffd6409fa022c8daaa1f16b5ee6a1554a6ad07dd6f2dca86`;
  desktop archive SHA-256 is
  `5f9de7375c7018ca9c4464c96ea4cc3848e8b4f6e6c733416a40985fbbdec38c`.


- **2026-11-10 - WBS-05.3/08.2 ACTIVE (Astro WebUI focal):** implemented
  source-aligned focal state and write forms, with bounded numeric validation,
  preview-port routing, a safe readback default, a separate write gate and
  disconnect-safe ViewModel ownership. Focused adapter, FeatureFlags,
  ViewModel lifecycle and Compose compilation tests passed. No live solve,
  camera or mount operation was exercised.


- **2026-11-10 - WBS-05.3/08.2 verification:** full host build for
  `3440bea` passed: 103 actionable tasks, shared and Compose JVM tests,
  Android debug compilation/APK assembly, and desktop distributable creation.
  APK SHA-256 is
  `0ff1161b4d04187202b9972c8dedb78e206f2300f9596b5cc3da86ca29948305`;
  desktop archive SHA-256 is
  `aa0023697a17bb49bfde95aa242fd9415fcfd880e24a709c6aa459ab83ff2d31`.
  No live Astro WebUI, solve, camera, mount or physical SLOW qualification
  was exercised.


- **2026-11-10 - WBS-05.1/08.2 ACTIVE (Astro WebUI travel limits):** added
  read-only status parsing, a default-on read gate, preview-port routing and
  disconnect-safe ViewModel/UI ownership. The surface distinguishes enforced,
  released and unknown rather than assuming a safe value; dangerous release
  remains unavailable. Focused parser, FeatureFlags, ViewModel lifecycle and
  Compose compilation tests passed. No live mount operation was exercised.


- **2026-11-10 - WBS-05.1/08.2 verification:** full host build for
  `26cbae0` passed: 103 actionable tasks, shared and Compose JVM tests,
  Android debug compilation/APK assembly, and desktop distributable creation.
  APK SHA-256 is
  `77f90e32f376c85cc3a78685cf78964beb04d756c7dc597f64e5fdae99b3cc9b`;
  desktop archive SHA-256 is
  `6dd0e84ce9e3861bdaccd9d1398e21f18964375625a1edc7d6093eaaa248f3c3`.
  No live Astro WebUI, travel-limit write, mount, camera or physical SLOW
  qualification was exercised.


- **2026-11-10 - WBS-02.3/08.2 ACTIVE (Astro keep-Wi-Fi):** implemented
  bounded readback and separately gated normal/force/off control forms on the
  shared connection surface. The ViewModel confirms accepted changes through
  readback and drops late results after disconnect. Focused parser,
  FeatureFlags, ViewModel lifecycle and Compose compilation tests passed. No
  live bridge or mount operation was exercised.


- **2026-11-10 - WBS-02.3/08.2 verification:** full host build for
  `2b13d40` passed: 103 actionable tasks, shared and Compose JVM tests,
  Android debug compilation/APK assembly, and desktop distributable creation.
  APK SHA-256 is
  `7d920dfb0191f40b5636c6aba93ab245905acb8c39e6620a50bc69e48e84d99b`;
  desktop archive SHA-256 is
  `e23629ec3f4cf61537f98bd24f3cc1dede099080fdf1d1239d200a1a659c0b41`.
  No live Astro WebUI, bridge, Bluetooth, mount, camera or physical SLOW
  qualification was exercised.


- **2026-11-10 - WBS-02.1/08.2 ACTIVE (Astro Wi-Fi status):** implemented
  source-aligned readback with response caps, safe host/preview-port routing,
  default-on policy and disconnect-safe ViewModel ownership. Focused parser,
  FeatureFlags, ViewModel lifecycle and Compose compilation tests passed. No
  live Wi-Fi or Bluetooth operation was exercised.


- **2026-11-10 - WBS-02.1/08.2 verification:** full host build for
  `69ca50b` passed: 103 actionable tasks, shared and Compose JVM tests,
  Android debug compilation/APK assembly, and desktop distributable creation.
  APK SHA-256 is
  `53f82599dedfe9c690fe53dd711e5deb87ae08ddfa3e1f7e81cf428275b9f8b4`;
  desktop archive SHA-256 is
  `e411b4d14a2ac8fcdda7e1ea40ff1d678e032f5e984b7ef728a981d11e63fa4f`.
  No live Astro WebUI, Wi-Fi, bridge, Bluetooth, mount, camera or physical
  SLOW qualification was exercised.


- **2026-11-10 - WBS-05.6/08.2 ACTIVE (Astro target controls):** implemented typed `/api/astro/align` and `/api/astro/goto` clients with source-aligned form bodies, bounded RA/Dec/name validation, positive acknowledgement evidence and explicit preview/confirm separation. Mission exposes preview, exact-preview confirmation and compass alignment with physical motion warnings; ViewModel jobs are session-owned and late results are discarded after disconnect. Target writes remain default-off.
- **2026-11-10 - verification:** focused adapter, feature-policy, ViewModel lifecycle and Compose compilation tests passed after correcting RA validation to the source-aligned `[0,360)` range. No live Astro WebUI, mount or physical SLOW qualification was performed.


- **2026-11-10 - WBS-05.3/08.2 ACTIVE (Astro solve controls):** implemented typed `/api/solve`, `/api/cancel` and `/api/apply` clients with fixed source query modes, strict started/busy/cancelled/applied evidence, preview-port routing, session-owned ViewModel jobs and visible default-off controls. Apply is explicitly described as an alignment-affecting action and does not imply mount success; resident `/api/state` and solve readback remain authoritative.
- **2026-11-10 - verification:** focused adapter, feature-policy, ViewModel lifecycle and Compose compilation tests passed. No live Astro WebUI, solver, camera, mount or physical SLOW qualification was performed.


- **2026-11-10 - WBS-02.1/02.3/08.2 ACTIVE (Astro link telemetry):** implemented a bounded `/api/link` read client with strict phase/counter/age validation, dynamic numeric opcode parsing capped at 256 entries, dedicated preview-port routing and session-safe ViewModel refresh. The Connection workspace shows counters, last error and a small opcode snapshot without offering raw link-send or motion bypasses.
- **2026-11-10 - verification:** focused parser, feature-policy, ViewModel lifecycle and Compose compilation tests passed. No live Astro WebUI, Bluetooth, bridge, mount or physical SLOW qualification was performed.


- **2026-11-10 - WBS-05.2/09.1 ACTIVE (mobile compass rehearsal):** implemented a lifecycle-stable Android rotation-vector source injected through the common app shell. Mission exposes Read phone compass and Use reading actions while preserving manual input, local-only state and the default-off Astro alignment write gate; desktop has an explicit unavailable/manual path.
- **2026-11-10 - verification:** Compose JVM tests, Compose JVM compilation and Android debug Kotlin compilation passed. No physical compass accuracy or mount alignment was exercised.


- **2026-11-10 - WBS-03.6/09.1 ACTIVE (SLOW rehearsal calibration UX):** the Motion surface now shows the selected host reference level, its two source-aligned planner rungs and an explicit warning that these values are not a live degrees-per-second contract. Hold controls now expose a button role plus Held/Released state to keyboard and accessibility clients. No live transport, 532/533/534 frame or AC 4A path was enabled.
- **2026-11-10 - verification:** focused Compose compilation and `SlowMotionPaneSafetyContractTest` passed; the full cross-platform build passed on rerun after one isolated timing-sensitive `SessionShutdownLeakTest` failure at cycle 3. No physical SLOW qualification was exercised.


- **2026-11-10 - WBS-03.6/09.1 ACTIVE (SLOW PC keyboard guidance):** added a collapsible keyboard-shortcut explanation to the shared Motion dock and extended the SLOW safety contract test for the shortcut surface, held/released state semantics and button role. This is UX rehearsal only; live transport remains hard-gated.
- **2026-11-10 - verification:** focused SLOW Compose compile/test and the full shared/Compose/Android/desktop build passed. No live hardware, Bluetooth, camera or SLOW qualification was exercised.



### WBS-04.3/06.1/08.2 — Gated Astro WebUI in-head capture

Commit 5f05872 adds the source-specific /api/astro/capture path as a
separate capability from the already guarded M/B /api/link/send path.

- AstroWebUiAstroCaptureParameters rejects unsafe interval, shot and bulb
  combinations before transport; blank shots are encoded as the source's
  unlimited form.
- The client requires the source confirm=1 acknowledgement and preserves
  resident programme status as the authority after HTTP acceptance.
- The ViewModel owns the request by mount session, drops late results after
  disconnect, and refreshes /api/prog without claiming a completed capture.
- Mission uses a two-step arm/start action and displays the source warning
  about unreliable externally triggered Astro capture. The capability is
  default-off because it can move the mount and fire the shutter.

Focused verification passed:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.AstroWebUiAstroCaptureTest --tests dev.openpolaris.core.config.FeatureFlagsTest :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiAstroCaptureTest --tests dev.openpolaris.ui.AstroWebUiProgrammeComposerContractTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon --quiet
~~~

No live Astro WebUI, camera, mount, Bluetooth or physical SLOW operation was
exercised.



### WBS-06.1/08.2 — Astro WebUI panorama interval control

Commit bc0b782 adds the source /api/prog/pano/interval route to the existing
programme-control seam.

- The shared adapter validates 0..3600 seconds before transport and sends the
  exact seconds form field to the dedicated WebUI port.
- The Mission surface shows the field only for an observed panorama and only
  inside the existing default-off programme-write gate.
- The ViewModel keeps the action session-owned and reports HTTP acceptance as a
  request; /api/prog polling remains authoritative.

Focused verification passed:

~~~text
./gradlew :shared:jvmTest --tests dev.openpolaris.core.domain.AstroWebUiProgrammeControlTest :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelAstroWebUiProgrammeControlTest --tests dev.openpolaris.ui.AstroWebUiProgrammeComposerContractTest :composeApp:compileKotlinJvm --rerun-tasks --console=plain --no-daemon --quiet
~~~

No live Astro WebUI or mount operation was exercised.



### WBS-04.3/06.1/09.1 — Astro capture arm safety hardening

Commit b016a1e aligns the source-specific Astro capture form with the source
WebUI's 5000-frame maximum and automatically clears the two-step arm after
four seconds. Parameter edits continue to clear the arm immediately. This
reduces the chance of a stale confirmation being used in a dark field session;
the capability remains default-off and no physical action is enabled.

Focused and full verification passed. No live Astro WebUI, camera, mount,
Bluetooth or physical SLOW operation was exercised.



### WBS-03.7/03.5 — Fine-rate cancellation truth

Commit e1ab877 corrects a safety boundary in SafeRateAdjustController:
CancellationException is rethrown from both rate apply and rate stop paths.
Deliberate teardown can therefore reach the session owner and explicit recovery
boundary without being mislabeled as a device-level UNKNOWN acknowledgement.

The controller remains unqualified unless its capability identity, units,
bounds, tracking context, ownership and physical stop evidence are supplied.
No live rate transport was enabled.

Focused and full cross-platform verification passed.


### WBS-03.7/03.5 — Fine-rate solve-evidence gate

Commit 64d0460 extends the existing SafeRateAdjustController boundary with explicit plate-solve evidence:

- correction requires solve age, confidence and field rotation values;
- default policy rejects solves older than 5000 ms, below 0.6 confidence or beyond 5 degrees of field rotation;
- absent, negative, non-finite and otherwise unsafe evidence is rejected before the transport is called;
- context changes while active transition the correction to Stale, requiring explicit stop/recovery;
- these values are policy defaults for safe integration and are not claims of hardware qualification.

Focused fine-rate tests and the full cross-platform build passed. No live solve, Bluetooth, mount or physical SLOW qualification was performed.


### WBS-03.6/03.7/09.1 — Fine-rate readiness UX

Commit a101008 adds a read-only readiness checklist to the shared Motion pane. The checklist makes the future fine-rate contract legible on mobile and PC:

- qualified firmware, route and physical stop;
- connected mount, confirmed tracking, half-speed disabled and exclusive motion owner;
- a local plate solve less than five seconds old with at least 0.60 confidence;
- field rotation within five degrees, currently shown as missing because the app has no rotation-bearing solve evidence.

Solve age is refreshed once per second while evidence is present. The card explicitly says the live rate transport is disabled and that the thresholds are policy defaults rather than hardware qualification. No control path was enabled.


### WBS-04.3/06.1/09.1 — Unknown capture outcome UX

Commit d9c8a4f closes a retry affordance gap in the Camera workspace. When the shared capture lifecycle reaches OutcomeUnknown, every new release surface is disabled:

- ordinary M capture;
- native Bulb capture;
- feature-gated Astro WebUI M/B capture;
- new client-driven intervalometer sequences.

The UI labels the result as unknown, tells the operator not to retry the shutter, and directs inspection of the camera/card followed by reconnect. This preserves the asynchronous Benro Connect lifecycle contract: HTTP or command acceptance is not completion, and missing lifecycle/file evidence is never silently retriggered.


### WBS-02.3/08.2 — Typed Bluetooth wake evidence

Commit ff427fd adds a shared BluetoothWakeState with explicit requested, discovering, connecting-GATT, succeeded, failed and cancelled phases. AppViewModel now updates it from the standalone Benro-style GATT wake pulse, the PC bridge progress callback, the mobile host status callback and disconnect cancellation.

The Connection workspace renders the phase and detail with a durable explanation: Bluetooth wakes the mount but is not the control socket, preview service or camera lifecycle. EndpointHealth remains authoritative for route/control/preview readiness.


### WBS-03.6/03.7/09.1 — Simulator fine-rate rehearsal

Commit `ea3436c` adds a Demo-only fine-rate interaction to make the safety contract exercisable on both mobile and PC without implying hardware support:

- Demo preparation arms synthetic evidence only; the real plate-solve cache is not overwritten.
- Tracking, half-speed, exclusive ownership and the existing solve-policy thresholds remain visible gates.
- A logical request uses the selected axis and ±0.1°/s target, enters `SafeRateAdjustController`, and acquires the shared `TRACKING_CORRECTION` owner.
- SLOW and fast-jog paths cannot acquire while fine correction owns motion.
- STOP, tracking stop, half-speed enable, emergency stop and disconnect provide explicit teardown; ambiguous outcomes remain visible and are not retried.
- The simulator transport has no `MountSession` and emits no 532/533/534 or AC 4A frame.

This is a rehearsal of UI/state/ownership behavior, not a live rate qualification.


### WBS-03.5/03.6 — Qualification-gated SLOW runtime seam

Commit `570ac1a` wires `QualifiedSlowMotionTransport` into the common SLOW controller without weakening the gate. The adapter dispatches source-aligned frames through the session's serialized `sendOnly` path only when the immutable capability record is live-ready; otherwise the existing Demo-only logical transport remains selected. Shared `MotionSupervisor` stop handling now selects the same transport, preserving release, emergency and disconnect boundaries.

This makes the runtime route installable from one evidence-backed capability record while retaining the current hard-disabled live UX.


### WBS-03.5/03.6 — Evidence-gated qualified SLOW path

Commit `96f8eac` removes the permanent `false` live branch from the Motion pane without weakening safety. Live availability now requires both the feature flag and `SlowMotionCapabilities.current.liveReady`; the current immutable record remains unqualified. The AppViewModel's SLOW controller selects the no-wire Demo transport in Demo mode and the existing `QualifiedSlowMotionTransport` otherwise. Pointer/key release, lease renewal, emergency stop, disconnect and owner arbitration are shared by both paths.

This is an integration improvement, not a qualification claim: a named firmware/gimbal/source-app record with route, units, signs, envelope, acknowledgement, watchdog, physical stop, disconnect recovery and limits evidence is still required before any live frame can be sent.


### WBS-03.5/03.6 — Single-source SLOW evidence provenance

Commit `d5c9ff4` injects the SLOW capability record into AppViewModel. Production still defaults to the immutable unqualified record, while tests or a future release can supply one exact evidence-backed record. The Motion pane, `QualifiedSlowMotionTransport`, live readiness check and support-bundle snapshot now read that same object. This prevents a future qualified transport from being enabled in the runtime while the UI or diagnostics report a different record.

No live capability was promoted.

### WBS-02.1/02.3/08.1 - Explicit AP/LAN connection intent

Commit `2282c60` adds a persisted Direct AP/Home Wi-Fi (LAN) mode to the Connection workspace. The selected mode changes endpoint identity and UI guidance, but never changes host networking implicitly. Direct-AP-only wake, Wi-Fi picker and desktop bridge actions are disabled in LAN mode; ordinary Connect remains available against the user-entered station host and port. Bluetooth wake failures now map to the typed FAILED evidence state while Wi-Fi discovery continues independently.

Focused and full cross-platform verification passed. This remains package/source-contract evidence: station listener reachability, phone Bluetooth GATT wake, live mount, camera, Astro WebUI and physical SLOW qualification are still required.

### WBS-09.2/09.1 - Field battery awareness

Commit `e03753b` adds a shared advisory battery classifier with explicit unknown, critical, low and normal states. The status strip now renders a visible Battery label, severity colour and accessible state description including charging. Invalid readbacks become unknown; no connection, capture or motion path is gated by this advisory value.

Focused and full cross-platform verification passed. The APK remains package/simulator evidence; live battery telemetry and all hardware qualification gates remain open.

### WBS-02.2/09.1 - Android session-owner lifetime

Commit `8672e9d` fixes the Android host's recomposition boundary: `remember` now retains the single AppViewModel for the Activity composition, while `onResume`, the Wi-Fi scan flow and the shared shell continue to use that same instance. The previous direct constructor could replace a live session during any state-driven recomposition.

Focused and full cross-platform verification passed. This closes a lifecycle defect in source and package evidence; it does not claim live hardware qualification.

### WBS-10.3 - Rolling phone-test checksum provenance

Commit `9d24d06` fixes the rolling release evidence contract. The published `apk-sha256.txt` now hashes the APK artifact itself; `apk-signer-sha256.txt` separately identifies the release certificate. `RollingReleaseWorkflowContractTest` prevents the previous signer/artifact confusion from returning.

The workflow still requires the protected release keystore secrets and an external push to `main`; no GitHub job or phone installation was claimed from this local test.

### WBS-02.2/09.1 - Android rotation-safe session owner

Commit `646fbfd` moves Android session retention from a Compose `remember` boundary to an Activity-retained `ViewModelProvider` owner. The shared AppViewModel and scope survive configuration changes; `onCleared()` disconnects the mount and cancels the retained scope. The Android host still supplies the same shared VM to the common UI and keeps protocol logic platform-neutral.

Focused and full cross-platform verification passed. This is source/package evidence; live rotation and hardware qualification still require a phone and named mount.
### WBS-04.3/08.2 - Astro WebUI shutter acknowledgement hardening

Commit 2ec31f1 replaces the Astro WebUI capture adapter's substring checks with bounded strict JSON parsing. M mode, B mode and the explicit B stop only return bridge-queued evidence for a real ok=true and exact numeric cmd=264; malformed, wrong-command, string-typed and oversized responses are rejected without retry. The existing lifecycle plus file-publication gate remains the only completion contract.

Focused tests and the full cross-platform build passed. The adapter is source-aligned with the Benro Connect state/bulb/c forms, but named-camera/firmware physical lifecycle evidence is still required.
### WBS-04.1 - Auxiliary camera capability readback

Commit 99c1fb5 adds a session-owned, explicit read-only refresh for the source-aligned task-3 camera values: image format, control mode, exposure time and interval type. The Camera workspace shows their values and age, while the simulator and ownership tests exercise the exact query shapes. No write route was opened, and a disconnect cancels the refresh before it can publish into a replacement session.

Focused and full cross-platform verification passed. Physical camera behaviour and M/B capture evidence remain open.


### WBS-01.3/09.1 - Compact mobile navigation and release cadence

Commit f61de35 replaces the phone NavigationBar icon-and-label stack with a
single 48 dp text-only destination row. Mission, Control, Camera, Astro and
More retain stable test tags, selected-state semantics and the same overflow
actions, while the phone shell uses 4 dp outer/section spacing to recover
workspace height. The desktop rail is unchanged.

The rolling GitHub release remains available on pushes to main and by manual
dispatch, but the scheduled weekly trigger was removed at the user's request.
This keeps phone-test releases tied to deliberate changes or an explicit
dispatch.

Focused contract tests and the full cross-platform build passed. Android debug
APK SHA-256: 0e326d22cba2f340992a0119ed27011ced5ac9ffce9dde05c74fdc188cb9454d.
