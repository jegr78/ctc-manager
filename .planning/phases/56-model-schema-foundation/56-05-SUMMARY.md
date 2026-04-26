---
phase: 56-model-schema-foundation
plan: 05
subsystem: test.persistence
tags:
  - integration-test
  - persistence
  - jpa
  - constraint
  - milestone-v1.9
requirements:
  - MODEL-01
  - MODEL-02
  - MODEL-03
  - MODEL-04
  - MODEL-05
  - MODEL-06
  - MIGR-01
dependency_graph:
  requires:
    - 56-02 (SeasonPhase, SeasonPhaseGroup, PhaseTeam entities + repositories)
    - 56-03 (V3 migration with uk_season_phase_type, uk_phase_team)
    - 56-04 (Season.phases, Matchday.phase, Matchday.group, Playoff.phase bidirectional fields)
  provides:
    - Persistence + audit-column proof for the three new entities (SeasonPhase, SeasonPhaseGroup, PhaseTeam)
    - DB-level UNIQUE constraint enforcement proof for uk_season_phase_type and uk_phase_team
    - Round-trip proof for Season.phases, Matchday.phase, Playoff.phase (Plan 04 deliverables)
    - Project precedent — first @SpringBootTest @ActiveProfiles("dev") @Transactional integration test class for the phase model
  affects:
    - Phase 57 data migration (planners can rely on persisted constraints when designing the backfill)
    - Phase 58 service layer (SeasonPhaseService can rely on DB-level uniqueness as belt-and-suspenders for its own guards)
tech-stack:
  added: []
  patterns:
    - "@SpringBootTest + @ActiveProfiles(\"dev\") + @Transactional class-level rollback per project precedent (BaseEntityAuditTest analog)"
    - "@PersistenceContext EntityManager injection + flush() + clear() to force a real DB reload of bidirectional collections inside a single @Transactional test method"
    - "saveAndFlush(...) on the repository under test to surface DataIntegrityViolationException inside the test scope (mitigates T-56-19)"
    - "AssertJ assertThatThrownBy(...).isInstanceOf(DataIntegrityViolationException.class) for DB-level constraint checks"
    - "Test-data prefix Phase56-Test / Phase56-Uniq per CLAUDE.md \"Isolate Test Data Completely\" rule"
key-files:
  created:
    - src/test/java/org/ctc/domain/model/SeasonPhaseEntityIntegrationTest.java
    - src/test/java/org/ctc/domain/model/PhaseTeamUniquenessIntegrationTest.java
  modified: []
decisions:
  - "Used @SpringBootTest + @ActiveProfiles(\"dev\") + @Transactional, not @DataJpaTest (no @DataJpaTest precedent in this codebase per 56-PATTERNS.md)"
  - "Injected EntityManager via @PersistenceContext to call flush() + clear() before reloading — necessary because Hibernate does not auto-sync the inverse side (Season.phases / SeasonPhase.groups) when only the owning side is saved"
  - "Used saveAndFlush(...) on the repository under test in both uniqueness tests so the DataIntegrityViolationException surfaces inside the test method (rather than at transaction commit time after the test returns)"
  - "Test 1 includes a positive control assertion (PLAYOFF on same season succeeds) before the negative duplicate-REGULAR assertion — proves uk_season_phase_type discriminates by tuple, not by season alone"
  - "Test 2 sets a DIFFERENT group_id on the duplicate PhaseTeam — proves uk_phase_team rejects regardless of group_id (only (phase_id, team_id) matters)"
metrics:
  tasks_completed: 2
  files_created: 2
  files_modified: 0
  duration_minutes: 13
  completed_at: 2026-04-26
  total_tests_run: 1072
  failures: 0
  errors: 0
  skipped: 0
  jacoco_line_coverage: "85.62%"
---

# Phase 56 Plan 05: Persistence and Constraint Integration Tests Summary

**One-liner:** Two new `@SpringBootTest @ActiveProfiles("dev") @Transactional` integration tests close the verification loop for Phase 56 — eight test methods together prove that the three new entities persist correctly via JPA, that BaseEntity audit columns populate, that the bidirectional `Season.phases` / `Matchday.phase` / `Playoff.phase` fields round-trip, and that the V3-declared UNIQUE constraints `uk_season_phase_type` and `uk_phase_team` actually reject duplicates at the database level.

## What Was Built

### `SeasonPhaseEntityIntegrationTest` (six test methods)

Persistence + audit + bidirectional round-trip for the new entities and the Plan 04 fields. All six pass on `./mvnw test -Dtest=SeasonPhaseEntityIntegrationTest`.

