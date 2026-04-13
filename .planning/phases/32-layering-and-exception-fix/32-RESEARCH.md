# Phase 32: Layering and Exception Fix - Research

**Researched:** 2026-04-14
**Domain:** Java package layering, domain exception hierarchy, Spring MVC exception handling
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** RaceGraphicService imports 4 admin graphic services (LineupGraphicService, OverlayGraphicService, ResultsGraphicService, SettingsGraphicService). Since RaceGraphicService is purely a facade that delegates to admin graphic services, move RaceGraphicService from `org.ctc.domain.service` to `org.ctc.admin.service`. This is the simplest fix — no interface abstraction needed.
- **D-02:** RaceService imports TeamCardService from admin. Extract the TeamCardService interaction into the controller layer (caller) or introduce a domain interface. Preferred: move the TeamCardService call out of RaceService into the controller that coordinates the race save + card regeneration.
- **D-03:** After moving/fixing, verify with: `grep -r "import org.ctc.admin" src/main/java/org/ctc/domain/` returns zero results.
- **D-04:** MatchdayService.java line 132 uses `ResponseStatusException(HttpStatus.NOT_FOUND)` — replace with `EntityNotFoundException` (already exists in `org.ctc.domain.exception`).
- **D-05:** MatchdayService.java line 140 uses `ResponseStatusException(HttpStatus.CONFLICT)` — replace with `BusinessRuleException` (already exists in `org.ctc.domain.exception`).
- **D-06:** GlobalExceptionHandler already handles both `EntityNotFoundException` (maps to 404) and `BusinessRuleException` (maps to 409). Verify the CONFLICT → BusinessRuleException mapping produces the correct HTTP status. If GlobalExceptionHandler maps BusinessRuleException to 400 but the original was 409, add a specific handler or use the existing exception with a descriptive message.
- **D-07:** Remove the `ResponseStatusException` import and `handleResponseStatus` handler from GlobalExceptionHandler only if no other domain service uses it. Otherwise leave the handler as a safety net for any remaining usages in controllers.

### Claude's Discretion
- Test strategy: Unit tests for the exception replacements, compile-time verification for the import fix. No new E2E tests needed.
- Whether to extract an interface for TeamCardService or simply move the call to the controller — choose whichever is simpler and maintains the existing behavior.

### Deferred Ideas (OUT OF SCOPE)
None — phase scope is narrow and well-defined.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ARCH-01 | Domain services do not import from admin service layer (layering fix or relocation) | Confirmed: 2 files violate this — RaceGraphicService.java (4 admin imports) and RaceService.java (1 admin import). Fix via move + decoupling. |
| ARCH-02 | Domain services use domain exceptions instead of HTTP-specific ResponseStatusException | Confirmed: MatchdayService.java lines 132 and 140 are the only violations. Both replacements have correct domain exceptions available. |
</phase_requirements>

---

## Summary

Phase 32 fixes two layering violations in the domain service layer: import of admin services from `org.ctc.domain.service`, and use of `ResponseStatusException` (a Spring Web/HTTP concern) inside domain services.

**ARCH-01 violations (2 files, 5 imports total):**
- `RaceGraphicService` is in `org.ctc.domain.service` but imports all 4 of its dependencies from `org.ctc.admin.service` (LineupGraphicService, OverlayGraphicService, ResultsGraphicService, SettingsGraphicService). It is a pure facade with no domain logic. Fix: move the file to `org.ctc.admin.service`.
- `RaceService` imports `TeamCardService` from `org.ctc.admin.service`. The usage is two `teamCardService.cardExists(st)` calls inside `getRaceDetailData()`, a query-only method. Fix: move the card-existence check to the controller layer where `TeamCardService` already belongs.

**ARCH-02 violation (1 file, 2 usages):**
- `MatchdayService.createInline()` throws `ResponseStatusException(HttpStatus.NOT_FOUND)` (line 132) and `ResponseStatusException(HttpStatus.CONFLICT)` (line 140). Both domain exception replacements already exist and `GlobalExceptionHandler` maps them to the correct HTTP status codes: `EntityNotFoundException` → 404, `BusinessRuleException` → 409.

