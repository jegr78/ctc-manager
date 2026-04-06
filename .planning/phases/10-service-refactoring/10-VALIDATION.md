---
phase: 10
slug: service-refactoring
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-06
---

# Phase 10 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + Spring Boot Test |
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
| 10-01-01 | 01 | 1 | ARCH-03 | — | N/A | unit | `./mvnw test -Dtest=TemplateManageableTest` | ❌ W0 | ⬜ pending |
| 10-01-02 | 01 | 1 | ARCH-03 | — | N/A | integration | `./mvnw test -Dtest=TemplateEditorControllerTest` | ✅ | ⬜ pending |
| 10-02-01 | 02 | 1 | ARCH-04 | — | N/A | unit | `./mvnw test -Dtest=PlayoffBracketViewServiceTest` | ❌ W0 | ⬜ pending |
| 10-02-02 | 02 | 1 | ARCH-04 | — | N/A | unit | `./mvnw test -Dtest=PlayoffSeedingServiceTest` | ❌ W0 | ⬜ pending |
| 10-02-03 | 02 | 1 | ARCH-04 | — | N/A | integration | `./mvnw test -Dtest=PlayoffControllerTest` | ✅ | ⬜ pending |
| 10-03-01 | 03 | 1 | ARCH-05 | — | N/A | unit | `./mvnw test -Dtest=RaceFormDataServiceTest` | ❌ W0 | ⬜ pending |
| 10-03-02 | 03 | 1 | ARCH-05 | — | N/A | unit | `./mvnw test -Dtest=RaceCalendarServiceTest` | ❌ W0 | ⬜ pending |
| 10-03-03 | 03 | 1 | ARCH-05 | — | N/A | integration | `./mvnw test -Dtest=RaceControllerTest` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Unit test stubs for new services (`PlayoffBracketViewServiceTest`, `PlayoffSeedingServiceTest`, `RaceFormDataServiceTest`, `RaceCalendarServiceTest`)
- [ ] `TemplateManageableTest` — interface contract tests

*Existing infrastructure covers framework and fixture needs.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| All graphic editing works identically from the UI | ARCH-03 | Visual rendering verification | Navigate to template editor, test save/reset for each graphic type |
| Playoff bracket display unchanged | ARCH-04 | Visual layout verification | Navigate to playoff brackets, verify display matches pre-refactor |
| Race form and calendar events work | ARCH-05 | External calendar API interaction | Create/edit race, verify calendar event created |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 120s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
