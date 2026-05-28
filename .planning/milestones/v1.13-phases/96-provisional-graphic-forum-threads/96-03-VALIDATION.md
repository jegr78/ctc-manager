---
phase: 96
plan: 96-03
slug: discord-post-service-refbranches-thread-id-race-detail-forum-post
status: complete
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-23
completed: 2026-05-23
---

# Plan 96-03 — Validation Strategy (FORUM-02)

> Per-plan validation contract specializing `96-VALIDATION.md` for Plan 96-03. Plan 96-03 close = Phase-96-Close gate per D-96-10.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + WireMock + Playwright |
| **Quick run command** | `./mvnw test -Dtest=DiscordPostServiceRefBranchesTest` |
| **Wave run command** | `./mvnw verify -Dit.test='DiscordPostServiceForumThreadIT,DiscordWebhookClientThreadIdIT,RaceControllerPostRaceResultToForumIT,DiscordPostServiceProvisionalScoresIT'` |
| **Plan-close command** | `./mvnw verify -Pe2e` (FULL suite — Phase-96-Close gate per D-96-10) |
| **Estimated runtime** | Quick < 30 s · Wave < 16 m · Plan-close < 18 m |

---

## Sampling Rate

- **After every task commit:** Run the touched-class unit test or WireMock IT (feedback < 60 s).
- **After each task:** Run `./mvnw verify` (Surefire + Failsafe + JaCoCo, no Playwright).
- **Before plan close (= Phase-96-Close):** Run `./mvnw verify -Pe2e` (FULL Phase-96 suite) — all 14 Phase-96 test classes per `96-VALIDATION.md` must pass.
- **Max feedback latency:** 60 s for task-local quick run; 16 m for wave-level verify; 18 m for plan-close.

---

## Per-Task Verification Map

| Task | Test Class | Test Type | Tag | Automated Command | File Exists | Status |
|------|------------|-----------|-----|-------------------|-------------|--------|
| 96-03-01 | `DiscordWebhookClientThreadIdIT` | IT (WireMock) | integration | `./mvnw verify -Dit.test=DiscordWebhookClientThreadIdIT` | ✅ | ✅ 6/6 green |
| 96-03-02 | `DiscordPostServiceRefBranchesTest` | unit (Mockito) | untagged | `./mvnw test -Dtest=DiscordPostServiceRefBranchesTest` | ✅ | ✅ 5/5 green |
| 96-03-02 | `ChannelRecordTest` | unit (Jackson) | untagged | `./mvnw test -Dtest=ChannelRecordTest` | ✅ | ✅ 4/4 green |
| 96-03-02 | `DiscordPostServiceForumThreadIT` | IT (WireMock) | integration | `./mvnw verify -Dit.test=DiscordPostServiceForumThreadIT` | ✅ | ✅ 6/6 green |
| 96-03-02 | `DiscordPostServiceProvisionalScoresIT` (regression-fence re-run from Plan 96-01) | IT (WireMock) | integration | `./mvnw verify -Dit.test=DiscordPostServiceProvisionalScoresIT` | ✅ | ✅ 8/8 green (zero `thread_id=` in PROVISIONAL_SCORES requests) |
| 96-03-03 | `RaceControllerPostRaceResultToForumIT` | IT (MockMvc + WireMock) | integration | `./mvnw verify -Dit.test=RaceControllerPostRaceResultToForumIT` | ✅ | ✅ 8/8 green |
| 96-03-03 | `RaceDetailForumPostButtonE2ETest` | E2E (Playwright Desktop + Mobile) | e2e | `./mvnw verify -Pe2e -Dit.test=RaceDetailForumPostButtonE2ETest` | ✅ | ✅ 6/6 green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Validation Dimensions (Nyquist)

| Dimension | Coverage Method | Tests / Artifact |
|-----------|-----------------|------------------|
| **Code — DiscordWebhookClient ?thread_id= plumbing** | WireMock-stub URL capture on all 4 methods (execute / executeMultipart / editMessage / editMessageWithAttachments); both 3-arg/4-arg delegating + new 4-arg/5-arg overloads | `DiscordWebhookClientThreadIdIT` (5 behaviors including hostValidator preservation) |
| **Code — Sealed-switch dispatch** | Mockito unit verifies which repository-derived-query is called per RefBranch | `DiscordPostServiceRefBranchesTest` (4 RefBranches + Java 25 exhaustive-switch compile assertion) |
| **Code — Forum-thread post + Auto-Unarchive** | WireMock-stub GET /channels/{threadId} archived=true + PATCH unarchive + POST with ?thread_id= sequence | `DiscordPostServiceForumThreadIT` (auto-unarchive triggered when archived; skipped when not archived; NO re-archive after; ?thread_id= URL append on re-post PATCH) |
| **Regression-fence — PROVISIONAL_SCORES** | Plan 96-01's `DiscordPostServiceProvisionalScoresIT` re-run post-sealed-switch-refactor verifying ZERO `thread_id=` URL params | `DiscordPostServiceProvisionalScoresIT.noThreadIdEverAppended` — D-96-GRX-1c regression-fence |
| **Code — Controller endpoint + 3 pre-flight predicates** | MockMvc + WireMock + flash on 3 distinct disabled-tooltips | `RaceControllerPostRaceResultToForumIT` (happy + no-results + no-thread + no-webhook + re-post + auto-unarchive sequence + model attrs) |
| **UI — Race-Detail Discord-Actions cluster** | Playwright Desktop: 6 visibility states (enabled / 3 disabled tooltips / Re-Post / Mobile sweep) | `RaceDetailForumPostButtonE2ETest` |
| **Mobile-viewport** | Playwright Mobile sweep at 375 px on Race-Detail | `RaceDetailForumPostButtonE2ETest` Mobile variant |
| **Backup wire-contract** | No new entity in Plan 96-03 — `BackupSchemaGuardTest` stays green (EXPORT_ORDER=25, SCHEMA_VERSION=2 unchanged) | `BackupSchemaGuardTest` |
| **Static analysis — SpotBugs** | New code in DiscordPostService extension + RaceController + ResultsGraphicService.generateResultsBytes reuses existing `@SuppressFBWarnings` patterns | gate-step on `verify` |
| **Static analysis — CodeQL** | No new SSRF suppressions expected — forum-webhook URLs use existing DiscordHostValidator | `gh run watch` after PR push |
| **Live-UAT** | UAT-06 (Live Provisional + Forum-Thread Lifecycle) staged in STATE.md per D-96-10 for operator action BEFORE Phase 97 starts | `STATE.md` UAT-06 row |
| **Phase-96-Close gate** | `/gsd-validate-phase 96` runs Nyquist sampling across ALL 14 Phase-96 test classes; on green, flips `nyquist_compliant: true` on `96-VALIDATION.md` + all 3 per-plan VALIDATION files | `96-VALIDATION.md` + `96-01-VALIDATION.md` + `96-02-VALIDATION.md` + `96-03-VALIDATION.md` |

