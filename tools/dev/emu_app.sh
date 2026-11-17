#!/usr/bin/env bash
# Shared steps of the emulator tools that run the debug app outside Gradle (crash_hunt.sh, main_thread_measure.sh):
# source it, then call the functions in this order. Never a phone: only emulator-* serials are taken, every adb call
# names the serial (the Pixel hangs on the same USB).
#   emu_lock            the machine's emulator lock (tools/dev/device.sh's), unless device.sh already holds it for us
#   emu_up              $SERIAL / $A = a running emulator, else the headless Rundgang AVD is started (and killed at exit)
#   emu_build           app + test APK with -Ponpath.e2e=true in a Gradle slot (skipped with BUILD=0)
#   emu_boot_install    waits for the boot, animations off, installs both APKs, `pm clear`, pushes the routing data
#   emu_instrument <class> [am instrument args…]   runs one androidTest class, prints failures; its rc
#   emu_tap <text>      taps the first node whose text / content description contains <text> (uiautomator dump); rc 1 = not shown
# Env: SERIAL, AVD (default Rundgang), RD5 (default ~/onpath-routing/segments/E10_N45.rd5), WORK, JAVA_HOME, BUILD.
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
WORK=${WORK:-$HOME/onpath-cells-work}
SDK=${ANDROID_HOME:-$HOME/Android/Sdk}
ADB=${ADB:-$SDK/platform-tools/adb}
export JAVA_HOME=${JAVA_HOME:-$HOME/.local/share/JetBrains/Toolbox/apps/android-studio/jbr}
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_AVD_HOME=${ANDROID_AVD_HOME:-$WORK/avd}
export ANDROID_TMP=${ANDROID_TMP:-$WORK/emu-tmp}
AVD=${AVD:-Rundgang}
PKG=com.onpath.app
EMU_STARTED=""

emu_cleanup() {
  [ -n "$EMU_STARTED" ] && "$ADB" -s "$EMU_STARTED" emu kill >/dev/null 2>&1
  [ "${EMU_LOCKED:-0}" = 1 ] && : > "$WORK/emulator.lock.info" 2>/dev/null
  return 0
}

emu_lock() {
  mkdir -p "$WORK" "$ANDROID_TMP"
  trap emu_cleanup EXIT
  [ "${ONPATH_DEVICE_HELD:-}" = emulator ] && return 0
  command -v flock >/dev/null || return 0
  # the adb server outlives this script: start it before the lock is open, or it inherits the fd and keeps the lock
  "$ADB" start-server >/dev/null 2>&1
  exec 7>>"$WORK/emulator.lock"
  if ! flock -n 7; then
    echo "Waiting for the emulator – $(cat "$WORK/emulator.lock.info" 2>/dev/null || echo "another agent has it")"
    flock 7
  fi
  EMU_LOCKED=1
  echo "$(basename "$0") ($(basename "$ROOT")) since $(date '+%H:%M:%S')" > "$WORK/emulator.lock.info"
}

emu_up() {
  SERIAL=${SERIAL:-$("$ADB" devices | awk '$1 ~ /^emulator-/ && $2 == "device" {print $1; exit}')}
  if [ -z "$SERIAL" ]; then
    [ -d "$ANDROID_AVD_HOME/$AVD.avd" ] || { echo "no AVD $AVD in $ANDROID_AVD_HOME – tools/ci/release_rundgang.sh creates it"; exit 2; }
    SERIAL=emulator-5590
    echo "Starting emulator $AVD ($SERIAL, headless, 4 GB)"
    TMPDIR=$ANDROID_TMP "$SDK/emulator/emulator" -avd "$AVD" -port 5590 -memory 4096 -no-window -no-snapshot -no-boot-anim \
      -gpu swiftshader_indirect -no-audio -camera-back none > "${OUT:-$ANDROID_TMP}/emulator.log" 2>&1 &
    EMU_STARTED=$SERIAL
  fi
  case "$SERIAL" in emulator-*) ;; *) echo "Not an emulator: $SERIAL – never a phone"; exit 2 ;; esac
  A=("$ADB" -s "$SERIAL")
}

