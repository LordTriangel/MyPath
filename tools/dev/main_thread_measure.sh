#!/usr/bin/env bash
# What a GPS fix costs on the main thread while a tour is recorded (code audit 2026-11-12, point d): runs the
# androidTest class MainThreadMeasure on the emulator – a Demo ride by bike at 1× (one fix a second), Navigate on
# screen, then the app left with Home – and prints its numbers: main-thread time per fix, StrictMode disk reads /
# writes on the main thread, frame times. The app itself is not changed for it. Results so far and how to read them:
# docs/design/recording-main-thread.md.
#   tools/dev/device.sh emulator -- tools/dev/main_thread_measure.sh [seconds on screen (180)] [seconds left (90)]
# Env: BUILD=0 skips the build; OUT (default ~/onpath-cells-work/measure/<date-time>); the rest as tools/dev/emu_app.sh.
# Exit: 0 = measured, 1 = the run failed, 2 = no emulator, 3 = build / install failed. Never a phone.
set -uo pipefail
fg=${1:-180}; bg=${2:-90}
OUT=${OUT:-$HOME/onpath-cells-work/measure/$(date +%Y-%m-%d_%H%M)}; mkdir -p "$OUT"
. "$(dirname "$0")/emu_app.sh"
emu_lock; emu_up; emu_build; emu_boot_install
"${A[@]}" logcat -c
echo "Measuring: $fg s on screen + $bg s left…"
emu_instrument MainThreadMeasure -e measureSeconds "$fg" -e backgroundSeconds "$bg"; rc=$?
"${A[@]}" logcat -d > "$OUT/logcat.txt" 2>&1
"${A[@]}" logcat -d -s OnPathMeasure:I | sed -E 's/^.*OnPathMeasure: //' | grep -v "^-----" | tee "$OUT/measure.txt"
echo "Files: $OUT"
[ $rc = 0 ] && grep -q "^== end" "$OUT/measure.txt"
