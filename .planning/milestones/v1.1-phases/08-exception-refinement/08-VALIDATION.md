---
phase: 8
slug: exception-refinement
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-05
---

# Phase 8 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + Spring MockMvc |
| **Config file** | `pom.xml` (surefire + failsafe plugins) |
| **Quick run command** | `./mvnw test -pl .` |
| **Full suite command** | `./mvnw verify` |
| **Estimated runtime** | ~60 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test -pl .`
- **After every plan wave:** Run `./mvnw verify`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 60s

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 08-01-01 | 01 | 1 | ERRH-01 | unit | `./mvnw test -Dtest=MatchdayControllerTest,PlayoffControllerTest,RaceControllerTest,PowerRankingsControllerTest,TeamCardControllerTest -pl .` | Existing | ⬜ pending |
| 08-01-02 | 01 | 1 | ERRH-01 | unit | `./mvnw test -Dtest=CarServiceTest,TrackServiceTest,TeamManagementServiceTest -pl .` | Existing | ⬜ pending |
| 08-01-03 | 01 | 1 | ERRH-01 | unit | `./mvnw test -Dtest=CsvImportControllerTest,Gt7SyncControllerTest -pl .` | Existing | ⬜ pending |
| 08-02-01 | 02 | 2 | QUAL-02 | unit | `./mvnw test -Dtest=RaceServiceTest -pl .` | Existing | ⬜ pending |
| 08-02-02 | 02 | 2 | QUAL-02 | unit | `./mvnw test -Dtest=DriverRankingServiceTest -pl .` | Existing | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Existing infrastructure covers all phase requirements. Exception narrowing is primarily a compile-time change (wrong catch types cause compile errors). Existing tests verify behavior is preserved.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Unexpected exceptions show admin error page | ERRH-01 | Requires triggering unexpected exception at runtime | Trigger server error in dev mode, verify error.html renders |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
