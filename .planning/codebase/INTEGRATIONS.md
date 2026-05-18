# External Integrations

**Analysis Date:** 2026-05-18

## APIs & External Services

**Google Sheets (Race Import):**
- Service: Google Sheets API v4
  - SDK/Client: `google-api-services-sheets` v4-rev20250106-2.0.0
  - Implementation: `src/main/java/org/ctc/dataimport/GoogleSheetsService.java`
  - Controller: `src/main/java/org/ctc/dataimport/DriverSheetImportController.java`
  - Purpose: Import driver lineups and race results from shared Google Sheets
  - Auth: Service account credentials file (path: `google-credentials.json`, configured via `google.sheets.credentials-path`)
  - Scopes: Read-only access to designated Sheets

**Google Calendar (Race Event Scheduling):**
- Service: Google Calendar API v3
  - SDK/Client: `google-api-services-calendar` v3-rev20250115-2.0.0
  - Implementation: `src/main/java/org/ctc/dataimport/GoogleCalendarService.java`
  - Purpose: Query and display race event dates in the application calendar
  - Auth: Service account credentials file (same `google-credentials.json`)
  - Environment Variable: `GOOGLE_CALENDAR_ID` - Target calendar ID
  - Scopes: Read-only access to specified calendar

**GT7 Web Scraping (Car/Track Metadata):**
- Service: GT7 (Gran Turismo 7) web portal
  - Client: Jsoup 1.22.2
  - Implementation: `src/main/java/org/ctc/gt7sync/Gt7ScraperService.java`
  - Purpose: Scrape and cache GT7 car and track metadata (names, images, class ratings)
  - Integration Points:
    - `src/main/java/org/ctc/gt7sync/Gt7SyncService.java` - Orchestrates scraping and DB import
    - `src/main/java/org/ctc/gt7sync/Gt7SyncController.java` - Admin UI for manual sync
    - `src/main/java/org/ctc/admin/DemoDataSeeder.java` - Demo profile auto-seeding
  - Frequency: On-demand via admin UI; auto-run in `dev,demo` profile at startup
  - Data Cached: Car names, images, track names, class ratings, manufacturers

**YouTube Scraping (Channel Metadata):**
- Service: YouTube
  - Client: Jsoup 1.22.2
  - Implementation: `src/main/java/org/ctc/sitegen/YouTubeScraperService.java`
  - Purpose: Scrape team channel subscriber counts and video stats for site generation
  - Integration: Part of static site generation pipeline (`docs/site/`)

## Data Storage

**Databases:**

**MariaDB (Production/Local/Docker):**
- Provider: MariaDB 11.x (external, self-hosted, or Docker)
- Connection String (profile-specific):
  - `local`: `jdbc:mariadb://localhost:3306/ctcdb?rewriteBatchedStatements=true`
  - `docker`: `jdbc:mariadb://db:3306/ctcdb?rewriteBatchedStatements=true` (hostname `db` in Docker network)
  - `prod`: `${DATABASE_URL}` (environment variable, must include `rewriteBatchedStatements=true`)
- Driver: `org.mariadb.jdbc.Driver`
- Username/Password: `ctc`/`ctc` (local/docker), environment variables for prod
- Credentials: `application-local.yml`, `application-docker.yml`, `application-prod.yml`
- Client: Spring Data JPA with Hibernate 6.x ORM
- Migration: Flyway (schema versioning via `src/main/resources/db/migration/V*.sql`)
  - Current migrations: V1 (initial schema), V2 (FK indexes), V3 (season phase tables), V7 (data import audit)
  - H2 + MariaDB compatible SQL (validated on both engines)

**H2 (Development/Test):**
- Provider: H2 in-memory
- Connection String: `jdbc:h2:mem:ctcdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`
- Driver: `org.h2.Driver`
- Purpose: Fast unit/integration tests without external DB
- Configuration: `application-dev.yml`
- Features: Console enabled at `/h2-console` (dev profile)
- Scope: Test classes, development mode (`-Dspring.profiles.active=dev`)

**File Storage:**
- **Local Filesystem:** Upload directory configured per profile
  - `dev`: `data/dev/uploads`
  - `local`: `data/local/uploads`
  - `docker`: `/app/uploads` (mounted volume)
  - `prod`: Environment-specific (e.g., mounted volume or object storage compatible path)
- **Site Output:** Static HTML generated to:
  - `dev`: `target/site` (Maven build output, per `application-dev.yml`)
  - Production: `docs/site` (repo-tracked)
  - `docker`: `/app/ctc-site-output` (mounted volume)
- **Backup Staging:** Per-profile working directories under `data/{profile}/backup-staging/`

**Caching:**
- None explicit (no Redis/Memcached)
- Hibernate second-level cache: Not configured (relies on first-level session cache + lazy loading via OSIV)

## Authentication & Identity

**Auth Provider:**
- Spring Security built-in (no external OAuth provider)

**Implementation:**
- `prod` profile: HTTP Basic auth with hardcoded user (`spring.security.user.name`, `spring.security.user.password`)
- `docker` profile: Same as prod
- `dev`/`local` profiles: No security constraints (auth disabled via profile-specific Spring Security config)

**Credentials (Docker Compose):**
- Environment variables passed to app at startup:
  - `SPRING_SECURITY_USER_NAME=admin`
  - `SPRING_SECURITY_USER_PASSWORD=ctc-admin` (example; should be externalized for production)

