---
phase: 35
slug: site-generator-bye-race-safety
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-14
---

# Phase 35 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito |
| **Config file** | pom.xml (Surefire + Failsafe + JaCoCo) |
| **Quick run command** | `./mvnw test -pl . -Dtest=SiteGeneratorServiceTest` |
| **Full suite command** | `./mvnw verify` |
| **Estimated runtime** | ~120 seconds (full suite) |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test -pl . -Dtest=SiteGeneratorServiceTest`
- **After every plan wave:** Run `./mvnw verify`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 120 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 35-01-01 | 01 | 1 | DATA-03 | — | N/A | unit | `./mvnw test -pl . -Dtest=SiteGeneratorServiceTest#givenByeRace*` | ❌ W0 | ⬜ pending |
| 35-01-02 | 01 | 1 | DATA-03 | — | N/A | unit | `./mvnw test -pl . -Dtest=SiteGeneratorServiceTest#givenByeRace*` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Add bye-race test methods to `SiteGeneratorServiceTest` — stubs for DATA-03 null safety

*Existing test infrastructure covers all phase requirements — no new framework or config needed.*

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
