---
phase: 2
slug: service-layer-extraction
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-03
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + Mockito |
| **Config file** | `pom.xml` (Surefire + JaCoCo) |
| **Quick run command** | `./mvnw test -pl .` |
| **Full suite command** | `./mvnw verify` |
| **Estimated runtime** | ~60 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test -pl .`
- **After every plan wave:** Run `./mvnw verify`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 02-01-01 | 01 | 1 | SRVC-01,02,03 | unit | `./mvnw test -pl .` | ❌ W0 | ⬜ pending |
| 02-02-01 | 02 | 1 | SRVC-04,05 | unit | `./mvnw test -pl .` | ❌ W0 | ⬜ pending |
| 02-03-01 | 03 | 2 | SRVC-06,07 | unit+integration | `./mvnw verify` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] New service test files for CarService, TrackService, RaceScoringService, MatchScoringService
- [ ] Updated test files for DriverService, TeamManagementService, SeasonManagementService extensions

*Existing WebMvcTest files will be updated to mock services instead of repositories.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Image upload still works | SRVC-02, SRVC-03 | File I/O integration | Upload image for Car/Track, verify it displays |
| Season pool management | SRVC-07 | Multi-step workflow | Add/remove teams, cars, tracks from season pool |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
