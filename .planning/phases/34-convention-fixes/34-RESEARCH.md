# Phase 34: Convention Fixes - Research

**Researched:** 2026-04-13
**Domain:** Spring MVC form validation + Thymeleaf/CSS inline style refactoring
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- D-01: PlayoffController.save() (L65-79) is missing @Valid on PlayoffForm and BindingResult parameter. Add standard pattern: `@Valid @ModelAttribute PlayoffForm form, BindingResult bindingResult` with early return to form view on validation errors.
- D-02: Follow existing controller pattern (e.g., SeasonController, TeamController) for validation error handling — return the form view with error messages via BindingResult.
- D-03: race-results.html has inline style= attributes on lines 70, 75, 79, 84, 100, 101, 153. Extract these to CSS classes in admin.css.
- D-04: Per CLAUDE.md: "When refactoring inline styles to CSS classes, always check JavaScript that sets element.className = '...' — the new classes must be added there as well."
- D-05: Use semantic CSS class names (e.g., `results-header`, `results-position`, `results-points`) rather than generic utility names.
- D-06: CONV-02 (SeasonTeam/RaceSettings toString) — Already compliant, no action needed.
- D-07: CONV-03 (English text) — Already compliant, no action needed.
- D-08: CONV-05 (log levels) — Already compliant, no action needed.

### Claude's Discretion
- Exact CSS class names for the extracted styles
- Whether PlayoffForm needs additional validation annotations (@NotNull, @NotBlank, etc.)

### Deferred Ideas (OUT OF SCOPE)
None.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| CONV-01 | PlayoffController.save() validates form input with @Valid and BindingResult | PlayoffController.save() at L66 confirmed missing `@Valid` and `BindingResult`. SeasonController.save() (L84) provides the canonical pattern. PlayoffForm fields analysed for applicable constraints. |
| CONV-04 | Race results page uses CSS classes from admin.css instead of inline styles | race-results.html inline styles inventoried at L70, L75, L79, L84, L100, L101, L153. JavaScript in the same file uses `span.style.*` property assignment (not `element.className`), so CSS class refactoring for HTML elements will not break JS behaviour. |
</phase_requirements>

---

## Summary

Phase 34 fixes two remaining convention violations in the CTC Manager codebase. The scope is narrow and well-understood from direct code inspection — no framework research is required.

**CONV-01** requires adding `@Valid` + `BindingResult` to `PlayoffController.save()`. The project already has `spring-boot-starter-validation` on the classpath (confirmed in pom.xml) and multiple controllers demonstrate the exact pattern. The canonical reference is `SeasonController.save()` (L84). The `PlayoffForm` DTO currently has no Bean Validation annotations; the fields `name` and `seasonId` are the natural candidates for `@NotBlank` / `@NotNull`. On a validation error the method must re-populate the model (seasons list) and return the `"admin/playoff-form"` view instead of redirecting.

**CONV-04** requires extracting seven inline `style=` attributes from `race-results.html` into named CSS classes in `admin.css`. The JavaScript block in that file does **not** use `element.className = '...'` — it uses `element.style.fontWeight`, `element.style.color`, etc. for the dynamically-built team-totals span only (L153–169). The existing HTML inline styles (L70, L75, L79, L84, L100, L101) are purely presentational and can be moved to CSS classes without touching JavaScript.

**Primary recommendation:** Implement CONV-01 by following SeasonController exactly; implement CONV-04 by creating purpose-named CSS classes under a `/* Race Results */` section in admin.css.

---

## Standard Stack

### Core (already in project — no new dependencies)

| Library | Version | Purpose | Source |
|---------|---------|---------|--------|
| spring-boot-starter-validation | per BOM (Boot 4.0.5) | Bean Validation (jakarta.validation) | [VERIFIED: pom.xml line 39] |
| Hibernate Validator | bundled | @NotBlank, @NotNull, @Min, @Max, @Valid | [VERIFIED: pom.xml] |

**No new dependencies are required for this phase.** [VERIFIED: pom.xml]

---

## Architecture Patterns

### Pattern 1: @Valid + BindingResult in POST handler (CONV-01)

