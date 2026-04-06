# Phase 9: Alltime Standings - Research

**Researched:** 2026-04-05
**Domain:** Cross-season team standings aggregation (Spring Boot / JPA / Thymeleaf)
**Confidence:** HIGH

## Summary

Phase 9 implements alltime team standings by aggregating per-season standings across all seasons. The existing codebase already has all building blocks in place: `StandingsService.calculateStandings(seasonId)` calculates per-season team standings, `DriverRankingService.calculateAlltimeRanking()` demonstrates the proven cross-season aggregation pattern with `getParentOrSelf()` team resolution, and the Thymeleaf template already has the `isAlltime` flag and conditional rendering for alltime headers.

The implementation scope is narrow: one new method in `StandingsService`, a one-line change in `StandingsController`, and no template modifications. The existing `TeamStanding` inner class can be reused directly since alltime standings use the same columns (MP, W, D, L, PR, Pts).

**Primary recommendation:** Create `StandingsService.calculateAlltimeStandings()` that iterates all seasons with matches, calls `calculateStandings(seasonId)` per season, resolves teams to parents via `getParentOrSelf()`, and merges the per-season `TeamStanding` accumulators into alltime totals. Wire it into the existing TODO placeholder in `StandingsController`.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Iterate all seasons with match results, calculate per-season standings using each season's own MatchScoring rules, then sum the resulting W/D/L/Points/PointsFor/PointsAgainst across seasons per team. This preserves the existing `calculateStandings(seasonId)` logic as the building block.
- **D-02:** No Buchholz in alltime standings -- Buchholz is Swiss-format-specific and meaningless across seasons.
- **D-03:** Resolve teams to their parent via `Team.getParentOrSelf()` for cross-season aggregation. Sub-team results count toward the parent team. This follows the established pattern from `DriverRankingService.calculateAlltimeRanking()`.
- **D-04:** Within each season, use `Season.buildSuccessionMap()` as the existing `calculateStandings()` already does. The parent-resolution happens after per-season calculation when merging into alltime totals.
- **D-05:** Alltime standings table uses the standard league table columns: # (rank), Team, MP, W, D, L, PR (Points Ratio), Pts. Same as the non-Swiss per-season table.
- **D-06:** Reuse the existing `standingsTable` template structure. The alltime view should render using the same table as league standings (the non-Swiss table already shows when `selectedSeason == null`).
- **D-07:** Include all seasons that have match results (completed matches with scores). No filtering by season status -- if matches have been played, the season counts. This matches the alltime driver ranking behavior.

### Claude's Discretion
- Whether to create a new method `calculateAlltimeStandings()` or extend the existing method with a flag
- Whether to reuse the existing `TeamStanding` class or create a new alltime-specific class
- Internal implementation: collect all seasons via `seasonRepository.findAll()` and filter to those with matches, or query matches directly across all seasons
- Test data setup: how to create multi-season test fixtures for unit tests

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| FEAT-01 | Alltime Standings zeigt cross-season Team-Aggregation (StandingsService.calculateAlltimeStandings()) | All research findings support this: existing per-season calculation, DriverRanking alltime pattern, controller TODO placeholder, template conditional rendering |
</phase_requirements>

## Architecture Patterns

### Implementation Strategy

The alltime standings follows a **two-phase aggregation** pattern:

1. **Per-season calculation** -- Reuse existing `calculateStandings(seasonId)` which already handles MatchScoring rules and succession maps correctly
2. **Cross-season merge** -- Resolve each team to parent via `getParentOrSelf()`, then sum W/D/L/Points/PointsFor/PointsAgainst

This mirrors the established `DriverRankingService.calculateAlltimeRanking()` pattern (lines 57-91).

### Recommended Approach: New Method

Create a dedicated `calculateAlltimeStandings()` method rather than adding a flag to the existing method. Reasons:
- The alltime method needs `seasonRepository.findAll()` + filtering, which is fundamentally different from single-season lookup
- No Buchholz (D-02), so the return type is the same `List<TeamStanding>` but the calculation pipeline differs
- Follows the `DriverRankingService` precedent: separate `calculateRanking(seasonId)` vs `calculateAlltimeRanking()` methods

### Reuse TeamStanding

Reuse the existing `TeamStanding` inner class. The alltime standings use identical columns (D-05). The `TeamStanding` accumulator pattern (addWin, addDraw, addLoss, addMatchPoints, addPointsFor, addPointsAgainst) works for merging -- just create a new `TeamStanding` per parent team and accumulate from per-season results.

### Algorithm Pseudocode

