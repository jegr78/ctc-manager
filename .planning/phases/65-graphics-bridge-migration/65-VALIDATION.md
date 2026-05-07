---
phase: 65
slug: graphics-bridge-migration
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-07
---

# Phase 65 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution. Phase 65 is a pure refactoring of an internal service API — no schema, no public HTTP surface, no UI changes. Validation focuses on behavioral preservation (LEAGUE), correct group-scoping (GROUPS), and the SC1 grep gate.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito 5 (via Spring Boot 4.x BOM) |
| **Config file** | `pom.xml` — Surefire (unit + IT, excludes `**/e2e/**`), Failsafe + `-Pe2e` (E2E only), JaCoCo `<minimum>0.82</minimum>` line gate |
| **Quick run command** | `./mvnw test -Dtest='AbstractMatchdayGraphicServiceTest,TeamCardServiceTest,OverlayGraphicServiceTest,SettingsGraphicServiceTest,LineupGraphicServiceTest,StandingsServiceTest,SwissPairingServiceTest,SiteGeneratorServiceIT'` |
| **Full suite command** | `./mvnw verify` |
| **E2E command** | `./mvnw verify -Pe2e` |
| **Estimated runtime** | Quick: ~45 s · Full: ~3 min · E2E: ~5 min |

---

## Sampling Rate

- **Per task commit (within a plan wave):** Run targeted Surefire `./mvnw test -Dtest='<affected-test-classes>'` — feedback < 30 s.
- **Per plan completion (end of P1, P2, P3):** Run `./mvnw verify` — full Surefire + JaCoCo gate, feedback < 90 s.
- **Phase gate (before `/gsd-verify-work`):** Run `./mvnw verify -Pe2e` — full E2E suite must be green.
- **Plan 65-03 SC1 gate:** `grep -nR "calculateStandings(seasonId" src/main/java | wc -l` MUST equal `0`. Plan acceptance blocks on non-zero.

---

## Per-Task Verification Map

> Each row maps a locked CONTEXT.md decision to its automated verification. Filled in by gsd-planner during plan generation; below is the skeleton derived from RESEARCH.md.

| Task ID | Plan | Wave | Requirement | Threat Ref | Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|----------|-----------|-------------------|-------------|--------|
| 65-01-T? | 01 | 1 | D-03a | — | AbstractMatchdayGraphicService uses `matchday.getPhase().getId()` + `matchday.getGroup()` | unit | `./mvnw test -Dtest=AbstractMatchdayGraphicServiceTest` | ✅ | ⬜ pending |
| 65-01-T? | 01 | 1 | D-03b | — | TeamCardService uses `findRegularPhase(seasonId).getId()` + `null` | unit | `./mvnw test -Dtest=TeamCardServiceTest` | ✅ (constructor change required) | ⬜ pending |
| 65-01-T? | 01 | 1 | D-03c | — | OverlayGraphicService uses `race.getMatchday().getPhase().getId()` + `race.getMatchday().getGroup()` | unit | `./mvnw test -Dtest=OverlayGraphicServiceTest` | ✅ | ⬜ pending |
| 65-01-T? | 01 | 1 | D-03d | — | SettingsGraphicService uses `race.getMatchday().getPhase()` + group (non-playoff branch only) | unit | `./mvnw test -Dtest=SettingsGraphicServiceTest` | ❌ W0 — new test class needed | ⬜ pending |
| 65-01-T? | 01 | 1 | D-03e | — | LineupGraphicService uses `race.getMatchday().getPhase()` + group (non-playoff branch only) | unit | `./mvnw test -Dtest=LineupGraphicServiceTest` | ✅ | ⬜ pending |
| 65-01-T? | 01 | 1 | D-11 | — | LEAGUE-regression — each caller invokes `calculateStandings(eq(phaseId), isNull())` for LEAGUE-layout matchdays | unit (Mockito.verify) | same as D-03a..e | ❌ W0 — 5 new test methods | ⬜ pending |
| 65-01-T? | 01 | 1 | D-12 | — | GROUPS-scope — Abstract, Overlay, Settings invoke `calculateStandings(eq(phaseId), eq(groupId))` for GROUPS-layout matchdays | unit (Mockito.verify) | `./mvnw test -Dtest='AbstractMatchdayGraphicServiceTest,OverlayGraphicServiceTest,SettingsGraphicServiceTest'` | ❌ W0 — 3 new test methods | ⬜ pending |
| 65-02-T? | 02 | 2 | D-04a | — | SwissPairingService.calculateBuchholz deleted (compile-time gate) + corresponding test deleted | compilation | `./mvnw test -Dtest=SwissPairingServiceTest` | ✅ (1 test deleted alongside method) | ⬜ pending |
| 65-02-T? | 02 | 2 | D-04b | — | calculateBuchholzScores private helper inlined into calculateBuchholzScoresForPhase | unit | `./mvnw test -Dtest=StandingsServiceTest` (existing Buchholz tests stay green) | ✅ | ⬜ pending |
| 65-03-T? | 03 | 3 | D-01 / SC1 | — | `StandingsService.calculateStandings(UUID seasonId)` removed | grep gate | `grep -nR "calculateStandings(seasonId" src/main/java \| wc -l` MUST equal 0 | n/a (grep gate) | ⬜ pending |
| 65-03-T? | 03 | 3 | D-09 | — | StandingsServiceTest bridge-tests triaged (3 deleted, 10 rewritten on canonical API) | unit | `./mvnw test -Dtest=StandingsServiceTest` (all green post-triage) | ✅ | ⬜ pending |
| 65-03-T? | 03 | 3 | — | — | SiteGeneratorServiceIT line 154 negative-verify removed | unit | `./mvnw test -Dtest=SiteGeneratorServiceIT` | ✅ | ⬜ pending |
| 65-03-T? | 03 | 3 | QUAL-01 / SC3 | — | JaCoCo line coverage stays ≥ 82% | gate | `./mvnw verify` | ✅ (automated gate) | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Wave 0 work is folded into Plan 65-01 (new test methods are added alongside production changes per CLAUDE.md TDD pattern; not a separate dependency wave).

