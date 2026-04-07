# Phase 8: Exception Refinement - Research

**Researched:** 2026-04-05
**Domain:** Exception handling refinement, unbounded query scoping (Spring Boot 4 / Java 25)
**Confidence:** HIGH

## Summary

Phase 8 replaces all blanket `catch(Exception e)` blocks in controllers and services with specific exception catches, and scopes unbounded `findAll()` queries. The codebase has 63 total `catch(Exception e)` occurrences across 15 files. Of these, 31 are in TemplateEditorController (deferred to Phase 10) and 1 is in DemoDataSeeder (excluded). That leaves **31 catch blocks to refine** across 13 files, plus 2-3 `findAll()` queries to scope.

The existing infrastructure is solid: GlobalExceptionHandler already handles `EntityNotFoundException`, `BusinessRuleException`, `ValidationException`, `NoSuchElementException`, and a general `Exception` fallback. Custom exception classes exist in `org.ctc.domain.exception`. The work is mechanical: trace each catch block to identify what the called code actually throws, narrow the catch accordingly, and let unexpected exceptions propagate to GlobalExceptionHandler.

**Primary recommendation:** Group changes by exception category (graphic endpoints, file upload services, import/sync controllers, business operation controllers) and process each group as a cohesive unit. The findAll() scoping is independent and can be a separate task.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Map each `catch(Exception e)` to the actual exceptions thrown by the called code. Controllers calling services that throw `IllegalStateException` or `BusinessRuleException` catch those specifically. IO operations catch `IOException`. Let unexpected exceptions propagate to GlobalExceptionHandler.
- **D-02:** Graphic generation endpoints (MatchdayController, PlayoffController, PowerRankingsController, RaceController) narrow from `catch(Exception e)` to `catch(RuntimeException e)` -- Playwright/AbstractGraphicService throws RuntimeException on failure. These endpoints return `ResponseEntity.internalServerError()` which is correct behavior for byte-returning graphic endpoints.
- **D-03:** Service-level image upload catches (CarService, TrackService, TeamManagementService) narrow to `catch(IOException e)` -- FileStorageService operations throw IOException. The wrapping to BusinessRuleException is preserved.
- **D-04:** CsvImportController catches narrow to `IOException` + `IllegalArgumentException` for CSV parsing, and `BusinessRuleException` for import execution.
- **D-05:** Gt7SyncController narrows to `RuntimeException` (scraping + HTTP + Playwright failures). Gt7SyncService and Gt7ScraperService batch catches narrow to `IOException` for image download resilience -- these are per-item catches in batch loops that must not abort the entire sync.
- **D-06:** TemplateEditorController catch blocks are excluded -- deferred to Phase 10 (ARCH-03).
- **D-07:** DemoDataSeeder catch block is excluded -- not in production code, excluded from coverage.
- **D-08:** SeasonController catch(Exception e) -- investigate what it catches and narrow accordingly.
- **D-09:** `RaceService.getRaceListData()` fallback to `raceRepository.findAll()` must require a seasonId -- remove the unscoped fallback path.
- **D-10:** `DriverRankingService.calculateAlltimeRanking()` uses `seasonDriverRepository.findAll()` then filters in-memory -- replace with a repository query if feasible.
- **D-11:** `DriverService.findAll()` is used for admin dropdown lists -- acceptable as-is, low priority.
- **D-12:** Other findAll() calls are out of QUAL-02 scope.

### Claude's Discretion
- Whether to add multi-catch (`catch (IOException | IllegalArgumentException e)`) or separate catch blocks per exception type
- Exact exception types for Gt7SyncService/Gt7ScraperService batch operations (IOException vs more specific)
- Whether DriverRankingService needs a new repository query method or if the in-memory filter is acceptable given dataset size
- Test strategy: which catch-block refinements need dedicated test coverage vs relying on existing tests

### Deferred Ideas (OUT OF SCOPE)
- TemplateEditorController catch blocks -- Phase 10 (ARCH-03) will refactor entire controller
- DemoDataSeeder catch block -- excluded from coverage, not production concern
- SeasonController catch block investigation -- may already use specific exceptions, verify during planning
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ERRH-01 | Alle 60+ catch(Exception e) in Controllern durch spezifische Exception-Catches ersetzt, unerwartete Exceptions propagieren zu GlobalExceptionHandler | Exception type mapping for all 31 in-scope catch blocks documented below with verified throw signatures |
| QUAL-02 | Unbounded findAll() in RaceService, DriverService, DriverRankingService eingegrenzt (seasonId-Parameter oder Limit) | RaceService fallback removal, DriverRankingService repository query options, DriverService acceptability documented |
</phase_requirements>

