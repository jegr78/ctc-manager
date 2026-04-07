# Phase 9: Alltime Standings - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-05
**Phase:** 09-alltime-standings
**Areas discussed:** Aggregation strategy, Team identity resolution, Display columns, Season inclusion
**Mode:** --auto (all decisions auto-selected with recommended defaults)

---

## Aggregation Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Sum per-season match results | Calculate per-season standings using each season's MatchScoring, then sum W/D/L/Points across seasons per team | ✓ |
| Recalculate from raw match scores | Ignore per-season MatchScoring, apply a single scoring rule across all matches | |
| Weighted by season recency | Weight recent seasons higher in the aggregation | |

**User's choice:** [auto] Sum per-season match results (recommended default)
**Notes:** Preserves existing `calculateStandings(seasonId)` as building block. Each season's own MatchScoring rules are respected.

---

## Team Identity Resolution

| Option | Description | Selected |
|--------|-------------|----------|
| Resolve to parent via getParentOrSelf() | Sub-team results count toward parent team, matching DriverRankingService pattern | ✓ |
| Keep sub-teams separate | Each sub-team appears as its own entry in alltime standings | |
| Configurable grouping | Let user toggle between parent-grouped and sub-team views | |

**User's choice:** [auto] Resolve to parent via getParentOrSelf() (recommended default)
**Notes:** Follows established pattern from `DriverRankingService.calculateAlltimeRanking()`. Within-season succession handled by existing `buildSuccessionMap()`.

---

## Display Columns

| Option | Description | Selected |
|--------|-------------|----------|
| Standard league table (MP/W/D/L/PR/Pts) | Same columns as non-Swiss per-season standings, no Buchholz | ✓ |
| Extended with seasons count | Add "Seasons" column showing how many seasons each team participated in | |
| Minimal (Team/W/L/Pts only) | Simplified view for cross-season overview | |

**User's choice:** [auto] Standard league table columns (recommended default)
**Notes:** Buchholz is Swiss-format-specific and meaningless across seasons. Reuses existing table template structure.

---

## Season Inclusion

| Option | Description | Selected |
|--------|-------------|----------|
| All seasons with match results | Include any season that has completed matches, regardless of season status | ✓ |
| Only completed seasons | Exclude currently active seasons from alltime aggregation | |
| Configurable season filter | Let user select which seasons to include in alltime view | |

**User's choice:** [auto] All seasons with match results (recommended default)
**Notes:** Matches the alltime driver ranking behavior which aggregates all available race results without filtering by season status.

---

## Claude's Discretion

- Method design: new `calculateAlltimeStandings()` vs extending existing method
- TeamStanding reuse vs new class
- Internal query strategy (iterate seasons vs query all matches directly)
- Test fixture design for multi-season scenarios

## Deferred Ideas

None — discussion stayed within phase scope
