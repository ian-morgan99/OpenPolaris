# Runtime-First UI Lens

This project's UI quality bar is runtime-first: do not conclude from static code or screenshots alone.

## Core rule

For any UI bug report (missing control, clipped control, bad flow, wrong state, unusable on device):

1. Reproduce on a real runtime first (emulator or device).
2. Capture evidence (screenshot + UI dump + relevant log lines).
3. Only then classify root cause and propose fix.

If a claim cannot be reproduced in current runtime state, mark it unresolved and keep gathering runtime evidence.

## Required evidence bundle

Minimum bundle per finding:

1. One screenshot at the failing step.
2. One `uiautomator dump` taken at the same step.
3. Foreground activity confirmation from `dumpsys activity`.
4. Any relevant app logs (`adb logcat` filters + timestamp window).

Without this bundle, findings are preliminary.

## Investigation sequence

Use this sequence for every UI defect:

1. Confirm runtime target:
   - correct APK/build installed,
   - correct package in foreground,
   - device orientation and size class known.
2. Reproduce exact user path end-to-end.
3. Capture evidence bundle at each transition.
4. Differentiate:
   - not rendered,
   - rendered but off-viewport,
   - rendered but disabled,
   - rendered in a different container/state.
5. Patch the smallest layout/state change that resolves the runtime symptom.
6. Re-run path and capture after-fix evidence.

## UI acceptance gates

A UI fix is complete only when all are true:

1. Primary task controls are visible in first viewport on phone landscape.
2. Overflow/secondary controls remain reachable by scrolling.
3. Dialog actions are reachable without precision tapping.
4. Evidence bundle exists for before and after states.
5. Build compiles and no new diagnostics are introduced.

## Screenshot set standard

For callout-based surfaces, the baseline set is:

1. Main surface
2. Connection (Wi-Fi) pane
3. Slew pane
4. Camera pane
5. Preview pane
6. More menu
7. Helpers pane
8. Firmware pane
9. Settings pane

Name with stable numeric prefixes so diffs are deterministic.

## Anti-patterns (do not do)

1. Declaring controls "missing" without a runtime scroll check.
2. Declaring controls "present" from source only.
3. Mixing evidence from different foreground apps/states.
4. Treating stale screenshots as current runtime truth.

## Ownership note

When user and static inspection disagree, runtime evidence wins until disproven by newer runtime evidence.
