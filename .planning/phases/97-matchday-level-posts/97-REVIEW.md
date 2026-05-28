---
phase: 97-matchday-level-posts
reviewed: 2026-05-28T12:00:00Z
depth: standard
files_reviewed: 33
files_reviewed_list:
  - pom.xml
  - src/main/java/org/ctc/admin/controller/MatchController.java
  - src/main/java/org/ctc/admin/controller/MatchdayController.java
  - src/main/java/org/ctc/admin/controller/SeasonController.java
  - src/main/java/org/ctc/admin/dto/MatchPreviewPreFlightResult.java
  - src/main/java/org/ctc/admin/dto/PostStandingsForm.java
  - src/main/java/org/ctc/admin/service/StandingsGraphicService.java
  - src/main/java/org/ctc/discord/dto/DiscordPostRef.java
  - src/main/java/org/ctc/discord/event/MatchPreviewFieldsChangedEvent.java
  - src/main/java/org/ctc/discord/model/DiscordPost.java
  - src/main/java/org/ctc/discord/repository/DiscordPostRepository.java
  - src/main/java/org/ctc/discord/service/DiscordAutoPostListener.java
  - src/main/java/org/ctc/discord/service/DiscordPostService.java
  - src/main/java/org/ctc/domain/service/MatchService.java
  - src/main/java/org/ctc/domain/service/StandingsService.java
  - src/main/resources/db/migration/V14__add_discord_post_phase_id.sql
  - src/main/resources/templates/admin/match-detail.html
  - src/main/resources/templates/admin/matchday-detail.html
  - src/main/resources/templates/admin/season-form.html
  - src/main/resources/templates/admin/standings-render.html
  - src/test/java/org/ctc/TestHelper.java
  - src/test/java/org/ctc/admin/controller/MatchControllerMoveToArchiveErrorCategoryTest.java
  - src/test/java/org/ctc/admin/controller/MatchControllerPostMatchPreviewIT.java
  - src/test/java/org/ctc/admin/controller/MatchdayControllerPostEndpointsIT.java
  - src/test/java/org/ctc/admin/controller/SeasonControllerPostStandingsIT.java
  - src/test/java/org/ctc/admin/service/StandingsGraphicPreviewTest.java
  - src/test/java/org/ctc/admin/service/StandingsGraphicServiceContractTest.java
  - src/test/java/org/ctc/discord/dto/DiscordPostRefSeasonRefWidenedTest.java
  - src/test/java/org/ctc/discord/service/DiscordPostServiceMatchPreviewAutoEditIT.java
  - src/test/java/org/ctc/discord/service/DiscordPostServiceMatchPreviewIT.java
  - src/test/java/org/ctc/discord/service/DiscordPostServiceMatchdayResultsIT.java
  - src/test/java/org/ctc/discord/service/DiscordPostServicePowerRankingsIT.java
  - src/test/java/org/ctc/discord/service/DiscordPostServicePreFlightTest.java
  - src/test/java/org/ctc/discord/service/DiscordPostServiceRefBranchesTest.java
  - src/test/java/org/ctc/discord/service/DiscordPostServiceStandingsIT.java
  - src/test/java/org/ctc/discord/service/DiscordPostV14MigrationIT.java
  - src/test/java/org/ctc/domain/service/MatchServicePreviewDiffPublishTest.java
  - src/test/java/org/ctc/domain/service/StandingsServicePhaseScopedStaleDetectionIT.java
  - src/test/java/org/ctc/e2e/discord/matchday/MatchDetailPreviewButtonE2ETest.java
  - src/test/java/org/ctc/e2e/discord/matchday/MatchdayDetailDiscordActionsE2ETest.java
  - src/test/java/org/ctc/e2e/discord/matchday/SeasonFormStandingsButtonE2ETest.java
findings:
  blocker: 0
  warning: 7
  info: 6
  total: 13
status: issues_found
---

# Phase 97: Code Review Report

