---
phase: "74"
plan: "06"
subsystem: "backup"
tags: [spring-boot, multipart, exception-handler, flash-redirect, backup, security, tdd]
depends_on:
  requires: ["74-01"]
  provides: ["MaxUploadSizeExceededException → Flash-redirect mapping (SECU-04)"]
  affects: ["BackupController", "GlobalExceptionHandler (read-only)"]
tech_stack:
  added: ["@ControllerAdvice with @Order(HIGHEST_PRECEDENCE)", "HttpURLConnection-based IT for real Tomcat multipart enforcement"]
  patterns: ["Sibling @ControllerAdvice for mixed return-type isolation (D-14)", "RANDOM_PORT + HttpURLConnection for Servlet-container limit testing"]
key_files:
  created:
    - src/main/java/org/ctc/backup/exception/BackupUploadExceptionHandler.java
    - src/test/java/org/ctc/backup/exception/BackupImportMultipartLimitIT.java
  modified: []
decisions:
  - "D-14: Separate @ControllerAdvice (BackupUploadExceptionHandler) — not mixed into GlobalExceptionHandler which returns ModelAndView throughout"
  - "@Order(HIGHEST_PRECEDENCE) required: without it, GlobalExceptionHandler.handleGeneral(Exception.class) catches MaxUploadSizeExceededException first"
  - "RANDOM_PORT + HttpURLConnection: MockMvc bypasses Tomcat's multipart size enforcement; real HTTP connection required to trigger MaxUploadSizeExceededException"
  - "HttpURLConnection with setInstanceFollowRedirects(false): no additional HTTP client dependency; works with plain Spring 7 test classpath"
metrics:
  duration: "~25 minutes"
  completed: "2026-05-12"
  tasks: 2
  files: 2
requirements: [SECU-04]
---

# Phase 74 Plan 06: BackupUploadExceptionHandler — MaxUploadSizeExceededException Flash redirect

## One-liner

New `@ControllerAdvice` with `@Order(HIGHEST_PRECEDENCE)` mapping `MaxUploadSizeExceededException` → `redirect:/admin/backup` with locked D-02#1 Flash string `Upload too large — maximum is 100 MB.` (U+2014); proven by `BackupImportMultipartLimitIT` using real Tomcat via `WebEnvironment.RANDOM_PORT`.

## Objective

Wire `MaxUploadSizeExceededException` (thrown by the Spring Multipart resolver after Plan 01 raised `spring.servlet.multipart.max-request-size` to `100MB`) to a clean Flash-redirect — replacing the default Tomcat stack-trace error page (information disclosure) with a single English sentence on the existing backup landing page (SECU-04).

## Artifacts Produced

| File | Role | Lines |
|------|------|-------|
| `src/main/java/org/ctc/backup/exception/BackupUploadExceptionHandler.java` | New `@ControllerAdvice` — `MaxUploadSizeExceededException` → Flash-redirect | 44 |
| `src/test/java/org/ctc/backup/exception/BackupImportMultipartLimitIT.java` | Failsafe IT — oversized-rejection + selectivity guard | 120 |

## Decisions Made

### D-14: Separate `@ControllerAdvice` class

`GlobalExceptionHandler` returns `ModelAndView` throughout. Adding a `String "redirect:..."` handler there mixes return types within a single advice class — fragile under Spring's handler-resolution ordering. The plan mandates a sibling class: `BackupUploadExceptionHandler` in `org.ctc.backup.exception`.

### `@Order(HIGHEST_PRECEDENCE)` needed

Without `@Order`, Spring's advice ordering is non-deterministic between `BackupUploadExceptionHandler` (specific: `MaxUploadSizeExceededException`) and `GlobalExceptionHandler` (broad: `Exception.class`). In practice the broad handler won first. `@Order(Ordered.HIGHEST_PRECEDENCE)` guarantees `BackupUploadExceptionHandler` is evaluated first.

### `WebEnvironment.RANDOM_PORT` + `HttpURLConnection` for IT

`MockMvc` dispatches directly through Spring MVC's `DispatcherServlet` without involving Tomcat's multipart resolver. The `MaxUploadSizeExceededException` is thrown at the Servlet-container layer (during request parsing, before controller dispatch) — which is never invoked in `MockMvc` mode. A real HTTP connection through an embedded Tomcat instance (`RANDOM_PORT`) is required to trigger the exception.

