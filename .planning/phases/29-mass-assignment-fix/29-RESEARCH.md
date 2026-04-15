# Phase 29: Mass Assignment Fix - Research

**Researched:** 2026-04-13
**Domain:** Spring MVC / Form DTO / Mass Assignment Protection
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Create `MatchdayForm` in `org.ctc.admin.dto` with fields: `UUID id`, `@NotBlank String label`, `int sortIndex`, `UUID seasonId`
- **D-02:** `seasonId` is part of the DTO (not a separate `@RequestParam`) — consistent with other Form DTOs (RaceForm, SeasonForm pattern)
- **D-03:** No JPA-managed fields (version, createdAt, updatedAt) on the DTO — only user-editable fields
- **D-04:** `matchdayService.saveMatchday(label, sortIndex, seasonId, id)` signature stays unchanged — controller maps DTO fields to individual parameters
- **D-05:** Domain service does NOT import admin.dto — clean layer separation preserved (aligns with ARCH-01 fix in Phase 32)
- **D-06:** Template `th:object` binds to `${form}` (MatchdayForm) instead of `${matchday}` (JPA entity)
- **D-07:** Season info displayed via separate model attribute `${season}` — controller loads Season entity and adds it to model for display
- **D-08:** Create flow: controller adds empty MatchdayForm to model (with seasonId pre-filled if provided via query param)
- **D-09:** Edit flow: controller loads Matchday entity, maps to MatchdayForm, adds form + season entity to model
- **D-10:** `save()` method signature changes from `@ModelAttribute Matchday` to `@ModelAttribute("form") MatchdayForm`
- **D-11:** `create()` and `edit()` methods populate a MatchdayForm instead of a Matchday entity for the model
- **D-12:** Seasons list remains as separate model attribute (unchanged)

### Claude's Discretion

- Validation annotations on MatchdayForm fields (beyond @NotBlank on label)
- Template hidden field handling for seasonId (th:field vs name attribute)
- Test structure and naming for new MatchdayForm-based tests

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SECU-01 | Admin can create/edit matchdays via MatchdayForm DTO instead of direct JPA entity binding | MatchdayForm DTO creation, controller save() refactor, template th:object rebind, test updates all documented below |
</phase_requirements>

---

## Summary

Phase 29 replaces the single mass assignment vulnerability in the codebase: `MatchdayController.save()` currently accepts `@ModelAttribute Matchday` — a JPA entity — as its POST target, meaning any form submission could bind to `id`, `createdAt`, `updatedAt`, `season`, `matches`, and `races`. This is the only controller in the project that still violates the "DTOs instead of Entities in Controllers" architectural principle documented in CLAUDE.md.

The fix follows the exact same pattern as 12 other Form DTOs already in `org.ctc.admin.dto`. `SeasonForm` is the structural match (simple entity, few fields). `SeasonController.edit()` demonstrates the canonical mapping pattern: load entity, create form, set individual fields. The existing `matchdayService.saveMatchday(label, sortIndex, seasonId, id)` signature needs no change; the controller simply passes `form.getLabel()`, `form.getSortIndex()`, `form.getSeasonId()`, `form.getId()`.

The template change is equally mechanical: replace `th:object="${matchday}"` with `th:object="${form}"` and adjust the season display section — the form now carries `seasonId` (UUID) while the controller adds a `season` entity to the model for display purposes. Existing tests require updating: assertions checking `model().attributeExists("matchday")` must change to `"form"`, and the hidden-field parameter in POST tests moves from `@RequestParam UUID seasonId` to a form field `seasonId`.

**Primary recommendation:** Create `MatchdayForm` (3 steps: new DTO, controller refactor, template rebind) and update `MatchdayControllerTest` to assert against `form` attribute and include `seasonId` as a POST param.

---

## Project Constraints (from CLAUDE.md)

