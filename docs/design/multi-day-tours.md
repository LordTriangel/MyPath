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
| 6 | The phone should load **all maps of the tour**; the right moment is open (§7). | "sollte es schon alle Karten runterladen. Die Frage ist, wann?" |
| 7 | Multi-day tours often start from a **downloaded GPX** (München–Venedig): cut off start and end, keep the part, make a plan from it. Find one UI that combines this. | "Schneiden von GPS-Routen" |
| 8 | Little effort, robust, extendable later. | |

Left out of the first version (my reading of "lassen wir weg", to confirm): rest days / several nights at one place, and
day trips from one base (that is a folder of single tours, a different thing).

## 2. The model

- A plan is a list of points (today: position, pinned or not, optional name). New: a point can carry a **day end**
  mark. There are three kinds:

  | Kind | Who set it | Picture | May the app move it? |
  | --- | --- | --- | --- |
  | **free** | the app ("Split into days") – a proposal: "about here" | hollow ring with the day number | **yes** |
  | **fixed** | the user: dragged a day end, or marked a point as day end | filled ring | never |
  | **stay** | the user: an accommodation – place and name, the route leads there (pinned) | bed | never |

- **Day n** = the points from day end n−1 (or the start) to day end n (or the last point). The end of one day and the
  start of the next are the same point, so moving it changes both days and nothing can drift apart.
- A plan without day ends is exactly today's plan. A plan with day ends opened by an older app is a normal long plan
  (§6) – the safe fallback.
- **Floating** (the owner 2026-11-16: "eine clevere Idee" – explained again): the free day ends are the ones that
  float. Start, finish, fixed day ends and stays are **anchors**. Between two anchors lies a **stretch** with a set
  number of days. Whenever something in a stretch changes – an anchor moves, an accommodation comes or goes, the
  route gets longer – the app spreads that stretch's free day ends again so its days are equally heavy (by time).
  Other stretches do not change. What the user has touched stays; what the app guessed adjusts. When every night is
  booked, nothing floats any more.
  Example: München–Venedig in 6 days, all day ends free, ~95 km each. A hotel is booked at km 170 instead of the
  proposed km 190. Without floating: day 3 grows to 115 km, the rest stays. With floating: days 1–2 share the 170 km
  (85 each), days 3–6 share the remaining 390 km (4 × ~97).
- A free day end the user drags becomes fixed (he chose the place). The menu of a fixed one offers "Let the app
  place it" (back to free).
- Removing a stay: the accommodation's point goes (and with it the detour); a **free** day end takes its place on the
  route. The day stays, only the bed is gone.
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
   (trimmed) track becomes route points behind the plan's last point; the joint becomes a fixed day end. This one
   button covers "glue several routes together" – no separate tool.
4. **Split (new).** "Split into days…": either a number of days, or limits per day (time, ascent – the same kind of
   limits as the tour suggestions). The app puts free day ends on the line; the route does not change.
5. **Adjust** – the scenarios of §4.
6. **Accommodations.** Search a place or paste a Google Maps link in the planner → "Accommodation, day n".
7. **Save** – the plan syncs to the phone as today.

### Phone (where it is ridden)

1. The tour arrives by sync. My routes shows one row "München – Venedig · 7 days"; it opens to the days.
2. Maps: §7.
3. **Pick a day** → that day is on the map as the plan to follow. Start, navigation and recording work as for any
   plan; the recording is a normal day tour.
4. A new accommodation found on the way: share the place to OnPath (exists) → "Accommodation in the tour" (new
   choice, later stage). The plan changes, syncs back.

## 4. Scenarios – what the user does and how (owner 2026-11-16: part of the concept, so the UI is not rebuilt later)

