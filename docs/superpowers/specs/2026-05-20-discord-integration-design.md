# Discord Integration — Design Spec

**Milestone:** v1.13 — Discord Integration & Carry-Forwards
**Date:** 2026-05-20
**Branch:** `gsd/v1.13-discord-integration`
**Status:** Approved by user 2026-05-20 after multi-round brainstorming

## 1. Goal

Eliminate manual Discord work for the CTC league operator by integrating channel-creation, role-permission setup, structured post-publishing, and post-edit workflows directly into the CTC Manager admin app. The app becomes the single command center for all Discord-side league communications.

## 2. Scope

### 2.1 In-Scope

1. **Carry-forwards from v1.12 audit** (Phase 92, single phase):
   - `CsvImportController` UX migration to typed-catch + `errorCategory` flash + badge (parity with `DriverSheetImportController` + `RaceController`).
   - JaCoCo coverage recovery to ≥ 88.88% via new `RaceControllerCalendarTest` and IT coverage of Google service `IOException` paths.
   - CLEAN-02 grep-predicate tightening to `org.junit.jupiter.api.Assumptions` (distinguishes from AssertJ Assumptions added by Phase 89 PERF-01).
   - Retroactive `89-VERIFICATION.md` / `90-VERIFICATION.md` / `91-VERIFICATION.md` authoring (optional, doc-shape parity).
   - `milestones/v1.12-REQUIREMENTS.md` bookkeeping fix: flip 7 stale `[ ]` checkboxes + 4 stale `Pending` traceability rows.

2. **Discord Integration headline feature** (Phases 93–98):
   - Hybrid integration: minimal Bot via Spring `RestClient` (channel CRUD, role/emoji lookups) + Webhooks (all message posting, uniform Edit-path).
   - Match-channel lifecycle: button-triggered creation with permission-overwrites, archival via category-picker honoring Discord's 50-channels-per-category limit.
   - 11 post types covering the entire matchday workflow (TeamCards, Settings, Lineups, Schedule, Provisional, MatchResults, MatchdayPairings, MatchPreview, RaceResults, MatchdayOverview, PowerRankings, Standings).
   - Provisional-Scores graphic generator (new) eliminating today's manual Google-Sheets screenshot.
   - Forum-channel thread linking per season for race-results and standings.
   - Native Discord-timestamp rendering in each viewer's timezone.
   - Convention-based emoji resolution via `team.shortName` → Discord-server-emoji lookup.

### 2.2 Out-of-Scope (Explicitly Deferred)

- Inbound Discord interaction (slash commands, polls, reaction reads). App is outbound-only.
- Multi-server / multi-guild support. App assumes exactly one Discord guild.
- Settings-form migration from Google Form. Home team continues filling the existing Google Form; app continues reading it.
- Auto-trigger on DB events (no "race-save → auto-post"). All posting is button-triggered to give the operator full control.
- Public-site Discord-notification backend. Discord posts do not appear on `docs/site`.

## 3. Architecture

### 3.1 Integration Model (Hybrid)

| Concern | Mechanism | Rationale |
|---|---|---|
| Channel CRUD (create, move, archive) | Bot via Spring `RestClient` (Bot-Token auth) | Webhooks cannot create channels |
| Webhook creation in new channel | Bot via REST | One API call after channel creation |
| All message posting (text + images) | Webhook POST | Separate rate-limit bucket, simpler auth, channel-bound |
| All message editing | Webhook PATCH (using stored `message_id` + `webhook_token`) | Uniform edit-path across all post types |
| Server-role lookup (for Team-Form dropdown) | Bot via REST | Live list of guild roles |
| Custom-emoji lookup | Bot via REST + 60-min cache | Source of truth is Discord server itself |
| Inbound interaction | None | Local app, not always-online |

### 3.2 Package Layout

