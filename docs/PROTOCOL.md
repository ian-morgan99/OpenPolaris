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

**Wire-format divergence (codes 513–516):** the stock Benro app encodes all four jog
codes as a duration — `time:Nms;` meaning "jog this axis for N milliseconds". Alpaca
and ogecko instead document 513/514 as `speed:%s;` (with a 2-axis variant `x:%d;y:%d;`)
and 515/516 as an opaque "angle string". The open client ships the Benro duration
encoding (consistent with the rest of the codebase's `time:` precedent for
`SETTLING_TIME` 544 and `SET_SYSTEM_TIME`) until a hardware pass confirms which
encoding the Benro firmware actually accepts. See
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

**Status of numeric codes:** the exact code numbers within 258–311 are INFERRED (built
dynamically in firmware; APK unavailable). Open Polaris implements the payload formats as
ground truth with named GET/SET constants in `Codes.kt`; camera controls carry an
experimental warning and must be validated on hardware before trusting the code mapping.

### 3.5 Out of scope (documented for completeness)

File ops (770–788) beyond thumbnail listing if needed, cellular remote (808–814, depends on
Benro's cloud), OMS external device (817–825), Theta secondary device commands, and firmware
update flashing (keep the official app for that).

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

## 6. References

- Firmware analysis: `../../docs/FIRMWARE-ANALYSIS-ALPACA.md`
- Ground truth sources: `PolarisCMD.java`, `PolarisOrderCommunication.java`, `SocketHelper.java`,
  `MainActivity.java` (decompiled Benro Connect v3.0.33)
- Independent implementation: https://github.com/ogecko/alpaca-benro-polaris
