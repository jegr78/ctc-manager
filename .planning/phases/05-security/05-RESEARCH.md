# Phase 5: Security - Research

**Researched:** 2026-04-04
**Domain:** Spring Security 7 / Spring Boot 4 HTTP Basic Auth, SSRF Protection
**Confidence:** HIGH

## Summary

Phase 5 adds HTTP Basic Authentication to the CTC Manager for prod and docker profiles while keeping dev and local profiles open. The implementation requires adding `spring-boot-starter-security` and `spring-boot-starter-security-test` to pom.xml, creating a profile-conditional SecurityFilterChain, exempting the actuator health endpoint, and adding HTTPS-only validation to `FileStorageService.storeFromUrl()`.

A critical finding from the codebase analysis: all 19 controller test classes use `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("dev")` -- NOT `@WebMvcTest`. Since tests run with the dev profile active, and the dev profile SecurityFilterChain will permit all requests, the existing tests should remain green WITHOUT needing `@WithMockUser`. The `spring-boot-starter-security-test` dependency is still needed for the dedicated security integration tests that verify 401 behavior under prod/docker profiles.

Spring Security 7 (bundled with Spring Boot 4.x) requires Lambda DSL exclusively -- the old `.and()` chaining style is removed. `requestMatchers()` replaces the deprecated `antMatchers()`. `PathPatternRequestMatcher` is the new default matcher, but for this simple use case, the standard `requestMatchers("/actuator/health").permitAll()` pattern suffices.

**Primary recommendation:** Use two separate `@Configuration` classes with `@Profile` annotations -- `SecuredSecurityConfig` for prod/docker and `OpenSecurityConfig` for dev/local. This is the cleanest approach because it avoids runtime profile checks inside the SecurityFilterChain and makes each config independently testable.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- D-01: Username/Passwort ausschliesslich ueber Environment Variables (`SPRING_SECURITY_USER_NAME`, `SPRING_SECURITY_USER_PASSWORD`)
- D-02: Docker-Profil hat Default-Credentials in docker-compose.yml (z.B. admin/ctc-admin) fuer einfaches lokales Testen
- D-03: Prod-Profil erfordert explizite Env-Vars -- Applikation startet ohne, aber alle Endpoints sind dann gesperrt (Spring Security Default-Passwort im Log)
- D-04: Eigene SecurityFilterChain Bean mit profil-bedingter Konfiguration: prod/docker verlangen Basic Auth, dev/local erlauben alles
- D-06: Actuator Health-Endpoint (/actuator/health) bleibt ohne Auth erreichbar (Docker Healthcheck)
- D-07: Alle 19 WebMvcTest-Klassen erhalten @WithMockUser (direkt oder via Basisklasse/Meta-Annotation) damit sie mit Security auf dem Classpath weiter gruen sind
- D-08: Eigene dedizierte Security-Tests die verifizieren: 401 ohne Credentials in prod/docker, 200 mit Credentials, dev-Profil bleibt offen
- D-10: FileStorageService.storeFromUrl() validiert URL-Schema -- nur `https://` erlaubt
- D-11: Bei nicht-HTTPS URL: IllegalArgumentException("Only HTTPS URLs allowed: " + url) + log.warn()
- D-12: Keine Hostname-Allowlist, kein Private-IP-Block -- nur Schema-Validierung fuer SECU-04
- D-13: 401 (nicht authentifiziert): Standard Browser Basic Auth Dialog via WWW-Authenticate Header -- kein custom Template
- D-14: 403 (Zugriff verweigert): Eigene Fehlerseite im Admin-Layout, analog zur bestehenden Error-Seite aus Phase 1
- D-15: Claude's Discretion: 403-Seite ueber GlobalExceptionHandler routen oder Spring Security AccessDeniedHandler konfigurieren

