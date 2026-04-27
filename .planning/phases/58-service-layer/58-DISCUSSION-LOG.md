# Phase 58: Service Layer - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-27
**Phase:** 58-service-layer
**Areas discussed:** API-Cutover-Stil, Combined-View Standings, Driver-Ranking-Aggregation, Test-Fixtures-Strategie, SeasonPhaseService-CRUD-Validierung, PlayoffSeedingService Pool-Quelle, MatchdayGenerator Group-Granularität, Season delete-Cascade, Playoff-Phase Auto-Erzeugung, PhaseTeam-Roster Initial-Population, SwissPairing bye-Team in GROUPS, Repository-Finder Naming, SiteGenerator Phase-Update-Timing, EntityGraph-Strategie, SeasonManagementService.save Form-Cleanup, MatchdayService Phase-Filter

---

## API-Cutover-Stil

| Option | Description | Selected |
|--------|-------------|----------|
| phaseId-only mit Overload | Kanonische phaseId-Signatur + dünner seasonId-Overload via SeasonPhaseService.findRegularPhase. Controllers bleiben auf seasonId. Phase 60 entfernt Overloads. | ✓ |
| Harte Umstellung in Phase 58 | Alle Controller müssen in Phase 58 mit umziehen. Keine Übergangs-API. | |
| Voll-parallele Methoden | seasonId-Methoden 1:1 erhalten + zusätzliche phaseId-Methoden. Doppelter Code-Pfad. | |

**User's choice:** phaseId-only mit Overload (Recommended)

---

| Option | Description | Selected |
|--------|-------------|----------|
| SeasonPhaseService zentral | findRegularPhase(seasonId) als einziger Auflösepunkt. Wirft EntityNotFoundException. | ✓ |
| Jeder Service löst selbst auf | seasonPhaseRepository direkt injiziert pro Service. | |
| Static Helper / Utility | SeasonPhaseResolver-Klasse als separater Lookup. | |

**User's choice:** SeasonPhaseService zentral (Recommended)

---

| Option | Description | Selected |
|--------|-------------|----------|
| @Deprecated + Bridge-Verhalten | M:N-Methoden bleiben funktional + alle Transitional-APIs als @Deprecated markieren. | ✓ |
| Hart umstellen | M:N-Methoden werfen sofort UnsupportedOperationException. | |
| Keine Markierung | M:N funktional, ohne @Deprecated. | |

**User's choice:** @Deprecated + Bridge-Verhalten beibehalten (Recommended)

---

## Combined-View Standings

| Option | Description | Selected |
|--------|-------------|----------|
| Flache Liste, rohe Punkte | Alle Teams in eine Liste, Sortierung points→pointDiff→pointsFor. | ✓ |
| Per-Group-Konkat ohne Sortierung | Map<Group, List<TeamStanding>> Rückgabe. | |
| Championship-Style mit Cross-Group-Anpassung | Komplexer Fair-Rank-Algorithmus. | |

**User's choice:** Flache Liste, rohe Punkte (Recommended)

---

| Option | Description | Selected |
|--------|-------------|----------|
| Optional group-Feld auf TeamStanding | Eine Rückgabesignatur, nullable group-Feld. | ✓ |
| Wrapper-Record CombinedStandingsRow | Neuer Record, zwei Rückgabetypen. | |
| Map<Group, List<TeamStanding>> | StandingsView mit perGroup + flat. | |

**User's choice:** Optional group-Feld auf TeamStanding (Recommended)

---

| Option | Description | Selected |
|--------|-------------|----------|
| Per-Group berechnen, in Combined ignorieren | Buchholz nur per Group; Combined fällt zurück auf points→pointDiff→pointsFor. | ✓ |
| Buchholz global gegen alle Gruppen-Gegner | Globale Buchholz-Berechnung, mathematisch fragwürdig. | |
| Combined-View verbietet Swiss/Buchholz | IllegalArgumentException bei GROUPS-Layout + null groupId. | |

**User's choice:** Per-Group berechnen, in Combined ignorieren (Recommended)

---

