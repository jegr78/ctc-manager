---
plan_id: 61-gap-01
phase: 61-cleanup-quality-gate
status: complete
completed: 2026-05-01T22:00:00Z
gap_closure: true
---

# 61-gap-01 — Stale comments: domain.model + domain.repository + domain.service

## What changed

Removed Phase-narrative comments (`Phase 6X MIGR-06`, `D-XX`, `WR-XX`, `CR-XX`, `MERGE-XX`,
`Wave N cascade`, `transitional bridge`) from the entire `src/main/java/org/ctc/domain/`
subtree. Where the comment carried genuine WHY (algorithm rationale, contract notes,
non-obvious invariants), the substance was kept and only the stale phase/decision-tag
prefix was stripped.

## Commits

- `4f35e05 docs(61-gap-01): remove stale phase-narrative comments from domain.model + domain.repository`
- `cabe8c5 docs(61-gap-01): remove stale phase-narrative comments from domain.service`

## Files touched

26 files in `src/main/java/org/ctc/domain/`:

- 3 model files (Season, Matchday, Playoff)
- 8 repository files (Match, Matchday, Phase­Team, Playoff, Race, RaceLineup, RaceResult, Season)
- 15 service files (DriverMerge, DriverRanking, Matchday, MatchdayGenerator, PlayoffBracketView,
  Playoff, PlayoffSeeding, RaceCalendar, RaceFormData, Race, Scoring, SeasonManagement,
  SeasonPhase, Standings, SwissPairing)

## Diff size

- Task 1 (model + repository): 11 files, 11 insertions, 54 deletions
- Task 2 (service): 15 files, 125 insertions, 235 deletions

Total: ~289 stale comment lines removed; ~136 substance-preserving rewrites kept the
WHY without the phase/decision-tag prefix.

## Borderline cases kept (with rationale)

- **Playoff.java** — kept the warning that `startDate` / `endDate` / `eventDurationMinutes`
  also live on the parent `SeasonPhase` and can diverge (genuine contract note that future
  callers need; only the historical "Phase 61 WR-04 follow-up" + planned-V7 prose was stripped).
- **PlayoffService.createPlayoff** — kept the comment "Link matchday to PLAYOFF phase, NOT
  REGULAR" because it warns about a misattribution pitfall in DriverRankingService; the stale
  "Phase 61 MIGR-06" prefix was stripped.
- **MatchdayService.createInline** — kept the explanation that the lookup is scoped to the
  REGULAR phase to prevent sortIndex/duplicate-label collisions across phases; only the
  "Phase 61 CR-01" prefix was stripped.
- **SwissPairingService** — preserved the algorithmic rationale for Buchholz tie-breaking,
  per-group isolation, and bye-tracking; only the `D-17` / `D-21` prefixes were dropped.
- **SeasonManagementService.findUnique** — kept the 0/1/many contract documentation; only the
  "see Phase 59 D-17 / D-19" cross-references were trimmed.

## Test gate

`./mvnw test -Dtest='SwissPairingServiceTest,StandingsServiceTest,SeasonPhaseServiceTest,DriverRankingServiceTest,PlayoffServiceTest,MatchdayServiceTest,SeasonManagementServiceTest'`

→ `Tests run: 175, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS

`./mvnw -q compile -o` → no compilation errors.

## Acceptance criteria

- [x] `grep -rn -E "Phase 6[01] (MIGR-06|WR-[0-9]+|CR-[0-9]+|IN-[0-9]+|D-[0-9]+)" src/main/java/org/ctc/domain/` → 0 lines
- [x] `grep -rn -E "Wave [0-9]+ cascade|cascade migration|transitional bridge|bridge field" src/main/java/org/ctc/domain/` → 0 lines
- [x] Convenience-Getter contracts on `Matchday` + `Playoff` preserved (`grep -c "Convenience getter"` returns 1 each)
- [x] Targeted Surefire suite GREEN
- [x] Each commit is atomic (Task 1 = model+repo, Task 2 = service)

## Self-Check: PASSED

Domain-layer stale-comment sweep complete. No behavior change. Test gate green.
