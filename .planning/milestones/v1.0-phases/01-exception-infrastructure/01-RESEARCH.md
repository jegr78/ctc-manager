# Phase 1: Exception Infrastructure - Research

**Researched:** 2026-04-03
**Domain:** Spring Boot 4.x @ControllerAdvice, Thymeleaf error pages, exception hierarchy
**Confidence:** HIGH

## Summary

Phase 1 introduces centralized exception handling for the CTC Manager admin application. The current codebase has 103 bare `.orElseThrow()` calls that throw `NoSuchElementException` without any message, plus 33 with lambdas throwing `IllegalArgumentException`/`IllegalStateException` with messages. There is no `@ExceptionHandler` anywhere in the codebase -- only one `@ControllerAdvice` (`GlobalModelAdvice`) that provides `appVersion` as a model attribute.

An existing `error.html` template exists but uses a standalone layout (no sidebar, old navbar pattern). It needs to be replaced with a template using the standard admin layout fragment pattern. The dev profile already configures `server.error.include-stacktrace: always` and `server.error.include-message: always`, while prod uses Spring Boot defaults (no stacktrace, no message exposure).

**Primary recommendation:** Create a new `GlobalExceptionHandler` class with `@ControllerAdvice` + `@ExceptionHandler` methods, introduce `EntityNotFoundException` in `org.ctc.domain.exception`, replace all 103 bare `.orElseThrow()` with `EntityNotFoundException`, and redesign `error.html` to use the admin layout.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Fehlerseite wird im bestehenden Admin-Layout gerendert (Sidebar, Header) -- kein Standalone-Template
- **D-06:** Eigene Exception-Klassen einfuehren (nicht Standard-Java-Exceptions mit Messages)
- **D-07:** 3-5 Exception-Typen pro Concern: EntityNotFoundException, ValidationException, BusinessRuleException + ggf. weitere passend zum Domain
- **D-08:** Alle Exception-Klassen in eigenem Package (z.B. `org.ctc.domain.exception`)
- **D-09:** Alle 50+ .orElseThrow() Aufrufe mit EntityNotFoundException und aussagekraeftiger Message versehen
- **D-11:** Exception-Typ fuer orElseThrow -- EntityNotFoundException empfohlen

### Claude's Discretion
- **D-02:** Dev-Profil darf mehr Details zeigen (Exception-Typ, Message), Prod nur freundliche Meldung
- **D-03:** HTTP-Statuscodes abdecken -- mindestens 404 + 500, 403 vorbereiten wenn sinnvoll fuer Phase 5 (Security)
- **D-04:** Neuer GlobalExceptionHandler.java oder GlobalModelAdvice erweitern
- **D-05:** Handler faengt nur unbehandelte Exceptions. Bestehende Controller-Flash-Message try-catches bleiben in Phase 1 erhalten
- **D-10:** Message-Format (Entity-Typ + ID ist Minimum)
- Error-Page Template-Design (innerhalb Admin-Layout)
- Ob 403-Seite schon vorbereitet wird

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| EXCP-01 | Global Exception Handler mit @ControllerAdvice faengt unbehandelte Exceptions (NoSuchElementException, EntityNotFoundException) und zeigt benutzerfreundliche Fehlerseite | GlobalExceptionHandler pattern, error.html redesign, @ExceptionHandler method signatures |
| EXCP-02 | Alle 50+ .orElseThrow() Aufrufe mit aussagekraeftigen Exception-Messages (Entity-Typ + ID) | 103 bare orElseThrow() + 33 with lambdas across 21 files; EntityNotFoundException class design |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

- **TDD:** Tests zuerst schreiben, dann Implementierung. Red -> Green -> Refactor
- **Controller duenn halten:** Keine Business-Logik in Controllern
- **Keine Fallback-Berechnungen:** Datenmodell und Service-Architektur analysieren statt Workarounds
- **Testdaten komplett isolieren:** E2E-Testdaten mit Test-Prefix
- **Git-Workflow:** Feature-Branch, PR mit `gh pr create --assignee jegr78`, `gh pr merge --squash`
- **Conventional Commits:** `feat:`, `fix:`, `refactor:`, etc.
- **Coverage:** 80% Minimum (JaCoCo), Build bricht bei Unterschreitung
- **Flyway:** Bestehende Migrationen nicht aendern (keine Schema-Aenderungen in dieser Phase noetig)
- **OSIV aktiv:** Entities koennen in Templates lazy geladen werden

## Architecture Patterns

### Recommended Project Structure

