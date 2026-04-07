# Phase 8: Exception Refinement - Context

**Gathered:** 2026-04-05
**Status:** Ready for planning

<domain>
## Phase Boundary

Replace all blanket `catch(Exception e)` blocks in controllers and services with specific exception catches. Constrain unbounded `findAll()` queries in RaceService, DriverService, and DriverRankingService. After this phase, unexpected exceptions propagate to GlobalExceptionHandler and display the admin error page.

</domain>

<decisions>
## Implementation Decisions

### Exception Type Mapping (ERRH-01)
- **D-01:** Map each `catch(Exception e)` to the actual exceptions thrown by the called code. Controllers calling services that throw `IllegalStateException` or `BusinessRuleException` catch those specifically. IO operations catch `IOException`. Let unexpected exceptions propagate to GlobalExceptionHandler.
- **D-02:** Graphic generation endpoints (MatchdayController, PlayoffController, PowerRankingsController, RaceController) narrow from `catch(Exception e)` to `catch(RuntimeException e)` — Playwright/AbstractGraphicService throws RuntimeException on failure. These endpoints return `ResponseEntity.internalServerError()` which is correct behavior for byte-returning graphic endpoints.
- **D-03:** Service-level image upload catches (CarService, TrackService, TeamManagementService) narrow to `catch(IOException e)` — FileStorageService operations throw IOException. The wrapping to BusinessRuleException is preserved.
- **D-04:** CsvImportController catches narrow to `IOException` + `IllegalArgumentException` for CSV parsing, and `BusinessRuleException` for import execution.
- **D-05:** Gt7SyncController narrows to `RuntimeException` (scraping + HTTP + Playwright failures). Gt7SyncService and Gt7ScraperService batch catches narrow to `IOException` for image download resilience — these are per-item catches in batch loops that must not abort the entire sync.

### Scope Exclusions
- **D-06:** TemplateEditorController catch blocks are excluded — deferred to Phase 10 (ARCH-03) which completely refactors this controller. Fixing catches now would be throwaway work.
- **D-07:** DemoDataSeeder catch block is excluded — not in production code, excluded from coverage.
- **D-08:** SeasonController catch(Exception e) — investigate what it catches and narrow accordingly.

### Unbounded Query Scoping (QUAL-02)
- **D-09:** `RaceService.getRaceListData()` fallback to `raceRepository.findAll()` must require a seasonId — the UI always provides one. Remove the unscoped fallback path.
- **D-10:** `DriverRankingService.calculateRankings()` uses `seasonDriverRepository.findAll()` then filters in-memory — replace with a repository query filtered by the relevant season(s) if feasible, otherwise document why findAll is acceptable.
- **D-11:** `DriverService.findAll()` is used for admin dropdown lists (dozens of drivers max) — acceptable as-is, low priority. Only address if a scoped alternative is trivial.
- **D-12:** Other findAll() calls in SeasonManagementService, MatchdayService, etc. are for admin list views with small datasets (teams, scorings, seasons) — out of QUAL-02 scope, acceptable.

### Claude's Discretion
- Whether to add multi-catch (`catch (IOException | IllegalArgumentException e)`) or separate catch blocks per exception type
- Exact exception types for Gt7SyncService/Gt7ScraperService batch operations (IOException vs more specific)
- Whether DriverRankingService needs a new repository query method or if the in-memory filter is acceptable given dataset size
- Test strategy: which catch-block refinements need dedicated test coverage vs relying on existing tests

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Error Handling Infrastructure
- `src/main/java/org/ctc/admin/controller/GlobalExceptionHandler.java` — Central exception handler: EntityNotFoundException, BusinessRuleException, ValidationException, NoSuchElementException, and general Exception fallback
- `src/main/java/org/ctc/domain/exception/` — Custom exception classes (EntityNotFoundException, BusinessRuleException, ValidationException)
- `.planning/codebase/CONCERNS.md` — "Unbounded findAll() Queries" section with specific locations and fix approach

