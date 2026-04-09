# Phase 23: Dev Seasons with Results - Research

**Researched:** 2026-04-09
**Domain:** TestDataService extension — seed matchdays, matches, races, lineups, results, and scoring aggregation
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Reuse existing seasons: S1 2023 Group A + Group B → `ROUND_ROBIN`, S2 2024 → `SWISS`, S4 2026 → `LEAGUE` (active)
- **D-02:** S3a/S3b (2025) remain as older seasons without matchdays/results — no changes needed
- **D-03:** Round Robin uses 2 separate Season entities (Group A + Group B) as the 2 groups — matches existing data model
- **D-04:** League (S4 2026): 5 matchdays, 1 race per match
- **D-05:** Swiss (S2 2024): 5 matchdays, 2 races per match
- **D-06:** Round Robin (S1 2023): 3 matchdays per group, 2 races per match
- **D-07:** 6 drivers per team per race (12 total per race) — uses full position spectrum 1-12
- **D-08:** Round Robin (S1 2023): Adjust season-teams to ~5-6 teams per group, mix of parent-teams and sub-teams. Current all-10-parents-per-group must be restructured.
- **D-09:** Swiss (S2 2024): Keep existing 10 parent-teams only, no sub-teams
- **D-10:** League (S4 2026): 14 teams — 7 standalone parents (ADR, ICL, SVT, NFR, EGP, HMS, PWR) + 7 sub-teams (VRX A, VRX B, SGM B, SGM S, TBR R, TBR B, TBR G). The 3 parent-teams with subs (VRX, SGM, TBR) do NOT participate as match teams.
- **D-11:** Deterministic (hard-coded) positions per race — 100% reproducible, predictable standings
- **D-12:** Simple rotation of positions between matchdays — not monotonous but predictable. Fastest lap rotates between different drivers.
- **D-13:** Use `ScoringService.calculatePoints()` to compute points from positions (not hard-coded point values). Call `aggregateMatchScores()` after saving results. This validates DATA-07.

### Claude's Discretion

- Specific match pairings per matchday (who plays whom) — as long as all season-teams play
- Exact position assignments per race — as long as rotation creates varied standings
- Which 6 of 10 drivers per team play each race — can vary between matchdays
- RaceLineup creation for all races (must exist before results)
- SeasonDriver assignments for S1 and S2 (currently only S4 has them)
- RaceSettings values for seeded races

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| DATA-04 | Dev profile creates League format season with matchdays, races, and results | S4 2026 gets `SeasonFormat.LEAGUE`, 5 matchdays seeded with Match + Race + RaceLineup + RaceResult; ScoringService called after each race |
| DATA-05 | Dev profile creates Swiss format season with matchdays, races, and results | S2 2024 gets `SeasonFormat.SWISS`, 5 matchdays each with 2 races, same pipeline |
| DATA-06 | Dev profile creates Round Robin format season (2 groups) with matchdays, races, and results | S1 2023 Group A + Group B each get `SeasonFormat.ROUND_ROBIN`, season-teams restructured, 3 matchdays per group |
| DATA-07 | Race results use actual existing scoring system for point calculation | `ScoringService` injected into `TestDataService`; `calculatePoints()` + `aggregateMatchScores()` called for every race |
</phase_requirements>

---

## Summary

Phase 23 extends `TestDataService` with matchday/result seeding for three seasons that already exist in the dev seed. The core plumbing — entities, constructors, repositories, and scoring service — is all verified to be in place. The work is entirely additive: no schema changes, no new entities, no new services. The only structural change is injecting `ScoringService` into `TestDataService`, which currently does not depend on it.

The data creation sequence for each race is fixed by the domain model: `Matchday → Match → Race + RaceSettings → RaceLineup (x12) → RaceResult (x12) → ScoringService.calculatePoints() → ScoringService.aggregateMatchScores()`. This sequence must not be reordered because `aggregateMatchScores()` reads `race.getResults()` (lazy collection) and `raceLineupRepository` (used by `isDriverInTeam()`), so both results and lineups must be persisted before aggregation.

