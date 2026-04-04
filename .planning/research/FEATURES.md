# Feature Landscape

**Domain:** Technical Debt Cleanup for Spring Boot 4 / Thymeleaf Admin Application
**Researched:** 2026-04-03

## Table Stakes

Features the codebase needs or architectural quality suffers. These are the "fix it or regret it" items.

| # | Feature | Why Expected | Complexity | Notes |
|---|---------|--------------|------------|-------|
| T1 | Extract controller repository access into services | Violates project's own architecture principle (CLAUDE.md). 6+ controllers call repositories directly. Scattered business logic makes future changes risky. | Medium | 8 controllers affected: SeasonController (8 repos!), TeamController, DriverController, TrackController, CarController, RaceScoringController, MatchScoringController. Each needs a corresponding service or extension of existing service. |
| T2 | Global exception handler via @ControllerAdvice | 60+ catch(Exception e) blocks mask real errors. Users see raw stacktraces on .orElseThrow() failures. No consistent error UX. | Low | Add GlobalExceptionHandler alongside existing GlobalModelAdvice. Handle EntityNotFoundException, IllegalArgumentException, IOException. Redirect to error page with flash message. |
| T3 | Meaningful .orElseThrow() exceptions | 50+ bare .orElseThrow() calls produce context-free NoSuchElementException. Debugging production issues becomes guesswork. | Low | Mechanical find-and-replace: `.orElseThrow()` to `.orElseThrow(() -> new EntityNotFoundException("Entity not found: " + id))`. Custom EntityNotFoundException caught by T2's handler. |
| T4 | Specific exception types (eliminate catch-all) | catch(Exception e) hides NullPointerException, ClassCastException, and other programming errors behind "operation failed" messages. | Low | After T2 is in place, replace catch(Exception e) with specific catches. TemplateEditorController alone has 30+ blocks -- addressed by T6. |
| T5 | DB indexes on foreign key columns | H2 (dev/test) does not auto-index FKs like MariaDB. Dev/prod performance parity is broken. 10+ FK columns lack indexes. | Low | Single new Flyway migration V2__add_indexes.sql. Straightforward CREATE INDEX statements. No schema changes, no data migration. |
| T6 | TemplateEditorController duplication cleanup | 380 lines with 30+ identical try-catch blocks, 10 injected graphic services. Extreme copy-paste debt -- any cross-cutting change requires 10+ edits. | Medium | Extract to map-based dispatch: Map<TemplateType, GraphicService> with generic load/save/reset/preview methods. Cuts ~250 lines. |
| T7 | Spring Security Basic Auth for prod/docker | Zero authentication on all endpoints. Single highest-severity concern. Any network-accessible deployment is wide open. | Medium | Add spring-boot-starter-security. SecurityFilterChain bean with profile-conditional config: httpBasic for prod/docker, permitAll for dev/local. Credentials via environment variables. |
| T8 | SSRF protection for FileStorageService.storeFromUrl() | Downloads from arbitrary URLs with no validation. SSRF vector if misused. | Low | URL scheme allowlist (https only) + host allowlist (gran-turismo.com domains). 5-10 lines of validation code. |
| T9 | H2 console explicitly dev-only | Currently implicitly safe (only enabled in dev profile) but fragile. A misconfiguration could expose database console in production. | Low | Explicit `spring.h2.console.enabled: false` in application.yml base, override to true only in application-dev.yml. Already nearly correct, just needs hardening. |
| T10 | Stacktrace exposure disabled in prod | Dev profile sets include-stacktrace: always. Prod relies on Spring Boot defaults. Explicit is better than implicit. | Low | Add `server.error.include-stacktrace: never` and `include-message: never` to application.yml base or application-prod.yml. |

## Differentiators

Goes beyond fixing -- improves the architecture for future development velocity.

