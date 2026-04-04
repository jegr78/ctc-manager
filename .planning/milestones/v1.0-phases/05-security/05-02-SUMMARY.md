---
phase: 05-security
plan: 02
subsystem: auth
tags: [spring-security, basic-auth, http-security, profile-conditional]

# Dependency graph
requires:
  - phase: 01-exception-handling
    provides: GlobalExceptionHandler for error pages
provides:
  - SecurityFilterChain with Basic Auth for prod/docker profiles
  - OpenSecurityConfig permit-all for dev/local profiles
  - Security integration tests (4 tests)
  - logback-test.xml for test classpath
affects: [05-03-ssrf-hardening]

# Tech tracking
tech-stack:
  added: [spring-boot-starter-security, spring-boot-starter-security-test]
  patterns: [profile-conditional SecurityFilterChain, nested @SpringBootTest with profile overrides]

key-files:
  created:
    - src/main/java/org/ctc/admin/SecurityConfig.java
    - src/main/java/org/ctc/admin/OpenSecurityConfig.java
    - src/test/java/org/ctc/admin/SecurityIntegrationTest.java
    - src/test/resources/logback-test.xml
  modified:
    - pom.xml

key-decisions:
  - "Two separate @Configuration classes with @Profile instead of single config with runtime profile check"
  - "CSRF disabled in both profiles per project decision (Out of Scope in REQUIREMENTS.md)"
  - "H2 Console frame options disabled in dev/local OpenSecurityConfig"
  - "Prod tests use H2 + logback-test.xml override to avoid /app/logs and MariaDB dependency"

patterns-established:
  - "Profile-conditional security: @Profile on @Configuration class, not runtime checks"
  - "Security test pattern: nested @SpringBootTest classes with different @ActiveProfiles"

requirements-completed: [SECU-01, SECU-02, SECU-03]

# Metrics
duration: 6min
completed: 2026-04-04
---

# Phase 5 Plan 2: Spring Security Basic Auth Summary

**Spring Security HTTP Basic Auth for prod/docker with profile-conditional SecurityFilterChain, dev/local permit-all, all 664 tests green**

## Performance

- **Duration:** 6 min
- **Started:** 2026-04-04T11:12:57Z
- **Completed:** 2026-04-04T11:19:04Z
- **Tasks:** 4
- **Files modified:** 5

## Accomplishments
- Spring Security Basic Auth active for prod/docker profiles (SECU-01)
- Dev/local profiles remain fully open without authentication (SECU-02)
- All 664 existing + new tests pass with Spring Security on classpath (SECU-03)
- 4 dedicated security integration tests verify all scenarios

## Task Commits

Each task was committed atomically:

1. **Task 1: Add Spring Security dependencies** - `cf8d348` (chore)
2. **Task 2: Write security integration tests (TDD RED)** - `7ece921` (test)
3. **Task 3: SecurityConfig for prod/docker (TDD GREEN)** - `aa401b8` (feat)
4. **Task 4: OpenSecurityConfig for dev/local + full verify** - `1ad23f5` (feat)

## Files Created/Modified
- `pom.xml` - Added spring-boot-starter-security and security-test dependencies
- `src/main/java/org/ctc/admin/SecurityConfig.java` - Basic Auth SecurityFilterChain for prod/docker
- `src/main/java/org/ctc/admin/OpenSecurityConfig.java` - Permit-all SecurityFilterChain for dev/local
- `src/test/java/org/ctc/admin/SecurityIntegrationTest.java` - 4 security integration tests
- `src/test/resources/logback-test.xml` - Console-only logback config for tests

## Decisions Made
- Two separate @Configuration classes with @Profile annotations (cleanest Spring Security 7 pattern)
- CSRF disabled in both profiles (project decision, single-admin app)
- Frame options disabled in dev/local for H2 Console access
- Prod profile tests override datasource to H2 and logging to console-only (avoids /app/logs Docker path and MariaDB requirement)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Fixed AutoConfigureMockMvc import for Spring Boot 4**
- **Found during:** Task 2 (security integration tests)
- **Issue:** Plan used `org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc` which moved to `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` in Spring Boot 4
- **Fix:** Updated import to correct package
- **Files modified:** src/test/java/org/ctc/admin/SecurityIntegrationTest.java
- **Verification:** test-compile passes
- **Committed in:** 7ece921 (Task 2 commit)

**2. [Rule 3 - Blocking] Prod profile test context fails due to /app/logs and missing MariaDB**
- **Found during:** Task 3 (SecurityConfig verification)
- **Issue:** @ActiveProfiles("prod") activates logback-spring.xml FILE appender to /app/logs/app.log (Docker path) and requires MariaDB datasource env vars
- **Fix:** Added @SpringBootTest properties to override datasource to H2, created logback-test.xml with console-only logging
- **Files modified:** SecurityIntegrationTest.java, src/test/resources/logback-test.xml
- **Verification:** Prod security tests pass with H2 backend
- **Committed in:** aa401b8 (Task 3 commit)

---

**Total deviations:** 2 auto-fixed (2 blocking issues)
**Impact on plan:** Both fixes necessary to run tests in CI/local environment. No scope creep.

## Issues Encountered
None beyond the auto-fixed deviations above.

## Known Stubs
None - all functionality is fully wired.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Security foundation complete, ready for Plan 3 (SSRF hardening)
- Credentials configured via env vars SPRING_SECURITY_USER_NAME / SPRING_SECURITY_USER_PASSWORD (Spring Boot auto-config)
- Docker Compose files will need env var entries for credentials (Plan 3 or separate task)

---
*Phase: 05-security*
*Completed: 2026-04-04*
