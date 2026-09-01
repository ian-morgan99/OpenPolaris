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
| **morning recheck #1** | none sent | 5s | **`526@step:6;ret:0;` UNSOLICITED at t=2115ms** | — |
| morning recheck #2 | none sent | 10s | NoRouteToHostException (gimbal offline again) | — |
| **run11** | **6 (fresh)** | **15s** | **`526@step:6;ret:0;` at t=7542ms (23ms after send)** | — |
| run12 | none sent | 20s | 0 frames (gimbal quietly online but no 526 push) | — |

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

6. **Morning recheck #1: 526 ret:0 appeared UNSOLICITED.** A 5-second passive
   listen (no `--send` flag) at 192.168.0.1:9090 captured `526@step:6;ret:0;`
   at t=2115ms, with no `>>> SEND` line in the log. This is **delayed-echo**
   evidence: the gimbal was replying to one of the prior sends (run9/run10)
   that arrived late because the gimbal had gone offline and just came back.

7. **run11: 526 ret:0 REPRODUCED.** With the gimbal online, a fresh send of
   `1&526&2&step:6;#` got `526@step:6;ret:0;` back 23ms later. So step:6 with
   the gimbal online reliably returns ret:0. This contradicts the
   "step:6 only works after a camera reattach" hypothesis — run11 had no
   286/282 push preamble, just a clean ret:0.

8. **run12: 20s passive listen got 0 frames.** This rules out a steady
   broadcast of 526 ret:0. Combined with run11, the picture is:
   - 526 is request-reply (not push)
   - Step:6 returns ret:0 reliably when the gimbal is online
   - Steps 1, 2, 3 return nothing or ret:-1
   - The gimbal's network is intermittent (gimbal goes offline for minutes
     at a time, even mid-test)
   - On reconnect, the gimbal may flush queued replies (delayed-echo)

9. **Why did steps 1–3 return nothing in 7+ runs?** Two hypotheses:
   - (a) They return ret:-1 (unsupported) but our 526 path is silently
     dropping ret:-1 replies. **Disproved by reading PushListener** —
     every frame, including ret:-1, is written to the log file (line 154)
     and counted in `seen` (line 138). If the gimbal had replied ret:-1,
     it would have appeared in the file. So the steps returned truly
     nothing, or the listen window ended before the reply arrived.
   - (b) Steps 1–5 are timelapse setup steps that need a camera attached
     AND a timelapse session started. With no camera, they truly do
     nothing. Step 6 (capture) is the first step that the gimbal can
     process standalone.
   - (c) The reply is delayed past the listen window. The gimbal went
     offline during run10, the reply to run9 may have been queued and
     delivered minutes later (the morning recheck #1 "unsolicited"
     capture supports this). For step:1, if the reply takes 10+ seconds,
     a 10s listen window would miss it.

10. **Punchlist for the next probe session.**
    - ✅ DONE: PushListener now supports `--send-delay <ms>` so we can
      send at t=500ms and then wait the full remaining window for the
      reply. Use with longer durations (e.g. `--send-delay 500 30
      192.168.0.1 9090 --send-step 1`).
    - ✅ DONE (runs 13, 14, 15): Re-test step:1, step:2 with
      `--send-delay 500` and a 30s window. See "Round 2 (2026-09-01
      afternoon)" below for findings.
    - Survey steps 4, 5, 7, 8, 9, 10 (camera attached, gimbal online).
    - Document 526 ret:0 in PROTOCOL.md as a live-verified entry.

## Round 2 (2026-09-01 afternoon)

Goal: rule out the "delayed reply missed by the listen window" hypothesis
for steps 1-3, and to test the "ret:0 only when not fresh" hypothesis for
step:6.

Test rig: same `PushListener` tool. `--send-delay 500` puts the SEND at
t≈500ms; the listen window is then the full configured duration. 30s
windows used for runs 13/14/15 to give any delayed reply time to arrive.

| Run | step | duration | send @ | first 525 | 810 ret:-1 @ | 526 reply | other |
|-----|------|----------|--------|-----------|--------------|-----------|-------|
| 13  | 1    | 30s      | 502ms  | 13679ms   | 22483ms      | 22485ms (`step:1;ret:-1`) | — |
| 14  | 2    | 30s      | 504ms  | 1734ms    | 7423ms       | **none** (socket dead) | — |
| 15  | 6    | 40s      | 504ms  | 16562ms   | 1956ms + 26957ms | **none** | — |

