<!-- refreshed: 2026-05-18 -->
# Architecture

**Analysis Date:** 2026-05-18

## System Overview

CTC Manager is a Spring Boot 4 admin application for managing Gran Turismo racing league competitions. It uses server-side rendering (Thymeleaf), H2/MariaDB persistence, and Playwright for graphic generation. The architecture follows strict layering: controllers delegate to services, services own business logic and repository access, DTOs protect against mass assignment, and OSIV is deliberately enabled for lazy-load support in templates.

```text
┌─────────────────────────────────────────────────────────────┐
│                    HTTP / Web Layer                          │
│  Controllers (`admin.controller.*`) + Thymeleaf Templates   │
│         `src/main/resources/templates/admin/`               │
└──────────────────┬──────────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────────┐
│          Form DTOs & Data Transfer Objects                  │
│  `admin.dto.*` (Form binding, display models, graphics)     │
│      Protects against mass assignment via @Valid            │
└──────────────────┬──────────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────────┐
│               Service Layer (Business Logic)                 │
│  ┌─────────────────┬──────────────────┬───────────────────┐ │
│  │ domain.service  │ admin.service    │ dataimport.*      │ │
│  │ (Core domain)   │ (Graphics/UI)    │ (CSV/Google/GT7)  │ │
│  └─────────────────┴──────────────────┴───────────────────┘ │
│       Scoring, Matchday Gen, Standings, RaceLineup, etc.     │
└──────────────────┬──────────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────────┐
│           Domain Model + Repository Layer                    │
│  JPA Entities (`domain.model.*`) + Spring Data Repositories  │
│  Transactional consistency; Flyway migrations (V1__...sql)   │
└──────────────────┬──────────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────────┐
│         Persistent Storage & External Services              │
│  MariaDB/H2 | Google Sheets | Google Calendar | GT7 Scraper │
│  File uploads: `data/dev/uploads/`; Site output: `docs/site/`
└─────────────────────────────────────────────────────────────┘
```

## Component Responsibilities

| Component | Responsibility | File |
|-----------|----------------|------|
| **SeasonController** | HTTP entry point for season CRUD, phase tabs, matchday nav | `src/main/java/org/ctc/admin/controller/SeasonController.java` |
| **RaceScoringController** | Race scoring template (points arrays), CRUD | `src/main/java/org/ctc/admin/controller/RaceScoringController.java` |
| **MatchScoringController** | Match scoring template (W/D/L points), CRUD | `src/main/java/org/ctc/admin/controller/MatchScoringController.java` |
| **RaceController** | Race form, results entry, graphic generation triggers | `src/main/java/org/ctc/admin/controller/RaceController.java` |
| **RaceLineupController** | Driver-team assignment per race, edit | `src/main/java/org/ctc/admin/controller/RaceLineupController.java` |
| **StandingsController** | Playoff bracket, final standings view | `src/main/java/org/ctc/admin/controller/StandingsController.java` |
| **TeamCardController** | Team card (graphic), generation/preview | `src/main/java/org/ctc/admin/controller/TeamCardController.java` |
| **DriverSheetImportController** | Google Sheets driver import flow | `src/main/java/org/ctc/admin/controller/DriverSheetImportController.java` |
| **CsvImportController** | Race result CSV import + multi-race leg support | `src/main/java/org/ctc/admin/controller/CsvImportController.java` |
| **Gt7SyncController** | GT7 car/track scrape + sync | `src/main/java/org/ctc/gt7sync/Gt7SyncController.java` |
| **SeasonManagementService** | Season CRUD, phase/team queries, data prep | `src/main/java/org/ctc/domain/service/SeasonManagementService.java` |
| **RaceScoringService** | RaceScoring CRUD + validation (monotonic points) | `src/main/java/org/ctc/domain/service/RaceScoringService.java` |
| **MatchScoringService** | MatchScoring CRUD | `src/main/java/org/ctc/domain/service/MatchScoringService.java` |
| **RaceService** | Race persistence, result entry, score aggregation kickoff | `src/main/java/org/ctc/domain/service/RaceService.java` |
| **ScoringService** | Calculate points (race/quali/FL), aggregate match scores from legs | `src/main/java/org/ctc/domain/service/ScoringService.java` |
| **RaceLineupService** | RaceLineup CRUD (driver-team binding per race) | `src/main/java/org/ctc/domain/service/RaceLineupService.java` |
| **CsvImportService** | CSV parsing, driver matching, multi-race import, RaceLineup creation | `src/main/java/org/ctc/dataimport/CsvImportService.java` |
| **DriverSheetImportService** | Google Sheets fetch, driver name → driver entity resolution | `src/main/java/org/ctc/dataimport/DriverSheetImportService.java` |
| **Gt7SyncService** | Scrape GT7 cars/tracks, persist to DB | `src/main/java/org/ctc/gt7sync/Gt7SyncService.java` |
| **AbstractGraphicService** | Playwright-based HTML→PNG rendering (base class) | `src/main/java/org/ctc/admin/service/AbstractGraphicService.java` |
| **TeamCardService** | Team card image generation/storage | `src/main/java/org/ctc/admin/service/TeamCardService.java` |
| **MatchdayOverviewGraphicService** | Matchday schedule/results/overview PNG | `src/main/java/org/ctc/admin/service/MatchdayOverviewGraphicService.java` |
| **BaseEntity** | Audit mixin (`createdAt`, `updatedAt`) via Spring JPA Auditing | `src/main/java/org/ctc/domain/model/BaseEntity.java` |
| **Race**, **Match**, **RaceResult** | Race outcome modeling; home/away score aggregation | `src/main/java/org/ctc/domain/model/Race.java` |
| **RaceLineup** | Driver-team assignment per race (Source of Truth for scoring) | `src/main/java/org/ctc/domain/model/RaceLineup.java` |
| **GlobalExceptionHandler** | Centralized exception → error template routing | `src/main/java/org/ctc/admin/controller/GlobalExceptionHandler.java` |

