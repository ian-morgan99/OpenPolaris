# VR view for the camera preview (closes issue #1)

> Issue: https://github.com/ian-morgan99/OpenPolaris/issues/1
> "Small screens are hard to read in the field" —
> request: immersive VR headset mode for full visualisation of the
> phone screen in a Cardboard-class viewer.

## What is it

A landscape, full-screen, stereoscopic view of the live camera preview.
Designed to be slipped into a Cardboard-class viewer (Google Cardboard,
Gear VR, Quest in phone-in-headset mode, etc) so you can frame a target
while staying warm inside.

Tap anywhere on the screen to exit. The system back button also exits.

## How to launch it

1. Connect to the Polaris Wi-Fi (host `192.168.43.1` by default).
2. Open the rail at the top of the main screen.
3. Tap **VR**.

The activity locks landscape, hides the system bars, and starts streaming
MJPEG from the mount's `:8080` endpoint into the GL view.

## How it works

- One full-screen texture, drawn twice — once per eye, half-width each.
- A lightweight vertex-shader pass applies a Cardboard v1 barrel
  distortion and an in-plane pan driven by the device's
  `TYPE_ROTATION_VECTOR` sensor. Yaw/pitch give a "look around" feel
  without a true 6-DoF scene.
- A short on-screen hint — *"Tilt your head to recenter — tap to exit"* —
  appears for two seconds on entry, then fades out. It comes back
  automatically the next time you enter the activity.

## What is **not** included

- No full 6-DoF head tracking. This is a "look around the frame"
  approximation, not a portal.
- No stereoscopic 3D — the left/right views share the same camera
  frame, offset only by the inter-pupillary distance. A real 3D
  experience would need a stereo camera or two separate feeds.
- No focus / IPD adjustment. We use the public Cardboard v1 viewer
  profile (FoV ≈ 60°, IPD ≈ 6.4 cm) as a sensible default.

## Tested on

### Code-level (CI)

- `shared:jvmTest` — `VrStereoShadersTest` cross-checks the GLSL barrel
  distortion and pan math against `CardboardWarp.kt` (Kotlin reference).
  All four cases pass.
- `:cli-probe:test` — `cli-probe --fake` boots `FakeMount`, exercises the
  preview pipeline, and exits 0.
- All 94 JVM tests pass.

### Device-level (awaiting owner)

- **Hardware:** the owner's device (TBD — will be added in a follow-up
  comment on issue #1 once a field test is run).
- **Android version:** TBD.
- **Viewer:** the owner is testing a Cardboard-class viewer; specifics
  will be added in a follow-up comment.

## Screenshot

The owner will attach a screenshot in a follow-up comment on issue #1.
Until then, the doc above describes the entry point, the in-VR HUD
(crosshair, status banner, connection-loss banner), and the recenter
hint.

## Status

- This doc is the source of truth for the VR close-out comment.
- The GitHub close-out comment lives on issue #1 and links here.
- VR slice 1 (crosshair + status HUD + connection-loss banner) shipped
  in commit `13d9cd0`. VR slice 2 (plate-solve target marker) is the
  next item and is tracked in `vr-issue-1` follow-up.

## Source

- `androidApp/.../VRActivity.kt` — activity, sensor wiring, GL setup.
- `shared/.../VrStereoShaders.kt` — vertex + fragment shader source
  (in `commonMain` so the constants are JVM-testable).
- `shared/.../CardboardWarp.kt` — Kotlin-side reference for the same
  pan / clamp math; the GLSL is cross-checked against it by
  `VrStereoShadersTest`.
