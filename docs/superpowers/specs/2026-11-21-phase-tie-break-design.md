# Phase tie-break by race winner

## Intent

From the 2026 playoffs on, the league requires a winner in every playoff and placement match. Announcement of 2026-10-19: "in the event of an (unlikely) draw, the victory will be awarded to the team whose driver wins the race." Earlier seasons allowed draws and must keep their results. Today a tied playoff matchup blocks `PlayoffService.determineWinner` until an admin sets the winner manually with a reason, and a tied regular `Match` counts as a draw in every view.

## Goals

- Each `SeasonPhase` gets an opt-in setting "tie-break by race winner", default off. Existing phases stay off.
- With the setting on, a match whose official totals are equal is won by the team of the race winner of the first leg with driver results.
- The decision is stored when scores are aggregated, so standings, views, graphics and `/stats` read one stored value.
- Playoff matchups in such a phase are decided automatically by `determineWinner` instead of requiring a manual decision.

## Non-goals

- No other tie-break criteria (head-to-head, qualifying, fastest lap).
- No retroactive change to phases that keep the setting off.
- No automatic regeneration or publication of graphics, site or Discord posts.

## Anti-goals

- A tied match in an enabled phase that still shows or counts as a draw somewhere.
- Views that recompute the tie-break themselves instead of reading the stored decision.

## Rule

1. Applies only when the owning phase has the setting on and both official totals (after team penalties) are equal and non-null.
2. "Match" means a regular `Match` or a `PlayoffMatchup`. Playoff legs are `Race` rows attached directly to the matchup (`Race.playoffMatchup`), not `Match` rows, so the tie is always the sum over all legs of the pairing, never a single leg.
3. Leg order is the existing `Match.races` order: `dateTime ASC NULLS LAST`, then race ID for equal or missing times. Playoff matchups use the same order.
4. The deciding leg is the first leg in that order with driver results.
5. The race winner is the result at classified position 1 according to `RaceClassification`. A disqualified driver is classified behind everyone else, so the next driver wins.
6. The winning team is the side that driver scores for, using the same attribution as the team totals (RaceLineup first, parent/subteam rules, guests).
7. Walkovers and byes are never ties and are untouched.
8. A whole-match quick score with equal totals in an enabled phase is always rejected before mutation with "Enter driver results for at least one leg to break the tie" (UI text is English). New tied matches without driver results cannot arise.
9. A tie-break win counts exactly like a points win: the phase's win/loss match points, one win and one loss. Points for and against keep the equal totals. Buchholz and the points/difference ordering consume these win/loss records unchanged. (Builder call, follows "the victory will be awarded".)

## Approaches

- A, derive on read: no new field, one shared rule evaluated wherever scores are compared. Rejected. Every missed comparison keeps showing a draw, and the project forbids recomputing outcomes outside aggregation.
- **B, store on aggregation (chosen):** aggregation writes the tie-break winner on the match. All readers use the stored value. Costs a migration, a backup schema bump, restore preflight and an audit check, but keeps one source of truth.

## Design

### Data

- New migration `V50__phase_tie_break.sql`:
  - `season_phases.tie_break_race_winner BOOLEAN NOT NULL DEFAULT FALSE`
  - `matches.tie_break_winner_id` nullable FK to `teams`
  - `playoff_matchups.decided_by_tie_break BOOLEAN NOT NULL DEFAULT FALSE`
- `PlayoffMatchup` keeps its `winner` plus `decisionReason`/history; `decidedByTieBreak` marks that the decision came from the tie-break.
- The deciding leg is not stored: a FK from a pairing to its own race would form a restore cycle (races reference their pairing). Graphics find it with the same `TieBreakRule` over the stored legs.
- Invariant for `Match.tieBreakWinner`: non-null only if the phase setting is on, scores are equal and non-null, the match is neither bye nor walkover, and the team is the home or away team.

### Aggregation

- `ScoringService.recomputeMatchScores` sets or clears `tieBreakWinner` after writing totals, under the existing score-write locks.
- The setting is locked once the phase has any scored pairing: a `Match` with scores or a walkover, a `PlayoffMatchup` with scores or a winner, or any leg with driver results. Byes do not count. Changing it then is rejected under the owning-season lock, like the existing rule that a scored phase's race-scoring reference cannot change. Because no scored pairing exists while the setting changes, no re-aggregation or clearing is needed.
- Historical seasons have not been imported yet. Their phases keep the default off. Whoever imports them sets the flag before importing results if a phase used the rule.
- A quick score sets the totals without results. In an enabled phase, `RaceService.quickScore` rejects equal totals before mutation.

### Playoffs

- `PlayoffService.determineWinner`: on equal totals with the setting on, it determines the race winner per the rule and decides the matchup with the system reason "Tie-break: race winner of leg N" and the existing decision history. Without the setting, or without any leg with driver results, the existing tie rejection stays.
- A decided matchup is already protected: `PlayoffDecisionGuard` rejects result changes until the matchup is reopened, and `determineWinner` runs again after reopening. No silent divergence can arise.
- `setWinnerManually` remains available. A tie-break winner counts as the points leader, so no reason is required for that team.

### Readers

Every place that derives win/draw/loss from scores reads the stored outcome instead. A shared method on `Match` (for example `outcomeFor(team)` or `winner()`) returns the score winner, else `tieBreakWinner`, else draw. Known places:

- `StandingsService` (regular and alltime aggregation, Buchholz inputs)
- `TeamStatsService` and `/stats` embeds, `CareerProfilePageGenerator`
- Admin `matchday-detail.html` score classes, standings graphics
- Public match cards and matchday pages

Views mark a tie-break win as such (for example "won on tie-break") next to the equal score. The builder must grep for every comparison of home/away scores and route it through the shared method.

### Results graphics

- A single-leg pairing: its race results graphic marks the tie-break winner and shows the badge "TIE-BREAK" on that side. No reason line.
- Several legs: only the match results graphic shows the tie-break; the individual leg graphics and race views keep their own leg result.
- New template variables `homeIsTieBreakWinner` and `awayIsTieBreakWinner`, documented in the template editor. `homeIsWinner`/`awayIsWinner` follow the stored outcome, so custom templates get the right winner without changes.
- Regeneration stays explicit.

### Admin UI

- Season phase form: a labeled checkbox "Tie-break by race winner" with a one-line explanation, bound through `SeasonPhaseForm`, saved by the phase service.

### Backup, restore and audit

- Backup schema 28 exports the phase flag and `Match.tieBreakWinner` as a UUID, eagerly fetched like `walkoverTeam`.
- Schemas 1 to 27 restore with the flag false and no tie-break winner. Preflight rejects these fields in older schemas.
- Schema 28 preflight validates the invariant above before wipe, without rescoring, including that `tieBreakWinner` references the match's own home or away team present in the archive. (Current schema verified as 27.)
- Restore keeps the stored value and does not reaggregate.
- The read-only Data Audit reports matches in enabled phases with equal totals and no tie-break winner, and tie-break winners that break the invariant or differ from the rule.

### Tests

- Unit tests for the rule: first leg decides, fallback to the next leg with results, DSQ of the race winner, guest/subteam attribution, penalties creating the tie.
- Integration tests for aggregation, phase toggle re-aggregation, quick score rejection, `determineWinner` auto-decision and standings counting a win/loss.
- Backup round-trip and schema 27 restore. Audit fixture in `DataAuditServiceIT`.

## Left to the builder

- Exact name and shape of the shared outcome method and the view label wording.
- Whether `PlayoffBracketViewService` previews the tie-break winner before the matchup is decided.
- The complete list of reader call sites, found by grep.
