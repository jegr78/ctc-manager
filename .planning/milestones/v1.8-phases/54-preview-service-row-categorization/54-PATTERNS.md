# Phase 54 — Pattern Map

**Phase:** 54 — Preview Service & Row Categorization
**Mapped:** 2026-04-24
**Files analyzed:** 3 (2 CREATE, 1 MODIFY)
**Analogs found:** 3 / 3 (100% exact matches)

**Purpose:** Map every file this phase creates/modifies to its closest in-repo analog, with concrete excerpts. Executors read this to avoid re-deriving conventions from scratch. All three new/modified files have an identical twin already in the codebase — Phase 54 is structurally a clone with a different data model.

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `src/main/java/org/ctc/dataimport/DriverSheetImportService.java` (CREATE) | Stateless domain service — external API + repo reads → typed DTO (no writes) | request-response / batch categorization | `src/main/java/org/ctc/dataimport/CsvImportService.java` | exact (same package, same dependencies, same "preview" pattern) |
| `src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java` (CREATE) | Pure unit test — mocks all 5 collaborators, asserts preview output | test | `src/test/java/org/ctc/dataimport/CsvImportServiceTest.java` | exact (same package, same Mockito + AssertJ idiom) |
| `src/main/java/org/ctc/domain/repository/SeasonRepository.java` (MODIFY — add `findByYear(int)`) | Spring Data JPA repository interface — add one derived-query method | request-response | `SeasonRepository.java` itself (existing methods `findByActiveTrue`, `findByYearAndNumber`) | exact (same file, same convention) |

---

## 1. `src/main/java/org/ctc/dataimport/DriverSheetImportService.java` (CREATE)

**Role:** Stateless `@Service` — fetches year-numbered tabs from a Google Sheet via `GoogleSheetsService`, categorizes every data row into one of six buckets (D-12 precedence waterfall), and returns a `DriverSheetImportPreview` record. **No DB writes.**

**Closest analog:** `src/main/java/org/ctc/dataimport/CsvImportService.java`

**Why this analog:**
- Same package (`org.ctc.dataimport`) → identical import conventions.
- Same collaborator set: `DriverMatchingService`, `SeasonRepository`, `SeasonDriverRepository` (new service also adds `GoogleSheetsService` + `TeamRepository`).
- Same "preview" pattern: a public method that returns a typed DTO holding categorized rows for downstream controller consumption (no DB writes at preview time).
- Same Lombok + `@Service` + `@Slf4j` idiom.
- Same inner-record data-model style (see `ImportRow`, `ImportMetadata`, `PlayoffMatchupDto` at lines 523-552).

### Excerpts to mirror

#### Class declaration + annotations + constructor-injected fields
Mirror from `CsvImportService.java:1-35`:

```java
package org.ctc.dataimport;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.exception.ValidationException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.ScoringService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CsvImportService {

    private final DriverMatchingService driverMatchingService;
    private final DriverRepository driverRepository;
    private final SeasonRepository seasonRepository;
    private final SeasonDriverRepository seasonDriverRepository;
    private final MatchdayRepository matchdayRepository;
    // ... (more final deps injected via constructor)
```

**Apply to Phase 54:**
- Same package `org.ctc.dataimport`.
- Same three top annotations: `@Slf4j`, `@Service`, `@RequiredArgsConstructor` (CLAUDE.md Lombok convention + "Keep Controllers Thin / services use constructor injection").
- Five `private final` fields (constructor-injected via `@RequiredArgsConstructor`): `GoogleSheetsService`, `DriverMatchingService`, `SeasonRepository`, `TeamRepository`, `SeasonDriverRepository`.
- No `@Transactional` — preview is read-only (skip the `@Transactional` import that `CsvImportService` uses for `executeImport`).

#### Preview method signature (throws IOException)
Mirror from `CsvImportService.java:75-99`:

