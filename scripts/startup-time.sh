#!/bin/sh
# Measures cold-start time of the installed Uno build on an emulator or a spare phone, under three ahead-of-time compilation levels:
#   verify         no precompiled code (what an install with no profile gets until Android's idle compile runs)
#   speed-profile  what the baseline profile (app/src/main/baseline-prof.txt) buys: only the profiled code precompiled
#   speed          everything precompiled: the upper bound, to show how much headroom a better profile could have
# Each level is timed with `am start -W` over several force-stopped launches; the median and the spread are printed.
#
# Install a RELEASE build first (the profile is only packaged into release, and debug is not minified), for example:
#   DUO_RELEASE_STORE_FILE=... DUO_RELEASE_STORE_PASSWORD=... DUO_RELEASE_KEY_ALIAS=... DUO_RELEASE_KEY_PASSWORD=... ./gradlew :app:assembleRelease
#   adb install -r app/build/outputs/apk/release/app-release.apk
# Usage: scripts/startup-time.sh [runs per level, default 10]
# Numbers from an emulator are for comparing levels with each other; they are not what a phone will show.
set -eu
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
PKG=com.jake.duolauncher
RUNS="${1:-10}"

"$ADB" shell pm path "$PKG" >/dev/null 2>&1 || { echo "Uno is not installed on the connected device." >&2; exit 2; }
# A returning user: skip the first-run sheet, which would otherwise be part of the first launch.
"$ADB" shell "run-as $PKG sh -c 'mkdir -p shared_prefs && echo \"<map><boolean name=\\\"finished\\\" value=\\\"true\\\" /></map>\" > shared_prefs/setup_experience.xml'" >/dev/null 2>&1 || true

median() { sort -n | awk '{a[NR]=$1} END {print (NR%2) ? a[(NR+1)/2] : (a[NR/2]+a[NR/2+1])/2}'; }

measure() {
  label="$1"; times=""; slow=""
  # One throwaway launch first, so the first measured run is not the one that pays for loading libraries into the page cache.
  "$ADB" shell am force-stop "$PKG"; sleep 1
  "$ADB" shell am start -W -n "$PKG/.MainActivity" >/dev/null 2>&1; sleep 3
  i=0
  while [ "$i" -lt "$RUNS" ]; do
    "$ADB" shell am force-stop "$PKG"; sleep 2
    out=$("$ADB" shell am start -W -n "$PKG/.MainActivity" 2>/dev/null | tr -d '\r')
    t=$(echo "$out" | awk '/TotalTime/ {print $2}')
    if [ -n "$t" ]; then times="$times $t"
    else
      # `am start -W` gives up after about ten seconds without a time: the launch was at least that slow. Count it, never drop it.
      w=$(echo "$out" | awk '/WaitTime/ {print $2}'); slow="$slow ${w:-0}"
    fi
    sleep 2
    i=$((i + 1))
  done
  ok=$(echo $times | wc -w | tr -d ' '); late=$(echo $slow | wc -w | tr -d ' ')
  if [ "$ok" -gt 0 ]; then
    sorted=$(echo $times | tr ' ' '\n' | sort -n)
    printf '%-14s median %6s ms   min %6s   max %6s' "$label" "$(echo "$sorted" | median)" "$(echo "$sorted" | head -1)" "$(echo "$sorted" | tail -1)"
  else printf '%-14s no launch finished in time' "$label"; fi
  if [ "$late" -gt 0 ]; then printf '   | %s of %s launches timed out (took at least %s ms)' "$late" "$RUNS" "$(echo $slow | tr ' ' '\n' | sort -n | head -1)"; fi
  printf '\n'
}

# Hand the packaged profile to ART the way a sideloaded install does at first launch, then compile at each level.
"$ADB" shell am start -W -n "$PKG/.MainActivity" >/dev/null 2>&1; sleep 4
"$ADB" shell am broadcast -a androidx.profileinstaller.action.INSTALL_PROFILE -n "$PKG/androidx.profileinstaller.ProfileInstallReceiver" >/dev/null 2>&1 || true
sleep 2

echo "Cold start of $PKG, $RUNS runs per level:"
"$ADB" shell cmd package compile -f -m verify "$PKG" >/dev/null; measure "verify"
"$ADB" shell cmd package compile -f -m speed-profile "$PKG" >/dev/null; measure "speed-profile"
"$ADB" shell cmd package compile -f -m speed "$PKG" >/dev/null; measure "speed"
