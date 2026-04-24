# Roadmap: CTC Manager

## Milestones

- :white_check_mark: **v1.0 Technical Debt Cleanup** — Phases 1-5 (shipped 2026-04-04)
- :white_check_mark: **v1.1 Codebase Concerns Cleanup** — Phases 6-15 (shipped 2026-04-07)
- :white_check_mark: **v1.3 English Test Data** — Phases 20-27 (shipped 2026-04-10)
- :white_check_mark: **v1.5 Code Review Fixes** — Phases 28-36 (shipped 2026-04-15)
- :white_check_mark: **v1.6 Static Site Quality** — Phases 37-53 (shipped 2026-04-18)
- **v1.8 Bulk Driver Import from Google Sheets** — Phases 54-55 (in progress)

## Phases

<details>
<summary>v1.0 Technical Debt Cleanup (Phases 1-5) -- SHIPPED 2026-04-04</summary>

- [x] Phase 1: Exception Infrastructure (2/2 plans) -- completed 2026-04-03
- [x] Phase 2: Service Layer Extraction (4/4 plans) -- completed 2026-04-04
- [x] Phase 3: God Service Split (2/2 plans) -- completed 2026-04-04
- [x] Phase 4: Database Optimization (1/1 plan) -- completed 2026-04-04
- [x] Phase 5: Security (3/3 plans) -- completed 2026-04-04

</details>

<details>
<summary>v1.1 Codebase Concerns Cleanup (Phases 6-15) -- SHIPPED 2026-04-07</summary>

- [x] Phase 6: Security Hardening (1/1 plan) -- completed 2026-04-04
- [x] Phase 7: Layer Cleanup (3/3 plans) -- completed 2026-04-05
- [x] Phase 8: Exception Refinement (2/2 plans) -- completed 2026-04-05
- [x] Phase 9: Alltime Standings (1/1 plan) -- completed 2026-04-05
- [x] Phase 10: Service Refactoring (3/3 plans) -- completed 2026-04-06
- [x] Phase 11: Template Quality (3/3 plans) -- completed 2026-04-06
- [x] Phase 12: Security Hardening Recovery (1/1 plan) -- completed 2026-04-06
- [x] Phase 13: Layer Cleanup Recovery (3/3 plans) -- completed 2026-04-06
- [x] Phase 14: Exception Refinement Recovery (2/2 plans) -- completed 2026-04-07
- [x] Phase 15: Alltime Standings Recovery (1/1 plan) -- completed 2026-04-07

See: milestones/v1.1-ROADMAP.md for full details

</details>

<details>
<summary>v1.3 English Test Data (Phases 20-27) -- SHIPPED 2026-04-10</summary>

- [x] Phase 20: English Messages -- completed 2026-04-08
- [x] Phase 21: English Code -- completed 2026-04-09
- [x] Phase 22: Dev Teams & Drivers -- completed 2026-04-09
- [x] Phase 23: Dev Seasons with Results -- completed 2026-04-10
- [x] Phase 24: Restore Fictive Dev Data -- completed 2026-04-10
- [x] Phase 25: Fix I18N Regressions -- completed 2026-04-10
- [x] Phase 26: Restore Fictive Team Logos -- completed 2026-04-10
- [x] Phase 27: Restore Matchday/Result Seed Pipeline -- completed 2026-04-10

</details>

<details>
<summary>v1.5 Code Review Fixes (Phases 28-36) -- SHIPPED 2026-04-15</summary>

- [x] Phase 28: RaceAttachment Security (1/1 plan) -- completed 2026-04-13
- [x] Phase 29: Mass Assignment Fix (1/1 plan) -- completed 2026-04-13
- [x] Phase 30: CSRF and Template Security (2/2 plans) -- completed 2026-04-13
- [x] Phase 31: Null Safety and Transaction Fix (2/2 plans) -- completed 2026-04-13
- [x] Phase 32: Layering and Exception Fix (2/2 plans) -- completed 2026-04-13
- [x] Phase 33: Controller Cleanup (2/2 plans) -- completed 2026-04-14
- [x] Phase 34: Convention Fixes (2/2 plans) -- completed 2026-04-14
- [x] Phase 35: Site Generator Bye-Race Null Safety (1/1 plan) -- completed 2026-04-14
- [x] Phase 36: Audit Remediation (1/1 plan) -- completed 2026-04-14

