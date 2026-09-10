# Probe run 2026-09-04 12:29 — post-reboot first-look (wake-and-probe.sh)

Captured via `wake-and-probe.sh` after the user hard-rebooted the gimbal.
The device came up ~20 min before capture (`up 20 min`). This is a
**pre-firmware-upgrade** state: FwVer unchanged, `/app/sd/FwPkt.zip` still
on the SD card from the Aug 31 upload.

## Reachability

- SSH `root@192.168.0.1` — **OPEN** (stages 1–4 of wake-and-probe.sh succeeded).
- Uptime: 20 min at capture time. Load avg 1.14 / 1.03 / 0.78.

## FwPkt.zip (still on device)

- Path: `/app/sd/FwPkt.zip`
- Size: 68 484 216 bytes
- MD5: `92da888387b14dc02976b5fa22b94067` (matches the padded build in
  `KNOWLEDGE-SHARE-FOR-PATCHER.md`)
- mtime: `Aug 31 2026` — untouched since the original upload.

→ **H1 (wrong path) is narrowed:** the zip *is* at `/app/sd/FwPkt.zip`,
  which is the only candidate watch path that exists (`/app/sdcard/`,
  `/mnt/sd1/`, `/firmware/` all absent). The watcher, if it exists,
  monitors this path.

## OmsPkt.zip (new finding)

- Path: `/app/sd/OmsPkt.zip`
- Size: 68 484 216 bytes (identical size to FwPkt.zip)
- mtime: `Sep 1 2026`

→ The OMS upgrade path (`SP_OmsUpgradeCheckFwPkt` @ `0x76f24`, see
  HANDOVER §4.4) has a zip on-device too. Both `getFwInfo.sh` and
  `getOmsFwInfo.sh` are present in `/app/`. The Oms path may be the
  active trigger rather than (or in addition to) the FwPkt path.

## Extracted FwPkt/ directory — gone

The 09-01 probe (`live-probe-2026-09-01-11-26/SUMMARY.md`) recorded an
extracted `/app/sd/FwPkt/` directory (dated Sep 1 10:52) containing the
unpacking target layout. In this 09-04 capture, `/app/sd/` contains only:

```
FwPkt.zip  HDR/  Lapse/  OmsPkt.zip  focusStack/  normal/  panorama/
```

No `FwPkt/` subdirectory. Either the device cleaned it up on reboot, or
the extraction was transient. This is consistent with the handover's
observation that "the `/app/sd/FwPkt/` working directory was never
created" (i.e. the install flow never completed far enough to leave a
persistent extracted dir).

## Install daemon

- `polestar_app` running as PID 248 (`./polestar_app`).
- No separate `upgrade` or `fwPkt` daemon in the process list.

→ **Open question #2 (handover §10) answered:** the install logic lives
  inside `polestar_app` itself; there is no standalone watcher process.

## Mlog location correction

The probe script greps `/app/mcu_debug/Mlog_*`, which does not exist on
this firmware build. The actual log files are:

| File | Size | mtime | Notes |
|------|------|-------|-------|
| `/app/Mlog.txt` | 28 113 B | Sep 4 12:29 | Main Mlog, now has content (was 0 B on 09-01) |
| `/app/Clog.txt` | 493 B | Sep 4 12:29 | Companion log |
| `/app/error.log` | 384 B | Sep 4 12:29 | Was 0 B on 09-01 |

→ **The Mlog path in the probe script and handover §6 command #3 is
  wrong for this firmware.** It should be `/app/Mlog.txt`, not
  `/app/mcu_debug/Mlog_*`. The 28 KB of content (written at boot,
  Sep 4 12:29) has **zero** `FwPkt`/`firmware`/`crc`/`upgrade` lines —
  confirming the install flow was not triggered on this boot.

## USB-UART gate

- `/dev/ttyUSB*` — still absent (same as 09-01).
- USB device tree: `Bus 001 Device 002: ID 1a40:0101` (Broadcom BCM43xx
  WiFi chip, not a UART adapter).
- `usb_f_acm.ko` and `usb_f_uvc.ko` are in `/app/komod/` but not loaded.

→ The `SP_TtyUsbUartInit` gate (which `SP_UpgradeCheckFw` calls before
  the MD5 comparison, per HANDOVER §4.4) likely fails because no USB
  serial device is present. This is a strong candidate for why the
  install flow is "not being triggered" — the pre-check bails out
  before it ever reaches the MD5 comparison.

## dmesg (last 80 lines, filtered)

Only WiFi firmware lines:
```
Firmware up: op_mode=0x0005, MAC=48:e7:da:d4:b5:73
Firmware version = wl0: Mar 22 2019 00:03:25 version 7.45.100.18 (r709126 CY) FWID 01-a562d688
```
No USB/UART/firmware/upgrade lines in the recent dmesg window.

## Files in this directory

| File | Source |
|------|--------|
| `01-first-look.txt` | Stage 5 of wake-and-probe.sh (on-device probe) |
| `02-cmdline.txt` | Stage 6: `cat /proc/cmdline` |
| `02-mtd.txt` | Stage 6: `cat /proc/mtd` |
| `02-os-release.txt` | Stage 6: `cat /etc/os-release` (empty — BusyBox) |
| `02-version.txt` | Stage 6: `cat /proc/version` |
| `SUMMARY.md` | This file |

No `03-Mlog_*` file: the script's Mlog pull targets `/app/mcu_debug/Mlog_*`,
which does not exist (see Mlog location correction above). The actual
Mlog at `/app/Mlog.txt` was not pulled by this run.

## Implications for the FwPkt investigation

1. **H1 (wrong path) is effectively ruled out.** The zip is at the only
   candidate path that exists.
2. **The USB-UART gate is the leading hypothesis** for why the install
   flow never triggers: `SP_TtyUsbUartInit` likely fails (no `/dev/ttyUSB*`),
   causing `SP_UpgradeCheckFw` to bail before the MD5 comparison.
3. **The OmsPkt path is now in scope.** `OmsPkt.zip` + `getOmsFwInfo.sh`
   are on-device; the OMS upgrade check (`SP_OmsUpgradeCheckFwPkt`) may
   have a different (or absent) USB-UART gate.
4. **Next step when the device is next awake:** pull `/app/Mlog.txt`
   (28 KB) and `/app/error.log` (384 B) for offline analysis, and check
   whether `polestar_app` logs a TtyUsbUartInit failure at boot.
