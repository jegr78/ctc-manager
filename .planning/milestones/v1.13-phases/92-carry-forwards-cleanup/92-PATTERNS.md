# Phase 92: Carry-Forwards & Cleanup — Pattern Map

**Mapped:** 2026-05-21
**Files analyzed:** 14 (4 production + 1 build + 4 test + 4 docs/bookkeeping + 1 templates touched twice via 1 entry)
**Analogs found:** 13 / 14 — only `AssumptionsFencePredicateTest` (greenfield package `org.ctc.build`) has no analog.

**Theme:** Phase 92 is **pattern propagation, not design**. Every code shape already exists in the codebase. The planner's job is verbatim copy + correct insertion-site selection. The single greenfield surface is the `org.ctc.build` package (first usage).

---

## File Classification

### Plan 92-01 — UX-01 (CsvImportController typed-catch + badge UX)

| Target File | New/Edit | Role | Data Flow | Closest Analog | Match Quality |
|-------------|----------|------|-----------|----------------|---------------|
| `src/main/java/org/ctc/dataimport/CsvImportController.java` | EDIT | controller | request-response → flash redirect | `src/main/java/org/ctc/admin/controller/DriverSheetImportController.java` lines 60-98 (preview) + 134-166 (execute) | EXACT — same idiom, same Google-Sheets consumer family |
| `src/main/resources/templates/admin/import.html` | EDIT | template | server-rendered (flash render) | `src/main/resources/templates/admin/driver-import.html` lines 8-16 | EXACT — verbatim block copy (badge already exists in analog) |
| `src/main/resources/templates/admin/import-preview.html` | EDIT | template | server-rendered (flash render) | `src/main/resources/templates/admin/driver-import.html` lines 8-16 | EXACT — same shape, different insertion site |
| `src/test/java/org/ctc/dataimport/CsvImportControllerExceptionTest.java` | EDIT (extend in place) | test (controller exception) | unit (`@SpringBootTest + @AutoConfigureMockMvc + @MockitoBean`) | `src/test/java/org/ctc/dataimport/DriverSheetImportControllerExceptionTest.java` (whole class, 12 tests) | EXACT — sibling family, copy each `@Test` method shape verbatim |

### Plan 92-02 — COV-01 (test-only, src/main git-clean)

| Target File | New/Edit | Role | Data Flow | Closest Analog | Match Quality |
|-------------|----------|------|-----------|----------------|---------------|
| `src/test/java/org/ctc/admin/controller/RaceControllerCalendarTest.java` | NEW | test (controller branch coverage) | unit (`@SpringBootTest + @AutoConfigureMockMvc + @MockitoBean`) per RESEARCH Pitfall 4 recommendation | `src/test/java/org/ctc/dataimport/DriverSheetImportControllerExceptionTest.java` lines 30-184 (shape) + `src/test/java/org/ctc/admin/controller/RaceControllerTest.java` lines 25-68 (`@Transactional` + `@MockitoBean` peers in `org.ctc.admin.controller`) | EXACT — combine the two analogs: package + repos layout from `RaceControllerTest`, exception-test shape from `DriverSheetImportControllerExceptionTest` |
| `src/test/java/org/ctc/dataimport/GoogleSheetsServiceIT.java` | NEW (Open Question 1 → Option 2 per RESEARCH Pitfall 1) | test (integration) | `@SpringBootTest @ActiveProfiles("dev") @Transactional @Tag("integration")` + `@MockitoBean GoogleSheetsService` | `src/test/java/org/ctc/dataimport/DriverSheetImportServiceIT.java` lines 1-60 (the canonical `*IT` shape in the same package); existing `GoogleSheetsServiceTest.java` lines 1-26 for the deep-stub mock shape | EXACT — sibling family with established `*IT` precedent in the same directory |
| `src/test/java/org/ctc/dataimport/GoogleCalendarServiceIT.java` | NEW (Open Question 1 → Option 2) | test (integration) | same as above | same as above (`DriverSheetImportServiceIT` lines 1-60) | EXACT — same precedent |

### Plan 92-03 — CLEAN-01 (build-tooling + greenfield test)

| Target File | New/Edit | Role | Data Flow | Closest Analog | Match Quality |
|-------------|----------|------|-----------|----------------|---------------|
| `pom.xml` (insert new `<execution id="assumptions-fence">` inside existing `exec-maven-plugin` block) | EDIT (append-execution) | build (Maven plugin) | `validate`-phase build-guard | `pom.xml` lines 434-456 (existing `<execution id="template-fragment-call-guard">`) | EXACT — same plugin, sibling `<execution>` |
| `src/test/java/org/ctc/build/AssumptionsFencePredicateTest.java` | NEW (in new package `org.ctc.build`) | test (build-guard predicate) | unit (`@TempDir` + `ProcessBuilder bash -c`) | **NO ANALOG** — package `org.ctc.build` has zero files (verified by `find src/test/java/org/ctc/build` returning empty). Use the `@TempDir` shape from `GoogleSheetsServiceTest.java:16` as a starter | NEW PATTERN — greenfield package; RESEARCH recommends `ProcessBuilder` shape so the predicate is verified end-to-end as bash invokes it |

