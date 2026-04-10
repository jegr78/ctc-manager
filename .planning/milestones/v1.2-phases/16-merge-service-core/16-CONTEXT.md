# Phase 16: Merge Service Core - Context

**Gathered:** 2026-04-07
**Status:** Ready for planning

<domain>
## Phase Boundary

Admin can execute a driver merge that reassigns all FK references (SeasonDriver, RaceLineup, RaceResult, PsnAlias) from the source driver to the target driver and deletes the source driver. This phase covers the service layer only (happy path) — duplicate-handling (Phase 17) and UI (Phase 18) are separate.

</domain>

<decisions>
## Implementation Decisions

### Service Architecture
- **D-01:** New `DriverMergeService` in `domain.service` — separate from existing `DriverService` (which stays focused on CRUD + alias management)
- **D-02:** Single `@Transactional` method wrapping all FK reassignments + source deletion — atomic, all-or-nothing rollback on failure

### Merge Validation
- **D-03:** Self-merge prevention — `BusinessRuleException` when source == target
- **D-04:** Source and target must exist — `EntityNotFoundException` (standard project pattern)
- **D-05:** No restriction on active/inactive drivers — inactive drivers should be mergeable (that's the primary use case)

### PSN-ID Transfer
- **D-06:** Source driver's PSN-ID becomes a new `PsnAlias` on the target driver (MERGE-09)
- **D-07:** If source PSN-ID already exists as alias on target, skip silently (idempotent) — log as "already exists"
- **D-08:** Existing `PsnAlias` entries of source driver: reassign FK via `alias.setDriver(target)` through repository (not via Driver.aliases collection, due to CascadeType.ALL + orphanRemoval)

### Merge Result
- **D-09:** `merge()` returns a `MergeResult` Java record with counts per FK table (seasonDrivers, raceLineups, raceResults, aliasesReassigned) — used by Phase 18 UI for success message
- **D-10:** Additionally log via `log.info()` with structured parameters: source id/name, target id/name, timestamp, counts per FK table (MERGE-14)

### Claude's Discretion
- Method signature details (parameter types, exact record field names)
- Internal ordering of FK reassignment steps
- Repository method additions needed for bulk updates

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Domain Model (FK tables to reassign)
- `src/main/java/org/ctc/domain/model/Driver.java` — Source/target entity, aliases collection with CascadeType.ALL + orphanRemoval
- `src/main/java/org/ctc/domain/model/SeasonDriver.java` — FK table with UniqueConstraint(season_id, driver_id)
- `src/main/java/org/ctc/domain/model/RaceLineup.java` — FK table, no unique constraint on (race_id, driver_id)
- `src/main/java/org/ctc/domain/model/RaceResult.java` — FK table with UniqueConstraint(race_id, driver_id)
- `src/main/java/org/ctc/domain/model/PsnAlias.java` — FK table, alias column is UNIQUE

### Repositories (existing query methods)
- `src/main/java/org/ctc/domain/repository/DriverRepository.java` — findById, findByPsnIdIgnoreCase
- `src/main/java/org/ctc/domain/repository/SeasonDriverRepository.java` — findByDriverId, findBySeasonIdAndDriverId
- `src/main/java/org/ctc/domain/repository/RaceLineupRepository.java` — findByRaceIdAndDriverId (no findByDriverId yet)
- `src/main/java/org/ctc/domain/repository/RaceResultRepository.java` — findByDriverId
- `src/main/java/org/ctc/domain/repository/PsnAliasRepository.java` — findByAliasIgnoreCase

### Existing Service
- `src/main/java/org/ctc/domain/service/DriverService.java` — Current CRUD + alias sync logic, delete method with DataIntegrityViolationException handling

### Requirements
- `.planning/REQUIREMENTS.md` — MERGE-05 through MERGE-10, MERGE-14

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `DriverRepository.findById()` — standard entity lookup pattern
- `SeasonDriverRepository.findByDriverId()` — already returns all season assignments for a driver
- `RaceResultRepository.findByDriverId()` — already returns all results for a driver
- `BusinessRuleException` / `EntityNotFoundException` — existing exception types for validation
- `BaseEntity` — provides `createdAt`/`updatedAt` auditing on all entities

### Established Patterns
- Services use `@RequiredArgsConstructor`, `@Slf4j`, `@Service`
- Repository injection via `final` fields (constructor injection)
- `log.info()` with parameterized `{}` format for state changes
- Java records for structured return values (see `DriverService.DriverEditData`)

### Integration Points
- `RaceLineupRepository` needs a new `findByDriverId()` method (currently only has `findByRaceIdAndDriverId`)
- `PsnAliasRepository` may need `findByDriverId()` for bulk reassignment
- `DriverMergeService` injects repositories directly (not through DriverService) for FK reassignment
- Phase 17 will extend merge logic with duplicate detection before reassignment
- Phase 18 will call `DriverMergeService.merge()` from a controller

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

*Phase: 16-merge-service-core*
*Context gathered: 2026-04-07*
