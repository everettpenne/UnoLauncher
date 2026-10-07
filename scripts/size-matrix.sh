#!/usr/bin/env bash
# Screenshot Home and the feed page at several window sizes and text scales on ONE emulator or
# device, to check layouts without owning a foldable. Uses `wm size`/`wm density`/font_scale
# overrides, and always restores them on exit (including Ctrl-C).
#
#   ANDROID_SERIAL=emulator-5554 scripts/size-matrix.sh [output-dir]
#
# ANDROID_SERIAL is required so a second attached device is never touched by accident.
# This approximates screen size only: it does not simulate hinge posture or a different cutout.
set -euo pipefail

if [[ -z "${ANDROID_SERIAL:-}" ]]; then
    echo "Set ANDROID_SERIAL to the single device to test (see: adb devices)." >&2
    exit 1
fi
adb_bin=${ADB:-adb}
adbx() { "$adb_bin" -s "$ANDROID_SERIAL" "$@"; }
out=${1:-size-matrix-out}
mkdir -p "$out"

restore() {
    adbx shell wm size reset >/dev/null 2>&1 || true
    adbx shell wm density reset >/dev/null 2>&1 || true
    adbx shell settings put system font_scale 1.0 >/dev/null 2>&1 || true
}
trap restore EXIT

# name width height density font-scale
configs=(
    "phone-pixel-10a 1080 2424 420 1.0"
    "phone-fold-cover 1080 2092 420 1.0"
    "phone-small 720 1280 320 1.0"
    "phone-large-text 1080 2424 420 1.5"
    "fold-inner 2208 1840 380 1.0"
    "tablet-portrait 1600 2560 320 1.0"
)

for config in "${configs[@]}"; do
    read -r name width height density font <<<"$config"
    echo "== $name (${width}x${height} @ ${density}dpi, text ${font}x)"
    adbx shell wm size "${width}x${height}" >/dev/null
    adbx shell wm density "$density" >/dev/null
    adbx shell settings put system font_scale "$font"
    sleep 3
    adbx shell am force-stop com.jake.duolauncher
    adbx shell input keyevent KEYCODE_HOME
    sleep 25   # a cold launcher start on an emulator can take this long
    adbx exec-out screencap -p >"$out/$name-home.png"
    adbx shell input swipe $((width * 10 / 100)) $((height * 60 / 100)) \
        $((width * 70 / 100)) $((height * 60 / 100)) 300
    sleep 8
    adbx exec-out screencap -p >"$out/$name-feed.png"
done
echo "Screenshots in $out/"
