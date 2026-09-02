#!/usr/bin/env bash
# Launch the OpenPolaris desktop UI on the local JVM.
#
# The `:composeApp:run` task already does exactly this — it forks the JVM
# against the built jvmJar with the correct jvmRuntimeClasspath attached
# (skiko-awt-runtime-linux-x64, compose-runtime, etc.) and the
# `dev.openpolaris.ui.MainKt` Main-Class. This script is a thin wrapper so
# the user doesn't have to remember the task name.
#
# On headless / software-only X servers (e.g. a forwarded :10.0 with no
# real GL driver) skiko fails with `Cannot create Linux GL context` and
# the app never paints. Forcing SOFTWARE_FAST rendering, the sw prism
# pipeline, and the t2k text rasteriser makes the app boot in software.
# Set OPENPOLARIS_GPU=1 to opt back into the GPU path.
#
# Usage:
#   ./scripts/run-desktop.sh         # boots the Compose window
#   OPENPOLARIS_GPU=1 ./scripts/run-desktop.sh   # use real GL if available
#   OPENPOLARIS_LOG=path.log ./scripts/run-desktop.sh  # tee gradle output
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ "${OPENPOLARIS_GPU:-0}" != "1" ]]; then
  export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dskiko.renderApi=SOFTWARE_FAST -Dprism.order=sw -Dprism.text=t2k"
fi

# By default the wrapper logs the Compose JVM's stdout to /tmp/openpolaris-desktop.log
# (in addition to the calling terminal, if any) and stays attached for the full
# lifetime of the JVM — `:composeApp:run` is a foreground task and never exits
# on its own, so the wrapper will only return when the Compose window closes
# (or the JVM is killed). This is intentional: a user who runs the script in
# a terminal wants the JVM to follow their session's lifecycle.
#
# If you need the wrapper to return while the JVM keeps running, launch it
# from your own backgrounding context (e.g. `nohup`, `setsid`, systemd, or
# a terminal multiplexer). The `tee` here is purely for log redirection, not
# lifecycle management.
#
# Override the log target with OPENPOLARIS_LOG=path or OPENPOLARIS_LOG=- to
# stream directly to the terminal (the latter still keeps the wrapper attached
# to the JVM).
LOG_TARGET="${OPENPOLARIS_LOG:-/tmp/openpolaris-desktop.log}"

run_with_tee() {
  # Pipes the given long-lived command's output to a tee'd log file, keeping
  # the wrapper attached for the full lifetime of the command. Sourced
  # separately by scripts/run-desktop-test.sh so the contract can be
  # regression-tested with a stub command.
  local log_target="$1"; shift
  if [[ "$log_target" == "-" ]]; then
    exec "$@"
  else
    ( "$@" 2>&1 ) | tee "$log_target"
  fi
}

if [[ "$LOG_TARGET" == "-" ]]; then
  exec ./gradlew :composeApp:run "$@"
else
  echo "Logging desktop UI startup to: $LOG_TARGET (wrapper stays attached until JVM exits)" >&2
  run_with_tee "$LOG_TARGET" ./gradlew :composeApp:run "$@"
fi
