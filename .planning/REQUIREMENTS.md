# Requirements: CTC Manager — v1.9 Season Phases & Groups

**Defined:** 2026-04-26
**Core Value:** Architectural Consistency: All controllers delegate to services, exception handling is centralized, and the production environment is secured.

**Foundation:** `/Users/jegr/.claude/plans/ich-bin-mit-dem-pure-gem.md` — Architektur-Plan aus Brainstorming-Session.

## v1.9 Requirements

Requirements for milestone v1.9. Each maps to roadmap phases.

### MODEL — Schema & Entities

- [ ] **MODEL-01**: `SeasonPhase`-Entity mit `phaseType` (REGULAR/PLAYOFF/PLACEMENT), `layout` (LEAGUE/GROUPS/BRACKET), `format` (LEAGUE/SWISS/ROUND_ROBIN), `sortIndex`, `label`, `startDate`/`endDate`, `totalRounds`, `legs`, `eventDurationMinutes`, FKs zu `RaceScoring`/`MatchScoring`
- [ ] **MODEL-02**: Constraint: max. 1 REGULAR + ≤1 PLAYOFF + ≤1 PLACEMENT pro Saison
- [ ] **MODEL-03**: `SeasonPhaseGroup`-Entity (`name`, `sortIndex`, FK zu Phase) — nur bei `layout=GROUPS`
- [ ] **MODEL-04**: `PhaseTeam`-Roster-Entity `(phase_id, team_id, group_id?)`, UNIQUE auf `(phase_id, team_id)`
- [ ] **MODEL-05**: `Matchday.season_id` → `Matchday.phase_id` (NOT NULL) + optional `Matchday.group_id`
- [ ] **MODEL-06**: `Playoff.season_id` → `Playoff.phase_id` (UNIQUE); M:N `playoff_seasons` entfällt
- [ ] **MODEL-07**: `Season` reduziert auf Identitäts-/Audit-Felder; format/scoring/dates/totalRounds/legs entfallen aus Season
- [ ] **MODEL-08**: `SeasonDriver` + `SeasonTeam` strukturell unverändert; `SeasonTeam` behält saisonweite Farben-/Logo-Overrides

### MIGR — Flyway Migration

- [ ] **MIGR-01**: Neue V*-Migration legt `season_phases`, `season_phase_groups`, `phase_teams` an (H2 + MariaDB)
- [x] **MIGR-02**: Daten-Migration: 1 REGULAR-Phase pro Bestandssaison mit kopierten Format/Scoring/Rounds/Legs/Dates
- [x] **MIGR-03**: Daten-Migration: 1 PLAYOFF-Phase pro existierendem Playoff; FK umgehängt
- [x] **MIGR-04**: Daten-Migration: `matchday.phase_id` auf REGULAR-Phase gesetzt
- [x] **MIGR-05**: Daten-Migration: `phase_teams` aus heutigen `season_teams` abgeleitet (LEAGUE-Layout, group NULL)
- [ ] **MIGR-06**: Cleanup-Migration: alte Spalten aus `seasons` + M:N `playoff_seasons` entfernt
- [ ] **MIGR-07**: Alle Migrationen additiv (neue V-Files); Bestand-V1/V2 unverändert

### SVC — Domain Service Layer

- [x] **SVC-01**: Neuer `SeasonPhaseService` mit Phase-/Group-CRUD und Roster-Management via `PhaseTeam`
- [x] **SVC-02**: `StandingsService.calculateStandings(...)` auf `phaseId`/`groupId` umgestellt; Combined-View-Aggregation über Sub-Gruppen
- [x] **SVC-03**: `PlayoffService` + `PlayoffSeedingService` operieren auf PLAYOFF-Phase statt Saison
- [x] **SVC-04**: `MatchdayGeneratorService` + `SwissPairingService` phase-/group-aware
- [x] **SVC-05**: `DriverRankingService` phase-/group-aware (mit Aggregation über Saison)

### IMPORT — Driver Sheet Import

- [x] **IMPORT-01**: `SeasonRepository.findByYearAndNumber(int, int)` liefert eindeutige Saison
- [x] **IMPORT-02**: `DriverSheetImportService.preview()` löst Tabs über `(year, number)` auf; Tab-Pattern erlaubt `^\d{4}_S\d+$` zusätzlich zu `^\d{4}$`
- [x] **IMPORT-03**: Group-Mitgliedschaft eines importierten Drivers wird über `PhaseTeam` der REGULAR-Phase aufgelöst
- [x] **IMPORT-04**: Preview emittiert Warnung für Teams ohne Group-Zuordnung in der Ziel-REGULAR-Phase

### UI — Admin Workflow

