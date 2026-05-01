---
plan_id: 61-gap-02
phase: 61-cleanup-quality-gate
status: complete
completed: 2026-05-01T22:30:00Z
gap_closure: true
---

# 61-gap-02 — Stale comments: admin layer + DTOs

## What changed

Removed Phase-narrative comments from `src/main/java/org/ctc/admin/`. Where the comment
carried genuine WHY (security gates, behavioural notes, layout-specific contracts), the
substance was kept and only the stale phase/decision-tag prefix was stripped.

`TestDataService` data-shape section headers (`=== Season 2023 ===`, `=== Season 2024 ===`,
`=== Season 2026 ===`) were re-added or kept where they describe data structure rather
than process history.

## Commits

- `b983f91 docs(61-gap-02): remove stale phase-narrative comments from admin layer + DTOs`

## Files touched

9 files in `src/main/java/org/ctc/admin/`:

- 1 top-level service: `TestDataService.java` (~17 stale markers stripped)
- 6 controllers: `SeasonController`, `SeasonPhaseController`, `SeasonPhaseGroupController`,
  `PlayoffController`, `StandingsController`, `DriverSheetImportController`
- 2 form DTOs: `SeasonPhaseForm`, `SeasonPhaseGroupForm` (decision-tag prefixes stripped from
  field-level comments — `per D-05` / `per D-10` / `per D-24`)

## Diff size

9 files, 77 insertions, 108 deletions. Net: ~31 stale comment lines removed plus rewrites
that strip the phase/decision-tag prefix while keeping the substance.

## Borderline cases kept (with rationale)

- **TestDataService section headers** — `=== Season 2023 ===` / `=== Season 2024 ===` etc.
  describe data shape, not process history. Some were re-introduced where the original
  comment had been a phase-tag prefix.
- **TestDataService.createSeason** — kept the note that the `scorings` argument is
  preserved so callers attach RaceScoring/MatchScoring to the REGULAR phase explicitly.
  The argument is intentionally unused at this method; the comment prevents future
  refactors from removing it without checking caller assumptions.
- **TestDataService 2024 playoff seeding** — kept the algorithmic rationale that
  `autoSeedBracket` avoids the double-counting bug from hand-rolled team-score aggregation
  when drivers swap teams mid-season; only the `Phase 61 WR-07` prefix was stripped.
- **StandingsController** — kept the legacy-bridge explanation for the `?seasonId=`
  query param fallback (auto-resolves to the season's REGULAR phase). The behavior is
  non-obvious and the comment is a permanent contract note.
- **SeasonPhaseController.detail** — kept the comment explaining why we resolve the
  Playoff entity early (so the template can read seeds without extra queries).

## Acceptance criteria

- [x] `grep -rn -E "Phase [56][0-9] (MIGR-06|D-[0-9]+|WR-0?[0-9]+|CR-0?[0-9]+|IN-0?[0-9]+)" src/main/java/org/ctc/admin/` → 0 lines
- [x] `grep -rn -E "Wave [0-9]+ cascade|cascade migration|transitional bridge" src/main/java/org/ctc/admin/` → 0 lines
- [x] `TestDataService` T- prefix isolation markers preserved (no functional changes)
- [x] `./mvnw compile -o` BUILD SUCCESS
- [x] Atomic commit (single commit for the admin sweep — overlap with gap-01 prevented
      separating the controller-vs-service commit cleanly without re-running tests)

## Self-Check: PASSED

Admin-layer stale-comment sweep complete. No behavior change. Compile gate green.