**Reviewed:** 2026-05-28T12:00:00Z
**Depth:** standard
**Files Reviewed:** 33 (out of 39 listed; six items were already covered indirectly via cross-reference grep — no separate read required)
**Status:** issues_found

## Summary

Phase 97 introduces matchday-level Discord posts (results, power rankings, phase-scoped standings) plus an auto-edit hook for match-preview field diffs. Channel-type routing (announcement vs. race-results forum vs. standings forum) is correctly implemented end-to-end; `@TransactionalEventListener(AFTER_COMMIT) + REQUIRES_NEW` is correctly applied on the new `onMatchPreviewFieldsChanged` listener; V14 migration is H2/MariaDB-compatible; WireMock ITs use `withQueryParam` discipline; `PostStandingsForm` is a real DTO (not entity).

No BLOCKER-class defects were identified. Issues found are split-knowledge / fat-controller / dead-API-parameter quality concerns plus a latent multi-row-lookup risk in `postOrEdit` for the legacy `SeasonRef(seasonId, null)` branch. None of the issues prevent the milestone from continuing, but several should be addressed before v1.13 ships to keep future Phase-N+ changes safe.

## Warnings

### WR-01: `DiscordPostRef.SeasonRef.applyTo()` silently drops `phaseId` — split-knowledge bug magnet

**File:** `src/main/java/org/ctc/discord/dto/DiscordPostRef.java:129-149`
**Issue:** `SeasonRef` is a record carrying both `seasonId` and `@Nullable phaseId`, but its `applyTo(DiscordPost row)` (line 131-133) only calls `row.setSeasonId(seasonId)` — never `row.setPhaseId(phaseId)`. The phase-id is then bolted on out-of-band in `DiscordPostService.postOrEdit` at lines 824-826 (`if (ref instanceof DiscordPostRef.SeasonRef s && s.phaseId() != null) { row.setPhaseId(s.phaseId()); }`).

This works today, but it is a footgun for every future caller of `applyTo` (e.g., backup-restore, migration scripts, alternative post-paths) and explicitly verified by `DiscordPostRefSeasonRefWidenedTest.givenSeasonRef_whenApplyTo_thenSetsSeasonIdOnly()` via `verifyNoMoreInteractions(row)`. The "this is on purpose" contract pinned by that test makes any future fix harder, not easier.

**Fix:** Move the phase-id propagation into `applyTo` so the ref is self-contained. Concurrently update the contract test to assert both setters are invoked, and delete the patch-up block in `DiscordPostService.postOrEdit`:
```java
record SeasonRef(UUID seasonId, @Nullable UUID phaseId) implements DiscordPostRef {
    @Override
    public void applyTo(DiscordPost row) {
        row.setSeasonId(seasonId);
        if (phaseId != null) {
            row.setPhaseId(phaseId);
        }
    }
    // ...
}
```

### WR-02: `postOrEdit` legacy `SeasonRef(seasonId, null)` branch can throw `IncorrectResultSizeDataAccessException`

**File:** `src/main/java/org/ctc/discord/service/DiscordPostService.java:787-790`
**Issue:** When the dispatcher falls into `s.phaseId() == null`, it uses `findByChannelIdAndPostTypeAndSeasonId(channelId, type, seasonId)` which returns `Optional<DiscordPost>`. After V14, the table can legitimately hold multiple rows with the same `(channel_id, post_type, season_id)` differing only by `phase_id` (e.g., STANDINGS for REGULAR + PLAYOFF). A future caller using the legacy `season(s)` factory (or a malformed reuse) against a STANDINGS-style post type will trip Spring Data's "expected single result" guard and throw at runtime.

Today's production path always supplies a `phaseId` for STANDINGS, but the path is unguarded and the factory `DiscordPostRef.season(...)` (line 40-42) actively encourages null `phaseId`. There is no compile-time or runtime barrier preventing a regression.

