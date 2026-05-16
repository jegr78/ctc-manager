# Phase 72: Backup Wire Contract — Schema, Manifest, ObjectMapper, Audit-Log Scope - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in 72-CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-11
**Phase:** 72-backup-wire-contract-schema-manifest-objectmapper-audit-log-
**Areas discussed:** Flyway-Version V4 vs V7 (resolved without input), Export-Scope (Car/Track + FeatureSettings), EXPORT_ORDER Generation-Mechanismus, data_import_audit Exclusion + Entity creation

---

## Area 1 — Flyway-Version V4 vs V7

**Resolved without user input.** Initial codebase scout (`ls src/main/resources/db/migration/`) returned only `V1`/`V2`/`V3`, suggesting a gap with REQUIREMENTS.md's `V7__data_import_audit.sql` naming. Deeper scout (`find . -name "V[0-9]*__*.java"`) revealed V4/V5/V6 exist as **dialect-aware Java migrations** under `src/main/java/db/migration/`:

- `V4__MigrateSeasonsToPhases.java`
- `V5__NullableLegacyScoringColumns.java`
- `V6__CleanupLegacySeasonColumns.java`

Conversion from SQL to Java migrations was committed in `6db56d4 fix(61-uat-03): convert V5 and V6 to dialect-aware Java migrations`. V7 is the correct next-available number — matches REQUIREMENTS.md SCHEMA-03 exactly. No user input needed.

**Outcome:** `V7__data_import_audit.sql` (pure SQL) is the correct file. See CONTEXT.md D-10 for the SQL-vs-Java rationale.

---

## Area 2 — Export-Scope: Master Data (Car/Track) + Missing FeatureSettings

### Question 2a: Car / Track in EXPORT_ORDER?

| Option | Description | Selected |
|--------|-------------|----------|
| Out-of-Scope: gt7sync ist die Quelle | Car/Track NICHT exportiert; Round-Trip-Annahme: Target-DB hat dieselben gt7sync-Daten | |
| Mit-Exportieren in EXPORT_ORDER | Car/Track werden Teil der Liste → 24 Entities. Round-Trip self-contained, kein gt7sync auf Target nötig | ✓ |
| Hybrid: Soft-FK Lookup-by-name | Race exportiert manufacturer+name strings statt FK-IDs | |

**User's choice:** Mit-Exportieren — fully self-contained round-trip.
**Notes:** Round-trip robustness wins over master-data purity. Car/Track are FK-leaves so topology cost is zero (they sort to the head of EXPORT_ORDER). Two extra MixIns + two extra `DELETE FROM` statements in later phases are an acceptable trade for round-trip determinism.

### Question 2b: Missing `FeatureSettings` entity?

| Option | Description | Selected |
|--------|-------------|----------|
| Aus 22er-Liste streichen → 21 Entities | REQUIREMENTS/ROADMAP-Update auf 21; spätere Einführung ist Schema-Bump | ✓ |
| Phase 72 erstellt das Entity | Leeres Stub-Entity + Migration in Phase 72 | |
| Future-Phase liefert nach | EXPORT_ORDER startet mit 21; spätere v1.11+ Phase bumpt 1 → 2 | |

**User's choice:** Aus 22er-Liste streichen.
**Notes:** Cleaner semver semantics — introducing `FeatureSettings` later is an explicit, traceable `SCHEMA_VERSION` bump rather than a silent stub.

**Combined outcome (D-01/D-02/D-03 in CONTEXT.md):** Final `EXPORT_ORDER` contains **23 entities** = 21 from original REQUIREMENTS list − FeatureSettings + Car + Track. REQUIREMENTS.md EXPORT-04 wording will be overridden via the D-XX traceable mechanism (not silently rewritten).

---

## Area 3 — EXPORT_ORDER Generation Mechanism

### Question 3a: Generation mechanism (GAP-5 forbids hand-list)?

| Option | Description | Selected |
|--------|-------------|----------|
| JPA `Metamodel` topo-sort | EntityManagerFactory.getMetamodel().getEntities() durchlaufen, @ManyToOne als Edges sammeln, Kahn-Topo-Sort. JPA-portabel | ✓ |
| Hibernate SessionFactoryImplementor | Hibernate-spezifische DDL-Drop-Order; API-fragil bei Spring-Boot-Bumps | |
| Reflection-only auf @ManyToOne | Classpath-Scan + reflection auf @ManyToOne/@JoinColumn-Felder | |

**User's choice:** JPA Metamodel topo-sort.
**Notes:** JPA-portable; survives Hibernate version bumps that ship with future Spring Boot versions. No Hibernate-internal API access.

### Question 3b: `BackupSchema` class shape?

