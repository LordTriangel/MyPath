#!/usr/bin/env bash
# Compile the app's tour-suggestion core (app/src/main/java/com/onpath/app/suggest/, without the app's routing glue
# RouterProfile.kt) plus the given extra Kotlin files with Gradle's own Kotlin compiler, then run MAIN on the plain JVM.
#   tools/suggest-poc/port/kt.sh <MainKt> <extra.kt …> -- <args…>
# Env: OUT (class dir, default ~/.cache/onpath-suggest-poc/port), XMX (default 1g), SUGGEST_LANG (de). No Gradle, no
# Android: the core is pure Kotlin + BRouter's jars; debug.Problems comes from ProblemsStub.kt here, the texts from
# strings_stub.py.
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
# the suggest files live in :core (core/src/main/kotlin) once moved there, the rest in :app (tools/dev/core_move.py)
S="$ROOT/app/src/main/java/com/onpath/app/suggest"; SC="$ROOT/core/src/main/kotlin/com/onpath/app/suggest"
SRC=$(ls "$S"/*.kt "$SC"/*.kt 2>/dev/null | grep -v RouterProfile.kt)
if [ -z "${NOCOMPILE:-}" ]; then  # NOCOMPILE=1: run the classes compiled before
rm -rf "$OUT" && mkdir -p "$OUT"
# the core's words come from the :strings module: a stand-in with the texts of strings_suggest.xml (German by default,
# the POC's language – SUGGEST_LANG=en for the English cards)
GEN="$OUT.gen" && rm -rf "$GEN" && mkdir -p "$GEN"
python3 "$HERE/strings_stub.py" "$ROOT" "$GEN/StringsStub.kt" "${SUGGEST_LANG:-de}" > /dev/null
# shellcheck disable=SC2086
nice -n 10 java -cp "$KCP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 1.8 \
  -cp "$STD:$LIBS" -d "$OUT" $SRC "$HERE/ProblemsStub.kt" "$GEN/StringsStub.kt" "$GEN/StringsStubRes.kt" "${EXTRA[@]}" 2>&1 \
  | grep -v '^warning\|^WARNING\|^$' || true
fi
exec nice -n 10 ionice -c3 java -Xmx"${XMX:-1g}" -cp "$STD:$LIBS:$OUT" "$MAIN" "$@"
