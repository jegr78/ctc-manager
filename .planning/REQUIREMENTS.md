# Requirements: CTC Manager v1.13 Discord Integration & Carry-Forwards

**Defined:** 2026-05-20
**Core Value:** Architectural Consistency: All controllers delegate to services, exception handling is centralized, and the production environment is secured.

## Milestone v1.13 Requirements

Requirements for the v1.13 release. Each maps to a roadmap phase. Carry-forwards from the v1.12 audit are absorbed in Phase 92.

The Discord-integration design captures all 18 design decisions made during the 2026-05-20 brainstorming session — see [`docs/superpowers/specs/2026-05-20-discord-integration-design.md`](../docs/superpowers/specs/2026-05-20-discord-integration-design.md).

### Carry-Forwards from v1.12 Audit (Phase 92)

Closes v1.12 audit findings + a bookkeeping drift so v1.13 starts on a clean baseline.

- [x] **UX-01**: `CsvImportController` (race-results sheet-import) migrates to the typed-catch + `errorCategory` flash + badge UX pattern for parity with `DriverSheetImportController` + `RaceController`; re-closes the T-91-02-IL info-leak threat for the 3rd Google-Sheets-consuming controller; whitelisted `getUserMessage()` only (never `e.getMessage()`); 4 error categories — `transient`, `auth`, `not-found`, `permission` — each rendered with the existing badge styles from `admin.css`; verified by integration tests for all 4 paths + a regression assertion that no `e.getMessage()` echo appears in the flash content

- [x] **COV-01**: New `RaceControllerCalendarTest` covers the `RaceController` calendar-rendering branches that drove the v1.12 JaCoCo Δ−0.44 pp regression; new integration tests cover the `GoogleCalendarService` + `GoogleSheetsService` `IOException` defensive `catch` paths (root cause: Java-25 javac requires defensive `catch (GoogleApiException)` blocks since sealed-exhaustiveness on catch is not yet a Java 25 feature); JaCoCo line coverage returns to ≥ 88.88 % (v1.11 baseline) — verified by `target/site/jacoco/jacoco.csv` LINE_MISSED/LINE_COVERED computation

- [x] **CLEAN-01**: The `@Disabled`/`Assumptions.` regression-fence predicate (introduced by v1.12 CLEAN-02 + extended by v1.11 deferral docs) is tightened to `org\.junit\.jupiter\.api\.Assumptions` so it no longer false-positives on AssertJ `Assumptions.assumeThat` added by Phase 89 PERF-01 in `BackupStagingDirPerForkIT.java`; documented as a comment on the grep invocation pointing to this REQ-ID; verified by a synthetic positive (`Assumptions.assumeFalse` from JUnit triggers the fence) + a synthetic negative (`Assumptions.assumeThat` from AssertJ does NOT trigger the fence)

- [x] **DOCS-01**: Optional retroactive `89-VERIFICATION.md`, `90-VERIFICATION.md`, and `91-VERIFICATION.md` are authored in `.planning/milestones/v1.12-phases/{89,90,91}-*/` (v1.11 precedent: commit `2e84fd57`) to close the goal-backward `VERIFICATION.md` document-shape gap from the v1.12 audit; substantive verification is already in VALIDATION.md + per-plan SUMMARY.md — only the file-shape convention is the gap; each file follows the standard VERIFICATION.md template (Phase Goal Recap + Goal-Backward Walk-Through + Verification Outcome)

- [x] **BOOK-01**: `.planning/milestones/v1.12-REQUIREMENTS.md` bookkeeping drift fixed — 7 stale `[ ]` checkboxes flipped to `[x]` (PERF-01, PERF-02, PERF-03, PERF-04, PERF-05, PERF-06, UX-01) and 4 stale `Pending` traceability rows flipped to `Resolved` (matches the post-merge state already captured in v1.12 MILESTONE-AUDIT.md); verified by `grep -c "Pending" milestones/v1.12-REQUIREMENTS.md` returning 0 and `grep -c "^- \[ \]" milestones/v1.12-REQUIREMENTS.md` returning 0

### Discord Foundation (INFRA, Phase 93)

Spring `RestClient` + Webhook client + sealed exception hierarchy + admin-config page. No business logic — just the platform on which Phases 94–97 build.

