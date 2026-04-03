# Project Research Summary

**Project:** CTC Manager — Technical Debt Cleanup
**Domain:** Spring Boot 4 / Thymeleaf SSR admin application — architectural cleanup milestone
**Researched:** 2026-04-03
**Confidence:** HIGH

## Executive Summary

The CTC Manager is a mature single-admin Spring Boot 4.0.5 application with well-understood technical debt: six controllers bypass the service layer by injecting repositories directly, 65 catch-all exception blocks mask errors and break consistent UX, a 673-line God Service (RaceManagementService) violates SRP, and the application has zero authentication on any profile. The good news is that the existing architecture is already correct in its principles — documented in CLAUDE.md — and this milestone is about enforcing those principles consistently rather than introducing new patterns. No new architectural ideas are needed; the work is disciplined enforcement of what is already specified.

The recommended approach follows a strict bottom-up dependency order: build exception infrastructure first (GlobalExceptionHandler, custom exception types, meaningful orElseThrow calls), then complete the service layer (move repository access from controllers, split RaceManagementService), then clean up controllers (remove try-catch blocks, thin out TemplateEditorController), and finally add Spring Security as the last phase. This order is non-negotiable because adding security before the codebase is stable means debugging two layers of change simultaneously, and extracting services before the exception handler exists forces adding catch blocks into the new services.

The single highest-severity risk is Pitfall 1: adding `spring-boot-starter-security` to the classpath immediately breaks all 221 MockMvc calls with 401/302 responses. This will shatter the test suite if done before proper preparation. The mitigation is firm: security must be the final phase, introduced with test updates in the same commit. A secondary risk is breaking transactional boundaries when splitting RaceManagementService — the 12 `@Transactional` methods must be mapped before the split, and the orchestrating method must remain the transaction owner.

## Key Findings

### Recommended Stack

The existing stack (Spring Boot 4.0.5, Java 25, Thymeleaf, MariaDB/H2, Flyway) requires only two new dependencies: `spring-boot-starter-security` and `spring-boot-starter-security-test`. Everything else — exception handling, service refactoring, DB indexes — uses zero new libraries. Spring Security 7.0 (BOM-managed via Spring Boot 4.0.5) ships breaking API changes from older tutorials: `authorizeRequests()` is gone (use `authorizeHttpRequests()`), `AntPathRequestMatcher` is removed (use `PathPatternRequestMatcher`), and all config must use lambda DSL — the `and()` chaining method does not exist.

**Core technologies:**
- `spring-boot-starter-security` 7.0.x: HTTP Basic Auth for prod/docker profiles — simplest auth for a single-admin app, BOM-managed, no version override needed
- `spring-boot-starter-security-test`: `@WithMockUser` and CSRF support in MockMvc tests — required to fix all controller tests after security is added
- `@ControllerAdvice` (built-in): GlobalExceptionHandler — no new dependency, pure Spring MVC
- Flyway (existing): `V2__add_indexes.sql` for FK column indexes — additive-only, no V1 modification

### Expected Features

All items in this milestone are debt cleanup, not feature additions. The research categorized them as Table Stakes (must fix) and Differentiators (improves architecture for future velocity).

**Must have (table stakes):**
- T1: Extract controller repository access into services — 6+ controllers violate the documented architecture principle
- T2: Global exception handler via `@ControllerAdvice` — 65 catch-all blocks mask errors, users see raw stacktraces
- T3: Meaningful `.orElseThrow()` exceptions — 103 bare calls produce context-free NoSuchElementException
- T4: Specific exception types replacing catch-all blocks — programming errors (NPE, ClassCast) hidden behind generic messages
- T5: DB indexes on FK columns — dev/prod performance parity broken (H2 vs MariaDB auto-indexing difference)
- T6: TemplateEditorController duplication cleanup — 380 lines, 30+ identical try-catch blocks
- T7: Spring Security Basic Auth for prod/docker — zero authentication is the highest-severity concern
- T8: SSRF protection in FileStorageService.storeFromUrl() — arbitrary URL downloads with no validation
- T9: H2 console explicitly dev-only — fragile implicit safety
- T10: Stacktrace exposure disabled in prod — explicit over implicit configuration

