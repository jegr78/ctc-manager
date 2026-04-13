# Phase 31: Null Safety and Transaction Fix - Context

**Gathered:** 2026-04-13
**Status:** Ready for planning

<domain>
## Phase Boundary

Multi-race CSV import becomes atomic (validate-then-import, all-or-nothing), race services handle bye matches and unlinked races without NPE by returning safe defaults, and the driver-team fallback in ScoringService filters by current season to prevent cross-season misattribution. Only the identified problem spots are in scope — no systematic audit of all null paths, no new features.

</domain>

<decisions>
## Implementation Decisions

### Transaction Atomicity (DATA-01)
- **D-01:** Validate-then-import strategy: `executeImport()` first validates ALL team pairs and rows (teams resolvable, drivers assignable, duplicates checked), then imports only if all validations pass
- **D-02:** If any validation error exists, no import happens — all errors are returned to the user in one response
- **D-03:** Duplicate check (match already exists on matchday) is part of the validation phase — when overwrite=false and a match exists, it counts as a validation error that aborts the entire import
- **D-04:** The existing `@Transactional` on `executeImport()` remains as safety net — if an unexpected exception occurs during the import phase, everything rolls back

### Null Safety for Bye Matches (DATA-03)
- **D-05:** Fix only the identified NPE paths: `RaceFormDataService.getRaceFormData()`, `RaceFormDataService.getResultsFormData()`, `ScoringService.aggregateMatchScores()`
- **D-06:** Safe defaults strategy: methods return empty/neutral values when encountering bye matches or unlinked races (empty result lists, skip populateDrivers for null teams, score 0)
- **D-07:** No error logging for bye matches — byes are normal operation, not exceptional situations
- **D-08:** Existing null-safe code (RaceCalendarService, StandingsService with `isBye()` checks, stream filters with `!r.isBye()`) remains unchanged

### Season-Filtered Driver Fallback (DATA-04)
- **D-09:** Fix only `ScoringService.isDriverInTeam()` — the only fallback that affects scoring results. Display-only fallbacks (TeamManagementService, SiteGeneratorService) are lower risk and out of scope
- **D-10:** Season ID derived from race internally: `raceId → Race → Matchday → Season.getId()`. No change to the `isDriverInTeam(RaceResult, UUID raceId, UUID teamId)` method signature
- **D-11:** Fallback becomes: `result.getDriver().getSeasonDrivers().stream().filter(sd -> sd.getSeason().getId().equals(seasonId)).anyMatch(sd -> sd.getTeam().getId().equals(teamId))`

### Claude's Discretion
- Internal structure of the validation phase in `executeImport()` (private method extraction, error collection pattern)
- Whether to load Race entity in `isDriverInTeam()` via repository lookup or pass through existing context
- Test structure and naming for new validation and null-safety tests

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### CSV Import (DATA-01)
- `src/main/java/org/ctc/dataimport/CsvImportService.java` — `executeImport()` method (L117-221), `groupByTeamPair()`, `findTeamFlexible()`, `resolveDriver()`, `ensureSeasonDriver()`
- `src/main/java/org/ctc/dataimport/CsvImportController.java` — Controller calling `executeImport()` (L138), error handling pattern
- `src/test/java/org/ctc/dataimport/CsvImportServiceTest.java` — Existing import tests (if any)

### Null Safety (DATA-03)
- `src/main/java/org/ctc/domain/service/RaceFormDataService.java` — `getRaceFormData()` L63 (NPE on `race.getHomeTeam().getId()`), `getResultsFormData()` L76-77 (NPE on `race.getAwayTeam()`)
- `src/main/java/org/ctc/domain/service/ScoringService.java` — `aggregateMatchScores()` L62-86 (inconsistent null checks for awayTeam)
- `src/main/java/org/ctc/domain/service/RaceCalendarService.java` L43 — Already null-safe (reference pattern)
- `src/main/java/org/ctc/domain/model/Race.java` L87 — `isBye()` implementation

### Season Filter (DATA-04)
- `src/main/java/org/ctc/domain/service/ScoringService.java` — `isDriverInTeam()` L130-145 (unfiltered SeasonDriver fallback)
- `src/main/java/org/ctc/domain/repository/RaceRepository.java` — May need `findById()` for race-to-season resolution

### Conventions
- `.planning/codebase/CONVENTIONS.md` — Transaction management patterns, exception hierarchy
- `CLAUDE.md` — TDD approach, RaceLineup Source of Truth principle, test naming (Given-When-Then)

### Requirements
- `.planning/REQUIREMENTS.md` — DATA-01, DATA-03, DATA-04

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `Race.isBye()` — Existing bye-match check (delegates to `match.isBye()`), used consistently in StandingsService, SwissPairingService, RaceFormDataService stream filters
- `RaceCalendarService` L43 — Reference pattern for null-safe team handling: `if (race.getHomeTeam() == null || race.getAwayTeam() == null) { return; }`
- `ValidationException` — Existing domain exception for validation errors, already used in CsvImportService
- `ImportResult` — Has `addError()` and `getErrors()` for collecting validation errors

### Established Patterns
- `@Transactional` on service write methods, `@Transactional(readOnly = true)` on reads
- Validation errors thrown as `ValidationException` (400 response via GlobalExceptionHandler)
- RaceLineup as Source of Truth with SeasonDriver fallback (ScoringService.isDriverInTeam pattern)
- Stream filters with `!r.isBye()` before accessing `getHomeTeam()`/`getAwayTeam()`

### Integration Points
- `CsvImportService.executeImport()` — Refactor to two-phase: validate-all, then import-all
- `RaceFormDataService.getRaceFormData()` / `getResultsFormData()` — Add null guards before team access
- `ScoringService.aggregateMatchScores()` — Consistent null-check for awayTeam
- `ScoringService.isDriverInTeam()` — Add season filter to SeasonDriver fallback

</code_context>

<specifics>
## Specific Ideas

No specific requirements — follows existing patterns (ValidationException for errors, isBye() for null-safety, SeasonDriver filtering by season ID).

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 31-null-safety-and-transaction-fix*
*Context gathered: 2026-04-13*
