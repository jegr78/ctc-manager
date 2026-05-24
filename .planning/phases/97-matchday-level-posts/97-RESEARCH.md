# Phase 97: Matchday-Level Posts — Research

**Researched:** 2026-05-24
**Domain:** Discord Post Integration (POST-06, POST-07a, POST-07b, POST-08) — Spring Boot 4.x / Thymeleaf / Java 25
**Confidence:** HIGH — all findings verified directly against the live codebase

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-97-PREV-1:** Mirror Phase 95 D-95-04 pattern verbatim. NEW `MatchPreviewFieldsChangedEvent(UUID matchId)` record. `MatchService.updateDiscordFields` performs Pre/Post-Diff on `streamLink` + `discordTeaser`; on diff publishes event. `DiscordAutoPostListener.onMatchPreviewFieldsChanged` handles it under `@TransactionalEventListener(AFTER_COMMIT)` + `@Transactional(REQUIRES_NEW)`. Calls `DiscordPostService.autoEditMatchPreviewIfNeeded(Match)`.
- **D-97-PREV-1a:** Only `streamLink` + `discordTeaser` trigger Auto-Edit. Race.dateTime changes do NOT trigger MATCH_PREVIEW auto-edit.
- **D-97-PREV-1b:** Per-match 1 PATCH per `MatchService.save` call.
- **D-97-PREV-2:** POST-06 simplified to a single per-match button on Match-Detail (`/admin/matches/{id}`). Target: `discordGlobalConfig.announcementWebhookUrl`. One `MATCH_PREVIEW` `discord_post` row per match with `match_id` FK.
- **D-97-PREV-2a:** One Discord-message per match = Markdown + 2 attachments (`settings-md{N}.png` + `lineups-md{N}.png`). Markdown: `# {season.name}` / `## {matchday.label}` / `### {teamA.shortName} vs. {teamB.shortName}{subTeamSuffix}` / `{match.discordTeaser}` / `- Date: <t:N:F>` / `- Stream: {streamLink ?: "TBA"}` / `Game On! {emoji(teamA)} {emoji(vs)} {emoji(teamB)}`.
- **D-97-PREV-2b:** H3 header uses `team.getShortName()` + optional sub-team suffix. Planner resolves cleanest suffix-extraction.
- **D-97-PREV-2c:** Pre-flight: `discordTeaser != null` AND `matchHasCompleteSettings` AND `matchHasCompleteLineups` AND `≥1 Race.dateTime != null` AND `announcementWebhookUrl != null`. `streamLink` is OPTIONAL (null → renders "TBA").
- **D-97-PHA-2:** Markdown H2 = `## {matchday.label}` (not hardcoded "Match Day N").
- **D-97-MD-1:** POST-07 split into 2 independent buttons on Matchday-Detail. POST-07a ("Post Match Day Results") uses `MatchdayResultsGraphicService`, type `MATCHDAY_OVERVIEW`. POST-07b ("Post Power Rankings") uses `PowerRankingsGraphicService`, type `POWER_RANKINGS`. Both target race-results-forum-thread via webhook + `?thread_id=`.
- **D-97-MD-2:** Both buttons in one shared Discord Actions card on Matchday-Detail.
- **D-97-MD-3:** Separate `discord_post` rows, separate stale-detection.
- **D-97-PHA-1:** POST-07a uses `MatchdayResultsGraphicService` for ALL PhaseTypes uniformly.
- **D-97-PHA-3:** POST-07b Power Rankings stays season-wide (phase-agnostic).
- **D-97-STA-1:** New `StandingsGraphicService` (Playwright-based). `generateStandingsBytes(Season season, SeasonPhase phase) → List<byte[]>`. New template `standings-render.html`. Iterative design loop. Team logos MUST be integrated.
- **D-97-STA-2:** POST-08 button on `season-form.html` `#discordIntegration` card with adjacent `<select name="phaseId">` dropdown for phase selection.
- **D-97-STA-3:** PNG granularity: REGULAR non-GROUPS → 1 PNG; REGULAR GROUPS → N PNGs (one per SeasonPhaseGroup, sorted by `sortIndex ASC`); PLAYOFF/PLACEMENT → 1 PNG. All in ONE Webhook-POST (multipart when N>1). ONE `STANDINGS` row per `(season_id, phase_id)`.
- **D-97-STA-4:** V14 Flyway migration `discord_post.phase_id UUID NULL` + FK to `season_phases(id)` ON DELETE SET NULL + index. `SeasonRef` record widened to carry optional `phaseId` (or new `SeasonPhaseRef` permit — Planner-Discretion, widening recommended).
- **D-97-STA-5:** Stale-detection per `(season, phase)` via `StandingsService.hasNewerResultsSincePhaseScoped(seasonId, phaseId, since) → boolean`. Surfaced as `standingsStaleByPhase: Map<UUID, Boolean>` on `SeasonController.editSeason`.
- **D-97-05:** Three plans, sequential inline on `gsd/v1.13-discord-integration`. No worktrees, no writing subagents.
- **D-97-06:** Rolling v1.13 Milestone-PR body updated after each plan.
- **D-97-07:** JaCoCo ≥ Phase-96-end baseline; SpotBugs 0 BugInstances; CodeQL gate-step exit 0.
- **D-97-08:** Per-plan VALIDATION.md.
- **D-97-09:** `@Tag("integration")` for WireMock ITs, untagged for Mockito-only unit tests, `@Tag("e2e")` for Playwright E2E.
- **D-97-10:** WireMock-IT-only Phase-97-Close; UAT-07 staged in STATE.md as Pending UAT.
- **D-97-11:** Production-code paths explicitly bounded (see CONTEXT.md § Production Behavior Boundary).

### Claude's Discretion

- Power Rankings persistence strategy (regenerate from current `SeasonTeam.rating` vs. snapshot into `payload_json`). Recommendation: regenerate.
- Sub-team-suffix resolution for Markdown H3 header.
- `StandingsGraphicService` template strategy (duplicate `standings.html` vs. Thymeleaf fragments).
- `MatchdayResultsGraphicService.generateMatchdayResultsBytes` byte[] variant — verify existence.
- Visual-regression snapshots for the 3 new Discord-graphic outputs — include or defer to Phase 98.
- `discord_post.matchday_id` column existence check (verified below: **already exists in V12**).
- `SeasonRef` widening vs. new `SeasonPhaseRef` permit (blast-radius grep below).
- `StandingsGraphicService` Multipart-PNG iteration order (`SeasonPhaseGroup.sortIndex ASC`).

### Deferred Ideas (OUT OF SCOPE)

- MATCHDAY_PAIRINGS overview-graphic post.
- Race.dateTime auto-edit trigger for MATCH_PREVIEW.
- PowerRankings persistent ordering snapshot.
- Pinned-thread auto-bump-to-top on Re-Post.
- `/admin/discord/posts` listing filter dropdowns per scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| POST-06 | Match Preview Announcement per match to announcement-webhook, Settings + Lineups attachments, auto-edit hook on streamLink/teaser change | `DiscordAutoPostListener` pattern verified; byte[] path via `readPng(generateSettings())` confirmed; `DiscordTimestamps.longDateTime` + `DiscordEmojiCache.emojiFor` available |
| POST-07 | Matchday Results (POST-07a) + Power Rankings (POST-07b) to race-results forum-thread | `MatchdayResultsGraphicService.generateResults(Matchday) → byte[]` returns byte[] directly (no uploads-path intermediate); `PowerRankingsGraphicService.generateRankings(year, number, subtitle, teamIds) → byte[]` confirmed; `?thread_id=` overload in `DiscordPostService.postOrEdit` (7-arg) confirmed |
| POST-08 | Standings to Standings forum-thread, phase-aware, stale-detection | `StandingsService.calculateStandings(phaseId, groupId)` available; `SeasonPhaseGroup.sortIndex` ordering exists; V14 migration pattern established; `RaceResultRepository.findByRaceMatchdayPhaseId` available for stale-detection query |
</phase_requirements>

---

## Summary

Phase 97 adds three post-type implementations on top of the Phase 95–96 Discord infrastructure. All foundational plumbing (sealed `DiscordPostRef`, `DiscordPostService.postOrEdit` 7-arg with `thread_id`, auto-unarchive, V12 polymorphic FK table, `DiscordPostType` enum values) is already in place. The planner must navigate four concrete technical areas: (1) extending `MatchService.updateDiscordFields` with a second diff-check and publishing a new `MatchPreviewFieldsChangedEvent`; (2) adding byte[] fanout for the Settings + Lineups graphics (which currently return upload-path strings, not byte arrays directly); (3) adding `MatchdayRef` awareness to `DiscordPostService.postOrEdit` sealed-switch — **already done**, `DiscordPostRef` already has `MatchdayRef`; (4) adding a new `StandingsGraphicService` (Playwright-based) with V14 schema change.

