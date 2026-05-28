---
phase: 102-code-review-fixes
plan: 02
type: execute
wave: 2
depends_on:
  - 102-01
files_modified:
  - src/main/java/org/ctc/admin/controller/MatchController.java
  - src/main/java/org/ctc/admin/controller/SeasonController.java
  - src/main/java/org/ctc/admin/controller/MatchdayController.java
  - src/main/java/org/ctc/domain/service/MatchService.java
  - src/main/java/org/ctc/admin/service/DiscordSeasonViewService.java
  - src/main/java/org/ctc/domain/service/StandingsService.java
  - src/main/java/org/ctc/discord/dto/DiscordPostRef.java
  - src/main/java/org/ctc/discord/service/DiscordPostService.java
  - src/main/java/org/ctc/discord/service/DiscordChannelService.java
  - src/main/java/org/ctc/discord/cache/DiscordRoleCache.java
  - src/main/java/org/ctc/admin/dto/DiscordConfigForm.java
  - src/main/java/org/ctc/admin/dto/TeamForm.java
  - src/main/java/org/ctc/admin/dto/SeasonForm.java
  - src/main/java/org/ctc/admin/dto/MatchdayPairingsForm.java
  - src/main/java/org/ctc/domain/service/RaceService.java
  - src/main/java/org/ctc/discord/service/ProvisionalScoresGraphicService.java
  - src/main/java/org/ctc/discord/service/DiscordForumService.java
  - src/main/java/org/ctc/admin/service/MatchdayPairingsGraphicService.java
  - src/main/java/org/ctc/discord/DiscordRestClient.java
  - src/main/java/org/ctc/discord/DiscordRateLimitInterceptor.java
  - src/main/java/org/ctc/backup/service/BackupExportService.java
  - src/main/java/org/ctc/backup/service/BackupImportService.java
  - src/main/java/org/ctc/backup/schema/BackupSchema.java
  - src/main/java/org/ctc/backup/mixin/DiscordGlobalConfigMixIn.java
  - src/main/java/org/ctc/backup/mixin/DiscordPostMixIn.java
  - src/test/java/org/ctc/admin/controller/MatchControllerDetailViewModelTest.java
  - src/test/java/org/ctc/admin/service/DiscordSeasonViewServiceTest.java
  - src/test/java/org/ctc/domain/service/StandingsServiceStalenessSnapshotTest.java
  - src/test/java/org/ctc/backup/service/BackupRoundTripIT.java
  - src/test/java/org/ctc/backup/service/BackupImportSchemaMismatchIT.java
  - src/test/java/org/ctc/discord/service/DiscordChannelArchiveServiceWireMockIT.java
autonomous: true
requirements:
  - REVIEW-FIX-02

must_haves:
  truths:
    - "MatchController.detail is a thin dispatch (under 15 lines of model-population) delegating to MatchService.buildMatchDetailModel(matchId)"
    - "SeasonController.populateDiscordIntegrationModel is replaced by a NEW service DiscordSeasonViewService.buildDiscordIntegrationModel(seasonId); controller stays thin"
    - "MatchdayController staleness logic + its sole-use seasonTeamRepository field are extracted to StandingsService"
    - "DiscordPostRef.SeasonRef.applyTo writes both seasonId AND phaseId (95 WR-07 closure)"
    - "Markdown-link builders escape ) in URLs (95 WR-04 closure)"
    - "populateMatchdayDiscordModel resolves announcement-channel ID exactly once per request (98 WR-02 closure)"
    - "DiscordRoleCache.refresh never opens a no-roles window (94 WR-03 closure)"
    - "moveToArchive surfaces 'no category selected' as a distinct error category (94 WR-01 closure)"
    - "matchHasCompleteSettings rejects unfilled RaceSettings rows (94 WR-02 closure)"
    - "Webhook-URL form regex matches DiscordPostService.parseWebhookUrl regex (94 WR-06 / 98 WR-03 closure)"
    - "Empty-string discordRoleId fails validation (94 WR-05 closure)"
    - "MatchdayPairingsForm.matchdayPairingsTemplate has an @Size cap (98 WR-01 closure)"
    - "BackupExportService + BackupImportService no longer reference 24 entities; 26-entity reality is consistent (101 WR-01 + WR-02 closure)"
    - "Duplicate test helpers in BackupRoundTripIT + BackupImportSchemaMismatchIT consolidated (101 WR-04 + WR-05 closure)"
    - "Existing tests for all touched classes remain green; new boundary tests cover the 3 controller-thin extracts"
  artifacts:
    - path: "src/main/java/org/ctc/domain/service/MatchService.java"
      provides: "buildMatchDetailModel(matchId) — extracted from MatchController.detail"
      contains: "buildMatchDetailModel"
    - path: "src/main/java/org/ctc/admin/service/DiscordSeasonViewService.java"
      provides: "buildDiscordIntegrationModel(seasonId) — NEW admin-layer view-model assembly service extracted from SeasonController.populateDiscordIntegrationModel"
      contains: "buildDiscordIntegrationModel"
    - path: "src/main/java/org/ctc/domain/service/StandingsService.java"
      provides: "snapshotMatchdayStaleness(...) — extracted from MatchdayController helpers"
      contains: "snapshotMatchdayStaleness"
    - path: "src/test/java/org/ctc/admin/controller/MatchControllerDetailViewModelTest.java"
      provides: "Boundary test for MatchService.buildMatchDetailModel"
      contains: "buildMatchDetailModel"
    - path: "src/test/java/org/ctc/admin/service/DiscordSeasonViewServiceTest.java"
      provides: "Boundary test for DiscordSeasonViewService.buildDiscordIntegrationModel"
      contains: "buildDiscordIntegrationModel"
    - path: "src/test/java/org/ctc/domain/service/StandingsServiceStalenessSnapshotTest.java"
      provides: "Boundary test for StandingsService.snapshotMatchdayStaleness"
      contains: "snapshotMatchdayStaleness"
  key_links:
    - from: "src/main/java/org/ctc/admin/controller/MatchController.java#detail"
      to: "src/main/java/org/ctc/domain/service/MatchService.java#buildMatchDetailModel"
      via: "5-line dispatch returning a view-model copied into Model"
      pattern: "matchService\\.buildMatchDetailModel"
    - from: "src/main/java/org/ctc/admin/controller/SeasonController.java"
      to: "src/main/java/org/ctc/admin/service/DiscordSeasonViewService.java#buildDiscordIntegrationModel"
      via: "admin-layer view-model assembly via @RequiredArgsConstructor @Service collaborator"
      pattern: "discordSeasonViewService\\.buildDiscordIntegrationModel"
    - from: "src/main/java/org/ctc/admin/controller/MatchdayController.java"
      to: "src/main/java/org/ctc/domain/service/StandingsService.java#snapshotMatchdayStaleness"
      via: "controller no longer holds seasonTeamRepository field; staleness lives in the service"
      pattern: "snapshotMatchdayStaleness"
---

<objective>
Close all 58 warning findings across Phase 92-99 + 101 (92 WR-01..06, 93 WR-01..06, 94 WR-01..06, 95 WR-01..08, 96 WR-01..08, 97 WR-01..07, 98 WR-01..06, 99 WR-01..02, 101 WR-01..07), including the 3 controller-thin refactor extracts per CONTEXT D-03.

