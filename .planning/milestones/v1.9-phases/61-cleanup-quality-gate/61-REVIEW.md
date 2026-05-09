---
phase: 61-cleanup-quality-gate
reviewed: 2026-05-01T18:17:37Z
depth: standard
files_reviewed: 39
files_reviewed_list:
  - src/main/java/org/ctc/admin/TestDataService.java
  - src/main/java/org/ctc/admin/controller/PlayoffController.java
  - src/main/java/org/ctc/admin/controller/SeasonController.java
  - src/main/java/org/ctc/dataimport/CsvImportService.java
  - src/main/java/org/ctc/domain/model/Matchday.java
  - src/main/java/org/ctc/domain/model/Playoff.java
  - src/main/java/org/ctc/domain/model/Season.java
  - src/main/java/org/ctc/domain/model/SeasonPhase.java
  - src/main/java/org/ctc/domain/repository/MatchRepository.java
  - src/main/java/org/ctc/domain/repository/MatchdayRepository.java
  - src/main/java/org/ctc/domain/repository/PlayoffRepository.java
  - src/main/java/org/ctc/domain/repository/RaceLineupRepository.java
  - src/main/java/org/ctc/domain/repository/RaceRepository.java
  - src/main/java/org/ctc/domain/repository/RaceResultRepository.java
  - src/main/java/org/ctc/domain/repository/SeasonRepository.java
  - src/main/java/org/ctc/domain/service/MatchService.java
  - src/main/java/org/ctc/domain/service/MatchdayGeneratorService.java
  - src/main/java/org/ctc/domain/service/MatchdayService.java
  - src/main/java/org/ctc/domain/service/PlayoffSeedingService.java
  - src/main/java/org/ctc/domain/service/PlayoffService.java
  - src/main/java/org/ctc/domain/service/RaceCalendarService.java
  - src/main/java/org/ctc/domain/service/RaceFormDataService.java
  - src/main/java/org/ctc/domain/service/RaceService.java
  - src/main/java/org/ctc/domain/service/SeasonManagementService.java
  - src/main/java/org/ctc/domain/service/StandingsService.java
  - src/main/java/org/ctc/domain/service/SwissPairingService.java
  - src/main/java/org/ctc/sitegen/SiteGeneratorService.java
  - src/main/resources/db/migration/V6__cleanup_legacy_season_columns.sql
  - src/main/resources/templates/admin/matchday-detail.html
  - src/main/resources/templates/admin/seasons.html
  - src/main/resources/templates/admin/swiss-rounds.html
  - src/main/resources/templates/site/archive.html
  - src/test/java/db/migration/V6MigrationTest.java
  - src/test/java/db/migration/V4MigrationSmokeIT.java
  - src/test/java/org/ctc/e2e/GroupsSeasonE2ETest.java
  - src/test/java/org/ctc/e2e/LegacyMigratedSeasonE2ETest.java
  - src/test/resources/sql/legacy-season-with-playoff.sql
  - src/test/resources/sql/legacy-season-without-playoff.sql
findings:
  blocker: 1
  warning: 7
  info: 5
  total: 13
status: issues_found
scope_notes: |
  Phase 61 changed 85 files total. The orchestrator narrowed scope to 39 files: all production code +
  V6 migration SQL + new E2E test classes + V6 regression test + 2 new SQL fixtures + adapted
  V4MigrationSmokeIT. The remaining ~46 files are mechanical cascade-migration of existing tests
  (entity-slim follow-ups), already covered by the 1167-test Surefire GREEN gate. Downstream
  reviewers may broaden scope if the listed adapted callsites raise follow-up suspicion in test code.
---

# Phase 61: Code Review Report

**Reviewed:** 2026-05-01T18:17:37Z
**Depth:** standard
**Files Reviewed:** 39
**Status:** issues_found

## Summary

Phase 61 ships the canonical schema cutover (V6 drops 1 M:N table, 2 bridge FK columns, and 8
legacy `seasons` columns) plus all callsite adaptations. The migration itself is well-ordered
(M:N table → named constraints → indexes → bridge columns → legacy columns), uses portable
`IF EXISTS` guards for indexes/constraints, and is exercised by a fresh Surefire-side regression
test (`V6MigrationTest`). The slim entities (Season/Matchday/Playoff/SeasonPhase) align with the
post-V6 schema, and Hibernate `ddl-auto=validate` agreement is asserted by
`V6MigrationTest.givenV6HasRun_whenLoadAllSeasons_thenJpaMappingStillWorks`.