- DTOs instead of Entities in Controllers: Always bind form inputs (POST/save) via Form DTOs (`admin.dto`), never JPA entities directly via `@ModelAttribute` — protection against Mass Assignment
- Keep Controllers Thin: No business logic or direct repository access
- Domain service does NOT import admin.dto (D-05 locks this)
- Test Coverage: Minimum 82% line coverage (JaCoCo, enforced in pom.xml at `<minimum>0.82</minimum>`)
- TDD: Write tests first (Red → Green → Refactor). Feature sequence: Unit Tests → Implementation → Integration Tests → E2E Tests
- Test naming: BDD `givenContext_whenAction_thenResult()` pattern
- Do not change Flyway migrations (irrelevant here — no schema change)
- OSIV remains enabled — no lazy-init workarounds needed

---

## Standard Stack

No new dependencies. Everything needed is already present.

### Core (Already in Project)
| Library | Version | Purpose | Status |
|---------|---------|---------|--------|
| Jakarta Validation | (Spring Boot 4.x managed) | `@NotBlank` etc. on DTO fields | Already used on all 12 Form DTOs |
| Lombok | (Spring Boot 4.x managed) | `@Getter @Setter @NoArgsConstructor` on DTO | Already used on all 12 Form DTOs |
| Spring MVC | (Spring Boot 4.x managed) | `@Valid @ModelAttribute("form")`, `BindingResult` | Already used in every controller |
| Thymeleaf | (Spring Boot 4.x managed) | `th:object`, `th:field`, `th:value` in template | Already used in matchday-form.html |

**No installation required.** [VERIFIED: codebase grep]

---

## Architecture Patterns

### Canonical Form DTO Pattern

All 12 existing Form DTOs follow this exact pattern. `MatchdayForm` must match. [VERIFIED: codebase read of SeasonForm.java, RaceForm.java, TeamForm.java]

```java
// src/main/java/org/ctc/admin/dto/MatchdayForm.java
package org.ctc.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class MatchdayForm {

    private UUID id;              // null for create, populated for edit

    @NotBlank
    private String label;

    private int sortIndex;

    private UUID seasonId;
}
```

Source: Pattern derived from `SeasonForm.java` (read directly) [VERIFIED: codebase]

### Controller Create Method Pattern

Decision D-08: empty form with seasonId pre-filled from query param.

```java
@GetMapping("/new")
public String create(@RequestParam(required = false) UUID seasonId, Model model) {
    var form = new MatchdayForm();
    if (seasonId != null) {
        form.setSeasonId(seasonId);
    }
    model.addAttribute("form", form);
    model.addAttribute("seasons", matchdayService.getAllSeasons());
    return "admin/matchday-form";
}
```

The `findSeasonById()` call in the current `create()` is eliminated — `form.setSeasonId(seasonId)` stores the UUID directly. The season entity for display is not needed in the create flow (the template shows a `<select>` when season is not pre-chosen, or shows season name — but with the DTO the template will use `form.seasonId` to pre-select the dropdown, which is simpler). [VERIFIED: existing create() code read]

### Controller Edit Method Pattern

Decision D-09: load entity, map to form, add season entity separately for display.

```java
@GetMapping("/{id}/edit")
public String edit(@PathVariable UUID id, Model model) {
    var matchday = matchdayService.getMatchdayDetail(id).matchday();
    var form = new MatchdayForm();
    form.setId(matchday.getId());
    form.setLabel(matchday.getLabel());
    form.setSortIndex(matchday.getSortIndex());
    form.setSeasonId(matchday.getSeason().getId());
    model.addAttribute("form", form);
    model.addAttribute("season", matchday.getSeason());
    model.addAttribute("seasons", matchdayService.getAllSeasons());
    return "admin/matchday-form";
}
```

Source: Pattern from `SeasonController.edit()` (read directly) [VERIFIED: codebase]

### Controller Save Method Pattern

Decision D-10: replace `@ModelAttribute Matchday` with `@ModelAttribute("form") MatchdayForm`. Decision D-04: service signature unchanged.

