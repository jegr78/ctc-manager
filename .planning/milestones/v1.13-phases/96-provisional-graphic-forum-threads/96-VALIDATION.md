---
phase: 96
slug: provisional-graphic-forum-threads
status: complete
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-23
completed: 2026-05-23
---

# Phase 96 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + WireMock + Playwright |
| **Config file** | `pom.xml` (Surefire `@Tag` includes, Failsafe `@Tag("e2e")` via `-Pe2e`) |
| **Quick run command** | `./mvnw test -Dtest='ProvisionalScores*,DiscordPostServiceRefBranchesTest,DiscordForumServiceTest'` |
| **Full suite command** | `./mvnw verify -Pe2e` |
| **Estimated runtime** | Surefire+Failsafe baseline ~14m + ~90s Phase 96 delta (8-12 new WireMock ITs + 3 Playwright E2E + Mobile sweep) |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test -Dtest='<the test class added/touched>'` (Mockito-only) — feedback < 30 s
- **After every plan wave:** Run `./mvnw verify` (Surefire + Failsafe + JaCoCo, no Playwright) — feedback < 16 m
- **Before phase close (`/gsd-validate-phase 96`):** Run `./mvnw verify -Pe2e` (incl. Playwright + Mobile sweep per [[feedback-e2e-verification]]) — feedback < 18 m
- **Max feedback latency:** ~30 s for task-local quick run; ~16 m for wave-level verify

---

## Per-Task Verification Map

> Populated by the planner per task. Plans 96-01..03 each ship with their own per-task assertions in `<acceptance_criteria>` blocks; the rows below list the Phase-96 test classes that gate plan closure.

| Plan | Wave | Requirement | Test Class | Test Type | Automated Command | File Exists | Status |
|------|------|-------------|------------|-----------|-------------------|-------------|--------|
| 96-01 | 1 | GRAFX-01 | `ProvisionalScoresGraphicServiceTest` | unit (Mockito) | `./mvnw test -Dtest=ProvisionalScoresGraphicServiceTest` | ✅ exists | ✅ green |
| 96-01 | 1 | GRAFX-01 | `DiscordPostServiceProvisionalScoresIT` | IT (WireMock) | `./mvnw verify -Dit.test=DiscordPostServiceProvisionalScoresIT` | ✅ exists | ✅ green |
| 96-01 | 1 | GRAFX-01 | `MatchControllerProvisionalPostIT` | IT (WireMock + MockMvc) | `./mvnw verify -Dit.test=MatchControllerProvisionalPostIT` | ✅ exists | ✅ green |
| 96-01 | 1 | GRAFX-01 | `MatchDetailProvisionalButtonsE2ETest` | E2E (Playwright + Mobile sweep) | `./mvnw verify -Pe2e -Dit.test=MatchDetailProvisionalButtonsE2ETest` | ✅ exists | ✅ green |
| 96-02 | 2 | FORUM-01 | `DiscordForumServiceTest` | unit (Mockito) | `./mvnw test -Dtest=DiscordForumServiceTest` | ✅ exists | ✅ green |
| 96-02 | 2 | FORUM-01 | `DiscordForumServiceIT` | IT (WireMock) | `./mvnw verify -Dit.test=DiscordForumServiceIT` | ✅ exists | ✅ green |
| 96-02 | 2 | FORUM-01 | `SeasonControllerLinkThreadIT` | IT (WireMock + MockMvc) | `./mvnw verify -Dit.test=SeasonControllerLinkThreadIT` | ✅ exists | ✅ green |
| 96-02 | 2 | FORUM-01 | `V13MigrationIT` (Flyway H2+MariaDB drill) | IT (Flyway) | `./mvnw verify -Dit.test=V13MigrationIT` | ✅ exists | ✅ green |
| 96-02 | 2 | FORUM-01 | `SeasonEditDiscordSectionE2ETest` | E2E (Playwright + Mobile sweep) | `./mvnw verify -Pe2e -Dit.test=SeasonEditDiscordSectionE2ETest` | ✅ exists | ✅ green |
| 96-03 | 3 | FORUM-02 | `DiscordPostServiceRefBranchesTest` | unit (Mockito) | `./mvnw test -Dtest=DiscordPostServiceRefBranchesTest` | ✅ exists | ✅ green |
| 96-03 | 3 | FORUM-02 | `DiscordPostServiceForumThreadIT` | IT (WireMock) | `./mvnw verify -Dit.test=DiscordPostServiceForumThreadIT` | ✅ exists | ✅ green |
| 96-03 | 3 | FORUM-02 | `DiscordWebhookClientThreadIdIT` | IT (WireMock) | `./mvnw verify -Dit.test=DiscordWebhookClientThreadIdIT` | ✅ exists | ✅ green |
| 96-03 | 3 | FORUM-02 | `RaceControllerPostRaceResultToForumIT` | IT (WireMock + MockMvc) | `./mvnw verify -Dit.test=RaceControllerPostRaceResultToForumIT` | ✅ exists | ✅ green |
| 96-03 | 3 | FORUM-02 | `RaceDetailForumPostButtonE2ETest` | E2E (Playwright + Mobile sweep) | `./mvnw verify -Pe2e -Dit.test=RaceDetailForumPostButtonE2ETest` | ✅ exists | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Validation Dimensions (Nyquist)

| Dimension | Coverage Method | Tests / Artifact |
|-----------|-----------------|------------------|
| **Code (logic / branch)** | JaCoCo line + branch coverage ≥ 88.88% (Phase 95 baseline; see `pom.xml`) | All Mockito unit + WireMock IT classes above |
| **Database (Flyway H2+MariaDB symmetry)** | New `V13MigrationIT` runs against both H2 (default profile) and MariaDB (`local` profile via Testcontainers or Maven `local` profile) | `V13MigrationIT` |
| **Discord API contract** | WireMock-stubbed Discord REST + webhook endpoints (multipart-post, PATCH-edit, `GET /channels/{threadId}`, PATCH unarchive, list-active, list-archived) | `DiscordPostService*IT`, `DiscordForumServiceIT`, `DiscordWebhookClientThreadIdIT` |
| **Visual (Provisional PNG layout)** | Iterative `playwright-cli` loop against `.screenshots/96-01/provisional-reference.png` per [[feedback-graphic-design-iteration]] + [[feedback-graphic-pixel-positioning]]; Visual-regression pixel-hash test is OPTIONAL (D-96-01 Claude's Discretion) | `MatchDetailProvisionalButtonsE2ETest` smoke + manual operator review pre-plan-close |
| **Mobile-viewport** | Playwright Mobile sweep on Match-Detail (96-01) + Season-Edit Discord Section (96-02) + Race-Detail (96-03) per [[feedback-playwright-cli]] | each `*E2ETest` runs Desktop + Mobile variant |
| **Backup wire-contract** | `BackupSchemaGuardTest` unchanged (`EXPORT_ORDER=25`, `SCHEMA_VERSION=2`); `SeasonMixIn` includes new thread-IDs, `DiscordGlobalConfigMixIn` (added or absent) skips webhook URLs (secret discipline, D-96-07) | `BackupSchemaGuardTest`, `SeasonRoundTripIT`, `DiscordGlobalConfigRoundTripIT` (latter NEW if MixIn added) |
| **Static analysis — SpotBugs + find-sec-bugs** | `./mvnw verify` enforces 0 Medium+HIGH findings; new code in `org.ctc.admin.service.ProvisionalScoresGraphicService`, `org.ctc.discord.service.DiscordForumService`, `DiscordPostService` extension reuses existing `@SuppressFBWarnings` patterns | gate-step on `verify` |
| **Static analysis — CodeQL** | PR-gate workflow at `.github/workflows/codeql.yml`; no new SSRF suppressions expected (forum-webhook URLs go through existing `DiscordHostValidator`) | `gh run watch` after PR push |
| **Live-UAT (operator)** | UAT-06 staged as STATE.md Pending UAT per D-96-10; operator runs against test guild before Phase 97 starts | `STATE.md` UAT-06 row |

---

## Wave 0 Requirements

- [x] `ProvisionalScoresGraphicServiceTest` (Plan 96-01 Wave 0)
- [x] `DiscordPostServiceProvisionalScoresIT` (Plan 96-01 Wave 0)
- [x] `MatchControllerProvisionalPostIT` (Plan 96-01 Wave 0)
- [x] `MatchDetailProvisionalButtonsE2ETest` (Plan 96-01 Wave 0)
- [x] `DiscordForumServiceTest` (Plan 96-02 Wave 0)
- [x] `DiscordForumServiceIT` (Plan 96-02 Wave 0)
- [x] `SeasonControllerLinkThreadIT` (Plan 96-02 Wave 0)
- [x] `V13MigrationIT` (Plan 96-02 Wave 0)
- [x] `SeasonEditDiscordSectionE2ETest` (Plan 96-02 Wave 0)
- [x] `DiscordPostServiceRefBranchesTest` (Plan 96-03 Wave 0)
- [x] `DiscordPostServiceForumThreadIT` (Plan 96-03 Wave 0)
- [x] `DiscordWebhookClientThreadIdIT` (Plan 96-03 Wave 0)
- [x] `RaceControllerPostRaceResultToForumIT` (Plan 96-03 Wave 0)
- [x] `RaceDetailForumPostButtonE2ETest` (Plan 96-03 Wave 0)

Existing infrastructure (JUnit 5, Mockito, WireMock, Playwright, JaCoCo, SpotBugs, CodeQL) covers all Phase-96 dimensions — no framework install required.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Provisional PNG visual fidelity vs Google-Sheets reference | GRAFX-01 | Pixel layout requires human eye against `.screenshots/96-01/provisional-reference.png`; visual-regression-snapshot test is deferred (D-96-01 Claude's Discretion) | `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo` → `/admin/matches/{id}` → click "Post Provisional Scores" → operator reviews PNG; iterate via `playwright-cli` per [[feedback-graphic-design-iteration]] |
| UAT-06 Live Provisional + Forum-Thread Lifecycle | GRAFX-01, FORUM-01, FORUM-02 | Requires real Discord guild + real Bot-Token + real archived/pinned threads (no WireMock substitute for inactivity auto-archive behavior) | Per D-96-10 step-by-step: 1) populate 2 webhook URLs in discord-config; 2) link race-results + standings threads via Modal-Picker (pinned auto-select); 3) post provisional scores from Match-Detail with ≥1 race results; 4) re-post after race-2 completes; 5) post race-result to forum-thread; 6) archive thread manually in Discord, re-post → bot auto-unarchives; 7) verify `/admin/discord/posts` lists all 5+N posts; 8) document in STATE.md UAT-06 row |
| Backup round-trip with new Discord-Thread-ID columns | FORUM-01 | Export + restore on a fresh database to confirm SeasonMixIn carries `discordRaceResultsThreadId` + `discordStandingsThreadId`; webhook URLs deliberately omitted (secret discipline) | `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` → `/admin/backup/export` → restore on fresh `dev` profile → verify season-edit page shows linked threads, discord-config webhook fields are empty |

---

## Validation Sign-Off

- [x] Plans 96-01..03 each ship with `<acceptance_criteria>` per task referencing the test classes in the Per-Task Verification Map
- [x] Plans 96-01..03 each ship with their own `VALIDATION.md` per D-96-08 (per-plan Nyquist file)
- [x] Sampling continuity: each plan wave has ≥ 1 unit test + ≥ 1 WireMock IT + ≥ 1 Playwright E2E
- [x] Wave 0 covers all 14 MISSING references (created during execution per `superpowers:test-driven-development`)
- [x] No watch-mode flags (project convention — `./mvnw verify` is single-shot)
- [x] Feedback latency: < 30s task-quick, < 16m wave-full, < 18m phase-close-with-E2E
- [x] `nyquist_compliant: true` set in frontmatter after `/gsd-validate-phase 96` PASS

**Approval:** COMPLETE 2026-05-23 — Phase 96 close-gate green (2120 tests, commit `3791622f`); UAT-06 full PASS (commit `b01af26d`).

---

## Validation Audit 2026-05-23

| Metric | Count |
|--------|-------|
| Planned test classes | 14 |
| Files exist on disk | 14 |
| Per-plan VALIDATION marked compliant | 3 / 3 (96-01, 96-02, 96-03) |
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |

**Evidence base:**

- Targeted quick run during audit: `./mvnw test -Dtest='ProvisionalScores*,DiscordPostServiceRefBranchesTest,DiscordForumServiceTest'` → **19 tests, 0 failures, 0 errors, 0 skipped** (2.85 s).
- Phase-close gate: full `./mvnw verify -Pe2e` recorded in commit `3791622f` ("Phase-96 close gate (2120 tests green) + stage UAT-06").
- Manual-only / live-Discord verification: UAT-06 closed full PASS (commit `b01af26d`, auto-unarchive verified end-to-end against live test guild).

**Drift fixed:** phase-level rollup frontmatter (`status`, `nyquist_compliant`, `wave_0_complete`) and Per-Task Map status column were stale relative to the per-plan VALIDATION.md files. No test gaps; documentation-only update.