### Claude's Discretion
- D-05: Ob eine einzelne SecurityConfig mit @ConditionalOnProfile oder zwei separate Configs (SecuredConfig + OpenConfig) -- Claude analysiert was mit Spring Security 7 / Spring Boot 4 am saubersten ist
- D-09: Ob @WithMockUser per Klasse oder als Meta-Annotation / Test-Basisklasse -- Claude waehlt was am wenigsten invasiv ist
- Spring Security 7 API: PathPatternRequestMatcher vs. antMatchers, lambda DSL Syntax
- Profil-Erkennung: @Profile, @ConditionalOnProperty, oder SecurityFilterChain mit Profil-Check
- @WithMockUser Platzierung (Klasse vs. Meta-Annotation vs. Basisklasse)
- 403-Routing-Mechanismus
- Ob H2-Console Pfad explizit in Security-Config aufgenommen wird

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SECU-01 | Spring Security Basic Auth aktiv fuer prod und docker Profile | Two-profile SecurityFilterChain pattern with `@Profile({"prod", "docker"})`, httpBasic + authorizeHttpRequests Lambda DSL |
| SECU-02 | Dev und local Profile bleiben ohne Authentifizierung | `@Profile({"dev", "local"})` SecurityFilterChain that permits all requests |
| SECU-03 | Alle bestehenden @WebMvcTest Tests funktionieren mit Security auf Classpath | Tests use `@SpringBootTest` + `@ActiveProfiles("dev")` -- dev profile SecurityFilterChain automatically permits all; `spring-boot-starter-security-test` needed for dedicated security tests |
| SECU-04 | FileStorageService.storeFromUrl() validiert URL-Schema (nur https) | URL scheme check before `URI.create()`, throw IllegalArgumentException + log.warn for non-HTTPS |
</phase_requirements>

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| spring-boot-starter-security | 4.0.5 (managed) | Security auto-configuration, SecurityFilterChain, Basic Auth | Spring Boot managed dependency, includes Spring Security 7.0.x |
| spring-boot-starter-security-test | 4.0.5 (managed) | @WithMockUser, security test utilities | Required in Spring Boot 4 for @WithMockUser to work (was split from starter-test) |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| (no additional dependencies) | | | All required classes come from the two starters above |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Two @Profile configs | Single config with Environment.matchesProfiles() | Runtime check in SecurityFilterChain is less clean; two separate classes are more explicit and testable |
| @ConditionalOnProperty | @Profile | @Profile is the standard Spring mechanism for profile-specific beans; aligns with existing codebase pattern |
| Custom AccessDeniedHandler | GlobalExceptionHandler for 403 | AccessDeniedHandler is the Spring Security standard; GlobalExceptionHandler does not intercept security exceptions (they are handled before reaching @ControllerAdvice) |

**Installation:**
```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

**Version verification:** Both starters are managed by `spring-boot-starter-parent:4.0.5` -- no explicit version needed.

## Architecture Patterns

### Recommended Project Structure
```
src/main/java/org/ctc/admin/
├── SecurityConfig.java          # @Profile({"prod", "docker"}) - Basic Auth
├── OpenSecurityConfig.java      # @Profile({"dev", "local"}) - Permit All
├── WebConfig.java               # Existing - upload dir resource handler
├── controller/
│   └── GlobalExceptionHandler.java  # Existing - extend for 403 (if needed)
├── ...
src/main/resources/templates/admin/
├── error.html                   # Existing error page
├── access-denied.html           # NEW - 403 page in admin layout
├── ...
src/test/java/org/ctc/admin/
├── SecurityIntegrationTest.java # NEW - dedicated security tests
├── controller/                  # Existing - NO changes needed
```

### Pattern 1: Two-Profile SecurityFilterChain (Recommended for D-05)
**What:** Two separate `@Configuration` classes, each with a `@Profile` annotation, providing different SecurityFilterChain beans.
**When to use:** When security behavior differs completely between profiles (permit-all vs. authenticated).
**Why chosen:** Avoids runtime profile checks; each config is small, focused, and independently testable. This is the cleanest pattern for Spring Security 7.

**Example:**
```java
// Source: Spring Security 7 official docs + Spring Boot 4 conventions
@Configuration
@Profile({"prod", "docker"})
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .httpBasic(Customizer.withDefaults())
            .exceptionHandling(exceptions -> exceptions
                .accessDeniedPage("/admin/access-denied")
            );
        return http.build();
    }
}
```

```java
@Configuration
@Profile({"dev", "local"})
public class OpenSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .anyRequest().permitAll()
            )
            .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
