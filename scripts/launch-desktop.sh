#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
export DISPLAY="${DISPLAY:-:0}"
source scripts/desktop-display.sh

openpolaris_with_display ./gradlew :desktopApp:run --console=plain
