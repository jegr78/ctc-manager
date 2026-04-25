---
phase: 54
plan: 01
subsystem: dataimport
status: complete
tags:
  - dataimport
  - google-sheets
  - preview-service
  - driver-matching
dependency_graph:
  requires:
    - GoogleSheetsService (existing, no modification)
    - DriverMatchingService (existing, no modification)
    - SeasonRepository (modified: +findByYear)
    - TeamRepository (existing, no modification)
    - SeasonDriverRepository (existing, no modification)
  provides:
    - DriverSheetImportService.preview(sheetUrl) — Phase 55 controller entry point
    - DriverSheetImportPreview + 7 inner record types + ErrorReason enum — Phase 55 template contract
  affects:
    - Phase 55: DriverSheetImportController consumes service unchanged
tech_stack:
  added:
    - DriverSheetImportService (Spring @Service, @RequiredArgsConstructor, @Slf4j)
    - 7 public inner Java records (DriverSheetImportPreview, TabPreview, NewDriverRow, NewAssignmentRow, ConflictRow, FuzzySuggestionRow, UnchangedRow, ErrorRow)
    - ErrorReason enum with message() helper
  patterns:
    - D-12 waterfall bucketing (7-step, first-match-wins)
    - D-11 first-occurrence-wins duplicate detection per tab (Set<String>)
    - D-01..D-03 Season auto-match via findByYear(int) with singleton/0/≥2 branching
    - D-06 idempotent re-fetch pattern (no @SessionAttributes)
key_files:
  created:
    - src/main/java/org/ctc/dataimport/DriverSheetImportService.java (278 lines)
    - src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java (494 lines)
  modified:
    - src/main/java/org/ctc/domain/repository/SeasonRepository.java (22 lines, +2 for findByYear)
decisions:
  - D-01: findByYear(int) added to SeasonRepository — Season.name is free-text, displayLabel is computed; findByYear is the only viable JPA derived query
  - D-12: Waterfall order BLANK_PSN→BLANK_TEAM→UNKNOWN_TEAM→DUPLICATE→FUZZY→EXACT→NONE; duplicate test requires both team codes stubbed because UNKNOWN_TEAM (step 3) fires before DUPLICATE (step 4)
  - D-06: No @SessionAttributes — preview() is stateless; Phase 55 re-fetches and re-runs preview before executing
  - D-07: Naive per-tab bucketing; same PSN in multiple tabs produces independent rows (cross-tab dedup deferred to Phase 55 execute)
metrics:
  duration: "~15 minutes"
  completed: 2026-04-24
  tasks_completed: 5
  tasks_total: 5
  files_created: 2
  files_modified: 1
  tests_added: 16
  tests_total: 1041
---

# Phase 54 Plan 01: DriverSheetImportService Preview — Summary

**One-liner:** Stateless preview service categorizing Google Sheets driver rows into 6 typed buckets via D-12 waterfall with SeasonRepository.findByYear(int) auto-match.

---

## Commits

| # | Hash | Subject |
|---|------|---------|
| 1 | af4e43c | feat(54-01): add SeasonRepository.findByYear(int) derived query |
| 2 | 8b526d4 | test(54-01): scaffold DriverSheetImportService stub + test skeleton |
| 3 | c7b9c4c | feat(54-01): implement DriverSheetImportService.preview() per D-12 waterfall |
| 4 | fc752d4 | test(54-01): add 16 given-when-then scenarios covering all 6 buckets + D-12 + ambiguous seasons |
| 5 | (this commit) | docs(54-01): record SUMMARY.md with coverage metrics and requirement coverage table |

---

## Coverage

JaCoCo line coverage for `org.ctc.dataimport.DriverSheetImportService` (main class, not inner types):

| Metric | Value |
|--------|-------|
| Lines missed | 1 |
| Lines covered | 90 |
| **Line coverage** | **98.9%** |
| Overall project JaCoCo gate | PASSED (82% minimum) |
| Total tests | 1041 (all passing) |

---

## Test Inventory

