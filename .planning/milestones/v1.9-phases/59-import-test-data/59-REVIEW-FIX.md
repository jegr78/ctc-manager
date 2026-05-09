---
phase: 59-import-test-data
fixed_at: 2026-04-29T21:55:00Z
review_path: .planning/phases/59-import-test-data/59-REVIEW.md
iteration: 1
findings_in_scope: 4
fixed: 4
skipped: 0
status: all_fixed
---

# Phase 59: Code Review Fix Report

**Fixed at:** 2026-04-29
**Source review:** `.planning/phases/59-import-test-data/59-REVIEW.md`
**Iteration:** 1

**Summary:**
- Findings in scope: 4 (WR-01, WR-02, WR-03, WR-04 — Critical: 0, Warning: 4; Info skipped per fix_scope=critical_warning)
- Fixed: 4
- Skipped: 0

All four warning-severity findings target the same test class
(`src/test/java/org/ctc/dataimport/DriverSheetImportServiceTransactionIT.java`).
The hotfix in production code (`SeasonManagementService.findUnique`) was sound;
the review only flagged defects in the new regression IT itself.

After all fixes, the full Surefire+Failsafe suite was run with `./mvnw verify`
(no `-Pe2e`, per the test-call budget guidance):

- **Tests run: 1145, Failures: 0, Errors: 0, Skipped: 0**
- **JaCoCo coverage gate: All coverage checks have been met** (>= 82%)

## Fixed Issues

### WR-01 / WR-02: Silent `catch (Exception ignore)` in `@AfterEach` leaks state and masks regressions

**Files modified:** `src/test/java/org/ctc/dataimport/DriverSheetImportServiceTransactionIT.java`
**Commit:** `f3cb515`
**Applied fix:**
- Added an idempotent pre-state probe in `@BeforeEach`: any leftover `FRESH_YEAR=2099`
  rows from a previous failed cleanup are deleted before the seed, so H2's
  `DB_CLOSE_DELAY=-1` cross-context persistence cannot escalate orphan rows
  into multi-hit ambiguity (WR-01).
- Replaced the bare `catch (Exception ignore)` with a diagnostic
  `System.err.println` that emits the exception class and message, so a
  re-introduced rollback-only-poisoning regression — or any other ORM error
  in cleanup — surfaces in the test report instead of being silently
  swallowed (WR-02).

WR-01 and WR-02 target the same code section and converge on the same fix
(pre-state probe + diagnostic logging), so they were delivered atomically as
a single commit per the review's combined recommendation.

### WR-03: Heavyweight `@ActiveProfiles("dev")` couples the regression IT to seed data

**Files modified:** `src/test/java/org/ctc/dataimport/DriverSheetImportServiceTransactionIT.java`
**Commit:** `a5473fe`
**Applied fix:** Implemented option (a) from the review — made the IT
self-contained by creating its own `RaceScoring` and `MatchScoring` rows in
`@BeforeEach` (autowiring `RaceScoringRepository` and `MatchScoringRepository`)
and deleting them in `@AfterEach` using the same diagnostic-`System.err`
pattern as the season cleanup. The brittle
`seasonRepository.findAll().stream().findFirst()` template-borrow is gone —
the IT no longer depends on the dev seed producing at least one season with
populated scoring FKs.

The `@ActiveProfiles("dev")` annotation itself was left in place (matching
the sibling `DriverSheetImportServiceIT` and the project's uniform test
configuration), but the IT no longer reads anything `DevDataSeeder` writes.

### WR-04: No JUnit-level invariant pins the absence of class-level `@Transactional`

**Files modified:** `src/test/java/org/ctc/dataimport/DriverSheetImportServiceTransactionIT.java`
**Commit:** `bedee45`
**Applied fix:** Added a self-protection JUnit test
`givenTestClass_whenCheckingClassAnnotations_thenIsNotTransactional()` that
uses `Class.isAnnotationPresent(Transactional.class)` to fail loudly if any
future contributor adds `@Transactional` to the class while "fixing flaky
cleanup". The test follows the project's BDD given-when-then naming
convention and references the class-level Javadoc rationale via an AssertJ
`.as(...)` description, so a CI failure points the maintainer at the design
intent.

---

_Fixed: 2026-04-29_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
