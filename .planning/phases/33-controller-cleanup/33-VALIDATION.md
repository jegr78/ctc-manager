---
phase: 33
slug: controller-cleanup
status: validated
nyquist_compliant: true
wave_0_complete: true
created: 2026-04-14
audited: 2026-04-14
---

# Phase 33 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + Spring Boot Test (SiteGeneratorServiceTest) |
| **Config file** | `pom.xml` (surefire + failsafe) |
| **Quick run command** | `./mvnw test -Dtest=SeasonManagementServiceTest,MatchdayServiceTest,DriverServiceTest,SiteGeneratorServiceTest` |
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
| 33-01-01 | 01 | 1 | ARCH-03 | — | SeasonManagementService.getSeasonGroupOptions() replaces inline controller logic | unit | `./mvnw test -Dtest=SeasonManagementServiceTest$GetSeasonGroupOptions` | ✅ | ✅ green |
| 33-01-02 | 01 | 1 | ARCH-03 | — | MatchdayDetailData carries graphic status fields | unit | `./mvnw test -Dtest=MatchdayServiceTest$GetMatchdayDetailGraphicStatus` | ✅ | ✅ green |
| 33-01-03 | 01 | 1 | ARCH-03 | — | DriverService.getMergeFormDrivers() replaces inline controller logic | unit | `./mvnw test -Dtest=DriverServiceTest$GetMergeFormDrivers` | ✅ | ✅ green |
| 33-02-01 | 02 | 1 | ARCH-04 | — | SiteGeneratorService uses RaceLineup-first for driver-team resolution | integration | `./mvnw test -Dtest=SiteGeneratorServiceTest` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

*Existing infrastructure covers all phase requirements.*

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
| Automated COVERED | 4 |

Reconstructed from phase artifacts (State B). All tests existed from TDD execution during phase implementation.

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 120s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** validated 2026-04-14
