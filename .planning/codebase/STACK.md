# Technology Stack

**Analysis Date:** 2026-05-18

## Languages

**Primary:**
- Java 25 (Eclipse Temurin) - Application logic, Spring Boot services, controllers, repositories, Flyway migrations

**Secondary:**
- SQL - MariaDB/H2 schema migrations via Flyway
- HTML/Thymeleaf - Server-side template rendering for admin UI

## Runtime

**Environment:**
- Java 25 (Eclipse Temurin)
- Spring Boot 4.0.6 (`pom.xml` parent version)

**Package Manager:**
- Maven 3.x (via `./mvnw` wrapper)
- Lockfile: Yes (`pom.xml` enforces versions; no external lock mechanism)

## Frameworks

**Core:**
- Spring Boot 4.0.6 - Web framework, dependency injection, auto-configuration
- Spring Data JPA - ORM abstraction, repository pattern
- Spring Security - Authentication/authorization (profile-restricted to `prod`/`docker`)
- Spring Boot Actuator - Health endpoints, metrics

**Server-Side Rendering:**
- Thymeleaf 3.1.5.RELEASE - Server-side template rendering (no separate frontend build)

**Testing:**
- JUnit 5 - Test framework
- Mockito 5.x - Mocking framework
- Spring Boot Test - TestContext framework, `@SpringBootTest`, `@DataJpaTest`
- Testcontainers 2.0.5 - Live MariaDB in integration tests (MariaDB:11)
- Playwright 1.59.0 - E2E tests, Team Card graphics generation (runtime dependency)

**Build/Dev:**
- Maven Surefire 3.x - Unit/integration test runner (tag-based routing via `@Tag`)
- Maven Failsafe 3.x - Integration test runner (tag-based routing via `@Tag`)
- JaCoCo 0.8.14 - Code coverage measurement + enforcement (82% minimum line coverage)
- SpotBugs 4.9.8.3 + find-sec-bugs 1.14.0 - Static bytecode analysis (Medium+HIGH blocking gate)
- OpenRewrite 6.39.0 (`-Prewrite` profile) - Recipe-driven code refactoring (dry-run and apply modes)
- Lombok 1.18.46 - Code generation (annotations, constructors, getters, setters)

## Key Dependencies

**Critical:**
- `spring-boot-starter-data-jpa` - Hibernate ORM with JPA
- `spring-boot-starter-thymeleaf` - Template engine
- `spring-boot-starter-webmvc` - Web framework
- `spring-boot-starter-security` - Authentication/authorization
- `spring-boot-starter-validation` - Bean validation (Jakarta)
- `spring-boot-starter-flyway` - Database migration management

**Database Drivers:**
- `mariadb-java-client` - MariaDB JDBC driver (prod/local/docker)
- `h2` - H2 in-memory database (dev/test profiles)

**External APIs & Services:**
- `google-api-client` 2.9.0 - Google API client library
- `google-api-services-sheets` v4-rev20250106-2.0.0 - Google Sheets API v4
- `google-api-services-calendar` v3-rev20250115-2.0.0 - Google Calendar API v3
- `google-auth-library-oauth2-http` 1.46.0 - OAuth2 authentication for Google APIs
- `guava` 33.4.8-jre - Google utilities (overridden to suppress Java 25 Unsafe deprecation warnings)
- `jsoup` 1.22.2 - HTML parser for GT7 web scraping
- `commons-text` 1.15.0 - Fuzzy string matching utilities

**Playwright:**
- `playwright` 1.59.0 (compile scope) - Browser automation for Team Card PNG generation + E2E tests

**JSON Serialization:**
- `jackson-datatype-jsr310` - Jackson Java-Time module (Instant/LocalDateTime serialization in backup exports)

**Code Generation (Annotation Processing):**
- `spotbugs-annotations` 4.9.8 - SpotBugs suppression annotations (provided scope, compile-only)

**Testing Utilities:**
- `testcontainers` 2.0.5 - Docker-based live MariaDB for integration tests
- `testcontainers-junit-jupiter` 2.0.5 - JUnit 5 integration
- `testcontainers-mariadb` 2.0.5 - MariaDB container image and health checks
- `mockito-core`, `mockito-junit-jupiter` - Mocking framework

## Configuration

**Environment (Profile-Specific):**
- `application.yml` - Common baseline (OSIV enabled, Flyway, Actuator)
- `application-dev.yml` - Development: H2 in-memory, port 9090, debug logging, H2 console at `/h2-console`
- `application-local.yml` - Local: MariaDB on localhost:3306, port 9091, `rewriteBatchedStatements=true` for batch inserts
- `application-docker.yml` - Docker Compose: MariaDB at hostname `db`, port 8080, file paths under `/app/`
- `application-prod.yml` - Production: External MariaDB (environment variables `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`)