```java
public ImportPreview parseAndPreview(InputStream csvStream, ImportMetadata metadata) throws IOException {
    var preview = new ImportPreview(metadata);
    var lines = readCsvLines(csvStream);

    for (int i = 0; i < lines.size(); i++) {
        var fields = lines.get(i);
        if (fields.length < 5) {
            preview.addError("Row " + (i + 2) + ": Too few columns ...");
            continue;
        }

        var teamShortName = fields[0].trim();
        var psnId = fields[1].trim();
        // ...
        var matchResult = driverMatchingService.findDriver(psnId);
        preview.addRow(new ImportRow(teamShortName, psnId, position, qualiPosition, fastestLap, matchResult));
    }

    return preview;
}
```

**Apply to Phase 54:**
- Public method signature: `public DriverSheetImportPreview preview(String sheetUrl) throws IOException` (the `throws IOException` is mandatory — `GoogleSheetsService.readRangeFromSheet` / `getSheetNames` declare it; Phase 55 controller catches and flashes the error).
- Two-pass loop: iterate tabs outer, iterate rows inner. Skip row 0 (header) unconditionally per RESEARCH.md §11.2 — do NOT copy the CSV `firstLine` heuristic at `CsvImportService.java:462-468` (brittle).
- Call `driverMatchingService.findDriver(psnId)` as a black box (MATCH-01). Do not touch `DriverRepository` directly.

#### Header-row skip pattern (DO NOT copy the CSV heuristic)
Contrast — `CsvImportService.java:458-475`:

```java
private List<String[]> readCsvLines(InputStream stream) throws IOException {
    var lines = new ArrayList<String[]>();
    try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
        String line;
        boolean firstLine = true;
        while ((line = reader.readLine()) != null) {
            if (firstLine) {
                firstLine = false;
                if (line.toLowerCase().contains("team") || line.toLowerCase().contains("psn")) {
                    continue; // Skip header
                }
            }
            if (line.isBlank()) continue;
            lines.add(line.split("[,;\\t]", -1));
        }
    }
    return lines;
}
```

**Apply to Phase 54 (deviation):** Phase 54 reads from Google Sheets where row 0 is **guaranteed** to be the header (IMPORT-03). Skip unconditionally using `rows.subList(1, rows.size())` guarded by `rows.size() > 1`. Do not port the content-heuristic from the CSV path — RESEARCH.md §11.2 rejects it as brittle for Sheets.

#### Cell-extraction helper (defensive Object → String)
Mirror from `ScorecardParser.java:178-181`:

```java
private String cellToString(Object cell) {
    if (cell == null) return "";
    return cell.toString();
}
```

**Apply to Phase 54:**
- Copy this helper verbatim into the new service as a `private` method.
- Wrap every `row.get(idx)` with bounds check: `String colA = rows.size() > 0 ? cellToString(row.get(0)) : "";` — handles trailing-empty-cell truncation by the Sheets API (RESEARCH.md §11.3, Q3).
- After extraction: `String normalized = raw.trim();` → `normalized.isBlank()` classifies as `BLANK_PSN_ID` / `BLANK_TEAM_CODE`.

#### Inner record + static-class style for preview data
Mirror from `CsvImportService.java:523-585`:

```java
// Lines 523-528: simple record with a computed getter
public record PlayoffMatchupDto(UUID id, String seasonDisplayLabel, String roundLabel,
                                String team1, String team2) {
    public String displayLabel() {
        return roundLabel + ": " + team1 + " vs " + team2;
    }
}

// Lines 530-548: record with overloaded canonical constructors + boolean helpers
public record ImportMetadata(UUID seasonId, String matchdayLabel, String track, String car,
                             UUID playoffMatchupId, UUID matchdayId) {
    public ImportMetadata(UUID seasonId, String matchdayLabel, String track, String car) {
        this(seasonId, matchdayLabel, track, car, null, null);
    }
    public boolean isPlayoff()     { return playoffMatchupId != null; }
    public boolean hasMatchdayId() { return matchdayId != null; }
}

// Lines 550-552: record with DriverMatchingService.MatchResult as a field
public record ImportRow(String teamShortName, String psnId, int position, int qualiPosition,
                        boolean fastestLap, DriverMatchingService.MatchResult matchResult) {
}

// Lines 554-585: mutable @Getter static inner class (ONLY for progressively-built state)
@Getter
public static class ImportPreview {
    private final ImportMetadata metadata;
    private final List<ImportRow> rows = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    @lombok.Setter
    private boolean duplicateDetected;

    public ImportPreview(ImportMetadata metadata) {
        this.metadata = metadata;
    }
    public void addRow(ImportRow row)   { rows.add(row); }
    public void addError(String error)  { errors.add(error); }
    public boolean hasErrors()          { return !errors.isEmpty(); }
    public boolean hasFuzzyMatches()    { return rows.stream().anyMatch(r -> r.matchResult().needsConfirmation()); }
    public boolean hasNewDrivers()      { return rows.stream().anyMatch(r -> !r.matchResult().isMatch()); }
}
```

