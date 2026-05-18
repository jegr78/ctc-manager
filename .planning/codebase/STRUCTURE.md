# Codebase Structure

**Analysis Date:** 2026-05-18

## Directory Layout

```
ctc-manager/
├── .github/
│   └── workflows/              # CI/CD: codeql.yml, build/test/e2e
├── .planning/
│   ├── codebase/               # Codebase documentation (ARCHITECTURE.md, etc.)
│   ├── milestones/             # Phase archives by version (v1.0-phases, v1.11-phases, etc.)
│   ├── phases/                 # Current active phase docs
│   ├── MILESTONES.md           # Release tracking
│   ├── PROJECT.md              # Project status and roadmap
│   └── ROADMAP.md              # Future feature pipeline
├── .screenshots/               # Playwright screenshot baseline + comparisons
├── .superpowers/               # Brainstorm sessions archive
├── config/
│   └── spotbugs-exclude.xml    # SpotBugs false-positive suppressions
├── data/
│   ├── dev/
│   │   ├── uploads/            # User-uploaded race attachments, team cards
│   │   ├── backup-staging/     # Backup preparation temp files
│   │   ├── import-backups/     # Previous import snapshots
│   │   └── logs/               # Application logs
│   ├── local/
│   │   └── uploads/            # Local MariaDB profile uploads
│   └── .import-backups/        # Timestamped import history (restore snapshots)
├── docs/
│   ├── operations/             # Deployment, ops guides
│   ├── security/               # SAST acceptance registry, security patterns
│   │   └── sast-acceptance.md  # CodeQL alert triage decisions + suppressions
│   ├── superpowers/            # Skill specs and execution plans
│   │   ├── specs/              # Design specs (scoring, release mgmt, etc.)
│   │   └── plans/              # Execution plans (archived)
│   ├── uat/                    # User acceptance test scenarios
│   ├── test-performance.md     # Performance profiling results
│   └── site/                   # Generated static site (output: `ctc.site.output-dir`)
├── scripts/                    # Utility scripts (backup, migrate, etc.)
├── src/
│   ├── main/
│   │   ├── java/org/ctc/
│   │   │   ├── CtcManagerApplication.java       # Spring Boot entry point
│   │   │   ├── admin/
│   │   │   │   ├── controller/                  # HTTP handlers
│   │   │   │   ├── dto/                         # Form DTOs, display models, graphic data
│   │   │   │   ├── service/                     # Graphics (Playwright), team cards, templates
│   │   │   │   ├── DemoDataSeeder.java          # Demo profile seed data (GT7 cars/tracks)
│   │   │   │   ├── DevDataSeeder.java           # Dev profile seed data
│   │   │   │   ├── TestDataService.java         # E2E test fixture data
│   │   │   │   ├── SecurityConfig.java          # Spring Security (prod/docker auth)
│   │   │   │   ├── OpenSecurityConfig.java      # No-auth config (dev/local)
│   │   │   │   ├── WebConfig.java               # Thymeleaf, Spring MVC config
│   │   │   │   └── GlobalModelAdvice.java       # Thymeleaf global attributes
│   │   │   ├── domain/
│   │   │   │   ├── model/                       # JPA entities (Season, Race, Match, etc.)
│   │   │   │   ├── repository/                  # Spring Data JPA repositories
│   │   │   │   ├── service/                     # Core domain logic (scoring, standings)
│   │   │   │   └── exception/                   # BusinessRuleException, EntityNotFoundException
│   │   │   ├── dataimport/
│   │   │   │   ├── CsvImportService.java        # Race result CSV parsing + import
│   │   │   │   ├── DriverSheetImportService.java # Google Sheets driver import
│   │   │   │   ├── DriverMatchingService.java   # Fuzzy driver name matching
│   │   │   │   ├── GoogleSheetsService.java     # Google Sheets API client
│   │   │   │   ├── GoogleCalendarService.java   # Google Calendar API client
│   │   │   │   ├── CsvImportController.java     # CSV import endpoints
│   │   │   │   ├── DriverSheetImportController.java
│   │   │   │   └── ScorecardParser.java         # HTML scorecard parsing (legacy)
│   │   │   ├── gt7sync/
│   │   │   │   ├── Gt7SyncService.java          # Orchestrate GT7 sync workflow
│   │   │   │   ├── Gt7ScraperService.java       # Web scraper for GT7 car/track data
│   │   │   │   ├── Gt7SyncController.java       # GT7 sync endpoints
│   │   │   │   └── Gt7SyncPreview.java          # Sync diff data transfer object
│   │   │   ├── sitegen/
│   │   │   │   ├── SiteGeneratorService.java    # Orchestrate static site generation
│   │   │   │   ├── MatchdaysPageGenerator.java  # Generate matchdays.html
│   │   │   │   ├── StandingsPageGenerator.java  # Generate standings.html
│   │   │   │   ├── DriverProfilePageGenerator.java
│   │   │   │   ├── TeamProfilePageGenerator.java
│   │   │   │   ├── DriverRankingPageGenerator.java
│   │   │   │   ├── YouTubeScraperService.java   # Fetch latest video metadata
│   │   │   │   ├── TemplateWriter.java          # Write HTML to docs/site
│   │   │   │   └── model/                       # Page DTOs (MatchdayPage, etc.)
│   │   │   └── backup/
│   │   │       ├── BackupController.java        # Backup/restore endpoints
│   │   │       ├── service/                     # Backup execution
│   │   │       ├── io/                          # ZIP file I/O
│   │   │       ├── restore/                     # Restore logic
│   │   │       ├── schema/                      # Schema validation
│   │   │       ├── serialization/               # Custom JSON serialization
│   │   │       ├── audit/                       # Backup history tracking
│   │   │       ├── lock/                        # Backup concurrency control
│   │   │       └── exception/                   # Backup-specific exceptions
│   │   └── resources/
│   │       ├── application.yml                  # Base Spring config (OSIV, Flyway, etc.)
│   │       ├── application-dev.yml              # H2 in-memory, port 9090, no auth
│   │       ├── application-local.yml            # Local MariaDB, port 9091
│   │       ├── application-docker.yml           # Docker MariaDB, port 8080, auth
│   │       ├── application-prod.yml             # Cloud MariaDB, env var config, auth
│   │       ├── db/
│   │       │   └── migration/
│   │       │       ├── V1__Initial_Schema.sql   # Immutable; contains base schema
│   │       │       ├── V2__*.sql                # New migrations only (never modify V1)
│   │       │       └── ... (V3+)
│   │       ├── templates/
│   │       │   ├── admin/                       # Admin UI templates
│   │       │   │   ├── layout.html              # Master layout
│   │       │   │   ├── season-*.html            # Season CRUD + detail tabs
│   │       │   │   ├── race-*.html              # Race form, results, lineup
│   │       │   │   ├── match-*.html             # Match/matchday views
│   │       │   │   ├── import*.html             # CSV/Sheets import flow
│   │       │   │   ├── standings.html           # Bracket, power rankings
│   │       │   │   ├── error.html               # Error display (GlobalExceptionHandler)
│   │       │   │   └── fragments/               # Reusable Thymeleaf fragments
│   │       │   ├── site/                        # Static site templates (output to docs/site/)
│   │       │   │   ├── matchdays.html
│   │       │   │   ├── standings.html
│   │       │   │   └── ...
│   │       │   └── fragments/                   # Shared HTML partials
│   │       ├── static/
│   │       │   ├── admin/
│   │       │   │   ├── css/
│   │       │   │   │   └── admin.css            # Button classes (btn-xs, btn-sm, etc.)
│   │       │   │   ├── js/                      # JavaScript for admin UI
│   │       │   │   ├── img/
│   │       │   │   │   └── ctc-logo-white.png  # Logo (used in graphics)
│   │       │   │   └── fonts/
│   │       │   │       └── ConthraxSb.woff2    # Custom font (used in graphics)
│   │       │   └── site/
│   │       │       ├── css/
│   │       │       ├── img/
│   │       │       └── fonts/
│   │       ├── demo/
│   │       │   ├── cars.csv                     # Demo GT7 cars
│   │       │   ├── tracks.csv                   # Demo GT7 tracks
│   │       │   └── team-logos/                  # Team logo images
│   │       └── META-INF/
│   │           └── spring.properties            # Spring config (metadata)
│   └── test/
│       ├── java/org/ctc/
│       │   ├── CtcManagerApplicationTests.java  # Context load smoke test
│       │   ├── DockerfilePinGuardTest.java      # Pinned version validation
│       │   ├── TestHelper.java                  # Shared test utilities
│       │   ├── testsupport/
│       │   │   ├── ContextLoadCountListener.java
│       │   │   └── SitegenTestDir.java          # Temp output dir for site gen tests
│       │   ├── domain/                          # Unit + integration tests for services
│       │   │   └── service/
│       │   │       ├── ScoringServiceTest.java
│       │   │       ├── MatchdayGeneratorServiceIT.java
│       │   │       └── ... (*IT.java = integration tests, @Tag("integration"))
│       │   ├── admin/
│       │   │   ├── SecurityIntegrationTest.java
│       │   │   ├── TestDataServiceIntegrationTest.java
│       │   │   └── controller/
│       │   │       └── *ControllerTest.java
│       │   ├── dataimport/
│       │   │   ├── CsvImportServiceTest.java
│       │   │   ├── CsvImportControllerTest.java
│       │   │   ├── DriverSheetImportServiceIT.java
│       │   │   └── ... (*IT.java = integration tests)
│       │   ├── gt7sync/
│       │   │   ├── Gt7ScraperServiceTest.java
│       │   │   ├── Gt7SyncServiceTest.java
│       │   │   └── Gt7SyncControllerTest.java
│       │   ├── sitegen/
│       │   │   └── *Generator*IT.java           # Integration tests
│       │   ├── backup/
│       │   │   └── *IT.java                     # Backup/restore integration tests
│       │   └── e2e/                             # Playwright E2E tests
│       │       ├── admin/
│       │       │   ├── AdminSeasonTest.java     # @Tag("e2e") E2E test
│       │       │   ├── AdminRaceTest.java
│       │       │   ├── AdminTeamCardTest.java
│       │       │   └── ... (all @Tag("e2e"))
│       │       ├── dataimport/
│       │       │   ├── CsvImportE2ETest.java
│       │       │   └── DriverSheetImportE2ETest.java
│       │       └── site/
│       │           └── SiteGenerationE2ETest.java
│       └── resources/
│           ├── application-test.yml             # Test profile (H2, different seed)
│           ├── db/
│           │   ├── migration/                   # Same V1 + V2+ migrations
│           │   └── test_data.sql                # Test fixture initialization
│           ├── sql/                             # SQL test helper files
│           ├── sitegen/
│           │   └── baseline/                    # Baseline expected output for site gen
│           ├── gt7/                             # Sample GT7 scraper response data
│           └── META-INF/
├── .env                        # Runtime env vars (ignored in git, local-only)
├── .env.dev                    # Dev profile hints
├── .env.local                  # Local profile hints
├── .env.example                # Template for .env
├── .gitignore                  # Git exclusions
├── .mvn/wrapper/               # Maven Wrapper
├── mvnw                        # Maven Wrapper script
├── pom.xml                     # Maven configuration (dependencies, profiles, JaCoCo)
├── lombok.config               # Lombok settings (FindBugs suppression)
├── rewrite.yml                 # OpenRewrite recipes (preview/apply)
├── renovate.json               # Renovate dependency automation
├── docker-compose.yml          # Local Docker: app + MariaDB
├── docker-compose.prod.yml     # Production Docker config
├── Dockerfile                  # Multi-stage build (JDK build, JRE runtime)
├── README.md                   # Project overview
├── CLAUDE.md                   # Project instructions (this reference)
├── google-credentials.json     # Google API service account (git-ignored)
└── skills-lock.json            # Agent skills lock manifest
```

