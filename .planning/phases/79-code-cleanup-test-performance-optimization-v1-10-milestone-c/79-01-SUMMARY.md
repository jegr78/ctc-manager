---
phase: 79
plan: "01"
subsystem: test-infrastructure
tags: [baseline, independence-audit, test-ordering, DirtiesContext]
dependency_graph:
  requires: []
  provides: [wallclock-baseline, independence-audit-gate]
  affects: [79-03-parallelization]
tech_stack:
  added: []
  patterns: [maven-surefire-runOrder, maven-failsafe-runOrder, DirtiesContext-audit]
key_files:
  created:
    - .planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-AUTO-UAT.md
    - .planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-INDEPENDENCE-AUDIT.md
  modified: []
decisions:
  - "Seed=1234 reveals reproducible TestDataServiceIntegrationTest ordering dependency — Plan 03 BLOCKED"
  - "@DirtiesContext audit: plan's pre-flight grep over-counts (13 vs 10 real); all 10 real annotations are KEEP-mandatory"
  - "Reverse-alphabetical first run was transient Playwright OS crash; second run GREEN — not a real isolation bug"
metrics:
  duration: "65m 18s"
  completed_date: "2026-05-15"
  tasks_completed: 4
  files_created: 2
---

# Phase 79 Plan 01: Baseline and Independence Audit Summary

Wallclock baseline (13m 27s at SHA 28d0469) and test-independence audit for Phase 79 Wave 1. The audit reveals one reproducible test-ordering dependency (seed=1234, `TestDataServiceIntegrationTest`) that BLOCKS Wave 3 parallelization (Plan 03).

## What Was Built

### Task 1: Wallclock Baseline (79-AUTO-UAT.md)

Ran `time ./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev -Ddocker.available=true` on a clean working tree at SHA `28d0469`:

- **Duration:** `13m 27s` real (13:26 min Maven total time)
- **Build result:** BUILD SUCCESS
- **Tests:** 1227 Surefire unit + 112 Failsafe IT + 36 E2E Playwright
- **JaCoCo:** 289 classes, all coverage checks met (≥ 82%)
- **D-06 target:** Final ≤ 9m 23s (≥ 30% reduction required)

### Task 2: Surefire + Failsafe Independence Runs (79-INDEPENDENCE-AUDIT.md)

| Run | Seed/Order | Result | Duration | Tests | Failures |
|-----|-----------|--------|----------|-------|----------|
| Surefire reverse-alpha | — | **GREEN** | 5m 36s | 1410 | 0 |
| Surefire random | 1234 | **RED** | 6m 43s | 1410 | 2 |
| Surefire random | 5678 | **GREEN** | 6m 15s | 1410 | 0 |
| Surefire random | 9999 | **GREEN** | 6m 11s | 1410 | 0 |
| Failsafe reverse-alpha | — | **GREEN** | 12m 34s | 112 IT | 0 |

**Seed=1234 RED detail:** `TestDataServiceIntegrationTest` (`givenDevSeed_whenStarted_thenSwissSeasonHasFiveMatchdays` and `givenDevSeed_whenStarted_thenS2HasFormatSwiss`) fails with "Season not found: year=2024 name=Regular Season". Root cause: with seed=1234 ordering, a sitegen test class (which calls `Flyway.clean()` + `Flyway.migrate()` + `testDataService.seed()` in `@BeforeAll` and carries `@DirtiesContext`) runs before `TestDataServiceIntegrationTest`. The H2 in-memory database (`DB_CLOSE_DELAY=-1`) state after `Flyway.clean()` is not fully re-seeded in a way that `TestDataServiceIntegrationTest` can find the 2024 seasons. Reproducible across two independent seed=1234 runs.

Note: The first reverse-alphabetical run (immediately after the 13m 27s E2E baseline run) produced 90 Playwright OS-crash errors in `TeamProfilePageGeneratorTest` — these were transient Chromium resource exhaustion, NOT an isolation bug. The second run was clean.

### Task 3: @DirtiesContext Audit

**Pre-flight grep result:** Plan's command reports TOTAL=13 (including 3 false-positive comment lines). Corrected code-only count: **10**.

