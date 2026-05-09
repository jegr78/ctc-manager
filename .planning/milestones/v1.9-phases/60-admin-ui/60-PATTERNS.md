# Phase 60: Admin UI - Pattern Map

**Mapped:** 2026-04-30
**Files analyzed:** 19 (8 new, 11 modified) + 9 test files
**Analogs found:** 19 / 19 (every file has at least a role-match analog)

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `src/main/java/org/ctc/admin/controller/SeasonPhaseController.java` | controller | request-response (CRUD) | `src/main/java/org/ctc/admin/controller/MatchdayController.java` | exact (nested-CRUD-under-parent-entity) |
| `src/main/java/org/ctc/admin/controller/SeasonPhaseGroupController.java` | controller | request-response (CRUD + bulk) | `src/main/java/org/ctc/admin/controller/MatchdayController.java` + `PlayoffController.saveSeed` (Z. 146-155) | exact + bulk-pattern |
| `src/main/java/org/ctc/admin/dto/SeasonPhaseForm.java` | DTO (Form) | form-binding | `src/main/java/org/ctc/admin/dto/SeasonForm.java` | exact (validation + Lombok stack) |
| `src/main/java/org/ctc/admin/dto/SeasonPhaseGroupForm.java` | DTO (Form) | form-binding | `src/main/java/org/ctc/admin/dto/MatchdayForm.java` | exact (slim form: name + sortIndex + parent FK) |
| `src/main/java/org/ctc/admin/dto/PhaseTeamForm.java` | DTO (Bulk-Form) | form-binding (indexed properties) | `src/main/java/org/ctc/admin/dto/SeedForm.java` | exact (List<NestedClass> with Spring indexed binding) |
| `src/main/resources/templates/admin/season-phase-form.html` | template (form) | form-render + BindingResult | `src/main/resources/templates/admin/season-form.html` (full Phase-Form scope) + `matchday-form.html` (slim layout cleanliness) | exact |
| `src/main/resources/templates/admin/season-phase-group-form.html` | template (form) | form-render + BindingResult | `src/main/resources/templates/admin/matchday-form.html` | exact (slim two-field form) |
| `src/main/resources/templates/admin/season-detail.html` (REWRITE) | template (detail-page) | render-with-tabs | `src/main/resources/templates/admin/template-editors.html` Z. 11-42 (tab pattern) + existing `season-detail.html` (header section to preserve) | composite (two analogs combine) |
| `src/main/resources/templates/admin/standings.html` (MODIFY) | template (detail-page) | render-with-tabs + conditional-columns | `template-editors.html` Z. 11-42 (Phase-Tabs Row 1) + existing `standings.html` (Saison-Dropdown + sortable-table-skeleton to keep) | composite |
| `src/main/resources/templates/admin/driver-import-preview.html` (MODIFY) | template | conditional-column + inline-badge | existing `driver-import-preview.html` (TabWarning banner already in Z. 31-39) | self-extension |
| `src/main/resources/templates/admin/playoff-bracket.html` (MODIFY) | template | render | existing `playoff-bracket.html` (Z. 96-121 Add/Remove-Season block to remove) | self-extension |
| `src/main/resources/templates/admin/season-form.html` (SLIM) | template (form) | form-render | existing `season-form.html` Z. 8-22 (kept) — Z. 47-99 removed | self-extension |
| `src/main/java/org/ctc/admin/controller/SeasonController.java` (MODIFY) | controller | request-response | self (slim `save` signature; remove `addScoringLists`, `format`/`scoring`-params) | self |
| `src/main/java/org/ctc/admin/controller/StandingsController.java` (MODIFY) | controller | request-response | self (extend with `?phase=&group=`; legacy `?season=` bridge) | self |
| `src/main/java/org/ctc/admin/controller/MatchdayController.java` (MODIFY) | controller | request-response | self (D-44 conservative: switch `findBySeasonId` callers to `findByPhaseId`) | self |
| `src/main/java/org/ctc/admin/controller/PlayoffController.java` (MODIFY) | controller | request-response | self — UI-side only; backend endpoints unchanged per D-43 | self |
| `src/main/java/org/ctc/admin/controller/DriverSheetImportController.java` (MODIFY) | controller | request-response | self (add `showGroupColumn` model attr) | self |
| `src/main/java/org/ctc/domain/service/SeasonManagementService.java` (MODIFY) | service | CRUD + transactional | self (remove D-25 auto-sync block; add D-25/D-26 atomic guards) | self |
| `src/main/java/org/ctc/domain/service/SeasonPhaseService.java` (EXTEND) | service | CRUD + bulk-diff | self (add `update`, `delete`, `updateGroup`, `deleteGroup`, `assignTeamsToPhase`) | self |
| `src/main/resources/static/admin/css/admin.css` (APPEND) | stylesheet | — | self (Z. 1038-1056 `.tab-btn` / `.tab-active`; Z. 1740-1748 `.tab-nav`; Z. 1781-1786 `.editor-tab-bar`) | self |

### Test File Classification

| New/Modified Test File | Role | Closest Analog | Match Quality |
|------------------------|------|----------------|---------------|
| `src/test/java/org/ctc/admin/controller/SeasonPhaseControllerTest.java` (NEW) | controller-test | `SeasonControllerTest.java` (`@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("dev") @Transactional` + `TestHelper`) and `MatchdayControllerTest.java` | exact |
| `src/test/java/org/ctc/admin/controller/SeasonPhaseGroupControllerTest.java` (NEW) | controller-test | same as above | exact |
| `src/test/java/org/ctc/admin/controller/integration/SeasonPhaseControllerIT.java` (NEW) | controller-IT | The "ControllerTest" classes already use full `@SpringBootTest @Transactional` shape — same template; or `DriverSheetImportServiceIT.java` Z. 48-51 for the canonical Phase-58-D-13 IT shape | exact |
| `src/test/java/org/ctc/admin/controller/integration/SeasonPhaseGroupControllerIT.java` (NEW) | controller-IT | same as above | exact |
| `src/test/java/org/ctc/admin/dto/SeasonPhaseFormTest.java` (NEW) | DTO-validation-test | (no existing `*FormTest.java` analog in codebase) — closest: `SeasonPhaseServiceTest.java` Bean-Validation-style assertions; otherwise plain JUnit + `Validator` from `jakarta.validation` | partial — no analog |
| `src/test/java/org/ctc/admin/dto/PhaseTeamFormTest.java` (NEW) | DTO-binding-test | (no existing analog) — Spring `BeanWrapperImpl` test pattern; otherwise plain JUnit assertion against `AutoPopulatingList` behavior | partial — no analog |
| `SeasonControllerTest.java` (EXTEND) | controller-test | self | self |
| `SeasonManagementServiceTest.java` (EXTEND) | service-test | self | self |
| `StandingsControllerTest.java` (EXTEND) | controller-test | self | self |
| `DriverSheetImportControllerTest.java` (EXTEND) | controller-test | self | self |
| `PlayoffControllerTest.java` (EXTEND) | controller-test | self | self |
| `SeasonPhaseServiceTest.java` (EXTEND) | service-test | self (Z. 41-71 Mockito-style with `@ExtendWith(MockitoExtension.class)` + `@InjectMocks` + `@Mock`-Repos) | self |

---

## Pattern Assignments

### `SeasonPhaseController.java` (controller, request-response CRUD)

**Analog:** `src/main/java/org/ctc/admin/controller/MatchdayController.java`

