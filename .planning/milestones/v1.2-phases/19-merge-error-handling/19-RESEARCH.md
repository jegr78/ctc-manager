# Phase 19: Merge Error Handling - Research

**Researched:** 2026-04-07
**Domain:** Spring MVC controller exception handling / flash redirect pattern
**Confidence:** HIGH

## Summary

Phase 19 is a focused gap-closure task: add a `try/catch` block to `previewMerge()` in `DriverController` so it mirrors the existing `executeMerge()` error handling pattern. The fix is three lines of change to one method. The pattern to copy exists verbatim in the same class at lines 134-148.

Currently, `previewMerge()` calls `driverService.findById()` (twice) and `driverMergeService.previewMerge()` without exception handling. Both methods throw `EntityNotFoundException` (non-existent driver) and `previewMerge()` also throws `BusinessRuleException` (self-merge). When either fires, Spring hands off to `GlobalExceptionHandler`, which renders `admin/error` page — degraded UX compared to the flash-redirect that `executeMerge()` delivers.

The fix: wrap the body of `previewMerge()` in a try/catch identical to `executeMerge()`, and redirect to the merge form (`/admin/drivers/{id}/merge`) with an `errorMessage` flash attribute. Two new integration tests cover the two error scenarios (self-merge, non-existent target).

**Primary recommendation:** Copy the `executeMerge()` try/catch pattern verbatim into `previewMerge()`, redirecting errors to `/admin/drivers/{id}/merge` (not `/admin/drivers` as executeMerge does — the merge form is the correct UX target for preview errors).

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| MERGE-02 (error path) | Admin kann den Ziel-Fahrer auswaehlen — error path: non-existent targetId on preview endpoint should return to merge form | EntityNotFoundException thrown by driverService.findById(targetId) and driverMergeService.previewMerge() — must be caught and flash-redirected |
| MERGE-03 (error path) | Admin sieht eine Vorschau — error path: self-merge via URL manipulation on preview endpoint should return to merge form | BusinessRuleException("Cannot merge driver with itself") thrown by driverMergeService.previewMerge() when sourceId == targetId — must be caught and flash-redirected |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

- Test coverage minimum: 82% line coverage (pom.xml enforces 0.82 COVEREDRATIO) [VERIFIED: pom.xml line 241]
- TDD: tests first, then implementation (Red → Green → Refactor)
- Test naming: `givenContext_whenAction_thenExpectedResult()` with `// given / when / then` structure
- Controllers thin: no business logic, only HTTP handling, flash attributes via `RedirectAttributes`
- No breaking changes to existing URLs/endpoints
- Lombok: `@Slf4j`, `@RequiredArgsConstructor` on controller
- Flash attribute keys: `"successMessage"` / `"errorMessage"` (project standard)
- Feature sequence: Unit Tests → Implementation → Integration Tests → E2E Tests
- Test framework: JUnit 5, MockMvc integration tests (`@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("dev") @Transactional`)

## Standard Stack

No new dependencies required for this phase. Everything is already in place.

### Core (already present)
| Component | Location | Purpose |
|-----------|----------|---------|
| DriverController | `org.ctc.admin.controller.DriverController` | Target for the fix |
| EntityNotFoundException | `org.ctc.domain.exception.EntityNotFoundException` | Already imported in DriverController |
| BusinessRuleException | `org.ctc.domain.exception.BusinessRuleException` | Already imported in DriverController |
| RedirectAttributes | Spring MVC | Flash attribute carrier — already used in executeMerge() |
| DriverControllerTest | `org.ctc.admin.controller.DriverControllerTest` | Integration test class to extend |

[VERIFIED: DriverController.java — both exception types are already imported at lines 4-5]

**Installation:** None required.

## Architecture Patterns

### The Pattern to Copy: executeMerge() error handling

This is the reference implementation that `previewMerge()` must match [VERIFIED: DriverController.java lines 131-149]:

