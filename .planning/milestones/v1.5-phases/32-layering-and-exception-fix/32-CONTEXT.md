# Phase 32: Layering and Exception Fix - Context

**Gathered:** 2026-04-14
**Status:** Ready for planning

<domain>
## Phase Boundary

Fix layering violations where domain services import from admin service layer, and replace HTTP-specific ResponseStatusException with domain exceptions in domain services.

Requirements: ARCH-01 (no admin imports in domain), ARCH-02 (domain exceptions instead of HTTP exceptions).

</domain>

<decisions>
## Implementation Decisions

### Layering Fix Strategy
- **D-01:** RaceGraphicService imports 4 admin graphic services (LineupGraphicService, OverlayGraphicService, ResultsGraphicService, SettingsGraphicService). Since RaceGraphicService is purely a facade that delegates to admin graphic services, move RaceGraphicService from `org.ctc.domain.service` to `org.ctc.admin.service`. This is the simplest fix — no interface abstraction needed.
- **D-02:** RaceService imports TeamCardService from admin. Extract the TeamCardService interaction into the controller layer (caller) or introduce a domain interface. Preferred: move the TeamCardService call out of RaceService into the controller that coordinates the race save + card regeneration.
- **D-03:** After moving/fixing, verify with: `grep -r "import org.ctc.admin" src/main/java/org/ctc/domain/` returns zero results.

### Exception Replacement
- **D-04:** MatchdayService.java line 132 uses `ResponseStatusException(HttpStatus.NOT_FOUND)` — replace with `EntityNotFoundException` (already exists in `org.ctc.domain.exception`).
- **D-05:** MatchdayService.java line 140 uses `ResponseStatusException(HttpStatus.CONFLICT)` — replace with `BusinessRuleException` (already exists in `org.ctc.domain.exception`).
- **D-06:** GlobalExceptionHandler already handles both `EntityNotFoundException` (maps to 404) and `BusinessRuleException` (maps to 400). Verify the CONFLICT → BusinessRuleException mapping produces the correct HTTP status. If GlobalExceptionHandler maps BusinessRuleException to 400 but the original was 409, add a specific handler or use the existing exception with a descriptive message.
- **D-07:** Remove the `ResponseStatusException` import and `handleResponseStatus` handler from GlobalExceptionHandler only if no other domain service uses it. Otherwise leave the handler as a safety net for any remaining usages in controllers.

### Claude's Discretion
- Test strategy: Unit tests for the exception replacements, compile-time verification for the import fix. No new E2E tests needed.
- Whether to extract an interface for TeamCardService or simply move the call to the controller — choose whichever is simpler and maintains the existing behavior.

</decisions>

<specifics>
## Specific References

- Domain exceptions already exist: `EntityNotFoundException`, `BusinessRuleException`, `ValidationException` in `org.ctc.domain.exception`
- GlobalExceptionHandler already maps domain exceptions to HTTP responses
- RaceGraphicService is a thin facade — only delegates to admin graphic services
- RaceService uses TeamCardService only for team card regeneration after race operations

</specifics>

<canonical_refs>
## Canonical References

- `.planning/codebase/ARCHITECTURE.md` — Layer boundaries and package structure
- `.planning/codebase/CONVENTIONS.md` — Exception handling conventions
- `src/main/java/org/ctc/domain/service/RaceGraphicService.java` — Admin import violation (4 imports)
- `src/main/java/org/ctc/domain/service/RaceService.java` — Admin import violation (TeamCardService)
- `src/main/java/org/ctc/domain/service/MatchdayService.java` — ResponseStatusException usage (L132, L140)
- `src/main/java/org/ctc/domain/exception/EntityNotFoundException.java` — Replacement for NOT_FOUND
- `src/main/java/org/ctc/domain/exception/BusinessRuleException.java` — Replacement for CONFLICT
- `src/main/java/org/ctc/admin/controller/GlobalExceptionHandler.java` — Exception-to-HTTP mapping

</canonical_refs>

<deferred>
## Deferred Ideas

None — phase scope is narrow and well-defined.

</deferred>
