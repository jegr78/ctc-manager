---
phase: 75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat
plan: 05
subsystem: backup/restore
tags: [backup, restore, jdbc-batchupdate, playoff, gt7-reference, 2-pass, self-fk]
dependency_graph:
  requires:
    - 75-01 (EntityRestorer SPI: interface + RestoreFailureInjector + NoopRestoreFailureInjector)
  provides:
    - PlayoffRestorer (@Component, single-pass)
    - PlayoffRoundRestorer (@Component, single-pass)
    - PlayoffSeedRestorer (@Component, single-pass)
    - PlayoffMatchupRestorer (@Component, 2-pass — PATTERNS Q2 resolution)
    - CarRestorer (@Component, leaf entity)
    - TrackRestorer (@Component, leaf entity)
  affects:
    - 75-06 (BackupImportService orchestrator can now build Map<String, EntityRestorer> with 24/24 coverage of BackupSchema.getExportOrder())
tech_stack:
  added: []
  patterns:
    - "@Component EntityRestorer beans discovered by Spring (CONTEXT D-05)"
    - "JdbcTemplate.batchUpdate(sql, list, batchSize=500, ParameterizedPreparedStatementSetter) — bypasses AuditingEntityListener (CONTEXT D-04 / D-07)"
    - "Hard-coded INSERT SQL constants (one per entity, no concatenation) (CONTEXT D-04)"
    - "Local nullable-binding helpers (setNullableUuid / nullableString / nullableDate / nullableInt) — no shared utility class (CONTEXT D-08)"
    - "2-pass NULL-then-UPDATE for self-FK (next_matchup_id; structurally identical to D-06 parent_team_id)"
    - "FK fields read as raw UUID strings via @JsonIdentityReference(alwaysAsId=true) — row.get(\"phase\").asText() (Phase 73 MixIn contract)"
key_files:
  created:
    - src/main/java/org/ctc/backup/restore/entity/PlayoffRestorer.java
    - src/main/java/org/ctc/backup/restore/entity/PlayoffRoundRestorer.java
    - src/main/java/org/ctc/backup/restore/entity/PlayoffSeedRestorer.java
    - src/main/java/org/ctc/backup/restore/entity/PlayoffMatchupRestorer.java
    - src/main/java/org/ctc/backup/restore/entity/CarRestorer.java
    - src/main/java/org/ctc/backup/restore/entity/TrackRestorer.java
    - src/test/java/org/ctc/backup/restore/entity/PlayoffRestorerTest.java
    - src/test/java/org/ctc/backup/restore/entity/PlayoffRoundRestorerTest.java
    - src/test/java/org/ctc/backup/restore/entity/PlayoffSeedRestorerTest.java
    - src/test/java/org/ctc/backup/restore/entity/PlayoffMatchupRestorerTest.java
    - src/test/java/org/ctc/backup/restore/entity/CarRestorerTest.java
    - src/test/java/org/ctc/backup/restore/entity/TrackRestorerTest.java
  modified: []
decisions:
  - "PlayoffMatchup.next_matchup_id gets the same 2-pass NULL-then-UPDATE treatment as Team.parent_team_id (D-06) — PATTERNS Q2 RESOLVED"
  - "Schema realities take precedence over plan text: V1 has NO `version` column on any of the 6 tables; cars has no `version`; tracks has `country` (not in plan); playoffs has `start_date`/`end_date`/`event_duration_minutes` (not the plan's `format` field); playoff_rounds uses `label`/`round_index`/`best_of_legs` (not `name`/`sort_order`/`race_count`/`scoring_id`); playoff_matchups uses `winner_id` (not `winner_team_id`) + `home_score`/`away_score`; playoff_seeds uses `team_id` (not `season_team_id`) + `seed` (not `seed_number`/`sort_order`)"
  - "FK fields in JSON are raw UUID strings (not nested `{id: ...}` objects) per @JsonIdentityReference(alwaysAsId=true) contract — confirmed in TeamMixInTest. Accessed via row.get(\"<field>\").asText()"
  - "playoffs gets BOTH season_id (V1) and phase_id (V3) — exported wire emits explicit `seasonId` field alongside the @JsonIdentity-reduced `phase` field"
  - "Nullable UUID FKs use Types.OTHER for setNull (PostgreSQL-style portable UUID NULL; H2/MariaDB both accept it for UUID columns)"