### Plan 92-04 — DOCS-01 + BOOK-01 (docs/bookkeeping)

| Target File | New/Edit | Role | Data Flow | Closest Analog | Match Quality |
|-------------|----------|------|-----------|----------------|---------------|
| `.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-VERIFICATION.md` | NEW | docs (Goal-Backward verification report) | docs/audit | `.planning/milestones/v1.11-phases/86-test-wallclock-reduction/86-VERIFICATION.md` (commit `2e84fd57`) | EXACT — v1.11 precedent specified by CONTEXT D-01 + canonical refs |
| `.planning/milestones/v1.12-phases/90-perf-consolidation-module-split-decision/90-VERIFICATION.md` | NEW | docs (same) | docs/audit | same (`86-VERIFICATION.md`) | EXACT |
| `.planning/milestones/v1.12-phases/91-perf-re-harvest-stretch-ux-polish-milestone-closer/91-VERIFICATION.md` | NEW | docs (same) | docs/audit | same (`86-VERIFICATION.md`) | EXACT |
| `.planning/milestones/v1.12-REQUIREMENTS.md` | EDIT (11 in-place marker flips) | docs (bookkeeping) | docs/state-flip | n/a — pure marker edit; verified by 2 grep counts per CONTEXT D-11 | EDIT-ONLY (no code analog applies; literal grep-then-flip per Pitfall 5/6 line list) |

---

## Pattern Assignments

### 1. `CsvImportController.java` (controller, request-response → flash redirect)

**Analog:** `src/main/java/org/ctc/admin/controller/DriverSheetImportController.java`
**Lines to mirror:** 60-98 (preview catch), 134-166 (execute catch), 1-25 (imports)

#### Imports pattern (analog `DriverSheetImportController.java:1-25`) — add to CsvImportController:

```java
import org.ctc.dataimport.exception.AuthGoogleApiException;
import org.ctc.dataimport.exception.GoogleApiException;
import org.ctc.dataimport.exception.NotFoundGoogleApiException;
import org.ctc.dataimport.exception.PermissionGoogleApiException;
import org.ctc.dataimport.exception.TransientGoogleApiException;
import org.springframework.dao.DataIntegrityViolationException;
// already imported: BusinessRuleException, ValidationException, DataAccessException
// remove if no longer needed: java.io.IOException (typed subtypes still extend IOException, so import remains for the CSV-only preview() catch — see Pitfall 3)
```

#### Core pattern — `previewSheet()` Model-return variant (analog `DriverSheetImportController.preview()` lines 60-98):

Replace the current `CsvImportController.previewSheet()` lines 115-120 single catch:

```java
} catch (IOException | IllegalArgumentException | IllegalStateException e) {
    log.error("Error reading Google Sheet", e);
    addCommonAttributes(model);
    model.addAttribute("errorMessage", "Error reading Google Sheet: " + e.getMessage());
    return "admin/import";
}
```

With the 6-arm shape (5 typed Google + 1 IllegalArgument/IllegalState preserving e.getMessage echo per RESEARCH Pitfall 3):

```java
} catch (AuthGoogleApiException e) {
    log.error("Google Sheets authentication failed during CSV import preview-sheet", e);
    addCommonAttributes(model);
    model.addAttribute("errorMessage", "Authentication problem — re-link Google account");
    model.addAttribute("errorCategory", "AUTH");
    return "admin/import";
} catch (NotFoundGoogleApiException e) {
    log.error("Google Sheet not found during CSV import preview-sheet", e);
    addCommonAttributes(model);
    model.addAttribute("errorMessage", "Sheet not found — check ID");
    model.addAttribute("errorCategory", "NOT_FOUND");
    return "admin/import";
} catch (PermissionGoogleApiException e) {
    log.error("Permission denied on Google Sheet during CSV import preview-sheet", e);
    addCommonAttributes(model);
    model.addAttribute("errorMessage", "Access denied — share the sheet with the service account");
    model.addAttribute("errorCategory", "PERMISSION");
    return "admin/import";
} catch (TransientGoogleApiException e) {
    log.warn("Transient Google API failure during CSV import preview-sheet", e);
    addCommonAttributes(model);
    model.addAttribute("errorMessage", "Connection problem — retry");
    model.addAttribute("errorCategory", "TRANSIENT");
    return "admin/import";
} catch (GoogleApiException e) {
    // Defensive catch on the sealed base — unreachable at runtime (the 4
    // permits above are exhaustive) but required by javac since sealed
    // exhaustiveness on catch blocks is not yet a language feature.
    log.error("Unexpected GoogleApiException subtype during CSV import preview-sheet", e);
    addCommonAttributes(model);
    model.addAttribute("errorMessage", "Connection problem — retry");
    model.addAttribute("errorCategory", "TRANSIENT");
    return "admin/import";
} catch (IllegalArgumentException | IllegalStateException e) {
    log.error("Error reading Google Sheet (client-side input)", e);
    addCommonAttributes(model);
    model.addAttribute("errorMessage", "Error reading Google Sheet: " + e.getMessage());
    return "admin/import";
}
```