**What:** Add `@Valid` to the `@ModelAttribute` parameter and add `BindingResult` immediately after. On errors, repopulate the model with required lists and return the form view (not a redirect).

**Canonical source:** `SeasonController.save()` — L84–91 [VERIFIED: direct file read]

```java
// Source: SeasonController.java L84-91 (VERIFIED)
@PostMapping("/save")
public String save(@Valid @ModelAttribute("seasonForm") SeasonForm form, BindingResult result,
                   @RequestParam UUID raceScoring,
                   @RequestParam UUID matchScoring,
                   RedirectAttributes redirectAttributes, Model model) {
    if (result.hasErrors()) {
        addScoringLists(model);
        return "admin/season-form";
    }
    // ... proceed with service call
}
```

**Applied to PlayoffController.save():**

```java
// Target pattern for PlayoffController.save()
@PostMapping("/save")
public String save(@Valid @ModelAttribute PlayoffForm form, BindingResult bindingResult,
                   RedirectAttributes redirectAttributes, Model model) {
    if (bindingResult.hasErrors()) {
        var data = playoffService.getPlayoffListData(null);
        model.addAttribute("seasons", data.allSeasons());
        return "admin/playoff-form";
    }
    try {
        var playoff = playoffService.createPlayoff(
                form.getSeasonId(), form.getName(), form.getNumberOfTeams(),
                form.getStartDate(), form.getEndDate(), form.getEventDurationMinutes());
        redirectAttributes.addFlashAttribute("successMessage",
                "Playoff created: " + playoff.getName());
        return "redirect:/admin/playoffs?seasonId=" + form.getSeasonId();
    } catch (IllegalArgumentException | IllegalStateException e) {
        log.error("Error creating playoff", e);
        redirectAttributes.addFlashAttribute("errorMessage", "Error: " + e.getMessage());
        return "redirect:/admin/playoffs/new?seasonId=" + form.getSeasonId();
    }
}
```

**Required import (already present in SeasonController — add to PlayoffController):**

```java
import jakarta.validation.Valid;
import org.springframework.validation.BindingResult;
```

**Note:** `org.springframework.web.bind.annotation.*` is already a wildcard import in PlayoffController. `jakarta.validation.Valid` and `org.springframework.validation.BindingResult` need to be added explicitly. [VERIFIED: PlayoffController.java imports]

### Pattern 2: PlayoffForm validation annotations (CONV-01 — Claude's Discretion)

`PlayoffForm` currently has no Bean Validation annotations. [VERIFIED: PlayoffForm.java]

Recommended additions following the project DTO convention (`@NotBlank` for strings, `@NotNull` for required objects):

```java
// Source: PlayoffForm.java — proposed additions
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@NotNull
private UUID seasonId;

@NotBlank
private String name;
```

`numberOfTeams` has a default of 8 and is an `int` (non-nullable), so no annotation needed. `startDate`, `endDate`, `eventDurationMinutes` are optional — no annotation needed. [VERIFIED: PlayoffForm.java, playoff-form.html]

**Precedent:** `SeasonForm` uses `@NotBlank` for `name`; `MatchdayForm` uses `@NotBlank` + `@NotNull`. [VERIFIED: grep of admin/dto]

### Pattern 3: Inline style extraction to CSS classes (CONV-04)

**What:** Replace each `style="..."` attribute in the HTML with a named CSS class. Add the new classes to `admin.css` under a dedicated comment block.

**Anti-pattern:** Generic utility classes (`fw-600`, `ta-right`) — use semantic names scoped to the template context instead (e.g., `results-team-name`, `results-driver-name`, `results-pos-input`). [ASSUMED — based on D-05 decision and existing admin.css naming conventions observed]

---

## Inline Style Inventory (CONV-04)

All inline `style=` attributes from race-results.html. [VERIFIED: direct file read]