## Directory Purposes

**`.planning/codebase/`:**
- Purpose: Codebase documentation (this file, ARCHITECTURE.md, etc.)
- Contains: Markdown docs generated by `/gsd-map-codebase`
- Key files: `ARCHITECTURE.md` (layer structure, data flow), `STRUCTURE.md` (file locations, where to add code)

**`src/main/java/org/ctc/admin/controller/`:**
- Purpose: HTTP request handlers; thin delegation to services
- Contains: `@Controller` classes with `@GetMapping`, `@PostMapping`, form binding via `@ModelAttribute`
- Key patterns: Flash attributes for success/error messages; redirect-after-POST; no business logic
- Examples: `SeasonController`, `RaceController`, `CsvImportController`

**`src/main/java/org/ctc/admin/dto/`:**
- Purpose: Data transfer objects for form binding and display
- Contains: `*Form` (POST input binding with `@Valid`), `*Dto` (display/query results), `*GraphicData` (prep for PNG generation)
- Key pattern: Never bind entities directly; Form DTOs protect against mass assignment
- Examples: `SeasonForm`, `RaceResultForm`, `MatchdayGraphicData`

**`src/main/java/org/ctc/admin/service/`:**
- Purpose: UI layer services — graphic generation, template management
- Contains: `AbstractGraphicService` and subclasses (Playwright HTML→PNG), `TeamCardService` (team card persistence)
- Key dependency: `TemplateEngine` (Thymeleaf for rendering), Playwright for screenshots
- Examples: `MatchdayOverviewGraphicService`, `PowerRankingsGraphicService`

