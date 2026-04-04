# Milestones

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