**Primary recommendation:** Move RaceGraphicService to admin.service, remove TeamCardService from RaceService and compute card flags in RaceController, replace both ResponseStatusExceptions in MatchdayService with domain exceptions. All three changes are independent and can be planned as separate tasks.

---

## Standard Stack

No new libraries are needed. [VERIFIED: codebase grep]

| Component | Location | Role |
|-----------|----------|------|
| `EntityNotFoundException` | `org.ctc.domain.exception` | Domain 404 — already used throughout codebase |
| `BusinessRuleException` | `org.ctc.domain.exception` | Domain 409 — already used throughout codebase |
| `ValidationException` | `org.ctc.domain.exception` | Domain 400 — available but not needed for this phase |
| `GlobalExceptionHandler` | `org.ctc.admin.controller` | Maps domain exceptions → HTTP responses via `@ControllerAdvice` |

---

## Architecture Patterns

### Current Violation Map

```
org.ctc.domain.service.RaceGraphicService
  imports: org.ctc.admin.service.LineupGraphicService    <-- ARCH-01 violation
  imports: org.ctc.admin.service.OverlayGraphicService   <-- ARCH-01 violation
  imports: org.ctc.admin.service.ResultsGraphicService   <-- ARCH-01 violation
  imports: org.ctc.admin.service.SettingsGraphicService  <-- ARCH-01 violation

org.ctc.domain.service.RaceService
  imports: org.ctc.admin.service.TeamCardService         <-- ARCH-01 violation

org.ctc.domain.service.MatchdayService.createInline()
  throws ResponseStatusException(HttpStatus.NOT_FOUND)   <-- ARCH-02 violation (line 132)
  throws ResponseStatusException(HttpStatus.CONFLICT)    <-- ARCH-02 violation (line 140)
```

### Fix 1: Move RaceGraphicService (D-01)

**What:** Move `RaceGraphicService.java` from `org.ctc.domain.service` to `org.ctc.admin.service`.

**Why it works:** RaceGraphicService contains zero domain logic. It only delegates to admin graphic services (LineupGraphicService, OverlayGraphicService, ResultsGraphicService, SettingsGraphicService) and persists the resulting attachment. After the move, all its imports are within the same package or the domain layer (RaceRepository, RaceAttachmentRepository, Race, RaceAttachment — all in `org.ctc.domain.*`), which is a legal dependency direction.

**Caller impact:** `RaceController` (in `org.ctc.admin.controller`) injects `RaceGraphicService`. The import in RaceController changes from `org.ctc.domain.service.RaceGraphicService` to `org.ctc.admin.service.RaceGraphicService`. No behavior change.

**Test impact:** `RaceGraphicServiceTest` is currently in `src/test/java/org/ctc/domain/service/`. After the move, the test must be relocated to `src/test/java/org/ctc/admin/service/` and its `package` declaration updated. The test class itself imports `LineupGraphicService`, `OverlayGraphicService`, etc. from `org.ctc.admin.service` — these imports remain valid.

### Fix 2: Decouple TeamCardService from RaceService (D-02)

**What:** Remove `TeamCardService` injection from `RaceService`. Move the two `teamCardService.cardExists(st)` calls from `RaceService.getRaceDetailData()` into `RaceController.detail()`.

**Current usage in RaceService (lines 137-141):**
```java
// inside getRaceDetailData()
boolean hasHomeCard = false;
boolean hasAwayCard = false;
if (race.getMatch() != null && race.getHomeTeam() != null && race.getAwayTeam() != null) {
    var season = race.getMatchday().getSeason();
    hasHomeCard = seasonTeamRepository.findBySeasonIdAndTeamId(season.getId(), race.getHomeTeam().getId())
            .map(st -> teamCardService.cardExists(st)).orElse(false);
    hasAwayCard = seasonTeamRepository.findBySeasonIdAndTeamId(season.getId(), race.getAwayTeam().getId())
            .map(st -> teamCardService.cardExists(st)).orElse(false);
}
```

**Two sub-decisions for D-02:**

