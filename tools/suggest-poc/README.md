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
- The page shows official circular routes near the start beside ours (checkbox "ausgeschilderte Rundwege") – only where
  a loops file from `official.py extract` exists: today Rheinland-Pfalz and Nordrhein-Westfalen.
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
| `reference.py`, `FROZEN.md` | the frozen method's reference runs for the Kotlin port; what is frozen and the numbers to reproduce |

## Weak spots (2026-11-16, evening)

- **Following a river is brittle.** A loop along a line needs a way home that is not the line; at a river that means
  a bridge or a ferry at the right place. Zons, bike 3 h: the check (a loop ≥ 25 km on the Rheinradweg) holds since
  the routelines test cells, but the first loop is "Rheinaue Langel-Merkenich – Chorbusch", the Rhine ride only
  further down; a hard road limit for wheels (600 m) loses it again.
- **Direction variety.** Hilden, bike 2 h: the three first loops all go west to the Rhine; the ring through the green
  belts east of the town, which the first version had on top, is not offered.
- **Meeting the own way.** At 2 of 20 judged wishes the first loop still meets its own way once (the coast at
  St. Peter-Ording, the Rhine meadow at Zons – few ways through a dyke or a meadow).
- **A wished climb in the hills.** Schierke, T3 sportlich: the loops have 300–430 m where 650 m and more were wished;
  the run says so, but a higher turning point was not found within the time.
- **Roads for bikes.** In farmland and at the coast a first loop can have 500–800 m on a road at a stretch.
- **Speed.** Walks 0.2–2 s (München centre 5 s), bikes 2–14 s on this computer; on big bike networks the facts take
  the most. Memory is not measured; nothing is measured on a phone.
- **Names.** Long names are shortened for titles and the card line (`score.short`, `short_route`: no brackets, an
  area without what follows " mit ", a list's more telling part, a route's head without stage / ref); titles over
  40 characters 121 → 28 of 587 in the comparison sets, longest 109 → 70 (the rest 41–45 characters, or one route
  name without a break, e.g. "Deutsch-französischer Pamina-Radweg Lautertal"). Unnamed areas are "Wald bei <Ort>"; in the mountains many titles
  are only a direction.
- **The way out counts too little against an official loop** (Traumschleife Marienberg, Boppard): our first loop
  walks 1.8 km through the town and back (28 % through houses) – the way out does not count, so its Natur is 100;
  the official loop leaves the town on its own way (6 %) and every metre of it counts (Natur 66). The way out should
  count as unavoidable only as far as no loop here avoids it. Not changed yet (one place; needs a measure).
- **A low wished ascent in the hills** (Große Dhünntalsperre, bike 2.5–3.75 h, ≤ 350 m): no loop stays below it; the
  run builds once more on flatter ways and shows the flattest (416 m) with "↑ 416 m – mehr als gewünscht (350 m)" and
  a note. Steering towards flat ways from the start (`search.climb` always on) made every comparison set worse
  (Traumpfade first 75 → 74 %, best shown 86 → 81 %, NRW hikes 42 → 38 %) – so it is only the fallback.
- **Similar cards at 75 %** (owner's number, `select.overlap`): two cards may now share up to three quarters of their
  way (Boppard 3 h: two own loops share 67 %); at 60 % the same wishes give a few tenths of a card less (sets: 4.4–4.6
  instead of 4.6–4.8 cards) and "other RLP hiking" best shown 53 instead of 48 %.
- **Brooks** come from the routing data's river class plus green beside the way; not checked on pictures.
- **Wheels against official loops.** The official cycling loops of Rheinland-Pfalz ride a median 28 % on streets or
  through villages (longest stretch at a road 1.2 km); we keep the owner's rule (a road is always bad, 2026-11-16:
  also above the bonus for a signed loop), so our first loop matches them only by 12 % (Nordrhein-Westfalen 13 %).
  "Blick über Altenkirchen" (957 m at a road) stays first there without the bonus: signed all the way, sights, and
  the loops without the road are rougher (Weg 53) or shorter in Form. MTB loops: our wheel network (minor ways left out) holds only 45–85 % of several MTB
  park tours – that comparison says little.
- **Signed loops from pieces** need the route in our map: many Traumschleifen run on the Saar-Hunsrück-Steig, which
  takes their ways in our overlays (one route per way), so the loop is not there to follow.
- **Not tried in this version:** the AI tips input (`--tips`), the remote front end to end (it compiles and the
  adapter is changed), the page's controls by hand (pictures are made from saved runs).
