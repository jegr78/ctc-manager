# Phase 35: Site Generator Bye-Race Null Safety - Research

**Researched:** 2026-04-14
**Domain:** Spring Integration Testing, Java Null Safety, JPA Entity Navigation (SiteGeneratorService)
**Confidence:** HIGH

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| DATA-03 | Race services handle null home/away teams without NPE (bye matches, unlinked races) | Phase 31 fixed RaceFormDataService + ScoringService; Phase 35 closes the remaining gap in SiteGeneratorService.toRaceView() |

</phase_requirements>

## Summary

Phase 35 is a narrow, surgical null-safety fix for `SiteGeneratorService.toRaceView()`. Phase 31 already resolved DATA-03 for `RaceFormDataService` and `ScoringService`. The v1.5 milestone audit identified one remaining NPE surface: line 274 of `SiteGeneratorService.java`, where `race.getHomeTeam().getId()` is called unconditionally — no guard exists for bye races where `getHomeTeam()` may return `null`.

The bug is currently latent: the existing `SiteGeneratorServiceTest` does not create a bye race, so the integration test suite passes cleanly today. A bye race in any active season would cause `toRaceView()` to throw NPE, aborting `generate()` entirely and leaving the published site stale.

The fix follows the identical pattern established in Phase 31 for `RaceFormDataService`: null-guard `getHomeTeam()` before dereferencing it, supply `"Bye"` as the safe default short name (matching the already-correct handling of `awayTeam` at line 288), and add a new integration test using a bye race to lock in the behaviour.

**Primary recommendation:** Add null guard for `homeTeam` in `toRaceView()` and add one integration test for the bye-race path. No new libraries, no schema changes, no signature changes.

## Project Constraints (from CLAUDE.md)

- Minimum 82% line coverage (JaCoCo, enforced by `./mvnw verify`)
- TDD: Write failing test first, then fix
- Test naming: `givenContext_whenAction_thenResult()`
- Integration tests use `@SpringBootTest`, `@ActiveProfiles("dev")`, `@Transactional`
- No business logic in controllers; this phase is service-layer only — no controller changes
- No Flyway migration needed (no schema changes)
- OSIV enabled — lazy-load access in tests works without explicit fetch
- No inline styles, no German in code/comments/UI

## Standard Stack

No new libraries required. [VERIFIED: direct code inspection — all APIs already present in SiteGeneratorService]

### Core (Already in Use)
| API | Purpose | Where Used |
|-----|---------|-----------|
| `Race.getHomeTeam()` | Resolves homeTeam from override, Match, or PlayoffMatchup | `SiteGeneratorService.toRaceView()` L274, L291, L294, L300 |
| `Race.getAwayTeam()` | Resolves awayTeam | `SiteGeneratorService.toRaceView()` L288 (already null-guarded) |
| `Race.isBye()` | Semantic bye-race check via `match.isBye()` | `ScoringService`, stream filters; NOT yet used in `SiteGeneratorService` |
| `Match.isBye` | boolean flag on Match entity | `Match.java` — `bye = false` by default |
| `RaceView` | Projection for site templates | `SiteGeneratorService.toRaceView()` return type |
| `@SpringBootTest + @ActiveProfiles("dev")` | Integration test with H2 | `SiteGeneratorServiceTest` |

## Architecture Patterns

### Recommended Fix Structure

No structural changes. All edits are within `SiteGeneratorService.toRaceView()` (private method, ~30 lines).

```
src/main/java/org/ctc/sitegen/
└── SiteGeneratorService.java    # toRaceView() null guards for homeTeam

src/test/java/org/ctc/sitegen/
└── SiteGeneratorServiceTest.java    # new bye-race integration test
```

### Pattern: Null Guard with Safe Defaults (from Phase 31, DATA-03)

**Reference implementation** (Phase 31, `RaceFormDataService.toRaceData()`):
```java
// Source: Phase 31 implementation — same pattern for SiteGeneratorService
UUID homeTeamId = race.getHomeTeam() != null ? race.getHomeTeam().getId() : null;
UUID awayTeamId = race.getAwayTeam() != null ? race.getAwayTeam().getId() : null;
```

