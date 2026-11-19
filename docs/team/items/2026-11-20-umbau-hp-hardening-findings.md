# [umbau-hp] release hardening for 0.18.2 – findings and what was done

**State at the hand-over (2026-11-20 night): done as far as the brief goes.** The state to release is main at or after
399e55b6 (app) / 07b07800 (planner, GitHub's planner run green; the deploy is the laptop's). A fresh [umbau-hp] session
starts from this file; nothing is half-done and nothing of mine is unpushed. Left for a fresh session if the night has
room: the sibling flake `NoStaleDownloadsTest.aMultiDayTour…` (not looked at – after tonight's find it deserves the same
question: test timing or a race?), the adapter pin test for [umbau], then the routing port 5b after the release.

Four read-only reviews of everything since `v0.18.1` (recording + map download · routing + plan sync · wait windows + late
answers · cards / „Mehr“ / pictures), each finding checked by the lead before a fix. Updated as the night goes.

## Found fine (compared line by line with v0.18.1 where code moved)
- Recording / the tour: `TourSession`, `TourEdits`, `TourEndSlice`, the fixes L1 / L2 / L5 – no lost guard, each fix has its test.
- Routing: `PlanRecalcSlice` (start guard, generations, cancel, fallback), `MultiDaySlice`, `PlanRunSlice` – a faithful move.
- Map download: `MapPictureGuard`, `RoutingDataCheck`, `SegmentDownloader`; `MapCells` unchanged since the tag.
- Late answers: search (app + planner), planner routing, planner suggestions, app suggestion search – all guarded.

## Fixed (test first, each seen red without the fix)
1. **Plan sync, the old race** (`SyncedRoutingSlice.routeSyncedPlans`, in 0.18.1 too): a browser plan still being routed on the
   phone overwrote a save that changed only activity / T level / pinned points (stops equal) – the old values came back,
   went to the backup and to the browser. Now the late write also compares those. It never lost a plan or a line; it lost
   that edit. Test `SyncedRoutingFlowTest.aSaveThatKeepsTheStopsButChangesActivityLevelOrPinsMeanwhileIsNotOverwritten`.
2. **Round trips** (`SuggestSlice.take`): „Übernehmen“, ✕ while the plan is made, a new search – the old take's late answer
   closed the new cards. Test `SuggestSliceTest.aLateAnswerOfATakeClosedMeanwhileDoesNotCloseTheNextSearchsCards`.
3. **My routes' pictures** (`ThumbMaker`): a picture given up because the map changed under it twice (areas joining during a
   download – the field report's situation) was marked failed until the next app start. Now it is made at the next look.
   Tests in `RouteThumbsTest`.

