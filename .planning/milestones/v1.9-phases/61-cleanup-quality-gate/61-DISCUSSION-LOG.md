# Phase 61: Cleanup & Quality Gate - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-01
**Phase:** 61-cleanup-quality-gate
**Areas discussed:** Code-Cleanup-Scope, MIGR-06 Mechanik, QUAL-02 E2E-Tiefe, QUAL-03 Regression-Fixture, Plan-Strukturierung, Worktree, Milestone-Abschluss, Convenience-API, Branch-Strategie

---

## Code-Cleanup-Scope

### Scope-Reichweite

| Option | Description | Selected |
|--------|-------------|----------|
| Strikt ROADMAP + Compile-Kaskade | Nur die 8 ROADMAP-Spalten + M:N + Cascade. matchdays.season_id + playoffs.season_id bleiben. | |
| Strikt + Audit aller season.getX() | + grep-Audit über Java + Templates + Tests. | |
| Maximaler Cleanup inkl. Bridge-Spalten | + Drop matchdays.season_id + playoffs.season_id (bricht Phase 56 D-02 / Phase 57 SC5 — erfordert ROADMAP-Update). | ✓ |

**User's choice:** Maximaler Cleanup inkl. Bridge-Spalten
**Notes:** Begründung implizit — das neue Modell ist Season → SeasonPhase → Matchday/Playoff, Bridge-FKs sind denormalisiert.

### Migration der ~14 Aufrufstellen

| Option | Description | Selected |
|--------|-------------|----------|
| Convenience-Getter behalten | matchday.getSeason() / playoff.getSeason() bleiben, delegieren auf phase. | ✓ |
| Explizite phase.getSeason()-Aufrufe | Convenience-Methoden weg; alle Aufrufer schreiben phase.getSeason() explizit. | |
| Hybrid: Java-Convenience + Templates explizit | Inkonsistent. | |

**User's choice:** Convenience-Getter behalten
**Notes:** Konsistent mit project memory feedback_entity_refactoring.

### Legacy-Endpoints

| Option | Description | Selected |
|--------|-------------|----------|
| Komplett entfernen | 404 für /admin/playoffs/{id}/add-season + /remove-season. | ✓ |
| 302 Redirect zur Playoff-Detail-Page | Backward-Compat-stärker, aber totes Routing. | |
| 410 Gone Response | HTTP-semantisch korrekt, unnötige Komplexität. | |

**User's choice:** Komplett entfernen
**Notes:** UI seit Phase 60 D-43 versteckt; keine externen Konsumenten erwartet. Tracked Behavior Change.

### ROADMAP-Update-Strategie

| Option | Description | Selected |
|--------|-------------|----------|
| ROADMAP-Update als Teil von Phase 61 | Plan 1 in Phase 61 startet mit ROADMAP-Update + PROJECT.md Decision-Eintrag. | ✓ |
| ROADMAP-Update vor /gsd-plan-phase manuell | User editiert ROADMAP selbst vor Plan-Phase. | |
| Nur in CONTEXT.md dokumentieren | ROADMAP bleibt unverändert. | |

**User's choice:** ROADMAP-Update als Teil von Phase 61
**Notes:** Audit-Trail bleibt sauber im Git-Log.

### Season.matchdays-@OneToMany-Mapping

| Option | Description | Selected |
|--------|-------------|----------|
| Derived Getter | phases.flatMap(p → p.matchdays). Konsistent mit Convenience-Approach. | ✓ |
| Season.matchdays komplett entfernen | Aufrufer schreiben season.getPhases().flatMap(...) explizit. | |
| Claude's Discretion | Planner entscheidet. | |

**User's choice:** Derived Getter

### Test-Cleanup-Strategie

| Option | Description | Selected |
|--------|-------------|----------|
| Compile-getrieben fixen + Audit-Pass | Compile-Failures markieren + grep-Audit + neue TestHelper-Helpers. | ✓ |
| Comprehensive TestHelper-Refactoring | Alle Tests umbauen für Konsistenz. | |
| Minimal-Invasive Test-Patches | Nur das Nötigste. | |

**User's choice:** Compile-getrieben fixen + Audit-Pass