**Discrepancy resolution:** The plan's `grep -v '^\s*//\|^\s*\*'` filter does not work on `grep -rn` output because the pattern anchors to line-start but after `grep -rn`, each line starts with `filepath:linenum:` — not `//` or `*`. Three comment-containing lines slip through: `ImportConcurrentLockIT.java:81` (`//` comment), `TeamProfilePageGeneratorTest.java:107` (`*` Javadoc), `BackupStagingCleanupIT.java:46` (`* {@code @DirtiesContext}` Javadoc). RESEARCH's expected 10 is correct. CONTEXT.md's "13" was the uncorrected grep count.

**All 10 annotations: KEEP-mandatory (zero removals)**
- 3 × `ImportLock*IT` — `CountDownLatch` non-resettable singleton (BEFORE_EACH_TEST_METHOD)
- 7 × sitegen tests — `SiteProperties.outputDir` singleton mutation (`setOutputDir(tempDir)`)

### Task 4: Commit

Single commit `06bd231` on `gsd/v1.10-platform-and-backup` containing both artifact files.

## Deviations from Plan

### Auto-detected Issues

**1. [Rule 1 - Bug] Plan's pre-flight grep over-counts @DirtiesContext annotations**
- **Found during:** Task 3
- **Issue:** The plan's `grep -v '^\s*//\|^\s*\*'` filter fails on `grep -rn` output format (`filepath:linenum:content`); excludes 0 of the 3 comment-containing lines it's supposed to remove, yielding TOTAL=13 instead of 10
- **Fix:** Documented discrepancy in 79-INDEPENDENCE-AUDIT.md with corrected count and explanation; audit table covers all 10 real code annotations
- **Files modified:** 79-INDEPENDENCE-AUDIT.md (documentation only)
- **Commit:** 06bd231

**2. [Rule 1 - Bug] First reverse-alphabetical run: Playwright OS crash (transient, not isolation bug)**
- **Found during:** Task 2
- **Issue:** Running `./mvnw test -Dsurefire.runOrder=reversealphabetical` immediately after the 13m27s E2E baseline run produced 90 errors — `TeamProfilePageGeneratorTest` (first in reverse order) failed with `PlaywrightException: Unable to capture screenshot` due to Chromium resource exhaustion right after the full Playwright E2E suite
- **Fix:** Waited a few minutes, re-ran — BUILD SUCCESS. Documented in 79-INDEPENDENCE-AUDIT.md with explanation
- **Files modified:** 79-INDEPENDENCE-AUDIT.md (documentation only)
- **Commit:** 06bd231

## !! CRITICAL BLOCKER: Plan 03 BLOCKED !!

**Seed=1234 RED: `TestDataServiceIntegrationTest` has a reproducible test-ordering dependency.**

The independence audit found that random seed=1234 ordering causes 2 unit test failures. This is a genuine test-isolation bug (not transient) — confirmed by running seed=1234 twice with identical failures each time.

**Impact:** Plan 03 (Surefire `forkCount=2C` parallelization) CANNOT proceed until this ordering dependency is fixed. Under `forkCount=2C`, tests will run across forked JVMs in unpredictable order — the same isolation bug that seed=1234 revealed will manifest as flaky failures in parallel mode.

**Root cause hypothesis:** `TestDataServiceIntegrationTest` (`@SpringBootTest @ActiveProfiles("dev") @Transactional`) shares the H2 in-memory database (`DB_CLOSE_DELAY=-1`) with sitegen tests that call `Flyway.clean()` directly on the datasource in their `@BeforeAll`. When a sitegen test runs before `TestDataServiceIntegrationTest` and its `@DirtiesContext` evicts the Spring context, the subsequent `DevDataSeeder` re-seed may not populate the expected 2024-year seasons before `TestDataServiceIntegrationTest` queries them.

**Recommended fix (Wave 1.5, before Plan 03):** Add `@DirtiesContext` to `TestDataServiceIntegrationTest` OR add a `@BeforeEach` that asserts/seeds the expected data, OR make the test independent of `DevDataSeeder` by using explicit `@Sql` setup.

## Known Stubs

None — this plan produces only documentation artifacts with real measured data.

## Threat Flags

None — no new network endpoints, auth paths, file access patterns, or schema changes.

## Self-Check

Files exist:
- `06bd231` commit: FOUND (git log verified)
- `.planning/phases/79-.../79-AUTO-UAT.md`: FOUND
- `.planning/phases/79-.../79-INDEPENDENCE-AUDIT.md`: FOUND

Commit shows exactly 2 files (both `.planning/phases/79-.../*.md`): VERIFIED

Branch is `gsd/v1.10-platform-and-backup`: VERIFIED

## Self-Check: PASSED