```
src/main/java/org/ctc/
├── domain/
│   ├── exception/                   # NEW: Exception hierarchy
│   │   ├── EntityNotFoundException.java
│   │   ├── ValidationException.java
│   │   └── BusinessRuleException.java
│   ├── model/
│   ├── repository/
│   └── service/
├── admin/
│   ├── controller/
│   │   ├── GlobalModelAdvice.java   # UNCHANGED: only appVersion
│   │   └── GlobalExceptionHandler.java  # NEW: @ControllerAdvice for exceptions
│   └── dto/
src/main/resources/templates/
├── admin/
│   ├── layout.html                  # UNCHANGED
│   └── error.html                   # NEW: error page within admin layout
├── error.html                       # REPLACED: becomes fallback for non-admin errors
```

### Pattern 1: Separate @ControllerAdvice for Exception Handling

**What:** A dedicated `GlobalExceptionHandler` class annotated with `@ControllerAdvice` and `@ExceptionHandler` methods. Separate from `GlobalModelAdvice` which handles model attributes.

**Why separate (not extending GlobalModelAdvice):**
- Single Responsibility: GlobalModelAdvice provides model attributes, GlobalExceptionHandler handles exceptions
- GlobalModelAdvice uses `@ModelAttribute`, GlobalExceptionHandler uses `@ExceptionHandler` -- different concerns
- Easier to test independently
- Clearer ownership as more exception types are added in later phases

**Example:**
```java
package org.ctc.admin.controller;

import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.exception.ValidationException;
import org.ctc.domain.exception.BusinessRuleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
public class GlobalExceptionHandler {

    private final Environment environment;

    public GlobalExceptionHandler(Environment environment) {
        this.environment = environment;
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ModelAndView handleEntityNotFound(EntityNotFoundException ex) {
        return buildErrorView(HttpStatus.NOT_FOUND, "Not Found", ex);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ModelAndView handleNoSuchElement(NoSuchElementException ex) {
        return buildErrorView(HttpStatus.NOT_FOUND, "Not Found", ex);
    }

    @ExceptionHandler(ValidationException.class)
    public ModelAndView handleValidation(ValidationException ex) {
        return buildErrorView(HttpStatus.BAD_REQUEST, "Validation Error", ex);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ModelAndView handleBusinessRule(BusinessRuleException ex) {
        return buildErrorView(HttpStatus.CONFLICT, "Business Rule Violation", ex);
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView handleGeneral(Exception ex) {
        return buildErrorView(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Error", ex);
    }

    private ModelAndView buildErrorView(HttpStatus status, String title, Exception ex) {
        var mav = new ModelAndView("admin/error");
        mav.setStatus(status);
        mav.addObject("status", status.value());
        mav.addObject("error", title);
        mav.addObject("message", ex.getMessage());
        boolean isDev = environment.matchesProfiles("dev");
        mav.addObject("showDetails", isDev);
        if (isDev) {
            mav.addObject("exceptionType", ex.getClass().getSimpleName());
        }
        return mav;
    }
}
```

### Pattern 2: Custom Exception Hierarchy

**What:** Domain-specific exceptions in `org.ctc.domain.exception` package.

**Design:**
```java
package org.ctc.domain.exception;

public class EntityNotFoundException extends RuntimeException {
    private final String entityType;
    private final Object entityId;

    public EntityNotFoundException(String entityType, Object entityId) {
        super(entityType + " not found with id: " + entityId);
        this.entityType = entityType;
        this.entityId = entityId;
    }

    // Getters for entityType and entityId
}

public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}

public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
```

**Why RuntimeException (not checked):** Spring's `@ExceptionHandler` works with both, but RuntimeException is the standard for Spring applications. No need to declare throws in every service method signature. Consistent with Spring's own `DataAccessException` hierarchy.

### Pattern 3: orElseThrow Migration Pattern

**What:** Replace bare `.orElseThrow()` with typed `EntityNotFoundException`.

**Before:**
```java
var season = seasonRepository.findById(seasonId).orElseThrow();
```

**After:**
```java
var season = seasonRepository.findById(seasonId)
    .orElseThrow(() -> new EntityNotFoundException("Season", seasonId));
```

**For the 33 existing lambda-style calls** (currently throwing `IllegalArgumentException`):
```java
// Before:
.orElseThrow(() -> new IllegalArgumentException("Season not found: " + seasonId));
// After:
.orElseThrow(() -> new EntityNotFoundException("Season", seasonId));
```

### Pattern 4: Error Template within Admin Layout

**What:** New `templates/admin/error.html` using the layout fragment pattern.

