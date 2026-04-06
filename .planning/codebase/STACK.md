# Technology Stack

**Analysis Date:** 2026-04-03

## Languages

**Primary:**
- Java 25 - All application code (`src/main/java/org/ctc/`)

**Secondary:**
- SQL - Database migrations (`src/main/resources/db/migration/V1__initial_schema.sql`, 321 lines)
- HTML/CSS - Thymeleaf templates (`src/main/resources/templates/`)

## Runtime

**Environment:**
- Eclipse Temurin JDK 25 (build) / JRE 25 (runtime Docker image)
- CI uses `actions/setup-java@v5` with `distribution: temurin`, `java-version: 25`

**Package Manager:**
- Apache Maven 3.9.14 via Maven Wrapper (`./mvnw`)
- Wrapper config: `.mvn/wrapper/maven-wrapper.properties`
- Lockfile: Not applicable (Maven uses `pom.xml` dependency resolution)

## Frameworks

**Core:**
- Spring Boot 4.0.5 - Application framework (`pom.xml` parent)
- Spring Data JPA - ORM and repository layer
- Spring MVC (WebMVC starter) - HTTP request handling
- Thymeleaf - Server-side HTML templating (admin UI)
- Spring Boot Validation - Bean validation (`jakarta.validation`)
- Spring Boot Actuator - Health endpoint for Docker healthchecks

**Database:**
- Flyway (with `flyway-mysql` dialect) - Schema migrations
- H2 Database - In-memory DB for dev/test profiles
- MariaDB 11 (via `mariadb-java-client`) - Production/local/docker profiles

**Testing:**
- JUnit 5 - Test framework (via Spring Boot test starters)
- Mockito - Mocking framework (core + JUnit Jupiter extension)
- Playwright 1.58.0 - E2E browser tests and runtime graphic generation
- Spring Boot Test starters - `data-jpa-test`, `flyway-test`, `thymeleaf-test`, `validation-test`, `webmvc-test`

**Build/Dev:**
- Maven Compiler Plugin - Java 25 compilation with Lombok annotation processing
- Maven Surefire Plugin - Unit + integration tests (excludes `**/e2e/**`)
- Maven Failsafe Plugin - E2E tests (activated via `-Pe2e` profile)
- JaCoCo 0.8.13 - Code coverage (82% minimum line coverage enforced)
- Spring Boot Maven Plugin - Fat JAR packaging (excludes Lombok)

## Key Dependencies

**Critical:**
- `spring-boot-starter-data-jpa` - JPA/Hibernate ORM, repository pattern
- `spring-boot-starter-thymeleaf` - Admin UI rendering
- `spring-boot-starter-flyway` - Database migration management
- `com.microsoft.playwright` 1.58.0 - Headless Chromium for graphic generation (team cards, lineup graphics, results graphics) AND E2E testing

**External Service SDKs:**
- `google-api-client` 2.9.0 - Google API client core
- `google-api-services-sheets` v4-rev20250106-2.0.0 - Google Sheets read access (race result import)
- `google-api-services-calendar` v3-rev20250115-2.0.0 - Google Calendar event management
- `google-auth-library-oauth2-http` 1.43.0 - Google service account authentication

**Utility:**
- `commons-text` 1.15.0 (Apache) - Fuzzy string matching (driver name matching during import)
- `jsoup` 1.22.1 - HTML parsing for GT7 web scraping
- `lombok` - Boilerplate reduction (@Getter, @Setter, @RequiredArgsConstructor, @Slf4j)

## Configuration

**Environment:**
- Spring profiles control all environment-specific config: `dev`, `local`, `docker`, `prod`
- `.env` files present for Docker/prod (`.env`, `.env.dev`, `.env.local`, `.env.example`)
- Production uses environment variables: `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `GOOGLE_CALENDAR_ID`
- Google credentials via file path: `google.sheets.credentials-path` (default: `google-credentials.json`)

**Build:**
- `pom.xml` - Maven build configuration, all dependencies
- `.mvn/wrapper/maven-wrapper.properties` - Maven Wrapper version
- No `package.json` or frontend build tools - pure server-side rendering

**Application Config Files:**
- `src/main/resources/application.yml` - Base config (shared across all profiles)
- `src/main/resources/application-dev.yml` - H2 in-memory, port 9090, debug logging
- `src/main/resources/application-local.yml` - MariaDB localhost, port 9091
- `src/main/resources/application-docker.yml` - MariaDB at host `db`, port 8080
- `src/main/resources/application-prod.yml` - Cloud DB via env vars

**Key Settings:**
- OSIV enabled (`spring.jpa.open-in-view: true`) - Intentional for Thymeleaf lazy loading
- File uploads: max 10MB (`spring.servlet.multipart.max-file-size`)
- Upload directory: configurable via `app.upload-dir` (default: `data/dev/uploads`)
- Static site output: configurable via `ctc.site.output-dir` (default: `docs/site`)

## Platform Requirements

**Development:**
- JDK 25 (Eclipse Temurin recommended)
- Maven 3.9+ (or use `./mvnw` wrapper)
- Chromium browser for Playwright: `./mvnw exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"`
- Optional: MariaDB for `local` profile, Docker for `docker` profile

**Production:**
- Docker (multi-stage build: `Dockerfile`)
- MariaDB 11+ (external or Docker Compose)
- Google service account credentials JSON file (optional, for Sheets/Calendar integration)
- Chromium installed in container (Playwright, for graphic generation)

## CI/CD

**CI Pipeline:** GitHub Actions (`.github/workflows/ci.yml`)
- Trigger: Push/PR to `master`/`main`
- Steps: Build, Unit/Integration Tests, Install Playwright, E2E Tests, JaCoCo Coverage Report
- Coverage PR comment via `madrapps/jacoco-report@v1.7.2` (min 70% overall, 80% changed files)
- Test reports uploaded as artifacts (7-day retention)

**Site Deployment:** GitHub Actions (`.github/workflows/deploy-site.yml`)
- Trigger: Push to `master` when `docs/site/**` changes, or manual dispatch
- Deploys static site to GitHub Pages

**Docker:**
- `docker-compose.yml` - Local dev (App + MariaDB 11)
- `docker-compose.prod.yml` - Production (App only, external DB via env vars)
- Multi-stage Dockerfile: Temurin JDK 25 build, Temurin JRE 25 runtime
- Non-root user `ctc` in container
- Healthcheck via `/actuator/health`

---

*Stack analysis: 2026-04-03*