## Architecture Patterns

### Exception Type Mapping (Verified from Source Code)

The following is the exact exception mapping for each in-scope catch block, verified by reading the called service/method signatures.

#### Category 1: Graphic Download Endpoints (return `ResponseEntity<byte[]>`)

These endpoints call graphic services that throw `IOException` (from `AbstractGraphicService.renderScreenshot()`). However, Playwright can also throw unchecked `RuntimeException`. D-02 says narrow to `catch(RuntimeException e)` -- but `IOException` is checked and would not be caught by `RuntimeException`.

**Research finding:** The Matchday/Playoff/PowerRankings graphic endpoints call services that declare `throws IOException`. Since `IOException` is checked, the code MUST catch it or declare it. The current `catch(Exception e)` catches both. Narrowing to `catch(RuntimeException e)` alone would cause a **compile error** because `IOException` is a checked exception.

**Correct narrowing:** `catch(IOException | RuntimeException e)` -- catches the declared checked exception AND any Playwright runtime failures.

| Controller | Lines | Called Method | Declared Throws | Narrow To |
|-----------|-------|--------------|-----------------|-----------|
| MatchdayController | 120, 132, 144, 157 | `*GraphicService.generate*()` | `IOException` | `catch(IOException \| RuntimeException e)` |
| PlayoffController | 196, 208, 220 | `*GraphicService.generate*()` | `IOException` | `catch(IOException \| RuntimeException e)` |
| PowerRankingsController | 80 | `powerRankingsGraphicService.generateRankings()` | `IOException` | `catch(IOException \| RuntimeException e)` |

**RaceController graphic endpoints** (lines 199, 210, 221, 232) call `raceGraphicService.generate*()` which does NOT declare `throws IOException` -- it wraps `IOException` into `RuntimeException` internally. So these can narrow to `catch(RuntimeException e)`.

| Controller | Lines | Called Method | Declared Throws | Narrow To |
|-----------|-------|--------------|-----------------|-----------|
| RaceController | 199, 210, 221, 232 | `raceGraphicService.generate*()` | none (wraps to RuntimeException) | `catch(RuntimeException e)` |
| TeamCardController | 72, 87 | `teamCardService.generate*()` | `IOException` | `catch(IOException \| RuntimeException e)` |

#### Category 2: Service-Level File Upload (wrap to BusinessRuleException)

| Service | Line | Called Method | Declared Throws | Narrow To |
|---------|------|--------------|-----------------|-----------|
| CarService | 95 | `fileStorageService.storeImage()` + `delete()` | `IOException` | `catch(IOException e)` |
| TrackService | 95 | `fileStorageService.storeImage()` + `delete()` | `IOException` | `catch(IOException e)` |
| TeamManagementService | 299 | `fileStorageService.storeImage()` + `delete()` | `IOException` | `catch(IOException e)` |

#### Category 3: Business Operation Controllers (flash error + redirect)

| Controller | Line | Called Method | Throws | Narrow To |
|-----------|------|--------------|--------|-----------|
| PlayoffController | 68 | `playoffService.createPlayoff()` | `IllegalArgumentException`, `IllegalStateException` | `catch(IllegalArgumentException \| IllegalStateException e)` |
| PlayoffController | 121 | `playoffService.autoSeedBracket()` | `IllegalStateException` | `catch(IllegalStateException e)` |
| PlayoffController | 166 | `playoffService.determineWinner()` | `IllegalStateException`, `EntityNotFoundException` | `catch(IllegalStateException \| EntityNotFoundException e)` |
| PlayoffController | 182 | `playoffService.setWinnerManually()` | `EntityNotFoundException` | `catch(EntityNotFoundException \| IllegalStateException e)` |
| RaceController | 156 | `raceAttachmentService.uploadAttachment()` | `RuntimeException` (wraps IOException) | `catch(RuntimeException e)` |
| RaceController | 188 | `raceService.createOrUpdateCalendarEvent()` | `IOException`, `IllegalStateException` | `catch(IOException \| IllegalStateException e)` |
| SeasonController | 133 | `seasonManagementService.updateSeasonTeam()` | `IOException` | `catch(IOException e)` |

#### Category 4: Import/Sync Controllers

| Controller | Line | Called Method | Throws | Narrow To |
|-----------|------|--------------|--------|-----------|
| CsvImportController | 50 | `csvImportService.parseAndPreview()` | `IOException` + parsing `IllegalArgumentException` | `catch(IOException \| IllegalArgumentException e)` |
| CsvImportController | 81 | `googleSheetsService.*()` + `scorecardParser.parse()` | `IOException`, `IllegalArgumentException` | `catch(IOException \| IllegalArgumentException e)` |
| CsvImportController | 147 | `csvImportService.executeImport()` + re-parse | `IOException`, `IllegalArgumentException`, `RuntimeException` | `catch(IOException \| RuntimeException e)` |
| Gt7SyncController | 53 | `syncService.executeSync()` | `IOException` | `catch(IOException e)` |

