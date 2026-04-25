# Phase 55: Admin Import UI & Transactional Execute — Pattern Map

**Mapped:** 2026-04-24
**Files analyzed:** 7 (5 CREATE, 2 MODIFY)
**Analogs found:** 7 / 7 (100%)

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `src/main/java/org/ctc/admin/controller/DriverSheetImportController.java` | controller | request-response | `src/main/java/org/ctc/dataimport/CsvImportController.java` | exact (same 3-handler pattern, same exception block, same flash idiom) |
| `src/main/java/org/ctc/dataimport/DriverSheetImportService.java` (+execute method) | service extension | CRUD + transactional write | `src/main/java/org/ctc/dataimport/CsvImportService.java` (ImportResult inner class + executeMultiRaceImport) | exact (same mutable-accumulator pattern) |
| `src/main/resources/templates/admin/driver-import.html` | template (form page) | request-response | `src/main/resources/templates/admin/import.html` (lines 1-6, 101-113) | role-match (same layout fragment + sheetUrl form structure; strip all CSV/matchday complexity) |
| `src/main/resources/templates/admin/driver-import-preview.html` | template (preview page) | request-response | `src/main/resources/templates/admin/import-preview.html` | role-match (same card + th:each + form-at-bottom; CRITICAL: do NOT copy inline styles) |
| `src/main/resources/templates/admin/drivers.html` (+1 button) | template modification (entry-point) | request-response | `src/main/resources/templates/admin/drivers.html` lines 8-11 (the existing `.actions` div) | exact (add sibling `<a>` to the same div) |
| `src/test/java/org/ctc/dataimport/DriverSheetImportControllerTest.java` | integration test | request-response | `src/test/java/org/ctc/dataimport/CsvImportControllerTest.java` | exact (same @SpringBootTest + @AutoConfigureMockMvc + @Transactional + TestHelper pattern) |
| `src/test/java/org/ctc/dataimport/DriverSheetImportControllerExceptionTest.java` | integration test (exception paths) | request-response | `src/test/java/org/ctc/dataimport/CsvImportControllerExceptionTest.java` | exact (same @SpringBootTest + @MockitoBean service/sheets pattern) |

---

## Pattern Assignments

### 1. `src/main/java/org/ctc/admin/controller/DriverSheetImportController.java`

**Analog:** `src/main/java/org/ctc/dataimport/CsvImportController.java`

**Imports pattern** (lines 1-20 of analog):
```java
package org.ctc.admin.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.dataimport.DriverSheetImportService;
import org.ctc.dataimport.GoogleSheetsService;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.ValidationException;
import org.ctc.domain.service.SeasonManagementService;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.Map;
```

**Class declaration + injected fields** (mirror from analog lines 21-31):
```java
@Slf4j
@Controller
@RequestMapping("/admin/drivers/import")
@RequiredArgsConstructor
public class DriverSheetImportController {

    private final DriverSheetImportService driverSheetImportService;
    private final GoogleSheetsService googleSheetsService;
    private final SeasonManagementService seasonManagementService;
```

**GET handler pattern** (mirror from analog lines 32-36):
```java
@GetMapping
public String showForm(Model model) {
    addCommonAttributes(model);
    return "admin/driver-import";
}
```

**POST /preview handler pattern** (mirror from analog lines 38-63; strip MultipartFile/matchday/playoff; use sheetUrl only):
```java
@PostMapping("/preview")
public String preview(@RequestParam String sheetUrl, Model model) {
    if (sheetUrl == null || sheetUrl.isBlank()) {
        addCommonAttributes(model);
        model.addAttribute("errorMessage", "Sheet URL must not be blank");
        return "admin/driver-import";
    }
    try {
        var preview = driverSheetImportService.preview(sheetUrl);
        model.addAttribute("preview", preview);
        model.addAttribute("sheetUrl", sheetUrl);   // needed for hidden input on preview page
        addCommonAttributes(model);
        return "admin/driver-import-preview";
    } catch (IOException | IllegalArgumentException | IllegalStateException e) {
        log.error("Error reading Google Sheet for driver import", e);
        addCommonAttributes(model);
        model.addAttribute("errorMessage", "Error reading Google Sheet: " + e.getMessage());
        return "admin/driver-import";
    }
}
```

