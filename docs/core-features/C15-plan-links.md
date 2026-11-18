# C15 – Plan links


Part of the core features (`docs/core-features.md`): every core feature has JVM regression tests; a field bug first gets a failing test, then the fix.


| # | Core feature | Field bugs guarded against | Regression tests |
| --- | --- | --- | --- |
| C15 | **Plan links open as the plan** (A-005, web planner phase 1, `docs/design/web-planner.md` §8) – `https://plan.onpath.earth/#p=<code>` (`PlanCode` v1 – frozen) from the phone's camera (QR code), a tapped link (App Link), text shared to OnPath or pasted into the search box loads points, activity, T level, point names and pinned points like a saved route: "Replace current route?" (Save first / Replace) when the plan has unsaved changes – a plan still exactly as an earlier link brought it is replaced without a question (owner); during a tour nothing is loaded (grey line "Plan links open after the tour …"); a broken / cut-off / newer link changes nothing and says so in one red line; My routes → ⋮ → Share plan writes the same link (`PlanLinks`) | – | `PlanLinksTest`, `PlanCodeTest` (`formatVersion1IsFrozen`), `CorePlanLinkTest`; emulator `CorePlanLinkE2ETest` |
