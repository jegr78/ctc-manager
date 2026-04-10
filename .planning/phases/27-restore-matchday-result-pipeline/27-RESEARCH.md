# Phase 27: Restore Matchday/Result Seed Pipeline - Research

**Researched:** 2026-04-10
**Domain:** TestDataService adaptation — seed pipeline for fictive teams
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Restore `seedMatchdaysAndResults()` and its helper methods (`seedLeagueSeason`, `seedSwissSeason`, `seedRoundRobinSeason`) from git commit `0396427` into `TestDataService.java`
- **D-02:** Adapt all team/driver references from the real CTC teams (used in Phase 23) to the fictive teams seeded by Phase 22/24 (VRX, SGM, ADR, TBR, ICL, SVT, NFR, EGP, HMS, PWR)
- **D-03:** Re-add the `seedMatchdaysAndResults()` call in the `seed()` method after `seedSeasonDrivers()`
- **D-04:** League format (S4 2026): 5 matchdays with Match + Race + RaceLineup + RaceResult per matchday
- **D-05:** Swiss format (S2 2024): 5 matchdays, each with 2 races, same result pipeline
- **D-06:** Round Robin format (S1 2023): 2 groups, 3 matchdays per group with full result pipeline
- **D-07:** Use `ScoringService.calculatePoints()` to compute points from positions (not hard-coded)
- **D-08:** Call `aggregateMatchScores()` after saving results for each race
- **D-09:** Inject `ScoringService` into `TestDataService` if not already present
- **D-10:** Integration tests verifying: League season has 5 matchdays with non-zero standings
- **D-11:** Integration tests verifying: Swiss season has 5 matchdays with non-zero standings
- **D-12:** Integration tests verifying: Round Robin seasons have 3 matchdays each with results
- **D-13:** Integration test verifying: Points computed by ScoringService are non-zero and consistent

### Claude's Discretion

- Exact matchday naming convention (e.g., "Matchday 1", "MD 1")
- Number of race results per race (should match team count in format)
- Position assignment strategy for results (random, sequential, or round-robin)

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| DATA-04 | Dev profile creates League format season with matchdays, races, and results | S4 2026 has 14 fictive teams; 5 matchdays x 7 matches x 1 race seeded via `seedLeagueSeason()` |
| DATA-05 | Dev profile creates Swiss format season with matchdays, races, and results | S2 2024 has 10 parent fictive teams; 5 matchdays x 5 matches x 2 races |
| DATA-06 | Dev profile creates Round Robin format season (2 groups) with matchdays, races, and results | S1 2023 Groups A+B each have 6 fictive teams; 3 matchdays x 3 matches x 2 races |
| DATA-07 | Race results use actual existing scoring system for point calculation | `ScoringService.calculatePoints()` + `aggregateMatchScores()` called for every race |
</phase_requirements>

---

## Summary

Phase 27 is a targeted adaptation task, not a ground-up rebuild. The `seedMatchdaysAndResults()` method and its helpers (`seedLeagueSeason`, `seedSwissSeason`, `seedRoundRobinSeason`, `seedRace`) already exist verbatim in `TestDataService.java` on the current branch (`gsd/v1.3-english-test-data`), carried forward from Phase 23 (commit `0396427`). The method structure, JPA flush/detach pattern, scoring integration, and position-rotation logic are all correct and already verified working.

**The only work required is a data-substitution:** every reference to a real CTC team (P1R, CLR, TCR, ART, AHR, MRL, GXR, DTR, VEZ, TNR) and their real PSN-ID drivers must be replaced with the corresponding fictive teams (VRX, SGM, ADR, TBR, ICL, SVT, NFR, EGP, HMS, PWR, plus sub-teams VRX A, VRX B, SGM B, SGM S, TBR R, TBR B, TBR G) and their fictive `{TEAM}_DriverXX` PSN IDs.

An additional complication: the current branch does not yet include the fictive-team changes from `gsd/v1.0-milestone` (Phase 24). The `seedTeams()`, `seedSubTeams()`, `seedSeasons()`, and `seedDrivers()` methods still use real CTC teams. Phase 27 must update `seedMatchdaysAndResults()` consistently with the fictive team/driver data that will exist after those surrounding methods are also updated. However, because the Season structure on both branches uses the same years/numbers/names (S1 2023 Group A/B, S2 2024, S4 2026), the season-lookup lambdas in `seedMatchdaysAndResults()` require no changes — only the driver/team assignment maps need to be replaced.