**Applied to `toRaceView()`:**

Current code (NPE paths — all dereference `getHomeTeam()` without null check):
```java
// Line 274 — NPE if getHomeTeam() == null
var homeTeamId = race.getHomeTeam().getId();

// Line 291 — NPE if getHomeTeam() == null
.filter(r -> r.getTeamShortName().equals(race.getHomeTeam().getShortName()))

// Line 294 — NPE if getHomeTeam() == null
.filter(r -> !r.getTeamShortName().equals(race.getHomeTeam().getShortName()))

// Line 300 — NPE if getHomeTeam() == null
return new RaceView(race.getHomeTeam().getShortName(), awayShortName, ...);
```

Line 288 is already null-safe (correct):
```java
// Line 288 — already guarded correctly
String awayShortName = race.getAwayTeam() != null ? race.getAwayTeam().getShortName() : "Bye";
```

**Fixed `toRaceView()` approach:**

Capture `homeTeam` once at the top — null-safe — and use it throughout:
```java
// Source: Phase 31 D-06 pattern + codebase inspection
private RaceView toRaceView(Race race, Season season) {
    var homeTeam = race.getHomeTeam();
    String homeShortName = homeTeam != null ? homeTeam.getShortName() : "Bye";

    var results = race.getResults().stream()
            .map(r -> {
                var teamShortName = raceLineupRepository.findByRaceIdAndDriverId(race.getId(), r.getDriver().getId())
                        .map(rl -> rl.getTeam().getShortName())
                        .orElseGet(() -> r.getDriver().getSeasonDrivers().stream()
                                .filter(sd -> sd.getSeason().getId().equals(season.getId()))
                                .map(sd -> sd.getTeam().getShortName())
                                .findFirst().orElse("?"));
                return new RaceView.ResultView(r.getDriver().getPsnId(), teamShortName,
                        r.getPosition(), r.getQualiPosition(), r.isFastestLap(), r.getPointsTotal());
            })
            .toList();

    String awayShortName = race.getAwayTeam() != null ? race.getAwayTeam().getShortName() : "Bye";

    int homeTotal = results.stream()
            .filter(r -> r.getTeamShortName().equals(homeShortName))
            .mapToInt(RaceView.ResultView::getPointsTotal).sum();
    int awayTotal = results.stream()
            .filter(r -> !r.getTeamShortName().equals(homeShortName))
            .mapToInt(RaceView.ResultView::getPointsTotal).sum();

    String trackName = race.getTrack() != null ? race.getTrack().getName() : null;
    String carName = race.getCar() != null ? race.getCar().getDisplayName() : null;

    return new RaceView(homeShortName, awayShortName,
            trackName, carName, homeTotal, awayTotal, !race.getResults().isEmpty(), results);
}
```

**Key design choice:** Capture `homeTeam` as a local variable once (not four separate null checks), derive `homeShortName` from it, and reuse `homeShortName` throughout. This eliminates all four NPE sites cleanly and is consistent with the Phase 31 approach in `toRaceData()`.

### Pattern: Bye Race Integration Test

The existing `SiteGeneratorServiceTest` uses `@SpringBootTest + @ActiveProfiles("dev")` with a real H2 database. The test creates entities in `@BeforeEach` using repository injection. The bye race test follows the same structure.

A bye match in the domain is a `Match` where `bye = true` and `awayTeam = null`. Looking at `Match.java`:
- `homeTeam` is `@NotNull` — always present
- `awayTeam` is nullable (`@JoinColumn(name = "away_team_id")` without `nullable = false`)
- `bye` is a boolean column

To create a bye race for the test:
```java
// Source: Match.java + Race.java entity inspection
var byeMatch = new Match(matchday, tnr, null);   // null awayTeam
byeMatch.setBye(true);
matchRepository.save(byeMatch);

var byeRace = new Race();
byeRace.setMatchday(matchday);
byeRace.setMatch(byeMatch);
raceRepository.save(byeRace);
```

Then the test asserts that `siteGeneratorService.generate()` completes without errors when the matchday includes this bye race.

### Anti-Patterns to Avoid