| # | Method | Verifies |
|---|--------|----------|
| 1 | `givenNewSeasonPhase_whenSaved_thenAuditFieldsArePopulated` | BaseEntity inheritance — `createdAt` / `updatedAt` populated on insert; PhaseType / PhaseLayout enums round-trip |
| 2 | `givenSeasonPhaseWithGroups_whenReloaded_thenGroupsCollectionIsOrderedBySortIndex` | `SeasonPhase.groups` `@OneToMany` collection loads in `@OrderBy("sortIndex ASC")` order after `flush()` + `clear()` |
| 3 | `givenPhaseTeamWithoutGroup_whenSaved_thenGroupIsNull` | `PhaseTeam.group` is nullable (D-01) — a phase team can exist without a group assignment |
| 4 | `givenSeasonWithPhases_whenReloaded_thenSeasonPhasesCollectionContainsTheSavedPhase` | `Season.phases` bidirectional collection (Plan 04) is populated correctly on reload |
| 5 | `givenMatchdayWithPhase_whenSaved_thenPhaseIsReachableOnReload` | `Matchday.phase` (Plan 04, REQ MODEL-05) — FK persists and reloads, `Matchday.season` is preserved alongside |
| 6 | `givenPlayoffWithPhase_whenSaved_thenPhaseIsReachableOnReload` | `Playoff.phase` (Plan 04, REQ MODEL-06) — FK persists and reloads |

### `PhaseTeamUniquenessIntegrationTest` (two test methods)

DB-level UNIQUE constraint enforcement. Both pass on `./mvnw test -Dtest=PhaseTeamUniquenessIntegrationTest`.

| # | Method | Verifies | Constraint |
|---|--------|----------|-----------|
| 1 | `givenSeasonWithRegularPhase_whenSecondRegularPhaseInserted_thenViolatesUniqueSeasonPhaseType` | A second REGULAR phase on the same season is rejected; PLAYOFF on the same season is positively allowed (positive control) | `uk_season_phase_type (season_id, phase_type)` — REQ MODEL-02 |
| 2 | `givenPhaseTeamForTeamA_whenSecondPhaseTeamForTeamAInserted_thenViolatesUniquePhaseTeam` | A second PhaseTeam with the same (phase, team) is rejected even when the duplicate is placed in a different group | `uk_phase_team (phase_id, team_id)` — REQ MODEL-04 |

Both methods use `saveAndFlush(...)` and `assertThatThrownBy(...).isInstanceOf(DataIntegrityViolationException.class)` per the plan's prescribed idiom.

## Key Decisions Made

- **Test harness:** `@SpringBootTest @ActiveProfiles("dev") @Transactional` — exactly as 56-PATTERNS.md flagged (no `@DataJpaTest` precedent in this repo, so the canonical persistence-test harness is full Spring context with H2 in-memory and class-level transactional rollback).
- **`EntityManager` injection for reload tests:** Tests 2 and 4 of the entity test reload via `seasonPhaseRepository.findById(...)` after writes. Inside a single `@Transactional` method Hibernate does not auto-sync the inverse `Season.phases` / `SeasonPhase.groups` collection when only the owning side (`SeasonPhase.season` / `SeasonPhaseGroup.phase`) is set during `save(...)`. Required `entityManager.flush() + entityManager.clear()` before the reload so `findById` actually re-reads from the DB. Used `@PersistenceContext` (jakarta.persistence) injection.
- **`saveAndFlush(...)` for constraint tests:** A plain `save(...)` defers the SQL INSERT to transaction commit time, which happens *after* the test method returns. The `DataIntegrityViolationException` would then fire during teardown, not inside the test method, and `assertThatThrownBy` would never see it. `saveAndFlush(...)` forces the INSERT immediately so the constraint violation surfaces inside the test scope.
- **Different group on duplicate PhaseTeam:** Test 2 deliberately attaches the duplicate to a *different* `group_id`. This proves the V3-declared UNIQUE is on `(phase_id, team_id)` only, not on `(phase_id, team_id, group_id)`, matching MODEL-04 wording.
- **Test-data prefixes:** `Phase56-Test` (entity test) and `Phase56-Uniq` (uniqueness test) per CLAUDE.md "Isolate Test Data Completely" — combined with the class-level `@Transactional` rollback this guarantees no leakage into the dev seed data.

## Verification Results

| Check | Expected | Actual | Status |
|-------|----------|--------|--------|
| `SeasonPhaseEntityIntegrationTest` exists | yes | yes | PASS |
| `PhaseTeamUniquenessIntegrationTest` exists | yes | yes | PASS |
| `@SpringBootTest` count (entity test) | 1 | 1 | PASS |
| `@SpringBootTest` count (uniqueness test) | 1 | 1 | PASS |
| `@ActiveProfiles("dev")` count (each file) | 1 | 1 | PASS |
| `@Transactional` count (each file) | >=1 | 2 | PASS |
| Given-When-Then methods (entity test) | >=6 | 6 | PASS |
| Given-When-Then methods (uniqueness test) | >=2 | 2 | PASS |
| `// given` / `// when` / `// then` (entity test) | >=5 each | 6 each | PASS |
| `// when / then` combined (uniqueness test) | >=2 | 2 | PASS |
| `import DataIntegrityViolationException` | 1 | 1 | PASS |
| `import assertThatThrownBy` | 1 | 1 | PASS |
| `assertThatThrownBy` calls (uniqueness test) | >=2 | 3 | PASS |
| `DataIntegrityViolationException.class` (uniqueness test) | >=2 | 2 | PASS |
| `saveAndFlush` (uniqueness test) | >=2 | 9 | PASS |
| `setPhase(phase)` / `matchday.setPhase` (entity test) | >=2 | 2 | PASS |
| `PhaseType.REGULAR` (each file) | >=1 | 7 / 3 | PASS |
| `PhaseLayout` (entity test) | >=1 | 9 | PASS |
| `./mvnw -q test -Dtest=SeasonPhaseEntityIntegrationTest` exit | 0 | 0 (6/0/0) | PASS |
| `./mvnw -q test -Dtest=PhaseTeamUniquenessIntegrationTest` exit | 0 | 0 (2/0/0) | PASS |
| `./mvnw verify` exit | 0 | 0 | PASS |
| Surefire totals | green | 1072 / 0 / 0 | PASS |
| JaCoCo line coverage | >=82% | 85.62% | PASS |