```
org.ctc.discord/
├── DiscordRestClient.java          // Spring RestClient with Bot-Token auth + rate-limit interceptor
├── DiscordWebhookClient.java       // Webhook POST/PATCH (multipart for images)
├── DiscordCategoryResolver.java    // Regex match "Match Days Archive {year}[ (n)]" + channel-count
├── DiscordEmojiCache.java          // 60-min TTL cache of guild emojis, keyed by name
├── exception/
│   ├── DiscordApiException.java          // sealed (analog GoogleApiException Phase 91)
│   ├── DiscordTransientException.java    //   permits: 5xx, 429 rate-limit-exhausted
│   ├── DiscordAuthException.java         //              401/403 token/permission issues
│   ├── DiscordNotFoundException.java     //              404 channel/role/webhook deleted
│   └── DiscordCategoryFullException.java //              50-channel-limit reached
├── dto/
│   ├── DiscordChannelDto.java
│   ├── DiscordCategoryDto.java
│   ├── DiscordEmojiDto.java
│   ├── DiscordThreadDto.java
│   └── DiscordWebhookPostRequest.java
├── util/
│   └── DiscordTimestamps.java      // <t:UNIX:STYLE> generator
└── service/
    ├── DiscordChannelService.java       // create-with-permissions, move-to-category, list-archive-categories
    ├── DiscordForumService.java         // list-threads (active + archived), create-thread
    ├── DiscordPostService.java          // post-or-edit via tracked message_id
    └── DiscordGraphicPostService.java   // glue: GraphicService → byte[] → multipart Webhook

org.ctc.admin.controller.DiscordController          // all button-POST endpoints
org.ctc.admin.controller.DiscordConfigController    // /admin/discord-config admin page
org.ctc.admin.dto.DiscordConfigForm                 // form-bind for global config
```

**No external Discord library.** Spring `RestClient` (Spring 6.1+) handles HTTP. Multipart bodies built via `MultipartBodyBuilder`. Zero new production dependencies.

### 3.3 Data Model

#### New Tables (Flyway V8 + V11)

```sql
-- V8__discord_global_config.sql (Phase 93)
CREATE TABLE discord_global_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  guild_id VARCHAR(32) NOT NULL,
  announcement_webhook_url VARCHAR(500) NOT NULL,
  race_results_forum_channel_id VARCHAR(32) NOT NULL,
  standings_forum_channel_id VARCHAR(32) NOT NULL,
  vs_emoji_name VARCHAR(50) NOT NULL DEFAULT 'CTC',
  bot_application_id VARCHAR(32),
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

-- V11__discord_post.sql (Phase 95)
CREATE TABLE discord_post (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  channel_id VARCHAR(32) NOT NULL,
  message_id VARCHAR(32) NOT NULL,
  webhook_id VARCHAR(32) NOT NULL,
  webhook_token VARCHAR(100) NOT NULL,
  post_type VARCHAR(50) NOT NULL,
  match_id BIGINT,
  matchday_id BIGINT,
  race_id BIGINT,
  season_id BIGINT,
  posted_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_discord_post_match FOREIGN KEY (match_id) REFERENCES matches(id) ON DELETE SET NULL,
  CONSTRAINT fk_discord_post_matchday FOREIGN KEY (matchday_id) REFERENCES matchdays(id) ON DELETE SET NULL,
  CONSTRAINT fk_discord_post_race FOREIGN KEY (race_id) REFERENCES races(id) ON DELETE SET NULL,
  CONSTRAINT fk_discord_post_season FOREIGN KEY (season_id) REFERENCES seasons(id) ON DELETE SET NULL
);
CREATE INDEX idx_discord_post_match ON discord_post(match_id);
CREATE INDEX idx_discord_post_matchday ON discord_post(matchday_id);
CREATE INDEX idx_discord_post_race ON discord_post(race_id);
CREATE INDEX idx_discord_post_season ON discord_post(season_id);
CREATE INDEX idx_discord_post_type ON discord_post(post_type);
```

