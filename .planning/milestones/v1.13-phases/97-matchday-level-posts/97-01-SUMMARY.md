---
phase: 97-matchday-level-posts
plan: 01
subsystem: discord-integration
tags: [discord, webhook, match-preview, auto-edit, application-event, multipart-post]

requires:
  - phase: 95-discord-post-buttons
    provides: DiscordPostService.postOrEdit + matchHasCompleteSettings/Lineups + Pre/Post-Diff schedule auto-edit pattern + Phase-95 button cluster shape
  - phase: 96-discord-forum-thread-integration
    provides: SeasonRef sealed-permit precedent + auto-unarchive shared code path (unused here but verified)
  - phase: 93-discord-infrastructure
    provides: DiscordWebhookClient + DiscordHostValidator + DiscordRateLimitInterceptor + DiscordEmojiCache + DiscordTimestamps + DiscordGlobalConfigService
provides:
  - Per-match "Post Match Preview" button on /admin/matches/{id} targeting the announcement webhook
  - One MATCH_PREVIEW discord_post row per match (channelId from parseWebhookUrl(announcementWebhookUrl), matchId FK)
  - Auto-edit hook on MatchService.updateDiscordFields when discordTeaser or streamLink diverge
  - Reusable resolveAnnouncementChannelId(webhookUrl) helper on DiscordPostService
affects: [Plan 97-02 (Matchday Results buttons share the global-config + populateDiscordModel pattern), Plan 97-03 (POST-08 Standings reuses Pre-Flight DTO record shape)]

tech-stack:
  added: []
  patterns:
    - "Spring application event for cross-class auto-edit: MatchPreviewFieldsChangedEvent → DiscordAutoPostListener.onMatchPreviewFieldsChanged (AFTER_COMMIT + REQUIRES_NEW)"
    - "Pre-flight DTO record (MatchPreviewPreFlightResult) returning (canPost, disabledReason) — top-down predicate evaluation with first-failure-wins tooltip string"

key-files:
  created:
    - src/main/java/org/ctc/discord/event/MatchPreviewFieldsChangedEvent.java
    - src/main/java/org/ctc/admin/dto/MatchPreviewPreFlightResult.java
    - src/test/java/org/ctc/domain/service/MatchServicePreviewDiffPublishTest.java
    - src/test/java/org/ctc/discord/service/DiscordPostServiceMatchPreviewIT.java
    - src/test/java/org/ctc/discord/service/DiscordPostServiceMatchPreviewAutoEditIT.java
    - src/test/java/org/ctc/admin/controller/MatchControllerPostMatchPreviewIT.java
    - src/test/java/org/ctc/e2e/discord/matchday/MatchDetailPreviewButtonE2ETest.java
  modified:
    - src/main/java/org/ctc/domain/service/MatchService.java
    - src/main/java/org/ctc/discord/service/DiscordPostService.java
    - src/main/java/org/ctc/discord/service/DiscordAutoPostListener.java
    - src/main/java/org/ctc/admin/controller/MatchController.java
    - src/main/resources/templates/admin/match-detail.html
    - src/test/java/org/ctc/discord/service/DiscordPostServicePreFlightTest.java
    - src/test/java/org/ctc/discord/service/DiscordPostServiceRefBranchesTest.java
    - src/test/java/org/ctc/admin/controller/MatchControllerMoveToArchiveErrorCategoryTest.java
    - .planning/REQUIREMENTS.md
    - .planning/phases/97-matchday-level-posts/97-UI-SPEC.md

key-decisions:
  - "Auto-edit hook rebuilds the full multipart payload (Markdown + N×2 PNG attachments) and PATCHes via webhookClient.editMessageWithAttachments — attachments_replaced_at advances on every save. Re-uploading PNGs is intentional per Plan task 2 action; the Discord webhook rate-limit interceptor (Phase 93) handles burst protection."
  - "resolveAnnouncementChannelId(webhookUrl) public helper on DiscordPostService keeps parseWebhookUrl package-private — controllers cannot reach in directly. Used by both detail-model enrichment and the future Plan 97-03 standings flow."
  - "Pre-flight predicates evaluated top-down per CONTEXT D-97-PREV-2c; first failure wins as the disabled-tooltip string. Order: teaser → settings → lineups → race date → webhook configured."
  - "Optional .discord-post-status--auto-edit CSS pill omitted — the per-match form sequence already communicates state via the three button variants; no extra discoverability hint shipped."

patterns-established:
  - "Pre-flight DTO record: (boolean canPost, @Nullable String disabledReason) returned by service-layer canPostX(Match) — controller passes the record directly to the template as a model attribute, template branches on canPost() / disabledReason()."
  - "Per-event auto-edit hook: domain service Pre/Post-Diff → ApplicationEventPublisher.publishEvent(XFieldsChangedEvent) → DiscordAutoPostListener.onXFieldsChanged (@TransactionalEventListener AFTER_COMMIT + @Transactional REQUIRES_NEW) → DiscordPostService.autoEditXIfNeeded(entity). Reusable for Plan 97-02 + future hooks."

