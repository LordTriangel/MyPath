# C7 – GPX import / export


Part of the core features (`docs/core-features.md`): every core feature has JVM regression tests; a field bug first gets a failing test, then the fix.


| # | Core feature | Field bugs guarded against | Regression tests |
| --- | --- | --- | --- |
| C7 | GPX import / export (Komoot `<type>` → activity, written on export; the user's T level stays inside the type's band – hike T1–T2, mountaineering T3–T5; a changed activity is said in one grey line in the track panel; opening a recorded or imported tour from My routes switches to its activity the same way – "Activity set to Bike from the tour", owner); a planned route is exported by the one plan GPX shared with the web planner (`gpx/PlanGpx`: line, route points as named `<wpt>`, sport) and "Plan from track" on such a file brings back exactly those route points | every recorded tour stored as T3 → its GPX said "mountaineering", reopening set T3 | `GpxParserTest`, `PlanGpxTest`, `SportTypeTest` (`openingATour*`), `NewTourSacTest`, `OneTimeModelTest` (time without timestamps = the planned time), screenshot `ScreenshotTest.trackImportedActivityNote` |