The cascade-migration is largely correct. However, the bridge convenience-getter approach
(`Matchday.getSeason()` → `phase.getSeason()`) intentionally widens the semantics of two
season-scoped repository finders (`findBySeasonIdOrderBySortIndexAsc` + a few peers), which
silently changes behaviour for the small set of seasons that have BOTH a REGULAR phase and a
PLAYOFF phase. The most consequential side effect is in `MatchdayService.createInline` /
`CsvImportService.findOrCreateMatchday`: the next REGULAR-phase matchday gets a sortIndex
poisoned by the playoff matchday's sortIndex (100+), and a duplicate-label check now collides
across phases. There are also a handful of leftover dead-code / stale-comment items that should
be cleaned up before merge.

## Blocker Issues

### CR-01: REGULAR matchday creation poisons sortIndex when a PLAYOFF phase already has matchdays

**Files:**
- `src/main/java/org/ctc/domain/service/MatchdayService.java:167-180`
- `src/main/java/org/ctc/dataimport/CsvImportService.java:445-456`

**Issue:**
`MatchdayRepository.findBySeasonIdOrderBySortIndexAsc` is now `WHERE m.phase.season.id = :seasonId`
(repository line 16), so it returns matchdays from **every** phase of the season — both REGULAR
and PLAYOFF. `MatchdayService.createInline` (line 175-178) computes
```java
int nextSortIndex = existingMatchdays.stream()
    .mapToInt(Matchday::getSortIndex)
    .max().orElse(0) + 1;
```
on that combined list. `PlayoffService.addRaceToMatchup` (line 322) creates PLAYOFF-bound
matchdays with `sortIndex = 100 + roundIndex*10 + legNumber`. So once a playoff has been
auto-created (via `playoffService.createPlayoff` → `addRaceToMatchup`), the next REGULAR-phase
matchday created via `createInline` (or via `CsvImportService.findOrCreateMatchday` line 451-454,
which uses the same finder + `mapToInt(Matchday::getSortIndex).max()` pattern) jumps to
`sortIndex >= 101`. This breaks the `Matchday(@OrderBy("sortIndex ASC"))` invariant on
`SeasonPhase.matchdays`, and reorders templates that rely on natural sortIndex (e.g.
`swiss-rounds.html` line 70 `#numbers.sequence(season.matchdays.size() + 1, totalRounds)` —
combined with `Season.getMatchdays()` which spans phases — will under-count REGULAR rounds when
playoff matchdays are present in `season.phases[*].matchdays`).

A second problem on the same finder: the duplicate-label check
(`createInline` line 169-173) raises `BusinessRuleException` if **any** phase already has a
matchday with that label, blocking a REGULAR-phase "Round 1" create when a PLAYOFF "Round 1"
already exists.

**Fix:**
Either restrict the finder to REGULAR (preferred, since `createInline` and
`findOrCreateMatchday` always create REGULAR-phase matchdays via `findRegularPhase`), or call
the phase-scoped `findByPhaseIdOrderBySortIndexAsc` directly:
```java
// MatchdayService.createInline (replace line 167)
var regular = seasonPhaseService.findRegularPhase(season.getId());
var existingMatchdays = matchdayRepository.findByPhaseIdOrderBySortIndexAsc(regular.getId());
// ... duplicate-label + maxIndex calculation now scoped to REGULAR phase only

// CsvImportService.findOrCreateMatchday (replace line 445 + 451)
var regular = seasonPhaseService.findRegularPhase(season.getId());
return matchdayRepository.findByPhaseIdOrderBySortIndexAsc(regular.getId()).stream()
    .filter(md -> md.getLabel().equals(metadata.matchdayLabel()))
    .findFirst()
    .orElseGet(() -> {
        var maxIndex = matchdayRepository.findByPhaseIdOrderBySortIndexAsc(regular.getId())
            .stream().mapToInt(Matchday::getSortIndex).max().orElse(0);
        return matchdayRepository.save(new Matchday(regular, metadata.matchdayLabel(), maxIndex + 1));
    });
```
Add a regression test that creates a season with a playoff (via `TestDataService.seedPlayoffs`
or the equivalent fixture) then calls `MatchdayService.createInline` and asserts the new
matchday's `sortIndex == lastRegularSortIndex + 1`.

