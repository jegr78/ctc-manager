---
phase: 95-match-channel-posts
reviewed: 2026-05-28T00:00:00Z
depth: standard
files_reviewed: 84
files_reviewed_list:
  - docker-compose.prod.yml
  - docker-compose.yml
  - pom.xml
  - src/main/java/org/ctc/admin/TestDataService.java
  - src/main/java/org/ctc/admin/controller/MatchController.java
  - src/main/java/org/ctc/admin/controller/MatchdayController.java
  - src/main/java/org/ctc/admin/controller/RaceController.java
  - src/main/java/org/ctc/admin/controller/SeasonController.java
  - src/main/java/org/ctc/admin/dto/MatchPreviewPreFlightResult.java
  - src/main/java/org/ctc/admin/dto/PostStandingsForm.java
  - src/main/java/org/ctc/admin/dto/SeasonForm.java
  - src/main/java/org/ctc/admin/service/PlaywrightScreenshotter.java
  - src/main/java/org/ctc/admin/service/ProvisionalScoresGraphicService.java
  - src/main/java/org/ctc/admin/service/ResultsGraphicService.java
  - src/main/java/org/ctc/admin/service/StandingsGraphicService.java
  - src/main/java/org/ctc/admin/service/TeamCardService.java
  - src/main/java/org/ctc/discord/DiscordDevSeedProperties.java
  - src/main/java/org/ctc/discord/DiscordDevSeeder.java
  - src/main/java/org/ctc/discord/DiscordPermissions.java
  - src/main/java/org/ctc/discord/DiscordWebhookClient.java
  - src/main/java/org/ctc/discord/dto/Channel.java
  - src/main/java/org/ctc/discord/dto/ChannelModifyRequest.java
  - src/main/java/org/ctc/discord/dto/DiscordConfigForm.java
  - src/main/java/org/ctc/discord/dto/DiscordPostFilterForm.java
  - src/main/java/org/ctc/discord/dto/DiscordPostRef.java
  - src/main/java/org/ctc/discord/dto/Thread.java
  - src/main/java/org/ctc/discord/dto/ThreadMetadata.java
  - src/main/java/org/ctc/discord/dto/WebhookPayload.java
  - src/main/java/org/ctc/discord/event/ChannelCreatedEvent.java
  - src/main/java/org/ctc/discord/event/MatchPreviewFieldsChangedEvent.java
  - src/main/java/org/ctc/discord/event/MatchScheduleFieldsChangedEvent.java
  - src/main/java/org/ctc/discord/exception/DiscordApiException.java
  - src/main/java/org/ctc/discord/exception/DiscordApiExceptionMapper.java
  - src/main/java/org/ctc/discord/exception/DiscordMissingPermissionsException.java
  - src/main/java/org/ctc/discord/model/DiscordGlobalConfig.java
  - src/main/java/org/ctc/discord/model/DiscordPost.java
  - src/main/java/org/ctc/discord/model/DiscordPostType.java
  - src/main/java/org/ctc/discord/repository/DiscordPostRepository.java
  - src/main/java/org/ctc/discord/service/DiscordAutoPostListener.java
  - src/main/java/org/ctc/discord/service/DiscordChannelService.java
  - src/main/java/org/ctc/discord/service/DiscordForumService.java
  - src/main/java/org/ctc/discord/service/DiscordGlobalConfigService.java
  - src/main/java/org/ctc/discord/service/DiscordPostService.java
  - src/main/java/org/ctc/discord/web/DiscordConfigController.java
  - src/main/java/org/ctc/discord/web/DiscordPostController.java
  - src/main/java/org/ctc/domain/model/Match.java
  - src/main/java/org/ctc/domain/model/Season.java
  - src/main/java/org/ctc/domain/service/MatchService.java
  - src/main/java/org/ctc/domain/service/RaceService.java
  - src/main/java/org/ctc/domain/service/SeasonManagementService.java
  - src/main/java/org/ctc/domain/service/StandingsService.java
  - src/main/resources/application-dev.yml
  - src/main/resources/db/migration/V11__add_matches_discord_channel_archived_at.sql
  - src/main/resources/db/migration/V12__discord_post.sql
  - src/main/resources/db/migration/V13__add_seasons_discord_threads_and_forum_webhooks.sql
  - src/main/resources/db/migration/V14__add_discord_post_phase_id.sql
  - src/main/resources/templates/admin/discord-config.html
  - src/main/resources/templates/admin/discord-posts.html
  - src/main/resources/templates/admin/match-detail.html
  - src/main/resources/templates/admin/matchday-detail.html
  - src/main/resources/templates/admin/race-detail.html
  - src/main/resources/templates/admin/season-form.html
  - src/main/resources/templates/admin/season-detail.html
  - src/main/resources/templates/admin/standings-render.html
  - src/main/resources/templates/admin/provisional-scores-render.html
  - src/test/java/db/migration/V13MigrationIT.java
  - src/test/java/org/ctc/admin/controller/MatchControllerPostMatchPreviewIT.java
  - src/test/java/org/ctc/admin/controller/MatchControllerPostSettingsPreFlightIT.java
  - src/test/java/org/ctc/admin/controller/RaceControllerPostRaceResultToForumIT.java
  - src/test/java/org/ctc/admin/controller/SeasonControllerPostStandingsIT.java
  - src/test/java/org/ctc/discord/DiscordPermissionsTest.java
  - src/test/java/org/ctc/discord/DiscordWebhookClientIT.java
  - src/test/java/org/ctc/discord/dto/DiscordPostRefSeasonRefWidenedTest.java
  - src/test/java/org/ctc/discord/exception/DiscordApiExceptionMapperTest.java
  - src/test/java/org/ctc/discord/service/DiscordAutoPostListenerIT.java
  - src/test/java/org/ctc/discord/service/DiscordChannelArchiveServiceWireMockIT.java
  - src/test/java/org/ctc/discord/service/DiscordForumServiceIT.java
  - src/test/java/org/ctc/discord/service/DiscordPostServiceForumThreadIT.java
  - src/test/java/org/ctc/discord/service/DiscordPostServiceMatchPreviewAutoEditIT.java
  - src/test/java/org/ctc/discord/service/DiscordPostServiceScheduleIT.java
  - src/test/java/org/ctc/discord/service/DiscordPostServiceStandingsIT.java
  - src/test/java/org/ctc/domain/service/MatchServiceScheduleEditHookIT.java
