---
phase: 12-security-hardening-recovery
plan: 01
subsystem: file-storage
tags: [security, ssrf, path-traversal, tdd, recovery]
dependency_graph:
  requires: []
  provides: [ssrf-hostname-validation, path-traversal-protection]
  affects: [FileStorageService]
tech_stack:
  added: []
  patterns: [defense-in-depth, hostname-blocklist, path-containment]
key_files:
  created: []
  modified:
    - src/main/java/org/ctc/domain/service/FileStorageService.java
    - src/test/java/org/ctc/domain/service/FileStorageServiceTest.java
decisions:
  - Restored exact implementation from commit 84e8896 (Phase 6 original)
  - 7 call sites instead of plan's stated 6 (storeImage validatePathWithinUploadDir was listed in action but miscounted in verification)
metrics:
  duration: ~30min
  completed: 2026-04-06
  tasks: 2/2
  files: 2
---

# Phase 12 Plan 01: FileStorageService Security Recovery Summary

Restored SSRF hostname validation (SECU-01) and path traversal protection (SECU-02) lost by worktree file-clobber regression in commit 5b3a58b, using TDD approach with exact code from commit 84e8896.

## Task Completion

| Task | Name | Type | Commit | Files |
|------|------|------|--------|-------|
| 1 | Restore 11 security test methods (RED) | test/tdd | 7dfe07e | FileStorageServiceTest.java |
| 2 | Restore 3 validation methods + 7 call sites (GREEN) | feat/tdd | cc697fc | FileStorageService.java |

## What Was Done

### Task 1: RED Phase - Security Tests
Added 12 test methods to FileStorageServiceTest.java (11 expected to fail, 1 expected to pass):
- 8 SSRF hostname validation tests covering localhost, 127.x loopback, 10.x/172.16-31.x/192.168.x private IPs, 169.254.x link-local, IPv6 [::1] loopback
- 1 public URL test confirming SSRF check passes (expects IOException from network)
- 3 path traversal tests: `../` in filename for store() and storeImage(), absolute path `/etc/passwd.png`
- 1 subDir traversal test: `../escape` in storeFromUrl()

Verification: 30 tests run, 11 failures (RED confirmed), 19 passing.

### Task 2: GREEN Phase - Validation Implementation
Added 3 private validation methods to FileStorageService.java:
- `validateHostname(String sourceUrl)`: Extracts hostname via `java.net.URI`, blocks localhost, [::1], 127.x, 10.x, 172.16-31.x, 192.168.x, 169.254.x
- `validatePathWithinUploadDir(Path target)`: Normalizes path and checks `startsWith(uploadDir)`
- `validateNoPathTraversal(String filename)`: Rejects filenames containing `..` or starting with `/`

Added 7 call sites:
- `store()`: validateNoPathTraversal + validatePathWithinUploadDir
- `storeFromUrl()`: validateHostname + validatePathWithinUploadDir(dir) + validatePathWithinUploadDir(target)
- `storeImage()`: validateNoPathTraversal + validatePathWithinUploadDir

Verification: 30 tests pass (GREEN), `./mvnw verify` BUILD SUCCESS, all JaCoCo coverage checks met.

## Deviations from Plan

### Minor Count Correction
**Plan stated 6 call sites, actual is 7.** The plan's action section correctly listed all 7 locations but the verification section counted 9 (3 declarations + 6 call sites). The actual count is 3 declarations + 7 call sites = 10 grep matches. The extra call site is `validatePathWithinUploadDir(target)` in `storeImage()`, which was correctly specified in the action but miscounted in verification.

No other deviations. Plan executed as written.

## Verification Results

| Check | Result |
|-------|--------|
| `./mvnw test -Dtest=FileStorageServiceTest` | 30 tests, 0 failures |
| `./mvnw verify` | BUILD SUCCESS |
| JaCoCo coverage checks | All met |
| validateHostname exists | Yes |
| validatePathWithinUploadDir exists | Yes |
| validateNoPathTraversal exists | Yes |
| Validation references in service | 10 (3 declarations + 7 call sites) |

## Known Stubs

None. All validation methods are fully implemented with production logic.

## Self-Check: PASSED

- [x] FileStorageService.java modified with 3 validation methods + 7 call sites
- [x] FileStorageServiceTest.java modified with 12 new test methods
- [x] Commit 7dfe07e exists (Task 1 - RED)
- [x] Commit cc697fc exists (Task 2 - GREEN)
- [x] Full test suite passes
- [x] Coverage checks met
