# Concerns & Technical Debt
> Generated: 2026-04-03 | Focus: concerns

## No Authentication / Authorization

### No Security Layer
- **Location:** Entire application (no `spring-boot-starter-security` in `pom.xml`)
- **Severity:** High
- **Description:** The application has no authentication or authorization. All admin endpoints (`/admin/**`) are publicly accessible. There is no CSRF protection beyond Spring's default (which requires Spring Security to be active). Any user with network access can create, modify, or delete all data. Acceptable only if deployed on a private network or behind an external auth proxy, but this is not enforced or documented in config.

## Controllers with Direct Repository Access

### Violation of "Controllers duenn halten" Principle
- **Location:** `src/main/java/org/ctc/admin/controller/DriverController.java:93-103` (assign), `src/main/java/org/ctc/admin/controller/DriverController.java:114-115` (delete), `src/main/java/org/ctc/admin/controller/TrackController.java:61-67` (image upload), `src/main/java/org/ctc/admin/controller/TrackController.java:83-88` (save), `src/main/java/org/ctc/admin/controller/TrackController.java:103-112` (delete), `src/main/java/org/ctc/admin/controller/CarController.java:57-73` (image upload), `src/main/java/org/ctc/admin/controller/RaceScoringController.java:39-87`, `src/main/java/org/ctc/admin/controller/MatchScoringController.java:37-81`, `src/main/java/org/ctc/admin/controller/TeamController.java:79-146`, `src/main/java/org/ctc/admin/controller/SeasonController.java:30-40` (8 repositories injected)
- **Severity:** Medium
- **Description:** Multiple controllers directly call `repository.findById()`, `repository.save()`, `repository.delete()` instead of delegating to service classes. This violates the project's own architecture principle documented in CLAUDE.md. The `SeasonController` injects 8 repositories directly. Business logic for CRUD operations (validation, cascading deletes, side effects) is scattered across controllers instead of being centralized in services. Fix approach: Extract repository calls from controllers into dedicated service methods (e.g., `DriverService.assignToSeason()`, `TrackService.save()`, `RaceScoringService.save()`).

## Missing Database Indexes

### No Indexes on Foreign Key Columns
- **Location:** `src/main/resources/db/migration/V1__initial_schema.sql`
- **Severity:** Medium
- **Description:** Only 2 indexes exist: `idx_cars_gt7id` (line 111) and `idx_alias_driver` (line 310). No indexes on frequently queried foreign key columns: `races.matchday_id`, `races.match_id`, `matches.matchday_id`, `race_results.race_id`, `race_results.driver_id`, `race_lineups.race_id`, `season_drivers.season_id`, `season_drivers.team_id`, `matchdays.season_id`, `season_teams.season_id`. MariaDB auto-indexes foreign keys but H2 does not, which means dev/test performance may differ from production. Fix approach: Add a new Flyway migration (`V2__add_indexes.sql`) with indexes on all FK columns used in queries.

## Broad Exception Catching

### `catch (Exception e)` Pattern Overuse
- **Location:** `src/main/java/org/ctc/admin/controller/TemplateEditorController.java` (30+ catch blocks), `src/main/java/org/ctc/admin/controller/RaceController.java:139-215` (6 catch blocks), `src/main/java/org/ctc/admin/controller/MatchdayController.java:121-158` (4 catch blocks), `src/main/java/org/ctc/admin/controller/PlayoffController.java:58-169` (4 catch blocks), `src/main/java/org/ctc/dataimport/CsvImportController.java:50-147` (3 catch blocks)
- **Severity:** Low
- **Description:** Over 60 `catch (Exception e)` blocks across controllers catch all exceptions indiscriminately. This masks programming errors (NullPointerException, ClassCastException) behind generic "operation failed" flash messages. The `TemplateEditorController` alone has 30+ identical try-catch blocks for template load/save operations. Fix approach: Catch specific exceptions (IllegalArgumentException, IOException) or use a `@ControllerAdvice` `@ExceptionHandler` for common error patterns. The existing `GlobalModelAdvice` only adds `appVersion` -- extend it or add a new `GlobalExceptionHandler`.

## No Global Exception Handler

### Missing @ExceptionHandler for Unhandled Exceptions
- **Location:** `src/main/java/org/ctc/admin/controller/GlobalModelAdvice.java`
- **Severity:** Medium
- **Description:** The `GlobalModelAdvice` class only provides the `appVersion` model attribute. There is no `@ExceptionHandler` for `NoSuchElementException` (thrown by numerous `.orElseThrow()` calls), `IllegalArgumentException`, or generic `Exception`. When a `.orElseThrow()` fails (e.g., invalid UUID in URL), the user sees a raw Spring Boot error page with stack trace (dev profile has `include-stacktrace: always`). Fix approach: Add a `GlobalExceptionHandler` class with `@ControllerAdvice` and `@ExceptionHandler` methods that redirect to a user-friendly error page.

