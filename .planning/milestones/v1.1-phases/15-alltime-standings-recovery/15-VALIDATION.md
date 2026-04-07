---
phase: 15
slug: alltime-standings-recovery
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-07
---

# Phase 15 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito (unit), Spring Boot Test (integration) |
| **Config file** | `pom.xml` (Surefire + Failsafe + JaCoCo) |
| **Quick run command** | `./mvnw test -pl . -Dtest=StandingsServiceTest` |
| **Full suite command** | `./mvnw verify` |
| **Estimated runtime** | ~90 seconds (full suite) |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test -pl . -Dtest=StandingsServiceTest`
- **After every plan wave:** Run `./mvnw verify`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 90 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 15-01-01 | 01 | 1 | FEAT-01 | — | N/A | unit | `./mvnw test -Dtest=StandingsServiceTest#AlltimeStandingsTest` | ❌ W0 | ⬜ pending |
| 15-01-02 | 01 | 1 | FEAT-01 | — | N/A | unit | `./mvnw test -Dtest=StandingsServiceTest#AlltimeStandingsTest` | ❌ W0 | ⬜ pending |
| 15-01-03 | 01 | 1 | FEAT-01 | — | N/A | integration | `./mvnw test -Dtest=StandingsControllerTest` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

*Existing infrastructure covers all phase requirements. JUnit 5, Mockito, and Spring Boot Test are already configured. No new test framework or fixture setup needed.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Alltime standings page displays ranked teams | FEAT-01 | Visual verification of rendered HTML | Start dev server, navigate to `/admin/standings?alltime=true`, verify table renders with team names and aggregated points |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 90s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
