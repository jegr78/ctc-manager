---
phase: 22-dev-teams-drivers
plan: 01
subsystem: testing
tags: [java, spring-boot, testdata, seed, h2, jpa]

requires:
  - phase: 21-english-code
    provides: English codebase with consistent naming patterns

provides:
  - Fictive racing-themed seed data: 10 parent teams + 7 sub-teams with colors
  - 100 fictive drivers (10 per team) with unique PSN IDs and international names
  - TeamCardService injected into TestDataService with graceful Playwright fallback
  - Integration test verifying team/driver structure and absence of real CTC data

affects: [dev-profile, TestDataService, seed-data, manual-testing]

tech-stack:
  added: []
  patterns:
    - "Filter integration tests by known seeded entity keys to avoid false failures from shared Spring context"
    - "Seed team cards at end of seed() with try-catch Exception for Playwright unavailability"
    - "seedSeasons() returns Season for use in seedTeamCards()"

key-files:
  created:
    - src/test/java/org/ctc/admin/TestDataServiceIntegrationTest.java
  modified:
    - src/main/java/org/ctc/admin/TestDataService.java

key-decisions:
  - "Filter integration tests by known seeded short names (SEEDED_PARENT_SHORT_NAMES, SEEDED_SUB_SHORT_NAMES constants) to isolate from other tests sharing the Spring context"
  - "Use catch(Exception) not catch(IOException) in seedTeamCards() because Playwright throws runtime exceptions when Chromium is not installed"
  - "seedSeasons() return type changed from void to Season to pass active season to seedTeamCards()"
  - "Driver PSN IDs follow <ShortName>_Driver## pattern for uniqueness and easy filtering in tests"

patterns-established:
  - "Test isolation: use explicit constant sets of known seeded IDs rather than generic prefix filters"

requirements-completed: [DATA-01, DATA-02, DATA-03]

duration: 25min
completed: 2026-04-09
---

# Phase 22 Plan 01: Dev Teams & Drivers Summary

**TestDataService refactored with 10 fictive racing teams (17 total with sub-teams), 100 fictive drivers (10 per team), and TeamCardService integration with graceful Playwright fallback**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-04-09T17:40:00Z
- **Completed:** 2026-04-09T17:65:00Z
- **Tasks:** 3 (implemented together in one TDD cycle due to interdependency)
- **Files modified:** 2

## Accomplishments

- Replaced all 10 real CTC parent teams (P1R, CLR, TNR, etc.) with fictive racing-themed teams (VRX, SGM, ADR, TBR, ICL, SVT, NFR, EGP, HMS, PWR) with hex color schemes
- Replaced 3 real sub-team groups with new fictive sub-teams: VRX (2), SGM (2), TBR (3) = 7 total sub-teams, 17 teams total
- Replaced all real CTC drivers with exactly 100 fictive drivers (10 per team) using pattern `<ShortName>_Driver##` PSN IDs and international-style nicknames
- Injected TeamCardService into TestDataService; seedTeamCards() called at end of seed() with try-catch for Playwright unavailability (generates 14 cards: 7 sub-teams + 7 parent-only teams; 3 parents with sub-teams are correctly skipped by generateAllCards)
- E2E test data (T-ALF, T-BRV, Test_Alpha_*, Test_Bravo_*) and scoring presets (CTC Standard, Standard 3-1-0) untouched
- Integration test covers: team count, sub-team structure, color presence, absence of real names, driver count, alias presence, active season existence

## Task Commits

1. **Tasks 1-3: Fictive data + integration test + team card generation** - `5472fd6` (test + implementation together)
2. **Fix: test isolation for shared Spring context** - `27566f4` (fix)

## Files Created/Modified

- `src/main/java/org/ctc/admin/TestDataService.java` - All real data replaced with fictive; TeamCardService injected; seedSeasons() returns Season; seedTeamCards() added
- `src/test/java/org/ctc/admin/TestDataServiceIntegrationTest.java` - New integration test with 11 test methods verifying team/driver structure

## Decisions Made

- **Test isolation approach:** Used explicit constant sets (`SEEDED_PARENT_SHORT_NAMES`, `SEEDED_SUB_SHORT_NAMES`) to filter test assertions. Other integration tests (e.g., `TeamControllerTest`) create teams without `@Transactional`, so generic prefix filters like `!startsWith("T-")` would count their data too. Filtering by known seeded keys makes tests robust.
- **Driver PSN ID pattern:** `<ShortName>_Driver##` enables both uniqueness (satisfies `@Column(unique=true)`) and exact regex filtering in tests (`(VRX|SGM|...)_Driver\d+`).
- **Catch `Exception` not `IOException`:** Playwright throws `PlaywrightException extends RuntimeException` when Chromium is not installed — catching only `IOException` would propagate these and crash the dev seeder.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed integration test false failures due to shared Spring context**
- **Found during:** Task 1 (integration test GREEN phase)
- **Issue:** Tests counting all non-test-prefixed teams/drivers included entities created by `TeamControllerTest` (which saves "MVR" team without rollback). Count tests failed: 21 parents instead of 10, 106 drivers instead of 100, MVR team had no colors.
- **Fix:** Replaced generic prefix filters with explicit constant sets (`SEEDED_PARENT_SHORT_NAMES`, `SEEDED_SUB_SHORT_NAMES`) and regex pattern for drivers (`(VRX|SGM|...)_Driver\d+`).
- **Files modified:** `src/test/java/org/ctc/admin/TestDataServiceIntegrationTest.java`
- **Verification:** All 11 integration tests pass, full suite of 863 tests passes.
- **Committed in:** `27566f4`

---

**Total deviations:** 1 auto-fixed (Rule 1 — Bug)
**Impact on plan:** Necessary for test correctness. No scope creep.

## Issues Encountered

- Shared Spring ApplicationContext in `@SpringBootTest` tests means entity changes made by non-transactional tests (e.g., `TeamControllerTest.givenValidTeamForm_whenSaveTeam_thenRedirectsAndPersists`) persist in H2 during the entire test suite run. Fixed by using precise filtering constants.

## Known Stubs

None — all data is fully wired. TeamCardService is injected and called. The graceful catch in `seedTeamCards()` is intentional behavior (not a stub) for environments without Playwright Chromium.

## Threat Flags

None — no new network endpoints, auth paths, or trust boundaries introduced. Dev-only seed data.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- Dev profile now provides realistic fictive seed data for all development and manual testing
- 17 teams with proper color schemes ready for team card generation testing
- 100 drivers with international-style names ready for race/standings testing
- `generateAllCards` correctly skips 3 parent teams (VRX, SGM, TBR) that have sub-teams in the active season, generating 14 cards

---

## Self-Check: PASSED

- `src/test/java/org/ctc/admin/TestDataServiceIntegrationTest.java` — FOUND
- `src/main/java/org/ctc/admin/TestDataService.java` — FOUND (modified)
- Commit `5472fd6` — FOUND
- Commit `27566f4` — FOUND
- 863 tests pass, 84% coverage (>= 82% minimum)

---
*Phase: 22-dev-teams-drivers*
*Completed: 2026-04-09*
