---
gsd_state_version: 1.0
milestone: v1.13
milestone_name: Discord Integration & Carry-Forwards
status: executing
stopped_at: Phase 101 context gathered
last_updated: "2026-05-26T12:44:38.086Z"
last_activity: 2026-05-26 -- Phase 101 execution started
progress:
  total_phases: 10
  completed_phases: 9
  total_plans: 41
  completed_plans: 36
  percent: 88
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-05-20)

**Core value:** Architectural Consistency: All controllers delegate to services, exception handling is centralized, and the production environment is secured.

**Current focus:** Phase 101 — backup-restore-covers-discord-schema-v8-v15

## Current Position

Phase: 101 (backup-restore-covers-discord-schema-v8-v15) — EXECUTING
Plan: 1 of 6
Verification: passed (14/14 decisions, 6/6 UAT, 7/7 dimensions)
Status: Executing Phase 101
Last activity: 2026-05-26 -- Phase 101 execution started

## Completed Milestones

- v1.0 Technical Debt Cleanup (5 phases, 12 plans) — shipped 2026-04-04
- v1.1 Codebase Concerns Cleanup (10 phases, 20 plans) — shipped 2026-04-07
- v1.2 Driver Merge (4 phases, 5 plans) — shipped 2026-04-07
- v1.3 English Test Data (8 phases) — shipped 2026-04-10
- v1.5 Code Review Fixes (9 phases, 14 plans) — shipped 2026-04-15
- v1.6 Static Site Quality (17 phases, 56 requirements) — shipped 2026-04-18
- v1.8 Bulk Driver Import from Google Sheets (2 phases, 4 plans, +52 tests) — shipped 2026-04-25
- v1.9 Season Phases & Groups (15 phases, ~70 plans, 38/38 requirements, +88.4k LOC) — shipped 2026-05-09
- v1.10 Spring Boot 4.0.6 Upgrade & Data Export/Import (9 phases, 50 plans, 39/39 requirements, +77.4k LOC, 87.80% JaCoCo) — shipped 2026-05-16
- v1.11 Tooling Infrastructure & Tech-Debt Sweep (8 phases 80-87, 46 plans, 46/46 requirements, JaCoCo 88.88%, 1675 tests, CI E2E median 23:00) — shipped 2026-05-18
- v1.12 Driver-Import Gap-Closure & Test Performance Round 2 (4 phases 88-91, 15 plans, 15/15 requirements substantively satisfied, JaCoCo 88.44%, 1696 tests, CI E2E median **17:39** Δ−23.3 %, Nyquist 4/0/0 compliant) — shipped 2026-05-20

## Active Milestone

**v1.13 Discord Integration & Carry-Forwards** — Phases 92-98 (in flight).

- Branch: `gsd/v1.13-discord-integration` (off `origin/master`)
- Design spec: `docs/superpowers/specs/2026-05-20-discord-integration-design.md` (18 decisions)
- Detailed roadmap: `.planning/milestones/v1.13-ROADMAP.md`
- Coverage: 25/25 REQ-IDs mapped to 7 phases (5 carry-forward + 20 Discord)
- Estimated duration: 15-20 working days
- New Flyway migrations: V8 (`discord_global_config`), V9 (`teams.discord_role_id`), V10 (`matches.discord_*`), V11 (`discord_post`), V12 (`seasons.discord_*_thread_id`)
- Zero new production dependencies (Spring `RestClient` is Spring 6.1+ core)

### Phase Order (Sequenced)

| Phase | Name | REQ-IDs | Depends on |
| ----- | ---- | ------- | ---------- |
| 92 | Carry-Forwards & Cleanup | UX-01, COV-01, CLEAN-01, DOCS-01, BOOK-01 | — |
| 93 | Discord Foundation | INFRA-01, INFRA-02, INFRA-03 | 92 |
| 94 | Team Roles + Match Channel Lifecycle | CHAN-01, CHAN-02, CHAN-03 | 93 |
| 95 | Match Channel Posts | POST-01..05 | 94 |
| 96 | Provisional Graphic + Forum Threads | GRAFX-01, FORUM-01, FORUM-02 | 95 |
| 97 | Matchday-Level Posts | POST-06, POST-07, POST-08 | 96 |
| 98 | Polish + E2E + Docs + Close | E2E-01, DOCS-02, DOCS-03 | 97 |

## Deferred Items

Items carried forward into v1.13 (from v1.12 audit + post-merge follow-ups) — all absorbed into Phase 92:

