---
phase: 32-layering-and-exception-fix
plan: "02"
subsystem: domain/service
tags: [exception-handling, arch-compliance, tdd]
dependency_graph:
  requires: []
  provides: [ARCH-02-matchday-service]
  affects: [MatchdayService, GlobalExceptionHandler]
tech_stack:
  added: []
  patterns: [domain-exception-replacement, tdd-red-green-refactor]
key_files:
  created: []
  modified:
    - src/main/java/org/ctc/domain/service/MatchdayService.java
    - src/test/java/org/ctc/domain/service/MatchdayServiceTest.java
decisions:
  - "Used EntityNotFoundException(String, Object) two-arg constructor for type-safe entity identification"
  - "BusinessRuleException used for duplicate label; message preserved for JavaScript 409 detection"
  - "GlobalExceptionHandler not modified — existing handlers already map to correct HTTP status codes"
metrics:
  duration: ~10min
  completed: "2026-04-13T22:20:19Z"
  tasks_completed: 2
  files_modified: 2
requirements: [ARCH-02]
---

# Phase 32 Plan 02: Domain Exception Replacement in MatchdayService Summary

**One-liner:** Replaced `ResponseStatusException(NOT_FOUND/CONFLICT)` in `MatchdayService.createInline()` with `EntityNotFoundException` and `BusinessRuleException` using TDD (red-green-refactor), preserving HTTP 404/409 behavior via `GlobalExceptionHandler`.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | RED — Failing tests for domain exceptions | 2586384 | MatchdayServiceTest.java |
| 2 | GREEN+REFACTOR — Replace exceptions in service | 7e93714 | MatchdayService.java |

## Implementation Details

### Task 1: RED (test commit: 2586384)

Added two new/updated tests to `MatchdayServiceTest.java`:
- `givenMissingSeason_whenCreateInline_thenThrowsEntityNotFoundException` — new test asserting `EntityNotFoundException` for missing season
- `givenDuplicateLabel_whenCreateInline_thenThrowsBusinessRuleException` — renamed and updated existing test from `ResponseStatusException` to `BusinessRuleException`

Removed `import org.springframework.web.server.ResponseStatusException` from test file. Added `import org.ctc.domain.exception.BusinessRuleException` and `import org.ctc.domain.exception.EntityNotFoundException`.

Both tests correctly failed (RED) against the original service implementation.

### Task 2: GREEN + REFACTOR (fix commit: 7e93714)

In `MatchdayService.createInline()`:
- Replaced `orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ...))` with `orElseThrow(() -> new EntityNotFoundException("Season", seasonId))`
- Replaced `throw new ResponseStatusException(HttpStatus.CONFLICT, ...)` with `throw new BusinessRuleException("Matchday label already exists in this season: " + label)`

Removed unused imports from `MatchdayService.java`:
- `import org.springframework.http.HttpStatus;`
- `import org.springframework.web.server.ResponseStatusException;`

Added `import org.ctc.domain.exception.BusinessRuleException;` (new). `EntityNotFoundException` import was already present.

## Verification

```
grep -r "ResponseStatusException" src/main/java/org/ctc/domain/
# Returns: 0 results (PASS)

./mvnw verify
# BUILD SUCCESS — all tests pass
```

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None.

## Threat Flags

No new security-relevant surface introduced. Exception type change only; HTTP status codes preserved (404/409) via existing `GlobalExceptionHandler` handlers.

## Self-Check: PASSED

- [x] `src/main/java/org/ctc/domain/service/MatchdayService.java` — modified, committed at 7e93714
- [x] `src/test/java/org/ctc/domain/service/MatchdayServiceTest.java` — modified, committed at 2586384
- [x] Commit 2586384 exists: `test(32-02): add failing tests for domain exceptions in MatchdayService.createInline`
- [x] Commit 7e93714 exists: `fix(32-02): replace ResponseStatusException with domain exceptions in MatchdayService`
- [x] `./mvnw verify` — BUILD SUCCESS
- [x] No `ResponseStatusException` in domain layer