The words of the UI in every scenario: the points list with a header per day (Day 2 · 62 km · 800 m ↑ · 4:10 h), the
three markers on the map and in the height profile (ring, filled ring, bed), the menu of a day end, the menu of a
place. Every step is one undo step (the planner's history).

| # | Situation | What the user does | What the app does |
| --- | --- | --- | --- |
| A | **First split.** A long plan, no days yet. | Plan menu → "Split into days…" → 6 days (or: at most 5 h / 1000 m ↑ a day) | 5 free day ends on the line, 6 day headers. The route is unchanged. |
| B | **First accommodation.** A hotel found near the end of day 2. | Search the place / paste the Google Maps link → place menu "Accommodation" → "End of day 2" (the nearest day end is preselected, another day can be chosen) | The stay replaces day end 2, the route leads to it. Days 1–2 and days 3–6 float (each stretch spread again). |
| C | **Accommodation cancelled or changed.** | Bed → "Remove accommodation". Or directly: new place → "Accommodation" → "replaces Hotel X (day 2)" | Removed: a free day end on the route takes its place and floats again. Replaced: as B. |
| D | **A day longer or shorter** (sightseeing on day 3, a hard pass on day 4). | Drag the day end along the line on the map or in the profile | It becomes fixed. The free day ends of the two stretches beside it float. |
| E | **A day more or fewer.** | "Split into days…" again with 7. Or in a day header's menu: "Split this day" / "Join with the next day" | Split again: only free day ends are placed anew, fixed ones and stays stay; the dialog says how many days each stretch gets. Split this day: a free day end in its middle. Join: the day end between them goes (a stay: asks first). |
| F | **The route changes** (another pass on day 4, a point dragged). | Edit the plan as always | The stretch got longer or shorter: its free day ends float. A day above the chosen limit gets an orange header – the app never adds a day by itself. |
| G | **On the way: not reached the day end, or ridden further** (phone). | In the tour's day list pick where to go on: one day, or several ("Day 2–3") | The picked part is followed from the current position as one plan (exists: follow from position). The plan is not edited on the phone, so nothing can break. |
| H | **On the way: new accommodation** (phone, stage 5). | Share the place to OnPath → "Accommodation, day n" | As B/C, then the plan syncs back. |

What these scenarios fix for the build:

- The three kinds and the floating rule belong to the shared code (stage 1), not to the UI – planner and phone behave
  the same.
- The plan stores, per stretch, nothing extra: the number of days in a stretch is the number of its day ends + 1.
  The limits of "Split into days" (time / ascent) are stored on the plan so the orange header has something to
  compare with.
- Floating happens **by itself** after a change, inside one undo step (recommendation – the alternative is a line
  "Days 3–6 are uneven – spread again?"; owner to decide, §9).
- The phone never changes the day ends in the first version (scenario G needs only "pick a range").

## 5. Why this fits the architecture

| Piece | Today | Change |
| --- | --- | --- |
| Plan format `routing/PlanCode.kt` (shared with the planner) | points, pinned bits, optional fields by tag; **unknown tags are skipped** by older readers | one new optional tag: day end (point index + kind free / fixed / stay); the split limits, one for the description. No new version, no new sync item kind. |
| Sync `sync/SyncItem.kt` | a plan item carries the plan code | unchanged |
| Index bookkeeping `routing/PlanEdits.kt`, `PlanPoints.kt` | every insert / delete / move / reverse keeps `pinned` and `names` on the right points | day ends are the **third per-point attribute** and must travel the same way. This is the one place where a bug would be nasty – it gets the tests first. |
| Days | – | new pure file (shared), e.g. `routing/PlanDays.kt`: plan + day ends → day ranges; per-day distance / ascent / time from the existing leg results; the split and the floating rule (§2, §4). No UI, no Android. |
| Routing `routing/Planner.kt`, `LegCache.kt` | per leg, changed legs only | unchanged. Moving a day end routes two legs. |
| Track → plan `routing/TrackToPlan.kt` (shared) | whole track | takes a range (trim); a second entry "append". |
| Following a day | Navigator / recording take a plan | they get the day's part of the plan – from their view a normal plan. `navigation/TourSplit.kt` (edits during a tour) keeps working on that part; an edit is written back into the whole plan by index offset. |
| Phone store `data/TourDb.kt` | `tours.waypoints`, `pinned` | the day ends beside them (one column / field in the waypoint text) |
| Maps `cells/CellTriggers.kt` | corridor along every saved route, automatic when online (F-OFF-02) | a multi-day plan must not start this silently – §7 |

What stays untouched: recording, the route line, the mailbox server, the map files.

## 6. Limits to measure before building (stage 0)

- A plan code holds at most **500 points** and **16 000 characters**. München–Venedig (~560 km) as a plan from a
  track: how many route points does `TrackToPlan` need (it stops at 60 router calls today)? If it does not fit, the
  limits or the track-to-plan step need work first.
- First route of ~560 km in the browser and on the phone: time, memory. (113 km on the desktop: ~1 s; the Pixel was
  5–8× slower, `docs/design/long-bike-routes.md`.)
- Map size for the whole corridor (bike today: 20 km either side). A narrower corridor for multi-day tours may be
  needed.
- An imported track as a sync item is thinned to 16 KB – fine for looking, another reason the tour travels as a plan.

## 7. Maps on the phone – when (recommendation, owner to decide)

Not silently on arrival: a 7-day tour can be gigabytes, possibly on mobile data.

- The tour arrives → its row in My routes says "Maps missing" (no dialog out of nowhere).
- Opening the tour the first time asks **once**, with the size: "Load the maps for the whole tour (≈ 1.4 GB)?" –
  *Whole tour* / *Only day n* / *Later*. On mobile data the default is to wait for Wi-Fi.
- Picking a day always checks that day's maps and says so before the start if something is missing – the robust
  net under everything else.

## 8. Build order

Each stage is usable alone and leaves the app releasable.

| Stage | What | Where | Done when |
| --- | --- | --- | --- |
| 0 | Measure §6 with a real München–Venedig GPX. No UI. | tools / tests | numbers on this page; go or change of limits |
| 1 | Day ends in the plan code, `PlanDays`, index bookkeeping. No UI. | shared code | unit tests: round trip old ⇄ new reader, every edit keeps day ends, day ranges, split rule, scenarios A–F of §4 as tests |
| 2 | Planner: day headers, mark / unmark day end and accommodation, "Split into days…" | `web/` | sketch approved → built → planner smoke |
| 3 | Planner: trim handles on an imported track, "Append to the plan" | `web/` + `TrackToPlan` | same |
| 4 | Phone: tour row with days, pick a day or a range (scenario G), follow it; maps question (§7) | app | E2E: synced 3-day plan → pick day 2 → start → recording saved |
| 5 | Phone: shared place → accommodation; tour description shown | app | – |

Later, not designed here: several nights / rest days, "done" ticks per day from the recordings, planning on the
phone, day trips from one base.

## 9. Open points for the owner

1. §1 last line: are rest days **and** base-camp tours both out for now?
2. §7: the moment for the map download.
3. Stage 2/3 need a sketch of the planner (points list with day headers, trim handles) before anything is built.
4. §4: floating by itself after a change (recommended), or only after a question?

## 10. UI – a separate round with the owner (2026-11-16: "über die UI reden wir noch einmal separat")

Round 1 (2026-11-17, a rough journey sketch, not a design): https://claude.ai/artifact/TdtKJFYzsmyQKJFTWSuCR1 – 11 steps from the GPX in the planner to riding day 4 on the phone, with the tour overview as step 6. The topics to bring to that round, with sketches (at most three variants, his yes before
any build):

1. **Tour overview in the plan** (owner 2026-11-16: "eine schöne Routenübersicht über die einzelnen Tage und
   Höhenmeter"): all days at a glance – per day distance, ascent, time, the overnight place; the height profile of
   the whole tour with the day borders in it.
2. The points list with a header per day; folding days open and shut.
3. The three day-end markers (free ring, fixed ring, bed) on the map and in the profile; dragging a day end.
4. "Split into days…" – number of days or limits per day; what it says about fixed day ends and stays.
5. The menus: day end (fix / let the app place it / remove), day header (split this day / join with the next),
   place ("Accommodation" → which day, "replaces Hotel X").
6. The orange day header when a day is above the limit.
7. GPX: the two trim handles on the line and in the profile, "Append to the plan".
8. Phone: the tour's row in My routes with its days, picking a day or a range, the maps question with the size.
9. The tour's description – where it is written and shown.
