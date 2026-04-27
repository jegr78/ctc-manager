# Phase 57: Data Migration - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-27
**Phase:** 57-data-migration
**Areas discussed:** Migrations-Mechanismus, PLAYOFF-Phase Scoring/Format, V-File-Granularitaet, Verifikations-/Test-Strategie

---

## Migrations-Mechanismus

### Mechanism — Java vs SQL vs Hybrid

| Option | Description | Selected |
|--------|-------------|----------|
| Java-basiert (V4__*.java) | Klasse extends BaseJavaMigration, JdbcTemplate, UUID.randomUUID(). Portabilitaet ueber H2/MariaDB ohne Tricks; Type-Safety; einfaches Debugging. Neuer Pattern im Projekt. | ✓ |
| Pure SQL (V4__*.sql) | Reine SQL-Migration mit conditional UUID-Generierung. Konsistent mit V1/V2/V3 — aber H2 (RANDOM_UUID()) und MariaDB (UUID()) unterscheiden sich; portable Loesung erfordert Flyway-Placeholders, Lesbarkeit leidet. | |
| Hybrid (Java + SQL) | Java fuer komplexe Teile, separates SQL fuer NOT-NULL-Flip. Zwei Patterns parallel ohne klaren Mehrwert. | |

**User's choice:** Java-basiert (V4__*.java)
**Notes:** D-01. Reason: portable UUID generation, type-safety, easier logging.

### Struktur — eine vs mehrere V-Files

| Option | Description | Selected |
|--------|-------------|----------|
| Eine Klasse mit klaren Methoden | V4__MigrateSeasonsToPhases.java mit privaten Methoden je Schritt. Lesbar, klare Reihenfolge, leichter zu testen. | ✓ |
| Mehrere V-Files (V4-V8) je Schritt | Granulare Recovery, aber mehr Boilerplate, Reihenfolge implizit ueber Versionsnummern. | |

**User's choice:** Eine Klasse mit klaren Methoden
**Notes:** D-02. One Flyway version entry, deterministic order.

### Package — db.migration vs org.ctc.migration

| Option | Description | Selected |
|--------|-------------|----------|
| src/main/java/db/migration/ | Spring Boot Flyway-Default; ohne Konfiguration findet Flyway Java-Migrations dort. | ✓ |
| src/main/java/org/ctc/migration/ | Projekt-Namespace beibehalten — erfordert spring.flyway.locations Konfiguration. | |

**User's choice:** src/main/java/db/migration/
**Notes:** D-03. Spring Boot Flyway scans this by default; consistent with src/main/resources/db/migration/.

### Fehler-Handling — fail-fast vs skip vs preflight

| Option | Description | Selected |
|--------|-------------|----------|
| Fail-fast mit klarer Fehlermeldung | throw FlywayException mit konkreter Saison-ID + fehlendem Feld. Migration bricht ab. Datenintegritaet > Verfuegbarkeit. | ✓ |
| Skip & log (best-effort) | Bei NULL-Werten Default verwenden oder ueberspringen. Risiko stiller Datenverluste. | |
| Preflight via JPA-Validation | Vor INSERT alle Saisons laden, validieren, Liste aller Probleme ausgeben. Zusaetzlicher Code; fuer reale Daten unwahrscheinlich noetig. | |

**User's choice:** Fail-fast mit klarer Fehlermeldung
**Notes:** D-05. Admin-Tool — kurzfristiger Ausfall akzeptabel; klarer Hinweis was zu reparieren ist.

---

## PLAYOFF-Phase Scoring/Format

### Scoring-Quelle

| Option | Description | Selected |
|--------|-------------|----------|
| Aus der Saison kopieren | PLAYOFF-Phase erbt season.raceScoring/matchScoring direkt. Einfach, deterministisch, gleiche Wertung wie Regular Season. | ✓ |
| Aus REGULAR-Phase referenzieren | Erst Regular anlegen, dann Playoff von Regular kopieren. Funktional identisch, aber semantisch anders. Mehr Code, kein Mehrwert. | |
| Spezielles Default-Scoring | Erstes RaceScoring/MatchScoring in DB. Nicht gelebte Praxis, kann zu Ueberraschungen fuehren. | |