## Monitoring & Observability

**Error Tracking:**
- None (no Sentry, Rollbar, etc.)
- Logs written to console and optional file-based logging (configurable)

**Logs:**
- **Approach:** SLF4J + Logback (Spring Boot defaults)
- **Configuration:** `application.yml` baseline + profile-specific overrides
- **Dev Logging:** DEBUG level for `org.ctc.*` packages
- **Prod Logging:** INFO level (configurable via environment)
- **OSIV Warning Suppression:** Explicitly set to ERROR level in `application.yml` since OSIV is intentionally enabled

**Health Checks:**
- Spring Boot Actuator health endpoint (`/actuator/health`)
  - Enabled in baseline `application.yml` under `management.endpoints.web.exposure.include: health`
  - Docker healthcheck: `curl -f http://localhost:8080/actuator/health || exit 1`
  - Kubernetes-ready; detects database and migration status

## CI/CD & Deployment

**Hosting:**
- Docker container (multi-stage build)
- Base image: Eclipse Temurin 25-jre-noble (pinned to `-noble` Ubuntu variant for Playwright compatibility)
- Non-root user: `ctc` (uid/gid reserved)
- Exposed port: 8080

**CI Pipeline:**
- **Service:** GitHub Actions
- **Workflows:**
  - `ci.yml` - Main CI: checkout, setup JDK 25, cache Maven dependencies, install Playwright browsers, build, unit tests, integration tests, E2E tests (via `-Pe2e`), JaCoCo coverage report comment on PRs
  - `codeql.yml` - CodeQL SAST: Checkout, JDK setup, CodeQL init (java-kotlin, security-extended), compile, analyze, alert gate on new HIGH/CRITICAL findings (PR jobs only; weekly cron is detection-only)
  - `mariadb-migration-smoke.yml` - Testcontainers smoke test: Validates Flyway migration round-trip parity on live MariaDB:11
  - `release.yml` - Semantic versioning: Auto-detect version bump, create GitHub release, build Docker image, push to registry
  - `deploy-site.yml` - GitHub Pages: Deploy `docs/site/` on push to master

**Build Process:**
- Maven (`./mvnw`): `compile` → `test` (Surefire) → `integration-test` (Failsafe) → `verify` (JaCoCo check, SpotBugs check)
- JaCoCo coverage gate: Minimum 82% line coverage (blocks release on failure)
- SpotBugs gate: Medium+ severity findings block build (checked by `spotbugs-maven-plugin` in verify phase)
- Dockerfile multi-stage: Dependency cache layer, source compile layer, runtime stage with Playwright Chromium pre-installed

**Test Execution:**
- **Standard (unit + integration):** `./mvnw verify` (Surefire + Failsafe, tag-based routing)
- **With E2E:** `./mvnw verify -Pe2e` (adds Playwright E2E tests via Failsafe e2e-it execution)
- **CI invocation:** `./mvnw verify --no-transfer-progress` + separate `./mvnw verify -Pe2e --no-transfer-progress` (both run on every CI job)

**Docker Image Build:**
- **Local development:** `docker compose build` (builds from `Dockerfile`)
- **Local run:** `docker compose up --build -d` (builds + runs full stack: MariaDB + app)
- **CI validation:** `.github/workflows/ci.yml` docker-build job exercises `docker build .` to validate Dockerfile multi-stage and Playwright Chromium install step

**Deployment Targets:**
- Development: `docker compose up`
- Production: External Kubernetes cluster or Docker host (configured via environment variables and volumes)
- GitHub Pages: Static site from `docs/site/` (auto-deployed on master push)

## Environment Configuration

**Required env vars (all profiles):**
- `SPRING_PROFILES_ACTIVE` - Active Spring profiles (`dev`, `local`, `docker`, `prod`)

**Required for prod profile:**
- `DATABASE_URL` - JDBC connection string (must include `?rewriteBatchedStatements=true`)
- `DATABASE_USERNAME` - MariaDB username
- `DATABASE_PASSWORD` - MariaDB password
- `GOOGLE_CALENDAR_ID` - Google Calendar ID for race events

**Optional/defaults:**
- `GOOGLE_CALENDAR_ID` - Empty string if not set (calendar integration disabled)
- `google.sheets.credentials-path` - Default: `google-credentials.json` (can be overridden in config)

**Docker Compose environment (docker profile):**
- `SPRING_SECURITY_USER_NAME=admin`
- `SPRING_SECURITY_USER_PASSWORD=ctc-admin` (dev/test value; externalize for production)
- `MARIADB_DATABASE=ctcdb`
- `MARIADB_USER=ctc`
- `MARIADB_PASSWORD=ctc`
- `MARIADB_ROOT_PASSWORD=root`

**Secrets location:**
- Development: `.env`, `.env.dev` files (committed or local-only)
- Production: Environment variables passed by orchestration platform (Kubernetes secrets, Docker secrets, ECS task definition, etc.)
- Google credentials: `google-credentials.json` file (checked in, safe: service account for CTC organization only)

## Webhooks & Callbacks

**Incoming:**
- None (application is data consumer, not webhook receiver)

**Outgoing:**
- None implemented (no event webhooks to external services)

---

*Integration audit: 2026-05-18*
