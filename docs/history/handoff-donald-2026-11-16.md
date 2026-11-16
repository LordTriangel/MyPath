# Handoff donald, 2026-11-16 (evening → night)

Everything below is on `main`. Nothing is released. The owner's Pixel (44080DLJH000E5) has a debug build of main at
43091d6b (installed 23:38, `adb install -r`, plans + tours kept); the symmetric hold rule (f12e8d83) is in it, the
kept-places sync and the POI E2E test are not (both after). Phone unplugged at 23:45.

## FIRST when the owner writes (his word 23:45) – top of backlog „Next“
1. **Hold rule past the goal** – „I want to continue my way from point 15 to the new target, and it still asks me“.
   Screen `docs/history/img/2026-11-16-hold-past-goal.jpg`; every hold past the end asked (insert ≈ 2 × append). Cause
   and a direction-based idea are in the backlog item. Talk + sketch before code.
2. **POI symbols and names** – A no names / B overlap / C first version (https://claude.ai/artifact/8ziJojuGmReaJh3PqfRQV6).
3. **Check with him: does a tap on a restaurant symbol show its details now?** The 23:35 bug (tap = nothing, hold = no
   point) was fixed in 43091d6b on the diagnosis „the runtime icon-image read found no POI layer“ – but the night's E2E
   test could NOT reproduce that on the emulator (the old code passed there). Other possible cause: since 2797bb7f a tap
   on a POI's *name* beside its icon no longer hits (the `-name` layers are still separate). Read `files/events.log`
   (run-as) for his taps before guessing again.

## Done today (owner's words in quotes)
- Old „For the roles“ lines for donald cleared (all in 0.17.10).
- **Hold rule (F-PLAN-15) merged** after his „das mit dem Einfügen und Stopp funktioniert ganz gut“; then the ask band
  **the same both ways** (his yes; also the Färberweg junction asks now, his yes): silent only when one detour is at most
  half of the other.
- **Bus-stop name (Hilden „Dorothea-Erxleben-Straße“)**: a hold on a name without its symbol is no POI (hold = map
  hold). The look of names is NOT changed (his A/B/C choice open; branch `worktree-agent-abc26b327212a7930` has A and C).
- **Ø speed for done tours** (sketch approved, order km · ↑ m · km/h): tour review top row Strecke · Aufstieg · Ø Tempo,
  lower row Gesamtzeit · In Bewegung · Pausen; list „Gemacht“ km · ↑ m · km/h, time in the activity line. Tours before
  2026-11-01 show no average in the list (no stored times).
- **Kept places sync** (format + phone side, `docs/design/web-sync.md` §5.4; laptop line in „For the roles“).
- **POI tap E2E** `CorePoiTapE2ETest` + Rundgang flow 17 + core feature C19 (his question „would a release test have
  caught it?“ – no, before this).

## Notes
- Fragenregister: donald-20261116-1 … -7.
- Pages: hold cases https://claude.ai/artifact/Ve3ZJFa1U7Z2evSR5feye2 · names round 1
  https://claude.ai/artifact/YCL4Mkh1NkyBSVqJNdhwSp · round 2 https://claude.ai/artifact/8ziJojuGmReaJh3PqfRQV6 · Ø
  sketch https://claude.ai/artifact/LgvfZnNKWjMp3k6vYqD9Js.
- The E2E agent saw MapLibre read a PMTiles file rewritten at the same path wrongly („unknown pbf field type“) in tests;
  possibly relevant if `MapCells` rewrites `pois.pmtiles` in place while the map shows – unchecked.
- Lesson: a change to how the map finds POIs went to his phone without a tap on a symbol first – now C19 / E2E.
