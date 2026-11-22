# Phase tie-break by race winner Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An opt-in per-phase setting awards a tied Match or PlayoffMatchup to the team whose driver wins the first leg with driver results.

**Architecture:** A pure rule class (`TieBreakRule`) picks the winning side from ordered legs. `ScoringService` stores the result on `Match.tieBreakWinner` during aggregation; `PlayoffService.determineWinner` uses the same rule. All readers ask `Match.outcome()` instead of comparing scores. Phase flag and match field travel through backup schema 28 with preflight and a read-only audit check.

**Tech Stack:** Java 25, Spring Boot 4, JPA/Hibernate, Flyway (H2 + MariaDB), Thymeleaf, JUnit 5/Mockito.

**Spec:** `docs/superpowers/specs/2026-11-21-phase-tie-break-design.md`

## Global Constraints

- Branch `feature/phase-tie-break`. No new migrations other than `V50__phase_tie_break.sql`; never edit V1–V49.
- `season_phases.tie_break_race_winner BOOLEAN NOT NULL DEFAULT FALSE`; `matches.tie_break_winner_id` nullable FK to `teams(id)`; `matches.tie_break_race_id` and `playoff_matchups.tie_break_race_id` nullable FKs to `races(id)`.
- `BackupSchema.SCHEMA_VERSION` 27 → 28; add 28 to `BackupImportService.SUPPORTED_SCHEMA_VERSIONS`.
- Leg order: `dateTime ASC NULLS LAST`, then race ID.
- Race winner: classified position 1 via `RaceClassification.position(result, results)`.
- Quick-score rejection text (exact): `Enter driver results for at least one leg to break the tie`.
- Lock rejection text (exact): `This phase has scored pairings. Keep its tie-break setting and use a new phase for a different rule.`
- Playoff decision reason (exact): `Tie-break: race winner of leg N` (N = 1-based position in leg order).
- Tie-break win counts exactly as a points win (phase win/loss match points); points for/against stay equal.
- UI/code/comments in English; no comments beyond one-line WHY; tests use given/when/then names; every `*IT` gets `@Tag("integration")`.
- Local checks only via `./scripts/test-feedback.py unit <Test>` (Maven via Docker JDK per memory); full verification in CI.

## Review Focus

1. Race winner attributed to the away side when the home team's lineup holds a guest or subteam driver: winner must follow `isDriverInTeam` attribution, not `Driver`'s season team. Test in Task 1.
2. Leg 1 without `dateTime` but leg 2 with one: leg 2 is "first" (NULLS LAST). Test in Task 1.
3. Tied quick score on a single leg of a multi-leg Match where the other leg has driver results: still rejected (rule 8 says always). Test in Task 3.
4. Toggling the flag on an empty phase with only byes: allowed. Test in Task 4.
5. Team penalty producing the tie while the race winner's team is penalized: tie-break still awards that team. Test in Task 2.

---

### Task 1: Tie-break rule

**Files:**
- Create: `src/main/java/org/ctc/domain/model/TieBreakRule.java`
- Test: `src/test/java/org/ctc/domain/model/TieBreakRuleTest.java`

**Interfaces:**
- Produces: `enum TieBreakRule.Side { HOME, AWAY }`; `record TieBreakRule.Decision(Side side, int legNumber)`; `static Optional<Decision> TieBreakRule.decide(List<Race> legs, Predicate<RaceResult> scoresForHome)`. `legs` in any order; the method sorts by leg order. `legNumber` is 1-based in leg order. Empty when no leg has results.

