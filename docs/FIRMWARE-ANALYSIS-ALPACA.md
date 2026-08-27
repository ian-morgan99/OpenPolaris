# Benro Polaris Firmware Analysis vs. alpaca-benro-polaris

## Plain-English summary (TL;DR)

**What we found:** The Polaris firmware is very smart about *where* things are in the sky — it carries a full
professional-grade astronomy library that can calculate the position of any star, planet, moon, or satellite. But once
it points the camera at a target, its *tracking* is surprisingly dumb: it just spins the motors at one fixed speed
("the stars move at this rate, so will I") and never checks whether it's actually still on target.

**What the Alpaca add-on does:** Because the firmware won't correct itself, the Alpaca driver does it from a laptop.
It watches the gimbal's sensor stream and restarts it if it dies, measures how far the camera has drifted every couple
of minutes, and continuously nudges the motor speeds up or down to stay locked on target.

**The good news:** Almost everything Alpaca does could be done inside the Polaris itself. The hardware already
supports fine per-axis motor speed adjustment — the tracking code just never uses it. The fixes below are software
changes to the main app; no disassembly of the motor controller needed.

**The fixes, in order of value:**

1. **Use the fine speed control that's already there.** The motor interface supports precise speed tweaks for each
   axis, but the tracking code only ever uses full speed / half speed. Wiring in the fine control would let the mount
   correct itself smoothly instead of jumping between two speeds.
2. **Check your work.** Every few minutes, compare where the camera is pointing against where it should be, and trim
   the speed slightly to compensate for drift (caused by imperfect polar alignment, atmospheric bending, gear
   imperfections).
3. **Tell the user when tracking didn't start.** Right now there are four situations where the mount finishes a slew,
   reports success, but silently isn't tracking. The user walks away thinking they're getting 5-minute exposures and
   gets star trails. It should warn loudly instead.
4. **Fix yourself when the sensors hiccup.** If the orientation-sensor data stream stalls, the firmware should notice
   within seconds and restart it — exactly what the Alpaca driver had to build because the firmware doesn't.
5. **Blend all the sensor data together.** The firmware currently uses simpler sensor-fusion math than it could;
   combining the attitude sensors with the motor position encoders (which the hardware also provides) gives steadier,
   more accurate pointing.
6. **Report more status.** Instead of just "half speed: yes/no", report actual current speed, last measured drift, and
   how fresh the sensor data is — useful for the official app and any third-party tools alike.

---

**Research report — non-destructive analysis. No firmware or repo binaries were modified.**

- Firmware analyzed: `FwPkt.zip` — `FwVer:4.0.0.32;date:2025.05.09`
  - `camera/rootfs.ubifs` (21 MB), `camera/appfs.ubifs` (64 MB), `camera/uImage` (Linux 4.9.37, HiSilicon Hi3559v200)
  - `gimbal/polaris403_2.0.0.22.bin`, `gimbal/polaris413_2.0.0.22.bin`
- Comparison project: https://github.com/ogecko/alpaca-benro-polaris (ASCOM Alpaca telescope driver that replaces the Polaris' native tracking with its own closed-loop control)

All extraction was performed on copies under `/tmp/polaris-analysis/`. The main analysis target is
`appfs-files/bin/polestar_app` — a 24 MB ARM ELF that is **not stripped and retains DWARF debug info**, exposing
source file names (`/home/win/polaris/polestarApp/...`) and full symbol/string inventories.

---

## 1. Firmware architecture

| Layer | Component | Role |
|---|---|---|
| SoC | Hi3559v200 | Camera + application processor, runs Linux 4.9.37 |
| Flash layout (mtdparts) | u-boot 512K / factoryParam 512K / userParam 1M / uImage 5M / rootfs 40M / appfs 81M | UBI/UBIFS NAND |
| Rootfs | BusyBox-based | System services |
| Appfs | `bin/polestar_app` | Main application: astro engine, tracking, modes, messaging |
| Gimbal MCU | separate firmware (2.0.0.22) | Motor control, AHRS sensors, receives commands over UART |

### polestar_app module map (from debug paths)

