# Season Structured Fields Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add structured fields (year, number, description) to Season, remove UNIQUE on name, replace findByName() with ID-based lookups, and use `getDisplayLabel()` across the Admin-UI.

**Architecture:** Extend the Season entity with three new columns, add a `getDisplayLabel()` convenience method for UI display. Refactor CSV-Import and Matchday JSON-API from season-name to season-ID lookups. Update all admin templates to use `displayLabel` in lists/dropdowns.

**Tech Stack:** Java 25, Spring Boot 4.x, JPA/Hibernate, Flyway (V1), Thymeleaf, JUnit 5/Mockito

**Spec:** `docs/superpowers/specs/2026-03-31-season-structured-fields-design.md`

---

## File Structure

### Modified files:
- `src/main/java/org/ctc/domain/model/Season.java` — New fields + `getDisplayLabel()`
- `src/main/resources/db/migration/V1__initial_schema.sql` — Schema: add columns, remove UNIQUE
- `src/main/java/org/ctc/admin/dto/SeasonForm.java` — New form fields
- `src/main/java/org/ctc/admin/controller/SeasonController.java` — Map new fields
- `src/main/resources/templates/admin/season-form.html` — Year/Number/Description inputs
- `src/main/java/org/ctc/admin/service/AbstractGraphicService.java` — Remove `extractYear()` + `YEAR_PATTERN`
- `src/main/java/org/ctc/admin/service/LineupGraphicService.java` — Use `season.getYear()`
- `src/main/java/org/ctc/admin/service/ResultsGraphicService.java` — Use `season.getYear()`
- `src/main/java/org/ctc/sitegen/SiteGeneratorService.java` — `slugify(season.getDisplayLabel())`
- `src/main/java/org/ctc/dataimport/CsvImportService.java` — `ImportMetadata`: seasonName → seasonId
- `src/main/java/org/ctc/dataimport/CsvImportController.java` — seasonName → seasonId params
- `src/main/java/org/ctc/domain/service/MatchdayService.java` — findByName → findById
- `src/main/java/org/ctc/admin/controller/MatchdayController.java` — seasonName → seasonId params
- `src/main/java/org/ctc/admin/dto/CreateMatchdayRequest.java` — seasonName → seasonId
- `src/main/java/org/ctc/domain/repository/SeasonRepository.java` — Remove findByName
- `src/main/resources/templates/admin/import.html` — Season select: value=id, JS API calls with seasonId
- `src/main/resources/templates/admin/import-preview.html` — hidden field seasonId statt seasonName
- `src/main/resources/templates/admin/seasons.html` — displayLabel
- `src/main/resources/templates/admin/race-form.html` — displayLabel
- `src/main/resources/templates/admin/matchdays.html` — displayLabel
- `src/main/resources/templates/admin/driver-detail.html` — displayLabel
- `src/main/resources/templates/admin/team-detail.html` — displayLabel
- `src/main/resources/templates/admin/race-detail.html` — displayLabel
- `src/main/resources/templates/admin/matchday-form.html` — displayLabel
- `src/main/java/org/ctc/admin/TestDataService.java` — year/number/description for all seasons
- `src/test/java/org/ctc/TestHelper.java` — createSeason mit year/number

### Test files (modified):
- `src/test/java/org/ctc/admin/service/LineupGraphicServiceTest.java` — Remove extractYear tests
- `src/test/java/org/ctc/domain/service/SeasonManagementServiceTest.java` — new Season() Aufrufe
- `src/test/java/org/ctc/domain/service/StandingsServiceTest.java` — new Season() Aufrufe
- `src/test/java/org/ctc/domain/service/MatchServiceTest.java` — new Season() Aufrufe
- `src/test/java/org/ctc/domain/service/MatchdayServiceTest.java` — new Season() + findById statt findByName
- `src/test/java/org/ctc/domain/service/ScoringServiceTest.java` — new Season() Aufrufe
- `src/test/java/org/ctc/domain/service/DriverRankingServiceTest.java` — new Season() Aufrufe
- `src/test/java/org/ctc/domain/service/RaceManagementServiceTest.java` — new Season() Aufrufe
- `src/test/java/org/ctc/domain/service/SwissPairingServiceTest.java` — new Season() Aufrufe
- `src/test/java/org/ctc/domain/service/PlayoffServiceTest.java` — new Season() Aufrufe
- `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java` — new Season() Aufrufe

### New test file:
- `src/test/java/org/ctc/domain/model/SeasonTest.java` — Unit tests for `getDisplayLabel()`

---

### Task 1: Season Entity — Neue Felder + getDisplayLabel()