findings:
  critical: 2
  warning: 8
  info: 5
  total: 15
status: issues_found
---

# Phase 95: Code Review Report

**Reviewed:** 2026-05-28
**Depth:** standard
**Files Reviewed:** 84 (subset of 133 milestone files — prod-code first, high-signal tests sampled)
**Status:** issues_found

## Summary

Phase 95 lands a large surface (MatchService schedule/preview edit hooks, DiscordPostService with 10+ post types, DiscordForumService, V13/V14 migrations, 5+ controllers, 4 Thymeleaf graphics templates). The `@TransactionalEventListener AFTER_COMMIT` discipline from the v1.13 fallback memory is correctly applied in `DiscordAutoPostListener` — the listener fires `REQUIRES_NEW` after outer-tx commit, swallows API and runtime exceptions, and records a request-scoped error attribute that the controller surfaces as a flash. WireMock ITs for the schedule and preview hooks correctly run the test without `@Transactional` so the AFTER_COMMIT phase actually fires; the controller IT for preview directly invokes the service so its `@Transactional` annotation is fine.

Two BLOCKER findings:

1. **`DiscordPostController.matchLabel` NPEs on bye matches** — the `/admin/discord/posts` page calls `m.getAwayTeam().getShortName()` for every match without bye check, will 500 as soon as any season has a bye.
2. **No event-driven re-edit when a race's `dateTime` is changed** — the schedule embed prominently shows "Date: …" derived from `firstRaceTime(match)`, but `RaceService.saveRace` (which writes `race.dateTime`) does not publish any event. Only `MatchService.updateDiscordFields` (which covers lobby host / race director / streamer) wires the AFTER_COMMIT hook. Operators editing a race time will silently leave the schedule post stale until manual Re-Post.

Eight WARNINGs cluster around: missing matchday/race index ordering for forum-result filenames, dead `SeasonForm` fields, no aggregation guard when `saveResults` is called with empty list, OSIV-leaning controller logic (>60 lines of staleness checks in `MatchdayController`), pre-V14 `STANDINGS` post lookup miss, asymmetric `DiscordPostRef` apply/lookup. Info items are minor (one D-96 comment in test, ambiguous error category mapping, `Thread` class-name shadowing).

