---
phase: 66
slug: team-shortname-collision-fix
status: approved
nyquist_compliant: true
wave_0_complete: true
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
| 66-01-01 | 01 | 1 | D-11 (TDD RED) | T-66-01 (Information Disclosure mitigation) | Resolver returns parent on multi-match | unit | `./mvnw test -Dtest=DriverSheetImportServiceTest#givenTeamsWithSameShortNameParentAndSub_whenPreview_thenResolvesParentTeam` | ❌ W0 | ✅ |
| 66-01-02 | 01 | 2 | D-03 (repo method) | — | `findAllByShortName` derives valid JPQL | unit (transitive via 66-01-04 + existing IT) | `./mvnw test -Dtest=DriverSheetImportServiceTest` | ❌ W0 | ✅ |
| 66-01-03 | 01 | 2 | D-01, D-06, D-07 (resolver + 5 call sites) | T-66-02 (DoS mitigation) | Multi-match → parent; single → use; empty → UNKNOWN_TEAM_CODE | unit | `./mvnw test -Dtest=DriverSheetImportServiceTest#givenTeamsWithSameShortNameParentAndSub_whenPreview_thenResolvesParentTeam` | ❌ W0 | ✅ |
| 66-01-04 | 01 | 2 | D-13 (18-stub migration) | — | All existing test paths green with new mock signature | unit | `./mvnw test -Dtest=DriverSheetImportServiceTest` | ❌ W0 | ✅ |
| 66-01-05 | 01 | 3 | D-12 (defensive) | — | Two parent matches → first wins, log.warn, no exception | unit | `./mvnw test -Dtest=DriverSheetImportServiceTest#givenTwoParentTeamsWithSameShortName_whenPreview_thenFirstWinsWithoutException` | ❌ W0 | ✅ |
| 66-01-06 | 01 | 4 (gate) | JaCoCo gate | — | BUNDLE LINE ≥ 0.82 | gate | `./mvnw verify` | ✅ exists | ✅ |

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

---

## Validation Audit 2026-05-08 (Phase 69 SC6 — D-13 methodology mirror of Phase 64)

**Verdict:** `nyquist_compliant: true` / `wave_0_complete: true`. All 6 Per-Task Verification Map rows confirmed COVERED by tests committed during Phase 66 plan execution (plans 66-01 + 66-02 + 66-03). Audit log: `.planning/phases/69-milestone-closure-hygiene/.work/66-validate-phase.log`.

**Methodology:** Mirrors Phase 64 sweep (`64-01-SUMMARY.md`). Each Per-Task row audited row-by-row against the actual `src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java` source + the `66-{01,02,03}-SUMMARY.md` evidence chain.

**Test-method evolution note:** The Per-Task Verification Map references the original 66-01 RED-phase test names (`givenTeamsWithSameShortNameParentAndSub_whenPreview_thenResolvesParentTeam` for D-11; `givenTwoParentTeamsWithSameShortName_whenPreview_thenFirstWinsWithoutException` for D-12). Plans 66-02 + 66-03 evolved the resolver to be **phase-aware** (the resolver now considers regular-phase team membership). The original test names were superseded by 4 more-precise successor tests in the same file:

| Original D-11/D-12 contract | Successor test (current name + line) |
|----|----|
| D-11 multi-match → parent (sub-aware) | `givenTeamsWithSameShortNameAndSubHasPhaseTeam_whenPreview_thenResolvesSubTeam` (line 734) |
| D-11 multi-match → fallback parent precedence (no candidate has phase team) | `givenTeamsWithSameShortNameAndNoCandidateHasPhaseTeam_whenPreview_thenFallsBackToParentPrecedence` (line 771) |
| D-11 collision when season has no regular phase | `givenSeasonHasNoRegularPhase_whenPreviewWithCollision_thenFallsBackToParentPrecedence` (line 817) |
| D-12 two parent teams, first wins, no exception | `givenLegacyPath_whenTwoParentTeamsCollideWithoutRegularPhase_thenFirstParentWinsWithoutException` (line 847) |

The D-11/D-12 contract is COVERED by these 4 successor tests; the 27/27 DriverSheetImportServiceTest pass + 1235/0/0 full-suite pass (per 66-03-SUMMARY.md metrics) reaffirms green status.

**REQ-ID coverage reinforced by Phase 66:**
- **IMPORT-04** (Phase 59 DriverSheet import — driver/team mapping) — Phase 66 fixed the team-shortname collision in `DriverSheetImportService.preview/execute` that produced `NonUniqueResultException` 500 pages when a parent team and one of its sub-teams share the same `shortName`. The resolver helper `resolveTeamByShortName(String, SeasonPhase)` (line 435) implements the parent-precedence + phase-aware fallback policy; 5 production call sites (lines 144, 155, 175, 204, 309) all routed through the helper.

**Auto-fill triggered:** none. Path A (mechanical flip) per D-13 first branch. The 18 mock-stub migrations + 2 new TDD tests + 1 new repository method + 1 new service helper Wave 0 entries all confirmed present in `src/main/java` and `src/test/java` per 66-01-SUMMARY.md commit chain (`dd123e0` RED → `d204624` repo → `4d26b75` fix).

**Manual-Only escalations (per D-15 — concrete `Why Manual` rationale):**

| # | Behavior | Why Manual |
|---|----------|------------|
| 1 | End-to-end driver import on a sheet with parent + sub same `shortName` (Phase Goal) | **Why Manual:** Live integration with Google Sheets API not exercised by Surefire/Mockito (no live OAuth in CI; sheet data shape requires production-like fixtures with parent + sub colliding shortName). Visual confirmation that admin UI renders without 500 page is the Manual-Only verifier; Mockito tests cover the resolver contract at unit level but cannot exercise the full controller → service → JPA → template path on live data. |

This Manual-Only row is documented in § "Manual-Only Verifications" above with concrete Test Instructions (boot dev,demo profile + URL `/admin/drivers/import` + paste sheet URL + Preview click + pass/fail criteria).

**Coverage delta vs. baseline:** `JaCoCo line` measured at Phase 66 close: 0.8561 (66-01 + 66-03 metrics). Pre-Phase-66 baseline: 0.8561 (Phase 65 close). Delta: 0.0 (hotfix at constant coverage; new resolver helper + tests absorbed into existing surface).

_Audited 2026-05-08 (Phase 69 SC6 — milestone closure hygiene)_
_Branch: gsd/v1.9-season-phases-groups_
