# Phase 15: Alltime Standings Recovery - Context

**Gathered:** 2026-04-07
**Status:** Ready for planning

<domain>
## Phase Boundary

Re-apply cross-season team standings aggregation lost by worktree file clobber during Phase 10/11 parallel execution. The alltime standings feature was fully implemented in Phase 9 (commits `0979c0f`, `d5c6e56`) and verified — this phase recovers that exact functionality into the current codebase.

</domain>

<decisions>
## Implementation Decisions

### Aggregation Strategy (from Phase 9)
- **D-01:** Iterate all seasons with match results, calculate per-season standings using each season's own MatchScoring rules, then sum W/D/L/Points/PointsFor/PointsAgainst across seasons per team. Reuses `calculateStandings(seasonId)` as the building block.
- **D-02:** No Buchholz in alltime standings — Buchholz is Swiss-format-specific and meaningless across seasons.

### Team Identity Resolution (from Phase 9)
- **D-03:** Resolve teams to parent via `Team.getParentOrSelf()` for cross-season aggregation. Sub-team results count toward the parent team.
- **D-04:** Within each season, use `Season.buildSuccessionMap()` as `calculateStandings()` already does. Parent-resolution happens after per-season calculation when merging into alltime totals.

### Display Columns (from Phase 9)
- **D-05:** Alltime standings table uses standard league columns: #, Team, MP, W, D, L, PR, Pts.
- **D-06:** Reuse the existing `standingsTable` template structure — no template changes needed.

### Season Inclusion (from Phase 9)
- **D-07:** Include all seasons that have match results (completed matches with scores). No filtering by season status.

### Recovery Approach
- **D-08:** Cherry-pick the logic from commits `0979c0f` and `d5c6e56` into the current StandingsService structure. The current service has gained `calculateStandingsWithBuchholz()` and `RaceRepository` since Phase 9 — the alltime code is additive and non-conflicting.

### Claude's Discretion
- Whether to apply diffs manually or use git cherry-pick (manual preferred since StandingsService has new methods)
- Test data setup adjustments if StandingsServiceTest structure changed since Phase 9

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Recovery Source (CRITICAL)
- Git commit `0979c0f` — `calculateAlltimeStandings()` method + `TeamStanding.merge()` + 7 unit tests
- Git commit `d5c6e56` — Controller wiring (TODO placeholder → service call) + integration test update

### Current Implementation
- `src/main/java/org/ctc/domain/service/StandingsService.java` — Current service state; alltime method goes after `calculateStandingsWithBuchholz()` (line 76)
- `src/main/java/org/ctc/admin/controller/StandingsController.java` — Lines 31-34: TODO placeholder to replace with `calculateAlltimeStandings()` call
- `src/test/java/org/ctc/domain/service/StandingsServiceTest.java` — Add alltime standings unit tests
- `src/test/java/org/ctc/admin/controller/StandingsControllerTest.java` — Update alltime integration test

### Original Phase Context
- `.planning/phases/09-alltime-standings/09-CONTEXT.md` — Full original decision context

### Domain Model
- `src/main/java/org/ctc/domain/model/Team.java` — `getParentOrSelf()` for sub-team resolution
- `src/main/java/org/ctc/domain/model/Season.java` — `buildSuccessionMap()`, `getMatchScoring()`, `getActiveTeams()`
- `src/main/java/org/ctc/domain/service/DriverRankingService.java` — `calculateAlltimeRanking()` as reference pattern

### Requirements
- `.planning/REQUIREMENTS.md` — FEAT-01: Alltime Standings cross-season team aggregation

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `StandingsService.calculateStandings(seasonId)` — Per-season calculation; called per-season in alltime loop
- `StandingsService.TeamStanding` — Inner class at line 172; needs `merge()` method added
- `DriverRankingService.calculateAlltimeRanking()` — Proven cross-season aggregation pattern with `getParentOrSelf()`

### Established Patterns
- Per-season calculation with `MatchScoring` from season entity
- `resolveTeamId()` using succession maps within a season
- `TeamStanding` accumulator pattern (add methods for wins/draws/losses/points)
- Sorting: points desc → point difference desc → pointsFor desc

### Integration Points
- `StandingsController.standings()` line 32: Replace `List.of()` with `standingsService.calculateAlltimeStandings()`
- Template already handles alltime display (headers, table selection) — no template changes needed
- `Season` import already present would need to be added (currently not imported)

</code_context>

<specifics>
## Specific Ideas

Recovery follows the exact implementation from Phase 9 commits. No new design decisions needed — apply the proven code to the current codebase structure.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 15-alltime-standings-recovery*
*Context gathered: 2026-04-07*
