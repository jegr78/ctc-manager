# CTC Manager — Gran Turismo Racing League Manager

## What This Is

Gran Turismo Racing League Management application (Spring Boot 4 / Thymeleaf / MariaDB). Manages Seasons, Matchdays, Matches, Races, Teams, Drivers, Scoring, and Standings for the Community Team Cup league. After four milestone cycles (v1.0 Tech Debt, v1.1 Concerns Cleanup, v1.3 English Test Data, v1.5 Code Review Fixes), the codebase is architecturally clean, security-hardened, convention-compliant, and production-ready.

## Core Value

Architectural Consistency: All controllers delegate to services, exception handling is centralized, and the production environment is secured.

## Current State (after v1.10 shipped 2026-05-16)

- **Codebase:** ~17k LOC Java (Prod) + ~25k LOC Java (Tests); 1652 Surefire unit + 231 Failsafe IT + 36 Playwright E2E; JaCoCo line coverage **87.80 %** (gate 82 %, comfort buffer 5.80 pp)
- **Tech Stack:** Spring Boot 4.0.6, Java 25, MariaDB 11 / H2, Thymeleaf 3.1.5 (pinned), Playwright 1.59.0, Lombok 1.18.46 (JEP 498 `--sun-misc-unsafe-memory-access=allow`), Guava 33.4.8-jre (override for `AbstractFuture` Unsafe warning)
- **Security:** HTTP Basic Auth (prod/docker), SSRF hostname blocklist, path traversal defense, CSRF tokens on AJAX POSTs + every backup POST endpoint, SpEL/OGNL injection validation, Content-Disposition sanitization, MatchdayForm DTO (mass assignment protection), ZIP-Slip + ZipBomb defenses on backup import (50 MB/entry, 500 MB total, 50.000 entries cap, `startsWith(uploadDir.toRealPath())` check)
- **Architecture:** Clean 3-tier (Controller → Service → Repository), no God Services, centralized exception handling, domain services fully decoupled from admin layer, RaceLineup as source of truth for driver-team assignment, sitegen decomposed into 5 page-generator beans + SiteSlugger + TemplateWriter, dedicated `org.ctc.backup.*` package (controller + service + io + dto + audit + lock + event + restore + schema + serialization 24 MixIns + 24 EntityRestorers)
- **Database:** 36 FK-Indexes, 28 @EntityGraph annotations, Flyway V1-V7 (V7 = `data_import_audit` for import provenance); Phase/Group model: `season_phases`, `season_phase_groups`, `phase_teams` tables driving Matchday and Playoff phase association
- **Templates:** CSS utility classes instead of inline styles, TemplateManageable generic dispatch, controller-side `pageTitle` model attributes replace fragment-parameter ternaries across ~80 admin + site templates (Thymeleaf 3.1.5 SpEL canonicalization compliance); `TemplateRenderingSmokeIT` + `exec-maven-plugin` grep-gate fence regression; phase-/group-aware public site templates
- **Data:** All UI text and code comments in English, dev profile with fictive test data including GROUPS multi-phase fixture (Season 2023) + Empty-Phase fixture for D-22 coverage; **backup wire contract** locks 24-entity scope via JPA-Metamodel topo-sort (Kahn), `BackupSchema.SCHEMA_VERSION = 1`, `manifest.json` first-entry ZIP layout
- **Public Site:** Phase-tab row + group-sub-tab row, per-phase URL variants, Phase Breakdown sections on team/driver profiles, alltime aggregation across all phases (D-19 TRACKED BEHAVIOR CHANGE), desktop sticky table headers
- **Admin Features:** `/admin/backup` page with streamed ZIP export (CSRF-protected `POST /admin/backup/export`, `StreamingResponseBody`, ISO-instant filename) + manifest-first preview + replace-all import (`@Transactional` wipe + `JdbcTemplate.batchUpdate` restore bypassing `AuditingEntityListener` + post-commit upload-tree stage-and-rename); concurrent-import `ReentrantLock` + persistent yellow read-only banner + `ImportLockedWriteRejector` HandlerInterceptor + synchronous auto-backup-before-import safety net; 24h recovery retention at `data/.import-backups/<ts>/`
- **Docker / CI:** Both Dockerfile stages pinned to `eclipse-temurin:25-{jdk,jre}-noble` (Playwright 1.59.0 compatibility); `dockerfile-noble-pin-guard` CI job (whitelist-on-suffix); full `docker build .` on every PR + push to master; ci.yml concurrency block + `--no-transfer-progress`; Surefire `forkCount=2C` + Failsafe default-it `forkCount=1C` + `excludedGroups=flaky` quarantine

