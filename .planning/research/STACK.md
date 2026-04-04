# Technology Stack: Security, Exception Handling & Service Refactoring

**Project:** CTC Manager - Technical Debt Cleanup
**Researched:** 2026-04-03
**Overall confidence:** HIGH

## Context

This document covers the **additional** technologies needed for the technical debt cleanup milestone. The existing stack (Spring Boot 4.0.5, Java 25, Thymeleaf, MariaDB/H2, Flyway) is documented in `.planning/codebase/STACK.md` and is not repeated here.

## Recommended Stack Additions

### Spring Security (Basic Auth)

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| `spring-boot-starter-security` | 4.0.5 (managed) | Authentication for prod/docker profiles | Only dependency needed. Spring Boot 4 ships Spring Security 7.0 which includes everything for HTTP Basic Auth. No version override needed -- BOM manages it. |
| `spring-boot-starter-security-test` | 4.0.5 (managed) | `@WithMockUser`, `SecurityMockMvcRequestPostProcessors` | Required for testing secured endpoints in WebMvc tests. Without it, all `@WebMvcTest` tests break when security is on the classpath. |

**Confidence:** HIGH -- verified via Spring Security 7.0 official docs and Spring Boot 4.0 release notes.

**Critical Spring Security 7 changes (vs. older tutorials):**

| Removed API | Replacement | Impact |
|-------------|-------------|--------|
| `and()` method on HttpSecurity | Lambda DSL only | All config must use lambda style: `.httpBasic(basic -> {...})` |
| `authorizeRequests()` | `authorizeHttpRequests()` | The old method does not exist in Security 7 |
| `AntPathRequestMatcher` | `PathPatternRequestMatcher` | Use `PathPatternRequestMatcher.withDefaults().matcher("/admin/**")` |
| `MvcRequestMatcher` | `PathPatternRequestMatcher` | Same replacement |
| `WebSecurityConfigurerAdapter` | `SecurityFilterChain` bean | Already removed in Security 6, confirmed gone in 7 |

### Exception Handling

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Spring MVC `@ControllerAdvice` | Built-in (Spring Framework 7) | Global exception handler | Already in the framework. No additional dependency. The existing `GlobalModelAdvice` already uses `@ControllerAdvice` -- add a separate `GlobalExceptionHandler` class. |

**No additional dependencies needed.** Exception handling is pure Spring MVC -- `@ControllerAdvice` + `@ExceptionHandler` annotations.

**Confidence:** HIGH -- this is core Spring MVC functionality unchanged across versions.

### Service Layer Refactoring

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| No new dependencies | -- | Service extraction is pure refactoring | Moving repository calls from controllers into services requires zero new libraries. |

**Confidence:** HIGH -- architectural refactoring, not technology change.

### Database Indexing

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Flyway (existing) | Already in stack | New migration file `V2__add_indexes.sql` | Indexes are pure SQL. No new dependency -- just a new migration file. |

**Confidence:** HIGH -- standard Flyway practice.

## Detailed Configuration Patterns

### Spring Security 7 Configuration for This Project

The app needs: auth on prod/docker, no auth on dev/local. The correct pattern for Spring Security 7:

**Production/Docker security config (always active):**
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @Profile({"prod", "docker"})
    public SecurityFilterChain securedFilterChain(HttpSecurity http) throws Exception {
        PathPatternRequestMatcher.Builder request = PathPatternRequestMatcher.withDefaults();
        return http
            .authorizeHttpRequests(authz -> authz
                .requestMatchers(request.matcher("/actuator/health")).permitAll()
                .anyRequest().authenticated()
            )
            .httpBasic(basic -> {})
            .csrf(csrf -> csrf.disable())  // Admin-only app, Basic Auth, no browser forms over API
            .build();
    }

    @Bean
    @Profile({"dev", "local"})
    public SecurityFilterChain openFilterChain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(authz -> authz
                .anyRequest().permitAll()
            )
            .csrf(csrf -> csrf.disable())
            .build();
    }
}
```

**User credentials via application-prod.yml / application-docker.yml:**
```yaml
spring:
  security:
    user:
      name: ${ADMIN_USERNAME:admin}
      password: ${ADMIN_PASSWORD}  # MUST be set via env var, no default