One additional scope item is required: `seedSeasons()` and `seedSeasonDrivers()` on the current branch also reference the old real teams. For `seedMatchdaysAndResults()` to work with fictive teams, all upstream methods in `seed()` that populate teams, sub-teams, drivers, seasons, and season-drivers must also be updated in the same PR. This is in scope because D-02 says "adapt all team/driver references" — the pipeline cannot run correctly in isolation.

**Primary recommendation:** Replace the entire driver-assignment and team-reference section inside `seedMatchdaysAndResults()` using the fictive PSN ID pattern `{TEAM}_Driver0N`. Simultaneously update `seedTeams()`, `seedSubTeams()`, `seedSeasons()`, `seedDrivers()`, `seedSeasonDrivers()`, and add `@Profile("dev")` and `seedTeamCards()` — all of which are present on `gsd/v1.0-milestone` as the reference implementation.

---

## Critical Context: Branch State

### What Exists on Current Branch (`gsd/v1.3-english-test-data`)

`src/main/java/org/ctc/admin/TestDataService.java` — **948 lines**, last modified in commit `0396427` (Phase 23).

| Method | Status | Teams Used |
|--------|--------|------------|
| `seed()` | Present — calls `seedMatchdaysAndResults()` | — |
| `seedTeams()` | Present | Real: P1R, CLR, TCR, ART, AHR, MRL, GXR, DTR, VEZ, TNR |
| `seedSubTeams()` | Present | Real: CLR 1/2, TNR A/B/C, AHR 1/2, P1Rx, P1R sub |
| `seedSeasons()` | Present | Real teams; S1/S2 with correct SeasonFormat; S4 with 14 teams |
| `seedDrivers()` | Present | Real PSN IDs (France-k88, etc.) |
| `seedSeasonDrivers()` | Present | Real teams |
| `seedMatchdaysAndResults()` | **Present** | Real teams P1R/CLR/TCR/ART/MRL/GXR/DTR/VEZ + subs CLR 1/2, TNR A/B/C, AHR 1/2 |
| `seedRaceLineups()` | Present — E2E test data | T-ALF, T-BRV (must NOT be changed) |
| `seedTeamCards()` | **MISSING** | — (was removed by Phase 23 worktree conflict) |
| `@Profile("dev")` | **MISSING** | — (security fix from Phase 24, commit `55d7493`) |

`[VERIFIED: read TestDataService.java current branch; git log --oneline -- src/main/java/org/ctc/admin/TestDataService.java]`

### What Exists on `gsd/v1.0-milestone` (Phase 24 complete)

`TestDataService.java` — **605 lines**, Phase 24 fully applied.

| Method | Status | Teams Used |
|--------|--------|------------|
| `seedTeams()` | Updated | Fictive: VRX, SGM, ADR, TBR, ICL, SVT, NFR, EGP, HMS, PWR |
| `seedSubTeams()` | Updated | Fictive: VRX A/B, SGM B/S, TBR R/B/G |
| `seedSeasons()` | Updated | Fictive teams; S1/S2/S3/S4 (different S4 structure — 17 teams including VRX/SGM/TBR parents) |
| `seedDrivers()` | Updated | Fictive: VRX_Driver01–10, SGM_Driver01–10, etc. |
| `seedSeasonDrivers()` | Updated | Fictive teams |
| `seedMatchdaysAndResults()` | **REMOVED** | — (gap that Phase 27 must close) |
| `seedTeamCards()` | Present | `teamCardService.generateAllCards(activeSeason)` |
| `@Profile("dev")` | Present | Added in `55d7493` |

`[VERIFIED: git show gsd/v1.0-milestone:src/main/java/org/ctc/admin/TestDataService.java]`

### Key Discrepancy: Season Structure S4 2026

On **current branch** (Phase 23), S4 has 14 teams: 7 standalone parents (P1R, DTR, MRL, ART, VEZ, GXR, TCR) + 7 sub-teams (CLR 1/2, TNR A/B/C, AHR 1/2).

On **`gsd/v1.0-milestone`** (Phase 24), S4 has 17 teams: VRX, VRX A, VRX B, SGM, SGM B, SGM S, TBR, TBR R, TBR B, TBR G, ADR, ICL, SVT, NFR, EGP, HMS, PWR (includes parent teams VRX/SGM/TBR alongside their sub-teams).

