#!/bin/bash
set -euo pipefail
MELOTRAIL_VALIDATION_RUN=$(cd "$(dirname "$0")/.." && pwd -P)
MELOTRAIL_VALIDATION_REPO=$(cd "$MELOTRAIL_VALIDATION_RUN/../../../../../.." && pwd -P)
cd "$MELOTRAIL_VALIDATION_REPO"
export JAVA_HOME=/Users/marcoandreose/.sdkman/candidates/java/21.0.11-tem
unset MELOTRAIL_RUN_LIVE_E2E MELOTRAIL_RESUME_LIVE_E2E
MELOTRAIL_VALIDATION_TEMP=$(mktemp -d "${TMPDIR:-/tmp}/melotrail-vg2-packet-checks.XXXXXX")
trap 'rm -f -- "$MELOTRAIL_VALIDATION_TEMP/headless.gradle"; rmdir -- "$MELOTRAIL_VALIDATION_TEMP"' EXIT
cat > "$MELOTRAIL_VALIDATION_TEMP/headless.gradle" <<'GRADLE'
allprojects {
  tasks.withType(org.gradle.api.tasks.testing.Test).configureEach {
    systemProperty "java.awt.headless", "true"
    exclude "**/MidiCoreNativeResponsivenessTest*"
  }
}
GRADLE
record() {
  local name="$1"
  shift
  "$@" > "$MELOTRAIL_VALIDATION_RUN/checks/$name.log" 2>&1
}
case "${1:-}" in
  --baseline)
    record dry-run ./gradlew -I "$MELOTRAIL_VALIDATION_TEMP/headless.gradle" test build --dry-run
    record baseline-focused ./gradlew -I "$MELOTRAIL_VALIDATION_TEMP/headless.gradle" :test --tests '*TargetArchitectureRulesTest' --tests '*DocumentationIntegrityTest'
    ;;
  --final)
    record dry-run ./gradlew -I "$MELOTRAIL_VALIDATION_TEMP/headless.gradle" test build --dry-run
    record focused-final ./gradlew -I "$MELOTRAIL_VALIDATION_TEMP/headless.gradle" :test --tests '*TargetArchitectureRulesTest' --tests '*DocumentationIntegrityTest' --tests '*VideoAnimationAssetsTest' --tests '*VideoMotionDescriptorFixtureTest'
    record node-final env MELOTRAIL_REPO_ROOT="$MELOTRAIL_VALIDATION_REPO" MELOTRAIL_MOTION_FIXTURE_ROOT="$MELOTRAIL_VALIDATION_REPO/build/video-motion-fixtures" /opt/homebrew/bin/node --test tools/video-motion/render.test.cjs tools/video-motion/scenery.test.cjs tools/video-motion/vg2-parts.test.cjs tools/video-motion/vg2-static-parts.test.cjs tools/video-motion/vg2-contour-repair.test.cjs tools/video-motion/vg2-wrist-attachment.test.cjs tools/video-motion/vg2-rig-support.test.cjs tools/video-motion/vg2-rig-motion.test.cjs tools/video-motion/vg2-colour-proof.test.cjs tools/video-motion/vg2-transfer-metadata.test.cjs tools/video-motion/vg2-png-transport.test.cjs tools/video-motion/vg2-fixed-artwork.test.cjs tools/video-motion/vg2-support-margin.test.cjs docs/pictures/video/evidence/VG2-13/continuation-20261001-153813Z/preparation.test.cjs
    record python-final env PYTHONDONTWRITEBYTECODE=1 /usr/bin/python3 "$MELOTRAIL_VALIDATION_RUN/scripts/check-supervision.py"
    record make-test make "GRADLE=./gradlew -I $MELOTRAIL_VALIDATION_TEMP/headless.gradle" test
    record make-build make "GRADLE=./gradlew -I $MELOTRAIL_VALIDATION_TEMP/headless.gradle" build
    record diff-check git diff --check
    ;;
  *) echo 'Use --baseline or --final' >&2; exit 2;;
esac
