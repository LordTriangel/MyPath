# Recording on the main thread – measured (2026-11-17)

The second code audit (2026-11-12, point d – `docs/team/backlog.md`) noted: every GPS fix arrives on the main looper
(`LocationTracker`, `requestLocationUpdates(…, Looper.getMainLooper())`) and `RecordingService.onFix` does the
navigation maths and `db.appendPoint` there. The plan said: measure first, then judge whether recording needs its own
thread. This is the measurement. **Nothing in the app was changed for it.**

## How it was measured

`tools/dev/device.sh emulator -- tools/dev/main_thread_measure.sh [seconds on screen] [seconds left]` runs the
androidTest class `e2e/MainThreadMeasure` (not a test of the app). It sits in the app's process and

- times every main-looper message (`Looper.setMessageLogging`). The Demo ride's tick (one a second) is the fix: it hands
  the fix to both trackers – the app's (position on the map) and the service's (`onFix`: off-route, arrival, junction
  cue, voice, maps ahead, pace, point filter, `appendPoint`, state for the UI) – like the two listener calls of a real fix;
- counts disk reads / writes on the main thread with StrictMode (listener only), split into "inside a fix" and
  "elsewhere", named by the first OnPath frames. StrictMode counts at most 10 per looper message;
- reads the window's frame times (FrameMetrics) and marks the frames a fix overlapped; plus `dumpsys gfxinfo`.

Scenario: debug build, a 10.5 km bike route around Meran with real routing data, Demo ride at 1×, Navigate on screen
for 180 s, then Home (app left, only the service takes the fix) for 90 s.

## Numbers (emulator: API 35 x86_64, headless, software GL, donald)

| | Navigate on screen (180 s) | App left, service only (90 s) |
| --- | --- | --- |
| Fixes / points stored | 179 / 179 | 90 / 47 (uphill under 3 m/s: the point filter keeps every second fix) |
| Main-thread time per fix (wall) | median 1.7 ms · p90 2.7 · p99 5.2 · **max 5.4 ms** | median 1.3 ms · p90 1.9 · **max 2.6 ms** |
| … CPU time per fix | median 1.7 ms · max 5.1 ms | median 1.3 ms · max 2.2 ms |
| Fixes over 8 ms / 16 ms / 100 ms | 0 / 0 / 0 | 0 / 0 / 0 |
| Share of the main thread | fixes 0.2 % · everything else 6.8 % | fixes 0.1 % · everything else 0.7 % |
| Disk on the main thread inside a fix | 382 = 2.1 per fix | 145 = 1.6 per fix |
| Disk on the main thread elsewhere | 18 | 9 |

Where the disk access is (both phases together):

| Count | Kind | Where |
| --- | --- | --- |
| 273 | read, in a fix | `Prefs.<init>` ← `RecordingService.voiceOn` – a new `Prefs(this)` on every fix (the preferences are in memory after the first load; StrictMode counts the look-up of the file) |
| 226 | write, in a fix | `TourDb.insertPoint` ← `appendPoint` – one per stored point, as the robustness rule wants („every point to SQLite immediately“) |
| 27 | write, outside a fix | `TourDb.updateStats` ← `RecordingService.saveStats` – every 10 s |
| 16 | read, in a fix | `Rd5PartStore.fileKey` ← `cached` (maps ahead: is the routing part there) |
| 9 | read, in a fix | `MapCells.regionBounds` ← `hasDetailedMap` (maps ahead) |

Frames while Navigate was on screen (the Compose window; MapLibre draws on its own thread and is not in these numbers):

- 2,940 frames; gfxinfo: 6.2 % janky, 50th / 90th / 99th percentile 12 / 25 / 38 ms, „Slow UI thread“ 8 – with software
  GL on the emulator the frame times say little about the phone, the comparison below is what counts.
- 55 frames overlapped a fix: 28 over 16.7 ms (51 %). 2,885 frames without a fix: 1,418 over 16.7 ms (49 %). The same
  share – a fix does not make a frame late. Waiting for the main thread: median 0.3 ms in both groups.
- Longest main-thread message that was not a fix: 31.9 ms (a frame); 59 of 32,841 messages over 16 ms, none over 100 ms.

## Reading

