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
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ "${OPENPOLARIS_GPU:-0}" != "1" ]]; then
  export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dskiko.renderApi=SOFTWARE_FAST -Dprism.order=sw -Dprism.text=t2k"
fi

exec ./gradlew :composeApp:run "$@"