See: milestones/v1.5-ROADMAP.md for full details

</details>

<details>
<summary>v1.6 Static Site Quality (Phases 37-53) -- SHIPPED 2026-04-18</summary>

- [x] Phase 37: Critical Link Fixes (2/2 plans) -- completed 2026-04-16
- [x] Phase 38: Season Content & Data Filtering (3/3 plans) -- completed 2026-04-16
- [x] Phase 39: Entity Cross-Linking (2/2 plans) -- completed 2026-04-16
- [x] Phase 40: Navigation & Structure (2/2 plans) -- completed 2026-04-16
- [x] Phase 41: UX Polish & Accessibility (2/2 plans) -- completed 2026-04-16
- [x] Phase 42: Navigation Gap Closure (1/1 plan) -- completed 2026-04-16
- [x] Phase 43: Code Quality Cleanup (1/1 plan) -- completed 2026-04-16
- [x] Phase 44: Clean Output Directory (2/2 plans) -- completed 2026-04-16
- [x] Phase 45: Footer YouTube Link (2/2 plans) -- completed 2026-04-16
- [x] Phase 46: Configurable Links Page (2/2 plans) -- completed 2026-04-17
- [x] Phase 47: Teams & Drivers Overview Pages (2/2 plans) -- completed 2026-04-17
- [x] Phase 48: Landing Page Redesign (2/2 plans) -- completed 2026-04-17
- [x] Phase 49: E2E Site Validation (1/1 plan) -- completed 2026-04-17
- [x] Phase 50: Site Generator Test Robustness (1/1 plan) -- completed 2026-04-17
- [x] Phase 51: YouTube Hero Video (1/1 plan) -- completed 2026-04-17
- [x] Phase 52: Alltime Pages (2/2 plans) -- completed 2026-04-18
- [x] Phase 53: Documentation & Code Cleanup (1/1 plan) -- completed 2026-04-18

</details>

### v1.8 Bulk Driver Import from Google Sheets

**Milestone Goal:** Provide admins a two-phase bulk import (Preview -> Execute) that seeds `Driver` records and `SeasonDriver` assignments from a curated Google Sheet with per-year tabs, reusing the existing CSV-import pattern (`GoogleSheetsService`, `DriverMatchingService`, `CsvImportService` preview-state).

- [ ] **Phase 54: Preview Service & Row Categorization** - Backend service that fetches year-numbered tabs, categorizes rows into six buckets, and is fully unit-tested
- [ ] **Phase 55: Admin Import UI & Transactional Execute** - Controller, form DTO, templates, entry button, and transactional execute path with integration coverage

## Phase Details

### Phase 54: Preview Service & Row Categorization

**Goal**: A backend service exists that, given a Google Sheet URL, returns a structured preview categorizing every relevant row into one of six buckets, with no DB writes
**Depends on**: Phase 53 (previous milestone complete)
**Requirements**: IMPORT-02, IMPORT-03, IMPORT-04, IMPORT-05, UX-01, UX-02, UX-03, UX-04, UX-05, UX-06, MATCH-01, MATCH-02, DATA-01, DATA-02, DATA-04, DATA-05, TEST-01

**Success Criteria** (what must be TRUE):

1. `DriverSheetImportService.preview(sheetUrl)` returns a `DriverSheetImportPreview` containing exactly one `TabPreview` per sheet tab whose name matches `^\d{4}$`, sorted ascending by year, and ignores all non-matching tabs
2. For each tab, every non-header row from columns A (PSN ID) and C (Team short code) is categorized into exactly one of: `NEW_DRIVER`, `NEW_ASSIGNMENT`, `CONFLICT`, `FUZZY_SUGGESTION`, `UNCHANGED`, `ERROR`, matching the definitions in UX-01..06
3. Each `TabPreview` carries a `suggestedSeasonId` resolved via `SeasonRepository.findByName(tabName)` with fallback to `findByDisplayLabel(tabName)`; null when neither matches
4. Driver matching delegates to the existing `DriverMatchingService` 4-stage logic (exact -> case-insensitive -> alias -> Levenshtein >=0.8) without modifying that service, and the same PSN ID across multiple tabs resolves to a single `Driver` identity in the preview model
5. Rows with blank PSN ID or unknown team short code are categorized as `ERROR` and carry a human-readable reason; no auto-create of `Season` or `Team` is attempted
6. `DriverSheetImportServiceTest` covers preview categorization with at least 9 given-when-then scenarios (one per bucket plus tab-filtering, cross-tab dedup, season-auto-match edge cases) and all assertions pass under `./mvnw verify`