## Warnings

### WR-01: SiteGeneratorService alltime-standings fallback path is dead and would crash if reached

**File:** `src/main/java/org/ctc/sitegen/SiteGeneratorService.java:590-597`

**Issue:**
The comment at lines 590-592 claims "fallback to the legacy seasonId-bridge for pre-Phase-57
seasons without a REGULAR phase row… The bridge handles that internally." Phase 61
explicitly removed that bridge — `StandingsService.calculateStandings(UUID seasonId)` (line
150-154 of StandingsService) now does:
```java
var regular = seasonPhaseService.findRegularPhase(seasonId);
return calculateStandings(regular.getId(), null);
```
and `findRegularPhase` (`SeasonPhaseService` line 78-81) **throws** `EntityNotFoundException`
if the REGULAR phase is absent. So the line 597 fallback `standingsService.calculateStandings(season.getId())`
will throw for the very edge case it claims to handle. The `@SuppressWarnings("deprecation")`
on line 594 is also stale — the called method is not annotated `@Deprecated`.

In practice the path is unreachable today because line 593 already enters the `regularPhaseOpt.isPresent()`
branch for production seasons (and the outer `generate()` loop at line 93 skips REGULAR-less
seasons). But the dead branch is misleading and will explode if a refactor ever changes the
upstream guard.

**Fix:**
Drop the fallback and the `@SuppressWarnings`:
```java
var regularPhaseOpt = seasonPhaseService.findByType(season.getId(), PhaseType.REGULAR);
if (regularPhaseOpt.isEmpty()) continue; // no profile possible without REGULAR phase
var seasonStandings = standingsService.calculateStandings(regularPhaseOpt.get().getId(), null);
```
Update the comment to reflect that REGULAR-less seasons are now skipped (mirroring the
loop-level skip on line 93).

### WR-02: SeasonManagementService.getDetailData is dead code that crashes on legacy seasons

**File:** `src/main/java/org/ctc/domain/service/SeasonManagementService.java:139-148`

**Issue:**
`getDetailData(UUID id)` calls `seasonPhaseService.findRegularPhase(season.getId()).getFormat()`
unconditionally on line 145. `findRegularPhase` throws `EntityNotFoundException` when the
REGULAR phase is absent (e.g., a half-migrated legacy season or a JdbcTemplate-seeded test
fixture that omitted the REGULAR row). However, no production code references
`getDetailData` (verified by grep — zero call sites in `src/main/java`). It is unreachable
dead code that would throw if reactivated.

**Fix:**
Either delete the method (and its `SeasonDetailData` record) outright, or guard the
`findRegularPhase` call with `findByType(...).map(p -> p.getFormat() == SeasonFormat.SWISS).orElse(false)`
so reactivation is safe. Prefer deletion to keep the service surface lean.

### WR-03: V6 migration drops `playoff_seasons` without `IF EXISTS` while every other DDL is guarded

**File:** `src/main/resources/db/migration/V6__cleanup_legacy_season_columns.sql:9`

**Issue:**
Line 9 `DROP TABLE playoff_seasons;` lacks the `IF EXISTS` guard that every other destructive
statement in the same file uses (lines 14-16, 21-22, 29-30 all use `IF EXISTS`). This is purely
inconsistency, not a correctness bug — Flyway tracks migrations by checksum so V6 runs exactly
once on a non-baseline DB and `playoff_seasons` is created in V1 unconditionally. Still, the
inconsistency surprises future maintainers and breaks the
"every-DDL-statement-is-portable-and-idempotent" pattern intentionally established for the rest
of the file.

**Fix:**
```sql
DROP TABLE IF EXISTS playoff_seasons;
```
(H2 2.x and MariaDB 10.7+ both accept this form — already proven by lines 14-22.)

### WR-04: V6 leaves `playoffs.start_date` / `end_date` / `event_duration_minutes` co-existing with the same fields on `season_phases`

**File:** `src/main/resources/db/migration/V6__cleanup_legacy_season_columns.sql` (omission)

