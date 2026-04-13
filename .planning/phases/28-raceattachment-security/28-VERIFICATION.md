---
phase: 28-raceattachment-security
verified: 2026-04-13T16:03:30Z
status: passed
score: 3/3 must-haves verified
overrides_applied: 0
---

# Phase 28: RaceAttachment Security Verification Report

**Phase Goal:** File download in RaceAttachmentService is secure against path traversal, null content-type crashes, and header injection
**Verified:** 2026-04-13T16:03:30Z
**Status:** PASSED
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | A download request with a path-traversal URL (containing ../) returns HTTP 400, not file content | VERIFIED | `file.startsWith(uploadDirPath)` guard at line 83 of RaceAttachmentService.java returns `ResponseEntity.badRequest().build()` on violation; test `givenPathTraversalUrl_whenDownloadAttachment_thenReturnsBadRequest` passes |
| 2 | A download request for a file whose MIME type cannot be probed returns application/octet-stream, not NPE | VERIFIED | Ternary `(probed != null) ? probed : "application/octet-stream"` at line 99; test `givenNullProbeContentType_whenDownloadAttachment_thenUsesOctetStream` passes |
| 3 | A download response for a file whose attachment name contains \r, \n, ", or ; has those characters replaced with _ in Content-Disposition | VERIFIED | `replaceAll("[\\r\\n\";]", "_")` at line 102; test `givenFilenameWithInjectionChars_whenDownloadAttachment_thenHeaderIsSanitized` asserts no \n, \r, literal-quote, or semicolons survive in the header |

**Score:** 3/3 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/test/java/org/ctc/domain/service/RaceAttachmentServiceTest.java` | Security unit tests for downloadAttachment | VERIFIED | Contains all four required test methods; file is 257 lines, substantive |
| `src/test/java/org/ctc/domain/service/RaceAttachmentServiceTest.java` | Contains `givenPathTraversalUrl_whenDownloadAttachment_thenReturnsBadRequest` | VERIFIED | Method present at line 166 |
| `src/test/java/org/ctc/domain/service/RaceAttachmentServiceTest.java` | Contains `givenNullProbeContentType_whenDownloadAttachment_thenUsesOctetStream` | VERIFIED | Method present at line 183 |
| `src/test/java/org/ctc/domain/service/RaceAttachmentServiceTest.java` | Contains `givenFilenameWithInjectionChars_whenDownloadAttachment_thenHeaderIsSanitized` | VERIFIED | Method present at line 211 |
| `src/main/java/org/ctc/domain/service/RaceAttachmentService.java` | Secured downloadAttachment method; contains `file.startsWith(uploadDirPath)` | VERIFIED | Guard present at line 83; full method body 37 lines with all three fixes |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `RaceAttachmentServiceTest.java` | `RaceAttachmentService.java` | `service.downloadAttachment()` calls with adversarial inputs | WIRED | 5 call sites in test file (lines 157, 176, 202, 228, 251); all call the production `downloadAttachment` method |

### Data-Flow Trace (Level 4)

Not applicable — this phase modifies a service method (not a rendering component). The security guards are control-flow checks, not data-rendering paths.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| 10/10 RaceAttachmentServiceTest pass | `./mvnw test -Dtest=RaceAttachmentServiceTest` | 10 tests run, 0 failures, BUILD SUCCESS | PASS |
| Full suite 858 tests, coverage >= 82% | `./mvnw verify` | 858 tests, 0 failures, all JaCoCo checks met, BUILD SUCCESS | PASS |
| Path traversal log warning emitted | WARN log visible in test output: "Path traversal attempt in download, attachmentId=..." | Confirmed in test run output | PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| SECU-02 | 28-01-PLAN.md | File download validates resolved path stays within upload directory (path traversal defense) | SATISFIED | `Paths.get(uploadDir).toAbsolutePath().normalize()` + `file.startsWith(uploadDirPath)` guard in `downloadAttachment()`; dedicated test passes |
| SECU-05 | 28-01-PLAN.md | Content-Disposition header uses sanitized filename to prevent header injection | SATISFIED | `replaceAll("[\\r\\n\";]", "_")` applied to attachment name before embedding in header; test asserts sanitized output |
| DATA-02 | 28-01-PLAN.md | File download handles null content type from probeContentType gracefully | SATISFIED | Two-step probe with ternary fallback to `application/octet-stream`; test mocks `Files.probeContentType` to return null and asserts response has octet-stream content type |

All three requirements mapped to Phase 28 in REQUIREMENTS.md are satisfied. No orphaned requirements for this phase.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (none) | — | — | — | — |

No TODO/FIXME/placeholder/stub patterns found in either modified file. No empty handlers or hardcoded empty returns.

### Human Verification Required

(None — all security behaviors are unit-testable and verified programmatically.)

### Gaps Summary

No gaps. All three must-have truths are satisfied by the actual implementation. The code is substantive (not stubs), fully wired (tests call the production method), and the test suite confirms runtime correctness: 10/10 security tests pass, 858/858 total tests pass, JaCoCo coverage threshold met.

**Notable deviation from plan (documented in SUMMARY, non-blocking):** The header injection test assertion was corrected from the plan spec — colons (`:`) are safe in Content-Disposition filenames and are not sanitized. The final regex `[\\r\\n\";]` correctly excludes colons, and the assertion `contains("evil_X-Injected: header_test_param")` reflects this. This is correct behavior.

---

_Verified: 2026-04-13T16:03:30Z_
_Verifier: Claude (gsd-verifier)_