- [x] **UI-01**: Saison-Form schlanker (year/number/name/description/active); Format/Scoring/Dates entfallen aus dieser Form
- [x] **UI-02**: Saison-Detail mit Phasen-Tabs; bei GROUPS-Phase zweite Tab-Ebene pro Gruppe (Roster, Matchdays, Standings je Tab)
- [x] **UI-03**: Neue Phase-Form für `SeasonPhase`-CRUD (Typ, Layout, Format, Scoring, Zeitraum, Rounds, Legs)
- [x] **UI-04**: Neue Group-Form für `SeasonPhaseGroup`-CRUD inkl. Team-Zuordnung via `PhaseTeam`
- [x] **UI-05**: Standings-UI mit Phase-/Group-Auswahl + Combined-View-Tab über Sub-Gruppen
- [x] **UI-06**: Driver-Import-Preview-Template zeigt eindeutige Saison-Zuordnung + Warnungen für unzugeordnete Teams
- [x] **UI-07**: Playoff-UI auf PLAYOFF-Phase umgestellt (statt Saison)

### DATA — Test and Dev Data

- [x] **DATA-01**: `TestDataService` legt Test-Saisons direkt mit Phasen/Gruppen an (Test-Prefix-Konvention bleibt; keine Backward-Compat-Helper für altes Modell)
- [x] **DATA-02**: `DevDataSeeder` (Profile `dev` / `dev,demo`) erzeugt fiktive Saison mit mind. einer GROUPS-Saison als Beispiel + Playoff-Phase

### QUAL — Quality and Coverage

- [ ] **QUAL-01**: JaCoCo Line-Coverage ≥ 82 % gehalten
- [ ] **QUAL-02**: E2E-Test deckt GROUPS-Saison: Anlegen, Roster pro Group, Matchdays pro Group, Driver-Import mit Group-Auflösung, Standings pro Group + Combined
- [ ] **QUAL-03**: Regression-Test: Bestandssaison öffnet nach Migration mit 1 REGULAR-Phase + allen Race-Daten erreichbar

## Future Requirements

Deferred to future milestones. Tracked but not in current roadmap.

### Playoff Enhancements

- **PLAYOFF-FUT-01**: Sub-Group-aware Playoff-Brackets (separater Playoff pro Gruppe statt gemeinsamer Bracket) — Modell unterstützt es bereits, UI/Default bleibt aber gemeinsamer Bracket bis Bedarf besteht

### Saison-Konsolidierung

- **CONSOL-FUT-01**: UI-Feature „Saisons konsolidieren" — zwei Bestandssaisons (z. B. alte Group-A/Group-B-Workarounds) zu einer Saison-mit-Groups zusammenführen

### Erweiterter Driver-Import

- **IMPORT-FUT-01**: Phase-/Group-Override im Sheet (zusätzliche Spalte) für Sonderfälle, in denen ein Fahrer nur in bestimmten Phasen startet

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Mehrere komplette Saisons pro Jahr | `number` bleibt Saison-Iteration, nicht Group-Discriminator — Mehrfach-Saisons im selben Jahr sind nicht das gelebte Modell |
| Trades/Team-Wechsel innerhalb einer Saison | Sub-Team-Wechsel via Parent-Team-Logik (heute schon vorhanden) genügt; Phase-Override im Driver-Import unnötig |
| Heuristische Konsolidierung alter Group-Workaround-Saisons | Risiko stiller Datenverschiebungen; manuelle Konsolidierung über UI ist zuverlässiger |
| Automatische Cross-Group-Playoff-Seeding-Algorithmen | Manuelles Seeding pro Phase reicht für die gelebte Praxis (immer gemeinsamer Bracket über alle Group-Top-X) |
| UI für rückwirkende Phase-Splits aus Bestandssaisons | Bestand bleibt mechanisch migriert (1 REGULAR-Phase pro Saison); kein UI-Feature für nachträgliches Aufteilen |

## Traceability

Which phases cover which requirements. Filled during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| MODEL-01 | 56 | Pending |
| MODEL-02 | 56 | Pending |
| MODEL-03 | 56 | Pending |
| MODEL-04 | 56 | Pending |
| MODEL-05 | 56 | Pending |
| MODEL-06 | 56 | Pending |
| MODEL-07 | 56 | Pending |
| MODEL-08 | 56 | Pending |
| MIGR-01 | 56 | Pending |
| MIGR-02 | 57 | Complete |
| MIGR-03 | 57 | Complete |
| MIGR-04 | 57 | Complete |
| MIGR-05 | 57 | Complete |
| MIGR-06 | 61 | Pending |
| MIGR-07 | 56 | Pending |
| SVC-01 | 58 | Complete |
| SVC-02 | 58 | Complete |
| SVC-03 | 58 | Complete |
| SVC-04 | 58 | Complete |
| SVC-05 | 58 | Complete |
| IMPORT-01 | 59 | Complete |
| IMPORT-02 | 59 | Complete |
| IMPORT-03 | 59 | Complete |
| IMPORT-04 | 59 | Complete |
| UI-01 | 60 | Complete |
| UI-02 | 60 | Complete |
| UI-03 | 60 | Complete |
| UI-04 | 60 | Complete |
| UI-05 | 60 | Complete |
| UI-06 | 60 | Complete |
| UI-07 | 60 | Complete |
| DATA-01 | 59 | Complete |
| DATA-02 | 59 | Complete |
| QUAL-01 | 61 | Pending |
| QUAL-02 | 61 | Pending |
| QUAL-03 | 61 | Pending |