**Issue:**
Phase 61's stated objective is "the `playoffs.season_id` bridge column was dropped in V6 (MIGR-06);
the phase association is now the single source of truth" (Playoff.java line 58-59). The
`Playoff` entity still maps `startDate`, `endDate`, `eventDurationMinutes` (lines 35-40), and
those columns live on **both** `playoffs` (V1 line 167-169) and `season_phases` (V3-shape).
V4 backfill (`V4__MigrateSeasonsToPhases.java` line 132-137) copies them from `playoffs` to
the new PLAYOFF SeasonPhase, but neither side is authoritative thereafter:
- `PlayoffService.createPlayoff(6-arg)` line 239-241 writes to `Playoff` only.
- `RaceCalendarService.resolveEventDuration` line 69 reads from `Playoff.eventDurationMinutes`.
- `SeasonPhase.eventDurationMinutes` (SeasonPhase.java line 54-55) is still set by
  `seasonPhaseService.create(...)` and is the line `RaceCalendarService` falls back to (line 75).

When the user edits the PLAYOFF phase's `eventDurationMinutes` via the phase form (Phase 60),
the playoff row's `event_duration_minutes` is NOT updated — the two values silently diverge
and `RaceCalendarService` returns the stale `Playoff` field (because it checks
`playoffMatchup != null` first). This is not a regression introduced by V6 alone, but V6 made
it permanent by removing the season-level bridge that previously absorbed scoring/duration
ambiguity.

**Fix:**
Either (a) add a follow-up migration V7 that drops `playoffs.start_date`, `playoffs.end_date`,
`playoffs.event_duration_minutes` and updates `RaceCalendarService.resolveEventDuration` /
`PlayoffService.createPlayoff(6-arg)` to read from / write to `playoff.getPhase().getEventDurationMinutes()`,
or (b) accept the duplication and add a service-level helper that synchronises both fields on
every Playoff/PlayoffPhase write. Document the chosen direction in `Playoff.java` so future
edits stay coherent. (Option (a) is preferred per the "single source of truth" principle the
phase migration is explicitly chasing.)

### WR-05: Stale `@SuppressWarnings("deprecation")` on a non-deprecated method

**File:** `src/main/java/org/ctc/sitegen/SiteGeneratorService.java:594`

**Issue:**
`@SuppressWarnings("deprecation")` is applied to a `var` declaration (line 594) whose
right-hand side calls `standingsService.calculateStandings(...)`. Neither overload of
`calculateStandings` carries `@Deprecated` (verified: StandingsService lines 47, 105, 151,
161, 170 — no annotations). The suppression is silently a lie and obscures whether the
deprecation that originally motivated it has been resolved.

**Fix:**
Remove the `@SuppressWarnings("deprecation")` annotation. (Also resolve the underlying dead
fallback — see WR-01 — which makes the whole branch redundant.)

### WR-06: RaceCalendarService NPEs on a corrupt playoff race (no matchday or no phase)

**File:** `src/main/java/org/ctc/domain/service/RaceCalendarService.java:67-76`

**Issue:**
After Phase 61, `resolveEventDuration` falls back to `race.getMatchday().getPhase().getEventDurationMinutes()`
on line 75. There is no null guard on `getMatchday()` or `getPhase()`. For non-playoff races
the matchday is required by V1 schema (`races.matchday_id NOT NULL`), but V1 line 196 actually
allows NULL: `matchday_id UUID` (no NOT NULL). For playoff races the entity allows
`matchday=null` (Race entity has no `nullable=false` annotation per inspection — needs
re-check, but the test fixture `legacy-season-with-playoff.sql` confirms playoff legs were
historically created without matchdays). If `matchday` is null OR the matchday's `phase` is
unset for any reason, `resolveEventDuration` NPEs instead of throwing the expected
`IllegalStateException("Event duration not configured...")` (line 49).

**Fix:**
```java
private Integer resolveEventDuration(Race race) {
    if (race.getPlayoffMatchup() != null) {
        var playoffDuration = race.getPlayoffMatchup().getRound().getPlayoff().getEventDurationMinutes();
        if (playoffDuration != null) return playoffDuration;
    }
    // Phase 61 MIGR-06: eventDurationMinutes lives on the SeasonPhase.
    var matchday = race.getMatchday();
    if (matchday == null || matchday.getPhase() == null) return null;
    return matchday.getPhase().getEventDurationMinutes();
}
```

