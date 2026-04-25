# Phase 55: Admin Import UI & Transactional Execute - Research

**Researched:** 2026-04-24
**Domain:** Spring MVC Controller + Thymeleaf Templates + JPA Transactional Execute
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-14:** One `.card` section per year-tab, vertically stacked. Each section: H2 year, Season dropdown (pre-selected or empty+ambiguousReason), six inner tables per bucket with counts. Empty buckets collapse. Execute button at bottom outside per-tab loop. Ambiguous-Season warning banner above Execute.
- **D-15:** Controller signature mirrors `CsvImportController.execute(...)`. `@RequestParam String sheetUrl`, `@RequestParam(required = false) Map<String, String> allParams`, `RedirectAttributes`. Keys: `seasonId_<year>`, `skip_<psnId>_<year>`, `accept_<psnId>_<year>`.
- **D-16:** Ambiguous Season = proceed-with-warning. Tabs with blank/missing `seasonId_<year>` on execute are silently skipped. Flash summary includes skipped-tab list.
- **D-17:** Headline aggregated flash: "Import successful: N new drivers, N new assignments, N conflicts overwritten, N conflict skipped, N unchanged, N error. Skipped tabs: [year] (no season selected)."
- **D-18:** `admin/drivers.html` toolbar: "Import from Google Sheet" `.btn-secondary` immediately left of "+ New Driver".
- **D-19:** New Driver defaults: `psnId` = sheet value, `nickname` = psnId, `active` = true, `aliases` = empty.
- **D-06 (Phase 54, carried forward):** No `@SessionAttributes`. Execute re-fetches sheet and re-runs `preview()`.
- **D-07 (Phase 54, carried forward):** Cross-tab dedup via `Map<String, UUID>` on execute side.
- **D-08 (Phase 54, carried forward):** Per-row fuzzy decision independence across tabs.
- **D-12 (Phase 54, carried forward):** Bucketing precedence delegated to `preview()`.
- **IMPORT-06 (locked as requirement):** Single `@Transactional` boundary on `execute()` service method.

### Claude's Discretion

- Exact method signature on `DriverSheetImportService.execute(...)`.
- Internal result record shape (e.g. `ExecuteResult` with headline count fields).
- Preview-page JavaScript for bulk-action "Accept all fuzzy" / "Skip all conflicts" buttons — simple DOM version in scope; complex version deferred.
- Exact error messages for validation failures.
- `DriverSheetImportControllerTest` test method naming and scope depth.

### Deferred Ideas (OUT OF SCOPE)

- Import history / audit log (`ImportRun` entity).
- Email / push notification on completion.
- Dry-run / CSV download.
- Undo / rollback after commit.
- Cross-tab fuzzy-decision propagation UX.
- i18n of admin strings.
- Playwright E2E tests for the import flow.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| IMPORT-01 | Admin can open `/admin/drivers/import` via button on `/admin/drivers` and submit a Sheet URL to load a preview | D-18 button + GET handler + POST /preview handler |
| IMPORT-06 | Execute persists Driver + SeasonDriver within single `@Transactional` boundary with redirect + flash | Focus area 1: `@Transactional` on `execute()` service method |
| UX-07 | Each Conflict row has a Skip checkbox | D-15 form binding: `skip_<psnId>_<year>` key in allParams |
| UX-08 | Each Fuzzy row has an Accept checkbox | D-15 form binding: `accept_<psnId>_<year>` key in allParams |
| DATA-03 | Conflict default is overwrite; Skip-flagged rows leave SeasonDriver untouched | Execute walk: absence of `skip_` key → overwrite; presence → retain |
| TEST-02 | Integration tests cover full controller flow (form → preview → execute) with mocked GoogleSheetsService | Focus area 3: `DriverSheetImportControllerTest` with `@MockitoBean` |
| TEST-03 | Project line coverage stays ≥82% after new code | JaCoCo gate must pass with `./mvnw verify` |
| QUAL-01 | Admin templates use CSS classes from `admin.css`; no inline styles on buttons or badges | Focus area 9: Thymeleaf pitfalls |
| QUAL-02 | Controller delegates to `DriverSheetImportService`; no business logic in controller | Thin-controller pattern (verified from CsvImportController blueprint) |
| QUAL-03 | Form binding uses no direct JPA entity `@ModelAttribute` binding | D-15 uses `@RequestParam Map<String,String>`; no entity binding |
| QUAL-04 | Preview-state persistence follows existing `CsvImportService` pattern | D-06: re-fetch pattern, no @SessionAttributes |
</phase_requirements>

---

## Summary

Phase 55 delivers the admin-facing slice of the v1.8 bulk-driver-import milestone: one controller, two templates, one button, and a `@Transactional` execute method added to `DriverSheetImportService`. All heavy lifting lives in the Phase 54 `preview()` method already shipped. Phase 55's execute path calls `preview()` a second time (D-06 re-fetch), then walks the result applying the admin's per-row decisions from the submitted form.

The blueprint to mirror is `CsvImportController` (225 lines, confirmed via read). It demonstrates the three-handler pattern (GET form, POST /preview, POST /execute), the `Map<String, String> allParams` iteration, the exception catch block (IOException | BusinessRuleException | ValidationException | IllegalArgumentException | IllegalStateException | DataAccessException), and the flash-redirect idiom. The integration test blueprint is `CsvImportControllerTest` using `@SpringBootTest + @AutoConfigureMockMvc + @ActiveProfiles("dev") + @Transactional` with real DB state assertions and flash-attribute checks.

