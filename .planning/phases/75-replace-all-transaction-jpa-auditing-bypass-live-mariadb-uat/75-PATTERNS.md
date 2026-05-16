# Phase 75: Replace-All Transaction + JPA Auditing Bypass + Live MariaDB UAT — Pattern Map

**Mapped:** 2026-05-14
**Files analyzed:** 56 (28 main + 27 test + 1 doc)
**Analogs found:** 56 / 56 (every file has at least a partial analog inside the existing `org.ctc.backup.*` subsystem)

> Pattern source priority: (1) Phase 74 `BackupImportService` + `BackupArchiveService` for service-extension shape, (2) Phase 73 24-MixIn family for the 24-entity-per-table parallelism, (3) Phase 72 `DataImportAudit` + `BackupSchema` for audit-row + topo-order glue, (4) `org.ctc.domain.*` for non-backup idioms (entity lookup, exception base, `BaseEntity`).
> Per CLAUDE.md: Lombok `@Slf4j @RequiredArgsConstructor` on services; Form DTOs with `@Valid + BindingResult`; flash keys `successMessage` / `errorMessage`; UUID columns are native `UUID` (V1 schema, NOT `BINARY(16)`).
> Per RESEARCH.md §1 / §2 / §5 / §10: `ParameterizedPreparedStatementSetter` auto-chunking, `@TransactionalEventListener(AFTER_COMMIT)`, `ps.setObject(idx, uuid)`, `rewriteBatchedStatements=true` missing from yml (must be added).

---

## File Classification

### Main production code (`src/main/java/org/ctc/backup/`)

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `org.ctc.backup.restore.EntityRestorer` (NEW interface) | interface (SPI) | batch-insert | none in main; closest: `org.ctc.backup.schema.EntityRef` shape (per-entity contract per `BackupSchema.getExportOrder()`) | role-only — new SPI family |
| `org.ctc.backup.restore.entity.CarRestorer` (NEW) | impl (24×) | batch-insert | `org.ctc.backup.serialization.CarMixIn` (Phase 73 leaf-entity sibling) + `Car.java` entity | strong (entity 1:1 + parallel 24-class family) |
| `org.ctc.backup.restore.entity.DriverRestorer` (NEW) | impl | batch-insert | `org.ctc.backup.serialization.DriverMixIn` + `Driver.java` | strong |
| `org.ctc.backup.restore.entity.MatchRestorer` (NEW) | impl | batch-insert | `org.ctc.backup.serialization.MatchMixIn` + `Match.java` | strong |
| `org.ctc.backup.restore.entity.MatchScoringRestorer` (NEW) | impl | batch-insert | `org.ctc.backup.serialization.MatchScoringMixIn` + `MatchScoring.java` | strong |
| `org.ctc.backup.restore.entity.MatchdayRestorer` (NEW) | impl | batch-insert | `org.ctc.backup.serialization.MatchdayMixIn` + `Matchday.java` | strong |
| `org.ctc.backup.restore.entity.PhaseTeamRestorer` (NEW) | impl | batch-insert | `org.ctc.backup.serialization.PhaseTeamMixIn` + `PhaseTeam.java` | strong |
| `org.ctc.backup.restore.entity.PlayoffRestorer` (NEW) | impl | batch-insert | `org.ctc.backup.serialization.PlayoffMixIn` + `Playoff.java` | strong |
| `org.ctc.backup.restore.entity.PlayoffMatchupRestorer` (NEW) | impl | batch-insert | `org.ctc.backup.serialization.PlayoffMatchupMixIn` + `PlayoffMatchup.java` | strong |
| `org.ctc.backup.restore.entity.PlayoffRoundRestorer` (NEW) | impl | batch-insert | `org.ctc.backup.serialization.PlayoffRoundMixIn` + `PlayoffRound.java` | strong |
| `org.ctc.backup.restore.entity.PlayoffSeedRestorer` (NEW) | impl | batch-insert | `org.ctc.backup.serialization.PlayoffSeedMixIn` + `PlayoffSeed.java` | strong |
| `org.ctc.backup.restore.entity.PsnAliasRestorer` (NEW) | impl | batch-insert | `org.ctc.backup.serialization.PsnAliasMixIn` + `PsnAlias.java` | strong |
| `org.ctc.backup.restore.entity.RaceAttachmentRestorer` (NEW) | impl | batch-insert | `org.ctc.backup.serialization.RaceAttachmentMixIn` + `RaceAttachment.java` | strong |
| `org.ctc.backup.restore.entity.RaceLineupRestorer` (NEW) | impl | batch-insert | `org.ctc.backup.serialization.RaceLineupMixIn` + `RaceLineup.java` | strong |
| `org.ctc.backup.restore.entity.RaceRestorer` (NEW) | impl | batch-insert | `org.ctc.backup.serialization.RaceMixIn` + `Race.java` | strong |
| `org.ctc.backup.restore.entity.RaceResultRestorer` (NEW) | impl | batch-insert | `org.ctc.backup.serialization.RaceResultMixIn` + `RaceResult.java` | strong |
| `org.ctc.backup.restore.entity.RaceScoringRestorer` (NEW) | impl | batch-insert | `org.ctc.backup.serialization.RaceScoringMixIn` + `RaceScoring.java` | strong |
| `org.ctc.backup.restore.entity.RaceSettingsRestorer` (NEW) | impl | batch-insert | `org.ctc.backup.serialization.RaceSettingsMixIn` + `RaceSettings.java` | strong |
| `org.ctc.backup.restore.entity.SeasonDriverRestorer` (NEW) | impl | batch-insert | `org.ctc.backup.serialization.SeasonDriverMixIn` + `SeasonDriver.java` | strong |
| `org.ctc.backup.restore.entity.SeasonPhaseGroupRestorer` (NEW) | impl | batch-insert | `org.ctc.backup.serialization.SeasonPhaseGroupMixIn` + `SeasonPhaseGroup.java` | strong |
| `org.ctc.backup.restore.entity.SeasonPhaseRestorer` (NEW) | impl | batch-insert | `org.ctc.backup.serialization.SeasonPhaseMixIn` + `SeasonPhase.java` | strong |
| `org.ctc.backup.restore.entity.SeasonRestorer` (NEW) | impl | batch-insert | `org.ctc.backup.serialization.SeasonMixIn` + `Season.java` | strong |
| `org.ctc.backup.restore.entity.SeasonTeamRestorer` (NEW) | impl | batch-insert | `org.ctc.backup.serialization.SeasonTeamMixIn` + `SeasonTeam.java` | strong |
| `org.ctc.backup.restore.entity.TeamRestorer` (NEW — 2-pass) | impl (2-pass) | batch-insert + bulk-update | `org.ctc.backup.serialization.TeamMixIn` + `Team.java` + V1 self-FK schema | strong + special-case (D-06) |
| `org.ctc.backup.restore.entity.TrackRestorer` (NEW) | impl | batch-insert | `org.ctc.backup.serialization.TrackMixIn` + `Track.java` | strong |
| `org.ctc.backup.restore.RestoreFailureInjector` (NEW interface) | interface (SPI) | hook-point | none direct; closest: small SPI-like single-method extension points | role-only |
| `org.ctc.backup.restore.NoopRestoreFailureInjector` (NEW `@Primary`) | impl (no-op) | hook-point | `BackupObjectMapperConfig.defaultObjectMapper(...)` (`@Primary` precedent) + `BackupStagingCleanup` (`@Component` no-args bean) | partial — `@Primary` + tiny `@Component` shape |
| `org.ctc.backup.restore.UuidPacker` (NEW utility) | **DROPPED** | n/a | n/a — RESEARCH §5 confirms UUID columns are typed `UUID`, NOT `BINARY(16)`; CONTEXT D-08 fallback is unnecessary | DROPPED |
| `org.ctc.backup.audit.DataImportAuditService` (NEW) | service (REQUIRES_NEW writer) | CRUD (write-only) | `BackupImportService.deleteStagingFile(...)` (Phase 74) for class shape; `DataImportAudit` (Phase 72) for entity surface; `DataImportAuditRepository` (Phase 72) for repo wiring | role-match (no existing REQUIRES_NEW writer in repo) |
| `org.ctc.backup.exception.UploadsRestoreException` (NEW) | exception | n/a | `org.ctc.backup.exception.BackupArchiveException` (Phase 74) | strong |
| `org.ctc.backup.exception.RestoreFailureSimulatedException` (NEW) | exception (test-only via main package) | n/a | `org.ctc.backup.exception.BackupArchiveException` (Phase 74) for ctor; `org.ctc.domain.exception.BusinessRuleException` for minimal shape | strong |
| `org.ctc.backup.service.BackupImportService.execute(UUID)` (EXTEND) | service-method | request-response + tx-boundary | `BackupImportService.stage(...)` / `reparse(...)` / `deleteStagingFile(...)` (same class, Phase 74) | exact (same-class extension anchor per D-14) |
| `org.ctc.backup.service.BackupArchiveService.extractUploadsTo(Path, Path)` (EXTEND) | service-method | streaming file-I/O | `BackupArchiveService.countUploadFiles(Path)` (same class, Phase 74) | exact (same-class extension anchor per D-12) |
| `org.ctc.backup.service.BackupImportPostCommitListener` (NEW) | listener (`@TransactionalEventListener`) | event-driven (AFTER_COMMIT) | `BackupStagingCleanup` (`@EventListener(ApplicationReadyEvent)` precedent — closest event-listener bean in the codebase) | role-only — Phase 75 introduces the first `@TransactionalEventListener` |
| `org.ctc.backup.BackupController.importExecute(...)` (MODIFY) | controller-method | request-response | `BackupController.importExecute(...)` Phase 74 stub (same method, same class) | exact (replace stub-body with real call) |
| `org.ctc.admin.controller.GlobalExceptionHandler` (OPTIONAL EXTEND) | exception-handler | n/a | `GlobalExceptionHandler.handleBusinessRule(...)` (same class) | exact |

### Configuration (`src/main/resources/`)

| File | Role | Change |
|------|------|--------|
| `application.yml` (MODIFY) | config | Add `app.backup.import-backups-dir: data/.import-backups` under `app.backup` |
| `application-local.yml` (MODIFY) | config | Append `?rewriteBatchedStatements=true` to JDBC URL (RESEARCH §10) |
| `application-docker.yml` (MODIFY) | config | Append `?rewriteBatchedStatements=true` to JDBC URL (RESEARCH §10) |
| `application-prod.yml` (MODIFY) | config | Document expected query-string convention — `DATABASE_URL` is env-var; mention in comment or sidecar README. CONTEXT D-07 claims it is set; RESEARCH §10 says NOT. Planner re-verifies at execute time. |

### Test code (`src/test/java/org/ctc/backup/`)