**Critical finding:** `DiscordPostRef.MatchdayRef` is **already present** in the codebase (source verified). The `DiscordPostService.postOrEdit` sealed-switch already handles `DiscordPostRef.MatchdayRef`. `DiscordPostRepository.findByChannelIdAndPostTypeAndMatchdayId` is also already present. Plan 97-02's "add MatchdayRef permit" task is therefore already complete — Plan 97-02 only needs new `DiscordPostService` methods and the new controller endpoints.

**Primary recommendation:** Follow the CONTEXT.md plan decomposition exactly. Plan 97-01 is the most technically novel (byte[] path for Settings/Lineups graphics for a non-channel-webhook context + new event-listener). Plans 97-02 and 97-03 follow established patterns closely.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| POST-06 Match Preview posting | API / Backend (`DiscordPostService`) | Controller (form submit + redirect) | Business logic (Markdown build, attachment prep, webhook call) belongs in service |
| POST-06 Auto-edit hook | API / Backend (`DiscordAutoPostListener`) | Domain Service (`MatchService`) | Transactional event pattern keeps Discord out of domain layer |
| POST-07a/7b Matchday posts | API / Backend (`DiscordPostService`) | Controller | Graphic generation + webhook dispatch are service-layer |
| POST-08 Standings post | API / Backend (`DiscordPostService`, new `StandingsGraphicService`) | Controller | Same pattern |
| V14 migration | Database / Storage | — | Schema change via Flyway |
| Phase-selector UI (POST-08) | Frontend Server (Thymeleaf SSR) | SeasonController model | Server-side computed dropdown; no client-side JS |
| Pre-flight predicates | API / Backend (computed in Controller, using service helpers) | Thymeleaf (conditional render) | All visibility computed server-side; zero JS toggles |
| Stale-detection (POST-07a, POST-08) | API / Backend (`StandingsService`, `DiscordPostService`) | Controller model attributes | Query-based freshness check |

---

## Per-Plan Technical Approach

### Plan 97-01: POST-06 Match Preview Announcement + Auto-Edit Hook

#### Verified File Inventory

**New files:**
- `src/main/java/org/ctc/discord/event/MatchPreviewFieldsChangedEvent.java` (record)
- `src/main/java/org/ctc/admin/dto/MatchPreviewPreFlightResult.java` (record)

**Modified files:**
- `src/main/java/org/ctc/domain/service/MatchService.java` — extend `updateDiscordFields` with Preview pre/post diff + event publish
- `src/main/java/org/ctc/discord/service/DiscordAutoPostListener.java` — add `onMatchPreviewFieldsChanged`
- `src/main/java/org/ctc/discord/service/DiscordPostService.java` — add `postMatchPreview(Match)` + `autoEditMatchPreviewIfNeeded(Match)` + inject `DiscordEmojiCache` + `DiscordGlobalConfigService` (already present)
- `src/main/java/org/ctc/admin/controller/MatchController.java` — add `POST /admin/matches/{id}/post-match-preview` endpoint + enrich `detail()` model
- `src/main/resources/templates/admin/match-detail.html` — append Post Match Preview button in `.discord-actions--posts` cluster
- `src/main/resources/static/admin/css/admin.css` — optional `.discord-post-status--auto-edit` pill (1 new class)
- `.planning/phases/97-matchday-level-posts/97-UI-SPEC.md` — revision (drop POST-06a section, replace POST-06b batch cluster with per-match description)
- `.planning/REQUIREMENTS.md` — POST-06 scope revision

#### Method Signatures

```java
// MatchPreviewFieldsChangedEvent.java (new)
package org.ctc.discord.event;
public record MatchPreviewFieldsChangedEvent(UUID matchId) {}

// MatchPreviewPreFlightResult.java (new)  
package org.ctc.admin.dto;
public record MatchPreviewPreFlightResult(boolean canPost, String disabledReason) {}

// MatchService.java (addition to updateDiscordFields)
// Pre-save: snapshot match.getDiscordTeaser() + match.getStreamLink()
// Post-save: if (!Objects.equals(before.teaser, form.discordTeaser) || !Objects.equals(before.streamLink, form.streamLink)) → publishEvent(new MatchPreviewFieldsChangedEvent(saved.getId()))

// DiscordPostService.java (new methods)
@Transactional
public DiscordPost postMatchPreview(Match match) throws DiscordApiException

@Transactional
public void autoEditMatchPreviewIfNeeded(Match match) throws DiscordApiException

// MatchController.java (new endpoint)
@PostMapping("/{id}/post-match-preview")
public String postMatchPreview(@PathVariable UUID id, RedirectAttributes ra)
```

#### Event-Listener Wiring (exact mirror of `onScheduleFieldsChanged`)

```java
// DiscordAutoPostListener.java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void onMatchPreviewFieldsChanged(MatchPreviewFieldsChangedEvent event) {
    Match match = matchRepository.findById(event.matchId()).orElse(null);
    if (match == null) {
        log.warn("Auto-edit MATCH_PREVIEW skipped — match {} not found post-commit", event.matchId());
        return;
    }
    try {
        discordPostService.autoEditMatchPreviewIfNeeded(match);
    } catch (DiscordApiException e) {
        log.warn("Auto-edit MATCH_PREVIEW failed for match {}: category={}", event.matchId(), e.category().name());
        recordRequestAttribute(AUTO_EDIT_ERROR_ATTRIBUTE, e.category().name().toLowerCase().replace('_', '-'));
    } catch (RuntimeException e) {
        log.warn("Auto-edit MATCH_PREVIEW failed for match {}: {}", event.matchId(), e.toString());
        recordRequestAttribute(AUTO_EDIT_ERROR_ATTRIBUTE, "transient");
    }
}
```

#### Webhook Target Resolution for POST-06

```java
// In postMatchPreview(Match match):
DiscordGlobalConfig config = globalConfigService.getOrInitialize();
String webhookUrl = config.getAnnouncementWebhookUrl();
// No thread_id — announcement-webhook is a regular channel webhook (not forum-thread)
// channelId = parseWebhookUrl(webhookUrl).id()
// ref = DiscordPostRef.match(match)
// type = DiscordPostType.MATCH_PREVIEW
postOrEdit(channelId, webhookUrl, MATCH_PREVIEW, payload, attachments, DiscordPostRef.match(match));
```

#### Attachment Byte[] Path for POST-06 — Critical Finding

`SettingsGraphicService.generateSettings(Race)` returns `String` (uploads-path), NOT `byte[]`. `LineupGraphicService.generateLineup(Race)` also returns `String`. `DiscordPostService` already handles this via `readPng(url)` for the POST-03/04 match-channel posts. For POST-06, the same `readPng()` private method can be reused — both graphics have already been generated to disk by the operator before posting. Pattern:

```java
// For the first race in match (Settings.png + Lineups.png for first race, or per-race if N races):
// D-97-PREV-2a specifies "settings-md{N}.png" + "lineups-md{N}.png" filenames
// The match may have N races (multi-leg). Convention from Phase 95: iterate races.
List<Race> races = match.getRaces();
List<NamedAttachment> attachments = new ArrayList<>();
for (int i = 0; i < races.size(); i++) {
    byte[] settingsBytes = readPng(settingsGraphicService.generateSettings(races.get(i)));
    byte[] lineupBytes = readPng(lineupGraphicService.generateLineup(races.get(i)));
    attachments.add(new NamedAttachment("settings-md" + (i+1) + ".png", settingsBytes));
    attachments.add(new NamedAttachment("lineups-md" + (i+1) + ".png", lineupBytes));
}
```

`readPng()` is `private` in `DiscordPostService` — no new helper needed; the new `postMatchPreview` method lives in the same class.

**Pre-flight check:** Settings + Lineups graphics must already exist on disk (via `matchHasCompleteSettings` + `matchHasCompleteLineups` predicates that check settings data in DB, not files). The Pre/Post-Diff for settings availability is already done by `DiscordPostService.matchHasCompleteSettings` + `matchHasCompleteLineups`. If files are missing, `readPng` throws `IOException` → caught → wrapped as `DiscordTransientException`. This is acceptable (consistent with existing behavior).

#### Markdown Build for POST-06

