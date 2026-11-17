#!/usr/bin/env bash
# Time and memory of the Kotlin tour-suggestion core on the desktop JVM (Measure.kt): per wish three runs in one JVM
# (the last one warm) and the smallest -Xmx of 48 / 64 / 80 / 96 / 128 / 160 / 192 / 256 MB that still runs it.
#   tools/suggest-poc/port/measure.sh [wish …]   (default: hilden-2h mittenwald-gipfel-3h zons-rad-3h nordkirchen-rad-3h)
# Env as parity.sh (SUGGEST_FILES, ONPATH_SEGMENTS, PROFILES).
set -uo pipefail
HERE=$(cd "$(dirname "$0")" && pwd); ROOT=$(cd "$HERE/../../.." && pwd)
WISHES=("$@")
[ ${#WISHES[@]} -gt 0 ] || WISHES=(hilden-2h mittenwald-gipfel-3h zons-rad-3h nordkirchen-rad-3h)
OUT=${OUT:-$HOME/.cache/onpath-suggest-poc/measure}
export OUT ONPATH_ROOT=$ROOT
# compile once (Parity.kt brings DirFiles), then run with growing heaps
XMX=1g "$HERE/kt.sh" MeasureKt "$HERE/Parity.kt" "$HERE/Measure.kt" -- "${WISHES[0]}" 3
for w in "${WISHES[@]}"; do
  for x in 48m 64m 80m 96m 128m 160m 192m 256m; do
    if NOCOMPILE=1 XMX=$x "$HERE/kt.sh" MeasureKt -- "$w" 3 > "$OUT.log" 2>&1; then
      echo "$w: smallest -Xmx $x"; grep "run 3" "$OUT.log"; break
    fi
  done
done
