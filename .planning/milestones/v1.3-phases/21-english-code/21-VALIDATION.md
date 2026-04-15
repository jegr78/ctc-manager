---
phase: 21
slug: english-code
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-08
---

# Phase 21 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test |
| **Config file** | `pom.xml` (Surefire + Failsafe configuration) |
| **Quick run command** | `./mvnw test -pl . -Dtest=StandingsServiceTest,StandingsControllerTest,SiteGeneratorServiceTest` |
| **Full suite command** | `./mvnw verify` |
| **Estimated runtime** | ~60 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test -pl . -Dtest=StandingsServiceTest,StandingsControllerTest,SiteGeneratorServiceTest`
- **After every plan wave:** Run `./mvnw verify`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 21-01-01 | 01 | 1 | I18N-03 | — | N/A | unit (existing) | `./mvnw test -Dtest=StandingsServiceTest` | ✅ | ⬜ pending |
| 21-01-02 | 01 | 1 | I18N-03 | — | N/A | unit (existing) | `./mvnw test -Dtest=StandingsControllerTest` | ✅ | ⬜ pending |
| 21-01-03 | 01 | 1 | I18N-03, I18N-05 | — | N/A | unit (existing) | `./mvnw test -Dtest=SiteGeneratorServiceTest` | ✅ | ⬜ pending |
| 21-02-01 | 02 | 1 | I18N-04 | — | N/A | grep scan | `grep -rn "ohne\|nur anzeigen\|direkt" src/main/resources/templates/` | ✅ | ⬜ pending |
| 21-03-01 | 03 | 2 | I18N-03, I18N-04, I18N-05 | — | N/A | grep scan | `grep -rn -i "spieltag\|saison\|fahrer\|mannschaft" src/main/java/ src/main/resources/templates/` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Existing infrastructure covers all phase requirements. No new test files needed.

---

## Manual-Only Verifications

All phase behaviors have automated verification (grep scans + existing unit tests).

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
