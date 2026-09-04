# Learning — VR mode emulator testing limits

## Context
While testing VRActivity (head-tracked stereo viewer for OpenPolaris),
I discovered two distinct things on Android emulator 1080x2340:

1. **`TYPE_ROTATION_VECTOR` cannot be injected from the emulator
   console.** The `adb emu sensor` console port exposes `acceleration`,
   `gyroscope`, `magnetic-field`, and `orientation` only. Trying
   `rotation-vector`, `rotation_vector`, `rotvec`, or
   `game-rotation-vector` returns "unknown sensor name". The
   `TYPE_ROTATION_VECTOR` is fused in the framework from
   accel/gyro/mag, but on emulator the fusion is not driven by the
   orientation console command, so head-tracking cannot be tested on
   emulator.

2. **The GLSurfaceView full-screens, so to confirm the VR view is
   actually rendering, look for EGL_emulation in logcat at
   `--pid=$app_pid`.** Two render threads at ~60Hz
   (`avg=2.8ms count=60`) means the GLSurfaceView is alive and
   pumping frames; the absence of that pattern means the activity
   crashed or the surface was not created.

3. **VRActivity tap-to-exit was broken.** The click listener was on a
   FrameLayout that was a sibling of the full-screen GLSurfaceView;
   the GLSurfaceView consumed touch events, so the FrameLayout's
   OnClickListener never fired. Fix: put `setOnClickListener { finish() }`
   directly on the GLSurfaceView and set the overlay TextView
   `isClickable = false`. Verified end-to-end: launching the activity
   and tapping the screen centre now returns to the launcher.

## Takeaway
- VR / 3D / Cardboard / sensor-fusion features need a real device to
  test. The emulator gives you a window into layout and lifecycle, but
  not into head-tracking.
- For "tap to exit" / "tap to select" patterns over a GLSurfaceView,
  attach the click listener to the GLSurfaceView itself, not to a
  sibling FrameLayout. The GLSurfaceView absorbs touch dispatch even
  with `isClickable=false`.
- After fixing a runtime defect that was discovered by testing, verify
  the fix on the actual install path (rebuild + `adb install -r` +
  re-launch + real tap), not just on the dev process.
