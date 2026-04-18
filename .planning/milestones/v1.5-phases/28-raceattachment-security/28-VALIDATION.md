---
phase: 28
slug: raceattachment-security
status: complete
nyquist_compliant: true
wave_0_complete: true
created: 2026-04-13
validated: 2026-04-13
---

# Phase 28 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito |
| **Config file** | pom.xml (Surefire + JaCoCo) |
| **Quick run command** | `./mvnw test -pl . -Dtest=RaceAttachmentServiceTest` |
| **Full suite command** | `./mvnw verify` |
| **Estimated runtime** | ~120 seconds (full), ~10 seconds (quick) |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test -pl . -Dtest=RaceAttachmentServiceTest`
- **After every plan wave:** Run `./mvnw verify`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 120 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 28-01-01 | 01 | 1 | SECU-02 | T-28-01 | Path traversal blocked: resolved path must start with upload dir | unit | `./mvnw test -Dtest=RaceAttachmentServiceTest#givenPathTraversalUrl_whenDownloadAttachment_thenReturnsBadRequest` | ✅ | ✅ green |
| 28-01-02 | 01 | 1 | DATA-02 | T-28-03 | Null content-type returns application/octet-stream | unit | `./mvnw test -Dtest=RaceAttachmentServiceTest#givenNullProbeContentType_whenDownloadAttachment_thenUsesOctetStream` | ✅ | ✅ green |
| 28-01-03 | 01 | 1 | SECU-05 | T-28-02 | Content-Disposition filename sanitized (no \r\n\";) | unit | `./mvnw test -Dtest=RaceAttachmentServiceTest#givenFilenameWithInjectionChars_whenDownloadAttachment_thenHeaderIsSanitized` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `RaceAttachmentServiceTest` — tests for SECU-02, DATA-02, SECU-05

*All Wave 0 tests implemented and passing.*

---

## Manual-Only Verifications

*All phase behaviors have automated verification.*

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 120s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-04-13

## Validation Audit 2026-04-13
| Metric | Count |
|--------|-------|
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |

All 3 requirements (SECU-02, DATA-02, SECU-05) have automated unit tests passing in `RaceAttachmentServiceTest`. 862 total tests green.
