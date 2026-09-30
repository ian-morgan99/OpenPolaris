#!/usr/bin/env bash
# Install desktop shortcuts that always verify/build the current source image.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd -P)"
TEMPLATE="$ROOT/packaging/linux/OpenPolaris.desktop.in"
LAUNCHER="$ROOT/scripts/launch-desktop-image.sh"
ICON="$ROOT/docs/screenshots/openpolaris-desktop-v1.0.0.png"

for path in "$TEMPLATE" "$LAUNCHER" "$ICON"; do
    [[ -f "$path" ]] || { echo "Missing required launcher asset: $path" >&2; exit 1; }
done

mkdir -p "$HOME/.local/share/applications"
DESTS=("$HOME/.local/share/applications/OpenPolaris.desktop")
if [[ -d "$HOME/Desktop" ]]; then
    DESTS+=("$HOME/Desktop/OpenPolaris.desktop")
fi

for dest in "${DESTS[@]}"; do
    escaped_root=${ROOT//\\/\\\\}
    escaped_root=${escaped_root//&/\\&}
    sed -e "s|@OPENPOLARIS_LAUNCHER@|$escaped_root/scripts/launch-desktop-image.sh|g" \
        -e "s|@OPENPOLARIS_ICON@|$escaped_root/docs/screenshots/openpolaris-desktop-v1.0.0.png|g" \
        "$TEMPLATE" > "$dest"
    chmod 644 "$dest"
    echo "Installed $dest"
done
