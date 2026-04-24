# Requirements: CTC Manager

**Defined:** 2026-04-24
**Core Value:** Architectural Consistency: All controllers delegate to services, exception handling is centralized, and the production environment is secured.

## v1.8 Requirements

Requirements for the Bulk Driver Import from Google Sheets milestone. Each maps to roadmap phases. Source of truth: `docs/superpowers/specs/2026-04-24-bulk-driver-import-design.md`.

### Import Flow

- [ ] **IMPORT-01**: Admin can open a new page at `/admin/drivers/import` (reachable via button on `/admin/drivers`) and submit a Google Sheet URL to load a preview
- [ ] **IMPORT-02**: Preview auto-detects tabs whose name matches `^\d{4}$` and ignores all non-matching tabs
- [ ] **IMPORT-03**: Preview reads columns A (`PSN ID`) and C (`Team` short code) from each relevant tab, skipping the header row
- [ ] **IMPORT-04**: Each detected tab renders as its own preview section, sorted by year ascending
- [ ] **IMPORT-05**: Each tab preview shows a Season dropdown auto-preselected to the `Season` matching by `name` or `displayLabel`; admin can override before execute
- [ ] **IMPORT-06**: Execute persists Drivers and `SeasonDriver` assignments within a single `@Transactional` boundary with redirect + flash summary on success

### Row Categorization

- [ ] **UX-01**: `New Drivers` bucket lists rows whose PSN ID is unknown to the system
- [ ] **UX-02**: `New Assignments` bucket lists rows whose driver exists but has no `SeasonDriver` for the selected season
- [ ] **UX-03**: `Conflicts` bucket lists rows whose `SeasonDriver` already exists with a different team
- [ ] **UX-04**: `Fuzzy Match Suggestions` bucket lists rows whose PSN ID matches an existing driver via Levenshtein ≥0.8 (not via exact/CI/alias)
- [ ] **UX-05**: `Unchanged` bucket lists rows whose driver+team assignment already matches (no-op on execute)
- [ ] **UX-06**: `Errors` bucket lists rows with blank PSN ID or unknown team code; these rows are excluded from execute
- [ ] **UX-07**: Each conflict row has a `Skip` checkbox; checked → existing assignment retained, unchecked (default) → overwrite with sheet value
- [ ] **UX-08**: Each fuzzy match row has an `Accept` checkbox; checked → link to suggested driver, unchecked (default) → treat as new driver

### Driver Matching

- [ ] **MATCH-01**: Existing `DriverMatchingService` 4-stage logic (exact → case-insensitive → alias → Levenshtein ≥0.8) is reused without modification
- [ ] **MATCH-02**: Same PSN ID appearing in multiple tabs creates the `Driver` exactly once; subsequent tabs attach additional `SeasonDriver` assignments

### Data Integrity

- [ ] **DATA-01**: Missing `Season` (neither by tab name nor via manual override) is reported as a row error; no Season is auto-created
- [ ] **DATA-02**: Unknown team short code is reported as a row error; no `Team` is auto-created
- [ ] **DATA-03**: Conflict default behavior is overwrite with sheet value; `Skip`-flagged rows leave the existing `SeasonDriver` untouched
- [ ] **DATA-04**: No Flyway schema migration is introduced by this milestone (existing `Driver` + `SeasonDriver` schema is sufficient)
- [ ] **DATA-05**: `RaceLineup` records are never modified by the driver import (respects RaceLineup-is-Source-of-Truth for race-level assignments)

### Testing

- [ ] **TEST-01**: Unit tests cover preview categorization with ≥9 given-when-then scenarios (one per bucket + edge cases)
- [ ] **TEST-02**: Integration tests cover the full controller flow (form → preview → execute) with mocked `GoogleSheetsService`
- [ ] **TEST-03**: Project line coverage stays ≥82% after new code lands (`./mvnw verify` JaCoCo gate passes)

### Code Quality

- [ ] **QUAL-01**: Admin templates use CSS classes from `admin.css`; no inline styles on buttons or category badges
- [ ] **QUAL-02**: Controller delegates to `DriverSheetImportService`; no business logic, repository calls, or Google Sheets I/O in the controller
- [ ] **QUAL-03**: Form binding uses `DriverSheetImportForm` DTO; no direct JPA entity `@ModelAttribute` binding
- [ ] **QUAL-04**: Preview-state persistence between preview and execute follows the existing `CsvImportService` pattern (no new parallel mechanism)

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
| IMPORT-01 | Phase 55 | Pending |
| IMPORT-02 | Phase 54 | Pending |
| IMPORT-03 | Phase 54 | Pending |
| IMPORT-04 | Phase 54 | Pending |
| IMPORT-05 | Phase 54 | Pending |
| IMPORT-06 | Phase 55 | Pending |
| UX-01 | Phase 54 | Pending |
| UX-02 | Phase 54 | Pending |
| UX-03 | Phase 54 | Pending |
| UX-04 | Phase 54 | Pending |
| UX-05 | Phase 54 | Pending |
| UX-06 | Phase 54 | Pending |
| UX-07 | Phase 55 | Pending |
| UX-08 | Phase 55 | Pending |
| MATCH-01 | Phase 54 | Pending |
| MATCH-02 | Phase 54 | Pending |
| DATA-01 | Phase 54 | Pending |
| DATA-02 | Phase 54 | Pending |
| DATA-03 | Phase 55 | Pending |
| DATA-04 | Phase 54 | Pending |
| DATA-05 | Phase 54 | Pending |
| TEST-01 | Phase 54 | Pending |
| TEST-02 | Phase 55 | Pending |
| TEST-03 | Phase 55 | Pending |
| QUAL-01 | Phase 55 | Pending |
| QUAL-02 | Phase 55 | Pending |
| QUAL-03 | Phase 55 | Pending |
| QUAL-04 | Phase 55 | Pending |

**Coverage:**

- v1.8 requirements: 28 total (6 Import Flow + 8 Row Categorization + 2 Matching + 5 Data Integrity + 3 Testing + 4 Code Quality)
- Phase mapping: 28/28 mapped (Phase 54: 17, Phase 55: 11) — no orphans, no duplicates

---

*Requirements defined: 2026-04-24 — derived from docs/superpowers/specs/2026-04-24-bulk-driver-import-design.md*
*Traceability updated: 2026-04-24 — phases mapped to Phase 54 (Preview Service) and Phase 55 (Admin Import UI & Execute)*
