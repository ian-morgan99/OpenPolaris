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
- `cli-probe --fake` runs every controller end-to-end and exits 0 in < 30 s.
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
- Android target: smoke-tested in `cli-probe --fake` plus a manual on-device test plan.
- iOS / desktop targets: build succeeds; controllers compile; no runtime validation needed for
  v1 (out of scope).

## Definition of mergeable

A commit is mergeable when **all six** of these hold:

1. `./gradlew :shared:jvmTest :composeApp:jvmTest` is green.
2. `./gradlew :tools:cli-probe:test` is green (FakeMount end-to-end).
3. `./gradlew :tools:cli-probe:run --args="--fake"` exits 0 (the 30-command scripted smoke).
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

## Definition of next-slice-ready

The agent self-picks the next slice when **all** of the following hold, and pauses for a reviewing
agent otherwise:

1. **The previous slice shipped clean.** All 6 merge conditions green; the issue it claimed to close
   is in fact closed on GitHub at the time of commit (mirror-honesty bar).
2. **The current top-of-list issue is action-ready.** Its acceptance criteria are unambiguous from
   the issue body and a code site exists (or is named). No "design first" steps needed before
   the first commit.
3. **No new high-priority issue has been posted by the reviewing agent.** If the reviewing agent
   has filed a P0/P1 issue, that preempts the queue regardless of position.
4. **The slice is bounded.** A single issue's acceptance is one PR-sized commit. A slice that would
   span two or more issues must be split.

**If any condition fails, the agent pauses** and writes a one-paragraph note explaining which
condition failed and what the next step is (refine the spec, ask the user, wait for the review
agent, etc.). The plan is the source of truth for this rule.

## Current stream priorities

Open issues, in priority order. Tracked in the session todo mirror and on
[GitHub issues](https://github.com/ian-morgan99/OpenPolaris/issues):

- **#2 — non-functional acceptance bars** *(this commit)*. Folds §B + §C + §D into the live
  plan.
- **#5 — AutoLevel settling condition quantified** ([PLAN-CRITICAL-REVIEW.md §F](./PLAN-CRITICAL-REVIEW.md#f-stream-11-settling-condition-not-quantified)).
  3 JVM tests + a `timeout` parameter on the controller. Code, not docs.
- **#3 — session pause/resume hardening** ([PLAN-CRITICAL-REVIEW.md §J](./PLAN-CRITICAL-REVIEW.md#j-there-is-no-pause-and-resume-session-story-for-the-controllers)).
  Four sub-tasks: (9.1) `Session.shutdown()` symmetric to `Session.connect()`, (9.2) `AppViewModel.disconnect()`,
  (9.3) `onResume` reconnect, (9.4) JVM test for lifecycle. Defer until #5 lands so the mount
  contract is stable first. The sub-task list above is the slice-boundary contract — shipping #3
  means shipping 9.1-9.4, not just `shutdown()`.

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
| **50 fps VR bar silently slips** (§I deferral) | Medium | VR bar violated, no early warning | Either (a) the VR scene-graph workstream (§G) explicitly pulls §I onto its critical path with an owner, or (b) the bar is downgraded. Currently neither is decided. |

## Out of scope (v1)

iOS build (architecture permits later), firmware flashing, cellular/cloud remote, panorama stitching,
accounts/analytics, and all v2 enhancement features (rate trims, drift meter, sync points).

## Plan reviews

- [PLAN-CRITICAL-REVIEW.md](./PLAN-CRITICAL-REVIEW.md) — first-class review, identifies 10 blockers
  (Stream 2 staleness, no non-functional bars, no CI gate, undefined iOS test surface, unowned
  `MountSession.lastError` / MJPEG-decode / session-lifecycle traps) and proposes the next 3 slices.

  **Status of the 10 blockers:**
  - §A (Stream 2 staleness) — addressed organically; stream is documented as "in flight" in the
    Critical Review's live-status note.
  - §B (non-functional bars) — **shipped into this file**, see "Non-functional acceptance" above.
  - §C (regression gate) — **shipped into this file**, see "Definition of mergeable" above.
  - §D (`ask_user` budget) — **shipped into this file**, see "Definition of unblockable" above.
  - §E (iOS / desktop test surface) — open; the bars above scope iOS/desktop to "build succeeds".
  - §F (AutoLevel settling) — open as issue #5.
  - §G (VR backlog) — open, 7.1-7.3 shipped; 7.4-7.10 outstanding.
  - §H (MountSession.lastError) — shipped (`bcfc6e6` / `ea53bf0`), issue #4 closed (during the
    issue #2 refresh — the prior close-out comment was posted but the `gh issue close` was
    never issued, violating the new merge condition 6; this slice caught and fixed it).
  - §I (MJPEG decode on GL thread) — deferred-to-forever; needs explicit VR follow-up.
  - §J (Session pause/resume) — open as issue #3.

## Immediate next actions

1. Scaffold repo structure per ARCHITECTURE.md §2.
2. Implement protocol layer + golden tests (Phase 0).
3. Build cli-probe and book the first hardware session for Gate G1.
4. Issue #5: ship the AutoLevel settling condition with tests; this unblocks issue #3 (session
   pause/resume) and is the next code change.
