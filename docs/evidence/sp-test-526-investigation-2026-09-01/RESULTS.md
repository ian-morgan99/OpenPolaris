# 526 (`SP_TEST`) investigation — 2026-09-01

Goal: determine whether code 526 is a "test / debug" command, a timelapse state
machine step, or a no-op firmware stub. We have only a soft payload shape
(`step:N;`); the firmware decompile gave us a symbol (`SP_TEST`) but no
documentation. Step values surveyed: 1, 2, 3, 6.

## Test rig

- `PushListener` (long-lived passive listener) at `192.168.0.1:9090` — the gimbal AP
- One request per run: `1&526&2&step:N;#` (header `&1&` ack-mode, code 526, subtype 2)
- Each run had a 10–20 s passive window before and after the send
- Step values cycled: 1 (×3), 2 (×2), 3 (×1), 6 (×2)

## Results

| Run | step | duration | response | unsolicited pushes |
|-----|------|----------|----------|---------------------|
| run1–run6 (prior commit) | 1, 1, 1, 1, 3, 1 | 10–20s | none | 525 (Tempa) only |
| run7 | 2 | 10s | none | 525 (Tempa) only |
| run8 | 2 (retry) | 10s | none | 525 (Tempa) only |
| **run9** | **6** | **20s** | **`526@step:6;ret:0;` (21 ms)** | **286 camera-attach (Pentax K-1 II, state:1) + 282 storage-format(0)** |
| run10 | 6 (retry) | 20s | none (gimbal offline) | — |

## Conclusions

1. **Steps 1, 2, 3 return nothing** (or `ret:-1` which the listener discards as
   not a 526 response). The gimbal is either silently dropping them, or it
   returns a `ret:-1` that the PushListener did not capture because the listening
   window ended before the reply could arrive. Either way, no `ret:0` for steps
   1–3 in any of seven runs.

2. **Step 6 returned `ret:0`** on the only run where the gimbal appeared online
   with a fresh USB camera enumeration (Pentax K-1 II attached). This is the
   **first successful 526 reply observed**. The 286/282 pushes arrived
   *before* the 526 was sent, so they were not caused by 526 — but the camera
   reattach is correlated with the ret:0 success.

3. **Caveat: causality is unclear.** ret:0 may indicate that step:6 is a real
   "test" command that requires a camera to be attached. Or it may be that
   `step:6` happens to be a benign value the gimbal always acknowledges when
   ready. To distinguish:
   - Repeat step:6 *without* a camera attached (need to detach the camera)
   - Send step:6 immediately after a fresh boot (no camera)
   - Try other step values (4, 5, 7, 8, 9, 10) with camera attached

4. **Gimbal went offline again** between run9 and run10 (9090/22 closed, port
   80 still open at 192.168.0.1 = LAN router per the NETWORK-FINGERPRINT rule).
   Same pattern as the "all the lights went off" event earlier: the gimbal
   becomes unreachable after a brief window, often correlated with camera
   attach/detach events.

5. **Possible firmware interpretation.** PROTOCOL.md §3.4 lists timelapse step
   payloads `step:6;state:%d;iso:%d,%d;` and `step:7;state:%d;num:%d;remainNum:%d;`
   as ground-truth shapes observed *somewhere* in the 258–311 camera-control
   range. If 526 is actually the timelapse state machine (and the symbol
   `SP_TEST` is a misread of something like `SP_TIMELAPSE_TEST` or
   `SP_CABLE_RELEASE_TEST`), then:
   - `step:6` = "trigger photo, update ISO range" (a real, hardware-dependent action)
   - `step:1`–`step:5` = setup steps that fail without a camera and produce
     `ret:-1` (unsupported on current camera state)

   This would explain the asymmetry: step 6 succeeded *because* the camera was
   attached. It would also explain the priority for live verification: any
   526-step-N test must be run with the camera attached, in the same way the
   Benro app does it.

## Open questions for the next session

- [ ] Re-test step:6 with camera attached; confirm ret:0 is reproducible
- [ ] Re-test step:6 with camera detached; confirm ret:-1 or silence
- [ ] Survey steps 4, 5, 7, 8, 9, 10 with camera attached
- [ ] Compare 286 push (state:0/1/2) timings vs successful 526 ret:0
- [ ] Map 526 to the timelapse state machine in `polestar_app` decompile

## Files in this directory

- `pure-passive-10s.log` / `pure-passive-2.log` — control: no 526 sent
- `push-with-send-step1.log` / `run1`-`run6` — initial 1/1/1/1/3/1 survey
- `run7-step2.log` — step:2 first try
- `run8-step2-retry.log` — step:2 retry
- `run9-step6-reboot.log` — **the ret:0 finding**
- `run10-step6-retry.log` — control after gimbal went offline (0 frames)
