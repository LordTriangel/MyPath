# Adding a point to a long multi-day plan – where the time goes (laptop, measured 2026-11-20 night)

Owner: München – Venedig, „jeder neue Punkt kostet etwa 2 Minuten“. Measured on local data (6-day tour München → Brenner →
Venedig, 3 points, legs 214 + 319 km Bike / 175 + 269 km Hike T2, one click 2 km beside the middle of the long leg; 3 runs
each; the machine was heavily loaded, load 16–38). Scripts: `web/engine/src/test/kotlin/com/onpath/web/AddPointPerfTest.kt`
(`ONPATH_PERF=1`), `web/app/test/addpoint-perf.mjs` (branch of the measurement, not on main yet).

## Result
- **One add here: 11–15 s in the browser, 4–8 s on the JVM.** The 2 minutes did not reproduce. ~95 % is BRouter routing
  the two new 130–165 km halves; everything else together < 0.5 s.
- **Plus 1.5 s fixed on every add:** the click's „insert or append“ measure on real paths (`holdOnMap`) hits its 1.5 s cap
  on long legs and then decides by straight lines anyway – pure waste there.
- **Routing time grows much faster than length** (JVM, one piece of the same line): 25 km 0.05–0.1 s · 50 km 0.15–0.3 s ·
  100 km 0.35–1.5 s · 200 km 4.3–4.9 s (Bike ×14 from 100 to 200 km). So a few long legs are the expensive shape.
- The browser is only ~1.4–2× slower than the JVM here. A slower computer or phone (×5–8) explains 1–2 minutes; so would
  legs routed again that should come from the leg store. **Owner: one diagnostics trace of a slow add on your machine
  would settle which it is** (Hilfe & Quellen → Diagnose kopieren, or tell me the device).

## Detour stitch – measured
Routing only anchor → new point → anchor (anchors on the old line) instead of the two full halves:
| new point off the line | full 2 legs | stitch | stitch differs from full (anchors 10 km) |
|---|---|---|---|
| 0.5 km | 4–12 s | 0.02–0.3 s | not at all (6/6) |
| 2 km | 3.4–12 s | 0.02–0.3 s | not at all (6/6) |
| 5 km | 4–12 s | 0.05–0.7 s | sometimes another valley: up to +9 km / +1 200 m (Hike) |
| 10 km | 4–16 s | 0.06–1.2 s | often: up to +34 km |
Anchors 3–5 km gave wrong detours on hikes (+4–7 km, up to +744 m) – 10 km is needed.

## Recommendation (to decide by the owner)
1. **Now, planner only, no format change:** skip the 1.5 s click measure on legs over ~30 km (decide by straight lines at
   once – it does that anyway after 1.5 s). –1.5 s on every add.
2. **Stitch only within ~2 km of the line and on legs over ~60 km, anchors 10 km before / after** – there it is identical
   to full routing in every measured case and 20–200× faster. As a field of the point (it goes when the point goes – the
   owner's worry), app + planner after the release.
3. **Beyond 2 km: route in full**, but think about cutting a long half at points of its old line every 50–80 km (the cost
   curve above): a 160 km half from ~5 s to ~2× 0.3–1.5 s. Needs the same „does the plan look the same everywhere“ care
   as the stitch – after the release, designed with the stitch.
4. Smaller: every plan reply re-sends the whole line (700–900 KB JSON) – fine for now.

## Owner's decisions (2026-11-20 morning)
- Step 1 (skip the click measure on legs > 30 km): **yes** – laptop builds it on `wip/laptop-2026-11-20-slices`, out after the release.
- Detour stitching: **yes, after the release** – ≤ 2 km from the line, legs > 60 km, anchors 10 km before / after; the
  detour is a field of the point (goes when the point goes); „Ganz neu berechnen“ in the plan's menu; a picture of the
  button to the owner before it is built. Format change → app + planner together ([umbau] / [mehrtage] / laptop).
- Zuletzt: leaving a plan opened from Zuletzt behaves **like the app** (new draft, the old one to the bin) – planner
  follows on the wip branch.
