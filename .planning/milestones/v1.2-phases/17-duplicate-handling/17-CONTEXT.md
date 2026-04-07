# Phase 17: Duplicate-Handling - Context

**Gathered:** 2026-04-07
**Status:** Ready for planning

<domain>
## Phase Boundary

The merge service resolves unique-constraint conflicts without data loss or uncaught exceptions. When source and target driver both exist in the same season, race lineup, or race result, the duplicate source entry is dropped rather than causing a constraint violation. All non-duplicate entries are still reassigned correctly. This phase modifies the existing `DriverMergeService.merge()` method from Phase 16.

</domain>

<decisions>
## Implementation Decisions

### Conflict Detection Strategy
- **D-01:** Proactive duplicate check before each FK reassignment — for each source entry, query whether the target driver already has an entry for the same season/race before attempting reassignment
- **D-02:** Use existing repository methods: `SeasonDriverRepository.findBySeasonIdAndDriverId()`, `RaceLineupRepository.findByRaceIdAndDriverId()` for lookup; add `RaceResultRepository.findByRaceIdAndDriverId()` (new method needed)

### Conflict Resolution
- **D-03:** When a duplicate is detected (target already has an entry for the same season/race), delete the source entry instead of reassigning it — the target's existing entry is preserved unchanged
- **D-04:** This applies to all three FK tables: SeasonDriver (UniqueConstraint on season_id+driver_id), RaceResult (UniqueConstraint on race_id+driver_id), RaceLineup (no DB constraint but logical duplicates handled defensively)

### MergeResult Reporting
- **D-05:** Extend `MergeResult` record with additional dropped counts: `seasonDriversDropped`, `raceLineupsDropped`, `raceResultsDropped` — so the UI (Phase 18) can report "3 reassigned, 1 duplicate dropped"
- **D-06:** Log dropped duplicates at `log.info()` level with the specific entity details (season name/race name) for audit trail

### RaceLineup Handling
- **D-07:** Even though `race_lineups` table has no UniqueConstraint, check for logical duplicates (same race + same driver) and handle them identically — delete source entry when target is already in that race's lineup
- **D-08:** No new Flyway migration to add a DB constraint — that would be scope creep. Defensive logic only.

### Claude's Discretion
- Internal ordering of duplicate detection within the reassignment loop
- Whether to use a single loop with inline check or extract a helper method
- Exact field names for the new MergeResult counts

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Service to Modify
- `src/main/java/org/ctc/domain/service/DriverMergeService.java` — Current merge logic (Phase 16), needs duplicate detection added to each FK reassignment loop

### Domain Model (Unique Constraints)
- `src/main/java/org/ctc/domain/model/SeasonDriver.java` — UniqueConstraint(season_id, driver_id) at line 13-14
- `src/main/java/org/ctc/domain/model/RaceResult.java` — UniqueConstraint(race_id, driver_id) at line 15-16
- `src/main/java/org/ctc/domain/model/RaceLineup.java` — No UniqueConstraint, but logical duplicates must be handled

### Repositories (Duplicate Detection Queries)
- `src/main/java/org/ctc/domain/repository/SeasonDriverRepository.java` — `findBySeasonIdAndDriverId()` exists (line 19)
- `src/main/java/org/ctc/domain/repository/RaceLineupRepository.java` — `findByRaceIdAndDriverId()` exists (line 19)
- `src/main/java/org/ctc/domain/repository/RaceResultRepository.java` — needs new `findByRaceIdAndDriverId()` method

### Existing Tests
- `src/test/java/org/ctc/domain/service/DriverMergeServiceTest.java` — Phase 16 tests, extend with duplicate scenarios

### Requirements
- `.planning/REQUIREMENTS.md` — MERGE-11, MERGE-12, MERGE-13

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `SeasonDriverRepository.findBySeasonIdAndDriverId()` — returns Optional, can detect if target driver already exists in same season
- `RaceLineupRepository.findByRaceIdAndDriverId()` — returns Optional, can detect if target driver already in same race lineup
- `DriverMergeService.merge()` — existing method with clear loop structure per FK table, each loop can be extended with a pre-check

### Established Patterns
- Proactive validation before mutation (see self-merge check D-03 in Phase 16)
- `Optional.isPresent()` for existence checks (standard project pattern)
- Repository `delete()` for removing entities (see `driverRepository.delete(source)` in Phase 16)
- `log.info()` with parameterized `{}` for state changes

### Integration Points
- `DriverMergeService.merge()` is the single method to modify — all three FK loops gain a duplicate check
- `MergeResult` record extends with 3 new fields — Phase 18 controller will use these for the success message
- `RaceResultRepository` needs one new method: `findByRaceIdAndDriverId(UUID, UUID)` returning `Optional<RaceResult>`

</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 17-duplicate-handling*
*Context gathered: 2026-04-07*
