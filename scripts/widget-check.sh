#!/bin/sh
# The widget lane's fast loop: lane check, then widget unit tests, then a debug build. Usage: scripts/widget-check.sh [--full]
# --full adds lint and the whole unit-test suite (do this before opening a pull request).
set -eu
cd "$(dirname "$0")/.."
scripts/check-lane.sh widgets
if [ "${1:-}" = "--full" ]; then
  exec scripts/gradle.sh :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
fi
exec scripts/gradle.sh :app:assembleDebug :app:testDebugUnitTest \
  --tests '*Widget*' --tests '*PhotoGallery*' --tests '*WorldClock*'
