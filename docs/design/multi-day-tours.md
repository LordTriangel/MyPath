# Multi-day tours – concept (owner discussion 2026-11-16)

Status: **concept, nothing built.** The owner chose the base model and said "noch nichts bauen – erst weiter
überlegen". This page is the agreed state and the build order for the day the owner says go. Before any UI is built:
a sketch of the exact picture and his yes (owner rule).

## 1. What the owner decided

| # | Decision | His words |
| --- | --- | --- |
| 1 | A multi-day tour is **one normal plan** in which points are marked as **day end**. Days are parts of it, not stored routes. | "plan mit tagesenden" |
| 2 | The route **leads to the accommodation**; the detour counts. | "Punkt 1 stimme ich dir zu" |
| 3 | An **accommodation** is more than a plain day end (it has a place and a name, it is fixed). | "eine Unterkunft etwas anderes als ein normales Tagesende" |
| 4 | On the phone the user **always picks the day** himself. No guessing from the position – a break, a day by train, a skipped day need no rule. | "Einen Tag werden wir immer auswählen" |
| 5 | **Planned in the browser planner first**, then sent to the phone. No multi-day planning on the phone in the first version. | "erstmal nur auf dem Planer im Netz" |
| 6 | The phone should load **all maps of the tour**; the right moment is open (§6). | "sollte es schon alle Karten runterladen. Die Frage ist, wann?" |
| 7 | Multi-day tours often start from a **downloaded GPX** (München–Venedig): cut off start and end, keep the part, make a plan from it. Find one UI that combines this. | "Schneiden von GPS-Routen" |
| 8 | Little effort, robust, extendable later. | |

Left out of the first version (my reading of "lassen wir weg", to confirm): rest days / several nights at one place, and
day trips from one base (that is a folder of single tours, a different thing).

## 2. The model

- A plan is a list of points (today: position, pinned or not, optional name). New: a point can carry a **day end**
  mark with a kind – `plain` or `stay` (accommodation).
- **Day n** = the points from day end n−1 (or the start) to day end n (or the last point). The end of one day and the
  start of the next are the same point, so moving it changes both days and nothing can drift apart.
- A plan without day ends is exactly today's plan. A plan with day ends opened by an older app is a normal long plan
  (§5) – the safe fallback.
- **Stay:** pinned (the route passes it exactly), named, placed where the accommodation is. Removing the stay leaves a
  plain day end at that point; the point itself stays until the user deletes it.
- **"Floating" in plain words** (the owner did not follow the term, so it is dropped): *Split again* replaces only the
  **plain** day ends. A **stay** is never moved by the app. That is the whole rule.
- **A new accommodation takes over the day end:** when a stay is inserted, the nearest plain day end in the two days
  it touches loses its mark – the day end "wanders" to the accommodation, as the owner described.
- The frame: the tour's description is one text on the plan; the overnight places are the stays. No other container.
- A single day "on its own": copy a day out as a separate route, or export it as GPX. A copy, not a live link.

## 3. The flows

### Browser planner (where the tour is made)

1. **Get a line.** Plan it as today, or import a GPX (exists: the track is shown with its panel – keep as a track, or
   "Plan a route from it").
2. **Trim (new).** The track panel gets two handles – start and end – on the map line and on the height profile. The
   part outside is shown grey. "Plan a route from it" takes only the part between the handles. The file itself is
   not changed, so nothing can be lost.
3. **Combine (new).** With a plan already on the map, the track panel offers **"Append to the plan"** as well: the
   (trimmed) track becomes route points behind the plan's last point; the joint becomes a plain day end. This one
   button covers "glue several routes together" – no separate tool.
4. **Split (new).** "Split into days…": either a number of days, or limits per day (time, ascent – the same kind of
   limits as the tour suggestions). The app puts plain day ends on the line; the route does not change.
5. **Adjust.** The points list shows a header per day (Day 2 · 62 km · 800 m ↑ · 4:10 h). A point's menu: "Day end" /
   "Accommodation" / remove. Drag a day end like any point.
6. **Accommodations.** Search a place or paste a Google Maps link in the planner → "Accommodation, day n".
7. **Save** – the plan syncs to the phone as today.

### Phone (where it is ridden)

1. The tour arrives by sync. My routes shows one row "München – Venedig · 7 days"; it opens to the days.
2. Maps: §6.
3. **Pick a day** → that day is on the map as the plan to follow. Start, navigation and recording work as for any
   plan; the recording is a normal day tour.
