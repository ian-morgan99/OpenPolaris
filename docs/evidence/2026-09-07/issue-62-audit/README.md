# OpenPolaris #62 audit — camera command map is 11% correct

**Date**: 2026-09-07 14:50
**Author**: this agent (OpenPolaris session)
**Subject**: OpenPolaris #62 — verify the camera command map
against the Benro Connect APK ground truth.

## The finding

The OpenPolaris `Codes.kt` camera/file/system codes (258-825) are
**89% wrong or missing** versus the actual Benro Connect
constants extracted from `BenroConnect_1727595281455.apk` v3.0.30.

| Category | Total | Correct | Wrong | Missing |
|---|---|---|---|---|
| Camera 258-282 | 25 | 4 (16%) | 14 (56%) | 7 (28%) |
| File/system 770-825 | 56 | 5 (9%) | 19 (34%) | 32 (57%) |
| **Total** | **81** | **9 (11%)** | **33 (41%)** | **39 (48%)** |

The only correctly-mapped codes in the camera range are:
- 286 (CAM_INFO = SP_CAMERA_INFO) — correct
- 291/292 (LIVEVIEW SET/GET) — correct
- 311 (CAM_FOCUS = SP_SET_FOCUS_ADJ) — correct

The only correctly-mapped codes in the file range are:
- 775 (SD_STATUS = SP_GET_SD_INFO) — correct
- 778 (BATTERY_STATUS = SP_GET_BAT_STATE) — correct
- 780 (DEVICE_INFO = SP_GET_DEVICE_VERSION) — semantically correct
- 796 (FILE_CAM_RAW = SP_GET_ISP_CFG_FILE) — semantically correct
- 799 (GET_CELLULAR_STATE) — correct

## The most dangerous finding

**Code 789 = FILE_DELETE_ALL is NOT in the Benro Connect APK at
all.** It only exists in our OpenPolaris Codes.kt. This is a
**synthetic code we created**, not something the Benro firmware
or app ever sends.

When OpenPolaris sends `1&789&2&path:normal/#` over the wire,
the firmware receives an unknown code but still parses the
payload (`path:normal`) and **executes a destructive file
delete with no Mlog/Clog entry**.

This is a **firmware-side feature that Benro Connect does not
use** — they have no UI for it. The "destructive file delete"
function is built into the firmware but never invoked by the
official app.

This is filed in OpenPolaris #64 with a full reproduction
trace.

## How to reproduce

```sh
# Decompile the Benro Connect APK and dump PolarisCMD
python3 docs/evidence/2026-09-07/issue-62-audit/extract-polaris-cmd.py
# Output: 121 SP_ constants sorted by numeric value
```

The script uses androguard 4.1.4 (already installed). The
extracted ground truth is at:
- `polaris-cmd-extracted.txt` (the 121 raw constants)
- `CAMERA-MAP-COMPARE.md` (the per-code comparison)

## Why this happened

OpenPolaris's Codes.kt was built **sequentially** (258, 259,
260... assuming GET/SET pairs). The Benro firmware does NOT
expose a clean sequential map. The 258-282 range interleaves:
- ISO/WB/EV/SHUTTER/FOCUS setters (258-262)
- VIDEO_RECORD_STATUS (263)
- PHOTO_RECORD_STATUS (264, also used for capture with subtype 4)
- ISO/WB/EV/SHUTTER getters (265-268)
- FOCUS_STACK (270), PANORAMIC (271), DELAY_SHOT (272)
- FNUM info (275/276), SUN_SHOT (277)
- HDR (280), IMG_FORMAT (282), PLC (283)
- CAMERA_INFO (286), CAMERA_PREVIEW (291/292)
- CONTROL_MODE (296/297), EX_TIME (298/299)
- HDMI cluster (300-304)
- INTERVAL_TYPE (306/307)
- FOCUS_ADJ (311)

The Benro app does **not** pair GET/SET as `(N, N+1)`. SET
codes 258-263 are dense, then GET codes 265-268 are dense,
then **workflow** codes 270-283 (focus_stack, panoramic,
delay_shot, hdr, plc), then meta 286-311 (info, preview,
hdmi, control, focus_adj).

## What needs to happen

1. **Replace Codes.kt** with the decompile-derived mapping
   (or add a PolarisCMD-mirror table that overrides our
   inferred names)
2. **Mark 789 as a synthetic code** that OpenPolaris uses
   but Benro does not, with a MANDATORY confirmation step
   before sending
3. **Add tests** that fail if any Codes.kt constant in the
   258-825 range doesn't match `polaris-cmd-extracted.txt`
4. **For every UI control**, validate GET-before / SET /
   GET-after on a charged K-3 III or K-1 II

## Time estimate

- Replace Codes.kt: 1-2 hours
- Add regression test: 1 hour
- Hardware re-test every UI control: 1-2 days

## Cross-references

- OpenPolaris #62 (the umbrella): this is the audit doc
  the issue needs
- OpenPolaris #64 (code 789): the destructive-delete bug
- OpenPolaris #65 (x86_64 process): separate audit item
- Patcher #38 (libgphoto2_port stub): separate runtime issue
- docs/evidence/2026-09-07/protocol-mapping/PROTOCOL-MAP-2026-09-07.md
  — should be regenerated with this new ground truth
