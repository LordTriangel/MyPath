# Laptop – morning state 2026-11-20

- Planner candidate for 0.18.2: **363b16645** (planner code = e337818ee), 60 mirrors; check-web, cells, areas green locally,
  web.yml full run green; release_check: routing / sync / 273 shared files equal with the app candidate 230dbc8b (its
  unit.yml run was not found – donald).
- Tonight live before the freeze: place filter + instant chip, honest time left (no number on one long leg, no
  „3 minutes“ / „5 seconds“ guesses), a night on the route line routes nothing, browser icon, Zuletzt + Save dialog on the
  app's slices, kept places on `KeptPlacesSlice`; 7-63-42 builds on TEST.
- **`wip/laptop-2026-11-20-slices` b8d6ea674 – 55 mirrors**, to merge after the release + deploy: My routes' search and
  order (`RouteSort`, `RoutesTab`), the Points drag (`PlanPoints`), the found place's menu (`PinMenuPlace`) run the app's
  code on the page's Wasm; stale `PlanRouteSlice` mirror out. If the page's Wasm fails to load: My routes keeps the stored
  order and search filters nothing, the drag drops nowhere (keyboard still works), the pin menu falls back to the bottom
  card – each reported.
- Why the other 55 stay: 20 screens the app draws with Compose (the web draws its own); 8 MapLibre-Native map code
  (style, layers, pictures); 7 app storage / sessions not compiled for the web; 7 coroutine slices (umbau's later cut);
  3 stand-ins / the string generator; 8 shared but kept on purpose (needed before any Wasm loads, or worded differently);
  2 too big for one night (PlanRunSlice, CellTriggers). The < 15 target needs umbau's coroutine cut after the release.
