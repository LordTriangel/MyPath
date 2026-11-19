# Handoff IR, 2026-11-15 (evening) – tour suggestions; the owner's end of day, nothing runs

IR = the unit for intelligent route planning (tour suggestions), a session on donald. Start sentence: „Du bist IR, die
Einheit für intelligente Routenplanung. Lies docs/roles/start.md.“ Then read this file,
`docs/design/tour-suggestions.md` (the method, the owner's rules, §7 the planned build) and `tools/suggest-poc/README.md`
(section "Official loops" and the weak spots).

## What happened today
1. **Compared with official circular routes** (owner: „Gleich der Vergleich“): `tools/suggest-poc/official.py`
   (extract OSM loops from a Geofabrik file, compare our suggestions with them, pictures). Sets: 21 Traumpfade
   (Mayen–Koblenz), 24 Traumschleifen (Hunsrück), 31 cycling / MTB loops, 24 random others – all Rheinland-Pfalz.
2. **Improved from it** (on main): follow a signed route all the way round even with gaps; +0.15 Besonderes for going
   round a signed loop; less "deep in the forest" at the gemütlich end; **Kante mit Blick** from `terrain.pmtiles`;
   castles / ruins / chapels / viewpoints / caves / waterfalls as Besonderes; Besonderes 0.18 → 0.29; rough ground
   welcome 0.1 → 0.3; `surface` (Wegbeschaffenheit) removed from the wish.
   Result, our first loop on the official one (median): Traumpfade 48 → 81 % (practically it: 5 → 11 of 21),
   Traumschleifen 26 → 43 %, others 23 % (best shown 45 → 53 %), wheels 11 → 12 %. `cases.py` 54 of 54.
   Reports: `~/onpath-suggest-poc/official-compare/REPORT.md`, `REPORT-2.md`; pictures `pictures/`, `pictures-2/`.
3. **The POC page** (`python3 tools/suggest-poc/serve.py` → http://localhost:8790, `/vergleich` = the comparison):
   official loops as cards in the same list (Rheinland-Pfalz and Nordrhein-Westfalen: `loops.json`, `loops-nrw.json`
   in `~/onpath-suggest-poc/official-compare/`, 1 798 NRW hikes + 171 wheel loops; jogging circuits left out), a text
   line per card, calm map. Stopped at the end of the day (`systemctl --user stop onpath-suggest-poc-page`; start again
   with the `systemd-run` line from `docs/history/handoff-donald-2026-11-14.md`).
4. **Phone measurement** (`tools/suggest-poc/bench-phone/`, on main): Pixel 8 hike < 1 s, bike 3 h ~4 s; reading through
   BRouter's objects peaks at 230 of 256 MB → owner: **option 1** (the port reads the network straight into compact arrays).
5. **Laptop built test cells with every signed route per way** (layer `routelines`, on the laptop only; `named.py`
   reads it, cache now attr-v7). With them, built suggestions contain the Traumpfad at 19 instead of 7 of 21 places
   (measured with the old method).
6. **Sketches:** sketch 3 (picture page in the owner's chat) (three variants), sketch 4
   (picture page in the owner's chat) (A as chosen: calm map, text on the cards).

## The owner's decisions today (all in the design doc)
- "Rundtour von hier" appears wherever "Hier starten" appears.
- No Wegbeschaffenheit – the activity (Rad, Gravel, MTB, Wandern T1–T5) says it. No road-test access needed now.
- Official loops (Traumpfade logic) rank above his own comments in `cases.py` (the Hilden protected-area check dropped).
- Map cells: every signed route per way – test cells first (done by the laptop); all cells only on his word.
- Result list = sketch variant A: one list, official and ours mixed, sorted by "passt zum Wunsch"; badge shows the
  origin; the same five Werte; an own loop ≥ 80 % on an official one is simply not shown (no note).
- Calm map: one line style for all; the chosen loop clear; all others only a breath.
- A short text per card on what is special (replaces "stark bei … · schwächer bei …").
- Memory on the phone: option 1 (compact arrays when reading, no map change).
- Build the foundation now (owner: „Ja, jetzt anfangen“) – see "Unfinished".

## Waiting for the owner (also in the backlog)
1. **Bikes and roads:** official cycling loops run 28 % on roads / through towns; the "round a signed loop" bonus puts
   such a loop first (e.g. "Blick über Altenkirchen", 957 m on a road). Recommendation: for wheels the road rule beats
   the bonus. Look first at Kirchberg (Hunsrück) or "Radweg Rund um Hilden" on the page.
2. **Copyright:** OSM geometry / names are fine (ODbL, the app credits OSM already). Recommendation: in the app never
   show OSM description texts (often copied from the operators' websites) – our own generated line instead; badge
   „Ausgeschildert · Traumpfad“ instead of „Offiziell“ (a brand name, no partnership); never GPX / photos / texts from
   operators' websites. Not legal advice – check before release.
3. **All map cells with every route** (cloud rebuild of the overlays layer, ~4 min per cell, on demand).

## Unfinished
- **Core in the app code** (owner: build the foundation now): branch `wip/ir-2026-11-15-suggest-core`, commit
  `9d2ddb55`, **only local on donald** in the worktree `.claude/worktrees/agent-af2ffdb17390c1f47` (not pushed: the
  gate is red). Contents: `app/src/main/java/com/onpath/app/suggest/` (NetReader, WayNet, NetSearch, WayKinds,
  RouterProfile, LongIntMap), tests `NetReaderTest`, `NetSearchParityTest` + `test/resources/suggest/net-parity.txt`,
  `tools/suggest-poc/bench-phone/NetBench.kt`, `tools/suggest-poc/port_parity.py`. **It changes
  `routing/BRouterEngine.kt`** (the key values moved out for sharing) – a core-feature file: review that change hard,
  better leave BRouterEngine as it was and add a new function beside it. Gate: `NetReaderTest` and
  `UserTextRuleTest` fail, screenshot differences (probably from main moving on, check). Not measured yet; TeaVM not tried.
- `wip/donald-2026-11-14-suggest-next` (Rhine from Zons, Hilden ring): still untested, partly superseded by today.

## Next, in order
1. Finish the core branch: undo the BRouterEngine move, make the gate green, measure memory (bike 3 h target ≪ 100 MB)
   on the desktop and the Pixel, TeaVM compile check, then merge.
2. With the owner's answers: the bike road rule; copyright wording.
3. Weak spots: long protected-area titles (Monschau), Marienberg regression, wheels barely better, the river at Zons,
   MTB network gaps; compare in NRW (the owner knows it best) – a NRW comparison set with `official.py compare`.
4. Port the method (facts, place, character, generate, score) into the core once it stands; planner first (branch
   `wip/donald-2026-11-14-planner-suggest-remote`, needs the ranges and the sketch-4 cards), then the app.
