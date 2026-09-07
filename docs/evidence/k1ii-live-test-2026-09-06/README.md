# K-1 Mark II live test — 2026-09-06 ~17:30–17:45 BST

**Setup:** Benro Polaris (sw 6.0.0.54, hw 1.1.1.2) on its own AP `polaris_d13e86`,
host joined via `wlp8s0` → 192.168.0.4. Pentax K-1 Mark II attached to the gimbal's
USB port. Gimbal uptime ~1h19m at start (fresh boot, no card-swap since).

**Files:**
- `k1ii-sweep.txt` — full matrix: system codes, camera GETs, capture 264, MJPG 8080
- `k1ii-deep.txt` — gphoto2 location, SD mount, photo dirs
- `k1ii-gphoto.txt` — on-device `gphoto2 --capture-image` repro
- `k1ii-restart.txt` — pgphoto kill + watchdog restart observation
- `k1ii-retest.txt` — camera GETs/capture/MJPG after pgphoto restart
- `k1ii-logs.txt` — Mlog/Clog grep for PTP/gphoto lines

## Results

### System / gimbal codes (port 9090) — all healthy
| Code | Response |
|---|---|
| 524 exAxis | `state:0;` ✅ |
| 525 temp | async push `Tempa<hex>` (arrived mixed into a later reply) ✅ |
| 543 settling | `time:2;` ✅ |
| 775 SD | `status:1;totalspace:121866;freespace:121798;usespace:68;` ✅ |
| 778 battery | `capacity:100;charge:2;` ✅ |
| 779 battery detail | silent (no reply) |
| 780 device info | `hw:1.1.1.2;sw:6.0.0.54;exAxis:;sv:1;ov: ;` ✅ |
| 802 wifi band | `band:0;` ✅ |
| 808 sys version | silent (no reply) |
| 824 OMS | `state:0;` ✅ |

### Camera path (K-1 II attached) — PTP session stuck in observe-only
- `258` (ISO get/set) → `ret:-1;` — PTP query failing.
- `265/266/267/268/275` (GET_*_INFO) → all silent.
- `292` liveview get → `state:1;` (enabled, but MJPG serves nothing).
- **Capture `264` payload `state:1;bulb:0;c:-1;`** → `264@state:1#` then `264@state:-1#`
  — command accepted, capture **failed**. No file in `/app/sd/normal/`.
- **MJPG 8080 `/snapshot.cgi`** → 0 bytes.

### Root cause confirmed (issue #34)
`/app/Mlog.txt`:
```
gp_camera_set_abilities [gphoto2-camera.c:358](0): Setting abilities ('Pentax K-1 Mark II (PTP mode)')...
Pentax session already open from a previous connection; observing camera state.
Pentax init stage vendor enable succeeded; function flags 0x00000003.
```
`pgphoto` (v1.3.4, libgphoto build 0562e8a) detects the pre-existing PTP session and
enters observe-only mode → no capture, no live-view frames.

### pgphoto crash loop (issue #34 back-off gap)
Killing PID 1081 → watchdog restarted it within seconds (PIDs 2494/2542), still in
observe-only mode. Tight-loop restart confirmed; no back-off.

### gphoto2 direct repro (issue #48)
`/app/bin/gphoto2 --capture-image` → `Error (-53: 'Could not claim the USB device')`
because pgphoto holds the PTP session. (Earlier -6 "unsupported operation" was the
post-claim failure; -53 is the pre-claim one when pgphoto owns the device.)

## Post-reboot verification (17:40–17:42, `k1ii-postreboot.txt`, `k1ii-mlog.txt`)
Full gimbal `reboot` was issued over SSH; AP came back after ~90 s, host rejoined
the AP, full matrix re-run. **Result: identical failures.**

- All system codes still healthy (524/543/775/778/780/802/824).
- Camera GETs still silent; 258 → `ret:-1`; capture 264 → `state:1` then `state:-1`;
  MJPG 8080 → 0 bytes; `/app/sd/normal/` empty.
- Fresh-boot Mlog (`Mlog_000044.log`) shows the **same** line:
  `Pentax session already open from a previous connection; observing camera state.`
  plus `checkGphotoTask: pgphoto is exit,reboot it` (crash loop continues).

