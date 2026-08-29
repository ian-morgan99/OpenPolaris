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

## Risk register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Hardware test nights lost to weather | High | Schedule slip | cli-probe enables daytime indoor testing (jog/status/camera); reserve sky-dependent checks for G2/G3 gates |
| Undocumented firmware behaviors surface | Medium | Rework | Tolerant parser + timeouts everywhere; log-and-continue rather than crash; compare against stock app live |
| Camera command ranges differ per model | Medium | Wrong sends | Range tables validated at G3 side-by-side; disable unverified ranges |
| KMP/Compose learning curve | Low–Med | Slowdown | Fallback: plain Android (Kotlin) single-target — architecture isolates this choice to ui/ |
| Scope creep into v2 enhancements | Medium | Delay | Feature flags hide hooks; v1 acceptance = SPEC.md §7 only |
| Benro firmware update changes protocol | Low | Breakage | cli-probe regression script re-runs golden frames after any mount update |

## Out of scope (v1)

iOS build (architecture permits later), firmware flashing, cellular/cloud remote, panorama stitching,
accounts/analytics, and all v2 enhancement features (rate trims, drift meter, sync points).

## Plan reviews

- [PLAN-CRITICAL-REVIEW.md](./PLAN-CRITICAL-REVIEW.md) — first-class review, identifies 10 blockers
  (Stream 2 staleness, no non-functional bars, no CI gate, undefined iOS test surface, unowned
  `MountSession.lastError` / MJPEG-decode / session-lifecycle traps) and proposes the next 3 slices.

## Immediate next actions

1. Scaffold repo structure per ARCHITECTURE.md §2.
2. Implement protocol layer + golden tests (Phase 0).
3. Build cli-probe and book the first hardware session for Gate G1.