**Critical ordering note (Pitfall 3 + 4 from RESEARCH):** Typed Google catches MUST come BEFORE `IllegalArgumentException | IllegalStateException` (sealed subtypes extend `IOException`; `IllegalArgumentException` is a sibling, not a supertype — so ordering is correctness, not just compiler-required, because `IllegalArgumentException` arrives from `extractSpreadsheetId()` for malformed URLs and must keep its `e.getMessage()` echo per Pitfall 3).

#### Core pattern — `execute()` RedirectAttributes variant (analog `DriverSheetImportController.execute()` lines 134-166):

Replace current `CsvImportController.execute()` lines 204-208 single 6-type multicatch:

```java
} catch (IOException | BusinessRuleException | ValidationException | IllegalArgumentException |
         IllegalStateException | DataAccessException e) {
    log.error("Error executing import", e);
    redirectAttributes.addFlashAttribute("errorMessage", "Import error: " + e.getMessage());
}
```

With 8-arm shape (mirror `DriverSheetImportController.execute()` lines 134-166 verbatim, adjusting strings to "CSV import" context):

```java
} catch (AuthGoogleApiException e) {
    log.error("Google Sheets authentication failed during CSV import execute", e);
    redirectAttributes.addFlashAttribute("errorMessage", "Authentication problem — re-link Google account");
    redirectAttributes.addFlashAttribute("errorCategory", "AUTH");
} catch (NotFoundGoogleApiException e) {
    log.error("Google Sheet not found during CSV import execute", e);
    redirectAttributes.addFlashAttribute("errorMessage", "Sheet not found — check ID");
    redirectAttributes.addFlashAttribute("errorCategory", "NOT_FOUND");
} catch (PermissionGoogleApiException e) {
    log.error("Permission denied on Google Sheet during CSV import execute", e);
    redirectAttributes.addFlashAttribute("errorMessage", "Access denied — share the sheet with the service account");
    redirectAttributes.addFlashAttribute("errorCategory", "PERMISSION");
} catch (TransientGoogleApiException e) {
    log.warn("Transient Google API failure during CSV import execute", e);
    redirectAttributes.addFlashAttribute("errorMessage", "Connection problem — retry");
    redirectAttributes.addFlashAttribute("errorCategory", "TRANSIENT");
} catch (GoogleApiException e) {
    // Defensive sealed-base catch (unreachable at runtime; javac requirement)
    log.error("Unexpected GoogleApiException subtype during CSV import execute", e);
    redirectAttributes.addFlashAttribute("errorMessage", "Connection problem — retry");
    redirectAttributes.addFlashAttribute("errorCategory", "TRANSIENT");
} catch (BusinessRuleException | ValidationException | IllegalArgumentException e) {
    log.error("Error executing CSV import", e);
    redirectAttributes.addFlashAttribute("errorMessage", "Import failed: " + e.getMessage());
} catch (DataIntegrityViolationException e) {
    log.error("CSV import hit DB constraint — transaction rolled back, no rows inserted", e);
    redirectAttributes.addFlashAttribute("errorMessage",
            "Import failed due to a database constraint. Nothing was imported. See server logs for details.");
} catch (IllegalStateException | DataAccessException e) {
    log.error("Error executing CSV import", e);
    redirectAttributes.addFlashAttribute("errorMessage", "Import failed due to an internal error. See server logs for details.");
}
```

#### Untouched: `preview()` (CSV file upload, lines 44-62)

Per RESEARCH Assumption A5: `preview()` consumes `MultipartFile.getInputStream()`, NOT Google services. The existing `IOException | IllegalArgumentException | IllegalStateException` catch at line 56 stays AS-IS — no typed Google subtypes can reach this code path.

---

### 2. `admin/import.html` (template, server-rendered flash)

**Analog:** `src/main/resources/templates/admin/driver-import.html` lines 8-16

**Pattern (verbatim copy block — Pitfall 2: `import.html` currently has NO `errorMessage` render at all, so we ADD the full block, not just the badge):**

```html
<div th:if="${errorMessage}" class="alert alert-error mb-md">
    <p>
        <span th:if="${errorCategory}"
              class="error-badge"
              th:classappend="|error-badge--${#strings.toLowerCase(errorCategory)}|"
              th:text="${errorCategory}"></span>
        <span th:text="${errorMessage}"></span>
    </p>
</div>
```

**Insertion site:** `src/main/resources/templates/admin/import.html` — between line 6 (`<h1>Import</h1>`) and line 8 (`<!-- Source Tabs -->`). Keep one blank line above and below.

**CSS class verification (from `src/main/resources/static/admin/css/admin.css:360-374`):**

```css
.error-badge { ... }
.error-badge--transient  { background: #3b2e0e; ... }
.error-badge--auth       { background: var(--danger-bg); ... }
.error-badge--not-found  { background: #2a2a3a; ... }
.error-badge--permission { background: var(--danger-bg); ... }
```

All 4 classes exist; no CSS additions required.

---

### 3. `admin/import-preview.html` (template, server-rendered flash)

**Analog:** same `driver-import.html` block (lines 8-16) — verbatim.

**Insertion site:** between line 6 (`<h1>Import Preview</h1>`) and line 7 (the existing `<div class="card mb-md">`). Both the multi-race and single-race blocks at lines 23 + 31 + 85 + 93 already render `alert alert-error` for `preview.hasErrors()` and `preview.duplicateDetected` — the new `errorMessage` block sits ABOVE those (page-level flash, not per-preview).