The key technical challenge is the execute walk: six bucket types, three decision flags, and cross-tab `Map<String, UUID>` deduplication. A complete pseudocode walk is provided in Focus Area 5. The transactional boundary is straightforward (`@Transactional` on the service method, no special propagation needed — Spring's default REQUIRED suffices). Driver save must precede SeasonDriver save; `driverRepository.save()` (not `saveAndFlush`) is sufficient because Spring Data's `save()` within one transaction makes the UUID available immediately for the subsequent `SeasonDriver` constructor.

**Primary recommendation:** Add one `execute(String sheetUrl, Map<String, String> allParams)` method to `DriverSheetImportService` annotated `@Transactional`, returning an `ExecuteResult` record with six count fields. The controller catches all checked and runtime exceptions and converts them to flash attributes.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| HTTP routing and form dispatch | Controller (`DriverSheetImportController`) | — | Thin controller: receives params, delegates, redirects |
| Preview categorization | Service (`DriverSheetImportService.preview()`) | — | Already shipped in Phase 54; re-called on execute (D-06) |
| Decision parsing from form params | Service (`execute()`) | — | Business logic belongs in service (QUAL-02) |
| Driver + SeasonDriver persistence | Service (`execute()`) via `@Transactional` | `DriverRepository`, `SeasonDriverRepository` | Transactional writes must be in service layer |
| Cross-tab psnId dedup | Service (`execute()`) | — | `Map<String, UUID>` lives within execute() scope |
| Flash message composition | Controller | — | HTTP concern: building redirect attributes |
| Season dropdown population | Controller (delegates to `SeasonManagementService.findAll()`) | — | GET handler provides `seasons` to template |
| Preview rendering (6 buckets × N tabs) | Template (`admin/driver-import-preview.html`) | — | Thymeleaf iterates `preview.tabPreviews()` |
| Entry button | Template (`admin/drivers.html`) | — | Static `<a>` link change, no new controller method |
| Integration testing | `DriverSheetImportControllerTest` | `@MockitoBean GoogleSheetsService` | Follows CsvImportControllerTest pattern exactly |

---

## Standard Stack

### Core (all existing — no new dependencies)

| Library | Version | Purpose | Notes |
|---------|---------|---------|-------|
| Spring MVC | Boot 4.0.5 bundled | Controller routing, MockMvc | `@Controller`, `@RequestMapping`, `@PostMapping` |
| Spring Data JPA | Boot 4.0.5 bundled | `DriverRepository.save()`, `SeasonDriverRepository.save()` / `findById()` | `@Transactional` on service method |
| Thymeleaf | Boot 4.0.5 bundled | Template rendering | `th:each`, `th:name`, `th:value`, `th:if`, `th:action` |
| Lombok | Boot 4.0.5 bundled | `@Slf4j`, `@RequiredArgsConstructor` on controller + service | No changes needed |
| JUnit 5 + Mockito | Boot 4.0.5 bundled | `@SpringBootTest`, `@MockitoBean` | Integration test pattern |

[VERIFIED: from pom.xml references in STACK.md and direct source reads]

**No new Maven dependencies required for Phase 55.** All needed libraries are already on the classpath.

---

## Architecture Patterns

### System Architecture Diagram

```
Admin Browser
     |
     | GET /admin/drivers/import
     v
DriverSheetImportController.showForm()
     |-- addAttribute("seasons", seasonManagementService.findAll())
     |-- addAttribute("sheetsAvailable", ...)
     --> render admin/driver-import.html

     | POST /admin/drivers/import/preview
     |   param: sheetUrl
     v
DriverSheetImportController.preview()
     |-- DriverSheetImportService.preview(sheetUrl)   [Phase 54 — read-only, no DB writes]
     |     |-- GoogleSheetsService (external API call)
     |     |-- DriverMatchingService (in-memory lookup)
     |     |-- SeasonRepository.findByYear() (read)
     |     |-- TeamRepository.findByShortName() (read)
     |     |-- SeasonDriverRepository.findBySeasonIdAndDriverId() (read)
     |     --> DriverSheetImportPreview (tabPreviews list, sorted by year)
     |-- on IOException/IllegalArgumentException: model.errorMessage -> render form
     --> render admin/driver-import-preview.html
           (form with sheetUrl hidden, seasonId_<year> selects, skip/accept checkboxes)

     | POST /admin/drivers/import/execute
     |   params: sheetUrl, allParams (seasonId_<year>, skip_<psnId>_<year>, accept_<psnId>_<year>)
     v
DriverSheetImportController.execute()
     |-- validate sheetUrl not blank (redirect with errorMessage if blank)
     |-- DriverSheetImportService.execute(sheetUrl, allParams)   [NEW, @Transactional]
     |     |-- DriverSheetImportService.preview(sheetUrl)        [re-fetch, D-06]
     |     |-- parse allParams into decisions map
     |     |-- walk tabPreviews ascending year:
     |     |     skip tab if seasonId_<year> blank/missing (D-16)
     |     |     resolve Season by seasonId UUID
     |     |     for each bucket row -> apply decision -> Driver.save() / SeasonDriver.save()/setTeam()
     |     |     cross-tab Map<psnId, driverUUID> dedup (D-07)
     |     --> ExecuteResult (counts + skippedTabYears)
     |-- flash successMessage (D-17 format)
     --> redirect:/admin/drivers/import
         on exception: flash errorMessage -> redirect:/admin/drivers/import
```

### Recommended Project Structure

```
src/main/java/org/ctc/
├── admin/controller/
│   └── DriverSheetImportController.java       [NEW ~130 lines]
├── dataimport/
│   └── DriverSheetImportService.java          [MODIFIED: +execute() method + ExecuteResult record, +60 lines]
src/main/resources/templates/admin/
├── driver-import.html                         [NEW ~50 lines]
├── driver-import-preview.html                 [NEW ~150 lines]
└── drivers.html                               [MODIFIED: +1 line button]
src/test/java/org/ctc/dataimport/
└── DriverSheetImportControllerTest.java       [NEW ~250 lines]
```

---

## Focus Area Deep-Dives

### Focus Area 1: Spring @Transactional Boundary

**Placement:** `@Transactional` on `DriverSheetImportService.execute(...)` only. The controller method is NOT annotated — it delegates entirely. [VERIFIED: matches pattern in CsvImportService line 101 and SeasonManagementService lines 117, 156, 223, etc.]

**Propagation:** Default `REQUIRED` is correct. No nested transaction complexity.

**RollbackFor:** Default Spring behavior rolls back on any `RuntimeException` (which covers `DataAccessException`, `ConstraintViolationException`, `IllegalStateException`, `BusinessRuleException`, `ValidationException`). `IOException` from `preview()` is checked — if it escapes `execute()`, it would NOT auto-rollback. Solution: catch `IOException` inside `execute()` and rethrow as `IllegalStateException("Sheet read failed: " + e.getMessage(), e)`, which is a RuntimeException and triggers rollback.

**OSIV and @Transactional interaction:** No conflict. OSIV keeps the Hibernate session open for the request, but `@Transactional` on the service method opens a proper transaction for the write. When the transaction commits at the end of `execute()`, writes are flushed. The OSIV session remains open for any post-commit lazy loading in the controller or redirect. No interference.

**Constraint violation detection:** `DataAccessException` (parent of `ConstraintViolationException` from Spring) is already caught in the controller's catch block. The `@Transactional` boundary ensures that if a `DataConstraintViolationException` is thrown mid-walk, no partial writes persist. [VERIFIED: CsvImportController.execute() catch block at line 205-208 catches DataAccessException]

**Key rule:** `DriverSheetImportService.preview()` is called INSIDE `execute()` before writes begin. If `preview()` throws `IOException`, the transaction has not yet dirtied anything — wrap and rethrow before any save.

**Concrete annotation:**
```java
@Transactional
public ExecuteResult execute(String sheetUrl, Map<String, String> allParams) throws IOException {
    // ... or wrap IOException internally
}
```

[VERIFIED: pattern from CsvImportService.executeImport() at line 101]

---

### Focus Area 2: Driver + SeasonDriver Save Ordering

**Save chain:**
1. `driverRepository.save(new Driver(psnId, psnId))` then `driver.setActive(true)` (note: `Driver` constructor only takes psnId + nickname; `active` defaults to `true` per entity definition). [VERIFIED: Driver.java line 46-48]
2. SeasonDriver constructor requires a managed Season entity, the saved Driver, and a managed Team entity. All must be looked up by UUID from the DB before construction.
3. `seasonDriverRepository.save(new SeasonDriver(season, driver, team))`

**`save` vs `saveAndFlush`:** Use `driverRepository.save()` (not `saveAndFlush`). Within the same transaction, Hibernate tracks the saved entity in the first-level cache. The UUID is assigned by `@GeneratedValue(strategy = GenerationType.UUID)` at persist time (Hibernate 6 assigns the UUID before the INSERT using its in-memory generator — the entity gets its `id` set by `driverRepository.save()`). The `SeasonDriver` constructor takes the `Driver` object directly (not just a UUID), so the UUID is available from the returned entity after `save()`. [VERIFIED: SeasonDriver.java constructor at line 40-43 takes `Driver driver` object directly]

**Cross-tab dedup (D-07):** Maintain `Map<String, Driver> createdDrivers` (keyed by psnId, storing the saved entity) across the tab walk. First occurrence of a psnId needing creation: save Driver, store in map. Subsequent occurrences: look up map, use stored Driver for SeasonDriver construction.

**Conflict overwrite path:** For a CONFLICT row where skip is NOT set:
```java
SeasonDriver sd = seasonDriverRepository.findById(conflictRow.existingSeasonDriverId()).orElseThrow();
Team newTeam = teamRepository.findByShortName(conflictRow.sheetTeamShortName()).orElseThrow();
sd.setTeam(newTeam);
seasonDriverRepository.save(sd);
```
[VERIFIED: `ConflictRow` carries `existingSeasonDriverId` — confirmed via DriverSheetImportService.java line 237-238 and 54-REVIEW-FIX.md IR-03]

**UNCHANGED rows:** No write action needed (they already have correct SeasonDriver). Count them for the flash summary.

**ERROR rows:** Never imported per requirement UX-06. Just count for flash summary.

---

### Focus Area 3: Integration Test Pattern

**Class structure (from CsvImportControllerTest + CsvImportControllerExceptionTest):**
- `@SpringBootTest` — full Spring context, real beans
- `@AutoConfigureMockMvc` — activates MockMvc
- `@ActiveProfiles("dev")` — H2 in-memory, no auth
- `@Transactional` — auto-rollback after each test (preserves isolation)

**Two-class approach (matching the blueprint exactly):**
1. `DriverSheetImportControllerTest.java` — happy-path tests using real DB + `@MockitoBean GoogleSheetsService` to short-circuit real API calls. Uses `TestHelper` for Season, Team, Driver fixtures.
2. (Optional): separate exception-path test class `DriverSheetImportControllerExceptionTest.java` using `@MockitoBean DriverSheetImportService` to force throws.

**Key mock pattern** from `CsvImportControllerExceptionTest`:
```java
@MockitoBean
private GoogleSheetsService googleSheetsService;
```
Use `@MockitoBean` (Spring Boot 3.4+ annotation, replaces `@MockBean`). [VERIFIED: CsvImportControllerExceptionTest line 39 uses `@MockitoBean`]

**Mocking GoogleSheetsService for happy-path tests:** The integration test must stub:
- `googleSheetsService.extractSpreadsheetId(anyString())` → returns a fake spreadsheet ID
- `googleSheetsService.getSheetNames(anyString())` → returns `List.of("2024")` (year tab)
- `googleSheetsService.readRangeFromSheet(anyString(), eq("2024"), eq("A:C"))` → returns rows (header + data rows)

**Flash attribute assertion:**
```java
.andExpect(flash().attributeExists("successMessage"))
.andExpect(flash().attribute("successMessage", containsString("new drivers")))
```

**DB state assertion after execute:**
```java
assertThat(driverRepository.findByPsnId("new_driver_psn")).isPresent();
assertThat(seasonDriverRepository.findBySeasonIdAndDriverId(season.getId(), driverId)).isPresent();
```

**Form POST simulation for execute:**
```java
mockMvc.perform(post("/admin/drivers/import/execute")
    .param("sheetUrl", "https://docs.google.com/spreadsheets/d/abc123")
    .param("seasonId_2024", season.getId().toString())
    .param("skip_conflict_psn_2024", "on"))
```

**NOTE:** Test class naming. The project uses `*Test.java` for both unit and integration tests (not `*IT.java`). [VERIFIED: CsvImportControllerTest.java, all files in src/test reviewed]

---

### Focus Area 4: Thymeleaf Rendering of 6 Buckets Per Tab

**Outer loop:**
```html
<th:block th:each="tab : ${preview.tabPreviews()}">
  <div class="card mb-md">
    <h2 th:text="${tab.year()}"></h2>
    
    <!-- Season dropdown -->
    <div class="form-group">
      <label th:for="'seasonId_' + ${tab.year()}">Season</label>
      <select th:name="'seasonId_' + ${tab.year()}" th:id="'seasonId_' + ${tab.year()}">
        <option value="">-- Select season --</option>
        <option th:each="s : ${seasons}"
                th:value="${s.id}"
                th:text="${s.displayLabel}"
                th:selected="${s.id == tab.suggestedSeasonId()}"></option>
      </select>
      <!-- Ambiguous reason hint -->
      <small th:if="${tab.ambiguousReason() != null}" 
             class="text-dim" 
             th:text="${tab.ambiguousReason()}"></small>
    </div>

    <!-- Bucket: New Drivers -->
    <th:block th:if="${!tab.newDrivers().isEmpty()}">
      <h3 th:text="'New Drivers (' + ${#lists.size(tab.newDrivers())} + ')'"></h3>
      <table>...</table>
    </th:block>

    <!-- Bucket: New Assignments -->
    <th:block th:if="${!tab.newAssignments().isEmpty()}">...</th:block>

    <!-- Bucket: Conflicts (with Skip checkboxes) -->
    <th:block th:if="${!tab.conflicts().isEmpty()}">
      <h3 th:text="'Conflicts (' + ${#lists.size(tab.conflicts())} + ')'"></h3>
      <table>
        <thead><tr><th>PSN ID</th><th>Current Team</th><th>Sheet Team</th><th>Skip</th></tr></thead>
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
    </th:block>

    <!-- Bucket: Fuzzy Suggestions (with Accept checkboxes) -->
    <th:block th:if="${!tab.fuzzySuggestions().isEmpty()}">
      <h3>...</h3>
      <table>
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
    </th:block>

    <!-- Bucket: Unchanged (display-only) -->
    <!-- Bucket: Errors (display-only) -->
  </div>
</th:block>
```

**Thymeleaf string concatenation:** Use `'literal_' + ${expression}` for `th:name` and `th:id`. This is standard SpEL-in-attribute syntax and works correctly with string literals and variable expressions. [VERIFIED: pattern used in existing import-preview.html line 63: `th:name="'confirm_' + ${row.psnId}"`]

**Empty bucket collapse:** Use `th:if="${!tab.newDrivers().isEmpty()}"`. When a bucket list is empty, the entire `th:block` renders as nothing. No CSS tricks needed.

**`th:selected` on Season dropdown:** Use `th:selected="${s.id == tab.suggestedSeasonId()}"` — Thymeleaf evaluates this as a boolean and adds/removes the `selected` attribute. [ASSUMED] (standard Thymeleaf pattern; not explicitly verified in project code but is documented Thymeleaf behavior)

**Hidden sheetUrl for re-submission:** The preview page form must carry `sheetUrl` as a hidden input so execute can re-fetch:
```html
<form th:action="@{/admin/drivers/import/execute}" method="post">
  <input type="hidden" name="sheetUrl" th:value="${sheetUrl}">
  <!-- all tab sections with their inputs -->
  <button type="submit" class="btn btn-primary">Execute Import</button>
  <a th:href="@{/admin/drivers/import}" class="btn btn-secondary">Cancel</a>
</form>
```

**`th:action` resolution:** `@{/admin/drivers/import/execute}` is an absolute path — Thymeleaf resolves it against the context root. Correct for POST. [VERIFIED: existing import-preview.html line 143 uses `th:action="@{/admin/import/execute}"` — same pattern]

---

### Focus Area 5: Execute Path — Re-run Preview and Reconcile

**Method signature (recommended):**
```java
@Transactional
public ExecuteResult execute(String sheetUrl, Map<String, String> allParams) {
```

**Pseudocode walk:**

```
ExecuteResult result = new ExecuteResult()
Map<String, Driver> crossTabCreatedDrivers = new HashMap<>()  // D-07 dedup

DriverSheetImportPreview preview = this.preview(sheetUrl)     // D-06 re-fetch
// tabs already sorted ascending by year (Phase 54 D-05)

for each TabPreview tab in preview.tabPreviews():

    String seasonIdKey = "seasonId_" + tab.year()
    String seasonIdStr = allParams.get(seasonIdKey)
    
    if (seasonIdStr == null || seasonIdStr.isBlank()):
        result.addSkippedTab(tab.year())    // D-16 silent skip
        continue
    
    UUID seasonId = UUID.fromString(seasonIdStr)
    Season season = seasonRepository.findById(seasonId).orElseThrow(...)
    
    // --- NEW_DRIVER rows ---
    for NewDriverRow row in tab.newDrivers():
        Driver driver = crossTabCreatedDrivers.get(row.psnId())
        if driver == null:
            driver = new Driver(row.psnId(), row.psnId())   // D-19: nickname = psnId
            // active=true is already the default
            driver = driverRepository.save(driver)
            crossTabCreatedDrivers.put(row.psnId(), driver)
            result.incrementNewDrivers()
        Team team = teamRepository.findByShortName(row.teamShortName()).orElseThrow(...)
        seasonDriverRepository.save(new SeasonDriver(season, driver, team))
        result.incrementNewAssignments()

    // --- NEW_ASSIGNMENT rows ---
    for NewAssignmentRow row in tab.newAssignments():
        Driver driver = crossTabCreatedDrivers.computeIfAbsent(row.psnId(), k ->
            driverRepository.findById(row.existingDriverId()).orElseThrow(...))
        Team team = teamRepository.findByShortName(row.teamShortName()).orElseThrow(...)
        // check if SeasonDriver already exists (race condition guard)
        Optional<SeasonDriver> existing = seasonDriverRepository
            .findBySeasonIdAndDriverId(season.getId(), driver.getId())
        if existing.isEmpty():
            seasonDriverRepository.save(new SeasonDriver(season, driver, team))
            result.incrementNewAssignments()
        // else: treat as unchanged (shouldn't happen in normal flow)

    // --- CONFLICT rows ---
    for ConflictRow row in tab.conflicts():
        String skipKey = "skip_" + row.psnId() + "_" + tab.year()
        boolean skipped = "on".equals(allParams.get(skipKey))
        if skipped:
            result.incrementConflictsSkipped()
        else:
            // Overwrite: update existing SeasonDriver's team
            SeasonDriver sd = seasonDriverRepository.findById(row.existingSeasonDriverId())
                .orElseThrow(...)
            Team newTeam = teamRepository.findByShortName(row.sheetTeamShortName()).orElseThrow(...)
            sd.setTeam(newTeam)
            seasonDriverRepository.save(sd)
            result.incrementConflictsOverwritten()

    // --- FUZZY_SUGGESTION rows ---
    for FuzzySuggestionRow row in tab.fuzzySuggestions():
        String acceptKey = "accept_" + row.psnId() + "_" + tab.year()
        String acceptValue = allParams.get(acceptKey)
        
        Driver driver
        if acceptValue != null && !acceptValue.isBlank():
            // Accept checked: link to suggested driver (D-08: per-row independence)
            UUID suggestedDriverId = UUID.fromString(acceptValue)
            driver = crossTabCreatedDrivers.computeIfAbsent(row.psnId(), k ->
                driverRepository.findById(suggestedDriverId).orElseThrow(...))
            // Note: crossTabCreatedDrivers key is psnId; value is the SUGGESTED driver
            // Subsequent tabs seeing the same psnId reuse this linked driver (D-07)
        else:
            // Accept not checked: create new driver (D-08)
            driver = crossTabCreatedDrivers.get(row.psnId())
            if driver == null:
                driver = new Driver(row.psnId(), row.psnId())   // D-19
                driver = driverRepository.save(driver)
                crossTabCreatedDrivers.put(row.psnId(), driver)
                result.incrementNewDrivers()
        
        Team team = teamRepository.findByShortName(row.teamShortName()).orElseThrow(...)
        Optional<SeasonDriver> existing = seasonDriverRepository
            .findBySeasonIdAndDriverId(season.getId(), driver.getId())
        if existing.isEmpty():
            seasonDriverRepository.save(new SeasonDriver(season, driver, team))
            result.incrementNewAssignments()

    // --- UNCHANGED rows --- (no DB action needed)
    result.addUnchanged(tab.unchanged().size())

    // --- ERROR rows --- (never imported, UX-06)
    result.addErrors(tab.errors().size())

return result
```

**Important nuance for FUZZY with Accept:** The `th:value` on the Accept checkbox is `${row.suggestedDriverId()}` (a UUID string). When the admin checks "Accept", the form submits `accept_<psnId>_<year>=<driverUUID>`. When unchecked, the browser sends nothing (no key at all). So `allParams.get(acceptKey) == null` means "not accepted" (create new driver). [VERIFIED: HTML checkbox behavior — unchecked checkboxes are not submitted by browsers]

---

### Focus Area 6: ExecuteResult Record Shape

**Recommended record (defined as inner record of `DriverSheetImportService`):**

```java
public record ExecuteResult(
    int newDriversCount,
    int newAssignmentsCount,
    int conflictsOverwrittenCount,
    int conflictsSkippedCount,
    int unchangedCount,
    int errorCount,
    List<Integer> skippedTabYears
) {
    public boolean hasSkippedTabs() {
        return !skippedTabYears.isEmpty();
    }
}
```

**Note:** Since records are immutable, the execute() method builds these counts imperatively using local counter variables and constructs the record at the end. Alternatively, use a mutable builder inner class (matching `CsvImportService.ImportResult` style at line 587 which uses `@Getter` mutable fields). Either approach works; the mutable builder approach (matching the existing pattern) is simpler for the incremental walk.

**Mutable builder approach (matching CsvImportService.ImportResult precedent):**
```java
// Inner class (not record) for mutable accumulation during execute walk
public static class ExecuteResult {
    private int newDriversCount;
    private int newAssignmentsCount;
    private int conflictsOverwrittenCount;
    private int conflictsSkippedCount;
    private int unchangedCount;
    private int errorCount;
    private final List<Integer> skippedTabYears = new ArrayList<>();
    
    void incrementNewDrivers() { newDriversCount++; }
    void incrementNewAssignments() { newAssignmentsCount++; }
    void incrementConflictsOverwritten() { conflictsOverwrittenCount++; }
    void incrementConflictsSkipped() { conflictsSkippedCount++; }
    void addUnchanged(int n) { unchangedCount += n; }
    void addErrors(int n) { errorCount += n; }
    void addSkippedTab(int year) { skippedTabYears.add(year); }
    
    // Getters for controller flash message composition
}
```

**Controller flash composition (D-17):**
```java
var result = driverSheetImportService.execute(sheetUrl, allParams);
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
```

---

### Focus Area 7: Given-When-Then Test Method Names

**Target: ≥15 test methods across 1-2 test classes.**

**`DriverSheetImportControllerTest.java` (happy-path + DB assertion):**

1. `whenGetImportPage_thenShowsImportFormWithSeasonsAndSheetsAvailable()`
2. `givenValidSheetUrl_whenPreview_thenRendersPreviewPageWithTabPreviews()`
3. `givenBlankSheetUrl_whenPreview_thenRendersFormWithErrorMessage()`
4. `givenSheetsUnavailable_whenPreview_thenRendersFormWithErrorMessage()`
5. `givenNewDriverRow_whenExecute_thenDriverAndSeasonDriverCreatedInDb()`
6. `givenNewAssignmentRow_whenExecute_thenSeasonDriverCreatedForExistingDriver()`
7. `givenConflictRowWithSkipSet_whenExecute_thenExistingSeasonDriverUntouched()`
8. `givenConflictRowWithoutSkip_whenExecute_thenSeasonDriverTeamOverwritten()`
9. `givenFuzzyRowWithAcceptSet_whenExecute_thenSuggestedDriverLinkedToSeason()`
10. `givenFuzzyRowWithoutAccept_whenExecute_thenNewDriverCreated()`
11. `givenSamePsnInMultipleTabs_whenExecute_thenDriverCreatedOnceOnly()` — D-07 verification
12. `givenAmbiguousSeasonTabWithBlankSeasonId_whenExecute_thenTabSkippedAndFlashNotes()`
13. `givenAllTabsSkipped_whenExecute_thenSuccessFlashWithZeroCounts()`
14. `givenUnchangedRow_whenExecute_thenNoDbWriteAndCountedInFlash()`
15. `givenErrorRow_whenExecute_thenRowNotImportedAndCountedInFlash()`

**`DriverSheetImportControllerExceptionTest.java` (exception paths via `@MockitoBean DriverSheetImportService`):**

16. `givenIoException_whenPreview_thenRendersFormWithErrorMessage()`
17. `givenDataAccessException_whenExecute_thenRedirectsWithErrorFlash()`
18. `givenIllegalStateException_whenExecute_thenRedirectsWithErrorFlash()`
19. `givenBlankSheetUrl_whenExecute_thenRedirectsWithValidationError()`

**Total: 19 test methods (exceeds the 15+ target).**

---

### Focus Area 8: Validation Architecture (Nyquist)

**Test Framework:**
| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) via Spring Boot Test |
| Config file | Surefire in pom.xml (lines 184-194) |
| Quick run command | `./mvnw test -Dtest=DriverSheetImportControllerTest` |
| Full suite command | `./mvnw verify` |

