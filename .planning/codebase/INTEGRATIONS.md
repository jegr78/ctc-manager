# External Integrations

**Analysis Date:** 2026-04-03

## APIs & External Services

**Google Sheets API v4:**
- Purpose: Read race result data from Google Spreadsheets for CSV import
- SDK: `google-api-services-sheets` v4-rev20250106-2.0.0
- Service: `src/main/java/org/ctc/dataimport/GoogleSheetsService.java`
- Auth: Google Service Account credentials file, path configured via `google.sheets.credentials-path` (default: `google-credentials.json`)
- Scope: `SheetsScopes.SPREADSHEETS_READONLY` (read-only access)
- Graceful degradation: Service checks `isAvailable()` at startup; logs info message if no credentials found, does not fail
- Client initialized lazily on first use (synchronized singleton)

**Google Calendar API v3:**
- Purpose: Create and update calendar events for race schedules
- SDK: `google-api-services-calendar` v3-rev20250115-2.0.0
- Service: `src/main/java/org/ctc/dataimport/GoogleCalendarService.java`
- Auth: Same credentials file as Sheets (`google.sheets.credentials-path`)
- Calendar ID: env var `GOOGLE_CALENDAR_ID` (config key: `google.calendar.id`)
- Scope: `CalendarScopes.CALENDAR_EVENTS`
- Operations: `createEvent(title, startTime, durationMinutes)`, `updateEvent(eventId, title, startTime, durationMinutes)`
- Timezone: Hardcoded to `Europe/London`
- Graceful degradation: Same `isAvailable()` pattern as Sheets

**GT7 Website (gran-turismo.com):**
- Purpose: Scrape car and track data from Gran Turismo 7 official website
- Client: Jsoup 1.22.1 (HTTP + HTML parsing)
- Service: `src/main/java/org/ctc/gt7sync/Gt7ScraperService.java`
- Sync orchestration: `src/main/java/org/ctc/gt7sync/Gt7SyncService.java`
- Admin UI: `src/main/java/org/ctc/gt7sync/Gt7SyncController.java`
- URLs scraped:
  - Cars: `https://www.gran-turismo.com/gb/gt7/carlist/`
  - Tracks: `https://www.gran-turismo.com/gb/gt7/tracklist/`
- Approach: Parses JavaScript bundles (not HTML) to extract structured car/track data
- Car images: Constructed from pattern `https://www.gran-turismo.com/common/dist/gt7/carlist/car_thumbnails/{gt7Id}.png`
- Track images: Resolved from per-track JS chunks (parallel fetching via `CompletableFuture`)
- No auth required (public website)

## Data Storage

**Databases:**
- H2 In-Memory (dev/test):
  - URL: `jdbc:h2:mem:ctcdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`
  - Driver: `org.h2.Driver`
  - Console: Available at `/h2-console` in dev profile
- MariaDB 11 (local/docker/prod):
  - Local URL: `jdbc:mariadb://localhost:3306/ctcdb`
  - Docker URL: `jdbc:mariadb://db:3306/ctcdb`
  - Prod URL: env var `DATABASE_URL`
  - Driver: `org.mariadb.jdbc.Driver`
- ORM: Hibernate via Spring Data JPA
- DDL strategy: `validate` (all profiles) - Flyway manages schema
- Schema: Single consolidated migration `src/main/resources/db/migration/V1__initial_schema.sql` (321 lines)

**File Storage:**
- Local filesystem only (no cloud storage)
- Upload directory: Configurable via `app.upload-dir`
  - Dev: `data/dev/uploads`
  - Local: `data/local/uploads`
  - Docker: `/app/uploads` (Docker volume `ctc-uploads`)
- File types stored: Team logos, car images, track images, generated graphics (team cards, lineup graphics, result graphics)
- Max upload size: 10MB
- Generated graphics use Playwright/Chromium to render HTML templates to PNG screenshots

**Static Site Output:**
- Generated HTML files for public-facing site
- Output directory: configurable via `ctc.site.output-dir`
  - Dev: `target/site`
  - Docker: `/app/ctc-site-output` (Docker volume `ctc-site-output`)
  - Default: `docs/site`
