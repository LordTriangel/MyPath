# C14 – Offline maps clean up


Part of the core features (`docs/core-features.md`): every core feature has JVM regression tests; a field bug first gets a failing test, then the fix.


| # | Core feature | Field bugs guarded against | Regression tests |
| --- | --- | --- | --- |
| C14 | **Offline maps: Clean up / Clear all** (owner, Settings → Offline maps) – "Clean up" first shows what goes ("Remove 23 areas · 410 MB") and what stays (current plan, saved routes, running tour, where you are now + 10 km), nothing before Remove; "Clear all" (red, second confirmation) removes every downloaded area, the world overview stays, not offered during a tour. Removal goes through the store generation path: batched in one generation, never while the map is dragged, never during a tour (applied after it); routing parts only removed areas needed go too; every removal is in the events trace | – | `MapCleanupTest`, `CellDownloadTest.removalWaitsForTheTourAndTheGestureAndIsBatched` / `clearAllRemovesEveryAreaAndEveryRequest` / `freshInstallAndResetLoadTheMapAgain`, `MapsNeverTouchToursTest`, `StorageNotReadyTest` (storage not ready: nothing removed); screenshots `SettingsScreenshotTest.offlineMaps*` |
