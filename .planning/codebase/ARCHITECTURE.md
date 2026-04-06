# Architecture

**Analysis Date:** 2026-04-03

## Pattern Overview

**Overall:** Layered MVC (Spring Boot + Thymeleaf server-side rendering)

**Key Characteristics:**
- Classic three-tier architecture: Controller -> Service -> Repository -> Database
- Domain-centric package structure with clear separation between admin UI, domain logic, and auxiliary modules
- OSIV (Open Session in View) deliberately enabled for lazy-loading in Thymeleaf templates
- No REST API -- all interactions are server-rendered HTML with form submissions and redirects
- Thin controllers delegate all business logic to service classes
- DTOs used for form binding (POST), entities passed directly to templates (GET) thanks to OSIV

## Layers

**Admin Controllers (`org.ctc.admin.controller`):**
- Purpose: HTTP request handling for the admin UI -- receives requests, delegates to services, populates Model or redirects
- Location: `src/main/java/org/ctc/admin/controller/`
- Contains: 17 `@Controller` classes, 1 `@ControllerAdvice` (`GlobalModelAdvice`)
- Key files: `SeasonController.java`, `MatchdayController.java`, `RaceController.java`, `MatchController.java`, `PlayoffController.java`, `TeamController.java`, `DriverController.java`, `CarController.java`, `TrackController.java`
- Depends on: Domain services (`domain.service`), admin services (`admin.service`)
- Used by: Thymeleaf templates (via Spring MVC)
- Pattern: POST-Redirect-GET with flash attributes for success/error messages

**Admin DTOs (`org.ctc.admin.dto`):**
- Purpose: Form-binding objects for POST requests (Mass Assignment protection)
- Location: `src/main/java/org/ctc/admin/dto/`
- Contains: Form DTOs (`TeamForm`, `CarForm`, `TrackForm`, `DriverForm`, `RaceForm`, etc.), display DTOs (`MatchdayDto`, `SeasonDriverGroupDto`, `MatchdayGraphicData`, `PowerRankingsGraphicData`)
- Depends on: Nothing (POJOs/records)
- Used by: Controllers

**Admin Services (`org.ctc.admin.service`):**
- Purpose: Graphic/image generation using Playwright (Chromium headless)
- Location: `src/main/java/org/ctc/admin/service/`
- Contains: `AbstractGraphicService` (base class), `TeamCardService`, `LineupGraphicService`, `ResultsGraphicService`, `MatchResultsGraphicService`, `MatchdayOverviewGraphicService`, `MatchdayResultsGraphicService`, `MatchdayScheduleGraphicService`, `OverlayGraphicService`, `SettingsGraphicService`, `PowerRankingsGraphicService`, `TemplatePreviewService`
- Pattern: Render Thymeleaf HTML template -> take Playwright screenshot -> save PNG
- Depends on: Thymeleaf `TemplateEngine`, Playwright, file system
- Used by: Admin controllers (TeamCardController, RaceController, MatchdayController, etc.)

**Domain Model (`org.ctc.domain.model`):**
- Purpose: JPA entities representing the core domain
- Location: `src/main/java/org/ctc/domain/model/`
- Contains: 18 entity classes + 1 `@MappedSuperclass` (`BaseEntity`) + enums
- Depends on: JPA/Hibernate, Jakarta Validation
- Used by: Repositories, services, controllers (via OSIV)

**Domain Repositories (`org.ctc.domain.repository`):**
- Purpose: Spring Data JPA interfaces for database access
- Location: `src/main/java/org/ctc/domain/repository/`
- Contains: 17 repository interfaces extending `JpaRepository`
- Depends on: Spring Data JPA, domain model
- Used by: Domain services, admin services, import services

**Domain Services (`org.ctc.domain.service`):**
- Purpose: Business logic -- scoring, standings, rankings, season/team/match management
- Location: `src/main/java/org/ctc/domain/service/`
- Contains: 14 service classes
- Key files: `ScoringService.java` (points calculation + match score aggregation), `StandingsService.java` (league table), `DriverRankingService.java`, `SeasonManagementService.java`, `TeamManagementService.java`, `MatchService.java`, `MatchdayService.java`, `MatchdayGeneratorService.java`, `RaceManagementService.java`, `PlayoffService.java`, `SwissPairingService.java`, `RaceLineupService.java`, `DriverService.java`, `FileStorageService.java`
- Depends on: Domain repositories, domain model
- Used by: Admin controllers, site generator, CSV import

**Data Import (`org.ctc.dataimport`):**
- Purpose: CSV file import for race results + Google Sheets/Calendar integration
- Location: `src/main/java/org/ctc/dataimport/`
- Contains: `CsvImportService.java` (parse/preview/execute flow), `CsvImportController.java`, `ScorecardParser.java`, `GoogleSheetsService.java`, `DriverMatchingService.java`, `GoogleCalendarService.java`
- Pattern: Two-phase import -- parse+preview (showing fuzzy matches) then confirm+execute
- Depends on: Domain repositories, `ScoringService`

