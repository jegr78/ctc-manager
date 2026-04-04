---
phase: 3
slug: god-service-split
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-04
---

# Phase 3 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito (Unit), Spring Boot Test (Integration) |
| **Config file** | `pom.xml` (Maven Surefire/Failsafe) |
| **Quick run command** | `./mvnw test -Dtest=RaceServiceTest,RaceGraphicServiceTest,RaceAttachmentServiceTest` |
| **Full suite command** | `./mvnw verify` |
| **Estimated runtime** | ~60 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test -Dtest=RaceServiceTest,RaceGraphicServiceTest,RaceAttachmentServiceTest,RaceControllerTest`
- **After every plan wave:** Run `./mvnw verify`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 03-01-01 | 01 | 1 | SRVC-08c | unit | `./mvnw test -Dtest=RaceAttachmentServiceTest` | ❌ W0 | ⬜ pending |
| 03-01-02 | 01 | 1 | SRVC-08b | unit | `./mvnw test -Dtest=RaceGraphicServiceTest` | ❌ W0 | ⬜ pending |
| 03-01-03 | 01 | 1 | SRVC-08a | unit | `./mvnw test -Dtest=RaceServiceTest` | ❌ W0 | ⬜ pending |
| 03-01-04 | 01 | 1 | SRVC-08 | integration | `./mvnw test -Dtest=RaceControllerTest` | ✅ | ⬜ pending |
| 03-01-05 | 01 | 1 | SRVC-08d | compile | `./mvnw compile` | N/A | ⬜ pending |
| 03-01-06 | 01 | 1 | SRVC-08e,f | integration | `./mvnw verify` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `RaceServiceTest.java` — split from RaceManagementServiceTest (27 tests)
- [ ] `RaceGraphicServiceTest.java` — split from RaceManagementServiceTest (5 tests)
- [ ] `RaceAttachmentServiceTest.java` — split from RaceManagementServiceTest (6 tests)

*Existing infrastructure covers all phase requirements — no new framework or fixtures needed.*

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
