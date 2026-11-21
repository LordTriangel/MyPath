# Detour stitch + long-half cuts – design proposal (2026-11-21)

Status: proposal, nothing built. Input: `docs/team/items/2026-11-20-add-point-speed.md` (measurements + owner's decisions).
Goal: a point added to a long leg routes in ~0.3 s instead of 4–15 s, and **every device gives the same route for the same
plan** (phone, browser, sync, plan link, GPX export, saved route reload).

## 1. The one idea: anchors are hidden via points stored on the point
A stitched point P keeps the points of the old line where its detour leaves and rejoins it. The Planner routes the plan
**expanded**: `[…, prev, a, P, b, next, …]` – leg by leg, exactly as today (`Planner.plan`, `LegCache`). Nothing else
changes in routing: same expanded list = same legs = same route (the rule `LegCache` already lives by).

```kotlin
/** Hidden via points of a plan point, all ON the line it was put on, in route order (1e-6°, BRouter's own grid). */
data class Anchors(val before: List<GeoPoint> = emptyList(), val after: List<GeoPoint> = emptyList())
// SharedPlan.anchors: Map<Int, Anchors> – the 4th per-point attribute beside pinned / names / nights
```
- **Stitch** (≤ 2 km off the visible leg's line, that leg's line > 60 km): `before = [line point 10 km before P's
  projection]`, `after = [10 km after]`. A side is left empty when its neighbour is closer than 10 km + 2 km along the
  line (that side is short – routed in full, cheap).
- **Speed-up 3** (beyond 2 km, or the far parts of a stitch): the long half gets cut points on its old line every
  50–80 km (line points chosen on the editing device only – it does
  not matter, they are stored), none closer than 50 km to P's projection. They go into the same lists (`before` =
  cuts then the 10 km anchor). Same data, same rules, one concept.

### Options considered
| Option | Same route everywhere? | Verdict |
|---|---|---|
| A. Flag only, anchors recomputed (10 km along the base line) | needs the old line on every device (cold browser: route the full base first) and identical float geometry JVM ⇄ Wasm (`Geo.distance` sin/cos may differ by an ulp → another line point) | no |
| B. Store the stitched geometry on the point | exact, but megabytes in a plan code / sync item; stale after a routing-data update | no |
| C. Anchors cut "the base leg prev→next" | exact, but the base depends on the neighbours: a second point on the same long leg has no routed base → does not compose | no |
| **D. Anchors as hidden via points (coordinates)** | yes up to one invariant (below); composes; tiny (a few bytes per point) | **recommended** |

**Why coordinates, not indices:** an index into a routed line means nothing on a device with other routing data or before
the line exists; a coordinate is plain data, survives every transport, and BRouter line points are 1e-6° integers – so the
anchor is exactly a line point and `LegSplit` cuts there without tolerance games.

**What the anchors buy, and the one invariant left.** Without anchors a cold device routes `prev→P` and `P→next` in full
– at 5–10 km off that is another valley (+9 to +34 km measured). With anchors every device routes `a→P→b` itself, so the
detour is identical everywhere. What is left: the editing device takes `prev→a` and `b→next` as **cuts of the cached old
leg** (no routing), a cold device **routes them** fresh. They are equal when "a route through a point of an optimal route
= that route's piece" (sub-path consistency). This is the same assumption `LegSplit` (0.18.3, nights on the line) already
ships with; the check in §5 measures it. Safety net if §5 finds mismatches: the editing device routes the cut legs once
more when the router is idle (lowest priority, dropped on any edit) and replaces them – converges to the cold line.

