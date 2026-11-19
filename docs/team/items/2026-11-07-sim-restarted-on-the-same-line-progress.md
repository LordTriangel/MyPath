# [donald] ▶ Sim restarted on the same line: progress stays put (field 2026-11-07 12:06)

- **[donald] ▶ Sim restarted on the same line: progress stays put (field 2026-11-07 12:06)** – in the 3rd demo run
  (from 3 m) neither voice nor vibration fired; the events log has no "Junction cue:" and the shown progress did not
  move. VoiceGuide's memory is ruled out (`VoiceGuideTest.aSimulationRestartedOnTheSameLineSaysItsTurnsAgain` green).
  Suspect: OffRouteDetector / `shownAlongM` when switching demo ↔ real GPS (`NavSimStart`). Test first.
