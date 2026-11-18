#!/usr/bin/env bash
# Timing of the three engines, interleaved so that machine load hits all alike: ROUNDS rounds of (Java, Kotlin on JVM,
# Kotlin/Native), 2 runs per route each; the table takes the fastest run per route and engine. Peak memory = VmHWM of the process.
# Usage: ./bench.sh <segmentsDir> [rounds=3]   → out/bench.md
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd); ROOT=$(cd "$HERE/../.." && pwd); cd "$HERE"
SEG=${1:?segments dir}; ROUNDS=${2:-3}
PROF="$ROOT/app/src/main/assets/brouter"; JB=${JAVA_BIN:-$(ls -d ~/.gradle/jdks/*21*/bin | head -1)}
CP=$(ls "$ROOT"/app/libs/brouter-*.jar | tr '\n' ':'); NAT=${NATIVE_EXE:-build/bin/linuxX64/releaseExecutable/spike-routing-kmp.kexe}
mkdir -p out/bench; rm -f out/bench/*.txt
for r in $(seq 1 "$ROUNDS"); do
  "$JB/java" -Xmx192m -cp "${CP}out/refclasses" btools.router.RefMain "$SEG" "$PROF" routes.txt out/bench/d.txt out/bench/java-$r.txt 2
  "$JB/java" -Xmx192m -cp "$(cat build/jvmcp.txt)" spike.MainKt "$SEG" "$PROF" routes.txt out/bench/d.txt out/bench/kjvm-$r.txt 2 > /dev/null
  "$NAT" "$SEG" "$PROF" routes.txt out/bench/d.txt out/bench/native-$r.txt 2 > /dev/null
done
python3 -I - <<'PY' > out/bench.md
import glob, re
def load(prefix):
    best, mem = {}, 0
    for f in glob.glob(f'out/bench/{prefix}-*.txt'):
        for line in open(f):
            p = line.split()
            if p[0] == 'VmHWM:': mem = max(mem, int(p[1]))
            elif p[0] not in ('total_ms', 'VmRSS:'): best[p[0]] = min([best.get(p[0], 10**9)] + [int(x) for x in p[1:]])
    return best, mem
(j, jm), (k, km), (n, nm) = load('java'), load('kjvm'), load('native')
print('| Route | Java engine | Kotlin on JVM | Kotlin/Native linuxX64 | Native ÷ Java |'); print('| --- | ---: | ---: | ---: | ---: |')
for name in j: print(f'| {name} | {j[name]} ms | {k[name]} ms | {n[name]} ms | {n[name] / max(j[name], 1):.1f} |')
sj, sk, sn = sum(j.values()), sum(k.values()), sum(n.values())
print(f'| **all routes** | {sj} ms | {sk} ms | {sn} ms | {sn / sj:.2f} |')
print(f'| peak memory of the process | {jm // 1024} MB | {km // 1024} MB | {nm // 1024} MB | |')
PY
cat out/bench.md
