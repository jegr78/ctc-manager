---
phase: 102
slug: code-review-fixes
status: reconstructed
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-28
---

# Phase 102 — Validation Strategy (reconstructed)

> Reconstructed retroactively from the 4 plan SUMMARY.md files and the close-loop `102-REVIEW.md`. Phase 102 closed on 2026-05-28 with `./mvnw clean verify -Pe2e` green, JaCoCo 89.43 %, SpotBugs 0, and `gsd-code-reviewer` CLEAN on the second cumulative-diff pass.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot 4 Test + Mockito + WireMock (standalone) + Playwright (E2E) |
| **Config file** | `pom.xml` (Surefire + Failsafe profiles + JaCoCo + SpotBugs + find-sec-bugs) |
| **Quick run command** | `./mvnw test -Dtest=<TestClass> -DfailIfNoTests=true` (Surefire targeted) or `./mvnw verify -Dit.test=<ITClass> -DfailIfNoTests=true` (Failsafe targeted) |
| **Full suite command** | `./mvnw clean verify -Pe2e` (Surefire + Failsafe + Playwright E2E + JaCoCo + SpotBugs gates) |
| **Estimated runtime** | Targeted ≤ 30 s · Full suite 9:51 min (Pass 2 actual) |

Test categorisation per `.planning/codebase/TESTING.md`: unit tests `*Test.java` (Surefire, untagged); integration tests `*IT.java` carry class-level `@Tag("integration")` (Failsafe default); Playwright E2E classes under `org.ctc.e2e.*` carry `@Tag("e2e")` and only run under `-Pe2e`.

---

## Sampling Rate (as executed)

- **After every task commit:** targeted `./mvnw test` / `./mvnw verify -Dit.test=...` per task `<verify>` block (Plans 102-01..04 enforced this via TDD RED→GREEN under `-DfailIfNoTests=true`).
- **After every plan:** wide Surefire + Failsafe pass over the touched classes (Plan 102-02 Task 11 ran the cumulative gate).
- **End of phase:** ONE `./mvnw clean verify -Pe2e` per CONTEXT D-11 (executed at Plan 102-04 Task 1; second run after remediation).
- **Reviewer gate:** `gsd-code-reviewer` invoked over the full Phase-102 cumulative diff (`d6b5ab01..HEAD`) per CONTEXT D-04 (executed at Plan 102-04 Task 2; re-dispatched as Task 2-R6 after remediation).

---

## Per-Task Verification Map

### Plan 102-01 — Critical / Blocker (REVIEW-FIX-01)

| Task | Finding | Requirement | Threat Ref | Secure Behavior | Test Class | Type | Status |
|------|---------|-------------|------------|-----------------|------------|------|--------|
| 102-01-01 | 92 CR-01 | REVIEW-FIX-01 | T-102-01-IL | Only whitelisted user-message reaches flash on Google-reachable arms; raw upstream `e.getMessage()` suppressed | `CsvImportControllerExceptionTest` | unit (Surefire) | ✅ green |
| 102-01-02 | 94 CR-01 + 95 CR-01 | REVIEW-FIX-01 | T-102-01-NPE | `matchLabel` renders bye matches as `"<home> vs Bye"` without NPE; `GET /admin/discord/posts` returns 200 | `DiscordPostControllerIT` | IT (Failsafe) | ✅ green |
| 102-01-03 | 94 CR-02 | REVIEW-FIX-01 | T-102-01-ORPH | Webhook-create-fail path issues cleanup DELETE on `/channels/{id}`; no orphan channel | `DiscordChannelServiceWebhookFailIT` + `DiscordChannelServicePermissionAuditFailIT` | IT (Failsafe + WireMock) | ✅ green |
| 102-01-04 | 95 CR-02 | REVIEW-FIX-01 | — | `RaceService.saveRace` publishes `MatchScheduleFieldsChangedEvent` AFTER_COMMIT iff `dateTime` changed; score aggregation invariant preserved | `DiscordAutoPostListenerScheduleEditIT` + `RaceServiceTest` | IT + unit | ✅ green |
| 102-01-05 | 98 BL-01 | REVIEW-FIX-01 | — | `canPostMatchdayPairings` AND `canPostMatchdaySchedule` reject empty + all-BYE matchdays; mixed matchdays pass | `DiscordPostServiceByeMatchdayGuardTest` *(filename deviation: plan listed `…IT.java`; closed as unit `…Test.java`)* + `DiscordPostServiceMatchdayPairingsPreFlightTest` + `…MatchdaySchedulePreFlightTest` | unit (Surefire) | ✅ green |
| 102-01-06 | 98 BL-02 | REVIEW-FIX-01 | — | `seedFullMatchdayLifecycle` uses isolated `T-ALC` shortName; no collision with dev-seed `T-ALF` | `TestDataServiceLifecycleSeedTest` | IT (Failsafe) | ✅ green |
| 102-01-07 | 101 CR-01 | REVIEW-FIX-01 | — | `V1_TABLES_24` uses plural JPA table names (`race_scorings`, `match_scorings`); schema-membership oracle pins drift | `BackupLenientV1AcceptanceIT` | IT (Failsafe) | ✅ green |
| 102-01-08 | 101 CR-02 | REVIEW-FIX-01 | T-102-01-NULL | Restorers raise `BackupArchiveException(MANIFEST_INVALID, …)` for 6 NOT-NULL columns; no NPE on missing fields | `DiscordGlobalConfigRestorerGuardTest` | unit (Surefire, 7 cases) | ✅ green |

