# C3 – Routing


Part of the core features (`docs/core-features.md`): every core feature has JVM regression tests; a field bug first gets a failing test, then the fix.


| # | Core feature | Field bugs guarded against | Regression tests |
| --- | --- | --- | --- |
| C3 | Routing: route for every activity, offline, fallback to straight lines; a point on a way without a connection (BRouter "island") is snapped to the next way; **a point on a way is always visited** (owner): when BRouter's via correction cuts the there-and-back to a stop matched on a way (≤ 30 m) and the line misses that spot by more than max(15 m, 3 × the tap's distance to its way) – `WaypointSnap.missesMatched` – every plan point is a request end: since PLANNER_VERSION 4 (2026-11-05) the Planner routes leg by leg, a there-and-back at a join is trimmed only for a tap beside the way, never for a stop on it (`docs/design/bike-routing.md` decision 14; before: decision 13, the splice); a stop still skipped is never moved onto the line silently: it keeps its place, grey dashed gap + red line "Route misses point 3 by 50 m" (`PlanGaps`, from 10 m); matched points come from `OnPathHints.matched` (no reflection – the web planner too) | Köln Dom → Neumarkt: no route for any activity; TU Dortmund 2026-10-30 (Bike): stop 3 on a path below steps, line 47 m from it, the phone moved the stop 47 m without a word | `PlannerTest` (`stopOnAPathMatched4mAndCut47mFromItsSpotIsVisited`, `aStopStillCutAfterTheLastPassIsReportedSkipped`), `WaypointSnapTest.missesMatchedIsRelativeToTheTap`, `PlanGapsTest.skippedStopKeepsItsPlaceAndShowsItsGap`, `OnPathHintsTest`, `ReferenceRoutesTest`, `RouteRulesTest`, `IslandPointTest`, `PlannerLegsTest`, `LegCacheTest`; JVM `tools/routing/jvm-test/IslandTest.kt`, `SpurStopJvmTest.kt`, `SpurSpliceJvmTest.kt`, `LegsVsWholeTest.kt` |

Route warnings and hints (plan sheet, `ui/RouteWarnings`): one amber ⚠ line, tap → "On this route" list → tap a row →
the map shows that spot. Way warnings (pushing, steps, above your T, steep) – no pushing / steep for
MTB / E-MTB (owner: "remove the rules for MTB – we don't want to look stupid": footways, hard trails
and steep sections are what they ride; with them the line was on 87 % of MTB routes, without 55 %); a signed way stays, worded as
legal information: bicycle=no → "1.2 km no bikes allowed" (row "No bikes allowed – sign on the way"), bicycle=dismount
→ "300 m dismount (sign)" (`RouteWarningsTest.mtbHasNoPushingOrSteepHint` / `mtbSignedWaysAreLegalInfoNotPushing` /
`mtbKeepsTheHintsToCheck`); hints to check before
going (owner: "a warning/hint on a route if it contains a ferry or anything else that requires
validation"): **ferry** for every activity ("Ferry 320 m – check times", several: "2 ferries – check times"; first in
the line), **construction site** ("Construction – may be closed"), **ford** (way or node; "Ford – water to cross") and,
for bikes only, a **stile / kissing gate / turnstile** ("Stile – lift the bike over"). The tags come from BRouter's way
and node tags per span (`BRouterWayStats.perNode` → `WayAttrs.ford / barrier / construction`). Tests:
`RouteWarningsTest`, `BRouterWayStatsTest.hintTagsReachTheWayAttributes`, `WayTimesTest.codeRoundTrip`, screenshots
`ScreenshotTest.planFerryHint` / `routeSpotsListHints`; JVM `tools/routing/jvm-test/FerryHintTest.kt` (Rhine ferries
Zons ⇄ Urdenbach, Kaiserswerth ⇄ Langst, every activity class). Counts on real routes: `HintInventory.kt`.
**"New route for Hike T2 – check it"** (safety audit B, owner 2026-10-22): after an activity / T level change the new
route is compared with the one before (`RouteWarnings.changedNoticeably`) and a noticeable change gets this grey INFO
line until tapped – **during a tour** (a re-route on the way) and for a change from elsewhere (the service, the switch
sheet, a browser plan) only; the user's own activity / T choice in Plan with no tour running gets none (owner
2026-11-07: they changed it themselves; `RouteWarnings.saysChanged`, `RouteWarningsTest.ownChangeInPlanSaysNothingButTourAndNewerDataStillDo`).
Newer routing data keeps its own grey line "Plan recalculated with newer map data – please check the route."
