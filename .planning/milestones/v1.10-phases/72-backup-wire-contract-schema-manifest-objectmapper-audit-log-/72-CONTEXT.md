# Phase 72: Backup Wire Contract — Schema, Manifest, ObjectMapper, Audit-Log Scope - Context

**Gathered:** 2026-05-11
**Status:** Ready for planning

<domain>
## Phase Boundary

Lock the on-disk wire contract for the v1.10 backup ZIP *before* any export or import code is written. Phase 72 delivers six concrete artefacts:

1. `BackupSchema` `@Component` exposing `public static final int SCHEMA_VERSION = 1` and a Spring-managed `List<EntityRef> exportOrder` populated at startup by FK-respecting topological sort of the JPA `Metamodel`.
2. `BackupManifest` record (`schema_version` int, `app_version` String, `export_date` Instant, `table_counts` Map<String, Long>) — wire-format spec, no I/O yet.
3. `BackupObjectMapperConfig` `@Configuration` registering a `@Qualifier("backupObjectMapper")` `ObjectMapper` bean (FAIL_ON_UNKNOWN_PROPERTIES=true, WRITE_DATES_AS_TIMESTAMPS=false, JavaTimeModule), isolated from the auto-configured default `ObjectMapper`. Zero MixIns registered in Phase 72 — registration mechanism is wired so Phase 73 can hook the ~22 per-entity MixIns in.
4. Flyway `V7__data_import_audit.sql` migration creating the `data_import_audit` table (H2 + MariaDB compatible).
5. JPA entity `DataImportAudit` + `DataImportAuditRepository` under `org.ctc.backup.audit` package — empty operative surface in Phase 72 (no writes yet; Phase 75 writes audit rows).
6. PROJECT.md Decisions row anchoring "data_import_audit is permanently out of export scope" + a canonical-wire-contract section documenting per-entity ZIP layout, integer schema versioning, and the 23-entity export-scope list.

**Out of scope** (deferred to Phase 73+): the per-entity Jackson MixIns themselves; `BackupExportService` / `BackupImportService`; the `/admin/backup` controller/templates; ZIP streaming; ZipSlip/ZipBomb defenses; the read-only-mode `@ControllerAdvice`; round-trip ITs (`BackupRoundTripIT`, `BackupImportRollbackIT`); README + WIKI documentation.

</domain>

<decisions>
## Implementation Decisions

### Export Scope (overrides REQUIREMENTS.md EXPORT-04 wording)

- **D-01:** **`Car` and `Track` ARE included in `EXPORT_ORDER`.** The 22-entity REQUIREMENTS.md EXPORT-04 list (which excluded both as gt7sync-managed master data) is **overridden** in favor of a fully self-contained round-trip. Round-trip semantics: a backup taken on environment A imports cleanly on environment B *without* requiring gt7sync to have been run on B first. Two extra Jackson MixIns (`CarMixIn`, `TrackMixIn`) land in Phase 73; two extra `DELETE FROM cars` / `DELETE FROM tracks` statements land in Phase 75. **Topology:** Car and Track are FK-leaves with no incoming dependencies — topo-sort places them at the head of `EXPORT_ORDER`, before `Race`.
- **D-02:** **`FeatureSettings` is DROPPED from the export-scope list.** The class does not exist anywhere in `src/main/java/org/ctc/domain/model/` (verified by `find`). Adding it now would force creating an unused stub entity + migration in Phase 72; the cleaner path is to treat its future introduction as a deliberate `SCHEMA_VERSION` bump (1 → 2) when it ships. REQUIREMENTS.md EXPORT-04 wording is overridden to remove the trailing `FeatureSettings`.
- **D-03 (derived from D-01 + D-02):** Final entity count in `EXPORT_ORDER` is **23 entities** (21 REQUIREMENTS.md entries − FeatureSettings + Car + Track). The 23 are:

  `Car, Track, Season, SeasonPhase, SeasonPhaseGroup, Team, PhaseTeam, SeasonTeam, Driver, SeasonDriver, PsnAlias, RaceScoring, MatchScoring, RaceSettings, Matchday, Match, Race, RaceLineup, RaceResult, RaceAttachment, Playoff, PlayoffMatchup, PlayoffSeed`

  (Order above is illustrative; the authoritative order is the runtime topo-sort output — see D-04.)