### Controllers with catch(Exception e) — Graphic Generation
- `src/main/java/org/ctc/admin/controller/MatchdayController.java` — 4 catch blocks (lines 120, 132, 144, 157) for overview/schedule/results/match-results graphics
- `src/main/java/org/ctc/admin/controller/PlayoffController.java` — 3 graphic catches (lines 196, 208, 220) + 4 business catches (lines 68, 121, 166, 182)
- `src/main/java/org/ctc/admin/controller/PowerRankingsController.java` — 1 graphic catch (line 80)
- `src/main/java/org/ctc/admin/controller/RaceController.java` — 4 graphic catches (lines 199, 210, 221, 232) + 2 business catches (lines 156, 188)

### Controllers with catch(Exception e) — Business Operations
- `src/main/java/org/ctc/dataimport/CsvImportController.java` — 3 catches (lines 50, 81, 147) for CSV parse, Google Sheet read, import execution
- `src/main/java/org/ctc/gt7sync/Gt7SyncController.java` — 1 catch (line 53) for sync execution
- `src/main/java/org/ctc/admin/controller/TeamCardController.java` — 2 catches (lines 72, 87) for card generation

### Services with catch(Exception e)
- `src/main/java/org/ctc/domain/service/CarService.java` — 1 catch (line 95) for image upload
- `src/main/java/org/ctc/domain/service/TrackService.java` — 1 catch (line 95) for image upload
- `src/main/java/org/ctc/domain/service/TeamManagementService.java` — 1 catch (line 299) for logo upload
- `src/main/java/org/ctc/gt7sync/Gt7SyncService.java` — 2 catches (lines 123, 134) for batch image download
- `src/main/java/org/ctc/gt7sync/Gt7ScraperService.java` — 1 catch (line 107) for image resolution

### Unbounded findAll() Targets (QUAL-02)
- `src/main/java/org/ctc/domain/service/RaceService.java` — line 89: `raceRepository.findAll()` fallback
- `src/main/java/org/ctc/domain/service/DriverRankingService.java` — line 59: `seasonDriverRepository.findAll()`
- `src/main/java/org/ctc/domain/service/DriverService.java` — line 46: `driverRepository.findAll()`

### Requirements
- `.planning/REQUIREMENTS.md` — ERRH-01, QUAL-02 definitions

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `GlobalExceptionHandler` already handles EntityNotFoundException, BusinessRuleException, ValidationException, NoSuchElementException — no new handler registrations needed
- `BusinessRuleException`, `ValidationException`, `EntityNotFoundException` custom exceptions already exist in `org.ctc.domain.exception`
- Existing test classes for all affected controllers and services

### Established Patterns
- Phase 1 established GlobalExceptionHandler and custom exceptions — this phase builds on that foundation
- Service methods throw `IllegalStateException` for business rules (legacy pattern from before Phase 1) and `BusinessRuleException` (newer pattern)
- FileStorageService throws `IOException` for file operations
- AbstractGraphicService methods throw `RuntimeException` for Playwright failures
- Controllers use flash attributes (`errorMessage`) for POST-Redirect-GET error handling

### Integration Points
- Graphic generation endpoints return `ResponseEntity<byte[]>` — error handling returns `.internalServerError().build()`
- Form submission endpoints use `RedirectAttributes` flash messages — error handling sets `errorMessage` and redirects
- All controllers use `@ActiveProfiles("dev")` in tests → GlobalExceptionHandler is active in test context

</code_context>

<specifics>
## Specific Ideas

No specific requirements — systematic exception narrowing following existing patterns and exception hierarchy.

</specifics>

<deferred>
## Deferred Ideas

- TemplateEditorController catch blocks — Phase 10 (ARCH-03) will refactor entire controller
- DemoDataSeeder catch block — excluded from coverage, not production concern
- SeasonController catch block investigation — may already use specific exceptions, verify during planning

</deferred>

---

*Phase: 08-exception-refinement*
*Context gathered: 2026-04-05*
