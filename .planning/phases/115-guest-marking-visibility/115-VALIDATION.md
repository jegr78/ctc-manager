---
phase: 115
slug: guest-marking-visibility
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-01
---

# Phase 115 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + Spring Boot Test + AssertJ |
| **Config file** | `pom.xml` (Surefire + Failsafe, JaCoCo) |
| **Quick run command** | `./mvnw clean test -Dtest=<affected-test-class>` |
| **Full suite command** | `./mvnw clean verify -Pe2e` |
| **Estimated runtime** | full suite ~7+ min; targeted unit ~30–60s |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw clean test -Dtest=<affected-test-class>` (targeted TDD loop)
- **After every plan wave:** Run `./mvnw clean verify` (no e2e — display-only phase can defer Playwright to the phase gate)
- **Before `/gsd:verify-work`:** `./mvnw clean verify -Pe2e` must be green
- **Max feedback latency:** ~60 seconds (targeted), full gate at phase end

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 115-01-* | 01 | 1 | — (shared CSS/fragment scaffold) | — | N/A | manual | playwright-cli screenshot | ✅ append admin.css/style.css | ⬜ pending |
| 115-02-* | 02 | 2 | MARK-01 | — | `th:text` HTML-escapes driver name | unit | `./mvnw clean test -Dtest=ResultsGraphicServiceTest` | ✅ extend | ⬜ pending |
| 115-03-* | 03 | 2 | MARK-02 | — | `th:text` HTML-escapes driver name | unit | `./mvnw clean test -Dtest=ProvisionalScoresGraphicServiceTest` | ✅ extend | ⬜ pending |
| 115-04-* | 04 | 2 | MARK-03 | — | `th:text` HTML-escapes driver name | unit | `./mvnw clean test -Dtest=LineupGraphicServiceTest` | ✅ extend | ⬜ pending |
| 115-05-* | 05 | 2 | MARK-04 | — | N/A (display-only, admin route) | unit | `./mvnw clean test -Dtest=RaceServiceTest` | ✅ extend | ⬜ pending |
| 115-06-* | 06 | 2 | MARK-05 | — | N/A (public read, no input) | integration | `./mvnw clean verify -Dit.test=DriverRankingServiceGuestIT -DfailIfNoTests=false` | ✅ extend | ⬜ pending |
| 115-07-* | 07 | 2 | MARK-06 | T-115-LOG | `fieldingTeamName` sanitized if logged | integration | `./mvnw clean verify -Dit.test=DriverProfilePageGeneratorIT -DfailIfNoTests=false` | ✅ extend | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

*Plan/wave/task IDs above are indicative; the planner finalizes the exact decomposition. The requirement→test mapping is the binding contract.*

---

## Wave 0 Requirements

*Existing infrastructure covers all phase requirements.* No new test files or framework config needed. The work is "add guest assertions to existing tests" (`ResultsGraphicServiceTest`, `ProvisionalScoresGraphicServiceTest`, `LineupGraphicServiceTest`, `RaceServiceTest`, `DriverRankingServiceGuestIT`, `DriverProfilePageGeneratorIT`), not "create new test infrastructure". Guest fixtures `Test_Guest_1` and `Test_DualRole_1` already exist in `TestDataService`.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| `matchday-detail.html` renders `★` on guest lineup rows | MARK-04 | Template-only change (OSIV `lu.guest` access); no new Java logic to unit-test | playwright-cli screenshot of admin matchday-detail with a `T-` guest lineup |
| `★` in amber (`#f59e0b`) precedes guest driver names on all marked surfaces; distinguishable at 1x on dark bg; no legend | SC-1 / all MARK | Visual treatment confirmed against rendered reference (CONTEXT D-01/D-03, ROADMAP SC-1) | `./scripts/app.sh start dev` + playwright-cli screenshots to `.screenshots/115-*.png` (results-render, provisional-scores, lineup-render, race-detail, matchday-detail, driver-ranking, driver-profile); share paths, await explicit user approval before phase close |
| No graphic posts alongside others with unmarked guests | SC-2 | Cross-surface visual consistency check | Compare all `.screenshots/115-*.png` graphic renders side by side |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or are listed under Manual-Only with explicit instructions
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (none — existing infra)
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s (targeted)
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
