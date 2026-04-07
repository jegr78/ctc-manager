---
phase: 20
slug: english-messages
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-07
---

# Phase 20 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Maven Surefire/Failsafe + JaCoCo |
| **Config file** | `pom.xml` |
| **Quick run command** | `./mvnw verify` |
| **Full suite command** | `./mvnw verify -Pe2e` |
| **Estimated runtime** | ~120 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw verify`
- **After every plan wave:** Run `./mvnw verify -Pe2e`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 120 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 20-01-01 | 01 | 1 | I18N-04 | — | N/A | compile+test | `./mvnw verify` | ✅ | ⬜ pending |
| 20-01-02 | 01 | 1 | I18N-04 | — | N/A | compile+test | `./mvnw verify` | ✅ | ⬜ pending |
| 20-01-03 | 01 | 1 | I18N-04 | — | N/A | compile+test | `./mvnw verify` | ✅ | ⬜ pending |
| 20-01-04 | 01 | 1 | I18N-04 | — | N/A | compile+test | `./mvnw verify` | ✅ | ⬜ pending |
| 20-01-05 | 01 | 1 | I18N-04 | — | N/A | compile+test | `./mvnw verify` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Existing infrastructure covers all phase requirements.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| No German text in comments/config | I18N-04 | One-time grep verification per D-09 | `grep -rn '[äöüÄÖÜß]' src/ Dockerfile` — expect only D-07/D-08 allowlist matches |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 120s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
