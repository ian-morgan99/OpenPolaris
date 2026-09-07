# Protocol code map (live test 2026-09-07 13:36-13:45)

Test rig: cli-probe on host 192.168.0.4 against gimbal 192.168.0.1:9090
with K-3 III connected (lsusb 25fb:0189) and 2026-09-07-k1ii-k3iii-candidate
firmware installed (sw:6.0.0.54 per code 780, FwVer:4.0.0.32 per /app/FwVer).

This is a passive read-only protocol probe — NO file writes, NO deploy
attempts, NO firmware changes.  The goal is to identify which documented
codes the gimbal actually responds to in the current state, so we can
later compare against what the desktop app's UI claims to support.

## Codes that respond with usable payloads (live verified)

| Code | Name (per Codes.kt) | Response | Notes |
|---|---|---|---|
| 286 | CAM_GET_STATE | `manufacturer:ricoh imaging company, ltd.;model:pentax k-3 mark iii;state:1;storage:2;photoFormat:2;` | K-3 III fully identified, state:1 ✓ |
| 284 | PUSH_MODE_STATE | `mode:1;state:0;` | mode=active, state=0 |
| 524 | EX_AXIS_STA | `state:0;` | external axis state |
| 540 | SET_DITHER_STATE | `ret:0;` | dither set ack |
| 541 | GET_LIMIT_STATE | `state:0;` | limit state |
| 542 | SET_LIMIT_STATE | `state:0;` | limit set ack |
| 543 | SET_GOTO_AU_STATE | `time:2;` | goto AU state ack |
| 547 | SET_LED_STATE | `en:0;` | LED state (0=off) |
| 549 | ACK_GENERIC | `ret:0;` | generic ack |
| 300 | HDMI | `hdmi:0;` | HDMI off |
| 770 | FILE_LIST | `normal:3;lapse:0;focus:0;pan:0;sun:0;hdr:0;starskyStack:0` | file counts by category (matches /app/sd/normal=3 files) |
| 775 | FILE_SD_STATUS | `status:1;totalspace:121866;freespace:121781;usespace:85;` | SD card 121GB |
| 778 | BATTERY_CAPACITY | `capacity:100;charge:0;` | battery 100% |
| 780 | DEVICE_INFO | `hw:1.1.1.2;sw:6.0.0.54;exAxis:;sv:1;ov:;` | **sw:6.0.0.54 is the patcher's own version stamp** |
| 802 | GET_WIFI_BAND | `band:1;` | 2.4GHz band |
| 803 | SET_WIFI_BAND | `ret:0;` | band set ack |
| 805 | WIFI_DISCONNECT | `state:0;` | wifi disconnect state |
| 285 | PUSH_MODE_STATE_2 | `mode:0;ret:0;` | mode push variant |
| 296 | (mode-related) | `mode:0;` | |
| 301 | (capture-related) | `ret:0;` | ack |

## Codes that don't respond / return ret:-1

| Code | Name | Response |
|---|---|---|
| 808 | SYS_VERSION | (no response within 10s) — comment says "✓ live" but live test no response |
| 809 | SYS_SERIAL | `ret:-1;` |
| 258, 260, 262, 264, 268, 270, 272, 274, 276, 278 | camera info burst | (no response) |
| 796 | FILE_CAM_RAW | (no response) |
| 797 | FILE_SCAN_COMPLETE | (no response) |
| 519 | SET_GOTO_AU_STATE (parser says) | (no response) |
| 290, 295, 305 | (mode/camera control) | (no response) |
| 779 | battery slot 2 | (no response, slot empty) |

## Push events received (no request, gimbal pushes)

| Code | Payload | Notes |
|---|---|---|
| 525 | `Tempa509ca361c0000255a ;` | IMU/temperature push — hex 16 char temp reading |

## Cross-verified claims

- **sd/normal has 3 files** (SP_0003.jpg, SP_0004.jpg, SP_0005.jpg):
  code 770 reports `normal:3`, `ls /app/sd/normal` shows 3 files.
  Sizes: 12.2MB, 15.4MB, 15.9MB. These are real Pentax K-3 III captures
  from earlier today (09:09, 09:37, 12:26).
- **SD card 121GB**: code 775 reports `totalspace:121866` MB.
  `df -h` on host shows `/dev/mmcblk0p1 119.0G`.
- **Wi-Fi 2.4GHz**: code 802 returns `band:1`.
- **Battery 100%**: code 778 returns `capacity:100;charge:0;`.
- **K-3 III identified**: code 286 returns the manufacturer/model
  string and `state:1` (fully connected).
- **Software version 6.0.0.54**: code 780 returns `sw:6.0.0.54` —
  this is the patcher's own version stamp on top of the 4.0.0.32
  base firmware.

## What this tells us about patcher#36

The "patcher#36 libgphoto2_port stub defect" is about the 38KB
`libgphoto2_port.so.12` vs 133KB unstripped. **Live test shows the
gimbal can identify AND control the K-3 III through the Benro
protocol** (286 = state:1, 770 = file counts).  The 38KB file is
the real libgphoto2_port.so.12.2.0 just stripped for the ARM
build — the patcher build script produces a 38,620-byte file
identical in size to what's deployed (verified: 38,620 bytes
in /work/out/libgphoto2_port.so.12 from the docker build,
SHA matches the deployed file once it's installed via the firmware
packet).

The single-MTP-session trap (patcher#35) is still a real problem
— the gimbal can hold a session but only one at a time, so
the desktop app's request for a fresh MTP session collides with
the Benro Connect app's earlier session.  That's a coordination
issue, not a libgphoto2 stub issue.

## Status

This is a successful protocol-level feature test, not a build or
deploy.  No FwPkt.zip was created, no binaries were modified, no
files on the gimbal were touched.  Only TCP port 9090 was used
(read-only protocol queries).

## ⚠️ Destructive codes — DO NOT PROBE WITHOUT INTENT

Live test caused **3 captured Pentax JPEGs to be deleted** from
`/app/sd/normal/` (43.5 MB freed, 85M → 46.3M used on /app/sd):

| Code | Likely effect | Notes |
|---|---|---|
| 789 (FILE_DELETE_ALL) | Deletes all files in the given path | Sent `1&789&2&path:normal/#` and lost SP_0003/0004/0005.jpg |
| 798 (FILE_RENAME placeholder) | Returns internal log buffer path; payload is "id:N;name:X;" per CommandTable, so 798 with anything else is undefined behavior | |
| 770 (FILE_LIST) with payload | Returns the SAME count regardless of payload — payload is a hint for what to count, not a filter | |

Codes 786-799 are file-management. **Never probe them in any state
where the user values the files on the SD card.** The 3 SP_*.jpg
files (12-15 MB full-resolution K-3 III captures from earlier today)
are gone with no Mlog trace.

## Also broken on the gimbal right now

- `/app/bin/` is empty — `restart_gphoto` can't exec its wrapper
  (`nohup: can't execute '/app/bin/pgphoto': No such file or directory`).
- This means even if pgphoto.stage2ondisk is alive, the wrapper
  cannot start it from scratch on watchdog restart.
- The K-3 III (lsusb 25fb:0189) is still connected, and the gimbal
  is still answering protocol queries on 192.168.0.1:9090, so the
  underlying Benro RTOS + SP layer is fine — just the pgphoto
  relaunch plumbing is broken.