requirements-completed: [POST-06]

duration: 102min
completed: 2026-05-24
---

# Phase 97 Plan 01: Match Preview Button + Auto-Edit Summary

**Per-match "Post Match Preview" announcement button ships POST-06: one click posts a structured Markdown message with N×2 attachments to the announcement webhook, and later teaser/streamLink edits auto-PATCH the existing message.**

## Performance

- **Duration:** ~102 min (interactive mode, 3 sequential tasks)
- **Tasks:** 3
- **Files modified:** 16 (5 production, 7 test, 2 planning artifacts, 2 existing tests touched for constructor signature)
- **Tests added:** 30 (6 unit + 19 integration + 5 E2E)
- **Coverage:** 89.03% line coverage (above 82% threshold)

## Accomplishments

- Operator-visible Post Match Preview button cluster on `/admin/matches/{id}`: 3 states (disabled with pre-flight tooltip, primary post, secondary re-post) gated by `discordAnnouncementsConfigured`.
- One round-trip multipart Webhook-POST to `announcementWebhookUrl` with Markdown body (`# season.name` / `## matchday.label` / `### homeShort vs. awayShort` / teaser / `- Date:` / `- Stream:` / `Game On!` emoji line) plus `settings-mdN.png` + `lineups-mdN.png` per race.
- Auto-edit hook: `MatchService.updateDiscordFields` publishes `MatchPreviewFieldsChangedEvent` after commit when teaser/streamLink diverge; `DiscordAutoPostListener` PATCHes the existing `MATCH_PREVIEW` row via `DiscordPostService.autoEditMatchPreviewIfNeeded`.
- REQUIREMENTS.md POST-06 row rewritten to the simplified per-match scope; UI-SPEC.md batch flow + matchday-detail POST-06 sections deleted, H2/H3 Markdown updated to `{matchday.label}` and `{teamA.shortName}`.

## Task Commits

1. **Task 1: event + DTO + MatchService diff/publish** — `59e251be` (feat)
2. **Task 2: DiscordPostService methods + AutoPostListener + 2 ITs** — `0514c485` (feat)
3. **Task 3: Controller endpoint + template + UI-SPEC/REQUIREMENTS revisions + 1 IT + 1 E2E** — pending (this commit)

## Files Created/Modified

### New production files
- `src/main/java/org/ctc/discord/event/MatchPreviewFieldsChangedEvent.java` — single-field record, mirrors MatchScheduleFieldsChangedEvent.
- `src/main/java/org/ctc/admin/dto/MatchPreviewPreFlightResult.java` — `(boolean canPost, @Nullable String disabledReason)` record.

### Modified production files
- `src/main/java/org/ctc/domain/service/MatchService.java` — adds Pre/Post-Diff of `discordTeaser` + `streamLink` and event publish after `matchRepository.save`. Both schedule + preview events publish independently when their respective fields changed.
- `src/main/java/org/ctc/discord/service/DiscordPostService.java` — adds `canPostMatchPreview`, `postMatchPreview`, `autoEditMatchPreviewIfNeeded`, `resolveAnnouncementChannelId`, `buildMatchPreviewMarkdown`, `buildMatchPreviewAttachments`. Injects `DiscordEmojiCache` (SuppressFBWarnings justification updated).
- `src/main/java/org/ctc/discord/service/DiscordAutoPostListener.java` — `onMatchPreviewFieldsChanged` listener (AFTER_COMMIT + REQUIRES_NEW); same recordRequestAttribute error-routing pattern as the schedule listener.
- `src/main/java/org/ctc/admin/controller/MatchController.java` — injects `DiscordGlobalConfigService`; `detail()` enriches model with `discordAnnouncementsConfigured`, `matchPreviewPost`, `matchPreviewPreFlight`; new `@PostMapping("/{id}/post-match-preview")` endpoint mirrors `postSchedule`.
- `src/main/resources/templates/admin/match-detail.html` — APPENDS `<div th:if="${discordAnnouncementsConfigured}">` with disabled span + initial post form + re-post form INSIDE the existing `.discord-actions--posts` cluster (no rewrites). No new inline styles.

