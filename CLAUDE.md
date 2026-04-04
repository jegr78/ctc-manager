# CTC Manager

Community Team Cup — Gran Turismo Racing League Manager

## Tech Stack

- Java 25, Spring Boot 4.x, Maven
- Thymeleaf (Admin-UI), MariaDB/H2, Flyway
- JUnit 5, Mockito, Playwright, Jsoup

## Sprache

Deutsch für Kommunikation und Dokumentation. Code, Kommentare und UI-Texte auf Englisch.

## Befehle

```bash
# Dev-Modus starten
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Dev-Modus mit GT7-Demodaten (Autos, Strecken, Bilder)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo

# Tests ausfuehren (Unit + Integration + JaCoCo Coverage)
./mvnw verify

# Tests ausfuehren inkl. Playwright E2E
./mvnw verify -Pe2e

# Coverage-Report oeffnen
open target/site/jacoco/index.html

# Local mit MariaDB
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Playwright Chromium installieren (fuer Team Card Generierung + E2E Tests)
./mvnw exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"

# Docker: Lokale Umgebung (App + MariaDB)
docker compose up --build -d
docker compose down

# Docker: Nur Image bauen
docker compose build

# Docker: Produktion (externe DB, .env konfigurieren)
docker compose -f docker-compose.prod.yml up -d
```

## Spring Profiles

- `dev` — H2 In-Memory, Port 9090 (Entwicklung, Tests)
- `dev,demo` — Wie `dev`, importiert beim Start alle GT7-Autos und -Strecken (mit Bildern) fuer manuelles Testen
- `local` — MariaDB lokal, Port 9091
- `docker` — MariaDB im Docker-Netzwerk (Host `db`), Port 8080
- `prod` — Cloud DB (Environment Variables)

## Package-Struktur

- `org.ctc.domain.model` — JPA Entities
- `org.ctc.domain.repository` — Spring Data Repositories
- `org.ctc.domain.service` — Geschaeftslogik (Scoring, Standings, Rankings)
- `org.ctc.admin.controller` — Admin CRUD Controller
- `org.ctc.admin.dto` — Form/Display DTOs
- `org.ctc.sitegen` — Statische Seitengenerierung
- `org.ctc.dataimport` — CSV/Bild-Import
- `org.ctc.gt7sync` — GT7 Auto/Strecken-Scraping und Sync

## Key Files

- `CtcManagerApplication.java` — Entry Point
- `TestDataService.java` — DevDataSeeder: Erstellt Teams, Seasons, Drivers, Scoring-Presets beim Start (dev-Profil)
- `ScoringService.java` — Punkteberechnung (konfigurierbar via RaceScoring) + Score-Aggregation auf Match/PlayoffMatchup + isDriverInTeam() (RaceLineup Source of Truth)
- `StandingsService.java` — Team-Tabelle (Match-basiert, nutzt MatchScoring)
- `SeasonManagementService.java` — Team/Car/Track-Pool-Verwaltung fuer Seasons
- `TeamManagementService.java` — Team-Detail-Daten, Farb/Logo-Propagation an Sub-Teams
- `BaseEntity.java` — @MappedSuperclass mit createdAt/updatedAt (JPA Auditing)
- `V1__initial_schema.sql` — Konsolidiertes DB-Schema (eingefroren seit v1.0.0)
- `layout.html` — Thymeleaf Admin-Layout mit Sidebar (Fragment-Pattern: `th:replace="~{admin/layout :: layout(...)}"`)

## Architektur-Prinzipien