| Category | Item | Resolution Plan |
| -------- | ---- | --------------- |
| tech_debt | UX-01 scope-gap — `CsvImportController` (race-results sheet-import) not migrated to typed-catch + `errorCategory` flash + badge UX; T-91-02-IL info-leak (`e.getMessage()` echo) re-introduced for this 3rd consumer of typed `GoogleSheetsService` | **Phase 92** — apply typed-catch + `errorCategory` flash + badge UX to `CsvImportController` for parity (REQ UX-01) |
| tech_debt | JaCoCo Δ−0.44 pp (88.44 % vs 88.88 %, above 82 % gate); root cause javac-mandated defensive `catch (GoogleApiException)` blocks (Java 25 lacks sealed-exhaustiveness on catch) + uncovered service-layer IOException paths | **Phase 92** — add `RaceControllerCalendarTest` + IT coverage for Google service error paths (REQ COV-01) |
| tech_debt | CLEAN-02 grep-predicate drift — Phase 89 PERF-01 introduced AssertJ `Assumptions.assumeThat` in `BackupStagingDirPerForkIT.java:12,37`; different package + intent than the JUnit `Assumptions.assumeFalse` that CLEAN-02 originally targeted, but grep can't distinguish | **Phase 92** — tighten predicate to `org\.junit\.jupiter\.api\.Assumptions` (REQ CLEAN-01) |
| docs_debt | Optional audit-trail retrofill — Phases 89/90/91 close on VALIDATION.md + per-plan SUMMARY.md without phase-level VERIFICATION.md (v1.11 had VERIFICATION.md per phase, some retroactively via commit `2e84fd57`) | **Phase 92** — optional retroactive `89-VERIFICATION.md` / `90-VERIFICATION.md` / `91-VERIFICATION.md` (REQ DOCS-01) |
| bookkeeping_debt | REQUIREMENTS.md checkbox + traceability lag — 7 of 15 v1.12 REQ-IDs require flip from `Pending`/`[ ]` to `Resolved`/`[x]` (PERF-01..06 + UX-01); Plan 91-03 deliberately deferred per stale-state avoidance pattern | **Phase 92** — flip 7 stale checkboxes + 4 stale `Pending` traceability rows in `milestones/v1.12-REQUIREMENTS.md` (REQ BOOK-01) |
| uat | UAT-02 legacy season visual smoke (real pre-V4 production data) | post-deploy operator action (procedure docs/uat/UAT-02-legacy-season-smoke.md) |
| uat | QUAL-02 local-profile MariaDB manual smoke (DevDataSeeder widening) | post-deploy operator action |
| uat | UX-01 visual UAT — 4 error-category badges × Desktop + Mobile (8 Playwright screenshots) | post-deploy operator action (procedure 91-02-SUMMARY.md § Manual UAT) |
| uat | UAT-03 Live-Discord UAT — `Test Connection` button drives a real Discord `GET /users/@me` against a live `DISCORD_BOT_TOKEN` + test guild; WireMock ITs cover the happy + 4-permit exception paths but cannot prove the actual Discord-API contract | ✅ Resolved 2026-05-21 — all 4 buttons pass on dev profile against the operator's live test bot (procedure `docs/operations/discord-integration.md` § 4); see § Pending UATs UAT-03 for full result detail |
| ui_debt | Discord-Config page mobile-viewport overflow — at 375 px the form inputs + the 4-button bar in "Connection & cache tests" extend beyond the visible area; horizontal scroll required to reach `Refresh Server Roles` / `Refresh Emoji Cache`. Surfaced during UAT-03 mobile sweep 2026-05-21. Functionality is intact (the underlying typed-catch + flash wiring works on mobile); only the responsive-layout polish is missing. Plan 94-01 shipped `.discord-actions` flex-wrap cluster which closes the BUTTON-BAR overflow, but the `.card` container itself still overflows horizontally at 375 px on `/admin/discord-config` AND on `/admin/teams/{id}/edit` (confirmed in `.screenshots/94-01/discord-config-mobile.png` + `team-form-mobile-cold.png` 2026-05-21). | **Within v1.13 — must NOT defer to v1.14.** Target Phase 98 (Polish + E2E + Docs + Close) — single CSS sweep on the shared `.card` / `.form-group` containers benefits all Discord-touching pages (94 + 95 + 96 + 97). Confirmed user requirement 2026-05-21 wave-pause: "muss noch innerhalb des Meilensteins" |
| ui_debt | Schedule-Embed layout asymmetry — POST-04 SCHEDULE Discord embed renders 4 fields (Date / Lobby Host / Race Director / Streamer) with `inline: true`, so Discord packs 3 fields into row 1 and the 4th into row 2. Date is the widest value (`Tuesday, 26 May 2026 at 19:00 (in a day)`) — its width plus the short Lobby Host + Race Director values causes a visually "unrund" / asymmetric row 1. Surfaced during UAT-08 Stage 5 verification 2026-05-25 (operator preference: "pro Eintrag eine eigene Zeile"). Initial-post + auto-edit PATCH both functional — only the field-layout is the polish target. | ✅ **Resolved 2026-05-25** by Plan 98-04 — `DiscordPostService.buildSchedulePayload(...)` flipped all 4 fields to `inline: false` (Option A); `DiscordPostServiceScheduleIT` extended with `equalTo("false")` invariant-guard on all 4 fields. **Live-verified UAT-08 Stage 5c (added in-place 2026-05-25 via Plan 98-07)** — operator confirmed one-per-row embed layout on the live re-post; full `./mvnw clean verify -Pe2e` bundled with Plan 98-07. See `.planning/phases/98-polish-e2e-docs-close/98-04-SUMMARY.md` + `98-07-PLAN.md`. |
| tech_debt | Existing match-channels created under the Phase-94 scheme (md{N}-{home}-vs-{away}) retain their old names. Phase 100 D-08 verdict 2026-05-26: leave-as-is — no PATCH-rename action, no diagnostic UI list, no lazy auto-rename. New channels created post-Phase-100 use the new md{N}-{phase}-[{group}-]{home}-vs-{away} scheme uniformly. | Potential v1.14+ phase — admin bulk-rename action on /admin/discord-config that iterates matches WHERE discord_channel_id IS NOT NULL and PATCHes each via Discord API. See `.planning/phases/100-match-day-channel-naming-scheme-phase-prefix-rs-po-pm-option/100-CONTEXT.md` § Deferred Ideas → "Bulk-rename action for old-scheme channels". |
| tech_debt | Two-scheme coexistence in the matches table is acceptable post-Phase-100. Discord is the source of truth for live channel names; matches.discord_channel_id stores only the channel ID, not the name. Existing v1.13 channels are predominantly UAT artifacts, not production league channels. — D-09 acknowledgement 2026-05-26. | Linked to D-08 row above. Diagnostic read-only "channels with old naming scheme" panel on /admin/discord-config is the optional v1.14 entry point. See 100-CONTEXT.md § Deferred Ideas → "Diagnostic list of channels with old naming scheme". |

Post-merge self-resolving items (not tracked further):

| Category | Item | Status |
| -------- | ---- | ------ |
| post_merge | v1.13 release workflow tagging (produces `v1.13.0` annotated tag + GitHub Release + `ghcr.io/jegr78/ctc-manager:1.13.0` + `:latest`) | will resolve on squash-merge with subject `feat(v1.13): discord integration & carry-forwards` (per `docs/operations/release-runbook.md § 6 — Squash-merge subject discipline`) |

## Pending UATs

### UAT-02: Legacy Season Visual Smoke (carry-forward from v1.11 QUAL-05)

- **Procedure:** docs/uat/UAT-02-legacy-season-smoke.md
- **Status:** Executing Phase 101
- **Result:** _(operator fills after execution)_
- **Date:** _(operator fills)_
- **Screenshots:** _(operator links)_

### QUAL-02: Local-Profile MariaDB Smoke (carry-forward from v1.11 QUAL-02)

- **Procedure:** start app with `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` and verify `DevDataSeeder` widened to `@Profile({"dev","local"})` seeds correctly against real MariaDB
- **Status:** post-deploy operator action
- **Result:** _(operator fills after execution)_

### UX-01: Driver-Import Error-Category Badges (carry-forward from v1.12)

- **Procedure:** Trigger each of the 4 typed `GoogleApiException` permits (transient/auth/not-found/permission) in `/admin/driver-import` and capture Desktop + Mobile screenshots per `feedback_playwright_cli`
- **Status:** post-deploy operator action
- **Result:** _(operator fills)_

### UAT-03: Live-Discord Smoke (Phase 93 INFRA-03)

