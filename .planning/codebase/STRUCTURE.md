# Codebase Structure

**Analysis Date:** 2026-04-04

## Directory Layout

```text
ctc-manager/
├── src/
│   ├── main/
│   │   ├── java/org/ctc/
│   │   │   ├── CtcManagerApplication.java          # Entry point
│   │   │   ├── admin/
│   │   │   │   ├── controller/                     # Admin UI controllers (18 files)
│   │   │   │   ├── dto/                            # Form/display DTOs (16 files)
│   │   │   │   ├── service/                        # Graphic generation services (15 files)
│   │   │   │   ├── DevDataSeeder.java              # Dev profile data seeder
│   │   │   │   ├── DemoDataSeeder.java             # Demo profile GT7 data importer
│   │   │   │   ├── TestDataService.java            # Creates test entities for dev/e2e
│   │   │   │   ├── SecurityConfig.java             # Auth for prod/docker profiles
│   │   │   │   ├── OpenSecurityConfig.java         # Permit-all for dev/local profiles
│   │   │   │   └── WebConfig.java                  # Upload file serving config
│   │   │   ├── domain/
│   │   │   │   ├── model/                          # JPA entities (20 files + 2 enums)
│   │   │   │   ├── repository/                     # Spring Data repos (18 files)
│   │   │   │   ├── service/                        # Business logic services (17 files)
│   │   │   │   └── exception/                      # Typed domain exceptions (3 files)
│   │   │   ├── dataimport/                         # CSV/Sheets import (5 files)
│   │   │   ├── gt7sync/                            # GT7 scraping + sync (4 files)
│   │   │   └── sitegen/                            # Static site generation (3 files)
│   │   └── resources/
│   │       ├── application.yml                     # Base config
│   │       ├── application-{dev,local,docker,prod}.yml  # Profile configs
│   │       ├── logback-spring.xml                  # Logging config
│   │       ├── db/migration/                       # Flyway SQL migrations
│   │       ├── demo/                               # Demo data (team logos)
│   │       ├── static/
│   │       │   ├── admin/                          # Admin UI assets (css, js, fonts, img)
│   │       │   └── site/                           # Public site assets (css, js, fonts, img)
│   │       └── templates/
│   │           ├── admin/                          # Admin Thymeleaf templates (55 files)
│   │           ├── site/                           # Public site templates (8 files)
│   │           └── error.html                      # Global error fallback
│   └── test/                                       # Tests (mirrors main structure)
├── docs/site/                                      # Generated static site output
├── data/dev/uploads/                               # Dev upload directory
├── .github/workflows/                              # CI/CD pipelines
├── pom.xml                                         # Maven build config
├── Dockerfile                                      # Multi-stage Docker build
├── docker-compose.yml                              # Local dev (App + MariaDB)
├── docker-compose.prod.yml                         # Production compose
├── CLAUDE.md                                       # AI assistant instructions
└── .planning/                                      # GSD workflow artifacts
```

## Directory Purposes

**`src/main/java/org/ctc/admin/controller/`:**

- Purpose: HTTP request handling for the admin UI
- Contains: 18 `@Controller` classes + 2 `@ControllerAdvice` classes
- Key files:
  - `SeasonController.java`: Season CRUD + team/car/track pool management + matchday generation
  - `RaceController.java`: Race CRUD, results entry, attachment management
  - `MatchdayController.java`: Matchday listing, detail, reordering
  - `PlayoffController.java`: Playoff bracket, rounds, matchups, seeding
  - `StandingsController.java`: Season standings + driver rankings display
  - `TeamCardController.java`: Team card graphic generation
  - `TemplateEditorController.java`: Custom graphic template management
  - `GlobalExceptionHandler.java`: Centralized exception -> error page mapping
  - `GlobalModelAdvice.java`: Injects `appVersion` into all views
  - `AdminRedirectController.java`: Redirects `/` to `/admin/seasons`

**`src/main/java/org/ctc/admin/dto/`:**

- Purpose: Form binding DTOs (POST) and structured display data records
- Contains: Form classes (`*Form`), display DTOs (`*Dto`, `*Data`), request records
- Key files:
  - `SeasonForm.java`: Season create/edit form binding
  - `RaceForm.java`: Race create/edit form binding
  - `RaceResultForm.java`: Race result entry form
  - `MatchdayGraphicData.java`: Prepared data for matchday graphic rendering
  - `RankedTeamData.java`: Power rankings display data
  - `SeasonDriverGroupDto.java`: Grouped driver display for season detail

