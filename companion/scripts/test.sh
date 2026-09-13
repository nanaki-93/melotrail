#!/bin/sh
set -eu

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cache="$root/.build/module-cache"
mkdir -p "$cache"
evidence=$(mktemp -d "$root/.build/v05b-editor-evidence.XXXXXX")
fixture="$evidence/fixture"
valid="$evidence/valid"
invalid="$evidence/invalid"
mkdir -p "$fixture" "$valid" "$invalid"
MELOTRAIL_TABI_EDITOR_FIXTURE_DIR="$fixture" \
  SWIFT_MODULECACHE_PATH="$cache" CLANG_MODULE_CACHE_PATH="$cache" \
  swift run --disable-sandbox --package-path "$root" melotrail-tabi-regression

# V05b delivery evidence must come from the release executable, rather than
# only constructing its library controller inside the regression process.
SWIFT_MODULECACHE_PATH="$cache" CLANG_MODULE_CACHE_PATH="$cache" \
  swift build --disable-sandbox --package-path "$root" -c release --product melotrail-tabi-editor
release_bin=$(SWIFT_MODULECACHE_PATH="$cache" CLANG_MODULE_CACHE_PATH="$cache" \
  swift build --disable-sandbox --package-path "$root" -c release --show-bin-path)
editor="$release_bin/melotrail-tabi-editor"

run_editor() {
  output_dir=$1
  shift
  MELOTRAIL_TABI_EDITOR_EVIDENCE_DIR="$output_dir" "$editor" "$@" &
  editor_pid=$!
  (
    remaining=20
    while [ "$remaining" -gt 0 ]; do
      if ! kill -0 "$editor_pid" 2>/dev/null; then
        exit 0
      fi
      sleep 1
      remaining=$((remaining - 1))
    done
    kill "$editor_pid" 2>/dev/null || true
  ) &
  watchdog_pid=$!
  if wait "$editor_pid"; then
    editor_status=0
  else
    editor_status=$?
  fi
  kill "$watchdog_pid" 2>/dev/null || true
  wait "$watchdog_pid" 2>/dev/null || true
  if [ "$editor_status" -ne 0 ]; then
    echo "release-editor-evidence=FAIL: editor exited with status $editor_status" >&2
    exit 1
  fi
}

run_editor "$valid" "$fixture/composition-request.json"
run_editor "$invalid" "$fixture/does-not-exist.json"

test "$(plutil -extract releaseExecutableLaunched raw -o - "$valid/editor-observations.json")" = "true"
test "$(plutil -extract executablePath raw -o - "$valid/editor-observations.json")" = "$editor"
test "$(plutil -extract compositionRequestPath raw -o - "$valid/editor-observations.json")" = "$fixture/composition-request.json"
selected_frame=$(plutil -extract selectedSceneStartFrame raw -o - "$valid/editor-observations.json")
boundary_frame=$(plutil -extract playedAcrossBoundaryFrame raw -o - "$valid/editor-observations.json")
test "$boundary_frame" -gt "$selected_frame"
keyboard_frame=$(plutil -extract keyboardRightFrame raw -o - "$valid/editor-observations.json")
test "$keyboard_frame" -gt "$selected_frame"
final_frame=$(plutil -extract finalPreviewFrame raw -o - "$valid/editor-observations.json")
final_tail=$(plutil -extract finalAudioTailEndFrame raw -o - "$valid/editor-observations.json")
test "$final_frame" -eq $((final_tail - 1))
test "$(plutil -extract soundtrackPlayerCountBeforeClose raw -o - "$valid/editor-observations.json")" = "1"
test "$(plutil -extract soundtrackPlayerCountAfterClose raw -o - "$valid/editor-observations.json")" = "0"
test "$(plutil -extract frameObserverAndPlayerReleasedOnClose raw -o - "$valid/editor-observations.json")" = "true"
test "$(plutil -extract inputErrorVisible raw -o - "$invalid/input-error-observations.json")" = "true"
test "$(sips -g pixelWidth "$valid/editor-window.png" | awk '/pixelWidth/ { print $2 }')" -gt 700
test "$(sips -g pixelHeight "$valid/editor-window.png" | awk '/pixelHeight/ { print $2 }')" -gt 500
for layout_fixture in 1536x1024 1280x900 720x900; do
  test -s "$valid/editor-$layout_fixture.png"
done
test "$(plutil -extract 'layoutCaptures.2.fixture' raw -o - "$valid/editor-observations.json")" = "720x900"
test "$(plutil -extract 'layoutCaptures.2.compactStackedLayout' raw -o - "$valid/editor-observations.json")" = "true"
test "$(sips -g pixelWidth "$invalid/input-error-window.png" | awk '/pixelWidth/ { print $2 }')" -gt 400
test "$(wc -c < "$valid/editor-window.png")" -gt 1000
echo "release-editor-evidence=PASS executable=$editor"
echo "release-editor-owned-request=$fixture/composition-request.json"
echo "release-editor-capture=$valid/editor-window.png"
echo "release-editor-observations=$valid/editor-observations.json"
echo "release-editor-input-error-capture=$invalid/input-error-window.png"
echo "release-editor-input-error-observations=$invalid/input-error-observations.json"

# V07a checks the separate installed executable's protocol and real intake caller.
installed="$evidence/installed TABI 日本語"
sh "$root/scripts/install.sh" "$installed"
editor="$installed/melotrail-tabi-editor"
test "$("$editor" --capabilities)" = "melotrail-tabi-export-handoff-v1-manifest-v2"
installed_digest=$(shasum -a 256 "$editor")
if sh "$root/scripts/install.sh" "$installed"; then
  echo "regression=FAIL: companion installation overwrote an existing directory" >&2
  exit 1
fi
test "$(shasum -a 256 "$editor")" = "$installed_digest"
handoff="$evidence/handoff"
stale_handoff="$evidence/stale-handoff"
mkdir "$handoff" "$stale_handoff"
manifest_digest=$(shasum -a 256 "$fixture/manifest.json" | awk '{print $1}')
run_editor "$handoff" --midi-export "$fixture/manifest.json" "$manifest_digest" owned-timing-snapshot
run_editor "$stale_handoff" --midi-export "$fixture/manifest.json" "$manifest_digest" wrong-snapshot
test "$(plutil -extract executablePath raw -o - "$handoff/handoff-observations.json")" = "$editor"
test "$(plutil -extract snapshotId raw -o - "$handoff/handoff-observations.json")" = "owned-timing-snapshot"
test "$(plutil -extract manifestSHA256 raw -o - "$handoff/handoff-observations.json")" = "$manifest_digest"
test "$(plutil -extract inputErrorVisible raw -o - "$stale_handoff/input-error-observations.json")" = "true"
test -s "$handoff/handoff-window.png"
test -s "$evidence/export-handoff.png"
test "$(shasum -a 256 "$fixture/manifest.json" | awk '{print $1}')" = "$manifest_digest"
echo "release-handoff-evidence=PASS executable=$editor"
echo "release-handoff-capture=$handoff/handoff-window.png"

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
