# Technology Stack

**Analysis Date:** 2026-04-04

## Languages

**Primary:**
- Java 25 (Eclipse Temurin) - All application code (`src/main/java/org/ctc/`)

**Secondary:**
- SQL - Flyway migrations (`src/main/resources/db/migration/`)
- HTML/CSS - Thymeleaf templates (`src/main/resources/templates/`)

## Runtime

**Environment:**
- Java 25 (Eclipse Temurin JDK for build, JRE for runtime)
- Spring Boot 4.0.5

**Package Manager:**
- Apache Maven 3.9.14 via Maven Wrapper (`./mvnw`)
- Wrapper config: `.mvn/wrapper/maven-wrapper.properties`
- Lockfile: Not applicable (Maven uses `pom.xml` with explicit versions)

## Frameworks

**Core:**
- Spring Boot 4.0.5 - Application framework (`pom.xml` parent)
- Spring MVC (WebMVC starter) - HTTP request handling, server-rendered HTML
- Spring Data JPA - Repository layer, Hibernate ORM
- Spring Security - Authentication (prod/docker profiles only)
- Spring Boot Actuator - Health endpoint (`/actuator/health`)
- Thymeleaf - Server-side HTML template rendering
- Flyway (with `flyway-mysql` dialect) - Database schema migration
- Lombok - Boilerplate reduction (@Getter, @Setter, @RequiredArgsConstructor, @Slf4j)

**Testing:**
- JUnit 5 - Test framework (via Spring Boot test starters)
- Mockito - Mocking framework (core + JUnit Jupiter extension, with `-javaagent` for Surefire/Failsafe)
- Playwright 1.58.0 - E2E browser tests and graphic generation
- JaCoCo 0.8.13 - Code coverage (82% line coverage minimum enforced at BUNDLE level)
- Spring Boot Test starters - `data-jpa-test`, `flyway-test`, `thymeleaf-test`, `validation-test`, `webmvc-test`, `security-test`

**Build/Dev:**
- Maven Compiler Plugin - Java 25 compilation with Lombok annotation processing
- Maven Surefire Plugin - Unit + integration tests (excludes `**/e2e/**`)
- Maven Failsafe Plugin - E2E tests (activated via `-Pe2e` profile, includes `**/e2e/**`)
- Maven Versions Plugin - Version management for releases
- JaCoCo 0.8.13 - Coverage reporting and enforcement
- Spring Boot Maven Plugin - Fat JAR packaging (excludes Lombok)

## Key Dependencies

**Critical:**
- `spring-boot-starter-data-jpa` - JPA/Hibernate persistence layer
- `spring-boot-starter-thymeleaf` - Server-side rendering engine
- `spring-boot-starter-webmvc` - HTTP handling (POST-Redirect-GET pattern)
- `spring-boot-starter-validation` - Bean validation (`@Valid`, `BindingResult`)
- `spring-boot-starter-security` - Auth for prod/docker profiles
- `spring-boot-starter-flyway` - Schema migration management
- `com.microsoft.playwright:playwright:1.58.0` - Browser automation (compile scope: runtime image generation + E2E tests)

**Infrastructure:**
- `org.mariadb.jdbc:mariadb-java-client` (runtime) - Production database driver
- `com.h2database:h2` (runtime) - Dev/test in-memory database
- `org.flywaydb:flyway-mysql` - MariaDB/H2 migration dialect support
- `org.projectlombok:lombok` (optional) - Compile-time annotation processing

**External API:**
- `com.google.api-client:google-api-client:2.9.0` - Google API base client
- `com.google.apis:google-api-services-sheets:v4-rev20250106-2.0.0` - Google Sheets read access
- `com.google.apis:google-api-services-calendar:v3-rev20250115-2.0.0` - Google Calendar event management
- `com.google.auth:google-auth-library-oauth2-http:1.43.0` - Google service account auth

**Utility:**
- `org.apache.commons:commons-text:1.15.0` - Fuzzy string matching (driver name matching during import)
- `org.jsoup:jsoup:1.22.1` - HTML parsing for GT7 web scraping

## Database

**Production:**
- MariaDB (profiles: `prod`, `docker`, `local`)
- Connection via `org.mariadb.jdbc.Driver`
- Env vars: `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` (prod profile)
- Docker network host: `db:3306` (docker profile)
- Local: `localhost:3306` (local profile)

**Development/Test:**
- H2 in-memory (`jdbc:h2:mem:ctcdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`, profile: `dev`)
- H2 console enabled at `/h2-console` in dev
- Username: `sa`, no password

