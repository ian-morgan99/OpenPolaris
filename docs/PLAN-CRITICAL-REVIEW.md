# Open Polaris - Critical Plan Review & First-Class Refinement

**Date:** 2026-08-29
**Reviewer:** Autopilot session `23f3b179`
**Inputs:** PLAN-TO-FULL.md, PLAN-2.0-REVISITED.md,
92 todos, recent commits `13d9cd0`-`e3ed6f9`, checkpoints 119/120/121.

This document is a *critical* review - not a status update. It identifies
**the things that will stop the plan from being first class**, and proposes
concrete refinements that are small enough to land alongside the next slice
of work.

---

## TL;DR - Top blockers

| # | Blocker | Severity | Fix in this doc |
|---|---------|----------|-----------------|
| 1 | Stream 2 marked DONE while new work keeps landing | High | §A |
| 2 | No definition of "first class" - only a feature list | High | §B |
| 3 | No CI / regression gate | High | §C |
| 4 | `ask_user` budget was understated (2.1 is *one* of several) | Medium | §D |
| 5 | iOS / desktop test surface undefined despite KMP claim | Medium | §E |
| 6 | Stream 1.1 settling condition not quantified | Medium | §F |
| 7 | VR backlog lives in a checkpoint, not in the plan | Low | §G |
| 8 | "Not connected" diagnostic trap (known, unowned) | Low | §H |
| 9 | MJPEG decode on GL thread (known, unowned) | Low | §I |
| 10 | No "pause and resume session" story for the controllers | Low | §J |

---

## §A - Stream 2 is not DONE, it is a moving target

**Problem.** PLAN-TO-FULL.md line 167 says
"### Stream 2 - plate-solving DONE". But:

- The 2.6 "Solve now" wiring is in_progress
  (`stream-2-6-solve-now`, `2-6`).
- `2-6-jvm-tests` and `2-5-goto-solve-hook` are pending.
- A real `OnDevicePlateSolver` was just added in `ee6eb1c` and a
  JVM bug in `AstroMath.toEquatorial` was fixed at `88-*` - work
  that was not in the original 2.1-2.7 outline.

The DONE marker misleads a future reader into thinking Stream 2 is a
sealed bucket when it is actually a live workstream.

**Refinement.** Strip the DONE marker and replace with a stream-status line
that links to the live todo list. Add a `Status` sub-section to
each stream block.

```diff
- ### Stream 2 - plate-solving DONE
+ ### Stream 2 - plate-solving (in flight)
+
+ **Live status:** see todos `2-6`, `stream-2-6-solve-now`,
+ `2-6-jvm-tests`, `2-5-goto-solve-hook`. Last commit on the
+ stream: `ee6eb1c` (OnDevicePlateSolver + solveAndRefine).
+ Stream body: pyramid matcher shipped, kdtree shipped, catalog shipped,
+ solveAndRefine shipped, Solve now button UI shipped, JVM tests open.
```

**Rule going forward:** if a stream has any non-done todos whose
`title` starts with `2-` or `stream-2-`, the stream is
**not** DONE.

---

## §B - "First class" is undefined

**Problem.** Definition-of-done lines 57-82 list seven functional
features but no non-functional bars. A reviewer cannot tell from
the plan whether a "first class" version of feature (4) is one
plate-solve per minute or one per second; whether VR mode should
hold 30 fps or 60 fps; whether the live preview latency should
be <500 ms or <2 s.

**Refinement.** Add a non-functional acceptance section to
PLAN-TO-FULL.md, before the DoD list.

```markdown
## Non-functional acceptance (first class bars)

A first-class Open Polaris app must meet all of the following
*in addition* to the feature list.

### Performance
- MJPEG preview latency, end-to-end (network to on-screen):
  p50 < 400 ms, p95 < 800 ms, on a Polaris-class Wi-Fi link.
- VR mode holds >= 50 fps on a 4-year-old mid-range phone, with
  no jank > 50 ms.
- Plate-solve on-device: p50 < 1 s, p95 < 2 s for a 5-star
  detection on a 640x480 frame.
- Goto accuracy after plate-solve refinement: < 1 arc-minute
  residual.

### Reliability
- Mount disconnect and recovery is automatic within 5 s of
  the link coming back; user is notified, not stranded.
- A protocol error never crashes the app; it surfaces in the
  status line and in `MountSession.lastError` for the
  controller to inspect.
- `MountSession.request()` is idempotent in the face of
  connection loss: a partial response does not leave the
  session in an undefined state.

### Testability
- Every controller has a JVM test that runs without hardware.
- `:shared:jvmTest` and `:composeApp:jvmTest` are green on
  every PR; the plan's `smoke` task is the gate.
- `cli-probe --fake` runs every controller end-to-end and
  exits 0 in < 30 s.
- `cli-probe --real` is runnable by the user manually and
  produces a clean PASS/FAIL report per command.

### UX
- No modal dialog blocks the live preview.
- All action buttons (jog, goto, level, solve) have a visible
  "in progress" state and a failure state.
- VR mode can be exited by a single tap from any orientation.
- Status line surfaces: connection state, last action, last
  result, last error.

### Accessibility
- All interactive controls are reachable via TalkBack.
- Color is not the only signal: error banner has an icon
  and a "dismiss" affordance.

### Platform coverage
- JVM target: full controller test coverage.
- Android target: smoke-tested in `cli-probe --fake` plus
  a manual on-device test plan.
- iOS / desktop targets: build succeeds; controllers compile;
  no runtime validation needed for v1 (out of scope).
```