`post_type` enum values: `TEAM_CARDS`, `SETTINGS`, `LINEUPS`, `SCHEDULE`, `PROVISIONAL_SCORES`, `MATCH_RESULTS`, `RACE_RESULTS`, `MATCHDAY_PAIRINGS`, `MATCH_PREVIEW`, `MATCHDAY_OVERVIEW`, `POWER_RANKINGS`, `STANDINGS`.

#### Extended Tables (Flyway V9, V10, V12)

```sql
-- V9__teams_discord_role.sql (Phase 94)
ALTER TABLE teams ADD COLUMN discord_role_id VARCHAR(32);

-- V10__matches_discord_fields.sql (Phase 94)
ALTER TABLE matches ADD COLUMN discord_channel_id VARCHAR(32);
ALTER TABLE matches ADD COLUMN discord_channel_webhook_url VARCHAR(500);
ALTER TABLE matches ADD COLUMN discord_teaser VARCHAR(2000);
ALTER TABLE matches ADD COLUMN stream_link VARCHAR(500);
ALTER TABLE matches ADD COLUMN lobby_host VARCHAR(100);
ALTER TABLE matches ADD COLUMN race_director VARCHAR(100);
ALTER TABLE matches ADD COLUMN streamer VARCHAR(100);

-- V12__seasons_discord_threads.sql (Phase 96)
ALTER TABLE seasons ADD COLUMN discord_race_results_thread_id VARCHAR(32);
ALTER TABLE seasons ADD COLUMN discord_standings_thread_id VARCHAR(32);
```

All FK columns get indexes per project FK-index convention. Snowflake IDs stored as `VARCHAR(32)` to avoid JavaScript-number-precision issues on JSON serialization. H2 + MariaDB compatible.

### 3.4 Security

- **Bot-Token** in `application-local.yml` / env-var `DISCORD_BOT_TOKEN`; never committed; never logged.
- **Webhook-URL secrets in DB**: `@ToString.Exclude` on `Match.discordChannelWebhookUrl`; log pattern masks `https://discord.com/api/webhooks/[^/\s]+/[^/\s]+`.
- **SSRF whitelist** `app.discord.allowed-hosts=discord.com` applied to all outbound calls (analog to v1.5 SSRF pattern).
- **CSRF tokens** on all `POST /admin/discord/**` endpoints (Phase 30 pattern).
- **Mass-assignment defense:** new `DiscordConfigForm` DTO; `MatchForm` extended with new fields via DTO, not direct entity-binding.
- **Threat model** (Phase 93 plan-time):
  - T-93-01 Bot-token leak via stacktrace → log-mask + `@ToString.Exclude` + SpotBugs scan for `log.*[Tt]oken*` patterns.
  - T-93-02 Webhook-URL leak via logs → mask in log pattern + audit `application.yml` `logging.pattern`.
  - T-93-03 Channel-permission bypass via wrong role-mapping → post-create Channel-Permission audit assertion (Bot fetches just-created channel, verifies only whitelisted 2 team-roles have View).
  - T-93-04 Rate-limit burst triggering Discord-bot-ban → token-bucket + max-5-parallel + sequential batch buttons.

### 3.5 Error Handling (sealed exception pattern, follows Phase 91 UX-01 convention)

```java
catch (DiscordApiException e) {
  String category = switch (e) {
    case DiscordTransientException ignored      -> "transient";
    case DiscordAuthException ignored           -> "auth";
    case DiscordNotFoundException ignored       -> "not-found";
    case DiscordCategoryFullException ignored   -> "category-full";
  };
  redirectAttributes.addFlashAttribute("errorMessage", "Discord operation failed: " + e.getUserMessage());
  redirectAttributes.addFlashAttribute("errorCategory", category);
  return "redirect:" + originalView;
}
```

`admin.css` extends the 4-category badge palette: `transient` (orange retry-icon), `auth` (red key-icon), `not-found` (grey ghost-icon), `category-full` (yellow box-icon).

### 3.6 Rate-Limit Handling

