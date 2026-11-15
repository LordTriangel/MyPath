# Can the tour suggestions run on a phone? – a measurement, not a port

The question (IR, 2026-11-15): before anyone ports the method (`docs/design/tour-suggestions.md` §3), how much time
and memory does its expensive core need – on this computer's JVM and on the Pixel 8.

| File | What |
| --- | --- |
| `count_searches.py` | wraps the POC (nothing in it changes): nodes, links, graph searches by kind, assembly calls, phase times of one run |
| `Bench.java` | the core the way a port would do it: read the network from the rd5 files as `service/RouteService.java` `/graph` does, into int / float arrays; a stand-in for the facts; the POC's number of searches with its penalties. Java 8, BRouter's jars only |
| `bench.sh` | compile + run on the desktop, or dex (d8) + run on the Pixel with `dalvikvm` (pushes `E5_N50.rd5` + profiles to `/data/local/tmp/onpath-bench`, removes them after) |

## Rerun

```
python3 tools/suggest-poc/bench-phone/count_searches.py Hilden hike 2          # needs the POC's route service (starts itself)
tools/suggest-poc/bench-phone/bench.sh desktop hike2 hike4 bike2 bike3 zons-bike3
tools/dev/device.sh pixel -- tools/suggest-poc/bench-phone/bench.sh pixel hike2 hike4 bike2 bike3
```

`bench.sh pixel` refuses while OnPath records a tour; it installs nothing and does not touch the app's data. `XMX`
sets the heap (Pixel default 256m, the usual app limit), `NODE_CACHE_MB` BRouter's node-cache limit (64).

## What the POC does per wish (count_searches.py, 2026-11-15, this computer)

| Wish | nodes | links | searches full / stop / bounded | assemble calls | Python s (network · facts · place · loops · judging) |
| --- | --- | --- | --- | --- | --- |
| Hilden hike 2 h | 19 966 | 26 180 | 2 / 102 / 3 | 62 | 1.5 (0.15 · 0.44 · 0.18 · 0.69 · 0.03) |
| Hilden hike 4 h | 65 441 | 83 819 | 4 / 96 / 3 | 50 | 4.5 (0.44 · 1.24 · 0.54 · 2.22 · 0.05) |
| München hike 2 h | 69 998 | 92 642 | 1 / 106 / 3 | 62 | 4.6 (0.41 · 1.07 · 0.56 · 2.50 · 0.08) |
| Hilden bike 2 h | 167 145 | 194 026 | 2 / 60 / 2 | 34 | 10.0 (1.29 · 2.59 · 1.11 · 4.85 · 0.15) |
| Hilden bike 3 h | 250 000 | 291 748 | 5 / 58 / 2 | 28 | 14.6 (2.01 · 3.91 · 1.65 · 6.82 · 0.15) |
| Zons bike 3 h | 245 958 | 288 338 | 3 / 54 / 2 | 28 | 13.7 (1.99 · 4.04 · 1.63 · 5.78 · 0.21) |
| München bike 3 h | 234 227 | 273 851 | 38 / 92 / 2 | 39 | 13.9 (1.82 · 3.68 · 1.63 · 6.59 · 0.13) |

"full" beyond the first are the ring checks of signed routes (tiny searches on the route's own ways). A stop search
settles 8–90 k nodes – a turning point is far out, so each one covers a good part of the network.

## Bench.java on the desktop (Ryzen 9 9950X, OpenJDK 25, -Xmx512m, node cache 64 MB)

| Case | read network | facts stand-in | searches (1st / 2nd run) | heap the run needs* | network as arrays |
| --- | --- | --- | --- | --- | --- |
| hike2 | 0.13 s | 0.01 s | 0.05 / 0.04 s | ~22 MB (sampled) | 6 MB |
| hike4 | 0.27 s | 0.03 s | 0.16 / 0.14 s | 32–48 MB | 15 MB |
| bike2 | 0.73 s | 0.08 s | 0.32 / 0.31 s | ≤ 112 MB | 34 MB |
| bike3 | 1.06 s | 0.10 s | 0.64 / 0.66 s | 144–160 MB | 46 MB |
| zons-bike3 | 1.06 s | 0.12 s | 0.51 / 0.42 s | ~150 MB (as bike3) | 46 MB |

\* smallest `-Xmx` with which the case still runs (bike3: OOM at 144m, fine at 160m). The peak is while reading:
BRouter's node and link objects of the whole area plus the walk's bookkeeping; once copied into arrays the network
takes a third of it. The node counts match the POC exactly (same walk, same profile).

## On the phone

Not measured yet: the Pixel was not connected on 2026-11-15 (the dex builds; `bench.sh pixel` is ready).
**Estimate, not a measurement:** a Pixel 8 big core is about half as fast as this desktop core, and ART's JIT about
1.5–2× slower than HotSpot on array code → 3–5× the desktop times: hike 2 h ≈ 0.6–1 s, hike 4 h ≈ 1.5–2.5 s,
bike 2 h ≈ 3–6 s, bike 3 h ≈ 5–9 s for the core (reading the network dominates, then the searches). Memory: the
hikes fit easily; bike 3 h needs ~150 MB of Java heap while reading – too close to a 256 MB app limit next to the
app itself.
