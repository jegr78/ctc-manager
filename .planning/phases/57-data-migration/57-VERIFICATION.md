---
phase: 57-data-migration
verified: 2026-04-27T18:00:00Z
status: passed
score: 5/5 must-haves verified
overrides_applied: 0
re_verification:
  re_verified: 2026-05-07T00:00:00Z
  previous_status: human_needed
  previous_score: 5/5
  gaps_closed:
    - "MariaDB MODIFY COLUMN UUID NOT NULL branch in V4.flipNotNullConstraints — covered de-facto by Phase 61 UAT-03 docker-compose smoke run (commit bed0ffd) and CI gate workflow .github/workflows/mariadb-migration-smoke.yml (added in Phase 61)"
  gaps_remaining: []
  regressions: []
human_verification: []
gaps: []
deferred: []
---

# Phase 57: Data Migration Verification Report

**Phase Goal:** All existing production data is correctly mapped into the new schema — every existing season has a REGULAR phase, every existing playoff has a PLAYOFF phase, every matchday is re-keyed to its phase, and phase rosters are populated — with old FK columns still present as a bridge for backward-compatible code.
**Verified:** 2026-04-27T18:00:00Z
**Status:** passed (re-verified 2026-05-07 — see Re-Verification Summary)
**Re-verification:** Yes — 2026-05-07 backfill (Phase 63) closes the MariaDB UAT gap via Phase 61 UAT-03 + CI smoke gate

## Re-Verification Summary

This phase originally verified `human_needed` because the V4 `flipNotNullConstraints` MariaDB `MODIFY COLUMN ... UUID NOT NULL` branch could not be exercised in CI (CI uses H2 only). Phase 63 closes this gap with two pieces of de-facto evidence:

| Item | Previous Status | Current Status | Evidence |
|------|----------------|----------------|----------|
| MariaDB `MODIFY COLUMN UUID NOT NULL` branch in `flipNotNullConstraints` | ⚠️ human_needed (CI uses H2 only) | ✓ COVERED (de-facto) | **Phase 61 UAT-03 (commit `bed0ffd`)** ran the docker-compose MariaDB smoke flow against the real production-shape MariaDB image — V4 applied successfully, all 5 data-integrity SELECT checks passed, `DESCRIBE matchdays` and `DESCRIBE playoffs` confirmed `phase_id NOT NULL` post-migration. **CI gate `.github/workflows/mariadb-migration-smoke.yml`** (added in Phase 61) replays every Flyway migration including V4 against MariaDB on every push and PR — the previously CI-untested code path now has automated regression coverage on every change. |

**Gap closure verdict:** The original `human_verification` test (Manual MariaDB Verification Checklist from 57-03-SUMMARY.md steps 1-7) is now de-facto satisfied by automated coverage. No regressions detected since the original 2026-04-27 verification — the Observable Truths table below stands unchanged.

**Audit trail:** `.planning/v1.9-MILESTONE-AUDIT.md` line 31 + line 80-83 explicitly endorse this re-verification path.

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Every existing Season row gets exactly one `season_phases` row with `phase_type = REGULAR` | VERIFIED | `migrateRegularPhases` loops `SELECT * FROM seasons`, inserts one row per season with `phase_type='REGULAR'`, `sort_index=0`, `layout='LEAGUE'`, fields 1:1 copied. Test method `givenLegacySeasons_whenMigrationRuns_thenEachSeasonHasOneRegularPhase` asserts `COUNT(*) = 3` for 3 seeded seasons and validates all field mappings (format, layout, sort_index, total_rounds, legs, event_duration_minutes, label=null, race/match scoring IDs). Passes GREEN. |
| 2 | Every existing Playoff row is linked to a newly created PLAYOFF SeasonPhase via `playoff.phase_id` | VERIFIED | `migratePlayoffPhases` JOINs playoffs to seasons, inserts `season_phases` row with `phase_type='PLAYOFF'`, `sort_index=10`, `layout='BRACKET'`, `format='LEAGUE'`, `legs=1`, `label=playoff.name`, then UPDATEs `playoffs.phase_id`. Test `givenLegacyPlayoff_whenMigrationRuns_thenPlayoffLinkedViaPhaseId` asserts 1 PLAYOFF phase, validates all D-08 fields, confirms `playoffs.phase_id` is not null. Passes GREEN. |
| 3 | Every existing Matchday row has `phase_id` populated pointing to the REGULAR phase of its season | VERIFIED | `migrateMatchdayFKs` executes a single correlated `UPDATE matchdays m SET phase_id = (SELECT sp.id FROM season_phases sp WHERE sp.season_id = m.season_id AND sp.phase_type = 'REGULAR')`. Test `givenLegacyMatchdays_whenMigrationRuns_thenAllMatchdaysHavePhaseId` asserts `COUNT(*) WHERE phase_id IS NULL = 0`, total count unchanged at 4, and all phase_ids point to REGULAR phases of the correct season. Passes GREEN. |
| 4 | `phase_teams` contains one row per `season_team` entry, associated with that season's REGULAR phase and group_id NULL | VERIFIED | `migratePhaseTeams` iterates `SELECT * FROM season_teams`, looks up REGULAR phase ID, inserts into `phase_teams` with `group_id=NULL`. Test `givenLegacySeasonTeams_whenMigrationRuns_thenPhaseTeamsPopulated` asserts count = 4, no group_id set, all phase_ids correctly linked via JOIN. Passes GREEN. |
| 5 | Old bridge columns (`matchday.season_id`, `playoff.season_id`) remain intact | VERIFIED | V4 migration code never touches `matchdays.season_id` or `playoffs.season_id`. `migrateMatchdayFKs` only updates `phase_id`; `migratePlayoffPhases` only inserts into `season_phases` and updates `playoffs.phase_id`. Test `givenLegacyData_whenMigrationRuns_thenBridgeColumnsRemainIntact` asserts `COUNT(*) WHERE season_id IS NOT NULL = 4` (matchdays) and `= 1` (playoffs), `playoff_seasons` table still exists, and Season 1 legacy columns (format, total_rounds, legs) remain populated. Passes GREEN. |

