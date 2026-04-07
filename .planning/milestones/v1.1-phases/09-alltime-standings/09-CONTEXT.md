# Phase 9: Alltime Standings - Context

**Gathered:** 2026-04-05
**Status:** Ready for planning

<domain>
## Phase Boundary

Implement cross-season team standings aggregation in StandingsService so that the existing "Alltime" dropdown option in the standings page displays a ranked list of teams with accumulated stats across all seasons. The alltime driver ranking already works — this phase adds the team equivalent.

</domain>

<decisions>
## Implementation Decisions

### Aggregation Strategy
- **D-01:** Iterate all seasons with match results, calculate per-season standings using each season's own MatchScoring rules, then sum the resulting W/D/L/Points/PointsFor/PointsAgainst across seasons per team. This preserves the existing `calculateStandings(seasonId)` logic as the building block.
- **D-02:** No Buchholz in alltime standings — Buchholz is Swiss-format-specific and meaningless across seasons.

### Team Identity Resolution
- **D-03:** Resolve teams to their parent via `Team.getParentOrSelf()` for cross-season aggregation. Sub-team results count toward the parent team. This follows the established pattern from `DriverRankingService.calculateAlltimeRanking()`.
- **D-04:** Within each season, use `Season.buildSuccessionMap()` as the existing `calculateStandings()` already does. The parent-resolution happens after per-season calculation when merging into alltime totals.

### Display Columns
- **D-05:** Alltime standings table uses the standard league table columns: # (rank), Team, MP, W, D, L, PR (Points Ratio), Pts. Same as the non-Swiss per-season table.
- **D-06:** Reuse the existing `standingsTable` template structure. The alltime view should render using the same table as league standings (the non-Swiss table already shows when `selectedSeason == null`).

### Season Inclusion
- **D-07:** Include all seasons that have match results (completed matches with scores). No filtering by season status — if matches have been played, the season counts. This matches the alltime driver ranking behavior.

### Claude's Discretion
- Whether to create a new method `calculateAlltimeStandings()` or extend the existing method with a flag
- Whether to reuse the existing `TeamStanding` class or create a new alltime-specific class
- Internal implementation: collect all seasons via `seasonRepository.findAll()` and filter to those with matches, or query matches directly across all seasons
- Test data setup: how to create multi-season test fixtures for unit tests

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Standings Implementation
- `src/main/java/org/ctc/domain/service/StandingsService.java` — Per-season standings calculation, TeamStanding inner class, processMatch() logic, Buchholz integration
- `src/main/java/org/ctc/domain/service/DriverRankingService.java` — Alltime driver ranking pattern (lines 56-91): cross-season aggregation with `getParentOrSelf()` team resolution

### Controller & Template
- `src/main/java/org/ctc/admin/controller/StandingsController.java` — Lines 29-34: TODO placeholder returning empty list for alltime, already has `isAlltime` flag
- `src/main/resources/templates/admin/standings.html` — Lines 10-11: "Alltime" dropdown option already exists, lines 24-25: conditional headers already handle alltime

### Domain Model
- `src/main/java/org/ctc/domain/model/Team.java` — `getParentOrSelf()` method for sub-team resolution
- `src/main/java/org/ctc/domain/model/Season.java` — `buildSuccessionMap()`, `getMatchScoring()`, `getActiveTeams()`
- `src/main/java/org/ctc/domain/model/MatchScoring.java` — Per-season scoring rules (pointsWin, pointsDraw, pointsLoss)

### Concern Documentation
- `.planning/codebase/CONCERNS.md` §"Incomplete Feature: Alltime Standings" — Fix approach: implement `StandingsService.calculateAlltimeStandings()`

### Requirements
- `.planning/REQUIREMENTS.md` — FEAT-01: Alltime Standings zeigt cross-season Team-Aggregation

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `StandingsService.calculateStandings(seasonId)` — Per-season standings calculation; can be called per-season and results merged
- `StandingsService.TeamStanding` — Inner class with W/D/L/Points/PointsFor/PointsAgainst; may need to be reused or cloned for alltime aggregation
- `DriverRankingService.calculateAlltimeRanking()` — Proven pattern for cross-season aggregation with parent-team resolution
- `Team.getParentOrSelf()` — Sub-team to parent team resolution
- `Season.buildSuccessionMap()` — Within-season team identity mapping

### Established Patterns
- Per-season calculation with `MatchScoring` rules from the season entity
- `resolveTeamId()` using succession maps within a season
- `TeamStanding` accumulator pattern (add methods for wins/draws/losses/points)
- Thymeleaf conditional rendering based on `isAlltime` flag (already in template)

### Integration Points
- `StandingsController.standings()` line 31-34: Replace `List.of()` with `standingsService.calculateAlltimeStandings()` call
- Template already handles alltime display (headers, table selection) — no template changes expected beyond ensuring the non-Swiss table renders for alltime

</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches. Follow the established `DriverRankingService.calculateAlltimeRanking()` pattern adapted for team standings.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 09-alltime-standings*
*Context gathered: 2026-04-05*
