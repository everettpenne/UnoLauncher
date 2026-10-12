#!/bin/sh
# Creates the widget lane's own working copy next to this one, so two people (or agents) never share a checkout.
# Usage: scripts/widget-worktree.sh [directory]    (default: ../uno-widgets, on branch widgets/work)
set -eu
cd "$(dirname "$0")/.."
dir="${1:-../uno-widgets}"
git fetch -q origin
if [ -e "$dir" ]; then echo "$dir already exists" >&2; exit 1; fi
git worktree add -q -B widgets/work "$dir" origin/main
[ -f local.properties ] && cp local.properties "$dir/"
git -C "$dir" config core.hooksPath scripts/git-hooks
echo "Widget working copy ready: $dir (branch widgets/work, from origin/main)."
echo "Work there; run scripts/widget-check.sh; open pull requests from it. Update with: git fetch && git rebase origin/main"
