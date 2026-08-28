# ViewTune — Nischenbeleg und Positionierung

Stand 28.08.2026. Alle Zahlen an der Modrinth-API erhoben, nicht aus Erzählungen.

## Der Orphan
`seemore` (SeeMore, froobynooby): **56.744 Downloads**, Loader `paper`/`folia`/`purpur` — also ein
reines Plugin, exakt unser Stack. Letzte Version **1.0.2 vom 14.10.2023**, das sind **34 Monate**
Stille. Von den 56,7k entfallen 55,5k auf diese eine Datei.

## Pflichtfilter 1: aktiver Nachfolger?
Modrinth-Suche „view distance render distance“, Facette `project_type:plugin`:

| Downloads | zuletzt aktualisiert | Projekt |
|---|---|---|
| 56.737 | vor 35 Monaten | seemore (der Orphan) |
| 1.742 | vor 31 Monaten | extended-view-distance |
| 36 | vor 7 Monaten | builder-mode (andere Aufgabe) |
| 29 | vor 5 Monaten | syenergy (andere Aufgabe) |

Es gibt **keinen aktiven Anbieter**. Der Zweitplatzierte ist selbst seit 31 Monaten still und liegt
bei 3 % der Downloads des Orphans.

## Pflichtfilter 2: publiziert das Projekt woanders weiter?
Quelle laut Modrinth: `github.com/froobynooby/SeeMore`. Kein Hangar-Projekt unter dem Namen. Die
letzte veröffentlichte Datei bleibt die vom 14.10.2023.

## Ehrliche Einschränkung
Das alte Jar wurde **heruntergeladen und auf beiden Testservern gestartet**: SeeMore 1.0.2 lädt auf
Paper 1.21.11 **und** auf Paper 26.2 fehlerfrei. Es benutzt nur langjährig stabile Bukkit-Aufrufe.
Die Positionierung kann also **nicht** „das Alte ist kaputt“ lauten — dieselbe Lage wie bei
ShoulderRide. Unser Vorsprung ist Funktionsumfang, Präsenz und der Update-Takt.

## Unterscheidungsmerkmale (alles nicht im Original)
1. **Simulationsdistanz pro Spieler.** SeeMore regelt nur die Sichtdistanz. Die Simulationsdistanz ist
   die teure: Mobs, Redstone, Pflanzenwachstum, Trichter. `Player#setSimulationDistance` ist gegen das
   echte Paper-API-Jar verifiziert.
2. **TPS-Regler.** Senkt die Decke für alle um 1 Chunk pro Intervall unter der Marke, hebt sie über der
   Erholungsmarke wieder — mit Totband und Boden. Das ist die eigentliche Neuerung: in dieser Nische
   bietet das niemand, obwohl es der Grund ist, warum Admins überhaupt an der Sichtweite drehen.
3. **Rang-Obergrenzen** `viewtune.view.<n>` / `viewtune.sim.<n>` — direkter Spendenanreiz für
   Serverbetreiber, funktioniert mit jedem Rechte-Plugin ohne harte Abhängigkeit.
4. **Pro Welt** und **Skalierung nach Spielerzahl.**
5. **`/viewtune savings`** — beziffert die gesparten Chunks. Verkauft das Plugin selbst.

## Warum diese Nische taugt
Serverleistung ist die Kategorie, in der Betreiber wirklich handeln — anders als bei Spielereien.
Und die Wirkung ist unmittelbar messbar, was Bewertungen erzeugt.

## Verifiziert vor dem Bau
Gegen `paper-api-1.21.4` per `javap`: `Player#getClientViewDistance/setViewDistance/`
`getSimulationDistance/setSimulationDistance`, `World`-Pendants, `Server#getTPS()` sowie
`com.destroystokyo.paper.event.player.PlayerClientOptionsChangeEvent#hasViewDistanceChanged` —
alle vorhanden.

## Testlage
`tools/test-plugin.sh`: PASS auf Paper 1.21.11 und 26.2.
`tools/prober` mit `probe viewtune`: **19/19 Zusicherungen grün auf beiden Versionen**, dabei wird
`dev.viewtune.Tuner` über den Plugin-Classloader aus dem **ausgelieferten Jar** geladen — die
Zusicherungen können also nicht von einer nachgezogenen Kopie abweichen (das war die Schwäche des
bisherigen Prober-Musters).

## Beim Bau gefundener und behobener Defekt
Die Absenk-Verzögerung gegen Schieberegler-Zappeln galt zunächst auch beim Join. Damit lief jeder
Spieler die ersten 10 Sekunden auf voller Serverdistanz — exakt die Chunk-Last, die das Plugin
einsparen soll. Join und Weltwechsel greifen jetzt sofort durch.