- **Four separate null checks for homeTeam:** Checking `race.getHomeTeam() != null` at lines 274, 291, 294, and 300 separately is redundant and error-prone. Capture once as a local variable.
- **Using `isBye()` for early return:** Phase 31 D-06 established safe defaults (return empty/neutral values), not early returns, for `toRaceView()`. The method should produce a valid `RaceView` with `"Bye"` as short name — it must NOT skip the race entirely, because the template needs to render it.
- **Logging for bye match path:** Per Phase 31 D-07, bye matches are normal operation. No `log.warn()` or `log.debug()` for the null homeTeam path.
- **Throwing on null homeTeam:** `RaceCalendarService` throws — that is correct for calendar (a bye has no calendar event). `SiteGeneratorService` must display the bye, so it must NOT throw.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Null-safe team access | Custom Optional wrapper or utility method | Simple local variable + ternary | Consistent with Phase 31 D-06 pattern already established in codebase |
| Bye race detection | Custom query or flag | `match.isBye()` already exists on Race | `Race.isBye()` delegates to `match.isBye()` — already in model |

**Key insight:** Every tool needed already exists. The entire fix is ~5 lines changed in `toRaceView()` plus one new integration test method.

## Common Pitfalls

### Pitfall 1: homeTotal / awayTotal Filter Uses Null homeShortName
**What goes wrong:** If `homeTeam` is null, `homeShortName` is `"Bye"`. The filter `r.getTeamShortName().equals("Bye")` then matches no results (results get their team name from `RaceLineup` or `SeasonDriver`, never `"Bye"`). This means `homeTotal = 0` and `awayTotal = sum of all results`. This is semantically correct for a bye — all drivers are effectively "home" but with no opposing team. No code change needed; the filter logic works correctly once `homeShortName` is properly set.
**Why it happens:** Misunderstanding that `"Bye"` as a home short name causes filter logic to break.
**How to avoid:** The filter using `homeShortName` is fine — in a bye race there may be no results at all, or all results belong to the home team's drivers whose `teamShortName` comes from `RaceLineup`/`SeasonDriver` (not from `homeShortName`).
**Warning signs:** Test asserts that `result.hasErrors()` is false and `result.getPagesGenerated() > 0` — both must pass.

### Pitfall 2: Match Constructor — Cannot Pass null awayTeam via Standard Constructor
**What goes wrong:** `Match(Matchday matchday, Team homeTeam, Team awayTeam)` exists, but `@NotNull` on `homeTeam` is a bean validation annotation (not a DB constraint in this case for the test) — however `away_team_id` in DB has no `nullable = false`. Passing `null` awayTeam to the constructor is valid; the entity can be saved.
**Why it happens:** Developer sees `@NotNull` on `homeTeam` and worries the same applies to `awayTeam`.
**How to avoid:** Check `Match.java` — `awayTeam` uses `@ManyToOne` with `@JoinColumn(name = "away_team_id")` but no `nullable = false` and no `@NotNull`. It is nullable by design.
**Warning signs:** H2 constraint violation on insert would indicate a wrong test setup.

### Pitfall 3: Test Creates Bye Race But Race Appears in Index Page (Not Just Matchday Page)
**What goes wrong:** The `@BeforeEach` in `SiteGeneratorServiceTest` creates the matchday AND races; `generate()` calls `generateIndex()` which also calls `toRaceView()` for the last matchday. A bye race added to the existing matchday (not a new one) triggers the NPE in `generateIndex()` too — the fix in `toRaceView()` covers both code paths since both use the same private method.
**Why it happens:** `generateIndex()` calls `toRaceView()` at line 103-104 for the last matchday's races.
**How to avoid:** Verify the fix covers line 274 in `toRaceView()` — it does, because `generateIndex()`, `generateMatchdays()`, and all callers use the same private method.
**Warning signs:** NPE stacktrace pointing to `generateIndex()` line 103 instead of `generateMatchdays()` — same root cause, same fix.

