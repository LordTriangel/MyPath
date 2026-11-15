# Tour suggestions – proof of concept

A wish in (where, how long, how hard, what you like), a few loops out, each with its reasons. Runs on this computer
only; nothing here is in the app or the planner. The method and its principles: `docs/design/tour-suggestions.md`.

## Run it

```
python3 tools/suggest-poc/serve.py                      # the page: http://localhost:8790 (+ /vergleich)
python3 tools/suggest-poc/run.py Hilden --hours 1.5-2.5 --ascent 0-150 --png
python3 tools/suggest-poc/run.py Zons --activity bike --hours 3 --natur hoch --wasser --all
python3 tools/suggest-poc/cases.py                      # the owner's comments as checks
python3 tools/suggest-poc/feedback.py --last 5          # his comments from the page, with their routes
```

- Needs: Java (the route service compiles itself in 2 s), the routing data in `~/onpath-routing/segments/`
  (`ONPATH_SEGMENTS`), a map key for the test map server in `~/.gradle/gradle.properties` (map areas are fetched as
  the app does). Python: standard library only – except Pillow for the terrain model (WebP); without it there is no
  "Kante mit Blick" and nothing else changes.
- The page shows official circular routes near the start beside ours (checkbox "offizielle Rundwege") – only where a
  loops file from `official.py extract` exists: today Rheinland-Pfalz.
- Ports: page 8790 (`--port` / `SUGGEST_PORT`), route service 8791 (`SUGGEST_SERVICE=http://127.0.0.1:<port>`).
- Everything it writes goes to `~/onpath-suggest-poc/` (`SUGGEST_DATA`): map areas, runs, pictures, comments.

## Judge it

1. `cases.py` – every comment the owner made, as one wish and a few plain statements. Read the failed ones.
2. Pictures – `run.py … --png` writes `overview.png` and `route-1.png` into `~/onpath-suggest-poc/runs/<run>/`. Look at
   them: would one walk this? Is it near the best this place allows? What obvious thing does it miss?
3. Other regions – a change counts only if it does not make another kind of place worse (coast, heath, mountains,
   a big city, flat farmland). `--all` prints every loop of the pool with its five axes, so one sees why a loop lost.
4. The owner's comments from the page (`feedback.py`) are the last word.

## Change it

- A number: `settings.json` (read again on every request; each group says what its numbers mean). No number there is
  for a region – if a place needs its own value, the measure is wrong.
- What a way *is*: `facts.py`. What is good *here*: `place.py`. What a person *wants*: `character.py`.
- A new way to build loops: a few lines in `generate.pool` that choose turning points and call `add`.
- What counts and what is shown: `score.py` (axes, hard rules, selection, the words).
- After a change: `cases.py`, a look at two or three pictures in different regions, then a pass over the code for
  anything that became needless.

## Files

| File | What |
| --- | --- |
| `suggest.py` | the edge: settings, place names, route service, map areas, the saved run; `suggest(wish)` |
| `character.py` | level 3: the wish (with its two ranges) and the character it gives |
| `facts.py` | level 1: way tags + map grids → facts per way |
| `place.py` | level 2: the place's yardstick, what stands out (areas, lines), the way out |
| `generate.py` | one assembly routine, turning points from four sources → the pool of loops |
| `score.py` | five axes, hard rules, selection, title / reasons / hints |
| `net.py` | the way network and the graph routines (search, path) |
| `land.py`, `named.py`, `cells.py`, `pmt.py` | our map areas: land grid, named routes / lakes / viewpoints, download, tile reader |
| `service/` | the BRouter route service (Java) and the profile copies with the main-road cost |
| `serve.py`, `page.html`, `run.py` | the page, its server, the terminal tool with pictures |
| `cases.py`, `feedback.py` | the owner's comments as checks; his comments from the page |
| `official.py` | official circular routes from OSM: the comparison (see "Official loops") and the official loops the page shows beside ours |
| `region_ai.py` | optional: the AI's region tips per map area (one call per area, picks from our own list) |
| `remote/` | the locked front and the test planner for testing on the road (services stopped; see its README) |
| `settings.json` | every number of the method |

## Weak spots (2026-11-15)

- **Following a river is brittle.** A loop along a line needs a way home that is not the line; at a river that means
  a bridge or a ferry at the right place. Zons, bike 3 h: the Rhine ride (north to Düsseldorf and back on the other
  bank, 59 km) comes out in a trace but not reliably in a run – the one failing check in `cases.py`. The 2 h Rhine
  ride from Stürzelberg works.
- **Direction variety.** Hilden, bike 2 h: the three first loops all go west to the Rhine; the ring through the green
  belts east of the town, which the first version had on top, is not offered.
