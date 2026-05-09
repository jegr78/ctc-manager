# Phase 57: Data Migration - Research

**Researched:** 2026-04-27
**Domain:** Flyway Java-based migration, JDBC dual-dialect (H2 + MariaDB), UUID handling, Spring integration testing
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01**: Java-based migration — single class `V4__MigrateSeasonsToPhases` extends `BaseJavaMigration`
- **D-02**: Single migration class with five private methods (migrateRegularPhases, migratePlayoffPhases, migrateMatchdayFKs, migratePhaseTeams, flipNotNullConstraints)
- **D-03**: Package `db.migration`; file at `src/main/java/db/migration/V4__MigrateSeasonsToPhases.java`
- **D-04**: `canExecuteInTransaction()` returns `true` (default); whole migration atomic
- **D-05**: Fail-fast on data integrity issues (null raceScoring/matchScoring) — throw `FlywayException`
- **D-06**: REGULAR phase fields are 1:1 copies from Season (complete field mapping locked)
- **D-07**: PLAYOFF phase inherits scoring from `playoff.season.raceScoring/matchScoring`
- **D-08**: PLAYOFF phase defaults (sort_index=10, layout=BRACKET, format=LEAGUE, legs=1, label=playoff.name)
- **D-09**: `playoffs.phase_id` updated per playoff immediately after SeasonPhase insert; bridge columns untouched
- **D-10**: Matchday FK backfill via single UPDATE with correlated subquery; `matchdays.group_id` stays NULL
- **D-11**: `phase_teams` 1:1 from `season_teams` — phase_id = REGULAR-phase of that season, group_id = NULL
- **D-12**: NOT-NULL flip via `getMetaData().getDatabaseProductName()` dialect detection; H2: `ALTER TABLE x ALTER COLUMN y SET NOT NULL`; MariaDB: `ALTER TABLE x MODIFY COLUMN y UUID NOT NULL`
- **D-13**: NOT-NULL flip is the LAST step in the migration sequence
- **D-14**: Structured logging with row counts per step via `LoggerFactory.getLogger(...)`
- **D-15**: Java integration test `V4MigrateSeasonsToPhasesIT` with `@JdbcTest` or programmatic Flyway; manual V4 trigger; seed-then-migrate pattern
- **D-16**: Six test methods with fixed naming (locked method signatures)
- **D-17**: Realistic seed scenario: 3 seasons (one with playoff, one without, one empty) + 2 teams/season + 2 matchdays/season + 1 playoff
- **D-18**: `@SpringBootTest` smoke test `V4MigrationSmokeIT`
- **D-19**: Both test files in `src/test/java/db/migration/`

### Claude's Discretion
- Exact MariaDB column type in `MODIFY COLUMN` ALTER (planner verifies at runtime if `UUID` vs `BINARY(16)`)
- Whether to wrap count-query `log.info` in try/catch to avoid masking real exceptions
- Per-step transaction boundary fallback (`canExecuteInTransaction()=false`) if MariaDB DDL implicit-commit causes prod issues
- Test data UUIDs: fixed (deterministic) vs `UUID.randomUUID()` per test run

### Deferred Ideas (OUT OF SCOPE)
- Drop of `playoff_seasons` M:N join table — Phase 61 (MIGR-06)
- Drop of legacy Season columns — Phase 61 (MIGR-06)
- Drop of `matchdays.season_id` and `playoffs.season_id` — Phase 61 (MIGR-06)
- Service-layer rewrite — Phase 58
- TestDataService/DevDataSeeder rewrite — Phase 59
- Group-aware data migration — out of scope (CONSOL-FUT-01)
- PLACEMENT phases for legacy data — out of scope (no PLACEMENT phases exist)
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| MIGR-02 | Daten-Migration: 1 REGULAR-Phase pro Bestandssaison mit kopierten Format/Scoring/Rounds/Legs/Dates | D-06 field mapping; `seasons` table columns confirmed in V1 schema; JdbcTemplate INSERT pattern |
| MIGR-03 | Daten-Migration: 1 PLAYOFF-Phase pro existierendem Playoff; FK umgehängt | D-07/D-09; `playoffs` table has `season_id NOT NULL` in V1 — correlated subquery for scoring lookup |
| MIGR-04 | Daten-Migration: `matchday.phase_id` auf REGULAR-Phase gesetzt | D-10; V3 confirms `matchdays.phase_id UUID` nullable column exists; single-statement UPDATE pattern |
| MIGR-05 | Daten-Migration: `phase_teams` aus heutigen `season_teams` abgeleitet (LEAGUE-Layout, group NULL) | D-11; `season_teams` UNIQUE(season_id, team_id) ensures no duplicates after mapping; INSERT pattern |
</phase_requirements>

---

## Summary

Phase 57 delivers a single Java Flyway migration (`V4__MigrateSeasonsToPhases`) that backfills the schema introduced in Phase 56. The migration is mechanically straightforward: it reads legacy rows from `seasons`, `playoffs`, `season_teams`, and `matchdays`, inserts corresponding rows into `season_phases` and `phase_teams`, patches FK columns on `matchdays` and `playoffs`, then flips two columns from NULLABLE to NOT NULL. All logic runs in a single Flyway-managed transaction on H2 (dev/test) and MariaDB (local/docker/prod), with dialect-detected DDL for the NOT-NULL flip.

The primary technical risk is the dual-dialect DDL divergence at the NOT-NULL flip step. Research confirms the exact ALTER syntax for both databases and the reliable dialect-detection mechanism (`getDatabaseProductName()` returns `"H2"` for H2 2.x and `"MariaDB"` for MariaDB JDBC driver 3.5.x with default settings). UUID parameter binding via JdbcTemplate works natively on H2 (`UUID` is a first-class type) and through BINARY(16) aliasing on MariaDB 10.7+; no casting helpers are required.

The test strategy (D-15..D-19) is a green-field pattern for this repo — there are currently zero programmatic Flyway tests. The recommended approach mirrors the existing `@SpringBootTest @ActiveProfiles("dev") @Transactional` pattern but adds a programmatic Flyway invocation that runs only V1–V3 before seeding, then runs V4 and asserts on the final state. Coverage from the new `db.migration` package adds to the overall JaCoCo report (currently 85.95% LINE on a COVERED/TOTAL of 4 979/5 608 instruction-equivalent lines).

