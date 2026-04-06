# Phase 14: Exception Refinement Recovery - Context

**Gathered:** 2026-04-06
**Status:** Ready for planning

<domain>
## Phase Boundary

Re-apply specific exception catches in controllers and services lost by worktree file clobber (commit 5b3a58b regression). Additionally recover unbounded query scoping from Phase 8. After this phase, no `catch(Exception e)` blocks remain in controllers — each catch targets a specific exception type, unexpected exceptions propagate to GlobalExceptionHandler, and unbounded findAll() queries are scoped.

</domain>

<decisions>
## Implementation Decisions

### Recovery Strategy
- **D-01:** Manual re-implementation based on Phase 8 CONTEXT.md decisions (D-01 through D-12). No cherry-pick — Phase 13 has changed many files (new service methods, DTO decoupling), making conflicts likely.
- **D-02:** Use Phase 8 exception type mapping as the blueprint, but verify against the current codebase since method signatures and imports have changed after Phase 13.

### Recovery Scope (ERRH-01 + QUAL-02)
- **D-03:** Full Phase 8 scope: both ERRH-01 (specific exception catches) and QUAL-02 (unbounded query scoping).
- **D-04:** TemplateEditorController is NOW IN SCOPE — Phase 8 excluded it (D-06) pending Phase 10 refactoring. Phase 10 is complete, the controller is stable with generic dispatch. Its 4 `catch(Exception e)` blocks should be narrowed.

### Exception Type Mapping (ERRH-01) — from Phase 8
- **D-05:** Graphic generation endpoints (MatchdayController, TeamCardController, PowerRankingsController) narrow from `catch(Exception e)` to `catch(RuntimeException e)` — Playwright/AbstractGraphicService throws RuntimeException on failure. These endpoints return `ResponseEntity.internalServerError()`.
- **D-06:** Service-level image upload catches (CarService, TrackService, TeamManagementService) narrow to `catch(IOException e)` — FileStorageService operations throw IOException. The wrapping to BusinessRuleException is preserved.
- **D-07:** CsvImportController catches narrow to `IOException` + `IllegalArgumentException` for CSV parsing, and `BusinessRuleException` for import execution.
- **D-08:** Gt7SyncController narrows to `RuntimeException` (scraping + HTTP + Playwright failures). Gt7SyncService and Gt7ScraperService batch catches narrow to `IOException` for image download resilience — these are per-item catches in batch loops that must not abort the entire sync.
- **D-09:** SeasonController catch(Exception e) — investigate what it catches and narrow accordingly.
- **D-10:** TemplateEditorController catches (4 blocks in generic dispatch) — investigate actual exception types from TemplateManageable implementations and narrow accordingly. Likely `RuntimeException` from Playwright graphic generation + `IOException` from file operations.

### Scope Exclusions
- **D-11:** DemoDataSeeder catch block is excluded — not production code, excluded from coverage.

### Unbounded Query Scoping (QUAL-02) — from Phase 8
- **D-12:** `RaceService.getRaceListData()` fallback to `raceRepository.findAll()` must require a seasonId — remove the unscoped fallback path.
- **D-13:** `DriverRankingService.calculateRankings()` uses `seasonDriverRepository.findAll()` then filters in-memory — replace with scoped query if feasible, otherwise document why findAll is acceptable.
- **D-14:** `DriverService.findAll()` for admin dropdown lists — acceptable as-is (small dataset). Only address if trivial.

### Test Strategy
- **D-15:** TDD approach per CLAUDE.md. Write tests for specific exception catch behavior first, then narrow the catch blocks. Verify existing tests still pass.
- **D-16:** Full verification via `./mvnw verify` — ensure all tests pass with narrowed exception catches.

### Claude's Discretion
- Whether to use multi-catch (`catch (IOException | IllegalArgumentException e)`) or separate catch blocks per exception type
- Exact exception types for TemplateEditorController catch blocks based on TemplateManageable analysis
- Whether DriverRankingService needs a new repository query or if in-memory filter is acceptable
- Commit grouping — logical groups by concern area

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase 8 (Recovery Source)
- `.planning/phases/08-exception-refinement/08-CONTEXT.md` — Original decisions D-01 through D-12, exception type mapping, unbounded query scoping decisions
- `.planning/phases/08-exception-refinement/08-DISCUSSION-LOG.md` — Discussion audit trail

### Error Handling Infrastructure
- `src/main/java/org/ctc/admin/controller/GlobalExceptionHandler.java` — Central exception handler: EntityNotFoundException, BusinessRuleException, ValidationException, NoSuchElementException, and general Exception fallback
- `src/main/java/org/ctc/domain/exception/` — Custom exception classes (EntityNotFoundException, BusinessRuleException, ValidationException)

