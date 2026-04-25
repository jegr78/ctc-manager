# Requirements: CTC Manager

**Defined:** 2026-04-24
**Core Value:** Architectural Consistency: All controllers delegate to services, exception handling is centralized, and the production environment is secured.

## v1.8 Requirements

Requirements for the Bulk Driver Import from Google Sheets milestone. Each maps to roadmap phases. Source of truth: `docs/superpowers/specs/2026-04-24-bulk-driver-import-design.md`.

### Import Flow

- [x] **IMPORT-01**: Admin can open a new page at `/admin/drivers/import` (reachable via button on `/admin/drivers`) and submit a Google Sheet URL to load a preview
- [x] **IMPORT-02**: Preview auto-detects tabs whose name matches `^\d{4}$` and ignores all non-matching tabs
- [x] **IMPORT-03**: Preview reads columns A (`PSN ID`) and C (`Team` short code) from each relevant tab, skipping the header row
- [x] **IMPORT-04**: Each detected tab renders as its own preview section, sorted by year ascending
- [x] **IMPORT-05**: Each tab preview shows a Season dropdown auto-preselected via `SeasonRepository.findByYear(int)` (singleton→id; 0/≥2→null + ambiguousReason). [D-13 override: ROADMAP wording said findByName/findByDisplayLabel; final implementation uses findByYear because Season.name is free-text and displayLabel is computed.] Admin can override before execute
- [x] **IMPORT-06**: Execute persists Drivers and `SeasonDriver` assignments within a single `@Transactional` boundary with redirect + flash summary on success

### Row Categorization

- [x] **UX-01**: `New Drivers` bucket lists rows whose PSN ID is unknown to the system
- [x] **UX-02**: `New Assignments` bucket lists rows whose driver exists but has no `SeasonDriver` for the selected season
- [x] **UX-03**: `Conflicts` bucket lists rows whose `SeasonDriver` already exists with a different team
- [x] **UX-04**: `Fuzzy Match Suggestions` bucket lists rows whose PSN ID matches an existing driver via Levenshtein ≥0.8 (not via exact/CI/alias)
- [x] **UX-05**: `Unchanged` bucket lists rows whose driver+team assignment already matches (no-op on execute)
- [x] **UX-06**: `Errors` bucket lists rows with blank PSN ID or unknown team code; these rows are excluded from execute
- [x] **UX-07**: Each conflict row has a `Skip` checkbox; checked → existing assignment retained, unchecked (default) → overwrite with sheet value
- [x] **UX-08**: Each fuzzy match row has an `Accept` checkbox; checked → link to suggested driver, unchecked (default) → treat as new driver

### Driver Matching