**Should have (architectural quality):**
- D1: Split RaceManagementService God Service (673 lines, 13 deps) into RaceService, RaceGraphicService, RaceAttachmentService
- D2: Move StandingsController Swiss sorting logic into StandingsService
- D4: CompletableFuture error propagation in GT7 sync (surface failures, not silence them)
- D6: `@EntityGraph` preparation on hot-path repository queries (future N+1 prevention)
- D7: Pagination-ready repository methods (Pageable parameter, no UI changes)

**Defer (v2+):**
- D3: Alltime Standings — disable UI option now, implement cross-season aggregation in a feature milestone (feature work masquerading as debt)
- D5: Playwright health check — documentation/monitoring concern, not critical path
- Full pagination UI — repository preparation only; UI rework is out of scope
- OAuth2/OIDC — massive scope creep; Basic Auth is correct for single-admin app
- Bean validation on all DTOs — blanket constraints are a feature, not debt

### Architecture Approach

The cleanup enforces the existing layered MVC architecture documented in CLAUDE.md without introducing new patterns. The target state adds a Spring Security filter chain in front of controllers (prod/docker only), a GlobalExceptionHandler alongside the existing GlobalModelAdvice, and a completed service layer where all repository access is owned by services. RaceManagementService splits into three focused services using standard SRP decomposition. TemplateEditorController reduces from 380 lines to a Map-based dispatch pattern that preserves per-template error isolation.

**Major components:**
1. `SecurityConfig` / `DevSecurityConfig` — profile-conditional Basic Auth (prod/docker) vs permit-all (dev/local); only one active per environment
2. `GlobalExceptionHandler` — two exception types: `EntityNotFoundException` (show error page) and `BusinessRuleException` (redirect with flash message); catch-all `Exception` handler as last resort only
3. `org.ctc.domain.exception` package — custom `EntityNotFoundException(entityType, id)` and `BusinessRuleException(message)` replacing all bare `NoSuchElementException`/`IllegalStateException` throws
4. Expanded service layer — new `SeasonService`, `CarService`, `TrackService`, `RaceScoringService`, `MatchScoringService`; extended `DriverService`; split `RaceManagementService` into `RaceService`, `RaceGraphicService`, `RaceAttachmentService`
5. `V2__add_indexes.sql` — 10+ FK column indexes using `CREATE INDEX IF NOT EXISTS` for H2+MariaDB compatibility

### Critical Pitfalls

1. **Spring Security breaks all 221 MockMvc calls** — adding the security starter immediately fails all controller tests with 401/302. Prevention: add security as the final phase; update all test classes in the same commit with `@WithMockUser` and `.with(csrf()`); use profile-conditional security so dev/test remain open.

2. **Broken transactional boundaries when splitting RaceManagementService** — 12 `@Transactional` methods; moving them across service boundaries can cause partial data writes (results saved, scores not aggregated). Prevention: map all `@Transactional` methods before splitting; orchestrating method owns the tx boundary; extracted methods use default `REQUIRED` propagation; write rollback integration tests.

3. **GlobalExceptionHandler swallows controller flash messages** — a catch-all `@ExceptionHandler(Exception.class)` intercepts exceptions that previously drove redirect-with-flash UX. Prevention: only handle entity-not-found and unrecoverable errors globally; keep controller-level try-catch for form submission UX; refactor catch blocks in two stages (specific types first, then migrate to global).

4. **Flyway V2 index migration breaks H2** — MariaDB-specific syntax fails H2 test runs. Prevention: use `CREATE INDEX IF NOT EXISTS` (valid in both H2 2.x and MariaDB 10.5+); test on both databases; name indexes explicitly to avoid collisions with auto-generated FK index names.

5. **Coverage drops below 82% during service extraction** — moving logic from tested controllers to new untested services temporarily drops line coverage, failing the JaCoCo check. Prevention: write service tests before or simultaneously with extraction (TDD); batch related extractions (move + test + commit).

## Implications for Roadmap

Based on research, the dependency graph is firm and the phase order is prescribed by architecture constraints, not preference.

