# donald (fresh session) – state 2026-11-19

All three pieces are on today's main as one commit each, `tools/ci/gate.sh --full` green, only the branch pushed:

| Piece | Branch | Commit | Touches [umbau]'s freeze |
|---|---|---|---|
| Kein Stehen bei 98 % | `wip/donald-2026-11-19-honest-progress` | a07af6144 | yes: `AppViewModel.kt` (routeShare KDoc, `bar.now()`, `routeShare = null`), `ui/MapDataFlow.kt`, `ui/RoutingLoads.kt`, `ui/BackgroundDownloads.kt`; `core/domain/PlanStatus.kt` (lock rewritten), `LoadSmoother` deleted, `web/parity.json` |
| Zuhause wie Google Maps | `wip/donald-2026-11-19-zuhause` | 5a958c25f | yes: three forwarders in `AppViewModel.kt` (size stays 3,901), `state/…/KeptPlacesSlice.kt`, `ui/search/SearchSession.kt`; `core/domain/PinMenuPlace.kt` (lock rewritten) |
| Undo im vollen Punkte-Fenster | `wip/donald-2026-11-19-undo-sheet` | ae22c2083 | no – Compose only (`MapFooter`, `PointsSection`, `PlanPanel`, `MapScreen`), calls `vm::undoStep` / `vm::redoStep` |

- **98 % side effect for the planner:** `WebPlanProgress` uses `PlanStatus.calculating`, so after the last leg the planner also says „Route wird geprüft…“ instead of „Abschnitt N von N“ → a line to [laptop] at merge.
- **Zuhause:** the pictures are byte-identical to the old branch's, so the owner's picture page from 2026-11-18 still holds. At merge: a line to [laptop] (planner mirrors `web/app/src/pinmenu.ts`, `main.ts`, `search.ts`, four strings).
- **Undo:** the title row scrolls with the list – on a long list, scroll up to reach ↶ ↷. The points-list E2E still uses the map's Undo (not switched).
- **0.18.1:** the round trips are on for everyone on main (72a66e39). Still blocked: (1) `TourDbMigrationTest` red on main (asserts schema 8, it is 9 – [mehrtage]); umbau asks for a green full E2E of main + Rundgang first, because this is the first release with `:core` / `:state`. (2) The owner's word in the fresh donald chat. (3) Note umbau's open question to the owner: no release between phase 2's tour pieces and his weekly Pixel tour (tour step 16a is already on main).

## Later the same day – 0.18.1 released (dccc49789)
- Owner in donald's chat: „baue weiter, offene punkte selber klären, erzeuge neues release wenn alles fertig“; the
  captain relayed: build the progress design on own judgement (pictures afterwards), Zuhause off – final.
- Progress design (A wait window: one bar, ≤3 steps, time left, „dauert etwas länger…“; B: one ring, one % for the
  whole job) – built, independently reviewed (map writes unchanged; 7 promise findings fixed with `WaitPromiseTest`),
  pictures in onpath.private `ideen/2026-11-19-fortschritt/` („Gebaut“). Planner mirror → `2026-11-19-progress-design-on-main.md`.
- Undo in the full points sheet (50ed2c16d), „Tag 2 starten“ (e9bc74244), Zuhause hidden (0579439f6, `HOME_ROW_SHOWN`;
  branch `wip/donald-2026-11-19-zuhause` parked), TourDbMigrationTest 8→9 (f58951c6f), hold-measure E2E accepts the
  timeout fallback (79dd1d816), OverviewSwipe E2E any total (4f4ec3795), found-place menu moved the map twice (074cb525a).
- Checks on the release commit: full E2E 99/99, Rundgang 18/18, release_check GO, web.yml green.
- Left `ci/donald-2026-11-19-*` and `ci/donald-2026-11-19-release-0181*` branches on GitHub (deleting needs the owner's
  yes via `prune_branches.sh`).