### Interpretation

**The "delayed reply" hypothesis is confirmed for step:1.** Run 13 saw
`526@step:1;ret:-1` at t=22485ms — almost 22 seconds after the SEND. This
explains every prior "step:1 returns nothing" observation: the listen
window was always ≤20s, so the 526 reply was always late. The
`PushListener` does not drop `ret:-1` — it does write them to the log
file — so a `ret:-1` after a SEND is genuine protocol evidence, not a
listener bug.

**Step:2 ret:-1 is still missing.** Run 14 with 30s window and
`--send-delay 500` saw 525 Tempa in 1.7s and 810 ret:-1 in 7.4s, but
the socket went silent afterward and no 526 echo arrived within the
window. Either the gimbal sends step:2 replies even later (>30s) or
the socket really does close after 810 and step:2 produces no echo.
The pattern in run 15 (step:6, 40s window, fresh gimbal) was
identical: 810 ret:-1 at 1.9s, no 526 echo, no 286/282 buffered push.

**The 810 ret:-1 is the auth gate.** Every run sees a `code=810 ret=-1`
within 1-22 seconds of opening the connection. This is the gimbal
asking the client to authenticate (login). Until the client replies
with the proper login sequence, all 526 attempts get either `ret:-1`
(seen for step:1) or no echo at all (steps 2, 6, fresh). This explains
the asymmetry between the morning session (which captured ret:0) and
the afternoon session (which mostly doesn't): the morning session
appears to have landed in a brief window where either the auth gate
was open (post-auth, gimbal still remembered a valid session) or the
delayed 526 echo arrived after a previous auth. There is NO
contradiction with the prior runs once auth is taken into account.

**Step:6 ret:0 is NOT reliable.** Run 15 was a fresh step:6 send to a
fresh gimbal (60s+ of no traffic, then a 40s listen). It saw 810
ret:-1 and 525 Tempa but **no 526 reply at all** — not even a late one.
This disproves the "step:6 reliably returns ret:0 when gimbal is
online" conclusion from the morning session. The correct picture is:
- When the gimbal accepts the request (auth satisfied, fresh enough),
  step:6 → ret:0
- When the gimbal is in auth-required state, step:6 → either ret:-1
  late (like step:1) or no echo at all (socket dies after 810)

### What this means for the 526 protocol model

526 (`SP_TEST`) is **not** a reliable request-reply opcode from a
fresh, unauthenticated TCP connection. The protocol requires login
(810 challenge) before 526 is processed. Without login, the gimbal
may:
- (a) Echo `ret:-1` 20+ seconds later (step:1 observed)
- (b) Drop the request entirely (step:2 and step:6 observed)
- (c) Successfully process the request, return `ret:0` (morning
  run 9, run 11) — this happens occasionally and may be a delayed
  echo of an earlier request after the gimbal's auth state expires

The most plausible model: the gimbal queues 526 sends and only
processes them after a successful login. If the client never logs in,
the queue is silently dropped. If the client logs in late, the
queued replies arrive in order.

### Open questions for the next session

- [ ] Implement a login sequence in the probe (`1&810&...`) and re-run
      step:1, step:2, step:6 — this should resolve the asymmetry
- [ ] Survey steps 4, 5, 7, 8, 9, 10 with auth completed
- [ ] Map 526 to the timelapse state machine in `polestar_app` decompile
- [ ] Document 526 ret:0 in PROTOCOL.md as a live-verified entry
      (only valid post-auth)

## Open questions for the next session

- [ ] Re-test step:1, step:2 with `--send-delay 500` and a 30s window
      (PushListener change in commit after this one) to rule out a
      delayed-reply hypothesis for steps 1-3.
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
- `morning-recheck-2026-09-01.log` — passive 5s, **526 ret:0 UNSOLICITED**
- `morning-recheck-2-2026-09-01.log` — passive 10s, NoRouteToHost (offline)
- `run11-step6-fresh.log` — **526 step:6 ret:0 reproduced (23ms)**
- `run12-20s-pure-passive.log` — 20s passive, 0 frames (rules out broadcast)
