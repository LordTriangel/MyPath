# [donald] Flow map + novice walkthrough (docs/design/flow-map.md, 2026-11-06) – open:

- **[donald] Flow map + novice walkthrough (docs/design/flow-map.md, 2026-11-06) – open:** H1 no way to start a tour
  without a plan (0–1 points: no green start on any sheet step, none in ☰; "Los geht's · ohne Route" unreachable –
  PlanPanel.kt:487/:650; after the F1/C build); M1 "Tour nachfahren" during a tour keeps the old plan – an edit switches the
  guidance back silently; M3/N3 new goal on the way = old goal silently becomes a stop (proposal: hold menu "Neues Ziel");
  M4 back from a pause: one rule with C; N1 Plan mode during a tour is silent – notification should say "Navigation
  pausiert – tippen zum Weiterfahren"; N2 "» Entsperren" hides Pause/Beenden (second line "Pause · Beenden · Plan"); N4
  back key in locked Navigate leaves the app; N5 bin hard to find; N6/N7 words "Original behalten / Neue Route",
  "Hinführen" (owner's words? ask); N8 undo icon only; N9 grey 12 sp lines in the sun. **Colour rule** C1–C17 (theme
  primary is green → every plain button green): owner thinks about an earth tone as the base colour (page
  https://claude.ai/artifact/P6SCbUKQhHuuPqJ9SmePq6, recommendation Torf #3B352F / Walnuss #4A3F37). H2 done (844c8401).