```
sp_sensor/src/   sp_Ahrs.c sp_ahrsComm.c sp_autoLevel.c sp_calAu.c sp_calibrate.c
                 sp_gotoAu.c sp_MagSensor.c sp_MpuSensor.c sp_newCalibrate.c
                 sp_SensorMng.c sp_trackAu.c
sp_uart/src/     sp_exDevUpgrade.c sp_gimbalUpgrade.c sp_uart.c sp_uartComm.c
sp_msgmng/src/   sp_btMsg.c sp_msgComm.c sp_msgProc.c sp_shareQueue.c
sp_module/src/   sp_lapse.c sp_holy_grail.c sp_panorama.c sp_starsky_stack.c
                 sp_sun.c sp_plc.c
sp_astro/        vsop87, moon(ELP), sun, sgp4/sdp4 satellites, chap95 planets,
                 precess, nutation, refract, parallax, utc_gst ...
```

The astrometric library is remarkably complete (VSOP87 planetary theory, ELP lunar theory, SGP4/SDP4 satellite
propagation, Chap95 planets, full precession/nutation/refraction/parallax chains) — the math for accurate pointing is
already in the device.

---

## 2. Tracking pipeline as implemented in firmware

### 2.1 Open-loop sidereal tracking

The core tracking rate is a fixed constant:

```
SiderealSolar = 1.0027379093
SidRate       = PI2 * SiderealSolar / SecondsPerDay
```

Once a GOTO completes and tracking starts, the gimbal is commanded to rotate at this fixed rate. There is **no
feedback loop**: no drift measurement, no re-sighting, no correction from the AHRS stream while tracking.

### 2.2 Alignment ("AU") state machines

GOTO alignment (`sp_gotoAu.c`):

```
SP_GOTO_STA_{NULL, SEND_BOX_AU, SEND_START, WAIT_RESULT, SUCCESS, FAIL, CANCLE}
```

Tracking alignment (`sp_trackAu.c`):

```
SP_TRACK_AU_STA_{NULL, SEND_BOX, SEND_START, WAIT_START, WAIT_RESULT, PAUSE, STOP, FAIL}
SP_TRACK_AU_CMD_{START, TRACK, MOON_POS, SET_HALF_SPEED, PAUSE, UNPAUSE, CANCLE,
                 RET_SUCCESS, RET_FAIL}
```

Calibration (`sp_calAu.c`): `SP_CAL_AU_STA_{NULL, GET_ACCE, GET_BOX, GET_BOX0, GET_TILT}`.
Box calibration produces quaternions pushed to the gimbal:

```
"send boxNwu to gimbal:w[%4f];x[%4f];y[%4f];z[%4f];"
"send g_qBoxTilt to gimbal..."
"send auInfo to gimbal:yaw[%4f];pitch[%4f]"
```

AHRS fusion in `sp_Ahrs.c`: **MadgwickAHRSupdate/IMU and MahonyAHRSupdate/IMU only** — no Kalman/EKF filter exists
anywhere in the firmware.

### 2.3 The tracking control surface is coarse

Everything an app can tell the tracker is essentially:

- Start payload: `state:%d;speed:%d;`
- Half-speed toggle: `SP_TrackAuUartSetHalfSpeed = %d` (binary on/off)
- Status push: `mode:%d;state:%d;track:%d;speed:%d;halfSpeed:%d;remNum:%d;runTime:%d;photoNum:%d;pause:;interval:;repeNum:`

There is no continuous speed trim, no per-axis rate command, no position-error feedback exposed at the trackAu level.
Per-axis fine speed adjustment *does* exist one layer down, at the gimbal UART:

```
UART_GIMBAL_ADJ_SPEED      0x41
UART_GIMBAL_HADJ_SPEED / VADJ_SPEED / RADJ_SPEED   (per-axis enums)
UART_GIMBAL_HADJ_ANGLE / VADJ_ANGLE
UART_GIMBAL_ROTATE         0x4A
UART_GIMBAL_AHRS           0x4B
UART_GIMBAL_LIMIT          0x4C
UART_GIMBAL_CONTROL        0x45
UART_GIMBAL_LAPSE          0x43
UART_GIMBAL_PANORAMA_CMD   0x47
```