---

## MIGR-06 Mechanik

### V6-Form

| Option | Description | Selected |
|--------|-------------|----------|
| Pure SQL, single V6 file | DROP-Statements in FK-Reihenfolge, H2 + MariaDB kompatibel. | ✓ |
| Pure SQL, gesplittet V6+V7 | Mehr Audit-Files, Zwischen-Smoke-Test möglich. | |
| Java-Migration analog V4 | Pre-Validation, Logging — overkill für DROP. | |

**User's choice:** Pure SQL, single V6 file

### Reihenfolge: Code vs. Migration

| Option | Description | Selected |
|--------|-------------|----------|
| Code-First, dann V6 | Plan 61-02: Java-Refactoring. Plan 61-03: V6. Hibernate startet zwischendurch sauber. | ✓ |
| V6-First, dann Code | Hibernate-Startup bricht zwischendurch. | |
| Atomar in einem Plan | Plan-Größe + Subagent-Risiko. | |

**User's choice:** Code-First, dann V6
**Notes:** Hibernate ddl-auto=validate auf allen Profilen — Code-First zwingt sauberen Zwischenzustand.

### V6-Verifikation

| Option | Description | Selected |
|--------|-------------|----------|
| H2-Test in Failsafe | MigrationTest.java in src/test/java/db/migration/, läuft im normalen verify. | ✓ |
| H2-Test + manuelles MariaDB-Smoke | + docker compose up, manuelle Verifikation. | |
| H2 + MariaDB-Integration-Test | Testcontainers, hoher Aufwand. | |

**User's choice:** H2-Test in Failsafe (im normalen verify, nicht -Pe2e)

### V6-Pre-Checks

| Option | Description | Selected |
|--------|-------------|----------|
| Keine Pre-Checks | V4 hat schon validiert. SQL bleibt einfach + atomar. | ✓ |
| Smoke-Doku für Prod-Rollout | + PROD-ROLLOUT.md mit Backup-Schritten. | |
| Java-Migration mit Pre-Check | Widerspricht der V6-Form-Entscheidung. | |

**User's choice:** Keine Pre-Checks

### V6-Test-Ort

| Option | Description | Selected |
|--------|-------------|----------|
| src/test/java/db/migration/V6MigrationTest.java | Spiegelt V4-Source-Path. | ✓ |
| src/test/java/org/ctc/migration/V6MigrationTest.java | Im org.ctc-Package, neuem 'migration'-Sub-Package. | |
| Inline in QUAL-03 Regression-Test | Ein Test-File weniger, aber QUAL-Anforderungen vermischt. | |

**User's choice:** src/test/java/db/migration/V6MigrationTest.java

### V6-Test-Profile

| Option | Description | Selected |
|--------|-------------|----------|
| Normales verify | Surefire-Run, CI-Standard-Build fängt Migration-Regressionen. | ✓ |
| Failsafe -Pe2e | Nur bei expliziter -Pe2e-Aktivierung. | |

**User's choice:** Normales verify

---

## QUAL-02 E2E-Tiefe

### Coverage-Tiefe

| Option | Description | Selected |
|--------|-------------|----------|
| Voller End-to-End-Workflow | Eine Test-Methode, ~80-150 Zeilen, 30-60s Laufzeit. | ✓ |
| Aufgesplittet in 3-5 fokussierte Tests | Mehrere Test-Klassen mit Setup-Helper. | |
| Smoke-Test-Niveau | Nur Kernpfade. | |

**User's choice:** Voller End-to-End-Workflow

### Google-Sheets-Mocking

| Option | Description | Selected |
|--------|-------------|----------|
| @TestConfiguration mit @Primary GoogleSheetsService-Stub | Konsistent mit ImportE2eTest. | ✓ |
| Static CSV-Fixture + CSV-Import-Pfad | Umgeht Sheets-Pfad. | |
| @MockBean GoogleSheetsService | Pattern-Drift. | |

**User's choice:** @TestConfiguration

### Assertions-Strategie