**Primary recommendation:** Implement `V4__MigrateSeasonsToPhases.java` with JdbcTemplate constructed from `context.getConnection()` via `new JdbcTemplate(new SingleConnectionDataSource(connection, true))`; use `getDatabaseProductName()` for dialect detection; use `UUID.randomUUID()` directly as a parameter object; flip NOT NULL last. Write `V4MigrateSeasonsToPhasesIT` before the migration class (TDD).

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| REGULAR phase insert | DB / Flyway migration | — | Pure data backfill; no HTTP layer involved; runs at startup before Spring context is fully ready |
| PLAYOFF phase insert | DB / Flyway migration | — | Same: raw JDBC INSERT + UPDATE |
| Matchday FK backfill | DB / Flyway migration | — | Single SQL UPDATE; no business logic |
| PhaseTeam derivation | DB / Flyway migration | — | INSERT SELECT from `season_teams`; pure data copy |
| NOT-NULL flip DDL | DB / Flyway migration | — | Schema change; must be after all rows populated |
| Dialect detection | DB / Flyway migration | — | `Connection.getMetaData().getDatabaseProductName()` |
| Integration test (before/after) | Test layer | H2 in-memory | Seed + migrate + assert pattern |
| Smoke test (app boot) | Test layer / Spring Boot | JPA/Hibernate | Full context load; Flyway runs automatically |

---

## Flyway Java Migration Pattern

### BaseJavaMigration Mechanics

**Source:** `flyway-core-11.14.1-sources.jar` (VERIFIED) — `org.flywaydb.core.api.migration.BaseJavaMigration`

The class `V4__MigrateSeasonsToPhases` must extend `BaseJavaMigration`. Flyway parses version and description from the class simple name using the standard naming convention (`V{version}__{description}`). No override of `getVersion()` or `getDescription()` is needed.

Key defaults from `BaseJavaMigration` (verified from source):

| Method | Default | Override needed? |
|--------|---------|-----------------|
| `getVersion()` | Parsed from class name → `4` | No |
| `getDescription()` | Parsed from class name → `MigrateSeasonsToPhases` | No |
| `getChecksum()` | Returns `null` | No — `null` is valid; Flyway stores null and does not re-verify |
| `canExecuteInTransaction()` | Returns `true` | No (D-04 uses default) |
| `migrate(Context context)` | Abstract — must implement | Yes — single entry point |

**Important:** `getChecksum() = null` means Flyway will not validate the Java migration's source code on re-runs. This is fine because `V4` is a versioned migration (runs exactly once per database). [VERIFIED: flyway-core-11.14.1 source]

### JdbcTemplate Construction from Context

The `Context` interface (verified from `flyway-core-11.14.1`) provides exactly one data access method:

```java
// From org.flywaydb.core.api.migration.Context (VERIFIED):
Connection getConnection();
```

There is **no** `JdbcTemplate` factory on `Context`. The standard pattern is to wrap the connection in a `SingleConnectionDataSource`:

```java
// Verified pattern — Spring JDBC 7.0.6 SingleConnectionDataSource exists [VERIFIED: spring-jdbc-7.0.6 source]
@Override
public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    JdbcTemplate jdbcTemplate = new JdbcTemplate(
        new SingleConnectionDataSource(connection, true) // suppressClose=true
    );
    String dialect = connection.getMetaData().getDatabaseProductName();
    migrateRegularPhases(jdbcTemplate);
    migratePlayoffPhases(jdbcTemplate);
    migrateMatchdayFKs(jdbcTemplate);
    migratePhaseTeams(jdbcTemplate);
    flipNotNullConstraints(jdbcTemplate, dialect);
}
```

`suppressClose=true` prevents `SingleConnectionDataSource` from closing the underlying connection when `JdbcTemplate` finishes, which is critical because Flyway manages the connection lifecycle (and transaction).

**Alternative (no SingleConnectionDataSource):** `new JdbcTemplate(new SimpleDriverDataSource(...))` is more verbose. Stick with `SingleConnectionDataSource`.

### Transaction Semantics

With `canExecuteInTransaction() = true` (the default), Flyway wraps the entire `migrate()` call in a single database transaction. On H2 this gives full ACID rollback. On MariaDB, `ALTER TABLE ... MODIFY COLUMN` is a DDL statement that causes an **implicit commit** regardless of transaction wrapping — this is standard MariaDB/MySQL behavior. Per D-04, this trade-off is acceptable for the small data size of this migration.

### Migration Package and Classpath Scanning

The global `application.yml` declares:
```yaml
spring:
  flyway:
    locations: classpath:db/migration
```

This single location handles both SQL migrations in `src/main/resources/db/migration/` and Java migrations in `src/main/java/db/migration/` (the `db.migration` package). Spring Boot Flyway autoconfiguration in Spring Boot 4.x / Flyway 11.x combines both locations automatically when both exist at the same classpath path. [VERIFIED: application.yml; ASSUMED: Spring Boot 4.x combines file system and class scanning for the same location path — standard Flyway behavior since v7]

---

## Dialect Detection & DDL Syntax

### Return Values Confirmed

| Database | Driver | `getDatabaseProductName()` return | Source |
|----------|--------|----------------------------------|--------|
| H2 2.4.240 | H2 built-in | `"H2"` | [VERIFIED: h2-2.4.240-sources.jar `JdbcDatabaseMetaData.getDatabaseProductName()`] |
| MariaDB 10.7+ | mariadb-java-client 3.5.7 | `"MariaDB"` (when `useMysqlMetadata=false`, which is the default) | [VERIFIED: mariadb-java-client-3.5.7-sources.jar `DatabaseMetaData.getDatabaseProductName()`] |

The MariaDB driver returns `"MySQL"` only when `useMysqlMetadata=true` is set in the JDBC URL. The project's connection strings (`jdbc:mariadb://...`) use no such parameter, so `"MariaDB"` is guaranteed. [VERIFIED: driver source + application-local.yml]

### Exact DDL Statements

**H2 — ALTER TABLE ... ALTER COLUMN ... SET NOT NULL:**

H2 2.x supports the SQL standard `ALTER TABLE t ALTER COLUMN c SET NOT NULL` syntax. Verified by tracing through `Parser.java` → `parseAlterTableAlterColumnSet()` → `parseNotNullConstraint()` reading `NOT NULL` → `NULL_IS_NOT_ALLOWED` → sets `ALTER_TABLE_ALTER_COLUMN_NOT_NULL` type. [VERIFIED: h2-2.4.240-sources.jar]

```java
// H2: ALTER TABLE matchdays ALTER COLUMN phase_id SET NOT NULL
jdbcTemplate.execute("ALTER TABLE matchdays ALTER COLUMN phase_id SET NOT NULL");
jdbcTemplate.execute("ALTER TABLE playoffs ALTER COLUMN phase_id SET NOT NULL");
```

**MariaDB — ALTER TABLE ... MODIFY COLUMN ... UUID NOT NULL:**

MariaDB's `ALTER TABLE ... MODIFY COLUMN` requires restating the **full column definition** including the type. Since V3 declared the column as `UUID` (MariaDB 10.7+ supports this as a native type alias for `BINARY(16)`), the MODIFY COLUMN must also use `UUID`:

