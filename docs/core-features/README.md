# Core features – general notes

Index: `../core-features.md`. Owner: "please note to have test cases for this scenario. Core features."
Rule: **every core feature has JVM regression tests; every field bug in a core feature first gets a failing test
that reproduces it, then the fix.** Run `./gradlew testDebugUnitTest` before every release (the release workflow does).

End to end on an emulator: `app/src/androidTest/.../CorePlanE2ETest` – the real app and MapLibre
map, a finger held on the screen (UiAutomator): hold → Start here, hold → point added, hold a point → menu → Delete,
the same after a map style reload and on a turned map. Runs in `.github/workflows/e2e.yml` on every app push to main,
beside the release (owner); the GitHub release gate is the short `StartSmokeE2ETest` (the app opens, also with a
saved plan – that start once crashed); a release made on donald runs the Release-Rundgang locally first
(`docs/release-rundgang.md`). A red full run is fixed at once. On failure the run keeps
screenshots, the app state and logcat as the artifact "e2e-report".

**Demo location** (Developer tools only – screenshots of navigation, re-planning during a tour, the slide switch and
the lock screen without the owner's real position; `recording/DemoLocation` + `DemoPath`): Settings → Diagnostics →
Developer tools → "Demo: ride the planned route" (else the shown track) or "Demo: stand at the map centre". Simulated
fixes (5 m, bearing, speed, height from the line) go into both `LocationTracker`s – the app's and the recording
service's – so Navigate, turn cues, arrival, recording, re-routing and the notification run as on a real tour; real GPS
is ignored meanwhile. Speed = the plan's time model with the user's pace, × 1 / 5 / 20 (the fix time runs on a
simulated clock, so the track keeps realistic speeds; moving time / pace shown are realistic at 1× only); "Stand still"
pauses. A small orange DEMO tag sits beside the scale bar; a demo ride ends with its tour (`DemoLocation.endWithTour` – no DEMO / Sim left after the sim ride), a standing demo stays; tours recorded meanwhile are named "Demo · …"; the problem
log and diagnostics export say when it ran. Never stuck on: process memory only (an app restart = real GPS), stops by
itself after 6 h, Stop in Developer tools. Tests: `DemoPathTest` (positions, speed, bearing, end),
`DemoLocationTest` (demo vs real switch, clock, stop, and the demo ride through `PointFilter` + `NavReplay`).

To add next: recording survives Plan mode, map update with a real cell generation. (Navigate → Plan with a followed
plan, markers back: `CoreFieldE2ETest.navigateThenBackToPlanStillDeletes`.)