**Phase Requirements → Test Map:**

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| IMPORT-01 | GET /admin/drivers/import returns 200 + form attributes | Integration | `./mvnw test -Dtest=DriverSheetImportControllerTest#whenGetImportPage_*` | Wave 0 |
| IMPORT-01 | Button on /admin/drivers links to /admin/drivers/import | Template | Visual / integration: model provides no redirect | Wave 0 |
| IMPORT-06 | Execute creates Driver+SeasonDriver in single transaction | Integration | `...#givenNewDriverRow_whenExecute_*` | Wave 0 |
| IMPORT-06 | DB rolled back on exception | Integration | `...#givenDataAccessException_whenExecute_*` | Wave 0 |
| UX-07 | Skip checkbox present for Conflict rows | Integration (view check) | `...#givenConflictRowWithSkipSet_*` | Wave 0 |
| UX-07 | Skip checked → existing SeasonDriver untouched | Integration (DB) | `...#givenConflictRowWithSkipSet_whenExecute_*` | Wave 0 |
| UX-08 | Accept checkbox present for Fuzzy rows | Integration (view check) | `...#givenFuzzyRowWithAcceptSet_*` | Wave 0 |
| UX-08 | Accept unchecked → new Driver created | Integration (DB) | `...#givenFuzzyRowWithoutAccept_*` | Wave 0 |
| DATA-03 | Default conflict = overwrite | Integration (DB) | `...#givenConflictRowWithoutSkip_whenExecute_*` | Wave 0 |
| TEST-02 | Full GET→POST /preview→POST /execute flow with mocked GoogleSheetsService | Integration | `./mvnw test -Dtest=DriverSheetImportControllerTest` | Wave 0 |
| TEST-03 | JaCoCo 82% gate | Coverage | `./mvnw verify` | Existing (pom.xml) |
| QUAL-01 | No inline styles in templates | Static/visual | Reviewer check + playwright-cli | Wave 0 (template review) |
| QUAL-02 | Controller has no repository calls | Code review | Static analysis / reviewer | Wave 0 |
| QUAL-03 | No JPA entity @ModelAttribute | Code review | Static analysis / reviewer | Wave 0 |
| QUAL-04 | No @SessionAttributes pattern | Code review | `grep -n "@SessionAttributes" DriverSheetImportController.java` | Wave 0 |