Option A — Controller computes card flags, passes them to service:
- `getRaceDetailData()` receives `boolean hasHomeCard, boolean hasAwayCard` as parameters
- `RaceController.detail()` calls `teamCardService.cardExists(st)` itself before calling `getRaceDetailData()`
- Requires `SeasonTeamRepository` access from the controller (via service), or passing `SeasonTeam` objects

Option B — Return partial data from RaceService, controller enriches:
- `RaceService.getRaceDetailData()` returns a record that exposes the `SeasonTeam` objects (or their IDs) needed for card checks
- Controller uses these to call `teamCardService.cardExists()` and builds the final boolean flags

Option C (simplest) — Add a helper method to RaceService that does NOT inject TeamCardService:
- Extract card-flag logic from `getRaceDetailData()` into two new method parameters (`hasHomeCard`, `hasAwayCard`)
- Controller calls `seasonTeamRepository` (via a new `RaceService` helper method that returns `SeasonTeam` objects) and `teamCardService.cardExists()` separately
- Then passes the results to a refactored `getRaceDetailData(UUID raceId, boolean hasHomeCard, boolean hasAwayCard)`

**Recommended approach (Option A / C merged — simplest):**
Refactor `getRaceDetailData` to accept `boolean hasHomeCard, boolean hasAwayCard` as parameters. Add a new method `getRaceSeasonTeams(UUID raceId)` (or inline in controller) that returns the two `SeasonTeam` objects needed for card checks. The controller calls `teamCardService.cardExists()` and passes the booleans.

**Test impact:** `RaceServiceTest` currently mocks `TeamCardService` and has it in `@Mock`. After the fix, `teamCardService` mock is removed from `RaceServiceTest`. The `getRaceDetailData` test that checks `canGenerateLineup()` depends on the card flags — the test signature must change to pass `hasHomeCard=false, hasAwayCard=false` (or similar). The test currently expects `canGenerateLineup()` to be false because cards are missing, which remains correct.

### Fix 3: Replace ResponseStatusExceptions in MatchdayService (D-04, D-05)

**Current code (lines 131-141 of MatchdayService.java):**
```java
var season = seasonRepository.findById(seasonId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Season not found: " + seasonId));
...
if (duplicateLabel) {
    throw new ResponseStatusException(HttpStatus.CONFLICT,
            "Matchday label already exists in this season: " + label);
}
```

**Replacement:**
```java
var season = seasonRepository.findById(seasonId)
        .orElseThrow(() -> new EntityNotFoundException("Season", seasonId));
...
if (duplicateLabel) {
    throw new BusinessRuleException("Matchday label already exists in this season: " + label);
}
```

**HTTP status preservation (VERIFIED):**
- `EntityNotFoundException` → GlobalExceptionHandler maps to `HttpStatus.NOT_FOUND` (404) [VERIFIED: GlobalExceptionHandler.java line 29-32]
- `BusinessRuleException` → GlobalExceptionHandler maps to `HttpStatus.CONFLICT` (409) [VERIFIED: GlobalExceptionHandler.java line 46-49]

Both replacements preserve the exact HTTP status codes previously produced by the `ResponseStatusException` usages.

**JavaScript caller compatibility (VERIFIED):**
The `create-inline` endpoint is called via AJAX from `import.html`. The JavaScript checks `r.status === 409` on line 246 to detect the duplicate label case. Since `BusinessRuleException` produces HTTP 409 via GlobalExceptionHandler, this check remains correct. [VERIFIED: import.html lines 244-247]

Note: GlobalExceptionHandler's `buildErrorView()` returns a `ModelAndView("admin/error")` — an HTML response body. For the `@ResponseBody` JSON endpoint `createInline`, this means the 409 response will have an HTML body rather than JSON. The JavaScript only checks the status code and ignores the body, so this is acceptable behavior. [VERIFIED: import.html JS code]