#### Category 5: Batch Operation Catches (in async loops, must not abort batch)

| Service | Lines | Operation | Narrow To |
|---------|-------|-----------|-----------|
| Gt7SyncService | 123, 134 | `fileStorageService.storeFromUrl()` in CompletableFuture | `catch(IOException e)` |
| Gt7ScraperService | 107 | `fetchText()` in CompletableFuture | `catch(IOException e)` |

**Note on Gt7SyncService/Gt7ScraperService batch catches:** These run inside `CompletableFuture.runAsync()`. The lambdas cannot throw checked exceptions directly. The `storeFromUrl()` method declares `throws IOException`, so the lambda body has a try-catch. Narrowing to `catch(IOException e)` is correct -- any other exception from `storeFromUrl` is a programming error that should propagate (the CompletableFuture will capture it as exceptional completion).

However, `Gt7ScraperService.fetchText()` may need investigation -- if it's a Jsoup call, it throws `IOException`. If it uses `java.net.http.HttpClient`, exceptions differ. The current catch logs a warning and continues, which is the right behavior for batch resilience.

### Pattern: Multi-Catch vs Separate Blocks

**Recommendation: Use multi-catch** (`catch (IOException | IllegalArgumentException e)`) when all exception types receive the same handling (same error message format, same redirect). Use separate blocks only when handling differs.

In this phase, all catch blocks within a given endpoint do the same thing (log + flash error + redirect, or log + return 500). Multi-catch is cleaner and reads better.

```java
// Preferred: multi-catch when handling is identical
} catch (IOException | IllegalStateException e) {
    redirectAttributes.addFlashAttribute("errorMessage", "Calendar: " + e.getMessage());
}

// Only if handling differs:
} catch (IOException e) {
    redirectAttributes.addFlashAttribute("errorMessage", "File error: " + e.getMessage());
} catch (IllegalStateException e) {
    redirectAttributes.addFlashAttribute("errorMessage", "Business rule: " + e.getMessage());
}
```

### Unbounded findAll() Scoping

**RaceService.getRaceListData() (D-09):**
Current code at line 88-89: `else { races = raceRepository.findAll(); }` -- this fallback fires when neither `matchdayId` nor `seasonId` is provided. The UI always passes a seasonId from the season dropdown. Fix: throw `IllegalArgumentException` when both are null, or require seasonId as non-null parameter. The controller should always provide one.

**DriverRankingService.calculateAlltimeRanking() (D-10):**
Uses `seasonDriverRepository.findAll()` at line 59 to build a driver-to-team map across all seasons. This is specifically an all-time ranking -- it genuinely needs all season-driver records. `SeasonDriverRepository` has `findBySeasonId()` but not a method that fetches across multiple specific seasons. The dataset is small (dozens of drivers x few seasons = hundreds of records max). **Recommendation: Leave findAll() but add a code comment documenting why it's acceptable.** A new repository method would add complexity for no practical gain.

**DriverService.findAll() (D-11):**
Used for admin dropdown lists. Dataset is small (dozens of drivers). D-11 marks this as acceptable. No change needed.

### Recommended Project Structure

No structural changes needed. All modifications are within existing files.

```
src/main/java/org/ctc/
├── admin/controller/       # 7 controllers with catch blocks to narrow
├── domain/service/         # 3 services with catch blocks to narrow  
├── domain/repository/      # No new repository methods needed
├── dataimport/             # 1 controller with catch blocks to narrow
└── gt7sync/                # 1 controller + 2 services with catch blocks
```

### Anti-Patterns to Avoid
- **Catching too broadly after narrowing:** Don't add `catch(RuntimeException e)` as a second catch "just in case" -- that defeats the purpose. Let unexpected exceptions propagate to GlobalExceptionHandler.
- **Changing error messages:** Keep existing error message formats. This is a mechanical narrowing, not an error UX change.
- **Catching checked exceptions not actually thrown:** If the called method doesn't declare `throws IOException`, don't catch `IOException`. The compiler enforces this anyway.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Global error handling | Per-controller error views | `GlobalExceptionHandler` (already exists) | Consistent error page rendering for all unhandled exceptions |
| Exception hierarchy | New exception classes | Existing `BusinessRuleException`, `EntityNotFoundException`, `ValidationException` | Infrastructure from Phase 1 is complete |