| # | Feature | Value Proposition | Complexity | Notes |
|---|---------|-------------------|------------|-------|
| D1 | Split RaceManagementService (God Service) | 673 lines, 13+ dependencies. Splitting into focused services (RaceGraphicService, RaceAttachmentService, core RaceManagementService) makes each testable in isolation, reduces cognitive load, and follows SRP. | High | Highest complexity item. Requires careful analysis of method groupings, dependency mapping, and ensuring no circular dependencies. The RaceDetailData record with 15+ booleans signals the service knows too much. |
| D2 | StandingsController business logic extraction | Swiss pairing sorting/buchholz enrichment lives in controller. Moving to StandingsService makes the calculation testable and reusable. | Low | Small scope: ~10 lines of sorting logic. StandingsService.calculateStandings() should handle format-specific enrichment internally. |
| D3 | Alltime Standings: implement or disable | Empty list with TODO is misleading UX. Either implement cross-season aggregation or remove UI option. | Medium | Recommend: disable UI option first (low effort), implement later as a feature. Cross-season aggregation touches StandingsService deeply. |
| D4 | CompletableFuture error propagation in GT7 Sync | Silent failures in async image downloads. Sync reports success when images fail. | Low | Collect futures' exceptions, aggregate into sync result summary. Pattern: `.exceptionally()` handler that records failure + returns null, then filter results. |
| D5 | Playwright runtime dependency documentation + health check | Chromium required at runtime but not enforced. Silent failure when missing. | Low | Add actuator health indicator that checks Playwright/Chromium availability. Document in deployment checklist. |
| D6 | @EntityGraph preparation for N+1 optimization | No JOIN FETCH or @EntityGraph exists anywhere. OSIV masks N+1 queries. Adding @EntityGraph to key repositories prepares for data growth. | Medium | Identify top 3-5 hot paths (standings calculation, race lists, matchday loading). Add @EntityGraph annotations. Do NOT disable OSIV -- just optimize the worst paths. |
| D7 | Repository pagination preparation | Unbounded findAll() on drivers, teams, cars, tracks. Fine now, technical debt accumulating. | Low | Change repository methods to support Pageable parameter. No UI pagination needed yet -- just ensure repositories accept it. Service layer can pass Pageable.unpaged() as default. |

## Anti-Features

Things to deliberately NOT do during this cleanup.

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| OAuth2/OIDC integration | Massive scope creep. Single-admin app with no user management. Basic Auth is sufficient and correct for this use case. | Basic Auth with env-var credentials (T7). If multi-user needed later, that is a separate milestone. |
| Full pagination UI | UI rework is not technical debt cleanup. Changing all list views adds risk and scope without immediate value at current data volumes. | Repository-level preparation only (D7). Pageable.unpaged() preserves current behavior. |
| Disable OSIV | OSIV is a deliberate architectural choice documented in CLAUDE.md. Disabling it would break all Thymeleaf templates that traverse lazy relations -- a massive rewrite. | @EntityGraph on hot paths (D6). Monitor with Hibernate statistics if needed. |
| Modify V1 Flyway migration | Flyway checks checksums. Changing V1 breaks all existing databases. | New V2__add_indexes.sql migration only (T5). All schema changes via new migration files. |
| Comprehensive test coverage for graphic services | 11 Playwright-dependent classes are excluded from JaCoCo. Writing tests for browser-based rendering is extremely complex and fragile. | Accept the exclusion. Consider extracting non-Playwright logic (data preparation) into testable helper classes as a future task. |
| Centralized error page with fancy UI | A beautiful error page is a feature, not debt cleanup. The goal is preventing raw stacktraces, not building a polished error experience. | Simple Thymeleaf error template (error.html) with flash message display. Functional, not pretty. |
| Refactor RaceDetailData record | The 15-boolean record is a code smell but refactoring it requires Template changes. High risk for a cleanup milestone. | Accept it during God Service split (D1). The booleans stay but live in the right service. Refactor in a future UI milestone. |
| Add Bean Validation to all DTOs | Comprehensive @Valid/@NotNull on every form DTO is a feature, not debt. Current forms work. | Only add validation where it prevents data corruption. Do not blanket-add constraints. |

## Feature Dependencies

```
T2 (Global Exception Handler) --> T3 (Meaningful .orElseThrow)
     |                               Handler catches the custom exceptions
     v
T4 (Specific exception types)
     Controllers can remove catch-all blocks because handler exists

T1 (Extract controller logic) --> T6 (TemplateEditor cleanup)
     |                               Both follow same pattern: move logic to services
     v
D1 (Split God Service)
     Easier to split when controller->service boundary is clean

T7 (Spring Security) -- independent, can be done in any order
     |
     +--> T9 (H2 console dev-only) -- Security makes this doubly safe
     +--> T10 (Stacktrace exposure) -- Security prevents info leakage

T5 (DB indexes) -- fully independent, no code dependencies
T8 (SSRF protection) -- fully independent
D2 (Standings extraction) -- independent, small scope
D3 (Alltime Standings) -- depends on D2 if implementing (standings logic in service)
D4 (CompletableFuture errors) -- fully independent
D5 (Playwright health check) -- fully independent
D6 (@EntityGraph) -- independent but best done after T1 (services own the queries)
D7 (Pagination prep) -- independent but best done after T1 (services own the queries)
```

## MVP Recommendation (Phase Ordering)

### Phase 1: Exception Handling Foundation
Prioritize:
1. **T2** - Global Exception Handler (unblocks T3, T4)
2. **T3** - Meaningful .orElseThrow() exceptions
3. **T10** - Stacktrace exposure hardening (trivial, pairs with T2)
4. **T9** - H2 console hardening (trivial, config-only)