| Line | Element | Current inline style | Proposed CSS class |
|------|---------|---------------------|-------------------|
| L70 | `<span>` (team short name) | `font-weight:600;` | `results-team-name` |
| L75 | `<td>` (driver PSN ID) | `font-weight:500;` | `results-driver-name` |
| L79 | `<input type="number">` (position) | `width:56px; padding:6px 8px; border:1px solid var(--border); border-radius:var(--radius-sm); background:var(--bg-input); color:var(--white); font-size:14px; font-weight:600; text-align:center; font-variant-numeric:tabular-nums;` | `results-pos-input` |
| L84 | `<input type="number">` (quali position) | same as L79 | `results-pos-input` (shared class) |
| L100 | `<tr>` (tfoot row) | `border-top:2px solid var(--border);` | `results-totals-row` |
| L101 | `<td>` (tfoot label) | `text-align:right; font-weight:600; color:var(--white);` | `results-totals-label` |

**Note on L153:** The CONTEXT.md references line 153 as containing an inline style. After code inspection, L153 is inside the JavaScript block (`<script th:inline="javascript">`) and contains `span.style.color = ...` which is a JavaScript property assignment — not an HTML `style=` attribute. [VERIFIED: race-results.html L114-194]

The JavaScript block (L153–169) uses `span.style.fontWeight`, `span.style.fontSize`, `span.style.color` to build the team totals dynamically. This is JavaScript DOM manipulation, not an HTML inline style. It cannot be replaced with a CSS class without refactoring the JS to use `classList.add()`. This is a separate concern; however since the CONTEXT.md decision D-05 calls for semantic class names, the safe approach is to add CSS classes for the dynamically-styled spans and update the JS to use `classList.add()` instead of `style.*` assignments.

**JavaScript className check (D-04 compliance):** [VERIFIED: race-results.html L114-194]
- No `element.className = '...'` anywhere in the file (confirmed by grep — no matches in associated JS files either).
- JS does use `span.style.fontWeight = '700'`, `span.style.fontSize = '16px'`, `span.style.color = ...` on L166-168 for the team totals spans built inside `calcPoints()`.
- Recommended approach: create a `results-total-value` CSS class (with `font-weight:700; font-size:16px`) and a `results-total-value--home` modifier (with `color:var(--accent)`), then update JS to use `span.classList.add(...)` instead of `span.style.*`.

### Proposed CSS additions to admin.css

```css
/* === Race Results === */
.results-team-name {
    font-weight: 600;
}

.results-driver-name {
    font-weight: 500;
}

.results-pos-input {
    width: 56px;
    padding: 6px 8px;
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
    background: var(--bg-input);
    color: var(--white);
    font-size: 14px;
    font-weight: 600;
    text-align: center;
    font-variant-numeric: tabular-nums;
}

.results-totals-row {
    border-top: 2px solid var(--border);
}

.results-totals-label {
    text-align: right;
    font-weight: 600;
    color: var(--white);
}

.results-total-value {
    font-weight: 700;
    font-size: 16px;
    color: var(--white);
}

.results-total-value--home {
    color: var(--accent);
}
```

**Similar existing class for reference:** `.quick-score-input` (admin.css L662-673) uses the same `width:56px; border; border-radius; background; color; font-size; font-weight; text-align; font-variant-numeric` pattern. `results-pos-input` intentionally differs in padding (6px 8px vs 4px 8px) and `var(--bg-input)` vs `var(--bg-card)`. [VERIFIED: admin.css L657-673]

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead |
|---------|-------------|-------------|
| Form field validation | Custom if/else null checks in controller | `@Valid` + Bean Validation annotations on DTO + `BindingResult` |
| CSS consistency | Duplicated inline styles | Single CSS class definition in admin.css |

---

## Common Pitfalls

### Pitfall 1: BindingResult must immediately follow the model attribute
**What goes wrong:** Spring MVC requires `BindingResult` to be the parameter directly following its bound `@ModelAttribute`. If any other parameter appears between them, validation errors are NOT captured and Spring throws an exception instead.
**How to avoid:** `@Valid @ModelAttribute PlayoffForm form, BindingResult bindingResult` — nothing in between.
**Warning signs:** `BindingException` at startup or test failure.

