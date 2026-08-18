#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly -a allowed=(
  "README.md"
  "plan/plan.md"
  "plan/tasks/063-full-melotrail-rename.md"
  "desktopApp/src/main/kotlin/app/melotrail/desktop/DesktopSupport.kt"
  "desktopApp/src/test/kotlin/app/melotrail/desktop/DesktopPreferencesMigrationTest.kt"
  "tools/check_no_legacy_product_name.sh"
)

is_allowed() {
  local candidate="$1"
  local permitted
  for permitted in "${allowed[@]}"; do
    [[ "$candidate" == "$permitted" ]] && return 0
  done
  return 1
}

status=0
while IFS= read -r match; do
  relative="${match#"$repository_root"/}"
  if ! is_allowed "$relative"; then
    echo "Former product identifier outside explicit migration evidence: $relative" >&2
    status=1
  fi
done < <(
  rg -l -i \
    --glob '!**/.git/**' \
    --glob '!**/.gradle/**' \
    --glob '!**/.kotlin/**' \
    --glob '!**/build/**' \
    --glob '!projects/**' \
    'ai[ -]?music[ -]?workstation|personal ai music arranger|ai[./]music[./]workstation|personal-ai-music-arranger' \
    "$repository_root" || true
)

exit "$status"
