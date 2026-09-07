# K-01 test plan — legacy Pentax qualification

**Date**: 2026-09-07 15:50
**Author**: this agent (OpenPolaris session)
**Subject**: Test the gimbal's libgphoto2 path with a **legacy
Pentax camera** (K-01, 2011, pure PTP, no MTP) to see whether
the path works for cameras outside the modern K-3 III / K-1 II
family.

---

## Why this test matters

The K-3 III and K-1 II are both **modern Pentax** bodies from
2018+ that use **MTP** (K-3 III) or modern **PTP** (K-1 II).
The K-01 is a **2011-era** camera that uses **pure PTP** (no
MTP). If the gimbal's libgphoto2 path works for K-01, it
proves the path is not specific to modern Pentax quirks. If
it fails, it narrows down whether the runtime defect is
Pentax-family-specific or generic.

## K-01 protocol facts (from libgphoto2 source)

| Attribute | K-01 value | Source |
|---|---|---|
| USB vendor | `0x25fb` (RICOH) | libgphoto2 `ptp2/library.c` |
| USB product (PTP mode) | `0x0131` | `{"Pentax:K-01 (PTP Mode)", 0x25fb, 0x0131, 0}` |
| USB product (MSC mode) | `0x0130` | same table; K-01 shares 0x0130 with 645D |
| libgphoto2 camlib | `ptp2` (modern) | comment: "K-01 in its native PTP/MTP USB mode (hardware-confirmed 2026-08-22)" |
| MTP support | **NO** | "Does not support MTP" per IT2 history |
| IT2 (official Pentax tether) support | **NO** | "IT2 never supported this body" |
| Tethered capture support | partial | `ptp2` camlib supports it via generic PTP |
| Live view support | **unknown** | legacy bodies sometimes lack it |

The K-01 is treated as a "generic PTP" device by libgphoto2,
not a Pentax-specific body. The Pentax-specific extensions
(vendor opcodes 0x90xx) are NOT exercised for the K-01.

## What the test will show

The K-01 test gives **two independent signals**:

1. **Does the gimbal's SP layer recognise a non-MTP, non-modern
   Pentax?** If `286` returns
   `manufacturer:ricoh imaging company, ltd.;model:pentax k-1;
   state:1;` (note: the K-01 reports model as `K-1` not `K-01` —
   this is a well-known Pentax firmware quirk), then libgphoto2
   successfully identified it.