- **Controller duenn halten:** Controller sind nur fuer HTTP-Handling zustaendig (Request annehmen, Service aufrufen, Model/Redirect/Flash befuellen). Keine Business-Logik, keine direkten Repository-Zugriffe in Controllern. Geschaeftslogik gehoert in Service-Klassen (`domain.service` oder `admin.service`).
- **DTOs statt Entities in Controllern:** Form-Eingaben (POST/save) immer ueber Form-DTOs (`admin.dto`) binden, nie JPA Entities direkt per `@ModelAttribute` — Schutz gegen Mass Assignment. Fuer Template-Anzeige (GET) duerfen Entities ans Model uebergeben werden (OSIV ist aktiv).
- **Keine Fallback-Berechnungen:** Wenn abgeleitete Daten fehlen, keine Workarounds in Templates oder Controllern einbauen. Stattdessen Datenmodell und Service-Architektur analysieren und die Ursache beheben — Daten muessen an der richtigen Stelle konsistent geschrieben werden.
- **Thymeleaf Templates schlank halten:** Keine komplexe Logik (SpEL-Expressions, Collection-Projektionen, verschachtelte Bedingungen) in Templates. Berechnungen und Datenaufbereitung gehoeren in den Service — Templates nur fuer Darstellung.
- **Keine Inline-Styles auf Buttons:** Statt `style="..."` auf `.btn`-Elementen immer CSS-Klassen aus `admin.css` verwenden (`btn-xs`, `btn-sm`, `btn-lg`, `btn-tab`). Beim Refactoring von Inline-Styles zu CSS-Klassen immer auch JavaScript pruefen, das `element.className = '...'` setzt — dort muessen die neuen Klassen ebenfalls ergaenzt werden.
- **Testdaten komplett isolieren:** E2E-Testdaten in `TestDataService` muessen eigene Entities mit Test-Prefix verwenden (z.B. `T-ALF`, `Test_Alpha_1`, `Test-Season 2026`). Nie echte Teams, Fahrer oder Saisons fuer automatisierte Tests nutzen — diese kollidieren mit manuellen Tests auf Import-Daten.
- **RaceLineup ist Source of Truth:** Fuer Fahrer-Team-Zuordnungen (insb. Sub-Teams) immer `RaceLineup` priorisieren, `SeasonDriver` nur als Fallback fuer Saisons ohne Rennen. Der CSV-Import bestimmt die korrekte Zuordnung.
- **Flyway Migrationen nicht aendern:** Bestehende `V*__*.sql` Dateien duerfen nach Release nie mehr geaendert werden (Flyway prueft Checksummen). Schema-Aenderungen immer als neue Migrationsdatei: `V{N}__{kurzbeschreibung}.sql` (snake_case, englisch). H2 + MariaDB Kompatibilitaet beachten.

## OSIV (Open Session in View)

Bewusst aktiviert (`spring.jpa.open-in-view=true`). Die Hibernate-Session bleibt bis zum Ende des HTTP-Requests offen, damit Thymeleaf lazy-geladene Felder rendern kann. Korrekt fuer diese Admin-Anwendung mit serverseitigem Rendering — kein Lazy-Init-Workaround in Controllern noetig.

## Entwicklungsansatz

- **TDD (Test-Driven Development):** Tests zuerst schreiben, dann Implementierung. Red → Green → Refactor.
- **BDD (Behavior-Driven Development):** Playwright E2E Tests beschreiben das erwartete Verhalten aus Nutzersicht.
- **Test-Naming (Given-When-Then):** Alle Testmethoden folgen dem BDD-Pattern:
  - Methodenname: `givenContext_whenAction_thenExpectedResult()`
  - Body: `// given` / `// when` / `// then` Kommentare zur Strukturierung
  - Bei einfachen Tests ohne Precondition: `whenAction_thenResult()` erlaubt
  - Bei Exception-Tests: `// when / then` kombiniert fuer assertThatThrownBy
- Reihenfolge bei neuen Features: Unit Tests → Implementierung → Integration Tests → E2E Tests
- Superpowers-Skill `superpowers:test-driven-development` nutzen
- **Visuelle Pruefung mit `playwright-cli`:** Bei UI-Aenderungen (Templates, CSS) immer `playwright-cli` nutzen um das Ergebnis visuell zu verifizieren. Dev-Server starten (`./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`), dann mit `playwright-cli open http://localhost:9090/...` die betroffenen Seiten inspizieren (Desktop + Mobile). Skill: `/playwright-cli`

## Code Coverage (JaCoCo)