- **Procedure:** `docs/operations/discord-integration.md` § 4 (UAT-03 — Live-Discord Smoke). Covers bot registration in the Developer Portal, OAuth2 invite with the recommended permission set, env-var wiring, Developer Mode toggle, guild-ID copy, and the 4-button click sequence on `/admin/discord-config`.
- **Status:** ✅ PASSED 2026-05-21 — driven via `playwright-cli` against a live test bot on profile `dev`; all 4 admin buttons returned the expected success-badge and the announcement-webhook test message was confirmed delivered to the configured Discord channel by the operator out-of-band.
- **Result:**
  - **Test Connection** → green badge `Connected as CTC Manager App` (real `GET /users/@me` succeeded).
  - **Refresh Server Roles** → green badge `Server roles refreshed (2 entries).` (matches the test-guild's `@everyone` + bot role).
  - **Refresh Emoji Cache** → green badge `Emoji cache refreshed (1 entries).` (matches the `CTC` custom emoji configured for VS).
  - **Test Announcement Webhook** → green badge `Webhook test message delivered.` + operator confirmed message visible in the configured Discord channel.
  - Mobile (375×667) render: page loads, flash-badge wiring works end-to-end (Test Connection success path confirmed on mobile). **Follow-up UI issue identified** — form inputs + the 4-button bar overflow the 375 px viewport (horizontal scroll required to reach `Refresh Server Roles` / `Refresh Emoji Cache`). Tracked under Deferred Items as a Phase 94+ UX polish.
- **Date:** 2026-05-21
- **Screenshots:** `.screenshots/uat-03/` (gitignored locally — 8 PNGs: Desktop initial + 4 button-success states, Mobile initial + scrolled + Test-Connection success).

### UAT-04: Live-Discord Channel Lifecycle Smoke (Phase 94 CHAN-01/02/03/04)

- **Bot permissions** — see [docs/operations/discord-integration.md § Minimum Bot Permissions](../docs/operations/discord-integration.md#minimum-bot-permissions) for the minimum-permission setup (no Administrator needed).
- **Pre-UAT-04 cleanup** — before retrying UAT-04, the operator deletes the orphan test channel `1507281506408595456` (created during the 2026-05-22 UAT-04 attempt before Plan 94-04 shipped). The orphan exists because the original audit-fail cleanup path itself failed with AUTH (the bot couldn't DELETE its own un-overridden channel under the legacy 3-overwrite model).
- **Procedure** (inline until Phase 98 DOCS-02 fills `docs/operations/discord-integration.md`):
  1. `/admin/discord-config` → click **Refresh Server Roles** → expect green badge with role count matching the test guild's `@everyone` + bot + per-team roles. Side-effect: bot-identity cache refreshed (used by Plan 94-04 4th-overwrite logic).
  2. `/admin/discord-config` → enter the test guild's "Current Match Category" snowflake → **Save** → expect green badge `Configuration saved.`.
  3. `/admin/teams/{id}/edit` for two test teams → select Discord roles from the dropdown → **Save** → confirm the role IDs persist (visible after page reload).
  4. `/admin/matches/{id}` for a match between the two test teams → click **Create Discord Channel** → expect green badge `Discord channel created.` AND the channel-id badge replaces the button. Operator verifies in the Discord client → channel-properties → Permissions tab: the channel has **4 permission-overwrites** — `@everyone DENY VIEW`, 2 team-role ALLOWs, and 1 bot-member ALLOW (bot's own user-ID with `VIEW_CHANNEL + MANAGE_CHANNELS + MANAGE_WEBHOOKS + SEND_MESSAGES + EMBED_LINKS + ATTACH_FILES + READ_MESSAGE_HISTORY`), plus a `CTC Manager` webhook.
  5. **Audit-fail path** — manually add a 5th role (or member) to the just-created channel with View permission via Discord client (a 5th overwrite OR a missing bot member-overwrite both trip the audit). Click **Create Discord Channel** on a sibling match between two other teams → expect red `error-badge--auth` flash with hardcoded `AUDIT_FAIL_MESSAGE` AND the sibling channel does NOT appear (cleanup DELETE succeeded). If a manual cleanup follow-up is required (cleanup-fail), the composed message contains the channel-id to delete.
  6. **Move-to-Archive happy path** — on the original match-detail page, click **Move to Archive** → modal opens with the year's archive categories listed and the highest-num-with-room pre-selected → **Confirm** → expect green badge `Channel moved to archive.` AND the channel relocates under the chosen archive category in Discord.
  7. **Move-to-Archive category-full** — fill an archive category to 50 channels manually (or pick a category close to full). Attempt **Move to Archive** with a near-full target → expect red `error-badge--category-full` flash with `CATEGORY_FULL_MESSAGE` AND the channel stays in its current category (no PATCH committed Discord-side).
- **Status:** pending operator action — **required before Phase 95 plans 95-02/03/04 start** (per CONTEXT D-12).
- **Result:** _(operator fills after execution)_
- **Date:** _(operator fills)_
- **Screenshots:** _(operator links)_

### UAT-05: Live-Discord Post Lifecycle Smoke (Phase 95 POST-01..05)

- **Pre-UAT-05** — UAT-04 (Phase 94 channel lifecycle) must have succeeded; the operator has at least one live test match with a Discord channel + webhook configured.
- **Procedure** (11 steps per CONTEXT D-95-10, inline until Phase 98 DOCS-02 fills `docs/operations/discord-integration.md`):
  1. `/admin/matches/{id}` for the test match with a live Discord channel → confirm `.discord-actions--posts` panel is visible. Expect "Post Team Cards" button rendered (no prior TEAM_CARDS row).
  2. Trigger a `Create Discord Channel` flow on a fresh match: expect green `Discord channel created.` AND a multipart-POST with 2 PNG attachments lands in the new Discord channel within ~30s (auto-post hook from Plan 95-02). If team-card generation fails, expect yellow `Channel created. Team Cards post failed: {category}` flash but the channel + webhook remain persisted.
  3. Click `Re-Post Team Cards` → expect the existing Discord message is EDITED (no new message), `match-card-home.png` + `match-card-away.png` attachments re-rendered identically. Verify in `/admin/discord/posts` listing that the `attachments_replaced_at` advances.
  4. Click `Refresh Cards` → expect TeamCardService re-generates BOTH team cards (synchronous, may take 30-60s worst case per RESEARCH Landmine 8), then the same Discord message is PATCHed with the new PNGs.
  5. Configure RaceSettings for all races in the match (Settings dropdowns) → expect `Post Settings` button transitions from DISABLED-with-tooltip to enabled. Click → expect ONE multipart-POST with N PNG attachments (one per race, indexed `settings-race-1.png`, `settings-race-2.png`, …) lands in Discord.
  6. Configure RaceLineup for all races (Lineup form) → expect `Post Lineups` button transitions to enabled. Click → expect analog multipart-POST with N lineup PNGs.
  7. Configure Race.dateTime for first race + Lobby Host / Race Director / Streamer on Match-Detail → expect `Post Schedule` button transitions to enabled. Click → expect ONE JSON-POST (no attachments) with a Discord embed containing 4 fields (Date with `<t:UNIX:F> (<t:UNIX:R>)`, Lobby Host, Race Director, Streamer; nulls render as `_TBD_`).
  8. **Schedule auto-edit smoke** — back to `/admin/matches/{id}/edit`, change `Lobby Host` → Save → expect the SCHEDULE Discord embed is automatically PATCHed with the new value (no operator click needed). Plan 95-04 hook in `MatchService.updateDiscordFields`. **Verification:** check Discord client embed; "Lobby Host" field reflects the new value within ~5s of save.
  9. Submit at least one RaceResult per race → expect `Post Match Results` button transitions from DISABLED-with-tooltip to enabled. Click → expect ONE multipart-POST with `match-results.png` attachment in Discord.
  10. **Stale-detection smoke** — edit ONE RaceResult (e.g. change a position) → re-visit Match-Detail → expect the Match Results button label flips from `Re-Post Match Results` to `Update Match Results` (yellow stale signal). Click → expect the Discord message is PATCHed and the label flips back to `Re-Post Match Results`.
  11. **Final verification on `/admin/discord/posts` listing** — filter by the test match's UUID → expect 5 DiscordPost rows (TEAM_CARDS / SETTINGS / LINEUPS / SCHEDULE / MATCH_RESULTS), each with non-null `attachments_replaced_at` for the 4 multipart types, and the SCHEDULE row's `updated_at` advanced AFTER the step-8 auto-edit.
- **Status:** PASS — executed inline on milestone branch BEFORE PR merge (deviation from D-95-10) because live-Discord drift surfaced 5 production regressions that had to land in the same PR.
- **Result:** PASS — all 11 steps green after Bugs A-E fixes (commits `64b5d06b` URL regex, `dd5b0ca2` AFTER_COMMIT listener pivot, `b0dd2962` `?wait=true`, `730e1151` Match Results bundle, `57e1130c` PATCH attachments descriptor, `2f5b5aad` Stream Link hyperlink, `aa25209d` orphan-remove flush). All 5 DiscordPost types created in `/admin/discord/posts` listing. Auto-post + auto-edit + stale-detection all verified live.
- **Date:** 2026-05-23
- **Screenshots:** captured in chat (channel-create, match-results bundle, schedule embed with stream-link, re-post team cards, stale-detection)

### UAT-06: Live Provisional Scores + Forum-Thread Lifecycle (Phase 96 GRAFX-01 + FORUM-01 + FORUM-02)

- **Pre-UAT-06** — UAT-05 (Phase 95 post lifecycle) must have succeeded; the operator has at least one live test match with a Discord channel + webhook configured AND a Discord forum-channel (race-results + standings) with at least one thread (ideally one pinned).
- **Procedure** (8 steps per CONTEXT D-96-10):
  1. `/admin/discord-config` → populate the 2 new Forum-Webhook URL fields (`Race-Results Forum Webhook URL` + `Standings Forum Webhook URL`) — webhook URLs are created in the Discord client under the forum-channel's Integrations tab → **Save** → expect green badge `Configuration saved.`.
  2. `/admin/seasons/{id}/edit` for the season under test → confirm a new `Discord Integration` card is visible with 2 thread-linker widgets (race-results + standings). Click `Link existing Thread...` → modal opens with the forum's threads listed (pinned first, then active by last-message desc, then archived). Pinned auto-pre-selected (D-96-FOR-2). Operator confirms the radio default OR overrides; click `Confirm` → expect green badge `Thread linked.` AND the card flips to the linked-thread state (badge with name+ID + Change Link + Unlink buttons).
  3. `/admin/matches/{id}` for a match with ≥1 race result → confirm the new `Post Provisional Scores` button is visible in the `.discord-actions--posts` cluster, BEFORE the Match-Results triplet. Click → expect a multipart-POST with N PNG attachments (one per race with results, filename `provisional-race-N.png`) lands in the match channel. Verify in the Discord client — the bot's provisional-scores message shows the per-driver scoring breakdown.
  4. Submit another race result → re-visit Match-Detail → expect button label flips to `Re-Post Provisional Scores`. Click → expect existing Discord message is PATCHed with the new N+1 attachments. Verify `attachments_replaced_at` advances on `/admin/discord/posts`.
  5. `/admin/races/{id}` for a race with results in the same season → confirm a new `Discord Actions` card is visible with `Post Race Result` button (D-96-FOR-3c). Click → expect a multipart-POST with `race-result-{matchdayLabel}-race-N.png` attachment lands in the linked forum-thread (not the match channel). Verify in the Discord client — the result lands in the race-results forum-thread.
  6. **Auto-unarchive smoke** — archive the linked forum thread manually in the Discord client (right-click → Archive Thread). Back to `/admin/races/{id}` → click `Re-Post Race Result` → expect the bot auto-unarchives the thread (D-96-FOR-4) THEN PATCHes the existing post. Verify in Discord — thread is active again AND the race-result message is updated; NO re-archive after.
  7. **3 distinct pre-flight tooltips** — pick 3 races violating each pre-flight gate (no results / no thread linked / no webhook configured) and verify the `Post Race Result` button renders as a disabled span with the corresponding tooltip text (`No race results yet` / `Link a race-results thread first` / `Configure race-results forum-webhook in Discord settings`).
  8. **Final verification on `/admin/discord/posts`** — filter by the test season's UUID → expect ≥1 PROVISIONAL_SCORES row (match channel) + ≥1 RACE_RESULTS row (forum thread, with thread_id captured in the URL). Both with non-null `attachments_replaced_at` after the re-post.
- **Status:** PASS 2026-05-23 — all 8 steps verified live (Steps 3-4 accepted at Plan 96-01 wave-pause; Steps 1, 2, 5, 5b, 7, 8 via playwright-cli + live Discord client; Step 6 auto-unarchive confirmed end-to-end via dev-server logs + operator visual).
- **Result:**
  - **Step 1** ✅ `/admin/discord-config` shows both new Forum-Webhook-URL fields populated by `DiscordDevSeeder` from `.env.dev` (`DISCORD_DEV_RACE_RESULTS_FORUM_WEBHOOK_URL` + `DISCORD_DEV_STANDINGS_FORUM_WEBHOOK_URL`).
  - **Step 2** ✅ `/admin/seasons/{id}/edit` Discord Integration card opens the Link-Thread modal, pinned thread (`Saison 4 - 2026`) auto-pre-selected, Confirm → `Thread linked.` success-flash, linked-state badge + Change Link + Unlink buttons render.
  - **Steps 3-4** ✅ Provisional Scores accepted at Plan 96-01 wave-pause review (2026-05-23): "Provisional Scores can be posted and the layout looks good — I like that."
  - **Step 5** ✅ `/admin/races/137b47e6-…` (ICL vs NFR) — `Post Race Result` button enabled when all 3 pre-flight predicates green. Click → `Race result posted to forum-thread.` success-flash. **Live Discord verification:** the `Scorecard ICL vs NFR · Group A — Matchday 3` PNG landed in the linked `Saison 4 - 2026` forum thread at 21:23 (CTC Results bot post).
  - **Step 5b** ✅ Re-Post → button label flips to `Re-Post Race Result` → Click → same success-flash. **Live Discord verification:** the existing post is PATCHed (the `(edited)` indicator appears below the message); no new message created.
  - **Step 6** ✅ operator closed the `Saison 4 - 2026` forum post manually in the Discord client (Discord's "Close Post" = `thread_metadata.archived=true`), then clicked `Re-Post Race Result` at 21:35. Dev-server logs prove the auto-unarchive sequence: `21:35:24 Unarchiving forum thread 1507059154626416690 before post` → `21:35:25 Edited RACE_RESULTS for ref RaceRef[id=137b47e6-…]`. Operator confirmed in Discord: thread is reopened (no "Reopen Post" button) and the Scorecard message carries a fresh `(edited)` marker; NO follow-up PATCH back to archived=true (D-96-FOR-4 honored).
  - **Step 7** ✅ all 3 distinct disabled-tooltip strings render via the same `Post Race Result` span: `No race results yet` (race in same season but `Results (0)`), `Link a race-results thread first` (race in different season without linked thread), `Configure race-results forum-webhook in Discord settings` (race in same season but global webhook URL cleared). Webhook URL restored to original `DiscordDevSeeder` value after the test.
  - **Step 8** ✅ `/admin/discord/posts` listing shows the new `RACE_RESULTS` row with channel-id `1507061819448098836` (webhook-id segment) and `attachments_replaced_at` advanced after the Re-Post (the 21:23 row vs the 21:23:50 re-post timestamp).
- **Date:** 2026-05-23
- **Screenshots:** `.screenshots/uat-06/` (8 PNGs gitignored locally) + live Discord screenshot shared inline confirming `Saison 4 - 2026` forum thread received the Scorecard PNG with `(edited)` marker after the Re-Post.

### UAT-07: Live Matchday-Level Posts Lifecycle (Phase 97 POST-06 + POST-07a + POST-07b + POST-08)

- **Pre-UAT-07** — UAT-06 (Phase 96 forum-thread lifecycle) must have succeeded; the operator has the same `Saison 4 - 2026` race-results forum-thread + `2026` standings forum-thread linked to a target season; bot has Manage-Webhooks on both forums and a `:CTC:` custom emoji uploaded.
- **Procedure** (9 steps per 97-VALIDATION.md Manual-Only):
  1. `/admin/seasons/{id}/edit` → Discord Integration card → POST-06 `Post Match Preview` button (from Match-Detail) → multipart-POST lands in announcement-webhook channel with Markdown body (H1/H2/H3/teaser/Date/Stream/Game On! emoji line) + Settings.png + Lineups.png attachments.
  2. Match-Edit → change `streamLink` → Save → `@TransactionalEventListener AFTER_COMMIT` auto-PATCHes the MATCH_PREVIEW post within ~5s (Discord `(edited)` indicator).
  3. Match-Edit → change `discordTeaser` → Save → same auto-PATCH path.
  4. Matchday-Detail → `Post Match Day Results` → POST-07a multipart-POST lands in race-results forum-thread.
  5. Same Matchday → re-click after RaceResult update → Re-Post Match Day Results PATCHes the existing message.
  6. Same Matchday → `Post Power Rankings` → POST-07b posts sequentially into the same race-results forum-thread; reflects current `SeasonTeam.rating` DESC order.
  7. Season-Edit → POST-08 `Post Standings` per-phase → multipart with N PNGs (1 for non-GROUPS / N for GROUPS) lands in standings forum-thread; per-phase identity preserved (V14 phase_id FK).
  8. Season-Edit → re-click `Post Standings` → PATCH path edits the existing message (`(edited)` marker, no new post).
  9. `/admin/discord/posts` → filter by season shows all 5+ new post-types (TEAM_CARDS + MATCH_PREVIEW + MATCHDAY_OVERVIEW + POWER_RANKINGS + STANDINGS) with `attachments_replaced_at` after re-posts.
- **Status:** PASS 2026-05-24 — all 9 steps verified live on operator's test guild via playwright-cli + live Discord client.
- **Result:**
  - **Steps 1-3** ✅ POST-06 (`#annoucements` channel, `Regular Season` H1 + `Matchday 2` H2 + `ADR vs. VRX A` H3 + teaser + `<t:N:F>` Date + Stream URL + Game On! line with `:CTC:` emoji + Settings.png + Lineups.png + Twitch/YouTube auto-embed). streamLink edit + discordTeaser edit both AUTO-PATCH within seconds; `(edited)` marker confirmed.
  - **Step 4** ✅ POST-07a Matchday-1-Results PNG in `Saison 4 - 2026` race-results thread (all 7 matches with 65:51 scores, team colors + logos).
  - **Step 5** ✅ POST-07a button-flip "Re-Post Match Day Results" after the post; PATCH path proven via the WireMock-IT regression-suite (no operator stale-data signal needed for UAT, button-flip alone confirms the existing-row lookup).
  - **Step 6** ✅ POST-07b Power Rankings 2026 PNG sequenziell hinter POST-07a im selben Thread (2-column layout 1-7 / 8-14, subtitle "Matchday 1").
  - **Step 7** ✅ POST-08 Standings PNG für `Regular Season 2026` (14 teams, REGULAR phase) im `2026` standings thread; dynamic 1920×1080 layout no-overflow.
  - **Step 8** ✅ POST-08 Re-Post → `(edited)` marker, single message preserved.
  - **Step 9** ✅ `/admin/discord/posts` listing zeigt nach Polish-Welle alle Posts mit friendly Season + Match labels (Typeahead-Filter).
- **Date:** 2026-05-24
- **Polish-Welle (7 in-milestone Fixes surfaced during UAT-07):**
  1. `093c29de` — Sub-Team Emoji-Resolution: `match.getHomeTeam().getParentOrSelf().getShortName()` für emoji lookup (Plan 97-01 polish, mit IT)
  2. `f3484acc` — `/admin/discord/posts` Season-Dropdown zeigt friendly Label (`2026 | #4 | Regular Season`) statt Lombok @ToString
  3. `b2581131` — Season-Phase-Detail Matchdays als `.chip-list a.chip` statt rohem `<ul><li>`
  4. `6c546b53` — `/admin/discord/posts` Match-Dropdown + Tabellenspalte mit friendly Match-Label (Map<UUID, String>)
  5. `e02b2d39` — Match-Dropdown optgroup grouping (superseded by typeahead)
  6. `24704d92` — `scripts/app.sh` profile-aware (`data/app-{profile}.pid` statt `target/app.pid`, `--all` flag, orphan-detection) + `LegacyMigratedSeasonE2ETest` Selector-Update auf chip-list
  7. `a59d1c99` — Match-Dropdown auf existing `.searchable-dropdown` typeahead (operator tippt `ADR` / `Matchday 3` zum Filtern)
- **Plan-End reverify** on `a59d1c99`: 1807 Tests grün (Surefire 1218 + Failsafe 589), JaCoCo 88.60 %, SpotBugs 0, ~9:35 min.

### UAT-08: Live Full-Matchday-Lifecycle (Phase 98 E2E-01 mirror — operator-driven)

- **Pre-UAT-08** — Phase 98 Plans 98-01 / 98-02 / 98-03 committed; bot has operating cache on the operator's test guild; at least 1 spare match unposted; race-results + standings forum threads linked on the test season.
- **Procedure** (9-stage walkthrough per `docs/operations/discord-integration.md` § 7):
  1. Create Discord Channel for the test match → expect green badge + Channel-ID + Webhook auto-created.
  2. Post Team Cards → multipart-POST with 2 PNGs lands in the new channel.
  3. Post Settings → multipart-POST with N PNGs.
  4. Post Lineups → multipart-POST with N PNGs.
  5. Post Schedule → JSON-POST with Discord embed (Date `<t:N:F>` + Lobby Host / RD / Streamer). Edit one field + re-save and confirm the embed auto-edits in place.
  6. Submit at least one race result → Post Provisional Scores → multipart-POST with N PNGs to the match channel.
  7. After `allMatchesFinal == true`, Post Match Results → multipart-POST with `match-results.png`; tweak a result + re-render and verify the button turns yellow ("Update Match Results").
  8. Open the Race-Result forum thread → confirm the race-result graphic landed (auto-unarchive if Discord archived it).
  9. Move-to-Archive via Modal → channel relocates under year-category.
- **Status:** ✅ **PASS** — Stages 1–9 (Match-Channel Lifecycle) + Stages 10–13 (Season/Matchday-Level Posts) all green on 2026-05-25 against live test guild `1507055541313208320` (bot app `1507053701427367966`).
- **Result:** ✅ PASS — all 13 effective post-types green, **+3 in-milestone-extended stages (5c + 14 + 15) added 2026-05-25 (Phase 98 re-open per CONTEXT Q-98-07).** Plus 4 bonus Re-Post-PATCH verifications (see § Bonus below). The originally enumerated "Stage 11 Matchday Schedule" misnomer has been retroactively closed: Plan 98-06 (POST-10) now introduces an actual `MATCHDAY_SCHEDULE` post type covered by Stage 15 below. Schedule-Embed-Layout polish item closed by Plan 98-04 (Deferred Items § ui_debt → Resolved); live verification in Stage 5c below.
- **Date:** 2026-05-25 (10:15 – 11:01 UTC)
- **Test setup:** Match `880eb32e` (ADR vs VRX A, Matchday 1 of season `cad632c4` "2026 | #4 | Regular Season"). Pre-staged: Schedule fields (Lobby Host `@uat-host`, RD `@uat-director`, Streamer `@uat-streamer`, Stream Link `https://twitch.tv/uat-test`, Teaser); Race-Settings (Track Suzuka Circuit, Car AMG — Mercedes-AMG GT3 '20, DateTime 2026-05-26T20:00, Settings imported from DevSeeder); Season-Pool seeded via `POST /admin/seasons/.../cars/add` + `.../tracks/add` (1 car + 1 track minimal). Forum threads linked via "Link existing Thread..." modal (Discord-API discovery auto-matched pinned threads: race-results `1507059154626416690`, standings `1507059367231356978`).
- **Stage results:**
  - **Stage 1** ✅ Channel `md2-adr-vs-vrx-a` (id `1508414873455820800`) created in category `1507055787225124924`; webhook auto-created; permission-overwrite audit passed (only ADR + VRX roles + bot user-overwrite + server owner; `@everyone` View Channel = red X — opposing teams blind, T-93-03 mitigation verified).
  - **Stage 2** ✅ Team Cards (2 PNGs ADR + VRX A) auto-posted by `@TransactionalEventListener AFTER_COMMIT` hook on channel create (Phase-97 fix); `Posted TEAM_CARDS messageId=1508414877310648460`.
  - **Stage 3** ✅ Settings PNG (Suzuka + AMG GT3 '20 + all race-settings) posted; `Posted SETTINGS messageId=1508417767294894152`. Button flipped to "Re-Post Settings".
  - **Stage 4** ✅ Lineups PNG (6 driver slots per team from DevSeeder) posted; `Posted LINEUPS messageId=1508418160116764765`.
  - **Stage 5** ✅ Schedule JSON-embed posted (`Posted SCHEDULE messageId=1508418568407224370`); Match-Edit Race Director `@uat-director` → `@uat-director-v2` + Save triggered `Edited SCHEDULE messageId=1508418568407224370` — **same `messageId`** confirms PATCH not POST. Discord shows `(edited)` marker; Date renders as `<t:N:F>` ("Tuesday, 26 May 2026 at 19:00 (in a day)" — Discord auto-formats per-viewer-timezone from London-stored value).
  - **Stage 6** ✅ Provisional Scores PNG posted; `Posted PROVISIONAL_SCORES messageId=1508420351611375821`. **Bonus:** Re-Post Provisional Scores clicked → `Edited PROVISIONAL_SCORES messageId=1508420351611375821` (same id) — confirms **multipart-attachment PATCH** works (`postOrEdit` pattern complete for both JSON embeds AND multipart files). Discord `(edited)` marker on image-message.
  - **Stage 7** ✅ Initial: `Posted MATCH_RESULTS messageId=1508421649714774110` (match-results.png with 2 cards: aggregate 65:51 + per-driver scorecard). Tweak (POS 1↔2 swap on ADR_Driver01 ↔ VRX_Driver01) → button flipped to **"Update Match Results"** (lighter background = stale indicator). Revert (POS swapped back) → button stayed "Update Match Results" — confirms **stale-marker is `lastModifiedAt`-based**, not content-hash (deterministic + cheap). Update-click triggered `Edited MATCH_RESULTS messageId=1508421649714774110` (same id), button reverted to grey "Re-Post Match Results".
  - **Stage 8** ✅ Race-Result Forum-Post posted to thread `Saison 4 - 2026` (id `1507059154626416690`) via separate webhook identity "CTC Results APP" (vs match-channel webhook "CTC Manager APP" — confirms per-use-case webhook identity per Phase 96 FORUM-02). `Posted RACE_RESULTS messageId=1508424185222004828`. Auto-unarchive not triggered (thread was not archived); code path is unit-test-covered via `DiscordFullMatchdayLifecycleE2ETest.step7` with `stubFetchChannelNotArchived` stub.
  - **Stage 9** ✅ Move-to-Archive Modal showed single year-filtered option "Match Days Archive 2026 — 0/50" (pre-checked); Confirm relocated channel `1508414873455820800` to year-archive category; UI shows new pill **"Archived 2026-05-25 11:01"** + all post-buttons grey-disabled. Discord-side: channel moved from "Match Days" → "Match Days Archive 2026" with all 6+ messages intact (PATCH on `parent_id`, no recreate).
- **Bonus verifications beyond spec:**
  - **Re-Post-PATCH for all stateful post types** — SCHEDULE (JSON-embed, Stage 5b), PROVISIONAL_SCORES (multipart, Stage 6b), MATCH_RESULTS (multipart, Stage 7d), and MATCH_PREVIEW (multipart on announcement-webhook, Stage 10 follow-up) all confirmed to PATCH the existing message rather than POSTing a new one. `postOrEdit` pattern (Phase 95 POST-01) is end-to-end verified across both content types (JSON embeds + multipart attachments) AND across all three webhook surfaces (match-channel webhook, announcement webhook, forum webhook).
  - **Stale-marker semantics** — Match Results button stale-flip is `lastModifiedAt`-based on the underlying RaceResult, not content-hash. Operator-friendly: any Save on results triggers the visual flag, even if the final aggregate is unchanged.
  - **Per-use-case webhook identities** — 4 distinct Discord webhook identities verified end-to-end: "CTC Manager APP" (match-channel webhook, Stages 1–9), "CTC Results APP" (race-results forum webhook, Stages 8/11/12), "CTC Announcements APP" (announcement webhook, Stage 10), "CTC Standings APP" (standings forum webhook, Stage 13). Phase 96/97 D-Decision intent (clear operator-UX in Discord sidebar) holds in production.
- **In-milestone polish surfaced (tracked):** Schedule-Embed Layout asymmetry — see Deferred Items § ui_debt. Resolution: Plan 98-04 (Option A: all 4 fields `inline: false` per operator preference 2026-05-25).
- **Screenshots:** `.screenshots/uat-08/00-test-connection.png` … `13-stage13-standings.png` (14 screenshots, gitignored locally).
- **Live Discord artifacts:** channel `md2-adr-vs-vrx-a` in `Match Days Archive 2026` category; thread `Saison 4 - 2026` in race-results forum (Stages 8 + 11 + 12); thread `2026` in standings forum (Stage 13); `announcements` channel (Stage 10).
- **Operator follow-up (out of UAT-08 scope, tracked separately):** Upload server-emojis `:ADR:` + `:VRX:` (and other team tricodes) to the Discord guild so the Match-Preview emoji-rendering line (`Game On! :ADR: :CTC: :VRX:`) renders the team emojis alongside the already-working `:CTC:` VS-emoji. The app resolves emojis by name via `DiscordEmojiCache`; nothing app-side to change.
- **Stage 10 — Match Preview** ✅ (POST-04 base + POST-06 enrichment): Match-Detail → "Post Match Preview" against archived match `880eb32e`. `Posted MATCH_PREVIEW messageId=1508428231143985164` → Discord `announcements` channel via Announcement Webhook. Operator noted `:CTC:` custom emoji rendered as raw `:CTC:` text → clicked Refresh Emoji Cache (`DiscordEmojiCache refreshed with 1 entries`) → Re-Post Match Preview → `Edited MATCH_PREVIEW messageId=1508428231143985164` (same id = PATCH on announcement-webhook embed verified) → `:CTC:` now renders as CTC-Logo custom emoji. `:ADR:` / `:VRX:` remain as raw text → operator-managed (team emojis not yet uploaded to the Discord guild; out of scope for v1.13 app). Separate webhook identity: "CTC Announcements APP".
- **Stage 11 — Match Day Results** ✅ (POST-07a): Matchday 1 detail → "Post Match Day Results". `Posted MATCHDAY_OVERVIEW for ref MatchdayRef[id=52fc567b…] messageId=1508430499960324099` → Race-Results Forum Thread `Saison 4 - 2026` via Race-Results Forum Webhook. PNG shows all 7 matches of Matchday 1 with team-logos + records + scores. Webhook identity: "CTC Results APP" (same as Stage 8 Race-Result).
- **Stage 12 — Power Rankings** ✅ (POST-07b): Matchday 1 detail → "Post Power Rankings" (sibling button to Stage 11 in same Discord Actions card). `Posted POWER_RANKINGS for ref MatchdayRef[id=52fc567b…] messageId=1508430968870928517` → SAME Race-Results Forum Thread. Separate `POWER_RANKINGS` discord_post row (not `MATCHDAY_OVERVIEW`) → independent Re-Post lifecycle per type. PNG shows 14 teams sorted DESC by `SeasonTeam.rating`: ADR #1, SGM Blue #2, …, TBR Green #14. Subtitle "Matchday 1".
- **Stage 13 — Standings** ✅ (POST-08): Season-Edit (#discordIntegration card) → "Post Standings" (phase-selector auto-hidden because the season has only 1 phase REGULAR). `StandingsService: Calculated standings for phase 2fa115a7… group null: 14 teams` → `Posted STANDINGS for ref SeasonRef[seasonId=cad632c4…, phaseId=2fa115a7…] messageId=1508431470807486496` → Standings Forum Thread `2026` via Standings Forum Webhook. PNG with 14-team table (#, TEAM-with-logo, W, D, L, PTS) for REGULAR-non-GROUPS phase (1 PNG layout per spec). Composite identity-key (seasonId, phaseId) via V14 FK migration. Webhook identity: "CTC Standings APP" (4th separate identity — verifies per-use-case webhook-identity decision per Phase 96/97).
- **Originally listed "Stage 11 Matchday Schedule" — does not exist in v1.13:** Initial Bestandsaufnahme assumed POST-06 was a separate "Matchday Schedule" post on the matchday-detail page. `REQUIREMENTS.md` and `97-UI-SPEC.md` clarified that POST-06 is the per-match "Post Match Preview" button on Match-Detail (an enrichment of POST-04 with additional disabled-state validations). No separate matchday-schedule announcement exists; Stage 10 (Match Preview) covers POST-06. The `MATCHDAY_PAIRINGS` enum value was retained but explicitly out-of-scope (deferred to v1.14 per CONTEXT D-97-PREV-2). **Update 2026-05-25 (Phase 98 re-open per CONTEXT Q-98-07):** the misnomer has been retroactively closed — Plan 98-05 introduces POST-09 (`MATCHDAY_PAIRINGS` enum now wired to a working button) and Plan 98-06 introduces POST-10 (new `MATCHDAY_SCHEDULE` enum value with pure-multipart PNG). Effective UAT-08 stage count is now **16** (13 original + Stage 5c Schedule-Embed-Layout-Polish + Stage 14 Matchday-Pairings + Stage 15 Matchday-Schedule).
- **Stage 5c — Schedule-Embed Layout Polish (Plan 98-04 closeout):** ✅ Match `880eb32e` `/admin/matches/{id}/edit` → field edit + Save triggered automatic `Edited SCHEDULE messageId=1508418568407224370` (same `messageId` from Stage 5 — PATCH path verified post-`inline: false` flip). Discord embed shows all 4 fields (`Date`, `Lobby Host`, `Race Director`, `Streamer`) **one-per-row** rather than the prior 3-on-row-1 + 1-on-row-2 asymmetry. Operator visual approval 2026-05-25. Closes Deferred Items § ui_debt (Schedule-Embed asymmetry).
- **Stage 14 — Matchday Pairings (POST-09, Plan 98-05):** ✅ Matchday `cb5e3e10-f288-49d8-ac7e-b46b63938540` → **Edit Pairings** form filled with `pickDeadline=2026-05-30T19:00` + `scheduledWeekend="30 May - 1 June"` → Save → **Post Matchday Pairings** → `Posted MATCHDAY_PAIRINGS messageId=1508521181710385192` (19:24:36 UTC) → Discord announcement-channel via Announcement Webhook. Hybrid Markdown body rendered from the operator-editable template (`{{ctcEmoji}}` → `<:CTC:id>` long-form via `DiscordEmojiCache.emojiFor("CTC")` — first iteration showed raw `:CTC:` text, operator screenshot 2026-05-25 confirmed the bug, fix landed via emoji-cache resolution + auto-refresh on `DiscordDevSeeder`) + Pairings PNG attachment. Re-Edit weekend → `"29-31 May (EDITED)"` → Save → button flipped to **Update Matchday Pairings** (stale-marker on `matchday.updatedAt > post.updatedAt`) → click → `Edited MATCHDAY_PAIRINGS messageId=1508521181710385192` (19:25:11 UTC, **same `messageId`** = PATCH path verified). Discord shows `(edited)` marker on the post.
- **Stage 15 — Matchday Schedule (POST-10, Plan 98-06):** ✅ Same matchday → **Post Matchday Schedule** → `Posted MATCHDAY_SCHEDULE messageId=1508519650579972120` → Discord announcement-channel via Announcement Webhook. **Pure-multipart PNG** verified (operator confirmed no Markdown body, no embed structure — only the schedule graphic). Re-Edit race `dateTime` → Save → button flipped to **Update Matchday Schedule** (stale-marker on MAX(`match.updatedAt`, `race.updatedAt`) > `post.updatedAt`) → click → `Edited MATCHDAY_SCHEDULE messageId=1508519650579972120` (same `messageId`, **3× PATCH** verified across iterative operator edits = post-PATCH stability proven). Operator visual approval 2026-05-25.

## Accumulated Context

### Decisions

All decisions logged in PROJECT.md "Key Decisions" table and per-milestone in `milestones/v*.x-ROADMAP.md`. v1.13 decisions will be captured per phase during `/gsd-discuss-phase`.

Roadmap-level decisions for v1.13 (captured during ROADMAP.md drafting 2026-05-20):

- **Phase 92 sequenced first (D-Phase-92-First)** — JaCoCo recovery + CLEAN-01 grep-predicate fix creates a clean baseline before Flyway V8-V12 migrations land. Running coverage measurements against a broken baseline would mask Discord-phase regressions. UX-01 + COV-01 + CLEAN-01 + DOCS-01 + BOOK-01 all bundled into a single sequencing-first phase to clear the v1.12 audit slate before any new business logic.
- **Phase 93 hard-precedes Phase 94 (D-Foundation-Before-Lifecycle)** — `DiscordRestClient` + `DiscordWebhookClient` + sealed `DiscordApiException` hierarchy + `DiscordEmojiCache` must exist before channel-creation buttons can be wired. CHAN-02's "Create Discord Channel" button requires `DiscordRestClient.createChannel` + `createWebhook` and a working CSRF + DTO mass-assignment surface from INFRA-02.
- **Phase 95 hard-precedes Phase 96 (D-Tracking-Before-Forum)** — `DiscordPost` tracking entity from POST-01 (Flyway V11) is structurally reused by FORUM-02 (race-result forum-thread posts). Same `DiscordPostService.postOrEdit` pattern + same `discord_post` row tracking; FORUM-02 only adds the `?thread_id=` query param + auto-unarchive logic.
- **Phase 96 hard-precedes Phase 97 (D-Threads-Before-MatchdayPosts)** — Forum-threads must be linkable on `seasons.discord_*_thread_id` before POST-07 (Matchday Overview + Power Rankings → race-results thread) and POST-08 (Standings → standings thread) can target them.
- **Phase 98 sequenced last (D-E2E-After-All-Buttons)** — E2E suite exercises the full create-channel → post-all-stages → archive lifecycle covering Phases 94-97; cannot run before all 11 post types from POST-01..08 have buttons.
- **Zero new production dependencies (D-No-New-Deps)** — Spring `RestClient` is Spring 6.1+ core, multipart via `MultipartBodyBuilder`, WireMock already in test scope. Avoids JDA / Discord4J transitive-dependency footprint + license review.
- **Outbound-only architecture (D-Outbound-Only)** — No inbound slash commands or reaction reads. Local app, no always-online endpoint feasible. Inbound is out-of-scope per design spec § 2.2 and tracked as DISC-FUTURE-01.
- **Button-triggered, no auto-post (D-Operator-Control)** — All Discord posting is operator-button-triggered. No DB-event auto-trigger pipeline (DISC-FUTURE-02). Preserves full operator control over what lands in Discord.

### Phase Numbering

Last phase shipped: **91** (v1.12 closer). v1.13 spans phases **92-98** (integer phases, no insertions). Per design spec § 5: 7 phases, ~23 plans estimated.

### Roadmap Evolution

- 2026-05-20: v1.12 milestone closed via `/gsd-complete-milestone v1.12`; PR #129 awaits squash-merge.
- 2026-05-20: v1.13 milestone started. Branch `gsd/v1.13-discord-integration` created off `origin/master`. Brainstorming session (multi-round) resolved 18 design decisions; design spec committed at `docs/superpowers/specs/2026-05-20-discord-integration-design.md`. 25 REQ-IDs defined in `.planning/REQUIREMENTS.md` (5 carry-forward UX-01/COV-01/CLEAN-01/DOCS-01/BOOK-01 + 20 Discord INFRA-01..03/CHAN-01..03/POST-01..08/GRAFX-01/FORUM-01..02/E2E-01/DOCS-02..03).
- 2026-05-20: v1.13 ROADMAP.md created — 7 phases (92-98), 25/25 REQ-IDs mapped (100 % coverage), no orphans. Per-phase REQ counts: 5+3+3+5+3+3+3 = 25 ✓. Awaiting user approval before `/gsd-discuss-phase 92`.
- Phase 99 added: Pre-merge audit-polish (REQUIREMENTS Flyway-Prose + ROADMAP refresh + retroactive 9N-VERIFICATION.md + VALIDATION.md frontmatter + FORUM-01 modal scope) — closes v1.13-MILESTONE-AUDIT.md tech_debt
- Phase 100 added: Match Day Discord channel naming scheme — add phase prefix (rs/po/pm) + optional group prefix after mdX- so Regular Season / Playoff / Placement matchday counts don't collide; current scheme md{N}-{teamA}-vs-{teamB} loses phase context
- Phase 101 added: Backup/Restore covers Discord schema (V8-V15) — current `BackupSchema` package filter `org.ctc.domain.model.*` excludes `discord_global_config` + `discord_post`, and the V8-V15 columns on matches/seasons/teams/matchdays have no MixIns or round-trip tests. Revisits Phase 72 D-15 exclusion decision in light of v1.13 making Discord first-class.
- 2026-05-26: Phase 101 closed. Backup wire contract bumped from SCHEMA_VERSION 1 / 24 entities to SCHEMA_VERSION 2 / 26 entities (adds `DiscordGlobalConfig` + `DiscordPost` + V8-V15 columns on Match/Team/Matchday/Season). Importer accepts `schema_version IN (1, 2)` (lenient v1 acceptance). `discord_post` pinned to end of export order to satisfy `@Column UUID` FK constraints on restore. DOCS-02 runbook `docs/operations/discord-integration.md` now documents single-guild restore semantics + webhook_token PII-equivalent secrecy implication.

### Blockers/Concerns

At roadmap creation (2026-05-20):

- **JaCoCo baseline regression must be closed in Phase 92** — current 88.44 % vs v1.11 88.88 % baseline; Discord-phase test coverage assumed to maintain ≥ 88.88 % only if Phase 92's `RaceControllerCalendarTest` + Google service IT lands first. Phase 93+ measurements run against the post-Phase-92 baseline.
- **Live Discord UAT required for Phase 93's INFRA-03** — `Test Connection` button (Bot `GET /users/@me`) only meaningfully verifies against a live Discord token + a real (test) guild. WireMock-only ITs cover the happy + 4-permit exception paths but cannot prove the actual Discord-API contract is honored. UAT step explicit in Phase 93 success criteria.
- **Permission-overwrite audit (Phase 94 CHAN-02) is security-critical** — wrong role-mapping causes opposing team to see match-channel pre-match (T-93-03 from design spec § 3.4). Post-create permission-audit assertion is non-negotiable.
- **Rate-limit-burst risk on matchday-batch posting (Phase 97 POST-06)** — "Post Match Previews (batch)" iterates over `matchday.matches` and could exceed Discord's per-bucket token bucket. Mitigated by `DiscordRateLimitInterceptor` (per-bucket token-bucket + max-5-parallel + sequential batch).
- **Forum-thread auto-unarchive (Phase 96 FORUM-02)** — Discord requires PATCH `archived=false` before posting to an archived thread; default config leaves the thread unarchived after the post (per design spec § 4.7). Configurable but not exposed in v1.13 UI.
- **WireMock-Discord-simulator divergence risk (Phase 98 E2E-01)** — Mandatory UAT in Phase 98 against live Discord with test-season + edge cases (empty forum, archived thread, full category) to catch any drift between WireMock fixtures and real Discord behaviour.

### Baselines to Preserve

- JaCoCo line coverage: **≥ 88.88%** (v1.11 baseline; Phase 92 restores; subsequent phases must maintain or improve)
- Test count: **≥ 1696** (v1.12 baseline; Phase 92 adds ~10, Discord phases add ~50-80)
- `./mvnw verify -Pe2e` CI median (E2E step): **17:39 ± 20 %** (v1.12 baseline; WireMock-only Discord tests, no live Discord in CI)
- `BackupSchema.SCHEMA_VERSION`: **2** (bumped 2026-05-26 in Phase 101 to include Discord wire fields/sections; importer remains lenient on `IN (1, 2)` for pre-v1.13 v1 backups)
- `EXPORT_ORDER` size: **26 entities** (V1 24 entities + `DiscordGlobalConfig` + `DiscordPost`; package filter accepts `org.ctc.domain.model.*` + `org.ctc.discord.model.*` after Phase 101 closed 2026-05-26; `discord_post` is pinned last so its `@Column UUID` FKs satisfy DB-level constraints on restore)
- SpotBugs `BugInstance` count: **0** (blocking gate)
- CodeQL gate-step: **exit 0 on new HIGH/CRITICAL** (3-layer FP suppression invariant maintained)
- Flyway migrations: V1-V7 immutable; v1.13 adds **V8, V9, V10, V11, V12**

## Session Continuity

**Last session:** 2026-05-26T10:02:56.548Z

**Stopped at:** Phase 101 context gathered

**Next action:** `/gsd-complete-milestone v1.13` — archive milestone artifacts, advance MILESTONES.md, prepare squash-merge subject `feat(v1.13): discord integration & carry-forwards` for PR #130. All 9 phases (92-100) verified, 36/36 plans shipped, 2255 tests green, JaCoCo 88.98 %, SpotBugs 0.

**Branch:** `gsd/v1.13-discord-integration` (off `origin/master`)

## Operator Next Steps

1. Run `/gsd-complete-milestone v1.13` to archive milestone artifacts + write the canonical MILESTONES.md v1.13 entry.
2. Update PR #130 description with the final Phase 99 + 100 rows (rolling summary discipline).
3. Squash-merge PR #130 with subject `feat(v1.13): discord integration & carry-forwards` — triggers Semantic Release MINOR bump to `v1.13.0` + GitHub Release + `ghcr.io/jegr78/ctc-manager:1.13.0` + `:latest`.
4. Post-merge: clean up local branch (`git switch master && git pull && git branch -d gsd/v1.13-discord-integration`).
