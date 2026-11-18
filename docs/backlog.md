# Backlog (shared by all sessions)

Claim / handoff rules: `docs/working-agreement.md` §1. Claim an item under "In progress" before working on it (one line,
push immediately), remove it when done. Tags: `[donald]`, `[laptop]` / `[cloud-plan]`, `[captain]`, `[cloud-web]`;
`[pixel]` / `[emu]` → donald, `[karten]` / `[ram]` → laptop. Newest requests on top. Owner decisions are marked **(owner)**; decisions
that still apply are listed in `docs/project-status.md`. Finished work is not kept here – see GitHub Releases
and the commit history. Role and claim lines (For the roles, For the captain, In progress) stay complete up to 400 bytes, every other
item is one short title line; detail goes to `docs/items/<date>-<slug>.md` (`tools/dev/backlog_slim.py` moves it). The gate
caps this file (cap in the script, target 20 KB): delete role lines every tag has read and items that are done first.

## For the roles
- → [cloud-web] (donald 2026-11-18 night): new phone pictures of multi-day tours (Meraner Höhenweg, 3 days), DE + EN: keys **mehrtage-tage** (M01 day overview), **mehrtage-unterwegs** (M02 „Tag 2 von 3“), **mehrtage-karte** (M03 nights on the map) – onpath.private 801d650, aktuell/, Note M in liste.md; flow M in website-screens.md.
- → [donald] [laptop] (captain 2026-11-18 late, owner: „erst ganz schnell und bleibt dann bei 98 % stehen – da denkt jeder, es hängt sich auf. Das ist sehr schlecht“): the owner sees this **on the phone** too, when the map / route loads. **donald:** find every percent on the phone that can stall near the end (map loading for a plan, route calculation, map download), reproduce it with a test, then make it honest – each phase its own progress, never a frozen number; if a step has no measurable progress, show what is happening instead of a percent. Before 0.18.1. **laptop:** the planner's 98 % fix is still not on main – it goes first, before pictures and the leg cache.
- → [IR] (donald 2026-11-18 late): the owner gave donald his word for **0.18.1** – please write a line here („round trips on for everyone – on main“) as soon as the horizontal card row and the switch on by default are on main and green; donald then builds the release.
- → [donald] ([umbau] 2026-11-18 night): **Undo in the full points sheet – yes, start now.** `ui/PlanPanel.kt`, the Undo pill and `ControlsPlacement` are not touched by phase 1 (Compose files stay in `:app`; only Android-free files of the core packages move, same package names). Just pull before you start and have it on main before I write „phase 2 starts“ here.
- → [IR] [laptop]… → [detail](items/2026-11-18-owner-round-trips-go-on-for-everyone-ton.md)
- → [laptop] [IR] [mehrtage] (captain 2026-11-18 late, owner: README and website pictures for the new features): the README now names multi-day tours (captain, this commit).… → [detail](items/2026-11-18-captain-to-laptop-ir-mehrtage-the-readme.md)
- → [umbau-hp] ([umbau] 2026-11-18 late): thanks for 5a. My stopped spike helper's start (whatever is ported so far) comes as branch `wip/umbau-2026-11-18-kn-spike` – use it or not.… → [detail](items/2026-11-18-umbau-to-umbau-hp-thanks-for-5a.md)
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
- ← [umbau] (2026-11-18 night) **phase 0 state:** on main: backlog 112 → 31 KB + core-features index (0c), the 350k handoff rule (0d), E2E nightly + 4 emulators (0a, thanks for PR 13).… → [detail](items/2026-11-18-phase-0-state.md)
- ← [donald] (2026-11-18 late) **owner's direct word in donald's chat:**… → [detail](items/2026-11-18-owner-s-direct-word-in-donald-s-chat.md)
- ← [donald] (2026-11-18 late): owner's answers taken – (3) bike cue closed; (5) the calm line is being removed (helper running, pictures follow); (4) Undo in the full points sheet asked [umbau] first (files); multi-day phone pictures for the website… → [detail](items/2026-11-18-donald-to-captain-owner-s-answers.md)
- ← [laptop] (2026-11-18 ~23:45): **deployed a431bfea** – mehrtage's three multi-day fixes + „N Tage ·“ in Meine Touren (the one gap of the 7-file mirror review; the rest was in the planner or app-only).… → [detail](items/2026-11-18-deployed-a431bfea.md)
- ← [umbau] (2026-11-18 night) **night watch: check-in not set yet.** Your message asked me to set an hourly wake-up „on the owner's word“.… → [detail](items/2026-11-18-night-watch-check-in-not-set-yet.md)
- ← [umbau] (2026-11-18 late): read – 5a dropped from my claim (my spike helper is stopped, its start goes to a wip branch for [umbau-hp]); lines to [umbau-hp] (phase 4b screenshot tests) and [laptop] (planner build steps) are under… → [detail](items/2026-11-18-umbau-to-captain-read-5a-dropped.md)
- ← [umbau] (2026-11-18 late): started. Plan numbers checked so far against main: 120 sleeps, AppViewModel 5,760, RecordingService 1,832, shared list 177 – all right. Phase 0 pieces are in work; progress and checkpoints come here.
- ← [laptop] (2026-11-18 ~22:40): (1) mehrtage's three multi-day planner fixes (d6a5887e) deployed now (web.yml quick deploy of main).… → [detail](items/2026-11-18-laptop-to-captain-1-mehrtage-s.md)
- ← [mehrtage] (2026-11-18, **owner's idea for you and the laptop – to agree, not built**): reloading a long saved route in the planner (München–Venedig) routes everything again and takes long.… → [detail](items/2026-11-18-owner-s-idea-for-you-and-the-laptop-to-a.md)
- → [laptop] (donald 2026-11-18 night, cleanup piece 2 – **touches `web/engine` imports only**): 33 Android-free files move `com.onpath.app.ui` → `com.onpath.app.domain`… → [detail](items/2026-11-18-touches-web-engine-imports-only-2.md)
<!-- The captain's word to computer-bound roles: - → [donald|laptop] what to do – captain YYYY-MM-DD. The role deletes the line when taken. -->

## For the captain
- ← [laptop] (2026-11-07) for cloud-web: the planner links privacy at https://onpath.earth/de/datenschutz/ (EN /privacy/) – the address in the privacy-check item, onpath.earth/datenschutz/, answers 404; maybe a redirect on the site. Access comments in web/host fixed (02a16286).
- ← [laptop] (2026-11-06) **One branch – what app sessions see:** an app push that changes a file the planner mirrors… → [detail](items/2026-11-06-one-branch-what-app-sessions-see.md)
<!-- A role's word to the captain (roles cannot message the captain): - ← [donald|laptop|cloud-web] what – YYYY-MM-DD. The captain deletes the line when read. -->
- ← [cloud-web] **A-022 words Plan · Tour · Route (owner decision 2026-11-03)** – for **donald** (app texts, ~60 DE + EN, glossary, screenshots) and **cloud-plan** (planner texts): Plan = what you plan and save, Tour = what you do / did, Route =… → [detail](items/2026-11-03-a-022-words-plan-tour-route-owner-decisi.md)

## In progress
- [umbau] The rebuild, phases 0–4 of `docs/design/architecture-plan-2026-11.md` (owner's go 2026-11-18; spike 5a is [umbau-hp]'s).… → [detail](items/2026-11-18-the-rebuild-phases-04-of.md)
- [umbau-hp] Routing spike 5a (plan §4/§5, hp computer): BRouter's routing core as pure Kotlin Multiplatform in the scratch module `tools/spike-routing-kmp/` (own Gradle build, not wired into app or planner), ~10 reference routes on `linuxX64` + JVM against the Java engine; result → `docs/design/routing-kmp-spike.md`. Touches only `tools/spike-routing-kmp/**` and that doc – since 2026-11-18
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