**Test impact:** `MatchdayServiceTest` imports `ResponseStatusException` and asserts:
```java
assertThatThrownBy(() -> service.createInline(season.getId(), "Existing"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("already exists");
```
After the fix, this test must be updated to assert `BusinessRuleException`:
```java
assertThatThrownBy(() -> service.createInline(season.getId(), "Existing"))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("already exists");
```
A new test for the NOT_FOUND case should assert `EntityNotFoundException`.

### GlobalExceptionHandler Cleanup (D-07)

**Scope question:** After all fixes, is `ResponseStatusException` still used anywhere in the codebase?

**Findings:** [VERIFIED: grep of entire domain layer]
- After fixing MatchdayService, no remaining usages of `ResponseStatusException` exist in `org.ctc.domain.*`.
- `GlobalExceptionHandler.handleResponseStatus()` re-throws `ResponseStatusException` for Spring to handle natively. This handler exists as a safety net for any Spring infrastructure exceptions that produce `ResponseStatusException` internally (e.g., method-not-allowed, missing parameters).
- **Recommendation (D-07):** Do NOT remove `handleResponseStatus` from GlobalExceptionHandler. Spring itself generates `ResponseStatusException` internally for request validation failures (e.g., missing required `@RequestParam`). Removing the handler would cause these to fall through to the `Exception` catch-all handler and produce a 500 instead of the correct 4xx. Leave it in place.
- The `import org.springframework.web.server.ResponseStatusException` in GlobalExceptionHandler must stay.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead |
|---------|-------------|-------------|
| Domain exception types | Custom exception hierarchy | `EntityNotFoundException`, `BusinessRuleException`, `ValidationException` already in `org.ctc.domain.exception` |
| Exception-to-HTTP mapping | Per-controller try/catch with status codes | `GlobalExceptionHandler` @ControllerAdvice — already maps domain exceptions correctly |
| HTTP status code selection | Hardcoded HttpStatus values in services | Implicit via exception type — EntityNotFoundException = 404, BusinessRuleException = 409 |

---

## Common Pitfalls