Phase 27 must decide which structure to use for `seedMatchdaysAndResults()`. Since D-04 says "14 match teams" (from the CONTEXT.md for Phase 23), and the CONTEXT.md for Phase 27 says to adapt for "fictive teams seeded by Phase 22/24 (VRX, SGM, ADR, TBR, ICL, SVT, NFR, EGP, HMS, PWR)", the correct interpretation is: **use the fictive equivalents of the 14-team structure.** The mapping is:

| Phase 23 (Real) | Phase 27 (Fictive) | Type |
|----------------|-------------------|------|
| P1R | ADR | standalone parent |
| TCR | ICL | standalone parent |
| ART | SVT | standalone parent |
| MRL | NFR | standalone parent |
| GXR | EGP | standalone parent |
| DTR | HMS | standalone parent |
| VEZ | PWR | standalone parent |
| CLR 1 | VRX A | sub-team |
| CLR 2 | VRX B | sub-team |
| TNR A | SGM B | sub-team |
| TNR B | SGM S | sub-team |
| TNR C | TBR R | sub-team |
| AHR 1 | TBR B | sub-team |
| AHR 2 | TBR G | sub-team |

`[ASSUMED — mapping based on season-structure parity between branches; planner should confirm if VRX/SGM/TBR parents should also be in S4]`

---

## Standard Stack

### Core (all verified in codebase)

| Component | Location | Status | Notes |
|-----------|----------|--------|-------|
| `TestDataService` | `org.ctc.admin` | Present, needs update | `[VERIFIED: read source]` |
| `ScoringService` | `org.ctc.domain.service` | Already injected | `[VERIFIED: line 63]` |
| `RaceResultRepository` | Injected at line 62 | Already present | `[VERIFIED: line 62]` |
| `EntityManager` | Injected at line 64 | Already present | `[VERIFIED: line 64]` |
| `TeamCardService` | `org.ctc.admin.service` | **Missing from current branch** | `[VERIFIED: not in imports]` |

### Fictive Team and Driver Data (from `gsd/v1.0-milestone`)

**Parent teams (10):** VRX, SGM, ADR, TBR, ICL, SVT, NFR, EGP, HMS, PWR

**Sub-teams (7):**
- VRX A, VRX B (parent: VRX)
- SGM B, SGM S (parent: SGM)
- TBR R, TBR B, TBR G (parent: TBR)

**Drivers per team (10 each, pattern `{TEAM}_Driver01` through `{TEAM}_Driver10`):**
- VRX: VRX_Driver01–10
- SGM: SGM_Driver01–10
- ADR: ADR_Driver01–10
- TBR: TBR_Driver01–10
- ICL: ICL_Driver01–10
- SVT: SVT_Driver01–10
- NFR: NFR_Driver01–10
- EGP: EGP_Driver01–10
- HMS: HMS_Driver01–10
- PWR: PWR_Driver01–10

