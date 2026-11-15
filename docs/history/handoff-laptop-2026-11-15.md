# Handoff laptop – 2026-11-15 (evening)

Start here next time: pull, read this, then `docs/backlog.md` (lines tagged [laptop]). Earlier days:
`handoff-laptop-2026-11-14.md`, `handoff-laptop-2026-11-12.md`.

## 1. Live on plan.onpath.earth (deployed by hand, `web.yml` deploy quick)
- Place menu like app 0.17.10+: „Einfügen“ / „Neues Ziel“ both always, the likelier first and tinted, one line each, no
  „⋯ Mehr“; „Neues Ziel“ appends (old goal → stop). A POI tap shows its details also with a menu open / on a route point
  (in the menu head – the planner has no details sheet).
- Meine Touren facts „km · ↑ · time“ like the app; unsaved = points only (an activity change is no change; Save still saves it).
- Browser recycle bin (owner decisions below): saved routes deleted without a paired phone go there (30 days,
  restore with same id/name/star, „Name (2)“ if taken); no twin draft when a plan has the points of a saved route.
- Dev server (`npm run dev`) no longer crashes (saveState TDZ); host test D1 stand-in binds `?NNN` portably
  (likely cause of donald's „column index out of range“ – not reproduced here; donald should rerun sync-smoke).

## 2. On main, deploy pending at the end of the session
- **Kept places in the planner** (app b2504c3d): „☆ Merken / ★ Gemerkt“, „⌂ Als Zuhause festlegen“ in the place menus;
  empty search: Zuhause, Favoriten (5 + „Alle Favoriten ›“), Zuletzt gesucht (new in the planner). Browser-only store
  `web/app/src/keptplaces.ts` (localStorage, the app's SearchStore line format) – ready for sync. **Shared code:**
  `search/SavedPlaces.kt` added to `app/web-shared-files.txt` (engine `WebKept.kt`). Saved also on
  `wip/laptop-2026-11-15-kept-places`. If the deploy below did not run: `gh workflow run web.yml --ref main -f deploy=true -f quick=true`.

## 3. Map pipeline – test cells with every signed route (code on main c78215c2, OFF by default)
- `extract_overlays.py --all-routes` / `ONPATH_ALL_ROUTES=1` → new tile layer `routelines` (one line per route
  relation); `routes` unchanged; no style reads it yet. Test cells only on this laptop:
  `onpath-data/store-test-routes/cells/f2/10/` (531–533/345–346 Traumpfade, 531/342 Hilden). official.py: the pool
  has the official loop (≥ 80 %) at 19 of 21 (was 7). Map draws identically. Overlays +5–10 %.

## 4. Owner decisions today (2026-11-15)
- Browser bin: **no „Jetzt löschen“** (entries go after 30 days); **saved routes deleted unpaired → browser bin**;
  bin button **only while it holds something**; **no twin draft** (like the app). Paired: a browser delete goes to the
  phone's bin (unchanged).
- **Kept places sync with the planner** („Ja, mit Abgleich“) – the format is donald's (backlog word to him).
- POI details in the menu head and the bottom card beside the sheet on wide screens: recommended to keep; no objection.
- Branch cleanup: **yes** to pruning wip/laptop-2026-11-13-sync-pace, wip/laptop-2026-11-14-search-menu,
  wip/laptop-2026-11-14-favourites, laptop/ci-smokes-2026-11-13 (local), local laptop-base-1113 – not done: GitHub's
  search API was rate-limited, so `prune_branches.sh` could not check for open PRs. Run it next time; also
  wip/laptop-2026-11-15-kept-places once main is deployed.
- Standing rules learnt (memory): every visible app change goes to the planner – read donald's code first, ask when
  unsure; the planner has NO large route overview; visible UI glitches are fixed at once, not parked.

## 5. Open for the owner
1. **All cells with every route?** (turn on `ONPATH_ALL_ROUTES=1` in the cloud job → all cells rebuilt).
2. **Show local route names** (e.g. „Traumpfad …“) on the map from `routelines`? A style decision.
3. **CI smokes PR**: `tools/ci/pending/web-yml-ci-smokes-2026-11-13.patch` – `git am` on a branch, open the PR (the
   laptop's token cannot push workflow files), then delete the patch.

## 6. Open for the laptop
- Kept places sync once donald defines the format.
- routes-smoke step 4b precondition flake (backlog).
- Map pictures on weak phone browsers unmeasured; round trip + „Neues Ziel“ appends after the closing point (untested).
- Stash entry „laptop-a5188-mine“ (6abbcd6f) in the shared stash list – applied, drop by hand.
