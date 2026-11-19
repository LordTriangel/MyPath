# Rebuild phase 1: the cut for `:core` ([umbau], 2026-11-18)

Working paper of phase 1 of `architecture-plan-2026-11.md`; measured on main 0.18.0 (survey by script, 2026-11-18).
Deleted when phase 1 is tagged (`umbau-phase-1`).

## What the survey found (and the plan did not say)
- 14 candidate packages = 230 files, 35,500 lines (plan: ~40,000). Only 65 files (8,800 lines) can move as the tree
  is; 161 once `debug/Problems` no longer imports `android.util.Log`.
- 43 candidate files use the texts (`com.onpath.strings`), and `:strings` has only an Android target → it gets a `jvm()` target.
- 13 candidate files use Android's built-in `org.json` → `:core` compiles against the `org.json:json` jar (`compileOnly`;
  the tests already run on it).
- Hubs outside the candidates that most of them need: `data/Geo`, `data/Format`, `data/AtomicFiles`, `debug/Problems`,
  `debug/UserText`, `data/TourDb` (for the types `Tour`, `TourKind`), `poi/Poi` (MapLibre geojson), `routing/BRouterEngine`
  (Context), `recording/LocationTracker` (two constants, `GpsStatus`), `ui/StopText` + `SettingsScreen.label`.
- The planner takes 103 files + 29 tests by the list, in place (no copy); 31 stand-in files (plan: 27).
- "83 source-text tests": 75 read `src/main/java`; only 2 files are pure border guards (`SharedFilesRuleTest`,
  `SharedCoreRuleTest`). ~70 guard something else and stay; they get path updates, not deleted.
- CI: `unit.yml` and `web.yml` filter on `app/**` paths and run `testDebugUnitTest` – a `core/` folder needs a workflow
  change (pull request). Until then `:app:testDebugUnitTest` depends on `:core:test`, so CI and the release run the core
  tests without it.

## The cut
- `:core` = plain Kotlin JVM module, folder `core/`, sources `core/src/main/kotlin/com/onpath/app/<pkg>/`, tests
  `core/src/test/kotlin/…`, resources `core/src/test/resources/`. **Package names do not change** (no import edits in
  the app, the planner's parity hashes stay). Java 17. Dependencies: `api(project(":strings"))` (its new jvm variant),
  `api` kotlinx-coroutines-core + gson, `compileOnly` org.json (+ `testImplementation`), the BRouter jars as
  `compileOnly(files(app/libs))` until `:engine-brouter` exists. Tests: UTC, en-US, `onpath.stringsDir` as in `:app`.
- `:strings`: add `jvm()`; `Txt` + `XmlStrings` go to a source set shared by Android and JVM; the `@Composable` `str()` /
  `plural()` stay Android-only. If Gradle cannot serve both variants cleanly → fallback: `:core` as a KMP module with
  `androidTarget()` + `jvm()` and one shared source set (decide in piece B, say which in the commit).
- What goes into `:core`: the 14 packages minus their Android files, plus the pure hubs (`Geo`, `Format`, `AtomicFiles`,
  `Problems` with a log sink, `EventLog`, `UserText`, `AppSettings`, `HoldSetting`, `Tour` / `TourKind`, `Poi` model without
  geojson, the pure files of `recording/` that navigation needs). They keep their package (`com.onpath.app.data` …): a
  package may live in both modules.
- Android files of the candidate packages stay in `:app` under the same package (`AndroidBackupStore`, `Backups`,
  `AndroidNetworkStatus`, `AppCells`, `MapLoadService`, `DbTourStore`, `PlanSync`, `BRouterEngine`, `EngineProvider`,
  `AndroidPlaceGeocoder`, `SearchStore`, `AddressSearch`, `SearchDb`, `AppSuggest` + runner / service). Ports for them are
  phase 3 / 4 work, not phase 1 – phase 1 only moves what is already pure or is made pure by a small split.

## Pieces (each: own worktree, `gate --full`, fresh review, then main)
- **A – untangle the hubs, still inside `:app`** (behaviour-neutral splits): (1) `Problems`: `android.util.Log` behind a
  sink set in `OnPathApp`; (2) `Tour` / `TourKind` out of `TourDb.kt` into `data/Tour.kt`; (3) `GpsStatus`,
  `NET_MAX_ACCURACY_M`, `tracksFix`, `MoveWatch`, `PointFilter` – the pure parts out of the Android files of `recording/`;
  (4) `StopTokens`, `SettingsScreen.label`, `RoutesState.keepReplaced`, `AppUpdater.isNewer`, `Backups.placesChanged`,
  `PhoneCheck` use – pure parts down into `domain` / their core package; (5) `Poi`: the MapLibre geojson conversion into
  an app-side file; (6) `BRouterEngine`: the pure statics (`searchProgress` …) into a core file; `SyncPoll` no
  longer names `AppViewModel`.
- **B – the module and the move tool**: `:core` skeleton, `:strings` jvm target, `tools/dev/core_move.py` (computes the
  closed set that can move, `git mv`s sources + the tests that only test moved classes + their resources, rewrites the
  path-keyed places: `web/parity.json` keys, `web-shared-files.txt` resolution in `web/engine/build.gradle.kts` /
  `web/parity/check.mjs` / `SharedFilesRuleTest` (look in `core/` first), `.claude/rules/*.md` globs, `lint-baseline.xml`,
  `tools/ci/risk_tests.py` + `gate.sh` (`:core:test`), `release_check.sh`, the tools under `tools/` that name source
  paths). First move: the 65 files that can move today.
- **C – the rest in batches** with the tool after A is on main (expect ~160–200 files), tests with them.
- **D – planner on the module (done 2026-11-19)**: `web/engine` compiles the whole `core/src/main/kotlin` folder minus the
  files a same-path stand-in replaces (a stand-in without a counterpart fails the build) minus `web/engine/core-excludes.txt`
  (empty), plus the 11 files still in `:app` (`app/web-shared-files.txt` `[files]`, tests as before). Stand-ins `UserText`,
  `GpsStatus` and the geojson `Feature` / `Point` went. The `Problems` stand-in stays: the real one needs executors,
  `java.nio` and `FileDescriptor`, which TeaVM lacks. `SharedFilesRuleTest` stays, because the gate never builds the engine:
  it checks the import allow-list for the whole compiled set, the stand-in's `Problems` members, the app-side entries and the
  lock (`web/engine/core-shared.lock`, until web.yml knows `core/**`). `SharedCoreRuleTest`'s android border test went (the module guards it).
- **E – checkpoint**: unit CI, planner run, full E2E (`tools/ci/e2e_shards.sh`), tag `umbau-phase-1`.

Done when (plan): `:app` builds on `:core`; planner builds from the module; `:core:test` runs without the Android build.
