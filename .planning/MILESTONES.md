# Milestones

## v1.10 Spring Boot 4.0.6 Upgrade & Data Export/Import (Shipped: 2026-05-16)

**Phases completed:** 9 phases (71-79), 50 plans, 39/39 requirements satisfied
**Diff:** +77 362 / −1 224 across 521 files (378 commits in milestone range)
**Tests:** 1652 Surefire unit + 231 Failsafe IT + 36 Playwright E2E; JaCoCo line coverage 87.80 % (gate 82 %, v1.9 baseline 87.02 %)
**Timeline:** 7 days (2026-05-09 → 2026-05-16)
**Branch:** `gsd/v1.10-platform-and-backup`
**Final-gate verify:** `./mvnw verify -Pe2e` BUILD SUCCESS, Maven total 11m 11s, bash wallclock 11m 13s
**Audit verdict:** passed (`v1.10-MILESTONE-AUDIT.md`)

**Key accomplishments:**

- Spring Boot 4.0.5 → 4.0.6 + Thymeleaf 3.1.5.RELEASE absorbed structurally — controller-side `pageTitle` model attributes replace fragment-parameter ternaries across ~80 admin + site templates; `TemplateRenderingSmokeIT` covers every `/admin/**` GET route; `exec-maven-plugin` grep-gate fences regression (PLAT-01..07)
- Backup wire contract locked before any export/import code: `BackupSchema.SCHEMA_VERSION = 1` (monotonic integer), `BackupManifest` record, 24-entity FK-respecting `EXPORT_ORDER` generated from JPA Metamodel via Kahn's algorithm, `@Qualifier("backupObjectMapper")` strict bean co-exists with `@Primary` default, Flyway `V7__data_import_audit.sql` migration runs on H2 + MariaDB, `data_import_audit` structurally excluded from export via package filter (SCHEMA-01..04, IMPORT-08)
- Streaming ZIP export — 24 per-entity Jackson MixIns keep `org.ctc.domain.model` annotation-clean, `BackupExportService` `@Transactional(readOnly=true)` with explicit `@EntityGraph` eager-fetch, `StreamingResponseBody` (no full-dataset buffering), CSRF-protected POST endpoint with ISO-instant `Content-Disposition` filename, admin/backup page wired to sidebar (EXPORT-01..06)
- Replace-all import path — manifest-first read + schema-version refusal BEFORE any DB write, ZIP-Slip + ZipBomb defenses (50 MB/entry, 500 MB total, 50.000 entries cap, `startsWith(uploadDir.toRealPath())` check), multipart limits raised to 100 MB on Spring + Tomcat layers, dedicated `BackupUploadExceptionHandler` `@ControllerAdvice` for `MaxUploadSizeExceededException`, stateless preview state via staging-path re-parse (D-15 v1.8 pattern); single `@Transactional` wipe + restore (FK-reverse native-SQL DELETE → `em.flush() + em.clear()` → `JdbcTemplate.batchUpdate` bypassing `AuditingEntityListener`), `Team.parentTeam = NULL` pre-step, post-commit upload-tree stage-and-rename with 24h `data/.import-backups/<ts>/uploads-old/` recovery (IMPORT-01..07, SECU-01..04)
- Operational hardening — `ImportLockService` `ReentrantLock` singleton + 409 redirect, persistent yellow read-only banner via `@ControllerAdvice`, `ImportLockedWriteRejector` HandlerInterceptor (HTTP 503 on non-import POSTs, whitelist-on-equals), synchronous auto-backup-before-import Step 0.5 with first-match-wins `AutoBackupBeforeImportException` catch-chain (SECU-05..07); 5-section operator runbook `docs/operations/import-runbook.md`
- Quality gates held — `BackupRoundTripIT` runs on H2 AND MariaDB profiles with 24-entity row-count parity + SHA-256 byte-equality on Race + SeasonDriver + Team; `BackupImportRollbackIT` injects mid-restore failure at 50 % → asserts pre-import DB state + `success=false` audit row; `BackupImportMariaDbSmokeIT` (Testcontainers) covers Saison-2023 round-trip; 75-HUMAN-UAT 10/10 PASS (6 visual + 4 operational); README "Backup & Restore" section + GitHub Wiki page pushed to ctc-manager.wiki.git (QUAL-01..05)
- Side-quest Phase 78: Dockerfile pinned `eclipse-temurin:25-{jdk,jre}-noble` (suffix-only) repairs the silently broken release workflow's `playwright install chromium` step on Ubuntu 26.04; CI `dockerfile-noble-pin-guard` job mirrors PLAT-07's `exec-maven-plugin` grep-gate pattern; full `docker build .` runs on every PR + push (PLAT-CI-01..02)
- Phase 79 milestone closer: per-package cleanup across `src/main/java` + `src/test/java` + 24 Jackson MixIns + 24 EntityRestorers + 15 graphic services (Schutzwortliste-protected, atomic commits), Surefire `forkCount=2C` + Failsafe default-it `forkCount=1C` + `excludedGroups=flaky` quarantine, ci.yml concurrency block + `--no-transfer-progress`, plan-SUMMARY frontmatter normalized for phases 56/57/62/64, TESTING.md "Test Invocation Discipline" appended

**Deferred to next milestone (acknowledged at close):**