**Files:**
- Modify: `src/main/java/org/ctc/domain/model/Season.java`
- Modify: `src/main/resources/db/migration/V1__initial_schema.sql`
- Create: `src/test/java/org/ctc/domain/model/SeasonTest.java`

- [ ] **Step 1: Write failing test for getDisplayLabel()**

```java
package org.ctc.domain.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SeasonTest {

    @Test
    void getDisplayLabel_withoutDescription() {
        var season = new Season("CTC Season 4", 2026, 4);
        assertThat(season.getDisplayLabel()).isEqualTo("2026 | #4 | CTC Season 4");
    }

    @Test
    void getDisplayLabel_withDescription() {
        var season = new Season("Season 3", 2025, 3);
        season.setDescription("Group A");
        assertThat(season.getDisplayLabel()).isEqualTo("2025 | #3 | Season 3");
    }

    @Test
    void getDisplayLabel_descriptionNotInLabel() {
        var season = new Season("Regular Season", 2026, 4);
        season.setDescription("Group A");
        // displayLabel zeigt nur year | number | name, nicht description
        assertThat(season.getDisplayLabel()).isEqualTo("2026 | #4 | Regular Season");
    }

    @Test
    void constructor_setsAllFields() {
        var season = new Season("Test", 2025, 3);
        assertThat(season.getName()).isEqualTo("Test");
        assertThat(season.getYear()).isEqualTo(2025);
        assertThat(season.getNumber()).isEqualTo(3);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl . -Dtest=SeasonTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: Compilation error — `Season(String, int, int)` constructor doesn't exist, `getYear()`, `getNumber()`, `getDisplayLabel()` don't exist.

- [ ] **Step 3: Add fields and methods to Season entity**

In `Season.java`, add after the `name` field (line 29):

```java
@Column(nullable = false)
private int year;

@Column(nullable = false)
private int number;

private String description;
```

Add new constructor after the existing `Season(String name)` constructor (line 81):

```java
public Season(String name, int year, int number) {
    this.name = name;
    this.year = year;
    this.number = number;
}
```

Add convenience method after `findSeasonTeam()` (before the closing brace):

```java
public String getDisplayLabel() {
    return year + " | #" + number + " | " + name;
}
```

- [ ] **Step 4: Update V1 schema**

In `V1__initial_schema.sql`, change the `seasons` table definition. After the `name` column, add:

```sql
    year INT NOT NULL,
    number INT NOT NULL,
    description VARCHAR(255),
```

And change `name VARCHAR(255) NOT NULL UNIQUE` to `name VARCHAR(255) NOT NULL` (remove UNIQUE).

- [ ] **Step 5: Remove UNIQUE annotation from Season.name**

In `Season.java`, change:
```java
@Column(nullable = false, unique = true)
private String name;
```
to:
```java
@Column(nullable = false)
private String name;
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./mvnw test -pl . -Dtest=SeasonTest`
Expected: All 4 tests PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/org/ctc/domain/model/Season.java \
       src/main/resources/db/migration/V1__initial_schema.sql \
       src/test/java/org/ctc/domain/model/SeasonTest.java
git commit -m "Season Entity: year, number, description Felder + getDisplayLabel()"
```

---

### Task 2: SeasonForm DTO + SeasonController Mapping

**Files:**
- Modify: `src/main/java/org/ctc/admin/dto/SeasonForm.java`
- Modify: `src/main/java/org/ctc/admin/controller/SeasonController.java`

- [ ] **Step 1: Add fields to SeasonForm**

In `SeasonForm.java`, add after the `name` field:

```java
private int year;

private int number;

private String description;
```

- [ ] **Step 2: Update SeasonController.edit() — add new fields to form mapping**

In `SeasonController.java` method `edit()`, after `form.setName(season.getName());` (line 66), add:

```java
form.setYear(season.getYear());
form.setNumber(season.getNumber());
form.setDescription(season.getDescription());
```

- [ ] **Step 3: Update SeasonController.save() — map new fields for update (existing season)**

In `SeasonController.save()`, after `existing.setName(form.getName());` (line 93), add:

```java
existing.setYear(form.getYear());
existing.setNumber(form.getNumber());
existing.setDescription(form.getDescription());
```

- [ ] **Step 4: Update SeasonController.save() — map new fields for create (new season)**

In `SeasonController.save()`, after `season.setName(form.getName());` (line 107), add:

```java
season.setYear(form.getYear());
season.setNumber(form.getNumber());
season.setDescription(form.getDescription());
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/ctc/admin/dto/SeasonForm.java \
       src/main/java/org/ctc/admin/controller/SeasonController.java
git commit -m "SeasonForm + Controller: year, number, description Mapping"
```

