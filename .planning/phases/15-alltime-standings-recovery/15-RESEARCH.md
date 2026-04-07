# Phase 15: Alltime Standings Recovery - Research

**Researched:** 2026-04-07
**Domain:** Spring Boot service layer, cross-season aggregation, JUnit 5 unit + integration tests
**Confidence:** HIGH

## Summary

Phase 15 recovers the alltime standings feature that was implemented in Phase 9 (commits `0979c0f`, `d5c6e56`) and subsequently lost to a worktree file clobber during Phase 10/11 parallel execution. The feature is a pure code addition — no schema changes, no new dependencies, no template changes.

The recovery source commits were examined in full and are intact in git history. The current codebase is in a compatible state: `StandingsService` at line 76 ends `calculateStandingsWithBuchholz()` and is the exact insertion point for `calculateAlltimeStandings()`. The `TeamStanding` inner class at line 172 is the exact insertion point for the `merge()` method. The `StandingsController` line 33 still contains the `TODO` placeholder comment and the `java.util.List.of()` stub that the recovery must replace.

The unit test class (`StandingsServiceTest`) currently has 15 tests across 3 `@Nested` classes with 0 alltime tests — the `AlltimeStandingsTest` nested class from the original commit does not exist in the current file. The integration test (`StandingsControllerTest`) has the `whenGetAlltimeStandings_thenReturnsAlltimeView()` test but without match data setup, without `MatchdayRepository`/`MatchRepository` `@Autowired` fields, and without the `hasSize(greaterThan(0))` assertion. Current test suite: 813 tests, all passing. Coverage minimum: 82%.

**Primary recommendation:** Apply the exact code from commits `0979c0f` and `d5c6e56` to the current files using manual diff application (not cherry-pick — the current `StandingsService` has gained `RaceRepository` injection and `calculateBuchholzScores()` since Phase 9, so cherry-pick would conflict). The code is fully self-contained and non-conflicting with post-Phase-9 additions.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**D-01:** Iterate all seasons with match results, calculate per-season standings using each season's own MatchScoring rules, then sum W/D/L/Points/PointsFor/PointsAgainst across seasons per team. Reuses `calculateStandings(seasonId)` as the building block.

**D-02:** No Buchholz in alltime standings — Buchholz is Swiss-format-specific and meaningless across seasons.

**D-03:** Resolve teams to parent via `Team.getParentOrSelf()` for cross-season aggregation. Sub-team results count toward the parent team.

**D-04:** Within each season, use `Season.buildSuccessionMap()` as `calculateStandings()` already does. Parent-resolution happens after per-season calculation when merging into alltime totals.

**D-05:** Alltime standings table uses standard league columns: #, Team, MP, W, D, L, PR, Pts.

**D-06:** Reuse the existing `standingsTable` template structure — no template changes needed.

**D-07:** Include all seasons that have match results (completed matches with scores). No filtering by season status.

**D-08:** Cherry-pick the logic from commits `0979c0f` and `d5c6e56` into the current StandingsService structure. The current service has gained `calculateStandingsWithBuchholz()` and `RaceRepository` since Phase 9 — the alltime code is additive and non-conflicting.

### Claude's Discretion

- Whether to apply diffs manually or use git cherry-pick (manual preferred since StandingsService has new methods)
- Test data setup adjustments if StandingsServiceTest structure changed since Phase 9

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| FEAT-01 | Alltime Standings zeigt cross-season Team-Aggregation (StandingsService.calculateAlltimeStandings()) | Recovery commits provide complete, verified implementation. Current codebase has all prerequisite methods and models. No new dependencies required. |
</phase_requirements>

## Standard Stack

### Core (no new dependencies required)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 4.x (project-defined) | Service/controller layer | Already in use [VERIFIED: pom.xml] |
| JUnit 5 | Project-defined | Unit + integration tests | Already in use [VERIFIED: pom.xml] |
| Mockito | Project-defined | Service unit test mocks | Already in use [VERIFIED: StandingsServiceTest.java] |
| H2 + MockMvc | Project-defined | Integration test infrastructure | Already in use [VERIFIED: StandingsControllerTest.java] |

No new `pom.xml` dependencies needed. This phase is purely code additions.

**Installation:** None required.

## Architecture Patterns

### Recommended Insertion Points

```
src/main/java/org/ctc/domain/service/StandingsService.java
  Line 76 (after calculateStandingsWithBuchholz closes)  → insert calculateAlltimeStandings()
  Line 172 (inside TeamStanding inner class, before getters) → insert merge() method
  Also add: import org.ctc.domain.model.Season;

src/main/java/org/ctc/admin/controller/StandingsController.java
  Lines 32-33: Replace TODO comment + List.of() with standingsService.calculateAlltimeStandings()

src/test/java/org/ctc/domain/service/StandingsServiceTest.java
  After the TeamSuccessionTest @Nested class (line 390, before findStanding helper)
  → insert AlltimeStandingsTest @Nested class with 7 tests

src/test/java/org/ctc/admin/controller/StandingsControllerTest.java
  Add @Autowired MatchdayRepository + MatchRepository fields (after line 30)
  Expand whenGetAlltimeStandings_thenReturnsAlltimeView() test with match data setup
  and hasSize(greaterThan(0)) assertion
```

### Pattern 1: calculateAlltimeStandings() — exact code from commit 0979c0f

**What:** Iterates all seasons via `seasonRepository.findAll()`, calls `calculateStandings(seasonId)` per season, resolves each team to parent via `getParentOrSelf()`, merges into a shared alltime map, then sorts.

**When to use:** Called by `StandingsController` when `seasonId=alltime`.

```java
// Source: git commit 0979c0f
@Transactional(readOnly = true)
public List<TeamStanding> calculateAlltimeStandings() {
    List<Season> allSeasons = seasonRepository.findAll();
    Map<UUID, TeamStanding> alltimeMap = new HashMap<>();

    for (Season season : allSeasons) {
        List<TeamStanding> seasonStandings = calculateStandings(season.getId());
        if (seasonStandings.isEmpty()) continue;

        for (TeamStanding standing : seasonStandings) {
            Team parentTeam = standing.getTeam().getParentOrSelf();
            TeamStanding alltime = alltimeMap.computeIfAbsent(
                parentTeam.getId(), id -> new TeamStanding(parentTeam));
            alltime.merge(standing);
        }
    }

    List<TeamStanding> result = new ArrayList<>(alltimeMap.values());
    result.sort(Comparator
        .<TeamStanding, Integer>comparing(TeamStanding::getPoints, Comparator.reverseOrder())
        .thenComparing(TeamStanding::getPointDifference, Comparator.reverseOrder())
        .thenComparing(TeamStanding::getPointsFor, Comparator.reverseOrder()));

    log.debug("Calculated alltime standings: {} teams across {} seasons", result.size(), allSeasons.size());
    return result;
}
```

### Pattern 2: TeamStanding.merge() — exact code from commit 0979c0f

**What:** Accumulates stats from a per-season TeamStanding into the alltime TeamStanding.

```java
// Source: git commit 0979c0f
public void merge(TeamStanding other) {
    this.wins += other.wins;
    this.draws += other.draws;
    this.losses += other.losses;
    this.points += other.points;
    this.pointsFor += other.pointsFor;
    this.pointsAgainst += other.pointsAgainst;
}
```

### Pattern 3: Controller wiring — exact change from commit d5c6e56

**What:** Replace the TODO placeholder in `StandingsController.standings()`.

```java
// Source: git commit d5c6e56
// REMOVE:
// TODO: Alltime-Standings muessen cross-season MatchScoring-Aggregation unterstuetzen
model.addAttribute("standings", java.util.List.of());

// REPLACE WITH:
model.addAttribute("standings", standingsService.calculateAlltimeStandings());
```

### Pattern 4: Integration test enhancement — exact change from commit d5c6e56

