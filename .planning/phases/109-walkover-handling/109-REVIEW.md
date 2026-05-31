---
phase: 109-walkover-handling
reviewed: 2026-05-30T18:35:05Z
depth: standard
files_reviewed: 23
files_reviewed_list:
  - src/main/java/org/ctc/admin/controller/MatchController.java
  - src/main/java/org/ctc/admin/dto/MatchForm.java
  - src/main/java/org/ctc/admin/service/LineupGraphicService.java
  - src/main/java/org/ctc/admin/service/MatchResultsGraphicService.java
  - src/main/java/org/ctc/admin/service/ProvisionalScoresGraphicService.java
  - src/main/java/org/ctc/domain/model/Match.java
  - src/main/java/org/ctc/domain/service/MatchService.java
  - src/main/java/org/ctc/domain/service/ScoringService.java
  - src/main/java/org/ctc/domain/service/StandingsService.java
  - src/main/resources/db/migration/V17__add_matches_walkover_team_id.sql
  - src/main/resources/static/admin/css/admin.css
  - src/main/resources/templates/admin/lineup-render.html
  - src/main/resources/templates/admin/match-form-edit.html
  - src/main/resources/templates/admin/match-results-render.html
  - src/main/resources/templates/admin/matchday-detail.html
  - src/main/resources/templates/admin/provisional-scores-render.html
  - src/main/resources/templates/site/standings.html
  - src/test/java/db/migration/V17MigrationIT.java
  - src/test/java/org/ctc/admin/controller/MatchControllerTest.java
  - src/test/java/org/ctc/domain/service/ScoringServiceTest.java
  - src/test/java/org/ctc/domain/service/StandingsServiceTest.java
  - src/test/java/org/ctc/e2e/WalkoverE2ETest.java
findings:
  critical: 0
  warning: 4
  info: 4
  total: 8
status: resolved
resolved: 2026-05-30
---

> **Resolution (2026-05-30):** All 8 findings fixed on `gsd/v1.15-ci-and-race-defaults`, each with a regression test where applicable.
> WR-01 `6c0d4ec6` · WR-02 `a638bc0d` · WR-03 `6d5bf059` · WR-04 `3c4df266` · IN-01 `9c97ecfa` · IN-03 `4bd535f6` · IN-04 `0fe2650b` · IN-02 `9488c290`.

# Phase 109: Code Review Report

**Reviewed:** 2026-05-30T18:35:05Z
**Depth:** standard
**Files Reviewed:** 23
**Status:** issues_found

## Summary

Phase 109 adds walkover handling: a `walkover_team_id` nullable FK (V17), a `MatchForm.walkoverTeamId` field, `MatchService.updateWalkover` with up-front validation, a standings read-time win/loss attribution branch in `StandingsService.processMatch` (mirroring `Match.bye`), `hasWalkover` "(w/o)" badges in standings, and "w/o" markers in three Playwright graphic templates plus the matchday-detail admin view.

The core attribution logic in `StandingsService.processMatch` is correct and well-tested for both home-forfeit and away-forfeit, including succession mapping and the precedence-over-scores case. The validation ordering in `updateWalkover` is sound (all checks before the single `save`, so no partial write). Mass-assignment is avoided (Form DTO + path-authoritative `id`). UUID comparisons use `.equals` throughout.

The defects below are correctness/consistency gaps rather than security holes. The most significant is an asymmetry: `ScoringService.aggregateMatchScores` does NOT skip walkover matches (unlike its sibling `recomputeMatchScoresFromAllLegs`), so a walkover match that later receives race results can have non-null `homeScore`/`awayScore` written and stored. Those stale scores are then rendered in the admin matchday-detail view next to the "w/o" label, producing self-contradictory UI. Standings themselves stay correct because the walkover branch short-circuits before the score branch.

## Warnings

### WR-01: `aggregateMatchScores` writes scores onto walkover matches; `recomputeMatchScoresFromAllLegs` does not — asymmetric guard