## Deviations from Plan

### `[Rule 1 - Bug]` Reload tests needed flush() + clear() to see persisted state

- **Found during:** Task 1 first test run (entity test methods 2 and 4 failed with empty collections on reload).
- **Issue:** Inside a single `@Transactional` method, Hibernate retains the parent (`Season` / `SeasonPhase`) in the persistence context with its empty collection field. When only the owning side (the child's `@ManyToOne`) is populated via `seasonPhaseRepository.save(...)`, Hibernate does **not** automatically synchronize the inverse `@OneToMany` collection on the parent. `seasonRepository.findById(...)` then returns the cached parent with the empty collection — even though the row exists in DB.
- **Fix:** Injected `EntityManager` via `@PersistenceContext` and called `entityManager.flush() + entityManager.clear()` before the reload step in the affected test methods. This forces the writes to DB and evicts the cached parents so the subsequent `findById` re-reads from the DB and lazily loads the (now correctly-mapped) inverse collection.
- **Files modified:** only `src/test/java/org/ctc/domain/model/SeasonPhaseEntityIntegrationTest.java` (test code only — no production code changed).
- **Commit:** `b079c36` (final committed version of the test contains the fix).
- **Rationale:** Pure test-correctness fix. No production code or schema change. Bidirectional sync helpers on the entities (`Season.addPhase(...)` / `SeasonPhase.addGroup(...)`) exist on `Season` (Plan 04) but the test deliberately exercises the persistence-then-reload path rather than the in-memory bidirectional helper path — this is what end-of-plan integration testing should look like.

No other deviations. No CLAUDE.md violations. No architectural decisions required (no Rule 4 trigger).

## Auth Gates

None — purely persistence-layer testing on H2 in-memory. No external service calls.

## Threat Surface Scan

No new attack surface introduced. Test code only. The two threats explicitly itemized in the plan's `<threat_model>` are mitigated as planned:

- **T-56-18** (test data leaking into shared dev data) — *mitigated*: every entity uses the `Phase56-Test` / `Phase56-Uniq` prefix and `@Transactional` rolls each test back. Verified manually: after `./mvnw verify`, no `Phase56-*` rows exist in the dev seed data (impossible by design, since the H2 DB is in-memory and recreated per JVM).
- **T-56-19** (test passes by silently skipping the constraint check) — *mitigated*: both uniqueness tests use `saveAndFlush(...)` (forces SQL INSERT inside the test scope) and assert `DataIntegrityViolationException.class` exactly. A green test conclusively proves the DB rejected the duplicate (not that the test never reached the SQL layer).

No additional threats discovered during execution.

## Known Stubs

None. Both tests are fully wired against real entities, real H2 DB, real Spring context. No mocks, no `@MockBean`, no `@Disabled`, no `assumeXyz` skips.

## Commits

| Hash | Message |
|------|---------|
| `b079c36` | `test(56-05): add SeasonPhaseEntityIntegrationTest` |
| `c8f715e` | `test(56-05): add PhaseTeamUniquenessIntegrationTest` |

Plan-metadata commit (this SUMMARY.md) follows.

## Self-Check: PASSED

Files verified to exist on disk:
- `src/test/java/org/ctc/domain/model/SeasonPhaseEntityIntegrationTest.java` — FOUND
- `src/test/java/org/ctc/domain/model/PhaseTeamUniquenessIntegrationTest.java` — FOUND
- `.planning/phases/56-model-schema-foundation/56-05-SUMMARY.md` — FOUND (this file)

Commits verified to exist in `git log`:
- `b079c36` — `test(56-05): add SeasonPhaseEntityIntegrationTest` — FOUND
- `c8f715e` — `test(56-05): add PhaseTeamUniquenessIntegrationTest` — FOUND

Build / coverage:
- `./mvnw verify` — exit 0
- Surefire — 1072 / 0 / 0 / 0 (run / fail / err / skip)
- JaCoCo line coverage — 85.62% (>= 82% threshold)

No production code modified. No Flyway migrations modified. No STATE.md / ROADMAP.md / REQUIREMENTS.md modified (per parallel-executor protocol).

---
*Phase: 56-model-schema-foundation*
*Completed: 2026-04-26*
