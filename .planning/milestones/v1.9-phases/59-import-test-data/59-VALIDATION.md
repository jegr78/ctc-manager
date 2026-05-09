---
phase: 59
slug: import-test-data
status: approved
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-07
mode: retroactive
---

# Phase 59 — Validation Strategy

> Retroactive Nyquist audit. Phase 59 delivered the driver-sheet importer with season disambiguation, group resolution via PhaseTeam, TabWarning emission, and a fully rebuilt TestDataService seeding the new phase/group model. All core unit + integration tests were green at Phase 59 close. One IT test has since regressed due to seed data drift introduced in a later phase.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + Spring Boot Test 4.x |
| **Config file** | [pom.xml](../../pom.xml) — Surefire + JaCoCo, line gate `<minimum>0.82</minimum>` |
| **Quick run command** | `./mvnw test -Dtest=<ClassName>` |
| **Full suite command** | `./mvnw verify` — Surefire + JaCoCo gate (~80 s on dev hardware) |
| **Coverage at phase close** | 86.63% line coverage (per 59-05 SUMMARY) — well above 82% gate |
| **Coverage at audit date** | Not re-measured (audit is documentation-only; see Validation Audit appendix) |

---

## Sampling Rate

Phase 59 was already executed. The retroactive sampling contract:

- **After every plan wave:** Targeted Surefire (`./mvnw test -Dtest=…`) — feedback < 30 s
- **After Phase 59 close:** `./mvnw verify` (Surefire + JaCoCo) green at 86.63% per 59-05 SUMMARY
- **At audit 2026-05-07:** Targeted test runs per REQ-ID (see Per-Task Verification Map); one BLOCKER surfaced

---

## Per-Task Verification Map

Phase 59 = 5 plans (59-01..59-05). The map below covers all 6 unique REQ-IDs enumerated from PLAN frontmatters.

