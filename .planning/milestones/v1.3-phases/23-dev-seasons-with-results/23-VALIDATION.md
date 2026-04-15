---
phase: 23
slug: dev-seasons-with-results
status: approved
nyquist_compliant: true
wave_0_complete: true
created: 2026-04-09
---

# Phase 23 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test |
| **Config file** | `pom.xml` (Surefire + Failsafe + JaCoCo) |
| **Quick run command** | `./mvnw test -pl .` |
| **Full suite command** | `./mvnw verify` |
| **Estimated runtime** | ~120 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test -pl .`
- **After every plan wave:** Run `./mvnw verify`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 120 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 23-01-01 | 01 | 1 | DATA-04, DATA-06 | — | N/A | integration | `./mvnw test-compile -pl .` | Yes (extended) | pending |
| 23-01-02 | 01 | 1 | DATA-05, DATA-06, DATA-07 | — | N/A | integration | `./mvnw test -Dtest=TestDataServiceIntegrationTest -pl .` | Yes (extended) | pending |
| 23-02-01 | 02 | 2 | DATA-04, DATA-05, DATA-06, DATA-07 | — | N/A | integration | `./mvnw test-compile -pl .` | Yes (extended) | pending |
| 23-02-02 | 02 | 2 | DATA-04, DATA-05, DATA-06, DATA-07 | — | N/A | integration | `./mvnw test -Dtest=TestDataServiceIntegrationTest -pl . && ./mvnw verify` | Yes (extended) | pending |
| 23-02-03 | 02 | 2 | DATA-07 | — | N/A | visual | Manual: standings pages verification | N/A | pending |

*Status: pending / green / red / flaky*

---

## Wave 0 Requirements

*Existing infrastructure covers all phase requirements. Test file `TestDataServiceIntegrationTest.java` exists and will be extended with new test methods in TDD RED tasks (Plan 01 Task 1 and Plan 02 Task 1).*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Standings pages display non-zero points | DATA-07 | Visual verification of rendered HTML | Start dev server, navigate to /admin/seasons/{id}/standings for each season |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 120s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved
