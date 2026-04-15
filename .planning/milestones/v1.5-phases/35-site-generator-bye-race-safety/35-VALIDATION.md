---
phase: 35
slug: site-generator-bye-race-safety
status: validated
nyquist_compliant: true
wave_0_complete: true
created: 2026-04-14
audited: 2026-04-14
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
| 35-01-01 | 01 | 1 | DATA-03 | — | Bye race does not NPE during site generation | integration | `./mvnw test -pl . -Dtest=SiteGeneratorServiceTest#givenByeRaceInSeason_whenGenerate_thenCompletesWithoutNPE` | ✅ | ✅ green |
| 35-01-02 | 01 | 1 | DATA-03 | — | Zero unsafe getHomeTeam() calls remain | structural | `grep -c "race.getHomeTeam().getShortName()" SiteGeneratorService.java` (returns 0) | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `givenByeRaceInSeason_whenGenerate_thenCompletesWithoutNPE` — added in Plan 01 Task 1 (commit c17a801)

*All Wave 0 tests implemented and passing.*

---

## Manual-Only Verifications

*All phase behaviors have automated verification.*

---

## Validation Audit 2026-04-14

| Metric | Count |
|--------|-------|
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |
| Manual-Only | 0 |
| Automated COVERED | 2 |

All Wave 0 tests were implemented during phase execution (Plan 01). No new gaps detected.

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 120s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** validated 2026-04-14
