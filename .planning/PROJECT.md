# CTC Manager — Gran Turismo Racing League Manager

## What This Is

Gran Turismo Racing League Management application (Spring Boot 4 / Thymeleaf / MariaDB). Manages Seasons, Matchdays, Matches, Races, Teams, Drivers, Scoring, and Standings for the Community Team Cup league. After four milestone cycles (v1.0 Tech Debt, v1.1 Concerns Cleanup, v1.3 English Test Data, v1.5 Code Review Fixes), the codebase is architecturally clean, security-hardened, convention-compliant, and production-ready.

## Core Value

Architectural Consistency: All controllers delegate to services, exception handling is centralized, and the production environment is secured.

## Current State (after v1.11 shipped 2026-05-18)

- **Codebase:** ~27k LOC Java (Prod) + ~48k LOC Java (Tests); **1675 tests passing** (Surefire unit + Failsafe IT + Playwright E2E combined); JaCoCo line coverage **88.88 %** (gate 82 %, comfort buffer 6.88 pp, +1.08 pp vs v1.10)
- **Gates:** SpotBugs + find-sec-bugs blocking (`<goal>check</goal>` verify-bound, 0 BugInstance) · CodeQL SAST blocking on push/PR/weekly-cron (gate-step exits 1 on new HIGH/CRITICAL) · OpenRewrite developer-invoked (`-Prewrite` profile, not in CI) · Renovate active (Mend Free Community plan, patch automerge with branch-protection enforcement) · Dockerfile noble-pin guard CI job · master branch protection: 3 required status checks
- **Codebase (pre-v1.11 numbers, for delta reference):** ~17k LOC Java (Prod) + ~25k LOC Java (Tests); 1652 Surefire + 231 Failsafe + 36 Playwright E2E; JaCoCo 87.80 %
- **Tech Stack:** Spring Boot 4.0.6, Java 25, MariaDB 11 / H2, Thymeleaf 3.1.5 (pinned), Playwright 1.59.0, Lombok 1.18.46 (JEP 498 `--sun-misc-unsafe-memory-access=allow`), Guava 33.4.8-jre (override for `AbstractFuture` Unsafe warning)
- **Security:** HTTP Basic Auth (prod/docker), SSRF hostname blocklist, path traversal defense, CSRF tokens on AJAX POSTs + every backup POST endpoint, SpEL/OGNL injection validation, Content-Disposition sanitization, MatchdayForm DTO (mass assignment protection), ZIP-Slip + ZipBomb defenses on backup import (50 MB/entry, 500 MB total, 50.000 entries cap, `startsWith(uploadDir.toRealPath())` check)
- **Architecture:** Clean 3-tier (Controller → Service → Repository), no God Services, centralized exception handling, domain services fully decoupled from admin layer, RaceLineup as source of truth for driver-team assignment, sitegen decomposed into 5 page-generator beans + SiteSlugger + TemplateWriter, dedicated `org.ctc.backup.*` package (controller + service + io + dto + audit + lock + event + restore + schema + serialization 24 MixIns + 24 EntityRestorers)
- **Database:** 36 FK-Indexes, 28 @EntityGraph annotations, Flyway V1-V7 (V7 = `data_import_audit` for import provenance); Phase/Group model: `season_phases`, `season_phase_groups`, `phase_teams` tables driving Matchday and Playoff phase association
- **Templates:** CSS utility classes instead of inline styles, TemplateManageable generic dispatch, controller-side `pageTitle` model attributes replace fragment-parameter ternaries across ~80 admin + site templates (Thymeleaf 3.1.5 SpEL canonicalization compliance); `TemplateRenderingSmokeIT` + `exec-maven-plugin` grep-gate fence regression; phase-/group-aware public site templates
- **Data:** All UI text and code comments in English, dev profile with fictive test data including GROUPS multi-phase fixture (Season 2023) + Empty-Phase fixture for D-22 coverage; **backup wire contract** locks 24-entity scope via JPA-Metamodel topo-sort (Kahn), `BackupSchema.SCHEMA_VERSION = 1`, `manifest.json` first-entry ZIP layout
- **Public Site:** Phase-tab row + group-sub-tab row, per-phase URL variants, Phase Breakdown sections on team/driver profiles, alltime aggregation across all phases (D-19 TRACKED BEHAVIOR CHANGE), desktop sticky table headers
- **Admin Features:** `/admin/backup` page with streamed ZIP export (CSRF-protected `POST /admin/backup/export`, `StreamingResponseBody`, ISO-instant filename) + manifest-first preview + replace-all import (`@Transactional` wipe + `JdbcTemplate.batchUpdate` restore bypassing `AuditingEntityListener` + post-commit upload-tree stage-and-rename); concurrent-import `ReentrantLock` + persistent yellow read-only banner + `ImportLockedWriteRejector` HandlerInterceptor + synchronous auto-backup-before-import safety net; 24h recovery retention at `data/.import-backups/<ts>/`
- **Docker / CI:** Both Dockerfile stages pinned to `eclipse-temurin:25-{jdk,jre}-noble` (Playwright 1.59.0 compatibility); `dockerfile-noble-pin-guard` CI job (whitelist-on-suffix); full `docker build .` on every PR + push to master; ci.yml concurrency block + `--no-transfer-progress`; Surefire `forkCount=2C` + Failsafe default-it `forkCount=1C` + `excludedGroups=flaky` quarantine

## Current Milestone: v1.12 Driver-Import Gap-Closure & Test Performance Round 2

**Goal:** Close the v1.11-deferred driver-import correctness bugs and substantially reduce CI test wallclock by implementing the documented PERF-FUTURE-01 architectural levers (per-fork backup-staging-dir, shared `@ContextConfiguration`, Testcontainers `withReuse`), plus decide on the test-module-split strategy.