```java
@PostMapping("/save")
public String save(@Valid @ModelAttribute("form") MatchdayForm form,
                   BindingResult result,
                   RedirectAttributes redirectAttributes,
                   Model model) {
    if (result.hasErrors()) {
        model.addAttribute("seasons", matchdayService.getAllSeasons());
        return "admin/matchday-form";
    }
    var saved = matchdayService.saveMatchday(
            form.getLabel(), form.getSortIndex(), form.getSeasonId(), form.getId());
    redirectAttributes.addFlashAttribute("successMessage", "Matchday saved: " + saved.getLabel());
    return "redirect:/admin/matchdays?seasonId=" + form.getSeasonId();
}
```

Key change: `@RequestParam UUID seasonId` is removed — `seasonId` now comes from `form.getSeasonId()`. [VERIFIED: existing save() code read]

### Template Rebind Pattern

Decisions D-06/D-07: `th:object="${form}"`, season display via `${season}`.

The current template uses `matchday.season` for both the hidden seasonId and the display label. With the DTO:
- Hidden `id` field: `th:field="*{id}"` (unchanged, MatchdayForm has `UUID id`)
- Hidden `seasonId` field: `th:field="*{seasonId}"` OR `name="seasonId" th:value="${form.seasonId}"`
- Season display (edit mode): use `${season.displayLabel}` from the separately loaded `season` entity
- Season select (create mode): pre-select using `th:selected="${s.id == form.seasonId}"`

`th:field="*{seasonId}"` is the cleaner approach — it handles both the name attribute and th:value binding in one expression, consistent with how `*{id}` is handled. [VERIFIED: current template read, Thymeleaf binding pattern observed across all other form templates]

The `th:if` condition that currently checks `matchday.season == null` must change to `form.seasonId == null` (or check whether a season entity was loaded into the model). Given that edit always sets `seasonId` and create sets it only when passed as a query param, `form.seasonId == null` is the correct condition for showing the season `<select>`. [VERIFIED: current template logic read]

**Updated template key sections:**

```html
<form th:action="@{/admin/matchdays/save}" th:object="${form}" method="post">
    <input type="hidden" th:field="*{id}">
    <input type="hidden" th:field="*{seasonId}" th:if="${form.seasonId != null}">
    <!-- Show select only for create without pre-selected season -->
    <div class="form-group" th:if="${form.seasonId == null}">
        <label for="seasonSelect">Season</label>
        <select id="seasonSelect" name="seasonId" required>
            <option value="">-- Select season --</option>
            <option th:each="s : ${seasons}"
                    th:value="${s.id}"
                    th:text="${s.displayLabel}"></option>
        </select>
    </div>
    <!-- Show season name when pre-selected (edit + create with seasonId param) -->
    <div th:if="${season != null}" class="text-dim mb-md">
        Season: <strong th:text="${season.displayLabel}"></strong>
    </div>
    <!-- label and sortIndex fields unchanged -->
    <div class="form-row">
        <div class="form-group">
            <label for="label">Label</label>
            <input type="text" id="label" th:field="*{label}" placeholder="e.g. Matchday 1, Playoff Semifinal" required>
        </div>
        <div class="form-group">
            <label for="sortIndex">Sort Order</label>
            <input type="number" id="sortIndex" th:field="*{sortIndex}" min="0" required>
        </div>
    </div>
    ...
</form>
```

Note: When `form.seasonId != null` (edit flow and create-with-season), the `season` model attribute must be loaded so the display `div` can show the label. For the create flow with `seasonId` param, `matchdayService.findSeasonById(seasonId)` provides the entity. [VERIFIED: existing create() code call to findSeasonById confirmed]

### Anti-Patterns to Avoid

