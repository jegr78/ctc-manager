# Bulk Driver Import from Google Sheets — Design

## Context

The CTC league maintains a canonical Google Sheet with historical driver rosters on per-year tabs (currently `2023`, `2024`, `2025`, plus additional non-year tabs that are out of scope). Each relevant tab has the same structure:

- Column **A**: `PSN ID`
- Column **C**: `Team` (short code, e.g. `AHR`, `ART`, `CLR`, `DTR`)

This data is not yet in CTC Manager. The feature seeds two things from the sheet:

1. `Driver` records (one per distinct PSN ID, tab-übergreifend dedupliziert — many drivers appear in multiple years).
2. `SeasonDriver` assignments per tab-year, linking driver → team for that season.

`Season` and `Team` records are assumed to exist already. Rows pointing at a missing season or team are reported as errors rather than auto-creating stub records (consistent with the "No Fallback Calculations" principle in `CLAUDE.md`).

## Goals / Non-Goals

**Goals**
- Idempotent driver import — same PSN ID across multiple tabs creates the Driver once.
- Per-tab `SeasonDriver(season, driver, team)` creation or update.
- Two-phase flow (Preview → Execute) with per-tab preview sections so the user can review and intervene before any DB write.
- Reuse existing infrastructure: `GoogleSheetsService`, `DriverMatchingService`, and the `CsvImportService` preview-pattern.

**Non-Goals**
- No auto-creation of missing `Season` or `Team` records.
- No modification of `RaceLineup` (race-level assignments continue to flow through CSV race import, which per `CLAUDE.md` is the source of truth).
- Non-year tabs are filtered out (regex `^\d{4}$`).

## User Flow

1. `/admin/drivers` gains a new button **"Import from Google Sheet"**.
2. Button → `/admin/drivers/import` (GET): form with a single field **Sheet URL**.
3. Submit → `POST /admin/drivers/import/preview`:
   - Server fetches all year-numbered tabs via `GoogleSheetsService.getSheetNames()` and `readRangeFromSheet(..., "A:C")`.
   - Renders **one preview section per tab**, in ascending year order.
4. Per-tab preview section contains:
   - **Season dropdown** — auto-preselected to the `Season` whose `name` or `displayLabel` matches the tab name; user may override.
   - **Row buckets** (with counts):
     - `New Drivers` — PSN ID unknown, will be created.
     - `New Assignments` — Driver exists, no current `SeasonDriver` for this season.
     - `Conflicts` — `SeasonDriver` exists with a different team. Default action: **overwrite with sheet value**, with per-row `Skip` checkbox to retain the existing assignment.
     - `Fuzzy Match Suggestions` — PSN ID not an exact/CI/alias match, but a Levenshtein candidate ≥0.8 exists. Per-row `Accept suggested match` checkbox (unchecked = treat as New Driver).
     - `Unchanged` — PSN ID + team already match. No-op. Listed for transparency.
     - `Errors` — team short code unknown, empty cells, etc. Row is excluded from import.
5. User submits `POST /admin/drivers/import/execute` → transactional write → redirect to `/admin/drivers` with flash message summarising counts.

## Architecture

### New files

| Path | Purpose |
|---|---|
| `src/main/java/org/ctc/dataimport/DriverSheetImportService.java` | Preview + execute orchestration. |
| `src/main/java/org/ctc/admin/controller/DriverSheetImportController.java` | `GET /admin/drivers/import`, `POST /admin/drivers/import/preview`, `POST /admin/drivers/import/execute`. |
| `src/main/java/org/ctc/admin/dto/DriverSheetImportForm.java` | `sheetUrl` input DTO. |
| `src/main/java/org/ctc/dataimport/DriverSheetImportPreview.java` | Per-tab state; a list of `TabPreview` with categorised rows. |
| `src/main/java/org/ctc/dataimport/DriverSheetImportResult.java` | Counts + error list for flash/summary rendering. |
| `src/main/resources/templates/admin/driver-import-form.html` | Sheet-URL input. |
| `src/main/resources/templates/admin/driver-import-preview.html` | Per-tab preview with dropdowns + checkboxes. |
| `src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java` | Unit tests. |
| `src/test/java/org/ctc/admin/controller/DriverSheetImportControllerIT.java` | Integration tests with mocked `GoogleSheetsService`. |