**Key design:**
```html
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{admin/layout :: layout('Error', ~{::content})}">
<div th:fragment="content">
    <div class="card">
        <h1>
            <span th:text="${status}">500</span> -- <span th:text="${error}">Error</span>
        </h1>
        <p th:text="${message}">Something went wrong</p>
        <!-- Dev details (only shown when showDetails=true) -->
        <div th:if="${showDetails}">
            <span th:text="${exceptionType}"></span>
        </div>
        <div class="actions">
            <a href="javascript:history.back()" class="btn btn-secondary">Back</a>
            <a th:href="@{/admin/seasons}" class="btn btn-primary">Home</a>
        </div>
    </div>
</div>
</html>
```

**Critical:** The admin layout already renders `successMessage` and `errorMessage` flash attributes (lines 61-62 of layout.html). The error page uses different model attributes (`status`, `error`, `message`), so there is no collision with flash messages.

### Anti-Patterns to Avoid

- **Catching too broadly in GlobalExceptionHandler:** Do NOT catch exceptions that are already handled by controller try-catch blocks. The `@ExceptionHandler` only fires for exceptions that escape the controller method. Controller-level try-catches with flash messages will continue to work undisturbed.
- **Putting GlobalExceptionHandler in domain package:** It references Spring MVC classes (`ModelAndView`, `HttpStatus`), so it belongs in `admin.controller`, not `domain`.
- **Adding @ResponseStatus to exception classes:** Use `@ExceptionHandler` to set status codes instead. `@ResponseStatus` on exceptions bypasses the handler when Spring's default resolver picks it up first -- creating inconsistent behavior.
- **Making exception hierarchy too deep:** 3 exception classes are sufficient for Phase 1. No need for abstract base class or inheritance hierarchy. Keep flat.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Error page rendering | Custom servlet filter for errors | Spring's @ExceptionHandler + Thymeleaf template | @ExceptionHandler integrates with Spring MVC properly, handles content negotiation, respects profile-specific config |
| Profile detection in templates | Thymeleaf `#environment.getActiveProfiles()` SpEL | Pass `showDetails` boolean from handler | CLAUDE.md says no complex SpEL in templates |
| Error attribute model | Custom error attribute map | `ModelAndView` with explicit attributes | Standard Spring MVC pattern, type-safe |

## Common Pitfalls

### Pitfall 1: Flash Message Interference
**What goes wrong:** GlobalExceptionHandler catches exceptions that controllers were already handling via try-catch, breaking the flash message pattern.
**Why it happens:** `@ExceptionHandler(Exception.class)` is a catch-all. If a controller's try-catch block itself throws (e.g., `redirectAttributes.addFlashAttribute` fails), the handler catches it.
**How to avoid:** The 65 existing `catch(Exception e)` blocks in controllers will continue to work because they catch BEFORE the exception escapes the controller method. `@ExceptionHandler` only fires for unhandled exceptions. No code change needed for existing catch blocks.
**Warning signs:** Existing flash message tests failing after adding GlobalExceptionHandler.

### Pitfall 2: Error.html Template Resolution Conflict
**What goes wrong:** Spring Boot's `BasicErrorController` resolves `error.html` at root templates level. A new `admin/error.html` within admin layout needs explicit routing.
**Why it happens:** Spring Boot auto-configures `BasicErrorController` which looks for `templates/error.html` (or `templates/error/{status}.html`). The `@ExceptionHandler` approach bypasses `BasicErrorController` entirely and returns a specific view name.
**How to avoid:** Use `@ExceptionHandler` returning `ModelAndView("admin/error")` -- this resolves to `templates/admin/error.html`. Keep the root `error.html` as a fallback for errors that occur outside Spring MVC (e.g., filter-level errors, errors before DispatcherServlet). Replace its content with a redirect or simpler version.
**Warning signs:** Seeing the old standalone error page instead of the admin-layout version.

### Pitfall 3: Missing appVersion on Error Page
**What goes wrong:** Error page renders within admin layout but `appVersion` is missing because `GlobalModelAdvice` might not fire for error views.
**Why it happens:** `@ModelAttribute` from `GlobalModelAdvice` IS applied to `@ExceptionHandler` responses because both are `@ControllerAdvice` beans and `@ModelAttribute` methods run for all controller requests.
**How to avoid:** Verify in tests that `appVersion` model attribute is present on error page responses.
**Warning signs:** Empty version badge in sidebar on error pages.

