# [donald] Code audit 2026-11-12 (owner asked: clean structure, low cost of change?) – verdict: good, one hotspot. Owner 2026-11-12: yes, after the smooth map – robustness first (the `nav-session.json` race as a JVM test, GPS handling during recording measured; fix only what is confirmed), then slice AppViewModel one piece per round.

- **[donald] Code audit 2026-11-12 (owner asked: clean structure, low cost of change?) – verdict: good, one hotspot. Owner 2026-11-12: yes, after the smooth map – robustness first (the `nav-session.json` race as a JVM test, GPS handling during recording measured; fix only what is confirmed), then slice AppViewModel one piece per round.** `ui/AppViewModel.kt` 5,356 lines
  (4,645 on 11-07), 246 public functions, 87 `mutableStateOf`, not JVM-testable; 29 test files pin its source text. Plan, one
  slice per round, forwarders stay, core features green each step: (1) slices after the `TourController` pattern (small
  interface, fake in tests): Zuletzt, activity check, follow / record, plan sync + plan links, shared places – plus a size
  test that fails above the current line count and is lowered after each slice; (2) the source-text pins of a moved slice
  become behaviour tests; (3) the 32 Android-free files in `ui/` (RouteWarnings, PlanGaps, PlanGuidance, RouteRules …) move
  to `domain/` – breaks the `ui` ↔ `domain` / `routing` cycle, update `app/web-shared-files.txt` (tell laptop: parity);
  (4) lint fails on new issues (baseline 105 stays) – build config, PR; R8 only after a full Rundgang on the minified build;
  (5) per touched file: history comments out (WHY stays), `collectAsStateWithLifecycle`. Also: `RecordingService` 1,777 lines
  (recording + navigation, companion flows). Leave: no DI framework / Room / DataStore, `mutableStateOf` holders, file
  persistence, one app module (+ `:strings`, later maybe `:core`). **Owner 2026-11-12 – night job, his rules:** at night only what nobody sees (tests, inside rebuild), never a new function or a changed screen; checked by machines (gate, E2E on GitHub, release Rundgang on the emulator, a crash hunt) – a piece that fails is taken back, not patched; at most one or two small rebuild pieces per week, the other nights build tests; no new version without his weekly Pixel test („ich habe nur einmal in der Woche Zeit“). Fable builds, Opus reviews each piece fresh. Order: safety net first (the `nav-session.json` race test, broken / missing / half-written files tests, crash hunt with Demo tour + random taps, StrictMode in debug builds = the GPS-on-main-thread measurement, then the JVM harness), rebuild only after that.
  **Second, independent audit the same day (owner: „mit frischen Augen“) – same verdict and same first three steps; it adds:**
  (a) the real knot inside the view model is `recalc()` (~line 2410, ~230 lines): the route run as a state machine in
  Boolean flags – „sheet rises once“ (2a54f2e4) needed a new flag at four distant places → first make the route run one
  explicit state + pure reducer with tests, then slice; (b) a JVM harness for state holders (sqlite-jdbc `TourDb` is in the
  tests already; fake engine + service state) comes before the slices; (c) service ↔ UI by convention: two
  `NavSessionStore` on `nav-session.json` (AppViewModel.kt:949, RecordingService.kt:258), token check at
  RecordingService.kt:997 not atomic (race inferred – settle with a JVM test that interleaves the view model's save and a
  service re-route; core feature, test first), mutable companion flows, ~20 Intent actions → a typed `RecordingClient`,
  the service the only writer; (d) every GPS fix runs on the main looper (LocationTracker.kt:188/296) and `onFix` does the
  navigation maths and `db.appendPoint` there (`writePoints`) – measure with StrictMode in debug / gfxinfo while
  recording, then a single-thread recording dispatcher; (e) screenshot blast radius: 409ba176 = 158 PNGs for +141 code
  lines, `.git` 562 MB → component-level pictures where a full screen is not the point; (f) E2E: 100 sleeps, 20 commits
  with „flak“; (g) tokens: 344 inline fully-qualified names in the view model, `docs/core-features.md` 51 KB.