## Driver-Ranking-Aggregation

| Option | Description | Selected |
|--------|-------------|----------|
| Nur REGULAR | Saisonweite Aggregation = REGULAR-only (heutiges Verhalten). | |
| REGULAR + PLAYOFF | Beide Phasen-Typen zählen. | |
| Alle Phasen (REGULAR + PLAYOFF + PLACEMENT) | Universellster Ansatz. | ✓ |

**User's choice:** Alle Phasen (REGULAR + PLAYOFF + PLACEMENT)
**Notes:** Verhaltensänderung gegenüber heute (PLAYOFF-Races landeten via Race.playoffMatchup nicht in der Saisons-Aggregation). Wird im Plan als Behavior-Change vermerkt.

---

| Option | Description | Selected |
|--------|-------------|----------|
| REGULAR-Phase-Team gewinnt | Saisonweite Aggregation zeigt Driver mit REGULAR-Team. | ✓ |
| Per-Race via RaceLineup | Granular pro (Driver, Team)-Paar; UI würde verwirrt. | |
| PhaseTeam pro Phase verwenden | Pro Phase eigenes Team, REGULAR als Default für saisonweit. | |

**User's choice:** REGULAR-Phase-Team gewinnt (Recommended)

---

| Option | Description | Selected |
|--------|-------------|----------|
| Zwei explizite Methoden | calculateRankingForPhase + calculateRankingForSeason. | |
| Eine Methode mit Phase-Filter-Liste | calculateRanking(seasonId, EnumSet<PhaseType>). | |
| Eine Methode pro Phase, Aggregation per Stream im Aufrufer | DriverRankingService bleibt schmal. | ✓ |

**User's choice:** Eine Methode pro Phase, Aggregation per Stream im Aufrufer
**Notes:** Q4 schloss "wo lebt die Aggregation" → Default-Methode auf DriverRankingService (siehe nächste Frage).

---

| Option | Description | Selected |
|--------|-------------|----------|
| Default-Methode auf DriverRankingService | aggregateAcrossPhases als named method auf dem Service. | ✓ |
| Aufrufer-Site (Controllers / SiteGenerator) | Reduce-Logik überall dupliziert. | |
| Neuer SeasonReportService | Cross-phase Aggregations-Service. | |

**User's choice:** Default-Methode auf DriverRankingService (Recommended)

---

| Option | Description | Selected |
|--------|-------------|----------|
| RaceLineup-Fallback | Bei fehlendem REGULAR-PhaseTeam: Team aus erstem/letztem RaceLineup. | ✓ |
| Driver bleibt teamlos | null-Team, UI rendert Sentinel. | |
| Driver wird ausgeschlossen | Stand-Ins erscheinen nicht im saisonweiten Ranking. | |

**User's choice:** RaceLineup-Fallback (Recommended)

---

## Test-Fixtures-Strategie

| Option | Description | Selected |
|--------|-------------|----------|
| Lokaler PhaseTestFixtures-Helper | Neue Test-Klasse mit builder-style Methoden. | ✓ |
| Inline JdbcTemplate / TestEntityManager pro Test | Self-contained pro Test. | |
| TestDataService selbst erweitern (Bridge-Methoden) | Phase 58 schon Phase-aware Builder in TestDataService. | |

**User's choice:** Lokaler PhaseTestFixtures-Helper (Recommended)

---

| Option | Description | Selected |
|--------|-------------|----------|
| Bestehendes Pattern beibehalten: Mockito | Mockito-Unit-Tests, PhaseTestFixtures liefert Stub-Entities. | ✓ |
| Mockito + ein @DataJpaTest pro Service | Doppelt Aufwand, Sicherheitsnetz für Phase-Migration. | |
| Nur @DataJpaTest | Kompletter Switch auf @DataJpaTest. | |

**User's choice:** Bestehendes Pattern beibehalten: Mockito (Recommended)

---

| Option | Description | Selected |
|--------|-------------|----------|
| @DataJpaTest pro Repository | Drei IT-Tests für die neuen Repositories. | ✓ |
| Nur per Service-Test indirekt | Custom-Finders nur indirekt abgedeckt. | |
| Ein gemeinsamer Repository-IT-Test | Eine PhaseRepositoriesIT-Klasse. | |

