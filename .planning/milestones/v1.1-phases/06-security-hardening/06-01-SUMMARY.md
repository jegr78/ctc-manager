---
phase: 06-security-hardening
plan: 01
subsystem: security
tags: [ssrf, path-traversal, file-storage, defense-in-depth]

# Dependency graph
requires: []
provides:
  - SSRF hostname validation in FileStorageService.storeFromUrl()
  - Path traversal protection in store(), storeImage(), storeFromUrl()
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Hostname blocklist validation for SSRF prevention (string-based, no DNS resolution)"
    - "normalize+startsWith path traversal check on all file-writing methods"
    - "Defense-in-depth: raw filename check before sanitization + resolved path check after"

key-files:
  created: []
  modified:
    - src/main/java/org/ctc/domain/service/FileStorageService.java
    - src/test/java/org/ctc/domain/service/FileStorageServiceTest.java

key-decisions:
  - "String-based hostname blocklist (no DNS resolution) to avoid DNS rebinding complexity"
  - "Defense-in-depth: validate raw filename for '..' before sanitize, plus post-resolve path check"
  - "validateNoPathTraversal checks original filename; validatePathWithinUploadDir checks resolved path"

patterns-established:
  - "SSRF blocklist: validateHostname() rejects localhost, 127.x, 10.x, 172.16-31.x, 192.168.x, 169.254.x, [::1]"
  - "Path traversal: validatePathWithinUploadDir() uses toAbsolutePath().normalize().startsWith(uploadDir)"

requirements-completed: [SECU-01, SECU-02]

# Metrics
duration: 10min
completed: 2026-04-04
---

# Phase 06 Plan 01: SSRF and Path Traversal Protection Summary

**SSRF hostname blocklist and path traversal defense-in-depth for all FileStorageService write methods**

## Performance

- **Duration:** 10 min
- **Started:** 2026-04-04T12:51:31Z
- **Completed:** 2026-04-04T13:02:12Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- SSRF hostname validation blocks localhost, loopback (127.x), private IPs (10.x, 172.16-31.x, 192.168.x), link-local (169.254.x), and IPv6 loopback ([::1])
- Path traversal protection on all three writing methods (store, storeImage, storeFromUrl) with both filename-level and resolved-path-level checks
- 12 new security tests covering SSRF and path traversal scenarios
- All 765 tests pass, coverage checks met

## Task Commits

Each task was committed atomically:

1. **Task 1: Write security tests (RED phase)** - `34e282c` (test)
2. **Task 2: Implement SSRF and path traversal protection (GREEN phase)** - `5d9df12` (feat)

## Files Created/Modified
- `src/main/java/org/ctc/domain/service/FileStorageService.java` - Added validateHostname(), validatePathWithinUploadDir(), validateNoPathTraversal() and wired into all write methods
- `src/test/java/org/ctc/domain/service/FileStorageServiceTest.java` - Added 12 security test methods for SSRF and path traversal

## Decisions Made
- String-based hostname blocklist without DNS resolution -- avoids DNS rebinding complexity, sufficient for admin-only tool
- Defense-in-depth: raw filename validated for `..` and `/` prefix BEFORE sanitization, plus resolved path checked AFTER resolve -- sanitize alone would neutralize traversal, but belt-and-suspenders approach is better for security
- validateNoPathTraversal added as separate method from validatePathWithinUploadDir to catch intent early

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added validateNoPathTraversal for raw filename check**
- **Found during:** Task 2 (GREEN implementation)
- **Issue:** Plan specified validatePathWithinUploadDir after resolve, but sanitize() converts `../` to `.._` before resolve, so the path check alone would never trigger on crafted filenames
- **Fix:** Added validateNoPathTraversal(filename) that checks raw original filename for `..` and leading `/` before sanitization -- ensures defense-in-depth actually catches traversal attempts
- **Files modified:** src/main/java/org/ctc/domain/service/FileStorageService.java
- **Verification:** All path traversal tests pass (givenPathTraversalFilename_whenStore, givenAbsolutePathFilename_whenStore, etc.)
- **Committed in:** 5d9df12 (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 missing critical)
**Impact on plan:** Essential for path traversal tests to actually catch attacks. Without this, sanitize would neutralize the traversal before the path check could detect it.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Known Stubs
None - all validation logic is fully implemented and wired.

## Next Phase Readiness
- FileStorageService is now hardened against SSRF and path traversal
- No further security plans in this phase (1 of 1 plans)

---
*Phase: 06-security-hardening*
*Completed: 2026-04-04*
