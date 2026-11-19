# OnPath 0.18.2 – A calmer round trip, richer route overviews

<!-- DRAFT (donald 2026-11-19 night) for the weekend release (docs/items/2026-11-19-weekend-release.md). At release:
move to docs/release-notes/v0.18.2.md (that path starts the release), delete v0.18.1.md, bump appVersion. Add what
[umbau] / [laptop] land until „main is clean“ if a user sees it. -->

**Round trips**
- **The search waits in the same window** as map loads and route calculation – with its steps and „Abbrechen“. Touching the map meanwhile no longer stops it or moves your start.
- **Tap a card's text to read all of it:** the card grows in place, „▴ weniger“ folds it back. With large text the card stops at a part of the screen, so the loop stays visible.
- The note about the way out through town is gone – it only took space.

**My routes**
- **Route pictures always show the map again:** a picture made while its map was not ready is drawn again; all pictures are made fresh once after this update.
- **Page dots** under the overview you swipe through.
- **„Mehr“** instead of „Details“: only what the overview does not show – a time bar from start to end with your breaks, uphill and downhill speed, planned against needed time.

**Recording and navigation**
- More robust inside: a failing step while switching the activity during a tour is tried again, and the tour remembers an activity you changed on the way.

**Planner (browser)**
- The same wait window and progress as the app; „Zuhause“ hidden there too.
