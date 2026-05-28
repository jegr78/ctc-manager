---
phase: 102-code-review-fixes
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - src/main/java/org/ctc/dataimport/CsvImportController.java
  - src/main/java/org/ctc/discord/web/DiscordPostController.java
  - src/main/java/org/ctc/discord/service/DiscordChannelService.java
  - src/main/java/org/ctc/domain/service/RaceService.java
  - src/main/java/org/ctc/discord/service/DiscordPostService.java
  - src/main/java/org/ctc/admin/TestDataService.java
  - src/test/java/org/ctc/backup/service/BackupLenientV1AcceptanceIT.java
  - src/main/java/org/ctc/backup/restore/entity/DiscordGlobalConfigRestorer.java
  - src/main/java/org/ctc/backup/restore/entity/DiscordPostRestorer.java
  - src/test/java/org/ctc/dataimport/CsvImportControllerIT.java
  - src/test/java/org/ctc/discord/web/DiscordPostControllerIT.java
  - src/test/java/org/ctc/discord/service/DiscordChannelServiceWebhookFailIT.java
  - src/test/java/org/ctc/discord/service/DiscordAutoPostListenerScheduleEditIT.java
  - src/test/java/org/ctc/discord/service/DiscordPostServiceByeMatchdayGuardIT.java
  - src/test/java/org/ctc/admin/TestDataServiceLifecycleSeedTest.java
  - src/test/java/org/ctc/backup/restore/entity/DiscordGlobalConfigRestorerGuardTest.java
autonomous: true
requirements:
  - REVIEW-FIX-01

must_haves:
  truths:
    - "CsvImportController never surfaces e.getMessage() on Google-reachable IllegalArg/IllegalState arms; only the whitelisted user message is flashed (T-91-02-IL invariant pinned)"
    - "DiscordPostController.matchLabel renders bye matches as 'home vs Bye' without NPE; GET /admin/discord/posts returns 200 when a bye match exists in the data set"
    - "DiscordChannelService.createMatchChannel issues the cleanup DELETE in the webhook-create-fail path, mirroring the audit-fail cleanup"
    - "RaceService.saveRace publishes MatchScheduleFieldsChangedEvent when race.dateTime changes; no event is published when dateTime is unchanged"
    - "DiscordPostService.canPostMatchdayPairings and canPostMatchdaySchedule return false on empty matchdays and on all-BYE matchdays (no vacuous allMatch/filter-then-allMatch true)"
    - "TestDataService.seedFullMatchdayLifecycle uses a distinct short-name (T-ALC) that does not collide with the dev-seed T-ALF; TeamRepository.findByShortName returns single rows for both seeds"
    - "BackupLenientV1AcceptanceIT uses plural JPA table names (race_scorings, match_scorings) so the synthetic v1 ZIP exercises the lenient import path; Discord tables stay empty because they are absent, not because the lookup failed"
    - "DiscordGlobalConfigRestorer + DiscordPostRestorer raise BackupArchiveException(MANIFEST_INVALID, ...) on missing NOT-NULL columns; no NPE on JsonNode.get(...).asText() for the six guarded columns"
  artifacts:
    - path: "src/test/java/org/ctc/dataimport/CsvImportControllerIT.java"
      provides: "Regression test for Phase 92 CR-01 — info-leak whitelisted-message-only assertion"
      contains: "googleApiException"
    - path: "src/test/java/org/ctc/discord/web/DiscordPostControllerIT.java"
      provides: "Regression test for Phase 94 CR-01 / 95 CR-01 — bye-match render of /admin/discord/posts"
      contains: "byeMatch"
    - path: "src/test/java/org/ctc/discord/service/DiscordChannelServiceWebhookFailIT.java"
      provides: "Regression test for Phase 94 CR-02 — cleanup-DELETE on webhook-create failure"
      contains: "webhookCreateFail"
    - path: "src/test/java/org/ctc/discord/service/DiscordAutoPostListenerScheduleEditIT.java"
      provides: "Regression test for Phase 95 CR-02 — dateTime-change publishes event, dateTime-unchanged does not"
      contains: "MatchScheduleFieldsChangedEvent"
    - path: "src/test/java/org/ctc/discord/service/DiscordPostServiceByeMatchdayGuardIT.java"
      provides: "Regression test for Phase 98 BL-01 — empty + all-BYE matchday pre-flight on both canPostMatchdayPairings and canPostMatchdaySchedule"
      contains: "allNonByeMatchesFinal"
    - path: "src/test/java/org/ctc/admin/TestDataServiceLifecycleSeedTest.java"
      provides: "Regression test for Phase 98 BL-02 — lifecycle-seed shortName isolation"
      contains: "T-ALC"
    - path: "src/test/java/org/ctc/backup/restore/entity/DiscordGlobalConfigRestorerGuardTest.java"
      provides: "Regression test for Phase 101 CR-02 — 6 NOT-NULL column guards"
      contains: "MANIFEST_INVALID"
  key_links:
    - from: "src/main/java/org/ctc/discord/web/DiscordPostController.java"
      to: "src/main/java/org/ctc/admin/controller/MatchController.java (line 107)"
      via: "awayTeam != null ? team.getShortName() : \"Bye\" defensive pattern"
      pattern: "awayTeam.*!=.*null.*Bye"
    - from: "src/main/java/org/ctc/discord/service/DiscordChannelService.java"
      to: "audit-fail cleanup-DELETE block in the same file"
      via: "same try-catch shape calling channelDeleteCleanup"
      pattern: "discordRestClient\\.deleteChannel"
    - from: "src/main/java/org/ctc/domain/service/RaceService.java"
      to: "src/main/java/org/ctc/discord/service/DiscordAutoPostListener.java#onScheduleFieldsChanged"
      via: "ApplicationEventPublisher.publishEvent(new MatchScheduleFieldsChangedEvent(matchId))"
      pattern: "publishEvent.*MatchScheduleFieldsChangedEvent"
    - from: "src/test/java/org/ctc/backup/service/BackupLenientV1AcceptanceIT.java"
      to: "src/main/java/org/ctc/backup/schema/BackupSchema.java#getExportOrder()"
      via: "plural JPA table-name list aligned to authoritative export-order slugs"
      pattern: "race_scorings|match_scorings"
---

