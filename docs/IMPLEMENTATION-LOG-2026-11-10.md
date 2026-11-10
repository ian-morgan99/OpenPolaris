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
