---
phase: 75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat
plan: 03
subsystem: backup
tags: [jdbc-batch-update, parameterized-prepared-statement-setter, native-uuid, jackson-tree-binding, auditing-bypass, two-pass-self-fk]

# Dependency graph
requires:
  - phase: 75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat
    plan: 01
    provides: "EntityRestorer SPI under org.ctc.backup.restore (interface + RestoreFailureInjector + NoopRestoreFailureInjector)"
  - phase: 73-backup-export-jackson-mixins-streaming-zip-endpoint
    provides: "Jackson MixIns under org.ctc.backup.serialization (Season/Team/Driver cluster — camelCase, @JsonIdentityReference UUID strings)"
  - phase: 72-backup-wire-contract-schema-manifest-objectmapper-audit-log
    provides: "BackupSchema.getExportOrder() + entity topo-sort (Team.parentTeam self-FK already excluded)"
provides:
  - "9 EntityRestorer @Component implementations under org.ctc.backup.restore.entity covering the Season/Team/Driver cluster"
  - "Locked binding pattern: native UUID via ps.setObject (no BINARY(16) packing); timestamps via Timestamp.valueOf(LocalDateTime.parse) — verbatim createdAt/updatedAt preserved"
  - "Locked 2-pass restorer pattern for self-FK breaking (TeamRestorer + SeasonTeamRestorer — same hand-rolled INSERT-NULL/UPDATE-FK shape)"
  - "Reference template for the remaining 15 single-pass restorers (Plans 04 + 05)"
