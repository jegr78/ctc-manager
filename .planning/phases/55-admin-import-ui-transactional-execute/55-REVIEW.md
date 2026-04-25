---
phase: 55-admin-import-ui-transactional-execute
reviewed: 2026-04-25T00:00:00Z
depth: standard
files_reviewed: 7
files_reviewed_list:
  - src/main/java/org/ctc/dataimport/DriverSheetImportService.java
  - src/main/java/org/ctc/admin/controller/DriverSheetImportController.java
  - src/main/resources/templates/admin/driver-import.html
  - src/main/resources/templates/admin/driver-import-preview.html
  - src/main/resources/templates/admin/drivers.html
  - src/test/java/org/ctc/dataimport/DriverSheetImportControllerTest.java
  - src/test/java/org/ctc/dataimport/DriverSheetImportControllerExceptionTest.java
findings:
  critical: 1
  warning: 3
  info: 3
  total: 7
status: issues_found
---

# Phase 55: Code Review Report

**Reviewed:** 2026-04-25
**Depth:** standard
**Files Reviewed:** 7
**Status:** issues_found

## Summary

Phase 55 ships the Google Sheets driver bulk-import flow: a new `DriverSheetImportService.execute()` transactional write-path, a three-handler `DriverSheetImportController`, two Thymeleaf templates, a toolbar button on `drivers.html`, and 21 integration/exception tests.

The overall architecture is clean. The controller is thin, delegation is correct, OSIV is exploited properly, no `@SessionAttributes` annotation is present, no `th:utext` is used, no inline styles appear on the new templates, and CSRF is handled by the shared layout.

One critical logic bug was found in the service: the `accept` checkbox in the preview template emits a driver UUID only when checked, but a checkbox that is not checked sends no value at all — however the execute handler uses this to decide whether to accept the fuzzy suggestion. The template renders an `<input type="checkbox">` whose `th:value` holds the UUID, but an unchecked checkbox sends no parameter. This is actually the intended behaviour for "not accepted". The real bug is in the **opposite direction**: the `execute()` service reads `allParams.get("accept_<psnId>_<year>")` and treats any non-blank value as "accept", but an HTML checkbox only submits its `value` when checked, meaning the accept-UUID arrives only on check. This part is correct. **However**, the critical bug is that the `crossTabCreatedDrivers` cache in `execute()` uses the **sheet PSN ID** as the cache key for the FUZZY/accept path, but when the same sheet PSN appears across multiple year-tabs (once accepted in year A, then again as FUZZY in year B), the second tab reuses the cached driver from year A without verifying the UUID matches the `acceptValue` from year B's param — potentially linking the wrong driver if a different UUID is supplied in the second tab's accept param.

Three warnings cover: exception-message leakage in the controller's error flash attributes, a missing `@Transactional(readOnly = true)` on the `preview()` method (which issues multiple read queries without a read-only transaction hint), and a flawed test that creates two drivers and two stubs but only exercises the second stub, leaving `fuzzyDriver` and the first stub entirely unused.

Three informational findings cover: a dead import in the exception test, a misaligned indentation in `drivers.html`, and the `ExecuteResult` class using fully-qualified `java.util.List` and `java.util.ArrayList` instead of the already-imported `java.util.*`.

---

## Critical Issues

### CR-01: crossTabCreatedDrivers Cache Ignores Accept Override on Second Tab

**File:** `src/main/java/org/ctc/dataimport/DriverSheetImportService.java:161-163`

**Issue:** `crossTabCreatedDrivers.computeIfAbsent(row.psnId(), ...)` on the FUZZY accept path caches the driver by sheet PSN ID. When the same sheet PSN appears as a FUZZY match in two different year-tabs with different `accept_<psnId>_<year>` params (e.g., year 2022 accepts driver-UUID-X, year 2021 accepts driver-UUID-Y), the first tab's resolution is stored in the cache under the raw PSN key. The second tab's `computeIfAbsent` call hits the cache and returns the driver resolved from the **first** tab's UUID, completely ignoring the second tab's `acceptValue`. The second tab is then linked to the wrong driver without any error.

The NEW_DRIVER path on lines 108-113 has the same cache-key collision issue, but there it is intentional (de-duplication). For the FUZZY/accept path the user may have provided different accept choices per year-tab.