…but `sp_trackAu.c` never uses it. The PLC scheduler module (`sp_plc.c`) has its own path
(`PlcGimbalSetSpeed/Start/Stop`) — again bypassing trackAu.

### 2.4 Silent tracking skip — four failure paths that look like success

Verbatim strings from polestar_app:

```
goto au success,bTrack=HI_FALSE,no tracking Au
goto au success,no track Au,Au no cal
goto au success,no track Au,no exAxsi
goto au success,no tracking Au,no in sky mode
goto au success,tracking Au,speed:%d;
```

In all four "no track Au" cases the GOTO reports **success** while tracking silently does not start. Only one of the
reasons (no calibration) is something the user could plausibly fix without knowing it happened.

---

## 3. What alpaca-benro-polaris does differently

The Alpaca driver (~9700 lines of Python) talks to the same device over the same protocol
(`%d&%ld&%ld&%s` framing; GOTO parse `state:%*d;yaw:%f;pitch:%f;lat:%f;track:%d;speed:%d;lng:%f;`), but wraps it in a
closed-loop control stack:

### 3.1 Watchdog & self-healing (`driver/polaris.py` @375–402)

- `_every_1s_watchdog_check`: ages cmd 517 (orientation) / 518 (quaternion) pushes; if silent > 2 s it restarts the
  AHRS stream via cmd 520; if silent > 5 s it reboots the connection.
- `_every_15s_send_polaris_keepalive`: cmd 284 mode query keeps the link alive.
- Firmware has **no equivalent self-healing** — if the AHRS push stream stalls, nothing notices.

### 3.2 Drift measurement (`driver/polaris.py` @1380–1404)

`every_2min_drift_check` → `drift_error_test`: measures actual RA/Dec drift over 120 s windows. Diagnostic input for
the PID loop below.

### 3.3 Closed-loop motor control (`driver/control.py`)

- `PID_Controller` (@1014, dt = 0.2 s) drives motors directly from measured error.
- `KalmanFilter` fuses AHRS quaternions against the motor encoder model — compensating for the fact that the reported
  orientation and the actual mount motion disagree.
- `MotorSpeedController` (@823): because raw rate 0 disengages torque, Alpaca emulates PWM by dithering between rates;
  `speed_calibration.json` interpolates the −2500..2500 raw-rate units.
- `MoveAxisMessenger` (@982): per-axis move-axis commands with independent RA/Dec rates.

### 3.4 Sync model (`driver/control.py` SyncManager @1718+, docs/quest_paper.md)

- QUEST/Wahba least-squares quaternion optimization over ≤10 weighted sync points, dedup radius 2.5°, separate roll
  adjustment.
- Their own paper notes plain least-squares doesn't minimize error along the target arc, so sync points near the
  target arc are weighted higher.

### 3.5 Guiding

Pulse guiding (PHD2) requires tracking active; guide rates validated 0..2 °/s.

---

## 4. Side-by-side gap table

| Capability | Stock firmware | Alpaca driver | Gap severity |
|---|---|---|---|
| Tracking rate | Fixed open-loop SidRate | PID closed-loop w/ drift measurement | **High** |
| Sensor fusion | Madgwick/Mahony only | Kalman fusing AHRS + encoders | High |
| Fine speed control | Binary halfSpeed toggle | Continuous per-axis rates (+PWM emulation) | **High** |
| AHRS stream health | None — silent stall possible | 1 s watchdog, auto-restart (cmd 520), reconnect | Medium |
| Link keepalive | None observed | 15 s keepalive (cmd 284) | Low |
| Drift diagnostics | None | 120 s drift test every 2 min | Medium |
| Sync/alignment | Box0/tilt quaternion push, single-shot | QUEST weighted multi-point sync | Medium |
| GOTO result reporting | pass/fail byte only | Position-error aware | Medium |
| Astrometry | Full VSOP87/ELP/SGP4 suite | Uses firmware's | FW ahead |
| Protocol | Same `%d&%ld&%ld&%s` framing | Same | parity |

