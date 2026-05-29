# CTC Manager — Gran Turismo Racing League Manager

## What This Is

Gran Turismo Racing League Management application (Spring Boot 4 / Thymeleaf / MariaDB). Manages Seasons, Matchdays, Matches, Races, Teams, Drivers, Scoring, and Standings for the Community Team Cup league. After twelve milestone cycles (v1.0 Tech Debt → v1.14 Team Card Redesign & Data Safety), the codebase is architecturally clean, security-hardened, convention-compliant, production-ready, with a hardened release pipeline, full SAST/SCA gating, a CI E2E baseline of 17:39 (−23.3 % vs v1.11 23:00), a complete outbound Discord integration (channel lifecycle + 11 post types + forum-thread linking), and a unified "Carbon HUD" Carbon/Gold visual system across all 16 Playwright-rendered admin graphics. The `local` profile is hard-fenced against test-data seeders so it can never write fictional data into the real MariaDB.

## Core Value

Architectural Consistency: All controllers delegate to services, exception handling is centralized, and the production environment is secured.

## Current State (after v1.14 shipped 2026-05-29)

- **v1.14 delta:** All 16 Playwright-rendered admin graphics (team card + 5 composites + 5 matchday/list + stream overlay + 4 analogy templates) restyled to the external Claude-Design **"Carbon HUD"** Carbon/Gold system as drop-in template replacements — same dimensions, format, data model, and `th:*` bindings; overlay geometry/skew/transparency preserved byte-exact. Every card-consumer integration path (Discord auto-post POST-02, manual Re-Post + Refresh PATCH, admin + template-editor previews) stayed backward-compatible with zero caller changes (CARD-01..04). Two named backend tweaks: `TeamCardService` color-robustness (`accentVisColor` + `onPrimaryColor`, JaCoCo-excluded), and `ProvisionalScoresGraphicService` `raceLabel`-only-for->1-race conditional (both-branch IT). Data-safety: `DevDataSeeder` + `TestDataService` reverted from the v1.11 `@Profile({"dev","local"})` drift back to `@Profile("dev")`, plus `LocalProfileDataSafetyIT` regression fence that fails `./mvnw verify` if either bean re-enters the `local` context (SAFE-01/02).
- **Codebase:** ~33k LOC Java (Prod) + ~67k LOC Java (Tests); **2393 tests passing** (Surefire unit + Failsafe IT + Playwright E2E combined; +697 net vs v1.12 1696 — Discord integration test surface + 13-per-field backup regression fence + 67 review-fix regression tests); JaCoCo line coverage **89.43 %** (gate 82 %, comfort buffer 7.43 pp, Δ+0.99 pp vs v1.12 88.44 % — Phase 92 COV-01 restored ≥ 88.88 % baseline, subsequent Discord phases added new IT coverage net-positive)
- **CI E2E baseline:** **17:39** median (preserved from v1.12 — no Discord-test wallclock regression; Phase 102 lock-IT async-latch removal partially offset Discord IT addition)
- **Gates:** SpotBugs + find-sec-bugs blocking (`<goal>check</goal>` verify-bound, 0 BugInstance) · CodeQL SAST blocking on push/PR/weekly-cron (gate-step exits 1 on new HIGH/CRITICAL) · OpenRewrite developer-invoked (`-Prewrite` profile, not in CI) · Renovate active (Mend Free Community plan, patch automerge with branch-protection enforcement) · Dockerfile noble-pin guard CI job · master branch protection: 3 required status checks · Hardened release.yml (SemVer-strict tag sort, fetch-tags, parser, idempotency guard, dry-run gates — REL-01)
- **Codebase (pre-v1.11 numbers, for delta reference):** ~17k LOC Java (Prod) + ~25k LOC Java (Tests); 1652 Surefire + 231 Failsafe + 36 Playwright E2E; JaCoCo 87.80 %
- **Tech Stack:** Spring Boot 4.0.6, Java 25, MariaDB 11 / H2, Thymeleaf 3.1.5 (pinned), Playwright 1.59.0, Lombok 1.18.46 (JEP 498 `--sun-misc-unsafe-memory-access=allow`), Guava 33.4.8-jre (override for `AbstractFuture` Unsafe warning)
- **Security:** HTTP Basic Auth (prod/docker), SSRF hostname blocklist, path traversal defense, CSRF tokens on AJAX POSTs + every backup POST endpoint, SpEL/OGNL injection validation, Content-Disposition sanitization, MatchdayForm DTO (mass assignment protection), ZIP-Slip + ZipBomb defenses on backup import (50 MB/entry, 500 MB total, 50.000 entries cap, `startsWith(uploadDir.toRealPath())` check)
- **Architecture:** Clean 3-tier (Controller → Service → Repository), no God Services, centralized exception handling, domain services fully decoupled from admin layer, RaceLineup as source of truth for driver-team assignment, sitegen decomposed into 5 page-generator beans + SiteSlugger + TemplateWriter, dedicated `org.ctc.backup.*` package (controller + service + io + dto + audit + lock + event + restore + schema + serialization 24 MixIns + 24 EntityRestorers)
- **Database:** 36 FK-Indexes, 28 @EntityGraph annotations, Flyway V1-V16 (V7 `data_import_audit`; V8 `discord_global_config`; V9 `teams.discord_role_id`; V10 `matches.discord_*` + scheduling; V11 + V14 `discord_post` + `phase_id` FK; V12 + V13 `seasons.discord_*_thread_id`; V15 + V16 audit-polish folds); Phase/Group model: `season_phases`, `season_phase_groups`, `phase_teams` tables driving Matchday and Playoff phase association
- **Templates:** CSS utility classes instead of inline styles, TemplateManageable generic dispatch, controller-side `pageTitle` model attributes replace fragment-parameter ternaries across ~80 admin + site templates (Thymeleaf 3.1.5 SpEL canonicalization compliance); `TemplateRenderingSmokeIT` + `exec-maven-plugin` grep-gate fence regression; phase-/group-aware public site templates
- **Data:** All UI text and code comments in English, dev profile with fictive test data including GROUPS multi-phase fixture (Season 2023) + Empty-Phase fixture for D-22 coverage; **backup wire contract** locks 26-entity scope via JPA-Metamodel topo-sort (Kahn) over `org.ctc.domain.model.*` + `org.ctc.discord.model.*` packages, `BackupSchema.SCHEMA_VERSION = 2`, lenient importer accepts `schema_version IN (1, 2)`, `manifest.json` first-entry ZIP layout
- **Public Site:** Phase-tab row + group-sub-tab row, per-phase URL variants, Phase Breakdown sections on team/driver profiles, alltime aggregation across all phases (D-19 TRACKED BEHAVIOR CHANGE), desktop sticky table headers
- **Admin Features:** `/admin/backup` page with streamed ZIP export (CSRF-protected `POST /admin/backup/export`, `StreamingResponseBody`, ISO-instant filename) + manifest-first preview + replace-all import (`@Transactional` wipe + `JdbcTemplate.batchUpdate` restore bypassing `AuditingEntityListener` + post-commit upload-tree stage-and-rename); concurrent-import `ReentrantLock` + persistent yellow read-only banner + `ImportLockedWriteRejector` HandlerInterceptor + synchronous auto-backup-before-import safety net; 24h recovery retention at `data/.import-backups/<ts>/`
- **Docker / CI:** Both Dockerfile stages pinned to `eclipse-temurin:25-{jdk,jre}-noble` (Playwright 1.59.0 compatibility); `dockerfile-noble-pin-guard` CI job (whitelist-on-suffix); full `docker build .` on every PR + push to master; ci.yml concurrency block + `--no-transfer-progress`; Surefire `forkCount=2C` + Failsafe default-it `forkCount=1C` + `excludedGroups=flaky` quarantine

