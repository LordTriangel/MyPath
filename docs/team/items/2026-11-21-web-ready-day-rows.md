# web-ready: the multi-day day rows (owner's variant A, 2026-11-21)

- → [laptop] ([umbau] 2026-11-21, **web-ready: day rows**, on main ff4d1174f + f4d8b189d). Exact picture for the planner: `docs/sketches/2026-11-21-day-rows/pics/planer-phone-a-zeit.png` on branch `sketch/umbau-2026-11-21-day-rows` (the owner chose „A“ and „Planer mit Zeit“).
  - **Shared:** `core/…/routing/DayRowText.kt`: `DayRowText.day(n)` (new key `multiday_day_n`: DE „Tag %1$s“, EN „Day %1$s“) and `DayRowText.places(from, to)` (key `days_from_to`; null unless BOTH names are non-blank).
  - **Row:** line 1 bold `day(n)` left, values right before the end sign: `km · ↑ m · time` (the planner KEEPS the time, the app has none); orange when over a limit. Line 2 grey: `places`, only when not null, one line, cut with „…“.
  - **Goes:** the third line (`endText`: „Nacht noch offen“ / „Übernachtung“ / „Hotel: …“, and `over`'s „Über der Grenze: …“ text) – the symbol and the orange say it; keep `over` for the colour.
  - **WebDays.kt:** pass RAW names to `places`: no `days_km_at` („km 362“), no `plan_point_start` / `plan_point_destination` fallback (they count as unknown). A hotel's name is `p.names[index]`. The map's night markers (`ends[].name`) are not part of this wish: leave them.
  - **Open with the owner:** a hotel's name disappears when the day's other end has no place (the both-or-nothing rule he approved). If he changes it, `DayRowText.places` changes for both.
  - App's model of the name order: `MultiDaySlice.daysUi()` (stop name → hotel → nearby → index). Delete the tag when read.
