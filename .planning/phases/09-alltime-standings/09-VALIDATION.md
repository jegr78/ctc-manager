---
phase: 9
slug: alltime-standings
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-05
---

# Phase 9 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + Spring Boot Test |
| **Config file** | `pom.xml` (surefire + failsafe) |
| **Quick run command** | `./mvnw test -Dtest=StandingsServiceTest -pl .` |
| **Full suite command** | `./mvnw verify` |
| **Estimated runtime** | ~60 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test -Dtest=StandingsServiceTest -pl .`
- **After every plan wave:** Run `./mvnw verify`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 09-01-01 | 01 | 1 | FEAT-01a | unit | `./mvnw test -Dtest=StandingsServiceTest` | Wave 0 | ⬜ pending |
| 09-01-02 | 01 | 1 | FEAT-01b | unit | `./mvnw test -Dtest=StandingsServiceTest` | Wave 0 | ⬜ pending |
| 09-01-03 | 01 | 1 | FEAT-01c | unit | `./mvnw test -Dtest=StandingsServiceTest` | Wave 0 | ⬜ pending |
| 09-01-04 | 01 | 1 | FEAT-01d | unit | `./mvnw test -Dtest=StandingsServiceTest` | Wave 0 | ⬜ pending |
| 09-01-05 | 01 | 1 | FEAT-01e | integration | `./mvnw test -Dtest=StandingsControllerTest` | Existing (needs update) | ⬜ pending |
| 09-01-06 | 01 | 1 | FEAT-01f | unit | `./mvnw test -Dtest=StandingsServiceTest` | Existing | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `StandingsServiceTest` — new `@Nested AlltimeStandingsTest` class with multi-season test fixtures
- [ ] `StandingsControllerTest` — update `whenGetAlltimeStandings_thenReturnsAlltimeView` to verify non-empty standings

*Existing infrastructure covers framework — only new test classes/methods needed.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Alltime option in dropdown selects correctly | FEAT-01 | UI rendering | Navigate to `/admin/standings`, select "Alltime" from dropdown, verify table shows data |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