Two seasons (S1 2023 Round Robin groups and S2 2024 Swiss) also require `SeasonDriver` records and season-team restructuring, which are prerequisites for standings to display correctly. S4 2026 already has SeasonDrivers and the correct team structure needs adjustment per D-10 (remove VRX/SGM/TBR parent match participants).

**Primary recommendation:** Implement as a single new `seedMatchdaysAndResults()` method called from `TestDataService.seed()` after `seedRaceLineups()`. Keep the existing test-data methods intact. Inject `ScoringService` via constructor injection (`final` field + `@RequiredArgsConstructor`).

---

## Standard Stack

### Core (all verified in codebase)

| Component | Location | Purpose | Notes |
|-----------|----------|---------|-------|
| `TestDataService` | `org.ctc.admin` | Dev-profile seed service, entry point for all changes | `[VERIFIED: read source]` |
| `ScoringService` | `org.ctc.domain.service` | `calculatePoints()` + `aggregateMatchScores()` | `[VERIFIED: read source]` |
| `RaceResult` | `org.ctc.domain.model` | Five-arg constructor: `(race, driver, position, qualiPosition, fastestLap)` | `[VERIFIED: read source]` |
| `RaceLineup` | `org.ctc.domain.model` | Three-arg constructor: `(race, driver, team)` | `[VERIFIED: read source]` |
| `Match` | `org.ctc.domain.model` | Three-arg constructor: `(matchday, homeTeam, awayTeam)` | `[VERIFIED: read source]` |
| `Matchday` | `org.ctc.domain.model` | Three-arg constructor: `(season, label, sortIndex)` | `[VERIFIED: read source]` |
| `SeasonDriver` | `org.ctc.domain.model` | Three-arg constructor: `(season, driver, team)` | `[VERIFIED: read source]` |
| `SeasonFormat` | `org.ctc.domain.model` | `LEAGUE`, `SWISS`, `ROUND_ROBIN` | `[VERIFIED: read source]` |

### Repositories available in TestDataService (already injected)

| Repository | Used for |
|------------|----------|
| `SeasonRepository` | Load/save seasons, restructure teams |
| `MatchdayRepository` | Save new matchdays |
| `MatchRepository` | Save new matches |
| `RaceRepository` | Save new races |
| `RaceLineupRepository` | Save race lineups |
| `SeasonDriverRepository` | Save S1/S2 season-driver assignments |
| `TeamRepository` | Look up teams by shortName |
| `DriverRepository` | Look up drivers by PSN ID |

**Missing dependency:** `RaceResultRepository` — not currently injected into `TestDataService`. Must be added. `[VERIFIED: read source]`

**Missing service injection:** `ScoringService` — not currently injected into `TestDataService`. Must be added. `[VERIFIED: read source]`

---

## Architecture Patterns

### Established Seeding Pattern (from existing `seedRaceLineups()`)

```java
// Source: TestDataService.seedRaceLineups() [VERIFIED]
// 1. Create and save Matchday
var md = matchdayRepository.save(new Matchday(season, "Matchday 1", 1));

// 2. Create and save Match
var match = new Match(md, homeTeam, awayTeam);
matchRepository.save(match);

// 3. Create and save Race (link to both matchday AND match)
var race = new Race();
race.setMatchday(md);
race.setMatch(match);
race.setSettings(createTestSettings(race));
raceRepository.save(race);

// 4. Save RaceLineups (must exist before RaceResults)
raceLineupRepository.save(new RaceLineup(race, driver, team));
```

### New Pattern Extension: RaceResults + Scoring

```java
// After lineups are saved, save results and score them
// Source: ScoringService.calculatePoints() [VERIFIED]
var raceScoring = season.getRaceScoring(); // already on season entity

var result = raceResultRepository.save(
    new RaceResult(race, driver, position, qualiPosition, fastestLap));
scoringService.calculatePoints(result, raceScoring);
raceResultRepository.save(result); // save after points calculated

// After all results for the race are saved:
scoringService.aggregateMatchScores(race); // updates Match.homeScore / awayScore
```

### Season-Team Restructuring Pattern