- [x] **INFRA-01**: `org.ctc.discord.DiscordRestClient` (Spring `RestClient`) authenticates with `Authorization: Bot $token`, encapsulates `/api/v10` versioning, and exposes typed methods for guild-role-list, guild-emoji-list, channel-create, channel-modify, channel-list, thread-list-active, thread-list-archived, thread-create; `DiscordWebhookClient` exposes execute-webhook (with multipart for image-attachments) and edit-webhook-message methods; both clients route exceptions through a sealed `DiscordApiException` hierarchy with 4 permits (`Transient`, `Auth`, `NotFound`, `CategoryFull`); `DiscordRateLimitInterceptor` implements per-bucket token-bucket with 429 retry-after handling and exponential 5xx backoff; `DiscordTimestamps` utility generates `<t:UNIX:STYLE>` for 5 styles (F, f, D, d, t, R) from `LocalDateTime` + `ZoneId`; `DiscordEmojiCache` resolves `team.shortName` to `<:name:id>` long-form syntax with 60-minute TTL; verified by WireMock-backed integration tests covering all 4 exception paths + rate-limit retry + multipart-upload + emoji-lookup-cache-refresh

- [x] **INFRA-02**: Threat model surfaces are addressed before any feature code lands: (a) `application-local.yml` documents `DISCORD_BOT_TOKEN` env-var pattern (analog to `GOOGLE_CALENDAR_ID`); (b) `app.discord.allowed-hosts=discord.com` whitelist enforced on all outbound calls (analog to v1.5 SSRF pattern); (c) logging-pattern mask redacts webhook URLs matching `https://discord.com/api/webhooks/[^/\s]+/[^/\s]+`; (d) `@ToString.Exclude` on `Match.discordChannelWebhookUrl` and any future webhook-secret entity field; (e) all `POST /admin/discord/**` endpoints are CSRF-protected (Phase 30 pattern); (f) `DiscordConfigForm` DTO replaces direct entity-binding (Phase 29 mass-assignment pattern); verified by SpotBugs (no findings), log-snapshot test (webhook URL never appears unmasked), and ZAP-style CSRF scan on every new endpoint

- [x] **INFRA-03**: `/admin/discord-config` page (Flyway V8 `discord_global_config` table) provides operator surface for: guild-ID input, bot-token-status indicator (read-only ✓/✗ derived from `RestClient.get('/users/@me')`), announcement-webhook-URL input, race-results-forum-channel-ID input, standings-forum-channel-ID input, vs-emoji-name input (default `CTC`), and 4 test/refresh buttons (Test Connection, Test Announcement-Webhook, Refresh Server-Roles Cache, Refresh Emoji Cache); verified by Playwright E2E filling the form + clicking each button + asserting WireMock receives the expected outbound calls and the page renders the success badges

### Discord Channel Lifecycle (CHAN, Phase 94)

Team-Discord-role mapping + match-channel creation with full permission-overwrite model + archive-modal honoring the 50-channels-per-category limit.

- [x] **CHAN-01**: `teams.discord_role_id VARCHAR(32)` column (Flyway V9) is added and Team-Form gains a `discordRoleId` field with snowflake-format validation (`^\d{17,20}$`); the field accepts plain-text input AND offers a live-dropdown of all guild-roles when the Discord-Bot is reachable (dropdown sourced from `DiscordRestClient.fetchGuildRoles()` 60-min cache); operator can clear the field to disable Discord-channel-creation for that team; verified by repository IT (round-trip persistence), validation test (rejects non-snowflake input), and dropdown-renders-in-the-presence-of-cache E2E

