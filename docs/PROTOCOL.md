# Polaris Control Protocol Reference

Complete, verified protocol for the Benro Polaris gimbal, extracted from firmware analysis
(`polestar_app`, FwVer 4.0.0.32) and ground-truth decompilation of Benro Connect (`com.snoppa.libra` v3.0.33).
This is the authoritative reference for the open-source client.

## 1. Transport

| Channel | Address | Purpose |
|---|---|---|
| Control | TCP `192.168.0.1:9090` | All commands, responses, pushes |
| Camera preview | HTTP MJPEG `http://192.168.0.1:8080/?action=stream` | Live view only |

- The phone joins the mount's Wi-Fi AP; no pairing, no BLE, no TLS on the control path.
- One persistent TCP connection; requests are effectively serialized (send → await response).
- A cellular/remote mode exists in the stock app but depends on Benro's cloud — out of scope.

## 2. Message framing

Request (client → mount):

```
1&<code>&<type>&<payload>#
```

- `<code>`: decimal command code.
- `<type>`: `2` for all commands observed in the app (request type marker).
- `<payload>`: key:value string, or `-100` (`EMPTY_CONTENT`) when there is no payload.
- Terminator is a literal `#`.

Example — start tracking at sidereal:

```
1&531&2&state:1;speed:0;#
```

Response (mount → client) uses the same framing. Parse by splitting the payload on `;`,
then taking the substring after the **last** `:` in each segment for the known key.

**Tolerance rule:** unknown keys must be ignored silently. This makes the client forward-compatible
with extended firmware payloads.

## 3. Command table

### 3.1 Tracking & alignment (astro core)

| Code | Name | Direction | Payload | Notes |
|---|---|---|---|---|
| 284 | SP_PUSH_MODE_STATE | C→M / M→C | empty | Status poll/push. Response carries mode, state, remNum, runTime, remTime, battery, etc. Stock app sends only on UI transitions — not periodic. |
| 517 | SP_GET_GIMBAL_POS | C→M | empty | Response: `yaw:` `pitch:` `roll:` (degrees) |
| 518 | SP_PUSH_ROTATE_VECTOR | M→C | push | Quaternion `w/x/y/z` + `compass` + `alt`. Streamed while AHRS enabled. |
| 519 | SP_SET_GOTO_AU_STATE | C→M | `state:%d;yaw:%f;pitch:%f;lat:%f;track:%d;speed:%d;lng:%f;` | Slew to alt/az. `state:0` cancels. Response has `ret:` and `track:` |
| 520 | SP_SET_AHRS_STATE | C→M | `state:%d;` | 1 = enable quaternion stream, 0 = disable. Response value `0` = success |
| 527 | SP_SET_YAW | C→M | `compass:%s;lat:%f;lng:%f;` | Set yaw reference from compass + location |
| 530 | SP_CALIBRATE_START | C→M | `step:%d;yaw:%f;pitch:%f;lat:%f;num:%d;lng:%f;` | Star alignment. App uses step=2 with target alt/az computed client-side; num = star index for multi-star |
| 531 | SP_SET_TRACK_AU_STATE | C→M | `state:%d;speed:%d;` | Start/stop tracking. speed: 0 = sidereal, 2 = lunar (per ogecko/alpaca driver, hardware-verified). Firmware format string `state:%d;speed:%d;` confirmed in polestar_app. Minimal `state:1;` (no speed) also accepted — firmware defaults the rate. |
| 536 | SP_SET_TRACK_HALF_SPEED | C→M | `halfSpeed:%d;` | ⚠️ **INVERTED**: app sends `halfSpeed:0` to ENABLE half-speed, `halfSpeed:1` for full speed |

### 3.2 Manual jog (rate/angle adjust)