```java
public List<TeamStanding> calculateAlltimeStandings() {
    List<Season> allSeasons = seasonRepository.findAll();
    Map<UUID, TeamStanding> alltimeMap = new HashMap<>();

    for (Season season : allSeasons) {
        List<TeamStanding> seasonStandings = calculateStandings(season.getId());
        if (seasonStandings.isEmpty()) continue; // D-07: skip seasons without match results

        for (TeamStanding standing : seasonStandings) {
            Team parentTeam = standing.getTeam().getParentOrSelf(); // D-03
            TeamStanding alltime = alltimeMap.computeIfAbsent(
                parentTeam.getId(), id -> new TeamStanding(parentTeam));
            // merge standing into alltime accumulator
            mergeStanding(alltime, standing);
        }
    }

    List<TeamStanding> result = new ArrayList<>(alltimeMap.values());
    result.sort(/* same comparator as calculateStandings */);
    return result;
}
```

### Merge Helper

`TeamStanding` currently has individual `addWin()`, `addMatchPoints(int)` etc. methods. For merging, a bulk `merge(TeamStanding other)` method would be cleaner but not mandatory -- the planner can decide whether to:
- (a) Add a `merge(TeamStanding other)` method to `TeamStanding`
- (b) Use the existing granular add methods in a loop (wins times, draws times, etc.)

Option (a) is cleaner. A simple `merge` method:
```java
public void merge(TeamStanding other) {
    this.wins += other.wins;
    this.draws += other.draws;
    this.losses += other.losses;
    this.points += other.points;
    this.pointsFor += other.pointsFor;
    this.pointsAgainst += other.pointsAgainst;
}
```

This requires access to the private fields, which is possible since `merge` would be within the `TeamStanding` class itself.

### Controller Change

Single-line change in `StandingsController.java` line 33:
```java
// Before:
model.addAttribute("standings", java.util.List.of());
// After:
model.addAttribute("standings", standingsService.calculateAlltimeStandings());
```

### Template Change

None expected. The template already handles alltime:
- Line 24: `<h2 th:if="${isAlltime}">Team Standings -- Alltime</h2>`
- Line 28: `th:if="${!standings.isEmpty() && (selectedSeason == null || selectedSeason.format.name() != 'SWISS')}"` -- when `selectedSeason == null` (alltime case), the non-Swiss league table renders, which has the correct columns (D-05)

### Anti-Patterns to Avoid
- **Do not query matches directly across all seasons** -- Reuse `calculateStandings(seasonId)` per D-01 to preserve season-specific MatchScoring rules
- **Do not include Buchholz** -- Per D-02, meaningless across seasons
- **Do not use `calculateStandingsWithBuchholz`** -- Only `calculateStandings` should be called per season for alltime aggregation

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Per-season standings | Custom match aggregation | `calculateStandings(seasonId)` | Already handles MatchScoring, succession maps, bye matches |
| Sub-team resolution | Manual parent lookup | `Team.getParentOrSelf()` | Established pattern, handles null parentTeam |
| Season filtering | Custom query for seasons with matches | `seasonRepository.findAll()` + filter on `calculateStandings` result | D-07: any season with match results counts; `calculateStandings` returns empty for seasons without matches |

## Common Pitfalls

### Pitfall 1: Lazy Loading in Season Iteration
**What goes wrong:** Calling `seasonRepository.findAll()` then iterating and calling `calculateStandings(seasonId)` may trigger N+1 queries for season scoring/teams.
**Why it happens:** `calculateStandings(seasonId)` internally calls `seasonRepository.findById(seasonId)` which loads the season again, so the initial `findAll()` seasons are not reused for scoring data.
**How to avoid:** This is actually fine -- `calculateStandings` does its own `findById` lookup with OSIV active. The N+1 is acceptable for the small dataset (few seasons). No optimization needed.
**Warning signs:** Only becomes a problem if there are hundreds of seasons (not the case for a racing league).

### Pitfall 2: Team Identity Across Seasons
**What goes wrong:** The same team may appear as different entities across seasons (e.g., a team renamed, or sub-teams used in one season but not another).
**Why it happens:** Teams have `parentTeam` relationships, and sub-teams may compete in some seasons while parent teams compete in others.
**How to avoid:** Always resolve to parent via `getParentOrSelf()` AFTER per-season calculation. The per-season `calculateStandings` already handles within-season succession via `buildSuccessionMap()`. Cross-season parent resolution is the separate step.
**Warning signs:** Teams appearing twice in alltime standings with slightly different names.

### Pitfall 3: Double-Counting Sub-Team Results
**What goes wrong:** If both a parent and sub-team have standings in different seasons, and parent resolution maps them to the same parent, the merge works correctly. But if within a single season a parent AND its sub-team both have standings (should not happen with `getActiveTeams()` + succession), results could double-count.
**Why it happens:** Edge case in data model.
**How to avoid:** The existing `calculateStandings` uses `getActiveTeams()` which filters to non-replaced teams only. Sub-teams are separate entries. `getParentOrSelf()` during cross-season merge correctly collapses them.

### Pitfall 4: Empty Alltime Standings
**What goes wrong:** If no seasons have completed matches, alltime standings returns an empty list, and template shows "No results yet."
**Why it happens:** Normal state for a fresh installation.
**How to avoid:** This is correct behavior -- no special handling needed. Template already handles empty standings with the "No results yet" message.

## Code Examples

