#!/bin/sh
set -eu

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cache="$root/.build/module-cache"
mkdir -p "$cache"
SWIFT_MODULECACHE_PATH="$cache" CLANG_MODULE_CACHE_PATH="$cache" \
  swift run --disable-sandbox --package-path "$root" melotrail-tabi-regression

# The real CLI must reject a path, even one below our own disposable build tree.
rejected="$root/.build/rejected-output-$$"
if rejection=$(SWIFT_MODULECACHE_PATH="$cache" CLANG_MODULE_CACHE_PATH="$cache" \
  swift run --disable-sandbox --package-path "$root" melotrail-tabi-spike "$rejected" 2>&1); then
  echo "regression=FAIL: the CLI accepted an output path" >&2
  exit 1
fi
printf '%s\n' "$rejection" | rg -q '^This owned-media spike accepts no paths or input assets\.$'
test ! -e "$rejected"
echo "path-confinement=PASS"
