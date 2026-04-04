# External Integrations

**Analysis Date:** 2026-04-04

## APIs & External Services

### Google Sheets API v4

**Purpose:** Import race results from Google Spreadsheets. League organizers maintain scorecards in Google Sheets; the app reads cell ranges for CSV-style data import.

**Library/API:**
- `com.google.apis:google-api-services-sheets:v4-rev20250106-2.0.0`
- `com.google.api-client:google-api-client:2.9.0`
- `com.google.auth:google-auth-library-oauth2-http:1.43.0`

**Implementation:** `src/main/java/org/ctc/dataimport/GoogleSheetsService.java`
- Lazy-initialized `Sheets` client (synchronized singleton pattern)
- Read-only scope: `SheetsScopes.SPREADSHEETS_READONLY`
- `readRange(spreadsheetId, range)` - Reads A1-notation cell ranges
- `extractSpreadsheetId(url)` - Parses spreadsheet ID from full URL or validates bare ID
- `isAvailable()` - Checks credentials file existence (graceful degradation)

**Configuration:**
- `google.sheets.credentials-path` (default: `google-credentials.json`) - Path to service account JSON file
- Auth: Google service account credentials loaded from filesystem via `FileInputStream`

**Error Handling:**
- `@PostConstruct` logs availability status at startup
- `isAvailable()` check enables graceful degradation when credentials absent
- `IllegalStateException` thrown when called without configured credentials
- `IOException` propagated from Google API calls
- `IllegalArgumentException` for invalid URL/ID format

**Consumers:**
- `src/main/java/org/ctc/dataimport/CsvImportService.java` - Race result import logic
- `src/main/java/org/ctc/dataimport/CsvImportController.java` - Import UI controller

---

### Google Calendar API v3

**Purpose:** Create and update calendar events for matchdays. Events are synced to a shared Google Calendar for race scheduling.

**Library/API:**
- `com.google.apis:google-api-services-calendar:v3-rev20250115-2.0.0`

**Implementation:** `src/main/java/org/ctc/dataimport/GoogleCalendarService.java`
- Lazy-initialized `Calendar` client (synchronized singleton pattern)
- Write scope: `CalendarScopes.CALENDAR_EVENTS`
- `createEvent(title, startTime, durationMinutes)` - Creates calendar event, returns event ID
- `updateEvent(eventId, title, startTime, durationMinutes)` - Updates existing event
- Timezone: `Europe/London` (hardcoded constant)
- Reuses same credentials file as Google Sheets

**Configuration:**
- `google.sheets.credentials-path` - Shared credentials path (reuses Sheets config key)
- `google.calendar.id` (env var: `GOOGLE_CALENDAR_ID`) - Target calendar ID
- Both credentials file AND calendar ID must be present for `isAvailable()` to return true

**Error Handling:**
- Same graceful degradation pattern as Sheets (`isAvailable()` + `@PostConstruct` logging)
- `IllegalStateException` when called without configuration
- `IOException` propagated from API calls

---

### Gran Turismo 7 Website (Web Scraping)

**Purpose:** Scrape car and track data from the official GT7 website for the racing league's car/track pool management.

**Library/API:**
- `org.jsoup:jsoup:1.22.1` - HTML parsing and HTTP fetching

**Implementation:** `src/main/java/org/ctc/gt7sync/Gt7ScraperService.java`
- Scrapes `https://www.gran-turismo.com/gb/gt7/carlist/` and `https://www.gran-turismo.com/gb/gt7/tracklist/`
- Multi-step scraping process:
  1. Fetch HTML page to find JavaScript bundle URLs
  2. Fetch index JS to find data chunk filenames
  3. Fetch and parse data chunks for structured car/track data
- Cars: Parses JS chunks for car IDs, names, manufacturers (with tuner/manufacturer lookup table)
- Tracks: Parses JS chunks for track IDs, names, countries, base IDs
- Car images: Constructed from known URL pattern (`CAR_IMAGE_BASE + gt7Id + ".png"`)
- Track images: Optional parallel resolution via `CompletableFuture` (~41 HTTP requests)
- No authentication required (public website)

