---
phase: 108
slug: missing-driver-n-a-rendering
status: validated
nyquist_compliant: true
nyquist_status: compliant
wave_0_complete: true
open_validation_items: 0
created: 2026-05-31
validated: 2026-05-31
---

# Phase 108 — Validation Strategy

> Per-phase validation contract, reconstructed retroactively from phase artifacts
> (108-01/02/03-SUMMARY.md) during `/gsd-audit-milestone v1.15` follow-up.
> All four requirements have green automated unit tests — no Wave 0 gap, no manual-only items.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito (Surefire unit tests) |
| **Config file** | `pom.xml` (maven-surefire-plugin) |
| **Quick run command** | `./mvnw test -Dtest='LineupGraphicServiceTest,ResultsGraphicServiceTest,ProvisionalScoresGraphicServiceTest,ScoringServiceTest'` |
| **Full suite command** | `./mvnw clean verify -Pe2e` |
| **Estimated runtime** | targeted unit run ~6 s; full suite = standard CI build |

---

## Sampling Rate

- **After every task commit:** Run the affected service's unit test (`-Dtest=…`).
- **After every plan wave:** `./mvnw test` (Surefire) for the touched service tests.
- **Before `/gsd-verify-work`:** `./mvnw clean verify -Pe2e` green.
- **Max feedback latency:** targeted unit run < 10 s.

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------------|-----------|-------------------|-------------|--------|
| 108-01 | 108-01 | 1 | LINEUP-01 | Lineup graphic pads to 6 rows; missing slot driver = `"n/a"` (no blank/null) | unit | `-Dtest=LineupGraphicServiceTest` | ✅ | ✅ green |
| 108-01 | 108-01 | 1 | LINEUP-02 | Results graphic pads to 6 rows; missing slot = `"n/a"` + 0 points | unit | `-Dtest=ResultsGraphicServiceTest` | ✅ | ✅ green |
| 108-02 | 108-02 | 1 | LINEUP-03 | Provisional-scores graphic padded to 6 rows; missing rows render `"n/a"`/0, totals unchanged | unit | `-Dtest=ProvisionalScoresGraphicServiceTest` | ✅ | ✅ green |
| 108-03 | 108-03 | 1 | LINEUP-04 | ScoringService records 0 points / no position for missing driver at save time; no NPE; no fallback in template/controller | unit | `-Dtest=ScoringServiceTest` (`givenHomeTeamWithFewerThanSixDrivers_…`) | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky.*

**Evidence (re-run 2026-05-31):** `./mvnw test -Dtest='LineupGraphicServiceTest,ResultsGraphicServiceTest,ProvisionalScoresGraphicServiceTest,ScoringServiceTest'` → **Tests run: 51, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS.**

Key assertions:
- `LineupGraphicServiceTest`: `buildPairings` `hasSize(6)`; padded slots `homeDriver()/awayDriver()` `== "n/a"` (lines 79–89, 120–126, 167–175).
- `ResultsGraphicServiceTest`: `buildResultRows` `hasSize(6)`; padded slots `== "n/a"` + 0 points (lines 66–83, 111–117, 149–162).
- `ProvisionalScoresGraphicServiceTest`: `homeRows`/`awayRows` `hasSize(6)`; index-5 padded row `driverName() == "n/a"`, `total == 0`; `homeOverallTotal == 59` / `awayOverallTotal == 42` pinned unchanged (lines 218–222).
- `ScoringServiceTest.givenHomeTeamWithFewerThanSixDrivers_whenAggregateMatchScores_thenTotalsEqualSumOfRealDrivers`: 3-driver home + 2-driver away aggregate to real-driver sums, no NPE, no defensive guard added to the service (D-02 / LINEUP-04 confirmed at source).

---

## Wave 0 Requirements

Existing JUnit 5 + Mockito infrastructure covers all phase requirements — no new test
framework introduced. The graphic services (Lineup/Results/ProvisionalScores) are
Playwright-rendered and JaCoCo-excluded, but their **context-building** logic
(`buildPairings`/`buildResultRows`/`buildContext`) is pure and unit-tested without
Playwright — the n/a padding is fully verifiable in Surefire.

---

## Manual-Only Verifications

The `.empty-slot` de-emphasis (D-05, opacity/colour treatment) was visually approved via a
user visual-checkpoint on faithful CSS mockups (`.screenshots/108-{lineup,results,provisional}-na.png`,
"Passt"). The data behaviour (6-row padding, `"n/a"`, 0 points) — the substance of
LINEUP-01..04 — is fully automated above. No outstanding manual-only verification items.

---

## Validation Sign-Off

- [x] All requirements have automated verify (LINEUP-01..04 → green unit tests)
- [x] Sampling continuity: no gap — every requirement maps to a green test
- [x] Wave 0 covers all references (existing infra sufficient)
- [x] No watch-mode flags
- [x] Feedback latency < 10 s (targeted unit run)
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-05-31 (reconstructed; all four LINEUP requirements green).

---

*Phase: 108-missing-driver-n-a-rendering*
*Validation strategy reconstructed & validated: 2026-05-31*
