# Phase 18: Merge UI - Research

**Researched:** 2026-04-07
**Domain:** Spring Boot MVC / Thymeleaf — Admin UI with multi-step form workflow
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Separate merge page at `/admin/drivers/{id}/merge`; "Merge" button on driver detail page links there (consistent with Edit button pattern)
- **D-02:** Two-step server-side flow: Step 1 shows source driver info + target dropdown, Step 2 (after form submit) shows preview with counts + confirm button
- **D-03:** No modals or AJAX — pure server-side rendering with form submits
- **D-04:** HTML `<select>` dropdown with all drivers sorted by PSN-ID, excluding source driver
- **D-05:** Each option shows PSN-ID and nickname (e.g., "PlayerOne (Nick)")
- **D-06:** Controller loads all drivers, passes to model, filtering out source driver
- **D-07:** New `DriverMergeService.previewMerge(sourceId, targetId)` method — counts FK entries without executing, returns `MergePreview` record
- **D-08:** Preview shows: SeasonDriver (reassign + duplicates), RaceLineup (reassign + duplicates), RaceResult (reassign + duplicates), PsnAlias count
- **D-09:** `MergePreview` record is separate from `MergeResult`
- **D-10:** "Confirm Merge" button on preview page submits POST to `/admin/drivers/{id}/merge` with `targetId` as hidden form field
- **D-11:** JavaScript `confirm('Really merge [source] into [target]? This cannot be undone.')` on confirm button
- **D-12:** After success, redirect to target driver detail page (`/admin/drivers/{targetId}`) with flash "Driver merged: [sourcePsnId] into [targetPsnId] — [total] references reassigned, [dropped] duplicates resolved"
- **D-13:** On error, redirect back to driver list with error flash message
- **D-14:** New endpoints in `DriverController`: `GET /admin/drivers/{id}/merge`, `POST /admin/drivers/{id}/merge/preview`, `POST /admin/drivers/{id}/merge`
- **D-15:** No separate Form DTO — target driver ID comes as `@RequestParam UUID targetId`
- **D-16:** Controller injects `DriverMergeService` alongside existing `DriverService`
- **D-17:** New template `admin/driver-merge.html` — uses `admin/layout` fragment
- **D-18:** Merge button in `driver-detail.html` toolbar between Edit and Delete, styled `btn btn-secondary`

### Claude's Discretion

- Preview page layout and styling details
- Exact wording of confirmation dialog and flash messages
- Whether to show driver names in the preview table or just counts
- Internal implementation of `previewMerge()` (can reuse query patterns from `merge()`)

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope

</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| MERGE-01 | Admin kann auf der Fahrer-Detailseite einen Merge starten | Merge button added to `driver-detail.html` toolbar (D-18); GET endpoint wires to existing driver detail page |
| MERGE-02 | Admin kann den Ziel-Fahrer auswaehlen | Step 1 form with `<select>` dropdown (D-04, D-05, D-06); controller filters out source driver from `driverService.findAll()` |
| MERGE-03 | Admin sieht eine Vorschau der betroffenen Referenzen | `previewMerge()` service method + Step 2 preview template (D-07, D-08, D-09); repositories already expose `findByDriverId()` for all FK tables |
| MERGE-04 | Admin muss den Merge explizit bestaetigen | Two-step flow: preview first, then confirm POST (D-10, D-11); JS `confirm()` guard consistent with delete pattern |

</phase_requirements>

---

## Summary

Phase 18 adds the admin UI for driver merges, building directly on `DriverMergeService.merge()` from Phase 16/17. The implementation touches four artifacts: `DriverController` (3 new endpoints), `DriverMergeService` (new `previewMerge()` method), a new `driver-merge.html` template, and a button added to the existing `driver-detail.html` toolbar.

The technical patterns are already established in this codebase. The two-step form flow (select target → preview → confirm) maps cleanly to the existing `@RequestParam`-based controller pattern. No new DTO class is needed — the target UUID travels as a raw `@RequestParam` and as a hidden form field in the preview step, consistent with how `assignToSeason` works today.

The only net-new code with any meaningful complexity is `previewMerge()`: it must replicate the duplicate-detection logic from `merge()` in read-only mode, returning counts instead of executing changes. This mirrors the existing merge loop structure exactly but substitutes count accumulation for mutation.