- [x] **MATCH-01**: Existing `DriverMatchingService` 4-stage logic (exact → case-insensitive → alias → Levenshtein ≥0.8) is reused without modification
- [x] **MATCH-02**: Same PSN ID appearing in multiple tabs creates the `Driver` exactly once; subsequent tabs attach additional `SeasonDriver` assignments (preview-layer: same Driver id surfaces in each tab's bucket; de-duplication lives in Phase 55 execute per D-07)

### Data Integrity

- [x] **DATA-01**: Missing `Season` (neither by tab name nor via manual override) is reported as a row error; no Season is auto-created (preview surfaces ambiguousReason; error reporting final in Phase 55 UI)
- [x] **DATA-02**: Unknown team short code is reported as a row error; no `Team` is auto-created
- [x] **DATA-03**: Conflict default behavior is overwrite with sheet value; `Skip`-flagged rows leave the existing `SeasonDriver` untouched
- [x] **DATA-04**: No Flyway schema migration is introduced by this milestone (existing `Driver` + `SeasonDriver` schema is sufficient)
- [x] **DATA-05**: `RaceLineup` records are never modified by the driver import (respects RaceLineup-is-Source-of-Truth for race-level assignments)

### Testing

- [x] **TEST-01**: Unit tests cover preview categorization with ≥9 given-when-then scenarios (one per bucket + edge cases) — 16 @Test methods delivered; JaCoCo 98.9% on DriverSheetImportService
- [x] **TEST-02**: Integration tests cover the full controller flow (form → preview → execute) with mocked `GoogleSheetsService`
- [x] **TEST-03**: Project line coverage stays ≥82% after new code lands (`./mvnw verify` JaCoCo gate passes)

### Code Quality

- [x] **QUAL-01**: Admin templates use CSS classes from `admin.css`; no inline styles on buttons or category badges
- [x] **QUAL-02**: Controller delegates to `DriverSheetImportService`; no business logic, repository calls, or Google Sheets I/O in the controller
- [x] **QUAL-03**: Form binding uses `@RequestParam` primitives + `Map<String, String>` for dynamic per-row params; no JPA entity `@ModelAttribute` binding [D-15 override: original wording said `DriverSheetImportForm` DTO; final implementation uses `@RequestParam` because the form has only `sheetUrl` plus dynamic per-row keys (`seasonId_<year>`, `skip_<psnId>_<year>`, `accept_<psnId>_<year>`) that don't fit a static DTO shape — mirrors `CsvImportController` precedent]
- [x] **QUAL-04**: Preview-state persistence between preview and execute follows the existing `CsvImportService` pattern (no new parallel mechanism)

## Validated Requirements (Previous Milestones)

Validated requirements from v1.0 through v1.6 are recorded in `PROJECT.md`. See that file for the historical validated list.

## Future Requirements

Deferred to future releases. Tracked but not in current roadmap.

### Extended Import Sources

- **IMPORT-FUTURE-01**: Bulk import from uploaded CSV file (for teams without Google Sheets access)
- **IMPORT-FUTURE-02**: Scheduled re-sync with the canonical Google Sheet (detect drift)

### Import-time Enrichment

- **IMPORT-FUTURE-03**: Capture alias entries automatically when a CI-match updates casing (e.g. `art_hockers` → `ART_Hockers`)
- **IMPORT-FUTURE-04**: Allow admin to enter a richer nickname during preview (default remains `psnId`)

### E2E Coverage

- **TEST-FUTURE-01**: Playwright E2E for the import flow once a stable mock strategy for the Google Sheets API is in place

## Out of Scope

| Feature | Reason |
|---------|--------|
| Auto-create missing Seasons | Contradicts "No Fallback Calculations" — Seasons need editorial definition (dates, structure) |
| Auto-create missing Teams | Contradicts "No Fallback Calculations" — Teams need name, logo, colors curated by admin |
| Modify RaceLineup | Race-level assignments remain the responsibility of CSV race import (RaceLineup is source of truth) |
| Support non-year tab names | Out-of-scope tabs (e.g. `AHR` team sheet) have inconsistent structure; year tabs are canonical |
| Schema changes | No new Driver/SeasonDriver fields needed; reuse existing entities |
| Playwright E2E | Google Sheets mocking through Playwright is fragile; unit + integration tests cover the controller contract |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| IMPORT-01 | Phase 55 | Verified |
| IMPORT-02 | Phase 54 | Verified |
| IMPORT-03 | Phase 54 | Verified |
| IMPORT-04 | Phase 54 | Verified |
| IMPORT-05 | Phase 54 | Verified |
| IMPORT-06 | Phase 55 | Verified |
| UX-01 | Phase 54 | Verified |
| UX-02 | Phase 54 | Verified |
| UX-03 | Phase 54 | Verified |
| UX-04 | Phase 54 | Verified |
| UX-05 | Phase 54 | Verified |
| UX-06 | Phase 54 | Verified |
| UX-07 | Phase 55 | Verified |
| UX-08 | Phase 55 | Verified |
| MATCH-01 | Phase 54 | Verified |
| MATCH-02 | Phase 54 | Verified |
| DATA-01 | Phase 54 | Verified |
| DATA-02 | Phase 54 | Verified |
| DATA-03 | Phase 55 | Verified |
| DATA-04 | Phase 54 | Verified |
| DATA-05 | Phase 54 | Verified |
| TEST-01 | Phase 54 | Verified |
| TEST-02 | Phase 55 | Verified |
| TEST-03 | Phase 55 | Verified |
| QUAL-01 | Phase 55 | Verified |
| QUAL-02 | Phase 55 | Verified |
| QUAL-03 | Phase 55 | Verified |
| QUAL-04 | Phase 55 | Verified |

**Coverage:**

- v1.8 requirements: 28 total (6 Import Flow + 8 Row Categorization + 2 Matching + 5 Data Integrity + 3 Testing + 4 Code Quality)
- Phase mapping: 28/28 mapped (Phase 54: 17, Phase 55: 11) — no orphans, no duplicates

---

*Requirements defined: 2026-04-24 — derived from docs/superpowers/specs/2026-04-24-bulk-driver-import-design.md*
*Traceability updated: 2026-04-24 — phases mapped to Phase 54 (Preview Service) and Phase 55 (Admin Import UI & Execute)*
