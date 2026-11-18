# Handoff donald, 2026-11-18 late (for the fresh donald session)

Read first: `docs/history/handoff-donald-2026-11-18.md` (night: cleanup, map rebuild, 0.18.0). Since then:

## Released
- **v0.18.0** (10:23, GitHub Release with APK): multi-day tours, hold rule on real paths, one-row place menu, saved
  places synced, POI names from z17, tour review with Ø speed, map downloads only what changed, the inside cleanup.
  Website pictures retaken (D09, F03, H04, J02; multi-day M01–M03) → onpath.private `aktuell/`, cloud-web told.

## Owner's direct words in donald's chat (also in donald's memory)
- **0.18.1:** „Veröffentliche 0.18.1, sobald die Rundtour für alle eingeschaltet und alles grün ist. Auch wenn ich nicht
  da bin.“ → when [IR] writes „round trips on for everyone – on main“ (IR's card-row-on branch was merged into main
  ~late 2026-11-18; check the line), run gate full, `tools/ci/e2e_shards.sh` (full E2E), emulator Release-Rundgang,
  `release_check.sh --app <last app commit> --go v0.18.1`, bump `appVersion`, notes (delete v0.18.0.md), push.
- **Still needs his word in donald's chat:** `gh secret delete CF_START_TOKEN` (auto mode refused the relayed yes).

## In flight
- **98 % on the phone** (owner, via captain ddc76295 – belongs into 0.18.1): helper branch `worktree-agent-a85f9c28de7519322` „Honest progress, no frozen 98 %“ – every percent that stalls near the end (plan map loading, route calculation, map downloads), JVM test first, then each phase its own progress / a text where nothing is measurable. **Screenshots to the owner first**, then merge, then 0.18.1.
- **Zuhause like Google Maps** (owner chose it 2026-11-18 late): helper branch `worktree-agent-a9bafbf61692b9077`
  (place menu: „Als Zuhause festlegen“ only while none is set; empty search: „⌂ Zuhause festlegen“ or „⌂ Zuhause“ with
  ⋮ Ändern / Entfernen). **Show the owner the screenshots first, merge only after his yes** (sketch-before-build).
  Check whether the planner (laptop) must mirror it.
- **Undo in the full points sheet** (owner's answer): asked [umbau] in „For the roles“ whether `ui/PlanPanel.kt` /
  the Undo pill are free – build when umbau says so.
- **E06 website picture** (pause card) shows the dropped calm line – retake DE + EN (calm line removed, 17b961af0).

## Found, for others
- Multi-day (mehrtage): reopening the same multi-day tour from Meine Touren → Planen shows no day overview
  (`MultiDaySlice` recomputes only when route / overview / marks change) – line in „For the roles“.
- „Tag 2 fahren“ / „Ride day 2“ also for a hike – wording question for the owner (Waiting for the owner).

## Machine notes
- Emulator: `website_shot.sh` uses `-gpu swangle_indirect` and resets size / density; never `-gpu host` (segfaults,
  leaves a `netsimd` that hangs later boots).
- [umbau] moves packages into `:core` – pull before every piece; agree in the backlog before touching AppViewModel.
