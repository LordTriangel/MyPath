# Handoff IR, 2026-11-17 (morning) – tour suggestions: frozen, suggest file, method ported

Start sentence: „Du bist IR, die Einheit für intelligente Routenplanung (OnPath). Lies docs/roles/start.md und danach
docs/history/handoff-ir-2026-11-17.md.“ The plan: `docs/design/tour-suggestions.md` §7 "Review 2026-11-16" (stages 0–5).

## Where the plan stands
| Stage | State |
| --- | --- |
| 0 Freeze | **done** – tag `suggest-poc-frozen-2026-11-16`, `tools/suggest-poc/FROZEN.md` (reference numbers), `reference.py` + `app/src/test/resources/suggest/reference/` (11 wishes, full pools). No method change in Python any more. |
| 1 Data | **producer done, not in the pipeline** – `tools/suggest_file.py` (format `suggest.bin`, reader), `tools/build_suggest.py` (producer), `tools/suggest_tiles.py`; ~0.2 MB per cell; the POC gives identical results with and without the file. Pipeline patch for the laptop: `tools/cells/suggest-layer.patch` (layer `suggest`, schema 9; 7 tests in `test_cloud.py` and `wrangler.toml` still to adapt). Owner question on the download wave (backlog "Waiting for the owner"). |
| 2 Core | **ported** – `app/src/main/java/com/onpath/app/suggest/` (`TourSuggester.suggest(wish, NetSource, SuggestFiles, SuggestSettings)`), parity tool `tools/suggest-poc/port/parity.sh [graph|reader]`: on the POC's network every candidate identical (score diff 0.0, 18/22 same choice – the rest = guessed gapped "Rundweg" loops not ported + cells without LOOP section); with the app's own NetReader 70–100 % same candidates, 12/22 same choice (tie order, stricter "allowed", no `mainroad_penalty` in app profiles). `cases.py` 65/65 both ways. Desktop: hike 2 h 0.23 s, bike 3 h 1.4–1.9 s (Python ~14 s). Not called by anything. Gate full green. |
| 3 Planer | not started – framework branch `wip/donald-2026-11-14-planner-suggest-remote` (needs ranges + sketch-4 cards, calls `TourSuggester`). TeaVM: the engine builds; the suggest code is not in the Wasm yet because nothing calls it (tree-shaken) – the real Wasm check comes with the WebApi wiring. |
| 4 App | not started |
| 5 All cells | waits (see owner question) |

## Changes to the method in this round (all in the frozen state)
- One selection over own + official loops: ≤ 5 cards, no two sharing > 75 % (`select.overlap`, owner), official +5 in selection.
- At least 3 cards when 3 different acceptable loops exist (`least`, `least_behind`) – Nordkirchen hike 1 → 3.
- Road repair on foot: a loop failing the road rule is rebuilt once with roads ×8 (`search.road_repair`).
- Ascent: one filled field counts (page bug fixed); the maximum is hard for own + official loops; nothing fits → one rebuild with climbing dearer (`search.climb`), then the flattest few with the hint „↑ … m – mehr als gewünscht“.
- Wheels: signed-loop bonus only with ≤ 300 m on a road with cars (`form.ring_road_m`, owner).
- Badge „Ausgeschildert“, always our own text line; OSM descriptions deleted from the loop files (owner, copyright).
- Shorter titles (general rule).
- Yardstick from the place alone (`place.top`: Natur 0.8, others 0.95 quantile beyond the way out); points no longer depend on the other candidates; the special rule for official loops is gone.
- NRW official loops on the page (`loops-nrw.json`), jogging circuits left out.

## For the owner this morning
1. Look at the page (http://localhost:8790, user unit `onpath-suggest-poc-page` – it runs the frozen state): Nordkirchen hike 2.5–3.25 h (3 cards now), Dhünntalsperre 51.07514, 7.20201 bike 2.5–3.75 h „bis 350“ (one card with the hint), Monschau 50.5548, 6.2458 (short titles), Dörenther Klippen 52.2332, 7.7457 (official first). His comments' explanations: this chat / the report below.
2. Decide: suggest file into all cells – no separate download wave (recommendation) vs. now.
3. Note: Traumschleifen median 43 → 28 % from the yardstick change (one loop across the middle; mean unchanged) – accepted for stability, his call if he reads the median.

## Open technical points
- `mainroad_penalty` exists in the POC's profile copies, not in the app's profiles – changing app profiles = a routing change, owner's word needed.
- Hint / reason texts are German literals in the core – need the strings module before the UI.
- Not measured on the Pixel (disconnected overnight): the full method's memory and time.
- Reading on the Pixel: bike 3 h 6.3 s (tag text work per way kind) – the next optimisation.
- Python method to delete and `settings.json` to move into the assets once stage 3 runs (end of stage 2).
- Cleanup: `~/onpath-suggest-poc/suggest-file/` 973 MB of work files (keep `cells/` until the pipeline builds the files).

## Next, in order
1. Owner's look + decision (above).
2. Stage 3: wire `TourSuggester` into WebApi (Wasm check, time in the browser), planner UI per sketch 4 on the framework branch, behind the switch; the laptop's pipeline step with the test cells first so the planner has files.
3. Then stage 4 (app) and 5 (all cells).
