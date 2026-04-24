# Phase 54: Preview Service & Row Categorization — Research

**Phase:** 54 — Preview Service & Row Categorization
**Researched:** 2026-04-24
**Domain:** Backend service (dataimport package) — Google Sheets ingestion + row bucketing
**Confidence:** HIGH (all findings verified by reading the cited files; no external-library uncertainty)
**Status:** Ready for planning

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01** New repository method `SeasonRepository.findByYear(int year)` replaces the ROADMAP SC#3 reference to `findByName`/`findByDisplayLabel` (neither fits: `Season.name` is free-text, `Season.displayLabel` is a computed getter). Tab-name parsing: `Integer.parseInt(tabName)` on a `^\d{4}$` match.
- **D-02** Auto-select rule: `findByYear(y)` returns a singleton → that Season's id becomes `suggestedSeasonId`. Returns `0` or `≥2` → `suggestedSeasonId = null`.
- **D-03** `TabPreview` carries an optional `ambiguousReason` string field. Populated when `suggestedSeasonId == null`: `"Multiple seasons for year 2024"` or `"No season found for year 2024"`. Phase 55 renders it next to the empty dropdown.
- **D-04** `TabPreview` uses **typed per-bucket fields** (six typed record lists) instead of a generic `Map<Bucket, List<Row>>`. Each row record carries only the fields that bucket needs. No nullable catch-all fields.
- **D-05** `DriverSheetImportPreview` is `record DriverSheetImportPreview(List<TabPreview> tabPreviews)` — tabs pre-sorted ascending by year (4-digit numeric string comparison == ascending year).
- **D-06** **No `@SessionAttributes`.** Phase 55 execute re-fetches the sheet and re-runs preview categorization server-side, then applies the user's per-row form decisions. Mirrors `CsvImportController.execute()` pattern exactly (re-fetch + `Map<String,String> allParams` + `confirm_<psnId>` convention). Phase 54's preview service must therefore be **idempotent and cheap to call twice**.
- **D-07** **Naive bucketing + execute-side dedup.** Same new PSN in three tabs produces three independent `NewDriverRow` entries across three `TabPreview`s. Phase 55 `execute(...)` deduplicates by `psnId` on commit.
- **D-08** User decisions on fuzzy rows are applied **per row, independent across tabs.** Two Drivers may end up being created for the same PSN if the admin accepts fuzzy in 2023 but rejects in 2024. Intentional UX tradeoff.
- **D-09** `ErrorRow.reason` is a typed enum `ErrorReason` with a `message()` helper returning a hard-coded English string (no i18n; UI is English-only per CLAUDE.md).
- **D-10** `ErrorReason` values for Phase 54: `BLANK_PSN_ID`, `BLANK_TEAM_CODE`, `UNKNOWN_TEAM_CODE`, `DUPLICATE_IN_TAB`.
- **D-11** Duplicate handling: **first occurrence wins.** First row with a given PSN in a tab is categorized normally; subsequent rows land in ERROR/`DUPLICATE_IN_TAB` and carry their own `rawTeamCode`.
- **D-12** Row bucketing precedence (first match wins):
  1. Blank PSN → `ERROR/BLANK_PSN_ID`
  2. Blank team code → `ERROR/BLANK_TEAM_CODE`
  3. Unknown team short code → `ERROR/UNKNOWN_TEAM_CODE`
  4. PSN already seen in this tab → `ERROR/DUPLICATE_IN_TAB`
  5. `DriverMatchingService.findDriver(psn)` returns `MatchType.FUZZY` → `FUZZY_SUGGESTION`
  6. Match type is `EXACT`: look up existing `SeasonDriver(suggestedSeasonId, matchedDriver)` — Not found or `suggestedSeasonId == null` → `NEW_ASSIGNMENT`; Found with same team → `UNCHANGED`; Found with different team → `CONFLICT`
  7. Match type is `NONE` → `NEW_DRIVER`
- **D-13** ROADMAP Success Criterion #3 is structurally wrong — CONTEXT.md is authoritative. Use `findByYear(int)` + uniqueness instead. Verifier must accept this as satisfying SC#3.

### Claude's Discretion

- Internal data structure for "seen PSNs within a tab" during duplicate check (Set vs Map — trivial).
- Exact class naming for row records (e.g. `NewDriverRow` vs `NewDriverEntry`) as long as `TabPreview`'s field names read clearly in Thymeleaf.
- Whether to front-load all team / season lookups into a per-tab cache or query repeatedly (researcher/planner may decide based on typical sheet size <100 rows/tab).
- Test utility helpers / builders to keep the 9+ given-when-then scenarios readable.

### Deferred Ideas (OUT OF SCOPE)

- Cross-tab fuzzy-decision propagation UX (master checkbox)
- i18n of error messages
- Configurable fuzzy threshold
- Post-phase ROADMAP SC#3 text correction (tracked separately)
- **Phase 55 pieces** (see "Out of Scope for Phase 54" section below)
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| IMPORT-02 | Preview auto-detects tabs matching `^\d{4}$` and ignores the rest | `GoogleSheetsService.getSheetNames()` returns all tab names unfiltered — preview service filters via `Pattern.compile("^\\d{4}$").asMatchPredicate()` |
| IMPORT-03 | Preview reads columns A (PSN ID) and C (Team short code), skipping header row | `GoogleSheetsService.readRangeFromSheet(id, tab, "A:C")` returns `List<List<Object>>`; service skips row index 0 (header convention matches ScorecardParser) |
| IMPORT-04 | Each detected tab renders as its own preview section, sorted ascending by year | `DriverSheetImportPreview.tabPreviews` is pre-sorted lex-ascending (4-digit strings sort == year) (D-05) |
| IMPORT-05 | Each tab preview shows a Season dropdown pre-selected to the matching Season | `suggestedSeasonId` populated from `SeasonRepository.findByYear(int)` singleton; null + `ambiguousReason` on 0 or ≥2 hits (D-01..D-03). Admin override happens in Phase 55. |
| UX-01 | `New Drivers` bucket | `TabPreview.newDrivers` populated when `MatchType.NONE` (D-12 step 7) |
| UX-02 | `New Assignments` bucket | `TabPreview.newAssignments` populated when EXACT match + no existing `SeasonDriver` or `suggestedSeasonId == null` (D-12 step 6) |
| UX-03 | `Conflicts` bucket | `TabPreview.conflicts` populated when EXACT match + existing `SeasonDriver` with different team (D-12 step 6) |
| UX-04 | `Fuzzy Match Suggestions` bucket (Levenshtein ≥0.8) | `TabPreview.fuzzySuggestions` populated when `MatchType.FUZZY` — threshold handled inside `DriverMatchingService` (constant `FUZZY_THRESHOLD = 0.8` at line 19) |
| UX-05 | `Unchanged` bucket | `TabPreview.unchanged` populated when EXACT match + existing `SeasonDriver` with same team (D-12 step 6) |
| UX-06 | `Errors` bucket (blank PSN / unknown team) + excluded from execute | `TabPreview.errors` with `ErrorReason` enum (D-09, D-10); Phase 55 execute ignores this bucket |
| MATCH-01 | Reuse existing `DriverMatchingService` 4-stage logic | `DriverMatchingService.findDriver(psnId)` used as black box (see Q2) |
| MATCH-02 | Same PSN in multiple tabs creates Driver exactly once | Preview stage: naive per-tab bucketing (D-07). Phase 55 execute dedups by psnId on commit. |
| DATA-01 | Missing Season reported as row error, no auto-create | `suggestedSeasonId == null` triggers `ambiguousReason`; Phase 55 rejects execute if admin doesn't pick a season. Phase 54 contribution: make the ambiguity visible. |
| DATA-02 | Unknown team short code reported as row error; no `Team` auto-create | `ErrorReason.UNKNOWN_TEAM_CODE` via `TeamRepository.findByShortName(...)` (D-10, D-12 step 3) |
| DATA-04 | No Flyway migration introduced | Confirmed — preview is read-only, no schema change |
| DATA-05 | `RaceLineup` records never modified | Confirmed — preview never touches `RaceLineupRepository` |
| TEST-01 | ≥9 given-when-then scenarios | Twelve scenarios enumerated in §Test Strategy (below), each covers ≥1 decision D-07..D-12 |
</phase_requirements>

## Research Summary

