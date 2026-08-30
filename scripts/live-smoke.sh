#!/usr/bin/env bash
# Live smoke test for the OpenPolaris gimbal.
#
# Goal: connect to the gimbal's TCP control plane (192.168.0.1:9090),
# fire the post-connect pre-camera burst, and capture the raw responses
# so we can tune the fromFrame() parsers against real wire format.
#
# This is the "Step 3" deliverable in docs/PLANNING-2026-08.md.
#
# Usage:
#   scripts/live-smoke.sh                # full pre-camera burst (9 codes)
#   scripts/live-smoke.sh 524 802 808    # custom code list
#   scripts/live-smoke.sh --check        # only verify reachability, don't burst
#
# Network gotcha: from the tplink AP, 192.168.0.1 is the TP-Link
# admin page (GoAhead-Webs), NOT the gimbal. You must be associated
# with the polaris_* AP for the gimbal to be reachable.
#
# Requirements:
#   - Polkit rule installed (see scripts/install-wifi-polkit-rule.sh)
#     so nmcli does not trigger auth prompts
#   - Gimbal powered on
#   - cli-probe built (./gradlew :tools:cli-probe:installDist)
#
# Output: appends to /tmp/openpolaris-live-smoke.log (rotates if >1MB)

set -euo pipefail

PROJ_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLI_PROBE="$PROJ_ROOT/tools/cli-probe/build/install/cli-probe/bin/cli-probe"
GIMBAL_HOST="192.168.0.1"
GIMBAL_PORT="9090"
LOG="/tmp/openpolaris-live-smoke.log"
MAX_LOG_BYTES=$((1024 * 1024))

# Default pre-camera burst (matches CommandTable.BURST_PRE_CAMERA order):
# 808 firmware, 809 serial, 802 wifi-band, 778/779 battery, 775 sd,
# 824 oms, 524 ex-axis, 543 settling-get.
DEFAULT_CODES=(808 809 802 778 779 775 824 524 543)

log() { printf '%s\n' "$*" | tee -a "$LOG"; }

rotate_log_if_needed() {
    if [[ -f "$LOG" ]] && (( $(stat -c %s "$LOG" 2>/dev/null || echo 0) > MAX_LOG_BYTES )); then
        mv "$LOG" "$LOG.old"
        log "rotated log (was >1MB) -> $LOG.old"
    fi
}

check_cli_probe_built() {
    if [[ ! -x "$CLI_PROBE" ]]; then
        log "ERROR: cli-probe not built at $CLI_PROBE"
        log "Run: ./gradlew :tools:cli-probe:installDist"
        exit 2
    fi
}

# We cannot probe 192.168.0.1 from the tplink subnet (it's a router
# there, not the gimbal), so the only reliable check is whether we
# are associated with the polaris AP.
check_on_polaris_ap() {
    local cur_ssid
    cur_ssid="$(nmcli -t -f ACTIVE,SSID device wifi 2>/dev/null | awk -F: '$1=="yes" {print $2; exit}')"
    if [[ -z "$cur_ssid" ]]; then
        if nmcli -t -f NAME connection show --active 2>/dev/null | grep -q "^polaris_"; then
            log "(on polaris AP via nmcli; nmcli device wifi list was empty)"
            return 0
        fi
        log "WARN: no active WiFi association. Likely on tplink."
        log "      192.168.0.1 from tplink is the TP-Link admin page, NOT the gimbal."
        log "      To reach the gimbal, run: nmcli connection up polaris_d13e86"
        return 1
    fi
    if [[ "$cur_ssid" == polaris_* ]]; then
        log "OK: associated with polaris AP '$cur_ssid'"
        return 0
    fi
    log "WARN: associated with '$cur_ssid', not a polaris_* AP."
    log "      Run: nmcli connection up polaris_d13e86"
    return 1
}

check_gimbal_reachable() {
    if timeout 3 bash -c "exec 3<>/dev/tcp/$GIMBAL_HOST/$GIMBAL_PORT" 2>/dev/null; then
        exec 3<&-
        exec 3>&-
        log "OK: $GIMBAL_HOST:$GIMBAL_PORT is reachable"
        return 0
    fi
    log "WARN: $GIMBAL_HOST:$GIMBAL_PORT not reachable from this network."
    return 1
}

do_burst() {
    local -a codes
    if (( $# == 0 )); then
        codes=("${DEFAULT_CODES[@]}")
    else
        codes=("$@")
    fi
    log "===== burst start $(date -u +%FT%TZ) codes=${codes[*]} ====="
    local code_args=""
    for c in "${codes[@]}"; do
        code_args+="$c,"
    done
    code_args="${code_args%,}"
    if "$CLI_PROBE" "$GIMBAL_HOST" "$GIMBAL_PORT" "$code_args" 2>&1 | tee -a "$LOG"; then
        log "===== burst end (clean) ====="
    else
        log "===== burst end (probe exit nonzero; see above) ====="
    fi
}

usage() {
    cat <<EOF
Usage: $(basename "$0") [--check] [code ...]
  --check      Only verify gimbal reachability; do not fire the burst.
  code ...     Custom code list (space-separated).

Default burst (no args): ${DEFAULT_CODES[*]}

Output is appended to $LOG.
EOF
}

main() {
    rotate_log_if_needed
    log "----- live-smoke.sh invoked $(date -u +%FT%TZ) pid=$$ -----"
    check_cli_probe_built

    if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
        usage
        exit 0
    fi

    local on_ap=0 reachable=0
    if check_on_polaris_ap; then on_ap=1; fi
    echo
    if check_gimbal_reachable; then reachable=1; fi
    echo

    if [[ "${1:-}" == "--check" ]]; then
        if (( on_ap && reachable )); then
            log "check: PASS (on AP, gimbal TCP open)"
            exit 0
        fi
        log "check: FAIL - see warnings above"
        exit 1
    fi

    if (( ! on_ap )); then
        log "Aborting: not on polaris AP. Use --check to just probe reachability."
        exit 3
    fi
    if (( ! reachable )); then
        log "Aborting: gimbal TCP not reachable. Is it powered?"
        exit 4
    fi

    if (( $# == 0 )); then
        do_burst
    else
        do_burst "$@"
    fi
}

main "$@"