### WR-07: TestDataService.seedPlayoffs computes 2024 standings via inline aggregation instead of StandingsService

**File:** `src/main/java/org/ctc/admin/TestDataService.java:957-985`

**Issue:**
The 2024 PLAYOFFS branch (lines 957-985) hand-rolls a team-score map by iterating
`raceResultRepository.findByRaceMatchdaySeasonId(s2.getId())` and grouping by home/away team
via stream filters. This re-implements `StandingsService.calculateStandings` with a different
algorithm: it sums `pointsTotal` per team across **both** home and away appearances using the
match.homeTeam / awayTeam edges, but the result attribution depends on `RaceResult` rows being
keyed only to the driver — when a single driver swaps teams mid-season (Phase 56 D-04
succession case), the same driver's results land in both teams' totals. The 2023 branch
uses `playoffSeedingService.autoSeedBracket` (the canonical D-15 path); the 2024 branch
should follow suit instead of bypassing the standings service.

This is dev-profile seed code only, so the consequence is dev-mode demo data with
slightly wrong "Top-2" ordering for any season with succession. Still, the inconsistency
violates the D-15 stance ("Top-N from REGULAR-phase standings") and creates a divergent
test harness from production.

**Fix:**
Replace lines 957-1013 with the same `playoffService.createPlayoff` +
`playoffSeedingService.autoSeedBracket` pattern used at lines 938-942 for 2023:
```java
var playoff2024 = playoffService.createPlayoff(s2.getId(), "2024 Playoffs", 2);
playoffSeedingService.autoSeedBracket(playoff2024.getId());
var finalRound = playoff2024.getRounds().getFirst();
finalRound.setBestOfLegs(2);
playoffRoundRepository.save(finalRound);
var playoffMatchday2024 = matchdayRepository.save(
    new Matchday(playoff2024.getPhase(), "2024 Playoffs", 5));
var reloaded2024 = playoffRepository.findById(playoff2024.getId()).orElseThrow();
for (var matchup : reloaded2024.getRounds().getFirst().getMatchups()) {
    createPlayoffRaces(playoffMatchday2024, matchup, s2, raceScoring, 2);
}
```

## Info

### IN-01: GroupsSeasonE2ETest performs repository-level setup despite D-15 mandate, with insufficient explanation

**File:** `src/test/java/org/ctc/e2e/GroupsSeasonE2ETest.java:271-321`

