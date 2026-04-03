---
phase: 01-exception-infrastructure
plan: 01
subsystem: exception-handling
tags: [controlleradvice, exception-handler, error-pages, thymeleaf]

requires: []
provides:
  - EntityNotFoundException with entityType/entityId for typed 404 responses
  - ValidationException for 400 validation error responses
  - BusinessRuleException for 409 business rule violation responses
  - GlobalExceptionHandler catching 5 exception types with admin error view
  - Admin error page within layout (sidebar, header visible)
  - Profile-aware error detail display (dev shows exception type, prod hides)
affects: [01-02-orElseThrow-migration]

tech-stack:
  added: []
  patterns: [ControllerAdvice exception handling, profile-aware error display, ModelAndView error responses]

key-files:
  created:
    - src/main/java/org/ctc/domain/exception/EntityNotFoundException.java
    - src/main/java/org/ctc/domain/exception/ValidationException.java
    - src/main/java/org/ctc/domain/exception/BusinessRuleException.java
    - src/main/java/org/ctc/admin/controller/GlobalExceptionHandler.java
    - src/main/resources/templates/admin/error.html
    - src/test/java/org/ctc/domain/exception/EntityNotFoundExceptionTest.java
    - src/test/java/org/ctc/admin/controller/GlobalExceptionHandlerTest.java
  modified:
    - src/main/resources/templates/error.html

key-decisions:
  - "Constructor-injected Environment for profile detection instead of @Profile annotation on class"
  - "ResponseStatusException re-thrown to preserve existing Spring handling (e.g. MatchdayService conflict responses)"
  - "Unit-tested handler directly via method calls with mocked Environment (no WebMvcTest needed)"

patterns-established:
  - "Exception hierarchy: domain exceptions in org.ctc.domain.exception, all extend RuntimeException"
  - "Error view pattern: GlobalExceptionHandler returns ModelAndView('admin/error') with status, error, message, showDetails attributes"
  - "Profile-aware details: environment.matchesProfiles('dev') controls exception type visibility"

requirements-completed: [EXCP-01]

duration: 4min
completed: 2026-04-03
---

# Phase 01 Plan 01: Exception Infrastructure Summary

**3 typed exception classes, GlobalExceptionHandler with @ControllerAdvice handling 6 exception types, and admin error page within layout with profile-aware detail display**

## Tasks Completed

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | Exception classes and GlobalExceptionHandler with tests | 795f6ce | EntityNotFoundException.java, ValidationException.java, BusinessRuleException.java, GlobalExceptionHandler.java, 2 test files |
| 2 | Admin error template and fallback error.html | 7dd77ca | admin/error.html, error.html, GlobalExceptionHandler.java (ResponseStatusException fix) |

## What Was Built

1. **Exception Classes** (org.ctc.domain.exception):
   - `EntityNotFoundException` — typed 404 with entityType and entityId fields
   - `ValidationException` — typed 400 for validation failures
   - `BusinessRuleException` — typed 409 for business rule violations

2. **GlobalExceptionHandler** (@ControllerAdvice):
   - EntityNotFoundException -> 404 "Not Found"
   - NoSuchElementException -> 404 "Not Found"
   - ValidationException -> 400 "Validation Error"
   - BusinessRuleException -> 409 "Business Rule Violation"
   - ResponseStatusException -> re-thrown (preserves existing Spring behavior)
   - Exception (catch-all) -> 500 "Internal Error"
   - Profile-aware: dev shows exception type, non-dev shows friendly message only

3. **Error Pages**:
   - `admin/error.html` — renders within admin layout with sidebar, card-based error display
   - `error.html` — simplified fallback (removed old navbar, inline styles)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] ResponseStatusException swallowed by generic handler**
- **Found during:** Task 2 (verify step)
- **Issue:** GlobalExceptionHandler's generic Exception handler caught ResponseStatusException (used by MatchdayService for 409 Conflict), returning 500 instead of the original status code
- **Fix:** Added @ExceptionHandler(ResponseStatusException.class) that re-throws, letting Spring's default handling preserve the original HTTP status
- **Files modified:** GlobalExceptionHandler.java, GlobalExceptionHandlerTest.java
- **Commit:** 7dd77ca

## Test Results

- 11 new tests (3 EntityNotFoundExceptionTest + 8 GlobalExceptionHandlerTest)
- Full suite: 670 tests, 0 failures, 0 errors
- JaCoCo coverage checks passed (82% minimum met)

## Known Stubs

None — all functionality is fully wired.

## Verification

- `./mvnw verify` — BUILD SUCCESS, all 670 tests pass
- 6 @ExceptionHandler methods in GlobalExceptionHandler
- 3 exception classes extending RuntimeException
- Admin error page uses layout fragment pattern
- No inline styles in admin/error.html

## Self-Check: PASSED

All 8 files found. Both commit hashes (795f6ce, 7dd77ca) verified.