Discord returns `X-RateLimit-Bucket`, `X-RateLimit-Remaining`, `X-RateLimit-Reset-After` headers + 429 with `Retry-After`. `DiscordRateLimitInterceptor` (Spring `ClientHttpRequestInterceptor`):
- Token-bucket per `X-RateLimit-Bucket`.
- On 429: max 3 retries with `Retry-After` sleep (jittered).
- On 5xx: exponential backoff 200ms → 1s → 5s.
- After exhaustion: throws `DiscordTransientException` with "retry" wording for UX badge.

### 3.7 Discord Timestamps (Native Per-Viewer Timezone)

Discord renders `<t:UNIX_SECONDS:STYLE>` in each viewer's locale. `DiscordTimestamps` utility:

```java
public static String longDateTime(LocalDateTime dt, ZoneId zone) {
  return "<t:" + dt.atZone(zone).toEpochSecond() + ":F>";
}
public static String relative(LocalDateTime dt, ZoneId zone) {
  return "<t:" + dt.atZone(zone).toEpochSecond() + ":R>";
}
// shortTime (:t), longDate (:D), etc.
```

Global config in `application.yml`: `app.timezone: Europe/Berlin` (default). All `LocalDateTime` fields (e.g. `Race.dateTime`) are interpreted as this zone. Match-start time = `match.races[].dateTime`-min unless a future enhancement adds a separate `match.scheduled_at` field.

### 3.8 Emoji Resolution (Convention-Based)

`team.shortName` (e.g. `AHR`, `TNR`) maps 1:1 to Discord custom-emoji name. `DiscordEmojiCache`:
- `GET /guilds/{guildId}/emojis` returns all server emojis with `name` + `id`.
- Cache: 60-min TTL, manual refresh button on `/admin/discord-config`.
- Lookup: `cache.emojiFor("AHR")` → `<:AHR:1234567890>` (long-form required by webhook posts).
- Fallback: missing emoji → `":AHR:"` literal text (visible but harmless).

`discord_global_config.vs_emoji_name` (default `CTC`) controls the VS-separator emoji in "Game On!" lines.

## 4. UI Workflow

### 4.1 New Pages

| Route | Purpose |
|---|---|
| `GET /admin/discord-config` | Global config: guild-ID, bot-token status (read-only ✓ / ✗), announcement-webhook URL, 2 forum-channel-IDs, vs-emoji-name, test-buttons, refresh-caches buttons |
| `GET /admin/discord/posts` | Browseable list of all `discord_post` entries, filterable by season/match/type, with re-edit/re-post buttons |

### 4.2 Extended Pages

| Page | New Elements |
|---|---|
| `/admin/teams/{id}/edit` | Field `discordRoleId` (plain-text snowflake, optional live dropdown via cached guild-roles) |
| `/admin/seasons/{id}/edit` | "Discord Integration" section with race-results-thread linker + standings-thread linker (each: Link existing / Create new / Unlink) |
| `/admin/matches/{id}/edit` | Fields: `discordTeaser` (textarea, 2000 chars), `streamLink`, `lobbyHost`, `raceDirector`, `streamer` |
| `/admin/matches/{id}` (detail) | "Discord Actions" panel with all match-channel buttons |
| `/admin/matchdays/{id}` (detail) | "Discord Actions" panel with matchday-level buttons |
| `/admin/races/{id}` (detail) | "Post Race Result to Forum-Thread" button |
| `/admin/seasons/{id}` (detail) | "Post Standings to Forum-Thread" button |

### 4.3 Button Matrix

#### On Match-Detail Page

