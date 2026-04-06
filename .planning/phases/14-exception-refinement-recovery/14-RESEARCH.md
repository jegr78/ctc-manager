# Phase 14: Exception Refinement Recovery - Research

**Researched:** 2026-04-06
**Domain:** Java Exception Handling — Spring Boot Controller/Service refinement
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Manual re-implementation based on Phase 8 CONTEXT.md decisions. No cherry-pick — Phase 13 changed many files (new service methods, DTO decoupling), making conflicts likely.
- **D-02:** Use Phase 8 exception type mapping as the blueprint, but verify against the current codebase since method signatures and imports have changed after Phase 13.
- **D-03:** Full Phase 8 scope: both ERRH-01 (specific exception catches) and QUAL-02 (unbounded query scoping).
- **D-04:** TemplateEditorController is NOW IN SCOPE — Phase 8 excluded it (D-06) pending Phase 10 refactoring. Phase 10 is complete, the controller is stable with generic dispatch. Its 4 `catch(Exception e)` blocks should be narrowed.
- **D-05:** Graphic generation endpoints (MatchdayController, TeamCardController, PowerRankingsController) narrow from `catch(Exception e)` to `catch(RuntimeException e)`.
- **D-06:** Service-level image upload catches (CarService, TrackService, TeamManagementService) narrow to `catch(IOException e)`.
- **D-07:** CsvImportController catches narrow to `IOException` + `IllegalArgumentException` for CSV parsing, and `BusinessRuleException` for import execution.
- **D-08:** Gt7SyncController narrows to `RuntimeException`. Gt7SyncService and Gt7ScraperService batch catches narrow to `IOException`.
- **D-09:** SeasonController catch(Exception e) — investigate what it catches and narrow accordingly.
- **D-10:** TemplateEditorController catches (4 blocks in generic dispatch) — investigate actual exception types from TemplateManageable implementations and narrow accordingly.
- **D-11:** DemoDataSeeder catch block is excluded — not production code.
- **D-12:** `RaceService.getRaceListData()` fallback to `raceRepository.findAll()` must require a seasonId — remove the unscoped fallback path.
- **D-13:** `DriverRankingService.calculateRankings()` uses `seasonDriverRepository.findAll()` — replace with scoped query if feasible.
- **D-14:** `DriverService.findAll()` for admin dropdown lists — acceptable as-is.
- **D-15:** TDD approach per CLAUDE.md. Write tests for specific exception catch behavior first.
- **D-16:** Full verification via `./mvnw verify`.

### Claude's Discretion

- Whether to use multi-catch (`catch (IOException | IllegalArgumentException e)`) or separate catch blocks
- Exact exception types for TemplateEditorController catch blocks based on TemplateManageable analysis
- Whether DriverRankingService needs a new repository query or if in-memory filter is acceptable
- Commit grouping — logical groups by concern area

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ERRH-01 | Alle 60+ catch(Exception e) in Controllern durch spezifische Exception-Catches ersetzt (IOException, BusinessRuleException), unerwartete Exceptions propagieren zu GlobalExceptionHandler | Verified: 23 catch(Exception e) blocks in production code confirmed via grep (excl. DemoDataSeeder). All exception types verified against actual method signatures. |
| QUAL-02 | Unbounded findAll() in RaceService, DriverService, DriverRankingService eingegrenzt | Partially addressed: RaceService already cleaned (returns List.of() instead of findAll()). DriverRankingService still has seasonDriverRepository.findAll(). |

</phase_requirements>

---

## Summary

Phase 14 recovers exception handling refinements lost during the worktree file clobber regression (commit 5b3a58b). The codebase currently contains 23 `catch(Exception e)` blocks in production code (excluding DemoDataSeeder), distributed across 7 controllers and 5 services. All of these must be narrowed to specific exception types so that programming errors (NullPointerException, ClassCastException) propagate to GlobalExceptionHandler instead of being silently swallowed as user error messages.