### Existing Pattern: DriverRankingService.calculateAlltimeRanking()
```java
// Source: src/main/java/org/ctc/domain/service/DriverRankingService.java:57-91
// Key pattern: findAll across seasons, resolve to parent team, aggregate
List<RaceResult> results = raceResultRepository.findByRacePlayoffMatchupIsNull();
List<SeasonDriver> allSeasonDrivers = seasonDriverRepository.findAll();
// ... resolve to parent via getParentOrSelf() ...
```

### Existing Pattern: StandingsService.calculateStandings()
```java
// Source: src/main/java/org/ctc/domain/service/StandingsService.java:30-56
// Key pattern: load season, get matchScoring, iterate matches, accumulate standings
var matchScoring = season.getMatchScoring();
List<Match> matches = matchRepository.findByMatchdaySeasonId(seasonId);
// ... processMatch per match, sort by points/diff/pointsFor ...
```

### Integration Point: StandingsController TODO
```java
// Source: src/main/java/org/ctc/admin/controller/StandingsController.java:31-33
if (isAlltime) {
    model.addAttribute("standings", java.util.List.of()); // Replace with calculateAlltimeStandings()
    model.addAttribute("driverRanking", driverRankingService.calculateAlltimeRanking());
}
```

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Mockito + Spring Boot Test |
| Config file | `pom.xml` (surefire + failsafe) |
| Quick run command | `./mvnw test -pl . -Dtest=StandingsServiceTest` |
| Full suite command | `./mvnw verify` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| FEAT-01a | calculateAlltimeStandings returns correct aggregation across multiple seasons | unit | `./mvnw test -Dtest=StandingsServiceTest#AlltimeStandingsTest -pl .` | Wave 0 |
| FEAT-01b | Sub-team results aggregate to parent team in alltime | unit | `./mvnw test -Dtest=StandingsServiceTest#AlltimeStandingsTest -pl .` | Wave 0 |
| FEAT-01c | Seasons without match results are excluded | unit | `./mvnw test -Dtest=StandingsServiceTest#AlltimeStandingsTest -pl .` | Wave 0 |
| FEAT-01d | Different MatchScoring rules per season are respected | unit | `./mvnw test -Dtest=StandingsServiceTest#AlltimeStandingsTest -pl .` | Wave 0 |
| FEAT-01e | Controller returns alltime standings (not empty list) | integration | `./mvnw test -Dtest=StandingsControllerTest -pl .` | Existing test needs update |
| FEAT-01f | Existing per-season standings remain unchanged | unit | `./mvnw test -Dtest=StandingsServiceTest#MatchBasedStandingsTest -pl .` | Existing |

### Sampling Rate
- **Per task commit:** `./mvnw test -Dtest=StandingsServiceTest -pl .`
- **Per wave merge:** `./mvnw verify`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `StandingsServiceTest` -- new `@Nested AlltimeStandingsTest` class with multi-season test fixtures
- [ ] `StandingsControllerTest` -- update `whenGetAlltimeStandings_thenReturnsAlltimeView` to verify standings are not empty (currently the TODO returns `List.of()`)

## Test Data Strategy for Unit Tests

The existing `StandingsServiceTest` uses Mockito mocks. For alltime tests:

1. Create 2-3 seasons with different `MatchScoring` (e.g., 3-1-0 vs 2-1-0)
2. Create teams, some shared across seasons, some with sub-teams
3. Mock `seasonRepository.findAll()` to return all seasons
4. Mock `seasonRepository.findById()` and `matchRepository.findByMatchdaySeasonId()` per season
5. Verify aggregation sums, parent resolution, and sorting

For integration tests (`StandingsControllerTest`):
- Use `TestHelper.createSeason()` to create multiple seasons with different MatchScoring
- Create matchdays and matches with scores
- Add shared teams across seasons
- Verify the controller returns non-empty alltime standings

## Sources

### Primary (HIGH confidence)
- `StandingsService.java` -- Full source code review of per-season calculation logic
- `DriverRankingService.java` -- Full source code review of alltime aggregation pattern
- `StandingsController.java` -- TODO placeholder at line 33, isAlltime flag handling
- `standings.html` -- Template conditional rendering already supports alltime
- `Team.java` -- `getParentOrSelf()` method
- `Season.java` -- `buildSuccessionMap()`, `getActiveTeams()`
- `MatchScoring.java` -- pointsWin/pointsDraw/pointsLoss fields
- `StandingsServiceTest.java` -- Existing test structure and patterns
- `StandingsControllerTest.java` -- Existing integration test for alltime (empty)
- `TestHelper.java` -- Test fixture creation patterns

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - No new libraries, pure domain logic within existing service
- Architecture: HIGH - Established alltime aggregation pattern in DriverRankingService, clear controller/template integration points
- Pitfalls: HIGH - Small scope, well-understood domain model, existing patterns to follow

**Research date:** 2026-04-05
**Valid until:** 2026-05-05 (stable -- no external dependencies)