The season entities for S1 and S4 currently have incorrect team membership (D-08 and D-10). These must be fixed in `seedSeasons()` or a new step before matchdays are created:

```java
// Source: Season.removeTeam(), Season.addTeam() [VERIFIED]
// Remove parent teams from S4 that have sub-teams competing
var s4 = ...;
s4.removeTeam(findParent.apply("VRX")); // VRX competes via VRX A + VRX B
s4.removeTeam(findParent.apply("SGM")); // SGM competes via SGM B + SGM S
s4.removeTeam(findParent.apply("TBR")); // TBR competes via TBR R + TBR B + TBR G
seasonRepository.save(s4);
```

**Note:** `Season.getEligibleTeams()` already filters out parent teams with sub-teams in the same season. However `StandingsService.calculateStandings()` uses `season.getActiveTeams()` (not `getEligibleTeams()`). To ensure standings show only match participants, parent teams that have sub-teams competing must be removed from the season's `seasonTeams` list, not just filtered. `[VERIFIED: read StandingsService and Season source]`

### SeasonFormat Assignment

```java
// Source: Season.java, SeasonFormat.java [VERIFIED]
season.setFormat(SeasonFormat.ROUND_ROBIN);
season.setFormat(SeasonFormat.SWISS);
season.setFormat(SeasonFormat.LEAGUE);
seasonRepository.save(season);
```

The format is already defined on the Season entity with default `LEAGUE`. It must be set explicitly for S1 and S2. `[VERIFIED: Season.java line 44]`

### SeasonDriver Pattern for S1 and S2

S1 and S2 currently have no SeasonDriver records (only S4 does). The `StandingsService` uses `RaceLineup` as source of truth, but `ScoringService.isDriverInTeam()` has a fallback to `SeasonDriver`. For standings to work correctly, RaceLineup is sufficient. However SeasonDriver records are still useful for driver-lookup contexts. The CONTEXT.md marks this as "Claude's discretion."

Per the ARCHITECTURE.md: "RaceLineup is Source of Truth — always prioritize RaceLineup; use SeasonDriver only as fallback for seasons without races."

Since we're seeding full RaceLineups for every race, SeasonDriver records for S1 and S2 are not strictly required for the standings page to work. The planner should decide whether to seed them or not.

### Position Rotation Design

With 12 drivers per race (6 per team), positions 1-12 are filled. The scoring preset is `"20,17,14,12,10,8,7,6,5,4,3,2"` for race, `"3,2,1"` for quali, 2 points for fastest lap. `[VERIFIED: TestDataService.seedScorings()]`

Example home-team sweep scenario (positions 1,3,5,7,9,11):
- Race points: 20+14+10+6+4+2 = 56
- Away-team (positions 2,4,6,8,10,12): 17+12+8+5+3+2 = 47 (note: 12th place = 2 pts)

Wait: the scoring array `"20,17,14,12,10,8,7,6,5,4,3,2"` has 12 values — position 12 = 2 pts. `[VERIFIED: ScoringService and RaceScoring]`

Rotating which team wins across matchdays ensures non-monotonous standings. Fastest lap bonus (2 pts) rotates among different drivers per decision D-12.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Point calculation | Custom point arithmetic | `ScoringService.calculatePoints(result, raceScoring)` | Handles race/quali/FL correctly, uses DB-stored scoring preset |
| Match score aggregation | Manual homeScore/awayScore sums | `ScoringService.aggregateMatchScores(race)` | Handles multi-leg matches, uses RaceLineup as source of truth for team assignment |
| Team-driver assignment lookup | Custom roster check | `ScoringService.isDriverInTeam()` — used internally by aggregateMatchScores | Correct sub-team-to-parent resolution logic |
| Driver lookup | Manual stream filter | Existing `findDriver` lambda pattern in TestDataService | PSN ID based, consistent with established codebase pattern |

---

## Common Pitfalls

### Pitfall 1: Saving Race Before RaceSettings
**What goes wrong:** `RaceSettings` has `@OneToOne(mappedBy = "race")` — the settings entity owns the FK to race. If race is not yet persisted, settings cannot be saved.
**Why it happens:** The `createTestSettings(race)` helper sets `race` on settings but the race must be persisted first.
**How to avoid:** `raceRepository.save(race)` before `race.setSettings(createTestSettings(race))`. The existing pattern in `seedRaceLineups()` already does this correctly. `[VERIFIED: existing code]`