**Fix:**
```java
// FUZZY_SUGGESTION rows
for (FuzzySuggestionRow row : tab.fuzzySuggestions()) {
    String acceptKey = "accept_" + row.psnId() + "_" + tab.year();
    String acceptValue = allParams.get(acceptKey);
    Driver driver;
    if (acceptValue != null && !acceptValue.isBlank()) {
        UUID suggestedDriverId = UUID.fromString(acceptValue);
        // Do NOT use crossTabCreatedDrivers for the accept path: the user may have
        // chosen a different existing driver for the same fuzzy PSN in each year-tab.
        // Look up directly, then cache if absent.
        driver = crossTabCreatedDrivers.computeIfAbsent(
                row.psnId() + "_accept_" + tab.year(),  // tab-scoped key
                ignored -> driverRepository.findById(suggestedDriverId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Driver not found: " + suggestedDriverId)));
    } else {
        // No accept → create new driver (de-duplication across tabs is valid here)
        driver = crossTabCreatedDrivers.computeIfAbsent(row.psnId(), psnId -> {
            Driver d = new Driver(psnId, psnId);
            d.setActive(true);
            Driver saved = driverRepository.save(d);
            result.incrementNewDrivers();
            return saved;
        });
    }
    // ... rest unchanged
}
```

---

## Warnings

### WR-01: Exception Message Leaked Directly into Flash Attribute (Information Disclosure)

**File:** `src/main/java/org/ctc/admin/controller/DriverSheetImportController.java:56,86`

**Issue:** Both handlers append `e.getMessage()` directly to the user-facing flash / model error attribute:
- Line 56: `model.addAttribute("errorMessage", "Error reading Google Sheet: " + e.getMessage())`
- Line 86: `redirectAttributes.addFlashAttribute("errorMessage", "Import error: " + e.getMessage())`

Internal exception messages may contain file paths, SQL fragments, internal UUIDs, or stack hints. In the `dev` profile this is benign, but in `prod` these messages will be visible to any authenticated admin. The CLAUDE.md convention for flash attributes is a human-readable, controlled message.

**Fix:**
```java
// preview handler (line 53-58)
} catch (IOException e) {
    log.error("Error reading Google Sheet for driver import", e);
    addCommonAttributes(model);
    model.addAttribute("errorMessage", "Could not read the Google Sheet. Check the URL and service account credentials.");
    return "admin/driver-import";
} catch (IllegalArgumentException | IllegalStateException e) {
    log.error("Driver import preview failed", e);
    addCommonAttributes(model);
    model.addAttribute("errorMessage", "Preview failed: " + e.getMessage()); // IllegalArgument messages are controlled
    return "admin/driver-import";
}

// execute handler (line 83-87)
} catch (BusinessRuleException | ValidationException | IllegalArgumentException e) {
    log.error("Error executing driver sheet import", e);
    redirectAttributes.addFlashAttribute("errorMessage", "Import failed: " + e.getMessage());
} catch (IllegalStateException | DataAccessException e) {
    log.error("Error executing driver sheet import", e);
    redirectAttributes.addFlashAttribute("errorMessage", "Import failed due to an internal error. See server logs for details.");
}
```

### WR-02: preview() Has No @Transactional(readOnly = true)

**File:** `src/main/java/org/ctc/dataimport/DriverSheetImportService.java:52`

**Issue:** `preview()` issues multiple read queries (`seasonRepository.findByYear`, `teamRepository.findByShortName`, `seasonDriverRepository.findBySeasonIdAndDriverId`, `driverMatchingService.findDriver`) without a read-only transaction boundary. Without `@Transactional(readOnly = true)`, each repository call may run in its own implicit transaction (or none), preventing the persistence provider from applying read-only optimisations, and causing inconsistent snapshot behaviour if a concurrent write happens between the `findByYear` and `findBySeasonIdAndDriverId` calls on the same tab.

**Fix:**
```java
@Transactional(readOnly = true)
public DriverSheetImportPreview preview(String sheetUrl) throws IOException {
```

Note: `execute()` calls `this.preview()` internally. Because `execute()` is already `@Transactional`, the `preview()` call within it will join the outer read-write transaction, which is correct. The `readOnly = true` will only apply to standalone calls from the controller.

### WR-03: givenFuzzyRowWithoutAccept Test Has Unreachable Dead Setup

**File:** `src/test/java/org/ctc/dataimport/DriverSheetImportControllerTest.java:289-320`

