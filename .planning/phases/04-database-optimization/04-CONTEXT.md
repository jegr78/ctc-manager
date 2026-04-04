# Phase 4: Database Optimization - Context

**Gathered:** 2026-04-04
**Status:** Ready for planning

<domain>
## Phase Boundary

FK-Indexes auf allen Foreign-Key-Columns per Flyway V2 Migration und @EntityGraph-Annotationen auf allen Listen-Queries fuer optimierte Queries ohne N+1 Probleme. Keine neuen Features, keine Schema-Aenderungen ausser Indexes.

</domain>

<decisions>
## Implementation Decisions

### FK-Index Scope
- **D-01:** Alle ~30 FK-Columns im Schema bekommen einen Index (nicht nur die 10 in CONCERNS.md genannten)
- **D-02:** Nur Single-Column Indexes pro FK-Column — keine Composite Indexes. Bei Bedarf spaeter ergaenzen.

### EntityGraph Strategie
- **D-03:** Breit: Alle findBy*-Repository-Methoden die Collections zurueckgeben bekommen passende @EntityGraph-Annotationen
- **D-04:** Tiefe: Nur 1 Ebene (direkte Beziehungen). Tiefere Navigations bleiben lazy und werden von OSIV aufgeloest.
- **D-05:** Definition: Inline @EntityGraph(attributePaths = {...}) direkt auf den Repository-Methoden — keine @NamedEntityGraph auf Entities

### N+1 Verifikation
- **D-06:** Hibernate SQL-Logging (spring.jpa.show-sql=true) in Tests aktivieren zur manuellen Verifikation. Keine dedizierten Query-Count-Assertion-Tests.

### Flyway Migration
- **D-07:** Universale Migration mit CREATE INDEX IF NOT EXISTS — funktioniert auf H2 und MariaDB (MariaDB hat FK-Indexes schon, IF NOT EXISTS verhindert Fehler)
- **D-08:** Eine einzelne Migrationsdatei: V2__add_fk_indexes.sql — EntityGraphs sind reine JPA-Annotationen ohne Schema-Aenderung

### Claude's Discretion
- Reihenfolge der EntityGraph-Annotationen (welche Repositories zuerst)
- Exakte attributePaths pro Repository-Methode (basierend auf Template-Nutzung)
- Index-Naming-Convention (z.B. idx_{table}_{column})
- Ob show-sql nur temporaer fuer Verifikation oder dauerhaft in Test-Profil

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Database Schema
- `src/main/resources/db/migration/V1__initial_schema.sql` — Bestehendes Schema mit allen FK-Constraints und den 2 existierenden Indexes (idx_cars_gt7id, idx_alias_driver)

### Concerns
- `.planning/codebase/CONCERNS.md` — Abschnitte "No Indexes on Foreign Key Columns" und "OSIV Dependency for Lazy Loading" mit betroffenen Tabellen und Queries

### Entity-Modell (FK-Beziehungen)
- `src/main/java/org/ctc/domain/model/Race.java` — 6 @ManyToOne Beziehungen (matchday, match, car, track, driver, seasonDriver)
- `src/main/java/org/ctc/domain/model/Match.java` — 3 @ManyToOne (matchday, homeTeam, awayTeam)
- `src/main/java/org/ctc/domain/model/Matchday.java` — 1 @ManyToOne (season)
- `src/main/java/org/ctc/domain/model/RaceResult.java` — 2 @ManyToOne (race, driver)
- `src/main/java/org/ctc/domain/model/RaceLineup.java` — 3 @ManyToOne (race, driver, seasonTeam)
- `src/main/java/org/ctc/domain/model/SeasonDriver.java` — 3 @ManyToOne (season, team, driver)
- `src/main/java/org/ctc/domain/model/SeasonTeam.java` — 3 @ManyToOne (season, team, replacedTeam)

### Repositories (EntityGraph-Ziele)
- `src/main/java/org/ctc/domain/repository/` — Alle 21 Repository-Interfaces, insbesondere MatchRepository, RaceRepository, MatchdayRepository, SeasonTeamRepository

### Projekt-Richtlinien
- `CLAUDE.md` — Flyway-Migrationsregeln, OSIV-Policy, H2+MariaDB Kompatibilitaet

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- Keine bestehenden @EntityGraph-Annotationen im Projekt — alles wird neu eingefuehrt
- Flyway-Setup funktioniert (V1 Migration laeuft auf H2 und MariaDB)
- Spring Data JPA Repositories nutzen Standard-findBy*-Methoden

### Established Patterns
- Alle Entities nutzen FetchType.LAZY fuer @ManyToOne (explizit gesetzt)
- OSIV ist aktiv — Templates navigieren lazy ueber Beziehungen
- Repositories nutzen Spring Data Query-Derivation (findBySeasonId, findByMatchdaySeasonId etc.)

### Integration Points
- V2 Migration wird automatisch von Flyway beim Start ausgefuehrt (nach V1)
- @EntityGraph-Annotationen erfordern keine Aenderungen an Services oder Controllern
- Bestehende Tests (DataJpaTest) nutzen H2 — V2 Migration muss dort laufen

</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 04-database-optimization*
*Context gathered: 2026-04-04*