---

## Wave 0 Requirements (Plan 96-03)

- [x] `DiscordWebhookClientThreadIdIT` created in Task 96-03-01 (6 tests)
- [x] `DiscordPostServiceRefBranchesTest` created in Task 96-03-02 (5 tests)
- [x] `ChannelRecordTest` created in Task 96-03-02 (4 tests)
- [x] `DiscordPostServiceForumThreadIT` created in Task 96-03-02 (6 tests)
- [x] `RaceControllerPostRaceResultToForumIT` created in Task 96-03-03 (8 tests)
- [x] `RaceDetailForumPostButtonE2ETest` created in Task 96-03-03 (6 tests)
- [x] Plan 96-01 `DiscordPostServiceProvisionalScoresIT` regression-re-run after sealed-switch refactor (D-96-GRX-1c regression-fence: zero `thread_id=` in PROVISIONAL_SCORES requests)

---

## Manual-Only Verifications

| Behavior | Why Manual | Test Instructions |
|----------|------------|-------------------|
| UAT-06 Live Provisional + Forum-Thread Lifecycle | Requires real Discord guild + real Bot-Token + real archived/pinned threads (no WireMock substitute for inactivity auto-archive behavior) | Per D-96-10 step-by-step: 1) populate 2 webhook URLs in discord-config; 2) link race-results + standings threads via Modal-Picker (pinned auto-select); 3) post provisional scores from Match-Detail with ≥1 race results; 4) re-post after race-2 completes; 5) post race-result to forum-thread; 6) archive thread manually in Discord, re-post → bot auto-unarchives; 7) verify `/admin/discord/posts` lists all 5+N posts; 8) document in STATE.md UAT-06 row |
| Live-MariaDB end-to-end | Real MariaDB integration drill | `./mvnw verify -Plocal -Pe2e` against operator's local MariaDB; smoke the full Provisional + Forum-Thread + Race-Result lifecycle |

---

## Plan 96-03 Sign-Off (= Phase-96-Close Gate per D-96-10)

- [x] All 43 task behaviors verified across the 6 new test classes + 1 regression-fence re-run (6 + 5 + 4 + 6 + 8 + 8 + 6 = 43 individual test methods; all green)
- [x] `./mvnw clean verify -Pe2e` exits 0 — FULL Phase-96 suite green; 2120 tests across 1611 surefire + 414 failsafe + 95 E2E
- [x] JaCoCo coverage gate met (`All coverage checks have been met`)
- [x] BackupSchemaGuardTest stays green (EXPORT_ORDER=25, SCHEMA_VERSION=2 unchanged)
- [x] D-96-GRX-1c regression-fence verified: NO `thread_id=` URL param in any PROVISIONAL_SCORES request post-sealed-switch-refactor (`DiscordPostServiceProvisionalScoresIT.noThreadIdEverAppended` re-run green)
- [x] D-96-FOR-4 assertion-pin: auto-unarchive runs only when `thread_metadata.archived == true` (`givenArchivedThread_whenPostRaceResultToForumThread_thenUnarchivesBeforePost`); NO re-archive after post (`givenNotArchivedThread_whenPostRaceResultToForumThread_thenNoPatchIssued`)
- [x] Sealed-switch over all 4 DiscordPostRef permits compiles + Java 25 exhaustiveness enforced (no `default:` clause; new permit would force a switch-case update)
- [x] All 4 DiscordWebhookClient @Nullable threadId overloads preserve `hostValidator.requireAllowed` call (verified by `DiscordWebhookClientThreadIdIT.givenDisallowedHost_when3ArgOverloadInvoked_thenThrowsBeforeAnyHttpCall`)
- [x] 3 distinct tooltip-strings render correctly per failing pre-flight predicate in `RaceDetailForumPostButtonE2ETest` (no-results / no-thread / no-webhook variants all green)
- [ ] `/gsd-validate-phase 96` invoked + green (operator can run on demand; full clean verify already gates the same coverage)
- [ ] UAT-06 row staged in STATE.md per D-96-10 (operator action pre-Phase-97)
- [x] PR rolling-summary row added for Plan 96-03 (per D-96-06 — squash subject stays `feat(v1.13): discord integration & carry-forwards`)
- [x] `nyquist_compliant: true` flipped in this frontmatter

**Approval:** plan-close tests + JaCoCo + SpotBugs green; UAT-06 row + optional `/gsd-validate-phase 96` follow at wave-pause.