- **Unmeasured guesses** in `settings.json`: the character table (axis weights at both ends, ground welcome, usual
  ascent per hour) and the search factors are first values that passed the checks, nothing more. The same for
  `place.top` (round 5: Natur 0.8, the rest 0.95, Besonderes + `form.ring`): read off 32 hiking places, not tuned.
- **The yardstick's upper end varies by place** (round 5): what the best loop reaches lies ±0.1–0.2 around
  `place.top` (Natur, Besonderes), so per place an axis weighs a little more or less than its character weight; the
  pool's best loop did not have that, but moved every loop's points when a loop was added.

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
python3 tools/suggest-poc/official.py compare --set neu-nrw-wandern --loops nrw --pick nrw-wandern --kind all
python3 tools/suggest-poc/official.py compare --set neu-nrw-rad --loops nrw --pick nrw-rad --kind all
python3 tools/suggest-poc/official.py summary --labels base,neu        # the table below; the page shows it at /vergleich
python3 tools/suggest-poc/official.py png --set neu-traumpfade --name Saynsteig   # our first blue, the official red
```

Per loop a wish made from it (start at the parking place; hike T2 or the loop's own T level, a cycling route bike –
gravel when less than half is paved –, an MTB route mtb; its time ±15 %, 60–130 % of its ascent); the official loop is
laid onto our network and judged on the place's yardstick like every loop (since round 5 no loop moves it), but
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

**The page** (`serve.py`): with "ausgeschilderte Rundwege" (on by default) up to three official loops that lie in reach
and fit the wish join the list as cards ("Ausgeschildert · Traumpfad", start distance, parking; the text line is our
own, generated from what the loop passes – no OSM / operator descriptions, owner 2026-11-16); they are chosen in one selection with ours (no two cards share more than 75 % of their way; official + 5 points between similar ones, 2026-11-16); one line style on the
map, the chosen loop blue, the others a breath, a fine line to the chosen official loop's parking place. It knows
official loops only where `official-compare/loops*.json` exists – today Rheinland-Pfalz and Nordrhein-Westfalen.

Data and results outside git: `~/onpath-suggest-poc/official-compare/` (`loops.json`, `compare-<label>-<set>-*.json`,
`summary.json`, `logs/`, `pictures/` of the first round, `pictures-2/` of this one, `REPORT.md`, `REPORT-2.md`).

## Third round (2026-11-16): the owner's answers, Nordrhein-Westfalen

- **Wheels: the road rule beats the ring bonus** (owner). The + 0.15 for going round a signed loop counts only while
  the loop's longest stretch along a road with cars stays within `form.ring_road_m` (300 m, the foot rule's number;
  on foot a longer stretch is out anyway, so hikes are unchanged). The same for an official loop on the page.
  Official cycling loops with long road stretches fall back (RLP: median rank of the official loop 5 → 7, official
  ahead of our first 5 → 3; NRW 3 → 4, 5 → 4); our own first loops stay (they rarely went round a road-heavy route).
  "Blick über Altenkirchen" stays first without the bonus (80 vs 76 points: signed all the way, sights; the quieter
  loops there are rougher or less clean).
- **Copyright** (owner): the badge reads "Ausgeschildert · <family>", the checkbox "ausgeschilderte Rundwege"; the
  card line of an official loop is our own line from what it passes; `extract` no longer keeps OSM descriptions
  (removed from the loops files too).
- **Two NRW comparison sets** (`official.PICKS`, `--loops nrw --pick …`): 25 well-known hiking loops (EifelSchleifen,
  Monschau, Rureifel, Siebengebirge, Sieg, Bergisches Land / Neanderland, Sauerland / Rothaarsteig-Spuren,
  Niederrhein, Teutoschleifen, Velmerstot; one does not lie on our network) and 15 cycling / MTB loops (Hilden,
  Apfelroute, Naafbachtal, Rur, Nettetal, Rees, Hülser Berg, Ruhr, Haltern, Tecklenburg, Nordkirchen, Rothaar,
  bike arena Sauerland, Eifel, Haard; one not on our network).
- **Shorter names** in titles and card lines (`score.short`, `score.short_route`), see Weak spots "Names".
- Cache files of the map areas are written whole and then moved (`named`, `land`): parallel runs read half a file.

| Set (loops) | first on the official (median) | first = official | best shown | pool | rank of the official | official ahead |
| --- | --- | --- | --- | --- | --- | --- |
| Traumpfade (21) | 81 → 81 % | 11 → 11 | 86 → 86 % | 87 → 87 % | 2 → 2 | 5 → 5 |
| Traumschleifen (24) | 43 → 43 % | 2 → 2 | 57 → 57 % | 63 → 63 % | 3 → 3 | 9 → 9 |
| RLP cycling / MTB (30) | 12 → 12 % | 1 → 1 | 19 → 19 % | 34 → 34 % | 5 → 7 | 5 → 3 |
| other RLP hiking (24) | 23 → 23 % | 4 → 4 | 53 → 53 % | 64 → 64 % | 7 → 7 | 8 → 8 |
| NRW hiking (24) | 20 → 20 % | 1 → 1 | 42 → 42 % | 53 → 53 % | 8 → 8 | 2 → 2 |
| NRW cycling / MTB (14) | 13 → 13 % | 0 → 0 | 26 → 25 % | 40 → 40 % | 3 → 4 | 5 → 4 |

`cases.py` 54 of 54 before and after. Tried and dropped: a hard road limit for wheels (`road_walk_max_m` 600):
RLP first 12 → 10 %, best shown 19 → 15 %, and the Zons Rhine check fails.

**What NRW shows:** against the well-known NRW loops our first loop runs only 20 % on the official one (Traumpfade
81 %), the official loop ranks 8th in our own points. Many of them cross fields and villages (KapellenSchleife 1
"Felder, Wiesen & Weiden": Natur 16, rank 30; Schwalmtalweg 2: Natur 6) – there our greener loop is the better
suggestion by the owner's rules. Where the official loop is a real nature loop (Teutoschleife Dörenther Klippen,
Monschau, Siebengebirge, Naafbachtal-Route, Von Ruhr zur Ruhr, Haard) it ranks 1–2 and the pool holds 50–90 % of it.

## Fourth round (2026-11-16 evening): the owner's comments from the page

Four comments (22:16–22:33), each looked at in its saved run and re-run (`run.py … --official --all`, `--official`
is new: the official loops near the start join as on the page).

- **One selection over all cards** (`score.choose`): our loops and the official loops that fit the wish (time as
  ours, ascent not above the maximum) are chosen together; no two cards share more than `select.overlap` (0.75,
  owner) of the shorter one's way beyond the way out; an official loop counts + 5 points in the selection only
  (`official_ahead`: between similar ones it wins unless clearly worse); an official loop inside the wished time is
  one of the things that stand out (shown if within 15 points, before lines and areas); at most five. Before: our
  five, then up to three official loops on top (Nordkirchen bike 2.5–3.25 h: 8 cards; now 5, one official).
  `select.official` and `on_official` are gone.
- **At least three when there are three** (`least` 3, `least_behind` 30): while fewer than three are shown, further
  different loops may be up to 30 points behind the first. Nordkirchen on foot 2.5–3.25 h: 1 → 3 cards.
- **Road repair on foot** (`search.road_repair` 8): a loop that would be out for > 300 m along a road (here mostly a
  path right beside a Landstraße) is built once more with such ways 8 × dearer. Nordkirchen on foot: 12 of 22 loops
  were out, 4 of 14 after it.
- **Ascent** (Dhünntalsperre): the owner had typed 350 into "bis" only; the page sent an ascent only when both fields
  were filled, so the run used 0–730 m. Now one field is enough (only "bis" = 0–x, only "von" = x up to what is
  usual), the status line shows the range used. The maximum stays hard for our and the official loops; when nothing
  stays below it, see Weak spots "A low wished ascent".
- `cases.py` runs as the page (official loops on) and has three more cases: Nordkirchen on foot 2.5–3.25 h (≥ 3
  cards), Nordkirchen bike 2.5–3.25 h (3–5 cards), Dhünntalsperre ≤ 350 m (no card above 350 m without the hint).

| Set (loops common to all) | first on the official | best shown | pool | cards | |
| --- | --- | --- | --- | --- | --- |
| Traumpfade (21) | 81 → 75 % (≥ 80 %: 11 → 10) | 86 → 86 % | 87 → 87 % | 4.7 → 4.8 | one loop: Heidehimmel's first is another loop (74.2 vs 73.0 points), the official one still shown |
| Traumschleifen (24) | 43 → 43 % | 56 → 61 % | 63 → 63 % | 4.5 → 4.8 | |
| RLP cycling / MTB (30) | 12 → 12 % | 19 → 19 % | 34 → 34 % | 4.1 → 4.2 | |
| other RLP hiking (24) | 23 → 23 % | 53 → 48 % | 64 → 64 % | 4.5 → 4.6 | at 60 %: 53 % |
| NRW hiking (24) | 20 → 20 % | 42 → 42 % | 53 → 53 % | 4.5 → 4.7 | |
| NRW cycling / MTB (14) | 13 → 13 % | 25 → 25 % | 40 → 40 % | 4.4 → 4.8 | |

`cases.py` 65 of 65 (the 54 old ones and 11 new statements; before this round the three new cases fail). The
comparison sets measure only our own selection (their official loop is measured, never shown); a set now has 1–2
more rows where earlier nothing was shown below the wished ascent and now the flattest is.

Comments 2 (Nordkirchen bike, "warum nicht weiter nördlich durch die Aue") and 3 (Hilden bike, "ist der Chorbusch den
Weg durch Dormagen und den Chempark wert") gave no general change: the Stever loops north of Lüdinghausen, routed by
hand, run 40–44 % on streets and along the Dortmund-Ems-Kanal and score 60–68 against 75; the Chorbusch loop passes
1.4 km through Dormagen and 1.2 km through Worringen (west of the Chempark, not through it) and wins by Besonderes
(four protected areas, the Rheinradweg) and Form – the 11 km way out of Hilden does not count (Weak spot "The way out").

## Fifth round (2026-11-16 night): the yardstick from the place alone – then frozen

The review's finding: a loop's four place axes were read up to the best raw value of the pool, so adding or dropping
one candidate changed every loop's points (and official loops needed a rule of their own not to move it). Now
(`place.top`, `Place.judge`): the upper end per axis is a quantile of the ways beyond the way out – Natur 0.8 (a loop's
mean over mixed ways rarely reaches the very best ways; at 0.8 the median gap to the pool's best loop is 0.01 over 32
hiking places), Ruhe / Weg / Besonderes 0.95, Besonderes plus `form.ring` (room for the bonus of going round a signed
loop). A loop's points depend only on the place and itself; the rule "official loops do not move the yardstick" is
gone with the pool maximum. Points are lower in absolute terms (our first loop's median 92 → 88 on the Traumpfade).

| Set (loops) | first on the official: median (mean) | ≥ 80 % | best shown | pool | official ranks 1st | cards |
| --- | --- | --- | --- | --- | --- | --- |
| Traumpfade (21) | 75 → 75 % (0.63 → 0.62) | 10 → 10 | 86 → 86 % | 87 → 87 % | 5 → 9 | 4.8 → 4.8 |
| Traumschleifen (24) | 43 → 28 % (0.40 → 0.39) | 2 → 3 | 61 → 61 % | 63 → 63 % | 9 → 12 | 4.8 → 4.9 |
| RLP cycling / MTB (32) | 11 → 11 % (0.19 → 0.20) | 1 → 1 | 19 → 20 % | 34 → 34 % | 3 → 6 | 4.1 → 4.2 |
| other RLP hiking (24) | 23 → 23 % (0.32 → 0.29) | 4 → 2 | 48 → 47 % | 64 → 64 % | 8 → 7 | 4.6 → 4.6 |
| NRW hiking (25) | 20 → 22 % (0.30 → 0.33) | 2 → 3 | 42 → 42 % | 53 → 53 % | 1 → 4 | 4.6 → 4.6 |
| NRW cycling / MTB (15) | 10 → 10 % (0.12 → 0.12) | 0 → 0 | 23 → 25 % | 30 → 30 % | 4 → 6 | 4.6 → 4.7 |

`cases.py` 65 of 65 before and after. The Traumschleifen median falls by one loop crossing the middle (mean 0.40 →
0.39, ≥ 50 % 9 → 10); overall neutral, and the official loops rank first in our own points more often.

Tried and dropped (same sets): the upper end = the 95 % quantile of all ways in reach (`place.high`) for every axis –
Traumpfade 75 → 57 % (mean 0.63 → 0.59), Traumschleifen 43 → 28 %: Besonderes saturates (59 % of the places have a
loop above it), going round a signed loop no longer counts; the same with room for every bonus (ring + 3 sights) –
Traumpfade 57 %, Traumschleifen 24 %: Besonderes is squeezed. Checked: with the old pool yardstick the rule for official
loops is still needed (without it Traumschleifen 43 → 28 %).

After this round the method is frozen: `FROZEN.md`.

