# CTC Manager — Gran Turismo Racing League Manager

## What This Is

Gran Turismo Racing League Management application (Spring Boot 4 / Thymeleaf / MariaDB). Manages Seasons, Matchdays, Matches, Races, Teams, Drivers, Scoring, and Standings for the Community Team Cup league. After four milestone cycles (v1.0 Tech Debt, v1.1 Concerns Cleanup, v1.3 English Test Data, v1.5 Code Review Fixes), the codebase is architecturally clean, security-hardened, convention-compliant, and production-ready.

## Core Value

Architectural Consistency: All controllers delegate to services, exception handling is centralized, and the production environment is secured.

## Current State (after v1.8)

- **Codebase:** ~15k LOC Java (Prod) + ~21k LOC Java (Tests), 1064 Tests, 82%+ Coverage
- **Tech Stack:** Spring Boot 4.0.5, Java 25, MariaDB 11 / H2, Thymeleaf, Playwright
- **Security:** HTTP Basic Auth (prod/docker), SSRF hostname blocklist, path traversal defense, CSRF tokens on AJAX POSTs, SpEL/OGNL injection validation, Content-Disposition sanitization, MatchdayForm DTO (mass assignment protection)
- **Architecture:** Clean 3-tier (Controller → Service → Repository), no God Services, centralized exception handling, domain services fully decoupled from admin layer, RaceLineup as source of truth for driver-team assignment
- **Database:** 36 FK-Indexes, 28 @EntityGraph annotations, Flyway-managed
- **Templates:** CSS utility classes instead of inline styles, TemplateManageable generic dispatch
- **Data:** All UI text and code comments in English, dev profile with fictive test data

## Current Milestone: v1.9 Season Phases & Groups

**Goal:** Saison vom flachen Container zur Klammer mit mehreren Phasen (Regular / Playoff / Placement) und optionalen Sub-Gruppen pro Phase weiterentwickeln, sodass Gruppen-Saisons ohne Multi-Saison-Workaround abbildbar werden und der Driver-Import wieder eindeutig auflösbar ist.

**Target features:**
- `SeasonPhase`-Entity (REGULAR/PLAYOFF/PLACEMENT) mit Format/Scoring/Dates auf Phase-Ebene
- `SeasonPhaseGroup` als Sub-Gruppen einer GROUPS-Phase (eigener Roster, eigene Standings)
- `PhaseTeam`-Roster (Team↔Phase, optional Group); `SeasonDriver` strukturell unverändert
- `Playoff` von Saison auf Phase umgehängt; M:N `playoff_seasons` entfällt
- Mechanische Migration: jede Bestandssaison → 1 REGULAR-Phase + ggf. 1 PLAYOFF-Phase
- Driver-Import: `findByYearAndNumber` statt `findByYear`; Group implizit über das Team
- UI: Saison-Detail mit Phasen-Tabs, Gruppen als zweite Tab-Ebene; Standings pro Gruppe + Combined-View
- `TestDataService` und `DevDataSeeder` direkt im neuen Modell (keine Backward-Compat-Helpers)

**Foundation:** `/Users/jegr/.claude/plans/ich-bin-mit-dem-pure-gem.md` — Architektur-Plan aus Brainstorming-Session, Basis dieses Meilensteins.

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

### Active

(None — awaiting next milestone definition via `/gsd-new-milestone`.)

#### Shipped Milestone: v1.6 Static Site Quality

**Goal:** Fix broken links, add missing content, improve navigation/cross-linking, and deliver a polished, accessible static site with professional UX.

All 56 requirements complete (22 original + 26 extended + 3 YouTube hero + 5 alltime pages). See REQUIREMENTS.md for full traceability. Pending `/gsd-complete-milestone` archival.

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

## Evolution

This document evolves at phase transitions and milestone boundaries.

---
*Last updated: 2026-04-25 — v1.8 milestone shipped and archived (PR #116 merged as 042cfbf)*