**`src/main/java/org/ctc/domain/model/`:**
- Purpose: JPA entity definitions; domain objects
- Contains: `Season`, `Matchday`, `Race`, `Match`, `RaceResult`, `RaceLineup`, `Team`, `Driver`, `Car`, `Track`, `RaceScoring`, `MatchScoring`
- Key pattern: All extend `BaseEntity` (audit fields); use Lombok `@Getter @Setter @NoArgsConstructor`
- Critical entity: `RaceLineup` (driver-team binding per race, source of truth for scoring)

**`src/main/java/org/ctc/domain/repository/`:**
- Purpose: Spring Data JPA repository interfaces; no custom logic
- Contains: One repository per entity type (`CarRepository`, `DriverRepository`, `RaceRepository`, etc.)
- Key methods: `findById()`, `findAll()`, custom queries via `@Query` when needed
- Used by: Services only (controllers never access repositories directly)

**`src/main/java/org/ctc/domain/service/`:**
- Purpose: Core domain/business logic; orchestrates repositories and entities
- Contains: `SeasonManagementService`, `RaceService`, `ScoringService`, `MatchdayGeneratorService`, `MatchService`, `RaceLineupService`, etc.
- Key methods: All `@Transactional`; write operations flush and log
- Critical: `ScoringService.aggregateMatchScores()` must be called after race results saved
- Examples: `calculatePoints()` (individual result scoring), `aggregateMatchScores()` (match-level aggregation from legs)