**Plan budget override:** This plan contains 11 tasks (3 refactor-extracts with boundary tests + 8 grouped point-fix tasks). CONTEXT D-01 LOCKS the by-severity split; by-domain splitting would conflict with that decision. Each task is ≤30% context (point-fix groups touch 2-5 files; refactors are mechanical move-method operations). Plan budget fits ~50%.

Purpose: Tighten architecture (controllers thin, asymmetric setters symmetric, regex parity), close subtle correctness issues (no-roles window, empty-string validation, vacuous predicates, race-filename instability, transactional IT mismatch), and consolidate duplicated test helpers. None of these are ship-blockers individually, but the milestone-close bar (D-04 close-loop returns clean zero-warning) requires they ALL close.

Output: 11 atomic commits, mix of `refactor(...)` for extracts and `fix(...)` / `chore(...)` for point-fixes. Per CONTEXT D-04 the orchestrator runs `/gsd-code-review 102 --files=<files_modified>` after the plan goes green, before the SUMMARY commit.

**Note on test-class naming:** Every `<verify>` block in this plan references EXISTING test classes confirmed to live in `src/test/java`. Where a NEW test class is created as part of a task `<action>`, that creation is explicit in the action step and the verify command uses `-DfailIfNoTests=true` to fail loudly if the class is missing.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@.planning/phases/102-code-review-fixes/102-CONTEXT.md
@.planning/phases/102-code-review-fixes/102-01-critical-fixes-PLAN.md
@.planning/phases/92-carry-forwards-cleanup/92-REVIEW.md
@.planning/phases/93-discord-foundation/93-REVIEW.md
@.planning/phases/94-team-roles-match-channel-lifecycle/94-REVIEW.md
@.planning/phases/95-match-channel-posts/95-REVIEW.md
@.planning/phases/96-provisional-graphic-forum-threads/96-REVIEW.md
@.planning/phases/97-matchday-level-posts/97-REVIEW.md
@.planning/phases/98-polish-e2e-docs-close/98-REVIEW.md
@.planning/phases/99-pre-merge-audit-polish-requirements-flyway-prose-roadmap-ref/99-REVIEW.md
@.planning/phases/101-backup-restore-covers-discord-schema-v8-v15/101-REVIEW.md
@CLAUDE.md
@.planning/codebase/CONVENTIONS.md

<execution_notes>
Inline-sequential on `gsd/v1.13-discord-integration` per CONTEXT D-05. Atomic commit per task per CONTEXT D-07. Per CLAUDE.md "Grep All Usages Before Refactor" — every refactor task includes a grep audit BEFORE editing the source file. Per CLAUDE.md "No Comment Pollution" — no marker comments in source or test code.

Per the 3 controller-thin extracts: re-use the Phase 33 v1.5 "Controller Cleanup" precedent — controller delegates to a service method returning either a view-model record OR a `Map<String, Object>`. Executor picks whichever matches the existing controller-thin pattern in the codebase.

**Discord-season view-model service placement (Task 2):** A new class `org.ctc.admin.service.DiscordSeasonViewService` is created (NOT extension of the existing `org.ctc.domain.service.SeasonManagementService`). Reasoning per planner-revision: (a) the existing `domain.service.SeasonManagementService` handles core season CRUD and references the dead `discordRaceResultsThreadId` / `discordStandingsThreadId` fields that Plan 102-03 Task 4 will REMOVE — extending it now would mix unrelated concerns; (b) Discord-integration model assembly is an admin-layer view-model concern (the model is rendered by `season-edit.html`), not a domain-service concern; (c) the new class avoids the package collision with the existing `domain.service.SeasonManagementService` and keeps the boundary clean per CLAUDE.md naming pattern (admin-layer view-model assembly → `admin.service`).

Per CONTEXT D-04 — read-only `/gsd-code-review 102 --files=<list>` runs once after this plan's tests are green, before SUMMARY commits. Plan does NOT spawn this; the orchestrator does.