**User's choice:** @DataJpaTest pro Repository (Recommended)

---

## SeasonPhaseService-CRUD-Validierung

| Option | Description | Selected |
|--------|-------------|----------|
| Service prüft VOR INSERT | findBySeasonAndPhaseType-Vorabprüfung + BusinessRuleException. | ✓ |
| DB-Constraint vertrauen, Exception konvertieren | DataIntegrityViolation → BusinessRuleException via try/catch. | |
| Beides (Belt & Suspenders) | Beide Schichten + Race-Condition-Schutz. | |

**User's choice:** Service prüft VOR INSERT (Recommended)

---

## PlayoffSeedingService Pool-Quelle

| Option | Description | Selected |
|--------|-------------|----------|
| REGULAR-Phase-Standings (Top-N) | autoSeedBracket zieht Top-N aus REGULAR-Standings. | ✓ |
| PhaseTeam-Roster der PLAYOFF-Phase | Admin pflegt PLAYOFF-Roster manuell. | |
| Schnittmenge: REGULAR-Top-N ∩ PLAYOFF-Roster | Top-N aus REGULAR, gefiltert auf PLAYOFF-Roster. | |

**User's choice:** REGULAR-Phase-Standings (Top-N) (Recommended)

---

## MatchdayGenerator Group-Granularität

| Option | Description | Selected |
|--------|-------------|----------|
| Pro Gruppe einzeln | generate(phaseId, groupId, ...) verlangt groupId bei GROUPS. | ✓ |
| Bulk-Aufruf (alle Gruppen) | generate(phaseId, ...) generiert alle Gruppen auf einmal. | |
| Beides (Optional groupId) | Bulk wenn groupId=null, sonst per-Gruppe. | |

**User's choice:** Pro Gruppe einzeln (Recommended)

---

## Season delete-Cascade-Verhalten

| Option | Description | Selected |
|--------|-------------|----------|
| Strikt: nur leere Saisons löschbar | BusinessRuleException bei aktiven Phasen. | ✓ |
| Cascade-löschen mit Confirm | Alles cascade-löschen, UI macht Confirm. | |
| Heute behalten (kein Phase-spezifischer Code) | delete bleibt unverändert, JPA cascade-löscht. | |

**User's choice:** Strikt: nur leere Saisons löschbar (Recommended)
**Notes:** Verhaltensänderung gegenüber heute. Heutiger SeasonManagementService.delete hat keine Pre-Checks. Phase 58 führt das strikte Guard ein.

---

## Playoff-Phase Auto-Erzeugung

| Option | Description | Selected |
|--------|-------------|----------|
| Auto: createPlayoff legt PLAYOFF-Phase mit an | Wenn keine PLAYOFF-Phase existiert: auto-anlegen. | ✓ |
| Strikt: Phase muss vorher existieren | createPlayoff verlangt phaseId, sonst Exception. | |
| Hybrid — phaseId optional, sonst Auto | UI hat Wahl zwischen explizit oder auto. | |

**User's choice:** Auto: createPlayoff legt PLAYOFF-Phase mit an (Recommended)

---

## PhaseTeam-Roster Initial-Population

| Option | Description | Selected |
|--------|-------------|----------|
| Phase-spezifisch | REGULAR auto aus SeasonTeam, PLAYOFF/PLACEMENT/GROUPS leer. | ✓ |
| Immer leer | Egal welcher Phase-Typ, immer leer. | |
| Immer aus SeasonTeam kopieren | Jede Phase erbt volles SeasonTeam-Roster. | |

**User's choice:** Phase-spezifisch (Recommended)

---

## SwissPairing bye-Team in GROUPS

| Option | Description | Selected |
|--------|-------------|----------|
| Komplett pro-Gruppe-isoliert | Alle 4 Methoden akzeptieren groupId, pro Group eigener Bye/Round. | ✓ |
| phaseId-only, Bye pro Gruppe intern | Sammelblick aus allen Gruppen. | |
| Phase-Layout-Sniffing intern | Bei groupId=null Bulk, sonst per Gruppe. | |

