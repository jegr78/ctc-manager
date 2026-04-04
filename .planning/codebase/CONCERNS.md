# Concerns & Technical Debt
> Generated: 2026-04-04 | Focus: concerns

## Layer Violation: Domain Services Depend on Admin DTOs

### Domain layer imports admin.dto and admin.service packages
- **Location:** `src/main/java/org/ctc/domain/service/RaceService.java:3-6`, `src/main/java/org/ctc/domain/service/SeasonManagementService.java:3`, `src/main/java/org/ctc/domain/service/TeamManagementService.java:3-4`, `src/main/java/org/ctc/domain/service/PlayoffService.java:3`, `src/main/java/org/ctc/domain/service/CarService.java:3`, `src/main/java/org/ctc/domain/service/TrackService.java:3`, `src/main/java/org/ctc/domain/service/DriverService.java:3`, `src/main/java/org/ctc/domain/service/MatchScoringService.java:3`, `src/main/java/org/ctc/domain/service/RaceScoringService.java:3`, `src/main/java/org/ctc/domain/service/MatchdayService.java:3`, `src/main/java/org/ctc/domain/service/RaceGraphicService.java:3-6`
- **Severity:** Medium
- **Description:** 11 domain services import from `org.ctc.admin.dto` or `org.ctc.admin.service`. This creates a circular dependency between layers: admin depends on domain (correct), but domain also depends on admin (incorrect). The `domain.service` layer should be reusable without the `admin` layer. `RaceService` even imports `TeamCardService` (an admin graphic service) and `GoogleCalendarService` (a dataimport service). `RaceGraphicService` imports 4 admin graphic services.
- **Fix approach:** Either move the DTOs used by domain services into `org.ctc.domain.dto` (form objects as domain-level input types), or have controllers convert DTOs to domain method parameters before calling services. For `RaceGraphicService` depending on admin graphic services, consider moving it to `admin.service` since it orchestrates admin-level graphic generation.

## Residual Direct Repository Access in Controllers

### Controllers still inject repositories directly
- **Location:** `src/main/java/org/ctc/admin/controller/StandingsController.java:26` (`SeasonRepository`), `src/main/java/org/ctc/admin/controller/PowerRankingsController.java:25` (`SeasonRepository`), `src/main/java/org/ctc/admin/controller/PlayoffController.java:31` (`PlayoffRoundRepository`), `src/main/java/org/ctc/admin/controller/TeamCardController.java:36-37` (`SeasonRepository`, `SeasonTeamRepository`), `src/main/java/org/ctc/dataimport/CsvImportController.java:23` (`SeasonRepository`)
- **Severity:** Low
- **Description:** Five controllers still directly inject repositories, violating the "Controller duenn halten" principle. The v1.0 cleanup extracted the majority of repository access, but these were missed. `StandingsController` uses `seasonRepository.findAll()` at line 67 and `seasonRepository.findById()` at line 44. `TeamCardController` uses both `seasonRepository` and `seasonTeamRepository` for listing and lookup logic (lines 45-51, 68, 84, 100, 119).
- **Fix approach:** Add `findAll()` / `findById()` pass-through methods to the relevant services (`StandingsService`, `TeamCardService`, `PlayoffService`) and remove repository injections from controllers.

## TemplateEditorController Repetition

### 380-line controller with 30+ identical try-catch blocks
- **Location:** `src/main/java/org/ctc/admin/controller/TemplateEditorController.java` (380 lines)
- **Severity:** Medium
- **Description:** The controller has 10 near-identical load blocks in the `index()` method (lines 42-131) and 20 near-identical save/reset endpoint pairs (lines 135-353). Each graphic service (teamCard, lineup, settings, raceResults, matchResults, matchdayOverview, matchdaySchedule, matchdayResults, overlay, powerRankings) gets its own copy-paste save/reset method pair. The `index()` method loads all 10 templates with repeated try-catch boilerplate.
- **Fix approach:** Extract to a service that manages template operations generically. Use a `Map<String, AbstractGraphicService>` keyed by template type. Reduce to a single parameterized `save(type, template)` and `reset(type)` endpoint pair using `@PathVariable`.

## Broad Exception Catching in Controllers