- **D-03 amendment (2026-05-11, post-research):** RESEARCH OQ-1 identified `PlayoffRound` (`@Entity @Table(name="playoff_rounds")` with `@ManyToOne Playoff`) as a 24th operative entity under `org.ctc.domain.model`. The runtime topo-sort over `Metamodel.getEntities()` will surface 24, not 23. Authorized final entity count: **24** (the 23 above PLUS `PlayoffRound`, which sits between `Playoff` and `PlayoffMatchup` in topo-order). `BackupSchemaTopologyIT` asserts `hasSize(24)`; PROJECT.md "Backup Wire Contract (v1.10)" subsection documents 24 entities.

### EXPORT_ORDER Generation (GAP-5 resolution)

- **D-04:** **JPA `Metamodel` topological sort.** `BackupSchema.@PostConstruct` reads `entityManagerFactory.getMetamodel().getEntities()`, restricts the candidate set to classes whose package starts with `org.ctc.domain.model` (see D-06), then walks each `EntityType<?>`'s singular attributes — for each attribute whose persistent type is `MANY_TO_ONE` or `ONE_TO_ONE` (owning side only, i.e. `mappedBy` is null), it records a directed edge `dependency → owner`. Kahn's algorithm produces an FK-respecting topo-sort. The `Team.parentTeam` self-FK (cycle of length 1) is detected and excluded from the edge set so Kahn does not deadlock — `Team` appears once at its dependency depth, and Phase 75 handles the self-link via the IMPORT-05 `UPDATE teams SET parent_team_id = NULL` pre-step. JPA-portable; no Hibernate-internal API access; survives Hibernate version bumps.
- **D-05:** **`BackupSchema` class shape:** single `@Component` class. `SCHEMA_VERSION` is `public static final int`; `exportOrder` is a `private List<EntityRef> exportOrder` Spring-bean field, immutably assigned in `@PostConstruct` via `List.copyOf(topoSort(...))` and exposed via `public List<EntityRef> getExportOrder()`. Downstream services `@Autowire` `BackupSchema` and call the getter — they never construct the list themselves. The `static final` constant + bean-managed list cohabit cleanly: Phase 73's `BackupExportService` reads both via the same `BackupSchema` reference.
- **D-06:** **Package-scope filter for IMPORT-08 enforcement.** Topo-sort restricts the candidate entity set to `clazz.getPackage().getName().startsWith("org.ctc.domain.model")`. Any future entity placed under `org.ctc.backup.*` (audit, staging, lock-state, etc.) is *structurally* excluded from `EXPORT_ORDER` — no marker annotation, no opt-in list, no developer memory required. This is the canonical exclusion mechanism for IMPORT-08.
- **D-07:** **`EntityRef` shape (Claude's discretion within these constraints):** Java record with `Class<?> entityClass`, `String tableName` (read from `@Table(name=...)`, fall back to JPA Inspector if absent), `String fileName` (kebab-case derived from `tableName` → `season_phases` becomes `data/season-phases.json`, matching EXPORT-03's examples). Planner may add fields if Phase 73 needs them (e.g., the `@EntityGraph` name), but the three above are mandatory.

### `data_import_audit` Architecture

