#!/bin/sh
set -eu

# Independent, unsigned local installation. Never replace an existing installation.
tabi_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
tabi_destination=${1:-"$HOME/Applications/Melotrail TABI"}
if [ "$#" -gt 1 ]; then
  echo "Usage: sh companion/scripts/install.sh [new absolute installation directory]" >&2
  exit 1
fi
case "$tabi_destination" in
  /*) ;;
  *) echo "Installation requires an absolute directory." >&2; exit 1 ;;
esac
if [ -e "$tabi_destination" ] || [ -L "$tabi_destination" ]; then
  echo "Installation destination already exists; choose a new directory." >&2
  exit 1
fi
tabi_cache="$tabi_root/.build/module-cache"
mkdir -p "$tabi_cache"
SWIFT_MODULECACHE_PATH="$tabi_cache" CLANG_MODULE_CACHE_PATH="$tabi_cache" \
  swift build --disable-sandbox --package-path "$tabi_root" -c release --product melotrail-tabi-editor
tabi_bin=$(SWIFT_MODULECACHE_PATH="$tabi_cache" CLANG_MODULE_CACHE_PATH="$tabi_cache" \
  swift build --disable-sandbox --package-path "$tabi_root" -c release --show-bin-path)
test "$("$tabi_bin/melotrail-tabi-editor" --capabilities)" = "melotrail-tabi-export-handoff-v1-manifest-v2"
mkdir -p "$(dirname -- "$tabi_destination")"
mkdir "$tabi_destination"
cp -p "$tabi_bin/melotrail-tabi-editor" "$tabi_destination/melotrail-tabi-editor"
printf 'Installed TABI: %s\n' "$tabi_destination/melotrail-tabi-editor"