### Plan 102-02 — Warning + Refactor + Fold-back (REVIEW-FIX-02)

| Task | Finding(s) | Requirement | Test Class(es) | Type | Status |
|------|------------|-------------|----------------|------|--------|
| 102-02-01 | 95 WR-thin-1 | REVIEW-FIX-02 | `MatchControllerDetailViewModelTest` + `MatchControllerTest` | unit | ✅ green |
| 102-02-02 | 95 WR-thin-2 | REVIEW-FIX-02 | `DiscordSeasonViewServiceTest` + `SeasonControllerTest` | unit | ✅ green |
| 102-02-03 | 95 WR-thin-3 / 95 WR-05 | REVIEW-FIX-02 | `StandingsServiceStalenessSnapshotTest` + `MatchdayControllerTest` | unit | ✅ green |
| 102-02-04 | 95 WR-04/07, 94 WR-06, 98 WR-02/03 | REVIEW-FIX-02 | `DiscordPostRefSeasonRefWidenedTest`, `DiscordConfigFormTest`, `DiscordPostServiceWebhookUrlPatternTest`, `DiscordPostServiceMatchdayScheduleIT`, `DiscordPostServiceMatchdayPairingsIT` | unit + IT | ✅ green |
| 102-02-05 | 94 WR-01..05 | REVIEW-FIX-02 | `MatchControllerMoveToArchiveErrorCategoryTest`, `DiscordRoleCacheTest`, `DiscordPostServicePreFlightTest`, `DiscordChannelServicePermissionAuditFailIT`, `TeamEffectiveDiscordRoleIdTest` | unit + IT | ✅ green |
| 102-02-06 | 95 WR-01/02/03/06/08 | REVIEW-FIX-02 | `ScoringServiceTest`, `DiscordPostServiceForumThreadIT`, `DiscordChannelArchiveServiceWireMockIT` | unit + IT | ✅ green |
| 102-02-07 | 96 WR-02/05/06/07/08, 97 WR-02/03 | REVIEW-FIX-02 | `DiscordPostServiceRefBranchesTest`, `ProvisionalScoresGraphicServiceTest`, `StandingsServicePhaseScopedStaleDetectionIT`, `RaceControllerPostRaceResultToForumIT` | unit + IT | ✅ green |
| 102-02-08 | 98 WR-01/04/05/06, 99 WR-01/02 | REVIEW-FIX-02 | `DiscordConfigFormTest`, `BackupSchemaGuardTest`, `MatchdayControllerPostEndpointsIT`, `DiscordRestClientIT`, `DiscordDevSeederIT` | unit + IT | ✅ green |
| 102-02-09 | 101 WR-01..07 | REVIEW-FIX-02 | `BackupSchemaGuardTest`, `BackupRoundTripIT`, `BackupImportSchemaMismatchIT`, `BackupLenientV1AcceptanceIT` | unit + IT | ✅ green |
| 102-02-10 | 92 WR-01/03/06, 93 WR-01..06 | REVIEW-FIX-02 | `CsvImportControllerExceptionTest`, `AssumptionsFencePredicateTest`, `GoogleCalendarServiceIT`, `GoogleSheetsServiceIT`, `DiscordConfigControllerErrorCategoryTest`, `DiscordRateLimitInterceptorIT`, `DiscordRestClientIT`, `DiscordClientHostWhitelistTest` | unit + IT | ✅ green |
| 102-02-11 | verification gate | REVIEW-FIX-02 | cumulative Surefire + wide Failsafe | gate | ✅ green |
| 102-02-FB | Post-review fold-back: 2 CR + 11 WR + 8 IN | REVIEW-FIX-02 | `DiscordMatchdayViewService` boundary tests, `MatchdayControllerPostEndpointsIT` enrichment IT, `DiscordSeasonViewService` no-write-on-GET unit test, `ScoringServiceTest` playoff branch, `MatchService` Discord-channel guard test, `DiscordPostService` forum-thread archive IT, `DiscordChannelService` `parseAllow` IT, `DiscordPostServiceEscapeMarkdownLinkUrlTest`, `BackupSchemaPinFkEntitiesLastTest` | unit + IT | ✅ green |