- **D-08:** **JPA entity + repository land in Phase 72.** `DataImportAudit` (record-style entity is preferred but Lombok class is acceptable) + `DataImportAuditRepository extends JpaRepository<DataImportAudit, UUID>` are created in package `org.ctc.backup.audit`. Phase 72 ships them empty (no controller, no service) — Phase 75 wires `DataImportAuditRepository.save(...)` into the `BackupImportService` transaction. **Entity does NOT extend `BaseEntity`** (no `@CreatedDate`/`@LastModifiedDate` auditing — the audit row's `executed_at` is set explicitly by the writer, and `AuditingEntityListener` interference is exactly what Phase 75 IMPORT-05 is bypassing).
- **D-09:** **Flyway V7 column types are H2-and-MariaDB-portable plain text columns.** Use `LONGTEXT` (alias on both DBs; MariaDB native, H2 since 2.x accepts as alias for CLOB) for `table_counts_wiped` and `table_counts_restored`. Native `JSON` type is intentionally **rejected** for v1.10 — MariaDB's `JSON` is itself a `LONGTEXT` alias with `CHECK JSON_VALID(...)`; H2's `JSON` is younger and validates differently. Keeping it textual avoids dialect drift; the JSON shape is enforced at write-time by Jackson serialization, not at the DB column level. Column DDL:
  ```sql
  CREATE TABLE data_import_audit (
      id UUID PRIMARY KEY,
      executed_at TIMESTAMP NOT NULL,
      executed_by VARCHAR(255) NOT NULL,
      schema_version INT NOT NULL,
      table_counts_wiped LONGTEXT NOT NULL,
      table_counts_restored LONGTEXT NOT NULL,
      source_filename VARCHAR(255) NOT NULL,
      success BOOLEAN NOT NULL
  );
  ```
  (Final DDL is planner's responsibility — this is the canonical column-type lock.)
- **D-10:** **No `V7` Java-migration variant — pure SQL.** V4/V5/V6 are dialect-aware Java migrations because they perform data-shape transformations (column nullables, deletes); V7 is a plain `CREATE TABLE` with portable column types (D-09), so it stays SQL (`V7__data_import_audit.sql`). Mirrors the V1/V2/V3 pattern.

### `BackupObjectMapperConfig` Isolation

- **D-11:** **Isolation via `@Qualifier("backupObjectMapper")` + explicit `new ObjectMapper()`.** The `@Configuration` `@Bean` method instantiates a **new** `ObjectMapper` (NOT `Jackson2ObjectMapperBuilder.json().build()` cloning the default), configures `FAIL_ON_UNKNOWN_PROPERTIES=true`, `WRITE_DATES_AS_TIMESTAMPS=false`, registers `JavaTimeModule`, and tags the bean with `@Qualifier("backupObjectMapper")`. The default Spring `ObjectMapper` (auto-configured by `JacksonAutoConfiguration`) remains untouched and continues to serve admin REST/AJAX paths. An IT (Failsafe profile) injects both beans (`@Autowired ObjectMapper defaultMapper; @Autowired @Qualifier("backupObjectMapper") ObjectMapper backupMapper`) and asserts they are different instances + configured differently.
- **D-12:** **MixIn-registration hook in place, zero MixIns in Phase 72.** The `@PostConstruct` (or `@Bean` body) of `BackupObjectMapperConfig` accepts a `List<Module>` or a `Map<Class<?>, Class<?>>` of MixIns via Spring DI. In Phase 72 the list/map is empty (no MixIn beans exist yet). Phase 73 adds the ~22 per-entity MixIn `@Component` beans, and they get picked up automatically without modifying `BackupObjectMapperConfig`. This keeps the Phase 72 / Phase 73 seam clean: zero Phase 72 code touches after Phase 73 ships.

### `BackupManifest` Wire Spec

- **D-13:** **`BackupManifest` is a pure Java `record`.** Fields: `int schemaVersion`, `String appVersion`, `Instant exportDate`, `Map<String, Long> tableCounts`. Jackson serializes records natively in Spring Boot 4 / Jackson 2.18+. `appVersion` is sourced from Spring Boot's `BuildProperties` (requires the `spring-boot-maven-plugin` `build-info` goal — verify wiring during planning; CTC may need the `<execution><goals><goal>build-info</goal>...` addition to pom.xml). `tableCounts` key is the **snake_case `@Table(name=...)`** (e.g., `seasons`, `season_phases`, `race_lineups`) — same key shape that Phase 73 uses for `data/<entity>.json` filename derivation.
- **D-14:** **"manifest.json is FIRST entry in ZIP" determinism is owned by Phase 73, not 72.** Phase 72 ships only the record definition. Phase 73's `BackupExportService` is responsible for the `ZipOutputStream.putNextEntry(new ZipEntry("manifest.json"))` write-order discipline. Phase 72 documents this contract in the canonical-wire-contract section of PROJECT.md (success criterion 2).

### PROJECT.md Canonical-Wire-Contract Documentation

- **D-15:** **PROJECT.md gets two additions in Phase 72:**
  1. A new row in the `## Key Decisions` table: `| data_import_audit out of export scope | Audit log is operational metadata about migrations, not league data — survives every import for traceability | ✓ v1.10 |`.
  2. A new short section `### Backup Wire Contract (v1.10)` capturing the four locked choices: integer SCHEMA_VERSION (GAP-2 resolution), per-entity `data/<entity>.json` ZIP layout with `manifest.json` first (GAP-1 resolution), `EXPORT_ORDER` is JPA-Metamodel-generated topo-sort over `org.ctc.domain.model.*` (GAP-5 resolution), and the 23-entity scope including Car/Track and excluding FeatureSettings (D-01/D-02/D-03 overrides).

### Test Plan for Phase 72

- **D-16:** **Five new tests, all Failsafe (IT) profile.**
  1. `BackupSchemaTopologyIT` — boots Spring context, asserts `BackupSchema.getExportOrder()` returns exactly 23 entities, asserts every `@ManyToOne` dependency precedes its owner in the list, asserts the list is immutable (`List.copyOf`).
  2. `BackupSchemaExclusionIT` — asserts `data_import_audit` / `DataImportAudit` is **NOT** in `getExportOrder()` (proves IMPORT-08 structural guarantee).
  3. `BackupObjectMapperConfigIT` — injects both `ObjectMapper` qualifiers, asserts they are different instances (`!=`), asserts default mapper has `FAIL_ON_UNKNOWN_PROPERTIES=false` (Spring default), asserts backup mapper has it `=true`.
  4. `BackupManifestSerializationTest` (Surefire — pure unit) — serializes a sample `BackupManifest` through the backup mapper, asserts JSON shape (`schema_version` snake_case, `export_date` ISO-8601 string, `table_counts` JSON object).
  5. `V7DataImportAuditMigrationIT` — boots Spring against an H2 fresh schema, queries `data_import_audit` columns via JDBC metadata, asserts columns + types. (MariaDB equivalent runs in the existing `mariadb-migration-smoke.yml` workflow analog to v1.9 — no new MariaDB-specific IT in Phase 72.)
- **D-17:** **No `BackupRoundTripIT` / `BackupImportRollbackIT` in Phase 72.** Those land in Phase 77 / 75 respectively, when there is actual export + import code to round-trip.

### Claude's Discretion

- `EntityRef`-record field additions beyond the three locked in D-07 (planner can add `@EntityGraph` name or per-entity export-order index if Phase 73 design benefits).
- Whether `BackupSchema.@PostConstruct` uses a separate `EntityTopoSorter` helper class (testability win) or inlines the Kahn algorithm — planner's call.
- IT class location: `src/test/java/org/ctc/backup/...` mirroring the production package layout is the obvious choice; planner may consolidate or split.
- DDL formatting / index addition on `data_import_audit` (only `executed_at` is a plausible query column; no FK indexes needed since the table has no FKs).
- Whether `DataImportAuditRepository` declares additional finder methods (`findTop10ByOrderByExecutedAtDesc()` for a future admin UI) — Phase 72 only needs default `JpaRepository` surface.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### v1.10 milestone foundation
- `.planning/ROADMAP.md` §"Phase 72" — Goal, depends-on, success criteria (5 SCs), requirements list (SCHEMA-01..04, IMPORT-08)
- `.planning/REQUIREMENTS.md` §SCHEMA-01..04 + §IMPORT-08 — Wire-contract acceptance criteria. **NOTE:** EXPORT-04 entity list in REQUIREMENTS is overridden by D-01/D-02/D-03 of this CONTEXT — final entity count is 23, not 22.
- `.planning/research/SUMMARY.md` §"Phase 2" — Synthesized research; rationale for `BackupSchema` / `BackupManifest` / `BackupObjectMapperConfig` / V7 cluster as a single coherent unit
- `.planning/research/STACK.md` §"Audit logging" (L135) + §"V7 migration" (L391) — Confirms entity-based audit pattern + V7 numbering
- `.planning/research/PITFALLS.md` §Pitfall 9 (audit-log scope), §Pitfall on V7 schema bump (L233+), §Pitfall on Flyway + audit table inclusion (L468) — Provides the rationale for IMPORT-08 + the package-filter exclusion mechanism

### Prior-phase context (carries forward)
- `.planning/phases/71-spring-boot-4-0-6-upgrade-thymeleaf-3-1-5-template-audit-bui/71-CONTEXT.md` — Phase 71 ships a clean 4.0.6 / Thymeleaf 3.1.5 baseline; Phase 72 inherits that baseline with no further upgrade work
- `.planning/STATE.md` §"Accumulated Context" — `data_import_audit` listed as new file under `Flyway V7__data_import_audit.sql`; full v1.10 file inventory

### Project conventions (mandatory reading)
- `CLAUDE.md` §"Constraints" — Coverage ≥ 82 %, do not modify existing Flyway migrations, OSIV enabled, no breaking URL changes
- `CLAUDE.md` §"Architectural Principles" — Controller→Service→Repository, DTOs in controllers, no fallback calculations; Phase 72 has no controllers/templates but `DataImportAudit` repo follows the same pattern
- `CLAUDE.md` §"Do Not Modify Flyway Migrations" — V1-V6 untouched; V7 is the only Flyway addition
- `.planning/codebase/CONVENTIONS.md` — `JpaRepository` everywhere (only existing `JdbcTemplate` usage is in `V4__MigrateSeasonsToPhases.java`); `DataImportAuditRepository` follows convention
- `.planning/codebase/TESTING.md` — Surefire (Unit) vs Failsafe (IT) split; `@SpringBootTest` profile for Failsafe; JaCoCo 82 % gate
- `.planning/codebase/STACK.md` — Pre-bump dependency snapshot (still applies; Phase 72 adds zero dependencies)
- `.planning/codebase/STRUCTURE.md` — Package layout; new `org.ctc.backup.*` package tree (`org.ctc.backup.schema`, `org.ctc.backup.audit`) follows the convention

### Existing code that Phase 72 references but does NOT modify
- `src/main/java/org/ctc/domain/model/*.java` — All 23 entity classes (read-only; no annotation drift per EXPORT-04 spirit)
- `src/main/java/org/ctc/domain/model/BaseEntity.java` — `@CreatedDate`/`@LastModifiedDate` auditing; Phase 72 documents that `DataImportAudit` deliberately does NOT extend this
- `src/main/java/db/migration/V4__MigrateSeasonsToPhases.java`, `V5__NullableLegacyScoringColumns.java`, `V6__CleanupLegacySeasonColumns.java` — Precedent for Java-based Flyway migrations; not used in Phase 72 (V7 stays SQL per D-10)
- `pom.xml` — `spring-boot-maven-plugin` may need a `<execution><goals><goal>build-info</goal>...` addition to populate `BuildProperties` for `BackupManifest.appVersion` (D-13). Verify during planning.

### External (consulted, not on-disk)
- JPA 3.0 `Metamodel` API — `EntityManagerFactory.getMetamodel().getEntities()` + `SingularAttribute.getPersistentAttributeType()` for ManyToOne/OneToOne detection (D-04)
- Spring Boot 4.0.6 `BuildProperties` — sourced from `META-INF/build-info.properties`, populated by `spring-boot-maven-plugin:build-info` goal
- Kahn's algorithm — standard topo-sort; trivial Java implementation

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`EntityManagerFactory`** — already wired into the Spring context for every `@Service` that does JPA queries. `BackupSchema.@PostConstruct` injects it (constructor injection via `@RequiredArgsConstructor` lombok), no new infrastructure needed.
- **Default `ObjectMapper`** — auto-configured by Spring Boot's `JacksonAutoConfiguration`. No custom `ObjectMapper` bean exists in `src/main/java/` (verified by grep). The `backupObjectMapper` bean coexists trivially via `@Qualifier`.
- **Flyway V4/V5/V6 Java-migration pattern** — `src/main/java/db/migration/V4__MigrateSeasonsToPhases.java` (and V5/V6) demonstrate the dialect-aware path; V7 deliberately stays SQL (D-10) because it is a pure `CREATE TABLE` with portable types.
- **`JpaRepository` extension pattern** — every existing repository under `src/main/java/org/ctc/domain/repository/` extends `JpaRepository<Entity, UUID>` with method-name-derived queries. `DataImportAuditRepository` mirrors the convention but lives in `org.ctc.backup.audit` (not `domain.repository`) to enforce the package-filter exclusion rule D-06.

### Established Patterns
- **Constructor injection via Lombok `@RequiredArgsConstructor`** (CLAUDE.md §"Lombok Usage") — `BackupSchema` and `BackupObjectMapperConfig` follow this pattern.
- **`@Configuration` + `@Bean` for explicit bean construction** — used in `SecurityConfig`, `WebConfig`, `OpenSecurityConfig`. `BackupObjectMapperConfig` follows the same shape.
- **English UI text + English code comments** (CLAUDE.md §"Language") — all backup-module comments, JavaDoc, and future UI strings are English.
- **Test naming Given-When-Then** (CLAUDE.md §"Test Naming") — all 5 new tests follow `givenContext_whenAction_thenExpectedResult()`.
- **`@SpringBootTest` for Failsafe ITs** — standard CTC IT bootstrap; both `BackupSchemaTopologyIT` and `BackupObjectMapperConfigIT` use it.

### Integration Points
- **New package: `org.ctc.backup`** — Phase 72 creates the package tree. Subpackages:
  - `org.ctc.backup.schema` → `BackupSchema` (`@Component`), `BackupManifest` (record), `EntityRef` (record).
  - `org.ctc.backup.config` → `BackupObjectMapperConfig` (`@Configuration`).
  - `org.ctc.backup.audit` → `DataImportAudit` (entity), `DataImportAuditRepository`.
- **Flyway:** new `src/main/resources/db/migration/V7__data_import_audit.sql`. No modifications to V1-V6.
- **pom.xml:** verify `spring-boot-maven-plugin` `build-info` goal is wired (needed for `BackupManifest.appVersion`). If not present, add `<execution><goals><goal>build-info</goal></goals></execution>` under the existing plugin block.
- **PROJECT.md:** insert Key Decisions row + new "Backup Wire Contract (v1.10)" subsection (D-15).
- **REQUIREMENTS.md:** insert a footnote or inline note on EXPORT-04 referencing D-01/D-02/D-03 overrides in `72-CONTEXT.md`. **Do not silently rewrite** the spec — keep the override traceable.
- **New tests:** `src/test/java/org/ctc/backup/schema/BackupSchemaTopologyIT.java`, `BackupSchemaExclusionIT.java`, `BackupManifestSerializationTest.java`; `src/test/java/org/ctc/backup/config/BackupObjectMapperConfigIT.java`; `src/test/java/db/migration/V7DataImportAuditMigrationIT.java`.

</code_context>

<specifics>
## Specific Ideas

- **`Team.parentTeam` self-FK is the only known graph anomaly** — Kahn's algorithm needs to detect and skip the self-edge so `Team` does not deadlock at depth 0. Planner: add a regression unit test in `BackupSchemaTopologyIT` that asserts `Team` appears exactly once and at the correct depth.
- **Snake-case ↔ kebab-case filename derivation** — `season_phases` → `season-phases.json` matches EXPORT-03 examples exactly. Implementation: `tableName.replace('_', '-') + ".json"`. Locked.
- **`SCHEMA_VERSION = 1` is a deliberate non-semver integer.** Per GAP-2 (research/SUMMARY.md) the chosen scheme is monotonic increment on every wire-incompatible schema change. The integer is **not** synced to `app.version` / `1.10.0`; it stays at `1` until a real wire-format break ships.
- **`DataImportAudit` is the only v1.10 entity that does NOT extend `BaseEntity`** — this is intentional and worth a one-line JavaDoc on the class explaining why (auditing-listener bypass).
- **The 23-entity scope decision is a v1.10 LOCK.** If a future milestone (v1.11+) needs to bump the count (e.g., adding `FeatureSettings`, splitting `Race` into `Race` + `RaceLap`), it bumps `SCHEMA_VERSION` 1 → 2 and adds the entity to `EXPORT_ORDER`. The bump is the explicit forward-compat seam.

</specifics>

<deferred>
## Deferred Ideas

- **Per-entity Jackson MixIns** — Phase 73 (EXPORT-04 implementation). Phase 72 wires the `BackupObjectMapperConfig` injection hook but ships zero MixIns.
- **`manifest.json` first-entry write-order discipline** — Phase 73 (EXPORT-03 implementation). Phase 72 documents the contract; Phase 73's `BackupExportService` enforces it.
- **`BackupExportService` / `BackupImportService` / `/admin/backup` controller + templates** — Phases 73-76.
- **ZIP-Slip / ZipBomb defenses, multipart-config bumps, `MaxUploadSizeExceededException` mapping** — Phase 74 (SECU-01..04).
- **Replace-All transaction, JPA-auditing bypass, `Team.parentTeam = NULL` pre-step** — Phase 75 (IMPORT-05).
- **Concurrent-import lock, read-only banner, auto-backup-before-import** — Phase 76 (SECU-05..07).
- **`BackupRoundTripIT`, README + WIKI documentation, final UAT** — Phase 77 (QUAL-01..05).
- **Per-Saison-Export/Import selectivity** — v1.11+ (REQUIREMENTS.md `EXPORT-FUT-01` / `IMPORT-FUT-01`).
- **SHA-256 checksum file `manifest.sha256` and verify-only import mode** — v1.11+ (`EXPORT-FUT-02` / `IMPORT-FUT-02`).
- **`FeatureSettings` entity (if ever introduced)** — would be a SCHEMA_VERSION 1→2 bump + `EXPORT_ORDER` add + Flyway V8+ migration. Not a Phase 72 concern.
- **Admin audit-log viewer UI** — `DataImportAuditRepository` is ready to back a future `/admin/backup/history` page, but the UI itself is out of v1.10 scope.

</deferred>

---

*Phase: 72-backup-wire-contract-schema-manifest-objectmapper-audit-log-*
*Context gathered: 2026-05-11*
