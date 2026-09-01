# Live probe 2026-09-01 11:26 BST — Polaris awake, pre-camera burst captured

Captured during this session after the user reported the polaris was on again.
The gimbal is responsive; this is a **pre-firmware-upgrade** state (the upgrade
attempted in the previous session did not apply — FwVer is unchanged from
`4.0.0.32;date:2025.05.09;`, and `/app/sd/FwPkt.zip` is still on the SD card
from the earlier interrupted attempt).

## Reachability

- WiFi AP `polaris_d13e86` visible.
- `ping 192.168.0.1` — 12.8 ms.
- TCP 9090 (polestar) — **OPEN** (PID 4511 = `./polestar_app`).
- TCP 22 (SSH) — **OPEN**.

## FwVer

`FwVer:4.0.0.32;date:2025.05.09;` — identical to baseline
`docs/evidence/firmware-update-2026-08-31/01-pre-probe-state.txt`.

→ The firmware update attempted previously **did not persist** across the
  reboots. See [`01-fwver.txt`](01-fwver.txt).

## FwPkt.zip (still on device)

- Path: `/app/sd/FwPkt.zip`
- Size: 68 484 216 bytes (68 MB)
- MD5: `92da888387b14dc02976b5fa22b94067`
- mtime: `Aug 31 21:38` (i.e. from the original upload during the firmware-fix
  session, untouched since)

The extracted `/app/sd/FwPkt/` directory contains the unpacking target layout:
`camera/{rootfs.ubifs,uImage,appfs.ubifs,config}`,
`gimbal/{polaris413_2.0.0.22.bin,polaris403_2.0.0.22.bin}`,
`firmwareInfo`, `crcInfo`. See [`03-fwpkt-dir.txt`](03-fwpkt-dir.txt).

The unpacked dir is dated `Sep  1 10:52` — earlier today, from the previous
upgrade attempt that did not finish installing.

→ **Implication:** a fresh FwPkt.zip is still on the SD card and ready to
  be re-flashed. The watcher is in partial-state; Open Polaris's
  `firmwareUpload` flow can pick this up and push it again with the same
  wire sequence (810 → 784 → 794×N → 795 → 811 → 812), see
  [CAPABILITY-GUIDE §4](../../CAPABILITY-GUIDE.md#4-firmware-upgrade--the-deep-dive).

## Live pre-camera burst (canonical codes 808..525)

Captured via `./gradlew :tools:cli-probe:liveBurst --args="192.168.0.1 9090 --full"`.
See [`09-liveBurst-full.txt`](09-liveBurst-full.txt).

| Code | Constant | Response | Notes |
|------|----------|----------|-------|
| 808 | `SP_OTA_CHECK` (per decompile; not in our Codes.kt as a query) | (no response) | push-mode / write-only |
| 809 | `OTA_STATUS` (per decompile) | `ret:-1;` | OTA not in progress (consistent — FwVer unchanged) |
| 802 | `GET_WIFI_BAND` | `band:0;` | 2.4 GHz |
| 778 | `BATTERY_STATUS` | `capacity:100;charge:2;` | 100 % charged, charge level 2 |
| 779 | `BATTERY_DETAIL` | (no response) | push-mode |
| 775 | `FILE_SD_STATUS` | `status:1;totalspace:121866;freespace:121644;usespace:222;` | SD present, ~121 GB free of 121.8 GB |
| 824 | `OMS_RUN_STATE` | `state:0;#` (followed by 775+778 push tail) | OMS idle, gimbal also pushes SD/battery |
| 524 | `EX_AXIS_STA` | `state:0;#517@yaw:-0.257268;pitch:0.872676;roll:0.000000;#` | Pan axis idle, attitude push 517 ≈ -14.7° pitch |
| 543 | `GET_SETTLING_TIME` | `time:2;` | 2 second settling window |
| 780 | `DEVICE_INFO` | `hw:1.1.1.2;sw:6.0.0.54;exAxis:;sv:1;ov: ;` | **The "sw" is the camera head firmware version, not the gimbal MCU** — the gimbal MCU version lives in the `gimbal/polaris4*_2.0.0.22.bin` files |
| 525 | `GET_TEMPERATURE` | (no response) | push-mode — arrives unsolicited later |

`525` is a **push-mode** code: it doesn't reply when you ask, it streams
when the head wants to (probably every N seconds or on threshold). The
`wake-and-probe.sh` raw-525 probe failed because it tried to send "525\n"
and read a response, which there isn't one. Need to wait for the push
to arrive, not poll.

## First-look on-device inspection

See [`01-first-look.txt`](01-first-look.txt). Highlights:

- `/app/sd` has 11 entries; `error.log` is 0 bytes dated `Sep  1 11:26` —
  no daemon errors.
- `/app/sd/run` is a directory (not a file), dated `Sep  1 10:55`.
- `/app/restart_polestar.sh` dated `Aug 31 23:04` — that is from the
  first firmware-fix session.
- No `Mlog_*` files were found in `/app/mcu_debug/` (stage 6 of
  wake-and-probe said "no Mlog_* found on device") — the camera head
  mlog subsystem is either not running, or rotates to a path we don't
  know yet. Worth chasing in a future session.
- `/proc/modules` is empty; the kernel has no loadable modules
  loaded. The 9090 server is built-in.

## What this unblocks

- **`wake-and-probe-pending`** — DONE (this run).
- **`replay-against-gimbal`** — DONE for the canonical pre-camera
  burst. Further replays of the audit's camera/file/OMS blocks require
  a UI consumer (they go through `CommandTable` descriptors with no
  live test yet).
- **`investigate-sp-test-526`** — the 525 code is a push, not a
  query. We confirmed the live gimbal does NOT respond to "525\n" on
  9090. To investigate further we need a long-lived listener that
  reads unsolicited frames, not a request/response probe. That
  listener is the next probe to add (suggested file:
  `tools/cli-probe/src/main/kotlin/dev/openpolaris/probe/PushListener.kt`).
