# Progress design on main (donald 2026-11-19)

Owner's order (`2026-11-19-wait-window-real-progress.md`, `2026-11-19-captain-to-donald-laptop-ich-kann-weiter.md`) +
his look at the sketches: „A, aber nie mehr als 3 Häkchen; B: einfach nur Prozent, muss aber passen für die gesamte Aktion“.

- **A wait window** (first map, new place, routing data, route calculation): one bar 0–100 over learned step shares
  (`core/domain/WaitProgress.kt`: `OneBar`, `StepShares`, `RouteShares`, `WaitSteps` (max 3 rows), `TimeLeft`), never back;
  an overrun keeps moving (one percent every max(8 s, wait/5)) and says „dauert etwas länger…“; ends at 100 %.
  Shares learned per phone in prefs `onpath_progress` (`data/ProgressMemory.kt`), bounded, defaults on failure; paused
  waits teach nothing.
- **Join-in measured:** `CellStore.apply` reports bytes of the pieces in (`MapCells.joining`); routing rewrite reports
  bytes written (`Rd5Parts.write` → `Rd5PartStore.writing`). Callbacks guarded – a throwing reporter never stops a write.
- **B sign:** one ring with one percent for the whole action (`ActionPercent`), seeded from the window at handover,
  moves while the server prepares; tap → details.
- **[laptop] the planner mirrors:** `WaitProgress.kt` is shared code; `WaitBar` returns 1 when all legs are done (was
  null); new strings `wait_*`; `WebPlanProgress` still uses `PlanStatus.calculating` – the app no longer does; the
  planner's wait window should get the same bar, ≤3 steps and texts.
- Review (independent, 2026-11-19): map writes unchanged and atomic; 7 promise findings fixed with tests (`WaitPromiseTest`).
- Not done: the app update has no wait today (non-blocking card) – unchanged.
- **Round-trip search is form A too** (owner 2026-11-19 „könnte auch als Overlay kommen mit der Option Abbrechen“):
  `ui/progress/SuggestWait.kt` – the method's real phases (`TourSuggester` `lapped`) as 3 steps „Wege lesen · Runden
  suchen · bewerten“ (network+facts · place+loops · judging), shares learned per phone (`ProgressMemory.suggestShares`,
  key `suggest_steps`), „Abbrechen“ = `SuggestSlice.cancel()`; while it runs the map takes no tap / hold
  (`SuggestSlice.searching`: MapHost ignores presses, `open` ignored). [laptop] the planner's wait card could follow.