## Common Pitfalls

### Pitfall 1: IOException in Multi-Catch with RuntimeException
**What goes wrong:** `catch(IOException | RuntimeException e)` -- `IOException` is checked, `RuntimeException` is unchecked. If the try block doesn't throw `IOException`, the compiler will reject it.
**Why it happens:** Copying a multi-catch pattern to an endpoint where the called method wraps IOException internally.
**How to avoid:** Verify each method's declared `throws` clause before choosing the catch type. RaceGraphicService methods do NOT declare `throws IOException` (they wrap it), so RaceController graphic endpoints catch `RuntimeException` only.
**Warning signs:** Compile errors after narrowing.

### Pitfall 2: CompletableFuture Lambda Checked Exceptions
**What goes wrong:** `CompletableFuture.runAsync(() -> { ... })` lambdas cannot throw checked exceptions. The existing code handles this correctly with try-catch inside the lambda.
**Why it happens:** Forgetting that Runnable's run() doesn't declare checked exceptions.
**How to avoid:** Keep the try-catch structure inside the lambda. Only narrow the exception type.

### Pitfall 3: Removing Fallback in RaceService Without Controller Check
**What goes wrong:** Removing the `findAll()` fallback in RaceService but the controller still allows requests without seasonId.
**Why it happens:** Not checking the controller's `@RequestParam` configuration.
**How to avoid:** Verify the RaceController's `getRaces()` method has `seasonId` as effectively required, or add a guard in the service.

### Pitfall 4: SeasonController Catch Block Context
**What goes wrong:** The SeasonController catch at line 133 catches Exception for `seasonManagementService.updateSeasonTeam()`. This method declares `throws IOException`. The error message says "Logo upload failed" which is accurate -- the only failure mode is file I/O.
**How to avoid:** Narrow to `catch(IOException e)`.

## Code Examples

### Multi-Catch for Graphic Download Endpoints (MatchdayController pattern)
```java
// Before:
} catch (Exception e) {
    log.error("Failed to generate overview graphic for matchday {}", id, e);
    return ResponseEntity.internalServerError().build();
}

// After:
} catch (IOException | RuntimeException e) {
    log.error("Failed to generate overview graphic for matchday {}", id, e);
    return ResponseEntity.internalServerError().build();
}
```

### IOException Narrowing for File Upload Services (CarService pattern)
```java
// Before:
} catch (Exception e) {
    throw new BusinessRuleException("Image upload failed: " + e.getMessage());
}

// After:
} catch (IOException e) {
    throw new BusinessRuleException("Image upload failed: " + e.getMessage());
}
```

### RaceService findAll() Removal
```java
// Before:
if (matchdayId != null) {
    races = raceRepository.findByMatchdayId(matchdayId);
} else if (seasonId != null) {
    races = raceRepository.findByMatchdaySeasonId(seasonId);
} else {
    races = raceRepository.findAll();
}

// After:
if (matchdayId != null) {
    races = raceRepository.findByMatchdayId(matchdayId);
    matchday = matchdayRepository.findById(matchdayId).orElse(null);
} else if (seasonId != null) {
    races = raceRepository.findByMatchdaySeasonId(seasonId);
    selectedSeasonId = seasonId;
} else {
    races = List.of();
}
```

### RaceController Graphic Endpoint (RuntimeException only)
```java
// Before:
} catch (Exception e) {
    redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
}

// After:
} catch (RuntimeException e) {
    redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
}
```

### PlayoffController Business Operation
```java
// Before:
} catch (Exception e) {
    log.error("Error creating playoff", e);
    redirectAttributes.addFlashAttribute("errorMessage", "Error: " + e.getMessage());
}

// After:
} catch (IllegalArgumentException | IllegalStateException e) {
    log.error("Error creating playoff", e);
    redirectAttributes.addFlashAttribute("errorMessage", "Error: " + e.getMessage());
}
```

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Mockito + Spring MockMvc |
| Config file | `pom.xml` (surefire + failsafe plugins) |
| Quick run command | `./mvnw test -pl . -Dtest=*ControllerTest` |
| Full suite command | `./mvnw verify` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| ERRH-01 | Graphic endpoints catch IOException/RuntimeException specifically | unit | `./mvnw test -Dtest=MatchdayControllerTest,PlayoffControllerTest,RaceControllerTest,PowerRankingsControllerTest,TeamCardControllerTest -pl .` | Existing tests cover happy paths; error path tests may need additions |
| ERRH-01 | File upload services catch IOException specifically | unit | `./mvnw test -Dtest=CarServiceTest,TrackServiceTest,TeamManagementServiceTest -pl .` | Existing service tests exist |
| ERRH-01 | Import/Sync controllers catch specific exceptions | unit | `./mvnw test -Dtest=CsvImportControllerTest,Gt7SyncControllerTest -pl .` | Existing controller tests exist |
| ERRH-01 | Unexpected exceptions propagate to GlobalExceptionHandler | unit | `./mvnw test -Dtest=GlobalExceptionHandlerTest -pl .` | Existing -- already tests general Exception handler |
| QUAL-02 | RaceService rejects null seasonId/matchdayId | unit | `./mvnw test -Dtest=RaceServiceTest -pl .` | Existing |
| QUAL-02 | DriverRankingService alltime ranking acceptable | unit | `./mvnw test -Dtest=DriverRankingServiceTest -pl .` | Existing |