**Apply to Phase 54:**
- All seven row types + `TabPreview` + `DriverSheetImportPreview` become **public records** (D-04, D-05) declared as inner types of `DriverSheetImportService` (RESEARCH.md §Q7 confirms inner-records preference — matches `CsvImportService.ImportRow`/`PlayoffMatchupDto` convention).
- `ErrorReason` is a public inner **enum** with a `message()` helper method (see D-09, RESEARCH.md §Q6).
- **Do NOT port the `@Getter` mutable `ImportPreview` class** — Phase 54 uses immutable records (D-04 explicitly forbids the generic catch-all). Build `ArrayList`s **inside the `categorize(tab, year, rows)` helper**, then freeze into the record at the end:
  ```java
  return new TabPreview(tabName, year, suggestedSeasonId, ambiguousReason,
                        List.copyOf(newDrivers), List.copyOf(newAssignments),
                        List.copyOf(conflicts), List.copyOf(fuzzySuggestions),
                        List.copyOf(unchanged), List.copyOf(errors));
  ```
- Exact shape of all 8 inner records + enum is already locked in RESEARCH.md §Q6 / §"Proposed Preview Data Model" (lines 389-470 of RESEARCH.md) — planner/executor copies verbatim from there.

#### Logging pattern
Mirror from `DriverMatchingService.java:32, 39, 46, 55, 61` (debug for calculations) + `CsvImportService.java:187, 250, 301` (info for state/summary):

```java
// DriverMatchingService.java:32
log.debug("Exact match for '{}': {}", searchTerm, exact.get().getPsnId());

// DriverMatchingService.java:55
log.debug("Fuzzy match for '{}': {} (similarity: {})",
        searchTerm, match.driver().getPsnId(), match.similarity());
```

**Apply to Phase 54** (sketch from RESEARCH.md §Q9, lines 532-559):
- `log.info(...)` **at preview entry** — summary of sheet id + tab counts ("Driver sheet preview: sheet {} has {} total tabs, {} match year pattern").
- `log.info(...)` **per tab after categorization** — 6-bucket summary ("Tab {} categorized: {} new, {} assignments, {} conflicts, {} fuzzy, {} unchanged, {} errors").
- `log.debug(...)` **per row** inside the categorize helper with the final bucket decision ("Row {} bucketed as {}: {}").
- Parameterized `{}` only; never `"..." + x`. CLAUDE.md convention.

### Pitfalls / Deviations

