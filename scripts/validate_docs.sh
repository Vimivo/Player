#!/usr/bin/env bash
set -euo pipefail

# Vimivo — Documentation Link & Integrity Validator
# Validates that all relative file links in markdown documents exist on disk.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

cd "${PROJECT_ROOT}"

echo "================================================================="
echo "  Vimivo — Documentation Relative Link Validator"
echo "================================================================="

MISSING_COUNT=0
CHECKED_COUNT=0

while IFS= read -r doc; do
    DIR="$(dirname "$doc")"
    # Extract links formatted like [text](target)
    # Using python to parse markdown links safely
    LINKS=$(python3 -c "
import re, sys

with open('$doc', 'r', encoding='utf-8', errors='ignore') as f:
    content = f.read()

# Match standard markdown links [label](path)
matches = re.findall(r'\[([^\]]*)\]\(([^)]+)\)', content)
for label, target in matches:
    # Filter out external schemes and anchors
    if target.startswith(('http://', 'https://', 'mailto:', '#', 'file://')):
        continue
    # Strip any anchor inside link: target#section -> target
    base = target.split('#')[0].strip()
    if base:
        print(base)
" 2>/dev/null || true)

    for link in $LINKS; do
        CHECKED_COUNT=$((CHECKED_COUNT + 1))
        # Handle links starting with / as relative to project root
        if [[ "$link" == /* ]]; then
            TARGET_PATH="${PROJECT_ROOT}${link}"
        else
            TARGET_PATH="${DIR}/${link}"
        fi

        if [[ ! -e "$TARGET_PATH" ]]; then
            echo "BROKEN LINK: In $doc -> $link (Resolved to: $TARGET_PATH)"
            MISSING_COUNT=$((MISSING_COUNT + 1))
        fi
    done
done < <(find . -type f -name "*.md" ! -path "*/build/*" ! -path "*/.gradle/*" ! -path "*/.git/*")

echo "Checked $CHECKED_COUNT relative links across markdown documents."

if [[ $MISSING_COUNT -gt 0 ]]; then
    echo "FAILED: Found $MISSING_COUNT broken relative documentation links."
    exit 1
else
    echo "SUCCESS: 100% of relative documentation links exist!"
fi