## Current Milestone: v1.15 CI Optimisation & Race/Match Defaults

**Goal:** Focus the CI pipeline on real code/build changes and speed it up; make the Race/Match workflow more robust through prefill defaults, consistent "n/a" rendering of missing drivers, and explicit walkover handling; introduce a new template-variable-driven "Lobby Settings" graphic.

**Target features:**

*Strand 1 — CI Optimisation:*
- **Path-aware CI (approach A+C):** `ci.yml` jobs always start (required checks `build-and-test`, `dockerfile-noble-pin-guard`, `docker-build` stay intact), but expensive steps (Maven build / E2E / Docker build) gate behind a `dorny/paths-filter` `changes` job. Non-required workflows (`codeql.yml`, `mariadb-migration-smoke.yml`) get hard `paths-ignore`. Ignore set: `.planning/**`, root `*.md`, `docs/**` (except `docs/site/**` which still triggers deploy), `.gitmessage` + meta files.
- **E2E runtime reduction** (current median 17:39).
- **Caching improvements** (Maven / Playwright / Docker layers).
- **Flaky-test / rerun reduction** — extend quarantine, stabilise.

*Strand 2 — Race/Match Defaults:*
- **Prefill on create:** scoring scheme, number of legs/races, date/time — inherited from season/matchday.
- **Missing-driver "n/a":** teams fielding < 6 drivers render "n/a" placeholders in Lineup, Scorecard/Results and Provisional-Scores graphics (Provisional padded to 6 rows); scoring treats missing drivers as 0 points / no position.
- **Walkover (w/o):** a team that does not compete at all is handled analogously to the existing `Match.bye` logic (auto-win with full match points for the opponent) plus a visible "w/o" label. Likely a new Flyway migration for a `walkover` flag.

*Strand 3 — Lobby Settings graphic (NEW):*
- **Brand-new standalone graphic** in the Carbon HUD style, delivered as a Claude-Design drop-in HTML.
- **No new data model** — solved purely via **template variables**; concrete variables arrive with the Claude-Design handoff (not elaborated now).
- **Integration:** admin preview + download · Discord auto-post (new post type) · template editor (custom override).
- New `LobbySettingsGraphicService` (Playwright-rendered → coverage-excluded, `extends AbstractGraphicService implements TemplateManageable`).

**Key context:**
- Required CI checks must never break → no naive `paths-ignore` on `ci.yml`.
- New Flyway migration only for the `walkover` flag (Lobby Settings needs none). H2 + MariaDB compatible.
- Missing-driver "n/a" touches 3 graphic services + their Thymeleaf templates; Provisional Scores is currently inconsistent (no padding today).
- 82 % coverage minimum, existing Flyway migrations immutable, Carbon HUD look preserved, new Discord post type plugs into the existing integration.

### Deferred candidates (NOT in v1.15 scope)

Carry-forwards from prior milestone audits, available for a future milestone:
- **DISC-FUTURE-01..05** — Inbound Discord interaction, auto-trigger pipeline (race-save → post), settings-form migration into the admin app, multi-guild support, public-site notification webhook (carry-forwards from v1.13).
- **ISEMPTY-AUDIT** — String `.isEmpty()` audit (~10 callsites with different semantics from `.isBlank()`; case-by-case per Phase 103 CONTEXT D-06).
- **Discord channel bulk-rename** — admin action on `/admin/discord-config` to PATCH existing match-channels created under the pre-Phase-100 naming scheme (two-scheme coexistence is acceptable today; D-08 verdict 2026-05-26).
- **Webhook-token encryption at rest** — `webhook_token` / `role_id` / `thread_id` are PII-equivalent secrets now persisted into backup ZIPs; filesystem ACL is the current mitigation (deferred from v1.13 Phase 101).
- **Cross-milestone operator-action UATs** — UAT-02 legacy season smoke, QUAL-02 local-profile MariaDB smoke (semantics flipped by v1.14 SAFE-01 — now "verify seeders ABSENT on local"), UX-01 driver-import badge screenshots (may cross milestone boundaries per CLAUDE.md "Pre-existing debt").

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