### `catch (Exception e)` masks programming errors
- **Location:** `src/main/java/org/ctc/admin/controller/TemplateEditorController.java` (37 `catch (Exception e)` blocks), `src/main/java/org/ctc/admin/controller/RaceController.java:143,175,186,197,208,219` (6 blocks), `src/main/java/org/ctc/admin/controller/PlayoffController.java:71,124,166,182,197,210,223` (7 blocks), `src/main/java/org/ctc/admin/controller/MatchdayController.java:121,133,145,158` (4 blocks), `src/main/java/org/ctc/admin/controller/TeamCardController.java:74,90` (2 blocks), `src/main/java/org/ctc/admin/controller/SeasonController.java:129` (1 block), `src/main/java/org/ctc/dataimport/CsvImportController.java:50,81,147` (3 blocks)
- **Severity:** Low
- **Description:** Despite having a `GlobalExceptionHandler`, 60+ controller methods still catch `Exception` locally and convert to flash messages. This means `NullPointerException`, `ClassCastException`, and other programming errors are silently swallowed as "operation failed" user messages instead of being caught by the global handler. The `GlobalExceptionHandler` correctly distinguishes `EntityNotFoundException`, `BusinessRuleException`, and `ValidationException` -- controllers should let these propagate.
- **Fix approach:** Replace `catch (Exception e)` in controllers with specific exception catches (`IOException` for file/graphic operations, `BusinessRuleException` for business rule violations). Let unexpected exceptions propagate to `GlobalExceptionHandler`. For graphic generation endpoints (`RaceController`, `PlayoffController`), catch `IOException` specifically.

## Incomplete Feature: Alltime Standings

### TODO: Cross-season aggregation not implemented
- **Location:** `src/main/java/org/ctc/admin/controller/StandingsController.java:34`
- **Severity:** Low
- **Description:** The alltime standings view returns an empty list (`java.util.List.of()`) with a TODO comment: "Alltime-Standings muessen cross-season MatchScoring-Aggregation unterstuetzen". The driver ranking works (`driverRankingService.calculateAlltimeRanking()`) but team standings across seasons are not aggregated. Users selecting "Alltime" see an empty team standings table.
- **Fix approach:** Implement a `StandingsService.calculateAlltimeStandings()` method that aggregates `MatchScoring` data across all seasons. The existing `calculateStandings(seasonId)` method provides the per-season pattern to build upon.

## StandingsController Business Logic

### Swiss-format sorting logic in controller
- **Location:** `src/main/java/org/ctc/admin/controller/StandingsController.java:48-56`
- **Severity:** Medium
- **Description:** The controller contains Swiss-format-specific business logic: calculating Buchholz scores, merging them into standings, and applying a 4-level sort comparator. This violates the "Controller duenn halten" principle. The controller should call `standingsService.calculateStandings(seasonId)` and get back correctly sorted results regardless of format.
- **Fix approach:** Move the Buchholz calculation and Swiss-specific sorting into `StandingsService.calculateStandings()`. The service already knows the season format and can apply the appropriate sort.

## Pervasive Inline Styles in Admin Templates

### 634 inline `style=` attributes across 47 templates
- **Location:** `src/main/resources/templates/admin/season-detail.html` (48 occurrences), `src/main/resources/templates/admin/race-detail.html` (51 occurrences), `src/main/resources/templates/admin/template-editors.html` (181 occurrences), `src/main/resources/templates/admin/race-form.html` (12 occurrences), `src/main/resources/templates/admin/car-form.html` (10 occurrences), `src/main/resources/templates/admin/track-form.html` (10 occurrences), and 41 other files
- **Severity:** Low
- **Description:** The CLAUDE.md architecture principle states "Keine Inline-Styles auf Buttons" and prescribes CSS classes from `admin.css`. While button styling was addressed, 634 inline `style=` attributes remain across 47 admin templates. The `season-detail.html` and `race-detail.html` files are the worst offenders with 48 and 51 inline styles respectively. The `template-editors.html` has 181 but many are for the editor preview area which may be intentional. For form layouts (`margin-top`, `display:flex`, `gap`), these should use utility classes.
- **Fix approach:** Define utility CSS classes in `admin.css` for common patterns (`mt-16`, `flex-row`, `gap-8`, etc.) and replace inline styles in templates. Prioritize `season-detail.html` and `race-detail.html` as they have the most occurrences. Leave graphic render templates (`*-render.html`) as-is since they produce standalone HTML for Playwright screenshots.