**Per-task finding enumeration is the executor's responsibility.** Tasks 7 and 10 are bulk sweeps over Phase 96/97 and 92/93 warnings; executor reads each REVIEW.md `### WR-NN` heading and applies the listed fix. The plan does NOT enumerate every WR finding inline — the REVIEW.md files ARE the enumeration source, kept intact per CONTEXT D-13.
</execution_notes>
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Refactor MatchController.detail → MatchService.buildMatchDetailModel + boundary test (WR-thin-1)</name>
  <files>src/main/java/org/ctc/admin/controller/MatchController.java, src/main/java/org/ctc/domain/service/MatchService.java, src/test/java/org/ctc/admin/controller/MatchControllerDetailViewModelTest.java</files>
  <read_first>
    - src/main/java/org/ctc/admin/controller/MatchController.java (focus `detail(...)` starting line 104; 40+ lines of `model.addAttribute(...)` calls)
    - src/main/java/org/ctc/domain/service/MatchService.java (check existing method style — record returns vs Map returns)
    - .planning/phases/95-match-channel-posts/95-REVIEW.md (find "MatchController.detail mixes Thymeleaf-OSIV" — the WR identifier)
    - src/test/java/org/ctc/admin/controller/MatchControllerTest.java (must stay green)
    - CLAUDE.md "Architectural Principles / Keep Controllers Thin"
  </read_first>
  <behavior>
    - Test 1 (boundary): given a valid matchId with a fully-populated match, when `MatchService.buildMatchDetailModel(matchId)` is invoked, then the returned view-model contains every attribute key currently set by `MatchController.detail` (match, archiveCategories, defaultSelectionId, pageTitle, teamCardsPost, settingsPost, lineupsPost, provisionalPost, matchHasCompleteSettings, matchHasCompleteLineups, matchHasProvisionalData, matchResultsPost, matchResultsStale, schedulePost, matchCanRenderResults, scheduleVisible, plus any others enumerated in the grep audit).
    - Existing `MatchControllerTest` + any `MatchControllerIT` remain green — no behavioural change visible at the HTTP boundary.
  </behavior>
  <action>
    Audit: `grep -rn "model\\.addAttribute" src/main/java/org/ctc/admin/controller/MatchController.java` to enumerate ALL keys; the new service method MUST return ALL of them.

    Refactor: extract the model-population block from `MatchController.detail` into `MatchService.buildMatchDetailModel(UUID matchId)`. Choose return shape based on existing code: either a new `MatchDetailViewModel` record (if < 15 fields) OR `Map<String, Object>` (Phase 33 precedent). Move all repository/service dependencies needed by that block from controller to `MatchService`. Controller becomes a 5-line dispatch: fetch view-model, copy into `Model` (via `model.addAllAttributes(...)` if Map-shape), return view name.

    Create `MatchControllerDetailViewModelTest` as a unit test (mock collaborators; NOT `@SpringBootTest`). One Given-When-Then method asserting every key from the original `model.addAttribute(...)` list is present in the view-model. Per CLAUDE.md "Controller-Test Fixture", use `TestHelper.createFullSeasonFixture()` if a full fixture is needed.

    Verify existing tests stay green by running them in the same Surefire invocation.
  </action>
  <verify>
    <automated>./mvnw test -Dtest=MatchControllerDetailViewModelTest,MatchControllerTest -DfailIfNoTests=true</automated>
  </verify>
  <acceptance_criteria>
    - `./mvnw test -Dtest=MatchControllerDetailViewModelTest,MatchControllerTest -DfailIfNoTests=true` exits 0.
    - The refactored `MatchController.detail` method body is ≤ 15 lines (excluding signature + braces + return).
    - Every key from the pre-refactor `model.addAttribute(...)` list is asserted present by the new test.
    - No marker comments in any touched file.
  </acceptance_criteria>
  <done>Controller-thin extract for MatchController.detail closed; commit: `refactor(102-02): extract MatchController.detail model-population to MatchService.buildMatchDetailModel — WR-thin-1`.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: Extract SeasonController.populateDiscordIntegrationModel → NEW DiscordSeasonViewService + boundary test (WR-thin-2)</name>
  <files>src/main/java/org/ctc/admin/controller/SeasonController.java, src/main/java/org/ctc/admin/service/DiscordSeasonViewService.java, src/test/java/org/ctc/admin/service/DiscordSeasonViewServiceTest.java</files>
  <read_first>
    - src/main/java/org/ctc/admin/controller/SeasonController.java (focus `populateDiscordIntegrationModel` starting line 120 — 45-line helper)
    - src/main/java/org/ctc/domain/service/SeasonManagementService.java (note: this is the EXISTING domain-layer service which we do NOT extend — see execution_notes)
    - src/test/java/org/ctc/admin/controller/SeasonControllerTest.java (must stay green)
    - Search REVIEW.md files for the WR identifier (Phase 96 or 97 carries the warning — `grep -rn populateDiscordIntegrationModel .planning/phases/96* .planning/phases/97*`)
    - CLAUDE.md "Architectural Principles / Keep Controllers Thin" + "Conventions / Naming Patterns" (admin-layer view-model assembly → `admin.service`)
  </read_first>
  <behavior>
    - Test 1 (boundary): given a seasonId with Discord integration configured, when `DiscordSeasonViewService.buildDiscordIntegrationModel(seasonId)` is called, then the returned view-model contains every attribute set by the original `populateDiscordIntegrationModel`.
    - Test 2 (boundary): given a seasonId without Discord integration, when the method is called, then sensible defaults are returned (announcement-webhook null, role-mappings empty, etc.).
    - Existing `SeasonController` tests stay green.
  </behavior>
  <action>
    Audit: `grep -rn "populateDiscordIntegrationModel" src/main/java` — only `SeasonController` should call it; the extract is private-controller-helper → new-service-public-method.

    **Create a NEW class** `src/main/java/org/ctc/admin/service/DiscordSeasonViewService.java` with method `buildDiscordIntegrationModel(UUID seasonId)`. Class annotations: `@Service`, `@RequiredArgsConstructor`, `@Slf4j` (alphabetical per CLAUDE.md). Return shape: a `Map<String, Object>` (Phase 33 precedent — simpler than a record for 8+ heterogeneous attributes), OR a `DiscordSeasonViewModel` record if the executor sees a cleaner record-based contract from the controller's current model attributes (no more than 12 fields). Either choice is documented in the Plan 102-02 SUMMARY.md.

    Do NOT extend `org.ctc.domain.service.SeasonManagementService` — it is a separate domain-CRUD service whose `discordRaceResultsThreadId` / `discordStandingsThreadId` fields will be removed in Plan 102-03 Task 4; coupling this view-model extraction to that class now would mix concerns. The new admin-layer class avoids the name collision and aligns with CLAUDE.md naming patterns.

    Refactor `SeasonController`: inject `DiscordSeasonViewService` via constructor (`@RequiredArgsConstructor` adds the final field automatically). Move the 45-line `populateDiscordIntegrationModel` block, any private helpers it calls, and any field dependencies (repositories pulled in only for this block) into the new service. The controller's `populateDiscordIntegrationModel` becomes either deleted (replaced by inline `model.addAllAttributes(discordSeasonViewService.buildDiscordIntegrationModel(seasonId))`) OR a thin 3-line dispatcher. Delete any repository fields from the controller that are now only used inside the extracted method (CLAUDE.md "Grep All Usages Before Refactor": confirm each candidate field has zero remaining controller usages).

    Create boundary test at `src/test/java/org/ctc/admin/service/DiscordSeasonViewServiceTest.java` (unit test, mock collaborators; NOT `@SpringBootTest`) with both Given-When-Then methods.
  </action>
  <verify>
    <automated>./mvnw test -Dtest=DiscordSeasonViewServiceTest,SeasonControllerTest -DfailIfNoTests=true</automated>
  </verify>
  <acceptance_criteria>
    - `./mvnw test -Dtest=DiscordSeasonViewServiceTest,SeasonControllerTest -DfailIfNoTests=true` exits 0.
    - File `src/main/java/org/ctc/admin/service/DiscordSeasonViewService.java` exists and is `@Service`-annotated.
    - The controller no longer holds the 45-line private helper (the method is either deleted or shrunk to ≤3 lines).
    - Boundary test asserts both Discord-configured AND not-configured paths.
    - `grep -rn "populateDiscordIntegrationModel" src/main/java` shows the symbol only in the SeasonController call site (if any thin dispatcher remains) — never inside the new service.
    - No marker comments.
  </acceptance_criteria>
  <done>Controller-thin extract #2 closed; commit: `refactor(102-02): extract populateDiscordIntegrationModel to new DiscordSeasonViewService — WR-thin-2`.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 3: Refactor MatchdayController staleness helpers + drop seasonTeamRepository field → StandingsService + boundary test (WR-thin-3 / 95 WR-05)</name>
  <files>src/main/java/org/ctc/admin/controller/MatchdayController.java, src/main/java/org/ctc/domain/service/StandingsService.java, src/test/java/org/ctc/domain/service/StandingsServiceStalenessSnapshotTest.java</files>
  <read_first>
    - src/main/java/org/ctc/admin/controller/MatchdayController.java (focus `seasonTeamRepository` field at line 61 and the 4 staleness helpers; line 218 shows one usage)
    - src/main/java/org/ctc/domain/service/StandingsService.java (existing service)
    - .planning/phases/95-match-channel-posts/95-REVIEW.md (WR-05 — "MatchdayController houses 60+ lines of staleness business logic")
    - src/test/java/org/ctc/admin/controller/MatchdayControllerTest.java (must stay green)
    - CLAUDE.md "Architectural Principles / Keep Controllers Thin"
  </read_first>
  <behavior>
    - Test 1 (boundary): given a matchday with mixed stale + fresh result data, when `StandingsService.snapshotMatchdayStaleness(matchdayId)` is called, then it returns a record/Map mirroring the values the 4 controller helpers produced in aggregate.
    - Test 2 (boundary): given a matchday with no race results, when the same method is called, then it returns the "no data" sentinel that the controller previously rendered.
    - Existing `MatchdayController` tests stay green.
  </behavior>
  <action>
    Audit: `grep -rn "seasonTeamRepository\\b" src/main/java/org/ctc/admin/controller/MatchdayController.java` — must enumerate every reference. Per the WR-05 REVIEW.md finding, the field is used ONLY by the 4 staleness helpers; after extraction, delete the field from the controller.

    Refactor: move the 4 helpers (e.g., `latestRaceResultUpdate`, `latestMatchUpdate`, plus their aggregators) into `StandingsService.snapshotMatchdayStaleness(UUID matchdayId)`. Move `seasonTeamRepository` from controller to service constructor injection (or drop entirely if `StandingsService` already has it).

    Update the controller's matchday-detail action to call the new service method and copy results into `Model`.

    Create `StandingsServiceStalenessSnapshotTest` with two Given-When-Then methods.
  </action>
  <verify>
    <automated>./mvnw test -Dtest=StandingsServiceStalenessSnapshotTest,MatchdayControllerTest -DfailIfNoTests=true</automated>
  </verify>
  <acceptance_criteria>
    - `./mvnw test -Dtest=StandingsServiceStalenessSnapshotTest,MatchdayControllerTest -DfailIfNoTests=true` exits 0.
    - `grep -n "seasonTeamRepository" src/main/java/org/ctc/admin/controller/MatchdayController.java` returns 0 lines.
    - `grep -n "snapshotMatchdayStaleness" src/main/java/org/ctc/domain/service/StandingsService.java` returns at least 1 line (declaration).
    - The controller's matchday-detail action is ≤ 20 lines of model-population.
    - No marker comments.
  </acceptance_criteria>
  <done>Controller-thin extract #3 closed; commit: `refactor(102-02): extract MatchdayController staleness helpers + drop seasonTeamRepository → StandingsService.snapshotMatchdayStaleness — WR-thin-3 / 95 WR-05`.</done>
