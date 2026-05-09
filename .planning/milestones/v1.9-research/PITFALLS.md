# Domain Pitfalls

**Domain:** Spring Boot Technical Debt Cleanup (Service Refactoring, Security, Exception Handling)
**Project:** CTC Manager (Spring Boot 4.0.5, Java 25, Thymeleaf, MariaDB/H2)
**Researched:** 2026-04-03

## Critical Pitfalls

Mistakes that cause mass test failures, data corruption, or multi-day rework.

### Pitfall 1: Adding Spring Security Breaks All 221 MockMvc Calls

**What goes wrong:** Adding `spring-boot-starter-security` to `pom.xml` immediately secures ALL endpoints. Every one of the 221 `mockMvc.perform()` calls across 19 controller test classes will fail with `401 UNAUTHORIZED` or `302 REDIRECT`. Spring Boot 4.x / Spring Security 7 secures everything by default -- there is no "partially secured" state.

**Why it happens:** Spring Security's auto-configuration activates the moment the starter is on the classpath. The 26 test classes using `@SpringBootTest` or `@WebMvcTest` will pick up the security filter chain. POST/PUT/DELETE requests will additionally fail CSRF validation.

**Consequences:** 628+ tests go red simultaneously. CI is broken until every test is fixed. Developers cannot verify other refactoring work in parallel.

**Prevention:**
1. Add Spring Security as the LAST phase, after all service extraction and exception handling is stable.
2. When adding security, simultaneously update ALL test classes in the same commit:
   - Add `spring-boot-starter-security-test` dependency.
   - Add `@WithMockUser` annotation to every controller test class (or a shared base class).
   - Add `.with(csrf())` to every POST/PUT/DELETE MockMvc call.
3. Use profile-conditional security configuration: `@Profile({"prod", "docker"})` on the `SecurityFilterChain` bean so dev/test profiles remain open.
4. Alternative: Use a `SecurityFilterChain` that permits all in dev/test but secures in prod/docker, so tests do not need `@WithMockUser`.

**Detection:** Run `./mvnw verify` immediately after adding the security starter -- if more than 0 tests fail, the security config is not test-compatible.

**Confidence:** HIGH -- confirmed by Spring Boot 4.x issue tracker and Spring Security 7 migration docs.

**Phase relevance:** Must be the final cleanup phase. Never mix with service refactoring.

---

### Pitfall 2: Breaking Transactional Boundaries When Splitting RaceManagementService

**What goes wrong:** When extracting methods from `RaceManagementService` (673 lines, 12 `@Transactional` methods) into new services (e.g., `RaceGraphicService`, `RaceAttachmentService`), the transactional context can silently change. A method that previously ran within a single transaction now calls across service boundaries, and Spring's proxy-based `@Transactional` only works on external calls through the proxy.

**Why it happens:** Spring's `@Transactional` is proxy-based. If `RaceManagementService.saveResults()` calls `this.aggregateScores()` internally, it runs in the same transaction. After extracting `aggregateScores()` to `ScoringService`, the call goes through the proxy and gets its own transaction (default `REQUIRED` propagation joins, but `REQUIRES_NEW` would not). More critically, if the new service method throws and the calling method catches the exception, the transaction may already be marked rollback-only.

**Consequences:** Partial data writes. Race results saved but scores not aggregated (or vice versa). Subtle bugs that only manifest under error conditions, not in happy-path tests.

**Prevention:**
1. Map every `@Transactional` method in `RaceManagementService` before splitting. Document which operations must remain atomic.
2. After splitting, the orchestrating method (the one that calls multiple services) should own the `@Transactional` boundary. Extracted service methods should use `@Transactional(propagation = REQUIRED)` (the default) so they join the existing transaction.
3. Write integration tests that verify atomicity: save results + aggregate scores in one call, then trigger a failure in the second step and verify both rolled back.
4. Never use `@Transactional(propagation = REQUIRES_NEW)` on the extracted methods unless you explicitly want independent transactions.

**Detection:** Integration tests that assert rollback behavior. Check for `UnexpectedRollbackException` in logs.