### Validated (v1.12)

- ✓ `BackupSchemaExclusionIT` Java-25 AssertJ generic-inference compile error resolved (Phase-80 JDT-cache diagnosis confirmed clean); `./mvnw verify` exits 0 with JaCoCo CSV — Phase 88 (CLEAN-01)
- ✓ YAGNI sweep: `@Disabled` placeholder + regression-fence + Windows-conditional skip removed; `grep -rn "@Disabled" src/test/java` returns 0 — Phase 88 (CLEAN-02)
- ✓ `SiteGeneratorBaselineCaptureTest` refactored from `@Test @Disabled` anti-pattern to `SiteGeneratorBaselineRefresh` CommandLineRunner under `@Profile("baseline-refresh")` with `@Primary` mocked `YouTubeScraperService` — Phase 88 (CLEAN-03)
- ✓ `.github/workflows/release.yml` hardened against duplicate-tag-pattern (4-milestone regression closed): SemVer-strict tag sort (`git tag --sort=-version:refname --list 'v[0-9]*.[0-9]*.[0-9]*'`), `fetch-tags: true`, parser defaults PATCH=0 with numeric validation, pre-`versions:set` idempotency guard, BREAKING CHANGE footer parser, `workflow_dispatch -F dry-run=true` exercises version-determination — Phase 88 (REL-01)
- ✓ `docs/operations/release-runbook.md` operator runbook for retroactive `v1.10.0` + `v1.11.0` publishes + legacy short-form tag cleanup; pre-merge executed 2026-05-20: `v1.10.0` + `v1.11.0` releases published (JAR + GHCR images), legacy `v1.3`/`v1.5`/`v1.8` tags deleted; 3 runbook bugs surfaced + patched same session — Phase 88 (REL-02)
- ✓ Season-aware `DriverSheetImportService.resolveTeamByShortName(shortName, regularPhase)` — sub-team with `PhaseTeam(regularPhase.id, candidate.id)` wins over parent bucket; D-07 legacy fallback to first-match + WARN log preserved when no PhaseTeam in target REGULAR phase — Phase 88 (DRIV-01)
- ✓ `TabPreview.usesGroups` boolean + `DriverSheetImportController.showGroupColumn` page-wide aggregation defensive future-proofing (template surface intentionally deferred per user-accepted "Defensive Future-Proofing" decision — the deferred-debug-doc surfaces no longer exist in v1.12 code) — Phase 88 (DRIV-02)
- ✓ CLAUDE.md "Skill Invocation Naming" subsection documents `/gsd-<name>` (dash) as canonical, deprecates pre-2026 colon-form prefix; `/gsd:` regression-fence grep returns 0 on 6 active top-level planning files — Phase 88 (DOCS-01)
- ✓ Per-fork `app.backup.staging-dir` + `app.backup.import-backups-dir` + `app.upload-dir` resolve to `…-fork-${surefire.forkNumber}` paths; Failsafe `default-it forkCount=2 reuseForks=true` + inherited Spring-Boot Failsafe execution unbound; 3-seed Failsafe `org.ctc.backup.**` (1234/5678/9999) all BUILD SUCCESS — Phase 89 (PERF-01)
- ✓ `ContextCacheKeyFingerprintListener` (`TestExecutionListener` via `spring.factories`) dumps per-context `MergedContextConfiguration.hashCode()` to sidecar `context-loads-{PID}-fingerprints.txt` markers + `aggregate-fingerprints.sh` Top-N cluster reporter; docs `§ PERF-02 Forensics` — Phase 89 (PERF-02)
- ✓ Composed `@CtcDevSpringBootContext` annotation applied across 19 outer test classes (13 Surefire + 6 Failsafe); Surefire cluster `9cefac4c → baafff8e` collapse (2 annotation-shape variants → 1 shared cache key, 13 outer classes preserved); 3-seed Failsafe + Surefire BUILD SUCCESS; Wave-5 local median 08:27 (−9.3 % vs Phase 89 09:19) — Phase 90 (PERF-03)
- ✓ Testcontainers MariaDB `.withReuse(true)` on `BackupImportMariaDbSmokeIT` + `BackupRoundTripIT` nested `MariaDbRoundTripTests`; `~/.testcontainers.properties` opt-in protocol documented in `docs/test-performance.md § PERF-04` + README; CI gate `@EnabledIfSystemProperty(docker.available=true)` preserved (CI cold-starts as today); T-90-TC-01 + T-90-TC-02 threats mitigated — Phase 90 (PERF-04)
- ✓ `docs/test-performance.md § Test-Module-Split Decision` verdict `defer` with 3 explicit blockers (TestDataService cross-boundary, IDE-friction-risk, no hard cumulative-effect data), v1.13 re-evaluation trigger (against PERF-06 CI median), and "Why not reject?" rationale paragraph — Phase 90 (PERF-05)
- ✓ CI 5-run median **17:39** re-harvested per D-17 trigger-equivalence (`workflow_dispatch` on PR-branch HEAD `b63a2be1`); sorted seconds 929/1015/**1059**/1072/1122, drop min+max → median of middle 3 = 1059s; variance 18.2 % within 20 % D-10 tolerance; Δ−23.3 % vs v1.11 baseline 23:00 — Phase 91 (PERF-06)
- ✓ Sealed `org.ctc.dataimport.exception.GoogleApiException` base + 4 typed permits (`TransientGoogleApiException` / `AuthGoogleApiException` / `NotFoundGoogleApiException` / `PermissionGoogleApiException`) + `Category` enum + `GoogleApiExceptionMapper` static helper (13 unit tests cover 8 mapping branches); `GoogleSheetsService` + `GoogleCalendarService` typed-throws; `DriverSheetImportController#preview` + `#execute` + `RaceController#createCalendarEvent` 4 typed catches set `errorCategory` flash key + hardcoded category-specific `errorMessage`; Thymeleaf `layout.html` + `driver-import.html` render `.error-badge--{transient|auth|not-found|permission}` BEM span; `docs/operations/google-integration.md` operator runbook (Setup / Error Categories / Troubleshooting); T-91-02-IL info-leak invariant closed for 2 of 3 GoogleSheets-consuming controllers (`CsvImportController` deferred to v1.13) — Phase 91 (UX-01)