---

### Task 3: Season-Formular Template — neue Eingabefelder

**Files:**
- Modify: `src/main/resources/templates/admin/season-form.html`

- [ ] **Step 1: Add Year, Number, Description fields to the form**

In `season-form.html`, replace the first `form-row` block (lines 10-22) containing name + active with:

```html
<div class="form-row">
    <div class="form-group">
        <label for="name">Name</label>
        <input type="text" id="name" th:field="*{name}" placeholder="e.g. Regular Season" required>
        <span class="field-error" th:if="${#fields.hasErrors('name')}" th:errors="*{name}"></span>
    </div>
    <div class="form-group">
        <div class="form-check" style="margin-top:24px;">
            <input type="checkbox" id="active" th:field="*{active}">
            <label for="active">Active Season</label>
        </div>
    </div>
</div>
<div class="form-row">
    <div class="form-group">
        <label for="year">Year *</label>
        <input type="number" id="year" th:field="*{year}" min="2020" max="2099" required>
    </div>
    <div class="form-group">
        <label for="number">Number *</label>
        <input type="number" id="number" th:field="*{number}" min="1" max="99" required>
    </div>
    <div class="form-group">
        <label for="description">Description</label>
        <input type="text" id="description" th:field="*{description}" placeholder="e.g. Group A">
    </div>
</div>
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/templates/admin/season-form.html
git commit -m "Season-Formular: Year, Number, Description Eingabefelder"
```

---

### Task 4: Admin-Templates — season.name → season.displayLabel in Listen/Dropdowns

**Files:**
- Modify: `src/main/resources/templates/admin/seasons.html:24`
- Modify: `src/main/resources/templates/admin/race-form.html:16`
- Modify: `src/main/resources/templates/admin/matchdays.html:29`
- Modify: `src/main/resources/templates/admin/driver-detail.html:37`
- Modify: `src/main/resources/templates/admin/team-detail.html:62,86`
- Modify: `src/main/resources/templates/admin/race-detail.html:96`
- Modify: `src/main/resources/templates/admin/matchday-form.html:19`

- [ ] **Step 1: Update seasons.html — Übersichtsliste**

In `seasons.html` line 24, change:
```html
<td><a th:href="@{/admin/seasons/{id}(id=${season.id})}" class="detail-link" th:text="${season.name}"></a></td>
```
to:
```html
<td><a th:href="@{/admin/seasons/{id}(id=${season.id})}" class="detail-link" th:text="${season.displayLabel}"></a></td>
```

- [ ] **Step 2: Update race-form.html — Matchday-Dropdown**

In `race-form.html` line 16, change:
```html
th:text="${md.season.name + ' — ' + md.label}"></option>
```
to:
```html
th:text="${md.season.displayLabel + ' — ' + md.label}"></option>
```

- [ ] **Step 3: Update matchdays.html — Season-Spalte**

In `matchdays.html` line 29, change:
```html
<td th:text="${md.season.name}"></td>
```
to:
```html
<td th:text="${md.season.displayLabel}"></td>
```

- [ ] **Step 4: Update driver-detail.html — Season Assignments**

In `driver-detail.html` line 37, change:
```html
<span th:text="${sd.season.name}"></span>
```
to:
```html
<span th:text="${sd.season.displayLabel}"></span>
```

- [ ] **Step 5: Update team-detail.html — Season-Header (two places)**

In `team-detail.html` line 62, change:
```html
<span class="season-header-title" th:text="${group.season.name}"></span>
```
to:
```html
<span class="season-header-title" th:text="${group.season.displayLabel}"></span>
```

In `team-detail.html` line 86, change:
```html
<a th:href="@{/admin/seasons/{id}(id=${s.id})}" th:text="${s.name}" class="season-header-title"></a>
```
to:
```html
<a th:href="@{/admin/seasons/{id}(id=${s.id})}" th:text="${s.displayLabel}" class="season-header-title"></a>
```

- [ ] **Step 6: Update race-detail.html — Season-Link**

In `race-detail.html` line 96, change:
```html
<a th:href="@{/admin/seasons/{id}(id=${race.matchday.season.id})}" class="detail-link" th:text="${race.matchday.season.name}"></a>
```
to:
```html
<a th:href="@{/admin/seasons/{id}(id=${race.matchday.season.id})}" class="detail-link" th:text="${race.matchday.season.displayLabel}"></a>
```

- [ ] **Step 7: Update matchday-form.html — Season-Anzeige**