```java
// executeMerge() — existing working pattern
@PostMapping("/{id}/merge")
public String executeMerge(@PathVariable UUID id, @RequestParam UUID targetId,
                           RedirectAttributes redirectAttributes) {
    try {
        var source = driverService.findById(id);
        var target = driverService.findById(targetId);
        var result = driverMergeService.merge(id, targetId);
        // ... success logic ...
        return "redirect:/admin/drivers/" + targetId;
    } catch (EntityNotFoundException | BusinessRuleException e) {
        redirectAttributes.addFlashAttribute("errorMessage",
                "Merge failed: " + e.getMessage());
        return "redirect:/admin/drivers";
    }
}
```

### Target: previewMerge() BEFORE fix

```java
// previewMerge() — current state (lines 120-129, NO error handling)
@PostMapping("/{id}/merge/preview")
public String previewMerge(@PathVariable UUID id, @RequestParam UUID targetId, Model model) {
    var source = driverService.findById(id);           // throws EntityNotFoundException
    var target = driverService.findById(targetId);     // throws EntityNotFoundException
    var preview = driverMergeService.previewMerge(id, targetId);  // throws EntityNotFoundException OR BusinessRuleException
    model.addAttribute("source", source);
    model.addAttribute("target", target);
    model.addAttribute("preview", preview);
    return "admin/driver-merge";
}
```

### Target: previewMerge() AFTER fix

Key difference from `executeMerge()`: redirect to `/admin/drivers/{id}/merge` (the merge form for this source driver), not to `/admin/drivers` (the driver list). This returns the user to a contextually appropriate page.

```java
@PostMapping("/{id}/merge/preview")
public String previewMerge(@PathVariable UUID id, @RequestParam UUID targetId,
                           RedirectAttributes redirectAttributes, Model model) {
    try {
        var source = driverService.findById(id);
        var target = driverService.findById(targetId);
        var preview = driverMergeService.previewMerge(id, targetId);
        model.addAttribute("source", source);
        model.addAttribute("target", target);
        model.addAttribute("preview", preview);
        return "admin/driver-merge";
    } catch (EntityNotFoundException | BusinessRuleException e) {
        redirectAttributes.addFlashAttribute("errorMessage",
                "Merge failed: " + e.getMessage());
        return "redirect:/admin/drivers/" + id + "/merge";
    }
}
```

**Signature change:** Add `RedirectAttributes redirectAttributes` parameter — Spring MVC injects this automatically, no other callers to update.

### Redirect Target Decision

| Scenario | executeMerge() redirects to | previewMerge() should redirect to |
|----------|----------------------------|-----------------------------------|
| Error | `/admin/drivers` | `/admin/drivers/{id}/merge` |
| Rationale | Source driver deleted on success, list is appropriate | Source driver still exists, merge form allows retry |

[ASSUMED] — redirecting to the merge form (not the driver list) is the better UX. If the product owner prefers the driver list for consistency, this is a valid alternative. Risk if wrong: minor UX inconsistency, no functional breakage.

### Exception Sources in previewMerge() call chain

[VERIFIED: DriverMergeService.java lines 48-58]

| Exception | Thrown by | Condition |
|-----------|-----------|-----------|
| `BusinessRuleException("Cannot merge driver with itself")` | `driverMergeService.previewMerge()` | `sourceId.equals(targetId)` |
| `EntityNotFoundException("Driver", sourceId)` | `driverMergeService.previewMerge()` | source driver not found in DB |
| `EntityNotFoundException("Driver", targetId)` | `driverMergeService.previewMerge()` | target driver not found in DB |
| `EntityNotFoundException("Driver", id)` | `driverService.findById(id)` | source driver not found (before service call) |
| `EntityNotFoundException("Driver", targetId)` | `driverService.findById(targetId)` | target driver not found (before service call) |

Note: `driverService.findById()` calls happen before `driverMergeService.previewMerge()`. The service also validates — but the controller's pre-fetch will catch missing drivers first. Either way, a single `catch (EntityNotFoundException | BusinessRuleException e)` covers all cases.

### Test Pattern (existing DriverControllerTest)

