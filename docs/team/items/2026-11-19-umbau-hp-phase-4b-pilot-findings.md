# [umbau-hp] 2026-11-19: phase 4b (screenshot tests) – pilot on main, two findings that change the target

Pilot: the ☰ menu (8 whole-screen pictures → 6 component pictures, every state now in EN and DE; 2 old pictures were
byte-identical duplicates). Helper: `app/src/test/java/com/onpath/app/ui/ShotGallery.kt` (`Gallery { cell(name) { … } }`,
two cells of phone width per picture, fails when cells do not fit). Inventory of all 551 pictures (what each is for,
FRAME / COMPONENT, variants): `docs/design/umbau-phase-4b-inventory.md` (state of 2026-11-19, before the pilot).

What the pilot showed:
1. **"549 → ~200" is not reachable without losing sharpness.** Paparazzi stores every picture scaled to 1000 px on its
   longer side. A gallery bigger than one screen (2400 px) stores its cells smaller than today, so a cut letter can slip
   through. At equal sharpness a picture holds two states of a tall component, four of a small one. Honest target:
   **about 400 pictures**, fewer only where pictures are true duplicates (2 of the first 8 were).
2. **Almost no test time in it.** All screenshot tests together take ~60 s of the ~125 s unit run (0.1 s per picture).
3. **The real gain is re-recording:** ~250 pictures show a component inside the test's `Frame` / `NavFrame` (footer, map
   controls, top bar, sheet). A change there re-records all of them today; as component pictures none.
4. **Limits:** a cell has no screen height – whatever scrolls or fills the height (`PlanPanel` uses `verticalScroll`)
   cannot be a cell as is; those need a cell with the phone's height, and the height is then part of the check. Large
   font needs the device's font scale (non-linear on a phone), so large-font twins stay classes of their own.
5. **Side effect:** `docs/design/website-screens.md` uses snapshot names to find website pictures to retake
   (`tools/dev/pictures_affected.sh`). Every moved scene needs its row updated (done for C03), and
   `docs/core-features/*.md` name scenes too (C16, C18 updated).

Proposal for the rest (I go on with it unless you say otherwise): by family, in this order – menus (remaining 24),
dialogs (21), message boxes / map notices (22), then Navigate cards and the bottom bar (with phone-height cells), the
plan sheet last (88 pictures, needs the height); the 12 arrangement scenes, My routes, Settings and Setup stay whole
screens. Each batch: the commit lists every removed picture and where its point is checked, full gate, Opus review.
