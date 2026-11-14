# MyPath

Mein persönlicher Tourenbegleiter fürs Wandern und Radfahren im Grenzland zwischen Bayern, Tirol und Salzburg.
Offline verlässlich, ohne Konto, ohne Werbung, ohne Community-Feed. Online kommt nur dazu, was eine Tour schöner
macht, zum Beispiel Bilder.

MyPath ist ein Fork von [OnPath](https://github.com/happychriss/OnPath) von happychriss (Apache-2.0) und baut auf
dessen Routenplanung, Navigation und Aufzeichnung auf. Ein Dank an den Original-Autor für die solide Grundlage.

Android 8 oder neuer · Deutsch und Englisch · Apache-2.0 · kein Konto, keine Analyse

## Was MyPath anders macht als das Original

| Bereich | MyPath |
| --- | --- |
| Karten | eigene Offline-Kartenpakete, die auf dem PC gebaut und als ZIP importiert werden; ein Paket deckt Bayern, Tirol und Salzburg ohne Wechsel an der Grenze ab; mehrere Pakete lassen sich speichern und umschalten |
| Kartenserver | keiner; der Download-Server des Originals wird nicht genutzt |
| Browser-Planer | ausgeschaltet (kein Koppeln, kein Sync, keine Plan-Links) |
| Routen | Schalter „Offizielle Routen bevorzugen“ (Aus · Normal · Stark) für markierte Wander-, Rad- und MTB-Netze; Anteil offizieller Routen in den Routendetails |
| Planen | Wegpunkte auf der Karte halten und ziehen, mit gestrichelter Vorschau; Wegtypen und Beläge direkt unter dem Höhenprofil |
| Höhenprofil | Steigung in Prozent beim Abfahren mit dem Finger |
| Zeiten | Fitnesslevel (5 Stufen) direkt in den Zeitdetails umschaltbar |
| POIs und Gipfel | Gipfel und Pässe antippbar; Bilder aus Wikipedia im Detailfenster (online, abschaltbar) |
| Online-Extras | Online-Radkarte (CyclOSM) und Waymarked-Trails-Overlay als bewusste Schalter, standardmäßig aus; die Offline-Karte bleibt immer die Grundkarte |
| Erscheinungsbild | eigenes Logo „Grenzpfad“ und Farbpalette „Alpenrand“ |
| Stabilität | absturzsicherer Paketwechsel mit Wiederherstellung beim Start; Kartenoverlays als Vektorkacheln statt großer GeoJSON-Dateien |

## Karten bauen

Die Kartenpakete entstehen mit der Pipeline des Originals unter Linux bzw. WSL2
([docs/map-pipeline.md](docs/map-pipeline.md), Skript [tools/build_region.sh](tools/build_region.sh)).
Für ein grenzübergreifendes Paket werden die Geofabrik-Auszüge von Bayern und Österreich auf ein gemeinsames Gebiet
zugeschnitten, mit `osmium merge` zusammengeführt und im Kartenformat 2 gebaut:

```sh
osmium extract --strategy=smart -b 10.15,46.95,13.55,48.25 bayern-latest.osm.pbf -o bayern-cut.osm.pbf
osmium extract --strategy=smart -b 10.15,46.95,13.55,48.25 austria-latest.osm.pbf -o austria-cut.osm.pbf
osmium merge bayern-cut.osm.pbf austria-cut.osm.pbf -o alpenrand.osm.pbf
OSM_FILE=alpenrand.osm.pbf bash tools/build_region.sh --format 2 alpenrand 10.15,46.95,13.55,48.25 <osm-url>
```

Die fertige `onpath-data-<region>.zip` wird in der App unter Einstellungen → Offline-Karten importiert.

## Bauen

JDK 17 oder neuer und Android SDK 36:

```sh
./gradlew assembleDebug
```

Die App-ID ist `de.lordtriangel.mypath`; der interne Code-Name `com.onpath.app` bleibt, damit Verbesserungen aus
dem Original weiter übernommen werden können.

## Wie es funktioniert

| Teil | Umsetzung |
| --- | --- |
| Android-App | Kotlin, Jetpack Compose; Navigation, Routenberechnung und Aufzeichnung auf dem Handy |
| Karten | MapLibre Native mit OpenStreetMap-Vektorkarten in PMTiles |
| Routing | eingebettetes [BRouter](https://github.com/abrensch/brouter) mit Profilen für Wandern und verschiedene Räder |
| Dateien | GPX-Import und -Export, automatische GPX-Sicherung, Papierkorb |

Ohne Google Play Services. Was die App ins Netz schickt, steht vollständig unter Einstellungen → Datenschutz.

## Herkunft und Lizenz

MyPath basiert auf [OnPath](https://github.com/happychriss/OnPath) von happychriss. Gegenüber dem Original geänderte
Teile sind in der Tabelle oben und in der Git-Historie dieses Repositorys beschrieben.

Kartendaten © [OpenStreetMap](https://www.openstreetmap.org/copyright)-Mitwirkende (ODbL) · © OpenMapTiles ·
Gelände: Terrarium-Kacheln (Mapzen / AWS Terrain Tiles; SRTM, Copernicus u. a.) · Weltübersicht: Natural Earth
(gemeinfrei) · Routing: [BRouter](https://github.com/abrensch/brouter) (MIT) · Kartendarstellung: MapLibre Native
(BSD) · Bilder: Wikipedia / Wikimedia Commons (jeweilige Lizenz des Bildes, verlinkt im Artikel).

Apache License 2.0, siehe [LICENSE](LICENSE).
