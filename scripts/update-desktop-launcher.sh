#!/usr/bin/env bash
# Rebuild the jpackage app image that ~/Desktop/OpenPolaris.desktop launches.
#
# The desktop launcher points at a *built* binary:
#   desktopApp/build/compose/binaries/main/app/OpenPolaris/bin/OpenPolaris
# which is only refreshed by `:desktopApp:createDistributable`. If you ship a
# new release (or land fixes on main) and don't run this, the desktop icon
# keeps running the stale build — exactly the trap that hid the #74 preview
# port fix from the K-1 II live test on 2026-09-15.
#
# Run this after every `git pull` / release you intend to test on the desktop:
#   ./scripts/update-desktop-launcher.sh
#
# If the app is currently running, close it first. Pass --force only when it is
# acceptable to terminate that local UI process; it does not affect Polaris.

set -euo pipefail
cd "$(dirname "$0")/.."

APP_IMAGE="desktopApp/build/compose/binaries/main/app/OpenPolaris"
BIN="${APP_IMAGE}/bin/OpenPolaris"

if ! git diff --quiet HEAD --; then
    echo "ERROR: refusing a release-style desktop build from a tracked dirty tree." >&2
    echo "Commit or discard the source changes, then launch the desktop shortcut again." >&2
    exit 1
fi

if [[ "${1:-}" == "--force" ]]; then
    pids=$(pgrep -f "OpenPolaris/bin/OpenPolaris" || true)
    if [[ -n "$pids" ]]; then
        echo "Killing running OpenPolaris (pid $pids)..."
        kill $pids
        sleep 2
    fi
else
    pids=$(pgrep -f "OpenPolaris/bin/OpenPolaris" || true)
    if [[ -n "$pids" ]]; then
        echo "ERROR: OpenPolaris is running (pid $pids); refusing to replace its app image." >&2
        echo "Close it first, or pass --force if terminating the local UI is intended." >&2
        exit 1
    fi
fi

echo "Running desktop regression tests and rebuilding the app image..."
./gradlew :shared:jvmTest :composeApp:jvmTest :desktopApp:createDistributable --rerun-tasks --console=plain
./gradlew :desktopApp:verifyDesktopAppImage --console=plain

echo
echo "Launcher target: $BIN"
ls -la "$BIN"
echo
echo "Done. Relaunch OpenPolaris from the desktop icon to pick up the new build."
