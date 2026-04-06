---
phase: 7
slug: layer-cleanup
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-04
---

# Phase 7 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + MockMvc |
| **Config file** | `pom.xml` (Surefire/Failsafe plugins) |
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
| 07-01-01 | 01 | 1 | FEAT-02 | unit | `./mvnw test -Dtest="StandingsServiceTest"` | ✅ needs extension | ⬜ pending |
| 07-01-02 | 01 | 1 | ARCH-02 | unit | `./mvnw test -Dtest="SeasonManagementServiceTest"` | ✅ needs extension | ⬜ pending |
| 07-02-01 | 02 | 2 | ARCH-02 | integration | `./mvnw test -Dtest="StandingsControllerTest,PowerRankingsControllerTest,CsvImportControllerTest,TeamCardControllerTest,PlayoffControllerTest"` | ✅ all exist | ⬜ pending |
| 07-03-01 | 03 | 3 | ARCH-01 | grep | `grep -r "import org.ctc.admin.dto" src/main/java/org/ctc/domain/service/ \| wc -l` (must be 0) | N/A | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `StandingsServiceTest` — add test for `calculateStandingsWithBuchholz()`
- [ ] New unit tests for service finder methods (findActiveSeason, findSeasonTeamById, findRoundById)
- [ ] Verify no circular dependency in StandingsService after Buchholz integration

*Existing infrastructure covers most phase requirements — Wave 0 extends existing test classes.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Admin UI pages render correctly | ARCH-02, FEAT-02 | Visual rendering check | Start dev server, navigate to Standings, PowerRankings, TeamCard, Playoff, CsvImport pages |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