**Confidence:** HIGH -- this is a well-documented Spring behavior. The 12 `@Transactional` methods in `RaceManagementService` make this a concrete risk.

**Phase relevance:** Service extraction phase. Must be addressed during RaceManagementService split.

---

### Pitfall 3: Global Exception Handler Swallows Controller-Level Flash Messages

**What goes wrong:** Adding a `@ControllerAdvice` with `@ExceptionHandler(Exception.class)` intercepts exceptions that were previously caught by the 65 `catch(Exception e)` blocks in controllers. If the migration is incremental (add global handler first, then remove try-catch blocks), there is a transition period where both exist. But if the global handler is too broad, it catches exceptions from controllers that intentionally throw after setting flash attributes, breaking the redirect-with-flash-message pattern.

**Why it happens:** `@ExceptionHandler` methods in `@ControllerAdvice` cannot access `RedirectAttributes` or the flash attribute map. They receive the exception after Spring MVC's redirect handling is no longer available. The existing pattern in controllers (catch exception, add flash error message, redirect back to form) cannot be replicated in a global handler without significant rework.

**Consequences:** User sees a generic error page instead of the contextual "Operation failed" flash message on the form they were using. UX regression across all CRUD operations.

**Prevention:**
1. Do NOT add a catch-all `@ExceptionHandler(Exception.class)` in the global handler. Only handle specific exception types that represent unrecoverable errors: `NoSuchElementException` (entity not found), `AccessDeniedException`, `MethodArgumentNotValidException`.
2. Keep the controller-level try-catch blocks for operations where flash messages + redirect are the correct UX (form submissions, CRUD operations). These are intentional error handling, not technical debt.
3. The global handler should only catch exceptions that escape controllers -- the "last line of defense" for unhandled errors.
4. Refactor the 65 catch blocks in two stages: (a) Replace generic `Exception` catches with specific types (`IOException`, `IllegalArgumentException`). (b) Only then consider which of those can move to the global handler.

**Detection:** After adding the global handler, test every CRUD operation with invalid data. If flash messages disappear, the handler is too broad.

**Confidence:** HIGH -- the existing 65 `catch(Exception e)` blocks are all in controllers using `RedirectAttributes`. The `@ExceptionHandler` documentation explicitly states `Model` is not available in handler methods.

**Phase relevance:** Exception handling phase. Must understand the controller flash-message pattern before designing the global handler.

---

## Moderate Pitfalls

### Pitfall 4: Extracting Repository Calls From Controllers Without Matching Test Expectations

**What goes wrong:** When moving `seasonRepository.findById(id).orElseThrow()` from `SeasonController` to `SeasonService.getById(id)`, existing `@SpringBootTest` tests that inject repositories directly to set up test data continue to pass. But `@WebMvcTest` tests (if any are later added) will fail because they mock the service layer, and the mock expectations have changed.

**Prevention:**
1. For each controller being refactored, update the test in the same commit. The test should verify the controller calls the service, not the repository.
2. The 103 `.orElseThrow()` calls without messages should get meaningful exceptions during extraction: `seasonService.getById(id)` internally throws `EntityNotFoundException("Season not found: " + id)`.
3. Do not change test infrastructure (e.g., switching from `@SpringBootTest` to `@WebMvcTest`) during the same refactoring phase. One change at a time.

**Detection:** Any test that directly references a repository that was removed from a controller is a test that needs updating.

**Confidence:** MEDIUM -- the current tests all use `@SpringBootTest` with real repositories, which absorbs the change. Risk increases if tests are later migrated to `@WebMvcTest`.

### Pitfall 5: Flyway Migration for Indexes Breaks H2 Compatibility

**What goes wrong:** Adding `V2__add_indexes.sql` with MariaDB-specific syntax breaks H2 tests. MariaDB auto-creates indexes for foreign keys; H2 does not. If the migration uses `CREATE INDEX IF NOT EXISTS` (MariaDB syntax), H2 may not support the same syntax. Conversely, if you use standard SQL `CREATE INDEX`, MariaDB may fail with "Duplicate key name" because the FK index already exists.