| New/Modified File | Role | Closest Analog | Match Quality |
|-------------------|------|----------------|---------------|
| `org.ctc.backup.restore.entity.CarRestorerTest` (NEW — and 23 siblings) | unit (Surefire) | `org.ctc.backup.serialization.CarMixInTest`-shape (Phase 73 — actually `TeamMixInTest` exists; `CarMixInTest` is not shipped but the pattern parallels the 5 written MixIn tests) | strong (24-class parallel family) |
| `org.ctc.backup.restore.entity.TeamRestorerIT` (NEW) | IT (Failsafe, H2) | `BackupImportServiceIT` (`@SpringBootTest @ActiveProfiles("dev")` shape) | exact |
| `org.ctc.backup.restore.NoopRestoreFailureInjectorTest` (NEW) | unit | any small Surefire unit test in `org.ctc.backup.*` (e.g., `BackupImportLimitsTest`) | role-match |
| `org.ctc.backup.audit.DataImportAuditServiceTest` (NEW) | unit | none direct; uses `@MockitoSpyBean` on `PlatformTransactionManager` to verify REQUIRES_NEW (RESEARCH §1) | role-only |
| `org.ctc.backup.audit.DataImportAuditSerializationTest` (NEW) | unit | `BackupManifestSerializationTest` (`backupObjectMapper` round-trip pattern) | strong |
| `org.ctc.backup.service.BackupImportExecuteIT` (NEW) | IT | `BackupImportServiceIT` (same class location, same `@SpringBootTest @ActiveProfiles("dev") @TestInstance(PER_CLASS)` shape) | exact |
| `org.ctc.backup.service.BackupImportRollbackIT` (NEW) | IT | `BackupImportServiceIT` skeleton + D-13 injector override | exact + special-case |
| `org.ctc.backup.service.BackupImportPostCommitIT` (NEW) | IT | `BackupImportServiceIT` skeleton + tempdir for `uploads-old` / `uploads-new` | exact + filesystem |
| `org.ctc.backup.service.BackupArchiveExtractUploadsIT` (NEW) | IT | `BackupArchiveServiceReadIT` (sibling to `countUploadFiles`) | strong |
| `org.ctc.backup.service.BackupImportMariaDbSmokeIT` (NEW) | IT (`@ActiveProfiles("local")`) | `TemplateRenderingSmokeIT` (`@SpringBootTest @AutoConfigureMockMvc` shape) + `mariadb-migration-smoke.yml` workflow (v1.9 D-22 precedent) | role-match (TemplateRenderingSmokeIT is dev-profile; the MariaDB workflow runs against the packaged JAR — Phase 75 IT plugs into the workflow) |
| `org.ctc.e2e.BackupImportE2ETest` (EXTEND, do NOT create) | E2E (Playwright) | the existing Phase 74 file — Phase 75 REMOVES stub-flash test + ADDS real-success-flash test | exact (file already exists at the given path) |

### Documentation

| File | Role | Closest Analog |
|------|------|----------------|
| `.planning/phases/75-.../75-HUMAN-UAT.md` (NEW) | docs (manual UAT checklist) | `.planning/phases/73-.../73-HUMAN-UAT.md` (3-test partial-format example) + memory `feedback_playwright_cli.md` + `feedback_screenshots_folder.md` |

---

## Pattern Assignments

### Group 1 — `org.ctc.backup.restore` (interface + injector + 24 restorers)

#### `org.ctc.backup.restore.EntityRestorer` (interface, D-05)

**Analogs:**
- **Conceptual shape:** `org.ctc.backup.schema.EntityRef` (`src/main/java/org/ctc/backup/schema/EntityRef.java`) — per-entity contract record discovered/used by `BackupSchema.getExportOrder()`.
- **Spring discovery:** the 24 MixIn classes (`org.ctc.backup.serialization.*MixIn`) are NOT `@Component`-discovered (they are registered via `BackupSerializationModule`); the closest `@Component`-discovered SPI in the codebase is the 18 Spring Data `JpaRepository<?, ?>` interfaces collected by `BackupImportService` constructor parameter `List<JpaRepository<?, ?>> allRepositories` (line 64).

**Canonical SPI shape to mirror** (from `BackupImportService.java:64-67` + `BackupImportService.java:104-132`):
```java
// Wiring side (planner — inject Map<String, EntityRestorer<?>> at orchestrator constructor)
private final Map<String, EntityRestorer<?>> restorersByTableName;

// Constructor parameter — Spring auto-injects the bean Map keyed by @Component bean name;
// alternative: inject List<EntityRestorer<?>> and build the map in @PostConstruct mirroring
// BackupImportService.wireRepositoriesByTableName() (lines 104-132 — exact shape).
```

**Interface signature** (per CONTEXT D-05; planner-recommended in RESEARCH §1 — auto-chunking):
```java
package org.ctc.backup.restore;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

public interface EntityRestorer {
    /** snake_case table name; key into BackupSchema.getExportOrder() */
    String tableName();

    /** Restores all rows from the JSON array into the table.
     *  Default impl: single-batch INSERT via insertSql() + setter.
     *  TeamRestorer overrides to perform 2-pass (INSERT-NULL → UPDATE-FK).
     *  RESEARCH §1: ParameterizedPreparedStatementSetter is the recommended flavor;
     *  batchSize comes from a configurable constant (D-07 default = 500). */
    void restore(List<JsonNode> rows, JdbcTemplate jdbcTemplate);
}
```

**Variation notes:**
- D-05 originally proposed three methods (`tableName()` / `insertSql()` / `setter()`). The 2-pass `TeamRestorer` (D-06) does not fit a `tableName() + insertSql() + setter()` triple (it has TWO SQL strings + two setter blocks). Collapsing to a single `restore(rows, jdbcTemplate)` method per CONTEXT "Claude's Discretion" keeps the orchestrator generic AND lets `TeamRestorer` hide its 2-pass discipline. Per-entity tests assert on the SQL emitted via spy `JdbcTemplate`.
- `org.ctc.backup.schema.EntityRef` is NOT extended/implemented here — it is a separate read-side contract that this interface lives parallel to (key by `tableName()` for the orchestrator lookup).

**Project conventions to apply:**
- Plain `interface` per CONTEXT "Claude's Discretion" (NOT `sealed interface` — over-engineering with 24 permits).
- No Lombok on interfaces.
- Package: `org.ctc.backup.restore` (production package — extension points + utility code).
- Javadoc cross-references `BackupSchema.getExportOrder()` (Phase 72) and `BackupImportService.execute()` (Phase 75 D-14).

---

#### `org.ctc.backup.restore.entity.<Entity>Restorer` × 24 (D-05, D-06, D-08)

**Master analog (parallel 24-class family):** the 24 Jackson MixIns at `org.ctc.backup.serialization.*MixIn`.

**1:1 entity mapping** (one Restorer per entity in `BackupSchema.getExportOrder()`):

| Restorer | Entity (`org.ctc.domain.model`) | Table | Notes |
|----------|----------------------------------|-------|-------|
| `CarRestorer` | `Car` | `cars` | V1 — leaf, 4 columns + `created_at`/`updated_at` |
| `DriverRestorer` | `Driver` | `drivers` | V1 — leaf, `psn_id UNIQUE` |
| `MatchRestorer` | `Match` | `matches` | V1 — FK matchday/home_team/away_team |
| `MatchScoringRestorer` | `MatchScoring` | `match_scorings` | V1 — leaf, `name UNIQUE` |
| `MatchdayRestorer` | `Matchday` | `matchdays` | V1 + V3 (FK to phase/group columns added in V3) |
| `PhaseTeamRestorer` | `PhaseTeam` | `phase_teams` | V3 |
| `PlayoffRestorer` | `Playoff` | `playoffs` | V1 + V3 (phase_id column added in V3) |
| `PlayoffMatchupRestorer` | `PlayoffMatchup` | `playoff_matchups` | V1 — round/team1/team2/winner/next_matchup FKs |
| `PlayoffRoundRestorer` | `PlayoffRound` | `playoff_rounds` | V1 |
| `PlayoffSeedRestorer` | `PlayoffSeed` | `playoff_seeds` | V1 |
| `PsnAliasRestorer` | `PsnAlias` | `psn_aliases` | (verify V1 file — alias_lower UNIQUE per Phase 71) |
| `RaceAttachmentRestorer` | `RaceAttachment` | `race_attachments` | V1 |
| `RaceLineupRestorer` | `RaceLineup` | `race_lineups` | V1 — Race+Driver+Team triple |
| `RaceRestorer` | `Race` | `races` | V1 — heavy: 10+ columns, multiple FKs |
| `RaceResultRestorer` | `RaceResult` | `race_results` | V1 — heaviest by row count |
| `RaceScoringRestorer` | `RaceScoring` | `race_scorings` | V1 — `race_points VARCHAR(500)` comma-separated |
| `RaceSettingsRestorer` | `RaceSettings` | `race_settings` | V1 — 12+ columns |
| `SeasonDriverRestorer` | `SeasonDriver` | `season_drivers` | V1 — season+driver+team triple, UNIQUE(season,driver) |
| `SeasonPhaseGroupRestorer` | `SeasonPhaseGroup` | `season_phase_groups` | V3 |
| `SeasonPhaseRestorer` | `SeasonPhase` | `season_phases` | V3 |
| `SeasonRestorer` | `Season` | `seasons` | V1 — central entity, race_scoring+match_scoring FKs |
| `SeasonTeamRestorer` | `SeasonTeam` | `season_teams` | V1 — successor_season_team_id self-FK exists but per CONTEXT only `Team.parentTeam` is 2-pass; `SeasonTeam.successor_season_team_id` is **also self-FK** — planner verifies whether it also needs 2-pass (NOT mentioned in CONTEXT/RESEARCH — possible gap; see Open Questions below). |
| `TeamRestorer` | `Team` | `teams` | V1 — **2-PASS** per D-06 (parent_team_id self-FK) |
| `TrackRestorer` | `Track` | `tracks` | V1 — leaf, `name UNIQUE` |

**Closest concrete code analog — `org.ctc.backup.serialization.CarMixIn`** (leaf parallel for the 22 non-special restorers):
```java
// File: src/main/java/org/ctc/backup/serialization/CarMixIn.java
package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import org.ctc.domain.model.Car;

/**
 * Externalised Jackson annotations for {@link Car}. Phase 73 EXPORT-04.
 *
 * <p>Trivial leaf entity — no foreign keys, no bidirectional collections...
 */
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "displayName"})
public abstract class CarMixIn {
}
```

**Concrete restorer template** (planner copies this skeleton 24 times; type-coercion lives next to the SQL per D-08):
```java
package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Phase 75 / Plan 04 — restores rows into the {@code cars} table from
 * the {@code data/cars.json} array in a backup ZIP.
 *
 * <p>Schema reference (V1 + V2 — no V3+ changes for cars):
 * <pre>
 * CREATE TABLE cars (id UUID PRIMARY KEY, manufacturer VARCHAR(255) NOT NULL,
 *   name VARCHAR(255) NOT NULL, gt7_id VARCHAR(20), image_url VARCHAR(500),
 *   created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 *   updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, CONSTRAINT uq_car UNIQUE (manufacturer, name));
 * </pre>
 *
 * <p>JSON shape (from CarMixIn — Phase 73): {@code {"id":"<uuid>","manufacturer":"...",
 * "name":"...","gt7Id":"...","imageUrl":"...","createdAt":"<iso>","updatedAt":"<iso>"}}.
 *
 * <p>Auditing bypass: written via {@link JdbcTemplate#batchUpdate} so
 * {@link org.ctc.domain.model.BaseEntity}'s {@code AuditingEntityListener}
 * does NOT overwrite {@code createdAt}/{@code updatedAt}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CarRestorer implements EntityRestorer {

    private static final String INSERT_SQL =
            "INSERT INTO cars (id, manufacturer, name, gt7_id, image_url, created_at, updated_at) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?)";

    @Override
    public String tableName() { return "cars"; }

    @Override
    public void restore(List<JsonNode> rows, JdbcTemplate jdbcTemplate) {
        jdbcTemplate.batchUpdate(INSERT_SQL, rows, /* batchSize = */ 500,
                (ps, row) -> {
                    ps.setObject(1, UUID.fromString(row.get("id").asText()));        // RESEARCH §5 — native UUID
                    ps.setString(2, row.get("manufacturer").asText());
                    ps.setString(3, row.get("name").asText());
                    ps.setString(4, nullableString(row, "gt7Id"));
                    ps.setString(5, nullableString(row, "imageUrl"));
                    ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.parse(row.get("createdAt").asText())));
                    ps.setTimestamp(7, Timestamp.valueOf(LocalDateTime.parse(row.get("updatedAt").asText())));
                });
        log.debug("CarRestorer: restored {} rows", rows.size());
    }

    private static String nullableString(JsonNode row, String field) {
        JsonNode n = row.get(field);
        return (n == null || n.isNull()) ? null : n.asText();
    }
}
```

