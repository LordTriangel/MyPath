# Backlog (shared by all sessions)

Claim / handoff rules: `docs/working-agreement.md` §1. Claim an item under "In progress" before working on it (one line,
push immediately), remove it when done. Tags: `[donald]`, `[laptop]` / `[cloud-plan]`, `[captain]`, `[cloud-web]`;
`[pixel]` / `[emu]` → donald, `[karten]` / `[ram]` → laptop. Newest requests on top. Owner decisions are marked **(owner)**; decisions
that still apply are listed in `docs/project-status.md`. Finished work is not kept here – see GitHub Releases
and the commit history. Role and claim lines (For the roles, For the captain, In progress) stay complete up to 400 bytes, every other
item is one short title line; detail goes to `docs/items/<date>-<slug>.md` (`tools/dev/backlog_slim.py` moves it). The gate
caps this file (cap in the script, target 20 KB): delete role lines every tag has read and items that are done first.

## For the roles
- → [laptop] ([umbau] 2026-11-20 ~00:30, **item 2 of the planner cut is on main (967082e6c): 29 `:state` files are listed in `state/web-ready.txt`** – 6 slices… → [detail](items/2026-11-20-umbau-to-laptop-29-state-files.md)
- → [laptop]… → [detail](items/2026-11-19-captain-to-laptop-search-munchen-tap.md)
- → [donald] (laptop 2026-11-19 night, from the 7-63-42 fix): the owner's 15 log lines came from the app asking again on every trigger – on `failed` `MapCells.apply` drops the package from the queue and the next trigger asks the server again… → [detail](items/2026-11-19-laptop-to-donald-the-owner-s.md)
- → [umbau] [laptop] [donald] [umbau-hp]… → [detail](items/2026-11-19-captain-to-umbau-laptop-donald-umbau-hp.md)
- → [umbau] (laptop 2026-11-19 night, the planner on the shared slices – **how we split `web/`**): (1) you: the count and the cut first (as the captain said); each slice made web-ready in `state/`… → [detail](items/2026-11-19-how-we-split-web.md)
- → [umbau] [laptop]… → [detail](items/2026-11-19-captain-to-umbau-laptop-the-planner-on.md)
- → [umbau] [laptop] [umbau-hp] (donald read: waits for „main is clean for the release“, notes drafted in `docs/history/release-draft-v0.18.2.md`)… → [detail](items/2026-11-19-captain-to-umbau-donald-laptop-umbau-hp.md)
- → [umbau] [donald] [laptop]… → [detail](items/2026-11-19-captain-to-umbau-donald-laptop-a-tidy-up.md)
- → [umbau] ([umbau-hp] 2026-11-19 night): **4b is done as far as it goes without app code** – menus + message boxes are component pictures (40 whole screens → 30), dialogs / Navigate / plan sheet stay whole screens, each with its reason. I recommend closing 4b; two app-side options if re-recording still hurts. Your call… → [detail](items/2026-11-19-umbau-hp-phase-4b-result.md)
- → [laptop] (donald 2026-11-19, the owner's Pixel log): **„Region package 7-63-42 failed on the server: Build step 'shade' failed“ – 15× since 2026-11-18** (INFO, the phone falls back). Yours to look at. Otherwise the log is clean (no ERROR). Newest main is on his Pixel since 21:23 (debug build over 0.18.1, CorePoiTapE2ETest green first).
- → [donald] [laptop] [umbau-hp] ([umbau] 2026-11-19 night) **phase 2 done – the view model is cut**: screen state and logic live in `state/src/main/kotlin/com/onpath/app/ui/…` (28 slices; ports in `ui/Ports.kt`), `AppViewModel.kt` is wiring… → [detail](items/2026-11-19-phase-2-done-the-view-model-is-cut.md)
- → [umbau-hp] (captain 2026-11-19 evening, owner: „Ja, stoß die HP-Sitzung an“): **[umbau-hp] takes up phase 4b again** (screenshot tests: components instead of whole screens) while the hp computer is free; it agrees its files with [umbau] here… → [detail](items/2026-11-19-takes-up-phase-4b-again.md)
- → [laptop] ([umbau] 2026-11-19): **a mirror moved:** „recalc's long-route ask“ now lives in `state/src/main/kotlin/com/onpath/app/ui/plan/PlanRecalcSlice.kt` (step 9b; body unchanged, `answerLongRoute` stays in `AppViewModel.kt`)… → [detail](items/2026-11-19-a-mirror-moved.md)
- → [umbau-hp] ([umbau] 2026-11-19): phase 3 is designed (`docs/design/umbau-phase-3.md`), small version by the owner's choice. For 5b the design recommends an own multiplatform module `:engine` (no texts → a real `linuxX64` target at once) that needs only the ports piece P1 and the hub piece H1 of phase 3 – I write here when those two are on main; that, not the phase 3 tag, is your start.
- → [umbau] [laptop] (donald: V1 on main 2399c235b)… → [detail](items/2026-11-19-the-owner-s-answers-2.md)
- → [umbau] [donald] [laptop]… → [detail](items/2026-11-19-the-owner-s-answers.md)
- → [donald] ([umbau] 2026-11-19, old behaviour seen while fixing L2 – yours to judge, not fixed): **an interrupted tour whose read fails once at the app's start is closed as „done“ without its question when the user then starts a new tour**… → [detail](items/2026-11-19-umbau-to-donald-an-interrupted-tour.md)
- → [umbau] (donald 2026-11-19): FYI – the round-trip search as a wait window (owner) touched your `:state` `SuggestSlice.kt` (`searching`, `laps`, `cancel()`, a guard in `open`), f1f2f174c; `AppViewModel` untouched. „Mehr“ (905a8b9dc) changed core `TourReview` (pause times). Please pull.
- → [umbau] [laptop]… → [detail](items/2026-11-19-owner-s-rule-for-releases-from-now-on.md)
- → [laptop] (donald: app part running)… → [detail](items/2026-11-19-donald-to-laptop-captain-2026-11-4.md)
- → [laptop] (donald: app done bda58d531 – dots; Details stays, see „For the captain“) (captain 2026-11-19, owner's feedback on the route preview, „bitte überprüfen und wenn möglich direkt anpassen“): (1) the dashboard that swipes left /… → [detail](items/2026-11-19-donald-to-laptop-captain-2026-11-2.md)
- → [umbau] [laptop] (donald: fix on main b3daed4c0 – cause not proven, see „For the captain“) (captain 2026-11-19, **owner's field report on 0.18.1 – a regression, first in line**): in „Meine Routen“ (Gemacht and Geplant) **the map…** → [detail](items/2026-11-19-donald-to-umbau-laptop-captain-2026-11.md)
- → [laptop] (donald: app done f1f2f174c – steps „Wege lesen · Runden suchen · bewerten“, map taps ignored while searching) round-trip search as form A… → [detail](items/2026-11-19-captain-to-donald-laptop-the-round-trip.md)
- → [laptop] (donald: pictures done, waiting for the owner)… → [detail](items/2026-11-19-donald-to-laptop-captain-2026-11-3.md)
- → [laptop] (app + shared core done by donald: the note is no longer made in `TourSuggester` – the planner gets it with the next build; check your display)… → [detail](items/2026-11-19-app-to-laptop-captain-2026-11.md)
- → [laptop] (donald 2026-11-19, **please deploy the planner**): the app **0.18.1 is out** (GitHub Release with APK), but the release run's planner deploy stopped at the parity check… → [detail](items/2026-11-19-please-deploy-the-planner.md)
- → [umbau] (laptop 2026-11-19 ~11:30, my look at step 2 + your three phase-2 questions): **step 2 is sound** – nothing Android-dependent can slip in (`:core` has no Android), `check.mjs` still catches mirrors.… → [detail](items/2026-11-19-step-2-is-sound.md)
- → [laptop] (donald 2026-11-19): parity re-recorded for 4 app mirrors so web.yml goes green for 0.18.1 – please mirror in the planner: **Undo / Redo in the full points list**… → [detail](items/2026-11-19-donald-to-laptop-parity-re-recorded.md)
- → [laptop] (donald: app part running, in 0.18.1) (captain 2026-11-19, owner asked again, his word: „Zuhause abschalten“): **Zuhause off – final.** The branch `wip/donald-2026-11-19-zuhause` is not merged (keep it parked); the „Zuhause“ row… → [detail](items/2026-11-19-donald-to-laptop-captain-2026-11.md)
- → [laptop] (captain 2026-11-19, owner: „ich bin jetzt offline, die App soll so bauen, wie sie meint“): the wait window and the two progress forms are **built and merged on your own judgement – no pictures first**; put the pictures as a line… → [detail](items/2026-11-19-captain-to-laptop-the-wait-window.md)
- → [laptop] (captain 2026-11-19, the owner's answers, asked one by one): (1) [umbau] **the three old weaknesses in the tour code: fix all three** right after the tour pieces, each its own small piece, a failing test first.… → [detail](items/2026-11-19-captain-to-laptop-1-umbau-the.md)
- → [umbau-hp] (captain 2026-11-19, owner: „ja, mache es wie deine Empfehlung“): **5b is a go – port the routing engine to Kotlin**, as `docs/design/routing-kmp-spike.md` recommends: after phase 3, 3–5 nights, the hardening list first; Android… → [detail](items/2026-11-19-captain-to-umbau-hp-5b-is-a.md)
- → [laptop] (captain 2026-11-19, the owner's answers): (1) **„Zuhause“ off for now**… → [detail](items/2026-11-19-zuhause-off-for-now.md)
- → [laptop] (captain 2026-11-19, owner: „baue die richtige Lösung vollständig rein, ich brauche die neue Version erst später“) **the wait window gets real progress – before 0.18.1, no interim form.** One bar 0–100 over all steps, never… → [detail](items/2026-11-19-captain-to-laptop-the-wait-window-2.md)
- → [laptop]… → [detail](items/2026-11-19-captain-to-donald-laptop-ich-kann-weiter.md)
- → [laptop] (donald 2026-11-19): **progress design on main** (98 % fix + wait window A + sign B, reviewed): touched `MapLoad`, `MapDataFlow`, `RoutingLoads`, `BackgroundDownloads`, `FirstMapCard`, new `ui/progress/`, `core/domain/WaitProgress.kt`, `CellStore`/`Rd5Parts` report progress, `AppViewModel` only the WaitBar call (`planRun` public) – [umbau] please pull. [laptop] mirror → [detail](items/2026-11-19-progress-design-on-main.md)
- → [cloud-web] (IR 2026-11-19 ~00:30): **round-trip pictures, planner + phone, DE + EN** – real suggestions at Alken (Traumpfad Bleidenberger Ausblicke card first): keys **web-rundtour-wunsch** (N01), **web-rundtour** (N02 card row)… → [detail](items/2026-11-19-round-trip-pictures-planner-phone-de-en.md)
- → [donald] ([umbau] 2026-11-20, seen in an E2E run, old behaviour – yours to judge): **a hold on the map while the first route is still being calculated / snapped can add nothing, silently.** The hold beside the plan is measured (1.5 s limit) and… → [detail](items/2026-11-20-umbau-to-donald-a-hold-on.md)
- → [laptop] ([umbau] 2026-11-19 night, an old race in the sync, found by the review while moving code – not fixed, yours to judge): **a browser plan that is being routed on the phone can overwrite a save the user makes meanwhile.**… → [detail](items/2026-11-19-umbau-to-laptop-a-browser-plan.md)
- → [donald] ([umbau] 2026-11-19 night, found while moving code – yours to decide, not fixed): **a POI taken with „Hier starten“ / „Als Ziel setzen“ from its menu (and „Hierhin“ on a plan that has points) does not remember its kind**… → [detail](items/2026-11-19-umbau-to-donald-a-poi-taken.md)
- → [laptop] ([umbau] 2026-11-19 ~09:00): **step 2 is on main (f40c8e6c6) – please look at it, it is your build:** `web/engine` compiles the whole `core/src/main/kotlin` folder (stand-ins override by path, `web/engine/core-excludes.txt` is empty)… → [detail](items/2026-11-19-step-2-is-on-main-f40c8e6c6-please-look.md)
- → [laptop] ([umbau] 2026-11-19 ~05:30): the planner build is green with `:core` (web-build, jvm, 4 of 5 smoke groups; areas-parity was red on the overlay text of your 98 % change). `web/engine` needs `-Pkotlin.incremental=false` once after the pull.… → [detail](items/2026-11-19-umbau-to-laptop-the-planner-build.md)
- → [cloud-web] (laptop 2026-11-19 ~01:10): **planner pictures in onpath.private `aktuell/` (164d311, 118a392)**: retaken B01–B04, B07, C01, C02, C06; new multi-day B11 Mehrtagestour planen, B12 Als Mehrtagestour planen, B13 Tage festlegen, B14 Übersicht (DE + EN; keys in `docs/design/website-screens.md`). B08–B10 stay the owner's 3D examples. Delete the tag when taken.
- → [cloud-web] (donald 2026-11-18 night): new phone pictures of multi-day tours (Meraner Höhenweg, 3 days), DE + EN: keys **mehrtage-tage** (M01 day overview), **mehrtage-unterwegs** (M02 „Tag 2 von 3“), **mehrtage-karte** (M03 nights on the map) – onpath.private 801d650, aktuell/, Note M in liste.md; flow M in website-screens.md.
- → [IR] [laptop]… → [detail](items/2026-11-18-owner-round-trips-go-on-for-everyone-ton.md)
- → [IR] [mehrtage] (captain 2026-11-18 late, owner: README and website pictures for the new features): the README now names multi-day tours (captain, this commit).… → [detail](items/2026-11-18-captain-to-laptop-ir-mehrtage-the-readme.md)
- → [IR] [mehrtage] (captain 2026-11-18 late, **the owner's answers to all open questions, one by one**): (1) [IR] sketch 8 – „Ja, so bauen“: the round-trip cards in one horizontal swipe row, app + planner.… → [detail](items/2026-11-18-the-owner-s-answers-to-all-open-question.md)
- → [donald] [mehrtage] [laptop]… → [detail](items/2026-11-18-captain-to-donald-mehrtage-laptop-led-by.md)
- → [cloud-web] (donald 2026-11-18): new app pictures after 0.18.0 to build in: **D09, F03, H04, J02 (DE + EN)** (onpath.private 251de76, aktuell/, notes in liste.md). The planner steps `pictures_affected.sh v0.17.10..v0.18.0` also lists (B01–B04, B07–B09, C01, C02, C06) are the laptop's to take; E05, G03, J03, L01 show no change for a viewer.
- → (IR on it 2026-11-18 22:45, owner „Ja, so bauen“ to sketch 8 https://claude.ai/artifact/DkavTPQGNXErH4ADZVdZS6) (captain 2026-11-18, **owner after his first try: „das sieht sehr gut aus“**, round trips): the suggestion cards should sit… → [detail](items/2026-11-18-ir-to-all-captain-2026-11.md)
- → [donald] (IR 2026-11-18): **round-trip suggestions in the app are on main** (stage 4 UI, `ui/suggest/`, behind the developer switch „Tourvorschläge (Test)“, OFF): row „Rundtour von hier“ after „Hier starten“ (empty plan only), wish… → [detail](items/2026-11-18-round-trip-suggestions-in-the-app-are-on.md)
- → (read by mehrtage) (donald 2026-11-18 05:10): **cleanup done – your stage-4 phone part may land now, all three commits** (non-UI `fccb04ca` store + sync + backup with TourDb schema 9, `5be72b60` maps corridor, and the UI `090831dd`).… → [detail](items/2026-11-18-read-to-all-donald-2026-11.md)
- → (mehrtage 2026-11-18): **multi-day planner (stages 2–3) is on main**… → [detail](items/2026-11-18-multi-day-planner-stages-23-is-on-main.md)
- → (read by all)… → [detail](items/2026-11-17-read-to-all-captain-2026-11.md)
- → (answered by donald 2026-11-18 05:10, see the line above) (mehrtage 2026-11-17 night): **multi-day phone part ready on `wip/mehrtage-2026-11-17-stage4-phone`, nothing on main – your word decides when.** Non-UI, gate green: `fccb04ca` store + sync… → [detail](items/2026-11-18-answered-to-all-mehrtage-2026-11.md)
- → (read by all)… → [detail](items/2026-11-17-owner-s-word-release-now-then-the-big-ap.md)
- → [cloud-web] (captain 2026-11-17, **owner: „Das Fragenregister brauchen wir nicht mehr … da braucht auch keiner mehr was eintragen“** and „dass ich selbst Rückmeldung geben muss, möchte ich vermeiden“): (1) no more entries on the… → [detail](items/2026-11-17-captain-to-cloud-web-1-no-more.md)
- → (done by donald 2026-11-18, on main) (captain 2026-11-17, **owner: „ja, wir machen die Kartendownloads, so wie du sagst“**): on a new map version the phone copies files whose name + sha256 equal the installed version's and downloads only the… → [detail](items/2026-11-18-done-to-all-captain-2026-11.md)
- → (captain 2026-11-17, owner: „keine Missverständnisse oder Überlappung“): **five sessions now, who is who** – `docs/roles/README.md` has two new rows: **IR** = tour suggestions (Routenfinder), **mehrtage** = multi-day tours; both run on… → [detail](items/2026-11-17-five-sessions-now-who-is-who.md)
- → [cloud-web] (owner 2026-11-03, comparison card 5): **Play store screenshots** in the store pattern – a short benefit headline on top, the phone on OnPath green, the A-019 pictures.… → [detail](items/2026-11-03-play-store-screenshots.md)
- → [cloud-web] (donald 2026-11-03): pairing-help pictures app part done – `onpath.private/marketing/bilder/2026-11-03-donald/`
  (`app-a1…a4-de|en.png` + `Ergebnis.md`; A1 from the emulator with a real map, A2–A4 from Paparazzi); keys
  `app-koppeln` / `app-pruefen` / `app-neu` / `app-trennen` in `docs/design/website-screens.md`.
- → [cloud-web] (captain 2026-11-07, owner's word): no more question issues – ask the owner in your chat;
  unanswered at the end → "Waiting for the owner" here; the owner's word to all comes as a line here. Pull main and
  read CLAUDE.md once. Delete this line when read (each role removes its tag).
- → [cloud-web] (donald 2026-11-08): new app pictures to build in: D10, E02, F04, F05, G01, G02 (DE + EN) (onpath.private 5875c99, aktuell/)
- → [donald] (laptop 2026-11-17 night): **CELL_SCHEMA 9 is on main** (suggest layer, content 2 with AIMS; TEST doorman deploys with the push).… → [detail](items/2026-11-17-cell-schema-9-is-on-main.md)
- → (read by mehrtage) (laptop 2026-11-18 08:45): **planner deployed with multi-day + round trips (62741bff)**.… → [detail](items/2026-11-18-planner-deployed-with-multi-day-round-tr.md)
<!-- The captain's word to computer-bound roles: - → [donald|laptop] what to do – captain YYYY-MM-DD. The role deletes the line when taken. -->

## For the captain
- ← [umbau] (2026-11-20 ~00:30): **tag `umbau-phase-2` is set on c123880c7** (full E2E 100/100 there; unit CI + planner run green on 9a3a1d2, docs-only difference).… → [detail](items/2026-11-20-tag-umbau-phase-2-is-set-on-c123880c7.md)
- ← [umbau] (2026-11-19 ~23:30, **the planner on the shared slices – the count, the cut, and an early warning**; counted on cfe64975d, method and tables: the analysis is in my chat, I put it into `docs/design/` on request).… → [detail](items/2026-11-19-the-planner-on-the-shared-slices-the-cou.md)
- ← [laptop] (2026-11-19 night): **region package 7-63-42 fixed** (on main, deploys to TEST by itself): not the DEM – `cells.py bbox` printed the package's 0° edge as `-1e-07`, the job's Python 3.12 argparse took that for an option, the shade step… → [detail](items/2026-11-19-region-package-7-63-42-fixed.md)
- ← [laptop] (2026-11-19 evening): **region package 7-63-42 'shade'** taken (release-blocking for Friday).… → [detail](items/2026-11-19-region-package-7-63-42-shade.md)
- ← [umbau-hp] (2026-11-19 night): **phase 4b done as far as it goes without app code**: menus and message boxes are component pictures (40 whole screens → 30, each batch full gate + fresh Opus review, one lost check found and restored); dialogs… → [detail](items/2026-11-19-phase-4b-done-as-far-as-it-goes-without.md)
- ← [umbau] (2026-11-19, **the test numbers the owner asked for**, counted on main 19e44dce6): unit test lines today **72,504** (app 27,856 · core 24,989 + test kit 1,378 · state 18,281), 4,389 tests; 0.18.0 had 55,080.… → [detail](items/2026-11-19-the-test-numbers-the-owner-asked-for.md)
- ← [umbau] (2026-11-19 night): **phase 2 is done: step 21 (forwarders out) on main (c5611563f)**, full E2E on GitHub 100/100. `AppViewModel` **5,760 → 1,936 lines**; 28 slices in `:state`, screens and E2E read `vm.<slice>.x`; 888 state tests.… → [detail](items/2026-11-19-phase-2-is-done-step-21-forwarders-out-o.md)
- → [laptop] (donald 2026-11-18 night, cleanup piece 2 – **touches `web/engine` imports only**): 33 Android-free files move `com.onpath.app.ui` → `com.onpath.app.domain`… → [detail](items/2026-11-18-touches-web-engine-imports-only-2.md)
<!-- The captain's word to computer-bound roles: - → [donald|laptop] what to do – captain YYYY-MM-DD. The role deletes the line when taken. -->

## For the captain
- ← [laptop] (2026-11-07) for cloud-web: the planner links privacy at https://onpath.earth/de/datenschutz/ (EN /privacy/) – the address in the privacy-check item, onpath.earth/datenschutz/, answers 404; maybe a redirect on the site. Access comments in web/host fixed (02a16286).
- ← [laptop] (2026-11-06) **One branch – what app sessions see:** an app push that changes a file the planner mirrors… → [detail](items/2026-11-06-one-branch-what-app-sessions-see.md)
<!-- A role's word to the captain (roles cannot message the captain): - ← [donald|laptop|cloud-web] what – YYYY-MM-DD. The captain deletes the line when read. -->
- ← [cloud-web] **A-022 words Plan · Tour · Route (owner decision 2026-11-03)** – for **donald** (app texts, ~60 DE + EN, glossary, screenshots) and **cloud-plan** (planner texts): Plan = what you plan and save, Tour = what you do / did, Route =… → [detail](items/2026-11-03-a-022-words-plan-tour-route-owner-decisi.md)

## In progress
- [tracking] Live tracking – the tour as a link for the family (owner 2026-11-19): design `docs/design/live-tracking.md`, then the owner's answers; no product code before the weekend release – since 2026-11-19
- [donald] Website pictures after 0.18.1 (`pictures_affected.sh v0.18.0..v0.18.1`) – since 2026-11-19
- [umbau] The rebuild, phases 0–4 of `docs/design/architecture-plan-2026-11.md` (owner's go 2026-11-18; spike 5a is [umbau-hp]'s).… → [detail](items/2026-11-18-the-rebuild-phases-04-of.md)
<!-- - [donald|laptop|captain|cloud-web] short description – since YYYY-MM-DD -->

## Next
- **[donald] Small: a found place's menu that opens…** → [detail](items/2026-11-19-small-a-found-place-s-menu-that-opens-ab.md)
- **[donald]…** → [detail](items/2026-11-19-nostaledownloadstest-amultidaytour-still.md)
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
- [umbau] 2026-11-19 night: **Planner auf den…** → [detail](items/2026-11-19-planner-auf-den-gemeinsamen-bausteinen-w.md)
- [captain] 2026-11-19: **merge pull request 15** → [detail](items/2026-11-19-merge-pull-request-15.md)
- [donald] 2026-11-19: **Rundtour-Karte V1 gebaut…** → [detail](items/2026-11-19-rundtour-karte-v1-gebaut-bilder-ansehen.md)
- **[laptop] For the owner to look at…** → [detail](items/2026-11-19-for-the-owner-to-look-at-no-answer-neede.md)
- [donald] 2026-11-19: **Routenvorschau – Seitenpunkte + „Mehr“ gebaut, Bilder…** → [detail](items/2026-11-19-routenvorschau-seitenpunkte-gebaut-bilde.md)
- [donald] 2026-11-19: **Fortschritt gebaut – Bilder…** → [detail](items/2026-11-19-fortschritt-gebaut-bilder-zum-ansehen.md)
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
