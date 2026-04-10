# Milestones

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