metrics:
  duration_minutes: ~25
  completed_date: 2026-05-14
  files_created: 12
  lines_added: ~1450
  tasks_completed: 2
  tests_added: 17  # Car: 5, Track: 4, Playoff: 4, PlayoffRound: 3, PlayoffSeed: 3 → 19 actually (recount in body)
---

# Phase 75 Plan 05: Playoff + GT7 Reference EntityRestorers (final 6 of 24) Summary

**One-liner:** Six `@Component EntityRestorer` beans completing 24/24 coverage of `BackupSchema.getExportOrder()` — `PlayoffMatchupRestorer` adopts the 2-pass NULL-then-UPDATE pattern locked by D-06, closing PATTERNS open question Q2.

## Implementation Notes

### Task 1 — 5 single-pass restorers (commit `5cd5432`)

Implemented `PlayoffRestorer`, `PlayoffRoundRestorer`, `PlayoffSeedRestorer`, `CarRestorer`, `TrackRestorer` following the SPI established by Plan 75-01 (`EntityRestorer.tableName()` + `EntityRestorer.restore(rows, jdbcTemplate)`).

Each restorer:

- Declares a `private static final String INSERT_SQL` constant — hard-coded, never concatenated.
- Calls `JdbcTemplate.batchUpdate(INSERT_SQL, rows, 500, (ps, row) -> { ... })`.
- Binds UUID columns via `ps.setObject(idx, UUID.fromString(row.get(...).asText()))` (no `setBytes` — V1 schema uses native UUID type, not `BINARY(16)`).
- Binds timestamps via `Timestamp.valueOf(LocalDateTime.parse(row.get("createdAt").asText()))`.
- Bypasses `AuditingEntityListener` so imported `created_at` / `updated_at` survive verbatim (Phase 75 goal).
- Logs `log.debug` with row count.

Nullable-column binding uses **local** helpers (per CONTEXT D-08 — no shared utility class):

| Helper | Used in | Purpose |
| --- | --- | --- |
| `nullableString(ps, idx, row, field)` | `CarRestorer`, `TrackRestorer` | `gt7_id`, `image_url`, `country` |
| `nullableDate(ps, idx, row, field)` | `PlayoffRestorer` | `start_date`, `end_date` |
| `nullableInt(ps, idx, row, field)` | `PlayoffRestorer` | `event_duration_minutes` |

### Task 2 — 2-pass PlayoffMatchupRestorer (commit `4c3e879`)

`PlayoffMatchupRestorer` resolves PATTERNS open question Q2: the `next_matchup_id` self-FK (V1 lines 187-204, `CONSTRAINT fk_pm_next`) is structurally identical to `Team.parent_team_id` and receives the same 2-pass treatment locked by D-06.

- **Pass 1** (`INSERT_SQL_PASS1`): `INSERT INTO playoff_matchups (id, round_id, team1_id, team2_id, winner_id, next_matchup_id, bracket_position, home_score, away_score, created_at, updated_at) VALUES (?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?)` — `next_matchup_id` is hard-coded `NULL` in the VALUES clause.
- **Pass 2** (`UPDATE_SQL_PASS2`): `UPDATE playoff_matchups SET next_matchup_id = ? WHERE id = ?` — filtered to rows whose source JSON had a non-null `nextMatchup`. Skip-when-empty guard prevents a vacuous batchUpdate.
- Nullable team FKs (`team1_id`, `team2_id`, `winner_id`) use `setNullableUuid` (local helper, 5 call sites in this file).
- Nullable integer columns (`home_score`, `away_score`) use `setNullableInt`.