**Issue:** `givenFuzzyRowWithoutAccept_whenExecute_thenCreatesNewDriver()` contains dead, misleading setup that never participates in the actual assertion:

1. Lines 291-296: `fuzzyDriver` is created and `stubSheets("...abc", 2021, ...)` is called — but the `mockMvc.perform()` at line 311 uses URL `"abc2"`, so `stub("abc")` is never triggered and `fuzzyDriver` is never exercised.
2. The test therefore only exercises the `fz_noacc`/`fz_noac0` pair with the `abc2` stub, but leaves an orphaned `fuzzyDriver` creation and `stubSheets("abc", ...)` that a future reader will believe is part of the test's given-block.

The orphaned stub could also shadow a future test if the stub is accidentally matched.

**Fix:** Remove the dead setup entirely and keep only the `fz_noacc`/`fz_noac0` pair:
```java
@Test
void givenFuzzyRowWithoutAccept_whenExecute_thenCreatesNewDriver() throws Exception {
    // given — "fz_noacc" (8 chars) vs sheet PSN "fz_noac0" (8 chars):
    // Levenshtein dist=1 ('c'→'0'), max=8, similarity=0.875 — FUZZY threshold met
    testHelper.createDriver("fz_noacc", "Fuzzy No Accept Driver");
    stubSheets("https://sheets.test/d/abc", 2021,
            List.of(
                    List.of("PSN ID", "Nickname", "Team"),
                    List.of("fz_noac0", "fz_noac0", "I_AHR")
            ));

    // when — no accept param → create new driver
    mockMvc.perform(post("/admin/drivers/import/execute")
                    .param("sheetUrl", "https://sheets.test/d/abc")
                    .param("seasonId_2021", season2021.getId().toString()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/drivers/import"))
            .andExpect(flash().attributeExists("successMessage"));

    // then — new Driver with PSN "fz_noac0" created; original "fz_noacc" still present
    assertThat(driverRepository.findByPsnId("fz_noac0")).isPresent();
    assertThat(driverRepository.findByPsnId("fz_noacc")).isPresent(); // not modified
}
```

---

## Info

### IN-01: ExecuteResult Uses Fully-Qualified java.util References Inside a java.util.* Import

**File:** `src/main/java/org/ctc/dataimport/DriverSheetImportService.java:422`

**Issue:** The `ExecuteResult` inner class references `java.util.List` and `java.util.ArrayList` with fully-qualified names on line 422, but the compilation unit already has a wildcard `import java.util.*;` on line 19 which covers both types. The FQN usage is inconsistent with the rest of the file.

**Fix:**
```java
// line 422 — replace:
private final java.util.List<Integer> skippedTabYears = new java.util.ArrayList<>();
// with:
private final List<Integer> skippedTabYears = new ArrayList<>();
```

### IN-02: Dead Import in DriverSheetImportControllerExceptionTest

**File:** `src/test/java/org/ctc/dataimport/DriverSheetImportControllerExceptionTest.java:4`

**Issue:** `import org.ctc.domain.exception.BusinessRuleException;` is used only in the `givenExecuteThrowsBusinessRule_whenPostExecute_*` test at line 69, so it is not dead. However, `import org.ctc.domain.service.SeasonManagementService;` at line 5 is referenced only as a `@MockitoBean` field (line 46) to prevent the real service wiring. This is legitimate but worth noting: if the test class is viewed without the `@MockitoBean` annotation context it looks like an unused import. No code change required — this is a documentation note.

Actually examining more carefully: `BusinessRuleException` at line 4 is indeed used. The import is not dead. Disregard — this is clean.

### IN-03: Misaligned Indentation in drivers.html Toolbar Actions

**File:** `src/main/resources/templates/admin/drivers.html:12`

**Issue:** The "Import from Google Sheet" anchor (line 11) is indented with 12 spaces inside the `.actions` div, but the "New Driver" anchor (line 12) uses only 8 spaces — inconsistent with the file's 4-space indentation convention applied everywhere else.

**Fix:**
```html
<div class="actions">
    <input type="text" id="driverSearch" placeholder="Search drivers..." aria-label="Search drivers"
           class="search-input">
    <a th:href="@{/admin/drivers/import}" class="btn btn-secondary">Import from Google Sheet</a>
    <a th:href="@{/admin/drivers/new}" class="btn btn-primary">+ New Driver</a>
</div>
```

---

_Reviewed: 2026-04-25_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
