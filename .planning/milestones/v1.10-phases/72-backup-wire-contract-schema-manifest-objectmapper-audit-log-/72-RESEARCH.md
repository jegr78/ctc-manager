# Phase 72: Backup Wire Contract — Schema, Manifest, ObjectMapper, Audit-Log Scope — Research

**Researched:** 2026-05-11
**Domain:** Spring Boot 4.0.6 / Hibernate 7.x JPA Metamodel introspection + Jackson 3 isolation + Flyway V7 portable DDL
**Confidence:** HIGH (with TWO planner-critical corrections to CONTEXT decisions — D-08 record-entity + D-11 ObjectMapper isolation)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Export Scope (overrides REQUIREMENTS.md EXPORT-04 wording):**

- **D-01:** `Car` and `Track` ARE included in `EXPORT_ORDER`. Round-trip semantics: a backup taken on environment A imports cleanly on environment B without requiring gt7sync to have been run on B first. Topology: Car and Track are FK-leaves with no incoming dependencies — topo-sort places them at the head of `EXPORT_ORDER`, before `Race`.
- **D-02:** `FeatureSettings` is DROPPED from the export-scope list. The class does not exist anywhere in `src/main/java/org/ctc/domain/model/`. Treat its future introduction as a deliberate `SCHEMA_VERSION` bump (1 → 2).
- **D-03:** Final entity count in `EXPORT_ORDER` is **23 entities** (illustrative — see Open Question OQ-1; the live codebase has 24 candidate entities under `org.ctc.domain.model` that satisfy D-06's package filter):
  `Car, Track, Season, SeasonPhase, SeasonPhaseGroup, Team, PhaseTeam, SeasonTeam, Driver, SeasonDriver, PsnAlias, RaceScoring, MatchScoring, RaceSettings, Matchday, Match, Race, RaceLineup, RaceResult, RaceAttachment, Playoff, PlayoffMatchup, PlayoffSeed`. Authoritative order is the runtime topo-sort output (D-04).

**EXPORT_ORDER Generation:**

- **D-04:** JPA `Metamodel` topological sort. `BackupSchema.@PostConstruct` reads `entityManagerFactory.getMetamodel().getEntities()`, restricts to `org.ctc.domain.model` package (D-06), walks each `EntityType<?>`'s singular attributes for `ManyToOne`/`OneToOne` owning-side relationships, builds directed edges `dependency → owner`, runs Kahn's algorithm. `Team.parentTeam` self-FK (cycle of length 1) detected and excluded from the edge set.
- **D-05:** `BackupSchema` is a single `@Component` class. `SCHEMA_VERSION` is `public static final int`; `exportOrder` is a `private List<EntityRef>` Spring-bean field, immutably assigned in `@PostConstruct` via `List.copyOf(topoSort(...))`, exposed via `public List<EntityRef> getExportOrder()`. Downstream services `@Autowire` `BackupSchema`.
- **D-06:** Package-scope filter for IMPORT-08. Topo-sort restricts to `clazz.getPackage().getName().startsWith("org.ctc.domain.model")`. Any entity under `org.ctc.backup.*` is structurally excluded — no marker annotation needed.
- **D-07:** `EntityRef` is a Java record with `Class<?> entityClass`, `String tableName` (from `@Table(name=...)`), `String fileName` (kebab-case derived from `tableName` → `season_phases` becomes `data/season-phases.json`). Planner may add fields.

**`data_import_audit` Architecture:**

- **D-08:** `DataImportAudit` (record-style entity preferred but Lombok class acceptable) + `DataImportAuditRepository extends JpaRepository<DataImportAudit, UUID>` in `org.ctc.backup.audit`. Empty operative surface in Phase 72. Entity does NOT extend `BaseEntity` (auditing-listener bypass for Phase 75). **NOTE: This research recommends Lombok class — see Pitfall P-1.**
- **D-09:** Flyway V7 column types are `LONGTEXT` (H2-and-MariaDB-portable). Native `JSON` rejected for v1.10. Column DDL locked:
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
- **D-10:** No `V7` Java-migration variant — pure SQL (`V7__data_import_audit.sql`). Mirrors V1/V2/V3.

**`BackupObjectMapperConfig` Isolation:**

- **D-11:** Isolation via `@Qualifier("backupObjectMapper")` + explicit `new ObjectMapper()` (NOT `Jackson2ObjectMapperBuilder` cloning). `FAIL_ON_UNKNOWN_PROPERTIES=true`, `WRITE_DATES_AS_TIMESTAMPS=false`, `JavaTimeModule`. Default Spring `ObjectMapper` remains untouched. **NOTE: This research identifies a Spring Boot auto-configuration trap — see Pitfall P-2; D-11 needs amendment.**
- **D-12:** MixIn-registration hook in place, zero MixIns in Phase 72. `@PostConstruct` (or `@Bean` body) of `BackupObjectMapperConfig` accepts a `List<Module>` or `Map<Class<?>, Class<?>>` of MixIns via Spring DI. Phase 73 adds the ~22 per-entity MixIn beans, picked up automatically.

**`BackupManifest` Wire Spec:**

- **D-13:** `BackupManifest` is a pure Java `record`. Fields: `int schemaVersion`, `String appVersion`, `Instant exportDate`, `Map<String, Long> tableCounts`. `appVersion` from Spring Boot's `BuildProperties` (requires `spring-boot-maven-plugin` `build-info` goal). `tableCounts` key is the snake_case `@Table(name=...)`. **NOTE: CTC already has a simpler `@Value("${app.version}")` mechanism — see Pitfall P-3; D-13's `BuildProperties` dependency is unnecessary.**
- **D-14:** "manifest.json is FIRST entry in ZIP" determinism is owned by Phase 73. Phase 72 ships only the record definition + documents the contract in PROJECT.md.

**PROJECT.md Documentation:**

- **D-15:** PROJECT.md gets two additions:
  1. `## Key Decisions` row: `| data_import_audit out of export scope | Audit log is operational metadata about migrations, not league data | ✓ v1.10 |`
  2. New `### Backup Wire Contract (v1.10)` section capturing integer SCHEMA_VERSION, per-entity `data/<entity>.json` layout with `manifest.json` first, JPA-Metamodel-generated `EXPORT_ORDER` over `org.ctc.domain.model.*`, 23-entity scope.

**Test Plan:**

- **D-16:** Five new tests, all Failsafe (IT) profile except #4:
  1. `BackupSchemaTopologyIT` (IT) — boots Spring, asserts 23 entities, every `@ManyToOne` dependency precedes owner, list is immutable.
  2. `BackupSchemaExclusionIT` (IT) — asserts `DataImportAudit` NOT in `getExportOrder()`.
  3. `BackupObjectMapperConfigIT` (IT) — injects both qualifiers, asserts different instances + configured differently.
  4. `BackupManifestSerializationTest` (Surefire/unit) — serializes sample `BackupManifest`, asserts JSON shape.
  5. `V7DataImportAuditMigrationIT` (IT) — H2 fresh schema, queries columns via JDBC metadata. MariaDB equivalent via `mariadb-migration-smoke.yml`.
- **D-17:** No `BackupRoundTripIT` / `BackupImportRollbackIT` in Phase 72. Those land in Phase 77 / 75.

### Claude's Discretion

- `EntityRef`-record field additions beyond D-07's three (planner may add `@EntityGraph` name or per-entity export-order index).
- Whether `BackupSchema.@PostConstruct` uses a separate `EntityTopoSorter` helper class (testability win) or inlines Kahn — planner's call. **Recommendation in §Architecture: extract to `EntityTopoSorter` for unit-testability.**
- IT class location: `src/test/java/org/ctc/backup/...` mirroring production package layout.
- DDL formatting / index addition on `data_import_audit` (only `executed_at` is a plausible query column).
- Whether `DataImportAuditRepository` declares additional finder methods — Phase 72 only needs default `JpaRepository` surface.

### Deferred Ideas (OUT OF SCOPE)

- Per-entity Jackson MixIns (Phase 73)
- `manifest.json` first-entry write-order enforcement (Phase 73)
- `BackupExportService` / `BackupImportService` / `/admin/backup` (Phases 73-76)
- ZIP-Slip / ZipBomb / multipart-config (Phase 74)
- Replace-All transaction, JPA-auditing bypass, `Team.parentTeam = NULL` pre-step (Phase 75)
- Concurrent-import lock, read-only banner, auto-backup-before-import (Phase 76)
- `BackupRoundTripIT`, README + WIKI, final UAT (Phase 77)
- Per-Saison-Export/Import selectivity (v1.11+)
- SHA-256 checksum + verify-only import mode (v1.11+)
- `FeatureSettings` entity (would be SCHEMA_VERSION 1→2 bump)
- Admin audit-log viewer UI

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SCHEMA-01 | `BackupSchema.SCHEMA_VERSION` integer constant (value `1` for v1.10) | §Architecture Pattern 1 (`BackupSchema` class shape); §Code Examples (constant declaration) |
| SCHEMA-02 | `BackupManifest` record with `schema_version`/`app_version`/`export_date`/`table_counts`; serialized as `manifest.json` (FIRST entry — Phase 73) | §Architecture Pattern 2 (record-based wire spec); §Code Examples (`BackupManifest`); §Pitfall P-3 (`app.version` wiring) |
| SCHEMA-03 | Flyway `V7__data_import_audit.sql` creates `data_import_audit` (H2 + MariaDB compatible) | §Architecture Pattern 4 (V7 DDL); §Pitfall P-4 (LONGTEXT portability VERIFIED); §Code Examples (V7 SQL) |
| SCHEMA-04 | `@Qualifier("backupObjectMapper")` ObjectMapper bean configured with `FAIL_ON_UNKNOWN_PROPERTIES=true`, `WRITE_DATES_AS_TIMESTAMPS=false`, `JavaTimeModule`; does NOT pollute default Spring `ObjectMapper` | §Architecture Pattern 3 (isolation via builder + `@Primary` redirect — D-11 amendment); §Pitfall P-2 (auto-config back-off trap) |
| IMPORT-08 | `data_import_audit` is EXPLICITLY OUTSIDE export scope | §Architecture Pattern 5 (D-06 package-filter enforces structural exclusion); §Test #2 (`BackupSchemaExclusionIT`) |

</phase_requirements>

## Project Constraints (from CLAUDE.md)

Extracted actionable directives the planner MUST honor:

| Constraint | Source | Phase 72 Impact |
|-----------|--------|-----------------|
| Test coverage ≥ 82 % line coverage | "Constraints" | Phase 72 adds ~150 LOC + 5 tests; must not drop overall coverage below 82 % |
| **Do NOT modify existing Flyway migrations V1–V6** | "Constraints" + "Do Not Modify Flyway Migrations" | V7 is the only addition; V1–V6 are byte-frozen |
| Maintain H2 + MariaDB compatibility in all migration files | "Do Not Modify Flyway Migrations" | V7 must use portable column types (LONGTEXT verified portable — Pitfall P-4) |
| Profiles: Auth only for prod/docker; dev/local without auth | "Constraints" | Phase 72 has no controllers — no impact, but ITs use `@ActiveProfiles("dev")` |
| OSIV remains enabled — `@EntityGraph` only for optimization | "Constraints" | Phase 72 has no JPA queries beyond stock repo — no impact |
| No breaking changes to existing URLs/endpoints | "Constraints" | Phase 72 introduces no URLs |
| Code/comments/UI in English; communication German | "Language" | All Phase 72 JavaDoc/comments English; no UI |
| Lombok: `@Getter @Setter @NoArgsConstructor` on entities; `@RequiredArgsConstructor` + `@Slf4j` on services | "Lombok Usage" | `DataImportAudit` (Lombok class), `BackupSchema`, `BackupObjectMapperConfig` (both `@RequiredArgsConstructor + @Slf4j`) |
| Test naming: `givenContext_whenAction_thenExpectedResult()` | "Test Naming" | All 5 new tests follow BDD convention |
| Surefire = Unit (excludes `**/*IT.java`); Failsafe = ITs (`*IT.java`, bound to `verify` phase per Phase 71 PLAT-06) | `pom.xml` lines 184-194 + 218-248 | Tests #1, #2, #3, #5 are `*IT.java`; #4 is `*Test.java` |

## Summary

Phase 72 lands six concrete artefacts that LOCK the on-disk wire contract for the v1.10 backup ZIP **before** any export or import code is written. The work is high-leverage but mechanically narrow — there is zero new business logic. The risk surface concentrates in three crisp areas: (1) the JPA Metamodel topological sort over a graph with one known self-referencing FK (`Team.parentTeam`), (2) the Spring Boot 4 / Jackson 3 ObjectMapper isolation pattern (where the conventional `@Qualifier` approach has a known auto-configuration back-off trap), and (3) Flyway V7's portability across H2 2.x and MariaDB.

This research reaches two **planner-critical corrections to CONTEXT decisions** that must be reconciled before plan creation:

1. **D-08 (record-style `DataImportAudit`)** is technically infeasible. Hibernate 7 / Jakarta Persistence does NOT support Java records as `@Entity` — records are final + immutable, JPA requires mutability and proxyable subclassing. CONTEXT D-08 hedges with "Lombok class is acceptable" — research recommends Lombok class as the **only** viable shape. Pitfall P-1 documents the rejected record shape.

2. **D-11 (ObjectMapper isolation via `@Qualifier` alone)** has a Spring Boot auto-configuration trap. `JacksonAutoConfiguration` uses `@ConditionalOnMissingBean(ObjectMapper.class)` — defining ANY `ObjectMapper` bean (regardless of qualifier) silently disables the auto-configured default, breaking admin REST/AJAX paths that rely on the default mapper. The fix: explicitly redefine the default `@Primary ObjectMapper` bean via `Jackson2ObjectMapperBuilder.json().build()` AND the `backupObjectMapper` qualified bean side-by-side. Pitfall P-2 documents the trap with verified GitHub-issue citation. This is the single biggest planner decision in Phase 72.

A third, smaller correction: **D-13's `BuildProperties` wiring is unnecessary** because CTC already exposes `app.version` via Maven resource-filtering (`@project.version@` in `application.yml`) consumed by `GlobalModelAdvice.@Value("${app.version}")`. `BackupManifest.appVersion` can be sourced the same way — zero pom.xml changes. Pitfall P-3 documents this.

**Primary recommendation:** Land the 5 deliverables in three small waves: (Wave 1) `BackupSchema` + `EntityTopoSorter` helper + `EntityRef` record + 2 ITs; (Wave 2) `BackupManifest` record + `BackupObjectMapperConfig` (with corrected D-11) + 2 tests; (Wave 3) Flyway V7 + `DataImportAudit` entity + repository + V7 IT + PROJECT.md updates. Waves are independent and parallelizable but Wave 1 produces the `BackupSchema` reference that Wave 2's IT uses, so prefer sequencing.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| FK-ordered entity discovery | Domain / Schema metadata | JPA (`Metamodel` API consumer) | `BackupSchema` reads the live `EntityManagerFactory.getMetamodel()` — no business logic, pure structural introspection |
| Wire-contract serialization config | Backup / Config | Jackson | `BackupObjectMapperConfig` is a `@Configuration` class isolated from MVC HTTP serialization; lives in `org.ctc.backup.config` |
| Manifest data carrier | Backup / DTO | None | `BackupManifest` is a record — pure data, no logic, no I/O |
| Audit row persistence | Backup / Audit | JPA / Hibernate | `DataImportAudit` + repository under `org.ctc.backup.audit`; write surface activates in Phase 75, not Phase 72 |
| Schema migration | Database / Flyway | None | `V7__data_import_audit.sql` is a pure DDL file — no Java touches the runtime DB schema |
| Wire-contract documentation | Project / Decisions | None | `PROJECT.md` gets two text additions (D-15) — no code |

**Tier check:** None of the Phase 72 deliverables introduce HTTP endpoints, templates, controllers, or services with business logic. The phase sits entirely in the **schema/config infrastructure tier** — a "skeleton" phase that future feature phases (73-77) flesh out.

## Standard Stack

### Core (all already present — zero new dependencies)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 4.0.6 | Framework | [CITED: pom.xml L8 — verified `<version>4.0.6</version>`]; Phase 71 baseline |
| Jakarta Persistence | 3.2.x (transitive via SB 4.0.6) | `Metamodel` API for D-04 topo-sort | [CITED: jakarta.ee/specifications/persistence/3.0/]; `EntityManagerFactory.getMetamodel()` exposes `EntityType<?>` + `SingularAttribute` |
| Hibernate | 7.x (transitive via SB 4.0.6) | JPA provider | [VERIFIED: SUMMARY.md confidence MEDIUM call-out — Phase 71 smoke green confirms Hibernate 7 works] |
| Jackson | 3.x — `JsonMapper` extends `ObjectMapper` | JSON serialization in `BackupObjectMapperConfig` | [CITED: spring.io/blog/2025/10/07/introducing-jackson-3-support-in-spring/]; SB 4.0 ships Jackson 3 via `spring-boot-starter-json`; `JsonMapper` is the immutable default; `JavaTimeModule` is part of `com.fasterxml.jackson.datatype:jackson-datatype-jsr310` transitively |
| Flyway | (transitive via SB 4.0.6) — current SB 4 default | V7 migration runner | [CITED: pom.xml — `spring-boot-starter-flyway`]; V1-V6 verified present (`V1__initial_schema.sql`, `V2__add_fk_indexes.sql`, `V3__add_season_phase_tables.sql`, V4/V5/V6 Java migrations) |
| H2 Database | 2.x (transitive) | Dev profile schema validation for V7 IT | [VERIFIED: H2 source `DataType.java` lists `LONGTEXT` as alias for `CLOB` value type — github.com/h2database/h2database/blob/master/h2/src/main/org/h2/value/DataType.java]; CTC test profile uses H2 in-memory |
| MariaDB | 10.x+ (runtime, prod/local/docker) | V7 migration target | [CITED: mariadb.com/kb/en/longtext]; `LONGTEXT` is the native variable-length type up to 4 GiB |
| JUnit 5 + AssertJ + Mockito | (transitive) | All 5 Phase 72 tests | [VERIFIED: TESTING.md L8-L17] |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Lombok | 1.18.x (transitive) | `@Getter @Setter @NoArgsConstructor @RequiredArgsConstructor @Slf4j` | All Phase 72 classes follow CTC's Lombok convention (CONVENTIONS.md L36-L67) |
| Spring Data JPA | (transitive) | `JpaRepository<DataImportAudit, UUID>` | Mirrors all 18 existing CTC repositories under `org.ctc.domain.repository` |

### Alternatives Considered

| Instead of | Could Use | Tradeoff | Verdict |
|------------|-----------|----------|---------|
| `Jackson2ObjectMapperBuilder.json().build()` for default mapper redefinition | `JsonMapperBuilderCustomizer` bean | Customizer modifies the SHARED auto-configured mapper — does NOT solve the isolation problem; would pollute admin REST/AJAX paths | **Reject** — D-11's stated goal is isolation; customizer breaks isolation |
| Java record `@Entity DataImportAudit` (D-08 hedged preference) | Lombok class `@Entity DataImportAudit extends ... but NOT BaseEntity` | Records are final + immutable; Hibernate 7 cannot proxy them, JPA requires mutable fields | **Reject record** — see Pitfall P-1; Lombok class is the only viable shape |
| `@Bean` for `BuildProperties` via `spring-boot-maven-plugin:build-info` (D-13 hedged step) | `@Value("${app.version}")` (existing CTC pattern in `GlobalModelAdvice`) | `BuildProperties` requires pom.xml change + new bean wiring; `@Value` reuses existing `app.version: @project.version@` resource-filter | **Use `@Value`** — see Pitfall P-3; zero pom.xml change needed |
| Hibernate-internal `MetadataSources` reflection (faster topo-sort, more info) | JPA `Metamodel` API (D-04 locked) | Hibernate-internal API leaks the ORM provider; survives no Hibernate version bumps | **Use JPA Metamodel** — D-04 is correct |
| Annotation-based opt-in (`@BackupExported` on entities) | Package-name filter `org.ctc.domain.model.*` (D-06 locked) | Annotation requires developer memory + maintenance; package filter is structural + zero-touch | **Use package filter** — D-06 is correct |

**Installation:** No `npm install` / `pom.xml` dependency-add needed. Phase 72 introduces ZERO new Maven coordinates.

**Version verification:** Performed against `pom.xml` at HEAD of `gsd/v1.10-platform-and-backup` branch:

```
spring-boot-starter-parent: 4.0.6 [VERIFIED — pom.xml L8]
thymeleaf pinned: 3.1.5.RELEASE [VERIFIED — pom.xml L26, Phase 71 lock]
project version: 1.8.0-SNAPSHOT [VERIFIED — pom.xml L13, irrelevant to Phase 72]
```

## Architecture Patterns

### System Architecture Diagram

```
Spring Boot Application Startup
            |
            v
      EntityManagerFactory (Spring auto-config)
            |
            +-> getMetamodel() ----> Set<EntityType<?>>
            |                              |
            |                              v
            |                    EntityTopoSorter (helper)
            |                    + filter org.ctc.domain.model.*
            |                    + walk SingularAttribute owning-side
            |                    + Kahn's algo (skip self-FK)
            |                              |
            |                              v
            |                    List<EntityRef> exportOrder
            |                              |
            v                              v
      BackupSchema @Component  <--- @PostConstruct populates exportOrder
            |
            +-> SCHEMA_VERSION (public static final int = 1)
            +-> getExportOrder() (public, immutable via List.copyOf)
            |
            +---------------> consumed by BackupExportService (Phase 73)
                              consumed by BackupImportService (Phase 75)

      Jackson Auto-Config (Spring Boot 4)
            |
            v
      JsonMapper (default @Primary, serves admin REST/AJAX)   <-- explicitly preserved in Phase 72
            |
            v
      BackupObjectMapperConfig @Configuration
            |
            +-> @Bean @Qualifier("backupObjectMapper") ObjectMapper
                    .FAIL_ON_UNKNOWN_PROPERTIES = true
                    .WRITE_DATES_AS_TIMESTAMPS = false (Jackson 3 default, set explicitly for forward-compat)
                    .registerModule(new JavaTimeModule())
                    .injects List<Module> from Spring DI (Phase 73 MixIn hook — empty in Phase 72)
            |
            +---------------> consumed by BackupExportService (Phase 73)
                              consumed by BackupImportService (Phase 74-75)

      Flyway Migration Runner (Spring Boot auto-config)
            |
            v
      V1__initial_schema.sql      [FROZEN]
      V2__add_fk_indexes.sql      [FROZEN]
      V3__add_season_phase_tables.sql [FROZEN]
      V4__MigrateSeasonsToPhases.java (Java) [FROZEN]
      V5__NullableLegacyScoringColumns.java (Java) [FROZEN]
      V6__CleanupLegacySeasonColumns.java (Java) [FROZEN]
      V7__data_import_audit.sql   <-- NEW in Phase 72 (pure SQL, no Java variant)
            |
            v
      data_import_audit table exists
            |
            v
      DataImportAudit @Entity + DataImportAuditRepository  <-- inert in Phase 72 (no writes)
            |
            +---------------> Phase 75 writes audit rows
                              Phase 73's BackupSchema topo-sort SKIPS this entity (D-06 package filter)
```

### Recommended Project Structure

```
src/main/java/org/ctc/backup/
├── schema/
│   ├── BackupSchema.java                    # @Component, holds SCHEMA_VERSION + exportOrder
│   ├── BackupManifest.java                  # record(schemaVersion, appVersion, exportDate, tableCounts)
│   ├── EntityRef.java                       # record(entityClass, tableName, fileName)
│   └── EntityTopoSorter.java                # helper — Kahn over JPA Metamodel; PLANNER DISCRETION (D-05)
├── config/
│   └── BackupObjectMapperConfig.java        # @Configuration; default @Primary + backupObjectMapper @Qualifier
└── audit/
    ├── DataImportAudit.java                 # @Entity (Lombok class; NOT BaseEntity); deliberately inert in P72
    └── DataImportAuditRepository.java       # extends JpaRepository<DataImportAudit, UUID>

src/main/resources/db/migration/
└── V7__data_import_audit.sql                # NEW; pure SQL, H2 + MariaDB portable

src/test/java/org/ctc/backup/
├── schema/
│   ├── BackupSchemaTopologyIT.java          # @SpringBootTest, asserts 23 entities, topo order, immutability
│   ├── BackupSchemaExclusionIT.java         # @SpringBootTest, asserts DataImportAudit NOT in exportOrder
│   ├── BackupManifestSerializationTest.java # Unit (Surefire), asserts JSON shape via backupObjectMapper
│   └── EntityTopoSorterTest.java            # OPTIONAL unit test if helper extracted (recommended)
└── config/
    └── BackupObjectMapperConfigIT.java      # @SpringBootTest, asserts two distinct mapper beans

src/test/java/db/migration/
└── V7DataImportAuditMigrationIT.java        # @SpringBootTest, JDBC metadata query for column types

PROJECT.md                                   # +1 Decisions row + new "Backup Wire Contract (v1.10)" section
REQUIREMENTS.md                              # footnote on EXPORT-04 referencing D-01/D-02/D-03 (D-15 directive)
```

### Pattern 1: `BackupSchema` `@Component` with `@PostConstruct` Topo-Sort

**What:** Spring-managed singleton that exposes both the integer `SCHEMA_VERSION` constant and the FK-ordered `List<EntityRef>` populated once at startup.

**When to use:** Always — this is the only Phase 72 component that consumers (Phase 73's `BackupExportService`, Phase 75's `BackupImportService`) inject.

**Source:** Verified pattern via [CITED: medium.com/@tharinduimalka915 — Kahn's algorithm for DB schema dependencies] + [CITED: Jakarta Persistence 3.0 spec §5.1 — `Metamodel` API].

```java
package org.ctc.backup.schema;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.Attribute.PersistentAttributeType;
import lombok.RequiredArgsConstructor;
import lombok.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Locks the v1.10 backup wire contract.
 *
 * <p>SCHEMA_VERSION is a deliberate non-semver integer (Phase 72 D-13 / GAP-2 resolution):
 * monotonically incremented on every wire-incompatible schema change. NOT synced to
 * {@code app.version} — stays at 1 until a real wire-format break ships.
 *
 * <p>exportOrder is generated at startup by FK-respecting topological sort of the JPA
 * Metamodel, restricted to {@code org.ctc.domain.model.*} (D-06). The runtime list is
 * the authoritative entity ordering — D-03's documented 23-entity list is illustrative.
 *
 * <p>Phase 72 ships this class empty of business logic. Phase 73's BackupExportService
 * reads SCHEMA_VERSION + getExportOrder(); Phase 75's BackupImportService re-uses the same.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BackupSchema {

    public static final int SCHEMA_VERSION = 1;

    private static final String DOMAIN_MODEL_PACKAGE = "org.ctc.domain.model";

    private final EntityManagerFactory entityManagerFactory;
    private final EntityTopoSorter entityTopoSorter;  // PLANNER DISCRETION — extracted for unit-testability

    private List<EntityRef> exportOrder;

    @PostConstruct
    void initializeExportOrder() {
        var entityTypes = entityManagerFactory.getMetamodel().getEntities().stream()
                .filter(et -> et.getJavaType().getPackage().getName().startsWith(DOMAIN_MODEL_PACKAGE))
                .toList();
        this.exportOrder = List.copyOf(entityTopoSorter.sort(entityTypes));
        log.info("BackupSchema initialized: SCHEMA_VERSION={}, exportOrder size={}, entities=[{}]",
                SCHEMA_VERSION,
                exportOrder.size(),
                exportOrder.stream().map(EntityRef::tableName).reduce((a, b) -> a + ", " + b).orElse(""));
    }

    public List<EntityRef> getExportOrder() {
        return exportOrder;
    }
}
```

```java
package org.ctc.backup.schema;

import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.Attribute.PersistentAttributeType;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Kahn's-algorithm topo-sort over JPA EntityType<?>. Dependency edge:
 * for every owning-side ManyToOne/OneToOne (mappedBy null), record
 * {@code dependency -> owner}. Self-FK edges (e.g. Team.parentTeam) are
 * detected and excluded so Kahn's queue does not deadlock at depth 0.
 *
 * <p>Extracted from BackupSchema to enable pure unit testing without a Spring context.
 */
@Component
class EntityTopoSorter {

    List<EntityRef> sort(List<EntityType<?>> entityTypes) {
        Map<Class<?>, Set<Class<?>>> outgoing = new HashMap<>();   // dependency -> {owners}
        Map<Class<?>, Integer> inDegree = new HashMap<>();
        Map<Class<?>, EntityType<?>> byClass = entityTypes.stream()
                .collect(Collectors.toMap(EntityType::getJavaType, et -> et));

        for (EntityType<?> et : entityTypes) {
            outgoing.putIfAbsent(et.getJavaType(), new HashSet<>());
            inDegree.putIfAbsent(et.getJavaType(), 0);
        }
        for (EntityType<?> owner : entityTypes) {
            for (SingularAttribute<?, ?> attr : owner.getSingularAttributes()) {
                var type = attr.getPersistentAttributeType();
                if (type != PersistentAttributeType.MANY_TO_ONE
                        && type != PersistentAttributeType.ONE_TO_ONE) continue;
                // mappedBy detection: owning-side singular attributes have no mappedBy hint;
                // JPA spec puts mappedBy on the inverse-side @OneToOne or @OneToMany. By restricting
                // to singular attributes (One-to-One owning) and ManyToOne (always owning), we cover
                // the owning-side without an explicit mappedBy() call.
                Class<?> depClass = attr.getJavaType();
                if (!byClass.containsKey(depClass)) continue;     // FK to non-domain entity → skip
                Class<?> ownerClass = owner.getJavaType();
                if (depClass.equals(ownerClass)) continue;        // self-FK (Team.parentTeam) → skip
                if (outgoing.get(depClass).add(ownerClass)) {     // dedupe duplicate edges
                    inDegree.merge(ownerClass, 1, Integer::sum);
                }
            }
        }

        Deque<Class<?>> queue = inDegree.entrySet().stream()
                .filter(e -> e.getValue() == 0)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(ArrayDeque::new));
        List<EntityRef> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            Class<?> cls = queue.poll();
            result.add(EntityRef.fromEntityType(byClass.get(cls)));
            for (Class<?> owner : outgoing.get(cls)) {
                if (inDegree.merge(owner, -1, Integer::sum) == 0) queue.offer(owner);
            }
        }
        if (result.size() != entityTypes.size()) {
            throw new IllegalStateException(
                    "Topo-sort produced " + result.size() + " entries, expected " + entityTypes.size()
                            + " — likely an unexpected cycle outside the known Team.parentTeam self-FK");
        }
        return result;
    }
}
```

### Pattern 2: `BackupManifest` Record + `EntityRef` Record

**What:** Pure-data records carrying the wire-format spec. Jackson 3 / Spring Boot 4 serializes Java records natively.

**Source:** [CITED: itnext.io/an-introduction-to-jackson-3-in-spring-7-and-spring-boot-4 — Jackson 3 records support].

```java
package org.ctc.backup.schema;

import java.time.Instant;
import java.util.Map;

/**
 * Wire-format spec for the manifest.json entry in every backup ZIP.
 *
 * <p>Phase 72 ships only the record definition. Phase 73's BackupExportService is
 * responsible for serializing this through the backupObjectMapper qualifier
 * (FAIL_ON_UNKNOWN_PROPERTIES=true / WRITE_DATES_AS_TIMESTAMPS=false) and
 * writing it as the FIRST entry in the ZipOutputStream.
 *
 * <p>schemaVersion: monotonic integer (BackupSchema.SCHEMA_VERSION = 1 in v1.10)
 * <p>appVersion:     CTC application version from app.version (Maven resource-filtered @project.version@)
 * <p>exportDate:     Instant at export start
 * <p>tableCounts:    snake_case table name (matching @Table(name=...)) -> row count
 */
public record BackupManifest(
        int schemaVersion,
        String appVersion,
        Instant exportDate,
        Map<String, Long> tableCounts
) {
}
```

```java
package org.ctc.backup.schema;

import jakarta.persistence.Table;
import jakarta.persistence.metamodel.EntityType;

/**
 * Per-entity reference: maps a JPA entity class to its wire-format file name.
 *
 * <p>fileName derivation: {@code season_phases} table -> {@code data/season-phases.json}
 * (snake_case -> kebab-case, prefix {@code data/}, suffix {@code .json}).
 *
 * <p>Planner discretion (D-07): may add fields like {@code String entityGraphName}
 * if Phase 73's BackupExportService benefits from a per-entity @EntityGraph hint.
 */
public record EntityRef(
        Class<?> entityClass,
        String tableName,
        String fileName
) {
    public static EntityRef fromEntityType(EntityType<?> et) {
        Table tableAnno = et.getJavaType().getAnnotation(Table.class);
        String table = (tableAnno != null && !tableAnno.name().isBlank())
                ? tableAnno.name()
                : et.getName().toLowerCase();   // JPA-portable fallback
        String file = "data/" + table.replace('_', '-') + ".json";
        return new EntityRef(et.getJavaType(), table, file);
    }
}
```

### Pattern 3: `BackupObjectMapperConfig` — Isolation WITHOUT auto-config back-off

**What:** A `@Configuration` class that defines two `ObjectMapper` beans:
1. The auto-config-replacing `@Primary` default (explicitly preserved with Spring's `Jackson2ObjectMapperBuilder` defaults — keeps admin REST/AJAX serving identically).
2. The qualified `backupObjectMapper` with strict FAIL_ON_UNKNOWN_PROPERTIES + ISO-8601 dates + JavaTimeModule + Phase 73 MixIn injection hook.

**Why this shape:** [CITED: github.com/spring-projects/spring-boot/issues/47379 + github.com/spring-projects/spring-boot/issues/22403 + github.com/spring-projects/spring-boot/issues/42598] — `JacksonAutoConfiguration` uses `@ConditionalOnMissingBean(ObjectMapper.class)`, so defining ANY `ObjectMapper` bean disables the auto-configured `@Primary` default. The CONTEXT D-11 plan ("new ObjectMapper(), tag with @Qualifier, default Spring ObjectMapper remains untouched") is **wrong** — the qualified bean silently steals the default-mapper role for admin REST/AJAX paths, breaking them.

**Mitigation:** Define BOTH beans explicitly in `BackupObjectMapperConfig`. The default is reconstructed via `Jackson2ObjectMapperBuilder.json().build()` (which preserves the auto-config's behavior) and tagged `@Primary`. The backup mapper is `@Qualifier("backupObjectMapper")` with strict settings.

```java
package org.ctc.backup.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.List;

/**
 * Isolates the backup ObjectMapper from the admin REST/AJAX serializer.
 *
 * <p>Spring Boot's JacksonAutoConfiguration uses {@code @ConditionalOnMissingBean(ObjectMapper.class)},
 * so defining ANY ObjectMapper bean disables the auto-configured default. To preserve the default
 * for admin REST/AJAX paths AND introduce a strict, separately-configured backup mapper, we
 * declare both beans here explicitly. See Phase 72 RESEARCH §Pitfall P-2 for the auto-config
 * back-off mechanism and the GitHub-issue trail (spring-projects/spring-boot#47379).
 *
 * <p>Phase 72 ships zero Jackson MixIns. Phase 73 adds the ~22 per-entity MixIn @Component beans;
 * they get picked up via the {@code List<Module> backupMixInModules} DI injection below.
 */
@Configuration
public class BackupObjectMapperConfig {

    /**
     * Replaces the JacksonAutoConfiguration default mapper byte-for-byte. Marked @Primary so admin
     * REST/AJAX MVC paths still resolve {@code @Autowired ObjectMapper} to this bean.
     */
    @Bean
    @Primary
    public ObjectMapper defaultObjectMapper(Jackson2ObjectMapperBuilder builder) {
        return builder.build();
    }

    /**
     * Strict backup-only mapper. Inputs:
     *  - List<Module> backupMixInModules: Phase 73 adds per-entity MixIn @Component beans here.
     *    Empty in Phase 72 (Spring DI injects an empty list when no matching beans exist).
     */
    @Bean
    @Qualifier("backupObjectMapper")
    public ObjectMapper backupObjectMapper(List<Module> backupMixInModules) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.registerModule(new JavaTimeModule());
        backupMixInModules.forEach(mapper::registerModule);
        return mapper;
    }
}
```

**Alternative MixIn injection shape (Phase 73 will pick):** instead of `List<Module>`, accept `Map<Class<?>, Class<?>>` of entity→MixIn-class pairs and call `mapper.addMixIn(entity, mixIn)` per entry. The `List<Module>` shape above is the more idiomatic Spring DI approach because Phase 73's MixIn beans can each implement `Module.setupModule(SetupContext)` to register themselves — Jackson's native module pattern. Phase 72 documents the hook; Phase 73 chooses the final shape.

### Pattern 4: Flyway V7 — `data_import_audit` (Pure SQL, H2 + MariaDB Portable)

**Source:** [VERIFIED: H2 source github.com/h2database/h2database/blob/master/h2/src/main/org/h2/value/DataType.java — LONGTEXT alias for CLOB]; [CITED: mariadb.com/kb/en/longtext — LONGTEXT is MariaDB's native variable-length text type, max ~4 GiB].

```sql
-- src/main/resources/db/migration/V7__data_import_audit.sql
--
-- Phase 72: data_import_audit table — operational audit log for backup imports.
-- This table is PERMANENTLY OUT OF EXPORT SCOPE (IMPORT-08, see PROJECT.md Decisions row).
-- Phase 75 writes rows here as part of the import @Transactional path; Phase 72 ships the
-- table empty and the JPA entity inert.
--
-- Column type rationale (Phase 72 D-09):
--   - id UUID:                  portable across H2 2.x + MariaDB 10.7+ (UUID is native on both).
--   - LONGTEXT (not JSON):      MariaDB JSON is itself a LONGTEXT alias with CHECK JSON_VALID();
--                               H2's JSON validates differently. Keeping textual avoids dialect drift.
--                               Jackson serialization enforces the JSON shape at write time, not DDL.
--   - TIMESTAMP NOT NULL:       portable; H2 and MariaDB both accept and store as DATETIME-equivalent.
--   - BOOLEAN:                  H2 native; MariaDB stores as TINYINT(1) — both accept the keyword.
--
-- DO NOT mutate this file after release (CLAUDE.md "Do Not Modify Flyway Migrations").

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

-- Single index on the most plausible query column (admin history view in a future milestone).
-- FK indexes are not needed: this table has no FKs.
CREATE INDEX idx_data_import_audit_executed_at ON data_import_audit (executed_at);
```

### Pattern 5: `DataImportAudit` Lombok Class (NOT a Record, NOT BaseEntity)

**Why NOT a record:** [VERIFIED: medium.com/javarevisited — Hibernate / JPA cannot use Java records as @Entity. Records are final + immutable; JPA requires mutable fields, no-arg constructor, and proxyable subclassing. CONTEXT D-08 hedged "Lombok class is acceptable" — research locks this to the only viable choice.] See Pitfall P-1.

**Why NOT BaseEntity:** [CITED: CONTEXT D-08] — the audit row's `executedAt` is set explicitly by the Phase 75 writer; the auditing-listener-bypass is exactly what Phase 75 IMPORT-05 is implementing. Inheriting `BaseEntity` would re-introduce `@CreatedDate` / `@LastModifiedDate` overrides on import-row writes, defeating the design.

```java
package org.ctc.backup.audit;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Operational audit log of backup imports. Phase 72 ships this entity inert (no writes,
 * no service surface). Phase 75 wires DataImportAuditRepository.save(...) into the
 * BackupImportService transaction.
 *
 * <p><strong>Intentionally does NOT extend BaseEntity.</strong> The {@code executedAt}
 * field is set explicitly by the Phase 75 writer; the AuditingEntityListener bypass is
 * exactly what Phase 75's IMPORT-05 transaction is designed to enable. Inheriting
 * BaseEntity would re-introduce {@code @CreatedDate}/{@code @LastModifiedDate}
 * overrides on import-row writes, defeating the design.
 *
 * <p><strong>NOT a Java record.</strong> Hibernate 7 / Jakarta Persistence does not support
 * record-based entities (records are final, immutable, and not proxyable). See
 * 72-RESEARCH §Pitfall P-1.
 */
@Entity
@Table(name = "data_import_audit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class DataImportAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    @NotBlank
    @Column(name = "executed_by", nullable = false)
    private String executedBy;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @NotBlank
    @Lob
    @Column(name = "table_counts_wiped", nullable = false, columnDefinition = "LONGTEXT")
    private String tableCountsWiped;       // JSON shape enforced at write time by Jackson

    @NotBlank
    @Lob
    @Column(name = "table_counts_restored", nullable = false, columnDefinition = "LONGTEXT")
    private String tableCountsRestored;    // JSON shape enforced at write time by Jackson

    @NotBlank
    @Column(name = "source_filename", nullable = false)
    private String sourceFilename;

    @Column(nullable = false)
    private boolean success;
}
```

```java
package org.ctc.backup.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

/**
 * Stock Spring Data repository for {@link DataImportAudit}. Phase 72 needs no custom
 * finders — Phase 75 calls only {@code save(...)}. A future admin UI may add
 * {@code findTop10ByOrderByExecutedAtDesc()} (Phase 72 D-discretion).
 */
public interface DataImportAuditRepository extends JpaRepository<DataImportAudit, UUID> {
}
```

### Anti-Patterns to Avoid

- **DO NOT use `Jackson2ObjectMapperBuilderCustomizer`** for `backupObjectMapper`. Customizers mutate the SHARED auto-configured default mapper, which is the opposite of D-11's isolation goal. The customizer-based approach pollutes admin REST/AJAX paths.
- **DO NOT use `JsonMapperBuilderCustomizer` (Jackson 3 / Spring Boot 4 idiom)** for the same reason — it mutates the shared default.
- **DO NOT use `@JsonIdentityInfo` on `org.ctc.domain.model` entities directly.** Phase 73's MixIns externalize Jackson annotations — entity classes stay annotation-clean. Phase 72 deliberately does NOT touch entities.
- **DO NOT make `DataImportAudit` a record `@Entity`.** Hibernate 7 cannot proxy or persist records. See Pitfall P-1.
- **DO NOT extend `BaseEntity` on `DataImportAudit`.** Defeats the Phase 75 auditing-listener bypass; auto-populates `created_at`/`updated_at` columns that the table does not have (V7 DDL omits them).
- **DO NOT add a `build-info` execution to `pom.xml`.** CTC already exposes `app.version` via Maven resource-filtering (`@project.version@` in `application.yml`). See Pitfall P-3.
- **DO NOT inline the Kahn algorithm in `BackupSchema.@PostConstruct`.** Extract to `EntityTopoSorter` helper so it can be unit-tested without booting Spring (planner discretion D-05; this research strongly recommends extraction).
- **DO NOT hand-write the 23-entity list anywhere in source.** D-04 mandates runtime generation; hand-written lists invalidate themselves silently as the domain model evolves.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Entity discovery | Reflective classpath scan via `Reflections` library, `ClassPathScanningCandidateComponentProvider`, or hand-maintained `Class[]` array | `EntityManagerFactory.getMetamodel().getEntities()` | JPA spec API; survives Hibernate version bumps; reflects the LIVE schema (matches what Flyway+Hibernate agree exists) |
| FK detection | Reading `@JoinColumn` / `@ManyToOne` via raw reflection | `SingularAttribute.getPersistentAttributeType()` returning `MANY_TO_ONE`/`ONE_TO_ONE` | JPA portable; Hibernate-internal-API-free; handles inheritance correctly |
| Topo-sort | Recursive DFS with manual cycle detection + visited-set bookkeeping | Kahn's algorithm with in-degree queue | Standard algorithm; trivial Java implementation; deterministic output order; clear cycle-detection (residue size ≠ input size) |
| `app.version` plumbing | Add `spring-boot-maven-plugin:build-info` goal + inject `BuildProperties` | Existing `@Value("${app.version}")` via Maven resource-filtered `@project.version@` | Already in CTC's working code (`GlobalModelAdvice` L10-L16); zero pom.xml change; same value source |
| Schema-version constant | Constant in `application.yml` + `@Value` injection | `public static final int SCHEMA_VERSION = 1` on `BackupSchema` | Compile-time constant; can be referenced from tests + downstream services without runtime injection; mirrors the design intent ("integer, manually bumped on wire breaks") |
| JSON portability column type | Native `JSON` column type | `LONGTEXT` + Jackson serialization at write time | MariaDB JSON is itself a LONGTEXT alias; H2's JSON validates differently — `LONGTEXT` keeps both dialects happy without `CHECK` constraint drift |
| ObjectMapper customization | `Jackson2ObjectMapperBuilderCustomizer` / `JsonMapperBuilderCustomizer` | Two explicit `@Bean` declarations: `@Primary defaultObjectMapper(builder)` + `@Qualifier("backupObjectMapper") backupObjectMapper(...)` | Customizers mutate the SHARED mapper; explicit two-bean shape is the only pattern that genuinely isolates |
| MixIn registration | Per-entity `@PostConstruct` `mapper.addMixIn(Entity.class, EntityMixIn.class)` calls | Spring DI `List<Module>` injection in the `backupObjectMapper` `@Bean` method | Phase 73 MixIn beans implement `Module.setupModule(SetupContext)` and self-register; zero Phase 72 code touch when Phase 73 ships |

**Key insight:** Phase 72 is structurally a "use the standard JPA + Spring + Jackson APIs to build a wire-contract carrier" phase. Every piece of infrastructure already exists in Spring Boot 4 / Hibernate 7 / Jackson 3. The temptation to hand-roll comes from the "make this elegant" instinct — the actual right answer is "use the boring vendor APIs."

## Runtime State Inventory

> Phase 72 is greenfield-infrastructure (new package `org.ctc.backup`, new Flyway migration, new audit table). No rename or refactor. The 23-entity scope is documented at the wire-contract level; no existing strings or records change.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None — Phase 72 introduces new table `data_import_audit` with zero rows (Phase 75 writes the first row). No existing data is renamed, migrated, or restructured. | None |
| Live service config | None — no n8n / Datadog / external-service config changes. | None |
| OS-registered state | None — no Task Scheduler / launchd / systemd / pm2 entries depend on Phase 72 outputs. | None |
| Secrets/env vars | None — Phase 72 introduces no env vars or secrets. (Future Phase 75 reads `Authentication.getName()` for `executedBy` but that is Spring Security plumbing, not env.) | None |
| Build artifacts | None — no compiled artifacts contain hard-coded "23-entity list" strings. The list is generated at runtime from the live `Metamodel`. | None |

**Verified by:** repo-wide grep for `BackupSchema`, `data_import_audit`, `EXPORT_ORDER` returns zero hits in `src/` (greenfield); `pom.xml` review shows no `build-info` execution to remove.

## Common Pitfalls

### Pitfall P-1: D-08's "record-style entity preferred" is infeasible — Lombok class is the ONLY viable shape

**What goes wrong:** A planner reads CONTEXT D-08 ("record-style entity is preferred but Lombok class is acceptable") and dispatches a task to "create `record DataImportAudit(UUID id, Instant executedAt, ...)`" annotated with `@Entity`. Compilation succeeds (records can carry annotations); Spring context boot fails with `MappingException: HHH000038: Composition not supported on records` or `HibernateException: Cannot bind to entity: record type`.

**Why it happens:**
- Records are `final` — Hibernate's proxy mechanism subclasses entities for lazy-loading; final classes break this.
- Records have implicitly `final` fields with constructor-only initialization — JPA requires `@Setter` / `set...()` to populate during deserialization and proxy hydration.
- Records lack a no-arg constructor — JPA spec mandates a public or protected no-arg constructor per §2.1 of Jakarta Persistence 3.0.
- [VERIFIED: medium.com/javarevisited — "Why I Can't Use Java Records as JPA Entities" + Vlad Mihalcea: "Java Records cannot be used as JPA entity classes" + Thorben Janssen]

**How to avoid:**
- Implement `DataImportAudit` as a Lombok class extending `Object` (NOT `BaseEntity`). See Pattern 5 code above for the canonical shape.
- Records remain useful for **embeddables** (Hibernate 6.2+ supports `@Embeddable record`), but `DataImportAudit` is a top-level entity, not embedded.

**Warning signs:**
- Plan-checker sees the task description "record DataImportAudit..." → reject and amend before dispatch.
- IT boot fails with `HHH000038` or `HibernateException: Cannot bind` → record shape leaked through.

### Pitfall P-2: D-11's `@Qualifier` alone is insufficient — auto-configured default ObjectMapper backs off

**What goes wrong:** A planner reads CONTEXT D-11 literally ("explicit `new ObjectMapper()` tagged `@Qualifier("backupObjectMapper")` — default Spring ObjectMapper remains untouched") and creates a single `@Bean @Qualifier("backupObjectMapper") ObjectMapper backupObjectMapper() { ... }`. At runtime, Spring Boot's `JacksonAutoConfiguration` sees an existing `ObjectMapper` bean → `@ConditionalOnMissingBean(ObjectMapper.class)` returns false → the auto-configured `@Primary` default mapper is NEVER created. Every `@Autowired ObjectMapper` injection point in the application — including the MVC `MappingJackson2HttpMessageConverter` that admin AJAX endpoints depend on — resolves to the strict `backupObjectMapper` with `FAIL_ON_UNKNOWN_PROPERTIES=true`. Admin AJAX requests that previously tolerated unknown JSON properties (e.g. CSRF tokens, browser-injected fields) start failing with HTTP 400.

**Why it happens:**
- `@ConditionalOnMissingBean` matches on **type alone**, not bean name or qualifier.
- [VERIFIED: github.com/spring-projects/spring-boot/issues/47379 — "Auto-configured JsonMapper backs off when any type of ObjectMapper is defined"]
- [VERIFIED: github.com/spring-projects/spring-boot/issues/22403 — "Make it easier to define a bean in addition to an auto-configured bean of the same type"]
- [VERIFIED: github.com/spring-projects/spring-boot/issues/42598 — "Clarify why @Primary is recommended when defining your own ObjectMapper"]
- The trap is invisible at compile time and at Spring-context-load time — it only manifests when an unknown JSON property arrives at an MVC endpoint.

**How to avoid:**
- Declare BOTH beans in `BackupObjectMapperConfig`:
  1. `@Bean @Primary public ObjectMapper defaultObjectMapper(Jackson2ObjectMapperBuilder builder) { return builder.build(); }` — preserves the auto-config's behavior byte-for-byte (Jackson2ObjectMapperBuilder is the same builder JacksonAutoConfiguration uses internally).
  2. `@Bean @Qualifier("backupObjectMapper") public ObjectMapper backupObjectMapper(List<Module> mixIns) { ... }` — strict config.
- See Pattern 3 code above for the canonical shape.

**Warning signs:**
- A planner-side reading of D-11 that says "just define `backupObjectMapper`, the default stays" → STOP, amend the plan to also redefine the default.
- `BackupObjectMapperConfigIT` asserts the two beans are distinct via `assertThat(defaultMapper).isNotSameAs(backupMapper)` — but ALSO assert `defaultMapper.getDeserializationConfig().hasDeserializationFeatures(FAIL_ON_UNKNOWN_PROPERTIES.getMask())` is **false** (or check the feature explicitly) to catch the back-off case.
- Admin AJAX test fails with HTTP 400 + "unknown field" message after Phase 72 lands → the default mapper was clobbered.

**Phase to address:** Phase 72 plan amendment BEFORE writing `BackupObjectMapperConfig`. The CONTEXT D-11 wording is the trap.

### Pitfall P-3: D-13's `BuildProperties` wiring is unnecessary — `@Value("${app.version}")` already works

**What goes wrong:** A planner reads CONTEXT D-13's hedge ("`appVersion` is sourced from Spring Boot's `BuildProperties` — verify wiring during planning; CTC may need the `<execution><goals><goal>build-info</goal>...` addition to pom.xml") and adds a `<execution>` block to `pom.xml`'s `spring-boot-maven-plugin` + injects `BuildProperties` into the (future Phase 73) `BackupExportService`. This works but is two extra pieces of plumbing that CTC doesn't need.

**Why it doesn't apply:**
- [VERIFIED: `src/main/resources/application.yml` lines 1-3]:
  ```yaml
  app:
    version: @project.version@
  ```
- [VERIFIED: `src/main/java/org/ctc/admin/controller/GlobalModelAdvice.java`]:
  ```java
  @Value("${app.version}") private String appVersion;
  ```
- Maven resource-filtering (`@project.version@`) populates `app.version` at build time. Spring's `@Value` resolves it at startup. The mechanism is already wired and working — `GlobalModelAdvice` proves it.

**How to avoid:**
- In the Phase 73 `BackupExportService` (when manifest construction happens), inject `@Value("${app.version}") String appVersion` and pass it to `new BackupManifest(SCHEMA_VERSION, appVersion, Instant.now(), counts)`.
- Phase 72 ships only the `BackupManifest` record — no wiring needed yet. But the planner should NOT chase a `BuildProperties` setup that adds pom.xml lines for no benefit.

**Warning signs:**
- Task description includes "add build-info execution to pom.xml" → reject; reuse existing `app.version` mechanism.
- Task imports `org.springframework.boot.info.BuildProperties` → reject; use `@Value("${app.version}")`.

**Phase to address:** Phase 72 plan amendment — D-13 should be re-stated as "use the existing `@Value("${app.version}")` mechanism."

### Pitfall P-4: `LONGTEXT` portability across H2 2.x + MariaDB — VERIFIED OK, but watch for `flyway-mysql` dialect

**What goes wrong:** A planner worries that `LONGTEXT` may behave differently on H2 vs MariaDB and over-engineers the V7 migration with `<dialect-aware>` Java logic (V4/V5/V6 pattern). Or ships V7 with `JSON` type and gets dialect drift.

**Why this is NOT a problem:**
- [VERIFIED: H2 2.x source — `DataType.java` registers `LONGTEXT`, `MEDIUMTEXT`, `TEXT`, `NTEXT` as aliases for the CLOB value type.]
- [VERIFIED: MariaDB documentation — `LONGTEXT` is the native variable-length text type, max 2^32 - 1 bytes (~4 GiB).]
- [VERIFIED: CTC uses `flyway-mysql` dialect (pom.xml dependency). H2 with the MySQL/MariaDB compatibility profile accepts `LONGTEXT` directly.]
- CTC's existing migrations V1/V2 already include `LONGTEXT`-style fields (verify `V1__initial_schema.sql` for precedent if uncertain — `RaceScoring.race_points` is a comma-string).

**How to avoid:**
- Keep V7 as pure SQL (D-10). The DDL in Pattern 4 is portable as-is.
- Do NOT add a Java migration variant. The V4/V5/V6 pattern exists because those migrations did data-shape transformations (column nullables, DELETEs over rows) — V7 is a single `CREATE TABLE` with portable types.

**Warning signs:**
- Plan task says "create V7 as `db.migration.V7DataImportAudit` Java class" → reject; pure SQL is correct.
- IT fails on MariaDB with "Unknown column type LONGTEXT" → ensure `flyway-mysql` dialect is on the classpath (it is — pom.xml has `spring-boot-starter-flyway` which transitively pulls `flyway-mysql`).

### Pitfall P-5: D-03's "23 entities" is illustrative — runtime topo-sort produces 24 (planner reconciliation needed)

**What goes wrong:** A planner writes `BackupSchemaTopologyIT` with `assertThat(exportOrder).hasSize(23)`. At runtime the topo-sort emits 24 entities. Test fails.

**Why:** Direct count of `@Entity` classes under `org.ctc.domain.model.*` (verified `ls + grep`):
1. Car, 2. Driver, 3. MatchScoring, 4. Match, 5. Matchday, 6. PhaseTeam, 7. Playoff, 8. PlayoffMatchup, **9. PlayoffRound**, 10. PlayoffSeed, 11. PsnAlias, 12. Race, 13. RaceAttachment, 14. RaceLineup, 15. RaceResult, 16. RaceScoring, 17. RaceSettings, 18. SeasonDriver, 19. SeasonPhase, 20. SeasonPhaseGroup, 21. SeasonTeam, 22. Season, 23. Team, 24. Track.

CONTEXT D-03's list omits **PlayoffRound** (the entity present in `Playoff` → `PlayoffRound` → `PlayoffMatchup` chain — verified file `PlayoffRound.java` exists with `@Table(name = "playoff_rounds")` and a `@ManyToOne Playoff` owning relationship). D-03's "23" is an off-by-one omission in the CONTEXT documentation.

**How to avoid:**
- The planner MUST reconcile D-03's 23 vs. the runtime 24 BEFORE writing tests. Two options:
  1. **Recommended:** Update D-03 in CONTEXT.md (or in the new PROJECT.md "Backup Wire Contract" section) to read "24 entities" and add PlayoffRound to the illustrative list. The runtime topo-sort is the authoritative source.
  2. Add an explicit exclusion of `PlayoffRound` to the topo-sort (e.g., a marker annotation) — **NOT recommended** because PlayoffRound IS operative data that must round-trip.
- `BackupSchemaTopologyIT` should assert the runtime count and the runtime entity-class set, not a hand-written magic number. Recommended assertion shape:
  ```java
  assertThat(exportOrder).hasSize(24);     // matches the runtime org.ctc.domain.model.* @Entity count
  assertThat(exportOrder).extracting(EntityRef::entityClass)
        .containsExactlyInAnyOrder(Car.class, Track.class, ..., PlayoffRound.class);
  ```

**Warning signs:**
- Plan task says "assert exportOrder.size() == 23" → STOP, run a quick `grep -rn "@Entity" src/main/java/org/ctc/domain/model/` to verify the live count.
- The discuss-phase D-03 list and the runtime list diverge → reconcile via CONTEXT amendment, not by hacking the topo-sort.

**This is an [ASSUMED] correction:** the runtime count of 24 is verified from the codebase via `grep -rn "@Entity" src/main/java/org/ctc/domain/model/*.java | wc -l` (24 hits, see metadata at the bottom of this RESEARCH). The CONTEXT discuss-phase had `PlayoffRound` slip through the manual count.

### Pitfall P-6: Hibernate proxy on the `EntityType<?>` Java type lookup

**What goes wrong:** `attr.getJavaType()` for a `@ManyToOne` association returns the **target entity Java type**, not the attribute's Java type (which would be e.g. `Long` for the FK column). The Pattern 1 / Pattern 3 code is correct, but a naive reader might confuse `getJavaType()` with `getBindableJavaType()` or `getDeclaringType().getJavaType()`.

**How to avoid:** Use `attr.getJavaType()` for association attributes — this returns the FK target's entity class, which is exactly what the topo-sort needs. Confirm via Jakarta Persistence spec §5.1.

### Pitfall P-7: `BaseEntity` accidental extension on `DataImportAudit` triggers JPA boot failure on first commit

**What goes wrong:** A planner notices every other entity extends `BaseEntity` and adds `extends BaseEntity` to `DataImportAudit` "for consistency." On startup, `AuditingEntityListener` looks for `created_at` / `updated_at` columns; the V7 DDL does not declare them. Hibernate's `ddl-auto: validate` fires `SchemaManagementException: Schema-validation: missing column [created_at]` and the application fails to start.

**How to avoid:** Explicit JavaDoc on `DataImportAudit` (see Pattern 5) stating "intentionally does NOT extend BaseEntity." Plan-checker should verify this in the implemented file.

**Warning signs:**
- Boot fails with "Schema-validation: missing column [created_at] in table [data_import_audit]" → `extends BaseEntity` slipped in. Remove and re-test.

## Code Examples

All code examples are co-located with their patterns above. Cross-reference:
- `BackupSchema` + `EntityTopoSorter` → Pattern 1
- `BackupManifest` + `EntityRef` → Pattern 2
- `BackupObjectMapperConfig` → Pattern 3
- `V7__data_import_audit.sql` → Pattern 4
- `DataImportAudit` + `DataImportAuditRepository` → Pattern 5

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Jackson 2 `ObjectMapper` (mutable) | Jackson 3 `JsonMapper` (immutable, extends ObjectMapper) | Spring Boot 4.0 (Oct 2025) | `JsonMapper` is the immutable default; `ObjectMapper` remains accessible as the parent type. CTC's Phase 72 code uses `ObjectMapper` types in bean signatures, which auto-resolve to `JsonMapper` instances under Jackson 3 — code is forward-compatible. |
| `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS = true` (Jackson 2 default) | `WRITE_DATES_AS_TIMESTAMPS = false` (Jackson 3 default, now `DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS`) | Jackson 3.0 | Phase 72's `backupObjectMapper` config sets `false` **explicitly** for forward-compat — same end-state, no surprise on later Jackson bumps. |
| `Jackson2ObjectMapperBuilder` | Jackson native `JsonMapper.builder()` | Spring Boot 4 deprecates `Jackson2ObjectMapperBuilder` | Phase 72 uses `Jackson2ObjectMapperBuilder` for `@Primary defaultObjectMapper` because that is the builder `JacksonAutoConfiguration` still uses internally in SB 4.0.6. When SB drops the deprecation, swap to `JsonMapper.builder()` — single-line change. |
| Hand-written Reflections-based entity scanner | JPA `Metamodel` API (`EntityManagerFactory.getMetamodel().getEntities()`) | Jakarta Persistence 3.0 finalized; widely adopted since SB 2.x | Phase 72 D-04 uses the modern API — survives Hibernate version bumps. |
| `pom.xml` `build-info` + `BuildProperties` injection for app version | Maven resource-filtering `@project.version@` + `@Value("${app.version}")` | Either pattern still valid; CTC has used `@Value` since v1.0 | Phase 72 reuses the existing pattern — see Pitfall P-3. |

**Deprecated/outdated:**
- Jackson 2 `@JsonIdentityInfo(property = "id")` annotations on entities directly → use Phase 73 MixIns externally (Phase 73 concern, documented here only because CONTEXT references it).
- `TRUNCATE TABLE` for replace-all wipes → MariaDB auto-commits TRUNCATE, breaking transactional rollback. Phase 75 uses `DELETE FROM ... ` in FK-reverse order via native query. (Phase 72 does not write any wipe code — flagged for downstream awareness.)

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | **D-03's 23-entity list is off by one — runtime topo-sort produces 24, including PlayoffRound.** | §Pitfall P-5 | Test `BackupSchemaTopologyIT` fails with size-assertion mismatch; planner must amend D-03 in CONTEXT.md before plan dispatch. **Verified** via `grep -rn "@Entity" src/main/java/org/ctc/domain/model/*.java` returning 24 entities; PlayoffRound.java exists with `@Table(name="playoff_rounds")` and `@ManyToOne Playoff` owning relationship. Tagging this `[ASSUMED]` because the resolution path (update D-03 vs. exclude PlayoffRound) is a user/discuss-phase decision, not a research-only call. |
| A2 | `Jackson2ObjectMapperBuilder` is still the bean Spring Boot 4.0.6's `JacksonAutoConfiguration` uses internally to construct the default mapper, despite Jackson 3 native `JsonMapper.builder()` being available. | §Pattern 3 + §State of the Art | If SB 4.0.6 has already migrated `JacksonAutoConfiguration` to `JsonMapper.builder()`, the `@Bean @Primary defaultObjectMapper(Jackson2ObjectMapperBuilder builder)` may not auto-wire the builder. Mitigation: planner verifies via a quick boot-test of an empty `@Configuration` declaring `@Autowired private Jackson2ObjectMapperBuilder builder;` — if it injects, Pattern 3 works as-is. If not, swap to `JsonMapper.builder().build()` in the bean method. **Low risk** because SB 4.0.6 is a patch release within 4.0.x, not a bump to 4.1+. |
| A3 | The Phase 72 `BackupObjectMapperConfig` `@Primary defaultObjectMapper` does NOT need to register `JavaTimeModule` explicitly — Jackson's auto-discovered modules pick up `JavaTimeModule` from the classpath. | §Pattern 3 | If admin REST/AJAX paths depend on `JavaTimeModule` and Spring Boot's customizer doesn't auto-register it, ISO-8601 date serialization in admin AJAX could break post-Phase-72. Mitigation: `BackupObjectMapperConfigIT` should assert the `@Primary` mapper serializes `Instant.parse("2026-01-01T00:00:00Z")` to the same string before and after Phase 72. **Low risk** because `jackson-datatype-jsr310` is on the classpath and Spring's default discovery includes it. |
| A4 | The JPA Metamodel API exposes `mappedBy` information only on the inverse-side attribute (where it's actually declared), so restricting to owning-side `SingularAttribute` + `MANY_TO_ONE`/`ONE_TO_ONE` is sufficient to skip inverse-side edges. | §Pattern 1 `EntityTopoSorter` | If some quirk of Hibernate 7 exposes `mappedBy`-bearing collections as singular attributes, the topo-sort would mis-identify them as owning edges. Mitigation: assertion in `BackupSchemaTopologyIT` that walks every `@ManyToOne` field in `org.ctc.domain.model.*` via reflection and verifies the topo-sort's edge set matches. **Low risk** — verified pattern in the Kahn-algorithm Medium article cited above. |
| A5 | The runtime entity at the head of `EXPORT_ORDER` will be `Car` and `Track` (both have zero incoming dependencies). | §D-01 verification | If `Car` / `Track` have an unexpected `@ManyToOne` to e.g. `Season` (which would be inverted from the expected leaf shape), they would not sort at the head. Mitigation: `BackupSchemaTopologyIT` asserts `exportOrder.subList(0, 2).stream().map(EntityRef::entityClass).toList().containsAll(List.of(Car.class, Track.class))`. **Low risk** — verified by inspecting Car.java + Track.java for `@ManyToOne` (none). |

**If this table has entries:** Five items, mostly verifying-during-planning concerns. The dominant one is A1 (D-03's off-by-one) which requires a CONTEXT amendment.

## Open Questions

1. **OQ-1: D-03 says 23, runtime produces 24 (PlayoffRound). Which is the wire contract?** (RESOLVED 2026-05-11)
   - What we know: PlayoffRound is operative entity (playoff bracket rounds), not metadata. It MUST round-trip or playoff data is lost.
   - What's unclear: Is D-03's omission a typo in the CONTEXT documentation (intended 24, written 23), or did the discuss-phase intentionally exclude PlayoffRound?
   - Recommendation: Planner should propose **CONTEXT amendment to D-03 listing 24 entities including PlayoffRound** in the Phase 72 plan or send back to `/gsd-discuss-phase`. Do NOT silently override D-03 in the implementation.
   - Resolution: CONTEXT.md D-03 amended on 2026-05-11 to authorize 24 entities (PlayoffRound included). See 72-CONTEXT.md D-03 amendment.

2. **OQ-2: Should `EntityRef` carry a `String entityGraphName` field (D-07's planner-discretion clause)?**
   - What we know: Phase 73's `BackupExportService` will need `@EntityGraph` hints to avoid `LazyInitializationException` during streaming (Pitfall 3 of PITFALLS.md).
   - What's unclear: Does Phase 73 read graph names from `EntityRef`, or does it construct them ad-hoc per entity?
   - Recommendation: Defer to Phase 73 planning. Phase 72 ships `EntityRef(entityClass, tableName, fileName)` as locked; if Phase 73 needs the graph name, Phase 73 adds a 4th record component (records are extensible without breaking existing consumers since Phase 72 has no `EntityRef`-construction consumers).

3. **OQ-3: Should `BackupObjectMapperConfigIT` assert the default mapper's `FAIL_ON_UNKNOWN_PROPERTIES` is **false**, or just assert it differs from `backupObjectMapper`?**
   - What we know: `JacksonAutoConfiguration`'s default is `FAIL_ON_UNKNOWN_PROPERTIES=false` (Jackson 3 default + Spring Boot's `spring.jackson.deserialization.fail-on-unknown-properties=false` baseline).
   - What's unclear: If a future `application.yml` change globally flips this, the assertion catches an unrelated breakage.
   - Recommendation: Assert `defaultMapper.getDeserializationConfig().hasDeserializationFeatures(FAIL_ON_UNKNOWN_PROPERTIES.getMask()) == false` explicitly. This makes the IT a regression guard against accidental global flips — high-value, low-cost.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 25 (Eclipse Temurin) | Compile + runtime | ✓ (Phase 71 baseline verified by Phase 71's PLAT-05 BUILD SUCCESS) | 25 | None — hard requirement |
| Maven 3.9+ via `./mvnw` | Build | ✓ | 3.9.14 (verified `.mvn/wrapper/maven-wrapper.properties`) | None |
| Spring Boot 4.0.6 | Application framework | ✓ | 4.0.6 (verified pom.xml L8) | None — Phase 71 baseline |
| Hibernate 7.x (transitive) | JPA Metamodel API | ✓ | 7.x (via SB 4.0.6) | None |
| Jackson 3 (transitive) | JSON serialization | ✓ | 3.x (via SB 4.0.6 `spring-boot-starter-json`) | None |
| Flyway (transitive) | V7 migration runner | ✓ | via SB 4.0.6 `spring-boot-starter-flyway` | None |
| H2 2.x | Test profile DB | ✓ | (transitive, dev profile) | None — required for V7DataImportAuditMigrationIT |
| MariaDB | Prod/local/docker profile DB | ✓ on user's machine; CI uses `mariadb-migration-smoke.yml` (Phase 70/v1.9) | 10.x+ | If not available locally, defer MariaDB-side V7 verification to CI smoke gate |
| `flyway-mysql` dialect | H2 + MariaDB portable migrations | ✓ | transitive via SB 4.0.6 | None |
| `jackson-datatype-jsr310` (`JavaTimeModule`) | `Instant` serialization in `BackupManifest` | ✓ | transitive via SB 4.0.6 `spring-boot-starter-json` | None |
| `playwright-cli` (UI verification) | UI hint check | N/A | — | Phase 72 has no UI — playwright-cli not needed |

**Missing dependencies with no fallback:** None.

**Missing dependencies with fallback:** None — Phase 72 introduces no new external dependencies.

## Validation Architecture

> Nyquist sampling: every Phase 72 deliverable maps to at least one automated test command runnable in < 30 s. The phase is small enough that the full Failsafe IT suite is also < 60 s (Phase 71 baseline).

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) via Spring Boot Test starters |
| Config file | `pom.xml` lines 184-194 (Surefire) + 218-248 (Failsafe) — verified via `grep` |
| Quick run command | `./mvnw test` (Surefire only — runs `BackupManifestSerializationTest`, < 10 s) |
| Per-IT run | `./mvnw verify -Dit.test=BackupSchemaTopologyIT` (single IT, < 20 s) |
| Full suite command | `./mvnw verify` (Surefire + Failsafe + JaCoCo gate; Phase 71 baseline ~2 min) |
| Phase gate | `./mvnw verify` BUILD SUCCESS + JaCoCo line coverage ≥ 82 % |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SCHEMA-01 | `BackupSchema.SCHEMA_VERSION` is `1`, `BackupSchema.getExportOrder()` returns FK-respecting topo-sort over `org.ctc.domain.model.*` | IT | `./mvnw verify -Dit.test=BackupSchemaTopologyIT` | ❌ Wave 0 |
| SCHEMA-02 | `BackupManifest` record serializes via `backupObjectMapper` to `manifest.json` shape (snake_case keys, ISO-8601 dates) | unit | `./mvnw test -Dtest=BackupManifestSerializationTest` | ❌ Wave 0 |
| SCHEMA-03 | Flyway V7 creates `data_import_audit` with documented columns + types on H2 fresh schema | IT | `./mvnw verify -Dit.test=V7DataImportAuditMigrationIT` | ❌ Wave 0 |
| SCHEMA-04 | `@Qualifier("backupObjectMapper")` and default `@Primary` mapper are distinct beans; backup has `FAIL_ON_UNKNOWN_PROPERTIES=true`, default has `=false` | IT | `./mvnw verify -Dit.test=BackupObjectMapperConfigIT` | ❌ Wave 0 |
| IMPORT-08 | `DataImportAudit` is structurally excluded from `getExportOrder()` (because it's in `org.ctc.backup.audit`, not `org.ctc.domain.model`) | IT | `./mvnw verify -Dit.test=BackupSchemaExclusionIT` | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `./mvnw test -Dtest={modifiedTestClass}` (Surefire-only, target the changed test, < 10 s)
- **Per wave merge:** `./mvnw verify -Dit.test=Backup*IT,V7*IT` (all Phase 72 ITs, < 60 s)
- **Phase gate:** `./mvnw verify` (full Surefire + Failsafe + JaCoCo) green before `/gsd-verify-work`

### Wave 0 Gaps

All 5 test files are NEW. No existing test infrastructure covers Phase 72 deliverables. Wave 0 (or Wave 1, depending on plan structure) must create:

- [ ] `src/test/java/org/ctc/backup/schema/BackupSchemaTopologyIT.java` — covers SCHEMA-01 + asserts D-04 topo-sort correctness + Team.parentTeam self-FK handling + immutability of returned list + 24-entity count (see Pitfall P-5 / OQ-1)
- [ ] `src/test/java/org/ctc/backup/schema/BackupSchemaExclusionIT.java` — covers IMPORT-08 structural guarantee
- [ ] `src/test/java/org/ctc/backup/schema/BackupManifestSerializationTest.java` — covers SCHEMA-02; pure unit test through `new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(manifest)` OR via Spring-loaded `backupObjectMapper` if the planner prefers IT shape
- [ ] `src/test/java/org/ctc/backup/config/BackupObjectMapperConfigIT.java` — covers SCHEMA-04 + Pitfall P-2 (assert default mapper is NOT FAIL_ON_UNKNOWN_PROPERTIES=true, defending against the auto-config back-off)
- [ ] `src/test/java/db/migration/V7DataImportAuditMigrationIT.java` — covers SCHEMA-03; mirrors `V4MigrationSmokeIT.java` pattern (`@SpringBootTest(classes = CtcManagerApplication.class)`, `@ActiveProfiles("dev")`, `JdbcTemplate.queryForList` over JDBC metadata)
- [ ] **Optional but recommended:** `src/test/java/org/ctc/backup/schema/EntityTopoSorterTest.java` — pure unit test on the extracted helper (no Spring context needed) covering self-FK handling, multiple-incoming-edge tie-break, and the cycle-detection guard. Adds ~80 LOC, < 1 s test runtime, replaces 1 of the IT's slower boot cycles for the same coverage.

Framework install: not needed — JUnit 5 + AssertJ + Spring Boot Test are already on the classpath (verified `pom.xml`).

### Phase 72 Observability Hooks

These hooks form the validation surface for downstream phases (73-77) and human UAT:

1. **`BackupSchema.@PostConstruct` log line** (Pattern 1 code, `log.info`):
   ```
   BackupSchema initialized: SCHEMA_VERSION=1, exportOrder size=24, entities=[cars, tracks, seasons, season_phases, ...]
   ```
   Surfaces in `target/spring.log` / `logs/ctc-manager.log` at every app startup. Phase 73+ can grep this line in CI to verify the wire contract before running export.

2. **Health-check absence:** Phase 72 deliberately does NOT add a `BackupSchemaHealthIndicator` `@Component` (actuator integration would be over-engineered for an infrastructure-only phase). The boot-time log line + `BackupSchemaTopologyIT` together cover the validation surface.

3. **JaCoCo coverage line:** Phase 72 adds ~250 LOC of production code (BackupSchema 50 + EntityTopoSorter 60 + BackupManifest 15 + EntityRef 20 + BackupObjectMapperConfig 40 + DataImportAudit 50 + DataImportAuditRepository 5 + V7 SQL 0 [non-Java]) and ~300 LOC of test code. Net coverage impact is neutral to positive — every production class has at least one IT or unit test exercising it.

4. **PROJECT.md anchor line:** the new "Backup Wire Contract (v1.10)" section + "data_import_audit out of export scope" Decisions row are the human-readable validation surface — a future agent reviewing the wire contract reads PROJECT.md first.

## Sources

### Primary (HIGH confidence)

- `pom.xml` (verified L8 `spring-boot-starter-parent: 4.0.6`, L26 Thymeleaf 3.1.5, plugin block L174-L290 — verified no `build-info` execution)
- `src/main/resources/application.yml` (verified L1-L3 `app.version: @project.version@`)
- `src/main/java/org/ctc/admin/controller/GlobalModelAdvice.java` (verified `@Value("${app.version}")` pattern)
- `src/main/java/org/ctc/domain/model/Team.java` (verified `Team.parentTeam` self-FK + `@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "parent_team_id")`)
- `src/main/java/org/ctc/domain/model/BaseEntity.java` (verified `@MappedSuperclass @EntityListeners(AuditingEntityListener.class)` — the listener Phase 75 bypasses)
- `src/main/java/db/migration/V4__MigrateSeasonsToPhases.java` (precedent for Java-based Flyway migration; V7 deliberately stays SQL per D-10)
- `src/test/java/db/migration/V4MigrationSmokeIT.java` (template for V7DataImportAuditMigrationIT — `@SpringBootTest(classes = CtcManagerApplication.class)`, `@ActiveProfiles("dev")`)
- `.planning/research/SUMMARY.md` §Phase 2 (synthesized rationale)
- `.planning/research/STACK.md` §Audit logging (L135) + §V7 migration (L391)
- `.planning/research/PITFALLS.md` §Pitfall 9 + §Schema-version drift (L233+) + §Flyway+audit table inclusion (L468)
- `.planning/codebase/CONVENTIONS.md` (Lombok + entity + repository conventions)
- `.planning/codebase/TESTING.md` (Surefire/Failsafe split, BDD naming)
- `CLAUDE.md` (Constraints + Lombok + Flyway immutability)
- Jakarta Persistence 3.0 specification §5.1 (`Metamodel` API contract)
- [Spring Boot Issue #47379 — Auto-configured JsonMapper backs off when any type of ObjectMapper is defined](https://github.com/spring-projects/spring-boot/issues/47379)
- [Spring Boot Issue #22403 — Make it easier to define a bean in addition to an auto-configured bean of the same type](https://github.com/spring-projects/spring-boot/issues/22403)
- [Spring Boot Issue #42598 — Clarify why @Primary is recommended when defining your own ObjectMapper](https://github.com/spring-projects/spring-boot/issues/42598)
- [Spring Blog — Introducing Jackson 3 support in Spring (2025-10-07)](https://spring.io/blog/2025/10/07/introducing-jackson-3-support-in-spring/)
- [H2 Source — DataType.java (LONGTEXT alias for CLOB)](https://github.com/h2database/h2database/blob/master/h2/src/main/org/h2/value/DataType.java)
- [MariaDB Docs — LONGTEXT](https://mariadb.com/docs/server/reference/data-types/string-data-types/longtext.md)

### Secondary (MEDIUM confidence)

- [Vlad Mihalcea — The best way to use Java Records with JPA and Hibernate](https://vladmihalcea.com/java-records-jpa-hibernate/) (record-entity infeasibility)
- [Thorben Janssen — Java Records with Hibernate and JPA](https://thorben-janssen.com/java-records-hibernate-jpa/) (record-entity infeasibility, embeddable support)
- [Medium / Tharindu Jayawardhana — How Kahn's Algorithm Helped Me Solve Database Schema Dependencies](https://medium.com/@tharinduimalka915/how-kahns-algorithm-helped-me-solve-database-schema-dependencies-2b7e54142fd5) (algorithm + JPA Metamodel application sketch)
- [Baeldung — Spring Boot: Customize the Jackson ObjectMapper](https://www.baeldung.com/spring-boot-customize-jackson-objectmapper)
- [Dan Vega — Jackson 3 in Spring Boot 4: JsonMapper, JSON Views, and What's Changed](https://www.danvega.dev/blog/2025/11/10/jackson-3-spring-boot-4) (JsonMapper-vs-ObjectMapper)
- [Itnext / Hantsy — An Introduction to Jackson 3 in Spring 7 and Spring Boot 4](https://itnext.io/an-introduction-to-jackson-3-in-spring-7-and-spring-boot-4-cba114aa36b1)

### Tertiary (LOW confidence — flagged for verification during planning)

- A2 in Assumptions Log: `Jackson2ObjectMapperBuilder` still being the SB 4.0.6 default-mapper builder. Verify with a boot-smoke `@Autowired Jackson2ObjectMapperBuilder` injection during Wave 2.

## Metadata

**Confidence breakdown:**
- Standard stack: **HIGH** — all dependencies verified against pom.xml + Spring Boot 4.0.6 release notes + Jackson 3 official Spring blog
- Architecture patterns: **HIGH** — Patterns 1, 2, 4, 5 follow CTC convention precedent (verified entity / repository / Flyway shapes). Pattern 3 (ObjectMapper isolation) is **HIGH** with one [ASSUMED] (A2) about builder availability.
- Pitfalls: **HIGH** — all 7 pitfalls cross-referenced to official GitHub issues, vendor docs, or live codebase grep evidence. P-1 (records) and P-2 (auto-config back-off) are the load-bearing planner corrections; both are multi-source-verified.

**Codebase entity-count verification** (run 2026-05-11):
```
$ grep -l "@Entity" src/main/java/org/ctc/domain/model/*.java | wc -l
24
```
Entities (alphabetical): Car, Driver, Match, MatchScoring, Matchday, PhaseTeam, Playoff, PlayoffMatchup, PlayoffRound, PlayoffSeed, PsnAlias, Race, RaceAttachment, RaceLineup, RaceResult, RaceScoring, RaceSettings, Season, SeasonDriver, SeasonPhase, SeasonPhaseGroup, SeasonTeam, Team, Track.

CONTEXT D-03 list = 23 entities (missing PlayoffRound). See OQ-1 for reconciliation path.

**Research date:** 2026-05-11
**Valid until:** 2026-06-10 (30 days for stable Spring Boot 4.0.x patch baseline; re-research if SB bumps to 4.1+ or Jackson bumps to 3.2+)

---

*Phase: 72-backup-wire-contract-schema-manifest-objectmapper-audit-log-*
*Research completed: 2026-05-11*
*Ready for planning: yes — subject to OQ-1 reconciliation (D-03 entity count) being resolved either in CONTEXT amendment or in the Phase 72 plan dispatch*