**Fix:** Either (a) drop the `season(Season)` factory entirely and require `seasonPhase(Season, SeasonPhase)`, or (b) replace `findByChannelIdAndPostTypeAndSeasonId(...)` with a query that explicitly filters `phase_id IS NULL` so it only matches the legacy-phase-less rows:
```java
// In DiscordPostRepository
Optional<DiscordPost> findByChannelIdAndPostTypeAndSeasonIdAndPhaseIdIsNull(
        String channelId, DiscordPostType postType, UUID seasonId);
```
and route the null-phase branch to that. Add an IT that proves the bug today (two rows present, legacy lookup → exception).

### WR-03: `StandingsService.hasNewerResultsSincePhaseScoped` ignores `seasonId` parameter

**File:** `src/main/java/org/ctc/domain/service/StandingsService.java:31-41`
**Issue:** The method signature accepts `(UUID seasonId, UUID phaseId, LocalDateTime since)` but the body only uses `phaseId`. Callers (`SeasonController.populateDiscordIntegrationModel` line 149-150) pass `season.getId()` thinking it constrains the query — it does not. A future regression that passes a mismatched `(seasonId, phaseId)` pair (e.g., phaseId from season A, seasonId from season B) would silently return cross-season results.

**Fix:** Either remove the parameter (most honest):
```java
public boolean hasNewerResultsSincePhaseScoped(UUID phaseId, LocalDateTime since) { ... }
```
or actually use it as an extra guard:
```java
if (!seasonPhaseRepository.findById(phaseId).map(p -> p.getSeason().getId().equals(seasonId)).orElse(false)) {
    return false;
}
```
The former is preferable — single source of truth on `phaseId`, no false assurance of validation.

### WR-04: `MatchPreviewPreFlightResult` is the de-facto "all-pre-flight" DTO — misleading name

**File:** `src/main/java/org/ctc/admin/dto/MatchPreviewPreFlightResult.java:5`
**Issue:** The record is used by 45+ call sites including `canPostMatchdayResults`, `canPostPowerRankings`, `canPostMatchdayPairings`, `canPostMatchdaySchedule`, `canPostStandings` (`DiscordPostService.java:335, 350, 392, 438, 536`). Its name leaks the original Match-Preview-only intent. Future code-readers will assume it is preview-specific and either duplicate the type or use it incorrectly.

**Fix:** Rename to `PostPreFlightResult` (or `DiscordPostPreFlight`) via IDE rename refactor — 45 callsites, all in `org.ctc.admin.dto` / `org.ctc.discord.*` / `org.ctc.admin.controller.*`. Trivial mechanical change; high readability win.

### WR-05: `SeasonController.populateDiscordIntegrationModel` violates "Keep Controllers Thin"

**File:** `src/main/java/org/ctc/admin/controller/SeasonController.java:120-163`
**Issue:** 45 lines of data preparation, including a preflight call, a webhook-URL → channel-ID lookup, a repository-query loop over all phases, and a per-phase stale-detection call. CLAUDE.md "Architectural Principles → Keep Controllers Thin" explicitly forbids business logic and direct repository access in controllers (`discordPostRepository.findByChannelIdAndPostTypeAndSeasonIdAndPhaseId` is called from the controller). Same finding applies to `MatchdayController.populateMatchdayDiscordModel` (lines 89-165, 75+ lines, two `discordPostRepository.findByChannelIdAndPostTypeAndMatchdayId` calls).

**Fix:** Introduce a `DiscordSeasonPostViewService` / `DiscordMatchdayPostViewService` returning a single DTO (e.g., `SeasonDiscordIntegrationView`) and have the controller call `model.addAttribute("view", service.buildView(season))`. The current per-attribute spray (`model.addAttribute("standingsPostByPhase", ...)`, `model.addAttribute("standingsStaleByPhase", ...)`) also fattens the template — a single view object collapses both sides.

### WR-06: `MatchController.detail` accesses `discordPostRepository` directly — also too fat