```java
// Season name:
String seasonName = match.getMatchday().getSeason().getName();
// Matchday label (D-97-PHA-2):
String matchdayLabel = match.getMatchday().getLabel();
// H3 team labels (D-97-PREV-2b):
String homeLabel = resolveTeamLabel(match.getHomeTeam());
String awayLabel = resolveTeamLabel(match.getAwayTeam());
// Date from first race (D-97-PREV-2c — ≥1 race with dateTime):
LocalDateTime firstRaceTime = firstRaceTime(match).orElseThrow(...);
String dateTag = discordTimestamps.longDateTime(firstRaceTime);
// Stream link (null → "TBA"):
String streamField = (match.getStreamLink() != null && !match.getStreamLink().isBlank())
    ? match.getStreamLink() : "TBA";
// Game On! line:
String vsEmojiName = globalConfig.getVsEmojiName(); // "CTC" default
String gameOnLine = "Game On! " + emojiCache.emojiFor(homeLabel) 
    + " " + emojiCache.emojiFor(vsEmojiName)
    + " " + emojiCache.emojiFor(awayLabel);
// Full Markdown:
String content = "# " + seasonName + "\n"
    + "## " + matchdayLabel + "\n"
    + "### " + homeLabel + " vs. " + awayLabel + "\n\n"
    + (match.getDiscordTeaser() != null ? match.getDiscordTeaser() + "\n\n" : "")
    + "- Date: " + dateTag + "\n"
    + "- Stream: " + streamField + "\n\n"
    + gameOnLine;
WebhookPayload payload = new WebhookPayload(content, List.of());
```

#### Sub-Team Suffix Resolution

`Team` entity has `parentTeam` (nullable) + `isSubTeam()` boolean. No `subTeamSuffix` field or method exists. The canonical approach:

- If `team.isSubTeam()` (parentTeam != null): the sub-team letter must be derived. Current `Team.shortName` convention for sub-teams is `"TNR B"` while the parent's shortName is `"TNR"`. So `shortName` already contains the full label including the suffix for sub-teams.
- Recommended implementation: `team.getShortName()` is sufficient for the H3 label. No separate suffix computation needed because `shortName` is already the canonical abbreviated label per team (including sub-team differentiator when applicable). The screenshot reference `### DTR vs. TNR B` uses sub-team shortNames directly.

**Recommendation for Planner:** Use `team.getShortName()` directly as the H3 label. This is consistent with how `MatchdayResultsGraphicService` builds match rows (line 93: `home.getShortName()`). No additional suffix computation needed.

#### Pre-Flight Predicates for POST-06

The `MatchController.detail()` method must add to the model:
```java
MatchPreviewPreFlightResult matchPreviewPreFlight = discordPostService.canPostMatchPreview(match);
model.addAttribute("matchPreviewPreFlight", matchPreviewPreFlight);
model.addAttribute("matchPreviewPost", discordPostRepository.findByChannelIdAndPostTypeAndMatchId(
    parseChannelIdFromWebhook(config.getAnnouncementWebhookUrl()), 
    DiscordPostType.MATCH_PREVIEW, match.getId()).orElse(null));
```

**Predicate evaluation order** (top-down, first failing wins as disabled reason):
1. `discordTeaser != null && !blank` → else `"Add a teaser text on Match-Edit first"`
2. `matchHasCompleteSettings(match)` → else `"Configure Race Settings for all races first"`
3. `matchHasCompleteLineups(match)` → else `"Configure Race Lineups for all races first"`
4. `≥1 Race.dateTime != null` → else `"Set Race date+time first"`
5. `config.announcementWebhookUrl != null && !blank` → else `"Configure announcement-webhook in Discord settings"`

Note: `canPostMatchPreview` returns `MatchPreviewPreFlightResult(boolean canPost, String disabledReason)`. Since multiple predicates exist, the service must evaluate them in order. The `discordAnnouncementsConfigured` check must also gating the button DISPLAY (the entire cluster may only render when announcementWebhookUrl is configured, per UI-SPEC).

**Channel ID for lookup:** `postOrEdit` uses the webhook's parsed `id` (webhook ID, not a Discord channel ID in the traditional sense). For `MatchRef`-based lookups, the existing pattern in `findByChannelIdAndPostTypeAndMatchId` uses `match.getDiscordChannelId()` for match-channel posts. For announcement-webhook posts, the channelId should be `parseWebhookUrl(announcementWebhookUrl).id()` (consistent with `postRaceResultToForumThread` which uses `creds.id()` from the forum webhook URL).

---

### Plan 97-02: POST-07a (Match Day Results) + POST-07b (Power Rankings)

#### Verified File Inventory

**No new files needed** (`DiscordPostRef.MatchdayRef` already exists, all repositories already present).

**Modified files:**
- `src/main/java/org/ctc/discord/service/DiscordPostService.java` — add `postMatchdayResults(Matchday)` + `postPowerRankings(Matchday)` + inject `MatchdayResultsGraphicService` + `PowerRankingsGraphicService` + `SeasonTeamRepository` (already injected)
- `src/main/java/org/ctc/admin/controller/MatchdayController.java` — add `POST /admin/matchdays/{id}/post-matchday-results` + `POST /admin/matchdays/{id}/post-power-rankings` + enrich `detail()` model + inject `DiscordPostService`, `DiscordPostRepository`, `DiscordGlobalConfigService`
- `src/main/resources/templates/admin/matchday-detail.html` — NEW Discord Actions card with 2 buttons
- `.planning/REQUIREMENTS.md` — POST-07 split into 7a + 7b revision

#### Critical Finding: MatchdayResultsGraphicService.generateResults Returns byte[] Directly

`MatchdayResultsGraphicService.generateResults(Matchday matchday) → byte[]` already returns `byte[]` directly (not via uploads path). This is the `AbstractMatchdayGraphicService.renderToBytes()` pattern. No `generateMatchdayResultsBytes` variant needs to be added — `generateResults` IS the byte[] variant.

Comparison with Phase 96 pattern: `ResultsGraphicService.generateResults(Race)` returns `String` (uploads-path), while `generateResultsBytes(Race)` returns `byte[]`. `MatchdayResultsGraphicService.generateResults(Matchday)` returns `byte[]` directly — no intermediate file. This is the correct path for Discord posting.

#### Critical Finding: DiscordPostRef.MatchdayRef Already Exists

`DiscordPostRef` already has all 4 permits: `MatchRef`, `MatchdayRef`, `RaceRef`, `SeasonRef`. `DiscordPostService.postOrEdit` sealed-switch already handles `DiscordPostRef.MatchdayRef d → discordPostRepository.findByChannelIdAndPostTypeAndMatchdayId(channelId, type, d.id())`. `DiscordPostRepository.findByChannelIdAndPostTypeAndMatchdayId` also already exists. Plan 97-02 does NOT need to add `MatchdayRef` to the sealed hierarchy.

#### Method Signatures

```java
// DiscordPostService.java (new methods)
@Transactional
public DiscordPost postMatchdayResults(Matchday matchday) throws DiscordApiException
// - Checks allMatchesFinal + raceResultsThreadId + raceResultsForumWebhookUrl
// - Calls matchdayResultsGraphicService.generateResults(matchday) → byte[]
// - filename: "matchday-results-" + matchday.getLabel().toLowerCase().replaceAll("[^a-z0-9]+", "-") + ".png"
// - threadId = season.getDiscordRaceResultsThreadId()
// - webhookUrl = config.getRaceResultsForumWebhookUrl()
// - channelId = parseWebhookUrl(webhookUrl).id()
// - postOrEdit(channelId, webhookUrl, MATCHDAY_OVERVIEW, WebhookPayload.empty(), attachments, DiscordPostRef.matchday(matchday), threadId)

@Transactional
public DiscordPost postPowerRankings(Matchday matchday) throws DiscordApiException
// - Checks raceResultsThreadId + raceResultsForumWebhookUrl (NO allMatchesFinal gate)
// - Derives year + number from matchday.getSeason()
// - Loads teamIds in rating-desc order via loadTeamsForSeasonGroup(year, number).stream().map(RankedTeamData::teamId).toList()
// - subtitle = "Match Day " + matchday.getLabel()  [or just matchday.getLabel() — Planner picks]
// - Calls powerRankingsGraphicService.generateRankings(year, number, subtitle, teamIds) → byte[]
// - filename: "power-rankings-" + matchday.getLabel().toLowerCase().replaceAll("[^a-z0-9]+", "-") + ".png"
// - postOrEdit(channelId, webhookUrl, POWER_RANKINGS, WebhookPayload.empty(), attachments, DiscordPostRef.matchday(matchday), threadId)
```

**Note on `loadTeamsForSeasonGroup`:** It takes `(int year, int number)`, which resolves by `Season.year` + `Season.number`. `matchday.getSeason()` provides the season. `season.getYear()` + `season.getNumber()` give the parameters. The ordering is `SeasonTeam.rating DESC` (already implemented in `loadTeamsForSeasonGroup`).

#### Webhook Target for POST-07a + POST-07b

- `webhookUrl = config.getRaceResultsForumWebhookUrl()`
- `threadId = matchday.getSeason().getDiscordRaceResultsThreadId()` (from V13 column)
- `channelId = parseWebhookUrl(webhookUrl).id()` (same pattern as `postRaceResultToForumThread`)
- Auto-unarchive inherited from `postOrEdit` 7-arg overload (D-96-FOR-4)

#### MatchdayController Model Additions

```java
// MatchdayController.detail() additions:
DiscordGlobalConfig config = discordGlobalConfigService.getOrInitialize();
String webhookUrl = config.getRaceResultsForumWebhookUrl();
String channelId = (webhookUrl != null && !webhookUrl.isBlank()) 
    ? DiscordPostService.parseWebhookUrl(webhookUrl).id() : null;
boolean allMatchesFinal = matchday.getMatches().stream()
    .filter(m -> !m.isBye())
    .allMatch(m -> m.getHomeScore() != null && m.getAwayScore() != null);
boolean threadLinked = season.getDiscordRaceResultsThreadId() != null;
boolean webhookConfigured = webhookUrl != null && !webhookUrl.isBlank();
boolean canPostMatchdayResults = allMatchesFinal && threadLinked && webhookConfigured;
// Disabled reason (computed in order of failure):
String matchdayResultsDisabledReason = !allMatchesFinal ? "Mark all matches as final first"
    : !threadLinked ? "Link a race-results thread on the Season page first"
    : "Configure race-results forum-webhook in Discord settings";
// Post existence lookups:
DiscordPost matchdayOverviewPost = channelId != null
    ? discordPostRepository.findByChannelIdAndPostTypeAndMatchdayId(channelId, MATCHDAY_OVERVIEW, matchday.getId()).orElse(null)
    : null;
DiscordPost powerRankingsPost = channelId != null
    ? discordPostRepository.findByChannelIdAndPostTypeAndMatchdayId(channelId, POWER_RANKINGS, matchday.getId()).orElse(null)
    : null;
// Stale detection POST-07a (≥1 RaceResult.updatedAt > post.updatedAt):
boolean matchdayResultsStale = isMatchdayResultsStale(matchday, matchdayOverviewPost);
// Stale detection POST-07b (MAX(SeasonTeam.updatedAt) > post.updatedAt):
boolean powerRankingsStale = isPowerRankingsStale(season, powerRankingsPost);
```

