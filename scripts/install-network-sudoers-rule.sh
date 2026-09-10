#!/usr/bin/env bash
# Install the sudoers rule that lets the OpenPolaris desktop bridge run its
# `ip rule`/`ip route` policy-route commands without a password prompt each
# time (they need CAP_NET_ADMIN, which the desktop app does not run with).
# Will prompt ONCE for the sudo password to copy+validate the file.
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
HELPER_SRC="$SCRIPT_DIR/openpolaris-network-helper"
HELPER_DST="/usr/local/libexec/openpolaris-network-helper"
RULE_DST="/etc/sudoers.d/openpolaris-network"
TARGET_USER="${SUDO_USER:-$(id -un)}"

if [[ ! -f "$HELPER_SRC" ]]; then
    echo "Helper not found: $HELPER_SRC" >&2
    exit 1
fi

sudo install -d -m 0755 -o root -g root /usr/local/libexec
sudo install -m 0755 -o root -g root "$HELPER_SRC" "$HELPER_DST"
RULE_LINE="$TARGET_USER ALL=(root) NOPASSWD: $HELPER_DST install *, $HELPER_DST remove *"
printf '%s\n' "$RULE_LINE" | sudo tee "$RULE_DST.tmp" >/dev/null
sudo chmod 0440 "$RULE_DST.tmp"
if ! sudo visudo -c -f "$RULE_DST.tmp"; then
    echo "New sudoers file failed validation; not installing." >&2
    sudo rm -f "$RULE_DST.tmp"
    exit 1
fi
sudo mv "$RULE_DST.tmp" "$RULE_DST"

echo "Installed fixed-contract helper and sudoers rule for $TARGET_USER."