**Primary recommendation:** Implement `previewMerge()` by copying the duplicate-check loops from `merge()`, replacing all `save()`/`delete()` calls with counter increments and a `@Transactional(readOnly = true)` annotation.

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring MVC | (Spring Boot 4.x — project-defined) | Controller endpoints, `@RequestParam`, `RedirectAttributes` | Project standard [VERIFIED: codebase] |
| Thymeleaf | (Spring Boot 4.x — project-defined) | Server-side rendering of both template states | Project standard [VERIFIED: codebase] |
| JUnit 5 + MockMvc | (project-defined) | Controller integration tests | Project standard [VERIFIED: DriverControllerTest.java] |
| Mockito | (project-defined) | Unit tests for `previewMerge()` | Project standard [VERIFIED: DriverMergeServiceTest.java] |

No new dependencies. Phase 18 adds no external libraries. [VERIFIED: 18-UI-SPEC.md "Registry Safety"]

**Installation:** none required.

---

## Architecture Patterns

### Recommended Project Structure

No new packages. All changes are additive within existing structure:

```
src/main/java/org/ctc/
├── admin/controller/DriverController.java    # add 3 endpoints + inject DriverMergeService
├── domain/service/DriverMergeService.java    # add previewMerge() + MergePreview record

src/main/resources/templates/admin/
├── driver-detail.html                        # add Merge button to toolbar
└── driver-merge.html                         # NEW — two rendering states in one template

src/test/java/org/ctc/
├── admin/controller/DriverControllerTest.java    # add merge endpoint tests
└── domain/service/DriverMergeServiceTest.java    # add previewMerge() unit tests
```

### Pattern 1: Multi-Step Form Without Session State

**What:** POST /preview renders Step 2 HTML containing a hidden `targetId` field. No session or flash storage needed between steps — the target ID is embedded in the form.

**When to use:** Two-step confirmations where the intermediate state is small enough to fit in a hidden field.

**Example (controller sketch):**
```java
// Source: DriverController pattern — @RequestParam consistent with assignToSeason
@PostMapping("/{id}/merge/preview")
public String previewMerge(@PathVariable UUID id,
                           @RequestParam UUID targetId,
                           Model model) {
    model.addAttribute("source", driverService.findById(id));
    model.addAttribute("target", driverService.findById(targetId));
    model.addAttribute("preview", driverMergeService.previewMerge(id, targetId));
    return "admin/driver-merge";  // same template, different state triggered by model presence
}
```

[VERIFIED: DriverController.java — assignToSeason uses identical @RequestParam UUID pattern]

### Pattern 2: Single Template for Multi-State Pages

**What:** `driver-merge.html` renders Step 1 when `preview` model attribute is absent, Step 2 when `preview` is present. Controlled via `th:if="${preview == null}"` / `th:unless`.

**When to use:** When two page states share the same toolbar + layout but differ in content.

**Example (template fragment):**
```html
<!-- Step 1 — visible when no preview -->
<div th:if="${preview == null}">
  <form th:action="@{/admin/drivers/{id}/merge/preview(id=${source.id})}" method="post">
    <input type="hidden" name="_method" value="post">
    <div class="form-group">
      <label>Merge into (target driver)</label>
      <select name="targetId" required>
        <option value="">-- Select target driver --</option>
        <option th:each="d : ${allDrivers}"
                th:value="${d.id}"
                th:text="${d.psnId + ' (' + d.nickname + ')'}"></option>
      </select>
    </div>
    <div class="actions">
      <button type="submit" class="btn btn-primary">Select Target</button>
      <a th:href="@{/admin/drivers/{id}(id=${source.id})}" class="btn btn-secondary">Back to Driver</a>
    </div>
  </form>
</div>

<!-- Step 2 — visible when preview exists -->
<div th:unless="${preview == null}">
  <form th:action="@{/admin/drivers/{id}/merge(id=${source.id})}" method="post"
        th:attr="onsubmit='return confirm(\'Really merge ' + ${source.psnId} + ' into ' + ${target.psnId} + '? This cannot be undone.\')'">
    <input type="hidden" name="targetId" th:value="${target.id}">
    <button type="submit" class="btn btn-danger">Confirm Merge</button>
  </form>
</div>
```

[VERIFIED: driver-detail.html, season-detail.html — confirm() pattern; driver-form.html — form structure]

### Pattern 3: previewMerge() — Read-Only Duplicate Simulation

**What:** Mirrors the mutation loops in `merge()` but only counts; marked `@Transactional(readOnly = true)`.

**When to use:** Any preview-before-confirm pattern for destructive operations.

