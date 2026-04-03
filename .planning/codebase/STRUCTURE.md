# Codebase Structure

**Analysis Date:** 2026-04-03

## Directory Layout

```
ctc-manager/
├── src/
│   ├── main/
│   │   ├── java/org/ctc/
│   │   │   ├── CtcManagerApplication.java          # Entry point
│   │   │   ├── admin/
│   │   │   │   ├── DevDataSeeder.java              # Dev profile data seeder
│   │   │   │   ├── DemoDataSeeder.java             # Demo profile GT7 import
│   │   │   │   ├── TestDataService.java            # Shared test data creation
│   │   │   │   ├── WebConfig.java                  # Upload dir resource handler
│   │   │   │   ├── controller/                     # Admin UI controllers (17 files)
│   │   │   │   ├── dto/                            # Form + display DTOs (14 files)
│   │   │   │   └── service/                        # Graphic generation services (13 files)
│   │   │   ├── domain/
│   │   │   │   ├── model/                          # JPA entities (19 files)
│   │   │   │   ├── repository/                     # Spring Data JPA repos (17 files)
│   │   │   │   └── service/                        # Business logic services (14 files)
│   │   │   ├── dataimport/                         # CSV import + Google integration (6 files)
│   │   │   ├── gt7sync/                            # GT7 car/track scraping (4 files)
│   │   │   └── sitegen/                            # Static site generator (3 files)
│   │   └── resources/
│   │       ├── application.yml                     # Base config
│   │       ├── application-dev.yml                 # Dev profile (H2, port 9090)
│   │       ├── application-local.yml               # Local MariaDB profile
│   │       ├── application-docker.yml              # Docker profile
│   │       ├── application-prod.yml                # Production profile
│   │       ├── db/migration/
│   │       │   └── V1__initial_schema.sql          # Complete Flyway schema
│   │       ├── demo/
│   │       │   └── team-logos/                     # Demo team logo images
│   │       ├── templates/
│   │       │   ├── error.html                      # Global error page
│   │       │   ├── admin/                          # Admin UI templates (55+ files)
│   │       │   │   ├── layout.html                 # Main layout with sidebar
│   │       │   │   ├── *-form.html                 # CRUD form pages
│   │       │   │   ├── *-render.html               # Graphic render templates
│   │       │   │   └── *.html                      # List/detail pages
│   │       │   └── site/                           # Public site templates (8 files)
│   │       │       ├── layout.html                 # Site layout
│   │       │       ├── index.html                  # Homepage
│   │       │       ├── standings.html              # League table
│   │       │       └── ...                         # Other public pages
│   │       └── static/
│   │           ├── admin/
│   │           │   ├── css/admin.css               # Admin stylesheet
│   │           │   ├── js/                         # Admin JS modules
│   │           │   │   ├── color-sync.js           # Team color synchronization
│   │           │   │   ├── searchable-dropdown.js  # Searchable select widget
│   │           │   │   ├── sidebar-toggle.js       # Mobile sidebar
│   │           │   │   └── timezone.js             # Timezone handling
│   │           │   ├── fonts/                      # Custom fonts (Conthrax)
│   │           │   └── img/                        # Admin images, icons, logos
│   │           └── site/
│   │               ├── css/style.css               # Public site stylesheet
│   │               ├── fonts/                      # Site fonts
│   │               └── img/                        # Site images
│   └── test/
│       ├── java/org/ctc/
│       │   ├── CtcManagerApplicationTests.java     # Context load test
│       │   ├── TestHelper.java                     # Shared test utilities
│       │   ├── admin/
│       │   │   ├── controller/                     # Controller tests (17 files)
│       │   │   └── service/                        # Graphic service tests (10 files)
│       │   ├── domain/
│       │   │   ├── model/                          # Entity tests (5 files)
│       │   │   └── service/                        # Service tests (12 files)
│       │   ├── dataimport/                         # Import tests (5 files)
│       │   ├── e2e/                                # Playwright E2E tests (4 files)
│       │   │   ├── PlaywrightConfig.java           # E2E base config
│       │   │   ├── AdminWorkflowE2ETest.java       # Admin CRUD workflows
│       │   │   ├── ImportE2eTest.java              # Import workflow
│       │   │   └── ScoringE2ETest.java             # Scoring workflow
│       │   ├── gt7sync/                            # GT7 sync tests (3 files)
│       │   └── sitegen/                            # Site generator test (1 file)
│       └── resources/
│           └── gt7/                                # GT7 scraper test fixtures
├── data/
│   └── dev/
│       ├── uploads/                                # File storage (cars, tracks, team-cards, etc.)
│       └── logs/                                   # Application logs
├── scripts/
│   ├── app.sh                                      # Application management script
│   ├── deploy-site.sh                              # Static site deployment
│   └── serve-site.sh                               # Local site preview server
├── docs/                                           # Documentation and specs
├── .github/workflows/
│   ├── ci.yml                                      # CI pipeline (build, test, coverage)
│   └── deploy-site.yml                             # Static site deployment workflow
├── pom.xml                                         # Maven build config
├── Dockerfile                                      # Production Docker image
├── docker-compose.yml                              # Local Docker (App + MariaDB)
├── docker-compose.prod.yml                         # Production Docker (external DB)
├── CLAUDE.md                                       # AI assistant instructions
└── README.md                                       # Project readme
```

