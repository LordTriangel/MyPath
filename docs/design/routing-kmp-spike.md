# Routing spike 5a: BRouter's routing core as pure Kotlin, without the JVM

[umbau-hp], 2026-11-18, hp computer. Question from `architecture-plan-2026-11.md` §4 / §5: can BRouter's routing core run
as pure Kotlin Multiplatform, so that iOS gets the same offline routing as Android? Budget two days; the answer was
clear after one evening.

## Answer

**Yes – port it (way A).** The Kotlin port gives **the same routes as today's Java engine, bit for bit, on 24 of 24
reference routes**, both on the JVM and as a native program without any JVM (`linuxX64`). Nothing blocks. The price on
native is speed: **about 1.9 times the Java engine's time** on this computer; memory is **a quarter**.

| | Java engine today | Kotlin port on the JVM | Kotlin port native (`linuxX64`) |
| --- | --- | --- | --- |
| Routes identical to the Java engine | – | 24 of 24 | 24 of 24 |
| Time, all 24 routes | 32.5 s | 34.1 s (+5 %) | 60.3 s (×1.86) |
| 98 km bike route (Hilden → Nordkirchen) | 3.9 s | 4.1 s | 7.4 s |
| 2 km hike / 1 km bike | 20 / 44 ms | 24 / 54 ms | 31 / 32 ms |
| Peak memory of the process | 385 MB | 371 MB | 96 MB |
| Program size | 5 jars, 266 KB | – | 1.8 MB executable |

What this means:
- **Android loses nothing.** The same Kotlin code on the JVM is as fast as the Java jars (+5 %, inside the noise of a
  shared computer) and gives identical routes. Plan §5b's bar "Android switches when equal and not slower" is in reach.
- **iOS gets real offline routing** from the same source, at roughly twice the Java time on equal hardware. Short and
  medium routes stay far below a second; the 98 km route takes 7 s here. The app already routes long plans leg by leg
  behind a wait bar, so this is a cost, not a blocker.
- **The port is much smaller than planned.** Four Sonnet helpers converted 13,100 lines in 6–11 minutes each; joining
  the four parts needed about 70 compiler fixes at the seams; the first run was identical on all routes. Plan §5b's
  "1–2 weeks" is closer to **3–5 nights**, most of it the hardening list below, not the conversion.

## What was built

`tools/spike-routing-kmp/` – a Gradle build of its own, **not wired into the app or the planner**.
- Source: `abrensch/brouter` commit `29898106`. That is exactly the app's jars: compiled, it matches
  `app/libs/brouter-*-1.7.11-beta-onpath1.jar` class for class, except the one class OnPath patches.
- `src/commonMain/kotlin/btools/{util,codec,expressions,mapaccess,router}` – 74 files, 13,100 lines, no `java.*`.
  The JVM is needed for nothing but a platform edge of 62 lines (`btools/platform`: read a file at a position, read a
  whole file, clock) with 22 lines for the JVM and 89 for Linux. An iOS edge would be the same size.
- Kept: the rd5 reader, the profile language, the search, waypoint matching, via-point correction, island detection,
  turn restrictions, nogos, voice hints, the memory budget. Cut (the app does not use it): output formats (GPX / KML /
  JSON / CSV), car profiles (kinematic model), BRouter's own round trip and elevation / info modes, raw-track reuse,
  map creation (encode side), debug logs. Every cut is marked `PORT-CUT` in the code (37 places), every doubt
  `PORT-NOTE` (35). Rules of the conversion: `PORTING.md`.
- `ref/` – the same routes on today's jars; `fixtures/reference.txt.gz` – their result; `compare.py`; `bench.sh`.

Run it (Gradle only in a slot):
```
tools/dev/gradle_slot.sh -- ./gradlew -p tools/spike-routing-kmp linkReleaseExecutableLinuxX64 jvmClasspath
tools/spike-routing-kmp/run_kotlin.sh <dir with E5_N50.rd5 + E10_N45.rd5>      # both targets + comparison
tools/spike-routing-kmp/bench.sh <dir>                                          # the time table
tools/spike-routing-kmp/ref/run_ref.sh <dir>                                    # fixtures again from the jars
```

## How "identical" was checked

24 routes in `routes.txt`, with the app's own settings (`BRouterEngine.keyValues`): bike (`trekking.brf`), hike
(`hiking-mountain.brf`), `mtb.brf`, `touring.brf` incl. the e-bike values; 1 km to 155 km; with via points; the Rhineland
/ Ruhr and South Tyrol / Tyrol / Bavaria map files, plus Flensburg and Verona at their north and south ends; two routes
that fail on purpose (a point on an island, no track found); three with a small memory budget.

Per route the dump holds: total cost, length, ascent, energy, time; **every track point** with position, elevation,
cost, time and energy (the raw bits of the float, so not even the last digit may differ), a hash of its way tags;
**every voice hint** with place, command, angle, roundabout exit. 38,000 track points in all. The dump of the Kotlin
port is compared line by line with the Java engine's.

14 of the routes run through BRouter's memory-pressure path (it throws nodes away during the search, up to 57 times
on one route) – the same number of times and the same result on native.

## Time and memory, per route