**POST /execute handler pattern** (mirror from analog lines 124-211; key differences noted):
```java
@PostMapping("/execute")
public String execute(@RequestParam String sheetUrl,
                      @RequestParam(required = false) Map<String, String> allParams,
                      RedirectAttributes redirectAttributes) {
    if (sheetUrl == null || sheetUrl.isBlank()) {
        redirectAttributes.addFlashAttribute("errorMessage", "Sheet URL must not be blank");
        return "redirect:/admin/drivers/import";
    }
    try {
        var result = driverSheetImportService.execute(sheetUrl, allParams);
        // compose D-17 flash summary (see Focus Area 6 in RESEARCH.md)
        var msg = new StringBuilder("Import successful: ")
            .append(result.getNewDriversCount()).append(" new drivers, ")
            .append(result.getNewAssignmentsCount()).append(" new assignments, ")
            .append(result.getConflictsOverwrittenCount()).append(" conflicts overwritten, ")
            .append(result.getConflictsSkippedCount()).append(" conflicts skipped, ")
            .append(result.getUnchangedCount()).append(" unchanged, ")
            .append(result.getErrorCount()).append(" errors.");
        if (result.hasSkippedTabs()) {
            msg.append(" Skipped tabs: ").append(result.getSkippedTabYears())
               .append(" (no season selected).");
        }
        redirectAttributes.addFlashAttribute("successMessage", msg.toString());
    } catch (IOException | BusinessRuleException | ValidationException |
             IllegalArgumentException | IllegalStateException | DataAccessException e) {
        log.error("Error executing driver sheet import", e);
        redirectAttributes.addFlashAttribute("errorMessage", "Import error: " + e.getMessage());
    }
    return "redirect:/admin/drivers/import";
}
```

**addCommonAttributes helper** (mirror from analog lines 220-224; replace csvImportService calls):
```java
private void addCommonAttributes(Model model) {
    model.addAttribute("seasons", seasonManagementService.findAll());
    model.addAttribute("sheetsAvailable", googleSheetsService.isAvailable());
}
```

**Key deviations from analog:**
- Route is `/admin/drivers/import` (NOT `/admin/import`) — different base path.
- No `MultipartFile` parameter anywhere — driver import is sheets-only.
- No `seasonId`, `matchdayId`, `playoffMatchupId`, `overwrite`, or `source` params — those are CSV-import-specific.
- No `ScorecardParser` injection — that is a CSV-import component.
- `allParams` null-check is inside `DriverSheetImportService.execute()` (matches D-06 re-fetch; analog does the null-check at line 178 inline in the controller — Phase 55 delegates this to the service).
- `addCommonAttributes` does NOT call `csvImportService.getPlayoffMatchups()` — no playoff matchups in driver import.
- Exception catch block is identical to analog lines 205-208: copy verbatim.

**DO NOT copy from analog:**
- `@PostMapping("/preview-sheet")` handler — Phase 55 has only one preview path (sheetUrl only, no CSV dual-path).
- The `allParams` key-iteration loop at analog lines 178-189 belongs in the service, not the controller (D-15 says controller passes `allParams` through to service).
- `addMatchdayName()` helper — matchday concept does not apply.

---

### 2. `src/main/java/org/ctc/dataimport/DriverSheetImportService.java` (add `execute()` method + `ExecuteResult` inner class)

**Analog:** `src/main/java/org/ctc/dataimport/CsvImportService.java` (lines 587-615 for `ImportResult` inner class; lines 95-210 for execute walk pattern)

**Existing service (Phase 54 shipped, lines 1-36):**
```java
// Class header already has correct imports + @Slf4j + @Service + @RequiredArgsConstructor
// Existing injected fields: googleSheetsService, driverMatchingService,
//   seasonRepository, teamRepository, seasonDriverRepository
// Phase 55 ADDS: driverRepository, seasonRepository (already present)
// Need to also ADD: driverRepository — inject it as new final field
private final DriverRepository driverRepository;
```

**ExecuteResult inner class** (mirror from analog lines 587-615 — mutable accumulator, not record):
```java
// Mirror CsvImportService.ImportResult shape exactly
@lombok.Getter
public static class ExecuteResult {
    private int newDriversCount;
    private int newAssignmentsCount;
    private int conflictsOverwrittenCount;
    private int conflictsSkippedCount;
    private int unchangedCount;
    private int errorCount;
    private final java.util.List<Integer> skippedTabYears = new java.util.ArrayList<>();

    void incrementNewDrivers()          { newDriversCount++; }
    void incrementNewAssignments()      { newAssignmentsCount++; }
    void incrementConflictsOverwritten(){ conflictsOverwrittenCount++; }
    void incrementConflictsSkipped()    { conflictsSkippedCount++; }
    void addUnchanged(int n)            { unchangedCount += n; }
    void addErrors(int n)               { errorCount += n; }
    void addSkippedTab(int year)        { skippedTabYears.add(year); }

    public boolean hasSkippedTabs() { return !skippedTabYears.isEmpty(); }
}
```