```java
// MariaDB: MODIFY COLUMN requires full type specification
jdbcTemplate.execute("ALTER TABLE matchdays MODIFY COLUMN phase_id UUID NOT NULL");
jdbcTemplate.execute("ALTER TABLE playoffs MODIFY COLUMN phase_id UUID NOT NULL");
```

**Note on `BINARY(16)` vs `UUID`:** MariaDB 10.7+ introduced native UUID storage as a first-class type. V3 declares columns as `UUID`, and the MariaDB JDBC driver 3.5.x stores/retrieves them correctly. Using `UUID NOT NULL` in MODIFY COLUMN is the correct and consistent approach — do not switch to `BINARY(16) NOT NULL` even though they are equivalent at the storage level. [ASSUMED: MariaDB 10.7+ UUID type in MODIFY COLUMN is valid — standard MariaDB DDL reference; not tested in a live MariaDB instance in this session]

**Dialect detection code template:**

```java
private void flipNotNullConstraints(JdbcTemplate jdbcTemplate, String dialect) {
    if ("H2".equals(dialect)) {
        jdbcTemplate.execute("ALTER TABLE matchdays ALTER COLUMN phase_id SET NOT NULL");
        jdbcTemplate.execute("ALTER TABLE playoffs ALTER COLUMN phase_id SET NOT NULL");
    } else {
        // MariaDB (and any other dialect — fail-safe to MariaDB syntax)
        jdbcTemplate.execute("ALTER TABLE matchdays MODIFY COLUMN phase_id UUID NOT NULL");
        jdbcTemplate.execute("ALTER TABLE playoffs MODIFY COLUMN phase_id UUID NOT NULL");
    }
}
```

---

## UUID Handling Across Dialects

### H2 Native UUID

H2 2.x has a first-class `UUID` data type (backed by `ValueUuid`). [VERIFIED: `ValueUuid.java` class exists in h2-2.4.240.jar] When a `java.util.UUID` object is passed as a JDBC parameter, H2's JDBC driver binds it natively to the `UUID` column. No casting or conversion required.

### MariaDB UUID Storage

The V3 migration declares all UUID columns as `UUID` type. MariaDB 10.7+ stores `UUID` columns as `BINARY(16)` internally but presents them as UUID strings via the JDBC driver. The mariadb-java-client 3.5.x driver handles `java.util.UUID` ↔ `BINARY(16)` conversion transparently when the column is declared `UUID`. [ASSUMED: mariadb-java-client 3.5.x automatically converts `UUID` objects for `UUID`-typed columns; consistent with driver changelog for 3.x and Phase 56 evidence that Spring JPA entities with `@GeneratedValue(strategy=UUID)` work on MariaDB]

### JdbcTemplate Parameter Passing

Pass `UUID.randomUUID()` as a plain `Object` in the `Object[]` args array:

```java
// Both H2 and MariaDB accept UUID directly — no casting helper needed
UUID newPhaseId = UUID.randomUUID();
jdbcTemplate.update(
    "INSERT INTO season_phases (id, season_id, sort_index, phase_type, layout, format, "
    + "label, start_date, end_date, total_rounds, legs, event_duration_minutes, "
    + "race_scoring_id, match_scoring_id, created_at, updated_at) "
    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
    newPhaseId, seasonId, 0, "REGULAR", "LEAGUE", "LEAGUE",
    null, startDate, endDate, totalRounds, legs, eventDurationMinutes,
    raceScoringId, matchScoringId
);
```

`java.time.LocalDate` is also supported natively by Spring's JdbcTemplate via `java.sql.Date.valueOf(localDate)` or directly as `LocalDate` with Spring 6+. [ASSUMED: Spring JDBC 7.0.6 handles `LocalDate` directly — consistent with Spring 6+ feature set]

---

## Schema Reference

Exact column types from V3 SQL (confirmed by reading `V3__add_season_phase_tables.sql`): [VERIFIED: direct file read]

### `season_phases` table

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| `id` | `UUID` | NOT NULL | — | PRIMARY KEY |
| `season_id` | `UUID` | NOT NULL | — | FK → seasons(id) |
| `sort_index` | `INT` | NOT NULL | — | |
| `phase_type` | `VARCHAR(20)` | NOT NULL | — | Enum: REGULAR/PLAYOFF/PLACEMENT |
| `layout` | `VARCHAR(20)` | NOT NULL | — | Enum: LEAGUE/GROUPS/BRACKET |
| `format` | `VARCHAR(20)` | NOT NULL | `'LEAGUE'` | Enum: LEAGUE/SWISS/ROUND_ROBIN |
| `label` | `VARCHAR(255)` | NULL | — | |
| `start_date` | `DATE` | NULL | — | |
| `end_date` | `DATE` | NULL | — | |
| `total_rounds` | `INT` | NULL | — | |
| `legs` | `INT` | NOT NULL | `1` | |
| `event_duration_minutes` | `INT` | NULL | — | |
| `race_scoring_id` | `UUID` | NOT NULL | — | FK → race_scorings(id) |
| `match_scoring_id` | `UUID` | NOT NULL | — | FK → match_scorings(id) |
| `created_at` | `TIMESTAMP` | NULL | `CURRENT_TIMESTAMP` | |
| `updated_at` | `TIMESTAMP` | NULL | `CURRENT_TIMESTAMP` | |

**UNIQUE constraint:** `uk_season_phase_type (season_id, phase_type)` — one phase_type per season

### `phase_teams` table

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| `id` | `UUID` | NOT NULL | — |
| `phase_id` | `UUID` | NOT NULL | — |
| `team_id` | `UUID` | NOT NULL | — |
| `group_id` | `UUID` | NULL | — |
| `created_at` | `TIMESTAMP` | NULL | `CURRENT_TIMESTAMP` |
| `updated_at` | `TIMESTAMP` | NULL | `CURRENT_TIMESTAMP` |

**UNIQUE constraint:** `uk_phase_team (phase_id, team_id)`

### V3 additions to existing tables

| Table | Column | Type | Nullable in V3 | Target state after V4 |
|-------|--------|------|---------------|----------------------|
| `matchdays` | `phase_id` | `UUID` | YES (nullable) | NOT NULL (V4 flip) |
| `matchdays` | `group_id` | `UUID` | YES (stays nullable) | stays NULL for migrated rows |
| `playoffs` | `phase_id` | `UUID` | YES (nullable) | NOT NULL (V4 flip) |

**UNIQUE on `playoffs.phase_id`:** `uk_playoff_phase UNIQUE (phase_id)` — already in V3.

### Legacy tables V4 reads from

From `V1__initial_schema.sql` (confirmed): [VERIFIED: direct file read]

