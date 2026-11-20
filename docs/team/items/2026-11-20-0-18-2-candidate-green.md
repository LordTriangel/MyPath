# 0.18.2 release candidate – all green (donald, 2026-11-20 night)

- Release commit **fdd5534f4** = main fa9031fe0 + version 0.18.2 + notes (`docs/release-notes/v0.18.2.md`), on branch
  `ci/donald-2026-11-20-rc2-full` (and `-rg`). Not on main – pushing it to main IS the release.
- Full E2E **100/100** (incl. FreshInstallE2ETest, RoutingDataFreshInstallE2ETest), Rundgang **18/18** (run 44635990691),
  `release_check --go v0.18.2` **GO** (app fa9031fe + planner 917e65fc).
- Main since the candidate: planner + docs only, no app / core / state / strings change.
- Not covered: a real first map download on a fresh install (GitHub's E2E builds have no map key; clearing the app on
  donald's emulator was refused without the owner's word).
- Flow 17 red once on the first candidate – judged flaky (night answers).
- To publish (owner's own word in donald's chat): rebase fdd5534f4 onto main, gate, push → release.yml; then tell [laptop].

## Re-made ~01:00 with 399e55b6d (C13 race fix, 00:36) – the earlier candidate missed it
- Release commit **230dbc8b0** on `ci/donald-2026-11-20-rc3-full` / `-rc3-rg` = main bb3918384 + version + notes. Full E2E 100/100, Rundgang 18/18 (run 44640223959), release_check GO (app 399e55b6 + planner fa407ebc). fdd5534f4 is superseded.
- unit.yml (unit + lint) green on exactly 230dbc8b0: run 44652334317 (dispatched on `ci/donald-2026-11-20-rc3-full`, 02:20).

## Fresh first start on a wiped emulator (owner's yes 2026-11-20 morning) – green
- Emulator reset with `-wipe-data` (no OnPath on it; the deny rules for uninstall / pm clear stay), the candidate 230dbc8b0 as a debug build with the TEST map key, position Meran.
- Setup 5 steps (location, notifications, vibration, voice) → while the user answers them the first map of the area (17 MB) and the routing data (14 MB) load in the background – done before „Close“; the map shows with detail at once. „Used OnPath before?“ question then.
- A new place (Innsbruck via search): the wait window „Map for your area“ 71 % → 86 % → done in ~10 s, steps „prepared · loading map + routing data“, „about 5 seconds left“, Cancel; the detailed map shows. A route (Start here + hold): 107 m, routed at once, „Offline ✓“.
- Problems log: 6 entries, no WARN / ERROR. Pictures: donald `~/onpath-cells-work/fresh-start-2026-11-20/`.
- Small wording note (not a blocker): the window says „Map for your area“ also for a searched place far away.
