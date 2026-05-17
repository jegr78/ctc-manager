---
phase: 83
slug: quality-and-polish-sweep
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-17
---

# Phase 83 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter) + Mockito + AssertJ + Playwright 1.58.0 + Spring Boot 4 MockMvc |
| **Config file** | `pom.xml` (Surefire/Failsafe/JaCoCo/SpotBugs) |
| **Quick run command** | `./mvnw test -Dtest=<ClassName> -DfailIfNoTests=false` |
| **Full suite command** | `./mvnw verify -Pe2e` |
| **Estimated runtime** | ~670 seconds (v1.10 baseline 11m 11s) |

---

## Sampling Rate

- **After every task commit:** Run targeted `-Dtest=` or `-Dit.test=` per CONTEXT.md D-03 (no full `verify` between QUAL-IDs)
- **After every plan wave:** N/A — Phase 83 is 5 sequential one-commit-per-QUAL-ID, no wave-merge orchestration
- **Before `/gsd:verify-work`:** `./mvnw verify -Pe2e` must be green AND JaCoCo ≥ 87.30 % AND SpotBugs `BugInstance size 0`
- **Max feedback latency:** ≤30 s for unit tests, ≤120 s for ITs, ≤300 s for E2E targeted runs

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 83-01-01 | 01 | 1 | QUAL-01 | — | N/A | source | `Driver.java contains @OrderBy("season.year ASC, season.number ASC")` | ❌ W0 | ⬜ pending |
| 83-01-02 | 01 | 1 | QUAL-01 | — | N/A | integration | `./mvnw test -Dtest=DriverRepositoryOrderIT -DfailIfNoTests=false` | ❌ W0 | ⬜ pending |
| 83-01-03 | 01 | 1 | QUAL-01 | — | N/A | e2e | `./mvnw verify -Pe2e -Dit.test=DriverDetailSmokeE2E -DfailIfNoTests=false` | ❌ W0 | ⬜ pending |
| 83-02-01 | 02 | 1 | QUAL-02 | T-83-02 (profile leakage) | DevDataSeeder + TestDataService widened ONLY to `{dev,local}`, NOT to prod/docker | source | `DevDataSeeder.java contains @Profile({"dev", "local"})` AND `TestDataService.java contains @Profile({"dev", "local"})` | ❌ W0 | ⬜ pending |
| 83-02-02 | 02 | 1 | QUAL-02 | T-83-02 | Bean activates under `local`, idempotent re-run safe | manual | `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` → log shows `Seed data created` (first run) or `Seed data already present, skipping` (re-run) | ❌ W0 | ⬜ pending |
| 83-03-01 | 03 | 1 | QUAL-03 | T-83-03 (mass assignment) | `MatchdayGeneratorForm` receives `groupId` via DTO, NOT via entity binding | source | `MatchdayGeneratorForm.java contains private UUID groupId` AND `SeasonController.java line ~251 contains form.getGroupId()` | ❌ W0 | ⬜ pending |
| 83-03-02 | 03 | 1 | QUAL-03 | T-83-03 | Template renders group `<select>` only for GROUPS-layout, hidden for SINGLE | integration | `./mvnw test -Dtest=SeasonController*Test -DfailIfNoTests=false` (MockMvc HTML assertion `name="groupId"`) | ✅ existing class; assertion is W0 | ⬜ pending |
| 83-03-03 | 03 | 1 | QUAL-03 | — | N/A | e2e | `./mvnw verify -Pe2e -Dit.test=MatchdayGeneratorGroupsE2E -DfailIfNoTests=false` | ❌ W0 | ⬜ pending |
| 83-04-01 | 04 | 1 | QUAL-04 | T-83-04 (lazy info disclosure) | Controller no longer calls `phase.getGroups()` directly; `StandingsViewService` wraps in `@Transactional(readOnly=true)` | source | `StandingsController.java does NOT contain resolvedPhase.getGroups()` AND `StandingsViewService.java contains @Transactional(readOnly = true)` | ❌ W0 | ⬜ pending |
| 83-04-02 | 04 | 1 | QUAL-04 | — | N/A | unit | `./mvnw test -Dtest=StandingsViewServiceTest -DfailIfNoTests=false` (6-7 branch tests) | ❌ W0 | ⬜ pending |
| 83-04-03 | 04 | 1 | QUAL-04 | — | N/A | integration | `./mvnw test -Dtest=StandingsControllerTest -DfailIfNoTests=false` (existing assertions stay green under Option a) | ✅ existing | ⬜ pending |
| 83-05-01 | 05 | 1 | QUAL-05 | — | N/A | docs | `test -f docs/uat/UAT-02-legacy-season-smoke.md` exits 0 AND file contains `## Pass Criteria` | ❌ W0 | ⬜ pending |
| 83-05-02 | 05 | 1 | QUAL-05 | — | N/A | docs | Result-slot exists in `.planning/STATE.md` (or `.planning/milestone-audits/v1.11-UAT-02.md`) and contains the literal string `pending — to be executed after v1.11 production deploy` | ❌ W0 | ⬜ pending |
| 83-V-01 | verify | end | all | — | N/A | gate | `./mvnw verify -Pe2e` exit 0 + JaCoCo line coverage ≥ 87.30 % + SpotBugs `BugInstance size 0` | ✅ existing toolchain | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/org/ctc/domain/repository/DriverRepositoryOrderIT.java` — covers QUAL-01 (`@OrderBy` SQL-side correctness)
- [ ] `src/test/java/org/ctc/e2e/DriverDetailSmokeE2E.java` — covers QUAL-01 (visual chip-order on admin driver-detail page)
- [ ] `src/test/java/org/ctc/e2e/MatchdayGeneratorGroupsE2E.java` — covers QUAL-03 (form UI + Group A/B submit on Season 2098 Test-prefix fixture)
- [ ] `src/test/java/org/ctc/domain/service/StandingsViewServiceTest.java` — covers QUAL-04 (6-7 branch resolution tests, Given/When/Then)
- [ ] `src/main/java/org/ctc/admin/dto/StandingsView.java` — Java 25 record DTO (QUAL-04)
- [ ] `src/main/java/org/ctc/domain/service/StandingsViewService.java` — composition service (QUAL-04)
- [ ] `docs/uat/UAT-02-legacy-season-smoke.md` — UAT-02 post-deploy procedure (QUAL-05)
- [ ] Result-slot section in `STATE.md` or `.planning/milestone-audits/v1.11-UAT-02.md` (QUAL-05)
- ✅ Framework: JUnit 5 + Mockito + AssertJ + Playwright + MockMvc — all already wired in `pom.xml`, no install needed

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` starts the app + seeds via `TestDataService.seed()` | QUAL-02 | MariaDB-testcontainer cost is too high for routine CI; manual smoke is the planner's recommended path per D-15 discretion | (1) Drop local DB (`docker compose down -v`). (2) Start MariaDB (`docker compose up db -d`). (3) Run `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`. (4) Expect log line `Seed data created: 12 teams, 4 seasons, ...`. (5) Stop and re-run same command. (6) Expect log line `Seed data already present, skipping`. |
| UAT-02 legacy season visual smoke against pre-V4 production data | QUAL-05 | Requires live production deploy of v1.11 — cannot be automated until staging mirrors prod | Follow `docs/uat/UAT-02-legacy-season-smoke.md` procedure post-deploy. Record date, executor, pass/fail, screenshots-path, in result-slot. |
| Standings page visual parity post-QUAL-04 refactor | QUAL-04 | Refactor is supposed to be visually transparent; existing E2E catches HTML attribute regressions but operator visual confirmation is the final acceptance | After QUAL-04 commits land, run `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`, navigate to `/admin/standings`, `/admin/standings?seasonId=alltime`, `/admin/standings?seasonId=<2023-uuid>`, `/admin/standings?phase=<2023-regular-phase-uuid>&group=<group-a-uuid>`. Compare visually with pre-refactor screenshots. |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify (all QUAL-IDs except QUAL-05 docs are automated)
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags (`./mvnw verify` is one-shot)
- [ ] Feedback latency < 670s (full suite); <120s per targeted command
- [ ] `nyquist_compliant: true` set in frontmatter (after planner produces PLAN.md files that reference these tasks)

**Approval:** pending