| Option | Description | Selected |
|--------|-------------|----------|
| Hybrid: UI + DB-State | Playwright + Repository-Lookups. | ✓ |
| Nur UI-Assertions | Reines Black-Box. | |
| Nur DB-State + minimale UI | Schneller, aber kein User-Erlebnis-Test. | |

**User's choice:** Hybrid

### Test-Daten-Naming

| Option | Description | Selected |
|--------|-------------|----------|
| T-Prefix konsistent (Year=2099) | T-GA-1, T-GB-1, ..., Saison "Test-GROUPS Season 2099". | ✓ |
| Random UUID-Suffix | Eindeutig, aber Debug schwer lesbar. | |

**User's choice:** T-Prefix konsistent

### Race-Result-Eintragung

| Option | Description | Selected |
|--------|-------------|----------|
| Volle UI-Klick-Eintragung | UI-Form-Bindings + JS + Success-Flash validiert. | ✓ |
| Direkter Service-Aufruf | Schneller, aber UI-Pfad nicht getestet. | |
| Hybrid: 1× UI + Rest via Service | Pattern-Verwirrung. | |

**User's choice:** Volle UI-Klick-Eintragung

### Volumen

| Option | Description | Selected |
|--------|-------------|----------|
| 2 Matchdays pro Group, 1 Race pro Matchday | 4 Races total, ~30-60s. | ✓ |
| 1 Matchday pro Group, 1 Race | Schnell, aber Combined-Aggregation kaum testbar. | |
| Realistic: 4 Matchdays × 4 Races pro Group | Hochrealistisch, aber 2+min, höheres Flake-Risiko. | |

**User's choice:** 2 Matchdays pro Group, 1 Race pro Matchday

---

## QUAL-03 Regression-Fixture

### Fixture-Konstruktion

| Option | Description | Selected |
|--------|-------------|----------|
| @Sql Pre-Insert vor V4 | Nicht möglich, Flyway läuft vor Tests. | |
| @Sql Pre-Insert in legacy-shape POST-V4 | Saison + Matchday + Playoff direkt in Phase-Form anlegen. | ✓ |
| TestDataService mit createLegacyMigratedSeason() | Pragmatisch, weniger explizit-'migrated'. | |
| Separater Test-Flyway-Run mit V1-V4-only | Zwei Flyway-Configs, hochkomplex. | |

**User's choice:** @Sql Pre-Insert in legacy-shape POST-V4

### QUAL-03-Assertions-Tiefe

| Option | Description | Selected |
|--------|-------------|----------|
| Vollständiges Lese-Path | (a)-(g) Asserts: Tab-Anzahl, Matchday-Liste, Race-Detail, Standings, Legacy-URL, Werte. | ✓ |
| Smoke (Tab + URL) | Nur (a)-(c). | |
| Vollständig + Schreib-Test | + neuen Race-Result auf migrierte Saison. Strenger als ROADMAP. | |

**User's choice:** Vollständiges Lese-Path

### Playoff-Variante

| Option | Description | Selected |
|--------|-------------|----------|
| Beide Varianten | givenWithoutPlayoff + givenWithPlayoff. | ✓ |
| Nur mit Playoff | Eine Test-Methode mit kompletten Daten. | |
| Nur ohne Playoff | Eine Test-Methode mit minimalen Daten. | |

**User's choice:** Beide Varianten

### JaCoCo-Threshold

| Option | Description | Selected |
|--------|-------------|----------|
| 82% halten | Konsistent mit CLAUDE.md + feedback_coverage_strategy. | ✓ |
| Bump auf gemessenen Wert | Lockt höheres Niveau, aber Tech-Debt für künftige Phasen. | |
| Bump auf 85% (aspirativ) | Erhöht Druck ohne Plan. | |

**User's choice:** 82% halten

### Coverage-Repair-Strategie

| Option | Description | Selected |
|--------|-------------|----------|
| QUAL-01 = Phasen-Erfolgs-Kriterium | Falls < 82%, Plan 61-05 schreibt zusätzliche Tests. | ✓ |
| Threshold temporär senken | Konflikt mit CLAUDE.md. | |

**User's choice:** QUAL-01 = Phasen-Erfolgs-Kriterium

---

## Plan-Strukturierung + Workflow