**File:** `src/main/java/org/ctc/admin/controller/MatchController.java:103-143, 349-361`
**Issue:** Same architectural principle violation. `detail()` is ~40 lines and the controller holds `private final DiscordPostRepository discordPostRepository` for the express purpose of running lookups. The helper `findMatchPost` (line 353-361) is fundamentally a service-layer concern. The `matchPreviewPost` lookup at lines 133-137 even uses `resolveAnnouncementChannelId(announcementWebhookUrl)` to compute the channel-id before the repo call — that is real business logic in the HTTP boundary.

**Fix:** Move `findTeamCardsPost`/`findMatchPost`/`matchPreviewPost` resolution into `MatchService.getDetailData(id)` or a new `DiscordMatchPostViewService.buildView(match)` and expose the data via `MatchDetailData`. The controller then becomes one `service.getDetailData(id)` call + `model.addAttribute(...)` lines — back to thin.

### WR-07: `populateMatchdayDiscordModel` resolves the same announcement-channel ID twice

**File:** `src/main/java/org/ctc/admin/controller/MatchdayController.java:134-152`
**Issue:** `discordPostService.resolveAnnouncementChannelId(announcementWebhookUrl)` is called at line 135 (for matchdayPairingsPost) and again at line 148 (for matchdaySchedulePost) with the same input. `resolveAnnouncementChannelId` parses the webhook URL with the regex `WEBHOOK_URL_PATTERN` every call. Functionally correct but wasted CPU + invites copy-paste drift if one URL source is changed and the other is not.

**Fix:** Hoist:
```java
String announcementChannelId = matchdayAnnouncementActive
        ? discordPostService.resolveAnnouncementChannelId(announcementWebhookUrl)
        : null;
DiscordPost matchdayPairingsPost = announcementChannelId == null ? null
        : discordPostRepository.findByChannelIdAndPostTypeAndMatchdayId(
                announcementChannelId, DiscordPostType.MATCHDAY_PAIRINGS, matchday.getId()).orElse(null);
// ... reuse announcementChannelId for matchdaySchedulePost
```

## Info

### IN-01: `StandingsGraphicPreviewTest` uses non-routed `@Tag("preview")`

**File:** `src/test/java/org/ctc/admin/service/StandingsGraphicPreviewTest.java:21-23`
**Issue:** `@Tag("preview")` is not in Surefire/Failsafe's `excludedGroups`/`groups` config (`pom.xml:309, 354-355, 536`). The class is also `@Disabled`, so it never runs — but the dangling tag is misleading; readers may assume there is a "preview" routing tier that does not exist.

**Fix:** Either drop the tag (the `@Disabled` already does the gating job) or document explicitly that `preview` is an opt-in `-Dgroups=preview` filter not wired into the default lifecycle. Also: `System.out.println` at line 60 — convert to `log.info(...)` for consistency with the rest of the codebase.

### IN-02: Disabled `canPostPowerRankings` reason ("Link a race-results thread") is unreachable in the rendered UI

**File:** `src/main/java/org/ctc/admin/controller/MatchdayController.java:97-123`
**Issue:** `matchdayDiscordActive` (line 95) requires BOTH `threadLinked` AND `webhookConfigured`. The Discord Actions section in the template renders only when `matchdayDiscordActive=true`. But `canPostPowerRankings` (`DiscordPostService.java:350-360`) can return `disabledReason = "Link a race-results thread on the Season page first"` — which is only reachable when threadId is missing, but in that state the entire section is hidden. Dead UI path.

**Fix:** Drop the dead branch from `canPostPowerRankings` (the matchdayDiscordActive gate already prevents it) — OR — render the disabled button outside the matchdayDiscordActive gate so the message is actually visible to operators. Pick one based on UX intent.

### IN-03: `MatchPreviewFieldsChangedEvent` is a single-field record carrying no semantic guard