**Target features:**
- Season-aware shortName resolver: sub-team with PhaseTeam in REGULAR phase wins over parent bucket (data-correctness)
- GROUPS-layout gate for group-assignment warnings (suppress noise on LEAGUE/BRACKET seasons)
- Per-fork `backup-staging-dir` enabling Failsafe `forkCount>1C` for backup ITs (PERF-Lever-1)
- Per-fork Spring context-fingerprint instrumentation + shared `@ContextConfiguration` cluster (PERF-Lever-2)
- Testcontainers MariaDB `.withReuse(true)` wiring (PERF-Lever-3, pre-emptive)
- Test-Module-Split decision document and (if approved) extraction of `src/test/java` into Maven sub-modules (PERF-Lever-4)
- Fix pre-existing Phase-72 `BackupSchemaExclusionIT` Java-25 AssertJ generic-inference compile error
- Google Sheets/Calendar user-facing error messages (stretch — only if PERF-levers come in under budget)

## Requirements

### Validated (v1.0)

- ✓ Centralized exception handling (GlobalExceptionHandler, EntityNotFoundException) — Phase 1
- ✓ Descriptive orElseThrow() messages in all 21 production files — Phase 1
- ✓ 7 controllers without repository injections, 4 new + 3 extended services — Phase 2
- ✓ RaceManagementService split into 3 services (RaceService, RaceGraphicService, RaceAttachmentService) — Phase 3
- ✓ 36 FK indexes on all foreign key columns via Flyway V2 migration — Phase 4
- ✓ 28 @EntityGraph annotations on collection-returning repository methods — Phase 4
- ✓ Spring Security Basic Auth for prod/docker profiles — Phase 5
- ✓ SSRF protection for FileStorageService.storeFromUrl() — Phase 5

### Validated (v1.1)

- ✓ SSRF hostname validation (private IPs, localhost, link-local blocked) — Phase 6/12
- ✓ Path traversal protection in store(), storeImage(), storeFromUrl() — Phase 6/12
- ✓ Domain services decoupled from admin DTOs (10 services, 0 admin.dto imports) — Phase 7/13
- ✓ 5 controllers use only services, no repositories — Phase 7/13
- ✓ StandingsController business logic (Buchholz/Swiss sorting) in StandingsService — Phase 7/13
- ✓ catch(Exception e) replaced with specific exceptions — Phase 8/14
- ✓ TemplateEditorController generic dispatch via TemplateManageable — Phase 10
- ✓ PlayoffService split (BracketView + Seeding) — Phase 10
- ✓ RaceService split (FormData + Calendar) — Phase 10
- ✓ Inline styles in admin templates replaced with CSS classes — Phase 11
- ✓ Alltime Standings cross-season aggregation — Phase 9/15

### Validated (v1.3)

- ✓ All UI text and code comments in English — Phases 20-21
- ✓ Dev profile with fictive teams, drivers, seasons, and results — Phases 22-27

### Validated (v1.5)

- ✓ Path traversal defense + null MIME + header injection in RaceAttachmentService — Phase 28
- ✓ MatchdayForm DTO replaces direct JPA entity binding (mass assignment fix) — Phase 29
- ✓ CSRF tokens on AJAX POSTs for prod/docker profiles — Phase 30
- ✓ SpEL/OGNL injection validation in template rendering — Phase 30
- ✓ Transactional CSV import (all-or-nothing) — Phase 31
- ✓ Bye match null safety in race services and site generator — Phase 31, 35
- ✓ Season-scoped driver-team fallback in ScoringService — Phase 31
- ✓ Domain services free of admin layer imports (RaceGraphicService relocated) — Phase 32
- ✓ Domain exceptions instead of HTTP exceptions in MatchdayService — Phase 32
- ✓ Controller logic extracted to service layer — Phase 33
- ✓ SiteGeneratorService uses RaceLineup as source of truth — Phase 33
- ✓ @Valid + BindingResult on PlayoffController.save() — Phase 34
- ✓ CSS classes instead of inline styles on race results page — Phase 34, 36

### Validated (v1.6)