| Code | Name | Payload |
|---|---|---|
| 513 | SP_GIMBAL_HADJ_SPEED | `time:%dms;` *(Benro app)* / `speed:%s;` *(ogecko/Alpaca)* |
| 514 | SP_GIMBAL_VADJ_SPEED | same |
| 515 | SP_GIMBAL_HADJ_ANGLE | `time:%dms;` *(Benro app)* / `angle string` *(ogecko/Alpaca)* |
| 516 | SP_GIMBAL_VADJ_ANGLE | same |
| 521 | SP_GIMBAL_RADJ_SPEED | roll-axis jog |
| 522 | SP_GIMBAL_RADJ_ANGLE | roll-axis angle |
| 523 | SP_POS_RESET | reset position reference |
| 524 | SP_EX_AXIS_STA | external axis state |
| 532–534 | YAW/PITCH/ROLL_KEY | key events |
| 535 | SP_SET_GIMBAL_POS | `yaw:%s;pitch:%s;roll:%s;` |

**Enhancement note:** 513–522 exist in firmware but the stock app uses them only for manual jog.
The open client may use them as fine rate trims during tracking (subject to hardware validation).

**Wire-format divergence (codes 513–516):** the stock app encodes all four jog
codes as a duration — `time:Nms;` meaning "jog this axis for N milliseconds". Alpaca
and ogecko instead document 513/514 as `speed:%s;` (with a 2-axis variant `x:%d;y:%d;`)
and 515/516 as an opaque "angle string". The open client ships the duration encoding
(consistent with the rest of the codebase's `time:` precedent for
`SETTLING_TIME` 544 and `SET_SYSTEM_TIME`) until a hardware pass confirms which
encoding the firmware actually accepts. See
[CommandTable.kt:115](shared/src/commonMain/kotlin/dev/openpolaris/core/protocol/CommandTable.kt)
and [FIRMWARE-ANALYSIS-ALPACA.md](FIRMWARE-ANALYSIS-ALPACA.md).

### 3.3 Astro helper settings

| Code | Name | Payload |
|---|---|---|
| 537/538 | GET/SET_TILT_STATE | tilt switch |
| 539 | GET_DITHER_STATE | query |
| 540 | SET_DITHER_STATE | `state:%d;` |
| 541 | GET_LIMIT_STATE | query |
| 542 | SET_LIMIT_STATE | `state:%d;` angle limits |
| 543 | GET settling time | query |
| 544 | SET settling time | `time:%d;` |
| 547 | GET_AUTO_LEVEL_EN | query |
| 548 | SET_AUTO_LEVEL_EN | `en:%d;` |
| 549 | SET_AUTO_LEVEL_STATE | trigger auto-level |

### 3.4 Camera parameters (codes 258–311) — IN SCOPE for parity

Full end-to-end support requires camera control. Codes 258–311 cover capture modes, exposure
triangle (ISO/aperture/shutter), white balance, bulb time, RAW/JPEG selection, and capture
triggers, each with get/set pairs. Caution: camera commands can interrupt a running capture
sequence — disable controls while a sequence is active.

**Ground truth recovered from `bin/polestar_app` (firmware appfs, unstripped symbols):**

Payload formats (requests and `<key>:<val>;ret:<code>;` responses):

| Parameter | Get response | Set request |
|---|---|---|
| ISO | `iso:%d;ret:%d;` | `iso:%d;` |
| White balance | `wb:%*d;ret:%d;` | `wb:%d;` |
| Aperture | `fNum:%d;ret:%d;` | `fNum:%d;` |
| EV | `ev:%d;ret:%d;` | `ev:%d;` |
| Shutter | `s:%d;ret:%d;` | (no literal request format) |
| Direction | `dir:%d;` | `dir:%d;` |
| Capture state push | `state:%d;bulb:%d;c:%d;` | — |
| Focus | `focus:%d;leve:%d;` / combined `state:%d;focus:%d;leve:%d;b:%d;path:%s;c:%d;` | — |

Timelapse step payloads: `step:%*d;bulb:%d;num:%d;`, `step:6;state:%d;iso:%d,%d;`,
`step:7;state:%d;num:%d;remainNum:%d;`, `step:4;priority:%d,%d,%d;`, `step:13;runTime:%d;`.

