---
phase: 31
slug: null-safety-and-transaction-fix
status: validated
nyquist_compliant: true
wave_0_complete: true
created: 2026-04-13
audited: 2026-04-14
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
| 31-01-01 | 01 | 1 | DATA-01 | — | N/A | unit | `./mvnw test -Dtest=CsvImportServiceTest` | ✅ | ✅ green |
| 31-02-01 | 02 | 1 | DATA-03 | — | N/A | unit | `./mvnw test -Dtest=RaceFormDataServiceTest` | ✅ | ✅ green |
| 31-02-02 | 02 | 1 | DATA-03 | — | N/A | unit | `./mvnw test -Dtest=ScoringServiceTest` | ✅ | ✅ green |
| 31-03-01 | 03 | 1 | DATA-04 | — | N/A | unit | `./mvnw test -Dtest=ScoringServiceTest` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

*Existing infrastructure covers all phase requirements.*

---

## Manual-Only Verifications

*All phase behaviors have automated verification.*

---

## Validation Audit 2026-04-14

| Metric | Count |
|--------|-------|
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |
| Manual-Only | 0 |
| Automated COVERED | 4 |

All tasks have automated verification via existing test classes. No gaps detected.

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 120s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** validated 2026-04-14