### New test files
- `src/test/java/org/ctc/domain/service/MatchServicePreviewDiffPublishTest.java` — 6 Mockito unit tests covering teaser-only, streamLink-only, both, identical, dual schedule+preview, and null-to-null scenarios.
- `src/test/java/org/ctc/discord/service/DiscordPostServiceMatchPreviewIT.java` — 7 @SpringBootTest ITs covering happy path, null streamLink → TBA, 4 sealed DiscordApiException permits (429/401/404/403), and missing-teaser BusinessRuleException.
- `src/test/java/org/ctc/discord/service/DiscordPostServiceMatchPreviewAutoEditIT.java` — 5 ITs covering existing-row PATCH, no-row no-op, missing-webhook no-op, no-race-time no-op, attachments_replaced_at advancement.
- `src/test/java/org/ctc/admin/controller/MatchControllerPostMatchPreviewIT.java` — 7 MockMvc ITs covering happy-path redirect + flash, 4 error-category flashes, GET enrichment with webhook + without webhook.
- `src/test/java/org/ctc/e2e/discord/matchday/MatchDetailPreviewButtonE2ETest.java` — 5 Playwright E2E tests covering 3 desktop states + announcements-not-configured + mobile (375×667).

### Modified test files
- `src/test/java/org/ctc/discord/service/DiscordPostServicePreFlightTest.java` — added DiscordEmojiCache mock arg to direct constructor invocation.
- `src/test/java/org/ctc/discord/service/DiscordPostServiceRefBranchesTest.java` — added DiscordEmojiCache mock arg.
- `src/test/java/org/ctc/admin/controller/MatchControllerMoveToArchiveErrorCategoryTest.java` — added DiscordGlobalConfigService mock arg to both controller constructions.

### Planning artifacts
- `.planning/REQUIREMENTS.md` — POST-06 row rewritten from batch-pairings-+-batch-previews to single per-match Post Match Preview; MATCHDAY_PAIRINGS noted as deferred to v1.14.
- `.planning/phases/97-matchday-level-posts/97-UI-SPEC.md` — surgical revisions: POST-06 matchday-detail batch + modal sections deleted, replaced with per-match Match-Detail cluster; Markdown H2 → `## {matchday.label}`, H3 → `### {teamA.shortName} vs. {teamB.shortName}`, Stream-bullet fallback `TBD` → `TBA`; model-contract split into MatchController (POST-06) + MatchdayController (POST-07); Click-Order section dropped; Auto-Edit hook rewritten to reference MatchPreviewFieldsChangedEvent; Checker Sign-Off note added.

## Verification

- `./mvnw -Dtest=MatchServicePreviewDiffPublishTest test` — 6/6 green.
- `./mvnw -Dit.test=DiscordPostServiceMatchPreviewIT,DiscordPostServiceMatchPreviewAutoEditIT verify` — 12/12 green.
- `./mvnw -Dit.test=MatchControllerPostMatchPreviewIT verify` — 7/7 green.
- `./mvnw -Dit.test=MatchDetailPreviewButtonE2ETest verify -Pe2e` — 5/5 green.
- `./mvnw clean verify -Pe2e` — BUILD SUCCESS, JaCoCo line coverage 89.03% (above 82% threshold), SpotBugs `BugInstance size: 0`, AdminWorkflowE2ETest 16/16 green.

## Pending UAT (Operator)

Per CONTEXT D-97-10, UAT-07 Steps 1-3 remain operator-driven:
1. **Live post smoke** — operator on `/admin/matches/{id}` with full data clicks Post Match Preview; verifies the resulting Discord message matches the 2026-05-23 reference screenshot (Match Day 4 DTR vs. TNR B layout).
2. **Auto-edit on streamLink change** — operator edits Match streamLink (e.g. from `null` to a Twitch URL), saves; verifies Discord client shows `(edited)` within ~5s and the Stream line now reflects the new URL.
3. **Auto-edit on teaser change** — operator edits Match teaser text, saves; verifies the Discord message body updates with `(edited)` marker.

Operator can run `/gsd-auto-uat 97-01` for the visual smoke per CLAUDE.md "gsd-auto-uat for UI-Heavy Verification". The 3 live-Discord steps require manual operator action (real webhook + real Discord client).

## Notes for Subsequent Plans

- **Plan 97-02 (POST-07)** can reuse the `populateMatchdayDiscordModel` helper pattern (currently named `discordAnnouncementsConfigured` + per-post-type model attrs in MatchController). Plan 97-02 adds `matchdayDiscordActive` boolean predicate; the per-post-type model attrs follow the same shape.
- **Plan 97-03 (POST-08)** can reuse the `MatchPreviewPreFlightResult` shape for a future `StandingsPreFlightResult` (boolean canPost, @Nullable String disabledReason) — same template branching pattern.
- **DiscordPostService.resolveAnnouncementChannelId** is the only public webhook→channelId helper today. Plan 97-03 may want a sibling `resolveStandingsForumChannelId(webhookUrl)` or generalize to a single `resolveChannelId(webhookUrl)` — current shape is fine.
