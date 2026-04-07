---
phase: 16
slug: merge-service-core
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-07
---

# Phase 16 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito (unit), Spring Boot Test (integration) |
| **Config file** | `pom.xml` (Surefire + Failsafe) |
| **Quick run command** | `./mvnw test -pl . -Dtest=DriverMergeServiceTest` |
| **Full suite command** | `./mvnw verify` |
| **Estimated runtime** | ~60 seconds (full suite) |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test -pl . -Dtest=DriverMergeServiceTest`
- **After every plan wave:** Run `./mvnw verify`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 16-01-01 | 01 | 1 | MERGE-05 | — | N/A | unit | `./mvnw test -Dtest=DriverMergeServiceTest#givenSourceDriver_whenMerge_thenSeasonDriversReassigned` | ❌ W0 | ⬜ pending |
| 16-01-02 | 01 | 1 | MERGE-06 | — | N/A | unit | `./mvnw test -Dtest=DriverMergeServiceTest#givenSourceDriver_whenMerge_thenRaceLineupsReassigned` | ❌ W0 | ⬜ pending |
| 16-01-03 | 01 | 1 | MERGE-07 | — | N/A | unit | `./mvnw test -Dtest=DriverMergeServiceTest#givenSourceDriver_whenMerge_thenRaceResultsReassigned` | ❌ W0 | ⬜ pending |
| 16-01-04 | 01 | 1 | MERGE-08 | — | N/A | unit | `./mvnw test -Dtest=DriverMergeServiceTest#givenSourceDriver_whenMerge_thenPsnAliasesTransferred` | ❌ W0 | ⬜ pending |
| 16-01-05 | 01 | 1 | MERGE-09 | — | N/A | unit | `./mvnw test -Dtest=DriverMergeServiceTest#givenSourceDriver_whenMerge_thenSourceDriverDeleted` | ❌ W0 | ⬜ pending |
| 16-01-06 | 01 | 1 | MERGE-10 | — | N/A | unit | `./mvnw test -Dtest=DriverMergeServiceTest#givenSameDriver_whenMerge_thenBusinessRuleException` | ❌ W0 | ⬜ pending |
| 16-01-07 | 01 | 1 | MERGE-14 | — | N/A | unit | `./mvnw test -Dtest=DriverMergeServiceTest#givenSourceDriver_whenMerge_thenMergeLogged` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/org/ctc/domain/service/DriverMergeServiceTest.java` — stubs for all MERGE requirements
- [ ] Repository method stubs: `RaceLineupRepository.findByDriverId()`, `PsnAliasRepository.findByDriverId()`

*Existing test infrastructure (JUnit 5, Mockito, AssertJ) covers all framework needs.*

---

## Manual-Only Verifications

*All phase behaviors have automated verification.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