**@Transactional placement** (mirror from analog line 101; note `@Transactional` is on the service method, NOT the controller):
```java
@org.springframework.transaction.annotation.Transactional
public ExecuteResult execute(String sheetUrl, Map<String, String> allParams) {
    if (allParams == null) allParams = Map.of();   // Pitfall 3 from RESEARCH.md Focus Area 9
    try {
        // Re-fetch preview (D-06 re-fetch; idempotent)
        DriverSheetImportPreview fullPreview = this.preview(sheetUrl);
        // ... execute walk per RESEARCH.md Focus Area 5 pseudocode
        return result;
    } catch (IOException e) {
        // IOException from preview() must be wrapped — it is checked and won't auto-rollback
        throw new IllegalStateException("Sheet read failed: " + e.getMessage(), e);
    }
}
```

**allParams key filtering pattern** (mirror from analog lines 178-189 — use startsWith for prefix-based extraction):
```java
// Extract per-tab/per-row decisions from allParams
// Keys: "seasonId_<year>", "skip_<psnId>_<year>", "accept_<psnId>_<year>"
// Filter by prefix (allParams also contains "sheetUrl" itself):
boolean skipped = "on".equals(allParams.get("skip_" + row.psnId() + "_" + tab.year()));
String acceptValue = allParams.get("accept_" + row.psnId() + "_" + tab.year());
```

**DO NOT copy from analog:**
- `executeMultiRaceImport()` signature — Phase 55 method takes `(String sheetUrl, Map<String, String> allParams)`, not a pre-built list of previews.
- `checkDuplicate()` call — no duplicate-matchday concept in driver import.
- The `overwrite` boolean parameter — Phase 55 uses per-row skip/accept decisions instead.

---

### 3. `src/main/resources/templates/admin/driver-import.html`

**Analog:** `src/main/resources/templates/admin/import.html`

**Layout fragment** (analog line 3 — copy verbatim, change title):
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{admin/layout :: layout('Import Drivers from Sheet', ~{::section})}">
<body>
<section>
    <h1>Import Drivers from Google Sheet</h1>
```

**Sheets-available guard + sheetUrl form** (analog lines 101-113 — the Google Sheet panel, stripped of shared metadata fields and CSV panel):
```html
<div class="card">
    <div th:if="${!sheetsAvailable}" class="alert alert-error mb-md">
        <p>Google Sheets is not configured. Check service account credentials.</p>
    </div>

    <form th:if="${sheetsAvailable}" th:action="@{/admin/drivers/import/preview}" method="post">
        <div class="form-group">
            <label for="sheetUrl">Google Sheet URL</label>
            <input type="url" id="sheetUrl" name="sheetUrl"
                   placeholder="https://docs.google.com/spreadsheets/d/..." required>
        </div>
        <div class="actions mt-md">
            <button type="submit" class="btn btn-primary">Preview</button>
            <a th:href="@{/admin/drivers}" class="btn btn-secondary">Cancel</a>
        </div>
    </form>
</div>
```

**errorMessage display** (standard admin pattern — shown at top of section before the card):
```html
<div th:if="${errorMessage}" class="alert alert-error mb-md">
    <p th:text="${errorMessage}"></p>
</div>
```

**DO NOT copy from analog:**
- The tab-nav (`div.tab-nav`), `switchSource()` JS, `panelCsv`, CSV form, `panelSheet`/`panelCsv` toggling — driver import is sheets-only, no tab switching needed.
- `seasonId` select, `matchdayId`, `playoffMatchupId`, `matchdayLabel` — no matchday/season on the entry form.
- The entire inline JS block (230+ lines) — not needed.
- `style="..."` attributes that appear in the analog's form (e.g. on `newMatchdayPanel`, `newMatchdayToggle`) — QUAL-01 violation.

---

### 4. `src/main/resources/templates/admin/driver-import-preview.html`

**Analog:** `src/main/resources/templates/admin/import-preview.html`

**Layout fragment** (analog line 3):
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{admin/layout :: layout('Driver Import Preview', ~{::section})}">
<body>
<section>
    <h1>Driver Import Preview</h1>
```