```

### Pattern 2: Credentials via Spring Boot Properties (for D-01, D-02, D-03)
**What:** Use Spring Boot's built-in `spring.security.user.name` and `spring.security.user.password` properties, which are automatically mapped from environment variables `SPRING_SECURITY_USER_NAME` / `SPRING_SECURITY_USER_PASSWORD`.
**Why:** No custom UserDetailsService needed. Spring Boot auto-configures an `InMemoryUserDetailsManager` with these properties. If no env vars are set, Spring Security generates a random password and logs it (D-03 behavior for free).

### Pattern 3: 403 Page via AccessDeniedHandler (Recommended for D-15)
**What:** Configure `exceptionHandling` in the SecurityFilterChain with `accessDeniedPage()` or a custom `AccessDeniedHandler`.
**Why chosen over GlobalExceptionHandler:** Spring Security exceptions (AccessDeniedException) are handled by the security filter chain BEFORE reaching `@ControllerAdvice`. The GlobalExceptionHandler never sees them. A custom `AccessDeniedHandler` or `accessDeniedPage()` is the only correct way.

**Example:**
```java
// In SecurityConfig (prod/docker)
.exceptionHandling(exceptions -> exceptions
    .accessDeniedPage("/admin/access-denied")
)
```

With a controller mapping:
```java
@GetMapping("/admin/access-denied")
public String accessDenied(Model model) {
    model.addAttribute("status", 403);
    model.addAttribute("error", "Access Denied");
    model.addAttribute("message", "You do not have permission to access this resource.");
    return "admin/access-denied";
}
```

### Anti-Patterns to Avoid
- **Using `.and()` chaining:** Removed in Spring Security 7. Use Lambda DSL exclusively.
- **Using `antMatchers()`:** Replaced by `requestMatchers()`.
- **Using `WebSecurityConfigurerAdapter`:** Removed since Spring Security 5.7. Use SecurityFilterChain beans.
- **Catching AccessDeniedException in @ControllerAdvice:** Spring Security handles these before they reach @ControllerAdvice. Use AccessDeniedHandler instead.
- **Disabling security for tests via `@AutoConfigureMockMvc(addFilters = false)`:** This hides security misconfigurations. Since tests use dev profile, the open SecurityFilterChain handles this cleanly.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| User credentials storage | Custom UserDetailsService with in-memory map | Spring Boot `spring.security.user.*` auto-config | Auto-creates InMemoryUserDetailsManager, handles password encoding, logs generated password |
| Basic Auth challenge | Custom filter that checks Authorization header | `httpBasic(Customizer.withDefaults())` | Handles WWW-Authenticate header, 401 response, realm, all edge cases |
| Profile-conditional security | if/else in SecurityFilterChain with Environment.matchesProfiles() | Two @Configuration classes with @Profile | Spring lifecycle handles bean selection; cleaner, testable |
| CSRF for stateless Basic Auth | Manual CSRF token management | Keep CSRF enabled (Spring Security default) or disable for API-only | For server-rendered forms with Basic Auth, CSRF can stay enabled (browser sends credentials automatically) |

**Key insight:** Spring Boot's security auto-configuration does 90% of the work. The custom SecurityFilterChain only needs to define which endpoints require authentication and configure httpBasic.

## Common Pitfalls

### Pitfall 1: CSRF with Basic Auth and Thymeleaf Forms
**What goes wrong:** Enabling CSRF (Spring Security default) with Basic Auth causes POST requests from Thymeleaf forms to fail with 403 if CSRF token is not included.
**Why it happens:** Basic Auth authenticates the request, but CSRF protection still requires a token for state-changing requests (POST, PUT, DELETE).
**How to avoid:** Two options: (a) Disable CSRF for prod/docker since Basic Auth is stateless and this is a single-admin app (D-14 from CONTEXT.md says "kein oeffentlich zugaengliches Formular in Prod"), or (b) add Thymeleaf CSRF token support. Option (a) is simpler and matches the project's explicit decision to exclude CSRF (see REQUIREMENTS.md Out of Scope).
**Warning signs:** 403 errors on form submissions after adding security.

### Pitfall 2: Spring Boot 4 Security Test Dependency
**What goes wrong:** `@WithMockUser` annotation silently does nothing without `spring-boot-starter-security-test`.
**Why it happens:** Spring Boot 4 split test starters into dedicated modules. Without the security test starter, the annotation processor for `@WithMockUser` is not loaded.
**How to avoid:** Always add `spring-boot-starter-security-test` with test scope.
**Warning signs:** Tests pass without authentication when they should require it.

### Pitfall 3: H2 Console Blocked by Security
**What goes wrong:** In dev profile, the H2 console at `/h2-console` becomes inaccessible after adding Spring Security.
**Why it happens:** Even the "permit all" SecurityFilterChain might interfere with H2 console's frame-based UI.
**How to avoid:** In the dev/local OpenSecurityConfig, also disable frame options: `.headers(headers -> headers.frameOptions(frame -> frame.disable()))`. Or since the dev config permits all and disables CSRF, this should work. Verify after implementation.
**Warning signs:** H2 console shows blank page or "refused to display in a frame" error.

### Pitfall 4: Actuator Health Behind Auth
**What goes wrong:** Docker healthcheck fails with 401 because the health endpoint requires authentication.
**Why it happens:** `anyRequest().authenticated()` catches `/actuator/health` unless explicitly exempted.
**How to avoid:** Place `.requestMatchers("/actuator/health").permitAll()` BEFORE `.anyRequest().authenticated()` -- order matters.
**Warning signs:** Docker container marked as unhealthy, restart loops.

### Pitfall 5: Security Exceptions Bypass @ControllerAdvice
**What goes wrong:** Developer adds AccessDeniedException handler to GlobalExceptionHandler, but it never triggers.
**Why it happens:** Spring Security exceptions are caught by the security filter chain (before DispatcherServlet). They never reach @ControllerAdvice.
**How to avoid:** Use Spring Security's `exceptionHandling()` configuration for 401/403 pages, not @ControllerAdvice.
**Warning signs:** Custom error pages never shown for security errors.

### Pitfall 6: DispatcherType.ERROR Not Permitted
**What goes wrong:** Error pages (including the 403 page) return 401 because the ERROR dispatch is itself caught by security.
**Why it happens:** Spring Security 7 filters all dispatcher types by default. When an error page is rendered, it goes through the security filter again as an ERROR dispatch.
**How to avoid:** Add `.dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()` in the secured config.
**Warning signs:** Error pages show browser Basic Auth dialog instead of the custom error page.

## Code Examples

### SecurityFilterChain for prod/docker (SECU-01)
```java
// Source: Spring Security 7 official docs
@Configuration
@Profile({"prod", "docker"})
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .httpBasic(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .exceptionHandling(exceptions -> exceptions
                .accessDeniedPage("/admin/access-denied")
            );
        return http.build();
    }
}
```

### SecurityFilterChain for dev/local (SECU-02)
```java
@Configuration
@Profile({"dev", "local"})
public class OpenSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .anyRequest().permitAll()
            )
            .csrf(csrf -> csrf.disable())
            .headers(headers -> headers
                .frameOptions(frame -> frame.disable())  // H2 Console
            );
        return http.build();
    }
}
```

### SSRF Protection (SECU-04)
```java
// In FileStorageService.storeFromUrl()
public String storeFromUrl(String subDir, UUID entityId, String sourceUrl, String filename) throws IOException {
    if (sourceUrl == null || !sourceUrl.toLowerCase().startsWith("https://")) {
        log.warn("Rejected non-HTTPS URL: {}", sourceUrl);
        throw new IllegalArgumentException("Only HTTPS URLs allowed: " + sourceUrl);
    }
    // ... existing implementation
}
```

### Security Integration Test (SECU-01 + SECU-02)
```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void givenNoCredentials_whenAccessAdmin_thenUnauthorized() throws Exception {
        mockMvc.perform(get("/admin/seasons"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void givenCredentials_whenAccessAdmin_thenOk() throws Exception {
        mockMvc.perform(get("/admin/seasons"))
            .andExpect(status().isOk());
    }

    @Test
    void givenNoCredentials_whenAccessHealth_thenOk() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());
    }
}
```

### Docker-Compose Credentials (D-02)
```yaml
# docker-compose.yml - add to app.environment
environment:
  SPRING_PROFILES_ACTIVE: docker
  SPRING_SECURITY_USER_NAME: admin
  SPRING_SECURITY_USER_PASSWORD: ctc-admin