**Example (service sketch):**
```java
// Source: DriverMergeService.merge() loop structure — verified in codebase
public record MergePreview(
    int seasonDriversToReassign, int seasonDriversDuplicate,
    int raceLineupsToReassign, int raceLineupsDuplicate,
    int raceResultsToReassign, int raceResultsDuplicate,
    int psnAliasesToReassign) {}

@Transactional(readOnly = true)
public MergePreview previewMerge(UUID sourceId, UUID targetId) {
    if (sourceId.equals(targetId)) throw new BusinessRuleException("Cannot merge driver with itself");
    driverRepository.findById(sourceId).orElseThrow(() -> new EntityNotFoundException("Driver", sourceId));
    driverRepository.findById(targetId).orElseThrow(() -> new EntityNotFoundException("Driver", targetId));

    var seasonDrivers = seasonDriverRepository.findByDriverId(sourceId);
    int sdReassign = 0, sdDup = 0;
    for (var sd : seasonDrivers) {
        if (seasonDriverRepository.findBySeasonIdAndDriverId(sd.getSeason().getId(), targetId).isPresent()) sdDup++;
        else sdReassign++;
    }
    // repeat for raceLineups, raceResults ...
    int aliases = psnAliasRepository.findByDriverId(sourceId).size();
    return new MergePreview(sdReassign, sdDup, rlReassign, rlDup, rrReassign, rrDup, aliases);
}
```

[VERIFIED: DriverMergeService.java — identical loop structure; SeasonDriverRepository.java — findBySeasonIdAndDriverId exists]

### Anti-Patterns to Avoid

- **Storing preview state in HTTP session:** Not needed — hidden `targetId` field carries state between steps. Session storage would be OSIV-incompatible and inconsistent with app patterns.
- **Calling `merge()` in preview:** The preview must be read-only. A distinct `previewMerge()` method prevents accidental data mutation.
- **Using `@ModelAttribute` form DTO for merge:** CONTEXT.md D-15 explicitly mandates `@RequestParam UUID targetId`. A form DTO class would add unnecessary weight for a single UUID field.
- **Complex SpEL in Thymeleaf for confirm() dialog:** Use `th:attr="onsubmit=..."` with server-side interpolation instead of constructing JS strings in SpEL.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Duplicate-detection for preview counts | New query methods | Reuse `findBySeasonIdAndDriverId()`, `findByRaceIdAndDriverId()` | Already implemented and tested in merge() [VERIFIED: DriverMergeService.java] |
| Flash messages | Custom alert mechanism | `RedirectAttributes.addFlashAttribute()` + layout.html alert block | Wired in layout.html lines 61-62 [VERIFIED: layout.html] |
| Driver sort for dropdown | Manual sort in controller | `driverService.findAll()` + Java `.stream().sorted(Comparator.comparing(Driver::getPsnId))` in controller or service | findAll() already returns all drivers; stream sort is trivial |

**Key insight:** All building blocks exist. Phase 18 is an assembly task, not a construction task.

---

## Common Pitfalls

### Pitfall 1: Thymeleaf confirm() Dialog With Dynamic Names

**What goes wrong:** `onclick="return confirm('Really merge ${sourcePsnId}?')"` does not interpolate — Thymeleaf does not process expressions inside plain `onclick` attributes.

**Why it happens:** Standard HTML attribute values are not Thymeleaf-processed unless using `th:attr` or `th:onclick`.

**How to avoid:** Use `th:attr="onsubmit=..."` with string concatenation:
```html
th:attr="onsubmit='return confirm(\'Really merge ' + ${source.psnId} + ' into ' + ${target.psnId} + '? This cannot be undone.\')'"
```

[VERIFIED: season-detail.html uses `onsubmit="return confirm('Really delete this season?')"` — static text; dynamic names require th:attr]

**Warning signs:** Confirm dialog shows literal `${sourcePsnId}` text instead of actual PSN ID.

### Pitfall 2: Two-State Template State Detection

**What goes wrong:** Using `th:if="${preview != null}"` can be true even when `preview` is `null` if the model attribute is explicitly set to null vs. not set at all.

**Why it happens:** Spring MVC model attributes set to null behave differently from absent attributes in Thymeleaf.

**How to avoid:** Do not add `preview` to the model in the GET handler for Step 1. Only add it in the preview POST handler. Then `th:if="${preview == null}"` reliably indicates Step 1.

**Warning signs:** Step 2 renders during GET /merge (no preview data loaded).

### Pitfall 3: Redirect to Target Driver After Source Deletion

**What goes wrong:** Merging deletes the source driver. If the controller redirects to the wrong ID (source instead of target), it gets a 404.

**Why it happens:** The `targetId` comes as a request param. If it is accidentally used as the path variable instead, redirect goes to the now-deleted source.

**How to avoid:** In the execute POST handler, keep `@PathVariable UUID id` (source) separate from `@RequestParam UUID targetId`. Redirect to `targetId`:
```java
return "redirect:/admin/drivers/" + targetId;
```

