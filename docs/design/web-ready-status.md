# What of `:state` the planner can take (status 2026-11-19, for [laptop])

`state/web-ready.txt` is the list; `WebReadyRuleTest` checks it on every gate. Numbers below come from the commands at the end.

## 1. The files of `:state` by class (62 files, 8955 lines)

| Class | Files | Lines | Which |
|---|---|---|---|
| **Listed now** (you may compile them as they are) | 29 | 1730 | see §3 |
| **One step away** | 1 | 146 | `ui/plan/PlanDocument.kt`: its only blocker is the type name `SnapshotStateList` (line 7 + line 36, `points`). Step: your Compose stand-in gets `class SnapshotStateList<T> : MutableList<T>` (+ `mutableStateListOf` returning it, which it has to anyway), and the rule test allows `androidx.compose.runtime.snapshots.SnapshotStateList`. It is the key file: 7 of the 13 slices below take a `PlanDocument`. |
| **Needs a cut** (coroutines inside) | 21 | 6629 | the 13 named: `PlanEditSlice` (host `PlanEditHost` 30 members, + `PointNames` 1), `PlaceAddSlice` (`PlaceAddHost` 21, `HoldRouter` 1), `PlaceMenusSlice` (`PlaceMenusHost` 10, `PlaceLookup` 1), `PlanRecalcSlice` (`PlanRecalcHost` 10, `MapDataPort` 6, `RouteSearchProgress` 1), `PlanRouteSlice` (`PlanRouteHost` 11, `RouterPort` 5, `TimesStore` 4, `PlanRoutePrefs` 2), `RecentPlans` (`RecentPlansHost` 9), `SaveSlice` (`SaveHost` 18), `RoutesSlice` (`RoutesHost` 34), `RoutesGoSlice` (no host interface of its own), `MultiDaySlice` (`MultiDayHost` 13), `SuggestSlice` (`SuggestHost` 6, two `suspend` members), `PlanLinkFlow` (`PlanLinkHost` 11), `MapViewSlice` (`MapViewHost` 17); and 8 more: `SettingsSlice`, `sync/SyncDriver`, `sync/SyncedRoutingSlice`, `tour/ActivityCheckSlice`, `tour/TourEdits`, `tour/TourEndSlice`, `tour/TourFollow`, `tour/TourSession` (the last five are the tour recording: not a planner feature). Other slices they need (from their constructors): `PlanDocument` (PlanEdit, PlaceAdd, PlaceMenus, MultiDay, Routes, RoutesGo, Save), `PlanRunSlice` (PlanEdit, PlaceAdd, PlaceMenus – listed), `PlanEditSlice` (PlaceAdd), `PlaceAddSlice` (PlaceMenus), `RoutesState` (listed: RecentPlans, Routes, RoutesGo, Save), `RecentPlans` (Save), `SaveSlice` (Routes, RoutesGo), `PlanRouteSlice` (MultiDay, Routes, RoutesGo), `ToursStore` (MultiDay, Routes, RoutesGo – a port, unlisted for now), `TourController` (Routes, RoutesGo – tour recording). Host member counts: command in §5. |
| **Not a planner feature** (tour recording, developer tools, phone-only) | 11 | 450 | `ui/ActivityCheckUi`, `ui/EndAsk`, `ui/DemoRules`, `ui/DevToolsUnlock`, `debug/FrameStats`, `data/RoutingDataCheck`, `ui/tour/TourController`, `ui/tour/TourRecorderAdapter`, `ui/tour/TourRows` (they pass the rule test; left unlisted on purpose), and the two ports of unlisted slices `ui/routes/SaveStore`, `ui/routes/ToursStore` (list them together with `SaveSlice` / `RoutesSlice`). |

(21 + 1 + 29 + 11 = 62.)

## 2. What the rule test checks, and what it cannot see