---

## §C - There is no regression gate

**Problem.** The plan describes a `:cli-probe:test` smoke
(Stream 5) but never says *what gates a PR*. The "overnight
execution order" (lines 362-391) walks streams but does not say
how a reviewer knows the streams are still working tomorrow.

**Refinement.** Add a "Definition of mergeable" subsection.

```markdown
### Definition of mergeable

A commit is mergeable when:

1. `./gradlew :shared:jvmTest :composeApp:jvmTest` is green.
2. `./gradlew :cli-probe:test` is green (FakeMount end-to-end).
3. `./gradlew :tools:cli-probe:run --args="--fake"` exits 0
   (the 30-command scripted smoke).
4. If the change touches `androidApp/`, the user has
   confirmed `:androidApp:assembleDebug` on `beast`.
5. The commit message names the stream / todo it advances.

The agent (Copilot session) is responsible for (1) and (2)
on every commit. The user is responsible for (3) on a
representative subset of streams. (4) is required only
for changes that touch Android shims.
```

This is the missing CI bar. Without it, "first class" is
subjective.

---

## §D - `ask_user` budget is understated

**Problem.** The plan claims one `ask_user` at 2.1
(PLAN-TO-FULL.md L389, PLAN-2.0-REVISITED.md L391). In reality
the user has already been asked 3 or more times in the last 24 h:
VR scope (Cardboard vs Quest vs none), polkit install
incantation, real-mount readiness. There are at least 4 more
latent decision points the plan does not call out:

1. **Stream 4.4 close-issue gate** - the user must post a
   real-device screenshot before `vr-issue-1` can be closed.
2. **Stream 5.3 real-mount smoke execution** - the user must
   power on the mount and run `cli-probe --real` themselves;
   the agent cannot do this from the worktree on a host
   that cannot reach the mount.
3. **Meeus formula accuracy target** - "a few hundred lines"
   in PLAN-2.0-REVISITED.md L334 is misleading; arc-second
   accuracy is *more* than a few hundred lines of Meeus. The
   user should be asked to confirm the target accuracy.
4. **Pyramid matcher tolerance** - what counts as "matched"
   in 2.4 / 2.5? N>=4 stars + quad distortion? The user
   should confirm before a lot of matcher work is done.

**Refinement.** Update the "Blockers" / "What I will do next"
sections in both plan files to call out these four explicit
gates. Defer until the next user check-in.

---

## §E - iOS / desktop test surface is undefined

**Problem.** PLAN-TO-FULL.md L3-4 says
"an Android + iOS + desktop Compose Multiplatform app" but
Stream 5 only tests JVM and Android. There is no `:iosTest`
or `:desktopTest` task defined. The `iosX64Test` /
`iosSimulatorArm64Test` tasks that the Kotlin Multiplatform
plugin generates by default are not wired into the build.

**Refinement.** Add a Stream 6 for non-JVM targets:

```markdown
### Stream 6 - multiplatform test wiring

- **6.1** Wire the default `iosX64Test` / `iosSimulatorArm64Test`
  tasks into the `smoke` target. No new tests required - the
  existing `:shared:jvmTest` should pass when run under the
  iOS-hosted Kotlin/Native runtime.
- **6.2** Same for `desktopTest` (JVM, but the entry point
  for the desktop app).
- **6.3** Add a `smoke:kmp` task that runs `:shared:jvmTest`
  + `:shared:iosX64Test` + `:shared:desktopTest`.
```

Alternatively, downgrade the iOS / desktop claim in the
top-of-doc blurb to "Android-first, JVM-tested".

---

## §F - Stream 1.1 settling condition is not quantified

**Problem.** PLAN-TO-FULL.md L156-159:
> "Test: `FakeMount` runs a simulated level cycle and the
>  controller returns `Completed` within the budget."

"Budget" is not specified. "Settling" is not specified.
A first-class implementation needs:

