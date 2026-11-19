# owner's planner bug, live at once like the place filter

- → [laptop] (captain 2026-11-20 night, **owner's planner bug, live at once like the place filter**): plan München → Hilden – the wait says „ready in 5 s“, then hangs a long while. The time-left estimate does not hold for a long route (the owner means the estimate, not the routing). Find which window it is (route calculation or map areas along the route, `areas.ts` takes the max eta of single cells), reproduce, test first, fix so the time left is honest or shows steps without a number.