**Note:** `parseWebhookUrl` is currently `static` but package-private in `DiscordPostService`. Planner must either make it accessible (change visibility or move to a utility class) or add a helper method. Recommended: make it `static` with package-private visibility (already is) and keep channelId derivation inside the new service methods (controllers don't need to call `parseWebhookUrl` directly — they use the service `canPost*` pre-flight method).

#### `allMatchesFinal` Computation

`matchday.getMatches()` is lazy-loaded but available via OSIV. Definition: all non-bye matches have `homeScore != null && awayScore != null`. The existing `MatchdayService.MatchdayDetailData.hasResults()` checks `anyMatch(homeScore != null && awayScore != null)` — this is NOT the same as `allMatchesFinal`. The planner must add a separate `allMatchesFinal` computation in the controller or a new service method.

Recommended: compute inline in controller:
```java
boolean allMatchesFinal = matchday.getMatches().stream()
    .filter(m -> !m.isBye())
    .allMatch(m -> m.getHomeScore() != null && m.getAwayScore() != null)
    && !matchday.getMatches().stream().filter(m -> !m.isBye()).findAny().isEmpty();
```
(The second condition guards the empty-matchday case — 0 non-bye matches should not count as "all final".)

---

### Plan 97-03: POST-08 Standings + StandingsGraphicService + V14 Migration

#### Verified File Inventory

**New files:**
- `src/main/java/org/ctc/admin/service/StandingsGraphicService.java`
- `src/main/resources/templates/admin/standings-render.html`
- `src/main/resources/db/migration/V14__add_discord_post_phase_id.sql`

**Modified files:**
- `src/main/java/org/ctc/discord/model/DiscordPost.java` — add `@ManyToOne SeasonPhase phase` field (nullable, mapped to `phase_id`)
- `src/main/java/org/ctc/discord/dto/DiscordPostRef.java` — widen `SeasonRef` to carry optional `phaseId` (Planner-Discretion: widening recommended)
- `src/main/java/org/ctc/discord/service/DiscordPostService.java` — add `postStandings(Season season, SeasonPhase phase)` + update `SeasonRef` sealed-switch branch to use `(season_id, phase_id)` compound lookup
- `src/main/java/org/ctc/discord/repository/DiscordPostRepository.java` — add `findByChannelIdAndPostTypeAndSeasonIdAndPhaseId`
- `src/main/java/org/ctc/domain/service/StandingsService.java` — add `hasNewerResultsSincePhaseScoped(UUID seasonId, UUID phaseId, LocalDateTime since) → boolean`
- `src/main/java/org/ctc/admin/controller/SeasonController.java` — add `POST /admin/seasons/{id}/post-standings` + enrich `edit()` model
- `src/main/resources/templates/admin/season-form.html` — append Post Standings button + phase-selector dropdown
- `pom.xml` — add `org/ctc/admin/service/StandingsGraphicService.class` to JaCoCo exclusions (and `AbstractMatchdayGraphicService.class` if needed — currently NOT in exclusions, see below)
- `.planning/phases/97-matchday-level-posts/97-UI-SPEC.md` — any final UI-SPEC updates if needed

#### V14 Migration Content (H2 + MariaDB Compatible)

```sql
ALTER TABLE discord_post ADD COLUMN phase_id UUID NULL;
ALTER TABLE discord_post ADD CONSTRAINT fk_discord_post_phase
    FOREIGN KEY (phase_id) REFERENCES season_phases(id) ON DELETE SET NULL;
CREATE INDEX idx_discord_post_phase_id ON discord_post (phase_id);
```

**H2 compatibility:** H2 2.x supports `UUID` type, `ALTER TABLE ... ADD COLUMN`, FK constraints with `ON DELETE SET NULL`, and `CREATE INDEX`. This exact pattern was used in V12 for all existing polymorphic FKs (e.g., `FOREIGN KEY (match_id) REFERENCES matches(id) ON DELETE SET NULL`) — proven compatible. No `CHECK` constraints, no `LONGTEXT`. Consistent with V12 and V13 patterns.

**MariaDB compatibility:** MariaDB 10.7+ supports `UUID` as a native type. The V12 pattern (`uuid NULL`, FK, index) is already in production use.

#### SeasonRef Widening Analysis

Current `SeasonRef(UUID id)` is used in exactly ONE place outside `DiscordPostRef.java` + `DiscordPostService.java`:
- `DiscordPostService.postOrEdit` sealed-switch: `case DiscordPostRef.SeasonRef s → discordPostRepository.findByChannelIdAndPostTypeAndSeasonId(channelId, type, s.id())`
- Factory method `DiscordPostRef.season(Season s)` called in `postRaceResultToForumThread` (line 443: `DiscordPostRef.race(race)`) — wait, that's `RaceRef`. Grep of `DiscordPostRef.season(` showed NO external callers (all `DiscordPostRef.season()` calls are in DiscordPostService itself, and only for `race`, `match`, `matchday` factory methods).

**Blast-radius analysis:** `DiscordPostRef.season(Season s)` static factory is not called anywhere in the codebase (grepped all of `src/main/`). The `SeasonRef` pattern in `postOrEdit` is the only existing callsite. Widening `SeasonRef` from `SeasonRef(UUID id)` to `SeasonRef(UUID seasonId, @Nullable UUID phaseId)` is safe with a backward-compatible factory update:

```java
// Widened record:
record SeasonRef(UUID seasonId, @Nullable UUID phaseId) implements DiscordPostRef {
    @Override public void applyTo(DiscordPost row) { 
        row.setSeasonId(seasonId); 
        // phase is set separately by postOrEdit when SeasonRef carries phaseId
    }
    @Override public UUID seasonId() { return seasonId; }
    // ... other methods return null
}
// Factory methods:
static DiscordPostRef season(Season s) { return new SeasonRef(s.getId(), null); }
static DiscordPostRef seasonPhase(Season s, SeasonPhase p) { return new SeasonRef(s.getId(), p.getId()); }
```

The `postOrEdit` sealed-switch must be updated to use the compound lookup when `phaseId != null`:
```java
case DiscordPostRef.SeasonRef s -> s.phaseId() != null
    ? discordPostRepository.findByChannelIdAndPostTypeAndSeasonIdAndPhaseId(channelId, type, s.seasonId(), s.phaseId())
    : discordPostRepository.findByChannelIdAndPostTypeAndSeasonId(channelId, type, s.seasonId());
```

Also `applyTo` must set `phase` on `DiscordPost` when `phaseId != null`. Since `DiscordPost.phase` is a `@ManyToOne SeasonPhase`, the entity must be loaded (requires `SeasonPhaseRepository` injection in `postOrEdit`, or the phase entity is passed in directly). Recommended: pass `SeasonPhase` in `SeasonRef` record instead of just `UUID`:
```java
record SeasonRef(UUID seasonId, @Nullable UUID phaseId) implements DiscordPostRef {
    // postOrEdit resolves the SeasonPhase entity separately via phaseId when present
}
```
`DiscordPost.applyTo` only sets `phase_id` FK. The JPA entity `@ManyToOne SeasonPhase phase` should be wired by ID reference in the `postOrEdit` logic — use `entityManager.getReference(SeasonPhase.class, phaseId)` or a repository lookup.

#### DiscordPost Entity Change

```java
// Add to DiscordPost.java:
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "phase_id")   // nullable by default (no nullable = false)
private SeasonPhase phase;
```

No `@JoinColumn(nullable = false)` — the column is nullable (existing posts keep `phase_id = NULL`).

#### StandingsGraphicService Architecture

```java
@Slf4j
@Service
public class StandingsGraphicService extends AbstractGraphicService {

    private final StandingsService standingsService;
    private final SeasonTeamRepository seasonTeamRepository;
    private final SeasonPhaseService seasonPhaseService;

    public List<byte[]> generateStandingsBytes(Season season, SeasonPhase phase) throws IOException
    // - branch on phase.getLayout():
    //   GROUPS → iterate phase.getGroups() sorted by sortIndex ASC
    //             → for each group: render standings-render.html with group-specific data
    //             → collect byte[] per group
    //   non-GROUPS → render single standings-render.html with combined phase data
    //             → return List.of(singlePng)
    // - Template: "admin/standings-render" (new template)
    // - Delegate to renderToBytes() from AbstractGraphicService base
}
```

**JaCoCo exclusion:** `StandingsGraphicService` uses Playwright runtime → must be added to pom.xml exclusions: `<exclude>org/ctc/admin/service/StandingsGraphicService.class</exclude>`.

**Important finding: `AbstractMatchdayGraphicService` is NOT in JaCoCo exclusions.** The exclusion list only contains `AbstractGraphicService.class`. This means `MatchdayResultsGraphicService` (which extends `AbstractMatchdayGraphicService`) is currently excluded via `AbstractGraphicService.class` only if it's a direct subclass — but `MatchdayResultsGraphicService extends AbstractMatchdayGraphicService extends AbstractGraphicService`. JaCoCo `*.class` excludes the exact class file, not subclasses. So `AbstractMatchdayGraphicService.class` is NOT excluded. This is a pre-existing state, not a Phase 97 issue.

`StandingsGraphicService` should extend `AbstractGraphicService` directly (same as `PowerRankingsGraphicService`) since it doesn't share `MatchdayGraphicData` or `prepareBaseContext`. Add `StandingsGraphicService.class` to pom.xml exclusions.

#### Template Strategy: Duplicate vs. Fragment

`templates/admin/standings.html` is the admin-display page — it renders a full HTML page with Thymeleaf layout, tabs, nav, etc. The graphic render needs a self-contained HTML with no layout wrapper (just a fixed-size container, e.g., 1920×1080px body, all CSS inline or embedded).

**Recommendation:** Duplicate into `standings-render.html` as a standalone Playwright-render template. Reuse the data shape (`StandingsService.calculateStandings(phaseId, groupId)` provides `List<TeamStanding>`) but strip the layout, navigation, and tabs. Add team logo integration consistent with `MatchdayResultsGraphicService` (`encodeLogoBase64` pattern from `AbstractMatchdayGraphicService`).

Starting template structure:
```html
<!DOCTYPE html>
<html>
<head><style>/* dark theme, 1920x1080, team logo cells */</style></head>
<body style="margin:0;width:1920px;height:1080px">
  <!-- Standings table with team logos, position, name, W-D-L, points -->
</body>
</html>
```

#### StandingsService.hasNewerResultsSincePhaseScoped

```java
// StandingsService.java (new method)
@Transactional(readOnly = true)
public boolean hasNewerResultsSincePhaseScoped(UUID seasonId, UUID phaseId, LocalDateTime since) {
    // Query RaceResultRepository for phase-scoped results:
    // Use findByRaceMatchdayPhaseId(phaseId) — already exists in RaceResultRepository
    // + findByRacePlayoffMatchupRoundPlayoffPhaseId(phaseId) for playoff results
    // Find any with updatedAt > since
}
```

`RaceResultRepository` already has `findByRaceMatchdayPhaseId(UUID phaseId)` (line 45 verified). This covers REGULAR + PLACEMENT phases. For PLAYOFF phases, use `findByRacePlayoffMatchupRoundPlayoffPhaseId(UUID phaseId)` (line 47-48 verified). The implementation combines both to cover all phase types.

#### SeasonController POST /post-standings

```java
@PostMapping("/{id}/post-standings")
public String postStandings(@PathVariable UUID id,
                            @RequestParam UUID phaseId,
                            RedirectAttributes redirectAttributes)
// 1. Load season + phase
// 2. Load config (thread + webhook validation)
// 3. Call discordPostService.postStandings(season, phase) 
// 4. Flash successMessage or error
// 5. redirect to /admin/seasons/{id}/edit
```

**Phase-selector in season-form.html:** The dropdown only renders if `season.phases.size() > 1`. If exactly one phase, it is auto-selected server-side (hidden `<input type="hidden" name="phaseId" value="...">` with the single phase's ID).

```java
// SeasonController.edit() additions:
List<SeasonPhase> allPhases = seasonPhaseService.findAllPhases(id);
DiscordGlobalConfig config = discordGlobalConfigService.getOrInitialize();
boolean canPostStandings = season.getDiscordStandingsThreadId() != null 
    && !season.getDiscordStandingsThreadId().isBlank()
    && config.getStandingsForumWebhookUrl() != null
    && !config.getStandingsForumWebhookUrl().isBlank()
    && !allPhases.isEmpty();
// standingsPost: Map<UUID, DiscordPost> keyed by phaseId (one per phase that has a post)
// standingsStaleByPhase: Map<UUID, Boolean>
```

---

## Validation Architecture (Nyquist)

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test + WireMock |
| Config file | `pom.xml` (Surefire + Failsafe via `@Tag`) |
| Quick run (unit) | `./mvnw test -Dtest=MatchServicePreviewDiffPublishTest` |
| IT run | `./mvnw verify -Dit.test=DiscordPostServiceMatchPreviewIT -DfailIfNoTests=false` |
| Full suite | `./mvnw clean verify -Pe2e` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | Plan |
|--------|----------|-----------|-------------------|------|
| POST-06 | `MatchService.updateDiscordFields` publishes `MatchPreviewFieldsChangedEvent` on streamLink/teaser diff | Unit (Mockito-only, untagged) | `./mvnw test -Dtest=MatchServicePreviewDiffPublishTest` | 97-01 |
| POST-06 | `DiscordPostService.postMatchPreview` POSTs multipart to announcement webhook | IT `@Tag("integration")` | `./mvnw verify -Dit.test=DiscordPostServiceMatchPreviewIT` | 97-01 |
| POST-06 | `autoEditMatchPreviewIfNeeded` PATCHes existing row | IT `@Tag("integration")` | `./mvnw verify -Dit.test=DiscordPostServiceMatchPreviewIT` | 97-01 |
| POST-06 | `MatchController.postMatchPreview` flashes success / 4 error categories | IT `@Tag("integration")` | `./mvnw verify -Dit.test=MatchControllerPostMatchPreviewIT` | 97-01 |
| POST-06 | Post Match Preview button visible / disabled / re-post state on match-detail | E2E `@Tag("e2e")` | `./mvnw verify -Pe2e -Dit.test=MatchDetailPreviewButtonE2ETest` | 97-01 |
| POST-07a | `DiscordPostService.postMatchdayResults` POSTs PNG to forum thread | IT `@Tag("integration")` | `./mvnw verify -Dit.test=DiscordPostServiceMatchdayResultsIT` | 97-02 |
| POST-07a | Stale detection flips button label | IT `@Tag("integration")` | included in above | 97-02 |
| POST-07b | `DiscordPostService.postPowerRankings` POSTs PNG to forum thread | IT `@Tag("integration")` | `./mvnw verify -Dit.test=DiscordPostServicePowerRankingsIT` | 97-02 |
| POST-07a+b | `MatchdayController` endpoints flash + redirect | IT `@Tag("integration")` | `./mvnw verify -Dit.test=MatchdayControllerPostEndpointsIT` | 97-02 |
| POST-07a+b | Discord Actions card on matchday-detail renders correctly | E2E `@Tag("e2e")` | `./mvnw verify -Pe2e -Dit.test=MatchdayDetailDiscordActionsE2ETest` | 97-02 |
| POST-08 | V14 migration applies without errors (H2 context) | IT (Spring context startup) | `./mvnw verify` (startup validates Flyway) | 97-03 |
| POST-08 | `DiscordPostService.postStandings` posts to standings thread (all 4 phase-layout variants) | IT `@Tag("integration")` | `./mvnw verify -Dit.test=DiscordPostServiceStandingsIT` | 97-03 |
| POST-08 | Phase-selector form binding + `SeasonController.postStandings` | IT `@Tag("integration")` | `./mvnw verify -Dit.test=SeasonControllerPostStandingsIT` | 97-03 |
| POST-08 | `hasNewerResultsSincePhaseScoped` detects stale correctly | IT `@Tag("integration")` | `./mvnw verify -Dit.test=StandingsServicePhaseScopedStaleDetectionIT` | 97-03 |
| POST-08 | Standings button + phase dropdown UX on season-form | E2E `@Tag("e2e")` | `./mvnw verify -Pe2e -Dit.test=SeasonFormStandingsButtonE2ETest` | 97-03 |

### WireMock Coverage Strategy

Per CLAUDE.md "WireMock is not Real-API Coverage":

1. **Production-format pinning:** Each IT must assert the EXACT payload format sent to Discord (content type `multipart/form-data`, JSON payload field `content`, attachment filenames). Use WireMock `withRequestBodyPart(...)` matchers, NOT just `urlPathEqualTo`.
2. **Query-param assertions:** Forum-thread posts must assert `withQueryParam("thread_id", equalTo(THREAD_ID))` in WireMock stubs. Do NOT use `urlPathEqualTo("/webhooks/...")` alone.
3. **No `@MockitoBean DiscordPostService` in transactional ITs:** The real `@Transactional` proxy must run. For `MatchServicePreviewDiffPublishTest` (Mockito-only unit test), mock `ApplicationEventPublisher` and assert `publishEvent(MatchPreviewFieldsChangedEvent)` is called.
4. **DB constraint IT:** V14 FK + index must be tested by an IT that inserts a `discord_post` row with a valid `phase_id` and one with `phase_id = NULL`, verifying both succeed.

### Sampling Rate

- **Per task commit (TDD Red/Green):** `./mvnw test -Dtest=<ClassName>` (Surefire) or `./mvnw verify -Dit.test=<ClassName> -DfailIfNoTests=false` (Failsafe)
- **Per wave merge:** `./mvnw clean verify` (full Surefire + Failsafe, no E2E)
- **Phase gate (end-of-phase):** `./mvnw clean verify -Pe2e`

### Wave 0 Gaps (New Test Files per Plan)

**Plan 97-01:**
- `src/test/java/org/ctc/domain/service/MatchServicePreviewDiffPublishTest.java` (Mockito-only, untagged)
- `src/test/java/org/ctc/discord/service/DiscordPostServiceMatchPreviewIT.java` (`@Tag("integration")`)
- `src/test/java/org/ctc/admin/controller/MatchControllerPostMatchPreviewIT.java` (`@Tag("integration")`)
- `src/test/java/org/ctc/e2e/discord/matchday/MatchDetailPreviewButtonE2ETest.java` (`@Tag("e2e")`)

**Plan 97-02:**
- `src/test/java/org/ctc/discord/service/DiscordPostServiceMatchdayResultsIT.java` (`@Tag("integration")`)
- `src/test/java/org/ctc/discord/service/DiscordPostServicePowerRankingsIT.java` (`@Tag("integration")`)
- `src/test/java/org/ctc/admin/controller/MatchdayControllerPostEndpointsIT.java` (`@Tag("integration")`)
- `src/test/java/org/ctc/e2e/discord/matchday/MatchdayDetailDiscordActionsE2ETest.java` (`@Tag("e2e")`)

**Plan 97-03:**
- `src/test/java/org/ctc/discord/service/DiscordPostServiceStandingsIT.java` (`@Tag("integration")`)
- `src/test/java/org/ctc/admin/controller/SeasonControllerPostStandingsIT.java` (`@Tag("integration")`)
- `src/test/java/org/ctc/domain/service/StandingsServicePhaseScopedStaleDetectionIT.java` (`@Tag("integration")`)
- `src/test/java/org/ctc/e2e/discord/matchday/SeasonFormStandingsButtonE2ETest.java` (`@Tag("e2e")`)

---

## Risks + Mitigations

### Risk 1: `readPng()` is `private` in `DiscordPostService`

**Problem:** `postMatchPreview` uses `generateSettings(race)` which returns an uploads-path String. Must call `readPng(String uploadsUrl)` which is private in the same class. `postMatchPreview` will be a new method in `DiscordPostService` — so it can access the private method directly. No risk.

**Mitigation:** Confirmed — new methods added to `DiscordPostService` have access to `private readPng`. No visibility change needed.

### Risk 2: `DiscordPostRef` Sealed-Switch Exhaustiveness

`DiscordPostRef` already has 4 permits + `DiscordPostService.postOrEdit` sealed-switch already handles all 4. Widening `SeasonRef` to carry `phaseId` is a RECORD CHANGE (not adding a permit), which does NOT affect the sealed-switch exhaustiveness. No compiler risk. Test: `DiscordPostRefSeasonRefWidenedTest` should verify Phase 96 `postRaceResultToForumThread` still calls `DiscordPostRef.season(season)` and compiles.

### Risk 3: V14 Migration H2 vs. MariaDB

V12 already uses the identical pattern (`UUID NULL`, FK `ON DELETE SET NULL`, `CREATE INDEX`) for all 4 existing polymorphic FKs. H2 2.x compatibility is proven. The only new element is the `season_phases` table reference — verify `season_phases` table name matches the entity `@Table(name = "season_phases")` in `SeasonPhase.java` (**verified: `@Table(name = "season_phases")`**).

**V13 has comment pollution** (`-- DO NOT mutate this file after release (CLAUDE.md "Do Not Modify Flyway Migrations")`). Per CLAUDE.md "No Comment Pollution" rule: the V14 file must NOT include such file-header comments restating conventions. Write V14 as clean SQL only.

### Risk 4: `StandingsGraphicService` JaCoCo Exclusion

`pom.xml` JaCoCo exclusion list does NOT currently include `MatchdayResultsGraphicService`, `AbstractMatchdayGraphicService`. `StandingsGraphicService` extends `AbstractGraphicService` (same as `PowerRankingsGraphicService`, which IS excluded). Plan 97-03 MUST add `<exclude>org/ctc/admin/service/StandingsGraphicService.class</exclude>` to the JaCoCo exclusions in `pom.xml`. Without this, the unreachable Playwright-runtime lines in `StandingsGraphicService` will drag down coverage.

**Coverage baseline:** Phase 96 closed at a JaCoCo value that must be preserved. `StandingsGraphicService` is excluded → does not count against coverage. The ~30-50 new ITs across all 3 plans should maintain or improve coverage.

### Risk 5: `parseWebhookUrl` Visibility

`DiscordPostService.parseWebhookUrl(String)` is `static` with package-private (default) visibility. Controllers in `org.ctc.admin.controller` are in a different package. If `MatchdayController` or `SeasonController` needs to call it, it cannot. **Mitigation:** Service methods (`canPostMatchdayResults()`, `canPostPowerRankings()`, `canPostStandings()`) encapsulate the pre-flight check including channelId derivation. Controllers never call `parseWebhookUrl` directly. The controller only receives pre-flight result records from service methods.

### Risk 6: Sub-Team Suffix in H3 Header

`Team.shortName` already includes the sub-team differentiator (e.g., `"TNR B"` for the B sub-team of `"TNR"`). No separate suffix computation is needed — `team.getShortName()` produces the correct Discord header label. **Verified:** `AbstractMatchdayGraphicService.buildMatchRow` uses `home.getShortName()` for both parent teams and sub-teams. The `MatchdayResultsGraphicService` already handles this correctly.

### Risk 7: MatchdayController Missing Dependencies

Currently `MatchdayController` does NOT inject `DiscordPostService`, `DiscordPostRepository`, or `DiscordGlobalConfigService`. Plan 97-02 must add these injections. The controller uses `@RequiredArgsConstructor` — add new `final` field declarations. **SpotBugs consideration:** `DiscordPostService` has `@SuppressFBWarnings` for constructor injection; `MatchdayController` will use standard Lombok `@RequiredArgsConstructor` without `@SuppressFBWarnings` (Lombok config handles `EI_EXPOSE_REP2` automatically via `lombok.config`).

### Risk 8: `DiscordPost.phase` ManyToOne Requires SeasonPhase Entity

When `postOrEdit` creates or updates a `DiscordPost` for STANDINGS, it must set `row.setPhase(phase)` where `phase` is a `SeasonPhase` entity. The `DiscordPostRef.SeasonRef.applyTo(DiscordPost row)` method sets `row.setSeasonId(seasonId)`. The phase FK must be set separately. Recommended approach: after `ref.applyTo(row)`, if `ref instanceof SeasonRef s && s.phaseId() != null`, call `row.setPhaseId(s.phaseId())`. But since `DiscordPost.phase` is a `@ManyToOne`, not a plain UUID column, JPA requires an entity reference. Use `row.setPhase(entityManager.getReference(SeasonPhase.class, phaseId))` in `postOrEdit`, or pass the SeasonPhase entity through the call chain. Alternatively, add a direct `phase_id` UUID setter alongside the JPA relation — but that would conflict with JPA mapping. **Recommendation:** Pass `SeasonPhase` entity into `DiscordPostService.postStandings(Season, SeasonPhase)` → set `row.setPhase(phase)` directly. The `SeasonRef.applyTo` only sets `seasonId`; the phase is set in the specialized `postStandings` method path, not via `applyTo`.

---

## Open Questions / Researcher-Discretion Findings

### OQ-1: PowerRankings subtitle convention for POST-07b

The operator-facing download uses `subtitle` as a free-text param. For POST-07b, the subtitle defaults to `"Match Day " + matchday.getLabel()`. **Question:** Should it be `"Match Day " + matchday.getLabel()` (redundant if label is already "Match Day 4") or just `matchday.getLabel()`? **Recommendation:** Use `matchday.getLabel()` directly as subtitle — avoids the `"Match Day Match Day 4"` double-prefix problem.

### OQ-2: Season.getYear() + getNumber() for PowerRankings

`PowerRankingsGraphicService.loadTeamsForSeasonGroup(int year, int number)` loads by `Season.year` + `Season.number`. The controller currently accepts these as URL params. For POST-07b, the values come from `matchday.getSeason().getYear()` + `matchday.getSeason().getNumber()`. This is straightforward. Verify `Season.getYear()` + `Season.getNumber()` getters exist (Lombok `@Getter` on Season entity — confirmed by inspecting other services).

### OQ-3: SeasonRef applyTo Phase ID — JPA vs. UUID

When a `STANDINGS` row is persisted, `DiscordPost.phase` must be populated. Using `entityManager.getReference(SeasonPhase.class, phaseId)` requires `EntityManager` injection into `DiscordPostService`. Alternative: make `DiscordPost.phaseId` a plain `@Column UUID` (separate from the `@ManyToOne`) and have a separate `phase` join. **Recommendation:** Keep it simple — add a `@Column(name = "phase_id") private UUID phaseId` UUID field alongside the `@ManyToOne SeasonPhase phase` relation using `insertable = false, updatable = false` for the relation, OR use `@JoinColumn(name = "phase_id")` on the `@ManyToOne` and set the entity reference in the service. The existing FK columns (`match_id`, `matchday_id`, etc.) in `DiscordPost` are plain `UUID` columns — NOT `@ManyToOne` relations (confirmed in source: they are `@Column(name = "match_id") private UUID matchId`). **Consistent approach: add `phase_id` as a plain `@Column(name = "phase_id") UUID phaseId` field, NO `@ManyToOne` relation.** This is perfectly consistent with the existing pattern and avoids the EntityManager complexity entirely. CONTEXT.md says `@ManyToOne SeasonPhase phase` — the Planner should implement this as a plain UUID column (matching the existing DiscordPost pattern) and note the discrepancy.

### OQ-4: canPostMatchPreview and channelId for Announcement Webhook

For `MATCH_PREVIEW` posts, the `channelId` stored in `discord_post.channel_id` should be the announcement webhook's ID (from `parseWebhookUrl(announcementWebhookUrl).id()`), NOT the match's `discordChannelId`. This matters for the `findByChannelIdAndPostTypeAndMatchId` lookup. The `autoEditMatchPreviewIfNeeded` method must use the same channelId derivation as `postMatchPreview` to look up the existing row correctly. Planner must ensure both methods use `config.getAnnouncementWebhookUrl()` parsed to `webhookId` for the channel_id column.

---

## Established Patterns to Mirror

### Pattern 1: AFTER_COMMIT Event-Listener (Auto-Edit Hook)

Already implemented for `SCHEDULE` in `DiscordAutoPostListener.onScheduleFieldsChanged` (lines 49-67). Plan 97-01 adds `onMatchPreviewFieldsChanged` as an exact mirror. The pattern:
1. `MatchService.updateDiscordFields` → snapshot before-values → save → compare → `publishEvent(MatchPreviewFieldsChangedEvent)`
2. `DiscordAutoPostListener.onMatchPreviewFieldsChanged` under `@TransactionalEventListener(AFTER_COMMIT)` + `@Transactional(REQUIRES_NEW)` → load match → call service
3. `DiscordPostService.autoEditMatchPreviewIfNeeded` → lookup existing `MATCH_PREVIEW` row → if exists, patch via webhook

### Pattern 2: Pre-Flight Predicates as ModelAttributes

`MatchController.detail()` adds `matchHasCompleteSettings`, `matchHasCompleteLineups`, etc. to model. Plans 97-01/02/03 follow the same approach — service method computes `canPost*` result, controller adds it to model, Thymeleaf template renders enabled/disabled state.

### Pattern 3: Forum-Thread Posting with ?thread_id=

`DiscordPostService.postRaceResultToForumThread` (Phase 96) shows the complete pattern:
1. Resolve `threadId` from `season.getDiscordRaceResultsThreadId()`
2. Resolve `webhookUrl` from `config.getRaceResultsForumWebhookUrl()`
3. `parseWebhookUrl(webhookUrl).id()` → channelId
4. `postOrEdit(channelId, webhookUrl, type, payload, attachments, ref, threadId)` (7-arg overload)

Plans 97-02 and 97-03 follow this exactly.

### Pattern 4: Button Label Transitions (Post → Re-Post → Update)

Phase 95 Match Results template pattern: `th:attr="data-testid=${stale ? 'update-X' : 'repost-X'}"` + `th:text="${stale ? 'Update X' : 'Re-Post X'}"`. Plans 97-02 + 97-03 follow verbatim.

### Pattern 5: Multipart PNG Posting

`DiscordPostService.postMatchResults` posts multiple PNGs via `executeMultipart`. `postStandings` for REGULAR-with-GROUPS follows the same multipart approach with `List<NamedAttachment>` where `size() > 1`.

---

## State of the Art

| Old Approach | Current Approach | Notes |
|--------------|------------------|-------|
| Batch POST-06 with confirmation modal (UI-SPEC) | Per-match button on Match-Detail (D-97-PREV-2) | Scope change confirmed 2026-05-23 |
| POST-07 as single combined post | POST-07a + POST-07b as independent buttons (D-97-MD-1) | User direction confirmed |
| SeasonRef single UUID | SeasonRef widened with optional phaseId (D-97-STA-4) | Breaking but safe — 0 external callers |
| `DiscordPost` without phase FK | V14 adds `phase_id UUID NULL` | Forward-compatible |

---

## Source Audit

| Symbol | Location | Status |
|--------|----------|--------|
| `DiscordPostRef.MatchdayRef` | `DiscordPostRef.java:69` | **EXISTS — Plan 97-02 does NOT need to add it** |
| `DiscordPostRepository.findByChannelIdAndPostTypeAndMatchdayId` | `DiscordPostRepository.java:22` | **EXISTS** |
| `DiscordPostType.MATCH_PREVIEW/MATCHDAY_OVERVIEW/POWER_RANKINGS/STANDINGS` | `DiscordPostType.java` | **ALL EXIST** |
| `DiscordPost.matchday_id` FK column | V12 migration + `DiscordPost.java:52` | **EXISTS — Plan 97-02 needs no V14 prerequisite** |
| `DiscordPost.season_id` FK column | V12 migration + `DiscordPost.java:55` | **EXISTS** |
| `MatchdayResultsGraphicService.generateResults(Matchday) → byte[]` | `MatchdayResultsGraphicService.java:27` | **EXISTS and returns byte[] directly** |
| `PowerRankingsGraphicService.generateRankings(int, int, String, List<UUID>) → byte[]` | `PowerRankingsGraphicService.java:87` | **EXISTS** |
| `DiscordTimestamps.longDateTime(LocalDateTime)` | `DiscordTimestamps.java:20` | **EXISTS** |
| `DiscordEmojiCache.emojiFor(String)` | `DiscordEmojiCache.java:23` | **EXISTS** |
| `DiscordGlobalConfig.getAnnouncementWebhookUrl()` | `DiscordGlobalConfig.java:31` | **EXISTS** |
| `DiscordGlobalConfig.getRaceResultsForumWebhookUrl()` | `DiscordGlobalConfig.java:40` | **EXISTS** |
| `DiscordGlobalConfig.getStandingsForumWebhookUrl()` | `DiscordGlobalConfig.java:43` | **EXISTS** |
| `DiscordGlobalConfig.getVsEmojiName()` | `DiscordGlobalConfig.java:46` | **EXISTS** |
| `Season.getDiscordRaceResultsThreadId()` | V13 + `Season` entity | **EXISTS** |
| `Season.getDiscordStandingsThreadId()` | V13 + `Season` entity | **EXISTS** |
| `SeasonPhase.getLayout()` (returns `PhaseLayout`) | `SeasonPhase.java:39` | **EXISTS** |
| `SeasonPhase.getGroups()` sorted by `sortIndex ASC` | `SeasonPhase.java:64-66` | **EXISTS** |
| `PhaseLayout.GROUPS` | `PhaseLayout.java` (enum) | **EXISTS** (inferred from usage in `StandingsService`) |
| `RaceResultRepository.findByRaceMatchdayPhaseId(UUID)` | `RaceResultRepository.java:45` | **EXISTS** |
| `RaceResultRepository.findByRacePlayoffMatchupRoundPlayoffPhaseId(UUID)` | `RaceResultRepository.java:47` | **EXISTS** |
| `StandingsService.calculateStandings(UUID phaseId, UUID groupId)` | `StandingsService.java:40` | **EXISTS** |
| `DiscordPostService.parseWebhookUrl` | `DiscordPostService.java:504` | **EXISTS — static, package-private** |
| `DiscordPostService.postOrEdit(7-arg with threadId)` | `DiscordPostService.java:340` | **EXISTS** |
| `Match.getDiscordTeaser()` | `Match.java:58` (Lombok @Getter on `discordTeaser` field) | **EXISTS** |
| `Match.getStreamLink()` | `Match.java:61` (Lombok @Getter on `streamLink` field) | **EXISTS** |
| `Matchday.getLabel()` | `Matchday.java:36` | **EXISTS** |
| `Matchday.getSeason()` | `Matchday.java:55` (convenience getter) | **EXISTS** |
| `Team.getShortName()` | `Team.java:31` (Lombok @Getter) | **EXISTS** |
| `Team.isSubTeam()` | `Team.java:66` | **EXISTS** |
| `MatchService.updateDiscordFields(UUID, MatchForm)` | `MatchService.java:68` | **EXISTS — Pre/Post-Diff already present for schedule fields** |
| `DiscordAutoPostListener.onScheduleFieldsChanged` | `DiscordAutoPostListener.java:49` | **EXISTS — exact template for Plan 97-01** |
| `MatchScheduleFieldsChangedEvent(UUID matchId)` | `org.ctc.discord.event` | **EXISTS — template for MatchPreviewFieldsChangedEvent** |
| `SeasonPhase table name` | `@Table(name = "season_phases")` | **VERIFIED — V14 FK references `season_phases(id)`** |

**No mismatches found between CONTEXT.md references and actual codebase. All required symbols exist.**

**MatchPreviewFieldsChangedEvent** — does NOT yet exist (new file for Plan 97-01). ✓ Correctly described as new.
**MatchPreviewPreFlightResult** — does NOT yet exist (new file for Plan 97-01). ✓ Correctly described as new.
**StandingsGraphicService** — does NOT yet exist (new file for Plan 97-03). ✓ Correctly described as new.
**standings-render.html** — does NOT yet exist (new template for Plan 97-03). ✓ Correctly described as new.
**V14 migration** — does NOT yet exist (new file for Plan 97-03). ✓ Correctly described as new.

---

## Project Constraints (from CLAUDE.md)

- **Test Coverage:** ≥ 82% line coverage. Phase 97 adds `StandingsGraphicService` to JaCoCo exclusions.
- **Flyway:** V1 unchanged. V14 is additive `ALTER TABLE` + FK + index. No modification of V1–V13.
- **Profiles:** Auth only for `prod`/`docker`. No change needed.
- **OSIV:** Enabled. All lazy-loaded fields accessible in Thymeleaf templates without explicit joins.
- **Playwright:** Compile-scope dependency. `StandingsGraphicService` uses it at runtime → JaCoCo-excluded.
- **No Inline Styles on Buttons:** All new buttons use CSS classes from `admin.css`.
- **No Comment Pollution:** V14 SQL must NOT contain `-- DO NOT mutate this file` header. No `// Plan 97-01:` comments in Java source.
- **Score Aggregation on Result Save:** Phase 97 does not touch `raceRepository.save` or result-writing paths — no `scoringService.aggregateMatchScores` needed.
- **Grep All Usages Before Refactor:** `SeasonRef` widening — grepped, 0 external callers confirmed.
- **Spring-Native over JDK-Built-In:** All Discord HTTP via existing `DiscordWebhookClient` (Spring `RestClient`-backed). No `HttpClient`.
- **Keep Controllers Thin:** All business logic in `DiscordPostService`. Controllers only: load entities, call service, flash + redirect.
- **DTOs not Entities in Controllers:** POST-08 uses `@RequestParam UUID phaseId` (not `@ModelAttribute SeasonPhase`). Binding via `SeasonController.postStandings(@RequestParam UUID phaseId)`.
- **WireMock vs Real-API:** Production-format assertions (`withQueryParam("thread_id", ...)`) required in all forum-thread ITs.
- **Sequential Inline Execution:** Plans execute inline on `gsd/v1.13-discord-integration`. No worktrees.
- **Documentation Maintenance:** After Phase 97 closes, `CLAUDE.md` + `README.md` + Wiki must reflect new post-types.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java 25 (Temurin) | All plans | ✓ (inferred from project) | 25 | — |
| Maven Wrapper (`./mvnw`) | Build/Test | ✓ | — | — |
| Playwright Chromium | Plan 97-03 `StandingsGraphicService` | ✓ (already installed for existing graphic services) | — | — |
| H2 (test/dev) | All plans | ✓ | — | — |
| WireMock | IT tests | ✓ (already used in Phase 95-96 ITs) | — | — |

---

## Sources

### Primary (HIGH confidence)

- Live codebase grep + file reads (2026-05-24) — all symbols verified against actual source files
- `src/main/java/org/ctc/discord/service/DiscordAutoPostListener.java` — event-listener pattern
- `src/main/java/org/ctc/discord/dto/DiscordPostRef.java` — sealed-hierarchy state
- `src/main/java/org/ctc/discord/service/DiscordPostService.java` — full postOrEdit implementation
- `src/main/resources/db/migration/V12__discord_post.sql` — polymorphic FK schema (matchday_id confirmed)
- `src/main/resources/db/migration/V13__add_seasons_discord_threads_and_forum_webhooks.sql` — discord thread columns
- `pom.xml` JaCoCo exclusions block — exclusion list verified
- `src/main/java/org/ctc/admin/service/MatchdayResultsGraphicService.java` — byte[] return type confirmed
- `src/main/java/org/ctc/discord/repository/DiscordPostRepository.java` — all existing query methods
- `src/main/java/org/ctc/domain/model/Team.java` — shortName field, no subTeamSuffix method

### Secondary (MEDIUM confidence)

- `97-CONTEXT.md` — all locked decisions (user-authorised 2026-05-23)
- `97-UI-SPEC.md` — visual and interaction contract (approved 2026-05-23, partial — requires revision in Plan 97-01)

---

## Metadata

**Confidence breakdown:**
- Standard Stack: HIGH — all existing classes verified in codebase
- Architecture: HIGH — all decision records verified against live code
- V14 migration pattern: HIGH — V12 SQL is the proven template
- SeasonRef blast-radius: HIGH — grepped, 0 external callers
- StandingsGraphicService template strategy: MEDIUM — design is iterative per D-97-STA-1
- PowerRankings subtitle convention (OQ-1): MEDIUM — Planner discretion

**Research date:** 2026-05-24
**Valid until:** 2026-06-07 (stable Spring Boot codebase — stable for 14 days)