### Modified files

| Path | Change |
|---|---|
| `src/main/resources/templates/admin/drivers.html` | Add "Import from Google Sheet" button (CSS class from `admin.css`, per No-Inline-Styles rule). |
| `src/main/resources/static/admin.css` | Add category-badge styles if reused counters require them (only if not already present). |

### Reused components (no changes)

| File | Role |
|---|---|
| `src/main/java/org/ctc/dataimport/GoogleSheetsService.java` | `extractSpreadsheetId()`, `getSheetNames()`, `readRangeFromSheet(..., "A:C")`. |
| `src/main/java/org/ctc/dataimport/DriverMatchingService.java` | 4-stage match (exact → CI → alias → Levenshtein 0.8). |
| `src/main/java/org/ctc/dataimport/CsvImportService.java` | **Reference pattern** for preview/execute/session-state; inspect before implementing to stay consistent. |
| `org.ctc.domain.repository.SeasonRepository` | `findByName(year)` + `findByDisplayLabel(year)` for the auto-suggested dropdown value. |
| `org.ctc.domain.repository.TeamRepository` | `findByShortName()` for team resolution. |
| `org.ctc.domain.repository.DriverRepository` / `DriverService` | Driver persistence. |

## Data Flow

### Preview

```
POST /admin/drivers/import/preview { sheetUrl }
  └─ DriverSheetImportService.preview(sheetUrl):
       1. spreadsheetId ← GoogleSheetsService.extractSpreadsheetId(sheetUrl)
       2. tabs ← getSheetNames(spreadsheetId).filter(^\d{4}$).sorted()
       3. For each tab:
            a. rows ← readRangeFromSheet(spreadsheetId, tab, "A:C"), header row skipped
            b. suggestedSeasonId ← SeasonRepository.findByName(tab)
                                    .or(findByDisplayLabel(tab)).map(Season::id)
            c. For each row (psnId, teamCode):
                 - Validate non-blank → else ERROR
                 - team ← TeamRepository.findByShortName(teamCode) → else ERROR
                 - driverMatch ← DriverMatchingService.findMatch(psnId)
                   · EXACT / CI / ALIAS → existingDriver
                   · FUZZY ≥0.8        → fuzzySuggestion (requires user opt-in)
                   · NONE              → newDriver
                 - existingSeasonDriver ← lookup(season, driver) if season auto-matched
                 - Bucket: NEW_DRIVER | NEW_ASSIGNMENT | CONFLICT | UNCHANGED
                           | FUZZY_SUGGESTION | ERROR
            d. Emit TabPreview { tabName, suggestedSeasonId, rowsByBucket }
       4. Return DriverSheetImportPreview { List<TabPreview> }
```

### Execute

```
POST /admin/drivers/import/execute { tabDecisions: [{ tabName, seasonId,
                                                      rowDecisions: [{psnId, teamCode,
                                                                      action, fuzzyTargetId?}] }] }
  └─ DriverSheetImportService.execute(decisions)   [@Transactional]:
       1. For each tab:
            a. season ← SeasonRepository.findById(seasonId) → else abort with error
            b. For each rowDecision where action ≠ SKIP:
                 - driver ← resolveDriver(action, psnId, fuzzyTargetId)
                            · CREATE              → new Driver(psnId, psnId)
                            · LINK_EXISTING       → DriverRepository.findByPsnIdIgnoreCase()
                            · ACCEPT_FUZZY        → DriverRepository.findById(fuzzyTargetId)
                 - team   ← TeamRepository.findByShortName(teamCode)
                 - upsert SeasonDriver(season, driver): set team
       2. Accumulate DriverSheetImportResult { createdDrivers, createdAssignments,
                                                overwrittenAssignments, skippedConflicts,
                                                unchanged, errors }
       3. Flash message summary; redirect → /admin/drivers
```

### Preview-state persistence