2. **Does the gimbal's libgphoto2 work for bodies that use
   `CAMLIBS` rather than `MTP`?** If 264 (capture) returns a
   valid image, the PTP-only path works. If it fails with
   "iolibs not found" (the same patcher#38 defect), the
   runtime defect is body-agnostic.

## Pre-conditions

(All 8 from K1II-SWAP-PLAN.md §0, plus:)

| # | Check | Expected |
|---|---|---|
| 0.9 | K-01 body on bench, battery charged | present |
| 0.10 | K-01 USB cable (data-capable, not charge-only) | present |
| 0.11 | K-01 USB mode set to **PTP** (NOT MTP, NOT MSC, NOT Auto) | confirmed on camera LCD |
| 0.12 | K-01 firmware | at least v1.01 (any post-launch) |

### K-01 USB mode setting

The K-01's "USB" menu has these options:
- **PTP** (default for tethering)
- **PC** (Mass Storage / MSC, exposes the SD card as a USB drive)
- **PictBridge** (printer, not relevant)

The K-01 has **no MTP option** (unlike K-3 III / K-1 II). For
this test, use **PTP**.

## Steps

### §1. Claim hardware ownership

Per K1II-SWAP-PLAN.md §1 — update LIVE-PROGRESS.md with the
new agent name and "K-01 swap, step N of K01-TEST-PLAN.md".
Commit and push. The push IS the claim.

### §2. Verify gimbal state

- `ssh root@192.168.0.1 'cat /app/FwVer'` = `FwVer:4.0.0.32;date:2025.05.09;`
- `lsusb` on the gimbal should NOT show a 25fb: device
  (the previous K-1 II should already be removed; if it is
  still attached, that's fine — the test is independent)
- Port 9090 listening
- `pgphoto` not running (still the patcher#39 issue)

### §3. Power off gimbal, physically swap K-1 II for K-01

Same as K1II-SWAP-PLAN.md §3:
1. Unplug gimbal power
2. Wait 10s
3. Disconnect K-1 II USB
4. Remove K-1 II
5. Mount K-01 on gimbal
6. Connect K-01 USB (data cable, not charge-only)
7. K-01 LCD: menu → "USB" → "PTP" (verify on camera)
8. **DO NOT power gimbal back on yet**

The K-01 is physically smaller than the K-3 III / K-1 II. It
may need a different mount plate or L-bracket. Use the camera
mount that fits the K-01's bottom plate.

### §4. Power on gimbal and wait for boot

Same as K1II-SWAP-PLAN.md §4 — 60s wait, then verify SSH.

### §5. Confirm K-01 enumerates

Expected: `Bus 001 Device NNN: ID 25fb:0131` (K-01 in PTP mode).

If the gimbal cold-boot doesn't see it, try warm plug and
`/sys/bus/usb/devices/*/authorized` re-enumeration per
K1II-SWAP-PLAN.md §5.

If `25fb:0130` appears instead, the K-01 is in MSC mode. Power
down, set PTP on the K-01 LCD, power up, re-check.

### §6. Run the K-01 matrix

Same protocol probes as K-1 II, but the expected results
differ because the K-01 is a different family:

```sh
CLI=tools/cli-probe/build/install/cli-probe/bin/cli-probe

# 286 with init:1 (will only return real K-01 info if pgphoto runs)
$CLI send 286 "init:1;"

# Other probes — same as K-1 II
$CLI status        # 284 mode state
$CLI send 287 ""   # 287 state dump
$CLI send 525 ""   # 525 temperature
$CLI send 770 ""   # 770 file list
$CLI send 775 ""   # 775 SD status
$CLI send 778 ""   # 778 battery

# Camera-specific probes
$CLI send 264 "state:1;bulb:0;c:-1;"   # 264 capture (subtype 4)
$CLI send 291 "state:1;"               # 291 liveview SET ON
$CLI send 311 "mode:near;adj:5;"      # 311 focus adjust
```

**Important**: with the runtime still dead (patcher#38/#39),
all camera-specific probes will fail with timeout. Only the
SP-layer endpoints (284, 525, 770, 775, 778) will return data.

### §7. Compare to K-1 II results

The interesting comparison is:

| Endpoint | K-3 III (MTP) | K-1 II (PTP) | K-01 (PTP) |
|---|---|---|---|
| USB enumerate | `25fb:0189` | `25fb:0183` | `25fb:0131` |
| 286 model string | `pentax k-3 mark iii` | `pentax k-1 mark ii` | `pentax k-1` (legacy quirk) |
| Capture (264) | works (direct CLI) | TBD | TBD |
| Live view (291) | 0xa008 (patcher#36) | TBD | TBD |
| Focus (311) | ret:-1 always | TBD | TBD |

If the K-01's 286 returns a real model string while the
K-3 III's returned `none:none`, that proves the K-01's PTP
handshake works and the K-3 III's failure was MTP-specific.
If the K-01 also returns `none:none`, the failure is body-
agnostic.

## What I can do autonomously NOW

Nothing. The gimbal is not reachable. Same state as 15:47.
This plan is ready for the next agent to execute when the
gimbal is back.

## Cross-references

- [K1II-SWAP-PLAN.md](K1II-SWAP-PLAN.md) — the K-1 II plan
  (analogous structure)
- [libgphoto2 ptp2/library.c](https://github.com/ian-morgan99/libgphoto2/blob/master/libgphoto2/camlibs/ptp2/library.c)
  — K-01 USB ID 0x0131
- [libgphoto2 pentax-utils.c](https://github.com/ian-morgan99/libgphoto2/blob/master/libgphoto2/camlibs/ptp2/pentax-utils.c)
  — K-01 model detection
- [OPENPOLARIS#56](https://github.com/ian-morgan99/OpenPolaris/issues/56) —
  umbrella qualification matrix
- [OPENPOLARIS#62](https://github.com/ian-morgan99/OpenPolaris/issues/62) —
  camera command map (Codes.kt is 11% correct)
- [patcher#38](https://github.com/ian-morgan99/benro-polaris-firmware-patcher/issues/38) —
  runtime iolibs-lookup defect
- [patcher#39](https://github.com/ian-morgan99/benro-polaris-firmware-patcher/issues/39) —
  /app/bin/ empty (pgphoto can't start)