- **Minimum:** 80% Line Coverage (Build bricht bei Unterschreitung)
- **Report:** `target/site/jacoco/index.html` nach `./mvnw verify`
- **CI:** Automatischer PR-Kommentar mit Coverage via `madrapps/jacoco-report`
- **Excludes:** CtcManagerApplication, TestDataService, DemoDataSeeder, TeamCardService, LineupGraphicService, ResultsGraphicService, SettingsGraphicService, OverlayGraphicService, AbstractGraphicService (Playwright-abhaengig)
- **Schwellwert anpassen:** Erst messen (`jacoco.csv`), dann Minimum setzen — nie optimistisch raten

## Git-Workflow

- **Default-Branch:** `master`
- **Tooling:** `gh` CLI fuer alle GitHub-Operationen (PRs, Issues, etc.)
- **Branching:** Fuer jedes Feature/Fix einen eigenen Branch erstellen
  - Naming: `feature/<kurzbeschreibung>` oder `fix/<kurzbeschreibung>`
  - Branch von `master` abzweigen
- **Pull Requests:** Aenderungen immer ueber PRs in `master` mergen, kein direkter Push
  - `gh pr create --assignee jegr78` zum Erstellen (immer jegr78 zuweisen)
  - `gh pr merge --squash` zum Mergen (saubere History)
  - Nach Merge lokalen Branch aufraeumen: `git switch master && git pull && git branch -d <branch>`
- **Commits:** Englische Commit-Messages mit Conventional Commits Prefixen:
  - `feat:` — Neues Feature (Minor-Bump)
  - `fix:` — Bugfix (Patch-Bump)
  - `docs:` — Dokumentation (Patch-Bump)
  - `chore:` — Maintenance (Patch-Bump)
  - `refactor:` — Refactoring (Patch-Bump)
  - `test:` — Tests (Patch-Bump)
  - `style:` — Formatting/CSS (Patch-Bump)
  - `perf:` — Performance (Patch-Bump)
  - `ci:` — CI/CD (kein Release)
  - `BREAKING CHANGE` im Footer → Major-Bump
  - Format: `<type>(<optional scope>): <description>`
  - Beispiel: `feat(scoring): add penalty point deduction`
- **Vor PR:**
  1. Tests lokal mit `./mvnw verify` sicherstellen
  2. Code-Review der eigenen Aenderungen durchfuehren (superpowers:code-reviewer)
  3. Gefundene Issues beheben und erneut testen
- **Nach PR:**
  1. CI-Build pruefen: `gh run list --branch <branch>` / `gh run view <run-id>`
  2. Bei CI-Failure: Logs analysieren (`gh run view --log-failed`), fixen, pushen
  3. PR darf erst gemergt werden wenn CI gruen ist

## Subagent-Regeln

- **Modellwahl:** Implementierungs-Subagents immer mit `model: "opus"` oder mindestens `model: "sonnet"`. Haiku NUR fuer Read-Only-Tasks (Reviews, Recherche). Nie fuer Code-Aenderungen.
- **Branch-Schutz:** Jeder Subagent-Prompt muss den aktiven Branch benennen und explizit verbieten: kein `git stash`, `git checkout`, `git reset`, kein Branch-Wechsel.
- **Post-Dispatch-Validierung:** Nach JEDEM Subagent sofort pruefen: `git branch --show-current`, `git log --oneline -3`, `git diff --stat`. Bei Abweichung sofort `git reset --hard` auf letzten guten Commit.
- **Plan-Treue:** Subagent-Prompt muss explizit sagen: "Implementiere NUR Task N. Wenn andere Dateien angepasst werden muessen, melde NEEDS_CONTEXT statt selbst zu fixen."
- **Atomare Tasks:** Tasks im Plan muessen einzeln lauffaehig sein. Wenn eine Aenderung mehrere Tasks erzwingt, als einen Task planen.
- **Fallback:** Wenn Subagents trotz Regeln Probleme machen, sequentiell selbst abarbeiten.

## References

