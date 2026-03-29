# Konfigurierbares Scoring & Regular-Season Legs — Design Spec

## Kontext

Bei der Durchsicht älterer CTC-Saisons wurde festgestellt, dass sich Punktesysteme und Matchday-Formate zwischen Saisons unterscheiden. Historisch hatte ein Matchday-Duell zwischen zwei Teams ein Hin- und Rückspiel (2 Legs) mit unterschiedlichen Autos und Strecken pro Leg, wobei die Ergebnisse zusammengeführt wurden. Außerdem variierten die Punktvergabe-Regeln (Renn-, Quali-, FL- und Match-Punkte) je Saison.

Aktuell ist das Scoring hardcoded (`ScoringService`: 20-17-14-12-10-8-7-6-5-4-3-2 / Quali 3-2-1 / FL 2 / Match 3-1-0) und Legs existieren nur für Playoffs (`PlayoffMatchup` → Races). Diese Spec beschreibt die Erweiterung um konfigurierbares Scoring als Stammdaten und ein Legs-Konzept für die Regular Season.

---

## Datenmodell

### Neue Entities

#### RaceScoring (Stammdaten — Rennpunkte-Regelwerk)

```
Entity: race_scorings
  id               UUID (PK)
  name             String (unique, not null)
  race_points      String (not null)     — CSV z.B. "20,17,14,12,10,8,7,6,5,4,3,2"
  quali_points     String (nullable)     — CSV z.B. "3,2,1" (null/leer = deaktiviert)
  fastest_lap_points int (default 0)     — 0 = deaktiviert
```

- `race_points` und `quali_points` sind kommagetrennte Integer-Listen
- Die Positionsnummer ergibt sich aus dem Index (1-basiert)
- Positionen jenseits der Liste erhalten 0 Punkte
- **Validierung:** Werte müssen monoton fallend sein — eine nachfolgende Position darf keinen höheren Wert als die vorherige haben. Gleiche Punktzahl ist erlaubt (z.B. "20,17,14,14,10" ist gültig, "20,17,14,15,10" ist ungültig). Gilt für `race_points` und `quali_points`.
- UI stellt die Werte als einzelne Positionszeilen dar, konvertiert beim Speichern in CSV-String
- Hilfsmethoden auf der Entity: `int[] getRacePointsArray()`, `int[] getQualiPointsArray()`

#### MatchScoring (Stammdaten — Match-Punkte-Regelwerk)

```
Entity: match_scorings
  id             UUID (PK)
  name           String (unique, not null)
  points_win     int (not null)
  points_draw    int (not null)
  points_loss    int (not null)
```

#### Match (Regular-Season-Begegnung)

```
Entity: matches
  id             UUID (PK)
  matchday_id    UUID (FK → matchdays, not null)
  home_team_id   UUID (FK → teams, not null)
  away_team_id   UUID (FK → teams, nullable)   — null bei Bye
  home_score     Integer (nullable)             — aggregierte Punkte über alle Legs
  away_score     Integer (nullable)
  bye            boolean (default false)
```

- Beziehung: `Match 1:N Race` (Legs)
- Analog zu `PlayoffMatchup`, aber ohne Bracket-Felder (winner, nextMatchup, bracketPosition)
- `homeScore`/`awayScore` = Summe der Fahrerpunkte über alle Legs des Matches

### Änderungen an bestehenden Entities

#### Season (erweitert)

```
+ race_scoring_id   UUID (FK → race_scorings, not null)
+ match_scoring_id  UUID (FK → match_scorings, not null)
+ legs              int (default 1)    — Anzahl Legs pro Match in Regular Season
```

- Beide Scoring-Referenzen sind Pflichtfelder (NOT NULL)
- `legs` ist global für die Saison (alle Matchdays gleich)
- Default-Scoring-Presets werden **nicht** per Flyway angelegt, sondern:
  - Im `DevDataSeeder` (Demo-Profil `dev,demo`): "CTC Standard" RaceScoring (20,17,14,12,10,8,7,6,5,4,3,2 / Quali 3,2,1 / FL 2) und "Standard 3-1-0" MatchScoring (3/1/0) mit den aktuell hardcoded Werten erstellen und den Demo-Saisons zuweisen
  - In Test-Fixtures (`@BeforeEach`): Analoge Scoring-Presets für Unit- und Integrationstests

