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

## Current stream priorities

Open issues, in priority order. Tracked in the session todo mirror and on
[GitHub issues](https://github.com/ian-morgan99/OpenPolaris/issues):

- **#5 — AutoLevel settling condition quantified** ([PLAN-CRITICAL-REVIEW.md §F](./PLAN-CRITICAL-REVIEW.md#f-stream-11-settling-condition-not-quantified))
  *(shipped, awaiting close)*. `AutoLevelController.run(timeout: Duration = 60.seconds)` returns
  `AutoLevelResult = Completed(rollDeg, pitchDeg) | Failed(reason) | TimedOut`. Settling predicate
  is **10 consecutive samples within ±0.01° of the running mean of (roll, pitch) on the tilt-state
  push (538)**, not the RA/Dec position push (517) — see [Spec error §F](#spec-error-§f-517538)
  below. 12 JVM tests, all green; `FakeMount` now emits a sustained ramp→settled 538 stream
  (configurable via `timeToSettleMs`).
- **#3 — session pause/resume hardening** ([PLAN-CRITICAL-REVIEW.md §J](./PLAN-CRITICAL-REVIEW.md#j-there-is-no-pause-and-resume-session-story-for-the-controllers)).
  Four sub-tasks: (9.1) `Session.shutdown()` symmetric to `Session.connect()`, (9.2) `AppViewModel.disconnect()`,
  (9.3) `onResume` reconnect, (9.4) JVM test for lifecycle. Ready to start now that #5's mount
  contract is stable. The sub-task list above is the slice-boundary contract — shipping #3 means
  shipping 9.1-9.4, not just `shutdown()`.

**Closed (recent):**
- **#2 — non-functional acceptance bars** (shipped `4683ebf`): folds §B + §C + §D into the live
  plan.
- **#4 — `MountSession.lastError` ownership** (shipped `bcfc6e6` / `ea53bf0`, closed during the
  #2 refresh).

### Spec error §F (517/538)

Both issue #5 and [PLAN-CRITICAL-REVIEW §F](./PLAN-CRITICAL-REVIEW.md#f-stream-11-settling-condition-not-quantified)
spec the settling source as "the existing 517 position push." **This is wrong.** Per
[`Codes.kt`](../../shared/src/commonMain/kotlin/dev/openpolaris/core/protocol/Codes.kt), `517` is
`GET_GIMBAL_POS` (RA/Dec gimbal position), not tilt. The auto-level controller must consume the
tilt-state push, which is `SET_TILT_STATE = 538`. The implementation reads 538. This error does
not block #5, but the spec wording in #5 and §F should be corrected when the issue is next
touched.

### Production gap: `MountSession` has no background reader

`AutoLevelController.runAndAwait(timeout)` works in tests (the test source injects frames
directly) and in `cli-probe` (the harness's per-call `request()` is the reader). On real
hardware, `MountSession.request()` owns a per-call reader loop and there is no background reader
that would let `runAndAwait` consume the 538 push frames. The settling predicate is correct;
the data path from socket → controller still needs a session-level background reader. This is
a new follow-up issue (likely the first item of #3, or a new #6), not part of #5's scope.

Known follow-up tickets (not yet filed): the MJPEG-decode-on-GL-thread issue
([PLAN-CRITICAL-REVIEW.md §I](./PLAN-CRITICAL-REVIEW.md#i-mjpeg-decode-on-the-gl-thread-is-unowned))
is currently deferred-to-forever because the VR scene-graph work (§G) is the only path that would
ever bring it back on the critical path. If the 50 fps VR bar above is to hold, the deferral needs
to be made explicit in the VR workstream or §I will silently slip.

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
    real-hardware `MountSession` has no background reader; this is issue #6 (open, with
    `enhancement` label).
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

In order, picked by the next agent:

1. ~~Issue #5: ship the AutoLevel settling condition with tests.~~ **DONE** (12/12 JVM tests, code
   slice, issue #5 closed at commit `4cb24bf` / `4683ebf`).
2. **Issue #6: `MountSession` background reader** — ships first. The `tilt: Flow<TiltSample>`
   contract is on the critical path: it unblocks the 517/538 contract test (§L of
   PLAN-CRITICAL-REVIEW), the production `AutoLevelController.runAndAwait` path, and issue #3a's
   `Session.shutdown` JVM test (a real reader is what makes the no-leak assertion meaningful).
   ~150-250 LoC + ~30 LoC JVM test. Hard-blocked on nothing.
3. **§F contract test** (per §L of PLAN-CRITICAL-REVIEW) — add two tests to
   `AutoLevelControllerTest.kt`: `gimbalPosFrame517DoesNotFeedSettling` and
   `tiltStateFrame538DoesFeedSettling`. Lands in the same commit as #6, since the test depends
   on the production sample source. Without this test, the 517/538 spec error fix is
   unverified against future refactor.
4. **Issue #7: re-slice #3 into 3a/3b/3c** — planning commit, no code. Closes the
   `next-slice-ready` rule 4 violation §K surfaces.
5. **Issue #3a: `Session.shutdown` + JVM no-leak test** — ~80 LoC. After #6 lands and
   #7 is filed.
6. **Stream 7.4-7.10 + 7.11 (new per §P)** — when Stream 7 work resumes; 7.11 owns
   the MJPEG-on-GL-thread fix that was previously "deferred-to-forever".
7. **Stream 5.3 real-mount smoke** — blocked on user hardware.
8. **Stream 6.2 iOS / desktop test surface** — blocked on user build.

The next agent MUST NOT skip step 2 — every other item depends on the `MountSession`
reader contract being stable. If a reviewing agent files a P0 (label `priority/p0`)
in between, that preempts per the `next-slice-ready` condition 3.
