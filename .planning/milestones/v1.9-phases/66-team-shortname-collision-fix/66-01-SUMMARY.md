---
phase: 66
plan: 01
slug: team-shortname-collision-fix
subsystem: dataimport
tags: [hotfix, repository, resolver, tdd, parent-precedence]
dependency_graph:
  requires:
    - 65-01-SUMMARY.md
  provides:
    - resolveTeamByShortName-helper
    - findAllByShortName-repository-method
  affects:
    - DriverSheetImportService
    - TeamRepository
    - DriverSheetImportServiceTest
tech_stack:
  added: []
  patterns:
    - Spring Data JPA derived list query (findAllBy{Property})
    - Service-level resolution policy with parent-precedence
key_files:
  created: []
  modified:
    - src/main/java/org/ctc/domain/repository/TeamRepository.java
    - src/main/java/org/ctc/dataimport/DriverSheetImportService.java
    - src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java
decisions:
  - D-01..D-18 honored (CONTEXT.md)
  - Helper placed alongside cellToString (D-18)
  - Helper carries Javadoc explaining parent-precedence (D-17)
metrics:
  duration_minutes: 10
  task_count: 6
  file_count: 3
  tests_run: 1231
  tests_failures: 0
  tests_skipped: 4
  jacoco_bundle_line_ratio: 0.8561
  jacoco_gate: 0.82
  completed_date: 2026-05-07
---

# Phase 66 Plan 01: Team ShortName Collision Fix (Driver Import) Summary

Resolved the `NonUniqueResultException` 500 in `DriverSheetImportService.preview/execute` when a parent team and one of its sub-teams share the same `shortName` (e.g. parent `ZFS` + sub `ZFS`). Added a list-returning repository method, replaced all 5 import-service call sites with a parent-preferring resolver helper, and migrated 18 mock stubs in `DriverSheetImportServiceTest`.

## What Changed

**Repository surface (`TeamRepository.java`):**
- Added `List<Team> findAllByShortName(String shortName)` — Spring Data derived query.
- Retained `findByShortName(...)` and `findByShortNameIgnoreCase(...)` (D-04, D-05) — still used by `TeamControllerTest` and `GroupsSeasonE2ETest`, both with unique test-prefix shortNames; no collision risk.
- Added Javadoc on `findByShortName` clarifying it is safe only for callers guaranteeing shortName uniqueness.

**Service helper (`DriverSheetImportService.java`):**
- New private helper `resolveTeamByShortName(String) -> Optional<Team>` placed alongside `cellToString` (D-18) for symmetry.
- Algorithm (verbatim from D-06): empty → `Optional.empty()`; single → use it; multi-match → prefer parent (`parentTeam == null`); multi-parent edge → log.warn and pick first deterministically (D-07).
- Replaced all 5 call sites that previously called `teamRepository.findByShortName(...)`:
  - Line 135 (`execute → NEW_DRIVER` rows)
  - Line 146 (`execute → NEW_ASSIGNMENT` rows)
  - Line 166 (`execute → CONFLICT` rows)
  - Line 195 (`execute → FUZZY_SUGGESTION` rows)
  - Line 296 (`preview → buildTabPreview` — the reported crash site)

**Tests (`DriverSheetImportServiceTest.java`):**
- Added 2 new TDD tests:
  - `givenTeamsWithSameShortNameParentAndSub_whenPreview_thenResolvesParentTeam` (D-11) — regression fence for the multi-match → parent precedence.
  - `givenTwoParentTeamsWithSameShortName_whenPreview_thenFirstWinsWithoutException` (D-12) — defensive cover of the multi-parent edge.
- Migrated 18 existing mock stubs at lines 243, 270, 294, 325, 356, 386, 459, 484, 485, 511, 543, 544, 573, 601, 672, 699, 732, 755 from `findByShortName` → `findAllByShortName` (Optional → List). No test logic changes.

## TDD Gate Compliance

| Gate | Commit | Marker |
|------|--------|--------|
| RED  | `dd123e0` | `test(66-01): add failing tests for team shortName collision resolver` (compile error confirmed RED) |
| GREEN (interim) | `d204624` | `feat(66-01): add findAllByShortName to TeamRepository` (tests now compile, runtime fail) |
| GREEN (final) | `4d26b75` | `fix(66): resolve team shortName collision in driver import` (27/27 DriverSheetImportServiceTest pass) |

RED gate confirmed by compile error: `cannot find symbol: method findAllByShortName`. GREEN gate confirmed by `Tests run: 27, Failures: 0, Errors: 0` for the test class and `Tests run: 1231, Failures: 0, Errors: 0` full-suite. No REFACTOR commit required (no cleanup beyond the three commits).