<objective>
Close all 9 critical/blocker findings from the milestone-wide code review (phases 92, 94, 95, 98, 101). Each fix lands paired with a regression-fence test pinning the invariant the finding violated, following TDD-Red/Green per task (CONTEXT D-10).

**Plan budget override:** This plan contains 8 fix-tasks (Phase 94 CR-01 and Phase 95 CR-01 share one fix; the other 7 are 1:1) — exceeding the standard "2-3 tasks per plan" heuristic. CONTEXT D-01 LOCKS the by-severity split (DISCUSSION-LOG.md), and each task is a small mechanical fix with a single targeted test (≤15% context each). Total plan budget fits ~50%.

Purpose: Restore production safety invariants violated by the 9 critical findings — info-leak protection (T-91-02-IL), bye-match defense, webhook-cleanup composition, schedule-edit auto-post hook, pre-flight gating correctness, dev-test seed isolation, lenient-import test correctness, and restorer NOT-NULL guards.

Output: 8 production patches + 8 new regression-fence tests, atomic-commit-per-task. Plan ends with targeted `./mvnw test` / `./mvnw verify -Dit.test=...` green for every affected class. The full `clean verify -Pe2e` runs once in Plan 102-04.

**TDD-RED hygiene (applies to every task in this plan):** Each TDD task follows a strict RED→GREEN sequence executed in this order:

1. **Create the test file first** at the path listed in `<files>`. Write the assertion that pins the invariant.
2. **RED step.** Run `./mvnw test -Dtest=<TestClass>#<testMethod> -DfailIfNoTests=true` (or `./mvnw verify -Dit.test=<ITClass>#<testMethod> -DfailIfNoTests=true` for Failsafe-routed `*IT` classes). This MUST exit non-zero — either because the assertion fails against the still-broken production code, or because the production fix has not yet been applied and the test compiles but fails. With `-DfailIfNoTests=true`, a missing test class also exits non-zero, but the test MUST be in place before this step — the RED proves the assertion fails against unfixed code, not that the file is absent.
3. **Apply the production fix** in the corresponding production file listed in `<files>`.
4. **GREEN step.** Re-run the exact same `./mvnw test -Dtest=...` / `./mvnw verify -Dit.test=...` command with `-DfailIfNoTests=true`. MUST exit 0.
5. **Commit** fix + test atomically (preferred) or as two separate Conventional Commits — fix first, then test, OR test first, then fix.

`-DfailIfNoTests=false` is forbidden for any RED/GREEN step in this plan — its semantics ("missing test = exit 0") mask vacuous-green RED steps and defeat D-10. The flag is allowed only on orchestrator-side smoke runs that span multiple targeted classes where some may not exist yet (none of the per-task `<verify>` commands in this plan fall in that category).
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@.planning/ROADMAP.md
@.planning/phases/102-code-review-fixes/102-CONTEXT.md
@.planning/phases/92-carry-forwards-cleanup/92-REVIEW.md
@.planning/phases/94-team-roles-match-channel-lifecycle/94-REVIEW.md
@.planning/phases/95-match-channel-posts/95-REVIEW.md
@.planning/phases/98-polish-e2e-docs-close/98-REVIEW.md
@.planning/phases/101-backup-restore-covers-discord-schema-v8-v15/101-REVIEW.md
@CLAUDE.md
@.planning/codebase/TESTING.md

<execution_notes>
Per CONTEXT D-05/D-06/D-07: all commits go to `gsd/v1.13-discord-integration` inline-sequentially. Do NOT spawn subagents for code edits — orchestrator works task-by-task. After each task: targeted test command green → atomic commit with Conventional Commit subject. Per CONTEXT D-04 the orchestrator (not this plan) runs `/gsd-code-review 102 --files=<files_modified>` after Wave 1 green and before the plan SUMMARY commit.

Per CLAUDE.md "Conventions / No Comment Pollution": NO `// Phase 102`, `// Plan 102-NN`, `// D-NN`, `// fix(CR-XX)`, `// regression-fence for CR-XX` etc. in any new code or test code. Comments only for non-obvious WHY (rare). The Conventional Commit message carries the finding-ID; the source must not.

Per CLAUDE.md "WireMock vs Real-API": Task 4 (Phase 95 CR-02 listener) MUST NOT use `@MockitoBean DiscordPostService`; the real Spring `@Transactional` proxy must run with a WireMock-backed `DiscordRestClient` so the AFTER_COMMIT semantics are exercised.

