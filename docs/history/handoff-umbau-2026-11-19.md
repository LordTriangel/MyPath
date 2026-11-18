# Handoff [umbau] – 2026-11-19 ~03:00 (the rebuild, `docs/design/architecture-plan-2026-11.md`)

Start sentence: „Du bist die Einheit **umbau** (OnPath) auf donald. Lies `docs/roles/start.md`, `docs/owner-preferences.md`,
`docs/design/architecture-plan-2026-11.md`, `docs/design/umbau-phase-1.md` und diese Datei.“ Rules of the job: the top
„For the roles“ line of 2026-11-18 (owner's go) – each piece own worktree, `gate --full`, fresh Opus review, then main;
a tag `umbau-phase-N` per phase after unit CI + planner run + full E2E; lines for the captain under „For the captain“.

## Done (on main)
- **Phase 0:** E2E nightly + 4 emulators (pull request 13); backlog slim + cap in the gate (`tools/dev/backlog_slim.py`,
  cap 40,000, target 20,000 once read role lines are deleted); core-features index; 350k handoff rule; 0b sleeps →
  `waitUntil` / `pause` (6 real waits, 114 named pauses).
- **Phase 1 piece A** (63b56ac93): hub splits – 213 of 231 candidate files can move.
- **Row T** (test clean-up, owner 2026-11-18 night): `docs/design/test-audit.md` + `.tsv` – verdict per test file.

## Open, in this order
1. **Tag `umbau-phase-0`:** needs one green full E2E run of main: push main's head to `ci/umbau-p0`, then
   `tools/ci/e2e_github.sh --ref ci/umbau-p0 --wait` (no `--tests`; a comma list of tests runs only the first). Red today
   only by `TourDbMigrationTest` (schema 9 needs its case – line to mehrtage) and the flaky `OverviewSwipeE2ETest`.
   Old `ci/umbau-wait*` branches: remove with `tools/dev/prune_branches.sh` (own ci branches, no owner question).
2. **Piece B** (`:core` module, `:strings` jvm target, `tools/dev/core_move.py`, first 57 files + 27 tests): branch
   `worktree-agent-a97ded78f641e807e` (worktree `.claude/worktrees/agent-a97ded78f641e807e`). Reviewed: FIX FIRST – the
   helper is on the fixes (rebase; `web/engine/core-shared.lock` + rule test because web.yml's cache key does not know
   `core/`; Android-API guard for `:core` (Animal Sniffer, API 26); tool traps: no test class into testkit, resource
   folders, web-listed tests; small things). When it reports: check the list is done, `gate --full` yourself on top of
   main, `-p web/engine test generateWasmGC`, `node web/parity/check.mjs`, push. A second short Opus look at the fix
   commits only (lock test, sniffer wiring, tool changes).
3. **Workflow patch 2** with piece B: local branch `umbau-workflow2` (this worktree) = `/tmp/core-paths-lint-2026-11-19.patch`
   (may be gone – recreate: `core/**` in unit.yml + web.yml path filters and web.yml's cache key, `core/build/reports/tests/`
   in the unit report, lint `abortOnError = true` + `continue-on-error` out). Put it in `tools/ci/pending/` and ask the
   captain for the pull request (the token here has no `workflow` scope). After its merge: delete the lock file + its test.
4. **Piece C:** the big move with `core_move.py` (plan said 119 files now; add the pure non-candidates `data/MultiDay`,
   `poi/PoiKinds`, `data/LastActivity`, `data/TourPlan`, `data/FollowFile`, `recording/AscentCounter` to the hubs if they
   are Android-free → most of the rest follows). Before each package moves: sort its tests by `test-audit.tsv` (DROP /
   CONVERT groups of that package; a field-bug test is never dropped; C18 saving needs `SaveFlowTest` first). In batches
   by package, each batch gate + review. `RecomposeProbe` / `ScaleRule` (internal, used by ui) stay in `:app`.
5. **Piece D** with the laptop: `web/engine` compiles `core/src/main/kotlin` as a source folder; list shrinks;
   `SharedFilesRuleTest` + the border test of `SharedCoreRuleTest` go; stand-ins `Problems`, `UserText`, `GpsStatus`,
   `DataFiles`, geojson go. Line to the laptop is in the backlog (they may take or review it).
6. **Piece E:** checkpoint – unit CI, planner run, full E2E, tag `umbau-phase-1`; delete `umbau-phase-1.md`.
7. **Phase 2** (`:state`, AppViewModel slices): write „phase 2 starts“ under „For the roles“ first. donald builds „Undo in
   the points sheet“ and IR's cards row is on main – both should be in before. Then phases 3, 4 (4b screenshots is
   offered to [umbau-hp] after its spike; lint-fails-on-new is in workflow patch 2).

## Things to know
- Push from this worktree: `git push origin HEAD:main`; the hook wants `tools/ci/gate.sh` green on HEAD first. Backlog
  edits: run `python3 tools/dev/backlog_slim.py` before committing. Unpushed work sits on the worktree branch; for a
  docs-only push beside it I used a temp branch `umbau-tmp2` from origin/main (branch deletion is denied – reuse it with `-B`).
- Helpers: Sonnet in own worktrees, Opus reviews read-only; their worktrees stay until merged. Gradle only via gate /
  `tools/dev/gradle_slot.sh`.
- Night watch: the captain asked for an hourly self wake-up „on the owner's word“; not set, because that word is not
  in the backlog (wake-up-off is the owner's rule). Set it when the line is there or the owner says so here.
- Spike 5a is [umbau-hp]'s; my helper's start is `wip/umbau-2026-11-18-kn-spike`.
- Waiting for the owner: may owner-decision pins be folded into flow tests (row T).