```

```yaml
# docker-compose.prod.yml - add to app.environment
environment:
  SPRING_SECURITY_USER_NAME: ${CTC_ADMIN_USER}
  SPRING_SECURITY_USER_PASSWORD: ${CTC_ADMIN_PASSWORD}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| WebSecurityConfigurerAdapter | SecurityFilterChain beans | Spring Security 5.7 (removed in 6.0) | Must use @Bean SecurityFilterChain |
| antMatchers() | requestMatchers() | Spring Security 6.0 | Pattern matching API change |
| .and() chaining | Lambda DSL (mandatory) | Spring Security 7.0 | All config must use lambdas |
| spring-boot-starter-test includes security-test | Separate spring-boot-starter-security-test | Spring Boot 4.0 | Must add explicitly for @WithMockUser |
| @MockBean | @MockitoBean | Spring Boot 4.0 | Deprecated but still works |

**Deprecated/outdated:**
- `WebSecurityConfigurerAdapter`: Removed. Use SecurityFilterChain beans.
- `.and()` method: Removed in Spring Security 7. Lambda DSL only.
- `antMatchers()`: Replaced by `requestMatchers()`.
- `spring.security.user.name` in application.yml for prod: Use env vars instead (D-01).

## Critical Finding: Existing Tests Do NOT Need @WithMockUser