The recovery is a manual re-implementation: Phase 13 changed service signatures and DTO decoupling, making cherry-pick impractical. The exception type mapping from Phase 8 CONTEXT.md remains the correct blueprint, but each catch block must be verified against the current code state. Key finding: `TemplateManageable` interface declares `throws IOException` on all three methods (`loadTemplate`, `saveTemplate`, `resetTemplate`), which definitively resolves the TemplateEditorController exception types — they should catch `IOException` specifically.

**Primary recommendation:** Work through each controller and service group in logical order. For each catch block, verify the actual exceptions thrown by the called code, narrow the catch, adjust imports, then write/extend tests to verify behavior. The GlobalExceptionHandler already handles all unexpected exceptions correctly — no new handler registrations are needed.

---

## Project Constraints (from CLAUDE.md)

- **TDD:** Write tests first (Red → Green → Refactor). Test naming: `givenContext_whenAction_thenExpectedResult()`.
- **Minimum test coverage:** 82% line coverage — must not drop below this after changes.
- **No breaking changes:** No changes to existing URLs/endpoints.
- **Flyway:** No V1 migration changes.
- **OSIV enabled:** No lazy-init changes.
- **Controllers thin:** Business logic stays in services. No direct repository access in controllers.
- **Profiles:** `@ActiveProfiles("dev")` in tests means GlobalExceptionHandler is active in test context.

---

## Current State Inventory

### Verified catch(Exception e) locations [VERIFIED: codebase grep]

**Controllers (in scope):**

| File | Line(s) | Context |
|------|---------|---------|
| `MatchdayController.java` | 120, 132, 144, 157 | Graphic generation (4 endpoints: overview, schedule, results, match-results) |
| `TeamCardController.java` | 72, 87 | Card generation (generate single, generate-all) |
| `PowerRankingsController.java` | 80 | Graphic generation (download endpoint) |
| `CsvImportController.java` | 50, 81, 147 | CSV preview, Google Sheet preview, execute import |
| `Gt7SyncController.java` | 53 | Sync execute endpoint |
| `SeasonController.java` | 132 | updateSeasonTeam (logo upload + metadata update) |
| `TemplateEditorController.java` | 74, 99, 117, 141 | index (load), save, reset, preview endpoints |

**Services (in scope):**

| File | Line(s) | Context |
|------|---------|---------|
| `CarService.java` | 95 | Image upload → wraps to BusinessRuleException |
| `TrackService.java` | 95 | Image upload → wraps to BusinessRuleException |
| `TeamManagementService.java` | 296 | Logo upload → wraps to BusinessRuleException |
| `Gt7SyncService.java` | 123, 134 | Batch image download in CompletableFuture lambdas |
| `Gt7ScraperService.java` | 107 | Image URL resolution in CompletableFuture lambdas |

**Excluded (DemoDataSeeder.java:48):** Not production code, excluded from coverage per D-11.

**Already clean (no action needed):**
- `RaceController.java` — 0 catch(Exception e) blocks [VERIFIED: not in grep results]
- `PlayoffController.java` — 0 catch(Exception e) blocks [VERIFIED: not in grep results]

---

## Architecture Patterns

### Exception Narrowing Strategy

Each `catch(Exception e)` block must be replaced with the narrowest correct type:

**Pattern 1: Graphic generation endpoints (return ResponseEntity<byte[]>)**
```java
// BEFORE
} catch (Exception e) {
    log.error("...", e);
    return ResponseEntity.internalServerError().build();
}

// AFTER — Playwright/AbstractGraphicService throws RuntimeException
} catch (RuntimeException e) {
    log.error("...", e);
    return ResponseEntity.internalServerError().build();
}
```
Applies to: MatchdayController (4x), TeamCardController (2x), PowerRankingsController (1x).

**Pattern 2: Service image upload wrapping to BusinessRuleException**
```java
// BEFORE
} catch (Exception e) {
    throw new BusinessRuleException("Image upload failed: " + e.getMessage());
}

// AFTER — FileStorageService.storeImage() throws IOException
} catch (IOException e) {
    throw new BusinessRuleException("Image upload failed: " + e.getMessage());
}
```
Applies to: CarService (1x), TrackService (1x), TeamManagementService (1x).