Fastest of 6 runs per route and engine, the three engines taking turns (so the computer's other work hits all alike);
JVM runs with `-Xmx192m` like the routing tests, JDK 21. Native: Kotlin/Native 2.2.10, release build, default settings.

| Route | Java engine | Kotlin on JVM | Kotlin/Native linuxX64 | Native ÷ Java |
| --- | ---: | ---: | ---: | ---: |
| bike-hilden-1km | 44 ms | 54 ms | 32 ms | 0.7 |
| bike-hilden-duesseldorf-15km | 463 ms | 412 ms | 503 ms | 1.1 |
| bike-hilden-koeln-30km | 72 ms | 73 ms | 133 ms | 1.8 |
| bike-hilden-dortmund-via-wuppertal-70km | 1472 ms | 1564 ms | 2822 ms | 1.9 |
| bike-hilden-nordkirchen-98km | 3897 ms | 4084 ms | 7442 ms | 1.9 |
| bike-koeln-muenster-170km | 124 ms | 120 ms | 223 ms | 1.8 |
| bike-meran-bozen-30km | 108 ms | 106 ms | 161 ms | 1.5 |
| bike-innsbruck-muenchen-150km | 1775 ms | 1895 ms | 3319 ms | 1.9 |
| hike-hilden-park-2km | 20 ms | 24 ms | 31 ms | 1.6 |
| hike-vellau-hochmuth-leiteralm | 26 ms | 30 ms | 28 ms | 1.1 |
| hike-meran-hirzer-20km | 64 ms | 67 ms | 92 ms | 1.4 |
| hike-garmisch-zugspitze | 107 ms | 114 ms | 152 ms | 1.4 |
| hike-dortmund-hilden-70km | 2056 ms | 2030 ms | 3727 ms | 1.8 |
| bike-koeln-deutz-bonn-30km | 575 ms | 579 ms | 1033 ms | 1.8 |
| bike-duesseldorf-muenster-120km | 5024 ms | 5182 ms | 9602 ms | 1.9 |
| mtb-hilden-wuppertal-25km | 1893 ms | 1877 ms | 3526 ms | 1.9 |
| mtb-meran-hirzer-20km | 102 ms | 108 ms | 160 ms | 1.6 |
| touring-hilden-dortmund-60km | 2254 ms | 2254 ms | 4114 ms | 1.8 |
| touring-ebike-meran-bozen-30km | 94 ms | 97 ms | 152 ms | 1.6 |
| bike-flensburg-schleswig-35km | 127 ms | 136 ms | 213 ms | 1.7 |
| bike-verona-mantova-40km | 251 ms | 259 ms | 407 ms | 1.6 |
| bike-hilden-nordkirchen-98km-mem16 | 4202 ms | 4479 ms | 7695 ms | 1.8 |
| bike-duesseldorf-muenster-120km-mem24 | 5354 ms | 5990 ms | 10001 ms | 1.9 |
| hike-dortmund-hilden-70km-mem16 | 2395 ms | 2572 ms | 4752 ms | 2.0 |
| **all routes** | 32499 ms | 34106 ms | 60320 ms | 1.86 |
| peak memory of the process | 385 MB | 371 MB | 96 MB | |

Where the native time goes: a build without any garbage collection still needed about 1.5 times the Java time (measured
once, on the first 15 routes), so roughly a third of the gap is memory management and the rest is the compiled code
itself (HotSpot optimises the hot search loop at run time; Kotlin/Native cannot). No tuning was tried beyond that.

## What the spike does not prove

An Opus review with fresh eyes compared the port with the Java side by side. It found **no wrong conversion**, and these
open points – none blocks the decision, all belong to the real port (5b):

1. **Not built for iOS itself.** `linuxX64` stands in: same compiler, same runtime, but an iPhone build (`iosArm64`)
   needs a Mac (or a GitHub macOS run) and the speed on a real phone is unknown. Android's phone speed is unknown too,
   but there the code runs on the same runtime as today.
2. **Maths of the platform.** Distances use a cosine table by latitude, times use `exp`. On native these come from the
   system's maths library. Here (glibc) every bit matched from 45°N to 55°N; Apple's library is not tested. Fix if
   needed: ship the table as constants (small).
3. **Order of a hash map under memory pressure.** One counter in the node clean-up depends on the order in which a hash
   map is walked, and that order differs on native. In 14 routes it made no difference; it can in principle. Fix: a
   fixed order on both sides (medium).
4. **Robustness on native:** a very deep recursion that Java survives would crash a native program (fix: a loop, ~40
   lines); out-of-memory cannot be caught on native; cancelling from another thread (`terminate()`) and the progress the
   wait bar reads were simplified in the spike and must be done properly.
5. **Not in the comparison yet:** the junction details the app reads from a voice hint (`OnPathHints`), the snapped
   positions of the plan points, time-outs, cancel, a successful island retry, warm second runs.
6. **The app's other users of BRouter classes** (`suggest/NetReader`, `Rd5Parts`, `Junctions`, `OnPathHints`) were not
   moved; the classes they need exist in the port.
7. **The planner** could later compile this Kotlin for the browser (Kotlin/Wasm) instead of the Java jars through
   TeaVM – not tried here.

## Recommendation

**Port (way A of plan §4).** Order:
1. Now: nothing more – the spike stays as `tools/spike-routing-kmp/`, outside every build.
2. After phase 3 of the rebuild (`:core` is multiplatform): phase 5b takes the spike's code into `:core` behind the
   `RoutingEngine` port, works off the list above, widens the comparison to what the app reads, and switches Android
   when the routes are identical and the Pixel is not slower (`tools/routing/phone-bench`). Size: 3–5 nights.
3. Before the iOS app starts: one build for `iosArm64` and the same routes on a real iPhone – then the speed question
   is answered for good.

Ways B (the browser engine inside the iOS app) and C (a server) are not needed.
