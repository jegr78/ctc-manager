---
quick_id: 260531-suj
status: complete
date: 2026-05-31
branch: feature/discord-link-existing-channel
---

# Quick Task 260531-suj — Summary

**Task:** Allow an admin to retroactively link an EXISTING Discord channel to a Match (when `discordChannelId` is null) via the Admin UI, instead of creating a new one.

## Outcome

Implemented end-to-end, TDD, 3 atomic commits. `./mvnw clean verify` green — all coverage checks met, SpotBugs clean, Checkstyle clean, Failsafe IT 543 / 0 failures.

## Decisions (from CONTEXT.md, all user-confirmed)

1. **Manual channel-ID input** (text field), no picker.
2. **Webhook reuse**: list channel webhooks, reuse the "CTC Manager" webhook if present, otherwise create one — both `discordChannelId` AND `discordChannelWebhookUrl` are set so the channel is posting-usable.
3. **Validation = existence/reachability only** (`fetchChannel`); 404 → not-found, 403 → missing-permissions flash. No category/permission-overwrite enforcement.
4. **UI**: inline form in the match-detail "Discord Actions" card, visible only while `discordChannelId == null`.

**Planner refinement (verified, beyond CONTEXT):** `linkExistingChannel` does **NOT** publish `ChannelCreatedEvent`. `DiscordAutoPostListener.onChannelCreated` auto-posts Team Cards on that event — firing it on link would spam a manually-prepared channel. The operator uses the explicit "Post Team Cards" button instead.

## Commits

| Hash | Task | What |
|------|------|------|
| 84ff37d1 | 1 | `Webhook.name` field + `DiscordRestClient.listWebhooks(channelId)` (`GET /channels/{id}/webhooks`); WireMock IT pins production array payload + empty-list case |
| cb1f5647 | 2 | `DiscordChannelService.linkExistingChannel(match, channelId)`; 4 WireMock IT scenarios (reuse / create / 404 / 403), no ChannelCreatedEvent |
| 886b99d9 | 3 | `MatchController` POST `/admin/matches/{id}/link-discord-channel` (thin, blank-check, `applyErrorFlash`) + inline link form in match-detail card; 3 controller tests |

## Files changed

- `src/main/java/org/ctc/discord/dto/Webhook.java`
- `src/main/java/org/ctc/discord/DiscordRestClient.java`
- `src/main/java/org/ctc/discord/service/DiscordChannelService.java`
- `src/main/java/org/ctc/admin/controller/MatchController.java`
- `src/main/resources/templates/admin/match-detail.html`
- `src/test/java/org/ctc/discord/DiscordRestClientIT.java` (added listWebhooks tests to the existing IT, not a new file)
- `src/test/java/org/ctc/discord/service/DiscordChannelServiceWireMockIT.java`
- `src/test/java/org/ctc/admin/controller/MatchControllerLinkChannelTest.java` (new)

No Flyway migration — both columns already exist and are nullable.

## Process deviations from stock /gsd-quick (per CLAUDE.md)

- **Inline sequential execution** instead of a worktree-isolated `gsd-executor` subagent (CLAUDE.md Subagent Rules — worktree branch-drift lockout).
- **Feature branch** `feature/discord-link-existing-channel` off `origin/master` instead of committing on the current branch (CLAUDE.md Git Workflow — no direct commits to master).
- **Discussion-style gray-area clarification** up front (task said "Analysiere…"), captured in CONTEXT.md before planning.

## Verification

`./mvnw clean verify` → BUILD SUCCESS, "All coverage checks have been met", SpotBugs clean.

## Suggested next steps

- Self code-review of the diff (`superpowers:code-reviewer` / `/gsd-code-review`) before PR (CLAUDE.md "Before PR").
- Visual UAT of the match-detail link form (desktop + mobile) via `/gsd-auto-uat` or playwright-cli — the only un-exercised piece is the inline form rendering.
- Open PR into `master` (`gh pr create --assignee jegr78`), squash-merge with a Conventional-Commit subject.
