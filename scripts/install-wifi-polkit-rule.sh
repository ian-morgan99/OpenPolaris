#!/usr/bin/env bash
# Install the polkit rule that silences repeated WiFi-scan auth prompts.
# Will prompt ONCE for the sudo password; after that, the rule stops the loop.
set -euo pipefail

RULE_SRC="/home/ian/.copilot/session-state/57abdabb-a2e1-4a3e-a7c1-77b48d31c65a/files/50-openpolaris-wifi-scan.rules"
RULE_DST="/etc/polkit-1/rules.d/50-openpolaris-wifi-scan.rules"

if [[ ! -f "$RULE_SRC" ]]; then
    echo "Source rule not found: $RULE_SRC" >&2
    exit 1
fi

if [[ -f "$RULE_DST" ]] && cmp -s "$RULE_SRC" "$RULE_DST"; then
    echo "Rule already installed and up to date: $RULE_DST"
else
    sudo cp "$RULE_SRC" "$RULE_DST"
    sudo chown root:root "$RULE_DST"
    sudo chmod 0644 "$RULE_DST"
    echo "Installed: $RULE_DST"
fi

if systemctl is-active --quiet polkit; then
    sudo systemctl restart polkit
    echo "Restarted polkit to pick up the new rule."
else
    echo "polkit service not active; rule will load on next start."
fi

echo
echo "Verifying:"
ls -la "$RULE_DST"
echo
echo "Confirming the rule is loaded by polkit:"
if command -v pkaction >/dev/null 2>&1; then
    for action in \
        org.freedesktop.NetworkManager.wifi.scan \
        org.freedesktop.NetworkManager.enable-disable-wifi \
        org.freedesktop.NetworkManager.network-control; do
        auth=$(pkaction --action-id "$action" --verbose 2>/dev/null | awk -F': *' '/implicit active:/ {print $2}')
        printf '  %-65s active-implicit=%s\n' "$action" "$auth"
    done
fi
echo
echo "Done. WiFi scans and toggles for user 'ian' should no longer prompt."