**`src/main/java/org/ctc/admin/service/`:**

- Purpose: Playwright-based graphic generation (HTML -> screenshot -> PNG)
- Contains: 3 abstract base classes + 12 concrete graphic services
- Key files:
  - `AbstractGraphicService.java`: Base: Playwright rendering, base64 encoding, file I/O
  - `AbstractMatchdayGraphicService.java`: Matchday context prep, custom template management
  - `AbstractPlayoffRoundGraphicService.java`: Playoff round context prep
  - `MatchdayScheduleGraphicService.java`: Schedule graphic (upcoming matches)
  - `MatchdayResultsGraphicService.java`: Results graphic (completed matches)
  - `MatchdayOverviewGraphicService.java`: Overview graphic (standings + results)
  - `LineupGraphicService.java`: Team lineup graphic
  - `ResultsGraphicService.java`: Single race results graphic
  - `TeamCardService.java`: Team card PNG generation
  - `PowerRankingsGraphicService.java`: Power rankings graphic
  - `OverlayGraphicService.java`: Stream overlay graphic
  - `TemplatePreviewService.java`: Live template preview rendering

**`src/main/java/org/ctc/domain/model/`:**

- Purpose: JPA entity classes representing the domain model
- Contains: 20 entities, 2 enums (`SeasonFormat`, `AttachmentType`), 1 `@MappedSuperclass`
- Key files:
  - `BaseEntity.java`: `@MappedSuperclass` with `createdAt`/`updatedAt` (JPA Auditing)
  - `Season.java`: Central entity -- links to teams, drivers, cars, tracks, scoring rules, matchdays
  - `Team.java`: Self-referencing parent/sub-team hierarchy
  - `SeasonTeam.java`: Join table with per-season overrides (rating, colors, logo) and succession tracking
  - `Match.java`: Home vs. away team pairing on a matchday, with aggregated scores
  - `Race.java`: Individual race event with team resolution delegation (override -> match -> playoff)
  - `RaceResult.java`: Per-driver race result with calculated points breakdown
  - `RaceLineup.java`: Source of truth for driver-to-team assignment per race
  - `RaceScoring.java`: Configurable points-per-position as comma-separated strings
  - `MatchScoring.java`: Win/draw/loss point values
  - `Matchday.java`: Groups matches within a season round
  - `Playoff.java`: Playoff bracket container with rounds and seeds
  - `PlayoffMatchup.java`: Single bracket matchup with team1/team2, scores, winner, next link
  - `RaceSettings.java`: Race configuration (laps, tyres, fuel, weather)
  - `Driver.java`: PSN ID + nickname, linked to seasons via SeasonDriver
  - `SeasonDriver.java`: Driver-to-team assignment per season (fallback for RaceLineup)

**`src/main/java/org/ctc/domain/repository/`:**

- Purpose: Spring Data JPA repository interfaces
- Contains: 18 repository interfaces
- Pattern: `{Entity}Repository extends JpaRepository<{Entity}, UUID>`
- Custom query methods use Spring Data naming conventions (e.g., `findBySeasonIdOrderBySortIndexAsc`)

**`src/main/java/org/ctc/domain/service/`:**

- Purpose: Business logic, calculations, data orchestration
- Contains: 17 service classes
- Key files:
  - `ScoringService.java`: Points calculation per result + match score aggregation + `isDriverInTeam()`
  - `StandingsService.java`: Team standings table (win/draw/loss, match points, point diff)
  - `SeasonManagementService.java`: Season CRUD + team/car/track pool + team replacement + structured data
  - `TeamManagementService.java`: Team detail data, color/logo propagation to sub-teams
  - `MatchdayGeneratorService.java`: Round-robin matchday generation
  - `SwissPairingService.java`: Swiss-system round-by-round pairing
  - `PlayoffService.java`: Bracket management, seeding, winner advancement
  - `MatchService.java`: Match CRUD operations
  - `MatchdayService.java`: Matchday CRUD and reordering
  - `RaceService.java`: Race CRUD, result saving, attachment management
  - `RaceLineupService.java`: Lineup CRUD per race
  - `DriverService.java`: Driver CRUD, PSN alias management
  - `DriverRankingService.java`: Per-driver ranking across a season
  - `MatchScoringService.java`: MatchScoring CRUD
  - `RaceScoringService.java`: RaceScoring CRUD
  - `FileStorageService.java`: File upload/delete for logos and images
  - `CarService.java`, `TrackService.java`: Simple CRUD

