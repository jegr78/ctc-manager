---
phase: 27
slug: restore-matchday-result-pipeline
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-10
---

# Phase 27 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test |
| **Config file** | `pom.xml` (Surefire + JaCoCo) |
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
| 27-01-01 | 01 | 1 | DATA-04 | — | N/A | integration | `./mvnw verify` | ✅ | ⬜ pending |
| 27-01-02 | 01 | 1 | DATA-05 | — | N/A | integration | `./mvnw verify` | ✅ | ⬜ pending |
| 27-01-03 | 01 | 1 | DATA-06 | — | N/A | integration | `./mvnw verify` | ✅ | ⬜ pending |
| 27-01-04 | 01 | 1 | DATA-07 | — | N/A | integration | `./mvnw verify` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

*Existing infrastructure covers all phase requirements.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Visual verification of seeded matchday data in dev mode | DATA-04-07 | Requires running app with dev,demo profile | Start with `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`, browse to seasons/matchdays |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 120s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