## Directory Purposes

**`src/main/java/org/ctc/admin/controller/`:**
- Purpose: Spring MVC controllers for the admin UI
- Contains: One controller per domain entity/feature, plus `AdminRedirectController` (root redirect) and `GlobalModelAdvice` (shared model attributes)
- Key files: `SeasonController.java`, `MatchdayController.java`, `RaceController.java`, `MatchController.java`, `PlayoffController.java`
- Naming: `{Entity}Controller.java`

**`src/main/java/org/ctc/admin/dto/`:**
- Purpose: Data transfer objects for form binding and display
- Contains: Form DTOs (`*Form.java`), display/graphic DTOs (`*Dto.java`, `*Data.java`)
- Key files: `TeamForm.java`, `RaceForm.java`, `MatchdayGraphicData.java`
- Naming: `{Entity}Form.java` for forms, descriptive names for display DTOs

**`src/main/java/org/ctc/admin/service/`:**
- Purpose: Playwright-based graphic/image generation services
- Contains: Abstract base classes and concrete graphic services
- Key files: `AbstractGraphicService.java` (base), `TeamCardService.java`, `ResultsGraphicService.java`, `LineupGraphicService.java`
- Naming: `{Feature}GraphicService.java` or `{Feature}Service.java`

**`src/main/java/org/ctc/domain/model/`:**
- Purpose: JPA entity classes representing the domain model
- Contains: All `@Entity` classes, `BaseEntity` superclass, enums (`AttachmentType`, `SeasonFormat`)
- Key files: `Season.java`, `Race.java`, `Match.java`, `Team.java`, `Driver.java`, `RaceResult.java`, `RaceLineup.java`
- Naming: `{DomainConcept}.java`

**`src/main/java/org/ctc/domain/repository/`:**
- Purpose: Spring Data JPA repository interfaces
- Contains: One repository per entity
- Key files: `RaceRepository.java`, `MatchRepository.java`, `RaceLineupRepository.java`
- Naming: `{Entity}Repository.java`

**`src/main/java/org/ctc/domain/service/`:**
- Purpose: Core business logic services
- Contains: Scoring, standings, rankings, CRUD orchestration, pairing algorithms
- Key files: `ScoringService.java`, `StandingsService.java`, `MatchService.java`, `PlayoffService.java`, `SwissPairingService.java`
- Naming: `{Feature}Service.java`

**`src/main/java/org/ctc/dataimport/`:**
- Purpose: External data import (CSV files, Google Sheets, Google Calendar)
- Contains: Controller, service, parser, driver matching logic
- Key files: `CsvImportService.java`, `CsvImportController.java`, `DriverMatchingService.java`, `GoogleSheetsService.java`

**`src/main/java/org/ctc/gt7sync/`:**
- Purpose: Web scraping and sync of Gran Turismo 7 car/track data
- Contains: Scraper (Jsoup), sync service, controller, preview DTO
- Key files: `Gt7ScraperService.java`, `Gt7SyncService.java`

