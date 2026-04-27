# Phase 57: Data Migration - Pattern Map

**Mapped:** 2026-04-27
**Files analyzed:** 3 (1 production, 2 test)
**Analogs found:** 2 / 3 (partial matches only — no existing Java migration or programmatic Flyway test in repo)

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `src/main/java/db/migration/V4__MigrateSeasonsToPhases.java` | migration | batch (CRUD transform) | `src/main/java/org/ctc/admin/TestDataService.java` (logging + structured step pattern) | partial — logging/step structure only; no JdbcTemplate analog exists |
| `src/test/java/db/migration/V4MigrateSeasonsToPhasesIT.java` | test (integration) | batch | `src/test/java/org/ctc/domain/model/SeasonPhaseEntityIntegrationTest.java` | partial — annotation set + given/when/then naming + helper pattern; full-context vs. programmatic is different |
| `src/test/java/db/migration/V4MigrationSmokeIT.java` | test (smoke) | request-response | `src/test/java/org/ctc/domain/model/SeasonPhaseEntityIntegrationTest.java` | role-match — same `@SpringBootTest @ActiveProfiles("dev") @Transactional` skeleton |

---

## Pattern Assignments

### `src/main/java/db/migration/V4__MigrateSeasonsToPhases.java` (migration, batch)

**Analog:** `src/main/java/org/ctc/admin/TestDataService.java` (logging conventions only)
**Note:** No existing Java Flyway migration exists in this repo. No JdbcTemplate usage exists in any production source file. All patterns below are derived from the RESEARCH.md confirmed patterns plus the logging/step conventions extracted from TestDataService.

**Imports pattern** — derive from RESEARCH.md; no codebase analog:
```java
package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.flywaydb.core.api.FlywayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
```

**Logger pattern** (non-Spring-managed class — NOT `@Slf4j`):
```java
// From CLAUDE.md + CONVENTIONS.md: "LoggerFactory.getLogger(...) for non-managed migration classes"
private static final Logger log = LoggerFactory.getLogger(V4__MigrateSeasonsToPhases.class);
```
Contrast with Spring-managed classes (TestDataService line 23) which use `@Slf4j` Lombok annotation. Migration classes cannot use Lombok `@Slf4j` because they are not Spring beans.

**Logging step pattern** — modeled on TestDataService lines 69–71 and 78:
```java
// TestDataService.java line 69-71 (parameterized {}, counts, state change):
log.info("Seed data created: {} teams, {} seasons, {} drivers, {} race-lineups, {} results",
        teamRepository.count(), seasonRepository.count(), ...);

// TestDataService.java line 78 (single-count variant):
log.info("Created default scoring presets: {} / {}", raceScoring.getName(), matchScoring.getName());

// V4 adaptation — use same parameterized {} format, count-per-step:
log.info("Migrated {} REGULAR phases (one per season)", seasons.size());
log.info("Migrated {} PLAYOFF phases", playoffCount);
log.info("Updated phase_id on {} matchdays", matchdayCount);
log.info("Migrated {} phase_teams entries (one per season_team)", seasonTeams.size());
```

**Core entry-point pattern** (BaseJavaMigration, from RESEARCH.md verified patterns):
```java
@Override
public void migrate(Context context) throws Exception {
    var connection = context.getConnection();
    var jdbcTemplate = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
    var dialect = connection.getMetaData().getDatabaseProductName();

    migrateRegularPhases(jdbcTemplate);
    migratePlayoffPhases(jdbcTemplate);
    migrateMatchdayFKs(jdbcTemplate);
    migratePhaseTeams(jdbcTemplate);
    flipNotNullConstraints(jdbcTemplate, dialect);
}
```
`suppressClose=true` is critical — prevents SingleConnectionDataSource from closing the Flyway-managed connection (Pitfall 3 in RESEARCH.md).