- [ ] **Step 1: Write failing tests**: `givenFirstLegWithResults_whenDecide_thenWinnerOfFirstLegDecides`, `givenFirstLegWithoutResults_whenDecide_thenNextLegWithResultsDecides` (legNumber 2), `givenDisqualifiedP1_whenDecide_thenNextClassifiedDriverDecides`, `givenLegWithoutDateTime_whenDecide_thenSortedLast`, `givenEqualDateTimes_whenDecide_thenRaceIdBreaksOrder`, `givenNoResults_whenDecide_thenEmpty`, `givenGuestOnHomeSide_whenDecide_thenPredicateAttributionWins` (predicate returns true for the guest's result → HOME).
- [ ] **Step 2: Run** `./scripts/test-feedback.py unit TieBreakRuleTest` → FAIL (class missing).
- [ ] **Step 3: Implement** `TieBreakRule` as a final utility class; winner = result with minimal `RaceClassification.position(r, leg.getResults())`.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(scoring): add race-winner tie-break rule`.

### Task 2: Schema, entity fields, aggregation and outcome

**Files:**
- Create: `src/main/resources/db/migration/V50__phase_tie_break.sql`, `src/main/java/org/ctc/domain/model/MatchOutcome.java`
- Modify: `SeasonPhase.java` (field `tieBreakRaceWinner`), `Match.java` (`@ManyToOne(fetch = LAZY) tieBreakWinner`, `outcome()`), `ScoringService.java:85-124`
- Test: `src/test/java/org/ctc/db/migration/V50PhaseTieBreakMigrationIT.java`, `ScoringServiceTest.java`, `src/test/java/org/ctc/domain/model/MatchOutcomeTest.java`

**Interfaces:**
- Consumes: `TieBreakRule.decide`.
- Produces: `SeasonPhase.isTieBreakRaceWinner()`/`setTieBreakRaceWinner(boolean)`; `Match.getTieBreakWinner()`/`setTieBreakWinner(Team)`, `Match.getTieBreakRace()`/`setTieBreakRace(Race)`, `PlayoffMatchup.getTieBreakRace()`/`setTieBreakRace(Race)`; `record MatchOutcome(Team winner, Team loser, boolean tieBreak)` with `static MatchOutcome draw()`, `boolean isDraw()`, `boolean isWinner(Team)`; `Match.outcome()` returns null when unplayed, computed from official scores, walkover, then `tieBreakWinner`. `ScoringService.tieBreakDecision(List<Race> legs, UUID homeTeamId): Optional<TieBreakRule.Decision>` (public, reuses the lineup attribution of `sumLegs`).

- [ ] **Step 1: Failing tests**: migration IT asserts both columns exist, default false, FK rejects unknown team (H2; mirror `V49CarImageCutoutMigrationIT`, plus a MariaDB variant like `V49CarImageCutoutMigrationMariaDbIT`). `MatchOutcomeTest`: home win, away win, walkover, draw, tie-break win (`tieBreak()==true`), unplayed → null. `ScoringServiceTest`: `givenTieBreakPhaseAndTiedLegs_whenRecompute_thenTieBreakWinnerStored`, `givenPhaseWithoutTieBreak_whenRecompute_thenNoTieBreakWinner`, `givenScoresNoLongerTied_whenRecompute_thenTieBreakWinnerCleared`, `givenPenaltyCausesTie_whenRecompute_thenRaceWinnerTeamStillWins`.
- [ ] **Step 2: Run** the three tests → FAIL.
- [ ] **Step 3: Implement** migration (H2 + MariaDB compatible `ALTER TABLE`s), fields, `MatchOutcome`, `Match.outcome()`; in `recomputeMatchScores` set `tieBreakWinner` and `tieBreakRace` only when phase flag on and totals equal non-null, else null. `recomputePlayoffMatchupScores` unchanged. Update `TestDataService`/`DevDataSeeder` only if they fail to compile.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(scoring): store tie-break winner on tied matches`.

### Task 3: Quick-score rejection and playoff auto-decision

**Files:**
- Modify: `RaceService.java:367-390`, `PlayoffService.java:160-225`
- Test: `RaceServiceTest.java`, `PlayoffServiceTest.java`

**Interfaces:**
- Consumes: `SeasonPhase.isTieBreakRaceWinner()`, `ScoringService.tieBreakDecision`.

- [ ] **Step 1: Failing tests**: `givenTieBreakPhase_whenTiedQuickScore_thenRejectedBeforeMutation` (scores unchanged, message exact), `givenTieBreakPhaseAndOtherLegWithResults_whenTiedQuickScore_thenRejected`, `givenPhaseWithoutTieBreak_whenTiedQuickScore_thenSaved`; `givenTieBreakPhaseAndTie_whenDetermineWinner_thenRaceWinnerDecidedWithReason` (reason `Tie-break: race winner of leg 1`, `tieBreakRace` = deciding leg, advancement as usual), `givenTieBreakPhaseWithoutAnyDriverResults_whenDetermineWinner_thenExistingTieRejection`, `givenPhaseWithoutTieBreak_whenTie_thenExistingTieRejection`, `givenTieBreakWinner_whenSetWinnerManuallyWithoutReason_thenAccepted`.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement**: `quickScore` throws `BusinessRuleException` before any setter when phase flag on and `homeScore == awayScore`. `determineWinner` and `setWinnerManually` treat the tie-break side as points leader (manual choice of that side also sets `tieBreakRace`); `reopen` clears `tieBreakRace`; phase via `matchup.getRound().getPlayoff().getPhase()`.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(playoffs): decide tied matchups by race winner`.

### Task 4: Phase setting with lock and admin form

**Files:**
- Modify: `SeasonPhaseService.java` (`create` and `update` gain `boolean tieBreakRaceWinner`), `SeasonPhaseForm.java`, `SeasonPhaseController.java`, `templates/admin/season-phase-form.html`, `playoff-form.html` only if it creates the phase with the same service signature
- Test: `SeasonPhaseServiceTest.java`, `src/test/java/org/ctc/admin/controller/SeasonPhaseTieBreakControllerIT.java`

**Interfaces:**
- Produces: `boolean SeasonPhaseService.hasScoredPairing(UUID phaseId)`: true if any Match in the phase has non-null scores or a walkover, any PlayoffMatchup of its playoff has scores or a winner (byes excluded), or any race in the phase has results. Add repository exists-queries as needed.

- [ ] **Step 1: Failing tests**: `givenScoredMatch_whenToggleTieBreak_thenRejected` (exact text), `givenDecidedPlayoffMatchup_whenToggleTieBreak_thenRejected`, `givenOnlyByes_whenToggleTieBreak_thenSaved`, `givenScoredPhase_whenSaveUnchangedFlag_thenOtherFieldsSaved`; controller IT: form shows labeled checkbox `Tie-break by race winner` with hint `A tied match goes to the team whose driver wins the first leg with results.`, POST persists it, rejection lands in field/flash error with submitted values kept.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement**: check inside `update` after `scoreLocks.lockAllSeasons()`, next to the race-scoring check. Grep all callers of `SeasonPhaseService.create`/`update` (incl. tests, `TestDataService`) and pass `false` where no form exists.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(phases): add locked tie-break setting`.

### Task 5: Readers use the stored outcome

**Files:**
- Modify: `StandingsService.java:411-456`, `sitegen/MatchdaysPageGenerator.java:284` (+ its view record and `site/matchday.html`/`fragments/match-card.html` for the label), `templates/admin/matchday-detail.html:80-85` (move comparisons into prepared fields), `race-detail.html:29-57` when the race's Match has exactly one leg
- Test: `StandingsServiceTest.java`, `MatchdaysPageGeneratorTest` (or the existing generator test), `src/test/java/org/ctc/admin/controller/MatchdayDetailTieBreakIT.java`

**Interfaces:**
- Consumes: `Match.outcome()`, `MatchOutcome`.

- [ ] **Step 1: Failing tests**: `givenTieBreakWin_whenCalculateStandings_thenWinAndLossWithWinPointsAndEqualPointsFor`; Buchholz phase IT variant counts it as a win (extend `StandingsBuchholzPhaseIT`); public matchday shows winner and label `Won on tie-break`; admin matchday detail renders `score-win`/`score-loss` plus the label; single-leg race detail shows no `DRAW` badge for a tie-break match.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement**; first `rg -n "Score\(\)\s*[<>]|Total\s*[<>]|score-draw|DRAW"` over `src/main` and route every Match-level comparison through `outcome()`. Per-race views of multi-leg matches stay unchanged. Graphics templates get `homeIsWinner`/`awayIsWinner` from the outcome.
- [ ] **Step 4: Run** → PASS. Refresh generated site snapshots if a snapshot test fails on the new label.
- [ ] **Step 5: Commit** `feat(standings): count tie-break wins in every view`.

### Task 5b: Results graphics badge

**Files:**
- Modify: `ResultsGraphicService.java:110-116`, `MatchResultsGraphicService.java:70-75`, `templates/admin/results-render.html`, `templates/admin/match-results-render.html`, `templates/admin/template-editors.html` (variable rows for both graphics), `admin.css` only if render templates use it (they likely carry inline `<style>`)
- Test: `ResultsGraphicServiceTest`/`MatchResultsGraphicServiceTest` on `buildHtml` (both services are JaCoCo-excluded but `buildHtml` is testable without Playwright)

**Interfaces:**
- Produces template variables `homeIsTieBreakWinner`, `awayIsTieBreakWinner` (boolean). `homeIsWinner`/`awayIsWinner` come from `Match.outcome()` or the matchup winner on the match graphic, and on the race graphic from the race totals unless the race is the pairing's `tieBreakRace`.

- [ ] **Step 1: Failing tests**: `givenDecidingLeg_whenBuildHtml_thenWinnerAndTieBreakBadge` (HTML contains `TIE-BREAK` on the winner side), `givenOtherLegOfTieBreakMatch_whenBuildHtml_thenNoBadge`, `givenTieBreakMatch_whenBuildMatchHtml_thenWinnerAndBadge`, `givenPlayoffTieBreak_whenBuildHtml_thenBadge`, `givenRegularDraw_whenBuildHtml_thenNoWinnerNoBadge`.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement**: badge text exactly `TIE-BREAK`, no reason line, styled like the existing winner marker.
- [ ] **Step 4: Run** → PASS. Render both graphics once with playwright-cli or the dev server and save screenshots to `.screenshots/`.
- [ ] **Step 5: Commit** `feat(graphics): mark tie-break wins on results graphics`.

### Task 6: Backup schema 28, restore and audit

**Files:**
- Create: `src/main/java/org/ctc/backup/schema/TieBreakArchive.java`, `TieBreakBackupValidator.java`
- Modify: `BackupSchema.java` (28), `BackupImportService.java` (supported set, `normalizeRestorableRow`, preflight validator), `MatchMixIn.java` (`getTieBreakWinner()`, `getTieBreakRace()` as ID references), `PlayoffMatchupMixIn.java` (`getTieBreakRace()`), `PlayoffMatchupRestorer.java`, backup graph fetch for `tieBreakWinner` next to `walkoverTeam`, `MatchRestorer.java`, `SeasonPhaseRestorer.java`, `DataAuditService.java` (category `PAIRING`)
- Test: `TieBreakArchiveTest`, `TieBreakBackupValidatorTest`, `src/test/java/org/ctc/backup/service/BackupTieBreakRoundTripIT.java`, `DataAuditServiceIT`, plus existing schema-number assertions

**Interfaces:**
- Produces: `TieBreakArchive.normalize(int version, String table, JsonNode row): JsonNode`: for `season_phases` and `matches`: version < 28 rejects a present non-default value (`"Tie-break fields require backup schema 28"`) and sets `false`/`null`; version 28 requires a boolean flag. `TieBreakBackupValidator.accept(String table, JsonNode row)` / `validate()` with the 100,000-reference budget pattern of `MatchWalkoverBackupValidator`: a set `tieBreakWinner` must come with a `tieBreakRace` of the same match and vice versa, equal home or away team, scores non-null and equal, `bye` false, no walkover, owning phase flag true (resolve match → matchday → phase).

- [ ] **Step 1: Failing tests**: archive normalize for 27 (absent → defaults; present → rejected) and 28; validator rejects each invariant break; round-trip IT exports a phase with flag and a tie-break match and restores both unchanged without reaggregation; schema-27 archive restores flag false; audit reports (a) enabled-phase match with equal scores and null winner, (b) winner violating the invariant, (c) stored winner differing from `TieBreakRule`.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement**; then `rg -n "\b27\b" src/test/java/org/ctc/backup deploy/` and update assertions that pin the current schema (see `BackupSchemaGuardIT`, `BackupImportSchemaMismatchIT`).
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(backup): carry tie-break settings in schema 28`.

### Task 7: Documentation and PR

**Files:**
- Modify: `CLAUDE.md` (one bullet "Phase tie-break"), `README.md` (one line under playoffs/scoring)

- [ ] **Step 1:** Add the CLAUDE.md bullet: flag per phase, locked once scored, first leg with results, `RaceClassification`, quick-score rejection, stored on aggregation, V50/schema 28.
- [ ] **Step 2:** Self-review via `superpowers:requesting-code-review`, fix findings.
- [ ] **Step 3:** Commit `docs: describe phase tie-break`, push, `gh pr create --assignee jegr78` with a Conventional title `feat(scoring): tie-break tied pairings by race winner`. Remind the user about the GitHub Wiki update.
- [ ] **Step 4:** Watch CI (`gh run list --branch feature/phase-tie-break`); fix failures; merge only after green CI and user OK.
