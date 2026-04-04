---
phase: 05-security
verified: 2026-04-04T12:00:00Z
status: passed
score: 10/10 must-haves verified
re_verification: false
---

# Phase 5: Security Verification Report

**Phase Goal:** Prod- und Docker-Umgebungen sind mit HTTP Basic Auth abgesichert, Dev/Local bleiben offen
**Verified:** 2026-04-04
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| #  | Truth                                                                                 | Status     | Evidence                                                                                          |
|----|---------------------------------------------------------------------------------------|------------|---------------------------------------------------------------------------------------------------|
| 1  | storeFromUrl() rejects URLs that do not start with https://                           | VERIFIED   | FileStorageService.java line 84: `!sourceUrl.toLowerCase().startsWith("https://")`               |
| 2  | storeFromUrl() throws IllegalArgumentException with descriptive message for non-HTTPS | VERIFIED   | FileStorageService.java line 87: `throw new IllegalArgumentException("Only HTTPS URLs allowed: " + sourceUrl)` |
| 3  | storeFromUrl() logs a warning when rejecting a URL                                    | VERIFIED   | FileStorageService.java line 85-86: `log.warn("Rejected non-HTTPS URL: {}", sourceUrl)`          |
| 4  | storeFromUrl() continues to work for valid HTTPS URLs                                 | VERIFIED   | Existing HTTPS test path unchanged; guard only fires when startsWith("https://") is false         |
| 5  | Admin URLs in prod/docker return HTTP 401 without credentials                         | VERIFIED   | SecurityConfig.java: `@Profile({"prod","docker"})` + `.anyRequest().authenticated()`             |
| 6  | Admin URLs in prod/docker return HTTP 200 with valid credentials                      | VERIFIED   | SecurityIntegrationTest: `givenValidCredentials_whenAccessAdmin_thenOk` with `@WithMockUser`     |
| 7  | Actuator health endpoint is accessible without authentication                         | VERIFIED   | SecurityConfig.java: `.requestMatchers("/actuator/health").permitAll()`                           |
| 8  | Admin URLs in dev/local profiles are accessible without authentication                | VERIFIED   | OpenSecurityConfig.java: `@Profile({"dev","local"})` + `.anyRequest().permitAll()`               |
| 9  | All existing tests remain green with Spring Security on the classpath                 | VERIFIED   | 753 tests pass — ./mvnw verify confirmed BUILD SUCCESS, 0 failures (provided as context)         |
| 10 | 403 Access Denied page renders in admin layout                                        | VERIFIED   | access-denied.html uses `th:replace="~{admin/layout :: layout(...)}"` + AccessDeniedController maps `/admin/access-denied` |

**Score:** 10/10 truths verified

### Required Artifacts

| Artifact                                                                             | Expected                                      | Status     | Details                                                               |
|--------------------------------------------------------------------------------------|-----------------------------------------------|------------|-----------------------------------------------------------------------|
| `src/main/java/org/ctc/domain/service/FileStorageService.java`                       | SSRF-protected storeFromUrl method            | VERIFIED   | Contains "Only HTTPS URLs allowed" guard at line 84-87               |
| `src/test/java/org/ctc/domain/service/FileStorageServiceTest.java`                   | Unit tests for URL schema validation          | VERIFIED   | Contains givenHttpUrl_, givenNullUrl_, givenFtpUrl_, givenEmptyUrl_  |
| `src/main/java/org/ctc/admin/SecurityConfig.java`                                    | Basic Auth SecurityFilterChain for prod/docker| VERIFIED   | `@Profile({"prod","docker"})`, httpBasic, .anyRequest().authenticated() |
| `src/main/java/org/ctc/admin/OpenSecurityConfig.java`                                | Permit-all SecurityFilterChain for dev/local  | VERIFIED   | `@Profile({"dev","local"})`, .anyRequest().permitAll()               |
| `src/test/java/org/ctc/admin/SecurityIntegrationTest.java`                           | Dedicated security integration tests          | VERIFIED   | Contains all 4 required test methods; nested classes for prod+dev    |
| `src/main/resources/templates/admin/access-denied.html`                              | 403 error page in admin layout                | VERIFIED   | Uses layout fragment, displays status/error/message, back-link to /admin/seasons |
| `src/main/java/org/ctc/admin/controller/AccessDeniedController.java`                 | Controller for /admin/access-denied           | VERIFIED   | `@GetMapping("/admin/access-denied")` with 403 model attributes      |

### Key Link Verification

