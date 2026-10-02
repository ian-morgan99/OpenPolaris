# Bulb and K-1 II support plan

## Current position

- OpenPolaris now has a Manual-pane Bulb duration control. It sends the existing
  Benro-compatible `264` payload with `bulb:<seconds>` and sets the exposure
  time first through the existing controller path.
- The current K-3 III evidence shows normal capture completing through the
  firmware/libgphoto2 stack. Bulb still needs a live test through this control.
- K-1 II support is not qualified. The decisive USB identity is `25fb:0183`
  in PTP/control mode. `0182` is storage mode and must not be treated as a
  camera-control failure.

## Bulb work

1. Run 5 s, 30 s, and a longer exposure through the new OpenPolaris control.
2. Save the matching `Mlog`, archived `Mlog_NNNNNN.log`, and
   `Clog_NNNNNN.log` for every attempt.
3. Correlate the request with `264` states, file event `773`, downloaded files,
   and the final idle state. Do not treat the UI spinner as capture evidence.
4. If `264` reaches the firmware but completion is absent, isolate whether the
   missing event is firmware/libgphoto2 or OpenPolaris correlation. If the
   image and lifecycle events exist but the UI remains busy, fix OpenPolaris
   state handling only.
5. Add a regression test for the exact observed event sequence before claiming
   Bulb support.

## K-1 II work

1. Attach the K-1 II in PTP/PC-P mode and record `lsusb`, `286`, and the
   camera-init trace. Do not test from `0182` storage mode.
2. Run the direct exact-SHA libgphoto2 path first: summary, configuration
   reads, normal capture, then Bulb. Keep this evidence separate from Polaris
   and OpenPolaris evidence.
3. Repeat through Polaris with the same camera and capture the archived logs.
4. Compare the first divergent PTP operation. Route the fix to libgphoto2 if
   direct K-1 II fails; route it to the patcher/Stage-2 path if direct works
   but Polaris fails; route it to OpenPolaris if Polaris works but the app
   fails.
5. Add K-1 II-specific capability handling only after the USB identity and
   direct protocol path are proven. Do not inherit K-3 III assumptions.
6. Qualify reconnect, normal capture, Bulb, RAW, RAW+JPEG, and two-shot
   completion separately.

## Release gate

No K-1 II or Bulb support claim is complete until the source commit, package,
installed runtime identity, archived logs, and physical capture evidence all
agree. The existing unrelated evidence directory remains untouched.
