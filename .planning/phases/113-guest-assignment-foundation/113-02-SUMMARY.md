---
phase: 113-guest-assignment-foundation
plan: 02
subsystem: domain-service
tags: [guest-drivers, lineup, scoring-cascade, results-form]
requires:
  - "RaceLineup.isGuest() / 4-arg constructor (113-01)"
provides:
  - "RaceLineupService.saveLineup(raceId, roster, guests) 3-arg overload + 2-arg backward-compat"
  - "Roster+guest dedup (BusinessRuleException)"
  - "Guest-removal RaceResult cascade + scoringService.aggregateMatchScores re-aggregation"
  - "RaceLineupService.getGuestLineups(raceId); guest-filtered getDriverAssignments(raceId)"
affects:
  - "Phase 113-03 (controller calls 3-arg saveLineup; GET reads getGuestLineups)"
tech-stack:
  added: []
  patterns:
    - "Overload delegation (2-arg → 3-arg with Map.of()) keeps existing callers unbroken"
    - "Score Aggregation on Result mutation (CLAUDE.md): aggregateMatchScores after guest-result cascade-delete"
    - "Mockito strict-mode: new deps stubbed/verified only on the guest-removal path"
key-files:
  created: []
  modified:
    - src/main/java/org/ctc/domain/service/RaceLineupService.java
    - src/test/java/org/ctc/domain/service/RaceLineupServiceTest.java
    - src/test/java/org/ctc/domain/service/RaceFormDataServiceTest.java
key-decisions:
  - "Dedup throws BusinessRuleException (established family, 17 usages, GlobalExceptionHandler-mapped) — chosen over the rarer ValidationException"
  - "No circular dependency: ScoringService does not inject RaceLineupService (grep-verified), so ScoringService is injected directly via @RequiredArgsConstructor — no @Lazy/ObjectProvider needed"
  - "droppedGuestDriverIds computed BEFORE deleteAll (needs the existing entries' guest flags); cascade + re-aggregate run only when at least one guest was dropped"
requirements-completed: [GUEST-02, GUEST-03]
duration: 14 min
completed: 2026-06-01
---

# Phase 113 Plan 02: Guest Service Layer Summary

Extended `RaceLineupService` so guest assignments are saved, validated, removed-with-result-cascade, and read back for prefill — while roster behavior and existing callers stay untouched. Guest results auto-derive from the lineup (GUEST-02), verified by a `RaceFormDataService` test.

## Tasks

- **Task 1** — `RaceLineupService`: two `@Transactional` `saveLineup` overloads (2-arg delegates to 3-arg with `Map.of()`); roster via 3-arg constructor (`is_guest=false`), guests via 4-arg (`is_guest=true`); roster∩guest collision throws `BusinessRuleException` before any mutation; guests dropped from the new map get their `RaceResult` cascade-deleted then `scoringService.aggregateMatchScores(race)` runs once; `getDriverAssignments` filters out guests; new `getGuestLineups`. Two new injected deps (`RaceResultRepository`, `ScoringService`). 7 new unit tests. Commit `abd6bcd3`.
- **Task 2** — `RaceFormDataServiceTest`: new test proving a guest `RaceLineup` surfaces as a results-form row for its fielding team via the public `getResultsFormData`. Commit `570b085b`.

2 tasks, 3 files modified, 2 atomic commits.

## Verification

- `./mvnw -Dtest=RaceLineupServiceTest clean test` — 13/13 green (6 existing + 7 new), no `UnnecessaryStubbingException`.
- `./mvnw -Dtest=RaceFormDataServiceTest clean test` — 8/8 green (7 existing + 1 new).
- Existing 2-arg `saveLineup` and `getDriverAssignments` tests remain green (no signature breakage).

## Deviations from Plan

None - plan executed exactly as written.

**Total deviations:** 0.

## Issues Encountered

None.

## Next Phase Readiness

Ready for 113-03 — the controller can call the 3-arg `saveLineup(raceId, roster, guests)`, read `getGuestLineups(raceId)` for prefill, and rely on the guest-removal cascade running end-to-end through the real `@Transactional` proxy.
