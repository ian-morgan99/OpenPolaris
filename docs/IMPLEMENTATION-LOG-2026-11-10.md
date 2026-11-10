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