**Why this is the closest match:** Both controllers manage a child entity (Matchday / SeasonPhase) belonging to a parent (Season), expose `list / detail / create / edit / save / delete` flows over a shared `@RequestMapping`-prefix, mix MockitoBean-friendly graphics services with domain services, return Thymeleaf views, and use `RedirectAttributes` for `successMessage` flash. MatchdayController is also the cleanest BindingResult + form-template controller in the codebase.

**Class header pattern** (MatchdayController Z. 28-32):
```java
@Slf4j
@Controller
@RequestMapping("/admin/matchdays")
@RequiredArgsConstructor
public class MatchdayController {
    private final MatchdayService matchdayService;
    // ...
}
```

→ For SeasonPhaseController use `@RequestMapping("/admin/seasons/{seasonId}/phases")` (nested per D-03; Pitfall 5 — no collision with SeasonController). Inject `SeasonPhaseService`, `SeasonManagementService`, `MatchdayService`, `PhaseTeamRepository` plus dependent scoring repos for D-17 defaults.

**GET-with-form-population pattern** (MatchdayController Z. 67-77):
```java
@GetMapping("/new")
public String create(@RequestParam(required = false) UUID seasonId, Model model) {
    var form = new MatchdayForm();
    if (seasonId != null) {
        form.setSeasonId(seasonId);
        model.addAttribute("season", matchdayService.findSeasonById(seasonId));
    }
    model.addAttribute("form", form);
    model.addAttribute("seasons", matchdayService.getAllSeasons());
    return "admin/matchday-form";
}
```

→ Apply for D-17 "+ Add Phase" form with REGULAR-defaults pre-population (RESEARCH.md Pattern 2 already shows the exact code).

**POST-save-with-BindingResult pattern** (MatchdayController Z. 93-113):
```java
@PostMapping("/save")
public String save(@Valid @ModelAttribute("form") MatchdayForm form,
                   BindingResult result,
                   RedirectAttributes redirectAttributes,
                   Model model) {
    if (result.hasErrors()) {
        model.addAttribute("seasons", matchdayService.getAllSeasons());
        if (form.getSeasonId() != null) {
            model.addAttribute("season", matchdayService.findSeasonById(form.getSeasonId()));
        }
        return "admin/matchday-form";
    }
    var saved = matchdayService.saveMatchday(form.getLabel(), form.getSortIndex(), form.getSeasonId(), form.getId());
    redirectAttributes.addFlashAttribute("successMessage", "Matchday saved: " + saved.getLabel());
    return "redirect:/admin/matchdays?seasonId=" + form.getSeasonId();
}
```

