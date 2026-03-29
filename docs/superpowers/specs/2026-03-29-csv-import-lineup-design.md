# RaceLineup: Entity-Migration & automatische Zuordnung beim CSV-Import

## Kontext

Der CSV-Import erstellt bereits Teams, Fahrer, Matches, Races und RaceResults. Die Zuordnung zum Lineup fehlt — diese muss bisher manuell im Admin-UI gepflegt werden. Da der Import bereits alle nötigen Daten enthält (Driver, Team, Race), lässt sich das Lineup direkt beim Import ableiten.

Zusätzlich ist das bestehende `MatchdayLineup` (gebunden an Matchday) zu grob für Multi-Leg-Matches: Bei 2 Legs können unterschiedliche Fahrer pro Rennen antreten. Daher wird das Lineup auf Race-Ebene verschoben.

## Entscheidungen

- **Entity-Umbenennung:** `MatchdayLineup` → `RaceLineup`, Tabelle `matchday_lineups` → `race_lineups`
- **FK-Änderung:** `matchday_id` → `race_id` (Matchday über `race.getMatchday()` ableitbar)
- **Unique Constraint:** `(race_id, driver_id)` statt `(matchday_id, driver_id)`
- **Scope (Import):** Nur für Sub-Teams (`parentTeam != null`)
- **Konfliktstrategie (Import):** Ergänzen — bestehende Einträge bleiben, nur neue hinzufügen
- **Import-Ansatz:** Inline in `CsvImportService.executeImport()`
- **UI-Feedback:** `ImportResult` um Lineup-Zähler erweitern
- **Admin-UI:** Lineup-Management pro Race statt pro Matchday

## Teil 1: Entity-Migration (MatchdayLineup → RaceLineup)

### 1.1 Neue Entity `RaceLineup`

**Datei:** `src/main/java/de/ctc/domain/model/RaceLineup.java` (ersetzt `MatchdayLineup.java`)

```java
@Entity
@Table(name = "race_lineups")
public class RaceLineup {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "race_id", nullable = false)
    private Race race;

    @NotNull @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @NotNull @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;
}
```

### 1.2 Repository

**Datei:** `src/main/java/de/ctc/domain/repository/RaceLineupRepository.java` (ersetzt `MatchdayLineupRepository.java`)

Query-Methoden:
- `findByRaceId(UUID raceId)` — alle Lineups eines Races
- `findByRaceIdAndTeamId(UUID raceId, UUID teamId)` — Lineups eines Teams in einem Race
- `findByRaceIdAndDriverId(UUID raceId, UUID driverId)` — einzelner Fahrer-Eintrag (für Duplikatprüfung)

### 1.3 Flyway-Migration

**Datei:** `src/main/resources/db/migration/V1__initial_schema.sql` (direkt ändern, da noch nicht veröffentlicht)

- Tabelle `matchday_lineups` → `race_lineups` umbenennen
- Spalte `matchday_id` → `race_id` ändern
- Foreign Key von `matchdays` auf `races` ändern
- Unique Constraint anpassen: `(race_id, driver_id)`

### 1.4 Betroffene Controller & Services

| Datei | Änderung |
|-------|----------|
| `MatchdayLineupController.java` → `RaceLineupController.java` | Umbau: Lineup pro Race verwalten. URL-Struktur anpassen (z.B. `/admin/races/{raceId}/lineup`). Repository-Calls auf `RaceLineupRepository` umstellen. |
| `MatchdayController.java` | Lineups aus Races des Matchdays ableiten statt direkt abfragen. `MatchdayLineupRepository` → `RaceLineupRepository`. Matchday-Detail zeigt aggregiertes Lineup readonly (alle Fahrer aus allen Races). |
| `MatchController.java` / `match-detail.html` | Match-Detail zeigt aggregiertes Lineup readonly: Alle Fahrer aus allen Races des Matches, gruppiert nach Team. Keine Bearbeitungsmöglichkeit — Verwaltung erfolgt pro Race. |
| `RaceController.java` (`populateDrivers()`) | `findByRaceIdAndTeamId(raceId, teamId)` statt `findByMatchdayIdAndTeamId()`. `MatchdayLineupRepository` → `RaceLineupRepository`. |
| `StandingsServiceTest.java` | Unbenutzten `MatchdayLineupRepository`-Mock entfernen |
| `matchday-lineup.html` → `race-lineup.html` | Template an Race-Kontext anpassen |

## Teil 2: Automatische Lineup-Zuordnung beim CSV-Import

### 2.1 CsvImportService.executeImport()

**Datei:** `src/main/java/de/ctc/dataimport/CsvImportService.java`

Neue Dependency: `RaceLineupRepository` per Constructor Injection.

In der Team-Pair-Schleife, nach dem Erstellen der RaceResult-Einträge (für jeden Fahrer):

```java
if (resolvedTeam.getParentTeam() != null) {
    Optional<RaceLineup> existing =
        raceLineupRepository.findByRaceIdAndDriverId(race.getId(), driver.getId());
    if (existing.isEmpty()) {
        raceLineupRepository.save(new RaceLineup(race, driver, resolvedTeam));
        importResult.incrementLineupCount();
    }
}
```

### 2.2 ImportResult

Neues Feld `lineupCount` (int) mit Getter und `incrementLineupCount()`.

### 2.3 UI Flash-Message

**Datei:** `src/main/java/de/ctc/dataimport/CsvImportController.java`

Erfolgs-Message um Lineup-Info ergänzen, z.B.:
`"Import successful: 6 results, 2 matches, 3 lineup entries created"`

## Nicht betroffen

- `ScorecardParser` / `GoogleSheetsService` — keine Änderungen am Parsing
- `DriverMatchingService` — keine Änderungen
- `SeasonDriver` — bleibt als Fallback für Standalone-Teams

## Verifikation

1. **Unit-Tests:**
   - `RaceLineup`-Entity: Korrekte Felder und Konstruktor
   - `CsvImportServiceTest`: Import mit Sub-Team → `raceLineupRepository.save()` aufgerufen
   - `CsvImportServiceTest`: Import mit Standalone-Team → kein Lineup-Save
   - `CsvImportServiceTest`: Ergänzen-Logik bei bestehendem Eintrag
   - `ImportResult.lineupCount` korrekt gezählt

2. **Integration / E2E:**
   - CSV-Import mit Sub-Team-Daten → RaceLineup-Einträge in DB
   - Flash-Message enthält Lineup-Zähler
   - Race-Detail-View zeigt importierte Lineup-Einträge
   - Admin-Lineup-Verwaltung pro Race funktioniert
   - Multi-Leg-Match: Unterschiedliche Lineups pro Race möglich
