---
phase: 109-walkover-handling
reviewed: 2026-05-30T00:00:00Z
depth: standard
files_reviewed: 23
files_reviewed_list:
  - src/main/java/org/ctc/domain/service/StandingsService.java
  - src/main/java/org/ctc/domain/service/ScoringService.java
  - src/main/java/org/ctc/domain/service/MatchService.java
  - src/main/java/org/ctc/admin/controller/MatchController.java
  - src/main/java/org/ctc/admin/service/LineupGraphicService.java
  - src/main/java/org/ctc/admin/service/MatchResultsGraphicService.java
  - src/main/java/org/ctc/admin/service/ProvisionalScoresGraphicService.java
  - src/main/java/org/ctc/discord/service/DiscordPostService.java
  - src/main/java/org/ctc/domain/model/Match.java
  - src/main/resources/db/migration/V17__add_matches_walkover_team_id.sql
  - src/main/resources/templates/admin/match-form-edit.html
  - src/main/resources/templates/admin/matchday-detail.html
  - src/test/java/db/migration/V17MigrationIT.java
  - src/test/java/org/ctc/TestHelper.java
  - src/test/java/org/ctc/admin/controller/MatchControllerDetailViewModelTest.java
  - src/test/java/org/ctc/admin/controller/MatchControllerTest.java
  - src/test/java/org/ctc/admin/controller/MatchdayControllerTest.java
  - src/test/java/org/ctc/discord/service/DiscordPostServiceProvisionalScoresIT.java
  - src/test/java/org/ctc/domain/model/MatchWalkoverTest.java
  - src/test/java/org/ctc/domain/service/ScoringServiceTest.java
  - src/test/java/org/ctc/domain/service/StandingsServiceTest.java
  - src/test/java/org/ctc/e2e/WalkoverE2ETest.java
findings:
  critical: 0
  warning: 2
  info: 3
  total: 5
status: issues_found
---

# Phase 109: Code Review Report (Delta-2)

**Reviewed:** 2026-05-30
**Depth:** standard
**Files Reviewed:** 23 (delta since 636ddc4f)
**Status:** issues_found

## Summary

This review covers the DELTA of Phase 109 since the first code review (636ddc4f): the
fix-pass for the original 8 findings plus the NEW walkover race-score feature in
`StandingsService`. The original findings are NOT re-reported.

**Core feature assessment — the walkover race-score credit is correct.** I traced
`fullTeamRaceScore(RaceScoring)` against all flagged edge cases:

- **null raceScoring** → guarded, returns `0` (line 427-429). Correct.
- **race-points array shorter than 6** → loop bound `i < racePoints.length && i < WALKOVER_TEAM_POSITIONS`
  sums only available positions (line 432). Correct.
- **empty quali array** → `getQualiPointsArray()` returns `new int[]{}`,
  `Arrays.stream(...).sum()` = 0 (line 435). Correct.
- **symmetry** → winner `addPointsFor(walkoverScore)`, forfeiter `addPointsAgainst(walkoverScore)`;
  both use the identical `walkoverScore` value (lines 358, 364). Symmetric. The test
  fixture confirms 89 = 81 (P1-6: 20+17+14+12+10+8) + 6 (quali 3+2+1) + 2 (FL).
- **no double-counting** → the walkover branch `return`s (line 366) before the normal
  score branch; the normal branch is unreachable for walkover matches. Confirmed.
- **read-time-only design** → `homeScore`/`awayScore` stay null; `ScoringService`
  (both `aggregateMatchScores` and `recomputeMatchScoresFromAllLegs`) early-returns on
  walkover, so no write path can populate them. `merge()` propagates `pointsFor`/`pointsAgainst`/`hasWalkover`
  to alltime standings. Internally consistent.

The fix-pass changes (atomic `updateMatchEdit`, score-clearing, NPE-guarded template,
walkover-aware Discord render gates, V17 FK index, `Match.isWalkoverFor`, full-UUID
scoring names) are all sound. The `@Transactional` self-invocation in `updateMatchEdit`
(WR analysis below) preserves atomicity because the inner calls join the outer
transaction via REQUIRED propagation — the bypassed inner proxies are irrelevant to
correctness here.

Two display-consistency warnings and three info items remain.

## Warnings

### WR-01: Walkover match shows misleading "Open" badge on matchday-detail