**Plans**: 1 plan
- [x] 54-01-PLAN.md — Preview service vertical slice: SeasonRepository.findByYear(int) + DriverSheetImportService (7 inner records + ErrorReason enum + D-12 waterfall preview method) + DriverSheetImportServiceTest (13 given-when-then scenarios) + JaCoCo 82% gate

### Phase 55: Admin Import UI & Transactional Execute

**Goal**: An admin can click a button on `/admin/drivers`, submit a Sheet URL, review the per-tab preview with override controls, and execute the import transactionally with a flash summary
**Depends on**: Phase 54
**Requirements**: IMPORT-01, IMPORT-06, UX-07, UX-08, DATA-03, TEST-02, TEST-03, QUAL-01, QUAL-02, QUAL-03, QUAL-04

**Success Criteria** (what must be TRUE):

1. Admin navigating to `/admin/drivers` sees an "Import from Google Sheet" button (styled via CSS classes from `admin.css`, no inline styles) that links to `/admin/drivers/import`; submitting the Sheet URL form renders a per-tab preview page with one section per year-tab, each showing a pre-selected Season dropdown and six categorized row buckets with counts
2. On the preview page, every `CONFLICT` row has a `Skip` checkbox (unchecked = overwrite with sheet value, checked = retain existing `SeasonDriver`), and every `FUZZY_SUGGESTION` row has an `Accept` checkbox (unchecked = treat as new driver, checked = link to suggested existing driver)
3. Clicking Execute performs all Driver creations and `SeasonDriver` upserts inside a single `@Transactional` boundary, then redirects to `/admin/drivers` with a flash summary listing counts of created drivers, new assignments, overwritten assignments, skipped conflicts, unchanged rows, and errors; `RaceLineup` records remain untouched
4. The controller contains no business logic, no repository calls, and no Google Sheets I/O - it delegates all work to `DriverSheetImportService`; form binding uses the `DriverSheetImportForm` DTO, never a direct JPA entity `@ModelAttribute`; preview-state persistence between preview and execute follows the exact pattern used by `CsvImportController`/`CsvImportService` (no new parallel mechanism)
5. `DriverSheetImportControllerIT` exercises the full `GET /admin/drivers/import` -> `POST /preview` -> `POST /execute` flow with a mocked `GoogleSheetsService` and asserts DB state plus flash attributes; `./mvnw verify` passes the JaCoCo 82% line-coverage gate with the new code included

