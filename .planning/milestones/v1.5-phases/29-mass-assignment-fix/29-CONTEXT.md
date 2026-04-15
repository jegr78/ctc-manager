# Phase 29: Mass Assignment Fix - Context

**Gathered:** 2026-04-13
**Status:** Ready for planning

<domain>
## Phase Boundary

Replace direct JPA entity binding on MatchdayController.save() with a MatchdayForm DTO, eliminating mass assignment risk. Only the matchday create/edit form is in scope — no other controllers, no new features.

</domain>

<decisions>
## Implementation Decisions

### MatchdayForm DTO Structure
- **D-01:** Create `MatchdayForm` in `org.ctc.admin.dto` with fields: `UUID id`, `@NotBlank String label`, `int sortIndex`, `UUID seasonId`
- **D-02:** `seasonId` is part of the DTO (not a separate `@RequestParam`) — consistent with other Form DTOs (RaceForm, SeasonForm pattern)
- **D-03:** No JPA-managed fields (version, createdAt, updatedAt) on the DTO — only user-editable fields

### Service Layer
- **D-04:** `matchdayService.saveMatchday(label, sortIndex, seasonId, id)` signature stays unchanged — controller maps DTO fields to individual parameters
- **D-05:** Domain service does NOT import admin.dto — clean layer separation preserved (aligns with ARCH-01 fix in Phase 32)

### Template Binding
- **D-06:** Template `th:object` binds to `${form}` (MatchdayForm) instead of `${matchday}` (JPA entity)
- **D-07:** Season info displayed via separate model attribute `${season}` — controller loads Season entity and adds it to model for display
- **D-08:** Create flow: controller adds empty MatchdayForm to model (with seasonId pre-filled if provided via query param)
- **D-09:** Edit flow: controller loads Matchday entity, maps to MatchdayForm, adds form + season entity to model

### Controller Changes
- **D-10:** `save()` method signature changes from `@ModelAttribute Matchday` to `@ModelAttribute("form") MatchdayForm`
- **D-11:** `create()` and `edit()` methods populate a MatchdayForm instead of a Matchday entity for the model
- **D-12:** Seasons list remains as separate model attribute (unchanged)

### Claude's Discretion
- Validation annotations on MatchdayForm fields (beyond @NotBlank on label)
- Template hidden field handling for seasonId (th:field vs name attribute)
- Test structure and naming for new MatchdayForm-based tests

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Security Pattern
- `CLAUDE.md` — "DTOs instead of Entities in Controllers" architectural principle and Mass Assignment protection rule

### Existing DTO Examples
- `src/main/java/org/ctc/admin/dto/SeasonForm.java` — Reference Form DTO pattern (Lombok, UUID id, validation)
- `src/main/java/org/ctc/admin/dto/RaceForm.java` — Form DTO with association IDs (seasonId, matchdayId)

### Affected Files
- `src/main/java/org/ctc/admin/controller/MatchdayController.java` — Controller with mass assignment vulnerability (lines 73-103)
- `src/main/resources/templates/admin/matchday-form.html` — Thymeleaf template binding to JPA entity
- `src/test/java/org/ctc/admin/controller/MatchdayControllerTest.java` — Existing controller tests

### Conventions
- `.planning/codebase/CONVENTIONS.md` — DTO patterns, controller patterns, Lombok usage
- `.planning/codebase/STRUCTURE.md` — Package layout for admin.dto

### Requirements
- `.planning/REQUIREMENTS.md` — SECU-01: Admin can create/edit matchdays via MatchdayForm DTO

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- 12 existing Form DTOs in `src/main/java/org/ctc/admin/dto/` — established pattern to follow
- `SeasonForm` — closest structural match (simple entity with few fields)
- `RaceForm` — example of Form DTO with association UUID fields

### Established Patterns
- Form DTOs: `@Getter @Setter @NoArgsConstructor`, Jakarta Validation annotations
- Controller save: `@Valid @ModelAttribute("form") XxxForm form, BindingResult result`
- Error handling: `if (result.hasErrors()) return "admin/xxx-form";`
- Service calls: controller maps DTO fields to service method parameters

### Integration Points
- `MatchdayController.save()` — replace `@ModelAttribute Matchday` with `@ModelAttribute("form") MatchdayForm`
- `MatchdayController.create()` / `edit()` — populate MatchdayForm instead of Matchday entity
- `matchday-form.html` — change `th:object="${matchday}"` to `th:object="${form}"`, season display via `${season}`
- `MatchdayControllerTest` — update test setup for DTO-based binding

</code_context>

<specifics>
## Specific Ideas

No specific requirements — follows established Form DTO pattern consistently applied across 12 other controllers.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 29-mass-assignment-fix*
*Context gathered: 2026-04-13*