**REGULAR phase INSERT step** (from RESEARCH.md verified INSERT pattern + V3 schema):
```java
private Map<UUID, UUID> migrateRegularPhases(JdbcTemplate jdbcTemplate) {
    List<Map<String, Object>> seasons = jdbcTemplate.queryForList("SELECT * FROM seasons");
    Map<UUID, UUID> seasonToRegularPhaseId = new HashMap<>();
    for (Map<String, Object> season : seasons) {
        UUID seasonId = toUUID(season.get("id"));
        UUID raceScoringId = toUUID(season.get("race_scoring_id"));
        UUID matchScoringId = toUUID(season.get("match_scoring_id"));
        if (raceScoringId == null) throw new FlywayException("Season " + seasonId + " has null race_scoring_id");
        if (matchScoringId == null) throw new FlywayException("Season " + seasonId + " has null match_scoring_id");
        UUID newPhaseId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO season_phases (id, season_id, sort_index, phase_type, layout, format, "
            + "label, start_date, end_date, total_rounds, legs, event_duration_minutes, "
            + "race_scoring_id, match_scoring_id, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            newPhaseId, seasonId, 0, "REGULAR", "LEAGUE",
            season.get("format"), null,
            season.get("start_date"), season.get("end_date"),
            season.get("total_rounds"), season.get("legs"),
            season.get("event_duration_minutes"),
            raceScoringId, matchScoringId
        );
        seasonToRegularPhaseId.put(seasonId, newPhaseId);
    }
    log.info("Migrated {} REGULAR phases (one per season)", seasons.size());
    return seasonToRegularPhaseId;
}
```
Column order matches V3 schema (`src/main/resources/db/migration/V3__add_season_phase_tables.sql` lines 5–26). `CURRENT_TIMESTAMP` is passed as a SQL keyword in the string, not as a Java parameter (Pitfall 5 in RESEARCH.md).

**UUID helper** (mandatory — H2 returns UUID, MariaDB may return byte[]):
```java
private static UUID toUUID(Object value) {
    if (value == null) return null;
    if (value instanceof UUID u) return u;
    if (value instanceof byte[] b) {
        ByteBuffer bb = ByteBuffer.wrap(b);
        return new UUID(bb.getLong(), bb.getLong());
    }
    if (value instanceof String s) return UUID.fromString(s);
    throw new IllegalArgumentException("Cannot convert " + value.getClass() + " to UUID");
}
```

**NOT-NULL flip** (dialect-conditional DDL, from RESEARCH.md confirmed syntax):
```java
private void flipNotNullConstraints(JdbcTemplate jdbcTemplate, String dialect) {
    if ("H2".equals(dialect)) {
        jdbcTemplate.execute("ALTER TABLE matchdays ALTER COLUMN phase_id SET NOT NULL");
        jdbcTemplate.execute("ALTER TABLE playoffs ALTER COLUMN phase_id SET NOT NULL");
    } else {
        // MariaDB (and fallback for any other dialect)
        jdbcTemplate.execute("ALTER TABLE matchdays MODIFY COLUMN phase_id UUID NOT NULL");
        jdbcTemplate.execute("ALTER TABLE playoffs MODIFY COLUMN phase_id UUID NOT NULL");
    }
    log.info("Flipped phase_id columns to NOT NULL on both matchdays and playoffs (dialect: {})", dialect);
}
```

**Matchday FK backfill** (single correlated UPDATE, D-10):
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

---

### `src/test/java/db/migration/V4MigrateSeasonsToPhasesIT.java` (integration test, batch)

**Analog:** `src/test/java/org/ctc/domain/model/SeasonPhaseEntityIntegrationTest.java`
**Note:** This file is a **green-field pattern** — no programmatic Flyway IT exists in the repo. The analog provides: (a) BDD method naming convention, (b) AssertJ assertion style, (c) helper method structure. The programmatic `EmbeddedDatabaseBuilder` + `Flyway.configure()` pattern is new to this codebase.

**Package declaration:**
```java
package db.migration;
```
Mirrors `src/main/java/db/migration/` per D-19 (test packages mirror production packages).