#### Race (wird zum reinen "Leg")

```
- homeTeam, awayTeam, homeScore, awayScore, bye   → entfernt (wandern auf Match)
+ match_id          UUID (FK → matches, nullable)  → für Regular-Season-Legs
  playoff_matchup_id bleibt                        → für Playoff-Legs
  track, car, dateTime, results, attachments       → bleiben
```

- **Invariante:** Ein Race gehört entweder zu `match` (Regular Season) ODER `playoffMatchup` (Playoffs) — nie zu beiden, nie zu keinem

#### PlayoffMatchup (Konsistenz-Anpassung)

```
+ home_score    Integer (nullable)    — aggregierte Punkte Team 1
+ away_score    Integer (nullable)    — aggregierte Punkte Team 2
```

- Bisher lagen Scores auf den einzelnen Races; jetzt konsistent auf dem Matchup
- `team1`/`team2`, `winner`, `nextMatchup`, `bracketPosition` bleiben unverändert

### Flyway-Migration

Alle Änderungen werden in die bestehende `V1__initial_schema.sql` integriert, da V1 noch nicht veröffentlicht wurde:
- Neue Tabellen: `race_scorings`, `match_scorings`, `matches`
- Season-Tabelle: Neue FK-Spalten `race_scoring_id`, `match_scoring_id`, `legs`
- Races-Tabelle: Spalten `home_team_id`, `away_team_id`, `home_score`, `away_score`, `bye` entfernen; `match_id` hinzufügen
- Playoff-Matchups-Tabelle: `home_score`, `away_score` hinzufügen

---

## Service-Logik

### ScoringService (Refactoring)

Aktuell: Hardcoded Konstanten (`RACE_POINTS`, `QUALI_POINTS`, `FASTEST_LAP_POINTS`).

Neu: Methode erhält `RaceScoring` als Parameter:

```java
void calculatePoints(RaceResult result, RaceScoring scoring)
```

- Parst `scoring.getRacePointsArray()` für Rennpunkte
- Parst `scoring.getQualiPointsArray()` für Quali-Punkte (leeres Array → 0)
- Nutzt `scoring.getFastestLapPoints()` statt Konstante
- Das RaceScoring wird über die Season aufgelöst:
  - Regular Season: `race.getMatch().getMatchday().getSeason().getRaceScoring()`
  - Playoffs: `race.getPlayoffMatchup().getRound().getPlayoff().getSeason().getRaceScoring()`
- `calculateTeamTotal()` bleibt unverändert (summiert `pointsTotal`)

### StandingsService (Refactoring)

Aktuell: Iteriert über Races, hardcoded 3-1-0.

Neu:
- Liest `season.getMatchScoring()` für Win/Draw/Loss-Punkte
- Iteriert über **Matches** statt Races
- Pro Match: Liest `homeScore`/`awayScore` (bereits aggregiert)
- Vergleicht und vergibt Match-Punkte aus `matchScoring.getPointsWin/Draw/Loss()`
- Sortierung bleibt: Match-Punkte → Punkt-Differenz → Punkte erzielt

### PlayoffService (Anpassung)

- Winner-Determination nutzt weiterhin Rohpunkte-Aggregation (kein MatchScoring)
- `homeScore`/`awayScore` werden auf `PlayoffMatchup` geschrieben (statt auf Race)
- Scoring-Berechnung nutzt `RaceScoring` aus der Season
- Bestehende Tie-Handling-Logik (manueller Winner) bleibt

### Score-Aggregation (Match & PlayoffMatchup)

Beim Speichern von Race-Results:
1. `ScoringService.calculatePoints()` mit `season.getRaceScoring()`
2. Team-Totals für dieses Leg berechnen
3. Übergeordnetes Match/PlayoffMatchup aktualisieren: `homeScore`/`awayScore` = Summe über alle Legs

### Betroffene Stellen (Anpassungsbedarf)

- **SwissPairingService:** Erstellt aktuell Races direkt → muss Matches erstellen und Races darunter hängen
- **CSV-Import (CsvImportService/ScorecardImportService):** Erstellt Races mit Team-Referenzen → muss Matches als Zwischenebene nutzen
- **DriverRankingService:** Summiert nur `pointsTotal` aus RaceResults → kein Änderungsbedarf
- **SiteGeneratorService:** Liest Standings und Matchday-Daten → muss Match-Ebene berücksichtigen

