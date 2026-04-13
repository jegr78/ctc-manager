---
phase: 28-raceattachment-security
plan: "01"
subsystem: domain/service
tags: [security, path-traversal, header-injection, mime-type, tdd]
dependency_graph:
  requires: []
  provides: [secured-downloadAttachment]
  affects: [RaceAttachmentService, RaceAttachmentServiceTest]
tech_stack:
  added: []
  patterns: [path-traversal-guard, null-safe-probe, header-sanitization]
key_files:
  created: []
  modified:
    - src/main/java/org/ctc/domain/service/RaceAttachmentService.java
    - src/test/java/org/ctc/domain/service/RaceAttachmentServiceTest.java
decisions:
  - "Return ResponseEntity.badRequest() (not throw) for path traversal — consistent with existing LINK-type guard pattern"
  - "Colon (:) not included in header injection sanitization pattern — safe in Content-Disposition filename values"
  - "Test assertion corrected from plan spec: doesNotContain(header\"test) and contains(evil_X-Injected: header_test_param)"
metrics:
  duration: ~12min
  completed: 2026-04-13
  tasks_completed: 2
  files_modified: 2
---

# Phase 28 Plan 01: RaceAttachment Security Summary

Secured `downloadAttachment()` in RaceAttachmentService against path traversal (SECU-02), null MIME NPE (DATA-02), and Content-Disposition header injection (SECU-05) using three targeted 1-3 line fixes with TDD.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 (RED) | Add failing security tests | 1b2490f | RaceAttachmentServiceTest.java |
| 2 (GREEN) | Fix downloadAttachment security | 5a1784a | RaceAttachmentService.java, RaceAttachmentServiceTest.java |

## What Was Built

Three security fixes applied to `RaceAttachmentService.downloadAttachment()`:

**SECU-02 — Path Traversal Guard:**
```java
Path uploadDirPath = Paths.get(uploadDir).toAbsolutePath().normalize();
Path file = uploadDirPath.resolve(url.substring("/uploads/".length())).normalize();
if (!file.startsWith(uploadDirPath)) {
    log.warn("Path traversal attempt in download, attachmentId={}", attachmentId);
    return ResponseEntity.badRequest().build();
}
```

**DATA-02 — Null-Safe MIME Type:**
```java
String probed = null;
try { probed = Files.probeContentType(file); } catch (IOException e) { ... }
String contentType = (probed != null) ? probed : "application/octet-stream";
```

**SECU-05 — Header Injection Sanitization:**
```java
String safeName = attachment.getName().replaceAll("[\\r\\n\";]", "_");
```

Four new unit tests added to `RaceAttachmentServiceTest`:
- `givenPathTraversalUrl_whenDownloadAttachment_thenReturnsBadRequest`
- `givenNullProbeContentType_whenDownloadAttachment_thenUsesOctetStream`
- `givenFilenameWithInjectionChars_whenDownloadAttachment_thenHeaderIsSanitized`
- `givenNonExistentFile_whenDownloadAttachment_thenReturnsNotFound` (coverage gap)

## Verification

- `./mvnw test -Dtest=RaceAttachmentServiceTest` — 10/10 tests pass
- `./mvnw verify` — 858 tests pass, JaCoCo coverage check passes (>= 82%)

## Deviations from Plan

**1. [Rule 1 - Bug] Corrected test assertion for header injection check**
- **Found during:** Task 2 GREEN phase
- **Issue:** Plan spec had `doesNotContain("\"evil")` which fails because the standard `filename="...` Content-Disposition format itself starts with `"` before the sanitized filename. Also `contains("evil_X-Injected_ header_test_param")` expected `_` where `:` (colon) is, but colons are safe and not sanitized.
- **Fix:** Changed to `doesNotContain("header\"test")` (checks the actual `"` from the evil name was removed) and `contains("evil_X-Injected: header_test_param")` (colon is retained — correct behavior).
- **Files modified:** RaceAttachmentServiceTest.java
- **Commit:** 5a1784a

## Known Stubs

None.

## Threat Flags

None — all three threats from the plan's threat register (T-28-01, T-28-02, T-28-03) are now mitigated.

## Self-Check: PASSED

| Item | Status |
|------|--------|
| RaceAttachmentService.java exists | FOUND |
| RaceAttachmentServiceTest.java exists | FOUND |
| 28-01-SUMMARY.md exists | FOUND |
| Commit 1b2490f (RED tests) | FOUND |
| Commit 5a1784a (GREEN fix) | FOUND |