| Button | Visible When | Effect |
|---|---|---|
| Create Discord Channel | `discordChannelId == null`, both teams have `discordRoleId`, season has current-category set | Bot creates channel `md{N}-{teamA}-vs-{teamB}` with permission-overwrites (see 4.4), creates webhook, stores both IDs |
| Post Team Cards | channel exists, no `TEAM_CARDS` post yet | **One** multipart Webhook-POST with both team-card PNGs as `files[0]` + `files[1]` |
| Post Settings Graphic | settings data present | `SettingsGraphicService` → PNG → multipart POST |
| Post Lineups Graphic | `RaceLineup` data present | `LineupGraphicService` → PNG → multipart POST |
| Post Schedule Message | `match.scheduled_at` (or first race) is set | Webhook embed with Date / Lobby Host / Race Director / Streamer fields (empty fields render as `_TBD_`) |
| Post Provisional Scores | race result data present, not final | `ProvisionalScoresGraphicService` (new) → multipart POST |
| Post Match Results | match final-marked | `MatchResultsGraphicService` → multipart POST |
| Re-post (any of above) | existing post tracked in `discord_post` | Webhook-PATCH on stored `message_id` |
| Move to Archive | `discordChannelId != null` | Modal: dropdown of `Match Days Archive {season.year}[ (n)]` categories with channel-counts (e.g. "47/50"), default selection = highest-num with `< 50` |

#### On Matchday-Detail Page

| Button | Target | Effect |
|---|---|---|
| Post Matchday Pairings | announcement webhook | `MatchdayOverviewGraphicService` (existing) → 1 post |
| Post Match Previews (batch) | announcement webhook | For each match: 1 structured Markdown post (see 4.5) + 2 attachments (Settings + Lineups) |
| Post Matchday Overview + Power Rankings | season's `race_results_thread` via webhook with `?thread_id=` | 2 posts: `MatchdayResultsGraphic` + `PowerRankingsGraphic` |

#### On Race-Detail Page

| Button | Target | Effect |
|---|---|---|
| Post Race Result to Forum-Thread | `season.discordRaceResultsThreadId` (Forum thread, webhook+thread_id) | `RaceGraphicService` → multipart POST |

#### On Season-Detail Page

| Button | Target | Effect |
|---|---|---|
| Post Standings to Forum-Thread | `season.discordStandingsThreadId` | `StandingsGraphic` → multipart POST |

#### On Discord-Config Page

| Button | Effect |
|---|---|
| Test Connection | Bot GET `/users/@me` → display bot username + avatar |
| Test Announcement-Webhook | POST "CTC Manager hello — {timestamp}" to announcement |
| Refresh Server-Roles Cache | Bot `GET /guilds/{id}/roles` → cache |
| Refresh Emoji Cache | Bot `GET /guilds/{id}/emojis` → cache |

### 4.4 Channel Permission-Overwrite Model

When the bot creates a match-channel, it sets these overwrites:

| Role | Allow | Deny |
|---|---|---|
| `@everyone` | — | `VIEW_CHANNEL` |
| Team-A-Role | `VIEW_CHANNEL`, `SEND_MESSAGES`, `ADD_REACTIONS`, `ATTACH_FILES`, `EMBED_LINKS`, `READ_MESSAGE_HISTORY`, `USE_EXTERNAL_EMOJIS`, `USE_EXTERNAL_STICKERS` | `SEND_VOICE_MESSAGES`, `MANAGE_CHANNELS`, `MANAGE_MESSAGES`, `MANAGE_THREADS`, `MANAGE_WEBHOOKS`, `CREATE_INSTANT_INVITE`, `MENTION_EVERYONE` |
| Team-B-Role | (identical to Team-A) | (identical to Team-A) |

Bitmasks defined in `DiscordPermissions.java` as `static final long TEAM_MEMBER_ALLOW_MASK` / `TEAM_MEMBER_DENY_MASK`.

### 4.5 MATCH_PREVIEW Post Structure

One Discord message in announcement per match:

```
# {season.name}
## Match Day {matchday.number}
### {match.teamA.name} vs. {match.teamB.name}

{match.discordTeaser}                    ← user-provided Markdown freeform

- Date: <t:N:F>                          ← auto from first race time via DiscordTimestamps
- Stream: {match.streamLink ?: "TBD"}    ← user-editable; supports <#channelId>, URL, or plain text

Game On! {emoji(teamA.shortName)} {emoji(config.vsEmojiName)} {emoji(teamB.shortName)}
```

Attachments: `settings.png` + `lineups.png` in a single multipart POST.