In `matchday-form.html` line 19, change:
```html
Season: <strong th:text="${matchday.season.name}"></strong>
```
to:
```html
Season: <strong th:text="${matchday.season.displayLabel}"></strong>
```

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/templates/admin/seasons.html \
       src/main/resources/templates/admin/race-form.html \
       src/main/resources/templates/admin/matchdays.html \
       src/main/resources/templates/admin/driver-detail.html \
       src/main/resources/templates/admin/team-detail.html \
       src/main/resources/templates/admin/race-detail.html \
       src/main/resources/templates/admin/matchday-form.html
git commit -m "Admin-Templates: season.displayLabel in Listen und Dropdowns"
```

---

### Task 5: Grafik-Generatoren — season.getYear() statt extractYear()

**Files:**
- Modify: `src/main/java/org/ctc/admin/service/AbstractGraphicService.java`
- Modify: `src/main/java/org/ctc/admin/service/LineupGraphicService.java:68`
- Modify: `src/main/java/org/ctc/admin/service/ResultsGraphicService.java:53`
- Modify: `src/test/java/org/ctc/admin/service/LineupGraphicServiceTest.java`

- [ ] **Step 1: Update LineupGraphicService — use season.getYear()**

In `LineupGraphicService.java` line 68, change:
```java
ctx.setVariable("seasonYear", extractYear(season.getName()));
```
to:
```java
ctx.setVariable("seasonYear", String.valueOf(season.getYear()));
```

- [ ] **Step 2: Update ResultsGraphicService — use season.getYear()**

In `ResultsGraphicService.java` line 53, change:
```java
ctx.setVariable("seasonYear", extractYear(season.getName()));
```
to:
```java
ctx.setVariable("seasonYear", String.valueOf(season.getYear()));
```

- [ ] **Step 3: Remove extractYear() and YEAR_PATTERN from AbstractGraphicService**

In `AbstractGraphicService.java`, remove:
```java
private static final Pattern YEAR_PATTERN = Pattern.compile("(20\\d{2})");
```

And remove the method:
```java
protected String extractYear(String seasonName) {
    if (seasonName == null) return "";
    Matcher m = YEAR_PATTERN.matcher(seasonName);
    return m.find() ? m.group(1) : "";
}
```

Also remove unused imports: `java.util.regex.Matcher` and `java.util.regex.Pattern`.

- [ ] **Step 4: Update LineupGraphicServiceTest — remove extractYear tests**

Remove all `extractYear`-related test methods from `LineupGraphicServiceTest.java`. Search for tests containing `extractYear` and remove them. These tests tested the now-deleted method on AbstractGraphicService.

- [ ] **Step 5: Run affected tests**

Run: `./mvnw test -pl . -Dtest="LineupGraphicServiceTest,ResultsGraphicServiceTest"`
Expected: PASS (or compilation succeeds — these tests may require Playwright which is excluded).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/ctc/admin/service/AbstractGraphicService.java \
       src/main/java/org/ctc/admin/service/LineupGraphicService.java \
       src/main/java/org/ctc/admin/service/ResultsGraphicService.java \
       src/test/java/org/ctc/admin/service/LineupGraphicServiceTest.java
git commit -m "Grafik-Generatoren: season.getYear() statt extractYear() Regex"
```

---

### Task 6: SiteGeneratorService — slugify(season.getDisplayLabel())

**Files:**
- Modify: `src/main/java/org/ctc/sitegen/SiteGeneratorService.java`
- Modify: `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java`

- [ ] **Step 1: Replace all slugify(season.getName()) with slugify(season.getDisplayLabel())**

In `SiteGeneratorService.java`, replace all 6 occurrences of:
```java
slugify(season.getName())
```
with:
```java
slugify(season.getDisplayLabel())
```

These are on lines 114, 125, 142, 165, 192, 211.

- [ ] **Step 2: Update SiteGeneratorServiceTest — Season construction**

In `SiteGeneratorServiceTest.java` line 87, change:
```java
season = new Season("Gen Test " + uniqueSuffix);
```
to:
```java
season = new Season("Gen Test " + uniqueSuffix, 2026, 1);
```

- [ ] **Step 3: Run test**

Run: `./mvnw test -pl . -Dtest=SiteGeneratorServiceTest`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/org/ctc/sitegen/SiteGeneratorService.java \
       src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java