**`src/main/java/org/ctc/dataimport/`:**
- Purpose: External data imports (CSV, Google Sheets, GT7)
- Contains: `CsvImportService`, `DriverSheetImportService`, `DriverMatchingService`, `GoogleSheetsService`, `GoogleCalendarService`
- Key responsibility: Multi-race import with RaceLineup creation, fuzzy driver matching, transaction safety
- Triggers: Controllers (`CsvImportController`, `DriverSheetImportController`)

**`src/main/java/org/ctc/gt7sync/`:**
- Purpose: GT7 car/track data synchronization
- Contains: `Gt7SyncService`, `Gt7ScraperService`, `Gt7SyncController`
- Key responsibility: Web scraping GT7 official data, diff with DB, bulk sync
- Output: Persisted Car and Track entities

**`src/main/java/org/ctc/sitegen/`:**
- Purpose: Static site generation (HTML export for public standings, driver profiles)
- Contains: `SiteGeneratorService`, `*PageGenerator` classes, `YouTubeScraperService`, `TemplateWriter`
- Output: `docs/site/` (committed to GitHub Pages)
- Key: Each page is generated from current DB state; no live queries on public site

**`src/main/java/org/ctc/backup/`:**
- Purpose: Backup/restore functionality (entire DB as JSON+metadata)
- Contains: `BackupController`, `BackupService`, serialization/restore logic, audit trail
- Output: Timestamped ZIP files in `data/{profile}/import-backups/`
- Key: Separate from git; allows quick rollback of data state