[VERIFIED: CONTEXT.md D-12 — "redirect to target driver detail page (/admin/drivers/{targetId})"]

**Warning signs:** 404 after successful merge.

### Pitfall 4: Missing `@Transactional(readOnly = true)` on previewMerge()

**What goes wrong:** Without `readOnly = true`, the preview call participates in a write transaction, adding unnecessary overhead and risk.

**Why it happens:** Forgetting the annotation on a new method in a `@Service` class.

**How to avoid:** Annotate `previewMerge()` with `@Transactional(readOnly = true)` — same pattern as `DriverService.findAll()` and `findById()`. [VERIFIED: DriverService.java lines 45-47]

---

## Code Examples

### Sorting all drivers for dropdown (excluding source)

```java
// Source: DriverService.findAll() + Java stream — VERIFIED DriverRepository.java
var allDrivers = driverService.findAll().stream()
    .filter(d -> !d.getId().equals(id))
    .sorted(Comparator.comparing(Driver::getPsnId, String.CASE_INSENSITIVE_ORDER))
    .toList();
model.addAttribute("allDrivers", allDrivers);
```

### Flash message with total counts

```java
// Source: CONTEXT.md D-12 + DriverController.save() pattern — VERIFIED DriverController.java line 78
int total = result.seasonDrivers() + result.raceLineups() + result.raceResults() + result.aliasesReassigned();
int dropped = result.seasonDriversDropped() + result.raceLineupsDropped() + result.raceResultsDropped();
redirectAttributes.addFlashAttribute("successMessage",
    "Driver merged: " + sourcePsnId + " into " + targetPsnId
    + " — " + total + " references reassigned, " + dropped + " duplicates resolved");
return "redirect:/admin/drivers/" + targetId;
```

### Exception handling in merge execute endpoint

```java
// Source: DriverController.delete() pattern — VERIFIED DriverController.java lines 93-101
try {
    var mergeResult = driverMergeService.merge(id, targetId);
    // ... flash success, redirect to target
} catch (EntityNotFoundException | BusinessRuleException e) {
    redirectAttributes.addFlashAttribute("errorMessage",
        "Merge failed: driver not found or already deleted. Verify both drivers exist.");
    return "redirect:/admin/drivers";
}
```

### MergePreview record declaration

```java
// Source: DriverMergeService.MergeResult record pattern — VERIFIED DriverMergeService.java line 29
public record MergePreview(
    int seasonDriversToReassign, int seasonDriversDuplicate,
    int raceLineupsToReassign, int raceLineupsDuplicate,
    int raceResultsToReassign, int raceResultsDuplicate,
    int psnAliasesToReassign) {

    public int totalToReassign() {
        return seasonDriversToReassign + raceLineupsToReassign + raceResultsToReassign + psnAliasesToReassign;
    }
    public int totalDuplicates() {
        return seasonDriversDuplicate + raceLineupsDuplicate + raceResultsDuplicate;
    }
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Spring Boot 3.x `@AutoConfigureMockMvc` import path | Spring Boot 4.x `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` | Boot 4.x | Import must use new package — verified in DriverControllerTest.java line 7 [VERIFIED] |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Sorting drivers by PSN-ID using Java stream in controller is sufficient (no DB-level `ORDER BY` needed) | Architecture Patterns | Low — list size is small (admin tool); if large, add `findAllByOrderByPsnIdAsc()` to DriverRepository |
| A2 | Single `driver-merge.html` template with two conditional states is cleaner than two separate templates | Architecture Patterns | Low — two templates is also valid; no functional difference |

---

## Open Questions

1. **Should `previewMerge()` validate self-merge and entity existence?**
   - What we know: `merge()` validates both — throws `BusinessRuleException` for self-merge, `EntityNotFoundException` for missing drivers
   - What's unclear: Preview is read-only; if the target doesn't exist yet, we'd rather show an error before reaching preview
   - Recommendation: Yes — include the same validation guards in `previewMerge()`. The controller should let exceptions propagate to the global exception handler.

---

## Environment Availability

Step 2.6: SKIPPED (no external dependencies identified — pure Java/Spring/Thymeleaf code changes, no external services or CLI tools)

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Mockito + Spring MockMvc |
| Config file | `pom.xml` (Surefire + Failsafe, JaCoCo) |
| Quick run command | `./mvnw test -Dtest=DriverMergeServiceTest,DriverControllerTest` |
| Full suite command | `./mvnw verify` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| MERGE-01 | GET /admin/drivers/{id}/merge returns merge form | Integration | `./mvnw test -Dtest=DriverControllerTest#givenExistingDriver_whenGetMergeForm_thenReturnsMergeView` | ❌ Wave 0 |
| MERGE-02 | Model contains all drivers except source | Integration | `./mvnw test -Dtest=DriverControllerTest#givenExistingDriver_whenGetMergeForm_thenModelContainsFilteredDrivers` | ❌ Wave 0 |
| MERGE-03 | POST /preview with valid targetId shows preview | Integration | `./mvnw test -Dtest=DriverControllerTest#givenTwoDrivers_whenPostPreview_thenReturnsPreviewState` | ❌ Wave 0 |
| MERGE-03 | previewMerge() counts reassign/duplicate correctly | Unit | `./mvnw test -Dtest=DriverMergeServiceTest` | ✅ (file exists, new nested class needed) |
| MERGE-04 | POST /merge executes and redirects to target | Integration | `./mvnw test -Dtest=DriverControllerTest#givenTwoDrivers_whenConfirmMerge_thenRedirectsToTarget` | ❌ Wave 0 |
| MERGE-04 | Source driver deleted after merge | Integration | `./mvnw test -Dtest=DriverControllerTest#givenTwoDrivers_whenConfirmMerge_thenSourceDriverDeleted` | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `./mvnw test -Dtest=DriverMergeServiceTest,DriverControllerTest`
- **Per wave merge:** `./mvnw verify`
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `DriverControllerTest` — add new test methods for merge endpoints (file exists, needs new methods)
- [ ] `DriverMergeServiceTest` — add `PreviewMergeTests` nested class (file exists, needs new nested class)
- [ ] `driver-merge.html` — new template (no test gap, but template must exist before controller tests pass)

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | Auth only in prod profile (Spring Security) |
| V3 Session Management | no | No session state used in merge flow |
| V4 Access Control | no | Admin-only paths; secured at security filter level (existing) |
| V5 Input Validation | yes | `UUID targetId` from `@RequestParam` — Spring auto-rejects non-UUID format with 400 |
| V6 Cryptography | no | No cryptographic operations |

