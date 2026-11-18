# Handoff donald, 2026-11-18 (night)

## Owner's words (direct, in donald's chat, 2026-11-17 ~23:10–23:15, „auch wenn ich weg bin“)
1. When the laptop has CELL_SCHEMA 9 on main → start `publish_area.sh` → **started 23:15** (`systemctl --user status
   onpath-germany`, PARALLEL=3 since 01:11, TEST bucket, Nice 19). Status `~/onpath-cells-work/germany/status.txt`;
   failed cells `failed.txt` (10-538-357: DACH extract 6.3 GB > 2.5 GB limit). When it ends: done / failed into the
   backlog, tell IR (Mosel, NRW, Fränkische Schweiz, Mittenwald / Garmisch).
2. „Wenn morgen alles grün ist, veröffentliche die neue Version, damit ich sie per Update aufs Handy bekomme“ → on
   2026-11-18, when gate, unit.yml, E2E on GitHub and the emulator Release-Rundgang are green: `tools/ci/release_check.sh
   --go`, bump version, notes, push (release.yml). Red pieces stay out. The other sessions (mehrtage multi-day,
   IR round-trip suggestions, laptop planner) aim to land their work during the day – the evening candidate includes
   what is green. His Pixel is with him: no USB installs; he gets it as an in-app update.

## Cleanup done (owner via captain, in one go) – all on main, each Fable-built, Opus-reviewed, gate full green
1a JVM safety net (`NavSessionStore.saveIfMine`, broken-file tests, test kit `app/src/test/.../testkit/` incl. the
`OwnComposeSnapshot` rule) · 1b crash hunt `tools/dev/crash_hunt.sh` (0 crashes in 30 000 events) + GPS main-thread
note `docs/design/recording-main-thread.md` (~2 ms/fix) · 2 `ui/` → `domain/` (33 files; gate runs ui tests for
domain changes) · 3 `domain/plan/RouteRun` + reducer · 4 slices `RecentPlans`, `SyncDriver`, `PlanLinkFlow`,
`PlanDocument` (+ `AppViewModelSizeTest`) · 5 `RecordingClient`, `navigation/FollowStore`, read-only service flows ·
6 history comments / inline names. AppViewModel 5,970 → 5,760 lines. E2E: CoreField 35/35, Release-Rundgang 18/18.

## Open
- Lint fails on new findings → build-config PR, proposal in „Waiting for the owner“.
- Hemingway (Hilden) symbol check needs his Pixel (backlog „Next“).
- Follow-ups from reviews: `RouteRequests` unused in app code (shared with the planner – agree with the laptop);
  `StopSkip.SKIP_RATIO` KDoc contradiction; FollowStore holds its lock while writing big follow.json (matters only once
  recording leaves the main thread).