Semantics: values are **indices into firmware-sorted option lists** (`SP_SetCameraIsoIndex`,
`eSortIso/eSortShutter/eSortFNum`), not raw numbers. Firmware validates per-model ranges
("iso max/min limit at:%d;", "shutter range limit at max/min:%d;"). Capture runs through a
CableRelease task (`SP_CreateCableReleaseTask`, `SP_CableReleaseMakePhoto`). Battery:
`capacity:%d;charge:%d;`.

**Status of numeric codes — evidence levels (keep these distinct):**

- **APK-derived (decompiled Benro Connect, 2026-09-11):** the exact code numbers and
  payload spellings for the camera SET/INFO map in `Codes.BenroCamera` (258–311),
  including focus jog 262 and MF adjust 311 (§3.4.1). The stock app's request methods
  are the source of truth for code + payload; the firmware symbols above are the source
  of truth for response shapes.
- **Live-verified:** only what is recorded in §5.1 (2026-09-07 hardware session) and in
  `docs/evidence/`. A code being APK-derived does NOT mean it has been exercised on a
  K-3 III or K-1 II.
- **Inferred:** payload *semantics* that are not directly observable — e.g. which index
  values map to which physical ISO/shutter/aperture options (the firmware sorts option
  lists per model), and the jog speed magnitudes (6/5/4 vs 2/1/0) whose direction
  (add vs drop) comes from UI button labels, not wire captures.

Open Polaris implements the payload formats as ground truth with named GET/SET constants
in `Codes.kt`; camera controls carry an experimental warning and must be validated on
hardware before trusting the code mapping.

#### 3.4.1 Focus set (262) and focus adjust (311) — derived from Benro Connect APK

Source: decompiled `PolarisOrderCommunication.java` + call sites in
`ParameterItemLayout` / `FocusTrackLayout` (see CAMERA-PARITY-JUNIOR-AGENT-GUIDE.md).
This supersedes the inferred `focus:<index>;` mapping for these two codes.

| Action | Code | Subtype | Exact payload | Parsed reply | Terminal? |
|---|---:|---:|---|---|---|
| focus jog (AF speed) | 262 | 1 | `mod:<m>;f:<s>;` | `ret:<n>;` only | no — jog |
| MF adjust (focus track) | 311 | 1 | `mode:<m>;adj:<a>;` | `ret:<n>;` only | no — jog |

- **262 is a jog, not a set.** The stock app sends it repeatedly every 300 ms while the
  user holds a focus-speed button; `mod:0;f:0;` is the stop. Speed values observed at
  call sites: left (add) fast/middle/slow = `6`/`5`/`4`, right (drop) fast/middle/slow =
  `2`/`1`/`0`, with `mod:1` for all moving states.
- **311 is the manual-focus jog used inside focus-track mode.** Observed values: add
  fast/slow = `-4`/`-1`, drop fast/slow = `4`/`1`, always `mode:1`.
- **Physical direction (K-3 III on Polaris, 2026-09-11):** the APK's add/drop
  labels are not safe Near/Far names. Polaris passes the signed value to the
  generic libgphoto2 focus control: `adj:+1/+4` means **Near** and
  `adj:-1/-4` means **Far**. An earlier qualification instruction labelled
  `adj:-1` as Near and was physically observed to move the opposite way.
  UI code must use the semantic controller wrappers rather than assigning raw
  signs at the button call site.
- **No INFO/read-back exists for either code** (no GET pair in the opcode table). The
  reply parser extracts only `ret:` and broadcasts it; success means `ret >= 0`. There is
  no state to verify against — do not treat a missing read-back as failure, but also do
  not claim the lens position changed without hardware evidence.
- Both are exposed in qualification mode only, with hold-to-jog semantics (repeat while
  held, send stop on release for 262).

#### 3.4.2 Camera liveview / preview (291/292) — APK-derived, K-3 III v9d live-verified