**Data Flow (two-phase pattern):**
1. Scrape + parse: `Gt7ScraperService.scrapeCars()` / `scrapeTracks()`
2. Preview: `Gt7SyncService` compares scraped data with DB
3. Confirm + execute: `Gt7SyncService` syncs to database
4. Admin UI: `Gt7SyncController` provides preview + confirm interface

**Configuration:**
- No configuration needed (hardcoded base URLs to gran-turismo.com)

**Error Handling:**
- `IllegalStateException` when expected JavaScript patterns not found (site structure changed)
- `IOException` from Jsoup HTTP connections
- Track image resolution: Individual failures logged as warnings, non-blocking

**Related Files:**
- `src/main/java/org/ctc/gt7sync/Gt7SyncService.java` - Sync logic (preview + execute)
- `src/main/java/org/ctc/gt7sync/Gt7SyncController.java` - Admin UI controller
- `src/main/java/org/ctc/gt7sync/Gt7SyncPreview.java` - Preview DTO

---

### Playwright (Chromium)

**Purpose:** Generate PNG graphics from HTML templates for league media assets (team cards, lineup graphics, result graphics, overlays, etc.). Also used for E2E browser testing.

**Library/API:**
- `com.microsoft.playwright:playwright:1.58.0` (compile scope - runtime dependency)
- Chromium browser (must be installed separately)

**Implementation:** `src/main/java/org/ctc/admin/service/AbstractGraphicService.java`
- Base class for all graphic generation services
- Pattern: Thymeleaf renders HTML template -> write to temp file -> Playwright opens in headless Chromium -> screenshot to PNG -> delete temp file
- `renderScreenshot(html, outputFile)` - Standard screenshot (1920x1080 viewport)
- `renderScreenshotTransparent(html, outputFile)` - Transparent background (`setOmitBackground(true)`)
- Each invocation creates a new Playwright/Browser/Page instance (no connection pooling)
- Resources (fonts, logos) embedded as base64 data URIs in HTML via `encodeClasspathResource()`

**Additional Base Classes:**
- `src/main/java/org/ctc/admin/service/AbstractMatchdayGraphicService.java` - Shared matchday graphic logic
- `src/main/java/org/ctc/admin/service/AbstractPlayoffRoundGraphicService.java` - Shared playoff graphic logic

**Concrete Graphic Services (all in `src/main/java/org/ctc/admin/service/`):**
- `TeamCardService.java` - Team card PNG generation
- `LineupGraphicService.java` - Race lineup graphics
- `ResultsGraphicService.java` - Race results graphics
- `MatchResultsGraphicService.java` - Match results graphics
- `SettingsGraphicService.java` - Settings/config graphics
- `OverlayGraphicService.java` - Stream overlay graphics
- `PowerRankingsGraphicService.java` - Power ranking graphics
- `PlayoffRoundOverviewGraphicService.java` - Playoff overview graphics
- `PlayoffRoundScheduleGraphicService.java` - Playoff schedule graphics
- `PlayoffRoundResultsGraphicService.java` - Playoff results graphics

**Configuration:**
- `PLAYWRIGHT_BROWSERS_PATH=/app/.playwright` (Docker env var)
- Dev install: `./mvnw exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"`
- Docker: Chromium installed during image build + system dependencies (libnss3, libgbm1, libasound2t64, etc.)

**Error Handling:**
- Playwright/Browser/Page managed via try-with-resources (AutoCloseable)
- Temp files cleaned up in `finally` block
- All graphic services excluded from JaCoCo coverage (Playwright dependency makes them hard to unit test)

---

## CSV Import Pipeline

**Purpose:** Import race results from CSV files or Google Sheets into the database with fuzzy driver name matching.

**Data Flow:** Google Sheets URL (or CSV file upload) -> Scorecard Parser -> Fuzzy Driver Matching -> Database

