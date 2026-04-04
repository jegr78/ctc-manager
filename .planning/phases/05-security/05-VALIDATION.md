---
phase: 5
slug: security
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-04
---

# Phase 5 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + Spring Boot Test |
| **Config file** | `pom.xml` (surefire/failsafe plugins) |
| **Quick run command** | `./mvnw test` |
| **Full suite command** | `./mvnw verify` |
| **Estimated runtime** | ~60 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test`
- **After every plan wave:** Run `./mvnw verify`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 05-01-01 | 01 | 1 | SECU-01 | integration | `./mvnw test -Dtest=SecurityConfigTest` | ❌ W0 | ⬜ pending |
| 05-01-02 | 01 | 1 | SECU-02 | integration | `./mvnw test -Dtest=SecurityConfigTest` | ❌ W0 | ⬜ pending |
| 05-01-03 | 01 | 1 | SECU-03 | unit+integration | `./mvnw test -Dtest=SecurityConfigTest` | ❌ W0 | ⬜ pending |
| 05-02-01 | 02 | 1 | SECU-04 | unit | `./mvnw test -Dtest=FileStorageServiceTest` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `SecurityConfigTest.java` — integration tests for auth on prod/docker profiles and open access on dev/local
- [ ] Existing `FileStorageServiceTest.java` — add HTTPS-only validation tests

*Existing test infrastructure (JUnit 5, Spring Boot Test, MockMvc) covers all phase requirements.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Browser login prompt | SECU-01 | Visual browser behavior | Start with `prod` profile, navigate to `/admin/seasons`, verify browser shows login dialog |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
