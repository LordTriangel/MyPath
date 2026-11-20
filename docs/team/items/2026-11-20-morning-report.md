# Morgenbericht – Nacht vom 19. auf den 20. November

## Kurz
**Der Release-Kandidat steht – für das Handy und für den Planer. Alles ist grün.** Es fehlt nur noch dein Wort.
Veröffentlicht ist noch nichts.

## Was du heute tun musst (in dieser Reihenfolge)
1. **Empfehlung: zuerst in donalds Chat „ja, Emulator leeren“.** Ein echter erster Kartendownload auf einem frisch
   installierten Handy ist noch nicht getestet. Für deine Freunde ist genau das der erste Eindruck. GitHub kann keine
   echten Karten laden, und das Leeren der App auf donalds eigenem Test-Emulator (nicht dein Handy) braucht dein Ja.
   Dauert etwa 10 Minuten.
2. **Dann in donalds Chat „veröffentlichen“.** donald bringt 0.18.2 heraus, danach geht der Planer online. Der Laptop
   wartet darauf ebenfalls auf dein Wort – sag es ihm auch, sobald die App draußen ist.
3. **Papier etwas dunkler (warmes Creme):** liegt als Probe auf deinem Pixel, ist **nicht** im Release. Rein nur auf
   dein Wort, dann noch ein Testlauf von etwa 40 Minuten. Meine Empfehlung: nicht in dieses Release, sondern danach.
4. **Zwei kleine Pull Requests**, die nur du zusammenführen kannst: umbaus Test-Einstellung im Build (drei Zeilen) und
   der ältere von vorgestern. Beide sind für das Release nicht nötig.

## Was in der Nacht fertig wurde
- **App 0.18.2:** voller Testlauf, Rundgang, Unit-Tests und Release-Check grün. Ich habe ihn einmal neu bauen lassen:
  Der erste grüne Kandidat hatte eine wichtige Korrektur beim Kartendownload noch nicht. Danach war main für App-Code
  gesperrt, damit sich nichts mehr unter dem Release bewegt.
- **Härtung:** Seit 0.18.1 kein Fehler gefunden, der eine Spur verliert oder die App abstürzen lässt (zwei Prüfrunden).
  Fünf kleine Korrekturen, jede zuerst mit einem Test. Darunter ein echter, seltener Fehler: Ein Kartengebiet, das der
  Plan inzwischen nicht mehr brauchte, wurde trotzdem heruntergeladen.
- **Planer:** Die Zuletzt-Liste, der Speichern-Dialog und die gemerkten Orte laufen jetzt mit demselben Code wie die App.
  Die Wartezeit-Anzeige ist ehrlich (kein „noch 5 Sekunden“ mehr, kein kurzes „3 Minuten“). Der Fehler München → Hilden
  ist behoben, und der Planer hat ein Browser-Symbol (das App-Symbol). Weitere solche Teile liegen fertig auf einem
  Seitenzweig und kommen nach dem Release.
- **Tests aufgeräumt** (umbau): weniger Testcode, keine Prüfung verloren.
- **Repo aufgeräumt**, wie du es wolltest: Die Arbeitsnotizen der Sitzungen liegen jetzt in einem Ordner
  (`docs/team/`), und es gibt eine kurze Anleitung für Mitwirkende (`docs/README.md`). Erledigte Notizen und alte
  Übergaben sind gelöscht, die Experimente liegen in `experiments/`. Private Dinge sind aus den Notizen entfernt:
  Seriennummern der Handys, deine E-Mail, eine Rückmeldung aus der Familie, Straßen- und Ortsangaben und private
  Bildlinks. Alte Zweige sind gelöscht (mit deinem Ja); jeder lässt sich mit einem Befehl zurückholen.

## Was die Rollen selbst entschieden haben (kurz – du kannst alles umwerfen)
- Versionsnummer **0.18.2**, kein großer neuer Teil.
- Noch mehr Tests streichen (Layout- und Farbprüfungen)? **Nein**, das ist eine Produktfrage – nach dem Release als Liste.
- Planer, Speichern ohne Namen: **Ortsvorschlag wie in der App.** Einen geöffneten Plan verlassen: heute noch das alte
  Verhalten (der Eintrag wird aktualisiert). Die gemeinsame Regel mit der App kommt nach dem Release.
- Übernachtung direkt auf der Linie: **schneidet die Strecke dort**, ohne neu zu rechnen (dein „der Punkt ist ja auf der
  Route“). Der Planer macht das schon, die App folgt nach dem Release.
- Lange Mehrtagestouren schneller machen? **Heute Nacht nur gemessen:** etwa 11–15 Sekunden pro Punkt, nicht
  2 Minuten. Die Vorschläge stehen in der Notiz des Laptops.
- Ein Rundgang-Schritt war einmal rot: als **Ausreißer** beurteilt (allein dreimal grün, der Emulator war in dem Moment
  überlastet).
- donald veröffentlicht **nur auf dein eigenes Wort** in seinem Chat, nicht auf ein weitergegebenes Ja.
- Testorte wie Hilden stehen weiter im Code und in den Tests. Ob dich das bei einem öffentlichen Repo stört,
  entscheidest du.
- Live-Teilen (nur Entwurf): **Akkustand zeigen ja**; der Name wird einmal gefragt und darf leer bleiben.

Alle Antworten einzeln: `docs/team/items/2026-11-20-night-answers.md`.

## Wer macht was
- **donald:** wartet auf „ja, Emulator leeren“ und „veröffentlichen“.
- **laptop:** wartet auf die Veröffentlichung, dann Planer online und den Seitenzweig einspielen.
- **umbau:** Umbau-Abschnitt fertig, wartet. Weiter erst nach dem Release.
- **umbau-hp:** Härtung fertig. Nächste Aufgabe (Routing-Rechner nach Kotlin) erst nach dem Release.
- **tracking:** Entwurf fertig, Code erst nach dem Release.
- **tidy:** fertig.

## Überschneidungen
Keine. Keine zwei Rollen haben in der Nacht an denselben Dateien gearbeitet. Die Sperre für App-Code auf main endet
mit der Veröffentlichung.