- **D-04 deviation (CRITICAL):** `CsvImportService.ImportPreview` is a mutable `@Getter` class with a single `List<ImportRow> rows` field. Phase 54's `TabPreview` MUST use **six typed per-bucket lists** (`newDrivers`, `newAssignments`, `conflicts`, `fuzzySuggestions`, `unchanged`, `errors`) — no generic `Map<Bucket, List<Row>>`, no nullable catch-all fields. Records, not mutable classes. See RESEARCH.md §Q6 for exact field list.
- **Header-row skip:** Use `rows.subList(1, rows.size())` with `rows.size() > 1` guard. Do NOT copy the brittle CSV heuristic (`line.toLowerCase().contains("team")`) — `CsvImportService:462-468` uses it for CSV where row count is unknown; Phase 54's Sheets structure is fixed (IMPORT-03). RESEARCH.md §11.2.
- **Short-row defensive read (IMPORT-03):** Google Sheets API truncates trailing empty cells. A row with only column A populated returns `List.of(psn)` (size 1) — `row.get(2)` throws `IndexOutOfBoundsException`. Always: `String colC = row.size() > 2 ? cellToString(row.get(2)) : "";`. See RESEARCH.md §11.3 and Q3.
- **D-12 precedence order (CRITICAL):** The 7-step waterfall is first-match-wins. A row with BOTH blank PSN AND unknown team surfaces as `BLANK_PSN_ID` only (step 1 short-circuits). Tests must explicitly verify the ordering — scenario #10 (`givenDuplicatePsnInTab...`) specifically exercises step-4 precedence.
- **D-07 naive bucketing:** Same PSN appearing in three tabs → three independent rows across three `TabPreview`s. Do NOT try to dedup at preview time — Phase 55 executes dedup on commit. The categorizer's "seen PSNs" set (D-11 duplicate check) is **per tab**, reset between tabs.
- **`findByShortName(String)` is case-sensitive** (TeamRepository.java:11). Phase 54 uses strict matching per RESEARCH.md §11.4. Trim first (`teamCode.trim()`) but do not lowercase. Unknown uppercase codes → `ERROR/UNKNOWN_TEAM_CODE`.
- **`@Transactional` NOT required:** Preview is read-only; no writes. Drop the `import org.springframework.transaction.annotation.Transactional` line entirely.
- **OSIV note:** CsvImportService relies on OSIV when lazy-loading `SeasonDriver.getTeam()` for conflict detection (`SeasonDriverRepository.findBySeasonIdAndDriverId` has no `@EntityGraph`, RESEARCH.md §Q1). Same applies to Phase 54 — no `@EntityGraph` annotation needed, lazy-load inside the HTTP request.
- **Inner-records placement:** Keep all 8 inner types (7 records + 1 enum) inside `DriverSheetImportService` (see `CsvImportService.ImportRow`/`PlayoffMatchupDto` precedent at lines 523-552). Do NOT create 7 top-level files in `org.ctc.dataimport`. RESEARCH.md §Q7.
- **Bucket order within a list is unspecified:** Tests should assert `.containsExactlyInAnyOrder(...)` or `.contains(...)`, never `.containsExactly(...)`. RESEARCH.md §"Validation That Is Impossible".

---

## 2. `src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java` (CREATE)

**Role:** Pure unit test class — `@ExtendWith(MockitoExtension.class)` + `@Mock` for all 5 collaborators + `@InjectMocks` for the service. Contains ≥12 given-when-then scenarios (target: 15 per RESEARCH.md §Q10). No Spring context, no DB.

**Closest analog:** `src/test/java/org/ctc/dataimport/CsvImportServiceTest.java`

**Why this analog:** Same package, same test framework, same `@Mock`/`@InjectMocks` pattern, same AssertJ idiom, tests the exact same `driverMatchingService.findDriver(...)` + `seasonDriverRepository.findBySeasonIdAndDriverId(...)` stubbing surface.

### Excerpts to mirror

#### Class annotations + imports + mock fields
Mirror from `CsvImportServiceTest.java:1-52`:

```java
package org.ctc.dataimport;

import org.ctc.domain.exception.ValidationException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.ScoringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CsvImportServiceTest {

    @Mock
    private DriverMatchingService driverMatchingService;
    @Mock
    private DriverRepository driverRepository;
    @Mock
    private SeasonRepository seasonRepository;
    @Mock
    private SeasonDriverRepository seasonDriverRepository;
    // ... more @Mock fields

    @InjectMocks
    private CsvImportService csvImportService;
```

