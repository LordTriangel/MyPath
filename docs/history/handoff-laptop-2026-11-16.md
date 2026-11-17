# Handoff laptop – 2026-11-16 / 17 (night, owner asleep: „alles ohne Rückfragen, eigene Entscheidungen notieren“)

## Owner decisions (2026-11-16, chat)
- All map cells keep every signed route: **yes** – on main 0f7030c2 (CELL_SCHEMA 8, overlays-only partial rebuild),
  TEST deployed; donald rebuilds overnight with `publish_area.sh` (owner's idea; word in the backlog).
- Local route names on the map: **no**. CI-smokes PR: **later**.
- Production map environment is not set up – everyone uses TEST (my earlier „start Maps deploy → production“ was wrong).

## Live in the planner (2026-11-16)
- Found place's pin + name chip never under the footer/credits on a phone (menu room ends above the footer, chip
  moved clear on close, z-order) – 06e33d12.
- donald's six shared `suggest/*.kt` files registered in parity.json (main's Web planner run was red on it).

## My own decisions tonight (owner asleep)
1. **POI click right after a map move** (search-smoke 1 in 3): treated as a real bug, not a flaky test – a visible
   restaurant icon did not answer a click (MapLibre's symbol placement lags a move). Fixed: a click waits for the
   map's `idle` (≤ 1 s) and looks again (443fd70d). Mirrors the app's „POIs read at camera idle“.
2. **Hold rule F-PLAN-15 + „a bare name is no POI“ (C19)** – donald's word: built in the planner via the shared
   rule through the engine, not copied (agent running).
3. **Kept places sync (donald's §5.4 format)** – web side built tonight with the guard „never export from an
   unreadable store“ (agent running).
4. **Suggest file (IR's patch, schema 9) – NOT pushed / NOT deployed tonight.** Reason: the order the owner set
   („test cells first, all cells after IR's check“) and the open owner question on the extra download wave; also
   donald's s8 run is going tonight – an s9 bump now would make the whole rebuild start again. Built on a branch +
   test cells only; the owner decides in the morning.
5. Branch cleanup (owner's yes 2026-11-15): done – four remote wip branches deleted via `prune_branches.sh` (it now
   falls back to GraphQL when the search API is rate-limited). The local branches `laptop-base-1113` and
   `laptop/ci-smokes-2026-11-13` stay – deleting local branches is denied to me; harmless.
6. **Suggest layer (schema 9)** built on `wip/laptop-2026-11-17-suggest-layer` with 7 test cells
   (`onpath-data/store-test-routes/.../2026-11-15-s9/`), acceptance replayed: data equal, loops 229/229. Not on main.
7. **Found while checking: every schema bump makes phones re-download whole cells** (9–23 MB per refreshed cell):
   the app never reuses unchanged files of the installed version. This also holds for the s8 bump I pushed yesterday –
   I told the owner „the map stays the same, +5–10 %“ but did not say that each refreshed cell is downloaded whole once
   (only cells a phone refreshes: after routing, „Karten jetzt aktualisieren“, a class mismatch – not by looking at the
   map). Proposal to donald in the backlog: reuse files with equal sha256 → later waves tiny.

8. **Deployed 2909bd34 (hold rule F-PLAN-15, bare name ≠ POI, kept places sync with the phone, click fixes)** after a
   green local check-web – but GitHub CI then showed two regressions on its slower machine (robust-smoke: two quick
   clicks in the wrong order; parity-smoke: a click on the line not put into the leg). robust-smoke was not in
   check-web.sh – I add it. Decision: fix forward at once (night, few users) rather than roll back; if the fix takes
   long I roll the planner back to 0f18a0c6.
   Result: no page bug – both CI failures were tests (robust-smoke clicked past the goal, which now asks by the hold
   rule; parity-smoke clicked into a map note while the first-route fit moved the map). Tests fixed (8976753b,
   22f292d8), check-web now runs nowebgl/robust like CI. The live planner stays as deployed.

9. **„Plan request failed (Failed requirement)“ found and fixed** (88ddec64): the page could send a plan with 0/1 point
   when the points changed while an older recalculation was still waiting (folder switch, Clear, New plan). Only the
   newest run plans now; shared `Planner.kt` got a message for its `require` (donald told).

## Open for the owner (morning)
- Hold rule past the goal (with donald's first item): with the band of f12e8d83 a silent new goal happens only exactly straight on past the goal – in practice every hold past the goal asks, with „Neues Ziel“ first, in app and planner alike (the planner follows the app's shared rule).
- Suggest file into all cells (schema 9): yes / when? Each refreshed cell is downloaded whole again (9–23 MB), not
  only the 0.3 MB file – unless donald's app first reuses unchanged files (one app release). Recommendation: app fix
  first, then s9; or s9 now if the extra download is fine (only refreshed cells, once).
- Everything else: see `handoff-laptop-2026-11-15.md` §5 (CI smokes PR later).