**6 drivers per sub-team race slot** (using parent team's drivers):
- VRX A → VRX_Driver01–06
- VRX B → VRX_Driver05–10 (overlap is intentional — different race lineups)
- SGM B → SGM_Driver01–06
- SGM S → SGM_Driver05–10
- TBR R → TBR_Driver01–06
- TBR B → TBR_Driver04–09
- TBR G → TBR_Driver05–10

`[VERIFIED: git show 0396427 shows the CLR/TNR/AHR driver assignments with same overlap pattern; mapping to fictive sub-teams is ASSUMED]`

---

## Architecture Patterns

### Existing Method Structure (verified, no changes to algorithm needed)

The current `seedMatchdaysAndResults()` at line 596–736 already implements the correct algorithm from commit `0396427`:

```java
// [VERIFIED: TestDataService.java lines 596-848]
private void seedMatchdaysAndResults() {
    var raceScoring = raceScoringRepository.findAll().getFirst();
    // Season lookups by year/number
    var s4 = allSeasons.stream().filter(s -> s.getYear() == 2026 && s.getNumber() == 4)...;
    var s2 = allSeasons.stream().filter(s -> s.getYear() == 2024 && s.getNumber() == 2)...;
    var s1a = allSeasons.stream().filter(s -> s.getYear() == 2023 && s.getName().equals("Group A"))...;
    var s1b = allSeasons.stream().filter(s -> s.getYear() == 2023 && s.getName().equals("Group B"))...;

    // Position rotation (unchanged)
    int[][] homePositions = {{1,3,5,7,9,11}, {2,4,6,8,10,12}, ...};
    int[][] awayPositions = {{2,4,6,8,10,12}, {1,3,5,7,9,11}, ...};
    int[] fastestLapPositions = {1, 2, 3, 4, 5};

    // teamDrivers maps → only this part changes
    seedLeagueSeason(s4, s4Teams, s4TeamDrivers, ...);
    seedSwissSeason(s2, s2Teams, s2TeamDrivers, ...);
    seedRoundRobinSeason(s1a, s1aTeams, s1aTeamDrivers, ...);
    seedRoundRobinSeason(s1b, s1bTeams, s1bTeamDrivers, ...);
}
```

### The seedRace() JPA Pattern (verified working, must not change)

```java
// [VERIFIED: TestDataService.java lines 812-848]
// Critical: flush + detach + reload before aggregateMatchScores()
raceResultRepository.saveAll(results);
scoringService.calculatePoints(results, raceScoring);
raceResultRepository.saveAll(results);
raceResultRepository.flush();
entityManager.detach(race);
var reloadedRace = raceRepository.findById(race.getId()).orElseThrow();
scoringService.aggregateMatchScores(reloadedRace);
matchRepository.save(reloadedRace.getMatch());
```

This pattern resolves Pitfall 2 (lazy results collection empty before aggregation). It was discovered empirically during Phase 23 execution. Do not simplify it. `[VERIFIED: 23-02-SUMMARY.md]`

### Season Structure for seedMatchdaysAndResults()

**S4 2026 League — 14 match teams (7 standalone parents + 7 sub-teams):**
```
Standalone parents: ADR, ICL, SVT, NFR, EGP, HMS, PWR
Sub-teams:          VRX A, VRX B, SGM B, SGM S, TBR R, TBR B, TBR G
```
Note: VRX, SGM, TBR parents must NOT be in the 14-team match-team list (only their sub-teams compete).
`[ASSUMED — maps Phase 23 D-10 to fictive equivalents]`

**S2 2024 Swiss — 10 parent teams:**
```
VRX, SGM, ADR, TBR, ICL, SVT, NFR, EGP, HMS, PWR
```

**S1 2023 Round Robin Group A — 6 teams:**
```
ADR, ICL, SVT, NFR, HMS, VRX A
```
(Maps to Phase 23: P1R, TCR, ART, MRL, GXR, CLR 1)

**S1 2023 Round Robin Group B — 6 teams:**
```
EGP, PWR, VRX B, SGM B, SGM S, TBR R
```
(Maps to Phase 23: DTR, VEZ, CLR 2, TNR A, TNR B, AHR 1)

`[ASSUMED — planner should verify mapping; alternative is DTR→HMS or VEZ→PWR swaps]`

### S4 2026 Season Registration Inconsistency

On `gsd/v1.0-milestone`, `seedSeasons()` for S4 adds 17 teams (including VRX/SGM/TBR parents alongside their sub-teams). The `getEligibleTeams()` method filters parents with active sub-teams, but `StandingsService.calculateStandings()` uses `getActiveTeams()` which does not filter. For correct standings, the Season registration must match the match-team list.

If Phase 27 starts from the current branch (real teams), it will also need to update `seedSeasons()` to exclude VRX/SGM/TBR parents from S4. If it merges from `gsd/v1.0-milestone` first, it must change `seedSeasons()` there to also exclude those parents.

`[VERIFIED: StandingsService.java calculateStandings() does not filter parents; confirmed in Phase 23 RESEARCH Pitfall 3]`

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Point calculation | Custom point arithmetic | `scoringService.calculatePoints(results, raceScoring)` | DB-stored preset; handles race/quali/FL |
| Match score aggregation | Manual homeScore/awayScore sums | `scoringService.aggregateMatchScores(reloadedRace)` | Sub-team-to-parent resolution |
| JPA result visibility | `@Transactional` assumption | `flush() + detach() + findById()` pattern | Lazy collection empty without explicit reload |

---

## Common Pitfalls

### Pitfall 1: Changing the seedRace() JPA Flush Pattern

**What goes wrong:** Removing `raceResultRepository.flush()` + `entityManager.detach(race)` + reload causes `aggregateMatchScores()` to see an empty results collection. Match scores remain null.
**Why it happens:** JPA first-level cache returns the same `race` instance with no results after `saveAll()` in the same transaction.
**How to avoid:** Keep the pattern verbatim. It was debugged and fixed during Phase 23 execution. `[VERIFIED: 23-02-SUMMARY.md]`

### Pitfall 2: Forgetting to Update seedSeasons() + seedSeasonDrivers()

**What goes wrong:** `seedMatchdaysAndResults()` uses `findParent.apply("VRX")` but `seedSeasons()` still uses `findParent.apply("P1R")`. The season has real teams in `season_teams` and fictive driver assignments will fail at RaceLineup creation.
**Why it happens:** Phase 27's CONTEXT.md says "restore seedMatchdaysAndResults for fictive teams" but the surrounding methods are equally broken — they create real teams that the new driver maps won't find.
**How to avoid:** Update all methods in a single coherent change: `seedTeams()`, `seedSubTeams()`, `seedSeasons()`, `seedDrivers()`, `seedSeasonDrivers()`, and `seedMatchdaysAndResults()` must all use the same fictive team/driver names. `[VERIFIED: current TestDataService.java lines 98-594 all use real teams]`

### Pitfall 3: S4 Has Parent + Sub-Teams in seasonTeams

**What goes wrong:** If VRX, SGM, TBR parents are added to S4's `seasonTeams`, `StandingsService.calculateStandings()` will include 0-match parent teams in standings output until filtered. The `removeIf(played == 0)` guard prevents display but the parent rows are created.
**How to avoid:** Do NOT add VRX, SGM, TBR to S4's `seasonTeams`. Only add VRX A, VRX B, SGM B, SGM S, TBR R, TBR B, TBR G. `[VERIFIED: Phase 23 RESEARCH Pitfall 3 + StandingsService.java]`

### Pitfall 4: Integration Test Short-Name Assertions

**What goes wrong:** The existing test `givenDevSeed_whenStarted_thenS4HasFourteenMatchTeams` asserts `doesNotContain("CLR", "TNR", "AHR")`. After the fictive switch, those teams no longer exist; instead the assertion should check for `doesNotContain("VRX", "SGM", "TBR")`.
**How to avoid:** Update `TestDataServiceIntegrationTest.java` to reflect the fictive team names. `[VERIFIED: read test file lines 122-127]`

### Pitfall 5: Missing @Profile("dev") on TestDataService

**What goes wrong:** Without `@Profile("dev")`, `TestDataService` is instantiated in production, causing Spring to attempt wiring `EntityManager`, `ScoringService`, etc. in contexts where they may not be present, or worse — `seed()` gets called on prod startup.
**How to avoid:** Add `@Profile("dev")` to the class. This was done in Phase 24 commit `55d7493` but not yet on the current branch. `[VERIFIED: no @Profile annotation in current TestDataService.java]`

### Pitfall 6: seedTeamCards() Missing

**What goes wrong:** Phase 24 restored `seedTeamCards(activeSeason)` which calls `teamCardService.generateAllCards(activeSeason)`. Without it, team cards are not generated in dev mode and the visual verification (Phase 26 logos) cannot be confirmed.
**How to avoid:** Add `seedTeamCards()` and `TeamCardService` injection. Reference implementation in `gsd/v1.0-milestone`. `[VERIFIED: not present in current branch; present at line 498 on gsd/v1.0-milestone]`

---

## Code Examples

### S4 League Driver Map (Fictive)

```java
// Source: adapted from commit 0396427 + fictive team mapping
var s4TeamDrivers = new java.util.LinkedHashMap<Team, Driver[]>();
// 7 standalone parents
s4TeamDrivers.put(findParent.apply("ADR"), new Driver[]{
    findDriver.apply("ADR_Driver01"), findDriver.apply("ADR_Driver02"),
    findDriver.apply("ADR_Driver03"), findDriver.apply("ADR_Driver04"),
    findDriver.apply("ADR_Driver05"), findDriver.apply("ADR_Driver06")});
// ... ICL, SVT, NFR, EGP, HMS, PWR same pattern ...
// 7 sub-teams (using parent's drivers)
s4TeamDrivers.put(findSub.apply("VRX A"), new Driver[]{
    findDriver.apply("VRX_Driver01"), findDriver.apply("VRX_Driver02"),
    findDriver.apply("VRX_Driver03"), findDriver.apply("VRX_Driver04"),
    findDriver.apply("VRX_Driver05"), findDriver.apply("VRX_Driver06")});
s4TeamDrivers.put(findSub.apply("VRX B"), new Driver[]{
    findDriver.apply("VRX_Driver05"), findDriver.apply("VRX_Driver06"),
    findDriver.apply("VRX_Driver07"), findDriver.apply("VRX_Driver08"),
    findDriver.apply("VRX_Driver09"), findDriver.apply("VRX_Driver10")});
// ... SGM B, SGM S, TBR R, TBR B, TBR G same overlap pattern ...
```

### S2 Swiss Driver Map (Fictive)

```java
// 10 parent teams, 6 drivers each
var s2TeamDrivers = new java.util.LinkedHashMap<Team, Driver[]>();
s2TeamDrivers.put(findParent.apply("VRX"), new Driver[]{
    findDriver.apply("VRX_Driver01"), ..., findDriver.apply("VRX_Driver06")});
// SGM, ADR, TBR, ICL, SVT, NFR, EGP, HMS, PWR same pattern
```

### S1 Round Robin Group A Driver Map (Fictive)

```java
// 6 teams: ADR, ICL, SVT, NFR, HMS, VRX A
var s1aTeamDrivers = new java.util.LinkedHashMap<Team, Driver[]>();
s1aTeamDrivers.put(findParent.apply("ADR"), s4TeamDrivers.get(findParent.apply("ADR")));
s1aTeamDrivers.put(findParent.apply("ICL"), s4TeamDrivers.get(findParent.apply("ICL")));
s1aTeamDrivers.put(findParent.apply("SVT"), s4TeamDrivers.get(findParent.apply("SVT")));
s1aTeamDrivers.put(findParent.apply("NFR"), s4TeamDrivers.get(findParent.apply("NFR")));
s1aTeamDrivers.put(findParent.apply("HMS"), s4TeamDrivers.get(findParent.apply("HMS")));
s1aTeamDrivers.put(findSub.apply("VRX A"), s4TeamDrivers.get(findSub.apply("VRX A")));
```

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test |
| Config file | `pom.xml` (Surefire + Failsafe) |
| Quick run command | `./mvnw test -Dtest=TestDataServiceIntegrationTest` |
| Full suite command | `./mvnw verify` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| DATA-04 | League season has 5 matchdays with results and non-zero standings | Integration | `./mvnw test -Dtest=TestDataServiceIntegrationTest` | Yes (must update for fictive teams) |
| DATA-05 | Swiss season has 5 matchdays with results and non-zero standings | Integration | `./mvnw test -Dtest=TestDataServiceIntegrationTest` | Yes (update team assertions) |
| DATA-06 | Round Robin seasons (2) have 3 matchdays each with results | Integration | `./mvnw test -Dtest=TestDataServiceIntegrationTest` | Yes (update team assertions) |
| DATA-07 | Points computed by ScoringService (non-zero, consistent with preset) | Integration | `./mvnw test -Dtest=TestDataServiceIntegrationTest` | Yes (no team-specific assertions) |

All 15 existing tests in `TestDataServiceIntegrationTest.java` currently pass with real teams. After adaptation, the tests that reference team short names (e.g., `doesNotContain("CLR", "TNR", "AHR")`) must be updated to use fictive names.

### Sampling Rate

- **Per task commit:** `./mvnw test -Dtest=TestDataServiceIntegrationTest`
- **Per wave merge:** `./mvnw verify`
- **Phase gate:** `./mvnw verify` green before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] Update assertions in `TestDataServiceIntegrationTest.java` that reference real team short names