| Task | Plan | Requirement | Behavior under test | Test Type | Test File / Evidence | Automated Command | Status |
|------|------|-------------|---------------------|-----------|----------------------|-------------------|--------|
| 59-01 | 01 | IMPORT-02 | `SeasonManagementService.findUnique(int, int)` returns Optional.empty() on 0 hits, Optional.of on 1 hit, throws BusinessRuleException with verbatim message on >1 hits; same contract for `findUnique(int)` one-arg overload | Unit (Mockito) | [SeasonManagementServiceTest.java:957-1025](../../src/test/java/org/ctc/domain/service/SeasonManagementServiceTest.java#L957-L1025) — 6 `@Test` methods covering 0/1/many for each overload | `./mvnw test -Dtest=SeasonManagementServiceTest#givenNoSeason_whenFindUniqueByYearAndNumber_thenReturnsEmpty+givenExactlyOneSeason_whenFindUniqueByYearAndNumber_thenReturnsOptionalOf+givenMultipleSeasons_whenFindUniqueByYearAndNumber_thenThrowsBusinessRule+givenNoSeason_whenFindUniqueByYear_thenReturnsEmpty+givenExactlyOneSeasonForYear_whenFindUniqueByYear_thenReturnsOptionalOf+givenMultipleSeasonsForYear_whenFindUniqueByYear_thenThrowsBusinessRule` | ✅ |
| 59-05 | 05 | IMPORT-02 (gap closure) | `preview()` no longer throws `UnexpectedRollbackException` when `findUnique` throws `BusinessRuleException` on an ambiguous legacy tab; `ambiguousReason` surfaced cleanly without poisoning the outer transaction | Integration (no class-level `@Transactional`) | [DriverSheetImportServiceTransactionIT.java:122-170](../../src/test/java/org/ctc/dataimport/DriverSheetImportServiceTransactionIT.java#L122-L170) — `givenAmbiguousLegacyTab_whenPreview_thenReturnsTabWithAmbiguousReasonWithoutRollbackException` + `givenTestClass_whenCheckingClassAnnotations_thenIsNotTransactional` | `./mvnw test -Dtest=DriverSheetImportServiceTransactionIT` | ✅ |
| 59-02 | 02 | IMPORT-01 | `DriverSheetImportService` accepts both `^\d{4}$` and `^\d{4}_S\d+$` tab patterns; `2023_S1` resolved via `findUnique(year, number)`, `2024` via `findUnique(year)` | Unit (Mockito) | [DriverSheetImportServiceTest.java:569-619](../../src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java#L569-L619) — `givenLegacyFourDigitTab_whenPreview_thenSeasonResolvedViaFindUniqueByYear` + `givenNumberedTab_whenPreview_thenSeasonResolvedViaFindUniqueByYearAndNumber` | `./mvnw test -Dtest=DriverSheetImportServiceTest#givenLegacyFourDigitTab_whenPreview_thenSeasonResolvedViaFindUniqueByYear+givenNumberedTab_whenPreview_thenSeasonResolvedViaFindUniqueByYearAndNumber` | ✅ |
| 59-02 | 02 | IMPORT-01 (IT path) | Legacy `2024` tab resolves to the single 2024 LEAGUE season seeded by TestDataService; `suggestedSeasonId` non-null, `number` is null | Integration (`@SpringBootTest`) | [DriverSheetImportServiceIT.java:114-131](../../src/test/java/org/ctc/dataimport/DriverSheetImportServiceIT.java#L114-L131) — `givenLegacyYearTab_whenPreview_thenSeasonAutoResolvedToLeague2024` | `./mvnw test -Dtest=DriverSheetImportServiceIT#givenLegacyYearTab_whenPreview_thenSeasonAutoResolvedToLeague2024` | ❌ BLOCKER — see Escalated section |
| 59-02 / 59-04 | 02, 04 | IMPORT-03 | Group membership resolved via `PhaseTeam(REGULAR) → SeasonPhaseGroup.name`; `resolvedGroupName` set on row records; `PhaseTeamRepository.findByPhaseIdAndTeamId` finder works correctly | Unit + Integration | [DriverSheetImportServiceTest.java:659-748](../../src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java#L659-L748) — 2 unit tests (GroupA + no-group); [DriverSheetImportServiceIT.java:189-235](../../src/test/java/org/ctc/dataimport/DriverSheetImportServiceIT.java#L189-L235) — `givenDriverInGroupATeam` + `givenDriverInGroupBTeam`; [PhaseTeamRepositoryTest.java:49-108](../../src/test/java/org/ctc/domain/repository/PhaseTeamRepositoryTest.java#L49-L108) — 3 repo tests | `./mvnw test -Dtest=DriverSheetImportServiceTest#givenTeamInGroupA_whenPreview_thenResolvedGroupNameSet,DriverSheetImportServiceIT#givenDriverInGroupATeam_whenPreview_thenResolvedGroupNameIsGroupA+givenDriverInGroupBTeam_whenPreview_thenResolvedGroupNameIsGroupB,PhaseTeamRepositoryTest` | ✅ |
| 59-02 / 59-04 | 02, 04 | IMPORT-04 | `TabWarning` emitted for every team with no PhaseTeam in REGULAR phase; deduplicated per team short name across multiple rows | Unit + Integration | [DriverSheetImportServiceTest.java:690-748](../../src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java#L690-L748) — `givenTeamMissingFromRegularPhase_whenPreview_thenWarningEmitted` + `givenTwoRowsSameMissingTeam_whenPreview_thenSingleWarningEmitted`; [DriverSheetImportServiceIT.java:236-269](../../src/test/java/org/ctc/dataimport/DriverSheetImportServiceIT.java#L236-L269) — `givenTeamNotInRegularPhase_whenPreview_thenSingleTabWarningEmittedAndDeduplicated` | `./mvnw test -Dtest=DriverSheetImportServiceTest#givenTeamMissingFromRegularPhase_whenPreview_thenWarningEmitted+givenTwoRowsSameMissingTeam_whenPreview_thenSingleWarningEmitted,DriverSheetImportServiceIT#givenTeamNotInRegularPhase_whenPreview_thenSingleTabWarningEmittedAndDeduplicated` | ✅ |
| 59-03 | 03 | DATA-01 | `TestDataService` creates ONE consolidated 2023 season with GROUPS-layout REGULAR phase containing 2 named groups (Group A sortIndex=0, Group B sortIndex=1) and 12 PhaseTeam rows (6+6); LEAGUE seasons get PhaseTeam rows with group=null | Integration (`@SpringBootTest @ActiveProfiles("dev")`) | [TestDataServiceIntegrationTest.java:263-323](../../src/test/java/org/ctc/admin/TestDataServiceIntegrationTest.java#L263-L323) — `givenDevSeed_whenStarted_thenConsolidated2023HasOneRegularGroupsPhase`, `givenDevSeed_whenStarted_thenConsolidated2023HasTwoNamedGroupsInOrder`, `givenDevSeed_whenStarted_thenConsolidated2023HasTwelvePhaseTeamsSplitSixSix`, `givenDevSeed_whenStarted_thenLeagueSeasonsHavePhaseTeamsWithNullGroup` | `./mvnw test -Dtest=TestDataServiceIntegrationTest#givenDevSeed_whenStarted_thenConsolidated2023HasOneRegularGroupsPhase+givenDevSeed_whenStarted_thenConsolidated2023HasTwoNamedGroupsInOrder+givenDevSeed_whenStarted_thenConsolidated2023HasTwelvePhaseTeamsSplitSixSix+givenDevSeed_whenStarted_thenLeagueSeasonsHavePhaseTeamsWithNullGroup` | ✅ |
| 59-03 | 03 | DATA-01 (matchday shape) | Consolidated 2023 has 6 matchdays; split evenly 3/3 by group; sortIndex values [1,2,3,4,5,6] do not collide (W-1 fix) | Integration | [TestDataServiceIntegrationTest.java:153-195](../../src/test/java/org/ctc/admin/TestDataServiceIntegrationTest.java#L153-L195) — `givenDevSeed_whenStarted_thenConsolidated2023HasSixMatchdays`, `givenDevSeed_whenStarted_thenConsolidated2023MatchdaysSplitEvenlyByGroup`, `givenDevSeed_whenStarted_thenConsolidated2023MatchdaySortIndicesDoNotCollide` | `./mvnw test -Dtest=TestDataServiceIntegrationTest#givenDevSeed_whenStarted_thenConsolidated2023HasSixMatchdays+givenDevSeed_whenStarted_thenConsolidated2023MatchdaysSplitEvenlyByGroup+givenDevSeed_whenStarted_thenConsolidated2023MatchdaySortIndicesDoNotCollide` | ✅ |
| 59-03 | 03 | DATA-02 | `DevDataSeeder` (`@Profile("dev")`) calls `testDataService.seed()` which creates a GROUPS-layout season + PLAYOFF via `autoSeedBracket`; legacy `playoff.getSeasons().add(s1b)` M:N write is gone | Integration (transitively via DATA-01 tests) + grep evidence | [TestDataServiceIntegrationTest.java:263-274](../../src/test/java/org/ctc/admin/TestDataServiceIntegrationTest.java#L263-L274); [DevDataSeeder.java line 21](../../src/main/java/org/ctc/admin/DevDataSeeder.java#L21) — `testDataService.seed()` under `@Profile("dev")`; confirmed by `grep -c 'playoffSeedingService.autoSeedBracket' TestDataService.java` returns at least 1; `grep -c 'getSeasons().add(' TestDataService.java` returns 0 | `./mvnw test -Dtest=TestDataServiceIntegrationTest#givenDevSeed_whenStarted_thenConsolidated2023HasOneRegularGroupsPhase` (DATA-02 exercised transitively via dev seed startup) | ✅ |
| 59-04 | 04 | IMPORT-03 (IT execute path) | `execute()` writes SeasonDriver only; PhaseTeam count is invariant before/after execute regardless of whether team has a PhaseTeam (D-07 + D-16 invariant) | Integration | [DriverSheetImportServiceIT.java:270-348](../../src/test/java/org/ctc/dataimport/DriverSheetImportServiceIT.java#L270-L348) — `givenNewDriverRowOnConsolidated2023_whenExecute_thenOnlySeasonDriverIsWritten` + `givenTeamWithoutPhaseTeam_whenExecute_thenSeasonDriverWrittenAndPhaseTeamUnchanged` | `./mvnw test -Dtest=DriverSheetImportServiceIT#givenNewDriverRowOnConsolidated2023_whenExecute_thenOnlySeasonDriverIsWritten+givenTeamWithoutPhaseTeam_whenExecute_thenSeasonDriverWrittenAndPhaseTeamUnchanged` | ✅ |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

**Sampling continuity:** No 3 consecutive tasks lack automated verification. Every code-touching plan has unit and/or integration tests.

---

## Wave 0 Requirements

Phase 59 did not require new test infrastructure installation — JUnit 5 + Mockito + Spring Boot Test were already wired. The **net-new test infrastructure** introduced by Phase 59:

- [SeasonManagementServiceTest.java (6 new methods)](../../src/test/java/org/ctc/domain/service/SeasonManagementServiceTest.java) — unit coverage for the `findUnique` 0/1/many contract (IMPORT-02)
- [PhaseTeamRepositoryTest.java](../../src/test/java/org/ctc/domain/repository/PhaseTeamRepositoryTest.java) — `@SpringBootTest` repo test for `findByPhaseIdAndTeamId` (IMPORT-03)
- [DriverSheetImportServiceTest.java (8 new methods)](../../src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java) — unit tests for tab-pattern, group-resolution, warning emission (IMPORT-01/03/04)
- [DriverSheetImportServiceIT.java](../../src/test/java/org/ctc/dataimport/DriverSheetImportServiceIT.java) — `@SpringBootTest @ActiveProfiles("dev") @Transactional` integration test with `@MockitoBean GoogleSheetsService`; 8 `@Test` methods exercising preview→execute roundtrip against consolidated 2023 GROUPS season (IMPORT-01/03/04)
- [DriverSheetImportServiceTransactionIT.java](../../src/test/java/org/ctc/dataimport/DriverSheetImportServiceTransactionIT.java) — `@SpringBootTest @ActiveProfiles("dev")` **without** class-level `@Transactional`; reproduces and pins the `UnexpectedRollbackException` regression; WR-04 self-protection guard via `Class.isAnnotationPresent(Transactional.class)` assertion (IMPORT-02 gap closure)
- [TestDataServiceIntegrationTest.java (net +5 methods after deletions)](../../src/test/java/org/ctc/admin/TestDataServiceIntegrationTest.java) — consolidated 2023 layout assertions, sortIndex non-collision, LEAGUE PhaseTeam shape (DATA-01/DATA-02)

All Wave-0-equivalent assets are committed and green (with one BLOCKER — see below).

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| **Warning badge renders as visible amber alert in browser** | IMPORT-04 sub-aspect | The backend `TabWarning` data contract is fully covered by `DriverSheetImportServiceIT` (tests 6, 8). The template binding (`driver-import-preview.html` lines 32-39, `th:each="warning : ${tab.warnings()}"`) is statically verifiable by code inspection (verified in 59-VERIFICATION.md). The amber visual rendering requires a live browser with a real Playwright screenshot — the `.alert-warning` CSS palette at `admin.css:161-163` can only be confirmed visually against a running dev server. | 1) `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`. 2) Navigate to `/admin/drivers/import`. 3) Paste a Google Sheet URL containing a tab with a team that has no PhaseTeam in the REGULAR phase of the resolved season. 4) Confirm amber alert box with the team short name and message is visible in the preview. |
| **`DevDataSeeder` dev server boots cleanly with consolidated 2023 layout visible in admin UI** | DATA-02 visual sanity | `TestDataServiceIntegrationTest` verifies the data shape programmatically. The visual rendering of the GROUPS season UI (phase tabs, per-group matchday views, combined standings) requires a running dev server + Playwright or manual browser inspection per `feedback_playwright_cli`. | 1) `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`. 2) Navigate to `/admin/seasons`. 3) Open Season 2023. 4) Confirm REGULAR phase tab shows "Groups" layout with two group sub-tabs. 5) Confirm each group has 3 matchdays visible. |

---

## Validation Sign-Off

- [x] All tasks have automated verify or are documented as manual-only with rationale
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references — all net-new test infrastructure is inventoried above
- [x] No watch-mode flags (Surefire in CI mode)
- [x] `wave_0_complete: true` — all test infrastructure is created and committed
- [ ] `nyquist_compliant: true` — NOT set; one BLOCKER exists (see Escalated section and Validation Audit below)

**Approval note:** `nyquist_compliant: false` per D-05 — one requirement dimension (IMPORT-01 IT path) has a failing test at audit date 2026-05-07. The gap is due to post-Phase-59 seed data drift, not a missing test. See escalation.

---

## Escalated (BLOCKER)

### BLOCKER-59-01: IMPORT-01/IMPORT-02 IT — Legacy tab resolution fails due to seed data drift

**Requirement:** `DriverSheetImportServiceIT.givenLegacyYearTab_whenPreview_thenSeasonAutoResolvedToLeague2024` — Tab `"2024"` resolves to the single LEAGUE 2024 season (`suggestedSeasonId` non-null, `ambiguousReason` null).

**Observed behavior at audit 2026-05-07:** The test FAILS with:
```
expected: 64842346-755a-428f-93c6-e21ecdcc695d (season 2024 number=2)
 but was: null
```
`tab.suggestedSeasonId()` is `null` because `tab.ambiguousReason()` is non-null — `findUnique(2024)` throws `BusinessRuleException("Multiple seasons exist for year 2024 — consolidate them first...")`.

**Root cause:** `TestDataService` now seeds `(year=2024, number=3, name="Season 2024 — Empty Phase")` as a D-22 empty-state coverage season (line 326 of TestDataService.java). This season was added AFTER Phase 59 was completed. With two 2024 seasons in the dev seed, the legacy `"2024"` tab hits the multi-hit branch of `findUnique(2024)`, returning ambiguous — correctly according to the contract, but breaking the IT test assumption.

**Affected file:** `src/test/java/org/ctc/dataimport/DriverSheetImportServiceIT.java` line 129

**Implementation file:** `src/main/java/org/ctc/admin/TestDataService.java` lines 322-395 (the `Season 2024 — Empty Phase` seed block added post-Phase-59)

**Escalation rationale:** This is NOT a test logic error (the test correctly asserts IMPORT-01). It is a seed data contract violation — the IT test was authored against a seed with one 2024 season; the seed now has two. Fix requires either: (a) updating the IT to use the numbered tab pattern `"2024_S2"` for the single-season path (since `findUnique(2024, 2)` would still resolve unambiguously), or (b) removing/renaming the `Season 2024 — Empty Phase` season to a different year. Both require a decision about the intended seed contract.

**Debug iterations:** 1/3 — initial investigation identifies root cause; fix requires developer decision on seed contract.

---

## Validation Audit 2026-05-07

| Metric | Count |
|--------|-------|
| Requirements audited | 6 (IMPORT-01, IMPORT-02, IMPORT-03, IMPORT-04, DATA-01, DATA-02) |
| Plans audited | 5 (59-01, 59-02, 59-03, 59-04, 59-05) |
| REQ-IDs from PLAN frontmatters | 6 unique: IMPORT-01 (59-02), IMPORT-02 (59-01, 59-05), IMPORT-03 (59-02, 59-04), IMPORT-04 (59-02), DATA-01 (59-03), DATA-02 (59-03) |
| ROADMAP signal | 6 REQ-IDs signaled |
| Count match vs ROADMAP | MATCH — 6 enumerated = 6 signaled |
| Gaps found | 1 — IMPORT-01 IT path (seed data drift, post-phase-59 addition of a second 2024 season) |
| Resolved (already automated) | 5 REQ-IDs fully covered by passing tests |
| Escalated to BLOCKER | 1 — IMPORT-01/IMPORT-02 IT path (`givenLegacyYearTab_whenPreview_thenSeasonAutoResolvedToLeague2024` fails) |
| Escalated to manual-only | 2 (visual warning badge rendering, DevDataSeeder dev server boot) |
| Net-new test infrastructure | PhaseTeamRepositoryTest (3 tests), DriverSheetImportServiceTest (+8 tests), DriverSheetImportServiceIT (8 tests, NEW file), DriverSheetImportServiceTransactionIT (2 tests, NEW file), TestDataServiceIntegrationTest (+5 net tests), SeasonManagementServiceTest (+6 tests) |
| Auto-fill tests generated | 0 — all REQ-IDs had pre-existing test classes; gap is a failing existing test, not a missing test |

**Verdict (initial audit):** NOT NYQUIST-COMPLIANT — IMPORT-01 IT (`givenLegacyYearTab_whenPreview_thenSeasonAutoResolvedToLeague2024`) failed because post-Phase-59 seed drift added `(2024, 3) "Season 2024 — Empty Phase"` to `TestDataService`, making `findUnique(2024)` ambiguous.

**Resolution (Phase 64 expanded scope, 2026-05-07):** Test renamed and rewritten to `givenLegacyYearTab_whenPreview_thenSeasonAutoResolvedToUniqueYearSeason`. The new test inserts a fresh single-season year (`2027 / Phase59-IT-Legacy-2027`) inline via `seasonRepository.save()` so the precondition is no longer coupled to seed years that may legitimately gain additional seasons (e.g., D-22 empty-state coverage at `(2024, 3)`). The `@Transactional` rollback isolates the inline fixture from other tests. Test green post-fix.

**Final verdict:** **NYQUIST-COMPLIANT.** All 6 REQ-IDs (IMPORT-01..04, DATA-01, DATA-02) covered by green automated tests. `nyquist_compliant: true`, `wave_0_complete: true`.