- **Passing MatchdayForm to service:** Domain service must not import `admin.dto` (D-05). The controller extracts fields individually before calling `saveMatchday()`.
- **Keeping `@RequestParam UUID seasonId` on save():** With the DTO, seasonId arrives as a form field. Having both would cause Spring to expect it from both places — keep only the DTO field.
- **Using `${matchday}` in template assertions after refactor:** Existing tests that assert `model().attributeExists("matchday")` will fail — they must check `"form"` instead.
- **Forgetting seasons model attribute on validation error:** The `save()` error path must re-add `seasons` to the model (same as current code) since returning the form view without this attribute breaks the season select.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Mass assignment protection | Custom binding filter | `MatchdayForm` DTO with only user-editable fields | Spring MVC's `@ModelAttribute` binds only what the DTO exposes |
| Hidden field for UUID | Encrypted/signed field | Plain `th:field="*{id}"` hidden input | Admin-only app, no external threat; consistent with all other Form DTOs |
| Validation | Custom validator class | `@NotBlank` on `label` field | Jakarta Validation + Spring's `@Valid` handles this |

---

## Runtime State Inventory

Step 2.5: SKIPPED — this is not a rename/refactor/migration phase. No stored data, live service configs, OS registrations, secrets, or build artifacts reference "MatchdayForm" (a new class being created). [VERIFIED: no runtime state involved]

---

## Environment Availability

Step 2.6: SKIPPED — no external dependencies beyond the project's own code. All tools (Java 25, Maven wrapper, H2) are already in use. [VERIFIED: project already builds and tests pass]

---

## Common Pitfalls

### Pitfall 1: Template condition `matchday.season == null` left unchanged
**What goes wrong:** Template still references `${matchday}` for the `th:if` condition — renders nothing or Thymeleaf throws a `SpEL evaluation error` because `matchday` is no longer in the model.
**Why it happens:** Missed the condition expression while updating `th:object`.
**How to avoid:** Grep template for all `${matchday}` occurrences before finishing. Replace with `${form.seasonId}` or `${season}` as appropriate.
**Warning signs:** `org.thymeleaf.exceptions.TemplateProcessingException` mentioning `matchday` in the stack trace during integration test.

### Pitfall 2: Test asserting `model().attributeExists("matchday")` for GET endpoints
**What goes wrong:** Existing tests `whenGetNewMatchdayForm_thenReturnsMatchdayForm` and `givenExistingMatchday_whenGetEditForm_thenReturnsMatchdayForm` check for a `"matchday"` attribute. After the refactor, the attribute is named `"form"`.
**Why it happens:** Tests written against the old entity-binding design.
**How to avoid:** Update both GET test assertions to check `model().attributeExists("form", "seasons")`. The edit test may also check for `"season"`.
**Warning signs:** Test failure: `Model attribute 'matchday' does not exist`.

### Pitfall 3: `seasonId` missing from POST params in tests
**What goes wrong:** Current `save()` tests pass `seasonId` as a separate `@RequestParam`. After the refactor, it arrives as a form field via `MatchdayForm`. The test `.param("seasonId", ...)` must remain — it will bind to `form.seasonId` through Spring's normal form binding. This actually works unchanged for POST params. No pitfall here if tests already pass `seasonId` as a param.
**Why it doesn't break:** Spring MVC's `@ModelAttribute` binding reads all request params matching field names — `.param("seasonId", value)` will bind to `form.seasonId`. [VERIFIED: existing test already passes seasonId as param, and MatchdayForm will have a seasonId field]

### Pitfall 4: Validation error path missing the `season` model attribute
**What goes wrong:** When save() returns the form view after a validation error, the template fails to render if `form.seasonId != null` and `${season}` is null (template tries to call `season.displayLabel`).
**Why it happens:** The success path loads season; the error path only re-adds `seasons` (the list).
**How to avoid:** In the error path, also load and add `season` if `form.getSeasonId() != null`. Or ensure the template uses `th:if="${season != null}"` as a guard before accessing `season.displayLabel`.

### Pitfall 5: Title expression `matchday.id != null` in template
**What goes wrong:** `<h1 th:text="${matchday.id != null ? 'Edit Matchday' : 'New Matchday'}">` references `matchday` directly. After refactor, it must use `*{id}` or `${form.id}`.
**Why it happens:** Overlooked the `<h1>` expression during template update.
**How to avoid:** Check the `<h1>` element — change to `th:text="${form.id != null ? 'Edit Matchday' : 'New Matchday'}"`.

