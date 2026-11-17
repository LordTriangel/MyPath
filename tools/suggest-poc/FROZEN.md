# Tour suggestions POC – frozen (stage 0, 2026-11-16)

From now on **no method change in Python.** The method (facts → place → character → candidates → five axes →
selection, `settings.json`) is the one of commit `a351d48c` (tag `suggest-poc-frozen-2026-11-16`). Changes to the
method go to the Kotlin core (`app/src/main/java/com/onpath/app/suggest/`) once it is ported (stage 2); until then they
wait. Allowed in Python: the data step (the suggest file per map cell, stage 1) as long as the POC reading it gives the
same suggestions, bug fixes that change no number below (or say so here), tools to compare the port.

## The reference the port must reproduce

The six comparison sets (`official.py compare`, commands in the README; labels `fzf` in
`~/onpath-suggest-poc/official-compare/compare-fzf-<set>-partial.json`, runs in `~/onpath-suggest-poc/runs/`), medians
over the loops, map data of 2026-11-16:

| Set (loops) | first loop on the official | first ≥ 80 % | best shown | best of pool | rank of the official | official 1st | cards |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Traumpfade (21) | 75 % | 10 | 86 % | 87 % | 2 | 9 | 4.8 |
| Traumschleifen (24) | 28 % | 3 | 61 % | 63 % | 2 | 12 | 4.9 |
| RLP cycling / MTB (32) | 11 % | 1 | 20 % | 34 % | 7 | 6 | 4.2 |
| other RLP hiking (24) | 23 % | 2 | 47 % | 64 % | 4 | 7 | 4.6 |
| NRW hiking (25) | 22 % | 3 | 42 % | 53 % | 7 | 4 | 4.6 |
| NRW cycling / MTB (15) | 10 % | 0 | 25 % | 30 % | 3 | 6 | 4.7 |

`cases.py`: **65 of 65** statements (15 cases, as on the page with the official loops near the start).

**Reference runs** (`reference.py`): per wish the whole pool – line (lon / lat), origin, raw place values, five axes,
points, hard-rule reason, chosen, numbers – plus the wish and the place's yardstick (`low`, `high`, `top`, way out).
In git (1.2 MB gzip): `app/src/test/resources/suggest/reference/` – six case starts (hilden-wandern, heide-wandern,
nordkirchen-rad-3h, zons-rad-3h, meran-flach, dhuenn-rad-350) and five official loops' wishes (NRW: Teutoschleife
Dörenther Klippen, Unterwegs in Monschau [35], Naafbachtal-Route; RLP: Traumpfad Saynsteig, [AK4] Blick über
Altenkirchen). All 15 case starts + the five: `~/onpath-suggest-poc/reference/` (2.2 MB, outside git). The runs are
deterministic (same wish, same data → the same file byte for byte).

## Settings that are guesses (first values that passed the checks, not measured)

- `character`: the axis weights at both ends (gemütlich / wild), `depth`, `signed`; `family.*.ground` (ground
  welcome), `up_m_per_h`, `ascent_m_per_h`.
- `search`: `taste`, `road`, `profile`, `used`, `beside_used`, `line`, `radius`, `road_repair`, `climb`.
- `place`: `low` / `high`, `top` (Natur 0.8, rest 0.95 – read off 32 hiking places), `standout`, `min_area`,
  `access_built`, `access_max`, `rim`, `sight`, `rim_natur`.