### Controllers with catch(Exception e) — Graphic Generation
- `src/main/java/org/ctc/admin/controller/MatchdayController.java` — 4 catch blocks (lines 120, 132, 144, 157) for overview/schedule/results/match-results graphics
- `src/main/java/org/ctc/admin/controller/TeamCardController.java` — 2 catches (lines 72, 87) for card generation
- `src/main/java/org/ctc/admin/controller/PowerRankingsController.java` — 1 graphic catch (line 80)

### Controllers with catch(Exception e) — Business Operations
- `src/main/java/org/ctc/dataimport/CsvImportController.java` — 3 catches (lines 50, 81, 147) for CSV parse, Google Sheet read, import execution
- `src/main/java/org/ctc/gt7sync/Gt7SyncController.java` — 1 catch (line 53) for sync execution
- `src/main/java/org/ctc/admin/controller/SeasonController.java` — 1 catch (line 132) for season operations
- `src/main/java/org/ctc/admin/controller/TemplateEditorController.java` — 4 catches (lines 74, 99, 117, 141) for generic template dispatch

### Services with catch(Exception e)
- `src/main/java/org/ctc/domain/service/CarService.java` — 1 catch (line 95) for image upload
- `src/main/java/org/ctc/domain/service/TrackService.java` — 1 catch (line 95) for image upload
- `src/main/java/org/ctc/domain/service/TeamManagementService.java` — 1 catch (line 296) for logo upload
- `src/main/java/org/ctc/gt7sync/Gt7SyncService.java` — 2 catches (lines 123, 134) for batch image download
- `src/main/java/org/ctc/gt7sync/Gt7ScraperService.java` — 1 catch (line 107) for image resolution

### Controllers already clean (no recovery needed)
- `src/main/java/org/ctc/admin/controller/RaceController.java` — 0 catch(Exception e), already specific
- `src/main/java/org/ctc/admin/controller/PlayoffController.java` — 0 catch(Exception e), already specific

### Unbounded findAll() Targets (QUAL-02)
- `src/main/java/org/ctc/domain/service/RaceService.java` — `raceRepository.findAll()` fallback
- `src/main/java/org/ctc/domain/service/DriverRankingService.java` — `seasonDriverRepository.findAll()`
- `src/main/java/org/ctc/domain/service/DriverService.java` — `driverRepository.findAll()`

### Requirements
- `.planning/REQUIREMENTS.md` — ERRH-01, QUAL-02 definitions
- `.planning/codebase/CONCERNS.md` — "Unbounded findAll() Queries" section with specific locations

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `GlobalExceptionHandler` already handles EntityNotFoundException, BusinessRuleException, ValidationException, NoSuchElementException — no new handler registrations needed
- `BusinessRuleException`, `ValidationException`, `EntityNotFoundException` custom exceptions already exist in `org.ctc.domain.exception`
- Existing test classes for all affected controllers and services
- Phase 8 CONTEXT.md provides exact exception type mapping per controller/service

### Established Patterns
- Phase 1 established GlobalExceptionHandler and custom exceptions — this phase builds on that foundation
- Service methods throw `IllegalStateException` for business rules (legacy) and `BusinessRuleException` (newer)
- FileStorageService throws `IOException` for file operations
- AbstractGraphicService methods throw `RuntimeException` for Playwright failures
- Controllers use flash attributes (`errorMessage`) for POST-Redirect-GET error handling
- Phase 10 refactored TemplateEditorController to use `TemplateManageable` interface with generic dispatch

### Integration Points
- Graphic generation endpoints return `ResponseEntity<byte[]>` — error handling returns `.internalServerError().build()`
- Form submission endpoints use `RedirectAttributes` flash messages
- TemplateEditorController uses generic Map<String, TemplateManageable> dispatch — catch blocks wrap the generic service calls
- All controllers use `@ActiveProfiles("dev")` in tests → GlobalExceptionHandler is active in test context

</code_context>

<specifics>
## Specific Ideas

- Recovery source is Phase 8 CONTEXT.md, not cherry-picks — manual re-implementation on current code state
- RaceController and PlayoffController are already clean (survived or recovered in Phase 13) — skip these
- TemplateEditorController is now included (was excluded in Phase 8 D-06, Phase 10 refactoring is complete)
- Verify exception types against current code since Phase 13 changed method signatures

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 14-exception-refinement-recovery*
*Context gathered: 2026-04-06*