### Pitfall 2: aggregateMatchScores Before Results Are Persisted
**What goes wrong:** `aggregateMatchScores(race)` calls `race.getResults()` (lazy collection). If results haven't been flushed to DB, the collection may be empty and match scores stay at null.
**Why it happens:** In the same `@Transactional` method, `save()` does not automatically flush unless Hibernate flushes before the query.
**How to avoid:** Call `aggregateMatchScores(race)` only after all `raceResultRepository.save(result)` calls for that race are complete. Since `TestDataService.seed()` is one big `@Transactional` method, consider using `raceResultRepository.saveAll(results)` and then passing the saved results explicitly, or call flush before aggregation. Alternatively, reload the race via `raceRepository.findById()` before aggregating.

**Safest approach:** Build all results as a list, call `raceResultRepository.saveAll(results)`, call `calculatePoints` on each, save again, then call `aggregateMatchScores`. `[ASSUMED — based on JPA transactional flush behavior]`

### Pitfall 3: StandingsService Filters Out Teams with 0 Matches
**What goes wrong:** `StandingsService.calculateStandings()` calls `standings.removeIf(s -> s.getPlayed() == 0)`. Teams registered to a season but with no matches appear in season teams list but not in standings.
**Why it happens:** If VRX parent is left in S4's seasonTeams but has no matches (because only sub-teams play), it shows in team list but not standings.
**How to avoid:** Remove parent teams (VRX, SGM, TBR) from S4's seasonTeams per D-10. `[VERIFIED: StandingsService.calculateStandings() line 49]`

### Pitfall 4: Round Robin Season-Team Restructuring on Second Seed Run
**What goes wrong:** The idempotency guard `if (seasonRepository.count() > 0) return;` prevents re-seeding. But if team membership is modified in `seedSeasons()` and a database already exists from a previous run (without this change), the team lists won't be updated.
**Why it happens:** The H2 database for tests is in-memory (fresh per test context start), so this only matters for local MariaDB persistence.
**How to avoid:** For dev profile with H2, not an issue. The dev data is always re-created from scratch. `[VERIFIED: TestDataService.seed() line 61, application-dev.yml would clarify H2 usage]`

### Pitfall 5: RaceResult Position Constraints
**What goes wrong:** `RaceResult` has `@Min(1) @Max(12)` on both `position` and `qualiPosition`. Seeding > 12 drivers or assigning positions outside 1-12 will fail validation.
**Why it happens:** Bean validation is applied on entity save.
**How to avoid:** With exactly 6 drivers per team and 2 teams per race = 12 drivers, positions 1-12 are used exactly once. Never assign the same position to two drivers in the same race. `[VERIFIED: RaceResult.java lines 37-42]`

### Pitfall 6: Missing RaceResultRepository in TestDataService
**What goes wrong:** `RaceResult` entities cannot be persisted without `RaceResultRepository`. It is currently absent from `TestDataService` field list.
**Why it happens:** Phase 22 didn't need it.
**How to avoid:** Add `private final RaceResultRepository raceResultRepository;` field. Spring's `@RequiredArgsConstructor` + Lombok will wire it automatically. `[VERIFIED: TestDataService.java — no RaceResultRepository field exists]`

### Pitfall 7: S4 Season-Team Structure Already Has Parents + Sub-Teams
**What goes wrong:** Current `seedSeasons()` for S4 adds VRX, VRX A, VRX B, SGM, SGM B, SGM S, TBR, TBR R, TBR B, TBR G — plus 7 standalone parents. Decision D-10 requires only 14 match teams: 7 sub-teams + 7 standalone parents. VRX, SGM, TBR parent teams must be removed.
**Why it happens:** The existing seeding was done before D-10 was decided.
**How to avoid:** In `seedSeasons()`, do not add VRX, SGM, TBR parent teams to S4. Remove the `findParent.apply("VRX")`, `findParent.apply("SGM")`, `findParent.apply("TBR")` calls for S4. `[VERIFIED: TestDataService.java lines 202-218]`