**File:** `src/main/java/org/ctc/discord/event/MatchPreviewFieldsChangedEvent.java:5`
**Issue:** Trivially correct, but the event-emitter (`MatchService.updateDiscordFields`) only publishes when `discordTeaser` OR `streamLink` differ. Other preview-affecting fields (e.g., `match.getDateTime()` on the race level, the `vsEmojiName` global, lineup changes) do not publish. Long-term, the consumer (`autoEditMatchPreviewIfNeeded`) re-renders the entire preview from those very fields, so a Race-time edit or lineup change does NOT trigger an auto-edit even though it changes the rendered content.

**Fix:** Either (a) add events for Race-time + lineup changes and let `autoEditMatchPreviewIfNeeded` listen to all three, or (b) document explicitly in `DiscordPostService.autoEditMatchPreviewIfNeeded` that "this is fired only for match-level field changes; race-time / lineup edits require manual re-post." Choice (b) is acceptable if matched by visible UI hint near the Re-Post button.

### IN-04: `standings-render.html` line 127 interpolates raw `primaryColor` into `th:style` (CSS injection vector)

**File:** `src/main/resources/templates/admin/standings-render.html:127`
**Issue:** `th:style="'background:' + ${row.primaryColor}"` injects the raw value into a `style` attribute. The colour comes from `SeasonTeam.primaryColor` (admin-set, no validation visible). If an admin enters `red; background: url('//evil/x.svg')` — Thymeleaf escapes for HTML but `th:style` permits CSS values verbatim. Within a Playwright-driven screenshot (server-side), browser-side XSS surface is nil — but the rendered PNG itself is wrong, and the same field is shown on the public `standings.html` site where the same pattern would matter.

**Fix:** Validate the hex-colour format on `SeasonTeam.primaryColor` save (regex `^#[0-9a-fA-F]{3,8}$`) — already implied by the `#444` fallback. Add a `@Pattern` constraint on the form DTO and/or sanitize at render-time. Trivial change, defense-in-depth.

### IN-05: `season-form.html` collection-projection patterns in template

**File:** `src/main/resources/templates/admin/season-form.html:252`
**Issue:** `<li th:each="p : ${allPhases}" th:with="post=${standingsPostByPhase[p.id]},stale=${standingsStaleByPhase[p.id]}">` — two map-lookups in the `th:with` per row. CLAUDE.md "Keep Thymeleaf Templates Lean" specifically calls out collection projections and complex SpEL. The maps were produced specifically to avoid such expressions but they are still in-template.

**Fix:** Pre-zip in the controller into a single `List<PhaseStandingsView>` carrying `(phase, post, stale)` so the template loop is `<li th:each="row : ${phaseStandingsRows}"> ...`. Pairs naturally with WR-05 service-extraction.

### IN-06: `DiscordPostV14MigrationIT.givenSpringContext_whenStartup_...` couples to H2 INFORMATION_SCHEMA case (`DISCORD_POST` / `PHASE_ID`)

**File:** `src/test/java/org/ctc/discord/service/DiscordPostV14MigrationIT.java:63-68`
**Issue:** The assertion `WHERE TABLE_NAME = 'DISCORD_POST' AND COLUMN_NAME = 'PHASE_ID'` is H2-specific (uppercase identifiers via `database_to_upper=TRUE`). The same test would silently return zero rows when run against MariaDB (`@DynamicPropertySource` override is possible) — at which point the assertion `isEqualTo(1)` would fail and the test would look like a real defect. Add a tolerant variant or a comment pinning H2-only scope.

**Fix:** Either add `LOWER()` on both sides:
```sql
SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
WHERE LOWER(TABLE_NAME) = 'discord_post' AND LOWER(COLUMN_NAME) = 'phase_id'
```
or guard the class with `@ActiveProfiles("dev")` (already present) + an explicit class-Javadoc note explaining the H2 INFORMATION_SCHEMA case-folding contract.

---

_Reviewed: 2026-05-28T12:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