---

## Project Constraints (from CLAUDE.md)

| Directive | Applies to Phase 27 |
|-----------|---------------------|
| TDD (Red→Green→Refactor) | Update test assertions first, then adapt `seedMatchdaysAndResults()` |
| Minimum 82% line coverage | Replacing existing lines in TestDataService does not reduce coverage; must verify after |
| `aggregateMatchScores()` after results save | Required by D-08; pattern already in place at line 841-847 |
| RaceLineup is Source of Truth | Create RaceLineup entries for every driver in every race BEFORE creating RaceResults |
| No Flyway migration changes | Dev seed data only; no schema changes |
| Isolate test data completely | T-ALF, T-BRV, Test_Alpha_* drivers in `seedRaceLineups()` must not be touched |
| OSIV remains enabled | No changes to entity graph annotations |
| `@Profile("dev")` required | Must add to prevent prod instantiation |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | S4 match-team mapping: 7 standalone parents (ADR, ICL, SVT, NFR, EGP, HMS, PWR) + 7 sub-teams (VRX A/B, SGM B/S, TBR R/B/G) | Season Structure | Wrong teams in S4 → league standings wrong; integration test for 14 teams fails |
| A2 | S1 Group A teams: ADR, ICL, SVT, NFR, HMS, VRX A | Season Structure | Mismatch with `seedSeasons()` registration → EntityNotFoundException at runtime |
| A3 | S1 Group B teams: EGP, PWR, VRX B, SGM B, SGM S, TBR R | Season Structure | Mismatch with `seedSeasons()` registration |
| A4 | Sub-team driver overlap: VRX A=Driver01-06, VRX B=Driver05-10 (same pattern as Phase 23 CLR/TNR/AHR) | Driver Mapping | Duplicate lineup entries if same driver in two teams per race — no constraint violation (different teams) |