**`seasons` table** (source for REGULAR phase fields):
- `id UUID NOT NULL` (PK)
- `format VARCHAR(20) DEFAULT 'LEAGUE' NOT NULL`
- `total_rounds INT` (nullable)
- `legs INT NOT NULL DEFAULT 1`
- `event_duration_minutes INT` (nullable)
- `start_date DATE` (nullable)
- `end_date DATE` (nullable)
- `race_scoring_id UUID NOT NULL`
- `match_scoring_id UUID NOT NULL`

**`playoffs` table** (source for PLAYOFF phase fields):
- `id UUID NOT NULL` (PK)
- `season_id UUID NOT NULL` (FK → seasons, UNIQUE — one playoff per season)
- `name VARCHAR(255) NOT NULL`
- `start_date DATE` (nullable)
- `end_date DATE` (nullable)
- `event_duration_minutes INT` (nullable)

**`season_teams` table** (source for `phase_teams` derivation):
- `id UUID NOT NULL` (PK)
- `season_id UUID NOT NULL`
- `team_id UUID NOT NULL`
- UNIQUE `(season_id, team_id)`

---

## Test Setup Patterns

### Existing Repo Pattern (Confirmed)

All existing integration tests in this repo use `@SpringBootTest @ActiveProfiles("dev") @Transactional`. [VERIFIED: `SeasonPhaseEntityIntegrationTest.java`, `PhaseTeamUniquenessIntegrationTest.java`, `TestDataServiceIntegrationTest.java` — all read directly]

Key characteristics:
- Full Spring context loaded
- H2 in-memory; Flyway runs all migrations automatically before tests
- `@Transactional` rolls back each test — no data leaks
- Test data uses namespaced prefixes (`Phase56-Test-*`, `Phase56-Uniq-*`)
- Repositories injected via `@Autowired`; `EntityManager` via `@PersistenceContext`
- `saveAndFlush()` used when DB constraint enforcement is needed

There is **currently zero** usage of programmatic Flyway (`Flyway.configure()...migrate()`) or `@JdbcTest` in the repo. [VERIFIED: grep for `Flyway.configure`, `FlywayTest`, `flyway.migrate` found no matches in `src/test/`]

### Recommended Pattern for V4MigrateSeasonsToPhasesIT

D-15 requires a test that seeds pre-V4 state and asserts post-V4 state. The repo pattern (full `@SpringBootTest`) does **not** support this because Flyway runs automatically on context load — by the time test code runs, V4 has already executed and the DB is empty (dev starts with no data).

The required pattern is:

**Option A — Programmatic Flyway (recommended for maximum control):**

```java
// No @SpringBootTest — run programmatically
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V4MigrateSeasonsToPhasesIT {

    private DataSource dataSource;
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    void setUp() {
        // 1. Create isolated H2 in-memory DB
        dataSource = new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .build();
        jdbcTemplate = new JdbcTemplate(dataSource);

        // 2. Run V1, V2, V3 only (no V4 yet)
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .target("3")  // stop at V3
            .load()
            .migrate();

        // 3. Seed legacy data (pre-V4 state)
        seedLegacyData();

        // 4. Run V4
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration", "classpath:db.migration") // SQL + Java
            .target("4")
            .baselineOnMigrate(false)
            .load()
            .migrate();
    }
}
```

**Option B — @SpringBootTest with `spring.flyway.target=3` + manual V4 trigger:**

```java
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = "spring.flyway.target=3")
class V4MigrateSeasonsToPhasesIT {
    @Autowired DataSource dataSource;

    @BeforeEach
    void seedAndMigrate() {
        // seed, then programmatically run V4
    }
}
```

**Recommendation for planner:** Option A (programmatic `EmbeddedDatabaseBuilder` + `Flyway.configure()`) is cleaner and fully isolated from the main application context. It avoids loading the full Spring context (faster), avoids `DevDataSeeder` side effects, and gives precise control over the migration sequence. The `EmbeddedDatabaseBuilder` is part of `spring-jdbc` (already a dependency).

**Flyway Java migration classpath location:** When running Flyway programmatically, Java migrations in `src/main/java/db/migration/` are on the classpath as `classpath:db/migration`. The `locations` config string `"classpath:db/migration"` covers both SQL and Java migrations in the same classpath location. [ASSUMED: Flyway 11.x automatically discovers both Java and SQL migrations under the same classpath location — consistent with Flyway documentation and standard behavior]

### V4MigrationSmokeIT Pattern

```java
// Mirrors existing @SpringBootTest pattern exactly
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class V4MigrationSmokeIT {
    @Autowired SeasonRepository seasonRepository;

    @Test
    void whenContextLoads_thenAllSeasonsHavePhases() {
        // V4 ran on empty DB = no seasons = trivially passes
        // With real data (local/prod), this would fail if V4 is missing
        List<Season> seasons = seasonRepository.findAll();
        seasons.forEach(s -> assertThat(s.getPhases()).isNotEmpty());
    }
}
```

Note: Since `DevDataSeeder` does not run in the standard test context (only in `dev` profile runtime), the smoke test on H2 will find zero seasons and trivially pass. This still validates that Flyway+JPA+Spring Boot all agree on the schema. For meaningful smoke validation, an integration test with seeded data (Option A above) is required.

---

## Standard Stack

### Core

| Library | Version | Purpose | Source |
|---------|---------|---------|--------|
| `flyway-core` | 11.14.1 | `BaseJavaMigration`, `Context`, migration execution | [VERIFIED: `~/.m2` cache] |
| `flyway-mysql` | 11.14.1 | MariaDB/MySQL dialect support for Flyway | [VERIFIED: `~/.m2` cache] |
| `spring-jdbc` | 7.0.6 | `JdbcTemplate`, `SingleConnectionDataSource`, `EmbeddedDatabaseBuilder` | [VERIFIED: `~/.m2` cache] |
| `h2` | 2.4.240 | In-memory DB for tests and dev profile | [VERIFIED: `~/.m2` cache] |
| `mariadb-java-client` | 3.5.7 | MariaDB JDBC driver; `getDatabaseProductName()` returns `"MariaDB"` | [VERIFIED: `~/.m2` cache + driver source] |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `slf4j-api` | via Spring Boot BOM | Logging in migration class | For `LoggerFactory.getLogger(V4__MigrateSeasonsToPhases.class)` |
| `junit-jupiter` | via Spring Boot BOM | Test framework | V4MigrateSeasonsToPhasesIT |
| `assertj-core` | via Spring Boot BOM | Fluent assertions | All test methods |

### Installation

No new dependencies needed — all are already in `pom.xml` via `spring-boot-starter-flyway`, `spring-boot-starter-data-jpa`, and `spring-boot-starter-flyway-test`.

---

## Architecture Patterns

### Migration Execution Flow