git commit -m "SiteGenerator: slugify(season.getDisplayLabel()) fuer Verzeichnis-Slugs"
```

---

### Task 7: CSV-Import — seasonName → seasonId

**Files:**
- Modify: `src/main/java/org/ctc/dataimport/CsvImportService.java`
- Modify: `src/main/java/org/ctc/dataimport/CsvImportController.java`
- Modify: `src/main/resources/templates/admin/import.html`
- Modify: `src/main/resources/templates/admin/import-preview.html`

- [ ] **Step 1: Update ImportMetadata record — seasonName → seasonId**

In `CsvImportService.java`, replace the `ImportMetadata` record (lines 348-366):

```java
public record ImportMetadata(UUID seasonId, String matchdayLabel, String track, String car,
                                UUID playoffMatchupId, UUID matchdayId) {
    public ImportMetadata(UUID seasonId, String matchdayLabel, String track, String car) {
        this(seasonId, matchdayLabel, track, car, null, null);
    }

    public ImportMetadata(UUID seasonId, String matchdayLabel, String track, String car,
                          UUID playoffMatchupId) {
        this(seasonId, matchdayLabel, track, car, playoffMatchupId, null);
    }

    public boolean isPlayoff() {
        return playoffMatchupId != null;
    }

    public boolean hasMatchdayId() {
        return matchdayId != null;
    }
}
```

- [ ] **Step 2: Update CsvImportService.executeImport() — findById statt findByName**

In `CsvImportService.java` lines 122-124, change:
```java
var season = seasonRepository.findByName(metadata.seasonName()).orElseThrow(
        () -> new IllegalArgumentException("Season not found: " + metadata.seasonName()));
```
to:
```java
var season = seasonRepository.findById(metadata.seasonId()).orElseThrow(
        () -> new IllegalArgumentException("Season not found: " + metadata.seasonId()));
