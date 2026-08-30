# Open Polaris — Project Plan

Faithful replica of Benro Connect, v1. Effort assumes one experienced developer, part-time
(evenings/weekends), with AI-assisted coding for mechanical work. **The schedule driver is hardware
test cadence (clear weekend nights), not code volume** — milestones are therefore gated on hardware
validation, not lines written.

## Phase 0 — Foundations (week 1, no hardware needed)

| Task | Est. | Output |
|---|---|---|
| Scaffold KMP project: core/protocol, core/domain, core/astro, ui, tools/cli-probe | 0.5 d | Building skeleton |
| Protocol layer: framing, CommandBuilder for all commands in PROTOCOL.md §3, ResponseParser | 2 d | Golden-frame unit tests green |
| cli-probe v0: connect, send 284, print parsed response | 0.5 d | Scriptable smoke test |

**Gate G0:** protocol tests pass; probe compiles. (iOS target compilation is verifiable only on a macOS/Xcode host; Linux CI validates Android+JVM targets.)

## Phase 1 — First light (weeks 2–3) ⛺ first hardware session

| Task | Est. |
|---|---|
| Android socket impl + MountSession (connect/reconnect/handshake per ARCHITECTURE §3.1) | 2 d |
| Status screen: battery/mode/state from 284; connection UI | 2 d |
| Jog rocker + position readout (513–517, 535) | 3 d |
| MJPEG preview pane | 2 d |

**Gate G1 (hardware):** connect from phone, see status, jog the mount, see live preview.
Side-by-side identical to stock app behavior.

## Phase 2 — Astro core (weeks 4–6)

| Task | Est. |
|---|---|
| core/astro: sidereal time, alt/az conversions, sun/moon ephemeris, catalog loader (+tests) | 3 d |
| AHRS gating + quaternion pointing display (520/518) | 2 d |
| Star alignment wizard (catalog pick → 530 flow, multi-star `num`) | 4 d |
| Goto (519) with cancel; tracking start/stop/rates/half-speed (531/536 incl. inversion) | 3 d |
| Astro helper dialog: tilt/dither/limits/settling/auto-level (537–549) | 2 d |

**Gate G2 (hardware):** full astro night — align 2 stars, goto target, track sidereal,
toggle half-speed, enable dither. This is the project's core milestone.

## Phase 3 — Camera & modes parity (weeks 7–10)

| Task | Est. |
|---|---|
| Camera params panel: get/set with range validation; lock during sequences (258–311) | 4 d |
| Timelapse mode (params, start/pause/resume, progress) | 3 d |
| Sun mode (rise/set card, goto/track) | 2 d |
| Dynamic lapse keyframes (517 reads + 535 sets) | 3 d |
| Panorama capture grid (no stitching) | 2 d |
| Precompile/appointment scheduling | 2 d |
| In-app log screen + export | 1 d |

**Gate G3 (hardware):** complete night's session end-to-end using only this client
(SPEC.md §7 acceptance criteria). Side-by-side comparison vs stock app for each flow.

## Phase 4 — Hardening & release (weeks 11–12)

| Task | Est. |
|---|---|
| Field-bug fixes from G3 sessions | 1 wk |
| Parser fuzzing pass; reconnect-under-load tests | 2 d |
| README, screenshots, tagged v1.0 release | 1 d |

## Milestone summary

```
G0 protocol proven ──► G1 first light ──► G2 astro night ──► G3 full parity ──► v1.0
   week 1               weeks 2–3           weeks 4–6           weeks 7–10        weeks 11–12
```

~12 weeks part-time as scoped; compressible toward ~6–8 weekends of focused sprints plus clear-sky
test nights if phases 1–2 are AI-accelerated.

## Non-functional acceptance (first-class bars)

A first-class Open Polaris app must meet all of the following *in addition* to the feature list.
These bars are testable without hardware unless noted; the agent runs the non-hardware ones on every
slice, and the user runs the hardware ones at each gate.

### Performance
- MJPEG preview latency, end-to-end (network to on-screen): p50 < 400 ms, p95 < 800 ms on a
  Polaris-class Wi-Fi link.
- VR mode holds ≥ 50 fps on a 4-year-old mid-range phone, with no jank > 50 ms.
- Plate-solve on-device: p50 < 1 s, p95 < 2 s for a 5-star detection on a 640×480 frame.
- Goto accuracy after plate-solve refinement: < 1 arc-minute residual.

### Reliability
- Mount disconnect and recovery is automatic within 5 s of the link coming back; the user is
  notified, not stranded.
