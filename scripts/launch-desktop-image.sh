#!/usr/bin/env bash
# Desktop shortcut entry point: never silently launch a stale app image.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/desktop-display.sh

APP_BIN="desktopApp/build/compose/binaries/main/app/OpenPolaris/bin/OpenPolaris"
if ! ./gradlew :desktopApp:verifyDesktopAppImage --console=plain; then
    if pgrep -f 'OpenPolaris/bin/OpenPolaris' >/dev/null 2>&1; then
        echo "OpenPolaris source changed, but an older instance is still running." >&2
        echo "Close that window, then launch the desktop shortcut again to run tests and rebuild." >&2
        exit 1
    fi
    echo "Desktop package is stale; running regression tests and rebuilding before launch."
    ./scripts/update-desktop-launcher.sh
    ./gradlew :desktopApp:verifyDesktopAppImage --console=plain
fi

openpolaris_with_display "$APP_BIN" "$@"