### Pitfall 4: Changing orElseThrow in TestDataService
**What goes wrong:** TestDataService has 4 bare `.orElseThrow()` + 7 with lambdas. Changing these could affect test data seeding.
**Why it happens:** TestDataService is excluded from coverage (JaCoCo excludes) but still runs during dev/test.
**How to avoid:** Include TestDataService in the orElseThrow migration. The exception messages improve debuggability when test setup fails. But note: TestDataService uses `orElseThrow()` on data it just created, so exceptions here indicate test infrastructure problems, not user-facing errors.
**Warning signs:** Test setup failures with unclear messages.

### Pitfall 5: PlayoffService Already Has Typed Messages
**What goes wrong:** PlayoffService already has 19 `.orElseThrow(() -> new IllegalArgumentException(...))` with good messages. Blindly replacing all of these loses the existing work.
**Why it happens:** Some services were written later with better practices.
**How to avoid:** For these 33 lambda-style calls, keep the message content but change the exception type from `IllegalArgumentException`/`IllegalStateException` to `EntityNotFoundException`. The message format should be standardized to match the new pattern.

## Code Examples

### orElseThrow Inventory by File

| File | Bare `.orElseThrow()` | Lambda-style | Total |
|------|----------------------|--------------|-------|
| SeasonManagementService | 14 | 1 | 15 |
| RaceManagementService | 20 | 0 | 20 |
| PlayoffService | 2 | 19 | 21 |
| SeasonController | 9 | 0 | 9 |
| MatchService | 7 | 0 | 7 |
| DriverController | 6 | 0 | 6 |
| TeamController | 6 | 0 | 6 |
| SwissPairingService | 5 | 0 | 5 |
| MatchdayService | 4 | 1 | 5 |
| RaceLineupService | 4 | 0 | 4 |
| TrackController | 4 | 0 | 4 |
| TeamCardController | 4 | 0 | 4 |
| CarController | 4 | 0 | 4 |
| TestDataService | 4 | 7 | 11 |
| RaceScoringController | 3 | 0 | 3 |
| MatchScoringController | 3 | 0 | 3 |
| PlayoffController | 0 | 3 | 3 |
| MatchdayGeneratorService | 2 | 0 | 2 |
| CsvImportService | 0 | 2 | 2 |
| DriverService | 1 | 0 | 1 |
| TeamManagementService | 1 | 0 | 1 |
| **Total** | **103** | **33** | **136** |

Note: CONTEXT.md says "50+" but actual count is 136 total (103 bare + 33 lambda). CsvImportService has 2 lambda-style calls that use `IllegalArgumentException` with contextual messages (driver matching) -- these may be better as `ValidationException` rather than `EntityNotFoundException` depending on the error semantics.

### Existing Error Configuration

| Profile | `include-stacktrace` | `include-message` | Effect |
|---------|---------------------|--------------------|--------|
| dev | `always` | `always` | Full details in BasicErrorController responses |
| prod | (default: `never`) | (default: `never`) | No details exposed |

These settings affect Spring Boot's `BasicErrorController` (the fallback `error.html`). The `@ExceptionHandler` approach renders its own template and controls detail visibility via the `showDetails` boolean based on active profile.

### Existing Flash Message Pattern (Must Preserve)

```java
// This pattern exists in 65 catch blocks across 17 controllers.
// It WILL NOT be affected by GlobalExceptionHandler because the
// exception is caught within the controller method.
@PostMapping("/{id}/attachments/upload")
public String uploadAttachment(@PathVariable UUID id,
                                @RequestParam("file") MultipartFile file,
                                RedirectAttributes redirectAttributes) {
    try {
        String filename = raceManagementService.uploadAttachment(id, file);
        redirectAttributes.addFlashAttribute("successMessage", "File uploaded: " + filename);
    } catch (Exception e) {
        redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
    }
    return "redirect:/admin/races/" + id;
}
```

### What Triggers the GlobalExceptionHandler

Exceptions that escape controller methods without being caught:
1. A GET request for `/admin/teams/{id}` where `id` does not exist -- the `teamRepository.findById(id).orElseThrow()` fires, exception propagates through controller method to Spring MVC, which delegates to `@ExceptionHandler`
2. An unexpected NullPointerException in a service method called from a controller that has no try-catch

