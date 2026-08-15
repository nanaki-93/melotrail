#!/usr/bin/env bash
set -euo pipefail

repository_root=$(git rev-parse --show-toplevel)
cd "$repository_root"

legacy_files=$(git ls-files | rg '^(src/main/resources/static/|tools/frontend_server\.py$)' || true)
if [[ -n "$legacy_files" ]]; then
    printf '%s\n%s\n' 'Legacy browser files are tracked:' "$legacy_files" >&2
    exit 1
fi

legacy_references=$(git grep -n -I -E \
    '(frontend_server\.py|make[[:space:]]+frontend|(:|port[[:space:]]+)3000|src/main/resources/static|static/index\.html|ClassPathResource\("static/|addResourceHandler\("/\*\*"\)|classpath:/web/)' \
    -- README.md Makefile docs src desktopApp worker tools 2>/dev/null \
    | rg -v '^tools/check_no_legacy_frontend\.sh:' || true)
if [[ -n "$legacy_references" ]]; then
    printf '%s\n%s\n' 'Legacy browser references remain in active source or user documentation:' "$legacy_references" >&2
    exit 1
fi

printf '%s\n' 'Legacy frontend guard passed.'
