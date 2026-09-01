#!/usr/bin/env bash
# Launch the OpenPolaris desktop UI on the local JVM.
#
# The `:composeApp:run` task already does exactly this — it forks the JVM
# against the built jvmJar with the correct jvmRuntimeClasspath attached
# (skiko-awt-runtime-linux-x64, compose-runtime, etc.) and the
# `dev.openpolaris.ui.MainKt` Main-Class. This script is a thin wrapper so
# the user doesn't have to remember the task name.
#
# Usage:
#   ./scripts/run-desktop.sh         # boots the Compose window
set -euo pipefail
cd "$(dirname "$0")/.."
exec ./gradlew :composeApp:run "$@"