Edit-path: when `match.streamLink` or `match.discordTeaser` changes and a `MATCH_PREVIEW` post exists, the form-save auto-triggers a Webhook-PATCH that regenerates the content and patches the existing message.

### 4.6 Schedule Post Structure

Discord embed in match-channel:

```json
{
  "title": "Match Schedule",
  "fields": [
    {"name": "Date", "value": "<t:N:F> (<t:N:R>)"},
    {"name": "Lobby Host", "value": "{match.lobbyHost ?: '_TBD_'}"},
    {"name": "Race Director", "value": "{match.raceDirector ?: '_TBD_'}"},
    {"name": "Streamer", "value": "{match.streamer ?: '_TBD_'}"}
  ]
}
```

Edit-path: any change to host/RD/streamer fields auto-triggers Webhook-PATCH on existing `SCHEDULE` post.

### 4.7 Forum-Thread Linking

Season-detail page section:

```
race-results Forum-Thread:
  ● linked → "Season 4 - 2026" (ID: 1234567890123)
    [Change Link] [Unlink]
  ○ not linked
    [Link existing Thread...] [Create new Thread...]

standings Forum-Thread:
  (same pattern)
```

**Link existing Thread...** modal:
- Bot fetches `GET /channels/{forum_id}/threads/active` + `GET /channels/{forum_id}/threads/archived/public?limit=100`.
- Lists threads sorted: pinned first, then active, then archived by `last_message_timestamp` desc.
- User clicks one → stored on `season.discordRaceResultsThreadId` / `discordStandingsThreadId`.

**Create new Thread...** modal:
- User enters thread name (default: `Season {N} - {year}` for race-results, `{year}` for standings).
- Bot `POST /channels/{forum_id}/threads` with starter-message content.
- Thread-ID stored.

**Unlink** clears the DB field only; does NOT delete the Discord thread.

## 5. Phase Breakdown

Phase numbering continues v1.12 (last phase 91). v1.13 spans **phases 92–98** (7 phases).

| # | Phase Name | Duration | Plans | Depends |
|---|---|---|---|---|
| 92 | Carry-Forwards & Cleanup | 1–2 d | 4 | — |
| 93 | Discord Foundation (Client + Config) | 2–3 d | 3 | 92 |
| 94 | Team Roles + Match Channel Lifecycle | 2–3 d | 3 | 93 |
| 95 | Match Channel Posts | 2–3 d | 4 | 94 |
| 96 | Provisional Graphic + Forum Threads | 2–3 d | 3 | 95 |
| 97 | Matchday-Level Posts | 2–3 d | 3 | 96 |
| 98 | Polish + E2E + Docs + Close | 2–3 d | 3 | 97 |

**Total:** 23 plans across 7 phases, ~15–20 working days estimated.

### Phase 92 — Carry-Forwards & Cleanup

**Goal:** v1.12 audit findings + bookkeeping drift resolved before Discord work starts.

- **92-01** UX-01 scope-extension to `CsvImportController`.
- **92-02** JaCoCo coverage cleanup (`RaceControllerCalendarTest` + Google service IOException IT coverage).
- **92-03** CLEAN-02 grep-predicate tightening.
- **92-04** Archive-glitch fix in `milestones/v1.12-REQUIREMENTS.md` + optional 89/90/91-VERIFICATION.md retrofill.

Success: JaCoCo ≥ 88.88%, 0 stale Pending markers in v1.12-REQUIREMENTS.md, CsvImportController parity with sibling controllers.

### Phase 93 — Discord Foundation

**Goal:** Working bot-rest client + webhook client + Discord-config admin page, no business logic yet.

- **93-01** `DiscordRestClient` (Spring `RestClient`) + `DiscordWebhookClient` + rate-limit interceptor + sealed exception hierarchy + `DiscordTimestamps` utility + `DiscordEmojiCache`.
- **93-02** Threat model + security surfaces: `application-local.yml` token setup, SSRF whitelist, log masking, CSRF coverage.
- **93-03** `/admin/discord-config` page: Flyway V8, `DiscordGlobalConfig` entity, form, Test-Connection + Test-Announcement + Refresh-Caches buttons.

