# Handoff donald, 2026-11-20 afternoon (for the fresh donald session)

Start: `docs/roles/start.md` → this file → backlog lines for [donald]. Token rules (owner 2026-11-20, captain line on top
of „For the roles“): fresh session at ~350k context; helpers on Sonnet (set `model`), one big-model review per piece;
**no full E2E / Rundgang per small weekend update** – gate + the change's own tests.

## Releases today
- **0.18.2** (52d0eb547, morning, owner's word in donald's chat; full round green, fresh first start on a wiped emulator
  green), planner deployed by the release run.
- **0.18.3** (318182e2a): a point on the routed line splits its leg at once (shared `core/…/routing/LegSplit.kt`). Its
  planner deploy was skipped on a red areas-parity smoke → handed to [laptop].
- **0.18.4** (5df211840, ~14:10): a hold always lands (`PlaceAddSlice.measureHold` decides again on the shown plan),
  a POI from its menu keeps its kind at once, [umbau]'s „Zuletzt updates its entry“. Check the release run
  (`gh run list --workflow release.yml`) and the planner deploy in it; a full E2E on `ci/donald-2026-11-20-rc-0184` ran
  beside it – look at its result once.

## The owner's words in donald's chat (also in memory)
- **Weekend (until Mon 2026-11-23):** his fixes / change requests (via the captain) go out as 0.18.x updates without a
  further question, after gate + the change's tests. After Monday: no release without „prepare for building a release“.
- Pixel (USB 44080DLJH000E5) runs the official 0.18.2 since 10:10 – he updates via the app; not on USB this weekend.
- `CF_START_TOKEN` deleted (his word). The paper try is dropped („gar nicht“).

## Open for donald (top first)
1. **The place menu's silent drop** – `state/…/ui/plan/PlaceMenusSlice.kt:157` drops a menu „not opened“ when the plan
   changed during its measure – same pattern as the hold fix 62e7064c5; test first, ship as an update.
2. **Workflow patch** `tools/ci/pending/e2e-first-map-key-2026-11-20.patch` – a `first-map` E2E job with the TEST key
   running `FirstMapDownloadE2ETest` (on main; skips without the key, green on GitHub). The token cannot push workflows
   → captain / owner opens the PR, owner merges.
3. **Website pictures after 0.18.2–0.18.4** (Next item) – `tools/dev/pictures_affected.sh v0.18.1..v0.18.4`.
4. The rest of „Next“ (tidied today, `docs/team/items/2026-11-20-donald-next-tidy.md`).
- Rule learned: the project settings deny `adb uninstall` / `pm clear` (they protect the Pixel) – a fresh emulator is
  `emulator -avd Rundgang … -wipe-data` (owner's yes 2026-11-20 morning); never a second emulator.
- Branches `ci/donald-*`, `ci/legsplit-app`, `ci/neutral-test-points`, `try/*` are left on GitHub – delete only via
  `tools/dev/prune_branches.sh` after the owner's yes to the list.
