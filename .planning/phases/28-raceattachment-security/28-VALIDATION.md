---
phase: 28
slug: raceattachment-security
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-13
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
| 28-01-01 | 01 | 1 | SECU-02 | T-28-01 | Path traversal blocked: resolved path must start with upload dir | unit | `./mvnw test -Dtest=RaceAttachmentServiceTest#givenTraversalPath_whenDownload_thenForbidden` | ❌ W0 | ⬜ pending |
| 28-01-02 | 01 | 1 | DATA-02 | — | Null content-type returns application/octet-stream | unit | `./mvnw test -Dtest=RaceAttachmentServiceTest#givenUnknownMimeType_whenDownload_thenOctetStream` | ❌ W0 | ⬜ pending |
| 28-01-03 | 01 | 1 | SECU-05 | T-28-02 | Content-Disposition filename sanitized (no \r\n\";) | unit | `./mvnw test -Dtest=RaceAttachmentServiceTest#givenMaliciousFilename_whenDownload_thenSanitized` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `RaceAttachmentServiceTest` — test stubs for SECU-02, DATA-02, SECU-05

*Existing infrastructure covers test framework — only new test methods needed.*

---

## Manual-Only Verifications

*All phase behaviors have automated verification.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 120s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