## Pattern Overview

**Overall:** Layered N-tier (DAO/Repository → Service → Controller).

**Key Characteristics:**
- **Thin Controllers:** HTTP binding only. All business logic in services (`domain.service` and `admin.service`).
- **DTOs for Form Input:** All POST requests use Form DTOs (`admin.dto.*Form`) with `@Valid` + `BindingResult`. Never bind entities directly (`@ModelAttribute`).
- **OSIV Enabled:** `spring.jpa.open-in-view=true` intentionally active. Thymeleaf templates can access lazy-loaded fields without extra service calls.
- **Flash Attributes:** Success/error messages via `RedirectAttributes.addFlashAttribute("successMessage"|"errorMessage", msg)`.
- **Transactional Consistency:** Domain services use `@Transactional`; multiple writes within one transaction stay consistent (e.g., race results → score aggregation).
- **Dual Scoring Pipelines:** Race results (RaceResult points) feed MatchScore aggregation; MatchScoring (W/D/L) is separate config.
- **RaceLineup Source of Truth:** `isDriverInTeam()` checks RaceLineup first, fallback to SeasonDriver for legacy data only.

## Layers

**Controller Layer (HTTP):**
- Purpose: Receive requests, delegate to services, fill model attributes, return view names or redirects
- Location: `src/main/java/org/ctc/admin/controller/`
- Contains: `@Controller` classes with `@GetMapping`, `@PostMapping`
- Depends on: `admin.service.*`, `admin.dto.*`, `domain.service.*`, `domain.model.*`
- Used by: HTTP clients (browser, Playwright E2E)

**Form DTO Layer (Data Transfer):**
- Purpose: Bind form inputs with `@Valid`, protect against mass assignment
- Location: `src/main/java/org/ctc/admin/dto/`
- Contains: `*Form` classes (binding input), `*Dto` classes (display), `*GraphicData` (graphics prep)
- Depends on: Jakarta validation annotations
- Used by: Controllers (`@ModelAttribute`), services for data prep

**Domain Service Layer (Business Logic):**
- Purpose: Core domain operations (scoring, standings, season management, matchday generation, CSV import)
- Location: `src/main/java/org/ctc/domain/service/`
- Contains: `SeasonManagementService`, `RaceService`, `ScoringService`, `MatchdayGeneratorService`, `RaceLineupService`, `CsvImportService`, etc.
- Depends on: `domain.repository.*`, `domain.model.*`, `domain.exception.*`
- Used by: Controllers, admin services

**Admin Service Layer (UI/Graphics):**
- Purpose: Generate graphics (Playwright), format data for UI, manage team cards
- Location: `src/main/java/org/ctc/admin/service/`
- Contains: `AbstractGraphicService`, `MatchdayOverviewGraphicService`, `PowerRankingsGraphicService`, `TeamCardService`
- Depends on: `domain.service.*`, `domain.model.*`, Playwright API
- Used by: Controllers (`TeamCardController`, `RaceController`)

