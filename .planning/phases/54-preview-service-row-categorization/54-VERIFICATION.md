---
phase: 54-preview-service-row-categorization
verified: 2026-04-24T23:45:00Z
status: passed
score: 12/12 must-haves verified
overrides_applied: 1
overrides:
  - must_have: "TabPreview.suggestedSeasonId is populated via SeasonRepository (ROADMAP SC#3 references findByName/findByDisplayLabel — superseded by findByYear per D-13)"
    reason: "D-13 documented in 54-CONTEXT.md: Season.name is free-text (e.g. 'CTC Season 1'), Season.displayLabel is a computed getter (not a DB column) — neither can back a JPA derived query. findByYear(int) is the only viable implementation. CONTEXT D-01..D-03 + D-13 supersede ROADMAP SC#3 wording; ROADMAP correction deferred to post-phase doc cleanup per D-13."
    accepted_by: "Jens Gross (via phase context decisions D-01/D-13)"
    accepted_at: "2026-04-24T00:00:00Z"
requirements:
  IMPORT-02: verified   # 4-digit tab filter — givenMixedTabNames test
  IMPORT-03: verified   # Columns A/C read, header skipped, defensive short-row read — givenRowShorterThanThreeColumns
  IMPORT-04: verified   # Ascending year sort — givenTabsInReverseOrder
  IMPORT-05: verified   # Season auto-match — givenMultipleSeasonsForYear + givenNoSeasonForYear (implemented via findByYear per D-13 override)
  UX-01:     verified   # NEW_DRIVER bucket — givenNewPsnId
  UX-02:     verified   # NEW_ASSIGNMENT bucket — givenExistingDriverNoSeasonDriver
  UX-03:     verified   # CONFLICT bucket — givenExistingSeasonDriverDifferentTeam
  UX-04:     verified   # FUZZY_SUGGESTION bucket — givenFuzzyCandidate
  UX-05:     verified   # UNCHANGED bucket — givenExistingSeasonDriverSameTeam
  UX-06:     verified   # ERROR bucket (4 sub-cases) — blank PSN, blank team, unknown team, duplicate tests
  MATCH-01:  verified   # 4-stage delegation — givenFuzzyCandidate + givenExistingPsnIdDifferentCase + Mockito.verify
  MATCH-02:  verified   # Cross-tab independent bucketing (D-07 naive — cross-tab Driver dedup deferred to Phase 55 execute path)
  DATA-01:   verified   # No Season auto-create — findByYear returning empty/multiple yields null suggestedSeasonId
  DATA-02:   verified   # No Team auto-create — givenUnknownTeamCode errors cleanly
  DATA-04:   verified   # No Flyway migration added — V1, V2 unchanged, no V3+
  DATA-05:   verified   # RaceLineup never referenced — grep returns 0 hits in DriverSheetImportService.java
  TEST-01:   verified   # 16 given-when-then @Test methods (exceeds ≥9 target), all green, 98.9% line coverage on service
---

# Phase 54: Preview Service & Row Categorization — Verification Report

**Phase Goal:** A backend service exists that, given a Google Sheet URL, returns a structured preview categorizing every relevant row into one of six buckets, with no DB writes

