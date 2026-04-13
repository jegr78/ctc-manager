---
phase: 31
slug: null-safety-and-transaction-fix
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-13
---

# Phase 31 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito |
| **Config file** | `pom.xml` (surefire + failsafe) |
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
| 31-01-01 | 01 | 1 | DATA-01 | — | N/A | unit | `./mvnw test -Dtest=CsvImportServiceTest` | ✅ | ⬜ pending |
| 31-02-01 | 02 | 1 | DATA-03 | — | N/A | unit | `./mvnw test -Dtest=RaceFormDataServiceTest` | ✅ | ⬜ pending |
| 31-02-02 | 02 | 1 | DATA-03 | — | N/A | unit | `./mvnw test -Dtest=ScoringServiceTest` | ✅ | ⬜ pending |
| 31-03-01 | 03 | 1 | DATA-04 | — | N/A | unit | `./mvnw test -Dtest=ScoringServiceTest` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

*Existing infrastructure covers all phase requirements.*

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