**`src/main/java/org/ctc/sitegen/`:**
- Purpose: Static HTML site generation from Thymeleaf templates
- Contains: Service, controller, view model
- Key files: `SiteGeneratorService.java`, `SiteGeneratorController.java`, `model/RaceView.java`

**`src/main/resources/templates/admin/`:**
- Purpose: Thymeleaf templates for admin UI pages
- Contains: Layout fragment, CRUD forms, list/detail views, graphic render templates
- Key files: `layout.html` (sidebar layout fragment used by all pages), `*-form.html` (entity forms), `*-render.html` (graphic templates for Playwright screenshots)
- Pattern: All pages use `th:replace="~{admin/layout :: layout(title, content)}"`

**`src/main/resources/templates/site/`:**
- Purpose: Thymeleaf templates for the public-facing static site
- Contains: Layout, index, standings, matchday, team/driver profiles, playoff bracket, archive
- Key files: `layout.html`, `index.html`, `standings.html`

**`src/main/resources/db/migration/`:**
- Purpose: Flyway database migrations
- Contains: Single consolidated migration (schema not yet released publicly)
- Key files: `V1__initial_schema.sql` (322 lines, full schema with 19 tables)

**`src/test/java/org/ctc/e2e/`:**
- Purpose: Playwright end-to-end tests running against the full Spring Boot application
- Contains: E2E test classes + configuration
- Key files: `PlaywrightConfig.java` (base setup), `AdminWorkflowE2ETest.java`, `ScoringE2ETest.java`, `ImportE2eTest.java`
- Activated by: `-Pe2e` Maven profile

## Key File Locations

**Entry Points:**
- `src/main/java/org/ctc/CtcManagerApplication.java`: Spring Boot main class
- `src/main/java/org/ctc/admin/controller/AdminRedirectController.java`: Root URL redirect to `/admin/seasons`
- `src/main/java/org/ctc/admin/DevDataSeeder.java`: Dev profile startup seeder
- `src/main/java/org/ctc/admin/DemoDataSeeder.java`: Demo profile startup seeder

**Configuration:**
- `pom.xml`: Maven dependencies, plugins, profiles (default + e2e)
- `src/main/resources/application.yml`: Base configuration (upload dir, OSIV, Flyway, Google integration, actuator)
- `src/main/resources/application-dev.yml`: H2 in-memory, port 9090, debug logging
- `src/main/resources/application-local.yml`: Local MariaDB, port 9091
- `src/main/resources/application-docker.yml`: Docker MariaDB (host `db`)
- `src/main/resources/application-prod.yml`: Cloud DB via environment variables
- `Dockerfile`: Multi-stage build (Maven build + JRE runtime)
- `docker-compose.yml`: Local dev with MariaDB
- `docker-compose.prod.yml`: Production with external DB

**Core Business Logic:**
- `src/main/java/org/ctc/domain/service/ScoringService.java`: Points calculation + match score aggregation
- `src/main/java/org/ctc/domain/service/StandingsService.java`: League table calculation
- `src/main/java/org/ctc/domain/service/DriverRankingService.java`: Individual driver rankings
- `src/main/java/org/ctc/domain/service/PlayoffService.java`: Playoff bracket management
- `src/main/java/org/ctc/domain/service/SwissPairingService.java`: Swiss-system pairing algorithm
- `src/main/java/org/ctc/domain/service/MatchdayGeneratorService.java`: Round-robin matchday generation
- `src/main/java/org/ctc/dataimport/CsvImportService.java`: CSV import with two-phase preview/execute

**Testing:**
- `src/test/java/org/ctc/TestHelper.java`: Shared test utility methods
- `src/test/java/org/ctc/e2e/PlaywrightConfig.java`: E2E test base configuration
- `src/test/java/org/ctc/admin/TestDataService.java`: Creates isolated test data (prefixed entities)

## Naming Conventions