**`src/main/java/org/ctc/domain/exception/`:**

- Purpose: Typed runtime exceptions for centralized error handling
- Contains:
  - `EntityNotFoundException.java`: Entity lookup failures (-> 404)
  - `BusinessRuleException.java`: Business rule violations (-> 409)
  - `ValidationException.java`: Input validation failures (-> 400)

**`src/main/java/org/ctc/dataimport/`:**

- Purpose: Race result import from CSV files and Google Sheets
- Contains:
  - `CsvImportController.java`: Import UI (preview + execute endpoints)
  - `CsvImportService.java`: CSV parsing, preview generation, import execution
  - `ScorecardParser.java`: Google Sheets data -> ImportPreview conversion
  - `DriverMatchingService.java`: PSN ID matching (exact, fuzzy, alias-based)
  - `GoogleSheetsService.java`: Google Sheets API client
  - `GoogleCalendarService.java`: Google Calendar integration for race scheduling

**`src/main/java/org/ctc/gt7sync/`:**

- Purpose: Scraping GT7 car and track data from external websites
- Contains:
  - `Gt7SyncController.java`: Sync UI (preview + execute endpoints)
  - `Gt7SyncService.java`: Sync orchestration, selective import
  - `Gt7ScraperService.java`: Jsoup-based web scraping
  - `Gt7SyncPreview.java`: Preview data model

**`src/main/java/org/ctc/sitegen/`:**

- Purpose: Static HTML site generation for public consumption
- Contains:
  - `SiteGeneratorController.java`: Trigger generation from admin UI
  - `SiteGeneratorService.java`: Generates index, standings, matchdays, profiles, playoff brackets
  - `model/RaceView.java`: View model for race display on static site

## Key File Locations

**Entry Points:**

- `src/main/java/org/ctc/CtcManagerApplication.java`: Spring Boot main class
- `src/main/java/org/ctc/admin/controller/AdminRedirectController.java`: Root URL redirect

**Configuration:**

- `src/main/resources/application.yml`: Base config (OSIV, Flyway, upload dir, Google, actuator)
- `src/main/resources/application-dev.yml`: H2 in-memory, port 9090
- `src/main/resources/application-local.yml`: MariaDB local, port 9091
- `src/main/resources/application-docker.yml`: MariaDB in Docker network, port 8080
- `src/main/resources/application-prod.yml`: Cloud DB via env vars
- `src/main/resources/logback-spring.xml`: Profile-based logging config
- `pom.xml`: Maven build with Surefire, Failsafe, JaCoCo, Playwright

**Database:**

- `src/main/resources/db/migration/V1__initial_schema.sql`: Complete initial schema (frozen)
- `src/main/resources/db/migration/V2__add_fk_indexes.sql`: FK index additions

**Templates:**

- `src/main/resources/templates/admin/layout.html`: Admin layout with sidebar navigation
- `src/main/resources/templates/admin/*.html`: 55 admin templates (CRUD forms, detail pages, graphic renders)
- `src/main/resources/templates/site/layout.html`: Public site layout
- `src/main/resources/templates/site/*.html`: 8 public site templates

**Static Assets:**

- `src/main/resources/static/admin/css/admin.css`: Admin UI styles (button classes, layout)
- `src/main/resources/static/admin/js/`: Admin JavaScript
- `src/main/resources/static/admin/fonts/ConthraxSb.woff2`: Custom font for graphics
- `src/main/resources/static/admin/img/`: Admin images (CTC logo, icons)
- `src/main/resources/static/site/`: Public site assets

**Infrastructure:**

- `Dockerfile`: Multi-stage build (JDK build -> JRE runtime), non-root user `ctc`
- `docker-compose.yml`: Local dev environment (App + MariaDB)
- `docker-compose.prod.yml`: Production (external DB via env vars)
- `.github/workflows/`: CI/CD pipelines (build, test, coverage, deploy)

## Naming Conventions

**Files:**