- Design Spec: `docs/superpowers/specs/2026-03-26-ctc-manager-design.md`
- Scoring/Legs Spec: `docs/superpowers/specs/2026-03-29-scoring-legs-design.md`
- Release Management Spec: `docs/superpowers/specs/2026-04-03-release-management-design.md`

<!-- GSD:project-start source:PROJECT.md -->
## Project

**CTC Manager — Technical Debt Cleanup**

Systematische Bereinigung aller technischen Schulden im CTC Manager, einer Gran Turismo Racing League Management-Anwendung (Spring Boot 4 / Thymeleaf / MariaDB). Ziel ist eine saubere Codebasis als Grundlage fuer zukuenftige Feature-Entwicklung.

**Core Value:** Architektur-Konsistenz: Alle Controller delegieren an Services, Exception Handling ist zentral, und die Prod-Umgebung ist abgesichert.

### Constraints

- **Testabdeckung**: 82% Line Coverage Minimum darf nicht unterschritten werden
- **Flyway**: Bestehende V1 Migration nicht aendern, nur neue V2+ Migrationen
- **Profile**: Auth nur fuer prod/docker, dev/local bleiben ohne Auth
- **OSIV**: Bleibt aktiviert — nur @EntityGraph-Annotationen als Optimierung
- **Abwaertskompatibilitaet**: Keine Breaking Changes an bestehenden URLs/Endpoints
- **Playwright**: Bleibt Compile-Scope Dependency (Runtime-Nutzung fuer Graphics)
<!-- GSD:project-end -->

<!-- GSD:stack-start source:codebase/STACK.md -->
## Technology Stack

