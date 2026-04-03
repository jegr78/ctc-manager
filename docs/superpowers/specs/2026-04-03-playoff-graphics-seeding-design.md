# Playoff Graphics & Seeding

**Datum:** 2026-04-03

## Problem

Die vier Race-Grafik-Services (Lineup, Results, Settings, Overlay) funktionieren nur fuer Regular-Season-Rennen:

1. **Hard-Fail:** Alle Services pruefen `if (match == null) throw IllegalStateException` — Playoff-Rennen (die `playoffMatchup` statt `match` haben) schlagen komplett fehl.
2. **Falsches Label:** `seasonName` zeigt immer den Season-Namen. Bei Playoff-Rennen soll stattdessen der Playoff-Name angezeigt werden.
3. **Falsche Position:** Die Standings-Position aus der Regular-Season-Tabelle wird angezeigt. Bei Playoff-Rennen soll die Seed-Nummer des Teams verwendet werden.
4. **Kein Seed-Datenmodell:** Es gibt aktuell kein explizites Seed-Feld pro Team in einem Playoff.

## Loesung

### 1. Neue Entity: PlayoffSeed

Tabelle `playoff_seeds` in `V1__initial_schema.sql` (keine Migration noetig, V1 ist nicht veroeffentlicht):

```sql
CREATE TABLE playoff_seeds (
    id CHAR(36) NOT NULL,
    playoff_id CHAR(36) NOT NULL,
    team_id CHAR(36) NOT NULL,
    seed INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_playoff_team (playoff_id, team_id),
    UNIQUE KEY uk_playoff_seed (playoff_id, seed),
    CONSTRAINT fk_playoff_seed_playoff FOREIGN KEY (playoff_id) REFERENCES playoffs (id),
    CONSTRAINT fk_playoff_seed_team FOREIGN KEY (team_id) REFERENCES teams (id)
);
```

Entity `PlayoffSeed` in `org.ctc.domain.model`:
- Felder: `id` (UUID), `playoff` (ManyToOne → Playoff), `team` (ManyToOne → Team), `seed` (int)
- Standard BaseEntity mit createdAt/updatedAt

Repository `PlayoffSeedRepository`:
- `findByPlayoffId(UUID playoffId)` — alle Seeds eines Playoffs
- `findByPlayoffIdAndTeamId(UUID playoffId, UUID teamId)` — Seed eines bestimmten Teams
- `deleteByPlayoffId(UUID playoffId)` — fuer Neuzuweisung

### 2. Seeding-UI Erweiterung

Die bestehende Seeding-Seite (`/admin/playoffs/{id}/seeding`) wird erweitert:

- **Nummernfeld pro Team:** Neben jedem Team in der Team-Liste ein Input-Feld fuer die Seed-Nummer (Integer).
- **Persistierung:** Beim Speichern der Matchup-Zuordnung werden die Seed-Nummern als `PlayoffSeed`-Eintraege mit persistiert.
- **Auto-Seed-Button:** Neuer Button "Auto-Seed by Number" verteilt Teams automatisch nach Standard-Bracket-Logik ins Bracket:
  - Bei 8 Teams: Seed 1 vs 8, Seed 4 vs 5, Seed 3 vs 6, Seed 2 vs 7
  - Bei 4 Teams: Seed 1 vs 4, Seed 2 vs 3
  - Bei 2 Teams: Seed 1 vs 2
  - Die manuelle Zuordnung bleibt danach weiterhin editierbar.
- **Validierung:** Seed-Nummern muessen unique pro Playoff sein, keine Luecken (1 bis N).

### 3. Grafik-Services: Playoff-Kompatibilitaet

Aenderungen in allen vier Services (LineupGraphicService, ResultsGraphicService, SettingsGraphicService, OverlayGraphicService):

**A) Match-Check ersetzen:**
- Statt `if (match == null) throw new IllegalStateException("Race has no match")` pruefen ob `race.getHomeTeam() != null && race.getAwayTeam() != null`.
- Funktioniert fuer beide Pfade (Match und PlayoffMatchup).

**B) Labels kontextabhaengig setzen:**
- `seasonName`: Bei `race.getPlayoffMatchup() != null` den Playoff-Namen verwenden (`race.getPlayoffMatchup().getRound().getPlayoff().getName()`), sonst Season-Name. Betrifft LineupGraphicService, ResultsGraphicService, SettingsGraphicService. OverlayGraphicService setzt kein `seasonName`.
- `seasonYear`: Immer aus der Season (aendert sich nicht).
- `matchdayName`: Bleibt wie bisher aus `race.getMatchday().getLabel()` — der PlayoffService setzt bereits korrekte Labels wie "Semifinal - Leg 1".

**C) Position/Seed kontextabhaengig laden:**
- Regular-Season-Rennen: Weiterhin `standingsService.calculateStandings()` fuer die Tabellenposition.
- Playoff-Rennen: Seed-Nummer aus `PlayoffSeedRepository.findByPlayoffIdAndTeamId()` laden.
- Die Template-Variablen `homePosition`/`awayPosition` bleiben gleich — nur die Werte aendern sich.
- OverlayGraphicService: `homeRecord`/`awayRecord` (W-L-D) wird bei Playoff-Rennen ebenfalls aus den Regular-Season-Standings geladen — zeigt die Saison-Bilanz des Teams als Kontext.

### 4. Templates

Keine Aenderungen an den Thymeleaf-Templates noetig. Die Variablen `seasonName`, `matchdayName`, `seasonYear`, `homePosition`, `awayPosition` bleiben identisch — die Grafik-Services befuellen sie kontextabhaengig mit den richtigen Werten.

## Betroffene Dateien

### Neu
- `org.ctc.domain.model.PlayoffSeed` — Entity
- `org.ctc.domain.repository.PlayoffSeedRepository` — Repository

### Geaendert
- `V1__initial_schema.sql` — Tabelle `playoff_seeds` hinzufuegen
- `LineupGraphicService` — Match-Check, Labels, Seed
- `ResultsGraphicService` — Match-Check, Labels, Seed (kein Position-Feld, aber seasonName)
- `SettingsGraphicService` — Match-Check, Labels, Seed
- `OverlayGraphicService` — Match-Check, Labels, Seed (Record statt Position)
- `PlayoffService` — Seed-Logik (Speichern, Auto-Seed-Bracket-Zuordnung)
- `SeedForm` / Seeding-Template — Nummernfeld, Auto-Seed-Button
- `TestDataService` — Playoff-Testdaten mit Seeds

## Nicht im Scope

- Aenderungen an Matchday-Level-Grafiken (MatchdaySchedule, MatchdayResults, MatchdayOverview) — diese haben keinen Race-Bezug
- Aenderungen an der Bracket-Visualisierung — Seeds werden dort nicht angezeigt (eigenes Feature)