### Phase 1: Exception Infrastructure

**Rationale:** Must come first because all subsequent refactorings produce cleaner code when exceptions are handled centrally. Without this, extracting controller logic requires adding try-catch blocks to new services — making things worse before better.
**Delivers:** GlobalExceptionHandler, custom exception hierarchy (`EntityNotFoundException`, `BusinessRuleException`), meaningful `.orElseThrow()` messages across all 103 call sites, T9/T10 config hardening (trivial, high value).
**Addresses:** T2, T3, T9, T10
**Avoids:** Pitfall 3 (flash message loss — design the handler correctly from the start)

### Phase 2: Service Layer Completion

**Rationale:** Controllers cannot be thinned until services exist to delegate to. The God Service split is placed here because it requires clean exception types (from Phase 1) and because controller cleanup (Phase 3) needs the split to be complete.
**Delivers:** New service classes for Season, Car, Track, RaceScoring, MatchScoring; extended DriverService; RaceManagementService split into RaceService + RaceGraphicService + RaceAttachmentService; Swiss sorting moved to StandingsService.
**Addresses:** D1, D2, T1 (partial — establishes services before controller cleanup)
**Avoids:** Pitfall 2 (transactional boundaries — map @Transactional methods before splitting), Pitfall 8 (coverage — TDD every extraction)

### Phase 3: Controller Cleanup

**Rationale:** With exception infrastructure and services in place, controllers can be cleaned mechanically. This is the core architectural improvement — removing the 6 direct repository injections, eliminating 65 catch-all blocks, and refactoring TemplateEditorController.
**Delivers:** All 17 controllers comply with thin-controller principle; TemplateEditorController reduced to map-based dispatch; T4 (specific exception types); T6 (TemplateEditor cleanup).
**Addresses:** T1 (complete), T4, T6
**Avoids:** Pitfall 4 (update tests in same commit as each extraction), Pitfall 6 (per-template error isolation in loop)

### Phase 4: Security and Hardening

**Rationale:** Security is the highest-severity concern but must be last to avoid debugging security issues mixed with architectural issues. All 221 MockMvc calls must be updated in one coordinated commit.
**Delivers:** HTTP Basic Auth on prod/docker profiles; SSRF protection in FileStorageService; explicit H2 console config; stacktrace suppression in prod.
**Addresses:** T7, T8 (confirmed by T9/T10 already done in Phase 1)
**Avoids:** Pitfall 1 (the entire phase strategy exists to contain this risk), Pitfall 7 (log actual GT7 URLs before adding allowlist), Pitfall 11 (profile isolation prevents H2 console issue)

### Phase 5: Optimization Preparation and Loose Ends

**Rationale:** Independent changes with no sequencing dependencies relative to Phases 1-4. Low risk, additive only. Can begin after Phase 2 (services own the queries that need @EntityGraph).
**Delivers:** V2 Flyway migration with FK indexes; `@EntityGraph` on hot-path queries; pagination-ready repository methods; CompletableFuture error surfacing in GT7 sync; Alltime Standings UI option disabled.
**Addresses:** T5, D3, D4, D6, D7
**Avoids:** Pitfall 5 (H2/MariaDB index compatibility), Pitfall 9 (disable Alltime Standings, do not implement), Pitfall 10 (surface as warnings, not failures)

### Phase Ordering Rationale

- Exception infrastructure must precede service extraction because services need typed exceptions from day one; retrofitting exceptions after-the-fact doubles the work.
- Service layer must precede controller cleanup because controllers cannot delegate to services that do not exist.
- Security must be last because it is the only change that simultaneously affects all 221 MockMvc calls — isolating it prevents contaminating the red/green signal of other phases.
- Optimization preparation is independent and low-risk; it runs after Phase 2 when services own the queries.
- The FEATURES.md and ARCHITECTURE.md research agree on phase order independently — high confidence in the sequencing.

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 2 (Service Layer Completion):** RaceManagementService split boundary needs hands-on code analysis. The 13 dependencies must be mapped against actual method call graphs to confirm the RaceService / RaceGraphicService / RaceAttachmentService grouping. Plan should include a spike to confirm no circular dependencies.
- **Phase 4 (Security):** Spring Security 7 API surface (PathPatternRequestMatcher, lambda DSL) should be verified against actual Spring Boot 4.0.5 dependency tree at implementation time. CSRF decision (disabled initially vs enabled with Thymeleaf integration) needs a documented rationale in SecurityConfig.

