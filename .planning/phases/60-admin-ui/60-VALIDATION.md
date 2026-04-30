---
phase: 60
slug: admin-ui
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-30
---

# Phase 60 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + Spring-Test (MockMvc) + Playwright (E2E + visual) |
| **Config file** | `pom.xml` (Surefire + Failsafe + JaCoCo + `-Pe2e`), `application-dev.yml` (H2 in-mem) |
| **Quick run command** | `./mvnw test -Dtest={ClassName}` (single class, ~5–15 s) |
| **Full suite command** | `./mvnw verify` (Unit + IT + JaCoCo) — phase gate: `./mvnw verify -Pe2e` |
| **Estimated runtime** | Quick: ~5–15 s · Full `verify`: ~3–5 min · Full `verify -Pe2e`: ~6–10 min |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test -Dtest={ClassName}` for the test class touched by the commit.
- **After every plan wave:** Run `./mvnw verify` (Unit + IT + JaCoCo coverage).
- **Before `/gsd-verify-work`:** `./mvnw verify -Pe2e` must be green AND JaCoCo line coverage ≥ 82 % AND `playwright-cli` visual verification complete (Desktop + Mobile) for every modified UI page.
- **Max feedback latency:** ~15 s per task (single-class run).

> Per project memory `feedback_test_call_optimization`: avoid repeated full `verify` during development — run targeted `-Dtest=` / `-Dit.test=` until the wave gate, then ONE full `verify`.

---

## Per-Task Verification Map

| Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|------|------|-------------|-----------|-------------------|-------------|--------|
| 60-00 (Wave 0 RED) | 0 | UI-02 | IT | `./mvnw test -Dtest=SeasonPhaseControllerIT#givenGroupsLayoutPhase_whenGetSeasonDetail_thenGroupSubTabsRendered` | ❌ W0 | ⬜ pending |
| 60-00 | 0 | UI-02 | IT | `./mvnw test -Dtest=SeasonPhaseControllerIT#givenWrongSeasonId_whenGetPhase_thenReturns404` | ❌ W0 | ⬜ pending |
| 60-00 | 0 | UI-02 | IT | `./mvnw test -Dtest=SeasonPhaseControllerIT#givenSeasonWithoutRegularPhase_whenGetSeasonDetail_thenRendersEmptyStateCard` | ❌ W0 | ⬜ pending |
| 60-00 | 0 | UI-03 | IT | `./mvnw test -Dtest=SeasonPhaseControllerIT#givenRegularPhase_whenAddPhase_thenFormPrefilledWithRegularDefaults` | ❌ W0 | ⬜ pending |
| 60-00 | 0 | UI-03 | IT | `./mvnw test -Dtest=SeasonPhaseControllerIT#givenExistingRegular_whenCreateSecondRegular_thenFlashError` | ❌ W0 | ⬜ pending |
| 60-00 | 0 | UI-03 | unit | `./mvnw test -Dtest=SeasonPhaseServiceTest#givenPhaseWithMatchdays_whenChangeLayout_thenThrowsBusinessRule` | ❌ W0 | ⬜ pending |
| 60-00 | 0 | UI-03 | unit | `./mvnw test -Dtest=SeasonPhaseServiceTest#givenPhaseWithMatchdays_whenDelete_thenThrowsBusinessRule` | ❌ W0 | ⬜ pending |
| 60-00 | 0 | UI-04 | IT | `./mvnw test -Dtest=SeasonPhaseGroupControllerIT#givenGroupsPhase_whenSaveGroup_thenRedirectsToRosterStep` | ❌ W0 | ⬜ pending |
| 60-00 | 0 | UI-04 | IT | `./mvnw test -Dtest=SeasonPhaseGroupControllerIT#givenRosterDiff_whenSave_thenInsertsAndDeletesAndUpdates` | ❌ W0 | ⬜ pending |
| 60-00 | 0 | UI-04 | unit | `./mvnw test -Dtest=PhaseTeamFormTest#givenAutoPopulatingList_whenBindIndexedProperties_thenAssignmentsParsed` | ❌ W0 | ⬜ pending |
| 60-00 | 0 | UI-05 | IT | `./mvnw test -Dtest=StandingsControllerTest#givenGroupsPhase_whenGetStandingsWithoutGroup_thenCombinedViewWithGroupColumn` | ❌ W0 | ⬜ pending |
| 60-00 | 0 | UI-05 | IT | `./mvnw test -Dtest=StandingsControllerTest#givenSwissPerGroup_whenGetStandings_thenShowBuchholzTrue` | ❌ W0 | ⬜ pending |
| 60-00 | 0 | UI-06 | IT | `./mvnw test -Dtest=DriverSheetImportControllerTest#givenGroupsLayoutTarget_whenPreview_thenGroupColumnRendered` | ❌ W0 | ⬜ pending |
| 60-00 | 0 | UI-06 | IT | `./mvnw test -Dtest=DriverSheetImportControllerTest#givenDriverWithNullResolvedGroup_whenPreview_thenInlineBadgeRendered` | ❌ W0 | ⬜ pending |
| 60-00 | 0 | UI-07 | IT | `./mvnw test -Dtest=PlayoffControllerTest#givenPlayoff_whenGetBracket_thenAddSeasonButtonNotPresent` | ❌ W0 | ⬜ pending |
| 60-00 | 0 | UI-07 | IT | `./mvnw test -Dtest=SeasonPhaseControllerIT#givenSeasonWithoutPlayoff_whenAddPhasePLAYOFF_thenPlayoffServiceAutoCreatesPlayoff` | ❌ W0 | ⬜ pending |
| 60-01 (Backend CRUD) | 1 | UI-01 | unit | `./mvnw test -Dtest=SeasonManagementServiceTest#givenSlimForm_whenSave_thenSeasonPersisted` | ❌ W0 | ⬜ pending |
| 60-01 | 1 | UI-01 | unit | `./mvnw test -Dtest=SeasonManagementServiceTest#givenExistingPhaseWithFormat_whenSeasonSaved_thenPhaseFormatUntouched` | ❌ W0 | ⬜ pending |
| 60-01 | 1 | UI-04 | unit | `./mvnw test -Dtest=SeasonManagementServiceTest#givenPhaseTeamRefs_whenRemoveSeasonTeam_thenThrowsBusinessRule` | ❌ W0 | ⬜ pending |
| 60-01 | 1 | UI-04 | IT | `./mvnw test -Dtest=SeasonManagementServiceIT#givenAddTeamToSeason_thenPhaseTeamCreatedInRegular` | ❌ W0 | ⬜ pending |
| 60-01 | 1 | UI-01..04 | unit | `./mvnw test -Dtest=SeasonPhaseControllerTest` (full class) | ❌ W0 | ⬜ pending |
| 60-01 | 1 | UI-04 | unit | `./mvnw test -Dtest=SeasonPhaseGroupControllerTest` (full class) | ❌ W0 | ⬜ pending |
| 60-02 (Saison-Detail tpl) | 2 | UI-01 | IT | `./mvnw test -Dtest=SeasonControllerTest#whenGetNewSeasonForm_thenReturnsSeasonForm` (extend) | ✅ extend | ⬜ pending |
| 60-02 | 2 | UI-02 | manual | `playwright-cli open http://localhost:9090/admin/seasons/{id}` Desktop + Mobile | manual | ⬜ pending |
| 60-02 | 2 | UI-03 | manual | `playwright-cli open http://localhost:9090/admin/seasons/{id}/phases/new` Desktop + Mobile | manual | ⬜ pending |
| 60-02 | 2 | UI-04 | manual | `playwright-cli open http://localhost:9090/admin/seasons/{id}/phases/{pid}/groups/new` Desktop + Mobile | manual | ⬜ pending |
| 60-03 (Standings) | 3 | UI-05 | IT | `./mvnw test -Dtest=StandingsControllerTest#givenLegacySeasonParam_whenGetStandings_thenResolvesToRegularPhase` (extend) | ✅ extend | ⬜ pending |
| 60-03 | 3 | UI-05 | IT | `./mvnw test -Dtest=StandingsControllerTest#givenGroupsPhase_whenGetStandings_thenSubTabsRendered` (extend) | ✅ extend | ⬜ pending |
| 60-03 | 3 | UI-05 | manual | `playwright-cli open http://localhost:9090/admin/standings?phase={pid}&group={gid}` Desktop + Mobile | manual | ⬜ pending |
| 60-04 (Importer) | 3 | UI-06 | IT | `./mvnw test -Dtest=DriverSheetImportControllerTest#given2025_S2Tab_whenPreview_thenH2ShowsRawName` (extend) | ✅ extend | ⬜ pending |
| 60-04 | 3 | UI-06 | IT | `./mvnw test -Dtest=DriverSheetImportControllerTest#givenTabWithWarning_whenPreview_thenBannerVisible` (existing) | ✅ exists | ⬜ pending |
| 60-04 | 3 | UI-06 | manual | `playwright-cli open http://localhost:9090/admin/drivers/import` Desktop + Mobile | manual | ⬜ pending |
| 60-05 (Playoff cutover) | 3 | UI-07 | IT | `./mvnw test -Dtest=PlayoffControllerTest#givenPlayoff_whenGetPlayoffsForSeason_thenReturnsBracket` (existing) | ✅ exists | ⬜ pending |
| 60-05 | 3 | UI-07 | IT | `./mvnw test -Dtest=PlayoffControllerTest#givenPlayoffAndOtherSeason_whenAddAndRemoveSeasonFromPlayoff_thenBothSucceed` (existing) | ✅ exists | ⬜ pending |
| 60-05 | 3 | UI-07 | manual | `playwright-cli open http://localhost:9090/admin/playoffs/{id}` Desktop + Mobile | manual | ⬜ pending |
| 60-06 (D-44 cleanup) | 4 | UI-01..07 | full | `./mvnw verify` (Unit + IT + JaCoCo ≥ 82 %) | n/a | ⬜ pending |
| 60-06 | 4 | UI-01..07 | full | `./mvnw verify -Pe2e` (final phase gate) | n/a | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/org/ctc/admin/controller/SeasonPhaseControllerTest.java` — UI-02/UI-03 unit tests
- [ ] `src/test/java/org/ctc/admin/controller/SeasonPhaseGroupControllerTest.java` — UI-04 unit tests
- [ ] `src/test/java/org/ctc/admin/controller/integration/SeasonPhaseControllerIT.java` — UI-02/UI-03 IT
- [ ] `src/test/java/org/ctc/admin/controller/integration/SeasonPhaseGroupControllerIT.java` — UI-04 IT
- [ ] `src/test/java/org/ctc/admin/dto/SeasonPhaseFormTest.java` — Bean-Validation
- [ ] `src/test/java/org/ctc/admin/dto/PhaseTeamFormTest.java` — AutoPopulatingList behavior
- [ ] Extend `SeasonControllerTest.java` — Slim-Form-Save tests (UI-01)
- [ ] Extend `SeasonManagementServiceTest.java` — D-25/D-26 + Auto-Sync removal
- [ ] Extend `StandingsControllerTest.java` — Combined-View, Buchholz-conditional, legacy bridge
- [ ] Extend `DriverSheetImportControllerTest.java` — tab-name display, inline badge, group column
- [ ] Extend `PlayoffControllerTest.java` — Add-Season UI hidden
- [ ] Extend `SeasonPhaseServiceTest.java` — `update`, `delete`, `updateGroup`, `deleteGroup`, `assignTeamsToPhase`

> Framework install: not needed — JUnit 5, Mockito, Spring-Test, Playwright already in `pom.xml`.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Two-row phase tabs + group sub-tabs render correctly with active state on Desktop AND Mobile (`overflow-x: auto`) | UI-02 | Visual regression — server rendering verified by IT, but tab styling, mobile horizontal scroll, and active-state CSS need eyeballs | Start `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo`, then `playwright-cli open http://localhost:9090/admin/seasons/{seed-season-id}` Desktop + Mobile (414×896). Click each phase tab, verify URL updates, verify active state highlights, scroll horizontally on mobile when many phases exist. |
| Phase form layout + validation errors display | UI-03 | Form rendering, field grouping, `th:errors` placement | `playwright-cli open http://localhost:9090/admin/seasons/{id}/phases/new` Desktop + Mobile. Submit empty form → verify field-level errors render. Submit duplicate phaseType → verify flash-error banner top of page. |
| Group form Step 1 → Phase-Detail roster section | UI-04 | Two-step navigation flow + roster multi-select with per-team group dropdown | `playwright-cli open http://localhost:9090/admin/seasons/{id}/phases/{groups-pid}/groups/new` Desktop. Save group → land on Phase-Detail tab with roster section visible. Toggle checkboxes, change group dropdown values, save. |
| Standings two-row tabs + Combined view + Group view + Buchholz column conditional | UI-05 | Visual layout — server flags verified by IT, but `combinedView`/`showBuchholz`/`showGroupColumn` rendering needs visual confirmation | `playwright-cli open http://localhost:9090/admin/standings?phase={groups-phase-id}` Desktop + Mobile. Switch between Combined and Group A/B/C tabs. Verify Combined shows Group column, per-Group hides it; Buchholz shows in Per-Group only when format=SWISS. |
| Driver-Import-Preview Group column + inline `⚠ No group` badge + TabWarning banner | UI-06 | Conditional rendering depends on target phase layout + per-row `resolvedGroupName` | Mock a driver-sheet import with at least one team without group assignment in a GROUPS-layout target season. `playwright-cli open http://localhost:9090/admin/drivers/import` Desktop + Mobile. Verify warning banner, inline badges in rows where `resolvedGroupName` is null. |
| Playoff bracket renders WITHOUT Add-Season UI | UI-07 | D-43: backend remains, UI hides — visual confirmation that buttons are gone | `playwright-cli open http://localhost:9090/admin/playoffs/{id}` Desktop + Mobile. Verify bracket displays. Verify NO "Add Season" / "Remove Season" buttons visible. Existing seeding interaction still works. |

---

## Validation Sign-Off

- [ ] All tasks have automated `<verify>` commands or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING test classes from Per-Task Verification Map
- [ ] No watch-mode flags
- [ ] Feedback latency < 15 s per task (single-class run)
- [ ] `nyquist_compliant: true` set in frontmatter once Wave 0 RED tests are committed

**Approval:** pending
