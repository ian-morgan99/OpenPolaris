# Firmware update evidence — 2026-08-31

## Pre-probe state (captured 18:18 UTC, device uptime ~58min)

User signalled "polaris is on again now if you want to continue probing whilst
we explore the firmware fix". Before any 9090 probe, captured device state.
**Conclusion: an active firmware push is in progress via `scp`; defer 9090
probes until the user signals done.**

### Key observations

- `polestar_app` PID 248, listening on `0.0.0.0:9090` — protocol daemon is up
- Two `scp -t /app/sd/FwPkt.zip` processes (PIDs 1370, 1371) — **active push in
  progress**; file size growing across consecutive checks
  (4.9 → 5.4 → 5.7 → 6.9 → 9.2 MB at 18:18:05 → 18:19:05)
- Installed FwVer: `4.0.0.32` dated 2025.05.09 — **upgrade not yet applied**
- Mlog tail shows the normal `GimbalUartRxMsgProcTask[398]:Tempa509ca361d0000265a`
  push (code 525 GET_TEMPERATURE) on a **30 s cadence** (18:18:05, 18:18:35,
  18:19:05), not the 2 s the user mentioned earlier. This is the in-process
  gimbal UART receive path; the Mlog line confirms the app is alive and
  processing but the *9090 path* (which logs at `SP_SendMsgToApp`) is the
  *outbound* channel.
- Clog.txt is empty — no app-level errors
- `/app/sd` is mounted from `/dev/mmcblk0p1` (vfat) — the SD card
- `/app/sd/HDR/` exists but is empty — temp dir for firmware header validation,
  presumably created on boot
- WiFi_BT_CFG.info: `id:d13e86;band:0;` (band 0 = 2.4 GHz)

### Why no 9090 probe yet

Sending protocol commands while `scp` is still writing `/app/sd/FwPkt.zip`
and `polestar_app` is alive risks:
- The user-initiated upgrade path could be interleaved with our probes,
  leading to confused state in the Mlog/Clog
- A "fix in progress" the user is exploring could be a code change to the
  app that responds differently to GETs than the old build
- The push could fail mid-write if we add load

The right call is to **wait for the user to signal "done"**, then:
1. Confirm FwVer has changed (upgrade applied)
2. Pull the new FwPkt.zip for offline analysis
3. Capture fresh 9090 wire format against the new build

## Pre-upgrade snapshot to capture after push completes

- `md5sum /app/sd/FwPkt.zip` (final)
- `cat /app/FwVer` (should change)
- `ls -la /app/sd/HDR/` (may now have header files)
- New Mlog tail (look for `SP_UpgradeCheckFw`, `SP_UpgradeFirmwareProC`,
  `SP_UpgradeReboot`, `SP_TtyUsbUartInit`)
- Last 20 Clog lines
- If port 9090 dropped during upgrade, confirm it came back

The full list above is scripted as
[`post-fw-update-probe.sh`](../gimbal-ssh-2026-08-31/post-fw-update-probe.sh) —
run it after the push completes and `wake-and-probe.sh` (or a manual
BT-wake + AP/SSH connect) has put you back on the gimbal. Pass
`--no-protocol` to skip the one-shot 525 frame if the gimbal is not on
the new build yet, or `--baseline <path>` to diff FwVer against a
non-default baseline file.

## Next probe plan (post-upgrade)

1. GET_TEMPERATURE=525 — confirm 9090 alive + capture fresh wire format
2. GET_GIMBAL_POS=517 — capture position push format
3. GET_FW_INFO — capture firmware metadata format (new build number)
4. SP_TEST=526 — attempt the 8-step debug handshake (deferred to
   `investigate-sp-test-526` if not already done)
5. Optional: replay confirmed codes (258, 259, 260, 261, 262, 263, 264, 265,
   280, 291, 292, 311, 518-565, 524, 525, 543, 547, 780) against the new build
   to verify they still decode the same way
