---
phase: 58
slug: service-layer
status: draft
nyquist_compliant: true
wave_0_complete: false
created: 2026-04-27
---

# Phase 58 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Spring Boot 4 test starter) + Mockito |
| **Config file** | `pom.xml` — surefire (unit + IT, excludes `**/e2e/**`), failsafe + `-Pe2e` (E2E only), JaCoCo 82% line gate |
| **Quick run command** | `./mvnw -pl . -am test -Dtest=*PhaseTest,*PhaseIT,SeasonPhaseService*Test,*RepositoryIT -DfailIfNoTests=false` (filtered to phase-58 surface) |
| **Full suite command** | `./mvnw verify` (unit + integration + JaCoCo) |
| **Estimated runtime** | Quick: ~30 s · Full: ~3 min (1064+ tests + JaCoCo enforce) |

---

## Sampling Rate

- **After every task commit:** Run quick filter via `./mvnw test -Dtest=<changed>Test,<changed>IT` (the executor agents run this per task per CLAUDE.md TDD pattern).
- **After every plan wave:** Run `./mvnw verify` (full suite + JaCoCo). Fail-fast on coverage drop below 82%.
- **Before `/gsd-verify-work`:** `./mvnw verify` must be green.
- **Max feedback latency:** 30 seconds for the quick filter; 180 seconds for the full suite.

---

## Per-Task Verification Map

