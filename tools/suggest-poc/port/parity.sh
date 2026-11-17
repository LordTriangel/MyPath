#!/usr/bin/env bash
# Parity of the Kotlin tour-suggestion core with the frozen POC (docs/design/tour-suggestions.md §7, Parity.kt).
#   tools/suggest-poc/port/parity.sh [graph|reader] [name words…]
# graph (default): the network from the POC's route service (started if it is not answering) – the method alone, must
#                  give the reference pools exactly. reader: the app's NetReader on the rd5 files.
# Env: SUGGEST_REFERENCE (~/onpath-suggest-poc/reference), SUGGEST_FILES (~/onpath-suggest-poc/suggest-file/cells-c2:
#      content 2, with AIMS – the files the reference runs read, SUGGEST_FILE=1),
#      SUGGEST_SERVICE (http://127.0.0.1:8791), ONPATH_SEGMENTS, PROFILES, PARITY_VERBOSE=1 (every differing loop, phase times),
#      PARITY_REPEAT=n (times of the n-th run: the JIT warm, as in an app that suggests more than once), PARITY_HEAP=1 (the
#      heap peak per wish), XMX (java -Xmx, default 1g).
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd); ROOT=$(cd "$HERE/../../.." && pwd)
SERVICE=${SUGGEST_SERVICE:-http://127.0.0.1:8791}
if [ "${1:-graph}" = graph ] && ! curl -s -m 2 "$SERVICE/ping" >/dev/null; then
  seg=${ONPATH_SEGMENTS:-$HOME/onpath-routing/segments}
  mkdir -p "$HOME/onpath-suggest-poc"
  setsid "$ROOT/tools/suggest-poc/service/start.sh" "$seg" "${SERVICE##*:}" >>"$HOME/onpath-suggest-poc/service.log" 2>&1 &
  for _ in $(seq 60); do sleep 0.5; curl -s -m 1 "$SERVICE/ping" >/dev/null && break; done
fi
ONPATH_ROOT=$ROOT exec "$HERE/kt.sh" ParityKt "$HERE/Parity.kt" -- "$@"