- Entities: `{SingularNoun}.java` (e.g., `Season.java`, `RaceScoring.java`)
- Repositories: `{Entity}Repository.java` (e.g., `SeasonRepository.java`)
- Services: `{Domain}Service.java` or `{Domain}ManagementService.java` (e.g., `ScoringService.java`, `SeasonManagementService.java`)
- Controllers: `{Entity}Controller.java` (e.g., `SeasonController.java`)
- Form DTOs: `{Entity}Form.java` (e.g., `SeasonForm.java`)
- Display DTOs: `{Entity}Dto.java` or `{Entity}Data.java` (e.g., `MatchdayDto.java`, `RankedTeamData.java`)
- Templates: `kebab-case.html` (e.g., `season-detail.html`, `race-scoring-form.html`)
- Graphic render templates: `*-render.html` (e.g., `results-render.html`, `lineup-render.html`)
- Migrations: `V{N}__{snake_case_description}.sql`

**Directories:**

- Packages: lowercase, domain-driven (`admin.controller`, `domain.model`, `dataimport`)
- Template dirs: match module names (`admin/`, `site/`)
- Static asset dirs: by type (`css/`, `js/`, `fonts/`, `img/`)

## Where to Add New Code

**New Entity:**

- Entity class: `src/main/java/org/ctc/domain/model/{Entity}.java` -- extend `BaseEntity`, use Lombok annotations
- Repository: `src/main/java/org/ctc/domain/repository/{Entity}Repository.java` -- extend `JpaRepository<Entity, UUID>`
- Migration: `src/main/resources/db/migration/V{next}__add_{table_name}.sql` -- H2 + MariaDB compatible
- Test data: add to `src/main/java/org/ctc/admin/TestDataService.java` if needed for dev seeding

**New Service:**

- Domain service: `src/main/java/org/ctc/domain/service/{Name}Service.java`
- Use `@Service`, `@RequiredArgsConstructor`, `@Slf4j`, `@Transactional` as needed
- Return structured data via Java records when multiple values needed

**New Controller:**

- Controller: `src/main/java/org/ctc/admin/controller/{Entity}Controller.java`
- Map under `/admin/{entity-plural}`
- Use `@Controller`, `@RequestMapping`, `@RequiredArgsConstructor`, `@Slf4j`
- Form DTOs: `src/main/java/org/ctc/admin/dto/{Entity}Form.java`
- Templates: `src/main/resources/templates/admin/{entity-name}.html`, `{entity-name}-form.html`, `{entity-name}-detail.html`

**New Graphic Service:**

- For matchday-related: extend `AbstractMatchdayGraphicService`
- For playoff-related: extend `AbstractPlayoffRoundGraphicService`
- For standalone: extend `AbstractGraphicService`
- Place in: `src/main/java/org/ctc/admin/service/{Name}GraphicService.java`
- Render template: `src/main/resources/templates/admin/{name}-render.html`

**New Feature Module:**

- Create package: `src/main/java/org/ctc/{modulename}/`
- Include controller, service, and any module-specific models
- Follow two-phase pattern for import/sync features (preview + execute)

**New Tests:**

- Unit tests: `src/test/java/org/ctc/` mirroring main structure
- Integration tests: same location, use `@SpringBootTest` or `@DataJpaTest`
- E2E tests: `src/test/java/org/ctc/e2e/` with Playwright

## Special Directories

**`data/dev/uploads/`:**

- Purpose: File upload storage for dev profile
- Generated: Yes (at runtime by `FileStorageService`)
- Committed: No (in `.gitignore`)

**`docs/site/`:**

- Purpose: Generated static site HTML output
- Generated: Yes (by `SiteGeneratorService`)
- Committed: Yes (deployed to GitHub Pages on push)

**`target/`:**

- Purpose: Maven build output
- Generated: Yes
- Committed: No

**`.planning/`:**

- Purpose: GSD workflow planning artifacts and codebase analysis
- Generated: Yes (by GSD commands)
- Committed: Yes

**`.github/workflows/`:**

- Purpose: CI/CD pipeline definitions
- Contains: Build/test workflow, GitHub Pages deploy
- Committed: Yes

**`src/main/resources/demo/`:**

- Purpose: Demo data for `dev,demo` profile (team logos)
- Generated: No
- Committed: Yes

---

*Structure analysis: 2026-04-04*