---

## 5. Recommended firmware fixes (informed by Alpaca)

*(Plain-English version at the top of this document; the technical detail follows.)*

Ordered by expected impact on tracking quality. All are changes inside `polestar_app` (repackable via this repo's
existing `container/repack_appfs.sh` workflow); none require touching the gimbal MCU firmware.

### R1 — Wire per-axis fine speed into trackAu (highest value)

The gimbal UART already implements `UART_GIMBAL_ADJ_SPEED` (0x41) plus H/V/R per-axis variants, and PLC already drives
gimbal speed directly. Extend `sp_trackAu.c` to:
1. accept a fine-trim parameter in the start payload (`state:%d;speed:%d;` → add e.g. `raTrim:%f;decTrim:%f;`),
2. periodically issue ADJ_SPEED commands instead of relying on the binary halfSpeed toggle.

This alone removes the quantization problem that forces Alpaca into PWM emulation, and enables real periodic-error /
drift compensation without external software.

### R2 — Close the loop: periodic drift check inside trackAu

Add a lightweight version of Alpaca's `drift_error_test` to the firmware task loop: every N minutes, compare commanded
position vs. AHRS-reported attitude delta, apply a small PI correction to the sidereal base rate. Even a simple
integral-only controller would capture most of the benefit (atmospheric refraction, polar alignment error, gear
periodic error at low frequencies).

### R3 — Stop failing silently when tracking can't start

The four `goto au success,no track Au,...` paths should either:
- return a distinct failure/warning code in the GOTO result payload, or
- emit a warning-tone + app push (`SP_PushWarningToneStateToApp` already exists).

A user who aligns, slews, and walks away deserves to know tracking never engaged.

### R4 — Self-heal the AHRS stream

Mirror Alpaca's watchdog inside polestar_app: timestamp each received 517/518-class AHRS frame; if stale > 2 s, resend
the AHRS-start command (the firmware already knows how — `SP_PushAhrsToGimbal`); if still stale, reset the sensor
manager (`sp_SensorMng.c`). This converts a hard-to-diagnose field failure into a self-recovering transient.

### R5 — Upgrade AHRS fusion

Replace/augment Madgwick+Mahony with an EKF that also ingests gimbal encoder angles (available via
`UART_GIMBAL_ANGLE` 0x3D responses). Alpaca demonstrated externally that fusing encoder data with AHRS quaternions
materially improves pointing consistency. The Hi3559v200 has ample headroom for a small EKF at 100–200 Hz.

### R6 — Richer status reporting

Extend the tracking status push beyond `halfSpeed:%d` with: current effective rate per axis, last drift estimate,
AHRS freshness age, and settle error after GOTO. This benefits the stock app *and* any third-party driver.

### R7 — Keepalive/drift diagnostics built-in (optional)

Adopt the 15 s keepalive concept internally so long exposures over BT/Wi-Fi don't depend on client behavior, and log
drift statistics accessible via a query command — turning Alpaca's diagnostic tooling into a native feature.

### Non-recommendations

- Don't port the PWM-emulation hack into firmware — R1 makes it unnecessary.
- Don't replicate Alpaca's full QUEST sync stack in firmware yet; the existing box-calibration flow works, and the
  arc-weighting refinement matters mostly for third-party drivers doing their own sync.

---

## 7. Follow-up: obtaining the mobile app (recommended)

Getting the Benro Polaris Android APK would upgrade several inferred mappings to confirmed facts:

1. **Protocol ground truth** — the app is the other end of the `%d&%ld&%ld&%s` framing. Decompiling it
   (`apktool`/`jadx`) would confirm exact command-code numbers (517/518/520/284, currently inferred from Alpaca's
   side only) and show precisely which payload fields the app parses vs. ignores — defining how much freedom we have
   to extend payloads without breaking the stock UX.
2. **UI surface mapping** — reveals which warning states/error codes the app can render, bounding what R3 can safely
   emit.
3. **Transport details** — whether the app uses Wi-Fi TCP, BLE, or both, informing third-party client design.