**Build:**
- `pom.xml` - Primary Maven configuration; defines all dependencies, plugins, build lifecycle, profiles
- `rewrite.yml` - OpenRewrite recipe configuration (activates `org.openrewrite.staticanalysis.CommonStaticAnalysis`)
- `.mvn/maven.config` - Maven wrapper configuration
- `lombok.config` - Lombok settings (enables `@SuppressFBWarnings` auto-generation by `addSuppressFBWarnings=true`)
- `config/spotbugs-exclude.xml` - SpotBugs suppression filter (EI_EXPOSE_REP* on entities/DTOs, class-level exclusions for test-data service)

**GitHub Actions Workflows:**
- `.github/workflows/ci.yml` - Build, unit tests, integration tests, E2E tests, JaCoCo coverage report comments
- `.github/workflows/codeql.yml` - CodeQL SAST (java-kotlin, security-extended), alert gate on HIGH/CRITICAL, weekly cron
- `.github/workflows/mariadb-migration-smoke.yml` - Testcontainers MariaDB round-trip validation
- `.github/workflows/deploy-site.yml` - GitHub Pages deployment of static site output
- `.github/workflows/release.yml` - Semantic versioning, Docker image build and push, GitHub release creation

**Google Integration:**
- `google-credentials.json` - Service account credentials (file path: `google-credentials.json`, configured via `google.sheets.credentials-path`)
- Environment variable: `GOOGLE_CALENDAR_ID` - Google Calendar ID for race event scheduling

**Docker:**
- `Dockerfile` - Multi-stage build: Maven build on JDK 25, runtime on JRE 25 (pinned to `-noble` Ubuntu variant for Playwright 1.59.0 compatibility)
- `docker-compose.yml` - Local development: MariaDB:11 service + Spring Boot app service, shared volumes for uploads and site output

## Platform Requirements

**Development:**
- Java 25 (Eclipse Temurin JDK)
- Maven 3.8+
- Docker (optional, for local MariaDB via `docker compose`)
- Chromium browser (auto-installed by Playwright on first use; cached in CI)

**Production:**
- Java 25 (Eclipse Temurin JRE)
- MariaDB 11.x (external)
- Docker (for containerized deployment)
- Environment variables: `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `GOOGLE_CALENDAR_ID`

## Special Considerations

**OSIV (Open Session in View):**
- `spring.jpa.open-in-view: true` in `application.yml` - Deliberately enabled for Thymeleaf SSR; Hibernate session remains open through the HTTP response so templates can render lazy-loaded associations without explicit fetching

**Static Analysis Gates:**
- SpotBugs (Medium+HIGH threshold) runs on every `./mvnw verify`; blocking gate in `pom.xml` via `spotbugs-maven-plugin` 4.9.8.3
- find-sec-bugs 1.14.0 plugin adds Spring Security-aware detection patterns (SSRF, path traversal, injection)
- Exclusions in `config/spotbugs-exclude.xml`; Lombok-generated methods covered by `lombok.config` `addSuppressFBWarnings=true`

**Code Coverage:**
- JaCoCo 0.8.14 enforces minimum 82% line coverage across all bundles
- Excludes: Spring Boot entry point, test-data services, all Graphic/Team Card Playwright-based services
- Report generated on every `./mvnw verify`; published to GitHub on PRs via `madrapps/jacoco-report`

**Test Routing:**
- Tag-based via `@Tag` annotation (not filename-based)
- Surefire: runs tests WITHOUT `@Tag("integration")`, `@Tag("e2e")`, `@Tag("flaky")` (unit tests)
- Failsafe default-it execution: runs `@Tag("integration")` tests (Spring context ITs, smoke tests)
- Failsafe e2e execution (enabled via `-Pe2e`): runs `@Tag("e2e")` tests only (Playwright tests)
- Flaky tests tagged to exclude from standard runs; manually verified before merging

**JEP 498 Workarounds:**
- Java 25 terminally deprecates `sun.misc.Unsafe`; Surefire/Failsafe/compiler jobs pass `--sun-misc-unsafe-memory-access=allow` to silence Lombok warnings until upstream fix lands

**OpenRewrite Profile:**
- Activated via `-Prewrite` flag: `./mvnw -Prewrite rewrite:dryRun` (preview) or `rewrite:run` (apply)
- Whitelist-only recipe activation; `rewrite.yml` enables only `CommonStaticAnalysis` (≈70 sub-recipes)
- Provides automated cleanup and migration recipes

---

*Stack analysis: 2026-05-18*