**Pattern to copy from `CarMixIn`-family for each of the 24 restorers:**
1. Javadoc header naming the V1/V3/V7 schema column block the INSERT targets (the SQL must mirror DDL exactly).
2. `INSERT INTO <table> (col1, col2, ..., created_at, updated_at) VALUES (?, ?, ..., ?, ?)` — hard-coded, NO concatenation (T-IMPORT-SQLI defense per Validation matrix).
3. `tableName()` returns the snake_case `BackupSchema.getExportOrder().get(i).tableName()` value.
4. `restore(rows, jdbcTemplate)` calls `jdbcTemplate.batchUpdate(SQL, rows, 500, lambda)` — `ParameterizedPreparedStatementSetter` flavor per RESEARCH §1.
5. JSON field names follow the camelCase contract emitted by the corresponding MixIn (e.g., `createdAt` not `created_at` — Jackson default property naming since no `@JsonNaming` is configured on `BackupObjectMapperConfig`).
6. UUID via `ps.setObject(idx, UUID.fromString(...))` (RESEARCH §5 — drop CONTEXT D-08's BINARY(16) fallback).
7. Timestamps via `Timestamp.valueOf(LocalDateTime.parse(...))` for `created_at`/`updated_at` columns (V1 schema uses `TIMESTAMP`).
8. Instants (e.g., `DataImportAudit.executedAt`) and dates (`Season.startDate` etc.) use `Date.valueOf(LocalDate.parse(...))` / `Timestamp.from(Instant.parse(...))` as appropriate.
9. Enums (`SeasonFormat`, `AttachmentType`, `PhaseType`) bind via `ps.setString(idx, row.get("...").asText())` — V1 schema stores them as `VARCHAR`.
10. Nullable strings via the `nullableString(row, field)` private helper (copy into every restorer — per-entity per D-08, no shared `JsonNodeRowMapper` utility).

**`TeamRestorer` 2-pass special case** (D-06):
```java
// Pass 1: INSERT every team with parent_team_id = NULL
private static final String INSERT_SQL_PASS1 =
        "INSERT INTO teams (id, name, short_name, logo_url, primary_color, secondary_color, "
      + "accent_color, parent_team_id, created_at, updated_at) "
      + "VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?, ?)";

// Pass 2: UPDATE parent_team_id only for sub-teams (skip rows where source JSON parentTeam IS NULL)
private static final String UPDATE_SQL_PASS2 =
        "UPDATE teams SET parent_team_id = ? WHERE id = ?";

@Override
public void restore(List<JsonNode> rows, JdbcTemplate jdbcTemplate) {
    // Pass 1: every row, hard-coded NULL parent
    jdbcTemplate.batchUpdate(INSERT_SQL_PASS1, rows, 500, (ps, row) -> { /* … 9 columns … */ });
    // Pass 2: only rows where source JSON had a parent (TeamMixIn renders parentTeam as a UUID string)
    List<JsonNode> withParent = rows.stream()
            .filter(r -> r.get("parentTeam") != null && !r.get("parentTeam").isNull())
            .toList();
    jdbcTemplate.batchUpdate(UPDATE_SQL_PASS2, withParent, 500, (ps, row) -> {
        ps.setObject(1, UUID.fromString(row.get("parentTeam").asText()));   // FK target — guaranteed already inserted in Pass 1
        ps.setObject(2, UUID.fromString(row.get("id").asText()));           // self
    });
    log.info("TeamRestorer: pass-1 inserted {} teams, pass-2 updated {} parent FKs",
            rows.size(), withParent.size());
}
```

**Project conventions to apply (all 24 restorers):**
- `@Slf4j @Component @RequiredArgsConstructor` (Lombok stack — CONVENTIONS.md §"Lombok Usage").
- Package: `org.ctc.backup.restore.entity` (new sub-package per D-05).
- File naming: `<Entity>Restorer.java` (PascalCase — matches `<Entity>MixIn.java` parallel).
- `INSERT_SQL` is `private static final String` — no concatenation, no string-formatting (SQLI defense).
- Logging: `log.debug("<Entity>Restorer: restored {} rows", rows.size())` for unit-of-work tracing (CONVENTIONS.md §"Logging").
- No `@Transactional` on the restorer (the outer `BackupImportService.execute()` carries the boundary per D-14).

---

#### `org.ctc.backup.restore.RestoreFailureInjector` + `NoopRestoreFailureInjector` (D-13)

**Interface signature** (D-13):
```java
package org.ctc.backup.restore;

public interface RestoreFailureInjector {
    /**
     * Called by BackupImportService inside the per-table batch loop after every 50 rows.
     * Default no-op; test-only impl throws RestoreFailureSimulatedException when its
     * (targetTable, targetRowIndex) matches the call.
     */
    void maybeFailAt(String tableName, int rowIndex);
}
```

**No-op `@Primary` impl analog — `BackupObjectMapperConfig.defaultObjectMapper(...)` (`@Primary` precedent):**
```java
// File: src/main/java/org/ctc/backup/config/BackupObjectMapperConfig.java:60
@Bean
@Primary
public ObjectMapper defaultObjectMapper(Jackson2ObjectMapperBuilder builder) {
    // … standard mapper …
}
```

**`@Component` no-args bean analog — `BackupStagingCleanup` (`src/main/java/org/ctc/backup/service/BackupStagingCleanup.java:26-50`):**
```java
@Component
@Slf4j
class BackupStagingCleanup {
    private final Path stagingDir;
    BackupStagingCleanup(@Value("${app.backup.staging-dir}") Path stagingDir) {
        this.stagingDir = stagingDir;
    }
    // …
}
```

**`NoopRestoreFailureInjector` planner-target shape:**
```java
package org.ctc.backup.restore;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Default production no-op. {@code @Primary} so test-scope replacements override cleanly
 * without bean-name fiddling (CONVENTIONS — mirrors {@link
 * org.ctc.backup.config.BackupObjectMapperConfig#defaultObjectMapper}).
 */
@Component
@Primary
public class NoopRestoreFailureInjector implements RestoreFailureInjector {
    @Override
    public void maybeFailAt(String tableName, int rowIndex) {
        // intentionally empty — production path
    }
}
```

**Variation notes:**
- The interface lives in PRODUCTION main per D-13 ("Extension-point discipline" — `BackupImportService` calls `injector.maybeFailAt(...)` on the regular bean; test overrides via `@TestConfiguration` swap-in the failing impl).
- `@Primary` ensures test `@TestConfiguration` beans win without `@Qualifier` discipline (`@Primary` precedent: `BackupObjectMapperConfig.defaultObjectMapper`).

---

#### `org.ctc.backup.restore.UuidPacker` (**DROPPED** — RESEARCH §5 correction)

**Status:** DROPPED from Phase 75 scope.

**Rationale:** RESEARCH §5 verified `V1__initial_schema.sql:5,15,25,46,...` — every UUID PK is typed `UUID`, NOT `BINARY(16)`. Both H2 2.x (native `UUID`) and MariaDB 10.7+ (`UUID` since 10.7.0) accept `ps.setObject(idx, java.util.UUID)` directly via the JDBC 4.3 native-UUID-type pathway. CONTEXT D-08's `BINARY(16)` packing assumption is incorrect.

**Planner action:** delete from VALIDATION Wave 0 checklist (not implemented); update CONTEXT D-08 reference in PLAN.md to cite RESEARCH §5. No file created.

---

### Group 2 — `org.ctc.backup.audit` (service + serialization test)

#### `org.ctc.backup.audit.DataImportAuditService` (NEW — D-01)

**Class-shape analog:** `org.ctc.backup.service.BackupImportService` (Phase 74; same `@RequiredArgsConstructor @Slf4j @Service` Lombok stack, single explicit ctor when `@Value` is involved):
```java
// File: src/main/java/org/ctc/backup/service/BackupImportService.java:54-90
@Slf4j
@Service
@Transactional(readOnly = true)
public class BackupImportService {
    private final BackupArchiveService backupArchive;
    private final BackupSchema backupSchema;
    private final List<JpaRepository<?, ?>> allRepositories;
    private final Path stagingDir;
    // …
    public BackupImportService(
            BackupArchiveService backupArchive,
            BackupSchema backupSchema,
            List<JpaRepository<?, ?>> allRepositories,
            @Value("${app.backup.staging-dir}") String stagingDirRaw
    ) { /* … */ }
}
```

**Entity-write analog** — Phase 72 `DataImportAudit` (`src/main/java/org/ctc/backup/audit/DataImportAudit.java:42-83`):
```java
@Entity
@Table(name = "data_import_audit")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @ToString
public class DataImportAudit {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @NotNull @Column(name = "executed_at", nullable = false) private Instant executedAt;
    @NotBlank @Column(name = "executed_by", nullable = false) private String executedBy;
    @Column(name = "schema_version", nullable = false) private int schemaVersion;
    @NotBlank @Column(name = "table_counts_wiped", nullable = false, columnDefinition = "LONGTEXT") private String tableCountsWiped;
    @NotBlank @Column(name = "table_counts_restored", nullable = false, columnDefinition = "LONGTEXT") private String tableCountsRestored;
    @NotBlank @Column(name = "source_filename", nullable = false) private String sourceFilename;
    @Column(nullable = false) private boolean success;
}
```

**Repository analog** — `DataImportAuditRepository.java`:
```java
public interface DataImportAuditRepository extends JpaRepository<DataImportAudit, UUID> { }
```

**Planner-target service shape** (REQUIRES_NEW per D-01):
```java
package org.ctc.backup.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Phase 75 / Plan 03 — REQUIRES_NEW audit-row writer.
 *
 * <p>Called from BOTH the success path (post-batch-restore, before tx.commit()) and the
 * catch-block in {@link org.ctc.backup.service.BackupImportService#execute(UUID)}.
 * REQUIRES_NEW guarantees the audit row survives a wipe-rollback (D-01 / IMPORT-07).
 *
 * <p>Bypasses BaseEntity AuditingEntityListener — DataImportAudit deliberately does NOT
 * extend BaseEntity (Phase 72 PROJECT §"Audit log persistence"); executedAt is set here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataImportAuditService {

    private final DataImportAuditRepository repository;
    private final Environment environment;

    // Use the BACKUP-qualified ObjectMapper (FAIL_ON_UNKNOWN_PROPERTIES — Phase 72 contract)
    @Qualifier("backupObjectMapper")
    private final ObjectMapper backupObjectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID recordResult(
            boolean success,
            int schemaVersion,
            String sourceFilename,
            Map<String, Long> tableCountsWiped,
            Map<String, Long> tableCountsRestored) {
        UUID id = UUID.randomUUID();
        DataImportAudit audit = DataImportAudit.builder()
                .id(id)
                .executedAt(Instant.now())
                .executedBy(resolveExecutedBy())
                .schemaVersion(schemaVersion)
                .sourceFilename(sourceFilename)
                .tableCountsWiped(toJson(tableCountsWiped))
                .tableCountsRestored(toJson(tableCountsRestored))
                .success(success)
                .build();
        DataImportAudit saved = repository.save(audit);
        log.info("DataImportAudit recorded: id={}, success={}, schemaVersion={}, sourceFilename={}",
                saved.getId(), success, schemaVersion, sourceFilename);
        return saved.getId();
    }

    private String resolveExecutedBy() {
        // CONTEXT D-02: SecurityContext name on prod/docker; "dev" literal on dev/local
        if (environment.matchesProfiles("prod", "docker")) {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            return auth != null ? auth.getName() : "anonymous";
        }
        return "dev";
    }

    private String toJson(Map<String, Long> map) {
        try {
            return backupObjectMapper.writeValueAsString(map == null ? Map.of() : map);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize tableCounts map; falling back to '{}'", e.getMessage());
            return "{}";
        }
    }
}
```

**Variation notes:**
- Mirrors `BackupImportService` Lombok stack (`@Slf4j @Service @RequiredArgsConstructor`).
- `@Transactional(propagation = REQUIRES_NEW)` is the WHOLE point of the class (D-01).
- `@Qualifier("backupObjectMapper")` reuses the Phase-72-isolated ObjectMapper (PROJECT §"ObjectMapper isolation") — same idiom as `BackupArchiveService.java:105`.
- `DataImportAudit.builder()` is available via Lombok `@Builder` on the entity (already shipped).
- `executedAt` is set EXPLICITLY here (NOT via `AuditingEntityListener` — entity deliberately bypasses `BaseEntity` per Phase 72 PROJECT).
- Profile-conditional `executedBy` mirrors v1.8 audit pattern (per CONTEXT D-02).

**Project conventions to apply:**
- Package: `org.ctc.backup.audit` (co-located with `DataImportAudit` + `DataImportAuditRepository` per D-01).
- No `@Transactional(readOnly = true)` at class level — the only method writes; REQUIRES_NEW is method-level only.
- `log.info()` for state changes per CONVENTIONS.md §"Logging".

---

#### `org.ctc.backup.audit.DataImportAuditSerializationTest` (NEW — round-trip)

**Analog:** `org.ctc.backup.schema.BackupManifestSerializationTest` (Phase 72 — same `backupObjectMapper` round-trip pattern; checks LONGTEXT-as-JSON shape).

**Pattern to copy:** load `backupObjectMapper` via Spring `@SpringBootTest`, write a `Map<String, Long>` via `writeValueAsString`, read it back via `readValue(String, Map.class)`, assert key/value parity.

---

### Group 3 — `org.ctc.backup.exception` (two new exceptions)

**Analog:** `org.ctc.backup.exception.BackupArchiveException` (Phase 74 — same `RuntimeException` base, same single-field-no-Lombok-no-Spring discipline per its own javadoc).

**`UploadsRestoreException` (D-09):**
```java
package org.ctc.backup.exception;

/**
 * Thrown by Step-2 of the post-commit uploads-move triple (BackupImportPostCommitListener)
 * when {@code Files.move(stagedUploadsDir, uploads, ATOMIC_MOVE)} fails.
 *
 * <p>The catch-block attempts a best-effort revert of Step-1 ({@code Files.move(uploads-old,
 * uploads, ATOMIC_MOVE)}) before this exception propagates, so the operator sees the
 * D-15#3 soft-fail flash. Structural template mirrors {@link BackupArchiveException}.
 */
public class UploadsRestoreException extends RuntimeException {
    public UploadsRestoreException(String message) { super(message); }
    public UploadsRestoreException(String message, Throwable cause) { super(message, cause); }
}
```

**`RestoreFailureSimulatedException` (D-13):**
```java
package org.ctc.backup.exception;

/**
 * Test-only exception thrown by FailAtTableInjector (used by BackupImportRollbackIT) when
 * the configured (targetTable, targetRowIndex) is reached. Lives in main package per D-13
 * extension-point discipline — production NoopRestoreFailureInjector never throws this.
 */
public class RestoreFailureSimulatedException extends RuntimeException {
    public RestoreFailureSimulatedException(String message) { super(message); }
}
```

**Concrete excerpt from `BackupArchiveException` (`src/main/java/org/ctc/backup/exception/BackupArchiveException.java:14-117` — see Reasons enum + dual ctor):**
```java
public class BackupArchiveException extends RuntimeException {
    public enum Reason { /* …8 values… */ }
    private final Reason reason;
    public BackupArchiveException(Reason reason, String message)              { super(message);        this.reason = reason; }
    public BackupArchiveException(Reason reason, String message, Throwable c) { super(message, c);     this.reason = reason; }
    public Reason reason() { return reason; }
}
```

**Variation notes:**
- Neither new exception carries a `Reason` enum — both are single-purpose (one path, one trigger), so plain `RuntimeException` suffices. This is consistent with `org.ctc.domain.exception.BusinessRuleException` which also has no enum.
- No Lombok, no Spring annotations (per `BackupArchiveException` javadoc: "Structural template mirrors `BusinessRuleException` (single-field, no Lombok, no Spring annotations)").

---

### Group 4 — `org.ctc.backup.service` (extensions + new listener)

#### `BackupImportService.execute(UUID stagingId)` extension (D-14)

**Exact same-class extension anchor:** `BackupImportService.java:155-204` (`stage`), `BackupImportService.java:219-233` (`reparse`), `BackupImportService.java:245-255` (`deleteStagingFile`).

**Concrete excerpt — Phase 74 `reparse()` (`BackupImportService.java:219-233`) — shape Phase 75 mirrors:**
```java
public BackupImportPreview reparse(UUID stagingId) throws BackupArchiveException, IOException {
    Path staged = stagingDir.resolve("upload-" + stagingId + ".zip");
    Path metaFile = stagingDir.resolve("upload-" + stagingId + ".zip.meta");
    if (!Files.exists(staged)) {
        throw new BackupArchiveException(Reason.MANIFEST_MISSING,
                "Staging file not found for id=" + stagingId);
    }
    String originalFilename = Files.exists(metaFile)
            ? Files.readString(metaFile, java.nio.charset.StandardCharsets.UTF_8)
            : staged.getFileName().toString();
    return buildPreview(stagingId, staged, originalFilename, Files.size(staged));
}
```

**Planner-target `execute(UUID stagingId)` shape:**
```java
// New imports required:
//   import jakarta.persistence.EntityManager;
//   import jakarta.persistence.PersistenceContext;
//   import org.springframework.context.ApplicationEventPublisher;
//   import org.springframework.jdbc.core.JdbcTemplate;
//   import org.springframework.transaction.annotation.Isolation;
//   import org.springframework.transaction.annotation.Propagation;
//   import org.ctc.backup.audit.DataImportAuditService;
//   import org.ctc.backup.restore.EntityRestorer;
//   import org.ctc.backup.restore.RestoreFailureInjector;

// New @Autowired fields added in the constructor (mirror Phase 74's explicit ctor — D-14):
//   private final JdbcTemplate jdbcTemplate;
//   private final EntityManager entityManager;        // injected via @PersistenceContext at field level
//   private final Map<String, EntityRestorer> restorersByTableName;   // Spring-discovered bean map
//   private final RestoreFailureInjector failureInjector;
//   private final DataImportAuditService auditService;
//   private final ApplicationEventPublisher eventPublisher;
//   private final Path importBackupsDir;              // @Value("${app.backup.import-backups-dir}")

@Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
public UUID execute(UUID stagingId) throws BackupArchiveException, IOException {
    log.info("Backup import execute started: stagingId={}", stagingId);

    Path staged = stagingDir.resolve("upload-" + stagingId + ".zip");
    Path metaFile = stagingDir.resolve("upload-" + stagingId + ".zip.meta");
    if (!Files.exists(staged)) {
        throw new BackupArchiveException(Reason.MANIFEST_MISSING,
                "Staging file not found for id=" + stagingId);
    }
    String sourceFilename = Files.exists(metaFile)
            ? Files.readString(metaFile, java.nio.charset.StandardCharsets.UTF_8)
            : staged.getFileName().toString();

    BackupManifest manifest = backupArchive.readManifest(staged);
    Map<String, Long> tableCountsWiped = new LinkedHashMap<>();
    Map<String, Long> tableCountsRestored = new LinkedHashMap<>();

    // <ts> directory for atomic move-triple per D-11
    String ts = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString().replace(":", "-");
    Path importBackupDir = importBackupsDir.resolve(ts);
    Path uploadsNew = importBackupDir.resolve("uploads-new");

    try {
        // (1) Extract staged uploads BEFORE the DB wipe (D-12) — reuses BackupArchiveService
        Files.createDirectories(uploadsNew);
        backupArchive.extractUploadsTo(staged, uploadsNew);

        // (2) Wipe — Team.parentTeam = NULL pre-step, FK-reverse DELETE, em.flush() + em.clear()
        wipeAllTables(tableCountsWiped);

        // (3) Restore — JdbcTemplate.batchUpdate via 24 EntityRestorers
        restoreAll(staged, tableCountsRestored);

        // (4) Publish AFTER_COMMIT event with move-triple payload
        eventPublisher.publishEvent(new BackupImportSucceededEvent(
                stagingId, importBackupDir, uploadsNew, manifest.schemaVersion(),
                sourceFilename, tableCountsWiped, tableCountsRestored));

        log.info("Backup import execute committed: stagingId={}, restored {} rows",
                stagingId, tableCountsRestored.values().stream().mapToLong(Long::longValue).sum());
        return null;  // audit UUID is returned by the AFTER_COMMIT listener; controller looks it up via flash
    } catch (RuntimeException | IOException ex) {
        // Audit BEFORE the outer @Transactional rolls back. REQUIRES_NEW survives the rollback.
        UUID auditId = auditService.recordResult(false, manifest.schemaVersion(), sourceFilename,
                tableCountsWiped, tableCountsRestored);
        log.error("Backup import failed: stagingId={}, auditId={}", stagingId, auditId, ex);
        // Clean up uploadsNew on rollback (CONTEXT Claude's Discretion default)
        deleteRecursivelyQuietly(uploadsNew);
        throw ex instanceof RuntimeException re ? re : new RuntimeException(ex);
    }
}

// Package-private helpers (D-14):
void wipeAllTables(Map<String, Long> tableCountsWipedOut) { /* see Group 4 internals below */ }
void restoreAll(Path stagedZip, Map<String, Long> tableCountsRestoredOut) { /* see below */ }
```

**`wipeAllTables` internal pattern** (CONTEXT IMPORT-05 a-c + RESEARCH §3 §4 §11):
```java
void wipeAllTables(Map<String, Long> out) {
    // (a) Team.parentTeam = NULL pre-step (D-06 + ROADMAP-Goal lock)
    int teamsDecoupled = jdbcTemplate.update("UPDATE teams SET parent_team_id = NULL");
    log.info("Decoupled {} parent_team_id FKs pre-wipe", teamsDecoupled);

    // (b) FK-reverse DELETE: Lists.reverse(BackupSchema.getExportOrder())
    List<EntityRef> wipeOrder = new ArrayList<>(backupSchema.getExportOrder());
    Collections.reverse(wipeOrder);
    for (EntityRef ref : wipeOrder) {
        int rowCount = entityManager
                .createNativeQuery("DELETE FROM " + ref.tableName())
                .executeUpdate();
        out.put(ref.tableName(), (long) rowCount);
        log.debug("Wiped {}: {} rows", ref.tableName(), rowCount);
    }

    // (c) Drop L1 cache between wipe and restore
    entityManager.flush();
    entityManager.clear();
}
```

**`restoreAll` internal pattern** (CONTEXT IMPORT-05 d + D-04..D-08 + D-13):
```java
void restoreAll(Path stagedZip, Map<String, Long> out) throws IOException {
    for (EntityRef ref : backupSchema.getExportOrder()) {
        EntityRestorer restorer = restorersByTableName.get(ref.tableName());
        if (restorer == null) {
            throw new IllegalStateException("No EntityRestorer wired for table=" + ref.tableName());
        }
        // Stream the data/<entity>.json array via Jackson JsonParser (mirror countDataEntries
        // streaming idiom — BackupArchiveService.java:306-370). Buffer rows into 500-batch
        // List<JsonNode> chunks; call failureInjector.maybeFailAt(table, rowIdx) every 50 rows.
        List<JsonNode> allRows = streamReadEntityJson(stagedZip, ref);
        for (int i = 0; i < allRows.size(); i += 50) {
            failureInjector.maybeFailAt(ref.tableName(), i);
        }
        restorer.restore(allRows, jdbcTemplate);
        out.put(ref.tableName(), (long) allRows.size());
    }
}
```

**Project conventions to apply:**
- The method goes into the EXISTING `BackupImportService` class (D-14 — no new service class).
- Class-level `@Transactional(readOnly = true)` (line 56) STAYS — the new `execute` method overrides with `@Transactional(propagation = REQUIRED, isolation = READ_COMMITTED)` at method level.
- The Phase 74 explicit constructor (lines 79-90) MUST be expanded to include the new dependencies (`JdbcTemplate`, `EntityManager` via `@PersistenceContext`, `Map<String, EntityRestorer>` discovered map, `RestoreFailureInjector` `@Primary` bean, `DataImportAuditService`, `ApplicationEventPublisher`, `@Value("${app.backup.import-backups-dir}") String importBackupsDirRaw`).

---

#### `BackupArchiveService.extractUploadsTo(Path zip, Path destDir)` extension (D-12)

**Exact same-class extension anchor:** `BackupArchiveService.java:384-429` (`countUploadFiles` — the streaming "drain via discard buffer" loop is the closest existing pattern; Phase 75 replaces the discard with `Files.copy(limited, destDir.resolve(rel))`).

**Concrete excerpt — `countUploadFiles(Path)` (`BackupArchiveService.java:384-429`):**
```java
public int countUploadFiles(Path zipPath) throws BackupArchiveException {
    Path stagingRoot = resolveStagingRoot(zipPath);
    long[] inflatedAcc = new long[]{0L};
    int entryCount = 0;
    int uploadCount = 0;

    try (ZipInputStream zis = openHardened(zipPath)) {
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            String name = entry.getName();
            entryCount++;

            if (name.startsWith("uploads/") && !entry.isDirectory()) {
                final String entryName = name;
                try (LimitedInputStream limited = new LimitedInputStream(
                        nonClosingView(zis), MAX_ENTRY_BYTES,
                        finalBytes -> {
                            inflatedAcc[0] += finalBytes;
                            if (finalBytes >= MAX_ENTRY_BYTES) {
                                log.warn("Backup ZIP entry exceeds limit: name={}, limit={} bytes",
                                        entryName, MAX_ENTRY_BYTES);
                            }
                        })) {
                    byte[] buf = new byte[8192];
                    while (limited.read(buf) != -1) { /* discard */ }
                }
                uploadCount++;
            }
            assertEntrySafe(entry, stagingRoot, entryCount, inflatedAcc[0]);
        }
    } catch (BackupArchiveException ex) {
        throw ex;
    } catch (IOException ex) {
        throw new BackupArchiveException(Reason.MANIFEST_INVALID, "ZIP read failure", ex);
    }
    log.info("Backup upload entries counted: count={}", uploadCount);
    return uploadCount;
}
```

**Planner-target `extractUploadsTo` shape** — replace the discard-buffer with `Files.copy`:
```java
public int extractUploadsTo(Path zipPath, Path destDir) throws BackupArchiveException {
    Path destRoot = destDir.toAbsolutePath().normalize();
    long[] inflatedAcc = new long[]{0L};
    int entryCount = 0;
    int extracted = 0;

    try (ZipInputStream zis = openHardened(zipPath)) {
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            String name = entry.getName();
            entryCount++;

            if (name.startsWith("uploads/") && !entry.isDirectory()) {
                // PathTraversalGuard — Phase 74 D-11 reuse (note: traversal-root is destRoot,
                // NOT staging root, since extraction writes to a different tree)
                String relName = name.substring("uploads/".length());
                PathTraversalGuard.assertWithin(destRoot, relName);
                Path target = destRoot.resolve(relName);
                Files.createDirectories(target.getParent());

                final String entryName = name;
                try (LimitedInputStream limited = new LimitedInputStream(
                        nonClosingView(zis), MAX_ENTRY_BYTES,
                        finalBytes -> { inflatedAcc[0] += finalBytes; /* … log at limit … */ })) {
                    Files.copy(limited, target);   // <— replaces the discard-buffer drain
                }
                extracted++;
            }
            assertEntrySafe(entry, destRoot, entryCount, inflatedAcc[0]);
        }
    } catch (BackupArchiveException ex) {
        throw ex;
    } catch (IOException ex) {
        throw new BackupArchiveException(Reason.MANIFEST_INVALID, "ZIP read failure", ex);
    }
    log.info("Backup upload entries extracted: count={}, destDir={}", extracted, destRoot);
    return extracted;
}
```

**Variation notes:**
- `Path stagingRoot` of `countUploadFiles` becomes `Path destRoot` here — traversal root tightens to the per-import extraction subdirectory (CONTEXT canonical_refs §"Phase 75 will add extraction; at that point the traversal root must be tightened" — `BackupArchiveService.java:90`).
- `assertEntrySafe(...)` is reused without modification (same bomb / count / traversal checks).
- `nonClosingView(zis)` reuse — same pattern as `countUploadFiles` (lines 401 / 466-474).
- Method visibility: `public` (called by `BackupImportService.execute`).

---

#### `BackupImportPostCommitListener` (NEW — D-14)

**Closest event-listener analog:** `org.ctc.backup.service.BackupStagingCleanup` (`@EventListener(ApplicationReadyEvent)` precedent — `src/main/java/org/ctc/backup/service/BackupStagingCleanup.java:36`):
```java
@EventListener(ApplicationReadyEvent.class)
void sweepStagingDir() {
    if (!Files.isDirectory(stagingDir)) { return; }
    try (Stream<Path> stream = Files.list(stagingDir)) { /* … */ }
}
```

**Variation:** Phase 75 introduces the FIRST `@TransactionalEventListener(phase=AFTER_COMMIT)` in the codebase. RESEARCH §2 confirms this is the Spring 6.x recommended idiom (over `TransactionSynchronizationManager.registerSynchronization`).

**Planner-target shape:**
```java
package org.ctc.backup.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.backup.audit.DataImportAuditService;
import org.ctc.backup.exception.UploadsRestoreException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Phase 75 / Plan 07 — post-commit listener for the upload-tree atomic move-triple (D-09).
 *
 * <p>Fires AFTER {@link org.ctc.backup.service.BackupImportService#execute(java.util.UUID)}
 * commits successfully. The three steps (D-09) are atomic-moves on the file system,
 * which cannot be enrolled in the JPA transaction — hence the AFTER_COMMIT phase.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class BackupImportPostCommitListener {

    private final BackupImportService importService;         // for deleteStagingFile(UUID)
    private final DataImportAuditService auditService;       // for REQUIRES_NEW success row
    private final Path uploadsDir;                            // @Value("${app.upload-dir}")

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSuccess(BackupImportSucceededEvent event) {
        Path uploadsOld = event.importBackupDir().resolve("uploads-old");
        Path uploadsNew = event.uploadsNewDir();

        try {
            // Step 1: rename current uploads → uploads-old (D-09 step 1)
            log.info("Step 1: moving uploads → {}", uploadsOld);
            Files.createDirectories(event.importBackupDir());
            Files.move(uploadsDir, uploadsOld, StandardCopyOption.ATOMIC_MOVE);
            log.info("Step 1 complete: {} renamed to {}", uploadsDir, uploadsOld);

            try {
                // Step 2: rename uploads-new → uploads (D-09 step 2)
                log.info("Step 2: moving {} → uploads", uploadsNew);
                Files.move(uploadsNew, uploadsDir, StandardCopyOption.ATOMIC_MOVE);
                log.info("Step 2 complete: {} renamed to {}", uploadsNew, uploadsDir);
            } catch (IOException step2Failure) {
                // Best-effort revert of step 1
                log.error("Step 2 FAILED — attempting revert of step 1", step2Failure);
                try {
                    Files.move(uploadsOld, uploadsDir, StandardCopyOption.ATOMIC_MOVE);
                    log.warn("Step 1 revert succeeded — uploads tree returned to pre-import state");
                } catch (IOException revertFailure) {
                    log.error("Step 1 REVERT ALSO FAILED — manual recovery required from {}",
                            uploadsOld, revertFailure);
                }
                throw new UploadsRestoreException("Step 2 atomic move failed", step2Failure);
            }

            // Step 3: success audit row (REQUIRES_NEW)
            auditService.recordResult(true, event.schemaVersion(), event.sourceFilename(),
                    event.tableCountsWiped(), event.tableCountsRestored());

            // Step 4: clean up staged ZIP + .meta
            importService.deleteStagingFile(event.stagingId());
        } catch (IOException e) {
            // Step 1 failure — DB has committed; this is the soft-fail path (D-15#3)
            log.error("Step 1 atomic move failed — DB committed but uploads not swapped", e);
            throw new UploadsRestoreException("Step 1 atomic move failed", e);
        }
    }
}
```

**Variation notes:**
- Package: `org.ctc.backup.service` (mirrors `BackupStagingCleanup` placement).
- Lombok: `@Slf4j @Component @RequiredArgsConstructor` (no explicit ctor needed because `@Value` is on a separate `Path uploadsDir` field — see analog in `BackupStagingCleanup:32` which uses explicit ctor because of `@Value`; planner may need explicit ctor here too).
- Event payload `BackupImportSucceededEvent` is a Java record co-located in the same package — planner adds it as part of this Plan.

---

### Group 5 — `org.ctc.backup` (controller upgrade — D-15)

#### `BackupController.importExecute(...)` modification (D-15)

**Exact same-method anchor:** `BackupController.java:195-229` (Phase 74 stub).

**Concrete excerpt — Phase 74 stub** (`BackupController.java:195-229`):
```java
@PostMapping("/import-execute")
public String importExecute(
        @Valid @ModelAttribute("backupImportConfirmForm") BackupImportConfirmForm form,
        BindingResult bindingResult, Model model, RedirectAttributes ra) {
    if (bindingResult.hasErrors()) {
        try {
            BackupImportPreview preview = backupImportService.reparse(form.getStagingId());
            model.addAttribute("preview", preview);
            return "admin/backup-confirm";
        } catch (BackupArchiveException ex) {
            ra.addFlashAttribute("errorMessage", mapReason(ex));
            return "redirect:/admin/backup";
        } catch (IOException ex) { /* … */ }
    }
    try {
        backupImportService.reparse(form.getStagingId());  // D-09 defense-in-depth re-validation
    } catch (BackupArchiveException ex) { /* … */ }
      catch (IOException ex)            { /* … */ }
    ra.addFlashAttribute("successMessage",
            "Validation succeeded. Import execution will be enabled in Phase 75.");   // <— STUB
    return "redirect:/admin/backup";
}
```

**Planner-target diff shape** — keep the BindingResult-error path + the D-09 reparse defense-in-depth; REPLACE the success-stub Flash with a real `execute(...)` call:
```java
@PostMapping("/import-execute")
public String importExecute(
        @Valid @ModelAttribute("backupImportConfirmForm") BackupImportConfirmForm form,
        BindingResult bindingResult, Model model, RedirectAttributes ra) {
    // BindingResult-error path: UNCHANGED (re-render confirm page with field errors)
    if (bindingResult.hasErrors()) {
        try { /* same Phase 74 re-render-with-preview block */ }
        catch (BackupArchiveException | IOException ex) { /* same Phase 74 redirect-with-flash */ }
    }
    try {
        backupImportService.reparse(form.getStagingId());  // D-09 defense-in-depth re-validation — UNCHANGED
        // NEW Phase 75: invoke the real execute
        backupImportService.execute(form.getStagingId());
        ra.addFlashAttribute("successMessage",
                /* D-15#1 */ "Import completed. " + /* lookup restored/entities counts */ "...");
    } catch (UploadsRestoreException ex) {
        ra.addFlashAttribute("errorMessage",
                /* D-15#3 */ "Import database succeeded but uploads restore failed and was reverted. "
                + "See logs. Audit-id: " + /* auditId lookup */ ".");
    } catch (BackupArchiveException ex) {
        ra.addFlashAttribute("errorMessage", mapReason(ex));
    } catch (Exception ex) {
        log.error("Import failed for stagingId={}", form.getStagingId(), ex);
        ra.addFlashAttribute("errorMessage",
                /* D-15#2 */ "Import failed and was rolled back — see logs. Audit-id: "
                + /* auditId lookup */ ".");
    }
    return "redirect:/admin/backup";
}
```

**Variation notes:**
- The stub-flash line at `BackupController.java:225-227` is DELETED.
- Three new exception branches added: `UploadsRestoreException` (D-15#3 soft-fail), `BackupArchiveException` (route via existing `mapReason()`), generic `Exception` (D-15#2 fallback).
- `auditId` is captured via either (a) the `execute(...)` method's return value or (b) a `ThreadLocal` populated by `BackupImportPostCommitListener` (planner picks; default recommendation: change `execute(...)` to return `UUID auditId` and have the listener publish a second event that the controller catches; alternatively, the catch block invokes `DataImportAuditService.recordResult(...)` directly).
- The endpoint URL `/admin/backup/import-execute` is UNCHANGED per D-17 — Phase 74's `admin/backup-confirm.html` continues to POST here.

**Project conventions to apply:**
- Controller stays thin (CLAUDE.md §"Architectural Principles") — no business logic added; only flash-message branching.
- `successMessage` / `errorMessage` flash keys preserved (CONVENTIONS.md §"flash attributes" + CSS-class binding in `admin/backup.html`).
- `mapReason(ex)` private helper preserved.

---

### Group 6 — `src/main/resources` (yml diffs)

**Diff target — `application.yml`:**
```yaml
app:
  upload-dir: data/dev/uploads
  version: @project.version@
  backup:
    staging-dir: data/${spring.profiles.active:dev}/backup-staging
    import-backups-dir: data/${spring.profiles.active:dev}/.import-backups   # <— NEW (D-12)
```

**Diff target — `application-local.yml`** (RESEARCH §10):
```yaml
spring:
  datasource:
    url: jdbc:mariadb://localhost:3306/ctcdb?rewriteBatchedStatements=true   # <— append ?rewriteBatchedStatements=true
    driver-class-name: org.mariadb.jdbc.Driver
    username: ctc
    password: ctc
```

**Diff target — `application-docker.yml`** (RESEARCH §10):
```yaml
spring:
  datasource:
    url: jdbc:mariadb://db:3306/ctcdb?rewriteBatchedStatements=true          # <— append ?rewriteBatchedStatements=true
    driver-class-name: org.mariadb.jdbc.Driver
    username: ctc
    password: ctc
```

**Diff target — `application-prod.yml`** (RESEARCH §10 — env-var-driven URL):
```yaml
spring:
  datasource:
    url: ${DATABASE_URL}                                                      # <— operator MUST include ?rewriteBatchedStatements=true (document in README + .env.example)
    driver-class-name: org.mariadb.jdbc.Driver
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
```

**Verification — pattern reuse:** the existing `application-local.yml:9` JDBC URL `jdbc:mariadb://localhost:3306/ctcdb` is the verbatim string to amend. No new properties beyond `app.backup.import-backups-dir` are needed.

---

### Group 7 — `src/test/java/org/ctc/backup/restore/entity` (24 RestorerTests + TeamRestorerIT)

#### 24× `<Entity>RestorerTest` (Surefire unit tests)

**Master analog — `TeamMixInTest.java` (`src/test/java/org/ctc/backup/serialization/TeamMixInTest.java:28-77`):**
```java
class TeamMixInTest {
    private ObjectMapper mapper;
    @BeforeEach void setUp() {
        mapper = new ObjectMapper()
                .registerModule(new BackupSerializationModule())
                .registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }
    @Test
    void givenTeamWithParentAndSubTeams_whenSerialize_thenParentIsIdRefAndSubTeamsAbsent() throws Exception {
        // given — manually construct a Team graph
        Team parent = new Team("Alpha Racing", "T-ALF");
        parent.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        // … child team, populate back-references …

        // when
        String json = mapper.writeValueAsString(child);
        JsonNode node = mapper.readTree(json);

        // then — assert per-property shape
        assertThat(node.has("id")).isTrue();
        assertThat(node.get("parentTeam").isTextual()).isTrue();
        // …
    }
}
```

**Planner-target shape (per-entity unit test):** for each `<Entity>Restorer`, assert that
1. `tableName()` returns the expected snake_case string,
2. `restore(rows, jdbcTemplate)` calls `jdbcTemplate.batchUpdate(insertSql, rows, 500, lambda)` with the correct SQL (use `Mockito.spy(jdbcTemplate)` + `ArgumentCaptor<String>`).
3. The `ParameterizedPreparedStatementSetter` lambda correctly maps a single sample `JsonNode` to `PreparedStatement` calls (use `Mockito.mock(PreparedStatement.class)` + verify `setObject(1, expectedUUID)`, `setString(2, expectedValue)`, etc.).

**Skeleton:**
```java
package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CarRestorerTest {
    private CarRestorer restorer;
    private JdbcTemplate jdbcTemplate;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        restorer = new CarRestorer();
        jdbcTemplate = mock(JdbcTemplate.class);
        mapper = new ObjectMapper();
    }

    @Test
    void whenTableName_thenReturnsCars() {
        assertThat(restorer.tableName()).isEqualTo("cars");
    }

    @Test
    void givenCarRow_whenRestore_thenBatchUpdateIsCalledWithExpectedSql() throws Exception {
        // given
        JsonNode row = mapper.readTree("""
                { "id":"11111111-1111-1111-1111-111111111111", "manufacturer":"Subaru",
                  "name":"WRX","gt7Id":"WRX-2022","imageUrl":null,
                  "createdAt":"2026-05-14T10:00:00", "updatedAt":"2026-05-14T10:00:00" }
                """);

        // when
        restorer.restore(List.of(row), jdbcTemplate);

        // then
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).batchUpdate(sqlCaptor.capture(),
                eq(List.of(row)), eq(500), any(ParameterizedPreparedStatementSetter.class));
        assertThat(sqlCaptor.getValue()).contains("INSERT INTO cars", "VALUES (?, ?, ?, ?, ?, ?, ?)");
    }

    @Test
    void givenCarRow_whenSetterApplied_thenPreparedStatementBoundCorrectly() throws Exception {
        // given
        JsonNode row = mapper.readTree("""
                { "id":"11111111-1111-1111-1111-111111111111", "manufacturer":"Subaru",
                  "name":"WRX","gt7Id":"WRX-2022","imageUrl":null,
                  "createdAt":"2026-05-14T10:00:00", "updatedAt":"2026-05-14T10:00:00" }
                """);
        PreparedStatement ps = mock(PreparedStatement.class);

        // when — invoke the setter via the captured ParameterizedPreparedStatementSetter
        ArgumentCaptor<ParameterizedPreparedStatementSetter<JsonNode>> setterCaptor =
                ArgumentCaptor.forClass(ParameterizedPreparedStatementSetter.class);
        restorer.restore(List.of(row), jdbcTemplate);
        verify(jdbcTemplate).batchUpdate(any(String.class), any(List.class), eq(500), setterCaptor.capture());
        setterCaptor.getValue().setValues(ps, row);

        // then — PreparedStatement bound with the expected JDBC-typed values
        verify(ps).setObject(1, UUID.fromString("11111111-1111-1111-1111-111111111111"));
        verify(ps).setString(2, "Subaru");
        verify(ps).setString(3, "WRX");
        verify(ps).setString(4, "WRX-2022");
        verify(ps).setString(5, null);
        // … timestamps …
    }
}
```

**Project conventions to apply:**
- Given-When-Then method names (CLAUDE.md §"Test Naming").
- AssertJ + Mockito (TESTING.md §"Mocking" + §"Common Patterns Summary").
- Test package: `org.ctc.backup.restore.entity` (mirrors main package).
- No Spring context (`@ExtendWith(MockitoExtension.class)` is unnecessary if all mocks are constructor-local — TESTING.md §"Unit Test Pattern").

---

#### `TeamRestorerIT` (Failsafe IT, H2)

**Master analog — `BackupImportServiceIT.java:45-90` (same package, same `@SpringBootTest @ActiveProfiles("dev")` shape):**
```java
@SpringBootTest
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BackupImportServiceIT {
    @Autowired BackupImportService service;
    @Autowired BackupArchiveService archive;
    @Autowired BackupSchema backupSchema;
    @Value("${app.backup.staging-dir}") String stagingDirRaw;
    Path stagingDir;
    byte[] phase73ZipBytes;

    @BeforeAll
    void produceFixtureBytes() throws IOException { /* … */ }
    @BeforeEach
    void clearStagingDir() throws IOException { /* … */ }

    @Test
    void givenPhase73Export_whenStage_thenPreviewHasNonZeroEntityCounts_andAll24CardsPopulated() throws Exception {
        // … given/when/then …
    }
}
```

**Planner-target `TeamRestorerIT` shape:**
```java
@SpringBootTest
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TeamRestorerIT {
    @Autowired TeamRestorer restorer;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired TeamRepository teamRepository;
    @Autowired @Qualifier("backupObjectMapper") ObjectMapper backupObjectMapper;

    @Test
    void givenTeamsWithSelfFkParent_whenRestore_thenPass1NullsThenPass2FixesParent() throws Exception {
        // given — JsonNode array with a parent and a sub-team referencing it by UUID
        // when — restorer.restore(rows, jdbcTemplate)
        // then — teamRepository.findById(parentId).getSubTeams() contains sub-team
    }
}
```

---

### Group 8 — `src/test/java/org/ctc/backup/audit` (2 audit tests)

#### `DataImportAuditServiceTest` (REQUIRES_NEW verification)

**Closest analog:** no existing test verifies REQUIRES_NEW directly. The closest pattern is the `@SpringBootTest`-on-dev shape from `BackupImportServiceIT`; REQUIRES_NEW verification uses `@MockitoSpyBean` on `PlatformTransactionManager` to assert that a NEW transaction is begun when the method is invoked from within an existing transaction.

**Planner-target shape (RESEARCH §1):**
```java
@SpringBootTest
@ActiveProfiles("dev")
class DataImportAuditServiceTest {
    @Autowired DataImportAuditService service;
    @Autowired DataImportAuditRepository repo;
    @MockitoSpyBean PlatformTransactionManager txManager;

    @Test
    void givenOuterTx_whenRecordResult_thenStartsNewTxAndRowSurvivesOuterRollback() {
        TransactionTemplate outer = new TransactionTemplate(txManager);
        UUID auditId = outer.execute(status -> {
            UUID id = service.recordResult(false, 1, "test.zip", Map.of(), Map.of());
            status.setRollbackOnly();   // force outer rollback
            return id;
        });
        // then — audit row survives the outer rollback
        assertThat(repo.findById(auditId)).isPresent();
    }
}
```

#### `DataImportAuditSerializationTest` (LONGTEXT JSON round-trip)

**Analog:** `BackupManifestSerializationTest` — uses `backupObjectMapper` to round-trip a `BackupManifest` record; Phase 75 mirrors with `Map<String, Long>` for `tableCountsWiped` / `tableCountsRestored`.

---

### Group 9 — `src/test/java/org/ctc/backup/service` (4 ITs)

| File | Analog | Notes |
|------|--------|-------|
| `BackupImportExecuteIT` | `BackupImportServiceIT` (exact shape) | `@SpringBootTest @ActiveProfiles("dev")`; export → wipe-and-restore → assert per-entity row counts equal pre-export |
| `BackupImportRollbackIT` | `BackupImportServiceIT` + D-13 `@TestConfiguration` override of `RestoreFailureInjector` | Inject `FailAtTableInjector` bean targeting largest table at 50 % rowIndex; assert (a) outer @Transactional rolls back wipe, (b) REQUIRES_NEW audit row `success=false` is committed, (c) NO post-commit listener fired |
| `BackupImportPostCommitIT` | `BackupImportServiceIT` skeleton + `@TempDir` for uploads-old / uploads-new directories | Assert step-1 → step-2 → step-3 ordering, step-2 simulated failure triggers step-1 revert |
| `BackupArchiveExtractUploadsIT` | `BackupArchiveServiceReadIT` (closest existing sibling — same archive-read pattern) | Assert `PathTraversalGuard` rejects `../etc/passwd` entries, `LimitedInputStream` rejects oversized entries (per Phase 74 hardening reuse) |
| `BackupImportMariaDbSmokeIT` | `TemplateRenderingSmokeIT` (`@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("dev")`) + `mariadb-migration-smoke.yml` (v1.9 D-22 workflow precedent) | Uses `@ActiveProfiles("local")` so MariaDB is required; the existing CI workflow boots `mariadb:11` as a service container and runs the packaged JAR — Phase 75 IT is included in the workflow's test scope (planner verifies workflow YAML scope OR adds explicit `-Dit.test=BackupImportMariaDbSmokeIT` step) |

**Concrete excerpt — `TemplateRenderingSmokeIT.java:51-58`** (the `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles + @Sql fixture` shape that `BackupImportMariaDbSmokeIT` mirrors but with `local` profile):
```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
@Sql(scripts = "/sql/template-rendering-smoke-fixture.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class TemplateRenderingSmokeIT {
    // …
}
```

**`mariadb-migration-smoke.yml` integration pattern** (`/Users/jegr/Documents/github/ctc-manager/.github/workflows/mariadb-migration-smoke.yml`):
```yaml
services:
  mariadb:
    image: mariadb:11
    env:
      MARIADB_DATABASE: ctcdb
      MARIADB_USER: ctc
      MARIADB_PASSWORD: ctc
    ports:
      - 3306:3306
# … boots packaged JAR with --spring.profiles.active=local then polls /actuator/health …
```

**Planner-target `BackupImportMariaDbSmokeIT` shape:**
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")    // requires MariaDB on localhost:3306 — CI service container
class BackupImportMariaDbSmokeIT {
    @Autowired BackupArchiveService archive;
    @Autowired BackupImportService importService;
    @Autowired BackupSchema backupSchema;
    @Autowired List<JpaRepository<?, ?>> allRepos;

    @Test
    void givenSeasonTwentyTwentyThreeFixture_whenExportThenWipeThenImport_thenRowCountsByEntityAreIdentical()
            throws Exception {
        // given — capture per-entity row counts BEFORE the round-trip
        Map<String, Long> beforeCounts = captureCounts();

        // when — export → stage → execute
        Path exportZip = tempZip();
        try (OutputStream out = Files.newOutputStream(exportZip)) {
            archive.writeZip(out, Instant.now());
        }
        MockMultipartFile multipart = new MockMultipartFile(
                "file", "saison-2023.zip", "application/zip", Files.readAllBytes(exportZip));
        BackupImportPreview preview = importService.stage(multipart);
        importService.execute(preview.stagingId());

        // then — assert per-entity row counts equal pre-export counts
        Map<String, Long> afterCounts = captureCounts();
        assertThat(afterCounts).isEqualTo(beforeCounts);
    }
}
```

**Variation notes:**
- The CI MariaDB workflow boots the packaged JAR, not a `@SpringBootTest` — but CONTEXT D-16 explicitly directs that the IT "follows the same `@SpringBootTest(properties=...) + @ActiveProfiles("local")` shape". Planner reconciles by either (a) running the IT in a separate workflow that boots `mariadb:11` as a service container before running `./mvnw verify -Dit.test=BackupImportMariaDbSmokeIT`, or (b) adopting the v1.9 D-22 "packaged JAR + curl probe" shape if the `@SpringBootTest @ActiveProfiles("local")` cannot connect (clarified in PLAN.md).

---

### Group 10 — `src/test/java/org/ctc/e2e` (EXTEND existing file)

#### `BackupImportE2ETest` (EXTEND, do NOT create new)

**Existing file:** `src/test/java/org/ctc/e2e/BackupImportE2ETest.java` (Phase 74, already shipped).

**Concrete pattern to REMOVE — Phase 74's stub-success test** (`BackupImportE2ETest.java:138-141`):
```java
// then — land on /admin/backup with D-02#5 stub-success Flash
assertThat(page).hasURL(Pattern.compile(".*/admin/backup$"));
assertThat(page.locator(".alert.alert-success"))
        .containsText("Validation succeeded. Import execution will be enabled in Phase 75.");
```

**Concrete pattern to ADD — Phase 75's real-success-flash test** (D-15#1):
```java
@Test
void givenPhase73ExportZip_whenAdminExecutesImport_thenLandsOnBackupWithRealSuccessFlash(
        @TempDir Path tempDir) throws Exception {
    // given — same fixture setup as Phase 74's happy-path test
    Path fixtureZip = tempDir.resolve("ctc-backup-test.zip");
    try (OutputStream out = Files.newOutputStream(fixtureZip)) {
        backupArchiveService.writeZip(out, Instant.now());
    }
    // … navigate, upload, click Proceed, tick acknowledged, click Execute Import …

    // then — D-15#1 success flash
    assertThat(page).hasURL(Pattern.compile(".*/admin/backup$"));
    assertThat(page.locator(".alert.alert-success"))
            .containsText("Import completed. ");
    assertThat(page.locator(".alert.alert-success"))
            .containsText(" rows restored across ");
    // negative assertions
    assertThat(page.locator(".alert.alert-error")).hasCount(0);
}
```

**Variation notes:**
- File already exists — do NOT recreate; EXTEND with the new test method and REPLACE the stub-flash assertion in the existing happy-path test.
- The cancel-flow / missing-checkbox-validation / stateless-re-parse tests (Phase 74) STAY (D-17 carry-forward).
- `@Tag("e2e")` and `extends PlaywrightConfig` preserved.

---

### Group 11 — `.planning/phases/75-.../75-HUMAN-UAT.md` (NEW)

**Analog:** `.planning/phases/73-backup-export-jackson-mixins-streaming-zip-endpoint/73-HUMAN-UAT.md` (3-test partial-format example):
```markdown
---
status: partial
phase: 73-backup-export-jackson-mixins-streaming-zip-endpoint
source: [73-VERIFICATION.md, 73-AUTO-UAT.md]
started: 2026-05-12T13:00:00Z
updated: 2026-05-12T13:05:00Z
---

## Current Test
[2/3 auto-verified — 1 deferred to Phase 75]

## Tests
### 1. Visual sanity check of /admin/backup page rendering on dev profile
expected: Page shows H1 "Backup"...
result: passed (auto-verified 2026-05-12T13:00Z via gsd-auto-uat — see 73-AUTO-UAT.md §1)

### 2. Manual full export click-through with downloaded ZIP inspection
expected: …
result: passed (auto-verified …)

## Summary
total: 3
passed: 2
…
```

**Convention for screenshot folder** (memory `feedback_screenshots_folder.md`): `.screenshots/<phase>/{before,after}/<route>-<viewport>.png`.

**Pattern to copy:** same YAML frontmatter shape (`status: partial`, `phase: 75-...`, `source: […]`, `started: …`, `updated: …`); same `## Tests` / `## Summary` / `## Gaps` sections; per-test `expected: … / result: …` lines.

**Planner-target file skeleton:**
```markdown
---
status: partial
phase: 75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat
source: [75-VERIFICATION.md, 75-AUTO-UAT.md, 75-VALIDATION.md §"Manual-Only Verifications"]
started: 2026-05-DD
updated: 2026-05-DD
---

## Current Test
[0/6 — operator runs through D-16 checklist on local MariaDB]

## Tests

### 1. /seasons/2023 Standings (Phase=REGULAR, Group=A) before+after import
expected: Position order + point totals byte-identical pre/post import (Desktop + Mobile)
command:  ./mvnw spring-boot:run -Dspring-boot.run.profiles=local,demo
          playwright-cli open http://localhost:9091/seasons/2023?phase=REGULAR&group=A
screenshots: .screenshots/75/before/standings-a-{desktop,mobile}.png
             .screenshots/75/after/standings-a-{desktop,mobile}.png
result: pending

### 2. /seasons/2023 Standings (Phase=REGULAR, Group=B) before+after
…

### 3. /seasons/2023 Driver Ranking (default phase) before+after
…

### 4. /seasons/2023/playoff PLAYOFF bracket before+after
…

### 5. /teams/<sub-team-slug> Phase Breakdown (Saison-2023 sub-team) before+after
…

### 6. /drivers/<top-driver-slug> Phase Breakdown (multi-phase results) before+after
…

### 7. data/.import-backups/<ts>/uploads-old/ retention check
expected: Directory exists 24 h after successful import
command:  ls -la data/.import-backups/
result: pending

## Summary
total: 7
passed: 0
issues: 0
pending: 7
skipped: 0
blocked: 0

## Gaps
(filled by operator)
```

**Variation notes:**
- Six screenshot routes per CONTEXT D-16 + one operator-driven retention check.
- `.screenshots/75/before/` and `.screenshots/75/after/` directories per memory `feedback_screenshots_folder.md`.
- The skill `gsd-auto-uat` is the auto-UAT path; this file documents the HUMAN UAT layer (memory `feedback_auto_uat_reminder.md`).

---

## Shared Patterns

### Authentication / Authorization
**Source:** Phase 73's `BackupControllerSecurityIT` (security matrix already covered; URL stable per D-17)
**Apply to:** No new auth wiring needed — `BackupController.importExecute` URL is unchanged, so existing profile-conditional auth (prod/docker require auth, dev/local permit all) applies verbatim.
**No code excerpt needed** — security is unmodified.

### Error Handling
**Source:** `org.ctc.backup.exception.BackupArchiveException` (`src/main/java/org/ctc/backup/exception/BackupArchiveException.java:14-117`)
**Apply to:** `UploadsRestoreException`, `RestoreFailureSimulatedException`
**Excerpt:**
```java
public class BackupArchiveException extends RuntimeException {
    // single-field, no Lombok, no Spring annotations
    private final Reason reason;
    public BackupArchiveException(Reason reason, String message)              { super(message);    this.reason = reason; }
    public BackupArchiveException(Reason reason, String message, Throwable c) { super(message, c); this.reason = reason; }
    public Reason reason() { return reason; }
}
```

### Logging
**Source:** every service in the codebase via `@Slf4j` + parameterized `{}` (CONVENTIONS.md §"Logging")
**Apply to:** Every new class
**Excerpt:**
```java
log.info("Backup import staged successfully: stagingId={}, schemaVersion={}, entityCount={}, ...",
        stagingId, backupVersion, entityCounts.size());  // BackupImportService.java:312-314
log.debug("Wiped {}: {} rows", tableName, rowCount);
log.error("Backup export failure mid-stream (filename={})", filename, e);  // BackupController.java:102
```

### Transaction Propagation
**Source:** CONVENTIONS.md §"Transaction Management" + this CONTEXT D-01 / D-14
**Apply to:** `DataImportAuditService.recordResult` (REQUIRES_NEW); `BackupImportService.execute` (REQUIRED + READ_COMMITTED).
**Excerpt:**
```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public UUID recordResult(...) { … }

@Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
public UUID execute(UUID stagingId) { … }
```

### Spring Bean Map Wiring (24-entity discovery)
**Source:** `BackupImportService.wireRepositoriesByTableName()` (`src/main/java/org/ctc/backup/service/BackupImportService.java:103-132`)
**Apply to:** `BackupImportService.execute` for `Map<String, EntityRestorer>` injection (planner can let Spring auto-inject `Map<String, EntityRestorer>` keyed by bean name, OR copy the `@PostConstruct` discovery pattern keyed by `restorer.tableName()`).
**Excerpt:**
```java
@PostConstruct
void wireRepositoriesByTableName() {
    Map<Class<?>, JpaRepository<?, ?>> byEntityClass = new HashMap<>();
    for (JpaRepository<?, ?> repo : allRepositories) {
        Class<?>[] typeArgs = GenericTypeResolver.resolveTypeArguments(repo.getClass(), JpaRepository.class);
        if (typeArgs != null && typeArgs.length >= 1) {
            byEntityClass.put(typeArgs[0], repo);
        }
    }
    // … fill map keyed by ref.tableName(); fail-fast if size mismatch …
    if (map.size() != backupSchema.getExportOrder().size()) {
        throw new IllegalStateException("BackupImportService bootstrap: expected " + ...);
    }
}
```

### ZIP Hardening Reuse
**Source:** `BackupArchiveService.assertEntrySafe(...)` (`BackupArchiveService.java:508-523`) + `PathTraversalGuard.assertWithin(...)` (`PathTraversalGuard.java:59-83`) + `LimitedInputStream` + `nonClosingView`
**Apply to:** `BackupArchiveService.extractUploadsTo(...)` (new Phase 75 method)
**Excerpt (already shown in Group 4 above):** Same `try (LimitedInputStream limited = new LimitedInputStream(nonClosingView(zis), MAX_ENTRY_BYTES, ...))` wrap; same `assertEntrySafe(entry, root, count, inflated)` call after every entry; same per-entry `PathTraversalGuard.assertWithin(destRoot, relName)`.

### Flash Attributes
**Source:** CONVENTIONS.md §"Flash messages" + `BackupController.java:139-150,225-227`
**Apply to:** `BackupController.importExecute` Phase 75 upgrade (D-15)
**Excerpt:**
```java
ra.addFlashAttribute("successMessage", "Import completed. " + restored + " rows restored across " + entities + " tables.");   // D-15#1
ra.addFlashAttribute("errorMessage",   "Import failed and was rolled back — see logs. Audit-id: " + auditUuid + ".");          // D-15#2
ra.addFlashAttribute("errorMessage",   "Import database succeeded but uploads restore failed and was reverted. See logs. Audit-id: " + auditUuid + ".");  // D-15#3
```

### Test Naming + Structure
**Source:** CLAUDE.md §"Test Naming (Given-When-Then)" + TESTING.md §"Test Naming Convention (BDD Style)"
**Apply to:** Every new test method
**Excerpt:** `givenContext_whenAction_thenResult()`; `// given` + `// when` + `// then` body comments; `assertThatThrownBy(...)` for exception assertions.

---

## No Analog Found

Files with no close match in the codebase (planner uses RESEARCH.md patterns instead):

| File | Role | Data Flow | Reason | Research Reference |
|------|------|-----------|--------|--------------------|
| `BackupImportPostCommitListener` (`@TransactionalEventListener(AFTER_COMMIT)`) | listener | event-driven | First `@TransactionalEventListener` in the codebase — closest is `@EventListener(ApplicationReadyEvent)` in `BackupStagingCleanup` (different phase) | RESEARCH §2 — Spring 6.x recommended idiom for post-commit hooks |
| `DataImportAuditService` (REQUIRES_NEW) | service | CRUD | First `@Transactional(propagation = REQUIRES_NEW)` in the codebase — no existing test verifies REQUIRES_NEW via spy on `PlatformTransactionManager` | RESEARCH §1 / §9 — REQUIRES_NEW composition with AFTER_COMMIT |
| `EntityRestorer` interface + 24 impls | SPI + bulk-insert family | batch-insert | First per-entity SQL-template family — closest is the 24 Jackson MixIns at `org.ctc.backup.serialization.*MixIn`, but those are Jackson-annotation classes (no logic), not SPI implementations | CONTEXT D-04..D-08 + RESEARCH §1 (ParameterizedPreparedStatementSetter) |
| `BackupImportMariaDbSmokeIT` (`@ActiveProfiles("local")` Testcontainers-style) | IT | DB connectivity | No existing IT runs against MariaDB — `mariadb-migration-smoke.yml` workflow boots the packaged JAR, not `@SpringBootTest`. Planner reconciles per CONTEXT D-16 + RESEARCH §7 | RESEARCH §7 — workflow-driven, NOT Testcontainers |

---

## Open Questions (planner to resolve)

1. **`SeasonTeam.successor_season_team_id` self-FK** — V1 schema line 90 declares `successor_season_team_id UUID, …, CONSTRAINT fk_st_successor FOREIGN KEY (successor_season_team_id) REFERENCES season_teams(id)`. CONTEXT D-06 names ONLY `Team.parentTeam` as 2-pass. The planner should verify whether `SeasonTeamRestorer` also needs a 2-pass NULL-then-UPDATE flow (likely yes — same FK class as `Team`). If yes, add to D-06 scope; if no (e.g., the successor link is always to a previously inserted row by JSON ordering), document the reasoning in the plan.

2. **`PlayoffMatchup.next_matchup_id` self-FK** — V1 schema line 195 + `fk_pm_next FOREIGN KEY (next_matchup_id) REFERENCES playoff_matchups(id)`. Same self-FK pattern — possibly needs 2-pass. CONTEXT/RESEARCH do not mention. Planner verifies.

3. **`auditId` propagation from `BackupImportService.execute()` to `BackupController`** — `execute()` is declared `@Transactional`; the audit row in the SUCCESS path is written by the AFTER_COMMIT listener AFTER `execute()` has returned. The controller needs the audit-id for the D-15#1 flash. Options: (a) `execute()` returns the staging-id, the listener publishes a follow-up event the controller awaits — complex; (b) `DataImportAuditService.recordResult(...)` is called from BOTH the catch-block (failure path) AND the listener (success path) — the controller gets the failure-audit-id from a `ThreadLocal` set in the catch; for success the listener stores the audit-id in a request-scoped bean — also complex; (c) the controller calls `DataImportAuditService.recordResult(true, ...)` itself after `execute()` returns — simplest, but violates D-09 step ordering (audit must be step 3, AFTER the file move). Planner picks; documents in PLAN.md.

4. **`BackupImportMariaDbSmokeIT` execution model** — RESEARCH §7 says "CTC's MariaDB-CI smoke does NOT use Testcontainers — it boots a `mariadb:11` service container and runs the packaged JAR." But CONTEXT D-16 directs `@SpringBootTest + @ActiveProfiles("local")`. These are incompatible (packaged JAR vs `@SpringBootTest`). Planner picks (recommended: extend `mariadb-migration-smoke.yml` with a NEW step after the JAR-is-healthy check that runs `./mvnw verify -Dit.test=BackupImportMariaDbSmokeIT -Dspring-boot.run.profiles=local` — the IT then connects to the SAME `mariadb:11` service container the JAR is connected to).

5. **`GlobalExceptionHandler.UploadsRestoreException` mapping** — CONTEXT canonical_refs §"`GlobalExceptionHandler`" calls this a planner judgment call. Recommendation: do NOT add a handler — `BackupController.importExecute` catches `UploadsRestoreException` directly and translates to the D-15#3 flash. The handler would only fire if catching is bypassed — defensive default. Per memory `feedback_plan_quality_gates.md`: prefer in-controller catch for known exception classes.

---

## Metadata

**Analog search scope:**
- `src/main/java/org/ctc/backup/**` — 44 files
- `src/main/java/org/ctc/domain/model/**` — 24 entities (1:1 mapping for 24 restorers)
- `src/main/java/org/ctc/admin/controller/GlobalExceptionHandler.java` — exception-handling reference
- `src/main/resources/db/migration/V*.sql` — schema reference for 24 restorer INSERT SQL
- `src/main/resources/application*.yml` — config-diff reference
- `src/test/java/org/ctc/backup/**` — 38 test files
- `src/test/java/org/ctc/admin/controller/integration/TemplateRenderingSmokeIT.java` — smoke IT analog
- `src/test/java/org/ctc/e2e/BackupImportE2ETest.java` — extend target (Phase 74 file)
- `.github/workflows/mariadb-migration-smoke.yml` — CI workflow precedent
- `.planning/phases/73-.../73-HUMAN-UAT.md` — HUMAN-UAT precedent

**Files scanned:** ~120 (main + test + config + workflow)

**Pattern extraction date:** 2026-05-14

**Key patterns identified:**
- All 24 restorers mirror the `<Entity>MixIn` 24-file family (parallel parallelism — same per-entity 1:1 mapping, same package layout `org.ctc.backup.<subpackage>.<Entity>X`).
- All services follow `@Slf4j @Service @RequiredArgsConstructor` (Lombok stack) with explicit ctor only when `@Value` is in the parameter list (CONVENTIONS.md mandate).
- ZIP hardening (PathTraversalGuard + LimitedInputStream + assertEntrySafe + nonClosingView) is verbatim-reused for `extractUploadsTo`.
- Flash messages use `successMessage` / `errorMessage` keys with locked English strings (D-15#1..3).
- All tests follow Given-When-Then BDD naming with `// given` / `// when` / `// then` comments.
- All ITs use `@SpringBootTest @ActiveProfiles("dev") @TestInstance(PER_CLASS)` (or `("local")` for the MariaDB smoke).
- Audit-row writer uses REQUIRES_NEW propagation to survive outer-tx rollback (the WHOLE point of D-01).
- L1 cache drop via `em.flush() + em.clear()` between native DELETE and `JdbcTemplate.batchUpdate` is mandatory (RESEARCH §3 + IMPORT-05 c).

---

## PATTERN MAPPING COMPLETE