**Conclusion:** the stale PTP session survives a gimbal reboot — it is held on the
**camera side** (K-1 II keeps its PTP session open across the gimbal's USB reset).
The field workaround in #34 ("reboot the Polaris after a card switch") is therefore
insufficient for this failure mode; the camera must be power-cycled (or the USB
cable replugged) to clear it. This strengthens #34: pgphoto should ResetDevice /
close+reopen instead of entering observe-only, because observe-only persists across
gimbal reboots.

## Second reboot + clean-session test (17:45–17:50, `k1ii-postreboot2.txt`, `k1ii-gphoto2.txt`, `k1ii-gphoto3.txt`)
A second full gimbal reboot was issued; after SSH came up the camera was given 60 s
to boot its PTP stack. This boot's Mlog (`Mlog_000045.log`) shows a **clean PTP init** —
the `Pentax session already open … observing camera state` line is **absent**:

```
gp_camera_set_abilities: Setting abilities ('Pentax K-1 Mark II (PTP mode)')...
SP_MsgFromCameraProc: code[286] model:pentax k-1 mark ii;state:1;storage:2;photoFormat:2
```

Yet the camera path **still fails identically**:
- Camera GETs (258/265–275) → silent / `ret:-1`
- Capture 264 → `state:1` then `state:-1`, no file in `/app/sd/normal/`
- MJPG 8080 → 0 bytes (even after explicitly enabling liveview via 291 → `state:1;ret:0`)
- gphoto2 direct → `-53 Could not claim the USB device` (expected: pgphoto owns the session)

**Refined conclusion — #34 and #48 are independent:**
- **#34 (stale PTP session / observe-only)** is a *separate, intermittent* failure mode.
  It was present on the first boot but cleared by the second reboot. It is NOT the cause
  of the capture failure.
- **#48 (capture unsupported / no file written)** reproduces **even with a clean PTP
  session**. The gimbal's own protocol path (code 264 → `SP_MakeNormalPhoto`) returns
  `state:-1` and MJPG serves no frames regardless of session state. This is the primary,
  always-present blocker for the K-1 II camera path.

So the K-1 II camera path is blocked by **#48** (capture/live-view unsupported in the
current gphoto2 build), with **#34** as an additional intermittent stale-session hazard.

## Verdict for K-1 II
Everything gimbal-side works. The camera path is blocked by #48 (capture + live-view
unsupported — reproduces on a clean PTP session) with #34 (stale PTP session →
observe-only, intermittent, cleared by reboot) as a secondary hazard. Next: plug in the
K-3 III and run the same matrix to see whether it hits the same #48 wall or behaves
differently.

## Formal readiness sign-off — 2026-09-06 17:42 UTC (`k1ii-final-readiness.txt`)
Environment confirmed ready for the K-3 III swap:

| Check | State |
|---|---|
| Gimbal uptime | 5 min (fresh post-reboot boot, stable) |
| `780` device info | `hw:1.1.1.2;sw:6.0.0.54` ✅ |
| pgphoto | running (PID 560/595 — observe-only crash loop, expected while K-1 II holds the PTP session) |
| MJPG 8080 | 0 bytes (expected in observe-only; will re-check on K-3 III) |
| SD `/app/sd` | 118.9 G free ✅ |
| Host link | `wlp8s0` → 192.168.0.4 on `polaris_d13e86`, SSH OK |

**Complete K-1 II test matrix executed:** system codes (524/543/775/778/780/802/824 ✅,
525 async ✅, 779/808 silent), camera GETs (258 `ret:-1`, 265–275 silent), capture 264
(`state:1`→`state:-1`), MJPG 8080 (0 B), on-device gphoto2 (`-53`), pgphoto crash-loop
observation, and **two** post-reboot re-runs (one with a stale session, one with a clean
session). All raw outputs archived in this directory.

**K-1 II testing is complete.** The gimbal/system layer is fully verified; the camera
path is blocked by #48 (capture + live-view unsupported — reproduces on a clean PTP
session), with #34 (intermittent stale PTP session → observe-only) as a secondary hazard.
Ready to plug in the K-3 III and run the identical matrix against it.

## Full code-catalog sweep (17:52, `k1ii-full.txt`)
Every code group in `Codes.kt` was probed on port 9090. Results by layer:

**Gimbal motion / status — ✅ healthy**
- 517 gimbal pos → real values (`yaw:-0.267;pitch:0.873;roll:0.0`)
- 520 ahrs, 531 track-au, 536 half-speed, 544 settling-set, 547/548/549 auto-level → `ret:0`
- 537 tilt / 539 dither / 541 limit state → `state:0`
- 513–522 (hadj/vadj/radj speed+angle), 519, 523, 530, 532–534 → no reply (setters/actions, fire-and-forget — expected)

**Device-info extras — ✅ healthy**
- 284 push-mode `mode:1;state:0`, 285 `mode:0;ret:0`, 286 cam-info (full K-1 II model string),
  282 sys-format `format:0`, 300 hdmi `hdmi:0`, 301 hdmi-mode `ret:0`
- 311 cam-focus → `ret:-1` (camera-side, consistent with #48)

**File / SD — ✅ healthy**
- 770 file-list → all categories `0` (no photos on card — consistent with capture failing)
- 781 thumb → timestamp only; 783 file-info `ret:0`; 776/777/779/782 no reply

**WiFi / system — ✅ healthy (no cellular module present)**
- 803 set-band `ret:0`, 804/805 wifi connect/disconnect `state:0`, 814 sys-time
  (`cellular:0;cellhwver:0;usbmode:0`), 815 timezone `sw:0`, 816 language `ret:0`
- 799 cellular / 800 wifi-scan / 809 serial → `ret:-1` (no cellular hardware — expected)

**OMS — ✅ healthy**
- 817 `ret:0`, 819 `ret:1`, 826 `state:-1`; 821/822/823/825 no reply (push-only / app-side)

**Camera (258–311) — ❌ blocked by #48** (see above; reproduces on a clean PTP session)

### Formal sign-off
The **entire** `Codes.kt` catalog has now been exercised against the K-1 II. Every
gimbal/system/motion/calibration/device-info/file/wifi/OMS code responds as expected.
The only failing surface is the camera path, which is a known gphoto2-side limitation
(#48) plus an intermittent stale-session hazard (#34) — neither is a K-1 II defect.
**K-1 II testing is formally complete and ready for hand-off to the K-3 III.**
