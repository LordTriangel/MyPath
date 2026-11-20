# After 0.18.2 is out – the plan (owner 2026-11-20 morning, asked one by one in the captain's chat)

Starts the moment donald has published 0.18.2 and the laptop has deployed the planner. Until then main stays frozen
for app / core / state code.

## Right after the release (no question needed)
- [captain] lifts the freeze line in „For the roles“.
- [laptop] deploys the planner, then merges `wip/laptop-2026-11-20-slices` (60 → 55 mirrors).
- Owner merges pull request 16 ([umbau]'s three test-input lines in `state/build.gradle.kts`).
- Owner says „Secret löschen ja“ in donald's chat → [donald] deletes the GitHub secret `CF_START_TOKEN`.
- [donald] the owner's Pixel back to the release build (the paper try is dropped – owner: „gar nicht“), when he says.

## The owner's answers to the night's questions – to build
- [umbau] **Zuletzt: a changed draft updates its entry** – the planner's rule for app + planner (`RoutesState`); the app
  changes (no new draft + old one to the bin any more). With the planner block below.
- [donald] the app **cuts a leg for a point / night put on the routed line** (as the planner does since this night).
- [donald] **test places stay, exact start coordinates move to neutral points** (not at a door) – code + tests.
- [laptop] long multi-day plans, the three speed-ups of `2026-11-20-add-point-speed.md`: (1) planner now, (2) stitch
  within ~2 km and (3) long halves in pieces – app + planner, with the „same route everywhere“ check.
- [umbau] the test-cut list (layout / colour pins, thinner screenshots) to the owner – he decides.
- [donald] a pull request that gives ONE E2E class the TEST map key, so the first map download on a fresh install is
  tested on GitHub (workflow change = owner merges).

## The big blocks – order and how many at once (owner)
1. **First: the planner on the shared code** – [laptop] + [umbau], mirrors 55 → under 15. One writer at a time in `web/`.
2. **In parallel only: live tracking** – [tracking] builds from `docs/design/live-tracking.md` (owner's answers in §12 and
   the night answers: battery level yes, name asked once, may stay empty).
3. **Waiting** (owner: save tokens, the weekly limit warns): rebuild phase 3 (small) / phase 4 ([umbau]), the routing
   engine port to Kotlin ([umbau-hp]), the multi-day day rows wish (no session for it yet). The captain asks the owner
   for the next block when 1 is done.

## Tidy the „Next“ list (owner: yes)
Each role goes through its own „Next“ items once after the release: done → out, overtaken → out (git keeps them); the
captain then orders the rest to at most ~25 and shows the owner the top 10.

## Decided, no work now
- **Map server for the friends:** the test server is enough for the weekend. The production server (per-phone quota,
  supporter code, key rotation – `docs/design/map-access.md`) stays part of the public launch with Google Play; its
  questions come then.
- **Pictures to look at** (planner wait window, round-trip card, route preview „Mehr“): closed – the owner sees them in
  the app and says if something should change.