**File:** `src/main/java/org/ctc/domain/service/ScoringService.java:62-66` vs `129-164`
**Issue:** `recomputeMatchScoresFromAllLegs` deliberately early-returns for walkover matches:
```java
if (race.getMatch() != null && race.getMatch().getWalkoverTeam() != null) {
    return;
}
```
But `aggregateMatchScores` has no equivalent walkover guard. If race results are entered on a walkover match's race — via `RaceService.saveResults` (line 270, results non-empty) or `RaceService.quickScore` (line 288) — `aggregateMatchScores` computes and sets `match.setHomeScore(...)` / `match.setAwayScore(...)` on a forfeited match. The result is a walkover match persisting non-null scores. Standings remain correct (the walkover branch in `processMatch` returns before the score branch), but `matchday-detail.html` (lines 48-58) renders those scores directly alongside the "w/o" marker, and `MatchResultsGraphicService` would render a real score for a forfeit. The two sibling methods must treat walkover identically.
**Fix:** Add the same guard at the top of `aggregateMatchScores`:
```java
@Transactional
public void aggregateMatchScores(Race race) {
    if (race.getResults().isEmpty()) {
        return;
    }
    if (race.isBye()) {
        return;
    }
    if (race.getMatch() != null && race.getMatch().getWalkoverTeam() != null) {
        return; // walkover scores are awarded at standings read-time; never aggregate legs
    }
    ...
}
```

### WR-02: Setting a walkover does not clear pre-existing `homeScore`/`awayScore`

**File:** `src/main/java/org/ctc/domain/service/MatchService.java:184-209`
**Issue:** `updateWalkover` sets `match.setWalkoverTeam(team)` but never nulls out `homeScore`/`awayScore`. If a match already had results aggregated (scores non-null) and is *then* marked as a walkover, the stale scores remain in the DB. `matchday-detail.html` displays `match.homeScore`/`match.awayScore` unconditionally for non-bye matches (lines 48-58), so the admin sees a concrete scoreline (e.g. `70 : 46`) next to the "w/o" badge — contradicting the forfeit semantics. This is the persistence-time counterpart to WR-01.
**Fix:** When a walkover is set, clear the derived scores so the match presents consistently (scores are re-derivable from legs if the walkover is later cleared, via `recomputeMatchScoresFromAllLegs`):
```java
match.setWalkoverTeam(team);
match.setHomeScore(null);
match.setAwayScore(null);
matchRepository.save(match);
```
Conversely, on the clear path (`walkoverTeamId == null`), consider re-deriving scores from legs so a mistakenly-set walkover can be fully reverted. At minimum the matchday-detail template should suppress the score area when `match.walkoverTeam != null` (presence check only, no logic), mirroring the existing `th:unless="${match.bye}"` pattern.

### WR-03: `matchday-detail.html` score comparison NPEs when exactly one of home/away score is null on a walkover (or partially-scored) match

