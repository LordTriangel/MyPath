# Handoff donald, 2026-11-20 night (for the next donald session)

## Ready to publish: 0.18.2
- Candidate **230dbc8b0** on `ci/donald-2026-11-20-rc3-full` = main bb3918384 (with the C13 fix 399e55b6d) + version 0.18.2 + notes
  (`docs/release-notes/v0.18.2.md`, v0.18.1.md deleted). Full E2E 100/100, Rundgang 18/18, release_check GO.
  Detail: `docs/team/items/2026-11-20-0-18-2-candidate-green.md`.
- **Publish only on the owner's own word in donald's chat** („veröffentlichen“) – a relayed word is not taken (night
  answers). Then: rebase fdd5534f4 onto main (check that no app / core / state / strings code came in since 399e55b6d –
  else one more E2E + Rundgang), gate, push → release.yml; watch the planner deploy job (it stopped at parity after
  0.18.1 – `node web/parity/check.mjs`); tell [laptop].
- Owner's release rule (since 2026-11-19): no release / prep without „prepare for building a release“.

## On the owner's Pixel (USB serial 44080DLJH000E5, unplugged for the night)
- Debug build of branch `try/paper-warm` (Paper `#F2EFE6`, warm, ~10 % darker) over 0.18.1 – data kept (debug and
  release share one certificate). Not on main, not in 0.18.2 – into a release only on his word (then one more E2E round).
  Before / after: onpath.private `ideen/2026-11-20-papier-dunkler/`. MyPath's palette (`try/alpenrand`) was too close.

## Done 2026-11-19 (all on main)
0.18.1 released (round trips for all, progress design A/B, Undo in the points sheet, „Tag 2 starten“, Zuhause hidden);
after it: My routes' pictures redrawn when their map changed, access note gone, page dots + „Mehr“, round-trip search in
the wait window, round-trip card V1 (grows in place). Four flaky / red tests fixed; a real double map move fixed.

## Open for donald
- Owner's question still open: may donald clear the app on its own test emulator for a real first-map-download check.
- After the release: a pull request giving ONE E2E class the TEST map key (`e2e.yml`) for a real first-download test.
- „Abstecher“ (short reroute on long plans) went to the planner – not donald's.
- Website pictures after the release (`tools/dev/pictures_affected.sh v0.18.1..v0.18.2`).
- Old lines for donald in the backlog (hold during first route, POI kind remembered) – judge.
