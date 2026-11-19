# [laptop] POI click on a slow phone after a pan (2026-11-17):

- **[laptop] POI click on a slow phone after a pan (2026-11-17):** MapLibre re-places symbols in 2 ms steps per frame; on a slow device this can exceed the planner's 5 s wait, then a click on a POI reads the old placement and adds a point instead. Real fix: a POI lookup independent of the placement (the source's features near the press, as the app's PoiTap from the POI tiles) – to decide.
