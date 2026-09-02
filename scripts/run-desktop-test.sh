#!/usr/bin/env bash
# Regression test for the scripts/run-desktop.sh "tee to log" wrapper.
#
# Issue #36 noted that the wrapper's `tee` plumbing doesn't actually detach
# the long-running Compose JVM from the calling shell — it stays attached
# for the full lifetime of the JVM and `tee` is purely log redirection, not
# lifecycle management. The wrapper's contract is therefore "foreground
# launcher with optional log file"; the comments and behaviour must agree.
#
# This test sources run-desktop.sh's `run_with_tee` helper and exercises it
# with a stub command (sleep) so we don't need a real gradle invocation.
#
# Contract under test:
#   1. With a log file target, the wrapper stays attached while the long-
#      lived command is running, then exits with the command's exit code.
#   2. The log file receives the command's stdout during the run.
#   3. With OPENPOLARIS_LOG=-, the wrapper still stays attached (no
#      lifecycle change), the command's stdout is not captured to a log
#      file, and the command's exit code propagates.
#   4. The wrapper does not background or detach — when the command is
#      killed mid-run, the wrapper exits non-zero.
#
# Exit codes:
#   0   all assertions passed
#   1   a stub or helper binary is missing (skip)
#   2+  assertion failure
#
# Usage: scripts/run-desktop-test.sh

set -uo pipefail

PROJ_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPTS_DIR="$PROJ_ROOT/scripts"
TMPDIR_TEST="$(mktemp -d -t openpolaris-run-desktop-test.XXXXXX)"
trap 'rm -rf "$TMPDIR_TEST"' EXIT

pass=0
fail=0

ok() { printf '  ok   - %s\n' "$1"; pass=$((pass + 1)); }
ng() { printf '  FAIL - %s\n' "$1"; fail=$((fail + 1)); }

# Source the helper from run-desktop.sh without re-running its main body.
# run-desktop.sh doesn't use a function guard today, so we extract just the
# `run_with_tee` definition by eval'ing a sed slice. If the file is ever
# refactored, this test must be updated to match.
# shellcheck disable=SC1090
if ! grep -q '^run_with_tee()' "$SCRIPTS_DIR/run-desktop.sh"; then
  echo "ERROR: scripts/run-desktop.sh no longer defines run_with_tee(); update this test." >&2
  exit 1
fi
# shellcheck disable=SC1090
eval "$(sed -n '/^run_with_tee() {/,/^}/p' "$SCRIPTS_DIR/run-desktop.sh")"
if ! declare -F run_with_tee >/dev/null; then
  echo "ERROR: failed to source run_with_tee() from run-desktop.sh" >&2
  exit 1
fi

stub_long_lived() {
  # Emit a sentinel line, then sleep until killed or the requested duration
  # elapses. Echoes a final exit-code marker so the parent test can assert
  # on propagation. Args: <seconds-to-run> <sentinel-string>
  local duration="$1" sentinel="$2"
  echo "stub-started: $sentinel"
  echo "stub-pid: $$"
  sleep "$duration"
  echo "stub-finished: $sentinel"
  exit 0
}

# ----- Test 1: foreground + log file -----------------------------------------
# The wrapper should stay attached while the long-lived command runs, the
# log file should receive stdout, and the wrapper should exit with the
# command's exit code once the command finishes.
echo "[1] foreground + log file: stays attached, logs, propagates exit"

LOG_FILE="$TMPDIR_TEST/desktop.log"
( run_with_tee "$LOG_FILE" bash -c '
    echo "stub-started: t1"
    sleep 0.6
    echo "stub-finished: t1"
    exit 0
  '
  echo "wrapper-exit: t1"
) >"$TMPDIR_TEST/wrapper-stdout.log" 2>&1 &
WRAPPER_PID=$!

# After 200ms the wrapper must still be alive (stub is still sleeping).
sleep 0.2
if kill -0 "$WRAPPER_PID" 2>/dev/null; then
  ok "wrapper still attached while stub is alive"
else
  ng "wrapper exited early (pid $WRAPPER_PID gone after 200ms)"
  wait "$WRAPPER_PID" 2>/dev/null || true
  exit 2
fi

# The log file must already contain the stub's startup sentinel.
if grep -q "stub-started: t1" "$LOG_FILE"; then
  ok "log file received stub stdout while running"
else
  ng "log file missing stub-started sentinel"
  cat "$LOG_FILE" >&2
fi

# Wait for the wrapper to finish naturally (stub runs 0.6s, give it 3s).
wait "$WRAPPER_PID"
WRAPPER_RC=$?

if [[ "$WRAPPER_RC" -eq 0 ]]; then
  ok "wrapper propagated stub exit 0"
else
  ng "wrapper exit was $WRAPPER_RC, expected 0"
fi

if grep -q "stub-finished: t1" "$LOG_FILE"; then
  ok "log file captured final stub output"
else
  ng "log file missing stub-finished sentinel"
  cat "$LOG_FILE" >&2
fi

# ----- Test 2: foreground + log file, command exits non-zero -----------------
# When the long-lived command exits non-zero, the wrapper must propagate
# that exit code (via set -o pipefail, the failed upstream closes the pipe
# to tee, which closes the pipe downstream, which makes the subshell exit
# with that status).
echo "[2] foreground + log file: non-zero exit propagates"
LOG_FILE2="$TMPDIR_TEST/desktop2.log"
set +e
( run_with_tee "$LOG_FILE2" bash -c '
    echo "stub-started: t2"
    exit 7
  '
) >"$TMPDIR_TEST/wrapper-stdout2.log" 2>&1
RC2=$?
set -e

