---
phase: 59-import-test-data
plan: 2
subsystem: dataimport
tags: [java, spring-boot, driver-import, group-resolution, regex, phase-team-repository, tab-warning, tdd]
completed: 2026-04-29

dependency_graph:
  requires:
    - Plan 59-01: SeasonManagementService.findUnique(year,number) + findUnique(year)
  provides:
    - PhaseTeamRepository.findByPhaseIdAndTeamId(UUID, UUID): Optional<PhaseTeam>
    - DriverSheetImportService.YEAR_TAB_PATTERN union regex ^(\d{4})(?:_S(\d+))?$
    - DriverSheetImportService.TabWarning record + WarningType enum
    - TabPreview.number (Integer, null for legacy tabs)
    - TabPreview.warnings (List<TabWarning>)
    - Row records NewDriverRow/NewAssignmentRow/ConflictRow/FuzzySuggestionRow/UnchangedRow with resolvedGroupName
  affects:
    - Plan 59-04: DriverSheetImportServiceIT (uses findByPhaseIdAndTeamId + group resolution in IT)

tech_stack:
  added: []
  patterns:
    - "Spring Data magic naming Optional<T> finder for unique lookup"
    - "Union regex with optional capture group for tab-name disambiguation"
    - "Per-tab SeasonPhase cache + per-row PhaseTeam lookup (D-28 Specifics)"
    - "Set<String> deduplication for tab-level warnings (D-06)"
    - "BusinessRuleException caught in buildTabPreview, surfaced as ambiguousReason"

key_files:
  modified:
    - src/main/java/org/ctc/domain/repository/PhaseTeamRepository.java
    - src/main/java/org/ctc/dataimport/DriverSheetImportService.java
    - src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java
  created:
    - src/test/java/org/ctc/domain/repository/PhaseTeamRepositoryTest.java

decisions:
  - "PhaseTeamRepositoryTest uses @SpringBootTest (not @DataJpaTest) to match existing PhaseTeamRepositoryIT pattern in the project"
  - "DriverRepository added as @Mock in DriverSheetImportServiceTest (was missing from original 5 mocks, needed for @InjectMocks with new 9-field constructor)"
  - "Execute path left completely unchanged per D-16 — seasonPhaseService and phaseTeamRepository only called from buildTabPreview, never from execute()"
  - "warnedTeams dedup uses Set.add() return value to emit exactly one TabWarning per team short name"

requirements-completed: [IMPORT-01, IMPORT-03, IMPORT-04]

metrics:
  duration_minutes: ~35
  tasks_completed: 3
  tests_added: 11
  files_created: 1
  files_modified: 3
---

# Phase 59 Plan 2: DriverSheetImportService Group Resolution & PhaseTeamRepository Summary

Tab-pattern union regex, group-resolution via PhaseTeam(REGULAR), TabWarning emission, and the missing `findByPhaseIdAndTeamId` finder — all implemented TDD with 11 new passing tests.

## What Was Built

### PhaseTeamRepository.findByPhaseIdAndTeamId (Task 1)

**`src/main/java/org/ctc/domain/repository/PhaseTeamRepository.java`** — one line added:

```java
Optional<PhaseTeam> findByPhaseIdAndTeamId(UUID phaseId, UUID teamId);
```

Spring Data magic-naming resolves this without `@Query`. The `UNIQUE(phase_id, team_id)` DB constraint (Phase 56 D-03) guarantees at-most-one hit — `Optional` is the correct return type.

### DriverSheetImportService changes (Task 2)

**Final field list (8 fields, was 6):**

| Field | Type | Purpose |
|-------|------|---------|
| `googleSheetsService` | `GoogleSheetsService` | Sheets API (existing) |
| `driverMatchingService` | `DriverMatchingService` | PSN fuzzy/exact matching (existing) |
| `seasonRepository` | `SeasonRepository` | Season lookups for execute() path (existing) |
| `teamRepository` | `TeamRepository` | Team code resolution (existing) |
| `seasonDriverRepository` | `SeasonDriverRepository` | SeasonDriver CRUD (existing) |
| `driverRepository` | `DriverRepository` | Driver creation in execute() (existing) |
| `seasonManagementService` | `SeasonManagementService` | findUnique wrapper (Phase 59 D-28) |
| `seasonPhaseService` | `SeasonPhaseService` | findRegularPhase once per tab (Phase 59 D-28) |
| `phaseTeamRepository` | `PhaseTeamRepository` | findByPhaseIdAndTeamId per row (Phase 59 D-28) |