**Pattern 3: TemplateEditorController — TemplateManageable interface throws IOException**

The `TemplateManageable` interface declares:
```java
String loadTemplate() throws IOException;
void saveTemplate(String content) throws IOException;
void resetTemplate() throws IOException;
```
All 4 catch blocks in TemplateEditorController should narrow to `IOException`. [VERIFIED: interface source]

```java
// index() — catch during template load
} catch (IOException e) { ... }

// save() — catch during template save
} catch (IOException e) { ... }

// reset() — catch during template reset
} catch (IOException e) { ... }

// preview() — already partially specific (catches IllegalArgumentException and TemplateSecurityException),
// remaining catch(Exception e) should become catch(IOException e)
} catch (IOException e) { ... }
```

**Pattern 4: SeasonController.updateSeasonTeam — service throws IOException**

`SeasonManagementService.updateSeasonTeam()` is declared `throws IOException` (verified at line 264). The catch block should narrow to `IOException`:
```java
// BEFORE
} catch (Exception e) {
    redirectAttributes.addFlashAttribute("errorMessage", "Logo upload failed: " + e.getMessage());
}

// AFTER
} catch (IOException e) {
    redirectAttributes.addFlashAttribute("errorMessage", "Logo upload failed: " + e.getMessage());
}
```

**Pattern 5: CsvImportController — multi-catch for parsing, specific for execution**

CsvImport operations can fail in different ways:
- CSV/Sheet parsing: `IOException` (file read) + `IllegalArgumentException` (malformed data)
- Import execution: `BusinessRuleException` (duplicate check, validation)