Rationale: These are low-complexity, high-value, and unblock the controller cleanup work. After this phase, the app handles errors gracefully instead of exposing internals.

### Phase 2: Controller Cleanup
1. **T1** - Extract controller repository access into services
2. **T6** - TemplateEditorController duplication cleanup
3. **D2** - StandingsController business logic extraction
4. **T4** - Replace remaining catch(Exception e) with specific types

Rationale: With the exception handler in place, controllers can be cleaned up confidently. Service extraction is the core architectural improvement.

### Phase 3: God Service Split
1. **D1** - Split RaceManagementService

Rationale: Standalone phase because it is the highest-complexity item. Needs full attention. Dependencies on T1 being complete (clean controller-service boundary).

### Phase 4: Security and Hardening
1. **T7** - Spring Security Basic Auth
2. **T8** - SSRF protection
3. **T5** - DB indexes (new Flyway migration)

Rationale: Security is the single highest-severity concern but is placed after architectural cleanup so the clean service layer makes SecurityFilterChain configuration simpler (clear endpoint patterns).

### Phase 5: Optimization Preparation
1. **D6** - @EntityGraph annotations
2. **D7** - Repository pagination preparation
3. **D4** - CompletableFuture error propagation
4. **D5** - Playwright health check
5. **D3** - Alltime Standings (disable UI option)

Defer: Full alltime standings implementation -- this is a feature, not debt cleanup.

## Implementation Notes

### T1: Controller-to-Service Extraction Pattern
For each controller with direct repository access:
1. Create or extend a service class (e.g., DriverService, TrackService)
2. Move repository calls into service methods with business-meaningful names
3. Controller calls service, handles redirect/flash messages only
4. Service owns transactions (@Transactional where needed)

SeasonController (8 repositories) is the most complex extraction. Consider a SeasonAdminService that wraps the pool management operations.

### T2: Global Exception Handler Structure
```java
@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(EntityNotFoundException.class)
    public String handleNotFound(EntityNotFoundException ex, RedirectAttributes attrs) {
        attrs.addFlashAttribute("error", ex.getMessage());
        return "redirect:/admin";
    }
    @ExceptionHandler(IllegalArgumentException.class)
    // ... similar pattern
}
```
Keep separate from GlobalModelAdvice (different concerns).

### T7: Profile-Conditional Security
```java
@Configuration
@Profile({"prod", "docker"})
public class SecurityConfig {
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated())
            .httpBasic(Customizer.withDefaults())
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"));
        return http.build();
    }
}
```
Dev/local profile: no SecurityConfig bean loaded, so Spring Security auto-config with `spring.security.user.*` defaults -- or add a permissive dev config.

### D1: God Service Split Strategy
Based on the 13 dependencies in RaceManagementService:
- **RaceGraphicService**: lineupGraphicService, resultsGraphicService, settingsGraphicService, overlayGraphicService, teamCardService, fileStorageService (for downloads)
- **RaceAttachmentService**: raceAttachmentRepository, fileStorageService (for uploads/links)
- **RaceCalendarService**: googleCalendarService (if complex enough, otherwise keep in core)
- **RaceManagementService** (slimmed): raceRepository, matchRepository, matchdayRepository, seasonRepository, teamRepository, driverRepository, scoringService -- core CRUD and detail assembly

### T5: Index Migration
```sql
-- V2__add_indexes.sql
CREATE INDEX idx_races_matchday ON races(matchday_id);
CREATE INDEX idx_races_match ON races(match_id);
CREATE INDEX idx_matches_matchday ON matches(matchday_id);
CREATE INDEX idx_race_results_race ON race_results(race_id);
CREATE INDEX idx_race_results_driver ON race_results(driver_id);
CREATE INDEX idx_race_lineups_race ON race_lineups(race_id);
CREATE INDEX idx_season_drivers_season ON season_drivers(season_id);
CREATE INDEX idx_season_drivers_team ON season_drivers(team_id);
CREATE INDEX idx_matchdays_season ON matchdays(season_id);
CREATE INDEX idx_season_teams_season ON season_teams(season_id);
```
Must verify exact column names against V1 schema before writing.

## Sources

- Spring Boot 4.0.5 project configuration (pom.xml, application*.yml)
- CLAUDE.md architecture principles (project documentation)
- .planning/codebase/CONCERNS.md (17 identified concerns with severity ratings)
- Codebase analysis: RaceManagementService.java (673 lines, 13 deps), TemplateEditorController.java (380 lines), StandingsController.java, SeasonController.java
- Spring MVC @ControllerAdvice pattern (Spring Framework reference)
- Spring Security httpBasic + profile activation pattern (Spring Security reference)
- Flyway migration best practices (additive-only, no V1 modification)
