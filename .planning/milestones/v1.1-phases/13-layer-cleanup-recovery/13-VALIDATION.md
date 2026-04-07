---
phase: 13
slug: layer-cleanup-recovery
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-06
---

# Phase 13 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito (unit), Spring Boot Test MockMvc (integration), Playwright (E2E) |
| **Config file** | `pom.xml` (Surefire for unit/integration, Failsafe + `-Pe2e` for E2E) |
| **Quick run command** | `./mvnw test` |
| **Full suite command** | `./mvnw verify -Pe2e` |
| **Estimated runtime** | ~90 seconds (unit+integration), ~180 seconds (with E2E) |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test`
- **After every plan wave:** Run `./mvnw verify`
- **Before `/gsd-verify-work`:** Full suite must be green (`./mvnw verify -Pe2e`)
- **Max feedback latency:** 90 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 13-01-01 | 01 | 1 | ARCH-02 | — | N/A | unit | `./mvnw test -Dtest=SeasonManagementServiceTest` | ✅ | ⬜ pending |
| 13-01-02 | 01 | 1 | ARCH-02 | — | N/A | unit | `./mvnw test -Dtest=TeamManagementServiceTest` | ✅ | ⬜ pending |
| 13-01-03 | 01 | 1 | ARCH-02 | — | N/A | unit | `./mvnw test -Dtest=PlayoffServiceTest` | ✅ | ⬜ pending |
| 13-01-04 | 01 | 1 | ARCH-02, FEAT-02 | — | N/A | integration | `./mvnw test -Dtest=StandingsControllerTest` | ✅ | ⬜ pending |
| 13-01-05 | 01 | 1 | ARCH-02 | — | N/A | integration | `./mvnw test -Dtest=PowerRankingsControllerTest` | ✅ | ⬜ pending |
| 13-01-06 | 01 | 1 | ARCH-02 | — | N/A | integration | `./mvnw test -Dtest=TeamCardControllerTest` | ✅ | ⬜ pending |
| 13-01-07 | 01 | 1 | ARCH-02 | — | N/A | integration | `./mvnw test -Dtest=PlayoffControllerTest` | ✅ | ⬜ pending |
| 13-01-08 | 01 | 1 | ARCH-02 | — | N/A | integration | `./mvnw test -Dtest=CsvImportControllerTest` | ✅ | ⬜ pending |
| 13-02-01 | 02 | 2 | ARCH-01 | — | N/A | unit | `./mvnw test -Dtest=CarServiceTest` | ✅ | ⬜ pending |
| 13-02-02 | 02 | 2 | ARCH-01 | — | N/A | unit | `./mvnw test -Dtest=TrackServiceTest` | ✅ | ⬜ pending |
| 13-02-03 | 02 | 2 | ARCH-01 | — | N/A | unit | `./mvnw test -Dtest=DriverServiceTest` | ✅ | ⬜ pending |
| 13-02-04 | 02 | 2 | ARCH-01 | — | N/A | unit | `./mvnw test -Dtest=RaceScoringServiceTest` | ✅ | ⬜ pending |
| 13-02-05 | 02 | 2 | ARCH-01 | — | N/A | unit | `./mvnw test -Dtest=MatchScoringServiceTest` | ✅ | ⬜ pending |
| 13-03-01 | 03 | 2 | ARCH-01 | — | N/A | unit | `./mvnw test -Dtest=SeasonManagementServiceTest` | ✅ | ⬜ pending |
| 13-03-02 | 03 | 2 | ARCH-01 | — | N/A | unit | `./mvnw test -Dtest=TeamManagementServiceTest` | ✅ | ⬜ pending |
| 13-03-03 | 03 | 2 | ARCH-01 | — | N/A | unit | `./mvnw test -Dtest=PlayoffSeedingServiceTest` | ✅ | ⬜ pending |
| 13-03-04 | 03 | 2 | ARCH-01 | — | N/A | unit | `./mvnw test -Dtest=MatchdayServiceTest` | ✅ | ⬜ pending |
| 13-03-05 | 03 | 2 | ARCH-01, ARCH-02, FEAT-02 | — | N/A | full | `./mvnw verify -Pe2e` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Existing infrastructure covers all phase requirements. Test content updates (not new files) are needed within existing test classes.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Admin UI pages render correctly after refactoring | Success Criteria 4 | Visual verification of page rendering | Navigate to Standings, PowerRankings, Playoff, TeamCard, CsvImport pages and verify unchanged behavior |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 90s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