---

## Code Examples

### Complete Race Seeding Sequence (Verified Pattern)

```java
// Source: extended from TestDataService.seedRaceLineups() [VERIFIED]
private void seedMatchdaysAndResults() {
    var scorings = new ScoringDefaults(
        raceScoringRepository.findAll().getFirst(),
        matchScoringRepository.findAll().getFirst());

    // Find season by year + number (existing helper pattern)
    var allSeasons = seasonRepository.findAll();
    var allTeams = teamRepository.findAll();
    var allDrivers = driverRepository.findAll();

    // Helper lambdas (same pattern as existing seedSeasonDrivers)
    Function<String, Team> findParent = shortName -> allTeams.stream()
        .filter(t -> t.getShortName().equals(shortName) && t.getParentTeam() == null)
        .findFirst().orElseThrow(...);
    Function<String, Team> findSub = shortName -> allTeams.stream()
        .filter(t -> t.getShortName().equals(shortName) && t.getParentTeam() != null)
        .findFirst().orElseThrow(...);
    Function<String, Driver> findDriver = psnId -> allDrivers.stream()
        .filter(d -> d.getPsnId().equals(psnId))
        .findFirst().orElseThrow(...);

    // --- League Season (S4 2026) ---
    var s4 = allSeasons.stream()
        .filter(s -> s.getYear() == 2026 && s.getNumber() == 4)
        .findFirst().orElseThrow(...);

    for (int mdIndex = 1; mdIndex <= 5; mdIndex++) {
        var md = matchdayRepository.save(new Matchday(s4, "Matchday " + mdIndex, mdIndex));
        // Create 7 matches (all 14 eligible teams paired)
        seedLeagueMatchday(md, s4, mdIndex, findSub, findParent, findDriver);
    }
}

private void seedLeagueRace(Matchday md, Match match, Season season,
        List<Driver[]> homeDrivers, List<Driver[]> awayDrivers,
        int[][] homePositions, int[][] awayPositions, int fastestLapDriverIndex) {
    var race = new Race();
    race.setMatchday(md);
    race.setMatch(match);
    raceRepository.save(race);
    race.setSettings(createTestSettings(race));
    raceRepository.save(race);

    // Save lineups (Source of Truth for scoring)
    // homeDrivers: 6 drivers for home team
    for (var driver : homeDrivers) {
        raceLineupRepository.save(new RaceLineup(race, driver, match.getHomeTeam()));
    }
    for (var driver : awayDrivers) {
        raceLineupRepository.save(new RaceLineup(race, driver, match.getAwayTeam()));
    }

    // Save results and calculate points
    var raceScoring = season.getRaceScoring();
    var allResults = new ArrayList<RaceResult>();
    // ... create 12 RaceResult objects, call calculatePoints on each
    // ... then call aggregateMatchScores(race)
}
```

### ScoringService Invocation

```java
// Source: ScoringService.calculatePoints() [VERIFIED]
var result = new RaceResult(race, driver, position, qualiPosition, isFastestLap);
raceResultRepository.save(result);
scoringService.calculatePoints(result, season.getRaceScoring());
raceResultRepository.save(result); // update with calculated points

// After all results for this race:
scoringService.aggregateMatchScores(race); // [VERIFIED: updates Match.homeScore/awayScore]
```

---

## Season Data Design

### S4 2026 League — Team Assignment (D-10)

14 eligible teams for matches:
- 7 standalone parents: ADR, ICL, SVT, NFR, EGP, HMS, PWR
- 7 sub-teams: VRX A, VRX B, SGM B, SGM S, TBR R, TBR B, TBR G

**Required change:** Remove VRX, SGM, TBR from S4's seasonTeams in `seedSeasons()`.

5 matchdays × 7 matches per matchday = 35 total matches, 35 races, 420 RaceResult rows.

### S2 2024 Swiss — Team Assignment (D-09)

