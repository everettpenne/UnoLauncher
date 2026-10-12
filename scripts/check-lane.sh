#!/bin/sh
# Fails if this branch changes files outside a lane. Usage: scripts/check-lane.sh widgets [base-ref]
# The base defaults to origin/main; only what the branch changed since it forked is checked.
set -eu
cd "$(dirname "$0")/.."
lane="${1:-}"; base="${2:-origin/main}"
M='app/src/main/java/com/jake/duolauncher'
case "$lane" in
  widgets)
    allowed="^($M/widgets/|app/src/test/java/com/jake/duolauncher/[A-Za-z]*(Widget|Photo|WorldClock)[A-Za-z]*\.kt$|app/src/androidTest/java/com/jake/duolauncher/[A-Za-z]*Widget[A-Za-z]*\.kt$|docs/widget-development\.md$)" ;;
  *) echo "usage: $0 widgets [base-ref]" >&2; exit 2 ;;
esac
changed=$({ git diff --name-only "$(git merge-base "$base" HEAD)" HEAD; git diff --name-only; git ls-files --others --exclude-standard; } | sort -u)
bad=$(printf '%s\n' "$changed" | grep -v '^$' | grep -Ev "$allowed" || true)
if [ -n "$bad" ]; then
  echo "Outside the $lane lane (leave these to the owner, or ask):" >&2
  printf '  %s\n' $bad >&2
  exit 1
fi
echo "OK: every change is inside the $lane lane."
