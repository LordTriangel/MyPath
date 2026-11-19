# OnPath 0.18.1 – Round trips for everyone, honest progress

<!-- DRAFT (donald 2026-11-19). At release: move to docs/release-notes/v0.18.1.md (that path starts the release),
delete v0.18.0.md, bump appVersion. Fill in the progress section from the real build. -->

**Round trips**
- **„Rundtour von hier“** for everyone: hold the map (or tap your first point) and pick a round trip from suggestions in one row of cards – real, signposted loops near you first.

**Progress you can trust**
- **The loading window never stands at 98 % any more:** one bar from 0 to 100 over all steps, the steps with ticks (prepared · loading · adding to the map), and how long it still takes.
- **Route calculation** shows its legs as steps in the same window.
- **Loads in the background** (maps along a long tour, offline areas) show one small ring with one percentage for the whole job – tap it for the details.

**Planning**
- **Undo / Redo also in the full points list** – no need to pull the sheet down first.
- Multi-day tours: **„Tag 2 starten“** for every activity (was „fahren“, also on hikes).
- Fixed: dropping a row of the points list on itself could empty the plan.
- „Zuhause“ is hidden in the search for now; your saved home place stays.

**Inside**
- The app's inside is now split into clear parts (the start of the big rebuild) – nothing you see changed; many new tests.
- Map areas a tour reaches load without a long wait.