**BusinessRuleException-flash-then-redirect pattern** (PlayoffController Z. 69-92, more idiomatic for Phase 60 than SeasonController's `IllegalStateException`):
```java
try {
    var playoff = playoffService.createPlayoff(...);
    redirectAttributes.addFlashAttribute("successMessage", "Playoff created: " + playoff.getName());
    return "redirect:/admin/playoffs?seasonId=" + form.getSeasonId();
} catch (IllegalArgumentException | IllegalStateException | BusinessRuleException e) {
    log.warn("Error creating playoff: {}", e.getMessage());
    redirectAttributes.addFlashAttribute("errorMessage", "Error: " + e.getMessage());
    return "redirect:/admin/playoffs/new?seasonId=" + form.getSeasonId();
}
```

→ Use `BusinessRuleException` (not `IllegalStateException`) for D-21/D-23/D-25/D-28 strict guards per CONVENTIONS. `GlobalExceptionHandler` already handles it (RESEARCH.md Z. 676-685).

**Conventions to mirror:**
- Class-level: `@Slf4j @Controller @RequestMapping("/admin/seasons/{seasonId}/phases") @RequiredArgsConstructor`
- Constructor injection via `final` fields (no `@Autowired`)
- `@PathVariable UUID seasonId` on every endpoint (pitfall 5)
- D-09 IDOR-safety: validate `phase.getSeason().getId().equals(seasonId)` and throw `EntityNotFoundException` if not
- POST actions return `redirect:` strings; pass `RedirectAttributes` parameter
- For BusinessRuleException: catch at controller, set `errorMessage` flash, redirect to form/list — never let it bubble to GlobalExceptionHandler if a flash-redirect is more user-friendly

---

### `SeasonPhaseGroupController.java` (controller, request-response CRUD + bulk-roster-save)

**Analog:** Same as above (`MatchdayController`) for CRUD shell, plus `PlayoffController.saveSeed` (Z. 146-155) for the indexed-properties bulk-save flow.

**Why this is the closest match:** This controller mixes per-entity CRUD (group create/edit/delete — same as MatchdayController) with a bulk multi-row save (the roster editor, D-20). The closest existing bulk-save endpoint is `PlayoffController.saveSeed` which receives a `SeedForm` containing `List<SeedEntry>` and bulk-converts to a service call.

**Bulk-save endpoint pattern** (PlayoffController Z. 146-155):
```java
@PostMapping("/{id}/seed")
public String saveSeed(@PathVariable UUID id, @ModelAttribute SeedForm form,
                       RedirectAttributes redirectAttributes) {
    var seeds = form.getSeeds().stream()
            .map(e -> new PlayoffSeedingService.SeedEntry(e.getMatchupId(), e.getSlot(), e.getTeamId(), e.getSeedNumber()))
            .toList();
    playoffSeedingService.saveSeed(id, seeds);
    redirectAttributes.addFlashAttribute("successMessage", "Seeding saved");
    return "redirect:/admin/playoffs?seasonId=" + playoffService.getSeasonIdForPlayoff(id);
}
```

→ Use this exact shape for `POST /admin/seasons/{sid}/phases/{pid}/roster`: receive `PhaseTeamForm` (with `List<Assignment> assignments`), convert each `Assignment` to a service-layer record, call `seasonPhaseService.assignTeamsToPhase(pid, assignments)` (Diff-Logic per RESEARCH.md Pitfall 8). Note: PlayoffController.saveSeed does NOT use `@Valid` on the bulk form — match this if validation is per-row only.

**Conventions to mirror:** Same as SeasonPhaseController. Add: extra path level `@RequestMapping("/admin/seasons/{seasonId}/phases/{phaseId}/groups")`.

---

### `SeasonPhaseForm.java` (DTO, form-binding)

**Analog:** `src/main/java/org/ctc/admin/dto/SeasonForm.java`

**Why this is the closest match:** Both are full-feature Form DTOs with the same Lombok stack and the same field families (name, dates, format, scoring refs, totalRounds, legs, eventDuration). SeasonPhaseForm absorbs exactly the fields UI-01 strips off SeasonForm.

**Lombok + jakarta.validation header pattern** (SeasonForm.java Z. 1-15):
```java
package org.ctc.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SeasonForm {

    private UUID id;

    @NotBlank
    private String name;
```

**Field-with-default + optional-field pattern** (SeasonForm.java Z. 34-40):
```java
private SeasonFormat format = SeasonFormat.LEAGUE;
private Integer totalRounds;       // optional → boxed
private int legs = 1;              // mandatory with default → primitive
private Integer eventDurationMinutes;  // optional → boxed
```

→ For SeasonPhaseForm:
- `@NotNull private UUID seasonId;` (parent FK like MatchdayForm Z. 21-22)
- `@NotNull private PhaseType phaseType;` (D-22)
- `@NotNull private PhaseLayout layout;` (D-22)
- `@NotNull private SeasonFormat format = SeasonFormat.LEAGUE;` (D-22)
- `private UUID raceScoringId;` and `private UUID matchScoringId;` (no `@NotNull` — empty edge case allowed; service-layer enforces)
- `private LocalDate startDate;`, `private LocalDate endDate;` — optional
- `private Integer totalRounds;`, `private int legs = 1;`, `private Integer eventDurationMinutes;` — copy verbatim from SeasonForm
- `private String label;` — optional (D-05 fallback to `phaseType.displayName`)
- `private Integer sortIndex;` — optional but auto-set on create per D-10 (REGULAR=0, PLAYOFF=10, PLACEMENT=20)

**Conventions to mirror:**
- Top-level Lombok block on class: `@Getter @Setter @NoArgsConstructor`
- Boxed types (`Integer`, `LocalDate`) for nullable fields, primitives for fields with defaults
- jakarta.validation imports (NOT javax.validation — Spring Boot 4.x is on Jakarta)
- Default values in field initializers (not in constructor)

---

### `SeasonPhaseGroupForm.java` (DTO, form-binding)

**Analog:** `src/main/java/org/ctc/admin/dto/MatchdayForm.java`

**Why this is the closest match:** MatchdayForm is the canonical "slim 4-field form" (id, label, sortIndex, parent-id) — exactly the shape D-19 prescribes for the Group-Form Step 1 (id, name, sortIndex, phaseId).

**Full file pattern** (MatchdayForm.java Z. 1-23):
```java
package org.ctc.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class MatchdayForm {

    private UUID id;

    @NotBlank
    private String label;

    private int sortIndex;

    @NotNull
    private UUID seasonId;
}
```

→ For SeasonPhaseGroupForm:
- `private UUID id;`
- `@NotBlank private String name;`
- `private Integer sortIndex;` (boxed because D-24 says "auto max+1 if null on create"; primitive `int` would conflate "0" with "auto")
- `@NotNull private UUID phaseId;`

**Conventions to mirror:** Inline Lombok line `@Getter @Setter @NoArgsConstructor` (MatchdayForm style — more compact than SeasonForm's 3-line block; pick whichever matches Plan-author preference).

---

### `PhaseTeamForm.java` (DTO, indexed-properties bulk-binding)

**Analog:** `src/main/java/org/ctc/admin/dto/SeedForm.java`

**Why this is the closest match:** SeedForm is the only existing DTO in the codebase that uses Spring's indexed-property binding (`form.seeds[N].field`). It contains a `List<NestedClass>` initialized to `new ArrayList<>()` and is consumed by a controller method that streams through `form.getSeeds()`. The exact same shape works for `PhaseTeamForm.assignments`.

**Full file pattern** (SeedForm.java Z. 1-29):
```java
package org.ctc.admin.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class SeedForm {

    private UUID playoffId;
    private List<SeedEntry> seeds = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    public static class SeedEntry {
        private UUID matchupId;
        private int slot;
        private UUID teamId;
        private Integer seedNumber;
    }
}
```

→ For PhaseTeamForm:
```java
@Getter @Setter @NoArgsConstructor
public class PhaseTeamForm {
    private UUID phaseId;
    private List<Assignment> assignments = new ArrayList<>();   // see Pitfall 2

    @Getter @Setter @NoArgsConstructor
    public static class Assignment {
        private UUID teamId;
        private boolean included;
        private UUID groupId;   // nullable
    }
}
```

**Pitfall 2 alternative — AutoPopulatingList:** RESEARCH.md Pitfall 2 recommends `new AutoPopulatingList<>(Assignment.class)` over `new ArrayList<>()` for safer auto-grow on form-POST. SeedForm uses plain ArrayList because the controller pre-populates the list size on GET (one-row-per-existing-matchup). For PhaseTeamForm the planner picks one approach explicitly:
- (a) Pre-populate on GET (one Assignment per `season.seasonTeams`) → ArrayList works
- (b) Use `AutoPopulatingList` for safety even when form is partially-empty

Recommendation: (a) is consistent with existing SeedForm pattern; (b) is documented for safety.

**Conventions to mirror:**
- Outer DTO has parent FK + `List<NestedClass>` initialized inline
- Static nested class for the row type (NOT a separate top-level DTO file)
- Primitives only inside nested class for required fields; boxed for nullable
- Naming: outer "Form", inner "Entry"/"Assignment"/etc.

---

### `season-phase-form.html` (template, form)

**Analog:** `src/main/resources/templates/admin/season-form.html` Z. 7-105 (full Phase-form scope) + structural cleanliness from `matchday-form.html`.

**Why this is the closest match:** SeasonForm contains exactly the field set (name, year, number, dates, format, totalRounds, legs, eventDurationMinutes, scoring) being moved into the new SeasonPhaseForm. Use SeasonForm's existing form-row blocks verbatim; only the wrapper, the action URL, and the th:object change.

**Template skeleton + BindingResult pattern** (season-form.html Z. 1-15, 47-105):
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{admin/layout :: layout('Edit Phase', ~{::section})}">
<body>
<section>
    <h1 th:text="${seasonPhaseForm.id != null ? 'Edit Phase' : 'New Phase'}"></h1>
    <div class="card">
        <form th:action="@{/admin/seasons/{sid}/phases/save(sid=${season.id})}"
              th:object="${seasonPhaseForm}" method="post">
            <input type="hidden" th:field="*{id}">
            <input type="hidden" th:field="*{seasonId}">

            <div class="form-row">
                <div class="form-group">
                    <label for="name">Label</label>
                    <input type="text" id="label" th:field="*{label}" placeholder="e.g. Regular Season">
                    <span class="field-error" th:if="${#fields.hasErrors('label')}" th:errors="*{label}"></span>
                </div>
                <!-- ... format, dates, rounds, legs, eventDuration: copy from season-form.html Z. 47-99 ... -->
            </div>

            <div class="actions mt-md">
                <button type="submit" class="btn btn-primary">Save</button>
                <a th:href="@{/admin/seasons/{id}(id=${season.id})}" class="btn btn-secondary">Cancel</a>
            </div>
        </form>
    </div>
</section>
</body>
</html>
```

**Field-error pattern** (season-form.html Z. 13-15):
```html
<input type="text" id="name" th:field="*{name}" placeholder="e.g. Regular Season" required>
<span class="field-error" th:if="${#fields.hasErrors('name')}" th:errors="*{name}"></span>
```

**Conventions to mirror:**
- `th:replace="~{admin/layout :: layout(<title>, ~{::section})}"` always at root html element
- `<section>` as wrapper
- `th:object="${formName}"` on `<form>`; `th:field="*{fieldName}"` on inputs
- Hidden id field at top: `<input type="hidden" th:field="*{id}">`
- Class layout: `card → form-row → form-group` nesting; spacing utility classes (`mt-md`)
- Button row: `<div class="actions mt-md">` with `btn btn-primary` (submit) + `btn btn-secondary` (cancel)
- No inline styles on buttons (CLAUDE.md hard rule)
- D-22 BindingResult-Error-Span on every field that has a constraint

---

### `season-phase-group-form.html` (template, slim form)

**Analog:** `src/main/resources/templates/admin/matchday-form.html`

**Why this is the closest match:** matchday-form.html is the canonical 40-line slim form with exactly the shape of D-19 Step 1 — `id`, `label`, `sortIndex`, parent FK (seasonId). Substitute label→name, seasonId→phaseId.

**Full template** (matchday-form.html Z. 1-39, used as-is with name substitutions):
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{admin/layout :: layout('Edit Group', ~{::section})}">
<body>
<section>
    <h1 th:text="${form.id != null ? 'Edit Group' : 'New Group'}"></h1>
    <div class="card">
        <form th:action="@{/admin/seasons/{sid}/phases/{pid}/groups/save(sid=${season.id}, pid=${phase.id})}"
              th:object="${form}" method="post">
            <input type="hidden" th:field="*{id}">
            <input type="hidden" th:field="*{phaseId}">
            <div class="form-row">
                <div class="form-group">
                    <label for="name">Name</label>
                    <input type="text" id="name" th:field="*{name}" placeholder="e.g. Group A" required>
                    <span class="field-error" th:if="${#fields.hasErrors('name')}" th:errors="*{name}"></span>
                </div>
                <div class="form-group">
                    <label for="sortIndex">Sort Order</label>
                    <input type="number" id="sortIndex" th:field="*{sortIndex}" min="0">
                </div>
            </div>
            <div class="actions mt-md">
                <button type="submit" class="btn btn-primary">Save Group</button>
                <a th:href="@{/admin/seasons/{sid}/phases/{pid}(sid=${season.id}, pid=${phase.id})}" class="btn btn-secondary">Cancel</a>
            </div>
        </form>
    </div>
</section>
</body>
</html>
```

**Conventions to mirror:** Same as season-phase-form.html (root th:replace, section wrapper, card→form-row→form-group, action buttons). D-19 Step 2 (Roster-Editor) is rendered as a separate Section inside the **Phase-Detail-Tab** — not in this Group-Form. The Roster-Editor uses the indexed-properties pattern from RESEARCH.md Pattern 3.

---

### `season-detail.html` (REWRITE — modified template, render-with-tabs)

**Analogs (composite):**
- `src/main/resources/templates/admin/template-editors.html` Z. 11-42 — for the Phase-Tab pattern
- Existing `src/main/resources/templates/admin/season-detail.html` Z. 6-90 — for the Saison-Header / Teams / Cars / Tracks block to preserve (D-02)

**Why these are the closest matches:** template-editors.html is the only existing template that does server-rendered tabs (`tab-btn` + `tab-active` link-style, no JS). The header block in current season-detail.html is the verbatim "Saison-Header" mandated by D-02 — only the toolbar (Z. 6-20) gets D-07-trimmed.

**Tab pattern** (template-editors.html Z. 11-42 — load-bearing snippet):
```html
<div class="editor-tab-bar">
    <a th:href="@{/admin/tools/template-editors(tab='team-cards')}"
       th:classappend="${activeTab == 'team-cards' ? 'tab-active' : ''}"
       class="tab-btn">Team Cards</a>
    <a th:href="@{/admin/tools/template-editors(tab='lineup')}"
       th:classappend="${activeTab == 'lineup' ? 'tab-active' : ''}"
       class="tab-btn">Race Lineup</a>
    <!-- ... -->
</div>
```

→ For Phase-Tabs Row 1, replace static `<a>` tags with a `th:each` loop over `${allPhases}`; URL pattern `@{/admin/seasons/{sid}/phases/{pid}(sid=${season.id}, pid=${p.id})}`; active-class via `${p.id == phase.id ? 'tab-active' : ''}`. For Group-Sub-Tabs Row 2, identical loop over `${phase.groups}` plus a leading "Combined" link.

**Saison-Header to preserve** (current season-detail.html Z. 6-20 → trim Phase-aware actions):
```html
<div class="toolbar">
    <div>
        <a th:href="@{/admin/seasons}" class="back-link">&larr; Back to Seasons</a>
        <h1 th:text="${season.name}"></h1>
    </div>
    <div class="actions">
        <!-- D-07: keep ONLY season-wide actions (Edit / Delete); REMOVE Swiss-Rounds, Generate-Matchdays -->
        <a th:href="@{/admin/seasons/{id}/edit(id=${season.id})}" class="btn btn-primary">Edit</a>
        <form th:action="@{/admin/seasons/{id}/delete(id=${season.id})}" method="post"
              onsubmit="return confirm('Really delete this season?')">
            <button type="submit" class="btn btn-danger">Delete</button>
        </form>
    </div>
</div>
```

**Existing modal / SeasonTeam-edit / Replace-Team blocks** (Z. 99-186 + Z. 188-240 JS): keep as-is — Saison-Teams are season-wide per D-02.

**Conventions to mirror:**
- Reuse `editor-tab-bar` + `tab-btn` + `tab-active` CSS (already in admin.css Z. 1038-1056, 1781-1786)
- Add a new `tabs-secondary` CSS class for Row 2 (RESEARCH.md Pattern 1 has the exact CSS-Append)
- No JS for tab-switching — server-routing per tab
- Card-stack within each Phase-Tab (Roster → Matchdays → Standings → ggf. Bracket) per D-04
- Server provides `effectivePhaseLabel` (Pitfall 9) — never compute label fallback in template
- `th:if="${combinedView}"` / `th:if="${phase.layout.name() == 'GROUPS'}"` — server-flag-driven

---

### `standings.html` (MODIFY — render-with-tabs + conditional-columns)

**Analogs:**
- `template-editors.html` Z. 11-42 — for the Phase-Tabs Row 1 (D-29)
- Existing `standings.html` Z. 6-15 — for the Saison-Dropdown to keep (D-34)
- Existing `standings.html` Z. 22-83 — for the table skeleton (sortable headers, `th:if`-conditional column rendering pattern already exists between LEAGUE and SWISS tables Z. 28 vs. Z. 59)

**Why this is the closest match:** standings.html already uses `th:if="${selectedSeason != null && selectedSeason.format.name() == 'SWISS' ...}"` to conditionally render two different tables — the exact server-flag-driven pattern D-32/D-33 needs. The Saison-Dropdown form (Z. 8-15) also stays.

**Existing conditional-table pattern** (standings.html Z. 28-30 — load-bearing snippet to extend):
```html
<table th:if="${!standings.isEmpty() && (selectedSeason == null || selectedSeason.format.name() != 'SWISS')}" id="standingsTable">
    <thead>
        <tr>
            <th>#</th>
            <th class="sortable" data-col="1" data-type="text" aria-label="Sort by Team">Team</th>
```

→ Replace `selectedSeason.format.name() != 'SWISS'` with server-flag conditions:
- `th:if="${combinedView}"` to show the Group column (D-32)
- `th:if="${showBuchholz}"` to show the Buchholz column (D-33)
- `th:if="${showGroupColumn}"` (mirrored across standings.html and driver-import-preview.html — D-32/D-40 share this flag-driven pattern)

**Existing Saison-Dropdown to keep** (standings.html Z. 6-16):
```html
<div class="toolbar">
    <h1>Standings &amp; Driver Ranking</h1>
    <form method="get" th:action="@{/admin/standings}" class="btn-with-hint">
        <select name="seasonId" onchange="this.form.submit()">
            <option value="">-- Select season --</option>
            <option value="alltime" th:selected="${isAlltime}">Alltime</option>
            <option th:each="s : ${seasons}" th:value="${s.id}" th:text="${s.displayLabel}"
                    th:selected="${selectedSeason != null && s.id == selectedSeason.id}"></option>
        </select>
    </form>
</div>
```

**Conventions to mirror:**
- Saison-Dropdown stays at top (D-34)
- Two new tab-rows below the dropdown (Phase-Tabs + Group-Sub-Tabs) using template-editors-style `tab-btn`/`tab-active`
- All conditional rendering driven by **server-side flags** — never project SpEL or recompute (CLAUDE.md "Lean Templates")
- D-36 Empty-State Banner: reuse existing `<div class="empty-state"><p>...</p></div>` pattern (already at Z. 84)

---

### `driver-import-preview.html` (MODIFY — conditional column + inline badge)

**Analog:** Existing `driver-import-preview.html` itself.

**Why this is the closest match:** The TabWarning-Banner block already exists (Z. 31-39) — only needs:
1. **D-37 fix** Z. 14 `<h2 th:text="${tab.year()}">` → `<h2 th:text="${tab.tabName()}">` (Pitfall 10)
2. **D-39 inline badge** new `<td>` per Driver-row when `row.resolvedGroupName == null && phase.layout == GROUPS`
3. **D-40 conditional Group-column** new `<th>Group</th>` + `<td>` only when `showGroupColumn == true` (server-flag from controller)

**Existing TabWarning-Banner to preserve** (Z. 31-39):
```html
<!-- TabWarning badges: teams with no PhaseTeam in REGULAR phase (IMPORT-04 / D-06) -->
<div class="alert alert-warning mb-md"
     th:if="${!tab.warnings().isEmpty()}">
    <strong>Group assignment warnings</strong>
    <ul>
        <li th:each="warning : ${tab.warnings()}"
            th:text="${warning.teamShortName() + ' — ' + warning.message()}"></li>
    </ul>
</div>
```

**Existing buckets-with-table pattern** (Z. 41-55) — extend each bucket's `<thead>` and `<tbody>` to add the Group column:
```html
<th:block th:if="${!tab.newDrivers().isEmpty()}">
    <h3 th:text="'New Drivers (' + ${#lists.size(tab.newDrivers())} + ')'"></h3>
    <div class="table-scroll">
        <table>
            <thead><tr>
                <th>PSN ID</th>
                <th>Team</th>
                <th th:if="${showGroupColumn}">Group</th>   <!-- D-40 NEW -->
            </tr></thead>
            <tbody>
                <tr th:each="row : ${tab.newDrivers()}">
                    <td th:text="${row.psnId()}"></td>
                    <td th:text="${row.teamShortName()}"></td>
                    <td th:if="${showGroupColumn}">
                        <span th:if="${row.resolvedGroupName() != null}" th:text="${row.resolvedGroupName()}"></span>
                        <span th:if="${row.resolvedGroupName() == null}" class="badge badge-warning"
                              th:title="${row.teamShortName()}">&#x26A0; No group</span>   <!-- D-39 inline-badge -->
                    </td>
                </tr>
            </tbody>
        </table>
    </div>
</th:block>
```

**Conventions to mirror:**
- Server-flag `${showGroupColumn}` (D-40) computed in `DriverSheetImportController` from `targetPhase.layout == GROUPS`
- Use `class="badge badge-warning"` — existing badge classes (no inline styles)
- `tab.tabName()` not `tab.year()` (Pitfall 10) — verified at `DriverSheetImportService.TabPreview` Z. 405-407 in research

---

### `playoff-bracket.html` (MODIFY — UI hide-only)

**Analog:** Existing `playoff-bracket.html` itself.

**Why this is the closest match:** D-43 says: backend endpoints stay, UI hides them. The "Linked Seasons" block (Z. 96-121) is the entire UI to remove. The remainder of the template (bracket rendering) stays untouched.

**Block to remove** (playoff-bracket.html Z. 96-121):
```html
<!-- Linked Seasons (Team sources for Playoffs) -->
<div th:if="${playoff != null}" class="card">
    <h2>Linked Seasons (Team Sources)</h2>
    <p class="text-dim mb-sm">Teams from these seasons are available in the seeding.</p>
    <div th:if="${!playoff.seasons.isEmpty()}" class="chip-list">
        <span th:each="s : ${playoff.seasons}" class="chip" style="gap:8px;">
            <a th:href="@{/admin/seasons/{id}(id=${s.id})}" th:text="${s.displayLabel}" style="color:var(--text);text-decoration:none;"></a>
            <form th:action="@{/admin/playoffs/{id}/remove-season(id=${playoff.id})}" method="post" class="form-inline">
                <input type="hidden" name="seasonId" th:value="${s.id}">
                <button type="submit" class="btn btn-danger btn-xs">Remove</button>
            </form>
        </span>
    </div>
    <!-- ... add-season form ... -->
</div>
```

**Convention:** Pure delete — D-43 is "UI hides them"; planner picks (a) physical removal of the block or (b) `th:if="${false}"` wrapping. Recommendation: physical removal (cleaner, avoids dead-code-leftovers). Backend endpoints (`addSeason`, `removeSeason` in PlayoffController Z. 103-117) stay untouched per D-43 + Pitfall 6.

---

### `season-form.html` (SLIM — modified template, form)

**Analog:** Self.

**Why this is the closest match:** UI-01 strips fields off this template. The remaining shape is the same template Z. 7-22 + Z. 100-104 (header card with name/year/number/description/active + actions row). Everything between (Z. 23-99 — startDate, endDate, format, totalRounds, legs, eventDurationMinutes, scoring) gets removed.

**Block to keep verbatim** (season-form.html Z. 8-22, modify only by removing Z. 23-99):
```html
<form th:action="@{/admin/seasons/save}" th:object="${seasonForm}" method="post">
    <input type="hidden" th:field="*{id}">
    <div class="form-row">
        <div class="form-group">
            <label for="name">Name</label>
            <input type="text" id="name" th:field="*{name}" placeholder="e.g. Regular Season" required>
            <span class="field-error" th:if="${#fields.hasErrors('name')}" th:errors="*{name}"></span>
        </div>
        <div class="form-group">
            <div class="form-check mt-xl">
                <input type="checkbox" id="active" th:field="*{active}">
                <label for="active">Active Season</label>
            </div>
        </div>
    </div>
```

**Convention:** Keep Year, Number, Description, Active, Name. Drop everything else. Team/Cars/Tracks blocks at Z. 109-232 stay as-is (these are saison-wide and survive UI-01).

---

### `SeasonController.java` (MODIFY — slim save)

**Analog:** Self.

**Existing save signature to slim** (SeasonController.java Z. 84-99):
```java
@PostMapping("/save")
public String save(@Valid @ModelAttribute("seasonForm") SeasonForm form, BindingResult result,
                   @RequestParam UUID raceScoring,
                   @RequestParam UUID matchScoring,
                   RedirectAttributes redirectAttributes, Model model) {
    if (result.hasErrors()) {
        addScoringLists(model);
        return "admin/season-form";
    }
    var season = seasonManagementService.save(form.getId(), form.getName(), form.getYear(),
            form.getNumber(), form.getDescription(), form.getStartDate(), form.getEndDate(),
            form.isActive(), form.getFormat(), form.getTotalRounds(), form.getLegs(),
            form.getEventDurationMinutes(), raceScoring, matchScoring);
    redirectAttributes.addFlashAttribute("successMessage", "Season saved: " + season.getName());
    return "redirect:/admin/seasons";
}
```

**Slim target:**
- Remove `@RequestParam UUID raceScoring`
- Remove `@RequestParam UUID matchScoring`
- Remove `addScoringLists(model)` call
- Slim service-call signature to `save(form.getId(), form.getName(), form.getYear(), form.getNumber(), form.getDescription(), form.isActive())`
- Remove `addScoringLists` private helper (Z. 249-252) — no callers left
- Remove SeasonForm field-population from edit() that touches removed fields (Z. 67-73)
- Keep swissRounds (Z. 194-203), generate (Z. 217-247) — Wave 3 task per RESEARCH.md Wave Plan §Wave 3 Task 5 internal-resolve-to-REGULAR-phase

---

### `StandingsController.java` (MODIFY — phase-canonical + legacy bridge)

**Analog:** Self.

**Current standings endpoint to extend** (StandingsController.java Z. 27-62):
```java
@GetMapping
public String standings(@RequestParam(required = false) String seasonId, Model model) {
    boolean isAlltime = "alltime".equals(seasonId);

    if (isAlltime) {
        model.addAttribute("standings", standingsService.calculateAlltimeStandings());
        // ...
    } else {
        // resolve season → call calculateStandings(season.getId()) or calculateStandingsWithBuchholz
    }
}
```

**Target shape (D-31 + D-12 + D-30/D-32/D-33):**
- Add `@RequestParam(required = false) UUID phase` (canonical)
- Add `@RequestParam(required = false) UUID group` (canonical, nullable for Combined)
- Keep `@RequestParam(required = false) String seasonId` (legacy bridge per D-12)
- Resolution priority: `phase` → use directly; `seasonId` → resolve via `seasonPhaseService.findByType(seasonId, PhaseType.REGULAR)` (Pitfall 4 — Optional, not throw)
- Server-flags model attributes: `combinedView` (`group == null && phase.layout == GROUPS`), `showBuchholz` (`phase.format == SWISS && group != null`), `showGroupColumn` (`phase.layout == GROUPS && group == null`)
- Switch service calls from `calculateStandings(seasonId)` to `calculateStandings(phaseId, groupId)` (Phase 58 D-04 — already canonical per RESEARCH.md "Don't Hand-Roll")

---

### `MatchdayController.java` (MODIFY — D-44 conservative cleanup)

**Analog:** Self.

**D-44 cutover scope:** Only the `findBySeasonId`-Bridge call. Per RESEARCH.md Cleanup-Map, no production caller of `MatchdayService.findBySeasonId(seasonId)` exists in the controller — that's the JSON `getMatchdaysBySeason` (Z. 185), which is a different method. So MatchdayController itself needs **no method-signature changes** in Phase 60. The `findBySeasonId` Bridge can be dropped from MatchdayService once the test file is updated.

**Convention:** Don't touch MatchdayController code. Touch `MatchdayService` only if confirmed by a `grep` pass before removal (RESEARCH.md Wave 4 Task 1 + Pitfall 6).

---

### `SeasonManagementService.java` (MODIFY — slim save + atomic team sync)

**Analog:** Self.

**D-25 / UI-01 — Auto-Sync-Block to remove:** Service method `save(...)` lines ~200-222 (per RESEARCH.md "Pitfall 1") sync `format/scoring/dates/rounds/legs/eventDuration` onto the REGULAR phase. Remove the entire block. New behavior per RESEARCH.md Open Question #1 + Recommendation: Auto-bootstrap a REGULAR-Phase with `format=null` so D-08 Empty-State is the defensive-only path.

**D-26 / addTeamToSeason — atomic PhaseTeam insert:** Existing `@Transactional` boundary stays; add a `seasonPhaseService.findByType(seasonId, PhaseType.REGULAR)` call followed by `phaseTeamRepository.save(new PhaseTeam(regularPhase, team))`. RESEARCH.md Pitfall 7 has the exact pattern.

**D-25 / removeTeamFromSeason — strict guard:** Add a `phaseTeamRepository.findByPhaseIdAndTeamId`-based pre-check across all phases of the season; throw `BusinessRuleException` if any PhaseTeam-row references the team.

**Convention:** Use `BusinessRuleException` (not `IllegalStateException`) — consistent with Phase 58 D-18/D-23 + GlobalExceptionHandler (RESEARCH.md Z. 671 "IllegalStateException is Legacy").

---

### `SeasonPhaseService.java` (EXTEND — new CRUD methods)

**Analog:** Self.

**Existing API surface** (RESEARCH.md Z. 692-700) — Phase 58 already canonical:
```java
seasonPhaseService.findRegularPhase(seasonId);
seasonPhaseService.findByType(seasonId, PhaseType.PLAYOFF);  // Optional
seasonPhaseService.findById(phaseId);
seasonPhaseService.findAllPhases(seasonId);                  // sorted by sortIndex
seasonPhaseService.create(seasonId, type, layout, sortIndex, label, raceScoring, matchScoring,
                          format, startDate, endDate, totalRounds, legs, eventDurationMinutes);
seasonPhaseService.createGroup(phaseId, name, sortIndex);
seasonPhaseService.assignTeamToPhase(phaseId, teamId, groupId);
```

**New methods to add (Phase 60):**
- `update(phaseId, ...)` — D-21 + D-22 layout/format compatibility pre-check; throw `BusinessRuleException` on Matchday/PhaseTeam-existence collision
- `delete(phaseId)` — D-23 strict guard against existing Matchdays/PlayoffMatches/PhaseTeams
- `updateGroup(groupId, name, sortIndex)` — D-24 manual sortIndex
- `deleteGroup(groupId)` — D-28 strict guard against existing PhaseTeams (group references) or matchdays
- `assignTeamsToPhase(phaseId, List<Assignment>)` — D-20 Bulk-Diff per RESEARCH.md Pitfall 8

**Convention:** Use `@Transactional(readOnly = true)` on read methods; `@Transactional` (full) on write methods. Throw `EntityNotFoundException` for missing entities; `BusinessRuleException` for guard failures.

---

### `admin.css` (APPEND)

**Analog:** Self (existing `.tab-btn` Z. 1038-1056, `.tab-nav` Z. 1740-1748, `.editor-tab-bar` Z. 1781-1786).

**Existing tab-button styles** (admin.css Z. 1038-1056):
```css
.tab-btn {
    padding: 10px 24px;
    font-size: 14px;
    font-weight: 600;
    color: var(--text-dim);
    text-decoration: none;
    border-bottom: 2px solid transparent;
    margin-bottom: -2px;
    transition: color 0.2s, border-color 0.2s;
}

.tab-btn:hover { color: var(--white); }

.tab-btn.tab-active {
    color: var(--accent);
    border-bottom-color: var(--accent);
}
```

**Append target (D-11 mobile-scroll + D-29 secondary-tab-row):** Use RESEARCH.md Pattern 1 CSS-block verbatim (`tabs-secondary` + mobile media-query). Place at end of admin.css; reuse CSS custom properties (`var(--border)`, `var(--bg-card)`, `var(--accent)`).

---

## Pattern Assignments — Test Files

### `SeasonPhaseControllerTest.java` (NEW — controller test)

**Analog:** `src/test/java/org/ctc/admin/controller/SeasonControllerTest.java`

**Why this is the closest match:** SeasonControllerTest already uses the exact stack the new tests need: `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("dev") @Transactional` + `TestHelper`-injection + MockMvc-form-POSTs.

**Class header pattern** (SeasonControllerTest.java Z. 27-44):
```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class SeasonControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SeasonRepository seasonRepository;
    @Autowired
    private TestHelper testHelper;
```

**GET-detail test pattern** (SeasonControllerTest.java Z. 47-54, BDD-style per CLAUDE.md):
```java
@Test
void whenGetSeasons_thenReturnsSeasonsView() throws Exception {
    // when
    mockMvc.perform(get("/admin/seasons"))
            // then
            .andExpect(status().isOk())
            .andExpect(view().name("admin/seasons"))
            .andExpect(model().attributeExists("seasons"));
}
```

**POST-save test pattern** (SeasonControllerTest.java Z. 67-82):
```java
@Test
void givenValidScoringRefs_whenSaveSeason_thenRedirects() throws Exception {
    // given
    var rs = testHelper.createSeason("Dummy").getRaceScoring();
    var ms = testHelper.createSeason("Dummy2").getMatchScoring();

    // when
    mockMvc.perform(post("/admin/seasons/save")
                    .param("name", "MockMvc Test Season")
                    .param("active", "true")
                    .param("raceScoring", rs.getId().toString())
                    .param("matchScoring", ms.getId().toString()))
            // then
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/seasons"));
}
```

**MockitoBean pattern for graphics services** (MatchdayControllerTest.java Z. 37-40):
```java
@MockitoBean private MatchdayOverviewGraphicService overviewGraphicService;
@MockitoBean private MatchdayScheduleGraphicService scheduleGraphicService;
```

→ For SeasonPhaseControllerTest, no graphics-service mocks needed (controllers call SeasonPhaseService + MatchdayService only). Use `@Autowired` for repositories you need to query post-action.

**Conventions to mirror:**
- `givenContext_whenAction_thenExpectedResult` test naming
- `// given / // when / // then` body comments
- `@Transactional` rolls back each test
- Use `TestHelper.createSeason("T-prefix Phase60-...")` for test isolation (CLAUDE.md hard rule)
- For BindingResult-error tests: `.andExpect(status().isOk()).andExpect(view().name("admin/season-phase-form"))` — same as SeasonControllerTest Z. 85-97
- For BusinessRule-flash-error tests: `.andExpect(redirectedUrl(...)).andExpect(flash().attributeExists("errorMessage"))`

---

### `SeasonPhaseGroupControllerTest.java` (NEW)

**Analog:** Same as SeasonPhaseControllerTest. Test the same shapes for Group-CRUD + Roster-Bulk-Save.

For the bulk-roster-save test, build POST params as `assignments[0].teamId=...&assignments[0].included=true&assignments[0].groupId=...&assignments[1].teamId=...&...` (Spring binds indexed properties).

---

### `SeasonPhaseControllerIT.java` + `SeasonPhaseGroupControllerIT.java` (NEW)

**Analog:** `DriverSheetImportServiceIT.java` (for IT-shape) + SeasonControllerTest (already uses IT-shape via `@SpringBootTest @Transactional`).

**Note:** SeasonControllerTest is already a hybrid — it uses `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("dev") @Transactional`. The Phase 58 D-13 IT-shape (`@SpringBootTest @ActiveProfiles("dev") @Transactional` per `DriverSheetImportServiceIT.java` Z. 48-51) is identical for service-IT but doesn't add MockMvc. For controller-IT, mirror SeasonControllerTest.

**Class header — preferred IT shape** (DriverSheetImportServiceIT.java Z. 48-51 plus `@AutoConfigureMockMvc` for MockMvc):
```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class SeasonPhaseControllerIT {
    @Autowired private MockMvc mockMvc;
    @Autowired private SeasonRepository seasonRepository;
    @Autowired private SeasonPhaseRepository seasonPhaseRepository;
    @Autowired private TestHelper testHelper;
```

→ For IT vs. Test split: keep "Test" suffix for those that exercise controller logic with @MockitoBean for services; use "IT" suffix when exercising the full service-stack with persistent test-data via `TestHelper`/`TestDataService`. RESEARCH.md Wave 0 lists both as separate files for the new SeasonPhase controllers.

---

### `SeasonPhaseFormTest.java` + `PhaseTeamFormTest.java` (NEW — DTO validation/binding tests)

**Analog:** No existing `*FormTest.java` in the codebase (the existing tests validate DTO behavior indirectly via controller-tests). For Phase 60, RESEARCH.md Wave 0 lists these as **optional** (RESEARCH.md Z. 750-751 marks them "(optional)"). The planner can either:
- (a) Skip dedicated FormTest classes; rely on controller-tests to catch validation indirectly (current codebase convention).
- (b) Add lightweight `jakarta.validation.Validator`-based unit tests for explicit Bean-Validation coverage.

**Recommendation:** Skip dedicated FormTest files unless the JaCoCo coverage report shows a gap on the DTO classes after Wave 1. Optional skeleton if needed:
```java
class SeasonPhaseFormTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void givenNullPhaseType_whenValidate_thenViolation() {
        // given
        var form = new SeasonPhaseForm();
        form.setLayout(PhaseLayout.LEAGUE);
        form.setFormat(SeasonFormat.LEAGUE);
        // when
        var violations = validator.validate(form);
        // then
        assertThat(violations).extracting("propertyPath").asString().contains("phaseType");
    }
}
```

---

### `SeasonPhaseServiceTest.java` (EXTEND)

**Analog:** Existing `src/test/java/org/ctc/domain/service/SeasonPhaseServiceTest.java`.

**Class header to mirror** (Z. 23-39):
```java
@ExtendWith(MockitoExtension.class)
class SeasonPhaseServiceTest {

    @Mock
    private SeasonPhaseRepository seasonPhaseRepository;
    @Mock
    private SeasonPhaseGroupRepository seasonPhaseGroupRepository;
    @Mock
    private PhaseTeamRepository phaseTeamRepository;
    @Mock
    private SeasonRepository seasonRepository;

    @InjectMocks
    private SeasonPhaseService seasonPhaseService;
```

**BDD-test method pattern** (SeasonPhaseServiceTest.java Z. 45-71):
```java
@Test
void givenSeasonWithRegularPhase_whenFindRegularPhase_thenReturnsPhase() {
    // given
    var season = buildSeason("Phase58-Test-Season-1");
    var phase = PhaseTestFixtures.regularPhase(season, season.getRaceScoring(), season.getMatchScoring());
    when(seasonPhaseRepository.findBySeasonIdAndPhaseType(season.getId(), PhaseType.REGULAR))
            .thenReturn(Optional.of(phase));

    // when
    SeasonPhase result = seasonPhaseService.findRegularPhase(season.getId());

    // then
    assertThat(result).isEqualTo(phase);
}

@Test
void givenMissingRegularPhase_whenFindRegularPhase_thenThrowsEntityNotFound() {
    // given
    var seasonId = UUID.randomUUID();
    when(seasonPhaseRepository.findBySeasonIdAndPhaseType(seasonId, PhaseType.REGULAR))
            .thenReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> seasonPhaseService.findRegularPhase(seasonId))
            .isInstanceOf(EntityNotFoundException.class);
}
```

**Conventions to mirror:**
- Mockito-based (no @SpringBootTest) for fast service-unit tests
- `PhaseTestFixtures.regularPhase(...)` helper already exists — reuse for new `update`/`delete`/`assignTeamsToPhase` tests
- BDD body comments `// given / // when / // then` are mandatory
- `assertThatThrownBy(...).isInstanceOf(BusinessRuleException.class)` for D-21/D-23/D-28 guard tests

---

## Shared Patterns

### Authentication / Routing Safety (D-09 IDOR)

**Source:** `src/main/java/org/ctc/admin/controller/GlobalExceptionHandler.java` Z. 28-50 (handles `EntityNotFoundException` → 404, `BusinessRuleException` → 409)

**Apply to:** `SeasonPhaseController`, `SeasonPhaseGroupController`

**Pattern:** In every endpoint that takes both `@PathVariable UUID seasonId` and `@PathVariable UUID phaseId` (or `groupId`), validate parent-child consistency before proceeding:

```java
// In every endpoint that loads a phase by phaseId scoped to a seasonId
var phase = seasonPhaseService.findById(phaseId);
if (!phase.getSeason().getId().equals(seasonId)) {
    throw new EntityNotFoundException("Phase not found for season: " + seasonId);
}
```

→ GlobalExceptionHandler converts to 404. No new exception classes needed.

---

### Error Handling — BusinessRuleException via Flash (Phase 60 idiom)

**Source:** `src/main/java/org/ctc/admin/controller/PlayoffController.java` Z. 69-92

**Apply to:** All POST endpoints in `SeasonPhaseController`, `SeasonPhaseGroupController` that call service methods which can throw `BusinessRuleException` (D-21, D-23, D-25, D-28).

**Pattern:**
```java
try {
    seasonPhaseService.delete(phaseId);
    redirectAttributes.addFlashAttribute("successMessage", "Phase deleted");
    return "redirect:/admin/seasons/" + seasonId;
} catch (BusinessRuleException e) {
    log.warn("Cannot delete phase {}: {}", phaseId, e.getMessage());
    redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
    return "redirect:/admin/seasons/" + seasonId + "/phases/" + phaseId;
}
```

**Anti-pattern:** Don't use `IllegalStateException` (legacy from SeasonController.removeTeam Z. 109-119). Use `BusinessRuleException` for new code (RESEARCH.md Z. 671 explicit instruction).

---

### Validation — @Valid + BindingResult + Field-Errors

**Source:** `src/main/java/org/ctc/admin/controller/MatchdayController.java` Z. 93-104 + `src/main/resources/templates/admin/season-form.html` Z. 13-15

**Apply to:** `SeasonPhaseController.save`, `SeasonPhaseGroupController.save` and all corresponding form templates.

**Controller pattern:**
```java
@PostMapping("/save")
public String save(@Valid @ModelAttribute("seasonPhaseForm") SeasonPhaseForm form,
                   BindingResult result,
                   @PathVariable UUID seasonId,
                   RedirectAttributes redirectAttributes,
                   Model model) {
    if (result.hasErrors()) {
        // re-populate model with anything the template needs
        model.addAttribute("season", seasonManagementService.findById(seasonId));
        addScoringLists(model);
        return "admin/season-phase-form";
    }
    // ... service call + redirect
}
```

**Template pattern** (per-field):
```html
<input type="text" id="name" th:field="*{name}" required>
<span class="field-error" th:if="${#fields.hasErrors('name')}" th:errors="*{name}"></span>
```

---

### Server-Side Flag Convention (Lean Templates)

**Source:** Existing `standings.html` Z. 28 — already uses `selectedSeason.format.name() == 'SWISS'` to switch tables. Phase 60 introduces clean server-flags instead of inline SpEL.

**Apply to:** `season-detail.html`, `standings.html`, `driver-import-preview.html`, `season-phase-form.html`, `season-phase-group-form.html`

**Pattern:** Controller computes flags in domain-language; template only branches on them:
```java
// Controller
model.addAttribute("combinedView", phase.getLayout() == PhaseLayout.GROUPS && groupId == null);
model.addAttribute("showBuchholz", phase.getFormat() == SeasonFormat.SWISS && groupId != null);
model.addAttribute("showGroupColumn", phase.getLayout() == PhaseLayout.GROUPS);
model.addAttribute("effectivePhaseLabel",
    phase.getLabel() != null && !phase.getLabel().isBlank()
        ? phase.getLabel()
        : phase.getPhaseType().getDisplayName());
```

```html
<!-- Template -->
<th th:if="${showGroupColumn}">Group</th>
<th th:if="${showBuchholz}">Buchholz</th>
<h1 th:text="${season.name + ' — ' + effectivePhaseLabel}"></h1>
```

**Anti-pattern:** Never project SpEL in templates (`${season.phases.?[phaseType.name() == 'REGULAR']}`) — CLAUDE.md hard rule.

---

### Tab-Pattern (Two-Row Server-Routed)

**Source:** `src/main/resources/templates/admin/template-editors.html` Z. 11-42

**Apply to:** `season-detail.html` (Phase-Tabs Row 1 + Group-Sub-Tabs Row 2), `standings.html` (Phase-Tabs Row 1 + Group-Sub-Tabs Row 2)

**Pattern:** RESEARCH.md Pattern 1 (verbatim) — link-based, server-routed, no JS. Tab-Container uses class `tab-nav` (existing) for Row 1, `tab-nav tabs-secondary` (NEW) for Row 2. Each `<a class="tab-btn">` toggles `tab-active` via `th:classappend`. Mobile-scroll via media-query (RESEARCH.md Pattern 1 CSS-block).

---

### Test Data Isolation (T-Prefix)

**Source:** CLAUDE.md "Isolate Test Data Completely" + `TestHelper.createSeason(...)` usage in SeasonControllerTest Z. 70.

**Apply to:** All Phase-60 controller tests + IT.

**Pattern:**
```java
var season = testHelper.createSeason("T-Phase60-PhaseDetail");
```

→ Use stable T-prefix names (e.g., "T-Phase60-Detail", "T-Phase60-Roster", "T-Phase60-Group-A") — never reuse production data.

**Important update for Phase 60:** RESEARCH.md "Test-Data Setup" notes that `testHelper.createSeason(...)` historically relied on the Phase 58 D-25 Auto-Sync to create a REGULAR phase. UI-01 removes that block. Per RESEARCH.md Open Question #1 + Recommendation, after Phase 60 the Auto-Bootstrap moves into a new code path (e.g., `SeasonManagementService.save` always creates a REGULAR phase with `format=null`), so `TestHelper.createSeason` continues to produce a season with REGULAR phase. Verify behavior of `TestHelper.createSeason` early in Wave 1.

---

## No Analog Found

No new file in this phase lacks an existing analog. The closest-match table at the top is fully populated. Two test-file types (`SeasonPhaseFormTest`, `PhaseTeamFormTest`) have no in-codebase analog but RESEARCH.md marks them as optional and the recommended pattern uses generic `jakarta.validation.Validator` boilerplate.

---

## Metadata

**Analog search scope:**
- `src/main/java/org/ctc/admin/controller/` — all controllers
- `src/main/java/org/ctc/admin/dto/` — all DTOs
- `src/main/resources/templates/admin/` — all templates
- `src/main/resources/static/admin/css/admin.css` — tab CSS classes
- `src/test/java/org/ctc/admin/controller/` — controller-tests
- `src/test/java/org/ctc/domain/service/SeasonPhaseServiceTest.java` — service-test
- `src/test/java/org/ctc/dataimport/DriverSheetImportServiceIT.java` — IT shape
- `src/test/java/org/ctc/TestHelper.java` — test-fixture helper

**Files scanned:** ~22

**Key insight for the planner:** Phase 60 is overwhelmingly a **pattern-replication phase**, not a pattern-invention phase. Every new file has an exact or strong-role analog in the codebase. The only genuinely new structure is the **Two-Row Tab CSS class `tabs-secondary`** + the mobile-scroll media-query — and even that is described verbatim in RESEARCH.md Pattern 1 and uses existing CSS variables.

**Pattern extraction date:** 2026-04-30