### Known Threat Patterns for Spring MVC admin

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Mass assignment via @ModelAttribute | Tampering | Not applicable — D-15 mandates @RequestParam UUID, no @ModelAttribute DTO |
| CSRF on POST endpoints | Spoofing | Spring Security CSRF (enabled in prod); dev profile has no auth but admin-only access |
| Self-merge | Tampering | BusinessRuleException in DriverMergeService.merge() and previewMerge() |
| Merge with non-existent target | Tampering | EntityNotFoundException guard in merge() and previewMerge() |

[VERIFIED: DriverMergeService.java lines 35-43 — self-merge and entity checks implemented]

---

## Sources

### Primary (HIGH confidence)

- `src/main/java/org/ctc/domain/service/DriverMergeService.java` — full implementation read; MergeResult record structure, merge loop patterns, all FK repository methods confirmed
- `src/main/java/org/ctc/admin/controller/DriverController.java` — full implementation read; @RequestParam pattern from assignToSeason, flash attributes, redirect patterns
- `src/main/resources/templates/admin/driver-detail.html` — toolbar structure confirmed; edit/delete button positions
- `src/main/resources/templates/admin/layout.html` — flash message handling confirmed (lines 61-62)
- `src/main/resources/templates/admin/driver-form.html` — form patterns confirmed
- `src/main/resources/templates/admin/season-detail.html` — confirm() delete pattern confirmed
- `src/main/java/org/ctc/domain/repository/*Repository.java` — all four FK repositories verified; findByDriverId() exists on all
- `.planning/phases/18-merge-ui/18-UI-SPEC.md` — design contract, component inventory, copywriting contract
- `.planning/phases/18-merge-ui/18-CONTEXT.md` — all locked decisions

### Secondary (MEDIUM confidence)

- `src/test/java/org/ctc/admin/controller/DriverControllerTest.java` — test patterns confirmed for new test structure
- `src/test/java/org/ctc/domain/service/DriverMergeServiceTest.java` — existing test structure for previewMerge() tests
- `src/test/java/org/ctc/TestHelper.java` — createDriver(), createSeasonDriver() helpers confirmed available

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — verified entirely from codebase, no new dependencies
- Architecture: HIGH — all patterns exist in current controller and service layer
- Pitfalls: HIGH — confirmed from actual code (Thymeleaf th:attr pattern, existing merge loop structure)
- Test map: HIGH — existing test file structure verified

**Research date:** 2026-04-07
**Valid until:** 2026-05-07 (stable Spring Boot project, no moving targets)