## Critical Issues

### CR-01: `DiscordPostController.matchLabel` NPEs on bye matches

**File:** `src/main/java/org/ctc/discord/web/DiscordPostController.java:71`

**Issue:** The match label helper unconditionally calls `m.getAwayTeam().getShortName()`. `Match.awayTeam` is nullable (`@JoinColumn(name = "away_team_id")` without `nullable=false`, see `Match.java:36-38`) — a bye match has `awayTeam=null`. This helper feeds the matches dropdown and the table rows on `/admin/discord/posts` via `matchLabels` and the controller calls it on `matchRepository.findAll()` — so a single bye match anywhere in the system makes the entire posts list page throw NPE on render. This page is also the only `/admin/discord/posts` endpoint, so the regression is unrecoverable without code change.

The same defensive treatment is already applied elsewhere (see `MatchController.detail` line 107, `RaceService` line 290, `matchday-detail.html` line 59 — all use `awayTeam != null ? … : 'Bye'`).

**Fix:**
```java
private static String matchLabel(Match m) {
    String away = m.getAwayTeam() != null ? m.getAwayTeam().getShortName() : "Bye";
    return m.getMatchday().getSeason().getYear() + " | " + m.getMatchday().getLabel()
            + " | " + m.getHomeTeam().getShortName() + " vs. " + away;
}
```

Add an IT that seeds a bye match and renders `/admin/discord/posts` — WireMock-only coverage missed this.

### CR-02: Race `dateTime` edits do not trigger auto re-edit of the SCHEDULE post

**File:** `src/main/java/org/ctc/domain/service/RaceService.java:187`, `src/main/java/org/ctc/domain/service/MatchService.java:68-96`

**Issue:** `DiscordPostService.buildSchedulePayload` (DiscordPostService.java:629-639) shows the schedule embed's `Date` field derived from `firstRaceTime(match)` (the earliest non-null `race.dateTime`). The auto-edit hook `autoEditScheduleIfNeeded` is correct in itself, but the only publisher of `MatchScheduleFieldsChangedEvent` is `MatchService.updateDiscordFields`, which is invoked from `/admin/matches/{id}/save-edit` (MatchForm fields only: `discordTeaser`, `streamLink`, `lobbyHost`, `raceDirector`, `streamer`). `RaceService.saveRace` (`/admin/races/save`) writes `race.setDateTime(dateTime)` at line 187 with no event publication.

Consequence: when an operator changes the race time on the race-edit form, the Discord schedule post's "Date: …" field becomes stale forever. The UI has no "schedule stale" indicator for race-time drift on the match-detail page (the existing `matchResultsStale` indicator tracks `RaceResult.updatedAt`, not `Race.dateTime`). The user only sees the stale date when they manually navigate Discord. Even the manual "Re-Post Schedule" requires the user to know the data drifted.

This is the exact class of bug the fallback memory `project_phase_95_auto_post_hook_fallback.md` warned about — Phase 95 missed it because no IT seeds a race, publishes the event, and asserts the WireMock patch fires after dateTime change.

**Fix:** Publish a `MatchScheduleFieldsChangedEvent` from `RaceService.saveRace` whenever `dateTime` changes (or the home/away team override changes). Use `Objects.equals(beforeDateTime, dateTime)` guard to avoid noop fires. Tests:
- Add `RaceServiceSaveRaceScheduleHookIT` with `@SpringBootTest` (no `@Transactional`) that updates a race time, asserts WireMock patch fires once.
- Add `RaceServiceSaveRaceScheduleHookIT.givenSameDateTime_whenSave_thenNoPatch` to verify the noop guard.

```java
// RaceService.saveRace, after race = repository.findById(id).orElseThrow():
LocalDateTime beforeDateTime = race.getDateTime();
// ... existing setters ...
raceRepository.save(race);
if (!Objects.equals(beforeDateTime, dateTime) && race.getMatch() != null) {
    eventPublisher.publishEvent(new MatchScheduleFieldsChangedEvent(race.getMatch().getId()));
}
```

(Requires injecting `ApplicationEventPublisher` into `RaceService`.)

## Warnings

### WR-01: `postRaceResultToForumThread` uses unstable matchday-wide race index for filename

**File:** `src/main/java/org/ctc/discord/service/DiscordPostService.java:867`