### Pitfall 1: Test class not relocated after RaceGraphicService move
**What goes wrong:** `RaceGraphicServiceTest` stays in `src/test/java/org/ctc/domain/service/` after the service moves to `admin.service`. The test still compiles (Mockito doesn't care about package) but the organizational intent is violated and coverage reports may attribute the test incorrectly.
**How to avoid:** Move the test file to `src/test/java/org/ctc/admin/service/` and update its `package` declaration in the same task as the service move.

### Pitfall 2: RaceController import not updated after RaceGraphicService move
**What goes wrong:** `RaceController` imports `org.ctc.domain.service.RaceGraphicService`. After the move, this import breaks compilation.
**How to avoid:** Update `RaceController` import in the same task as the service move. Search for all usages: `grep -r "RaceGraphicService" src/main/java/` before committing.

### Pitfall 3: getRaceDetailData test broken by signature change
**What goes wrong:** After removing `TeamCardService` from `RaceService`, the method signature of `getRaceDetailData` changes. Existing test stubs (`when(seasonTeamRepository...)...map(st -> teamCardService.cardExists(st))`) become unused and Mockito strict mode flags them as unnecessary stubs.
**How to avoid:** Remove the `@Mock TeamCardService teamCardService` and the `seasonTeamRepository.findBySeasonId...` stubs from `RaceServiceTest` when updating test to the new signature.

### Pitfall 4: CONFLICT status assumed to be 400 from BusinessRuleException
**What goes wrong:** Developer assumes `BusinessRuleException` maps to 400 (BAD_REQUEST), not 409 (CONFLICT), and concludes the replacement breaks the JavaScript caller.
**How to avoid:** Confirmed: GlobalExceptionHandler line 47-49 maps `BusinessRuleException` to `HttpStatus.CONFLICT` (409), matching the original `ResponseStatusException(HttpStatus.CONFLICT)`. [VERIFIED: GlobalExceptionHandler.java]

### Pitfall 5: EntityNotFoundException constructor signature mismatch
**What goes wrong:** Developer constructs `EntityNotFoundException("Season not found: " + seasonId)` using the single-string constructor that doesn't exist — the only constructor takes `(String entityType, Object entityId)`.
**How to avoid:** Use `new EntityNotFoundException("Season", seasonId)` which produces message "Season not found with id: {seasonId}". [VERIFIED: EntityNotFoundException.java]

---

## Code Examples

### EntityNotFoundException (verified constructor signature)
```java
// Source: src/main/java/org/ctc/domain/exception/EntityNotFoundException.java
new EntityNotFoundException("Season", seasonId)
// Produces message: "Season not found with id: <seasonId>"
```

### BusinessRuleException (verified constructor signature)
```java
// Source: src/main/java/org/ctc/domain/exception/BusinessRuleException.java
new BusinessRuleException("Matchday label already exists in this season: " + label)
// Produces message as provided
```

### GlobalExceptionHandler mappings (verified status codes)
```java
// Source: src/main/java/org/ctc/admin/controller/GlobalExceptionHandler.java
// EntityNotFoundException  -> HttpStatus.NOT_FOUND (404)    line 29-32
// BusinessRuleException    -> HttpStatus.CONFLICT  (409)    line 46-49
// ValidationException      -> HttpStatus.BAD_REQUEST (400)  line 40-43
```

### RaceGraphicService target package declaration
```java
// After move: src/main/java/org/ctc/admin/service/RaceGraphicService.java
package org.ctc.admin.service;
// All other content unchanged
```

### RaceController updated import
```java
// Before: import org.ctc.domain.service.RaceGraphicService;
// After:  import org.ctc.admin.service.RaceGraphicService;
```

### MatchdayService createInline after fix
```java
// Source: src/main/java/org/ctc/domain/service/MatchdayService.java (proposed)
@Transactional
public MatchdayData createInline(UUID seasonId, String label) {
    var season = seasonRepository.findById(seasonId)
            .orElseThrow(() -> new EntityNotFoundException("Season", seasonId));  // replaces NOT_FOUND ResponseStatusException

    var existingMatchdays = matchdayRepository.findBySeasonIdOrderBySortIndexAsc(season.getId());

    boolean duplicateLabel = existingMatchdays.stream()
            .anyMatch(md -> md.getLabel().equals(label));
    if (duplicateLabel) {
        throw new BusinessRuleException("Matchday label already exists in this season: " + label);  // replaces CONFLICT ResponseStatusException
    }
    // ... remainder unchanged
}
```

### MatchdayServiceTest updated assertion
```java
// Before:
assertThatThrownBy(() -> service.createInline(season.getId(), "Existing"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("already exists");

// After:
assertThatThrownBy(() -> service.createInline(season.getId(), "Existing"))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("already exists");
```

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Mockito via MockitoExtension |
| Config file | `pom.xml` (Surefire + Failsafe plugins) |
| Quick run command | `./mvnw test -pl . -Dtest=RaceGraphicServiceTest,RaceServiceTest,MatchdayServiceTest` |
| Full suite command | `./mvnw verify` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| ARCH-01 | RaceGraphicService has no admin imports | compile-time | `./mvnw compile` | N/A (structural) |
| ARCH-01 | RaceService has no admin imports | compile-time | `./mvnw compile` | N/A (structural) |
| ARCH-01 | grep verification after fix | manual verify | `grep -r "import org.ctc.admin" src/main/java/org/ctc/domain/` | N/A |
| ARCH-02 | MatchdayService.createInline throws EntityNotFoundException on missing season | unit | existing test needs new case | ❌ Wave 0 gap |
| ARCH-02 | MatchdayService.createInline throws BusinessRuleException on duplicate label | unit | `./mvnw test -Dtest=MatchdayServiceTest#givenDuplicateLabel_whenCreateInline_thenThrowsException` | ✅ needs update |

### Sampling Rate
- **Per task commit:** `./mvnw test -Dtest=RaceGraphicServiceTest,RaceServiceTest,MatchdayServiceTest`
- **Per wave merge:** `./mvnw verify`
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `MatchdayServiceTest` — add `givenMissingSeason_whenCreateInline_thenThrowsEntityNotFoundException()` to cover the NOT_FOUND replacement (REQ ARCH-02)
- [ ] Update existing `givenDuplicateLabel_whenCreateInline_thenThrowsException` test to assert `BusinessRuleException` instead of `ResponseStatusException`
- [ ] `RaceGraphicServiceTest` — update `package` declaration after file move
- [ ] `RaceServiceTest` — remove `@Mock TeamCardService teamCardService` and update stubs/assertions related to card flags after signature change

---

## Security Domain

> No security-sensitive changes in this phase. This is a pure architecture/exception refactoring with no changes to authentication, authorization, input validation, or file handling.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | No other files import `org.ctc.domain.service.RaceGraphicService` besides RaceController | Fix 1 | Compilation failure — additional import updates needed |
| A2 | JavaScript in import.html only checks `r.status` (not response body) for the 409 case | Fix 3 / Pitfall 4 | If body is parsed, 409 HTML body from GlobalExceptionHandler could cause a parse error in JS |

A1 is LOW risk: [VERIFIED by grep] RaceController is the only caller. A2 is LOW risk: [VERIFIED: import.html lines 244-247] JavaScript uses `throw new Error(...)` on non-ok status, does not parse body for error cases.

---

## Open Questions

1. **D-02 exact refactor shape for getRaceDetailData**
   - What we know: `teamCardService.cardExists(st)` is called twice in `getRaceDetailData()` with results stored as `hasHomeCard`/`hasAwayCard` booleans. These feed into the `canGenerateLineup`, `canGenerateResults`, `canGenerateSettings` flags in the returned `RaceDetailData` record.
   - What's unclear: Whether to pass `hasHomeCard`/`hasAwayCard` as parameters to `getRaceDetailData`, or return `SeasonTeam` objects from the service and compute flags in the controller, or introduce a separate enrichment step.
   - Recommendation: Pass booleans as parameters to `getRaceDetailData(UUID raceId, boolean hasHomeCard, boolean hasAwayCard)`. The controller calls `seasonTeamRepository` (via RaceService helper or directly) and `teamCardService.cardExists()`, then delegates to `getRaceDetailData`. This keeps RaceService free of TeamCardService while minimizing the change surface. Controller already injects `seasonTeamRepository` indirectly via services — but to avoid repository injection in controller, add a `getSeasonTeamsForRace(UUID raceId)` method to RaceService that returns the two `SeasonTeam` objects (or `Optional<SeasonTeam>` pair).

---

## Sources

### Primary (HIGH confidence)
- [VERIFIED: codebase] `src/main/java/org/ctc/domain/service/RaceGraphicService.java` — confirmed 4 admin imports, all delegation
- [VERIFIED: codebase] `src/main/java/org/ctc/domain/service/RaceService.java` — confirmed 1 admin import (TeamCardService), lines 138-140
- [VERIFIED: codebase] `src/main/java/org/ctc/domain/service/MatchdayService.java` — confirmed ResponseStatusException at lines 132 and 140
- [VERIFIED: codebase] `src/main/java/org/ctc/admin/controller/GlobalExceptionHandler.java` — confirmed BusinessRuleException → 409, EntityNotFoundException → 404
- [VERIFIED: codebase] `src/main/java/org/ctc/domain/exception/EntityNotFoundException.java` — constructor `(String entityType, Object entityId)`
- [VERIFIED: codebase] `src/main/java/org/ctc/domain/exception/BusinessRuleException.java` — constructor `(String message)`
- [VERIFIED: codebase] `src/main/java/org/ctc/admin/controller/RaceController.java` — sole caller of RaceGraphicService
- [VERIFIED: codebase] `src/main/resources/templates/admin/import.html` — JavaScript only checks `r.status === 409`, not response body
- [VERIFIED: grep] No other domain services use ResponseStatusException or import from `org.ctc.admin.*`

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all components verified in codebase
- Architecture: HIGH — all violations and callers verified by grep + file reads
- Pitfalls: HIGH — derived from direct code inspection, not assumptions
- Exception mappings: HIGH — verified in GlobalExceptionHandler source

**Research date:** 2026-04-14
**Valid until:** Phase scope is narrow and self-contained — valid until code changes
