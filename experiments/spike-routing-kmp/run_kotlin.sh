#!/usr/bin/env bash
# The spike's routes on the Kotlin port: jvm (the same -Xmx192m as the Java reference) and linuxX64, then the comparison.
# Usage: ./run_kotlin.sh <segmentsDir> [reps=3] [jvm|native|both]     (build first, see README.md)
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd); ROOT=$(cd "$HERE/../.." && pwd)
SEG=${1:?segments dir}; REPS=${2:-3}; WHAT=${3:-both}
PROF="$ROOT/app/src/main/assets/brouter"; ROUTES=${ROUTES:-$HERE/routes.txt}
JB=${JAVA_BIN:-$(ls -d ~/.gradle/jdks/*21*/bin 2>/dev/null | head -1)}
mkdir -p "$HERE/out"; rc=0
if [ "$WHAT" != native ]; then
  echo "== Kotlin on the JVM"
  "$JB/java" -Xmx${JAVA_XMX:-192m} -cp "$(cat "$HERE/build/jvmcp.txt")" spike.MainKt "$SEG" "$PROF" "$ROUTES" \
    "$HERE/out/kotlin-jvm-dump.txt" "$HERE/out/kotlin-jvm-times.txt" "$REPS"
  python3 -I "$HERE/compare.py" "$HERE/fixtures/reference.txt.gz" "$HERE/out/kotlin-jvm-dump.txt" || rc=1
fi
if [ "$WHAT" != jvm ]; then
  echo "== Kotlin/Native linuxX64"
  "$HERE/build/bin/linuxX64/releaseExecutable/spike-routing-kmp.kexe" "$SEG" "$PROF" "$ROUTES" \
    "$HERE/out/kotlin-native-dump.txt" "$HERE/out/kotlin-native-times.txt" "$REPS"
  python3 -I "$HERE/compare.py" "$HERE/fixtures/reference.txt.gz" "$HERE/out/kotlin-native-dump.txt" || rc=1
fi
exit $rc
