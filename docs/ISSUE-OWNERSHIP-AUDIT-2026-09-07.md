# Cross-repository open-issue ownership audit — 2026-09-07

Snapshot taken after reviewing the title, body, acceptance criteria and current
cross-links of every open GitHub issue in:

- `ian-morgan99/OpenPolaris`
- `ian-morgan99/benro-polaris-firmware-patcher`
- `ian-morgan99/libgphoto2`

Ownership rule:

| Repository | Owns |
|---|---|
| OpenPolaris | client UI/state, protocol construction/parsing, network bridge, preview transport, client-side firmware delivery and release packaging |
| firmware patcher | reproducible FwPkt construction, embedded binaries/libraries, Stage-2, pgphoto/watchdog/runtime behavior and firmware-side hardening |
| libgphoto2 | behavior independently reproducible with that repository's build and a directly attached camera; upstream preparation and generic PTP2/Pentax implementation |

## Result

No open libgphoto2 issue is misplaced. The camera investigations there are
explicit isolation gates rather than claims based only on Polaris behavior.

Two OpenPolaris issues mixed ownership:

- #64 correctly retains the client/CLI destructive-command guard. The silent
  firmware handler and device logging work was split to patcher #40.
- #65 remains in OpenPolaris only as the process-violation/unsafe-helper incident
  record. Device restoration and reproducible firmware repair belong to patcher
  #38/#39.

Three non-actionable/stale patcher tracking issues were closed during this
audit: duplicate subscription seeds #25/#26 and superseded conditional updater
investigation #22. Patcher #11/#15 remain correctly located but were annotated
to remove reliance on stale pre-flash and side-loading assumptions.

## OpenPolaris audit

| Issue | Decision | Reason / required adjustment |
|---:|---|---|
| #66 | Correct | live protocol map and client-facing command registry |
| #65 | Mixed; retained as incident record | OpenPolaris owns preventing unsafe helper/process recurrence; patcher #38/#39 own device repair |
| #64 | Split | OpenPolaris owns CLI confirmation/deny-list; new patcher #40 owns firmware validation/logging |
| #63 | Correct | cross-client camera parity and E2E matrix |
| #62 | Correct | OpenPolaris camera opcode registry and optimistic UI writes |
| #61 | Correct | client first-frame/stale-frame detection; patcher #36 owns missing embedded frames |
| #60 | Correct | client asynchronous capture state; patcher #37 owns runtime response contract |
| #59 | Correct | OpenPolaris wake, bridge, route validation and keepalive orchestration |
| #57 | Correct | application structured logging |
| #56 | Correct | OpenPolaris E2E qualification and cross-repo attribution umbrella |
| #53 | Correct | client installer/network privilege boundary |
| #51 | Correct | OpenPolaris SSH delivery cancellation/watchdog implementation |
| #50 | Correct | OpenPolaris release metadata/CI |
| #49 | Correct | firmware-selection UI flow, despite empty issue body |
| #48 | Correct | in-app manual and renderer |
| #47 | Correct | application mobile visual/accessibility test baseline |
| #45 | Correct | application responsive dialog implementation |
| #44 | Correct | Android application signing/release identity |
| #41 | Correct | OpenPolaris Windows packaging workflow |

## Firmware-patcher audit

| Issue | Decision | Reason / required adjustment |
|---:|---|---|
| #40 | Correct; created by audit | firmware-side validation/audit logging for destructive code 789 |
| #39 | Correct, evidence reconciliation required | packaged `/app/bin`/launcher contents and restart target are firmware-layout work; reconcile its “empty” snapshot with #38's `/app/bin/gphoto2` command |
| #38 | Correct, evidence reconciliation required | staged port library, iolib lookup, ABI and architecture gates are patcher build defects; reconcile exact device/timepoint with #39/#65 |
| #37 | Correct | runtime emits misleading capture state; OpenPolaris #60 separately owns client handling |
| #36 | Correct; likely downstream of #38/#39 | embedded preview failure reproduces without OpenPolaris; close only after rebuilt firmware proves root cause and regression |
| #35 | Correct but stale umbrella | embedded K-3 III capability isolation belongs here; body predates successful delayed capture and newer #36/#38 evidence |
| #34 | Correct | pgphoto/watchdog/session recovery belongs to runtime; flat-battery run is explicitly invalid evidence |
| #31 | Correct | decision about adopting an upstream patcher debug-access feature |
| #15 | Correct but stale | canonical firmware/release qualification umbrella; must remove reversible side-loading instruction and refresh resolved prerequisites |
| #11 | Correct but stale | HDMI/DEAD-site and release-language work belongs here; candidate has now been flashed, contrary to old body |
| #8 | Correct | HDMI binary patcher's DEAD-site handling |
| #2 | Correct | appliance runtime memory ceiling in generated firmware wrapper |

Closed during audit:

- #25 and #26: duplicate, non-actionable cross-project subscription seeds.
- #22: conditional package-rejection investigation superseded by the isolated
  stale-manifest cause (#23) and later successful verified install.

## libgphoto2 audit

| Issue | Decision | Reason / required adjustment |
|---:|---|---|
| #49 | Correct as an isolation gate | asks for direct K-1 II reproduction of WB/aperture false failure; close/move downstream if direct test passes |
| #48 | Correct as an isolation gate | asks for direct K-1 II capture reproduction; close/move downstream if direct test passes |
| #46 | Correct | final upstream submission gate |
| #44 | Correct | direct-camera hardware validation matrix |
| #42 | Correct | upstream-style source isolation/maintainability audit |
| #41 | Correct | upstream-facing clean Pentax patch series |

## Required follow-up hygiene

1. Reconcile patcher #38/#39/#65 into one timestamped deployed-file inventory;
   their current bodies describe conflicting `/app/bin` state and multiple
   direct modifications.
2. Refresh patcher #35, #11 and #15 bodies or replace their stale claims with a
   current status section.
3. Keep OpenPolaris #64 and patcher #40 cross-linked; do not attempt to solve a
   firmware handler weakness solely in the client.
4. Keep #60/#37 and #61/#36 as explicit client/runtime pairs rather than moving
   either half into the other repository.
5. For libgphoto2 #48/#49, the next ownership decision is determined only by a
   clean direct K-1 II run. A Polaris-only result must not keep a source-library
   defect open indefinitely.

