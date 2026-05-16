# Architecture Patterns

**Domain:** Spring Boot MVC/Thymeleaf admin app -- technical debt cleanup
**Researched:** 2026-04-03

## Recommended Target Architecture

The application already follows a layered MVC architecture. The cleanup does not change the fundamental architecture -- it enforces it consistently. Every layer violation documented in CONCERNS.md is a case where the existing pattern was not followed, not a case where a new pattern is needed.

### Target Layer Diagram

```
[Browser] --> [Spring Security Filter Chain]
                        |
                  [Controllers]        -- HTTP only: parse request, call service, populate model/redirect
                        |
                  [Domain Services]    -- All business logic, all repository access
                        |
                  [Repositories]       -- Spring Data JPA interfaces
                        |
                  [Database]           -- MariaDB (prod) / H2 (dev)

Cross-cutting:
  [GlobalExceptionHandler]  -- @ControllerAdvice: catches exceptions, returns error views
  [GlobalModelAdvice]       -- @ControllerAdvice: injects appVersion (existing)
  [Spring Security Config]  -- Profile-conditional: Basic Auth for prod/docker only
```

### Component Boundaries

| Component | Responsibility | Communicates With | Rules |
|-----------|---------------|-------------------|-------|
| Controllers (17) | HTTP request/response, Model population, redirects | Services only (never repositories) | No business logic, no repository injection, catch no exceptions (let GlobalExceptionHandler handle them) |
| Domain Services (14+) | Business logic, data access orchestration, validation | Repositories, other services | Own all repository access. Throw typed exceptions. @Transactional where needed. |
| Admin Services (12) | Graphic generation (Playwright) | Thymeleaf TemplateEngine, filesystem | Stateless. Called by controllers or other services. |
| Repositories (17) | Data access | Database via JPA | No business logic. Query methods only. |
| GlobalExceptionHandler | Central error handling | Controllers (implicit via @ControllerAdvice) | Catches typed exceptions, maps to error views with user-friendly messages |
| SecurityConfig | Authentication/authorization | Spring Security filter chain | Profile-conditional: active only for prod/docker |
| DTOs | Form binding (POST), display data | Controllers <-> Services | POJOs/records. No JPA annotations. |

### What Changes vs. Current State

| Aspect | Current | Target |
|--------|---------|--------|
| Repository access | 6 controllers inject repositories directly | All repository access goes through services |
| Exception handling | 60+ catch blocks in controllers, no global handler | GlobalExceptionHandler catches all; controllers have zero try-catch |
| Exception types | `IllegalStateException`, `NoSuchElementException` (no message) | Custom hierarchy: `EntityNotFoundException`, `BusinessRuleException` |
| Security | None | Spring Security Basic Auth on prod/docker profiles |
| RaceManagementService | 673 lines, 13 dependencies, God Service | Split into 3 focused services |
| TemplateEditorController | 380 lines, 30+ identical try-catch blocks | Generic template operations with Map-based dispatch |
| StandingsController | Swiss sorting logic in controller | Sorting logic moved to StandingsService |

## Data Flow

### Request Lifecycle (Target State)

```
1. HTTP Request arrives
2. Spring Security filter chain checks authentication (prod/docker only)
3. Spring MVC dispatches to @Controller method
4. Controller calls service method(s)
5. Service performs business logic, accesses repositories
6. Service returns domain objects or DTOs
7. Controller puts result into Model
8. Thymeleaf renders template (lazy-loads via OSIV if needed)
9. HTML response sent

On error:
5a. Service throws EntityNotFoundException or BusinessRuleException
6a. GlobalExceptionHandler catches exception
7a. Handler returns error view with flash message, or redirects with error
```

### POST-Redirect-GET Pattern (Unchanged)

```
1. POST /admin/seasons/save
2. Controller binds form DTO, calls service
3. Service validates and persists
4. Controller adds flash attribute (success/error message)
5. Controller returns "redirect:/admin/seasons/{id}"
6. Browser follows redirect (GET)
7. Flash attribute displayed once, then discarded
```

### Exception Propagation (Target State)

