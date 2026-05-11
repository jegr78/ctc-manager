# CTC Manager — Gran Turismo Racing League Manager

## What This Is

Gran Turismo Racing League Management application (Spring Boot 4 / Thymeleaf / MariaDB). Manages Seasons, Matchdays, Matches, Races, Teams, Drivers, Scoring, and Standings for the Community Team Cup league. After four milestone cycles (v1.0 Tech Debt, v1.1 Concerns Cleanup, v1.3 English Test Data, v1.5 Code Review Fixes), the codebase is architecturally clean, security-hardened, convention-compliant, and production-ready.

## Core Value

Architectural Consistency: All controllers delegate to services, exception handling is centralized, and the production environment is secured.

## Current State (after v1.9, Phase 71 of v1.10 landed)

- **Codebase:** ~17k LOC Java (Prod) + ~25k LOC Java (Tests), 1370 Tests (1227 Surefire + 112 Failsafe-IT + 31 E2E), 89.44 % Line Coverage
- **Tech Stack:** Spring Boot 4.0.6, Java 25, MariaDB 11 / H2, Thymeleaf 3.1.5 (pinned), Playwright
- **Security:** HTTP Basic Auth (prod/docker), SSRF hostname blocklist, path traversal defense, CSRF tokens on AJAX POSTs, SpEL/OGNL injection validation, Content-Disposition sanitization, MatchdayForm DTO (mass assignment protection)
- **Architecture:** Clean 3-tier (Controller → Service → Repository), no God Services, centralized exception handling, domain services fully decoupled from admin layer, RaceLineup as source of truth for driver-team assignment, sitegen decomposed into 5 page-generator beans + SiteSlugger + TemplateWriter (D-20)
- **Database:** 36 FK-Indexes, 28 @EntityGraph annotations, Flyway-managed; Phase/Group model: `season_phases`, `season_phase_groups`, `phase_teams` tables driving Matchday and Playoff phase association
- **Templates:** CSS utility classes instead of inline styles, TemplateManageable generic dispatch, phase-/group-aware public site templates (`standings.html`, `matchdays.html`, `driver-ranking.html`, `team-profile.html`, `driver-profile.html`)
- **Data:** All UI text and code comments in English, dev profile with fictive test data including GROUPS multi-phase fixture (Season 2023) + Empty-Phase fixture for D-22 coverage
- **Public Site:** Phase-tab row + group-sub-tab row, per-phase URL variants, Phase Breakdown sections on team/driver profiles, alltime aggregation across all phases (D-19 TRACKED BEHAVIOR CHANGE), desktop sticky table headers

## Current Milestone: v1.10 Spring Boot Upgrade & Data Export/Import

**Goal:** Plattform-Hygiene (Spring Boot 4.0.6 + Thymeleaf-3.2-Template-Audit) und neue Admin-Funktion für strukturellen Daten-Export/Import als ZIP-Paket — für Backup/Recovery vor riskanten Operationen und Migration zwischen dev↔prod-Environments.

**Target features:**

### A. Spring Boot Platform Upgrade

- Spring Boot 4.0.5 → 4.0.6 (Maven `spring-boot-starter-parent` bump, plus transitiver Thymeleaf 3.1.5 (CVE-2026-40478 SpEL hardening))
- Vorsorglicher Audit aller ~80 Templates auf Thymeleaf-3.2-Inkompatibilitäten (Fragment-Parameter-Ternaries in `th:replace="...layout(${cond ? 'A' : 'B'}, ...)"`)
- Fix der 3 bekannten Templates (`match-scoring-form`, `race-scoring-form`, `season-phase-form` — alle Zeile 3) plus weiterer im Audit gefundener: Title-Computation in Controller (`pageTitle` Model-Attribut)
- `./mvnw verify -Pe2e` grün auf 4.0.6, JaCoCo ≥ 82 % gehalten

### B. Strukturelle Daten-Export/Import (Admin)

- **Export:** Admin-Button `Export Backup` → ZIP-Paket mit `data.json` (operative Daten) + `uploads/`-Verzeichnis (Logos, CTC-Grafiken, Race-Attachments)
- Scope: Seasons → SeasonPhases → SeasonPhaseGroups → PhaseTeams → Teams → SeasonTeams → Drivers → SeasonDrivers → PsnAlias → Matchdays → Matches → Races → RaceLineups → RaceResults → Playoffs → PlayoffMatchups → PlayoffSeeds → RaceScoring → MatchScoring (operative Domain-Daten, ohne Audit-Rauschen)
- Header mit `schema_version` + `app_version` + `export_date` (Forward-Compat / Inkompatibilitäts-Erkennung)
- **Import:** Admin-Button `Import Backup` → Upload ZIP → Preview-Screen → Confirm-Dialog
- **Replace-All Conflict-Policy:** operative Tabellen werden vor Import in einer Transaktion geleert, dann 1:1 wiederhergestellt; Confirm-Dialog mit "Diese Aktion löscht ALLE operativen Daten" Pflicht
- Schema-Version-Check: ZIP mit nicht passender `schema_version` wird mit klarer Fehlermeldung abgelehnt (kein implizites Upgrade)
- MVP: ganzes ZIP einlesen (kein Per-Saison-Selector — späteres Milestone-Feature)
- Audit-Log-Eintrag beim Import (wer, wann, anzahl gewiped Rows pro Tabelle, anzahl restored Rows pro Tabelle)

**Key context:**

- v1.9 endete mit reverteiertem Spring-Boot-Bump wegen Thymeleaf-3.2-Template-Inkompatibilität — v1.10 fixt das Problem strukturell statt nur reaktiv
- Use-Case Migration dev↔prod ist real: Aktuell nur über `mysqldump` machbar, Admin-UI-Button ist erheblich friction-reduzierend
- ZIP statt JSON-only: Logos sind oft groß und schwer manuell nachzupflegen — komplette Round-Trip-Fähigkeit ist Gold wert
- Replace-All ist sicherer als Merge (Schema-Drift kann inkonsistente Zustände erzeugen)

**Carried over from v1.9 deferred (candidates für nachgelagerte v1.11+ Milestones, NICHT v1.10):**

- Quality Gate Lock / CI comment-noise guard (Phase 67 D-06 forward commitment)
- Plan SUMMARY frontmatter sweep for phases 56/57/62/64 (bookkeeping, ~15 plan SUMMARYs)
- Per-group matchday generation UI affordance (`SeasonController.generateMatchdays:251` Rule-3 deviation)
- `StandingsController.java:139` lazy collection style cleanup
- UAT-02 (legacy season visual smoke against real pre-V4 production data) on next deploy

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

### Active

(None — awaiting next milestone definition via `/gsd-new-milestone`.)

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

## Evolution

This document evolves at phase transitions and milestone boundaries.

---
*Last updated: 2026-05-09 — v1.9 milestone shipped: Season Phases & Groups, all 15 phases (56-70) verified PASSED, 38/38 requirements satisfied, 1227 unit + 31 Playwright E2E tests green, JaCoCo line coverage 87.02% (gate 82%), live UAT D-22 confirmed Saison 2023 driver import on MariaDB (287/357/0 errors)*