Not useful for modification — patching a distributed app is legally messier than patching owned firmware and
unnecessary given the strategy: firmware handles everything automatic; protocol exposes knobs to third-party clients.

### App-control constraints on the recommendations

Anything needing new user-facing controls is blocked by the untouchable app:

| Recommendation | App constraint |
|---|---|
| R1 user-set trims | App only sends `state:%d;speed:%d;` — trims must default to firmware-computed values or wait for third-party clients |
| R6 richer status | Extra pushed fields are silently ignored by stock app; visible only to third-party clients |
| Sync-point submission | No sync button/command exists in the app |
| Pulse guiding | No guiding UI in the app |
| R3 warnings | Must reuse warning-tone/error channels the app already renders |

Unconstrained (fully automatic): R2 drift correction, R4 watchdog, firmware-internal use of ADJ_SPEED. The firmware-mod
and Alpaca-driver projects are therefore complementary: automatic fixes in firmware, human knobs via protocol for
third-party clients.

---

## 8. Feasibility without source (binary-patch tiers)

`polestar_app` is unstripped with DWARF debug info — unusually favorable for surgical binary patching via this repo's
repack workflow:

- **Tier 1 — R3** (silent-skip warnings): small logic patches at known functions; classic hex-patch territory.
- **Tier 2 — R4** (AHRS watchdog): inject staleness check into an existing periodic task via code cave/hook.
- **Tier 3 — R1** (ADJ_SPEED wiring): plumbing + parser extension; mechanical but invasive.
- **Tier 4 — R2** (drift loop): ~50 lines of injected logic; hard part is blind tuning on real hardware.
- **Tier 5 — R5** (EKF): not realistic as a patch; needs source.

Suggested path: fork as `polaris-firmware-mods`, pilot R3 through `container/repack_appfs.sh`, then R4 → R1 → R2,
using Alpaca's drift test as the measurement oracle against stock vs. patched.

---

## 9. Benro Connect APK reverse-engineering (protocol ground truth)

The stock Android app (**Benro Connect**, `com.snoppa.libra`, v3.0.33) was downloaded from a public mirror and
decompiled with jadx 1.5.0. This converts every previously Alpaca-inferred protocol fact into **confirmed ground
truth** from the vendor's own client.

### 9.1 Transport — confirmed

- Control channel: plain **TCP socket to `192.168.0.1:9090`** (`SocketHelper` defaults). No BLE for Polaris control.
- Live camera preview: separate HTTP MJPEG stream at `http://192.168.0.1:8080/?action=stream`.
- A cellular/remote mode exists (`CellularSocketHelper`) for remote shooting over the internet; same framing.

### 9.2 Message framing — confirmed

`PolarisOrderCommunication.sendOrder()` builds exactly:

```
"1&" + code + "&" + type + "&" + payload + "#"
```

matching the firmware's `%d&%ld&%ld&%s` printf format, with `#` terminator. A null payload is sent as `"-100"`
(`EMPTY_CONTENT`). Responses are parsed by splitting on `;` and taking text after the last `:` per key.

### 9.3 Command table — all inferred codes now verified

From `com/snoppa/application/constant/polaris/PolarisCMD.java`:

