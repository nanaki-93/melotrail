#!/bin/sh
set -eu

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cache="$root/.build/module-cache"
mkdir -p "$cache"

if rg -n -i '^\s*import\s+.*(midi|app\.melotrail|javax\.sound)|gradle' \
  "$root/Package.swift" "$root/Sources"; then
  echo "Companion boundary check failed: MIDI or root-build dependency found." >&2
  exit 1
fi

SWIFT_MODULECACHE_PATH="$cache" CLANG_MODULE_CACHE_PATH="$cache" \
  swift package --disable-sandbox --package-path "$root" describe --type json | \
  /usr/bin/plutil -extract dependencies json -o - -- - |
  rg -U -q '^\[\s*\]$' || {
    echo "Companion boundary check failed: the Swift package has external dependencies." >&2
    exit 1
  }

echo "companion boundary=PASS (independent Swift package; no MIDI/root-build dependency)"