```

- [ ] **Step 3: Update CsvImportService — second findByName occurrence**

Search for the other `findByName` in CsvImportService (around line 373) and change it from `metadata.seasonName()` to use `metadata.seasonId()` with `findById()`.

- [ ] **Step 4: Update PlayoffMatchupDto — seasonName → season displayLabel**

In `CsvImportService.java`, the `PlayoffMatchupDto` record (line 83) uses `seasonName`. Change:
```java
public record PlayoffMatchupDto(UUID id, String seasonName, String roundLabel,
```
to:
```java
public record PlayoffMatchupDto(UUID id, String seasonDisplayLabel, String roundLabel,
```

And update where it's constructed (around line 63):
```java
season.getName()
```
to:
```java
season.getDisplayLabel()
```

- [ ] **Step 5: Update CsvImportController — seasonName → seasonId params**

In `CsvImportController.java`, for all three endpoints (`preview`, `preview-sheet`, `execute`), change:
```java
@RequestParam String seasonName,
```
to:
```java
@RequestParam UUID seasonId,
```

And update the `ImportMetadata` construction in all three methods from:
```java
var metadata = new CsvImportService.ImportMetadata(seasonName, matchdayLabel, null, null, playoffMatchupId, matchdayId);
```
to:
```java
var metadata = new CsvImportService.ImportMetadata(seasonId, matchdayLabel, null, null, playoffMatchupId, matchdayId);
```

- [ ] **Step 6: Update import.html — Season select uses ID + displayLabel**

In `import.html` line 22-25, change the season select:
```html
<select id="seasonName" name="seasonName" required form="importForm">
    <option value="">-- Select season --</option>
    <option th:each="s : ${seasons}" th:value="${s.name}" th:text="${s.name}"></option>
</select>
```
to:
```html
<select id="seasonId" name="seasonId" required form="importForm">
    <option value="">-- Select season --</option>
    <option th:each="s : ${seasons}" th:value="${s.id}" th:text="${s.displayLabel}"></option>
</select>
```

Update the `syncFields` function (line 280) — change `seasonName` to `seasonId` in the fields array:
```javascript
var fields = ['seasonId', 'matchdayId', 'playoffMatchupId'];
```

Update `loadMatchdays()` function (line 153-154) — change:
```javascript
var seasonName = document.getElementById('seasonName').value;
```
to:
```javascript
var seasonId = document.getElementById('seasonId').value;
```

And the fetch URL (line 172):
```javascript
fetch('/admin/matchdays/by-season?seasonId=' + encodeURIComponent(seasonId))
```

Update `createMatchday()` function (line 231) — change:
```javascript
var seasonName = document.getElementById('seasonName').value;
```
to:
```javascript
var seasonId = document.getElementById('seasonId').value;
```

And the fetch body (line 244):
```javascript
body: JSON.stringify({seasonId: seasonId, label: label})
```

- [ ] **Step 7: Update import-preview.html — seasonId hidden field**

In `import-preview.html` line 27, change:
```html
<input type="hidden" name="seasonName" th:value="${metadata.seasonName}">
```
to:
```html
<input type="hidden" name="seasonId" th:value="${metadata.seasonId}">
```

Also line 8, change the display line:
```html
<p><strong>Season:</strong> <span th:text="${metadata.seasonName}"></span> |
```
Here we need to display the season name. Since metadata now has seasonId, we need to either pass the season to the model or resolve it. The simplest approach: add the season to the model in the controller. In `CsvImportController.java`, in both `preview()` and `previewSheet()` methods, add after `model.addAttribute("metadata", metadata);`:
```java
seasonRepository.findById(seasonId).ifPresent(s -> model.addAttribute("seasonDisplayLabel", s.getDisplayLabel()));
```

This requires adding `SeasonRepository` as a dependency to `CsvImportController`. Add it as a field:
```java
private final SeasonRepository seasonRepository;
```

Then in `import-preview.html` line 8, change:
```html
<p><strong>Season:</strong> <span th:text="${metadata.seasonName}"></span> |
```
to:
```html
<p><strong>Season:</strong> <span th:text="${seasonDisplayLabel}"></span> |
```

- [ ] **Step 8: Run compilation check**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 9: Commit**

```bash
git add src/main/java/org/ctc/dataimport/CsvImportService.java \
       src/main/java/org/ctc/dataimport/CsvImportController.java \
       src/main/resources/templates/admin/import.html \
       src/main/resources/templates/admin/import-preview.html
git commit -m "CSV-Import: seasonName durch seasonId ersetzt"
```

---

### Task 8: MatchdayService + Controller — findByName → findById

**Files:**
- Modify: `src/main/java/org/ctc/domain/service/MatchdayService.java`
- Modify: `src/main/java/org/ctc/admin/controller/MatchdayController.java`
- Modify: `src/main/java/org/ctc/admin/dto/CreateMatchdayRequest.java`
- Modify: `src/main/java/org/ctc/domain/repository/SeasonRepository.java`

- [ ] **Step 1: Update MatchdayService.getMatchdaysBySeason() — String → UUID**

In `MatchdayService.java`, change the method (lines 116-122):
```java
public List<MatchdayDto> getMatchdaysBySeason(String seasonName) {
    return seasonRepository.findByName(seasonName)
            .map(season -> matchdayRepository.findBySeasonIdOrderBySortIndexAsc(season.getId()).stream()
                    .map(md -> new MatchdayDto(md.getId(), md.getLabel(), md.getSortIndex()))
                    .toList())
            .orElse(List.of());
}
```
to:
```java
public List<MatchdayDto> getMatchdaysBySeason(UUID seasonId) {
    return matchdayRepository.findBySeasonIdOrderBySortIndexAsc(seasonId).stream()
            .map(md -> new MatchdayDto(md.getId(), md.getLabel(), md.getSortIndex()))
            .toList();
}
```

- [ ] **Step 2: Update MatchdayService.createInline() — String → UUID**

Change the method signature and body (lines 127-130):
```java
public MatchdayDto createInline(String seasonName, String label) {
    var season = seasonRepository.findByName(seasonName)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Season not found: " + seasonName));
```
to:
```java
public MatchdayDto createInline(UUID seasonId, String label) {
    var season = seasonRepository.findById(seasonId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Season not found: " + seasonId));
```

- [ ] **Step 3: Update CreateMatchdayRequest — seasonName → seasonId**

In `CreateMatchdayRequest.java`, change:
```java
public record CreateMatchdayRequest(@NotBlank String seasonName, @NotBlank String label) {
}
```
to:
```java
import jakarta.validation.constraints.NotNull;

public record CreateMatchdayRequest(@NotNull UUID seasonId, @NotBlank String label) {
}
```

Add the UUID import:
```java
import java.util.UUID;
```

- [ ] **Step 4: Update MatchdayController — seasonName → seasonId**

In `MatchdayController.java`, change the `matchdaysBySeason` endpoint (lines 91-95):
```java
@GetMapping("/by-season")
@ResponseBody
public List<MatchdayDto> matchdaysBySeason(@RequestParam String seasonName) {
    return matchdayService.getMatchdaysBySeason(seasonName);
}
```
to:
```java
@GetMapping("/by-season")
@ResponseBody
public List<MatchdayDto> matchdaysBySeason(@RequestParam UUID seasonId) {
    return matchdayService.getMatchdaysBySeason(seasonId);
}
```

And the `createInline` endpoint (lines 97-102):
```java
@PostMapping("/create-inline")
@ResponseBody
public ResponseEntity<MatchdayDto> createInline(@Valid @RequestBody CreateMatchdayRequest request) {
    var dto = matchdayService.createInline(request.seasonName(), request.label());
    return ResponseEntity.status(HttpStatus.CREATED).body(dto);
}
```
to:
```java
@PostMapping("/create-inline")
@ResponseBody
public ResponseEntity<MatchdayDto> createInline(@Valid @RequestBody CreateMatchdayRequest request) {
    var dto = matchdayService.createInline(request.seasonId(), request.label());
    return ResponseEntity.status(HttpStatus.CREATED).body(dto);
}
```

- [ ] **Step 5: Remove findByName from SeasonRepository**

In `SeasonRepository.java`, remove the line:
```java
Optional<Season> findByName(String name);
```

- [ ] **Step 6: Run compilation check**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/org/ctc/domain/service/MatchdayService.java \
       src/main/java/org/ctc/admin/controller/MatchdayController.java \
       src/main/java/org/ctc/admin/dto/CreateMatchdayRequest.java \
       src/main/java/org/ctc/domain/repository/SeasonRepository.java
git commit -m "Matchday API: seasonName durch seasonId ersetzt, findByName entfernt"
```

---

### Task 9: TestDataService + TestHelper — Season-Konstruktor anpassen

**Files:**
- Modify: `src/main/java/org/ctc/admin/TestDataService.java`
- Modify: `src/test/java/org/ctc/TestHelper.java`

- [ ] **Step 1: Update TestDataService.createSeason()**

In `TestDataService.java`, change the `createSeason` method:
```java
private Season createSeason(String name, ScoringDefaults scorings) {
    var season = new Season(name);
    season.setRaceScoring(scorings.raceScoring());
    season.setMatchScoring(scorings.matchScoring());
    return season;
}
```
to:
```java
private Season createSeason(String name, int year, int number, String description, ScoringDefaults scorings) {
    var season = new Season(name, year, number);
    season.setDescription(description);
    season.setRaceScoring(scorings.raceScoring());
    season.setMatchScoring(scorings.matchScoring());
    return season;
}
```

- [ ] **Step 2: Update all createSeason() calls in TestDataService**

Update all calls to use the new signature. Find each `createSeason(` call and add year, number, description parameters:

```java
// Old seasons (line ~136-140)
"Season 1 - 2023 - Group A" → createSeason("Season 1 - 2023 - Group A", 2023, 1, "Group A", scorings)
"Season 1 - 2023 - Group B" → createSeason("Season 1 - 2023 - Group B", 2023, 1, "Group B", scorings)
"Season 2 - 2024"           → createSeason("Season 2 - 2024", 2024, 2, null, scorings)

// Season 3 Group A (line ~143)
"Season 3 - 2025 - Group A" → createSeason("Season 3 - 2025 - Group A", 2025, 3, "Group A", scorings)

// Season 3 Group B (line ~154)
"Season 3 - 2025 - Group B" → createSeason("Season 3 - 2025 - Group B", 2025, 3, "Group B", scorings)

// Season 4 (line ~165)
"Season 4 - 2026" → createSeason("Season 4 - 2026", 2026, 4, null, scorings)

// Test seasons (line ~471, ~506)
"Test-Season 2026" → createSeason("Test-Season 2026", 2026, 99, "Test", scorings)
"Test-Season 2025" → createSeason("Test-Season 2025", 2025, 98, "Test", scorings)
```

Note: For the old seasons created in a loop (lines ~136-140), the loop must be expanded since each season now has different year/number/description values.

- [ ] **Step 3: Update TestHelper.createSeason()**

In `TestHelper.java`, change:
```java
public Season createSeason(String name) {
    var suffix = UUID.randomUUID().toString().substring(0, 4);
    var rs = raceScoringRepository.save(
            new RaceScoring("RS " + suffix, "20,17,14,12,10,8,7,6,5,4,3,2", "3,2,1", 2));
    var ms = matchScoringRepository.save(
            new MatchScoring("MS " + suffix, 3, 1, 0));
    var season = new Season(name);
    season.setRaceScoring(rs);
    season.setMatchScoring(ms);
    return seasonRepository.save(season);
}
```
to:
```java
public Season createSeason(String name) {
    return createSeason(name, 2026, 1);
}

public Season createSeason(String name, int year, int number) {
    var suffix = UUID.randomUUID().toString().substring(0, 4);
    var rs = raceScoringRepository.save(
            new RaceScoring("RS " + suffix, "20,17,14,12,10,8,7,6,5,4,3,2", "3,2,1", 2));
    var ms = matchScoringRepository.save(
            new MatchScoring("MS " + suffix, 3, 1, 0));
    var season = new Season(name, year, number);
    season.setRaceScoring(rs);
    season.setMatchScoring(ms);
    return seasonRepository.save(season);
}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/org/ctc/admin/TestDataService.java \
       src/test/java/org/ctc/TestHelper.java
git commit -m "TestDataService + TestHelper: year/number/description fuer Seasons"
```

---

### Task 10: Test-Klassen — new Season() Aufrufe korrigieren

**Files:**
- Modify: All test files that call `new Season(String)` or `new Season()` — they must compile with the new constructor.

The existing `Season(String name)` constructor still exists, so `new Season("name")` still compiles. However, the new NOT NULL columns `year` and `number` will cause DB constraint violations for tests using `new Season()` (no-arg) or `new Season("name")` without setting year/number.

- [ ] **Step 1: Fix unit tests using new Season() (no-arg constructor)**

These tests use Mockito and don't persist to DB, so the no-arg constructor is fine as long as tests don't rely on `getYear()` or `getNumber()`. Check each test file:

In `MatchdayServiceTest.java`: The tests use `new Season()` — these are unit tests with mocks, no DB. Leave as-is unless they test anything related to year/number.

In `RaceManagementServiceTest.java`: Uses `new Season("Test Season 2026")` and `new Season()`. These are mock-based unit tests. Leave as-is.

In `ScoringServiceTest.java`: Uses `new Season("Test")`. Mock-based. Leave as-is.

- [ ] **Step 2: Fix integration tests using new Season(name)**

Integration tests that persist to DB need year/number set. Update these:

In `StandingsServiceTest.java` line 53:
```java
season = new Season("2026");
```
to:
```java
season = new Season("2026", 2026, 4);
```

In `DriverRankingServiceTest.java` line 38:
```java
season = new Season("2026");
```
to:
```java
season = new Season("2026", 2026, 4);
```

And line 165:
```java
var season2 = new Season("2025");
```
to:
```java
var season2 = new Season("2025", 2025, 3);
```

And line 194:
```java
var season2 = new Season("2025");
```
to:
```java
var season2 = new Season("2025", 2025, 3);
```

In `SwissPairingServiceTest.java` line 41:
```java
season = new Season("Swiss Test " + uniqueSuffix);
```
to:
```java
season = new Season("Swiss Test " + uniqueSuffix, 2026, 1);
```

And line 101:
```java
var leagueSeason = new Season("League Test " + uniqueSuffix);
```
to:
```java
var leagueSeason = new Season("League Test " + uniqueSuffix, 2026, 2);
```

In `PlayoffServiceTest.java` line 53:
```java
season = new Season("Playoff Test " + UUID.randomUUID().toString().substring(0, 8));
```
to:
```java
season = new Season("Playoff Test " + UUID.randomUUID().toString().substring(0, 8), 2026, 1);
```

In `SeasonManagementServiceTest.java` line 156:
```java
var season = new Season(name);
```
to:
```java
var season = new Season(name, 2026, 1);
```

In `MatchServiceTest.java` — uses `new Season("Test Season")` in mock tests, but these are persisted in some integration tests. Change all occurrences:
```java
var season = new Season("Test Season");
```
to:
```java
var season = new Season("Test Season", 2026, 1);
```

- [ ] **Step 3: Fix MatchdayServiceTest — findByName → findById in mocks**

In `MatchdayServiceTest.java`, search for any mocked calls to `findByName` and replace with `findById`. This may involve changing:
```java
when(seasonRepository.findByName(anyString())).thenReturn(Optional.of(season));
```
to:
```java
when(seasonRepository.findById(any(UUID.class))).thenReturn(Optional.of(season));
```

And updating the service method call from `seasonName` to `seasonId`.

- [ ] **Step 4: Run all tests**

Run: `./mvnw verify`
Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/test/
git commit -m "Tests: Season-Konstruktor und findById Anpassungen"
```

---

### Task 11: Verifikation — Gesamtbuild + visuelle Pruefung

- [ ] **Step 1: Run full build**

Run: `./mvnw verify`
Expected: BUILD SUCCESS, all tests green, JaCoCo coverage >= 80%.

- [ ] **Step 2: Start dev server and visually verify**

Run: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`

Use `playwright-cli` to check:
- `http://localhost:9090/admin/seasons` — DisplayLabel in der Liste
- `http://localhost:9090/admin/seasons/{id}/edit` — Year/Number/Description Felder
- `http://localhost:9090/admin/import` — Season-Dropdown mit DisplayLabel + ID als Value
- `http://localhost:9090/admin/matchdays` — Season-Spalte mit DisplayLabel

- [ ] **Step 3: Final commit (if any fixes needed)**

```bash
git add -A
git commit -m "Season strukturierte Felder: Verifikation und Fixes"
```