---

### 4. `CsvImportControllerExceptionTest.java` (test, controller exception)

**Analog:** `src/test/java/org/ctc/dataimport/DriverSheetImportControllerExceptionTest.java` (whole class, lines 30-225)

**Imports pattern (analog lines 4-23):**

```java
import org.ctc.dataimport.exception.AuthGoogleApiException;
import org.ctc.dataimport.exception.NotFoundGoogleApiException;
import org.ctc.dataimport.exception.PermissionGoogleApiException;
import org.ctc.dataimport.exception.TransientGoogleApiException;
// existing in CsvImportControllerExceptionTest.java: TransientGoogleApiException, BusinessRuleException, MockitoBean, MockMvc, @SpringBootTest, @AutoConfigureMockMvc
```

**Class header pattern (analog lines 31-34) — already matches existing CsvImportControllerExceptionTest:**

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class CsvImportControllerExceptionTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private CsvImportService csvImportService;
    @MockitoBean private GoogleSheetsService googleSheetsService;
    @MockitoBean private ScorecardParser scorecardParser;
    // (also need @MockitoBean SeasonManagementService if previewSheet path requires it)
}
```

**Per-arm test pattern (analog `DriverSheetImportControllerExceptionTest.java:47-62` — AUTH on Model-return endpoint):**

```java
@Test
void givenPreviewSheetThrowsAuthGoogleApiException_whenPostPreviewSheet_thenModelAttributeCategoryAuth() throws Exception {
    // given
    when(googleSheetsService.isAvailable()).thenReturn(true);
    when(googleSheetsService.extractSpreadsheetId(anyString())).thenReturn("abc123");
    when(googleSheetsService.getSheetNames(anyString()))
            .thenThrow(new AuthGoogleApiException("auth failure", null));
    when(csvImportService.getAllSeasons()).thenReturn(List.of());
    when(csvImportService.getPlayoffMatchups()).thenReturn(List.of());

    // when
    mockMvc.perform(post("/admin/import/preview-sheet")
                    .param("sheetUrl", "https://docs.google.com/spreadsheets/d/abc123")
                    .param("seasonId", UUID.randomUUID().toString())
                    .param("matchdayLabel", "MD1"))
            // then
            .andExpect(status().isOk())
            .andExpect(view().name("admin/import"))
            .andExpect(model().attribute("errorMessage", "Authentication problem — re-link Google account"))
            .andExpect(model().attribute("errorCategory", "AUTH"));
}
```

**Per-arm test pattern for `execute()` (analog `DriverSheetImportControllerExceptionTest.java:118-133` — flash variant):**

```java
@Test
void givenExecuteSheetThrowsAuthGoogleApiException_whenPostExecute_thenRedirectFlashCategoryAuth() throws Exception {
    // given
    when(googleSheetsService.extractSpreadsheetId(anyString())).thenReturn("abc123");
    when(googleSheetsService.getSheetNames(anyString()))
            .thenThrow(new AuthGoogleApiException("auth failure", null));

    // when
    mockMvc.perform(post("/admin/import/execute")
                    .param("seasonId", UUID.randomUUID().toString())
                    .param("source", "sheet")
                    .param("sheetUrl", "https://docs.google.com/spreadsheets/d/abc123"))
            // then
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/import"))
            .andExpect(flash().attribute("errorMessage", "Authentication problem — re-link Google account"))
            .andExpect(flash().attribute("errorCategory", "AUTH"));
}
```

**Coverage target:** Mirror all 8 typed `@Test` methods from the analog (4 preview-sheet model arms + 4 execute redirect-flash arms). Plus update the existing `givenIoException_whenPreviewSheet_thenRedirectsWithError` test at lines 65-84 to assert the new `errorCategory` flash attribute (currently only asserts `attributeExists("errorMessage")`).

---

### 5. `RaceControllerCalendarTest.java` (test, controller calendar branches)

**Analogs (combine two):**
- Package + repo-injection layout: `src/test/java/org/ctc/admin/controller/RaceControllerTest.java` lines 1-70
- Exception-test method shape: `src/test/java/org/ctc/dataimport/DriverSheetImportControllerExceptionTest.java` lines 30-184

**Class header pattern (synthesized — `@MockitoBean` for collaborator stubbing, no `@Transactional` because we don't write DB rows):**

```java
package org.ctc.admin.controller;