| Code | Constant | Payload format (from senders) |
|---|---|---|
| 284 | SP_PUSH_MODE_STATE | empty (keepalive/status poll; response carries mode/state/remNum/runTime/remTime/battery…) |
| 513/514 | SP_GIMBAL_HADJ_SPEED / VADJ_SPEED | `"speed:%s;"` |
| 515/516 | HADJ_ANGLE / VADJ_ANGLE | angle strings |
| 517 | SP_GET_GIMBAL_POS | query; response `yaw:/pitch:/roll:` |
| 518 | SP_PUSH_ROTATE_VECTOR | push; quaternion w/x/y/z + compass + alt |
| 519 | SP_SET_GOTO_AU_STATE | `"state:%d;yaw:%f;pitch:%f;lat:%f;track:%d;speed:%d;lng:%f;"` |
| 520 | SP_SET_AHRS_STATE | `"state:%d;"` (1 = enable AHRS stream, 0 = disable) |
| 521/522 | RADJ_SPEED / RADJ_ANGLE | roll-axis jog |
| 523/524 | POS_RESET / EX_AXIS_STA | misc |
| 526 | test | — |
| 527 | SP_SET_YAW | `"compass:%s;lat:%f;lng:%f;"` |
| 530 | SP_CALIBRATE_START | `"step:%d;yaw:%f;pitch:%f;lat:%f;num:%d;lng:%f;"` |
| 531 | SP_SET_TRACK_AU_STATE | `"state:%d;speed:%d;"` |
| 532–534 | YAW/PITCH/ROLL_KEY | key events |
| 535 | SP_SET_GIMBAL_POS | `"yaw:%s;pitch:%s;roll:%s;"` |
| 536 | SP_SET_TRACK_HALF_SPEED | `"halfSpeed:0;" / "halfSpeed:1;"` (**inverted boolean** — app sends `!z`) |
| 537/538 | GET/SET_TILT_STATE | tilt switch |
| 539/540 | GET/SET_DITHER_STATE | `"state:%d;"` |
| 541/542 | GET/SET_LIMIT_STATE | `"state:%d;"` angle limits |
| 543/544 | settling time get/set | `"time:%d;"` |
| 547/548/549 | auto-level enable/state | `"en:%d;"` |

Also present: OMS external-device commands (817–825), cellular remote (808–814), file ops (770–788), camera params
(258–311).

### 9.4 What the stock app actually exposes

Confirmed UI → command mapping in `MainActivity` / `StarrySkyLayout` / `StarHelpDialog`:

- **AHRS toggle**: sends 520 with state 1 while in star/sun shooting modes, 0 on exit or when activity pauses
  (`sensorValuesModleValues`, lines ~1536–1567).
- **Star alignment**: `SP_CALIBRATE_START(2, levelAngle, verticalAngle, lat, num, lng)` where angles come from the
  app's own planetarium math (`DrawSkyTools.getSearchTargetSendData`) for the *selected star* — i.e. the app computes
  expected RA/Dec→Alt/Az locally and the firmware slews there. Multi-star: `num` field.
- **Tracking**: `SP_SET_TRACK_AU_STATE(state, speedType)` where speedType is **0 = sidereal, 2 = lunar**
  (auto-set to 2 only when the aligned target is the Moon). No solar/lunar-rate table beyond this binary choice.
- **Half-speed**: single toggle button sending 536 (inverted). No fine rate trim anywhere in the UI.
- **Manual jog**: rocker pad sends 513/514/521 speed strings.
- **Dither on/off** (540), **angle limits** (542), **settling time** (544), **auto-level** (548): all simple toggles
  inside StarHelpDialog ("star help") settings.
- **Keepalive**: 284 is sent only on specific UI transitions (mode change, calibration dialog dismiss) — **not** on a
  periodic timer. The app relies on TCP liveness, not polling.

### 9.5 What the app does NOT expose (reinforces firmware-side strategy)

- No drift measurement, error display, or periodic position feedback loop — the app consumes pushed quaternions (518)
  purely to draw the AR sky overlay.
- No PID/gain controls, no sync-point submission, no pulse-guiding input, no custom tracking rates beyond
  sidereal/lunar.