**Issue:**
The test javadoc (lines 56-69) explicitly justifies the deviation ("no UI exists for
group-bound matchday/race generation as of Phase 60 — group-bound matchday creation has no
controller endpoint"), and the comment at line 273-276 is clear. However, this means the
single E2E test (per Phase 61 D-11) is only **partially** UI-driven: ~60% of the flow goes
through Playwright, ~40% (matchday/match/race/lineup persistence) goes through repositories.
The Phase 61 D-15 mandate "UI-Klick-Eintragung für Race-Results" is honoured for results
specifically, but the broader "GROUPS-Saison workflow end-to-end" framing in the class
comment slightly oversells the test. This is not a correctness defect — the assertions still
exercise post-V6 entity wiring — but a future reader scanning for evidence of full UI
coverage will be misled.

**Fix:**
Either strengthen the comment to "exercises the workflow end-to-end with repository-level
setup for the unimplemented group-bound matchday UI" (more honest framing), or file a
follow-up issue tracking the missing
`/admin/season-phases/{phaseId}/groups/{groupId}/matchdays/generate` endpoint so the
deviation can be retired.

### IN-02: V6MigrationTest is a `@SpringBootTest` but is named `*Test` — possibly subtle Surefire/Failsafe split issue

**File:** `src/test/java/db/migration/V6MigrationTest.java:29`

**Issue:**
The class comment (lines 24-26) explicitly states "Class suffix is `Test` (Surefire) per Phase 61
D-09 — runs in the standard `./mvnw verify` gate, not the `-Pe2e` Failsafe profile." The
intent is clear and Surefire defaults pick up `*Test.java`. However, the test is annotated
`@SpringBootTest` (line 27) which loads the full Spring context (~2-5s) per test method. Four
test methods in this class run, so total cold-startup overhead is amortised, but Surefire
parallelism is the concern: if Surefire is configured with `forkCount > 1`, this test
re-bootstraps the full context per fork, which is wasteful for a pure-INFORMATION_SCHEMA
assertion. Compare with `V4MigrationSmokeIT` (`*IT.java`, Failsafe) which uses the same
`@SpringBootTest` for parallel reasons.

**Fix:**
Either accept the bootstrapping cost (current tests pass, gate is GREEN) or refactor the four
methods to share a `@SpringBootTest`-loaded `JdbcTemplate` via a common
`AbstractV6MigrationAssertion` base with `@TestInstance(Lifecycle.PER_CLASS)` so the four
INFORMATION_SCHEMA queries reuse one context. Pure optimisation — non-blocking.

### IN-03: SeasonRepository.findBySeasonTeamsTeamId and findByYear lack documentation of new convenience-getter semantics

**File:** `src/main/java/org/ctc/domain/repository/SeasonRepository.java:14-21`

**Issue:**
The repository comment at lines 14-15 documents that `@EntityGraph(...raceScoring...matchScoring...)`
is no longer valid post-V6, but the three repository methods themselves carry no documentation
about how they interact with the new phase-derived season identity. `findBySeasonTeamsTeamId`,
`findByYearAndNumber`, `findByYear` all return `Season`, which now exposes scoring only
indirectly via `season.getPhases().get(...).getRaceScoring()` (lazy under OSIV). Future
callers may see a `Season` and reach for `season.getRaceScoring()` (which no longer exists),
hit a compile error, and need to grep this comment to understand why.

**Fix:**
Add a short javadoc on each method noting the new phase indirection:
```java
/**
 * Returns seasons containing the given team. Note: scoring/format/dates are no longer accessible
 * via Season directly (Phase 61 MIGR-06) — fetch via {@code season.getPhases().stream()...}
 * or call {@code seasonPhaseService.findRegularPhase(season.getId())}.
 */
List<Season> findBySeasonTeamsTeamId(UUID teamId);
```

### IN-04: matchday-detail.html template still has inline styles (pre-existing, but in scope)

**File:** `src/main/resources/templates/admin/matchday-detail.html:42, 65, 72-83`

**Issue:**
Several `th:styleappend` and inline-style attributes remain (e.g. line 42
`th:styleappend="${match.bye ? 'opacity:0.6;' : ''}"`, line 72 `th:styleappend="${!iter.last ?
'border-bottom:1px solid var(--border);' : ''}"`). CLAUDE.md "No Inline Styles on Buttons" is
strictly limited to `.btn` elements; these targets are `<div>` rows, so the rule does not
literally apply. Still, the broader project preference (per `.planning/codebase/CONVENTIONS.md`
and CLAUDE.md "Code Quality") is CSS classes from `admin.css`. Phase 61 did not introduce these
styles; they predate the cleanup but were touched as part of the cascade-migration.

**Fix:**
Move the conditional styles to CSS classes (`.match-bye`, `.leg-row--bordered` modifier) and
flip the logic to `th:classappend`. Non-blocking; can be deferred to a separate template-cleanup
phase.

### IN-05: Comment in StandingsService.calculateBuchholzScoresForPhase contradicts implementation

**File:** `src/main/java/org/ctc/domain/service/StandingsService.java:240-245`

**Issue:**
The javadoc (lines 234-239) says "For LEAGUE phases or per-group GROUPS phases, delegates to
the season-level Buchholz using the phase's parent season". The implementation (lines 240-245)
unconditionally delegates to `calculateBuchholzScores(phase.getSeason().getId())` regardless
of the `groupId` argument. The `groupId` parameter is therefore unused and does nothing — yet
it's required by the public method signature on line 240. This is a stale-after-refactor smell:
either the per-group Buchholz computation was planned but never landed, or the parameter
should be removed.

**Fix:**
Either implement per-group scoping (filter the `raceRepository` query by group's matchdays
before the points-of-opponents sum) or drop the unused parameter from
`calculateBuchholzScoresForPhase` and update the single caller (`calculateStandingsWithBuchholz`
line 114 passes `groupId` here).

---

_Reviewed: 2026-05-01T18:17:37Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
