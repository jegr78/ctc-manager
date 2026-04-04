---
phase: 05-security
plan: 01
subsystem: security
tags: [ssrf, url-validation, file-storage]

# Dependency graph
requires: []
provides:
  - SSRF-protected FileStorageService.storeFromUrl() method
  - URL scheme validation (HTTPS-only) with logging
affects: [gt7sync, dataimport]

# Tech tracking
tech-stack:
  added: []
  patterns: [url-scheme-guard-clause-before-io]

key-files:
  created: []
  modified:
    - src/main/java/org/ctc/domain/service/FileStorageService.java
    - src/test/java/org/ctc/domain/service/FileStorageServiceTest.java

key-decisions:
  - "Guard clause with log.warn before IllegalArgumentException for security monitoring"
  - "Existing file:// URI test updated to verify rejection (was using local file URI)"

patterns-established:
  - "URL validation: scheme check as first guard clause before any I/O operation"

requirements-completed: [SECU-04]

# Metrics
duration: 3min
completed: 2026-04-04
---

# Phase 05 Plan 01: SSRF URL Validation Summary

**SSRF protection via HTTPS-only guard clause in FileStorageService.storeFromUrl() with 4 new unit tests**

## Performance

- **Duration:** 3 min
- **Started:** 2026-04-04T11:12:58Z
- **Completed:** 2026-04-04T11:15:34Z
- **Tasks:** 1
- **Files modified:** 2

## Accomplishments
- FileStorageService.storeFromUrl() now rejects null, empty, http://, ftp://, and file:// URLs
- Guard clause throws IllegalArgumentException with descriptive message before any I/O
- Security monitoring via log.warn for rejected URLs
- 4 new unit tests + 1 updated test covering all rejection scenarios

## Task Commits

Each task was committed atomically (TDD):

1. **Task 1 RED: Add failing SSRF tests** - `69a8132` (test)
2. **Task 1 GREEN: Implement URL validation** - `83cdea3` (feat)

## Files Created/Modified
- `src/main/java/org/ctc/domain/service/FileStorageService.java` - Added HTTPS-only URL validation guard clause in storeFromUrl()
- `src/test/java/org/ctc/domain/service/FileStorageServiceTest.java` - 4 new tests + 1 updated test for URL scheme validation

## Decisions Made
- Existing storeFromUrl test used file:// URI -- updated to assert rejection instead of success (file:// is not HTTPS)
- No hostname allowlist per plan design decision D-12 (only scheme validation)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Updated existing storeFromUrl test for file:// URI rejection**
- **Found during:** Task 1 (TDD RED phase)
- **Issue:** Existing test `givenSourceUrl_whenStoreFromUrl_thenFileStoredAndUrlReturned` used `sourceFile.toUri().toString()` which produces a `file://` URI -- this would fail after SSRF protection
- **Fix:** Renamed test to `givenHttpsUrl_whenStoreFromUrl_thenFileStoredAndUrlReturned` and changed assertion to verify file:// URIs are rejected
- **Files modified:** src/test/java/org/ctc/domain/service/FileStorageServiceTest.java
- **Verification:** All 18 tests pass
- **Committed in:** 69a8132 (RED phase commit)

---

**Total deviations:** 1 auto-fixed (1 bug)
**Impact on plan:** Necessary adjustment -- existing test used non-HTTPS URI that conflicts with new security validation.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Known Stubs
None

## Next Phase Readiness
- SSRF protection complete, FileStorageService is secured
- Ready for 05-02 (H2 console restriction) and 05-03 (Spring Security)

## Self-Check: PASSED

- FileStorageService.java: FOUND
- FileStorageServiceTest.java: FOUND
- Commit 69a8132 (RED): FOUND
- Commit 83cdea3 (GREEN): FOUND

---
*Phase: 05-security*
*Completed: 2026-04-04*