```

**Why this approach:**
- Single-admin app -- `spring.security.user` properties are sufficient, no UserDetailsService needed
- `@Profile` annotation on beans cleanly separates secured vs. open configs
- BCrypt encoding happens automatically when using Spring Security's default `InMemoryUserDetailsManager`
- H2 console access in dev preserved (no auth on dev profile)
- Actuator health endpoint stays public (Docker healthcheck)

**Why NOT these alternatives:**

| Alternative | Why Not |
|-------------|---------|
| Form-based login | Overkill for single-admin. Basic Auth with browser's built-in dialog is simpler. |
| OAuth2/OIDC | Massively overkill. Requires external IdP setup for one user. |
| Database-backed users | No user management needed. One admin, credentials in env vars. |
| `@ConditionalOnProperty` | `@Profile` is cleaner and already the project's pattern for environment config. |
| CSRF enabled with Basic Auth | Basic Auth + Thymeleaf forms: CSRF protection is good practice but adds complexity. For a single-admin app behind Basic Auth, the threat model is minimal. Can be re-enabled later if needed. |

### CSRF Decision: Disable vs. Enable

**Recommendation: Disable CSRF initially, document as future consideration.**

Rationale: This is a single-admin app where the admin is the only user. Basic Auth already requires credentials for every request. CSRF attacks require a victim to be authenticated in a browser session -- with Basic Auth, the browser caches credentials per session, so there is a theoretical CSRF vector. However, for a single-admin racing league manager, the risk is negligible. If the app ever gets multi-user support, CSRF should be re-enabled.

**Alternative if CSRF is desired:** Thymeleaf's Spring Security integration automatically adds CSRF tokens to forms via `th:action`. This works out of the box -- but requires `spring-security-extras` Thymeleaf dialect (see below).

**Confidence:** MEDIUM -- CSRF decision is a tradeoff, not a clear-cut answer.

### Thymeleaf Spring Security Integration (Optional)

| Technology | Version | Purpose | When to Use |
|------------|---------|---------|-------------|
| `thymeleaf-extras-springsecurity7` | Latest (check Maven Central) | `sec:authorize` attributes in templates, CSRF token injection | Only if showing/hiding UI elements based on auth status, or if enabling CSRF |

**Recommendation: Skip for now.** The admin UI is either fully secured (prod) or fully open (dev). There is no partial access control where you would hide buttons based on roles. If CSRF is enabled later, this becomes necessary.

**Confidence:** MEDIUM -- the library exists but version alignment with Spring Security 7 needs verification at implementation time.

### Global Exception Handler Pattern

**No new dependencies.** Use existing Spring MVC capabilities:

```java
@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)  // After GlobalModelAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public String handleNotFound(EntityNotFoundException ex, RedirectAttributes attrs) {
        attrs.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:/admin";
    }

    @ExceptionHandler(NoSuchElementException.class)
    public String handleNoSuchElement(NoSuchElementException ex, RedirectAttributes attrs) {
        attrs.addFlashAttribute("errorMessage", "Requested item not found");
        return "redirect:/admin";
    }

    @ExceptionHandler(Exception.class)
    public String handleGeneral(Exception ex, RedirectAttributes attrs) {
        log.error("Unexpected error", ex);
        attrs.addFlashAttribute("errorMessage", "An unexpected error occurred");
        return "redirect:/admin";
    }
}
```

**Why NOT ProblemDetail / RFC 7807:**
This is a Thymeleaf server-rendered app, not a REST API. ProblemDetail produces `application/problem+json` responses -- useless for browser users. The correct pattern for SSR apps is redirect-with-flash-message, which the app already uses in individual controllers.

**Why a custom `EntityNotFoundException`:**
The JPA `jakarta.persistence.EntityNotFoundException` exists but is tied to JPA proxy loading. A custom `org.ctc.domain.exception.EntityNotFoundException` is cleaner -- it carries the entity type and ID, and is not coupled to JPA internals.

**Confidence:** HIGH -- standard Spring MVC pattern, well-documented.

## Installation

```xml
<!-- Add to pom.xml dependencies -->