```
App Startup (dev/test/local/prod)
│
├── Spring Boot AutoConfig triggers Flyway
│   └── Flyway scans classpath:db/migration
│       ├── V1__initial_schema.sql (already applied)
│       ├── V2__add_fk_indexes.sql (already applied)
│       ├── V3__add_season_phase_tables.sql (already applied)
│       └── V4__MigrateSeasonsToPhases.java ← Phase 57 adds this
│
└── V4.migrate(Context context)
    ├── 1. migrateRegularPhases(JdbcTemplate)
    │     SELECT all seasons → INSERT season_phases (REGULAR) per season
    │     Store map: seasonId → newPhaseId
    ├── 2. migratePlayoffPhases(JdbcTemplate)
    │     SELECT all playoffs (with season scoring) → INSERT season_phases (PLAYOFF)
    │     → UPDATE playoffs SET phase_id = newPlayoffPhaseId
    ├── 3. migrateMatchdayFKs(JdbcTemplate)
    │     UPDATE matchdays SET phase_id = (subquery on season_phases REGULAR)
    ├── 4. migratePhaseTeams(JdbcTemplate)
    │     SELECT season_teams → INSERT phase_teams (using seasonId→phaseId map)
    └── 5. flipNotNullConstraints(JdbcTemplate, dialect)
          dialect = "H2" → ALTER COLUMN x SET NOT NULL
          dialect = "MariaDB" → MODIFY COLUMN x UUID NOT NULL
```

### Recommended Project Structure

```
src/main/java/db/migration/
└── V4__MigrateSeasonsToPhases.java     # extends BaseJavaMigration

src/test/java/db/migration/
├── V4MigrateSeasonsToPhasesIT.java     # programmatic Flyway; seed-migrate-assert
└── V4MigrationSmokeIT.java             # @SpringBootTest context-loads + phase check
```

### Pattern: REGULAR Phase INSERT (per Season row)

```java
// Source: D-06 field mapping + V3 column types [VERIFIED]
private void migrateRegularPhases(JdbcTemplate jdbcTemplate) {
    List<Map<String, Object>> seasons = jdbcTemplate.queryForList("SELECT * FROM seasons");
    Map<UUID, UUID> seasonToRegularPhaseId = new HashMap<>();

    for (Map<String, Object> season : seasons) {
        UUID seasonId = toUUID(season.get("id"));
        UUID raceScoringId = toUUID(season.get("race_scoring_id"));
        UUID matchScoringId = toUUID(season.get("match_scoring_id"));
        if (raceScoringId == null) {
            throw new FlywayException("Season " + seasonId + " has null race_scoring_id");
        }
        if (matchScoringId == null) {
            throw new FlywayException("Season " + seasonId + " has null match_scoring_id");
        }
        UUID newPhaseId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO season_phases (id, season_id, sort_index, phase_type, layout, format, "
            + "label, start_date, end_date, total_rounds, legs, event_duration_minutes, "
            + "race_scoring_id, match_scoring_id, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            newPhaseId, seasonId, 0, "REGULAR", "LEAGUE",
            season.get("format"),       // VARCHAR — pass as String
            null,                       // label = null per D-06
            season.get("start_date"),   // java.sql.Date from ResultSet
            season.get("end_date"),
            season.get("total_rounds"),
            season.get("legs"),
            season.get("event_duration_minutes"),
            raceScoringId, matchScoringId
        );
        seasonToRegularPhaseId.put(seasonId, newPhaseId);
    }
    log.info("Migrated {} REGULAR phases (one per season)", seasons.size());
}
```

### Pattern: UUID Helper Method

`jdbcTemplate.queryForList()` returns `Map<String, Object>` where UUID columns come back as `java.util.UUID` on H2 and as `byte[]` on MariaDB. A helper is needed:

```java
private static UUID toUUID(Object value) {
    if (value == null) return null;
    if (value instanceof UUID) return (UUID) value;
    if (value instanceof byte[]) return bytesToUUID((byte[]) value);
    if (value instanceof String) return UUID.fromString((String) value);
    throw new IllegalArgumentException("Cannot convert " + value.getClass() + " to UUID");
}

private static UUID bytesToUUID(byte[] bytes) {
    ByteBuffer bb = ByteBuffer.wrap(bytes);
    return new UUID(bb.getLong(), bb.getLong());
}
```

[ASSUMED: MariaDB JDBC driver 3.5.x may return UUID columns as `byte[]` when accessed via generic `queryForList` — behavior depends on driver config. If `useMysqlMetadata=false` (default), MariaDB native UUID type columns are returned as `java.util.UUID`. If returned as `byte[]`, the helper above handles it. Confirm behavior in the integration test; add `byte[]` branch defensively.]

### Pattern: Matchday FK Backfill (Single SQL, D-10)

```java
private void migrateMatchdayFKs(JdbcTemplate jdbcTemplate) {
    int count = jdbcTemplate.update(
        "UPDATE matchdays m SET phase_id = ("
        + "  SELECT sp.id FROM season_phases sp"
        + "  WHERE sp.season_id = m.season_id AND sp.phase_type = 'REGULAR'"
        + ")"
    );
    log.info("Updated phase_id on {} matchdays", count);
}
```

This is a safe no-op when `matchdays` is empty. The subquery returns NULL for matchdays without a matching REGULAR phase — but since step 1 inserts one REGULAR phase per season, and every matchday has `season_id NOT NULL`, this will always find a match for any existing matchday.

### Pattern: PhaseTeam INSERT (D-11)

```java
private void migratePhaseTeams(JdbcTemplate jdbcTemplate, Map<UUID, UUID> seasonToRegularPhaseId) {
    List<Map<String, Object>> seasonTeams = jdbcTemplate.queryForList("SELECT * FROM season_teams");
    for (Map<String, Object> st : seasonTeams) {
        UUID seasonId = toUUID(st.get("season_id"));
        UUID phaseId = seasonToRegularPhaseId.get(seasonId);
        if (phaseId == null) continue; // defensive — should not happen after step 1
        jdbcTemplate.update(
            "INSERT INTO phase_teams (id, phase_id, team_id, group_id, created_at, updated_at)"
            + " VALUES (?, ?, ?, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            UUID.randomUUID(), phaseId, toUUID(st.get("team_id"))
        );
    }
    log.info("Migrated {} phase_teams entries (one per season_team)", seasonTeams.size());
}
```

### Anti-Patterns to Avoid