## Large Service Classes

### PlayoffService and RaceService exceeding reasonable size
- **Location:** `src/main/java/org/ctc/domain/service/PlayoffService.java` (614 lines), `src/main/java/org/ctc/domain/service/RaceService.java` (515 lines), `src/main/java/org/ctc/dataimport/CsvImportService.java` (436 lines), `src/main/java/org/ctc/admin/service/TemplatePreviewService.java` (390 lines), `src/main/java/org/ctc/domain/service/SeasonManagementService.java` (370 lines)
- **Severity:** Low
- **Description:** `PlayoffService` at 614 lines handles bracket creation, seeding, matchup management, race creation, winner determination, and bracket view assembly. `RaceService` at 515 lines handles race CRUD, form data assembly, results saving, calendar events, quick scoring, and used-selection queries. These are becoming unwieldy and harder to test. The original `RaceManagementService` God Service was split in v1.0, but the resulting services have grown with new features.
- **Fix approach:** For `PlayoffService`, consider extracting `PlayoffBracketViewService` (bracket/view assembly) and `PlayoffSeedingService` (seeding logic). For `RaceService`, consider extracting `RaceFormDataService` (form assembly for views) and moving calendar event logic to a dedicated service. No urgency -- these are manageable but should be watched.

## SSRF Risk in FileStorageService.storeFromUrl

### Server-side URL fetching with limited validation
- **Location:** `src/main/java/org/ctc/domain/service/FileStorageService.java:83-97`
- **Severity:** Low
- **Description:** `storeFromUrl()` opens an HTTP connection to a user-influenced URL. While it enforces HTTPS, it does not validate the hostname -- internal network URLs (`https://localhost`, `https://169.254.169.254`) could be fetched. Currently called only from `Gt7SyncService` with scraped GT7 image URLs (trusted source), and from `CarService`/`TrackService` image imports. The risk is low because it requires admin access, but it's a defense-in-depth concern.
- **Fix approach:** Add hostname validation to reject private/internal IP ranges and localhost. Alternatively, add a URL allowlist for known domains (e.g., `gran-turismo.com`).

## Missing Path Traversal Check in store() and storeImage()

### store() and storeImage() don't verify target stays within uploadDir
- **Location:** `src/main/java/org/ctc/domain/service/FileStorageService.java:33-44` (`store`), `src/main/java/org/ctc/domain/service/FileStorageService.java:99-108` (`storeImage`)
- **Severity:** Low
- **Description:** The `delete()` method correctly normalizes the path and checks `file.startsWith(uploadDir)`, but `store()` and `storeImage()` do not perform this check. The `sanitize()` method strips special characters which mitigates the risk, and the UUID-based directory structure adds another layer. However, the defense is inconsistent -- `delete()` has path traversal protection but `store()` does not.
- **Fix approach:** Add `.normalize()` and `startsWith(uploadDir)` check to `store()` and `storeImage()` after resolving the target path, matching the pattern already used in `delete()`.

## Unbounded findAll() Queries

### Multiple services call findAll() without pagination
- **Location:** `src/main/java/org/ctc/domain/service/RaceService.java:76` (`raceRepository.findAll()`), `src/main/java/org/ctc/domain/service/DriverService.java:48` (`driverRepository.findAll()`), `src/main/java/org/ctc/domain/service/DriverRankingService.java:59` (`seasonDriverRepository.findAll()`), `src/main/java/org/ctc/dataimport/DriverMatchingService.java:51` (`driverRepository.findAll()`), `src/main/java/org/ctc/domain/service/RaceService.java:226,237` (`matchdayRepository.findAll()`, `teamRepository.findAll()`)
- **Severity:** Low
- **Description:** Several services load entire tables into memory without pagination. Currently acceptable because this is a small-scale admin tool (dozens of teams, hundreds of races), but `raceRepository.findAll()` and `driverRepository.findAll()` will become problematic if the application scales. The `RaceService.getRaceListData()` falls back to `findAll()` when neither matchdayId nor seasonId is provided.
- **Fix approach:** Low priority. For `RaceService.getRaceListData()`, require a seasonId parameter when no matchdayId is given (the UI always sends one). For driver lists, add pagination or limit queries where results are displayed in tables.

---

*Concerns audit: 2026-04-04*