Success: `./mvnw verify` green without live Discord token (WireMock-backed ITs); `/admin/discord-config` renders; test-connection works against live server (UAT); webhook secrets never appear in logs.

### Phase 94 — Team Roles + Match Channel Lifecycle

**Goal:** Match channels created/archived per-button with correct permission-overwrites.

- **94-01** Team discord-role mapping: Flyway V9 `teams.discord_role_id`, form field, optional live-dropdown via cached guild roles.
- **94-02** Match channel creation: Flyway V10 `matches.discord_*` + scheduling fields, Match-Form fields (teaser, stream-link, lobby-host, race-director, streamer), "Create Discord Channel" button with full permission-overwrite model, webhook creation, post-create permission-audit assertion.
- **94-03** Archive modal with category-picker (regex + count + default-suggestion of highest-num with `<50`).

Success: Test-server channel creation produces correct permission overwrites (audit assertion); category-full triggers typed `DiscordCategoryFullException`; archive modal sorted + filtered correctly.

### Phase 95 — Match Channel Posts

**Goal:** Team-Cards, Settings, Lineups, Schedule, Match-Results posts via webhook with edit-path.

- **95-01** Flyway V11 `discord_post` table, repository, `DiscordPostService.postOrEdit` pattern (find by `(channel_id, post_type, match_id)` → PATCH or POST).
- **95-02** Team Cards (one post with 2 attachments) + Settings + Lineups buttons.
- **95-03** Match Results post + re-post detection on final-marker change.
- **95-04** Schedule embed post + auto-edit-on-form-save when host/RD/streamer fields change.

Success: One Team-Cards post contains both PNGs; re-post edits in place (Discord edit-indicator visible, no duplicate); `/admin/discord/posts` lists all posts with filter.

### Phase 96 — Provisional Graphic + Forum Threads

**Goal:** New graphic generator replaces sheet-screenshot; forum-thread linking for race-results + standings.

- **96-01** `ProvisionalScoresGraphicService` + Thymeleaf template (pixel-accurate to existing screenshot layout).
- **96-02** Flyway V12 `seasons.discord_*_thread_id`, Season-detail "Discord Integration" section, Link-existing-Thread modal + Create-new-Thread modal.
- **96-03** Match-Detail "Post Provisional Scores" button + Race-Detail "Post Race Result to Forum-Thread" button (webhook with `?thread_id=` query param + thread-unarchive-if-archived).

Success: Provisional graphic visually verified via `playwright-cli` matches existing screenshot template; thread-linking picker shows active + archived threads; race-result posts land in correct forum-thread.

### Phase 97 — Matchday-Level Posts

**Goal:** All remaining 4 post types covered; full match-day workflow can run end-to-end.

- **97-01** Matchday Pairings + Match Previews (batch): structured Markdown post per match in announcement, auto-emoji-rendered "Game On!" line, attachments per match.
- **97-02** Matchday Overview + Power Rankings in season race-results forum-thread (not announcement, per user correction).
- **97-03** Standings post in season standings forum-thread.

Success: All 11 post types implemented; re-edit works for all types; `/admin/discord/posts` filterable by all dimensions.

### Phase 98 — Polish + E2E + Docs + Close

**Goal:** Production-ready, documented, milestone closed.

- **98-01** E2E test suite: Playwright + WireMock Discord simulator covering full match lifecycle (create channel → post all stages → archive).
- **98-02** Operator runbook `docs/operations/discord-integration.md`: bot-setup walkthrough with screenshots, OAuth-URL-generator, token rotation, troubleshooting for 4 typed error categories. README + Wiki update.
- **98-03** Milestone close: REQUIREMENTS.md traceability, rolling-summary PR body, MILESTONES.md v1.13 entry.

Success: E2E matchday lifecycle green in CI; operator runbook self-contained (someone without Discord experience can set up the bot); JaCoCo ≥ 88.88% maintained; v1.13 PR ready for squash-merge with `feat(v1.13): discord integration & carry-forwards` subject.

