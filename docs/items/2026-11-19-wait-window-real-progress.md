# The wait window gets real progress (owner 2026-11-19)

Owner: „Ladefenster – das ist das Erste, was man sieht, wenn man eine neue Map liest, wir brauchen einen realistischeren
Fortschritt.“ … „baue die richtige Lösung vollständig rein, ich brauche die neue Version erst später.“

This replaces the interim form of the 98 % fix for the loading window (the running bar without a number at the end).
The other fixed places of that branch stay; all of them then move to the two forms (order of the same day, "one
progress design, two forms").

## What to build (form A, every wait window)
- **One bar 0–100 over all steps.** It never goes back and never stands. Each step owns the share of time it really
  takes – measured on the phone, not guessed (start values e.g. prepare 30, download 50, join in 20; then learn from
  the last loads on this phone).
- **Every step measures itself:** prepare = the server's own progress / time left; download = real bytes;
  **join in = new: it reports how many pieces are in** (today it reports nothing – that is where the number stood);
  routing data = bytes, then the rewrite reports bytes written.
- **If join-in cannot report cleanly:** estimate that step from the map's size and this phone's measured speed, kept
  inside its own share – say so in the handoff.
- **Steps as a small list with ticks** under the bar: „✓ vorbereitet · lädt … · einfügen“; routing data, when missing,
  is a fourth step in the same list.
- **Time left** („noch etwa 20 Sekunden“) as soon as it can be estimated.
- Route calculation is form A too (owner: „besser eher zu A als den Benutzer zu verunsichern“): legs as the steps.

## Rules
- A test first that reproduces a standing number per step (the existing `PercentStallTest` grows).
- Pictures of the window (DE, normal + large font) to the owner before it lands.
- [laptop]: the planner's wait window gets the same bar, steps and texts.
- 0.18.1 waits for this.
