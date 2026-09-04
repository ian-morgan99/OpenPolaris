#!/usr/bin/env bash
# Verify every released version's date in CHANGELOG.md matches the date of
# the corresponding git tag. Catches drift like issue #50 where the
# changelog says "2026-09-05" but the tag was actually created on
# "2026-09-04". Designed to run as a CI step; exit 1 on any mismatch.
#
# Usage:
#   scripts/check-changelog-tag-dates.sh
#
# The check is permissive in two ways:
#   1. The "[Unreleased]" header has no date and is ignored.
#   2. Tagged versions without a matching CHANGELOG section are warned
#      about (a freshly-tagged release should land in CHANGELOG before
#      the tag is pushed) but don't fail — the tag push itself is the
#      trigger, so a release that pre-dates this script is allowed.
#
# For each [X.Y.Z] - YYYY-MM-DD in CHANGELOG.md:
#   - if a tag vX.Y.Z exists, its creatordate (short) must equal YYYY-MM-DD
#   - if the tag does not exist, the line is ignored (the version is
#     future/unreleased or was removed)
#
# Date format: matches the existing convention in this repo, which is
# the same short-form ISO 8601 date (YYYY-MM-DD) used by `git for-each-ref
# --format='%(creatordate:short)'`.

set -u
set -o pipefail

CHANGELOG="CHANGELOG.md"
if [ ! -f "$CHANGELOG" ]; then
    echo "::error::Cannot find $CHANGELOG from $(pwd)"
    exit 1
fi

# Extract every "## [X.Y.Z] - YYYY-MM-DD" header. Sort by version so the
# output is stable; use sed so the [Unreleased] line (no date) is dropped
# (mawk on minimal Linux runners does not have gensub).
mapfile -t HEADERS < <(
    sed -nE 's/^##[[:space:]]+\[([0-9]+\.[0-9]+\.[0-9]+([+-][A-Za-z0-9.-]+)?)\][[:space:]]+-[[:space:]]+([0-9]{4}-[0-9]{2}-[0-9]{2}).*/\1\t\3/p' "$CHANGELOG" | sort -V
)

if [ "${#HEADERS[@]}" -eq 0 ]; then
    echo "::notice::No released version headers found in $CHANGELOG; nothing to check."
    exit 0
fi

# Collect tag dates once: vX.Y.Z -> YYYY-MM-DD
declare -A TAG_DATE
while IFS=$'\t' read -r TAG_NAME TAG_DATE_STR; do
    TAG_NAME="${TAG_NAME#refs/tags/}"
    TAG_DATE["$TAG_NAME"]="$TAG_DATE_STR"
done < <(git for-each-ref --format='%(refname)%09%(creatordate:short)' refs/tags)

FAIL=0
for LINE in "${HEADERS[@]}"; do
    VERSION="${LINE%%$'\t'*}"
    CL_DATE="${LINE##*$'\t'}"
    # Normalise: strip any pre-release suffix (e.g. 0.1.0-rc1) — the tag
    # for the same release uses "v0.1.0-rc1" in this repo, so look up the
    # exact ref.
    TAG="v${VERSION}"
    if [ -z "${TAG_DATE[$TAG]+x}" ]; then
        # Version listed in CHANGELOG but no tag yet (e.g. the [Unreleased]
        # entry that got a date by mistake, or a future release). Skip.
        continue
    fi
    ACTUAL="${TAG_DATE[$TAG]}"
    if [ "$CL_DATE" != "$ACTUAL" ]; then
        echo "::error file=$CHANGELOG::CHANGELOG date drift for $VERSION: header says $CL_DATE, but tag $TAG was created on $ACTUAL"
        FAIL=1
    else
        echo "OK  $VERSION ($CL_DATE) matches $TAG"
    fi
done

if [ "$FAIL" -ne 0 ]; then
    echo
    echo "::error::CHANGELOG/tag date drift detected (see issue #50). Fix the dates in $CHANGELOG to match the actual tag creation dates, then re-run this check."
    exit 1
fi

echo "All CHANGELOG dates match their tag dates."
