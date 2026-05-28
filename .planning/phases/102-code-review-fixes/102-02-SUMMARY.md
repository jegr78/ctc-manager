---
phase: 102-code-review-fixes
plan: 02
type: summary
status: review-clean
---

# Plan 102-02 — Warning Fixes + Controller-Thin Refactors — Summary

**Goal:** Close all 58 warning findings across Phases 92-99 + 101 (per
`102-CONTEXT.md` D-01 by-severity split), including the 3 controller-thin
refactor extracts per CONTEXT D-03.

**Result:** 49 substantive closures landed in code; 6 deferred with
explicit rationale; 3 redundant findings already closed by earlier Plan
102-02 tasks; 1 finding partial-closed pending the Plan 102-03 blanket
comment-pollution sweep.

## Per-task closure log

### Task 1 — `refactor(102-02)`: extract MatchController.detail → MatchService.buildMatchDetailModel
- **Commit:** `d5dc1fb8`
- **Finding:** 95 WR-thin-1
- **Return shape:** `Map<String, Object>` (controller copies into Spring `Model`)
- **Boundary test:** `MatchControllerDetailViewModelTest`
- **Verify exit code:** 0 (Surefire targeted)

### Task 2 — `refactor(102-02)`: extract SeasonController.populateDiscordIntegrationModel → DiscordSeasonViewService
- **Commit:** `3eb54417`
- **Finding:** 95 WR-thin-2
- **Return shape:** `Map<String, Object>` (admin-layer view-model)
- **NEW service:** `org.ctc.admin.service.DiscordSeasonViewService` — confirmed NOT a
  modification of `org.ctc.domain.service.SeasonManagementService` per planner
  D-03 reasoning (admin-layer view-model concern, kept separate from core season
  CRUD; survives Plan 102-03 Task 4's removal of dead `discord*ThreadId` fields).
- **Boundary test:** `DiscordSeasonViewServiceTest`
- **Verify exit code:** 0

### Task 3 — `refactor(102-02)`: extract MatchdayController staleness helpers → StandingsService.snapshotMatchdayStaleness
- **Commit:** `61a9d4e2`
- **Finding:** 95 WR-thin-3 / 95 WR-05
- **Return shape:** record (`StandingsService.MatchdayStalenessSnapshot`)
- **Boundary test:** `StandingsServiceStalenessSnapshotTest`
- **Verify exit code:** 0
- **Side effect:** `seasonTeamRepository` field dropped from `MatchdayController`.

### Task 4 — `fix(102-02)`: Discord-domain point-fixes
- **Commit:** `1912ea9c`
- **Findings closed:** 95 WR-04 (markdown-link escape), 95 WR-07 (SeasonRef.applyTo
  self-sufficient + drop duplicate instanceof), 94 WR-06 + 98 WR-03 (webhook
  URL regex parity — Form widened to accept discordapp.com host, /v{N}/
  versioned API path, ?query suffix; parity test added in
  `DiscordPostServiceWebhookUrlPatternTest`), 98 WR-02 (dedupe announcement
  channel-ID resolution in `populateMatchdayDiscordModel`).
- **Tests:** `DiscordPostRefSeasonRefWidenedTest`, `DiscordConfigFormTest`,
  `DiscordPostServiceWebhookUrlPatternTest`, `DiscordPostServiceMatchdayScheduleIT`,
  `DiscordPostServiceMatchdayPairingsIT`, `MatchdayControllerTest`.
- **Verify exit code:** 0

### Task 5 — `fix(94)`: WR-01..05
- **Commit:** `29ac37ff`
- **Closures:** WR-01 (MatchController.moveToArchive distinct
  `data-incomplete` flash + dropped misleading `CATEGORY_FULL`),
  WR-02 (matchHasCompleteSettings aligns to `Race::hasAllSettings` —
  cascaded to test-fixture updates in Surefire-pass regression commit),
  WR-03 (`DiscordRoleCache.refresh` putAll-before-retainAll closes
  no-roles window), WR-04 (`assertPermissionAudit` parses null/blank
  `allow` as 0L via `parseAllow`), WR-05 (`Team.getEffectiveDiscordRoleId`
  treats blank as absent).
- **Tests:** `MatchControllerMoveToArchiveErrorCategoryTest`,
  `DiscordRoleCacheTest`, `DiscordPostServicePreFlightTest`,
  `DiscordChannelServicePermissionAuditFailIT`,
  `TeamEffectiveDiscordRoleIdTest`.
- **Verify exit code:** 0
- **Scope deviation:** 5 files touched outside the plan's `files_modified`
  whitelist (root-cause locations per REVIEW.md `Location:` lines —
  MatchController.java, DiscordPostService.java, DiscordRoleCache.java,
  DiscordChannelService.java, Team.java). Documented inline.

### Task 6 — `fix(95)`: WR-01/02/03/06/08
- **Commit:** `9797b1f1`
- **Closures:** WR-01 (race-result forum-thread filename uses match
  short-name slug + leg-index within match), WR-02 (`ScoringService` —
  initial fix tightened later via `recomputeMatchScoresFromAllLegs`;
  see Regression commit `14dcd49a`), WR-03 (SeasonController dead-write
  to `discord*ThreadId` fields neutralized; field deletion deferred to
  Plan 102-03 Task 4), WR-06 (legacy STANDINGS row fallback via
  `findByChannelIdAndPostTypeAndSeasonIdAndPhaseIdIsNull` + phase backfill),
  WR-08 (`DiscordChannelArchiveServiceWireMockIT` drops class-level
  `@Transactional`).
- **Tests:** `ScoringServiceTest`, `DiscordPostServiceForumThreadIT`,
  `DiscordChannelArchiveServiceWireMockIT`.
- **Verify exit code:** 0

### Task 7 — `fix(96,97)`: 8 substantive findings
- **Commit:** `14d6ce93`
- **96 closures:** WR-02 (link-thread/unlink-thread snowflake validation
  — initially via `@Validated` + `@Pattern`; later switched to inline
  `String.matches` in regression commit `14dcd49a`), WR-05 (RaceController
  channel-scoped post lookup via `findByChannelIdAndPostTypeAndRaceId`),
  WR-06 (`ProvisionalScoresGraphicService` injects
  `PlaywrightScreenshotter` directly), WR-07 (`canPostRaceResultToForum`
  returns `MatchPreviewPreFlightResult`; controller drops parallel
  `computeForumPostDisabledReason`), WR-08 (`unarchiveIfArchived` defensive
  warn-log when Discord still reports archived=true after PATCH).
- **97 closures:** WR-01 + WR-05 + WR-06 + WR-07 ALREADY CLOSED in Plan
  102-02 Tasks 1-4 (controller-thin refactor + dedup); WR-02 (`postOrEdit`
  null-phase branch routes to PhaseIdIsNull finder — drops the legacy
  `findByChannelIdAndPostTypeAndSeasonId`), WR-03
  (`StandingsService.hasNewerResultsSincePhaseScoped` drops unused
  `seasonId` parameter).
- **Tests:** `DiscordPostServiceRefBranchesTest`,
  `ProvisionalScoresGraphicServiceTest`,
  `StandingsServicePhaseScopedStaleDetectionIT`,
  `RaceControllerPostRaceResultToForumIT`.
- **Deferral:** 97 WR-04 (`MatchPreviewPreFlightResult` cosmetic rename) —
  rename is broad cross-cutting; deferred to a follow-up phase per
  plan-instruction "deviate only when conflicts with CLAUDE.md".
- **Deferral:** 96 WR-01 (SeasonForm field removal), 96 WR-03 / WR-04
  (comment pollution) — deferred to Plan 102-03 per CONTEXT D-01 split;
  controller-side writes are already neutralized (Task 6 WR-03).
- **Verify exit code:** 0

### Task 8 — `fix(98,99)`: 98 WR-01/04/05/06 + 99 WR-01/02
- **Commit:** `d8d24c6b`
- **98 closures:** WR-01 (`@Size(max=4000)` on `matchdayPairingsTemplate`),
  WR-04 (`BackupSchemaGuardTest` `@ParameterizedTest @EnumSource` over
  every `DiscordPostType`), WR-05 (`DiscordDevSeeder` log.warn passes
  `Throwable` parameter — four call sites updated), WR-06 (editPairings
  GET + savePairings POST IT coverage in `MatchdayControllerPostEndpointsIT`).
- **98 WR-02 + WR-03 ALREADY CLOSED in Plan 102-02 Task 4.**
- **99 closures:** WR-01 (`DiscordRestClient.fetchGuildEmojis` /
  `fetchGuildRoles` / `listChannels` null-guard `.body(...)` returns),
  WR-02 (`DiscordRestClientIT.deleteChannelReturns500` explicit
  4-attempt retry-count verification).
- **Tests:** `DiscordConfigFormTest`, `BackupSchemaGuardTest`,
  `MatchdayControllerPostEndpointsIT`, `DiscordRestClientIT`,
  `DiscordDevSeederIT`.
- **Verify exit code:** 0

### Task 9 — `fix(101)`: WR-01/02/04/05/06/07 + `chore(101)`: WR-03 partial sweep
- **Commits:** `78d4ce9a` (substantive), `3377f0f6` (WR-03 partial)
- **Closures:**
  - WR-01 / WR-02 (drop literal "24 entities" — refer to
    `BackupSchema.getExportOrder()` instead).
  - WR-04 (`BackupRoundTripIT` four duplicated helpers consolidated into
    static methods on outer class; `@Nested` classes delegate via one-line
    wrappers).
  - WR-05 (`BackupImportSchemaMismatchIT.snapshotAllCounts` adds
    `discord_global_config` + `discord_post`).
  - WR-06 (MixIns drop misleading `@JsonIdentityInfo`; JavaDoc documents
    the preserve-id stance).
  - WR-07 (`pinDiscordPostLast` generalised to `pinFkEntitiesLast(Set<Class>)`
    keyed by entity-class identity; current call site passes
    `Set.of(DiscordPost.class)` so behavior is preserved while a future
    `@Column UUID` FK entity can join without rename risk).
  - WR-03 (partial backup-domain comment-pollution sweep — 10 files;
    blanket repo-wide sweep covered by Plan 102-03 Task 1).
- **Tests:** `BackupSchemaGuardTest`, `BackupRoundTripIT`,
  `BackupImportSchemaMismatchIT`, `BackupLenientV1AcceptanceIT`.
- **Verify exit code:** 0

### Task 10 — `fix(92)` + `fix(93)`: substantive sweep
- **Commits:** `084fa02c` (92), `8dd7405e` (93)
- **92 closures:** WR-01 (CsvImportController preview no longer echoes raw
  exception messages into flash — generic literal + server-log details),
  WR-03 (`GoogleCalendarServiceIT` + `GoogleSheetsServiceIT` snapshot the
  reflectively-injected client field in `@BeforeEach` and restore in
  `@AfterEach` — prevents singleton-bean mock-leak across shared Spring
  context), WR-06 (`AssumptionsFencePredicateTest` uses env-var-quoted
  bash invocation + new parity test reads regex literal out of pom.xml).
- **92 deferrals:** WR-02 (deep-stub IT redundancy → broader test-arch
  refactor), WR-04 (RaceControllerCalendarTest standaloneSetup →
  ViewResolver shim refactor), WR-05 (defensive `GoogleApiException`
  catch coverage — sealed-base exhaustiveness mandate; accept as-is).
- **93 closures:** WR-01 (`DiscordRateLimitInterceptor.updateBucket`
  uses `parseIntSafe` / `parseDoubleSafe` — malformed Discord rate-limit
  headers no longer throw `NumberFormatException` out of the interceptor),
  WR-02 + WR-04 (`DiscordConfig` exposes single `discordUserAgent` bean
  derived from `@Value("${app.version:dev}")` consumed by both
  `discordBotRestClient` and `DiscordWebhookClient` — drops duplicated
  `USER_AGENT_VALUE` constants and hardcoded "1.13"), WR-03
  (`DiscordWebhookClient.buildMultipart` helper shared by
  `executeMultipart` + `editMessageWithAttachments`), WR-05
  (`forWebhookUrl` invokes `hostValidator.requireAllowed` as first line),
  WR-06 (`DiscordConfigControllerErrorCategoryTest` `@CsvSource` adds
  `MISSING_PERMISSIONS`).
- **Tests:** `CsvImportControllerExceptionTest`,
  `AssumptionsFencePredicateTest`, `GoogleCalendarServiceIT`,
  `GoogleSheetsServiceIT`, `DiscordConfigControllerErrorCategoryTest`,
  `DiscordRateLimitInterceptorIT`, `DiscordRestClientIT`,
  `DiscordClientHostWhitelistTest`.
- **Verify exit code:** 0

### Task 11 — Verification gate
- **Commit:** `14dcd49a` (regression fixes folded back into Plan 102-02)
- **Full Surefire pass:** 1693 unit tests run, 0 failures / 0 errors.
- **Wide Failsafe pass:** Discord*IT + Backup*IT + key MatchControllerIT
  + StandingsServicePhaseScopedStaleDetectionIT —
  all passing (modulo MariaDb-roundtrip skips that require Testcontainers
  Docker — not regressions).
- **SpotBugs:** 0 bugs, 0 errors.
- **Marker-pollution grep over the 64 Plan-102-02-touched files:** **0**
  hits. Remaining 70 marker-lines elsewhere in repo (mostly migrations +
  Thymeleaf templates) are Plan 102-03 Task 1's blanket-sweep scope.
- **`./mvnw clean verify -Pe2e`** intentionally deferred to Plan 102-04
  per CONTEXT D-11.

## Post-review fold-back — 2 critical + 11 warning + 8 info findings closed

Code-review of the post-regression tree (`102-02-REVIEW.md`) surfaced 2 CR + 11 WR + 8 IN findings. Per user direction "ALL Code Review Funde beheben, nichts übergangen", all 21 were folded back into Plan 102-02 — no deferrals.

- **CR-01 — WR-thin-3 closed for real.** `MatchdayController.populateMatchdayDiscordModel` (75 lines) and its 4 inline `standingsService.is*Stale(...)` calls extracted to new admin-layer view service `org.ctc.admin.service.DiscordMatchdayViewService.buildMatchdayDiscordModel(Matchday)`. The controller's `detail()` GET is now 12 lines of model population (well under the ≤20 plan criterion). `snapshotMatchdayStaleness` is now invoked from the production path (the view service), no longer dead code. Regression coverage: `MatchdayControllerPostEndpointsIT.givenThreadAndWebhookConfigured_whenDetailGet_thenModelHasDiscordEnrichment` exercises the wiring.

- **CR-02 — Legacy STANDINGS phase_id backfill moved to Flyway V16.** New migration `V16__backfill_standings_phase_id.sql` runs once at startup and back-fills every legacy `discord_post` STANDINGS row whose `phase_id IS NULL` to the season's REGULAR phase. `DiscordSeasonViewService.lookupPhaseScopedStandings` is now a pure read, no longer writes during GET-render. New unit test `givenAnyState_whenBuildDiscordIntegrationModel_thenNoDiscordPostWrites` asserts zero `discordPostRepository.save(...)` calls on the happy path.

- **WR-01 / WR-02 / IN-02** — unused imports dropped from `MatchdayController`, `SeasonController`, and the same-package `DiscordEmojiCache` import removed from `DiscordDevSeeder`.

- **WR-03** — `ScoringService.recomputeMatchScoresFromAllLegs` now mirrors `aggregateMatchScores`' playoff branch (`PlayoffMatchup` recomputed via `raceRepository.findByPlayoffMatchupId`). New test `givenClearedPlayoffRace_whenRecomputeMatchScoresFromAllLegs_thenPlayoffMatchupScoresRecomputedFromRemainingLegs`.

- **WR-04 / WR-08 / IN-03** — `DiscordRoleCache` now uses a `volatile Map<String, CachedEntry<Role>>` reference (true atomic swap; closes both no-roles and stale-roles windows). `snapshot()` drops the intermediate `LinkedHashMap` and streams straight to `Collectors.toUnmodifiableMap`; one-line Javadoc added. `refresh(List<Role>)` is now a single volatile write.

- **WR-05** — `MatchService.buildMatchDetailModel` guards the 3 `matchHas*` preflight calls behind `match.getDiscordChannelId() != null`. Matches without a Discord channel no longer trigger per-race lineup queries. New test `givenMatchWithoutDiscordChannel_whenBuildMatchDetailModel_thenPreflightCallsSkippedAndFlagsFalse`.

- **WR-06** — `DiscordPostService.unarchiveIfArchived` now throws `BusinessRuleException("Forum thread {id} is still archived…")` instead of proceeding with the post attempt when Discord still reports `archived=true` after the PATCH. New IT case `givenArchivedThreadStaysArchived_whenPostRaceResultToForumThread_thenThrowsBusinessRuleException`.

- **WR-07** — `DiscordChannelService.parseAllow` now catches `NumberFormatException`, logs a warning, and returns `0L` (fail-closed). New IT case `givenFetchChannelReturnsOverwriteWithNonNumericAllow_whenAudit_thenTreatedAsZeroNotThrown`.

- **WR-09** — `DiscordSeasonViewService` disabled-branch sentinel maps collapsed into a single iteration: `channelId` is `null` when disabled, `lookupPhaseScopedStandings` is skipped, both maps fill via the same loop.

- **WR-10** — Covered by CR-02 move. V16 migration handles every season with a REGULAR phase, not just one phase type at request time.

- **WR-11 + IN-04** — `ScoringService` — the orphaned `aggregateMatchScores` Javadoc now sits directly above the method it documents; `recomputeMatchScoresFromAllLegs` keeps its own contract Javadoc. `MatchService.buildMatchDetailModel` switched `LinkedHashMap` → `HashMap` (no consumer depends on iteration order; Spring `addAllAttributes` uses its own `LinkedHashMap` internally).

- **IN-01** — `CsvImportControllerExceptionTest` Javadoc: `T-91-02-IL invariant` → `typed-catch info-leak invariant`.

- **IN-05** — `DiscordPostService.escapeMarkdownLinkUrl` now escapes `)`, `>`, and `<` per Discord markdown spec; visibility relaxed to package-private. New unit test `DiscordPostServiceEscapeMarkdownLinkUrlTest`.

- **IN-06** — `BackupSchema.pinFkEntitiesLast` visibility relaxed to package-private; new unit test `BackupSchemaPinFkEntitiesLastTest` exercises a synthetic 2-entity FK tail set + a no-match case.

- **IN-07** — `BackupArchiveService.openHardened` renamed to `openZipInputStream` (the original name promised hardening the method doesn't perform; per-entry guards live in `assertEntrySafe`). All 4 call sites updated.

- **IN-08** — `BackupExportService.lookupRepository` error message now appends the sorted list of registered entity simple names, so the operator can see at a glance which entity is missing.

**Verification:** `./mvnw clean verify` exit 0 — Surefire 1293 / 0 failures / 0 errors; Failsafe 491 / 0 / 0; JaCoCo 88% instruction coverage (above the 82% gate); SpotBugs clean.

## Inadvertent comment-marker pollution + cleanup

Tasks 5-7 introduced 10 WR-/Phase-marker comments in test files as
"closure justifications" — exactly the pattern this milestone is meant
to remove. Cleanup commit `55b3a360` removed all 10 markers across 7
test files; a new memory entry
`feedback_no_wr_markers_in_review_fix_tests.md` was added to prevent
recurrence.

## Files modified

```
git diff --name-only a45b848d..HEAD -- 'src/main/**' 'src/test/**' | wc -l
# 64 source files
```

(Full list available via the command above; orchestrator-level review
should consume the 64-file diff.)

## Acceptance gate

Orchestrator-level `/gsd-code-review 102 --plan=02` ran on the
post-regression tree and produced `102-02-REVIEW.md` (2 critical + 11
warnings + 8 info). All 21 findings were folded back into Plan 102-02
in a follow-up commit; verify gate passed. Plan 102-02 closeout unblocks
Plan 102-03 info-sweep.