Source: decompiled `SP_SET_CAMERA_PREVIEW` / `SP_GET_CAMERA_PREVIEW` in
`PolarisOrderCommunication.java`. **Live-verified on K-3 III (firmware v9d), 2026-09-11**
(issue #80 evidence, patcher commits 8bc6163 and 12465c8): `1&291&2&state:1;#` →
`291@state:1;ret:0;#`; `1&292&2&#` → `292@state:1;#`; a 120 s run delivered 58/58
complete JPEG frames (~0.483 fps) on the 8080 data plane, and stop returned `state:0`.

| Action | Code | Subtype | Exact payload | Parsed reply | Terminal? |
|---|---:|---:|---|---|---|
| preview ON | 291 | 2 | `state:1;` | `state:1;ret:0;` | no — stream starts on 8080 |
| preview OFF | 291 | 2 | `state:0;` | `state:0;ret:0;` (verify) | yes for the control plane |
| preview state query | 292 | 2 | *(empty)* | `state:<0\|1>;` | no — query |

- **The 292 reply carries `state:` but NOT `ret:`** (live capture: `292@state:1;#`).
  The stock app's parser reads only `state:` and treats `0` as "off". Do not require a
  `ret` field on 292 replies.
- **The image data does not travel over the 9090 control socket.** Starting preview
  opens a separate multipart JPEG stream on port 8080 (see §3.4.2 evidence and the
  preview-stream issues #61/#74). Observed cadence: ~0.5 fps, complete JPEG frames
  (~66 KB each) in a continuous multipart stream; the stream stops cleanly when
  `state:0` is sent.
- Qualification-mode exposure only until K-1 II is also verified (issue #63).

### 3.5 Out of scope (documented for completeness)

File ops (770–788) beyond thumbnail listing if needed, cellular remote (808–814, depends on
Benro's cloud), and Theta secondary device commands.

### 3.6 Firmware update (in scope, see [CAPABILITY-GUIDE.md §4](CAPABILITY-GUIDE.md#4-firmware-upgrade--the-deep-dive))

Open Polaris can upload a local `FwPkt.zip` to the head using the same wire-protocol sequence
the Benro Connect app uses (`SYS_FW_UPGRADE` 810 → `FILE_UPLOAD_FW` 784 → `FILE_UPLOAD_CHUNK`
794 × N → `FILE_UPLOAD_END` 795 → `SYS_FW_PROGRESS` 811 → optional `SYS_REBOOT` 812). The
"stream from Benro server" in the official app is just the source of its bytes — the wire is
identical regardless of source, so the head doesn't care whether the bytes came from the
Benro CDN or a local file you pick.

The full state machine, the file-picker UI, the `firmwareUpload` feature flag, the safety
caveats, and the comparison to the official app are in
[CAPABILITY-GUIDE.md §4](CAPABILITY-GUIDE.md#4-firmware-upgrade--the-deep-dive).

> **Caveat:** the chunk binary framing (794's payload slot) was smoke-tested against the
> in-process simulator only. A final live Benro Connect capture is pending. See
> [CAPABILITY-GUIDE.md §4.7](CAPABILITY-GUIDE.md#47-caveats).

### 3.7 Out of scope (still out of scope, for the record)

OMS external device (817–825) — the read of 824/825 is in scope (`omsRead`), but
add/edit/delete scheduler tasks is gated behind `omsScheduler` and not yet implemented
(awaiting a live trace; see audit ticket `pr4b-oms`).

## 4. Session lifecycle (learned from the stock app)

1. Connect TCP to `192.168.0.1:9090`.
2. Send `284` (status poll) to confirm liveness and read current mode/state/battery.
3. On entering astro mode: send `520 state:1` to enable AHRS stream.
4. Alignment: compute target alt/az client-side → send `530`; repeat per alignment star.
5. Goto: send `519 state:1 ...`; cancel with `state:0`.
6. Track: send `531 state:1 speed:<0|2>`; stop with `state:0`.
7. On leaving astro mode or disconnecting: send `520 state:0`.

**Headless rule:** nothing in the firmware requires a phone heartbeat. A client that connects,
enables AHRS, and tracks works indefinitely without any keepalive.

## 5. Known quirks & traps

1. **halfSpeed inversion (536)** — see table above. Third-party implementations have tripped on this.
2. **AHRS is app-gated** — quaternions flow only after `520 state:1`. A watchdog-style reconnect must
   re-enable it.
3. **speedType is binary** — sidereal (0) or lunar (2) only. No solar rate, no custom rates via 531.
   Custom rates require the ADJ_SPEED family (§3.2) or patched firmware.
4. **Payload parsing is key-based** — extra fields appended to responses are ignored by tolerant parsers;
   our client should be tolerant too.
5. **`-100` sentinel** — an absent payload is the literal string `-100`, not an empty field.
6. **No error taxonomy observed** — failures surface as `ret:` values or silence. The client needs
   timeout-based error handling (recommend 2 s command timeout, 10 s for slews).

## 5.1 Live hardware verification (2026-09-07)

A live protocol sweep of **all 30 codes most relevant to the desktop app's
core features** (codes 254, 256, 282, 284, 286, 290, 295, 300, 305, 519,
524, 525, 526, 528, 540, 541, 542, 543, 547, 549, 770, 775, 778, 779,
780, 802, 803, 805, 808, 809) was run against the **2026-09-07-k1ii-
k3iii-candidate** firmware on the live gimbal (sw:6.0.0.54, FwVer 4.0.0.32,
K-3 III connected at lsusb 25fb:0189). The full map is at:

**[`docs/evidence/2026-09-07/protocol-mapping/PROTOCOL-MAP-2026-09-07.md`](evidence/2026-09-07/protocol-mapping/PROTOCOL-MAP-2026-09-07.md)**

with the raw transcript at
[`docs/evidence/2026-09-07/protocol-mapping/transcripts/cli-probe-transcript-2026-09-07-1336.txt`](evidence/2026-09-07/protocol-mapping/transcripts/cli-probe-transcript-2026-09-07-1336.txt).

Highlights that **change the picture above**:

- **286 (`CAM_GET_STATE`)** — `manufacturer:ricoh imaging company, ltd.;model:pentax k-3 mark iii;state:1;storage:2;photoFormat:2;#`
  — the K-3 III **is** fully identified by the firmware. The `state:-2` reported
  by `sp_Gphoto_Init` is the pgphoto runtime, not the protocol. See
  [patcher#38](https://github.com/ian-morgan99/benro-polaris-firmware-patcher/issues/38)
  for the runtime/iolibs-lookup split.
- **770 (`FILE_LIST`)** — returns per-category counts (`normal:3;lapse:0;…`)
  cross-checked against `ls /app/sd/normal` (3 SP_*.jpg files before, 0 after a
  destructive probe — see [OpenPolaris#64](https://github.com/ian-morgan99/OpenPolaris/issues/64)).
- **775 (`FILE_SD_STATUS`)** — `totalspace:121866;freespace:121781;usespace:85;`
  matches `df -h` exactly.
- **780 (`DEVICE_INFO`)** — `hw:1.1.1.2;sw:6.0.0.54;exAxis:;sv:1;ov: ;` — the
  `sw:6.0.0.54` is the **patcher's own version stamp** on top of FwVer 4.0.0.32.
- **802 (`GET_WIFI_BAND`)** — `band:1` (2.4 GHz). The gimbal's AP does not
  advertise 5 GHz; 802 is the canonical band probe.
- **808 (`SYS_VERSION`)** — does **not** reply on the live gimbal, confirming
  the [POLARIS-FUNCTIONS-REPORT.md §4 quirk 1](POLARIS-FUNCTIONS-REPORT.md)
  note. (The vyskocil wiki's "Create Connect Context Request" framing is
  recorded as an [open question](#7-where-another-teams-notes-disagree-with-ours)
  in §7 below.)
- **Codes 258–278** (the camera info burst) — **do not** reply. Pentax-specific
  camlib reads; reach here only through the embedded gphoto2 path which the
  currently-broken `sp_Gphoto_Init` blocks. The direct CLI on the gimbal's
  stock `gphoto2` binary does work for these (see the K-3 III -1005/-1203
  investigations).
- **525 (`GET_TEMPERATURE`)** — pushed (not pulled), `Tempa<hex16>;` every
  ~30 s. Use `liveListen` to capture.

Cross-verification rule used throughout: every protocol-level claim is
backed by a direct shell inspection (`ls`, `df`, `cat /app/FwVer`,
`cat /app/openpolaris-libgphoto2-provenance.txt`, `cat /proc/net/tcp`)
on the live device. If a protocol response disagrees with a shell
inspection, the protocol response is the suspect — not the shell.

## 6. References

- Firmware analysis: `../../docs/FIRMWARE-ANALYSIS-ALPACA.md`
- Independent implementation: https://github.com/ogecko/alpaca-benro-polaris
- Third-party wiki cross-reference (vyskocil/Polaris): see
  [`VYSKOCIL-WIKI-REVIEW-2026-09-07.md`](VYSKOCIL-WIKI-REVIEW-2026-09-07.md). That doc compares our
  live-captured payloads against a second team's wiki. **Most of their wire shapes agree with ours
  and can be used as second-source confirmation. A handful of codes disagree — see §3 of that doc
  for the divergence list.** Until we hardware-confirm the wiki's claims on those divergent codes,
  PROTOCOL.md stays on our evidence and does not import the wiki's framing for them.

## 7. Where another team's notes disagree with ours

This section is the human-readable summary of the divergences in
[`VYSKOCIL-WIKI-REVIEW-2026-09-07.md` §3](VYSKOCIL-WIKI-REVIEW-2026-09-07.md). Until a live capture
on our gimbal confirms the alternative framing, **we keep our existing table entries and treat
the wiki as a question to investigate, not a fact to import.**

| Code | Our framing (live-captured) | Wiki framing (vyskocil/Polaris) | Status |
|---|---|---|---|
| 271 | `CAM_SET_IMG_SIZE` — image size set, payload `imgSize:N;` | "Camera Panorama Msg" — `step:11;` | Conflicting. Different firmware version, different mode, or different code. **Open question.** |
| 272 | `CAM_GET_IMG_FMT` — image format read, payload `imgFmt:N;#` | "Camera Lapse Msg" — `step:10;` | Same as 271. **Open question.** |
| 305 | Catalogue entry exists; payload format not yet wire-confirmed | "Camera HG message" — `step:2;` | **Open question.** |
| 797 | `FILE_SCAN_COMPLETE` (ack), payload `797@..;#` | "Physical-limits error frame" — `797@errorCode:-1203;#` | Conflicting. Both could be true on different firmware versions, or one of us is wrong. **Open question — do not import `errorCode:` parsing until hardware-confirmed.** |
| 799 | `GET_CELLULAR_STATE` — live `ret:-1;`, asymmetric with 802 | "Password Msg Process" — `799@ret:-1;` | Same `ret:-1;` observed by both, but our decompile names it `SP_GET_CELLULAR_STATE`. The wiki's "Password" framing contradicts our reading and is **not** what the stock app's connect-burst does on our gimbal. **We keep `GET_CELLULAR_STATE`.** |
| 808 | `SYS_VERSION` (corpus) — **live doesn't reply on real hw** ([POLARIS-FUNCTIONS-REPORT.md §4 quirk 1](POLARIS-FUNCTIONS-REPORT.md)) | "Create Connect Context Request" — `808@type:0;` → `808@ret:0;` (claimed critical to keep socket open) | Conflicting. Our live evidence: 808 doesn't reply on our gimbal (sw 6.0.0.54). The wiki's framing may describe a newer firmware build, or a different role entirely. **We keep `SYS_VERSION` and note the open question about connection-drop behaviour.** |

The wiki also includes claims we **cannot yet evaluate** because we have no matching live capture
on our gimbal: `797@errorCode:-1203;` (physical-limit error), the "couple of minutes" socket-close
time when 808 is omitted, and the 0.1 s/0.05 s timing window for the 513/514/521 quick-move
family. These are recorded as **open questions in the review doc** rather than imported as facts.