| # | Method | REQ-IDs covered |
|---|--------|-----------------|
| 1 | givenSkeleton_whenCompiles_thenPasses | TEST-01 (Wave 0 smoke) |
| 2 | givenMixedTabNames_whenPreview_thenOnlyFourDigitTabsIncluded | IMPORT-02 |
| 3 | givenTabsInReverseOrder_whenPreview_thenTabsSortedAscendingByYear | IMPORT-04 |
| 4 | givenRowShorterThanThreeColumns_whenPreview_thenTreatedAsBlankTeamCode | IMPORT-03 |
| 5 | givenMultipleSeasonsForYear_whenPreview_thenSuggestedSeasonNullWithAmbiguousReason | IMPORT-05, DATA-01 |
| 6 | givenNoSeasonForYear_whenPreview_thenSuggestedSeasonNullWithNoSeasonReason | IMPORT-05, DATA-01 |
| 7 | givenNewPsnId_whenPreview_thenCategorisedAsNewDriver | UX-01 |
| 8 | givenExistingDriverNoSeasonDriver_whenPreview_thenCategorisedAsNewAssignment | UX-02 |
| 9 | givenExistingSeasonDriverDifferentTeam_whenPreview_thenCategorisedAsConflict | UX-03 |
| 10 | givenFuzzyCandidate_whenPreview_thenSuggestedMatchAwaitsUserOptIn | UX-04, MATCH-01 |
| 11 | givenExistingSeasonDriverSameTeam_whenPreview_thenCategorisedAsUnchanged | UX-05 |
| 12 | givenBlankPsnId_whenPreview_thenRowErroredWithBlankPsn | UX-06 |
| 13 | givenBlankTeamCode_whenPreview_thenRowErroredWithBlankTeam | UX-06 |
| 14 | givenUnknownTeamCode_whenPreview_thenRowErroredWithUnknownTeam | UX-06, DATA-02 |
| 15 | givenDuplicatePsnInTab_whenPreview_thenSecondRowErroredWithDuplicate | UX-06, D-11 |
| 16 | givenExistingPsnIdDifferentCase_whenPreview_thenResolvedViaCaseInsensitive | MATCH-01 |
| 17 (*)| givenSamePsnInMultipleTabs_whenPreview_thenEachTabCategorisedIndependently | MATCH-02, D-07 |

*Note: 16 `@Test` methods in file (scenario 17 = method #16; the smoke test brings the count to 16 total, all shown above).*

---

## Guardrail Confirmations

| Guardrail | Check | Result |
|-----------|-------|--------|
| D-04: typed per-bucket fields | `grep -c "Map<" DriverSheetImportService.java` → 0 | PASS |
| D-06: no @SessionAttributes | `grep -c "@SessionAttributes" DriverSheetImportService.java` → 0 | PASS |
| D-13: findByYear used (not findByName/findByDisplayLabel) | `grep -c "findByName\|findByDisplayLabel" DriverSheetImportService.java` → 0; `findByYear` appears at line 71 | PASS |
| DATA-04: no new Flyway migration | `ls src/main/resources/db/migration/V*.sql` → 2 files (unchanged) | PASS |
| DATA-05: no RaceLineup reference | `grep -ni "raceLineup\|RaceLineup" DriverSheetImportService.java` → zero hits | PASS |

---

## Deviations from Plan

**1. [Rule 1 - Bug] Fixed duplicate test stub: both team codes needed for DUPLICATE_IN_TAB to fire**

- **Found during:** Task 4 test run
- **Issue:** The `givenDuplicatePsnInTab` test stubbed only "AHR" team code, but the duplicate row used "CRL". Per D-12, UNKNOWN_TEAM_CODE (step 3) fires before DUPLICATE_IN_TAB (step 4), so the test assertion was wrong about which error fires.
- **Fix:** Stubbed both `teamRepository.findByShortName("AHR")` and `teamRepository.findByShortName("CRL")` in that test so both rows pass team validation and the duplicate check (step 4) correctly fires for the second row.
- **Files modified:** `DriverSheetImportServiceTest.java`
- **Commit:** fc752d4 (included in Task 4 commit)

**2. Test count: 16 instead of planned 13**

- The plan requested ≥13 tests. Five of the planned scenario names were split or expanded: `givenNoSeasonForYear` (additional scenario alongside `givenMultipleSeasonsForYear`), plus all 4 ERROR sub-cases were each given their own `@Test` method for clarity rather than a single combined test. Final count: 16 `@Test` methods.
- No functional deviation — all planned scenario behaviors covered and exceeded.

---

## Known Stubs

None. The `preview()` method is fully implemented. Inner records are data-only (no stubs). `ErrorReason.message()` returns hard-coded English strings per D-09.

---

## Threat Flags

None. `DriverSheetImportService.preview()` is read-only (no DB writes, no file I/O beyond Google Sheets API calls delegated to `GoogleSheetsService`). No new network endpoints, auth paths, or schema changes introduced in this plan.

---

## Self-Check: PASSED

| Check | Result |
|-------|--------|
| SeasonRepository.java exists | FOUND |
| DriverSheetImportService.java exists | FOUND |
| DriverSheetImportServiceTest.java exists | FOUND |
| Commit af4e43c exists | CONFIRMED |
| Commit 8b526d4 exists | CONFIRMED |
| Commit c7b9c4c exists | CONFIRMED |
| Commit fc752d4 exists | CONFIRMED |
| ./mvnw verify exits 0 | PASSED (1041 tests, all green) |
| JaCoCo gate 82% | PASSED (98.9% line coverage on DriverSheetImportService) |
| No STATE.md / ROADMAP.md / REQUIREMENTS.md modifications | CONFIRMED |
| No files outside plan's files_modified list changed | CONFIRMED |
