# Milestones

## v1.8 Bulk Driver Import from Google Sheets (Shipped: 2026-04-25)

**Phases completed:** 2 phases, 4 plans, ~12 tasks
**PR:** [#116](https://github.com/jegr78/ctc-manager/pull/116) (squash-merged as `042cfbf`)
**Diff:** +11 246 / −539 across 39 files
**Tests:** 1064 total project-wide (+52 from baseline 1011)
**Timeline:** 2 days (2026-04-24 → 2026-04-25)

**Key accomplishments:**

- Stateless preview service (`DriverSheetImportService.preview()`) categorizing Google Sheets driver rows into 6 typed buckets via D-12 waterfall with `SeasonRepository.findByYear(int)` auto-match — 16 unit tests, 98.9% line coverage
- `@Transactional execute()` method with 6-bucket walk, cross-tab driver dedup, per-row Skip/Accept decisions, mutable `ExecuteResult` accumulator — `IOException` wrapped as `IllegalStateException` for proper rollback semantics
- Thin Spring MVC `DriverSheetImportController` (3 handlers: GET form, POST preview, POST execute) + 2 Thymeleaf templates with 6 bucket tables and Skip/Accept checkboxes + entry button on `/admin/drivers` toolbar — zero business logic, zero inline styles, zero `th:utext`
- 21 integration tests (17 happy-path + 4 exception-path) exercising the full GET/POST-preview/POST-execute flow with `@MockitoBean GoogleSheetsService`; JaCoCo 82% line gate met
- Code review found 1 critical (per-tab cache key for FUZZY/accept) + 3 warnings (exception leakage, missing `@Transactional(readOnly=true)`, dead test setup) — all auto-fixed in 4 atomic commits
- Reuse pattern reinforced: `GoogleSheetsService`, `DriverMatchingService` (4-stage fuzzy), and `CsvImportController` preview-state pattern reused without modification — no parallel infrastructure introduced
- Form-binding contract evolved (D-15 override): per-row dynamic keys (`seasonId_<year>`, `skip_<psnId>_<year>`, `accept_<psnId>_<year>`) bound via `@RequestParam` + `Map<String,String>` instead of static DTO

**Known tech debt:**

1. D-15 wording carryover in REQUIREMENTS.md QUAL-03 (override accepted, documented in PROJECT.md)
2. UAT 3 (ambiguous-season banner) verified by template inspection + integration test instead of live Google-Sheet render

---

## v1.5 Code Review Fixes (Shipped: 2026-04-15)

**Phases completed:** 9 phases, 14 plans, 18 tasks

**Key accomplishments:**

- 1. [Rule 1 - Bug] Corrected test assertion for header injection check
- MatchdayController mass assignment vulnerability eliminated by replacing `@ModelAttribute Matchday` (JPA entity) with `@ModelAttribute("form") MatchdayForm` DTO containing only 4 user-editable fields
- layout.html
- One-liner:
- One-liner:
- RaceGraphicService relocated from domain.service to admin.service and TeamCardService decoupled from RaceService — zero admin imports now remain in the domain layer
- One-liner:
- Season grouping/sorting, matchday graphic status computation, and driver merge filtering extracted from three controllers into their respective service methods using TDD
- SiteGeneratorService.toRaceView() now resolves driver-team assignments from RaceLineup entries first, falling back to SeasonDriver only when no lineup entry exists — matching the canonical pattern from RaceFormDataService
- One-liner:
- 1. classList.add count is 2, not 3 as plan stated

---

## v1.3 English Test Data (Shipped: 2026-04-10)

**Phases completed:** 8 phases, 9 plans, 17 tasks

**Key accomplishments:**

- One-liner:
- Replaced 26 German test-data strings and 3 HTML comments with English equivalents, completing the codebase English cleanup started in Phase 20
- TestDataService refactored with 10 fictive racing teams (17 total with sub-teams), 100 fictive drivers (10 per team), and TeamCardService integration with graceful Playwright fallback
- 1. [Rule 1 - Bug] Added @Transactional to integration test class
- 1. [Rule 1 - Bug] JPA flush + detach for aggregateMatchScores
- 1. [Rule 1 - Bug] Fixed integration test assertions for shared H2 database
- One-liner:
- Commit:

---

## v1.2 Driver Merge (Shipped: 2026-04-07)

**Phases completed:** 4 phases (16-19), 5 plans
**Timeline:** 11 days (2026-03-27 → 2026-04-07)
**Requirements:** 14/14 satisfied

**Key accomplishments:**

- Transactional DriverMergeService with FK reassignment across SeasonDriver, RaceLineup, RaceResult, and PsnAlias tables — source PSN-ID preserved as alias on target
- Proactive duplicate detection for all 3 FK tables — source entries dropped instead of reassigned when target already exists in the same season/race, preventing unique-constraint violations
- Read-only MergePreview with per-table reference and duplicate counts for informed merge decisions
- Full merge UI workflow: merge button on driver detail, target selection dropdown, preview table, JavaScript confirm dialog
- Graceful error handling on all merge endpoints with flash redirect (matching executeMerge pattern)

**Known tech debt:** 5 human visual verification items (Phase 18 merge UI), REQUIREMENTS.md checkboxes not updated

---

## v1.1 Codebase Concerns Cleanup (Shipped: 2026-04-07)

**Phases completed:** 10 phases (6-15), 20 plans, 820 tests
**Timeline:** 4 days (2026-04-04 → 2026-04-07)
**Requirements:** 12/12 satisfied

**Key accomplishments:**

- SSRF hostname blocklist + path traversal defense for all FileStorageService write methods (SECU-01, SECU-02)
- 5 controllers use services only, 10 domain services decoupled from admin DTOs (ARCH-01, ARCH-02)
- 25+ catch(Exception e) blocks narrowed to specific exception types across controllers and services (ERRH-01)
- Cross-season alltime standings aggregation with sub-team resolution (FEAT-01)
- TemplateEditorController generic dispatch via TemplateManageable interface (ARCH-03)
- PlayoffService + RaceService split into focused units (ARCH-04, ARCH-05)
- Inline styles replaced with CSS utility classes across all admin templates (QUAL-01)
- Unbounded findAll() scoped or documented (QUAL-02)

**Known tech debt:** 9 human visual verification items (template editor, admin pages after refactor)

---

## v1.0 Technical Debt Cleanup (Shipped: 2026-04-04)

**Phases completed:** 5 phases, 12 plans, 16 tasks

**Key accomplishments:**

- 3 typed exception classes, GlobalExceptionHandler with @ControllerAdvice handling 6 exception types, and admin error page within layout with profile-aware detail display
- Migrated all 135 orElseThrow calls across 21 production files to EntityNotFoundException/ValidationException with entity type and ID in every message
- Extracted 9 methods into 2 focused services from RaceManagementService, reducing dependencies from 20 to 14 with DRY GraphicGenerator pattern
- Renamed God Service to RaceService and rewired RaceController to 3 direct service injections, completing the service split with 744 tests passing
- Flyway V2 migration with 36 FK indexes and 28 @EntityGraph annotations across 11 repositories to eliminate N+1 queries
- SSRF protection via HTTPS-only guard clause in FileStorageService.storeFromUrl() with 4 new unit tests
- Spring Security HTTP Basic Auth for prod/docker with profile-conditional SecurityFilterChain, dev/local permit-all, all 664 tests green
- 403 access-denied page in admin layout with Docker credential wiring for local (admin/ctc-admin) and prod (env vars)

---