10 parent teams: VRX, SGM, ADR, TBR, ICL, SVT, NFR, EGP, HMS, PWR.
No sub-teams. Current seed already has this structure (all 10 parents). `[VERIFIED: TestDataService.java line 169]`

5 matchdays × 5 matches per matchday × 2 races per match = 50 races, 600 RaceResult rows.

**Required addition:** Set `SeasonFormat.SWISS` on S2. Currently defaults to `LEAGUE`. `[VERIFIED: Season.java default format = LEAGUE]`

### S1 2023 Round Robin — Team Restructuring (D-08)

Current state: all 10 parent teams in both Group A and Group B. `[VERIFIED: TestDataService.java lines 166-172]`

Required: ~5-6 teams per group with sub-team mix per D-08.

Example distribution (planner's discretion):
- Group A: ADR, ICL, SVT, NFR, HMS + VRX A, SGM B (7 teams → 3 matchdays × 3 matches × 2 races = 18 races)
- Group B: EGP, PWR, TBR + VRX B, SGM S, TBR R, TBR B (7 teams → 3 matchdays × 3 matches × 2 races = 18 races)

**Required changes to `seedSeasons()`:**
1. Clear existing season-teams for S1 Group A and Group B
2. Add the new team set
3. Set `SeasonFormat.ROUND_ROBIN`
4. Add SeasonDriver records for participating drivers

**Required addition:** Set `SeasonFormat.ROUND_ROBIN` on both Group A and Group B seasons.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test (integration), JUnit 5 + Mockito (unit) |
| Config file | `pom.xml` (Surefire + Failsafe plugins) |
| Quick run command | `./mvnw test -Dtest=TestDataServiceIntegrationTest` |
| Full suite command | `./mvnw verify` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| DATA-04 | League season has 5 matchdays with results and non-zero standings | Integration | `./mvnw test -Dtest=TestDataServiceIntegrationTest` | ❌ Wave 0 |
| DATA-05 | Swiss season has 5 matchdays with results and non-zero standings | Integration | `./mvnw test -Dtest=TestDataServiceIntegrationTest` | ❌ Wave 0 |
| DATA-06 | Round Robin seasons (2) have 3 matchdays each with results | Integration | `./mvnw test -Dtest=TestDataServiceIntegrationTest` | ❌ Wave 0 |
| DATA-07 | Points computed by ScoringService (non-zero, consistent with scoring preset) | Integration | `./mvnw test -Dtest=TestDataServiceIntegrationTest` | ❌ Wave 0 |

All four requirements should be verified in `TestDataServiceIntegrationTest` alongside existing tests. New test methods follow the established `givenDevSeed_whenStarted_then*` naming pattern.

### Sampling Rate

- **Per task commit:** `./mvnw test -Dtest=TestDataServiceIntegrationTest`
- **Per wave merge:** `./mvnw verify`
- **Phase gate:** Full suite green (`./mvnw verify`) before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] New test methods in `src/test/java/org/ctc/admin/TestDataServiceIntegrationTest.java` for DATA-04 through DATA-07

The existing test file exists and will be extended. No new file creation needed.

---

## Project Constraints (from CLAUDE.md)

| Directive | Applies to Phase 23 |
|-----------|---------------------|
| TDD: Write tests first (Red → Green → Refactor) | New test methods must be written before implementation |
| Minimum 82% line coverage | Adding ~200+ lines to TestDataService; ensure coverage ratio maintained |
| No business logic in controllers | N/A — this phase is service/data-only |
| DTOs not entities in POST | N/A — no new controllers |
| `RaceLineup` is Source of Truth | Must create RaceLineup entries for every driver in every race BEFORE creating RaceResults |
| Isolate test data completely | Phase 23 seeds dev data, not E2E test data — dev seasons use fictive team/driver names with no T-ALF/T-BRV prefix |
| No Flyway migration changes | Test data is dev-profile only, seeded at runtime — confirmed correct approach |
| No inline styles | N/A — no new templates |
| OSIV remains enabled | N/A — no entity graph changes needed |
| `aggregateMatchScores()` after results save | Explicitly required by D-13 and confirmed in CLAUDE.md feedback |
| Do not change V1 migration | N/A — no schema changes in this phase |

