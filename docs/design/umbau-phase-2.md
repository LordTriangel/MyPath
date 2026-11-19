# Rebuild phase 2: `:state` – the cut of `AppViewModel` ([umbau], 2026-11-19)

Working paper of phase 2 of `architecture-plan-2026-11.md` §5 and of row **T** for the `ui` package. Design only – no
code changed, no Gradle run. Measured on main @ b3a343bd7 by script and by reading; the per-member index (685 rows:
line, size, kind, slice, what it touches, which files read it) is **`umbau-phase-2-members.tsv`** (line numbers are
those of b3a343bd7 – after the first piece use the names). Deleted when phase 2 is tagged (`umbau-phase-2`).
"Not verified" marks what I could not prove by reading.

## 0. In one table

| Question | Answer |
| --- | --- |
| Module | **own module `:state`** (`state/`, plain JVM like `:core`, depends on `:core`); package names stay (`com.onpath.app.ui…`) |
| State representation | **Compose runtime snapshot state stays** (`mutableStateOf`), runtime only – no Compose UI on the classpath. Screens change nothing |
| What a slice is | a class with snapshot state + the user's actions as functions; everything outside comes through a **store port** (blocking data) and a **host port** (the rest of the app); background work on the view model's scope passed in + an `io` dispatcher; no work in the constructor (`start()`) |
| Pieces | **21** (+ 5 optional that move the Android-bound flows behind ports) – §4 |
| `AppViewModel` | 5,760 → ≈ 1,300 with one-line forwarders → **≈ 700 after the last piece** (screens read `vm.<slice>.x`) |
| Nights | **5** (4–6) with two helper lanes; +2 with the optional pieces |
| Riskiest | the tour session (start, follow, the service's collectors): pieces 18–19 – §4.3 |
| Plan's "mirrors 52 → under 15" | **not reachable by phase 2**: only 8 of the 52 watch state logic (§2). Proposed done-rule: those 8 → 0 |

## 1. The map of `AppViewModel.kt` (5,760 lines)

### 1.1 Numbers

| Fact | Number |
| --- | --- |
| Members of the class | 685: 404 functions (266 public, 3 internal, 135 private), 268 properties, 7 `init` blocks, 6 nested types |
| State holders (`mutableStateOf` / `IntStateOf` / `StateListOf`) | **80** (the plan said 78); 6 private |
| Public members | 436; **110 are never read as `vm.x`** outside the file (only through a forwarder's own slice, tests or nothing) |
| Who reads `vm.` | `MapScreen` 148 members, `MainActivity` 140, `MapOverlays` 39, `MapHost` 34, `NavigateScreen` 25; then only the slices' hosts. All other Composables take values and lambdas |
| E2E (`androidTest`) | 27 members read through `ViewModelProvider` (`E2EBase.vm`, 8 classes) |
| Unit tests | none constructs the view model (it cannot run on the JVM); **33 test files read its source text** (56 tests, §1.6) |
| Coroutines | 100 `launchSafe`, 70 `Dispatchers.IO`, 77 `viewModelScope`; 4 own single threads (`routingDispatcher`, `backgroundRouting`, `checkRouting`, `holdRouting`) + `planCopyDispatcher` (IO, one at a time) |
| Android-bound | `getApplication` 49, `Uri` in 5 functions, `Intent` in 4, bitmaps in 3, `SystemClock` 3; **30 lines talk to the recording service** (§4.3) |
| `Problems.` / `Txt.` | 231 / 61 calls |
| Process death | **no `SavedStateHandle`** (4 `rememberSaveable` in screens only). What survives is read in property initialisers: plan points, pins, saved link (`PlanStore` → prefs), activity, T level, settings, camera, Zuletzt id, `followIsTrack` (prefs), the followed line + session (`FollowStore`, files), multi-day state (prefs), the tour itself (database: `findActiveRecording` in `init`) |

### 1.2 What the user does → lines (every line of the file is in exactly one row)

| Group (target) | Lines | Main functions | State holders (° private) |
| --- | ---: | --- | --- |
| **PlanRoute** – route run | 889 | `recalc` (151), `perform`, `routeEvent`, `cancelRun`, `answerLongRoute`, `answerSlowPlan`, `planAnyway`, `cancelRouting`, `snapToRoute`, `aliasSnappedLegs`, `routingEngine`, `selectActivity`, `selectSac`, `withModelTime` ×2, `tourTimes`, `openTimeDetails`, `routeSpots`, `tapWarningLine`, `routingDataArrived`, `gated`, `checkRoutingData`, `refreshRd5` | activity, sac, route, routeRun°, routingSpot, routeError, highlight, planReach, routeChangedNotice, syncRoutingNote, spotsOpen, spotLine, routeProgress, routeShare, pointMenuTarget, timeDetails, guidanceEndedByEmptyPlan |
| **PlaceMenus** – POI, hold menus, kept places | 630 | `holdOnMap`, `measureHold`, `placeMenu`, `addPoint`, `insertionFor`, `addPoiPoint`, `addPoiWith`, `openPoiMenu`, `openPointMenu`, `openPinMenu`, `removePin`, `closeMapOverlays`, `addPlace`, `placeCardUi`, `toggleFavouritePlace`, `toggleHomePlace`, `refreshPinnedKinds` | holdAddMenu, holdMeasuring, planPoi, poiMenu, firstPointMenu, routePeek, savedPlaces, poiKindsKnown°, poiKindsFound° |
| **Routes** – My routes: open, go, shown track | 557 | `openTour` (62), `refreshTours`, `requestOpen`, `goFromRoutes`, `undoNewPlan`, `withFollowedPlan`, `tourReview`, `exportFollowedPlan`, `proposeName`, `nameRecordedTour`, `planFromShownNow`, `planFromLine`, `deleteTour`, `openSharedPlan`, `sharePlan` | planFromTrackBusy (+ `shown`, counted under TourSession) |
| **TourSession** – mode, start, follow | 519 | `enter`, `startNavigation`, `continueNavigation`, `followPlanned`, `followEditedPlan` (40), `followAhead` (42), `followAgain`, `setFollow`, `clearFollow`, `followShown`, the 7 question answers, the big `init` (110: start-up check, `startFailed` / `state` / `finished` collectors) | mode, navStartZoom, selectedPoi, navTurn, screenRest, shown, followName, followLine, straightAsk |
| **PlanEdit** – points | 402 | `change`, `splitOf`, `answerRouteBack`, `oneLeftGoesFromHere`, `endGuidanceIfPlanEmpty`, `removePoint`, `movePointInList`, `deletePointFromList`, `deletePointFromMenu`, `reverseRoute`, `roundTrip`, `undoStep`, `redoStep`, `clearPlan`, `refreshPointNames` | planSize, tapHoldHint, offlineReady, offlineInfo, routeBackAsk, nearbyNames°, routeDetailsOpen |
| **Save** (C18) | 354 | `requestSave`, `askSave`, `confirmSave`, `cancelSave`, `requestCopyRoute`, `copyRoute`, `saveRoute`, `insertPlanned`, `overwriteSaved` (54), `renameTour`, `setFavourite`, `keepAsDraft`, `saveNewPlanAtStart`, `updateSavedAtStart`, `setSaved` | saveAsk, routesProblem |
| **MapView** – camera, compass, scrub | 298 | `onCompass`, `locate`, `navScrub`, `onMapGesture`, `checkFreeLook`, `onAppStart` / `Stop`, `showWholeRoute`, `showPlanAndMe`, `showPlanWhole`, `fitFirstRoute`, `showSpot`, `showPlanGap` | northTick, recentreTick, planGpsNote, planGpsNoteTick, appOnScreen, followMe, startModeKnown, startCameraGen |
| **TourEnd** | 270 | `askEndTour`, `endTourWith`, `endTour`, `endTourAndDelete`, `undoShortRecording`, `openEditRides`, `applyEditRides`, `askSaveUnfinished`, `saveUnfinished`, `deleteUnfinished`, `continueRecording`, `startRecording`, `binFinishedTour`, `isShortRecording` | editRides |
| **ImportExport** | 270 | `importGpx` (66), `importData`, `exportAll`, `exportTour`, `share`, `restoreFrom`, `checkRestoreOffer` | dataBusy, styleVersion, restoreOffer, locationAsking, restoreProgress |
| **ActivityCheck** | 240 | `openActivityCheck`, `startCheck`, `checkRow`, `chooseActivity`, `chooseHikeLevel`, `applySwitch`, `switchPlanActivity`, `syncNavActivity` | activityCheck |
| **Settings** + diagnostics | 192 | `updateSettings`, `openSettings`, `settingsBack`, `unlockDevTools`, `loadRoutingInfo`, `mapGestureMeasured`, `diagnosticsState`, `recheckData` | settings, syncNote, settingsShown°, settingsOnTheWay, page°, routingInfo, devToolsShown, diagnosticsSeenMs, packageCheck, restartForData |
| **Sync** wiring + synced routing | 190 | `syncDriver` host, `syncRoutingCheck`, `followSyncedSavedPlan`, `routeSyncedPlans` (41), pairing forwarders | – |
| **RoutePictures** | 114 | `overviewShot`, `routeSnapshot`, `snapshotStyle`, `thumbMaker`, `overviewMaker`, `kickThumbs` | thumbVersion |
| **Demo** (developer tools) | 84 | `startDemoRide`, `startNavSim`, `startDemoTour`, `navSimFollow` | – |
| **Notices** | 47 | `say`, `sayError`, `sayInfo` | errorCard, routesNote, settingsNote, mapNote, mapNoteTick, busy |
| Forwarders to existing slices | 274 | map data 93, phone setup 52, search 36, tour 33, updates 29, bin 16, routes 15 | events |
| Wiring (construction, hosts, last `init`, `onCleared`) | 278 | | missing, dataScreenDismissed |
| File head (imports, `ShownTrack`, `EndAsk`, `UiEvent`) | 152 | | |

### 1.3 Slices that already exist – the pattern, and where it bends

| Class (`ui/…`) | Lines | Holds | Android-free today? | Ports | Test | How the view model wires it |
| --- | ---: | --- | --- | --- | --- | --- |
| `routes/RoutesState` | 260 | My routes: open, tab, list, Zuletzt, bin rules | **yes** (Compose runtime only), **no coroutines** | `RoutesStore` (7 db calls), clock | `RoutesFlowPropertyTest` 572, `RoutesOpeningTest` | anonymous `RoutesStore` over `TourDb`; 15 lines of forwarders |
| `routes/RecentPlans` | 236 | Zuletzt + bin as actions | **yes** | `RecentPlansHost` (9), scope, `io` | `RecentPlansTest` 327 (on `JdbcTours`, two `ManualExecutor`s) | anonymous host (15 lines) |
| `tour/TourController` | 181 | end sheet, arrival, saved box, interrupted tour | yes, but imports `RecordingClient` (Context / Intent) for one adapter function and `EndAsk` from `AppViewModel.kt` | `TourRecorder` (4 commands) | `TourFlowPropertyTest` 370, `UnfinishedStartGuardTest` | `tourRecorder(recording)`; 33 lines of forwarders |
| `plan/PlanDocument` | 166 | points, Undo / Redo, pins, saved link, stored at once | yes, but `PrefsPlanStore` in the same file takes `Prefs` | `PlanStore` | `PlanDocumentTest` 192 | `PrefsPlanStore(prefs)`; private in the view model |
| `suggest/SuggestSlice` | 247 | round-trip suggestions | **yes** | `SuggestHost` (6), scope | `SuggestSliceTest` 170 | `AppSuggestHost` (Android, stays in `:app`) |
| `sync/SyncDriver` | 164 | sync triggers, pull, marks | no: `PlanSync.State` (nested in an Android class), `SyncPoll` (pure, still in `:app`) | `SyncEngine`, `SyncHost`, clock, sleep | `SyncDriverTest` 263 | anonymous host (27 lines) |
| `sync/PlanLinkFlow` | 122 | plan links | yes, needs `NoticePlace` (in a Composable file) | `Host` | `PlanLinkFlowTest` 167 | anonymous host (27 lines) |
| `MultiDayFlow` | 294 | multi-day state | needs `Prefs` | – | `MultiDayFlowTest` 180 | inside `MultiDaySlice` |
| `plan/MultiDaySlice` | 371 | multi-day actions | **no – takes `vm: AppViewModel`** (19 members), `TourDb`, `Backups`, `viewModelScope`, `snapshotFlow` | none | none of its own (E2E `MultiDayTourE2ETest`) | `MultiDaySlice(this, plan, ::change, …)` – **the pattern to correct** |
| `MapDataFlow` | 979 | cells, routing data, loading UI | no: `Application`, `TourDb`, `LocationTracker`, `MapLoadService`, `RoutingDataLoader`; UI models from 5 Composable files | `Host` (20) | rule tests only (G10) | `by lazy`, 93 lines of forwarders |
| `SearchFlow` / `search/SearchSession` | 266 / 394 | search view, shared places / the search itself | no: `SearchStore` (Context), `SearchDb`, `AddressDb`, Photon | `Host` (≈ 20) | `SearchIndexSwapTest`, `SearchStableListTest` | `by lazy` |
| `PairingFlow`, `PhoneSetupState`, `AppUpdates` | 156 / 168 / 96 | pairing / setup guide / updates | no: `Prefs`, `PlanSync`, `Application`, `Build.VERSION` | `Screens` / – / lambdas | `PairEntryTest`, `PhoneCheckTest` … | lazy / direct |

The pattern to continue is `RecentPlans`: **constructor = (other slices, store port, host port, scope, `io`)**, state as
`var x by mutableStateOf(..)` with `private set`, launches with `scope.launchSafe(area, what)`, blocking calls in
`withContext(io)`, tested with `Dispatchers.Unconfined` or two `ManualExecutor`s. To correct: a slice never gets the
view model (`MultiDaySlice`), never an `Application` (`MapDataFlow`, `AppUpdates`), and its UI-model types do not live
in Composable files.

### 1.4 The 28 `ui/` files without `@Composable`
`AppViewModel` (5,760) · the 13 slices above · pure rules that only need a type out of a Composable file:
`SettingsSummary` 205, `WholeRoute` 145, `LoadNames` 131, `routes/RouteRowActions` 96, `PlanGps` 76, `RoutingLoads` 54,
`MapControlsEnabled` 43, `LongRouteCancelCamera` 36, `ExpandGesture` 26 · Android edges that stay in `:app`:
`setup/PhoneStateReader` 98, `suggest/AppSuggestHost` 53, `Haptics` 35.

### 1.5 `AppViewModelSizeTest`, `ViewModelInitOrderTest`, the launch rule
- `AppViewModelSizeTest`: `AppViewModel.kt` ≤ `MAX_LINES` = 5,760. Audit verdict DROP; **kept through phase 2 as the
  ratchet** (the lead lowers it at each merge, helpers never touch it), dropped with the last piece for the plan's
  one size rule (no file above 800 lines).
- `ViewModelInitOrderTest` (2 source-text tests): (1) every property `recalc()` assigns before its job is declared
  above `init { recalc() }`; (2) every `by lazy` reachable from an `init` block is declared above it. The bug behind
  it: **0.9.1 crashed at start** (new Compose states declared below the `init` that routes the saved plan – null while
  it ran; 0.9.2, 4329fded0), then the same with a lazy (`planTimes`, fe4d11045). Today 7 `init` blocks and 18 lazies.
- `SafeLaunchRuleTest`: no bare `viewModelScope.launch` anywhere (a failure there ended the app). `launchSafe` /
  `CoroutineScope.reported` are in `:core` (`debug/SafeLaunch.kt`) – usable from `:state` as they are.

### 1.6 Tests that read the view model's source (33 files, 3,266 lines, 56 tests) – each breaks when its lines move
| Moves with | Files (tests that read the source / verdict) |
| --- | --- |
| Save | `SaveDialogTest` 2 (G04), `PlanAutoSaveTest` 2 (G04), `BackgroundRoutingRuleTest` 1 of 2 (G04), `SavePathsTest` 1 of 4 (G04) |
| PlanEdit | `PlanActionsTest` 1 (G04), `PlanSheetLayoutTest` 1 (DROP), `SavePathsTest` 1, `TourEditTest` 1 (G05), `StartAtPointOneTest` 1 (KEEP), `PlanEditsTest` 1 (KEEP) |
| PlanRoute | `ViewModelInitOrderTest` 2, `FollowLineLegsTest` 3 of 4 (G05), `SlowPlanTest` 1, `LongRouteCancelCameraTest` 1, `RouteWarningsTest` 1, `OneTimeModelTest` 1, `SyncedRoutingTest` 1, `BackgroundRoutingRuleTest` 1 |
| PlaceMenus | `FoundPlacePinTest` 4 (G09), `FarDestinationTest` 3 (field bug, KEEP), `WholeRouteTest` 1 |
| MapView | `WholeRouteTest` 1, `PlanLayersTest` 1 |
| Settings | `SettingsSummaryTest` 1 (G12), `ReliefAutoTest` 1, `RoutesOpeningTest` 1 (G13) |
| TourEnd / TourSession | `SavePathsTest` 2, `ShortRecordingBoxTest` 2, `TourEndPathsTest` 1 (G05), `ArrivalBoxTest` 1 (G05), `InterruptedLogTest` 1 (G08), `FollowLineLegsTest` 1, `UxReviewFixesTest` 1 (G09) |
| Stay valid (they read screens or all files) | `NoticesTest`, `SavedTourBoxTest` (DROP), `FirstMapCardTest`, `CorePlanLinkTest`, `AppViewModelSizeTest` |

Rule for every piece: a source pin whose lines move is **replaced by an assertion on the slice** in the same piece
(the list above is the helper's checklist); it is never just re-pointed at the new file.

## 2. The planner side

| Fact | Measured |
| --- | --- |
| Engine | `web/engine`: Kotlin compiled by TeaVM 0.16 to Wasm; runs in a **Web Worker** (`web/app/src/engine/worker.ts`); the page talks to it by messages |
| `WebApi.kt` (1,036 lines) + `WebSync` 432, `WebDays` 310, `WebSuggest` 169, `WebSearch` 157, `WebKept` 116, `WebPace` 114, `WebImport` 98, `WebPlanRules` 77 | **stateless functions**: strings in (points `lat,lon;…`), JSON out. The only state in the engine is the Undo history and the routing files |
| State of the planner | **in TypeScript on the page**: `main.ts` 2,799 (plan points, edits, menus, route run, save entry, camera), `sync.ts` 1,129, `plans.ts` 1,009 (My plans, save dialog, copy), `multiday.ts` 493, `search.ts` 434, `suggest.ts` 393, `gpximport.ts` 381, `recent.ts` 243 (Zuletzt + bin), `keptplaces.ts` 187, `longroute.ts` 136 |
| What the engine build has | BRouter jars, gson, org.json, threetenbp. **No `kotlinx.coroutines`, no Compose runtime.** Stand-ins: `Problems`, `UserText`, `GpsStatus`, `DataFiles`, `Txt`, `StringResource` |
| Sources | by list (`app/web-shared-files.txt`, 136 entries); piece D of phase 1 (the `core/` folder as a source folder) is being built, not on main. 9 `:core` files import coroutines (`SafeLaunch`, `Problems`, `HoldPaths`, `MapCells`, 3 suggest files, `SyncMarks`, `SharedPlaceResolve`) – how piece D keeps them out I did not see |
| Coroutines under TeaVM | **not verified** – nothing in the engine uses them today; treat as "not available" |

### 2.1 The 52 mirrors of `parity.json` by what they watch
| Kind | Count | Entries (app file) | Can a slice retire it? |
| --- | ---: | --- | --- |
| **State logic re-written in TS / Kotlin stand-in** | **8** | `AppViewModel` (#18: long-route ask, `importGpx`, `planFromShown`, `withModelTime`, `addPlace`, `requestSave`, `holdOnMap`, kept places …), `routes/RoutesState` (#38 → `recent.ts`), `search/SearchSession` (#3), `search/SearchStore` (#50 → `WebKept`), `WholeRoute` (#33), `LongRouteCancelCamera` (#49), `data/Prefs` pace keys (#27), `domain/routes/RoundTrip` (#43) | **yes – this is phase 2's share** |
| Look of a Composable (rows, chips, dialogs, colours) | 17 | `Screens`, `MapOverlays`, `PlaceCard`, `MapFooter`, `SettingsScreen`, `ElevationProfile`, `TourBar`, `Panels`, `PointsSection`, `PaceSettings`, `CellCard`, `RouteSortChips`, `RouteThumbImages`, `ActivityFilter`, `Favourites`, `BackupScreens`, `SearchScreen` | no – HTML / CSS is a second screen by nature |
| Pure rules already in `:core`, written again in TS (speed per pointer move, or history) | 13 | `PlanStatus`, `PlanPoints`, `RouteRules`, `PinnedPoiKinds`, `CellMath`, `CellStore`, `CellTriggers`, `Format`, `RouteSort`, `RoutesTab`, `LivePlan`, `PinMenuPlace`, `PoiDensity` | by the laptop, with an engine call each – independent of `:state` |
| Map (MapLibre Native vs GL JS) | 8 | `MapController`, `LayerSettings`, `OptionalSources`, `StyleLoader`, `Overview`, `RouteSnapshot`, `ThumbMaker`, `RouteThumbs` | no |
| Engine stand-ins | 6 | `Problems`, `UserText`, `GpsStatus`, `DataFiles`, `Txt`, `XmlStrings` | 4 by piece D of phase 1; the texts stay |

So after phase 2 + piece D the honest floor is **52 − 8 − 4 = 40**; under 15 would also need the 13 pure-rule mirrors
(laptop) and still leaves 17 + 8 that no shared code removes. **Proposal:** split `mirrors` into `logic` and `look`
(`parity.json` gets a `kind` per entry) and make phase 2's done-rule "`logic` mirrors of state: 8 → 0".

### 2.2 What a slice must look like so that `WebApi` can call it
1. **Web-ready = no coroutine in the file.** The decisions of a slice sit in synchronous functions on synchronous
   ports; anything that waits (database, router, name lookup) is a second class in the same package that drives the
   first. Today's examples: `RoutesState` (sync, web-ready as it is) under `RecentPlans` (driver); `RouteRun` (reducer in
   `:core`) under `recalc` (driver); `SaveFlow` (rules) under the save writes.
2. Imports only `:core`, `com.onpath.strings` and `androidx.compose.runtime.{mutableStateOf, getValue, setValue,
   mutableStateListOf, mutableIntStateOf}`. For TeaVM the laptop adds **one stand-in file** for these five names (a
   plain holder, ≈ 40 lines, like its `StringResource` stand-in) – not verified that the real Compose runtime jar
   compiles under TeaVM, so the stand-in is the plan.
3. State is readable as plain values (`slice.x`), actions return nothing or a small result; the web reads the state
   after each call and serialises it itself (its engine lives in a worker).
4. Ports are small interfaces with plain data (`Tour`, `GeoPoint`, `String`) – the web implements `RoutesStore` over
   its stored plans, `PlanStore` over `localStorage`, the clock over `Date.now()`.
5. A list `state/web-ready.txt` names the web-ready files; a rule test in `:state` checks rule 1 + 2 for them (one
   test, replaces the per-file parity hash for these files). The laptop compiles the list.

| Slice | Web-ready part | Mirror it retires (when the laptop calls it) |
| --- | --- | --- |
| `RoutesState` (+ `RouteRowActions`) | all of it | #38 `RoutesState` → `recent.ts`, `plans.ts` |
| `SaveSlice` | `SaveDialog` (ask / confirm / cancel / copy, which write) | #18 part "requestSave", `saveKeepsOtherEdit` call sites |
| `PlanRoute` | `RouteRun` + long / slow answers + cancel camera | #18 part "long-route ask / answerLongRoute / cancelRun", #49 |
| `PlaceMenus` | hold / place decisions (sync; the measuring is the driver) | #18 parts "holdOnMap, placeMenu, addPlace, poiAsNewDestination, pinnedPoiKinds" |
| `KeptPlaces` (in PlaceMenus) | toggle home / favourite, recent searches | #50 `SearchStore`, #18 "keepPlaces" |
| `MapView` | `WholeRoute` decisions | #33 |
| `Routes` | `RoundTrip` (move to `:core`), plan from a track / GPX import decisions | #43, #18 parts "importGpx, planFromShown" |
| `Settings` | pace keys | #27 |
| Search (optional pieces) | `SearchSession`'s offline-then-online order | #3 |

`AppViewModel`'s own entry (#18) goes when the last of its parts moved (piece 12 at the latest, §4).

## 3. The target shape

### 3.1 Module: `:state`, not a package in `:core`
| | `:state` (chosen) | package inside `:core` |
| --- | --- | --- |
| Border the compiler guards | rules (`:core`) cannot see screen state; `:state` cannot see Android or Compose UI | none between rules and state |
| Web | `:core` stays coroutine-poor for the folder compile of piece D; the web takes `:state` file by file (`web-ready.txt`) | every slice with a coroutine lands in the web's source folder |
| Build / test time | a slice edit recompiles `:state` + `:app`, not `:core`; `:state:test` runs without the Android build (as `:core:test`, ≈ 1 min) | every slice edit recompiles `:core` and runs its 1,744 tests in the gate's selection |
| iOS (phase 3) | two modules turn multiplatform one after the other; `:state` may keep Compose runtime + coroutines, `:core` needs neither | one big module with both |
| Cost | one more module: `settings.gradle.kts`, `gate.sh` / `risk_tests.py` (`:state:test`), `release_check.sh`, CI path filters (`state/**` – a workflow change = pull request; until then `:app:testDebugUnitTest` depends on `:state:test`, as phase 1 did for `:core`), `core_move.py` gets a `--module` | none |

Shape: `state/build.gradle.kts` = `:core`'s (kotlin jvm 17, animal sniffer API 26, UTC / en-US / `onpath.stringsDir`);
`api(project(":core"))` (brings coroutines-core and, through `:strings`, `org.jetbrains.compose.runtime:runtime:1.7.3`
– name it explicitly as `api` so it does not depend on that accident); sources `state/src/main/kotlin/com/onpath/app/ui/…`
(**packages unchanged**: no import edits in screens, parity keys stay path-rewritten only), tests `state/src/test/kotlin`,
test helpers shared through `core/src/testkit/kotlin` as today.

### 3.2 State representation
| Option | Existing slices | Screens | Web (TeaVM) | iOS | Verdict |
| --- | --- | --- | --- | --- | --- |
| **Compose runtime snapshot state** | all 13 use it | no change | 40-line stand-in (not the real jar) | Compose MP: as is. SwiftUI: a small observer over `snapshotFlow` | **chosen** |
| `StateFlow` | 2 use it for service state | every read becomes `collectAsState` – ≈ 400 call sites | needs coroutines – not there | fine for both | no: breaks "screens change nothing", locks the web out |
| Own observable holder (`Cell<T>` + factory, Compose-backed in the app) | none | no change | plain holder | fine | no: the same as the stand-in, but paid in all 13 slices and every new one |

Rules: `:state` depends on the Compose **runtime** artifact only (no `ui`, no compiler plugin – a `@Composable` in
`:state` does not compile); state is written on the main thread only; `snapshotFlow` is allowed in drivers, not in
web-ready files.

### 3.3 What a slice is
```kotlin
class SaveSlice(                       // state/src/main/kotlin/com/onpath/app/ui/routes/SaveSlice.kt
    private val plan: PlanDocument,    // other slices it works on: passed in, never looked up
    private val routes: RoutesState,
    private val store: SaveStore,      // STORE port: blocking data calls, may throw; only called inside withContext(io)
    private val host: SaveHost,        // HOST port: what it reads of / asks from the rest of the app (main thread)
    private val scope: CoroutineScope, // the view model's scope (Main.immediate + problem handler)
    private val io: CoroutineDispatcher,
) {
    var ask by mutableStateOf<SaveAsk?>(null); private set
    fun request(then: (() -> Unit)? = null) { … }   // the user's actions; main thread
    fun start() { … }                  // collectors and start-up work – never in the constructor
}
```
- **Ports are implemented in `:app`** by the view model as small objects (as `routesStore`, `RecentPlansHost` today).
  Shared ones, defined once in `:state` (`ui/Ports.kt`): `Say` (`say(text, kind, place)` – `NoticeBoard` implements
  it), `Camera` (`fit`, `centerOn`, `panTo` – the view model turns them into `UiEvent`s, which keep their `Intent`
  and stay in `:app`), `Clock` (`nowMs`, `elapsedMs`), `TourService` (the pure half of `RecordingClient`, §4.3).
  Texts: `Txt.s(Res.string.x)` directly (works in `:core` today). Errors: `Problems.*` directly.
- **A host port is the decoupling that lets pieces land in any order**: a slice never names a neighbour that is not a
  slice yet; it asks its host (`host.route`, `host.followEditedPlan()`), and the view model answers from its own
  property today and from the neighbour slice later. Hosts shrink in the last piece.
- **Threading – no timing change by construction:** the slice gets the *same* scope object the code runs on today
  (`viewModelScope`, i.e. `Dispatchers.Main.immediate`), `io = Dispatchers.IO`, and the four single threads are passed
  as dispatchers by name (`routing`, `backgroundRouting`, `checkRouting`, `holdRouting`; the view model keeps and closes
  them). A moved `viewModelScope.launchSafe("app", "x", Dispatchers.IO) { … withContext(Dispatchers.Main) { … } }`
  becomes `scope.launchSafe("app", "x", io) { … withContext(main) { … } }` with `main` = the scope's own dispatcher –
  same hops, same order. `SafeLaunchRuleTest` gets one more pattern: no bare `scope.launch` in `state/`.
  The main thread is never blocked: a store port is called only inside `withContext(io)` – the one exception that
  exists today stays an exception by name (`RecentPlans.keep` writes one row on the calling thread before the plan
  leaves the map, tester review 2026-11-07).
- **Process death:** a slice reads what it restores in its property initialisers from its store port (as
  `PlanDocument` does) and writes at once on change. Nothing new is persisted in phase 2; no `SavedStateHandle`.
- **Init order – the 0.9.1 bug made impossible instead of pinned:** no slice does work in its constructor;
  the view model constructs all slices at the top and calls `start()` on each in **one `init` at the end of the file**,
  in a fixed order (stores → plan → route → tour → map data → sync). Kotlin's rule (a property below a running `init`
  is null) cannot bite code that only runs after the last property. `ViewModelInitOrderTest` is replaced in piece 12
  by `PlanRouteStartTest` ("a stored plan is routed by `start()`; constructing alone routes nothing") and dropped in
  piece 21 when the view model has one `init`.

## 4. The cut list

### 4.1 Pieces in landing order
Lines = lines that leave `AppViewModel.kt` → lines that come back as wiring + forwarders. "VM after" assumes the order
of the table. Lane: A = plan side (file lines 853–1116, 1289–1375, 1694–3340), B = routes / tour side (3341–5330),
C = edges (204–852, 1132–1229, 1538–1693, 5331–5596). A piece's small islands outside its lane (Save: 1100–1116;
PlanRoute: 750–812, 5597–5672; the tour: 1117–1131, 1263–1288, 1426–1537) are in the TSV's `slice` column. Risk: L / M / H.

| # | Piece | Lane | Moves (lines out → back) | VM after | Ports (new) | Risk | E2E class on GitHub |
| ---: | --- | --- | --- | ---: | --- | --- | --- |
| 1 | **`:state` module + `RoutesState` / `RecentPlans` / `RouteRowActions` moved + `NoticeBoard` cut** | – | 47 → 12; types `NoticeKind`, `NoticePlace`, `Notice`, `Busy`, `NoticeRouting` out of `Notices.kt` | 5,725 | `Say` | L | `StartSmokeE2ETest` |
| 2 | **Hub types + the pure slices moved**: `TourController` (+ `EndAsk`), `ShownTrack`, `PlanDocument` (`PrefsPlanStore` stays), `SuggestSlice`, `PlanLinkFlow`, `PlanSize`, `SettingsPage`, `SyncNote`, the 9 pure rule files of §1.4 with the types they need | – | 35 → 0 | 5,690 | `Camera`, `Clock` | L | – |
| 3 | **`SaveSlice` (C18)** | B | 354 → 70 | 5,405 | `SaveStore`, `SaveHost` | M | `CorePlanE2ETest` |
| 4 | `SettingsSlice` (+ diagnostics state, relief auto) | C | 192 → 35 | 5,250 | `SettingsStore`, `SettingsHost` | L | – |
| 5 | `MapViewSlice` (camera, compass, scrub, fits) | C | 298 → 50 | 5,000 | `Position`, uses `Camera` | L–M | `CorePlanE2ETest` |
| 6 | `RoutePictures` + `DemoRides` – helpers that **stay in `:app`** (bitmaps, `DemoLocation`) | C | 198 → 25 | 4,830 | – | L | `RouteThumbE2ETest` |
| 7 | `ImportExport` – helper in `:app` (`Uri`, `Intent`, `FileProvider`); its decisions already in `:core` | C | 270 → 35 | 4,595 | – | M (C7, C11) | `CoreBackupE2ETest` |
| 8 | `PlanRoute` I: engine + threads + leg cache, activity / T level, time estimate, warnings | A | 300 → 45 | 4,340 | `RouterPort`, `TimesStore` | M | `CorePlanE2ETest` |
| 9 | `PlanRoute` II: the run (`recalc`, `perform`, long / slow / cancel, routing data arrived, freshness) | A | 590 → 50 | 3,800 | `PlanRouteHost`, `MapDataPort` | **H** (C3, init order) | `CorePlanE2ETest`, `RoutingDataFreshInstallE2ETest` |
| 10 | `PlanEdit` (`change`, points list, reverse / round trip / Undo / clear, the tour hooks behind the host) | A | 402 → 50 | 3,450 | `PlanEditHost` | M (C1, C2, C9) | `CoreDeleteE2ETest`, `CorePointsListE2ETest` |
| 11 | `PlaceMenus` I: add / insert / hold on the map / measuring | A | 330 → 35 | 3,155 | `PlaceHost`, `HoldRouter` | M (C2) | `CorePoiTapE2ETest` |
| 12 | `PlaceMenus` II: POI / point / pin menus, place card, kept places (`KeptPlaces`) | A | 300 → 35 | 2,890 | `PlacesStore` | M (C19) | `KeptPlacesE2ETest` |
| 13 | `Routes` I: list, open tour, shown track, plan from track, delete | B | 315 → 40 | 2,615 | `ToursStore`, `RoutesHost` | M | `RoutesActivityFilterE2ETest` |
| 14 | `Routes` II: go from My routes, followed plan, review, names, plan links / share | B | 242 → 30 | 2,400 | – | M | `DoneTourReviewE2ETest`, `CorePlanLinkE2ETest` |
| 15 | `SyncDriver` moved (`SyncPoll` → `:core`, `PlanSync.State` → own file) + `SyncedRouting` | B | 190 → 40 | 2,250 | `SyncEngine` (exists) | M (C16) | `SyncMailboxE2ETest` |
| 16 | `ActivityCheck` | B | 240 → 25 | 2,035 | `TourService`, `TourRows` | M–H | `CoreFieldE2ETest` |
| 17 | `TourEnd` (end sheet writes, rides, short recording, interrupted tour, the `finished` collector) | B | 315 → 25 | 1,745 | uses `TourRows`, `TourService` | **H** (C4, C12) | `CoreFieldE2ETest`, `UnfinishedTourSetupE2ETest` |
| 18 | `TourSession` I: follow (`setFollow`, `clearFollow`, `followEditedPlan`, `followAhead`, `followAgain`, plan copy) | B | 252 → 30 | 1,525 | `FollowFiles` (= `FollowStore`) | **H** (C5) | `CoreFieldE2ETest` |
| 19 | `TourSession` II: mode, `enter`, start, the start-up check, `startFailed` / `state` collectors | B | 222 → 30 | 1,335 | – | **H** (C4) | `CoreFieldE2ETest`, `CoreIndoorStartE2ETest` |
| 20 | `MultiDaySlice` without the view model (its 19 `vm.` reads behind `MultiDayHost`, `TourDb` behind `ToursStore`) | A | 0 → 0 (moves 371 + 294) | 1,335 | `MultiDayHost` | M | `MultiDayTourE2ETest` |
| 21 | **Forwarders out**: screens and E2E read `vm.<slice>.x` (rename by script, compiler-checked: 386 member reads in 5 screen files + 8 E2E classes); one `init`; size ratchet → the 800-line rule; hosts that only forward to a neighbour slice collapse | – | ≈ 630 → 0 | **≈ 700** | – | M (touches every screen file once) | full E2E run |
| 22–26 | *optional*: `MapDataFlow` behind ports (2 pieces), `SearchSession` + `SearchFlow` (2), `PairingFlow` + `PhoneSetupState` + `AppUpdates` (1) | – | forwarders only | – | `CellsPort`, `LoaderPort`, `SearchDbPort`, `PrefsPort` … | M | `NoStaleDownloadsE2ETest`, `PhoneSetupE2ETest` |

Dependencies: 1 → 2 → everything. 3 needs 1 + 2. 9 needs 8. 10 after 9 (it calls `recalc`; before 9 through the host –
allowed, but then 9 rewires). 11 → 12. 13 → 14. 17 before 18 before 19 (inside-out: the end first, the start last).
20 after 10. 21 last. 4–7 any time.

**Parallel worktrees – judged honestly.** Kotlin has no partial classes; splitting the file first into extension
functions would mean making 135 private members internal, moving 5,000 lines twice and reviewing a diff nobody can
read – **no**. What works: a piece = new files (never conflict) + **one contiguous deletion** in `AppViewModel.kt` +
forwarders written **in place of the deleted block**. Two helpers at a time, one in lane A and one in lane B (lane C
fills gaps); the lead merges serially, the other helper rebases – git merges disjoint hunks by itself. The three
places that always conflict are taken out of the helpers' hands: (1) `MAX_LINES` – only the lead edits it, at the
merge; (2) the import block – helpers add imports, never remove one (unused imports are warnings; the lead prunes
every third merge); (3) the `init` blocks and `onCleared` – a helper adds its `slice.start()` only in the last `init`.
More than two lanes is not worth it: the review and the gates are serial anyway.

### 4.2 Tests per piece – what exists, what the slice gets, what it replaces
"Replaces" = deleted in the same piece once the new test is green (reviewer's question: *which test stays green if
the production code breaks?*). Lines are from `test-audit.tsv`; after-lines are estimates.

| # | Today (verdict, group) | The slice's behaviour test – flows | Replaces (lines) | Stays |
| ---: | --- | --- | ---: | --- |
| 1 | `RoutesFlowPropertyTest` KEEP, `RecentPlansTest` KEEP, `RouteRowActionsTest` KEEP, `NoticesTest` KEEP | the three move as they are (to `:state` if `JdbcTours` can move, §6); `NoticeBoardTest`: an error is a box that stays, a line goes where the user acted and with its page, a second error replaces the first, every `say` is in the problem log | – | all |
| 2 | `PlanDocumentTest`, `TourFlowPropertyTest`, `SuggestSliceTest`, `PlanLinkFlowTest`, 9 rule tests – KEEP | move as they are | – | all |
| 3 | G04: `SaveDialogTest` 206, `SavePathsTest` 149, `PlanAutoSaveTest` 51, `PlanActionsTest` 65, `BackgroundRoutingRuleTest` 36 | **`SaveFlowTest`** on the real `SaveSlice` + `JdbcTours` – the 21 flows of bc58f6a1d (§6 piece 3) | `SaveDialogTest`, `PlanAutoSaveTest`, the save half of `SavePathsTest` and `BackgroundRoutingRuleTest` ≈ −340; new ≈ +360 | the rest of G04 until 10 / 17 |
| 4 | G12: `SettingsSummaryTest` 178, `SettingRowsTest` 75, `SettingsOnTheWayTest` 30, `ProblemFoldTest` 20, `SyncSettingsTest` 266, `SetupTextsTest` 97; `ReliefAutoTest` KEEP | `SettingsPagesTest` (audit's target) on the slice: open / page / back, on-the-way rows, a changed setting reaches prefs, the service and the cells exactly once, developer tools after 7 taps and remembered, relief auto never overrides a user choice | G12 ≈ −333; 2 source pins | `ReliefAutoTest` rules |
| 5 | `WholeRouteTest` 225 KEEP (3 pins), `PlanLayersTest`, `PlanGpsTest`, `MapControlsEnabledTest`, `LongRouteCancelCameraTest` | `MapViewTest`: the first route is fitted once and not after a map gesture; "whole route" picks plan / track / followed line; a hand on the map ends following and free look ends after 25 m; scrub holds 4 s then follows again; no position → the note, no fit | 4 source pins ≈ −40 | the rule tests |
| 6, 7 | `OneTimeModelTest` (1 pin), `RoutesOpeningTest`; C7 `GpxParserTest` …, C11 `BackupTest` … KEEP | 6: none new (bitmaps: E2E). 7: `ImportFlowTest` on the pure decisions already in `:core` only if a pin moves | ≈ −10 | all |
| 8, 9 | `RouteRunTest` 364 KEEP, `SlowPlanTest`, `PlannerTest` …; G05 `FollowLineLegsTest` 44; `ViewModelInitOrderTest` 85; `SyncedRoutingTest` | **`PlanRouteFlowTest`** with `FakeRoutingEngine` + two `ManualExecutor`s (C3): edit → debounce → route; a newer edit supersedes (old result ignored, follow line cancelled); router fails → straight legs + the line says why; very long plan asks first and Cancel puts plan and camera back; time limit asks only in Plan; routing data arrives → one re-route per burst (gate); activity change says "route changed" once; a stored line shows at once and is replaced. `PlanRouteStartTest` (init order) | `ViewModelInitOrderTest` test 1, `FollowLineLegsTest`, 7 source pins ≈ −150; new ≈ +300 | `RouteRunTest`, all C3 rule tests |
| 10 | `PlanEditsTest` KEEP, `CoreDeletePointTest`, `CoreHoldPointTest`, `PlanPointsTest`; G05 `TourEditTest` 45, `ClearPlanDuringTourTest` 41, `RouteBackTest` 141; G04 `PlanActionsTest` 65; DROP `PlanSheetLayoutTest` 81 | **`PlanEditFlowTest`** (C1 / C2 / C9, on `PlanDocument` + a fake tour): add, insert on the line, move, delete, reverse, round trip, Undo / Redo each one step and stored at once; clear keeps the plan under Zuletzt and never asks; during a tour: empty plan ends guidance and never the recording, no point ahead asks "route back", one point left goes from here | `TourEditTest`, `ClearPlanDuringTourTest`, `PlanActionsTest`, `PlanSheetLayoutTest`, 4 pins ≈ −240; new ≈ +220 | `RouteBackTest` rules (fold only with the owner's yes, §4.4) |
| 11, 12 | `MapGesturesTest` 31 tests, `PlaceAddMenuTest`, `PinMenuPlaceTest` (G20), `PoiHitTest`; G09 `FoundPlacePinTest` 119; `FarDestinationTest` KEEP (field bug, 3 pins) | **`PlaceFlowTest`** (C2 / C19): hold on the line adds at once, beside it opens the menu after the paths are measured, a newer hold cancels the measuring; POI tap → card, hold → menu; found place pin goes only by the owner's four ways; add from the card fits the route; home / favourite toggles are stored, backed up and synced | `FoundPlacePinTest`, 8 pins ≈ −150; new ≈ +250 | `FarDestinationTest` (its 3 pins become asserts in `PlaceFlowTest`, the file stays) |
| 13, 14 | G13 (8 files, 460), `RoutesGoTest`, `StoredLineTest`, `CorePlanLinkTest` KEEP | `RoutesListFlowTest` (audit's target, beside `RoutesFlowPropertyTest`) + open flows: a planned route becomes the plan (unsaved one kept under Zuletzt first), a done tour is shown, during a tour a tapped plan is followed with "Neuer Plan" + Undo; delete → bin → restore | G13 ≈ −184 | property test |
| 15 | `SyncDriverTest` 263, `SyncPollTest`, `CorePlanSyncTest` KEEP | driver test moves; `SyncedRoutingTest` grows by one flow (a plan from the browser is routed when its data arrives, never twice) | 1 pin | all |
| 16 | `ActivityCheckTest` 22, `ActivityCheckRulesTest` KEEP | `ActivityCheckFlowTest` with `FakeRecordingClient`: open → rows computed off the main thread → choose → the tour's row, the plan copy and the service are told in that order; hike level; cancel leaves everything | 1 pin | all |
| 17 | G05: `ArrivalBoxTest` 86, `TourEndPathsTest` 60, `TourRunningTest` 41, `PlanGuidanceTest` 150; `ShortRecordingBoxTest` 54, `TourFlowPropertyTest` KEEP; G08 `InterruptedLogTest` (stays until phase 4) | **`TourEndFlowTest`** (audit's target; C4 / C12) on `JdbcTours` + `FakeRecordingClient`: the three ends run the ride check and save; Delete → bin, short recording → bin + Undo restores a normal tour; interrupted tour: Continue / Save / Delete, each clears the notification, Delete marks it done first; a failed write says so and keeps the tour | G05 tour half ≈ −250, 6 pins; new ≈ +300 | `TourFlowPropertyTest`, `ShortRecordingTest` |
| 18, 19 | `PlanStartTest`, `AppModeTest`, `StartAtPointOneTest`, `TourSplitTest`, `RerouteTargetsTest`, `TourPlanCopyTest` KEEP; G08 wiring tests (stay) | **`TourSessionFlowTest`**: §4.3 | 4 pins | all rule tests; G08 untouched |
| 20 | `MultiDayFlowTest` 180 KEEP | `MultiDaySliceTest`: a day becomes the plan, an edit of the day is written back into the whole tour, Save while a day is the plan writes the whole tour, a lost day state reloads the whole tour (never a day over the tour) | – | all |
| 21 | `AppViewModelSizeTest` 31 DROP, `ViewModelInitOrderTest` | the size rule of the plan (one test) | −116 | – |

Row T for `ui` (15,424 lines today): the pieces above delete ≈ 1,800 test lines (G04, G05 tour half, G09 part, G12,
G13, the source pins) and add ≈ 1,700 lines of flow tests – **phase 2 by itself is about line-neutral in `ui`**. The
audit's −3,800 for `ui` comes from the 21 DROP files (978 lines – deleted in piece 2, they need no slice), G19
screenshots (phase 4b), G10 / G11 and the owner-decision pins (§4.4). What phase 2 buys for row T is the other half of
its sentence: C18, the tour's end (C4 / C12), the route run (C3), the edits (C1 / C2 / C9) and the menus (C19) get a
**behaviour test that runs the real code**, and the source-text tests on the view model go 56 → 0.

### 4.3 The risky pieces: the tour (17–19)
**The 30 lines that talk to the service** (b3a343bd7): commands – `recording.settingsChanged` 1740, `say` 1779 (1768),
`navigating` 3967, `checkNow` 4111, the seven answers 4115–4133, `proposeSwitch` 4330, `followChanged` 4610 / 4736 /
4755, and through `TourController`: `start` (`startRecording` 4758), `continueTour`, `pause`, `stop`; reads –
`recording.state` 1262, 1447, 1454–1458, 1480, 3998, `startFailed` 1472–1473, `finished` 1499–1501,
`takeFinishedParts` 1522; plus `TourInterruptedReceiver.cancelNote` 4767 and `RecordingService.SAY_NO_DESTINATION` 1768.

**The port – nothing new is invented:** `RecordingClient` already is the typed contract; only its file is Android
(`RecordingCommand.intent(context)`, `ServiceRecordingClient`). Piece 16 splits it: the interface + `RecordingCommand`
(action names as constants, no `Intent`) + `RecState` (a pure data class, today inside `RecordingService.kt`) go to
`:core` `recording/`; `intent()` becomes an extension in `:app`. The slices take the **same `RecordingClient` object
the view model has** – so every command is the same call on the same thread at the same point of the same function,
and the flows are collected on the same scope. No queue, no extra coroutine, no new dispatcher between slice and
service. `FakeRecordingClient` (exists) records the commands in order for the tests.

**Order rules the flow test pins (they are the timing):**
1. `setFollow` / `followAgain` / `clearFollow`: the follow files are written (`FollowStore.appFollows` / `appEnds`)
   **before** `followChanged()` is sent – the service reads the file on that command.
2. `startNavigation`: follow → corridor → save at start → `start` → Navigate **only after the first `RecState`**
   (R-01, 6 s, else the error box and the mode stays Plan).
3. Start-up: `unfinishedPending` is set before the database check and cleared in `finally` (a stuck flag would refuse
   every tour); a tour marked active with no service shows its question after 1.5 s only if the service did not come back.
4. `finished`: `finishedSeen()` first, then the outcome (deleted / short / saved), then `refreshTours`, `clearFollow`,
   `enter(PLAN)`, `afterFinished` – in this order.
5. A plan edit during a tour: `followEditedPlan` runs only for the newest route generation; an empty plan ends the
   guidance and never the recording.

**De-risking:** (a) inside-out order 16 → 17 → 18 → 19, each on main for one nightly E2E before the next; (b) each of
the three is a *move with the functions byte-identical* – the reviewer gets the instruction to diff function bodies
(`git diff --color-moved=dimmed-zebra`, hunks in non-identical moves listed by the helper); (c) `TourSessionFlowTest`
is written **first, against the view model's order as read**, and reviewed on its own before the move; (d)
`CoreFieldE2ETest` (35 tests, drives a tour on the emulator) on a `ci/` branch for each, `CoreIndoorStartE2ETest` for
19; (e) these three never run in parallel with another piece; (f) after 19 the owner's weekly Pixel tour is the
release gate as always – say so in the handoff. G08's wiring tests are untouched (phase 4).

### 4.4 Owner-decision pins – both ways
22 KEEP files in `ui` (1,869 lines) pin an owner decision without a field bug or a C-id (`SmartScreenTest` 265,
`WholeRouteTest` 225, `RouteRowActionsTest` 139, `NextStopTest` 135, `LivePlanRowsTest` 114, `NoFlashTest` 99,
`PocketLogicTest` 97, `PlanGpsTest` 84 …).
- **If they may be folded:** each piece folds the pins of its slice into its flow test as table rows (5: `WholeRoute`,
  `PlanGps`, `MapControlsEnabled`, `LongRouteCancelCamera`, `PlanLayers`; 13: `RouteRowActions`, `LivePlanRows`; 17:
  `ShortRecordingBox`, `PocketLogic`, `NextStop`) – estimate −650 lines (35 %, the audit's L3 ratio), +10 min per piece.
- **If they stay (default until he answers):** they move with their class, only source pins inside them are replaced.
  No piece waits for the answer.

## 5. Numbers

| | |
| --- | --- |
| Pieces | 21 required (2 pure moves, 16 cuts, 2 `:app` helpers, 1 rename) + 5 optional |
| One piece | helper 30–60 min · review 10 min · fix round (expect one in two pieces) 20 min · gates 10 min ≈ **75–100 min** |
| Per night (8 h, two lanes, serial merges) | 5–6 pieces; the H pieces alone: 3 per night |
| Nights | night 1: 1–5 · night 2: 6–10 · night 3: 11–15 + 20 · night 4: 16–19 · night 5: 21, full E2E, taken-back pieces again, tag → **5 nights (4–6)**; optional 22–26: +2 |
| Feature pause in `AppViewModel` / ui state | from piece 3 to piece 21; lane C pieces barely block others. After night 2 a new feature can already land *as a slice* |
| Shorter if needed | drop 22–26 (they are phase 4's ports anyway) · drop 21 and accept ≈ 1,300 lines until the next quiet week · 20 can wait (multi-day works as it is) → **15 cut pieces, 4 nights** |
| Never dropped | 3 (C18 has no other behaviour test), 9 (init order), 17 (G05 needs it) |

**Done per piece:** `tools/ci/gate.sh --full` green on top of main · androidTest compiles
(`tools/dev/gradle_slot.sh -- ./gradlew :app:compileDebugAndroidTestKotlin`) · `-p web/engine test generateWasmGC` +
`node web/parity/check.mjs` (a moved file changes a parity key: `core_move.py` rewrites it; a changed mirror hash is
a line for the laptop, not a silent re-hash) · the E2E class of the table on a `ci/umbau-p2-<n>` branch
(`tools/ci/e2e_github.sh --ref … --tests <class> --wait`) for M and H pieces · fresh Opus review with the two questions
(tests that stay green if the code breaks; hunks in non-identical moves) · `MAX_LINES` lowered by the lead ·
nothing the user sees changed: no screenshot re-recorded (a changed picture = the piece is wrong).
**Done for the phase:** unit CI + planner run + full E2E green, tag `umbau-phase-2`, this file deleted.

## 6. The first three pieces as helper briefs

### Piece 1 – `:state` exists; My routes' state lives there; `NoticeBoard` is the first cut
*Goal:* prove the whole pattern on the lowest-risk code: module, border, moved slice with its tests, one real cut
with a port, forwarders, ratchet, a web-ready file.
1. **Module** `state/`: `build.gradle.kts` after `core/build.gradle.kts` (kotlin jvm, Java 17, animal sniffer,
   `sourceSets.test { kotlin.srcDir("../core/src/testkit/kotlin") }`, test system properties); dependencies
   `api(project(":core"))`, `api("org.jetbrains.compose.runtime:runtime:1.7.3")`; `include(":state")`; `:app` gets
   `implementation(project(":state"))` and `:app:testDebugUnitTest` depends on `:state:test` unless
   `-Ponpath.coreTests=explicit` (copy `:core`'s lines); `tools/ci/gate.sh` + `tools/ci/risk_tests.py` + `release_check.sh`
   know `:state:test`; `.claude/rules/ui.md` globs get `state/src/**`. **Not** `.github/workflows/` (a patch file into
   `tools/ci/pending/`, as phase 1 did).
2. **Move** (`git mv`, package unchanged): `ui/routes/RoutesState.kt`, `ui/routes/RecentPlans.kt`,
   `ui/routes/RouteRowActions.kt` (its `ReviewText` dependency: move that pure object out of `DoneTourOverview.kt`
   into its own file in `:state`; if it is not pure, `RouteRowActions` stays – say so) → `state/src/main/kotlin/com/onpath/app/ui/routes/`.
   Tests `RecentPlansTest`, `RoutesFlowPropertyTest`, `RouteRowActionsTest` → `state/src/test/kotlin/…` **if**
   `testkit/JdbcTours` can go to `core/src/testkit` (it needs `TourSchema` and three `TourDb` statics that only forward
   to `TourJson` – use `TourJson` / move `TourSchema` if it is Android-free); else the tests stay in `:app` – say which.
   Path-keyed places: `web/parity.json` key of `RoutesState.kt`, `lint-baseline.xml` (`tools/dev/core_move.py` does
   these for `core/`; give it a `--module state` or do them by hand and list them).
3. **Types out of `ui/Notices.kt`** into `state/…/ui/NoticeTypes.kt`: `NoticeKind`, `NoticePlace`, `Notice`, `Busy`,
   `NoticeRouting` (pure; the Composables stay).
4. **Cut** `NoticeBoard` (`state/…/ui/NoticeBoard.kt`): state `errorCard`, `routesNote`, `settingsNote`, `mapNote`,
   `mapNoteTick`, `busy`; `say`, `sayError`, `sayInfo` (bodies unchanged, lines 204–222, 813–817, 1207–1229);
   it implements the new `fun interface Say { fun say(text: String, kind: NoticeKind, place: NoticePlace) }`
   (`state/…/ui/Ports.kt`). What `say` reads of the screens (which page is open) comes as constructor lambdas. In the
   view model: `val notices = NoticeBoard(…)` where `errorCard` was, and one-line forwarders **with the same names**
   (`var errorCard: String? get() = notices.errorCard; set(v) { notices.errorCard = v }` …) – no screen file changes.
5. **Tests:** `NoticeBoardTest` (`:state`, 4 flows of §4.2 row 1). `NoticesTest` stays.
6. **Web-ready:** `state/web-ready.txt` with `ui/routes/RoutesState.kt`; `WebReadyRuleTest` in `:state` (the listed
   files import nothing but `com.onpath.app.*` of `:core` / `:state`, `com.onpath.strings.*`, the five Compose
   runtime names, `java.*` / `kotlin.*`; no `kotlinx.coroutines`). Nothing under `web/` is edited.
7. `SafeLaunchRuleTest` also scans `state/` for bare `scope.launch(` / `scope.launch {`.
*Do not touch:* `MAX_LINES` (report the new line count), any screen file, `web/`, workflows, `docs/team/backlog.md`,
other slices. *Checks:* the "done per piece" list of §5; `StartSmokeE2ETest`. *Report:* files moved, the path-keyed
places changed, where the three tests live and why, hunks that are not pure moves.

### Piece 2 – the hub types and the slices that are already pure
*Goal:* everything that later pieces need to name lives in `:state`; no logic changes.
1. Move as files: `ui/plan/PlanDocument.kt` (**split first**: `PrefsPlanStore` into `app/…/ui/plan/PrefsPlanStore.kt`),
   `ui/tour/TourController.kt` (the adapter `tourRecorder(client)` stays in `:app`; `EndAsk` moves with it out of
   `AppViewModel.kt`), `ui/suggest/SuggestSlice.kt`, `ui/sync/PlanLinkFlow.kt`, and the pure rule files
   `WholeRoute`, `LoadNames`, `PlanGps`, `RoutingLoads`, `MapControlsEnabled`, `LongRouteCancelCamera`,
   `ExpandGesture`, `SettingsSummary` – each with the small types it needs out of Composable files, moved into
   `:state` under the same package: `PlanSize` (from `PlanPanel.kt`), `SettingsPage`, `SyncNote`, `ShownTrack`
   (from `AppViewModel.kt`), constants such as `TOP_BAR_BOTTOM`, `MAP_FAB`, `OverviewPillPlace`, `MessagePlacement`
   **only if pure** (dp as numbers); a file whose types pull Compose UI stays in `:app` – list it, do not bend it.
2. Their tests move with them (`PlanDocumentTest`, `TourFlowPropertyTest`, `UnfinishedStartGuardTest`,
   `SuggestSliceTest`, `PlanLinkFlowTest`, the rule tests of the moved files); a test with a source pin on
   `AppViewModel.kt` keeps that one test in `:app` for now.
3. New ports in `Ports.kt`, defined only (used from piece 3 on): `Camera { fit(points); centerOn(point, zoom);
   panTo(point) }`, `Clock { fun nowMs(): Long; fun elapsedMs(): Long }`.
4. Delete the 21 DROP test files of `ui` (`test-audit.md` §5.1 – all rows with package `ui` except
   `AppViewModelSizeTest`; `PlanSheetLayoutTest`'s five `PlanFoot.of` asserts and `StoppedTextTest`'s clock assert are
   folded first as the audit says). One commit of its own.
*Do not touch:* function bodies, `AppViewModel` beyond the two type moves and imports, `MAX_LINES`, `web/`.
*Checks:* §5; screenshots must verify unchanged. *Report:* every file that could not move and the one type that held it.

### Piece 3 – `SaveSlice` (C18: every Save asks for the name)
*Read first:* `docs/core-features/C18-save-name.md`, `domain/routes/SaveFlow.kt`, `RouteRules`, `sync/SaveRule`,
`PlanAutoSave`, and `git show bc58f6a1d:app/src/test/java/com/onpath/app/ui/SaveFlowTest.kt` – its 21 tests are the
flows wanted; its inner `Session` class (a copy of the view model's steps) is what must **not** exist any more.
1. **Slice** `state/…/ui/routes/SaveSlice.kt` – moved with bodies unchanged from `AppViewModel` (lines 1100–1116,
   3341–3460, 3511–3530, 3531–3672, 4004–4058): `request(then)` (was `askSave` / `requestSave`), `requestCopy(t)`,
   `cancel()`, `confirm(input)`, `saveRoute(name, then)`, `copyRoute`, `insertPlanned`, `overwriteSaved`,
   `rename(t, input)`, `setFavourite(t, on)`, `keepAsDraft(why)`, `atTourStart()` (`saveNewPlanAtStart` +
   `updateSavedAtStart`), `currentSaveMode`, `plannedNames`, `linkedDraft`; state `ask` (was `saveAsk`),
   `problem` (was `routesProblem`), `saving`. Constructor `(plan: PlanDocument, routes: RoutesState, recent: RecentPlans,
   store: SaveStore, host: SaveHost, scope, io, nameWaitMs = RecentPlans.NAME_WAIT_MS)`.
   The dialog half (`request`, `cancel`, `confirm`'s decision, `requestCopy`) goes into a coroutine-free class
   `SaveDialog` in its own file (web-ready; add it to `web-ready.txt`); the name proposal that arrives later is the
   driver's job (`SaveSlice` sets it through `SaveDialog.proposed(ask, name)`).
2. **`SaveStore`** (blocking; called only in `withContext(io)`): `getTour(id)`, `loadSegments(id)`,
   `insertPlanned(row: PlannedRow): Long`, `replacePlanned(id, row: PlannedRow): Boolean`, `rename(id, name)`,
   `setFavourite(id, on): Boolean`. `PlannedRow` = name, activity, sac, distance, ascent, descent, duration, segments,
   stops, pins, difficulty, days. In `:app`: an object over `TourDb` next to `routesStore`.
3. **`SaveHost`** (main thread): `route`, `activity`, `sac`; `stops(points)`, `pins()`, `difficulty(route, activity)`,
   `days(points)`, `keepWhole()`, `overwriteWhole(name, then)` (multi-day); `suspend proposeName(points, route)`;
   `datedName()`; `storeTimes(id, route, activity)`, `forgetTimes(id)`, `copyTimes(from, to, distanceM)`;
   `tourChanged(id)` (backup); `syncChanged(why)`; `refreshTours()`; `saved(name, id, points)` (the view model's
   `setSaved`: the plan's link **and** the running tour's plan copy – that second half stays in the view model);
   `shownRenamed(id, name)`; plus `Say`. The view model implements it as one object; `setSaved` stays there.
4. **View model:** `val save = SaveSlice(…)`; forwarders with today's names (`saveAsk`, `routesProblem`,
   `requestSave`, `requestCopyRoute`, `cancelSave`, `confirmSave`, `saveRoute`, `renameTour`, `setFavourite`);
   `enter()` calls `save.cancel()`; `startNavigation` calls `save.atTourStart()`; `clearPlan` / `followShownDuringTour`
   / `MultiDaySlice` call `save.keepAsDraft(why)`. Order of every write, every `Problems` text and every
   `Backups` / sync call as today.
5. **`SaveFlowTest`** (in `:state` if `JdbcTours` moved in piece 1, else `:app`): drives **the real `SaveSlice`** with
   `PlanDocument` on a map-backed `PlanStore`, the real `RoutesState` / `RecentPlans`, `SaveStore` over `JdbcTours`
   (with `failWrites`), a fake `SaveHost` that records backup / sync / refresh calls, `Dispatchers.Unconfined` (and two
   `ManualExecutor`s for the double-tap test). Every test asserts what is **stored** (re-read) and the state the user
   is left in. Flows: new plan saved under the proposal · no proposal → date name, typed name wins · empty name keeps
   the proposal · a taken name gets a number and never touches the other route · Cancel saves nothing and "Save first"
   loads nothing · a second tap while the write runs makes no second route · a failed write stores nothing, says so,
   plan stays unsaved · save again after an edit overwrites the same route · changed name renames in the same write ·
   empty name keeps the old · overwrite never hits another route · sync changed the stored route → a copy, the other
   edit stays (review sync H3) · route deleted meanwhile → new route with that name · a route from My routes is
   overwritten in place, copy proposes "(2)" · a plan from Zuletzt is saved as a new route and takes its place ·
   "Los geht's": never-saved plan saved without asking and not twice / saved plan changed → updated in place, the
   version before under Zuletzt / unchanged or no plan → nothing / from Zuletzt → under its own name · a replaced
   unsaved plan goes under Zuletzt, a saved one as it is does not · plan and saved link survive a new `PlanDocument`
   on the same store · Save's dialog closes on a mode change · rename and star: stored, backup + sync told, a failure
   puts the row back and says so.
6. **Replaces** (delete only when `SaveFlowTest` is green): `SaveDialogTest`, `PlanAutoSaveTest`,
   `BackgroundRoutingRuleTest.overwriteChecksTheStoredRouteFirst`, `SavePathsTest.thePlanSurvivesTheProcess`. The
   other tests of `SavePathsTest`, `PlanActionsTest` and `BackgroundRoutingRuleTest.backgroundWorkHasItsOwnThread` pin
   code that has not moved – they stay.
*Do not touch:* `MultiDaySlice`'s own overwrite, `RecentPlans`, the tour code, `MAX_LINES`, `web/`, screens.
*Checks:* §5 + `CorePlanE2ETest` on a `ci/` branch. *Report:* each function with "moved unchanged" or the hunk; the
tests of bc58f6a1d that could not be written against the slice and why.

## 7. Open questions

**For the owner** (each with a recommendation; no piece waits):
1. *Owner-decision tests:* may the small tests that only pin a decision of yours be folded into the flow tests as
   table rows (22 files in `ui`, ≈ −650 lines)? **Recommendation: yes**, with the rule that each decision keeps one
   named row. Until then they stay as they are.
2. *The tour pieces (16–19) change no behaviour but move the code that starts, follows and ends a tour.* Release the
   result only after your weekly Pixel tour, as the night-job rule says? **Recommendation: yes – no release between
   piece 16 and that tour.**
3. *Feature pause:* five nights instead of "about a week"; from night 3 on new work can land as a slice.
   **Recommendation: start after the 0.18 test round, lanes A + B together.**

**For the lead / captain** (not owner questions): the plan's done-rule "mirrors 52 → under 15" → "state-logic
mirrors 8 → 0, `parity.json` split into logic / look" (§2.1); piece 21 (screens read `vm.<slice>.x`) is the only
piece that edits many screen files – confirm it, or accept ≈ 1,300 lines.

**For the laptop** (web side):
1. Can TeaVM 0.16 run `kotlinx.coroutines` in the worker? If no (assumed): web-ready = coroutine-free files only (§2.2).
2. A stand-in for the five `androidx.compose.runtime` names (plain holder) – OK, or do you want web-ready files free
   of Compose imports too (then they keep state in plain fields and the driver mirrors it – more code on our side)?
3. Will the planner hold slices in the worker (state there, the page renders what it reads back), starting with
   `RoutesState` for `recent.ts` and `SaveDialog` for `plans.ts`? The slices are built so that it can; nothing forces it.
4. `web/parity.json`: a `kind` per mirror (logic / look / map / stand-in) so the number says something; `RoundTrip`
   (#43) can move to `:core` in piece 13 – then it is a shared file, not a mirror.
5. How does piece D keep the 9 coroutine files of `:core` out of the folder compile – the same list can serve `:state`.

## 8. What I could not verify
- Whether the Compose runtime jar or `kotlinx.coroutines` compile under TeaVM (assumed no for both).
- Whether `JdbcTours` / `TourSchema` can move to the shared test kit (decides where three tests of piece 1 and
  `SaveFlowTest` live); whether `ReviewText` and the small layout constants of piece 2 are pure.
- Line counts "back" (wiring + forwarders) and the test lines of §4.2 are estimates; lines "out" are measured.
- I read in full: the class head, the `init` blocks, the save flow, mode / start, follow writes, `change`, the head of
  `recalc`, `perform`, `onCleared`, all slice constructors. The rest (≈ 3,400 lines) I know from the member index
  (signature, size, what it touches) – a piece's helper reads its own block whole before moving it.
- The 110 public members never read as `vm.x` were found by a name search (`vm.` / `viewModel.` / `host.`); some are
  read through a different variable name. Piece 21 lets the compiler decide.