**GT7 Sync (`org.ctc.gt7sync`):**
- Purpose: Scrape Gran Turismo 7 car/track data from external website, sync to local DB
- Location: `src/main/java/org/ctc/gt7sync/`
- Contains: `Gt7ScraperService.java` (HTML scraping via Jsoup), `Gt7SyncService.java` (preview + execute), `Gt7SyncController.java`, `Gt7SyncPreview.java`
- Pattern: Two-phase sync with preview (like import)
- Depends on: Car/Track repositories, `FileStorageService`

**Site Generator (`org.ctc.sitegen`):**
- Purpose: Generate static HTML pages from Thymeleaf templates for public-facing league website
- Location: `src/main/java/org/ctc/sitegen/`
- Contains: `SiteGeneratorService.java`, `SiteGeneratorController.java`, `model/RaceView.java`
- Pattern: Reads all season data, renders Thymeleaf templates to static HTML files, copies assets
- Output: `docs/site/` (or `target/site` in dev)
- Depends on: Domain repositories, `StandingsService`, `DriverRankingService`, `PlayoffService`

**Application Bootstrap (`org.ctc.admin`):**
- Purpose: Application startup, configuration, dev data seeding
- Location: `src/main/java/org/ctc/admin/`
- Contains: `WebConfig.java` (upload dir resource handler), `DevDataSeeder.java` (dev profile), `DemoDataSeeder.java` (demo profile), `TestDataService.java` (shared test data creation)
- Pattern: `CommandLineRunner` for dev/demo profile seeding

## Data Flow

**Race Result Import (CSV):**

1. User uploads CSV file on `/admin/import` page
2. `CsvImportController` receives file + metadata (season, matchday label)
3. `CsvImportService.parseAndPreview()` parses CSV, uses `DriverMatchingService` for fuzzy PSN ID matching
4. User reviews preview, confirms fuzzy matches and new driver creation
5. `CsvImportService.executeImport()` within `@Transactional`: creates Matchday (if needed) -> Match -> Race -> RaceResult + RaceLineup entries
6. `ScoringService.calculatePoints()` applies `RaceScoring` preset to each result
7. `ScoringService.aggregateMatchScores()` sums team totals onto Match.homeScore/awayScore

**Standings Calculation:**

1. `StandingsController` or `SiteGeneratorService` calls `StandingsService.calculateStandings(seasonId)`
2. Loads all Matches for the season, resolves team succession (replaced teams)
3. Processes each match: win/draw/loss based on Match.homeScore vs awayScore
4. Applies `MatchScoring` preset (points for win/draw/loss)
5. Returns sorted list of `TeamStanding` objects

**Graphic Generation:**

1. Controller prepares data as `MatchdayGraphicData` or similar DTO
2. Graphic service renders Thymeleaf HTML template with embedded Base64 images/fonts
3. Playwright takes a headless Chromium screenshot of the HTML
4. PNG saved to file storage (`data/dev/uploads/`)
5. URL returned to controller -> shown in admin UI

**Static Site Generation:**

1. User clicks "Generate Site" in admin sidebar
2. `SiteGeneratorController` calls `SiteGeneratorService.generate()`
3. Service iterates all seasons, renders Thymeleaf templates (`site/*.html`) with data
4. Writes static HTML files to output directory, copies CSS/JS/font/image assets
5. Result shown with page count and any errors

**State Management:**
- All state is persisted in the relational database (MariaDB in prod, H2 in dev)
- No application-level caching or session state beyond standard Spring MVC
- File uploads stored on local filesystem (`data/dev/uploads/` or configured path)

## Domain Model (Entity Graph)

**Central Hierarchy:**
```
Season
  |-- has RaceScoring (many-to-one)
  |-- has MatchScoring (many-to-one)
  |-- has many SeasonTeam (season-specific team config: rating, colors, logo override, succession)
  |-- has many SeasonDriver (driver-team assignment per season)
  |-- has many Matchday (ordered by sortIndex)
  |-- has many Car (many-to-many pool)
  |-- has many Track (many-to-many pool)

Matchday
  |-- has many Match
  |-- has many Race

Match
  |-- belongs to Matchday
  |-- has homeTeam, awayTeam (Team)
  |-- has homeScore, awayScore (aggregated from races)
  |-- has many Race (legs)

Race
  |-- belongs to Matchday
  |-- optionally belongs to Match
  |-- optionally belongs to PlayoffMatchup
  |-- has Track, Car (optional)
  |-- has homeTeamOverride, awayTeamOverride (race-level)
  |-- has one RaceSettings (optional)
  |-- has many RaceResult
  |-- has many RaceLineup
  |-- has many RaceAttachment

RaceResult
  |-- belongs to Race
  |-- belongs to Driver
  |-- position, qualiPosition, fastestLap, points breakdown

RaceLineup (Source of Truth for driver-team assignment per race)
  |-- belongs to Race
  |-- belongs to Driver
  |-- belongs to Team

Team
  |-- optional parentTeam (self-referential for sub-teams)
  |-- has many subTeams
  |-- has many SeasonDriver

Driver
  |-- has psnId (unique PSN identifier)
  |-- has many PsnAlias (for fuzzy matching)
  |-- has many SeasonDriver
  |-- has many RaceResult

Playoff
  |-- belongs to Season (one-to-one)
  |-- has many PlayoffRound (ordered by roundIndex)
  |-- has many PlayoffSeed

PlayoffRound
  |-- has many PlayoffMatchup

PlayoffMatchup
  |-- has team1, team2, winner
  |-- has nextMatchup (bracket progression)
  |-- has many Race
```