- [ ] New test method `givenLeagueLayoutMatchday_whenPrepareBaseContext_thenStandingsCalledWithPhaseIdAndNullGroup()` in `src/test/java/org/ctc/admin/service/AbstractMatchdayGraphicServiceTest.java` (D-11)
- [ ] New test method `givenGroupsLayoutMatchday_whenPrepareBaseContext_thenStandingsCalledWithPhaseAndMatchdayGroup()` in `src/test/java/org/ctc/admin/service/AbstractMatchdayGraphicServiceTest.java` (D-12)
- [ ] **NEW FILE** `src/test/java/org/ctc/admin/service/SettingsGraphicServiceTest.java` — covers D-11 LEAGUE-regression AND D-12 GROUPS-test for the Settings caller (non-playoff branch only). Confirmed missing via `find src/test -name 'SettingsGraphic*'` returning empty.
- [ ] New test method in `TeamCardServiceTest` for D-11 LEAGUE-regression — requires injecting `SeasonPhaseService` mock since constructor changes to add the new dependency
- [ ] New test method in `OverlayGraphicServiceTest` for D-11 LEAGUE-regression
- [ ] New test method `givenGroupsLayoutRace_whenGenerateOverlay_thenStandingsCalledWithPhaseAndMatchdayGroup()` in `OverlayGraphicServiceTest` for D-12
- [ ] New test method in `LineupGraphicServiceTest` for D-11 LEAGUE-regression

*No new test infrastructure needed — JUnit 5, Mockito 5, and `PhaseTestFixtures` are already wired.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Graphics generation pixel-identical for LEAGUE seasons after migration | SC2 (behavior preservation) | Playwright graphics generation is compile-scope only; no automated screenshot comparison in suite | Boot dev server with `dev,demo` profile, generate a team card and matchday schedule graphic before and after migration; confirm visual output is identical via `playwright-cli` per `feedback_playwright_cli`. Capture screenshots in `.screenshots/` |
| GROUPS-layout graphic shows that group's standings (smoke) | D-12 | Test mocks verify the API call; visual confirmation that the resulting standings match the group is human-judgement | Boot with GROUPS-season seed, generate matchday graphic for a Group A match, confirm the displayed seed/record matches Group A standings (not combined) |

---

## Validation Sign-Off

- [ ] All locked decisions D-01 through D-15 mapped to a test or grep gate row in the Per-Task Verification Map
- [ ] Sampling continuity: no plan wave produces a commit without an automated verify command
- [ ] Wave 0 covers all currently MISSING test files (`SettingsGraphicServiceTest.java`) and 8 new test methods
- [ ] No watch-mode flags (Surefire / Failsafe in CI mode)
- [ ] Feedback latency < 30 s (quick filter) / 180 s (full verify)
- [ ] `nyquist_compliant: true` set in frontmatter (after planner verifies all rows are covered)
- [ ] JaCoCo line coverage ≥ 82% on `./mvnw verify` (project-wide gate from `pom.xml`) — verified post-execution
- [ ] SC1 grep gate verified by Plan 65-03 acceptance: `grep -nR "calculateStandings(seasonId" src/main/java | wc -l` returns 0

**Approval:** pending — to be flipped to `approved` after gsd-plan-checker confirms the per-task verification map covers all D-* decisions and Wave 0 gaps are listed in their owning plans.
