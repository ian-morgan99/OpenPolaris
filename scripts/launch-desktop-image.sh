#!/usr/bin/env bash
# Desktop shortcut entry point: never silently launch a stale app image.
set -euo pipefail
cd "$(dirname "$0")/.."
export SKIKO_RENDER_API="${SKIKO_RENDER_API:-SOFTWARE_FAST}"
export LIBGL_ALWAYS_SOFTWARE="${LIBGL_ALWAYS_SOFTWARE:-1}"

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

exec "$APP_BIN" "$@"
