---
phase: 29-mass-assignment-fix
plan: 01
subsystem: security
tags: [mass-assignment, dto, spring-mvc, thymeleaf, form-binding]

# Dependency graph
requires: []
provides:
  - MatchdayForm DTO with only user-editable fields (id, label, sortIndex, seasonId)
  - MatchdayController refactored to bind MatchdayForm instead of Matchday JPA entity
  - matchday-form.html template binding to ${form} DTO
affects: [mass-assignment-fix]

# Tech tracking
tech-stack:
  added: []
  patterns: [Form DTO binding pattern applied to last remaining JPA-entity-bound controller]

key-files:
  created:
    - src/main/java/org/ctc/admin/dto/MatchdayForm.java
  modified:
    - src/main/java/org/ctc/admin/controller/MatchdayController.java
    - src/main/resources/templates/admin/matchday-form.html
    - src/test/java/org/ctc/admin/controller/MatchdayControllerTest.java

key-decisions:
  - "seasonId sourced from form DTO (not @RequestParam) in save() — eliminates mismatch between hidden field and query param"
  - "season entity added as separate model attribute for template display — keeps DTO clean (no JPA entity reference)"
  - "save() error path loads season entity via findSeasonById() to prevent NPE on season display label in re-rendered form"

patterns-established:
  - "MatchdayForm pattern: 4-field DTO (id, label, sortIndex, seasonId) mirrors all other 12 admin Form DTOs"
  - "Controller edit() maps entity to DTO field-by-field — explicit mapping, no reflection"

requirements-completed: [SECU-01]

# Metrics
duration: 8min
completed: 2026-04-13
---

# Phase 29 Plan 01: Mass Assignment Fix Summary

**MatchdayController mass assignment vulnerability eliminated by replacing `@ModelAttribute Matchday` (JPA entity) with `@ModelAttribute("form") MatchdayForm` DTO containing only 4 user-editable fields**

## Performance

- **Duration:** ~8 min
- **Started:** 2026-04-13T14:56:00Z
- **Completed:** 2026-04-13T14:58:17Z
- **Tasks:** 3
- **Files modified:** 4

## Accomplishments
- Created MatchdayForm DTO with exactly 4 fields (id, label, sortIndex, seasonId) — no JPA-managed fields bindable
- Refactored create/edit/save controller methods to use MatchdayForm instead of Matchday entity
- Updated template to bind to `${form}` DTO, separating display-only `${season}` from form binding
- Added validation error test covering blank label path returning form with field errors
- All 863 tests pass, JaCoCo coverage threshold (>= 82%) maintained

## Task Commits

Each task was committed atomically:

1. **Task 1: RED — Update tests to assert MatchdayForm DTO binding** - `b40442d` (test)
2. **Task 2: GREEN — Create MatchdayForm DTO, refactor controller, update template** - `b436234` (feat)
3. **Task 3: Full suite verification** - `b66a5cc` (refactor)

_Note: TDD tasks — RED committed first, then GREEN implementation._

## Files Created/Modified
- `src/main/java/org/ctc/admin/dto/MatchdayForm.java` - New DTO with 4 user-editable fields only
- `src/main/java/org/ctc/admin/controller/MatchdayController.java` - Refactored create/edit/save to use MatchdayForm
- `src/main/resources/templates/admin/matchday-form.html` - Updated bindings to ${form} and ${season}
- `src/test/java/org/ctc/admin/controller/MatchdayControllerTest.java` - Updated 3 tests + added validation error test

## Decisions Made
- `seasonId` is now sourced from the form DTO in `save()` instead of a separate `@RequestParam` — this removes the risk of a mismatch between the hidden input field value and the query parameter
- `season` entity is added as a separate model attribute (not embedded in MatchdayForm) to keep the DTO clean while still providing `displayLabel` to the template
- The `save()` error path explicitly loads the `season` entity via `matchdayService.findSeasonById()` to prevent a NullPointerException when re-rendering the form after validation failure

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## Known Stubs
None — all data flows are wired.

## Threat Flags
None — this plan's purpose was to mitigate T-29-01 (mass assignment via JPA entity binding). Mitigation applied as designed. No new security surface introduced.

## Self-Check: PASSED
- `src/main/java/org/ctc/admin/dto/MatchdayForm.java` — FOUND
- `src/main/java/org/ctc/admin/controller/MatchdayController.java` — FOUND (modified)
- `src/main/resources/templates/admin/matchday-form.html` — FOUND (modified)
- Commit `b40442d` — FOUND (test RED)
- Commit `b436234` — FOUND (feat GREEN)
- Commit `b66a5cc` — FOUND (refactor verify)
- 863 tests, 0 failures — VERIFIED
- JaCoCo coverage check passed — VERIFIED

## Next Phase Readiness
- SECU-01 fully satisfied: MatchdayController is the last controller that bound a JPA entity directly via `@ModelAttribute`; all 13 admin Form DTOs now use the same pattern
- No blockers for subsequent phases

---
*Phase: 29-mass-assignment-fix*
*Completed: 2026-04-13*