---

## Open Questions

1. **S4 Season Registration: Should VRX/SGM/TBR parents be in `seasonTeams`?**
   - What we know: On `gsd/v1.0-milestone`, S4 adds 17 teams including VRX/SGM/TBR parents. Phase 23 D-10 says parents must NOT be match teams.
   - What's unclear: Phase 27 CONTEXT.md D-02 says "fictive teams seeded by Phase 22/24" without specifying whether to follow the Phase 23 14-team rule or the Phase 24 17-team rule.
   - Recommendation: Follow Phase 23 D-10 (14 teams, no VRX/SGM/TBR parents in S4) for correct standings display. Planner should confirm.

2. **Should `seedSeasons()` be updated to use SeasonFormat for S1/S2?**
   - What we know: On current branch, S1 has `SeasonFormat.ROUND_ROBIN` and S2 has `SeasonFormat.SWISS`. On `gsd/v1.0-milestone`, these formats are absent (not set in the loop).
   - What's unclear: Which branch's season-format assignment is correct.
   - Recommendation: Keep Phase 23's explicit format assignments (`s1a.setFormat(ROUND_ROBIN)`, etc.) — they are required for standings pages to display the correct format-specific UI.

---

## Environment Availability

Step 2.6: SKIPPED — Phase 27 is purely Java code changes to `TestDataService`. No external dependencies beyond the existing dev Spring Boot stack (H2, Maven).

