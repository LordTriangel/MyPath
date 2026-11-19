# [umbau-hp] release hardening for 0.18.2 – findings and what was done

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

4. **The wait windows' time line on the phone** (`RouteWait`, `SuggestWait`, `MapLoad`; core `OneBar.measured`): the time left
   was estimated from the bar, creep included – a route leg the router reports nothing of counted down „1 minute … 30
   seconds“ by itself (the owner's „noch etwa 5 Sekunden“, then minutes; seen in the planner, the same on the phone). Now it
   comes from the measured share only. Tests `WaitPromiseTest.routeWindow_aLegWithoutAnyProgressPromisesNoTime` (red before),
   `WaitProgressTest.aBarThatOnlyCreepsGivesNoTimeLeft`. The planner's side is the laptop's own fix in `WebWait` (3afebead).
5. **„Mehr“ pause times**: 12 new edge tests (`TourReviewTest`, `RouteMoreTest`: a stop at the very start / end, stepped stops,
   a recording gap, odd timestamps, midnight / summer-time change in four zones) – no bug found; two proven red by breaking the code.

## Open (in work or noted)
- Planner wait window (helper in work, branch, merged after review): a failed / stopped route closes the window as a success
  (100 % flash, learned shares from an unfinished run); a shared link whose engine fails can leave the window open; a ghost
  wait after Abbrechen (suspected).
- Second review round in work: the view model's host adapters and init order for the new slices, `TourEdits` line by line, the
  screens after "forwarders out"; a fresh review of the fixes above.
- Noted, no fix tonight: L5's service half is guarded by source-text tests only (no test drives `RecordingService`);
  `StyleLoader.lastMapDir` is process-global and now written per picture (a rollback could compare against the wrong folder –
  suspected, low); planner `areas.ts` can show a stale state for one poll (heals itself); a round-trip search that dies with an
  `Error` (out of memory) leaves the map deaf to presses until „Abbrechen“.