**What:** `whenGetAlltimeStandings_thenReturnsAlltimeView()` currently passes but does not assert non-empty standings. Must add match data setup and the `hasSize(greaterThan(0))` assertion. Also requires `@Autowired MatchdayRepository matchdayRepository` and `@Autowired MatchRepository matchRepository` fields.

```java
// Source: git commit d5c6e56
@Test
void whenGetAlltimeStandings_thenReturnsAlltimeView() throws Exception {
    // given - create a matchday and match with scores for activeSeason
    var matchday = new Matchday(activeSeason, "Spieltag 1", 1);
    matchday = matchdayRepository.save(matchday);
    var teamA = activeSeason.getSeasonTeams().stream()
        .map(SeasonTeam::getTeam).findFirst().orElseThrow();
    var teamB = activeSeason.getSeasonTeams().stream()
        .map(SeasonTeam::getTeam).skip(1).findFirst().orElseThrow();
    var match = new Match(matchday, teamA, teamB);
    match.setHomeScore(70);
    match.setAwayScore(46);
    matchRepository.save(match);

    // when
    mockMvc.perform(get("/admin/standings").param("seasonId", "alltime"))
            // then
            .andExpect(status().isOk())
            .andExpect(view().name("admin/standings"))
            .andExpect(model().attributeExists("seasons", "standings", "driverRanking"))
            .andExpect(model().attribute("isAlltime", true))
            .andExpect(model().attribute("selectedSeasonId", "alltime"))
            .andExpect(model().attributeDoesNotExist("selectedSeason"))
            .andExpect(model().attribute("standings", hasSize(greaterThan(0))));
}
```

### Anti-Patterns to Avoid

- **Do not cherry-pick directly:** `git cherry-pick 0979c0f` will conflict because `StandingsService` has added `RaceRepository`, `calculateBuchholzScores()`, and `calculateStandingsWithBuchholz()` between Phase 9 and now. Apply diffs manually instead.
- **Do not use `seasonRepository.findAll()` unbounded without caution:** In this case it is correct per D-07 (all seasons included). No filtering by status. The `calculateStandings()` inner call already handles empty seasons by returning an empty list.
- **Do not add Buchholz to alltime:** Per D-02, `merge()` does not include `buchholz`, and `calculateAlltimeStandings()` does not call `calculateBuchholzScores()`.
- **Do not add template changes:** Per D-06, the existing `standingsTable` fragment already handles alltime display. No Thymeleaf changes needed.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Per-season calculation | Custom match iteration in alltime method | `calculateStandings(seasonId)` | Already handles succession maps, bye matches, null scores, scoring rules |
| Sub-team resolution | Manual parentTeam null check | `Team.getParentOrSelf()` | Established pattern; used identically in `DriverRankingService.calculateAlltimeRanking()` |
| Alltime sort | Custom comparator | Copy sort from `calculateStandings()` minus Buchholz | Consistent tie-breaking rules |

## Common Pitfalls

### Pitfall 1: Missing Season import in StandingsService
**What goes wrong:** `List<Season> allSeasons = seasonRepository.findAll()` fails to compile.
**Why it happens:** `Season` is not currently imported in `StandingsService.java` (verified: only `Match`, `MatchScoring`, `Race`, `Team` are imported).
**How to avoid:** Add `import org.ctc.domain.model.Season;` alongside the existing imports.
**Warning signs:** Compilation error on `List<Season>`.

### Pitfall 2: Controller test missing repository injections
**What goes wrong:** `matchdayRepository.save(matchday)` NPE in integration test.
**Why it happens:** `StandingsControllerTest` does not currently `@Autowired` `MatchdayRepository` or `MatchRepository` (verified: only `SeasonRepository`, `TeamRepository`, `TestHelper` are present).
**How to avoid:** Add both `@Autowired` fields before the `whenGetAlltimeStandings` test setup.
**Warning signs:** NullPointerException in the `whenGetAlltimeStandings` test.