**Sampling Rate:**
- Per task commit: `./mvnw test -Dtest=DriverSheetImportControllerTest`
- Per wave merge: `./mvnw verify`
- Phase gate: `./mvnw verify` (full suite green before `/gsd-verify-work`)

**Wave 0 Gaps (must create before implementation):**
- [ ] `src/test/java/org/ctc/dataimport/DriverSheetImportControllerTest.java` — covers IMPORT-01, IMPORT-06, UX-07, UX-08, DATA-03, TEST-02, QUAL-04
- [ ] `src/test/java/org/ctc/dataimport/DriverSheetImportControllerExceptionTest.java` — covers exception paths
- [ ] `src/main/java/org/ctc/admin/controller/DriverSheetImportController.java` — stub (compile-green) before test can be written
- [ ] `src/main/resources/templates/admin/driver-import.html` — minimal stub for GET test
- [ ] `src/main/resources/templates/admin/driver-import-preview.html` — minimal stub for preview test

---

### Focus Area 9: Spring + Thymeleaf + Form-Binding Pitfalls

**Pitfall 1: Unchecked checkboxes are not submitted**

HTML form behavior: unchecked checkboxes are completely absent from the POST body. So `allParams.get("skip_foo_2024") == null` means the admin did NOT check Skip (default = overwrite). This is correct behavior for D-15: absence of `skip_<psnId>_<year>` → overwrite. The execute() implementation must treat `null` (absent) as "no skip".