---

## Code Examples

### Existing Form DTO (SeasonForm — closest structural match)
[VERIFIED: read directly from codebase]
```java
// src/main/java/org/ctc/admin/dto/SeasonForm.java
@Getter @Setter @NoArgsConstructor
public class SeasonForm {
    private UUID id;
    @NotBlank
    private String name;
    private int year;
    // ...
}
```

### Existing Controller Edit Pattern (SeasonController)
[VERIFIED: read directly from codebase]
```java
// SeasonController.edit() — canonical mapping pattern
var form = new SeasonForm();
form.setId(season.getId());
form.setName(season.getName());
// ... map all fields
model.addAttribute("seasonForm", form);
model.addAttribute("season", season);
```

### Existing Controller Save Pattern
[VERIFIED: read directly from codebase]
```java
// SeasonController.save()
@PostMapping("/save")
public String save(@Valid @ModelAttribute("seasonForm") SeasonForm form, BindingResult result, ...)
```

### Current Vulnerability (MatchdayController.save() — to be fixed)
[VERIFIED: read directly from codebase, lines 92-104]
```java
// BEFORE — mass assignment vulnerability: JPA entity as @ModelAttribute
@PostMapping("/save")
public String save(@Valid @ModelAttribute Matchday matchday,   // <-- JPA entity bound from form
                   BindingResult result,
                   @RequestParam UUID seasonId,                // <-- seasonId separate
                   ...)

// AFTER — DTO-based binding
@PostMapping("/save")
public String save(@Valid @ModelAttribute("form") MatchdayForm form,  // <-- DTO only
                   BindingResult result,
                   ...)
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `@ModelAttribute Matchday` on save() | `@ModelAttribute("form") MatchdayForm` | This phase | Removes mass assignment risk; JPA-managed fields (createdAt, updatedAt, season association, matches list) no longer bindable from form submission |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `form.seasonId == null` is the correct condition for showing the season `<select>` in the create flow | Architecture Patterns / Template | If wrong: season select might not show; but the create form always starts with an empty MatchdayForm where seasonId is null unless a query param is passed — this logic is safe |
| A2 | `th:field="*{seasonId}"` works for UUID type binding in Thymeleaf without custom converter | Architecture Patterns / Template | If wrong: UUID→String conversion fails; but all other Form DTOs (RaceForm, SeasonForm) use UUID fields with th:field — this is standard Spring MVC behavior [CITED: RaceForm.java uses UUID fields with th:field pattern observed across project] |

**Confidence note:** A1 and A2 are low-risk — both are consistent with patterns already working in the codebase.

---

## Open Questions (RESOLVED)

1. **Season display for create-with-seasonId flow**
   - What we know: When `/admin/matchdays/new?seasonId=X` is called, the current code calls `matchdayService.findSeasonById(seasonId)` and sets `matchday.setSeason(...)`. With the DTO, the controller should also load the season entity and add it to the model as `"season"` so the display div renders correctly.
   - What's unclear: Whether the planner should include this as part of the create() method refactor or treat it as optional (the template could show the season select even with a pre-filled seasonId, letting the user change it).
   - Recommendation: Add `model.addAttribute("season", matchdayService.findSeasonById(seasonId))` in create() when seasonId is non-null. This matches the edit() flow and the template's display logic.
   - **RESOLVED:** create() calls `findSeasonById(seasonId)` and adds season to model, consistent with edit() flow and template display logic. Implemented in Plan 29-01 Task 2 step 2b.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test + MockMvc |
| Config file | `pom.xml` (Surefire + Failsafe) |
| Quick run command | `./mvnw test -Dtest=MatchdayControllerTest` |
| Full suite command | `./mvnw verify` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SECU-01 | Create flow: GET /new returns model with "form" attribute (MatchdayForm), not "matchday" | Integration | `./mvnw test -Dtest=MatchdayControllerTest#whenGetNewMatchdayForm_thenReturnsMatchdayForm` | ✅ (needs update) |
| SECU-01 | Edit flow: GET /{id}/edit returns model with "form" attribute populated from entity | Integration | `./mvnw test -Dtest=MatchdayControllerTest#givenExistingMatchday_whenGetEditForm_thenReturnsMatchdayForm` | ✅ (needs update) |
| SECU-01 | Save new: POST /save with MatchdayForm data creates matchday | Integration | `./mvnw test -Dtest=MatchdayControllerTest#givenValidMatchdayForm_whenSaveNewMatchday_thenRedirectsWithSuccess` | ✅ (needs update) |
| SECU-01 | Save edit: POST /save with id field updates existing matchday | Integration | `./mvnw test -Dtest=MatchdayControllerTest#givenExistingMatchday_whenSaveUpdatedMatchday_thenRedirectsAndUpdates` | ✅ (works as-is if params match) |
| SECU-01 | Validation: POST /save with blank label returns form with errors | Integration | New test needed | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `./mvnw test -Dtest=MatchdayControllerTest`
- **Per wave merge:** `./mvnw verify`
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] New test: `givenBlankLabel_whenSaveMatchday_thenReturnsFormWithErrors` — covers SECU-01 validation path
- [ ] Update: `whenGetNewMatchdayForm_thenReturnsMatchdayForm` — assert `model().attributeExists("form")` instead of `"matchday"`
- [ ] Update: `givenSeasonId_whenGetNewMatchdayFormWithSeasonId_thenReturnsPrefilledForm` — add assertion that `form.seasonId` matches
- [ ] Update: `givenExistingMatchday_whenGetEditForm_thenReturnsMatchdayForm` — assert `model().attributeExists("form", "season", "seasons")`

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | — |
| V3 Session Management | no | — |
| V4 Access Control | no | — |
| V5 Input Validation | yes | Jakarta Validation `@NotBlank` on MatchdayForm.label |
| V6 Cryptography | no | — |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Mass Assignment (HTTP parameter pollution) | Tampering | MatchdayForm DTO exposes only user-editable fields; JPA-managed fields (id generation, version, createdAt, updatedAt) are excluded |
| Over-posting (binding to collections) | Tampering | MatchdayForm has no collection fields; `matches` and `races` OneToMany associations are not bindable |