<!-- Spring Security -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Spring Security Test Support -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

That is it. Two dependencies total. Everything else (exception handling, service refactoring, indexes) uses existing stack.

## Alternatives Considered

| Category | Recommended | Alternative | Why Not |
|----------|-------------|-------------|---------|
| Auth mechanism | HTTP Basic Auth | Form Login | Single admin, no login page needed, browser dialog sufficient |
| User store | `spring.security.user` properties | Database / LDAP | One user, env vars are simplest |
| Security config | `@Profile` on `SecurityFilterChain` beans | `@ConditionalOnProperty` | Profiles already control all env differences |
| Request matchers | `PathPatternRequestMatcher` | `AntPathRequestMatcher` | Ant matchers removed in Spring Security 7 |
| Error responses | Redirect + flash messages | RFC 7807 ProblemDetail | SSR app, not REST API -- users see HTML, not JSON |
| Exception types | Custom `EntityNotFoundException` | `jakarta.persistence.EntityNotFoundException` | JPA-coupled, poor error messages |
| CSRF | Disabled (initially) | Enabled with Thymeleaf integration | Low risk for single-admin Basic Auth app |

## Version Compatibility Matrix

| Component | Version | Managed By | Notes |
|-----------|---------|------------|-------|
| Spring Boot | 4.0.5 | Parent POM | Already in use |
| Spring Security | 7.0.x | Spring Boot BOM | Auto-managed, no version override |
| Spring Framework | 7.0.x | Spring Boot BOM | Auto-managed |
| Jakarta Servlet | 6.1 | Spring Boot BOM | Required by Security 7 |
| Thymeleaf | 3.1.x | Spring Boot BOM | Already in use |

**No version conflicts expected.** All new dependencies are managed by the Spring Boot 4.0.5 BOM.

**Confidence:** HIGH -- BOM-managed dependencies, standard Spring Boot practice.

## Impact on Existing Tests

Adding `spring-boot-starter-security` to the classpath changes default behavior:

1. **All `@WebMvcTest` tests will fail** -- Spring Security auto-configures and requires authentication
2. **Fix:** Add `@WithMockUser` annotation to test classes, or `@Import(SecurityConfig.class)` with the dev profile active
3. **`spring-boot-starter-security-test`** provides `@WithMockUser`, `@WithAnonymousUser`, and `SecurityMockMvcRequestPostProcessors.httpBasic()`
4. **E2E tests (Playwright):** Must run against dev profile (no auth) -- already the case

**Recommended test pattern:**
```java
@WebMvcTest(SomeController.class)
@WithMockUser  // Authenticates all requests in this test class
class SomeControllerTest {
    // existing tests work unchanged
}
```

**Confidence:** HIGH -- well-documented Spring Security testing pattern.

## Sources

- [Spring Security 7.0 What's New](https://docs.spring.io/spring-security/reference/whats-new.html) -- verified API removals and new patterns
- [Spring Security Basic Auth Reference](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/basic.html) -- official Basic Auth docs
- [Spring Boot 4.0 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes) -- confirmed Security 7 as managed dependency
- [Spring Security 7 Web Migration Guide](https://docs.spring.io/spring-security/reference/6.5-SNAPSHOT/migration-7/web.html) -- PathPatternRequestMatcher migration
- [PathPatternRequestMatcher API](https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/web/servlet/util/matcher/PathPatternRequestMatcher.html) -- builder pattern reference
- [Baeldung: Disable Security for Profile](https://www.baeldung.com/spring-security-disable-profile) -- profile-based security pattern
- [Spring Boot 4 & Spring Framework 7 Overview (Baeldung)](https://www.baeldung.com/spring-boot-4-spring-framework-7) -- compatibility overview

---

*Stack research: 2026-04-03*
