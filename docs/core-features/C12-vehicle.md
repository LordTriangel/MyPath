# C12 – Bus / car / lift in a tour


Part of the core features (`docs/core-features.md`): every core feature has JVM regression tests; a field bug first gets a failing test, then the fix.


| # | Core feature | Field bugs guarded against | Regression tests |
| --- | --- | --- | --- |
| C12 | **Bus / car / lift in a tour** (owner, `docs/design/vehicle-detection.md`) – nothing during the ride; at End tour a summary (timeline, small map) with Save only until … / Save all, rides not counted / Split into two tours / Save as recorded; the choice sets km / time / ascent, the drawn track, Export and the backup file; raw points always kept (`tours.rides`, "Edit rides" in My routes) | – (field data: Taser gondola + car) | `RideDetectorTest`, `OwnerTaserTourTest`, `RideViewTest`, `RideApplyTest`, `RideBackupTest`, `TourSchemaTest`, screenshot `RideSummaryScreenshotTest` |
