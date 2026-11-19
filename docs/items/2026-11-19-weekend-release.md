# A clean release for the weekend (owner 2026-11-19 night)

Owner: „Mit 0, 1 und 2 haben wir unseren Code schon mal aufgeräumt … Was ist los mit dem Browserplaner? Das kannst du
erstmal klären. Die Tests aufräumen sollten wir auch machen. Lass uns das jetzt abschließen, sodass wir davon ein
sauberes Release fertig haben, das ich ins Wochenende nehmen kann und das meine Freunde auch runterladen können.“

Goal: by **Friday evening, 2026-11-20**, a published app release from a quiet, fully tested main. Friends install it –
so first-start, first map download and a first tour matter most.

## [umbau]
1. **Tag `umbau-phase-2`** after a full E2E on main.
2. **Test clean-up now** (the open audit work: 25 DROP files, 85 CONVERT files, the pins step 21 made needless) – one
   helper per group, fresh review per group, no check lost. Numbers before / after under "For the captain".
3. **The planner question, answered in numbers under "For the captain":** `web/parity.json` lists 66 hand mirrors
   (52 before the rebuild; target < 15) and `state/web-ready.txt` names 2 of 28 slices. What is missing for the planner
   to run the slices, how big is it, when? **Not before the release** – only the answer and a plan.
4. **No phase 3 / phase 4 code on main until the release is out.** Design work is fine.
5. Write „main is clean for the release“ under "For the roles" for [donald] when 1 and 2 are on main and green.

## [laptop]
- **„Region package 7-63-42 failed on the server: Build step 'shade' failed“** (15× in the owner's Pixel log): find and
  fix before the release – a friend in that area must get his map. Check for other packages with the same failure.
- Planner: keep it in step with what the release contains.

## [donald]
- When [umbau] writes „clean“: the release round (version, notes for everything since 0.18.1, full E2E + Rundgang,
  first-start and first-map-download once on a fresh emulator). Then ask the owner for his word in your chat.
- Until then: nothing new on main except fixes.

## [umbau-hp]
- Waits (routing port after the release, when [umbau] names P1 / H1).

## Later, not for this release
- Repo tidy-up (order of 2026-11-19 night) – after the release, so nothing moves under the release round.
- Phase 3 (small), phase 4, routing port 5b.
