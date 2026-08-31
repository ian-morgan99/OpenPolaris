#!/usr/bin/env bash
# watch-fwpkt-update.sh
#
# Live monitor for a Polaris SD-card FwPkt install attempt.
# Tails /app/Mlog.txt on the gimbal (a regular file, not a symlink) and
# filters for every line related to firmware upgrade decisions.
#
# Usage:
#   ./scripts/watch-fwpkt-update.sh                       # tail until you Ctrl-C
#   ./scripts/watch-fwpkt-update.sh > capture.log         # save full log
#   ./scripts/watch-fwpkt-update.sh --card A             # label this attempt
#
# This script is READ-ONLY on the device. It reads /app/Mlog.txt but
# never touches /app/sd/FwPkt, /dev/mtd*, the bootloader, or any NAND
# writer. Insert your SD card AFTER you see the banner so the timestamps
# are clean.
#
# Survives a device reboot: when the SSH session drops, restart this
# script and tail -F will resume from the new EOF. The matching lines
# written during the install attempt are what we want to catch.
#
# Pair with the diagnostic card matrix at:
#   ian-morgan99/benro-polaris-firmware-patcher#22
#
# Caveat: the gimbal ships BusyBox grep (v1.26.2), which does NOT
# support --line-buffered. The pipe is line-buffered by the host's
# stdbuf / ssh, and the device-side grep just does a fixed-string
# search. This works for our log lines because each is a complete
# record.

set -euo pipefail

GIMBAL="${GIMBAL:-root@192.168.0.1}"
LABEL="${1:-}"

if [[ -n "$LABEL" ]]; then
    echo "=== Card attempt: $LABEL ==="
fi
echo "=== Watching $GIMBAL:/app/Mlog.txt (Ctrl-C to stop) ==="
echo "=== Insert SD card / trigger firmware update NOW ==="
echo

# Patterns we want. Adjust as we learn more from the binary.
# Fixed-string (-F) for speed and BusyBox compatibility.
PATTERN='ExDevFwPkt|FwPkt|crcInfo|firmwareInfo|getFwInfo|getOmsFwInfo|UPGRADE|FwVer|appfs|rootfs|md5|size|remove /app/sd|FwPkt.zip|EXDEV_FW_PATH|GIMBAL_FW_PATH|FwSize'

# Run the tail+grep on the gimbal (BusyBox-friendly), pipe to host stdout.
# We need line-buffered output on the host side, so we do the filter
# on the HOST with grep -E, not on the device. That way the device's
# BusyBox just runs tail -F, which it supports fine.
ssh -o BatchMode=yes -o ServerAliveInterval=5 "$GIMBAL" \
    "tail -n0 -F /app/Mlog.txt 2>/dev/null" \
    | grep -E --line-buffered "$PATTERN"
