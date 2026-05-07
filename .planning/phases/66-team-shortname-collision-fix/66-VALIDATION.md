---
phase: 66
slug: team-shortname-collision-fix
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-07
---

# Phase 66 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter) + Mockito 5 + AssertJ |
| **Config file** | `pom.xml` (Surefire + JaCoCo plugins, line coverage gate ≥ 0.82) |
| **Quick run command** | `./mvnw test -Dtest=DriverSheetImportServiceTest` |
| **Full suite command** | `./mvnw verify` |
| **Estimated runtime** | Quick: ~10s · Full: ~3–5min |

Out-of-phase: `./mvnw verify -Pe2e` (Playwright E2E) — UAT only per project memory `feedback_e2e_verification.md`.

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test -Dtest=DriverSheetImportServiceTest`
- **After every plan wave:** Run `./mvnw verify`
- **Before `/gsd-verify-work`:** Full suite must be green; JaCoCo BUNDLE LINE ≥ 0.82
- **Max feedback latency:** 10 seconds (quick command)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 66-01-01 | 01 | 1 | D-11 (TDD RED) | T-66-01 (Information Disclosure mitigation) | Resolver returns parent on multi-match | unit | `./mvnw test -Dtest=DriverSheetImportServiceTest#givenTeamsWithSameShortNameParentAndSub_whenPreview_thenResolvesParentTeam` | ❌ W0 | ⬜ pending |
| 66-01-02 | 01 | 2 | D-03 (repo method) | — | `findAllByShortName` derives valid JPQL | unit (transitive via 66-01-04 + existing IT) | `./mvnw test -Dtest=DriverSheetImportServiceTest` | ❌ W0 | ⬜ pending |
| 66-01-03 | 01 | 2 | D-01, D-06, D-07 (resolver + 5 call sites) | T-66-02 (DoS mitigation) | Multi-match → parent; single → use; empty → UNKNOWN_TEAM_CODE | unit | `./mvnw test -Dtest=DriverSheetImportServiceTest#givenTeamsWithSameShortNameParentAndSub_whenPreview_thenResolvesParentTeam` | ❌ W0 | ⬜ pending |
| 66-01-04 | 01 | 2 | D-13 (18-stub migration) | — | All existing test paths green with new mock signature | unit | `./mvnw test -Dtest=DriverSheetImportServiceTest` | ❌ W0 | ⬜ pending |
| 66-01-05 | 01 | 3 | D-12 (defensive) | — | Two parent matches → first wins, log.warn, no exception | unit | `./mvnw test -Dtest=DriverSheetImportServiceTest#givenTwoParentTeamsWithSameShortName_whenPreview_thenFirstWinsWithoutException` | ❌ W0 | ⬜ pending |
| 66-01-06 | 01 | 4 (gate) | JaCoCo gate | — | BUNDLE LINE ≥ 0.82 | gate | `./mvnw verify` | ✅ exists | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java` — add 2 new test methods:
  - [ ] `givenTeamsWithSameShortNameParentAndSub_whenPreview_thenResolvesParentTeam` (D-11)
  - [ ] `givenTwoParentTeamsWithSameShortName_whenPreview_thenFirstWinsWithoutException` (D-12)
- [ ] `src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java` — migrate 18 existing stubs (lines 243, 270, 294, 325, 356, 386, 459, 484, 485, 511, 543, 544, 573, 601, 672, 699, 732, 755) — see `66-RESEARCH.md` § "Test Stub Migration Map"
- [ ] `src/main/java/org/ctc/domain/repository/TeamRepository.java` — add `List<Team> findAllByShortName(String shortName)`
- [ ] `src/main/java/org/ctc/dataimport/DriverSheetImportService.java` — add private helper `resolveTeamByShortName(String) -> Optional<Team>` and replace 5 call sites (lines 135, 146, 166, 195, 296)

*No framework install required — JUnit 5 + Mockito + AssertJ + Surefire all pre-configured. No new fixtures — existing `setupSheetsStub()` and `oneDataRow()` helpers cover the input shape.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| End-to-end driver import on a sheet with parent + sub same `shortName` | Phase Goal | Requires real Google Sheet + admin login + visual confirmation no 500 page | 1) Start `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo`. 2) Visit `/admin/drivers/import`. 3) Paste a sheet URL whose data has rows referencing a team-code that exists as parent + sub. 4) Click Preview. 5) Confirm: page renders without 500; preview table shows the row mapped to the parent team's name; no banner error. |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (2 new tests + 18 stub migrations + 1 repo method + 1 service helper)
- [ ] No watch-mode flags
- [ ] Feedback latency < 10s for quick command
- [ ] `nyquist_compliant: true` set in frontmatter (after Wave 0 complete)

**Approval:** pending