### Sampling Rate
- **Per task commit:** `./mvnw test -pl .` (unit tests only, ~30s)
- **Per wave merge:** `./mvnw verify` (full suite with coverage)
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
None -- existing test infrastructure covers all phase requirements. Exception narrowing is primarily a compile-time change (wrong catch types cause compile errors). Existing tests verify behavior is preserved. New error-path tests are optional but recommended for the RaceService findAll() removal.

## Open Questions

1. **CsvImportController execute() (line 147) -- full exception scope**
   - What we know: `executeImport()` can throw various exceptions including `RuntimeException` from driver matching. Re-parse calls throw `IOException` and `IllegalArgumentException`.
   - What's unclear: Whether `BusinessRuleException` is thrown during import execution.
   - Recommendation: Use `catch(IOException | RuntimeException e)` -- RuntimeException covers BusinessRuleException (which extends RuntimeException) and any unexpected parsing failures. Or investigate executeImport() more deeply during implementation.

2. **Gt7ScraperService.fetchText() exception type**
   - What we know: Used in a CompletableFuture lambda to fetch JS chunks via HTTP.
   - What's unclear: Whether it uses Jsoup (IOException) or java.net.http.HttpClient (different exceptions).
   - Recommendation: Check during implementation. Most likely `IOException` from Jsoup. Narrowing to `IOException` is safe; if wrong, the compiler will tell you.

## Project Constraints (from CLAUDE.md)

- **Test Coverage:** 82% Line Coverage Minimum must be maintained
- **TDD:** Tests first, then implementation (Red -> Green -> Refactor)
- **Test naming:** `givenContext_whenAction_thenExpectedResult()` with `// given` / `// when` / `// then` comments
- **Error Handling Pattern:** `IllegalStateException` for business rules, `IllegalArgumentException` for invalid input, `orElseThrow()` for entity lookups
- **Flyway:** No changes to existing migrations (not relevant for this phase)
- **Git:** Feature branch, PR workflow, `./mvnw verify` before PR
- **Conventional Commits:** `refactor(exception): ...` prefix for this work

## Sources

### Primary (HIGH confidence)
- Direct source code analysis of all 15 files with `catch(Exception e)` blocks
- `FileStorageService.java` method signatures: `store()`, `storeFromUrl()`, `storeImage()` all declare `throws IOException`
- `AbstractGraphicService.java`: `renderScreenshot()` and `renderScreenshotTransparent()` declare `throws IOException`
- `RaceGraphicService.java`: Wraps `IOException` to `RuntimeException` internally, no checked exceptions declared
- `SeasonManagementService.updateSeasonTeam()`: declares `throws IOException`
- `RaceService.createOrUpdateCalendarEvent()`: declares `throws IOException`
- `Gt7SyncService.executeSync()`: declares `throws IOException`
- `PlayoffService` methods: throw `IllegalStateException`, `IllegalArgumentException`, `EntityNotFoundException`
- `CsvImportService.parseAndPreview()`: declares `throws IOException`
- `GoogleSheetsService.readRange()`: declares `throws IOException`
- `GlobalExceptionHandler.java`: Handles EntityNotFoundException, BusinessRuleException, ValidationException, NoSuchElementException, and general Exception

### Secondary (MEDIUM confidence)
- Exception mapping for CsvImportController execute() -- RuntimeException scope inferred from method chains

## Metadata

**Confidence breakdown:**
- Exception type mapping: HIGH - verified from method signatures in source code
- Architecture patterns: HIGH - mechanical narrowing with compiler verification
- Pitfalls: HIGH - based on Java language rules (checked vs unchecked exceptions)
- findAll() scoping: HIGH - verified repository interfaces and usage patterns

**Research date:** 2026-04-05
**Valid until:** 2026-05-05 (stable -- exception handling infrastructure unchanged)