emu_build() {
  [ "${BUILD:-1}" = 1 ] || return 0
  echo "Building (Gradle slot, --max-workers=2)…"
  (cd "$ROOT" && WORK="$WORK" tools/dev/gradle_slot.sh -- nice -n 19 ionice -c3 ./gradlew --max-workers=2 -Ponpath.e2e=true -q \
    assembleDebug assembleDebugAndroidTest) > "${OUT:-$ANDROID_TMP}/build.log" 2>&1 || { tail -30 "${OUT:-$ANDROID_TMP}/build.log"; exit 3; }
}

emu_boot_install() {
  echo "Waiting for $SERIAL to boot…"
  "${A[@]}" wait-for-device
  for _ in $(seq 1 180); do [ "$("${A[@]}" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] && break; sleep 2; done
  for s in window_animation_scale transition_animation_scale animator_duration_scale; do "${A[@]}" shell settings put global $s 0 >/dev/null; done
  "${A[@]}" install -r -t "$ROOT/app/build/outputs/apk/debug/app-debug.apk" >/dev/null || { echo "app APK not installed"; exit 3; }
  "${A[@]}" install -r -t "$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk" >/dev/null || { echo "test APK not installed"; exit 3; }
  "${A[@]}" shell pm clear $PKG >/dev/null
  RD5=${RD5:-$HOME/onpath-routing/segments/E10_N45.rd5}
  if [ -f "$RD5" ] && [ "$("${A[@]}" shell ls -l /data/local/tmp/E10_N45.rd5 2>/dev/null | awk '{print $5}')" != "$(stat -c %s "$RD5")" ]; then
    echo "Pushing routing data $(du -h "$RD5" | cut -f1)"
    "${A[@]}" push "$RD5" /data/local/tmp/E10_N45.rd5 >/dev/null && "${A[@]}" shell chmod 644 /data/local/tmp/E10_N45.rd5
  fi
  [ -f "$RD5" ] || echo "WARNING: no routing data ($RD5) – routes are straight lines"
}

emu_instrument() {
  local cls=$1; shift
  "${A[@]}" shell am instrument -w -r "$@" -e class "com.onpath.app.e2e.$cls" $PKG.test/androidx.test.runner.AndroidJUnitRunner \
    > "${OUT:-$ANDROID_TMP}/instrument-$cls.txt" 2>&1
  # "OK" alone is not enough: a class skipped by an assumption (the tool classes without `-e onpathTool 1`) says OK too
  if grep -q "^OK (" "${OUT:-$ANDROID_TMP}/instrument-$cls.txt" &&
    ! grep -q "INSTRUMENTATION_STATUS_CODE: -[34]" "${OUT:-$ANDROID_TMP}/instrument-$cls.txt"; then return 0; fi
  grep -E "INSTRUMENTATION_(RESULT|CODE)|STATUS_CODE: -[34]|stack=|Error|Process crashed|FAILURES" "${OUT:-$ANDROID_TMP}/instrument-$cls.txt" | head -12
  return 1
}

emu_tap() {
  local xy
  "${A[@]}" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  xy=$("${A[@]}" shell cat /sdcard/ui.xml 2>/dev/null | python3 -c '
import sys, re, xml.etree.ElementTree as ET
want = sys.argv[1]
try: root = ET.fromstring(sys.stdin.read())
except Exception: sys.exit()
for n in root.iter("node"):
    if want in (n.get("text") or "") + "|" + (n.get("content-desc") or ""):
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
        print((x1 + x2) // 2, (y1 + y2) // 2); break
' "$1")
  [ -n "$xy" ] || return 1
  # shellcheck disable=SC2086
  "${A[@]}" shell input tap $xy
  sleep "${2:-1.5}"
}