**Prevention:**
1. Use `CREATE INDEX IF NOT EXISTS` -- this is valid in both H2 (since 2.x) and MariaDB 10.5+.
2. Test the migration against both databases: `./mvnw verify` (H2) and local MariaDB (`./mvnw spring-boot:run -Dspring-boot.run.profiles=local`).
3. Name indexes explicitly (e.g., `idx_races_matchday_id`) to avoid collisions with auto-generated FK index names.
4. MariaDB names auto-created FK indexes after the constraint name. Use different names for the explicit indexes.

**Detection:** `./mvnw verify` fails with SQL syntax errors after adding the migration.

**Confidence:** HIGH -- H2/MariaDB SQL dialect differences are well-documented. The project already uses both.

### Pitfall 6: TemplateEditorController Refactoring Breaks Template Load Order

**What goes wrong:** The `TemplateEditorController` has 30+ identical try-catch blocks loading templates individually. When refactoring to a loop-based pattern (iterate over a map of template types to services), a single template load failure can prevent the entire page from rendering, whereas the current pattern gracefully degrades (one broken template does not affect others).

**Prevention:**
1. The loop-based approach must catch exceptions per-template, not around the entire loop. Each template load should be independently error-handled.
2. Use a `Map<String, Supplier<String>>` or similar pattern where each entry is independently evaluated and failures are collected, not thrown.
3. Keep the existing behavior as the acceptance criterion: if template X fails to load, templates Y and Z still render.

**Detection:** Test loading the template editor page with a deliberately corrupted template file. All other templates should still render.

**Confidence:** HIGH -- the current code explicitly handles each template independently.

### Pitfall 7: SSRF Fix Breaks GT7 Image Sync

**What goes wrong:** Adding URL scheme validation or host allowlisting to `FileStorageService.storeFromUrl()` inadvertently blocks legitimate GT7 image sync URLs. The GT7 scraper downloads images from `gran-turismo.com` CDN, which may use subdomains or CDN hosts that do not match a naive allowlist.

**Prevention:**
1. Before implementing the fix, grep all callers of `storeFromUrl()` and log the actual URLs being fetched in a dev run with GT7 sync enabled.
2. Implement scheme validation (`https://` only) as a first step -- this is safe and blocks the SSRF vector (no `file://`, `ftp://`, internal IPs).
3. If adding host allowlisting, make it configurable via `application.yml` rather than hardcoded, so it can be updated without code changes.
4. Add an integration test that verifies GT7 sync still works after the fix.

**Detection:** Run a GT7 sync after the fix. If images stop downloading, the allowlist is too restrictive.

**Confidence:** MEDIUM -- depends on exact CDN URLs used by gran-turismo.com.

## Minor Pitfalls

### Pitfall 8: Coverage Drops Below 82% During Service Extraction

**What goes wrong:** Extracting logic from controllers (which have tests) into new service classes (which start with no tests) temporarily drops line coverage. The logic moves from tested code to untested code until service-level tests are written. The JaCoCo check fails the build.

**Prevention:**
1. For each extraction, write the service test BEFORE or simultaneously with the extraction. TDD approach: write the service test, create the service method, move the logic.
2. Track coverage per-commit. If coverage is at 82.1% and you extract 50 lines without tests, it may drop to 81.8%.
3. Batch related extractions: move DriverController repository calls to DriverService, write DriverService tests, then move on.

**Detection:** `./mvnw verify` fails with JaCoCo coverage below 82%.

### Pitfall 9: Alltime Standings -- Implement vs Disable Decision Paralysis

**What goes wrong:** The Alltime Standings feature has a TODO placeholder returning an empty list. The cleanup scope says "implement or disable." Implementing cross-season aggregation is a significant feature (not debt cleanup). Disabling is trivial but feels like regression. The decision stalls.

**Prevention:**
1. Disable the UI option in this milestone. Add a comment explaining it is deferred to the next feature cycle.
2. Do not implement cross-season aggregation during a debt cleanup milestone. It requires new queries, new service logic, and new tests -- that is feature work.
3. Disabling means: hide the "Alltime" option in the standings dropdown/tab, not deleting the code. A `TODO` comment with a future milestone reference is sufficient.

