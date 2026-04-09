---
phase: 23
slug: dev-seasons-with-results
status: draft
nyquist_compliant: false
wave_0_complete: false
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
| 23-01-01 | 01 | 1 | DATA-04 | — | N/A | integration | `./mvnw verify` | ⬜ TBD | ⬜ pending |
| 23-01-02 | 01 | 1 | DATA-05 | — | N/A | integration | `./mvnw verify` | ⬜ TBD | ⬜ pending |
| 23-01-03 | 01 | 1 | DATA-06 | — | N/A | integration | `./mvnw verify` | ⬜ TBD | ⬜ pending |
| 23-01-04 | 01 | 1 | DATA-07 | — | N/A | integration | `./mvnw verify` | ⬜ TBD | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

*Existing infrastructure covers all phase requirements.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Standings pages display non-zero points | DATA-07 | Visual verification of rendered HTML | Start dev server, navigate to /admin/seasons/{id}/standings for each season |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 120s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
