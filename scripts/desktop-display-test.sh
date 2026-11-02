#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/desktop-display.sh"

assert_contains() {
    local haystack="$1"
    local needle="$2"
    [[ "$haystack" == *"$needle"* ]] || {
        echo "Expected output to contain: $needle" >&2
        echo "$haystack" >&2
        exit 1
    }
}

direct_output="$({
    export DISPLAY=:3391
    export XDG_SESSION_TYPE=x11
    unset WAYLAND_DISPLAY
    OPENPOLARIS_NESTED_X=auto openpolaris_with_display env
} 2>&1)"
assert_contains "$direct_output" "DISPLAY=:3391"
assert_contains "$direct_output" "SKIKO_RENDER_API=SOFTWARE_FAST"
assert_contains "$direct_output" "LIBGL_ALWAYS_SOFTWARE=1"
assert_contains "$direct_output" "XDG_SESSION_TYPE=x11"

set +e
wayland_output="$({
    export DISPLAY=:3389
    export XDG_SESSION_TYPE=wayland
    export PATH="$(mktemp -d)"
    OPENPOLARIS_NESTED_X=auto openpolaris_with_display true
} 2>&1)"
wayland_status=$?
set -e
[[ "$wayland_status" -ne 0 ]] || {
    echo "Wayland launch unexpectedly bypassed the nested-X safety gate." >&2
    exit 1
}
assert_contains "$wayland_output" "Xephyr is unavailable"

set +e
invalid_output="$(OPENPOLARIS_NESTED_X=invalid openpolaris_with_display true 2>&1)"
invalid_status=$?
set -e
[[ "$invalid_status" -eq 2 ]] || {
    echo "Invalid nested-X mode did not fail with status 2." >&2
    exit 1
}
assert_contains "$invalid_output" "use auto, always, or never"

echo "desktop-display tests passed"