- `form`: `sight` 0.05, `ring` 0.15 (measured on two sets only), `short_zero`, `long_zero`, `doubled_zero`, `cross`.
- `select`: `behind`, `cover_behind`, `official_ahead` 5, `least` 3 / `least_behind` 30 (`overlap` 0.75 and
  `ring_road_m` / `road_walk_max_m` 300 are the owner's numbers).
- `facts`: the road factors, `calm`, `beside`, `big_water`, the rim heights.

Known weak spots stay as listed in the README ("Weak spots"); they are for the Kotlin core now.

## Python is the lab again (owner 2026-11-17)

Python is the lab again; changes are collected and ported to Kotlin in one batch once the owner accepts them on the
page. Until then parity with the Kotlin core is off for these changes on purpose (`port/parity.sh` against the renewed
reference: the official loops differ; everything else is identical).

### The batch to port (in this order)

1. **2026-11-17, official loops as a ride from the start** (owner's finding: "Bei Nordkirchen, Rad 1,5 bis 2 h …
   ausgeschilderte Rundwege, obwohl sie bis zu 13 km entfernt sind … die Anfahrt einbeziehen"). Before, an official
   loop was laid on from its own start (the parking place) and judged on the loop alone ("Start x km entfernt"). Now
   `official.near` builds it as a ride from the wished start: `assemble` with the loop's turning points (one per
   ~200 m, `trail` / `thin`), from its best joining point (the turning point cheapest by the search cost), round in its
   order (either direction, the shorter kept; pieces our map lacks skipped as in `lay_on`, `follow`), the loop's parts
   on the laid-on cost, the way there and home as for our own loops; a ride meeting its own way more than `max_cross`
   is dropped like ours. Time, km, ascent and the axes are those of the whole ride (the way there is the stem / way out
   as for every loop); in time as our loops. The card says "davon x km auf dem Rundweg" (`official.loop_km` instead of
   `start_km` / `parking` / `p`); no parking line on the map. Kotlin to change: `Official.near` (+ a `trail` / `thin` /
   `follow` split of `layOn`), `Candidate.Official` (name, badge, osm, loopKm). A port was written and showed parity
   (every built candidate identical, cases 69/69) – it is in the history of branch
   `wip/ir-2026-11-17-official-access` (commit 1363cb27), not on main. `lay_on` (the comparison) is unchanged, so the
   six comparison sets above keep their medians (measured again 2026-11-17, label `acc`). `cases.py`: 69 of 69 with
   the new case `nordkirchen-rad-kurz` (bike 1.5–2 h: every card starts at the start and the whole ride stays in the
   wished time; at least one own loop).
2. **2026-11-17 night, the Ziel and aims; quality-trail measures** (owner: the controls Natur / Ruhe / Bekannte
   Wege / "am Wasser entlang" go, the wish gets „Ziel“; Mittenwald: "warum nicht auch den Ferchensee?", "warum geht
   dieser Weg nicht um den Lautersee?"). Branch `wip/ir-2026-11-17-aims`; README "Sixth round". Kotlin to change:
   - `Character` / `Wish`: no `natur`, `ruhe`, `bekannt`, `wasser`; `ziel` (list of gipfel / wasser / aussicht /
     einkehr / kultur); Besonderes × `aims.weight` with a Ziel; `water` = "wasser" in ziel; `path` from the character
     ends (foot only). `settings.json`: `controls` gone; new `aims`, `quality`, `character.*.path`,
     `place.water_natur`, `family.*.few`, `family.*.road_repair_m`.
   - `SuggestFile`: section `AIMS` (content 2: u8 kind of `AIM_KINDS`, name, point, zz height); `Named`: aims as
     things of kind "aim" (not in the index); `build_suggest.aims` is the producer (peaks from base `mountain_peak`
     z12–14, pois: hut, restaurant, cafe, spring, museum – named only).
   - `Facts.width` (path / track / rough track / wide, `of_tags`); `Place`: Weg on foot with the width, Natur with
     `water_natur`, `aims` = `aims.find`.
   - `Generate`: `assemble` / `cutSpurs` / `measure` with `keep` (the way up to a summit stays, is not "walked twice",
     no meeting); the aims generator (alone, a quarter turn either side when near, two in a chain, lake shore cost);
     the "few" retry (`turning` with twice the sectors at three times); road repair on wheels from 1 km.
   - `Score`: `aims.reached` / `aims.worth` on raw Besonderes (at most `most`); `variety` on Natur; `goals`, the
     order and one card per chosen Ziel in `choose` instead of "am Wasser"; the finest aims get a card like areas;
     title and line words for aims (`aim_bits`, `um`); numbers `path_pct`, `variety`, `aims`.
   - The port's fixtures (`port/fixtures.py`) still send the old fields – rebuild them with the batch.
   - Not for the batch: `cells.ensure` asks again for a missing cell after 10 minutes (POC edge only).
- (batch, owner 2026-11-17) card order: loops a few minutes outside the wished time (10 % of the range end, at least 10 min) sort with the ones inside (`score.order`).
