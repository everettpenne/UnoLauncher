#!/bin/sh
# Runs emulator tests from a known state: Uno set as the emulator's Home app (so "go Home" stays in Uno instead of bouncing out to
# the stock launcher and back, which is slow and leaves tests in different states), app data cleared (several tests assume a fresh
# install; leftover data from earlier runs breaks them), and the previous Home app put back at the end.
#
# Usage: scripts/emulator-tests.sh [test class or Class#method ...]     (default: the suites named in docs/polish-checklist.md's run list)
# Only ever run this against an emulator: it changes the device's Home app and wipes the app's data.
set -eu
cd "$(dirname "$0")/.."
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
PKG=com.jake.duolauncher
case "$("$ADB" shell getprop ro.kernel.qemu 2>/dev/null | tr -d '\r')$("$ADB" shell getprop ro.boot.qemu 2>/dev/null | tr -d '\r')" in
  *1*) ;;
  *) echo "Refusing: the connected device is not an emulator." >&2; exit 2 ;;
esac
previous=$("$ADB" shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN | tail -1 | tr -d '\r')
restore() { [ -n "$previous" ] && [ "$previous" != "$PKG/.MainActivity" ] && "$ADB" shell cmd package set-home-activity "$previous" >/dev/null 2>&1 || true; }
trap restore EXIT
./scripts/gradle.sh installDebug installDebugAndroidTest >/dev/null
"$ADB" shell pm clear "$PKG" >/dev/null
# A cleared app would show the first-run sheet over the screens the tests drive (a returning user has finished it), so mark it finished.
"$ADB" shell "run-as $PKG sh -c 'mkdir -p shared_prefs && echo \"<map><boolean name=\\\"finished\\\" value=\\\"true\\\" /></map>\" > shared_prefs/setup_experience.xml'"
"$ADB" shell cmd package set-home-activity "$PKG/$PKG.MainActivity" >/dev/null
if [ "$#" -eq 0 ]; then
  set -- CustomizationNavigationIntegrationTest AppearanceBackgroundIntegrationTest NativeAppearanceLocationIntegrationTest \
    LayoutBackupIntegrationTest FolderIntegrationTest LargeFolderIntegrationTest GlassMenusInstrumentedTest \
    UnoWidgetLibraryIntegrationTest SetupSheetInteractionIntegrationTest PlaceEntryInstrumentedTest
fi
classes=""
for t in "$@"; do classes="${classes:+$classes,}$PKG.$t"; done
"$ADB" shell am instrument -w -e class "$classes" "$PKG.test/androidx.test.runner.AndroidJUnitRunner"
