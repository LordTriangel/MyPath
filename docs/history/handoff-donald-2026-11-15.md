# Handoff donald, 2026-11-15 (evening) – the owner's hike findings

The owner walked with 0.17.10 (no crash) and sent findings one by one. Everything below is on `main` except the hold
rule (branch). Nothing is released. The owner's Pixel (44080DLJH000E5) has the test APK of the hold branch on top of
today's main (installed 23:13, `adb install -r`, plan and tours kept). Problem log of the day: no ERROR, only
"main thread blocked while leaving the app" WARNs.

## Decided today (the owner's words in quotes)
1. **Place menu „Einfügen“ / „Neues Ziel“** – both rows always, one line each, no where-line, no km („das sehe ich ja
   auf der Karte“); the likelier first and tinted; nothing of the two under „⋯ Mehr“. The search card uses the same
   words. Shared engine → the planner shows both rows too (laptop line in the backlog „For the roles“).
2. **Hold on the empty map while planning (branch `donald-2026-11-15-hold-menu`, F-PLAN-15, NOT on main):** „Die App
   nimmt, was den kürzeren Umweg macht; sind beide ähnlich, fragt sie.“ On the line (~24 dp) → insert; append cost
   g ≤ insertion detour i → new goal, silent; i ≤ g/2 → insert, silent; between → the menu beside a ring at the spot,
   the cheaper first. The held-place menu's order follows the same rule while planning. Tour and round trip keep
   `PlaceAddMenu.zone`. Field cases as tests: the Färberweg / Hunsterstraße junction (23:03, appends silently) and the
   22:57 case (i≈177 m, g≈225 m → asks). The first version (angle from the previous point, shares of the whole route)
   asked at the junction – that is why the rule changed.
3. **A tap on a POI** shows its details everywhere: with a menu open and on a POI that is a route point (hold = point
   menu as before).
4. **Back while recording** never leaves the app („aus der Aufzeichnung verlassen wir die App nicht“); „App
   verlassen?“ is gone; Back closes what is open inside the app; a trace line counts it.
5. **Zuletzt gesucht** keeps 20 (was 8).
6. **Orte merken (F-PLAN-14):** Zuhause on top of the empty search, ≤5 favourites + „Alle Favoriten ›“, Zuletzt
   gesucht, all one-line rows; „Merken“ / „Als Zuhause festlegen“ in a place's menu. No „Nach Hause“ button during a
   tour (owner: only 1). In the backup (`OnPath Orte.gpx`), not synced with the planner.
7. **My routes overview swipeable** („3 von 27“ pill) with stored map pictures (`files/overviews/`, newest 20 +
   favourites, cap 40, not in backup; new map areas do not redraw a stored picture).
8. **Tour review (F-NAV-07):** a done tour's overview = three big values, picture + profile, „x % schneller als
   geplant“, highlights (longest pause, highest point, fastest km – place shown as „km 6,2“, no offline place name),
   Details sheet, share → GPX / plan export. Our colours only (the owner liked another chat's orange proposal but it
   is too close to Komoot). Three sketch rounds: compact, one phone page.
9. Answered: a plan is stored as the plan (points, activity, line), GPX only on export („<name> - plan.gpx“ = track +
   named waypoints; other apps show line + markers; OnPath rebuilds the plan from it).

## Open for the owner (also in the backlog „Waiting for the owner“)
- **23:25, owner: the new hold rule „funktioniert nicht so richtig“ – keep it on the Pixel, open point.** (a) The „S“ of a street-looking label was a bus stop's name (icon hidden) → place menu; (b) twice „Neues Ziel“ silently where he pressed Undo – ask what he wanted (backlog line has the numbers). Do NOT merge the branch before that.
- Try the hold rule on the Pixel (same junction, plan further) → his yes → merge the branch to main.
- Then a release of today's batch on his word (none of it is released).

## Pages
Insert menu https://claude.ai/artifact/WjKusxvp2S1Ya6fTRkUWNx · places (compact) https://claude.ai/artifact/LqFT7h7KhEmKRdGJUVtrG9 ·
swipe https://claude.ai/artifact/QwU5BSHSbxbF9xUSz76T6X · tour review https://claude.ai/artifact/4QQF66CN7f8L2pJ15xY5fm ·
Fragenregister entries donald-20261115-1 … -8.

## Notes
- The Pixel's gesture decisions are in `files/events.log` (run-as), not in problems.json – read it for "why did it ask".
- The local AVD Medium_Phone ran out of disk space during an E2E run (the agents used the Rundgang emulator).
- Idle Gradle / Kotlin daemons from the evening were left to time out (stopping them by hand was not allowed here).