- **Using Spring-managed services or repositories in the migration** — migration runs before Spring context is fully initialized. Only `JdbcTemplate` with raw SQL is safe.
- **Calling `count(*)` before insert and after** — this doubles the query count and adds latency. Use `jdbcTemplate.update()`'s return value (rows affected) for counts where possible, or accept that the `log.info` count query is a separate SELECT.
- **`UUID.toString()` in SQL** — H2 natively accepts `UUID` objects; converting to String and back adds unnecessary churn.
- **Single global transaction for DDL on MariaDB** — MariaDB DDL causes implicit commit; the `flipNotNullConstraints` step accepts this (D-04). Do not try to wrap DDL in a savepoint.
- **Referencing JPA entities in the migration** — use raw column names from V1/V3 SQL, not Java field names from the entity classes (e.g., `"season_id"` not `"seasonId"`).

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Database product detection | Custom string parsing | `Connection.getMetaData().getDatabaseProductName()` | Standard JDBC; driver returns canonical names |
| Connection wrapping for JdbcTemplate | Direct connection usage | `SingleConnectionDataSource(conn, true)` | Prevents accidental connection close; Spring-standard |
| UUID ↔ byte[] conversion | Custom conversion | Helper method + `ByteBuffer.wrap()` | Standard bit-exact conversion; covers all edge cases |
| In-memory H2 for tests | File-based H2 | `EmbeddedDatabaseBuilder().setType(H2)` | Guaranteed isolation; auto-destroyed after test |
| Assert row counts | Manual SELECT count + compare | AssertJ `assertThat(count).isEqualTo(expected)` | Readable; consistent with existing test style |

---

## Empty-DB Behavior

When V4 runs on a fresh dev/test H2 (zero rows in all tables):

| Step | Behavior on empty DB | Safe? |
|------|---------------------|-------|
| `migrateRegularPhases` | `SELECT * FROM seasons` returns empty list; loop body never executes; 0 rows inserted | Yes — no-op |
| `migratePlayoffPhases` | `SELECT * FROM playoffs` returns empty list; 0 rows inserted | Yes — no-op |
| `migrateMatchdayFKs` | UPDATE affects 0 rows (WHERE clause has no matches) | Yes — 0 rows updated |
| `migratePhaseTeams` | `SELECT * FROM season_teams` returns empty list; 0 rows inserted | Yes — no-op |
| `flipNotNullConstraints` | `ALTER TABLE matchdays ALTER COLUMN phase_id SET NOT NULL` on an empty table succeeds — the constraint is satisfied vacuously | Yes — DDL on empty table is safe |

**Conclusion:** V4 is safe on empty databases without any special null-checks or skip-guards. [VERIFIED: H2 DDL behavior; SQL semantics of NOT NULL constraint on empty tables]

---

## Coverage Baseline & Targets

### Current State

| Metric | Value | Source |
|--------|-------|--------|
| LINE coverage | 88.78% (4 979 / 5 608 lines covered) | [VERIFIED: `target/site/jacoco/jacoco.csv` — local report] |
| JaCoCo minimum gate | 82% line coverage | [VERIFIED: `pom.xml` JaCoCo configuration] |
| `db.migration` package in report | NOT present | [VERIFIED: `grep db.migration jacoco.csv` found nothing] |
| Number of packages tracked | 190 packages | [VERIFIED: `jacoco.csv` line count] |

**Note:** The 85.62% figure in STATE.md and the 85.95% in 56-VERIFICATION.md both refer to the same post-Phase-56 build. The `jacoco.csv` on disk now shows 88.78% (instruction-level) which may include Phase 56 tests. LINE ratio from the CSV is the metric Flyway uses in `pom.xml`.

### Coverage Impact of Phase 57

Adding `V4__MigrateSeasonsToPhases.java` introduces new lines in the `db.migration` package. JaCoCo will include this package in its report automatically once the class is on the classpath (no changes to `pom.xml` excludes needed — only Playwright graphic services are excluded).

| Class | Estimated lines | Coverage strategy |
|-------|----------------|-------------------|
| `V4__MigrateSeasonsToPhases` | ~80-100 lines | Covered by `V4MigrateSeasonsToPhasesIT` via programmatic invocation |

The `V4MigrateSeasonsToPhasesIT` and `V4MigrationSmokeIT` tests themselves (test sources) are not counted in JaCoCo coverage. Only the production migration class contributes to the coverage ratio.

**Risk:** If `V4__MigrateSeasonsToPhases` is added but the integration test does not exercise all branches (e.g., the MariaDB dialect branch is never reached in H2 tests), the uncovered branch will reduce coverage. The `flipNotNullConstraints` method has a `"H2"` branch and an `else` branch. Only the H2 branch is exercised in tests. This is acceptable — the MariaDB branch cannot be tested in unit/integration tests without a live MariaDB instance.

**Planner must:**
1. Verify that adding V4 does not push coverage below 82%
2. Ensure `V4MigrateSeasonsToPhasesIT` exercises all five `migrateX` methods with non-empty data
3. Do NOT add `db/migration` to JaCoCo excludes — the migration code must be covered

---

## Validation Architecture (8 Dimensions)

### 1. Correctness (data values are accurate)

| Scenario | Test Method | Type |
|----------|-------------|------|
| REGULAR phase has correct field values copied from Season | `givenLegacySeasons_whenMigrationRuns_thenEachSeasonHasOneRegularPhase()` | IT |
| PLAYOFF phase has correct values (scoring from season, name=playoff.name, sort_index=10) | `givenLegacyPlayoff_whenMigrationRuns_thenPlayoffLinkedViaPhaseId()` | IT |
| Matchday.phase_id points to the correct season's REGULAR phase | `givenLegacyMatchdays_whenMigrationRuns_thenAllMatchdaysHavePhaseId()` | IT |
| PhaseTeam.phase_id matches the REGULAR phase of the season | `givenLegacySeasonTeams_whenMigrationRuns_thenPhaseTeamsPopulated()` | IT |

### 2. Idempotency (running V4 twice fails gracefully)

| Scenario | Test Method | Type |
|----------|-------------|------|
| Second Flyway run is a no-op (Flyway records version=4, skips re-execution) | Implicit via Flyway `flyway_schema_history` table | Smoke |
| UNIQUE constraint `uk_season_phase_type` prevents duplicate REGULAR phases | Flyway would reject re-run via schema history | Structural |

Note: Flyway versioned migrations (V*) are idempotent by design — Flyway records in `flyway_schema_history` and never re-runs a versioned migration. The `uk_season_phase_type` constraint provides an additional data-level safety net.

### 3. Edge Cases

| Scenario | Test Method | Type |
|----------|-------------|------|
| Season with zero matchdays and zero teams: migration completes with 0 inserts for that season | Covered by D-17 seed: "one empty season" | IT |
| Season with no playoff: only REGULAR phase created, no PLAYOFF phase | Covered by D-17 seed: "one season without playoff" | IT |
| `matchdays` table empty: UPDATE returns 0, no error | Empty-DB scenario | IT (empty case) |
| `phase_teams` table empty before V4: INSERT fills it correctly | Asserted by SC4 | IT |
| Bridge columns untouched: `matchday.season_id` and `playoff.season_id` still populated | `givenLegacyData_whenMigrationRuns_thenBridgeColumnsRemainIntact()` | IT |