- **`findBySeasonAndDriver` question is answered:** `SeasonDriverRepository.findBySeasonIdAndDriverId(UUID seasonId, UUID driverId)` already exists at `src/main/java/org/ctc/domain/repository/SeasonDriverRepository.java:19`, returns `Optional<SeasonDriver>`, no annotations. **No new repo method is needed for SeasonDriver lookup.** Only `SeasonRepository.findByYear(int)` is new.
- **`DriverMatchingService.MatchResult` is a rich `record`** (line 97) with fields `(searchTerm, driver, MatchType type, double similarity)`. FUZZY carries the candidate `Driver` + similarity in `[0.8, 1.0]`. EXACT returns similarity `1.0`. NONE returns null driver + similarity `0.0`. The service already includes all 4 spec stages; Phase 54 never calls the repository directly for matching.
- **`GoogleSheetsService.readRangeFromSheet` returns `List<List<Object>>`** (line 84). Empty sheet → `List.of()`. Cells arrive as `Object` (Sheets API returns `String` for text cells, `BigDecimal` for numbers, `Boolean` for checkboxes). Trailing empty cells in a row are **truncated** by Sheets API — a row with only col A populated returns a size-1 list. Header row is **not** separately handled by the API — must skip row index 0 in the service (consistent with `readCsvLines`'s `firstLine` skip at `CsvImportService.java:462-468` and with how `ScorecardParser` ignores cells above the first header row).
- **`CsvImportService.ImportPreview` is a `@Getter` static inner class** (line 554-585) with `ArrayList` fields, `List<String> errors`, and convenience getters like `hasErrors()`, `hasFuzzyMatches()`. Phase 54 mirrors this style but with **records** for immutability and **typed per-bucket lists** (D-04) — a cleaner evolution, not a reuse.
- **Test conventions are uniform and simple:** `@ExtendWith(MockitoExtension.class)`, `@Mock` for all repos and `DriverMatchingService`, `@InjectMocks` for the service under test, AssertJ fluent assertions (`assertThat(...).isEqualTo(...)`), given-when-then comments + method names. No integration-test scaffolding in Phase 54 — purely unit tests. Phase 55 owns the integration test.
- **Plan primary recommendation:** split Phase 54 into 3 atomic tasks: (1) add `SeasonRepository.findByYear(int)` + preview data model records, (2) implement `DriverSheetImportService.preview(...)` with the D-12 precedence waterfall, (3) write 12 given-when-then unit tests. Each task independently commits; together they hit ≥82% JaCoCo and satisfy all 17 Phase 54 requirements.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Google Sheets HTTP I/O | Reuse (`GoogleSheetsService` — infrastructure) | — | Already encapsulated; Phase 54 treats it as a dependency. |
| Driver matching (4-stage) | Reuse (`DriverMatchingService` — domain service) | — | MATCH-01 locks this; no changes. |
| Season-by-year resolution | Domain repository (`SeasonRepository.findByYear`) | — | Derived finder; Spring Data autogenerates from method name. |
| Team-by-short-code resolution | Reuse (`TeamRepository.findByShortName`) | — | Existing finder, case-sensitive. |
| SeasonDriver conflict detection | Reuse (`SeasonDriverRepository.findBySeasonIdAndDriverId`) | — | Existing finder, returns `Optional<SeasonDriver>`. |
| Row bucketing business logic | **New: `DriverSheetImportService`** (`org.ctc.dataimport`) | Private helpers | D-12 waterfall lives here. |
| Preview data model (records) | **New: inner records of `DriverSheetImportService`** | — | Mirrors `CsvImportService.ImportPreview` inner-class convention (lines 554-585). |
| HTTP handling (`POST /preview`) | OUT OF SCOPE — Phase 55 | — | Phase 54 is pure backend. |
| Template rendering | OUT OF SCOPE — Phase 55 | — | Phase 54 is pure backend. |

## Answers to Research Questions

### 1. SeasonDriverRepository finder

**Answer: the finder already exists. No new method is required.**

File: `src/main/java/org/ctc/domain/repository/SeasonDriverRepository.java`

Complete list of methods (lines 13-28):

```java
public interface SeasonDriverRepository extends JpaRepository<SeasonDriver, UUID> {

    @EntityGraph(attributePaths = {"driver", "team"})
    List<SeasonDriver> findBySeasonId(UUID seasonId);                                  // line 14

    @EntityGraph(attributePaths = {"driver", "team"})
    List<SeasonDriver> findBySeasonIdAndTeamId(UUID seasonId, UUID teamId);            // line 17

    Optional<SeasonDriver> findBySeasonIdAndDriverId(UUID seasonId, UUID driverId);    // line 19  ← USE THIS

    @EntityGraph(attributePaths = {"driver", "team"})
    List<SeasonDriver> findByDriverId(UUID driverId);                                  // line 22

    @EntityGraph(attributePaths = {"driver", "team"})
    List<SeasonDriver> findBySeasonIdIn(List<UUID> seasonIds);                         // line 25

    @EntityGraph(attributePaths = {"driver", "team"})
    List<SeasonDriver> findByTeamIdIn(List<UUID> teamIds);                             // line 28
}
```

- **Signature:** `Optional<SeasonDriver> findBySeasonIdAndDriverId(UUID seasonId, UUID driverId)`
- **Annotations:** NONE — plain Spring Data derived query, no `@Query`, no `@EntityGraph`
- **Nullability:** Returns `Optional<SeasonDriver>` (wraps `null`)
- **Lazy loading implication:** Because there is no `@EntityGraph`, accessing `.getTeam()` on the returned `SeasonDriver` will **lazy-load** the team. Inside a preview (which runs inside an HTTP request with OSIV active), this is fine. For the CONFLICT check (`existing.getTeam().getId().equals(sheetTeam.getId())`), the lazy fetch will happen once per existing `SeasonDriver` touched — acceptable for <100 rows/tab.
- **Reference usage (production code):** `CsvImportService.java:397` calls it inside `ensureSeasonDriver(...)` — confirmed working pattern.
- **Reference usage (test):** `CsvImportServiceTest.java:116, 937` shows both the mock stub (`when(...).thenReturn(Optional.empty())`) and the "existing" case (`Optional.of(existingSeasonDriver)`).

**Confidence: HIGH** (verified by reading the repository source and two usage sites).

### 2. DriverMatchingService.MatchResult shape

File: `src/main/java/org/ctc/dataimport/DriverMatchingService.java`

**MatchResult record (lines 97-118):**

```java
public record MatchResult(String searchTerm, Driver driver, MatchType type, double similarity) {

    public static MatchResult exact(String searchTerm, Driver driver) {
        return new MatchResult(searchTerm, driver, MatchType.EXACT, 1.0);
    }

    public static MatchResult fuzzy(String searchTerm, Driver driver, double similarity) {
        return new MatchResult(searchTerm, driver, MatchType.FUZZY, similarity);
    }

    public static MatchResult noMatch(String searchTerm) {
        return new MatchResult(searchTerm, null, MatchType.NONE, 0.0);
    }

    public boolean isMatch() { return type != MatchType.NONE; }
    public boolean needsConfirmation() { return type == MatchType.FUZZY; }
}
```

**MatchType enum (lines 90-92):**

```java
public enum MatchType { EXACT, FUZZY, NONE }
```

**Which MatchType fires for which stage (inside `findDriver(String)` lines 24-63):**

| Stage | Code path | Returns |
|-------|-----------|---------|
| 1. Exact PSN match | `driverRepository.findByPsnId(searchTerm)` | `MatchResult.exact(searchTerm, driver)` — type=EXACT, similarity=1.0 |
| 2. Case-insensitive PSN | `driverRepository.findByPsnIdIgnoreCase(searchTerm)` | `MatchResult.exact(...)` — type=EXACT, similarity=1.0 |
| 3. Alias (case-insensitive) | `driverRepository.findByAliasIgnoreCase(searchTerm)` | `MatchResult.exact(...)` — type=EXACT, similarity=1.0 |
| 4. Levenshtein fuzzy | Best match from `findAll()` filtered by `similarity >= 0.8` | `MatchResult.fuzzy(searchTerm, driver, similarity)` — type=FUZZY, similarity ∈ [0.8, 1.0) |
| 5. No match | Empty/null input or no stage matched | `MatchResult.noMatch(searchTerm)` — type=NONE, driver=null, similarity=0.0 |

**Key facts for Phase 54:**

- FUZZY **does carry the candidate `Driver`** — the preview's `FuzzySuggestionRow` can use `match.driver().getId()` for `suggestedDriverId` and `match.driver().getPsnId()` / `match.driver().getNickname()` for display.
- Similarity range for FUZZY is `[0.8, 1.0)` in practice (1.0 would go through an exact stage first).
- **Threshold constant to reuse:** `DriverMatchingService.FUZZY_THRESHOLD = 0.8` (line 19, `private static`). Not exposed. If the preview records want to assert a documented floor, hard-code `0.8` in the record's javadoc; no getter needed.
- **Null input is safe** — blank/null PSNs return `MatchResult.noMatch(null)`. However, Phase 54 should **not** reach this branch because the D-12 step-1 precedence check rejects blank PSN before calling the matcher.

**Confidence: HIGH.**

### 3. GoogleSheetsService.readRangeFromSheet output shape

File: `src/main/java/org/ctc/dataimport/GoogleSheetsService.java:67-87`

**Signature:**

```java
public List<List<Object>> readRangeFromSheet(String spreadsheetId, String sheetName, String range)
    throws IOException;
```

It delegates to `readRange(spreadsheetId, sheetName + "!" + range)`.

**Return shape:**

- Outer `List<List<Object>>` — one element per sheet row
- Inner `List<Object>` — cells; element types are determined by Google's Sheets v4 API:
  - Text cells → `String`
  - Number cells → `BigDecimal` or `Double` (Sheets API returns as text by default; `ScorecardParser.parseIntSafe` at line 156 accommodates `"2.0"` strings)
  - Boolean / checkbox → `Boolean`
  - Empty cells at the end of a row → **truncated** (Sheets API omits trailing empties)
  - Empty cells in the middle → represented as empty `String ""`
- `response.getValues()` may return `null` when the range is empty → converted to `List.of()` (line 73).

**Header row handling:**

`readRangeFromSheet` is **agnostic** — it returns every row in the range including the header. Phase 54 must skip the header explicitly. Precedent:

- `CsvImportService.readCsvLines` (lines 458-475) skips row 0 if it contains `"team"` or `"psn"` (case-insensitive). Good for CSV, brittle for Sheets.
- `ScorecardParser.parse` (lines 37-67) does NOT skip a fixed header row — it walks rows looking for "Position" in column B as a dynamic header marker.

**For Phase 54: skip `rows.get(0)` unconditionally.** The sheet's structure is locked to "row 0 = headers, row 1+ = data" per the design spec (IMPORT-03). No dynamic detection needed. A one-line `rows.subList(1, rows.size())` (guarded by `rows.size() > 1`) is sufficient.

**A:C handling with short rows:**

The phase reads `"A:C"`. Typical rows will have 3 cells. But three pathological cases must be handled:

1. **Row has only column A (e.g. empty team code → trailing B and C omitted):** `row.size() == 1`. Accessing `row.get(2)` throws `IndexOutOfBoundsException`. → **Use a defensive helper** `String colC = row.size() > 2 ? cellToString(row.get(2)) : "";` Mirrors `ScorecardParser.cellToString` (line 178-181: returns `""` for null).
2. **Empty row (`row.isEmpty()`):** Skip silently (no error bucket — it's not a data row, it's whitespace). `ScorecardParser.java:41-45` uses the same skip behavior.
3. **Whitespace-only PSN (`"   "`):** After trimming, `psn.isBlank()` → `ERROR/BLANK_PSN_ID` (D-10). Same for team code.

**Recommended cell-extraction helper (already present in ScorecardParser.java:178-181):**

```java
private String cellToString(Object cell) {
    if (cell == null) return "";
    return cell.toString();
}
```

Phase 54 should reuse this pattern (or extract `cellToString` into a shared static utility, but that's a cross-cutting refactor — discretion says keep it private per service).

**Confidence: HIGH.**

### 4. CsvImportService.ImportPreview + inner record classes

File: `src/main/java/org/ctc/dataimport/CsvImportService.java`

**Inner records (lines 523-552):**

```java
public record PlayoffMatchupDto(UUID id, String seasonDisplayLabel, String roundLabel,
                                String team1, String team2) {
    public String displayLabel() {
        return roundLabel + ": " + team1 + " vs " + team2;
    }
}

public record ImportMetadata(UUID seasonId, String matchdayLabel, String track, String car,
                             UUID playoffMatchupId, UUID matchdayId) {
    // compact constructors for convenience omitted for brevity — see lines 532-539
    public boolean isPlayoff() { return playoffMatchupId != null; }
    public boolean hasMatchdayId() { return matchdayId != null; }
}

public record ImportRow(String teamShortName, String psnId, int position, int qualiPosition,
                        boolean fastestLap, DriverMatchingService.MatchResult matchResult) {
}
```

**Inner `@Getter` static class `ImportPreview` (lines 554-585):**

```java
@Getter
public static class ImportPreview {
    private final ImportMetadata metadata;
    private final List<ImportRow> rows = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    @lombok.Setter
    private boolean duplicateDetected;

    public ImportPreview(ImportMetadata metadata) { this.metadata = metadata; }

    public void addRow(ImportRow row) { rows.add(row); }
    public void addError(String error) { errors.add(error); }

    public boolean hasErrors() { return !errors.isEmpty(); }
    public boolean hasFuzzyMatches() {
        return rows.stream().anyMatch(r -> r.matchResult().needsConfirmation());
    }
    public boolean hasNewDrivers() {
        return rows.stream().anyMatch(r -> !r.matchResult().isMatch());
    }
}
```

**Observations for Phase 54:**

- Records are used for immutable value objects (`ImportMetadata`, `ImportRow`, `PlayoffMatchupDto`).
- `@Getter` + `ArrayList` mutable fields are used where the service progressively builds state (`ImportPreview`).
- **Phase 54's `TabPreview` should be a record** (D-04 says typed fields, D-05 confirms outer `DriverSheetImportPreview` is a record). This is an evolution of the pattern toward full immutability — `DriverSheetImportService.preview(...)` can build per-tab ArrayList locally, then freeze into an immutable record at the end.
- **Thymeleaf-friendly getter naming:** Records expose accessors as `tabName()`, `suggestedSeasonId()` — Thymeleaf resolves both `${tab.tabName}` (bean-property style via introspection) and `${tab.tabName()}` (method-call style). `CsvImportController` + import templates already pass records to templates successfully (e.g. `PlayoffMatchupDto`), so records are template-safe.

**Confidence: HIGH.**

### 5. Existing test patterns under `src/test/java/org/ctc/dataimport/`

**Mocking setup (uniform across the package):**

```java
@ExtendWith(MockitoExtension.class)    // JUnit 5 + Mockito
class ServiceNameTest {

    @Mock private DependencyOne dep1;
    @Mock private DependencyTwo dep2;

    @InjectMocks private ServiceUnderTest service;

    @BeforeEach
    void setUp() { /* test data fixtures */ }
}
```

**Representative tests (with exact line citations):**

1. **`DriverMatchingServiceTest.ExactMatchTest.givenKnownPsnId_whenFindDriver_thenReturnsExactMatch` (line 45-56):**
   ```java
   // given
   when(driverRepository.findByPsnId("AHR_Hills_93")).thenReturn(Optional.of(hills));

   // when
   var result = matchingService.findDriver("AHR_Hills_93");

   // then
   assertEquals(DriverMatchingService.MatchType.EXACT, result.type());
   assertEquals(hills, result.driver());
   ```
   → Pure Mockito `@Mock` + `@InjectMocks`; mixed JUnit `assertEquals` (not AssertJ). **Phase 54 should prefer AssertJ** (CLAUDE.md implies AssertJ convention via `CsvImportServiceTest` usage) for uniform style.

2. **`CsvImportServiceTest.givenValidCsv_whenParseAndPreview_thenReturnsRows` (line 364-382):**
   ```java
   when(driverMatchingService.findDriver("drv1"))
           .thenReturn(DriverMatchingService.MatchResult.exact("drv1", driver1));
   when(driverMatchingService.findDriver("drv2"))
           .thenReturn(DriverMatchingService.MatchResult.exact("drv2", driver2));

   var csvContent = "Team,PSN ID,Position,Quali,FL\nBRV,drv1,1,1,true\nCRL,drv2,2,2,false\n";
   var stream = new java.io.ByteArrayInputStream(csvContent.getBytes());
   ...
   assertThat(preview.getRows()).hasSize(2);
   assertThat(preview.hasErrors()).isFalse();
   ```
   → **Canonical pattern for Phase 54.** Stub `driverMatchingService.findDriver(psn)` per row; assert on the preview's bucket lists.

3. **`CsvImportServiceTest.givenFuzzyMatchOnAlias_whenFindDriver_thenReturnsFuzzyMatch` (inside `DriverMatchingServiceTest.AliasMatchTest` — line 124-139):** Shows fuzzy-match scenario setup and similarity assertion (`assertTrue(result.similarity() >= 0.8)`).

4. **`CsvImportServiceTest.givenExistingSeasonDriverWithDifferentTeam_whenEnsureSeasonDriver_thenUpdatesTeam` (line 931-951):** Shows how to mock `seasonDriverRepository.findBySeasonIdAndDriverId(...)` — this is the exact same call Phase 54 uses for CONFLICT detection.
   ```java
   var existingSeasonDriver = new SeasonDriver(season, driver1, standaloneTeam1);
   when(seasonDriverRepository.findBySeasonIdAndDriverId(season.getId(), driver1.getId()))
           .thenReturn(Optional.of(existingSeasonDriver));
   ```

**Assertion style:**

- `CsvImportServiceTest` uses **AssertJ** (`org.assertj.core.api.Assertions.assertThat`): `.hasSize`, `.isFalse()`, `.contains()`, `.extracting(...)`, `.containsExactlyInAnyOrder(...)`, `.allSatisfy(...)`. **← Phase 54 target.**
- `DriverMatchingServiceTest` uses **JUnit 5** (`org.junit.jupiter.api.Assertions.assertEquals`). Older style.

**Argument captor example (line 631-633):**

```java
ArgumentCaptor<Match> matchCaptor = ArgumentCaptor.forClass(Match.class);
verify(matchRepository).save(matchCaptor.capture());
```

**Convention summary for Phase 54:**

- Framework: JUnit 5 + Mockito `@ExtendWith(MockitoExtension.class)`
- Mocks: `@Mock` for all five dependencies (`GoogleSheetsService`, `DriverMatchingService`, `SeasonRepository`, `TeamRepository`, `SeasonDriverRepository`); `@InjectMocks` for `DriverSheetImportService`
- Assertions: AssertJ (`assertThat`)
- Naming: `givenX_whenY_thenZ()` with `// given / // when / // then` comments
- Test data: `@BeforeEach` sets up reusable `Season`, `Driver`, `Team` fixtures via plain constructors + `setId(UUID.randomUUID())`

**Confidence: HIGH.**

### 6. Bucket / field design for row records

Per D-04 (typed per-bucket records). Concrete definitions below — names chosen to read cleanly in Thymeleaf (`th:each="row : ${tab.newDrivers}"` etc.).

```java
public record NewDriverRow(
        String psnId,
        String teamShortName
) {}

public record NewAssignmentRow(
        String psnId,
        UUID existingDriverId,          // the matched Driver from DriverMatchingService (EXACT)
        String teamShortName
) {}

public record ConflictRow(
        String psnId,
        UUID existingDriverId,
        UUID existingSeasonDriverId,    // for Phase 55 upsert; not rendered but needed on execute
        String existingTeamShortName,   // currently-assigned team — to display "was X, now Y"
        String sheetTeamShortName       // the team from the sheet
) {}

public record FuzzySuggestionRow(
        String psnId,                   // what was in the sheet (raw, for form value)
        UUID suggestedDriverId,         // DriverMatchingService match candidate
        String suggestedPsnId,          // the candidate's canonical PSN (for display)
        String suggestedNickname,       // the candidate's nickname (for admin readability)
        double similarity,              // [0.8, 1.0) — for transparency
        String teamShortName
) {}

public record UnchangedRow(
        String psnId,
        UUID existingDriverId,
        String teamShortName            // already matches sheet; listed for transparency
) {}

public record ErrorRow(
        String rawPsnId,                // may be blank/whitespace — do not trim for display
        String rawTeamCode,             // may be blank — show what the sheet had
        ErrorReason reason
) {}
```

**`ErrorReason` enum (D-09, D-10):**

```java
public enum ErrorReason {
    BLANK_PSN_ID    ("PSN ID is blank"),
    BLANK_TEAM_CODE ("Team short code is blank"),
    UNKNOWN_TEAM_CODE ("Team short code not found"),
    DUPLICATE_IN_TAB  ("PSN already listed earlier in this tab");

    private final String message;
    ErrorReason(String message) { this.message = message; }
    public String message() { return message; }
}
```

**`TabPreview` record (D-04, six typed bucket fields + suggested season + ambiguity reason):**

```java
public record TabPreview(
        String tabName,                                 // "2024" etc.
        int year,                                       // parsed Integer.parseInt(tabName)
        UUID suggestedSeasonId,                         // null if 0 or ≥2 candidates
        String ambiguousReason,                         // null if singleton match; populated per D-03
        List<NewDriverRow> newDrivers,
        List<NewAssignmentRow> newAssignments,
        List<ConflictRow> conflicts,
        List<FuzzySuggestionRow> fuzzySuggestions,
        List<UnchangedRow> unchanged,
        List<ErrorRow> errors
) {}
```

**`DriverSheetImportPreview` top-level record (D-05):**

```java
public record DriverSheetImportPreview(
        List<TabPreview> tabPreviews                    // sorted ascending by year
) {}
```

**Design rationale aligned with D-04:**

- Each bucket carries **exactly the fields its downstream renderer / executor needs** — no nullable catch-all fields (D-04 constraint met).
- `ConflictRow` surfaces `existingTeamShortName` so the admin can see "currently AHR → sheet says CRL"; `existingSeasonDriverId` enables Phase 55 to update in-place without a second lookup (minor optimization; can be dropped if planner prefers to re-query).
- `FuzzySuggestionRow` carries enough data for the admin to judge the suggestion without Phase 55 re-hitting the DB: the candidate's `psnId`, `nickname`, and `similarity` are all useful in the UI.
- `ErrorRow.rawPsnId` / `rawTeamCode` are intentionally **untrimmed** — if the admin sees `"   "` vs `""` vs `"PSN With Trailing Space "`, they can diagnose sheet errors more easily.

**Confidence: HIGH** (records follow exactly what D-04 prescribes; Claude's discretion used on naming and the two optional fields — `existingSeasonDriverId` in `ConflictRow`, `suggestedNickname` in `FuzzySuggestionRow`).

### 7. Preview-data-class organization

**Recommendation: inner records of `DriverSheetImportService`, mirroring `CsvImportService.ImportPreview`.**

Rationale:

- **Existing convention.** `CsvImportService` keeps `ImportMetadata`, `ImportRow`, `ImportPreview`, `ImportResult`, `PlayoffMatchupDto` all as inner types. The dataimport package does not currently use a `preview` subpackage or top-level data classes. Consistency with existing code beats cleanliness-for-its-own-sake.
- **Scope & cohesion.** These records are used exclusively by `DriverSheetImportService` (producer) and Phase 55's `DriverSheetImportController` (consumer). Both live in non-domain packages. No reason to graduate them to top-level.
- **Discoverability.** Phase 55 consumers will write `service.preview(...)` and IDE autocomplete surfaces the inner types under the service class — same experience as `CsvImportService.ImportPreview`.

**Contrary option considered:** Top-level records in `org.ctc.dataimport` (e.g. `DriverSheetImportPreview.java`, `TabPreview.java`). The design spec's §Architecture "New files" table lists these as separate files. This was the spec's assumption, not a locked decision.

**Decision:** **Inner records.** If the planner disagrees, the alternative (top-level per-file records) is valid — the tradeoff is one more file per record vs. a ~300-line service. Given Phase 55 will add its own DTO (`DriverSheetImportForm`) in `org.ctc.admin.dto`, keeping Phase 54's records nested avoids polluting the `dataimport` top-level namespace with 7+ new files.

**Optional second preference:** If the planner insists on separating (for clarity), create a subpackage `org.ctc.dataimport.preview` with `DriverSheetImportPreview.java` and the six row records. But do NOT create 7 top-level files alongside the 5 already-existing service classes in `org.ctc.dataimport/`.

### 8. Package structure

**File listing of `src/main/java/org/ctc/dataimport/` (verified):**

```
CsvImportController.java      (9.7 KB)
CsvImportService.java         (22.4 KB)
DriverMatchingService.java    (4.1 KB)
GoogleCalendarService.java    (4.3 KB)
GoogleSheetsService.java      (6.3 KB)
ScorecardParser.java          (5.6 KB)
```

Six files. **Naming collisions: none.** `DriverSheetImportService.java` does not exist yet. The name is distinct from `CsvImportService.java` and `DriverMatchingService.java`.

**Conclusion:** `org.ctc.dataimport.DriverSheetImportService` is the correct FQCN. No package restructuring needed.

**Existing `CsvImportController` lives in the same `dataimport` package** (line 1 of CsvImportController.java) — not in `org.ctc.admin.controller`. This deviates from the design spec's "New files" table, which assumes `DriverSheetImportController` → `org.ctc.admin.controller`. Phase 55 is out of scope for this research, but the planner should note this inconsistency and decide whether Phase 55's controller follows the existing `dataimport` precedent (same package as service) or the design-spec aspiration (admin package). Either is defensible.

**Confidence: HIGH.**

### 9. Logging

**CLAUDE.md convention:** `log.info()` for state changes, `log.debug()` for calculations, always parameterized `{}` format.

**Existing patterns in the dataimport package:**

- `DriverMatchingService.java:32, 39, 46, 55, 61` — all `log.debug(...)` for per-row calculation decisions ("Exact match for '{}'", "Fuzzy match for '{}': {} (similarity: {})"). Correct per convention: matching is a calculation.
- `CsvImportService.java:187, 250, 301, 355, 385` — `log.info(...)` for state-changing events ("Overwriting existing match", "Created new driver", "Import completed: {} races...").
- `CsvImportService.java:400, 404` — `log.debug(...)` for SeasonDriver create/update decisions (no external state change yet during preview — they're advisory).

**Proposed skeleton for `DriverSheetImportService.preview(String sheetUrl)`:**

```java
public DriverSheetImportPreview preview(String sheetUrl) throws IOException {
    var spreadsheetId = googleSheetsService.extractSpreadsheetId(sheetUrl);
    var allTabs = googleSheetsService.getSheetNames(spreadsheetId);
    var yearTabs = filterYearTabs(allTabs);

    log.info("Driver sheet preview: sheet {} has {} total tabs, {} match year pattern",
             spreadsheetId, allTabs.size(), yearTabs.size());

    var tabPreviews = new ArrayList<TabPreview>();
    for (String tab : yearTabs) {
        int year = Integer.parseInt(tab);
        var rows = googleSheetsService.readRangeFromSheet(spreadsheetId, tab, "A:C");
        log.debug("Tab {} ({}) returned {} raw rows", tab, year, rows.size());

        var tabPreview = categorize(tab, year, rows);
        log.info("Tab {} categorized: {} new, {} assignments, {} conflicts, {} fuzzy, {} unchanged, {} errors",
                 tab,
                 tabPreview.newDrivers().size(),
                 tabPreview.newAssignments().size(),
                 tabPreview.conflicts().size(),
                 tabPreview.fuzzySuggestions().size(),
                 tabPreview.unchanged().size(),
                 tabPreview.errors().size());
        tabPreviews.add(tabPreview);
    }

    return new DriverSheetImportPreview(tabPreviews);
}
```

- **`log.info` at entry + per-tab summary** — transparency for admin-triggered operations.
- **`log.debug` for per-row categorization** — noise control; the bucketing helper should emit one `log.debug` per row with its final bucket (`log.debug("Row {} bucketed as {}: {}", row.psnId(), bucket, reason)`).
- Google Sheets errors (`IOException`) bubble up to the Phase 55 controller, which renders them as `errorMessage` flash — mirrors `CsvImportController.previewSheet` lines 116-121.

**Confidence: HIGH.**

### 10. Test coverage strategy for ≥9 scenarios (SC#6)

The CONTEXT.md §specifics lists **twelve** scenarios (lines 251-262). Each maps to at least one D-07..D-12 decision AND/OR one phase requirement. Mapping:

| # | Scenario | Decision | Requirement |
|---|----------|----------|-------------|
| 1 | `givenMixedTabNames_whenPreview_thenOnlyFourDigitTabsIncluded` | — (IMPORT-02) | IMPORT-02 |
| 2 | `givenNewPsnId_whenPreview_thenCategorisedAsNewDriver` | D-12 step 7 | UX-01 |
| 3 | `givenExistingPsnIdDifferentCase_whenPreview_thenResolvedViaCaseInsensitive` | MATCH-01 reuse | MATCH-01 |
| 4 | `givenFuzzyCandidate_whenPreview_thenSuggestedMatchAwaitsUserOptIn` | D-12 step 5 | UX-04 |
| 5 | `givenExistingSeasonDriverSameTeam_whenPreview_thenCategorisedAsUnchanged` | D-12 step 6 | UX-05 |
| 6 | `givenExistingSeasonDriverDifferentTeam_whenPreview_thenCategorisedAsConflict` | D-12 step 6 | UX-03 |
| 7 | `givenUnknownTeamCode_whenPreview_thenRowErroredWithUnknownTeam` | D-10, D-12 step 3 | UX-06, DATA-02 |
| 8 | `givenBlankPsnId_whenPreview_thenRowErroredWithBlankPsn` | D-10, D-12 step 1 | UX-06 |
| 9 | `givenBlankTeamCode_whenPreview_thenRowErroredWithBlankTeam` | D-10, D-12 step 2 | UX-06 |
| 10 | `givenDuplicatePsnInTab_whenPreview_thenSecondRowErroredWithDuplicate` | D-10, D-11, D-12 step 4 | — (error completeness) |
| 11 | `givenSamePsnInMultipleTabs_whenPreview_thenEachTabCategorisedIndependently` | D-07 | MATCH-02 |
| 12 | `givenMultipleSeasonsForYear_whenPreview_thenSuggestedSeasonNullWithAmbiguousReason` | D-02, D-03 | IMPORT-05, DATA-01 |

**Additional scenarios the planner should consider to cover remaining phase requirements:**

| # | Proposed scenario | Requirement |
|---|-------------------|-------------|
| 13 | `givenNoSeasonForYear_whenPreview_thenSuggestedSeasonNullWithNoSeasonReason` | D-02 (zero-candidate branch), IMPORT-05, DATA-01 |
| 14 | `givenFuzzyAcceptedInOneTabRejectedInAnother_whenPreview_thenBothCategorisedIndependently` | D-08 (cross-tab independence) |
| 15 | `givenEmptyTab_whenPreview_thenEmptyTabPreviewReturned` | IMPORT-04 (empty-data resilience) |
| 16 | `givenTabsInReverseOrder_whenPreview_thenTabsSortedAscendingByYear` | IMPORT-04 (D-05 sort guarantee) |
| 17 | `givenGoogleSheetsIoError_whenPreview_thenIoExceptionPropagated` | QUAL-04 shared error contract (preview throws; Phase 55 controller handles) |
| 18 | `givenRowShorterThanThreeColumns_whenPreview_thenTreatedAsBlankTeamCode` | IMPORT-03 defensive cell-extraction |
| 19 | `givenPsnWithSurroundingWhitespace_whenPreview_thenTrimmedBeforeMatching` | IMPORT-03 (data hygiene) |

**Total: 12 required + 7 recommended = 19 scenarios.** Planner can pick 12–15 to keep the test file readable while hitting all requirements.

**Coverage verification approach:**

- Run `./mvnw verify` → `target/site/jacoco/index.html`. The new `DriverSheetImportService` class and its inner records must individually hit ≥82% line coverage.
- Records generate synthetic accessor / constructor methods that count toward lines; JaCoCo filters record accessors automatically in recent versions (0.8.10+; project uses 0.8.14 per pom.xml:200 — good).
- The `ErrorReason` enum is trivially covered by scenarios 7, 8, 9, 10.
- No JaCoCo exclusion needed for the new service (only graphic services are currently excluded per pom.xml:203-216).

**Confidence: HIGH.**

### 11. Edge cases the planner must handle

**11.1. Tab filter regex `^\d{4}$` sufficiency.**

- `"2024"` ✓ matches
- `"2024 (Archive)"` ✗ does NOT match — regex is anchored; the trailing `" (Archive)"` fails. Correct behavior (out-of-scope tabs).
- `"12345"` ✗ does NOT match — `{4}` is exact count.
- `"202"` ✗ — too short.
- `" 2024"` / `"2024 "` ✗ — anchors exclude leading/trailing whitespace. If admins have whitespace-padded tab names, preview silently drops them. **Recommendation: do not trim tab names before matching** — a whitespaced tab name indicates sheet hygiene issues the admin should fix manually. Matches spec intent.

**Confidence: HIGH.**

**11.2. Header row skip.**

Sheet structure is "row 0 = headers (PSN ID, [hidden B], Team), row 1+ = data" per IMPORT-03. **Skip `rows.get(0)` unconditionally** when the sheet has ≥1 row. If `rows.isEmpty()`, emit a `TabPreview` with all empty bucket lists (no error — empty tabs are a valid state; scenario 15).

Precedent: `CsvImportService.readCsvLines:462-468` uses a `firstLine` flag + content heuristic (`contains "team"` OR `contains "psn"`) to skip. This is CSV-specific; for Sheets, the structure is fixed, so the heuristic is unnecessary.

**Confidence: HIGH.**

**11.3. Whitespace-only vs empty vs null cell.**

All three must map to BLANK. The normalization pipeline:

```java
String raw = row.size() > colIndex ? cellToString(row.get(colIndex)) : "";
String normalized = raw.trim();
if (normalized.isEmpty()) {   // catches "", "   ", null (via cellToString)
    // → BLANK
}
```

`cellToString` must handle `null` → `""` (ScorecardParser.java:179). `Object.toString()` on `Integer`, `BigDecimal`, `Boolean` all yield a sensible string. `String.trim()` handles leading/trailing whitespace and ASCII control chars.

**Confidence: HIGH.**

**11.4. Team short-code case sensitivity (`TeamRepository.findByShortName`).**

- **`findByShortName(String)` is case-sensitive** (derived Spring Data query — no `IgnoreCase` suffix). Line 11 of TeamRepository.java.
- `findByShortNameIgnoreCase(String)` also exists (line 13) — Phase 54 could prefer that for lenient matching.
- **CsvImportService does not use the repository at all for team resolution** — it uses `findTeamFlexible()` (line 412-429) which searches within `season.getTeams()` with exact → CI → underscore-normalized fallback.

**Recommendation for Phase 54:** Use `findByShortName(String)` as CONTEXT.md §Canonical Refs prescribes. This matches the existing `TeamRepository` finder and is intentionally strict — short codes in the sheet should match exactly (e.g. `AHR`, `ART`). If the sheet has `"ahr"` lowercase, that is a sheet-hygiene error the admin can fix.

**Contrary option:** Use `findByShortNameIgnoreCase` for user-friendliness. Not recommended — the canonical sheet uses uppercase short codes by convention, and ambiguity with case-variant teams is avoided.

**No season-scoping:** Unlike `CsvImportService.findTeamFlexible` which scopes to `season.getTeams()`, Phase 54 uses the global `TeamRepository.findByShortName`. This is correct for Phase 54 because:
1. At preview time, the admin has not yet committed a season (`suggestedSeasonId` may be null).
2. The short code is globally unique in practice (one `AHR` team exists).
3. Phase 55's execute path is where season-team membership can be enforced.

**Confidence: HIGH** (case-sensitive `findByShortName` matches the `@ASSUMED` convention with an `[CITED: TeamRepository.java:11]`).

**11.5. Whitespace-trimming team code.**

Apply `String.trim()` before calling `findByShortName`. A row with `"  AHR  "` should resolve to the AHR team. Precedent: `ScorecardParser.parseDriverRow:128` calls `psnId.trim()`. Same treatment here.

**Confidence: HIGH.**

**11.6. Google Sheets API throttling / auth / 404 errors.**

`GoogleSheetsService` does **not** wrap retries or rate-limit handling. Failures propagate as `IOException` from `readRange` (line 67). `IllegalArgumentException` from `extractSpreadsheetId` for malformed URLs (line 155). `IllegalStateException` if credentials file missing (line 161).

**Phase 54's preview method signature should declare `throws IOException`** to pass through. The controller (Phase 55) catches and renders `errorMessage` flash — mirrors `CsvImportController.previewSheet` lines 116-121 exactly. No new wrapping layer in Phase 54.

**Confidence: HIGH.**

### 12. (MANDATORY) Validation Architecture

> See the dedicated `## Validation Architecture` section below.

### 13. Out-of-scope confirmation

See `## Out of Scope for Phase 54` section below.

## Validation Architecture

**Required per `.planning/config.json: workflow.nyquist_validation: true` (verified — see Q12 in Research Focus).**

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (`org.junit.jupiter` 5.x via Spring Boot 4.x BOM) + Mockito (`mockito-core`, `mockito-junit-jupiter`) + AssertJ 3.x |
| Config file | `pom.xml` (no separate test config); Surefire plugin lines 183-192 |
| Quick run command | `./mvnw test -Dtest=DriverSheetImportServiceTest` |
| Full suite command | `./mvnw verify` |

**Versions (verified in pom.xml):**

- `mockito-core`, `mockito-junit-jupiter` — scope `test`, version managed by Spring Boot BOM (lines 137-147)
- JaCoCo `0.8.14` (pom.xml:200)
- Project line-coverage gate **82%** (pom.xml:241)

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| IMPORT-02 | Only `^\d{4}$` tabs included | unit | `./mvnw test -Dtest=DriverSheetImportServiceTest#givenMixedTabNames_whenPreview_thenOnlyFourDigitTabsIncluded` | ❌ Wave 0 |
| IMPORT-03 | Reads A:C, skips header row, handles short rows | unit | `./mvnw test -Dtest=DriverSheetImportServiceTest#givenRowShorterThanThreeColumns_whenPreview_thenTreatedAsBlankTeamCode` | ❌ Wave 0 |
| IMPORT-04 | One TabPreview per tab, sorted ascending | unit | `./mvnw test -Dtest=DriverSheetImportServiceTest#givenTabsInReverseOrder_whenPreview_thenTabsSortedAscendingByYear` | ❌ Wave 0 |
| IMPORT-05 | `suggestedSeasonId` auto-match via year | unit | `./mvnw test -Dtest=DriverSheetImportServiceTest#givenMultipleSeasonsForYear_whenPreview_thenSuggestedSeasonNullWithAmbiguousReason` | ❌ Wave 0 |
| UX-01 | NEW_DRIVER bucket | unit | `...#givenNewPsnId_whenPreview_thenCategorisedAsNewDriver` | ❌ Wave 0 |
| UX-02 | NEW_ASSIGNMENT bucket | unit | (covered by scenario 5 — SeasonDriver absence branch) | ❌ Wave 0 |
| UX-03 | CONFLICT bucket | unit | `...#givenExistingSeasonDriverDifferentTeam_whenPreview_thenCategorisedAsConflict` | ❌ Wave 0 |
| UX-04 | FUZZY_SUGGESTION bucket | unit | `...#givenFuzzyCandidate_whenPreview_thenSuggestedMatchAwaitsUserOptIn` | ❌ Wave 0 |
| UX-05 | UNCHANGED bucket | unit | `...#givenExistingSeasonDriverSameTeam_whenPreview_thenCategorisedAsUnchanged` | ❌ Wave 0 |
| UX-06 | ERROR bucket (blank PSN / blank team / unknown team / duplicate) | unit | `...#givenUnknownTeamCode...`, `...#givenBlankPsnId...`, `...#givenBlankTeamCode...`, `...#givenDuplicatePsnInTab...` | ❌ Wave 0 |
| MATCH-01 | Delegates to DriverMatchingService unchanged | unit (mock verification) | `...#givenExistingPsnIdDifferentCase_whenPreview_thenResolvedViaCaseInsensitive` + `verify(driverMatchingService).findDriver(...)` | ❌ Wave 0 |
| MATCH-02 | Same PSN in multiple tabs → independent TabPreviews | unit | `...#givenSamePsnInMultipleTabs_whenPreview_thenEachTabCategorisedIndependently` | ❌ Wave 0 |
| DATA-01 | Missing season reported, never auto-created | unit + code inspection | `...#givenNoSeasonForYear_whenPreview_thenSuggestedSeasonNullWithNoSeasonReason` + grep for absence of `seasonRepository.save` in the new service | ❌ Wave 0 |
| DATA-02 | Unknown team code reported, never auto-created | unit + code inspection | `...#givenUnknownTeamCode...` + grep for absence of `teamRepository.save` in the new service | ❌ Wave 0 |
| DATA-04 | No Flyway migration | manual-only (grep) | `ls src/main/resources/db/migration/V*.sql` unchanged after phase | ✅ (no new migration expected) |
| DATA-05 | RaceLineup never modified | manual-only (grep) | Grep the new service file for `raceLineup` — should return zero hits | ✅ (code inspection) |
| TEST-01 | ≥9 given-when-then scenarios | unit | `./mvnw test -Dtest=DriverSheetImportServiceTest` — expect ≥9 `@Test` methods (target 12–15) | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `./mvnw test -Dtest=DriverSheetImportServiceTest` (< 10 s — pure unit, no Spring context)
- **Per wave merge:** `./mvnw verify` (full suite + JaCoCo gate)
- **Phase gate:** `./mvnw verify` green + `target/site/jacoco/index.html` shows `DriverSheetImportService` at ≥82% line coverage before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java` — the unit test file itself (the single most important Wave 0 deliverable; contains all 12+ scenarios)
- [ ] Test fixtures: reusable `Season(year=2024)`, `Season(year=2023)`, `Team("AHR")`, `Team("CRL")`, `Driver("some_psn_id")` constructed in `@BeforeEach` (pattern from `CsvImportServiceTest:63-100`)
- [ ] Mock setup helpers: a small `private void setupSheetsStub(String url, Map<String, List<List<Object>>> tabsToRows)` utility to shorten the `when(googleSheetsService.extractSpreadsheetId(...))` + `when(googleSheetsService.getSheetNames(...))` + `when(googleSheetsService.readRangeFromSheet(...))` chain. Optional but recommended for readability (Claude's discretion per CONTEXT.md).
- [ ] No new framework install needed — Mockito and AssertJ are already on the test classpath.

### Mock Boundaries

| Dependency | Stubbed Method | Typical Stub |
|------------|----------------|--------------|
| `GoogleSheetsService` | `extractSpreadsheetId(url)` | returns a fixed spreadsheet id string |
| `GoogleSheetsService` | `getSheetNames(id)` | returns `List.of("2023", "2024", "Roster", "Overall")` |
| `GoogleSheetsService` | `readRangeFromSheet(id, tab, "A:C")` | returns `List<List<Object>>` — 1 header row + N data rows; mix `String` cells with occasional empty `""` and short rows |
| `DriverMatchingService` | `findDriver(psn)` | returns `MatchResult.exact(...)`, `MatchResult.fuzzy(...)`, or `MatchResult.noMatch(...)` per scenario |
| `SeasonRepository` | `findByYear(int)` | returns `List.of(season2024)` (singleton), `List.of()` (none), or `List.of(s1, s2)` (ambiguous) |
| `TeamRepository` | `findByShortName(code)` | returns `Optional.of(team)` or `Optional.empty()` |
| `SeasonDriverRepository` | `findBySeasonIdAndDriverId(seasonId, driverId)` | returns `Optional.empty()` (NEW_ASSIGNMENT), `Optional.of(seasonDriverSameTeam)` (UNCHANGED), or `Optional.of(seasonDriverDifferentTeam)` (CONFLICT) |

**No repository is called with real DB I/O in Phase 54 unit tests.** No `@SpringBootTest`, no `@DataJpaTest`. Phase 55 will add a `@SpringBootTest`-based `DriverSheetImportControllerIT` — that is out of scope.

### Coverage Expectations

- **New file `DriverSheetImportService.java`:** ≥90% line coverage expected (the service is small, all branches are exercised by the 12+ scenarios).
- **New inner records:** accessor methods auto-counted by JaCoCo; `ErrorReason` enum covered by 4 scenarios.
- **Existing files touched:** `SeasonRepository.java` gains `findByYear(int)` — zero executable lines added (derived query, no body). No coverage impact.
- **Project aggregate:** must remain ≥82% (pom.xml:241). Current project coverage (per memory note `project_status_2026_04_06.md`) is at the 82% floor with 1011 tests; Phase 54 adds ~50-150 lines of covered code + 12-15 new tests, nudging the overall ratio up.

### Validation That Is Impossible or Impractical

| Validation | Why | Substitute |
|------------|-----|------------|
| Real Google Sheets HTTP round-trip | Auth credentials not available in CI; sheets rate-limit; test flakiness | Mock `GoogleSheetsService` completely. Phase 55's `DriverSheetImportControllerIT` also uses `@MockBean GoogleSheetsService`. |
| E2E Playwright test of preview | Design spec §Testing.Skip-for-MVP explicitly defers this (TEST-FUTURE-01) | Not in Phase 54 or Phase 55 scope. |
| Real `DriverMatchingService` with full DB | Would require `@DataJpaTest` + seed data; transforms a pure unit test into integration | Mock `DriverMatchingService.findDriver(...)` — the service's own unit tests (`DriverMatchingServiceTest`) already cover its 4 stages. |
| Bucket ordering within a bucket list | D-07 does not specify intra-bucket order | Assertions should use `.contains(...)` / `.containsExactlyInAnyOrder(...)` not `.containsExactly(...)` for bucket contents. |

## Proposed Preview Data Model

**(Collected from Q6; repeated here as a single block for the planner.)**

```java
// Inside org.ctc.dataimport.DriverSheetImportService (inner records and enum)

public record DriverSheetImportPreview(
        List<TabPreview> tabPreviews                    // sorted ascending by year
) {}

public record TabPreview(
        String tabName,
        int year,
        UUID suggestedSeasonId,                         // null if 0 or ≥2 candidates
        String ambiguousReason,                         // null if singleton match
        List<NewDriverRow> newDrivers,
        List<NewAssignmentRow> newAssignments,
        List<ConflictRow> conflicts,
        List<FuzzySuggestionRow> fuzzySuggestions,
        List<UnchangedRow> unchanged,
        List<ErrorRow> errors
) {}

public record NewDriverRow(String psnId, String teamShortName) {}

public record NewAssignmentRow(
        String psnId,
        UUID existingDriverId,
        String teamShortName
) {}

public record ConflictRow(
        String psnId,
        UUID existingDriverId,
        UUID existingSeasonDriverId,
        String existingTeamShortName,
        String sheetTeamShortName
) {}

public record FuzzySuggestionRow(
        String psnId,
        UUID suggestedDriverId,
        String suggestedPsnId,
        String suggestedNickname,
        double similarity,
        String teamShortName
) {}

public record UnchangedRow(
        String psnId,
        UUID existingDriverId,
        String teamShortName
) {}

public record ErrorRow(
        String rawPsnId,
        String rawTeamCode,
        ErrorReason reason
) {}

public enum ErrorReason {
    BLANK_PSN_ID      ("PSN ID is blank"),
    BLANK_TEAM_CODE   ("Team short code is blank"),
    UNKNOWN_TEAM_CODE ("Team short code not found"),
    DUPLICATE_IN_TAB  ("PSN already listed earlier in this tab");

    private final String message;
    ErrorReason(String message) { this.message = message; }
    public String message() { return message; }
}
```

**Proposed `SeasonRepository.findByYear(int)` signature (unannotated — consistent with existing repo methods):**

```java
// add to src/main/java/org/ctc/domain/repository/SeasonRepository.java

List<Season> findByYear(int year);
```

No `@EntityGraph` needed — preview only reads `season.getId()` (no lazy-loaded association access).

**Proposed service class skeleton:**

```java
package org.ctc.dataimport;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.repository.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriverSheetImportService {

    private static final Pattern YEAR_TAB_PATTERN = Pattern.compile("^\\d{4}$");

    private final GoogleSheetsService googleSheetsService;
    private final DriverMatchingService driverMatchingService;
    private final SeasonRepository seasonRepository;
    private final TeamRepository teamRepository;
    private final SeasonDriverRepository seasonDriverRepository;

    public DriverSheetImportPreview preview(String sheetUrl) throws IOException {
        // 1. extractSpreadsheetId
        // 2. getSheetNames → filter YEAR_TAB_PATTERN → sort ascending
        // 3. for each year tab:
        //      - readRangeFromSheet(id, tab, "A:C")
        //      - skip header row
        //      - resolve suggestedSeasonId via seasonRepository.findByYear(year)
        //      - walk rows, applying D-12 precedence into six bucket ArrayLists
        //      - wrap in TabPreview record
        // 4. return DriverSheetImportPreview(sortedTabs)
    }

    // inner records + enum as above
}
```

## Open Questions / Risks

None blocking. The following are resolved or explicitly deferred:

1. **`SeasonDriverRepository.findBySeasonIdAndDriverId` existence** — RESOLVED: method exists at line 19, no new repo method required. Only `SeasonRepository.findByYear(int)` is net new.
2. **Team short-code case sensitivity** — RESOLVED: use `findByShortName(String)` (case-sensitive) per CONTEXT.md canonical refs; justification given in Q11.4.
3. **`ConflictRow.existingSeasonDriverId` field** — RECOMMENDED but OPTIONAL. It saves Phase 55 one DB round-trip per conflict row on execute. If the planner prefers a leaner record, drop this field; the execute path can re-look up via `findBySeasonIdAndDriverId`. Flagged as Claude's discretion per D-04.
4. **`FuzzySuggestionRow.suggestedNickname`** — RECOMMENDED but OPTIONAL. Helps admin readability in Phase 55 UI. If the template only renders PSN IDs, this field is dead weight. Suggest keeping it — nicknames are short and the template designer (Phase 55) will probably want it.
5. **Per-tab team cache** — Claude's discretion (CONTEXT.md §Discretion). Sheet sizes <100 rows/tab mean ~100 `findByShortName` calls per tab in the worst case. Recommendation: **skip the cache for Phase 54 simplicity**. If profiling Phase 55 shows DB pressure, add a `Map<String, Team>` cache per tab call. Not a blocker.
6. **Internal "seen PSNs" tracker** — Claude's discretion. Recommendation: `Set<String>` of normalized (trimmed) PSN IDs, fed as each row is categorized. `Set` is enough; a `Map<String, Integer>` with occurrence counts would be overkill (D-11 says first wins, rest are errors).

## Out of Scope for Phase 54

The following belong to Phase 55 — **the planner must not schedule them into Phase 54 tasks.** This list exists to prevent scope drift.

| Artifact | Phase | Reason |
|----------|-------|--------|
| `DriverSheetImportController` (`GET /admin/drivers/import`, `POST /admin/drivers/import/preview`, `POST /admin/drivers/import/execute`) | 55 | Phase 54 is pure backend service. |
| `DriverSheetImportForm` DTO | 55 | Form binding; needs `@NotBlank` Bean Validation etc. |
| `driver-import-form.html` Thymeleaf template | 55 | UI. |
| `driver-import-preview.html` Thymeleaf template | 55 | UI. |
| "Import from Google Sheet" button on `/admin/drivers` (modify `drivers.html`) | 55 | UI. |
| `@Transactional` execute path that writes `Driver` and `SeasonDriver` | 55 | DB writes. |
| `DriverSheetImportControllerIT` integration test | 55 | Integration. |
| `admin.css` badge-style additions (if needed) | 55 | UI. |
| Flash-attribute summary rendering (`successMessage` / `errorMessage` counts) | 55 | UI. |
| `confirm_<psnId>` / `skip_<psnId>_<year>` / `accept_<psnId>_<year>` form-param convention implementation | 55 | Controller form handling. |
| Phase 55 controller-level Google Sheets error handling (`IOException` → `errorMessage` redirect) | 55 | Controller concern. |
| Decisions D-07 cross-tab dedup on commit (Driver created once across tabs) | 55 | Execute path. Phase 54 produces independent `NewDriverRow` entries per tab (D-07 locked); Phase 55 dedups on commit. |
| Decisions D-08 per-row fuzzy independence in execute | 55 | Execute path. Phase 54 surfaces the rows; Phase 55 applies per-row decisions. |
| Playwright E2E | Out of all phases | Deferred to `TEST-FUTURE-01`. |

**Phase 54 explicitly DOES deliver:**

- `SeasonRepository.findByYear(int)` repository method (single line added)
- `org.ctc.dataimport.DriverSheetImportService` class with public `DriverSheetImportPreview preview(String sheetUrl)` method
- Inner records: `DriverSheetImportPreview`, `TabPreview`, `NewDriverRow`, `NewAssignmentRow`, `ConflictRow`, `FuzzySuggestionRow`, `UnchangedRow`, `ErrorRow`
- Inner enum: `ErrorReason` with `message()` method
- `src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java` with ≥9 (target 12-15) given-when-then unit tests
- `./mvnw verify` green with ≥82% project-level line coverage

Nothing else.

## Sources

### Primary (HIGH confidence — verified by direct file reads)

- `.planning/phases/54-preview-service-row-categorization/54-CONTEXT.md` — user decisions D-01..D-13
- `.planning/REQUIREMENTS.md` — 17 Phase 54 requirements
- `.planning/ROADMAP.md` §Phase 54 — Goal and Success Criteria (SC#3 superseded)
- `.planning/config.json` — `workflow.nyquist_validation: true`
- `docs/superpowers/specs/2026-04-24-bulk-driver-import-design.md` — architecture + test list
- `CLAUDE.md` — project conventions (thin controllers, BDD naming, 82% coverage)
- `src/main/java/org/ctc/dataimport/GoogleSheetsService.java` — reuse target (read lines 1-183)
- `src/main/java/org/ctc/dataimport/DriverMatchingService.java` — reuse target (read lines 1-119)
- `src/main/java/org/ctc/dataimport/CsvImportService.java` — reuse reference (read lines 1-622; key lines 397, 554-585)
- `src/main/java/org/ctc/dataimport/CsvImportController.java` — re-fetch pattern (read lines 1-225; key lines 124-211)
- `src/main/java/org/ctc/dataimport/ScorecardParser.java` — row-reading precedent (read lines 1-182; key lines 26-72, 178-181)
- `src/main/java/org/ctc/domain/model/Season.java` — entity shape + `getDisplayLabel` computed getter (lines 99-101)
- `src/main/java/org/ctc/domain/model/SeasonDriver.java` — entity shape (unique constraint on season_id + driver_id per line 14)
- `src/main/java/org/ctc/domain/model/Driver.java` — entity shape
- `src/main/java/org/ctc/domain/repository/SeasonRepository.java` — current methods (3 existing; missing `findByYear`)
- `src/main/java/org/ctc/domain/repository/TeamRepository.java` — `findByShortName` + `findByShortNameIgnoreCase`
- `src/main/java/org/ctc/domain/repository/SeasonDriverRepository.java` — **`findBySeasonIdAndDriverId` confirmed at line 19**
- `src/test/java/org/ctc/dataimport/CsvImportServiceTest.java` — test patterns (read lines 1-1063)
- `src/test/java/org/ctc/dataimport/DriverMatchingServiceTest.java` — test patterns (read lines 1-271)
- `src/test/java/org/ctc/dataimport/GoogleSheetsServiceTest.java` — test patterns + edge-case examples
- `pom.xml` — JaCoCo configuration (lines 197-249); Surefire (lines 183-192); 82% gate (line 241); JaCoCo 0.8.14 (line 200)

### Secondary (MEDIUM confidence)

- Spring Data JPA derived-query naming conventions — CITED via existing repo methods following the same pattern (`findBy<Property>`, `findBy<Property1>And<Property2>`)
- Spring Boot 4.x BOM — training data; version transitive for Mockito/JUnit (CLAUDE.md confirms)

### Tertiary

- None — every claim in this document traces back to a source file or CONTEXT.md decision.

## Metadata

**Confidence breakdown:**

- Standard stack: **HIGH** — all libraries already on classpath (Spring Data JPA, Mockito, AssertJ, JaCoCo); no new dependencies introduced
- Architecture: **HIGH** — reuses three existing services (`GoogleSheetsService`, `DriverMatchingService`, repositories) and mirrors a well-understood inner-class data-model pattern (`CsvImportService.ImportPreview`)
- Pitfalls: **HIGH** — all 6 edge cases (Q11) answered with file-line citations; no `[ASSUMED]` tags remain on Phase-critical decisions
- Test strategy: **HIGH** — 12 scenarios enumerated with exact method names, test framework verified, mocks identified

**Research date:** 2026-04-24
**Valid until:** 2026-05-24 (30 days; dataimport package is stable — no pending refactors)

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| (none) | All claims verified by file reads or locked by CONTEXT.md | — | — |

**This table is empty** — every factual claim in this research was verified against a source file in the working directory or locked by CONTEXT.md decisions D-01..D-13. No user confirmation needed before planning.

---

*Phase: 54-preview-service-row-categorization*
*Research completed: 2026-04-24*