Phases with standard patterns (skip research-phase):
- **Phase 1 (Exception Infrastructure):** `@ControllerAdvice` / `@ExceptionHandler` is core Spring MVC unchanged across versions. Two exception types, one handler class — straightforward implementation.
- **Phase 3 (Controller Cleanup):** Mechanical refactoring of known patterns. No architectural decisions; just enforcement of documented CLAUDE.md principles.
- **Phase 5 (Optimization Prep):** `CREATE INDEX IF NOT EXISTS`, `@EntityGraph`, `Pageable.unpaged()` — all well-documented, low-risk, additive changes.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Two new dependencies (security + security-test), both BOM-managed. Spring Security 7 API changes verified against official migration guide. |
| Features | HIGH | Derived from direct codebase analysis (CONCERNS.md with 17 identified issues, line/method counts). Not inferred — measured. |
| Architecture | HIGH | Enforces documented CLAUDE.md principles. Thin controller, service-owns-repositories, and GlobalExceptionHandler are standard Spring MVC patterns with extensive documentation. |
| Pitfalls | HIGH | Spring Security test breakage and @Transactional proxy behavior are well-documented, not speculative. Concrete numbers (221 MockMvc calls, 12 @Transactional methods) confirm severity. |

**Overall confidence:** HIGH

### Gaps to Address

- **RaceManagementService exact split boundary:** The three-way split (RaceService / RaceGraphicService / RaceAttachmentService) is the right shape but the exact method allocation needs implementation-time validation. Some methods may not fit cleanly — plan for one iteration of adjustment.
- **SSRF allowlist for GT7 CDN:** The exact CDN hosts used by gran-turismo.com are not documented in the research. Must grep actual sync URLs in a dev run before coding the allowlist. If CDN uses dynamic hosts, scheme-only validation (`https://`) may be the only safe option.
- **CSRF decision for Thymeleaf forms:** Disabling CSRF is recommended for the initial implementation but the interaction with multipart form uploads (CSV import) and Thymeleaf `th:action` CSRF token injection needs testing. Document the decision explicitly in SecurityConfig.
- **`@WebMvcTest` migration path:** Current tests use `@SpringBootTest`. If the team later migrates to `@WebMvcTest` for speed, the security mock pattern (`@WithMockUser`) will be required. Flag this in test comments but do not change test infrastructure during the cleanup milestone.

## Sources

### Primary (HIGH confidence)
- Spring Security 7.0 What's New — verified API removals (authorizeRequests, AntPathRequestMatcher, and() method)
- Spring Security Basic Auth Reference — official httpBasic() configuration
- Spring Boot 4.0 Release Notes — confirmed Security 7.0 as BOM-managed dependency
- Spring Security 7 Web Migration Guide — PathPatternRequestMatcher builder pattern
- Project CLAUDE.md — architecture principles (authoritative for this project)
- `.planning/codebase/CONCERNS.md` — 17 identified concerns with severity ratings and concrete metrics

### Secondary (MEDIUM confidence)
- Baeldung: Disable Security for Profile — profile-based security pattern
- Baeldung: Spring Security Integration Tests — `@WithMockUser` and MockMvc patterns
- Vlad Mihalcea: Spring @Transactional best practices — proxy-based @Transactional behavior and cross-service boundary risks
- reflectoring.io: Spring Boot Exception Handling — @ControllerAdvice design patterns

### Tertiary (LOW confidence)
- GT7 CDN URL patterns — not verified; requires dev run to confirm actual hosts before SSRF allowlist implementation
- `thymeleaf-extras-springsecurity7` version compatibility with Spring Boot 4.0.5 — exists but version alignment needs verification at implementation time (skipped as not needed for initial scope)

---
*Research completed: 2026-04-03*
*Ready for roadmap: yes*
