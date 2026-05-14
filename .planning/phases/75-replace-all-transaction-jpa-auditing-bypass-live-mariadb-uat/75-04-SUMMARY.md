---
phase: 75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat
plan: 04
subsystem: backup
tags: [backup, restore, jdbc, batch, auditing-bypass, jpa]
requires:
  - 75-01 (EntityRestorer SPI)
provides:
  - "MatchdayRestorer — restores matchdays (V1+V3 schema with phase_id/group_id FKs)"
  - "MatchRestorer — restores matches with bye BOOLEAN and nullable away_team/scores"
  - "MatchScoringRestorer — restores match_scorings (points_win/draw/loss INT)"
  - "RaceRestorer — restores races with 7 FKs and date_time/calendar_event_id"
  - "RaceLineupRestorer — restores race_lineups (race+driver+team triple, source-of-truth)"
  - "RaceResultRestorer — restores race_results (HOTTEST PATH, ~1000 rows on Saison-2023)"
  - "RaceScoringRestorer — restores race_scorings with VARCHAR(500) race_points verbatim"
  - "RaceSettingsRestorer — restores race_settings (12 nullable config columns)"
  - "RaceAttachmentRestorer — restores race_attachments (AttachmentType enum as VARCHAR)"
affects:
  - "BackupImportService.execute() orchestrator (Plan 06) will look these 9 restorers up via Map<String, EntityRestorer> keyed by tableName()"
tech-stack:
  added: []
  patterns:
    - "ParameterizedPreparedStatementSetter auto-chunking at batchSize=500"
    - "@Slf4j @Component @RequiredArgsConstructor Lombok stack on all restorers"
    - "Hard-coded INSERT SQL as private static final String (SQLI defense)"
    - "Inlined setNullableUuid/setNullableInt/setNullableString/setNullableTimestamp helpers per restorer (D-08 — no shared utility)"
    - "Native UUID JDBC binding via setObject(idx, UUID.fromString(...)) (RESEARCH §5 — drops BINARY(16) packing)"
    - "Enum-as-VARCHAR binding via setString on row.get(field).asText() (per @Enumerated(STRING))"
key-files:
  created:
    - src/main/java/org/ctc/backup/restore/entity/MatchdayRestorer.java
    - src/main/java/org/ctc/backup/restore/entity/MatchRestorer.java
    - src/main/java/org/ctc/backup/restore/entity/MatchScoringRestorer.java
    - src/main/java/org/ctc/backup/restore/entity/RaceRestorer.java
    - src/main/java/org/ctc/backup/restore/entity/RaceLineupRestorer.java
    - src/main/java/org/ctc/backup/restore/entity/RaceResultRestorer.java
    - src/main/java/org/ctc/backup/restore/entity/RaceScoringRestorer.java
    - src/main/java/org/ctc/backup/restore/entity/RaceSettingsRestorer.java
    - src/main/java/org/ctc/backup/restore/entity/RaceAttachmentRestorer.java
    - src/test/java/org/ctc/backup/restore/entity/MatchdayRestorerTest.java
    - src/test/java/org/ctc/backup/restore/entity/MatchRestorerTest.java
    - src/test/java/org/ctc/backup/restore/entity/MatchScoringRestorerTest.java
    - src/test/java/org/ctc/backup/restore/entity/RaceRestorerTest.java
    - src/test/java/org/ctc/backup/restore/entity/RaceLineupRestorerTest.java
    - src/test/java/org/ctc/backup/restore/entity/RaceResultRestorerTest.java
    - src/test/java/org/ctc/backup/restore/entity/RaceScoringRestorerTest.java
    - src/test/java/org/ctc/backup/restore/entity/RaceSettingsRestorerTest.java
    - src/test/java/org/ctc/backup/restore/entity/RaceAttachmentRestorerTest.java
  modified: []
