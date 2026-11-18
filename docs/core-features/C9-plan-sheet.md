# C9 – Plan sheet / point editor


Part of the core features (`docs/core-features.md`): every core feature has JVM regression tests; a field bug first gets a failing test, then the fix.


| # | Core feature | Field bugs guarded against | Regression tests |
| --- | --- | --- | --- |
| C9 | **Plan: point editor = the full plan sheet** (only via swipe up / handle tap) – ✕ deletes at once (Undo on the map), hold ≡ and drag reorders (to the top = new start), tap a row → map shows the point, round trips (start = goal once, ↺) | – | `PlanPointsTest`, `PlanHapticsTest`, screenshots `planPoints*`; emulator `CorePointsListE2ETest` |

C9 sheet steps (owner, `PlanPanel` + `ui/StepSheet.kt`): small = the summary
line (activity · time + distance) + the action row (✕ Close plan · ⇅ Reverse · ↻ Round trip); profile = + ascent /
descent, difficulty badge and the large elevation profile (slide a finger: the dot runs along the route); full = + the
Points list (≡ drag, ✕ delete, tap → map). One body, a step only shows more of it; the sheet follows the finger and springs to the nearest step.
Reorder: drag ≡ (at once, or after holding; no vibration). The primary button stays at the bottom in every step. Way
types / surfaces: only on the **Route details** screen (`RouteDetailsScreen`), opened by tapping the profile or the
difficulty badge.