Checks on every listed file (`state/src/test/kotlin/com/onpath/app/ui/WebReadyRuleTest.kt`):
1. the file exists; 2. imports only `com.onpath.app.*`, `com.onpath.strings.*`, `java.*`, `kotlin.*`, `org.json.*`,
`org.jetbrains.compose.resources.StringResource` / `PluralStringResource`, and the five Compose names (`mutableStateOf`, `getValue`,
`setValue`, `mutableStateListOf`, `mutableIntStateOf`); no `kotlinx.coroutines`, no other `androidx.*`;
3. no `suspend`, `Thread`, `java.util.concurrent` (but `.atomic.` is fine: core files the engine compiles use it), `synchronized(` /
`@Synchronized` / `@Volatile`, `java.io.File`, `System.currentTimeMillis()` / `System.nanoTime()` called directly (a default argument
`System::currentTimeMillis` of a clock lambda, as `RoutesState` has, is allowed: it is injectable);
4. closure: every `:state` type / function a listed file names (same package or imported) is declared in a listed file;
5. every `:core` file it names is compiled by the planner: not in `web/engine/core-excludes.txt` (empty today), not replaced by a
stand-in in `web/engine/src/main/kotlin/com/onpath/app/` – except `debug/Problems.kt`, where each `Problems.<member>` used must be
declared by the stand-in.

It cannot see: names inside string templates (`"${x.y}"`), a wildcard import of `:state` / `:core` (that fails: import by name),
two top-level declarations of one name in one package (first wins), the type behind an inferred value (`x.foo()` counts as a member
call; only extension functions match after a dot), a member of a stand-in other than `Problems` (whether your `StringResource`
has what a file calls), and whether TeaVM really compiles a file (nothing here builds the engine). The test is also not a proof that
the Compose stand-in is enough: it only allows the five names.

Also done: `:state:test` now has `web-ready.txt`, `core-excludes.txt` and the engine's stand-in folder as inputs (Gradle used to skip the test
when only the list changed).

## 3. Listed now (29 files, 1730 lines) and the ports you implement

Slices:
- `ui/routes/RoutesState.kt` (260): port `RoutesStore` (7 members: listTours, listDeleted, setDraft, setDeleted, loadSegments, deleteForGood, rename); a clock lambda `() -> Long`; two hooks it calls (`onClosed`, `onRecent`).
- `ui/routes/SaveDialog.kt` (50): none.
- `ui/plan/KeptPlacesSlice.kt` (85): `PlacesStore` (4: saved, update, backupChanged, syncChanged), `KeptPlacesHost` (2: searchMark, isCandidate).
- `ui/NoticeBoard.kt` (51): is the `Say` port itself; three lambdas (routesOpen, settingsOpen, mapShown).
- `ui/MultiDayFlow.kt` (294): `MultiDayPrefs` (5 properties), lambda `linkedId`; uses `org.json`.
- `ui/plan/PlanRunSlice.kt` (121): `PlanRunHost` (6: points, clearRoute, noCorridor, loadDeferredMaps, recalc, cancelRun) and `Cancellable` (1: cancel) for its `routeJob` handle. It no longer names `kotlinx.coroutines.Job`: the app sets the handle with `JobHandle(job)` (in `PlanRecalcSlice.kt`), you set your own. The run itself (`recalc`) is in `PlanRecalcSlice` (coroutines): you write that part as the page's own code and call `routeEvent` / `perform`.

State types: `NoticeTypes`, `ShownTrack`, `TrackThen`, `SyncNote`, `TimeDetailsUi`, `search/PlaceCardUi`, `MultiDayUi`, `SettingsPage`.
Ports (`ui/Ports.kt`: `Say` 1, `Camera` 3, `Position` 1, `Clock` 2, `Cancellable` 1; `ui/MultiDayPorts.kt`: `MultiDayPrefs` 5 properties, `EndNamer` 1).
Rules: `WholeRoute`, `LongRouteCancelCamera`, `ImportRules`, `LoadNames`, `PlanGps`, `PlanSize`, `ExpandGesture`, `MapControlsEnabled`, `MessagePlacement`, `MapDimens`, `MapSizes`, `OverviewPillPlace`, `SheetRise` (the last six are screen-layout rules of the Compose app: listed because they pass and `WholeRoute` needs `MapDimens`; you may leave them out).