Similarly, `allParams.get("accept_foo_2024") == null` means "do not accept" (create new driver, not link to suggested).

**No hidden-input "baseline" needed** for skip/accept because absence is the correct default signal.

**Pitfall 2: `@RequestParam(required = false) Map<String, String> allParams`**

When the form posts ONLY `sheetUrl` and NO dynamic params (e.g., a sheet with zero interactive rows), `allParams` will be `null`. The execute() method must null-check: `if (allParams == null) allParams = Map.of()`. [VERIFIED: CsvImportController line 178 checks `if (allParams != null)` before iterating]

**Pitfall 3: `sheetUrl` must be a hidden input in the preview form**

The preview page is rendered on POST /preview. On POST /execute, the controller needs `sheetUrl` to re-fetch (D-06). The preview page form must carry it as a hidden input. Forgetting this causes a blank/null `sheetUrl` on execute, which must be caught and redirected with error.

**Pitfall 4: seasonId_<year> is a String UUID, not a UUID directly**

`allParams.get("seasonId_2024")` returns a String (or null). The execute() method must parse it with `UUID.fromString(seasonIdStr)` after null/blank check. Wrap `UUID.fromString` in a try-catch or validate the format before calling.

**Pitfall 5: Thymeleaf `th:if` on empty bucket tables**