---

## Open Questions

1. **S1 Round Robin exact team distribution**
   - What we know: D-08 says "~5-6 teams per group, mix of parent-teams and sub-teams"
   - What's unclear: Exact assignment is Claude's discretion
   - Recommendation: Use 6 teams per group for even matchday pairings (5 matches per 3-round robin would be uneven; 6 teams = 3 matches per matchday per group at 3 matchdays)

2. **SeasonDriver records for S1 and S2**
   - What we know: They're absent; RaceLineup is source of truth so they're not required for standings
   - What's unclear: Whether they're needed for any other UI feature
   - Recommendation: Add SeasonDriver records for S1 and S2 since the CONTEXT.md marks it as Claude's discretion and they provide defensive fallback coverage. The existing `seedSeasonDrivers()` pattern is easy to replicate.

3. **Whether `raceRepository.save(race)` needs explicit call after `setSettings()`**
   - What we know: `RaceSettings` has `@OneToOne(mappedBy = "race", cascade = ALL)` — so saving race after `setSettings` would cascade
   - What's unclear: The existing pattern in `seedRaceLineups()` saves race, then calls `createTestSettings(race)` without a second `raceRepository.save(race)` (settings are saved separately? No — settings is orphaned without cascade save)
   - Recommendation: Follow the existing pattern exactly as used in `seedRaceLineups()`. The `createTestSettings()` method returns a `RaceSettings` with race set, but it's not saved. Actually looking at the code: `race.setSettings(createTestSettings(race))` is called before `raceRepository.save(race)` for race3 (line 579), but `createTestSettings` is called without saving settings separately. Since `RaceSettings` maps `cascade = CascadeType.ALL` from Race, saving race after setting the settings will cascade. The plan should call `race.setSettings(createTestSettings(race))` then `raceRepository.save(race)` once.

---

## Environment Availability

Step 2.6: SKIPPED — Phase 23 is purely Java code changes to `TestDataService`. No external dependencies beyond the existing dev Spring Boot stack (H2, Maven).

---

## Sources

### Primary (HIGH confidence)
- `src/main/java/org/ctc/admin/TestDataService.java` — full source read; existing seed patterns, constructor usage, idempotency guard, missing RaceResultRepository
- `src/main/java/org/ctc/domain/service/ScoringService.java` — full source read; `calculatePoints()` and `aggregateMatchScores()` signatures and behavior
- `src/main/java/org/ctc/domain/model/RaceResult.java` — constructor, `@Min/@Max` constraints
- `src/main/java/org/ctc/domain/model/Season.java` — format field default, `addTeam()`, `removeTeam()`, `getEligibleTeams()`
- `src/main/java/org/ctc/domain/service/StandingsService.java` — `calculateStandings()` logic, `removeIf(played == 0)`
- `src/test/java/org/ctc/admin/TestDataServiceIntegrationTest.java` — test naming convention, existing assertions
- `.planning/phases/23-dev-seasons-with-results/23-CONTEXT.md` — all locked decisions D-01 through D-13

### Secondary (MEDIUM confidence)
- `docs/superpowers/specs/2026-03-29-scoring-legs-design.md` — scoring system architecture, relationship between Match, Race, and aggregation
- `.planning/codebase/ARCHITECTURE.md` — scoring calculation flow, RaceLineup as source of truth

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | JPA flush behavior: results must be persisted before `aggregateMatchScores()` reads them in same transaction | Common Pitfalls #2 | Race match scores remain null → standings show 0 for all teams |
| A2 | SeasonDriver records are not required for standings with full RaceLineup coverage | Open Questions #2 | Certain UI paths using SeasonDriver fallback would fail to show driver-team association |

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all entities, repositories, and services read directly from source
- Architecture: HIGH — seeding patterns verified from existing `seedRaceLineups()`, scoring verified from `ScoringService`
- Pitfalls: HIGH (most) / MEDIUM (A1 — JPA flush assumption)

**Research date:** 2026-04-09
**Valid until:** Stable — no external dependencies. Valid until Season/ScoringService entities change.