**Apply to Phase 54:**
- Copy header imports verbatim (drop `ScoringService`, `ArgumentCaptor` — not needed).
- `@ExtendWith(MockitoExtension.class)` at class level.
- Five `@Mock` fields: `GoogleSheetsService googleSheetsService`, `DriverMatchingService driverMatchingService`, `SeasonRepository seasonRepository`, `TeamRepository teamRepository`, `SeasonDriverRepository seasonDriverRepository`.
- One `@InjectMocks private DriverSheetImportService service;`.
- AssertJ static imports (`assertThat`, `assertThatThrownBy`) — NOT JUnit 5 `assertEquals`. RESEARCH.md §Q5 confirms CsvImportServiceTest is the canonical AssertJ style; DriverMatchingServiceTest uses older JUnit `assertEquals` — do not port that.

#### `@BeforeEach` fixture setup (reusable test data)
Mirror from `CsvImportServiceTest.java:54-100`:

```java
private Season season;
private Matchday matchday;
private Team subTeam1;
// ...
private Driver driver1;
private Driver driver2;

@BeforeEach
void setUp() {
    season = new Season();
    season.setId(UUID.randomUUID());
    season.setName("Season 1");
    var raceScoring = new RaceScoring();
    season.setRaceScoring(raceScoring);

    var parentTeam = new Team("Alpha Racing", "AHR");
    parentTeam.setId(UUID.randomUUID());

    standaloneTeam1 = new Team("Bravo Racing", "BRV");
    standaloneTeam1.setId(UUID.randomUUID());

    driver1 = new Driver("driver1_psn", "Driver One");
    driver1.setId(UUID.randomUUID());
    // ...
}
```

**Apply to Phase 54** (VALIDATION.md Wave 0 row 2):
- Two `Season`s: `season2024` (year=2024), `season2023` (year=2023). Populate `name` + `year` + `setId(UUID.randomUUID())`.
- Two `Team`s: `teamAhr` (shortName "AHR"), `teamCrl` (shortName "CRL") with ids.
- One `Driver`: `existingDriver` (psnId "driver1_psn") with id.
- One existing `SeasonDriver`: `new SeasonDriver(season2024, existingDriver, teamAhr)` — reused across CONFLICT / UNCHANGED scenarios by stubbing the repo to return `Optional.of(existingSd)`.
- Do NOT set up a `Matchday` / playoff / scoring — Phase 54 doesn't need them.

#### Canonical test pattern — stub findDriver, call preview, assert bucket contents
Mirror from `CsvImportServiceTest.java:364-382`:

```java
@Test
void givenValidCsv_whenParseAndPreview_thenReturnsRows() throws Exception {
    // given
    when(driverMatchingService.findDriver("drv1"))
            .thenReturn(DriverMatchingService.MatchResult.exact("drv1", driver1));
    when(driverMatchingService.findDriver("drv2"))
            .thenReturn(DriverMatchingService.MatchResult.exact("drv2", driver2));

    var csvContent = "Team,PSN ID,Position,Quali,FL\nBRV,drv1,1,1,true\nCRL,drv2,2,2,false\n";
    var stream = new java.io.ByteArrayInputStream(csvContent.getBytes());
    var metadata = new CsvImportService.ImportMetadata(season.getId(), "MD1", null, null);

    // when
    var preview = csvImportService.parseAndPreview(stream, metadata);

    // then
    assertThat(preview.getRows()).hasSize(2);
    assertThat(preview.hasErrors()).isFalse();
}
```

**Apply to Phase 54:**
- Method naming: `givenX_whenPreview_thenY()` — exact scenario names are enumerated in CONTEXT.md §specifics (lines 251-262) and VALIDATION.md rows 54-01-02..13.
- Body layout: `// given` block (mock stubs) → `// when` block (single `service.preview(url)` call) → `// then` block (AssertJ assertions on the returned `DriverSheetImportPreview`).
- AssertJ patterns to reach for: `.hasSize(N)`, `.extracting(NewDriverRow::psnId).containsExactlyInAnyOrder(...)`, `.isEmpty()`, `.first().satisfies(row -> { ... })`, `.singleElement()`.

#### SeasonDriver CONFLICT/UNCHANGED stub pattern
Mirror from `CsvImportServiceTest.java:931-951`:

```java
@Test
void givenExistingSeasonDriverWithDifferentTeam_whenEnsureSeasonDriver_thenUpdatesTeam() {
    setupCommonMocks();

    var existingSeasonDriver = new SeasonDriver(season, driver1, standaloneTeam1);
    when(seasonDriverRepository.findBySeasonIdAndDriverId(season.getId(), driver1.getId()))
            .thenReturn(Optional.of(existingSeasonDriver));
    // ...
}
```

**Apply to Phase 54** (VALIDATION.md rows 54-01-08, 54-01-10):
- **UNCHANGED scenario:** stub returns `Optional.of(new SeasonDriver(season2024, existingDriver, teamAhr))` where sheet team == `teamAhr`.
- **CONFLICT scenario:** stub returns `Optional.of(new SeasonDriver(season2024, existingDriver, teamAhr))` where sheet team == `teamCrl` (different).
- **NEW_ASSIGNMENT scenario:** stub returns `Optional.empty()`.
- Always stub by id: `seasonDriverRepository.findBySeasonIdAndDriverId(season2024.getId(), existingDriver.getId())`.

#### MATCH-01 mock verification (Mockito `verify`)
Mirror from `CsvImportServiceTest.java:631-633`:

```java
ArgumentCaptor<Match> matchCaptor = ArgumentCaptor.forClass(Match.class);
verify(matchRepository).save(matchCaptor.capture());
```

**Apply to Phase 54** (VALIDATION.md row 54-01-12 MATCH-01):
- In the case-insensitive test, additionally call `verify(driverMatchingService).findDriver("DRIVER1_PSN");` to assert Phase 54 delegates (not re-implements) the 4-stage matching logic. The `verify` plus stubbed `MatchResult.exact(...)` return demonstrates black-box reuse.

#### Recommended mock-setup helper
Per VALIDATION.md Wave 0 row 3 — Claude's discretion, recommended for readability:

```java
private void setupSheetsStub(String url, Map<String, List<List<Object>>> tabsToRows) throws IOException {
    when(googleSheetsService.extractSpreadsheetId(url)).thenReturn("spreadsheet-id");
    when(googleSheetsService.getSheetNames("spreadsheet-id"))
            .thenReturn(new ArrayList<>(tabsToRows.keySet()));
    for (var entry : tabsToRows.entrySet()) {
        when(googleSheetsService.readRangeFromSheet("spreadsheet-id", entry.getKey(), "A:C"))
                .thenReturn(entry.getValue());
    }
}
```

- Reduces per-scenario boilerplate from ~8 `when(...)` lines to one call.
- Row construction helper (also recommended): `private static List<Object> row(String... cells) { return List.of((Object[]) cells); }` — short rows via fewer arguments.

### Pitfalls / Deviations