If `tab.newDrivers().isEmpty()` returns true, the `<th:block th:if="...">` renders as nothing. This is the correct empty-bucket collapse. Do NOT use `th:if="${#lists.size(tab.newDrivers()) > 0}"` (equivalent but more verbose). The simpler `th:if="${!tab.newDrivers().isEmpty()}"` matches project conventions.

**Pitfall 6: `th:selected` requires UUID equals comparison**

`th:selected="${s.id == tab.suggestedSeasonId()}"` compares a `java.util.UUID` from the Season entity with the `UUID` from `TabPreview.suggestedSeasonId()`. Both are `java.util.UUID` — SpEL `==` will use `.equals()` correctly. No toString() conversion needed.

**Pitfall 7: `allParams` includes ALL request params, not just dynamic ones**

`@RequestParam(required = false) Map<String, String> allParams` collects ALL POST parameters into the map, including `sheetUrl` itself and all `seasonId_<year>` params. When iterating to extract skip/accept params, filter by key prefix: `if (entry.getKey().startsWith("skip_"))`. [VERIFIED: CsvImportController line 180 does `entry.getKey().startsWith("confirm_")`]

**Pitfall 8: `admin/import-preview.html` has inline styles — DO NOT copy**

The existing `import-preview.html` uses `style="..."` on `<tr>` (line 50) and `<span>` (line 72-73). Phase 55 templates MUST NOT copy these. Use CSS classes from `admin.css` exclusively (QUAL-01). If color-coded bucket badges are desired, add semantic classes (`.badge-new`, `.badge-conflict`, `.badge-fuzzy`, `.badge-error`, `.badge-unchanged`) to `admin.css` instead.

**Pitfall 9: `th:action` form action URL must use `@{}`**

Use `th:action="@{/admin/drivers/import/execute}"` — not a bare string. The `@{}` syntax ensures context-path-aware URL resolution. [VERIFIED: import-preview.html line 143 pattern]

**Pitfall 10: Test class in wrong package**

`DriverSheetImportController` lives in `org.ctc.admin.controller`. But the test blueprint (`CsvImportControllerTest`) lives in `org.ctc.dataimport`. Because this is an import feature, placing `DriverSheetImportControllerTest` in `org.ctc.dataimport` mirrors the precedent and co-locates with the service tests. Either package works (Spring Boot test scans everything), but `org.ctc.dataimport` matches the blueprint.

---

### Focus Area 10: Security / Threat Model

**T-54-02 (carried forward): Stored-XSS risk via sheet cells**

Sheet cells (PSN IDs, team codes) are fetched from Google Sheets and rendered in Thymeleaf templates. They must not be rendered unescaped.

**Thymeleaf auto-escape:** By default, `th:text="${row.psnId()}"` HTML-escapes the value before rendering. This prevents XSS. `th:utext` would render raw HTML — **never use `th:utext` for user-provided data.** [ASSUMED — standard Thymeleaf default behavior; consistent with Thymeleaf docs]

**Action for template implementation:** Use only `th:text` for all bucket row data (psnId, teamShortName, reason messages, nicknames). Zero `th:utext` in either template.

