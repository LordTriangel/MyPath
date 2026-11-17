#!/usr/bin/env bash
# Crash hunt on the emulator (owner's safety net 2026-11-12, cleanup piece 1b): the real debug app with a Demo tour
# running (Developer tools → "Demo: ride the planned route", Let's go – set up through the UI like a user), then
# `monkey` restricted to the app throws random taps / holds / swipes / back presses at it for <minutes>. Afterwards
# everything a crash leaves behind: the crash log buffer, ANRs, dropbox entries and the app's own problem log
# (ERROR / CRASH). Repeatable: the same seed gives the same events (round r uses seed + r).
#   tools/dev/device.sh emulator -- tools/dev/crash_hunt.sh <seed> <minutes> [more monkey args…]
#   e.g. … crash_hunt.sh 1117 10          … crash_hunt.sh 2001 5 --pct-rotation 8 --pct-pinchzoom 10
# Steps: emulator (a running one, else the headless Rundgang AVD, killed afterwards) → build app + test APK
# (BUILD=0 skips) → install, `pm clear`, CrashHuntSetup (tiny map package, real routing, a plan in Meran, developer
# tools unlocked; SETUP=0 keeps the installed app as it is) → Demo tour → rounds of ROUND_EVENTS (default 1500, ~1 min)
# monkey events until the minutes are over; a round that finds the tour gone (ended by a tap, process died) says so and
# starts it again when the plan screen is there.
# Exit: 0 = nothing found, 1 = crash / ANR / problem-log ERROR found (see the summary), 2 = usage / no emulator,
# 3 = build / install / setup failed. Output: $OUT (default ~/onpath-cells-work/hunt/runs/seed-<seed>).
# Never a phone: only emulator-* serials (tools/dev/emu_app.sh).
set -uo pipefail
seed=${1:?usage: crash_hunt.sh <seed> <minutes> [more monkey args…]}; minutes=${2:?minutes}; shift 2
OUT=${OUT:-$HOME/onpath-cells-work/hunt/runs/seed-$seed}; mkdir -p "$OUT"
. "$(dirname "$0")/emu_app.sh"
ROUND_EVENTS=${ROUND_EVENTS:-1500}

recording() { "${A[@]}" shell dumpsys activity services $PKG 2>/dev/null | grep -q RecordingService; }

# The Demo tour through the UI: ☰ → Settings → Diagnostics → Developer tools → Demo ride → back to the plan → Let's go.
demo_tour() {
  "${A[@]}" shell am start -n $PKG/.MainActivity >/dev/null 2>&1; sleep 4
  emu_tap Menu && emu_tap Settings && emu_tap Diagnostics && emu_tap "Developer tools" || return 1
  local i
  for i in 1 2 3 4; do
    emu_tap "Demo: ride the planned route" && break
    "${A[@]}" shell input swipe 540 1900 540 900 400; sleep 1.5
    [ "$i" = 4 ] && return 1
  done
  "${A[@]}" shell input keyevent BACK; sleep 1; "${A[@]}" shell input keyevent BACK; sleep 2
  emu_tap "Let’s go" 4 || return 1
  emu_tap Later 3 # the phone-setup hint, when shown
  for i in 1 2 3 4 5; do recording && return 0; sleep 2; done
  return 1
}

emu_lock; emu_up
[ "${SETUP:-1}" = 1 ] && emu_build
"${A[@]}" wait-for-device
if [ "${SETUP:-1}" = 1 ]; then
  emu_boot_install
  emu_instrument CrashHuntSetup -e onpathTool 1 || { echo "crash_hunt: setup failed ($OUT/instrument-CrashHuntSetup.txt)"; exit 3; }
fi
"${A[@]}" shell svc power stayon true
"${A[@]}" shell cmd statusbar disable-for-setup true >/dev/null 2>&1 # no notification shade / quick settings under the monkey
restore() {
  "${A[@]}" shell cmd statusbar disable-for-setup false >/dev/null 2>&1
  "${A[@]}" shell settings put system user_rotation 0 >/dev/null 2>&1
  "${A[@]}" shell svc power stayon false >/dev/null 2>&1
  emu_cleanup
}
trap restore EXIT
if demo_tour; then echo "Demo tour is running (recording)"; else echo "WARNING: Demo tour not started – the hunt begins without a tour"; fi