**`src/main/resources/db/migration/`:**
- Purpose: Flyway database schema versioning
- Contains: `V1__*.sql` (immutable), `V2__*.sql` and later (new migrations only)
- Key rule: **Never modify V1 migrations after release**; incompatibility between H2 (test) and MariaDB (prod) detected early
- Trigger: Automatic on startup via Flyway

**`src/main/resources/templates/admin/`:**
- Purpose: Thymeleaf HTML templates for admin UI
- Contains: Master layout (`layout.html`), CRUD forms (`*-form.html`), detail views (`*-detail.html`), import flows
- Key pattern: No complex SpEL expressions; data comes pre-prepared from service layer
- CSS: Use classes from `admin.css` (e.g., `btn-xs`, `btn-sm`, `btn-primary`)

**`src/main/resources/static/admin/css/`:**
- Purpose: Admin UI styling
- Key file: `admin.css` — contains `.btn-xs`, `.btn-sm`, `.btn-lg`, `.btn-tab` classes
- Pattern: No inline styles; always use CSS classes (refactor inline styles to classes when encountered)

**`src/main/resources/static/admin/fonts/`:**
- Purpose: Custom fonts for graphic generation
- Key file: `ConthraxSb.woff2` — used in Playwright-rendered graphics

**`src/test/java/org/ctc/`:**
- Purpose: Unit, integration, and E2E tests
- Tagging: `@Tag("integration")` for `*IT.java` integration tests (Spring context loaded), `@Tag("e2e")` for Playwright tests
- Key pattern: BDD method names: `givenContext_whenAction_thenExpected()`
- Surefire routing: Tags determine which fork to use (unit vs. integration); untagged `*IT.java` files run in wrong fork and may race

**`src/test/resources/`:**
- Purpose: Test data, fixtures, configuration
- Key files: `application-test.yml` (H2 config), `test_data.sql` (fixture initialization), `gt7/*.json` (sample scraper responses)

**`data/dev/uploads/`:**
- Purpose: User-uploaded files (race attachments, team card PNGs)
- Configured: `app.upload-dir` in `application.yml`
- Pattern: Relative path from working directory; normalized on access to prevent directory traversal

**`docs/site/`:**
- Purpose: Generated static site (output directory)
- Committed: Yes (GitHub Pages source)
- Trigger: `SiteGeneratorService.generateSite()` called from controller endpoint or during test

**`docs/security/`:**
- Purpose: SAST security decisions and suppressions
- Key file: `sast-acceptance.md` — registry of CodeQL alerts triaged (suppressed or accepted)
- Pattern: Every suppression requires source-code marker + row in this doc

**`.github/workflows/`:**
- Purpose: GitHub Actions CI/CD pipelines
- Key file: `codeql.yml` — runs CodeQL SAST scanning, gates on HIGH/CRITICAL alerts
- Others: Build, unit/integration tests, E2E tests, JaCoCo coverage reporting

**`.planning/milestones/`:**
- Purpose: Phase execution records (archived)
- Pattern: `v1.X-phases/` subdirectories contain phase documents after milestone closes
- Examples: `v1.11-phases/` (archived v1.11 phases)

**`.screenshots/`:**
- Purpose: Playwright screenshot baselines and diffs
- Pattern: `{phase-number}/before/` (baseline), `{phase-number}/after/` (new run), `auto-uat/` (auto-generated)

## Key File Locations

**Entry Points:**
- `src/main/java/org/ctc/CtcManagerApplication.java` — Spring Boot startup (@SpringBootApplication)
- `src/main/resources/application*.yml` — Profile-specific config (dev, local, docker, prod)
- `src/main/resources/templates/admin/layout.html` — Master Thymeleaf template