The core vulnerability: current `@ModelAttribute Matchday` binding allows a malicious POST to include `createdAt`, `updatedAt` parameters that Spring would bind to the JPA entity before it reaches the service. With `MatchdayForm`, only `id`, `label`, `sortIndex`, `seasonId` are bindable. [VERIFIED: Matchday entity fields read from codebase]

---

## Sources

### Primary (HIGH confidence)
- Codebase: `src/main/java/org/ctc/admin/controller/MatchdayController.java` — current vulnerability (lines 92-104 read directly)
- Codebase: `src/main/java/org/ctc/admin/dto/SeasonForm.java` — canonical Form DTO pattern
- Codebase: `src/main/java/org/ctc/admin/dto/RaceForm.java` — Form DTO with UUID association fields
- Codebase: `src/main/java/org/ctc/admin/controller/SeasonController.java` — canonical edit/save with Form DTO
- Codebase: `src/main/java/org/ctc/domain/model/Matchday.java` — entity fields that were bindable
- Codebase: `src/main/java/org/ctc/domain/model/BaseEntity.java` — JPA-managed fields (createdAt, updatedAt)
- Codebase: `src/main/resources/templates/admin/matchday-form.html` — template to be updated
- Codebase: `src/test/java/org/ctc/admin/controller/MatchdayControllerTest.java` — tests to be updated
- `.planning/codebase/CONVENTIONS.md` — DTO patterns, controller patterns confirmed

### Secondary (MEDIUM confidence)
- None needed — all findings verified directly against codebase.

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new dependencies, all patterns verified in codebase
- Architecture: HIGH — exact template from 12 existing Form DTOs, read directly from source
- Pitfalls: HIGH — derived from reading actual code being changed

**Research date:** 2026-04-13
**Valid until:** Stable — not time-sensitive (no external dependencies)