"${A[@]}" logcat -c; "${A[@]}" logcat -b crash -c
devstart=$(date -d @"$("${A[@]}" shell date +%s | tr -d '\r')" '+%Y-%m-%d %H:%M:%S')
pid0=$("${A[@]}" shell pidof $PKG | tr -d '\r')
echo "== seed $seed, $minutes min, rounds of $ROUND_EVENTS events, extra: $* (device time $devstart, pid $pid0)"
end=$(( $(date +%s) + minutes * 60 )); round=0; with_tour=0; restarts=0; injected=0
: > "$OUT/monkey.txt"; : > "$OUT/rounds.txt"
while [ "$(date +%s)" -lt "$end" ]; do
  tour=no
  if recording; then tour=yes; with_tour=$((with_tour + 1))
  elif demo_tour; then tour=restarted; with_tour=$((with_tour + 1)); restarts=$((restarts + 1)); fi
  "${A[@]}" shell monkey -p $PKG --pct-syskeys 0 --throttle 150 --ignore-crashes --ignore-timeouts \
    --ignore-security-exceptions -s $((seed + round)) -v "$@" "$ROUND_EVENTS" > "$OUT/monkey-round.txt" 2>&1
  cat "$OUT/monkey-round.txt" >> "$OUT/monkey.txt"
  n=$(grep -oE "Events injected: [0-9]+" "$OUT/monkey-round.txt" | grep -oE "[0-9]+" | tail -1); injected=$((injected + ${n:-0}))
  pid=$("${A[@]}" shell pidof $PKG | tr -d '\r')
  echo "round $round seed $((seed + round)): tour $tour, events ${n:-?}, pid ${pid:-none}$([ "${pid:-}" != "$pid0" ] && echo " (PROCESS CHANGED)")" | tee -a "$OUT/rounds.txt"
  pid0=${pid:-$pid0}; round=$((round + 1))
done
rm -f "$OUT/monkey-round.txt"

"${A[@]}" logcat -b crash -d > "$OUT/crash.txt" 2>&1
"${A[@]}" logcat -d > "$OUT/logcat.txt" 2>&1
"${A[@]}" shell dumpsys dropbox --print data_app_crash data_app_anr data_app_native_crash > "$OUT/dropbox.txt" 2>&1
"${A[@]}" shell run-as $PKG cat files/problems.json > "$OUT/problems.json" 2>/dev/null
alive=$("${A[@]}" shell pidof $PKG | tr -d '\r'); rec=no; recording && rec=yes
echo
echo "== Crash hunt: seed $seed, $minutes min, $round rounds ($with_tour with a tour, $restarts tour restarts), $injected events"
python3 - "$OUT" "$devstart" <<'PY'
import collections, json, re, sys
out, start = sys.argv[1], sys.argv[2]
def read(n):
    try: return open(f"{out}/{n}", errors="replace").read()
    except OSError: return ""
found = 0
# Java crashes: "FATAL EXCEPTION" blocks of our process → exception line + first OnPath frame
heads = collections.Counter()
for b in re.split(r"(?=^.*FATAL EXCEPTION)", read("crash.txt"), flags=re.M):
    if "FATAL EXCEPTION" not in b or "com.onpath.app" not in b: continue
    lines = [re.sub(r"^.*?AndroidRuntime: ", "", l) for l in b.splitlines()]
    exc = next((l for l in lines[1:] if re.match(r"[\w.$]+(Exception|Error)", l)), lines[1] if len(lines) > 1 else "?")
    own = next((l.strip() for l in lines if l.strip().startswith("at com.onpath.")), "(no OnPath frame)")
    heads[f"{exc[:140]}  ←  {own}"] += 1
native = len(re.findall(r"Fatal signal ", read("logcat.txt")))
anr = collections.Counter(re.findall(r"ANR in (com\.onpath\.app[^\n]*)", read("logcat.txt")))
drop = [l for l in read("dropbox.txt").splitlines()
        if re.match(r"\d{4}-\d\d-\d\d [\d:]+ data_app", l) and l[:19] >= start]
print(f"Java crashes: {sum(heads.values())}")
for h, n in heads.most_common(): print(f"   {n} × {h}")
print(f"Native crashes (fatal signal): {native}")
print(f"ANRs: {sum(anr.values())}")
for h, n in anr.most_common(): print(f"   {n} × {h[:160]}")
print(f"Dropbox entries since the start: {len(drop)}")
for d in drop[:10]: print("   " + d)
found += sum(heads.values()) + native + sum(anr.values()) + len(drop)
try: items = json.loads(read("problems.json"))["problems"]
except Exception as e: items = []; print("problems.json not readable:", e)
lv = lambda p: str(p.get("level", "")).upper()
bad = [p for p in items if lv(p) in ("ERROR", "CRASH")]
warn = [p for p in items if lv(p) == "WARN"]
print(f"Problem log: {len(items)} entries, {len(bad)} ERROR/CRASH, {len(warn)} WARN")
for p in bad + warn[:25]:
    print(f"   {lv(p)} {p.get('area')} | {str(p.get('message'))[:170]} | ×{p.get('count', 1)}")
found += len(bad)
print("RESULT:", "nothing found" if not found else f"FOUND {found} – see {out}")
sys.exit(1 if found else 0)
PY
rc=$?
echo "App alive at the end: ${alive:-NO}, recording: $rec · files: $OUT"
exit $rc