**Issue:** `int raceNumber = race.getMatchday().getRaces().indexOf(race) + 1;` indexes into `Matchday.races` which has no `@OrderBy` annotation (see `Matchday.java:50-51`). Hibernate returns rows in arbitrary order. For a multi-match matchday this is the index across ALL matches' races, not the leg number within the match. Two consequences:

1. Filename `race-result-{matchdayLabel}-race-{N}.png` is non-deterministic between runs / DB engines (H2 vs MariaDB) and can collide between separate matches in the same matchday.
2. On Re-Post, the filename may differ from the original Discord attachment — confusing the UI and breaking attachment-replace semantics.

**Fix:** Use the race's leg index within its own match (already ordered by `dateTime ASC NULLS LAST`):
```java
int raceNumber = race.getMatch() != null
        ? race.getMatch().getRaces().indexOf(race) + 1
        : 1;
```
Or — better — include the match short-name slug in the filename: `race-result-{matchdayLabel}-{home}-vs-{away}-leg-{N}.png`.

### WR-02: `RaceService.saveResults` does not aggregate when given empty results list

**File:** `src/main/java/org/ctc/domain/service/RaceService.java:242-269`

**Issue:** When `results` is `List.of()` (e.g., admin clears all results via the form), the method clears the existing results, flushes, skips the `for` loop entirely, then calls `raceRepository.save(race)` and `scoringService.aggregateMatchScores(race)`. The `Match.homeScore`/`awayScore` aggregation now needs to handle "no results" — but the prior race-scope home/away leg points are gone. If `aggregateMatchScores` only sums existing results, the Match scores will not be reset to null; the previous results' points stay aggregated on Match. (This depends on `ScoringService.aggregateMatchScores` internals not covered by this review's reading.)

**Fix:** Audit `ScoringService.aggregateMatchScores` for the "empty results" path. Add an IT covering: race had 8 results, operator clears all → Match.homeScore/awayScore must be either null or recomputed from remaining races, not stale from prior aggregation.

### WR-03: `SeasonForm` thread-ID fields are dead — written by controller, never read or bound in template

**File:** `src/main/java/org/ctc/admin/dto/SeasonForm.java:29-33`, `src/main/java/org/ctc/admin/controller/SeasonController.java:109-110`, `src/main/java/org/ctc/domain/service/SeasonManagementService.java:148-179`

**Issue:** `SeasonForm.discordRaceResultsThreadId` and `discordStandingsThreadId` are populated by `SeasonController.edit()` (lines 109-110) and decorated with `@Pattern(regexp=DiscordSnowflake.PATTERN)`, but:
1. `season-form.html` never binds them via `th:field="*{discordRaceResultsThreadId}"` (grep `th:field.*discord` in season-form returns no hits). Thread linking is handled by separate `/link-thread` and `/unlink-thread` POST endpoints (controller lines 236-269).
2. `SeasonController.save()` (lines 271-282) passes only id/name/year/number/description/active to `SeasonManagementService.save` — thread fields are silently dropped.

Consequence: the `@Pattern` validation on these DTO fields is dead code that suggests "edit season → thread IDs are validated", but in fact the only validation path is via the JS modal that POSTs to `link-thread` (no Jakarta Validation there at all — see `SeasonController.linkThread` line 236-252, raw `@RequestParam String threadId`). Future maintainers will be misled into thinking the form covers thread IDs.

**Fix:** Either bind them in the form (and route through `save`), or delete the DTO fields. Recommendation: delete the dead fields, since the modal-based link/unlink flow is the SSOT.

### WR-04: Schedule embed's `streamerField` builds Markdown link without escaping `)` in URL

**File:** `src/main/java/org/ctc/discord/service/DiscordPostService.java:641-656`

**Issue:** Builds `"[" + name + "](" + link + ")"`. If admin-supplied `streamLink` contains `)` (e.g., `https://example.com/path)`), Discord will render the link incorrectly (link terminates at the first `)`). Same applies to `buildMatchPreviewMarkdown` which prints `streamLine` raw — if the link contains characters that need URL encoding (e.g., spaces or markdown), Discord may render unexpectedly.

This is admin-only input, not user-controlled, so not a security finding. But the rendered output is wrong for any URL containing parentheses.

**Fix:** Either reject `)` at validation time (`@Pattern` on `MatchForm.streamLink`), or URL-encode the closing paren before embedding in markdown link syntax, or escape with `\)`.