**Configuration:**
- `src/main/resources/application.yml` — Base config (OSIV=true, Flyway, upload dir, Google credentials path)
- `src/main/resources/application-dev.yml` — H2 in-memory, port 9090, no auth
- `src/main/resources/application-local.yml` — MariaDB localhost, port 9091
- `src/main/resources/application-docker.yml` — Docker MariaDB (db:3306), auth enabled
- `src/main/resources/application-prod.yml` — Cloud DB via env vars (DATABASE_URL, etc.)
- `pom.xml` — Maven config, dependencies, JaCoCo/SpotBugs gates, profiles

**Core Logic:**
- `src/main/java/org/ctc/domain/service/RaceService.java` — Race persistence and results entry
- `src/main/java/org/ctc/domain/service/ScoringService.java` — Point calculation and match score aggregation (critical!)
- `src/main/java/org/ctc/dataimport/CsvImportService.java` — Multi-race CSV import with RaceLineup creation
- `src/main/java/org/ctc/domain/service/SeasonManagementService.java` — Season CRUD and queries
- `src/main/java/org/ctc/domain/service/MatchdayGeneratorService.java` — Swiss pairing and matchday generation

**Testing:**
- `src/test/java/org/ctc/TestHelper.java` — Shared test utilities
- `src/test/java/org/ctc/domain/service/*Test.java` — Unit tests (no Spring context)
- `src/test/java/org/ctc/domain/service/*IT.java` — Integration tests (@Tag("integration"))
- `src/test/java/org/ctc/e2e/*Test.java` — Playwright E2E tests (@Tag("e2e"))

## Naming Conventions

**Files:**
- Controllers: `{Entity}Controller.java` (e.g., `SeasonController`, `RaceController`)
- Services: `{Domain}Service.java` (e.g., `RaceService`, `ScoringService`)
- Repositories: `{Entity}Repository.java` (e.g., `SeasonRepository`, `RaceRepository`)
- Form DTOs: `{Entity}Form.java` (e.g., `SeasonForm`, `RaceResultForm`)
- Display DTOs: `{Entity}Dto.java` or `{Entity}View.java` (e.g., `MatchdayDto`, `StandingsView`)
- Tests: `{Class}Test.java` (unit), `{Class}IT.java` (integration)
- Thymeleaf: `{feature}-{action}.html` (e.g., `season-detail.html`, `race-form.html`)
- Migrations: `V{number}__{description}.sql` (snake_case description, e.g., `V2__add_race_lineup.sql`)

**Directories:**
- Package hierarchy: `org.ctc.{layer}.{feature}` (e.g., `org.ctc.admin.controller`, `org.ctc.dataimport`)
- Resource dirs: `templates/{context}/`, `static/{context}/css/`, `db/migration/`

**Classes:**
- Entity classes: PascalCase singular (`Season`, `Race`, `Driver`)
- Service classes: `{Domain}Service` or `{Feature}Service`
- Controller classes: `{Entity}Controller`
- DTO classes: Suffixed with `Form`, `Dto`, `View`, or `Data`
- Methods: camelCase, verb-first (`calculatePoints()`, `aggregateMatchScores()`)
- Boolean methods: `is*()` or `has*()` (e.g., `isDriverInTeam()`, `hasResults()`)

## Where to Add New Code

**New Controller Endpoint:**
1. Create handler method in `src/main/java/org/ctc/admin/controller/{Entity}Controller.java`
2. Add route: `@GetMapping("...")` or `@PostMapping("...")`
3. Delegate to service (never access repository directly)
4. Bind form DTOs via `@ModelAttribute("formName") @Valid {Entity}Form form, BindingResult result`
5. Add model attributes for template display
6. Return view name or redirect (flash attributes for messages)

**New Service Method:**
1. Create or extend `src/main/java/org/ctc/domain/service/{Domain}Service.java`
2. Annotate with `@Transactional` if writing state
3. Use `@RequiredArgsConstructor` for dependency injection
4. Call repositories; apply business logic; throw `BusinessRuleException` on constraint violations
5. Log state changes with `@Slf4j`

**New Entity:**
1. Create `src/main/java/org/ctc/domain/model/{Entity}.java`
2. Extend `BaseEntity` (provides `createdAt`, `updatedAt`)
3. Use `@Getter @Setter @NoArgsConstructor` (Lombok)
4. Add `@Entity` and `@Table` annotations
5. Define relationships with `@OneToMany`, `@ManyToOne`, etc.