**Outer tab loop** (new pattern — analog uses `th:each="preview, iterStat : ${previews}"` at line 17; Phase 55 iterates `preview.tabPreviews()` instead):
```html
<th:block th:each="tab : ${preview.tabPreviews()}">
    <div class="card mb-md">
        <h2 th:text="${tab.year()}"></h2>

        <!-- Season dropdown -->
        <div class="form-group">
            <label th:for="'seasonId_' + ${tab.year()}">Season</label>
            <select th:name="'seasonId_' + ${tab.year()}"
                    th:id="'seasonId_' + ${tab.year()}">
                <option value="">-- Select season --</option>
                <option th:each="s : ${seasons}"
                        th:value="${s.id}"
                        th:text="${s.displayLabel}"
                        th:selected="${s.id == tab.suggestedSeasonId()}"></option>
            </select>
            <small th:if="${tab.ambiguousReason() != null}"
                   class="text-dim"
                   th:text="${tab.ambiguousReason()}"></small>
        </div>

        <!-- Bucket: New Drivers -->
        <th:block th:if="${!tab.newDrivers().isEmpty()}">
            <h3 th:text="'New Drivers (' + ${#lists.size(tab.newDrivers())} + ')'"></h3>
            <div class="table-scroll">
                <table>
                    <thead><tr><th>PSN ID</th><th>Team</th></tr></thead>
                    <tbody>
                        <tr th:each="row : ${tab.newDrivers()}">
                            <td th:text="${row.psnId()}"></td>
                            <td th:text="${row.teamShortName()}"></td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </th:block>

        <!-- Bucket: New Assignments -->
        <th:block th:if="${!tab.newAssignments().isEmpty()}">
            <h3 th:text="'New Assignments (' + ${#lists.size(tab.newAssignments())} + ')'"></h3>
            <div class="table-scroll">
                <table>
                    <thead><tr><th>PSN ID</th><th>Team</th></tr></thead>
                    <tbody>
                        <tr th:each="row : ${tab.newAssignments()}">
                            <td th:text="${row.psnId()}"></td>
                            <td th:text="${row.teamShortName()}"></td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </th:block>

        <!-- Bucket: Conflicts (Skip checkbox) -->
        <th:block th:if="${!tab.conflicts().isEmpty()}">
            <h3 th:text="'Conflicts (' + ${#lists.size(tab.conflicts())} + ')'"></h3>
            <div class="table-scroll">
                <table>
                    <thead>
                        <tr>
                            <th>PSN ID</th><th>Current Team</th><th>Sheet Team</th><th>Skip</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr th:each="row : ${tab.conflicts()}">
                            <td th:text="${row.psnId()}"></td>
                            <td th:text="${row.existingTeamShortName()}"></td>
                            <td th:text="${row.sheetTeamShortName()}"></td>
                            <td>
                                <input type="checkbox"
                                       th:name="'skip_' + ${row.psnId()} + '_' + ${tab.year()}"
                                       value="on">
                            </td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </th:block>

        <!-- Bucket: Fuzzy Suggestions (Accept checkbox) -->
        <th:block th:if="${!tab.fuzzySuggestions().isEmpty()}">
            <h3 th:text="'Fuzzy Match Suggestions (' + ${#lists.size(tab.fuzzySuggestions())} + ')'"></h3>
            <div class="table-scroll">
                <table>
                    <thead>
                        <tr>
                            <th>PSN ID</th><th>Suggested Driver</th><th>Team</th><th>Accept</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr th:each="row : ${tab.fuzzySuggestions()}">
                            <td th:text="${row.psnId()}"></td>
                            <td th:text="${row.suggestedPsnId() + ' (' + #numbers.formatDecimal(row.similarity() * 100, 1, 0) + '%)'}"></td>
                            <td th:text="${row.teamShortName()}"></td>
                            <td>
                                <input type="checkbox"
                                       th:name="'accept_' + ${row.psnId()} + '_' + ${tab.year()}"
                                       th:value="${row.suggestedDriverId()}">
                            </td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </th:block>

        <!-- Bucket: Unchanged (display-only) -->
        <th:block th:if="${!tab.unchanged().isEmpty()}">
            <h3 th:text="'Unchanged (' + ${#lists.size(tab.unchanged())} + ')'"></h3>
            <div class="table-scroll">
                <table>
                    <thead><tr><th>PSN ID</th><th>Team</th></tr></thead>
                    <tbody>
                        <tr th:each="row : ${tab.unchanged()}">
                            <td th:text="${row.psnId()}"></td>
                            <td th:text="${row.teamShortName()}"></td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </th:block>

        <!-- Bucket: Errors (display-only) -->
        <th:block th:if="${!tab.errors().isEmpty()}">
            <h3 th:text="'Errors (' + ${#lists.size(tab.errors())} + ')'"></h3>
            <div class="table-scroll">
                <table>
                    <thead><tr><th>PSN ID</th><th>Team Code</th><th>Reason</th></tr></thead>
                    <tbody>
                        <tr th:each="row : ${tab.errors()}">
                            <td th:text="${row.psnId()}"></td>
                            <td th:text="${row.teamCode()}"></td>
                            <td th:text="${row.reason().message()}"></td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </th:block>

    </div>
</th:block>
```