- A protocol error never crashes the app; it surfaces in the status line and in
  `MountSession.lastError` for the controller to inspect.
- `MountSession.request()` is idempotent in the face of connection loss: a partial response does
  not leave the session in an undefined state.

### Testability
- Every controller has a JVM test that runs without hardware.
- `./gradlew :shared:jvmTest :composeApp:jvmTest` is green on every PR; the `smoke` task is the
  gate.
- `cli-probe` smoke tests (`:tools:cli-probe:test`) run every controller end-to-end through a
  real socket against `FakeMount` on an ephemeral port and exit 0 in < 30 s.
- `cli-probe --real` is runnable by the user manually and produces a clean PASS/FAIL report per
  command.

### Repository mirror honesty
- The GitHub issue mirror is the source of truth for "what is shipped vs. open". The session
  todo SQL mirror must be reconciled to it at the end of every slice (open/closed status,
  numbering, and title wording). A closed issue that is not actually closed on GitHub, or a
  todo that the plan claims is done but GitHub shows as open, is a first-class failure of this
  bar — caught by `gh issue list --state all` cross-checked against the SQL mirror before a
  slice is marked done.
- The "Definition of mergeable" includes a sixth condition: every issue that the commit
  claims to close is in fact closed on GitHub at commit time. (Prior session violated this
  by posting a "shipped" comment on #4 but never issuing the close; caught during this
  slice's refresh.)

### UX
- No modal dialog blocks the live preview.
- All action buttons (jog, goto, level, solve) have a visible "in progress" state and a failure
  state.
- VR mode can be exited by a single tap from any orientation.
- Status line surfaces: connection state, last action, last result, last error.

### Accessibility
- All interactive controls are reachable via TalkBack.
- Color is not the only signal: the error banner has an icon and a "dismiss" affordance.

### Platform coverage
- JVM target: full controller test coverage.
- Android target: smoke-tested via `cli-probe` JVM tests (which use a real socket against
  `FakeMount`) plus a manual on-device test plan.
- iOS / desktop targets: build succeeds; controllers compile; no runtime validation needed for
  v1 (out of scope).

## Definition of mergeable

A commit is mergeable when **all six** of these hold:

1. `./gradlew :shared:jvmTest :composeApp:jvmTest` is green.
2. `./gradlew :tools:cli-probe:test` is green (FakeMount end-to-end over a real socket — the
   `ProbeSmokeTest` harness starts `FakeMount` on an ephemeral port and drives every controller
   through the production `MountSession` API).
3. `./gradlew :tools:cli-probe:run` runs cleanly in every supported mode
   (`status|send|burst|listen|preview-smoke`); no syntactic mode rename / flag drift.
4. If the change touches `androidApp/`, the user has confirmed `:androidApp:assembleDebug` on
   `beast`.
5. The commit message names the stream / todo it advances.
6. Every issue that the commit claims to close is in fact closed on GitHub at commit time
   (no "shipped" comments without an actual `gh issue close`).

The agent (Copilot session) is responsible for (1), (2), (3), and (6) on every commit. The user
is responsible for (4) on changes that touch Android shims. (5) is a style rule.

## Definition of unblockable

Distinct from "mergeable": a slice is **unblockable** only when the *user-gated* prerequisites
from [PLAN-CRITICAL-REVIEW.md §D](./PLAN-CRITICAL-REVIEW.md#d-ask_user-budget-is-understated) are
cleared. Without these, a mergeable commit can still leave the plan stalled:

1. **Stream 4.4 close-issue gate** — the user posts a real-device VR-mode screenshot before
   `vr-issue-1` can be closed. *Status: closed (`vr-issue-1` in todo list).*
2. **Stream 5.3 real-mount smoke execution** — the user must power on the mount and run
   `cli-probe --real`; the agent cannot reach 192.168.0.1 from the worktree. *Status: blocked on
   user.*
3. **Meeus formula accuracy target** — the user has confirmed the target accuracy (arc-minute
   vs. arc-second) for sidereal / alt-az conversions. *Status: assumed arc-minute; revisit at
   G2.*
4. **Pyramid matcher tolerance** — the user has confirmed what counts as "matched" in
   2.4 / 2.5 (N≥4 stars, quad distortion budget). *Status: assumed N≥4, 0.05 quad distortion;
   revisit if G2 plate-solve flakes.*

A blocked unblockable does not block merge — it blocks the gate.

5. **User-gated timeout (per PLAN-CRITICAL-REVIEW §M).** If a user-gated item has been
   "blocked on user" for more than 14 days without the user confirming they will action it,
   the agent downgrades it from "blocked" to "deferred" in the todo (and any related GitHub
   issue comment) and proceeds. The agent cites the date in the todo description so the
   timeline is auditable. This is the *only* rule that allows a slice to proceed past a
   user-gated item.

## Definition of next-slice-ready

The agent self-picks the next slice when **all** of the following hold, and pauses for a reviewing
agent otherwise:

1. **The previous slice shipped clean.** All 6 merge conditions green; the issue it claimed to close
   is in fact closed on GitHub at the time of commit (mirror-honesty bar).
   1.b. **No regression found in shipped work.** If the agent discovers a regression in shipped
        code while preparing the next slice, it pauses the new slice and files a regression
        issue (label `priority/p0` or `priority/p1`) before proceeding. The agent can fix the
        regression in a small commit (≤50 LoC) and resume, or hand it back to the user.
2. **The current top-of-list issue is action-ready.** Its acceptance criteria are unambiguous from
   the issue body and a code site exists (or is named). No "design first" steps needed before
   the first commit.
3. **No new high-priority issue has been posted by the reviewing agent.** If the reviewing agent
   has filed an issue with label `priority/p0` or `priority/p1`, that preempts the queue regardless
   of position. (Labels were added at commit time per PLAN-CRITICAL-REVIEW §O.)
4. **The slice is bounded.** A single issue's acceptance is one PR-sized commit. A slice that would
   span two or more issues must be split. If an issue contains N>1 sub-tasks (e.g. issue #3 with
   9.1-9.4), the agent must re-slice it into N issues first.

**If any condition fails, the agent pauses** and writes a one-paragraph note explaining which
condition failed and what the next step is (refine the spec, ask the user, wait for the review
agent, etc.). The plan is the source of truth for this rule.

**Source of truth for the slice order.** The "Immediate next actions"
items 5-12 are a *cached* queue for this worktree. The authoritative
order is the live issue list, sorted by label and creation date:
[p1 queue](https://github.com/ian-morgan99/OpenPolaris/issues?q=is%3Aissue+is%3Aopen+label%3Apriority%2Fp1+sort%3Acreated-asc)
and [p2 queue](https://github.com/ian-morgan99/OpenPolaris/issues?q=is%3Aissue+is%3Aopen+label%3Apriority%2Fp2+sort%3Acreated-asc).
If a reviewing agent files a new `priority/p0` or re-prioritises an
existing issue, that preempts the cached order per condition 3 above.

## Current stream priorities

Open issues, in priority order. Tracked in the session todo mirror and on
[GitHub issues](https://github.com/ian-morgan99/OpenPolaris/issues):

- **#17 — polkit rule typo on the Wi-Fi scan policy** ([link](https://github.com/ian-morgan99/OpenPolaris/issues/17)).
  The deployed `/etc/polkit-1/rules.d/99-openpolaris-wifi-scan.rules` references
  `unix-group:admin`, which does not exist on Ubuntu (Ubuntu uses `sudo`). Each
  `nmcli device wifi rescan` prompts the user. *Blocked on user* to run
  `/tmp/openpolaris-fix-polkit-typo.sh` and confirm. A self-contained one-shot fix
  is staged in the session workspace.

### 3a/3b/3c queue (Stream 3 — session lifecycle, filed 2026-08-30T17:18Z)

| # | Priority | Issue | Title | Depends on |
|---|----------|-------|-------|-----------|
| 3a.1 | p1 | [#20](https://github.com/ian-morgan99/OpenPolaris/issues/20) | `Session.shutdown` no-leak JVM test | — |
| 3a.2 | p1 | [#21](https://github.com/ian-morgan99/OpenPolaris/issues/21) | `_tilt.value` survives `Session.stop()`/`start()` | 3a.1 |
| 3b.1 | p1 | [#22](https://github.com/ian-morgan99/OpenPolaris/issues/22) | `runAndAwait` cancellation returns `Failed("cancelled")` within 1s | 3a.2 |
| 3b.2 | p1 | [#23](https://github.com/ian-morgan99/OpenPolaris/issues/23) | No leftover coroutines after `runAndAwait` cancellation | 3b.1 |
| 3c.1 | p2 | [#24](https://github.com/ian-morgan99/OpenPolaris/issues/24) | `SessionMarker` data class with `kotlinx.serialization` JSON | — |
| 3c.2 | p2 | [#25](https://github.com/ian-morgan99/OpenPolaris/issues/25) | `SessionStore` interface (save, loadAll, delete, latest) | 3c.1 |
| 3c.3 | p2 | [#26](https://github.com/ian-morgan99/OpenPolaris/issues/26) | `FileSessionStore` in `androidApp/src/androidMain` | 3c.2 |
| 3c.4 | p2 | [#27](https://github.com/ian-morgan99/OpenPolaris/issues/27) | Auto-reconnect prompt on Wi-Fi loss (uses `SessionStore.latest()`) | 3c.3 |

p1 sub-issues ship in queue order before any p2 work begins (see
"Immediate next actions" items 5-12 and the
[live p1 queue](https://github.com/ian-morgan99/OpenPolaris/issues?q=is%3Aissue+is%3Aopen+label%3Apriority%2Fp1+sort%3Acreated-asc)).
p2 sub-issues are deferred until the p1 queue is empty or until a
reviewing agent assigns a new owner.

**Closed (recent, in this worktree's history):**
- **#18 — Stream 7.5: in-VR recenter affordance (volume key + offset math)**
  (shipped `56831b1`, closed 2026-08-30T17:18Z). 12 new `RecenterMathTest`
  cases; raw-vs-effective pose split; `VRActivity.onKeyDown` consumes the
  volume-key event; toast debounced via 0.5° epsilon. 220/220 `:shared:jvmTest`
  PASSED.
- **#15 — VR marker reads live camera FoV from connected mount sensor**
  (shipped `f948ced`). #16 — barrel-distortion shader routing
  (shipped `ff81c2c`). #14 — `StateFlow` update on `lastSolveResult` change.
  #13 — `confidence` thread-through on `GoToController.solveAndRefine`. #12 —
  `SolveResult.timestampMs` for honest `ageMs`. #11 — Stream 7.4 plate-solve
  target marker in VR (all shipped, all closed).
- **#10 / #9 / #8 — Stream 7.3 g/h/i**: coroutine startup race in
  `MountSession.tryConnect`; hard-coded preview ports + Connecting-state check;
  `SimulatedMount` reader leak + save-failure status preservation. All closed.
- **#7 — re-slice #3 into 3a/3b/3c** (planning, no code). Closed 2026-08-30T15:51:06Z
  without the sub-issues being filed — see #19 sub-point (c) for the
  reconciliation plan.
- **#6 — `MountSession` background reader for `AutoLevelController.runAndAwait`**
  (closed 2026-08-30T15:50:54Z). **Caveat per #19**: the work landed on a
  different branch (`d693197` / `55c83f9` / `73474ed`); on this worktree's
  branch, the bug the user reopened the issue for
  (SUSPEND+tryEmit contradiction, 2026-08-30T10:17:50Z) is still present.
  The next slice is the fix on this branch, not new code in #6.
- **#5 — AutoLevel settling condition quantified** (shipped `4cb24bf` /
  `4683ebf`, 12/12 JVM tests). `AutoLevelController.run(timeout: Duration = 60.seconds)`
  returns `AutoLevelResult = Completed(rollDeg, pitchDeg) | Failed(reason) | TimedOut`.
  Settling predicate is **10 consecutive samples within ±0.01° of the running
  mean of (roll, pitch) on the tilt-state push (538)**, not the RA/Dec position
  push (517) — see [Spec error §F](#spec-error-§f-517538) below.
- **#3 — session pause/resume hardening** (shipped). Re-sliced into 3a/3b/3c
  per #7; sub-issues to be filed as part of the #19 reconciliation.
- **#2 — non-functional acceptance bars** (shipped `4683ebf`): folds §B + §C + §D
  into the live plan.
- **#4 — `MountSession.lastError` ownership** (shipped `bcfc6e6` / `ea53bf0`,
  closed during the #2 refresh).

### Spec error §F (517/538)

Both issue #5 and [PLAN-CRITICAL-REVIEW §F](./PLAN-CRITICAL-REVIEW.md#f-stream-11-settling-condition-not-quantified)
spec the settling source as "the existing 517 position push." **This is wrong.** Per
[`Codes.kt`](../../shared/src/commonMain/kotlin/dev/openpolaris/core/protocol/Codes.kt), `517` is
`GET_GIMBAL_POS` (RA/Dec gimbal position), not tilt. The auto-level controller must consume the
tilt-state push, which is `SET_TILT_STATE = 538`. The implementation reads 538. This error does
not block #5, but the spec wording in #5 and §F should be corrected when the issue is next
touched.

### Production gap (closed, with caveat on this worktree): `MountSession` background reader

`AutoLevelController.runAndAwait(timeout)` works in tests (the test source injects frames
directly) and in `cli-probe` (the harness's per-call `request()` is the reader). On real
hardware, `MountSession.request()` owns a per-call reader loop and there is no background reader
that would let `runAndAwait` consume the 538 push frames. The settling predicate is correct;
the data path from socket → controller needs a session-level background reader. **Issue #6
closed 2026-08-30T15:50:54Z** with reader work at `d693197` / `55c83f9` / `73474ed`.

**Caveat per #19**: the reader's *connect-time race fix* did land on this worktree (commit
`55c83f9`, "plan-#7-3i: decouple reader race"). The SUSPEND+tryEmit contradiction
in the publisher path was fixed in the #19 slice (see commit on this branch that
replaces `BufferOverflow.SUSPEND` with `BufferOverflow.DROP_OLDEST` on
`MountSession._tilt` and exposes `tiltDrops: StateFlow<Long>` so silent loss is
visible). The reader's `tryEmit` is now intentional: with DROP_OLDEST it never
returns false on a healthy buffer, and the `subscriptionCount == 0` guard
increments the counter only when the emit is guaranteed lost. Two regression
tests in `TiltStreamTest.kt` (`zeroSubscriberPushesIncrementTiltDrops`,
`liveCollectorDoesNotIncrementTiltDrops`) pin the contract end-to-end through the
real reader loop.

Known follow-up tickets (not yet filed): the MJPEG-decode-on-GL-thread issue
([PLAN-CRITICAL-REVIEW.md §I](./PLAN-CRITICAL-REVIEW.md#i-mjpeg-decode-on-the-gl-thread-is-unowned))
is currently deferred-to-forever because the VR scene-graph work (§G) is the only path that would
ever bring it back on the critical path. If the 50 fps VR bar above is to hold, the deferral needs
to be made explicit in the VR workstream or §I will silently slip.

## Plan / GitHub reconciliation

The plan and the GitHub issue mirror are both sources of truth. They drift when
one is updated without the other. The drift observed during the critical review
after Stream 7.5:

- **2026-08-30T17:42:00Z (issue #19 closed, commit `7970e55`)**: the SUSPEND+tryEmit
  fix per the 2026-08-30T16:12:51Z entry landed as `7970e55` on
  `agents/connectivity-tests-for-polaris` (pushed to origin). Four files:
  `MountSession.kt` (DROP_OLDEST + `tiltDrops` counter + reader comment), and
  `TiltStreamTest.kt` (two regression tests). PLAN.md updated in three places
  ("Caveat per #19", the 16:12:51Z reconciliation entry, and "Immediate next
  actions" item 2). Full `:shared:jvmTest --rerun-tasks` is green at 222/222.
  Issue **#19 closed** with a comment listing the four-file edit list, the two
  new test names and assertions, and the test pass count. The plan and the
  mirror now agree: open = #17, #20-#27; closed includes #19 with the
  shippable ref. Next slice is **#20** (3a.1 `Session.shutdown` no-leak JVM
  test, p1) per item 5 of the "Immediate next actions" list below.
- **2026-08-30T16:12:51Z (issue #19)**: the "Immediate next actions" list
  named issue #6 as the next slice, but #6 was already closed (2026-08-30T15:50:54Z).
  The connect-time race fix from #6 did land on this worktree (commit `55c83f9`).
  The SUSPEND+tryEmit critique in the #6 reopen is now fixed in the #19 slice:
  `BufferOverflow.SUSPEND` → `BufferOverflow.DROP_OLDEST` on `_tilt`, plus a
  new `tiltDrops: StateFlow<Long>` counter, plus two regression tests in
  `TiltStreamTest.kt`. Full `:shared:jvmTest --rerun` is green at 222/222
  (was 220, +2 from this slice). Issue #19 is closed. See the "Caveat per #19"
  paragraph above and item 2 in the "Immediate next actions" list below for
  the new contract.
- **2026-08-30T15:51:06Z (issue #7 close)**: the re-slice commit proposed
  sub-issues 3a/3b/3c, but no separate issues were filed. The plan's
  "Issue #3a: `Session.shutdown` + JVM no-leak test" referenced an issue that
  did not exist on GitHub.

To prevent recurrence, the agent runs a reconciliation audit at the end of
every critical review (and at the end of every slice, where the mirror-honesty
bar of the "Non-functional acceptance" section already requires it). The audit
is three commands, captured in the slice's commit body:

```sh
# (a) every "open" issue referenced in this plan is open on GitHub
gh issue list --repo ian-morgan99/OpenPolaris --state open --limit 100

# (b) every "next-slice" / "shipped" claim in this plan has a corresponding
#     issue, open or closed, in the right state
gh issue list --repo ian-morgan99/OpenPolaris --state all --limit 100

# (c) the worktree's branch has the SHAs cited in the plan
git log --oneline -50
```

A divergence between (a)/(b) and the plan body is a first-class failure of the
repository-mirror-honesty bar in the "Non-functional acceptance" section. The
slice is not mergeable until the divergence is fixed (either the issue list is
reconciled by reopening/closing/filing issues, or the plan is corrected and
the change is cited in the commit body).

## Risk register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Hardware test nights lost to weather | High | Schedule slip | cli-probe enables daytime indoor testing (jog/status/camera); reserve sky-dependent checks for G2/G3 gates |
| Undocumented firmware behaviors surface | Medium | Rework | Tolerant parser + timeouts everywhere; log-and-continue rather than crash; compare against stock app live |
| Camera command ranges differ per model | Medium | Wrong sends | Range tables validated at G3 side-by-side; disable unverified ranges |
| KMP/Compose learning curve | Low–Med | Slowdown | Fallback: plain Android (Kotlin) single-target — architecture isolates this choice to ui/ |
| Scope creep into v2 enhancements | Medium | Delay | Feature flags hide hooks; v1 acceptance = SPEC.md §7 only |
| Benro firmware update changes protocol | Low | Breakage | cli-probe regression script re-runs golden frames after any mount update |
| **50 fps VR bar silently slips** (§I deferral) | Medium | VR bar violated, no early warning | **Resolved 2026-05:** §I is now Stream 7.11 (per PLAN-CRITICAL-REVIEW §P). Owner is whichever agent picks up Stream 7 work next. The bar is no longer at risk of silent slip; it is on the workstream's critical path. |

## Out of scope (v1)

iOS build (architecture permits later), firmware flashing, cellular/cloud remote, panorama stitching,
accounts/analytics, and all v2 enhancement features (rate trims, drift meter, sync points).

## Plan reviews

- [PLAN-CRITICAL-REVIEW.md](./PLAN-CRITICAL-REVIEW.md) — first-class review, identifies 10 blockers
  (Stream 2 staleness, no non-functional bars, no CI gate, undefined iOS test surface, unowned
  `MountSession.lastError` / MJPEG-decode / session-lifecycle traps) and proposes the next 3 slices.

  **Status of the 10 blockers (re-cited with SHAs and issue links, per §Q):**
  - §A (Stream 2 staleness) — addressed organically at commits `ee6eb1c` (OnDevicePlateSolver
    + solveAndRefine) and `88-*` (AstroMath.toEquatorial fix); stream body marked "in flight"
    in PLAN-CRITICAL-REVIEW live-status note.
  - §B (non-functional bars) — **shipped** in commit `7a1c3ec`; see "Non-functional acceptance"
    below.
  - §C (regression gate) — **shipped** in commit `7a1c3ec`; see "Definition of mergeable" below.
  - §D (`ask_user` budget) — **shipped** in commit `7a1c3ec`; see "Definition of unblockable"
    below. See also §M of PLAN-CRITICAL-REVIEW: timeout rule added.
  - §E (iOS / desktop test surface) — open; the bars above scope iOS/desktop to "build succeeds".
    No commit or issue.
  - §F (AutoLevel settling) — **shipped** in commits `4cb24bf` / `4683ebf` (12/12 JVM tests,
    sealed `AutoLevelResult`, injectable `sampleSource`, `runAndAwait(timeout)`, `AppViewModel`
    wired, `FakeMount` ramp→settled 538 stream). Issue #5 closed. Note the
    [517/538 spec error](#spec-error-§f-517538) flagged in both #5 and §F. Production gap:
    real-hardware `MountSession` had no background reader; the connect-time race fix
    landed in commit `55c83f9` (plan-#7-3i). #6 was opened 2026-08-30, reopened the same
    day with a sharp critique of the SUSPEND+tryEmit loss path, and re-closed
    2026-08-30T15:50:54Z. The reopen critique was never addressed on any branch. On this
    worktree (HEAD `56831b1`), the bug is still present at `MountSession.kt` line 301 and
    the next slice is the [issue #19 fix](https://github.com/ian-morgan99/OpenPolaris/issues/19)
    (see "Immediate next actions" item 2).
  - §G (VR backlog) — partially shipped. 7.1-7.3 landed at `13d9cd0`; 7.4-7.10 outstanding,
    no owner. The "backlog in checkpoint 119" gap is the source of the §P risk-register entry
    below.
  - §H (MountSession.lastError) — shipped at `bcfc6e6` / `ea53bf0`; issue #4 closed
    (close-out comment posted *and* `gh issue close` issued — verified in the issue #2
    refresh per merge condition 6).
  - §I (MJPEG decode on GL thread) — **promoted to Stream 7.11** per §P below; no longer
    "deferred-to-forever" but unowned until Stream 7 work resumes.
  - §J (Session pause/resume) — open as issue #3. **Re-slicing required** per §K of
    PLAN-CRITICAL-REVIEW; tracked as issue #7 once filed.

## Immediate next actions

In order, picked by the next agent. **Note**: this list was rewritten after the
critical review following Stream 7.5 (see [issue #19](https://github.com/ian-morgan99/OpenPolaris/issues/19)).
It no longer references issue #6 as the next slice — #6 is closed and only the
connect-time race fix (commit `55c83f9`) is on this worktree. The SUSPEND+tryEmit
reopen critique has now been addressed on this worktree as item 2 below (see
"Plan / GitHub reconciliation" 2026-08-30T16:12:51Z entry for the commit ref);
issue #19 is closed.

1. ~~Issue #5: ship the AutoLevel settling condition with tests.~~ **DONE**
   (12/12 JVM tests, code slice, issue #5 closed at commit `4cb24bf` / `4683ebf`).
2. **MountSession.tilt: fix the SUSPEND+tryEmit contradiction (issue #19 first
   acceptance criterion)** — **DONE** on this worktree (commit on this branch;
   see "Plan / GitHub reconciliation" 2026-08-30T16:12:51Z entry below).
   Chose option **(A)**: `BufferOverflow.SUSPEND` → `BufferOverflow.DROP_OLDEST`
   on `_tilt`, and exposed `tiltDrops: StateFlow<Long>` (reset to 0 on every
   (re)connect) so silent loss is observable. The reader's `tryEmit` is now
   intentional: with DROP_OLDEST it never returns false on a healthy buffer,
   and the `subscriptionCount == 0` guard after each emit increments the
   counter only when the emit is guaranteed lost. The comment block at the
   reader was rewritten to document the honest contract. Two regression tests
   in `TiltStreamTest.kt` pin the contract end-to-end through the real reader
   loop:
   - `zeroSubscriberPushesIncrementTiltDrops` — 100 538 frames pushed through
     `FakeConnection` with no collector attached → `tiltDrops == 100`.
   - `liveCollectorDoesNotIncrementTiltDrops` — `take(10).toList()` collector
     drains 10 frames → `tiltDrops == 0`.
   Full `:shared:jvmTest --rerun` is green at **222/222** (was 220, +2 from
   this slice). Issue #19 is closed with a comment documenting the four-file
   edit list and the new test coverage.
3. ~~**§F contract test (per §L of PLAN-CRITICAL-REVIEW)** — add two tests to
   `AutoLevelControllerTest.kt`.~~ **DONE** in commit `7970e55`. The shipped
   tests are `gimbalPosFrame517DoesNotFeedTilt` (line 272) and
   `tiltStateFrame538DoesFeedTilt` (line 298). Both pass in
   `:shared:jvmTest --rerun-tasks` at 222/222. The original "Settling" suffix
   was renamed to "Tilt" before commit because the test asserts the *tilt
   stream* contract (no 517 frame ever feeds `_tilt`), not the *settling*
   contract. PLAN-CRITICAL-REVIEW §F wording is updated accordingly.
4. **File 3a/3b/3c as separate issues (issue #19 acceptance criterion)** —
   planning commits, no code. Closes the `next-slice-ready` rule 4 violation
   §K surfaces. 3a.1 = `AutoLevelController.stop()` + restart test (no leak);
   3a.2 = `_tilt.value` survives stop/start; 3b.1 = scope cancel
   mid-`runAndAwait` returns `Failed("cancelled")` within 1s; 3b.2 = no leftover
   coroutines (via `kotlinx.coroutines.debug`); 3c.1-3c.4 = SessionMarker,
   SessionStore, file-backed, auto-reconnect prompt.
   **Filed 2026-08-30T17:18Z**:
   [#20](https://github.com/ian-morgan99/OpenPolaris/issues/20) 3a.1 Session.shutdown no-leak (p1);
   [#21](https://github.com/ian-morgan99/OpenPolaris/issues/21) 3a.2 _tilt.value survives (p1);
   [#22](https://github.com/ian-morgan99/OpenPolaris/issues/22) 3b.1 cancel returns Failed(cancelled) (p1);
   [#23](https://github.com/ian-morgan99/OpenPolaris/issues/23) 3b.2 no leftover coroutines (p1);
   [#24](https://github.com/ian-morgan99/OpenPolaris/issues/24) 3c.1 SessionMarker (p2);
   [#25](https://github.com/ian-morgan99/OpenPolaris/issues/25) 3c.2 SessionStore interface (p2);
   [#26](https://github.com/ian-morgan99/OpenPolaris/issues/26) 3c.3 FileSessionStore (p2);
   [#27](https://github.com/ian-morgan99/OpenPolaris/issues/27) 3c.4 auto-reconnect prompt (p2).
   p1 sub-issues unblock after item 2 lands; p2 sub-issues are deferred
   to a later slice.
5. **Issue #20: 3a.1 `Session.shutdown` + JVM no-leak test** — ~80 LoC. After
   items 2-4 land. The live issue is the source of truth:
   [#20](https://github.com/ian-morgan99/OpenPolaris/issues/20).
6. **Issue #21: 3a.2 `_tilt.value` survives `Session.stop()`/`start()`** — p1,
   unblocks after #20. ~60 LoC. Live:
   [#21](https://github.com/ian-morgan99/OpenPolaris/issues/21).
7. **Issue #22: 3b.1 `runAndAwait` cancellation returns `Failed("cancelled")`
   within 1s** — p1, unblocks after #21. ~60 LoC. Live:
   [#22](https://github.com/ian-morgan99/OpenPolaris/issues/22).
8. **Issue #23: 3b.2 No leftover coroutines after `runAndAwait` cancellation**
   (gated on `-Pkotlinx.coroutines.debug=true`) — p1, unblocks after #22.
   ~60 LoC. Live:
   [#23](https://github.com/ian-morgan99/OpenPolaris/issues/23).
9. **Issue #24: 3c.1 `SessionMarker` data class with `kotlinx.serialization`
   JSON** — p2, deferred to a later slice. ~80 LoC. Live:
   [#24](https://github.com/ian-morgan99/OpenPolaris/issues/24).
10. **Issue #25: 3c.2 `SessionStore` interface (save, loadAll, delete, latest)**
    — p2, deferred. ~80 LoC. Live:
    [#25](https://github.com/ian-morgan99/OpenPolaris/issues/25).
11. **Issue #26: 3c.3 `FileSessionStore` in `androidApp/src/androidMain`**
    (atomic temp+rename) — p2, deferred. ~100 LoC. Live:
    [#26](https://github.com/ian-morgan99/OpenPolaris/issues/26).
12. **Issue #27: 3c.4 Auto-reconnect prompt on Wi-Fi loss** (uses
    `SessionStore.latest()`) — p2, deferred. ~90 LoC. Live:
    [#27](https://github.com/ian-morgan99/OpenPolaris/issues/27).

p1 sub-issues (#20-#23) ship in queue order before any p2 work begins
(see the `next-slice-ready` condition 4 in PLAN.md and the
[live p1 queue](https://github.com/ian-morgan99/OpenPolaris/issues?q=is%3Aissue+is%3Aopen+label%3Apriority%2Fp1+sort%3Acreated-asc)).
6. **Stream 7.6-7.10 + 7.11 (new per §P)** — when Stream 7 work resumes; 7.11
   owns the MJPEG-on-GL-thread fix that was previously "deferred-to-forever".
   7.4-7.5 are shipped (`f948ced` / `ff81c2c`); 7.5 (recenter) shipped
   `56831b1`; 7.6 (VR recenter persistence across sessions) is the natural
   successor to 7.5.
7. **Issue #17: polkit rule typo** — blocked on user; reminder pinged at
   session start.
8. **Stream 5.3 real-mount smoke** — blocked on user hardware.
9. **Stream 6.2 iOS / desktop test surface** — blocked on user build.

Items 2 and 3 have now landed (commit `7970e55`); the next agent MUST verify
on resume that the worktree is at or past `7970e55` and that
`:shared:jvmTest --rerun-tasks` is still green at 222/222 before starting
item 5. Item 4 (the filed #20-#27 sub-issues) is also landed and should
be confirmed in the worktree before any code change. If a reviewing
agent files a P0 (label `priority/p0`) in between, that preempts per the
`next-slice-ready` condition 3.