**Files:**
- Entities: PascalCase singular noun -- `Team.java`, `RaceResult.java`
- Repositories: `{Entity}Repository.java` -- `TeamRepository.java`
- Services: `{Feature}Service.java` -- `ScoringService.java`, `SeasonManagementService.java`
- Controllers: `{Entity}Controller.java` -- `TeamController.java`
- DTOs: `{Entity}Form.java` (forms) or descriptive `{Feature}Dto.java` / `{Feature}Data.java`
- Tests: `{ClassName}Test.java` -- `ScoringServiceTest.java`, `TeamControllerTest.java`
- E2E tests: `{Feature}E2ETest.java` -- `AdminWorkflowE2ETest.java`

**Directories:**
- Packages follow module boundaries: `admin.controller`, `admin.dto`, `admin.service`, `domain.model`, `domain.repository`, `domain.service`
- Templates mirror the admin/site split: `templates/admin/`, `templates/site/`
- Static assets mirror the admin/site split: `static/admin/`, `static/site/`

**Templates:**
- List pages: `{entities}.html` (plural) -- `teams.html`, `cars.html`
- Form pages: `{entity}-form.html` -- `team-form.html`, `car-form.html`
- Detail pages: `{entity}-detail.html` -- `team-detail.html`, `season-detail.html`
- Graphic render templates: `{feature}-render.html` -- `team-card-render.html`, `results-render.html`

## Where to Add New Code

**New Entity:**
- Entity class: `src/main/java/org/ctc/domain/model/{Entity}.java` (extend `BaseEntity`, use Lombok `@Getter @Setter @NoArgsConstructor`)
- Repository: `src/main/java/org/ctc/domain/repository/{Entity}Repository.java` (extend `JpaRepository<Entity, UUID>`)
- Migration: New `V{N}__description.sql` in `src/main/resources/db/migration/`
- Unit test: `src/test/java/org/ctc/domain/model/{Entity}Test.java`

**New Service:**
- Domain service: `src/main/java/org/ctc/domain/service/{Feature}Service.java` (use `@Service`, `@RequiredArgsConstructor`, `@Slf4j`)
- Unit test: `src/test/java/org/ctc/domain/service/{Feature}ServiceTest.java`

**New Admin CRUD Feature:**
- Controller: `src/main/java/org/ctc/admin/controller/{Entity}Controller.java` (use `@Controller`, `@RequestMapping("/admin/{entities}")`)
- Form DTO: `src/main/java/org/ctc/admin/dto/{Entity}Form.java`
- Templates: `src/main/resources/templates/admin/{entities}.html` (list), `{entity}-form.html` (create/edit), `{entity}-detail.html` (detail)
- Controller test: `src/test/java/org/ctc/admin/controller/{Entity}ControllerTest.java`

**New Graphic Service:**
- Service: `src/main/java/org/ctc/admin/service/{Feature}GraphicService.java` (extend `AbstractGraphicService`)
- Render template: `src/main/resources/templates/admin/{feature}-render.html`
- Test: `src/test/java/org/ctc/admin/service/{Feature}GraphicServiceTest.java`

**New Import Feature:**
- Add to `src/main/java/org/ctc/dataimport/`
- Follow two-phase pattern: parse+preview then confirm+execute

**Utilities / Shared Helpers:**
- Domain-level: Add to appropriate service in `domain.service`
- Test utilities: `src/test/java/org/ctc/TestHelper.java`

## Special Directories

**`data/dev/uploads/`:**
- Purpose: File storage for uploaded images (car photos, track images, team logos, team cards, graphic outputs)
- Generated: Yes (at runtime via `FileStorageService`)
- Committed: No (gitignored)

**`target/`:**
- Purpose: Maven build output
- Generated: Yes
- Committed: No

**`.planning/`:**
- Purpose: GSD planning and codebase analysis documents
- Generated: Yes (by AI tooling)
- Committed: Yes

**`.worktrees/`:**
- Purpose: Git worktrees for parallel feature development
- Generated: Yes (manually)
- Committed: No (gitignored)

**`docs/`:**
- Purpose: Documentation, design specs, generated static site output
- Committed: Yes

**`scripts/`:**
- Purpose: Operational scripts for app management and site deployment
- Key files: `app.sh` (start/stop/status), `deploy-site.sh`, `serve-site.sh`
- Committed: Yes

---

*Structure analysis: 2026-04-03*