import java.util.UUID;
import org.ctc.dataimport.exception.AuthGoogleApiException;
import org.ctc.dataimport.exception.NotFoundGoogleApiException;
import org.ctc.dataimport.exception.PermissionGoogleApiException;
import org.ctc.dataimport.exception.TransientGoogleApiException;
import org.ctc.domain.service.RaceAttachmentService;
import org.ctc.domain.service.RaceCalendarService;
import org.ctc.domain.service.RaceFormDataService;
import org.ctc.domain.service.RaceService;
import org.ctc.admin.service.RaceGraphicService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class RaceControllerCalendarTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private RaceService raceService;
    @MockitoBean private RaceFormDataService raceFormDataService;
    @MockitoBean private RaceCalendarService raceCalendarService;
    @MockitoBean private RaceAttachmentService raceAttachmentService;
    @MockitoBean private RaceGraphicService raceGraphicService;
    // ...8 @Test methods covering RaceController.java:193-224 (4 typed-catch arms +
    // defensive base + IllegalStateException catch + GET model-attribute branches at 69-71)
}
```

**Per-arm test pattern (analog `DriverSheetImportControllerExceptionTest.java:119-133` — flash variant; mirrors the production code at `RaceController.java:198-201`):**

```java
@Test
void givenCalendarAuthFailure_whenPostCreateCalendarEvent_thenRedirectsWithAuthBadge() throws Exception {
    // given
    UUID raceId = UUID.randomUUID();
    doThrow(new AuthGoogleApiException("auth failure", null))
            .when(raceCalendarService).createOrUpdateCalendarEvent(raceId);

    // when
    mockMvc.perform(post("/admin/races/" + raceId + "/create-calendar-event"))
            // then
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/races/" + raceId))
            .andExpect(flash().attribute("errorMessage", "Authentication problem — re-link Google account"))
            .andExpect(flash().attribute("errorCategory", "AUTH"));
}
```

**Note:** Existing `RaceControllerTest.java` uses `@Transactional` + real repositories — that's a different test type (CRUD integration). For the calendar-branch coverage test, follow the lighter `DriverSheetImportControllerExceptionTest` shape (no `@Transactional`, all collaborators `@MockitoBean`-replaced).

---

### 6. `GoogleSheetsServiceIT.java` + `GoogleCalendarServiceIT.java` (test, integration)

**Analog (the canonical `*IT` shape in the same package):** `src/test/java/org/ctc/dataimport/DriverSheetImportServiceIT.java` lines 1-60

**Class header pattern (analog lines 42-46):**

```java
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
@Tag("integration")
class GoogleSheetsServiceIT {
    // @MockitoBean to replace the real Google client transport (no live API calls in CI)
    // — but use a SPY/PARTIAL approach so the real GoogleApiExceptionMapper translation
    // path runs end-to-end (the whole point of these ITs per RESEARCH Pitfall 1).
    //
    // Alternative: rely on the existing deep-stub Mockito pattern from
    // GoogleSheetsServiceTest.java:22-25:
    //   var sheets = mock(Sheets.class, RETURNS_DEEP_STUBS);
    //   when(sheets.spreadsheets().values().get(anyString(), anyString()).execute())
    //       .thenThrow(new IOException("network timeout"));
    // then verify the real GoogleApiExceptionMapper.from(IOException) emits
    // TransientGoogleApiException at the service boundary.
}
```

**Imports pattern (analog `DriverSheetImportServiceIT.java:1-25`):**

```java
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
```

**Mock-shape reference (existing unit test that the IT supplements, NOT replaces — `GoogleSheetsServiceTest.java:22-25`):**

```java
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
// var sheets = mock(Sheets.class, RETURNS_DEEP_STUBS); ... when(...).thenThrow(new IOException(...));
```

**Coverage targets per RESEARCH Validation Architecture table (lines 919-922):**
- `IOException` → `TransientGoogleApiException` default path
- `GeneralSecurityException` → `AuthGoogleApiException` path
- `GoogleJsonResponseException` 403 with reason "authError" → `AuthGoogleApiException` (not Permission) discriminator path

---

### 7. `pom.xml` — new `<execution id="assumptions-fence">`

**Analog:** `pom.xml` lines 434-456 (existing `<execution id="template-fragment-call-guard">`)

**Excerpt of analog (verbatim from `pom.xml:434-456`):**

```xml
<execution>
    <id>template-fragment-call-guard</id>
    <phase>validate</phase>
    <goals><goal>exec</goal></goals>
    <configuration>
        <executable>bash</executable>
        <arguments>
            <argument>-c</argument>
            <argument><![CDATA[
violations=$(grep -rE 'th:(replace|insert|include)="~\{[^"]*\(.*\$\{.*\}.*\)\}"' src/main/resources/templates/ | grep -vF 'layout(${pageTitle}' || true);
if [ -n "$violations" ]; then
  echo "[PLAT-07 build-guard] Forbidden Thymeleaf fragment-call expression detected (Plan 05):";
  echo "$violations";
  echo "Move the value to the controller via model.addAttribute(\"pageTitle\", ...) and use ~{layout :: layout(\${pageTitle}, ~{::section})}.";
  echo "See .planning/phases/71-*/71-CONTEXT.md Decisions D-05 + D-12 for the canonical fix.";
  exit 1;
fi;
echo "[PLAT-07 build-guard] OK - no Thymeleaf fragment-call expression offenders.";
exit 0;
]]></argument>
        </arguments>
    </configuration>