**File:** `src/main/resources/templates/admin/matchday-detail.html:74`
**Issue:** The fix-pass now forces `homeScore = null` whenever a walkover is set
(`MatchService.updateWalkover` lines 223-224). The "Open" badge condition is
`legs <= 1 && match.homeScore == null && !match.bye` and does NOT exclude walkover
matches. For a single-leg phase, a walkover match therefore renders BOTH the "w/o"
marker (the intended state) AND an "Open" badge — signalling the match is unplayed/pending
when it is in fact a settled forfeit. Before the fix-pass, a walkover applied to a
previously-scored match kept a (stale) non-null `homeScore` and so suppressed the badge;
the score-clearing change surfaced this latent inconsistency. The score-area block (lines
48-61) was correctly updated to branch on `walkoverTeam != null`, but the badge block on
line 74 was not.
**Fix:** Exclude walkover from the "Open" badge condition:
```html
<span th:if="${matchday.phase.legs <= 1 && match.homeScore == null && !match.bye && match.walkoverTeam == null}"
      class="badge badge-inactive">Open</span>
```

### WR-02: Half-scored matches now render "– : –" instead of the one known score (intended NPE fix, but a display regression)

**File:** `src/main/resources/templates/admin/matchday-detail.html:49-57`
**Issue:** The NPE guard changed every score span's presence condition from the
per-side `homeScore != null` / `awayScore != null` to the combined
`homeScore != null and awayScore != null`. This correctly prevents the OGNL NPE in the
`homeScore > awayScore` comparison when exactly one side is null (verified by the new
`MatchdayControllerTest.givenHalfScoredMatch_...` test). However, the side-effect is that
a match with exactly one known score (e.g. home=50, away=null — a partial aggregation
state) now displays `– : –`, hiding the one value that IS known. This is a behavioural
regression versus the prior per-side rendering, even though it is safe. Whether a
half-scored match is even a reachable state for non-walkover matches should be confirmed;
if it is reachable, the known side should still display.
**Fix:** If half-scored states are reachable, guard each comparison individually rather
than blanking both sides, e.g. render the known value and dim only the unknown side:
```html
<span th:if="${match.homeScore != null}" class="match-score-value"
      th:classappend="${match.awayScore == null ? '' : (match.homeScore > match.awayScore ? 'score-win' : (match.homeScore < match.awayScore ? 'score-loss' : 'score-draw'))}"
      th:text="${match.homeScore}"></span>
<span th:unless="${match.homeScore != null}" class="match-score-value match-score-value--dim">–</span>
```
If half-scored is provably unreachable (always both-or-neither), downgrade to Info and
add a one-line comment documenting the invariant.

## Info

### IN-01: `fullTeamRaceScore` Javadoc asserts a domain semantic that diverges from a contested race

**File:** `src/main/java/org/ctc/domain/service/StandingsService.java:421-437`
**Issue:** The walkover credit models the winner sweeping P1-P6 + all quali + FL. In a
real contested race the winning team's total is the interleave of both teams' drivers, so
this is an idealised maximal sweep, not "the score the team would have scored." The
Javadoc ("the top 6 finishing positions its drivers would sweep") encodes a deliberate
design decision rather than a derivation. This is acceptable per the feature spec, but the
magic constant `WALKOVER_TEAM_POSITIONS = 6` hard-codes a team-size assumption: if a phase
ever runs with a different drivers-per-team count, the walkover credit silently
under/over-counts relative to that phase's real-race team totals. No defect today; flagging
the coupling.
**Fix:** Optional — if drivers-per-team is configurable elsewhere, source the position
count from that config instead of a private constant; otherwise leave as-is.

### IN-02: `TestHelper` full-UUID scoring-name change is correct but worth a one-line rationale

**File:** `src/test/java/org/ctc/TestHelper.java:38`
**Issue:** Suffix widened from a 4-char UUID prefix to the full UUID. The `name` column on
`race_scorings`/`match_scorings` is `VARCHAR(255) NOT NULL UNIQUE`, so `"RS " + 36 chars`
= 39 chars fits, and the full UUID removes the ~65k-combination collision surface a 4-char
prefix exposed under parallel/many-test runs against the shared UNIQUE constraint. Correct
fix; the WHY (UNIQUE-constraint collision avoidance) is non-obvious from the diff alone.
**Fix:** None required. A single-line `// full UUID: avoid UNIQUE name collisions under
parallel test runs` would aid future readers, but per the no-comment-pollution convention
this is optional.

### IN-03: Walkover credit not reflected in `matchday-detail` (read-time-only design is consistent, but invisible to operators)

**File:** `src/main/resources/templates/admin/matchday-detail.html:59-61`
**Issue:** By design the walkover race-score (e.g. 89:0) is computed only at standings
read-time and never written to `homeScore`/`awayScore`; the matchday-detail row shows only
"w/o" with no numeric scoreline. This is internally consistent with the spec, but an
operator viewing the matchday cannot see the points the forfeit actually credited to the
standings — the number only materialises on the standings page. This is a UX observation,
not a correctness defect.
**Fix:** Optional future polish — surface the credited score (or a tooltip) next to the
"w/o" marker. Not required for this phase.

---

_Reviewed: 2026-05-30_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