decisions:
  - "Adopt actual V1 + V3 schema column names (NOT the PLAN's <interfaces> block) where they conflict — Rule 1 deviation; see Deviations section for the column-by-column reconciliation"
  - "race_points and quali_points bound as setString — VARCHAR(500) comma-separated integrity preserved verbatim"
  - "AttachmentType enum (FILE/LINK) bound via setString — matches @Enumerated(STRING) and the V1 type VARCHAR(10) column"
  - "All nullable columns (FK UUIDs, INTs, VARCHARs, TIMESTAMPs) bound via setNull with the matching java.sql.Types code"
metrics:
  duration: "~14 min"
  completed: "2026-05-14"
---

# Phase 75 Plan 04: Match/Race Cluster Entity Restorers Summary

Nine `EntityRestorer` SPI implementations for the Match/Race cluster of the Phase 75 backup-import wipe-and-restore pipeline, using `JdbcTemplate.batchUpdate` to bypass `AuditingEntityListener` so imported `created_at`/`updated_at` survive verbatim — 24 unit tests, all green.

## Decisions Made

| Decision | Why |
| --- | --- |
| Use actual V1+V3 schema column names over the PLAN's `<interfaces>` block | PLAN listed several phantom columns (e.g., `matches.status`, `match_scorings.win_points`, `race_results.dnf/dsq/penalty_seconds`, `race_attachments.file_name/mime_type/file_size/file_path`) that do not exist in `V1__initial_schema.sql`. Implemented restorers against the real schema verified by direct migration-file read |
| `race_points` and `quali_points` bound as `setString` | Schema column type is `VARCHAR(500)` storing comma-separated integers (e.g., `"25,18,15,12,10,8,6,4,2,1"`) — must round-trip the literal CSV string, never coerce to numeric |
| Inline `setNullableUuid` / `setNullableInt` / `setNullableString` / `setNullableTimestamp` helpers per restorer (no shared utility) | Per Phase 75 CONTEXT D-08: each restorer's coercion lives next to its SQL. Repetition is intentional — keeps each restorer trivially refactorable when V8+ migrations add columns |
| Use `@JsonIdentityReference(alwaysAsId=true)` FK rendering via `row.get("<field>").asText()` directly | All MixIns (Phase 73) use `PropertyGenerator` + `alwaysAsId=true`, which renders nested FK references as raw UUID strings, NOT as `{"id":"..."}` objects. Verified via `RaceMixInTest.java:118-127` (`node.get("matchday").asText()` returns the UUID string verbatim) |
| `setObject(idx, UUID.fromString(...))` for UUIDs | Per Phase 75 PATTERNS §"UuidPacker DROPPED" + RESEARCH §5: V1 schema uses native `UUID` columns (NOT `BINARY(16)`); both H2 2.x and MariaDB 10.7+ accept `setObject(idx, java.util.UUID)` directly via JDBC 4.3 native-UUID pathway |

## Verification Results

| Check | Status | Notes |
| --- | --- | --- |
| `./mvnw -Dtest='*RestorerTest' test` (Plan 04 only — 9 restorer test classes) | PASS | 24 tests run, 24 pass |
| `./mvnw compile` BUILD SUCCESS | PASS | All restorers compile with the existing `EntityRestorer` SPI from Plan 75-01 |
| `grep 'setBytes' src/main/java/org/ctc/backup/restore/` returns 0 | PASS | No legacy `BINARY(16)` UUID packing — native `setObject(UUID)` only |
| No SQL string concatenation across the 9 new restorers | PASS | All `INSERT_SQL` are `private static final String` constants, parameterized with `?` placeholders only (T-IMPORT-SQLI defense) |
| `RaceResultRestorer` setter is lean (hottest path) | PASS | 11 binds + 2 `log.debug` — no allocations per row beyond 3 UUIDs + 2 Timestamps |
| `RaceLineupRestorer` preserves race+driver+team triple verbatim | PASS | Three consecutive `setObject(idx, UUID.fromString(...))` calls in source-JSON order |
| `RaceScoringRestorer` binds race_points + quali_points as setString | PASS | Verified by `givenSampleScoring_whenRestoreCalled_thenRacePointsBoundAsString` test, including negative assertions against `setInt`/`setArray` on the racePoints column |
| `RaceAttachmentRestorer` binds AttachmentType as setString | PASS | Verified by `givenSampleAttachment_whenRestoreCalled_thenTypeBoundAsString` + `givenLinkAttachment_whenRestoreCalled_thenLinkTypeBoundAsString` (both FILE and LINK enum variants) |
| All 9 production classes are `@Component` | PASS | Spring will auto-discover them into the orchestrator's `Map<String, EntityRestorer>` (wired in Plan 75-06) |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Schema correctness] PLAN's `<interfaces>` block listed phantom columns that do not exist in V1 schema**