[VERIFIED: DriverControllerTest.java lines 25-30, 236-248]

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class DriverControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private DriverRepository driverRepository;

    // Existing test pattern for preview (success path):
    @Test
    void givenTwoDrivers_whenPostPreview_thenReturnsPreviewState() throws Exception {
        var source = driverRepository.save(new Driver("merge_prev_src", "Preview Source"));
        var target = driverRepository.save(new Driver("merge_prev_tgt", "Preview Target"));
        mockMvc.perform(post("/admin/drivers/" + source.getId() + "/merge/preview")
                        .param("targetId", target.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/driver-merge"))
                .andExpect(model().attributeExists("source", "target", "preview"));
    }
}
```

New error-path tests to add:

```java
@Test
void givenDriver_whenPreviewMergeWithSelf_thenRedirectsToMergeFormWithError() throws Exception {
    // given
    var source = driverRepository.save(new Driver("merge_self_err", "Self Merge Error"));

    // when
    mockMvc.perform(post("/admin/drivers/" + source.getId() + "/merge/preview")
                    .param("targetId", source.getId().toString()))
            // then
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/drivers/" + source.getId() + "/merge"))
            .andExpect(flash().attributeExists("errorMessage"));
}

@Test
void givenDriver_whenPreviewMergeWithNonExistentTarget_thenRedirectsToMergeFormWithError() throws Exception {
    // given
    var source = driverRepository.save(new Driver("merge_missing_tgt", "Missing Target Test"));
    var nonExistentId = UUID.randomUUID();

    // when
    mockMvc.perform(post("/admin/drivers/" + source.getId() + "/merge/preview")
                    .param("targetId", nonExistentId.toString()))
            // then
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/drivers/" + source.getId() + "/merge"))
            .andExpect(flash().attributeExists("errorMessage"));
}
```

### Anti-Patterns to Avoid

- **Do NOT redirect to `admin/driver-merge` template directly in catch block**: A redirect (not forward) is required so the flash attribute survives. Returning a view name from a catch block would require the model to be re-populated.
- **Do NOT add a new `@ExceptionHandler` in GlobalExceptionHandler**: The requirement is a local try/catch with flash redirect, matching the executeMerge() pattern. GlobalExceptionHandler is intentionally the last resort, not the primary handler.
- **Do NOT re-fetch source driver in catch block**: The redirect URL uses `id` (the path variable), which is always available regardless of exception.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead |
|---------|-------------|-------------|
| Flash message persistence across redirect | Custom session attribute | `RedirectAttributes.addFlashAttribute()` — Spring MVC standard |
| Exception type checking | instanceof chain | Multi-catch `catch (A \| B e)` syntax |

## Common Pitfalls

### Pitfall 1: Wrong redirect target in catch block
**What goes wrong:** Redirect to `/admin/drivers` (like executeMerge) instead of `/admin/drivers/{id}/merge`
**Why it happens:** Copy-paste from executeMerge without thinking about UX context
**How to avoid:** The source driver still exists after a preview error — return user to the merge form for that driver
**Warning signs:** Test for `redirectedUrl("/admin/drivers/" + source.getId() + "/merge")` fails

### Pitfall 2: Forgetting to add RedirectAttributes parameter
**What goes wrong:** `redirectAttributes` not available in catch block — compiler error or NPE
**Why it happens:** Current `previewMerge()` signature only has `@PathVariable UUID id, @RequestParam UUID targetId, Model model`
**How to avoid:** Add `RedirectAttributes redirectAttributes` to the method signature before writing catch block
**Warning signs:** Compilation failure on `redirectAttributes.addFlashAttribute()`

### Pitfall 3: Test uses wrong flash key
**What goes wrong:** Test checks `flash().attributeExists("error")` instead of `"errorMessage"`
**Why it happens:** Typo / inconsistent with project standard
**How to avoid:** Project standard is `"errorMessage"` — verified in existing delete() and executeMerge() [VERIFIED: DriverController.java lines 103, 145]

### Pitfall 4: Coverage drop below 82%
**What goes wrong:** New catch block uncovered if tests don't trigger both exception types
**Why it happens:** Only testing success path
**How to avoid:** Add tests for both error scenarios (self-merge = BusinessRuleException, non-existent target = EntityNotFoundException)

## Code Examples

### Complete fixed method (reference implementation)
```java
// Source: DriverController.java previewMerge() after fix
@PostMapping("/{id}/merge/preview")
public String previewMerge(@PathVariable UUID id, @RequestParam UUID targetId,
                           RedirectAttributes redirectAttributes, Model model) {
    try {
        var source = driverService.findById(id);
        var target = driverService.findById(targetId);
        var preview = driverMergeService.previewMerge(id, targetId);
        model.addAttribute("source", source);
        model.addAttribute("target", target);
        model.addAttribute("preview", preview);
        return "admin/driver-merge";
    } catch (EntityNotFoundException | BusinessRuleException e) {
        redirectAttributes.addFlashAttribute("errorMessage",
                "Merge failed: " + e.getMessage());
        return "redirect:/admin/drivers/" + id + "/merge";
    }
}
```

### Exception messages (for test assertions)
[VERIFIED: DriverMergeService.java lines 51, 55-58]

- Self-merge: `"Cannot merge driver with itself"`
- Non-existent driver: `"Driver not found with id: {uuid}"`

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring MockMvc |
| Config file | pom.xml (Surefire plugin) |
| Quick run command | `./mvnw test -Dtest=DriverControllerTest -pl .` |
| Full suite command | `./mvnw verify` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| MERGE-02 (error path) | Non-existent targetId redirects to merge form with errorMessage | Integration | `./mvnw test -Dtest=DriverControllerTest#givenDriver_whenPreviewMergeWithNonExistentTarget_thenRedirectsToMergeFormWithError` | ❌ Wave 0 |
| MERGE-03 (error path) | Self-merge via preview redirects to merge form with errorMessage | Integration | `./mvnw test -Dtest=DriverControllerTest#givenDriver_whenPreviewMergeWithSelf_thenRedirectsToMergeFormWithError` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `./mvnw test -Dtest=DriverControllerTest -pl .`
- **Per wave merge:** `./mvnw verify`
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] Two new test methods in `src/test/java/org/ctc/admin/controller/DriverControllerTest.java`
  - `givenDriver_whenPreviewMergeWithSelf_thenRedirectsToMergeFormWithError`
  - `givenDriver_whenPreviewMergeWithNonExistentTarget_thenRedirectsToMergeFormWithError`

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | Admin-only; prod profile has Spring Security |
| V3 Session Management | no | — |
| V4 Access Control | no | — |
| V5 Input Validation | yes | UUID type coercion by Spring (non-UUID rejected as 400) |
| V6 Cryptography | no | — |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Self-merge via URL manipulation (sourceId == targetId as targetId param) | Tampering | BusinessRuleException thrown in service, now caught in controller |
| Non-existent targetId | Tampering | EntityNotFoundException thrown in service, now caught in controller |

