# CTC Manager — Gran Turismo Racing League Manager

## What This Is

Gran Turismo Racing League Management application (Spring Boot 4 / Thymeleaf / MariaDB). Manages Seasons, Matchdays, Matches, Races, Teams, Drivers, Scoring, and Standings for the Community Team Cup league. After four milestone cycles (v1.0 Tech Debt, v1.1 Concerns Cleanup, v1.3 English Test Data, v1.5 Code Review Fixes), the codebase is architecturally clean, security-hardened, convention-compliant, and production-ready.

## Core Value

Architectural Consistency: All controllers delegate to services, exception handling is centralized, and the production environment is secured.

## Current State (after v1.5)

- **Codebase:** ~14k LOC Java (Prod) + ~19k LOC Java (Tests), 922 Tests, 82%+ Coverage
- **Tech Stack:** Spring Boot 4.0.5, Java 25, MariaDB 11 / H2, Thymeleaf, Playwright
- **Security:** HTTP Basic Auth (prod/docker), SSRF hostname blocklist, path traversal defense, CSRF tokens on AJAX POSTs, SpEL/OGNL injection validation, Content-Disposition sanitization, MatchdayForm DTO (mass assignment protection)
- **Architecture:** Clean 3-tier (Controller → Service → Repository), no God Services, centralized exception handling, domain services fully decoupled from admin layer, RaceLineup as source of truth for driver-team assignment
- **Database:** 36 FK-Indexes, 28 @EntityGraph annotations, Flyway-managed
- **Templates:** CSS utility classes instead of inline styles, TemplateManageable generic dispatch
- **Data:** All UI text and code comments in English, dev profile with fictive test data

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

### Active

#### Current Milestone: v1.6 Static Site Quality

**Goal:** Fix broken links, add missing content, improve navigation/cross-linking, and deliver a polished, accessible static site with professional UX.

**Target features:**

- ~~Fix all broken navigation links (archive slug mismatch, driver ranking 404, absolute paths)~~ — Phase 37 complete
- ~~Display season year and number across all pages~~ — Phase 38 complete
- ~~Add inline links from standings to teams, from rankings to drivers, from matchdays to profiles~~ — Phase 39 complete
- Add season subnavigation (matchdays, standings, driver ranking, playoff per season)
- ~~Filter test seasons from archive, fix empty match-meta and period column~~ — Phase 38 complete
- Remove inline styles in archive and driver-profile templates
- ~~Fix broken team logo paths on static site~~ — Phase 37 complete
- Add skip-link, nav active-state, breadcrumbs for accessibility
- Highlight match winner in match cards
- Mobile scroll indicator for tables
- Footer with useful links
- Aria-label improvements, hover transitions, cursor:pointer on clickables

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

## Evolution

This document evolves at phase transitions and milestone boundaries.

---
*Last updated: 2026-04-16 after Phase 38 (Season Content & Data Filtering) complete*