**User's choice:** Aus der Saison kopieren
**Notes:** D-07. Matches lived practice (playoffs use same scoring as regular season).

### Felder — Defaults vs Erben vs Minimal

| Option | Description | Selected |
|--------|-------------|----------|
| Standard-Defaults | phaseType=PLAYOFF, layout=BRACKET, format=LEAGUE (DB-Default), sortIndex=10, label='Playoff' (oder playoff.name), totalRounds=NULL, legs=1, eventDurationMinutes/start/end aus Playoff. | ✓ |
| Werte aus REGULAR erben | format=regular.format, legs=regular.legs, label=regular.label + ' Playoff'. Konsistent mit Saison; format-Mismatch zu BRACKET. | |
| Aus playoff.name + minimale Defaults | label=playoff.name, andere Felder NULL wo erlaubt. Minimale Annahmen, aber format-Default bleibt LEAGUE. | |

**User's choice:** Standard-Defaults
**Notes:** D-08. label=playoff.name carries human-readable label; sortIndex=10 leaves room for PLACEMENT at 20.

### M:N-Tabelle playoff_seasons

| Option | Description | Selected |
|--------|-------------|----------|
| Unveraendert lassen | Tabelle und Eintraege bleiben bestehen. Drop ist Phase 61. Konsistent mit ROADMAP-SC5. | ✓ |
| Eintraege loeschen, Tabelle behalten | DELETE FROM playoff_seasons. Risiko: bestehender Code, der ueber Playoff.seasons iteriert, sieht nichts mehr. | |
| Ignorieren | Identisch zu Option 1 in der Wirkung. | |

**User's choice:** Unveraendert lassen (auch in Phase 57)
**Notes:** D-09. ROADMAP-SC5: alte Bridge-Spalten und Tabellen bleiben intakt fuer backward-compatible code.

---

## V-File-Granularitaet (Unter-Aspekte zur Java-Migration)

### NOT-NULL-Flip

| Option | Description | Selected |
|--------|-------------|----------|
| Im selben Java-Migration-File ueber JdbcTemplate.execute() | Dialect-Detection via Connection.getMetaData(). Atomar in einer Migration, klare Reihenfolge. | ✓ |
| Separates V5__flip_not_null.sql mit Flyway-Placeholders | Erfordert spring.flyway.placeholders-Konfiguration; V4 und V5 muessen zusammen laufen. | |
| Separates V5__flip_not_null.java | Konsequent Java; zwei Files fuer eng zusammenhaengende Aenderung. | |

**User's choice:** Im selben Java-Migration-File ueber JdbcTemplate.execute()
**Notes:** D-12. Reihenfolge: erst Backfill, dann Flip — sonst schlaegt der Constraint-Check fehl.

### Transaktion

| Option | Description | Selected |
|--------|-------------|----------|
| Eine Transaktion (Flyway-Default) | Ganze Migration rollt zurueck bei Fehler. MariaDB DDL implicit commit ist Trade-off. | ✓ |
| Mehrere Transaktionen | Granulare Recovery, aber inkonsistente DB bei Teil-Fehler. Komplex, fuer unsere Datenmenge overkill. | |

**User's choice:** Eine Transaktion (Flyway-Default)
**Notes:** D-04. canExecuteInTransaction()=true (default).

### Reihenfolge

| Option | Description | Selected |
|--------|-------------|----------|
| Regular -> Playoff -> Matchday-FK -> PhaseTeams -> NOT-NULL-Flip | Klare Datenfluss-Abhaengigkeit. | ✓ |
| Andere Reihenfolge | Freie Antwort. | |

**User's choice:** Regular -> Playoff -> Matchday-FK -> PhaseTeams -> NOT-NULL-Flip
**Notes:** D-02 / D-13. Ein Flyway-Eintrag, deterministisch.

### Logging

| Option | Description | Selected |
|--------|-------------|----------|
| Strukturiertes Logging mit Counts pro Schritt | log.info() vor und nach jedem Schritt; Counts via JdbcTemplate.queryForObject. | ✓ |
| Minimales Logging | Nur Start/Ende. | |

**User's choice:** Strukturiertes Logging mit Counts pro Schritt
**Notes:** D-14. Bei Prod-Migration sieht User in Logs was passiert.