**Repository Layer (Data Access):**
- Purpose: JPA entity queries; one repository per entity type
- Location: `src/main/java/org/ctc/domain/repository/`
- Contains: `CarRepository`, `DriverRepository`, `RaceRepository`, `MatchRepository`, `RaceLineupRepository`, etc. (all Spring Data JPA)
- Depends on: JPA, entity models
- Used by: Domain services

**Model Layer (Entities):**
- Purpose: JPA entity definitions; extend `BaseEntity` for audit fields
- Location: `src/main/java/org/ctc/domain/model/`
- Contains: `Season`, `Matchday`, `Race`, `Match`, `RaceResult`, `RaceLineup`, `Team`, `Driver`, `Car`, `Track`, etc.
- Depends on: JPA, Lombok
- Used by: Services, repositories

**Exception Layer:**
- Purpose: Custom exceptions for domain errors
- Location: `src/main/java/org/ctc/domain/exception/`
- Contains: `EntityNotFoundException`, `BusinessRuleException`, `ValidationException`
- Used by: Services, caught by `GlobalExceptionHandler`

## Data Flow

### Primary Request Path (Race Result Entry)

1. **GET /admin/races/{id}** → `RaceController.detail()` (`src/main/java/org/ctc/admin/controller/RaceController.java`)
   - Calls `RaceService.getRaceDetailData()` (query race, check lineup graphics, etc.)
   - Renders `admin/race-detail.html` with form

2. **POST /admin/races/{id}/results/save** → `RaceController.saveResults()` (`src/main/java/org/ctc/admin/controller/RaceController.java`)
   - Binds `@ModelAttribute("raceScoringForm") RaceResultForm` (DTO protection)
   - Calls `RaceService.saveResults(raceId, resultsList)`

3. **RaceService.saveResults()** (`src/main/java/org/ctc/domain/service/RaceService.java:241`)
   - Loop: For each result, create `RaceResult` entity
   - Call `ScoringService.calculatePoints(result, raceScoring)` — sets pointsRace, pointsQuali, pointsFl, pointsTotal
   - Save race (cascade to results)
   - **Call `ScoringService.aggregateMatchScores(race)`** — critical: updates `Match.homeScore` / `Match.awayScore` by summing leg results
   - Return success message

4. **ScoringService.aggregateMatchScores()** (`src/main/java/org/ctc/domain/service/ScoringService.java:56`)
   - Query `raceRepository.findByMatchId(matchId)` to get all legs for this match
   - For each leg, sum team points using `isDriverInTeam()` (RaceLineup check)
   - Write aggregated scores back to `Match`
   - Log aggregation event

5. **Controller redirects** → `RedirectAttributes.addFlashAttribute("successMessage", msg)`

**Critical:** `aggregateMatchScores()` is always called after race results are saved — this is the synchronization point between race-level scoring and match-level aggregation.

### Secondary Flow: CSV Import (Multi-Race, Multi-Leg)

1. **GET /admin/import** → `CsvImportController.showImportForm()` (`src/main/java/org/ctc/admin/controller/CsvImportController.java`)
   - Renders form with season/matchday dropdowns

2. **POST /admin/import/preview** → `CsvImportController.preview()` with CSV file
   - Calls `CsvImportService.parseAndPreview(csvStream, metadata)` → `ImportPreview` (DTO with errors/rows)
   - Renders `admin/import-preview.html` showing driver matches and warnings

3. **POST /admin/import/execute** → `CsvImportController.execute()` with confirmed matches
   - Calls `CsvImportService.executeImport(preview, confirmedMatches, createNewDrivers, overwriteExisting)`

4. **CsvImportService.executeImport()** → **CsvImportService.executeMultiRaceImport()** (`src/main/java/org/ctc/dataimport/CsvImportService.java:120`)
   - For each ImportPreview (one per CSV/race/leg):
     - Create or find Match for (home team, away team) pair
     - Create Race, add to Match
     - Create RaceResult entries with driver PSN → entity resolution
     - **Create RaceLineup** entries (CSV row team → team lookup)
     - Call `ScoringService.calculatePoints()` for each result
     - **Call `ScoringService.aggregateMatchScores(race)`** after race results saved
   - Return cumulative result (total races, drivers created, errors)

**Key:** Multiple CSV previews (legs) processed in one transaction; one Match with multiple Races; RaceLineup entries explicitly created to bind drivers to teams per race.

### Tertiary Flow: Google Sheets Driver Import

1. **GET /admin/driver-import** → `DriverSheetImportController.showForm()` (`src/main/java/org/ctc/admin/controller/DriverSheetImportController.java`)