`CsvImportService` already persists preview state across the confirm step. Before implementing, read `CsvImportService` + `CsvImportController` to identify the exact mechanism (likely `@SessionAttributes` on the controller) and follow that pattern. Do not invent a parallel mechanism.

## Error Handling

- Preview catches all validation issues before DB writes (missing team, blank PSN ID, sheet auth errors).
- Google Sheets API failures (auth, 404, rate limit, invalid URL) → form-level error, rendered via `errorMessage` flash attribute on redirect to the import form.
- Execute runs in a single `@Transactional` boundary. Row-level validation errors do **not** roll back — they are already filtered in Preview. Unexpected runtime exceptions roll back the entire import (safe default).
- Log state changes with `log.info("Imported {} drivers, {} assignments for tab {}", ...)`; use parameterised `{}` format (per `CLAUDE.md`).

## Testing

### Unit (`DriverSheetImportServiceTest`)

Given-When-Then naming (per `CLAUDE.md` BDD convention):

- `givenMixedTabNames_whenPreview_thenOnlyFourDigitTabsIncluded`
- `givenNewPsnId_whenPreview_thenCategorisedAsNewDriver`
- `givenExistingPsnIdDifferentCase_whenPreview_thenResolvedViaCaseInsensitiveMatch`
- `givenFuzzyCandidate_whenPreview_thenSuggestedMatchAwaitsUserOptIn`
- `givenExistingSeasonDriverSameTeam_whenPreview_thenCategorisedAsUnchanged`
- `givenExistingSeasonDriverDifferentTeam_whenPreview_thenCategorisedAsConflict`
- `givenUnknownTeamCode_whenPreview_thenRowErrored`
- `givenConflictWithSkip_whenExecute_thenExistingAssignmentUntouched`
- `givenConflictWithOverwrite_whenExecute_thenTeamReplaced`
- `givenSameDriverAcrossThreeTabs_whenExecute_thenSingleDriverWithThreeSeasonDrivers`
- `givenFuzzyAccepted_whenExecute_thenLinkedToExistingDriverNoDuplicate`
- `givenFuzzyRejected_whenExecute_thenNewDriverCreated`

### Integration (`DriverSheetImportControllerIT`)

- Mock `GoogleSheetsService` to return canned tab data.
- Full request cycle: `GET /admin/drivers/import` → `POST /preview` → `POST /execute` → assert DB state + flash attributes.

### Coverage

Project minimum is **82% line coverage** (`CLAUDE.md` Constraints). New service + controller must be fully covered by unit + integration tests. `./mvnw verify` enforces via JaCoCo.

### Skip for MVP

Playwright E2E — mocking Google Sheets through Playwright is fragile and the unit + integration tests already cover the controller contract. Add later if the feature sees heavy use.

## Verification

```bash
# 1. Tests + coverage gate
./mvnw verify

# 2. Manual happy path
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo
#    → http://localhost:9090/admin/drivers
#    → "Import from Google Sheet"
#    → paste https://docs.google.com/spreadsheets/d/1E14IYf-bZfG-uegoUonnd93kfFAjiyCv54TmWXJKk1E/edit
#    → verify three tab sections render (2023, 2024, 2025) with auto-selected seasons
#    → toggle a conflict skip, accept/reject a fuzzy suggestion, override one season
#    → Execute → flash summary + DB populated

# 3. Visual check (CLAUDE.md mandates playwright-cli for UI changes)
playwright-cli open http://localhost:9090/admin/drivers/import   # desktop
playwright-cli open http://localhost:9090/admin/drivers/import --mobile
```

## Assumptions / Open Points for Implementation

- **Session-state mechanism between preview and execute:** follow `CsvImportService`/`CsvImportController` precedent; do not introduce a new pattern.
- **Season lookup field:** tab `2024` matches `Season.name == "2024"` OR `Season.displayLabel == "2024"` — implementer should verify which field is typically populated for year-labelled seasons by inspecting existing rows and PR #111.
- **Column reading range:** `A:C` keeps the hidden column B inert and future-proofs if additional columns are added.
- **Flyway:** this feature has no schema changes. If any derived counters or caches warrant a column, they belong in a new `V{N}__...sql` migration — do **not** touch existing V1.