### What Does NOT Trigger GlobalExceptionHandler
1. POST handlers with try-catch blocks (65 instances) -- exception is caught, flash attribute set, redirect returned
2. Template rendering errors (Thymeleaf) -- these bypass `@ExceptionHandler` and go to `BasicErrorController`

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Mockito + Spring Boot Test |
| Config file | pom.xml (spring-boot-starter-test dependency) |
| Quick run command | `./mvnw test -pl .` |
| Full suite command | `./mvnw verify` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| EXCP-01 | GlobalExceptionHandler catches EntityNotFoundException, returns 404 with admin error page | unit | `./mvnw test -Dtest=GlobalExceptionHandlerTest -pl .` | Wave 0 |
| EXCP-01 | GlobalExceptionHandler catches generic Exception, returns 500 | unit | `./mvnw test -Dtest=GlobalExceptionHandlerTest -pl .` | Wave 0 |
| EXCP-01 | Error page uses admin layout (sidebar visible) | integration | `./mvnw test -Dtest=GlobalExceptionHandlerTest -pl .` | Wave 0 |
| EXCP-01 | Dev profile shows exception details, prod does not | unit | `./mvnw test -Dtest=GlobalExceptionHandlerTest -pl .` | Wave 0 |
| EXCP-02 | EntityNotFoundException contains entity type and ID | unit | `./mvnw test -Dtest=EntityNotFoundExceptionTest -pl .` | Wave 0 |
| EXCP-02 | orElseThrow calls use EntityNotFoundException | integration | `./mvnw verify` (existing service tests still pass) | Existing tests cover |
| EXCP-01 | Flash messages still work after GlobalExceptionHandler added | integration | `./mvnw verify` (existing controller tests) | Existing tests cover |

### Sampling Rate
- **Per task commit:** `./mvnw test -Dtest=GlobalExceptionHandlerTest,EntityNotFoundExceptionTest -pl .`
- **Per wave merge:** `./mvnw verify`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `src/test/java/org/ctc/admin/controller/GlobalExceptionHandlerTest.java` -- covers EXCP-01 (handler behavior, HTTP status codes, admin layout rendering, dev vs prod details)
- [ ] `src/test/java/org/ctc/domain/exception/EntityNotFoundExceptionTest.java` -- covers EXCP-02 (message format, entity type + ID in message)

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `@ResponseStatus` on exception classes | `@ExceptionHandler` in `@ControllerAdvice` | Spring 4+ (still current in Spring Boot 4.x) | @ExceptionHandler gives full control over response |
| `HandlerExceptionResolver` bean | `@ControllerAdvice` + `@ExceptionHandler` | Spring 3.2+ | Annotation-based is standard |
| Extending `ResponseEntityExceptionHandler` | Only for REST APIs | N/A | This is a Thymeleaf app, use `ModelAndView` not `ResponseEntity` |

**Important for Spring Boot 4.x:** The `@ControllerAdvice` and `@ExceptionHandler` API is unchanged from Spring Boot 3.x. Spring Boot 4.0 (Spring Framework 7) did not change the exception handling mechanism. The `BasicErrorController` still provides fallback error handling for non-MVC errors.

## Open Questions

1. **CsvImportService orElseThrow semantics**
   - What we know: 2 lambda-style calls throwing IllegalArgumentException during CSV import driver matching
   - What's unclear: These may represent validation failures (bad CSV data) rather than missing entities
   - Recommendation: Use `ValidationException` for these 2 cases, not `EntityNotFoundException`. Decide during implementation based on method context.

2. **Root error.html fate**
   - What we know: Existing `templates/error.html` uses standalone layout (no sidebar)
   - What's unclear: Whether to keep it as fallback for non-admin errors or replace entirely
   - Recommendation: Keep as minimal fallback (for errors outside DispatcherServlet), but update styling. Primary error page is `admin/error.html` via @ExceptionHandler.

## Sources

### Primary (HIGH confidence)
- Codebase analysis: `GlobalModelAdvice.java`, `error.html`, `layout.html`, `application-dev.yml`, `application-prod.yml`
- orElseThrow grep audit: 136 total occurrences across 21 files (103 bare, 33 with lambdas)
- Existing catch blocks: 65 `catch(Exception e)` across 17 controllers

### Secondary (MEDIUM confidence)
- Spring Boot 4.x @ControllerAdvice behavior -- unchanged from Spring Boot 3.x based on project running with Spring Boot 4.0.5

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- Spring Boot @ControllerAdvice is mature, well-documented, unchanged in 4.x
- Architecture: HIGH -- Pattern is standard for Thymeleaf/MVC apps, no novel design
- Pitfalls: HIGH -- Based on direct codebase analysis of 136 orElseThrow calls and 65 catch blocks
- orElseThrow inventory: HIGH -- grep-verified counts per file

**Research date:** 2026-04-03
**Valid until:** 2026-05-03 (stable domain, Spring Boot exception handling is mature)