</execution>
```

**Adaptation for `assumptions-fence` (insertion site: directly after the closing `</execution>` of `template-fragment-call-guard` at line 456, still inside `<executions>`):**

```xml
<execution>
    <id>assumptions-fence</id>
    <phase>validate</phase>
    <goals><goal>exec</goal></goals>
    <configuration>
        <executable>bash</executable>
        <arguments>
            <argument>-c</argument>
            <argument><![CDATA[
violations=$(grep -rE '^import\s+(static\s+)?org\.junit\.jupiter\.api\.Assumptions(\.|;)' src/test/java/ | grep -v 'src/test/java/org/ctc/build/' || true);
if [ -n "$violations" ]; then
  echo "[CLEAN-01 build-guard] Forbidden JUnit-Jupiter Assumptions import detected:";
  echo "$violations";
  echo "Remove the JUnit Assumptions usage; either rewrite as an unconditional assertion or use AssertJ org.assertj.core.api.Assumptions.assumeThat (different package, intentional).";
  echo "See .planning/phases/92-*/92-CONTEXT.md Decision D-04 for the canonical fix and rationale.";
  exit 1;
fi;
echo "[CLEAN-01 build-guard] OK - no JUnit-Jupiter Assumptions offenders.";
exit 0;
]]></argument>
        </arguments>
    </configuration>
</execution>
```

**Predicate notes (locked by CONTEXT D-04 + Pitfall 7 / Example 7 RESEARCH):**
- `org\.junit\.jupiter\.api\.Assumptions` (not `Assumptions\.` standalone) — leaves the AssertJ `org.assertj.core.api.Assumptions` import at `BackupStagingDirPerForkIT.java:12` alone.
- `grep -v 'src/test/java/org/ctc/build/'` so the AssumptionsFencePredicateTest fixture (if it materializes JUnit-Assumptions sources to disk) is not flagged.

---

### 8. `AssumptionsFencePredicateTest.java` (test, build-guard predicate)

**Analog:** **NONE** — package `org.ctc.build` is greenfield (verified: `find src/test/java/org/ctc/build` returns empty). This is the only file in Phase 92 with no existing analog.

**Closest structural reference for the `@TempDir` shape:** `src/test/java/org/ctc/dataimport/GoogleSheetsServiceTest.java:8,16` (imports `@TempDir` from `org.junit.jupiter.api.io.TempDir`).

**Recommended shape (per RESEARCH Claude's Discretion note + Validation Architecture lines 923-925):**

```java
package org.ctc.build;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class AssumptionsFencePredicateTest {

    @Test
    void givenJunitAssumptionsImport_whenPredicateRuns_thenViolationDetected(@TempDir Path tmp) throws Exception {
        // given a synthetic source file with the forbidden JUnit static import
        Path src = tmp.resolve("src/test/java/SyntheticPositive.java");
        Files.createDirectories(src.getParent());
        Files.writeString(src, """
                import static org.junit.jupiter.api.Assumptions.assumeFalse;
                class SyntheticPositive { }
                """);

        // when invoking the same grep predicate as the Maven exec
        var pb = new ProcessBuilder("bash", "-c",
                "grep -rE '^import\\s+(static\\s+)?org\\.junit\\.jupiter\\.api\\.Assumptions(\\.|;)' " + tmp.resolve("src/test/java"));
        pb.redirectErrorStream(true);
        var proc = pb.start();
        int exit = proc.waitFor();
        String out = new String(proc.getInputStream().readAllBytes());

        // then grep exit 0 means at least one match found
        assertThat(exit).isZero();
        assertThat(out).contains("SyntheticPositive.java");
    }

    @Test
    void givenAssertjAssumptionsImport_whenPredicateRuns_thenNoViolation(@TempDir Path tmp) throws Exception {
        // given a synthetic source file with the ALLOWED AssertJ static import
        Path src = tmp.resolve("src/test/java/SyntheticNegative.java");
        Files.createDirectories(src.getParent());
        Files.writeString(src, """
                import static org.assertj.core.api.Assumptions.assumeThat;
                class SyntheticNegative { }
                """);

        // when invoking the same grep predicate
        var pb = new ProcessBuilder("bash", "-c",
                "grep -rE '^import\\s+(static\\s+)?org\\.junit\\.jupiter\\.api\\.Assumptions(\\.|;)' " + tmp.resolve("src/test/java"));
        pb.redirectErrorStream(true);
        var proc = pb.start();
        int exit = proc.waitFor();

        // then grep exit 1 means NO matches (correct behavior: AssertJ Assumptions is allowed)
        assertThat(exit).isOne();
    }
}
```

**Tag:** Untagged per CLAUDE.md `@Tag` convention (plain unit test). Confirmed by CONTEXT D-09.

---

### 9. `89-VERIFICATION.md` / `90-VERIFICATION.md` / `91-VERIFICATION.md` (docs, Goal-Backward audit)

**Analog:** `.planning/milestones/v1.11-phases/86-test-wallclock-reduction/86-VERIFICATION.md` (commit `2e84fd57` — v1.11 precedent specified by CONTEXT D-01)

**Front-matter pattern (analog lines 1-9):**

```markdown
---
phase: 89
verified_on: 2026-05-21
status: passed
verifier: gsd-verifier (retroactive — v1.12 carry-forward Phase 92 DOCS-01)
score: N/N success-criteria + M/M dimensions
overrides_applied: 0
audit_method: retroactive
---
```

**Required level-2 sections (analog headers):**
1. `# Phase {N} — {Name} — Verification Report` + Phase Goal paragraph
2. `## Goal Achievement — Success Criteria` (table with `# | Success Criterion | Status | Evidence` columns)
3. `## Observable Truths (must-haves)` (table)
4. `## Per-Dimension Verdict Table` (table)
5. `## CONTEXT.md Decision Compliance (selected key decisions)` (table)