## 2. Edits (all in core `PlanAnchors.follow`, used by app and planner via `PlanEdits` / `PlanPoints`)
Anchors follow their point like pins / names / nights (`PlanEdits.follow`, `keepMoved = false`).
| Edit | Rule |
|---|---|
| Delete P | its anchors go with it (owner). `prev→next` comes back from `LegCache` (the old base is usually still there) – instant |
| Undo / redo | the history snapshot holds the anchors (`PlanHistory` / page undo) – legs from the cache, nothing routed |
| Move P | P is a new point → anchors dropped; a move that stays ≤ 2 km of the current line is stitched again against it (Q3) |
| Insert N between `prev` and a `before` anchor (by line position) | P keeps its anchors (the route still passes them) |
| Insert N between an anchor and its P | **transfer**: the anchors between N and P move from P's `before` to N's `before` (N on the line: zero routing; N off the line: N gets its own stitch, the transferred anchors beyond N's 10 km anchor are dropped and the cut leg is concatenated – same invariant) |
| Delete / move / replace a neighbour | the facing side of P's anchors is dropped (the new neighbour's natural way need not pass them) |
| Reverse | `before` ⇄ `after`, lists reversed |
| Round trip / loopStart | no special case (the closing leg is a leg) |
| Activity / T level change | all anchors cleared (they lie on the other profile's line) – full routing, as today |
| „Ganz neu berechnen" | all anchors cleared, full re-route of the visible points |

All decisions (stitch or not, where the anchors go) are made **once, on the editing device**, from its line, and stored.
Other devices only read data – no device-dependent geometry decides a route.

## 3. Transport and storage
- **Plan code** (`PlanCode`, still VERSION 1): optional tag 7 = anchors: varint point index, varint count before,
  varint count after, then each anchor as zigzag deltas in 1e-6° to the previous one (first: to the point ×10). Reader
  drops an entry whose index is out of range, more than 8 per side, or an anchor > 200 km from its point. **Older apps
  skip tag 7** (existing rule "unknown tags skipped") → they show the same points, route in full (slower, possibly a
  slightly different route), never lose the plan. An older app that re-saves the plan writes it without tag 7 – the plan
  then degrades to plain routing everywhere (consistent, never broken). Readers ship one release before writers (§6).
- **Sync**: the item carries the plan code → anchors travel for free; merge stays "newer wins, older stays as a copy"
  (whole plan, no field merge). `SyncItem.routing` / `routingData` keep telling when two sides route on different data.
- **Saved route** (`Tour`): one new JSON column `anchors` (schema +1, like `days` in `MultiDay`; unreadable = none,
  reported). Same field in `TourPlan` (a done tour's plan copy), `BackupCodec`, `RouteItems` (route ⇄ sync item),
  Zuletzt drafts, `PlanStore`.
- **Reload**: all expanded legs (incl. the cut ones, `LegCache.put` → `LegFiles`) are in the leg store → instant; a new
  phone / browser routes the expanded list (cost as today, never more).
- **GPX export** (`PlanGpx`): the computed line (stitched) + the visible points as `<wpt>`; anchors are not exported. A
  re-import is a new plain plan (as for any GPX today) – the line in the file is the one every device shows.
- **Multi-day**: nights are visible points; anchors never carry a night and are never a day border. A night put on the
  line inside an anchor span = "insert N between anchor and P" → transfer, zero routing (`LegSplit` cuts the sub-leg).
  `PlanDays` works on the visible points + the line – unchanged.
- **Navigation**: follows the computed line; the rerouter (`Rerouter`, `TourSplit`) targets visible points only and
  ignores anchors (a get-back route is a fresh route anyway).

## 4. Where it lives
- **core/routing** (shared, the planner compiles it): `PlanAnchors.kt` – `Anchors`, `expand(points, anchors, pinned)`
  → expanded list + index map back; `forInsert(legLine, prev, next, p)` (stitch / cut rule, the thresholds as constants);
  `follow(before, after, …)` (§2). `Planner.plan(…, anchors)` and `PlanResult.plan(…, anchors)` expand inside and map
  `snapped` / `skipped` / gaps back to visible indices – callers cannot forget it. `LegSplit.seed` learns to cut any
  line point of a known **chain** of legs and to concatenate two legs (for transfers). `PlanCode` tag 7, `SharedPlan.anchors`.
  No `PLANNER_VERSION` bump (plans without anchors route as before).
- **core/data, gpx, plansync**: storage fields (§3).
- **state** (app): `PlanDocument` / `PlanHistory` hold anchors; the add-point flow calls `forInsert`; `PlanRecalcSlice`
  passes anchors; "Ganz neu berechnen" action + `hasAnchors` for the menu row.
- **app**: the menu row (last row of the plan menu, only while `hasAnchors`; picture to the owner first), Room migration.
- **planner (web/)**: `WebApi` / `WebPlanChain` pass anchors (page state + its undo), `WebLegSplit` replaced by core
  `LegSplit`, the same menu row with the app's string, `parity.json` entry.

## 5. The "same route everywhere" check
1. `PlanAnchorsTest` (core, fake engine, runs everywhere): expand / map back, every rule in §2, code round trip, an
   older reader (`PlanCode.decode(knownTags = 6)`) gets the same points.
2. `SameRouteTest` (core, real BRouter, where the rd5 tiles are – `ONPATH_PERF`-style switch, laptop + the planner CI if
   it has the tile cache): corpus = München–Venedig Bike + Hike T2 and two more long tours; 20 adds ≤ 2 km, 10 adds
   5–10 km with cuts, then a night in a span, a delete, an undo. Per case: **warm** (old legs cached, stitch via
   seed/cut) vs **cold** (empty cache, route the expanded list) → `RouteHash` (sha256 of the 1e-6 line + rounded
   totals) must be equal. The planner runs the same test compiled to Wasm (web/engine test) and must hit the same hashes,
   checked in as `core/src/test/resources/same-route/*.sha`. A mismatch fails the gate and turns on the §1 safety net.

## 6. Rollout
1. Readers: core model, tag 7, expansion, storage – app + planner route anchors but never create them (one release).
2. `SameRouteTest` green on JVM and Wasm.
3. Writers: stitch on insert + transfers + „Ganz neu berechnen" (owner picture), app and planner in the same release.
4. Speed-up 3 cuts (same field, own corpus run).
5. Only if §5 found mismatches: idle re-route of cut legs.

## 7. Risks
- Sub-path invariant fails somewhere (BRouter heuristics, turn costs at the anchor) → warm and cold lines differ by a
  bit; caught by §5, healed by the safety net.
- A forgotten caller routes without anchors (navigation, `SyncedRouting`, `TrackToPlan`, thumbnails) → another line;
  mitigated by putting the expansion inside `Planner` / `PlanResult`, plus a test that every `Planner.plan` call site
  passes the plan's anchors.
- Routing-data update under an anchor (the way moved) → the route still passes the stored coordinate (a small bump);
  „Ganz neu berechnen" fixes it; acceptable.
- UI shows hidden points by mistake – they never enter the visible point list, only the Planner's input.
- Pre-feature apps silently drop anchors on re-save (Q2).

## Questions for the owner
1. Do speed-up 3's cut points also count for „Ganz neu berechnen" (row shows whenever the route keeps any point of an
   older line)? **Recommendation: yes** – one rule, one button.
2. An app from before this feature opens a stitched plan in full routing (slower, maybe slightly another route) and drops
   the anchors when it saves it; the plan is never lost. OK, with readers shipping one release before writers?
   **Recommendation: yes.**
3. Moving a stitched point: re-stitch when it stays ≤ 2 km of the line (fast), or always route in full (slow on long
   legs)? **Recommendation: re-stitch** – a move is an insert at another spot.