**Score:** 5/5 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/java/db/migration/V4__MigrateSeasonsToPhases.java` | Production Flyway Java migration, extends BaseJavaMigration, 5 private steps | VERIFIED | 226 lines; `public class V4__MigrateSeasonsToPhases extends BaseJavaMigration`; all 5 private methods present; LoggerFactory (not @Slf4j); no getChecksum/getVersion/getDescription overrides; `SingleConnectionDataSource(connection, true)`. |
| `src/test/java/db/migration/V4MigrateSeasonsToPhasesIT.java` | Programmatic Flyway IT harness with 6 locked test methods | VERIFIED | 355 lines; `EmbeddedDatabaseBuilder` + two-phase `Flyway.configure()` (target "3" then "4"); all 6 D-16 method names present; D-17 seed (3 seasons including empty, 4 matchdays, 4 season_teams, 1 playoff); `toUUID()` helper; no @SpringBootTest. |
| `src/test/java/db/migration/V4MigrationSmokeIT.java` | @SpringBootTest smoke, SeasonRepository.findAll(), context loads | VERIFIED | 64 lines; `@SpringBootTest(classes = CtcManagerApplication.class) @ActiveProfiles("dev") @Transactional`; `seasonRepository.findAll()`; `getPhases().isNotNull()` (deviation from plan's isNotEmpty — justified, see Known Risks). |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `V4__MigrateSeasonsToPhases.java` | `V3__add_season_phase_tables.sql` | `INSERT INTO season_phases` targets V3-added columns; `UPDATE matchdays SET phase_id` targets V3-added column | WIRED | INSERT column list exactly matches V3 schema. Two `INSERT INTO season_phases` calls confirmed (lines 79, 115). Correlated UPDATE on `matchdays.phase_id` (line 141). |
| `V4__MigrateSeasonsToPhases.java` | `V4MigrateSeasonsToPhasesIT.java` | Flyway classpath scan discovers V4 when test calls `Flyway.configure().target("4").migrate()` | WIRED | 6 tests run: 6, Failures: 0 confirmed by live test run `./mvnw test -Dtest=V4MigrateSeasonsToPhasesIT`. |
| `V4MigrationSmokeIT.java` | `V4__MigrateSeasonsToPhases.java` | Spring Boot autoload runs Flyway V1+V2+V3+V4 before context start | WIRED | Test passes: 1 test run, Failures: 0; context loads without exception confirming V4 executed cleanly. |
| `V4MigrationSmokeIT.java` | `SeasonRepository.java` | `seasonRepository.findAll()` invoked in test method | WIRED | `grep -c 'seasonRepository.findAll'` = 1 in smoke test. |

### Data-Flow Trace (Level 4)

The production V4 migration class writes to real DB tables via JdbcTemplate — there are no UI rendering components or lazy props to trace. Data flow verification is covered entirely by the programmatic IT harness (V4MigrateSeasonsToPhasesIT) which seeds legacy data, runs V4, then asserts exact counts and field values via direct JDBC queries against the same H2 instance.

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `V4__MigrateSeasonsToPhases.java:migrateRegularPhases` | `seasons` list from `queryForList("SELECT * FROM seasons")` | JDBC query against `seasons` table | Yes — iterates real rows, INSERTs one `season_phases` row per season | FLOWING |
| `V4__MigrateSeasonsToPhases.java:migratePlayoffPhases` | `playoffs` list from JOIN query | JDBC query against `playoffs JOIN seasons` | Yes — INSERTs PLAYOFF phase and UPDATEs `playoffs.phase_id` | FLOWING |
| `V4__MigrateSeasonsToPhases.java:migrateMatchdayFKs` | `count` from correlated UPDATE | Single UPDATE statement, no query needed | Yes — correlated UPDATE writes `phase_id` on all matchdays | FLOWING |
| `V4__MigrateSeasonsToPhases.java:migratePhaseTeams` | `seasonTeams` from `SELECT * FROM season_teams` | JDBC query | Yes — INSERTs one `phase_teams` row per season_team | FLOWING |

### Behavioral Spot-Checks

All spot-checks executed against live codebase.

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| 6 V4 IT tests GREEN | `./mvnw test -Dtest=V4MigrateSeasonsToPhasesIT` | Tests run: 6, Failures: 0, Errors: 0 | PASS |
| Smoke test GREEN | `./mvnw test -Dtest=V4MigrationSmokeIT` | Tests run: 1, Failures: 0, Errors: 0 | PASS |
| Both Phase 57 ITs GREEN | `./mvnw test -Dtest=V4MigrateSeasonsToPhasesIT,V4MigrationSmokeIT` | Tests run: 7, Failures: 0, Errors: 0 | PASS |
| Full verify pipeline GREEN | `./mvnw verify` | Tests run: 1072, Failures: 0, "All coverage checks have been met." BUILD SUCCESS | PASS |
| JaCoCo >= 82% | Extract from `target/site/jacoco/jacoco.csv` | LINE_COVERED=5040 LINE_MISSED=680 TOTAL=5720 — **88.11%** | PASS |
| V1/V2/V3 SQL unmodified | `git diff HEAD -- src/main/resources/db/migration/V1..V3` | 0 lines changed | PASS |
| V4 is public, extends BaseJavaMigration, no @Slf4j | grep checks | `public class V4__MigrateSeasonsToPhases extends BaseJavaMigration` — @Slf4j count = 0 — LoggerFactory present | PASS |
| 5 private methods in correct order | grep + line inspection | migrateRegularPhases(l.45) → migratePlayoffPhases(l.46) → migrateMatchdayFKs(l.47) → migratePhaseTeams(l.48) → flipNotNullConstraints(l.49) | PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| MIGR-02 | 57-01, 57-02, 57-03 | Data migration: 1 REGULAR phase per existing season, format/scoring/rounds/legs/dates copied | SATISFIED | `migrateRegularPhases` iterates all seasons, inserts one REGULAR phase per row with 1:1 field copy. Verified by SC1 test. |
| MIGR-03 | 57-01, 57-02, 57-03 | Data migration: 1 PLAYOFF phase per existing Playoff, FK re-linked | SATISFIED | `migratePlayoffPhases` inserts PLAYOFF phase with D-08 defaults, updates `playoffs.phase_id`. Verified by SC2 test. |
| MIGR-04 | 57-01, 57-02, 57-03 | Data migration: `matchday.phase_id` set to REGULAR phase | SATISFIED | `migrateMatchdayFKs` correlated UPDATE sets all matchday `phase_id` values. Verified by SC3 test (COUNT(*) WHERE phase_id IS NULL = 0). |
| MIGR-05 | 57-01, 57-02, 57-03 | Data migration: `phase_teams` derived from `season_teams` (LEAGUE layout, group NULL) | SATISFIED | `migratePhaseTeams` inserts one `phase_teams` row per `season_teams` row with `group_id=NULL`. Verified by SC4 test. |

All 4 phase-assigned requirements (MIGR-02, MIGR-03, MIGR-04, MIGR-05) are covered across all three plans.

**Orphaned requirements check:** MIGR-06 is assigned to Phase 61 (cleanup migration dropping old columns) — not a Phase 57 concern. MIGR-07 (additive migrations, V1/V2 unmodified) — verified: git diff confirms V1/V2/V3 unchanged.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `V4__MigrateSeasonsToPhases.java` | 162-165 | Silent `continue` in `migratePhaseTeams` when `phaseId == null`; log message reports `seasonTeams.size()` even when rows are skipped | Warning | On CTC production data this path is never hit (all season_teams have valid season_ids). Contradicts D-05 fail-fast pattern. Documented as CR-02 in REVIEW.md. |
| `V4MigrationSmokeIT.java` | 62 | `assertThat(s.getPhases()).isNotNull()` instead of plan-specified `isNotEmpty()` | Info | Intentional deviation — documented as Rule 1 fix in 57-03-SUMMARY.md. DevDataSeeder runs after Flyway so dev H2 seasons have empty phases. `isNotNull()` is the valid invariant on dev profile; `isNotEmpty()` is valid on prod/local. Primary smoke value (Spring context loads, V4 runs) is fully preserved. |

**Non-blocking review concerns from REVIEW.md** (CR-01, CR-02, CR-03 — flagged but not phase blockers):

**CR-01 (Warning):** `uk_season_phase_type UNIQUE(season_id, phase_type)` in V3 means the migration is non-idempotent. A partial run followed by a retry would fail at `migratePlayoffPhases`. On CTC data, each season has at most one playoff (enforced by V1's `uk_playoff_season UNIQUE(season_id)` on playoffs), so this collision cannot occur in practice. However, idempotency is not guaranteed.

**CR-02 (Warning):** Silent skip in `migratePhaseTeams` contradicts D-05 fail-fast. Log message misleads by reporting total season_teams count regardless of skips. Low-risk on CTC data; addressable in a future V5 refinement or via `/gsd-code-review-fix 57`.

**CR-03 (Warning):** On MariaDB, DDL in `flipNotNullConstraints` causes an implicit commit, making the "single transaction" claim in the class Javadoc incorrect. Steps 1–4 DML would be committed before the NOT NULL flip; a second ALTER failure would leave the DB partially migrated with no rollback. On H2 (test environment), DDL is transactional and this gap does not manifest. This is the primary reason human MariaDB verification is required before prod merge.

### Human Verification Required

#### 1. MariaDB NOT NULL Flip Verification

**Test:** Execute the 7-step Manual MariaDB Verification Checklist from `57-03-SUMMARY.md` against a local MariaDB instance before merging to prod.

**Steps:**
1. `docker compose down -v && docker compose up db -d` — fresh MariaDB without V4
2. Set `spring.flyway.target=3` in `application-local.yml`, start app once to apply V1–V3, stop
3. Insert seed data matching pre-V4 state (or use an existing prod data dump)
4. Remove `target=3` override, restart: `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`
5. Confirm Flyway log: "Successfully applied 1 migration to schema ..., now at version v4"
6. Connect to MariaDB: `DESCRIBE matchdays` — `phase_id` row must show `Null=NO`; `DESCRIBE playoffs` — same
7. Run integrity checks:
   - `SELECT COUNT(*) FROM season_phases WHERE phase_type = 'REGULAR'` = `SELECT COUNT(*) FROM seasons`
   - `SELECT COUNT(*) FROM season_phases WHERE phase_type = 'PLAYOFF'` = `SELECT COUNT(*) FROM playoffs`
   - `SELECT COUNT(*) FROM matchdays WHERE phase_id IS NULL` = 0
   - `SELECT COUNT(*) FROM phase_teams` = `SELECT COUNT(*) FROM season_teams`
   - `SELECT COUNT(*) FROM matchdays WHERE season_id IS NULL` = 0

**Expected:** All 7 steps pass without exception; data integrity queries return the expected values.

**Why human:** The `flipNotNullConstraints` MariaDB branch (`ALTER TABLE matchdays MODIFY COLUMN phase_id UUID NOT NULL`) is statically present in the code and structurally correct, but CI runs H2 only. The MariaDB implicit-commit behavior (CR-03) means the DDL is not transactional — this must be confirmed to work correctly on a real MariaDB instance. Additionally, `UUID NOT NULL` type support in MariaDB 10.7+ (RESEARCH.md assumption A3) cannot be verified in CI.

---

### Gaps Summary

No automated gaps. All 5 ROADMAP success criteria are verified by the codebase evidence:
- SC1–SC5 are each covered by a dedicated @Test method that runs against a real H2 database with seeded legacy data, exercising the exact SQL paths in V4.
- Full test suite (1072 tests) is GREEN.
- JaCoCo line coverage is 88.11% — well above the 82% minimum gate.
- V1/V2/V3 migration files are unmodified (Flyway checksum integrity preserved).
- Three commits documented (f4d8004, e7abe8a, e7352b5) are present in the git log.

One human verification item remains: MariaDB runtime verification of the DDL branch in `flipNotNullConstraints`, which cannot be exercised in the H2-only CI environment.

---

_Verified: 2026-04-27T18:00:00Z_
_Verifier: Claude (gsd-verifier)_