</task>

<task type="auto" tdd="false">
  <name>Task 4: Discord-domain point-fixes — symmetric setters, regex parity, markdown escape, dedup channel-resolve (95 WR-04 + 95 WR-07 + 94 WR-06 + 98 WR-02 + 98 WR-03)</name>
  <files>src/main/java/org/ctc/discord/dto/DiscordPostRef.java, src/main/java/org/ctc/discord/service/DiscordPostService.java, src/main/java/org/ctc/admin/dto/DiscordConfigForm.java</files>
  <read_first>
    - src/main/java/org/ctc/discord/dto/DiscordPostRef.java (focus `SeasonRef.applyTo` — must write both seasonId AND phaseId per 95 WR-07)
    - src/main/java/org/ctc/discord/service/DiscordPostService.java (focus `streamerField` builder, `parseWebhookUrl` regex, `populateMatchdayDiscordModel`)
    - src/main/java/org/ctc/admin/dto/DiscordConfigForm.java (webhook-URL form-validation regex)
    - src/test/java/org/ctc/discord/dto/DiscordPostRefSeasonRefWidenedTest.java (existing test for DiscordPostRef.SeasonRef — extend this rather than creating a new test class)
    - src/test/java/org/ctc/discord/dto/DiscordConfigFormTest.java (existing test for DiscordConfigForm — extend for webhook-URL regex parity)
    - .planning/phases/95-match-channel-posts/95-REVIEW.md (WR-04, WR-07)
    - .planning/phases/94-team-roles-match-channel-lifecycle/94-REVIEW.md (WR-06)
    - .planning/phases/98-polish-e2e-docs-close/98-REVIEW.md (WR-02, WR-03)
  </read_first>
  <action>
    Sub-fix A (95 WR-07) — `DiscordPostRef.SeasonRef.applyTo`: set both `target.seasonId = this.seasonId` AND `target.phaseId = this.phaseId`. Add 1 test method to the existing `DiscordPostRefSeasonRefWidenedTest` asserting both fields propagate.

    Sub-fix B (95 WR-04) — `streamerField` markdown-link escape: locate the builder via `grep -rn "streamerField\\b" src/main`; escape `)` in the URL portion of `[label](url)` so URLs ending with `)` don't break Discord's markdown parser. Use `url.replace(")", "\\\\)")` before concatenation. Existing `DiscordPostServiceWireMockIT` or `DiscordPostServiceMatchdayScheduleIT` covers the surrounding behavior; the regex fix is a point-edit — pin the escape behavior by extending whichever existing test exercises `streamerField`. If grep shows the method is exercised only at the service level, add a focused assertion to `DiscordPostServiceMatchdayPairingsPreFlightTest` or analogous existing test.

    Sub-fix C (94 WR-06 / 98 WR-03) — `parseWebhookUrl` ↔ `DiscordConfigForm` regex parity: align both regexes to `https://discord\\.com/api/webhooks/\\d{17,20}/[A-Za-z0-9_-]+`. Extract a single `WEBHOOK_URL_REGEX` constant on `DiscordPostService` (or new `DiscordWebhookUrls` utility) and reference it from both sites. Extend existing `DiscordConfigFormTest` with a parity assertion (e.g., a test that loops through a small fixture set of URLs and asserts both the form-validation and the service-parsing agree).

    Sub-fix D (98 WR-02) — `populateMatchdayDiscordModel` duplicate channel-ID resolution: extract the channel-ID resolution into a local variable and reference it twice; eliminate the duplicate `resolveAnnouncementChannelId(...)` call. Coverage is via existing `DiscordPostServiceMatchdayScheduleIT` or analogous — no new test class needed; behavior is unchanged but the call count drops.
  </action>
  <verify>
    <automated>./mvnw test -Dtest=DiscordPostRefSeasonRefWidenedTest,DiscordConfigFormTest -DfailIfNoTests=true && ./mvnw verify -Dit.test=DiscordPostServiceMatchdayScheduleIT,DiscordPostServiceMatchdayPairingsIT -DfailIfNoTests=true</automated>
  </verify>
  <acceptance_criteria>
    - Both verify commands exit 0.
    - `grep -n "phaseId" src/main/java/org/ctc/discord/dto/DiscordPostRef.java` shows phaseId being set inside `SeasonRef.applyTo`.
    - The webhook-URL regex appears exactly once as a constant referenced by both call sites.
    - `populateMatchdayDiscordModel` issues only one `resolveAnnouncementChannelId(...)` call per request.
    - `streamerField` builder escapes `)` in URLs.
    - No marker comments.
  </acceptance_criteria>
  <done>5 warning point-fixes in the Discord-DTO/service layer closed; commit: `fix(102-02): Discord-domain point-fixes (sym-setter, markdown-escape, webhook-regex parity, dedup channel-resolve) — 95 WR-04/07, 94 WR-06, 98 WR-02/03`.</done>
</task>