`HttpURLConnection` with `setInstanceFollowRedirects(false)` was chosen over `RestTemplate`/`TestRestTemplate` because:
- `spring-boot-resttestclient` auto-configuration requires `spring-boot-restclient` (not present in this project)
- `SimpleClientHttpRequestFactory` in Spring 7 dropped `setOutputStreaming` and has no redirect-disable API
- `HttpURLConnection` is zero-dependency and gives direct control over `InstanceFollowRedirects`

## Deviations from Plan

### [Rule 1 - Bug] MockMvc cannot trigger MaxUploadSizeExceededException

**Found during:** Task 06.1 RED verification
**Issue:** The plan specified `@SpringBootTest @AutoConfigureMockMvc` with `MockMultipartFile`. `MockMvc` bypasses Tomcat's multipart resolver entirely — `MaxUploadSizeExceededException` is never thrown. Test ran but the 101 MB upload went through without rejection (resulted in a 404/500, not the expected redirect).
**Fix:** Switched Test 1 to `WebEnvironment.RANDOM_PORT` + `HttpURLConnection` with redirect-following disabled. Test 2 (selectivity guard) retains `MockMvc` since it only checks the absence of the Flash attribute.
**Files modified:** `BackupImportMultipartLimitIT.java`
**Commit:** 6f35ce7

### [Rule 2 - Missing Critical] `@Order(HIGHEST_PRECEDENCE)` required

**Found during:** Task 06.2 GREEN verification
**Issue:** `GlobalExceptionHandler.handleGeneral(Exception.class)` caught `MaxUploadSizeExceededException` before `BackupUploadExceptionHandler` could intercept it. Without explicit ordering, the broad catch-all won.
**Fix:** Added `@Order(Ordered.HIGHEST_PRECEDENCE)` to `BackupUploadExceptionHandler`. This ensures it is evaluated before `GlobalExceptionHandler` for any exception type it explicitly handles.
**Files modified:** `BackupUploadExceptionHandler.java`
**Commit:** 6f35ce7

## Verification Results

### Plan-Level Gates

| Gate | Check | Result |
|------|-------|--------|
| 1 | `BackupImportMultipartLimitIT`: Tests run: 2, Failures: 0 | PASS |
| 2 | `git diff --exit-code GlobalExceptionHandler.java` = empty | PASS |
| 3 | `git diff --exit-code GlobalExceptionHandlerTest.java` = empty | PASS |
| 4 | D-02#1 string appears exactly 1x in `src/main/java` | PASS (count=1) |
| 5 | No `log.error`/`log.fatal` in `BackupUploadExceptionHandler` | PASS (count=0) |
| 6 | `./mvnw verify`: 3204 tests, Failures: 0, JaCoCo ≥ 82% | PASS |

### WARN log observed during Test 1

```
WARN o.c.b.e.BackupUploadExceptionHandler : Multipart upload rejected: max-size exceeded — uri=/admin/backup/import-preview, limit=-1
```

`limit=-1` is expected — Spring's `MaxUploadSizeExceededException.getMaxUploadSize()` returns `-1` when the limit is enforced at the Tomcat connector level (which reads from `server.tomcat.max-http-form-post-size`) rather than via Spring's multipart properties. This is not a defect.

## Known Stubs

None — the new `BackupUploadExceptionHandler` contains no placeholder values. The locked D-02#1 Flash string is hard-coded and verified by IT.

## Threat Surface Scan

No new network endpoints, auth paths, file access patterns, or schema changes introduced. The handler only intercepts an existing exception type and redirects to an existing URL — no new trust boundary.

## TDD Gate Compliance

| Gate | Commit | Status |
|------|--------|--------|
| RED: failing IT | `77933e5` `test(74-06): add failing BackupImportMultipartLimitIT (RED)` | PASS |
| GREEN: implementation | `6f35ce7` `feat(74-06): implement BackupUploadExceptionHandler (GREEN)` | PASS |
| REFACTOR | not needed (37-line class, zero complexity) | N/A |