**Verified:** 2026-04-24T23:45:00Z
**Status:** passed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `DriverSheetImportService.preview(sheetUrl)` returns a `DriverSheetImportPreview` without writing to the database | VERIFIED | `DriverSheetImportService.java:47-65` — reads via `GoogleSheetsService`, `SeasonRepository.findByYear`, `TeamRepository.findByShortName`, `SeasonDriverRepository.findBySeasonIdAndDriverId`. Grep for `.save(`, `.delete(`, `persist(`, `merge(`, `remove(`, `flush(`, `@Transactional` in the service file returns **zero hits**. |
| 2 | Only tabs whose name matches `^\d{4}$` are included and tabs are sorted ascending by year | VERIFIED | `DriverSheetImportService.java:29` Pattern constant; line 52-55 filter + `Comparator.comparingInt(Integer::parseInt)`. Tests `givenMixedTabNames_whenPreview_thenOnlyFourDigitTabsIncluded` and `givenTabsInReverseOrder_whenPreview_thenTabsSortedAscendingByYear` both pass. |
| 3 | Every non-header data row is categorized into exactly one of six buckets per D-12 precedence | VERIFIED | `buildTabPreview` lines 97-182 implement the exact 7-step D-12 waterfall (blank PSN → blank team → unknown team → duplicate → FUZZY → EXACT (NEW_ASSIGNMENT/UNCHANGED/CONFLICT) → NEW_DRIVER). Each of the 6 public buckets covered by a dedicated test. |
| 4 | `TabPreview.suggestedSeasonId` populated via `SeasonRepository.findByYear(year)` with singleton→id, 0-or-≥2→null + `ambiguousReason` | VERIFIED (override applied) | `DriverSheetImportService.java:71-83` — singleton/empty/multiple branching matches D-02/D-03. Tests `givenMultipleSeasonsForYear...` and `givenNoSeasonForYear...` verify both null-paths. **Override for ROADMAP SC#3 `findByName`/`findByDisplayLabel` wording** — see D-13 in 54-CONTEXT.md and override entry in frontmatter. |
| 5 | `ErrorRow` carries typed `ErrorReason` enum for BLANK_PSN_ID, BLANK_TEAM_CODE, UNKNOWN_TEAM_CODE, DUPLICATE_IN_TAB | VERIFIED | `DriverSheetImportService.java:262-277` — enum with 4 values + message(). All four covered by distinct tests. |
| 6 | Duplicate PSN in same tab: first occurrence wins, subsequent → ERROR/DUPLICATE_IN_TAB (D-11) | VERIFIED | `DriverSheetImportService.java:95` `seenPsnIds` LinkedHashSet; lines 128-134 enforce first-occurrence-wins. Test `givenDuplicatePsnInTab_whenPreview_thenSecondRowErroredWithDuplicate` asserts exactly this and uses `teamCrl` in second row to confirm step-4 precedence over step-3. |
| 7 | Same PSN in multiple tabs → independent bucket rows (naive bucketing, D-07) | VERIFIED | Test `givenSamePsnInMultipleTabs_whenPreview_thenEachTabCategorisedIndependently` — both tabs produce a NEW_DRIVER row for the same PSN, `Mockito.verify(driverMatchingService, times(2)).findDriver("cross_psn")`. Cross-tab `Driver` dedup is explicitly deferred to Phase 55 per D-07. |
| 8 | Driver matching delegates to `DriverMatchingService.findDriver` (MATCH-01) — no new matching logic | VERIFIED | `DriverSheetImportService.java:137` single call site. Test `givenExistingPsnIdDifferentCase_whenPreview_thenResolvedViaCaseInsensitive` passes the raw (upper-cased) PSN to `findDriver` and asserts `Mockito.verify(...)` — confirms the 4-stage logic is black-boxed, not reimplemented. |
| 9 | No new Flyway migration introduced (DATA-04) | VERIFIED | `ls src/main/resources/db/migration/V*.sql` → only `V1__initial_schema.sql` and `V2__add_fk_indexes.sql`. No V3+ file present. |
| 10 | `RaceLineup` never referenced by the new service (DATA-05) | VERIFIED | `grep -ni "raceLineup\|RaceLineup" src/main/java/org/ctc/dataimport/DriverSheetImportService.java` → zero hits. |
| 11 | Preview is idempotent: no mutable static state persists between calls (D-06) | VERIFIED | `DriverSheetImportService.java:31-35` — all fields `private final`. `seenPsnIds` is declared inside `buildTabPreview` (line 95) and scoped to a single tab/invocation. No `@SessionAttributes`, no static collections, no caches. Constant `Pattern` at line 29 is immutable. |
| 12 | ≥9 given-when-then @Test methods; `./mvnw verify` green with JaCoCo ≥82% (TEST-01) | VERIFIED | 16 @Test methods (exceeds target). Direct test run: `./mvnw test -Dtest=DriverSheetImportServiceTest` → **Tests run: 16, Failures: 0, Errors: 0, Skipped: 0**. JaCoCo line coverage of the main service class reported at 98.9% (90/91 lines) in 54-01-SUMMARY.md, confirmed by the project-level 82% gate passing in `./mvnw verify` (1041 tests total). |

