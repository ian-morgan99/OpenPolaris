# Protocol code conflict: two camera maps for the same numbers

**Date:** 2026-10-07
**Status:** OPEN — unresolved contradiction inside the canonical source. Blocks
any claim that a camera code is "verified".
**Affects:** `shared/src/commonMain/kotlin/dev/openpolaris/core/protocol/Codes.kt`,
`CommandTable.kt`, and every downstream document that quotes a camera code.

## The problem

`Codes.kt` defines **two different camera code maps in the same file**, and they
disagree about what the numbers 258–279 mean.

`Codes.BenroCamera` (line ~39, comment: *"Canonical Benro Connect camera-setting
map (v3.0.30 build 240930)"*):

```
258 SET_ISO   259 SET_WB   260 SET_EV   261 SET_SHUTTER   262 SET_FOCUS
263 SET_VIDEO_RECORD_STATUS   264 SET_PHOTO_RECORD_STATUS
265 GET_ISO_INFO  266 GET_WB_INFO  267 GET_EV_INFO  268 GET_SHUTTER_INFO
275 GET_FNUM_INFO  276 SET_FNUM  277 SUN_SHOT
```

Top-level `Codes.CAM_*` (line ~163, comment: *"Ground-truth payload keys from
polestar_app strings"*):

```
258 CAM_GET_ISO  259 CAM_SET_ISO  260 CAM_GET_WB  261 CAM_SET_WB
262 CAM_GET_FNUM 263 CAM_SET_FNUM 264 CAM_GET_EV / CAM_CAPTURE
266 CAM_GET_STATE 268 CAM_GET_FOCUS 276 CAM_GET_SHUTTER 277 CAM_SET_SHUTTER
```

Both are referenced from production code — 33 uses of `BenroCamera.*` and 84 of
`Codes.CAM_*`. This is not a stale constant; both are live.

## The device evidence decides it

`pgphoto`'s dispatcher is `camera_info_update_with_message` @ `0x16dac`, jump
table `0x16e38`, indexed `code - 258`. Decoding that table, and cross-checking
against the Benro Connect app's own traffic in `Mlog`, gives:

| code | handler | meaning | observed payload |
|---:|---|---|---|
| 258 | `setCameraConfig(0,…)` | ISO set | `iso:6;` → `ret:0` |
| 259 | `setCameraConfig(1,…)` | aperture-ish | unused by the app |
| 260 | `setCameraConfig(2,…)` | EV set | `ev:15;` → `ret:0` |
| 261 | shutter set | **shutter** | `s:<index>;` → `ret:0` |
| 262 | focus mode | focus | `mod:1;f:6;` → `ret:0` |
| 264 | capture | capture | `state:1;bulb:N;c:-1;` |
| 265/266/267/268/275 | `getCameraConfig` | ISO/WB/EV/**shutter**/f lists | `RD:0;V:33;R:1/8000,…` |
| **277** | `camera_set_aperture` | **aperture, not shutter** | never sent by the app |

Evidence: `docs/evidence/bulb-root-cause-20261005/SUMMARY.md` §3 in
`ian-morgan99/benro-polaris-firmware-patcher`, including a live reproduction
`TX 1&261&2&s:44;#` → `RX 261@s:44;ret:0;`.

**Conclusion:** `Codes.BenroCamera` matches the device. The top-level
`Codes.CAM_*` camera block does not, and its names are actively misleading —
`CAM_SET_SHUTTER = 277` is the aperture setter, and `CAM_GET_SHUTTER = 276` is
not the shutter list (268 is).

This was already found once. `PROTOCOL-CODE-AUDIT-2026-08-31.md` tabulated the
same mismatch and recorded *"inferred catalog needs rewrite"*. `BenroCamera` was
added as the corrected map, but the incorrect one was never removed and is still
used 84 times. `USER-MANUAL.md` and `OPENPOLARIS-PARITY-2026-09-03.md` still
publish the wrong pairing (276/277 as shutter get/set).

## Why this matters beyond naming

The canary historically sent **277 with `shutter:`** — an aperture setter with a
shutter payload. A `ret:0` from that exchange looks like success and is not:
the shutter was never set. Any test that "sets shutter" via 277 and then judges
the shot by file existence has been measuring the wrong thing. This is the
mechanism behind the long-running "shutter does not stick" class of report and
it is a client bug, not a camera one.

## Required fix

1. Delete or clearly quarantine the camera block of top-level `Codes.CAM_*`
   (258–279). Keep the gimbal (513–549), file (770–798), system and app blocks —
   those were verified live and are not in dispute.
2. Migrate the 84 `Codes.CAM_*` camera references to `Codes.BenroCamera.*`.
   `CAM_CAPTURE`/`CAM_CAPTURE_SUBTYPE` need care: capture is 264 **with subtype
   4**, and 264 is also the EV read — subtype disambiguates, and that is the one
   genuinely load-bearing detail in the whole block.
3. Correct `USER-MANUAL.md` and `OPENPOLARIS-PARITY-2026-09-03.md` in the same
   change, or they become the next source of the same error.
4. Re-run the code audit against the decompile and attach the transcript.

## Timing, which is the other half of every interaction

Code numbers alone do not describe an interaction. The reference client's
behaviour (see `libgphoto2/docs/pentax/REFERENCE_CLIENTS.md`) sets the floor:

- Reads and transfers are serialized under one lock; nothing overlaps on the bus.
- Status polling is a **self-rearming one-shot**: disarm, read, transfer, then
  re-arm after 100 ms. The period is 100 ms plus the work, not a fixed 100 ms.
- A long exposure is ended by a **second release command** after N one-second
  ticks, not by a duration written into a shutter property.
- Readback after a set is polled, not assumed. Our own canary honours this with
  `SHUTTER_SETTLE_TIMEOUT_S = 30.0` in `scripts/canary-probe.py`.

Any corrected code table must be published with the settle/poll behaviour that
goes with it, or the next agent will reintroduce the fire-and-forget bug that
made 277 look harmless.