**Migrations:**
- Flyway with `classpath:db/migration` location
- Current migrations: `V1__initial_schema.sql` (frozen since v1.0.0), `V2__add_fk_indexes.sql`
- JPA `ddl-auto: validate` on all profiles (Flyway manages schema exclusively)
- Constraint: H2 + MariaDB compatibility required in all migration files
- Rule: Never modify existing migration files; always create new `V{N}__{description}.sql`

**OSIV:**
- `spring.jpa.open-in-view: true` - Deliberately enabled for Thymeleaf lazy loading
- OSIV warning suppressed in logging config

## Configuration

**Environment:**
- Profile-based: `application-{dev,local,docker,prod}.yml`
- Base config: `src/main/resources/application.yml`
- Dev: port 9090, H2, debug logging, H2 console, no cache
- Local: port 9091, MariaDB localhost
- Docker: port 8080, MariaDB `db:3306`, actuator health details
- Prod: port 8080, MariaDB via env vars
- File upload limit: 10MB (`spring.servlet.multipart.max-file-size`)

**Key Properties:**
- `app.upload-dir` - File upload directory (default: `data/dev/uploads`)
- `app.version` - Application version from `pom.xml` (`@project.version@`)
- `google.sheets.credentials-path` - Google service account JSON (default: `google-credentials.json`)
- `google.calendar.id` - Google Calendar ID (env var: `GOOGLE_CALENDAR_ID`)
- `ctc.site.output-dir` - Static site output directory (default: `docs/site`)

**Build:**
- `pom.xml` - Maven project descriptor (version: 1.2.0-SNAPSHOT)
- `.mvn/wrapper/maven-wrapper.properties` - Maven wrapper config (3.9.14)
- `Dockerfile` - Multi-stage build (JDK 25 build, JRE 25 runtime)

## Deployment

**Docker:**
- Multi-stage Dockerfile: `eclipse-temurin:25-jdk` (build) -> `eclipse-temurin:25-jre` (runtime)
- Non-root user `ctc` for runtime
- Installs Chromium system dependencies (libnss3, libgbm1, etc.) for Playwright
- Playwright browser path: `PLAYWRIGHT_BROWSERS_PATH=/app/.playwright`
- Healthcheck via `/actuator/health`
- Directories: `/app/uploads`, `/app/ctc-site-output`, `/app/logs`
- `docker-compose.yml` - Local dev (App + MariaDB)
- `docker-compose.prod.yml` - Production (external DB, `.env` config)

**Container Registry:**
- GitHub Container Registry (GHCR): `ghcr.io/{owner}/ctc-manager`
- Tags: `{version}` + `latest` on each release

**CI/CD (GitHub Actions):**
- `.github/workflows/ci.yml` - Push/PR to master: Build, unit/integration tests, Playwright E2E, JaCoCo coverage PR comment
  - Coverage PR comment via `madrapps/jacoco-report@v1.7.2` (min 70% overall, 80% changed files)
  - Test reports + coverage uploaded as artifacts (7-day retention)
- `.github/workflows/release.yml` - Push to master: Conventional Commits version bump, GitHub Release with JAR, Docker build+push to GHCR, SNAPSHOT bump
  - Uses `RELEASE_TOKEN` PAT for branch protection bypass
  - Skips `[skip ci]` commits (SNAPSHOT bumps)
- `.github/workflows/deploy-site.yml` - Push `docs/site/**` to master: GitHub Pages deployment
- Java 25 Temurin with Maven cache on all workflows

**Release Process:**
- Automated via Conventional Commits (feat = minor, fix/others = patch, BREAKING CHANGE = major)
- Flow: set release version in pom.xml -> verify build -> commit + tag -> GitHub Release with JAR -> Docker push to GHCR -> bump to next SNAPSHOT
- Initial release detection: reads version from pom.xml SNAPSHOT if no tags exist

## Platform Requirements

**Development:**
- JDK 25 (Eclipse Temurin recommended)
- Maven 3.9+ (or use `./mvnw` wrapper)
- Chromium for Playwright: `./mvnw exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"`
- Optional: MariaDB for `local` profile
- Optional: `google-credentials.json` for Google Sheets/Calendar integration

**Production:**
- Docker with `eclipse-temurin:25-jre` (or standalone Java 25 JRE)
- MariaDB database
- Environment variables: `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`
- Optional: `GOOGLE_CALENDAR_ID`, Google credentials file
- Chromium (auto-installed in Docker image for Playwright graphics)

---

*Stack analysis: 2026-04-04*