**Class-level annotations** — programmatic Flyway, NOT `@SpringBootTest`:
```java
// No @SpringBootTest — programmatic Flyway controls migration sequence
// No @ActiveProfiles — not a Spring context test
// @TestInstance(PER_CLASS) allows @BeforeAll in instance method (no static required)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V4MigrateSeasonsToPhasesIT {
```

**Setup pattern** (EmbeddedDatabaseBuilder + Flyway.configure(), new to repo):
```java
private DataSource dataSource;
private JdbcTemplate jdbcTemplate;

@BeforeAll
void setUp() {
    dataSource = new EmbeddedDatabaseBuilder()
        .setType(EmbeddedDatabaseType.H2)
        .build();
    jdbcTemplate = new JdbcTemplate(dataSource);

    // Run V1, V2, V3 only — stop before V4
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .target("3")
        .load()
        .migrate();

    // Seed legacy data (pre-V4 state per D-17)
    seedLegacyData();

    // Run V4 (the class under test)
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .target("4")
        .load()
        .migrate();
}
```

**Test method naming** — copy exactly from SeasonPhaseEntityIntegrationTest.java convention (lines 79, 103, 128, 147, 165, 183):
```java
// Pattern from SeasonPhaseEntityIntegrationTest:
// givenContext_whenAction_thenExpectedResult()
// with // given / // when / // then inline comments

@Test
void givenLegacySeasons_whenMigrationRuns_thenEachSeasonHasOneRegularPhase() {
    // given — 3 seasons seeded in setUp()
    // when — migration already ran in @BeforeAll

    // then
    Integer count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM season_phases WHERE phase_type = 'REGULAR'", Integer.class);
    assertThat(count).isEqualTo(3);
}
```

**AssertJ style** — copy from SeasonPhaseEntityIntegrationTest lines 95–99:
```java
// From SeasonPhaseEntityIntegrationTest.java lines 95-99:
assertThat(saved.getId()).isNotNull();
assertThat(saved.getCreatedAt()).isNotNull();

// V4 adaptation — raw count/value assertions via JdbcTemplate:
assertThat(count).isEqualTo(3);
assertThat(phaseId).isNotNull();
```

**Seed helper structure** — modeled on TestDataService private seed methods (lines 73–80):
```java
// TestDataService.java pattern — private method, explicit log.info, returns seeded ids:
private ScoringDefaults seedScorings() {
    var raceScoring = raceScoringRepository.save(...);
    log.info("Created default scoring presets: {} / {}", ...);
    return new ScoringDefaults(raceScoring, matchScoring);
}

// V4 adaptation — private seed methods using JdbcTemplate.update() directly:
private void seedLegacyData() {
    // Insert scoring presets (required by NOT NULL FK constraints on seasons)
    UUID raceScId = UUID.randomUUID();
    UUID matchScId = UUID.randomUUID();
    jdbcTemplate.update("INSERT INTO race_scorings (...) VALUES (...)", ...);
    jdbcTemplate.update("INSERT INTO match_scorings (...) VALUES (...)", ...);
    // Insert 3 seasons, 2 teams per season, 2 matchdays per season, 1 playoff
    ...
}
```
Use fixed (deterministic) UUIDs declared as constants in the test class — easier to debug when a specific row assertion fails.

**NOT-NULL constraint verification** — modeled on PhaseTeamUniquenessIntegrationTest lines 83–85:
```java
// From PhaseTeamUniquenessIntegrationTest.java lines 83-85:
assertThatThrownBy(() -> seasonPhaseRepository.saveAndFlush(secondRegular))
        .isInstanceOf(DataIntegrityViolationException.class);

// V4 adaptation — raw JDBC:
assertThatThrownBy(() ->
    jdbcTemplate.update("INSERT INTO matchdays (id, season_id, label, sort_index) VALUES (?, ?, ?, ?)",
        UUID.randomUUID(), existingSeasonId, "MD-noPhase", 99))
    .isInstanceOf(DataIntegrityViolationException.class);
// Note: after V4 NOT-NULL flip, inserting a matchday without phase_id should violate constraint.
// H2: the column no longer accepts NULL, so omitting it triggers NOT NULL violation.
```

