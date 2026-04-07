---
phase: 19
slug: merge-error-handling
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-07
---

# Phase 19 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test |
| **Config file** | `pom.xml` (surefire + failsafe) |
| **Quick run command** | `./mvnw test -pl . -Dtest=DriverControllerTest` |
| **Full suite command** | `./mvnw verify` |
| **Estimated runtime** | ~60 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test -pl . -Dtest=DriverControllerTest`
- **After every plan wave:** Run `./mvnw verify`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 19-01-01 | 01 | 1 | MERGE-02 | — | Self-merge returns flash error, not exception page | unit | `./mvnw test -Dtest=DriverControllerTest#givenSelfMerge_whenPreviewMerge_thenRedirectsWithError` | ❌ W0 | ⬜ pending |
| 19-01-02 | 01 | 1 | MERGE-03 | — | Non-existent target returns flash error, not exception page | unit | `./mvnw test -Dtest=DriverControllerTest#givenNonExistentTarget_whenPreviewMerge_thenRedirectsWithError` | ❌ W0 | ⬜ pending |
| 19-01-03 | 01 | 1 | MERGE-02, MERGE-03 | — | previewMerge catches exceptions and redirects | integration | `./mvnw test -Dtest=DriverControllerTest` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Tests for self-merge error on preview endpoint — stubs for MERGE-02
- [ ] Tests for non-existent target on preview endpoint — stubs for MERGE-03

*Existing test infrastructure covers all framework requirements.*

---

## Manual-Only Verifications

*All phase behaviors have automated verification.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