- **A fix costs about 2 ms of main thread, at worst 5 ms** – a third of one 60 Hz frame, once a second. No fix was
  longer than a frame, none came near a stall. The off-route check, cues, pace and the point write together are small.
- **The one write per fix is the point itself** (SQLite WAL: an append to the log, no sync per row). It is the write the
  robustness rule asks for; on this run it fits inside the 1–2 ms.
- **What the emulator cannot say:** flash storage on a phone under load (a map download writing beside it, a nearly
  full disk) can stretch a single write to tens or hundreds of ms; the emulator's disk is donald's SSD. And a real fix
  wakes the main thread twice (two listeners) instead of once. The phone's own watchdog (`debug/Health`, „Main thread
  blocked … ms“ in the problem log) is the place where such a stall would show on a tour – the stalls looked at on the
  Pixel on 2026-11-07 (`debug/MainStall`) were first frames and `onStop`, none named `onFix`.
- **Verdict for the audit plan:** moving recording off the main thread is **not urgent for smoothness**; there is no
  measured problem to fix. The reasons left for it are structural (one writer, the service as owner – audit point c)
  and the rare slow-disk case. If it is done, it is a rebuild of a core feature (test first), not a speed fix.
- **Small things seen on the way** (not changed in this piece – nothing confirmed as a bug):
  `voiceOn()` builds a new `Prefs` on every fix; the two „maps ahead“ look-ups touch the file system on the main thread
  now and then. Both are cheap; they are candidates for the later service piece.

## Repeat

`tools/dev/device.sh emulator -- tools/dev/main_thread_measure.sh 180 90` (about 8 min with the build). Output:
`~/onpath-cells-work/measure/<date-time>/measure.txt`. A change that moves work in `onFix` should be measured before
and after with the same command.

## Later: what moving recording off the main thread would need (note of cleanup piece 5, 2026-11-18)

Piece 5 did **not** move anything (the numbers above say it is not urgent). It put the contract in place that a later
move stands on: the app talks to the service only through `recording/RecordingClient` (typed commands = today's
Intents), the service's flows are read-only outside it, and `nav-session.json` + `follow.json` are written through one
object (`navigation/FollowStore`: every write one locked step, the service's conditional on its token). What is still
to do for a recording thread of its own:

1. **One recording dispatcher** (single thread) that owns everything `onFix` touches: `TourRun`, `PointQueue`,
   `Navigator`, `tourRun` / pace / rest state, `follow`, `settings`. Today these are plain fields guarded only by
   "everything is on main". `onStartCommand` then only posts the command to that thread; `LocationTracker` gets the
   thread's looper instead of `Looper.getMainLooper()` (LocationTracker.kt, both `requestLocationUpdates`), and the
   Demo ride's tick hands its fix over the same way.
2. **What must stay on main**, posted back: `startForeground` / notifications are thread-safe, but `Speaker` (TTS
   callbacks), `Vibrator`, the wake lock and `stopSelf(startId)` should keep their thread; `_state.value = …` may be
   set from any thread (StateFlow), the view model collects on main already.
3. **The re-route result** arrives on main today (`startReroute`: the service scope is on main, only the routing runs on `routeDispatcher`): it must land on the recording
   thread. From then on the app's write (main) and the service's (recording thread) really run side by side – the case
   `FollowStore` / `NavSessionStore.saveIfMine` are built for (`FollowStoreTest`, two threads).
4. **`finish()`**: the last points, `saveStats("done")`, `RideApply.finish` and `_finished` in this order on the
   recording thread, then the foreground stop on main; `TourInterruptedReceiver` and the sticky restart read the same
   database rows as now.
5. **SQLite**: `TourDb` is used by the view model on `Dispatchers.IO` beside the service already (WAL); one writer
   thread for points makes it simpler, not harder. `Prefs(this)` per fix (`voiceOn`) → read once per settings change.
6. **Tests first** (core feature C4/C5): `FakeNavService` already runs the service's navigation wiring on two
   `ManualExecutor`s – an Android-free core of the service (commands in, state out) would let the same tests drive the
   real code; then `MainThreadMeasure` before / after, `CoreFieldE2ETest`, the Rundgang's recording flows, and a tour on
   the Pixel with the screen off.
