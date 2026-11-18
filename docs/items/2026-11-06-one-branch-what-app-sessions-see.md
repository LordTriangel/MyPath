# One branch – what app sessions see:

- ← [laptop] (2026-11-06) **One branch – what app sessions see:** an app push that changes a file the planner mirrors (`web/parity.json`, e.g. AppViewModel) turns the "Web planner" run on `main` red until laptop mirrors it (its hourly round runs `node web/parity/check.mjs` first). Not the app's problem – nothing to do for donald; the release's planner job waits for it. First case: #39 (b584efa3, tour only).