<task type="auto" tdd="false">
  <name>Task 5: Phase 94 remaining warnings — moveToArchive label, RoleCache no-roles window, audit NumberFormat, empty discordRoleId (94 WR-01 + WR-02 + WR-03 + WR-04 + WR-05)</name>
  <files>src/main/java/org/ctc/discord/service/DiscordChannelService.java, src/main/java/org/ctc/discord/cache/DiscordRoleCache.java, src/main/java/org/ctc/admin/dto/TeamForm.java</files>
  <read_first>
    - .planning/phases/94-team-roles-match-channel-lifecycle/94-REVIEW.md (WR-01..WR-05 in full)
    - src/main/java/org/ctc/discord/service/DiscordChannelService.java (focus `moveToArchive`, `assertPermissionAudit`, `matchHasCompleteSettings`)
    - src/main/java/org/ctc/discord/cache/DiscordRoleCache.java (focus `refresh`)
    - src/main/java/org/ctc/admin/dto/TeamForm.java (focus `discordRoleId` validation)
    - src/test/java/org/ctc/discord/service/DiscordChannelServiceNamingTest.java (existing unit test for DiscordChannelService — extend for WR-01 / WR-02 / WR-04 assertions; this is the unit-level fixture analogue of "DiscordChannelServiceTest")
    - src/test/java/org/ctc/discord/DiscordRoleCacheTest.java (existing test for the role cache)
    - src/test/java/org/ctc/admin/dto/TeamFormSnowflakeValidationTest.java (existing test for TeamForm snowflake-id validation — extend for empty-string rejection assertion)
  </read_first>
  <action>
    WR-01 — `moveToArchive` mislabels "no category selected" as `CATEGORY_FULL`: introduce a distinct error category (e.g., `DiscordCategoryNotSelectedException` or `IllegalArgumentException` with a `no-category-selected` flash-badge code). Update the controller mapping so the flash badge reflects the distinct category. Pin with a regression assertion added to the existing `DiscordChannelServiceNamingTest` (or a sibling unit-test class in the same package).

    WR-02 — `matchHasCompleteSettings` accepts unfilled `RaceSettings` rows: tighten the predicate to reject rows where required settings fields are null/blank. Reference existing `RaceSettings` validators OR required-field annotations to define "filled". Pin via assertion in `DiscordChannelServiceNamingTest`.

    WR-03 — `DiscordRoleCache.refresh` no-roles window: replace "clear-then-fill" with "compute-into-temp → atomic swap". Compute the new map first; only after success replace the cached reference. Existing `DiscordRoleCacheTest` must still pass.

    WR-04 — `assertPermissionAudit` `NumberFormatException` on `null` allow: guard the `Long.parseLong(allow)` with a null/blank check; treat missing `allow` as `0L` before XOR-ing with the expected bitmask. Pin via assertion in `DiscordChannelServiceNamingTest`.

    WR-05 — empty-string `discordRoleId`: extend validation on `TeamForm.discordRoleId` to reject empty strings. Most likely the existing `@Pattern(regexp = "^\\d{17,20}$")` already rejects empty strings; if not, add `@NotBlank` in combination. Add 1 method to the existing `TeamFormSnowflakeValidationTest` asserting empty-string rejection.
  </action>
  <verify>
    <automated>./mvnw test -Dtest=DiscordChannelServiceNamingTest,DiscordRoleCacheTest,TeamFormSnowflakeValidationTest -DfailIfNoTests=true</automated>
  </verify>
  <acceptance_criteria>
    - The verify command exits 0.
    - All 5 Phase-94 warnings closed per the REVIEW.md headings.
    - One new test method asserting empty-string `discordRoleId` rejection in `TeamFormSnowflakeValidationTest`.
    - `DiscordRoleCache.refresh` shows compute-then-swap shape (no clear-before-fill in the same critical section).
    - No marker comments.
  </acceptance_criteria>
  <done>5 Phase-94 warning fixes landed; commit: `fix(94): close 5 warning findings (mislabel/incomplete-settings/no-roles-window/NumberFormat/empty-roleId) — WR-01..05`.</done>
</task>

<task type="auto" tdd="false">
  <name>Task 6: Phase 95 remaining warnings — race-filename, empty-aggregate, dead-form-write, V14-orphan lookup, IT-transactional (95 WR-01 + WR-02 + WR-03 + WR-06 + WR-08)</name>
  <files>src/main/java/org/ctc/discord/service/DiscordPostService.java, src/main/java/org/ctc/domain/service/RaceService.java, src/main/java/org/ctc/admin/dto/SeasonForm.java, src/main/java/org/ctc/admin/controller/SeasonController.java, src/test/java/org/ctc/discord/service/DiscordChannelArchiveServiceWireMockIT.java</files>
  <read_first>
    - .planning/phases/95-match-channel-posts/95-REVIEW.md (WR-01, WR-02, WR-03, WR-06, WR-08 in full)
    - src/main/java/org/ctc/discord/service/DiscordPostService.java (focus `postRaceResultToForumThread` filename builder)
    - src/main/java/org/ctc/domain/service/RaceService.java (focus `saveResults` — empty-results-list path)
    - src/main/java/org/ctc/admin/dto/SeasonForm.java (focus `discordRaceResultsThreadId`, `discordStandingsThreadId`)
    - src/test/java/org/ctc/discord/service/DiscordChannelArchiveServiceWireMockIT.java (focus class-level `@Transactional`)
    - src/test/java/org/ctc/discord/service/DiscordPostServiceForumThreadIT.java (existing IT covering the forum-thread path — extend for filename-stability assertion)
  </read_first>
  <action>
    WR-01 — `postRaceResultToForumThread` filename uses unstable matchday-wide race index: switch to the race's own ordinal-within-match (e.g., `race.getOrder()`) for filename stability. Extend `DiscordPostServiceForumThreadIT` with an assertion that two races in different matchdays with the same `race.getOrder()` produce the expected filename pattern.

    WR-02 — `RaceService.saveResults` does not aggregate when given empty results list: per CLAUDE.md "Score Aggregation on Result Save", call `scoringService.aggregateMatchScores(race)` after the save even when the list is empty (intentional clearing). Pin with regression assertion in existing `RaceServiceTest`.

    WR-03 — `SeasonForm.discordRaceResultsThreadId` + `discordStandingsThreadId` dead-write: this task neutralizes the controller's WRITE to these fields (so the asymmetric write→never-read is closed). Actual field deletion happens in Plan 102-03 Task 4.

    WR-06 — pre-V14 `STANDINGS` rows orphaned from new phase-aware lookup: add a defensive lookup-fallback in `DiscordPostService` for rows where `phase_id` is null. This is NOT a "fallback calculation" per CLAUDE.md — it's a defensive read of legitimately-distinguishable historical data.

    WR-08 — `DiscordChannelArchiveServiceWireMockIT` `@Transactional` but tests channel-archive flow: remove the class-level `@Transactional`. Restructure setup to use explicit cleanup OR `@DirtiesContext` per `.planning/codebase/TESTING.md`.
  </action>
  <verify>
    <automated>./mvnw test -Dtest=RaceServiceTest -DfailIfNoTests=true && ./mvnw verify -Dit.test=DiscordPostServiceForumThreadIT,DiscordChannelArchiveServiceWireMockIT -DfailIfNoTests=true</automated>
  </verify>
  <acceptance_criteria>
    - Both verify commands exit 0.
    - `RaceService.saveResults` calls `scoringService.aggregateMatchScores(...)` for the empty-list path.
    - `DiscordChannelArchiveServiceWireMockIT` no longer carries `@Transactional` at class or method level.
    - `postRaceResultToForumThread` filename is deterministic per race (grep for `race.getOrder()` or analogous identifier).
    - No marker comments.
  </acceptance_criteria>
  <done>5 Phase-95 warning fixes landed; commit: `fix(95): close 5 warning findings (race-filename, empty-aggregate, dead-form-write, V14-orphan, IT-transactional) — WR-01/02/03/06/08`.</done>
