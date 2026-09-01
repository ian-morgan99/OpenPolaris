#!/usr/bin/env bash
# protocol-code-audit lint
# ========================
# Regression check for issue #31 followup: forbid claims that a code is
# "VERIFIED" when there is no live-capture evidence in the audit doc.
#
# Background: the 808-816 system codes were once annotated as live-verified
# (commit 7df0d8e), but the read-only hardware smoke in commit b496c32
# showed 814 returned a cellular payload (not time), 815 returned `sw=0`
# (not timezone/language), and 812/813 were never exercised (destructive).
# A future engineer reading the codebase must not be able to mistake these
# destructive/mis-named codes for verified, so this script enforces it.
#
# Rules enforced (any violation fails CI):
#   R1. The 811-816 row in docs/PROTOCOL-CODE-AUDIT-*.md must NOT have
#       a `✓` in the "Semantic?" column.
#   R2. CommandTable.kt must NOT mark SYS_REBOOT/SYS_SHUTDOWN as VERIFIED.
#   R3. If CommandTable.kt contains a `VERIFIED` marker adjacent to a
#       constant in the 808-816 range, the audit doc must list the
#       matching code with a `✓` in "Semantic?". Catches drift between
#       source comments and the audit table.
#
# Exits 0 on success, 1 on any violation.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
AUDIT="$(ls "$ROOT"/docs/PROTOCOL-CODE-AUDIT-*.md 2>/dev/null | head -1 || true)"
COMMAND_TABLE_KT="$ROOT/shared/src/commonMain/kotlin/dev/openpolaris/core/protocol/CommandTable.kt"

if [[ -z "$AUDIT" ]]; then
    echo "FAIL: no docs/PROTOCOL-CODE-AUDIT-*.md file found"
    exit 1
fi

violations=0

# Extract WiFi/system block rows from the audit (lines starting with "| 8NN ").
mapfile -t AUDIT_ROWS < <(grep -E '^\| (799|80[0-9]|81[0-9]) \|' "$AUDIT" || true)

# R1 — 811-816 must NOT have ✓ in the Semantic? column.
for row in "${AUDIT_ROWS[@]}"; do
    code=$(echo "$row" | awk -F'|' '{gsub(/ /, "", $2); print $2}')
    if [[ ! "$code" =~ ^8(11|12|13|14|15|16)$ ]]; then
        continue
    fi
    # The 5th column is the "Semantic?" / "Live?" column. The format
    # changed during the issue-#31 refactor; accept both legacy and new
    # column names. The marker is "✓" anywhere in that column.
    if echo "$row" | grep -qE '\| *(✓) *\|' ; then
        echo "FAIL R1: audit doc row for $code still claims a ✓ in the Semantic? column"
        echo "    row: $row"
        echo "    (destructive / never-exercised / wire-contradicts-name codes must be UNVERIFIED or UNRESOLVED)"
        violations=$((violations + 1))
    fi
done

# R2 — CommandTable.kt must NOT mark SYS_REBOOT/SYS_SHUTDOWN as VERIFIED.
for name in SYS_REBOOT SYS_SHUTDOWN; do
    # match: val SYS_REBOOT = Descriptor<...>( ... )  ... VERIFIED (within 4 lines)
    if grep -E "val $name\\b" "$COMMAND_TABLE_KT" -A 4 | grep -q 'VERIFIED'; then
        echo "FAIL R2: CommandTable.kt marks $name as VERIFIED"
        echo "    (destructive commands must be UNVERIFIED — they were never exercised in the read-only smoke)"
        violations=$((violations + 1))
    fi
done

# R3 — any VERIFIED marker for an 808-816 code in CommandTable.kt must be
# reflected by a ✓ in the audit doc's Semantic? column. The marker must
# be ATTACHED to the constant: either on the same line as `val <NAME> =`
# or in a `/** ... */` KDoc block that ends on the line immediately above
# the val. A VERIFIED comment further away does not attach to this entry.
for name in SYS_VERSION SYS_SERIAL SYS_FW_UPGRADE SYS_FW_PROGRESS SYS_TIME SYS_TIMEZONE SYS_LANGUAGE; do
    line_no=$(grep -n "val $name\\b" "$COMMAND_TABLE_KT" | head -1 | cut -d: -f1 || true)
    if [[ -z "$line_no" ]]; then continue; fi
    # Inspect the val line and the 4 lines immediately above it (covers
    # the /** KDoc block, which is usually 1-3 lines).
    attached=0
    for offset in 0 -1 -2 -3 -4; do
        target=$((line_no + offset))
        text=$(sed -n "${target}p" "$COMMAND_TABLE_KT")
        if [[ -z "$text" ]]; then continue; fi
        if echo "$text" | grep -q 'VERIFIED'; then
            # A VERIFIED marker on a line that itself declares another val
            # doesn't attach to this entry. Skip if the line starts with
            # `val <something> = ... VERIFIED` (i.e. the VERIFIED belongs
            # to that other entry).
            if [[ "$offset" -ne 0 ]] && echo "$text" | grep -qE '^\s*val [A-Z_]+ ='; then
                continue
            fi
            attached=1
            break
        fi
    done
    if [[ "$attached" -eq 1 ]]; then
        case "$name" in
            SYS_VERSION)      code=808 ;;
            SYS_SERIAL)       code=809 ;;
            SYS_FW_UPGRADE)   code=810 ;;
            SYS_FW_PROGRESS)  code=811 ;;
            SYS_TIME)         code=814 ;;
            SYS_TIMEZONE)     code=815 ;;
            SYS_LANGUAGE)     code=816 ;;
        esac
        audit_row=$(printf '%s\n' "${AUDIT_ROWS[@]}" | grep -E "^\| *$code \|" || true)
        if [[ -z "$audit_row" ]]; then
            echo "FAIL R3: CommandTable.kt marks $name as VERIFIED but audit doc has no row for code $code"
            violations=$((violations + 1))
            continue
        fi
        if ! echo "$audit_row" | grep -qE '\| *✓ *\|'; then
            echo "FAIL R3: CommandTable.kt marks $name as VERIFIED but audit doc row for $code does not claim ✓ in Semantic?"
            echo "    row: $audit_row"
            violations=$((violations + 1))
        fi
    fi
done

if [[ "$violations" -gt 0 ]]; then
    echo
    echo "protocol-code-audit lint: $violations violation(s) — see PROTOCOL-CODE-AUDIT-*.md"
    exit 1
fi

echo "protocol-code-audit lint: OK (all 808-816 codes correctly tagged UNVERIFIED/UNRESOLVED)"
exit 0
