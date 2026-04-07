---
phase: 17
slug: duplicate-handling
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-07
---

# Phase 17 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito |
| **Config file** | `pom.xml` (Surefire + JaCoCo) |
| **Quick run command** | `./mvnw test -pl . -Dtest=DriverMergeServiceTest` |
| **Full suite command** | `./mvnw verify` |
| **Estimated runtime** | ~60 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test -pl . -Dtest=DriverMergeServiceTest`
- **After every plan wave:** Run `./mvnw verify`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 17-01-01 | 01 | 1 | MERGE-11 | — | N/A | unit | `./mvnw test -Dtest=DriverMergeServiceTest` | ✅ | ⬜ pending |
| 17-01-02 | 01 | 1 | MERGE-12 | — | N/A | unit | `./mvnw test -Dtest=DriverMergeServiceTest` | ✅ | ⬜ pending |
| 17-01-03 | 01 | 1 | MERGE-13 | — | N/A | unit | `./mvnw test -Dtest=DriverMergeServiceTest` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Existing infrastructure covers all phase requirements. `DriverMergeServiceTest.java` already exists from Phase 16 with test setup and mocks.

---

## Manual-Only Verifications

All phase behaviors have automated verification.

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