**File:** `src/main/resources/templates/admin/matchday-detail.html:49-57`
**Issue:** The score cell renders with
```html
<span th:if="${match.homeScore != null}"
      th:classappend="${match.homeScore > match.awayScore ? ... }" ...>
```
The `th:if` only guards `homeScore != null`; the comparison then dereferences `match.awayScore`. If `homeScore` is non-null while `awayScore` is null (reachable for a walkover match whose scores were partially written via WR-01, or any half-scored match), `Integer > null` throws inside OGNL and the whole matchday page fails to render. The away cell (lines 54-56) has the mirror-image hazard. While the underlying template predates this phase, Phase 109 newly makes the "one score null on a non-bye match" state reachable for walkover matches, so it falls in scope.
**Fix:** Guard both operands, or compute the win/loss/draw class in the service and inject it (per the project's "no logic in templates" rule). Minimal template-level guard:
```html
<span th:if="${match.homeScore != null and match.awayScore != null}"
      th:classappend="${match.homeScore > match.awayScore ? 'score-win' : (match.homeScore < match.awayScore ? 'score-loss' : 'score-draw')}"
      th:text="${match.homeScore}"></span>
<span th:if="${match.homeScore != null and match.awayScore == null}" class="match-score-value" th:text="${match.homeScore}"></span>
```
Preferred: suppress the entire score area for walkover matches (see WR-02 fix), which removes the hazard for the walkover case entirely.

### WR-04: `ProvisionalScoresGraphicService` throws unchecked `IllegalStateException` for walkover after the post-gate already passed

**File:** `src/main/java/org/ctc/admin/service/ProvisionalScoresGraphicService.java:41-43`; gate at `src/main/java/org/ctc/discord/service/DiscordPostService.java:690-697`
**Issue:** `generateProvisional` rejects walkover matches with `throw new IllegalStateException("Walkover match has no provisional scores")`. But the caller `DiscordPostService.postProvisionalScores` gates only on `matchHasProvisionalData(match)`, which checks `anyMatch(r -> !r.getResults().isEmpty())` and has no walkover awareness. In the normal walkover flow (no results) the gate returns false and the post is blocked, so the throw is unreachable. However, combined with WR-01 (a walkover match *can* acquire race results), the gate would pass and `generateProvisional` would throw `IllegalStateException` — an unchecked exception that is NOT caught by `postProvisionalScores` (which only catches `IOException`) nor by the controller's `post-provisional` handler (which catches `BusinessRuleException` / `DiscordApiException`). Result: a 500 / generic error page instead of a clean message, and a half-built attachment list. The guard should be a `BusinessRuleException` and ideally evaluated in the gate, not deep in the renderer.
**Fix:** Make `matchHasProvisionalData` walkover-aware (return false when `match.getWalkoverTeam() != null`) so the post is cleanly blocked, and change the in-renderer guard to throw a caught type for defense-in-depth:
```java
// DiscordPostService.matchHasProvisionalData
return match.getWalkoverTeam() == null
        && !races.isEmpty()
        && races.stream().anyMatch(r -> !r.getResults().isEmpty());
```
Mirror the same walkover gate in `matchCanRenderResults` (line 166) for the match-results post path, which currently has the same blind spot.

## Info

### IN-01: New FK `walkover_team_id` has no covering index, breaking the V2 FK-index convention

**File:** `src/main/resources/db/migration/V17__add_matches_walkover_team_id.sql:1-2`
**Issue:** `V2__add_fk_indexes.sql` establishes the convention that every FK column on `matches` is indexed (`idx_matches_matchday_id`, `idx_matches_home_team_id`, `idx_matches_away_team_id`). The new `walkover_team_id` FK adds none. Beyond query performance (out of v1 scope), an unindexed child FK forces a full scan of `matches` on every `DELETE`/`UPDATE` of a parent `teams` row to enforce the constraint, which can escalate locking. Keeping the convention is the safer default.
**Fix:** Add to V17 (H2 + MariaDB compatible, matching the V2 style):
```sql
CREATE INDEX IF NOT EXISTS idx_matches_walkover_team_id ON matches(walkover_team_id);
```

### IN-02: Walkover persistence and Discord-field update are two separate transactions in `saveEdit`

**File:** `src/main/java/org/ctc/admin/controller/MatchController.java:127-135`; `MatchService.updateWalkover` / `updateDiscordFields`
**Issue:** `saveEdit` calls `matchService.updateWalkover(...)` (its own `@Transactional`) and then `matchService.updateDiscordFields(...)` (a second `@Transactional`). If the first commits and the second fails, the walkover change is persisted while the Discord/teaser fields are not — a partial save from the operator's perspective, even though each method is individually atomic. Low blast radius because the second call only updates string fields, but the two-phase save is worth noting.
**Fix:** Either wrap both in a single service method/transaction, or accept the split and document it. No action required if the split is intentional.

### IN-03: Duplicated walkover-flag computation across three graphic services

**File:** `LineupGraphicService.java:53-55`, `MatchResultsGraphicService.java:75-77`, `ProvisionalScoresGraphicService.java:124-126`
**Issue:** The exact `homeIsWalkover` / `awayIsWalkover` derivation
```java
boolean homeIsWalkover = walkoverTeam != null && walkoverTeam.getId().equals(homeTeam.getId());
boolean awayIsWalkover = walkoverTeam != null && !homeIsWalkover;
```
is copy-pasted verbatim in three services. The pattern is correct (and correctly computes the boolean in-service rather than in the no-Spring-context template), but the duplication invites drift. Consider a small shared helper on `Match` (e.g. `isHomeWalkover()` / `isAwayWalkover()`) or on `AbstractGraphicService`.
**Fix:** Extract to one location, e.g. on the `Match` entity:
```java
public boolean isWalkoverFor(Team team) {
    return walkoverTeam != null && team != null && walkoverTeam.getId().equals(team.getId());
}
```
Note the `awayIsWalkover = walkoverTeam != null && !homeIsWalkover` shorthand is only sound because `updateWalkover` guarantees the walkover team is exactly home or away; a shared helper that compares against the actual away team is more robust against future entry paths.

### IN-04: Mixed-language UI string in walkover dropdown

**File:** `src/main/resources/templates/admin/match-form-edit.html:56`
**Issue:** The walkover select default option reads `Kein Walkover` (German) while the rest of the admin UI — labels, buttons, the surrounding form ("Walkover", "Stream Link", "Save", "Cancel") — is English. CLAUDE.md mandates English for all UI texts (communication is German, but UI/code/comments are English).
**Fix:** `<option value="">No walkover</option>`.

---

_Reviewed: 2026-05-30T18:35:05Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