**Context:** CONTEXT.md D-07 says "Alle 19 WebMvcTest-Klassen erhalten @WithMockUser". However, the CONTEXT.md incorrectly refers to them as "WebMvcTest-Klassen".

**Reality:** All 19 controller test classes use `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("dev")`. They are full integration tests running with the dev profile.

**Implication:** If the dev profile SecurityFilterChain (`OpenSecurityConfig`) permits all requests:
1. Existing tests will continue to pass WITHOUT `@WithMockUser`
2. No changes to any of the 19 test classes are needed
3. `spring-boot-starter-security-test` is only needed for the NEW security integration tests

**Recommendation:** Do NOT add @WithMockUser to existing tests. Instead, verify that adding `spring-boot-starter-security` + the two SecurityFilterChain configs results in all 19 existing test classes passing green. Only add `spring-boot-starter-security-test` for the dedicated security tests.

**Risk level:** LOW -- the `@ActiveProfiles("dev")` + `@Profile({"dev", "local"})` permitAll chain is a well-understood Spring pattern. But this MUST be verified early (first task).

## Open Questions

1. **CSRF disable for prod/docker?**
   - What we know: REQUIREMENTS.md "Out of Scope" explicitly excludes "CSRF Protection" with reason "Kein oeffentlich zugaengliches Formular in Prod". This indicates CSRF should be disabled.
   - What's unclear: With Basic Auth + server-rendered Thymeleaf forms, the browser sends credentials automatically on every request. CSRF protection would actually add value for session-fixation attacks, but the project explicitly excluded it.
   - Recommendation: Disable CSRF (`.csrf(csrf -> csrf.disable())`) per project decision. Document in code comment why.

2. **H2 Console frame options in dev profile?**
   - What we know: dev profile enables H2 Console at `/h2-console`. Spring Security default blocks frames via X-Frame-Options.
   - What's unclear: Whether the OpenSecurityConfig's `permitAll()` alone is sufficient, or if frame options must also be explicitly disabled.
   - Recommendation: Explicitly disable frame options in dev/local config to be safe. Low effort, prevents debugging later.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + MockMvc (via Spring Boot 4.0.5) |