---

### `src/test/java/db/migration/V4MigrationSmokeIT.java` (smoke test, request-response)

**Analog:** `src/test/java/org/ctc/domain/model/SeasonPhaseEntityIntegrationTest.java` (lines 43–46, 48–50, 79)
**Match quality:** role-match — same `@SpringBootTest @ActiveProfiles("dev") @Transactional` skeleton

**Full annotation pattern** — copy verbatim from SeasonPhaseEntityIntegrationTest.java lines 43–46:
```java
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class V4MigrationSmokeIT {
```

**Repository injection** — copy from SeasonPhaseEntityIntegrationTest lines 48–50:
```java
// From SeasonPhaseEntityIntegrationTest.java lines 48-50:
@Autowired
private SeasonRepository seasonRepository;
```

**Smoke test method** — modeled on TestDataServiceIntegrationTest convention (lines 57–62):
```java
// TestDataServiceIntegrationTest pattern — given/when/then; findAll() + assertThat:
@Test
void givenDevSeed_whenStarted_thenS1GroupAHasFormatRoundRobin() {
    var season = findSeason(2023, "Group A");
    assertThat(season.getFormat()).isEqualTo(SeasonFormat.ROUND_ROBIN);
}

// V4 smoke test adaptation:
@Test
void whenContextLoads_thenAllSeasonsHavePhases() {
    // when
    List<Season> seasons = seasonRepository.findAll();

    // then — on empty dev H2, trivially passes; validates Flyway+JPA+Spring alignment
    seasons.forEach(s -> assertThat(s.getPhases()).isNotEmpty());
}
```
`whenContextLoads_thenAllSeasonsHavePhases()` uses the shortened form (no `given` precondition) per CLAUDE.md: "For simple tests without preconditions: `whenAction_thenResult()` is allowed."

**Package declaration:**
```java
package db.migration;
```

---

## Shared Patterns

### Logging (non-Spring-managed classes)
**Source:** CLAUDE.md §Conventions + CONVENTIONS.md
**Apply to:** `V4__MigrateSeasonsToPhases.java` only (migration is not Spring-managed)
```java
// For non-Spring-managed classes (migrations), use explicit static logger:
private static final Logger log = LoggerFactory.getLogger(V4__MigrateSeasonsToPhases.class);

// For Spring-managed classes (services, seeders), use Lombok:
// @Slf4j (see TestDataService.java line 23, DevDataSeeder.java line 8)
```

### Logging format — parameterized `{}`
**Source:** `src/main/java/org/ctc/admin/TestDataService.java` lines 69–71, 78
**Apply to:** All `log.info()` / `log.warn()` calls in `V4__MigrateSeasonsToPhases.java`
```java
// Always parameterized {}, never string concatenation:
log.info("Migrated {} REGULAR phases (one per season)", seasons.size());
log.info("Site generated: {} pages, {} errors", result.getPagesGenerated(), result.getErrors().size());
```

### Test annotation set — `@SpringBootTest @ActiveProfiles("dev") @Transactional`
**Source:** `src/test/java/org/ctc/domain/model/SeasonPhaseEntityIntegrationTest.java` lines 43–46
**Apply to:** `V4MigrationSmokeIT.java`
```java
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
```
This triple is the project's established pattern for every integration test that uses the Spring context.

