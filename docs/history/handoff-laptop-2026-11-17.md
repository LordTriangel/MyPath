# Handoff laptop – 2026-11-17 late (context nearly full – the next laptop session starts here)

Read first: the top lines of `docs/backlog.md` „For the roles“ (captain 49750e83 ~23:20 = the order for tomorrow
evening's owner goal), then this page, then `handoff-laptop-2026-11-16.md`.

## Owner's goal (captain 2026-11-17 ~23:20)
Tomorrow evening a new version on the phone AND in the browser: multi-day tours + round-trip suggestions, with the new
maps. Laptop's order: (1) hold-rule mirror in `web/` → Web planner run green → write „done“ in the mehrtage line
(→ [laptop] [IR] [donald] (mehrtage …)) so mehrtage + IR can build in `web/`; (2) CELL_SCHEMA 9 (suggest layer + aims,
content 2): test cells first, published to TEST (NRW incl. Hilden / Nordkirchen / Monschau, Mosel–Traumpfade,
Fränkische Schweiz / Pottenstein 49.77, 11.41, Mittenwald / Garmisch), tell IR; donald then rebuilds all cells
(fast lane); (3) deploy the planner whenever its tests are green (`gh workflow run web.yml --ref main -f deploy=true -f quick=true`).

## Work in flight at the end of this session (two agents, worktrees under `.claude/worktrees/`)
1. **Hold rule on real paths + one-row place menu + WebPlaceMenuTest** (donald's words 2026-11-17 in the backlog:
   `pathDetours` with leg lengths, `planningHold` simplified (≤ 1.2 → ASK), `planningPlaceMenu(lineDp, d)`, pin menu
   with ONE add row, names from z17 = no code change). Worktree `agent-a0ec544b55a26bdcc`, branch
   `worktree-agent-a0ec544b55a26bdcc` – no commit of its own yet when this was written. If the agent is gone: redo from
   donald's backlog lines (they hold exact code hints).
2. **Suggest layer s9 rebase onto main (AIMS, content 2)** – worktree `agent-aec6f4aef384aee8d`, branch
   `laptop-suggest-layer-rebase` (64f1aa4d schema 9 layer, 5f67799b docs). Still to do there: rebuild the 7 test
   cells with content 2 in `onpath-data/store-test-routes`, acceptance (`SUGGEST_FILE=1 SUGGEST_FILES=… cases.py`,
   `official.py compare --name Traumpfad`), the publish plan for the owner's test areas. Pushing it to main deploys
   the TEST doorman with CELL_SCHEMA 9 (owner said yes: „Normale Kartenschicht“, phones reload once – donald also
   builds the app's „reuse unchanged files“ fix).

## Main's Web planner run is RED until (1) lands
`WebPlaceMenuTest.besideTheGoalAsksTheCheaperFirst` (old rule) + parity: new shared files (PlanDays, Aims,
SuggestText, strings_suggest, PlanCodeDaysTest, PlanDaysTest, PlanMarksFollowTest) and the MapOverlays / AppViewModel
mirrors. After merging (1): `node web/parity/check.mjs --update` (bare – registers the new shared files; only once
every mirror error is really handled), full `web/parity/check-web.sh <segments> <store>`, push, deploy.

## Rules learnt today (also in memory)
- No Fragenregister any more; answer in the chat only. A word for the captain goes under „For the captain“ – never
  ask the owner to relay. Before touching `web/app` / `WebApi` write a line in the backlog (several sessions now:
  donald, IR, mehrtage, laptop, cloud-web).
- Multi-day tours are mehrtage's; the laptop owns `web/` and merges in order.

## Data on this laptop
Segments `/home/development/Projects/work/onpath-data/segments`, cell store `…/onpath-data/store`, test cells
`…/onpath-data/store-test-routes` (s8 routelines + s9 suggest test versions), venv with pyosmium
`…/onpath-data/test-routes-venv`.