**Substance sources (per CONTEXT canonical_refs § Code Surface DOCS-01):**
- `89-VERIFICATION.md` ← `.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-VALIDATION.md` + `89-{01,02,03}-SUMMARY.md`
- `90-VERIFICATION.md` ← `.planning/milestones/v1.12-phases/90-perf-consolidation-module-split-decision/90-VALIDATION.md` + `90-{01,02,03}-SUMMARY.md`
- `91-VERIFICATION.md` ← `.planning/milestones/v1.12-phases/91-perf-re-harvest-stretch-ux-polish-milestone-closer/91-VALIDATION.md` + `91-{01,02,03}-SUMMARY.md`

**Important:** Plan 92-04 ADDS three new files; do NOT modify any existing file in the 89/90/91 directories (Phase-Overwrite-Prevention per `[[feedback-phase-overwrite-prevention]]`).

---

### 10. `.planning/milestones/v1.12-REQUIREMENTS.md` (docs, bookkeeping)

**No code analog applies.** Pure marker flip per CONTEXT D-11 + RESEARCH Pitfall 5/6.

**Exact 11 markers to flip (verified by grep, listed exhaustively):**

7 checkbox flips (`- [ ]` → `- [x]`):
- Line 30 `- [ ] **PERF-01**: ...`
- Line 31 `- [ ] **PERF-02**: ...`
- Line 32 `- [ ] **PERF-03**: ...`
- Line 33 `- [ ] **PERF-04**: ...`
- Line 34 `- [ ] **PERF-05**: ...`
- Line 35 `- [ ] **PERF-06**: ...`
- Line 56 `- [ ] **UX-01**: ...`

4 traceability rows (`Pending` → `Resolved`):
- Line 106 `| PERF-01 | 89 | Pending |`
- Line 107 `| PERF-02 | 89 | Pending |`
- Line 111 `| PERF-06 | 91 | Pending |`
- Line 112 `| UX-01 | 91 | Pending (stretch — descopable to v1.13 if PERF over budget) |`

**Case sensitivity (Pitfall 6):** Use lowercase `x` in `- [x]`. Post-edit grep must return:
- `grep -c "^- \[ \]" .planning/milestones/v1.12-REQUIREMENTS.md` → `0`
- `grep -c "Pending" .planning/milestones/v1.12-REQUIREMENTS.md` → `0`
- `grep -c "^- \[X\]" .planning/milestones/v1.12-REQUIREMENTS.md` (sanity check for uppercase regression) → `0`

---

## Shared Patterns (Cross-Cutting)

### S-1 — Whitelisted user-message strings (T-91-02-IL info-leak invariant)

**Source:** `src/main/java/org/ctc/dataimport/exception/GoogleApiExceptionMapper.java` lines 26-30 (constants), plus the verbatim string literals already used in `DriverSheetImportController.java:63,69,75,81` and `RaceController.java:200,204,208,212`.

**Apply to:** Plan 92-01 only — all 8 typed-Google catch arms in `CsvImportController.previewSheet()` + `.execute()`.