**Ambiguous-season warning banner** (above Execute button — D-16):
```html
<!-- Warning banner when any tab has no season selected -->
<div class="alert alert-error mb-md"
     th:if="${preview.tabPreviews().stream().anyMatch(t -> t.suggestedSeasonId() == null)}">
    <strong>Warning:</strong> Some tabs have no matching season.
    Tabs with no season selected will be skipped on execute.
</div>
```

**Execute form at bottom** (mirror analog lines 143-164; adapt action URL and hidden fields):
```html
<form th:action="@{/admin/drivers/import/execute}" method="post">
    <input type="hidden" name="sheetUrl" th:value="${sheetUrl}">
    <!-- season selects and checkboxes are already rendered inside the tab loop above -->
    <div class="actions mt-md">
        <button type="submit" class="btn btn-primary">Execute Import</button>
        <a th:href="@{/admin/drivers/import}" class="btn btn-secondary">Cancel</a>
    </div>
</form>
```

**Thymeleaf string-concatenation pattern for dynamic name/id** (verified from analog line 63):
```
th:name="'skip_' + ${row.psnId()} + '_' + ${tab.year()}"
```
Use single-quoted literal prefix + `${expression}`. Works for `th:name`, `th:id`, `th:for`.

**DO NOT copy from analog — CRITICAL violations:**
- Analog line 50: `th:style="${row.matchResult.type.name() == 'NONE' ? 'background:#3a2e1a' : ...}"` — inline style on `<tr>`. QUAL-01 violation. Use CSS classes instead (add `.bucket-conflict`, `.bucket-new` etc. to `admin.css` if color-coding is desired).
- Analog lines 72-73: `<span class="badge" style="background:#3a2e1a;color:#ffb74d;">` — inline style on `<span>`. QUAL-01 violation. Use existing `.badge-inactive` or add a semantic class.
- Analog uses `th:utext` nowhere, but confirm new templates also never use `th:utext` for row data — only `th:text` (XSS guard, T-54-02).
- `enctype="multipart/form-data"` on the form — not needed (no file upload in driver import).
- The re-upload `<input type="file">` (analog lines 152-155) — entirely absent from Phase 55.
- `metadata.seasonId`, `metadata.matchdayId`, `source` hidden inputs — not applicable; Phase 55 form carries only `sheetUrl`.
- Inline JS in the template — Phase 55 preview page requires no JavaScript (bulk-action buttons are deferred).

---

### 5. `src/main/resources/templates/admin/drivers.html` (+1 button in toolbar)

**Analog:** `src/main/resources/templates/admin/drivers.html` lines 8-11 (current state of toolbar `.actions` div)

**Current toolbar section** (lines 6-12):
```html
<div class="toolbar">
    <h1>Drivers</h1>
    <div class="actions">
        <input type="text" id="driverSearch" placeholder="Search drivers..." aria-label="Search drivers"
               class="search-input">
        <a th:href="@{/admin/drivers/new}" class="btn btn-primary">+ New Driver</a>
    </div>
</div>
```

**Target state after D-18 modification** (insert new `<a>` immediately before `+ New Driver`):
```html
<div class="toolbar">
    <h1>Drivers</h1>
    <div class="actions">
        <input type="text" id="driverSearch" placeholder="Search drivers..." aria-label="Search drivers"
               class="search-input">
        <a href="/admin/drivers/import" class="btn btn-secondary">Import from Google Sheet</a>
        <a th:href="@{/admin/drivers/new}" class="btn btn-primary">+ New Driver</a>
    </div>
</div>
```

**Note on href format:** The new link uses a plain `href` (not `th:href="@{}"`) because the URL is static and contains no variables. Either `href="/admin/drivers/import"` or `th:href="@{/admin/drivers/import}"` is acceptable — the existing `+ New Driver` uses `th:href` (with a path variable), so using `th:href` for consistency is fine.

**DO NOT copy from analog:**
- No inline `style="..."` on the button.
- No icon — plain text "Import from Google Sheet" per D-18.
- No JavaScript changes needed — the existing JS only targets `#driverSearch` and table rows; no `className` reset for the toolbar buttons.

---

### 6. `src/test/java/org/ctc/dataimport/DriverSheetImportControllerTest.java`

**Analog:** `src/test/java/org/ctc/dataimport/CsvImportControllerTest.java`