### Plan 102-03 — Info Sweep (REVIEW-FIX-03)

| Task | Closure mechanism | Verification | Status |
|------|-------------------|--------------|--------|
| 102-03-01 (src/main sweep) | grep-oracle = 0 lines on `src/main` | `grep -rnE "^\s*(//\|--\|#)\s*(Phase \|Plan \|D-[0-9]\|UAT-\|WR-\|CR-\|IN-\|BL-\|Wave )" src/main` returns 0; `./mvnw test-compile` exit 0 | ✅ green |
| 102-03-02 (src/test sweep) | grep-oracle = 0 lines on `src/test` | same oracle on `src/test` returns 0; BDD `// given/when/then` preserved (regex-orthogonal) | ✅ green |
| 102-03-03 (Flyway headers) | inapplicable per CLAUDE.md "Do Not Modify Flyway Migrations" — V8/V9/V13/V15 already clean; V7/V10 markers documented inapplicable | Flyway dev startup via `./scripts/app.sh start dev` shows no `MigrationChecksumMismatch` | ✅ no-op (documented) |
| 102-03-04 (dead-code) | grep-then-delete for unused symbols | `./mvnw test -Dtest=SeasonFormTest,SeasonControllerTest,DiscordTimestampsTest -DfailIfNoTests=true` exit 0 | ✅ green |
| 102-03-05 (style + correctness) | 97 IN-04 hex sanitiser (initial), 96 IN-04 null-guard, 101 IN-05 CRLF sanitiser, 93 IN-04 `ThreadLocalRandom`, 93 IN-03 volatile-map, 99 IN-01 `@JsonIgnoreProperties`, 99 IN-02 `@JsonInclude(NON_NULL)`, multiple template inline-style strips | `BackupSchemaGuardTest`, `SeasonManagementServiceTest`, full Surefire + Failsafe regression | ✅ green |

**Inapplicable findings (per CONTEXT D-02, documented in 102-03 SUMMARY § "Inapplicable findings"):** 93 IN-01/08, 94 IN-01 (V10 portion), 95 IN-02/04/05, 96 IN-01/02/03, 97 IN-01/02/03/06, 98 IN-01/02/03/04/05, 99 IN-03/04, 101 IN-01/02/04 — each with grep evidence or referenced spec/convention.

### Plan 102-04 — Close-Loop Remediation (REVIEW-FIX-01/02/03)

| Task | Finding | Requirement | Threat Ref | Secure Behavior | Test Class | Type | Status |
|------|---------|-------------|------------|-----------------|------------|------|--------|
| 102-04-2R1 | Close-loop W1 | REVIEW-FIX-02 | CSS-injection on `th:style` | `Team.primaryColor/secondaryColor/accentColor` sanitised by `HexColor.sanitize` on CREATE + UPDATE paths in BOTH `TeamManagementService.save` and `SeasonManagementService.updateSeasonTeam` | `HexColorTest` (16 cases incl. injection payloads) + `TeamManagementServiceTest` | unit | ✅ green |
| 102-04-2R2 | Close-loop W2 | REVIEW-FIX-02 | NPE / DoS on partial match | `teamSlugOrFallback(Match)` returns `"race"` when match or either team is null; safe filename composition | `DiscordPostServiceForumThreadFilenameTest` (4 paths) | unit | ✅ green |
| 102-04-2R3 | Close-loop W3 | REVIEW-FIX-02 | — | `GlobalModelAdvice.activeRoute` centralises URL-prefix → activeRoute mapping; 19 sidebar links migrated; ordering invariant pinned (e.g., `/admin/tools/team-cards` before `/admin/teams`) | `GlobalModelAdviceActiveRouteTest` (29 parameterised + 5 edge cases) + `DiscordPostFilterControllerIT` | unit + IT | ✅ green |
| 102-04-2R4 | Close-loop I1 | REVIEW-FIX-03 | — | `recomputeMatchScoresFromAllLegs` emits `log.warn` with `{}` placeholders on null-team skip (Match.homeTeam + PlayoffMatchup.team1) | `ScoringServiceTest` | unit | ✅ green |
| 102-04-2R5 | Close-loop I2 | REVIEW-FIX-03 | — | Explicit `matchRepository.save(match)` + `playoffMatchupRepository.save(matchup)` after score mutation; behaviour-neutral inside existing `@Transactional` boundary | `ScoringServiceTest` (with `@Mock MatchRepository` + `@Mock PlayoffMatchupRepository`) | unit | ✅ green |
| 102-04-1 | End-of-phase gate | REVIEW-FIX-01/02/03 | — | `./mvnw clean verify -Pe2e` exit 0; JaCoCo line 89.43 %; SpotBugs `BugInstance` 0 | Maven build (Surefire 1752 / Failsafe IT 526 / E2E 115 = 2393 tests; 0 failures / 0 errors / 5 skipped) | gate | ✅ green |
| 102-04-2 / 2R6 | Reviewer clean | REVIEW-FIX-01/02/03 | — | `gsd-code-reviewer` Pass 2 over `d6b5ab01..08c505be` (134 files, 45 commits): CLEAN (zero critical + zero warning + zero info) | reviewer subagent | gate | ✅ green |
| 102-04-3 | Historical record | REVIEW-FIX-01/02/03 | — | `102-REVIEW.md` authored with first-pass findings, remediation commits, second-pass clean, end-gate metrics, memory-promotion candidates | doc-existence check | gate | ✅ green |