| From                          | To                        | Via                              | Status   | Details                                                                     |
|-------------------------------|---------------------------|----------------------------------|----------|-----------------------------------------------------------------------------|
| FileStorageService.storeFromUrl() | IllegalArgumentException | URL scheme check before URI.create() | VERIFIED | Pattern `!sourceUrl.toLowerCase().startsWith("https://")` at line 84      |
| SecurityConfig                | httpBasic                 | @Profile({"prod","docker"})      | VERIFIED | `.httpBasic(Customizer.withDefaults())` at line 23                         |
| OpenSecurityConfig            | permitAll                 | @Profile({"dev","local"})        | VERIFIED | `.anyRequest().permitAll()` present in OpenSecurityConfig                  |
| SecurityConfig.accessDeniedPage | AccessDeniedController  | /admin/access-denied             | VERIFIED | `.accessDeniedPage("/admin/access-denied")` in SecurityConfig; `@GetMapping("/admin/access-denied")` in controller |
| docker-compose.yml            | Spring Security user      | SPRING_SECURITY_USER_NAME/PASSWORD | VERIFIED | Lines 24-25: `admin` / `ctc-admin` hardcoded for local Docker testing     |
| docker-compose.prod.yml       | Spring Security user      | ${CTC_ADMIN_USER}/${CTC_ADMIN_PASSWORD} | VERIFIED | Lines 9-10 reference env vars; .env.example documents them              |

### Data-Flow Trace (Level 4)

Not applicable — phase artifacts are security configuration classes and a minimal error page controller. No dynamic data rendering (no state variables, no DB queries, no user-facing data flow to trace).

### Behavioral Spot-Checks

| Behavior                                        | Command                                                                                                               | Result     | Status  |
|-------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------|------------|---------|
| SecurityConfig has correct profile annotation   | `grep -n "@Profile" src/main/java/org/ctc/admin/SecurityConfig.java`                                                 | `@Profile({"prod", "docker"})` | PASS |
| OpenSecurityConfig has correct profile annotation | `grep -n "@Profile" src/main/java/org/ctc/admin/OpenSecurityConfig.java`                                           | `@Profile({"dev", "local"})` | PASS  |
| SSRF guard present in FileStorageService        | `grep -n "Only HTTPS URLs allowed" src/main/java/org/ctc/domain/service/FileStorageService.java`                     | Line 84+87 | PASS    |
| Docker credentials in both compose files        | `grep "SPRING_SECURITY_USER" docker-compose.yml docker-compose.prod.yml`                                             | Found in both | PASS |
| Security dependency in pom.xml                  | `grep "spring-boot-starter-security" pom.xml`                                                                        | Lines 51+95 | PASS  |

### Requirements Coverage

| Requirement | Source Plan | Description                                                                     | Status    | Evidence                                                                                            |
|-------------|-------------|---------------------------------------------------------------------------------|-----------|-----------------------------------------------------------------------------------------------------|
| SECU-01     | 05-02, 05-03 | Spring Security Basic Auth aktiv fuer prod und docker Profile                  | SATISFIED | SecurityConfig.java: `@Profile({"prod","docker"})` + httpBasic; docker-compose files wired          |
| SECU-02     | 05-02        | Dev und local Profile bleiben ohne Authentifizierung                             | SATISFIED | OpenSecurityConfig.java: `@Profile({"dev","local"})` + `.anyRequest().permitAll()`                 |
| SECU-03     | 05-02        | Alle bestehenden @WebMvcTest Tests funktionieren mit Security auf Classpath      | SATISFIED | 753 tests pass (BUILD SUCCESS confirmed); OpenSecurityConfig covers dev profile used by test suite  |
| SECU-04     | 05-01        | FileStorageService.storeFromUrl() validiert URL-Schema (nur https)               | SATISFIED | Validation guard at line 84-87; 4 new unit tests (givenHttpUrl_, givenNullUrl_, givenFtpUrl_, givenEmptyUrl_) |

All 4 requirements satisfied. No orphaned requirements found.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (none) | — | — | — | — |

No TODO, FIXME, placeholder comments, empty implementations, or stub patterns found in any phase artifact.

### Human Verification Required

#### 1. Basic Auth Browser Prompt (prod/docker profile)

**Test:** Start app with docker compose (`docker compose up --build -d`), navigate to `http://localhost:8080/admin/seasons` in a browser.
**Expected:** Browser shows a native HTTP Basic Auth credential dialog. After entering `admin` / `ctc-admin`, the admin UI loads correctly.
**Why human:** Cannot test browser dialog behavior programmatically without a running Docker environment.

#### 2. Access Denied Page Visual Rendering

**Test:** Start app with dev profile. Hit `/admin/access-denied` directly in the browser.
**Expected:** Page renders within the admin layout (sidebar visible), shows "403 — Access Denied" heading, message text, and a functional "Back to Home" link to `/admin/seasons`.
**Why human:** Template rendering appearance requires visual inspection.

### Gaps Summary

No gaps. All must-haves verified at levels 1 (exists), 2 (substantive), 3 (wired). Phase goal is fully achieved.

---

_Verified: 2026-04-04_
_Verifier: Claude (gsd-verifier)_