**Implementation:**
- Controller: `src/main/java/org/ctc/dataimport/CsvImportController.java`
- Service: `src/main/java/org/ctc/dataimport/CsvImportService.java`
- Parser: `src/main/java/org/ctc/dataimport/ScorecardParser.java`
- Driver matching: `src/main/java/org/ctc/dataimport/DriverMatchingService.java`
  - Uses Apache Commons Text (`commons-text:1.15.0`) for fuzzy string matching

**Two-Phase Pattern:**
1. Parse + preview: Read data, match drivers, show preview to admin
2. Confirm + execute: Admin confirms, data written to database

---

## Static Site Generation

**Purpose:** Generate public-facing HTML pages from domain data for GitHub Pages deployment.

**Implementation:**
- Service: `src/main/java/org/ctc/sitegen/SiteGeneratorService.java`
- Controller: `src/main/java/org/ctc/sitegen/SiteGeneratorController.java`
- View model: `src/main/java/org/ctc/sitegen/model/RaceView.java`
- Uses Thymeleaf templates to render static HTML files
- Output directory: `ctc.site.output-dir` (default: `docs/site`, committed to repo)
- Deployed via GitHub Pages workflow (`.github/workflows/deploy-site.yml`)

---

## Data Storage

**Databases:**
- MariaDB (prod/docker/local profiles)
  - Connection: `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` (prod env vars)
  - Driver: `org.mariadb.jdbc.Driver`
  - Client: Spring Data JPA / Hibernate
- H2 In-Memory (dev/test profiles)
  - Connection: `jdbc:h2:mem:ctcdb`
  - Console: `/h2-console` (dev only)

**File Storage:**
- Local filesystem only (no cloud storage)
- Upload directory: `app.upload-dir` property
  - Dev: `data/dev/uploads`
  - Local: `data/local/uploads`
  - Docker: `/app/uploads`
- Stored files: Team logos, car images, track images, generated PNG graphics
- Team cards path pattern: `{upload-dir}/team-cards/{seasonId}/{teamShortName}.png`
- Max upload size: 10MB

**Static Site Output:**
- Generated HTML files in `ctc.site.output-dir`
  - Dev: `target/site`
  - Docker: `/app/ctc-site-output`
  - Default: `docs/site` (committed to repo, deployed via GitHub Pages)

**Caching:**
- None (no Redis, Caffeine, or application-level cache)

## Authentication & Identity

**Auth Provider:**
- Spring Security with HTTP Basic Auth
- Active only on `prod` and `docker` profiles (`@Profile({"prod", "docker"})`)
- Implementation: `src/main/java/org/ctc/admin/SecurityConfig.java`
- All requests require authentication except `/actuator/health`
- CSRF disabled (internal admin tool)
- Dev/local profiles: No authentication (open access)
- Google API auth uses service account credentials (not user OAuth)

## Monitoring & Observability

**Health Check:**
- Spring Boot Actuator: `/actuator/health`
- Only `health` endpoint exposed
- Docker healthcheck configured in `Dockerfile`

**Error Tracking:**
- None (no Sentry, Datadog, etc.)

**Logs:**
- SLF4J via Lombok `@Slf4j` (Logback backend from Spring Boot)
- Parameterized logging: `log.info("message {}", var)` (never string concatenation)
- Dev: `org.ctc` at DEBUG level
- Docker: Logs directory at `/app/logs`

## Environment Configuration

**Required env vars (production):**
- `DATABASE_URL` - JDBC connection string for MariaDB
- `DATABASE_USERNAME` - Database user
- `DATABASE_PASSWORD` - Database password

**Optional env vars:**
- `GOOGLE_CALENDAR_ID` - Google Calendar ID for matchday event sync

**Credentials files:**
- `google-credentials.json` - Google service account JSON key file (path configurable)
- `.env` files present for Docker Compose configuration

## Webhooks & Callbacks

**Incoming:**
- None

**Outgoing:**
- None (all integrations are pull-based or direct API writes)

---

*Integration audit: 2026-04-04*