---

## Verifikations-/Test-Strategie

### Test-Approach

| Option | Description | Selected |
|--------|-------------|----------|
| Java-Integration-Test mit @JdbcTest + manuellem Flyway-Setup | Test startet eigene Flyway-Instanz: V1-V3, dann legacy data, dann V4 manuell, dann Asserts. | ✓ |
| Test-Migration V3.5__seed_legacy_data.sql nur in test/resources | Einfacher Pattern, aber V3.5-Versionsnummer im Test-Klassenpfad seltsam. | |
| Application-Level-Test mit DemoDataSeeder | Realistisch, aber DemoDataSeeder muesste Pre-V4-Style Daten erzeugen — tricky mit V3-aware-Entities. | |

**User's choice:** Java-Integration-Test mit @JdbcTest + manuellem Flyway-Setup
**Notes:** D-15. Echtes Vorher/Nachher ohne Coupling an main app context.

### Asserts — pro SC oder eine

| Option | Description | Selected |
|--------|-------------|----------|
| Pro Success Criterion ein Test-Methoden | 5 Test-Methoden je SC1-SC5 + NOT-NULL-Constraint-Test. Informative Diagnose. | ✓ |
| Eine grosse end-to-end-Test-Methode | Weniger Boilerplate, weniger informative Diagnose. | |

**User's choice:** Pro Success Criterion ein Test-Methoden
**Notes:** D-16. 5 SC-Tests + 1 NOT-NULL-Constraint-Test = 6 Test-Methoden.

### Szenarien — realistisch oder minimal

| Option | Description | Selected |
|--------|-------------|----------|
| Realistisches Mehrfach-Saison-Szenario | 3 Saisons (eine mit Playoff, eine ohne, eine leere), 2 Teams pro Saison, 2 Matchdays pro Saison. Edge-Cases abgedeckt. | ✓ |
| Minimales Happy-Path-Szenario | 1 Saison mit 1 Playoff, 2 Teams, 1 Matchday. Edge-Cases nicht abgedeckt. | |

**User's choice:** Realistisches Mehrfach-Saison-Szenario
**Notes:** D-17. Empty-Season-Case essenziell — phase_teams = 0 fuer leere Saison muss durchlaufen.

### Smoke-Test

| Option | Description | Selected |
|--------|-------------|----------|
| Ja, einfacher @SpringBootTest | Spring startet (= Flyway gruen), seasonRepository.findAll() liefert Saisons mit phases-Collection. End-to-End-Confidence. | ✓ |
| Nein, separates IT-Test reicht | JdbcTest deckt schon alles ab. SpringBoot-Test waere redundant. | |

**User's choice:** Ja, einfacher @SpringBootTest
**Notes:** D-18. Bestaetigt dass Migration + JPA-Mapping + Repositories alle zusammenspielen.

---

## Claude's Discretion

- Exact MariaDB column type in `MODIFY COLUMN` ALTER (BINARY(16) vs UUID literal) — planner verifies against actual DB metadata at runtime.
- Whether to wrap each step's `log.info` count query in try/catch (counter failures non-fatal).
- Per-step transaction boundary fallback if MariaDB DDL implicit-commit becomes a problem during prod migration.
- Test data UUIDs — fixed (deterministic) vs `UUID.randomUUID()` per test run.

## Deferred Ideas

- Drop of `playoff_seasons` M:N table and `Playoff.seasons` collection — Phase 61 (MIGR-06).
- Drop of legacy Season columns (`format`, `total_rounds`, etc.) — Phase 61.
- Drop of `matchdays.season_id` and `playoffs.season_id` — Phase 61.
- Service-layer rewrite to consume `phase_id` — Phase 58.
- Custom phase/group-aware repository finders — Phase 58.
- TestDataService / DevDataSeeder rewrite — Phase 59.
- Group-aware data migration (sub-group rosters, group matchdays) — out of scope for v1.9 (CONSOL-FUT-01 future).
- Phase-Override on driver-import sheet — out of scope (IMPORT-FUT-01 future).
- PLACEMENT phases for legacy data — none exist; PLACEMENT-phase UI is Phase 60.
