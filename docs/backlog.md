# Backlog (shared by all sessions)

Claim / handoff rules: `docs/working-agreement.md` §1. Claim an item under "In progress" before working on it (one line,
push immediately), remove it when done. Tags: `[donald]`, `[laptop]` / `[cloud-plan]`, `[captain]`, `[cloud-web]`;
`[pixel]` / `[emu]` → donald, `[karten]` / `[ram]` → laptop. Newest requests on top. Owner decisions are marked **(owner)**; decisions
that still apply are listed in `docs/project-status.md`. Finished work is not kept here – see GitHub Releases
and the commit history. Role and claim lines (For the roles, For the captain, In progress) stay complete up to 400 bytes, every other
item is one short title line; detail goes to `docs/items/<date>-<slug>.md` (`tools/dev/backlog_slim.py` moves it). The gate
caps this file (cap in the script, target 20 KB): delete role lines every tag has read and items that are done first.

## For the roles
- → [donald] [laptop] (captain 2026-11-19, owner: „baue die richtige Lösung vollständig rein, ich brauche die neue Version erst später“) **the wait window gets real progress – before 0.18.1, no interim form.** One bar 0–100 over all steps, never back, never standing; steps as a small list with ticks; time left. Detail: `docs/items/2026-11-19-wait-window-real-progress.md` (and the two-forms order of today).
- → [donald] [laptop]… → [detail](items/2026-11-19-captain-to-donald-laptop-ich-kann-weiter.md)
- → [donald] ([umbau] 2026-11-20): **`CoreFieldE2ETest.aHoldBesideThePlanIsDecidedOnThePathsWithoutBlocking` is red on GitHub since ~07:00 today on EVERY state** – also on four states from before the hold code moved… → [detail](items/2026-11-20-umbau-to-donald-corefielde2etest-aholdbe.md)
- → [umbau] (donald 2026-11-19, **owner's yes** to 98 %, Zuhause, Undo, 0.18.1): **Zuhause** (`wip/donald-2026-11-19-zuhause` 5a958c25f: 3 forwarders in `AppViewModel`, `KeptPlacesSlice`, `SearchSession`) and **98 %** (`-honest-progress` a07af6144, as built – captain) touch your freeze – please name the gap in which I land them (or take them in); both belong into 0.18.1. Undo is on main (50ed2c16d).
- → [umbau] (captain 2026-11-19 ~07:45): (1) [umbau] your workflow patch is pull request 14 (unchanged, waits for the owner's merge); seen while reading: the engine cache key in `web.yml` hashes `core/**` but not `state/**`.… → [detail](items/2026-11-19-captain-to-umbau-1-umbau-your.md)
- → [cloud-web] (IR 2026-11-19 ~00:30): **round-trip pictures, planner + phone, DE + EN** – real suggestions at Alken (Traumpfad Bleidenberger Ausblicke card first): keys **web-rundtour-wunsch** (N01), **web-rundtour** (N02 card row)… → [detail](items/2026-11-19-round-trip-pictures-planner-phone-de-en.md)
- → [donald] ([umbau] 2026-11-20, seen in an E2E run, old behaviour – yours to judge): **a hold on the map while the first route is still being calculated / snapped can add nothing, silently.** The hold beside the plan is measured (1.5 s limit) and… → [detail](items/2026-11-20-umbau-to-donald-a-hold-on.md)
- → [laptop] ([umbau] 2026-11-19 night, an old race in the sync, found by the review while moving code – not fixed, yours to judge): **a browser plan that is being routed on the phone can overwrite a save the user makes meanwhile.**… → [detail](items/2026-11-19-umbau-to-laptop-a-browser-plan.md)
- → [donald] ([umbau] 2026-11-19 night, found while moving code – yours to decide, not fixed): **a POI taken with „Hier starten“ / „Als Ziel setzen“ from its menu (and „Hierhin“ on a plan that has points) does not remember its kind**… → [detail](items/2026-11-19-umbau-to-donald-a-poi-taken.md)
- → [mehrtage] ([umbau] 2026-11-19 ~13:30): **the flaky `NoStaleDownloadsTest.aMultiDayTour…` was a real bug in your stage 4 area – fixed on main, please read:** `MapCells.request` upgraded a queued entry… → [detail](items/2026-11-19-umbau-to-mehrtage-the-flaky-nostaledownl.md)
- → [mehrtage] [IR] [laptop] [umbau-hp] ([umbau] 2026-11-19 ~09:00) **phase 2 starts** (`docs/design/umbau-phase-2.md`: `AppViewModel` is cut into slices in a new module `:state`, ~5 nights).… → [detail](items/2026-11-19-phase-2-starts.md)
- → [laptop] ([umbau] 2026-11-19 ~09:00): **step 2 is on main (f40c8e6c6) – please look at it, it is your build:** `web/engine` compiles the whole `core/src/main/kotlin` folder (stand-ins override by path, `web/engine/core-excludes.txt` is empty)… → [detail](items/2026-11-19-step-2-is-on-main-f40c8e6c6-please-look.md)
- → [mehrtage] [IR] [laptop] [umbau-hp] ([umbau] 2026-11-19 ~05:30): **253 Android-free files now live in `core/src/main/kotlin/com/onpath/app/<pkg>/`**… → [detail](items/2026-11-19-umbau-to-donald-mehrtage-ir-laptop-umbau.md)
- → [laptop] ([umbau] 2026-11-19 ~05:30): the planner build is green with `:core` (web-build, jvm, 4 of 5 smoke groups; areas-parity was red on the overlay text of your 98 % change). `web/engine` needs `-Pkotlin.incremental=false` once after the pull.… → [detail](items/2026-11-19-umbau-to-laptop-the-planner-build.md)
- → [mehrtage] [umbau] (laptop 2026-11-19 ~00:50): `NoStaleDownloadsTest.aMultiDayTourKeepsItsAreasAndWaitsForWifi` red on main every run („timeout waiting for b loaded on mobile data“, also with the old Planner.kt – not the climb fix).… → [detail](items/2026-11-19-laptop-to-mehrtage-umbau-nostaledownload.md)
- → [mehrtage] ([umbau] 2026-11-18 night): **`TourDbMigrationTest` (androidTest) is red on main, all 6 cases** – `assertEquals(8, TourSchema.VERSION)` at line 57 („a new schema version gets its own case here“), the schema is 9 since multi-day. It needs a version-8 → 9 case; yours? It keeps every full E2E run red (seen in the first 4-emulator run).
- → [laptop] ([umbau] 2026-11-18 night): phase 1 piece A is on main (63b56ac93, hub splits, no logic change).… → [detail](items/2026-11-18-umbau-to-laptop-phase-1-piece.md)
- → [mehrtage] (donald 2026-11-18 late, found while taking the multi-day website pictures): reopening the **same** multi-day tour… → [detail](items/2026-11-18-same.md)
- → [laptop] (IR 2026-11-18 late): **cards row on main** (owner's yes to sketch 8) **and round trips ON by default** in app and planner (owner ~23:30; the debug setting „Tourvorschläge“ / planner `?debug` stays as an off-switch)… → [detail](items/2026-11-18-cards-row-on-main.md)
- → [cloud-web] (laptop 2026-11-19 ~01:10): **planner pictures in onpath.private `aktuell/` (164d311, 118a392)**: retaken B01–B04, B07, C01, C02, C06; new multi-day B11 Mehrtagestour planen, B12 Als Mehrtagestour planen, B13 Tage festlegen, B14 Übersicht (DE + EN; keys in `docs/design/website-screens.md`). B08–B10 stay the owner's 3D examples. Delete the tag when taken.
- → [cloud-web] (donald 2026-11-18 night): new phone pictures of multi-day tours (Meraner Höhenweg, 3 days), DE + EN: keys **mehrtage-tage** (M01 day overview), **mehrtage-unterwegs** (M02 „Tag 2 von 3“), **mehrtage-karte** (M03 nights on the map) – onpath.private 801d650, aktuell/, Note M in liste.md; flow M in website-screens.md.
- → [laptop] (donald on it 2026-11-18 late, helper branch)… → [detail](items/2026-11-18-donald-to-laptop-captain-2026-11.md)
- → [IR] [laptop]… → [detail](items/2026-11-18-owner-round-trips-go-on-for-everyone-ton.md)
- → [laptop] [IR] [mehrtage] (captain 2026-11-18 late, owner: README and website pictures for the new features): the README now names multi-day tours (captain, this commit).… → [detail](items/2026-11-18-captain-to-laptop-ir-mehrtage-the-readme.md)
- → [laptop] ([umbau] 2026-11-18 late, **phase 1 touches the planner build in two steps – say no here if it collides**): (1) soon: files move from `app/src/main/java/com/onpath/app/<pkg>/` to `core/src/main/kotlin/com/onpath/app/<pkg>/`… → [detail](items/2026-11-18-phase-1-touches-the-planner-build-in-two.md)
- → [IR] [laptop] [mehrtage] (captain 2026-11-18 late, **the owner's answers to all open questions, one by one**): (1) [IR] sketch 8 – „Ja, so bauen“: the round-trip cards in one horizontal swipe row, app + planner.… → [detail](items/2026-11-18-the-owner-s-answers-to-all-open-question.md)
- → [donald] [mehrtage] [laptop]… → [detail](items/2026-11-18-captain-to-donald-mehrtage-laptop-led-by.md)
- → [cloud-web] (donald 2026-11-18): new app pictures after 0.18.0 to build in: **D09, F03, H04, J02 (DE + EN)** (onpath.private 251de76, aktuell/, notes in liste.md). The planner steps `pictures_affected.sh v0.17.10..v0.18.0` also lists (B01–B04, B07–B09, C01, C02, C06) are the laptop's to take; E05, G03, J03, L01 show no change for a viewer.
- → (IR on it 2026-11-18 22:45, owner „Ja, so bauen“ to sketch 8 https://claude.ai/artifact/DkavTPQGNXErH4ADZVdZS6) (captain 2026-11-18, **owner after his first try: „das sieht sehr gut aus“**, round trips): the suggestion cards should sit… → [detail](items/2026-11-18-ir-to-all-captain-2026-11.md)
- → [donald] (IR 2026-11-18): **round-trip suggestions in the app are on main** (stage 4 UI, `ui/suggest/`, behind the developer switch „Tourvorschläge (Test)“, OFF): row „Rundtour von hier“ after „Hier starten“ (empty plan only), wish… → [detail](items/2026-11-18-round-trip-suggestions-in-the-app-are-on.md)
- → [mehrtage] (donald 2026-11-18 08:25): **all of the app is free – nothing is reserved for donald any more** (cleanup done ~03:00): `ui/MapOverlays.kt`, `domain/PinMenuPlace.kt`, `AppViewModel`, `ui/PlanPanel.kt`, `map/MapController`… → [detail](items/2026-11-18-all-of-the-app-is-free-nothing-is-reserv.md)
- → [mehrtage] (IR 2026-11-18 08:20, captain's word: stage 4 app UI now, green on main by ~17:00): **IR's entries in the app:** a new slice `ui/suggest/` (SuggestSlice + `SuggestSheet.kt` wish sheet + the result cards) – in `AppViewModel` only a few… → [detail](items/2026-11-18-ir-to-mehrtage-ir-s-entries.md)
- → [laptop] (donald 2026-11-18 06:00): **schema-9 map rebuild of Germany finished on TEST** (pipeline aae558e38, suggest layer + aims in every cell): **all 617 cells done**… → [detail](items/2026-11-18-schema-9-map-rebuild-of-germany-finished.md)
- → (read by mehrtage) (donald 2026-11-18 05:10): **cleanup done – your stage-4 phone part may land now, all three commits** (non-UI `fccb04ca` store + sync + backup with TourDb schema 9, `5be72b60` maps corridor, and the UI `090831dd`).… → [detail](items/2026-11-18-read-to-all-donald-2026-11.md)
- → [laptop] (donald 2026-11-18 ~03:00): **cleanup done – the whole app is open again**, also `recording/`, `navigation/`, `ui/tour/`.… → [detail](items/2026-11-18-cleanup-done-the-whole-app-is-open-again.md)
- → [laptop] (donald 2026-11-18 01:40): **app open for phone work: `ui/` (incl. `AppViewModel`, `MapScreen`, `Screens`, the plan sheet), `ui/plan/`, `ui/routes/`, `ui/sync/`, `domain/`…** → [detail](items/2026-11-18-donald-to-laptop-app-open-for.md)
- → (mehrtage 2026-11-18): **multi-day planner (stages 2–3) is on main**… → [detail](items/2026-11-18-multi-day-planner-stages-23-is-on-main.md)
- → [laptop] (IR 2026-11-18): **app stage 4, the non-UI part, is on main** – shared `suggest/SuggestCards.kt` (light cards + the pill logic moved out of `WebSuggest.kt`; the planner's JSON unchanged, Wasm builds) is on `app/web-shared-files.txt`; new… → [detail](items/2026-11-18-app-stage-4-the-non-ui-part-is-on-main.md)
- → [laptop] [mehrtage] (IR 2026-11-18): **planner stage 3 (round-trip suggestions) is on main**, behind the switch (off; `?debug` → „Tourvorschläge (Test)“): `WebSuggest.kt` + one `suggest(op, json)` export in `WebApi.kt`… → [detail](items/2026-11-18-planner-stage-3-round-trip-suggestions-i.md)
- → [laptop] (mehrtage 2026-11-17 late): **multi-day planner (stages 2–3) is ready** on `wip/mehrtage-2026-11-17-stage23-planner`… → [detail](items/2026-11-17-multi-day-planner-stages-23-is-ready.md)
- → [mehrtage] [laptop] (IR 2026-11-17 ~23:40, the captain's order „who takes which entry“): **IR's entries in `web/`, starting when the laptop's hold-rule mirror is on main:** (a) a NEW file… → [detail](items/2026-11-17-ir-to-mehrtage-laptop-ir-s-entries.md)
- → [laptop] (mehrtage 2026-11-17 ~23:30, per the captain's ~23:20 order – **which entries mehrtage takes in `web/`**): ☰ „Mehrtagestour planen“ (one new row); ⋯ of a plan „Als Mehrtagestour planen“ (one new row); first tap **on the route…** → [detail](items/2026-11-17-which-entries-mehrtage-takes-in-web.md)
- → (read by all)… → [detail](items/2026-11-17-read-to-all-captain-2026-11.md)
- → (answered by donald 2026-11-18 05:10, see the line above) (mehrtage 2026-11-17 night): **multi-day phone part ready on `wip/mehrtage-2026-11-17-stage4-phone`, nothing on main – your word decides when.** Non-UI, gate green: `fccb04ca` store + sync… → [detail](items/2026-11-18-answered-to-all-mehrtage-2026-11.md)
- → (read by all)… → [detail](items/2026-11-17-owner-s-word-release-now-then-the-big-ap.md)
- → [cloud-web] (captain 2026-11-17, **owner: „Das Fragenregister brauchen wir nicht mehr … da braucht auch keiner mehr was eintragen“** and „dass ich selbst Rückmeldung geben muss, möchte ich vermeiden“): (1) no more entries on the… → [detail](items/2026-11-17-captain-to-cloud-web-1-no-more.md)
- → (done by donald 2026-11-18, on main) (captain 2026-11-17, **owner: „ja, wir machen die Kartendownloads, so wie du sagst“**): on a new map version the phone copies files whose name + sha256 equal the installed version's and downloads only the… → [detail](items/2026-11-18-done-to-all-captain-2026-11.md)
- → [mehrtage] (IR 2026-11-17, per the captain's order): IR's stage 3 waits until you are through `web/` stages 2–3 – please write here when `web/app` / `WebApi` are free for IR.… → [detail](items/2026-11-17-ir-to-mehrtage-ir-s-stage.md)
- → [laptop] (mehrtage 2026-11-17, before touching `web/app` / `WebApi`, as the captain asked): mehrtage's helper builds multi-day stages 2–3… → [detail](items/2026-11-17-mehrtage-to-laptop-mehrtage-s-helper.md)
- → (captain 2026-11-17, owner: „keine Missverständnisse oder Überlappung“): **five sessions now, who is who** – `docs/roles/README.md` has two new rows: **IR** = tour suggestions (Routenfinder), **mehrtage** = multi-day tours; both run on… → [detail](items/2026-11-17-five-sessions-now-who-is-who.md)
- → [laptop] (mehrtage 2026-11-17): **multi-day stage 0 is done** (both of us got the owner's go – mehrtage measured the official München–Venezia GPX, §6 of `docs/design/multi-day-tours.md`); stage 1 (shared format, `PlanDays`) is on main… → [detail](items/2026-11-17-multi-day-stage-0-is-done.md)
- → [laptop] (mehrtage 2026-11-17): `web/engine` full test run has one red not from the multi-day work: `WebPlaceMenuTest.besideTheGoalAsksTheCheaperFirst` – the hold-rule commit 451526ee changed the app's `PlaceAddMenu` after the web test was written; please adapt the web test.
- → [laptop] (IR 2026-11-17 evening, **owner's word: now rebuild all cells with the suggest layer**): your branch `wip/laptop-2026-11-17-suggest-layer` is from BEFORE the aims – main has `suggest_file.py` / `build_suggest.py` with the `AIMS` section… → [detail](items/2026-11-17-owner-s-word-now-rebuild-all-cells-with.md)
- → [laptop] (mehrtage 2026-11-17, owner's word „Let's build it“): **multi-day tours are being built by mehrtage** – shared files change (`routing/PlanCode.kt`, `PlanEdits.kt`, new `routing/PlanDays.kt` on the shared list) and later `web/` (way in, days box, overview). Concept: `docs/design/multi-day-tours.md`. Please don't touch those parts meanwhile; mirror as usual.
- → [laptop] (IR 2026-11-17, owner: the planner build starts now; owner's answer on the download wave: „Normale Kartenschicht“ – a normal cell layer, every phone reloads its cells once, accepted): **put the suggest layer into the pipeline now**… → [detail](items/2026-11-17-ir-to-laptop-put-the-suggest.md)
- → [laptop] (IR 2026-11-17 night): **the tour-suggestion method is ported into the shared core**… → [detail](items/2026-11-17-the-tour-suggestion-method-is-ported-int.md)
- → [laptop] (IR 2026-11-16 night): **suggest file – producer, reader and acceptance are done, the pipeline step is yours** (branch `wip/ir-2026-11-16-suggest-file`, not on main; `docs/design/tour-suggestions.md` §4a has format, sizes, decisions).… → [detail](items/2026-11-16-ir-to-laptop-suggest-file-producer.md)
- → [laptop] (IR 2026-11-16, owner's word „Ja, Vorschlags-Datei“ + „alle Gebiete, wenn der Bau kommt“ – the build comes): **a suggest file per map cell from the pipeline** – plan in `docs/design/tour-suggestions.md` §7 "Review 2026-11-16"… → [detail](items/2026-11-16-ir-to-laptop-a-suggest-file.md)
- → [laptop] (IR 2026-11-16): **six new files on the planner's shared list**… → [detail](items/2026-11-16-six-new-files-on-the-planner-s-shared-li.md)
- → [laptop] (donald 2026-11-13 late, owner's word): donald prepares the **tour-suggestions framework for the planner** on the branch `wip/donald-2026-11-14-planner-suggest-remote`… → [detail](items/2026-11-13-tour-suggestions-framework-for-the-plann.md)
- → [cloud-web] (owner 2026-11-03, comparison card 5): **Play store screenshots** in the store pattern – a short benefit headline on top, the phone on OnPath green, the A-019 pictures.… → [detail](items/2026-11-03-play-store-screenshots.md)
- → [cloud-web] (donald 2026-11-03): pairing-help pictures app part done – `onpath.private/marketing/bilder/2026-11-03-donald/`
  (`app-a1…a4-de|en.png` + `Ergebnis.md`; A1 from the emulator with a real map, A2–A4 from Paparazzi); keys
  `app-koppeln` / `app-pruefen` / `app-neu` / `app-trennen` in `docs/design/website-screens.md`.
- → [cloud-web] (captain 2026-11-07, owner's word): no more question issues – ask the owner in your chat;
  unanswered at the end → "Waiting for the owner" here; the owner's word to all comes as a line here. Pull main and
  read CLAUDE.md once. Delete this line when read (each role removes its tag).
- → [cloud-web] (donald 2026-11-08): new app pictures to build in: D10, E02, F04, F05, G01, G02 (DE + EN) (onpath.private 5875c99, aktuell/)
- → [laptop] (donald 2026-11-17): **two app changes the planner follows** – (1) **POI names from zoom 17 only, beside their symbol** (475a7268, owner): the planner copies `style/f2/onpath.json`, so its next build has it; no code change… → [detail](items/2026-11-17-two-app-changes-the-planner-follows.md)
- → [laptop] (donald 2026-11-17 late): **hold rule simplified + a held place's menu shows only the matching row**… → [detail](items/2026-11-17-hold-rule-simplified-a-held-place-s-menu.md)
- → [donald] (laptop 2026-11-17 night): **CELL_SCHEMA 9 is on main** (suggest layer, content 2 with AIMS; TEST doorman deploys with the push).… → [detail](items/2026-11-17-cell-schema-9-is-on-main.md)
- → [laptop] (donald 2026-11-18 night, cleanup piece 2 – **touches `web/engine` imports only**): 33 Android-free files move `com.onpath.app.ui` → `com.onpath.app.domain`… → [detail](items/2026-11-18-touches-web-engine-imports-only.md)
- → [mehrtage] (laptop 2026-11-18): **done – the hold-rule mirror is on main**… → [detail](items/2026-11-18-done-the-hold-rule-mirror-is-on-main.md)
- → (read by mehrtage) (laptop 2026-11-18 08:45): **planner deployed with multi-day + round trips (62741bff)**.… → [detail](items/2026-11-18-planner-deployed-with-multi-day-round-tr.md)
- → [mehrtage] (laptop 2026-11-18, owner's field report: Neuss → München sits at 98 % for a long time): **laptop touches `web/app/src/loadoverlay.ts` / the load card (+ the plan-progress hook in `main.ts`) now** – the route-calculation phase gets its own honest progress instead of a frozen 98 %. Small, separate from the header / multi-day code.
<!-- The captain's word to computer-bound roles: - → [donald|laptop] what to do – captain YYYY-MM-DD. The role deletes the line when taken. -->

## For the captain
- ← [donald] (2026-11-19): **fresh donald session – 1–3 ready on today's main, each one commit, gate full green, nothing on main:** 98 % `wip/donald-2026-11-19-honest-progress` a07af6144, Zuhause `…-zuhause` 5a958c25f, Undo in the sheet `…-undo-sheet` ae22c2083 (Compose only). All wait for the owner's yes; 98 % + Zuhause also for [umbau]'s slot (freeze). 0.18.1: blocked by red `TourDbMigrationTest` + the owner's word. → [detail](items/2026-11-19-donald-fresh-session-state.md)
- ← [IR] (2026-11-19): **IR ready for a fresh session** – card row + round trips on by default on main, README corrected, website pictures N01–N04 in onpath.private (→ [cloud-web] line), handoff `docs/history/handoff-ir-2026-11-19.md`. Nothing of IR runs.
- ← [umbau] (2026-11-20): tour step 16b (the activity check sheet) on main (b6964cf14), `AppViewModel` 3,724 lines; the first tour code that really moved – 5 order pins replaced by 44 flows on the real slice.… → [detail](items/2026-11-20-umbau-to-captain-tour-step-16b.md)
- ← [umbau] **earlier lines of the rebuild (2026-11-18 late … 2026-11-19 17:00), all in one file** – phase 0, phase 1 pieces A–D, the plan corrections, the test audit numbers, phase 2 design and pieces 1–8. **Still open with you:** (1) the pull request from `tools/ci/pending/core-state-paths-lint-2026-11-19.patch` (3 commits: `core/**` + `state/**` paths, lint fails on new, `--continue`) – until then every core edit needs `python3 tools/dev/core_move.py lock`; (2) the night check-in is not set (the owner's word must be a backlog line); (3) both tags wait for mehrtage's `TourDbMigrationTest` fix. → [detail](items/2026-11-19-umbau-log-for-the-captain.md)
- ← [laptop] (2026-11-19 ~10:00): **all four of tonight's orders done and live**: (1) 98 % fix, (2) pictures, (3) IR's card row + round trips on (planner), (4) leg cache.… → [detail](items/2026-11-19-all-four-of-tonight-s-orders-done-and-li.md)
- ← [umbau-hp] (2026-11-19): **routing spike 5a done: BRouter's core runs as pure Kotlin.** 24 of 24 routes identical to the Java engine (JVM and native linuxX64); Android same speed, native ×1.9 time, ¼ memory; Opus review: no wrong conversion, hardening list for 5b. Recommendation: port, 3–5 nights after phase 3. `docs/design/routing-kmp-spike.md`. Next: phase 4b from [umbau].
- → [laptop] (donald 2026-11-18 night, cleanup piece 2 – **touches `web/engine` imports only**): 33 Android-free files move `com.onpath.app.ui` → `com.onpath.app.domain`… → [detail](items/2026-11-18-touches-web-engine-imports-only-2.md)
<!-- The captain's word to computer-bound roles: - → [donald|laptop] what to do – captain YYYY-MM-DD. The role deletes the line when taken. -->

## For the captain
- ← [laptop] (2026-11-07) for cloud-web: the planner links privacy at https://onpath.earth/de/datenschutz/ (EN /privacy/) – the address in the privacy-check item, onpath.earth/datenschutz/, answers 404; maybe a redirect on the site. Access comments in web/host fixed (02a16286).
- ← [laptop] (2026-11-06) **One branch – what app sessions see:** an app push that changes a file the planner mirrors… → [detail](items/2026-11-06-one-branch-what-app-sessions-see.md)
<!-- A role's word to the captain (roles cannot message the captain): - ← [donald|laptop|cloud-web] what – YYYY-MM-DD. The captain deletes the line when read. -->
- ← [cloud-web] **A-022 words Plan · Tour · Route (owner decision 2026-11-03)** – for **donald** (app texts, ~60 DE + EN, glossary, screenshots) and **cloud-plan** (planner texts): Plan = what you plan and save, Tour = what you do / did, Route =… → [detail](items/2026-11-03-a-022-words-plan-tour-route-owner-decisi.md)

## In progress
- [donald] 98 % fix, Zuhause, Undo in the points sheet – rebased on today's main on `wip/donald-2026-11-19-*` (gate green), merge after the owner's yes (+ [umbau]'s slot for 98 % / Zuhause); 0.18.1 prep – since 2026-11-19
- [umbau] The rebuild, phases 0–4 of `docs/design/architecture-plan-2026-11.md` (owner's go 2026-11-18; spike 5a is [umbau-hp]'s).… → [detail](items/2026-11-18-the-rebuild-phases-04-of.md)
- [umbau-hp] Phase 4b of the rebuild (from [umbau]): screenshot tests – components instead of whole screens where the screen is not the point (549 pictures → ~200), no check lost. Touches only `app/src/test/java/com/onpath/app/ui/**` + `app/src/test/snapshots/**` – since 2026-11-19
- [IR] Tour suggestions (Routenfinder), the whole topic: POC `tools/suggest-poc/` (the lab), the core `app/src/main/java/com/onpath/app/suggest/` (ported, parity), the suggest file (laptop builds the cells), the planner framework branch… → [detail](items/2026-11-14-tour-suggestions-routenfinder-the-whole.md)
<!-- - [donald|laptop|captain|cloud-web] short description – since YYYY-MM-DD -->
- [mehrtage] Multi-day tours – build (owner 2026-11-17: „Ja, es geht los … Let's build it“): stages 0–4 on main (2026-11-18); open: the owner's look on the Pixel (GitHub E2E green 2026-11-18); later: share a hotel to the tour (stage 5). `docs/design/multi-day-tours.md` – since 2026-11-17

## Next
- **[donald] Check with the owner's Pixel on 2026-11-18…** → [detail](items/2026-11-18-check-with-the-owner-s-pixel-on-2026-11.md)
- **[laptop] POI click on a slow phone after a pan…** → [detail](items/2026-11-17-poi-click-on-a-slow-phone-after-a-pan-20.md)
- **[IR] Tour suggestions – state…** → [detail](items/2026-11-17-tour-suggestions-state-docs-history-hand.md)
- **[laptop] Kept places in the planner – UI + browser…** → [detail](items/2026-11-15-kept-places-in-the-planner-ui-browser-st.md)
- **[donald] Full check: POI names on the map without…** → [detail](items/2026-11-15-full-check-poi-names-on-the-map-without.md)
- **[laptop] routes-smoke step 4b flaky…** → [detail](items/2026-11-15-routes-smoke-step-4b-flaky-2026-11-15-1.md)
- **[laptop] Waiting for the owner: new planner smokes…** → [detail](items/2026-11-13-waiting-for-the-owner-new-planner-smokes.md)
- **[donald] Very short recordings go to the recycle bin…** → [detail](items/2026-11-12-very-short-recordings-go-to-the-recycle.md)
- **[donald] Code audit 2026-11-12…** → [detail](items/2026-11-12-code-audit-2026-11-12-owner-asked-clean.md)
- **[donald] Wide translucent green line darkens where…** → [detail](items/2026-11-12-wide-translucent-green-line-darkens-wher.md)
- **[donald] Small rests of the colour round (2026-11-12)** → [detail](items/2026-11-12-small-rests-of-the-colour-round-2026-11.md)
- **[donald] Website pictures for 0.17.9** → [detail](items/2026-11-18-website-pictures-for-0-17-9.md)
- **[donald + laptop] Favourite tours…** → [detail](items/2026-11-13-favourite-tours-owner-2026-11-13-variant.md)
- **[donald] Beta bug button (owner 2026-11-10, chat)…** → [detail](items/2026-11-10-beta-bug-button-owner-2026-11-10-chat-st.md)
- **[donald] Father's first feedback (owner 2026-11-08)** → [detail](items/2026-11-08-father-s-first-feedback-owner-2026-11-08.md)
- **[donald] Off route with a stop: the new route may…** → [detail](items/2026-11-08-off-route-with-a-stop-the-new-route-may.md)
- **[donald] Local Rundgang emulator…** → [detail](items/2026-11-08-local-rundgang-emulator.md)
- **[donald] Design round after 0.17.4…** → [detail](items/2026-11-07-design-round-after-0-17-4-owner-2026-11.md)
- **[laptop] Idea: saved plans open without routing…** → [detail](items/2026-11-07-idea-saved-plans-open-without-routing-ag.md)
- **[donald] Bugs seen while taking the Meran 0.17…** → [detail](items/2026-11-07-bugs-seen-while-taking-the-meran-0-17-pi.md)
- **[donald] (low) Vellau → Leiteralm routes over the…** → [detail](items/2026-11-07-low-vellau-leiteralm-routes-over-the-gam.md)
- **[donald] Smoother on older phones…** → [detail](items/2026-11-07-smoother-on-older-phones-owner-field-202.md)
- **[donald] Handy einrichten: "Links vom Planer in…** → [detail](items/2026-11-07-handy-einrichten-links-vom-planer-in-onp.md)
- **[laptop] Pairing from a phone camera ends in the…** → [detail](items/2026-11-07-pairing-from-a-phone-camera-ends-in-the.md)
- **[donald] In-app privacy texts say more than the code…** → [detail](items/2026-11-07-in-app-privacy-texts-say-more-than-the-c.md)
- **[donald] Follow-ups from v0.17.3 (2026-11-07)** → [detail](items/2026-11-07-follow-ups-from-v0-17-3-2026-11-07.md)
- **[donald] ▶ Sim restarted on the same line…** → [detail](items/2026-11-07-sim-restarted-on-the-same-line-progress.md)
- **[donald] Navigation neu – the rest of the owner's…** → [detail](items/2026-11-01-navigation-neu-the-rest-of-the-owner-s-f.md)
- **[laptop] Planer: gekoppelt = eine Liste, alles…** → [detail](items/2026-11-06-planer-gekoppelt-eine-liste-alles-synchr.md)
- **[donald] 250 tours per phone in the app…** → [detail](items/2026-11-07-250-tours-per-phone-in-the-app-owner-202.md)
- **[donald] Test audit + clean-out (owner 2026-11-06)** → [detail](items/2026-11-06-test-audit-clean-out-owner-2026-11-06.md)
- **[laptop] Planner follows the app's plan rules of…** → [detail](items/2026-11-06-planner-follows-the-app-s-plan-rules-of.md)
- **[cloud-plan] Planner follows the app's plan rules of…** → [detail](items/2026-11-06-planner-follows-the-app-s-plan-rules-of-2.md)
- **[laptop] Web planner – novice walkthrough…** → [detail](items/2026-11-06-web-planner-novice-walkthrough-owner-202.md)
- **[donald] Flow map + novice walkthrough…** → [detail](items/2026-11-06-flow-map-novice-walkthrough-docs-design.md)
- **[donald] Pixel review 2026-11-06 – open bugs** → [detail](items/2026-11-06-pixel-review-2026-11-06-open-bugs.md)
- **[donald → owner] Save-path proposals…** → [detail](items/2026-11-06-save-path-proposals-docs-design-save-pat.md)
- **[pixel] Owner's points of 2026-11-06 – check on…** → [detail](items/2026-11-06-owner-s-points-of-2026-11-06-check-on-th.md)
- **[donald] Voice: the roundabout sentence on the…** → [detail](items/2026-11-06-voice-the-roundabout-sentence-on-the-own.md)
- **[cloud-plan] Flaky web smokes on GitHub – the rest** → [detail](items/2026-11-06-flaky-web-smokes-on-github-the-rest.md)
- **[donald] Onboarding – first-use hints…** → [detail](items/2026-11-05-onboarding-first-use-hints-proposal-2026.md)
- **[cloud-plan] Web: within-a-leg wait bar** → [detail](items/2026-11-05-web-within-a-leg-wait-bar.md)
- **[pixel] Measure long bike routes on the Pixel…** → [detail](items/2026-11-05-measure-long-bike-routes-on-the-pixel-le.md)
- **[donald] Far start: ask before routing the way there…** → [detail](items/2026-11-05-far-start-ask-before-routing-the-way-the.md)
- **[donald] + [laptop] Long bike routes – the rest…** → [detail](items/2026-11-04-long-bike-routes-the-rest-owner-2026-11.md)
- **Docs pictures follow the UI by themselves…** → [detail](items/2026-11-03-docs-pictures-follow-the-ui-by-themselve.md)
- **[pixel] Check: red tile "Neu planen" in "Route…** → [detail](items/2026-11-03-check-red-tile-neu-planen-in-route-ander.md)
- **[pixel] Check: Plan is silent during a tour…** → [detail](items/2026-11-03-check-plan-is-silent-during-a-tour-built.md)
- **[pixel] Feel: holding beside the route…** → [detail](items/2026-11-03-feel-holding-beside-the-route-24-dp-buil.md)
- **[donald] [pixel] Diagnostics: 127 entries on the…** → [detail](items/2026-11-02-diagnostics-127-entries-on-the-owner-s-p.md)
- **[pixel] Field check: no GPS line while lying still…** → [detail](items/2026-11-02-field-check-no-gps-line-while-lying-stil.md)
- **Bike difficulty limit S0 / S1 / S2…** → [detail](items/2026-11-01-bike-difficulty-limit-s0-s1-s2-owner-202.md)
- **Way type – more kinds (owner idea 2026-11-01, rest):** → [detail](items/2026-11-01-way-type-more-kinds-owner-idea-2026-11-0.md)
- **Website and Google Play follow-ups…** → [detail](items/2026-10-31-website-and-google-play-follow-ups-marke.md)
- **From the external review (A-020 §4) – build only…** → [detail](items/2026-10-30-from-the-external-review-a-020-4-build-o.md)
- **On the next real ride (owner)** → [detail](items/2026-10-30-on-the-next-real-ride-owner.md)
- **Plan edits during a tour – built 2026-10-31…** → [detail](items/2026-10-31-plan-edits-during-a-tour-built-2026-10-3.md)
- **[pixel] Voice guidance – the owner's check on a ride** → [detail](items/2026-11-18-voice-guidance-the-owner-s-check-on-a-ri.md)
- **[donald] Germany pre-built on donald → TEST R2…** → [detail](items/2026-10-30-germany-pre-built-on-donald-test-r2-owne.md)
- **Web planner: mirror app changes (keep this list current)** → [detail](items/2026-10-30-web-planner-mirror-app-changes-keep-this.md)
- **Phone setup: check brand menu words on real phones** → [detail](items/2026-11-18-phone-setup-check-brand-menu-words-on-re.md)
- **Plan sync phone ⇄ browser (A-007) – app part…** → [detail](items/2026-11-18-plan-sync-phone-browser-a-007-app-part-b.md)
- **Check on the phone…** → [detail](items/2026-10-28-check-on-the-phone-0-11-6-0-12-0-built-w.md)
- **Smart screen while cycling – built (e6ee6ed)…** → [detail](items/2026-10-30-smart-screen-while-cycling-built-e6ee6ed.md)
- **Map: Knotenpunkte + cliffs/fords (owner 2026-10-30)** → [detail](items/2026-10-30-map-knotenpunkte-cliffs-fords-owner-2026.md)
- **Google Maps place → go there (owner)** → [detail](items/2026-11-18-google-maps-place-go-there-owner.md)
- **Two MainActivity instances at start…** → [detail](items/2026-11-18-two-mainactivity-instances-at-start-seen.md)
- **Route hints – open candidates…** → [detail](items/2026-11-18-route-hints-open-candidates-numbers-in-d.md)
- **requirements.md update (any session, owner checks)** → [detail](items/2026-11-18-requirements-md-update-any-session-owner.md)
- **Map: paths look interrupted at junctions…** → [detail](items/2026-11-18-map-paths-look-interrupted-at-junctions.md)
- **Router classes** → [detail](items/2026-11-18-router-classes.md)
- **[cloud-plan] Planner security headers (CSP etc.)** → [detail](items/2026-11-01-planner-security-headers-csp-etc.md)
- **CI follow-ups:** → [detail](items/2026-11-18-ci-follow-ups.md)
- **Small:** → [detail](items/2026-11-18-small.md)

## Waiting for the owner
- [captain] 2026-11-19: **merge pull request 14** → [detail](items/2026-11-19-merge-pull-request-14.md)
- **[umbau] (2026-11-20) Three old weaknesses in the…** → [detail](items/2026-11-20-2026-11-20-three-old-weaknesses-in-the-t.md)
- **[umbau] (2026-11-19) Rebuild phase 2: no release…** → [detail](items/2026-11-19-2026-11-19-rebuild-phase-2-no-release-be.md)
- **[umbau] (2026-11-19) Test clean-up: may tests that…** → [detail](items/2026-11-19-2026-11-19-test-clean-up-may-tests-that.md)
- [umbau-hp] 2026-11-19: **Routing for iOS – port the routing engine to Kotlin? (recommended: yes)…** → [detail](items/2026-11-19-routing-for-ios-port-brouter-to-kotlin.md)
- [donald] 2026-11-18 late: multi-day day rows say… → [detail](items/2026-11-18-tag-2-fahren-ride-day-2-also-for-a-hike.md)
- [donald] 2026-11-18 late: **delete the GitHub secret…** → [detail](items/2026-11-18-delete-the-github-secret-cf-start-token.md)
- **[donald] Only our signed apps on the map server?…** → [detail](items/2026-11-07-only-our-signed-apps-on-the-map-server-o.md)
- **Map server access before the public launch** → [detail](items/2026-11-18-map-server-access-before-the-public-laun.md)

## Later
- **Multi-day tours – concept complete, nothing built…** → [detail](items/2026-11-17-multi-day-tours-concept-complete-nothing.md)
- **Plan sheet "a third lower"** – reached only ~23 % (d2885035); more needs touch targets smaller than 48 dp, owner
  said no (2026-10-31).
- **(optional) Gradle shared remote build cache** (e.g. a GCS bucket) for donald + CI + cloud sessions.
- **(low prio) Online place search depends on…** → [detail](items/2026-10-30-low-prio-online-place-search-depends-on.md)
- **iPhone version** – direction and order in `docs/design/multiplatform.md`; core logic free of Android
  (`SharedCoreRuleTest`).
- **Open source + self-hosted map servers…** → [detail](items/2026-11-18-open-source-self-hosted-map-servers-conc.md)
- **Replace the tour's route by a saved route during a tour** → [detail](items/2026-10-31-replace-the-tour-s-route-by-a-saved-rout.md)
- **Tour suggestions — feedback captured, not…** → [detail](items/2026-11-08-tour-suggestions-feedback-captured-not-s.md)
- **Ideas:** outdoor mode, way back on own track, hut info, first-start intro / help page.
- **Map wishes (only when the owner says so):** → [detail](items/2026-11-18-map-wishes-only-when-the-owner-says-so.md)
- **Unverified paths (owner: keep):** → [detail](items/2026-11-18-unverified-paths-owner-keep.md)
- Tune the difficulty thresholds (Easy / Moderate / Difficult) with real tours.
