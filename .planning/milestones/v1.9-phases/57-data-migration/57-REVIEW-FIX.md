---
phase: 57-data-migration
fixed_at: 2026-04-27T19:35:00Z
review_path: .planning/phases/57-data-migration/57-REVIEW.md
iteration: 1
findings_in_scope: 7
fixed: 5
skipped: 2
status: partial
---

# Phase 57: Code Review Fix Report

**Fixed at:** 2026-04-27T19:35:00Z
**Source review:** .planning/phases/57-data-migration/57-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 7 (CR-01, CR-02, CR-03, WR-01, WR-02, WR-03, WR-04)
- Fixed: 5 (CR-01, CR-02, WR-01, WR-03, WR-04)
- Skipped: 2 (CR-03, WR-02 — locked decisions, documented below)
- Out of scope: 2 (IN-01, IN-02 — info-level, fix_scope=critical_warning)

Full test suite post-fix: **1072 tests, 0 failures, JaCoCo CHECK PASSED** (`./mvnw verify`)

---

## Fixed Issues

### CR-01: UNIQUE constraint collision in `migratePlayoffPhases`

**Files modified:** `src/main/java/db/migration/V4__MigrateSeasonsToPhases.java`
**Commit:** `b46aa16`
**Applied fix:** Added a D-05 fail-fast pre-check before each INSERT in `migratePlayoffPhases`:
queries `COUNT(*) FROM season_phases WHERE season_id = ? AND phase_type = 'PLAYOFF'`
and throws `FlywayException` with the offending playoff id if a PLAYOFF phase already exists
for that season. This prevents silent `DataIntegrityViolationException` on the unique constraint
and makes partial-run idempotency failures explicit and diagnosable.

---

### CR-02: Silent data-loss in `migratePhaseTeams` — orphaned season_teams silently skipped

**Files modified:** `src/main/java/db/migration/V4__MigrateSeasonsToPhases.java`
**Commit:** `608ab1b`
**Applied fix:** Replaced the silent `continue` with `throw new FlywayException(...)` per D-05
fail-fast pattern. Added `insertCount` counter so the log line reports actual inserts, not the
total `seasonTeams.size()` (which previously reported misleading counts when rows were skipped).

---

### WR-01: `migrateMatchdayFKs` correlated UPDATE silently sets `phase_id=NULL` for orphan matchdays

**Files modified:** `src/main/java/db/migration/V4__MigrateSeasonsToPhases.java`
**Commit:** `9b5973a`
**Applied fix:** After the correlated UPDATE, added a D-05 fail-fast post-check:
`SELECT COUNT(*) FROM matchdays WHERE phase_id IS NULL`. If the count is > 0, throws
`FlywayException` identifying the number of orphan matchdays. This surfaces the root cause
(orphan `matchday.season_id`) instead of a generic constraint violation at the NOT NULL flip step.

---

### WR-03: `V4MigrationSmokeIT` assertion vacuous on dev profile

**Files modified:** `src/test/java/db/migration/V4MigrationSmokeIT.java`
**Commit:** `7529d6a`
**Applied fix:** Added `@Autowired JdbcTemplate` and a `@BeforeEach seedSmokeTestData()` method
that inserts one Season row + one REGULAR SeasonPhase row directly via JdbcTemplate (using
`Phase57-Smoke-` prefix per CLAUDE.md test isolation rule). Added a second meaningful test
`givenSeasonWithBackfilledPhase_whenLoadedViaRepository_thenPhasesCollectionIsNotEmpty()` that
asserts `seasonRepository.findById(SMOKE_SEASON_ID)` returns a season with exactly 1 REGULAR
phase — directly testing the D-18 invariant via JPA mapping. The original `whenContextLoads_*`
test is preserved with a corrected non-vacuous `isNotNull()` assertion. Both tests run under
`@Transactional` so inserts are rolled back after each test. Both tests pass (2 tests, 0 failures).

---

### WR-04: IT test comment says "DB DEFAULT" but production code uses explicit `"LEAGUE"` value

**Files modified:** `src/test/java/db/migration/V4MigrateSeasonsToPhasesIT.java`
**Commit:** `c98cfe9`
**Applied fix:** Updated the IT test comment on the `format` assertion from
`// D-08: DB DEFAULT` to `// D-08: explicit value 'LEAGUE' per V4 migration (not DB DEFAULT)`.
Production code (`V4__MigrateSeasonsToPhases.java` line 131) correctly uses the explicit
`"LEAGUE"` string per CONTEXT.md D-08 — the IT comment was the artifact out of sync.

---

## Skipped Issues

### CR-03: DDL inside Flyway-managed transaction on MariaDB — NOT NULL flip commits independently

**File:** `src/main/java/db/migration/V4__MigrateSeasonsToPhases.java:190-205`
**Reason:** Locked by CONTEXT.md D-04. The decision explicitly documents: "canExecuteInTransaction()=true;
MariaDB DDL implicitly commits intermediate steps — for our small data size this trade-off is
acceptable." Splitting the NOT NULL flip to a separate V5 migration would directly contradict D-04.
The MariaDB non-atomicity risk is acknowledged as an accepted trade-off for the small dataset in scope.
A manual sanity-check item for MariaDB has been persisted in 57-HUMAN-UAT.md (per D-04 rationale).
**DO NOT fix** without first revisiting D-04 in CONTEXT.md.

---

### WR-02: `flipNotNullConstraints` empty-DB guard too coarse — does not verify all `phase_id` columns populated

**File:** `src/main/java/db/migration/V4__MigrateSeasonsToPhases.java:190-195`
**Reason:** Phase-59 integration tradeoff. Replacing the empty-seasons guard with a pre-flight
`COUNT(*) WHERE phase_id IS NULL` check would break the dev-profile boot: on an empty H2 database
after V4, there are zero matchdays and zero playoffs, so the pre-flight check passes trivially —
but the DevDataSeeder (which runs after Flyway, i.e., after the NOT NULL constraint is set) would
then attempt to insert matchdays WITHOUT a phase_id, violating the newly-set NOT NULL constraint.
The current empty-seasons guard keeps the column nullable on empty databases until Phase 59
rebuilds the seeder on the new Phase model. Re-evaluate in Phase 59 after DevDataSeeder is ported
to create SeasonPhase rows before inserting matchdays.

---

## Out of Scope (Info — not in fix_scope)

- **IN-01:** `toUUID` helper duplicated between migration class and IT — extract to shared utility.
  _(fix_scope=critical_warning; info findings not actioned in this iteration)_
- **IN-02:** Stale "TDD-RED state" comment in `V4MigrateSeasonsToPhasesIT` class Javadoc.
  _(fix_scope=critical_warning; info findings not actioned in this iteration)_

---

_Fixed: 2026-04-27T19:35:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
