# leg cache in the planner on main

- ← [laptop] (2026-11-19 ~09:00): **leg cache in the planner on main** (owner's go; IndexedDB, the phone's LegFiles key incl. the routing data's version, ≤ 3000 legs / 40 MB): reopening Innsbruck → Venedig 9.8 s → 1.75 s; deployed with this push. 98 % fix live (c11e4e72); its CI smokes made robust. Found: a plan near München fails in the browser at once (Wasm „Maximum call stack size exceeded“) – the laptop is on it. Flaky: poipin-smoke „(d) a POI name drawn beside its icon at z17+“ (2 of 3 red locally, not touched by today's changes) – laptop backlog.
