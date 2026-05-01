---
plan_id: 61-gap-03
phase: 61-cleanup-quality-gate
status: complete
completed: 2026-05-01T23:10:00Z
gap_closure: true
---

# 61-gap-03 — Stale comments: dataimport + sitegen + gt7sync + src/test

## What changed

Stripped Phase-narrative prefixes from comments across:
- `src/main/java/org/ctc/dataimport/` (CsvImportService, DriverSheetImportService)
- `src/main/java/org/ctc/sitegen/` (SiteGeneratorService, YouTubeScraperService)
- `src/main/java/org/ctc/gt7sync/` (clean — algorithmic "Phase 1/2/3" markers stayed)
- 43 src/test files (TestHelper, all admin controller/service tests, all domain
  service+repository tests, the two E2E tests, sitegen + dataimport test packages, V4/V6
  migration tests)

Method: a Perl in-place rewrite stripped the recurring prefixes (`// Phase 6X MIGR-06:`,
`// Phase 6X D-NN:`, `// D-NN:`, ` (D-NN)`, ` per D-NN`, `Phase 5X D-NN`, etc.) while
preserving the substantive comment text and the BDD scaffold structure.

## Commits

- `b580fd0 docs(61-gap-03): remove stale comments from dataimport, sitegen, gt7sync`
- `3e50df6 docs(61-gap-03): remove stale phase-narrative comments from src/test files`

## Files touched

47 files total — 4 in src/main + 43 in src/test:

src/main:
- `dataimport/CsvImportService.java`, `dataimport/DriverSheetImportService.java`
- `sitegen/SiteGeneratorService.java`, `sitegen/YouTubeScraperService.java`

src/test (selected — full list in commit `3e50df6`):
- `TestHelper.java` (top-level)
- All admin controller tests (Season/SeasonPhase/SeasonPhaseGroup/Playoff/Race/Standings/
  Team/Track/Car/RaceGraphic/RaceFormData)
- All domain service tests (Standings/SwissPairing/SeasonPhase/SeasonManagement/
  DriverRanking/Playoff/PlayoffSeeding/PlayoffBracketView/Match/Matchday/MatchdayGenerator/
  Race/RaceCalendar/RaceFormData/Scoring/PhaseTestFixtures/MatchdayService)
- All repository ITs (PhaseTeam, SeasonPhase, SeasonPhaseGroup)
- E2E: GroupsSeasonE2ETest, LegacyMigratedSeasonE2ETest, SiteGeneratorE2ETest
- dataimport tests (CsvImportServiceTest, DriverSheetImportServiceTest, IT variants)
- sitegen tests (SiteGeneratorServiceTest, IT)
- DB migration tests (V4MigrationSmokeIT, V6MigrationTest)
- model entity ITs (PhaseTeamUniquenessIntegrationTest, SeasonPhaseEntityIntegrationTest)
- admin DTO test (SeasonPhaseFormTest)
- admin TestDataServiceIntegrationTest

## Diff size

- src/main: 4 files, 64 insertions, 72 deletions
- src/test: 43 files, 198 insertions, 204 deletions

## Test gate

`./mvnw test -Dtest='SwissPairingServiceTest,StandingsServiceTest,SeasonPhaseServiceTest,
DriverRankingServiceTest,PlayoffServiceTest,MatchdayServiceTest,SeasonManagementServiceTest,
DriverSheetImportServiceTest,SiteGeneratorServiceTest,CsvImportServiceTest'`

→ `Tests run: 320, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS

`./mvnw test-compile` → BUILD SUCCESS (verifies all 43 test files still compile).

## Acceptance criteria

- [x] `grep -rn -E "Phase [56][0-9] (MIGR-06|D-[0-9]+|WR-0?[0-9]+|CR-0?[0-9]+|IN-0?[0-9]+)" src/main/java/org/ctc/dataimport src/main/java/org/ctc/sitegen src/main/java/org/ctc/gt7sync` → 0 lines
- [x] `grep -rn -E "Phase [56][0-9] (MIGR-06|D-[0-9]+|WR-0?[0-9]+|CR-0?[0-9]+|IN-0?[0-9]+)" src/test/java` → 0 lines
- [x] BDD scaffolds (`// given|when|then`) preserved (perl rewrite was prefix-only)
- [x] T- prefixed identifiers preserved
- [x] Targeted Surefire suite GREEN (320 tests)

## Self-Check: PASSED

dataimport, sitegen, gt7sync, and 43 src/test files cleaned. No behavior change.
Compile + targeted-test gate green.
