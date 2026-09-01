# Smoke test against real Polaris — 2026-08-31 17:33 BST

Read-only run of `tools/cli-probe/Smoke.kt` against `192.168.0.1:9090`
(no `-Pdestructive=true`, so setters and the always-blocked codes
never ran). Source: `feat(cli-probe): add Smoke.kt — 73/0/6 against
stub` (commit `36ad5e4` on the `connectivity-tests-for-polaris` branch — see `git log`).
Raw log: [`smoke-polaris-readonly-2026-08-31-1733.log`](./smoke-polaris-readonly-2026-08-31-1733.log).

## Result

**44 PASS / 0 FAIL / 35 SKIP** (read-only). The 35 SKIPs are dominated
by codes that only push unsolicited frames (266, 267, 524, 518, 779,
287, 525, 822, 823, etc.) — the harness treats a no-immediate-reply
as a SKIP. This is the same behaviour as the stub run.

## Hardware evidence captured

| Code | What we learned |
|------|-----------------|
| 780 (DEVICE_INFO) | `hw=1.1.1.2 sw=6.0.0.54 exAxis= sv=1 ov=` |
| 778 (BATTERY_STATUS) | `capacity=100 charge=2` — on charge, no battery |
| 517 (GIMBAL_POS) | `yaw=1.556255 pitch=0.872692 roll=0.000000` — pointing up-and-slightly-east |
| 524 (EX_AXIS_STA) | `state=0` — no external axis attached (matches empty `exAxis=`) |
| 547 (AUTO_LEVEL) | `en=1` — auto-level is on |
| 802 (WIFI_BAND) | `band=0` — 2.4 GHz |
| 543 (SETTLING_TIME) | `time=2` |
| 815 (LANGUAGE) | `sw=0` |
| 814 (TIMEZONE) | `cellular=0 cellhwver=0 usbmode=1` |

## Quirks (worth flagging to the firmware-fix agent)

1. **Code 808 (SYS_VERSION) and code 287 (SETTINGS) don't reply** on
   the real gimbal. The stub replied to both. Either these are
   push-mode-only on real hardware, or the gimbal suppresses them when
   not in a particular state. The control panel currently uses 808 in
   the pre-camera burst.

2. **Code 525 (TEMPERATURE) is push-only** on the real gimbal — but the
   stub returned an immediate frame. The harness SKIPs it. The control
   panel's `BURST_PRE_CAMERA` includes 525 as a GET; on real hardware
   we have to wait for the unsolicited push.

3. **Code 524 returns the gimbal position as a piggy-back** (the same
   `code=517 yaw=...` frame that code 524 itself produces). The parser
   correctly splits both frames.

4. **Code 802 (GET_WIFI_BAND) returns `band=0` on both stub and real**
   but the stub's response to 799 (WIFI_BAND) was a *ret=-1* error on
   the real gimbal — the set/get pair is asymmetric on real hardware.
   The control panel probably needs to use only 802, not 799.

5. **Code 825 (OMS_TASK_LIST) is push-only** on the real gimbal.

6. **Push-mode 266/267 (CAPTURE_STATE/CAPTURE)** never returned an
   immediate frame on the real gimbal — capture state is fully
   push-driven.

## Coverage gap vs control panel

The control panel uses several codes the harness doesn't yet cover:

- **File operations**: download (772), delete (771), protect, rename (798)
- **Movement**: 512, 513, 514, 515 (goto/slew), 530 (settings-reset)
- **Capture**: 256, 257 (capture start/stop), 279 (capture mode set)
- **OMS task control**: 825 (already covered as push), task start/stop
- **Wi-Fi connect**: 804
- **Settings**: 530 (already blocked), 526 (SKIP'd on real), 815/816
- **Security Q/A**: 287 (SKIP'd on real)
- **Time sync**: 813 (blocked)

The harness covers the read-side of every control-panel refresh
mechanism. The write-side is gated behind `-Pdestructive=true`.

## Safety

- 5 always-blocked codes never ran: 812 (reboot), 813 (shutdown),
  776 (SD-format), 810 (fw-upgrade), 530 (settings-reset).
- No setters ran (DESTRUCTIVE=false).
- All write operations in the harness are gated behind
  `-Pdestructive=true` and that flag was not set.