- ✓ Archive links use slugified displayLabel matching actual directory names (SeasonEntry record) — Phase 37
- ✓ Nav Driver Ranking link resolves to active season's driver-ranking page — Phase 37
- ✓ All navigation links use relative paths (rootPath defaults to ".") — Phase 37
- ✓ Team logo images resolve correctly via copyLogoToAssets with path-traversal guard — Phase 37
- ✓ Season year and number displayed on all 7 page types (hero, archive, standings, matchday, driver-ranking, team-profile, driver-profile) — Phase 38
- ✓ Test seasons filtered from archive and page generation (productionSeasons filter) — Phase 38
- ✓ Empty match-meta (no track/car) hidden via th:if guard on matchday and index pages — Phase 38
- ✓ Entity cross-links: team names in standings link to team profiles, driver names in rankings and matchday results link to driver profiles — Phase 39
- ✓ Team profile lists team's drivers with links to driver profiles (DriverEntry record, SeasonDriver data source) — Phase 39
- ✓ Index page cross-links consistent with detail pages (D-04) — Phase 39
- ✓ .entity-link CSS class with accent color (#4fc3f7) and hover state (#b3e5fc + underline) — Phase 39
- ✓ Season subnavigation with Standings, Matchdays, Driver Ranking, Playoff links — Phase 40
- ✓ Active navigation state highlighting for top-nav and subnav — Phase 40
- ✓ Breadcrumbs on subpages (Home > Season > Page) — Phase 40
- ✓ Matchday index page per season — Phase 40
- ✓ Skip-to-content link as first focusable element on every page — Phase 41
- ✓ Match winner visually highlighted with accent background in match cards — Phase 41
- ✓ Mobile scroll indicator (gradient fade) on wide tables — Phase 41
- ✓ Footer with working links (Top, Archive, active season) — Phase 41
- ✓ Nav toggle aria-label on label element with role=button — Phase 41
- ✓ Hover transitions (200ms) on table rows and links, cursor:pointer on clickables — Phase 41
- ✓ Inline styles removed from driver-profile.html (CSS classes instead) — Phase 41

### Validated (v1.8)

- ✓ Bulk driver import from Google Sheets (admin UI + transactional execute) — Phases 54-55
- ✓ Per-tab preview with 6 category buckets + Skip/Accept override controls — Phase 55
- ✓ `SeasonRepository.findByYear(int)` auto-match (D-13 override of original `findByName/findByDisplayLabel` wording) — Phase 54
- ✓ Reuse of `GoogleSheetsService`, `DriverMatchingService`, `CsvImportController` preview-state pattern (no parallel infrastructure) — Phases 54-55
- ✓ `@RequestParam` + `Map<String, String>` form-binding (D-15 override of original DTO wording) — Phase 55
- ✓ JaCoCo 82% line gate met with 1064 tests project-wide (+52 from baseline) — Phase 55

### Validated (v1.9)

- ✓ `SeasonPhase` entity (REGULAR/PLAYOFF/PLACEMENT) with format/scoring/dates at phase level — Phase 56
- ✓ `SeasonPhaseGroup` as sub-groups of GROUPS-layout phases (independent roster + standings) — Phase 56
- ✓ `PhaseTeam` roster (Team↔Phase, optional Group); `SeasonDriver` structurally unchanged — Phase 56
- ✓ Mechanical migration of existing seasons → 1 REGULAR phase (+ 1 PLAYOFF if existed); old `season_id` columns dropped in V6 — Phases 57, 61
- ✓ `Playoff` re-anchored from Season to Phase; M:N `playoff_seasons` table dropped — Phases 57, 61
- ✓ Domain services (`StandingsService`, `DriverRankingService`, `MatchdayService`, `PlayoffService`, `PlayoffSeedingService`, `SeasonManagementService`) phase-aware with delete-guard, REGULAR auto-sync, dual-API surface (D-18, D-25, D-26 v1.9) — Phase 58
- ✓ Driver import: `findByYearAndNumber(int, int)` replaces `findByYear(int)`; tab pattern `^\d{4}_S\d+$` — Phase 59
- ✓ `TestDataService` and `DevDataSeeder` directly in new model with multi-phase + GROUPS fixture (Season 2023) + Empty-Phase fixture (Season 2024-3) — Phases 59, 62
- ✓ Admin UI: Saison-Detail with phase tabs, group sub-tabs, per-phase standings + combined view — Phase 60
- ✓ Cleanup quality gate: bridge columns dropped, no admin code referencing legacy `season_id` on Matchday/Playoff — Phase 61
- ✓ Public static site phase- and group-aware (analogous to admin season-detail): per-phase URL variants, group sub-tab row, PLAYOFF tab routing to playoff.html (D-08), Phase Breakdown sections on team/driver profiles, byte-identity preserved for single-LEAGUE seasons (SC4) — Phase 62
- ✓ D-19 TRACKED BEHAVIOR CHANGE: alltime aggregation spans all phases (REGULAR + PLAYOFF + PLACEMENT) — Phase 62
- ✓ Cross-cutting regression IT (`SiteGeneratorPhaseAwarenessIT`, 9 @Test methods covering SC1-SC5 + D-22 + D-26) — Phase 62
- ✓ JaCoCo 82% line gate held with 1246 tests project-wide (1215 Surefire + 31 Failsafe), 87.24% line coverage — Phase 62

### Validated (v1.10)

- ✓ Spring Boot 4.0.5 → 4.0.6 + Thymeleaf 3.1.5.RELEASE pinned (CVE-2026-40478 SpEL canonicalization hardening) — Phase 71
- ✓ Controller-side `pageTitle` model attributes replace fragment-parameter ternaries across ~80 admin + site templates — Phase 71
- ✓ `TemplateRenderingSmokeIT` (HTTP 200 + no `TemplateProcessingException` for every `/admin/**` GET) + `exec-maven-plugin` grep-gate fence — Phase 71
- ✓ Backup wire contract locked: `BackupSchema.SCHEMA_VERSION = 1` (monotonic int), `BackupManifest` record, 24-entity FK-respecting `EXPORT_ORDER` via JPA-Metamodel + Kahn — Phase 72
- ✓ `@Qualifier("backupObjectMapper")` strict bean co-exists with `@Primary` default (preserves admin REST/AJAX behaviour) — Phase 72
- ✓ Flyway `V7__data_import_audit.sql` (H2 + MariaDB compatible, LONGTEXT for JSON-shape fields) — Phase 72
- ✓ Streaming ZIP export — 24 per-entity Jackson MixIns (domain entities annotation-clean), `BackupExportService` `@Transactional(readOnly=true)` with `@EntityGraph` eager-fetch, `StreamingResponseBody`, CSRF-protected POST — Phase 73
- ✓ Manifest-first import preview, schema-version refusal BEFORE any DB write, ZIP-Slip + ZipBomb defenses, multipart limits raised to 100 MB on Spring + Tomcat — Phase 74
- ✓ Replace-all import: single `@Transactional` wipe + restore, FK-reverse native-SQL DELETE, `em.flush() + em.clear()`, `JdbcTemplate.batchUpdate` bypasses `AuditingEntityListener`, post-commit upload-tree stage-and-rename with 24h recovery — Phase 75
- ✓ `BackupImportMariaDbSmokeIT` (Testcontainers Saison-2023 round-trip) + `BackupImportRollbackIT` (50 %-injected exception, asserts pre-import state) — Phase 75
- ✓ Operational hardening: `ImportLockService` `ReentrantLock` singleton + 409 redirect, persistent yellow read-only banner, `ImportLockedWriteRejector` HandlerInterceptor (HTTP 503 on non-import POSTs, whitelist-on-equals), synchronous auto-backup-before-import safety net — Phase 76
- ✓ `BackupRoundTripIT` on H2 + MariaDB (24-entity row-count parity + SHA-256 byte-equality on Race + SeasonDriver + Team); README "Backup & Restore" section + GitHub Wiki page — Phase 77
- ✓ Dockerfile pinned `eclipse-temurin:25-{jdk,jre}-noble`; CI `dockerfile-noble-pin-guard` + full `docker build .` on every PR + push to master — Phase 78
- ✓ Surefire `forkCount=2C` + Failsafe default-it `forkCount=1C` + `excludedGroups=flaky`; per-package code cleanup sweep across `org.ctc.backup.*` + `domain.*` + `admin.*` + `sitegen` — Phase 79
- ✓ JaCoCo line coverage 87.80 % (gate 82 %, v1.9 baseline 87.02 %, +0.78 pp despite +24 MixIns / +24 EntityRestorers / +15 backup-service classes) — Phase 79
- ✓ TESTING.md "Test Invocation Discipline" section, plan-SUMMARY frontmatter normalized for phases 56/57/62/64 — Phase 79

### Validated (v1.11)

- ✓ OpenRewrite developer-invoked refactoring tool wired (`-Prewrite` profile, `rewrite-spring:6.30.4` + `rewrite-migrate-java:3.34.1` + `rewrite-static-analysis:2.20.0` on plugin classpath, `rewrite.yml` activates `org.openrewrite.staticanalysis.CommonStaticAnalysis` composite, documentary `UpgradeSpringBoot_4_0` tripwire) — Phase 80 (REWR-01..06)
- ✓ One-shot `CommonStaticAnalysis` cleanup applied: 380 files refactored, +0.33 pp JaCoCo (88.13 %), 1 file 4-line revert per D-08 fallback (`RaceService.MethodReferences` regression) — Phase 80 (REWR-02, REWR-05)
- ✓ SpotBugs 4.9.8.3 + find-sec-bugs 1.14.0 blocking gate (`<goal>check</goal>`, `<effort>Max</effort>`, verify-bound) with `lombok.config` (`addLombokGeneratedAnnotation` + `addSuppressFBWarnings`) suppressing Lombok-generated false positives — Phase 81 (STAT-01..03)
- ✓ `config/spotbugs-exclude.xml` with rationale comments on every `<Match>` (D-08 layer 2 architectural filter extended to all service/DTO/record packages; 220 baseline findings triaged to 0; 10 DM_DEFAULT_ENCODING fixed with `StandardCharsets.UTF_8`; STAT-06 throwaway-branch deliberate-violation `NP_ALWAYS_NULL` proved gate blocks on HIGH) — Phase 81 (STAT-04..07)
- ✓ Backup wire-contract guard tests: `BackupSchemaGuardTest` (SCHEMA_VERSION + EXPORT_ORDER size), `BackupRestoreZipOpenCountIT` (single-pass ZIP read), `BackupRoundTripIT` extended to 24-entity row-count parity on H2 + MariaDB — Phase 82 (BACK-01, BACK-03..05)
- ✓ 12 Phase-75 REVIEW.md items resolved with atomic per-item commits + `82-BACKLOG-AUDIT.md` ledger; WR-01 `BackupExecutedByResolver` bean extracted; profile-isolated `import-backups-dir` per `data/${spring.profiles.active}/import-backups` — Phase 82 (BACK-02)
- ✓ Driver-detail Season-Assignment chip ordering enforced via JPQL `ORDER BY s.year ASC, s.number ASC` in `DriverRepository#findDetailById`; `DriverRepositoryOrderIT` + `DriverDetailSmokeE2ETest` — Phase 83 (QUAL-01)
- ✓ `DevDataSeeder` + `TestDataService` widened to `@Profile({"dev", "local"})` for live-MariaDB UAT bootstrap; `DemoDataSeeder` unchanged at `@Profile("demo")` — Phase 83 (QUAL-02)
- ✓ Per-group matchday generation UI affordance (`SeasonController.generate` passes `form.getGroupId()`; template `th:if="${phase.layout.name() == 'GROUPS'}"` guard); `MatchdayGeneratorGroupsE2ETest` — Phase 83 (QUAL-03)
- ✓ `StandingsView` record DTO + `StandingsViewService` (`@Transactional(readOnly = true)`) replace lazy-collection access in `StandingsController`; 9 dedicated Mockito unit tests — Phase 83 (QUAL-04)
- ✓ `docs/uat/UAT-02-legacy-season-smoke.md` operator procedure + STATE.md result-slot for pre-V4-data post-deploy verification — Phase 83 (QUAL-05; live execution post-v1.11-deploy)
- ✓ Mend Renovate GitHub App installed (single-repo scope, Free Community plan, Interactive mode), `renovate.json` with safety packageRules: Guava `-jre` allowedVersions, Thymeleaf `enabled: false` (CVE-2026-40478 pin) + secondary vulnerability-override, `config:recommended` LTS preset inheritance, 4 group names (Spring Boot, Spring Security, Google API clients, Testcontainers), eclipse-temurin `-noble` regex with Adoptium underscore-build support, patch automerge with `automergeType: "pr"` — Phase 84 (DEPS-01..07)
- ✓ Synthetic Dockerfile-bump PR #126 exercises `dockerfile-noble-pin-guard` end-to-end (`[noble-pin-guard] OK`); `.github/dependabot.yml` removed in same atomic commit as `renovate.json` introduction (D-03 T-5 dual-bot mitigation) — Phase 84 (DEPS-08)
- ✓ CodeQL SAST blocking gate (`.github/workflows/codeql.yml` on push/pull_request/schedule/workflow_dispatch, `java-kotlin` with `security-extended`, inline-bash SARIF-diff gate-step with PR-context vs branch-context query split fix, exit 1 + `::error::` annotation on new HIGH/CRITICAL) — Phase 85 (SAST-01..03, SAST-06)
- ✓ 3-layer FP suppression invariant: `.github/codeql/codeql-config.yml` `query-filters` (SSRF/ZIP-Slip/path-injection) + `// CodeQL FP: <rule-id>` source markers + `docs/security/sast-acceptance.md` per-finding table; BCrypt-N/A documented as D-05 tracked deviation (no `PasswordEncoder` bean, httpBasic auth path) — Phase 85 (SAST-04, SAST-05)
- ✓ Test-wallclock baseline established: `ContextLoadCountListener` instruments unique Spring context boots; 3 phase-repository ITs converted from `@SpringBootTest` to `@DataJpaTest`; 8 `@DirtiesContext` removed (sitegen cluster fix) + surgical per-method retention on latch-dependent backup ITs — Phase 86 (PERF-01..03)
- ✓ PERF-04 OR-branch verdict: target ≤ 7m 50s MISSED (CI median 23:00 ≫ target); architectural blocker (Spring-context-per-fork structural cost) documented in `docs/test-performance.md § v1.12 Forward Path` with 3 prioritized levers (per-fork backup-staging-dir, shared `@ContextConfiguration`, Testcontainers reuse); tracked as `PERF-FUTURE-01` for v1.12 — Phase 86 (PERF-04)
- ✓ CI 5-run PR-branch median 23:00 captured per D-17 trigger-equivalence (5 `workflow_dispatch` runs on milestone branch, drop min+max, variance 7.5 % within D-10 20 % tolerance) — Phase 86 (PERF-05)
- ✓ v1.10 Nyquist VALIDATION debt closed retroactively: 8 v1.10 phases (71-76, 78-79) `status: approved` + `nyquist_compliant: true`; 6 gap-fill tests landed atomically across 4 plans (5 new test files, 0 impl bugs surfaced); v1.10-MILESTONE-AUDIT scoreboard `partial 1/6/2` → `compliant 9/0/0` — Phase 87 (VAL-01..04)
- ✓ v1.11 inline Nyquist closure (post-Phase-87 audit, in-milestone): 6 v1.11 phases (81-86) retroactively approved + Phase-86 retroactive `86-VERIFICATION.md` created; v1.11 scoreboard `compliant 8/0/0` — Option A inline pattern mirrors v1.10 → Phase 87 cross-milestone pattern within a single milestone
- ✓ CI Playwright fork-channel corruption fix: `actions/cache@v4` for `~/.cache/ms-playwright` + pre-install all 3 default browsers before Surefire (~360 MiB cache footprint, ~30 s cold-cache once per key bump); eliminates `Playwright.create()` mid-Surefire auto-download corrupting fork-channel stdout — Phase 86 follow-up (`5cc76ab9` + `3590b3a7`)
- ✓ T-2 master branch protection active: `required_status_checks.contexts = [build-and-test, dockerfile-noble-pin-guard, docker-build]`, `strict: true`, `enforce_admins: false`, force-pushes/deletions disabled — Phase 84 follow-up (operator action 2026-05-18)

### Active

v1.12 in flight (carry-forwards absorbed into roadmap):

- **Driver-Import gap-closure** (2 deferred debug sessions in `.planning/debug/deferred/` — fully diagnosed):
  - `shortname-resolver-picks-parent-without-phaseteam` → DRIV-01
  - `group-warnings-for-non-groups-seasons` → DRIV-02
- **PERF-FUTURE-01** test-wallclock reduction Round 2 → PERF-01..PERF-05 (3-lever forward path in `docs/test-performance.md § v1.12 Forward Path` + test-module-split decision)
- **BackupSchemaExclusionIT** Java-25 AssertJ generic-inference compile error → CLEAN-01
- Items added by future `/gsd:new-milestone` after v1.12 close.

### Out of Scope

- OAuth2/OIDC — Basic Auth sufficient for single-admin app
- Full Pagination UI — only repository preparation, no template rework
- Disable OSIV — deliberately enabled, only @EntityGraph as optimization
- Modify Flyway V1 migration — checksum-protected
- Form Login / User Management — over-engineered for admin tool

## Constraints

- **Test Coverage**: 82% line coverage minimum
- **Flyway**: Do not change existing migrations, only new ones
- **Profiles**: Auth only for prod/docker, dev/local remain without auth
- **OSIV**: Remains enabled — only @EntityGraph as optimization
- **Backward Compatibility**: No breaking changes to existing URLs/endpoints
- **Playwright**: Remains compile-scope dependency (runtime usage for graphics)

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Architecture refactoring before auth | Clean service layer makes auth integration easier | ✓ Validated |
| Basic Auth instead of Form Login | Single-admin app, no user management needed | ✓ v1.0 |
| Auth only prod/docker profiles | Dev/local without network exposure | ✓ v1.0 |
| RaceManagementService split | 673 lines, 13 dependencies → 3 services | ✓ v1.0 |
| Two-profile SecurityFilterChain | @Profile-based instead of runtime check | ✓ v1.0 |
| CSRF disabled globally, tokens on AJAX | Single-admin via Basic Auth, AJAX POSTs protected | ✓ v1.0/v1.5 |
| TemplateManageable interface | Generic dispatch instead of 380 lines duplication | ✓ v1.1 |
| PlayoffService + RaceService split | Focused services instead of God Services | ✓ v1.1 |
| Domain DTO decoupling | Primitive parameters instead of admin DTOs in domain services | ✓ v1.1 |
| RaceGraphicService to admin.service | Fix layering violation — domain must not import admin | ✓ v1.5 |
| SpEL pattern-based validation | Not a full sandbox — sufficient for admin-only templates | ✓ v1.5 |
| CONV-02/03/05 already compliant | Research confirmed no code changes needed | ✓ v1.5 |
| Reuse `GoogleSheetsService` + `DriverMatchingService` + `CsvImportController` preview-state pattern | No parallel import infrastructure | ✓ v1.8 |
| Form-params re-fetch instead of `@SessionAttributes` (D-06) | Stateless controller, predictable transactional boundary, mirrors `CsvImportController` | ✓ v1.8 |
| `@RequestParam` primitives + `Map<String, String>` instead of static Form DTO (D-15 override of QUAL-03 wording) | Per-row keys (`seasonId_<year>`, `skip_<psnId>_<year>`, `accept_<psnId>_<year>`) are dynamic — DTO would not fit | ✓ v1.8 |
| Per-tab cache key for FUZZY-accept driver resolution (CR-01 fix) | Per-tab `accept_<psnId>_<year>` choices must stay isolated; cross-tab dedup keeps plain PSN key for the no-accept branch | ✓ v1.8 |
| Test years 2021/2022 (not 2023/2024) | DevDataSeeder seeds 2023/2024/2026 on context startup → `findByYear()` ambiguity broke conflict-overwrite assertions | ✓ v1.8 |
| Bridge-Spalten-Drop in V6 erweitert (Phase 61 D-01) | matchdays.season_id + playoffs.season_id sind denormalisiert + wartungsbelastend (vs. canonical Season → SeasonPhase → Matchday/Playoff); Phase 56 D-02 / Phase 57 SC5 superseded | ✓ v1.9 |
| Phase-additive entity scope (Phase 56) | Old Season fields + season_id FKs stay until Phase 61 — services migrate before schema cleanup, so Phase 57 data migration runs against a stable surface | ✓ v1.9 |
| `findByType` (Optional) over `findRegularPhase` (throws) for legacy bridge (Phase 58 D-?) | Avoids transaction rollback-only poisoning; pre-V4 seasons fall through gracefully | ✓ v1.9 |
| `PlayoffService.createPlayoff` atomically writes PLAYOFF SeasonPhase + Playoff (Plan 58-05 D-19) | Single `@Transactional` boundary mitigates Pitfall 2 (orphan phase rows on partial failure) | ✓ v1.9 |
| `PlayoffSeedingService` dual-flow: manual seeds win, REGULAR Top-N is fallback (Plan 58-05 D-15) | Preserves legacy admin workflow while adding the new auto-seeding path; PhaseTeam roster on PLAYOFF phase populated as side-effect (D-20) | ✓ v1.9 |
| Driver-import `findByYearAndNumber(int, int)` replaces `findByYear(int)` (Phase 59) | Multi-season-per-year ambiguity resolved at the API surface, not via heuristic; tab pattern extended to `^\d{4}_S\d+$` | ✓ v1.9 |
| Group membership for imported drivers resolved via `PhaseTeam` of the REGULAR phase, not via per-driver sheet override (Phase 59) | Group is a phase-scope property of the team, not a driver attribute — keeps the import sheet shape stable | ✓ v1.9 |
| `TestDataService` + `DevDataSeeder` rebuilt directly on the new model — no Backward-Compat helpers (DATA-01, DATA-02) | Helpers would mask schema regressions; T-prefix isolation keeps fixtures collision-free with manual data | ✓ v1.9 |
| D-19 alltime aggregation spans REGULAR + PLAYOFF + PLACEMENT (Phase 62, TRACKED BEHAVIOR CHANGE) | Public alltime totals include playoff results — explicit user decision to make playoff outcomes count alltime; flagged as behavior change in 62-CONTEXT.md | ✓ v1.9 |
| `templates/site/` mirrors `templates/admin/season-detail.html` two-row tabs structure (Phase 62) | Same UX shape across admin and public site reduces cognitive load for the league operator | ✓ v1.9 |
| Parent-team always wins on shortName multi-match (Phase 70 D-05, inverts Phase 66 D-04) | Domain model: `SeasonDriver.team_id` references parent; sub-team split is per-match via `RaceLineup`, never per-phase. Phase 66 D-04 was a model-violating default discovered during live MariaDB UAT | ✓ v1.9 |
| Group-resolution UX in driver-import preview decommissioned (Phase 70 D-09) | Per Phase-70 D-05 the parent team is the only correct answer; `usesGroups` / `resolvedGroupName` / `TEAM_NOT_IN_REGULAR_PHASE` warning all became dead branches | ✓ v1.9 |
| `findByPsnId` guard in NEW_DRIVER branch of DriverSheetImportService (Phase 70 GAP-70-01 fix) | `computeIfAbsent` did not consult the DB → cross-tab duplicate PSN-ID inserts caused full-transaction rollback on live MariaDB import (Saison 2023). 2 IT regression tests fence the regression | ✓ v1.9 |
| Lombok 1.18.46 + JEP 498 `--sun-misc-unsafe-memory-access=allow` (Phase 68) | Java 25 emits terminally-deprecated warnings for `lombok.permit.Permit`'s `sun.misc.Unsafe` calls; Lombok 1.18.46 + the JEP 498 flag silence the warnings without forcing a Java downgrade | ✓ v1.9 |
| Guava pinned to 33.4.8-jre (`<guava.version>` override) | Transitive Guava 33.1.0-jre from `google-api-client` emitted `AbstractFuture$UnsafeAtomicHelper` Unsafe warning on Java 25; 33.4.x switches to `VarHandle` for Java 9+ | ✓ v1.9 |
| `data_import_audit` permanently out of export scope (Phase 72 D-15) | Audit log is operational metadata about migrations, not league data — survives every import for traceability; enforced structurally via `BackupSchema` package-name filter `org.ctc.domain.model.*` (D-06), so any entity under `org.ctc.backup.*` is excluded with no opt-in / marker / developer memory required | ✓ v1.10 |
| OpenRewrite stays developer-invoked, not CI-bound (Phase 80 D-01) | Plugin bound to `verify` lifecycle can silently mutate source files in CI; profile-scoped `-Prewrite` makes refactoring an explicit operator action and prevents accidental upgrades from sneaking into PRs | ✓ v1.11 |
| `CommonStaticAnalysis` composite + `UpgradeSpringBoot_4_0` documentary tripwire only (Phase 80 D-04, D-05) | Codebase already on Boot 4.0.6; activating Boot-4-migration recipes would regress the modular starter decomposition. Comment-only tripwire in `rewrite.yml` acts as self-enforcing reminder since `rewrite-spring` is on plugin classpath and would resolve the recipe if added back | ✓ v1.11 |
| D-08 layer 2 SpotBugs filter extended to all service/DTO/record packages (Phase 81) | Runtime baseline showed 197 of 220 findings (89.5 %) are the same `EI_EXPOSE_REP*` false-positive shape on Lombok-generated record/DTO classes across `org.ctc.{domain,admin,backup,dataimport,gt7sync,sitegen}.*` — architectural-filter expansion structurally identical to original D-08 rationale, alternative was per-class `@SuppressFBWarnings` flood | ✓ v1.11 |
| Mend "Renovate Only" Free Community plan, single-repo install (Phase 84 D-02, T-1 mitigation) | Mend Application Security commercial features unnecessary — SpotBugs+find-sec-bugs (Phase 81) + CodeQL (Phase 85) cover SAST/SCA. Single-repo scope minimizes blast radius of third-party SaaS write access | ✓ v1.11 |
| Default CodeQL Setup disabled to allow advanced workflow (Phase 85) | `code-scanning/default-setup` and advanced `codeql.yml` are mutually exclusive per GitHub. Trade-off: javascript/typescript/actions auto-scanning lost (java-kotlin only via Phase 85 workflow); future phase may add matrix-strategy if needed | ✓ v1.11 |
| 3-layer FP suppression invariant: codeql-config-exclude + source-marker + sast-acceptance-row (Phase 85 D-19) | Every CodeQL false-positive requires all three layers (Update-on-Triage discipline). Single source-of-truth checking via `grep` across the three files catches drift between machine-readable filter and human-readable rationale | ✓ v1.11 |
| Driver-detail chip order via JPQL `ORDER BY` not `@OrderBy` (Phase 83 QUAL-01) | `@OrderBy` on `Driver.seasonAssignments` collection triggers OSIV lazy-init at template render time; explicit `LEFT JOIN FETCH ... ORDER BY` in `findDetailById` resolves in the controller-call boundary | ✓ v1.11 |
| `StandingsViewService` extraction + `StandingsView` record DTO (Phase 83 QUAL-04) | Controller's lazy collection access (`getGroups()` traversal) was OSIV-dependent and untestable in unit slice; service-layer extraction with `@Transactional(readOnly = true)` boundary and 9 dedicated Mockito tests covers all 5 resolution branches | ✓ v1.11 |
| PERF-04 accepted as OR-branch (Phase 86) | ≥30 % wallclock target MISSED (CI median 23:00 vs target ≤7m 50s); architectural blocker — Spring-context-per-fork structural cost cannot be amortized without test-module split. 3-lever forward path documented in `docs/test-performance.md`, tracked as PERF-FUTURE-01 for v1.12. Forking further would multiply not amortize | ✓ v1.11 (OR-branch) |
| PR-branch CI harvest semantically equivalent to post-merge master (Phase 86 D-17) | `ci.yml` runs identical steps for `pull_request`, `push to master`, and `workflow_dispatch` triggers; Maven step timing independent of trigger event. Allowed Phase 86 to close inside the same milestone PR (#122) without an orphan post-merge `docs(86):` commit on master | ✓ v1.11 |
| In-milestone Nyquist closure (v1.11 Option A) mirrors cross-milestone Phase-87 pattern (v1.10) | After Phase 87 closed v1.10 Nyquist debt, the milestone audit surfaced the same draft VALIDATION pattern in v1.11 itself. Resolution path: 6 retroactive `/gsd:validate-phase` runs + 1 retroactive 86-VERIFICATION.md inline (same morning), avoiding a v1.12 Phase-88 carry-forward. Closing pattern: precedent for future milestones — if Nyquist debt accumulates during execution, prefer inline closure post-audit over cross-milestone closure phase | ✓ v1.11 |
| Playwright fork-channel corruption fix: pre-install all 3 default browsers + actions/cache@v4 (CI follow-up) | `Playwright.create()` validates all default browsers (Chromium + Firefox + WebKit) on first use, not just `chromium()`. Mid-Surefire auto-download corrupts fork-channel stdout → Maven exit 1, 0 failing tests. Cache footprint (~360 MiB) and 30 s cold-install acceptable for stable forks | ✓ v1.11 |

### Backup Wire Contract (v1.10)

Phase 72 locked the on-disk wire contract for the backup ZIP before any export/import code was written. Four invariants follow every Phase 72+ backup; importers MUST refuse anything that doesn't match.

**1. Schema versioning is a monotonic integer, not a semver.**
`BackupSchema.SCHEMA_VERSION = 1` (public static final int). Every wire-incompatible schema change bumps the integer by 1. Phase 74's import service rejects ZIPs whose `manifest.json:schema_version` does not equal the current `BackupSchema.SCHEMA_VERSION` BEFORE any DB write (catastrophic-data-loss prevention). GAP-2 resolution.

**2. ZIP layout is per-entity, manifest-first.**
Every backup ZIP contains, in this order:

- `manifest.json` as the FIRST entry (write-order discipline owned by Phase 73's `BackupExportService`).
- Per-entity JSON under `data/`: `data/seasons.json`, `data/season-phases.json`, `data/race-results.json`, etc. Filenames are derived from `@Table(name=...)` via snake_case to kebab-case (`season_phases` becomes `season-phases.json`). `EntityRef.fileName` codifies this rule.
- `uploads/` directory mirroring only entity-referenced files from `data/{profile}/uploads/`.

GAP-1 resolution.

**3. `EXPORT_ORDER` is a JPA-Metamodel-generated topological sort over `org.ctc.domain.model.*`.**
`BackupSchema.@PostConstruct` reads `EntityManagerFactory.getMetamodel().getEntities()`, filters classes by `getPackage().getName().startsWith("org.ctc.domain.model")`, walks each `EntityType<?>`'s `@ManyToOne`/`@OneToOne` owning-side singular attributes, and runs Kahn's algorithm to produce a dependency-first ordered list. The `Team.parentTeam` self-FK is detected and skipped so Kahn does not deadlock. The result is wrapped in `List.copyOf(...)` and exposed via `getExportOrder()`. Hand-rolled FK orderings are forbidden — they drift. GAP-5 resolution.

**4. The 24-entity scope (D-03 corrected post-research: 24 operative entities including `PlayoffRound`).**
The runtime topo-sort returns 24 `EntityRef` instances (CONTEXT.md originally said 23; `PlayoffRound` was missed in D-03 and reconciled in RESEARCH §OQ-1). The 24 are: `Car`, `Track`, `Season`, `SeasonPhase`, `SeasonPhaseGroup`, `Team`, `PhaseTeam`, `SeasonTeam`, `Driver`, `SeasonDriver`, `PsnAlias`, `RaceScoring`, `MatchScoring`, `RaceSettings`, `Matchday`, `Match`, `Race`, `RaceLineup`, `RaceResult`, `RaceAttachment`, `Playoff`, `PlayoffRound`, `PlayoffMatchup`, `PlayoffSeed`. `Car` and `Track` ARE included (D-01: round-trip from environment A to B must not require gt7sync on B first). `FeatureSettings` is DROPPED (D-02: class does not exist in `src/main/java/org/ctc/domain/model/`; if ever introduced, it bumps `SCHEMA_VERSION` 1 to 2). Any new entity under `org.ctc.domain.model` is automatically picked up by the next boot's topo-sort — no marker, no opt-in.

**ObjectMapper isolation (D-11 amended per RESEARCH §Pitfall P-2).**
`BackupObjectMapperConfig` declares BOTH a `@Primary` default mapper (built from the auto-config `Jackson2ObjectMapperBuilder`, preserving admin REST/AJAX behaviour) AND a `@Qualifier("backupObjectMapper")` strict mapper (`FAIL_ON_UNKNOWN_PROPERTIES=true`, `WRITE_DATES_AS_TIMESTAMPS=false`, `JavaTimeModule` registered, Phase 73 MixIn `@Component` beans injected via `List<Module>`). Defining a single qualified bean would silently disable Spring's auto-configured default via `@ConditionalOnMissingBean(ObjectMapper.class)` — verified against spring-projects/spring-boot#47379.

**Audit log persistence (`DataImportAudit` Lombok class, NOT record).**
`org.ctc.backup.audit.DataImportAudit` is a Lombok `@Entity` (Hibernate 7 cannot proxy Java records — RESEARCH §Pitfall P-1 corrects D-08's preference). The entity deliberately does NOT extend `BaseEntity` so the Phase 75 writer fully controls `executedAt` without `AuditingEntityListener` interference. V7 columns use `LONGTEXT` (portable across H2 2.x and MariaDB 10.7+) for the JSON-shape text fields `table_counts_wiped` and `table_counts_restored` (D-09 — native `JSON` rejected for v1.10 dialect drift).

## Evolution

This document evolves at phase transitions and milestone boundaries.

---
*Last updated: 2026-05-18 — v1.12 milestone started: Driver-Import Gap-Closure & Test Performance Round 2. Scope absorbs 2 v1.11 carry-forwards (driver-import data-correctness + UI-noise bugs from deferred debug sessions) and the PERF-FUTURE-01 architectural work (3-lever forward path: per-fork backup-staging-dir, shared `@ContextConfiguration`, Testcontainers `withReuse`) plus a test-module-split decision-point, with `BackupSchemaExclusionIT` Java-25 compile fix and optional Google-API error-UX as stretch. v1.11 baseline: 1675 tests / JaCoCo 88.88 % / CI E2E median 23:00 (gate target ≤7m 50s — Phase 86 OR-branch). Branch: `gsd/v1.12-driver-import-and-test-perf`. Next: roadmap creation via `/gsd:new-milestone`.*