- Settling predicate: `|roll - roll_mean| < eps_roll` AND
  `|pitch - pitch_mean| < eps_pitch` for N consecutive
  samples, where `eps_roll = eps_pitch = 0.01 deg` and
  `N = 10`.
- Timeout: 60 s default; user-overridable via
  `AutoLevelController.run(timeout: Duration = 60.s)`.
- The synthetic level cycle in `FakeMount` must walk through
  the settling phases: ramp to settled to done, with a
  configurable time to settle.

**Refinement.** Replace lines 156-159 with:

```markdown
- **1.1** Add `AutoLevelController` in
  `shared/commonMain/.../domain/AutoLevelController.kt` that
  takes a `MountSession` and exposes a single
  `suspend fun run(timeout: Duration = 60.seconds):
  AutoLevelResult` returning
  `Completed(rollDeg, pitchDeg) | Failed(reason) |
  TimedOut`. Internally it calls `AUTO_LEVEL_TRIGGER`, then
  subscribes to the existing position push (517) and waits
  for the AHRS settling condition:

      |roll - roll_mean| < 0.01 deg AND |pitch - pitch_mean| < 0.01 deg
      for 10 consecutive samples

  Tests:
  1. **Happy path.** `FakeMount` runs a simulated level cycle
     that takes 5 s to settle; the controller returns
     `Completed(roll=0.0, pitch=0.0)` within the budget.
  2. **Timeout.** `FakeMount` never settles; the controller
     returns `TimedOut` after 60 s (or a smaller test-only
     timeout of 2 s).
  3. **Failure injection.** `FakeMount` returns a parse error
     on the position push; the controller returns
     `Failed("parse error: ...").
  4. **Settling oscillation.** `FakeMount` alternates between
     settled and unsettled for 8 s; the controller waits out
     the oscillation and returns `Completed`.
```

This makes "first class" measurable.

> **Spec error (flagged at implementation time, 2026-05).** The
> `subscribes to the existing position push (517)` line above is **wrong** per
> [`Codes.kt`](../shared/src/commonMain/kotlin/dev/openpolaris/core/protocol/Codes.kt):
> `517 = GET_GIMBAL_POS` (RA/Dec), not tilt. The auto-level controller must consume the
> tilt-state push, which is `SET_TILT_STATE = 538`. The implementation reads 538; this
> paragraph should be corrected when §F is next touched.

---

## §G - VR backlog lives in checkpoint 119, not in the plan

**Problem.** PLAN-TO-FULL.md Stream 4 (lines 310-338) covers the
*baseline* VR (lens fix, cross-check, overlay, close-issue). The
"amazing VR view" backlog (checkpoint 119) is a 10-item list that
includes items the plan doesn't mention:

- Brightness / contrast overlay
- Degrees scale (alt ring around the crosshair)
- 3D scene-graph representation
- Cardboard v2 SDK migration
- Stereo passthrough

**Refinement.** Promote the VR backlog into PLAN-TO-FULL.md
as a Stream 7, and link the checkpoint from the plan. Slice
1 (crosshair + HUD + conn banner) is already landed at
`13d9cd0`.

```markdown
### Stream 7 - "amazing VR view" upgrade (post-baseline)

Picks up from checkpoint 119. Items 1, 3, 5 landed in `13d9cd0`.

- [x] 7.1 Crosshair overlay (item 1)
- [x] 7.2 Status HUD (item 3)
- [x] 7.3 Connection-loss banner (item 5)
- [ ] 7.4 Plate-solve target marker (item 2)
- [ ] 7.5 In-VR recenter affordance (item 4)
- [ ] 7.6 Brightness / contrast overlay (item 6)
- [ ] 7.7 Degrees scale around the crosshair (item 7)
- [ ] 7.8 3D scene-graph (item 8) - defer until 3D objects
      exist (gotos can be 3D)
- [ ] 7.9 Cardboard v2 migration (item 9) - defer until
      the user has a v2 viewer
- [ ] 7.10 Stereo passthrough (item 10) - defer until the
      phone supports it
```

This keeps the plan and the user's active focus in lockstep.

---

## §H - `MountSession.request()` "not connected" trap is unowned

**Problem.** A known footgun (captured in checkpoint 118):
`MountSession(connectionFactory, host, port)` does not
connect. `request()` returns
`CmdResult.ProtocolError("not connected")` if `connect()` is
not called first. This surfaces up as a misleading
"Plate-solve failed (no confident match)" to the user.

**Refinement.** Add a small ticket:

```markdown
### Stream 8 - diagnostics & error surfaces

- **8.1** `MountSession.lastError: CmdResult?` field, set
  whenever a request returns `ProtocolError`. This lets the
  plate-solver distinguish "the solver didn't find a match"
  from "the mount isn't connected" - currently conflated.
