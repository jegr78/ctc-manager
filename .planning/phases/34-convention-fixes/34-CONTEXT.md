# Phase 34: Convention Fixes - Context

**Gathered:** 2026-04-14
**Status:** Ready for planning

<domain>
## Phase Boundary

Fix remaining convention violations: form validation in PlayoffController and inline styles in race results template.

Requirements: CONV-01 (form validation), CONV-02 (toString — already compliant), CONV-03 (English text — already compliant), CONV-04 (CSS classes), CONV-05 (log levels — already compliant).

</domain>

<decisions>
## Implementation Decisions

### Form Validation (CONV-01)
- **D-01:** PlayoffController.save() (L65-79) is missing @Valid on PlayoffForm and BindingResult parameter. Add standard pattern: `@Valid @ModelAttribute PlayoffForm form, BindingResult bindingResult` with early return to form view on validation errors.
- **D-02:** Follow existing controller pattern (e.g., SeasonController, TeamController) for validation error handling — return the form view with error messages via BindingResult.

### Inline Styles (CONV-04)
- **D-03:** race-results.html has inline style= attributes on lines 70, 75, 79, 84, 100, 101, 153. Extract these to CSS classes in admin.css.
- **D-04:** Per CLAUDE.md: "When refactoring inline styles to CSS classes, always check JavaScript that sets element.className = '...' — the new classes must be added there as well."
- **D-05:** Use semantic CSS class names (e.g., `results-header`, `results-position`, `results-points`) rather than generic utility names.

### Already Compliant (no action needed)
- **D-06:** CONV-02 (SeasonTeam/RaceSettings toString) — Both entities use Lombok @Getter/@Setter without @ToString. No lazy associations in toString. Already compliant.
- **D-07:** CONV-03 (English text) — No German text found in production templates or business logic. Only in test data service which is acceptable.
- **D-08:** CONV-05 (log levels) — No business rule violations logged at error level. Only legitimate IO errors use log.error(). Already compliant.

### Claude's Discretion
- Exact CSS class names for the extracted styles
- Whether PlayoffForm needs additional validation annotations (@NotNull, @NotBlank, etc.)

</decisions>

<specifics>
## Specific References

- PlayoffController.save() at L65-79 — no @Valid, no BindingResult
- race-results.html inline styles on L70, 75, 79, 84, 100, 101, 153
- admin.css already has btn-xs, btn-sm, btn-lg, btn-tab patterns to follow
- UI hint in ROADMAP: "yes" for phase 34 — visual verification needed after CSS changes

</specifics>

<canonical_refs>
## Canonical References

- `src/main/java/org/ctc/admin/controller/PlayoffController.java` — save() method (L65-79)
- `src/main/java/org/ctc/admin/dto/PlayoffForm.java` — form DTO
- `src/main/resources/templates/admin/race-results.html` — inline styles
- `src/main/resources/static/admin/css/admin.css` — target CSS file
- `.planning/codebase/CONVENTIONS.md` — coding conventions

</canonical_refs>

<deferred>
## Deferred Ideas

None.

</deferred>