```
Service Layer:
  repository.findById(id)
    .orElseThrow(() -> new EntityNotFoundException("Season", id))

GlobalExceptionHandler:
  @ExceptionHandler(EntityNotFoundException.class)
  public String handleNotFound(EntityNotFoundException ex, Model model, HttpServletRequest request) {
      model.addAttribute("errorMessage", ex.getMessage());
      // For AJAX-like requests from Thymeleaf fragments, return error fragment
      // For full page requests, return error view
      return "error";
  }

  @ExceptionHandler(BusinessRuleException.class)
  public String handleBusinessRule(BusinessRuleException ex, RedirectAttributes attrs, HttpServletRequest request) {
      attrs.addFlashAttribute("errorMessage", ex.getMessage());
      return "redirect:" + extractReferer(request);
  }
```

## Patterns to Follow

### Pattern 1: Thin Controller with Service Delegation

**What:** Controllers contain zero business logic, zero repository access, zero try-catch blocks.
**When:** Every controller method.
**Why:** The project already documents this as a principle in CLAUDE.md -- the cleanup enforces it.

```java
// TARGET: SeasonController.save()
@PostMapping("/save")
public String save(@Valid @ModelAttribute("seasonForm") SeasonForm form,
                   BindingResult result,
                   RedirectAttributes redirectAttributes, Model model) {
    if (result.hasErrors()) {
        model.addAttribute("allRaceScorings", seasonService.getAllRaceScorings());
        model.addAttribute("allMatchScorings", seasonService.getAllMatchScorings());
        return "admin/season-form";
    }
    var season = seasonService.saveFromForm(form);
    redirectAttributes.addFlashAttribute("successMessage", "Season saved: " + season.getName());
    return "redirect:/admin/seasons";
}
```

### Pattern 2: Custom Exception Hierarchy

**What:** Two exception types replace all generic exceptions.
**When:** All service methods that can fail.

```java
// Base for "entity not found" scenarios (HTTP 404 equivalent)
public class EntityNotFoundException extends RuntimeException {
    public EntityNotFoundException(String entityType, UUID id) {
        super(entityType + " not found: " + id);
    }
}

// Base for business rule violations (HTTP 400 equivalent)
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
```

**Why two, not more:** This is a single-admin Thymeleaf app, not a REST API. The user sees flash messages, not HTTP status codes. Two exception types map cleanly to two user-facing behaviors: "thing not found" (show error page) and "operation not allowed" (redirect back with message).

### Pattern 3: Map-Based Template Operations

**What:** Replace 30+ identical try-catch blocks with a registry of template services.
**When:** TemplateEditorController refactoring.

```java
// In TemplateEditorController or a new TemplateEditorService:
private final Map<String, AbstractGraphicService> templateServices;

// Spring injects all AbstractGraphicService beans automatically
// then build map by template type key

public void loadAll(Model model) {
    templateServices.forEach((key, service) -> {
        model.addAttribute(key + "Template", service.loadTemplate());
        model.addAttribute(key + "IsCustom", service.hasCustomTemplate());
    });
}
```

### Pattern 4: Profile-Conditional Security

**What:** Spring Security active only on prod/docker profiles, transparent on dev/local.
**When:** Security implementation phase.

```java
@Configuration
@Profile({"prod", "docker"})
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/uploads/**").permitAll()
                .anyRequest().authenticated()
            )
            .httpBasic(Customizer.withDefaults())
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/admin/import/**")  // multipart forms
            );
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(
            @Value("${app.admin.username}") String username,
            @Value("${app.admin.password}") String password) {
        var user = User.withUsername(username)
                .password("{noop}" + password)  // or bcrypt for production
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(user);
    }
}

@Configuration
@Profile({"dev", "local"})
public class DevSecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
```

**Confidence:** MEDIUM. Spring Security 7 (shipped with Spring Boot 4) uses this lambda DSL. The `WebSecurityConfigurerAdapter` was removed in Spring Security 6. Verified via Spring Security docs.

### Pattern 5: God Service Decomposition

**What:** Split RaceManagementService (673 lines, 13 deps) into focused services.
**When:** Service layer cleanup phase.

