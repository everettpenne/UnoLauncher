#!/bin/sh
# The fast development loop: compile and run the JVM unit tests only (a few seconds). No lint, no release build, no emulator.
# Run the deeper set (scripts/emulator-tests.sh, lint, the release build) before a release, not after every edit.
# Usage: scripts/quick-check.sh [unit test filter ...]     e.g. scripts/quick-check.sh '*Spotlight*'
set -eu
cd "$(dirname "$0")/.."
filters=""
for f in "$@"; do filters="$filters --tests $f"; done
# shellcheck disable=SC2086
./gradlew -q :app:testDebugUnitTest $filters
echo "Quick check passed (unit tests only; run the release checks before shipping)."