### WR-05: `MatchdayController` houses 60+ lines of staleness business logic — violates "Keep Controllers Thin"

**File:** `src/main/java/org/ctc/admin/controller/MatchdayController.java:167-223`

**Issue:** Four static helpers — `isMatchdayScheduleStale`, `isMatchdayPairingsStale`, `isMatchdayResultsStale`, `isPowerRankingsStale` — walk match → races → results trees, time-compare against post's `updatedAt`, and return booleans. CLAUDE.md's "Architectural Principles" says: "No business logic or direct repository access in controllers" — here `isPowerRankingsStale` injects `seasonTeamRepository` into the controller (`MatchdayController.java:61`, used on line 218). The post-staleness invariant is shared with `SeasonController.populateDiscordIntegrationModel` (which calls `StandingsService.hasNewerResultsSincePhaseScoped`) — so the pattern is acknowledged for STANDINGS but not extracted for the matchday-level posts.

**Fix:** Move `isMatchdayScheduleStale` / `isMatchdayPairingsStale` / `isMatchdayResultsStale` / `isPowerRankingsStale` into `MatchdayService` (or a dedicated `DiscordPostStalenessService`), and inject only the boolean results into the model.

### WR-06: Pre-V14 `STANDINGS` rows orphaned from new phase-aware lookup

**File:** `src/main/java/org/ctc/admin/controller/SeasonController.java:142-152`, `src/main/java/org/ctc/discord/repository/DiscordPostRepository.java:33-37`

**Issue:** V14 migration adds `phase_id` as nullable to existing `discord_post` rows. Pre-existing `STANDINGS` posts (if any) have `phase_id=NULL`. After V14, `SeasonController.populateDiscordIntegrationModel` always calls `findByChannelIdAndPostTypeAndSeasonIdAndPhaseId(..., p.getId())` for each phase. With non-null `p.getId()` and DB-stored `phase_id=NULL`, JPA generates `phase_id = ?` (not `phase_id IS NULL`) — the pre-V14 rows never match.

Consequence: a pre-existing STANDINGS post is "invisible" to the UI; clicking "Post Standings" creates a NEW row, leaving two rows pointing at the same Discord message. The next Re-Post may PATCH the wrong row.

This particular project shipped V12 (`discord_post` table) only in v1.13 itself, so the upgrade window is small — but the assumption "pre-V14 STANDINGS row may exist" applies for any deployment that ran V12 + V13 without V14 (intermediate releases between phase 94 and phase 95). The likelihood is low; the cost of the fix is trivial.

**Fix:** When the lookup with phase returns empty, fall back to the no-phase lookup and attribute the row to the phase via a one-off `UPDATE discord_post SET phase_id=? WHERE id=?` on Re-Post. Or — simpler — make the V14 migration explicitly backfill `phase_id` from the season's REGULAR phase for any existing STANDINGS row (V14 currently does only `ALTER TABLE ADD COLUMN`).

### WR-07: `DiscordPostRef.SeasonRef.applyTo` writes seasonId only — phaseId set asymmetrically by caller

**File:** `src/main/java/org/ctc/discord/dto/DiscordPostRef.java:129-149`, `src/main/java/org/ctc/discord/service/DiscordPostService.java:823-826`

**Issue:** All other `DiscordPostRef` permits set their single FK field via `applyTo(row)`. `SeasonRef.applyTo` sets only `seasonId`; the `phaseId` is written by a separate `instanceof` branch in `DiscordPostService.postOrEdit`:

```java
ref.applyTo(row);
if (ref instanceof DiscordPostRef.SeasonRef s && s.phaseId() != null) {
    row.setPhaseId(s.phaseId());
}
```

Any future caller of `postOrEdit` that uses a `SeasonRef` outside this method would silently drop the `phaseId`. This is a maintenance trap. The sealed-record contract should be self-sufficient.

**Fix:** Move the phaseId write into `SeasonRef.applyTo`:
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
Then drop the duplicate instanceof check in `DiscordPostService.postOrEdit`. Update `DiscordPostRefSeasonRefWidenedTest.givenSeasonRef_whenApplyTo_thenSetsSeasonIdOnly` accordingly (the test currently `verifyNoMoreInteractions(row)` after `setSeasonId` — that assertion would break, which is the right signal that behavior changed).