## Languages
- Java 25 - All application code (`src/main/java/org/ctc/`)
- SQL - Database migrations (`src/main/resources/db/migration/V1__initial_schema.sql`, 321 lines)
- HTML/CSS - Thymeleaf templates (`src/main/resources/templates/`)
## Runtime
- Eclipse Temurin JDK 25 (build) / JRE 25 (runtime Docker image)
- CI uses `actions/setup-java@v5` with `distribution: temurin`, `java-version: 25`
- Apache Maven 3.9.14 via Maven Wrapper (`./mvnw`)
- Wrapper config: `.mvn/wrapper/maven-wrapper.properties`
- Lockfile: Not applicable (Maven uses `pom.xml` dependency resolution)
## Frameworks
- Spring Boot 4.0.5 - Application framework (`pom.xml` parent)
- Spring Data JPA - ORM and repository layer
- Spring MVC (WebMVC starter) - HTTP request handling
- Thymeleaf - Server-side HTML templating (admin UI)
- Spring Boot Validation - Bean validation (`jakarta.validation`)
- Spring Boot Actuator - Health endpoint for Docker healthchecks
- Flyway (with `flyway-mysql` dialect) - Schema migrations
- H2 Database - In-memory DB for dev/test profiles
- MariaDB 11 (via `mariadb-java-client`) - Production/local/docker profiles
- JUnit 5 - Test framework (via Spring Boot test starters)
- Mockito - Mocking framework (core + JUnit Jupiter extension)
- Playwright 1.58.0 - E2E browser tests and runtime graphic generation
- Spring Boot Test starters - `data-jpa-test`, `flyway-test`, `thymeleaf-test`, `validation-test`, `webmvc-test`
- Maven Compiler Plugin - Java 25 compilation with Lombok annotation processing
- Maven Surefire Plugin - Unit + integration tests (excludes `**/e2e/**`)
- Maven Failsafe Plugin - E2E tests (activated via `-Pe2e` profile)
- JaCoCo 0.8.13 - Code coverage (82% minimum line coverage enforced)
- Spring Boot Maven Plugin - Fat JAR packaging (excludes Lombok)
## Key Dependencies
- `spring-boot-starter-data-jpa` - JPA/Hibernate ORM, repository pattern
- `spring-boot-starter-thymeleaf` - Admin UI rendering
- `spring-boot-starter-flyway` - Database migration management
- `com.microsoft.playwright` 1.58.0 - Headless Chromium for graphic generation (team cards, lineup graphics, results graphics) AND E2E testing
- `google-api-client` 2.9.0 - Google API client core
- `google-api-services-sheets` v4-rev20250106-2.0.0 - Google Sheets read access (race result import)
- `google-api-services-calendar` v3-rev20250115-2.0.0 - Google Calendar event management
- `google-auth-library-oauth2-http` 1.43.0 - Google service account authentication
- `commons-text` 1.15.0 (Apache) - Fuzzy string matching (driver name matching during import)
- `jsoup` 1.22.1 - HTML parsing for GT7 web scraping
- `lombok` - Boilerplate reduction (@Getter, @Setter, @RequiredArgsConstructor, @Slf4j)
## Configuration
- Spring profiles control all environment-specific config: `dev`, `local`, `docker`, `prod`
- `.env` files present for Docker/prod (`.env`, `.env.dev`, `.env.local`, `.env.example`)
- Production uses environment variables: `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `GOOGLE_CALENDAR_ID`
- Google credentials via file path: `google.sheets.credentials-path` (default: `google-credentials.json`)
- `pom.xml` - Maven build configuration, all dependencies
- `.mvn/wrapper/maven-wrapper.properties` - Maven Wrapper version
- No `package.json` or frontend build tools - pure server-side rendering
- `src/main/resources/application.yml` - Base config (shared across all profiles)
- `src/main/resources/application-dev.yml` - H2 in-memory, port 9090, debug logging
- `src/main/resources/application-local.yml` - MariaDB localhost, port 9091
- `src/main/resources/application-docker.yml` - MariaDB at host `db`, port 8080
- `src/main/resources/application-prod.yml` - Cloud DB via env vars
- OSIV enabled (`spring.jpa.open-in-view: true`) - Intentional for Thymeleaf lazy loading
- File uploads: max 10MB (`spring.servlet.multipart.max-file-size`)
- Upload directory: configurable via `app.upload-dir` (default: `data/dev/uploads`)
- Static site output: configurable via `ctc.site.output-dir` (default: `docs/site`)
## Platform Requirements
- JDK 25 (Eclipse Temurin recommended)
- Maven 3.9+ (or use `./mvnw` wrapper)
- Chromium browser for Playwright: `./mvnw exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"`
- Optional: MariaDB for `local` profile, Docker for `docker` profile
- Docker (multi-stage build: `Dockerfile`)
- MariaDB 11+ (external or Docker Compose)
- Google service account credentials JSON file (optional, for Sheets/Calendar integration)
- Chromium installed in container (Playwright, for graphic generation)
## CI/CD
- Trigger: Push/PR to `master`/`main`
- Steps: Build, Unit/Integration Tests, Install Playwright, E2E Tests, JaCoCo Coverage Report
- Coverage PR comment via `madrapps/jacoco-report@v1.7.2` (min 70% overall, 80% changed files)
- Test reports uploaded as artifacts (7-day retention)
- Trigger: Push to `master` when `docs/site/**` changes, or manual dispatch
- Deploys static site to GitHub Pages
- `docker-compose.yml` - Local dev (App + MariaDB 11)
- `docker-compose.prod.yml` - Production (App only, external DB via env vars)
- Multi-stage Dockerfile: Temurin JDK 25 build, Temurin JRE 25 runtime
- Non-root user `ctc` in container
- Healthcheck via `/actuator/health`
<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->
## Conventions