- **8.2** `AppViewModel.connect()` should clear `lastError`
  on a successful `tryConnect()`.
- **8.3** A JVM test for the lifecycle: connect, request,
  request, then forcibly drop the socket; the next request
  returns `Failed("not connected")` AND `lastError` is set;
  a fresh `connect()` clears it.
```

This is a 30-line change with one JVM test; landed in a
single commit.

---

## §I - MJPEG decode on GL thread is unowned

**Problem.** `VRActivity.kt` calls
`BitmapFactory.decodeByteArray` on the GL thread per frame.
On a mid-range phone this is jank-prone. The plan's DoD line
77 says "the preview keeps streaming while the user moves
their head", which implies >= 30 fps under head motion.

**Refinement.** Add a ticket to Stream 7 or Stream 8:

```markdown
- **7.11** Move JPEG decode off the GL thread. Decode on the
  `PreviewController` IO dispatcher, post the
  `ImageBitmap` (or the raw RGBA bytes) to the GL thread.
  Measure: the onDrawFrame callback should take < 8 ms on
  a Pixel 4a or equivalent.
```

Defer until the 3D scene-graph item (7.8) makes decode
latency user-visible.

---

## §J - Pause/resume story is missing

**Problem.** The controllers are not designed to be paused
and resumed. When the user navigates away from the main
screen, the connection is dropped via `disconnect()` but the
position-stream subscription, helpers state, and preview
session are not formally torn down. There is no
"re-establish the same session" story.

**Refinement.** Add a Stream 9:

```markdown
### Stream 9 - session lifecycle hardening

- **9.1** A `Session.shutdown()` that calls
  `preview.shutdown()`, `helpers.stop()`,
  `autoLevel.cancel()`, `mount.disconnect()`. Symmetric
  with `Session.connect()`.
- **9.2** `AppViewModel.disconnect()` calls `shutdown()`
  (currently it only calls `preview.stop()`).
- **9.3** On `onResume()` of the main activity, if a
  previous session was active, re-connect with the same
  host/port and re-attach the controllers.
- **9.4** JVM test: connect, shutdown, connect, shutdown,
  assert no leaked coroutines (use a `TestCoroutineScheduler`
  and assert all child jobs are complete).
```

---

## Refined "Definition of done"

Replacing PLAN-TO-FULL.md L57-82 with:

```markdown
## Definition of done (first class)

A first-class Open Polaris app meets all of:

### Functional
1. The phone connects to the mount's Wi-Fi, opens both TCP
   channels, and shows the live preview.
2. Jog, goto, tracking rate, position read all work in the
   Goto pane.
3. `Level now` runs the auto-level cycle and reports the
   outcome (Completed / Failed / TimedOut).
4. After a goto arrives, a plate-solve refinement loop
   captures a frame, runs the solver, and nudges the mount.
5. VR mode opens a Cardboard-style stereo view with the lens
   correction applied to the texture, the crosshair, the
   status HUD, and the connection-loss banner (Stream 7
   items 1, 3, 5).
6. Every controller is JVM-testable via `FakeMount`.
7. A `smoke` task proves (1)-(6) on JVM in one command.

### Non-functional
8. The non-functional acceptance bars in §B above are met.
9. The mergeability gates in §C above are met on every PR.
10. The session lifecycle (§J) is sound; no leaked
    coroutines on connect / disconnect / pause / resume.
```

---

## Action plan - what this refactor enables

This is not a new workstream; it is a *backlog reshuffle*.
The next 3 slices, in order, are now well-defined:

1. **Stream 7.4 - plate-solve target marker** (small, additive,
   uses the solver we already have, no new dependencies).
2. **Stream 8.1-8.3 - `MountSession.lastError`** (30 LoC,
   unblocks a clearer "Plate-solve failed (no mount)" error).
3. **Stream 7.5 - in-VR recenter affordance** (small, restores
   the "tap to exit" pattern to a confirmable user action).

Each slice is < 200 LoC of code, lands in a single commit,
keeps `:shared:jvmTest :composeApp:jvmTest` green, and is
ready for the user's reviewing agent within an hour.

After these three slices, the next big-ticket work is Stream
5.3 real-mount smoke, which is blocked on the user powering
on the mount and running `cli-probe --real` themselves.

---

## Sign-off

This review surfaces 10 distinct first-class blockers. None
of them require a rewrite; all of them are tractable
incremental changes. The plan is *almost* first class. The
remaining work is to align the plan text with the actual
state (Stream 2 is not DONE), add the missing non-functional
bars (§B), and own the known traps (§H, §I, §J).

Filed as a document. Will be referenced in the next
checkpoint (123) and the Stream 7 work that follows.