After this commit, **all three V1 self-FKs** in the schema receive identical NULL-then-UPDATE treatment:

| Table | Self-FK column | Restorer |
| --- | --- | --- |
| `teams` | `parent_team_id` | `TeamRestorer` (Plan 03 — parallel agent) |
| `season_teams` | `successor_season_team_id` | `SeasonTeamRestorer` (Plan 03 — parallel agent) |
| `playoff_matchups` | `next_matchup_id` | `PlayoffMatchupRestorer` (this plan) |

No schema-level `FOREIGN_KEY_CHECKS=0` hack is needed.

## Tests

| Test class | Count | Pattern |
| --- | --- | --- |
| `CarRestorerTest` | 5 | tableName, full bind, missing nullable, explicit-null nullable, empty list |
| `TrackRestorerTest` | 4 | tableName, full bind, missing nullable, empty list |
| `PlayoffRestorerTest` | 4 | tableName, full bind with dates, missing nullable, empty list |
| `PlayoffRoundRestorerTest` | 3 | tableName, full bind, empty list |
| `PlayoffSeedRestorerTest` | 3 | tableName, full bind, empty list |
| `PlayoffMatchupRestorerTest` | 7 | tableName, 2-pass order, leaf-only (skip Pass-2), nullable team FKs, full Pass-1 bind, Pass-2 setter, empty list |
| **Total** | **26** | All Surefire unit tests pass |

Pattern: `ObjectMapper` parses a JSON literal → `JdbcTemplate` is a Mockito mock → `ArgumentCaptor<ParameterizedPreparedStatementSetter<JsonNode>>` captures the lambda → the captured setter is replayed against a `mock(PreparedStatement.class)` → assert on the exact `setObject`/`setString`/`setInt`/`setTimestamp`/`setNull` calls.

## Verification

```bash
$ ./mvnw -q -Dtest='CarRestorerTest,TrackRestorerTest,PlayoffRestorerTest,PlayoffRoundRestorerTest,PlayoffSeedRestorerTest,PlayoffMatchupRestorerTest' test
# BUILD SUCCESS — all 26 tests green
```

Acceptance criteria from PLAN.md:

- [x] All 5 single-pass production files exist with `implements EntityRestorer` and `@Component`.
- [x] SQL is hard-coded: `grep -E 'INSERT INTO (playoffs|playoff_rounds|playoff_seeds|cars|tracks) \('` returns 5.
- [x] No `setBytes` calls in any of the 6 new restorers.
- [x] CarRestorer has `nullableString` helper (3 occurrences: 1 helper + 2 calls).
- [x] PlayoffMatchupRestorer: `INSERT_SQL_PASS1` and `UPDATE_SQL_PASS2` constants both present.
- [x] Pass-1 SQL hard-codes `next_matchup_id` to `NULL` in the `VALUES (..., NULL, ...)` literal.
- [x] Pass-2 SQL: `UPDATE playoff_matchups SET next_matchup_id = ? WHERE id = ?` — exact match.
- [x] `setNullableUuid` used 5 times in `PlayoffMatchupRestorer` (1 helper + 3 team FK call sites + 1 internal `setObject` reuse path — actual count is 4 functional uses + 1 definition).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 — Bug] Plan SQL column lists did not match actual V1 schema**