4. **The route wait window's time line on the phone** (`RouteWait`; core `OneBar.measured`, additive): the time left was
   read off the bar, creep included – a leg the router reports nothing of counted down „1 minute … 30 seconds“ by itself
   (the owner's „noch etwa 5 Sekunden“, then minutes; seen in the planner). Now the route window says a time only while the
   router measures (its share within the legs, or the check). **My first version (e5a61deb) also switched the round-trip and
   the map window – the fresh review found the round-trip search's time then counted UP in a long phase; corrected in
   fa9031fe: those two are byte for byte as before**, with a test that is red on the first version
   (`SuggestWaitTest.theTimeLeftNeverCountsUpInALongPhase`). Tests `WaitPromiseTest.routeWindow_…PromisesNoTime` (two).
   The planner's side is the laptop's own fix in `WebWait` (3afebead).
5. **„Mehr“ pause times**: 12 new edge tests (`TourReviewTest`, `RouteMoreTest`: a stop at the very start / end, stepped stops,
   a recording gap, odd timestamps, midnight / summer-time change in four zones) – no bug found; two proven red by breaking the code.

6. **Planner wait window** (07b07800, `web/app/src/loadoverlay.ts`, `main.ts`; robust-smoke step 10, each check red first):
   a failed / too slow / stopped route closed the window as a success (the 100 % flash before the error line, learned shares
   from a run that did not finish); a `#p=` link with no engine left the window open for good; after Abbrechen the stopped
   run's late report restarted the wait unseen, its answer showed a red "planning failed" line, and a new route got no
   window while another job was open. Fresh review: correct, push. Verified on a fresh engine build: robust-smoke 50/50
   three times, share-smoke, phonelink-smoke. One new check was flaky as first written (a tiles-only wait writes the default
   shares by itself) – it now ignores untouched defaults. **Not deployed – the laptop's step.**
   Known, left: a replan the app starts by itself after Abbrechen (undo / activity back) can bring the window back when
   another job is still open and the replan takes over a second – the same as when no job is open.

7. **Map cells, a real race behind a "flaky" test** (399e55b6, `MapCells.downloadAndInstall`, core feature C13; in the code
   since before v0.18.1): GitHub's unit run on fa9031fe was red once at `NoStaleDownloadsTest.runningCorridorDownloadStops`.
   The worker downloads from a list taken earlier; when the plan dropped the area (or the user cancelled) between the list and
   the download, only the progress callback noticed – every 256 KB, never on a retry's first request or a small file – so the
   dropped area was fetched and installed (data, storage). Now checked before every file and before the folder is made
   complete. Reproduced under CPU load (1 of 40), a deterministic test without sleeps
   (`planDropsTheCellBetweenTheWorkersListAndItsDownload`, red on main), fresh review: every legitimate download keeps its
   queue entry, push. Left: one request can still go out in the microseconds between the check and the GET (never an install).

## Second round (all clean)
- `TourEdits` (edits of a running tour) line by line against v0.18.1, every host adapter of the new slices in `AppViewModel`,
  the initialisation order by hand: faithful, no finding.
- The screens after "forwarders out": all 389 removed forwarders compared with their old bodies by script, the screens
  re-derived mechanically from the old files + the rename table – identical. No guard, fallback or second statement lost.
- A fresh review of fixes 1–3: correct, nothing made worse. Fix 4: see above.

## For the release walk-through (suspicious by nature, not known bugs – tap, expect)
1. Round trip → wait window: the phone's Back does nothing bad; a tap on the map adds no point under the window.
2. A track shown in Plan: a map tap adds no point; Back closes the track first.
3. During a tour: ☰ → My routes → Back → Back gives "Continue navigation", the app does not close.
4. An update offered during a tour: the "after the tour / install now" question, both answers.
5. A plan link opened while a track is shown / My routes is open: both close, the plan opens.
6. Settings → Offline maps → clean up → Cancel: the preview goes, nothing is deleted.
7. Cycling rest view: it yields to every question box (arrival, end tour, route back, update, error, My routes).
8. Save fails → OK: the box does not come back.
9. A very long route: "Plan anyway" and "Cancel" – the map goes back, no stuck "Calculating…".
10. Heading-up on, GPS follow on, turn the map by hand: follow switches off.
11. A paired browser plan, opened on the phone right after it arrived, activity changed, saved: it stays changed after
    reopening My routes (fix 1).

## Open (in work or noted)
- The planner run on fa9031fe was red at the parity check: my two app files (`RouteWait`, `ThumbMaker`) are mirrored by the
  planner and needed a parity note – my miss (`node web/parity/check.mjs` before an app push); the laptop added it (0072edd7).
- Noted for [umbau], after the release: nothing pins the real host adapters in `AppViewModel` for `TourSession`, `TourEdits`,
  `PlanRecalcSlice`, `TourEndSlice` (the flow tests use their own fakes) – a source-text test like `RoutesWiringTest`.
- Noted, no fix tonight: L5's service half is guarded by source-text tests only (no test drives `RecordingService`);
  `StyleLoader.lastMapDir` is process-global and written per picture (a rollback could compare against the wrong folder –
  suspected, low, older than this release); planner `areas.ts` can show a stale state for one poll (heals itself); a
  round-trip search that dies with an `Error` (out of memory) leaves the map deaf to presses until „Abbrechen“; in the route
  window a tiny measured share after a long wait gives a very long time („98 Minuten“) until "dauert etwas länger…".