**DB side:** Spring Data JPA uses parameterized queries (PreparedStatement) automatically. No hand-rolled SQL concatenation anywhere in this phase. SQL injection is not a concern. [VERIFIED: all repository calls use Spring Data derived queries]

**CSRF:** Spring Security is enabled in `prod` and `docker` profiles. The `admin/layout.html` (or base form pattern) must include a CSRF token in the POST forms. Thymeleaf + Spring Security auto-inserts the CSRF hidden input when the `SpringSecurityDialect` is on the classpath and `th:action` is used (not bare `action`). [ASSUMED — standard Spring Security + Thymeleaf integration; consistent with existing admin forms that use `th:action`]

**Admin role enforcement:** The `/admin/**` path prefix is secured in `SecurityConfig` for prod/docker profiles. No new security configuration needed for `/admin/drivers/import/**` — it is automatically covered by the existing admin security rule. [ASSUMED — consistent with how other /admin/* routes work; SecurityConfig scope not explicitly re-verified in this session]

**ASVS Categories applicable to Phase 55:**

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No (no new auth paths) | Existing admin auth |
| V3 Session Management | No (@SessionAttributes explicitly excluded by D-06) | — |
| V4 Access Control | Yes (admin-only route) | Existing `/admin/**` Spring Security rule |
| V5 Input Validation | Yes (sheetUrl, UUID params) | Null/blank guards in controller; UUID.fromString() |
| V6 Cryptography | No | — |
| V7 Error Handling | Yes (exception catch → flash, never leaks stack trace) | Controller catch block mirrors CsvImportController |

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Transaction management | Manual begin/commit/rollback | `@Transactional` on service method | Spring handles all edge cases |
| UUID assignment for new Driver | Custom UUID generation | `@GeneratedValue(strategy = GenerationType.UUID)` on `Driver.id` | Already configured |
| Thymeleaf CSRF token | Manual `<input type="hidden" name="_csrf">` | `th:action` (auto-injects CSRF when Spring Security is present) | Thymeleaf-Security integration |
| Driver uniqueness guard | Manual `findByPsnId` before save | Let DB constraint + `DataAccessException` handle it | `Driver.psnId` has `@Column(unique = true)` |
| Cross-tab dedup logic | Complex merge algorithm | Simple `Map<String, Driver>` in execute() local scope | The naive map approach fully satisfies D-07 |
| HTML-escaping of sheet data | Manual escaping | `th:text` (auto-escapes) | Standard Thymeleaf behavior |

---

## File Modification Impact List

| File Path | Action | Estimated Size | Notes |
|-----------|--------|----------------|-------|
| `src/main/java/org/ctc/admin/controller/DriverSheetImportController.java` | CREATE | ~130 lines | 3 handlers (GET, POST /preview, POST /execute); injects DriverSheetImportService + SeasonManagementService + GoogleSheetsService |
| `src/main/java/org/ctc/dataimport/DriverSheetImportService.java` | MODIFY | +65 lines | Add `execute()` method + `ExecuteResult` inner class; `preview()` untouched |
| `src/main/resources/templates/admin/driver-import.html` | CREATE | ~45 lines | Simple form: sheetUrl input, POST /preview, uses admin/layout |
| `src/main/resources/templates/admin/driver-import-preview.html` | CREATE | ~160 lines | Per-tab card loop, 6 bucket tables, Skip/Accept controls, hidden sheetUrl, Execute button |
| `src/main/resources/templates/admin/drivers.html` | MODIFY | +2 lines | Add "Import from Google Sheet" `.btn-secondary` before "+ New Driver" in toolbar |
| `src/main/resources/static/admin/css/admin.css` | MODIFY (optional) | +10 lines | Add bucket badge classes if color-coding adopted (`.badge-new`, `.badge-conflict`, etc.) |
| `src/test/java/org/ctc/dataimport/DriverSheetImportControllerTest.java` | CREATE | ~250 lines | 15 happy-path + DB assertion tests with @MockitoBean GoogleSheetsService |
| `src/test/java/org/ctc/dataimport/DriverSheetImportControllerExceptionTest.java` | CREATE | ~80 lines | 4 exception-path tests with @MockitoBean DriverSheetImportService |

**Files explicitly NOT modified:**
- `DriverSheetImportService.preview()` (Phase 54 contract, locked)
- `SeasonRepository`, `TeamRepository`, `SeasonDriverRepository` (no new query methods needed for execute)
- Any Flyway migration file (DATA-04)
- `RaceLineup*` (DATA-05)
- `GoogleSheetsService` (black box)
- `DriverMatchingService` (Phase 54 black box, not called in execute — Phase 54 already resolved matches in preview)

---

## Common Pitfalls

### Pitfall 1: Calling `driverMatchingService.findDriver()` in execute()

**What goes wrong:** Execute re-runs preview(), which already did all driver matching. Calling `findDriver()` again in execute() is redundant, potentially inconsistent (driver DB changed between preview and execute), and violates D-06 spirit.
**Why it happens:** Implementer tries to resolve drivers "fresh" instead of trusting the pre-categorized buckets.
**How to avoid:** Trust the bucket rows from `preview()`. `NewDriverRow` = no driver exists, create one. `NewAssignmentRow.existingDriverId` = the driver UUID to load. `FuzzySuggestionRow.suggestedDriverId` = the UUID to link if Accept.

### Pitfall 2: Using `saveAndFlush` for new Driver before SeasonDriver

**What goes wrong:** Unnecessary extra flush to DB mid-transaction; performance concern.
**Why it happens:** Developer worried UUID isn't assigned until flush.
**How to avoid:** Spring Data's `driverRepository.save()` returns the entity with the UUID assigned (Hibernate assigns UUID in-memory via `@GeneratedValue(strategy = GenerationType.UUID)`). No flush needed. The `SeasonDriver` constructor takes the `Driver` object, not just a UUID.

### Pitfall 3: Forgetting to null-check allParams before iteration

**What goes wrong:** NullPointerException at `allParams.entrySet()` when the form posts no dynamic params.
**Why it happens:** Spring only populates `allParams` if there are any params with matching keys.
**How to avoid:** `if (allParams == null) allParams = Map.of();` at the top of execute().

### Pitfall 4: Using `th:utext` instead of `th:text` for sheet data

**What goes wrong:** XSS — a malicious psnId like `<script>alert(1)</script>` in the sheet renders as executable JavaScript.
**Why it happens:** Copy-paste error or desire to render formatted text.
**How to avoid:** Always `th:text`. All sheet-sourced strings are plain text, never HTML.

### Pitfall 5: Missing hidden `sheetUrl` input on preview page

**What goes wrong:** `POST /execute` receives blank `sheetUrl`; controller cannot re-fetch.
**Why it happens:** Missed the D-06 re-fetch requirement when building the preview form.
**How to avoid:** The preview page form must have `<input type="hidden" name="sheetUrl" th:value="${sheetUrl}">`. The controller must also add `sheetUrl` to the model on POST /preview success.

### Pitfall 6: Catch block in controller not mirroring CsvImportController exactly

**What goes wrong:** Some exception types escape to `GlobalExceptionHandler`, showing an error page instead of a flash message.
**Why it happens:** Incomplete catch block (missing `IOException` or `DataAccessException`).
**How to avoid:** Copy the catch signature from CsvImportController line 205: `catch (IOException | BusinessRuleException | ValidationException | IllegalArgumentException | IllegalStateException | DataAccessException e)`.

### Pitfall 7: Inline styles in templates (QUAL-01 violation)

**What goes wrong:** Code review fails; CSS-class enforcement rule violated.
**Why it happens:** Copy-pasting from `import-preview.html` which has inline styles on `<tr>` (line 50) and `<span>` (lines 72-73).
**How to avoid:** Use `admin.css` classes for all visual differentiation. Add minimal bucket badge classes if needed.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `th:selected="${s.id == tab.suggestedSeasonId()}"` performs UUID `.equals()` comparison correctly via SpEL | Focus Area 4 | Dropdown would not pre-select correct season; admin would need to manually pick every time |
| A2 | Thymeleaf auto-escapes `th:text` (prevents XSS) | Focus Area 10 | XSS vulnerability if wrong; mitigate by reviewing output in browser |
| A3 | Thymeleaf + Spring Security auto-inserts CSRF token when `th:action` is used | Focus Area 10 | Forms would fail CSRF check in prod profile; verify by checking existing form behavior |
| A4 | `/admin/**` Spring Security rule covers `/admin/drivers/import/**` without explicit mapping | Focus Area 10 | Import page accessible without auth in prod if wrong; verify SecurityConfig scope |
| A5 | `@GeneratedValue(strategy = GenerationType.UUID)` assigns UUID at `save()` time (not flush) | Focus Area 2 | Would need `saveAndFlush` if wrong; safe to verify by existing `CsvImportService` `driverRepository.save()` pattern |

---

## Open Questions

1. **Redirect target after execute success**
   - What we know: 55-CONTEXT.md says `redirect:/admin/drivers/import` (to see flash in context), but notes `redirect:/admin/drivers` is also reasonable.
   - What's unclear: Which is better UX — staying on import page (allow re-import) or going back to driver list?
   - Recommendation: Default to `redirect:/admin/drivers/import` (mirrors CsvImportController self-redirect pattern); planner can override.

2. **`SeasonManagementService.findAll()` vs `findByActiveTrue()` for Season dropdown**
   - What we know: The GET form page must populate a Season dropdown for the preview page (in case admin needs to manually pick). The preview page must also have season dropdowns per tab.
   - What's unclear: Should inactive seasons appear in the dropdown?
   - Recommendation: Use `seasonManagementService.findAll()` (all seasons) so admins can import into archived seasons if needed. [ASSUMED]

3. **Team repository calls in execute() — cache or per-row query?**
   - What we know: Each row needs a `Team` object. `teamRepository.findByShortName()` is called per row in preview(). In execute(), same pattern.
   - What's unclear: At scale (hundreds of rows), N+1 team queries could be slow.
   - Recommendation: For v1.8, per-row queries are acceptable (sheet size is <100 rows/tab per D-08 discretion note). Cache with `Map<String, Team> teamCache` in execute() scope if performance becomes an issue later.

---

## Environment Availability

Step 2.6: SKIPPED — Phase 55 is purely code/config changes. All external dependencies (Google Sheets API, H2/MariaDB) are already available and were verified in Phase 54 (`./mvnw verify` passed with 1042 tests, BUILD SUCCESS).

---

## Sources

### Primary (HIGH confidence)

- `src/main/java/org/ctc/dataimport/CsvImportController.java` — blueprint for controller structure (read in full)
- `src/main/java/org/ctc/dataimport/DriverSheetImportService.java` — Phase 54 shipped API (read in full)
- `src/test/java/org/ctc/dataimport/CsvImportControllerTest.java` — integration test precedent (read in full)
- `src/test/java/org/ctc/dataimport/CsvImportControllerExceptionTest.java` — exception test pattern (read in full)
- `src/main/resources/templates/admin/import-preview.html` — template pattern reference (read in full)
- `src/main/resources/templates/admin/import.html` — form page pattern (read in full)
- `src/main/resources/templates/admin/drivers.html` — entry point for D-18 button (read in full)
- `src/main/java/org/ctc/domain/model/Driver.java` — entity constructor + field defaults (read in full)
- `src/main/java/org/ctc/domain/model/SeasonDriver.java` — entity constructor (read in full)
- `src/main/java/org/ctc/domain/repository/SeasonDriverRepository.java` — available query methods (read in full)
- `.planning/phases/55-admin-import-ui-transactional-execute/55-CONTEXT.md` — locked decisions (read in full)
- `.planning/phases/54-preview-service-row-categorization/54-REVIEW-FIX.md` — field renames IR-02, IR-03 (read in full)
- `.planning/codebase/CONVENTIONS.md` — transaction, logging, DTO patterns (read in full)
- `.planning/codebase/TESTING.md` — integration test pattern, naming conventions (read in full)

### Secondary (MEDIUM confidence)

- `.planning/phases/54-preview-service-row-categorization/54-01-SUMMARY.md` — confirmed shipped contract
- `.planning/phases/54-preview-service-row-categorization/54-CONTEXT.md` — Phase 54 locked decisions D-06 through D-12
- `src/main/java/org/ctc/dataimport/CsvImportService.java` (lines 95-155, 540-605) — `@Transactional` placement pattern, `ImportResult` mutable class shape

---

## Metadata

**Confidence breakdown:**
- Controller structure: HIGH — CsvImportController blueprint read verbatim, mirroring confirmed
- Transactional boundary: HIGH — standard Spring @Transactional, no complexity; existing pattern confirmed
- Execute pseudocode walk: HIGH — derived from locked decisions D-07, D-08, D-12, D-15, D-19 and verified Phase 54 record fields
- Thymeleaf patterns: HIGH — existing project templates read; standard `th:text`/`th:if`/`th:each`/`th:name` syntax
- Test pattern: HIGH — CsvImportControllerTest read verbatim; naming conventions verified from TESTING.md
- Security: MEDIUM — Thymeleaf escaping and Spring Security CSRF integration are standard but not verified against SecurityConfig source in this session

**Research date:** 2026-04-24
**Valid until:** 2026-05-24 (stable Spring Boot 4.x — 30-day window)