Per CLAUDE.md "Spring-Native": Any new client code prefers Spring abstractions; reuse the existing `DiscordRestClient` / `ApplicationEventPublisher` — do NOT introduce JDK `HttpClient` paths.
</execution_notes>
</context>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Google Sheets API → CsvImportController | Untrusted upstream error payloads cross into operator-facing flash messages |
| Discord REST API → DiscordRestClient | Untrusted upstream responses cross into channel-creation lifecycle |
| Backup ZIP → restorer pipeline | Untrusted JSON-row payloads cross into NOT-NULL DB columns |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-102-01-IL | Information Disclosure | `CsvImportController` `IllegalArg|IllegalState` arms (lines 154-159, 265) | mitigate | Regression-fence asserts flash-text equals the whitelisted user-message constant — never `e.getMessage()`. Pins the T-91-02-IL invariant first introduced in v1.12 UX-01 |
| T-102-01-NPE | Denial of Service | `DiscordPostController.matchLabel` on bye-match data | mitigate | Mirror `MatchController.detail:107` `awayTeam != null ? team.getShortName() : "Bye"` defensive pattern; IT asserts `/admin/discord/posts` returns 200 with a bye match in scope |
| T-102-01-ORPH | Resource Leak / Tampering | `DiscordChannelService.createMatchChannel` webhook-fail path leaves orphan channel | mitigate | Compose the same cleanup-DELETE block already used on audit-fail; WireMock IT asserts the DELETE was issued |
| T-102-01-NULL | Tampering / DoS | restorer NPE on `JsonNode.get(X).asText()` for missing NOT-NULL columns | mitigate | Guard via `BackupArchiveException(MANIFEST_INVALID, ...)`; per-column guard test |
</threat_model>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Phase 92 CR-01 — CsvImportController info-leak fix + regression fence</name>
  <files>src/main/java/org/ctc/dataimport/CsvImportController.java, src/test/java/org/ctc/dataimport/CsvImportControllerIT.java</files>
  <read_first>
    - src/main/java/org/ctc/dataimport/CsvImportController.java (lines 1-280; focus 150-170, 260-275)
    - .planning/phases/92-carry-forwards-cleanup/92-REVIEW.md (CR-01 section)
    - src/main/java/org/ctc/dataimport/DriverSheetImportController.java (reference: same getUserMessage()-only pattern from v1.12 UX-01)
    - src/test/java/org/ctc/dataimport/CsvImportControllerIT.java (current shape; extend rather than rewrite)
  </read_first>
  <behavior>
    - Test 1: when CsvImportController receives an IllegalArgumentException whose root cause is a Google-reachable GoogleApiException ("auth", "permission", "not-found", "transient"), the flash `errorMessage` equals the whitelisted user-message constant (e.g., `RaceImportErrorMessages.AUTH_USER_MESSAGE`), and does NOT contain the upstream `e.getMessage()` substring.
    - Test 2: same as Test 1 for the IllegalStateException arm at line 265.
    - Test 3: a non-Google IllegalArgumentException (caller-side validation) still surfaces the validation-message via the existing user-facing path — pinning that the fix does not over-correct.
  </behavior>
  <action>
    Step (a) — **Create the regression-fence test methods first** in `src/test/java/org/ctc/dataimport/CsvImportControllerIT.java` (extend the existing class; do not create a new file). Three Given-When-Then methods following the BDD naming pattern from `.planning/codebase/TESTING.md`: `givenGoogleApiAuthError_whenImport_thenFlashesWhitelistedMessageOnly`, `givenGoogleApiTransientError_whenImport_thenFlashesWhitelistedMessageOnly`, `givenLocalValidationError_whenImport_thenSurfacesValidationMessage`. Each assertion checks the flash `errorMessage` content as described in `<behavior>`.

    Step (b) — **RED step.** Run `./mvnw verify -Dit.test=CsvImportControllerIT -DfailIfNoTests=true`. MUST exit non-zero (the first two methods fail because the production code still echoes `e.getMessage()`).

    Step (c) — **Apply the production fix.** Replace the two `e.getMessage()` echoes at `CsvImportController:154-159` and `:265` with the whitelisted user-message accessor used by `DriverSheetImportController` (typed-catch + `getUserMessage()` on the categorized exception).

    Step (d) — **GREEN step.** Re-run `./mvnw verify -Dit.test=CsvImportControllerIT -DfailIfNoTests=true`. MUST exit 0.

    Step (e) — Commit fix + test as two atomic Conventional Commits (test first, then fix) OR one combined `fix+test` commit. Per CLAUDE.md "No Comment Pollution" — no marker comments in source or test.
  </action>
  <verify>
    <automated>./mvnw verify -Dit.test=CsvImportControllerIT -DfailIfNoTests=true</automated>
  </verify>
  <acceptance_criteria>
    - `./mvnw verify -Dit.test=CsvImportControllerIT -DfailIfNoTests=true` exits 0.
    - `grep -n "e.getMessage()" src/main/java/org/ctc/dataimport/CsvImportController.java` returns 0 lines within the typed-catch blocks at the two original sites (a residual occurrence outside these arms is acceptable only if it does not cross the Google-reachable boundary).
    - The three new test methods exist with Given-When-Then naming and `// given / // when / // then` body comments per CLAUDE.md "Test Naming".
    - No `// Phase 92`, `// CR-01`, `// info-leak fix` markers in any touched file (grep on touched files returns 0 lines for the oracle in Plan 102-03 acceptance).
  </acceptance_criteria>
  <done>The whitelisted-message-only invariant is pinned by automated tests; commit subjects: `test(92): regression-fence — CR-01 info-leak whitelisted-message-only` + `fix(92): drop e.getMessage() echoes on Google-reachable arms — CR-01`.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: Phase 94 CR-01 + 95 CR-01 — DiscordPostController.matchLabel bye-match defense + regression IT</name>
  <files>src/main/java/org/ctc/discord/web/DiscordPostController.java, src/test/java/org/ctc/discord/web/DiscordPostControllerIT.java</files>
  <read_first>
    - src/main/java/org/ctc/discord/web/DiscordPostController.java (lines 1-90; focus 55-75)
    - src/main/java/org/ctc/admin/controller/MatchController.java (line 107 — `awayTeam != null ? … : "Bye"` reference pattern)
    - .planning/phases/94-team-roles-match-channel-lifecycle/94-REVIEW.md (CR-01)
    - .planning/phases/95-match-channel-posts/95-REVIEW.md (CR-01)
    - src/test/java/org/ctc/discord/web/DiscordPostFilterControllerIT.java (analogous IT shape — Spring slice + MockMvc, NO @MockitoBean of DiscordPostService)
  </read_first>
  <behavior>
    - Test 1: given a matchday with one bye match (awayTeam = null), when `GET /admin/discord/posts` is requested, then response status is 200 AND the model attribute `matchLabels` contains an entry mapping the bye match's UUID to `"<homeShort> vs Bye"`.
    - Test 2: given a regular non-bye match, when the same GET is requested, then the label is `"<homeShort> vs <awayShort>"` (regression-fence for the non-bye path so the fix does not break the happy case).
  </behavior>
  <action>
    Step (a) — **Create the regression-fence test file first** at `src/test/java/org/ctc/discord/web/DiscordPostControllerIT.java` modelled after `DiscordPostFilterControllerIT`: Spring `@SpringBootTest` + `MockMvc`, class-level `@Tag("integration")` per `.planning/codebase/TESTING.md` "Test Categorization", seed a bye match via the existing `TestHelper`/`TestDataService` shape, assert response status 200 + the model attribute. Both test methods use Given-When-Then naming.

    Step (b) — **RED step.** Run `./mvnw verify -Dit.test=DiscordPostControllerIT -DfailIfNoTests=true`. MUST exit non-zero (Test 1 throws NPE inside `matchLabel` because `m.getAwayTeam()` is null for the bye match).

    Step (c) — **Apply the production fix.** Modify `DiscordPostController.matchLabel(Match m)` (line 69) to mirror `MatchController.detail:107`: replace `m.getAwayTeam().getShortName()` with `m.getAwayTeam() != null ? m.getAwayTeam().getShortName() : "Bye"`.

    Step (d) — **GREEN step.** Re-run `./mvnw verify -Dit.test=DiscordPostControllerIT -DfailIfNoTests=true`. MUST exit 0.

    Step (e) — Commit fix + test atomically (test first, then fix, OR combined). No marker comments.
  </action>
  <verify>
    <automated>./mvnw verify -Dit.test=DiscordPostControllerIT -DfailIfNoTests=true</automated>
  </verify>
  <acceptance_criteria>
    - `./mvnw verify -Dit.test=DiscordPostControllerIT -DfailIfNoTests=true` exits 0.
    - `grep -n "awayTeam.*!=.*null.*Bye\|awayTeam.*null.*\"Bye\"" src/main/java/org/ctc/discord/web/DiscordPostController.java` returns at least 1 line.
    - New test file `src/test/java/org/ctc/discord/web/DiscordPostControllerIT.java` carries `@Tag("integration")` exactly once (class-level).
    - No `// Phase 94`, `// CR-01`, `// bye-match defense` markers in either file.
  </acceptance_criteria>
  <done>Bye-match render invariant pinned; commits: `test(94): pin matchLabel bye-handling — CR-01 regression-fence` + `fix(94): defend matchLabel against bye matches — CR-01 / 95 CR-01`.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 3: Phase 94 CR-02 — DiscordChannelService webhook-fail cleanup-DELETE + WireMock IT</name>
  <files>src/main/java/org/ctc/discord/service/DiscordChannelService.java, src/test/java/org/ctc/discord/service/DiscordChannelServiceWebhookFailIT.java</files>
  <read_first>
    - src/main/java/org/ctc/discord/service/DiscordChannelService.java (lines 1-200; focus the audit-fail cleanup block which is the template — typically near lines 85-150)
    - src/test/java/org/ctc/discord/service/DiscordChannelServicePermissionAuditFailIT.java (reference: WireMock stub + cleanup-DELETE assertion)
    - src/test/java/org/ctc/discord/service/DiscordChannelServiceCleanupFailIT.java (reference: WireMock stub shape using `withQueryParam(...)` per CLAUDE.md "WireMock vs Real-API")
    - .planning/phases/94-team-roles-match-channel-lifecycle/94-REVIEW.md (CR-02)
  </read_first>
  <behavior>
    - Test 1: given a Discord guild + category configured in WireMock, when the channel-create POST returns 201 and the subsequent webhook-create POST returns 5xx, then `DiscordChannelService.createMatchChannel` raises a typed `DiscordTransientException` AND issues a DELETE to `/channels/{newChannelId}` (cleanup) before propagating.
    - Test 2 (existing audit-fail path): no regression — already exercised by `DiscordChannelServicePermissionAuditFailIT`; re-run as part of the targeted Failsafe command to confirm parity.
  </behavior>
  <action>
    Step (a) — **Create the regression-fence test file first** at `src/test/java/org/ctc/discord/service/DiscordChannelServiceWebhookFailIT.java` modelled on `DiscordChannelServicePermissionAuditFailIT`. WireMock stubs use `withQueryParam(...)` plus `urlPathMatching(...)` per CLAUDE.md "WireMock vs Real-API". Class-level `@Tag("integration")`. Method `givenWebhookCreateFails_whenCreateMatchChannel_thenCleanupDeleteIsIssued` with Given-When-Then body. Assert via `WireMock.verify(deleteRequestedFor(urlPathMatching("/channels/.+")))` or equivalent.

    Step (b) — **RED step.** Run `./mvnw verify -Dit.test=DiscordChannelServiceWebhookFailIT -DfailIfNoTests=true`. MUST exit non-zero (the DELETE verification fails because no cleanup is issued today).

    Step (c) — **Apply the production fix.** In `DiscordChannelService.createMatchChannel` (around lines 85-114 per REVIEW.md), wrap the webhook-create call in the same try-catch shape used by the audit-fail block; on failure, invoke the same cleanup method (e.g., `discordRestClient.deleteChannel(newChannelId)`) before rethrowing the typed exception.

    Step (d) — **GREEN step.** Re-run `./mvnw verify -Dit.test=DiscordChannelServiceWebhookFailIT,DiscordChannelServicePermissionAuditFailIT -DfailIfNoTests=true` (both tests, to confirm no regression on the audit-fail path). MUST exit 0.

    Step (e) — Commit fix + test atomically. No marker comments.
  </action>
  <verify>
    <automated>./mvnw verify -Dit.test=DiscordChannelServiceWebhookFailIT,DiscordChannelServicePermissionAuditFailIT -DfailIfNoTests=true</automated>
  </verify>
  <acceptance_criteria>
    - `./mvnw verify -Dit.test=DiscordChannelServiceWebhookFailIT,DiscordChannelServicePermissionAuditFailIT -DfailIfNoTests=true` exits 0.
    - The new IT carries `@Tag("integration")` exactly once.
    - The WireMock verification block asserts a DELETE call to `/channels/{id}` after the failed webhook POST (verify via `WireMock.verify(deleteRequestedFor(urlPathMatching("/channels/.+")))` or equivalent).
    - The fix uses the existing cleanup method — `grep -n "deleteChannel\|cleanup" src/main/java/org/ctc/discord/service/DiscordChannelService.java` shows the same symbol referenced from both audit-fail AND webhook-fail blocks.
    - No marker comments.
  </acceptance_criteria>
  <done>Webhook-fail cleanup invariant pinned; commits: `test(94): pin webhook-create-fail cleanup-DELETE — CR-02 regression-fence` + `fix(94): cleanup-DELETE on webhook-create failure — CR-02`.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 4: Phase 95 CR-02 — RaceService publishes MatchScheduleFieldsChangedEvent on dateTime change + listener IT</name>
  <files>src/main/java/org/ctc/domain/service/RaceService.java, src/test/java/org/ctc/discord/service/DiscordAutoPostListenerScheduleEditIT.java</files>
  <read_first>
    - src/main/java/org/ctc/domain/service/RaceService.java (focus `saveRace(...)` at line 147 — the existing dateTime-mutation path)
    - src/main/java/org/ctc/discord/event/MatchScheduleFieldsChangedEvent.java (exists; record(UUID matchId))
    - src/main/java/org/ctc/discord/service/DiscordAutoPostListener.java (lines 1-90; focus `onScheduleFieldsChanged` at line 52 and `onMatchPreviewFieldsChanged` at line 72 — the reference AFTER_COMMIT pattern)
    - src/test/java/org/ctc/discord/service/DiscordAutoPostListenerIT.java (analogous IT shape, real `@Transactional` proxy + WireMock — DO NOT @MockitoBean DiscordPostService)
    - .planning/phases/95-match-channel-posts/95-REVIEW.md (CR-02)
    - CLAUDE.md "Build & Test Discipline / WireMock vs Real-API"
  </read_first>
  <behavior>
    - Test 1: given an existing race with `dateTime = T0`, when `RaceService.saveRace(...)` is called with `newDateTime = T1` (T0 ≠ T1) inside a transaction, then exactly one `MatchScheduleFieldsChangedEvent` is observed AFTER_COMMIT AND the listener triggers the existing `postOrEdit` MATCHDAY_SCHEDULE re-post path (WireMock-backed `DiscordRestClient` records the call).
    - Test 2: given the same race, when `RaceService.saveRace(...)` is called with `newDateTime = T0` (unchanged), then NO event is published AND no Discord call is issued.
  </behavior>
  <action>
    Step (a) — **Create the regression-fence test file first** at `src/test/java/org/ctc/discord/service/DiscordAutoPostListenerScheduleEditIT.java`: full Spring context (`@SpringBootTest`), WireMock-backed `DiscordRestClient` (NOT `@MockitoBean DiscordPostService` — real `@Transactional` proxy must run per CLAUDE.md memory `feedback_wiremock_vs_real_api`), class-level `@Tag("integration")`. Two methods Given-When-Then: `givenDateTimeChanges_whenSaveRace_thenScheduleEventPublishedAfterCommit` and `givenDateTimeUnchanged_whenSaveRace_thenNoScheduleEventPublished`. Use `ApplicationEvents` (Spring 5+ test-event-recorder) or a captured `@EventListener` test-bean to assert the AFTER_COMMIT timing.

    Step (b) — **RED step.** Run `./mvnw verify -Dit.test=DiscordAutoPostListenerScheduleEditIT -DfailIfNoTests=true`. MUST exit non-zero (Test 1 fails because no event is published from `RaceService.saveRace` today).

    Step (c) — **Apply the production fix.** In `RaceService.saveRace`: inject `ApplicationEventPublisher`, compute `boolean dateTimeChanged = !Objects.equals(race.getDateTime(), newDateTime)` BEFORE the `race.setDateTime(newDate)` assignment, then after `raceRepository.save(race)` and existing `scoringService.aggregateMatchScores(...)` calls (per CLAUDE.md "Score Aggregation on Result Save" — score aggregation must still run), conditionally `applicationEventPublisher.publishEvent(new MatchScheduleFieldsChangedEvent(race.getMatch().getId()))`. Mirror the publish shape used by the existing preview-fields-changed publisher (grep `MatchPreviewFieldsChangedEvent.*publishEvent` for the reference).

    Step (d) — **GREEN step.** Re-run `./mvnw verify -Dit.test=DiscordAutoPostListenerScheduleEditIT -DfailIfNoTests=true`. MUST exit 0. Then re-run `./mvnw test -Dtest=RaceServiceTest -DfailIfNoTests=true` (must still pass; score-aggregation invariant unbroken).

    Step (e) — Commit fix + test atomically. No marker comments.
  </action>
  <verify>
    <automated>./mvnw verify -Dit.test=DiscordAutoPostListenerScheduleEditIT -DfailIfNoTests=true && ./mvnw test -Dtest=RaceServiceTest -DfailIfNoTests=true</automated>
  </verify>
  <acceptance_criteria>
    - Both commands exit 0.
    - `grep -n "MatchScheduleFieldsChangedEvent" src/main/java/org/ctc/domain/service/RaceService.java` returns at least 1 line.
    - The new IT does NOT contain `@MockitoBean.*DiscordPostService` (regex grep returns 0 lines).
    - The new IT uses WireMock stubs with `withQueryParam(...)` for any query-param-bearing endpoint per CLAUDE.md "WireMock vs Real-API".
    - `scoringService.aggregateMatchScores(...)` still runs for saves that included results, verified by `./mvnw test -Dtest=RaceServiceTest -DfailIfNoTests=true` exit 0.
    - No marker comments.
  </acceptance_criteria>
  <done>Schedule-edit auto-post hook invariant pinned via the real `@Transactional` + AFTER_COMMIT path; commits: `test(95): pin schedule-edit auto-post hook — CR-02 regression-fence` + `fix(95): publish MatchScheduleFieldsChangedEvent on dateTime change — CR-02`.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 5: Phase 98 BL-01 — DiscordPostService pre-flight excludes empty / all-BYE matchdays (two distinct predicate bugs) + guard IT</name>
  <files>src/main/java/org/ctc/discord/service/DiscordPostService.java, src/test/java/org/ctc/discord/service/DiscordPostServiceByeMatchdayGuardIT.java</files>
  <read_first>
    - src/main/java/org/ctc/discord/service/DiscordPostService.java (focus `canPostMatchdayPairings` AND `canPostMatchdaySchedule` — they have DIFFERENT predicate shapes and DIFFERENT bug surfaces; read both fully before editing)
    - .planning/phases/98-polish-e2e-docs-close/98-REVIEW.md (BL-01)
    - src/main/java/org/ctc/admin/TestDataService.java (helpers for seeding bye matches)
  </read_first>
  <behavior>
    - Test 1 (`givenEmptyMatchday_whenCanPostMatchdayPairings_thenFalse` + sibling for canPostMatchdaySchedule): given a matchday with zero matches, when each pre-flight is called, then the result is `false` (today's `allMatch(...)` returns vacuous true on empty stream).
    - Test 2 (`givenAllByeMatchday_whenCanPostMatchdayPairings_thenFalse` + sibling for canPostMatchdaySchedule): given a matchday with two matches, both bye (awayTeam = null), when each pre-flight is called, then the result is `false`. For `canPostMatchdayPairings`, today's predicate `.allMatch(m -> m.getHomeTeam() != null && m.getAwayTeam() != null)` already returns false for individual bye matches — but on an empty filter result the bug emerges; the explicit assertion still pins the invariant. For `canPostMatchdaySchedule`, today's predicate `.filter(m -> !m.isBye()).allMatch(m -> firstRaceTime(m).isPresent())` strips byes BEFORE allMatch, so an all-BYE matchday yields empty stream → vacuous true.
    - Test 3 (`givenMixedByeAndRegularMatchday_whenCanPostBoth_thenTrue`): given a matchday with one regular fully-settled match AND one bye match, when both pre-flights are called with all other gates green, then the result is `true` (regression-fence for the happy case so the guard does not over-reject).
  </behavior>
  <action>
    The two methods have distinct current predicates and require distinct, surgically-targeted edits. Read both methods in full BEFORE editing.

    Step (a) — **Create the regression-fence test file first** at `src/test/java/org/ctc/discord/service/DiscordPostServiceByeMatchdayGuardIT.java` (full Spring IT, class-level `@Tag("integration")`) with the three Given-When-Then test methods listed in `<behavior>`. Each test asserts BOTH `canPostMatchdayPairings` AND `canPostMatchdaySchedule` for that scenario.

    Step (b) — **RED step.** Run `./mvnw verify -Dit.test=DiscordPostServiceByeMatchdayGuardIT -DfailIfNoTests=true`. MUST exit non-zero. The empty-matchday assertion fails on BOTH methods (current `allMatch` returns vacuous true). The all-BYE assertion fails on `canPostMatchdaySchedule` (filter-then-allMatch on empty stream is vacuous true); for `canPostMatchdayPairings` it likely passes today because the per-match `awayTeam != null` predicate rejects byes — but the test still pins the invariant.

    Step (c) — **Apply the production fix, distinguishing the two methods.**

    For `canPostMatchdayPairings`: the current `.allMatch(m -> m.getHomeTeam() != null && m.getAwayTeam() != null)` correctly rejects bye matches per-match, but is vacuously true on empty matchdays. **Bug fix:** add an `!matchday.getMatches().isEmpty()` guard before the allMatch.

    For `canPostMatchdaySchedule`: the current `.filter(m -> !m.isBye()).allMatch(m -> firstRaceTime(m).isPresent())` strips byes before allMatch, producing vacuous true for empty AND all-BYE matchdays. **Bug fix:** require that at least ONE non-bye match exists. Concretely, compute `List<Match> nonByeMatches = matchday.getMatches().stream().filter(m -> !m.isBye()).toList()` and return `!nonByeMatches.isEmpty() && nonByeMatches.stream().allMatch(m -> firstRaceTime(m).isPresent())` (in combination with any other existing AND-clauses).

    Factor out shared logic if a clean private helper emerges (e.g., `hasAtLeastOneNonByeMatchSatisfying(Matchday, Predicate<Match>)`), but do not over-engineer — surgical edits per method are acceptable.

    Step (d) — **GREEN step.** Re-run `./mvnw verify -Dit.test=DiscordPostServiceByeMatchdayGuardIT -DfailIfNoTests=true`. All three test methods MUST pass for BOTH `canPostMatchdayPairings` AND `canPostMatchdaySchedule`. Also re-run `./mvnw test -Dtest=DiscordPostServiceMatchdayPairingsPreFlightTest,DiscordPostServiceMatchdaySchedulePreFlightTest -DfailIfNoTests=true` to confirm existing pre-flight tests stay green.

    Step (e) — Commit fix + test atomically. No marker comments.
  </action>
  <verify>
    <automated>./mvnw verify -Dit.test=DiscordPostServiceByeMatchdayGuardIT -DfailIfNoTests=true && ./mvnw test -Dtest=DiscordPostServiceMatchdayPairingsPreFlightTest,DiscordPostServiceMatchdaySchedulePreFlightTest -DfailIfNoTests=true</automated>
  </verify>
  <acceptance_criteria>
    - Both commands exit 0.
    - Both `canPostMatchdayPairings` and `canPostMatchdaySchedule` return `false` for empty AND all-BYE matchdays per the new test assertions.
    - The happy-case (mixed bye + non-bye) regression assertion is green on BOTH methods.
    - Existing `DiscordPostServiceMatchdayPairingsPreFlightTest` + `DiscordPostServiceMatchdaySchedulePreFlightTest` stay green (no regression on the per-match gates).
    - No marker comments in source or test.
  </acceptance_criteria>
  <done>Pre-flight gating invariant pinned for both predicate-shapes; commits: `test(98): pin matchday pre-flight bye-guard on pairings + schedule — BL-01 regression-fence` + `fix(98): reject empty / all-BYE matchdays in pairings + schedule pre-flight — BL-01`.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 6: Phase 98 BL-02 — TestDataService.seedFullMatchdayLifecycle uses distinct shortName T-ALC + isolation test</name>
  <files>src/main/java/org/ctc/admin/TestDataService.java, src/test/java/org/ctc/admin/TestDataServiceLifecycleSeedTest.java</files>
  <read_first>
    - src/main/java/org/ctc/admin/TestDataService.java (focus `seedFullMatchdayLifecycle` — current shortName collides with dev-seed `T-ALF`)
    - .planning/phases/98-polish-e2e-docs-close/98-REVIEW.md (BL-02)
    - src/main/java/org/ctc/domain/repository/TeamRepository.java (for the `findByShortName` signature returning `Optional<Team>` or single-row)
  </read_first>
  <behavior>
    - Test 1: when `seedFullMatchdayLifecycle()` runs against a fixture that already contains the dev-seed `T-ALF` team, then `teamRepository.findByShortName("T-ALC")` returns a single row AND `teamRepository.findByShortName("T-ALF")` still returns the existing dev-seed (no `IncorrectResultSizeDataAccessException`).
    - Test 2: when `seedFullMatchdayLifecycle()` runs from an empty DB, then it still creates the `T-ALC` team and a valid full-lifecycle fixture.
  </behavior>
  <action>
    Step (a) — **Create the regression-fence test file first** at `src/test/java/org/ctc/admin/TestDataServiceLifecycleSeedTest.java` (`@SpringBootTest` + class-level `@Tag("integration")`) with the two Given-When-Then test methods.

    Step (b) — **RED step.** Run `./mvnw verify -Dit.test=TestDataServiceLifecycleSeedTest -DfailIfNoTests=true`. MUST exit non-zero (Test 1 fails because `findByShortName("T-ALF")` throws `IncorrectResultSizeDataAccessException` after lifecycle-seed collides with dev-seed).

    Step (c) — **Apply the production fix.** In `TestDataService.seedFullMatchdayLifecycle`, replace every `T-ALF` literal usage with `T-ALC` (and any human-readable name suffix like `Test_Alpha_Lifecycle_1` if the regular seed uses `Test_Alpha_1`). Per CLAUDE.md "Architectural Principles / Isolate Test Data Completely" — separate test entities with a unique prefix; the new short-name MUST NOT collide with any existing dev-seed.

    Step (d) — **GREEN step.** Re-run `./mvnw verify -Dit.test=TestDataServiceLifecycleSeedTest -DfailIfNoTests=true`. MUST exit 0.

    Step (e) — Commit fix + test atomically. No marker comments.
  </action>
  <verify>
    <automated>./mvnw verify -Dit.test=TestDataServiceLifecycleSeedTest -DfailIfNoTests=true</automated>
  </verify>
  <acceptance_criteria>
    - `./mvnw verify -Dit.test=TestDataServiceLifecycleSeedTest -DfailIfNoTests=true` exits 0.
    - `grep -n '"T-ALF"' src/main/java/org/ctc/admin/TestDataService.java` returns 0 lines inside `seedFullMatchdayLifecycle` (the dev-seed `T-ALF` elsewhere in the file may still exist if that is the regular dev-seed name).
    - `grep -n '"T-ALC"' src/main/java/org/ctc/admin/TestDataService.java` returns at least 1 line inside `seedFullMatchdayLifecycle`.
    - No marker comments.
  </acceptance_criteria>
  <done>Lifecycle-seed shortName isolation pinned; commits: `test(98): pin lifecycle-seed shortName isolation — BL-02 regression-fence` + `fix(98): isolate lifecycle-seed under T-ALC shortName — BL-02`.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 7: Phase 101 CR-01 — BackupLenientV1AcceptanceIT uses plural JPA table names</name>
  <files>src/test/java/org/ctc/backup/service/BackupLenientV1AcceptanceIT.java</files>
  <read_first>
    - src/test/java/org/ctc/backup/service/BackupLenientV1AcceptanceIT.java (focus `V1_TABLES_24` constant at line 52 and the synthetic-ZIP builder)
    - src/main/java/org/ctc/backup/schema/BackupSchema.java (focus `getExportOrder()` — the authoritative entity-to-table mapping; the synthetic V1 list must use slugs that match the entity's @Table name)
    - src/main/java/org/ctc/backup/service/BackupArchiveService.java (focus line 142 — "data/<slug>.json per entity in EXPORT_ORDER")
    - .planning/phases/101-backup-restore-covers-discord-schema-v8-v15/101-REVIEW.md (CR-01)
  </read_first>
  <behavior>
    - Test 1 (existing `BackupLenientV1AcceptanceIT.givenV2ManifestZipBuiltLikeV1_*` family): after the fix, the synthetic V1 ZIP uses plural JPA table names — including `race_scorings` AND `match_scorings` — so the lenient-import path actually ingests data and the Discord tables stay empty *because* the v1 archive did not contain them (NOT because the lookup silently failed).
    - Test 2 (new assertion in the same IT class): the existing test methods gain a row-count assertion proving the v1 archive was *read* (e.g., `assertThat(seasonRepository.count()).isGreaterThan(0)`) AND a parity assertion `discordGlobalConfigRepository.count() == 0` confirming the Discord tables stayed empty due to absence, not silent failure.
  </behavior>
  <action>
    Step (a) — **Add the new row-count + Discord-emptiness assertions to the existing test methods FIRST**, before reshaping the table-name list. This makes the lenient-gate proof non-vacuous and is the RED step's failure surface.

    Step (b) — **RED step.** Run `./mvnw verify -Dit.test=BackupLenientV1AcceptanceIT -DfailIfNoTests=true`. The new row-count assertions MUST fail because the synthetic V1 ZIP currently uses the wrong (singular) table names — no data is actually ingested. MUST exit non-zero.

    Step (c) — **Apply the production fix (test-source change).** Reshape the `V1_TABLES_24` constant to use plural JPA table names that align with the entity `@Table(name=…)` annotations. The source of truth is `BackupSchema.getExportOrder()`: each `EntityRef` carries the entity class and its slug; the test table-name must equal the entity's `@Table` name. Concretely: `race_scoring` → `race_scorings`, `match_scoring` → `match_scorings` (and audit other entries against `getExportOrder()` in the same edit).

    Step (d) — **GREEN step.** Re-run `./mvnw verify -Dit.test=BackupLenientV1AcceptanceIT -DfailIfNoTests=true`. MUST exit 0.

    Step (e) — Commit as a single test-source commit (no production source touched). No marker comments.
  </action>
  <verify>
    <automated>./mvnw verify -Dit.test=BackupLenientV1AcceptanceIT -DfailIfNoTests=true</automated>
  </verify>
  <acceptance_criteria>
    - `./mvnw verify -Dit.test=BackupLenientV1AcceptanceIT -DfailIfNoTests=true` exits 0.
    - `grep -n '"race_scorings"\|"match_scorings"' src/test/java/org/ctc/backup/service/BackupLenientV1AcceptanceIT.java` returns at least 2 lines.
    - At least one test method contains an explicit assertion that a row-count grew (proves the v1 archive was read).
    - No marker comments in the file.
  </acceptance_criteria>
  <done>Lenient-import proof non-vacuous; commit: `fix(101): align V1_TABLES_24 to plural JPA table names + non-vacuous gate proof — CR-01`.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 8: Phase 101 CR-02 — Discord restorers guard 6 NOT-NULL columns + per-column guard tests</name>
  <files>src/main/java/org/ctc/backup/restore/entity/DiscordGlobalConfigRestorer.java, src/main/java/org/ctc/backup/restore/entity/DiscordPostRestorer.java, src/test/java/org/ctc/backup/restore/entity/DiscordGlobalConfigRestorerGuardTest.java</files>
  <read_first>
    - src/main/java/org/ctc/backup/restore/entity/DiscordGlobalConfigRestorer.java (focus the `row.get(X).asText()` chains)
    - src/main/java/org/ctc/backup/restore/entity/DiscordPostRestorer.java (any sibling NOT-NULL columns from V12: channel_id, message_id, post_type — apply the same guard pattern if applicable)
    - src/main/java/org/ctc/backup/exception/BackupArchiveException.java (constructor — `MANIFEST_INVALID` error-code)
    - src/main/java/org/ctc/backup/service/BackupImportService.java (reference: existing `BackupArchiveException(MANIFEST_INVALID, ...)` raise pattern)
    - .planning/phases/101-backup-restore-covers-discord-schema-v8-v15/101-REVIEW.md (CR-02)
  </read_first>
  <behavior>
    - Per-column guard tests in `DiscordGlobalConfigRestorerGuardTest` — six methods, one per NOT-NULL column (`guildId`, `announcementWebhookUrl`, `raceResultsForumChannelId`, `standingsForumChannelId`, `vsEmojiName`, `currentMatchCategoryId`):
      - Given a JSON row that omits the column, when the restorer is invoked, then a `BackupArchiveException` is raised with `errorCode == MANIFEST_INVALID` and the message names the missing column.
    - Test 7: given a complete JSON row with all 6 columns present, when the restorer is invoked, then it persists the entity without throwing (happy-case regression-fence).
    - For `DiscordPostRestorer` NOT-NULL columns (if any present in the V12 schema), the same guard pattern applies — those guards land as part of this task but may not need separate test methods if the analogous code path is already exercised by an existing IT; the executor judges per-file based on the read-first pass.
  </behavior>
  <action>
    Step (a) — **Create the regression-fence test file first** at `src/test/java/org/ctc/backup/restore/entity/DiscordGlobalConfigRestorerGuardTest.java`. Unit test (NOT `@SpringBootTest`; construct the restorer directly with stub dependencies); no `@Tag` needed unless Spring context is required — default is plain unit test. Seven Given-When-Then test methods (six per-column guard + one happy-case).

    Step (b) — **RED step.** Run `./mvnw test -Dtest=DiscordGlobalConfigRestorerGuardTest -DfailIfNoTests=true`. The six per-column tests MUST fail because the restorer currently throws NPE (not `BackupArchiveException`) on missing columns. MUST exit non-zero.

    Step (c) — **Apply the production fix.** In `DiscordGlobalConfigRestorer`, replace each unguarded `row.get(X).asText()` for the 6 NOT-NULL columns with a guard: if `row.get(X) == null || row.get(X).isNull()`, raise `new BackupArchiveException(MANIFEST_INVALID, "missing required column '" + X + "' in discord_global_config row")`. Same pattern in `DiscordPostRestorer` for any NOT-NULL column present in V12.

    Step (d) — **GREEN step.** Re-run `./mvnw test -Dtest=DiscordGlobalConfigRestorerGuardTest -DfailIfNoTests=true`. MUST exit 0.

    Step (e) — Commit fix + test atomically. No marker comments.
  </action>
  <verify>
    <automated>./mvnw test -Dtest=DiscordGlobalConfigRestorerGuardTest -DfailIfNoTests=true</automated>
  </verify>
  <acceptance_criteria>
    - `./mvnw test -Dtest=DiscordGlobalConfigRestorerGuardTest -DfailIfNoTests=true` exits 0.
    - `grep -n "MANIFEST_INVALID" src/main/java/org/ctc/backup/restore/entity/DiscordGlobalConfigRestorer.java` returns at least 6 lines (one per guarded column).
    - The new test class contains 7 test methods (6 per-column guard + 1 happy-case).
    - No marker comments in any touched file.
  </acceptance_criteria>
  <done>Restorer NOT-NULL guards pinned; commits: `test(101): pin Discord restorer NOT-NULL guards — CR-02 regression-fence` + `fix(101): guard 6 NOT-NULL columns in DiscordGlobalConfigRestorer + DiscordPostRestorer — CR-02`.</done>
</task>

</tasks>

<verification>
After all 8 tasks land:
1. Run each targeted test command listed in the `<verify>` blocks above (one final pass per task — no `clean verify -Pe2e` here per CONTEXT D-11; that's Plan 102-04). Every command MUST use `-DfailIfNoTests=true` (or omit the flag, equivalent default).
2. `grep -rEn "^\s*(//|--|#)\s*(Phase |Plan |D-[0-9]|UAT-|WR-|CR-|IN-|BL-|Wave )" src/main src/test src/main/resources/db/migration` MUST NOT report any new lines on the files this plan touched (Plan 102-03 closes the pre-existing pollution; this plan must not add to it).
3. Orchestrator-level (NOT a plan task per CONTEXT D-04): spawn `/gsd-code-review 102 --files=<comma-separated files_modified>` on the Plan 102-01 diff; result must be `clean` (zero critical + zero warning).
4. Only then commit the plan SUMMARY.md.
</verification>

<success_criteria>
- All 9 critical/blocker findings closed by 8 tasks (Phase 94 CR-01 + 95 CR-01 fold to Task 2).
- 8 new regression-fence test classes (or extended ITs) exist and pass under `-DfailIfNoTests=true`.
- `grep -n "e.getMessage()" src/main/java/org/ctc/dataimport/CsvImportController.java` returns 0 lines within the Google-reachable arms.
- `MatchScheduleFieldsChangedEvent` is published from `RaceService.saveRace` on dateTime-change AND no event on dateTime-unchanged (proven by the listener IT).
- `BackupLenientV1AcceptanceIT` uses plural JPA table names AND its lenient-gate proof is non-vacuous.
- `BackupArchiveException(MANIFEST_INVALID, ...)` is raised by the restorers for all 6 NOT-NULL columns of `discord_global_config`.
- `canPostMatchdayPairings` AND `canPostMatchdaySchedule` both return false on empty AND all-BYE matchdays; both return true on mixed bye + non-bye matchdays.
- ZERO new comment-pollution markers added in any touched file.
- Per-plan `/gsd-code-review 102 --files=...` returns clean before SUMMARY commit.
</success_criteria>

<output>
Create `.planning/phases/102-code-review-fixes/102-01-SUMMARY.md` capturing per-task: finding-ID, fix-commit SHA, test-commit SHA (if separate), targeted-test exit code. Note the per-plan review result as "clean" once the orchestrator confirms.
</output>
</content>
</invoke>