**Verbatim strings (per RESEARCH Don't-Hand-Roll table — default to INLINE LITERALS for D-02 consistency with the 2 already-converted controllers):**
- `AUTH`        → `"Authentication problem — re-link Google account"`
- `NOT_FOUND`   → `"Sheet not found — check ID"`
- `PERMISSION`  → `"Access denied — share the sheet with the service account"`
- `TRANSIENT`   → `"Connection problem — retry"`
- defensive `GoogleApiException` base → use `"Connection problem — retry"` + `errorCategory="TRANSIENT"` (mirrors analog at `DriverSheetImportController.java:90-91`)

**FORBIDDEN:** `model.addAttribute("errorMessage", "anything " + e.getMessage())` in any typed-Google catch arm. The trailing `IllegalArgumentException | IllegalStateException` arm is the ONLY catch in `previewSheet()` that may echo `e.getMessage()` (client-side input error, NOT Google leakage — per Pitfall 3 explanation).

### S-2 — Log level per category

**Source:** `DriverSheetImportController.java:61,67,73,79` + `RaceController.java:199,203,207,211`

**Apply to:** Plan 92-01 only.

| Category | Log Level | Method |
|----------|-----------|--------|
| AUTH | `log.error` | with exception arg |
| NOT_FOUND | `log.error` | with exception arg |
| PERMISSION | `log.error` | with exception arg |
| TRANSIENT | `log.warn` | with exception arg |
| Defensive base | `log.error` | with exception arg |

**Parameterized `{}` only** (CLAUDE.md § Logging). Exception added as the last argument, NEVER concatenated into the message string (log-injection invariant).

### S-3 — Mockito `@MockitoBean` (Spring Boot 4.x replacement for `@MockBean`)

**Source:** `src/test/java/org/ctc/dataimport/DriverSheetImportControllerExceptionTest.java:14,37-45`

**Apply to:** Plan 92-01 test (extend `CsvImportControllerExceptionTest`) + Plan 92-02 (`RaceControllerCalendarTest`).

**Import:** `import org.springframework.test.context.bean.override.mockito.MockitoBean;`

### S-4 — `@SpringBootTest + @AutoConfigureMockMvc` for controller-layer tests

**Source:** `DriverSheetImportControllerExceptionTest.java:31-32`, `CsvImportControllerExceptionTest.java:29-30`, `RaceControllerTest.java:25-26`

**Apply to:** Plans 92-01 test extension + 92-02 `RaceControllerCalendarTest`.

**Rationale (RESEARCH Pattern 4 note + Don't-Hand-Roll table):** CONTEXT D-03 says "Mockito `@WebMvcTest`" but the codebase has ZERO `@WebMvcTest` usage. Use the proven `@SpringBootTest + @AutoConfigureMockMvc + @MockitoBean` triple for codebase consistency.

### S-5 — `@Tag("integration")` on `*IT.java` siblings

**Source:** `src/test/java/org/ctc/dataimport/DriverSheetImportServiceIT.java:19,45` + `src/test/java/org/ctc/backup/service/BackupStagingDirPerForkIT.java:17`

**Apply to:** Plan 92-02 new `GoogleSheetsServiceIT` + `GoogleCalendarServiceIT`.

**Untagged stays untagged** (CLAUDE.md `@Tag` rule) — `RaceControllerCalendarTest` (unit) + `AssumptionsFencePredicateTest` (unit) are PLAIN unit tests; do NOT add any `@Tag` to them.

### S-6 — Test method naming Given-When-Then (CLAUDE.md § Test Naming)

**Source:** `DriverSheetImportControllerExceptionTest.java:47,65,83,101,119,...` (all methods follow `givenContext_whenAction_thenResult` shape)

**Apply to:** Plans 92-01 (8 new test methods) + 92-02 (calendar test + IT extension methods) + 92-03 (2 predicate-test methods).

---

## No Analog Found

| File | Role | Data Flow | Reason | Recommended Approach |
|------|------|-----------|--------|----------------------|
| `src/test/java/org/ctc/build/AssumptionsFencePredicateTest.java` | test (build-guard predicate) | unit (`@TempDir` + `ProcessBuilder`) | Package `org.ctc.build` has zero files (greenfield); no `scripts/*-fence.sh` predecessor either | Use the `@TempDir` shape from `GoogleSheetsServiceTest.java:16` + `ProcessBuilder bash -c` to invoke the SAME grep regex as the Maven exec — end-to-end-faithful predicate verification |

---

## Metadata

**Analog search scope:**
- `src/main/java/org/ctc/admin/controller/` (controllers; `DriverSheetImportController`, `RaceController` confirmed verbatim analogs)
- `src/main/java/org/ctc/dataimport/` (`CsvImportController` refactor target; `GoogleApiExceptionMapper` constants source)
- `src/main/resources/templates/admin/` (`driver-import.html` verbatim template-block source; `import.html` / `import-preview.html` insertion targets)
- `src/main/resources/static/admin/css/admin.css` (verified `.error-badge*` BEM classes at lines 360-374)
- `src/test/java/org/ctc/dataimport/` (`*ExceptionTest` + `*ServiceIT` precedents)
- `src/test/java/org/ctc/admin/controller/` (21 `*Test.java` peers; `RaceControllerTest` for package + repo-injection shape)
- `src/test/java/org/ctc/backup/service/BackupStagingDirPerForkIT.java` (the canonical AssertJ-Assumptions reference for CLEAN-01 negative predicate)
- `pom.xml` (`exec-maven-plugin` block lines 430-458; `template-fragment-call-guard` verbatim shape)
- `.planning/milestones/v1.11-phases/86-test-wallclock-reduction/86-VERIFICATION.md` (commit `2e84fd57` Goal-Backward template)
- `.planning/milestones/v1.12-REQUIREMENTS.md` (verified 7 `[ ]` + 4 `Pending` markers via grep)

**Files scanned (read or grep-confirmed):** 16
- Controllers: `CsvImportController.java` (full, 225 lines) + `DriverSheetImportController.java` (lines 1-170) + `RaceController.java` (lines 180-230)
- Templates: `driver-import.html` (lines 1-25) + `import.html` (lines 1-40) + `import-preview.html` (lines 1-40)
- Tests: `CsvImportControllerExceptionTest.java` (full) + `DriverSheetImportControllerExceptionTest.java` (full) + `RaceControllerTest.java` (lines 1-70) + `DriverSheetImportServiceIT.java` (lines 1-60) + `GoogleSheetsServiceTest.java` (lines 1-60) + `BackupStagingDirPerForkIT.java` (lines 1-45)
- Build: `pom.xml` (lines 420-495)
- Docs: `86-VERIFICATION.md` (lines 1-80) + `91-PATTERNS.md` (lines 1-80, structural template reference)
- Bookkeeping: `v1.12-REQUIREMENTS.md` (grep on `Pending` + `^- \[ \]`)

**Pattern extraction date:** 2026-05-21

---

*Phase 92 PATTERNS map complete. Planner can now produce 4 plan files with concrete file:line analog citations for every action.*
