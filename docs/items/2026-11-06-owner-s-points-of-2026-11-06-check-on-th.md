# [pixel] Owner's points of 2026-11-06 – check on the phone (v0.17.1 + main)

- **[pixel] Owner's points of 2026-11-06 – check on the phone (v0.17.1 + main)**, none seen on a device yet:
  (1) activity switch during a tour (Bike → Hike → "Neue Route": guided as Hike and stays; fix 301db9b5 = the switched
  session keeps the running session's token, Rundgang flow 14); (2) the dot leaves the line beyond 6 m (was 25 m;
  SnapToRoute.MAX_OFF_M) – on a parallel road it must stay on the road; watch whether it now wobbles off the line on the
  route itself (GPS noise > 6 m) and tell the owner; (3) "Weiter geht's. … bis …, etwa …" once after a plan change during a
  tour (VoiceGuide carryOn); (4) a turn is announced only after the one before is ridden (VoiceGuide.nextJunction) –
  replay of the demo ride: "Jetzt rechts halten, danach links" then nothing until the
  left is ridden, then "Jetzt rechts abbiegen"; (5) Sperren | Plan after unlocking; (6) Tour beenden on one screen
  (numbers in one line, Tour speichern, Weiterfahren | Löschen, bin note) – no scrolling on the Pixel, ✕ not hidden under the
  volume bar; (7) "Plan auch speichern" ON for a plan never saved; the plan is in Meine Touren after the tour; (8) search in
  Meine Touren; (9) after v0.17.2: shelters (Unterstände) stay while zooming from z12.