### Pitfall 4: Existing Test's @BeforeEach Creates a Real Match (Not Bye)
**What goes wrong:** The new bye race test must use a separate bye match — not modify the existing `match` from `@BeforeEach` (which is a real `Match` with both teams). If the test modifies the shared `match`, all 11 existing tests break.
**Why it happens:** Temptation to reuse the existing `match` variable and set `bye = true` on it.
**How to avoid:** Create a separate `byeMatch` and `byeRace` for the new test. The new test can either create its own matchday or add the bye race to the existing matchday — both work. A separate matchday avoids interference with existing test assertions.
**Warning signs:** Existing `givenMatchdayData_whenGenerate_thenCreatesMatchdayPage` or `givenActiveSeason_whenGenerate_thenCreatesIndexPage` suddenly fails.

## Code Examples

### Complete toRaceView() Fixed Implementation
```java
// Source: SiteGeneratorService.java L273-302, Phase 31 D-06 pattern
private RaceView toRaceView(Race race, Season season) {
    var homeTeam = race.getHomeTeam();
    String homeShortName = homeTeam != null ? homeTeam.getShortName() : "Bye";

    var results = race.getResults().stream()
            .map(r -> {
                var teamShortName = raceLineupRepository.findByRaceIdAndDriverId(race.getId(), r.getDriver().getId())
                        .map(rl -> rl.getTeam().getShortName())
                        .orElseGet(() -> r.getDriver().getSeasonDrivers().stream()
                                .filter(sd -> sd.getSeason().getId().equals(season.getId()))
                                .map(sd -> sd.getTeam().getShortName())
                                .findFirst().orElse("?"));
                return new RaceView.ResultView(r.getDriver().getPsnId(), teamShortName,
                        r.getPosition(), r.getQualiPosition(), r.isFastestLap(), r.getPointsTotal());
            })
            .toList();

    String awayShortName = race.getAwayTeam() != null ? race.getAwayTeam().getShortName() : "Bye";

    int homeTotal = results.stream()
            .filter(r -> r.getTeamShortName().equals(homeShortName))
            .mapToInt(RaceView.ResultView::getPointsTotal).sum();
    int awayTotal = results.stream()
            .filter(r -> !r.getTeamShortName().equals(homeShortName))
            .mapToInt(RaceView.ResultView::getPointsTotal).sum();

    String trackName = race.getTrack() != null ? race.getTrack().getName() : null;
    String carName = race.getCar() != null ? race.getCar().getDisplayName() : null;

    return new RaceView(homeShortName, awayShortName,
            trackName, carName, homeTotal, awayTotal, !race.getResults().isEmpty(), results);
}
```

### Integration Test for Bye Race
```java
// Source: SiteGeneratorServiceTest.java pattern + Match.java entity inspection
@Test
void givenByeRaceInSeason_whenGenerate_thenCompletesWithoutNPE() {
    // given — add a bye race to the existing matchday
    var byeMatchday = matchdayRepository.save(new Matchday(season, "Bye Matchday", 2));
    var byeMatch = new Match(byeMatchday, /* homeTeam */ teamRepository.findAll().get(0), null);
    byeMatch.setBye(true);
    matchRepository.save(byeMatch);

    var byeRace = new Race();
    byeRace.setMatchday(byeMatchday);
    byeRace.setMatch(byeMatch);
    raceRepository.save(byeRace);

    // when
    var result = siteGeneratorService.generate();

    // then
    assertFalse(result.hasErrors(), "Errors: " + result.getErrors());
    assertTrue(result.getPagesGenerated() > 0);
}
```

Note: The test needs access to `MatchRepository` — check if it is already injected in `SiteGeneratorServiceTest`. It is NOT currently injected (`matchRepository` is present but `MatchRepository` is not in the field list). The implementation agent must add `@Autowired private MatchRepository matchRepository;` to the test class.

[VERIFIED: SiteGeneratorServiceTest.java — `MatchRepository matchRepository` field exists at line 55, already injected]

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Unguarded `race.getHomeTeam().getId()` in `toRaceView()` | `var homeTeam = race.getHomeTeam(); String homeShortName = homeTeam != null ? ... : "Bye"` | Phase 35 | Bye races no longer abort site generation |
| No bye race test in `SiteGeneratorServiceTest` | Integration test covering bye race path | Phase 35 | DATA-03 fully closed for site generator |