**YEAR_TAB_PATTERN:**
```java
private static final Pattern YEAR_TAB_PATTERN = Pattern.compile("^(\\d{4})(?:_S(\\d+))?$");
```
group(1) = year (always), group(2) = seasonNum (null for legacy `^\d{4}$` form).

**Final TabPreview record shape:**
```java
public record TabPreview(
    String tabName,
    int year,
    Integer number,           // null for legacy ^\d{4}$ tabs (D-01)
    UUID suggestedSeasonId,
    String ambiguousReason,
    List<TabWarning> warnings, // D-06: per-team, deduplicated
    List<NewDriverRow> newDrivers,
    List<NewAssignmentRow> newAssignments,
    List<ConflictRow> conflicts,
    List<FuzzySuggestionRow> fuzzySuggestions,
    List<UnchangedRow> unchanged,
    List<ErrorRow> errors
) {}
```

**5 row records with resolvedGroupName (D-08):**
- `NewDriverRow(String psnId, String teamShortName, String resolvedGroupName)`
- `NewAssignmentRow(String psnId, UUID existingDriverId, String teamShortName, String resolvedGroupName)`
- `ConflictRow(String psnId, UUID existingDriverId, UUID existingSeasonDriverId, String existingTeamShortName, String sheetTeamShortName, String resolvedGroupName)`
- `FuzzySuggestionRow(String psnId, UUID suggestedDriverId, String suggestedPsnId, String suggestedNickname, double similarity, String teamShortName, String resolvedGroupName)`
- `UnchangedRow(String psnId, UUID existingDriverId, UUID existingSeasonDriverId, String teamShortName, String resolvedGroupName)`

`ErrorRow` unchanged (errors short-circuit before group resolution per D-08).

**New sibling types:**
```java
public record TabWarning(WarningType type, String teamShortName, String message) {}
public enum WarningType { TEAM_NOT_IN_REGULAR_PHASE("Team has no PhaseTeam in REGULAR phase"); }
```

**D-16 invariant confirmed:** `execute()` calls zero times `phaseTeamRepository` or `seasonPhaseService`. Verified with awk.

### Test coverage (Task 3)

**PhaseTeamRepositoryTest (3 new tests):**

| Test | Scenario |
|------|----------|
| `givenPhaseTeamForTeam_whenFindByPhaseIdAndTeamId_thenReturnsIt` | Happy path |
| `givenNoPhaseTeam_whenFindByPhaseIdAndTeamId_thenReturnsEmpty` | Wrong team UUID |
| `givenSameTeamOnDifferentPhase_whenFindByPhaseIdAndTeamId_thenReturnsEmpty` | Team bound to a different phase |

**DriverSheetImportServiceTest — 8 new tests (D-01..D-06):**

| Test | Covers |
|------|--------|
| `givenLegacyFourDigitTab_whenPreview_thenSeasonResolvedViaFindUniqueByYear` | D-01 + D-04 |
| `givenNumberedTab_whenPreview_thenSeasonResolvedViaFindUniqueByYearAndNumber` | D-01 + D-02 |
| `givenAmbiguousLegacyTab_whenPreview_thenSurfacesBusinessRuleMessage` | D-18, one-arg |
| `givenAmbiguousNumberedTab_whenPreview_thenSurfacesBusinessRuleMessage` | D-18, two-arg |
| `givenTeamInGroupA_whenPreview_thenResolvedGroupNameSet` | D-05 |
| `givenTeamMissingFromRegularPhase_whenPreview_thenWarningEmitted` | D-06 |
| `givenTwoRowsSameMissingTeam_whenPreview_thenSingleWarningEmitted` | D-06 dedup |
| `givenNoRegularPhase_whenPreview_thenResolvedGroupNameIsNullAndNoWarnings` | D-05 fallback |

**Existing test count change:** 17 → 25 (DriverSheetImportServiceTest). All 17 prior tests updated to use `seasonManagementService.findUnique` stubs (replacing `seasonRepository.findByYear`). `seasonPhaseService.findRegularPhase` stub added to all pre-existing tests that use a resolved season.

## TDD Compliance