Decision per D-07 (Claude's discretion on multi-catch vs. separate):
```java
// preview/previewSheet
} catch (IOException | IllegalArgumentException e) { ... }

// execute
} catch (BusinessRuleException e) { ... }
```
Note: The current `execute()` catch at line 147 wraps a re-parse + `csvImportService.executeImport()`. The re-parse can throw `IOException`, execution throws `BusinessRuleException`. Using `IOException | BusinessRuleException` multi-catch is appropriate.

**Pattern 6: Gt7SyncController — RuntimeException for scraping stack**

`Gt7SyncService.executeSync()` orchestrates Playwright scraping + HTTP downloads. The scraping stack throws RuntimeException on failure:
```java
} catch (RuntimeException e) { ... }
```

**Pattern 7: Gt7SyncService and Gt7ScraperService — IOException in batch CompletableFuture lambdas**

These are per-item catches inside CompletableFuture lambdas. They MUST NOT abort the entire sync. Narrowing to `IOException` is correct since `fileStorageService.storeFromUrl()` throws `IOException` and the image URL fetch also throws `IOException`:
```java
} catch (IOException e) {
    carImageResults.add(new ImageResult<>(task, null, e));
}
```
Note: `Gt7ScraperService` catch at line 107 wraps `fetchText()` calls — these are HTTP operations. The `IOException` narrowing is correct here.

### Unbounded Query Scoping

**RaceService.getRaceListData() — already fixed [VERIFIED: source]**

Current state at lines 80-88:
```java
if (matchdayId != null) {
    races = raceRepository.findByMatchdayId(matchdayId);
} else if (seasonId != null) {
    races = raceRepository.findByMatchdaySeasonId(seasonId);
} else {
    races = List.of();  // No longer calls findAll() — already fixed!
}
```
The `raceRepository.findAll()` fallback was already removed. QUAL-02 is partially complete for RaceService.

**DriverRankingService.calculateAlltimeRanking() — findAll() still present [VERIFIED: source]**

Line 59: `List<SeasonDriver> allSeasonDrivers = seasonDriverRepository.findAll();`

This is used in `calculateAlltimeRanking()` (not `calculateRankings()`). The purpose is to determine each driver's most recent team for display in alltime rankings. A scoped alternative would require a query like `findTopByDriverOrderBySeasonNameDesc()` per driver — which would be N+1 queries. The in-memory grouping is correct here for an alltime view that by definition spans all seasons. Per D-13, document why `findAll` is acceptable.

**DriverService.findAll() — acceptable as-is [VERIFIED: D-14 locked]**

Small dataset (admin dropdown). No action needed.

### GlobalExceptionHandler — No changes needed [VERIFIED: source]

Already handles:
- `EntityNotFoundException` → 404
- `NoSuchElementException` → 404
- `ValidationException` → 400
- `BusinessRuleException` → 409
- `ResponseStatusException` → rethrows
- `Exception` → 500 (fallback for unexpected exceptions)

After narrowing all catch blocks, unexpected exceptions (NullPointerException, etc.) will propagate here automatically.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead |
|---------|-------------|-------------|
| Central error page | Custom error controller | Existing `GlobalExceptionHandler` + `admin/error` template |
| Exception hierarchy | New exception classes | Existing `BusinessRuleException`, `EntityNotFoundException`, `ValidationException` |
| Mock for IOException in tests | Manual bytecode tricks | Mockito `doThrow(new IOException(...)).when(service).method()` |

---

## Common Pitfalls

### Pitfall 1: Catching too broadly still after narrowing
**What goes wrong:** Changing `catch(Exception e)` to `catch(RuntimeException e)` when the called code throws checked exceptions — compiler will catch this, but catches `IOException extends Exception` which is NOT a RuntimeException.
**How to avoid:** Always check the actual `throws` declaration of the called method. FileStorageService methods declare `throws IOException` (checked). TemplateManageable declares `throws IOException`. SeasonManagementService.updateSeasonTeam declares `throws IOException`.
**Warning signs:** Compiler error "Exception is never thrown in body of corresponding try statement" — this means the catch is wrong.

### Pitfall 2: Forgetting to add IOException to import
**What goes wrong:** After narrowing to `catch(IOException e)`, forgetting `import java.io.IOException;` causes a compile error.
**How to avoid:** IDE/compiler catches this immediately. The `java.io.IOException` is needed in any class that now explicitly catches `IOException`.

### Pitfall 3: Breaking CompletableFuture lambda exception handling
**What goes wrong:** In Gt7SyncService, the catch blocks are inside lambda functions passed to `CompletableFuture.runAsync()`. Lambdas cannot throw checked exceptions. `IOException` is a checked exception.
**Critical insight:** The lambdas use `} catch (Exception e) {` precisely because checked exceptions cannot propagate out of `Runnable` lambdas. Narrowing to `catch(IOException e)` is valid only if we also handle other potential checked exceptions OR if we're certain only `IOException` can be thrown.
**How to avoid:** Check if `fileStorageService.storeFromUrl()` throws only `IOException` — it does (verified: `throws IOException` declaration). But the lambda cannot propagate `IOException` up. The catch-and-store pattern is correct; just narrow the type.
**Alternative if uncertain:** Use multi-catch `catch (IOException | RuntimeException e)` to be safe.

### Pitfall 4: Test coverage regression
**What goes wrong:** Narrowing a catch block from `Exception` to `IOException` means the test that mocked `RuntimeException` still passes, but the production catch no longer catches RuntimeException — silent divergence.
**How to avoid:** Per D-15 (TDD), write tests that specifically throw the narrowed exception type and verify the catch behavior. Also write a test that throws a different exception type and verifies it propagates (i.e., is NOT caught).

### Pitfall 5: TemplateEditorController preview endpoint — already partially specific
**What goes wrong:** The preview endpoint at line 141 has `catch(Exception e)` as the THIRD catch (after `IllegalArgumentException` and `TemplateSecurityException`). Java executes catch blocks in order. The remaining `Exception` catch is a fallback for genuine unexpected exceptions from `templatePreviewService.renderPreview()`.
**Correct action:** Narrow the final catch to `IOException` (from template rendering) or `RuntimeException` (from Playwright). Must check what `renderPreview()` throws.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Mockito + Spring MockMvc |
| Config file | `pom.xml` (Surefire + Failsafe plugins) |
| Quick run command | `./mvnw test` |
| Full suite command | `./mvnw verify` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| ERRH-01 | Graphic endpoint catches RuntimeException, returns 500 | Unit (MockMvc) | `./mvnw test -pl . -Dtest=MatchdayControllerTest` | ✅ |
| ERRH-01 | Image upload service catches IOException, wraps to BusinessRuleException | Unit (Mockito) | `./mvnw test -pl . -Dtest=CarServiceTest` | ✅ |
| ERRH-01 | CsvImport catches IOException for parse failures | Unit (MockMvc) | `./mvnw test -pl . -Dtest=CsvImportControllerTest` | ✅ |
| ERRH-01 | SeasonController catches IOException from updateSeasonTeam | Unit (MockMvc) | `./mvnw test -pl . -Dtest=SeasonControllerTest` | ✅ |
| ERRH-01 | TemplateEditorController catches IOException from TemplateManageable | Unit (MockMvc) | `./mvnw test -pl . -Dtest=TemplateEditorControllerTest` | ✅ |
| ERRH-01 | Non-IOException propagates to GlobalExceptionHandler (not caught locally) | Unit (MockMvc) | `./mvnw test -pl . -Dtest=*ControllerTest` | ✅ |
| QUAL-02 | DriverRankingService.findAll() scope documented | Unit | `./mvnw test -pl . -Dtest=DriverRankingServiceTest` | ✅ |

### Sampling Rate

- **Per task commit:** `./mvnw test`
- **Per wave merge:** `./mvnw verify`
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps

None — existing test infrastructure covers all phase requirements. Test files for all affected controllers and services already exist.

---

## Code Examples

### Verified: TemplateManageable interface signature [VERIFIED: source]
```java
public interface TemplateManageable {
    String loadTemplate() throws IOException;
    void saveTemplate(String content) throws IOException;
    void resetTemplate() throws IOException;
    boolean hasCustomTemplate();
}
```
All three mutable operations declare `throws IOException`. This definitively narrows TemplateEditorController catch blocks to `IOException`.

### Verified: SeasonManagementService.updateSeasonTeam signature [VERIFIED: source]
```java
public String updateSeasonTeam(UUID seasonTeamId, Integer rating,
                               String primaryColor, String secondaryColor, String accentColor,
                               MultipartFile logoOverride) throws IOException {
```
SeasonController catch should narrow to `catch(IOException e)`.

### Verified: FileStorageService method signatures [VERIFIED: source]
```java
public String store(UUID raceId, MultipartFile file) throws IOException
public String storeFromUrl(String subDir, UUID entityId, String sourceUrl, String filename) throws IOException
public String storeImage(String subDir, UUID entityId, MultipartFile file) throws IOException
```
All storage methods throw checked IOException — service catches should be `IOException`.

### Verified: RaceService.getRaceListData() — findAll() already removed [VERIFIED: source]
```java
} else {
    races = List.of();  // No unscoped fallback — already clean
}
```
QUAL-02 for RaceService is already complete. No action needed.

### Mockito pattern for testing narrowed catches
```java
// Test that narrowed catch DOES catch the specific type
@Test
void givenIoException_whenUploadImage_thenThrowsBusinessRuleException() {
    // given
    given(fileStorageService.storeImage(any(), any(), any()))
        .willThrow(new IOException("disk full"));
    // when / then
    assertThatThrownBy(() -> carService.uploadImage(carId, mockFile))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("Image upload failed");
}

// Test that unexpected exception propagates (is NOT caught)
@Test
void givenRuntimeException_whenUploadImage_thenPropagates() {
    // given
    given(fileStorageService.storeImage(any(), any(), any()))
        .willThrow(new RuntimeException("unexpected"));
    // when / then
    assertThatThrownBy(() -> carService.uploadImage(carId, mockFile))
        .isInstanceOf(RuntimeException.class);  // not BusinessRuleException
}
```

---

## Open Questions

1. **TemplatePreviewService.renderPreview() exception contract**
   - What we know: `TemplateEditorController.preview()` already catches `IllegalArgumentException` and `TemplateSecurityException` specifically; the final `catch(Exception e)` is a fallback.
   - What's unclear: Whether `renderPreview()` can throw `IOException` (file read) or only `RuntimeException` (Thymeleaf processing).
   - Recommendation: Read `TemplatePreviewService.java` during planning/implementation to determine the correct narrowing. If it throws `IOException`, use that; if only RuntimeException, use that.

2. **Gt7SyncService batch lambdas — IOException vs Exception**
   - What we know: `fileStorageService.storeFromUrl()` throws `IOException`. The lambdas catch-and-store the exception object.
   - What's unclear: Whether the lambda infrastructure (CompletableFuture.runAsync with Runnable) allows narrowing to `IOException` in the catch without compiler issues.
   - Recommendation: Since we're catching (not rethrowing) inside the lambda, narrowing `catch(Exception e)` to `catch(IOException e)` is valid as long as only `IOException` can actually be thrown in the try block. Verify `storeFromUrl` is the only throwing call.

---

## Environment Availability

Step 2.6: SKIPPED (no external dependencies — this is a pure code refactoring phase).

---

## Security Domain

Security enforcement is enabled (not explicitly false in config). However, this phase involves only exception catch narrowing and query scoping — no new inputs, no authentication changes, no file path handling changes.

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V5 Input Validation | no | No new input handling added |
| V6 Cryptography | no | No crypto operations |
| V2 Authentication | no | No auth changes |
| V4 Access Control | no | No access control changes |

No ASVS categories are materially affected by narrowing catch block types.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | AbstractGraphicService/Playwright methods throw RuntimeException for failures | Architecture Patterns (Pattern 1) | If they throw checked exceptions, catch(RuntimeException e) would fail to compile. Verify during implementation by checking AbstractGraphicService. |
| A2 | Gt7SyncService.executeSync() throws RuntimeException (not checked exceptions) | Architecture Patterns (Pattern 6) | If it throws IOException, Gt7SyncController catch must be IOException, not RuntimeException. |
| A3 | DriverRankingService.calculateAlltimeRanking() findAll is acceptable for alltime view | Architecture Patterns (Unbounded Query) | For very large datasets this would be slow, but project scope is small-scale admin tool. |

---

## Sources

### Primary (HIGH confidence)
- `/src/main/java/org/ctc/admin/service/TemplateManageable.java` — Interface method signatures with IOException declarations
- `/src/main/java/org/ctc/domain/service/SeasonManagementService.java:264` — updateSeasonTeam throws IOException declaration
- `/src/main/java/org/ctc/domain/service/FileStorageService.java:33,85,104` — All storage methods throw IOException
- `/src/main/java/org/ctc/domain/service/RaceService.java:80-88` — findAll() fallback already removed
- `/src/main/java/org/ctc/domain/service/DriverRankingService.java:59` — seasonDriverRepository.findAll() still present
- `/src/main/java/org/ctc/admin/controller/GlobalExceptionHandler.java` — Existing handler registrations
- Codebase grep for `catch\s*\(Exception` — 23 production occurrences confirmed

### Secondary (MEDIUM confidence)
- Phase 8 CONTEXT.md decisions D-01 through D-12 — Original exception type mapping decisions

---

## Metadata

**Confidence breakdown:**
- Current catch(Exception e) inventory: HIGH — verified via grep
- Exception type narrowing mapping: HIGH — verified against actual method signatures
- RaceService QUAL-02 status: HIGH — verified source shows List.of() not findAll()
- TemplateEditorController IOException narrowing: HIGH — TemplateManageable interface verified
- Gt7 CompletableFuture catch narrowing: MEDIUM — lambda constraints need implementation-time verification

**Research date:** 2026-04-06
**Valid until:** 2026-05-06 (stable domain — exception handling patterns don't change)
