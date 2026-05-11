# Phase 72: Backup Wire Contract — Schema, Manifest, ObjectMapper, Audit-Log Scope - Pattern Map

**Mapped:** 2026-05-11
**Files analyzed:** 14 (9 production / 5 test) + 2 docs
**Analogs found:** 14 / 14 (100 %)

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `src/main/java/org/ctc/backup/schema/BackupSchema.java` | Component (Spring-managed singleton) | metadata-introspection (read JPA Metamodel at startup) | `src/main/java/org/ctc/admin/WebConfig.java` (closest `@Configuration`/bean-init shape) + `src/main/java/org/ctc/admin/controller/GlobalModelAdvice.java` (`@Value` + advice singleton) | role-match (no prior `@Component` consuming `EntityManagerFactory.getMetamodel()` exists) |
| `src/main/java/org/ctc/backup/schema/EntityRef.java` | Record DTO | transform (`EntityType<?>` → `EntityRef`) | `src/main/java/org/ctc/admin/dto/MatchdayGraphicData.java` (record DTO precedent) | role-match |
| `src/main/java/org/ctc/backup/schema/EntityTopoSorter.java` | Component (helper) | pure transform (Kahn's algorithm) | none (algorithmic helper — no codebase precedent for graph algorithms) | no-match (use RESEARCH Pattern 1 code excerpt verbatim) |
| `src/main/java/org/ctc/backup/schema/BackupManifest.java` | Record DTO (wire-format spec) | transform (Java record ↔ JSON via Jackson) | `src/main/java/org/ctc/admin/dto/MatchdayGraphicData.java` | role-match |
| `src/main/java/org/ctc/backup/config/BackupObjectMapperConfig.java` | Configuration (`@Bean` factory) | request-response (DI graph wiring) | `src/main/java/org/ctc/admin/SecurityConfig.java` (`@Configuration` + `@Bean` precedent) | role-match |
| `src/main/java/org/ctc/backup/audit/DataImportAudit.java` | JPA Entity (deliberately NOT extending `BaseEntity`) | CRUD (write-only in Phase 75) | `src/main/java/org/ctc/domain/model/Season.java` (Lombok-annotated entity shape) + `src/main/java/org/ctc/domain/model/BaseEntity.java` (the class explicitly NOT extended) | exact for shape, deviates on `BaseEntity` |
| `src/main/java/org/ctc/backup/audit/DataImportAuditRepository.java` | Repository (Spring Data) | CRUD | `src/main/java/org/ctc/domain/repository/SeasonRepository.java` | exact |
| `src/main/resources/db/migration/V7__data_import_audit.sql` | Flyway SQL migration | DDL (one-time forward migration) | `src/main/resources/db/migration/V2__add_fk_indexes.sql` (pure-SQL `CREATE INDEX` precedent) + `V3__add_season_phase_tables.sql` (pure-SQL `CREATE TABLE` precedent) | exact |
| `src/test/java/org/ctc/backup/schema/BackupSchemaTopologyIT.java` | Failsafe IT (Spring context boot) | event-driven (assert on `@PostConstruct` output) | `src/test/java/org/ctc/domain/repository/PhaseTeamRepositoryIT.java` | exact |
| `src/test/java/org/ctc/backup/schema/BackupSchemaExclusionIT.java` | Failsafe IT | event-driven (assert exclusion list) | `src/test/java/org/ctc/domain/repository/PhaseTeamRepositoryIT.java` | exact |
| `src/test/java/org/ctc/backup/schema/BackupManifestSerializationTest.java` | Surefire unit test | transform (record → JSON round-trip) | `src/test/java/org/ctc/gt7sync/Gt7ScraperServiceTest.java` (plain JUnit 5 + AssertJ unit test) | role-match |
| `src/test/java/org/ctc/backup/config/BackupObjectMapperConfigIT.java` | Failsafe IT | request-response (DI graph assertion) | `src/test/java/org/ctc/domain/repository/PhaseTeamRepositoryIT.java` | role-match |
| `src/test/java/db/migration/V7DataImportAuditMigrationIT.java` | Failsafe IT (post-migration JDBC metadata query) | event-driven (assert DDL after Flyway runs) | `src/test/java/db/migration/V4MigrationSmokeIT.java` | exact |
| `PROJECT.md` | Documentation | edit (table row + new section) | (existing PROJECT.md structure) | doc edit |
| `.planning/REQUIREMENTS.md` | Documentation | edit (inline footnote on EXPORT-04) | (existing REQUIREMENTS.md structure) | doc edit |

---

## Pattern Assignments

### `src/main/java/org/ctc/backup/schema/BackupSchema.java` (Component, metadata-introspection)

**Analog:** `src/main/java/org/ctc/admin/controller/GlobalModelAdvice.java` (closest `@Component`-style bean) + RESEARCH §Pattern 1 (Spring-Metamodel consumer — no codebase precedent).

**Deviations from analogs:**
- Belongs to NEW package `org.ctc.backup.schema` (NOT `org.ctc.domain.model` — package filter D-06 must structurally exclude it).
- Injects `EntityManagerFactory` (no existing component does this — closest are JPA repositories, which inject via Spring Data, not direct field).
- Uses `@PostConstruct` to populate state at startup (no existing CTC component does this; pattern verified in RESEARCH §Pattern 1).

**Imports pattern** (verified against `SecurityConfig.java` + RESEARCH §Pattern 1):
```java
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
```
(Order per CONVENTIONS.md L271-L278: own → jakarta → lombok → spring → java.)

**Class-level annotations** (cribbed from `ScoringService` shape per CONVENTIONS.md L57-L67):
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class BackupSchema {
    public static final int SCHEMA_VERSION = 1;
    private final EntityManagerFactory entityManagerFactory;
    private final EntityTopoSorter entityTopoSorter;
    private List<EntityRef> exportOrder;
```

**Logging pattern** (verified against CONVENTIONS.md L250-L262):
```java
log.info("BackupSchema initialized: SCHEMA_VERSION={}, exportOrder size={}, entities=[{}]",
        SCHEMA_VERSION,
        exportOrder.size(),
        exportOrder.stream().map(EntityRef::tableName).reduce((a, b) -> a + ", " + b).orElse(""));
```
(Parameterized `{}` placeholders, no string concatenation — matches existing `log.info("Added team {} to season {}", ...)` from `ScoringService`.)

**`@PostConstruct` + immutable assignment** (from RESEARCH §Pattern 1 — no codebase precedent):
```java
@PostConstruct
void initializeExportOrder() {
    var entityTypes = entityManagerFactory.getMetamodel().getEntities().stream()
            .filter(et -> et.getJavaType().getPackage().getName().startsWith("org.ctc.domain.model"))
            .toList();
    this.exportOrder = List.copyOf(entityTopoSorter.sort(entityTypes));
}
```

---

### `src/main/java/org/ctc/backup/schema/EntityRef.java` (Record DTO)

**Analog:** `src/main/java/org/ctc/admin/dto/MatchdayGraphicData.java` (record DTO precedent in codebase).

**Record shape pattern** (verified against `MatchdayGraphicData.java` lines 5-12):
```java
public record MatchdayGraphicData(
        String matchdayLabel,
        String seasonName,
        String seasonYear,
        String ctcLogoBase64,
        String fontBase64,
        List<MatchGraphicRow> matches
) {
```

**Apply to `EntityRef`** (from RESEARCH §Pattern 2 lines 485-498):
```java
public record EntityRef(
        Class<?> entityClass,
        String tableName,
        String fileName
) {
    public static EntityRef fromEntityType(EntityType<?> et) {
        Table tableAnno = et.getJavaType().getAnnotation(Table.class);
        String table = (tableAnno != null && !tableAnno.name().isBlank())
                ? tableAnno.name()
                : et.getName().toLowerCase();
        String file = "data/" + table.replace('_', '-') + ".json";
        return new EntityRef(et.getJavaType(), table, file);
    }
}
```

**Deviation from analog:** `MatchdayGraphicData` is a pure data carrier with zero static factories; `EntityRef` adds a static factory `fromEntityType(...)` because the record construction logic is non-trivial (filename derivation rule from D-07). The factory is the right home — keeps construction logic out of the topo-sorter.

---

### `src/main/java/org/ctc/backup/schema/EntityTopoSorter.java` (Component, helper)

**Analog:** **No codebase precedent.** No existing CTC code performs graph algorithms or JPA Metamodel introspection. Use RESEARCH §Pattern 1 (lines 383-432) verbatim. Mark as Claude's-discretion extraction (per D-05 + RESEARCH recommendation for unit-testability).

**Spring registration:** package-private `@Component` (RESEARCH §Pattern 1 line 383: `@Component class EntityTopoSorter`) — visible within `org.ctc.backup.schema` for unit tests + `BackupSchema` injection; invisible outside the package.

**Self-FK skip pattern (Team.parentTeam)** — the only non-obvious algorithmic detail (RESEARCH §Pattern 1 line 407):
```java
Class<?> ownerClass = owner.getJavaType();
if (depClass.equals(ownerClass)) continue;        // self-FK (Team.parentTeam) → skip
```

---

### `src/main/java/org/ctc/backup/schema/BackupManifest.java` (Record DTO, wire-format spec)

**Analog:** `src/main/java/org/ctc/admin/dto/MatchdayGraphicData.java` (record precedent).

**Class shape** (from RESEARCH §Pattern 2 lines 461-467):
```java
public record BackupManifest(
        int schemaVersion,
        String appVersion,
        Instant exportDate,
        Map<String, Long> tableCounts
) {
}
```

**Deviations from analog:**
- No nested helper records (`MatchdayGraphicData` declares a nested `MatchGraphicRow`); `BackupManifest` is flat.
- Field naming is camelCase in Java but Jackson serializes to **snake_case** at the JSON level — this is configured at the **`backupObjectMapper`** level (via `PropertyNamingStrategies.SNAKE_CASE` if planner picks it) OR via `@JsonProperty("schema_version")` per field. **Planner decision** — RESEARCH §Pattern 3 does NOT set a naming strategy; the simpler path is per-field `@JsonProperty` annotations on the record components. The snake_case JSON shape is asserted in `BackupManifestSerializationTest`.

---

### `src/main/java/org/ctc/backup/config/BackupObjectMapperConfig.java` (Configuration)

**Analog:** `src/main/java/org/ctc/admin/SecurityConfig.java` (`@Configuration` + `@Bean` precedent).

**Imports pattern** (verified against `SecurityConfig.java` lines 1-9 + RESEARCH §Pattern 3 lines 514-525):
```java
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
```

**Class-level annotations** (matches `SecurityConfig.java` line 11-12 shape — but WITHOUT `@Profile` since backup config is loaded in all profiles):
```java
@Configuration
public class BackupObjectMapperConfig {
```

**`@Bean` method pattern** (verified against `SecurityConfig.java` line 15-17 — `@Bean` returning a configured object):
```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(...).build();
}
```

**CRITICAL DEVIATION (RESEARCH Pitfall P-2):** CONTEXT D-11 instructs "default Spring `ObjectMapper` remains untouched" but `@ConditionalOnMissingBean(ObjectMapper.class)` means ANY new `ObjectMapper` bean disables the auto-config. Planner MUST declare BOTH beans:

```java
@Bean
@Primary
public ObjectMapper defaultObjectMapper(Jackson2ObjectMapperBuilder builder) {
    return builder.build();           // preserves auto-config behavior for admin REST/AJAX
}

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
```

(Phase 72 ships zero MixIn beans — Spring DI injects an empty `List<Module>`. Phase 73 adds the `@Component`-tagged `Module` beans.)

---

### `src/main/java/org/ctc/backup/audit/DataImportAudit.java` (JPA Entity)

**Analog:** `src/main/java/org/ctc/domain/model/Season.java` for the Lombok+JPA annotation shape; `src/main/java/org/ctc/domain/model/BaseEntity.java` is the class explicitly NOT extended.

**Imports pattern** (verified against `Season.java` lines 1-11):
```java
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;
import java.util.UUID;
```

**Class-level annotations** (`Season.java` lines 13-19 — Lombok stack):
```java
@Entity
@Table(name = "seasons")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"phases", "seasonDrivers", "seasonTeams", "cars", "tracks"})
public class Season extends BaseEntity {
```

**Apply to `DataImportAudit`** (RESEARCH §Pattern 5 lines 642-650; DEVIATION: does NOT extend `BaseEntity`):
```java
@Entity
@Table(name = "data_import_audit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class DataImportAudit {       // ← deliberate omission of `extends BaseEntity` (D-08)
```

**ID strategy pattern** (verified against `Season.java` lines 21-23):
```java
@Id
@GeneratedValue(strategy = GenerationType.UUID)
private UUID id;
```

**Column patterns** (verified against `Season.java` lines 26-38):
```java
@NotBlank
@Column(nullable = false)
private String name;

@Column(name = "season_year", nullable = false)
private int year;
```

**Apply to `DataImportAudit`** (RESEARCH §Pattern 5 — note `@Lob` + `columnDefinition = "LONGTEXT"` for JSON-text columns per D-09):
```java
@NotNull
@Column(name = "executed_at", nullable = false)
private Instant executedAt;

@NotBlank
@Lob
@Column(name = "table_counts_wiped", nullable = false, columnDefinition = "LONGTEXT")
private String tableCountsWiped;
```

**Critical deviations the planner must enforce:**
1. **NO `extends BaseEntity`** — auditing-listener bypass for Phase 75 IMPORT-05 (D-08 + RESEARCH §Pattern 5 lines 614-615). Add a one-line JavaDoc explaining why.
2. **Package `org.ctc.backup.audit`** — NOT `org.ctc.domain.model` — enforces D-06 structural exclusion from `BackupSchema.exportOrder`.
3. **`@AllArgsConstructor + @Builder`** are added (Season does NOT have these) so Phase 75's writer can construct rows fluently.
4. **NOT a Java record** — Hibernate 7 / JPA cannot proxy records (RESEARCH Pitfall P-1). CONTEXT D-08 hedged "Lombok class is acceptable" — research locks Lombok as the only viable shape.

---

### `src/main/java/org/ctc/backup/audit/DataImportAuditRepository.java` (Repository)

**Analog:** `src/main/java/org/ctc/domain/repository/SeasonRepository.java` (exact match for `JpaRepository<Entity, UUID>` shape).

**Full file pattern** (verified against `SeasonRepository.java` lines 1-10):
```java
package org.ctc.domain.repository;

import org.ctc.domain.model.Season;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeasonRepository extends JpaRepository<Season, UUID> {
```

**Apply to `DataImportAuditRepository`** (RESEARCH §Pattern 5 lines 687-698):
```java
package org.ctc.backup.audit;          // ← deviation: NOT org.ctc.domain.repository (D-06)

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface DataImportAuditRepository extends JpaRepository<DataImportAudit, UUID> {
}
```

**Deviations from analog:**
1. Package is `org.ctc.backup.audit` (co-located with the entity), NOT `org.ctc.domain.repository`. This enforces D-06.
2. No custom finder methods — Phase 72 only needs the default `JpaRepository` surface (CONTEXT Claude's Discretion). Phase 75 calls only `save(...)`.

---

### `src/main/resources/db/migration/V7__data_import_audit.sql` (Flyway SQL migration)

**Analog:** `src/main/resources/db/migration/V3__add_season_phase_tables.sql` (closest pure-SQL `CREATE TABLE` precedent in the codebase) + `V1__initial_schema.sql` (for H2/MariaDB header comment).

**SQL header pattern** (verified against `V3__add_season_phase_tables.sql` lines 1-3 + `V1__initial_schema.sql` lines 1-2):
```sql
-- Add Season Phase tables (Season -> Phase -> Group hierarchy)
-- Adds nullable phase_id/group_id FKs on matchdays and playoffs (additive; non-null flip in V?? after data migration)
-- Compatible with H2 2.x and MariaDB 10.7+
```

**`CREATE TABLE` shape** (verified against `V3__add_season_phase_tables.sql` lines 5-26 — UUID PK, snake_case columns, `created_at`/`updated_at` with `DEFAULT CURRENT_TIMESTAMP`):
```sql
CREATE TABLE season_phases (
    id UUID PRIMARY KEY,
    season_id UUID NOT NULL,
    sort_index INT NOT NULL,
    ...
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_seasonphase_season FOREIGN KEY (season_id) REFERENCES seasons(id),
    ...
);
```

**Apply to `V7__data_import_audit.sql`** (RESEARCH §Pattern 4 lines 594-607 + D-09 column DDL lock):
```sql
-- Phase 72: data_import_audit table — operational audit log for backup imports.
-- This table is PERMANENTLY OUT OF EXPORT SCOPE (IMPORT-08, see PROJECT.md).
-- Compatible with H2 2.x and MariaDB 10.7+.

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

CREATE INDEX idx_data_import_audit_executed_at ON data_import_audit (executed_at);
```

**Index pattern** (verified against `V2__add_fk_indexes.sql` line 5 — `CREATE INDEX IF NOT EXISTS idx_<table>_<col>` style). **Deviation:** V7 uses plain `CREATE INDEX` (NOT `IF NOT EXISTS`) because this is a fresh table from V7 — the index is guaranteed not to pre-exist on a fresh schema. `IF NOT EXISTS` is the pattern when adding indexes to pre-existing tables (V2's use case). Planner may choose to keep `IF NOT EXISTS` for defensive consistency — both work on H2 + MariaDB.

**Deviations from V3 analog:**
- No `created_at`/`updated_at` columns. The entity does NOT extend `BaseEntity` (D-08), so the auditing columns are absent by design.
- No FK constraints (the table has no FKs — it is a leaf audit table).
- `LONGTEXT` is used for JSON-shape text columns instead of `VARCHAR(255)` (D-09).

---

### `src/test/java/org/ctc/backup/schema/BackupSchemaTopologyIT.java` (Failsafe IT)

**Analog:** `src/test/java/org/ctc/domain/repository/PhaseTeamRepositoryIT.java` (exact match for `@SpringBootTest @ActiveProfiles("dev") @Transactional` pattern).

**Bootstrap pattern** (verified against `PhaseTeamRepositoryIT.java` lines 25-28):
```java
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class PhaseTeamRepositoryIT {

    @Autowired
    private SeasonRepository seasonRepository;
```

**Apply to `BackupSchemaTopologyIT`:**
```java
@SpringBootTest
@ActiveProfiles("dev")
class BackupSchemaTopologyIT {

    @Autowired
    private BackupSchema backupSchema;
```

**Deviation:** Drop `@Transactional` — this IT is read-only against the bean's `@PostConstruct` output (no DB writes). The Spring context boot is the trigger.

**Test method naming pattern** (verified against `PhaseTeamRepositoryIT.java` lines 54, 73, 102, 119 — CLAUDE.md §Test Naming):
```java
@Test
void givenPhaseTeams_whenFindByPhaseId_thenReturnsAll() {
    // given
    ...
    // when
    ...
    // then
    assertThat(result).hasSize(2);
}
```

**Apply to `BackupSchemaTopologyIT`:**
```java
@Test
void givenSpringContext_whenGetExportOrder_thenReturns23Entities() { ... }

@Test
void givenSpringContext_whenGetExportOrder_thenManyToOneDependenciesPrecedeOwners() { ... }

@Test
void givenSpringContext_whenGetExportOrder_thenReturnedListIsImmutable() { ... }

@Test
void givenTeamSelfFK_whenGetExportOrder_thenTeamAppearsExactlyOnce() { ... }
```

**Note (RESEARCH OQ-1):** CONTEXT D-03 says "23 entities" but the live codebase has 24 candidate entities under `org.ctc.domain.model` (PlayoffRound — see `find` output, line 13 of model dir). Planner must reconcile: either (a) assert the runtime topo-sort returns 23 entities and explicitly exclude `PlayoffRound` (would require a new filter mechanism — breaks D-06's "structural-only" promise), or (b) assert `>= 23` and document `PlayoffRound` as a 24th expected entity. **Recommended:** option (b) — let the test assert the **actual** topo-sort output size (24) and add a comment that the 23-entity CONTEXT figure pre-dated `PlayoffRound`'s discovery. This is a planner decision; the analog test pattern supports either.

---

### `src/test/java/org/ctc/backup/schema/BackupSchemaExclusionIT.java` (Failsafe IT)

**Analog:** `src/test/java/org/ctc/domain/repository/PhaseTeamRepositoryIT.java` (same `@SpringBootTest` pattern).

**Apply to `BackupSchemaExclusionIT`:**
```java
@SpringBootTest
@ActiveProfiles("dev")
class BackupSchemaExclusionIT {

    @Autowired
    private BackupSchema backupSchema;

    @Test
    void givenSpringContext_whenGetExportOrder_thenDataImportAuditIsNotPresent() {
        // when
        var exportOrder = backupSchema.getExportOrder();

        // then — D-06 structural guarantee
        assertThat(exportOrder).extracting(EntityRef::entityClass)
                .doesNotContain(DataImportAudit.class);
        assertThat(exportOrder).extracting(EntityRef::tableName)
                .doesNotContain("data_import_audit");
    }
}
```

---

### `src/test/java/org/ctc/backup/schema/BackupManifestSerializationTest.java` (Surefire unit test)

**Analog:** `src/test/java/org/ctc/gt7sync/Gt7ScraperServiceTest.java` (plain JUnit 5 + AssertJ unit test, no Spring context).

**Bootstrap pattern** (verified against `Gt7ScraperServiceTest.java` lines 13-20):
```java
class Gt7ScraperServiceTest {

    private Gt7ScraperService scraperService;

    @BeforeEach
    void setUp() {
        scraperService = new Gt7ScraperService();
    }
```

**Apply to `BackupManifestSerializationTest`:**
```java
class BackupManifestSerializationTest {

    private ObjectMapper backupMapper;

    @BeforeEach
    void setUp() {
        // Inline the exact same config as BackupObjectMapperConfig.backupObjectMapper(...)
        backupMapper = new ObjectMapper();
        backupMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        backupMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        backupMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void givenSampleManifest_whenSerializeThroughBackupMapper_thenJsonHasSnakeCaseShape() {
        // given
        var manifest = new BackupManifest(1, "1.10.0", Instant.parse("2026-05-11T10:00:00Z"), Map.of("seasons", 5L));
        // when
        String json = backupMapper.writeValueAsString(manifest);
        // then
        assertThatJson(json).node("schema_version").isEqualTo(1);
        assertThatJson(json).node("export_date").isString().startsWith("2026-05-11T10:00:00");
    }
```

**Deviation:** This is a **`*Test.java`** file (Surefire) — not `*IT.java`. Surefire convention enforced by pom.xml (CONTEXT references it).

---

### `src/test/java/org/ctc/backup/config/BackupObjectMapperConfigIT.java` (Failsafe IT)

**Analog:** `src/test/java/org/ctc/domain/repository/PhaseTeamRepositoryIT.java` (`@SpringBootTest` + `@Autowired` injection pattern).

**Apply to `BackupObjectMapperConfigIT`:**
```java
@SpringBootTest
@ActiveProfiles("dev")
class BackupObjectMapperConfigIT {

    @Autowired
    private ObjectMapper defaultMapper;

    @Autowired
    @Qualifier("backupObjectMapper")
    private ObjectMapper backupMapper;

    @Test
    void givenTwoMapperBeans_whenComparingInstances_thenTheyAreDifferent() {
        assertThat(defaultMapper).isNotSameAs(backupMapper);
    }

    @Test
    void givenBackupMapper_whenCheckingFailOnUnknownProperties_thenItIsEnabled() {
        assertThat(backupMapper.getDeserializationConfig()
                .isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isTrue();
    }

    @Test
    void givenDefaultMapper_whenCheckingFailOnUnknownProperties_thenItIsDisabled() {
        // Spring Boot default: FAIL_ON_UNKNOWN_PROPERTIES is false
        assertThat(defaultMapper.getDeserializationConfig()
                .isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();
    }
}
```

---

### `src/test/java/db/migration/V7DataImportAuditMigrationIT.java` (Failsafe IT, post-migration JDBC metadata)

**Analog:** `src/test/java/db/migration/V4MigrationSmokeIT.java` (EXACT MATCH — same package, same intent: assert post-migration schema shape via Spring context bootstrap).

**Bootstrap pattern** (verified against `V4MigrationSmokeIT.java` lines 38-53):
```java
@SpringBootTest(classes = CtcManagerApplication.class)        // ← required: db.migration is outside org.ctc scan tree
@ActiveProfiles("dev")
@Transactional
class V4MigrationSmokeIT {

    @Autowired
    private SeasonRepository seasonRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;
```

**Apply to `V7DataImportAuditMigrationIT`:**
```java
@SpringBootTest(classes = CtcManagerApplication.class)        // ← MANDATORY (same reason)
@ActiveProfiles("dev")
class V7DataImportAuditMigrationIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    void givenH2WithV7Applied_whenInspectingDataImportAuditColumns_thenAllExpectedColumnsExist() {
        try (Connection c = dataSource.getConnection()) {
            DatabaseMetaData md = c.getMetaData();
            try (ResultSet rs = md.getColumns(null, null, "DATA_IMPORT_AUDIT", null)) {
                Set<String> columns = new HashSet<>();
                while (rs.next()) columns.add(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                assertThat(columns).contains(
                        "id", "executed_at", "executed_by", "schema_version",
                        "table_counts_wiped", "table_counts_restored",
                        "source_filename", "success");
            }
        }
    }
}
```

**Deviations from V4 analog:**
- Drops `@Transactional` (this IT only reads JDBC metadata — no DML).
- Uses `DataSource.getConnection().getMetaData()` instead of `JdbcTemplate` for column-type inspection (more portable than `SHOW COLUMNS` SQL).

---

## Shared Patterns

### Lombok Annotation Stack

**Source:** `src/main/java/org/ctc/domain/service/ScoringService.java` (verified via CONVENTIONS.md L57-L67) + `src/main/java/org/ctc/domain/model/Season.java` lines 13-19.

**Apply to:**
- `BackupSchema` → `@Component @RequiredArgsConstructor @Slf4j`
- `EntityTopoSorter` → `@Component` (package-private; no logging needed)
- `BackupObjectMapperConfig` → `@Configuration` (no `@RequiredArgsConstructor` — DI happens at `@Bean`-method param level)
- `DataImportAudit` → `@Entity @Table(name = "data_import_audit") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @ToString`

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class ScoringService {
    private final RaceLineupRepository raceLineupRepository;
```

---

### Constructor Injection via `final` Fields

**Source:** `src/main/java/org/ctc/domain/service/ScoringService.java` (CONVENTIONS.md L57-L67).

**Apply to:** All `@Component`/`@Service` classes in Phase 72.

```java
private final RaceLineupRepository raceLineupRepository;     // injected via Lombok @RequiredArgsConstructor
```

In Phase 72 → `BackupSchema` injects `EntityManagerFactory` + `EntityTopoSorter` via `final` fields; `EntityTopoSorter` has no dependencies.

---

### Logging with Parameterized `{}` Placeholders

**Source:** CONVENTIONS.md L250-L262 (verified against `ScoringService`).

**Apply to:** `BackupSchema.@PostConstruct` (one `log.info(...)` on initialization completion — RESEARCH §Pattern 1 line 351).

```java
log.info("Added team {} to season {}", team.getShortName(), season.getName());
```

NEVER use string concatenation in log calls.

---

### Spring Data `JpaRepository<Entity, UUID>` Pattern

**Source:** `src/main/java/org/ctc/domain/repository/SeasonRepository.java` (and 23 other CTC repositories).

**Apply to:** `DataImportAuditRepository`.

```java
public interface SeasonRepository extends JpaRepository<Season, UUID> {
```

**Phase 72 deviation:** package is `org.ctc.backup.audit` (NOT `org.ctc.domain.repository`) to enforce D-06.

---

### Flyway SQL Migration Header Comment

**Source:** `src/main/resources/db/migration/V1__initial_schema.sql` lines 1-2 + `V3__add_season_phase_tables.sql` lines 1-3.

**Apply to:** `V7__data_import_audit.sql`.

```sql
-- Add Season Phase tables (Season -> Phase -> Group hierarchy)
-- Compatible with H2 2.x and MariaDB 10.7+
```

The "Compatible with H2 2.x and MariaDB 10.7+" line is the project-wide convention — repeat it in V7.

---

### `@SpringBootTest @ActiveProfiles("dev")` IT Bootstrap

**Source:** `src/test/java/org/ctc/domain/repository/PhaseTeamRepositoryIT.java` lines 25-28.

**Apply to:** `BackupSchemaTopologyIT`, `BackupSchemaExclusionIT`, `BackupObjectMapperConfigIT`.

```java
@SpringBootTest
@ActiveProfiles("dev")
@Transactional         // ← optional; drop if test is read-only on the bean's state
class PhaseTeamRepositoryIT {
```

**Special variant for `db.migration` package** — `V4MigrationSmokeIT.java` line 38:

```java
@SpringBootTest(classes = CtcManagerApplication.class)    // ← MANDATORY when test lives outside org.ctc.* scan tree
```

Apply this variant to `V7DataImportAuditMigrationIT` (same package location).

---

### Given-When-Then Test Method Naming

**Source:** CLAUDE.md §"Test Naming" + `PhaseTeamRepositoryIT.java` line 54.

**Apply to:** All 5 new Phase 72 tests.

```java
@Test
void givenPhaseTeams_whenFindByPhaseId_thenReturnsAll() {
    // given
    ...
    // when
    ...
    // then
    assertThat(result).hasSize(2);
}
```

---

## No Analog Found

Files with no close codebase precedent. Planner must rely on RESEARCH.md patterns + JPA/Spring documentation:

| File | Role | Data Flow | Reason | RESEARCH reference |
|------|------|-----------|--------|--------------------|
| `EntityTopoSorter.java` | Component (algorithmic helper) | pure transform (Kahn's algorithm over JPA Metamodel) | No CTC code performs graph algorithms or JPA Metamodel introspection. | RESEARCH §Pattern 1 lines 363-432 (use verbatim) |
| `BackupObjectMapperConfig.java` (the dual-`@Bean` shape) | Configuration | DI graph (overriding auto-config) | `SecurityConfig.java` is the only `@Configuration` with `@Bean`, but it does NOT override an auto-config-default bean. The dual-bean `@Primary` + `@Qualifier` shape has no codebase precedent. | RESEARCH §Pattern 3 lines 511-567 + §Pitfall P-2 (auto-config back-off) |

---

## Critical Pattern Anomalies for Planner Attention

1. **PlayoffRound entity discrepancy (RESEARCH OQ-1):**
   CONTEXT D-03 lists 23 entities for `EXPORT_ORDER`; RESEARCH OQ-1 + the actual `ls org.ctc.domain.model` output show **24** classes that pass D-06's `org.ctc.domain.model.*` package filter (the extra is `PlayoffRound.java`). `BackupSchemaTopologyIT` cannot hard-code "23" — it must assert the actual runtime topo-sort size, and the planner must update PROJECT.md's "23-entity scope" wording to "24-entity scope (Car, Track, ..., PlayoffRound)" OR justify excluding PlayoffRound via a structural mechanism (which would break D-06's purity).

2. **D-08 record-entity rejected (RESEARCH Pitfall P-1):**
   CONTEXT D-08 says "record-style entity is preferred but Lombok class is acceptable". Hibernate 7 + Jakarta Persistence **cannot** use Java records as `@Entity` (records are final + immutable; JPA requires mutability + proxyable subclassing). Planner MUST use the Lombok class shape (RESEARCH §Pattern 5).

3. **D-11 isolation mechanism amended (RESEARCH Pitfall P-2):**
   CONTEXT D-11 says "default Spring `ObjectMapper` remains untouched" — this is technically wrong. `JacksonAutoConfiguration` uses `@ConditionalOnMissingBean(ObjectMapper.class)`, so defining the `backupObjectMapper` bean alone disables the auto-config default, breaking admin REST/AJAX paths. Planner MUST declare BOTH beans (`@Primary` default + `@Qualifier("backupObjectMapper")` strict) in `BackupObjectMapperConfig` (RESEARCH §Pattern 3).

4. **D-13 `BuildProperties` wiring unnecessary (RESEARCH Pitfall P-3):**
   CONTEXT D-13 requires `spring-boot-maven-plugin:build-info` goal addition to pom.xml. RESEARCH P-3 confirms CTC already exposes `app.version` via Maven resource-filtering (`@project.version@` → `application.yml` → `@Value("${app.version}")` in `GlobalModelAdvice`, line 10-11). `BackupManifest.appVersion` can be sourced the same way — **zero pom.xml changes needed**. Planner should use `@Value("${app.version}")` injected into a `BackupManifest` factory (in Phase 73's `BackupExportService`, not Phase 72) instead of `BuildProperties`.

---

## Metadata

**Analog search scope:** `src/main/java/org/ctc/{admin,domain}/`, `src/main/resources/db/migration/`, `src/test/java/org/ctc/`, `src/test/java/db/migration/`
**Files scanned:** ~50 (model, repository, config, migration, test analogs)
**Pattern extraction date:** 2026-05-11

---

*Pattern map for Phase 72 — Backup Wire Contract.*