### Pitfall 2: Forgetting to repopulate model on validation failure
**What goes wrong:** Returning the form view without repopulating `model.addAttribute("seasons", ...)` causes a Thymeleaf `null` rendering error for the seasons dropdown.
**How to avoid:** Before `return "admin/playoff-form"`, call `playoffService.getPlayoffListData(null)` and add `seasons` to the model. [VERIFIED: playoff-form.html uses `${seasons}` dropdown]
**Warning signs:** 500 error on form re-render after validation failure.

### Pitfall 3: @ModelAttribute attribute name mismatch
**What goes wrong:** Thymeleaf `th:object="${playoffForm}"` binds to an attribute named `playoffForm`. If `@ModelAttribute` uses a different implicit name, the binding breaks.
**How to avoid:** `PlayoffForm` → implicit attribute name is `playoffForm` (Spring MVC lowerCamelCase convention). This matches `th:object="${playoffForm}"` in playoff-form.html. No explicit `@ModelAttribute("playoffForm")` annotation is required, but adding it makes intent explicit. [VERIFIED: playoff-form.html L8, PlayoffController create() L60]
**Warning signs:** Empty form fields on re-render.

### Pitfall 4: JavaScript inline styles for dynamically-built DOM elements
**What goes wrong:** Extracting only the HTML `style=` attributes while leaving the JavaScript `span.style.*` assignments means the team totals area still has "inline styles" at runtime, just generated by JS rather than authored in HTML.
**How to avoid:** Replace JS `span.style.fontWeight = '700'` etc. with `span.classList.add('results-total-value')` and `span.classList.add('results-total-value--home')` where appropriate. [VERIFIED: race-results.html calcPoints() L154-169]

---

## Test Patterns (from PlayoffControllerTest.java)

The existing test file uses `@SpringBootTest + @AutoConfigureMockMvc + @ActiveProfiles("dev") + @Transactional`. [VERIFIED: PlayoffControllerTest.java]

Two new tests are needed for CONV-01:

**Test: validation triggers on blank name**
```java
// Given/When/Then pattern from existing tests
@Test
void givenBlankName_whenSavePlayoff_thenRedirectsWithValidationError() throws Exception {
    mockMvc.perform(post("/admin/playoffs/save")
                    .param("seasonId", season.getId().toString())
                    .param("name", "")           // blank — triggers @NotBlank
                    .param("numberOfTeams", "4"))
            .andExpect(status().isOk())           // returns form view, not redirect
            .andExpect(view().name("admin/playoff-form"))
            .andExpect(model().attributeHasFieldErrors("playoffForm", "name"));
}
```

**Test: validation triggers on missing seasonId**
```java
@Test
void givenMissingSeasonId_whenSavePlayoff_thenReturnsFormView() throws Exception {
    mockMvc.perform(post("/admin/playoffs/save")
                    .param("name", "Test Playoff")
                    .param("numberOfTeams", "4"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/playoff-form"))
            .andExpect(model().attributeHasFieldErrors("playoffForm", "seasonId"));
}
```

**Note:** `givenValidPlayoffForm_whenSavePlayoff_thenRedirectsAndPersists` (L86) must remain passing — it provides `seasonId` and a non-blank `name`. [VERIFIED: PlayoffControllerTest.java L86-99]

---

## Code Examples

### Imports to add to PlayoffController.java

```java
// Add these two imports (not currently present in PlayoffController)
import jakarta.validation.Valid;
import org.springframework.validation.BindingResult;
```

[VERIFIED: PlayoffController.java L1-25 — neither import present]

### PlayoffForm annotations

```java
// PlayoffForm.java additions
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@NotNull
private UUID seasonId;

@NotBlank
private String name;
```

[VERIFIED: PlayoffForm.java — no validation annotations present; pattern from SeasonForm.java L17 and MatchdayForm.java L16,21]

---

## Environment Availability