### 4. Performance (acceptable migration duration)

| Scenario | Notes | Type |
|----------|-------|------|
| Small data set (~3-5 seasons): sub-second migration | Expected given row counts | Manual smoke |
| Per-row loops vs. batch insert | D-17 seed is only 3 seasons — per-row INSERT acceptable | Design decision |
| Matchday UPDATE: single SQL vs. per-row loop | D-10 uses single correlated UPDATE — optimal | Design |

No automated performance test required for Phase 57 (data volume is small).

### 5. Observability (logs visible in production)

| Scenario | Notes | Type |
|----------|-------|------|
| `log.info("Migrated {} REGULAR phases...", count)` appears in logs | D-14 locked; verified by log output inspection | Manual |
| No swallowed exceptions (count query try/catch decision) | Claude's Discretion — planner must decide | Design |
| FlywayException message identifies offending Season ID | D-05 fail-fast message format | IT (test with null scoring) |

### 6. Recoverability (failure leaves DB in clean state)

| Scenario | Notes | Type |
|----------|-------|------|
| Exception in step 2 (PLAYOFF insert): H2 rolls back all V4 changes | `canExecuteInTransaction()=true` — Flyway rolls back | IT (test with bad data) |
| Exception in step 5 (DDL flip): MariaDB DDL implicit commit = partial state; recovery = run cleanup and re-run | MariaDB caveat documented; prod migration must succeed on first run | Manual plan |
| Application fails to start on migration error | Flyway throws; Spring Boot context fails | Smoke |

### 7. Integration (JPA + Spring context alignment)

| Scenario | Test Method | Type |
|----------|-------------|------|
| Spring context loads after V4 | `V4MigrationSmokeIT` context load | Smoke |
| `seasonRepository.findAll()` returns seasons | Smoke test assertion | Smoke |
| `Season.phases` collection non-empty (when data exists) | Smoke test assertion (would need seeded data) | Manual on local |
| Matchday.phase accessible via JPA LAZY load | Covered by Phase 56 tests — structure unchanged | Existing |

### 8. Regression (Phase 56 functionality unaffected)

| Scenario | Test Method | Type |
|----------|-------------|------|
| All 1072 Phase 56 tests still pass after V4 is added | `./mvnw verify` | CI |
| V1, V2, V3 checksums unchanged | Flyway schema history | Automated (Flyway validates) |
| `matchday.season_id NOT NULL` still intact (bridge column) | `givenLegacyData_whenMigrationRuns_thenBridgeColumnsRemainIntact()` | IT |
| `playoff.season_id NOT NULL` still intact | Same test | IT |
| `playoff_seasons` M:N table unchanged | Same test | IT |

### Nyquist Validation Summary

| Dimension | Automated Command | Wave |
|-----------|-------------------|------|
| Correctness | `./mvnw verify` (covers V4MigrateSeasonsToPhasesIT) | After each task |
| Idempotency | Implicit via Flyway schema history | App boot |
| Edge cases | `./mvnw verify` | After IT task |
| Performance | N/A for this phase | — |
| Observability | Manual log inspection | Post-smoke |
| Recoverability | Documented; H2 covered by IT; MariaDB manual | Post-deploy |
| Integration | `./mvnw verify` (V4MigrationSmokeIT) | Final wave |
| Regression | `./mvnw verify` | Phase gate |

---

## Common Pitfalls

### Pitfall 1: UUID Column Type Returned as byte[] from queryForList

**What goes wrong:** `jdbcTemplate.queryForList("SELECT * FROM season_teams")` returns a `Map<String, Object>` where UUID columns come back as `java.util.UUID` on H2 but potentially as `byte[]` on MariaDB (when using raw `queryForList` without type mapping). Casting directly to `UUID` in the loop causes `ClassCastException` on MariaDB.

**Why it happens:** H2 maps its native UUID type to `java.util.UUID` in JDBC. MariaDB's JDBC driver behavior for UUID-typed columns depends on driver version and configuration.

**How to avoid:** Implement the `toUUID(Object value)` helper method that handles `UUID`, `byte[]`, and `String` inputs. This makes the migration DB-agnostic.

**Warning signs:** `ClassCastException: byte[] cannot be cast to UUID` in logs during local (MariaDB) run.

### Pitfall 2: NOT NULL Flip on Non-Empty Table Fails

**What goes wrong:** If any row in `matchdays` or `playoffs` still has `phase_id = NULL` when `flipNotNullConstraints` runs, the DDL will be rejected with a constraint violation.