if [[ "$RC2" -ne 0 ]]; then
  ok "wrapper exited non-zero ($RC2) when stub failed"
else
  ng "wrapper exited 0 when stub exited 7 (pipefail not effective)"
fi

if grep -q "stub-started: t2" "$LOG_FILE2"; then
  ok "log file still received output before stub exit"
else
  ng "log file missing sentinel for failing stub"
fi

# ----- Test 3: OPENPOLARIS_LOG=- (terminal) keeps wrapper attached -----------
# The "-" branch `exec`s the long-lived command directly — the wrapper must
# still stay attached, the command's exit code must still propagate, and
# no log file should be created.
echo "[3] OPENPOLARIS_LOG=-: wrapper stays attached, no log file written"
( run_with_tee "-" bash -c '
    echo "stub-started: t3"
    sleep 0.4
    exit 0
  '
) >"$TMPDIR_TEST/wrapper-stdout3.log" 2>&1 &
WRAPPER_PID3=$!

sleep 0.1
if kill -0 "$WRAPPER_PID3" 2>/dev/null; then
  ok "wrapper stayed attached in - mode while stub ran"
else
  ng "wrapper exited early in - mode"
fi

wait "$WRAPPER_PID3"
RC3=$?

if [[ "$RC3" -eq 0 ]]; then
  ok "wrapper propagated exit 0 in - mode"
else
  ng "wrapper exit in - mode was $RC3, expected 0"
fi

# Snapshot the test 3 $TMPDIR_TEST contents before the wrapper runs so we
# can assert that test 3 did not create any new log file. (Other tests
# populate the same dir with their own log files; this check is scoped
# to test 3 only.)
BEFORE_T3=$(find "$TMPDIR_TEST" -maxdepth 1 -name '*.log' 2>/dev/null | sort)
( run_with_tee "-" bash -c '
    echo "stub-started: t3"
    sleep 0.4
    exit 0
  '
) >"$TMPDIR_TEST/wrapper-stdout3.log" 2>&1 &
WRAPPER_PID3=$!

sleep 0.1
if kill -0 "$WRAPPER_PID3" 2>/dev/null; then
  ok "wrapper stayed attached in - mode while stub ran"
else
  ng "wrapper exited early in - mode"
fi

wait "$WRAPPER_PID3"
RC3=$?

if [[ "$RC3" -eq 0 ]]; then
  ok "wrapper propagated exit 0 in - mode"
else
  ng "wrapper exit in - mode was $RC3, expected 0"
fi

AFTER_T3=$(find "$TMPDIR_TEST" -maxdepth 1 -name '*.log' 2>/dev/null | sort)
NEW_T3=$(comm -13 <(printf '%s\n' "$BEFORE_T3") <(printf '%s\n' "$AFTER_T3"))
# Only the wrapper-stdout capture is allowed in - mode.
UNEXPECTED_T3=$(printf '%s\n' "$NEW_T3" | grep -v 'wrapper-stdout3\.log$' || true)
if [[ -z "$UNEXPECTED_T3" ]]; then
  ok "no log file was created in - mode (besides test capture)"
else
  ng "unexpected log file(s) in - mode: $UNEXPECTED_T3"
fi

# ----- Test 4: no detach — killing the stub kills the wrapper ---------------
# A regression guard against future "helpful" edits that try to background
# the long-lived process. The wrapper must still be tied to its child.
echo "[4] no detach: killing the stub terminates the wrapper"
LOG_FILE4="$TMPDIR_TEST/desktop4.log"
( run_with_tee "$LOG_FILE4" bash -c '
    echo "stub-started: t4"
    echo "stub-pid: $$"
    # exec sleep so the stub is a single process — mirrors the gradle/jvm
    # contract where the long-lived process is the immediate child of the
    # pipeline. Forward SIGTERM to a handler so we can report cause of death.
    trap "echo stub-killed; exit 99" TERM INT
    exec sleep 10
  '
  echo "wrapper-exit: t4"
) >"$TMPDIR_TEST/wrapper-stdout4.log" 2>&1 &
WRAPPER_PID4=$!

# Give the stub a moment to start and print its pid.
sleep 0.4
STUB_PID=$(grep -oE 'stub-pid: [0-9]+' "$LOG_FILE4" 2>/dev/null | awk '{print $2}' | head -1)

if [[ -n "$STUB_PID" ]] && kill -0 "$STUB_PID" 2>/dev/null; then
  ok "found stub child pid $STUB_PID under wrapper $WRAPPER_PID4"
  kill -TERM "$STUB_PID" 2>/dev/null || true
  # Give the wrapper up to 3s to react to the closed pipe.
  for _ in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
    sleep 0.2
    if ! kill -0 "$WRAPPER_PID4" 2>/dev/null; then
      break
    fi
  done
  if kill -0 "$WRAPPER_PID4" 2>/dev/null; then
    ng "wrapper still alive after stub was killed (would be a detach regression)"
    kill -KILL "$WRAPPER_PID4" 2>/dev/null || true
    wait "$WRAPPER_PID4" 2>/dev/null || true
  else
    wait "$WRAPPER_PID4" 2>/dev/null || true
    ok "wrapper terminated when stub died (no detach)"
  fi
else
  ng "could not locate stub child pid under wrapper (log: $(head -c 200 "$LOG_FILE4" 2>/dev/null))"
  kill -KILL "$WRAPPER_PID4" 2>/dev/null || true
  wait "$WRAPPER_PID4" 2>/dev/null || true
fi

# ----- Summary ---------------------------------------------------------------
echo
echo "passed: $pass"
echo "failed: $fail"
if (( fail > 0 )); then
  exit 1
fi
exit 0
