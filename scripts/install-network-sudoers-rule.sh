#!/usr/bin/env bash
# Install the sudoers rule that lets the OpenPolaris desktop bridge run its
# `ip rule`/`ip route` policy-route commands without a password prompt each
# time (they need CAP_NET_ADMIN, which the desktop app does not run with).
# Will prompt ONCE for the sudo password to copy+validate the file.
set -euo pipefail

RULE_SRC="/home/ian/Documents/VSCodeProjects/OpenPolaris/scripts/sudoers/openpolaris-network"
RULE_DST="/etc/sudoers.d/openpolaris-network"

if [[ ! -f "$RULE_SRC" ]]; then
    echo "Source rule not found: $RULE_SRC" >&2
    exit 1
fi

if [[ -f "$RULE_DST" ]] && cmp -s "$RULE_SRC" "$RULE_DST"; then
    echo "Rule already installed and up to date: $RULE_DST"
else
    sudo install -m 0440 -o root -g root "$RULE_SRC" "$RULE_DST.tmp"
    if ! sudo visudo -c -f "$RULE_DST.tmp"; then
        echo "New sudoers file failed validation; not installing." >&2
        sudo rm -f "$RULE_DST.tmp"
        exit 1
    fi
    sudo mv "$RULE_DST.tmp" "$RULE_DST"
    echo "Installed: $RULE_DST"
fi

echo
echo "Verifying:"
sudo -l -U ian | grep -F "ip rule" || true
echo
echo "Done. The Wi-Fi bridge's policy-route commands for user 'ian' should no"
echo "longer prompt for a password."