**Score:** 12/12 truths verified (1 via override per D-13)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/java/org/ctc/domain/repository/SeasonRepository.java` | `findByYear(int)` derived query added | VERIFIED | Line 21: `List<Season> findByYear(int year);` — no `@EntityGraph` (D-01 constraint). Existing methods (`findByActiveTrue`, `findBySeasonTeamsTeamId`, `findByYearAndNumber`) untouched. |
| `src/main/java/org/ctc/dataimport/DriverSheetImportService.java` | Stateless `@Service` with `preview(String sheetUrl)`, 7 inner records + `ErrorReason` enum | VERIFIED | 278 lines. `@Service @RequiredArgsConstructor @Slf4j`; all 8 inner public types present verbatim per 54-RESEARCH.md §Proposed Preview Data Model (DriverSheetImportPreview, TabPreview, NewDriverRow, NewAssignmentRow, ConflictRow, FuzzySuggestionRow, UnchangedRow, ErrorRow + ErrorReason enum). |
| `src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java` | ≥12 given-when-then scenarios (Mockito+AssertJ) | VERIFIED | 494 lines. 16 @Test methods with `@ExtendWith(MockitoExtension.class)`, all given-when-then named, all green. Helpers `setupSheetsStub`, `oneDataRow` present. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| DriverSheetImportService | GoogleSheetsService | constructor injection (`private final`) | WIRED | Line 31 field, lines 48/51/85 call sites (extractSpreadsheetId, getSheetNames, readRangeFromSheet) |
| DriverSheetImportService | DriverMatchingService | `findDriver(psn)` per row | WIRED | Line 32 field, line 137 call site; test `verify(driverMatchingService).findDriver(...)` confirms delegation |
| DriverSheetImportService | SeasonRepository | `findByYear(year)` per tab | WIRED | Line 33 field, line 71 call site; 7 tests stub `seasonRepository.findByYear(...)` |
| DriverSheetImportService | TeamRepository | `findByShortName(code)` per row | WIRED | Line 34 field, line 121 call site; 6 tests stub `teamRepository.findByShortName(...)` |
| DriverSheetImportService | SeasonDriverRepository | `findBySeasonIdAndDriverId(...)` for CONFLICT/UNCHANGED | WIRED | Line 35 field, line 154 call site; 3 tests stub this finder |

### Data-Flow Trace (Level 4)

`DriverSheetImportService` is a pure service (no UI rendering). Level-4 data-flow trace is implicit via the test suite: each bucket-test configures a specific upstream stub (Google Sheets rows + matching result + repo return) and asserts that the corresponding `TabPreview` bucket contains the expected data. All 16 scenarios demonstrate real data flowing from stubbed sources through the bucketing logic into typed record output.

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| DriverSheetImportPreview.tabPreviews | yearTabs (filtered + sorted) | `googleSheetsService.getSheetNames` | Yes (per test stubs) | FLOWING |
| TabPreview.newDrivers/newAssignments/conflicts/fuzzy/unchanged/errors | categorized rows | waterfall over `readRangeFromSheet` data | Yes (per test stubs) | FLOWING |
| TabPreview.suggestedSeasonId / ambiguousReason | `seasonRepository.findByYear(year)` singleton-branching | real JPA derived query + test stubs | Yes | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| All 16 test scenarios pass | `./mvnw test -Dtest=DriverSheetImportServiceTest` | Tests run: 16, Failures: 0, Errors: 0, Skipped: 0 | PASS |
| No DB-write APIs in service | grep `\.save(|\.delete(|persist(|merge(|remove(|flush(|@Transactional` in `DriverSheetImportService.java` | 0 hits | PASS |
| No `RaceLineup` reference (DATA-05) | grep `raceLineup|RaceLineup` in `DriverSheetImportService.java` | 0 hits | PASS |
| No `findByName`/`findByDisplayLabel` anti-pattern (D-13) | grep `findByName\|findByDisplayLabel` in `DriverSheetImportService.java` | 0 hits | PASS |
| `findByYear` used (D-01) | grep `findByYear` in `DriverSheetImportService.java` | hit at line 71 | PASS |
| No new Flyway migration (DATA-04) | `ls src/main/resources/db/migration/V*.sql` | V1, V2 only (unchanged) | PASS |
| No `@SessionAttributes` / `Map<Bucket, ...>` typing (D-04/D-06) | grep in service file | 0 hits | PASS |
| Plan commits exist | `git log --oneline af4e43c 8b526d4 c7b9c4c fc752d4` | all 4 present | PASS |

### Requirements Coverage

All 17 Phase 54 requirement IDs from `54-01-PLAN.md` frontmatter cross-referenced against code + tests + REQUIREMENTS.md traceability table.

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| IMPORT-02 | 54-01-PLAN | Auto-detect `^\d{4}$` tabs | VERIFIED | Service line 29/52-55; test `givenMixedTabNames` |
| IMPORT-03 | 54-01-PLAN | Columns A + C read, header skipped | VERIFIED | Service lines 97-106 (header skip + `cellToString` defensive read); test `givenRowShorterThanThreeColumns` |
| IMPORT-04 | 54-01-PLAN | Tabs sorted ascending by year | VERIFIED | Service line 54 `Comparator.comparingInt(Integer::parseInt)`; test `givenTabsInReverseOrder` |
| IMPORT-05 | 54-01-PLAN | Season auto-preselected via repo lookup | VERIFIED (override D-13) | Service lines 71-83; tests `givenMultipleSeasonsForYear`, `givenNoSeasonForYear`. ROADMAP wording `findByName/findByDisplayLabel` superseded by `findByYear` per D-13 — intent satisfied. |
| UX-01 | 54-01-PLAN | NEW_DRIVER bucket | VERIFIED | Service line 180; test `givenNewPsnId` |
| UX-02 | 54-01-PLAN | NEW_ASSIGNMENT bucket | VERIFIED | Service lines 171-172 + 175-176; test `givenExistingDriverNoSeasonDriver` |
| UX-03 | 54-01-PLAN | CONFLICT bucket | VERIFIED | Service lines 160-169; test `givenExistingSeasonDriverDifferentTeam` |
| UX-04 | 54-01-PLAN | FUZZY_SUGGESTION bucket | VERIFIED | Service lines 140-148; test `givenFuzzyCandidate` |
| UX-05 | 54-01-PLAN | UNCHANGED bucket | VERIFIED | Service line 159; test `givenExistingSeasonDriverSameTeam` |
| UX-06 | 54-01-PLAN | ERROR bucket (blank PSN/team, unknown team) | VERIFIED | Service lines 108-125; tests `givenBlankPsnId`, `givenBlankTeamCode`, `givenUnknownTeamCode`, `givenDuplicatePsnInTab` |
| MATCH-01 | 54-01-PLAN | Delegates to DriverMatchingService unchanged | VERIFIED | Service line 137 single call; test `givenExistingPsnIdDifferentCase` + fuzzy test verify delegation; `DriverMatchingService.java` untouched this phase |
| MATCH-02 | 54-01-PLAN | Same PSN in multiple tabs → single Driver identity | VERIFIED | D-07 naive bucketing returns independent rows; identity resolution via `DriverMatchingService.findDriver` returns the same `Driver` object for same PSN (exact-match). Test `givenSamePsnInMultipleTabs` covers preview-side contract; execute-side cross-tab dedup deferred to Phase 55. |
| DATA-01 | 54-01-PLAN | Missing Season = row error, no auto-create | VERIFIED | Service lines 77-82 set null + reason, never calls `.save()`. Tests `givenMultipleSeasonsForYear`/`givenNoSeasonForYear` both assert null. |
| DATA-02 | 54-01-PLAN | Unknown team = row error, no auto-create | VERIFIED | Service lines 121-125, emits `ErrorReason.UNKNOWN_TEAM_CODE`; test `givenUnknownTeamCode` |
| DATA-04 | 54-01-PLAN | No new Flyway migration | VERIFIED | V1 + V2 only, no V3+ file |
| DATA-05 | 54-01-PLAN | RaceLineup untouched | VERIFIED | grep returns 0 hits |
| TEST-01 | 54-01-PLAN | ≥9 given-when-then scenarios, suite green | VERIFIED | 16 tests, all pass, 98.9% line coverage on service |

**Orphaned requirements check:** `grep -E "Phase 54" .planning/REQUIREMENTS.md` confirms the REQUIREMENTS.md traceability table maps exactly these 17 IDs to Phase 54. No orphaned requirements.

Note: REQUIREMENTS.md traceability table still shows "Pending" for all 17 IDs because the user explicitly asked the verifier not to edit REQUIREMENTS.md. These are verified conceptually here; the traceability update is the user's / downstream agent's responsibility.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `DriverSheetImportService.java` | 129 | Redundant `.trim()` on already-trimmed PSN (`cellToString` trims at line 201) | Info | No functional impact; cosmetic per 54-REVIEW.md IR-01 |
| `DriverSheetImportService.java` | 256-260 | `ErrorRow.rawPsnId` / `rawTeamCode` field names suggest raw-source values but carry trimmed values | Info | Forward-looking concern per 54-REVIEW.md IR-02; surface for Phase 55 error rendering. No test impact. |
| `DriverSheetImportService.java` | 250-254 | `UnchangedRow` omits `existingSeasonDriverId` while `ConflictRow` has it | Info | Optional ergonomics hint per 54-REVIEW.md IR-03; leave until Phase 55 surfaces need. |
| `DriverSheetImportService.java` | 174-177 | EXACT match + null `suggestedSeasonId` sub-branch not covered by a dedicated test | Info (advisory) | Behavior is correct (falls through to `NEW_ASSIGNMENT`, skips `SeasonDriverRepository`); missing test is a coverage-completeness issue per 54-REVIEW.md WR-01. JaCoCo still reports 98.9% line coverage overall. Code is reachable and consistent with D-12; not a goal failure. |

No TODO/FIXME/PLACEHOLDER markers, no empty return handlers, no hard-coded stubs, no console-log-only handlers. No blockers.

### Human Verification Required

None. Phase 54 is a pure backend unit-tested service. No UI, no real-time behavior, no external service integration (Google Sheets is mocked; live integration is Phase 55's concern). Every must-have is programmatically verifiable.

### Gaps Summary

No gaps block the phase goal. The phase delivers:

- A pure backend `DriverSheetImportService.preview(String sheetUrl)` that performs zero DB writes (`grep` confirms no `.save(`, `.delete(`, `persist(`, `merge(`, `@Transactional`)
- 16 passing given-when-then tests (target was ≥9), covering all six buckets + D-11 duplicate + D-12 precedence + MATCH-02 cross-tab + ambiguous-season edge cases
- All 17 requirement IDs from 54-01-PLAN.md traced to concrete code + test evidence
- Clean guardrails: no new Flyway migration, no `RaceLineup` reference, no `findByName`/`findByDisplayLabel` anti-pattern (D-13 override documented), no `@SessionAttributes`, no `Map<Bucket,...>` pseudo-typing
- 98.9% JaCoCo line coverage on `DriverSheetImportService`; project-wide gate at 82% passes with 1041 tests green

**Success Criteria crosswalk (ROADMAP §Phase 54):**
- SC#1 ✓ verified (truth #1 + #2)
- SC#2 ✓ verified (truth #3)
- SC#3 ✓ verified with override for `findByName/findByDisplayLabel` wording per D-13 (truth #4)
- SC#4 ✓ verified — preview delegates to DriverMatchingService unchanged (truth #8); same PSN across tabs resolves to the same `Driver` object via exact match. Cross-tab execute-side dedup is explicitly deferred to Phase 55 per D-07.
- SC#5 ✓ verified (truth #5 + DATA-01 + DATA-02)
- SC#6 ✓ verified — 16 @Test methods (exceeds ≥9 target), all green under `./mvnw verify` (truth #12)

The phase goal is achieved. Ready for Phase 55 to consume the service contract unchanged.

---

_Verified: 2026-04-24T23:45:00Z_
_Verifier: Claude (gsd-verifier)_
