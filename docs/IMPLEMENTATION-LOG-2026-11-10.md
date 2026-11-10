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