> Filled in by gsd-planner from each PLAN's tasks. The map below is a skeleton showing the per-plan validation footprint expected from the planner. Actual rows are written into the plan tasks.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 58-01-01 | 01 | 1 | SVC-01 | — | findRegularPhase fail-loud on missing REGULAR phase | unit | `./mvnw test -Dtest=SeasonPhaseServiceTest#givenMissingRegularPhase_whenFindRegularPhase_thenThrowsEntityNotFound` | ❌ W0 | ⬜ pending |
| 58-01-02 | 01 | 1 | SVC-01 | — | createPhase rejects duplicate REGULAR via service guard | unit | `./mvnw test -Dtest=SeasonPhaseServiceTest#givenExistingRegularPhase_whenCreateRegular_thenThrowsBusinessRuleException` | ❌ W0 | ⬜ pending |
| 58-01-03 | 01 | 1 | SVC-01 | — | findBySeasonIdAndPhaseType returns correct row (DB-truth) | IT | `./mvnw test -Dtest=SeasonPhaseRepositoryIT#givenFixture_whenFindBySeasonIdAndPhaseType_thenReturnsExpected` | ❌ W0 | ⬜ pending |
| 58-02-01 | 02 | 2a | SVC-02 | — | calculateStandings(phaseId, null) on GROUPS-layout returns flat list with group field set | unit | `./mvnw test -Dtest=StandingsServiceTest#givenGroupsLayout_whenCalculateStandingsWithoutGroupId_thenFlatListWithGroupBadge` | ❌ W0 | ⬜ pending |
| 58-02-02 | 02 | 2a | SVC-02 | — | seasonId-overload delegates to REGULAR phase | unit | `./mvnw test -Dtest=StandingsServiceTest#givenSeasonId_whenCalculateStandings_thenDelegatesToRegularPhase` | ❌ W0 | ⬜ pending |
| 58-02-03 | 02 | 2a | SVC-02 | — | Buchholz tiebreaker ignored in combined-view (groupId=null) | unit | `./mvnw test -Dtest=StandingsServiceTest#givenSwissGroups_whenCalculateStandingsCombined_thenBuchholzNotUsedAsTiebreaker` | ❌ W0 | ⬜ pending |
| 58-03-01 | 03 | 2a | SVC-05 | — | calculateRankingForPhase aggregates RaceResult by phase (REGULAR finder) | unit | `./mvnw test -Dtest=DriverRankingServiceTest#givenRegularPhase_whenCalculateRankingForPhase_thenAggregatesViaMatchdayPhaseId` | ❌ W0 | ⬜ pending |
| 58-03-02 | 03 | 2a | SVC-05 | — | calculateRankingForPhase aggregates RaceResult by PLAYOFF (matchup chain) | unit | `./mvnw test -Dtest=DriverRankingServiceTest#givenPlayoffPhase_whenCalculateRankingForPhase_thenAggregatesViaPlayoffMatchupChain` | ❌ W0 | ⬜ pending |
| 58-03-03 | 03 | 2a | SVC-05 | — | aggregateAcrossPhases sums per-phase rankings with REGULAR-team attribution | unit | `./mvnw test -Dtest=DriverRankingServiceTest#givenMultiPhaseSeason_whenAggregateAcrossPhases_thenRegularTeamGuardsAttribution` | ❌ W0 | ⬜ pending |
| 58-03-04 | 03 | 2a | SVC-05 | — | RaceLineup-fallback for stand-in without REGULAR PhaseTeam | unit | `./mvnw test -Dtest=DriverRankingServiceTest#givenStandInWithoutRegularPhaseTeam_whenAggregateAcrossPhases_thenRaceLineupFallback` | ❌ W0 | ⬜ pending |
| 58-04-01 | 04 | 2b | SVC-04 | — | MatchdayGenerator.generate(phaseId, groupId) requires groupId for GROUPS layout | unit | `./mvnw test -Dtest=MatchdayGeneratorServiceTest#givenGroupsLayoutAndNullGroupId_whenGenerate_thenThrowsIllegalArgument` | ❌ W0 | ⬜ pending |
| 58-04-02 | 04 | 2b | SVC-04 | — | SwissPairing all 4 methods isolate per-group | unit | `./mvnw test -Dtest=SwissPairingServiceTest#givenSwissGroupsPhase_whenGenerateNextRoundForGroupA_thenOnlyGroupAAdvances` | ❌ W0 | ⬜ pending |
| 58-05-01 | 05 | 3 | SVC-03 | — | playoffService.createPlayoff auto-creates PLAYOFF phase | unit | `./mvnw test -Dtest=PlayoffServiceTest#givenSeasonWithoutPlayoffPhase_whenCreatePlayoff_thenAutoCreatesPlayoffPhase` | ❌ W0 | ⬜ pending |
| 58-05-02 | 05 | 3 | SVC-03 | — | playoffService.createPlayoff rejects duplicate PLAYOFF phase | unit | `./mvnw test -Dtest=PlayoffServiceTest#givenSeasonWithExistingPlayoffPhaseAndPlayoff_whenCreatePlayoff_thenThrowsBusinessRuleException` | ❌ W0 | ⬜ pending |
| 58-05-03 | 05 | 3 | SVC-03 | — | playoffSeedingService.autoSeedBracket draws Top-N from REGULAR phase standings | unit | `./mvnw test -Dtest=PlayoffSeedingServiceTest#givenRegularStandings_whenAutoSeedBracket_thenSeedsTopNFromCombinedView` | ❌ W0 | ⬜ pending |
| 58-05-04 | 05 | 3 | SVC-03 | — | playoffService.addRaceToMatchup links new matchday to PLAYOFF phase (Pitfall 4) | unit | `./mvnw test -Dtest=PlayoffServiceTest#givenPlayoffMatchup_whenAddRaceToMatchup_thenNewMatchdayLinkedToPlayoffPhase` | ❌ W0 | ⬜ pending |
| 58-06-01 | 06 | 4 | SVC-01 | — | seasonManagementService.delete refuses to delete season with active phase content | unit | `./mvnw test -Dtest=SeasonManagementServiceTest#givenSeasonWithActiveMatchdays_whenDelete_thenThrowsBusinessRuleException` | ❌ W0 | ⬜ pending |
| 58-06-02 | 06 | 4 | SVC-01 | — | seasonManagementService.save auto-syncs format/scoring/dates onto REGULAR phase | unit | `./mvnw test -Dtest=SeasonManagementServiceTest#givenSeasonSave_whenServiceSavesForm_thenRegularPhaseFieldsSynchronized` | ❌ W0 | ⬜ pending |
| 58-06-03 | 06 | 4 | SVC-04 | — | matchdayService.findByPhaseId works on REGULAR + PLAYOFF | unit | `./mvnw test -Dtest=MatchdayServiceTest#givenRegularAndPlayoffMatchdays_whenFindByPhaseId_thenSegmentedCorrectly` | ❌ W0 | ⬜ pending |
| 58-06-04 | 06 | 4 | SVC-01 | — | siteGeneratorService renders standings/rankings via phase-aware service calls | IT | `./mvnw test -Dtest=SiteGeneratorServiceIT#givenSeasonWithRegularPhase_whenGenerateStandings_thenUsesPhaseAwareApi` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/org/ctc/domain/service/PhaseTestFixtures.java` — pure-Java entity builders (CONTEXT.md D-11) — used by both Mockito service tests and `@SpringBootTest @ActiveProfiles("dev") @Transactional` repository IT-tests (matches codebase precedent — D-13 revised 2026-04-27).
- [ ] `src/test/java/org/ctc/domain/repository/SeasonPhaseRepositoryIT.java` — `@SpringBootTest @ActiveProfiles("dev") @Transactional` IT-test scaffold for SeasonPhaseRepository (matches codebase precedent — D-13 revised 2026-04-27).
- [ ] `src/test/java/org/ctc/domain/repository/SeasonPhaseGroupRepositoryIT.java` — `@SpringBootTest @ActiveProfiles("dev") @Transactional` IT-test scaffold for SeasonPhaseGroupRepository (matches codebase precedent — D-13 revised 2026-04-27).
- [ ] `src/test/java/org/ctc/domain/repository/PhaseTeamRepositoryIT.java` — `@SpringBootTest @ActiveProfiles("dev") @Transactional` IT-test scaffold for PhaseTeamRepository (matches codebase precedent — D-13 revised 2026-04-27).
- [ ] No new test framework needed — JUnit 5 + Mockito + Spring Boot test starter all present in `pom.xml`.
- [ ] No new application config needed — H2 dev profile already active for `@SpringBootTest @ActiveProfiles("dev")`.

*Wave 0 must complete before Wave 1 service tests can be written, since `PhaseTestFixtures` is the shared dependency.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Visual confirmation that admin UI still works after service refactor (no UI changes in Phase 58, but seasonId-overloads must keep flow working end-to-end) | SVC-01..SVC-05 | UI-level smoke test — Phase 58 promises no UI break, only service-internal change | Start dev server (`./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo`), use `playwright-cli` per CLAUDE.md to visit `/admin/seasons`, `/admin/standings?seasonId=...`, `/admin/playoffs?seasonId=...`, `/admin/matchdays?seasonId=...` — confirm each loads without 500. Capture screenshots in `.screenshots/` per project convention. |
| MariaDB compatibility of Spring Data magic-naming for new finders (Pitfall 1) | SVC-01..05 | H2 in-memory tests pass differently from MariaDB at runtime for deep `findBy...` chains | Run with `local` profile against MariaDB (`./mvnw spring-boot:run -Dspring-boot.run.profiles=local`), trigger an endpoint that calls each new finder, observe logs for `BadJpqlGrammarException` or similar. |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies (planner fills as it produces PLANs)
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (`PhaseTestFixtures` + 3 repo IT scaffolds) — pending Wave 0 execution
- [x] No watch-mode flags (Maven runs are one-shot)
- [x] Feedback latency < 30s (quick filter) / 180s (full suite)
- [x] `nyquist_compliant: true` set in frontmatter (after planner completes plan generation)
- [ ] JaCoCo line coverage ≥ 82% on `./mvnw verify` (project-wide gate from `pom.xml`) — verified post-execution

**Approval:** approved 2026-04-27 (planning-time sign-off; runtime gates marked above remain to be confirmed during execution)