```
RaceManagementService (673 lines, 13 deps)
    |
    +--> RaceService (~200 lines)
    |      CRUD, detail assembly, form data, calendar events
    |      Deps: RaceRepository, MatchRepository, MatchdayRepository, SeasonRepository,
    |            TeamRepository, DriverRepository, CarRepository, TrackRepository,
    |            SeasonDriverRepository, SeasonTeamRepository, ScoringService,
    |            GoogleCalendarService
    |
    +--> RaceGraphicService (~150 lines)
    |      Graphic generation and download serving
    |      Deps: LineupGraphicService, ResultsGraphicService, SettingsGraphicService,
    |            OverlayGraphicService, TeamCardService, FileStorageService
    |
    +--> RaceAttachmentService (~100 lines)
           Upload, delete, link management for race attachments
           Deps: RaceAttachmentRepository, RaceRepository, FileStorageService
```

## Anti-Patterns to Avoid

### Anti-Pattern 1: Repository Injection in Controllers
**What:** `@Autowired SeasonRepository` in a `@Controller`
**Why bad:** Business logic leaks into the web layer. Validation, authorization checks, side effects (logging, events) get skipped. Makes testing harder (need Spring MVC test instead of unit test).
**Instead:** Create or extend a service method. Even simple `findAll()` calls should go through a service -- the service is where you add pagination, filtering, or caching later.

### Anti-Pattern 2: Catch-All Exception Blocks in Controllers
**What:** `try { ... } catch (Exception e) { redirectAttributes.addFlashAttribute("errorMessage", ...); }`
**Why bad:** Masks programming errors (NPE, ClassCast). Makes every controller method 10+ lines longer. Duplicated across 60+ locations.
**Instead:** Let exceptions propagate to GlobalExceptionHandler. For specific business errors that should redirect back with a message, services throw `BusinessRuleException` and the handler redirects.

### Anti-Pattern 3: Business Logic in Templates (SpEL)
**What:** Complex comparators, collection projections, conditional chains in Thymeleaf expressions.
**Why bad:** Untestable, hard to debug, performance invisible. Already documented in CLAUDE.md as anti-pattern.
**Instead:** Compute everything in the service, pass simple values to template.

### Anti-Pattern 4: Multiple SecurityFilterChain Without @Order
**What:** Defining multiple SecurityFilterChain beans without explicit ordering.
**Why bad:** Spring Security processes filter chains in order. Without `@Order`, behavior is unpredictable.
**Instead:** Use `@Profile` to ensure only one SecurityFilterChain is active per environment (the recommended approach for this app).

## Refactoring Order (Build Dependencies)

The order matters because later refactorings depend on earlier ones being complete.

### Phase 1: Exception Infrastructure (Foundation)

**Must happen first** because all subsequent refactorings produce cleaner code when exceptions are handled centrally.

1. **Create custom exception types** (`EntityNotFoundException`, `BusinessRuleException`) in `org.ctc.domain.model` or a new `org.ctc.domain.exception` package
2. **Create GlobalExceptionHandler** (`@ControllerAdvice` with `@ExceptionHandler` methods)
3. **Replace bare `.orElseThrow()`** with `.orElseThrow(() -> new EntityNotFoundException(...))` across all 50+ occurrences

**Why first:** Without central exception handling, extracting repository calls from controllers requires adding try-catch blocks (making things worse). With the handler in place, services can throw typed exceptions and controllers stay clean.

**Dependency:** None. Can start immediately.

### Phase 2: Service Layer Completion (Enables Controller Cleanup)

**Must happen before controller cleanup** because controllers need services to delegate to.

1. **Create missing service methods** for operations currently done in controllers:
   - `SeasonService` (new): season CRUD, scoring preset lookups
   - Extend `DriverService`: `assignToSeason()`, `delete()`
   - Extend `TrackService` (new or extend existing): save, delete, image upload
   - `CarService` (new): save, delete, image upload
   - `RaceScoringService` (new): CRUD for race scoring presets
   - `MatchScoringService` (new): CRUD for match scoring presets
2. **Move Swiss sorting logic** from StandingsController into StandingsService
3. **Split RaceManagementService** into RaceService, RaceGraphicService, RaceAttachmentService

**Why second:** Controllers cannot be made thin until the services they delegate to exist.

**Dependency:** Phase 1 (new services use custom exceptions from day one).