- **Found during:** read_first step of Task 1 (verified `V1__initial_schema.sql` directly)
- **Issue:** The PLAN's `<interfaces>` block referenced columns that the actual V1 schema does not contain:
  - `matches.status` (enum) — schema has `bye BOOLEAN` instead; no status column
  - `matches.sort_order` — schema has no `sort_order` column; ordering is via `OrderBy("dateTime ASC NULLS LAST")` on the entity collection
  - `match_scorings.win_points / draw_points / loss_points` — schema columns are `points_win / points_draw / points_loss`
  - `races.race_format / sort_order / race_settings_id` — schema has `matchday_id, match_id, playoff_matchup_id, track_id, car_id, home_team_id, away_team_id, date_time, calendar_event_id`. The `race_format` doesn't exist; `race_settings_id` is inverted — `race_settings` owns the `race_id` FK (one-to-one inverse)
  - `race_results.finish_position / dnf / dsq / sort_order / penalty_seconds` — schema columns are `position, quali_position, fastest_lap BOOLEAN, points_race, points_quali, points_fl, points_total`. No DNF/DSQ/penalty concept in V1
  - `race_lineups.sort_order` — schema has only `race_id, driver_id, team_id` (no sort column; ordering is implicit in import order)
  - `race_scorings.bonus_points_fastest_lap` — schema column is `fastest_lap_points` (singular `INT`, not "bonus_points_fastest_lap"). Schema also has a `quali_points VARCHAR(500)` column the PLAN omitted
  - `race_settings` complete schema mismatch — PLAN listed `race_format, distance, laps, weather, time_of_day, tyre_wear, fuel_consumption, mechanical_damage, refuelling, tyre_change BOOLEAN, ghost_cars BOOLEAN`; actual schema has `race_id FK, number_of_laps INT, tyre_wear_multiplier INT, fuel_consumption_multiplier INT, refueling_speed INT, initial_fuel VARCHAR, number_of_required_pit_stops INT, time_progression_multiplier INT, weather VARCHAR, time_of_day VARCHAR, available_tyres VARCHAR, mandatory_tyres VARCHAR`. No BOOLEAN columns at all
  - `race_attachments.file_name / mime_type / file_size / file_path / uploaded_at` — schema columns are `race_id FK, type VARCHAR(10), name, url VARCHAR(1000)`. No file_size BIGINT, no mime_type, no file_path/uploaded_at
- **Fix:** Implemented all 9 restorers against the verified V1+V3 schema. Each restorer's class-level Javadoc documents the actual schema column list as a comment block so future maintainers don't re-introduce the PLAN's phantom columns
- **Files modified:** All 9 production files + all 9 test files; tests assert against the verified schema column names
- **Commits:** `78c314d`, `6834896`, `65a58ba`, `7127de1`

**2. [Rule 1 - Acceptance-criterion adjustment] PLAN acceptance criteria referenced non-existent columns**