## Current Milestone: v1.11 Tooling Infrastructure & Tech-Debt Sweep

**Goal:** Promote the entire Phase 999.x tooling backlog into the active pipeline (OpenRewrite, Clean-Code enforcement, Renovate, SAST) and clear the v1.10 + v1.9 carried-over tech-debt to enter v1.12 with a fully clean slate.

**Target features:**

*Tooling backlog (4 streams, previously parked as Phase 999.1–999.4):*

- OpenRewrite refactoring/migration tool integration — automated recipe-based refactoring + future Spring Boot / Java version upgrades (Phase 999.1)
- Clean Code Principles enforcement — Checkstyle / PMD / SpotBugs gates wired into Maven verify (Phase 999.2)
- Renovate automated dependency updates — recurring PRs against pom.xml + workflow files (Phase 999.3)
- Security SAST static analysis — CodeQL / Semgrep integration into CI (Phase 999.4)

*Backup cleanup (v1.10 carryover from Phase 75 REVIEW.md):*

- 12 Info/Warning items: `Map.copyOf` order strip, Step-1-revert `FileAlreadyExistsException` handling, `executedBy` duplication, `restoreOneTable` opens ZIP 24× → single-pass, etc.

*Quality / polish sweep:*

- Driver-detail Season-Assignment chip ordering (explicit `ORDER BY year` on `Driver.seasonAssignments`)
- `DevDataSeeder` `@Profile` widening for live-MariaDB-UAT bootstrap on `local,demo`
- Per-group matchday generation UI affordance (`SeasonController.generateMatchdays:251` Rule-3 deviation, carried over from v1.9)
- `StandingsController.java:139` lazy collection style cleanup (carried over from v1.9)
- UAT-02 legacy season visual smoke against real pre-V4 production data (carried over from v1.9, verify on next prod deploy)

*Test infrastructure (architectural):*

- Phase 79 D-06 wallclock-reduction debt — achieved 16.85 %, target ≥ 30 %; requires test-module split / Spring-context-per-fork restructuring

*Validation closure:*

- Nyquist `*-VALIDATION.md` drafts → approved for 6 phases (72-76, 79)
- Nyquist `*-VALIDATION.md` creation for phases 71 + 78

**Explicitly out of milestone scope:**

- Wiki QUAL-05 image render (self-resolves on PR merge to master, not tech debt)
- PLAT-CI-02 release-workflow observation (by-design post-merge, not tech debt)

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

### Active

v1.12 candidates (after v1.11 ships):

- **Driver-Import gap-closure** (carries 2 deferred debug sessions from 2026-05-08):
  - `shortname-resolver-picks-parent-without-phaseteam` — data-correctness bug (resolver picks parent over sub-team-with-PhaseTeam in target season); season-aware algorithm documented in `.planning/debug/deferred/`
  - `group-warnings-for-non-groups-seasons` — UI-noise bug (per-row "⚠ No group" + tab-level warnings fire for non-GROUPS layouts); root cause + files_to_change documented in `.planning/debug/deferred/`
- **PERF-FUTURE-01** — split `src/test/java/` into separate Maven modules (carry-forward from v1.11 PERF-04 OR-branch; 3-lever forward path documented in `docs/test-performance.md § v1.12 Forward Path`)
- Items added by `/gsd:new-milestone` after v1.11 close.

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
*Last updated: 2026-05-16 — v1.11 milestone started: Tooling Infrastructure & Tech-Debt Sweep. Promotes Phase 999.1–999.4 backlog (OpenRewrite, Clean-Code enforcement, Renovate, SAST) into the active pipeline and clears v1.10 + v1.9 carried-over tech-debt (Phase 75 REVIEW.md items, polish sweep, test-wallclock reduction, Nyquist VALIDATION closure). Phase numbering continues at 80+. Branch: `gsd/v1.11-tooling-and-cleanup`.*
