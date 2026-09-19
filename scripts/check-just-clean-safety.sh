#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

tracked_justfiles="$(git ls-files | awk 'tolower($0) == "justfile"')"
tracked_justfile_count="$(printf '%s\n' "$tracked_justfiles" | awk 'NF { count++ } END { print count + 0 }')"
if [[ $tracked_justfile_count -ne 1 || $tracked_justfiles != "Justfile" ]]; then
    echo "Expected exactly one tracked canonical Justfile; found: ${tracked_justfiles:-none}" >&2
    exit 1
fi

if ! grep -Fxq 'clean:' Justfile; then
    echo "Clean recipe missing in Justfile" >&2
    exit 1
fi

clean_recipe="$({
    awk '
        /^clean:/ { in_clean = 1; next }
        in_clean && /^[^[:space:]]/ { exit }
        in_clean { print }
    ' Justfile
})"

if [[ -z ${clean_recipe//[[:space:]]/} ]]; then
    echo "Justfile clean recipe is missing or empty" >&2
    exit 1
fi

clean_forbidden='git[[:space:]]+(checkout|restore|reset|clean)|(^|[[:space:]])(cp|mv)([[:space:]]|$)'
if grep -Eiq "$clean_forbidden" <<<"$clean_recipe"; then
    echo "Unsafe source/configuration mutation found in the clean recipe:" >&2
    grep -Ein "$clean_forbidden" <<<"$clean_recipe" >&2
    exit 1
fi

echo "Justfile clean safety check passed"