**Detection:** If anyone spends more than 30 minutes on this item, it has become feature work and should be deferred.

### Pitfall 10: CompletableFuture Error Collection Changes Sync Behavior

**What goes wrong:** Currently, GT7 sync silently logs failures and reports overall success. Changing this to collect and surface errors may cause the sync operation to appear "broken" to the admin user, when in reality it always had partial failures that were hidden.

**Prevention:**
1. Surface errors as warnings, not failures. The sync summary should show "Synced 145/150 images (5 failed: [list])" rather than throwing an exception.
2. Do not change the method return type from void to a result object in a way that breaks existing callers. Add the result information as an optional enhancement.

**Detection:** Run a full GT7 sync and compare behavior before/after the change.

### Pitfall 11: H2 Console Security -- Explicit Disable May Conflict With Profile Override

**What goes wrong:** Setting `spring.h2.console.enabled: false` in the base `application.yml` and `true` only in `application-dev.yml` is correct. But if Spring Security is also active and does not permit `/h2-console/**`, the console becomes inaccessible even in dev, causing confusion.

**Prevention:**
1. Since security is only active in prod/docker profiles, this is not a real issue IF security is properly profile-scoped.
2. Document the interaction: H2 console requires both `spring.h2.console.enabled: true` AND no security filter blocking it.

**Detection:** After adding security, verify H2 console is still accessible in dev profile.

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|-------------|---------------|------------|
| Service extraction (Controller cleanup) | Coverage drops below 82% | Write service tests before/during extraction, not after |
| Service extraction (RaceManagementService split) | Broken transactional boundaries | Map all @Transactional methods first; keep orchestrating method as tx owner |
| Exception handling (@ControllerAdvice) | Flash messages lost on form errors | Only handle entity-not-found and unrecoverable errors globally; keep controller catch blocks for form UX |
| Exception handling (.orElseThrow) | 103 call sites to update | Batch by service: update all orElseThrow in one service at a time, with meaningful messages |
| Spring Security addition | 221 MockMvc calls fail | Do this LAST; update all tests in same commit; use profile-conditional security |
| Flyway migration (indexes) | H2/MariaDB syntax mismatch | Use CREATE INDEX IF NOT EXISTS; test on both databases |
| TemplateEditor refactoring | Single failure breaks entire page | Per-template error handling in loop; match existing graceful degradation |
| SSRF fix | GT7 sync breaks | Log actual URLs first; scheme validation before host allowlist |
| Alltime Standings | Feature creep into debt milestone | Disable UI only; defer implementation to feature milestone |

## Recommended Phase Ordering (Based on Pitfall Analysis)

1. **Service extraction first** -- Moves logic to services, establishes clean boundaries. Pitfalls are manageable with TDD.
2. **Exception handling second** -- Requires stable service layer to know which exceptions services throw. Global handler design depends on knowing the exception taxonomy.
3. **Database/infrastructure fixes third** -- Indexes, OSIV prep, pagination prep. Low risk, independent.
4. **Security LAST** -- Touches every test. Requires stable codebase to avoid debugging security issues mixed with refactoring issues.

## Sources

- [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [Spring Security 7 Migration Index](https://docs.spring.io/spring-security/reference/migration/index.html)
- [Spring @WebMvcTest security behavior change in 4.x](https://github.com/spring-projects/spring-framework/issues/36423)
- [Spring @Transactional annotation best practices - Vlad Mihalcea](https://vladmihalcea.com/spring-transactional-annotation/)
- [Spring Security testing with MockMvc](https://docs.spring.io/spring-security/reference/servlet/test/mockmvc/setup.html)
- [Spring MVC Exception Handling](https://spring.io/blog/2013/11/01/exception-handling-in-spring-mvc/)
- [Baeldung: Spring Security Integration Tests](https://www.baeldung.com/spring-security-integration-tests)
- Project analysis: 103 `.orElseThrow()` calls, 65 `catch(Exception e)` blocks, 221 MockMvc calls, 12 `@Transactional` methods in RaceManagementService

---

*Concerns audit: 2026-04-03*