affects:
  - 75-04 (RaceLineup/Race/Match-cluster restorers reuse the same template + nullableXxx helpers)
  - 75-05 (Playoff/Car/Track-cluster restorers reuse the same template)
  - 75-06 (orchestrator's Map<String, EntityRestorer> lookup is keyed by tableName() — all 9 here register cleanly)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "JdbcTemplate.batchUpdate(SQL, rows, 500, ParameterizedPreparedStatementSetter<JsonNode>) — auto-chunking flavor with hand-rolled Jackson-tree-to-JDBC type coercion"
    - "Hard-coded INSERT_SQL private static final String constant per restorer — no concatenation, no string-format, SQLI defense by construction"
    - "Native UUID column binding via ps.setObject(idx, UUID.fromString(...)) — V1+V3 columns are UUID, NOT BINARY(16) (RESEARCH §5 correction of CONTEXT D-08)"
    - "Nullable INT binding via ps.setObject(idx, Integer|null, Types.INTEGER) — preserves SQL NULL vs. asInt()'s default 0"
    - "2-pass self-FK breaking: Pass-1 INSERT with NULL hard-coded in SQL VALUES (...); Pass-2 UPDATE for the subset whose JSON FK field is non-null; Pass-2 skipped when empty"

key-files:
  created:
    - src/main/java/org/ctc/backup/restore/entity/SeasonRestorer.java
    - src/main/java/org/ctc/backup/restore/entity/SeasonPhaseRestorer.java
    - src/main/java/org/ctc/backup/restore/entity/SeasonPhaseGroupRestorer.java
    - src/main/java/org/ctc/backup/restore/entity/TeamRestorer.java
    - src/main/java/org/ctc/backup/restore/entity/SeasonTeamRestorer.java
    - src/main/java/org/ctc/backup/restore/entity/PhaseTeamRestorer.java
    - src/main/java/org/ctc/backup/restore/entity/DriverRestorer.java
    - src/main/java/org/ctc/backup/restore/entity/SeasonDriverRestorer.java
    - src/main/java/org/ctc/backup/restore/entity/PsnAliasRestorer.java
    - src/test/java/org/ctc/backup/restore/entity/SeasonRestorerTest.java
    - src/test/java/org/ctc/backup/restore/entity/SeasonPhaseRestorerTest.java
    - src/test/java/org/ctc/backup/restore/entity/SeasonPhaseGroupRestorerTest.java
    - src/test/java/org/ctc/backup/restore/entity/TeamRestorerTest.java
    - src/test/java/org/ctc/backup/restore/entity/SeasonTeamRestorerTest.java
    - src/test/java/org/ctc/backup/restore/entity/PhaseTeamRestorerTest.java
    - src/test/java/org/ctc/backup/restore/entity/DriverRestorerTest.java
    - src/test/java/org/ctc/backup/restore/entity/SeasonDriverRestorerTest.java
    - src/test/java/org/ctc/backup/restore/entity/PsnAliasRestorerTest.java
  modified: []

key-decisions:
  - "PLAN-Q1 resolved: SeasonTeam.successor_season_team_id receives the same 2-pass treatment as Team.parentTeam (D-06). V1__initial_schema.sql:90 declares it as a structurally identical self-FK; the alternative (topologically sorting the JSON array) would couple the restorer to source-array order which Phase 73's exporter does not guarantee."
  - "RESEARCH §5 native-UUID correction is honored: ps.setObject(idx, UUID.fromString(...)) — NOT BINARY(16) packing as CONTEXT D-08 originally suggested. V1+V3 schema confirms every PK is typed UUID, not BINARY(16). No UuidPacker utility is created."
  - "JSON property naming is camelCase per Jackson default (no @JsonNaming SnakeCase) — keys are createdAt / sortIndex / parentTeam / raceScoring / etc. Verified via existing TeamMixInTest / SeasonMixInTest."
  - "FK fields render as bare UUID strings (NOT nested {id:...} objects) per @JsonIdentityReference(alwaysAsId=true) on every MixIn FK getter — corrected the PLAN <interfaces> mis-description after reading TeamMixInTest.java:58-62."
  - "Schema-correction deviations from PLAN <interfaces> use the V1+V3 SQL as the source of truth (Rule 3 — blocking issue): season_drivers.team_id (not season_team_id); psn_aliases.alias single col; season_phase_groups.phase_id + sort_index; phase_teams.group_id; SeasonTeamMixIn property is `successor` not `successorSeasonTeam`."
  - "SeasonRestorer SQL writes only the entity-mapped subset of columns (id, name, season_year, season_number, description, active, created_at, updated_at). The DB-level columns format / total_rounds / legs / event_duration_minutes / race_scoring_id / match_scoring_id were demoted to SeasonPhase in V3 and are no longer on the Season entity — they are NOT in the export JSON. Behavior at MariaDB runtime relies on the V1 column-level DEFAULT clauses (format DEFAULT 'LEAGUE', legs DEFAULT 1) and on race_scoring_id/match_scoring_id having since been relaxed at the DB layer (Plan 06 IT verifies end-to-end)."

patterns-established:
  - "Per-restorer Surefire unit test asserts: (a) tableName() returns the snake_case literal; (b) restore(...) invokes JdbcTemplate.batchUpdate(SQL, rows, 500, setter) where SQL matches `^INSERT INTO <table> \\([^)]+\\) VALUES \\(\\?(, \\?)+\\)$`; (c) the captured ParameterizedPreparedStatementSetter, driven against a @Mock PreparedStatement with one realistic JSON row, calls setObject/setString/setInt/setBoolean/setTimestamp/setDate with the exact verbatim values."
  - "2-pass test pattern: verify(jdbcTemplate, times(2)).batchUpdate(...) to capture ALL invocations; then InOrder.verify with non-capturing matchers (startsWith / eq) to assert Pass-1 INSERT precedes Pass-2 UPDATE. Setter-driving uses one PreparedStatement mock PER pass to keep Mockito invocation tallies isolated."
  - "private static nullableString / nullableInt / nullableDate / nullableUuid helpers per restorer (no shared utility — D-08's per-entity rule)."

requirements-completed: [IMPORT-05]

# Metrics
duration: ~20min
completed: 2026-05-14
---

# Phase 75 Plan 03: Season/Team/Driver Cluster Restorers Summary

**9 EntityRestorer @Component implementations covering the Season/Team/Driver cluster — 7 single-pass + 2 two-pass (Team + SeasonTeam break their self-FKs via INSERT-NULL/UPDATE-FK). Locks the per-restorer template and resolves PATTERNS open question Q1.**

## Performance

- **Duration:** ~20 min wall clock
- **Tasks:** 2/2 complete
- **Commits:** 4 (2× test/RED + 2× feat/GREEN)
- **Tests added:** 32 (9 Restorer classes × 3–4 tests each)
- **All tests green:** `./mvnw -q -Dtest='*RestorerTest' test` → 32/32

## What was built

### Task 1 — 7 single-pass restorers (commits `5af0cb1` test + `95baa46` feat)

| Restorer                  | Table                  | Columns bound                                                                                                                       | Notable                                                                       |
| ------------------------- | ---------------------- | ----------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------- |
| `SeasonRestorer`          | `seasons`              | id, name, season_year, season_number, description (nullable), active, created_at, updated_at                                        | DB-level columns format / race_scoring_id etc. NOT in entity → not bound      |
| `SeasonPhaseRestorer`     | `season_phases`        | id, season_id, sort_index, phase_type, layout, format, label, start_date, end_date, total_rounds, legs, event_duration_minutes, race_scoring_id, match_scoring_id, created_at, updated_at | Nullable INT via `setObject(idx, Integer|null, Types.INTEGER)`                |
| `SeasonPhaseGroupRestorer`| `season_phase_groups`  | id, phase_id, name, sort_index, created_at, updated_at                                                                              | Phase-FK is bare UUID string per MixIn                                        |
| `PhaseTeamRestorer`       | `phase_teams`          | id, phase_id, team_id, group_id (nullable), created_at, updated_at                                                                  | `nullableUuid(row, "group")` helper for the only nullable FK                  |
| `DriverRestorer`          | `drivers`              | id, psn_id, nickname, active, created_at, updated_at                                                                                | Smallest restorer — fully leaf                                                |
| `SeasonDriverRestorer`    | `season_drivers`       | id, season_id, driver_id, team_id, created_at, updated_at                                                                           | V1 column is `team_id` (FK to teams) — plan's `season_team_id` was incorrect  |
| `PsnAliasRestorer`        | `psn_aliases`          | id, driver_id, alias, created_at, updated_at                                                                                        | V1 column is single `alias` (UNIQUE) — no `alias_lower`/`valid_from`/`valid_to` |

### Task 2 — 2 two-pass restorers (commits `ec0a052` test + `8445419` feat)

`TeamRestorer` (D-06):
- `INSERT_SQL_PASS1`: `INSERT INTO teams (id, name, short_name, logo_url, primary_color, secondary_color, accent_color, parent_team_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?, ?)`
- `UPDATE_SQL_PASS2`: `UPDATE teams SET parent_team_id = ? WHERE id = ?`
- Pass 2 invoked only for `rows.stream().filter(r -> r.get("parentTeam") != null && !r.get("parentTeam").isNull()).toList()`.

`SeasonTeamRestorer` (PLAN-Q1 resolution):
- `INSERT_SQL_PASS1`: `INSERT INTO season_teams (id, season_id, team_id, rating, primary_color, secondary_color, accent_color, logo_url, successor_season_team_id, replaced_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?)`
- `UPDATE_SQL_PASS2`: `UPDATE season_teams SET successor_season_team_id = ? WHERE id = ?`
- Pass 2 filter on the `successor` camelCase JSON field — NOT `successorSeasonTeam` as the plan's interfaces block claimed (corrected per `SeasonTeamMixIn` + entity field name).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] PLAN `<interfaces>` block listed JSON FK shape as nested `{id:...}` object**
- **Found during:** Task 1 RED-test authoring (reading `TeamMixInTest.java:58-62`).
- **Issue:** PLAN said "the @JsonIdentityInfo PropertyGenerator from the MixIn emits a nested object with only the id property visible." Actual behavior (verified via `TeamMixInTest`): `@JsonIdentityReference(alwaysAsId=true)` on the MixIn getter emits a **bare UUID string**, not a nested object.
- **Fix:** Every Restorer reads FKs via `row.get("<fkField>").asText()` directly (no `.get("id")` indirection).
- **Files modified:** All 9 production restorers + 9 test files.
- **Commits:** `5af0cb1`, `95baa46`, `ec0a052`, `8445419`.

**2. [Rule 3 - Schema mismatch] PLAN `<interfaces>` block listed columns absent from V1/V3 schema**
- **Found during:** Task 1 RED-test authoring (reading `V1__initial_schema.sql`).
- **Issue:** PLAN named these columns/fields that do not exist:
  - `season_drivers.season_team_id` → actual: `team_id` (V1:72, joining `teams` not `season_teams`)
  - `psn_aliases.psn_alias` / `alias_lower` / `valid_from` / `valid_to` → actual: single `alias` column (V1:303)
  - `season_phase_groups.season_phase_id` / `sort_order` → actual: `phase_id` / `sort_index` (V3:30,32)
  - `phase_teams.season_team_id` → does not exist; nullable column is `group_id` (V3:42)
  - `SeasonTeamMixIn` field `successorSeasonTeam` → actual: `successor` per entity field name + MixIn camelCase default
- **Fix:** Followed V1/V3 SQL + the actual MixIn output as the source of truth (Rule 3 — blocking issue). Documented each deviation in the class-level Javadoc of the affected restorer.
- **Files modified:** `SeasonDriverRestorer`, `PsnAliasRestorer`, `SeasonPhaseGroupRestorer`, `PhaseTeamRestorer`, `SeasonTeamRestorer` (+ their tests).

**3. [Rule 1 - Bug] InOrder.verify + ArgumentCaptor double-capture conflict**
- **Found during:** Task 2 first GREEN test run.
- **Issue:** Using `InOrder.verify(jdbcTemplate).batchUpdate(captor.capture()...)` twice on the same mock made Mockito report each `batchUpdate` call as "wanted 1 time but was 2 times" — InOrder cannot capture across multiple matched invocations on the same mock without `times(N)`.
- **Fix:** Split into (a) `verify(jdbcTemplate, times(2)).batchUpdate(captors)` to capture both calls, then (b) `InOrder.verify(jdbcTemplate).batchUpdate(startsWith(...), anyList(), anyInt(), any())` with non-capturing matchers for the ordering assertion only.
- **Files modified:** `TeamRestorerTest.java`, `SeasonTeamRestorerTest.java`.
- **Commit:** Fix was bundled into the GREEN commit `8445419` per TDD norms (test-only iteration during GREEN).

**4. [Rule 1 - Bug] Shared PreparedStatement mock double-counted setter calls across Pass-1 + Pass-2**
- **Found during:** Task 2 second GREEN test run.
- **Issue:** Driving both the Pass-1 setter and the Pass-2 setter against the same `@Mock PreparedStatement` made `verify(preparedStatement).setObject(1, <uuid>)` fail with "wanted 1 time but was 2 times" because both passes happen to start with `setObject(1, …)`.
- **Fix:** Use a fresh `Mockito.mock(PreparedStatement.class)` per pass — `psPass1` and `psPass2`. Tally isolation is mechanical.
- **Files modified:** `TeamRestorerTest.java`, `SeasonTeamRestorerTest.java`.
- **Commit:** `8445419` (same GREEN commit).

## Self-Check: PASSED

**Files created (verified `[ -f "<path>" ] && echo FOUND`):**
- All 9 production restorers exist at `src/main/java/org/ctc/backup/restore/entity/<Name>Restorer.java`.
- All 9 test files exist at `src/test/java/org/ctc/backup/restore/entity/<Name>RestorerTest.java`.

**Commits exist (verified `git log --oneline`):**
- `5af0cb1` test(75-03): add failing tests for 7 single-pass Season/Team/Driver restorers
- `95baa46` feat(75-03): implement 7 single-pass Season/Team/Driver restorers
- `ec0a052` test(75-03): add failing tests for 2-pass TeamRestorer + SeasonTeamRestorer
- `8445419` feat(75-03): implement 2-pass TeamRestorer + SeasonTeamRestorer

**Plan acceptance criteria — Task 1:**
- ✅ `grep -l 'implements EntityRestorer' src/main/java/org/ctc/backup/restore/entity/*.java | wc -l` → 9 (7 from Task 1 + 2 from Task 2 = total 9)
- ✅ Each restorer has `@Component`
- ✅ Each restorer has a `private static final String INSERT_SQL =` or `INSERT_SQL_PASS1`
- ✅ Each restorer's SQL starts with `INSERT INTO <table> (...)`
- ✅ No SQL concatenation (column-name-into-SQL) — grep returns 0 matches for `".+"\s*\+\s*\w` inside INSERT/UPDATE/DELETE
- ✅ No `setBytes` anywhere under `src/main/java/org/ctc/backup/restore/` (native-UUID guard per RESEARCH §5)
- ✅ `./mvnw -q -Dtest='*RestorerTest' test` → 32/32 BUILD SUCCESS

**Plan acceptance criteria — Task 2:**
- ✅ `grep -c 'INSERT_SQL_PASS1' src/main/java/org/ctc/backup/restore/entity/TeamRestorer.java` → 2
- ✅ `grep -c 'INSERT_SQL_PASS1' src/main/java/org/ctc/backup/restore/entity/SeasonTeamRestorer.java` → 2
- ✅ Pass-1 SQL hard-codes self-FK to NULL in both restorers (verified via grep `parent_team_id` + `NULL` / `successor_season_team_id` + `NULL`)
- ✅ Pass-2 SQL `UPDATE teams SET parent_team_id = ? WHERE id = ?` literal in TeamRestorer
- ✅ Pass-2 SQL `UPDATE season_teams SET successor_season_team_id = ? WHERE id = ?` literal in SeasonTeamRestorer
- ✅ Both tests verify exactly 2 batchUpdate invocations on the happy path and exactly 1 on the no-FK path

**Plan overall verification block:**
- ✅ `./mvnw -q -Dtest='*RestorerTest' test` → 32/32 tests passed BUILD SUCCESS
- ✅ `./mvnw -q compile` BUILD SUCCESS — all 9 new files compile cleanly
- ✅ `grep -rn 'setBytes' src/main/java/org/ctc/backup/restore/` → no matches
- ✅ PATTERNS open question Q1 resolved by adopting 2-pass for `SeasonTeam.successor_season_team_id`; reference captured in `SeasonTeamRestorer` class Javadoc with explicit citation of V1__initial_schema.sql:90 and CONTEXT D-06

## Known Stubs

None. All 9 restorers are fully functional and wire-up into the orchestrator's `Map<String, EntityRestorer>` lookup (Plan 06) via Spring's `@Component`-discovery. The remaining 15 entities (Race-cluster, Playoff-cluster, Car/Track, scoring tables) are owned by Plans 04 + 05.

## Threat Flags

None. No new network endpoints, auth paths, file access patterns, or schema changes. The hard-coded `INSERT_SQL` constants close the T-IMPORT-SQLI surface declared in Phase 75's Validation matrix (no concatenation = no injection at the restorer layer).