- **Prefer AssertJ over JUnit 5 `assertEquals`:** `DriverMatchingServiceTest` in the same package uses `assertEquals` (older style) — do NOT copy that. `CsvImportServiceTest` is the canonical AssertJ reference (RESEARCH.md §Q5). CLAUDE.md does not mandate either, but AssertJ's fluent assertions are required for the bucket-contents checks (`.extracting(...)...containsExactlyInAnyOrder(...)`).
- **Verbose @BeforeEach fixture risk:** `CsvImportServiceTest.setUp()` builds 10+ entities (2 sub-teams, 2 standalone teams, parent, drivers, matchday, scoring). Phase 54 needs **only** 2 seasons + 2 teams + 1 driver + 1 existing SeasonDriver — strictly keep the fixture minimal to avoid the readability issue that `CsvImportServiceTest` has accumulated.
- **Do NOT reuse `setupCommonMocks()`:** `CsvImportServiceTest.setupCommonMocks()` (lines 102-117) stubs `matchRepository`, `raceRepository`, `matchdayRepository` — none exist in Phase 54's mock set. Ignore that helper entirely; use the recommended `setupSheetsStub(...)` helper instead.
- **Short-row test case (IMPORT-03 defensive read):** VALIDATION.md row 54-01-03 explicitly calls out `givenRowShorterThanThreeColumns_whenPreview_thenTreatedAsBlankTeamCode`. Build the stubbed cell list as `List.of("some_psn")` (size 1) — the test proves the service doesn't throw `IndexOutOfBoundsException`.
- **Google Sheets IOException propagation test:** Optional scenario 17 from RESEARCH.md §Q10. If included, use `assertThatThrownBy(() -> service.preview(url)).isInstanceOf(IOException.class)` — requires `when(googleSheetsService.readRangeFromSheet(...)).thenThrow(new IOException("api error"))`.
- **Intra-bucket order is unspecified (D-07):** When asserting bucket contents, use `.containsExactlyInAnyOrder(...)` for unordered matches. Reserve `.containsExactly(...)` only for `preview.tabPreviews()` top-level (D-05 locks ascending-year order).
- **MockitoExtension strict-stubbing:** By default `MockitoExtension` uses STRICT_STUBS — unused stubs fail the test. For the optional lenient stubs (e.g. `driverMatchingService.findDriver(...)` called only when blank-PSN check doesn't short-circuit), wrap with `lenient()` — see `CsvImportServiceTest.java:105, 106, 111` precedent: `lenient().when(...).thenReturn(...)`.
- **Test count target:** VALIDATION.md Per-Task Verification Map lists 13 automated tests (54-01-01 through 54-01-13). SC#6 requires ≥9. Aim for 12-15 `@Test` methods (RESEARCH.md §Q10 lists 19 candidates — the planner should pick 12-15 to keep the file readable while covering all 17 requirements).

---

## 3. `src/main/java/org/ctc/domain/repository/SeasonRepository.java` (MODIFY — add `findByYear(int)`)

**Role:** Spring Data JPA repository interface — add one derived-query finder method. Zero-body (Spring Data autogenerates from the method name per D-01).

**Closest analog:** `SeasonRepository.java` itself — existing methods `findByActiveTrue()`, `findBySeasonTeamsTeamId(UUID)`, `findByYearAndNumber(int, int)`.

### Excerpts to mirror

Mirror from `SeasonRepository.java:1-20` (entire existing file):

```java
package org.ctc.domain.repository;

import org.ctc.domain.model.Season;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeasonRepository extends JpaRepository<Season, UUID> {

    Optional<Season> findByActiveTrue();

    @EntityGraph(attributePaths = {"raceScoring", "matchScoring"})
    List<Season> findBySeasonTeamsTeamId(UUID teamId);

    @EntityGraph(attributePaths = {"raceScoring", "matchScoring"})
    List<Season> findByYearAndNumber(int year, int number);
}
```

**Apply to Phase 54:**
- Add a single new method — zero-body derived query. Place it next to `findByYearAndNumber` (same `year` grouping):
  ```java
  List<Season> findByYear(int year);
  ```
- No `@Query` annotation (Spring Data autogenerates SQL from the name).
- No `@EntityGraph` annotation — the preview service uses only `season.getId()`, no lazy-loaded associations (RESEARCH.md §11.4).
- No new imports required — `List`, `Season`, `JpaRepository` already imported.

### Pitfalls / Deviations

- **Return type MUST be `List<Season>`, NOT `Optional<Season>`:** D-02 explicitly branches on "0 / 1 / ≥2" candidate counts. `Optional` would lose the multi-result case. RESEARCH.md §user_constraints D-02.
- **Do NOT add `@EntityGraph`:** Only `season.getId()` is used at preview time (for `suggestedSeasonId` + `findBySeasonIdAndDriverId` lookups). Adding an `@EntityGraph` would pull in `raceScoring`/`matchScoring` unnecessarily. Contrast with `findBySeasonTeamsTeamId` which has `@EntityGraph` because callers iterate scoring fields.
- **Do NOT use `findByName` or `findByDisplayLabel`:** ROADMAP SC#3's reference to these is **structurally wrong** (D-13) — `Season.name` is free-text ("CTC Season 1"), `Season.displayLabel` is a computed getter (not a persisted column). JPA cannot derive a finder from a transient getter. CONTEXT.md D-01 supersedes ROADMAP SC#3.
- **Case + naming:** Spring Data derives `findByYear` → `SELECT * FROM seasons WHERE year = ?` automatically. Method name must match the entity field exactly (`Season.year`, `int` primitive — not `Integer`). Using `findByYear(Integer)` would still work but deviates from `findByYearAndNumber(int, int)` precedent at line 19.

---

## Shared / Cross-Cutting Patterns

### Service class skeleton (CLAUDE.md architectural convention)
**Source:** `CsvImportService.java:20-35`, `DriverMatchingService.java:14-22`
**Apply to:** `DriverSheetImportService`
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class <Name>Service {
    private final <Dep1> dep1;
    private final <Dep2> dep2;
    // ... all final fields → constructor-injected by Lombok
}
```

### Test class skeleton (CLAUDE.md BDD convention)
**Source:** `CsvImportServiceTest.java:25-52`
**Apply to:** `DriverSheetImportServiceTest`
```java
@ExtendWith(MockitoExtension.class)
class <Name>ServiceTest {
    @Mock private <Dep1> dep1;
    @Mock private <Dep2> dep2;
    @InjectMocks private <Name>Service service;

    @BeforeEach
    void setUp() { /* fixtures */ }

    @Test
    void givenContext_whenAction_thenExpectedResult() {
        // given
        // when
        // then
    }
}
```

### Logging (CLAUDE.md §Logging)
**Source:** `DriverMatchingService.java:32-62`, `CsvImportService.java:187-301`
**Apply to:** `DriverSheetImportService`
- `log.info(...)` for state-change summaries (preview entry, per-tab summary).
- `log.debug(...)` for per-row categorization decisions.
- Parameterized `{}` only. Never `"..." + value`.

### AssertJ over JUnit asserts
**Source:** `CsvImportServiceTest.java:19-20, 380-381`
**Apply to:** `DriverSheetImportServiceTest`
- `import static org.assertj.core.api.Assertions.assertThat;`
- `assertThat(collection).hasSize(N).extracting(...).containsExactlyInAnyOrder(...);`
- `assertThat(record.field()).isEqualTo(expected);`
- For exception tests: `assertThatThrownBy(() -> service.preview(url)).isInstanceOf(IOException.class);`

### Cell extraction (defensive Object → String)
**Source:** `ScorecardParser.java:178-181`
**Apply to:** `DriverSheetImportService` (add as a `private` method)
```java
private String cellToString(Object cell) {
    if (cell == null) return "";
    return cell.toString();
}
```

---

## No Analog Required

All three files have strong in-repo analogs. No Phase 54 file lacks a match. No fallback to generic RESEARCH.md patterns is needed.

---

## Summary Table

| File | Action | Closest Analog | Lines (estimate) |
|------|--------|----------------|------------------|
| `src/main/java/org/ctc/dataimport/DriverSheetImportService.java` | CREATE | `src/main/java/org/ctc/dataimport/CsvImportService.java` (lines 1-35 for skeleton; 523-585 for inner records; 75-99 for preview method; 458-475 contrast for header skip) | ~180-260 |
| `src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java` | CREATE | `src/test/java/org/ctc/dataimport/CsvImportServiceTest.java` (lines 1-100 for skeleton + fixture; 364-382 for canonical test pattern; 931-951 for SeasonDriver stub) | ~350-500 |
| `src/main/java/org/ctc/domain/repository/SeasonRepository.java` | MODIFY (+1 method) | SeasonRepository.java itself (existing `findByYearAndNumber(int, int)` at line 19) | +1-2 |

---

## Metadata

- **Analog search scope:** `src/main/java/org/ctc/dataimport/` (6 files), `src/test/java/org/ctc/dataimport/` (test files), `src/main/java/org/ctc/domain/repository/SeasonRepository.java`
- **Files read for excerpt extraction:** 6 (CsvImportService.java, CsvImportServiceTest.java, SeasonRepository.java, GoogleSheetsService.java, DriverMatchingService.java, ScorecardParser.java)
- **Pattern extraction date:** 2026-04-24
- **Confidence:** HIGH — all three new/modified files have exact-match analogs in the same package.