2. **POST /admin/driver-import/fetch** → `DriverSheetImportController.fetch()`
   - Calls `DriverSheetImportService.fetchSheet(sheetUrl)`
   - Calls `GoogleSheetsService.readSheet(spreadsheetId, range)` (Google API)
   - Parse names → `DriverSheetImportService.resolveDrivers()` (fuzzy match + user confirmation)
   - Render preview with matches

3. **POST /admin/driver-import/save** → `DriverSheetImportController.save()`
   - Calls `DriverSheetImportService.importDrivers()` (create new Driver entities)
   - Return success count

### Quaternary Flow: GT7 Car/Track Sync

1. **GET /admin/gt7-sync** → `Gt7SyncController.showForm()` (`src/main/java/org/ctc/gt7sync/Gt7SyncController.java`)

2. **POST /admin/gt7-sync/preview** → `Gt7SyncController.preview()`
   - Calls `Gt7SyncService.preview()` → `Gt7ScraperService.scrapeGt7()`
   - Fetches GT7 official data, compares with DB
   - Render diff view

3. **POST /admin/gt7-sync/execute** → `Gt7SyncController.execute()`
   - Calls `Gt7SyncService.sync()` — creates/updates Car and Track entities

## Key Abstractions

**RaceLineup (Driver-Team Binding):**
- Purpose: Records which team a driver belongs to in a specific race
- Examples: `src/main/java/org/ctc/domain/model/RaceLineup.java`
- Pattern: One-to-one mapping (race, driver) → team
- Usage: CSV import creates lineups; `ScoringService.isDriverInTeam()` queries for score aggregation
- Source of Truth: Always checked first; `SeasonDriver` is fallback for legacy data

**Match & PlayoffMatchup (Score Aggregation):**
- Purpose: Hold aggregated home/away scores across race legs
- Examples: `src/main/java/org/ctc/domain/model/Match.java`, `src/main/java/org/ctc/domain/model/PlayoffMatchup.java`
- Pattern: One Match = multiple Race legs; `aggregateMatchScores()` sums race results into match scores
- Usage: Standings, bracket visualization, final results

**Matchday & SeasonPhase (Tournament Structure):**
- Purpose: Organize races into phases (REGULAR, PLAYOFF) and matchdays (MD1, MD2, etc.)
- Examples: `src/main/java/org/ctc/domain/model/Matchday.java`, `src/main/java/org/ctc/domain/model/SeasonPhase.java`
- Pattern: Season → Phase → Matchday → Match → Race → RaceResult
- Scoring Config: Phase holds RaceScoring (points arrays) and MatchScoring (W/D/L)

**Graphic Services (Playwright):**
- Purpose: Generate PNG graphics for team cards, matchday overviews, standings
- Examples: `AbstractGraphicService`, `MatchdayOverviewGraphicService`, `TeamCardService`
- Pattern: Render Thymeleaf HTML → Page.screenshot() → PNG file in `data/{profile}/uploads/`
- Usage: Triggered by controller endpoints; cached/retrieved via `RaceAttachment`

## Entry Points

