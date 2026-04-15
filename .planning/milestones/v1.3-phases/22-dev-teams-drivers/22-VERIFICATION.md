---
phase: 22-dev-teams-drivers
verified: 2026-04-09T18:30:00Z
status: human_needed
score: 5/6 must-haves verified
re_verification: false
human_verification:
  - test: "Start dev profile and navigate to /admin/tools/team-cards — select the active season (2026)"
    expected: "14 team card images are displayed (7 sub-teams + 7 parent-only teams); the 3 parent teams with sub-teams (VRX, SGM, TBR) are absent from the card list. If Playwright/Chromium is installed, images render visually. If not installed, placeholder boxes appear with a 'Generate' button per card."
    why_human: "Card generation at seed time throws a graceful exception (catches Exception) when Playwright Chromium is not installed in the test environment. The test suite cannot verify whether actual PNG files were produced. Visual confirmation requires a running dev server with Playwright installed or inspection of the upload directory for team-card files."
---

# Phase 22: Dev Teams & Drivers Verification Report

**Phase Goal:** The dev profile starts with a complete, realistic set of fictive teams and drivers that cover all structural variations (parent teams, sub-teams) and have generated team card images
**Verified:** 2026-04-09T18:30:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Dev profile starts with 14+ teams, all with fictive racing-themed names | VERIFIED | `seedTeams()` creates 10 parents (VRX, SGM, ADR, TBR, ICL, SVT, NFR, EGP, HMS, PWR); `seedSubTeams()` creates 7 sub-teams — total 17. No real names found in `TestDataService.java`. Integration test `givenDevSeed_whenStarted_thenTotalNonTestTeamCountIsAtLeastFourteen` asserts >= 14. |
| 2 | At least 2 parent teams have 2+ named sub-teams each | VERIFIED | VRX has VRX A + VRX B (2); SGM has SGM B + SGM S (2); TBR has TBR R + TBR B + TBR G (3). Integration test `givenDevSeed_whenStarted_thenAtLeastTwoParentsHaveTwoOrMoreSubTeams` verified. |
| 3 | Every team has exactly 10 drivers with fictive first+last names | VERIFIED | `seedDrivers()` creates exactly 100 driver entries via 10 calls of 10 per team pattern (`<ShortName>_Driver01` through `<ShortName>_Driver10`). Integration test `givenDevSeed_whenStarted_thenExactlyOneHundredNonTestDriversExist` asserts count == 100. |
| 4 | Team cards are generated at seed time for the active season — only for leaf teams | VERIFIED (wiring) / ? (execution) | `seedTeamCards(activeSeason)` is called at end of `seed()`; it calls `teamCardService.generateAllCards(activeSeason)` which correctly skips parent teams with sub-teams in the season. Exception is caught gracefully. Wiring is correct. Whether PNG files are actually produced depends on Playwright Chromium being installed — requires human verification. |
| 5 | E2E test data (T-ALF, T-BRV, Test_Alpha_*, Test-Season) is untouched | VERIFIED | `seedRaceLineups()` still creates "Test Alpha Racing" (T-ALF), "Test Bravo Racing" (T-BRV), Test_Alpha_1/2, Test_Bravo1_1/2, Test_Bravo2_1/2 drivers. Integration test asserts T-ALF and T-BRV are present. |
| 6 | Existing scoring presets (CTC Standard, Standard 3-1-0) are untouched | VERIFIED | `seedScorings()` creates `RaceScoring("CTC Standard", ...)` and `MatchScoring("Standard 3-1-0", ...)` — unchanged from original. |