Step 2.6: SKIPPED — this phase is purely Java/HTML/CSS changes with no external service dependencies.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring MockMvc (SpringBootTest) |
| Config file | pom.xml (Surefire + Failsafe) |
| Quick run command | `./mvnw test -pl . -Dtest=PlayoffControllerTest` |
| Full suite command | `./mvnw verify` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CONV-01 | Blank name returns form view with field error | integration | `./mvnw test -Dtest=PlayoffControllerTest` | ❌ Wave 0 (new test) |
| CONV-01 | Missing seasonId returns form view with field error | integration | `./mvnw test -Dtest=PlayoffControllerTest` | ❌ Wave 0 (new test) |
| CONV-04 | No inline style= in race-results.html | visual/manual | playwright-cli visual check | N/A — manual |

### Sampling Rate
- **Per task commit:** `./mvnw test -Dtest=PlayoffControllerTest`
- **Per wave merge:** `./mvnw verify`
- **Phase gate:** `./mvnw verify` green before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] Add `givenBlankName_whenSavePlayoff_thenRedirectsWithValidationError()` to `PlayoffControllerTest.java`
- [ ] Add `givenMissingSeasonId_whenSavePlayoff_thenReturnsFormView()` to `PlayoffControllerTest.java`

---

## Project Constraints (from CLAUDE.md)

All constraints enforced in this phase:

| Directive | Applies | Notes |
|-----------|---------|-------|
| Min 82% line coverage | Yes | New tests cover new branches in save() |
| No breaking URL changes | Yes | POST /admin/playoffs/save endpoint unchanged |
| No Flyway changes | N/A | No DB schema changes |
| No inline styles on buttons | Yes | CSS classes used (not buttons here, but same principle) |
| Controllers thin — no business logic | Yes | Validation check + model re-populate only |
| DTOs for form binding | Yes | PlayoffForm already a DTO; @Valid added |
| TDD: Write tests first | Yes | Wave 0 gaps must be written before CONV-01 impl |
| Playwright visual check after CSS changes | Yes | D-04 — verify race-results page after CSS refactor |
| No `style=` on elements | Yes | CONV-04 target |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | CSS class names (`results-team-name`, `results-driver-name`, etc.) are appropriate semantic names | Inline Style Inventory | Low — names are descriptive and not reused elsewhere; planner can choose alternatives |
| A2 | `results-pos-input` should differ from existing `quick-score-input` (different padding, bg-input vs bg-card) | CSS additions | Low — visual verification will catch if styles are wrong |

---

## Open Questions

1. **JavaScript span styling (L153–169)**
   - What we know: The JS uses `span.style.*` property assignment to colour the team totals. These are not HTML `style=` attributes in the source.
   - What's unclear: Whether CONV-04 scope explicitly includes JS-generated inline styles (not just authored HTML `style=` attributes).
   - Recommendation: Include JS refactoring in the plan (replace `span.style.*` with `classList.add()`) to fully satisfy "no inline styles" in the rendered DOM. This is a small addition to the task.

---

## Sources

### Primary (HIGH confidence)
- `PlayoffController.java` (direct read) — save() method signature, L65-79
- `PlayoffForm.java` (direct read) — all fields, no validation annotations
- `SeasonController.java` (direct read) — canonical @Valid + BindingResult pattern, L84-91
- `PlayoffControllerTest.java` (direct read) — existing test patterns, 23 tests
- `race-results.html` (direct read) — all inline style= attributes inventoried, JS block analysed
- `admin.css` (direct read) — confirmed no existing race-results classes; `quick-score-input` as closest analogue
- `pom.xml` (grep) — `spring-boot-starter-validation` confirmed present
- `playoff-form.html` (direct read) — form view name confirmed as `"admin/playoff-form"`, `${seasons}` dropdown

### Secondary (MEDIUM confidence)
- None required — all findings from direct codebase inspection

### Tertiary (LOW confidence)
- None

---

## Metadata

**Confidence breakdown:**
- CONV-01 implementation: HIGH — canonical pattern confirmed from SeasonController, imports verified
- CONV-04 inline style inventory: HIGH — all 6 HTML inline styles enumerated from direct file read
- CONV-04 CSS class names: MEDIUM — names are discretionary (A1); named in Assumptions Log
- Test patterns: HIGH — existing test file confirmed; new test method signatures follow established patterns

**Research date:** 2026-04-13
**Valid until:** 2026-05-13 (stable — no external dependencies, only local code)