## Unguarded `.orElseThrow()` Calls

### NoSuchElementException on Invalid IDs
- **Location:** 50+ occurrences across service and controller classes (see `RaceManagementService.java`, `SeasonManagementService.java`, `PlayoffService.java`, `DriverController.java`, `TeamController.java`, etc.)
- **Severity:** Low
- **Description:** Services and controllers use `.orElseThrow()` without providing meaningful exception messages. When an entity is not found, the resulting `NoSuchElementException` has no context about which entity or ID failed. Combined with the missing global exception handler, this produces unhelpful error responses. Fix approach: Use `.orElseThrow(() -> new EntityNotFoundException("Season not found: " + id))` or similar, and handle in global exception handler.

## God Service: RaceManagementService

### Excessive Responsibilities (673 Lines)
- **Location:** `src/main/java/org/ctc/domain/service/RaceManagementService.java`
- **Severity:** Medium
- **Description:** This service is the largest in the codebase at 673 lines with 13 injected dependencies (9 repositories + 4 graphic services + ScoringService + FileStorageService + GoogleCalendarService). It handles: race CRUD, race detail assembly, calendar events, result saving/scoring, file uploads, link management, graphic generation, overlay generation, settings graphic generation, and download serving. The `getRaceDetailData()` method alone (line 114-183) computes 15+ boolean flags. Fix approach: Extract into focused services: `RaceGraphicService` (graphic generation/download), `RaceAttachmentService` (upload/delete/link), keep `RaceManagementService` for core CRUD + detail.

## Incomplete Feature: Alltime Standings

### TODO: Cross-Season Standings
- **Location:** `src/main/java/org/ctc/admin/controller/StandingsController.java:34`
- **Severity:** Low
- **Description:** The alltime standings feature returns an empty list with a TODO comment: "Alltime-Standings muessen cross-season MatchScoring-Aggregation unterstuetzen". The UI presumably shows an empty table when "alltime" is selected, which is misleading. Fix approach: Either implement cross-season aggregation in `StandingsService` or remove/disable the alltime option in the UI until it works.

## CompletableFuture Without Error Propagation

### Async Image Downloads Swallow Exceptions
- **Location:** `src/main/java/org/ctc/gt7sync/Gt7ScraperService.java:100-113`, `src/main/java/org/ctc/gt7sync/Gt7SyncService.java:116-140`
- **Severity:** Low
- **Description:** GT7 sync uses `CompletableFuture.runAsync()` for parallel image downloads with individual catch blocks that only log errors. The `CompletableFuture.allOf(futures).join()` call will not surface exceptions from individual futures since they are caught internally. This means partial sync failures are silently logged but the overall operation reports success. Fix approach: Collect and report failed downloads in the sync result summary.

## OSIV Dependency for Lazy Loading

### Implicit Coupling Between Templates and Hibernate Session
- **Location:** `src/main/resources/application.yml:16` (`open-in-view: true`)
- **Severity:** Low
- **Description:** OSIV is intentionally enabled (documented in CLAUDE.md). However, no `@EntityGraph` or `JOIN FETCH` queries exist in any repository. All relationship traversal in Thymeleaf templates triggers lazy-loading N+1 queries through the open session. For the current data volume this is acceptable, but it makes query behavior invisible and hard to optimize. If data grows, standings calculation (which loads all matches for a season, then traverses teams) or race lists could become slow. Fix approach: Add `@EntityGraph` annotations to key repository methods (e.g., `findByMatchdaySeasonId` with `homeTeam`/`awayTeam` graphs) as data grows.

## Unbounded `findAll()` Calls

### No Pagination on List Endpoints
- **Location:** `src/main/java/org/ctc/admin/controller/DriverController.java:36`, `src/main/java/org/ctc/domain/service/RaceManagementService.java:99`, `src/main/java/org/ctc/domain/service/MatchdayService.java:62`, `src/main/java/org/ctc/domain/service/DriverRankingService.java:59`, `src/main/java/org/ctc/dataimport/DriverMatchingService.java:51`
- **Severity:** Low
- **Description:** Multiple endpoints call `repository.findAll()` without pagination: drivers, teams, cars, tracks, seasons, matchdays, races. With the current small dataset this works, but loading all drivers or all races into memory will degrade as the league grows across multiple seasons. The `DriverMatchingService.findAll()` is called during every CSV import preview. Fix approach: Add pagination support to list endpoints and convert `findAll()` calls to filtered/paginated queries where the full dataset is not needed.

## TemplateEditorController Repetition

