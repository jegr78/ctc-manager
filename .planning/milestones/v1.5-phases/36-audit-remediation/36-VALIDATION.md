---
phase: 36
slug: audit-remediation
status: approved
nyquist_compliant: true
wave_0_complete: true
created: 2026-04-14
---

# Phase 36 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Surefire (unit/integration), Failsafe + `-Pe2e` (E2E) |
| **Config file** | pom.xml (Surefire/Failsafe plugins) |
| **Quick run command** | `./mvnw verify` |
| **Full suite command** | `./mvnw verify -Pe2e` |
| **Estimated runtime** | ~60 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw verify`
- **After every plan wave:** Run `./mvnw verify`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 36-01-01 | 01 | 1 | CONV-04 | T-36-01 / N/A | N/A | grep | `grep -c "parts.push.*style" src/main/resources/templates/admin/race-results.html` (expect 0) | ✅ | ⬜ pending |
| 36-01-02 | 01 | 1 | Traceability | — | N/A | grep | `grep -c "Pending" .planning/REQUIREMENTS.md` (expect 0) | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Existing infrastructure covers all phase requirements. No new test files needed — dead code removal verified by grep, traceability by Pending count, regression by existing 885-test suite.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Race results page renders team totals correctly after dead code removal | CONV-04 | Visual confirmation that CSS-styled totals display | Start dev server, navigate to race results page, verify team totals render with correct styling |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 60s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-04-14