| Option | Description | Selected |
|--------|-------------|----------|
| @Component mit static + @PostConstruct | Eine Klasse: `public static final SCHEMA_VERSION = 1` + bean-instance `exportOrder` populiert in @PostConstruct | ✓ |
| Split: BackupSchema (constants) + EntityRegistry (bean) | Saubere Trennung Wert-vs-Logik, aber zwei Klassen | |
| Pure static class via reflection on classpath scan | Klein, kein Spring-Lifecycle, aber neue Dependency (Reflections-Library) | |

**User's choice:** Single `BackupSchema` `@Component`.
**Notes:** Coherent single class is preferred for the downstream API surface — `@Autowired BackupSchema` gives both `SCHEMA_VERSION` (via static reference) and `getExportOrder()` (via bean reference). No new dependencies.

---

## Area 4 — `data_import_audit` Exclusion + Entity Creation

### Question 4a: Structural guarantee for IMPORT-08 (audit never in export)?

| Option | Description | Selected |
|--------|-------------|----------|
| Package-Filter: scant nur `org.ctc.domain.model.*` | Topo-sort restricts to domain-model package. Strukturelle Barriere ohne Marker-Annotation | ✓ |
| Marker-Interface `@BackupExcluded` Annotation | Annotation auf Klassen-Ebene; topo-sort skipped @BackupExcluded-annotierte Entities. Explizit aber leichter zu vergessen | |
| Pure-JdbcTemplate: kein JPA-Entity erstellen | data_import_audit nur als Flyway-Tabelle, kein @Entity. Bricht CTC-Konvention | |

**User's choice:** Package-Filter.
**Notes:** Convention-driven exclusion; no marker annotation; future infrastructure entities under `org.ctc.backup.*` are excluded by default. Strongest structural guarantee that aligns with package hygiene.

### Question 4b: `DataImportAudit` JPA entity in Phase 72?

| Option | Description | Selected |
|--------|-------------|----------|
| Ja, Phase 72: Entity + Repository unter `org.ctc.backup.audit` | Coherent unit (migration + entity + repo); Phase 75 nutzt das Repository | ✓ |
| Nein, Phase 75 entscheidet später | Phase 72 nur Flyway-Tabelle | |

**User's choice:** Ja — entity + repository in Phase 72.
**Notes:** Bundling migration + entity + repository in the same phase keeps the audit-table architecture as a single coherent unit. Phase 75 then `@Autowired`s `DataImportAuditRepository` for writes without re-deciding the data layer. Entity deliberately does NOT extend `BaseEntity` (per CONTEXT.md D-08) — Phase 75 sets `executed_at` explicitly, and AuditingEntityListener interference is exactly what IMPORT-05 is bypassing.

---

## Claude's Discretion

- **`EntityRef`-record field set** beyond `entityClass` / `tableName` / `fileName` (e.g., `@EntityGraph` name) — planner adds if Phase 73 benefits.
- **Whether `BackupSchema.@PostConstruct` extracts a separate `EntityTopoSorter` helper** vs. inlining Kahn's algorithm — planner's call for testability.
- **`BackupObjectMapperConfig` MixIn-injection mechanism** — Spring `List<Module>` vs `Map<Class<?>, Class<?>>` injection — planner picks based on Phase 73 MixIn registration ergonomics.
- **`DataImportAudit` lombok/record-style choice** — both acceptable; planner picks based on CTC entity-style convention (current entities use Lombok class form).
- **IT class location and split granularity** within `src/test/java/org/ctc/backup/...`.
- **`DataImportAuditRepository` finder method additions** (`findTop10ByOrderByExecutedAtDesc()` for future admin UI) — Phase 72 only needs default `JpaRepository` surface; planner may add or defer.

## Deferred Ideas

- **`FeatureSettings` entity introduction** — would be a deliberate `SCHEMA_VERSION` 1 → 2 bump in a future milestone, not silently added to a stub now.
- **Per-Saison-Export/Import selectivity** (REQUIREMENTS.md `EXPORT-FUT-01` / `IMPORT-FUT-01`) — v1.11+.
- **SHA-256 manifest checksum + verify-only import mode** (REQUIREMENTS.md `EXPORT-FUT-02` / `IMPORT-FUT-02`) — v1.11+.
- **Admin audit-log viewer UI** backed by `DataImportAuditRepository` — out of v1.10 scope, but the repository surface is ready.
- **Hybrid soft-FK (lookup-by-name) for Car/Track** — explicitly rejected for v1.10 in favor of full hard-FK export. Could revisit in v1.11+ if master-data drift between environments becomes a real pain point.
- **Hibernate-internal DDL-drop-order** as alternative topo source — rejected as too API-fragile across Spring Boot version bumps.