### 30+ Identical Try-Catch Blocks
- **Location:** `src/main/java/org/ctc/admin/controller/TemplateEditorController.java` (380 lines)
- **Severity:** Medium
- **Description:** The `TemplateEditorController` has an extreme pattern of code duplication. The `index()` method (lines 41-133) contains 10 identical try-catch blocks, each loading one template. Each save/reset/preview endpoint follows the same pattern. This controller injects 10 graphic service dependencies and repeats the same load/save/reset/preview structure for each. Fix approach: Introduce a generic template operations pattern using a map of template type to service, then loop over the map for load/save/reset operations.

## File Upload Path Traversal Risk (Mitigated but Fragile)

### `storeFromUrl` Downloads Arbitrary URLs
- **Location:** `src/main/java/org/ctc/domain/service/FileStorageService.java:83-93`
- **Severity:** Medium
- **Description:** The `storeFromUrl()` method downloads content from an arbitrary URL using `java.net.URI.create(sourceUrl).toURL().openStream()`. While this is used for GT7 image sync (trusted source), there is no URL validation or allowlist. A bug or misuse could trigger SSRF (Server-Side Request Forgery) against internal services. The `delete()` method (line 47-60) correctly validates path traversal, but `storeFromUrl()` does not validate the source URL scheme or host. Fix approach: Add URL scheme validation (only `https://`) and optionally an allowlist for trusted hosts.

## H2 Console Enabled in Dev

### Database Console Without Authentication
- **Location:** `src/main/resources/application-dev.yml:15-16`
- **Severity:** Low
- **Description:** H2 console is enabled at `/h2-console` in dev profile with no password. This is acceptable for local development but must never be enabled in production. The `application-prod.yml` does not explicitly disable it (it just does not enable it). Fix approach: Explicitly set `spring.h2.console.enabled: false` in application.yml default and only override in dev profile (current behavior is implicitly safe but fragile).

## Stacktrace Exposure in Dev Profile

### Error Details Visible to Users
- **Location:** `src/main/resources/application-dev.yml:3-4` (`include-stacktrace: always`, `include-message: always`)
- **Severity:** Low
- **Description:** Dev profile exposes full stack traces and error messages in HTTP responses. While appropriate for development, the production profile does not explicitly set these to `never`. Spring Boot defaults are safe, but explicit configuration would be more robust against future changes.

## Single Flyway Migration File

### Monolithic Initial Schema
- **Location:** `src/main/resources/db/migration/V1__initial_schema.sql` (322 lines)
- **Severity:** Low
- **Description:** The entire schema lives in a single V1 migration with a comment "noch nicht veroeffentlicht" (not yet published). This is fine for pre-release, but once in production, schema changes must be incremental. There is no V2+ migration yet, suggesting the schema is still evolving through V1 modifications rather than additive migrations. Fix approach: Once the schema stabilizes and goes to production, freeze V1 and use V2+ for all future changes.

## Playwright Runtime Dependency

### Browser Engine Required at Runtime
- **Location:** `pom.xml` (Playwright dependency without `<scope>test</scope>`)
- **Severity:** Medium
- **Description:** Playwright is declared as a compile-scope dependency (not test-scope) because it is used at runtime for graphic generation (TeamCardService, LineupGraphicService, etc.). This means the production deployment requires Chromium to be installed (`./mvnw exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"`). If Chromium is not installed, graphic generation silently fails. The graphic services are excluded from JaCoCo coverage because they require Playwright. Fix approach: Document the Chromium requirement in deployment checklist; consider making graphic generation optional with a health check.

## Test Coverage Gaps from Excludes

### 11 Classes Excluded from Coverage
- **Location:** `pom.xml` JaCoCo configuration (excludes section)
- **Severity:** Low
- **Description:** 11 classes are excluded from JaCoCo coverage measurement: `CtcManagerApplication`, `TestDataService`, `DemoDataSeeder`, `TeamCardService`, `LineupGraphicService`, `ResultsGraphicService`, `SettingsGraphicService`, `OverlayGraphicService`, `MatchResultsGraphicService`, `PowerRankingsGraphicService`, `AbstractGraphicService`. The graphic services are excluded because they require Playwright, which is reasonable. However, this means approximately 1500+ lines of production code have no coverage enforcement. Fix approach: For graphic services, consider testing template rendering logic separately from Playwright browser interaction.

## StandingsController Business Logic

### Swiss Pairing Sorting Logic in Controller
- **Location:** `src/main/java/org/ctc/admin/controller/StandingsController.java:48-56`
- **Severity:** Low
- **Description:** The `StandingsController` contains sorting logic for Swiss-format standings directly in the controller (lines 48-56): it calls `swissPairingService.calculateBuchholz()`, merges buchholz into standings, then sorts with a 4-level comparator. This violates the "Controllers duenn halten" principle. The non-Swiss path just delegates to `standingsService.calculateStandings()`. Fix approach: Move Swiss-specific buchholz enrichment and sorting into `StandingsService.calculateStandings()` which already knows the season format.

---

*Concerns audit: 2026-04-03*