| Config file | pom.xml (surefire/failsafe plugins) |
| Quick run command | `./mvnw test -pl . -Dtest=SecurityIntegrationTest` |
| Full suite command | `./mvnw verify` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SECU-01 | Admin URLs return 401 without credentials in prod/docker | integration | `./mvnw test -Dtest=SecurityIntegrationTest -x` | Wave 0 |
| SECU-01 | Admin URLs return 200 with valid credentials in prod/docker | integration | `./mvnw test -Dtest=SecurityIntegrationTest -x` | Wave 0 |
| SECU-01 | Actuator health returns 200 without credentials | integration | `./mvnw test -Dtest=SecurityIntegrationTest -x` | Wave 0 |
| SECU-02 | Admin URLs accessible without login in dev profile | integration | `./mvnw test -Dtest=SecurityIntegrationTest -x` | Wave 0 |
| SECU-03 | All 19 existing controller tests pass with security on classpath | integration | `./mvnw verify` | Existing (19 files) |
| SECU-04 | storeFromUrl rejects non-HTTPS URLs | unit | `./mvnw test -Dtest=FileStorageServiceTest -x` | Existing (extend) |
| SECU-04 | storeFromUrl accepts HTTPS URLs | unit | `./mvnw test -Dtest=FileStorageServiceTest -x` | Existing (extend) |

### Sampling Rate
- **Per task commit:** `./mvnw verify` (ensures all 754+ tests stay green)
- **Per wave merge:** `./mvnw verify` (full suite)
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `src/test/java/org/ctc/admin/SecurityIntegrationTest.java` -- covers SECU-01, SECU-02
- [ ] Framework install: add `spring-boot-starter-security-test` to pom.xml

## Sources

### Primary (HIGH confidence)
- [Spring Security 7 Configuration Migration](https://docs.spring.io/spring-security/reference/6.5-SNAPSHOT/migration-7/configuration.html) -- Lambda DSL mandatory, .and() removed
- [Spring Security authorizeHttpRequests](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html) -- requestMatchers, permitAll, authenticated patterns
- [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide) -- security-test split, MockMvc changes
- [Spring Framework Issue #36423](https://github.com/spring-projects/spring-framework/issues/36423) -- @WebMvcTest security behavior in Boot 4 (confirms test dependency split)

### Secondary (MEDIUM confidence)
- [Baeldung Spring Security Basic Authentication](https://www.baeldung.com/spring-security-basic-authentication) -- General patterns verified against official docs
- [Spring Boot Security Auto-Configuration](https://www.baeldung.com/spring-boot-security-autoconfiguration) -- spring.security.user.* property auto-config

### Tertiary (LOW confidence)
- None -- all findings verified against official sources

## Project Constraints (from CLAUDE.md)

Directives relevant to this phase:
- **Profile**: Auth nur fuer prod/docker, dev/local bleiben ohne Auth
- **Testabdeckung**: 82% Line Coverage Minimum darf nicht unterschritten werden
- **Abwaertskompatibilitaet**: Keine Breaking Changes an bestehenden URLs/Endpoints
- **Flyway**: Keine DB-Aenderungen noetig fuer Security (pure Java config)
- **TDD**: Tests zuerst schreiben, dann Implementierung
- **Git-Workflow**: Feature-Branch, PR, squash-merge
- **Conventional Commits**: `feat(security):` Prefix
- **Controller duenn halten**: Security config in eigene Klassen, nicht in Controller
- **Alle Usages greppen**: Vor Aenderungen an FileStorageService.storeFromUrl() alle Aufrufer pruefen

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- Spring Boot managed dependencies, official docs verified
- Architecture: HIGH -- Two-profile SecurityFilterChain is well-documented pattern
- Pitfalls: HIGH -- CSRF, H2 console, dispatcher type issues are well-known
- Test strategy: HIGH -- Verified all 19 test classes use @ActiveProfiles("dev"), critical for D-07 decision

**Research date:** 2026-04-04
**Valid until:** 2026-05-04 (stable -- Spring Boot 4.0.x is mature)