### Validated (v1.13)

- ✓ Carry-forwards from v1.12 closed: UX-01 `CsvImportController` typed-catch + `errorCategory` badge parity (T-91-02-IL info-leak invariant closed for all 3 Google-Sheets consumers); COV-01 JaCoCo recovery (`RaceControllerCalendarTest` + `GoogleSheets/CalendarServiceIT` defensive-catch coverage, 88.44 % → ≥ 88.88 %); CLEAN-01 `@Disabled`/`Assumptions.` grep predicate tightened to `org.junit.jupiter.api.Assumptions` (distinguishes from AssertJ `Assumptions.assumeThat`); DOCS-01 retroactive 89/90/91-VERIFICATION.md authoring; BOOK-01 11-marker flip in v1.12-REQUIREMENTS.md — Phase 92
- ✓ Discord Foundation: `org.ctc.discord.DiscordRestClient` (Spring `RestClient`, Bot-token auth via `/api/v10`) + `DiscordWebhookClient` (multipart-PATCH for image attachments) + sealed `DiscordApiException` (4 permits: Transient/Auth/NotFound/CategoryFull) + `DiscordRateLimitInterceptor` (per-bucket token-bucket with 429 retry-after + exponential 5xx backoff) + `DiscordTimestamps` (`<t:UNIX:STYLE>` for 5 styles) + `DiscordEmojiCache` (`team.shortName` → `<:name:id>` long-form, 60-min TTL); threat-model surfaces (`DISCORD_BOT_TOKEN` env-var, `app.discord.allowed-hosts=discord.com` SSRF whitelist, Logback `%replace` webhook-URL mask, `@ToString.Exclude` on secrets, CSRF on all `POST /admin/discord/**`, `DiscordConfigForm` DTO); `/admin/discord-config` page on Flyway V8 `discord_global_config` with 4 test/refresh buttons (Test Connection / Test Webhook / Refresh Roles / Refresh Emojis); WireMock-backed integration tests cover all 4 exception paths + rate-limit retry + multipart-upload + emoji-lookup-cache-refresh — Phase 93 (INFRA-01..03)
- ✓ Team Roles + Match Channel Lifecycle: Flyway V9 `teams.discord_role_id VARCHAR(32)` + Team-Form snowflake-validated field (`^\d{17,20}$`) + live-dropdown sourced from cached `DiscordRestClient.fetchGuildRoles()`; Flyway V10 `matches.discord_*` (channel_id, webhook_url, webhook_token, host_user_id, race_director_user_id, streamer_user_id) + scheduling fields + Match-Detail "Create Discord Channel" button with full permission-overwrite model (visible to both team roles + RD/streamer/host individuals, hidden from `@everyone`) + webhook creation + post-create permission-audit assertion; Archive modal with category regex `^Match Days Archive (?<year>\d{4})(?: \((?<num>\d+)\))?$` + live channel-count + default-suggestion of highest-num with `< 50` (honors Discord's 50-channels-per-category limit) — Phase 94 (CHAN-01..03)
- ✓ Match Channel Posts (5 types): Flyway V11 `discord_post` idempotency table + `DiscordPostService.postOrEdit` lookup-on-`(match_id, post_type)`; POST-02 Team Cards (auto-posted on channel-create + manual Re-Post + manual Refresh, multipart with 2 PNGs); POST-03 Settings + Lineups (multipart posts gated on pre-flight predicates with new 5th `data-incomplete` error category); POST-04 Match Results with race-result-based stale-detection auto-flagging stale on re-render; POST-05 Schedule JSON embed with auto-edit hook (`MatchScheduleFieldsChangedEvent` → `DiscordAutoPostListener.onScheduleFieldsChanged` AFTER_COMMIT) on host/RD/streamer/dateTime field changes — Phase 95 (POST-01..05)
- ✓ Provisional Graphic + Forum Threads: new `ProvisionalScoresGraphicService` + Thymeleaf graphic template (pixel-accurate to historical Google-Sheets screenshot) replaces manual sheet-screenshot step; Flyway V13 `seasons.discord_race_results_thread_id` + `discord_standings_thread_id` + Season-Detail "Discord Integration" section + Link-existing-Thread modal (Create-new-Thread modal explicitly scoped OUT per Phase 99 FORUM-01 acceptance rewrite); Race-Detail "Post Race Result to Forum-Thread" with `?thread_id=` query param + `unarchiveIfArchived` hook — Phase 96 (GRAFX-01, FORUM-01, FORUM-02)
- ✓ Matchday-Level Posts: POST-06 per-match Match Preview Announcement (Markdown body + N×2 Settings/Lineups attachments on announcement webhook + auto-edit hook on streamLink/teaser/RD change); POST-07a Match Day Results + POST-07b Power Rankings (2 independent Matchday-Detail buttons to race-results forum-thread; 07a gates on all-matches-final + thread + webhook, 07b stays looser because rating is operator-curated); POST-08 phase-aware Standings to standings forum-thread (Flyway V14 `discord_post.phase_id` FK; new `StandingsGraphicService` with dynamic-sizing Playwright graphic handling 14+ teams without overflow; iterative visual-approval design loop) — Phase 97 (POST-06, POST-07a/b, POST-08)
- ✓ Polish + E2E + Docs: mobile-polish CSS sweep (`.card` / `.form-group` / `.searchable-dropdown` + 640px MQ padding); `DiscordFullMatchdayLifecycleE2ETest` 8-stage Playwright + WireMockExtension mega-walkthrough (create channel → post-all-stages → archive); `docs/operations/discord-integration.md` operator runbook (Bot setup screenshots + OAuth URL generator + token rotation + troubleshooting); README + GitHub Wiki canonical paragraph; POST-09 Matchday Pairings (Markdown + Pairings-PNG multipart on announcement webhook); POST-10 Matchday Schedule (pure Multipart-PNG, reuses `MatchdayScheduleGraphicService`); 4-state sibling button clusters on Matchday-Detail — Phase 98 (E2E-01, DOCS-02, DOCS-03, POST-09, POST-10)
- ✓ Pre-merge audit-polish (Phase 99): REQUIREMENTS.md Flyway-prose fix (POST-01 V11 → V12, FORUM-01 V12 → V13 + acceptance rewrite scoping out the unbuilt "Create new Thread..." modal); ROADMAP v1.13 Progress refresh (top-level row → Complete, 7 per-phase rows); retroactive 92/94/95/96/97/98-VERIFICATION.md retrofill per v1.12 DOCS-01 precedent; Phase 93 + Phase 95 VALIDATION.md frontmatter refresh; YAGNI delete of `DiscordRestClient.createThread()` + `ThreadCreateRequest` DTO + orphan IT method
- ✓ Match Day Channel Naming Scheme (Phase 100): `DiscordChannelService.channelName(Match)` extended to emit `md{N}-{phase}-[{group}-]{home}-vs-{away}` with `phaseAbbrev(PhaseType)` (`rs`/`po`/`pm`) + `groupSlug(SeasonPhaseGroup)` + 100-char overflow guard; new pure-unit `DiscordChannelServiceNamingTest`; 5 IT files refreshed (14 literal occurrences); existing channels stay as-is per D-08 (no migration, two-scheme coexistence acceptance)
- ✓ Backup/Restore covers Discord schema (Phase 101): `BackupSchema.SCHEMA_VERSION` 1 → 2; lenient `IN (1, 2)` importer accept (pre-v1.13 v1 backups remain restorable, leave Discord empty + self-heal); package-filter expansion to `org.ctc.discord.model.*` (24-entity → 26-entity scope); 2 new MixIns + 2 new Restorers for `DiscordGlobalConfig` + `DiscordPost` + 4 existing Restorers extended for 13 V8-V15 columns; `discord_post` pinned-last in topo-sort (FKs as `@Column UUID`, not `@ManyToOne`); 13 per-field regression-fence tests + byte-equality round-trip on H2 + MariaDB; single-guild restore semantics + webhook_token PII-equivalent secrecy documented in `docs/operations/discord-integration.md § Backup & Restore`
- ✓ Code-Review Fixes closeout (Phase 102): 67 review findings closed across Phases 92-101 (9 critical/blocker + 58 warning/info); 4 plans, 45 commits; close-loop `gsd-code-reviewer` Pass-2 CLEAN after 5 inline remediations (3 warning + 2 info); final JaCoCo line coverage 89.43 %, 2392 tests, 0 SpotBugs `BugInstance`; controller-thin extract template (`MatchService.buildMatchDetailModel`, `DiscordSeasonViewService.buildDiscordIntegrationModel`, `DiscordMatchdayViewService.buildMatchdayDiscordModel`, `StandingsService.snapshotMatchdayStaleness`) applied across 4 sites; lock-IT async-latch dance removal in `ImportLockBannerAdviceIT` / `ImportConcurrentLockIT` / `ImportLockedPostRejectorIT`
- ✓ StringUtils Blank-Check Sweep (Phase 103): replaced 85 occurrences of manual `s != null && !s.isBlank()` (41×) + `s == null || s.isBlank()` (47×) with `org.springframework.util.StringUtils.hasText(s)` (and `!hasText(s)` negation) across 42 production files; new `config/rewrite-validate-hasText.yml` OpenRewrite `FindMethods` detector recipe acts as closing-validation oracle; mechanical 1:1 substitution, no behavior change, no coverage regression — pure Spring-Native consistency refactor per CLAUDE.md "Spring-Native over JDK-Built-In"

### Validated (v1.14)

- ✓ `DevDataSeeder` + `TestDataService` load only when the active profile contains `dev`; `local`/`docker`/`prod` instantiate neither bean (reverts the v1.11 `@Profile({"dev","local"})` drift from commit `598d1431`) — Phase 104 (SAFE-01)
- ✓ `LocalProfileDataSafetyIT` regression fence: `@ActiveProfiles("local")` + H2-override asserts both seeder beans absent; build goes red on any future `@Profile`-widening / `@ConditionalOnProperty`-flip / `@Component` re-introduction — Phase 104 (SAFE-02)
- ✓ Team-card PNG output regenerated to the external Claude-Design "Carbon HUD" handoff (layout/typography/spacing/logo/hierarchy) + `TeamCardService` color-robustness patch (`accentVisColor` + `onPrimaryColor`) — Phase 105 (CARD-01)
- ✓ All card-consumer integration paths backward-compatible after the redesign (Discord auto-post POST-02, manual Re-Post + Refresh, admin preview) with no caller changes; extends to all 16 graphics — no `GraphicService` signature or model-variable changes except the two named backend tweaks — Phase 105 (CARD-02)
- ✓ 5 composite/matchup graphics (`settings-`/`lineup-`/`results-`/`match-results-`/`provisional-scores-render.html`) restyled as drop-in Carbon/Gold replacements with unchanged `th:*` bindings; `ProvisionalScoresGraphicService` `raceLabel` set only for > 1-race matches (both-branch IT) — Phase 105 (CARD-03)
- ✓ 5 matchday/list graphics + stream overlay (geometry/skew/transparency preserved exactly) restyled to Carbon/Gold; 4 non-handoff templates (`matchday-pairings-` + 3 `playoff-round-*-render.html`) rebuilt by analogy so no old/new style break occurs when graphics post together — Phase 105 (CARD-04)

### Active

v1.14 shipped 2026-05-29. Next milestone to be defined via `/gsd-new-milestone` — see § "Next Milestone (to be defined)" above for the audit-surfaced carry-forward candidates.

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
| In-milestone Nyquist closure (v1.11 Option A) mirrors cross-milestone Phase-87 pattern (v1.10) | After Phase 87 closed v1.10 Nyquist debt, the milestone audit surfaced the same draft VALIDATION pattern in v1.11 itself. Resolution path: 6 retroactive `/gsd-validate-phase` runs + 1 retroactive 86-VERIFICATION.md inline (same morning), avoiding a v1.12 Phase-88 carry-forward. Closing pattern: precedent for future milestones — if Nyquist debt accumulates during execution, prefer inline closure post-audit over cross-milestone closure phase | ✓ v1.11 |
| Playwright fork-channel corruption fix: pre-install all 3 default browsers + actions/cache@v4 (CI follow-up) | `Playwright.create()` validates all default browsers (Chromium + Firefox + WebKit) on first use, not just `chromium()`. Mid-Surefire auto-download corrupts fork-channel stdout → Maven exit 1, 0 failing tests. Cache footprint (~360 MiB) and 30 s cold-install acceptable for stable forks | ✓ v1.11 |
| PERF-06 v1.12 CI baseline re-harvested (Phase 91 D-17) | 5 `workflow_dispatch` runs on the v1.12 milestone PR-branch HEAD SHA; D-17 trigger-equivalence (PR-branch CI = post-merge master CI because `ci.yml` runs identical steps regardless of trigger event); median of middle 3 after drop min+max per D-10; variance 18.2 % within 20 % tolerance | ✓ v1.12 (CI E2E median **17:39**, was 23:00 in v1.11; Δ−23.3 %) |
| `release.yml` SemVer-strict tag sort + idempotency guard (Phase 88 REL-01) | 4-milestone regression (v1.8 final → v1.11) caused by `git describe` returning legacy short-form tags like `v1.5` (no 3-part SemVer); pre-`versions:set` guard refuses BEFORE the 19-minute build runs, fast-fails idempotent re-runs; BREAKING CHANGE footer detected via `%B` + `^BREAKING[ -]CHANGE:` regex; dry-run via `workflow_dispatch -F dry-run=true` skips push/tag/release but exercises version-determination | ✓ v1.12 (dispatch run 26080324918 success; v1.10.0/v1.11.0 retroactively published via runbook 2026-05-20; ready to produce v1.12.0 on squash-merge) |
| Defensive Future-Proofing for DRIV-02 template surface (Phase 88 Plan-05 user-accepted override) | Deferred-debug-doc's three surfaces (`TEAM_NOT_IN_REGULAR_PHASE` warning, per-row Group cell in `driver-import-preview.html`, page-wide `showGroupColumn` aggregation) all do NOT exist in v1.12 code — refactored away between deferral and Phase 88 start. Only service+controller API surfaces wired; template gate deferred until a real Group-UI feature is requested. Override recorded in 88-VERIFICATION.md frontmatter | ✓ v1.12 (override) |
| Sealed `GoogleApiException` extends `IOException` for backward-compat (Phase 91 Plan 91-02 D-06) | Sealed hierarchy permits `Transient`/`Auth`/`NotFound`/`Permission` subtypes; extending `IOException` keeps callers that catch the supertype working unchanged while new code can pattern-match on subtypes for category-specific UX. Java 25 lacks sealed-exhaustiveness on `catch` — defensive `catch (GoogleApiException e)` blocks are required by javac (root cause of Δ−0.44 pp JaCoCo delta) | ✓ v1.12 |
| Pre-merge runbook execution for REL-02 (Phase 88 → audit-time inversion 2026-05-20) | Verification (2026-05-19) accepted REL-02 as "post-merge operator action". Audit at 2026-05-20 identified that running it pre-merge is REQUIRED — otherwise the hardened workflow reads `v1.9.0` as last tag and mis-tags `v1.10.0` against the v1.12 HEAD commit + locks out `--target 45aabfd0` retroactive tag via idempotency guard. Runbook timing pinned to "pre-merge" via commit `871d42ff`; 3 runbook bugs (Short-SHA in `gh release create --target`, bash-loop-in-zsh, fine-grained PAT limitation) patched in `1180a627` after live execution | ✓ v1.12 |
| Three explicit `gh api -X DELETE` commands replace bash interactive loop (Phase 88 REL-02 runbook fix 2026-05-20) | The original Section 4b confirmation loop used `read -p` which is bash-only — in zsh `-p` reads from a coprocess (not a prompt string), so the loop blocks silently. Three explicit DELETE commands per legacy tag are shell-agnostic and each command's execution IS the confirmation. Same Section 4 intro updated to match | ✓ v1.12 |
| Hybrid Bot + Webhook split (Phase 93 D-01) | Discord Bot REST (`/api/v10`) handles channel/role/category CRUD where stateful identity is needed; Webhooks handle all message posting because they allow per-message author identity (team-branded avatar) without requiring an always-online bot connection. Eliminates the deployment-model change a slash-command-enabled bot would force | ✓ v1.13 |
| Outbound-only Discord integration (Phase 93 D-02) | No inbound slash-commands, polls, or reaction-reads. The app remains a local admin tool with no always-online endpoint requirement. DISC-FUTURE-01 captures the deployment-model change required to add inbound | ✓ v1.13 |
| Sealed `DiscordApiException` hierarchy with 4 permits (Phase 93 D-04) | `Transient`/`Auth`/`NotFound`/`CategoryFull` — same shape as Phase 91 `GoogleApiException`. Permits typed catches and 4 distinct user-facing error categories. CategoryFull is Discord-specific (50-channels-per-category limit) | ✓ v1.13 |
| Operator-button trigger model, not auto-trigger on DB events (Phase 95 D-?) | Operator retains full control over what posts; auto-edit hooks fire only on field changes to existing posts, never on initial creation. DISC-FUTURE-02 captures the auto-trigger pipeline once edit-confidence is established | ✓ v1.13 |
| `DiscordPostService.postOrEdit` idempotency dispatcher on `(match_id, post_type)` key (Phase 95 POST-01) | Single lookup → POST or PATCH branch. Eliminates duplicate-post risk on operator-click retries. Stored `message_id` enables Webhook-PATCH edits across all 11 post types via a single code path | ✓ v1.13 |
| `?thread_id=` query-param forum-thread posting + `unarchiveIfArchived` (Phase 96 FORUM-02) | Discord forum-threads auto-archive after inactivity; posting to an archived thread fails. `unarchiveIfArchived` pre-unarchives via REST before each webhook POST. Single `?thread_id=` param reuses the existing Webhook client without a parallel ForumWebhookClient | ✓ v1.13 |
| `Match Days Archive {year}[ ({num})]` regex category resolver (Phase 94 CHAN-03) | 50-channels-per-category is a hard Discord limit. Operator picks any category with `< 50` channels via modal; default-suggestion is highest-num for current year. Year-keyed naming preserves chronological retrieval without manual category creation | ✓ v1.13 |
| Phase 99 + Phase 102 pre-merge audit-polish + code-review-fix closeout phases | Mid-milestone audit (2026-05-25) surfaced 5 tech_debt items + 67 review findings across Phases 92-101. Closing inline via Phase 99 (doc-debt) + Phase 102 (code fixes) before milestone close kept v1.13 from carrying debt into v1.14. Pattern: inline closure post-audit > cross-milestone closure phase (validated by Phase 87 v1.10/v1.11 precedent) | ✓ v1.13 |
| Phase 100 `md{N}-{phase}-[{group}-]{home}-vs-{away}` two-scheme coexistence (D-08) | Existing match-channels stay on Phase-94 format (`md{N}-{home}-vs-{away}`); only post-Phase-100 channels use the new scheme. No migration script. Two-scheme coexistence acceptance documented in STATE.md Deferred Items | ✓ v1.13 |
| Phase 101 26-entity backup scope via `org.ctc.discord.model.*` package-filter extension (D-08) | Phase 72 D-15's `org.ctc.domain.model.*`-only filter excluded Discord entities — silent data loss on restore. Filter extension is the structurally identical fix (no new opt-in, no marker), `DiscordGlobalConfig` + `DiscordPost` automatically picked up by next-boot topo-sort. `discord_post` pinned-last because FKs are `@Column UUID` (not `@ManyToOne`) — topo-sort cannot detect edges | ✓ v1.13 |
| Phase 101 SCHEMA_VERSION 1 → 2 with lenient `IN (1, 2)` accept (D-09, D-10, D-11) | Pre-v1.13 v1 backups remain restorable; importer leaves Discord-section JSON files empty (self-healed on first operator page-load via `DiscordGlobalConfigService.getOrInitialize()`). V8-V15 columns on Match/Team/Matchday/Season land NULL after v1 restore. Avoids forcing all operators to take a fresh v2 backup before upgrade | ✓ v1.13 |
| `feat(v1.13): discord integration & carry-forwards` squash-merge subject (operator runbook) | Conventional Commit subject is mandatory for Semantic Release MINOR bump → `v1.13.0` per CLAUDE.md "Git Workflow" + `docs/operations/release-runbook.md § 6`. Release CI tags `v1.13.0` after merge — never manually per "No Local Git Tags" | ✓ v1.13 |
| Two-phase split (Data Safety 104 + Team Card 105) instead of bundling (v1.14 D-Two-Pillars) | The two workstreams share no entities, services, templates, or migrations; separate phases give each its own goal + verify gate. Phase 104 first because it was immediately executable (fix staged) and establishes a clean green baseline before UI/CSS churn | ✓ v1.14 |
| SAFE-01 + SAFE-02 in one atomic phase (v1.14 D-Fix-And-Fence-Together) | Shipping the bean-scope flip without the regression IT re-opens the v1.11 drift on the next refactor; shipping the IT without the flip tests a state the code isn't in | ✓ v1.14 |
| Scope expanded team-card-only → all 16 admin graphics (Phase 105 D-01, D-03) | The external Claude-Design session delivered a coherent Carbon/Gold system; applying it across the board avoids a visible old/new style break when graphics post together. CARD-03 + CARD-04 added, ROADMAP retitled "Carbon HUD Graphics Redesign" | ✓ v1.14 |
| Single re-scoped Phase 105, not split into 105/106/107 (Phase 105 D-02) | `plan-phase` split the redesign into 4 wave-ordered plan groups (Team Card / Composites / Matchday-List / Overlay+Analogy) under one phase + one end-of-phase verify gate, rather than three sequential phases | ✓ v1.14 |
| 4 non-handoff templates rebuilt by analogy, not deferred (Phase 105 D-06) | `matchday-pairings` + 3 `playoff-round-*` have no handoff reference but post alongside Carbon graphics; rebuilding them by analogy (each `playwright-cli`-verified against Carbon tokens) prevents a mixed-style render | ✓ v1.14 |
| `TeamCardService` color-robustness patch (Phase 105 D-04) | After `gradientColor`, compute `accentVisColor` (accent < 28 → fall back to primary) + `onPrimaryColor` (primary luminance > 140 → dark text, else white) reusing existing `relativeLuminance`; template reads both via Thymeleaf-Elvis. JaCoCo-excluded service → no coverage impact | ✓ v1.14 |
| `ProvisionalScoresGraphicService` `raceLabel` conditional (Phase 105 D-05) | `raceLabel` set only for > 1-race matches (else `null`) so the new `.race-chip` disappears on single-race matches; existing IT updated to assert both branches | ✓ v1.14 |
| `feat(v1.14): team card redesign & data safety` squash-merge subject | Conventional Commit subject mandatory for Semantic Release MINOR bump → `v1.14.0`; PR #131 squash-merge at close. Release CI tags `v1.14.0` after merge — never manually per "No Local Git Tags" | — Pending (merge at close) |

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

**v1.13 Phase 101 update — 26-entity scope.**
Phase 101 (2026-05-26) raised the scope to 26 entities by extending the package-filter to also accept `org.ctc.discord.model.*` (in addition to `org.ctc.domain.model.*`). The two added entities are `DiscordGlobalConfig` (single-row global config, V8 + V9 + V13 + V15 columns) and `DiscordPost` (idempotency-tracking table from V12 + V14). `BackupSchema.SCHEMA_VERSION` advances from `1` to `2`; `BackupImportService` now accepts `schema_version IN (1, 2)` via the `SUPPORTED_SCHEMA_VERSIONS` constant. Pre-v1.13 v1 backups remain restorable — Discord-section JSON files are absent from v1 ZIPs, so the importer naturally leaves `discord_global_config` empty (self-healed on first operator page-load via `DiscordGlobalConfigService.getOrInitialize()`) and `discord_post` empty; V8-V15 columns on Match/Team/Matchday/Season land NULL after a v1 restore. `DiscordPost` exposes its five FK columns as `@Column UUID` (not `@ManyToOne`), so the topo-sort cannot detect dependency edges — `BackupSchema.initializeExportOrder()` post-processes the order to pin `discord_post` last, guaranteeing parent rows are restored first. Cross-guild restore is documented as undefined behaviour in `docs/operations/discord-integration.md` § Backup & Restore semantics — operator preserves `guild_id` identity across export/import. `webhook_token` + `role_id` + `thread_id` are PII-equivalent secrets now persisted into the backup ZIP; operator-side filesystem access control is the v1.13 mitigation (encryption at rest deferred to v1.14+).

**ObjectMapper isolation (D-11 amended per RESEARCH §Pitfall P-2).**
`BackupObjectMapperConfig` declares BOTH a `@Primary` default mapper (built from the auto-config `Jackson2ObjectMapperBuilder`, preserving admin REST/AJAX behaviour) AND a `@Qualifier("backupObjectMapper")` strict mapper (`FAIL_ON_UNKNOWN_PROPERTIES=true`, `WRITE_DATES_AS_TIMESTAMPS=false`, `JavaTimeModule` registered, Phase 73 MixIn `@Component` beans injected via `List<Module>`). Defining a single qualified bean would silently disable Spring's auto-configured default via `@ConditionalOnMissingBean(ObjectMapper.class)` — verified against spring-projects/spring-boot#47379.

**Audit log persistence (`DataImportAudit` Lombok class, NOT record).**
`org.ctc.backup.audit.DataImportAudit` is a Lombok `@Entity` (Hibernate 7 cannot proxy Java records — RESEARCH §Pitfall P-1 corrects D-08's preference). The entity deliberately does NOT extend `BaseEntity` so the Phase 75 writer fully controls `executedAt` without `AuditingEntityListener` interference. V7 columns use `LONGTEXT` (portable across H2 2.x and MariaDB 10.7+) for the JSON-shape text fields `table_counts_wiped` and `table_counts_restored` (D-09 — native `JSON` rejected for v1.10 dialect drift).

## Evolution

This document evolves at phase transitions and milestone boundaries.

---
**Branch:** `gsd/v1.15-ci-and-race-defaults` (authoritative — all v1.15 planning, discuss, plan, and execute commits go here)

*Last updated: 2026-05-30 — milestone v1.15 (CI Optimisation & Race/Match Defaults) started on branch `gsd/v1.15-ci-and-race-defaults`. Scope confirmed across three strands: (1) CI Optimisation — path-aware CI (approach A+C: conditional steps on `ci.yml` required jobs + hard `paths-ignore` on non-required CodeQL/MariaDB-smoke), E2E runtime reduction, caching, flaky/rerun reduction; (2) Race/Match Defaults — prefill (scoring scheme / legs-races / date-time inherited from season/matchday), missing-driver "n/a" across Lineup/Scorecard/Provisional graphics + scoring as 0 pts/no position, walkover handling analogous to `Match.bye`; (3) new template-variable-driven Lobby Settings graphic (Carbon HUD drop-in, no new data model, admin preview + Discord auto-post + template editor). Requirements + roadmap next. Previous entry: 2026-05-29 — after v1.14 milestone (Team Card Redesign & Data Safety, Phases 104-105, 5 plans, 51 commits on branch `gsd/v1.14-team-card-redesign`). Two unrelated pillars shipped: (1) Data-Safety Lockdown — `DevDataSeeder` + `TestDataService` reverted to `@Profile("dev")` with `LocalProfileDataSafetyIT` regression fence (SAFE-01/02); (2) Carbon HUD Graphics Redesign — all 16 Playwright-rendered admin graphics restyled to the external Claude-Design Carbon/Gold system as drop-in template replacements with full card-consumer backward-compat and two named backend tweaks (`TeamCardService` color-robustness, `ProvisionalScoresGraphicService` `raceLabel` conditional) (CARD-01..04). Phase 105 was re-scoped mid-milestone from team-card-only to all-graphics after the handoff delivered a coherent system (D-01/D-03). Milestone audit verdict: passed — 6/6 requirements, both phases verified, integration PASS, Nyquist compliant. Final state: JaCoCo ~89.42 % / 0 SpotBugs findings / 0 CodeQL HIGH+ alerts. PR #131 ready for squash-merge with subject `feat(v1.14): team card redesign & data safety` → release CI tags v1.14.0. Previous entry: 2026-05-28 after v1.13 milestone — Discord Integration & Carry-Forwards (Phases 92-103, 43 plans, 399 commits): hybrid Bot + Webhook architecture, 11 post types, match-channel lifecycle, forum-thread linking, 9 new Flyway migrations (V8-V16); shipped at 2393 tests / JaCoCo 89.43 % / 0 SpotBugs.*