### Test naming — BDD `givenContext_whenAction_thenExpectedResult()`
**Source:** `src/test/java/org/ctc/domain/model/SeasonPhaseEntityIntegrationTest.java` lines 79, 103, 128, 147, 165, 183
**Apply to:** All test methods in both `V4MigrateSeasonsToPhasesIT` and `V4MigrationSmokeIT`
```java
// Established in SeasonPhaseEntityIntegrationTest:
void givenNewSeasonPhase_whenSaved_thenAuditFieldsArePopulated()
void givenSeasonPhaseWithGroups_whenReloaded_thenGroupsCollectionIsOrderedBySortIndex()

// V4 locked method names from D-16:
void givenLegacySeasons_whenMigrationRuns_thenEachSeasonHasOneRegularPhase()
void givenLegacyPlayoff_whenMigrationRuns_thenPlayoffLinkedViaPhaseId()
void givenLegacyMatchdays_whenMigrationRuns_thenAllMatchdaysHavePhaseId()
void givenLegacySeasonTeams_whenMigrationRuns_thenPhaseTeamsPopulated()
void givenLegacyData_whenMigrationRuns_thenBridgeColumnsRemainIntact()
void givenMigratedSchema_whenMatchdayInsertedWithoutPhaseId_thenViolatesNotNullConstraint()
```

### AssertJ assertions
**Source:** `src/test/java/org/ctc/domain/model/SeasonPhaseEntityIntegrationTest.java` lines 95–99; `PhaseTeamUniquenessIntegrationTest.java` lines 83–85
**Apply to:** All test assertion lines in both test files
```java
// From SeasonPhaseEntityIntegrationTest (assertThat + chained matchers):
assertThat(saved.getId()).isNotNull();
assertThat(saved.getCreatedAt()).isNotNull();

// From PhaseTeamUniquenessIntegrationTest (exception assertion):
assertThatThrownBy(() -> ...).isInstanceOf(DataIntegrityViolationException.class);
```

### Test data prefixes
**Source:** CLAUDE.md §"Isolate Test Data Completely"; SeasonPhaseEntityIntegrationTest lines 81, 105, 110
**Apply to:** All seed data in `V4MigrateSeasonsToPhasesIT.seedLegacyData()`
```java
// Established prefix pattern from SeasonPhaseEntityIntegrationTest:
// "Phase56-Test-S1", "Phase56-Test-Team-A", "Phase56-Test-B"
// V4 adaptation:
"Phase57-Test-Season-1", "Phase57-Test-Season-2", "Phase57-Test-Season-3"
"Phase57-Test-Team-A", "Phase57-Test-Team-B"
"Phase57-Test-Playoff-1"
```

### Audit columns via CURRENT_TIMESTAMP
**Source:** `src/main/resources/db/migration/V3__add_season_phase_tables.sql` lines 17–18; RESEARCH.md §"Pitfall 5"
**Apply to:** All INSERT statements in `V4__MigrateSeasonsToPhases.java`
```sql
-- V3 declares: created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
-- V4 must explicitly pass it as a SQL function call (not a Java parameter):
... created_at, updated_at) VALUES (..., CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
```

---

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| `src/main/java/db/migration/V4__MigrateSeasonsToPhases.java` | migration | batch | No Java Flyway migration exists in this repo (V1/V2/V3 are all SQL). No `JdbcTemplate` usage in any production class. Planner must use RESEARCH.md patterns (all verified against library sources) rather than codebase analogs for the Flyway/JdbcTemplate portions. |
| `src/test/java/db/migration/V4MigrateSeasonsToPhasesIT.java` | test (integration) | batch | No programmatic Flyway test (`Flyway.configure()`, `EmbeddedDatabaseBuilder`) exists in the repo. RESEARCH.md §"Recommended Pattern for V4MigrateSeasonsToPhasesIT" provides the verified pattern. |

---

## Metadata

**Analog search scope:** `src/main/java/org/ctc/admin/`, `src/main/java/org/ctc/domain/model/`, `src/test/java/org/ctc/`, `src/main/resources/db/migration/`
**Files scanned:** 10 (4 integration tests, 2 seeders, 2 model entities, 2 SQL migrations)
**JdbcTemplate usages in codebase:** 0 (confirmed by grep)
**Programmatic Flyway usages in tests:** 0 (confirmed by grep)
**Pattern extraction date:** 2026-04-27
