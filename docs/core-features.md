# Core features – must never break (owner)

Rule: **every core feature has JVM regression tests; every field bug in a core feature first gets a failing test that reproduces it, then the fix.** Run `./gradlew testDebugUnitTest` before every release (the release workflow does).
One file per feature in `docs/core-features/` (full text: what must hold, field bugs guarded against, regression tests). General notes (end-to-end run, Demo location): [README](core-features/README.md).

| # | Core feature | In one sentence |
| --- | --- | --- |
| C1 | [Plan: hold a route point](core-features/C1-hold-route-point.md) | Hold a route point (within 48 dp) opens its menu; beside it adds a new point; Delete always deletes the held point. |
| C2 | [Plan: hold the map](core-features/C2-hold-map-add-point.md) | Hold the map or route line adds a point (smart insertion). |
| C3 | [Routing](core-features/C3-routing.md) | A route for every activity, offline, fallback to straight lines; way warnings and route hints. |
| C4 | [Recording](core-features/C4-recording.md) | Recording never loses a track and ends only deliberately; ascent / descent from terrain. |
| C5 | [Navigate](core-features/C5-navigate.md) | Follow the route, off-route alarm, re-route via the next route point. |
| C6 | [Map loads](core-features/C6-map-loads.md) | Missing data, cells or style never crash; the old map stays until the new one is complete. |
| C7 | [GPX import / export](core-features/C7-gpx.md) | Komoot types map to activities; the user's T level stays in the type's band. |
| C8 | [App update](core-features/C8-app-update.md) | Update from GitHub resumes after a broken connection. |
| C9 | [Plan sheet / point editor](core-features/C9-plan-sheet.md) | The full plan sheet is the point editor: delete, reorder, tap to show; small / profile / full steps. |
| C10 | [Routing data with the map](core-features/C10-routing-data.md) | Missing 1-degree routing parts load by themselves with every map region. |
| C11 | [Backup, restore, recycle bin](core-features/C11-backup.md) | Tours and plans are never lost: GPX copy in Documents/OnPath, restore, 30-day bin. |
| C12 | [Bus / car / lift in a tour](core-features/C12-vehicle.md) | Vehicle rides are detected and offered as a summary at End tour. |
| C13 | [No stale downloads](core-features/C13-no-stale-downloads.md) | Corridor map regions and routing parts belong to the current plan only. |
| C14 | [Offline maps clean up](core-features/C14-offline-maps-cleanup.md) | Clean up / Clear all shows what goes and what stays first. |
| C15 | [Plan links](core-features/C15-plan-links.md) | plan.onpath.earth links open as the plan (camera, App Link, text). |
| C16 | [Pair phone and browser](core-features/C16-pair-browser.md) | Pairing keeps plans in step between phone and browser. |
| C17 | [Phone setup](core-features/C17-phone-setup.md) | Setup guide before a tour: permissions, battery, location. |
| C18 | [Save asks for the name](core-features/C18-save-name.md) | Every Save opens a small dialog asking for the name. |
| C19 | [Tap / hold a POI](core-features/C19-poi-tap-hold.md) | Tap a POI symbol for details, hold it in Plan for its menu. |
