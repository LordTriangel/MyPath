# Handoff IR, 2026-11-19 – tour suggestions are live (on by default), ready for a fresh session

Start sentence: „Du bist IR, die Einheit für intelligente Routenplanung (OnPath). Lies docs/roles/start.md und danach
docs/history/handoff-ir-2026-11-19.md.“ The topic's single source: `docs/design/tour-suggestions.md` (§1–5 method and
the owner's rules, §7 every decision with date, the stages and what exists in code). Nothing of IR runs.

## State
- **Method:** Python POC `tools/suggest-poc/` = the lab (page `python3 tools/suggest-poc/serve.py` → :8790, user unit
  `onpath-suggest-poc-page` – stopped or running, harmless); `cases.py` 83/83; comparison sets `official.py compare`
  (Traumpfade, Traumschleifen, RLP wheels, random, NRW hikes / wheels); official loops of RLP, NRW, Bayern in
  `~/onpath-suggest-poc/official-compare/loops*.json`. Rule (owner 2026-11-17): try in Python, the owner looks on the
  page, accepted changes go to Kotlin in one batch with parity (`tools/suggest-poc/port/parity.sh`, reference outputs,
  `FROZEN.md` batch list – empty now).
- **Core:** `app/src/main/java/com/onpath/app/suggest/` (TourSuggester, Aims, Facts, Generate, Place, Score, Official,
  SuggestFile, SuggestCards + pills, `suggest/app/` SuggestService / Runner / Fetch / AppSuggest), shared with the
  planner (`app/web-shared-files.txt`). Desktop JVM: hike 2 h 0.21 s / 24 MB, bike 3 h 2.0 s / < 96 MB live.
- **Data:** suggest file per cell (`suggest.bin`, content 2 with AIMS and the signed loops), CELL_SCHEMA 9, all
  Germany built on TEST (616 cells; 10-538-357 failed).
- **Planner:** `web/engine/.../WebSuggest.kt` + `web/app/src/suggest.ts/.css`; **App:** `ui/suggest/`. Both: on by
  default (off-switch in the debug settings / `?debug`), „Rundtour von hier“ only while setting the first point
  (map tap or a symbol's menu), wish = sketch 5 B, cards = horizontal swipe row (sketch 8) with pills (sketch 7, owner's
  words), only the chosen loop on the map, „Übernehmen“ → a round-trip plan (`planFromLine`).
- **Website:** README paragraph corrected; pictures N01–N04 (DE + EN) in onpath.private `marketing/bilder/aktuell/`
  (46e28fb), line → [cloud-web].

## Open / next
1. The owner's field look in the released app / deployed planner (the laptop deploys; donald releases).
2. Not measured on the Pixel: the full method's time and memory in the app (bike 3 h). Measure on his next connect.
3. Browser memory: the worker holds rd5 bytes twice (as for routing) – the laptop re-measures after deploy.
4. Weak spots (README of the POC): river following (Zons), MTB network gaps, every named hill counts as a summit, every
   restaurant as Einkehr, raw names from the map, the way out of town (Marienberg).
5. `suggest-smoke.mjs` into `web.yml` – a PR (workflow file), the laptop's call.
6. The [umbau] session moves files between modules (phases 0–4): pull before every piece, no feature work in
   `AppViewModel` / `ui/` state during its phase 2.