### Plan-Splitting

| Option | Description | Selected |
|--------|-------------|----------|
| 5 Plans, sequentiell | 61-01 ROADMAP + 61-02 Code-Cleanup + 61-03 V6 + 61-04 QUAL-02 + 61-05 QUAL-03/Coverage. | ✓ |
| 3 Plans, größer | Kombiniert. | |
| Plan-Struktur ist Planner-Aufgabe | Claude's Discretion. | |

**User's choice:** 5 Plans, sequentiell

### Behavior-Change-Tracking

| Option | Description | Selected |
|--------|-------------|----------|
| Ja, explizit tracken | CONTEXT.md + Plan-SUMMARY + PR-Description. | ✓ |
| Nein, im Phasen-SUMMARY reicht | | |

**User's choice:** Ja, explizit tracken

### Worktree

| Option | Description | Selected |
|--------|-------------|----------|
| Sequentiell auf gsd/v1.9-Branch | Konsistent mit Phase 60. Subagent-Stabilität > Speedup. | ✓ |
| Worktree pro QUAL-Plan | Parallel, aber Setup-Overhead + clobber-Risiko. | |
| Claude's Discretion | | |

**User's choice:** Sequentiell

### Milestone-Abschluss

| Option | Description | Selected |
|--------|-------------|----------|
| v1.9 wrap-up nach Phase 61 | Standard-GSD-Workflow: execute → verify → audit-milestone → complete-milestone → ship. | ✓ |
| Erst Doc-Update + UAT | Vor Audit. | |

**User's choice:** v1.9 wrap-up nach Phase 61
**Notes:** Doc-Update läuft parallel oder nach.

### Convenience-API-Status

| Option | Description | Selected |
|--------|-------------|----------|
| First-class behalten | matchday.getSeason() / playoff.getSeason() ohne @Deprecated. | ✓ |
| Mit @Deprecated markieren | Neue @Deprecated-Schuld direkt in Cleanup-Phase. | |
| Komplett entfernen | Widerspricht D-02. | |

**User's choice:** First-class behalten

### Branch-Strategie

| Option | Description | Selected |
|--------|-------------|----------|
| Vor Phase 61 fetch + ggf. rebase | Konsistent mit feedback_branch_from_origin. | |
| gsd/v1.9-Branch unverändert weiterführen | Bricht den Pattern, aber Stabilität priorisiert. | ✓ |

**User's choice:** gsd/v1.9-Branch unverändert weiterführen
**Notes:** Branch hat schon viele Commits; Rebase könnte Konflikte erzeugen. Am Milestone-Ende per `gh pr merge --squash`.

---

## Claude's Discretion

- Exakte SQL-Statement-Reihenfolge in V6 (Tabelle vor FK-Spalten vor Saison-Spalten ist die logische Folge, aber genaue Syntax bleibt offen).
- Test-Helper-Methoden-Naming in TestHelper.java.
- JavaDoc-Wording der Convenience-Getter.
- Wave-Plan-Splitting innerhalb von Plan 61-02 (Compile-Cluster-Größe).
- Konkretes @Sql-Script-Inhalt für QUAL-03-Fixtures.
- Reihenfolge der Standings-Asserts (per-group vs. combined).
- Test-Driver-Ranking-Werte für QUAL-02.
- JaCoCo-Exclusions für neue Convenience-Getter (falls nötig).

## Deferred Ideas

- README + Wiki-Update für v1.9 (nach Phase 61).
- Threshold-Bump > 82% (nicht in Phase 61).
- JaCoCo-Aspirationsziel 85% (future).
- PLAYOFF-FUT-01, CONSOL-FUT-01, IMPORT-FUT-01 (future milestones).
- Manueller Saison-Selector-Dropdown (Phase 60 deferred).
- Optimistic Locking auf Phase-Edit.
- Drag-and-Drop-UI für Tab/Group/Roster.
- Mobile-Dropdown-Navigation.
- Java-V6-Migration mit Pre-Validation.
- Worktree-Parallelisierung 61-04 + 61-05.
- Rebase auf origin/master.
- Testcontainers-MariaDB-Migration-Test.