**Plans**: TBD
**UI hint**: yes

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. Exception Infrastructure | v1.0 | 2/2 | Complete | 2026-04-03 |
| 2. Service Layer Extraction | v1.0 | 4/4 | Complete | 2026-04-04 |
| 3. God Service Split | v1.0 | 2/2 | Complete | 2026-04-04 |
| 4. Database Optimization | v1.0 | 1/1 | Complete | 2026-04-04 |
| 5. Security | v1.0 | 3/3 | Complete | 2026-04-04 |
| 6. Security Hardening | v1.1 | 1/1 | Complete | 2026-04-04 |
| 7. Layer Cleanup | v1.1 | 3/3 | Complete | 2026-04-05 |
| 8. Exception Refinement | v1.1 | 2/2 | Complete | 2026-04-05 |
| 9. Alltime Standings | v1.1 | 1/1 | Complete | 2026-04-05 |
| 10. Service Refactoring | v1.1 | 3/3 | Complete | 2026-04-06 |
| 11. Template Quality | v1.1 | 3/3 | Complete | 2026-04-06 |
| 12. Security Hardening Recovery | v1.1 | 1/1 | Complete | 2026-04-06 |
| 13. Layer Cleanup Recovery | v1.1 | 3/3 | Complete | 2026-04-06 |
| 14. Exception Refinement Recovery | v1.1 | 2/2 | Complete | 2026-04-07 |
| 15. Alltime Standings Recovery | v1.1 | 1/1 | Complete | 2026-04-07 |
| 20. English Messages | v1.3 | — | Complete | 2026-04-08 |
| 21. English Code | v1.3 | — | Complete | 2026-04-09 |
| 22. Dev Teams & Drivers | v1.3 | — | Complete | 2026-04-09 |
| 23. Dev Seasons with Results | v1.3 | — | Complete | 2026-04-10 |
| 24. Restore Fictive Dev Data | v1.3 | 1/1 | Complete | 2026-04-10 |
| 25. Fix I18N Regressions | v1.3 | 1/1 | Complete | 2026-04-10 |
| 26. Restore Fictive Team Logos | v1.3 | 1/1 | Complete | 2026-04-10 |
| 27. Restore Matchday/Result Seed Pipeline | v1.3 | 1/1 | Complete | 2026-04-10 |
| 28. RaceAttachment Security | v1.5 | 1/1 | Complete | 2026-04-13 |
| 29. Mass Assignment Fix | v1.5 | 1/1 | Complete | 2026-04-13 |
| 30. CSRF and Template Security | v1.5 | 2/2 | Complete | 2026-04-13 |
| 31. Null Safety and Transaction Fix | v1.5 | 2/2 | Complete | 2026-04-13 |
| 32. Layering and Exception Fix | v1.5 | 2/2 | Complete | 2026-04-13 |
| 33. Controller Cleanup | v1.5 | 2/2 | Complete | 2026-04-14 |
| 34. Convention Fixes | v1.5 | 2/2 | Complete | 2026-04-14 |
| 35. Site Generator Bye-Race Null Safety | v1.5 | 1/1 | Complete | 2026-04-14 |
| 36. Audit Remediation | v1.5 | 1/1 | Complete | 2026-04-14 |
| 37. Critical Link Fixes | v1.6 | 2/2 | Complete | 2026-04-16 |
| 38. Season Content & Data Filtering | v1.6 | 3/3 | Complete | 2026-04-16 |
| 39. Entity Cross-Linking | v1.6 | 2/2 | Complete | 2026-04-16 |
| 40. Navigation & Structure | v1.6 | 2/2 | Complete | 2026-04-16 |
| 41. UX Polish & Accessibility | v1.6 | 2/2 | Complete | 2026-04-16 |
| 42. Navigation Gap Closure | v1.6 | 1/1 | Complete | 2026-04-16 |
| 43. Code Quality Cleanup | v1.6 | 1/1 | Complete | 2026-04-16 |
| 44. Clean Output Directory | v1.6 | 2/2 | Complete | 2026-04-16 |
| 45. Footer YouTube Link | v1.6 | 2/2 | Complete | 2026-04-16 |
| 46. Configurable Links Page | v1.6 | 2/2 | Complete | 2026-04-17 |
| 47. Teams & Drivers Overview Pages | v1.6 | 2/2 | Complete | 2026-04-17 |
| 48. Landing Page Redesign | v1.6 | 2/2 | Complete | 2026-04-17 |
| 49. E2E Site Validation | v1.6 | 1/1 | Complete | 2026-04-17 |
| 50. Site Generator Test Robustness | v1.6 | 1/1 | Complete | 2026-04-17 |
| 51. YouTube Hero Video | v1.6 | 1/1 | Complete | 2026-04-17 |
| 52. Alltime Pages | v1.6 | 2/2 | Complete | 2026-04-18 |
| 53. Documentation & Code Cleanup | v1.6 | 1/1 | Complete | 2026-04-18 |
| 54. Preview Service & Row Categorization | v1.8 | 1/1 | In progress (awaiting verification) | — |
| 55. Admin Import UI & Transactional Execute | v1.8 | 0/? | Not started | — |