- 12 REVIEW.md Info/Warning items from Phase 75 (`Map.copyOf` order strip, Step-1-revert `FileAlreadyExistsException`, `executedBy` duplication, `restoreOneTable` opens ZIP 24×, etc.) — v1.11 backup-cleanup mini-phase
- Phase 79 D-06 wallclock-reduction debt: achieved 16.85 % vs ≥ 30 % target; Spring-context startup is structural — architectural test-restructuring needed (Spring-context-per-fork is unavoidable cost without splitting test modules)
- Driver-detail Season-Assignment chip ordering (cosmetic; 75-HUMAN-UAT test 6) — explicit `ORDER BY year` on `Driver.seasonAssignments` query
- `DevDataSeeder` is `@Profile("dev")`-only; live-MariaDB-UAT on `local,demo` requires either profile widening or a separate Saison-2023 fixture-bootstrap
- Nyquist `*-VALIDATION.md` drafts → approved for 6 phases (72-76, 79) + creation for 71 + 78 — optional `/gsd:validate-phase {N}`
- Backlog: OpenRewrite (Phase 999.1), Clean-Code enforcement (999.2), Renovate (999.3), SAST (999.4)

**Post-merge self-resolving (not tech debt):**

- QUAL-05 wiki image embed render — `raw.githubusercontent.com/master/...` URLs resolve on PR merge to master
- PLAT-CI-02 release-workflow run on master observation — by-design post-merge

Known deferred items at close: see `STATE.md` Deferred Items + `v1.10-ROADMAP.md` "Issues Deferred"

---

## v1.9 Season Phases & Groups (Shipped: 2026-05-09)

**Phases completed:** 15 phases (56-70), ~70 plans, 38/38 requirements satisfied
**Diff:** +88 447 / −2 502 across 567 files (442 commits in milestone range)
**Tests:** 1227 unit + 31 Playwright E2E (Failsafe), JaCoCo line coverage 87.02% (gate 82%)
**Timeline:** 14 days (2026-04-26 → 2026-05-09)
**Branch:** `gsd/v1.9-season-phases-groups`
**Live UAT D-22:** Saison 2023 driver import on local MariaDB — 287 new drivers / 357 new assignments / 0 errors

**Key accomplishments:**

- Phase/Group domain model: `SeasonPhase` (REGULAR/PLAYOFF/PLACEMENT) + `SeasonPhaseGroup` + `PhaseTeam` roster replace the flat-season container; group-seasons are expressible without the multi-season workaround (MODEL-01..08, MIGR-01..07)
- Mechanical data migration: Flyway V3-V6 maps every existing season to 1 REGULAR (+ optional PLAYOFF) phase, drops the `season_id` bridge columns and the `playoff_seasons` join-table — legacy seasons stay reachable byte-identically (live UAT 287/357/0 errors confirms)
- Phase-aware domain services: `StandingsService.calculateStandings(phaseId, groupId)` + `DriverRankingService` + `MatchdayGeneratorService` + `PlayoffService(Seeding)` operate exclusively on phase scope with combined-view aggregation; 5 graphics services migrated off the legacy `calculateStandings(seasonId)` bridge (SVC-01..05)
- Admin UI mirrors the new model: slim season form, phase + group CRUD forms, season-detail two-row tabs (phase row + group sub-tab row), per-phase standings + combined view, playoff bracket bound to PLAYOFF phase, driver-import preview with unambiguous `year_S{number}` labels (UI-01..07)
- Public site phase + group awareness: phase-tab row + group-sub-tab row + per-phase URL variants + PLAYOFF tab routing + Phase Breakdown sections on team/driver profiles + alltime aggregation across all phases (D-19 TRACKED BEHAVIOR CHANGE); LEAGUE-only seasons render byte-identically (SITE-01..03 + 9-test `SiteGeneratorPhaseAwarenessIT`)
- Driver import: `findByYearAndNumber(int, int)` resolves `^\d{4}_S\d+$` tabs unambiguously (IMPORT-01..04); Phase 70 inverted the Phase-66 sub-team-wins default — `SeasonDriver.team_id` always points to parent, sub-team split happens per-match via `RaceLineup`; group-resolution UX + `TEAM_NOT_IN_REGULAR_PHASE` warning fully decommissioned; 2 IT regression tests close GAP-70-01 (cross-tab duplicate driver insert)
- Quality gate held: JaCoCo 87.02% (gate 82%), 1227 unit + 31 Playwright E2E green, comment-policy re-sweep across `src/main` + `src/test` (Phase 67), Lombok 1.18.46 + JEP 498 silence the `sun.misc.Unsafe` warnings on Java 25 (Phase 68), Guava 33.4.8-jre override silences the transitive `AbstractFuture` Unsafe warning

**Deferred to next milestone (acknowledged at close):**

- Quality Gate Lock / CI comment-noise guard (Phase 67 D-06 forward commitment) — automated gate (Maven Enforcer / pre-commit hook / CI grep gate) blocking attribution-marker introduction
- UAT-02 (Legacy season visual smoke with real pre-V4 production data) — user verifies opportunistically after next production deploy; local fixtures cover empty-state path only
- WARN-1: per-group matchday generation UI affordance (`SeasonController.generateMatchdays:251` hardcodes `groupId=null`) — Rule-3 deviation documented in Phase 61 QUAL-02
- OBS-3: `StandingsController.java:139` lazy collection (`resolvedPhase.getGroups()`) — OSIV-safe read-only context, style-only
- Plan SUMMARY frontmatter sweep for phases 56/57/62/64 (15 plan SUMMARYs across 4 phases, analog to Phase 69's 17-SUMMARY sweep) — pure bookkeeping
- Stub quick task `260404-jh8-fix-release-workflow-use-release-token-s` (predates v1.9, status `missing`)
- 2 debug sessions (`group-warnings-for-non-groups-seasons`, `shortname-resolver-picks-parent-without-phaseteam`) — both `diagnosed` with hypothesis confirmed; resolutions captured in session docs and superseded by Phase 70

Known deferred items at close: 8 (see STATE.md Deferred Items)

---

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