## Key Abstractions

**BaseEntity (`org.ctc.domain.model.BaseEntity`):**
- Purpose: `@MappedSuperclass` providing `createdAt`/`updatedAt` via JPA Auditing (`@EntityListeners(AuditingEntityListener.class)`)
- All entities extend this
- File: `src/main/java/org/ctc/domain/model/BaseEntity.java`

**AbstractGraphicService (`org.ctc.admin.service.AbstractGraphicService`):**
- Purpose: Base class for all Playwright-based graphic generators
- Provides: `renderScreenshot()`, `renderScreenshotTransparent()`, `encodeCardBase64()`, `encodeClasspathResource()`, `processStringTemplate()`
- File: `src/main/java/org/ctc/admin/service/AbstractGraphicService.java`
- Extended by: All graphic service classes in `admin.service`

**AbstractMatchdayGraphicService (`org.ctc.admin.service.AbstractMatchdayGraphicService`):**
- Purpose: Intermediate base class for matchday-specific graphics (schedule, results, overview)
- Adds matchday data preparation helpers
- File: `src/main/java/org/ctc/admin/service/AbstractMatchdayGraphicService.java`

**RaceScoring / MatchScoring (configurable scoring presets):**
- Purpose: Decouple scoring rules from code -- points arrays stored as comma-separated strings in DB
- `RaceScoring`: race position points, quali points, fastest lap points
- `MatchScoring`: win/draw/loss match points
- Each Season references one of each
- Files: `src/main/java/org/ctc/domain/model/RaceScoring.java`, `src/main/java/org/ctc/domain/model/MatchScoring.java`

**SeasonTeam (season-specific team configuration):**
- Purpose: Join table with extra attributes -- per-season team rating, color overrides, logo overrides, and team succession (replacement) tracking
- File: `src/main/java/org/ctc/domain/model/SeasonTeam.java`

## Entry Points

**Application Main:**
- Location: `src/main/java/org/ctc/CtcManagerApplication.java`
- Triggers: `SpringApplication.run()`, enables `@EnableJpaAuditing`
- Responsibilities: Bootstrap Spring Boot context

**Root URL (`/` and `/admin`):**
- Location: `src/main/java/org/ctc/admin/controller/AdminRedirectController.java`
- Triggers: Any request to `/` or `/admin`
- Responsibilities: Redirect to `/admin/seasons` (the default landing page)

**DevDataSeeder:**
- Location: `src/main/java/org/ctc/admin/DevDataSeeder.java`
- Triggers: Application startup with `dev` profile active
- Responsibilities: Calls `TestDataService.seed()` to create test teams, seasons, drivers, scoring presets

**DemoDataSeeder:**
- Location: `src/main/java/org/ctc/admin/DemoDataSeeder.java`
- Triggers: Application startup with `demo` profile active
- Responsibilities: Imports all GT7 cars and tracks with images for manual testing

## Error Handling

**Strategy:** Spring MVC default error handling with flash-attribute-based user feedback

**Patterns:**
- Controllers catch `IllegalStateException` from services, convert to flash error messages, redirect back
- Global error page: `src/main/resources/templates/error.html`
- Services throw `IllegalStateException` or `IllegalArgumentException` for business rule violations
- No custom exception hierarchy -- standard Java exceptions used throughout
- `@Transactional` ensures atomicity -- failures roll back the entire operation

## Cross-Cutting Concerns

**Logging:** SLF4J with Lombok `@Slf4j` on all services and controllers. DEBUG level for domain logic, INFO for significant operations (imports, sync, generation).

**Validation:** Jakarta Bean Validation (`@NotBlank`, `@NotNull`, `@Min`, `@Max`) on entities. No explicit `@Valid` controller-level validation observed -- validation happens at JPA persist time.

**Authentication:** None. This is a trusted admin-only application with no authentication or authorization layer.

**Auditing:** JPA Auditing via `BaseEntity` -- `createdAt` set on insert, `updatedAt` on every update, for all entities.

**File Storage:** `FileStorageService` manages file uploads to a configurable directory (`app.upload-dir`). `WebConfig` maps `/uploads/**` to the filesystem.

**Global Model Attributes:** `GlobalModelAdvice` (`@ControllerAdvice`) injects `appVersion` into every template model.

---

*Architecture analysis: 2026-04-03*