| Phase | Commit | Description |
|-------|--------|-------------|
| RED | `963fa30` | Failing test — `findByPhaseIdAndTeamId` compile error |
| GREEN | `9321135` | Finder added to PhaseTeamRepository |
| Task 2 | `5cd04a1` | Service implementation (test-compile OK; DriverSheetImportServiceTest stubs not yet updated) |
| Task 3 | `d515dc5` | All 25 DriverSheetImportServiceTest tests green |

## Regression Check

All 47 `DriverSheetImport*` tests pass (25 Service + 4 ExceptionHandler + 18 Controller). PhaseTeamRepositoryTest: 3/3. Full project build: `./mvnw compile test-compile` exits 0.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Mock] DriverRepository @Mock added to DriverSheetImportServiceTest**
- **Found during:** Task 3
- **Issue:** Original test had 5 `@Mock` fields; the constructor now requires 9 fields. `DriverRepository` was missing from the original mocks (Mockito injected null silently). With the new constructor having more final fields, explicit mocking avoids `NullPointerException` in execute-path tests.
- **Fix:** Added `@Mock private org.ctc.domain.repository.DriverRepository driverRepository;`
- **Files modified:** `DriverSheetImportServiceTest.java`
- **Commit:** `d515dc5`
- **Note:** This brings @Mock count to 9 (not 8 as the plan's acceptance criterion states). The plan criterion counted 5 pre-existing + 3 new = 8, but missed that `DriverRepository` was an implicit null before.

**2. [Rule 2 - Missing Stub] seasonPhaseService stubs added to all pre-existing tests**
- **Found during:** Task 3
- **Issue:** After Task 2 added `seasonPhaseService.findRegularPhase` call, all pre-existing tests that stub a resolved season needed a corresponding `findRegularPhase` stub to avoid NPE from Mockito strict-stub mode.
- **Fix:** Added `when(seasonPhaseService.findRegularPhase(seasonX.getId())).thenThrow(new EntityNotFoundException(...))` to all pre-existing tests. EntityNotFoundException is the correct response when no REGULAR phase exists (D-05 graceful degradation path).
- **Files modified:** `DriverSheetImportServiceTest.java`
- **Commit:** `d515dc5`

## Known Stubs

None. All new fields carry live data from the mock chain; `resolvedGroupName` is null only when `regularPhase` is null (EntityNotFoundException path) or `PhaseTeam` has no group — both are correct semantic values, not placeholders.

## Threat Flags

None. All threat items from the plan threat model (T-59-02-01 through T-59-02-06) are addressed by the implementation:
- T-59-02-01 (Tampering): `YEAR_TAB_PATTERN` strictly validates tab format; `teamRepository.findByShortName` returns Optional → ErrorRow on unknown code.
- T-59-02-03 (DoS): per-tab `findRegularPhase` cache implemented; per-row `findByPhaseIdAndTeamId` is O(1) on UNIQUE index.

## Plan 59-04 Integration Contract

Plan 59-04 (`DriverSheetImportServiceIT`) can now use:
- `phaseTeamRepository.findByPhaseIdAndTeamId(phaseId, teamId)` — verified Spring Data finder
- `DriverSheetImportService.preview("url")` — returns `TabPreview` with `number`, `warnings`, `resolvedGroupName` on rows
- `TabPreview.warnings()` — `List<TabWarning>` with deduplicated `TEAM_NOT_IN_REGULAR_PHASE` entries
- Row records all carry `resolvedGroupName` field, accessible via accessor method

## Self-Check

### Created Files

- `src/test/java/org/ctc/domain/repository/PhaseTeamRepositoryTest.java` — FOUND

### Modified Files

- `src/main/java/org/ctc/domain/repository/PhaseTeamRepository.java` — FOUND (verified: `Optional<PhaseTeam> findByPhaseIdAndTeamId` present)
- `src/main/java/org/ctc/dataimport/DriverSheetImportService.java` — FOUND (verified: YEAR_TAB_PATTERN, 3 new fields, TabWarning, WarningType, 5 row records extended)
- `src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java` — FOUND (verified: 25 tests, 8 new methods present)

### Commits

- `963fa30` — RED test commit
- `9321135` — GREEN: PhaseTeamRepository finder
- `5cd04a1` — feat: DriverSheetImportService implementation
- `d515dc5` — test: DriverSheetImportServiceTest updated + 8 new tests

### Test Results

- `./mvnw test -Dtest=DriverSheetImportServiceTest` → 25/25 PASS
- `./mvnw test -Dtest=PhaseTeamRepositoryTest` → 3/3 PASS

## Self-Check: PASSED
