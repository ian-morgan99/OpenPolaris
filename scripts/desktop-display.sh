#!/usr/bin/env bash

# Keep Compose Desktop away from the host Wayland compositor when running from
# the GNOME Remote Desktop session. xrdp provides an ordinary X11 session and
# can run the application directly.

openpolaris_configure_rendering() {
    if [[ "${OPENPOLARIS_GPU:-0}" != "1" ]]; then
        export SKIKO_RENDER_API="${SKIKO_RENDER_API:-SOFTWARE_FAST}"
        export LIBGL_ALWAYS_SOFTWARE="${LIBGL_ALWAYS_SOFTWARE:-1}"
        export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dskiko.renderApi=${SKIKO_RENDER_API} -Dprism.order=sw -Dprism.text=t2k -Dsun.java2d.opengl=false -Dsun.java2d.xrender=false"
    fi
}

openpolaris_nested_x_required() {
    local mode="${OPENPOLARIS_NESTED_X:-auto}"

    case "$mode" in
        always)
            return 0
            ;;
        never)
            return 1
            ;;
        auto)
            [[ "${XDG_SESSION_TYPE:-}" == "wayland" || ( -n "${WAYLAND_DISPLAY:-}" && -n "${DISPLAY:-}" ) ]]
            ;;
        *)
            echo "Invalid OPENPOLARIS_NESTED_X=$mode; use auto, always, or never." >&2
            return 2
            ;;
    esac
}

openpolaris_with_display() {
    openpolaris_configure_rendering

    local needs_nested_x
    if openpolaris_nested_x_required; then
        needs_nested_x=1
    else
        local decision=$?
        if [[ "$decision" -eq 2 ]]; then
            return 2
        fi
        needs_nested_x=0
    fi

    if [[ "$needs_nested_x" -eq 0 ]]; then
        "$@"
        return $?
    fi

    if [[ -z "${DISPLAY:-}" ]]; then
        echo "OpenPolaris is in a Wayland session but has no parent DISPLAY for a nested X server." >&2
        echo "Refusing to launch directly because that can take down the desktop compositor." >&2
        return 1
    fi

    local xephyr_command="${OPENPOLARIS_XEPHYR:-Xephyr}"
    if ! command -v "$xephyr_command" >/dev/null 2>&1; then
        echo "OpenPolaris is in a Wayland session but Xephyr is unavailable." >&2
        echo "Install Xephyr, or set OPENPOLARIS_NESTED_X=never only on a known-safe X11 display." >&2
        return 1
    fi

    (
        set -u
        local display_number
        local nested_display
        local socket_path
        local screen="${OPENPOLARIS_NESTED_X_SCREEN:-1280x800x24}"
        local log_file="${OPENPOLARIS_XEPHYR_LOG:-${TMPDIR:-/tmp}/openpolaris-xephyr.log}"
        local xephyr_pid
        local status

        display_number=100
        while [[ "$display_number" -le 199 ]]; do
            socket_path="/tmp/.X11-unix/X${display_number}"
            if [[ ! -e "$socket_path" && ! -e "/tmp/.X${display_number}-lock" ]]; then
                break
            fi
            display_number=$((display_number + 1))
        done

        if [[ "$display_number" -gt 199 ]]; then
            echo "No free nested X display was found in :100 through :199." >&2
            exit 1
        fi

        nested_display=":${display_number}"
        mkdir -p "$(dirname "$log_file")"
        "$xephyr_command" "$nested_display" \
            -screen "$screen" \
            -nolisten tcp \
            -ac >"$log_file" 2>&1 &
        xephyr_pid=$!

        cleanup_nested_x() {
            if kill -0 "$xephyr_pid" 2>/dev/null; then
                kill "$xephyr_pid" 2>/dev/null || true
                wait "$xephyr_pid" 2>/dev/null || true
            fi
        }
        trap cleanup_nested_x EXIT HUP INT TERM

        for _ in {1..50}; do
            if [[ -S "$socket_path" ]]; then
                break
            fi
            if ! kill -0 "$xephyr_pid" 2>/dev/null; then
                echo "Xephyr exited before display $nested_display became ready." >&2
                echo "See $log_file for details." >&2
                exit 1
            fi
            sleep 0.1
        done

        if [[ ! -S "$socket_path" ]]; then
            echo "Xephyr did not create display $nested_display within 5 seconds." >&2
            echo "See $log_file for details." >&2
            exit 1
        fi

        export DISPLAY="$nested_display"
        unset WAYLAND_DISPLAY
        export XDG_SESSION_TYPE=x11

        "$@"
        status=$?
        exit "$status"
    )
}