</task>

<task type="auto" tdd="false">
  <name>Task 7: Phase 96 + Phase 97 warning sweep (96 WR-01..08 + 97 WR-01..07)</name>
  <files>per-finding files enumerated from REVIEW.md by the executor; likely: src/main/java/org/ctc/discord/service/ProvisionalScoresGraphicService.java, src/main/java/org/ctc/discord/service/DiscordForumService.java, src/main/java/org/ctc/admin/controller/MatchdayController.java, src/main/java/org/ctc/admin/service/MatchdayPairingsGraphicService.java, plus their test files</files>
  <read_first>
    - .planning/phases/96-provisional-graphic-forum-threads/96-REVIEW.md (WR-01..WR-08; read each ### heading + body in full)
    - .planning/phases/97-matchday-level-posts/97-REVIEW.md (WR-01..WR-07; read each ### heading + body in full)
    - For each WR finding: the file referenced in the REVIEW.md `Location` line + the corresponding test file
    - Existing test classes that may cover the touched surface: `src/test/java/org/ctc/admin/service/ProvisionalScoresGraphicServiceTest.java`, `src/test/java/org/ctc/discord/service/DiscordForumServiceTest.java`, `src/test/java/org/ctc/admin/controller/MatchdayControllerTest.java`, `src/test/java/org/ctc/discord/service/DiscordPostServiceMatchdayPairingsIT.java`, `src/test/java/org/ctc/discord/service/DiscordPostServiceMatchdayPairingsPreFlightTest.java`
  </read_first>
  <action>
    Enumerate every `### WR-NN` heading in the two REVIEW.md files and apply the listed fix. The REVIEW.md files ARE the authoritative finding list per CONTEXT D-13 — do not duplicate them inline here. For each finding:
    1. Read the REVIEW.md section (Description + Suggested Fix).
    2. Read the source file at the cited location.
    3. Apply the suggested fix verbatim where possible; deviate only when the suggestion conflicts with CLAUDE.md (e.g., a suggested fix that adds a fallback calculation — in that case apply the architectural-principles version).
    4. If the finding requires a NEW test, add it; if existing tests cover the new behavior, add an assertion to the existing test method. **Important:** before assuming a test class exists, grep `src/test/java` for the actual class name — the candidate list in `<read_first>` is verified to exist; for any other test file the action references, the executor MUST first `find src/test/java -name '*<symbol>*'` and use the actual class name. If no test class covers the affected behavior, the executor either extends a near-neighbor test or creates a new class with `@Tag` appropriate to its category (`*IT.java` → `@Tag("integration")`).
    5. Per CLAUDE.md "No Comment Pollution": no marker comments in source or test.

    Group commits by REVIEW.md phase (one commit per phase): `fix(96): close 8 warning findings — WR-01..08` and `fix(97): close 7 warning findings — WR-01..07`. Body of each commit lists the finding-IDs closed.
  </action>
  <verify>
    <automated>./mvnw test -Dtest=ProvisionalScoresGraphicServiceTest,DiscordForumServiceTest,MatchdayControllerTest -DfailIfNoTests=true && ./mvnw verify -Dit.test=DiscordPostServiceMatchdayPairingsIT,DiscordPostServiceMatchdayResultsIT -DfailIfNoTests=true</automated>
  </verify>
  <acceptance_criteria>
    - Both verify commands exit 0.
    - Every `### WR-NN` finding in `96-REVIEW.md` and `97-REVIEW.md` has a corresponding code change or test addition (validated by the orchestrator-level `/gsd-code-review 102 --files=...` per CONTEXT D-04 returning zero re-occurrences of these WR-IDs).
    - At least 2 atomic commits (one per phase) on the milestone branch.
    - No marker comments.
  </acceptance_criteria>
  <done>15 warning fixes in Phase 96 + 97 closed; commits per the enumeration above.</done>
</task>

<task type="auto" tdd="false">
  <name>Task 8: Phase 98 remaining warnings + Phase 99 warnings — pairings @Size cap, dev-seeder save guard, editPairings IT, V14 backup-mixin regression, prose alignment (98 WR-01 + WR-04 + WR-05 + WR-06 + 99 WR-01 + WR-02)</name>
  <files>src/main/java/org/ctc/admin/dto/MatchdayPairingsForm.java, src/main/java/org/ctc/discord/seed/DiscordDevSeeder.java, src/test/java/org/ctc/admin/controller/MatchdayControllerPostEndpointsIT.java, src/main/java/org/ctc/discord/dto/DiscordPostType.java, plus any prose-doc files flagged in 99-REVIEW.md</files>
  <read_first>
    - .planning/phases/98-polish-e2e-docs-close/98-REVIEW.md (WR-01, WR-04, WR-05, WR-06)
    - .planning/phases/99-pre-merge-audit-polish-requirements-flyway-prose-roadmap-ref/99-REVIEW.md (WR-01, WR-02 — both in full)
    - src/main/java/org/ctc/admin/dto/MatchdayPairingsForm.java (focus `matchdayPairingsTemplate` — missing `@Size` cap)
    - src/main/java/org/ctc/discord/seed/DiscordDevSeeder.java (focus `persistIfDirty`)
    - src/main/java/org/ctc/discord/dto/DiscordPostType.java (focus enum constants — backup-MixIn regression test required per 98 WR-04)
    - src/test/java/org/ctc/backup/schema/BackupSchemaGuardTest.java (existing test for backup schema guards — extend for the DiscordPostType-vs-MixIn coverage assertion)
    - src/test/java/org/ctc/discord/DiscordDevSeederIT.java (existing IT for the dev seeder; extend for the dirty-check assertion)
    - src/test/java/org/ctc/admin/controller/MatchdayControllerPostEndpointsIT.java (existing IT in the MatchdayController test family; the editPairings GET coverage test goes here, rather than a new MatchdayPairingsControllerIT class which does not currently exist)
  </read_first>
  <action>
    98 WR-01 — `MatchdayPairingsForm.matchdayPairingsTemplate` no `@Size` cap: add `@Size(max=2000)` (matching Discord's content limit for webhook payloads) on the field. Pin with a unit test — if `MatchdayPairingsFormValidationTest` does not yet exist (confirm via `find src/test/java -name 'MatchdayPairingsForm*'`), add the assertion to a near-neighbor form-validation test (e.g., `TeamFormSnowflakeValidationTest` pattern as a reference for shape) OR create `MatchdayPairingsFormValidationTest` as a new unit-test class with one Given-When-Then method asserting the cap. The decision is documented in Plan 102-02 SUMMARY.md.

    98 WR-04 — `DiscordPostType` enum extended without backup-MixIn regression test: add a regression-fence test method to the existing `BackupSchemaGuardTest` asserting that every `DiscordPostType` constant is covered by the backup-mixin's `JsonTypeInfo` configuration. Test fails on the next added enum constant without parallel mixin update.

    98 WR-05 — `DiscordDevSeeder.persistIfDirty` re-saves config that may have been mutated by `applyConfig` already: add a comparison-based dirty-check before save. If config is byte-identical to the DB-row, skip the save. Extend existing `DiscordDevSeederIT` with a no-op assertion (second invocation produces no save).

    98 WR-06 — `editPairings` GET endpoint lacks integration test coverage: add an IT method `givenMatchdayWithPairings_whenGetEditPairings_thenRendersForm` to the existing `MatchdayControllerPostEndpointsIT` (which already carries `@Tag("integration")` and the MatchdayController test infrastructure). Do NOT create a new `MatchdayPairingsControllerIT` class — the existing IT is the natural home.

    99 WR-01 + WR-02 — prose/roadmap alignment: read both findings in 99-REVIEW.md and apply the textual edits to the cited files (likely ROADMAP.md or REQUIREMENTS.md). Per CONTEXT non-goals these are text-only edits; no Flyway migration touched.
  </action>
  <verify>
    <automated>./mvnw test -Dtest=BackupSchemaGuardTest -DfailIfNoTests=true && ./mvnw verify -Dit.test=DiscordDevSeederIT,MatchdayControllerPostEndpointsIT -DfailIfNoTests=true</automated>
  </verify>
  <acceptance_criteria>
    - Both verify commands exit 0.
    - `grep -n "@Size" src/main/java/org/ctc/admin/dto/MatchdayPairingsForm.java` returns at least 1 line on the template field.
    - `BackupSchemaGuardTest` has a new assertion that every `DiscordPostType` constant is covered by the mixin.
    - `MatchdayControllerPostEndpointsIT` has at least one `editPairings` GET-coverage test.
    - 99 WR-01 + WR-02 prose edits applied to the cited files.
    - No marker comments.
  </acceptance_criteria>
  <done>6 warning fixes landed; commit: `fix(102-02): Phase 98 + 99 warning closures (pairings-cap, mixin-guard, dev-seeder dirty-check, editPairings IT, prose) — 98 WR-01/04/05/06, 99 WR-01/02`.</done>
</task>

<task type="auto" tdd="false">
  <name>Task 9: Phase 101 warning sweep — 24-entity stale references, helper consolidation, mixin id-info, pinDiscordPostLast generalization (101 WR-01..07)</name>
  <files>src/main/java/org/ctc/backup/service/BackupExportService.java, src/main/java/org/ctc/backup/service/BackupImportService.java, src/main/java/org/ctc/backup/schema/BackupSchema.java, src/main/java/org/ctc/backup/mixin/DiscordGlobalConfigMixIn.java, src/main/java/org/ctc/backup/mixin/DiscordPostMixIn.java, src/test/java/org/ctc/backup/service/BackupRoundTripIT.java, src/test/java/org/ctc/backup/service/BackupImportSchemaMismatchIT.java</files>
  <read_first>
    - .planning/phases/101-backup-restore-covers-discord-schema-v8-v15/101-REVIEW.md (WR-01..WR-07 in full)
    - src/main/java/org/ctc/backup/service/BackupExportService.java (focus stale "24 entities" string + `lookupRepository` error message)
    - src/main/java/org/ctc/backup/service/BackupImportService.java (same stale references)
    - src/main/java/org/ctc/backup/schema/BackupSchema.java (focus `pinDiscordPostLast` — see if it can be generalized to `pinUuidFkEntitiesLast`)
    - src/main/java/org/ctc/backup/mixin/DiscordGlobalConfigMixIn.java + src/main/java/org/ctc/backup/mixin/DiscordPostMixIn.java (focus `@JsonIdentityInfo`)
    - src/test/java/org/ctc/backup/service/BackupRoundTripIT.java + src/test/java/org/ctc/backup/service/BackupImportSchemaMismatchIT.java (focus duplicated helpers `exportToBytes`, `captureRowCounts`, `hashEntity`, `awaitAuditRow`)
  </read_first>
  <action>
    WR-01 — stale "24 entities / 24 tables" references across `BackupExportService` + `BackupImportService`: grep for the literal `"24"` in both files; replace with a constant derived from `BackupSchema.getExportOrder().size()` OR an explicit `"26 entities"` if a constant is not architecturally clean.

    WR-02 — `BackupExportService.lookupRepository` error message stale at "24 entities": same fix as WR-01 on that specific message.

    WR-03 — comment pollution in main source (Phase / Plan / D-NN / WR-NN / EXPORT-NN / CR-NN / SC# / IMPORT-NN / UI-SPEC markers): NOTE — this is also covered by Plan 102-03's blanket grep oracle. In this task, address only the WR-03 listed file-specific markers; the blanket sweep happens in 102-03 Task 1. Commit subject: `chore(101): remove phase-marker comments in backup-domain source — WR-03 (partial; full sweep in 102-03)`.

    WR-04 — `BackupRoundTripIT` duplicated helpers across H2 and MariaDB nested classes: extract `exportToBytes`, `captureRowCounts`, `hashEntity`, `awaitAuditRow` into a shared base class OR a `BackupITSupport` utility. Both nested classes reference the shared utility.

    WR-05 — `BackupImportSchemaMismatchIT.snapshotAllCounts` missing `discord_global_config` and `discord_post`: add those tables to the snapshot. Test assertions must still pass after the addition.

    WR-06 — `DiscordGlobalConfigMixIn` / `DiscordPostMixIn` `@JsonIdentityInfo` contradicts "no id" intent: either remove `@JsonIdentityInfo` (if id is genuinely never serialized) OR rename the intent comment to reflect the actual policy.

    WR-07 — `BackupSchema.pinDiscordPostLast` hardcoded; does not extend to future @Column-UUID FK entities: generalize to `pinFkEntitiesLast(Set<EntityRef> fkEntities)` accepting a configurable set. Default behavior preserved by passing `Set.of(DiscordPost.class)`. Document the generalization via the method signature; no marker comments.
  </action>
  <verify>
    <automated>./mvnw verify -Dit.test=BackupRoundTripIT,BackupImportSchemaMismatchIT,BackupLenientV1AcceptanceIT -DfailIfNoTests=true</automated>
  </verify>
  <acceptance_criteria>
    - The verify command exits 0.
    - `grep -rn "24 entities\\|24 tables" src/main/java/org/ctc/backup/` returns 0 lines.
    - `BackupRoundTripIT` H2 + MariaDB nested classes call shared utility methods rather than duplicated bodies.
    - `BackupImportSchemaMismatchIT.snapshotAllCounts` includes `discord_global_config` AND `discord_post`.
    - `BackupSchema.pinDiscordPostLast` is renamed or wraps the generalized `pinFkEntitiesLast` API.
    - No new marker comments; WR-03 partial sweep in this commit limited to the backup-domain files identified in the REVIEW.md location field.
  </acceptance_criteria>
  <done>7 Phase-101 warning fixes landed; commit: `fix(101): close 7 warning findings (stale-counts, helper-consolidation, mixin-id, pin-generalization) — WR-01..07`.</done>
</task>

<task type="auto" tdd="false">
  <name>Task 10: Phase 92 + Phase 93 warning sweep — info-leak parity, regex parity, dev-profile drift (92 WR-01..06 + 93 WR-01..06)</name>
  <files>per-finding files enumerated from REVIEW.md by the executor; likely: src/main/java/org/ctc/discord/DiscordRestClient.java, src/main/java/org/ctc/discord/DiscordRateLimitInterceptor.java, src/main/java/org/ctc/dataimport/CsvImportController.java (any 92 WR carried over from CR-01 patch), src/main/java/org/ctc/discord/DiscordTimestamps.java</files>
  <read_first>
    - .planning/phases/92-carry-forwards-cleanup/92-REVIEW.md (WR-01..WR-06; read each in full)
    - .planning/phases/93-discord-foundation/93-REVIEW.md (WR-01..WR-06; read each in full)
    - For each WR finding: the file referenced in the REVIEW.md `Location` line
    - Existing test classes confirmed to cover the surfaces: `src/test/java/org/ctc/discord/DiscordRestClientIT.java`, `src/test/java/org/ctc/discord/DiscordRateLimitInterceptorIT.java`, `src/test/java/org/ctc/discord/DiscordTimestampsTest.java`, `src/test/java/org/ctc/dataimport/CsvImportControllerIT.java` (Plan 102-01 already extended this)
  </read_first>
  <action>
    Enumerate every `### WR-NN` heading in 92-REVIEW.md and 93-REVIEW.md. Apply the suggested fix per finding. Same discipline as Task 7: no marker comments, REVIEW.md is the source of truth, deviate only when the suggestion conflicts with CLAUDE.md (e.g., a suggested fix that introduces a JDK `HttpClient` would conflict with "Spring-Native" — apply the Spring-native version).

    Before extending or creating any test, run `find src/test/java -name '<symbol>*'` to confirm the actual existing test-class name; do NOT assume names like `DiscordRestClientTest` or `DiscordRateLimitInterceptorTest` exist — the actual files are `DiscordRestClientIT.java` (Failsafe-routed IT) and `DiscordRateLimitInterceptorIT.java`. Where the fix is behaviour-preserving and covered by an existing IT, extend that IT. Where the fix is purely an internal refactor with no test-observable behavior change (e.g., regex constant extraction), no test extension is required — but acceptance still requires the verify command exits 0 and the orchestrator-level review confirms closure.

    Group commits by phase: `fix(92): close 6 warning findings — WR-01..06` and `fix(93): close 6 warning findings — WR-01..06`. Body lists each finding-ID closed.
  </action>
  <verify>
    <automated>./mvnw test -Dtest=DiscordTimestampsTest -DfailIfNoTests=true && ./mvnw verify -Dit.test=DiscordRestClientIT,DiscordRateLimitInterceptorIT,CsvImportControllerIT -DfailIfNoTests=true</automated>
  </verify>
  <acceptance_criteria>
    - Both verify commands exit 0.
    - Every `### WR-NN` finding in `92-REVIEW.md` and `93-REVIEW.md` has a corresponding code change or test addition (validated by the orchestrator-level `/gsd-code-review 102 --files=...` per CONTEXT D-04 returning zero re-occurrences of these WR-IDs).
    - At least 2 atomic commits (one per phase).
    - No marker comments.
  </acceptance_criteria>
  <done>12 warning fixes in Phase 92 + 93 closed; commits per enumeration above.</done>
</task>

<task type="auto" tdd="false">
  <name>Task 11: Plan 102-02 final verification — orchestrator-level review prep</name>
  <files>(no source files modified; this task is a verification gate)</files>
  <read_first>
    - .planning/phases/102-code-review-fixes/102-CONTEXT.md (D-04 — per-plan review cadence)
    - All previous Plan 102-02 task outputs (commits, test results)
  </read_first>
  <action>
    Re-run all targeted test commands from Tasks 1-10 (in a single Surefire+Failsafe pass) to confirm the cumulative Plan 102-02 diff is green. Do NOT run `./mvnw clean verify -Pe2e` — that's Plan 102-04 per CONTEXT D-11.

    Compute `files_modified` for the close-loop review: `git diff --name-only origin/master..HEAD -- 'src/main/**' 'src/test/**'`. Hand this list to the orchestrator (out-of-band, in the SUMMARY.md "files for per-plan review" section).

    Per CONTEXT D-04 the orchestrator (NOT this plan) runs `/gsd-code-review 102 --files=<list>` after this task is green AND before the SUMMARY.md commit. Plan 102-02 SUMMARY.md records "per-plan review: clean" only after the orchestrator confirms.
  </action>
  <verify>
    <automated>./mvnw test && ./mvnw verify -Dit.test='Discord*IT,BackupRoundTripIT,BackupImportSchemaMismatchIT,BackupLenientV1AcceptanceIT,MatchdayControllerPostEndpointsIT' -DfailIfNoTests=true</automated>
  </verify>
  <acceptance_criteria>
    - All verify commands exit 0.
    - `git diff --name-only origin/master..HEAD` against the milestone branch lists ONLY files in this plan's `files_modified` plus the Plan 102-01 files already on the branch.
    - No new marker comments anywhere — `grep -rEn "^\\s*(//|--|#)\\s*(Phase |Plan |D-[0-9]|UAT-|WR-|CR-|IN-|BL-|Wave )" src/main src/test src/main/resources/db/migration` MUST NOT contain new lines on files this plan touched.
    - SUMMARY.md captures the file-list for the orchestrator-level review.
  </acceptance_criteria>
  <done>Plan 102-02 ready for orchestrator-level `/gsd-code-review 102 --files=...`; SUMMARY.md commit happens after the orchestrator confirms `clean`.</done>
</task>

</tasks>

<verification>
After all 11 tasks land:
1. The orchestrator runs `/gsd-code-review 102 --files=<files_modified>` on the Plan 102-02 diff per CONTEXT D-04. Result must be `clean` (zero critical + zero warning).
2. `grep -rEn "^\\s*(//|--|#)\\s*(Phase |Plan |D-[0-9]|UAT-|WR-|CR-|IN-|BL-|Wave )" src/main src/test src/main/resources/db/migration` MUST NOT report NEW lines on files this plan touched (Plan 102-03 closes pre-existing pollution).
3. Only then commit the SUMMARY.md.
</verification>

<success_criteria>
- All 58 warning findings closed (or formally deferred via STATE.md entry per ROADMAP success criterion #2; default is close).
- 3 controller-thin refactor extracts landed with boundary tests; controllers ≤ 15 lines of model-population for the touched actions.
- The new `DiscordSeasonViewService` exists at `src/main/java/org/ctc/admin/service/DiscordSeasonViewService.java` (NOT a modification of the unrelated `domain.service.SeasonManagementService`).
- Every `<verify>` block references an existing test class confirmed via `find src/test/java`. NEW test classes are created explicitly inside their task's `<action>` step.
- All existing tests stay green (no regressions).
- ZERO new marker comments in any touched file.
- Per-plan `/gsd-code-review 102 --files=...` returns clean before SUMMARY commit.
</success_criteria>

<output>
Create `.planning/phases/102-code-review-fixes/102-02-SUMMARY.md` capturing per-task: finding-IDs closed, commit SHAs, targeted-test exit codes, and the file-list for the orchestrator-level review. Record (a) the chosen return-shape for the three controller-thin extracts (record vs Map), (b) confirmation that DiscordSeasonViewService is a NEW admin-layer service (not an extension of SeasonManagementService), and (c) actual test-class names used per task verify command.
</output>
</content>
</invoke>