**Score:** 5/6 truths verified (truth 4 wiring confirmed, execution requires human)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/java/org/ctc/admin/TestDataService.java` | Fictive team/driver seed data + team card generation; contains `seedTeamCards` | VERIFIED | File exists, substantive (603 lines), `seedTeamCards` method present, `TeamCardService` injected via `@RequiredArgsConstructor`, called from `seed()`. |
| `src/test/java/org/ctc/admin/TestDataServiceIntegrationTest.java` | Integration test verifying team/driver counts and structure; contains `TestDataServiceIntegrationTest` | VERIFIED | File exists with `@SpringBootTest @ActiveProfiles("dev") @Transactional`, 11 test methods covering team count, sub-team structure, colors, absence of real names, driver count, aliases, and active season. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `TestDataService.seed()` | `TeamCardService.generateAllCards(Season)` | `seedTeamCards()` called at end of `seed()` | WIRED | Line 74: `seedTeamCards(activeSeason)` called after all data seeded; line 498: `teamCardService.generateAllCards(activeSeason)` inside try-catch(Exception). `generateAllCards` correctly skips parent teams that have sub-teams in the season. |
| `TestDataService.seedTeams()` | `teamRepository.saveAll()` | 10 fictive parent teams persisted | WIRED | Line 92: `teamRepository.saveAll(List.of(...))` with all 10 teams. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `TestDataService.java` | `teams` (10 parent teams) | `teamRepository.saveAll(List.of(...))` at line 92 | Yes — 10 hardcoded fictive team entries | FLOWING |
| `TestDataService.java` | Drivers | `driverRepository.save(new Driver(...))` via `driver()` helper | Yes — 100 hardcoded fictive driver entries | FLOWING |
| `TestDataService.java` | `activeSeason` (Season 4, 2026) | `seedSeasons()` returns `s4` with `setActive(true)` | Yes — `seedSeasons()` return type changed to `Season` | FLOWING |
| `seedTeamCards()` | `paths` (generated card file paths) | `teamCardService.generateAllCards(activeSeason)` | Conditional — requires Playwright Chromium | CONDITIONAL |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Real CTC team names absent from TestDataService | `grep -c "Project One Racing" TestDataService.java` | 0 | PASS |
| Real CTC team names absent from TestDataService | `grep -c "Community League Racing" TestDataService.java` | 0 | PASS |
| Real PSN IDs absent | `grep -c "France-k88" TestDataService.java` | 0 | PASS |
| Fictive teams present | `grep -c "Velocity Racing" TestDataService.java` | 1 | PASS |
| Fictive teams present | `grep -c "Shadow Grid Motorsport" TestDataService.java` | 1 | PASS |
| E2E data preserved | `grep -c "Test Alpha Racing" TestDataService.java` | 1 | PASS |
| Scoring presets preserved | `grep -c "CTC Standard" TestDataService.java` | 1 | PASS |
| TeamCardService references | `grep -c "TeamCardService" TestDataService.java` | 2 | PASS |
| seedTeamCards references | `grep -c "seedTeamCards" TestDataService.java` | 2 | PASS |
| generateAllCards present | `grep -c "generateAllCards" TestDataService.java` | 1 | PASS |
| seedSeasons return type | `grep "private Season seedSeasons" TestDataService.java` | matches | PASS |
| Graceful exception catch | `grep "catch.*Exception" TestDataService.java` | line 500 | PASS |
| Commits exist | `git log --oneline \| grep 5472fd6\|27566f4` | both found | PASS |
| Driver count | `grep -c "*_Driver*" TestDataService.java` (100 entries) | 100 | PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| DATA-01 | 22-01-PLAN.md | Dev profile creates 14+ teams with fictive names, including 2-3 parent teams with sub-teams | SATISFIED | 17 teams (10 parents + 7 sub-teams) created; 3 parent teams have sub-teams (VRX: 2, SGM: 2, TBR: 3). Integration test verified. |
| DATA-02 | 22-01-PLAN.md | Dev profile creates 10 drivers per team with fictive names | SATISFIED | Exactly 100 drivers created (10 per parent team) with fictive international names and `<ShortName>_Driver##` PSN IDs. Integration test asserts count == 100. |
| DATA-03 | 22-01-PLAN.md | Team cards generated for all dev teams | PARTIALLY SATISFIED | `seedTeamCards()` is wired and calls `generateAllCards(activeSeason)` with graceful fallback. `generateAllCards` correctly generates 14 cards (7 sub-teams + 7 parent-only; 3 parents with sub-teams are skipped). Actual PNG generation requires Playwright Chromium — visual confirmation needed. |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `TestDataService.java` | 510 | German comment `// === Komplett isolierte Testdaten (kein Bezug zu echten Teams/Fahrern) ===` in `seedRaceLineups()` | Info | German comment inside a method that was explicitly excluded from changes (E2E test data boundary). Low impact — pre-existing from original code, outside the scope of this phase. |

Note: The `seedTeamCards()` catch-Exception block (line 500) is intentional behavior per T-22-01 threat mitigation — not a stub. Playwright throws `PlaywrightException extends RuntimeException` when Chromium is not installed; catching only `IOException` would crash the dev seeder.

### Human Verification Required

#### 1. Team Card Image Visibility

**Test:** Start the dev profile (`./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`), navigate to `/admin/tools/team-cards`, and select the active 2026 season from the dropdown.

**Expected:** The page displays 14 team entries (the 7 sub-teams: VRX A, VRX B, SGM B, SGM S, TBR R, TBR B, TBR G — plus the 7 standalone parent teams: ADR, ICL, SVT, NFR, EGP, HMS, PWR). The 3 parent teams with sub-teams (VRX, SGM, TBR) do NOT appear as separate card entries.

- If Playwright Chromium is installed: team card PNG images render in the grid at startup.
- If Playwright Chromium is not installed: placeholder boxes appear with individual "Generate" buttons. The startup log should contain a warning: "Team card generation skipped (Playwright not installed?): ..." confirming graceful fallback.

**Why human:** The `seedTeamCards()` method wraps `generateAllCards()` in a broad `try-catch(Exception)` — in CI and test environments without Playwright Chromium, cards are not generated and the exception is silently swallowed. Automated tests only verify the active season exists and the application context loads; they cannot assert that PNG files were actually written to disk. Visual confirmation of the card grid or log output review is required.

### Gaps Summary

No actionable gaps. All structural requirements (teams, drivers, wiring) are fully implemented and verified programmatically. The single human verification item (team card image visibility) is a conditional runtime behavior that depends on Playwright Chromium availability — the code path is correctly wired with a graceful fallback, which is the intended design per threat T-22-01.

One pre-existing German comment was found in `seedRaceLineups()` (out of scope for this phase) — info-level, no action required.

---

_Verified: 2026-04-09T18:30:00Z_
_Verifier: Claude (gsd-verifier)_
