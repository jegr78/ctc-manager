# Phase 4: Database Optimization - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-04
**Phase:** 04-database-optimization
**Areas discussed:** FK-Index Scope, EntityGraph Tiefe, N+1 Verifikation, H2/MariaDB Kompatibilitaet

---

## FK-Index Scope

| Option | Description | Selected |
|--------|-------------|----------|
| Alle FK-Columns | Alle ~30 FK-Beziehungen im Schema bekommen einen Index. Komplett, kein Nachdenken noetig. | ✓ |
| Nur haeufig abgefragte | Nur die 10 in CONCERNS.md genannten. Spart Indexes, muss nachgepflegt werden. | |
| Priorisiert in Stufen | Erst kritische, dann Rest in zweiter Migration. Inkrementell testbar. | |

**User's choice:** Alle FK-Columns
**Notes:** Standard-Practice, minimaler Overhead bei kleinem Datenvolumen.

| Option | Description | Selected |
|--------|-------------|----------|
| Nur Single-Column | Ein Index pro FK-Column. Einfach, vorhersehbar. | ✓ |
| Auch Composite wo sinnvoll | Zusaetzlich 2-3 Composite Indexes fuer bekannte Query-Patterns. | |

**User's choice:** Nur Single-Column
**Notes:** Composite Indexes bei Bedarf spaeter ergaenzen.

---

## EntityGraph Tiefe

| Option | Description | Selected |
|--------|-------------|----------|
| Gezielt: Top-N+1-Hotspots | Nur 3-5 Repository-Methoden mit offensichtlichsten N+1 Problemen. | |
| Breit: Alle Listen-Queries | Jede findBy*-Methode die Collections zurueckgibt bekommt @EntityGraph. | ✓ |
| Nur Match-Beziehungen | Fokus auf Match→homeTeam/awayTeam (Standings-Berechnung). | |

**User's choice:** Breit: Alle Listen-Queries
**Notes:** Umfassender Ansatz, deckt auch weniger offensichtliche N+1s ab.

| Option | Description | Selected |
|--------|-------------|----------|
| 1 Ebene tief | Nur direkte Beziehungen laden. Kein Risiko von Cartesian Products. | ✓ |
| 2 Ebenen wo noetig | Bei bekannten Template-Traversals 2 Ebenen laden. | |
| Du entscheidest | Claude analysiert Templates und waehlt passende Tiefe pro Query. | |

**User's choice:** 1 Ebene tief
**Notes:** Tiefere Navigations bleiben lazy und werden bei Bedarf von OSIV aufgeloest.

| Option | Description | Selected |
|--------|-------------|----------|
| Inline attributePaths | @EntityGraph(attributePaths = {...}) direkt auf Repository-Methode. | ✓ |
| @NamedEntityGraph auf Entity | Zentrale Graph-Definition auf Entity-Klasse. | |
| Du entscheidest | Claude waehlt passenden Ansatz je nach Situation. | |

**User's choice:** Inline attributePaths
**Notes:** Graph-Definition direkt am Nutzungsort sichtbar.

---

## N+1 Verifikation

| Option | Description | Selected |
|--------|-------------|----------|
| Hibernate SQL-Log im Test | show-sql=true in Tests, Query-Anzahl manuell pruefen. | ✓ |
| Query-Count Assertions | Dedizierte Tests mit DataSource-Proxy die Query-Anzahl assertieren. | |
| Nur manuelle Pruefung | Einmalig mit SQL-Log verifizieren, kein dauerhafter Test. | |

**User's choice:** Hibernate SQL-Log im Test
**Notes:** Kein extra Test-Code noetig, reicht fuer Verifikation in der Success Criteria.

---

## H2/MariaDB Kompatibilitaet

| Option | Description | Selected |
|--------|-------------|----------|
| Universal mit IF NOT EXISTS | CREATE INDEX IF NOT EXISTS fuer alle FK-Columns. Idempotent. | ✓ |
| Separate Migrationen pro DB | Flyway-Profil-Steuerung fuer H2 und MariaDB. | |
| Nur Standard-SQL | CREATE INDEX ohne IF NOT EXISTS. | |

**User's choice:** Universal mit IF NOT EXISTS
**Notes:** MariaDB hat FK-Indexes schon, IF NOT EXISTS verhindert Fehler.

| Option | Description | Selected |
|--------|-------------|----------|
| Eine V2-Datei | Alle FK-Indexes in V2__add_fk_indexes.sql. | ✓ |
| V2 + V3 getrennt | V2 fuer Indexes, V3 reserviert fuer Schema-Aenderungen. | |

**User's choice:** Eine V2-Datei
**Notes:** EntityGraphs sind reine JPA-Annotationen ohne Schema-Aenderung.

---

## Claude's Discretion

- Reihenfolge der EntityGraph-Annotationen (welche Repositories zuerst)
- Exakte attributePaths pro Repository-Methode
- Index-Naming-Convention
- show-sql Konfiguration (temporaer vs. dauerhaft)

## Deferred Ideas

None — discussion stayed within phase scope
