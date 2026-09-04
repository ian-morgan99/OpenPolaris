#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
export DISPLAY="${DISPLAY:-:0}"
export SKIKO_RENDER_API=SOFTWARE_FAST
export LIBGL_ALWAYS_SOFTWARE=1

exec ./gradlew :desktopApp:run --console=plain
