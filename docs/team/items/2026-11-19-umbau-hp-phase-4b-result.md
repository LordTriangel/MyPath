# [umbau-hp] 2026-11-19 night: phase 4b (screenshot tests) – what is done, and why I stop here

**Done, on main:** three families as component pictures (`ShotGallery.kt`): the ☰ menu (pilot), the place / point /
hold and My-routes menus, the message boxes in Plan's top slot. 40 whole-screen pictures removed, 30 component pictures
added, 2 byte-identical duplicates found; 574 → 566 pictures (others added pictures meanwhile). Each batch: built by a
Sonnet helper with a list "removed picture → where its point is checked now", full gate, fresh Opus review – the
review found one lost check (a placement case at the largest font), restored before the push.

**Not converted, with the reason:**
- **Dialogs (21 pictures):** the `AlertDialog`s write title, text and buttons inline; `EndTourContent` /
  `ArrivalContent` are private and scroll. Needs their content as own composables in `app/src/main`.
- **Navigate cards, bottom bar, slider (~80 pictures):** drawn through `NavFrame`, which is the app's real
  `NavigateOverlay` scaffold – these pictures check the real screen, so the screen is the point. Judged from the
  inventory and the scene code, not tried.
- **Plan sheet (~90 pictures):** `PlanPanel` scrolls and its three steps are heights of the screen; a cell has no
  screen height. With a phone-high cell the picture is as big as before and the sheet's edge against the top bar and
  the controls – which several scenes are about – is gone. Judged, not tried.
- **My routes, Settings, Setup:** bare screens already; the screen is the subject.

**What I learned about the target:** "549 → ~200 pictures" and "less test time" do not come from component pictures
(`2026-11-19-umbau-hp-phase-4b-pilot-findings.md`). The remaining cost is re-recording ~170 pictures whenever the
footer, the map controls or the top bar change, because the test's `Frame` copies Plan's layout by hand.

**My recommendation (yours to decide, [umbau]):** close 4b here. If re-recording still hurts after phase 2: (1) let the
tests draw Plan through the app's own scaffold (make `PlanMode`'s layout callable, as `NavigateOverlay` is) – then
`Frame` cannot drift from the app; (2) give the dialogs content composables. Both are app code, so after step 21 and
by whoever owns the screens then. I can do either when you say so.
