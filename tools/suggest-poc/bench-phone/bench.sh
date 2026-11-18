#!/usr/bin/env bash
# Phone feasibility benchmark for the tour suggestions (README.md beside this file).
#   bench.sh desktop [case…]     compile, run on this computer's JVM (-Xmx512m)
#   bench.sh pixel   [case…]     compile, dex (d8), run on the Pixel with dalvikvm; pushes one rd5 + profiles to
#                                /data/local/tmp/onpath-bench and deletes them afterwards. Run it through
#                                tools/dev/device.sh pixel -- … ; refuses while OnPath records a tour.
# Cases: hike2 hike4 bike2 bike3 zons-bike3 (Hilden / Zons: E5_N50.rd5), muenchen-bike3 (E10_N45.rd5, desktop only).
# BENCH=net: the app's own reader and searches (app/src/main/java/com/onpath/app/suggest/, NetBench.kt) instead of
#      Bench.java – same cases and counts; needs Gradle's Kotlin compiler (~/.gradle/wrapper/dists). TILE_MB (12) =
#      the reader's raw tile budget, LIVE=1 = peaks of the live heap (a GC before each sample; slower).
# Env: ONPATH_SEGMENTS (~/onpath-routing/segments), PROFILES (~/.cache/onpath-suggest-poc/profiles-8791, made by
#      service/start.sh), XMX (desktop 512m, pixel 256m = the app's usual heap limit), NODE_CACHE_MB (64).
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd); ROOT=$(cd "$HERE/../../.." && pwd)
MODE=${1:?desktop or pixel}; shift
CASES=${*:-hike2 hike4 bike2 bike3 zons-bike3}
SEG=${ONPATH_SEGMENTS:-$HOME/onpath-routing/segments}
PROFILES=${PROFILES:-$HOME/.cache/onpath-suggest-poc/profiles-8791}
OUT=${BENCH_BUILD:-$HOME/.cache/onpath-suggest-poc/bench-phone}
CACHE=${NODE_CACHE_MB:-64}
[ -f "$PROFILES/lookups.dat" ] || python3 "$HERE/../service/make_profiles.py" "$ROOT/app/src/main/assets/brouter" "$PROFILES"
LIBS=$(ls "$ROOT"/app/libs/brouter-*.jar | tr '\n' ':')
rm -rf "$OUT/classes" && mkdir -p "$OUT/classes"
if [ "${BENCH:-}" = net ]; then
  GV=$(sed -n 's#.*/gradle-\([0-9][0-9.]*\)-\(bin\|all\)\.zip.*#\1#p' "$ROOT/gradle/wrapper/gradle-wrapper.properties")
  KCE=$(ls ~/.gradle/wrapper/dists/gradle-"$GV"-*/*/*/lib/kotlin-compiler-embeddable-*.jar | tail -1)
  L=$(dirname "$KCE")
  KCP=$(ls $L/kotlin-compiler-embeddable-*.jar $L/kotlin-stdlib-[0-9]*.jar $L/kotlin-reflect-*.jar $L/kotlin-script-runtime-*.jar $L/kotlinx-coroutines-core-jvm-*.jar $L/annotations-*.jar $L/kotlin-daemon-embeddable-*.jar | tr '\n' ':')
  STD=$(ls $L/kotlin-stdlib-[0-9]*.jar)
  S="$ROOT/core/src/main/kotlin/com/onpath/app/suggest"; [ -f "$S/WayNet.kt" ] || S="$ROOT/app/src/main/java/com/onpath/app/suggest"  # moved to :core
  java -cp "$KCP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 1.8 -cp "$STD:$LIBS" -d "$OUT/classes" \
    "$S/WayNet.kt" "$S/WayKinds.kt" "$S/LongIntMap.kt" "$S/NetReader.kt" "$S/NetSearch.kt" "$HERE/NetBench.kt" 2>&1 | grep -v '^warning\|^WARNING\|^$' || true
  MAIN=NetBenchKt; ARGS="2 ${TILE_MB:-12} $([ -n "${LIVE:-}" ] && echo live || true)"; LIBS="$STD:$LIBS"
else
  javac --release 8 -nowarn -cp "$LIBS" -d "$OUT/classes" "$HERE/Bench.java" 2>&1 | grep -v '^warning\|^Note' || true
  MAIN=Bench; ARGS="2 $CACHE"
fi

if [ "$MODE" = desktop ]; then
  for c in $CASES; do nice -n 19 ionice -c3 java -Xmx"${XMX:-512m}" -cp "$LIBS:$OUT/classes" $MAIN "$SEG" "$PROFILES" "$c" $ARGS; done
  exit 0
fi

[ "$MODE" = pixel ] || { echo "desktop or pixel" >&2; exit 2; }
SDK=${ANDROID_HOME:-$HOME/Android/Sdk}
D8=$(ls -d "$SDK"/build-tools/*/d8 | sort -V | tail -1)
rm -rf "$OUT/dex" && mkdir -p "$OUT/dex"
if [ "$MAIN" = Bench ]; then
  "$D8" --release --min-api 26 --output "$OUT/dex" "$OUT"/classes/Bench*.class "$ROOT"/app/libs/brouter-*.jar
else
  (cd "$OUT/classes" && jar cf ../netbench.jar .)
  "$D8" --release --min-api 26 --output "$OUT/dex" "$OUT/netbench.jar" "$STD" "$ROOT"/app/libs/brouter-*.jar
fi
[ "$(adb devices | grep -c 'device$')" = 1 ] || { echo "no single device on adb" >&2; exit 1; }
if adb shell dumpsys activity services com.onpath.app | grep -q RecordingService; then
  echo "OnPath records a tour – the phone is not touched" >&2; exit 3
fi
T=/data/local/tmp/onpath-bench
t0=$(date +%s)
trap 'adb shell rm -rf $T >/dev/null 2>&1 || true; echo "phone: $(( $(date +%s) - t0 )) s, $T removed"' EXIT
adb shell mkdir -p $T/segments $T/profiles
adb push -q "$OUT/dex/classes.dex" $T/bench.dex
adb push -q "$PROFILES"/lookups.dat "$PROFILES"/hiking-mountain.brf "$PROFILES"/touring.brf $T/profiles/
adb push -q "$SEG/E5_N50.rd5" $T/segments/
echo "device: $(adb shell getprop ro.product.model) · Android $(adb shell getprop ro.build.version.release) · app heap limit $(adb shell getprop dalvik.vm.heapgrowthlimit) (largeHeap $(adb shell getprop dalvik.vm.heapsize))"
for c in $CASES; do
  adb shell "cd $T && dalvikvm -Xmx${XMX:-256m} -cp bench.dex $MAIN segments profiles $c $ARGS" || echo "case $c failed"
done