---

## Sources

### Primary (HIGH confidence)
- `src/main/java/org/ctc/admin/TestDataService.java` (current branch) — full structure read; method list, line numbers, all real-team references verified
- `git show 0396427:src/main/java/org/ctc/admin/TestDataService.java` — original `seedMatchdaysAndResults()` with complete driver maps
- `git show gsd/v1.0-milestone:src/main/java/org/ctc/admin/TestDataService.java` — Phase 24 fictive team/driver reference implementation
- `src/test/java/org/ctc/admin/TestDataServiceIntegrationTest.java` — current 15 tests, all passing
- `.planning/phases/23-dev-seasons-with-results/23-02-SUMMARY.md` — JPA flush/detach pattern origin documented
- `.planning/phases/27-restore-matchday-result-pipeline/27-CONTEXT.md` — locked decisions

### Secondary (MEDIUM confidence)
- `.planning/phases/23-dev-seasons-with-results/23-RESEARCH.md` — original pitfall documentation
- `git show gsd/v1.0-milestone:src/test/java/org/ctc/admin/TestDataServiceIntegrationTest.java` — Phase 26 test replacement (logo-only tests)

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all entities, repositories verified from source
- Architecture: HIGH — JPA pattern verified working from Phase 23 execution
- Team/driver mapping: MEDIUM — fictive sub-team driver overlap is ASSUMED based on Phase 23 pattern analogy
- Season structure: MEDIUM — S4 14-team vs 17-team and group assignments are ASSUMED

**Research date:** 2026-04-10
**Valid until:** Until Phase 27 completes — TestDataService changes are the only dependency