### Pitfall 3: AlltimeStandingsTest helper method visibility
**What goes wrong:** `AlltimeStandingsTest` nested class cannot call `createMatchWithScore()` or `findStanding()`.
**Why it happens:** These are private methods at the outer `StandingsServiceTest` class level — but since `AlltimeStandingsTest` is a non-static inner class (standard JUnit 5 `@Nested`), it has access to private outer class members.
**How to avoid:** No action needed — `@Nested` inner classes in JUnit 5 are inner classes and share access. Confirmed by how `MatchBasedStandingsTest`, `TeamSuccessionTest`, and `CalculateStandingsWithBuchholzTest` already use these helpers.
**Warning signs:** None expected if structure matches existing nested classes.

### Pitfall 4: `calculateAlltimeStandings()` N+1 query pattern
**What goes wrong:** Each `calculateStandings(seasonId)` call triggers multiple queries. For N seasons this is N×queries.
**Why it happens:** `seasonRepository.findAll()` + per-season `matchRepository.findByMatchdaySeasonId()` + per-season `seasonRepository.findById()`.
**How to avoid:** Accepted — per D-07, all seasons are included. The number of seasons in this application is small (single digits). OSIV is enabled. This is a known, acceptable tradeoff per the original Phase 9 design.
**Warning signs:** Slow response only if season count grows significantly.

## Code Examples

### Current StandingsService structure (verified 2026-04-07)

```
Lines 1-17:   package + imports (Season NOT imported)
Lines 22-27:  class declaration + @Autowired fields (includes RaceRepository)
Lines 29-56:  calculateStandings(UUID seasonId)
Lines 58-76:  calculateStandingsWithBuchholz(UUID seasonId)   ← INSERT calculateAlltimeStandings() AFTER here
Lines 78-110: calculateBuchholzScores(UUID seasonId)
Lines 112-166: processMatch() + resolveTeamId()
Lines 172-206: TeamStanding inner class                       ← INSERT merge() BEFORE getters (line 194)
```

### Current StandingsControllerTest test that needs enhancement

The test `whenGetAlltimeStandings_thenReturnsAlltimeView()` (line 81) currently passes without match data because `List.of()` is what the controller returns (no `hasSize` check). After this phase, the test must verify the endpoint returns actual standings.

## State of the Art

| Old Approach (Phase 9 state before clobber) | Current State (to be recovered) | Impact |
|---------------------------------------------|----------------------------------|--------|
| `calculateAlltimeStandings()` implemented | TODO stub in controller, no service method | GET /admin/standings?seasonId=alltime returns empty list |
| `TeamStanding.merge()` implemented | No merge method | Cannot aggregate across seasons |
| 7 AlltimeStandingsTest unit tests | 0 alltime unit tests (15 total in other nested classes) | Coverage gap |
| Integration test verifies non-empty standings | Test passes but does not verify standings content | Weak assertion |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | No template changes needed — standingsTable fragment already handles alltime display | Architecture Patterns, D-06 | If template was changed after Phase 9 in a way that broke alltime rendering, the feature would appear broken in UI without test failures. Low risk — template is server-side rendered and integration test covers the endpoint. | [ASSUMED based on CONTEXT.md D-06; template not re-read in this session] |

## Open Questions

1. **Integration test: will SeasonTeam be loaded in the alltime test?**
   - What we know: `activeSeason.getSeasonTeams()` is used in the integration test to find teams. OSIV is enabled, `@Transactional` is on the test class.
   - What's unclear: Whether the `SeasonTeam` relationship is loaded after `seasonRepository.save(activeSeason)` when accessed via `activeSeason.getSeasonTeams()` inside the test.
   - Recommendation: The existing `setUp()` already calls `activeSeason.addTeam()` + `seasonRepository.save()`, which is the same pattern used in other controller tests. The alltime test setup in the original Phase 9 commit uses this identical pattern — low risk.

## Environment Availability