---

## Wave 0 Requirements

Existing infrastructure (JUnit 5 + Spring Boot Test + Mockito + WireMock + Playwright + JaCoCo + SpotBugs + find-sec-bugs) covered all Phase 102 requirements. No new framework, no new test-runner config, no Wave 0 stubs needed. Phase 102 only added regression-fence tests in already-supported frameworks.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Flyway checksum stability for V8/V9/V13/V15 headers when edits were considered (Plan 102-03 Task 3) | REVIEW-FIX-03 | Plan 102-03 SUMMARY § Task 3 reduced this to a no-op (the plan-named files were already marker-free); the Flyway-startup sanity check via `./scripts/app.sh start dev` is the human-observable confirmation. Automated re-run is unnecessary because the executable SQL bytes are unchanged. | `./scripts/app.sh start dev` → tail logs → confirm no `MigrationChecksumMismatch` → `./scripts/app.sh stop dev`. Documented as no-op in 102-03 SUMMARY. |

All other phase behaviors have automated verification under the per-task `<verify>` blocks or the end-of-phase `./mvnw clean verify -Pe2e` gate.

---

## Validation Audit 2026-05-28

| Metric | Count |
|--------|-------|
| Requirements audited | 3 (REVIEW-FIX-01, REVIEW-FIX-02, REVIEW-FIX-03) |
| Input findings tracked | 119 input + 21 fold-back + 5 close-loop = **145** |
| Findings COVERED by automated tests | 145 |
| Findings PARTIAL | 0 |
| Findings MISSING | 0 |
| Inapplicable findings (documented rationale) | 22 info findings (Plan 102-03) + 2 Flyway header buckets (V7/V10) |
| New regression-fence test classes added | 13 (Plan 102-01: 7 · Plan 102-02 + fold-back: 4 · Plan 102-04: 4 — net of overlap) |
| New test methods added across the phase | +54 (delta from `cd414ffb` → `08c505be`: 2339 → 2393 tests) |
| Test-class filename deviations | 1 (`DiscordPostServiceByeMatchdayGuardTest.java` instead of `…GuardIT.java` — coverage equivalent, documented in Plan 102-01 SUMMARY Deviation #1) |
| Gaps filled by this audit | 0 (no auditor subagent dispatched) |
| Escalated to manual-only | 1 (Flyway-startup observation — documented no-op) |

**Conclusion:** Phase 102 is **Nyquist-compliant**. Every closed finding has automated verification via a regression-fence test, an extended existing test assertion, the blanket grep oracle for comment pollution, or the end-of-phase `./mvnw clean verify -Pe2e` + `gsd-code-reviewer` gates. There are no MISSING or PARTIAL coverage gaps.

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or are covered by the end-of-phase `clean verify -Pe2e` gate
- [x] Sampling continuity: no 3 consecutive tasks without automated verify (every task in 102-01..04 had a per-task verify command)
- [x] Wave 0 covers all MISSING references (none — existing infrastructure sufficient)
- [x] No watch-mode flags (Surefire + Failsafe are single-shot per Maven lifecycle)
- [x] Feedback latency < 30 s for targeted runs; full suite 9:51 min (within CI median budget)
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** reconstructed 2026-05-28 (validate-phase audit, State B).