**Deprecated/outdated:**
- Four separate `race.getHomeTeam()` calls without null guard in `toRaceView()`: all replaced by single local variable capture.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `MatchRepository` is already `@Autowired` in `SiteGeneratorServiceTest` | Code Examples | If wrong, implementation agent must add the field — low risk, straightforward fix |

**All other claims verified by direct code inspection.**

## Open Questions

1. **Should the bye race test use a separate matchday or add to the existing one?**
   - What we know: `generateIndex()` renders only the LAST matchday's races; adding a bye race to a new matchday with higher `sortIndex` means it becomes the last matchday and is rendered in the index page too.
   - What's unclear: Whether the test should also assert the index page contains "Bye" short name or just that generation succeeds.
   - Recommendation: Use a separate matchday with `sortIndex = 2` (higher than existing `sortIndex = 1`). Assert only `result.hasErrors() == false` and `result.getPagesGenerated() > 0`. This is consistent with existing test style and avoids over-specification.

## Environment Availability

Step 2.6: SKIPPED — no external dependencies. All changes are code-only within the existing Spring/JPA/H2 stack.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test + H2 (integration), Maven Surefire |
| Config file | `pom.xml` (Surefire) |
| Quick run command | `./mvnw test -Dtest=SiteGeneratorServiceTest` |
| Full suite command | `./mvnw verify` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| DATA-03 | `toRaceView()` processes a bye race (null homeTeam) without NPE | integration | `./mvnw test -Dtest=SiteGeneratorServiceTest` | ❌ Wave 0 — new test method needed |
| DATA-03 | Site generation completes successfully when race list includes bye matches | integration | `./mvnw test -Dtest=SiteGeneratorServiceTest` | ❌ Wave 0 — same new test method covers this |

### Sampling Rate
- **Per task commit:** `./mvnw test -Dtest=SiteGeneratorServiceTest`
- **Per wave merge:** `./mvnw verify`
- **Phase gate:** `./mvnw verify` (874+ tests green, ≥82% coverage)

### Wave 0 Gaps
- [ ] `givenByeRaceInSeason_whenGenerate_thenCompletesWithoutNPE()` in `SiteGeneratorServiceTest.java` — covers DATA-03

*(All other test infrastructure exists; this is the only missing test method)*

## Security Domain

Not applicable. Phase 35 is a null-safety fix in the site generator service layer — no authentication, session management, access control, input validation boundary, or cryptographic changes. ASVS categories V2–V6 do not apply to this change.

## Sources

### Primary (HIGH confidence)
- Direct code inspection: `SiteGeneratorService.java` L273-302 — all four NPE call sites identified
- Direct code inspection: `SiteGeneratorServiceTest.java` — existing 11 tests, `@BeforeEach` setup, repository fields
- Direct code inspection: `Match.java` — bye flag, nullable awayTeam, constructor
- Direct code inspection: `Race.java` — `getHomeTeam()`, `getAwayTeam()`, `isBye()` delegation methods
- Direct code inspection: `RaceView.java` — constructor signature, no changes needed
- Phase 31 RESEARCH.md decisions D-05, D-06, D-07 — established null safety pattern for DATA-03
- Phase 31 Plan 02 SUMMARY.md — confirmed what was already fixed (RaceFormDataService, ScoringService) and what remains (SiteGeneratorService)
- REQUIREMENTS.md — DATA-03 scope: "Race services handle null home/away teams without NPE (bye matches, unlinked races)"
- `./mvnw test -Dtest=SiteGeneratorServiceTest` — confirmed 11 tests pass, 0 failures (current state)

### Secondary (MEDIUM confidence)
- TESTING.md — integration test patterns, BDD naming, coverage rules
- CLAUDE.md — constraints (82% coverage minimum, TDD, no schema changes, no Flyway)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new libraries, verified by code inspection
- Architecture: HIGH — single private method fix, follows established Phase 31 pattern exactly
- Pitfalls: HIGH — all identified from direct code inspection of the 30-line method and test class

**Research date:** 2026-04-14
**Valid until:** 2026-05-14 (stable — no external dependencies)