---

## Admin-UI

### Neue Stammdaten-Seiten

#### RaceScoring CRUD (`/admin/race-scorings`)

- **Liste:** Tabelle mit Name, Rennpunkte (gekürzt), Quali, FL-Punkte
- **Formular:** Zweispaltiges Layout
  - Links: Rennpunkte als einzelne Positionszeilen (Position + Punktefeld) mit "Hinzufügen"/"Entfernen"-Buttons
  - Rechts oben: Qualifying-Punkte (gleiche Struktur)
  - Rechts unten: Schnellste-Runde-Punkte (Einzelfeld, 0 = deaktiviert)
- Name-Feld oben (volle Breite)
- UI konvertiert Positionszeilen ↔ CSV-String beim Laden/Speichern (JavaScript)

#### MatchScoring CRUD (`/admin/match-scorings`)

- **Liste:** Tabelle mit Name, Sieg/Unentschieden/Niederlage-Punkte (farbcodiert)
- **Formular:** Name + 3 Zahlenfelder nebeneinander (Sieg grün, Unentschieden grau, Niederlage rot)

#### Navigation

Neue Sidebar-Einträge unter einer Gruppe "Stammdaten" oder "Regelwerk":
- Race-Scorings
- Match-Scorings

### Season-Formular (erweitert)

Neuer "Regelwerk"-Block (visuell abgegrenzt):
- **Race-Scoring** Dropdown (Pflicht) — mit Vorschau der Werte unterhalb
- **Match-Scoring** Dropdown (Pflicht) — mit Vorschau der Werte unterhalb
- **Legs pro Match** Zahlenfeld (min 1, default 1)

### Matchday-Detail (angepasst)

Zeigt **Matches** statt direkt Races:

**Bei 1 Leg (Standard):**
- Kompakte Match-Karten: Home Team — Score : Score — Away Team
- Direktlinks zu Race-Details/Ergebnissen

**Bei Multi-Leg (> 1):**
- Match-Header mit aggregiertem Gesamtscore
- Darunter Leg-Zeilen mit: Leg-Nummer, Track, Car, Einzel-Score, Ergebnis-Link
- Badge zeigt Fortschritt (z.B. "1/2 Legs", "2/2 Legs")
- "Leg anlegen"-Button für fehlende Legs

### Match-Verwaltung

- **Neues Match anlegen:** Button auf Matchday-Detail → Home/Away Team Auswahl (oder Bye-Toggle)
- **Legs anlegen:** Über Match-Detail oder direkt aus der Matchday-Ansicht
- Race-Formular zeigt Match-Referenz (readonly Teams), Track, Car, DateTime

### Race-Formular (vereinfacht)

- Race verliert Home/Away Team Felder — diese kommen vom übergeordneten Match
- Match-Referenz wird readonly angezeigt ("TNR vs P1R")
- Track, Car, DateTime, Results bleiben wie bisher

---

## Verifizierung

1. **Unit Tests:**
   - `ScoringServiceTest` — verschiedene RaceScoring-Konfigurationen testen
   - `StandingsServiceTest` — verschiedene MatchScoring-Konfigurationen, Multi-Leg-Aggregation
   - `PlayoffServiceTest` — Score-Aggregation auf PlayoffMatchup statt Race

2. **Integration Tests:**
   - RaceScoring/MatchScoring CRUD Controller
   - Season-Formular mit Scoring-Referenzen
   - Match CRUD mit Leg-Verwaltung
   - Score-Berechnung End-to-End (Race-Result speichern → Match-Score aggregiert → Standings korrekt)

3. **E2E Tests (Playwright):**
   - Scoring-Preset anlegen → Season mit Scoring erstellen → Matchday + Match + Ergebnis eingeben → Tabelle prüfen
   - Multi-Leg-Workflow: Match mit 2 Legs anlegen, beide Legs mit Ergebnissen befüllen, aggregierte Scores prüfen
   - Race-Scoring Formular: Positionen hinzufügen/entfernen, Validierung monoton fallender Werte prüfen

4. **Manueller Test:**
   - Dev-Profil mit Demo-Daten starten
   - Scoring-Presets anlegen
   - Season mit Scoring + 2 Legs erstellen
   - Matchday mit Matches + Legs anlegen und Ergebnisse eingeben
   - Tabelle prüfen: korrekte Match-Punkte und Aggregation