- **Meeting the own way.** At 2 of 20 judged wishes the first loop still meets its own way once (the coast at
  St. Peter-Ording, the Rhine meadow at Zons – few ways through a dyke or a meadow).
- **A wished climb in the hills.** Schierke, T3 sportlich: the loops have 300–430 m where 650 m and more were wished;
  the run says so, but a higher turning point was not found within the time.
- **Roads for bikes.** In farmland and at the coast a first loop can have 500–800 m on a road at a stretch.
- **Speed.** Walks 0.2–2 s (München centre 5 s), bikes 2–14 s on this computer; on big bike networks the facts take
  the most. Memory is not measured; nothing is measured on a phone.
- **Names.** Long protected-area names; unnamed areas are "Wald bei <Ort>"; in the mountains many titles are only a
  direction.
- **Brooks** come from the routing data's river class plus green beside the way; not checked on pictures.
- **Wheels against official loops.** The official cycling loops of Rheinland-Pfalz ride a median 28 % on streets or
  through villages (longest stretch at a road 1.2 km); we keep the owner's rule (a road is always bad), so our first
  loop matches them only by 12 %. MTB loops: our wheel network (minor ways left out) holds only 45–85 % of several MTB
  park tours – that comparison says little.
- **Signed loops from pieces** need the route in our map: many Traumschleifen run on the Saar-Hunsrück-Steig, which
  takes their ways in our overlays (one route per way), so the loop is not there to follow.
- **Not tried in this version:** the AI tips input (`--tips`), the remote front end to end (it compiles and the
  adapter is changed), the page's controls by hand (pictures are made from saved runs).
- **Unmeasured guesses** in `settings.json`: the character table (axis weights at both ends, ground welcome, usual
  ascent per hour) and the search factors are first values that passed the checks, nothing more.

## Official loops (2026-11-15)

The owner's question: where do we land against official circular routes – do settings suffice, is a step missing,
or should the official loops be used directly? And then: use what the comparisons show to make our loops better.

```
python3 -m venv ~/onpath-suggest-poc/official-compare/venv && ~/onpath-suggest-poc/official-compare/venv/bin/pip install osmium
<venv>/bin/python tools/suggest-poc/official.py extract rheinland-pfalz-latest.osm.pbf   # once, ~2 min: hiking 4–20 km, cycling / MTB 15–60 km
python3 tools/suggest-poc/official.py compare --set neu-traumpfade --name Traumpf [--complete]
python3 tools/suggest-poc/official.py compare --set neu-traumschleifen --name Traumschleife --sample 24
python3 tools/suggest-poc/official.py compare --set neu-wheel --kind wheel --max 40
python3 tools/suggest-poc/official.py compare --set neu-andere --skip Traumpf,Traumschleife --sample 24
python3 tools/suggest-poc/official.py summary --labels base,neu        # the table below; the page shows it at /vergleich
python3 tools/suggest-poc/official.py png --set neu-traumpfade --name Saynsteig   # our first blue, the official red
```

Per loop a wish made from it (start at the parking place; hike T2 or the loop's own T level, a cycling route bike –
gravel when less than half is paved –, an MTB route mtb; its time ±15 %, 60–130 % of its ascent); the official loop is
laid onto our network and judged as one more pool member on the same yardstick (it does not move the yardstick), but
never chosen (`suggest(wish, S, extra)`). Sets from the Geofabrik Rheinland-Pfalz extract (966 closed loops: 934
hiking, 21 cycling, 11 MTB): the 21 Traumpfade around Mayen–Koblenz; 24 of the 43 Traumschleifen (Saar-Hunsrück,
picked at random); all 31 cycling / MTB loops that lie on our network (one MTB tour does not); 24 other hiking loops
picked at random. "base" = origin/main before this round, "neu" = this round; "vollständig" = as if our map carried
every circular route whole.

| Set (loops) | map | first loop on the official (median) | first = official (≥ 80 %) | best shown | best of pool |
| --- | --- | --- | --- | --- | --- |
| Traumpfade (21) | today | 48 → **81 %** | 5 → **11** | 58 → 86 % | 69 → 87 % |
| | complete | 69 → 81 % | 9 → 11 | 86 → 97 % | 99 → 99 % |
| Traumschleifen (24) | today | 26 → **43 %** | 2 → 2 | 52 → 57 % | 60 → 63 % |
| | complete | 66 → 88 % | 10 → 12 | 93 → 98 % | 100 → 100 % |
| Cycling / MTB (31) | today | 11 → 12 % | 0 → 1 | 19 → 19 % | 28 → 34 % |
| | complete | 14 → 16 % | 6 → 8 | 34 → 54 % | 100 → 100 % |
| other hiking loops (24) | today | 23 → 23 % | 3 → 4 | 45 → **53 %** | 50 → **64 %** |

