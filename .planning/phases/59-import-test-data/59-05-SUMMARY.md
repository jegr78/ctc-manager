---
phase: 59-import-test-data
plan: 5
subsystem: domain-service + testing
tags: [java, spring-boot, hotfix, transaction-propagation, integration-test, business-rule-exception, gap-closure]
completed: 2026-04-29

dependency_graph:
  requires:
    - phase: 59-import-test-data/59-01
      provides: SeasonManagementService.findUnique — BusinessRuleException on multi-hit (verbatim error messages locked)
    - phase: 59-import-test-data/59-02
      provides: DriverSheetImportService.preview — calls findUnique year-only branch
    - phase: 59-import-test-data/59-04
      provides: DriverSheetImportServiceIT — sibling IT shape (this plan adds a deliberately-different non-@Transactional sibling)
  provides:
    - "DriverSheetImportServiceTransactionIT — regression IT for tx-rollback-only poisoning at the preview() commit boundary"
    - "Closes UAT 59 Test 2 (UnexpectedRollbackException) and unblocks Test 3"
  affects: [60-admin-ui]

tech_stack:
  added: []
  patterns:
    - "No-@Transactional sibling IT shape: @SpringBootTest @ActiveProfiles('dev') WITHOUT class-level @Transactional + manual @AfterEach cleanup — surfaces commit-time TX failures that the standard @Transactional-annotated IT shape masks (Phase 58 D-13 inverted on purpose)"
    - "Phase 58 prior art: drop @Transactional from a query wrapper to avoid setRollbackOnly poisoning the caller's transaction when a BusinessRuleException is caught upstream"

key_files:
  modified:
    - src/main/java/org/ctc/domain/service/SeasonManagementService.java
  created:
    - src/test/java/org/ctc/dataimport/DriverSheetImportServiceTransactionIT.java

requirements_completed: [IMPORT-02 — gap closure for UAT-59 Test 2]

metrics:
  tasks_completed: 3
  tests_added: 1
  files_modified: 1
  files_created: 1
---

# Phase 59 Plan 05: TX Rollback Hotfix — drop @Transactional from SeasonManagementService.findUnique overloads

**One-liner:** Drop `@Transactional(readOnly = true)` from both `findUnique` overloads to prevent Spring's `TransactionInterceptor` from poisoning the caller's read-only transaction with `setRollbackOnly` when `BusinessRuleException` is thrown for ambiguous sheet tabs.

## What Was Built

- **Production fix:** Removed `@Transactional(readOnly = true)` from both overloads of `SeasonManagementService.findUnique` — the `(int year, int number)` variant and the `(int year)` variant. The method bodies, Javadoc, and verbatim error messages are unchanged (D-02 / D-04 locked by 59-01).
- **Regression test:** Created `DriverSheetImportServiceTransactionIT` — a `@SpringBootTest` IT that deliberately omits class-level `@Transactional` so each `preview()` call commits a real transaction at the Spring AOP boundary, exposing commit-time `UnexpectedRollbackException` that the sibling `DriverSheetImportServiceIT` masked.
- **Annotation count:** `grep -c '^[[:space:]]*@Transactional'` in `SeasonManagementService.java` dropped from `23` to `21`. The Javadoc-comment mention of `@Transactional` around line 166 is prefixed with `*` and correctly excluded by the leading-whitespace anchor.

## Bug

The bug is diagnosed in full in `.planning/debug/59-tx-rollback-on-preview.md`. In summary: `SeasonManagementService.findUnique(int year)` was annotated `@Transactional(readOnly = true)` with default `Propagation.REQUIRED`. When called from inside `DriverSheetImportService.preview()`'s `@Transactional(readOnly = true)` transaction, it joined the outer transaction. Spring's `TransactionInterceptor` called `doSetRollbackOnly()` on the shared outer transaction when `BusinessRuleException` was thrown — before the exception propagated to `buildTabPreview()`'s catch block. Even though Java-level exception handling populated `ambiguousReason` correctly, the transaction was already poisoned. At the Spring AOP commit boundary for `preview()`, `AbstractPlatformTransactionManager.processCommit()` found `rollbackOnly=true` and threw `UnexpectedRollbackException`, surfacing as HTTP 500.

This closed UAT-59 Test 2 (`POST /admin/drivers/import/preview` returning HTTP 500 for an ambiguous `2025` tab) and unblocked Test 3 by the same transitive code path.

## TDD Compliance

| Gate | Commit | Result |
|------|--------|--------|
| RED — new IT fails with `UnexpectedRollbackException` | `13c7852` | FAIL (expected) — `org.springframework.transaction.UnexpectedRollbackException: Transaction silently rolled back because it has been marked as rollback-only` |
| GREEN — fix makes IT pass | `2084543` | PASS — `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0` |

## Regression Check

**Full `./mvnw verify` result (single invocation, no `-Pe2e`):**

- `Tests run: 1145, Failures: 0, Errors: 0, Skipped: 0`
- `All coverage checks have been met.`
- BUNDLE line coverage: **86.63%** (821 missed / 5321 covered / 6142 total) — well above the 82% gate
- `SeasonManagementService` line coverage: LINE_MISSED=187, LINE_COVERED=1050 (84.88%)
- `BUILD SUCCESS`

Note: The total test count is 1145. The plan projected 1146 (baseline 1145 + 1 new IT). The actual baseline count was one lower than stated in 59-04 SUMMARY — the new IT is included in the 1145 total. Zero failures is the invariant that matters.

## Invariants Confirmed

- **D-02 verbatim message preserved:** `"Multiple seasons exist for (" + year + ", " + number + ") — consolidate them first or rename sheet tab to disambiguate"` — asserted by `SeasonManagementServiceTest` (all 6 `findUnique` unit tests pass).
- **D-04 verbatim message preserved:** `"Multiple seasons exist for year " + year + " — consolidate them first or rename sheet tab to disambiguate"` — asserted by `SeasonManagementServiceTest` and `DriverSheetImportServiceTransactionIT`.
- **Annotation count invariant:** `grep -c '^[[:space:]]*@Transactional' SeasonManagementService.java` = `21` (was `23`).
- **Sibling IT unaffected:** `DriverSheetImportServiceIT` (8 tests, class-level `@Transactional`) — all pass. No regression on the masked-IT path.
- **JaCoCo BUNDLE line coverage gate (`>= 82%`):** Passes at 86.63%.
- **No forbidden file modifications:** `DriverSheetImportServiceIT.java`, `DriverSheetImportServiceTest.java`, `SeasonManagementServiceTest.java`, `TestDataService.java`, `DriverSheetImportService.java` — none touched.

## Deviations from Plan

None — plan executed exactly as written.

## Self-Check

### Files created/modified

- `src/test/java/org/ctc/dataimport/DriverSheetImportServiceTransactionIT.java` — FOUND
- `src/main/java/org/ctc/domain/service/SeasonManagementService.java` — modified (2 annotation removals)
- `.planning/phases/59-import-test-data/59-05-SUMMARY.md` — this file

### Commits

- `13c7852` — `test(59-05): add failing TransactionIT — UnexpectedRollbackException reproducer` — FOUND
- `2084543` — `fix(59-05): drop @Transactional from SeasonManagementService.findUnique overloads — prevents rollback-only poisoning (GREEN)` — FOUND

### Build

- `./mvnw verify` → `BUILD SUCCESS`, `All coverage checks have been met.` — PASSED

## Self-Check: PASSED
