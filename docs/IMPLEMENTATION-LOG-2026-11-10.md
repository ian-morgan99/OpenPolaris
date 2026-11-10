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