## 4. What you / we do next

- You (`web/`): the Compose stand-in; if it gets `SnapshotStateList` (§1), tell the captain / state owner and `PlanDocument` is listed in one commit. Until then the 7 slices that take a `PlanDocument` stay out even after their cut.
- Cutting the coroutine slices (a later piece, `state/`): each host above is the list of calls you implement; `suspend` members (SuggestHost) and `withContext` blocks are what the cut has to turn into ports with a callback or plain state + a reducer.

## 5. The numbers, by command (run in `state/`)

```
R=src/main/kotlin/com/onpath/app
find $R -name '*.kt' | wc -l                                  # 62 files
find $R -name '*.kt' -print0 | xargs -0 cat | wc -l           # 8955 lines
grep -v '^#' web-ready.txt | grep -v '^$' | wc -l             # 29 listed
grep -rlE 'kotlinx.coroutines|\bsuspend\b' $R | wc -l         # 21 with coroutines
# members of a host: count the `fun` / `val` / `var` lines inside its interface, e.g.
awk '/^interface PlanEditHost/{c=1;next} c&&/^}/{exit} c&&/^    (fun|val|var|suspend fun) /{n++} END{print n}' $R/ui/plan/PlanEditSlice.kt
```

## 6. The cut for slices with a coroutine: `XState` + `XSlice : XState` (2026-11-20, first one built: place menus)

The 13 planner-feature slices are not listed because of a few lines of background work each, not because of their
state. The cut, the same for each – **no call site changes, in the app or in a flow test**:

- `XState.kt` (listed, `open class`): everything but the coroutines – the state, the decisions, and for each piece of
  background work two or three plain members: **what is to do** (`…Todo()`), **the blocking work on its input**
  (`find…(todo, port)`), **what a finished run changes** (`…Found(todo, result)`). Its host interface `XPorts` has no
  `suspend` member.
- `XSlice.kt` (app only, not listed): `class XSlice(…, io, scope) : XState(…)` with the `Job`, `launchSafe`,
  `withContext` – it calls the three members in the old order. Its host `XHost : XPorts` adds the suspending members.
  Name, constructor and members are the old ones, so `AppViewModel`, the screens and the flow tests stay as they are.
- The planner constructs `XState` and drives the work itself (its own async on the page): `todo` → its lookup → `found`.

Built: `ui/plan/PlaceMenusState.kt` (was `PlaceMenusSlice.kt`; the mirror entry in `parity.json` follows the file) +
`PlaceMenusSlice.kt` (46 lines: the pinned POI kinds' lookup). **Not listed yet** – the closure, measured by a trial
listing with `WebReadyRuleTest`:

| File | What still stands in the way | Cut |
| --- | --- | --- |
| `PlaceMenusState.kt` | takes a `PlaceAddSlice` | lists as soon as `PlaceAddState` exists (type in its constructor changes) |
| `PlaceAddSlice.kt` (480 lines) | builds `HoldPaths(scope, holdWorker)` itself (2 coroutine imports); takes a `PlanEditSlice` | `PlaceAddState(…, holdPaths: HoldPaths)`; `PlaceAddSlice` builds the `HoldPaths` and passes it up |
| `PlanEditSlice.kt` | `refreshPointNames` (one coroutine, `suspend fun pointNames()` in the host) – nothing else, its own closure is clean | as place menus: `pointNamesTodo()` / `findPointNames` / `pointNamesFound` |

So the three list together after two more small cuts (order: `PlanEditState`, `PlaceAddState`, then the three lines in
`web-ready.txt`). `HoldPaths` is `:core` (the planner compiles it; it uses coroutines – [laptop]: say if TeaVM cannot
take it, then `PlaceAddState` asks a small `HoldMeasure` port instead).
`PlanRunSlice` (listed) needs no cut: its host's `recalc()` is a plain call – the planner's host implements it with
its own run (`PlanRunHost`, 6 members); what is missing is on the page side, not in `state/`.
