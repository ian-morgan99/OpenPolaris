# Open Polaris — Real-Device Smoke Test Checklist

Ordered validation against a physical Benro Polaris. Run top to bottom;
each step assumes the previous passed. Record pass/fail + notes per step.

**Setup:** mount powered on, Wi-Fi joined to the mount AP, app connected
(`1&100&2&#` handshake OK, status pane shows mode/battery).

## 1. Core (verified against firmware ground truth — low risk)

| # | Action | Expected | Pass |
|---|--------|----------|------|
| 1.1 | Connect → status poll (284) | Mode/battery populate | ☐ |
| 1.2 | Position read (517) | Az/Pitch degrees shown | ☐ |
| 1.3 | Jog H/V (513–516), 500 ms taps | Gimbal moves, position updates | ☐ |
| 1.4 | AHRS toggle (520) | Quaternion stream starts/stops | ☐ |
| 1.5 | Half-speed toggle (536) | Rate halves; wire value is INVERTED (0=on) | ☐ |
| 1.6 | Goto az/alt (519) | Slew to target, `ret:0`, tracking engages | ☐ |
| 1.7 | Tracking start `state:1;` (531) | Sidereal tracking, star test: no drift over 2 min | ☐ |
| 1.8 | Tracking stop `state:0;` (531) | Tracking halts | ☐ |

## 2. Speed field (partially verified — medium risk)

Firmware format is `state:%d;speed:%d;`. Per ogecko/alpaca driver:
**0 = sidereal, 2 = lunar**. Solar rate not exposed via 531.

| # | Action | Expected | Pass |
|---|--------|----------|------|
| 2.1 | Start with `state:1;speed:0;` | Tracks at sidereal rate | ☐ |
| 2.2 | Start with `state:1;speed:2;` | Tracks at lunar rate (moon test) | ☐ |
| 2.3 | Try `speed:1;` | Document behavior (solar? rejected? sidereal?) | ☐ |

If 2.1/2.2 fail, fall back to minimal `state:1;` and file an issue.

## 3. Camera (codes INFERRED — highest risk)

⚠️ Camera commands can interrupt a running capture. Test with no
timelapse running and a memory card installed.

| # | Action | Expected | Pass |
|---|--------|----------|------|
| 3.1 | Each camera GET code (258–269 range) | Response with expected keys, no error frame | ☐ |
| 3.2 | Read current exposure/ISO/aperture values | Match camera LCD | ☐ |
| 3.3 | Set one exposure step, read back | Value changed and sticks | ☐ |
| 3.4 | Single-shot trigger | Camera fires once | ☐ |
| 3.5 | Bulb start/stop | Exposure opens/closes | ☐ |

For any failing code: capture the raw request/response frames and compare
against firmware payload formats in PROTOCOL.md §3.4 before changing codes.

## 4. Regression gates

- [ ] After camera tests, tracking still works (531 round-trip)
- [ ] Disconnect/reconnect clean (no stale state)
- [ ] Demo mode still passes full simulator suite (`./gradlew :shared:jvmTest`)