- Extra fields appended to any payload would be silently ignored by the app's key-based parser (`substring after
  last ":"` per known key) — confirming R6-style protocol extensions are backward-compatible with the stock client.

### 9.6 Implications for the recommendations

| Recommendation | APK finding impact |
|---|---|
| R1 (rate trims via ADJ_SPEED) | Confirmed commands exist (513–522) but app uses them only for manual jog. Firmware can wire them into trackAu invisibly to the app. |
| R2 (drift correction) | App has zero drift logic — fully firmware territory, no conflict. |
| R3 (silent-skip warnings) | App renders warnings via EventBus actions keyed on existing codes; new codes would be ignored, so reuse existing channels as planned. |
| R4 (AHRS watchdog) | App enables/disables 520 around mode switches — watchdog must tolerate intentional disable (only restart when tracking is active). |
| R6 (richer status) | Key-based parsing confirmed tolerant of extra fields; third-party clients (Alpaca) can consume them. |
| Half-speed semantics | The inverted boolean (536) is a real trap for third-party implementers — document it in the Alpaca driver. |

---

### 9.7 Headless operation (no phone after initial setup)

Porting Alpaca-style control onto the device — or running the driver without Benro Connect present — is viable and
cleaner than the app-dependent flow:

- The app is just another protocol client; nothing in the firmware requires a phone heartbeat (284 is sent only on UI
  transitions, never periodically).
- Star alignment (530), goto/tracking (519/531), and quaternion consumption (518) all work from any TCP client.
- One-time physical calibrations persist in firmware NVRAM; the phone is only needed for those.

**One constraint:** the AHRS stream (520) is app-gated (`state:1` on entering star mode, `state:0` on exit/pause).
A headless client must send 520 itself at session start. For R4's watchdog this means distinguishing "phone left /
tracking stopped" from "AHRS died mid-tracking" — auto-restart should apply **only while tracking is active**.

---

### 9.8 Extending the APK itself (modded client)

Two routes exist now that the app is decompiled:

**Route 1 — patch the existing APK (modded Benro Connect).** Edit smali (apktool) and repackage. Could add UI for
commands the firmware supports but the app never sends (custom tracking rates via 513–522), or a drift readout fed by
the 518 quaternion stream. Catches: must re-sign with our own key (won't install over the official app; settings
lost); every Benro update invalidates the patch; distributing a modified proprietary app is legally messier than
patching owned firmware — personal use only; obfuscated code is fragile (e.g. the inverted halfSpeed boolean).

**Route 2 — build our own companion client.** The full protocol is now documented from ground truth, so a small
open-source Android app (or extending the Alpaca driver) avoids signing, update-fragility, and legal issues entirely.

**Recommendation:** use a patched APK as a fast, zero-firmware-risk test bench for experiments (e.g. custom sidereal
rate via 513/521 during tracking); build fresh on the documented protocol for anything shareable.

---

### 9.9 Open-source replica client — scope and effort

Building an open-source replacement for Benro Connect is tractable because the hard part (protocol reverse-engineering)
is already done: every command code, payload format, framing rule, and transport detail is documented above, and the
Alpaca driver proves the protocol end-to-end.

**Tier A — core astro control (~3–4 weeks part-time; 1–2 focused weekends with AI assistance):**
TCP client + framing (1–2 days), command/response layer for ~20 commands (2–3 days), connect/status screens (3–4
days), jog/goto/tracking/half-speed UI (4–5 days), AHRS gating + quaternion readout (2–3 days), plus hardware testing.
Covers ~90% of astro use.

**Tier B — alignment + settings parity (+1–2 months):** star-alignment UI is the big item (planetarium math and
catalog search — solved libraries exist); dither/limits/settling/auto-level toggles are trivial.

**Tier C — skip:** panorama stitching (heavy native synthesis), AR overlay, cellular remote (needs Benro's cloud),
firmware-update UI (keep official app for that).

**Real bottleneck is validation cadence, not code size:** every feature needs the physical mount under clear skies,
so weekend-only testing dominates the schedule. Recommended shape: Tier A as a focused astro tool; keep the official
app installed for one-time calibrations and firmware updates. Owning the client unlocks everything previously blocked:
custom rate sliders, drift display, sync points, pulse guiding.

---

## Method notes

- Extraction: `ubi_reader` (`ubireader_extract_images`/`extract_files`) on copies of rootfs/appfs UBIFS images.
- Analysis: `strings -n 6 bin/polestar_app` full enumeration; format-string and enum-name cross-referencing against
  the Alpaca driver source (`driver/polaris.py`, `driver/control.py`, `docs/guiding.md`, `docs/quest_paper.md`).
- App-level numeric command codes (e.g., 517/518/520/284) are constructed dynamically in firmware and not visible as
  literals; the mapping rests on Alpaca-side evidence, which is consistent across watchdog, keepalive, and quaternion
  handling code paths.
- Nothing in the firmware package or this repository was modified during the analysis.