### WR-08: `DiscordChannelArchiveServiceWireMockIT` is `@Transactional` but tests channel-archive flow

**File:** `src/test/java/org/ctc/discord/service/DiscordChannelArchiveServiceWireMockIT.java:46`

**Issue:** The class is annotated `@Transactional`, meaning the test transaction rolls back at end-of-test. The phase-95 controller flow it tests (`MatchController.moveToArchive`) calls `discordRestClient.modifyChannel(...)` then `matchService.markChannelArchived(id)` — `markChannelArchived` is `@Transactional` and the Spring transaction-manager will join the outer `@Transactional` test wrapper. The `match.discordChannelArchivedAt` write therefore commits only at outer-test commit, which never happens (the test rolls back).

The test currently only asserts the WireMock PATCH was issued and the flash message — it does not reload the match from the DB to assert `discordChannelArchivedAt` was written. So the bug is latent: a regression that breaks `markChannelArchived`'s write would not fail this test.

**Fix:** Either remove `@Transactional` from this IT, or add an explicit reload + assertion on `discordChannelArchivedAt` (which will fail under `@Transactional` because the outer rollback erases the write — forcing the right fix).

## Info

### IN-01: Single D-XX phase-marker comment in test file

**File:** `src/test/java/org/ctc/discord/service/DiscordPostServiceProvisionalScoresIT.java:185`

**Issue:** `// D-96-GRX-1c: PROVISIONAL_SCORES targets the match-channel only, never a forum-thread.` — exactly the kind of phase/plan reference CLAUDE.md "No Comment Pollution" bans.

**Fix:** Rewrite without the phase tag: `// PROVISIONAL_SCORES targets the match-channel only (never the forum-thread).`

### IN-02: `org.ctc.discord.dto.Thread` shadows `java.lang.Thread`

**File:** `src/main/java/org/ctc/discord/dto/Thread.java`

**Issue:** Custom DTO record named `Thread` collides with `java.lang.Thread`. In `TeamCardService.renderCardScreenshotWithRetry` (line 133), `Thread.currentThread().interrupt()` refers to `java.lang.Thread` only because no static import or local import of the DTO Thread is present. Future maintainers editing files that import `org.ctc.discord.dto.Thread` cannot use `Thread.sleep` / `Thread.currentThread` without FQN.

**Fix:** Rename DTO to `DiscordThread` or `ForumThread`. Renames are cheap (5 import lines per `grep`).

### IN-03: `DiscordPostService.matchHasCompleteLineups` issues one repository call per race

**File:** `src/main/java/org/ctc/discord/service/DiscordPostService.java:667-671`

**Issue:** N queries per page render. Out of v1 review scope (performance), recorded for visibility. With typical matchday legs ≤ 4, impact is negligible.

**Fix (deferred):** Batch by race-id when this becomes hot.

### IN-04: `MatchPreviewPreFlightResult` repurposed as generic pre-flight DTO

**File:** `src/main/java/org/ctc/admin/dto/MatchPreviewPreFlightResult.java`, `src/main/java/org/ctc/discord/service/DiscordPostService.java:335-360`

**Issue:** Type name implies match-preview-only usage, but used for `canPostMatchdayResults`, `canPostPowerRankings`, `canPostMatchdayPairings`, `canPostMatchdaySchedule`, `canPostStandings`. Misleading naming — future maintainers will trip on it.

**Fix:** Rename to `PreFlightResult` or `DiscordPostPreFlightResult`.

### IN-05: `MatchController.detail` mixes Thymeleaf-OSIV access with explicit repo lookup

**File:** `src/main/java/org/ctc/admin/controller/MatchController.java:103-143`

**Issue:** `detail()` is 40+ lines. It triggers `discordPostRepository.findByChannelIdAndPostTypeAndMatchId` 6 times via `findMatchPost`, plus `discordPostService.matchHasCompleteSettings/Lineups/ProvisionalData`, plus the announcement-channel lookup. None of this is HTTP-binding logic; it belongs in `MatchService.getDetailData` (already exists as a record carrier) extended with `MatchDiscordDetailData`.

This is the controller-thin equivalent of WR-05.

**Fix:** Move the model population into a new `MatchService.getDiscordDetailData(match)` method returning a record. Controller becomes a 5-line shim.

---

_Reviewed: 2026-05-28_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