**Class annotations + fields** (mirror analog lines 21-34):
```java
package org.ctc.dataimport;

import org.ctc.TestHelper;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonDriver;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.DriverRepository;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class DriverSheetImportControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestHelper testHelper;
    @Autowired
    private DriverRepository driverRepository;
    @Autowired
    private SeasonDriverRepository seasonDriverRepository;

    @MockitoBean
    private GoogleSheetsService googleSheetsService;
```

**@BeforeEach fixture** (Phase 55-specific; mirrors CsvImportControllerTest's spirit but scoped to driver import):
```java
    // Shared fixtures — reused across multiple test methods
    private Season season2024;
    private Team teamAhr;
    private Team teamCrl;
    private Driver existingDriver;

    @BeforeEach
    void setUp() {
        // Use unique prefixes to avoid cross-test collision (TestHelper ensures DB isolation via @Transactional)
        season2024 = testHelper.createSeason("ImpTest_2024", 2024, 1);
        teamAhr = testHelper.createTeam("Import Test AHR", "I_AHR");
        teamCrl = testHelper.createTeam("Import Test CRL", "I_CRL");
        existingDriver = testHelper.createDriver("imp_existing_drv", "Imp Existing Driver");
    }
```

**GoogleSheetsService mock stub helper** (for happy-path tests; mirrors pattern from analog lines 104-114 for sheets):
```java
    // Stub GoogleSheetsService to return one year-tab with provided data rows
    private void stubSheets(String sheetUrl, int year, List<List<Object>> rows) throws Exception {
        String fakeId = "fake-spreadsheet-id";
        when(googleSheetsService.extractSpreadsheetId(sheetUrl)).thenReturn(fakeId);
        when(googleSheetsService.getSheetNames(fakeId))
                .thenReturn(List.of(String.valueOf(year)));
        when(googleSheetsService.readRangeFromSheet(eq(fakeId), eq(String.valueOf(year)), eq("A:C")))
                .thenReturn(rows);
    }

    // Header + one NEW_DRIVER data row
    private static List<List<Object>> newDriverRows(String psnId, String teamCode) {
        return List.of(
                List.of("PSN ID", "Nickname", "Team"),  // header
                List.of(psnId, psnId, teamCode)          // data row
        );
    }
```

**Canonical test pattern — GET form** (mirror analog lines 37-43):
```java
    @Test
    void whenGetImportPage_thenShowsImportFormWithSeasonsAndSheetsAvailable() throws Exception {
        // when / then
        mockMvc.perform(get("/admin/drivers/import"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/driver-import"))
                .andExpect(model().attributeExists("seasons", "sheetsAvailable"));
    }
```

**Canonical test pattern — POST /preview happy path** (mirror analog lines 149-172):
```java
    @Test
    void givenValidSheetUrl_whenPostPreview_thenRendersPreviewTemplate() throws Exception {
        // given
        stubSheets("https://sheets.test/d/abc", 2024, newDriverRows("new_psn", "I_AHR"));

        // when / then
        mockMvc.perform(post("/admin/drivers/import/preview")
                        .param("sheetUrl", "https://sheets.test/d/abc"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/driver-import-preview"))
                .andExpect(model().attributeExists("preview", "sheetUrl", "seasons"));
    }
```

**Canonical test pattern — POST /execute with DB assertion** (mirror analog lines 119-145; use driverRepository + seasonDriverRepository assertions):
```java
    @Test
    void givenNewDriverRow_whenExecute_thenCreatesDriverAndSeasonDriver() throws Exception {
        // given
        stubSheets("https://sheets.test/d/abc", 2024, newDriverRows("brand_new_drv", "I_AHR"));

        // when / then
        mockMvc.perform(post("/admin/drivers/import/execute")
                        .param("sheetUrl", "https://sheets.test/d/abc")
                        .param("seasonId_2024", season2024.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/drivers/import"))
                .andExpect(flash().attributeExists("successMessage"))
                .andExpect(flash().attribute("successMessage",
                        org.hamcrest.Matchers.containsString("new drivers")));

        assertThat(driverRepository.findByPsnId("brand_new_drv")).isPresent();
    }
```

**Flash attribute assertion pattern** (from analog's style, lines 85-87):
```java
.andExpect(flash().attributeExists("errorMessage"))
.andExpect(flash().attribute("successMessage", containsString("new drivers")))
```

**DO NOT copy from analog:**
- `MockMultipartFile` / `multipart(...)` request builder — driver import uses `post(...)` with `.param()`, not multipart.
- `testHelper.createFullSeasonFixture(prefix)` — it creates Matchday/Match/Race entities not needed here; instead use individual `createSeason`, `createTeam`, `createDriver`, `createSeasonDriver` calls.
- `@MockitoBean CsvImportService` — not applicable; the happy-path test mocks `GoogleSheetsService` only.
- `param("seasonId", ...)` as top-level param — Phase 55 uses `param("seasonId_2024", ...)` keyed by year.

---

### 7. `src/test/java/org/ctc/dataimport/DriverSheetImportControllerExceptionTest.java`

**Analog:** `src/test/java/org/ctc/dataimport/CsvImportControllerExceptionTest.java`

**Class annotations + fields** (mirror analog lines 29-43 — note `@MockitoBean` for the service itself):
```java
package org.ctc.dataimport;

import org.ctc.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Focused tests for DriverSheetImportController exception-handling behavior.
 * Uses @MockitoBean to force throws; normal behavior covered in DriverSheetImportControllerTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DriverSheetImportControllerExceptionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DriverSheetImportService driverSheetImportService;

    @MockitoBean
    private GoogleSheetsService googleSheetsService;
```

**IOException on preview test** (mirror analog lines 44-63):
```java
    @Test
    void givenPreviewThrowsIOException_whenPostPreview_thenFormWithError() throws Exception {
        // given
        when(driverSheetImportService.preview(anyString()))
                .thenThrow(new IOException("api error"));
        when(googleSheetsService.isAvailable()).thenReturn(true);
        // SeasonManagementService is a real bean — @MockitoBean not used, its findAll() works via H2

        // when / then
        mockMvc.perform(post("/admin/drivers/import/preview")
                        .param("sheetUrl", "https://sheets.test/d/abc"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/driver-import"))
                .andExpect(model().attributeExists("errorMessage"));
    }
```

**BusinessRuleException on execute test** (mirror analog lines 87-105):
```java
    @Test
    void givenExecuteThrowsBusinessRule_whenPostExecute_thenRedirectsWithFlashError() throws Exception {
        // given
        when(driverSheetImportService.execute(anyString(), any()))
                .thenThrow(new BusinessRuleException("constraint violation"));

        // when / then
        mockMvc.perform(post("/admin/drivers/import/execute")
                        .param("sheetUrl", "https://sheets.test/d/abc")
                        .param("seasonId_2024", "00000000-0000-0000-0000-000000000099"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/drivers/import"))
                .andExpect(flash().attributeExists("errorMessage"));
    }
```

**Blank sheetUrl on execute test** (no service stub needed — guard fires before service call):
```java
    @Test
    void givenMissingSheetUrl_whenPostExecute_thenRedirectsWithError() throws Exception {
        // when / then
        mockMvc.perform(post("/admin/drivers/import/execute")
                        .param("sheetUrl", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/drivers/import"))
                .andExpect(flash().attributeExists("errorMessage"));
    }
```

**DO NOT copy from analog:**
- `@MockitoBean CsvImportService csvImportService` — replaced by `@MockitoBean DriverSheetImportService driverSheetImportService`.
- `@MockitoBean ScorecardParser scorecardParser` — `ScorecardParser` is not injected into Phase 55's controller.
- `MockMultipartFile` — no file upload.
- `when(csvImportService.getAllSeasons()).thenReturn(List.of())` stubs — Phase 55 uses `SeasonManagementService.findAll()` which is a real Spring bean; no stub needed for the model attribute (H2 returns empty list).
- `when(csvImportService.getPlayoffMatchups())` stubs — not applicable.
- The `@Transactional` annotation on the test class — `CsvImportControllerTest` has it, but `CsvImportControllerExceptionTest` does NOT (line 32 of analog). Follow the exception-test analog: no `@Transactional` (data isolation is irrelevant when the service is mocked).

---

## Shared Patterns

### Layout Fragment
**Source:** `src/main/resources/templates/admin/layout.html`
**Apply to:** Both new templates
```html
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{admin/layout :: layout('Page Title', ~{::section})}">
<body>
<section>
  <!-- content here -->
</section>
</body>
</html>
```

### Flash Message Display (errorMessage / successMessage)
**Source:** Every existing admin page (e.g. `drivers.html` implicitly via `layout.html`). The flash attributes are rendered by `admin/layout.html` itself — no per-template rendering needed in new templates.
**Apply to:** Both new templates — no action required, layout handles it automatically.

### Exception Catch Block (Controller)
**Source:** `src/main/java/org/ctc/dataimport/CsvImportController.java` lines 205-208
**Apply to:** `DriverSheetImportController.execute()` — copy verbatim:
```java
catch (IOException | BusinessRuleException | ValidationException |
       IllegalArgumentException | IllegalStateException | DataAccessException e) {
    log.error("Error executing driver sheet import", e);
    redirectAttributes.addFlashAttribute("errorMessage", "Import error: " + e.getMessage());
}
```

### @Transactional on Service Method
**Source:** `src/main/java/org/ctc/dataimport/CsvImportService.java` line ~101
**Apply to:** `DriverSheetImportService.execute(...)` method only — NOT the controller, NOT the `preview()` method.

### @MockitoBean (Spring Boot 3.4+ replacement for @MockBean)
**Source:** `src/test/java/org/ctc/dataimport/CsvImportControllerExceptionTest.java` line 37
**Apply to:** Both test classes — use `@MockitoBean` (from `org.springframework.test.context.bean.override.mockito.MockitoBean`), NOT the older `@MockBean` from `org.springframework.boot.test.mock.mockito.MockBean`.

### TestHelper Fixture Methods
**Source:** `src/test/java/org/ctc/TestHelper.java`
**Apply to:** `DriverSheetImportControllerTest.@BeforeEach`
- `testHelper.createSeason(name, year, number)` — creates Season with H2-compatible scoring entities.
- `testHelper.createTeam(name, shortName)` — persists Team; use short codes that match the sheet data rows in stubs.
- `testHelper.createDriver(psnId, nickname)` — persists Driver.
- `testHelper.createSeasonDriver(season, driver, team)` — persists SeasonDriver; needed for CONFLICT and UNCHANGED test scenarios.
- Do NOT use `testHelper.createFullSeasonFixture(prefix)` — it creates Matchday/Match/Race overhead that driver import tests do not need.

### Logging Convention
**Source:** `CLAUDE.md` §Logging; `src/main/java/org/ctc/dataimport/CsvImportController.java` lines 58, 118
**Apply to:** `DriverSheetImportController` and `DriverSheetImportService.execute()`
```java
log.error("Error executing driver sheet import", e);   // controller — exception paths
log.info("Executing driver sheet import: sheetUrl={}", sheetUrl);  // service — entry
log.debug("Tab {} processed: {} new drivers, {} assignments", year, newDriversCount, assignmentsCount);  // service — per-tab summary
```

---

## No Analog Found

All 7 files have strong in-repo analogs. No Phase 55 file lacks a match.

---

## Pitfall Index

| # | File | Pitfall | Resolution |
|---|------|---------|------------|
| P1 | `driver-import-preview.html` | Copy `th:style` from `import-preview.html` lines 50, 72-73 | Use CSS classes from `admin.css`; add `.badge-conflict` etc. if color-coding wanted |
| P2 | `driver-import-preview.html` | Use `th:utext` for psnId/teamCode | Always `th:text` only (XSS guard) |
| P3 | `DriverSheetImportController` | Copy `preview-sheet` dual-path from `CsvImportController` | Phase 55 has ONE preview path: sheetUrl only, no CSV/multipart |
| P4 | `DriverSheetImportService.execute()` | Forget `null` check on `allParams` | Add `if (allParams == null) allParams = Map.of();` at method start |
| P5 | `driver-import-preview.html` | Forget hidden `sheetUrl` input on the execute form | `<input type="hidden" name="sheetUrl" th:value="${sheetUrl}">` in the form |
| P6 | `DriverSheetImportService.execute()` | `IOException` from `preview()` won't auto-rollback | Catch and rethrow as `IllegalStateException("Sheet read failed: …", e)` |
| P7 | `DriverSheetImportControllerTest` | Use `multipart(...)` builder | Use `post(...)` with `.param()` — no file upload |
| P8 | `DriverSheetImportControllerTest` | `@MockBean` instead of `@MockitoBean` | Import from `org.springframework.test.context.bean.override.mockito.MockitoBean` |
| P9 | `DriverSheetImportControllerExceptionTest` | Add `@Transactional` (copying from happy-path test) | Exception test analog has NO `@Transactional`; omit it |
| P10 | `DriverSheetImportController` | Call `allParams` null-check in controller | Null-check belongs in `DriverSheetImportService.execute()` (thin controller principle) |
| P11 | `drivers.html` | Inline `style="..."` on the new button | Use only `.btn .btn-secondary` CSS classes — no `style=` attribute |

---

## Metadata

**Analog search scope:** `src/main/java/org/ctc/dataimport/`, `src/main/resources/templates/admin/`, `src/test/java/org/ctc/dataimport/`, `src/test/java/org/ctc/TestHelper.java`
**Files read for excerpt extraction:** 9 (CsvImportController.java, CsvImportControllerTest.java, CsvImportControllerExceptionTest.java, import.html, import-preview.html, drivers.html, DriverSheetImportService.java, CsvImportService.java lines 540-615, TestHelper.java)
**Pattern extraction date:** 2026-04-24
**Confidence:** HIGH — all 7 files have exact or role-match analogs that were read verbatim.