**New Repository:**
1. Create `src/main/java/org/ctc/domain/repository/{Entity}Repository.java`
2. Extend `JpaRepository<{Entity}, UUID>`
3. Add custom query methods (e.g., `findByXyz()`) — let Spring Data auto-generate JPQL
4. Use `@Query` for complex queries

**New Import Pipeline:**
1. Create service class in `src/main/java/org/ctc/dataimport/{Feature}Service.java`
2. Add controller in `src/main/java/org/ctc/admin/controller/{Feature}Controller.java`
3. Create DTOs in `src/main/java/org/ctc/admin/dto/` for preview and execution results
4. Add templates in `src/main/resources/templates/admin/{feature}*.html`
5. Use `@Transactional` for multi-step imports; call `ScoringService.aggregateMatchScores()` after race creation
6. Write integration tests with `@SpringBootTest`, `@Tag("integration")`

**New Template:**
1. Create `src/main/resources/templates/admin/{feature}-{action}.html`
2. Use master layout: `th:replace="admin/layout"`
3. Pass pre-formatted data from service (no complex logic in template)
4. Use CSS classes from `admin.css` (no inline styles on buttons)
5. Use Thymeleaf `th:*` attributes for conditionals, loops, variable substitution

**New Test:**
1. **Unit test** (no Spring context): `src/test/java/org/ctc/domain/service/{Class}Test.java`
   - Use `@ExtendWith(MockitoExtension.class)`, mock dependencies
   - No `@Tag` annotation

2. **Integration test** (Spring context): `src/test/java/org/ctc/{module}/{Class}IT.java`
   - Use `@SpringBootTest`, `@Tag("integration")`
   - Access real database (H2 in test profile)

3. **E2E test** (Playwright): `src/test/java/org/ctc/e2e/{Feature}Test.java`
   - Use Playwright `BrowserContext`, `Page`
   - Tag with `@Tag("e2e")`
   - Navigate real application, verify UI behavior

**New Database Migration:**
1. Create `src/main/resources/db/migration/V{next-number}__{description}.sql`
2. Use snake_case for description (e.g., `V3__add_race_lineup_table.sql`)
3. Write SQL compatible with both H2 (test) and MariaDB (prod)
4. Never modify existing V1 migrations
5. Test with: `./mvnw clean test` (runs H2 tests), then manual MariaDB validation in local profile

**New Graphic Service:**
1. Create class extending `AbstractGraphicService`
2. Implement abstract method `renderGraphic()` (e.g., `renderLineupGraphic()`)
3. Use `TemplateEngine.process()` to render Thymeleaf HTML
4. Call `renderScreenshot()` or `renderScreenshotTransparent()` for PNG output
5. Save to `uploadDir / {season-id} / {race-id} / {graphic-name}.png`
6. Log file path for user feedback

## Special Directories

**`src/main/resources/db/migration/`:**
- Purpose: Flyway database versioning
- Generated: No (manually written)
- Committed: Yes
- Key rule: V1 is immutable; V2+ only for new changes

**`data/dev/uploads/`:**
- Purpose: User-uploaded files (team cards, race attachments)
- Generated: Yes (runtime uploads)
- Committed: No (git-ignored)
- Configurable: `app.upload-dir` in `application.yml`

**`docs/site/`:**
- Purpose: Static site output
- Generated: Yes (by `SiteGeneratorService`)
- Committed: Yes (GitHub Pages source)
- Trigger: Manual endpoint or batch job

**`.screenshots/`:**
- Purpose: Playwright visual regression baseline + diffs
- Generated: Yes (by Playwright)
- Committed: Yes (version control baseline)
- Pattern: `{phase}/before/`, `{phase}/after/`, `auto-uat/`

**`.planning/codebase/`:**
- Purpose: Codebase documentation
- Generated: Yes (by `/gsd-map-codebase`)
- Committed: Yes (reference for future work)

---

*Structure analysis: 2026-05-18*