## Verification

| Criterion | Result |
|-----------|--------|
| `./mvnw verify` | BUILD SUCCESS |
| Tests run / failures | 1231 / 0 |
| JaCoCo BUNDLE LINE | 0.8561 (gate 0.82) ✅ |
| `git grep "teamRepository\.findByShortName(" src/main/java/org/ctc/dataimport/` | empty ✅ |
| `git grep "teamRepository\.findByShortName(" src/test/java/org/ctc/dataimport/` | empty ✅ |
| `git grep "List<Team> findAllByShortName" src/main/java/org/ctc/domain/repository/TeamRepository.java` | 1 match ✅ |
| TeamControllerTest.java unchanged | ✅ |
| GroupsSeasonE2ETest.java unchanged | ✅ |
| `findByShortName` / `findByShortNameIgnoreCase` retained in TeamRepository | ✅ (per D-04, D-05) |

## Commits

| Task | Hash | Message |
|------|------|---------|
| 1 (RED) | `dd123e0` | `test(66-01): add failing tests for team shortName collision resolver` |
| 2 | `d204624` | `feat(66-01): add findAllByShortName to TeamRepository` |
| 3+4 | `4d26b75` | `fix(66): resolve team shortName collision in driver import` |
| 5 (verify) | — | `./mvnw verify` (gate, no code commit) |
| 6 (summary) | (this commit) | `docs(66-01): plan summary — team shortName collision fix complete` |

Tasks 3 + 4 are atomic per CONTEXT.md D-10 (one bundled fix covers all 5 call sites and the 18-stub migration — splitting per-site would create transient half-migrated states).

## Deviations from Plan

None — plan executed exactly as written.

CONTEXT.md said "17 mock stubs"; RESEARCH.md (Pitfall #2) corrected to 18. Plan executed against the verified count of 18 from the canonical Test Stub Migration Map, which lists the exact 18 line numbers (243, 270, 294, 325, 356, 386, 459, 484, 485, 511, 543, 544, 573, 601, 672, 699, 732, 755). All 18 migrated.

## Decisions Made

| ID | Decision | Outcome |
|----|----------|---------|
| D-01 | Resolver at service level | private helper in `DriverSheetImportService` |
| D-02 | No `@Query` magic in repo | plain Spring Data derived `findAllByShortName` |
| D-03 | New list finder | `List<Team> findAllByShortName(String)` |
| D-04 | Keep existing `findByShortName` | retained with clarifying Javadoc |
| D-05 | Don't touch `findByShortNameIgnoreCase` | retained as-is |
| D-06 | 6-line resolver algorithm | implemented verbatim |
| D-07 | Multi-parent edge → log.warn + first | implemented with WARN log |
| D-08 | No new TabWarning for multi-match | not added (silent success path) |
| D-09 | All 5 call sites migrate to helper | done (lines 135, 146, 166, 195, 296) |
| D-10 | Single bundled fix commit | `4d26b75` covers helper + 5 sites + 18 stubs |
| D-11 | One TDD test for parent-precedence | added at end of test class |
| D-12 | Defensive multi-parent test | added at end of test class |
| D-13 | Mechanical stub migration | 18 stubs migrated, no logic changes |
| D-14 | No new integration test | none added |
| D-15 | BDD test naming | both new tests follow `givenX_whenY_thenZ` |
| D-16 | Single plan | this is the only plan in the phase |
| D-17 | Javadoc on helper | added (parent-precedence semantics + multi-parent fallback) |
| D-18 | Helper near `cellToString` | placed at line 411, just below `cellToString` |

## Threat Model Outcomes

Fix is risk-reducing per RESEARCH § Threat Model:
- **Information Disclosure (mitigated)** — replaced JPA stack-trace 500 with graceful Optional-returning path.
- **Denial of Service via crash (mitigated)** — preview/execute no longer crash on the documented data shape.
- **No new attack surface** — derived JPQL parameter binding remains unchanged; `log.warn` argument is bounded VARCHAR(50) public team shortName, no PII.

## Manual UAT (out-of-scope, deferred)

Per `66-VALIDATION.md` § Manual-Only Verifications, end-to-end UAT requires a real Google Sheet with parent + sub `shortName` collision and admin login. Not gated by this plan; left for the orchestrator's UAT phase.

## Self-Check: PASSED

- `.planning/phases/66-team-shortname-collision-fix/66-01-SUMMARY.md` — to be committed in the final docs commit
- Commit `dd123e0` — FOUND
- Commit `d204624` — FOUND
- Commit `4d26b75` — FOUND
- All claimed file modifications present in git log: TeamRepository.java, DriverSheetImportService.java, DriverSheetImportServiceTest.java