- [x] **CHAN-02**: Match-channel creation surface delivers (a) Flyway V10 migration adding `matches.discord_channel_id`, `matches.discord_channel_webhook_url`, `matches.discord_teaser`, `matches.stream_link`, `matches.lobby_host`, `matches.race_director`, `matches.streamer`; (b) Match-Form fields for `discordTeaser` (textarea, max 2000 chars, Markdown allowed), `streamLink` (plain-text, accepts `<#channelId>`, URL, or blank → "TBD"), `lobbyHost`, `raceDirector`, `streamer`; (c) "Create Discord Channel" button on the Match-Detail page (visible only when `match.discordChannelId == null` AND both teams have `discordRoleId` AND season has current-category set) that triggers Bot channel-creation with name `md{N}-{teamA.shortName}-vs-{teamB.shortName}` (lowercase, dash-separated, Discord-enforced), permission-overwrites per the design spec table (allow View+Send+React+Attach+Embed+History+ExternalEmojis+ExternalStickers; deny Voice+ManageChannels+ManageMessages+ManageThreads+ManageWebhooks+CreateInvite+MentionEveryone), webhook-creation in the new channel, and storage of both IDs; (d) a post-create permission-audit assertion that fetches the just-created channel and verifies only the 2 whitelisted team-roles have View permission (any other role with View triggers `DiscordAuthException`); verified by integration test covering happy-path channel-create + audit-mismatch failure-path

- [x] **CHAN-03**: Archive-modal on the Match-Detail page provides operator-driven category-selection: (a) `DiscordCategoryResolver` matches Discord guild categories against regex `^Match Days Archive (?<year>\d{4})(?: \((?<num>\d+)\))?$`; (b) modal lists only categories where `year == match.season.year`, sorted ascending by `num` (no-num = 1); (c) per category, live channel-count is fetched and displayed as `current/50` (e.g. `Match Days Archive 2026 (1)` → "47/50"); (d) default-selection is highest-num with `< 50` channels; (e) user override-selectable; (f) on confirm, Bot patches channel `parent_id` to selected category; (g) if all categories full, `DiscordCategoryFullException` rendered with `category-full` badge linking operator-runbook section "Creating a new archive category"; verified by integration tests covering: regex-match (multiple year-suffix variants), count-display, default-selection (full-vs-empty cases), patch-call, full-category exception path

### Match-Channel Posts (POST, Phase 95)

5 post types posted to the per-match channel with stored `message_id` for in-place editing.

- [x] **POST-01**: Flyway V12 `discord_post` table (channel_id, message_id, webhook_id, webhook_token, post_type, match_id, matchday_id, race_id, season_id, posted_at, updated_at) + FK-indexes per project convention; `DiscordPostService.postOrEdit(channelId, postType, payload, attachments)` looks up existing post by `(channel_id, post_type, foreign_key)` and routes to Webhook-PATCH if found, Webhook-POST otherwise, then stores/updates the row; uniform error-handling routes all 4 sealed-exception cases through the controller flash-badge pattern from INFRA-01; `/admin/discord/posts` page lists all entries filterable by season, match, type — each row has Re-Edit + Re-Post buttons; verified by IT for both create and edit paths + Playwright E2E for the listing page filters

- [x] **POST-02**: "Post Team Cards" button on Match-Detail issues ONE multipart Webhook-POST containing both team-card PNGs as `files[0]` + `files[1]` (Discord allows up to 10 attachments per message); resulting `discord_post` row has `post_type=TEAM_CARDS` and a single `message_id`; re-post replaces both attachments in the existing message via Webhook-PATCH; verified by IT asserting the multipart body has both files + the resulting message renders both side-by-side (WireMock body-pattern assertion)

- [x] **POST-03**: "Post Settings Graphic" and "Post Lineups Graphic" buttons on Match-Detail trigger `SettingsGraphicService.render()` / `LineupGraphicService.render()` → PNG byte-array → multipart Webhook-POST; each lands in its own `discord_post` row with type `SETTINGS` / `LINEUPS`; re-post path edits the existing message (replacing the attachment via Webhook-PATCH); the buttons are visible only when the upstream form data is present (settings filled / RaceLineup populated); verified by IT for both post + re-post paths