**Web Application:**
- Location: `src/main/java/org/ctc/CtcManagerApplication.java`
- Triggers: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`
- Responsibilities: Spring Boot autoconfiguration, enable JPA auditing, start embedded Tomcat

**Controller Entry Points (HTTP):**
- `/admin/seasons` — Season list/form
- `/admin/races/{id}` — Race detail, results entry
- `/admin/race-scorings` — RaceScoring config
- `/admin/match-scorings` — MatchScoring config
- `/admin/driver-import` — Google Sheets import
- `/admin/import` — CSV race import
- `/admin/gt7-sync` — GT7 car/track sync
- `/admin/teams`, `/admin/drivers`, `/admin/cars`, `/admin/tracks` — Master data CRUD

**Background Tasks:**
- None (no scheduled jobs); all triggered via HTTP request
- Exception: E2E tests use `TestDataService.seedData()` to populate demo data

## Architectural Constraints

- **Threading:** Single-threaded event loop (Spring Boot Tomcat, default pool size)
- **Global State:** None (stateless HTTP handlers)
- **Session State:** Spring Security session for auth (prod/docker only)
- **Lazy Loading:** OSIV enabled; controllers can safely access lazy relations in templates without triggering lazy-init errors
- **Transaction Scope:** `@Transactional` methods; one HTTP request = one transaction (typically)
- **Circular Imports:** None detected
- **Circular Dependencies:** Controller → Service → Repository → Model; no backward references
- **Immutable Flyway V1:** `src/main/resources/db/migration/V1__*.sql` must never change after release; V2+ migrations only

## Anti-Patterns

### Missing aggregateMatchScores() Call

**What happens:** Race results are saved but match-level scores are not updated. Standings show stale data.

**Why it's wrong:** RaceResult points are calculated and persisted, but Match.homeScore/awayScore remain unchanged. Controllers/templates then read stale match scores.

**Do this instead:** Always call `ScoringService.aggregateMatchScores(race)` immediately after saving RaceResults. See `RaceService.saveResults()` line 259 (`src/main/java/org/ctc/domain/service/RaceService.java`).

### Not Using RaceLineup for Team Assignment

**What happens:** `isDriverInTeam()` fallback to SeasonDriver; multi-leg CSV imports assign drivers to wrong teams.

**Why it's wrong:** CSV import can place same driver on different teams across legs. SeasonDriver is season-wide; RaceLineup is race-specific and accurate.

**Do this instead:** Always create RaceLineup entries during CSV import. `CsvImportService.executeMultiRaceImport()` does this at `src/main/java/org/ctc/dataimport/CsvImportService.java:120`. Never skip RaceLineup creation.

### Binding Form DTOs Directly to Entities

**What happens:** Controller uses `@ModelAttribute Season season` (entity) instead of `@ModelAttribute SeasonForm form` (DTO).

**Why it's wrong:** Mass assignment: user can modify fields not intended for editing (e.g., createdAt, foreign key IDs).

**Do this instead:** Always use Form DTOs (`admin.dto.*Form`) for POST requests. Copy validated form fields to entity in service layer. Example: `SeasonController.save()` uses `SeasonForm` (see `src/main/java/org/ctc/admin/controller/SeasonController.java:94`).

### Complex Logic in Thymeleaf Templates

**What happens:** Template uses SpEL expressions, collection projections, or conditional formatting.

**Why it's wrong:** Reduces testability; logic is not unit-tested; hard to refactor.

**Do this instead:** Prepare all data in service layer and pass simple objects/lists to template. Example: Use `StandingsViewService` to pre-calculate standings, pass to template as simple list of rows (see `src/main/java/org/ctc/domain/service/StandingsViewService.java`).

### Inline Styles on Buttons

**What happens:** Template contains `<button style="color: red; padding: 10px;">Save</button>`.

**Why it's wrong:** Styling logic scattered across codebase; hard to maintain; doesn't scale.

**Do this instead:** Use CSS classes from `admin.css`: `<button class="btn btn-primary btn-sm">Save</button>`. When JavaScript sets className, include CSS classes: `element.className = 'btn btn-primary btn-sm'` (see CLAUDE.md "No Inline Styles on Buttons").

## Error Handling

**Strategy:** Centralized exception catching in `GlobalExceptionHandler` (`src/main/java/org/ctc/admin/controller/GlobalExceptionHandler.java`).

**Patterns:**
- `EntityNotFoundException` → 404 Not Found + error template
- `ValidationException` → 400 Bad Request
- `BusinessRuleException` → 409 Conflict (e.g., duplicate scoring name, ref integrity)
- `Exception` (uncaught) → 500 Internal Server Error
- Development mode (`dev` profile) shows exception type and stack trace; production hides details

**Flash Attributes:** Services throw exceptions; controllers catch and convert to flash messages (success/error) for user-friendly redirect. Example: `RaceScoringController.save()` catches `BusinessRuleException` and adds `"errorMessage"` flash attribute (see `src/main/java/org/ctc/admin/controller/RaceScoringController.java:51`).

## Cross-Cutting Concerns

**Logging:** All services use `@Slf4j` with parameterized `log.info("msg {}", value)` format. Key events: entity CRUD, score calculations, imports. Development mode includes debug logs for calculations.

**Validation:** Two-tier approach:
- Form-level: Jakarta `@NotBlank`, `@Valid` + `BindingResult` in controller
- Business-level: Service methods throw `BusinessRuleException` for domain rules (e.g., monotonic race points)

**Authentication:** Spring Security configured in `SecurityConfig.java` and `OpenSecurityConfig.java` (`src/main/java/org/ctc/admin/`). Auth enabled for `prod`/`docker` profiles only; `dev`/`local` are open. No role-based access control (RBAC) in current implementation.

**Transactional Consistency:** All state-mutating service methods are `@Transactional`. Multiple writes (race + results + lineup + aggregation) complete atomically within one transaction. Rollback on any exception.

---

*Architecture analysis: 2026-05-18*