Step 2.6: SKIPPED (no external dependencies — pure Java code addition to existing service and test files)

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Mockito (unit), SpringBootTest + MockMvc (integration) |
| Config file | pom.xml (Surefire plugin) |
| Quick run command | `./mvnw test -Dtest=StandingsServiceTest,StandingsControllerTest` |
| Full suite command | `./mvnw verify` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| FEAT-01 | calculateAlltimeStandings() aggregates W/D/L/Pts across seasons | unit | `./mvnw test -Dtest="StandingsServiceTest\$AlltimeStandingsTest"` | ❌ Wave 0 — nested class missing |
| FEAT-01 | calculateAlltimeStandings() respects per-season MatchScoring rules | unit | same | ❌ Wave 0 |
| FEAT-01 | Sub-team results aggregate to parent team | unit | same | ❌ Wave 0 |
| FEAT-01 | Seasons with no matches are excluded | unit | same | ❌ Wave 0 |
| FEAT-01 | Alltime standings sorted points→pointDiff→pointsFor | unit | same | ❌ Wave 0 |
| FEAT-01 | Buchholz is always 0 in alltime standings | unit | same | ❌ Wave 0 |
| FEAT-01 | Empty season list returns empty result | unit | same | ❌ Wave 0 |
| FEAT-01 | GET /admin/standings?seasonId=alltime returns non-empty standings | integration | `./mvnw test -Dtest=StandingsControllerTest#whenGetAlltimeStandings_thenReturnsAlltimeView` | ✅ exists but weak (enhancement needed) |

### Sampling Rate

- **Per task commit:** `./mvnw test -Dtest=StandingsServiceTest,StandingsControllerTest`
- **Per wave merge:** `./mvnw verify`
- **Phase gate:** Full suite green (`./mvnw verify`) before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `StandingsServiceTest$AlltimeStandingsTest` nested class — 7 unit tests for FEAT-01 (all listed above)
- [ ] `StandingsControllerTest#whenGetAlltimeStandings_thenReturnsAlltimeView` — enhance existing test with match data + `hasSize(greaterThan(0))`

## Security Domain

This phase adds a read-only, internal-only aggregation method. No new attack surface:

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | Auth enforced at prod profile via existing Spring Security config |
| V3 Session Management | No | No session changes |
| V4 Access Control | No | `/admin/**` already protected |
| V5 Input Validation | No | No new user input; `seasonId=alltime` string literal comparison already exists |
| V6 Cryptography | No | No cryptographic operations |

No new threat patterns introduced. `calculateAlltimeStandings()` is `@Transactional(readOnly = true)` and receives no user input.

## Sources

### Primary (HIGH confidence)

- `git show 0979c0f` — Full diff of `calculateAlltimeStandings()`, `merge()`, and all 7 unit tests [VERIFIED: git history]
- `git show d5c6e56` — Full diff of controller wiring and integration test enhancement [VERIFIED: git history]
- `src/main/java/org/ctc/domain/service/StandingsService.java` — Current service state, line numbers confirmed [VERIFIED: Read tool]
- `src/main/java/org/ctc/admin/controller/StandingsController.java` — TODO placeholder confirmed at line 33 [VERIFIED: Read tool]
- `src/test/java/org/ctc/domain/service/StandingsServiceTest.java` — 15 existing tests, no AlltimeStandingsTest [VERIFIED: Read tool + test run]
- `src/test/java/org/ctc/admin/controller/StandingsControllerTest.java` — Missing @Autowired repos confirmed [VERIFIED: Read tool]
- `./mvnw test` — 813 tests, all green, confirmed 2026-04-07 [VERIFIED: Bash]
- `pom.xml` JaCoCo minimum: 0.82 [VERIFIED: grep]

### Secondary (MEDIUM confidence)

- `.planning/phases/15-alltime-standings-recovery/15-CONTEXT.md` — All design decisions [CITED: project planning]
- `src/main/java/org/ctc/domain/model/Team.java` — `getParentOrSelf()` confirmed at line 69 [VERIFIED: grep]

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new dependencies, all existing libraries verified
- Architecture: HIGH — exact code from git history, insertion points verified line-by-line
- Pitfalls: HIGH — derived from actual code inspection of current files vs. recovery commits
- Test map: HIGH — test file contents and test runner output verified

**Research date:** 2026-04-07
**Valid until:** 2026-04-14 (stable domain, low volatility)
