# [umbau-hp] release hardening for 0.18.2 – start brief for the fresh session

The job (captain, owner's word for the night of 2026-11-19 → 20: a clean release candidate for phone + planner in the
morning): **review everything changed since tag `v0.18.1` (app + planner) by risk, find bugs before friends get the
link, and write a behaviour test for every risk that has no guard.** Tests and small fixes only. A fix in planner code
(`web/`) → first a line to [laptop] in the backlog; in the rebuild's area (`state/`, `AppViewModel`, module build) → a
line to [umbau]. Questions: answer them yourself, one line each in `docs/items/2026-11-20-night-answers.md`. Findings
under "For the captain". The routing port (5b) waits until after the release.

Start: pull, read `docs/roles/start.md`, `docs/owner-preferences.md`, `docs/core-features.md` (index), the top lines of
"For the roles". Work in the worktree `.claude/worktrees/umbau-hp` (branch `umbau-hp-work`, rebase on `origin/main`
first; `local.properties` is there). Gradle only through `tools/dev/gradle_slot.sh`, `tools/ci/gate.sh` before a push.
Tokens (captain's rule): Sonnet helpers read and write the bulk in their own worktrees, one fresh Opus review per piece,
the lead only cuts, checks and merges.

## Where the risk is (measured 2026-11-19 22:55, `git diff --stat v0.18.1..main`: 129 commits, 82 product files)

Order = the captain's list, core features first; behind each the files that changed most.
1. **Recording / the tour** – `state/…/ui/tour/TourSession.kt` (new, 391), `TourEdits.kt` (new, 364), `TourEndSlice.kt`,
   `MainActivity.kt`, the fixes L1 (a throwing collector pass), L2 (a failing read of the active tour at start), L5
   (the service learns an activity switch). Moved code + three behaviour fixes in the code that must never lose a track.
2. **Map download** – "My routes' pictures: never kept without their map" (field report 0.18.1), `MapHost.kt`, the
   multi-day download fix in `MapCells.request` (was a real bug behind a flaky test).
3. **Routing / the route run** – `state/…/ui/plan/PlanRecalcSlice.kt` (new, 461; the start guard moved),
   `MultiDaySlice.kt`, `PlanRunSlice` (run handle as a port).
4. **Plan sync** – `[umbau]` reported an old race (a browser plan being routed on the phone can overwrite a save made
   meanwhile, `docs/items/2026-11-19-umbau-to-laptop-a-browser-plan.md`) – not fixed; check whether it can lose a plan.
5. **The new wait windows** – app `ui/progress/SuggestWait.kt` (round-trip search waits with Abbrechen), planner
   `web/app/src/loadoverlay.ts` (431 changed), `waitengine.ts`, `web/engine/…/WebWait.kt` (new, 364). Timing: cancel
   during a wait, a late answer after cancel, two waits at once, "ready in 5 s" then hanging (München → Hilden).
6. **Round-trip cards** – `ui/suggest/SuggestSheet.kt` (a tap grows the card), `SuggestSlice`.
7. **„Mehr“** – `ui/RouteMore.kt` (new, 305), `DoneTourOverview.kt`, core `TourReview` (pause times).
8. **Meine Routen pictures** – page dots, the pictures made again after 0.18.1.
9. **Planner search** – the place filter bug class (a late online answer for another text or chip): look for the same
   pattern elsewhere – every place where an answer arrives after the question changed (search, routing, suggestions,
   map notices, sync), in the app and the planner.

## How (proposal)
- Three or four Sonnet readers in parallel, one per group (1–2, 3–4, 5 + 9, 6–8), read-only: each returns a list
  "risk → why → is there a test that would fail → proposed behaviour test", with file:line, no fixes.
- The lead picks by risk, helpers write the tests in worktrees (flow tests in `:state`, planner tests in `web/`), each
  test must fail without the guard it protects (prove it once by breaking the code locally).
- A bug found: a failing test first, then the small fix or the line to its owner.
