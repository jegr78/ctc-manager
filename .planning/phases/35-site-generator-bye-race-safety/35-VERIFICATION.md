---
phase: 35-site-generator-bye-race-safety
verified: 2026-04-14T20:08:00Z
status: passed
score: 5/5 must-haves verified
overrides_applied: 0
gaps: []
---

# Phase 35: Site Generator Bye-Race Null Safety — Verification Report

**Phase Goal:** SiteGeneratorService handles bye races (null home/away team) without NPE during site generation
**Verified:** 2026-04-14T20:08:00Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | SiteGeneratorService.toRaceView() processes a bye race (null homeTeam or awayTeam) without throwing NPE | VERIFIED | Line 274-275: `var homeTeam = race.getHomeTeam(); String homeShortName = homeTeam != null ? homeTeam.getShortName() : "Bye";` — null guard present; awayTeam already guarded at line 290 |
| 2 | Site generation completes successfully when the race list includes bye matches | VERIFIED | Test `givenByeRaceInSeason_whenGenerate_thenCompletesWithoutNPE` creates a bye match (null awayTeam, bye=true) and asserts `result.hasErrors()` is false and `result.getPagesGenerated() > 0` — all 12 tests pass |
| 3 | Existing 11 integration tests continue to pass unchanged | VERIFIED | `./mvnw test -Dtest=SiteGeneratorServiceTest` reports: Tests run: 12, Failures: 0, Errors: 0, Skipped: 0 |
| 4 | SiteGeneratorService.java contains "homeTeam != null" null guard | VERIFIED | Line 275: `String homeShortName = homeTeam != null ? homeTeam.getShortName() : "Bye";` — confirmed by grep |
| 5 | SiteGeneratorServiceTest.java contains test method "givenByeRaceInSeason_whenGenerate_thenCompletesWithoutNPE" | VERIFIED | Test method at line 291 — contains `byeMatch.setBye(true)`, `new Match(byeMatchday, homeTeam, null)`, `assertFalse(result.hasErrors()...)` |

**Score:** 5/5 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/java/org/ctc/sitegen/SiteGeneratorService.java` | Null-safe toRaceView() with homeTeam guard | VERIFIED | Contains `homeTeam != null`; zero occurrences of `race.getHomeTeam().getId()` or `race.getHomeTeam().getShortName()` |
| `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java` | Bye race integration test | VERIFIED | Contains `givenByeRaceInSeason_whenGenerate_thenCompletesWithoutNPE` at line 291 |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `SiteGeneratorServiceTest.java` | `SiteGeneratorService.java` | `siteGeneratorService.generate()` exercises `toRaceView()` with bye race | WIRED | `siteGeneratorService.generate()` called at line 307 in the bye-race test; 12 total call sites confirmed by grep |

Note: gsd-tools key-link verification tool reported a false negative (escaped regex pattern issue). Manual grep confirmed the pattern exists at 12 locations in the test file.

### Data-Flow Trace (Level 4)

Not applicable — `toRaceView()` is a private transformation method, not a data-fetching component. The test directly exercises the code path through `siteGeneratorService.generate()`, which calls `toRaceView()` internally. The bye-race test provides a `Race` with null awayTeam and verifies no errors are raised.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| 12 tests pass in SiteGeneratorServiceTest | `./mvnw test -Dtest=SiteGeneratorServiceTest` | Tests run: 12, Failures: 0, Errors: 0 | PASS |
| No unguarded `race.getHomeTeam()` chained calls remain | `grep -c "race.getHomeTeam()."` in SiteGeneratorService.java | 0 matches | PASS |
| Null guard at line 275 exists | `grep "homeTeam != null"` | Line 275 found | PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| DATA-03 | 35-01-PLAN.md | Race services handle null home/away teams without NPE (bye matches, unlinked races) | SATISFIED (extended) | Phase 31 fixed RaceFormDataService and ScoringService; Phase 35 extends coverage to SiteGeneratorService.toRaceView(). Four unguarded `getHomeTeam()` call sites replaced with null-safe `homeShortName` pattern. |

**DATA-03 scope note:** REQUIREMENTS.md marks DATA-03 as "Pending" with traceability to "Phase 31, Phase 35". Phase 35 delivers the SiteGeneratorService portion. Full closure of DATA-03 depends on Phase 31 verification also confirming the RaceFormDataService and ScoringService portions.

### Anti-Patterns Found

None. Scans for TODO/FIXME/HACK/placeholder comments, empty return statements, and stub patterns returned no matches in either modified file.

### Human Verification Required

None. All must-haves are verifiable programmatically.

### Gaps Summary

No gaps. All five must-haves verified against the codebase:

1. The null guard (`homeTeam != null`) is present at line 275 of SiteGeneratorService.java.
2. All four previously unguarded `race.getHomeTeam()` call sites (old lines 274, 291, 294, 300) have been replaced — zero occurrences of `race.getHomeTeam().getId()` or `race.getHomeTeam().getShortName()` remain.
3. The new test method `givenByeRaceInSeason_whenGenerate_thenCompletesWithoutNPE` exists at line 291 of SiteGeneratorServiceTest.java and exercises the bye-race path end-to-end.
4. All 12 tests (11 original + 1 new) pass with BUILD SUCCESS.
5. Two commits (c17a801, db0df91) document the TDD sequence: test first, then fix.

Phase goal achieved.

---
_Verified: 2026-04-14T20:08:00Z_
_Verifier: Claude (gsd-verifier)_
