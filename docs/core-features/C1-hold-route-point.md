# C1 – Plan: hold a route point


Part of the core features (`docs/core-features.md`): every core feature has JVM regression tests; a field bug first gets a failing test, then the fix.


| # | Core feature | Field bugs guarded against | Regression tests |
| --- | --- | --- | --- |
| C1 | **Plan: hold a route point (≤ 48 dp) → its menu → Delete / Start here / Google; beside it → a new point, never a question whether the point was meant (owner); in doubt where the new point goes, „Einfügen“ / „Neues Ziel“ (owner 2026-11-15, F-PLAN-15)**; Delete is the Points list's ✕ (`PlanPoints.deleteAt`): a round trip's start deleted, the loop closes at the next point | map turned under the finger; a new point added instead of the menu; a hold 68 dp from the point added a point next to it; "Delete did nothing"; a round trip's start deleted on the map opened the loop | `CoreHoldPointTest`, `CoreDeletePointTest`, `MapGesturesTest`; emulator `CoreDeleteE2ETest` |

C1 catch zone: within 48 dp the point menu, beyond it a new point (`MapGestures.MARKER_RADIUS_DP`); a hold 48–80 dp
from a point is logged as a near miss (`NEAR_RADIUS_DP`, diagnostics only). The point menu acts on the point it was opened for, found
by position (`PointMenuTarget`), not on an index that the plan can change under it. Every hold, press, menu open /
action / close and route result is one line in the gesture log (`Problems.trace`, last 50, never merged; logcat tag
OnPath and `problems.json` "events" in the diagnostics export).

Plan haptics (owner, `ui/PlanHaptics` + `ui/Haptics`, played by `MapHost` when the hold ring
is full): **vibrate at the moment of the press, never after the action, only on the map.** Hold on the empty map /
route line / a POI = 1× (40 ms); hold on a route point (menu with Delete) = 2× (40/100/40 ms); amplitude ~230. Nothing
after an edit (menu buttons, set, delete, undo) and nothing in the Points list (✕, drag). Only OnPath's setting
"Vibrate when planning" (default on) counts; the pulses carry an explicit media usage – unlabelled, Android treated the
short 1× pulse as touch feedback and muted it with the system "Touch feedback" off ("set" then never vibrated).