### Phase 3: Controller Cleanup (Applies the Pattern)

**Must happen after service layer is complete.**

1. **Remove repository injections** from all 6 affected controllers
2. **Replace controller try-catch blocks** with service delegation (60+ blocks)
3. **Refactor TemplateEditorController** with Map-based dispatch pattern
4. **Remove business logic** from StandingsController (already moved in Phase 2)

**Why third:** This is the actual cleanup -- replacing bad patterns with good ones. Requires services (Phase 2) and exception infrastructure (Phase 1) to be in place.

**Dependency:** Phase 1 + Phase 2.

### Phase 4: Security Integration (Sits on Top)

**Must happen after controller cleanup** to avoid securing broken code.

1. **Add `spring-boot-starter-security` dependency**
2. **Create SecurityConfig** (prod/docker profiles) with Basic Auth
3. **Create DevSecurityConfig** (dev/local profiles) permitting all
4. **SSRF protection** in FileStorageService.storeFromUrl()
5. **Explicit H2 console** and stacktrace settings

**Why last:** Security is a cross-cutting layer that wraps everything else. Adding it before the architecture is clean means dealing with security + broken patterns simultaneously. Also, security changes affect all existing tests (need to add `@WithMockUser` or disable security in test profile).

**Dependency:** Phase 3 (clean controllers make security rules predictable).

### Phase 5: Database and Performance (Independent, Low Risk)

**Can happen in parallel with Phase 4** since these are additive changes.

1. **V2 Flyway migration** with FK indexes
2. **@EntityGraph annotations** on key repository queries (preparation only)
3. **Pagination-ready repository methods** (preparation only, no UI changes)

**Dependency:** None for indexes. Phase 2 for @EntityGraph (needs service methods to exist).

### Phase 6: Loose Ends (Cleanup)

1. **Alltime Standings**: implement or disable UI option
2. **CompletableFuture error propagation** in GT7 sync
3. **Playwright health check** documentation

**Dependency:** Phase 2 (StandingsService must be complete for alltime feature).

## Dependency Graph

```
Phase 1: Exception Infrastructure
    |
    v
Phase 2: Service Layer Completion
    |
    v
Phase 3: Controller Cleanup
    |
    +---> Phase 4: Security Integration
    |
    +---> Phase 5: Database & Performance (can parallel with Phase 4)
              |
              v
         Phase 6: Loose Ends
```

## Scalability Considerations

| Concern | Current (Small League) | At 10 Seasons | At 50 Seasons |
|---------|----------------------|---------------|---------------|
| findAll() calls | Fine | Noticeable on driver/car lists | Needs pagination |
| N+1 queries (OSIV) | Fine | Standings calculation slows | @EntityGraph essential |
| No DB indexes (H2) | Fine (H2 dev only) | N/A (MariaDB auto-indexes FKs) | N/A |
| Single admin user | Fine | Fine | Fine (single-admin app) |
| File storage | Local filesystem | Fine | Consider cleanup/archival |

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Thin controller pattern | HIGH | Standard Spring MVC, documented in project CLAUDE.md |
| Custom exception hierarchy | HIGH | Well-established Spring pattern, verified in multiple sources |
| @ControllerAdvice exception handling | HIGH | Core Spring MVC feature, stable across versions |
| Profile-conditional security | MEDIUM | Spring Security 7 lambda DSL verified; exact API for Spring Boot 4.0.5 should be tested |
| RaceManagementService split | MEDIUM | Based on code analysis; exact boundary needs validation during implementation |
| Refactoring order | HIGH | Based on dependency analysis of the 17 concerns |

## Sources

- Spring Security Basic Authentication docs: https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/basic.html
- Spring Security HttpSecurity API (7.0.4): https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/config/annotation/web/builders/HttpSecurity.html
- Spring MVC Exception Handling: https://spring.io/blog/2013/11/01/exception-handling-in-spring-mvc/
- Exception handling best practices: https://reflectoring.io/spring-boot-exception-handling/
- Project CLAUDE.md architecture principles (local)
- Codebase analysis: `.planning/codebase/ARCHITECTURE.md` and `.planning/codebase/CONCERNS.md` (local)

---

*Architecture research: 2026-04-03*