No new attack surface introduced. The fix reduces attack surface by preventing unhandled exceptions from leaking stack traces via GlobalExceptionHandler in dev profile.

## Environment Availability

Step 2.6: SKIPPED (no external dependencies — code-only change to an existing controller method)

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Redirecting to `/admin/drivers/{id}/merge` (merge form) is better UX than `/admin/drivers` (driver list) for preview errors | Architecture Patterns | Minor UX inconsistency; no functional breakage |

## Open Questions

None. The fix is fully specified by the existing `executeMerge()` pattern in the same file.

## Sources

### Primary (HIGH confidence)
- `src/main/java/org/ctc/admin/controller/DriverController.java` — executeMerge() pattern (lines 131-149), previewMerge() current state (lines 120-129)
- `src/main/java/org/ctc/domain/service/DriverMergeService.java` — exception throw sites (lines 48-58)
- `src/main/java/org/ctc/domain/exception/EntityNotFoundException.java` — exception type and message format
- `src/main/java/org/ctc/domain/exception/BusinessRuleException.java` — exception type
- `src/test/java/org/ctc/admin/controller/DriverControllerTest.java` — test class structure and existing patterns
- `pom.xml` lines 235-244 — JaCoCo 82% minimum coverage enforcement

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — code directly verified from source files
- Architecture: HIGH — pattern copied from same class, same file
- Pitfalls: HIGH — derived directly from current code state and known Java/Spring behavior

**Research date:** 2026-04-07
**Valid until:** Until DriverController.java is modified
