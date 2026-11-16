#!/usr/bin/env bash
# Compile the app's tour-suggestion core (app/src/main/java/com/onpath/app/suggest/, without the app's routing glue
# RouterProfile.kt) plus the given extra Kotlin files with Gradle's own Kotlin compiler, then run MAIN on the plain JVM.
#   tools/suggest-poc/port/kt.sh <MainKt> <extra.kt …> -- <args…>
# Env: OUT (class dir, default ~/.cache/onpath-suggest-poc/port), XMX (default 1g). No Gradle, no Android: the core
# is pure Kotlin + BRouter's jars; debug.Problems comes from tools/routing/jvm-test/ProblemsStub.kt.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd); ROOT=$(cd "$HERE/../../.." && pwd)
MAIN=${1:?main class}; shift
EXTRA=()
while [ $# -gt 0 ] && [ "$1" != "--" ]; do EXTRA+=("$1"); shift; done
[ "${1:-}" = "--" ] && shift
OUT=${OUT:-$HOME/.cache/onpath-suggest-poc/port}
GV=$(sed -n 's#.*/gradle-\([0-9][0-9.]*\)-\(bin\|all\)\.zip.*#\1#p' "$ROOT/gradle/wrapper/gradle-wrapper.properties")
KCE=$(ls ~/.gradle/wrapper/dists/gradle-"$GV"-*/*/*/lib/kotlin-compiler-embeddable-*.jar | tail -1)
L=$(dirname "$KCE")
KCP=$(ls $L/kotlin-compiler-embeddable-*.jar $L/kotlin-stdlib-[0-9]*.jar $L/kotlin-reflect-*.jar $L/kotlin-script-runtime-*.jar $L/kotlinx-coroutines-core-jvm-*.jar $L/annotations-*.jar $L/kotlin-daemon-embeddable-*.jar | tr '\n' ':')
STD=$(ls $L/kotlin-stdlib-[0-9]*.jar)
LIBS=$(ls "$ROOT"/app/libs/brouter-*.jar | tr '\n' ':')
S="$ROOT/app/src/main/java/com/onpath/app/suggest"
SRC=$(ls "$S"/*.kt | grep -v RouterProfile.kt)
rm -rf "$OUT" && mkdir -p "$OUT"
# shellcheck disable=SC2086
nice -n 10 java -cp "$KCP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 1.8 \
  -cp "$STD:$LIBS" -d "$OUT" $SRC "$HERE/ProblemsStub.kt" "${EXTRA[@]}" 2>&1 \
  | grep -v '^warning\|^WARNING\|^$' || true
exec nice -n 10 ionice -c3 java -Xmx"${XMX:-1g}" -cp "$STD:$LIBS:$OUT" "$MAIN" "$@"
