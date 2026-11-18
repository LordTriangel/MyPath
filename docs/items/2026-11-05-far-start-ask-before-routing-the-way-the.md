# [donald] Far start: ask before routing the way there (owner 2026-11-05, corrected)

- **[donald] Far start: ask before routing the way there (owner 2026-11-05, corrected)** – a plan loaded in Meran, "Route
  starten" in Hilden → "Hinführen" routed the lead for a very long time, then "no route". No hard limit (owner: 100 km by
  bike is fine for some). Instead, before the lead is routed, when the way there is long (same idea as the plan's
  `LongRoute` question "Diese Route ist sehr lang", here for the lead – threshold e.g. straight line > 25 km, tune with
  the measurements): a box "Der Start ist 790 km entfernt. Trotzdem einen Weg dorthin berechnen? Das kann lange dauern."
  – Berechnen / Nur aufzeichnen. While it routes: the wait card with Abbrechen; at the time limit the slow-plan question
  "Weiter warten?". Field-bug rule: a reproducing test first (`NavPrompt.FarStart`).