`cases.py`: 54 of 54 before and after.

**What the comparisons showed** (official / our first, medians, base): on foot the official loops have less forest
(Natur 77–80 / 92–97), are signed all the way (100 / 80 %), pass more sights; the loop itself is mostly not in our pool
(7 of 21 Traumpfade, 4 of 24 Traumschleifen, 5 of 24 others), and where it is, it loses by a few points on Natur. On
wheels the official cycling loops ride 28 % on streets or through villages (longest stretch at a road 1.2 km), 71 %
paved; ours stay in the forest (76 %) – the owner's road rule, not a gap to close.

**What changed** (each measured on all four sets and `cases.py`):

- *A signed loop walked round, also from pieces* (`generate.pool`): the step of the first comparison needed the
  route's ways to close into a ring, which today's map almost never gives. Now: where they close, the ring is walked
  exactly; else the route's points seen from its middle (24 directions, at least 18 taken) give the order and 7
  turning points (wheels 9) on the route, its ways at 0.3 ×. Traumpfade best shown 58 → 86 %, other loops pool 50 → 65 %.
- *Walking a signed loop round counts on Besonderes* (+ 0.15 raw, `form.ring`; also for an official loop itself):
  what the place's makers laid out. Traumpfade first 48 → 75–81 %.
- *Less depth at the gemütlich end* (`character.gemütlich.depth` 0.3 → 0.1: Natur is more "green beside the way",
  less "deep inside the forest"). Traumschleifen first 28 → 43 %.
- *Facts "Kante mit Blick" and sights* (`land.rim`, `facts`, `place`): from the map areas' terrain model; a way on an
  open edge with a view counts on Natur as nature and 0.8 on Besonderes, a way at a castle / ruin / chapel / viewpoint
  / cave / waterfall 0.8 on Besonderes, and every sight passed adds to the loop (as viewpoints did). With the two
  changes above: Traumschleifen first 33 → 43 %, other loops pool 59 → 64 %, Traumpfade 81 → 75 % at ring 0.1 (81 %
  at 0.15).
- The wish field `surface` / Wegbeschaffenheit is gone (owner: the activity says it).

**Tried and dropped**

| Tried | Result | |
| --- | --- | --- |
| facts alone (Kante, sights) | ±0 on all sets (Besonderes is near its top anyway) | kept with the others |
| sight bonus 0.1 and Kante worth 1.0 | Traumpfade best shown 86 → 81 %, rest ±0 | dropped |
| a signed loop's ways by length only (whatever they are like) | Traumpfade first 81 → 57 % | dropped |
| ring bonus 0.1 / depth 0.15 | Traumpfade 75 % / 52 %, Traumschleifen 28 % / 43 % | 0.15 / 0.1 kept |
| bins-only ring (no exact walk where the ring closes) | complete map: Traumschleifen first 66 → 28 %, pool 100 → 80 % | exact walk added |
| the official loop moving the yardstick (as before) | after the ring bonus also counted for it: Traumschleifen first 43 → 28 % | yardstick from our pool only |

**Still open**

- The data is the lever: with complete route lines the first loop is the official one at 11 of 21 Traumpfade and 12
  of 24 Traumschleifen (pool 99–100 %). Our overlays keep one route per way (`extract_overlays.py`), so a Traumschleife
  on the Saar-Hunsrück-Steig is mostly not there. Smallest fix: every route of a way in the routes layer; cleaner:
  circular relations as ordered lines with name, length, ascent and start per map area.
- Wheels: the ring bonus now also puts a road-heavy official cycling loop first ("Blick über Altenkirchen": 957 m at
  a road, 24 % through houses, Ruhe 47) – against "along a road is always bad"; the owner should look.
- "Kante mit Blick" is measured on a 45 m grid from zoom-12 heights: valley rims of 20 m and more; checked on a few
  pictures (Dhrontalsperre, Moselhang), not more.

**The page** (`serve.py`): with "offizielle Rundwege" (on by default) up to three official loops that lie in reach and
fit the wish join the list as cards ("Offiziell · Traumpfad", start distance, parking; their OSM `description` as the
text line, else the generated one); an own loop that runs ≥ 80 % on one of them is left out; one line style on the
map, the chosen loop blue, the others a breath, a fine line to the chosen official loop's parking place. It knows
official loops only where `official-compare/loops*.json` exists – today Rheinland-Pfalz.

Data and results outside git: `~/onpath-suggest-poc/official-compare/` (`loops.json`, `compare-<label>-<set>-*.json`,
`summary.json`, `logs/`, `pictures/` of the first round, `pictures-2/` of this one, `REPORT.md`, `REPORT-2.md`).