## Naming Patterns
- Domain layer: `org.ctc.domain.model`, `org.ctc.domain.repository`, `org.ctc.domain.service`
- Admin layer: `org.ctc.admin.controller`, `org.ctc.admin.dto`, `org.ctc.admin.service`
- Feature modules: `org.ctc.dataimport`, `org.ctc.gt7sync`, `org.ctc.sitegen`
- Entities: singular nouns, PascalCase (`Season`, `RaceScoring`, `PlayoffMatchup`)
- Repositories: `{Entity}Repository` (`SeasonRepository`, `RaceLineupRepository`)
- Services: `{Domain}Service` or `{Domain}ManagementService` for complex orchestration (`ScoringService`, `SeasonManagementService`, `TeamManagementService`)
- Controllers: `{Entity}Controller` (`SeasonController`, `RaceController`)
- DTOs: `{Entity}Form` for form binding, `{Entity}Dto` for display data, `{Entity}Data` for record-based view data (`SeasonForm`, `MatchdayDto`, `MatchdayGraphicData`)
- camelCase, verb-first: `calculatePoints()`, `addTeamToSeason()`, `getAvailableTeamsForReplacement()`
- Boolean getters: `isSubTeam()`, `hasSubTeams()`, `isActive()`, `canParse()`
- Controller actions: HTTP verb mapping (`list()`, `detail()`, `create()`, `edit()`, `save()`, `delete()`)
- camelCase, descriptive: `homeStanding`, `matchScoring`, `raceScoring`
- Use `var` for local variables with obvious types (Java 10+ style used consistently)
- UUID parameters named `id` (path) or `{entity}Id` (request param)
- Table names: plural snake_case (`seasons`, `race_scorings`, `season_teams`)
- Column names: snake_case (`created_at`, `race_scoring_id`, `season_year`)
- Join tables: `{parent}_{child}` (`season_cars`, `season_tracks`)
## Lombok Usage
- Always `@Getter @Setter @NoArgsConstructor` on entities
- `@ToString(exclude = ...)` to prevent lazy-loading triggers and circular references
- Entities extend `BaseEntity` for `createdAt`/`updatedAt` auditing
- `@RequiredArgsConstructor` for constructor injection via `final` fields
- `@Slf4j` for logging
## Entity Patterns
## DTO Patterns
- Form DTOs use `@Valid` + `BindingResult` in controllers
- Entities are acceptable in GET model attributes (OSIV is active)
- Display DTOs use Java records where appropriate (e.g., `TeamManagementService.TeamDetailData`)
## Controller Patterns
- `"successMessage"` for success
- `"errorMessage"` for errors
## Import Organization
## Error Handling
- `IllegalStateException` caught and converted to flash error messages:
- Generic `Exception` catch only for IO-related operations (file uploads)
- Throws `IllegalStateException` for business rule violations
- Throws `IllegalArgumentException` for invalid input
- `orElseThrow()` for required entity lookups (NoSuchElementException if missing)
- No custom exception classes -- uses standard Java exceptions
## Logging
- `log.info()` for significant state changes (create, update, delete operations):
- `log.debug()` for calculation results and intermediate state:
- Always use parameterized messages `{}`, never string concatenation
## Transaction Management
## Repository Patterns
## Inner Classes and Records
## Global Model Attributes
## Thymeleaf Templates
## CSS Guidelines
- Use CSS classes from `admin.css` instead of inline styles on buttons
- Size classes: `btn-xs`, `btn-sm`, `btn-lg`, `btn-tab`
- When refactoring inline styles to CSS classes, check JavaScript `element.className = '...'` references
<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->
## Architecture