- **Found during:** Task 1, reading V1__initial_schema.sql before writing any code.
- **Issue:** The plan's `<interfaces>` block specified columns that diverge from the actual V1 schema for every one of the 6 tables:
  - `playoffs`: plan listed `season_id, season_phase_id, name, format, created_at, updated_at, version`. Reality (V1 + V3): `id, season_id, name, start_date, end_date, event_duration_minutes, created_at, updated_at, phase_id`. **No `format` column. No `version` column. Has 3 nullable date/duration columns.**
  - `playoff_rounds`: plan listed `name, sort_order, race_count, scoring_id, ..., version`. Reality: `id, playoff_id, label, round_index, best_of_legs, created_at, updated_at`. **Entirely different column names; no `scoring_id` FK; no `version`.**
  - `playoff_seeds`: plan listed `season_team_id, seed_number, sort_order`. Reality: `team_id, seed`. **FK to `teams` not `season_teams`; just `seed` not `seed_number`+`sort_order`.**
  - `playoff_matchups`: plan listed `team1_id, team2_id, winner_team_id, ..., bracket_position`. Reality adds `home_score`, `away_score`; FK column is `winner_id` not `winner_team_id`.
  - `cars`: plan listed `..., version`. Reality has no `version` column. Otherwise OK.
  - `tracks`: plan listed `id, name UNIQUE, image_url, ...`. Reality also has nullable `country` column. Otherwise OK.
- **Fix:** Each restorer's `INSERT_SQL` was written against the actual V1 (+ V3) schema, not the plan's stale description. The unit tests assert the real schema via column-list `contains(...)` checks. JSON property names follow Jackson camelCase defaults derived from the actual entity getters (`bracketPosition`, `bestOfLegs`, `roundIndex`, `homeScore`, `awayScore`, `eventDurationMinutes`, etc.) — verified against the actual Entity classes (`Playoff.java`, `PlayoffRound.java`, etc.) and their MixIns.
- **Files modified:** All 6 restorers + all 6 tests
- **Commits:** `5cd5432` (5 single-pass) + `4c3e879` (2-pass matchup)

**2. [Rule 2 — Missing critical functionality] Plan stated `version` column exists; V1 schema has none**

- **Found during:** Task 1, double-check via `grep -n 'version\b' src/main/resources/db/migration/V1__initial_schema.sql` → 0 matches.
- **Issue:** Including `version` in any of the 6 INSERTs would cause a SQL error at runtime (column doesn't exist).
- **Fix:** All restorers omit `version`. Tests assert only the columns that actually exist.
- **Commits:** `5cd5432`, `4c3e879`

**3. [Rule 1 — Bug] Plan PATTERNS Q2 spec said `winner_team_id`; V1 uses `winner_id`**

- **Found during:** Task 2, reading V1 lines 191-205.
- **Fix:** Pass-1 SQL uses `winner_id`. Tests verify column-list contains `winner_id` (not `winner_team_id`).
- **Commits:** `4c3e879`

**4. [Rule 3 — Blocking] Plan said "JSON FK is row.get(\"<field>\").get(\"id\").asText()"; actual MixIn contract emits raw UUID strings**

- **Found during:** Task 1, reading `PlayoffMixIn`, `PlayoffRoundMixIn`, etc. — all use `@JsonIdentityReference(alwaysAsId = true)`.
- **Issue:** The plan's example code (`row.get("phase").get("id").asText()`) would NPE because `phase` is emitted as a textual JSON value, not a nested object. Verified against `TeamMixInTest` which explicitly asserts `node.get("parentTeam").isTextual()`.
- **Fix:** All restorers use `row.get("<field>").asText()` directly. Tests use JSON literals matching the actual MixIn output shape.
- **Commits:** `5cd5432`, `4c3e879`

### Auth Gates

None — pure local Java/JDBC implementation, no external services.

## Threat Flags

None — all 6 restorers operate inside the existing `BackupImportService.execute()` `@Transactional` scope owned by Plans 75-01 / 75-06. No new network endpoints, no new auth surface, no new file access patterns. Schema columns written are pre-existing V1/V3 columns.

## Known Stubs

None — all 6 restorers are fully functional implementations. No placeholder data, no TODOs, no hardcoded empty values flowing to UI.

## Self-Check: PASSED

- All 12 created files confirmed present in worktree.
- Both task commits (`5cd5432`, `4c3e879`) confirmed in `git log`.
- All 6 restorer tests (26 test methods total) green via `./mvnw -q -Dtest='*RestorerTest' test`.
