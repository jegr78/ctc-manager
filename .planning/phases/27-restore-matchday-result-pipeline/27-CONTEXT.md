# Phase 27: Restore Matchday/Result Seed Pipeline - Context

**Gathered:** 2026-04-10
**Status:** Ready for planning
**Source:** Auto-mode (recommended defaults selected)

<domain>
## Phase Boundary

Re-add the `seedMatchdaysAndResults()` pipeline that was removed by Phase 24, adapted for the fictive teams seeded by Phase 22/24. This pipeline creates matchdays, matches, races, race lineups, race results, and scoring for all three season formats (League, Swiss, Round Robin) in the dev profile.

</domain>

<decisions>
## Implementation Decisions

### Data Restoration Strategy
- **D-01:** Restore `seedMatchdaysAndResults()` and its helper methods (`seedLeagueSeason`, `seedSwissSeason`, `seedRoundRobinSeason`) from git commit `0396427` into `TestDataService.java`
- **D-02:** Adapt all team/driver references from the real CTC teams (used in Phase 23) to the fictive teams seeded by Phase 22/24 (VRX, SGM, ADR, TBR, ICL, SVT, NFR, EGP, HMS, PWR)
- **D-03:** Re-add the `seedMatchdaysAndResults()` call in the `seed()` method after `seedSeasonDrivers()`

### Season Format Coverage
- **D-04:** League format (S4 2026): 5 matchdays with Match + Race + RaceLineup + RaceResult per matchday
- **D-05:** Swiss format (S2 2024): 5 matchdays, each with 2 races, same result pipeline
- **D-06:** Round Robin format (S1 2023): 2 groups, 3 matchdays per group with full result pipeline

### Scoring Integration
- **D-07:** Use `ScoringService.calculatePoints()` to compute points from positions (not hard-coded)
- **D-08:** Call `aggregateMatchScores()` after saving results for each race — this is the established pattern (see CLAUDE.md: "Nach Results-Save immer aggregateMatchScores() aufrufen")
- **D-09:** Inject `ScoringService` into `TestDataService` if not already present

### Test Coverage
- **D-10:** Integration tests verifying: League season has 5 matchdays with non-zero standings
- **D-11:** Integration tests verifying: Swiss season has 5 matchdays with non-zero standings
- **D-12:** Integration tests verifying: Round Robin seasons have 3 matchdays each with results
- **D-13:** Integration test verifying: Points computed by ScoringService are non-zero and consistent

### Claude's Discretion
- Exact matchday naming convention (e.g., "Matchday 1", "MD 1")
- Number of race results per race (should match team count in format)
- Position assignment strategy for results (random, sequential, or round-robin)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase 23 (original implementation)
- `.planning/phases/23-dev-seasons-with-results/23-RESEARCH.md` — Original research for seedMatchdaysAndResults, requirement mapping DATA-04 through DATA-07
- `.planning/phases/23-dev-seasons-with-results/23-02-PLAN.md` — Original plan that created the seed pipeline
- `.planning/phases/23-dev-seasons-with-results/23-02-SUMMARY.md` — What was actually built, commit references

### Phase 24 (what was removed)
- `.planning/phases/24-restore-fictive-dev-data/24-01-PLAN.md` — Plan that removed seedMatchdaysAndResults

### Source code
- `src/main/java/org/ctc/admin/TestDataService.java` — Current state (pipeline removed)
- Git commit `0396427` — Last version with seedMatchdaysAndResults intact

### Project conventions
- `CLAUDE.md` — Score aggregation rule, TDD approach, test naming convention

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `TestDataService.java` already has all repository injections (MatchdayRepository, etc.) and imports for Matchday
- `ScoringService` exists and is used elsewhere for point calculation
- Season formats (LEAGUE, SWISS, ROUND_ROBIN) are already seeded by `seedSeasons()`
- Fictive teams and drivers are seeded by Phase 22/24 code

### Established Patterns
- `TestDataService.seed()` calls private methods in sequence
- Each seed method uses repository.save() directly
- Race results use `RaceLineup` for driver-team assignments (source of truth)
- Scoring follows: save RaceResult → calculatePoints() → aggregateMatchScores()

### Integration Points
- `seed()` method in TestDataService — add `seedMatchdaysAndResults()` call
- Existing seasons created by `seedSeasons()` — pipeline creates matchdays for those seasons
- `TestDataServiceIntegrationTest.java` — add tests for matchday/result verification

</code_context>

<specifics>
## Specific Ideas

- The ~205-line method from commit `0396427` is the reference implementation — adapt, don't rewrite
- All three season formats must have complete seed data for visual verification in dev mode
- Phase 26 restored logos, so team cards can now render correctly with matchday data

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 27-restore-matchday-result-pipeline*
*Context gathered: 2026-04-10 via auto-mode*
