---
plan_id: 61-gap-04
phase: 61-cleanup-quality-gate
status: complete
completed: 2026-05-01T23:30:00Z
gap_closure: true
---

# 61-gap-04 — Javadoc audit: domain.repository + domain.service

## What changed

Targeted Javadoc additions and one parameter cleanup across the domain layer. Followed
CLAUDE.md "default to no comments — only add WHY when non-obvious" — the audit added
Javadoc only on methods whose post-cascade semantics or contracts are non-obvious from
the name + signature.

## IN-03 fix — SeasonRepository

Added Javadoc to all four finders explaining:
- `findByActiveTrue` — clarifies that scoring/format/dates live on `SeasonPhase`
- `findBySeasonTeamsTeamId` — describes the association path
- `findByYearAndNumber` — documents the 0/1/&gt;1 multiplicity contract
- `findByYear` — legacy single-year lookup, same contract

## IN-05 fix — StandingsService.calculateBuchholzScoresForPhase

Dropped the unused `groupId` parameter (private method, single internal caller). Updated
the Javadoc to explain why the season-level delegation is correct for both LEAGUE and
GROUPS combined-view contexts. Also removed the now-unused
`org.ctc.domain.exception.EntityNotFoundException` import that the IDE flagged as a
warning after the parameter was removed.

## Other Javadoc additions

- `MatchdayRepository.findBySeasonIdOrderBySortIndexAsc` and
  `findByPhaseIdOrderBySortIndexAsc`: explain the phase-vs-season distinction (the
  CR-01 fix made this distinction a permanent contract — phase-scoped is preferred for
  sortIndex/duplicate-label).
- `PlayoffService.createPlayoff`: documents the atomic auto-creation of the PLAYOFF
  SeasonPhase, scoring copy from REGULAR, and the {2, 4, 8} numberOfTeams contract.
- `PlayoffService.getPlayoffTeams`: documents the canonical-season derivation.
- `MatchdayService.createInline`: documents REGULAR-phase scoping (CR-01 contract).

## Commits

- `d5704b8 docs(61-gap-04): add Javadoc to public APIs and fix unused groupId param (IN-03 + IN-05)`

## Files touched

5 files in `src/main/java/org/ctc/domain/`:
- `repository/SeasonRepository.java`
- `repository/MatchdayRepository.java`
- `service/StandingsService.java` (parameter removed + Javadoc + import cleanup)
- `service/PlayoffService.java`
- `service/MatchdayService.java`

## Diff size

5 files, 62 insertions, 7 deletions.

## Test gate

`./mvnw test -Dtest='StandingsServiceTest,PlayoffServiceTest,MatchdayServiceTest'`

→ `Tests run: 79, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS

## Acceptance criteria

- [x] IN-03 explicitly addressed (SeasonRepository finders documented)
- [x] IN-05 explicitly addressed (parameter dropped + Javadoc updated; the "implement per-group
      scoping vs drop the parameter" decision was resolved by dropping)
- [x] No new boilerplate Javadoc added — every Javadoc block was added because the WHY is
      non-obvious from name+signature (CR-01-derived distinctions, atomic-creation
      contracts, multiplicity contracts)
- [x] Targeted Surefire suite GREEN (79 tests)
- [x] Test count UNCHANGED (one private parameter dropped → no @Test impact)

## Self-Check: PASSED

IN-03 and IN-05 closed. Public-API Javadoc added on the methods identified as
non-obvious during the audit. No behavior change beyond the parameter signature update
(method body unchanged).