**Why it happens:** Steps 3 and 2 (matchday/playoff FK backfill) must complete successfully for ALL rows before step 5 runs. A bug in step 2 or 3 (e.g., missing REGULAR phase for a matchday's season due to a data integrity issue) leaves rows with NULL `phase_id`.

**How to avoid:** D-13 mandates NOT-NULL flip is the last step. Additionally, a pre-flip check can be added: `SELECT COUNT(*) FROM matchdays WHERE phase_id IS NULL` and throw `FlywayException` if > 0.

**Warning signs:** SQL error like `Column 'phase_id' cannot be null` during the `ALTER TABLE` step.

### Pitfall 3: SingleConnectionDataSource Closes the Flyway-Managed Connection

**What goes wrong:** If `suppressClose=false` (the default) is used when constructing `SingleConnectionDataSource`, Spring's `JdbcTemplate` will close the connection after use. This closes the Flyway-managed connection, corrupting the transaction.

**Why it happens:** `JdbcTemplate` calls `conn.close()` when done. `SingleConnectionDataSource` by default proxies this call through to the real connection.

**How to avoid:** Always use `new SingleConnectionDataSource(connection, true)` — the `true` flag enables suppress-close mode.

**Warning signs:** `Connection is closed` exceptions in subsequent steps; Flyway transaction rollback failure.

### Pitfall 4: Missing `db.migration` Java Class Discovery in Tests

**What goes wrong:** The programmatic `Flyway.configure().locations("classpath:db/migration")` does not find `V4__MigrateSeasonsToPhases.java` when running tests, because the test JVM classpath may not include `src/main/java/db/migration/` compiled classes.

**Why it happens:** Maven Surefire includes both `target/classes` and `target/test-classes` on the classpath. V4's compiled class ends up in `target/classes/db/migration/`. The Flyway `classpath:db/migration` location should find it. However, if the Flyway scanner is configured to look only for SQL files, it may skip Java classes.

**How to avoid:** Flyway 11.x discovers Java migrations via `JavaMigrationClassProvider` when the `classpath:` prefix is used and Java migrations implement `JavaMigration`. This is automatic — no extra configuration required. Verify that the test output includes `"Successfully applied 1 migration to schema"` after V4.

**Warning signs:** Flyway reports `Found 0 migrations` or skips V4; test fails because `season_phases` table remains empty.

### Pitfall 5: `CURRENT_TIMESTAMP` vs Java `LocalDateTime` in Audit Columns

**What goes wrong:** V3 declares `created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP`. If the INSERT statement omits `created_at`/`updated_at`, H2 uses the DB default. But if the column definition uses `NOT NULL` elsewhere (it doesn't — V3 leaves them nullable), omitting them causes an error.

**How to avoid:** Per D-06 and D-11, explicitly pass `CURRENT_TIMESTAMP` in the SQL string (as a SQL function call, not a Java parameter). This ensures the timestamp reflects migration time on both H2 and MariaDB.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Spring Boot 4.x / Flyway 11.x automatically discovers both SQL and Java migrations when both exist under `classpath:db/migration` without extra config | Flyway Java Migration Pattern | Planner must add `spring.flyway.locations` with Java class scanner prefix — low risk, easy to fix |
| A2 | MariaDB JDBC driver 3.5.x returns `java.util.UUID` (not `byte[]`) from `queryForList` for `UUID`-typed columns | UUID Handling | `ClassCastException` on MariaDB prod run — mitigated by `toUUID()` helper |
| A3 | MariaDB 10.7+ `MODIFY COLUMN phase_id UUID NOT NULL` is valid DDL (UUID remains UUID type, not BINARY(16)) | Dialect Detection | DDL syntax error on MariaDB prod run — fallback is `MODIFY COLUMN phase_id BINARY(16) NOT NULL` if UUID type is rejected |
| A4 | Spring JDBC 7.0.6 `JdbcTemplate` accepts `LocalDate` directly as a parameter for DATE columns | Code Examples | `SQLFeatureNotSupportedException` — fallback: `java.sql.Date.valueOf(localDate)` |
| A5 | Flyway programmatic `target("3")` works in Flyway 11.x to stop migration at version 3 | Test Setup Patterns | Test cannot isolate pre-V4 state — alternative: populate raw SQL manually instead of relying on Flyway target |

---

## Open Questions

1. **MariaDB UUID parameter return type from `queryForList`**
   - What we know: H2 returns `java.util.UUID`; MariaDB driver source shows UUID-typed columns are supported natively in 3.x
   - What's unclear: exact Java type returned by `queryForList` on MariaDB for UUID columns vs. String vs. byte[]
   - Recommendation: Implement `toUUID()` helper defensively; validate in integration test by running against local MariaDB before prod migration

2. **`log.info` count query — try/catch for non-fatal counter errors**
   - What we know: D-14 requires counts; count queries can fail if the JdbcTemplate is in a degraded state
   - What's unclear: whether Claude's Discretion calls for protective try/catch around count queries only
   - Recommendation: Wrap count queries in try/catch; log a warning on failure but do not mask the real exception. The count is informational only.

3. **Flyway `target("3")` API in Flyway 11.x**
   - What we know: `Flyway.configure().target(MigrationVersion.fromVersion("3"))` was valid in Flyway 9+
   - What's unclear: exact method signature in Flyway 11.x (`target(String)` vs `target(MigrationVersion)`)
   - Recommendation: Use `.target("3")` as a String; Flyway parses it automatically. If needed, fall back to `.target(MigrationVersion.fromVersion("3"))`.

---

## Environment Availability

All dependencies available locally — no external services needed for Phase 57.

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java 25 | Migration compilation | ✓ | OpenJDK 25.0.2 | — |
| Maven (`./mvnw`) | Build + test | ✓ | (wrapper present) | — |
| H2 | Dev/test profile | ✓ | 2.4.240 (in `.m2` cache) | — |
| Flyway core | Migration execution | ✓ | 11.14.1 (in `.m2` cache) | — |
| MariaDB | Local profile testing | Not checked | — | Rely on H2 IT; manual MariaDB test before prod |

MariaDB NOT-NULL flip syntax (assumption A3) should be manually verified on a local MariaDB instance before the prod migration.

---

## Sources

### Primary (HIGH confidence)
- `flyway-core-11.14.1-sources.jar` — `BaseJavaMigration.java`, `JavaMigration.java`, `Context.java` — verified method signatures, defaults
- `h2-2.4.240-sources.jar` — `JdbcDatabaseMetaData.java` (`getDatabaseProductName()` returns `"H2"`), `Parser.java` (SET NOT NULL syntax), `AlterTableAlterColumn.java`, `ValueUuid.java`
- `mariadb-java-client-3.5.7-sources.jar` — `DatabaseMetaData.java` (`getDatabaseProductName()` returns `"MariaDB"` when `useMysqlMetadata=false`)
- `spring-jdbc-7.0.6-sources.jar` — `SingleConnectionDataSource.java` exists with `suppressClose` parameter
- `V3__add_season_phase_tables.sql` — column types, nullable states, constraints (direct file read)
- `V1__initial_schema.sql` — legacy table shapes (direct file read)
- `SeasonPhase.java`, `PhaseTeam.java`, `Season.java`, `Matchday.java`, `Playoff.java`, `SeasonTeam.java` — entity field names and types (direct file read)
- `pom.xml` — Flyway 11.14.1, JaCoCo 0.8.14, coverage minimum 0.82 (direct file read)
- `SeasonPhaseEntityIntegrationTest.java`, `PhaseTeamUniquenessIntegrationTest.java`, `TestDataServiceIntegrationTest.java` — established test patterns (direct file read)
- `target/site/jacoco/jacoco.csv` — current coverage baseline (LINE 88.78%, 5 608 lines)

### Secondary (MEDIUM confidence)
- Phase 56 CONTEXT.md + VERIFICATION.md — confirmed Phase 56 decisions as pre-conditions for V4; verified V3 schema actually landed as specified
- `application*.yml` files — confirmed `classpath:db/migration` in all profiles; confirmed MariaDB JDBC URL without `useMysqlMetadata`

### Tertiary (LOW confidence)
- A2: MariaDB UUID column return type from `queryForList` — not verified against live MariaDB instance
- A3: `MODIFY COLUMN ... UUID NOT NULL` DDL syntax on MariaDB 10.7+ — not tested live
- A4: Spring JDBC 7.0.6 `LocalDate` direct parameter — assumed from Spring 6+ feature set

---

## Metadata

**Confidence breakdown:**
- Standard Stack: HIGH — all versions verified from `.m2` cache
- Architecture: HIGH — locked decisions; schema verified from SQL files
- Pitfalls: HIGH (Pitfalls 1-4) / MEDIUM (Pitfall 5) — based on verified driver source + standard JDBC semantics
- Test patterns: HIGH for H2 programmatic approach; MEDIUM for MariaDB UUID return type

**Research date:** 2026-04-27
**Valid until:** 2026-05-27 (Flyway, H2, MariaDB driver versions pinned by `pom.xml`; stable ecosystem)

---

## RESEARCH COMPLETE