## Pattern Overview
- Classic three-tier architecture: Controller -> Service -> Repository -> Database
- Domain-centric package structure with clear separation between admin UI, domain logic, and auxiliary modules
- OSIV (Open Session in View) deliberately enabled for lazy-loading in Thymeleaf templates
- No REST API -- all interactions are server-rendered HTML with form submissions and redirects
- Thin controllers delegate all business logic to service classes
- DTOs used for form binding (POST), entities passed directly to templates (GET) thanks to OSIV
## Layers
- Purpose: HTTP request handling for the admin UI -- receives requests, delegates to services, populates Model or redirects
- Location: `src/main/java/org/ctc/admin/controller/`
- Contains: 17 `@Controller` classes, 1 `@ControllerAdvice` (`GlobalModelAdvice`)
- Key files: `SeasonController.java`, `MatchdayController.java`, `RaceController.java`, `MatchController.java`, `PlayoffController.java`, `TeamController.java`, `DriverController.java`, `CarController.java`, `TrackController.java`
- Depends on: Domain services (`domain.service`), admin services (`admin.service`)
- Used by: Thymeleaf templates (via Spring MVC)
- Pattern: POST-Redirect-GET with flash attributes for success/error messages
- Purpose: Form-binding objects for POST requests (Mass Assignment protection)
- Location: `src/main/java/org/ctc/admin/dto/`
- Contains: Form DTOs (`TeamForm`, `CarForm`, `TrackForm`, `DriverForm`, `RaceForm`, etc.), display DTOs (`MatchdayDto`, `SeasonDriverGroupDto`, `MatchdayGraphicData`, `PowerRankingsGraphicData`)
- Depends on: Nothing (POJOs/records)
- Used by: Controllers
- Purpose: Graphic/image generation using Playwright (Chromium headless)
- Location: `src/main/java/org/ctc/admin/service/`
- Contains: `AbstractGraphicService` (base class), `TeamCardService`, `LineupGraphicService`, `ResultsGraphicService`, `MatchResultsGraphicService`, `MatchdayOverviewGraphicService`, `MatchdayResultsGraphicService`, `MatchdayScheduleGraphicService`, `OverlayGraphicService`, `SettingsGraphicService`, `PowerRankingsGraphicService`, `TemplatePreviewService`
- Pattern: Render Thymeleaf HTML template -> take Playwright screenshot -> save PNG
- Depends on: Thymeleaf `TemplateEngine`, Playwright, file system
- Used by: Admin controllers (TeamCardController, RaceController, MatchdayController, etc.)
- Purpose: JPA entities representing the core domain
- Location: `src/main/java/org/ctc/domain/model/`
- Contains: 18 entity classes + 1 `@MappedSuperclass` (`BaseEntity`) + enums
- Depends on: JPA/Hibernate, Jakarta Validation
- Used by: Repositories, services, controllers (via OSIV)
- Purpose: Spring Data JPA interfaces for database access
- Location: `src/main/java/org/ctc/domain/repository/`
- Contains: 17 repository interfaces extending `JpaRepository`
- Depends on: Spring Data JPA, domain model
- Used by: Domain services, admin services, import services
- Purpose: Business logic -- scoring, standings, rankings, season/team/match management
- Location: `src/main/java/org/ctc/domain/service/`
- Contains: 14 service classes
- Key files: `ScoringService.java` (points calculation + match score aggregation), `StandingsService.java` (league table), `DriverRankingService.java`, `SeasonManagementService.java`, `TeamManagementService.java`, `MatchService.java`, `MatchdayService.java`, `MatchdayGeneratorService.java`, `RaceManagementService.java`, `PlayoffService.java`, `SwissPairingService.java`, `RaceLineupService.java`, `DriverService.java`, `FileStorageService.java`
- Depends on: Domain repositories, domain model
- Used by: Admin controllers, site generator, CSV import
- Purpose: CSV file import for race results + Google Sheets/Calendar integration
- Location: `src/main/java/org/ctc/dataimport/`
- Contains: `CsvImportService.java` (parse/preview/execute flow), `CsvImportController.java`, `ScorecardParser.java`, `GoogleSheetsService.java`, `DriverMatchingService.java`, `GoogleCalendarService.java`
- Pattern: Two-phase import -- parse+preview (showing fuzzy matches) then confirm+execute
- Depends on: Domain repositories, `ScoringService`
- Purpose: Scrape Gran Turismo 7 car/track data from external website, sync to local DB
- Location: `src/main/java/org/ctc/gt7sync/`
- Contains: `Gt7ScraperService.java` (HTML scraping via Jsoup), `Gt7SyncService.java` (preview + execute), `Gt7SyncController.java`, `Gt7SyncPreview.java`
- Pattern: Two-phase sync with preview (like import)
- Depends on: Car/Track repositories, `FileStorageService`
- Purpose: Generate static HTML pages from Thymeleaf templates for public-facing league website
- Location: `src/main/java/org/ctc/sitegen/`
- Contains: `SiteGeneratorService.java`, `SiteGeneratorController.java`, `model/RaceView.java`
- Pattern: Reads all season data, renders Thymeleaf templates to static HTML files, copies assets
- Output: `docs/site/` (or `target/site` in dev)
- Depends on: Domain repositories, `StandingsService`, `DriverRankingService`, `PlayoffService`
- Purpose: Application startup, configuration, dev data seeding
- Location: `src/main/java/org/ctc/admin/`
- Contains: `WebConfig.java` (upload dir resource handler), `DevDataSeeder.java` (dev profile), `DemoDataSeeder.java` (demo profile), `TestDataService.java` (shared test data creation)
- Pattern: `CommandLineRunner` for dev/demo profile seeding
## Data Flow
- All state is persisted in the relational database (MariaDB in prod, H2 in dev)
- No application-level caching or session state beyond standard Spring MVC
- File uploads stored on local filesystem (`data/dev/uploads/` or configured path)
## Domain Model (Entity Graph)
```
```
## Key Abstractions
- Purpose: `@MappedSuperclass` providing `createdAt`/`updatedAt` via JPA Auditing (`@EntityListeners(AuditingEntityListener.class)`)
- All entities extend this
- File: `src/main/java/org/ctc/domain/model/BaseEntity.java`
- Purpose: Base class for all Playwright-based graphic generators
- Provides: `renderScreenshot()`, `renderScreenshotTransparent()`, `encodeCardBase64()`, `encodeClasspathResource()`, `processStringTemplate()`
- File: `src/main/java/org/ctc/admin/service/AbstractGraphicService.java`
- Extended by: All graphic service classes in `admin.service`
- Purpose: Intermediate base class for matchday-specific graphics (schedule, results, overview)
- Adds matchday data preparation helpers
- File: `src/main/java/org/ctc/admin/service/AbstractMatchdayGraphicService.java`
- Purpose: Decouple scoring rules from code -- points arrays stored as comma-separated strings in DB
- `RaceScoring`: race position points, quali points, fastest lap points
- `MatchScoring`: win/draw/loss match points
- Each Season references one of each
- Files: `src/main/java/org/ctc/domain/model/RaceScoring.java`, `src/main/java/org/ctc/domain/model/MatchScoring.java`
- Purpose: Join table with extra attributes -- per-season team rating, color overrides, logo overrides, and team succession (replacement) tracking
- File: `src/main/java/org/ctc/domain/model/SeasonTeam.java`
## Entry Points
- Location: `src/main/java/org/ctc/CtcManagerApplication.java`
- Triggers: `SpringApplication.run()`, enables `@EnableJpaAuditing`
- Responsibilities: Bootstrap Spring Boot context
- Location: `src/main/java/org/ctc/admin/controller/AdminRedirectController.java`
- Triggers: Any request to `/` or `/admin`
- Responsibilities: Redirect to `/admin/seasons` (the default landing page)
- Location: `src/main/java/org/ctc/admin/DevDataSeeder.java`
- Triggers: Application startup with `dev` profile active
- Responsibilities: Calls `TestDataService.seed()` to create test teams, seasons, drivers, scoring presets
- Location: `src/main/java/org/ctc/admin/DemoDataSeeder.java`
- Triggers: Application startup with `demo` profile active
- Responsibilities: Imports all GT7 cars and tracks with images for manual testing
## Error Handling
- Controllers catch `IllegalStateException` from services, convert to flash error messages, redirect back
- Global error page: `src/main/resources/templates/error.html`
- Services throw `IllegalStateException` or `IllegalArgumentException` for business rule violations
- No custom exception hierarchy -- standard Java exceptions used throughout
- `@Transactional` ensures atomicity -- failures roll back the entire operation
## Cross-Cutting Concerns
<!-- GSD:architecture-end -->

<!-- GSD:workflow-start source:GSD defaults -->
## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:
- `/gsd:quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd:debug` for investigation and bug fixing
- `/gsd:execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->

<!-- GSD:profile-start -->
## Developer Profile

> Profile not yet configured. Run `/gsd:profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->