## 6. Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Bot-token leak via logs | Medium | High (token must be rotated) | `@ToString.Exclude`, log-pattern mask, SpotBugs scan, never in test code |
| Channel-permission bypass via wrong role-mapping | Low | High (opposing team sees match-channel) | Post-create permission-audit assertion in `DiscordChannelService` |
| Rate-limit burst on matchday-batch posting | High | Medium (some posts missed) | Token-bucket per `X-RateLimit-Bucket`, max-5-parallel, sequential batch with progress indicator |
| Discord-API breaking change | Low | Medium | `DiscordRestClient` encapsulates `/api/v10` path, migration to v11 localized |
| Bot-setup friction for operator | High | Low (one-time) | Phase 98 operator runbook with screenshot-step-by-step walkthrough |
| WireMock-Discord-simulator diverges from real Discord | Medium | Medium | Mandatory UAT in Phase 98 against live Discord with test-season + edge cases (empty forum, archived thread, full category) |
| `message_id` lock-in to webhook | Low | Low | All posts go through webhook (never bot-user); uniform edit-path |

## 7. Baselines Preserved

- **JaCoCo line coverage:** ≥ 88.88% (Phase 92 restores v1.11 baseline; subsequent phases must maintain or improve).
- **Test count:** ≥ 1696 (v1.12 baseline; Phase 92 adds ~10, Discord phases add ~50–80).
- **SpotBugs `BugInstance`:** 0 (blocking gate).
- **CodeQL gate-step:** exit 0 on new HIGH/CRITICAL.
- **Flyway:** V1–V7 immutable; v1.13 adds V8–V12.
- **CI E2E median:** 17:39 ± 20% (WireMock-only tests, no live Discord in CI).

## 8. Dependencies

**Zero new production dependencies.** Spring `RestClient` is Spring 6.1+ core (already on Spring Boot 4). Multipart via Spring's `MultipartBodyBuilder`. JSON via Jackson (already transitive). WireMock is existing test-scope.

## 9. Open Questions (Resolved During Brainstorming)

1. ~~Bot library or self-built?~~ → Self-built on Spring `RestClient`.
2. ~~Channel-naming convention?~~ → `md{N}-{teamA}-vs-{teamB}`.
3. ~~Archive trigger?~~ → Manual button per match.
4. ~~Globale channels config?~~ → announcement global; race-results + standings as Saison-scoped forum-threads.
5. ~~Trigger model?~~ → All button-triggered.
6. ~~Team-role mapping storage?~~ → `team.discord_role_id` field.
7. ~~Edit strategy?~~ → Webhook-PATCH using stored `message_id`.
8. ~~Provisional-scores graphic?~~ → App-generated (new `ProvisionalScoresGraphicService`).
9. ~~Match-preview teaser?~~ → Per-match `discordTeaser` free-text.
10. ~~Host/RD/Streamer edit?~~ → App-editable fields + auto-Webhook-PATCH on schedule-post.
11. ~~Inbound interaction?~~ → Out-of-scope (outbound-only).
12. ~~Archive category identification?~~ → Hardcoded regex `^Match Days Archive (?<year>\d{4})(?: \((?<num>\d+)\))?$`.
13. ~~Stream-link editability?~~ → Editable `match.stream_link` field + auto-PATCH on match-preview.
14. ~~Native Discord timestamps?~~ → `<t:N:STYLE>` via `DiscordTimestamps` utility, ZoneId from `app.timezone` config.
15. ~~Team-emoji storage?~~ → Convention-based via `team.shortName` + `DiscordEmojiCache` (no per-team DB field).
16. ~~Channel permissions?~~ → Explicit allow + deny bitmasks (no manage-rights, no voice-messages, full reactions + attachments + history).
17. ~~Team-cards post?~~ → One multipart post with both PNGs.
18. ~~Matchday-overview + power-rankings target?~~ → Race-results forum-thread (not announcement).