- [x] **POST-04**: "Post Match Results" button posts the `MatchResultsGraphicService` PNG to the match-channel; re-post is automatically detected when the underlying race-result is corrected post-stewarding (the existing `discord_post` row's `updated_at` is older than `match.lastModifiedAt`); button label changes to "Update Match Results" when an existing post is detected; verified by IT for post + auto-detected re-post + stale-detection-only-on-data-change

- [x] **POST-05**: "Post Schedule Message" button posts a Discord embed to the match-channel containing 4 fields (Date with `<t:N:F> (<t:N:R>)` relative, Lobby Host, Race Director, Streamer); empty host/RD/streamer fields render as `_TBD_`; the button is visible when `match.races[].dateTime` min is set; on Match-Form save, if any of `lobbyHost` / `raceDirector` / `streamer` changed AND a `SCHEDULE` post exists, Webhook-PATCH is auto-triggered to refresh the embed (no separate button needed); verified by IT covering: initial post with TBD placeholders, edit-on-form-save when any host field updates, no edit when fields unchanged

### Provisional Graphic + Forum Threads (GRAFX/FORUM, Phase 96)

New `ProvisionalScoresGraphicService` eliminates today's manual sheet-screenshot; forum-thread linking surface lets the operator connect each season to its race-results and standings threads.

- [x] **GRAFX-01**: New `ProvisionalScoresGraphicService` analog to `MatchResultsGraphicService` renders a PNG that matches today's manually-screenshotted Google-Sheets layout (pixel-accurate per the existing screenshot reference); Thymeleaf template `src/main/resources/templates/admin/provisional-scores-render.html` follows the `*-render.html` convention from other graphic services; "Post Provisional Scores" button on Match-Detail visible when race-result data exists but match is not yet marked final; verified by Playwright visual-regression test (Desktop + Mobile per `feedback_playwright_cli`) + IT for the post pipeline

- [x] **FORUM-01**: Flyway V13 adds `seasons.discord_race_results_thread_id` + `seasons.discord_standings_thread_id`; Season-Detail page gains a "Discord Integration" section with 2 thread-linker widgets (race-results + standings), each providing: (a) read-only display of currently-linked thread (name + ID) or "not linked"; (b) "Link existing Thread..." modal listing all threads from the corresponding forum-channel (active + archived, sorted: pinned first, then active, then archived by last-message-timestamp desc) via `DiscordForumService.listThreads(forumChannelId)`; (c) "Unlink" button clearing only the DB field, leaving the Discord thread untouched; operator-workflow note: to add a new thread, the operator creates it directly in the Discord forum-channel, then links it via the modal in (b) — in-app thread-creation is not built (YAGNI per v1.13 milestone audit 2026-05-25; backend `DiscordRestClient.createThread()` removed in Plan 99-05); verified by IT for list-existing + unlink + the modal-listing reflecting both active and archived threads correctly

- [x] **FORUM-02**: "Post Race Result to Forum-Thread" button on Race-Detail posts the existing `RaceGraphicService` PNG to `season.discordRaceResultsThreadId` via Webhook with `?thread_id={id}` query param; if the thread is archived, the bot first issues PATCH `/channels/{id}` `archived=false` to unarchive (Discord requires this for posting), then posts, then optionally re-archives (configurable, default no); applies to both Race-Result and Provisional-Scores Forum-Thread posts; verified by IT covering: post-to-active-thread + auto-unarchive-on-archived-thread + thread-id-query-param-presence

### Matchday-Level Posts (POST cont., Phase 97)

The 3 remaining post types covering matchday-pairings, match-previews, and matchday-aggregates.

- [x] **POST-06**: "Post Match Preview" per-match button on Match-Detail posts ONE multipart Webhook to the announcement-webhook with: structured Markdown body (H1 = `# {season.name}`, H2 = `## {matchday.label}`, H3 = `### {teamA.shortName} vs. {teamB.shortName}`, body = `{match.discordTeaser}`, bullet `- Date: <t:N:F>` via `DiscordTimestamps.longDateTime` from first race, bullet `- Stream: {match.streamLink ?: "TBA"}`, line `Game On! {emoji(teamA.shortName)} {emoji(vsEmojiName)} {emoji(teamB.shortName)}` resolved via `DiscordEmojiCache`) AND N×2 PNG attachments (`settings-md{N}.png` + `lineups-md{N}.png` per race in the match); recorded as ONE `MATCH_PREVIEW` discord_post row scoped to `(announcementChannelId, matchId)`; auto-edit hook on `MatchService.updateDiscordFields` publishes `MatchPreviewFieldsChangedEvent` AFTER_COMMIT when `streamLink` or `discordTeaser` diverges, listener PATCHes the existing row; `MATCHDAY_PAIRINGS` enum value retained but dropped from scope (deferred to v1.14 per CONTEXT D-97-PREV-2 / Out of scope); verified by IT for multipart-POST + Markdown structure + 4 sealed-exception permits + Mockito unit for diff/publish + E2E for button state transitions

- [x] **POST-07a**: "Post Match Day Results" button on Matchday-Detail (new Discord Actions card) posts `MatchdayResultsGraphicService.generateResults(matchday)` PNG via Webhook to the season's race-results forum-thread (`raceResultsForumWebhookUrl` + `?thread_id=season.discordRaceResultsThreadId`); recorded as `MATCHDAY_OVERVIEW` discord_post row with `matchday_id` FK; pre-flight gates on `allMatchesFinal` AND `threadLinked` AND `webhookConfigured`; stale-detection signals "Update Match Day Results" when ≥1 `RaceResult.updatedAt` > `post.updatedAt`; verified by IT for multipart-POST + `?thread_id` assertion + pre-flight branches + 4 sealed exception permits + 1 BusinessRule permit; auto-unarchive-before-post inherited from `postOrEdit` shared path (D-96-FOR-4).

- [x] **POST-07b**: "Post Power Rankings" button on Matchday-Detail (same Discord Actions card, sibling button) posts `PowerRankingsGraphicService.generateRankings` PNG (current `SeasonTeam.rating` DESC order, `subtitle = matchday.label`, `year` + `number` from `matchday.getSeason()`) via Webhook to the SAME forum-thread; recorded as separate `POWER_RANKINGS` discord_post row with `matchday_id` FK; pre-flight LOOSER than POST-07a — gates ONLY on `threadLinked` AND `webhookConfigured` (no `allMatchesFinal` — operator-curated ratings per D-97-MD-1 / D-97-PHA-3); stale-detection signals "Update Power Rankings" when MAX(`SeasonTeam.updatedAt`) > `post.updatedAt`; verified by IT + E2E sibling-button-state matrix.

- [x] **POST-08**: "Post Standings" per-phase button on Season-Edit (#discordIntegration card) posts `StandingsGraphicService.generateStandingsBytes(season, phase)` PNG(s) via Webhook to `season.discordStandingsThreadId` (`?thread_id=`) — multipart with 1 PNG for REGULAR-non-GROUPS / PLAYOFF / PLACEMENT, N PNGs sorted by `SeasonPhaseGroup.sortIndex` ASC for REGULAR-with-GROUPS; ONE `STANDINGS` discord_post row per `(season_id, phase_id)` identity-key via V14 FK migration (`ON DELETE SET NULL`); Re-Post replaces all N attachments atomically; phase-selector `<select>` auto-hides when season has exactly 1 phase (hidden `<input>` instead); per-phase stale-detection signal "Update Standings" when `StandingsService.hasNewerResultsSincePhaseScoped` returns true; `PostStandingsForm` DTO with `@NotNull phaseId` prevents Mass Assignment via `SeasonPhase` entity; dynamic 1920×1080 graphic layout (CTC logo + team logos + primary color strip, font-/row-sizes auto-scale 14+ teams without overflow); verified by IT for all 4 phase-layout combinations + V14 migration + `SeasonRef` widening contract + Mass-Assignment-safe form binding + E2E for multi-phase dropdown UX + iterative graphic-design loop with operator visual approval.

- [x] **POST-09**: "Post Matchday Pairings" button on Matchday-Detail (new "Discord Announcements" card) posts a hybrid Markdown+PNG to the global Announcement-Channel webhook (`discord_global_config.announcement_webhook_url`); Markdown body uses an operator-editable per-config template (default seeded via `DiscordPostService.DEFAULT_MATCHDAY_PAIRINGS_TEMPLATE` with placeholders `{{matchdayNumber}}`, `{{deadline}}`, `{{weekend}}`, `{{ctcEmoji}}` — the latter resolved via `DiscordEmojiCache.emojiFor(vsEmojiName)` to `<:CTC:id>` long-form); PNG attachment generated by `MatchdayPairingsGraphicService` (Playwright-runtime, JaCoCo-excluded); recorded as `MATCHDAY_PAIRINGS` discord_post row scoped to `matchday_id`; pre-flight predicate `canPostMatchdayPairings` gates on `announcementWebhookConfigured` AND `pickDeadlineSet` AND `scheduledWeekendSet` AND `allTeamsAssigned`; stale-detection signal "Update Matchday Pairings" when `matchday.updatedAt > post.updatedAt`; Flyway V15 adds `matchdays.pick_deadline`, `matchdays.scheduled_weekend`, `discord_global_config.matchday_pairings_template`; operator-driven Re-Post / Update (NO AFTER_COMMIT-hook); verified by `DiscordPostServiceMatchdayPairingsIT` (6 tests incl. emoji-cache resolution + body-size>1024 assertion) + Mockito unit pre-flight matrix + `MatchdayDetailDiscordAnnouncementE2ETest` button-state cases + E2E live-UAT Stage 14 (Initial-POST + Re-Post-PATCH same messageId).

- [x] **POST-10**: "Post Matchday Schedule" button on Matchday-Detail (sibling to Pairings in the "Discord Announcements" card) posts a pure-multipart PNG (no Markdown, no embed) to the global Announcement-Channel webhook; PNG generated by `MatchdayScheduleGraphicService` (Playwright-runtime, JaCoCo-excluded); recorded as `MATCHDAY_SCHEDULE` discord_post row scoped to `matchday_id`; pre-flight predicate `canPostMatchdaySchedule` gates on `announcementWebhookConfigured` AND every match's first `Race.dateTime` set; stale-detection signal "Update Matchday Schedule" when MAX(`match.updatedAt`, `race.updatedAt`) > `post.updatedAt`; no schema migration (re-uses Flyway V15 webhook config); operator-driven Re-Post / Update (NO AFTER_COMMIT-hook, per D-98-AUTO-1 — PNG-Re-Render Playwright-teuer); verified by `DiscordPostServiceMatchdayScheduleIT` (4 tests incl. pure-multipart assertion `body.doesNotContain("\"content\":")` + `body.doesNotContain("\"embeds\":")`) + Mockito unit pre-flight matrix + `MatchdayDetailDiscordAnnouncementE2ETest` button-state cases + E2E live-UAT Stage 15 (Initial-POST + Re-Post-PATCH same messageId).

### Polish + E2E + Docs (E2E/DOCS, Phase 98)

End-to-end test of the full match-lifecycle + operator runbook + milestone close.

- [x] **E2E-01**: A Playwright + WireMock-backed Discord-simulator integration test exercises the complete matchday lifecycle in one suite: create-channel → post-team-cards → post-settings → post-lineups → post-schedule → post-provisional → post-final-results → move-to-archive — all clicks driven through the admin UI, all Discord-side calls intercepted by WireMock with realistic response payloads (snowflake IDs, webhook responses, rate-limit headers); verified by the test suite passing in CI under the `e2e` profile + WireMock recorded-payloads asserting the expected request bodies for all 8 stages

- [x] **DOCS-02**: `docs/operations/discord-integration.md` operator runbook is published containing: (a) Bot-application setup walkthrough with annotated screenshots (Discord-Developer-Portal → Application → Bot tab → Token + Public-Bot-off + Required-Permissions checklist); (b) OAuth-invite-URL generator with the exact permission bitmask required (`MANAGE_CHANNELS | MANAGE_ROLES | MANAGE_WEBHOOKS | VIEW_CHANNEL | SEND_MESSAGES | ATTACH_FILES | EMBED_LINKS | READ_MESSAGE_HISTORY | MANAGE_THREADS`); (c) Token-rotation procedure; (d) Webhook-URL retrieval procedure for the announcement channel; (e) Forum-channel + thread setup for race-results + standings (matching the screenshots already in the design spec); (f) Troubleshooting section for all 4 typed error categories (`transient`, `auth`, `not-found`, `category-full`) with diagnostic steps and remediation; verified by a manual UAT walking through the runbook with a fresh Discord-Developer-Portal account (operator-time recorded in the UAT result)

- [x] **DOCS-03**: `README.md` and the project Wiki are updated with the Discord-Integration feature description (`feedback_docs_update`) — short bullet under "Admin Features" pointing to the operator runbook; sample screenshot of `/admin/discord-config`; the changelog reference for v1.13; verified by both files containing the canonical Discord-Integration paragraph + a working link to `docs/operations/discord-integration.md`

## Out of Scope (Explicitly Deferred)

The following are explicitly excluded from v1.13. Adding them would scope-creep beyond the brainstorming-approved boundary. They may be picked up in future milestones.

- **Inbound Discord interaction** — slash commands, polls, reaction-reads. The app is local-only, no always-online endpoint. Reaction-based voting would require either a Gateway-WebSocket (always-online) or a public HTTP-Interactions-Endpoint (not feasible for local app). Out-of-scope until the deployment model changes.
- **Multi-Server / Multi-Guild support** — the app assumes exactly 1 Discord guild. Multi-region or multi-community support would require a guild-FK propagated through `discord_global_config`, `matches.discord_channel_id`, etc. Out-of-scope; would be its own milestone.
- **Settings-form migration from Google Form to in-app form** — home team continues filling the existing Google Form, app continues reading it via `GoogleSheetsService`. Migrating the form itself would require building a per-match settings-input page + auth model for team-captains; out-of-scope for v1.13.
- **Auto-trigger on DB events** — no "race-result-save → auto-post-to-Discord" wiring. All Discord posting is button-triggered by the admin operator. This is a deliberate operator-control choice from the brainstorming session, not a technical limitation.
- **Discord-notification backend for the public site** — Discord posts are not mirrored to `docs/site`. The public site continues to be its own publishing channel.
- **Per-user timezone override in the admin UI** — `app.timezone` is server-global. Per-user TZ would require user-accounts which the app does not have (single-operator model).
- **Settings-grafik dynamic from Google-Form fields** — the existing `SettingsGraphicService` workflow (data → graphic → post) is preserved unchanged; v1.13 only adds the Discord-post button on top.

## Future Requirements (Acknowledged, Not Scoped)

Items raised during brainstorming that are worth tracking but not for v1.13:

- **DISC-FUTURE-01**: Inbound slash-command support if the deployment model changes (e.g. always-online Docker deployment); would expose `/standings`, `/next-match`, `/team-info` etc. as Discord-User-triggered queries
- **DISC-FUTURE-02**: Auto-trigger pipeline (race-save → post) once the post-edit confidence is established and the operator wants less manual ceremony
- **DISC-FUTURE-03**: Settings-form migration into the admin app — team captains submit settings via app instead of Google Form, app auto-generates settings-graphic + auto-posts on submission
- **DISC-FUTURE-04**: Multi-guild support for a hypothetical second league or sub-community
- **DISC-FUTURE-05**: Discord-notification webhook surface for the public site to mirror major announcements (matchday-pairings, season-end) into Discord automatically from public-site events

## Traceability

| REQ-ID | Phase | Status |
|---|---|---|
| UX-01 | 92 | Resolved |
| COV-01 | 92 | Resolved |
| CLEAN-01 | 92 | Resolved |
| DOCS-01 | 92 | Resolved |
| BOOK-01 | 92 | Resolved |
| INFRA-01 | 93 | Resolved |
| INFRA-02 | 93 | Resolved |
| INFRA-03 | 93 | Resolved |
| CHAN-01 | 94 | Resolved |
| CHAN-02 | 94 | Resolved |
| CHAN-03 | 94 | Resolved |
| POST-01 | 95 | Resolved |
| POST-02 | 95 | Resolved |
| POST-03 | 95 | Resolved |
| POST-04 | 95 | Resolved |
| POST-05 | 95 | Resolved |
| GRAFX-01 | 96 | Resolved |
| FORUM-01 | 96 | Resolved |
| FORUM-02 | 96 | Resolved |
| POST-06 | 97 | Resolved |
| POST-07a | 97 | Resolved |
| POST-07b | 97 | Resolved |
| POST-08 | 97 | Resolved |
| E2E-01 | 98 | Resolved |
| DOCS-02 | 98 | Resolved |
| DOCS-03 | 98 | Resolved |
| POST-09 | 98 | Resolved |
| POST-10 | 98 | Resolved |

**Total:** 28 requirements across 7 phases (92–98). Phases 99/100/101 are decision-driven and close separately against their CONTEXT.md decisions (D-01..D-08 / D-01..D-14 / D-01..D-17).