**User's choice:** Komplett pro-Gruppe-isoliert (Recommended)

---

## Repository-Finder Naming

| Option | Description | Selected |
|--------|-------------|----------|
| Magic-Naming bevorzugt, @Query als Fallback | Method-Name-Magic für einfache, @Query für komplexe Queries. | ✓ |
| Immer @Query mit JPQL | Alle Custom-Finder als @Query JPQL-String. | |
| @EntityGraph + Magic-Naming | Magic-Naming + automatisch @EntityGraph. | |

**User's choice:** Magic-Naming bevorzugt, @Query als Fallback (Recommended)

---

## SiteGenerator Phase-Update-Timing

| Option | Description | Selected |
|--------|-------------|----------|
| Phase 58: SiteGen wird mitgezogen | SiteGenerator nutzt phaseId-APIs ab Phase 58. | ✓ |
| Phase 58: SiteGen bleibt unangetastet | SiteGenerator nutzt @Deprecated seasonId-Overloads. | |
| SiteGen NUR die Hot-Paths in Phase 58 | Mittelweg, keine konkreten Änderungen. | |

**User's choice:** Phase 58: SiteGen wird mitgezogen (Recommended)

---

## EntityGraph-Strategie

| Option | Description | Selected |
|--------|-------------|----------|
| Pragmatic: bei Bedarf | EntityGraph nur wo messbar nötig. | ✓ |
| Vollständig: EntityGraph auf alle Custom-Finder | Maximal optimiert. | |
| Defer auf Phase 60 oder QUAL-Audit | Nichts in Phase 58. | |

**User's choice:** Pragmatic: bei Bedarf (Recommended)

---

## SeasonManagementService.save Form-Cleanup

| Option | Description | Selected |
|--------|-------------|----------|
| Status quo: weiter alte Season-Felder | + Auto-Sync auf REGULAR-Phase. | ✓ |
| Dual-Write: REGULAR-Phase ist Wahrheit | Form-Refactoring schon in Phase 58. | |
| Nichts ändern, alte Felder weiter Wahrheit | Inkonsistenz-Risiko. | |

**User's choice:** Status quo: weiter alte Season-Felder (Recommended)

---

## MatchdayService Phase-Filter

| Option | Description | Selected |
|--------|-------------|----------|
| Beide Wege parallel | findByPhaseId + @Deprecated findBySeasonId-Bridge. | ✓ |
| Hard-Switch zu phaseId | findBySeasonId gelöscht, alle Aufrufer umgestellt. | |
| phaseId-only ohne Bridge | Aufrufer baut sich Bridge selbst. | |

**User's choice:** Beide Wege parallel (Recommended)

---

## Claude's Discretion

- Exakte Method-Signaturen und Return-Shapes (z.B. ob `MatchdayGeneratorService.GeneratorFormData` `SeasonPhase phase` allein oder zusammen mit `Season season` trägt) — Planner.
- Ob `aggregateAcrossPhases` als Java-Interface-default-Methode oder als reguläre public method auf der `@Service`-Klasse implementiert wird — Planner.
- Detection-Mechanik im Season-delete-Guard (boolean-exists vs count-Query) — Planner.
- Reihenfolge in der die sechs Services migriert werden (vermutlich SeasonPhaseService zuerst, dann StandingsService, dann der Rest).
- Ob `findByRaceMatchdayPhaseId` als Magic-Name funktioniert oder auf `@Query` zurückfällt — Planner verifiziert.
- Test-Methoden-Counts pro Service — Planner inflates D-12 zu konkreten given/when/then-Methoden pro Success-Criterion.

## Deferred Ideas

(siehe `<deferred>`-Sektion in 58-CONTEXT.md — deckt Phase-60 UI-Cutover, Phase-61 Cleanup, Phase-59 TestDataService/DriverImport-Refactor, sowie zukünftige Features wie per-group playoff brackets)