- Service: `src/main/java/org/ctc/sitegen/SiteGeneratorService.java`
- Deployed to GitHub Pages via `deploy-site.yml` workflow

**Caching:**
- None (no Redis, Caffeine, or other caching layer)

## Authentication & Identity

**Auth Provider:**
- None - No user authentication implemented
- The application is an admin tool without login/access control
- Google API auth uses service account credentials (not user OAuth)

## Monitoring & Observability

**Health Checks:**
- Spring Boot Actuator health endpoint: `/actuator/health`
- Only `health` endpoint exposed (configured in `application.yml`)
- Docker healthcheck: `curl -f http://localhost:8080/actuator/health || exit 1`

**Error Tracking:**
- None (no Sentry, Datadog, etc.)

**Logs:**
- SLF4J via Lombok `@Slf4j` annotation (Logback backend from Spring Boot)
- Dev profile: `org.ctc` package at `DEBUG` level
- Docker: Logs volume at `/app/logs`
- No structured logging or log aggregation

## CI/CD & Deployment

**Hosting:**
- Docker containers (self-hosted, no cloud PaaS)
- GitHub Pages for static site (`docs/site/`)

**CI Pipeline:**
- GitHub Actions (`.github/workflows/ci.yml`)
- Runs on: `ubuntu-latest`
- Coverage reporting: JaCoCo with PR comments via `madrapps/jacoco-report@v1.7.2`

**Deployment:**
- Docker Compose for both dev and production
- `docker-compose.yml` - Full stack (App + MariaDB)
- `docker-compose.prod.yml` - App only (expects external DB)

## Environment Configuration

**Required env vars (production):**
- `DATABASE_URL` - JDBC connection string for MariaDB
- `DATABASE_USERNAME` - Database user
- `DATABASE_PASSWORD` - Database password
- `SPRING_PROFILES_ACTIVE` - Must be set to `prod` (or `docker`)

**Optional env vars:**
- `GOOGLE_CALENDAR_ID` - Google Calendar ID for race event sync

**Credentials files (not env vars):**
- `google-credentials.json` - Google service account JSON key file (path configurable via `google.sheets.credentials-path`)

**Secrets location:**
- `.env` files for Docker Compose (`.env`, `.env.dev`, `.env.local` exist, `.env.example` shows template)
- Google credentials as JSON file on filesystem

## Webhooks & Callbacks

**Incoming:**
- None

**Outgoing:**
- None

## CSV Import Pipeline

**Data flow:** Google Sheets (or CSV upload) -> Parser -> Fuzzy Driver Matching -> Database
- Controller: `src/main/java/org/ctc/dataimport/CsvImportController.java`
- Service: `src/main/java/org/ctc/dataimport/CsvImportService.java`
- Parser: `src/main/java/org/ctc/dataimport/ScorecardParser.java`
- Driver matching: `src/main/java/org/ctc/dataimport/DriverMatchingService.java` (uses Apache Commons Text for fuzzy string matching)
- Supports both direct CSV file upload and Google Sheets URL/ID input

## Graphic Generation (Playwright)

**Purpose:** Generate PNG images from HTML/CSS templates using headless Chromium
- Base class: `src/main/java/org/ctc/admin/service/AbstractGraphicService.java`
- Services: `TeamCardService`, `LineupGraphicService`, `ResultsGraphicService`, `SettingsGraphicService`, `OverlayGraphicService`, `MatchResultsGraphicService`, `PowerRankingsGraphicService`
- Approach: Thymeleaf renders HTML template -> written to temp file -> Playwright opens in headless Chromium -> screenshot to PNG
- Viewport: 1920x1080
- Supports transparent backgrounds (`setOmitBackground(true)`)
- Resources (fonts, logos) embedded as base64 data URIs in HTML
- Requires Chromium installation: `PLAYWRIGHT_BROWSERS_PATH=/app/.playwright` in Docker

---

*Integration audit: 2026-04-03*