- **Found during:** Task 1 acceptance-criterion validation
- **Issue:** Plan AC for Task 1 required `setBoolean` count >= 2 in `RaceResultRestorer` (for `dnf` + `dsq`). Real schema has only ONE BOOLEAN column (`fastest_lap`). Task 1 AC also required `MatchRestorer` to bind `status` via `setString` — there is no `status` column. Task 2 AC required `setLong` in `RaceAttachmentRestorer` for `file_size` — there is no `file_size` column
- **Fix:** Tested the actual semantics: `RaceResultRestorer` binds the single `fastest_lap` BOOLEAN via `setBoolean` (one call, sufficient); `MatchRestorer` binds the `bye` BOOLEAN via `setBoolean`; `RaceAttachmentRestorer` binds the `type` enum via `setString`. All AC intent (booleans-as-boolean, enums-as-string) is preserved against the real schema
- **Files modified:** test files (assertions match real schema)

**3. [Rule 1 - JSON FK shape] FK references render as raw UUID strings, not nested `{"id":"..."}` objects**

- **Found during:** read_first step (read `RaceMixInTest.java:118-127`)
- **Issue:** PLAN action description told implementers to use `row.get("<camelCaseField>").get("id").asText()` for FK columns ("because @JsonIdentityInfo PropertyGenerator emits a nested object"). This is incorrect — `@JsonIdentityReference(alwaysAsId=true)` (on every FK in every MixIn under `org.ctc.backup.serialization`) renders the reference as a RAW UUID string, NOT as a nested object
- **Fix:** Used `row.get("<field>").asText()` directly (no `.get("id")` chaining). Tests construct JSON fixtures using raw UUID strings for FK fields (matching the actual MixIn output) and pass
- **Files modified:** All 9 production restorers; all 9 test classes
- **Evidence:** `RaceMixInTest.java:118` asserts `node.get("matchday").asText()` returns the UUID string verbatim

### Architectural Changes

None. All deviations are schema-correctness fixes within the plan's stated scope.

### Authentication Gates

None encountered.

## TDD Gate Compliance

Each task followed the RED → GREEN cycle with separate commits:

- Task 1 RED gate: commit `78c314d` `test(75-04): add RED tests for matchday/match/race restorers` — verified by Maven compile-fail
- Task 1 GREEN gate: commit `6834896` `feat(75-04): implement matchday/match/race entity restorers` — 15 unit tests pass
- Task 2 RED gate: commit `65a58ba` `test(75-04): add RED tests for race-scoring/settings/attachment restorers` — verified by Maven compile-fail
- Task 2 GREEN gate: commit `7127de1` `feat(75-04): implement race-scoring/settings/attachment restorers` — 9 unit tests pass

REFACTOR phase: not needed — code shipped at the level the PATTERNS template prescribed; no duplication beyond the per-restorer null helpers (intentional per D-08).

## Threat Flags

None. All 9 restorers are downstream consumers of the Phase 75 orchestrator (Plan 06); their surface area is the existing `EntityRestorer` SPI from Plan 75-01. No new network endpoints, no new auth paths, no new file-access patterns, no schema changes.

## Self-Check: PASSED

- File `src/main/java/org/ctc/backup/restore/entity/MatchdayRestorer.java`: FOUND
- File `src/main/java/org/ctc/backup/restore/entity/MatchRestorer.java`: FOUND
- File `src/main/java/org/ctc/backup/restore/entity/MatchScoringRestorer.java`: FOUND
- File `src/main/java/org/ctc/backup/restore/entity/RaceRestorer.java`: FOUND
- File `src/main/java/org/ctc/backup/restore/entity/RaceLineupRestorer.java`: FOUND
- File `src/main/java/org/ctc/backup/restore/entity/RaceResultRestorer.java`: FOUND
- File `src/main/java/org/ctc/backup/restore/entity/RaceScoringRestorer.java`: FOUND
- File `src/main/java/org/ctc/backup/restore/entity/RaceSettingsRestorer.java`: FOUND
- File `src/main/java/org/ctc/backup/restore/entity/RaceAttachmentRestorer.java`: FOUND
- 9 corresponding test files: FOUND
- Commit `78c314d`: FOUND
- Commit `6834896`: FOUND
- Commit `65a58ba`: FOUND
- Commit `7127de1`: FOUND