4. A new accommodation found on the way: share the place to OnPath (exists) → "Accommodation in the tour" (new
   choice, later stage). The plan changes, syncs back.

## 4. Why this fits the architecture

| Piece | Today | Change |
| --- | --- | --- |
| Plan format `routing/PlanCode.kt` (shared with the planner) | points, pinned bits, optional fields by tag; **unknown tags are skipped** by older readers | one new optional tag: day end (point index + kind), one for the description. No new version, no new sync item kind. |
| Sync `sync/SyncItem.kt` | a plan item carries the plan code | unchanged |
| Index bookkeeping `routing/PlanEdits.kt`, `PlanPoints.kt` | every insert / delete / move / reverse keeps `pinned` and `names` on the right points | day ends are the **third per-point attribute** and must travel the same way. This is the one place where a bug would be nasty – it gets the tests first. |
| Days | – | new pure file (shared), e.g. `routing/PlanDays.kt`: plan + day ends → day ranges; per-day distance / ascent / time from the existing leg results; the split rule. No UI, no Android. |
| Routing `routing/Planner.kt`, `LegCache.kt` | per leg, changed legs only | unchanged. Moving a day end routes two legs. |
| Track → plan `routing/TrackToPlan.kt` (shared) | whole track | takes a range (trim); a second entry "append". |
| Following a day | Navigator / recording take a plan | they get the day's part of the plan – from their view a normal plan. `navigation/TourSplit.kt` (edits during a tour) keeps working on that part; an edit is written back into the whole plan by index offset. |
| Phone store `data/TourDb.kt` | `tours.waypoints`, `pinned` | the day ends beside them (one column / field in the waypoint text) |
| Maps `cells/CellTriggers.kt` | corridor along every saved route, automatic when online (F-OFF-02) | a multi-day plan must not start this silently – §6 |

What stays untouched: recording, the route line, the mailbox server, the map files.

## 5. Limits to measure before building (stage 0)

- A plan code holds at most **500 points** and **16 000 characters**. München–Venedig (~560 km) as a plan from a
  track: how many route points does `TrackToPlan` need (it stops at 60 router calls today)? If it does not fit, the
  limits or the track-to-plan step need work first.
- First route of ~560 km in the browser and on the phone: time, memory. (113 km on the desktop: ~1 s; the Pixel was
  5–8× slower, `docs/design/long-bike-routes.md`.)
- Map size for the whole corridor (bike today: 20 km either side). A narrower corridor for multi-day tours may be
  needed.
- An imported track as a sync item is thinned to 16 KB – fine for looking, another reason the tour travels as a plan.

## 6. Maps on the phone – when (recommendation, owner to decide)

Not silently on arrival: a 7-day tour can be gigabytes, possibly on mobile data.

- The tour arrives → its row in My routes says "Maps missing" (no dialog out of nowhere).
- Opening the tour the first time asks **once**, with the size: "Load the maps for the whole tour (≈ 1.4 GB)?" –
  *Whole tour* / *Only day n* / *Later*. On mobile data the default is to wait for Wi-Fi.
- Picking a day always checks that day's maps and says so before the start if something is missing – the robust
  net under everything else.

## 7. Build order

Each stage is usable alone and leaves the app releasable.

| Stage | What | Where | Done when |
| --- | --- | --- | --- |
| 0 | Measure §5 with a real München–Venedig GPX. No UI. | tools / tests | numbers on this page; go or change of limits |
| 1 | Day ends in the plan code, `PlanDays`, index bookkeeping. No UI. | shared code | unit tests: round trip old ⇄ new reader, every edit keeps day ends, day ranges, split rule |
| 2 | Planner: day headers, mark / unmark day end and accommodation, "Split into days…" | `web/` | sketch approved → built → planner smoke |
| 3 | Planner: trim handles on an imported track, "Append to the plan" | `web/` + `TrackToPlan` | same |
| 4 | Phone: tour row with days, pick a day, follow it; maps question (§6) | app | E2E: synced 3-day plan → pick day 2 → start → recording saved |
| 5 | Phone: shared place → accommodation; tour description shown | app | – |

Later, not designed here: several nights / rest days, "done" ticks per day from the recordings, planning on the
phone, day trips from one base.

## 8. Open points for the owner

1. §1 last line: are rest days **and** base-camp tours both out for now?
2. §6: the moment for the map download.
3. Stage 2/3 need a sketch of the planner (points list with day headers, trim handles) before anything is built.
